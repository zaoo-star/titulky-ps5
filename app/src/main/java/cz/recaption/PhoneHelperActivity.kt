package cz.recaption

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import cz.recaption.core.DiagLog
import cz.recaption.core.Lang
import cz.recaption.core.LoginClient
import cz.recaption.core.PhoneLogin
import cz.recaption.core.Ps5
import cz.recaption.core.RemoteSetup
import cz.recaption.core.UpdateCheck
import cz.recaption.core.TvType
import java.util.Locale

/**
 * ReCaption v telefonu: pomocník pro přihlášení účtem PlayStation (v telefonu se nepřekládá).
 *
 * Otevře se odkazem ze stránky, kterou televize vystavuje (čtvereček na obrazovce) – tím se dozví,
 * kde televize čeká. Uživatel se přihlásí na stránce Sony přímo tady, aplikace si z adresy, na
 * kterou Sony po přihlášení přesměruje, vezme jednorázový kód, zjistí číslo účtu a pošle ho
 * televizi po domácí síti. Číslo účtu si zapamatuje: příště (nová televize, nové párování) se
 * pošle jedním klepnutím bez přihlašování. Heslo se nikam neukládá.
 *
 * Záloha, kdyby Sony přihlášení uvnitř aplikace nepustila: přihlásit se v běžném prohlížeči
 * a prázdnou stránku „redirect“ nasdílet do ReCaptionu.
 */
class PhoneHelperActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var root: View
    private lateinit var content: LinearLayout
    /** Kde televize čeká na účet (z odkazu na stránce televize); null = zatím nevíme. */
    private var target: PhoneLogin.Target? = null
    private var msg: String? = null
    private var msgColor = Theme.MUTED
    private var busy = false
    /** Účet už je v televizi – není co dalšího posílat, dokud nepřijde nový odkaz. */
    private var sent = false
    /** Otevřená přihlašovací stránka Sony (null = je vidět hlavní obrazovka pomocníka). */
    private var web: WebView? = null
    /** Novější verze aplikace, která je ke stažení (null = žádná, nebo se to ještě nezjistilo). */
    private var update: UpdateCheck.Offer? = null
    /**
     * Odkaz z vlastní aplikace v tomtéž telefonu (ReCaption jako čtečka, krok přihlášení): bez uložených účtů se rovnou
     * otevře přihlášení Sony a po odeslání účtu se pomocník zavře – průvodce v aplikaci už je na dalším kroku.
     */
    private var self = false

    private fun t(key: String) = tx(key)
    private fun log(m: String) { Log.i("TP_TEST", "HELPER|$m"); DiagLog.add("HELPER|$m") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        // jazyk: poprvé podle telefonu (později ho může přepsat jazyk televize z odkazu, nebo uživatel)
        // (ne v režimu „Nastavuji TV“ – jazyk by se zapsal do nastavení TV)
        if (!RemoteClient.on(this) && !Prefs.uiLangChosen(this)) Prefs.setUiLang(this, Lang.ui(Locale.getDefault().language) ?: Lang.EN)
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Theme.BG) }
        content = ui.vbox().apply { setPadding(ui.dp(20), ui.dp(28), ui.dp(20), ui.dp(36)) }
        scroll.addView(content)
        root = scroll
        setContentView(root)
        log("start")
        Updater.cleanUp(this)
        render()
        if (savedInstanceState == null) take(intent)
        checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        try { UsagePing.opened(this) } catch (_: Throwable) {}
        // po návratu z dotazu systému na instalaci ukázat, jak aktualizace dopadla
        if (web == null) render()
        Updater.resumed { if (!isFinishing && web == null) render() }
        // vývojářský režim televize LG: nejvýš jednou za hodinu se zeptat, kolik zbývá
        LgDevMode.refresh(this, false) { runOnUiThread { if (!isFinishing && web == null) render() } }
        // i po návratu do aplikace se zeptat na novou verzi (nejvýš jednou za 5 minut)
        if (android.os.SystemClock.elapsedRealtime() - lastUpdateCheck > 5 * 60_000L) checkForUpdate()
    }

    private var lastUpdateCheck = 0L

    /** Na pozadí zjistí, jestli není ke stažení novější verze (se zapnutým zkušebním kanálem i v něm). */
    private fun checkForUpdate() {
        lastUpdateCheck = android.os.SystemClock.elapsedRealtime()
        val test = Prefs.testChannel(this)
        Thread({
            val offer = Updater.offer(test)
            runOnUiThread { if (!isFinishing && offer != update && !Updater.busy) { update = offer; if (web == null) render() } }
        }, "tp-helper-update").start()
    }

    /** Stáhne nabídnutou verzi a předá ji systému k instalaci – stejně jako v televizi. */
    private fun startUpdate(o: UpdateCheck.Offer) {
        Updater.start(this, UpdateCheck.url(o.channel, Updater.apkFile()), false) {
            runOnUiThread { if (!isFinishing && web == null) render() }
        }
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        take(intent)
    }

    companion object {
        /** Otevřeno z celé aplikace (⚙ → Pomocník pro přihlášení) – nahoře „Zpět do aplikace“, Zpět vede tam. */
        const val EXTRA_FROM_APP = "z_aplikace"
    }

    private val fromApp get() = intent?.getBooleanExtra(EXTRA_FROM_APP, false) == true

    override fun onDestroy() {
        web?.destroy(); web = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val w = web
        @Suppress("DEPRECATION")
        when {
            w == null -> if (fromApp) finish() else super.onBackPressed()
            w.canGoBack() -> w.goBack()
            else -> { closeLogin(); render() }
        }
    }

    /** Čím byla aplikace otevřena: odkazem ze stránky televize, nebo sdílením z prohlížeče. */
    private fun take(i: Intent?) {
        when (i?.action) {
            Intent.ACTION_VIEW -> {
                val link = i.dataString
                // čtvereček „Nastavit z telefonu“ v Android TV: celá aplikace v režimu „Nastavuji TV“ (RemoteClient)
                if (link?.startsWith("recaption://tv") == true) { connectTv(link); return }
                val tg = PhoneLogin.parseAppLink(link)
                log("link " + if (tg != null) "ok" else "bad")
                if (tg != null) {
                    target = tg; sent = false; msg = null
                    self = PhoneLogin.selfFromAppLink(link)
                    if (!Prefs.uiLangChosen(this)) PhoneLogin.langFromAppLink(link)?.let { Prefs.setUiLang(this, it) }
                }
                render()
                // z vlastní aplikace a v telefonu žádný uložený účet: rovnou přihlášení Sony (s uloženými účty dál seznam –
                // účet se vždy volí vědomě, nikdy potichu)
                if (tg != null && self && Prefs.phoneAccounts(this).isEmpty() && !busy) openLogin()
            }
            Intent.ACTION_SEND -> shared(i.getStringExtra(Intent.EXTRA_TEXT) ?: "")
        }
        // jen ve zkušebním sestavení: rovnou otevřít přihlášení (snímek ze zkoušky v emulátoru)
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (i?.getBooleanExtra("helperlogin", false) == true && debuggable) openLogin()
        // jen ve zkušebním sestavení: celá aplikace v telefonu (stejně jako tlačítko pro televizi LG)
        if (i?.getBooleanExtra("fullapp", false) == true && debuggable) useForTv(TvType.LG)
    }

    private fun add(v: View, top: Int = 12) = content.addView(v, ui.lp(top = top))

    private fun note(text: String?, color: Int = Theme.MUTED) { msg = text; msgColor = color; if (web == null) render() }

    private fun render() {
        content.removeAllViews()
        val head = ui.hbox()
        head.addView(ImageView(this).apply { setImageResource(R.drawable.ic_logo) }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        val names = ui.vbox().apply { setPadding(ui.dp(12), 0, 0, 0) }
        names.addView(ui.text("Titulky PS5", 22f, Theme.TEXT, bold = true))
        names.addView(ui.text(t("hp_title"), 14f, Theme.ACCENT, bold = true))
        head.addView(names)
        content.addView(head)
        // otevřeno z celé aplikace: cesta zpět (od 0.97.55)
        if (fromApp) content.addView(ui.button("‹ " + t("hp_back_app")) { finish() }.apply { textSize = 15f },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.dp(14) })
        // výsledek / chyba (přihlášení, odeslání účtu, spojení s TV) hned pod hlavičkou, ne pod kartou TV
        // a návodem (přání majitele 22. 11. 2026)
        msg?.let { add(ui.text(it, 17f, msgColor, bold = true), 14) }
        // připojování k TV, dotaz na Sony, posílání účtu: slabý běžící pruh pod hláškou
        if (busy && msg != null) content.addView(ui.waiting(0).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(6) })

        // režim „Nastavuji TV“: zpět do nastavení TV, nebo odpojit (telefon se vrátí ke svému nastavení)
        if (RemoteClient.on(this)) add(ui.card().apply {
            val (st, warn) = RemoteClient.stateText(this@PhoneHelperActivity)
            addView(ui.text(t("rs_bar").replace("%s", RemoteClient.tvName) + " · " + st, 17f, if (warn) Theme.WARN else Theme.OK, bold = true))
            addView(ui.button(t("rs_open"), primary = true) { if (!busy) openFullApp() }, ui.lp(top = 12))
            addView(ui.button(t("rs_disconnect")) { if (!busy) { RemoteClient.disconnect(this@PhoneHelperActivity); render() } }, ui.lp(top = 8))
        }, 14)

        // nová verze: stejná nabídka jako na stránce Domů v televizi
        update?.let { o ->
            val st = Updater.state
            add(ui.card().apply {
                addView(ui.text(t("upd_new") + " (${o.remote.name}" + (if (o.channel == UpdateCheck.Channel.TEST) " · " + t("upd_test") else "") + ")", 17f, Theme.ACCENT, bold = true))
                val line = when {
                    st == Updater.State.DOWNLOADING -> t("upd_down") + (if (Updater.percent >= 0) " ${Updater.percent} %" else "…")
                    st == Updater.State.INSTALLING -> Updater.text(this@PhoneHelperActivity, "upd_inst")
                    st == Updater.State.FAILED -> Updater.text(this@PhoneHelperActivity, Updater.problem ?: "upd_fail")
                    else -> null
                }
                line?.let { addView(ui.text(it, 14f, if (st == Updater.State.FAILED) Theme.BAD else Theme.MUTED), ui.lp(top = 4)) }
                // pruh: kolik je staženo (instalace = čekání na systém, pruh jen běhá)
                if (st == Updater.State.DOWNLOADING) addView(ui.progress(if (Updater.percent >= 0) Updater.percent / 100f else null), ui.lp(h = ui.dp(10), top = 8))
                if (st == Updater.State.INSTALLING) addView(ui.progress(null), ui.lp(h = ui.dp(10), top = 8))
                if (st == Updater.State.FAILED) addView(ui.text(UpdateCheck.url(o.channel, "TitulkyPS5.apk"), 13f, Theme.MUTED), ui.lp(top = 6))
                if (!Updater.busy) addView(ui.button(t("upd_btn"), primary = true) { startUpdate(o) }, ui.lp(top = 10))
            }, 14)
        }

        // Televize LG a Samsung si titulky samy nepřečtou: číst, překládat a posílat je do nich může
        // tento telefon. Pak tu místo pomocníka běží celá aplikace (viz core/TvSender).
        add(ui.card().apply {
            addView(ui.text(t("hp_lg_title"), 18f, Theme.TEXT, bold = true))
            addView(ui.text(t("hp_lg_text"), 15f, Theme.MUTED), ui.lp(top = 6))
            // jedno tlačítko: druh TV (LG / Samsung) se zjistí v síti, nejasný = volba v aplikaci (krok Televize)
            addView(ui.button(t("hp_read_btn"), primary = true) { if (!busy) readForTv() }.apply { tag = "hpread" }, ui.lp(top = 12))
            // nahrát ReCaption do televize LG přímo z telefonu (vývojářský režim LG)
            addView(ui.button(t("lg_open")) { if (!busy) startActivity(Intent(this@PhoneHelperActivity, LgInstallActivity::class.java)) }, ui.lp(top = 8))
            LgInstallActivity.shortLine(this@PhoneHelperActivity)?.let { (s, warn) ->
                addView(ui.text(s, 14f, if (warn) Theme.WARN else Theme.MUTED, bold = warn), ui.lp(top = 6))
            }
            // Samsung: návod, uložení balíčku a ověření, že se televize připojila
            addView(ui.button(t("ss_open")) { if (!busy) startActivity(Intent(this@PhoneHelperActivity, SamsungInstallActivity::class.java)) }, ui.lp(top = 8))
            // Android TV / Google TV: nahrát přes ladění po síti, bez aplikace v televizi
            addView(ui.button(t("at_open")) { if (!busy) startActivity(Intent(this@PhoneHelperActivity, AtvInstallActivity::class.java)) }, ui.lp(top = 8))
        }, 18)
        add(ui.card().apply { addView(ui.text(t("hp_intro"), 15f, Theme.MUTED)) }, 10)
        val tg = target
        if (!sent) add(ui.card().apply {
            addView(ui.text(t(if (tg != null) "hp_tv_ready" else "hp_scan"), 16f, if (tg != null) Theme.OK else Theme.TEXT, bold = tg != null))
        }, 10)

        val accounts = Prefs.phoneAccounts(this)
        if (accounts.isNotEmpty()) {
            content.addView(ui.section(t("hp_saved")))
            accounts.forEachIndexed { i, a ->
                add(ui.card().apply {
                    addView(ui.text(a.name.ifEmpty { t("hp_acc") }, 18f, Theme.TEXT, bold = true))
                    addView(ui.button(t("wp_send"), primary = tg != null && !sent) { if (!busy) send(a) }, ui.lp(top = 12))
                    addView(ui.button(t("hp_forget")) {
                        if (!busy) { Prefs.setPhoneAccounts(this@PhoneHelperActivity, PhoneLogin.withoutAccount(Prefs.phoneAccounts(this@PhoneHelperActivity), a.id)); note(null) }
                    }, ui.lp(top = 8))
                }, if (i == 0) 0 else 10)
            }
        }
        add(ui.button(t("wp_login_btn"), primary = accounts.isEmpty()) { if (!busy) openLogin() }, 22)
        add(ui.button(t("hp_browser")) { if (!busy) openBrowser() }, 8)

        val lang = Prefs.uiLang(this)
        add(ui.settingRow(t("l_ui_lang"), lang.label) { Prefs.setUiLang(this, lang.next()); render() }, 26)
        // zkušební verze jen pro testery (Tester.kt): vypnutí = konec testování, řádek pak zmizí
        if (Tester.active(this)) add(ui.settingRow(t("s_test"), t("on")) {
            Prefs.setTestChannel(this, false); render(); checkForUpdate()
        }, 8)
        add(ui.button(t("hp_is_tv")) {
            if (RemoteClient.on(this)) RemoteClient.disconnect(this)
            Prefs.setForceTv(this, true)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }.apply { textSize = 14f; setTextColor(Theme.MUTED) }, 8)
        add(ui.text("Titulky PS5 · ${BuildInfo.VERSION}", 13f, Theme.MUTED), 20)
    }

    /**
     * Telefon bude číst a překládat pro televizi LG nebo Samsung (typ televize se nastaví na Domů).
     * Účet se tu potichu nebere (od 0.97.28): účty uložené v telefonu nabídne krok „Účet pro ReCaption“
     * při spárování se jménem a hráč ho vědomě zvolí – jinak PS5 hlásila CE-110032-7 (jiný uživatel).
     */
    private fun useForTv(type: TvType?) {
        // telefon jako čtečka pracuje se svým nastavením – režim „Nastavuji TV“ ukončit
        if (RemoteClient.on(this)) RemoteClient.disconnect(this)
        Prefs.setForceTv(this, true)
        // nejasný druh TV: typ zůstane (z Android TV se stane LG jako dřív) a aplikace nabídne volbu
        val t = type ?: Prefs.tvType(this).takeIf { it != TvType.ANDROID } ?: TvType.LG
        Prefs.setTvType(this, t)
        log(when (type) { TvType.LG -> "lg mode"; TvType.SAMSUNG -> "samsung mode"; else -> "tv mode, type unclear" })
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_PICK_TV, type == null))
        finish()
    }

    /**
     * „Číst pro TV“: druh televize (LG / Samsung) se zjistí hledáním v síti (asi 4 s, na pozadí); jediný nalezený
     * druh = rovnou, nic / obojí = aplikace nabídne krok Televize (v průvodci), jinak volbu typu TV.
     */
    private fun readForTv() {
        if (busy) return
        busy = true
        note(t("hp_read_btn") + "…")
        Thread({
            val type = try {
                cz.recaption.core.TvDetect.suggest(LgFinder.ssdp(this, 2000), LgFinder.samsung(this, 2000))
            } catch (_: Throwable) { null }
            runOnUiThread {
                busy = false
                if (isFinishing) return@runOnUiThread
                msg = null
                useForTv(type)
            }
        }, "tp-helper-findtv").start()
    }

    // ---------------- nastavení Android TV z telefonu (RemoteClient) ----------------

    /**
     * Odkaz `recaption://tv?…` ze stránky z TV: stáhnout nastavení TV a otevřít celou aplikaci v režimu
     * „Nastavuji TV“. Když to nejde, zůstane pomocník s krátkou hláškou (jiná Wi-Fi, starší ReCaption v TV…).
     */
    private fun connectTv(text: String?) {
        val link = RemoteSetup.parseLink(text)
        log("tv link " + if (link != null) "ok" else "bad")
        if (link == null) { note(t("rs_e_bad"), Theme.BAD); return }
        if (busy) return
        busy = true
        note(t("rs_connecting"))
        Thread({
            val r = RemoteClient.connect(this, link)
            runOnUiThread {
                busy = false
                log("tv connect $r")
                if (isFinishing) return@runOnUiThread
                when (r) {
                    RemoteClient.Connect.OK -> openFullApp()
                    RemoteClient.Connect.UNREACHABLE -> note(t("rs_e_net"), Theme.BAD)
                    RemoteClient.Connect.VERSION -> note(t("rs_e_version"), Theme.BAD)
                    RemoteClient.Connect.BAD -> note(t("rs_e_bad"), Theme.BAD)
                }
            }
        }, "tp-helper-tv").start()
    }

    /** Celá aplikace (v režimu „Nastavuji TV“ ji pustí [NetInfo.isPhone]); pomocník zůstává pod ní. */
    private fun openFullApp() {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_REMOTE, true))
    }

    // ---------------- přihlášení u Sony ----------------

    /** Přihlašovací stránka Sony přímo v aplikaci; po přihlášení si aplikace vezme kód sama. */
    private fun openLogin() {
        if (web != null) return
        val w = try { WebView(this) } catch (e: Throwable) {
            // telefon bez součásti pro zobrazení stránek – zbývá běžný prohlížeč
            log("webview missing " + e.javaClass.simpleName); openBrowser(); return
        }
        msg = null
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        // stránka Sony se má chovat stejně jako v běžném prohlížeči telefonu
        w.settings.userAgentString = w.settings.userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(w, true)
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = caught(request.url.toString())
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { caught(url) }
            override fun onPageFinished(view: WebView, url: String) { log("page " + (Uri.parse(url).host ?: "?")) }
        }
        val box = ui.vbox().apply { setBackgroundColor(Theme.BG) }
        box.addView(ui.text(t("hp_login_top"), 14f, Theme.ACCENT).apply { setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(10)) })
        box.addView(w, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val row = ui.hbox().apply { setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12)) }
        row.addView(ui.button(t("wz_back")) { closeLogin(); render() }.apply { textSize = 15f })
        row.addView(ui.button(t("hp_browser")) { closeLogin(); openBrowser() }.apply { textSize = 15f },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = ui.dp(10) })
        box.addView(row)
        web = w
        setContentView(box)
        log("login open")
        // předchozí přihlášení u Sony zapomenout (od 0.97.28): hráč vždy píše e-mail a heslo a vědomě volí
        // účet – jinak Sony potichu vzala naposledy přihlášený (často herní) účet a PS5 pak hlásila CE-110032-7.
        // Mažou se jen data přihlašovacího okna ReCaptionu (WebView), ne prohlížeče telefonu.
        try { android.webkit.WebStorage.getInstance().deleteAllData() } catch (_: Throwable) {}
        try {
            cookies.removeAllCookies { removed ->
                log("cookies cleared=$removed")
                cookies.flush()
                if (web === w) w.loadUrl(PsnAuth.LOGIN_URL)
            }
        } catch (e: Throwable) { log("cookies " + e.javaClass.simpleName); w.loadUrl(PsnAuth.LOGIN_URL) }
    }

    private fun closeLogin() {
        val w = web ?: return
        web = null
        try { w.stopLoading(); (w.parent as? ViewGroup)?.removeView(w); w.destroy() } catch (_: Throwable) {}
        setContentView(root)
    }

    /** Sony po přihlášení přesměruje na adresu s jednorázovým kódem – tu aplikace zachytí. */
    private fun caught(url: String): Boolean {
        if (!url.startsWith(PsnAuth.REDIRECT)) return false
        if (web == null) return true
        closeLogin()
        val code = Ps5.authCodeFromUrl(url)
        log("redirect code=" + (code != null))
        if (code == null) note(t("wp_bad"), Theme.BAD) else exchange(code)
        return true
    }

    /** Záloha: přihlášení v běžném prohlížeči, prázdná stránka se pak nasdílí do ReCaptionu. */
    private fun openBrowser() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PsnAuth.LOGIN_URL)))
            log("browser open")
        } catch (e: Throwable) { log("browser missing " + e.javaClass.simpleName) }
        note(t("hp_browser_text"), Theme.TEXT)
    }

    /** Text nasdílený z prohlížeče: adresa prázdné stránky „redirect“ s kódem. */
    private fun shared(text: String) {
        val url = text.split(Regex("\\s+")).firstOrNull { it.contains("code=") } ?: text
        val code = Ps5.authCodeFromTyped(url)
        log("shared code=" + (code != null))
        if (code == null) note(t("wp_bad"), Theme.BAD) else exchange(code)
    }

    /** Jednorázový kód → číslo účtu (dotaz na Sony), uložit a rovnou poslat televizi, když čeká. */
    private fun exchange(code: String) {
        if (busy) return
        busy = true
        note(t("ps5_acc_wait"))
        Thread({
            val acc = PsnAuth.accountForCode(code)
            runOnUiThread {
                busy = false
                if (isFinishing) return@runOnUiThread
                if (acc == null) { log("account failed"); note(t("wp_bad"), Theme.BAD); return@runOnUiThread }
                Prefs.setPhoneAccounts(this, PhoneLogin.withAccount(Prefs.phoneAccounts(this), acc))
                log("account saved named=" + acc.name.isNotEmpty())
                if (target != null && !sent) send(acc) else note(t("ps5_acc_done"), Theme.OK)
            }
        }, "tp-helper-code").start()
    }

    /** Pošle účet televizi po domácí síti. */
    private fun send(a: PhoneLogin.Account) {
        val tg = target
        if (tg == null || sent) { note(t("hp_scan"), Theme.WARN); return }
        if (busy) return
        busy = true
        note(t("wp_send") + "…")
        Thread({
            val r = LoginClient.send(tg, a)
            runOnUiThread {
                busy = false
                log("send $r")
                if (isFinishing) return@runOnUiThread
                if (r == LoginClient.Result.OK) {
                    sent = true; note(t("hp_sent"), Theme.OK)
                    // odkaz z vlastní aplikace: účet je v ní, průvodce už ukazuje další krok – pomocník zavřít
                    if (self) finish()
                }
                else note(t("hp_send_fail"), Theme.BAD)
            }
        }, "tp-helper-send").start()
    }
}
