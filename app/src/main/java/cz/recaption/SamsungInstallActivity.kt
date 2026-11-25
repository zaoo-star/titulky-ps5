package cz.recaption

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import cz.recaption.core.SamsungBundle
import cz.recaption.core.SamsungInstall
import cz.recaption.core.SamsungSetup
import cz.recaption.core.TvAnnounce
import cz.recaption.core.TvCheck
import cz.recaption.core.TvSender
import cz.recaption.core.TvType

/**
 * Průvodce: ReCaption do televize Samsung (Tizen). Otevírá se ze stránky Domů (typ televize
 * Samsung), z Nápovědy a z pomocníka v telefonu. Ovládá se ovladačem (šipky + OK) i dotykem.
 *
 * Přímo z ReCaptionu se do Samsungu nahrávat nedá: televize přijme jen balíček podepsaný certifikátem,
 * který Samsung vystaví na účet hráče pro jeho televizi (viz tizen/CTI-ME.md). Od 0.97.52 to za hráče
 * dělá **Apps2Samsung** v telefonu (zdarma, bez počítače) – průvodce ve stylu pomocníka LG
 * ([cz.recaption.core.SamsungSetup], kroky se odškrtávají samy, kde to jde):
 *  1. vývojářský režim v TV (Host PC IP = adresa telefonu; stav z REST API TV `:8001/api/v2/` – vypnutý, jiná / obrácená
 *     Host PC IP, chybí restart – a odškrtne se, když nalezená TV Samsung otevře port SDB; rozhodování [SamsungSetup.devState]),
 *  2. Apps2Samsung v telefonu (pozná se sám; na TV/boxu čtvereček na naši stránku s pomocí),
 *  3. přidat ReCaption jako zdroj (odkaz `apps2samsung://source?url=…` na vydání `latest` s `ReCaption-Samsung.wgt`),
 *  4. nainstalovat v Apps2Samsungu (napoprvé přihlášení účtem Samsung = podpis),
 *  5. hotovo = aplikace z televize se opravdu připojila a ohlásila verzi ([TvSender.lastSeen], [TvCheck]);
 *     pak pošle do televize zkušební větu.
 * Záloha bez telefonu s Androidem (rozbalovací skupina): uložit nepodepsaný .wgt a Tizen Studio v počítači.
 */
class SamsungInstallActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var content: LinearLayout
    private var bundle: SamsungBundle? = null
    private var saveMsg: String? = null
    private var saveOk = false
    /** Vlastní příjem televize, když neběží překlad (ten má svůj). */
    private var sender: TvSender? = null
    private var portBusy = false
    private var check = TvCheck.WAITING
    private var testSent = false
    /** TV Samsung nalezená v síti (SSDP), null = zatím žádná. */
    @Volatile private var tvIp: String? = null
    /** Stav vývojářského režimu nalezené TV (REST API + port SDB); READY = hotovo. */
    @Volatile private var devState = SamsungSetup.DevState.UNKNOWN
    /** Host PC IP a model, jak je hlásí REST API TV. */
    @Volatile private var devIp: String? = null
    @Volatile private var tvModel: String? = null
    /** Hledání a zkouška portu běží (nejvýš jedno vlákno). */
    @Volatile private var probing = false
    @Volatile private var lastSsdp = 0L
    @Volatile private var lastProbe = 0L
    /** Kroky potvrzené hráčem („Hotovo“) a předání zdroje Apps2Samsungu. */
    private var okDev = false
    private var okInstaller = false
    private var okSource = false
    /** Apps2Samsung je v tomto zařízení (zjišťuje se při každém návratu do průvodce). */
    private var installerHere = false
    /** Rozbalená záloha „počítač (Tizen Studio)“. */
    private var pcOpen = false
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            ensureListening()
            probe()
            val now = currentCheck()
            if (now != check) {
                check = now
                if (check.ok) sendTestLine()
                render(true)
                // z kroku Televize v průvodci: ReCaption v TV se ohlásil → po chvíli (zelený řádek je vidět) zpět, krok ukáže ✔
                if (check.ok && intent?.getBooleanExtra(LgInstallActivity.EXTRA_WIZARD, false) == true)
                    handler.postDelayed({ if (!isFinishing) finish() }, 3000)
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun t(key: String) = tx(key)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        bundle = loadBundle()
        // novější aplikace pro TV ve vydání (bez nové verze pro Android, od 1.0.3) → stáhnout a nabídnout ji
        TvPackages.refresh(this, TvType.SAMSUNG, page = true) {
            runOnUiThread { if (!isFinishing) { bundle = loadBundle(); check = currentCheck(); render(true) } }
        }
        savedInstanceState?.let {
            okDev = it.getBoolean("okDev"); okInstaller = it.getBoolean("okInst"); okSource = it.getBoolean("okSrc"); pcOpen = it.getBoolean("pc")
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Theme.BG) }
        content = ui.vbox().apply {
            val side = if (resources.configuration.screenWidthDp >= 900) ui.dp(80) else ui.dp(20)
            setPadding(side, ui.dp(28), side, ui.dp(36))
        }
        scroll.addView(content)
        setContentView(scroll)
        check = currentCheck()
        render()
    }

    /** Balíček .wgt z APK, nebo novější stažený z vydání (TvPackages). */
    private fun loadBundle(): SamsungBundle? = try {
        val (manifest, data) = TvPackages.load(this, TvType.SAMSUNG)
        SamsungInstall.bundle(manifest, data)
    } catch (_: Exception) { null }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putBoolean("okDev", okDev); out.putBoolean("okInst", okInstaller); out.putBoolean("okSrc", okSource); out.putBoolean("pc", pcOpen)
    }

    override fun onResume() {
        super.onResume()
        // po návratu (stažení Apps2Samsungu, přidání zdroje) zjistit znovu, co v telefonu je
        installerHere = installerInstalled()
        render(true)
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    override fun onDestroy() {
        sender?.stop(); sender = null
        super.onDestroy()
    }

    private fun currentCheck(): TvCheck {
        val b = bundle ?: return TvCheck.WAITING
        return TvCheck.of(TvSender.lastSeen?.announce, TvAnnounce.TIZEN, b.version)
    }

    /** Aby se televize měla kam připojit: když neběží překlad s posíláním, čeká na ni průvodce sám. */
    private fun ensureListening() {
        if (sender != null || TranslatorService.tvConnected >= 0) { portBusy = false; return }
        val s = TvSender { }
        if (s.start()) { sender = s; portBusy = false } else if (!portBusy) { portBusy = true; render(true) }
    }

    /** Zkušební věta do televize – jen přes vlastní příjem (překlad posílá své titulky sám). */
    private fun sendTestLine() {
        if (testSent) return
        sender?.let { it.show(t("ss_test_bar")); testSent = true }
    }

    /** Apps2Samsung v tomto zařízení? (Android 11+: viditelný díky `<queries>` v manifestu.) */
    private fun installerInstalled(): Boolean = try {
        packageManager.getLaunchIntentForPackage(SamsungSetup.INSTALLER_PACKAGE) != null ||
            packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse(SamsungSetup.sourceLink())), 0).isNotEmpty()
    } catch (_: Exception) { false }

    /**
     * Na pozadí: TV Samsung v síti (SSDP, nejvýš jednou za 15 s, dokud se nenajde), její REST API a port SDB (každých 5 s,
     * dokud vývojářský režim není hotový). Jen dokud je průvodce vidět a ReCaption z TV ještě není hotové.
     */
    private fun probe() {
        if (probing || check.ok || devState == SamsungSetup.DevState.READY) return
        val now = android.os.SystemClock.elapsedRealtime()
        val ip = tvIp
        if (ip == null && now - lastSsdp < 15_000L) return
        if (ip != null && now - lastProbe < 5_000L) return
        probing = true
        val before = listOf(tvIp, devState, devIp, tvModel)
        // Host PC IP má být zařízení s Apps2Samsungem: v telefonu tento telefon, z TV/boxu se adresa neporovnává
        val tvDevice = Prefs.isTv(this)
        Thread({
            try {
                if (tvIp == null) { lastSsdp = android.os.SystemClock.elapsedRealtime(); tvIp = LgFinder.samsung(this, 2000).firstOrNull() }
                tvIp?.let { ip ->
                    lastProbe = android.os.SystemClock.elapsedRealtime()
                    val info = LgFinder.samsungInfo(ip)
                    val open = LgFinder.open(ip, SamsungSetup.SDB_PORT, 800)
                    info?.model?.let { tvModel = it }
                    devIp = info?.developerIp
                    devState = SamsungSetup.devState(info, open, if (tvDevice) null else NetInfo.homeAddress(this))
                }
            } finally {
                probing = false
                // překreslit jen při změně (stránka by jinak každých pár sekund poskočila)
                if (listOf(tvIp, devState, devIp, tvModel) != before) runOnUiThread { if (!isFinishing) render(true) }
            }
        }, "tp-samsung").start()
    }

    private fun add(v: View, top: Int = 12) = content.addView(v, ui.lp(top = top))

    private fun <V : View> tagged(v: V, tag: String): V { v.tag = tag; return v }

    private fun open(url: String): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: Exception) { false }

    /** Čtvereček pro telefon (na televizi/boxu) s popiskem vedle. */
    private fun qrRow(url: String, label: String): View {
        val row = ui.hbox()
        QrImage.bitmap(url)?.let { qr ->
            row.addView(ImageView(this).apply { setImageBitmap(qr); setBackgroundColor(android.graphics.Color.WHITE); setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6)) },
                LinearLayout.LayoutParams(ui.dp(160), ui.dp(160)))
        }
        row.addView(ui.text(label + "\n" + t("help_qr_note"), 15f, Theme.MUTED).apply { setPadding(ui.dp(16), 0, 0, 0) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun probeState() = SamsungSetup.Probe(
        devMode = okDev || devState == SamsungSetup.DevState.READY,
        installer = okInstaller || installerHere,
        source = okSource,
        check = check,
    )

    private fun render(keepFocus: Boolean = false) {
        val focused = if (keepFocus) currentFocus?.tag else null
        val tv = Prefs.isTv(this)
        content.removeAllViews()
        add(ui.text(t("ss_title"), 26f, Theme.TEXT, bold = true), 0)
        add(ui.text(t("sa_overview"), 16f, Theme.MUTED), 8)

        // výsledek nahoře: tohle hráč hledá, když se vrací z instalace
        add(ui.card().apply {
            addView(ui.text(t("ss_s4_h"), 18f, Theme.ACCENT, bold = true))
            val seen = TvSender.lastSeen?.announce
            val (line, color) = when (check) {
                TvCheck.WAITING -> t("ss_wait") to Theme.MUTED
                TvCheck.OTHER -> t("ss_other") to Theme.WARN
                TvCheck.NO_VERSION -> t("sa_noversion") to Theme.WARN
                TvCheck.OLDER -> t("sa_older").replace("%s", seen?.version ?: "?") to Theme.WARN
                TvCheck.SAME -> t("ss_ok").replace("%s", seen?.version ?: "?") to Theme.OK
                TvCheck.NEWER -> t("ss_ok_newer").replace("%s", seen?.version ?: "?") to Theme.OK
            }
            addView(ui.text(line, 17f, color, bold = check != TvCheck.WAITING).apply { tag = "sscheck" }, ui.lp(top = 10))
            if (check == TvCheck.WAITING) addView(ui.waiting(0).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(6) })
            seen?.model?.let { addView(ui.text(it + (seen.system?.let { s -> " · $s" } ?: ""), 14f, Theme.MUTED), ui.lp(top = 4)) }
            if (portBusy) addView(ui.text(t("ss_port"), 15f, Theme.WARN), ui.lp(top = 8))
        }, 18)

        val p = probeState()
        val now = SamsungSetup.step(p)
        var defaultFocus = "ssback"
        add(ui.card().apply {
            addView(ui.text(t("lg_steps_h"), 18f, Theme.ACCENT, bold = true))
            val total = SamsungSetup.Step.values().size
            addView(ui.steps(now.ordinal + 1, total, tx("steps_of").replace("%1", (now.ordinal + 1).toString()).replace("%2", total.toString())), ui.lp(top = 8))
            val update = check == TvCheck.OLDER || check == TvCheck.NO_VERSION
            val steps = listOf(SamsungSetup.Step.DEV_MODE to "sa_dev", SamsungSetup.Step.INSTALLER to "sa_inst",
                SamsungSetup.Step.SOURCE to "sa_src", SamsungSetup.Step.INSTALL to "sa_install")
            for ((s, key) in steps) {
                val done = SamsungSetup.done(s, p)
                val cur = s == now
                val mark = if (done) "✔ " else if (cur) "➜ " else "○ "
                // hotový krok INSTALLER/SOURCE jde znovu otevřít klepnutím (tester 24. 11. 2026: po potvrzení
                // zdroje appka skočila rovnou na Instalaci a nešlo se vrátit, když se přidání v Apps2Samsungu nepovedlo)
                val zpet: (() -> Unit)? = if (done) when (s) {
                    SamsungSetup.Step.INSTALLER -> { { okInstaller = false; okSource = false; render() } }
                    SamsungSetup.Step.SOURCE -> { { okSource = false; render() } }
                    else -> null
                } else null
                addView(ui.text(mark + t(key), 16f, if (done) Theme.OK else if (cur) Theme.TEXT else Theme.MUTED, bold = cur).apply {
                    zpet?.let { akce -> isClickable = true; isFocusable = true; setOnClickListener { akce() } }
                }, ui.lp(top = 8))
                // nalezená TV je vidět u prvního kroku i po odškrtnutí, pod ní konkrétní potíž s vývojářským režimem (REST API TV)
                if (s == SamsungSetup.Step.DEV_MODE) tvIp?.let { ip ->
                    addView(ui.text(t("sa_found").replace("%s", tvModel?.let { m -> "$m · $ip" } ?: ip), 14f, Theme.MUTED), ui.lp(top = 2))
                    SamsungSetup.devStateKey(devState)?.let { key ->
                        val me = NetInfo.homeAddress(this@SamsungInstallActivity)
                        val line = t(key).replace("%1", devIp ?: "?").replace("%2", me?.let { a -> SamsungSetup.reversed(a) } ?: "?")
                        addView(ui.text(line, 15f, Theme.WARN, bold = true).apply { tag = "ssdm" }, ui.lp(top = 4))
                    }
                }
                if (!cur) continue
                when (s) {
                    SamsungSetup.Step.DEV_MODE -> {
                        addView(ui.waiting(24).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(4) })   // odškrtne se samo, až TV otevře port
                        addView(ui.text(t("sa_dev_how"), 15f, Theme.TEXT), ui.lp(top = 4))
                        addView(ui.text(t("sa_dev_tip"), 14f, Theme.MUTED), ui.lp(top = 4))
                        // Host PC IP = zařízení s Apps2Samsungem: tento telefon, z televize/boxu jen popis
                        val ip = NetInfo.homeAddress(this@SamsungInstallActivity)
                        if (tv) addView(ui.text(t("sa_ip_phone"), 16f, Theme.ACCENT, bold = true), ui.lp(top = 8))
                        else addView(ui.text(if (ip != null) t("sa_ip").replace("%s", ip) else t("ss_ip_none"), 20f, if (ip != null) Theme.ACCENT else Theme.WARN, bold = true), ui.lp(top = 8))
                        addView(tagged(ui.button(t("sa_done"), primary = true) { okDev = true; render() }, "ssok"), ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT, top = 10))
                        defaultFocus = "ssok"
                    }
                    SamsungSetup.Step.INSTALLER -> {
                        addView(ui.text(t("sa_inst_how"), 15f, Theme.TEXT), ui.lp(top = 4))
                        val url = cz.recaption.core.HelpLinks.SAMSUNG_INSTALLER
                        if (tv) addView(qrRow(url, t("sa_inst_get")), ui.lp(top = 10))
                        else { addView(tagged(ui.button(t("sa_inst_get"), primary = true) { open(url) }, "ssact"), ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT, top = 10)); defaultFocus = "ssact" }
                        addView(tagged(ui.smallButton(t("sa_done")) { okInstaller = true; render() }, "ssok"), ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT, top = 10))
                        if (tv) defaultFocus = "ssok"
                    }
                    SamsungSetup.Step.SOURCE -> {
                        addView(ui.text(t("sa_src_how"), 15f, Theme.TEXT), ui.lp(top = 4))
                        addView(ui.text(SamsungSetup.SOURCE_URL, 14f, Theme.ACCENT), ui.lp(top = 4))
                        if (tv) addView(qrRow(SamsungSetup.sourceWebLink(), t("sa_src_btn")), ui.lp(top = 10))
                        else {
                            addView(tagged(ui.button(t("sa_src_btn"), primary = true) {
                                // Apps2Samsung 3.3.1+ přijme odkaz rovnou; jinak web, který ho předá nebo nabídne stažení
                                if (!open(SamsungSetup.sourceLink())) open(SamsungSetup.sourceWebLink())
                                okSource = true
                            }, "ssact"), ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT, top = 10))
                            defaultFocus = "ssact"
                        }
                        addView(tagged(ui.smallButton(t("sa_done")) { okSource = true; render() }, "ssok"), ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT, top = 10))
                        if (tv) defaultFocus = "ssok"
                    }
                    SamsungSetup.Step.INSTALL -> {
                        addView(ui.text(t(if (update) "sa_update_how" else "sa_install_how"), 15f, Theme.TEXT), ui.lp(top = 4))
                        if (!tv && installerHere) {
                            addView(tagged(ui.button(t("sa_open"), primary = true) {
                                packageManager.getLaunchIntentForPackage(SamsungSetup.INSTALLER_PACKAGE)?.let { i -> try { startActivity(i) } catch (_: Exception) {} }
                                    ?: open(SamsungSetup.sourceLink())
                            }, "ssact"), ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT, top = 10))
                            defaultFocus = "ssact"
                        }
                        addView(ui.progress(null), ui.lp(h = ui.dp(8), top = 10))
                        addView(ui.text(t("sa_install_tip"), 14f, Theme.MUTED), ui.lp(top = 10))
                    }
                    SamsungSetup.Step.READY -> {}
                }
            }
            if (now == SamsungSetup.Step.READY) {
                addView(ui.text("✔ " + t("sa_ready"), 16f, Theme.OK, bold = true), ui.lp(top = 10))
                addView(ui.text(t("sa_ready_tip"), 14f, Theme.MUTED), ui.lp(top = 6))
            }
        }, 14)

        // záloha bez telefonu s Androidem: soubor .wgt a Tizen Studio v počítači
        add(tagged(ui.settingRow(t("sa_pc"), if (pcOpen) "▲" else "▼") { pcOpen = !pcOpen; render(true) }, "sspc"), 14)
        if (pcOpen) {
            val b = bundle
            add(ui.card().apply {
                if (b == null) addView(ui.text(t("ss_pkg_bad"), 16f, Theme.BAD, bold = true))
                else {
                    // výsledek uložení jako první v kartě, nad popisem a tlačítkem (přání majitele 22. 11. 2026)
                    saveMsg?.let { addView(ui.text(it, 16f, if (saveOk) Theme.OK else Theme.BAD, bold = true)) }
                    addView(ui.text(t("ss_s2").replace("%s", b.fileName).replace("%v", b.version), 16f, Theme.TEXT), ui.lp(top = if (saveMsg != null) 8 else 0))
                    addView(tagged(ui.button(t("ss_save")) { save() }, "sssave"), ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT, top = 12))
                }
                addView(ui.text(t("ss_s3"), 16f, Theme.TEXT), ui.lp(top = 14))
                val url = cz.recaption.core.HelpLinks.SAMSUNG_PC
                val label = t("ss_link").trimEnd(':', '：', ' ')
                if (tv) addView(qrRow(url, label), ui.lp(top = 10))
                else addView(tagged(ui.button(label) { open(url) }, "sstizen"), ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT, top = 10))
            }, 8)
        }

        add(tagged(ui.button(t("wz_back")) { finish() }, "ssback"), 22)

        val target = focused?.let { content.findViewWithTag<View>(it) } ?: content.findViewWithTag<View>(defaultFocus)
        ui.restoringFocus()   // po překreslení bez „pulzu“ vybraného tlačítka
        target?.requestFocus()
    }

    // ---------------- uložení balíčku (Storage Access Framework) ----------------

    private fun save() {
        val b = bundle ?: return
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, b.fileName)
        }
        try {
            @Suppress("DEPRECATION") startActivityForResult(i, SAVE)
        } catch (_: Exception) {
            // Android TV bez správce souborů: dialog pro uložení neexistuje
            saveOk = false; saveMsg = t("ss_save_none"); render(true)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SAVE) return
        val uri = data?.data
        val b = bundle
        if (resultCode != RESULT_OK || uri == null || b == null) return
        saveOk = try {
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(b.data); true } ?: false
        } catch (_: Exception) { false }
        saveMsg = if (saveOk) t("ss_saved") else t("ss_save_fail")
        render(true)
    }

    companion object {
        private const val SAVE = 41
    }
}
