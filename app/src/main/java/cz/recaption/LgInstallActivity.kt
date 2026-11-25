package cz.recaption

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import cz.recaption.core.LgAction
import cz.recaption.core.LgError
import cz.recaption.core.LgException
import cz.recaption.core.LgInstall
import cz.recaption.core.LgPhase
import cz.recaption.core.LgSetup

/**
 * Průvodce: nahrát (aktualizovat) ReCaption do televize LG z tohoto zařízení (telefon nebo box) přes
 * vývojářský režim LG, a kolik dní režimu zbývá. Otevírá se z Domů, Nastavení, průvodce, Nápovědy
 * a z pomocníka v telefonu. Ovládá se ovladačem (šipky + OK) i dotykem.
 *
 * Dva tvary:
 *  - **stručný** – spárovaná TV se známou adresou: stav („LG OLED55B7 · 1.5.6 → 1.5.8“), jedno velké
 *    tlačítko (Aktualizovat v TV / Nahrát do TV / Spustit ReCaption v TV), postup, dny vývojářského
 *    režimu a řádek „Jiná TV nebo potíže…“, který rozbalí přípravu,
 *  - **příprava** – TV není spárovaná, adresa chybí, TV/vývojářský režim nejde najít nebo nahrání
 *    selhalo: karta krok za krokem, adresa, pod ní kamera, Passphrase. Jednou rozbalená zůstane.
 * Delší vysvětlení (síť, prodloužení režimu, nahrání z počítače) je v Nápovědě (`hc3_lg`).
 */
class LgInstallActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var content: LinearLayout
    private lateinit var vault: LgVault
    private var ip = ""
    /** Passphrase jen v paměti, po úspěchu se smaže. */
    private var pass = ""
    private var running = false
    private var phase: LgPhase? = null
    private var installer: LgInstaller? = null
    private var resultText: String? = null
    private var resultOk = false
    private var error: LgError? = null
    /** Zpráva po čtení kamerou (text, upozornit?) – jen do dalšího spuštění instalace. */
    private var scanNote: Pair<String, Boolean>? = null

    /** U spárované televize je políčko Passphrase schované, dokud hráč nechce zadat jinou. */
    private var askPass = false

    /**
     * Rozbalená příprava (karta kroků, adresa, kamera, Passphrase). U spárované TV až na přání („Jiná TV
     * nebo potíže…“), po chybě nahrání, nebo když TV / vývojářský režim nejde najít; jednou rozbalená
     * zůstane (stránka by jinak mezi tvary skákala).
     */
    private var expanded = false
    /** Proběhlo už aspoň jedno zjištění stavu TV (do té doby se spárované TV věří). */
    private var probed = false

    // ---- pomocník krok za krokem (core/LgSetup.kt): co se o televizi právě ví ----
    private var probe = LgSetup.Probe(tvFound = false, devMode = false, keyServer = false, paired = false, passphrase = false)
    /** Televize LG nalezené v síti (pro řádek „Nalezena televize“). */
    private var foundTvs: List<String> = emptyList()
    /** Stav každé nalezené TV (vývojářský režim, Key Server) – pro seznam „Nalezené TV“. */
    private var foundState: Map<String, TvState> = emptyMap()
    /** Pro kterou adresu platí [probe] (normalizovaná; null = zatím pro žádnou). */
    private var probedTarget: String? = null
    /** Hráč chce hledat znovu (tlačítko „Hledat znovu“) – při příštím zjištění hledat i se známou adresou. */
    @Volatile private var forceSearch = false
    /** Právě se hledá v síti (slabý pruh pod „Hledat znovu“). */
    @Volatile private var searching = false
    /** Stav vedle políčka adresy – mění se přímo, ať se při psaní nepřekresluje celá stránka. */
    private var addrStatus: android.widget.TextView? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var watching = false
    @Volatile private var checking = false
    /** Stav se změnil, ale hráč zrovna píše do políčka – překreslit, až z něj odejde. */
    private var needsRender = false
    private var lastSearch = 0L
    private var lastScan = 0L
    private val tick = object : Runnable {
        override fun run() {
            if (!watching) return
            if (needsRender && currentFocus !is EditText && !running) { needsRender = false; render(true) }
            check()
            main.postDelayed(this, 3000)
        }
    }

    /** Na pozadí: najdi televizi (když adresa chybí) a zkus porty vývojářského režimu. */
    private fun check() {
        if (checking || running) return
        checking = true
        val ipNow = ip
        val passOk = LgSetup.passphraseLooksOk(pass)
        Thread({
            try {
                var found = foundTvs
                var target = runCatching { LgInstall.address(ipNow) }.getOrNull()
                val now = System.currentTimeMillis()
                // hledá se, když adresa chybí, na přání („Hledat znovu“) a jednou na začátku (seznam nalezených TV)
                val force = forceSearch || !probed
                if ((target == null && now - lastSearch > 10_000) || force) {
                    forceSearch = false
                    searching = true
                    if (force) runOnUiThread { if (!isFinishing && !running && currentFocus !is EditText) render(true) }
                    lastSearch = now
                    found = LgFinder.ssdp(this)
                    if (found.isEmpty() && (force || now - lastScan > 30_000)) { lastScan = now; found = LgFinder.scan(this) }
                    searching = false
                    if (target == null) target = found.firstOrNull()
                }
                val t = target
                val dev = t != null && LgFinder.open(t, LgSetup.SSH_PORT)
                val key = t != null && LgFinder.open(t, LgSetup.KEY_PORT)
                val tvOn = t != null && (dev || found.contains(t) || LgFinder.open(t, LgSetup.WEBOS_PORT) || LgFinder.open(t, LgSetup.WEBOS_SSL_PORT))
                val paired = t != null && runCatching { vault.paired(t) }.getOrDefault(false)
                val next = LgSetup.Probe(tvOn, dev, key, paired, passOk)
                // stav ostatních nalezených TV (jen pár portů, TV v síti bývá jedna nebo dvě)
                val states = found.associateWith { f ->
                    if (f == t) TvState(true, dev, key || paired)
                    else { val d = LgFinder.open(f, LgSetup.SSH_PORT)
                        TvState(true, d, d && (LgFinder.open(f, LgSetup.KEY_PORT) || runCatching { vault.paired(f) }.getOrDefault(false))) }
                }
                runOnUiThread {
                    if (isFinishing || running) return@runOnUiThread
                    val fill = ip.isEmpty() && t != null && found.contains(t)
                    if (fill) ip = t!!
                    val first = !probed
                    probed = true
                    probedTarget = t
                    updateAddrStatus()
                    if (next != probe || found != foundTvs || states != foundState || fill || first) {
                        probe = next; foundTvs = found; foundState = states
                        // při psaní do políčka nepřekreslovat (zavřela by se klávesnice a skočil kurzor) – až potom
                        if (currentFocus is EditText && !fill) needsRender = true else render(true)
                    }
                    // zjištění patřilo adrese z kamery → kroky hotové = nahrát samo
                    if (ipNow == ip) autoStart()
                }
            } finally { checking = false }
        }, "tp-lg-check").start()
    }

    override fun onResume() {
        super.onResume()
        watching = true
        main.removeCallbacks(tick)
        main.post(tick)
    }

    override fun onPause() {
        watching = false
        main.removeCallbacks(tick)
        super.onPause()
    }

    /** Seznam kroků s fajfkami; rozepsaný je jen ten, který je na řadě. */
    private fun stepsCard(): View = ui.card().apply {
        addView(ui.text(t("lg_steps_h"), 18f, Theme.ACCENT, bold = true))
        val p = probe.copy(passphrase = LgSetup.passphraseLooksOk(pass))
        val now = LgSetup.step(p)
        // kolik kroků zbývá: 4 kroky přípravy + nahrání (READY = poslední)
        val total = LgSetup.Step.values().size
        addView(ui.steps(now.ordinal + 1, total, tx("steps_of").replace("%1", (now.ordinal + 1).toString()).replace("%2", total.toString())), ui.lp(top = 8))
        val steps = listOf(LgSetup.Step.FIND_TV to "lg_s_find", LgSetup.Step.DEV_MODE to "lg_s_dev",
            LgSetup.Step.KEY_SERVER to "lg_s_key", LgSetup.Step.PASSPHRASE to "lg_s_pass")
        for ((s, key) in steps) {
            val done = LgSetup.done(s, p)
            val cur = s == now
            val mark = if (done) "✔ " else if (cur) "➜ " else "○ "
            // spárovaná televize: Passphrase se nepíše, klíč už tohle zařízení má
            val label = if (s == LgSetup.Step.PASSPHRASE && p.paired) t("lg_pass_paired") else t(key)
            addView(ui.text(mark + label, 16f, if (done) Theme.OK else if (cur) Theme.TEXT else Theme.MUTED, bold = cur), ui.lp(top = 8))
            if (cur) {
                // krok, který se sám odškrtne (každé 3 s se zjišťuje): slabý běžící pruh pod ním
                if (s != LgSetup.Step.PASSPHRASE) addView(ui.waiting(24).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(4) })
                addView(ui.text(t(key + "_how"), 15f, Theme.TEXT), ui.lp(top = 4))
                if (s == LgSetup.Step.DEV_MODE) addView(accountLink(), ui.lp(top = 10))
            }
        }
        // v televizi už je stejná nebo novější verze: nic se nenahrává, jen se spustí
        val current = TvChoice.upToDate(this@LgInstallActivity, cz.recaption.core.TvType.LG)
        if (now == LgSetup.Step.READY) addView(ui.text("✔ " + (current?.let { t("lg_st_cur").replace("%s", it) } ?: t("lg_s_ready")), 16f, Theme.OK, bold = true), ui.lp(top = 10))
    }

    /** Účet LG pro vývojáře: v telefonu tlačítko do prohlížeče, v televizi čtvereček pro telefon. */
    private fun accountLink(): View {
        // naše stránka s pomocí: odkaz na registraci LG + záložní odkazy, kdyby LG stránku přesunulo
        val url = cz.recaption.core.HelpLinks.LG_ACCOUNT
        if (LgScanActivity.available(this)) return tagged(ui.button(t("lg_s_account")) {
            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) {}
        }, "lgacc")
        val row = ui.hbox()
        QrImage.bitmap(url)?.let { qr ->
            row.addView(android.widget.ImageView(this).apply { setImageBitmap(qr); setBackgroundColor(android.graphics.Color.WHITE); setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6)) },
                LinearLayout.LayoutParams(ui.dp(150), ui.dp(150)))
        }
        row.addView(ui.text(t("lg_s_account") + "\n" + t("help_qr_note"), 15f, Theme.MUTED).apply { setPadding(ui.dp(16), 0, 0, 0) })
        return row
    }

    private fun t(key: String) = tx(key)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        vault = LgVault(this)
        ip = vault.lastIp
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Theme.BG) }
        content = ui.vbox().apply {
            val side = if (resources.configuration.screenWidthDp >= 900) ui.dp(80) else ui.dp(20)
            setPadding(side, ui.dp(28), side, ui.dp(36))
        }
        scroll.addView(content)
        setContentView(scroll)
        render()
        LgDevMode.refresh(this, false) { runOnUiThread { if (!isFinishing && !running) render(true) } }
        LgDevMode.renew(this) { runOnUiThread { if (!isFinishing && !running) render(true) } }
        // novější aplikace pro TV ve vydání (bez nové verze pro Android, od 1.0.3) → stáhnout, tlačítko pak nabídne ji
        TvPackages.refresh(this, cz.recaption.core.TvType.LG, page = true) { runOnUiThread { if (!isFinishing && !running) render(true) } }
        // z Domů „Aktualizovat v TV“: spárovaná TV se známou adresou → rovnou nahrát; jinak stránka s přípravou
        if (savedInstanceState == null && intent?.getBooleanExtra(EXTRA_UPDATE, false) == true && !LgUpdate.running &&
            ip.isNotEmpty() && runCatching { vault.paired(LgInstall.address(ip)) }.getOrDefault(false)) start(false)
    }

    override fun onDestroy() {
        installer?.cancel()
        pass = ""
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (running) { installer?.cancel(); return }
        @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun add(v: View, top: Int = 12) = content.addView(v, ui.lp(top = top))

    private fun field(value: String, hintKey: String, tagName: String, secret: Boolean, onChange: (String) -> Unit) = EditText(this).apply {
        setText(value)
        setSelection(value.length)
        hint = t(hintKey)
        setSingleLine(true)
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            else InputType.TYPE_CLASS_PHONE
        if (!secret) IpField.setup(this)
        imeOptions = EditorInfo.IME_ACTION_DONE
        // Passphrase se nesmí dostat do zálohy ani do automatického vyplňování
        if (secret) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        isSaveEnabled = false
        setTextColor(Theme.TEXT)
        setHintTextColor(Theme.MUTED)
        textSize = 17f
        background = ui.focusBg(Theme.CARD)
        setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12))
        tag = tagName
        isEnabled = !running
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: "") }
        })
    }

    private fun render(keepFocus: Boolean = false) {
        val focused = if (keepFocus) currentFocus?.tag else null
        content.removeAllViews()
        add(ui.text(t("lg_title"), 26f, Theme.TEXT, bold = true), 0)

        val paired = runCatching { vault.paired(LgInstall.address(ip)) }.getOrDefault(false)
        val compact = compact(paired)
        // krátký stav „LG OLED55B7 · 1.5.6 → 1.5.8“ – jen když o TV něco víme
        if (paired || Prefs.tvSeen(this, cz.recaption.core.TvAnnounce.WEBOS) != null)
            add(ui.text(statusLine(), 18f, Theme.ACCENT, bold = true), 8)
        // výsledek a chyba jako první věc pod nadpisem, ne odscrollované pod kroky (přání majitele 22. 11. 2026)
        if (!running) {
            resultText?.let { add(ui.text(it, 17f, if (resultOk) Theme.OK else Theme.WARN, bold = true), 12) }
            if (offerRenew) add(renewOffer(), 14)
            error?.let { e ->
                // jen srozumitelná věta – technická podrobnost je v záznamu (LG|chyba …)
                add(ui.text(t("lg_e_" + e.name.lowercase()), 16f, Theme.BAD, bold = true), 12)
                if (e == LgError.HOST_CHANGED) add(tagged(ui.button(t("lg_repair")) { start(true) }, "lgrepair"), 10)
            }
        }
        // spárovaná TV, která není v síti (vypnutá): žádný průvodce instalací, jen „zapnout TV“ a čekat (hledá se každé 3 s)
        val tvOff = compact && probed && !probe.tvFound && !running
        if (tvOff) {
            add(ui.text(t("lg_wait_tv"), 18f, Theme.WARN, bold = true), 14)
            content.addView(ui.waiting(0).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(6) })
        }

        if (compact) {
            // spárovaná TV se známou adresou: hlavní je jedno tlačítko, příprava až na přání
            if (!tvOff) actions(paired, compact = true)
            add(devRow(), 22)
            // příprava (adresa, kamera, Passphrase) se ukáže sama, až když něco nejde – bez řádku „Jiná TV…“ (majitel 20. 11. 2026)
        } else {
            // krok za krokem: kroky, které jde poznat, se odškrtávají samy (televize v síti, vývojářský režim, Key Server)
            add(stepsCard(), 18)
            // nalezené TV jako seznam se stavem (OK = vybrat) a „Hledat znovu“ (od 1.0.5, přání majitele)
            if (!running) foundCard()?.let { add(it, 14) }
            if (!running) {
                add(tagged(ui.smallButton("⟳ " + t("lg_search_again")) { forceSearch = true; check() }, "lgsearch"), 10)
                if (searching) content.addView(ui.waiting(0).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(6) })
            }

            add(ui.text(t("lg_ip"), 16f, Theme.TEXT, bold = true), 20)
            // políčko adresy a hned vedle stav té adresy (připraveno / vývojářský režim vypnutý / nenalezeno)
            add(ui.hbox().apply {
                addView(field(ip, "lg_ip_hint", "lgip", false) { ip = it.trim(); updateAddrStatus() },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(ui.text("", 15f, Theme.MUTED, bold = true).apply { setPadding(ui.dp(14), 0, 0, 0); addrStatus = this })
            }, 6)
            updateAddrStatus()
            // telefon s kamerou: adresu a Passphrase přečíst z obrazovky televize (LgScanActivity) – pomocník
            // pod políčkem; hlavní jen dokud adresa chybí
            if (!running && LgScanActivity.available(this))
                add(tagged(if (ip.isEmpty()) ui.button("📷 " + t("km_scan"), primary = true) { scan() }
                    else ui.smallButton("📷 " + t("km_scan")) { scan() }, "lgscan"), 6)
            scanNote?.let { add(ui.text(it.first, 15f, if (it.second) Theme.WARN else Theme.OK, bold = true), 10) }
            // spárovaná televize: prázdné políčko by mátlo („mám něco vyplnit?“) – schované, nabídne se až pod tlačítkem
            // (jiný otisk TV: „Spárovat znovu“ potřebuje Passphrase – políčko rovnou)
            if (!paired || askPass || error == LgError.HOST_CHANGED) {
                add(ui.text(t("lg_pass"), 16f, Theme.TEXT, bold = true), 16)
                add(field(pass, "lg_pass_hint", "lgpass", true) { val was = LgSetup.passphraseLooksOk(pass); pass = it; if (was != LgSetup.passphraseLooksOk(it)) needsRender = true }, 6)
                // Passphrase je na téže obrazovce Developer Mode jako adresa – kamera vyplní obojí (od 0.97.37 i pod tímto políčkem)
                if (!running && LgScanActivity.available(this) && ip.isNotEmpty())
                    add(tagged(ui.smallButton("📷 " + t("km_scan")) { scan() }, "lgscanpass"), 6)
            }
            // jedna věta, co je teď na řadě – hned nad hlavním tlačítkem
            if (!running && error == null && resultText == null) add(nextLine(), 16)
            actions(paired, compact = false)
            add(devRow(), 22)
        }

        add(tagged(ui.button(t("wz_back")) { if (running) installer?.cancel(); finish() }, "lgback"), 22)

        val target = focused?.let { content.findViewWithTag<View>(it) }
            ?: content.findViewWithTag<View>(if (running) "lgcancel" else if (offerRenew) "lgrenewyes" else if (ip.isEmpty() && !compact) "lgip" else "lggo")
        ui.restoringFocus()   // po překreslení bez „pulzu“ vybraného tlačítka
        target?.requestFocus()
    }

    /** Stručný tvar? Jen spárovaná TV; po chybě nebo když TV / vývojářský režim nejde najít se příprava rozbalí natrvalo. */
    private fun compact(paired: Boolean): Boolean {
        if (!expanded && (error != null || (probed && probe.tvFound && LgSetup.step(probe) != LgSetup.Step.READY))) expanded = true
        return paired && !expanded
    }

    private enum class Act { INSTALL, UPDATE, LAUNCH }

    /** Co hlavní tlačítko udělá: v TV stejná/novější verze = jen spustit (nikdy nesnižovat), starší = aktualizovat. */
    private fun act(): Act = when {
        TvChoice.upToDate(this, cz.recaption.core.TvType.LG) != null -> Act.LAUNCH
        TvChoice.outdated(this, cz.recaption.core.TvType.LG) != null -> Act.UPDATE
        else -> Act.INSTALL
    }

    /** „LG OLED55B7 · 1.5.6 → 1.5.8“ / „LG OLED55B7 · 1.5.8 · aktuální“ / „LG · nová 1.5.8“. */
    private fun statusLine(): String {
        val lg = cz.recaption.core.TvType.LG
        val p = cz.recaption.core.TvAnnounce.WEBOS
        // model z živého ohlášení, jinak z uloženého „model · verze“ (nebo jen „verze“ / jen „model“)
        val model = cz.recaption.core.TvSender.lastSeen?.announce?.takeIf { it.platform == p }?.model
            ?: Prefs.tvSeen(this, p)?.let { s ->
                if (TvChoice.storedVersion(s) == null) s.takeIf { it.isNotBlank() }
                else s.substringBeforeLast(" · ", "").takeIf { it.isNotBlank() }
            }
        val ver = TvChoice.outdated(this, lg)?.let { "${it.first} → ${it.second}" }
            ?: TvChoice.upToDate(this, lg)?.let { t("lg_st_cur").replace("%s", it) }
            ?: TvChoice.bundledVersion(this, lg)?.let { t("lg_st_new").replace("%s", it) }
        return listOfNotNull("LG" + (model?.let { " $it" } ?: ""), ver).joinToString(" · ")
    }

    /** Hlavní tlačítko, průběh nahrávání, výsledek a chyba. */
    private fun actions(paired: Boolean, compact: Boolean) {
        if (running) {
            // „Spustit“ u aktuální verze: celou dobu jen „Spouštím ReCaption v TV…“ (kroky balíček / klíč / spojení vypadaly
            // jako nové nahrávání – majitel 22. 11. 2026)
            // průběh jako seznam (od 1.0.5, přání majitele): hotové kroky ✔, probíhající s pruhem, zbývající šedě
            add(phaseList(), 18)
            add(ui.text(t("lg_run_hint"), 15f, Theme.MUTED), 10)
            add(tagged(ui.button(t("lg_cancel")) { installer?.cancel() }, "lgcancel"), 10)
            return
        }
        // aktuální verze v televizi: LgInstaller ji stejně jen spustí – tak to i říct; starší = Aktualizovat
        val label = when (act()) { Act.LAUNCH -> "lg_launch"; Act.UPDATE -> "lg_update"; Act.INSTALL -> "lg_go" }
        add(tagged(ui.button(t(label), primary = true) { start(false) }, "lggo"), 18)
        if (!compact && paired && !askPass && error != LgError.HOST_CHANGED) add(tagged(ui.button(t("lg_pass_other")) { askPass = true; render() }, "lgpassother"), 8)
    }

    /** Vývojářský režim jedním řádkem („Vývojářský režim TV · Zbývá dní: 41“); OK = zjistit znovu. */
    private fun devRow(): View = ui.vbox().apply {
        val line = devLine(this@LgInstallActivity)
        val known = LgDevMode.known(this@LgInstallActivity) != null
        val renewOn = Prefs.lgDevRenew(this@LgInstallActivity)
        val value = line.first + (if (LgDevMode.failed && known) " · " + t("lg_dev_fail") else "") +
            if (renewOn && LgDevMode.renewFailed) " · " + t("lg_renew_fail") else ""
        // bez uloženého spojení s TV jde stav jen ukázat (kliknutí by nic neudělalo) – stavový řádek, ne tlačítko
        val color = if (line.second) Theme.WARN else if (known) Theme.ACCENT else Theme.MUTED
        addView(tagged(if (!LgDevMode.hasSession(this@LgInstallActivity)) ui.infoRow(t("lg_dev_h"), value, color)
        else ui.settingRow(t("lg_dev_h"), value, color) {
            if (!running) LgDevMode.refresh(this@LgInstallActivity, true) { runOnUiThread { if (!isFinishing && !running) render(true) } }
        }, "lgdev"))
        if (line.second) addView(ui.text(t("lg_dev_warn"), 15f, Theme.WARN, bold = true), ui.lp(top = 6))
        // prodlužovat vývojářský režim (stejná volba jako v ⚙ Nastavení → Obecné; popis v dialogu při zapnutí)
        addView(tagged(ui.settingRow(t("lg_renew"), t(if (renewOn) "on" else "off")) {
            if (running) return@settingRow
            if (renewOn) { Prefs.setLgDevRenew(this@LgInstallActivity, false); render(true) }
            else try {
                android.app.AlertDialog.Builder(this@LgInstallActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(t("lg_renew")).setMessage(t("lg_renew_desc"))
                    .setPositiveButton(t("on")) { d, _ -> d.dismiss(); renewOn() }
                    .setNegativeButton(t("wz_back")) { d, _ -> d.dismiss() }
                    .show()
            } catch (_: Exception) { renewOn() }
        }, "lgrenew"), ui.lp(top = 8))
    }

    /** Zapnout prodlužování vývojářského režimu a rovnou prodloužit (když je na čase). */
    private fun renewOn() {
        Prefs.setLgDevRenew(this, true)
        render(true)
        LgDevMode.renew(this) { runOnUiThread { if (!isFinishing && !running) render(true) } }
    }

    /** Stav jedné nalezené TV. */
    private data class TvState(val on: Boolean, val dev: Boolean, val ready: Boolean)

    /** Krátký stav TV „● připraveno“ a jeho barva. */
    private fun stateText(s: TvState): Pair<String, Int> = when {
        !s.on -> "● " + t("lg_found_off") to Theme.WARN
        !s.dev -> "● " + t("lg_found_nodev") to Theme.WARN
        !s.ready -> "● " + t("lg_found_nokey") to Theme.WARN
        else -> "● " + t("lg_found_ready") to Theme.OK
    }

    /** Seznam nalezených TV: vybraná s ✔, každá se stavem; OK = použít tuto adresu. null = nic nenalezeno. */
    private fun foundCard(): View? {
        if (foundTvs.isEmpty()) return null
        val chosen = runCatching { LgInstall.address(ip) }.getOrNull()
        return ui.vbox().apply {
            addView(ui.text(t("lg_s_found"), 15f, Theme.MUTED, bold = true))
            foundTvs.forEachIndexed { i, f ->
                val st = foundState[f]?.let { stateText(it) } ?: ("● " + t("lg_found_wait") to Theme.MUTED)
                addView(tagged(ui.settingRow((if (f == chosen) "✔ " else "") + "TV LG · " + f, st.first, st.second) {
                    if (running) return@settingRow
                    ip = f; error = null; resultText = null; probedTarget = null; render(); check()
                }, "lgfound$i"), ui.lp(top = 8))
            }
        }
    }

    /** Stav adresy v políčku – mění jen text vedle políčka (bez překreslení stránky). */
    private fun updateAddrStatus() {
        val v = addrStatus ?: return
        val target = runCatching { LgInstall.address(ip) }.getOrNull()
        val (text, color) = when {
            target == null -> "" to Theme.MUTED
            target != probedTarget || !probed -> "● " + t("lg_found_wait") to Theme.MUTED
            else -> stateText(TvState(probe.tvFound, probe.devMode, probe.keyServer || probe.paired))
        }
        v.text = text; v.setTextColor(color)
        v.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    /** Jedna věta, co je teď na řadě (podle kroku přípravy). */
    private fun nextLine(): View {
        val step = LgSetup.step(probe.copy(passphrase = LgSetup.passphraseLooksOk(pass)))
        val ready = step == LgSetup.Step.READY
        return ui.hbox().apply {
            background = ui.cardBg()
            setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(14))
            addView(ui.text(if (ready) "✔" else "➜", 18f, if (ready) Theme.OK else Theme.ACCENT, bold = true).apply { setPadding(0, 0, ui.dp(14), 0) })
            addView(ui.text(t("lg_next_" + step.name.lowercase()), 16f, Theme.TEXT),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** Kdy který krok nahrávání začal (pro ✔ a Podrobnosti). */
    private val phaseTimes = LinkedHashMap<LgPhase, Long>()
    /** Rozbalené Podrobnosti průběhu (trvání kroků). */
    private var showDetails = false

    /** Kroky nahrávání jako seznam: ✔ hotové, probíhající s pruhem, zbývající šedě; dole Podrobnosti. */
    private fun phaseList(): View = ui.card().apply {
        val cur = if (launching) LgPhase.LAUNCH else (phase ?: LgPhase.PACKAGE)
        // „Spustit“ u aktuální verze: jen spouštění (kroky nahrávání by vypadaly jako nová instalace – majitel 22. 11. 2026)
        val list = if (launching) listOf(LgPhase.LAUNCH) else LgPhase.values().toList()
        for (p in list) {
            val done = p.ordinal < cur.ordinal
            val now = p == cur
            val label = t("lg_p_" + p.name.lowercase()).let { if (done) it.removeSuffix("…") else it }
            addView(ui.text((if (done) "✔ " else if (now) "➜ " else "○ ") + label, 16f,
                if (done) Theme.OK else if (now) Theme.TEXT else Theme.MUTED, bold = now), ui.lp(top = if (p == list.first()) 0 else 8))
            if (now) addView(ui.waiting(24).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(4) })
        }
        if (!launching) {
            addView(tagged(ui.smallButton((if (showDetails) "▲ " else "▼ ") + t("lg_details")) { showDetails = !showDetails; render(true) }, "lgdetails"), ui.lp(top = 12))
            if (showDetails) {
                val keys = phaseTimes.keys.toList()
                val lines = keys.mapIndexed { i, p ->
                    val end = if (i + 1 < keys.size) phaseTimes[keys[i + 1]]!! else System.currentTimeMillis()
                    t("lg_p_" + p.name.lowercase()).removeSuffix("…") + " · " + ((end - phaseTimes[p]!!) / 1000) + " s"
                }
                addView(ui.text(lines.joinToString("\n"), 14f, Theme.MUTED), ui.lp(top = 8))
            }
        }
    }

    /** Po úspěšném nahrání nabídnout prodlužování vývojářského režimu (jednou). */
    private var offerRenew = false

    private fun renewOffer(): View = ui.card().apply {
        addView(ui.text(t("lg_renew_ask"), 17f, Theme.TEXT, bold = true))
        addView(ui.text(t("lg_renew_short"), 15f, Theme.MUTED), ui.lp(top = 6))
        val row = ui.hbox()
        val answer = { yes: Boolean ->
            getSharedPreferences("ui_jednou", MODE_PRIVATE).edit().putBoolean("lg_prodlouzeni", true).apply()
            offerRenew = false
            if (yes) renewOn() else render(true)
            if (fromWizard) main.postDelayed({ if (!isFinishing && !running) finish() }, 600)
        }
        row.addView(tagged(ui.button(t("lg_renew_yes"), primary = true) { answer(true) }, "lgrenewyes"))
        row.addView(tagged(ui.button(t("lg_renew_no")) { answer(false) }, "lgrenewno"), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = ui.dp(12) })
        addView(row, ui.lp(top = 12))
    }

    private fun scan() {
        if (running) return
        if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            @Suppress("DEPRECATION") startActivityForResult(Intent(this, LgScanActivity::class.java), REQ_SCAN)
            return
        }
        // před žádostí o kameru jednou věta, k čemu je (dřív šedý text pod tlačítkem)
        val ask = { requestPermissions(arrayOf(android.Manifest.permission.CAMERA), REQ_CAMERA) }
        try {
            android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(t("km_scan"))
                .setMessage(t("km_why"))
                .setPositiveButton(t("ok")) { d, _ -> d.dismiss(); ask() }
                .setNegativeButton(t("lg_cancel"), null)
                .show()
        } catch (_: Exception) { ask() }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        @Suppress("DEPRECATION") super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_CAMERA) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) scan()
        else { scanNote = t("km_denied") to true; render(true) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SCAN) return
        if (resultCode == RESULT_OK && data != null) {
            data.getStringExtra(LgScanActivity.EXTRA_IP)?.let { ip = it }
            data.getStringExtra(LgScanActivity.EXTRA_PASS)?.let { pass = it }
            val unsure = data.getBooleanExtra(LgScanActivity.EXTRA_UNSURE, false)
            scanNote = (t("km_filled") + if (unsure) "\n" + t("km_unsure") else "") to unsure
            error = null; resultText = null
            // adresa i Passphrase přečtené jistě: nahrát samo, jakmile jsou kroky hotové (zjistí se hned, pak každé 3 s)
            autoGo = if (!unsure && ip.isNotEmpty() && LgSetup.passphraseLooksOk(pass)) ip to pass else null
            render()
            content.findViewWithTag<View>("lggo")?.requestFocus()
            if (autoGo != null) { autoStart(); check() }
        } else if (data?.getBooleanExtra(LgScanActivity.EXTRA_FAILED, false) == true) {
            scanNote = t("km_cam_fail") to true
            render(true)
        }
    }

    private fun <V : View> tagged(v: V, tag: String): V { v.tag = tag; return v }

    /** Hlavní tlačítko bylo „Spustit“ (v TV aktuální verze) – průběh ukazuje jen spouštění. */
    private var launching = false

    /** Adresa a Passphrase přečtené kamerou bez nejistoty – nahrát samo, až budou kroky hotové (null = nic). */
    private var autoGo: Pair<String, String>? = null

    /** Údaje z kamery platí (hráč je nepřepsal) a všechny kroky přípravy jsou hotové → spustit nahrání. */
    private fun autoStart() {
        val go = autoGo ?: return
        if (running) return
        if (go.first != ip || go.second != pass) { autoGo = null; return }
        if (LgSetup.step(probe.copy(passphrase = LgSetup.passphraseLooksOk(pass))) != LgSetup.Step.READY) return
        if (runCatching { LgInstall.address(ip) }.getOrNull() == null) { autoGo = null; return }
        autoGo = null
        start(false)
    }

    private val fromWizard get() = intent?.getBooleanExtra(EXTRA_WIZARD, false) == true

    private fun start(repair: Boolean) {
        if (running) return
        launching = !repair && act() == Act.LAUNCH
        val inst = LgInstaller(this)
        installer = inst
        running = true; phase = LgPhase.PACKAGE; error = null; resultText = null; scanNote = null; offerRenew = false
        phaseTimes.clear(); phaseTimes[LgPhase.PACKAGE] = System.currentTimeMillis()
        render()
        val ipNow = ip; val passNow = pass
        Thread({
            try {
                val r = inst.run(ipNow, passNow, repair) { p -> runOnUiThread {
                    if (!phaseTimes.containsKey(p)) phaseTimes[p] = System.currentTimeMillis()
                    phase = p; if (!isFinishing) render(true) } }
                runOnUiThread { done(r, null) }
            } catch (e: LgException) {
                runOnUiThread { done(null, e) }
            } catch (e: Exception) {
                runOnUiThread { done(null, LgException(LgError.SSH)) }
            }
        }, "tp-lg-install").start()
    }

    private fun done(r: LgInstaller.Result?, e: LgException?) {
        running = false; installer = null
        if (isFinishing || isDestroyed) return
        if (r != null) {
            pass = ""
            resultOk = true
            val key = when (r.action) {
                LgAction.INSTALL -> "lg_r_install"
                LgAction.UPDATE -> "lg_r_update"
                LgAction.LAUNCH -> "lg_r_same"
                LgAction.LAUNCH_NEWER -> "lg_r_newer"
            }
            resultText = t(key).replace("%s", r.version) + "\n" + t(if (r.launched) "lg_r_launched" else "lg_r_nolaunch")
            LgDevMode.refresh(this, false) { runOnUiThread { if (!isFinishing && !running) render(true) } }
            // verze v TV je teď ta nahraná: Domů ji ví hned, i když se aplikace v TV ještě znovu nepřipojila
            // (do 0.97.21 tam zůstávalo staré ohlášení a Domů dál hlásilo „nová …“)
            LgUpdate.recordInstalled(this, r)
            // po prvním nahrání nabídnout prodlužování vývojářského režimu (od 1.0.5, přání majitele) – jednou, výchozí vypnuto
            offerRenew = !Prefs.lgDevRenew(this) && !getSharedPreferences("ui_jednou", MODE_PRIVATE).getBoolean("lg_prodlouzeni", false)
            // potvrzení jen zeleným řádkem nahoře stránky (dialog „v pořádku“ majitel nechce, 22. 11. 2026)
            // z průvodce: po chvíli (zelený řádek je vidět) zpět do kroku Televize, ten ukáže ✔ – s nabídkou až po odpovědi
            if (fromWizard && !offerRenew) main.postDelayed({ if (!isFinishing && !running) finish() }, 2500)
        } else if (e != null && e.code != LgError.CANCELLED) {
            error = e.code
            // podrobnost (odpověď TV, výjimka) jen do záznamu – na obrazovku nepatří (přání majitele 22. 11. 2026)
            val line = "LG|chyba ${e.code}" + (e.detail?.let { " · $it" } ?: "")
            EventFile.add(line); cz.recaption.core.DiagLog.add(line)
        }
        render()
    }

    companion object {
        private const val REQ_CAMERA = 41
        private const val REQ_SCAN = 42
        /** Otevřeno z Domů „Aktualizovat v TV“: spárovanou TV rovnou aktualizovat. */
        const val EXTRA_UPDATE = "update"
        /** Otevřeno z kroku Televize v průvodci: po úspěchu stránku zavřít (krok ukáže ✔). Totéž u Samsungu. */
        const val EXTRA_WIZARD = "z_pruvodce"

        /** Řádek o vývojářském režimu (text, upozornit?) – i pro Nastavení a pomocníka v telefonu. */
        fun devLine(c: Context): Pair<String, Boolean> {
            val time = LgDevMode.known(c) ?: return c.tx("lg_dev_unknown") to false
            val now = System.currentTimeMillis()
            val days = time.days(now)
            val text = if (days >= 1) c.tx("lg_dev_days").replace("%d", days.toString())
                else c.tx("lg_dev_hours").replace("%d", time.hours(now).toString())
            return text to time.warn(now)
        }

        /** Krátký řádek pro Nastavení; null = o televizi zatím nic nevíme. */
        fun shortLine(c: Context): Pair<String, Boolean>? {
            val time = LgDevMode.known(c) ?: return null
            val l = devLine(c)
            return (c.tx("lg_dev_h") + ": " + l.first) to time.warn(System.currentTimeMillis())
        }
    }
}

