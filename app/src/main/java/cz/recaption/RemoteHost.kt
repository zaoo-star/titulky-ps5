package cz.recaption

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import cz.recaption.core.Console
import cz.recaption.core.Deepl
import cz.recaption.core.LoginServer
import cz.recaption.core.PhoneLogin
import cz.recaption.core.Ps5
import cz.recaption.core.RemoteSetup
import cz.recaption.core.SetupPage
import cz.recaption.core.TvSettings
import cz.recaption.core.WebSettings
import cz.recaption.core.UpdateCheck

/**
 * Nastavení Android TV z telefonu – strana televize (návrh `navrhy/nastaveni-z-telefonu.md`).
 *
 * Televize vystaví v domácí síti stránku se stálým klíčem ([Prefs.remoteKey], ve čtverečku v ⚙ → Obecné
 * → „Nastavit z telefonu“). ReCaption v telefonu přes ni:
 *  - [RemoteSetup.OP_SNAPSHOT] – stáhne celé nastavení TV,
 *  - [RemoteSetup.OP_SET] – pošle změněné volby (uloží se a služba si je hned načte),
 *  - [RemoteSetup.OP_DEEPL] – pošle klíč DeepL (ověří se a uloží zašifrovaný jako dosud).
 * Prohlížeč (GET) dostane od 0.97.63 stránku „Nastavení TV“ ([SetupPage], iPhone i Android): 🔵 účet pro ReCaption
 * ([SetupPage.OP_ACCOUNT] – adresa „redirect“; z ReCaptionu v telefonu pole `account`), osmimístné číslo z PS5
 * ([SetupPage.OP_CODE] – TV spáruje sama, [pair]), DeepL a stav ([SetupPage.OP_INFO]).
 * Běží jen na Android TV / boxu, ne v telefonu.
 */
object RemoteHost {
    const val PORT = 8766
    private var server: LoginServer? = null
    private val main = Handler(Looper.getMainLooper())

    /** Obrazovka aplikace v TV se po změně z telefonu překreslí (nastavuje MainActivity). */
    @Volatile var onChanged: (() -> Unit)? = null

    /** Smí toto zařízení přijímat nastavení z telefonu? Jen televize nebo box, ne telefon/tablet. */
    fun eligible(c: Context): Boolean = !NetInfo.isHandheld(c) && !Prefs.forceTv(c)

    @Synchronized fun start(c: Context): Boolean {
        val app = c.applicationContext
        if (!eligible(app)) return false
        server?.takeIf { it.running }?.let { return true }
        val s = LoginServer.forPage({ Prefs.uiLang(app) }, object : LoginServer.Custom {
            override fun get(path: String) = page(app)
            override fun gone() = RemoteSetup.page(Prefs.uiLang(app), "")
            override fun post(path: String, form: Map<String, String>): String = handle(app, form)
        }, log = { Log.i("TP_TEST", "REMOTE|$it") }, fixedKey = Prefs.remoteKey(app))
        if (!s.start(PORT)) return false
        server = s
        return true
    }

    @Synchronized fun stop() { server?.stop(); server = null }

    /** Krok průvodce, který TV právě ukazuje (-1 = průvodce neběží); zapisuje MainActivity, čte telefon ([RemoteSetup.OP_STEP]). */
    @Volatile var tvStep = -1
    /** Telefon přešel na jiný krok průvodce → TV ho ukáže taky (volá se na hlavním vlákně). */
    @Volatile var onStep: ((Int) -> Unit)? = null

    /** Adresa stránky pro čtvereček; null = televize není v domácí síti nebo server neběží. */
    fun pageUrl(c: Context): String? = target(c)?.let { PhoneLogin.pageUrl(it) }

    /** Odkaz do aplikace v telefonu (na stránce z TV). */
    fun link(c: Context): String? = target(c)?.let { RemoteSetup.appLink(it, tvName()) }

    private fun target(c: Context): PhoneLogin.Target? {
        val s = server?.takeIf { it.running } ?: return null
        val ip = NetInfo.homeAddress(c) ?: return null
        return PhoneLogin.Target(ip, s.port, s.key)
    }

    fun tvName(): String = listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }
        .joinToString(" ").replaceFirstChar { it.uppercase() }.take(60)

    /** Stránka pro telefon (texty v jazyce aplikace v TV). */
    private fun page(c: Context): String = SetupPage.page(Prefs.uiLang(c), tvName(), target(c),
        UpdateCheck.url(if (Prefs.testChannel(c)) UpdateCheck.Channel.TEST else UpdateCheck.Channel.STABLE, "ReCaption.apk"),
        PsnAuth.LOGIN_URL, Deepl.SIGNUP_URL, Deepl.KEYS_URL, cz.recaption.core.HelpLinks.IPHONE_SHORTCUT, appModeLink = link(c),
        web = webOptions(c), xbox = Prefs.console(c) == Console.XBOX)

    /** Volby pro webovou obdobu aplikace (texty v jazyce aplikace v TV). */
    private fun webOptions(c: Context): WebSettings.Options {
        val ui = Prefs.uiLang(c)
        fun t(k: String) = cz.recaption.core.I18n.t(ui, k)
        return WebSettings.Options(
            langs = cz.recaption.core.Lang.targets.map { it.code to it.label },
            sources = cz.recaption.core.SourceLang.offered(Prefs.lang(c)).map { it.code to it.label },
            games = cz.recaption.core.GameProfiles.ALL.map {
                WebSettings.GameOption(it.id, if (it.id == "all") t("game_universal") else it.title, it.prompts, it.menuSpec != null)
            },
            positions = TvSettings.POSITIONS.map { (k, p) -> k to t("pos_" + p.name.lowercase()) },
            sizeByGame = t("qk_size_game"),
        )
    }

    /** Současné hodnoty a přehled stavu pro stránku (dotaz každé 2 s – nic drahého: žádné dešifrování klíče). */
    private fun webState(c: Context) = WebSettings.State(
        lang = Prefs.lang(c).code, src = Prefs.sourceLang(c).code, game = Prefs.gameId(c),
        opacity = Prefs.opacity(c), size = if (Prefs.matchSize(c)) 0 else Prefs.textSize(c),
        position = TvSettings.POSITIONS.entries.firstOrNull { it.value == Prefs.layout(c).position }?.key ?: "vlastni",
        original = Prefs.showOriginal(c), prompts = Prefs.gamePrompts(c), objective = Prefs.objectiveOn(c), menu = Prefs.gameMenu(c),
        deeplKey = DeeplKey.has(c), deeplOn = DeeplKey.has(c) && DeeplKey.enabled(c),
        rows = TvStatus.rows(c),
    )

    /** Změna z webové obdoby aplikace – jen povolené volby a hodnoty ([WebSettings.parse]). */
    private fun webChange(c: Context, form: Map<String, String>): String {
        val ch = WebSettings.parse(form["klic"], form["hodnota"], cz.recaption.core.GameProfiles.ALL.map { it.id }) ?: return RemoteSetup.ERR_BAD
        when (ch) {
            is WebSettings.Change.Target -> { Prefs.setLang(c, ch.lang); OfflineTranslator.ensureModel(ch.lang) { } }
            is WebSettings.Change.Source -> { Prefs.setSourceLang(c, ch.lang); OfflineTranslator.ensureModel(Prefs.lang(c)) { } }
            is WebSettings.Change.Game -> Prefs.setGameId(c, ch.id)
            is WebSettings.Change.Look -> TvLookPrefs.apply(c, ch.change)
            is WebSettings.Change.Prompts -> Prefs.setGamePrompts(c, ch.on)
            is WebSettings.Change.Objective -> Prefs.setObjectiveOn(c, ch.on)
            is WebSettings.Change.Menu -> Prefs.setGameMenu(c, ch.on)
            is WebSettings.Change.Deepl -> if (DeeplKey.has(c)) DeeplKey.setEnabled(c, ch.on) else return RemoteSetup.ERR_BAD
        }
        Log.i("TP_TEST", "REMOTE|web ${ch.javaClass.simpleName}")
        changed(c)
        return "OK"
    }

    /** Spárování spuštěné z telefonu (číslo z PS5) – stav pro stránku i pro obrazovku v TV. */
    @Volatile var pair = SetupPage.Pair.IDLE
        private set
    /** Proč spárování z telefonu nevyšlo (pro TV, ne pro stránku). */
    @Volatile var pairError: String? = null
        private set
    @Volatile private var failAt = 0L
    /** Poslední nepovedené spárování z telefonu bylo kvůli víc PS5 v síti. */
    @Volatile private var multi = false

    /** Stav pro stránku v telefonu. */
    fun status(c: Context): SetupPage.Status {
        val id = Prefs.psnId(c)
        val host = Prefs.ps5Host(c)
        return SetupPage.Status(
            account = if (Ps5.parseAccountId(id) != null) Prefs.psnName(c) else null,
            // nepovedené nové spárování (i když staré platí) stránka ukáže ještě 3 minuty, ať hráč ví, že číslo nevyšlo
            pair = when {
                pair == SetupPage.Pair.BUSY -> pair
                pair == SetupPage.Pair.FAIL && (host == null || android.os.SystemClock.elapsedRealtime() - failAt < 180_000L) -> pair
                host != null -> SetupPage.Pair.OK
                else -> pair
            },
            console = host?.name,
            deepl = DeeplKey.has(c),
            multiPs5 = multi && pair == SetupPage.Pair.FAIL,
        )
    }

    /**
     * Osmimístné číslo z telefonu → TV spáruje sama (adresu PS5 zná z dřívějška, jinak ji najde v síti). Spárovat
     * jde jen s ověřeným 🔵 účtem; číslo musí na PS5 vyvolat týž uživatel (jinak CE-110032-7 – stránka to řekne).
     */
    private fun startPair(c: Context, pin: Int): String {
        val account = Ps5.parseAccountId(Prefs.psnId(c)) ?: return "NOACC"
        synchronized(this) {
            if (pair == SetupPage.Pair.BUSY) return "OK"
            pair = SetupPage.Pair.BUSY; pairError = null; multi = false
        }
        changed(c)
        Thread({
            fun fail(why: String) { pairError = why; failAt = android.os.SystemClock.elapsedRealtime(); pair = SetupPage.Pair.FAIL; changed(c) }
            try {
                // vždy nejdřív hledat v síti (od 0.97.69): uložená adresa může patřit jiné, už spárované PS5 nebo být stará
                val saved = Prefs.ps5Ip(c)
                val found = Ps5Pairing.discover()
                val paired = Prefs.ps5Hosts(c).map { it.ip }
                val ip = when {
                    found.size == 1 -> found[0].ip
                    // víc PS5 v síti: jen adresa zadaná v TV pro párování (ne už spárovaná), jinak se zvolí v TV (Konzole → Najít)
                    found.size > 1 -> found.firstOrNull { it.ip == saved && saved !in paired }?.ip ?: run {
                        multi = true; EventFile.add("PAIR|z telefonu: v síti víc PS5 (${found.size})"); fail("multi"); return@Thread
                    }
                    else -> saved   // nic nenalezeno (hledání v síti blokované?) – zkusit uloženou
                }
                if (!Ps5.validHost(ip)) { EventFile.add("PAIR|z telefonu: PS5 v síti nenalezena"); fail("ps5_notfound"); return@Thread }
                Prefs.setPs5Ip(c, ip)
                val name = Prefs.psnName(c).ifEmpty { "?" }
                Ps5Pairing.pair(ip, account, pin) { h ->
                    EventFile.add("PAIR|z telefonu: " + (if (h != null) "spárováno ${h.name} ${h.ip}" else "nepovedlo se ($ip)") +
                        " · účet $name" + (Ps5Pairing.lastError?.takeIf { h == null }?.let { " · $it" } ?: ""))
                    if (h != null) { Prefs.setPs5Host(c, h); pair = SetupPage.Pair.OK; changed(c) }
                    else fail(Ps5Pairing.lastError ?: "")
                }
            } catch (e: Throwable) { fail(e.javaClass.simpleName) }
        }, "tp-remote-pair").start()
        return "OK"
    }

    private fun handle(c: Context, form: Map<String, String>): String {
        // ReCaption v telefonu (pomocník přihlášení) posílá ověřený účet stejně jako stránce přihlášení
        if (form.containsKey("account")) {
            val acc = PhoneLogin.cleanAccount(form["account"], form["name"]) ?: return RemoteSetup.ERR_BAD
            useAccount(c, acc)
            return "OK"
        }
        val v = form["v"]?.toIntOrNull() ?: 0
        if (v > RemoteSetup.VERSION) return RemoteSetup.ERR_VERSION
        return when (form["op"]) {
            SetupPage.OP_INFO -> SetupPage.status(status(c), WebSettings.json(webState(c)))
            RemoteSetup.OP_STEP -> {
                // krok z telefonu platí hned (další dotaz telefonu ho už vrátí), obrazovka TV se překreslí na hlavním vlákně
                form["krok"]?.toIntOrNull()?.takeIf { it >= 0 && tvStep >= 0 && it != tvStep }?.let { n ->
                    tvStep = n
                    Log.i("TP_TEST", "REMOTE|krok z telefonu $n")
                    Handler(Looper.getMainLooper()).post { onStep?.invoke(n) }
                }
                tvStep.toString()
            }
            WebSettings.OP -> webChange(c, form)
            SetupPage.OP_ACCOUNT -> {
                val code = Ps5.authCodeFromTyped(form["url"] ?: "") ?: return RemoteSetup.ERR_BAD
                val acc = try { PsnAuth.accountForCode(code) } catch (_: Throwable) { null } ?: return RemoteSetup.ERR_BAD
                useAccount(c, acc)
                "OK"
            }
            SetupPage.OP_CODE -> {
                val pin = SetupPage.pin(form["pin"]) ?: return RemoteSetup.ERR_BAD
                startPair(c, pin)
            }
            RemoteSetup.OP_SNAPSHOT -> RemoteSetup.snapshot(tvName(), Prefs.remoteValues(c), DeeplKey.has(c), DeeplKey.enabled(c),
                SubtitleOverlay.canShow(c))
            RemoteSetup.OP_SET -> {
                val values = RemoteSetup.decode(form["data"] ?: "") ?: return RemoteSetup.ERR_BAD
                Prefs.applyRemote(c, values)
                Log.i("TP_TEST", "REMOTE|set " + values.keys.joinToString(","))
                changed(c)
                "OK"
            }
            RemoteSetup.OP_DEEPL -> {
                // jen zapnout / vypnout DeepL (Titulky → Kvalita překladu v telefonu); zapnout jde jen s klíčem
                if (form["key"] == null && form["on"] != null) {
                    DeeplKey.setEnabled(c, form["on"] == "1" && DeeplKey.has(c))
                    changed(c)
                    return "OK"
                }
                val key = Deepl.cleanKey(form["key"])
                if (Deepl.keyKind(key) == null) return RemoteSetup.ERR_BAD
                val r = DeeplTranslator.check(key)
                if (r.problem == Deepl.Problem.BAD_KEY) return "REJECTED"
                if (DeeplKey.get(c) != key) {
                    val first = !DeeplKey.has(c)
                    DeeplKey.save(c, key)
                    if (first) DeeplKey.setEnabled(c, true)
                }
                DeeplKey.saveCheck(c, r)
                Log.i("TP_TEST", "REMOTE|deepl")
                changed(c)
                "OK"
            }
            else -> RemoteSetup.ERR_BAD
        }
    }

    /** Ověřený 🔵 účet z telefonu: uložit s jménem; jiný účet = staré spárování z telefonu neplatí. */
    private fun useAccount(c: Context, a: PhoneLogin.Account) {
        Prefs.setPsnAccount(c, a.id, PhoneLogin.cleanName(a.name))
        if (pair == SetupPage.Pair.FAIL) pair = SetupPage.Pair.IDLE
        Log.i("TP_TEST", "REMOTE|account")
        EventFile.add("LOGIN|účet z telefonu (nastavení TV)")
        changed(c)
    }

    /** Služba si nastavení načte znovu a obrazovka v TV se překreslí. */
    private fun changed(c: Context) {
        // jen do běžící služby – jinak by ji změna nastavení sama spustila
        if (TranslatorService.running) try {
            c.startService(Intent(c, TranslatorService::class.java).setAction(TranslatorService.ACTION_RELOAD))
        } catch (_: Exception) {}
        main.post { onChanged?.invoke() }
    }
}
