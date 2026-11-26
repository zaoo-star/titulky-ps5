package cz.recaption

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import cz.recaption.core.ScreenLayout
import cz.recaption.core.Segment
import cz.recaption.core.DiagLog
import cz.recaption.core.GameProfiles
import cz.recaption.core.LoginClient
import cz.recaption.core.TvDetect
import cz.recaption.core.LoginServer
import cz.recaption.core.PhoneLogin
import cz.recaption.core.Console
import cz.recaption.core.Ps5
import cz.recaption.core.VideoGuide
import cz.recaption.core.Ps5Host
import cz.recaption.core.UpdateCheck
import cz.recaption.core.Novinky
import cz.recaption.core.SubtitleHistory
import cz.recaption.core.TranslationProbe
import cz.recaption.core.TvSender
import cz.recaption.core.TvType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hlavní obrazovka: vlevo menu s logem, vpravo obsah vybrané sekce.
 * Vše se ovládá šipkami a tlačítkem OK na ovladači od TV.
 */
class MainActivity : Activity() {

    companion object {
        private const val REQ_NOTIF = 1002
        private const val REQ_PS5_SCAN = 1003
        private const val REQ_PS5_CAMERA = 1004
        private const val REQ_PS5_IP_SCAN = 1005
        /** Otevřeno z pomocníka po spojení s TV (režim „Nastavuji TV“, [RemoteClient]). */
        const val EXTRA_REMOTE = "dalkove"
        /**
         * Otevřeno z pomocníka „Číst pro TV“, když hledání v síti druh TV nepoznalo: průvodce dojde ke kroku Televize sám,
         * po průvodci se nabídne volba typu TV.
         */
        const val EXTRA_PICK_TV = "vyber_tv"
        /**
         * Klíč stránky nastavení, který poslal telefon po nahrání ReCaptionu do Android TV ([AtvInstaller], od šablony
         * průvodce 24. 11. 2026): TV ho vezme (jen během prvního průvodce), otevře „nastavení telefonem“ a telefon se
         * k ní připojí sám – hráč nic neskenuje.
         */
        const val EXTRA_PHONE_KEY = "klic_telefonu"
        // kroky průvodce prvním spuštěním (pořadí = pořadí v menu průvodce; zkouška jím prochází naslepo)
        /** Jazyky: jazyk aplikace a z čeho se překládá – hned při prvním spuštění (přání majitele, 21. 11. 2026). */
        private const val W_LANGS = 0
        private const val W_DEVICE = 1
        /** Od 0.97.98 Televize hned za Zařízením (přání majitele: jazyky, pak TV a zařízení), pak Konzole. */
        private const val W_CONSOLE = 3
        /** Licence a platby v aplikaci: zatím skryté, odkryjí se při vydání (true). */
        private const val SHOW_LICENSE = false
        private const val W_TV = 2
        private const val W_LANG = 4
        /** Kvalita překladu: jen offline, nebo DeepL s vlastním klíčem (od 0.94). */
        private const val W_TRANSLATE = 5
        private const val W_PERM = 6
        private const val W_PAIR = 7
    }

    /**
     * Stránky aplikace v pořadí menu (zkouška jím prochází naslepo šipkami). [key] = nadpis stránky,
     * [menuKey] = text v menu. Nastavení je poslední, s ozubeným kolem. Historie se otevírá
     * z Nastavení, v menu není.
     */
    private enum class Section(val key: String, val menuKey: String = key) {
        // pořadí = pořadí v menu; od 0.97.52 Titulky před Konzolí (přání majitele), od 0.97.71 Videonávody za
        // Instalací – zkouška v emulátoru na něm stojí (07_menu_1–5, ⚙ poslední)
        HOME("m_home"), LOOK("m_look"), PS5("m_ps5", "c_console"), HELP("m_help"),
        SETTINGS("m_settings"), HISTORY("m_history")
    }

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var ui: Ui
    private lateinit var menu: LinearLayout
    /** Místo pro ozubené kolo (Nastavení): v telefonu vpravo nahoře, na televizi dole v levém sloupci. */
    private lateinit var gear: LinearLayout
    /** Telefon: vodorovně posuvná řada záložek menu (na televizi null). */
    private var menuScroll: HorizontalScrollView? = null
    /** Levý sloupec (televize, telefon na šířku): seznam kroků průvodce se posune, když se celý nevejde. */
    private var sideScroll: ScrollView? = null

    /** Posune vodorovnou řadu záložek tak, aby vybraná byla celá vidět (doprostřed, když to jde). */
    private fun scrollTabIntoView(scroll: HorizontalScrollView?, tab: View?, smooth: Boolean = true) {
        if (scroll == null || tab == null) return
        scroll.post {
            val x = maxOf(0, tab.left - (scroll.width - tab.width) / 2)
            if (smooth) scroll.smoothScrollTo(x, 0) else scroll.scrollTo(x, 0)
        }
    }
    private lateinit var content: LinearLayout
    /**
     * Pevná hlavička nad posuvnou částí (Nastavení: nadpis a záložky zůstávají nahoře, posouvá se jen obsah).
     * Jinde je prázdná a schovaná.
     */
    private lateinit var pinned: LinearLayout
    /** Titulky PS5 (TV): menu Nastavení vlevo (Obecné, Vzhled titulků…) a náhled titulků vpravo – jen na široké obrazovce. */
    private var sideNav: LinearLayout? = null
    private var previewPanel: LinearLayout? = null
    private var pageScroll: ScrollView? = null
    private var contentTop = 0
    private var section = Section.HOME
    private var demo: SubtitleOverlay? = null
    private var busyText: String? = null
    private var lastRunning = false
    private var lastPaused = false
    /** Krok průvodce prvním spuštěním; -1 = průvodce neběží. */
    private var wizard = -1
    private var draftEn = ""
    private var draftTr = ""
    /** Nabídka nové verze (null = žádná není). */
    private var update: UpdateCheck.Offer? = null
    /** Aplikace minule spadla → na stránce Domů upozornění (jen do zavření aplikace). */
    private var crashNote = false
    private var draftCode = ""
    private var codeError = false
    // párování s PlayStationem
    private var draftIp = ""
    private var draftPsn = ""
    private var draftPin = ""
    private var draftLogin = ""
    private var pairMsg: String? = null
    private var pairMsgColor = Theme.MUTED
    private var pairBusy = false
    private var pairStep = 0
    private var draftAuth = ""
    private var skipDraftSave = false
    // co průvodce udělá sám (přání majitele): najde TV v síti, PS5 v síti, po povolení pruhu a spárování pokračuje
    /** Krok Televize: kdy naposledy proběhlo hledání televize v síti (opakuje se po 10 s, dokud nic nenajde). */
    private var tvSearchAt = 0L
    /** Krok Televize: hráč typ televize zvolil sám – nalezená televize ho už nepřepíše. */
    private var tvPicked = false
    /** Krok Televize: televize nalezená v síti („LG · 192.168.1.40“), null = nic. */
    private var tvFound: String? = null
    /** Krok Povolení: hráč otevřel Usnadnění – po návratu s povolením průvodce pokračuje sám. */
    private var permAsked = false
    /** Krok 1 spárování: kdy naposledy proběhlo tiché hledání PS5 (zapnutá PS5 = rovnou dál). */
    private var ps5AutoFindAt = 0L
    /** Tiché hledání už jednou posunulo dál – po „Zpět“ na krok 1 už hráče znovu neposouvat. */
    private var ps5AutoFound = false
    /** Spárování právě proběhlo (krok Hotovo v průvodci ukáže „Spárováno …“). */
    private var justPaired = false
    /** Stránka pro telefon; běží jen po dobu kroků přihlášení účtem (null = neběží). */
    private var loginServer: LoginServer? = null
    private var lastLink = Ps5.Link.OFF
    private var lastFrames = 0L
    private var lastStage = cz.recaption.core.LinkStatus.Stage.OFF
    private var ticks = 0
    /** Kolik televizí LG / Samsung bylo připojeno a kdy se naposledy ohlásila (pro obnovu stránky Domů). */
    private var lastTv = -1
    private var lastTvSeen = 0L
    /** Odhad hry z titulků při posledním vykreslení – nový odhad překreslí Domů (oranžový řádek Hra). */
    private var lastGuess: String? = null
    private var lastProbe = Ps5Probe.State.UNKNOWN

    /** Titulky PS5: stav „Překlad z PC“ při posledním vykreslení (Domů se obnoví při změně). */
    private var lastPc = PcReceiverService.State.OFF
    private var lastPcShown = 0
    private var lastPcPair: cz.recaption.core.PcWake.Reply? = null

    private val ticker = object : Runnable {
        override fun run() {
            // obnov obrazovku, když se změnil stav překladu (např. zastavení z notifikace)
            ticks++
            // nová verze i když je aplikace otevřená celou hru (přání majitele 21. 11. 2026): každých 10 min jeden malý dotaz
            if (android.os.SystemClock.elapsedRealtime() - lastUpdateCheck > UPDATE_EVERY_MS) checkForUpdate()
            // stav pro aplikaci v TV (jen LG/Samsung, jen když překlad neběží; posílá se jen změna)
            if (ticks % 2 == 0) TvIdle.ensure(this@MainActivity)
            // stav PS5 před spuštěním překladu (Domů a stav pro TV) – hledání v síti nejvýš 1× za 20 s, na pozadí
            if (section == Section.HOME && wizard < 0 && !TranslatorService.running) Ps5Probe.poke(this@MainActivity)
            // průvodce / stránka Konzole: tiché hledání PS5 (nejvýš 1× za 20 s, na pozadí) a TV LG/Samsung v kroku Televize
            // (nejvýš 1× za 10 s, dokud nic nenašlo) – samo se hledá jen, když na to krok čeká
            ps5Scan()
            if (wizard == W_TV && Prefs.readerDevice(this@MainActivity) && !remote) findTv()
            // telefon nastavuje TV: krok, na který přešla TV (ovladačem), ukázat i tady
            if (remote && wizard >= 0 && ticks % 2 == 0) RemoteClient.pollStep { n -> goToSyncedStep(n) }
            val typing = currentFocus is EditText
            val changed = lastRunning != TranslatorService.running || lastPaused != TranslatorService.paused ||
                lastLink != TranslatorService.linkState || (lastFrames == 0L) != (TranslatorService.framesDecoded == 0L) ||
                lastStage != TranslatorService.linkStatus().stage ||
                (section == Section.HOME && (lastTv != TranslatorService.tvConnected || lastTvSeen != (TvSender.lastSeen?.at ?: 0L) ||
                    lastGuess != Prefs.gameGuess(this@MainActivity) || lastProbe != Ps5Probe.state ||
                    lastPc != PcReceiverService.state || lastPcShown != PcReceiverService.shownCount ||
                    lastPcPair != PcReceiverService.pairRequest))
            // náhled obrazu z PlayStationu se obnovuje každé 3 s (ne když uživatel zrovna píše)
            // (jen na stránce Konzole – Domů náhled nemá a překreslovat ho každé 3 s bylo zbytečné)
            val preview = TranslatorService.running && ticks % 3 == 0 && wizard < 0 && section == Section.PS5 && "preview" in openGroups
            if ((changed || preview) && !typing && !pairBusy) render(keepFocus = true)
            if (SnapRecorder.active || lastSnap) tickSnap(typing)
            main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v telefonu aplikace nepřekládá – je tam jen pomocník pro přihlášení účtem PlayStation
        if (NetInfo.isPhone(this)) {
            // Odpověď systému na aktualizaci spuštěnou z pomocníka přijde sem: jen ji vyřídit (ukázat
            // dotaz na instalaci) a zmizet – pomocník zůstává pod tím.
            if (!Updater.onStatus(this, intent)) startActivity(Intent(this, PhoneHelperActivity::class.java))
            finish()
            return
        }
        EventFile.init(this)
        crashNote = CrashLog.takeUnseen(this)
        ui = Ui(this)
        initXbox()
        buildShell()
        // Titulky PS5 (přání majitele 25. 11. 2026: „PC dělá vše“): nová instalace v TV jde rovnou do překladu z PC –
        // párování PS5, DeepL ani hra se v TV nenastavují, TV jen ukazuje titulky z PC. Čtení přímo v TV zůstává v Nastavení.
        if (!Prefs.wizardDone(this)) {
            if (NetInfo.isTvDevice(this) && !Prefs.readerDevice(this)) { Prefs.setPcMode(this, true); Prefs.setWizardDone(this, true) }
            else wizard = 0
        }
        Updater.cleanUp(this)
        // odpověď systému na aktualizaci (po úspěšné instalaci otevře už novou verzi)
        if (savedInstanceState == null && Updater.onStatus(this, intent)) setIntent(Intent(this, MainActivity::class.java))
        // po aktualizaci dát vědět, že běží nová verze (na Androidu 12+ proběhne bez jediného okna)
        val seen = Prefs.seenVersion(this)
        if (seen != BuildInfo.VERSION_CODE) {
            Prefs.setSeenVersion(this, BuildInfo.VERSION_CODE)
            if (seen in 1 until BuildInfo.VERSION_CODE) toast(t("upd_done") + " " + BuildInfo.VERSION.substringAfter(' '))
        }
        if (savedInstanceState == null) phoneKey(intent)
        // pro automatický test: přeskočit průvodce a rovnou spustit překlad
        val auto = intent?.getBooleanExtra("autostart", false) == true
        if (auto) { Prefs.setWizardDone(this, true); wizard = -1 }
        render()
        if (auto) main.postDelayed({ startDemo() }, 1500)
        // pomocník „Číst pro TV“ druh TV nepoznal: po průvodci volba typu TV (v průvodci ji nabídne krok Televize)
        if (savedInstanceState == null && intent?.getBooleanExtra(EXTRA_PICK_TV, false) == true && wizard < 0)
            main.post { if (!isFinishing) TvChoice.show(this) { render(true) } }
        ps5SelfTest(intent)
        checkForUpdate()
        UpdateNotifier.schedule(this)
        // v režimu „Nastavuji TV“ si jazykový balíček stáhne TV sama – telefon ne
        if (!remote) OfflineTranslator.ensureModel(Prefs.lang(this)) { runOnUiThread { if (section == Section.HOME || wizard >= 0) render(true) } }
        // Android 13+: povolení notifikací (tlačítka Pozastavit / Zastavit v notifikaci)
        if (Build.VERSION.SDK_INT >= 33) {
            try { requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), REQ_NOTIF) } catch (_: Exception) {}
        }
    }

    /** Systémové Zpět: z Historie zpět do Nastavení (jinak jako dřív – aplikace se zavře / jde do pozadí). */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (wizard < 0 && section == Section.HISTORY) { section = Section.SETTINGS; render(); return }
        // od 1.0.02 (majitel: „tlačítko Samsung Zpět občas vyskočí z aplikace místo návratu na Domů“): z jiné sekce na Domů
        if (wizard < 0 && section != Section.HOME) { section = Section.HOME; render(); return }
        @Suppress("DEPRECATION") super.onBackPressed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Updater.onStatus(this, intent)) { render(true); return }
        // pomocník právě spojil telefon s TV ([RemoteClient]): jiná sada nastavení → průvodce / Domů znovu
        if (intent.getBooleanExtra(EXTRA_REMOTE, false)) {
            wizard = if (Prefs.wizardDone(this)) -1 else 0
            section = Section.HOME
            buildShell(); render()
            return
        }
        if (intent.getBooleanExtra(EXTRA_PICK_TV, false) && wizard < 0) { TvChoice.show(this) { render(true) }; return }
        if (phoneKey(intent)) { render(); return }
        ps5SelfTest(intent)
    }

    /**
     * Telefon právě nahrál ReCaption do této TV a poslal klíč ([EXTRA_PHONE_KEY]): jen v prvním průvodci a jen na TV / boxu.
     * Stránka nastavení poběží s tímto klíčem a průvodce ukáže „nastavení telefonem“ – telefon se připojí sám.
     */
    private fun phoneKey(intent: Intent?): Boolean {
        val k = intent?.getStringExtra(EXTRA_PHONE_KEY) ?: return false
        if (Prefs.wizardDone(this) || !RemoteHost.eligible(this) || !cz.recaption.core.PhoneLogin.validKey(k)) return false
        Prefs.setRemoteKey(this, k)
        RemoteHost.stop()
        RemoteHost.start(this)
        // krok Jazyky s rozcestníkem: předvybraný pomocník v telefonu („Telefon je připojený“), ale jde zvolit
        // i Jen ovladač TV / Web (přání majitele 24. 11. 2026 – dřív TV skočila rovnou na nastavení telefonem)
        wizard = W_LANGS; phoneSetup = false; phoneSetupWeb = false; phoneLinked = true
        EventFile.add("WIZARD|telefon po nahrání do TV – připojený, rozcestník s pomocníkem")
        return true
    }

    /**
     * Jen ve zkušebním sestavení (automatická zkouška v emulátoru): vyzkouší hledání konzole,
     * párování a spojení proti adrese, kde žádný PlayStation není – nic nesmí spadnout.
     */
    private fun ps5SelfTest(i: Intent?) {
        if (i?.getBooleanExtra("nowizard", false) == true) { Prefs.setWizardDone(this, true); wizard = -1; section = Section.HOME; render() }
        if (i?.getBooleanExtra("phonelogin", false) == true) {
            Prefs.setWizardDone(this, true); wizard = -1
            Prefs.setPs5Host(this, null)
            section = Section.PS5; pairStep = i?.getIntExtra("pairstep", 1) ?: 1; render(); return
        }
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        // Zkouška rozložení: průvodce TV Samsung (snímky p2_<velikost>_samsung v ci/phone-test.sh).
        if (debuggable && i?.getStringExtra("open") == "samsung") {
            Prefs.setWizardDone(this, true); wizard = -1; section = Section.HOME; render()
            startActivity(Intent(this, SamsungInstallActivity::class.java)); return
        }
        // Zkouška: volba konzole (ps / xbox) a stránka konzole – bez sítě, jen vzhled.
        i?.getStringExtra("console")?.let { c ->
            if (debuggable) {
                Prefs.setConsole(this, Console.of(c))
                Prefs.setWizardDone(this, true); wizard = -1; section = Section.PS5; render(); return
            }
        }
        // Zkouška rozložení (snímky v různých velikostech displeje): rovnou na danou stránku.
        // Bývalá stránka Licence je teď součástí Nastavení.
        i?.getStringExtra("page")?.let { p ->
            val name = if (p == "LICENSE") "SETTINGS" else p
            if (debuggable) Section.values().firstOrNull { it.name == name }?.let {
                Prefs.setWizardDone(this, true); wizard = -1; section = it; render(); return
            }
        }
        // Zkouška rozložení průvodce: rovnou na daný krok (0 = Jazyky, 1 = Zařízení).
        if (debuggable && i?.hasExtra("wizard") == true) {
            wizard = i.getIntExtra("wizard", 0).coerceIn(0, wizardSteps.size - 1); render(); return
        }
        // Zkouška překladu: typické herní repliky projdou stejnou cestou jako titulky ze hry
        // a výsledek jde do záznamu (řádky TR|anglicky|překlad).
        if (i?.getBooleanExtra("trtest", false) == true && debuggable) {
            Prefs.setWizardDone(this, true); wizard = -1; section = Section.HOME; render()
            // „trlangs“ (např. "cs,sk,pl"): tytéž repliky do více jazyků (řádky TRL|kód|anglicky|překlad) –
            // skutečné výstupy offline překladu pro každý jazyk. Uložené nastavení se nemění.
            i.getStringExtra("trlangs")?.let { list ->
                val langs = list.split(',').map { it.trim() }.mapNotNull { c -> cz.recaption.core.Lang.targets.firstOrNull { it.code == c } }.distinct()
                // slovník pro daný jazyk stejně jako Prefs.glossary (ten bere jen uložený jazyk překladu)
                val game = GameProfiles.of(Prefs.gameId(this))
                val glossaries = langs.associateWith { l ->
                    val user = Prefs.userDict(this, l)
                    val sentences = LinkedHashMap(cz.recaption.core.BuiltinPhrases.forLang(l))
                    val terms = LinkedHashMap(game.termsFor(l))
                    for ((k, v) in user) if (cz.recaption.core.Glossary.isSentence(k)) sentences[k] = v else terms[k] = v
                    cz.recaption.core.Glossary(game.names, terms, sentences, game.cast, user.keys.filterNot { cz.recaption.core.Glossary.isSentence(it) })
                }
                scope.launch(Dispatchers.Default) {
                    fun log(m: String) { android.util.Log.i("TP_TEST", "TRL|$m") }
                    log("start ${langs.joinToString(",") { it.code }} source=${OfflineTranslator.source.code}")
                    for (l in langs) {
                        // balíček stejnou cestou jako služba (ensureModel), nejvýš 120 s na jazyk
                        var ready = OfflineTranslator.isReady(l)
                        if (!ready) {
                            val done = kotlinx.coroutines.CompletableDeferred<Boolean>()
                            OfflineTranslator.ensureModel(l) { done.complete(it) }
                            ready = kotlinx.coroutines.withTimeoutOrNull(120_000) { done.await() } ?: false
                        }
                        log("${l.code}|model=$ready")
                        if (ready) for (line in TranslationProbe.LINES) {
                            val out = try { OfflineTranslator.translateSubtitle(l, line, glossaries[l]) } catch (e: Exception) { "!" + e.javaClass.simpleName }
                            log("${l.code}|$line|$out")
                        }
                        log("done ${l.code}")
                    }
                    log("alldone")
                }
                return
            }
            val lang = Prefs.lang(this)
            val glossary = Prefs.glossary(this)
            scope.launch(Dispatchers.Default) {
                fun log(m: String) { android.util.Log.i("TP_TEST", "TR|$m") }
                var ready = OfflineTranslator.isReady(lang)
                for (n in 0 until 90) { if (ready) break; kotlinx.coroutines.delay(1000); ready = OfflineTranslator.isReady(lang) }
                log("start ${lang.code} model=$ready")
                for (line in TranslationProbe.LINES) {
                    val out = try { OfflineTranslator.translateSubtitle(lang, line, glossary) } catch (e: Exception) { "!" + e.javaClass.simpleName }
                    log("$line|$out")
                }
                log("done ${TranslationProbe.LINES.size}")
            }
            return
        }
        // Zkouška přihlášení přes telefon: stránka z televize odpovídá, špatný klíč odmítne a účet
        // poslaný „z telefonu“ posune průvodce na krok s číslem z PS5.
        if (i?.getBooleanExtra("logintest", false) == true && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Prefs.setWizardDone(this, true); wizard = -1
            Prefs.setPs5Host(this, null); Prefs.setPsnAccount(this, "", ""); draftPsn = ""
            section = Section.PS5; pairStep = 1; render()
            val s = loginServer
            Thread({
                fun log(m: String) { android.util.Log.i("TP_TEST", "LOGIN|$m") }
                if (s == null || !s.running) { log("test no page"); return@Thread }
                try {
                    val page = LoginClient.request("127.0.0.1", s.port, "GET", PhoneLogin.path(s.key))
                    log("test page ${page.first} form=${page.second.contains("name=\"url\"")}")
                    log("test wrong key " + LoginClient.request("127.0.0.1", s.port, "GET", PhoneLogin.path("spatnyklic")).first)
                    val acc = PhoneLogin.Account(Ps5.accountIdToBase64(ByteArray(8) { (it + 1).toByte() }), "Zkouška")
                    log("test app " + LoginClient.send(PhoneLogin.Target("127.0.0.1", s.port, s.key), acc))
                } catch (e: Throwable) { log("test error $e") }
            }, "tp-logintest").start()
            return
        }
        // Zkouška aktualizace. „updtest“: stáhne běžnou vydanou verzi a předá ji systému.
        // „updself“: aplikace nainstaluje sama sebe (stejná verze i podpis) – projde celá instalace.
        val updSelf = i?.getBooleanExtra("updself", false) == true
        if ((updSelf || i?.getBooleanExtra("updtest", false) == true) && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Prefs.setWizardDone(this, true); wizard = -1; section = Section.HOME
            val o = UpdateCheck.Offer(UpdateCheck.Remote(BuildInfo.VERSION_CODE + 1, "zkouška"), UpdateCheck.Channel.STABLE)
            update = o
            startUpdate(o, force = true, source = if (updSelf) packageCodePath else null)
            return
        }
        val ip = i?.getStringExtra("ps5test") ?: return
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        Prefs.setWizardDone(this, true); wizard = -1
        Thread({
            Ps5Pairing.discover(2000)
            Ps5Pairing.pair(ip, ByteArray(8) { (it + 1).toByte() }, 12345678) {
                val key = "1a2b3c4d".toByteArray() + ByteArray(8)
                Prefs.setPs5Host(this, Ps5Host(ip, "Zkouška", key, ByteArray(16) { 7 }, ByteArray(6)))
                runOnUiThread { section = Section.HOME; startFlow() }
            }
        }, "tp-selftest").start()
    }

    override fun onResume() {
        super.onResume()
        UiState.shown()
        // anonymní počítání: otevření aplikace (nejvýš 1× za 6 h, i bez překladu – UsagePing.opened)
        try { UsagePing.opened(this) } catch (_: Throwable) {}
        if (wizard == W_PERM && permAsked && SubtitleOverlay.canShow(this)) { permAsked = false; wizardMove(1) }
        // návrat z dotazu systému na instalaci aktualizace bez odpovědi = zrušeno, Aktualizovat je hned zpět
        Updater.resumed { if (!isFinishing) render(true) }
        render(keepFocus = true)
        main.removeCallbacks(ticker)
        main.postDelayed(ticker, 1000)
        // po návratu do aplikace se zeptá i na novou verzi (dřív jen při spuštění – nová verze se pak
        // ukázala až po zavření a otevření aplikace); nejvýš jednou za 5 minut
        if (android.os.SystemClock.elapsedRealtime() - lastUpdateCheck > 5 * 60_000L) checkForUpdate()
        // seznam her: jen se zapnutou volbou „Seznam her automaticky“, nejvýš jednou za 6 h
        checkGameList(manual = false)
        // aplikace pro TV LG/Samsung novější než v APK (vydaná samostatně, od 1.0.3): nejvýš 1× za 6 h, Domů pak nabídne „Aktualizovat v TV“
        TvPackages.refresh(this, Prefs.tvType(this), page = false) { runOnUiThread { if (!isFinishing && section == Section.HOME && wizard < 0) render(keepFocus = true) } }
        // TV LG: prodloužit vývojářský režim (jen se zapnutou volbou, nejvýš jednou denně – LgDevMode.renew)
        LgDevMode.renew(this) { runOnUiThread { if (!isFinishing && wizard < 0 && currentFocus !is EditText) render(true) } }
        // nastavení z telefonu (jen Android TV / box): stránka běží, dokud žije aplikace; změna z telefonu překreslí
        // změna z telefonu (účet, spárování) → i rozepsané údaje spárování podle uloženého, ať je krok neukáže staré
        if (RemoteHost.start(this)) RemoteHost.onChanged = { if (!isFinishing) {
            draftPsn = Prefs.psnId(this); Prefs.ps5Ip(this).takeIf { it.isNotEmpty() }?.let { draftIp = it }
            // telefon nastavení dokončil → průvodce na TV jde sám dál (Povolení, když pruh chybí, jinak Hotovo)
            phoneSetupAdvance()
            buildShell(); render(keepFocus = true)
        } }
        // telefon nastavuje TV: změna stavu spojení (nedostupná / připojeno) jen přepíše pruh nahoře
        RemoteClient.onState = { if (!isFinishing) updateRemoteBar() }
        // TV: telefon přešel na jiný krok průvodce → TV taky
        RemoteHost.onStep = { n -> if (!isFinishing && !remote) goToSyncedStep(n) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // krok DeepL v telefonu bez klíče: po návratu z prohlížeče je klíč obvykle ve schránce → vyplnit a ověřit samo
        // (schránka jde číst až s fokusem okna; jinde v aplikaci se nečte)
        if (hasFocus && wizard == W_TRANSLATE && !Prefs.isTv(this)) deeplUi?.fromClipboard()
    }

    override fun onPause() {
        RemoteClient.onState = null
        TvIdle.stopLater()   // spojení s TV ještě chvíli drží – po návratu do aplikace je TV hned připojená
        UiState.previewWanted = false
        UiState.hidden()
        main.removeCallbacks(ticker)
        Prefs.setStats(this, TranslatorService.statsFor(this))
        super.onPause()
    }

    override fun onDestroy() {
        ReportSender.closeFor(this)   // okno odesílání nesmí zůstat viset po zaniklé obrazovce (otočení telefonu)
        if (::xbox.isInitialized) xbox.cancel()
        loginServer?.stop(); loginServer = null
        deeplUi?.stopPage()
        RemoteHost.onChanged = null
        RemoteHost.onStep = null
        demo?.destroy(); demo = null
        scope.cancel()
        super.onDestroy()
    }

    private fun t(key: String) = tx(key)

    // ======================= kostra: menu + obsah =======================

    /**
     * Úzká obrazovka (telefon na výšku). Aplikace je kreslená pro televizi – menu vlevo, obsah vpravo.
     * Na úzké obrazovce by na obsah zbyl proužek, proto je tam menu nahoře jako řada a obsah pod ním
     * přes celou šířku. Na televizi se nic nemění.
     */
    private val narrow: Boolean get() = resources.configuration.screenWidthDp in 1 until 600

    /** Řada tlačítek; na úzké obrazovce jsou tlačítka pod sebou, aby se vešla. */
    private fun row(): LinearLayout = if (narrow) ui.vbox() else ui.hbox()

    // Titulky PS5: menu je všude nahoře v řadě (jako menu PS5), na televizi i v telefonu
    private fun menuLp() =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = ui.dp(if (narrow) 6 else 10) }

    private fun buildNarrowShell() {
        // clipChildren: posuvná část se nesmí kreslit přes lištu (ui.vbox() má ořez vypnutý kvůli zvětšení vybraného prvku)
        val root = ui.vbox().apply { setBackgroundColor(Theme.BG); clipChildren = true }
        val top = ui.vbox().apply {
            setBackgroundColor(Theme.SIDEBAR)
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(8))
        }
        val logoRow = ui.hbox()
        logoRow.addView(ImageView(this).apply { setImageResource(R.drawable.ic_logo) }, LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)))
        logoRow.addView(ui.text("Titulky PS5", 19f, Theme.TEXT, bold = true).apply { setPadding(ui.dp(10), 0, ui.dp(10), 0) })
        logoRow.addView(ui.text(Prefs.lang(this).label, 13f, Theme.ACCENT, bold = true))
        logoRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        gear = ui.hbox()
        logoRow.addView(gear)
        top.addView(logoRow)
        menu = ui.hbox()
        menuScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(menu) }
        top.addView(menuScroll, ui.lp(top = 8))
        root.addView(top, ui.lp())

        pinned = ui.vbox().apply { setBackgroundColor(Theme.BG); setPadding(ui.dp(16), ui.dp(18), ui.dp(16), ui.dp(10)); visibility = View.GONE }
        root.addView(pinned, ui.lp())
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = ui.vbox().apply { setPadding(ui.dp(16), ui.dp(18), ui.dp(16), ui.dp(32)) }
        contentTop = ui.dp(18)
        scroll.addView(content)
        pageScroll = scroll
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    /** Zpět k pomocníkovi pro přihlášení (telefon, na kterém byla omylem nebo dočasně otevřená celá aplikace). */
    private fun backToPhoneHelper() {
        if (TranslatorService.running) stopService(Intent(this, TranslatorService::class.java))
        // od 0.97.55: aplikace zůstává pod pomocníkem a režim celé aplikace se nemění – Zpět (tlačítko v pomocníkovi
        // i telefonu) vede zpátky; dřív se aplikace zavřela, telefon přepnul na „jen pomocník“ a cesta zpět nebyla
        startActivity(Intent(this, PhoneHelperActivity::class.java).putExtra(PhoneHelperActivity.EXTRA_FROM_APP, true))
    }

    private fun buildShell() {
        sideScroll = null
        if (narrow) { buildNarrowShell(); return }
        val root = ui.hbox().apply {
            setBackgroundColor(Theme.BG)
            gravity = Gravity.TOP
        }

        // Titulky PS5: lišta nahoře jako menu PS5 – logo, záložky v řadě, ⚙ Nastavení vpravo; obsah přes celou šířku
        root.orientation = LinearLayout.VERTICAL
        val top = ui.hbox().apply {
            setBackgroundColor(Theme.SIDEBAR)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(40), ui.dp(18), ui.dp(40), ui.dp(14))
        }
        top.addView(ImageView(this).apply { setImageResource(R.drawable.ic_logo) }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        val names = ui.vbox().apply { setPadding(ui.dp(12), 0, ui.dp(28), 0) }
        names.addView(ui.text("Titulky PS5", 20f, Theme.TEXT, bold = true))
        names.addView(ui.text(Prefs.lang(this).label, 13f, Theme.ACCENT, bold = true))
        top.addView(names)
        menu = ui.hbox().apply { clipChildren = false; clipToPadding = false; setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6)) }
        menuScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false
            isFocusable = false   // v průvodci nesmí vzít výběr ovladače (kroky nejsou k výběru)
            addView(menu)
        }
        top.addView(menuScroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        gear = ui.hbox()
        top.addView(gear, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = ui.dp(16) })
        root.addView(top, ui.lp())

        val column = ui.vbox().apply { clipChildren = true }
        pinned = ui.vbox().apply { setBackgroundColor(Theme.BG); setPadding(ui.dp(48), ui.dp(34), ui.dp(64), ui.dp(10)); visibility = View.GONE }
        column.addView(pinned, ui.lp())
        val scroll = ScrollView(this).apply { isFillViewport = true; clipChildren = false; clipToPadding = false }
        content = ui.vbox().apply { setPadding(ui.dp(48), ui.dp(34), ui.dp(64), ui.dp(48)) }
        contentTop = ui.dp(34)
        scroll.addView(content)
        pageScroll = scroll
        column.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        // Titulky PS5: tělo = [menu Nastavení vlevo] [obsah] [náhled titulků vpravo]; okraje jen u Nastavení a Titulků
        val body = ui.hbox().apply { gravity = Gravity.TOP }
        sideNav = ui.vbox().apply {
            setBackgroundColor(Theme.SIDEBAR); visibility = View.GONE
            setPadding(ui.dp(28), ui.dp(30), ui.dp(16), ui.dp(20))
            clipChildren = false; clipToPadding = false
        }
        body.addView(sideNav, LinearLayout.LayoutParams(ui.dp(250), LinearLayout.LayoutParams.MATCH_PARENT))
        body.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        previewPanel = ui.vbox().apply { visibility = View.GONE; setPadding(0, ui.dp(34), ui.dp(40), ui.dp(20)) }
        body.addView(previewPanel, LinearLayout.LayoutParams(ui.dp(300), LinearLayout.LayoutParams.MATCH_PARENT))
        root.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    /** Široká obrazovka, Nastavení nebo Titulky: menu vlevo jako v Nastavení PS5 (Obecné · Vzhled titulků · Čtení a překlad · Experimentální). */
    private fun sideNavOn() = !narrow && wizard < 0 && (section == Section.SETTINGS || section == Section.LOOK)

    private fun renderSideNav() {
        val nav = sideNav ?: return
        val prev = previewPanel
        nav.removeAllViews(); prev?.removeAllViews()
        val on = sideNavOn()
        nav.visibility = if (on) View.VISIBLE else View.GONE
        prev?.visibility = if (on && section == Section.LOOK) View.VISIBLE else View.GONE
        if (!on) return
        nav.addView(ui.text(t("m_settings"), 24f, Theme.TEXT, bold = true), ui.lp().apply { bottomMargin = ui.dp(16) })
        fun item(icon: String, label: String, active: Boolean, tag: String, go: () -> Unit) {
            val v = ui.hbox().apply {
                isFocusable = true; isClickable = true
                gravity = Gravity.CENTER_VERTICAL
                background = ui.focusBg(if (active) Theme.ACCENT else Theme.SIDEBAR, radius = 12)
                setPadding(ui.dp(14), ui.dp(12), ui.dp(12), ui.dp(12))
                addView(ui.text(icon, 17f, if (active) Theme.TEXT else Theme.MUTED, bold = true), LinearLayout.LayoutParams(ui.dp(30), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(ui.text(label, 16f, if (active) Theme.TEXT else Theme.MUTED, bold = active).apply { maxLines = 1 })
                setOnClickListener { go() }
            }
            nav.addView(tagged(v, tag), ui.lp().apply { bottomMargin = ui.dp(6) })
        }
        val set = section == Section.SETTINGS
        fun tab(tb: SettingsTab) { section = Section.SETTINGS; settingsTab = tb; render(keepFocus = true); pageScroll?.post { pageScroll?.scrollTo(0, 0) } }
        item("◍", t(SettingsTab.GENERAL.key), set && settingsTab == SettingsTab.GENERAL, "stab_GENERAL") { tab(SettingsTab.GENERAL) }
        if (!Prefs.pcMode(this)) item("Aa", ct("m_look"), section == Section.LOOK, "stab_LOOK") { section = Section.LOOK; render(keepFocus = true); pageScroll?.post { pageScroll?.scrollTo(0, 0) } }
        // čtení a překlad dělá v režimu z PC program na PC – v TV zůstane jen Obecné
        if (!Prefs.pcMode(this)) {
            item("≡", t(SettingsTab.READ.key), set && settingsTab == SettingsTab.READ, "stab_READ") { tab(SettingsTab.READ) }
            item("⚗", t(SettingsTab.EXPERIMENTAL.key), set && settingsTab == SettingsTab.EXPERIMENTAL, "stab_EXPERIMENTAL") { tab(SettingsTab.EXPERIMENTAL) }
        } else settingsTab = SettingsTab.GENERAL
        if (prev != null && section == Section.LOOK) lookPreview(prev)
    }

    /** Náhled vzhledu titulků vedle voleb (velikost, barva, tučně, průhlednost pruhu) – mění se hned s volbou. */
    private fun lookPreview(panel: LinearLayout) {
        panel.addView(ui.text("NÁHLED", 13f, Theme.MUTED, bold = true), ui.lp().apply { bottomMargin = ui.dp(10) })
        val scene = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(0x3A, 0x4A, 0x5E), Color.rgb(0x1C, 0x24, 0x30), Color.rgb(0x0F, 0x14, 0x1B))).apply { cornerRadius = ui.dp(16).toFloat() }
            clipToOutline = true
        }
        // úkol vlevo nahoře (žlutý štítek jako v pruhu úkolu)
        scene.addView(ui.text("ÚKOL  Dojet do obchodu", 11f, Theme.WARN, bold = true).apply {
            background = GradientDrawable().apply { setColor(Color.argb(210, 10, 16, 30)); cornerRadius = ui.dp(8).toFloat() }
            setPadding(ui.dp(9), ui.dp(6), ui.dp(9), ui.dp(6))
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { setMargins(ui.dp(12), ui.dp(12), ui.dp(12), 0) })
        // titulek podle voleb (písmo zmenšené v poměru náhledu k obrazovce)
        val size = Prefs.textSize(this) * 0.62f
        val color = Prefs.TEXT_COLORS[Prefs.textColorIndex(this)]
        val alpha = (Prefs.opacity(this).coerceIn(0, 100) * 255 / 100)
        val line = android.text.SpannableStringBuilder().apply {
            append("Lucia: ")
            setSpan(android.text.style.ForegroundColorSpan(Color.rgb(0x7F, 0xB2, 0xFF)), 0, length, 0)
            append("Byl to jen instinkt. Špatný instinkt.")
        }
        scene.addView(ui.text(line, size, color, bold = Prefs.boldText(this)).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(Color.argb(alpha, 6, 10, 22)); cornerRadius = ui.dp(10).toFloat() }
            setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { setMargins(ui.dp(12), 0, ui.dp(12), ui.dp(14)) })
        panel.addView(scene, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(200)))
        panel.addView(ui.text("Dialogy dole, úkol vlevo nahoře, mobil vedle telefonu.", 13f, Theme.MUTED), ui.lp(top = 10))
    }

    /** Pod kterou položkou menu právě zobrazená stránka patří. */
    private fun menuSection(): Section = if (section == Section.HISTORY) Section.SETTINGS else section

    private fun renderMenu() {
        menu.removeAllViews()
        gear.removeAllViews()
        for (s in Section.values()) {
            if (s == Section.HISTORY) continue
            // překlad z PC: PS5 páruje a čte PC, v TV stránka konzole není potřeba
            // vzhled titulků se v režimu z PC nastavuje na PC (přání majitele 25. 11. 2026: „vše jen z PC“)
            if ((s == Section.PS5 || s == Section.LOOK) && Prefs.pcMode(this) && !remote) continue
            val active = s == menuSection()
            if (s == Section.SETTINGS && narrow) {
                // telefon: jen ozubené kolo v rohu lišty, vidět pořád
                gear.addView(ui.text("⚙", 26f, if (active) Theme.ACCENT else Theme.TEXT, bold = true).apply {
                    isFocusable = true
                    isClickable = true
                    gravity = Gravity.CENTER
                    background = ui.focusBg(if (active) Theme.CARD else Theme.SIDEBAR, radius = 10)
                    contentDescription = t(s.menuKey)
                    setPadding(ui.dp(12), ui.dp(2), ui.dp(12), ui.dp(4))
                    setOnClickListener { section = s; render(keepFocus = true) }
                    tag = "menu_${s.name}"
                })
                continue
            }
            // záložka jako v menu PS5: text, pod vybranou modrá čárka
            val item = ui.vbox().apply {
                isFocusable = true
                isClickable = true
                gravity = Gravity.CENTER_HORIZONTAL
                background = ui.focusBg(if (active) Theme.CARD else Theme.SIDEBAR, radius = 10)
                ui.pop(this)
                setPadding(ui.dp(18), ui.dp(9), ui.dp(18), ui.dp(7))
                val label = if (s == Section.SETTINGS) "⚙  " + t(s.menuKey) else ct(s.menuKey)
                addView(ui.text(label, 17f, if (active) Theme.TEXT else Theme.MUTED, bold = active))
                addView(View(this@MainActivity).apply {
                    background = GradientDrawable().apply {
                        setColor(if (active) Theme.ACCENT else android.graphics.Color.TRANSPARENT)
                        cornerRadius = ui.dp(2).toFloat()
                    }
                }, LinearLayout.LayoutParams(ui.dp(28), ui.dp(3)).apply { topMargin = ui.dp(5) })
                setOnClickListener { section = s; render(keepFocus = true) }
                tag = "menu_${s.name}"
            }
            if (s == Section.SETTINGS) gear.addView(item) else menu.addView(item, menuLp())
            if (active) scrollTabIntoView(menuScroll, item)
        }
        if (!narrow) linkGear()
    }

    /** Televize: šipka dolů z poslední položky menu vede na ⚙ Nastavení dole v levém sloupci a nahoru zpět.
     *  Bez toho systém vybere bližší řádek stránky vpravo (na Nápovědě kapitolu) a ⚙ ovladačem nešlo vybrat. */
    private fun linkGear() {
        val last = menu.getChildAt(menu.childCount - 1) ?: return
        val g = gear.getChildAt(0) ?: return
        if (last.id == View.NO_ID) last.id = View.generateViewId()
        if (g.id == View.NO_ID) g.id = View.generateViewId()
        last.nextFocusRightId = g.id
        g.nextFocusLeftId = last.id
    }

    /** Překreslí menu i obsah (po každé změně – je to rychlé a nic se nerozsynchronizuje). */
    private fun render(keepFocus: Boolean = false) {
        val focusedTag = if (keepFocus) currentFocus?.tag else null
        if (!skipDraftSave) saveDrafts()
        syncLoginPage()
        // stránka pro vložení klíče DeepL z telefonu běží jen, dokud je vidět (Titulky s otevřenou skupinou Kvalita
        // překladu, nebo krok průvodce Lepší překlad)
        if (if (wizard >= 0) wizard != W_TRANSLATE else section != Section.LOOK || lookOpen != LOOK_DEEPL) deeplUi?.stopPage()
        lastRunning = TranslatorService.running
        lastPaused = TranslatorService.paused
        lastLink = TranslatorService.linkState
        lastFrames = TranslatorService.framesDecoded
        lastStage = TranslatorService.linkStatus().stage
        lastTv = TranslatorService.tvConnected
        lastTvSeen = TvSender.lastSeen?.at ?: 0L
        lastGuess = Prefs.gameGuess(this)
        lastProbe = Ps5Probe.state
        // aplikace v televizi LG/Samsung se může připojit i před Spustit (skutečný stav na Domů)
        TvIdle.ensure(this)
        // barevný náhled hry jen pro stránku Konzole (jinde by se zbytečně počítal každé 1,5 s)
        UiState.previewWanted = wizard < 0 && section == Section.PS5 && "preview" in openGroups
        // pevná hlavička jen v Nastavení (nadpis a záložky), jinde schovaná a obsah s běžným odsazením
        pinned.removeAllViews()
        renderSideNav()
        // s menu vlevo (široká obrazovka) záložky Nastavení nahoře nejsou
        val pin = wizard < 0 && section == Section.SETTINGS && !sideNavOn()
        pinned.visibility = if (pin) View.VISIBLE else View.GONE
        content.setPadding(content.paddingLeft, if (pin) ui.dp(8) else contentTop, content.paddingRight, content.paddingBottom)
        if (wizard >= 0) { renderWizard(focusedTag); return }
        renderMenu()
        content.removeAllViews(); closeNests()
        remoteBar()
        // nadpis stránky jen u Historie – ostatní stránky mají název v menu (přání majitele, od 0.97.6)
        if (section == Section.HISTORY) content.addView(ui.text(ct(section.key), 30f, Theme.TEXT, bold = true))
        when (section) {
            Section.HOME -> home()
            Section.PS5 -> ps5()
            // Titulky: nejdřív vzhled, pod ním kde se čtou
            Section.LOOK -> subtitlesPage()
            // Nastavení (⚙): záložky Obecné / Čtení a překlad / Ladění a hlášení / Experimentální
            Section.SETTINGS -> settings()
            Section.HISTORY -> history()
            Section.HELP -> help()
        }
        val target = focusedTag?.let { window.decorView.findViewWithTag<View>(it) }
            ?: window.decorView.findViewWithTag<View>("menu_${menuSection().name}")
        ui.restoringFocus()   // stejné tlačítko znovu vybrané – bez „pulzu“
        target?.requestFocus()
    }

    // ======================= PRŮVODCE PRVNÍM SPUŠTĚNÍM =======================

    /**
     * Názvy kroků průvodce: jazyky (aplikace a titulků), zařízení, konzole, televize, jazyk překladu,
     * lepší překlad, povolení pruhu, spárování konzole, hotovo (indexy W_…). Jednorázové volby jdou
     * později změnit v Nastavení.
     */
    private val wizardSteps = listOf("wz_langs", "wz_dev", "wz_tv", "c_console", "wz_s2", "wz_tr_step", "wz_s1", "wz_s3", "wz_s4")

    /**
     * Hlavní kroky průvodce (od 0.97.98, přání majitele: méně kroků nahoře, podkroky uvnitř): název a kroky [wizardSteps],
     * které do něj patří. Hotovo (poslední krok) do počtu nepatří. Kroky samotné a jejich pořadí se nemění (zkouška
     * průvodcem prochází naslepo), mění se jen záhlaví („Krok 2 z 6 · Zařízení a TV · 2/2“) a levý seznam.
     */
    private val wizardGroups: List<Pair<String, List<Int>>> by lazy { listOf(
        "wz_langs" to listOf(W_LANGS), "wz_g_dev" to listOf(W_DEVICE, W_TV), "c_console" to listOf(W_CONSOLE),
        "wz_g_tr" to listOf(W_LANG, W_TRANSLATE), "wz_s1" to listOf(W_PERM), "wz_g_conn" to listOf(W_PAIR),
    ) }

    /** Kroky hlavního kroku [g], které se tu ukážou: bez přeskočených ([skipStep]), aktuální vždy. */
    private fun groupSteps(g: Int): List<Int> = wizardGroups[g].second.filter { it == wizard || it == W_LANGS || !skipStep(it) }

    /** Hlavní kroky (indexy do [wizardGroups]), které tu mají co ukázat – podle nich „Krok X z N“ a levý seznam. */
    private fun shownGroups(): List<Int> = wizardGroups.indices.filter { groupSteps(it).isNotEmpty() }

    /** Hlavní krok (0..5) pro krok průvodce; Hotovo = počet hlavních kroků. */
    private fun wizardGroup(step: Int): Int = wizardGroups.indexOfFirst { step in it.second }.let { if (it < 0) wizardGroups.size else it }

    /** Volba v průvodci: řádek s názvem a popisem; vybraná je zlatá a s fajfkou. */
    private fun option(title: String, desc: String?, selected: Boolean, tag: String, onPick: () -> Unit) {
        val row = ui.vbox().apply {
            isFocusable = true
            isClickable = true
            background = ui.focusBg(Theme.CARD)
            ui.pop(this)
            setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12))
            addView(ui.text((if (selected) "✓  " else "○  ") + title, 18f, if (selected) Theme.ACCENT else Theme.TEXT, bold = selected))
            desc?.let { addView(ui.text(it, 14f, Theme.MUTED).apply { setPadding(ui.dp(28), 0, 0, 0) }, ui.lp(top = 4)) }
            setOnClickListener { onPick() }
        }
        add(tagged(row, tag), 10)
    }

    /**
     * Na čem ReCaption běží. Telefon/tablet jen čte a posílá do televize LG / Samsung, proto se mu
     * typ Android TV přepne na LG (v kroku Televize jde změnit na Samsung).
     */
    private fun setReader(reader: Boolean) {
        Prefs.setReaderDevice(this, reader)
        if (reader && !Prefs.tvSend(this)) TvChoice.set(this, TvType.LG)
    }

    /**
     * Podkrok průvodce pro klip (`krok-<krok>-<podkrok>…`, [cz.recaption.core.VideoSteps]): DeepL klíč, obrazovky spárování
     * PS5 (index [pairStep] → pořadí „pg_sub“ 1–4) a Hotovo; jinde null = klip celého kroku.
     */
    private fun videoSubstep(): String? = when (wizard) {
        W_TRANSLATE -> "klic"
        W_PAIR -> if (Prefs.console(this) == Console.XBOX || Prefs.consoleReady(this) && !pairAnother) null
            else when (pairStep.coerceIn(0, 5)) { 0 -> "ucet"; 1, 2, 3 -> "prihlaseni"; 4 -> "rp"; else -> "cislo" }
        in 0 until wizardSteps.size - 1 -> null
        else -> "hrat"
    }

    private fun renderWizard(focusedTag: Any?) {
        if (wizard > Prefs.wizardMaxStep(this)) Prefs.setWizardMaxStep(this, wizard)
        // krok průvodce v telefonu i v TV (přání majitele 24. 11. 2026): telefon v režimu „Nastavuji TV“ pošle svůj krok,
        // TV ho zveřejní pro telefon – kroky se posouvají na obou zařízeních, ovládat jde kterékoli
        if (remote) RemoteClient.stepShown(wizard) else RemoteHost.tvStep = wizard
        menu.removeAllViews()
        gear.removeAllViews()   // ⚙ Nastavení až po průvodci (v levém sloupci by bralo místo krokům)
        var activeStep: View? = null
        // jen hlavní kroky, které tu mají co ukázat (od 1.00, přání majitele – jako webový průvodce); Hotovo = poslední
        val shown = shownGroups()
        val curGroup = shown.indexOf(wizardGroup(wizard)).let { if (it < 0) shown.size else it }
        (shown + wizardGroups.size).forEachIndexed { i, grp ->
            val active = i == curGroup
            // levý sloupec: 9 kroků se musí vejít i na televizi 1080p – nižší řádky, každý krok na jeden řádek
            val pad = if (narrow) 12 else 7
            val item = ui.hbox().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(14), ui.dp(pad), ui.dp(14), ui.dp(pad))
                addView(ui.text("${i + 1}", if (narrow) 15f else 14f, if (i <= curGroup) Theme.ACCENT else Theme.MUTED, bold = true),
                    LinearLayout.LayoutParams(ui.dp(24), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(ui.text(groupName(grp), if (narrow) 16f else 15f, if (active) Theme.ACCENT else if (i < curGroup) Theme.TEXT else Theme.MUTED, bold = active).apply {
                    maxLines = 1
                })
            }
            if (active) activeStep = item
            menu.addView(item, menuLp())
        }
        // nízká obrazovka: aktuální krok vidět (seznam se posune)
        sideScroll?.let { sc -> activeStep?.let { a -> sc.post { sc.scrollTo(0, maxOf(0, a.top - (sc.height - a.height) / 2)) } } }
        scrollTabIntoView(menuScroll, activeStep, smooth = false)
        content.removeAllViews(); closeNests()
        remoteBar()
        content.addView(ui.text(t("wz_title"), 15f, Theme.ACCENT, bold = true))
        // kolik kroků zbývá (v telefonu není vidět levý seznam kroků)
        // „Krok 2 z 6 · Zařízení a TV · 2/2“ – hlavní kroky s podkroky (od 0.97.98); Hotovo = plný pruh
        // přeskočené kroky (Zařízení, Televize na Android TV…) se nepočítají – číslo sedí s levým seznamem
        val g = wizardGroup(wizard)
        val total = shown.size
        val label = if (g >= wizardGroups.size) groupName(g) else {
            val members = groupSteps(g)
            steps(curGroup + 1, total) + " · " + groupName(g) + if (members.size > 1) " · ${members.indexOf(wizard) + 1}/${members.size}" else ""
        }
        content.addView(ui.steps(minOf(curGroup + 1, total), total, label), ui.lp(top = 8))
        // nadpis obrazovky ze šablony průvodce (core/Pruvodce.kt – stejně jako web); obrazovky, které šablona nemá, vlastní
        val nadpis = if (phoneSetup && wizard == W_LANGS) cz.recaption.core.Pruvodce.volba("na", if (phoneSetupWeb) "jine" else "telefon", pStav(), uiCode())?.first
            else wizardScreen(wizard)?.let { cz.recaption.core.Pruvodce.appNadpis(it, pStav(), uiCode()) }
        content.addView(ui.text(nadpis ?: ct(wizardSteps[wizard]), 30f, Theme.TEXT, bold = true), ui.lp(top = 10))
        // krátké video k podkroku, jinak k hlavnímu kroku podle zvolené TV a konzole (VideoSteps; jen když klip na stránkách je;
        // Hotovo = krok 6 „hotovo“, bez klipu v seznamu řádek není)
        if (!(phoneSetup && wizard == W_LANGS) && g <= wizardGroups.size) {
            VideoStepList.load(this) { runOnUiThread { if (!isFinishing && wizard >= 0) render(keepFocus = true) } }
            val tvKey = when (Prefs.tvType(this)) { TvType.LG -> "lg"; TvType.SAMSUNG -> "samsung"; TvType.ANDROID -> "android" }
            val conKey = if (Prefs.console(this) == Console.XBOX) "xbox" else "ps"
            cz.recaption.core.VideoSteps.pick(g, tvKey, conKey, VideoStepList.clips, videoSubstep())?.let { id ->
                add(tagged(ui.settingRow("▶  " + t("vs_row"), VideoStepList.clips[id] ?: "") { VideoGuideActivity.open(this, id) }, "wvideo"), 10)
            }
        }
        // rozcestník: nastavení telefonem – jedna stránka v telefonu, TV jen ukazuje, co je hotové
        if (phoneSetup && wizard == W_LANGS) { phoneSetupScreen(focusedTag); return }

        val lang = Prefs.lang(this)
        var nextLabel = t("wz_next")
        when (wizard) {
            // Jazyky: jazyk aplikace (texty se hned přepnou) a z čeho se překládá (jazyk titulků ve hře)
            W_LANGS -> {
                pruvodceRadky("langs")
                val uiLang = Prefs.uiLang(this)
                add(tagged(ui.settingRow(t("l_ui_lang"), uiLang.label) {
                    val all = cz.recaption.core.Lang.uiLangs
                    choose(t("l_ui_lang"), all.map { it.label }, all.indexOf(uiLang)) { i ->
                        Prefs.setUiLang(this, all[i])
                        // jazyk překladu, který hráč ještě nezvolil, jde s jazykem aplikace → i dvojice jazyků
                        OfflineTranslator.source = Prefs.sourceLang(this)
                        applyToService(); buildShell(); render()
                        window.decorView.findViewWithTag<View>("wuilang")?.requestFocus()
                    }
                }, "wuilang"), 18)
                sourceRow("wsrc", 8)
                // všechny tři jazyky na jednom místě (od 0.97.65 – majitel: krok „Jazyky“ a pozdější „Jazyk překladu“ se kryly);
                // krok 5 je teď jen offline překlad (stažení balíčku pro zvolenou dvojici)
                langRow("wlang", 8)
                // rozcestník (od 0.97.63, přání majitele): účet, PS5 a DeepL z telefonu / počítače na jedné stránce (Android TV / box
                // s PlayStationem); výchozí je dál průvodce na TV – OK na Pokračovat jde jako dřív (zkouška kliká naslepo).
                // Bez nadpisu a popisu – volby jsou celé věty, ať se krok vejde na obrazovku TV i s tlačítky.
                // od šablony průvodce (24. 11. 2026) stejné tři volby jako web („Čím nastavovat?“) a i pro Xbox
                if (RemoteHost.eligible(this)) {
                    val st = pStav()
                    fun volba(v: String, tag: String, selected: Boolean, onPick: () -> Unit) =
                        cz.recaption.core.Pruvodce.volba("na", v, st, uiCode())?.let { (n, d) -> option(n, d, selected, tag, onPick) }
                    volba("telefon", "wphone", phoneLinked) { phoneSetup = true; phoneSetupWeb = false; render() }
                    if (phoneLinked) { add(ui.text(t("wz_ph_linked"), 16f, Theme.OK, bold = true), 4); addWaiting(top = 4) }
                    volba("jine", "wweb", false) { phoneLinked = false; phoneSetup = true; phoneSetupWeb = true; render() }
                    // Jen ovladač TV: telefon zůstane připojený, kroky se posouvají v TV i v telefonu (ovládat jde obojí)
                    volba("ovladac", "wtvonly", !phoneLinked) { window.decorView.findViewWithTag<View>("wnext")?.requestFocus() }
                }
            }
            W_DEVICE -> {
                add(ui.text(t("wz_dev_q"), 20f, Theme.TEXT, bold = true), 18)
                val reader = Prefs.readerDevice(this)
                option(t("dev_tv"), t("dev_tv_d"), !reader, "wdevtv") { setReader(false); render(true) }
                option(t("dev_phone"), t("dev_phone_d"), reader, "wdevphone") { setReader(true); render(true) }
            }
            W_CONSOLE -> {
                add(ui.text(t("wz_con_q"), 20f, Theme.TEXT, bold = true), 18)
                val cur = Prefs.console(this)
                val ps = cz.recaption.core.Pruvodce.volba("ko", "ps5", pStav(), uiCode()) ?: (t("c_ps") to t("c_ps_d"))
                val xb = cz.recaption.core.Pruvodce.volba("ko", "xbox", pStav(), uiCode()) ?: (t("c_xbox") to t("c_xbox_d"))
                option(ps.first, ps.second, cur == Console.PLAYSTATION, "wconps") { setConsole(Console.PLAYSTATION); render(true) }
                option(xb.first, xb.second, cur == Console.XBOX, "wconxbox") { setConsole(Console.XBOX); render(true) }
                // Xbox: přihlášení účtem Microsoft je v tomhle kroku (krok Připojení má jen PlayStation – šablona průvodce)
                if (cur == Console.XBOX && !remote) ps5Form()
                // příprava 🔵 účtu (od 1.00, přání majitele – jako webový průvodce): na TV čtvereček, v telefonu tlačítko;
                // celý postup (Remote Play, spárování) je dál v kroku Připojení konzole
                if (cur == Console.PLAYSTATION) add(tagged(helpLink(cz.recaption.core.HelpLinks.PS5_PREP, t("help_ps5"), qr = 150), "wconhelp"), 14)
            }
            W_TV -> {
                add(ui.text(t("wz_tv_q"), 20f, Theme.TEXT, bold = true), 18)
                val type = Prefs.tvType(this)
                // telefon pruh přes hru neukáže – jen posílá do televize LG / Samsung
                // v režimu „Nastavuji TV“ jde o Android TV – jiný typ nenabízet
                for (tt in TvType.values()) if ((tt != TvType.ANDROID || !Prefs.readerDevice(this)) && (!remote || tt == TvType.ANDROID)) {
                    val nazev = cz.recaption.core.Pruvodce.volba("tv", tvKlic(tt), pStav(), uiCode())?.first ?: TvChoice.label(this, tt)
                    option(nazev, if (tt == TvType.ANDROID) t("tv_android_d") else null, tt == type, "wtv_${tt.key}") {
                        tvPicked = true; TvChoice.set(this, tt); render(true)
                    }
                }
                // telefon jako čtečka: televizi v síti najde a předvybere sám (jen jediný druh, hráč může změnit)
                if (Prefs.readerDevice(this)) findTv()
                if (tvFinding) { addWaiting(top = 10); tvWaitShown = true }   // hledá se TV v síti (pár sekund)
                tvFound?.let { add(ui.text("✔ " + t("wz_tv_found").replace("%s", it), 16f, Theme.OK, bold = true), 12) }
                if (type.sends) {
                    add(ui.text(t("s_tv_desc"), 15f, Theme.MUTED), 14)
                    // v televizi už je aktuální ReCaption: nic nenabízet (nahrát / opravit jde v Instalaci, kapitola 3)
                    val current = TvChoice.upToDate(this, type)
                    if (current != null) add(ui.text("✔ " + t("tv_current").replace("%s", current), 16f, Theme.OK, bold = true), 12)
                    else {
                        add(tagged(ui.button(TvChoice.installLabel(this, type)) { TvChoice.openInstall(this, type, wizard = true) }, "wtvinstall"), 10)
                        add(ui.text(t("wz_tv_later"), 14f, Theme.MUTED), 8)
                    }
                }
            }
            // v režimu „Nastavuji TV“ si balíček stáhne TV sama (po změně jazyka) – v telefonu jen krok pro TV
            W_LANG -> if (remote) tvOnlyStep("lang") else {
                val ok = OfflineTranslator.isReady(lang)
                // jazyky se volí v kroku 1 (Jazyky) – tady jen balíček pro zvolenou dvojici (od 0.97.65)
                if (!ok) pruvodceRadky("lang")
                add(ui.card().apply {
                    addView(ui.checkRow(ok, false, t("chk_model") + " (${Prefs.langPair(this@MainActivity)})", t("ok"), null, null))
                    if (!ok) addView(ui.text(busyText ?: t("prep_model"), 15f, Theme.WARN), ui.lp(top = 6))
                    // stahování jazykového balíčku (asi 30 MB): pruh běhá, dokud ML Kit neskončí
                    if (!ok && OfflineTranslator.isDownloading(lang)) addView(ui.progress(null), ui.lp(h = ui.dp(10), top = 8))
                }, 12)
                if (!ok) {
                    add(tagged(ui.button(t("download")) { downloadModel() }, "wdl"), 16)
                    nextLabel = t("wz_skip_step")
                }
            }
            // Lepší překlad (od 0.97.28 bez volby DeepL / jen offline): rovnou jedna věta proč, cesta ke klíči
            // (televize: jeden čtvereček na stránku pro telefon, telefon: tlačítka a políčko) a „Čekám na klíč z telefonu…“. Bez klíče je hlavní
            // tlačítko „Přeskočit – zůstat u offline překladu“, po přijetí klíče „✔ DeepL je nastavený“ a Pokračovat.
            W_TRANSLATE -> {
                add(ui.text(t("wz_tr_why"), 17f, Theme.MUTED), 12)
                // klíč uložený z telefonu nebo políčka → DeepL rovnou zapnout
                if (DeeplKey.has(this) && !DeeplKey.enabled(this)) { DeeplKey.setEnabled(this, true); applyToService() }
                deeplSection().build(content, wizard = true)
                if (!DeeplKey.has(this)) nextLabel = t("wz_tr_skip")
            }
            // titulky jdou do televize LG / Samsung → povolení pruhu na tomto zařízení není potřeba
            W_PERM -> if (remote) {
                tvOnlyStep("perm")
                if (RemoteClient.tvOverlay == true) add(ui.text("✔ " + t("chk_overlay"), 16f, Theme.OK, bold = true), 12)
            } else if (Prefs.tvSend(this)) add(ui.text(t("s_tv_desc"), 17f, Theme.MUTED), 12) else {
                val ok = SubtitleOverlay.canShow(this)
                pruvodceRadky("perm")
                add(ui.card().apply { addView(ui.checkRow(ok, false, t("chk_overlay"), t("ok"), null, null)) }, 16)
                if (!ok) {
                    add(tagged(ui.button(t("wz_perm_open"), primary = true) { permAsked = true; openAccessibility() }, "wperm"), 16)
                    nextLabel = t("wz_skip_step")
                }
            }
            W_PAIR -> {
                add(ui.text(ct("wz_ps5"), 17f, Theme.MUTED), 12)
                ps5Form()
            }
            else -> {
                // spárování právě skončilo a průvodce sem přešel sám → „Spárováno …“ jako první pod nadpisem kroku
                val paired = Prefs.consoleReady(this)
                val pairedMsg = if (justPaired && paired) pairMsg?.also { add(ui.text("✔ $it", 16f, Theme.OK, bold = true), 12) } else null
                // „přepnout zpět na 🟢 herní účet“ jen jednou: když to už říká hláška o spárování, bod ze šablony se vynechá
                val saidGame = pairedMsg?.let { it.contains(cz.recaption.core.AccountMarks.GAME) || it.contains(cz.recaption.core.AccountMarks.GAME_OLD) } == true
                pruvodceRadky("done", skip = if (saidGame) cz.recaption.core.AccountMarks.GAME else null)
                // „Nastavuji TV“: krok Povolení se v telefonu přeskakuje – stav pruhu v TV jedním řádkem
                if (remote) add(if (RemoteClient.tvOverlay == true) ui.text("✔ " + t("chk_overlay"), 16f, Theme.OK, bold = true)
                    else ui.text(t("rs_perm_tv"), 16f, Theme.WARN, bold = true), 12)
                // víc TV nebo konzolí: jeden řádek, vysvětlení v okně (a v Instalaci, kapitola 9)
                add(tagged(ui.settingRow(t("hm_row"), "›", Theme.MUTED) { infoDialog(t("hm_row"), t("hc9_text")) }, "wmore"), 12)
                add(ui.text(t("wz_done_auto"), 15f, Theme.MUTED), 12)
                // sbírání textů ze hry pro lepší překlady (přání majitele 24. 11. 2026): jedna věta, že nic jiného
                if (Prefs.collectLines(this)) add(ui.text(t("wz_lines_note"), 15f, Theme.MUTED), 8)
                if (Prefs.consoleReady(this)) nextLabel = t("b_start")
            }
        }

        val pairing = wizard == W_PAIR && !Prefs.consoleReady(this)
        if (pairing) {
            // Připojení konzole: jen jedna řada tlačítek – ta spárování (Pokračovat / Zpět, u Xboxu Přihlásit)
            // a pod ní malé „Spárovat později“ (přeskočí na Hotovo). U Xboxu, který vlastní Zpět nemá, i malé Zpět.
            val later = row()
            if (Prefs.console(this) == Console.XBOX) later.addView(tagged(ui.smallButton(t("wz_back")) { wizardMove(-1); render() }, "wback"))
            later.addView(tagged(ui.smallButton(t("wz_pair_later")) { wizard = wizardSteps.size - 1; render() }, "wlater"),
                if (later.childCount > 0) wrapLp() else ui.lp())
            add(later, 20)
        } else {
            val buttons = mutableListOf<View>()
            buttons += tagged(ui.button(nextLabel, primary = true) {
                if (wizard < wizardSteps.size - 1) { wizardMove(1); render() }
                else {
                    val paired = Prefs.consoleReady(this)
                    finishWizard()
                    if (paired) startFlow()
                }
            }, "wnext")
            if (wizard > 0) buttons += tagged(ui.button(t("wz_back")) { wizardMove(-1); render() }, "wback")
            // Lepší překlad: jen Přeskočit/Pokračovat a Zpět (průvodce se tu nepřeskakuje celý)
            if (wizard != W_TRANSLATE) buttons += tagged(ui.button(t(if (wizard == wizardSteps.size - 1) "wz_finish" else "wz_skip")) { finishWizard() }, "wskip")
            if (narrow) {
                // úzký telefon: po dvou vedle sebe (delší popisek se zalomí), ať se tlačítka vejdou na obrazovku
                buttons.chunked(2).forEachIndexed { r, pair ->
                    val line = ui.hbox()
                    pair.forEachIndexed { i, b ->
                        (b as? TextView)?.setPadding(ui.dp(10), ui.dp(12), ui.dp(10), ui.dp(12))
                        line.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                            .apply { if (i > 0) leftMargin = ui.dp(10) })
                    }
                    add(line, if (r == 0) 24 else 10)
                }
            } else {
                val row = row()
                buttons.forEachIndexed { i, b -> if (i == 0) row.addView(b) else row.addView(b, wrapLp()) }
                add(row, 28)
            }
        }
        if (NetInfo.isHandheld(this)) add(tagged(ui.button(t("hp_back")) { backToPhoneHelper() }, "tophone"), 16)

        val target = focusedTag?.let { window.decorView.findViewWithTag<View>(it) }
            ?: window.decorView.findViewWithTag<View>(if (pairing) "pnext" else "wnext")
        ui.restoringFocus()   // stejné tlačítko znovu vybrané – bez „pulzu“
        target?.requestFocus()
    }

    /** Průvodce: nastavení telefonem (rozcestník v prvním kroku). */
    private var phoneSetup = false
    /**
     * Telefon, který ReCaption do TV nahrál, je s TV spojený ([phoneKey]): rozcestník má předvybraného pomocníka v telefonu.
     * Volba Jen ovladač TV spojení ukončí (telefon pak ukáže, že TV nedostupná) a TV se nastavuje ovladačem.
     */
    private var phoneLinked = false
    /** Rozcestník: zvolen „Web v prohlížeči“ (jinak „Pomocník v telefonu“) – stejná obrazovka se čtverečkem, jiný nadpis. */
    private var phoneSetupWeb = false

    // ---- šablona průvodce (core/Pruvodce.kt): názvy kroků, nadpisy, body a přeskakování jako na webu ----

    private fun uiCode() = Prefs.uiLang(this).code

    /** Volby hráče pro šablonu: typ TV, čím se nastavuje, konzole, DeepL, na čem aplikace běží. */
    private fun pStav(): cz.recaption.core.Pruvodce.Stav = cz.recaption.core.Pruvodce.appStav(
        tvKlic(Prefs.tvType(this)),
        if (remote || phoneFlow || Prefs.readerDevice(this)) "telefon" else if (phoneSetup) (if (phoneSetupWeb) "jine" else "telefon") else "ovladac",
        if (Prefs.console(this) == Console.XBOX) "xbox" else "ps5", DeeplKey.has(this), if (Prefs.isTv(this)) "tv" else "telefon")

    private fun tvKlic(t: TvType) = when (t) { TvType.LG -> "lg"; TvType.SAMSUNG -> "samsung"; TvType.ANDROID -> "atv" }

    /** Obrazovka průvodce → obrazovka v šabloně ([cz.recaption.core.Pruvodce.Podkrok.app]). */
    private fun wizardScreen(step: Int): String? = when (step) {
        W_LANGS -> "langs"; W_DEVICE -> "device"; W_CONSOLE -> "console"; W_LANG -> "lang"; W_PERM -> "perm"
        wizardSteps.size - 1 -> "done"; else -> null
    }

    /** Název hlavního kroku (0–5) a Hotovo (6) ze šablony – stejný jako na webu. */
    private fun groupName(g: Int): String =
        cz.recaption.core.Pruvodce.krok(cz.recaption.core.VideoSteps.STEPS[g.coerceIn(0, cz.recaption.core.VideoSteps.STEPS.size - 1)]).nazev.get(uiCode())

    /**
     * Telefon v režimu „Nastavuji TV“ na kroku, který se dělá jen v TV (jazykový balíček, povolení titulků): hláška, pod ní co
     * v TV zmáčknout (šablona) a slabý běžící pruh – až TV půjde dál, telefon taky (synchronizace kroků); Pokračovat jde i tady.
     */
    private fun tvOnlyStep(app: String) {
        add(ui.text(t("rs_tv_only"), 18f, Theme.ACCENT, bold = true), 12)
        addWaiting(top = 4)
        pruvodceRadky(app)
    }

    /** Body ze šablony pod nadpisem obrazovky průvodce (co zmáčknout); poznámky menším šedým písmem. */
    private fun pruvodceRadky(app: String, skip: String? = null) {
        for ((text, druh) in cz.recaption.core.Pruvodce.appRadky(app, pStav(), uiCode())) if (skip == null || !text.contains(skip)) when (druh) {
            "pozn" -> add(ui.text(text, 15f, Theme.MUTED), 8)
            "li" -> add(ui.text("•  $text", 17f, Theme.TEXT), 8)
            else -> add(ui.text(text, 17f, Theme.TEXT), 10)
        }
    }

    /**
     * Nastavení telefonem: čtvereček na stránku z TV ([RemoteHost], [cz.recaption.core.SetupPage]) a seznam, který se
     * odškrtává podle toho, co přišlo z telefonu (🔵 účet, spárování PS5, DeepL). Pokračovat → jazyk překladu a dál
     * průvodce na TV (hotové kroky se jen potvrdí; povolení pruhu musí být ovladačem v TV).
     */
    private fun phoneSetupScreen(focusedTag: Any?) {
        val url = if (RemoteHost.start(this)) RemoteHost.pageUrl(this) else null
        val st = RemoteHost.status(this)
        val xboxCon = Prefs.console(this) == Console.XBOX
        val done = if (xboxCon) st.deepl else st.account != null && st.pair == cz.recaption.core.SetupPage.Pair.OK
        // hotovo jako první věc, nad čtverečkem (přání majitele 22. 11. 2026)
        if (done) add(ui.text(t("sp_done"), 16f, Theme.OK, bold = true), 10)
        if (url != null) add(qrRow(url, t("wz_ph_scan") + "\n💻 " + url + "\n\n" + t("sp_where")), 14) else add(ui.text(t("rs_nolan"), 16f, Theme.WARN), 14)
        add(ui.card().apply {
            fun l(ok: Boolean, text: String) = addView(ui.text((if (ok) "✔ " else "○ ") + text, 18f, if (ok) Theme.OK else Theme.TEXT, bold = ok), ui.lp(top = 6))
            if (!xboxCon) l(st.account != null, t("wz_ph_acc") + (st.account?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""))
            if (!xboxCon) when (st.pair) {
                cz.recaption.core.SetupPage.Pair.BUSY -> {
                    addView(ui.text("⏳ " + t("sp_pair_busy"), 18f, Theme.ACCENT), ui.lp(top = 6))
                    addView(ui.waiting(0))
                }
                else -> l(st.pair == cz.recaption.core.SetupPage.Pair.OK, t("wz_ph_pair") + (st.console?.let { " · $it" } ?: ""))
            }
            l(st.deepl, t("wz_ph_deepl"))
        }, 12)
        if (!done) { add(ui.text(t("wz_ph_wait"), 16f, Theme.ACCENT, bold = true), 10); addWaiting() }
        val row = row()
        // hotové kroky (spárovaná PS5, povolený pruh) průvodce pak přeskočí – jen v tomhle toku ([phoneFlow])
        row.addView(tagged(ui.button(t("wz_ph_go"), primary = done) { phoneSetup = false; if (done) phoneFlow = true; wizard = W_LANG; render() }, "wphgo"))
        row.addView(tagged(ui.button(t("wz_back")) { phoneSetup = false; render() }, "wback"), wrapLp())
        add(row, 20)
        val target = focusedTag?.let { window.decorView.findViewWithTag<View>(it) } ?: window.decorView.findViewWithTag<View>("wphgo")
        ui.restoringFocus()
        target?.requestFocus()
    }

    /**
     * Další / předchozí krok průvodce. Přeskakují se kroky, které tady nemají co nastavit ([skipStep]); první krok
     * a Hotovo nikdy.
     */
    private fun wizardMove(d: Int) {
        wizard += d
        while (wizard in 1 until wizardSteps.size - 1 && skipStep(wizard)) {
            // krok jasný podle zařízení: uložit totéž, co by uložila volba v něm (jako dřív ručně)
            if (!remote && autoStep(wizard)) applyAutoStep(wizard)
            wizard += d
        }
        wizard = wizard.coerceIn(0, wizardSteps.size - 1)
    }

    /**
     * Krok, který se v průvodci přeskočí (přání majitele: žádné ruční kroky, které jsou hotové nebo tu nemají smysl):
     *  - režim „Nastavuji TV“ (telefon nastavuje Android TV, [RemoteClient]): Zařízení, Televize (je to Android TV),
     *    Offline překlad (balíček si stáhne TV) a Povolení (zapíná se ovladačem v TV – stav jedním řádkem v Hotovo),
     *  - telefon / box jako čtečka pro TV LG / Samsung: Povolení (pruh se tu neukazuje, byla tam jen věta),
     *  - Android TV, kterou nastavil telefon ([phoneFlow]): už spárovaná konzole a už povolený pruh.
     *  - krok jasný podle zařízení ([autoStep], od 1.00): na Android TV Zařízení a Televize, v telefonu Zařízení.
     */
    private fun skipStep(step: Int): Boolean = when {
        // Offline překlad a Povolení se dělají v TV – telefon je ukáže s hláškou „Tento krok se dělá v TV“ ([tvOnlyStep]),
        // ať jsou kroky v telefonu i v TV stejné (přání majitele 24. 11. 2026); Zařízení a Televize TV přeskakuje taky
        remote -> step == W_DEVICE || step == W_TV
        autoStep(step) -> true
        step == W_PERM && cz.recaption.core.Pruvodce.krok("zobrazeni").kdy?.plati(pStav()) == false -> true
        step == W_PAIR && cz.recaption.core.Pruvodce.krok("pripojeni").kdy?.plati(pStav()) == false -> true
        phoneFlow && step == W_PERM -> SubtitleOverlay.canShow(this)
        phoneFlow && step == W_PAIR -> Prefs.consoleReady(this)
        else -> false
    }

    /**
     * Na čem ReCaption běží, je jasné (od 1.00, přání majitele): Android TV / box → false (ne čtečka), telefon → true;
     * null = nejasné (tablet bez mobilní sítě, telefon, který hráč v pomocníkovi označil za TV / box) – krok Zařízení zůstane.
     */
    private fun obviousReader(): Boolean? = when {
        NetInfo.isTvDevice(this) -> false
        NetInfo.isHandheld(this) && (!Prefs.forceTv(this) || Prefs.tvSend(this)) -> true
        else -> null
    }

    /**
     * Krok, který podle zařízení nemá co nastavit: Zařízení (Android TV / telefon) a na Android TV Televize (= Android TV).
     * Jen když uložená volba odpovídá (kdo dřív zvolil jinak – box jako čtečka pro LG –, krok dál vidí a nic se nepřepíše).
     */
    private fun autoStep(step: Int): Boolean {
        if (step != W_DEVICE && step != W_TV) return false
        val reader = obviousReader() ?: return false
        if (Prefs.readerDevice(this) != reader) return false
        return step == W_DEVICE || (!reader && Prefs.tvType(this) == TvType.ANDROID)
    }

    /** Přeskočený krok uloží totéž jako volba v něm: Zařízení = [setReader], Televize = Android TV. */
    private fun applyAutoStep(step: Int) {
        when (step) {
            W_DEVICE -> setReader(Prefs.readerDevice(this))
            W_TV -> if (Prefs.tvType(this) == TvType.ANDROID) TvChoice.set(this, TvType.ANDROID)
        }
    }

    /** Android TV nastavená telefonem: průvodce na TV šel sám dál a hotové kroky přeskakuje (jen v tomto toku). */
    private var phoneFlow = false

    /**
     * Nastavení telefonem dokončené (telefon v režimu „Nastavuji TV“ prošel průvodcem – přišlo `wizard_done`, nebo
     * na obrazovce nastavení telefonem je vše ✔: účet, PS5 i DeepL) → TV jde sama dál: na Povolení, když pruh chybí,
     * jinak na Hotovo. Jen jednou za běh průvodce.
     */
    private fun phoneSetupAdvance() {
        if (wizard < 0 || phoneFlow) return
        val st = RemoteHost.status(this)
        val allDone = phoneSetup && st.deepl && (Prefs.console(this) == Console.XBOX || st.account != null && st.pair == cz.recaption.core.SetupPage.Pair.OK)
        if (!allDone && !Prefs.wizardDone(this)) return
        phoneSetup = false; phoneFlow = true
        val needPerm = Prefs.tvType(this) == TvType.ANDROID && !SubtitleOverlay.canShow(this)
        wizard = if (needPerm) W_PERM else wizardSteps.size - 1
        EventFile.add("WIZARD|nastaveno telefonem → " + if (needPerm) "povolení" else "hotovo")
    }

    /**
     * Krok průvodce z druhého zařízení (telefon ↔ TV). Krok, který se tady přeskakuje (telefon nestahuje jazykový balíček
     * ani nepovoluje pruh, TV nemá krok Zařízení…), se posune na nejbližší další, který tu je; druhé zařízení se tím nemění.
     */
    private fun goToSyncedStep(n: Int) {
        if (wizard < 0 || n !in 0 until wizardSteps.size || n == wizard) return
        val fwd = n > wizard
        phoneSetup = false
        var s = n
        while (s in 1 until wizardSteps.size - 1 && skipStep(s)) s += if (fwd) 1 else -1
        s = s.coerceIn(0, wizardSteps.size - 1)
        if (s == wizard) return
        wizard = s
        EventFile.add("WIZARD|krok z druhého zařízení $n → $s")
        render()
    }

    private fun finishWizard() {
        Prefs.setWizardDone(this, true)
        wizard = -1
        if (!remote) RemoteHost.tvStep = -1
        section = Section.HOME
        render()
    }

    private fun add(v: View, top: Int = 12) = target().addView(v, ui.lp(top = top))

    /** Slabý běžící pruh pod textem, na který se čeká ([Ui.waiting]); bez odsazení – pod volným textem stránky. */
    private fun addWaiting(indent: Int = 0, top: Int = 4) =
        target().addView(ui.waiting(indent).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(top) })

    // ---- rozbalené skupiny jako odsazená karta (od 0.97.53, přání majitele: nepoznal, co je podmenu) ----

    /** Otevřené karty podmenu (vnořené: Hra → Postavy, místa a pojmy); řádky přidané přes [add] jdou do poslední. */
    private val nests = ArrayList<LinearLayout>()
    /** Nadpis skupiny, kterou hráč právě rozbalil – její karta se plynule zjeví a stránka se posune na nadpis. */
    private var justOpened: String? = null

    private fun target(): LinearLayout = nests.lastOrNull() ?: content

    /**
     * Obsah rozbalené skupiny pod nadpisem [tag]: odsazená tmavší karta se žlutou čarou vlevo. Platí do dalšího
     * nadpisu skupiny ([closeNests]) nebo konce stránky; [inner] = karta uvnitř karty.
     */
    private fun openNest(tag: String, inner: Boolean = false) {
        if (!inner) closeNests()
        val body = ui.vbox().apply { setPadding(ui.dp(12), ui.dp(2), 0, ui.dp(10)) }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false; clipToPadding = false
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.rgb(0x08, 0x12, 0x2C)); cornerRadius = ui.dp(12).toFloat()
            }
            addView(View(this@MainActivity).apply {
                background = android.graphics.drawable.GradientDrawable().apply { setColor(Theme.ACCENT); cornerRadius = ui.dp(2).toFloat() }
            }, LinearLayout.LayoutParams(ui.dp(3), LinearLayout.LayoutParams.MATCH_PARENT).apply { topMargin = ui.dp(8); bottomMargin = ui.dp(8) })
            addView(body, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        target().addView(wrap, ui.lp(top = 4).apply { leftMargin = ui.dp(18) })
        nests.add(body)
        if (justOpened == tag) {
            justOpened = null
            wrap.alpha = 0f; wrap.translationY = -ui.dp(8).toFloat()
            wrap.animate().alpha(1f).translationY(0f).setDuration(180).start()
            // nadpis nahoru (s kouskem místa nad ním), ať je vidět, že obsah pod ním patří k němu
            pageScroll?.post {
                val sv = pageScroll ?: return@post
                val head = content.findViewWithTag<View>(tag) ?: return@post
                val r = android.graphics.Rect(); head.getDrawingRect(r)
                try { (sv.getChildAt(0) as? android.view.ViewGroup)?.offsetDescendantRectToMyCoords(head, r) } catch (_: Exception) { return@post }
                sv.smoothScrollTo(0, (r.top - ui.dp(16)).coerceAtLeast(0))
            }
        }
    }

    private fun closeNests() = nests.clear()

    /** Rozbalit / sbalit: zapamatovat, který nadpis se právě otevřel (animace a posun na něj). */
    private fun toggled(tag: String, opening: Boolean) { justOpened = if (opening) tag else null }

    private fun <V : View> tagged(v: V, tag: String): V { v.tag = tag; return v }

    private fun onOff(b: Boolean) = if (b) t("on") else t("off")

    private fun wrapLp(left: Int = 12) =
        if (narrow) ui.lp(top = 8)   // tlačítka pod sebou přes celou šířku
        else LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = ui.dp(left) }

    // ======================= REŽIM „NASTAVUJI TV“ (telefon, RemoteClient) =======================

    /** Telefon nastavuje Android TV: čte a zapisuje nastavení TV, každá změna odejde do TV. */
    private val remote: Boolean get() = RemoteClient.on(this)
    private var remoteBarText: android.widget.TextView? = null
    private var remoteBarWait: View? = null

    /** Pruh nahoře na každé stránce i v průvodci: „Nastavuji: <TV> · připojeno“ a Odpojit. */
    private fun remoteBar() {
        remoteBarText = null
        remoteBarWait = null
        if (!remote) return
        val line = ui.hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        val txt = ui.text("", 15f, Theme.OK, bold = true)
        line.addView(txt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        line.addView(tagged(ui.smallButton(t("rs_disconnect")) { disconnectRemote() }, "rsoff"),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = ui.dp(10) })
        // TV nedostupná: telefon zkouší znovu sám → slabý běžící pruh pod řádkem (jen se ukáže / schová)
        val wait = ui.waiting(0).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(6) }
        content.addView(ui.card().apply { addView(line); addView(wait) }, ui.lp().apply { bottomMargin = ui.dp(10) })
        remoteBarText = txt
        remoteBarWait = wait
        updateRemoteBar()
    }

    /** Jen text a barva pruhu (bez překreslení stránky – hráč může zrovna psát). */
    private fun updateRemoteBar() {
        val v = remoteBarText ?: return
        val (st, warn) = RemoteClient.stateText(this)
        v.text = t("rs_bar").replace("%s", RemoteClient.tvName) + " · " + st
        v.setTextColor(if (warn) Theme.WARN else Theme.OK)
        remoteBarWait?.visibility = if (RemoteClient.state == RemoteClient.State.UNREACHABLE) View.VISIBLE else View.GONE
    }

    /** Odpojit: telefon se vrátí ke svému nastavení a k pomocníkovi. */
    private fun disconnectRemote() {
        RemoteClient.disconnect(this)
        startActivity(Intent(this, PhoneHelperActivity::class.java))
        finish()
    }

    // ======================= DOMŮ =======================

    private fun home() {
        if (Prefs.pcMode(this) && !remote) { pcHome(); return }
        val running = TranslatorService.running
        val paused = TranslatorService.paused
        val demoRun = running && TranslatorService.demo
        // titulky jdou do televize LG → pruh na tomto zařízení není potřeba; v režimu „Nastavuji TV“ pruh
        // i jazykový balíček řeší TV sama (telefon to o ní neví)
        val overlayOk = remote || SubtitleOverlay.canShow(this) || Prefs.tvSend(this)
        val lang = Prefs.lang(this)
        val modelOk = remote || OfflineTranslator.isReady(lang)
        val onXbox = Prefs.console(this) == Console.XBOX
        val paired = Prefs.consoleReady(this)
        val ready = overlayOk && modelOk && paired
        val lic = Prefs.license(this)
        val link = TranslatorService.linkState

        val title: String
        var sub: String
        val color: Int
        when {
            !lic.canRun -> { title = t("lic_expired"); sub = t("lic_expired_sub"); color = Theme.BAD }
            running && paused -> { title = t("st_paused"); sub = t("st_paused_sub"); color = Theme.WARN }
            demoRun -> { title = t("st_running"); sub = t("st_demo"); color = Theme.OK }
            // podrobnosti (spojení, obraz, síť, rychlost…) jsou v bodech pod nadpisem, ne v textu
            running && link == Ps5.Link.CONNECTED -> { title = t("st_running"); sub = ""; color = Theme.OK }
            // PS5 v klidovém režimu (hráč ji vypnul): aplikace ji nebudí a jen čeká – žádné „neodpovídá, zkouším znovu“
            running && !onXbox && Ps5Link.asleep -> { title = ct("m_ps5"); sub = t("st_asleep"); color = Theme.WARN }
            running && link == Ps5.Link.FAILED -> {
                title = ct("m_ps5")
                sub = if (onXbox) t(TranslatorService.linkReason ?: "xb_st_failed")
                    else t(if (Ps5Link.needsLoginPin) "ps5_login_fail" else if (Ps5Link.reason == "unstable") "st_unstable" else "st_failed")
                color = Theme.BAD
            }
            running && link == Ps5.Link.WAITING -> { title = ct("m_ps5"); sub = ct("st_waiting"); color = Theme.WARN }
            running -> { title = ct("m_ps5"); sub = ct("st_connecting"); color = Theme.WARN }
            // LG / Samsung: připraveno je až s připojenou aplikací v televizi (spojení drží TvIdle i před Spustit)
            ready && Prefs.tvSend(this) && TranslatorService.tvConnected <= 0 -> { title = t("st_wait_tv"); sub = ""; color = Theme.WARN }
            ready -> { title = t("st_ready"); sub = ""; color = Theme.ACCENT }
            // co chybí, ukazují body pod nadpisem – bez dalšího textu
            else -> { title = t("st_setup"); sub = ""; color = Theme.WARN }
        }
        if (running && !demoRun && link != Ps5.Link.CONNECTED && !(onXbox && link == Ps5.Link.FAILED) && !(!onXbox && Ps5Link.asleep))
            // Xbox: důvod je srozumitelný text; PS5: jen technický kód knihovny – ten je v záznamu (PS5|…), ne na obrazovce
            TranslatorService.linkReason?.takeIf { onXbox }?.let { sub += "\n(" + t(it) + ")" }
        val buttons = row()
        if (!lic.canRun) {
            buttons.addView(tagged(ui.button(t("lic_open"), primary = true) { section = Section.SETTINGS; render() }, "start"))
        } else if (!running && !paired && !onXbox) {
            buttons.addView(tagged(ui.button(t("ps5_pair"), primary = true) { startFlow() }, "start"))
        } else if (running && demoRun) {
            buttons.addView(tagged(ui.button(t("b_testcard"), primary = true) {
                startActivity(Intent(this, TestCardActivity::class.java))
            }, "start"))
            buttons.addView(tagged(mediaButton(STOP, STOP_RED, t("b_stop")) { stopTranslation() }, "stop"), wrapLp())
        } else {
            // jako přehrávač (od 0.97.127, přání majitele): ▶ ❚❚ ■ vedle sebe, zeleně svítí to, co se právě děje
            // „Zpět do hry (HDMI)“ jen na Android TV / boxu (telefon jako čtečka do hry nepřepíná)
            if (running && Prefs.isTv(this)) buttons.addView(tagged(ui.button(t("b_go_game"), primary = true) { moveTaskToBack(true) }, "start"))
            // barva podle toho, co tlačítko udělá: spustit zeleně, pozastavit žlutě, zastavit červeně; co teď nejde, ztlumeně.
            // Jedna řada stejně velkých tlačítek přes celou šířku (od 0.97.137, majitel: rozmístění „fuj“)
            val media = mediaRow(listOf(
                tagged(mediaButton(PLAY, if (!running || paused) Theme.OK else null, t(if (paused) "b_resume" else "b_start")) {
                    if (!running) startFlow() else if (paused) togglePause()
                }, if (running && Prefs.isTv(this)) "play" else "start"),
                tagged(mediaButton(PAUSE, if (running && !paused) Theme.ACCENT else null, t("b_pause")) { if (running && !paused) togglePause() }, "pause"),
                tagged(mediaButton(STOP, if (running) STOP_RED else null, t("b_stop")) { if (running) stopTranslation() }, "stop"))
                // telefon jako čtečka: ☾ = černý displej při překladu (od 1.0.5, přání majitele) – displej nezhasne, čtení se nezpomalí
                + (if (NetInfo.isHandheld(this) && !Prefs.isTv(this)) listOf(tagged(mediaButton(MOON, if (running) MOON_GRAY else null, t("bs_btn")) {
                    if (running) startActivity(Intent(this, BlackScreenActivity::class.java))
                }, "black")) else emptyList()))
            // telefon jako čtečka: titulky ke čtení v telefonu (od 1.0.5, přání majitele – podle GlossaSubu) celou šířkou
            // pod řadou přehrávače (pátý znak by na úzkém telefonu splýval); jen při běžícím překladu, jinak ztlumeně
            val block = ui.vbox()
            block.addView(media, ui.lp())
            if (NetInfo.isHandheld(this) && !Prefs.isTv(this)) block.addView(tagged(mediaButton("\u2261  " + t("rf_btn"), MOON_GRAY, t("rf_btn")) {
                // i bez běžícího překladu – po zastavení se dají titulky dočíst (přání majitele)
                startActivity(Intent(this, ReadFeedActivity::class.java))
            }.apply { textSize = 17f }, "readfeed"), ui.lp(h = ui.dp(48), top = 10))
            buttons.addView(block, ui.lp(top = if (buttons.childCount > 0) 10 else 0))
        }
        // telefon, který nastavuje TV, sám nepřekládá – Spustit je v TV
        // tlačítka nahoře nad přehledem (od 0.97.129, přání majitele)
        // Titulky PS5 na široké obrazovce: dlaždice jako menu PS5 (velká hlavní akce + zkratky), stav vedle mřížky kartiček
        val tilesWide = !remote && !narrow && lic.canRun && !demoRun
        if (tilesWide) homeTileRow(running, paused, paired, onXbox)
        else {
            if (!remote) add(buttons, 14)
            if (!remote && !narrow) homeTiles()
        }
        if (tilesWide) {
            val rowsBox = ui.vbox()
            statusRows(rowsBox, running, demoRun, overlayOk, modelOk, paired)
            val extra = mutableListOf<View>()
            (busyText ?: sub).takeIf { it.isNotEmpty() }?.let { extra += ui.text(it, 16f, Theme.MUTED) }
            if (busyText != null && OfflineTranslator.isDownloading(Prefs.lang(this))) extra += ui.progress(null)
            if (update != null && Updater.state == Updater.State.FAILED)
                extra += ui.text(Updater.text(this, Updater.problem ?: "upd_fail"), 16f, Theme.BAD, bold = true)
            add(stateBlock(title, color, extra, rowsBox), 22)
            if (crashNote) add(ui.card().apply { addView(ui.text(t("cr_note") + " " + t("m_help") + " → " + t("rep_btn"), 15f, Theme.WARN)) }, 12)
            warnings()
            return
        }
        val status = ui.card()
        // velký nadpis jen, když je potřeba něco vědět (připojuje se, PS5 spí, chyba, čeká na TV…); „Překlad běží“ a „Připraveno“
        // ne – to ukazují tlačítka nahoře a body přehledu (od 0.97.129, přání majitele)
        val plain = title == t("st_running") && !demoRun || title == t("st_ready")
        if (!plain) {
            val head = ui.hbox()
            head.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } },
                LinearLayout.LayoutParams(ui.dp(16), ui.dp(16)).apply { rightMargin = ui.dp(14) })
            head.addView(ui.text(title, 24f, Theme.TEXT, bold = true))
            status.addView(head)
        }
        (busyText ?: sub).takeIf { it.isNotEmpty() }?.let { status.addView(ui.text(it, 16f, Theme.MUTED), ui.lp(top = 6)) }
        if (busyText != null && OfflineTranslator.isDownloading(Prefs.lang(this))) status.addView(ui.progress(null), ui.lp(h = ui.dp(10), top = 8))
        // nepovedená aktualizace hned pod velkým stavem, ne až pod řádkem verze na konci přehledu (přání majitele 22. 11. 2026)
        if (update != null && Updater.state == Updater.State.FAILED)
            status.addView(ui.text(Updater.text(this, Updater.problem ?: "upd_fail"), 16f, Theme.BAD, bold = true), ui.lp(top = 8))
        // přehled v bodech: zelená tečka = jede, oranžová = potřebuje pozornost, šedá = jen informace
        statusRows(status, running, demoRun, overlayOk, modelOk, paired)
        add(status, 18)
        if (crashNote) add(ui.card().apply {
            addView(ui.text(t("cr_note") + " " + t("m_help") + " → " + t("rep_btn"), 15f, Theme.WARN))
        }, 12)


        // upozornění, která nejsou v přehledu (končící vývojářský režim LG, licence)
        warnings()
    }

    /**
     * Titulky PS5 – Domů v režimu „Překlad z PC“: TV nečte ani nepřekládá, jen ukazuje titulky z ReCaptionu pro PC
     * (Čtečka pro TV). Spustit / Zastavit řídí [PcReceiverService].
     */
    private fun pcHome() {
        lastPc = PcReceiverService.state
        lastPcShown = PcReceiverService.shownCount
        lastPcPair = PcReceiverService.pairRequest
        val on = PcReceiverService.state != PcReceiverService.State.OFF
        val overlayOk = SubtitleOverlay.canShow(this)
        val buttons = row()
        if (on) {
            if (Prefs.isTv(this)) buttons.addView(tagged(ui.button(t("b_go_game"), primary = true) { moveTaskToBack(true) }, "start"))
            buttons.addView(tagged(mediaButton(STOP, STOP_RED, t("b_stop")) { PcReceiverService.stop(this); main.postDelayed({ render(true) }, 300) }, "stop"),
                if (buttons.childCount > 0) wrapLp() else ui.lp())
        } else {
            buttons.addView(tagged(mediaButton(PLAY, Theme.OK, "Spustit překlad z PC") {
                if (!SubtitleOverlay.canShow(this)) { toast(t("need_overlay")); return@mediaButton }
                PcReceiverService.start(this); main.postDelayed({ render(true) }, 300)
            }, "start"), ui.lp())
        }
        if (narrow) add(buttons, 14)
        else {
            val tl = mutableListOf<HomeTile>()
            if (!on && !overlayOk) tl += HomeTile("✓", "Povolit zobrazení titulků", "start", big = true) { openAccessibility() }
            else if (!on) tl += HomeTile("▶", "Spustit překlad z PC", "start", big = true) {
                PcReceiverService.start(this); main.postDelayed({ render(true) }, 300)
            } else {
                if (Prefs.isTv(this)) tl += HomeTile("⤶", t("b_go_game"), "start", big = true) { moveTaskToBack(true) }
                tl += HomeTile("■", t("b_stop"), "stop", big = !Prefs.isTv(this), color = STOP_RED) {
                    PcReceiverService.stop(this); main.postDelayed({ render(true) }, 300)
                }
            }
            // vše ostatní (PS5, hra, mobil, DeepL) se nastavuje v programu na PC
            tl += HomeTile("⚙", t("m_settings"), "tile_settings") { section = Section.SETTINGS; render() }
            tl += HomeTile("?", ct("m_help"), "tile_help") { section = Section.HELP; render() }
            tileRow(tl)
        }
        val card = if (narrow) ui.card() else ui.vbox()
        fun line(color: Int, text: String, tag: String, onClick: (() -> Unit)? = null) {
            val r = ui.hbox().apply {
                setPadding(ui.dp(6), ui.dp(7), ui.dp(6), ui.dp(7))
                if (onClick != null) { isFocusable = true; isClickable = true; background = ui.focusBg(Theme.CARD, radius = 10); setOnClickListener { onClick() } }
                addView(View(this@MainActivity).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } },
                    LinearLayout.LayoutParams(ui.dp(12), ui.dp(12)).apply { rightMargin = ui.dp(12) })
                addView(ui.text(text, 16f, if (color == Theme.MUTED) Theme.MUTED else Theme.TEXT), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                if (onClick != null) addView(ui.text("›", 20f, Theme.MUTED, bold = true).apply { setPadding(ui.dp(10), 0, ui.dp(4), 0) })
            }
            card.addView(tagged(r, tag))
        }
        line(Theme.ACCENT, "Režim · překlad z PC", "pcmode") { section = Section.SETTINGS; render() }
        when (PcReceiverService.state) {
            PcReceiverService.State.CONNECTED -> line(Theme.OK, (PcReceiverService.pcName ?: "PC") + " · připojeno " + (PcReceiverService.pcAddress ?: ""), "pcstate")
            PcReceiverService.State.SEARCHING -> { line(Theme.WARN, "PC · hledám v síti", "pcstate"); card.addView(ui.waiting()) }
            PcReceiverService.State.OFF -> line(Theme.MUTED, "PC · neběží", "pcstate")
        }
        if (PcReceiverService.state == PcReceiverService.State.CONNECTED)
            line(Theme.MUTED, "Titulky z PC · ${PcReceiverService.shownCount}", "pccount")
        // PC nemá PS5 spárovanou: klíče PS5 se pošlou až po potvrzení (jen tomuto PC, pak už samo)
        PcReceiverService.pairRequest?.let { r ->
            line(Theme.WARN, "${r.name} · PS5 nespárovaná", "pcpair")
            card.addView(tagged(ui.smallButton("Poslat spárování z TV") { PcReceiverService.sendPairing(this); main.postDelayed({ render(true) }, 300) }, "pcpair_fix"),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = ui.dp(30); bottomMargin = ui.dp(4) })
        }
        line(if (overlayOk) Theme.OK else Theme.WARN, if (overlayOk) "Pruh přes obraz · povoleno" else "Pruh přes obraz · chybí povolení", "pcoverlay",
            if (overlayOk) null else { { openAccessibility() } })
        Prefs.pcAddress(this)?.let { line(Theme.MUTED, "Poslední PC · $it", "pclast") }
        line(Theme.MUTED, if (Prefs.pcMac(this) != null) "Probuzení PC ze spánku · zapnuto" else "Probuzení PC ze spánku · po prvním spojení", "pcwake")
        line(Theme.MUTED, "Vzhled titulků, hra, DeepL · nastavuje se na PC", "pcsettings")
        if (narrow) { add(card, 18); return }
        val (title, color) = when {
            !overlayOk -> "Chybí povolení" to Theme.WARN
            PcReceiverService.state == PcReceiverService.State.CONNECTED -> "Překlad z PC běží" to Theme.OK
            PcReceiverService.state == PcReceiverService.State.SEARCHING -> "Hledám PC" to Theme.WARN
            else -> t("st_ready") to Theme.ACCENT
        }
        val extra = if (PcReceiverService.state == PcReceiverService.State.OFF && overlayOk)
            listOf<View>(ui.text("OK spustí čtení na PC a vrátí obraz do hry.", 16f, Theme.MUTED)) else emptyList()
        add(stateBlock(title, color, extra, card), 22)
    }

    /** Dlaždice na Domů (široká obrazovka): [big] = hlavní akce (větší, modrá), ostatní zkratky. */
    private class HomeTile(val icon: String, val title: String, val tag: String, val big: Boolean = false,
                           val color: Int? = null, val go: () -> Unit)

    /** Zkratky za hlavní akcí: Vzhled titulků, Mobil ve hře, Konzole, Nastavení, Nápověda. */
    private fun shortcutTiles(): List<HomeTile> = listOf(
        HomeTile("Aa", ct("m_look"), "tile_look") { section = Section.LOOK; render() },
        HomeTile("▯", "Mobil ve hře", "tile_phone") { section = Section.LOOK; lookOpen = 0; render() },
        HomeTile("◎", consoleName() ?: ct("c_console"), "tile_console") { section = Section.PS5; render() },
        HomeTile("⚙", t("m_settings"), "tile_settings") { section = Section.SETTINGS; render() },
        HomeTile("?", ct("m_help"), "tile_help") { section = Section.HELP; render() },
    )

    /** Domů na široké obrazovce: hlavní akce podle stavu (Spustit / Zpět do hry + Pozastavit + Zastavit) a zkratky. */
    private fun homeTileRow(running: Boolean, paused: Boolean, paired: Boolean, onXbox: Boolean) {
        val tl = mutableListOf<HomeTile>()
        when {
            !running && !paired && !onXbox -> tl += HomeTile("▶", t("ps5_pair"), "start", big = true) { startFlow() }
            !running -> tl += HomeTile("▶", t("b_start"), "start", big = true) { startFlow() }
            else -> {
                if (Prefs.isTv(this)) tl += HomeTile("⤶", t("b_go_game"), "start", big = true) { moveTaskToBack(true) }
                tl += HomeTile(if (paused) "▶" else "❚❚", t(if (paused) "b_resume" else "b_pause"), "pause", big = !Prefs.isTv(this)) { togglePause() }
                tl += HomeTile("■", t("b_stop"), "stop", color = STOP_RED) { stopTranslation() }
            }
        }
        // při překladu se nevejde všech pět zkratek – Nápověda je v horní liště
        tl += shortcutTiles().let { if (tl.size > 1) it.dropLast(1) else it }
        tileRow(tl)
    }

    private fun tileRow(tiles: List<HomeTile>) {
        val row = ui.hbox().apply { gravity = Gravity.BOTTOM; clipChildren = false; clipToPadding = false }
        for (tl in tiles) {
            val bg = if (tl.big) Theme.ACCENT else (tl.color?.let { Color.argb(70, Color.red(it), Color.green(it), Color.blue(it)) } ?: Theme.CARD)
            val v = ui.vbox().apply {
                isFocusable = true; isClickable = true
                background = ui.focusBg(bg, radius = if (tl.big) 22 else 18)
                ui.pop(this)
                setPadding(ui.dp(18), ui.dp(16), ui.dp(16), ui.dp(16))
                addView(ui.text(tl.icon, if (tl.big) 38f else 26f, Theme.TEXT, bold = true))
                addView(View(this@MainActivity), LinearLayout.LayoutParams(1, 0, 1f))
                addView(ui.text(tl.title, if (tl.big) 20f else 15f, Theme.TEXT, bold = true).apply { maxLines = 2 })
                setOnClickListener { tl.go() }
            }
            row.addView(tagged(v, tl.tag), LinearLayout.LayoutParams(0, ui.dp(if (tl.big) 172 else 128), if (tl.big) 1.5f else 1f).apply {
                if (row.childCount > 0) leftMargin = ui.dp(14)
            })
        }
        add(row, 18)
    }

    /**
     * Stav na Domů (široká obrazovka): vlevo „STAV“ a velký nápis s tečkou, vpravo body přehledu jako kartičky ve dvou
     * sloupcích. Body bere hotové z [rows] (řádek s tečkou + případné tlačítko či pruh pod ním = jedna kartička).
     */
    private fun stateBlock(title: String, color: Int, extra: List<View>, rows: LinearLayout): View {
        val wrap = ui.hbox().apply { gravity = Gravity.TOP }
        val left = ui.vbox()
        left.addView(ui.text("STAV", 13f, Theme.MUTED, bold = true))
        val head = ui.hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } },
            LinearLayout.LayoutParams(ui.dp(16), ui.dp(16)).apply { rightMargin = ui.dp(14) })
        head.addView(ui.text(title, 30f, Theme.TEXT, bold = true))
        left.addView(head, ui.lp(top = 6))
        extra.forEach { left.addView(it, ui.lp(top = 8)) }
        wrap.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.36f).apply { rightMargin = ui.dp(28) })
        // body → kartičky
        val kids = (0 until rows.childCount).map { rows.getChildAt(it) }
        rows.removeAllViews()
        val groups = mutableListOf<MutableList<View>>()
        for (k in kids) {
            val isRow = k is LinearLayout && k.orientation == LinearLayout.HORIZONTAL && (k.tag as? String)?.endsWith("_fix") != true
            if (isRow || groups.isEmpty()) groups += mutableListOf(k) else groups.last() += k
        }
        val grid = ui.vbox()
        groups.chunked(2).forEach { pair ->
            val line = ui.hbox()
            pair.forEachIndexed { i, g ->
                val cell = ui.vbox().apply {
                    background = GradientDrawable().apply { setColor(Theme.CARD); cornerRadius = ui.dp(14).toFloat() }
                    setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(6))
                }
                g.forEach { v ->
                    val lp = v.layoutParams as? LinearLayout.LayoutParams
                    cell.addView(v, if (lp != null) LinearLayout.LayoutParams(lp).apply { topMargin = 0 } else ui.lp())
                }
                line.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { if (i > 0) leftMargin = ui.dp(12) })
            }
            if (pair.size == 1) line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = ui.dp(12) })
            grid.addView(line, ui.lp(top = if (grid.childCount > 0) 12 else 0))
        }
        wrap.addView(grid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.64f))
        return wrap
    }

    /** Titulky PS5: velké dlaždice jako v menu PS5 – rychlý skok na Vzhled titulků, Konzoli, Nápovědu a Nastavení. */
    private fun homeTiles() {
        val tiles = ui.hbox()
        fun tile(icon: String, title: String, sub: String, tag: String, go: () -> Unit) {
            val v = ui.vbox().apply {
                isFocusable = true; isClickable = true
                background = ui.focusBg(Theme.CARD, radius = 16)
                ui.pop(this)
                setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(18))
                addView(ui.text(icon, 30f, Theme.ACCENT, bold = true))
                addView(ui.text(title, 18f, Theme.TEXT, bold = true).apply { maxLines = 1 }, ui.lp(top = 10))
                addView(ui.text(sub, 14f, Theme.MUTED).apply { maxLines = 2 }, ui.lp(top = 2))
                setOnClickListener { go() }
            }
            tiles.addView(tagged(v, tag), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                if (tiles.childCount > 0) leftMargin = ui.dp(14)
            })
        }
        tile("Aa", ct("m_look"), "Písmo, barvy, úkoly, mobil", "tile_look") { section = Section.LOOK; render() }
        tile("◎", ct("c_console"), consoleName() ?: "PS5", "tile_console") { section = Section.PS5; render() }
        tile("?", ct("m_help"), "Návody a řešení potíží", "tile_help") { section = Section.HELP; render() }
        tile("⚙", t("m_settings"), "Jazyk, aktualizace, další", "tile_settings") { section = Section.SETTINGS; render() }
        add(tiles, 16)
    }

    /**
     * Přehled na Domů v bodech (od 0.97, místo textu s návody a samostatného řádku „PS5 → televize“):
     * konzole a obraz, televize (u Android TV pruh přes obraz), síť, rychlost čtení, překlad, šetření
     * baterie (telefon jako čtečka), verze. Řádek, který potřebuje zásah, má rovnou tlačítko.
     * Konzole a televize jdou změnit OK na jejich řádku ([chooseConsole], [TvChoice.show]).
     */
    private fun statusRows(card: LinearLayout, running: Boolean, demoRun: Boolean, overlayOk: Boolean, modelOk: Boolean, paired: Boolean) {
        val lang = Prefs.uiLang(this)
        val type = Prefs.tvType(this)
        var rows = 0
        fun row(color: Int, text: String, tag: String, action: String? = null, onAction: (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
            val r = ui.hbox().apply {
                setPadding(ui.dp(6), ui.dp(7), ui.dp(6), ui.dp(7))
                if (onClick != null) {
                    isFocusable = true; isClickable = true
                    background = ui.focusBg(Theme.CARD, radius = 10)
                    setOnClickListener { onClick() }
                }
                addView(View(this@MainActivity).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } },
                    LinearLayout.LayoutParams(ui.dp(12), ui.dp(12)).apply { rightMargin = ui.dp(12) })
                addView(ui.text(text, 16f, if (color == Theme.MUTED) Theme.MUTED else Theme.TEXT), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                // klikací řádek pozná hráč podle „›“ (od 1.00-rc20, přání majitele – informativní řádky ho nemají)
                if (onClick != null) addView(ui.text("›", 20f, Theme.MUTED, bold = true).apply { setPadding(ui.dp(10), 0, ui.dp(4), 0) })
            }
            card.addView(tagged(r, tag), ui.lp(top = if (rows++ == 0) 10 else 0))
            if (action != null && onAction != null) {
                // malé žluté tlačítko (běžné tlačítko má barvu karty a v kartě splývalo s textem)
                card.addView(tagged(ui.smallButton(action) { onAction() }, tag + "_fix"),
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = ui.dp(30); bottomMargin = ui.dp(4) })
            }
        }
        // konzole a obraz
        val console = Prefs.console(this)
        // krátce jako ostatní řádky: „PS5-151 · připojeno“ (jméno konzole, jinak PS5 / Xbox)
        val conName = consoleName() ?: if (console == Console.XBOX) "Xbox" else "PS5"
        if (running && !demoRun) {
            val ls = TranslatorService.linkStatus()
            val (color, state) = if (console == Console.PLAYSTATION && Ps5Link.asleep) Theme.WARN to t("h_con_sleep") else when (ls.stage) {
                cz.recaption.core.LinkStatus.Stage.IMAGE -> Theme.OK to t("tv_st_on")
                cz.recaption.core.LinkStatus.Stage.STALLED, cz.recaption.core.LinkStatus.Stage.NO_IMAGE -> Theme.WARN to t("h_con_noimg")
                cz.recaption.core.LinkStatus.Stage.FAILED -> Theme.WARN to t("h_con_fail")
                cz.recaption.core.LinkStatus.Stage.PAUSED -> Theme.MUTED to t("st_paused").lowercase()
                else -> Theme.MUTED to t("h_con_wait")
            }
            row(color, "$conName · $state", "linkline", onClick = { chooseConsole() })
            val sleeping = console == Console.PLAYSTATION && Ps5Link.asleep
            // spící PS5: nic se nepřipojuje, pruh „pracuji“ by klamal
            if (!sleeping && (ls.stage == cz.recaption.core.LinkStatus.Stage.CONNECTING || ls.stage == cz.recaption.core.LinkStatus.Stage.RETRYING ||
                ls.stage == cz.recaption.core.LinkStatus.Stage.WAIT_IMAGE)) card.addView(ui.progress(null), ui.lp(h = ui.dp(8), top = 2))
            // čeká se: PS5 spí (aplikace se každých pár sekund ptá, jestli ji hráč zapnul) nebo obraz nechodí (hlídání spojení čeká)
            else if (sleeping || ls.stage == cz.recaption.core.LinkStatus.Stage.STALLED || ls.stage == cz.recaption.core.LinkStatus.Stage.NO_IMAGE)
                card.addView(ui.waiting())
            // spojení ukončil sám PlayStation: jiné zařízení se stejným účtem, nebo jeho vlastní důvod (ne když spí – vypnul ji hráč)
            if (!ls.console && console == Console.PLAYSTATION && !sleeping) {
                val refusal = Ps5Link.refusal
                if (Ps5Link.otherDevice || refusal == Ps5.Refusal.IN_USE) card.addView(ui.text(t("ls_other_device"), 14f, Theme.WARN), ui.lp(top = 2))
                // PS5 odmítla spojení ještě před obrazem: místo anglické hlášky knihovny co s tím (od 0.97.31)
                // i neznámý důvod má radu: nejčastěji chybí „Povolit Remote Play“ pod účtem pro ReCaption (kolega majitele,
                // 20. 11. 2026 – PS5 to hlásí obecným kódem, knihovna jen „Unknown Session Request Error“)
                else if (refusal != null) {
                    card.addView(ui.text(t("ps5_ref_" + refusal.name.lowercase()).replace("%s", accName()), 14f, Theme.WARN), ui.lp(top = 2))
                    if (refusal == Ps5.Refusal.ACCOUNT) card.addView(tagged(ui.smallButton(t("ps5_ref_repair")) { section = Section.PS5; render() }, "hrepair"), ui.lp(top = 6))
                }
                // důvod od PS5 (anglický text knihovny) je v záznamu a v hlášení („PlayStation ukončil spojení“), na Domů jen věta
                else if (Ps5Link.endedByConsole != null) card.addView(ui.text(t("ls_ended"), 14f, Theme.WARN), ui.lp(top = 2))
            }
        } else {
            // překlad neběží: zeleně jen PS5, která v síti odpověděla zapnutá (od 0.97.75, majitel: vypnutá PS5 svítila zeleně)
            val probe = if (paired && console == Console.PLAYSTATION) Ps5Probe.state else null
            val (color, state) = when {
                !paired -> Theme.WARN to t("sum_nopair")
                probe == Ps5Probe.State.ON -> Theme.OK to t("h_con_on")
                probe == Ps5Probe.State.REST -> Theme.WARN to t("h_con_sleep")
                probe != null -> Theme.WARN to t("h_con_ready")
                else -> Theme.OK to t("h_con_ready")
            }
            row(color, "$conName · $state", "hcon", onClick = { chooseConsole() })
        }
        // televize
        if (remote) {
            // telefon nastavuje Android TV: jméno TV a stav spojení s ní
            val (st, warn) = RemoteClient.stateText(this)
            row(if (warn) Theme.WARN else Theme.OK, RemoteClient.tvName + " · " + st, "htv")
        } else if (type == TvType.ANDROID) {
            if (overlayOk) row(Theme.OK, t("h_bar_ok"), "htv", onClick = { TvChoice.show(this) { render(true) } })
            else row(Theme.WARN, t("w_overlay"), "htv", t("fix"), { settingsTab = SettingsTab.GENERAL; section = Section.SETTINGS; render() })
        } else {
            // jen spojení: „LG OLED55B7 · připojeno“ zeleně, jinak „nepřipojeno“ oranžově (verze aplikace má vlastní řádek)
            val (tvLine, connected) = TvChoice.short(this)
            row(if (connected) Theme.OK else Theme.WARN, tvLine, "htv", onClick = { TvChoice.show(this) { render(true) } })
            // čeká se, až se TV připojí (port je otevřený, TV telefon v síti hledá) – slabý běžící pruh pod oranžovým řádkem
            if (!connected && TranslatorService.tvConnected == 0) {
                card.addView(ui.waiting())
                if (tvWaitSince == 0L) tvWaitSince = System.currentTimeMillis()
                // dlouho nepřipojeno (tester 24. 11. 2026: po resetu routeru skončily telefon a TV na jiném
                // pásmu Wi‑Fi, 2,4 GHz / 5 GHz) – po 20 s rada, kam se podívat
                if (System.currentTimeMillis() - tvWaitSince > 20_000) card.addView(ui.text(t("h_tv_wait_net"), 14f, Theme.MUTED), ui.lp(top = 4))
            } else tvWaitSince = 0L
        }
        // síť tohoto zařízení – jen věcně, bez varování (vysvětlení k výpadkům je v Nápovědě)
        when (NetInfo.kind(this)) {
            Ps5.Net.CABLE -> row(Theme.OK, t("h_net_cable"), "hnet")
            Ps5.Net.WIFI_5 -> row(Theme.OK, t("h_net_5"), "hnet")
            Ps5.Net.WIFI_24 -> row(Theme.OK, t("h_net_24"), "hnet")
            Ps5.Net.UNKNOWN -> {}
        }
        // překlad: jazyk, offline / DeepL, chybějící balíček
        val pair = Prefs.langPair(this)
        if (!modelOk) row(Theme.WARN, t("w_model").replace("%s", pair), "hmodel", t("download"), { downloadModel() })
        // bez běžícího překladu jazyk a hra šedě (jen informace) – stejně jako stav v TV LG/Samsung (od 0.97.101)
        else row(if (running) Theme.OK else Theme.MUTED, pair + " · " + t(if (DeeplTranslator.active(this)) "dl_mode_on" else "h_offline"), "hmodel", onClick = { openQuality() })
        // hra: vybraný profil zeleně; titulky ukazují na jinou hru → oranžově s Přepnout (GameGuess, nic se nepřepíná samo).
        // S univerzálním profilem a bez odhadu řádek není.
        val game = GameProfiles.of(Prefs.gameId(this))
        val guess = Prefs.gameGuess(this)?.let { GameProfiles.of(it) }
        // vybraná hra zůstává vidět, odhad je za ní s otazníkem: „Call of Duty · Grand Theft Auto VI?“
        if (guess != null) row(Theme.WARN, (if (game.id != "all") game.title + " · " else "") + guess.title + "?", "hgame", t("gp_switch"),
            { Prefs.setGameId(this, guess.id); applyToService(); render(true) }, onClick = { pickGame() })
        else if (game.id != "all") row(if (running) Theme.OK else Theme.MUTED, game.title, "hgame", onClick = { pickGame() })
        // verze ReCaptionu: aktuální zeleně, novější ke stažení oranžově s tlačítkem Aktualizovat v řádku
        val me = "Titulky PS5 ${BuildInfo.VERSION.removePrefix("verze ")}"
        val o = update
        if (o == null) row(Theme.OK, me, "hver")
        else {
            val st = Updater.state
            row(Theme.WARN, me + " · " + t("h_ver_new").replace("%s", o.remote.name), "hver",
                if (Updater.busy) null else t("upd_btn"), { startUpdate(o) })
            val line = when (st) {
                Updater.State.DOWNLOADING -> t("upd_down") + (if (Updater.percent >= 0) " ${Updater.percent} %" else "…")
                Updater.State.INSTALLING -> Updater.text(this, "upd_inst")
                // chyba aktualizace je nahoře pod velkým stavem (home); tady jen adresa k ručnímu stažení
                else -> null
            }
            line?.let { card.addView(ui.text(it, 14f, Theme.MUTED), ui.lp(top = 2)) }
            if (st == Updater.State.DOWNLOADING) card.addView(ui.progress(if (Updater.percent >= 0) Updater.percent / 100f else null), ui.lp(h = ui.dp(8), top = 4))
            if (st == Updater.State.INSTALLING) card.addView(ui.progress(null), ui.lp(h = ui.dp(8), top = 4))
            // když to z aplikace nejde, zbývá ruční cesta – adresa souboru, klikatelná (otevře stažení v prohlížeči,
            // který instalaci řeší sám – obchází nepovedenou instalaci přes PackageInstaller) a s tlačítkem Kopírovat,
            // ať jde adresu poslat na jiné zařízení (testeři hlásili, že se text nedal ani zkopírovat, 24. 11. 2026)
            if (st == Updater.State.FAILED) {
                val apkUrl = UpdateCheck.url(o.channel, "TitulkyPS5.apk")
                card.addView(ui.text(apkUrl, 13f, Theme.ACCENT).apply {
                    isClickable = true
                    setOnClickListener { try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(apkUrl))) } catch (_: Exception) {} }
                }, ui.lp(top = 4))
                card.addView(tagged(ui.smallButton(t("zz_copy")) {
                    try {
                        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Titulky PS5", apkUrl))
                        Toast.makeText(this, t("upd_url_copied"), Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {}
                }, "updcopy"), ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT, top = 6))
            }
            // co je nového od nainstalované verze po tu nabízenou (přání majitele, 24. 11. 2026): pomoc/novinky.json,
            // stahuje se jako videa/kroky.json (NovinkyUpdate); bez staženého seznamu / bez bodů pro tenhle rozsah nic není
            NovinkyUpdate.load(this) { runOnUiThread { if (!isFinishing && section == Section.HOME) render(keepFocus = true) } }
            val novinky = Novinky.mezi(NovinkyUpdate.zaznamy, BuildInfo.VERSION_CODE, o.remote.code)
            if (novinky.isNotEmpty()) card.addView(tagged(ui.smallButton(t("wn_row")) {
                val cesky = Prefs.uiLang(this).code == "cs" || Prefs.uiLang(this).code == "sk"
                val text = novinky.joinToString("\n\n") { z -> z.verze + "\n" + (if (cesky) z.cs else z.en).joinToString("\n") { "• $it" } }
                infoDialog(t("wn_row"), text)
            }, "hwhatsnew"), ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT, top = 6))
        }
        // aplikace v televizi LG/Samsung: stejně – chybí / starší oranžově s tlačítkem nahrání, aktuální zeleně
        if (type.sends) {
            val tvName = if (type == TvType.LG) "LG" else "Samsung"
            val bundled = TvChoice.bundledVersion(this, type)
            val old = TvChoice.outdated(this, type)
            val current = TvChoice.upToDate(this, type)
            when {
                TvChoice.needsInstall(this, type) -> row(Theme.WARN, t("h_tv_app_none").replace("%s", tvName), "htvapp", t("w_install"), { TvChoice.openInstall(this, type) })
                // neznámá verze v TV (aplikace před 1.1.0) = bez čísla, ne „?“ (přání majitele, 0.97.30)
                // LG spárovaná a známá adresa: aktualizace přímo tady (LgUpdate), bez další stránky (od 0.97.93, přání majitele)
                type == TvType.LG && LgUpdate.running -> {
                    row(Theme.WARN, "Titulky PS5 " + t("h_ver_tv").replace("%s", tvName) + " · " + t("lg_p_" + (LgUpdate.phase ?: cz.recaption.core.LgPhase.PACKAGE).name.lowercase()), "htvapp")
                    card.addView(ui.progress(((LgUpdate.phase ?: cz.recaption.core.LgPhase.PACKAGE).ordinal + 1f) / cz.recaption.core.LgPhase.values().size), ui.lp(h = ui.dp(8), top = 2))
                }
                old != null -> row(Theme.WARN, "Titulky PS5 " + t("h_ver_tv").replace("%s", if (old.first == "?") tvName else "$tvName ${old.first}") +
                    " · " + t("h_ver_new").replace("%s", old.second), "htvapp", t(if (type == TvType.LG) "lg_update" else "w_install"), {
                        if (type == TvType.LG && LgUpdate.direct(this)) LgUpdate.start(this) { runOnUiThread { if (!isFinishing && section == Section.HOME) render(keepFocus = true) } }
                        else TvChoice.openInstall(this, type, update = true)
                    })
                current != null -> row(Theme.OK, "Titulky PS5 " + t("h_ver_tv").replace("%s", "$tvName $current"), "htvapp")
                bundled != null -> row(Theme.MUTED, "Titulky PS5 " + t("h_ver_tv").replace("%s", tvName), "htvapp")
            }
            // aktualizace z Domů selhala: chyba a cesta na stránku s přípravou (tam je postup krok za krokem)
            if (type == TvType.LG && !LgUpdate.running) LgUpdate.error?.let { e ->
                card.addView(ui.text(t("lg_e_" + e.name.lowercase()), 14f, Theme.BAD), ui.lp(top = 2))
                card.addView(tagged(ui.smallButton(t("lg_open")) { LgUpdate.clearError(); TvChoice.openInstall(this, type) }, "htvfix"),
                    ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT, top = 6))
            }
        }
        // rychlost čtení (o kolik se titulek opozdí za obrazem) – od 0.97.71 až úplně dole pod nenápadnou čarou (přání majitele:
        // řádek uprostřed se objevoval a měnil barvu, přehled poskakoval); oranžová od 450 ms, zpět zelená pod 350 ms (neblikne)
        if (running) TranslatorService.readMs.takeIf { it > 0 }?.let { ms ->
            speedWarn = ms >= 450 || (speedWarn && ms >= 350)
            card.addView(View(this).apply { setBackgroundColor(Theme.SIDEBAR) }, ui.lp(h = ui.dp(1), top = 6))
            row(if (speedWarn) Theme.WARN else Theme.OK, t("h_speed").replace("%d", ms.toString()) +
                TranslatorService.videoSize.takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty(), "hspeed")
        }
        // zkratka pro ladění (⚙ → Experimentální → „Snímky na Domů“): úplně dole, jen tlačítka ▶ / ■ Snímky a Odeslat (od 0.97.119)
        if (!remote && Prefs.snapHome(this)) snapHomeRow(card, running) { c, text, tag -> row(c, text, tag) }
        // Titulky PS5: místo „Podpořit“ a Facebooku odkaz na zdrojový kód (AGPL-3.0) – úplně dole nenápadný šedý řádek
        card.addView(View(this).apply { setBackgroundColor(Theme.SIDEBAR) }, ui.lp(h = ui.dp(1), top = 6))
        row(Theme.MUTED, "Zdrojový kód · licence AGPL-3.0", "hsource",
            onClick = { openLink("https://github.com/zaoo-star/titulky-ps5", "Titulky PS5") })
    }

    /**
     * Řádek Snímky na Domů (⚙ → Experimentální → „Snímky na Domů“, výchozí vypnuto): „● Snímky“ spustí záznam
     * ([SnapRecorder], jako tlačítko v upozornění – jen při běžícím překladu), během záznamu „Snímky · nahrávám (n)“
     * a „■ Snímky“ zastaví; s hotovou dávkou „Odeslat“ = [ReportSender.sendLatest] (bez volby a potvrzení, pak kód).
     * Počet snímků bere z proměnné SnapRecorder, hotové dávky ze souhrnu spočítaného jednou na pozadí.
     */
    private fun snapHomeRow(card: LinearLayout, running: Boolean, row: (Int, String, String) -> Unit) {
        val active = SnapRecorder.active
        // nadpis s počtem snímků během nahrávání (majitel chtěl počet vidět), jinak jen „Snímky“
        row(if (active) Theme.OK else Theme.MUTED, if (active) t("snd_home_rec").replace("%c", SnapRecorder.count.toString()) else t("snd_home_row"), "hsnap")
        val saved = SnapRecorder.summary
        if (saved == null) SnapRecorder.refreshSummary(this) { if (!isFinishing && section == Section.HOME) render(keepFocus = true) }
        // hned po Stop souhrn ještě není přepočítaný – dávka, která právě skončila, jde odeslat rovnou (hlášení 22VF-GGWU)
        val ready = !active && ((saved?.second ?: 0) > 0 || (saved == null && SnapRecorder.justStopped))
        // ▶ zeleně (spustit), ■ červeně (zastavit), co teď nejde, ztlumeně; Odeslat modře – stejná řada jako nahoře
        fun toggle() { if (!SnapRecorder.toggle(this)) toast(t("sn_need_run")); render(keepFocus = true) }
        val items = mutableListOf<View>(
            tagged(mediaButton(PLAY, if (!active && running) Theme.OK else null, t("sn_n_start"), enabled = !active) { if (!active) toggle() }, "hsnap_rec"),
            tagged(mediaButton(STOP, if (active) STOP_RED else null, t("sn_n_stop")) { if (active) toggle() }, "hsnap_stop"))
        items += if (ready) tagged(mediaButton(t("snd_home_send"), SEND_BLUE, t("snd_home_send")) {
            ReportSender.sendLatest(this, ui) { if (section == Section.HOME) render(keepFocus = true) }
        }.apply { textSize = 17f; typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD) }, "hsnap_send")
            else mediaButton(t("snd_home_send"), null, t("snd_home_send")) {}.apply { textSize = 17f }
        card.addView(mediaRow(items), ui.lp(top = 10).apply { bottomMargin = ui.dp(4) })
    }

    /**
     * Tlačítko přehrávače (▶ ❚❚ ■, od 0.97.127): [color] = barva akce (spustit zeleně, pozastavit žlutě, zastavit červeně,
     * od 0.97.128 podle běžné praxe – barva podle toho, co tlačítko udělá), null = teď nejde (šedé). Popisek pro čtečku
     * obrazovky [label]. Znaky s U+FE0E, ať se nekreslí jako barevné emoji.
     */
    private fun mediaButton(symbol: String, color: Int?, label: String, enabled: Boolean = color != null, onClick: () -> Unit): TextView =
        ui.button(symbol) { onClick() }.apply {
            // co teď nejde, nejde ani vybrat ani stisknout (od 1.00-rc21, přání majitele: klik, který nic nedělá, mate)
            if (!enabled) { isFocusable = false; isClickable = false }
            textSize = 22f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 0)
            // nejde = ztlumený vlastní podklad (na Domů i v kartě je vidět, že tlačítko je, jen teď nejde)
            background = ui.focusBg(color ?: MEDIA_OFF)
            // Titulky PS5: na světlém podkladu (zelená ▶, žlutá) tmavý symbol, na tmavém bílý
            setTextColor(when (color) { null -> MEDIA_OFF_TEXT; Theme.OK, Theme.WARN -> Theme.BG; else -> android.graphics.Color.WHITE })
            contentDescription = label
        }

    /** Řada tlačítek přehrávače: stejná šířka a výška, stejné mezery, přes celou šířku (od 0.97.137). */
    private fun mediaRow(items: List<View>): LinearLayout = ui.hbox().apply {
        items.forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(0, ui.dp(56), 1f).apply { if (i > 0) leftMargin = ui.dp(10) })
        }
    }

    private val MEDIA_OFF = android.graphics.Color.rgb(0x17, 0x24, 0x44)
    private val MEDIA_OFF_TEXT = android.graphics.Color.rgb(0x5E, 0x6E, 0x92)

    private val STOP_RED = android.graphics.Color.rgb(0xE5, 0x48, 0x5F)
    private val SEND_BLUE = android.graphics.Color.rgb(0x3B, 0x82, 0xF6)
    private val MOON_GRAY = android.graphics.Color.rgb(0x4B, 0x55, 0x63)

    private val PLAY = "\u25B6\uFE0E"
    private val PAUSE = "\u275A\u275A"
    private val STOP = "\u25A0\uFE0E"
    private val MOON = "\u263E\uFE0E"

    /** Je čtení pomalé (oranžový řádek rychlosti na Domů)? Pamatuje se kvůli rozpětí 350–450 ms, ať barva neblikne. */
    private var speedWarn = false
    /** Od kdy TV LG/Samsung nepřipojená (0 = právě teď připojená) – po chvíli ukáže radu na síť. */
    private var tvWaitSince = 0L

    /** Smí ReCaption běžet i při zhasnutém displeji (bez omezení šetřením baterie)? */
    private fun batteryUnrestricted(): Boolean = try {
        (getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(packageName)
    } catch (_: Throwable) { true }

    /**
     * Samsung: Nastavení → Péče o baterii a zařízení → Baterie → Limity využití na pozadí → Nikdy neuspávané aplikace.
     * Názvy obrazovek se mezi verzemi One UI liší, proto se zkouší postupně; když žádná nejde, obecný seznam
     * šetření baterie, nakonec Nastavení. Nikdy nespadne.
     */
    private fun openSamsungNeverSleep() {
        val tries = ArrayList<Intent>()
        for (pkg in listOf("com.samsung.android.lool", "com.samsung.android.sm", "com.samsung.android.sm_cn")) {
            // přímo seznam nikdy neuspávaných aplikací (activity_type 2), pak obrazovka Baterie
            tries += Intent("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY").setPackage(pkg).putExtra("activity_type", 2)
            tries += Intent().setClassName(pkg, "com.samsung.android.sm.battery.ui.BatteryActivity")
            tries += Intent().setClassName(pkg, "com.samsung.android.sm.ui.battery.BatteryActivity")
        }
        tries += Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        tries += Intent(Settings.ACTION_SETTINGS)
        for (i in tries) {
            try { startActivity(i); return } catch (_: Throwable) {}
        }
    }

    /** Systémový dotaz „Povolit běh na pozadí bez omezení?“; když ho systém nemá, seznam aplikací. */
    @android.annotation.SuppressLint("BatteryLife")
    private fun askBatteryUnrestricted() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {}
        }
    }

    /** Malá nabídka ze souhrnného řádku: změnit konzoli, nebo typ televize. */
    private fun chooseSetup() {
        val c = Prefs.console(this)
        val items = arrayOf(
            t("c_console") + ": " + t(if (c == Console.XBOX) "c_xbox" else "c_ps"),
            t("tv_type") + ": " + TvChoice.label(this, Prefs.tvType(this)),
        )
        try {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(t("sum_q"))
                .setItems(items) { d, i -> d.dismiss(); if (i == 0) chooseConsole() else TvChoice.show(this) { render(true) } }
                .show()
        } catch (_: Exception) { section = Section.SETTINGS; render() }
    }

    /**
     * Upozornění na Domů – jen když něco chybí: povolení pruhu (Android TV), jazykový model,
     * nespárovaná konzole, aplikace v televizi LG/Samsung (nenahraná, nebo nepřipojená za běhu),
     * končící vývojářský režim LG a zkušební doba (3 dny a méně). Každé jde rovnou opravit.
     */
    private fun warnings() {
        val card = ui.card()
        fun warn(v: View) = card.addView(v, ui.lp(top = if (card.childCount == 0) 0 else 4))
        fun note(s: String) = warn(ui.text(s, 15f, Theme.WARN, bold = true).apply { setPadding(0, ui.dp(8), 0, ui.dp(8)) })
        val type = Prefs.tvType(this)
        // LG: když vývojářský režim brzy vyprší, aplikace z televize zmizí – upozornit
        if (type == TvType.LG) {
            LgInstallActivity.shortLine(this)?.takeIf { it.second }?.let { (s, _) -> note(s) }
            LgDevMode.refresh(this, false) {
                runOnUiThread { if (!isFinishing && wizard < 0 && section == Section.HOME && currentFocus !is EditText) render(true) }
            }
        }
        // licence a platby jsou zatím skryté (odkryjí se při vydání) – upozornění na konec zkušební doby taky
        val lic = Prefs.license(this)
        if (SHOW_LICENSE && lic.kind == cz.recaption.core.License.Kind.TRIAL && lic.daysLeft <= 3) {
            warn(ui.checkRow(false, false, t("lic_trial").replace("%d", lic.daysLeft.toString()), t("ok"), t("lic_open")) {
                section = Section.SETTINGS; render()
            })
        }
        if (card.childCount > 0) add(card, 12)
    }

    /** Náhled: co aplikace z PlayStationu právě vidí (důkaz, že spojení funguje). */
    private fun preview() {
        val snap = TranslatorService.snapshot ?: run { add(ui.progress(null), 8); return }
        val w = ui.dp(420)
        val h = w * snap.height / maxOf(1, snap.width)
        content.addView(ImageView(this).apply {
            setImageBitmap(snap)
            scaleType = ImageView.ScaleType.FIT_XY
        }, LinearLayout.LayoutParams(w, h))
        // náhled je poslední snímek – když obraz zrovna nechodí, je starý (nebo černý); říct proč
        val ls = TranslatorService.linkStatus()
        if (!ls.image) add(ui.text(t("pv_stale") + " " + cz.recaption.core.LinkStatus.line(Prefs.uiLang(this), ls, Prefs.console(this)), 14f, Theme.WARN), 6)
        // Android TV: z náhledu rovnou zpět do hry (kolega majitele zůstal v aplikaci a nevěděl jak ven, 20. 11. 2026);
        // náhled se počítá jen při rozbalené skupině a vidět aplikaci – sbalením nebo odchodem se zastaví
        if (Prefs.isTv(this)) add(tagged(ui.smallButton(t("b_go_game")) { moveTaskToBack(true) }, "pvgame"), 10)
    }

    // ======================= PLAYSTATION =======================

    private fun ps5() {
        ps5Form()
        // znovu něco z telefonu (jiný účet, spárovat znovu, DeepL) – i u už nastavené TV, bez hledání v ⚙ (od 0.97.64)
        if (wizard < 0 && RemoteHost.eligible(this) && Prefs.console(this) == Console.PLAYSTATION) remotePhoneRow("remoteps5")
        // náhled obrazu jen po rozbalení – jinak se snímek z konzole vůbec nepočítá (od 0.97.15)
        if (TranslatorService.running && !TranslatorService.demo && group("preview", ct("ps5_preview"), 18)) preview()
    }

    private fun input(hintKey: String, value: String, tagName: String, number: Boolean) = EditText(this).apply {
        setText(value)
        hint = t(hintKey)
        setSingleLine(true)
        inputType = if (number) InputType.TYPE_CLASS_PHONE
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_DONE
        setTextColor(Theme.TEXT)
        setHintTextColor(Theme.MUTED)
        textSize = 17f
        background = ui.focusBg(Theme.CARD)
        setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12))
        tag = tagName
    }

    /**
     * Políčko pro přihlašovací kód účtu na PS5 (4 číslice): skoro nikdo ho nepotřebuje, takže se ukáže,
     * jen když o kód PlayStation při spojení požádal, nebo když už nějaký je uložený.
     */
    private fun loginPinWanted() = Ps5Link.needsLoginPin || draftLogin.isNotEmpty() || Prefs.ps5LoginPin(this).isNotEmpty()

    /** Hledání PlayStationu v síti nic nenašlo → nabídnout načtení adresy kamerou. */
    private var ps5NotFound = false
    /** Na co se čeká po povolení kamery: adresa (true) nebo číslo pro spárování. */
    private var scanIpPending = false

    private fun scanPs5Code(ip: Boolean = false) {
        saveDrafts()
        if (checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            @Suppress("DEPRECATION") startActivityForResult(Intent(this, LgScanActivity::class.java)
                .putExtra(LgScanActivity.EXTRA_MODE, if (ip) LgScanActivity.MODE_PS5_IP else LgScanActivity.MODE_PS5), if (ip) REQ_PS5_IP_SCAN else REQ_PS5_SCAN)
        } else { scanIpPending = ip; requestPermissions(arrayOf(android.Manifest.permission.CAMERA), REQ_PS5_CAMERA) }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        @Suppress("DEPRECATION") super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PS5_CAMERA) return
        if (grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) scanPs5Code(scanIpPending)
        else toast(t("km_denied"))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PS5_SCAN && requestCode != REQ_PS5_IP_SCAN) return
        val code = data?.getStringExtra(LgScanActivity.EXTRA_CODE)
        if (resultCode == RESULT_OK && code != null) {
            saveDrafts()
            if (requestCode == REQ_PS5_IP_SCAN) {
                draftIp = code; Prefs.setPs5Ip(this, code); pairMsg = null
            } else draftPin = code
            // políčko s číslem ještě drží starý text – nepřepsat jím přečtené číslo
            skipDraftSave = true
            try { render(true) } finally { skipDraftSave = false }
            // číslo z PS5 přečtené kamerou → spárovat hned
            if (requestCode == REQ_PS5_SCAN && autoPairOk()) pairPs5()
        } else if (data?.getBooleanExtra(LgScanActivity.EXTRA_FAILED, false) == true) toast(t("km_cam_fail"))
    }

    /** Uloží rozepsané údaje z políček, než se obrazovka překreslí. */
    private fun saveDrafts() {
        fun read(tagName: String): String? = window.decorView.findViewWithTag<View>(tagName)?.let { (it as? EditText)?.text?.toString() }
        read("pip")?.let { draftIp = it.trim() }
        read("ppin")?.let { draftPin = it.trim() }
        read("pauth")?.let { draftAuth = it.trim() }
        read("plogin")?.let {
            draftLogin = it.trim()
            if (draftLogin.isEmpty() || (draftLogin.length == 4 && draftLogin.all(Char::isDigit))) Prefs.setPs5LoginPin(this, draftLogin)
        }
        read("dicten")?.let { draftEn = it }
        read("dicttr")?.let { draftTr = it }
        read("code")?.let { draftCode = it }
    }

    /** Spárování s PlayStationem (stránka PlayStation i krok průvodce). */
    private fun ps5Form() {
        if (Prefs.console(this) == Console.XBOX) {
            xbox.render(null); return   // Spustit jen na Domů
        }
        val host = Prefs.ps5Host(this)
        if (draftLogin.isEmpty()) draftLogin = Prefs.ps5LoginPin(this)
        if (host != null && !pairAnother) {
            val c = ui.card()
            c.addView(ui.text(t("ps5_paired") + ": " + host.name, 20f, Theme.OK, bold = true))
            c.addView(ui.text(host.ip, 15f, Theme.MUTED), ui.lp(top = 4))
            // kterým účtem je ReCaption spárované – se značkou 🔵 účtu pro ReCaption (jméno znají účty od 0.97.28)
            Prefs.psnName(this).takeIf { it.isNotEmpty() }?.let { c.addView(ui.text(cz.recaption.core.AccountMarks.named(it), 15f, Theme.MUTED), ui.lp(top = 4)) }
            add(c, 16)
            // výsledek / chyba hned pod kartou se spárovanou PS5 (přání majitele 22. 11. 2026)
            pairMsg?.let { val res = pairMsgColor != Theme.MUTED; add(ui.text(it, if (res) 16f else 15f, pairMsgColor, bold = res), 10) }
        // hledání PS5, ověřování účtu, párování: pruh běhá, ať je vidět, že se něco děje
        if (pairBusy) add(ui.progress(null), 8)
            // Spustit je jen na Domů (telefon i Android TV) – na stránce Konzole bylo podruhé (od 0.97.13)
            // přihlašovací kód účtu na PS5 jen tehdy, když ho PlayStation chtěl (nebo už je zadaný)
            if (loginPinWanted()) add(input("ps5_login", draftLogin, "plogin", number = true), 12)
            // víc spárovaných PS5 (od 0.97.67): přepnout aktivní; spárovat další bez zapomenutí té první
            val all = Prefs.ps5Hosts(this)
            if (all.size > 1) add(tagged(ui.settingRow(t("mp_active"), host.name) {
                choose(t("mp_active"), all.map { it.name + " · " + it.ip }, 0) { i -> switchPs5(all[i]) }
            }, "pswitch"), 12)
            add(tagged(ui.smallButton(t("mp_pair_more")) {
                pairAnother = true; pairStep = 0; pairMsg = null; draftIp = ""; draftPin = ""; foundPs5 = emptyList(); render()
            }, "pmore"), 12)
            add(tagged(ui.button(t("ps5_forget")) {
                if (TranslatorService.running && !TranslatorService.demo) stopService(Intent(this, TranslatorService::class.java))
                // jen tahle PS5 – další spárované zůstanou a aktivní bude první z nich
                EventFile.add("PAIR|zrušeno spárování " + host.name); Prefs.forgetPs5Host(this); pairStep = 0; pairMsg = null; render(true)
            }, "pforget"), 12)
            return
        }
        // párování další PS5 jde zrušit (zůstane ta dosavadní)
        if (pairAnother && host != null) add(tagged(ui.smallButton(t("mp_cancel").replace("%s", host.name)) {
            pairAnother = false; pairMsg = null; pairStep = 0; render()
        }, "pmorex"), 12)
        // u další PS5 adresu té dosavadní nedoplňovat – krok 1 pak hledá v síti (0.97.69: číslo z další PS5 šlo té první)
        if (draftIp.isEmpty() && !pairAnother) draftIp = Prefs.ps5Ip(this)
        if (draftPsn.isEmpty()) draftPsn = Prefs.psnId(this)

        // ---- průvodce spárováním: pět malých kroků, na každé obrazovce jedna věc (sloučeno 23. 11. 2026, přání
        // majitele: „zapnout PS5“ a „najít PS5“ byly jen jedna věta na obrazovku) ----
        // Index → pořadí na obrazovce: 0 PlayStation v síti + účet pro ReCaption (1; appka hledá a dřívější účet
        // bere sama – obě věci, jak se samy ověří, jsou v krátkém seznamu s fajfkou, rozepsaná je jen ta, co
        // ještě chybí), 1–3 přihlášení tím účtem (2; 3 = záloha opsáním kódu), 4 nastavení na PS5 pod tím
        // uživatelem (3), 5 kód z PS5 (4). Účet se volí PŘED nastavením na PS5 a jeho jméno je vidět až do konce:
        // PS5 spáruje jen, když osmimístné číslo vyvolá stejný uživatel, jakým se aplikace přihlásila (jinak
        // CE-110032-7). Indexy 0–5 drží zkouška.
        val step = pairStep.coerceIn(0, 5)
        if (step != 5) pairFailed = false
        val have = Ps5.parseAccountId(draftPsn) != null
        val found = Ps5.validHost(draftIp)
        val name = accName()
        val key = when (step) { 0 -> "pg_prep"; 1 -> "pa_1"; 2 -> "pa_2"; 3 -> "pa_3"; 4 -> "pg_1"; else -> "pg_4" }
        val pos = when (step) { 0 -> 1; 1, 2, 3 -> 2; 4 -> 3; else -> 4 }
        val posTotal = 4
        // kolikátý krok spárování: v průvodci jen malý podtitul (nahoře už je „Krok 8 z 9“), jinde pruh na dílky
        if (wizard == W_PAIR) add(tagged(ui.text(t("pg_sub").replace("%1", pos.toString()).replace("%2", posTotal.toString()), 14f, Theme.MUTED), "psub"), 10)
        else add(ui.steps(pos, posTotal, steps(pos, posTotal)), 16)
        add(ui.text(t("${key}_title"), 22f, Theme.TEXT, bold = true), 2)
        // chyba spárování (i výsledek akce – nalezená PS5, nenalezená…) hned pod nadpisem kroku, ne pod textem
        // a políčky (přání majitele 22. 11. 2026); průběžné šedé hlášky („hledám…“) zůstávají u pruhu dole
        val pairResult = pairMsgColor != Theme.MUTED
        if (pairResult) pairMsg?.let { add(ui.text(it, 16f, pairMsgColor, bold = true), 8) }
        var nextOk = true
        var nextLabel = t("wz_next")
        when (step) {
            // PlayStation v síti a účet pro ReCaption na jedné obrazovce (sloučeno 23. 11. 2026): krátký seznam
            // s fajfkou, co appka ověří / zná sama, rozepsaná je jen ta věc, co ještě chybí. Uložený účet se
            // nikdy nepoužije potichu – i když je od začátku vidět s fajfkou, pokračuje se jen tlačítkem dole.
            0 -> {
                // TV: čtvereček s pomocí (jak založit 🔵 účet) v pravém sloupci vedle seznamu, text ho obtéká – dřív byl
                // pod tlačítky a na TV se k němu muselo sjet ▼ (přání majitele 24. 11. 2026); telefon má tlačítko dole
                val side = if (narrow) null else ui.vbox().also { left ->
                    val box = ui.hbox().apply { gravity = Gravity.TOP }
                    box.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    box.addView(tagged(qrSide(cz.recaption.core.HelpLinks.PS5_PREP, t("help_ps5"), 150), "phelp"),
                        LinearLayout.LayoutParams(ui.dp(190), LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = ui.dp(24) })
                    add(box, 10)
                    nests.add(left)
                }
                // kde se co dělá (telefon / PS5 / TV) – majitel: „lepší popis, kdy se kde přihlásit“
                add(ui.text(t("sp_where"), 15f, Theme.MUTED), if (side != null) 0 else 10)
                add(ui.text((if (found) "✔ " else "○ ") + t("pg_2_title"), 16f, if (found) Theme.OK else Theme.TEXT, bold = !found), 6)
                if (!found) {
                    add(ui.text(t("pg_2_text"), 15f, Theme.MUTED), 8)
                    add(input("ps5_ip", draftIp, "pip", number = true).also { IpField.setup(it) }, 10)
                    // telefon s kamerou: čtení adresy z obrazovky PS5 vždy hned pod políčkem (od 0.97.37, dřív jen po
                    // neúspěšném hledání a až pod Najít); hlavní tlačítko až když hledání nenašlo nic
                    if (LgScanActivity.available(this)) add(tagged(ui.smallButton("📷 " + t("km_scan_ps5ip")) { scanPs5Code(true) }, "pscanip"), 6)
                    add(tagged(ui.button(t("ps5_find")) { findPs5() }, "pfind"), 10)
                    // víc PS5 v síti: vybrat, kterou spárovat (už spárované označené)
                    val pairedMacs = Prefs.ps5Hosts(this).map { Ps5.hex(it.mac).lowercase() }
                    foundPs5.forEachIndexed { i, f ->
                        val known = f.id.isNotEmpty() && f.id.lowercase() in pairedMacs
                        add(tagged(ui.settingRow(f.name, f.ip + (if (known) " · " + t("mp_known") else "")) {
                            saveDrafts(); draftIp = f.ip; Prefs.setPs5Ip(this, f.ip); foundPs5 = emptyList(); pairMsg = null; render()
                        }, "pfound$i"), if (i == 0) 10 else 6)
                    }
                    // PS5 už je zapnutá: najít ji potichu, adresa se vyplní sama
                    autoFindPs5()
                }
                // legenda značek 🔵 účet pro ReCaption / 🟢 herní účet (stejné značky v dalších krocích, Nápovědě
                // i na stránce v telefonu) se ukáže, až je PlayStation nalezená a účet ještě chybí
                add(ui.text((if (have) "✔ " else if (found) "○ " else "○ ") + t("pg_acc_title"), 16f,
                    if (have) Theme.OK else if (found) Theme.TEXT else Theme.MUTED, bold = found && !have), 10)
                if (found && !have) {
                    add(ui.card().apply {
                        addView(tagged(ui.text(t("acc_legend"), 17f, Theme.TEXT, bold = true), "plegend"))
                        addView(ui.text(t("pg_acc_text"), 15f, Theme.MUTED), ui.lp(top = 8))
                    }, 10)
                    // účty ověřené v tomhle telefonu (ReCaption v telefonu jako čtečka) – se jmény, k výběru
                    Prefs.phoneAccounts(this).forEachIndexed { i, a ->
                        add(tagged(ui.button(t("pg_acc_use").replace("%s", a.name.ifEmpty { t("pg_acc_unknown") })) {
                            saveDrafts(); useAccount(a.id, a.name); pairMsg = null; render()
                        }, "pacc$i"), if (i == 0) 12 else 8)
                    }
                }
                nextLabel = if (!found) t("wz_next") else if (have) t("pg_acc_use").replace("%s", name) else t("pg_acc_login")
                if (side != null) nests.remove(side)
            }
            1 -> {
                // telefon: velký QR kód s adresou stránky, kterou televize vystavuje v domácí síti
                // (když se ji vystavit nepodařilo, vede kód rovnou na přihlášení Sony)
                val pageUrl = loginPageUrl()
                // ReCaption běží v telefonu (čtečka pro TV LG/Samsung): čtvereček by tenhle telefon nenaskenoval – tlačítko
                // otevře přihlášení Sony rovnou v pomocníkovi v tomto telefonu; účet pošle sem (od 0.97.99, přání majitele)
                val here = loginServer?.takeIf { it.running }
                val ip = NetInfo.homeAddress(this)
                if (NetInfo.isHandheld(this) && here != null && ip != null) {
                    add(ui.text(t("pa_1_phone"), 16f, Theme.TEXT), 10)
                    add(tagged(ui.button(t("pa_1_btn"), primary = true) {
                        val link = PhoneLogin.appLink(PhoneLogin.Target(ip, here.port, here.key), Prefs.uiLang(this), self = true)
                        try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link)).setPackage(packageName)) } catch (_: Exception) {}
                    }, "palogin"), 12)
                } else add(qrRow(pageUrl ?: PsnAuth.LOGIN_URL, t("pa_1_text")), 10)
                // adresa i slovy – pro přihlášení z počítače (stránka funguje v každém prohlížeči
                // ve stejné síti) a pro telefon, kterému se čtvereček nedaří přečíst
                if (pageUrl != null) {
                    // adresa na vlastním řádku, aby se nelámala uprostřed
                    val s = android.text.SpannableStringBuilder(t("pa_pc").trimEnd()).append('\n')
                    val from = s.length
                    s.append(pageUrl)
                    s.setSpan(android.text.style.ForegroundColorSpan(Theme.TEXT), from, s.length, 0)
                    s.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), from, s.length, 0)
                    add(tagged(ui.text(s, 15f, Theme.MUTED), "pcurl"), 6)
                    addWaiting(top = 8)   // TV čeká na účet z telefonu (stránka v domácí síti běží)
                }
                else add(ui.text(t("pa_nolan"), 15f, Theme.WARN), 6)
            }
            2 -> { add(ui.card().apply { addView(ui.text(t("pa_2_text"), 17f, Theme.TEXT)) }, 10); if (loginServer?.running == true) addWaiting(top = 8) }
            3 -> {
                // záloha, když se stránka z televize v telefonu neotevře (jiná Wi-Fi, mobilní data):
                // čtvereček rovnou na přihlášení Sony a kód opsaný z adresy prázdné stránky „redirect“
                if (NetInfo.isHandheld(this)) {
                    add(ui.text(t("pa_3_phone"), 16f, Theme.TEXT), 10)
                    add(tagged(ui.button(t("pa_3_btn")) {
                        try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PsnAuth.LOGIN_URL))) } catch (_: Exception) {}
                    }, "pasony"), 10)
                } else add(qrRow(PsnAuth.LOGIN_URL, t("pa_3_text")), 10)
                add(ui.text(t("pa_example"), 14f, Theme.MUTED), 8)
                // ukázka adresy se zvýrazněným kódem
                val ex = android.text.SpannableStringBuilder("…playstation.net/remoteplay/redirect?code=")
                val from = ex.length
                ex.append("v3.Ab12Cd")
                ex.setSpan(android.text.style.ForegroundColorSpan(Theme.ACCENT), from, ex.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ex.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), from, ex.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ex.append("&cid=0000…")
                add(ui.text(ex, 16f, Theme.TEXT), 2)
                add(input("ph_code", draftAuth, "pauth", number = false), 8)
                nextOk = false
            }
            // předposlední krok: na PS5 přepnout na uživatele s tímhle jménem a v Nastavení → Systém → Remote Play
            4 -> {
                accountLine(name, other = true)
                add(ui.card().apply { addView(ui.text(t("pg_1_text").replace("%s", name), 17f, Theme.TEXT)) }, 10)
            }
            else -> {
                accountLine(name, other = !pairFailed)
                add(ui.card().apply { addView(ui.text(t("pg_4_text").replace("%s", name), 17f, Theme.TEXT)) }, 10)
                // osm číslic napsaných → spárovat hned, bez hledání tlačítka (jen když PS5 nechce i kód účtu)
                add(input("ps5_pin", draftPin, "ppin", number = true).apply {
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun afterTextChanged(e: android.text.Editable?) {
                            val v = e?.toString().orEmpty()
                            if (v.count(Char::isDigit) == 8 && Ps5.parsePin(v) != null && autoPairOk()) post { if (!pairBusy) pairPs5() }
                        }
                    })
                }, 10)
                // telefon s kamerou: osmimístné číslo přečíst z obrazovky PlayStationu
                if (LgScanActivity.available(this)) add(tagged(ui.smallButton("📷 " + t("km_scan_ps5")) { scanPs5Code() }, "pscan"), 6)
                if (loginPinWanted()) add(input("ps5_login", draftLogin, "plogin", number = true), 8)
                nextOk = false
            }
        }
        if (!pairResult) pairMsg?.let { add(ui.text(it, 15f, pairMsgColor), 10) }
        // hledání PS5, ověřování účtu, párování: pruh běhá, ať je vidět, že se něco děje
        if (pairBusy) add(ui.progress(null), 8)
        val row = row()
        when {
            nextOk -> row.addView(tagged(ui.button(nextLabel, primary = true) { pairNext() }, "pnext"))
            step == 3 -> row.addView(tagged(ui.button(t(if (pairBusy) "ps5_acc_wait" else "ph_check"), primary = true) { if (!pairBusy) checkPhoneCode() }, "pnext"))
            else -> row.addView(tagged(ui.button(t(if (pairBusy) "ps5_pairing" else if (pairFailed) "pg_retry" else "ps5_pair"), primary = true) { if (!pairBusy) pairPs5() }, "pnext"))
        }
        // jiný účet: hned v přípravě místo uloženého (má-li appka účet od dřívějška), po chybě spárování hned vedle „Zkusit znovu“
        if ((step == 0 && have) || (step == 5 && pairFailed && !pairBusy)) row.addView(tagged(ui.button(t("pg_acc_other")) { otherAccount(step) }, "pother"), wrapLp())
        if (step > 0) row.addView(tagged(ui.button(t("wz_back")) {
            saveDrafts()
            pairStep = when (step) {
                5 -> 4        // z kódu zpět na nastavení PS5
                4 -> 0        // z nastavení PS5 zpět na přípravu (volba účtu je v ní, přihlášení se neopakuje)
                1 -> 0        // z přihlášení zpět na přípravu
                else -> step - 1
            }
            pairMsg = null; render()
        }, "pback"), wrapLp())
        // první krok spárování v průvodci: Zpět vede na předchozí krok průvodce (jedna řada tlačítek)
        else if (wizard == W_PAIR) row.addView(tagged(ui.button(t("wz_back")) { wizardMove(-1); render() }, "pback"), wrapLp())
        // stránka z televize se v telefonu neotevřela → rovnou na záložní krok se čtverečkem na Sony
        if (step == 1) row.addView(tagged(ui.button(t("pa_stuck")) {
            saveDrafts()
            pairStep = 3; pairMsg = null; render()
        }, "pstuck"), wrapLp())
        add(row, 14)
        // krok s přípravou: jak založit druhý účet (i do iPhonu) – pod tlačítky, ať ta zůstanou vidět
        // telefon: pomoc jako tlačítko pod tlačítky (na TV je čtvereček v pravém sloupci nahoře)
        if (step == 0 && narrow) add(helpLink(cz.recaption.core.HelpLinks.PS5_PREP, t("help_ps5")), 14)
    }

    /** Páruje se další PS5 (už jedna spárovaná je) – od 0.97.67. */
    private var pairAnother = false
    /** Hledání našlo víc PS5 → v kroku „najdi PS5“ volba, kterou spárovat. */
    private var foundPs5: List<Ps5Pairing.Found> = emptyList()

    /** Přepnout aktivní PS5; běží-li překlad, spojí se znovu s novou. */
    private fun switchPs5(h: Ps5Host) {
        if (Prefs.ps5Host(this)?.let { cz.recaption.core.Ps5Hosts.same(it, h) } == true) return
        val running = TranslatorService.running && !TranslatorService.demo
        if (running) stopService(Intent(this, TranslatorService::class.java))
        Prefs.setPs5Host(this, h); Prefs.setPs5Ip(this, h.ip)
        EventFile.add("PAIR|aktivní PS5 ${h.name} ${h.ip}")
        render(true)
        if (running) main.postDelayed({ if (!isFinishing) startFlow() }, 800)
    }

    /** Spárování se nepovedlo – nabídnout „Zkusit znovu“ a „Přihlásit jiný účet“ (CE-110032-7). */
    private var pairFailed = false

    /** Jméno ověřeného účtu PlayStation (Online ID); u účtů uložených před 0.97.28 neznámé. */
    private fun accName(): String = Prefs.psnName(this).ifEmpty { t("pg_acc_unknown") }

    /** Ověřený účet: uložit číslo i jméno najednou (jméno se ukazuje v dalších krocích a v hlášce o spárování). */
    private fun useAccount(id: String, name: String) {
        Prefs.setPsnAccount(this, id, PhoneLogin.cleanName(name))
        draftPsn = id; draftAuth = ""
    }

    /** Řádek „Ověřený účet: JMÉNO“ v krocích 4 a 5, s malým tlačítkem „Přihlásit jiný účet“. */
    private fun accountLine(name: String, other: Boolean) {
        val line = row()
        line.addView(ui.text("✔ " + t("pg_acc_done").replace("%s", name), 16f, Theme.OK, bold = true).apply { gravity = Gravity.CENTER_VERTICAL })
        if (other && !pairBusy) line.addView(tagged(ui.smallButton(t("pg_acc_other")) { otherAccount(pairStep) }, "pother"), wrapLp())
        add(line, 10)
    }

    /** Zapomene ověřený účet i jméno a vrátí na přípravu (z ní, když v telefonu žádný uložený není, rovnou na přihlášení). */
    private fun otherAccount(from: Int) {
        saveDrafts()
        draftPsn = ""; draftAuth = ""; draftPin = ""
        Prefs.setPsnAccount(this, "", "")
        pairFailed = false
        pairStep = if (from == 0 && Prefs.phoneAccounts(this).isEmpty()) 1 else 0
        pairMsg = null; render()
    }

    /** Zpráva u párování; [change] upraví rozepsané údaje (až po uložení toho, co je v políčkách). */
    private fun pairNote(msg: String?, color: Int = Theme.MUTED, busy: Boolean = false, change: (() -> Unit)? = null) {
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            saveDrafts()
            change?.invoke()
            pairMsg = msg; pairMsgColor = color; pairBusy = busy
            skipDraftSave = true
            try { render(true) } finally { skipDraftSave = false }
        }
    }

    /** Další krok průvodce spárováním (s kontrolou toho, co je v daném kroku potřeba). */
    private fun pairNext() {
        saveDrafts()
        val have = Ps5.parseAccountId(draftPsn) != null
        when (pairStep.coerceIn(0, 5)) {
            // příprava: PlayStation nalezená (adresa platná) → rovnou dál podle toho, jestli je i účet hotový
            0 -> {
                if (!Ps5.validHost(draftIp)) { pairNote(t("ps5_bad_ip"), Theme.BAD); return }
                Prefs.setPs5Ip(this, draftIp)
                pairStep = if (have) 4 else 1; pairMsg = null; render()
            }
            1 -> { pairStep = if (have) 4 else 2; pairMsg = null; render() }
            2 -> { pairStep = 3; pairMsg = null; render() }
            4 -> { pairStep = 5; pairMsg = null; render() }
        }
    }

    /** Stránka pro telefon běží jen po dobu, kdy průvodce ukazuje kroky přihlášení účtem (1 až 3). */
    private fun syncLoginPage() {
        val pairing = Prefs.console(this) == Console.PLAYSTATION && (Prefs.ps5Host(this) == null || pairAnother) && (wizard == W_PAIR || (wizard < 0 && section == Section.PS5))
        val want = pairing && pairStep.coerceIn(0, 5) in 1..3
        if (want && loginServer == null) {
            loginServer = LoginServer(
                lang = { Prefs.uiLang(this) },
                loginUrl = PsnAuth.LOGIN_URL,
                apkUrl = { UpdateCheck.url(if (Prefs.testChannel(this)) UpdateCheck.Channel.TEST else UpdateCheck.Channel.STABLE, "TitulkyPS5.apk") },
                resolveCode = { PsnAuth.accountForCode(it) },
                onAccount = { accountFromPhone(it) },
                log = { android.util.Log.i("TP_TEST", "LOGIN|$it"); DiagLog.add("LOGIN|$it") },
            ).also { it.start() }
        } else if (!want && loginServer != null) {
            loginServer?.stop(); loginServer = null
        }
        // přihlašování v telefonu trvá pár minut – televize mezitím nesmí zhasnout
        if (want) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else if (!(::xbox.isInitialized && xbox.waiting)) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Adresa stránky pro telefon (do čtverečku); null, když stránka neběží nebo televize nezná svou adresu. */
    private fun loginPageUrl(): String? {
        val s = loginServer?.takeIf { it.running } ?: return null
        val ip = NetInfo.homeAddress(this) ?: return null
        return PhoneLogin.pageUrl(PhoneLogin.Target(ip, s.port, s.key))
    }

    /**
     * Telefon poslal ověřený účet (ze stránky nebo z ReCaptionu v telefonu) → nastavení na PS5 pod tímhle
     * uživatelem (jméno je pak vidět v řádku „Ověřený účet: JMÉNO“ až do spárování).
     */
    private fun accountFromPhone(a: PhoneLogin.Account) {
        pairNote(null, Theme.OK) { useAccount(a.id, a.name); pairStep = 4 }
    }

    /** Čtvereček (QR kód) s adresou a vedle něj text; na úzké obrazovce je text pod čtverečkem. */
    private fun qrRow(url: String, text: String, size: Int = 200): LinearLayout {
        val row = if (narrow) ui.vbox() else ui.hbox().apply { gravity = Gravity.TOP }
        qrBitmap(url)?.let { qr ->
            row.addView(ImageView(this).apply { setImageBitmap(qr); setBackgroundColor(android.graphics.Color.WHITE); setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8)) },
                // velikost tak, aby se kód i s tlačítky vešel na obrazovku celý, bez posouvání
                LinearLayout.LayoutParams(ui.dp(size), ui.dp(size)))
        }
        row.addView(ui.text(text, 16f, Theme.TEXT).apply { if (narrow) setPadding(0, ui.dp(12), 0, 0) else setPadding(ui.dp(20), 0, 0, 0) },
            if (narrow) ui.lp() else LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    /** Čtvereček do úzkého pravého sloupce (TV): kód nahoře, popisek a poznámka pod ním – text stránky ho obtéká vlevo. */
    private fun qrSide(url: String, label: String, size: Int = 150): LinearLayout = ui.vbox().apply {
        qrBitmap(url)?.let { qr ->
            addView(ImageView(this@MainActivity).apply { setImageBitmap(qr); setBackgroundColor(android.graphics.Color.WHITE); setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8)) },
                LinearLayout.LayoutParams(ui.dp(size), ui.dp(size)))
        }
        addView(ui.text(label, 15f, Theme.TEXT, bold = true), ui.lp(top = 10))
        addView(ui.text(t("help_qr_note"), 13f, Theme.MUTED), ui.lp(top = 4))
    }

    /** QR kód s adresou (černobílý obrázek); null, když se ho nepodaří vytvořit. */
    private fun qrBitmap(text: String): android.graphics.Bitmap? = QrImage.bitmap(text)

    /**
     * Odkaz na stránku s pomocí ([cz.recaption.core.HelpLinks]): v telefonu tlačítko do prohlížeče,
     * na televizi čtvereček pro telefon (i iPhone).
     */
    private fun helpLink(url: String, label: String, qr: Int = 200): View {
        if (narrow) return ui.button(label) {
            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) {}
        }
        return qrRow(url, label + "\n" + t("help_qr_note"), qr)
    }

    /** Krátké vysvětlení v okně (řádek „›“ v průvodci) – ne jako text mezi řádky. */
    private fun infoDialog(title: String, text: String) {
        try {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title).setMessage(text)
                .setPositiveButton(t("ok")) { d, _ -> d.dismiss() }
                .show()
        } catch (_: Exception) {}
    }

    /** Kód opsaný z telefonu → číslo účtu (dotaz jde přímo na server Sony, ne přes přihlašovací stránku). */
    private fun checkPhoneCode() {
        saveDrafts()
        val code = Ps5.authCodeFromTyped(draftAuth)
        if (code == null) { pairNote(t("ph_bad"), Theme.BAD); return }
        pairNote(t("ps5_acc_wait"), busy = true)
        Thread({
            // číslo účtu i jméno (Online ID z odpovědi Sony o účtu)
            val acc = PsnAuth.accountForCode(code)
            if (acc != null) pairNote(null, Theme.OK) { useAccount(acc.id, acc.name); pairStep = 4 }
            else pairNote(t("ph_bad"), Theme.BAD)
        }, "tp-code").start()
    }

    private fun findPs5() {
        if (pairBusy) return
        saveDrafts()
        pairNote(t("ps5_finding"), busy = true)
        Thread({
            val list = Ps5Pairing.discover()
            val first = list.firstOrNull()
            if (first != null) {
                if (list.size == 1) Prefs.setPs5Ip(this, first.ip)
                // jediná PS5 v síti → rovnou na volbu účtu (víc PS5: adresa první, hráč může přepsat)
                // víc PS5 (od 0.97.67): žádná se nevybere sama – krok ukáže volbu, kterou spárovat
                pairNote(if (list.size > 1) t("mp_pick") else t("ps5_found") + " " + first.name + " (" + first.ip + ")", Theme.OK) {
                    ps5NotFound = false
                    foundPs5 = if (list.size > 1) list else emptyList()
                    if (list.size == 1) draftIp = first.ip
                }
            } else pairNote(t("ps5_notfound"), Theme.WARN) { ps5NotFound = true }
        }, "tp-find").start()
    }

    /** Spárovat samo (osm číslic napsaných nebo přečtených kamerou)? Jen na kroku s číslem, s účtem a bez kódu účtu PS5. */
    private fun autoPairOk() = !pairBusy && pairStep == 5 && Ps5.validHost(draftIp) &&
        Ps5.parseAccountId(draftPsn) != null && !loginPinWanted()

    /**
     * Krok 0 spárování (příprava): tiché hledání v síti, nejvýš jednou za 20 s. Najde se právě jedna PS5 → adresa se
     * sama vyplní (účet pro ReCaption jde pak hned vybrat na téže obrazovce). Nic nenajde = nic se nezmění.
     */
    private fun autoFindPs5() {
        // výsledek hledání z dřívějška (už od kroku Konzole) → rovnou dál; až po vykreslení (pairNote kreslí znovu)
        main.post { applyPs5Seen() }
        ps5Scan()
    }

    /** PS5 z posledního tichého hledání, kterou jde vzít bez ptaní (jediná v síti / na uložené adrese); null = žádná. */
    private var ps5Seen: Ps5Pairing.Found? = null
    private var ps5SeenAt = 0L
    @Volatile private var ps5Scanning = false

    /**
     * Tiché hledání PS5 má smysl: konzole PlayStation zatím nespárovaná a průvodce je mezi krokem Konzole a krokem 0
     * spárování (příprava), nebo stránka Konzole na kroku 0. Párování další PS5 ne (jediná nalezená může být ta
     * už spárovaná), po „Zpět“ na krok 0 taky ne.
     */
    private fun ps5ScanWanted(): Boolean {
        // nejdřív levné podmínky (volá se z tickeru), nastavení až potom
        if (ps5AutoFound || pairAnother || pairBusy) return false
        if (!(wizard in W_CONSOLE..W_PERM || (pairStep == 0 && (wizard == W_PAIR || (wizard < 0 && section == Section.PS5))))) return false
        return Prefs.console(this) == Console.PLAYSTATION && Prefs.ps5Host(this) == null
    }

    /** Hledání PS5 v síti na pozadí, nejvýš jednou za 20 s (jen hledání – spojení s PS5 se tím nemění). */
    private fun ps5Scan() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (ps5Scanning || (ps5AutoFindAt != 0L && now - ps5AutoFindAt < 20_000L) || !ps5ScanWanted()) return
        ps5AutoFindAt = now
        ps5Scanning = true
        val saved = Prefs.ps5Ip(this)
        Thread({
            val pick = try {
                val list = Ps5Pairing.discover(3000, quiet = true)
                // jediná konzole v síti, nebo ta na adrese, kterou hráč už dřív zadal / vybral (víc PS5 = nic samo)
                list.singleOrNull() ?: list.firstOrNull { saved.isNotEmpty() && it.ip == saved }
            } catch (_: Throwable) { null } finally { ps5Scanning = false }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                ps5Seen = pick; ps5SeenAt = android.os.SystemClock.elapsedRealtime()
                applyPs5Seen()
            }
        }, "tp-autofind").start()
    }

    /** Krok 0 spárování (příprava) a tiché hledání PS5 našlo → adresa se vyplní, na téže obrazovce jde vybrat účet. */
    private fun applyPs5Seen() {
        val one = ps5Seen ?: return
        if (isFinishing || android.os.SystemClock.elapsedRealtime() - ps5SeenAt > 60_000L) return
        if (pairBusy || ps5AutoFound || pairAnother || pairStep != 0 || Prefs.ps5Host(this) != null) return
        if (Prefs.console(this) != Console.PLAYSTATION || !(wizard == W_PAIR || (wizard < 0 && section == Section.PS5))) return
        Prefs.setPs5Ip(this, one.ip)
        ps5AutoFound = true
        EventFile.add("PAIR|PS5 nalezena sama ${one.name} ${one.ip}")
        pairNote(t("ps5_found") + " " + one.name + " (" + one.ip + ")", Theme.OK) { draftIp = one.ip; ps5NotFound = false }
    }

    /**
     * Krok Televize v průvodci (telefon jako čtečka): najít televizi LG / Samsung v síti a předvybrat ji – jen jediný
     * druh, jen když ho hráč nezvolil sám a na zvolené televizi se ReCaption ještě neohlásilo.
     */
    @Volatile private var tvFinding = false
    /** Pruh „hledá se TV“ je na obrazovce (po hledání bez výsledku se překreslí jen tehdy). */
    private var tvWaitShown = false

    private fun findTv() {
        // opakuje se nejvýš jednou za 10 s, dokud krok Televize je vidět a nic nenašlo (TV se mohla zapnout až teď)
        val now = android.os.SystemClock.elapsedRealtime()
        if (tvFinding || tvFound != null || (tvSearchAt != 0L && now - tvSearchAt < 10_000L)) return
        tvSearchAt = now
        tvFinding = true
        Thread({
            val lg = LgFinder.ssdp(this, 2000)
            val samsung = LgFinder.samsung(this, 2000)
            val type = TvDetect.suggest(lg, samsung)
            runOnUiThread {
                tvFinding = false
                if (isFinishing) return@runOnUiThread
                // nic: pruh „hledá se“ schovat, jen když byl vidět (opakované hledání každých 10 s nic nepřekresluje)
                if (type == null) { if (wizard == W_TV && tvWaitShown) { tvWaitShown = false; render(true) }; return@runOnUiThread }
                val ip = (if (type == TvType.LG) lg else samsung).first()
                val cur = Prefs.tvType(this)
                val seen = cur.platform?.let { Prefs.tvSeen(this, it) } != null
                if (!tvPicked && !seen && cur != type) TvChoice.set(this, type)
                tvFound = TvChoice.label(this, type) + " · " + ip
                if (wizard == W_TV) render(true)
            }
        }, "tp-findtv").start()
    }

    private fun pairPs5() {
        saveDrafts()
        val ip = draftIp
        if (!Ps5.validHost(ip)) { pairNote(t("ps5_bad_ip"), Theme.BAD); return }
        val pin = Ps5.parsePin(draftPin)
        if (pin == null) { pairNote(t("ps5_bad_pin"), Theme.BAD); return }
        Prefs.setPs5Ip(this, ip)
        val account = Ps5.parseAccountId(draftPsn)
        if (account == null) { pairStep = 0; pairNote(t("pg_need_acc"), Theme.BAD); return }
        val name = accName()
        pairNote(t("ps5_pairing"), busy = true)
        Thread({
            Ps5Pairing.pair(ip, account, pin) { h: Ps5Host? ->
                // do záznamu (i z minulých spuštění): kdy a jakým účtem se párovalo – opakované párování je vidět v hlášení
                EventFile.add("PAIR|" + (if (h != null) "spárováno ${h.name} ${h.ip} …${Ps5.hex(h.mac).takeLast(4)}" else "nepovedlo se ($ip)") +
                    " · účet $name" + (Ps5Pairing.lastError?.takeIf { h == null }?.let { " · $it" } ?: ""))
                if (h != null) {
                    Prefs.setPs5Host(this, h)
                    pairNote(t("ps5_ok").replace("%s", name), Theme.OK) {
                        draftPin = ""; pairStep = 0; pairFailed = false; justPaired = true; pairAnother = false; foundPs5 = emptyList()
                        // průvodce: spárováno → sám na poslední krok (Hotovo se Spustit)
                        if (wizard == W_PAIR) wizard = wizardSteps.size - 1
                    }
                } else {
                    // nejčastější příčina: číslo vyvolal na PS5 jiný uživatel než účet v aplikaci (CE-110032-7)
                    // chyba knihovny (Ps5Pairing.lastError) je v záznamu PAIR| výše, na obrazovku nepatří
                    val why = t("ps5_fail") + "\n" + t("ps5_fail_user").replace("%s", name)
                    pairNote(why, Theme.BAD) { pairFailed = true; draftPin = "" }
                }
            }
        }, "tp-pair").start()
    }

    // ======================= OBLASTI =======================

    // ======================= VZHLED =======================

    /**
     * Řádek „Překládat z“ – jazyk titulků ve hře (průvodce krok Jazyky a stránka Titulky pod jazykem překladu).
     * Nabízí jen jazyky v latince, které umí offline překladač i DeepL ([cz.recaption.core.SourceLang]).
     */
    private fun sourceRow(tag: String, top: Int) {
        val cur = Prefs.sourceLang(this)
        add(tagged(ui.settingRow(t("l_src_lang"), cur.label) {
            val all = cz.recaption.core.SourceLang.offered(Prefs.lang(this))
            choose(t("l_src_lang"), all.map { it.label }, all.indexOf(cur)) { i ->
                Prefs.setSourceLang(this, all[i])
                // jiný jazyk titulků = jiný jazykový balíček (stáhne se jednou; v režimu „Nastavuji TV“ si ho stáhne TV)
                if (!remote) OfflineTranslator.ensureModel(Prefs.lang(this)) { runOnUiThread { if (wizard >= 0 || section == Section.HOME) render(true) } }
                applyToService(); render(true)
            }
        }, tag), top)
    }

    /** Řádek s výběrem jazyka překladu (stránka Titulky; v průvodci krok Jazyk překladu). */
    private fun langRow(tag: String, top: Int) {
        add(tagged(ui.settingRow(t("l_lang"), Prefs.lang(this).label) {
            val langs = cz.recaption.core.Lang.targets
            choose(t("l_lang"), langs.map { it.label }, langs.indexOf(Prefs.lang(this))) { i ->
                Prefs.setLang(this, langs[i])
                if (!remote) OfflineTranslator.ensureModel(langs[i]) { runOnUiThread { if (wizard >= 0 || section == Section.HOME) render(true) } }
                applyToService(); render(true)
            }
        }, tag), top)
    }

    private fun gameTitle(g: cz.recaption.core.GameProfile) = if (g.id == "all") t("game_universal") else g.title

    /** Výběr profilu hry (Nastavení → Hra i řádek Hra na Domů). */
    private fun pickGame() {
        val game = GameProfiles.of(Prefs.gameId(this))
        val games = GameProfiles.ALL
        choose(t("gp_profile"), games.map { gameTitle(it) }, games.indexOf(game)) { i ->
            Prefs.setGameId(this, games[i].id); applyToService(); render(true)
        }
    }

    /**
     * Seznam her ze stránek: [manual] = OK na řádku (vždy), jinak jen automaticky se zapnutou volbou a nejvýš
     * jednou za 6 h. Po novém seznamu se službě znovu načte nastavení (slovník, postavy) a stránka překreslí.
     */
    private fun checkGameList(manual: Boolean) {
        val done: (Boolean) -> Unit = { changed ->
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (changed) applyToService()
                if (section == Section.LOOK || changed) render(true)
            }
        }
        if (manual) { GameListUpdate.check(this, done); render(true) } else GameListUpdate.maybeAuto(this, done)
    }

    /** Rozbalený seznam postav, míst a pojmů profilu hry. */
    private var gameListOpen = false

    /**
     * Hra (Nastavení → Čtení a překlad): profil hry, nabídka hry odhadnuté z titulků (nic se nepřepíná samo)
     * a rozbalovací seznam toho, co profil zná – postavy s rodem, jména a místa, pojmy, přeskakované nápisy.
     */
    private fun gameGroup() {
        val game = GameProfiles.of(Prefs.gameId(this))
        add(tagged(ui.settingRow(t("gp_profile"), gameTitle(game)) { pickGame() }, "game"), 8)
        Prefs.gameGuess(this)?.let { id ->
            val g = GameProfiles.of(id)
            add(tagged(ui.settingRow(g.title + "?", t("gp_switch") + " ›", Theme.WARN) {
                try {
                    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(g.title + "?").setMessage(String.format(t("gp_guess_desc"), g.title))
                        .setPositiveButton(t("gp_switch")) { d, _ -> d.dismiss(); Prefs.setGameId(this, g.id); applyToService(); render(true) }
                        .setNegativeButton(t("gp_keep")) { d, _ -> d.dismiss(); Prefs.dismissGameGuess(this); render(true) }
                        .show()
                } catch (_: Exception) {}
            }, "gameguess"), 8)
        }
        // rada, jak hru nastavit, aby se titulky četly (GTA VI: pozadí titulků) – OK = vysvětlení v dialogu
        game.hint?.let { h ->
            add(tagged(ui.settingRow(t(h), t(h + "_val") + " ›", Theme.MUTED) {
                try {
                    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(t(h)).setMessage(t(h + "_desc"))
                        .setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
                        .show()
                } catch (_: Exception) {}
            }, "gamehint"), 8)
        }
        // co se překládá navíc k titulkům: každý text hry má vlastní přepínač (od 0.97.59, dřív jedna volba „Rozsah čtení“),
        // od 0.97.98 zvlášť pro každou hru (Prefs → GameOptions); ukáže se jen u her, jejichž profil ten text zná.
        // Univerzální profil žádný nemá – jen šedý řádek.
        if (!cz.recaption.core.GameOptions.hasOptions(game.id)) add(ui.text(t("gp_only_subs"), 14f, Theme.MUTED), 8)
        // Titulky PS5: titulek poznaný podle pruhu hry – cedule a nápisy ve scéně se nepřekládají
        if (game.subtitleLook != null) add(tagged(ui.settingRow("Titulky jen v pruhu hry", onOff(Prefs.subtitleBox(this))) {
            if (Prefs.subtitleBox(this)) { Prefs.setSubtitleBox(this, false); applyToService(); render(true) }
            else confirmOn("Titulky jen v pruhu hry", "Titulek se pozná podle poloprůhledného pruhu, který hra kreslí pod titulky. " +
                "Nápisy ve scéně (cedule, billboardy, poznávací značky) se pak nepřekládají. Ve hře musí být zapnuté pozadí titulků; " +
                "když ho hra delší dobu nekreslí, čte se zase všechno.") { Prefs.setSubtitleBox(this, true); applyToService(); render(true) }
        }, "gamebox"), 8)
        if (game.prompts) add(tagged(ui.settingRow(t("gp_prompts"), onOff(Prefs.gamePrompts(this))) {
            Prefs.setGamePrompts(this, !Prefs.gamePrompts(this)); applyToService(); render(true)
        }, "gameprompts"), 8)
        // úkol pod titulky (GTA): zapnout, písmo, poloha, pozadí, barvy (od 0.97.45)
        if (game.prompts) {
            val on = Prefs.objectiveOn(this)
            add(tagged(ui.settingRow(t("obj_row"), onOff(on)) {
                Prefs.setObjectiveOn(this, !on); applyToService(); render(true)
            }, "objon"), 8)
            if (on) {
                val sz = Prefs.objSize(this)
                val sizes = listOf(0) + Prefs.TEXT_SIZES.toList()
                // „podle hry“ jen když i titulky jdou podle hry, jinak „jako titulky“
                val autoLabel = t(if (Prefs.matchSize(this)) "qk_size_game" else "obj_bg_same")
                add(tagged(ui.settingRow(t("obj_size"), if (sz == 0) autoLabel else sz.toString()) {
                    choose(t("obj_size"), sizes.map { if (it == 0) autoLabel else it.toString() }, sizes.indexOf(sz).coerceAtLeast(0)) { i ->
                        Prefs.setObjSize(this, sizes[i]); applyToService(); render(true)
                    }
                }, "objsize"), 8)
                val pos = Prefs.objPos(this)
                // polohy jako u pruhu (od 0.97.50) + vlastní výška 0–100 %
                val fixed = listOf(-1 to t("obj_pos_game"), -2 to t("obj_pos_above"), -5 to t("obj_pos_below"), -3 to t("obj_pos_bar"), -4 to t("pos_top"))
                fun posLabel(p: Int) = fixed.firstOrNull { it.first == p }?.second ?: (t("pos_height") + " · $p %")
                add(tagged(ui.settingRow(t("obj_pos"), posLabel(pos)) {
                    choose(t("obj_pos"), fixed.map { it.second } + t("pos_height"), fixed.indexOfFirst { it.first == pos }.let { if (it < 0) fixed.size else it }) { i ->
                        if (i < fixed.size) { Prefs.setObjPos(this, fixed[i].first); applyToService(); render(true) }
                        else {
                            val near = Prefs.HEIGHTS.minByOrNull { kotlin.math.abs(it - (if (pos >= 0) pos else 90)) } ?: 90
                            choose(t("pos_height"), Prefs.HEIGHTS.map { "$it %" }, Prefs.HEIGHTS.indexOf(near)) { k ->
                                Prefs.setObjPos(this, Prefs.HEIGHTS[k]); applyToService(); render(true)
                            }
                        }
                    }
                }, "objpos"), 8)
                // pozadí úkolu zvlášť (výchozí jako pruh s titulky)
                val bg = Prefs.objBg(this)
                val bgs = listOf(-1) + Prefs.OPACITIES.toList()
                add(tagged(ui.settingRow(t("obj_bg"), if (bg < 0) t("obj_bg_same") else "$bg %") {
                    choose(t("obj_bg"), bgs.map { if (it < 0) t("obj_bg_same") else "$it %" }, bgs.indexOf(bg).coerceAtLeast(0)) { i ->
                        Prefs.setObjBg(this, bgs[i]); applyToService(); render(true)
                    }
                }, "objbg"), 8)
                // barevná slova úkolu z obrazu (pokus, výchozí vypnuto)
                add(tagged(ui.settingRow(t("obj_colors"), onOff(Prefs.objColors(this))) {
                    Prefs.setObjColors(this, !Prefs.objColors(this)); applyToService(); render(true)
                }, "objcolors"), 8)
            }
        }
        // překlad menu hry: jen u her, které ho umí; výchozí vypnuto
        if (game.menuSpec != null) add(tagged(ui.settingRow(t("gp_menu"), onOff(Prefs.gameMenu(this))) {
            Prefs.setGameMenu(this, !Prefs.gameMenu(this)); applyToService(); render(true)
        }, "gamemenu"), 8)
        // menu vpravo dole (nápovědy tlačítek): jen u her, které ho znají; výchozí vypnuto, popis při první volbě
        if (game.cornerSpec != null) {
            val cm = Prefs.cornerMenu(this)
            val labels = listOf(t("off"), t("gp_corner_above"), t("gp_corner_over"))
            add(tagged(ui.settingRow(t("gp_corner"), labels[cm]) {
                choose(t("gp_corner"), labels, cm) { i ->
                    val set = { Prefs.setCornerMenu(this, i); applyToService(); render(true) }
                    if (i > 0 && cm == 0) confirmOn(t("gp_corner"), t("gp_corner_desc"), set) else set()
                }
            }, "gamecorner"), 8)
        }
        // pokyny u předmětů („□ OPEN DOOR“): jen u her se známými pokyny (zatím GTA VI), výchozí vypnuto, popis při zapnutí
        if (game.cornerSpec != null) add(tagged(ui.settingRow(t("gp_world"), onOff(Prefs.worldPrompts(this))) {
            if (Prefs.worldPrompts(this)) { Prefs.setWorldPrompts(this, false); applyToService(); render(true) }
            else confirmOn(t("gp_world"), t("gp_world_desc")) { Prefs.setWorldPrompts(this, true); applyToService(); render(true) }
        }, "gameworld"), 8)
        // překlad telefonu ve hře do panelu vedle něj (zatím GTA VI), výchozí vypnuto, popis při zapnutí
        if (game.cornerSpec != null) add(tagged(ui.settingRow(t("gp_phone"), onOff(Prefs.phonePanel(this))) {
            if (Prefs.phonePanel(this)) { Prefs.setPhonePanel(this, false); applyToService(); render(true) }
            else confirmOn(t("gp_phone"), t("gp_phone_desc")) { Prefs.setPhonePanel(this, true); applyToService(); render(true) }
        }, "gamephone"), 8)
        // kde překlad telefonu: panel vedle (výchozí) / přímo přes text v telefonu (od 0.97.110, přání majitele)
        if (game.cornerSpec != null && Prefs.phonePanel(this)) {
            // od 1.00 i „na volné straně obrazu“ (panel na opačné polovině než telefon, vlastní klíč – uložený `telefon_pres` platí dál)
            val place = if (Prefs.phoneOver(this)) 1 else if (Prefs.phoneFree(this)) 2 else 0
            val keys = listOf("gp_phone_side", "gp_phone_over", "gp_phone_free")
            add(tagged(ui.settingRow(t("gp_phone_place"), t(keys[place])) {
                choose(t("gp_phone_place"), keys.map { t(it) }, place) { i ->
                    Prefs.setPhoneOver(this, i == 1); Prefs.setPhoneFree(this, i == 2); applyToService(); render(true)
                }
            }, "gamephoneplace"), 8)
        }
        // tipy hry vlevo nahoře („Tip: …“): jen u her se známými tipy (zatím GTA VI), výchozí vypnuto, popis při zapnutí
        if (game.cornerSpec != null) add(tagged(ui.settingRow(t("gp_tip"), onOff(Prefs.gameTips(this))) {
            if (Prefs.gameTips(this)) { Prefs.setGameTips(this, false); applyToService(); render(true) }
            else confirmOn(t("gp_tip"), t("gp_tip_desc")) { Prefs.setGameTips(this, true); applyToService(); render(true) }
        }, "gametip"), 8)
        // seznam her ze stránek (třetí aktualizace): stav z paměti, OK = zjistit hned
        val glVer = GameProfiles.catalog.version
        val glState = GameListUpdate.state
        add(tagged(ui.settingRow(t("gl_row"), when (glState) {
            GameListUpdate.State.CHECKING -> t("gl_busy")
            GameListUpdate.State.UPDATED -> String.format(t("gl_new"), glVer)
            GameListUpdate.State.CURRENT -> String.format(t("gl_current"), glVer)
            GameListUpdate.State.FAILED -> String.format(t("gl_fail"), glVer)
            GameListUpdate.State.IDLE -> String.format(t("gl_ver"), glVer)
        }, Theme.MUTED) { checkGameList(manual = true) }, "gamelistver"), 8)
        if (glState == GameListUpdate.State.CHECKING) addWaiting(indent = 12, top = 2)
        add(tagged(ui.settingRow(t("gp_list"), if (gameListOpen) "▲" else "▼") { gameListOpen = !gameListOpen; toggled("gamelist", gameListOpen); render(true) }, "gamelist"), 8)
        if (!gameListOpen) return
        openNest("gamelist", inner = true)
        add(ui.text(t("t_game_desc") + " " + t("t_game_deepl"), 14f, Theme.MUTED), 8)
        if (game.id == "all") {
            add(ui.text(String.format(t("gp_universal"), game.names.size, game.characters.size), 15f), 8)
            return
        }
        fun block(head: String, items: List<String>) {
            if (items.isEmpty()) return
            add(ui.text(head + " (" + items.size + ")", 16f, Theme.ACCENT, bold = true), 12)
            add(ui.text(items.joinToString(" · "), 15f), 4)
        }
        block(t("gp_chars"), game.characters.map { c ->
            c.name + (if (c.gender == cz.recaption.core.Gender.F) " ♀" else " ♂") +
                (c.aliases.filter { !c.name.contains(it) }.takeIf { it.isNotEmpty() }?.joinToString(", ", " (", ")") ?: "")
        })
        val inCast = game.characters.flatMap { it.allNames }.toSet()
        block(t("gp_names"), game.names.filter { it !in inCast })
        block(t("gp_terms"), game.termsFor(Prefs.lang(this)).map { (en, tr) -> "$en → $tr" })
        block(t("gp_skip"), game.hud + game.menu)
    }

    /** Zvolení tlačítka rychlé nabídky vzhledu: dialog čeká na stisk tlačítka na ovladači. */
    private fun learnQuickKey() {
        try {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(t("qk_row")).setMessage(t("qk_press"))
                .setNegativeButton(t("wz_back")) { d, _ -> d.dismiss() }
                .setNeutralButton(t("off")) { d, _ -> d.dismiss(); Prefs.setQuickKey(this, 0); TitulkyAccessibility.instance?.updateKeyFilter(false); render(true) }
                .setOnKeyListener { d, code, ev ->
                    // šipky, OK a Zpět ovládají dialog samotný; ostatní zakázaná tlačítka jen upozorní
                    if (code in QuickLook.FORBIDDEN) {
                        val nav = code == android.view.KeyEvent.KEYCODE_BACK || code == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                            code == android.view.KeyEvent.KEYCODE_ENTER || code in 19..22
                        if (!nav && ev.action == android.view.KeyEvent.ACTION_UP) toast(t("qk_bad"))
                        return@setOnKeyListener !nav
                    }
                    if (ev.action == android.view.KeyEvent.ACTION_UP) {
                        Prefs.setQuickKey(this, code); TitulkyAccessibility.instance?.updateKeyFilter(true); d.dismiss(); render(true)
                    }
                    true
                }
                .show()
        } catch (_: Exception) {}
    }

    /** Otevřené rozbalovací skupiny v Nastavení (klíč skupiny). */
    private val openGroups = HashSet<String>()

    /**
     * Rozbalovací skupina po vzoru Titulků a Nápovědy: řádek s ▼/▲, obsah jen když je otevřená.
     * Vrací true, když je otevřená (volající pak přidá obsah).
     */
    private fun group(id: String, title: String, top: Int = 8): Boolean {
        val open = id in openGroups
        closeNests()
        add(tagged(ui.settingRow(title, if (open) "▲" else "▼") {
            if (!openGroups.remove(id)) openGroups.add(id)
            toggled("grp_$id", id in openGroups); render(true)
        }, "grp_$id"), top)
        if (open) openNest("grp_$id")
        return open
    }

    /** Titulky: otevřená skupina (-1 = žádná, [LOOK_DEEPL] = Kvalita překladu). */
    private var lookOpen = -1
    private val LOOK_DEEPL = 100

    /**
     * Stránka Titulky (od 0.97.1) po vzoru Nápovědy: rozbalovací skupiny Jazyk a písmo / Pruh s překladem /
     * Oblasti titulků a dole obnovení vzhledu. Žádné vysvětlující texty mezi řádky – popis volby se ukáže
     * jen v dialogu, když ji hráč zapíná ([confirmOn]); podrobnosti jsou v Nápovědě.
     */
    private fun subtitlesPage() {
        langRow("lang", 16)
        sourceRow("srclang", 8)
        // Kvalita překladu (DeepL) hned pod jazykem překladu (od 0.97.28, dřív Nastavení → Experimentální)
        val dlOpen = lookOpen == LOOK_DEEPL
        add(tagged(ui.settingRow(t("wz_tr"), t(if (DeeplTranslator.active(this)) "dl_mode_on" else "h_offline") + if (dlOpen) "  ▲" else "  ▼") {
            lookOpen = if (dlOpen) -1 else LOOK_DEEPL; toggled("lkdl", !dlOpen); render(true)
        }, "lkdl"), 8)
        if (dlOpen) { openNest("lkdl"); deeplSection().build(target()); closeNests() }
        // hra hned pod jazykem (od 0.97.54, přání majitele): profil hry je vidět dřív než obecné volby, které doplňuje
        val groups = listOf<Pair<String, () -> Unit>>(
            "t_game" to { gameGroup() },
            "lk_text" to { lookText() },
            "lk_bar" to { lookBar() },
            "m_zones" to { lookZones() },
        )
        groups.forEachIndexed { i, (key, build) ->
            val open = lookOpen == i
            closeNests()
            add(tagged(ui.settingRow(t(key), if (open) "▲" else "▼") { lookOpen = if (open) -1 else i; toggled("lk$i", !open); render(true) }, "lk$i"), if (i == 0) 14 else 8)
            if (open) { openNest("lk$i"); build(); closeNests() }
        }
        add(tagged(ui.settingRow(t("l_reset"), "↺", Theme.MUTED) {
            // výchozí hodnoty jako u nové instalace (od 1.00 podle majitele: písmo 14, pruh 60 %, těsně nad titulkem)
            Prefs.setTextSize(this, Prefs.DEFAULT_TEXT_SIZE); Prefs.setOpacity(this, Prefs.DEFAULT_OPACITY); Prefs.setTextColorIndex(this, 0)
            Prefs.setBoldText(this, false); Prefs.setHoldMs(this, 1000)
            Prefs.setLayout(this, Prefs.layout(this).withPosition(Prefs.DEFAULT_LAYOUT.position).withBarWidth(0.84f))
            applyToService(); render(true); showDemo()
        }, "lreset"), 20)
    }

    /** Volba s popisem: popis se ukáže v dialogu jen při zapínání; vypnutí je hned. */
    private fun confirmOn(title: String, desc: String, turnOn: () -> Unit) {
        try {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title).setMessage(desc)
                .setPositiveButton(t("on")) { d, _ -> d.dismiss(); turnOn() }
                .setNegativeButton(t("wz_back")) { d, _ -> d.dismiss() }
                .show()
        } catch (_: Exception) { turnOn() }
    }

    /** Jazyk a písmo: jazyk překladu, velikost, barva, tučně, originál, barvy jmen. */
    private fun lookText() {
        val sizeIdx = Prefs.TEXT_SIZES.indexOf(Prefs.textSize(this)).let { if (it < 0) Prefs.TEXT_SIZES.indexOf(Prefs.DEFAULT_TEXT_SIZE) else it }
        add(tagged(ui.settingRow(t("l_size_auto"), onOff(Prefs.matchSize(this))) {
            Prefs.setMatchSize(this, !Prefs.matchSize(this)); applyToService(); render(true)
        }, "sizeauto"), 6)
        // pevná velikost se nabízí jen tehdy, když se písmo neřídí originálem
        if (!Prefs.matchSize(this)) add(tagged(ui.settingRow(t("l_size"), Prefs.TEXT_SIZES[sizeIdx].toString()) {
            choose(t("l_size"), Prefs.TEXT_SIZES.map { it.toString() }, sizeIdx) { i ->
                Prefs.setTextSize(this, Prefs.TEXT_SIZES[i]); applyToService(); render(true); showDemo()
            }
        }, "size"), 6)
        val colorIdx = Prefs.textColorIndex(this)
        add(tagged(ui.settingRow(t("l_color"), t("color_$colorIdx"), Prefs.TEXT_COLORS[colorIdx]) {
            choose(t("l_color"), Prefs.TEXT_COLORS.indices.map { t("color_$it") }, colorIdx) { i ->
                Prefs.setTextColorIndex(this, i); applyToService(); render(true); showDemo()
            }
        }, "color"), 6)
        add(tagged(ui.settingRow(t("l_bold"), onOff(Prefs.boldText(this))) {
            Prefs.setBoldText(this, !Prefs.boldText(this)); applyToService(); render(true); showDemo()
        }, "bold"), 6)
        add(tagged(ui.settingRow(t("l_original"), onOff(Prefs.showOriginal(this))) {
            Prefs.setShowOriginal(this, !Prefs.showOriginal(this)); applyToService(); render(true); showDemo()
        }, "original"), 6)
        // velikost originálu jen se zapnutým originálem (od 0.97.44)
        if (Prefs.showOriginal(this)) {
            val os = Prefs.origSp(this)
            val sizes = listOf(0) + Prefs.TEXT_SIZES.toList()
            add(tagged(ui.settingRow(t("l_orig_size"), if (os == 0) t("orig_auto") else os.toString()) {
                choose(t("l_orig_size"), sizes.map { if (it == 0) t("orig_auto") else it.toString() }, sizes.indexOf(os).coerceAtLeast(0)) { i ->
                    Prefs.setOrigSp(this, sizes[i]); applyToService(); render(true); showDemo()
                }
            }, "origsize"), 6)
        }
        // profil hry má barvy postav (jako ve hře, např. jedna postava žlutě, druhá tyrkysově) – je vidět, odkud barvy jsou
        val profColors = Prefs.coloredSpeaker(this) && GameProfiles.of(Prefs.gameId(this)).colors.isNotEmpty()
        add(tagged(ui.settingRow(t("l_speaker"), onOff(Prefs.coloredSpeaker(this)) + if (profColors) " · " + t("gp_mark") else "") {
            Prefs.setColoredSpeaker(this, !Prefs.coloredSpeaker(this)); applyToService(); render(true); showDemo()
        }, "speaker"), 6)
    }

    /** Pruh s překladem: poloha, šířka, průhlednost, jak dlouho zůstane (u LG/Samsungu pro kterou televizi). */
    private fun lookBar() {
        // LG / Samsung: vzhled a oblasti jsou zvlášť pro každou televizi (TvProfile)
        TvProfile.activeName(this)?.let { add(ui.text(t("tv_profile_for").replace("%s", it), 14f, Theme.MUTED), 6) }
        val lay = Prefs.layout(this)
        val custom = lay.position == cz.recaption.core.ScreenLayout.Position.CUSTOM
        val customPct = Math.round(lay.barCenterY * 100)
        add(tagged(ui.settingRow(t("l_pos"), if (custom) t("pos_height") + " · $customPct %" else t("pos_" + lay.position.name.lowercase())) {
            // pevné polohy + „Vlastní výška“ (střed pruhu 0–100 % výšky, od 0.97.45); jemně dál táhnutím v Jemném doladění
            val all = cz.recaption.core.ScreenLayout.Position.values().filter { it != cz.recaption.core.ScreenLayout.Position.CUSTOM }
            choose(t("l_pos"), all.map { t("pos_" + it.name.lowercase()) } + t("pos_height"), if (custom) all.size else all.indexOf(lay.position)) { i ->
                if (i < all.size) { Prefs.setLayout(this, lay.withPosition(all[i])); applyToService(); render(true); showDemo() }
                else {
                    val near = Prefs.HEIGHTS.minByOrNull { kotlin.math.abs(it - (if (custom) customPct else 85)) } ?: 85
                    choose(t("pos_height"), Prefs.HEIGHTS.map { "$it %" }, Prefs.HEIGHTS.indexOf(near)) { k ->
                        Prefs.setLayout(this, lay.withBarCenter(Prefs.HEIGHTS[k] / 100f)); applyToService(); render(true); showDemo()
                    }
                }
            }
        }, "pos"), 6)
        // „Přes titulky“: pruh přes celé pole, kde titulky naposledy byly (i dvouřádkové), ne jen přes právě přečtený řádek
        if (lay.position == cz.recaption.core.ScreenLayout.Position.OVER) add(tagged(ui.settingRow(t("l_cover_all"), onOff(Prefs.coverAll(this))) {
            if (Prefs.coverAll(this)) { Prefs.setCoverAll(this, false); applyToService(); render(true) }
            else confirmOn(t("l_cover_all"), t("l_cover_all_desc")) { Prefs.setCoverAll(this, true); applyToService(); render(true) }
        }, "coverall"), 6)
        val widthPct = Math.round(lay.barWidth * 100)
        add(tagged(ui.settingRow(t("l_width"), "$widthPct %") {
            choose(t("l_width"), Prefs.BAR_WIDTHS.map { "$it %" }, Prefs.BAR_WIDTHS.indexOf(widthPct)) { i ->
                Prefs.setLayout(this, lay.withBarWidth(Prefs.BAR_WIDTHS[i] / 100f)); applyToService(); render(true); showDemo()
            }
        }, "width"), 6)
        add(tagged(ui.settingRow(t("l_opacity"), "${Prefs.opacity(this)} %") {
            choose(t("l_opacity"), Prefs.OPACITIES.map { "$it %" }, Prefs.OPACITIES.indexOf(Prefs.opacity(this))) { i ->
                Prefs.setOpacity(this, Prefs.OPACITIES[i]); applyToService(); render(true); showDemo()
            }
        }, "opacity"), 6)
        // okraj pruhu nad a pod textem (0–3): jak moc pruh přesahuje písmo
        add(tagged(ui.settingRow(t("l_pad"), Prefs.barPad(this).toString()) {
            choose(t("l_pad"), (0..3).map { it.toString() }, Prefs.barPad(this)) { i ->
                Prefs.setBarPad(this, i); applyToService(); render(true); showDemo()
            }
        }, "barpad"), 6)
        val hold = Prefs.holdMs(this)
        add(tagged(ui.settingRow(t("l_hold"), if (hold == 0) t("off") else "+${hold / 1000} s") {
            choose(t("l_hold"), Prefs.HOLD_TIMES.map { if (it == 0) t("off") else "+${it / 1000} s" }, Prefs.HOLD_TIMES.indexOf(hold)) { i ->
                Prefs.setHoldMs(this, Prefs.HOLD_TIMES[i]); applyToService(); render(true)
            }
        }, "hold"), 6)
        // nejkratší doba titulku: rychlé repliky za sebou čekají ve frontě, ať jdou přečíst (od 0.97.46)
        val minShow = Prefs.minShowMs(this)
        fun secs(ms: Int) = if (ms == 0) t("off") else (if (ms % 1000 == 0) "${ms / 1000}" else "%.1f".format(ms / 1000f)) + " s"
        add(tagged(ui.settingRow(t("l_min_show"), secs(minShow)) {
            choose(t("l_min_show"), Prefs.MIN_SHOW_TIMES.map { secs(it) }, Prefs.MIN_SHOW_TIMES.indexOf(minShow)) { i ->
                Prefs.setMinShowMs(this, Prefs.MIN_SHOW_TIMES[i]); applyToService(); render(true)
            }
        }, "minshow"), 6)
    }

    /** Oblasti titulků: automatika (popis v dialogu) a obnovení. */
    private fun lookZones() {
        add(tagged(ui.settingRow(t("z_auto"), onOff(Prefs.autoZone(this))) {
            if (Prefs.autoZone(this)) { Prefs.setAutoZone(this, false); applyToService(); render(true) }
            else confirmOn(t("z_auto"), t("z_auto_desc")) { Prefs.setAutoZone(this, true); applyToService(); render(true) }
        }, "zauto"), 6)
        // „Číst jen titulky“, ruční oblasti nad snímkem a Jemné doladění polohy od 0.97.97 nejsou (přání majitele: na telefonu
        // nešly ovládat prstem a nikdo je nepoužil; výška pruhu je v Poloha → Vlastní výška). Uložené hodnoty platí dál,
        // „Obnovit výchozí oblasti“ vrátí i automatiku a čtení jen titulků.
        add(tagged(ui.settingRow(t("z_reset"), "↺", Theme.MUTED) {
            Prefs.setLayout(this, Prefs.DEFAULT_LAYOUT); Prefs.setAutoZone(this, true); Prefs.setOnlySubtitles(this, true); applyToService()
            toast(t("z_reset_done")); render(true)
        }, "zreset"), 6)
    }

    // ======================= PŘEKLAD =======================

    private fun translate() {
        val lang = Prefs.lang(this)
        val ready = OfflineTranslator.isReady(lang)
        // překlad: DeepL online + offline / jen offline; DeepL se nastavuje v Titulkách → Kvalita překladu
        val deepl = DeeplTranslator.active(this)
        // jazyk, který DeepL neumí (Deepl.target == null), je prostě offline – ne „zapnuto bez klíče“
        val idle = !deepl && DeeplKey.enabled(this) && cz.recaption.core.Deepl.target(lang) != null
        add(tagged(ui.settingRow(t("t_mode"), t(if (deepl) "dl_mode_on" else if (idle) "dl_mode_idle" else "h_offline"),
            if (idle) Theme.WARN else Theme.ACCENT) {
            openQuality()
        }, "tmode"), 8)
        // jazykový balíček pro offline překlad: chybí → OK ho stáhne (v režimu „Nastavuji TV“ ho řeší TV)
        // stažený = jen stav (nejde kliknout), chybí = řádek s akcí Stáhnout
        if (!remote) add(tagged(if (ready) ui.infoRow(t("chk_model") + " · " + lang.label, t("ok"), Theme.OK)
            else ui.settingRow(t("chk_model") + " · " + lang.label, t("download"), Theme.WARN) { downloadModel() }, "dl"), 8)

        // sbírání replik ze hry patří k překladu (přání majitele 23. 11. 2026: výchozí zapnuto, dřív v Experimentální)
        linesRow()

        // statistika a historie – bez rozbalovacích skupin (přání majitele 23. 11. 2026), jen malý nadpis
        add(ui.section(t("t_stats")), 0)
        val s = TranslatorService.statsFor(this)
        add(ui.infoRow(t("t_lines"), s.lines.toString()), 0)
        add(ui.infoRow(t("t_hits"), s.cacheHits.toString()), 8)
        add(tagged(ui.settingRow(t("m_history"), "›") { section = Section.HISTORY; render() }, "hist"), 8)
        add(tagged(ui.settingRow(t("t_reset_stats"), "↺", Theme.MUTED) {
            s.reset(); Prefs.setStats(this, s); toast(t("t_cleared")); render(true)
        }, "rstats"), 8)
        add(tagged(ui.settingRow(t("t_clear_memory"), "↺", Theme.MUTED) { clearMemory() }, "clrmem"), 8)

        // vlastní slovník – málo používaný, proto úplně dole
        val dict = Prefs.userDict(this, lang)
        add(ui.section(t("t_dict") + if (dict.isNotEmpty()) " · ${dict.size}" else ""), 0)
        fun field(hintKey: String, value: String, tagName: String) = EditText(this).apply {
            setText(value)
            hint = t(hintKey)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(Theme.TEXT)
            setHintTextColor(Theme.MUTED)
            textSize = 16f
            background = ui.focusBg(Theme.CARD)
            setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12))
            tag = tagName
        }
        val en = field("t_dict_en", draftEn, "dicten")
        val tr = field("t_dict_tr", draftTr, "dicttr")
        add(en, 0); add(tr, 8)
        add(tagged(ui.settingRow(t("t_dict_add"), "+") {
            val k = en.text.toString().trim(); val v = tr.text.toString().trim()
            if (k.length >= 2 && v.isNotEmpty()) {
                dict[k] = v; Prefs.setUserDict(this, lang, dict)
                draftEn = ""; draftTr = ""; applyToService()
            } else { draftEn = k; draftTr = v }
            render(true)
        }, "dictadd"), 8)
        dict.entries.toList().forEachIndexed { i, e ->
            add(tagged(ui.settingRow(e.key, e.value + "   ✕", Theme.TEXT) {
                dict.remove(e.key); Prefs.setUserDict(this, lang, dict); applyToService(); render(true)
            }, "dict$i"), 8)
        }
    }

    // ======================= NASTAVENÍ =======================

    /** Záložky Nastavení (⚙) v tomto pořadí; [key] = název záložky. */
    private enum class SettingsTab(val key: String) { GENERAL("st_general"), READ("st_read"), EXPERIMENTAL("dl_section") }

    private var settingsTab = SettingsTab.GENERAL

    /**
     * Nastavení (⚙, vidět pořád v rohu): nahoře záložky, pod nimi jen obsah vybrané záložky – ať se
     * nemusí dlouho posouvat. Dlouhé popisy voleb jsou v Nápovědě (část help_settings), tady jen názvy.
     * Licence a platby jsou zatím skryté; ukážou se jen tehdy, když zkušební doba skončila (jinak by
     * hráč nevěděl, proč aplikace nejde spustit).
     */
    private fun settings() {
        val lic = Prefs.license(this)
        if (!lic.canRun) { content.addView(ui.section(t("m_license"))); license() }

        val tabs = ui.hbox()
        var activeTab: View? = null
        for (tab in SettingsTab.values()) {
            if (sideNavOn()) break   // na široké obrazovce jsou záložky v menu vlevo
            val active = tab == settingsTab
            tabs.addView(ui.text(t(tab.key), 16f, if (active) Theme.BG else Theme.TEXT, bold = true).apply {
                isFocusable = true
                isClickable = true
                background = ui.focusBg(if (active) Theme.ACCENT else Theme.CARD, radius = 20)
                ui.pop(this)
                setPadding(ui.dp(18), ui.dp(9), ui.dp(18), ui.dp(9))
                // jiná záložka = obsah od začátku (hned pod záložkami, ty jsou připnuté nahoře)
                setOnClickListener { settingsTab = tab; render(keepFocus = true); pageScroll?.post { pageScroll?.scrollTo(0, 0) } }
                tag = "stab_${tab.name}"
                if (active) activeTab = this
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = ui.dp(8) })
        }
        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(tabs) }
        if (!sideNavOn()) pinned.addView(tabScroll, ui.lp())
        // vybraná záložka celá na obrazovce (řada se překresluje, takže by jinak začínala vždy zleva)
        scrollTabIntoView(tabScroll, activeTab, smooth = false)

        when (settingsTab) {
            SettingsTab.GENERAL -> settingsGeneral()
            SettingsTab.READ -> { settingsRead(); translate() }
            SettingsTab.EXPERIMENTAL -> experimentalSection()
        }
    }

    /** Obecné v pořadí průvodce: jazyk aplikace, zařízení, konzole, TV (+ povolení u Android TV), telefon (baterie, displej), nastavení z telefonu. */
    private fun settingsGeneral() {
        // jazyk aplikace je nezávislý na jazyku překladu (ten je na stránce Titulky)
        val uiLang = Prefs.uiLang(this)
        add(tagged(ui.settingRow(t("l_ui_lang"), uiLang.label) {
            val all = cz.recaption.core.Lang.uiLangs
            choose(t("l_ui_lang"), all.map { it.label }, all.indexOf(uiLang)) { i ->
                Prefs.setUiLang(this, all[i]); applyToService(); buildShell(); render()
            }
        }, "uilang"), 8)
        val reader = Prefs.readerDevice(this)
        if (!remote) add(tagged(ui.settingRow(t("wz_dev"), t(if (reader) "dev_phone" else "dev_tv")) {
            choose(t("wz_dev_q"), listOf(t("dev_tv"), t("dev_phone")), if (reader) 1 else 0) { i -> setReader(i == 1); render(true) }
        }, "device"), 8)
        consoleRow("sconsole", 8)
        // Titulky PS5: „Překlad z PC“ – TV jen ukazuje titulky z ReCaptionu pro PC (Čtečka pro TV)
        if (!remote && !Prefs.readerDevice(this)) add(tagged(ui.settingRow("Překlad z PC", onOff(Prefs.pcMode(this))) {
            if (Prefs.pcMode(this)) { PcReceiverService.stop(this); Prefs.setPcMode(this, false); render(true) }
            else confirmOn("Překlad z PC", "Titulky čte a překládá program Titulky PS5 na PC ve stejné domácí síti. " +
                "Tato TV jen ukazuje hotové titulky. Spustit na Domů zapne čtení i na PC (program na PC musí běžet, " +
                "PC ze spánku probudí, když to PC dovolí). Zastavit ho na PC zase vypne.") {
                if (TranslatorService.running) stopTranslation()
                Prefs.setPcMode(this, true); render(true)
            }
        }, "pcmode_set"), 8)
        // probouzení PlayStationu: zapne ho na spárovaném (druhém) účtu, proto výchozí vypnuto
        if (Prefs.console(this) == Console.PLAYSTATION) {
            add(tagged(ui.settingRow(t("ps_wake_row"), onOff(Prefs.ps5Wake(this))) {
                if (Prefs.ps5Wake(this)) { Prefs.setPs5Wake(this, false); render(true) }
                else confirmOn(t("ps_wake_row"), t("ps_wake_desc")) { Prefs.setPs5Wake(this, true); render(true) }
            }, "ps5wake"), 8)
        }
        // Typ televize (Android TV / LG / Samsung) – stejná nabídka jako ze souhrnného řádku na Domů.
        // V režimu „Nastavuji TV“ je to vždy Android TV; zařízení, baterie a povolení pruhu patří TV (schované).
        val type = Prefs.tvType(this)
        if (!remote) add(tagged(ui.settingRow(t("tv_type"), TvChoice.label(this, type)) {
            TvChoice.show(this) { render(true) }
        }, "tvtype"), 8)
        // TV LG: prodlužovat vývojářský režim u služby LG (výchozí vypnuto, popis v dialogu a v Nápovědě)
        if (type == TvType.LG && !remote) add(tagged(ui.settingRow(t("lg_renew"), onOff(Prefs.lgDevRenew(this))) {
            if (Prefs.lgDevRenew(this)) { Prefs.setLgDevRenew(this, false); render(true) }
            else confirmOn(t("lg_renew"), t("lg_renew_desc")) {
                Prefs.setLgDevRenew(this, true); render(true)
                LgDevMode.renew(this) { runOnUiThread { if (!isFinishing) render(true) } }
            }
        }, "lgrenew"), 8)
        // povolení pruhu potřebuje jen Android TV (u LG/Samsungu ukazuje titulky televize)
        if (type == TvType.ANDROID && remote) {
            // stav přes hru se přenáší do TV; Usnadnění, tlačítko ovladače a překrytí se nastavují v TV
            val sm = Prefs.statusMode(this)
            val modes = listOf(t("off"), t("so_dyn"), t("so_always"))
            add(tagged(ui.settingRow(t("so_row"), modes[sm]) {
                choose(t("so_row"), modes, sm) { i -> Prefs.setStatusMode(this, i); render(true) }
            }, "statusov"), 8)
        } else if (type == TvType.ANDROID) {
            val a11yOn = TitulkyAccessibility.instance != null
            add(tagged(ui.settingRow(t("s_a11y"), onOff(a11yOn), if (a11yOn) Theme.OK else Theme.WARN) {
                openAccessibility()
            }, "a11y"), 8)
            val ovOn = Settings.canDrawOverlays(this)
            // „zobrazení přes jiné aplikace“ je jen záloha, když Usnadnění neběží
            if (!a11yOn) add(tagged(ui.settingRow(t("s_overlay"), onOff(ovOn), if (ovOn) Theme.OK else Theme.MUTED) {
                openOverlaySettings()
            }, "overlay"), 8)
            // tlačítko ovladače pro rychlou nabídku vzhledu při hře (QuickLook, jen Android TV)
            val qk = Prefs.quickKey(this)
            // tlačítka chytá služba Usnadnění – bez ní volba nefunguje (od 0.97.54 schovaná)
            if (a11yOn) add(tagged(ui.settingRow(t("qk_row"), if (qk == 0) t("qk_none") else QuickLook.keyName(qk), if (qk == 0) Theme.MUTED else Theme.ACCENT) {
                learnQuickKey()
            }, "quickkey"), 8)
            // stav vlevo nahoře přes hru (StatusPanel): vypnuto (výchozí) / dynamicky / vždy
            val sm = Prefs.statusMode(this)
            val modes = listOf(t("off"), t("so_dyn"), t("so_always"))
            add(tagged(ui.settingRow(t("so_row"), modes[sm]) {
                choose(t("so_row"), modes, sm) { i -> Prefs.setStatusMode(this, i); render(true) }
            }, "statusov"), 8)
        }
        // telefon jako čtečka: běh na pozadí (šetření baterie) – stav a cesta k povolení
        if (!Prefs.isTv(this) && !remote) {
            val bg = batteryUnrestricted()
            add(tagged(ui.settingRow(t("h_battery_fix"), onOff(bg), if (bg) Theme.OK else Theme.WARN) { askBatteryUnrestricted() }, "battery"), 8)
            // telefon Samsung má navíc vlastní uspávání aplikací – řádek otevře rovnou seznam „Nikdy neuspávané aplikace“
            if (NetInfo.isSamsungPhone(this)) add(tagged(ui.settingRow(t("h_never_sleep"), "›") { openSamsungNeverSleep() }, "neversleep"), 8)
        }
        // telefon jako čtečka: displej při překladu nezhasne (ztlumený) – jinak některé telefony zpomalí čtení obrazu
        if (NetInfo.isHandheld(this) && !remote) {
            add(tagged(ui.settingRow(t("dm_row"), onOff(Prefs.screenDim(this))) {
                if (Prefs.screenDim(this)) { Prefs.setScreenDim(this, false); TranslatorService.applyScreenDim(this); render(true) }
                else confirmOn(t("dm_row"), t("dm_desc")) { Prefs.setScreenDim(this, true); TranslatorService.applyScreenDim(this); render(true) }
            }, "scrdim"), 8)
        }
        // nastavení z telefonu: čtvereček na stránku z TV (ReCaption v telefonu pak nastaví celou aplikaci)
        if (RemoteHost.eligible(this)) remotePhoneRow("remote")
        // telefon, na kterém běží celá aplikace: cesta zpět k pomocníkovi pro přihlášení
        if (NetInfo.isHandheld(this)) add(tagged(ui.settingRow(t("hp_back"), "›") { backToPhoneHelper() }, "tophone"), 8)
        // dole u sebe: nová verze a hlášení (přání majitele 23. 11. 2026, dřív v Experimentální → Ladění a hlášení)
        updateRow(18)
        // hlášení vývojáři přes internet, výsledkem je kód (ReportSender; tester může přidat dávku snímků)
        add(tagged(ui.settingRow(t("snd_btn"), "›") { ReportSender.open(this, ui) }, "rsend"), 8)
        add(tagged(ui.settingRow(t("rep_btn"), "›") { makeReport() }, "sreport"), 8)
        // od 1.0.05 (přání majitele: „jak můžu aplikaci zavřít? Zavřít všechny na Samsungu ji nechává otevřenou“) – jen
        // telefon a jen když překlad neběží (při překladu ne – to se zastavuje tlačítkem Zastavit)
        if (NetInfo.isHandheld(this) && !Prefs.isTv(this) && !TranslatorService.running)
            add(tagged(ui.settingRow(t("exit_app"), "⏻") { quitApp() }, "quit"), 18)

        if (SHOW_LICENSE && Prefs.license(this).canRun) { content.addView(ui.section(t("m_license"))); license() }
    }

    /**
     * Ukončit aplikaci úplně: zastaví server pro nastavení z telefonu, zavře okna a ukončí proces (jinak ho na Samsungu
     * drží záznam v síti nebo jiné vlákno a „Zavřít vše“ ho nechá běžet). Při běžícím překladu se neukazuje.
     */
    private fun quitApp() {
        if (TranslatorService.running) return
        try { RemoteHost.stop() } catch (_: Throwable) {}
        finishAndRemoveTask()
        android.os.Handler(mainLooper).postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 300)
    }

    /** Čtvereček „Nastavit z telefonu“ rozbalený (⚙ → Obecné, stránka Konzole). */
    private var remoteQr = false

    /** Řádek „Nastavit z telefonu“ s rozbalovacím čtverečkem na stránku z TV ([RemoteHost], [cz.recaption.core.SetupPage]). */
    private fun remotePhoneRow(tag: String) {
        add(tagged(ui.settingRow(t("rs_row"), if (remoteQr) "▲" else "▼") { remoteQr = !remoteQr; render(true) }, tag), 8)
        if (remoteQr) {
            val url = if (RemoteHost.start(this)) RemoteHost.pageUrl(this) else null
            if (url != null) add(qrRow(url, t("rs_qr_text")), 8) else add(ui.text(t("rs_nolan"), 15f, Theme.WARN), 8)
        }
    }

    /** Čtení a překlad: kvalita čtení (nad tím, co dělá [translate]). */
    private fun settingsRead() {
        val q = Prefs.readQuality(this)
        val qKeys = listOf("auto", "std", "high")
        // přesnost čtení = rozlišení obrazu z PlayStationu; Xbox ji nepoužívá
        if (Prefs.console(this) == Console.PLAYSTATION) add(tagged(ui.settingRow(t("s_quality"), t("q_$q")) {
            choose(t("s_quality"), qKeys.map { t("q_$it") }, qKeys.indexOf(q)) { i ->
                Prefs.setReadQuality(this, qKeys[i])
                // jiné rozlišení obrazu z konzole = spojení je potřeba navázat znovu
                if (TranslatorService.running) { stopService(Intent(this, TranslatorService::class.java)); toast(t("q_restart")) }
                render(true)
            }
        }, "quality"), 8)
        // čtení jen pásu s textem (od 0.97.142 už ne pokus – přání majitele), výchozí vypnuto; co to stojí, řekne dialog při zapnutí
        add(tagged(ui.settingRow(t("fr_row"), onOff(Prefs.fastRead(this))) {
            if (Prefs.fastRead(this)) { Prefs.setFastRead(this, false); applyToService(); render(true) }
            else confirmOn(t("fr_row"), t("fr_desc")) { Prefs.setFastRead(this, true); applyToService(); render(true) }
        }, "fastread"), 8)
    }

    /**
     * Snímky ze hry pro ladění (od 1.00-rc19 pro všechny v Experimentální, přání majitele – dřív jen testeři; všechno výchozí
     * vypnuto) a jen pro testery řádek Zkušební verze. Pořadí od sběru k odeslání: snímky → snímky na Domů → uložit do telefonu.
     */
    private fun testerRows() {
        // vypnutí = konec testování (Tester.set(false)), řádek pak zmizí; nová instalace testerského APK ho zapne znovu
        if (Tester.active(this)) add(tagged(ui.settingRow(t("s_test"), t("on")) {
            Prefs.setTestChannel(this, false); checkForUpdate(); render(true)
        }, "testch"), 18)
        snapRow()
        // zkratka na Domů: Start/Stop snímků a Odeslat poslední dávku rovnou s kódem (přání majitele 22. 11. 2026)
        add(tagged(ui.settingRow(t("snd_home_opt"), onOff(Prefs.snapHome(this))) {
            if (Prefs.snapHome(this)) { Prefs.setSnapHome(this, false); render(true) }
            else confirmOn(t("snd_home_opt"), t("snd_home_desc")) { Prefs.setSnapHome(this, true); render(true) }
        }, "snaphome"), 8)
        add(tagged(ui.settingRow(t("snap_row"), "›") { SnapSaver.open(this) }, "snapsave"), 8)
    }

    /**
     * „Záznam snímků“ (od 0.97.59, pro ladění, výchozí vypnuto): Start/Stop dávky snímků obrazovky hry s tím,
     * co aplikace četla (SnapRecorder). Vývojář dávky stáhne přes záznam v domácí síti (ci/stahni-davky.ps1).
     */
    private fun snapRow() {
        add(tagged(ui.settingRow(t("sn_row"), snapValue()) {
            if (!SnapRecorder.toggle(this)) Toast.makeText(this, t("sn_need_run"), Toast.LENGTH_LONG).show()
            render(true)
        }, "snimky"), 8)
        // souhrn uložených dávek se počítá na pozadí (procházení složek), po spočítání se stránka překreslí
        val saved = SnapRecorder.summary
        if (saved == null) SnapRecorder.refreshSummary(this) { if (section == Section.SETTINGS) render(keepFocus = true) }
        else if (saved.first > 0) add(ui.text(t("sn_saved").replace("%b", saved.first.toString())
            .replace("%c", saved.second.toString())
            .replace("%m", ((saved.third + 524_287) / 1_048_576).toString()), 14f, Theme.MUTED), 6)
        // smazat dávky z telefonu (od 0.97.104, přání majitele) – jen když nějaké jsou, s potvrzením
        if (saved != null && saved.first > 0) add(tagged(ui.settingRow(t("snap_del"), "›") {
            try {
                android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setMessage(t("snap_del_q").replace("%b", saved.first.toString()).replace("%m", ((saved.third + 524_287) / 1_048_576).toString()))
                    .setPositiveButton(t("snap_del")) { _, _ ->
                        Thread({
                            try { SnapRecorder.delete(this) } catch (_: Throwable) {}
                            runOnUiThread { if (!isFinishing && section == Section.SETTINGS) render(keepFocus = true) }
                        }, "tp-smazat-snimky").apply { isDaemon = true; start() }
                    }
                    .setNegativeButton(t("lg_cancel"), null)
                    .show()
            } catch (_: Exception) {}
        }, "snapdel"), 8)
    }

    private fun snapValue(): String = if (SnapRecorder.active)
        t("sn_on").replace("%n", SnapRecorder.batch.toString()).replace("%c", SnapRecorder.count.toString()) else t("off")

    /** Ticker: počet snímků v řádku Záznam snímků bez překreslení stránky; zapnutí/vypnutí (i samo po 10 min) překreslí. */
    private var lastSnap = false
    private fun tickSnap(typing: Boolean) {
        val on = SnapRecorder.active
        if (on != lastSnap) {
            lastSnap = on
            if ((section == Section.SETTINGS || (section == Section.HOME && Prefs.snapHome(this))) && !typing) render(keepFocus = true)
        } else if (on) {
            (window.decorView.findViewWithTag<View>("snimky") as? LinearLayout)?.let { row ->
                (row.getChildAt(row.childCount - 1) as? android.widget.TextView)?.text = snapValue()
            }
            // nadpis Snímky na Domů: počet snímků bez překreslení stránky
            (window.decorView.findViewWithTag<View>("hsnap") as? LinearLayout)?.let { row ->
                (row.getChildAt(row.childCount - 1) as? android.widget.TextView)?.text = t("snd_home_rec").replace("%c", SnapRecorder.count.toString())
            }
        }
    }

    /**
     * „Zpřístupnit záznam v domácí síti“ pro vývojáře (výchozí vypnuto, samo se vypne), viz NetLogShare.
     * Vlastní blok v části Experimentální pod DeepL.
     */
    private fun logShareRow() = add(NetLogShare.row(this, ui) { render(true) }, 8)

    // ======================= EXPERIMENTÁLNÍ =======================

    /** DeepL (Titulky → Kvalita překladu a krok průvodce Lepší překlad) – jedna instance, ať drží rozepsaný klíč. */
    private var deeplUi: DeeplSection? = null
    private fun deeplSection(): DeeplSection =
        deeplUi ?: DeeplSection(this, ui, { applyToService() }, { render(true) }) {
            // průvodce: klíč přijatý a ověřený → po chvíli (✔ je vidět) sám další krok
            if (wizard == W_TRANSLATE) main.postDelayed({ if (!isFinishing && wizard == W_TRANSLATE) { wizardMove(1); render() } }, 1500)
        }.also { deeplUi = it }

    /** Titulky → Kvalita překladu s otevřenou skupinou (z řádku jazyka na Domů, Nastavení i Nápovědy). */
    private fun openQuality() {
        wizard = -1; section = Section.LOOK; lookOpen = LOOK_DEEPL; render()
    }

    /**
     * Experimentální (od 1.00 jen málo, přání majitele 23. 11. 2026): pokus Jen nové repliky, diagnostika a záznam v síti;
     * nová verze a hlášení jsou v Obecné, seznam her se stahuje vždy sám. Snímky ze hry pro všechny, tester navíc zkušební verze ([testerRows]).
     */
    private fun experimentalSection() {
        // pokus: z bloku replik pod sebou (rozhovory lidí kolem) jen nové repliky, výchozí vypnuto
        add(tagged(ui.settingRow(t("nr_row"), onOff(Prefs.onlyNewLines(this))) {
            if (Prefs.onlyNewLines(this)) { Prefs.setOnlyNewLines(this, false); applyToService(); render(true) }
            else confirmOn(t("nr_row"), t("nr_desc")) { Prefs.setOnlyNewLines(this, true); applyToService(); render(true) }
        }, "onlynew"), 8)
        add(tagged(ui.settingRow(t("s_debug"), onOff(Prefs.debug(this))) {
            Prefs.setDebug(this, !Prefs.debug(this)); applyToService(); render(true)
        }, "debug"), 8)
        sporicGroup()
        // záznam pro vývojáře v domácí síti (samo se vypne po 2 h)
        logShareRow()
        testerRows()
    }

    /**
     * Vypnutí spořiče LG/Samsung - zkušebně (od 1.5.60, přání majitele: víc nápadů najednou, ať podle SPORIC|/TICHO|
     * v hlášeních poznáme, co na které TV pomáhá). Jen LG/Samsung - Android TV vlastní spořič neřeší appka, ale
     * systém. Luna služba (tvpower) výchozí zapnuto - dřív běžela vždy, tohle jen dovolí ji vypnout; video
     * (nehybné / s měnícím se pixelem) výchozí vypnuto - podle LG dokumentace musí být opravdu vidět, aby se počítalo
     * jako "fullscreen video", což appka jako průhledný přehled přes hru nesplní (viz CLAUDE.md, Televize LG).
     */
    private fun sporicGroup() {
        val type = Prefs.tvType(this)
        if (type != TvType.LG && type != TvType.SAMSUNG) return
        if (!group("sporic", t("sp_group"))) return
        add(tagged(ui.settingRow(t("sp_luna"), onOff(Prefs.sporicLuna(this))) {
            Prefs.setSporicLuna(this, !Prefs.sporicLuna(this)); applyToService(); render(true)
        }, "sporicluna"), 8)
        if (type == TvType.LG) {
            val sv = Prefs.sporicVideo(this)
            val modes = listOf(t("off"), t("sp_video_1"), t("sp_video_2"))
            add(tagged(ui.settingRow(t("sp_video"), modes[sv]) {
                choose(t("sp_video"), modes, sv) { i -> Prefs.setSporicVideo(this, i); applyToService(); render(true) }
            }, "sporicvideo"), 8)
        }
    }

    /**
     * „Sbírání replik ze hry“ (od 0.97.139; od 1.00 výchozí zapnuto bez dotazu – přání majitele, kdo ho vypnul nebo odmítl,
     * má dál vypnuto, viz [Prefs.collectLines]). Vypnuté → OK zapne (co se posílá, je v Nápovědě, `ln_help`);
     * zapnuté → dialog s počty, Odeslat teď a Vypnout a smazat. Sběr a odesílání: LineCollector (návrh navrhy/preklad-cele-hry.md).
     */
    private fun linesRow() {
        val on = Prefs.collectLines(this)
        val value = when {
            !on -> t("off")
            Prefs.gameId(this) == GameProfiles.UNIVERSAL.id -> t("ln_no_game")
            else -> LineCollector.summary(this) { runOnUiThread { if (!isFinishing && section == Section.SETTINGS) render(keepFocus = true) } }
                ?.let { t("ln_on").replace("%n", it.first.toString()) } ?: t("on")
        }
        add(tagged(ui.settingRow(t("ln_row"), value) {
            if (on) linesDialog() else { Prefs.setCollectLines(this, true); applyToService(); render(true) }
        }, "lines"), 8)
    }

    private fun linesDialog() {
        val sum = LineCollector.summary(this) ?: (0 to 0)
        val msg = t("ln_status").replace("%n", sum.first.toString()).replace("%p", sum.second.toString()) +
            (LineCollector.lastResult?.takeIf { it.isNotEmpty() }?.let { "\n\n" + t(it) } ?: "")
        try {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(t("ln_row")).setMessage(msg)
                .setPositiveButton(t("ln_send")) { d, _ ->
                    d.dismiss()
                    if (sum.second == 0) toast(t("ln_nothing"))
                    else LineCollector.sendNow(this) { err ->
                        if (!isFinishing) { toast(if (err == null) t("ln_sent") else t(err)); if (section == Section.SETTINGS) render(keepFocus = true) }
                    }
                }
                .setNegativeButton(t("ln_off")) { d, _ -> d.dismiss(); LineCollector.turnOff(this); applyToService(); render(true) }
                .setNeutralButton(t("dg_close")) { d, _ -> d.dismiss() }
                .show()
        } catch (_: Exception) {}
    }

    /**
     * „Podpořit ReCaption ☕“ (řádek dole na Domů, od ReCaptionu 1.00; dřív v ⚙ → Experimentální): krátká adresa
     * `HelpLinks.SUPPORT` (pomoc/kafe.html přesměruje na stránku s příspěvkem – měnit jde bez nového APK).
     * Telefon ji otevře v prohlížeči, TV / box ukáže čtvereček v okně.
     */
    /** Odkaz ven: v telefonu prohlížeč, na TV okno se čtverečkem pro telefon. */
    private fun openLink(url: String, title: String) {
        if (narrow) { try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) {}; return }
        try {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(qrRow(url, t("help_qr_note")).apply { setPadding(ui.dp(24), ui.dp(12), ui.dp(24), 0) })
                .setPositiveButton(t("ok")) { d, _ -> d.dismiss() }
                .show()
        } catch (_: Exception) {}
    }

    private fun openSupport() {
        val url = cz.recaption.core.HelpLinks.SUPPORT
        if (narrow) { try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) {}; return }
        try {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(t("sup_row"))
                .setView(qrRow(url, t("sup_qr")).apply { setPadding(ui.dp(24), ui.dp(12), ui.dp(24), 0) })
                .setPositiveButton(t("ok")) { d, _ -> d.dismiss() }
                .show()
        } catch (_: Exception) {}
    }

    // ======================= HISTORIE =======================

    private fun history() {
        val items = SubtitleHistory.all()
        // zpět do Nastavení (v telefonu i systémovým Zpět – onBackPressed), přání majitele
        val top = ui.hbox()
        top.addView(tagged(ui.button("← " + t("wz_back")) { section = Section.SETTINGS; render() }, "hback"))
        top.addView(View(this), LinearLayout.LayoutParams(ui.dp(12), 1))
        top.addView(tagged(ui.button(t("h_clear")) { SubtitleHistory.clear(); render(true) }, "hclear"))
        add(top, 16)
        if (items.isEmpty()) {
            add(ui.text(t("h_empty"), 16f, Theme.MUTED), 16); return
        }
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        items.take(100).forEachIndexed { i, it ->
            // jen informace: v telefonu nejde vybrat (nevypadá jako tlačítko), na TV ano – ovladač jinak seznamem neposune
            val c = ui.card().apply { if (!narrow) { isFocusable = true; background = ui.focusBg(Theme.CARD) }; tag = "h$i" }
            c.addView(ui.text(fmt.format(Date(it.time)), 12f, Theme.MUTED))
            c.addView(ui.text(it.translated, 18f, Theme.TEXT), ui.lp(top = 4))
            c.addView(ui.text(it.original, 14f, Theme.MUTED), ui.lp(top = 4))
            add(c, 10)
        }
    }

    // ======================= LICENCE =======================

    private fun license() {
        val lic = Prefs.license(this)
        val card = ui.card()
        val (title, sub, color) = when (lic.kind) {
            cz.recaption.core.License.Kind.UNLOCKED -> Triple(t("lic_unlocked"), t("lic_unlocked_sub"), Theme.OK)
            cz.recaption.core.License.Kind.TRIAL ->
                Triple(t("lic_trial").replace("%d", lic.daysLeft.toString()), t("lic_trial_sub"), Theme.ACCENT)
            cz.recaption.core.License.Kind.EXPIRED -> Triple(t("lic_expired"), t("lic_expired_sub"), Theme.BAD)
        }
        card.addView(ui.text(title, 22f, color, bold = true))
        card.addView(ui.text(sub, 16f, Theme.MUTED), ui.lp(top = 6))
        add(card, 0)
        if (lic.kind == cz.recaption.core.License.Kind.UNLOCKED) return

        content.addView(ui.section(t("lic_pay")))
        add(ui.card().apply { addView(ui.text(t("lic_pay_soon"), 16f, Theme.MUTED)) }, 0)

        content.addView(ui.section(t("lic_code")))
        // špatný kód hned pod nadpisem, ne pod políčkem a tlačítkem (přání majitele 22. 11. 2026)
        if (codeError) { add(ui.text(t("lic_bad"), 16f, Theme.BAD, bold = true), 0); codeError = false }
        val code = EditText(this).apply {
            setText(draftCode)
            hint = t("lic_code_hint")
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(Theme.TEXT)
            setHintTextColor(Theme.MUTED)
            textSize = 18f
            background = ui.focusBg(Theme.CARD)
            setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(14))
            tag = "code"
        }
        add(code, 0)
        add(tagged(ui.button(t("lic_enter"), primary = true) {
            draftCode = code.text.toString()
            if (cz.recaption.core.License.checkCode(draftCode)) {
                Prefs.setUnlocked(this, true); draftCode = ""
                toast(t("lic_ok")); render()
            } else {
                codeError = true; render(true)
            }
        }, "codeok"), 12)
    }

    // ======================= NÁPOVĚDA =======================

    /** Nápověda: otevřená kapitola (-1 = žádná) a televize, pro kterou se ukazuje (null = zvolená). */
    private var helpOpen = -1
    private var helpTv: TvType? = null

    /**
     * Nápověda jako postup od začátku (od 0.97): rejstřík kapitol v pořadí, jak člověk postupuje.
     * Kapitola se po OK rozbalí; obsah je jen pro vybranou televizi (nahoře „Nápověda pro“, výchozí
     * zvolená) a zvolenou konzoli. ✔ / ○ podle skutečného stavu – jen pro zvolenou televizi.
     */
    private fun help() {
        val chosen = Prefs.tvType(this)
        val tv = helpTv ?: chosen
        val mine = tv == chosen
        val ps = Prefs.console(this) == Console.PLAYSTATION
        val types = TvType.values().toList()
        add(tagged(ui.settingRow(t("help_for"), TvChoice.label(this, tv)) {
            choose(t("help_for"), types.map { TvChoice.label(this, it) }, types.indexOf(tv)) { i -> helpTv = types[i]; render(true) }
        }, "helpfor"), 12)

        fun body(vararg keys: String) = ui.card().apply {
            keys.forEachIndexed { i, k -> addView(ui.text(t(k), 16f, if (i == 0) Theme.TEXT else Theme.MUTED), ui.lp(top = if (i == 0) 0 else 10)) }
        }
        fun LinearLayout.button(label: String, tag: String, onClick: () -> Unit) =
            addView(tagged(ui.smallButton(label) { onClick() }, tag), ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT, top = 12))
        // stránka s návodem: v telefonu tlačítko do prohlížeče, na televizi čtvereček pro telefon
        fun LinearLayout.page(url: String, tag: String) {
            if (!Prefs.isTv(this@MainActivity)) button(t("help_page"), tag) {
                try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) {}
            } else addView(qrRow(url, t("help_page") + "\n" + t("help_qr_note")), ui.lp(top = 12))
        }

        // průvodce nastavením nahoře (od 1.00-rc20, přání majitele: Instalace je přehled, průvodce jde vždy spustit znovu)
        add(tagged(ui.button(t("wz_again"), primary = true) { wizard = 0; render() }, "wagain"), 12)
        // krátké video k části průvodce podle zvolené TV a konzole (dřív záložka Videonávody)
        VideoStepList.load(this) { runOnUiThread { if (!isFinishing && section == Section.HELP && wizard < 0) render(keepFocus = true) } }
        val vTv = when (chosen) { TvType.LG -> "lg"; TvType.SAMSUNG -> "samsung"; TvType.ANDROID -> "android" }
        val vCon = if (ps) "ps" else "xbox"
        fun LinearLayout.video(groupKey: String) {
            val g = wizardGroups.indexOfFirst { it.first == groupKey }
            if (g < 0 || !mine) return
            cz.recaption.core.VideoSteps.pick(g, vTv, vCon, VideoStepList.clips)?.let { id ->
                addView(tagged(ui.settingRow("▶  " + t("vs_row"), VideoStepList.clips[id] ?: "") { VideoGuideActivity.open(this@MainActivity, id) }, "hvid_$groupKey"), ui.lp(top = 12))
            }
        }

        val chapters = mutableListOf<Triple<String, Boolean?, () -> View>>()
        // 1. instalace
        chapters += Triple("hc1", true, {
            body(if (tv == TvType.ANDROID) "hc1_android" else "hc1_reader", "inst_text").apply {
                page(if (tv == TvType.ANDROID) cz.recaption.core.HelpLinks.ANDROID_TV else cz.recaption.core.HelpLinks.PHONE, "hpage1")
            }
        })
        // 2. příprava konzole
        chapters += Triple("hc2", Prefs.consoleReady(this), {
            (if (ps) body("hc2_ps", "rp_hide", "ps5_rest") else body("hc2_xbox")).apply {
                page(if (ps) cz.recaption.core.HelpLinks.PS5_PREP else cz.recaption.core.HelpLinks.XBOX, "hpage2")
                video("c_console")
            }
        })
        // 3. televize
        chapters += Triple("hc3", if (!mine) null else when (tv) {
            TvType.ANDROID -> SubtitleOverlay.canShow(this)
            else -> TvChoice.upToDate(this, tv) != null
        }, {
            when (tv) {
                TvType.ANDROID -> body("hc3_android").apply {
                    addView(ui.text("adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow", 14f, Theme.ACCENT), ui.lp(top = 8))
                    if (mine) button(t("fix"), "hfix3") { settingsTab = SettingsTab.GENERAL; section = Section.SETTINGS; render() }
                }
                TvType.LG -> body("hc3_lg").apply {
                    button(t("lg_open"), "lghelp") { startActivity(Intent(this@MainActivity, LgInstallActivity::class.java)) }
                    page(cz.recaption.core.HelpLinks.LG_ACCOUNT, "hpage3")
                    video("wz_g_dev")
                }
                TvType.SAMSUNG -> body("sa_help", "sa_tips").apply {   // sa_tips: rady k vývojářskému režimu, instalaci a Anynet+ (1.0.x)
                    button(t("ss_open"), "sshelp") { startActivity(Intent(this@MainActivity, SamsungInstallActivity::class.java)) }
                    page(cz.recaption.core.HelpLinks.SAMSUNG, "hpage3")
                    video("wz_g_dev")
                }
            }
        })
        // 4. jazyk a kvalita překladu
        chapters += Triple("hc4", OfflineTranslator.isReady(Prefs.lang(this)), {
            body("hc4_text").apply {
                button(t("m_look") + " → " + t("wz_tr"), "hdeepl") { openQuality() }
                video("wz_g_tr")
            }
        })
        // 5. spárování konzole
        chapters += Triple("hc5", Prefs.consoleReady(this), {
            // u Xboxu vlastní text (účet Microsoft, microsoft.com/link) – dřív se ukazoval postup pro PS5
            body(if (ps) "hc5_text" else "hc5_xbox").apply { button(ct("m_ps5"), "hpair") { section = Section.PS5; render() }; video("wz_g_conn") }
        })
        // 6. hraní
        // + rady k nastavení her z profilů (GTA VI: pozadí titulků) – pro všechny hry, i když je vybraný univerzální profil
        chapters += Triple("hc6", null, {
            body(*(listOf(if (tv == TvType.ANDROID) "hc6_android" else "hc6_tv") + GameProfiles.GAMES.mapNotNull { g -> g.hint?.let { it + "_desc" } }).toTypedArray())
        })
        // 7. když něco nejde
        chapters += Triple("hc7", null, {
            ui.card().apply {
                val items = listOfNotNull("hc7_qr", "hc7_ps5".takeIf { ps }, "hc7_net",
                    "hc7_battery".takeIf { tv != TvType.ANDROID }, "hc7_screen".takeIf { tv != TvType.ANDROID }, "hc7_other".takeIf { ps }, "hc7_read")
                items.forEachIndexed { i, k -> addView(ui.text("•  " + t(k), 16f, Theme.TEXT), ui.lp(top = if (i == 0) 0 else 10)) }
                button(t("rep_btn"), "report") { makeReport() }
            }
        })
        // 8. volby v Nastavení (v Nastavení jsou jen názvy)
        chapters += Triple("hc8", null, {
            ui.card().apply {
                // v pořadí Nastavení: Obecné, Titulky, Čtení a překlad, Experimentální (zkušební verze jen pro testery)
                listOfNotNull("ps_wake_row" to "ps_wake_desc", "qk_row" to "qk_desc", "so_row" to "so_desc", "dm_row" to "dm_desc", "lg_renew" to "lg_renew_desc",
                    "t_game" to "t_game_desc", "gp_corner" to "gp_corner_desc", "gp_world" to "gp_world_desc", "gp_phone" to "gp_phone_desc", "l_cover_all" to "l_cover_all_desc",
                    "s_quality" to "q_desc", "fr_row" to "fr_desc", "t_mode" to "t_offline_desc", "ln_row" to "ln_help", "t_dict" to "t_dict_desc",
                    "nr_row" to "nr_desc", ("s_test" to "s_test_desc").takeIf { Tester.active(this@MainActivity) }).forEachIndexed { i, (k, d) ->
                    addView(ui.text(t(k), 16f, Theme.TEXT, bold = true), ui.lp(top = if (i == 0) 0 else 14))
                    addView(ui.text(t(d), 15f, Theme.MUTED), ui.lp(top = 4))
                }
                // anonymní počítání uživatelů (UsagePing) – bez volby, jen co se posílá
                addView(ui.text(t("anon_desc"), 15f, Theme.MUTED), ui.lp(top = 14))
            }
        })

        // 9. víc TV nebo konzolí (od 1.00 – i z kroku Hotovo průvodce)
        chapters += Triple("hc9", null, { body("hc9_text") })

        chapters.forEachIndexed { i, (key, done, make) ->
            val open = helpOpen == i
            val mark = when (done) { true -> "✔  "; false -> "○  "; null -> "" }
            // šipka: zeleně hotovo, žlutě ještě udělat, šedě kapitola bez stavu (Hraní, Když něco nejde…)
            add(tagged(ui.settingRow(mark + t(key), if (open) "▲" else "▼", when (done) { true -> Theme.OK; false -> Theme.ACCENT; null -> Theme.MUTED }) {
                helpOpen = if (open) -1 else i; toggled("hc$i", !open); render(true)
            }, "hc$i"), if (i == 0) 16 else 8)
            if (open) { openNest("hc$i"); add(make(), 6); closeNests() }
        }
        add(ui.text("Titulky PS5 · ${BuildInfo.VERSION}", 13f, Theme.MUTED), 24)
    }

    // ======================= KONZOLE (PlayStation / Xbox) =======================

    /** Stránka konzole pro Xbox (přihlášení, výběr Xboxu); stav drží mezi překresleními. */
    private lateinit var xbox: XboxForm

    private fun initXbox() {
        if (!::xbox.isInitialized) xbox = XboxForm(this, ui, { v, top -> add(v, top) }, { row() }, { wrapLp() },
            { url, text -> qrRow(url, text) }, { render(keepFocus = true) }, onReady = {
                // průvodce: Xbox vybraný → sám na Hotovo (jako po spárování PS5); překreslí rerender hned potom
                if (wizard == W_PAIR && Prefs.consoleReady(this)) wizard = wizardSteps.size - 1
            })
    }

    /** Text podle vybrané konzole: u Xboxu „xb_<klíč>“, pokud existuje (core/I18nXbox.kt). */
    private fun ct(key: String) = t(Prefs.console(this).key(key))

    /** „Krok 3 z 8“. */
    private fun steps(current: Int, total: Int) = t("steps_of").replace("%1", current.toString()).replace("%2", total.toString())

    /** Jméno spárovaného PlayStationu / vybraného Xboxu, nebo null. */
    private fun consoleName(): String? = when (Prefs.console(this)) {
        Console.PLAYSTATION -> Prefs.ps5Host(this)?.name
        Console.XBOX -> if (XboxAccount.ready(this)) XboxAccount.consoleName(this) else null
    }

    /**
     * Řádek „Konzole: Xbox Series X|S / One · Obývák“ (zeleně, když je připravená); kliknutím se
     * otevře výběr ([chooseConsole]). V Nastavení; na Domů je konzole v souhrnném řádku.
     */
    private fun consoleRow(tag: String, top: Int) {
        val c = Prefs.console(this)
        val value = t(if (c == Console.XBOX) "c_xbox" else "c_ps") + consoleName()?.let { " · $it" }.orEmpty()
        add(tagged(ui.settingRow(t("c_console"), value, if (Prefs.consoleReady(this)) Theme.OK else Theme.WARN) { chooseConsole() }, tag), top)
    }

    /**
     * Výběr konzole (PlayStation 5 / Xbox) v nabídce se seznamem. Po změně se zastaví překlad,
     * který běží s druhou konzolí, a (mimo průvodce) otevře se stránka konzole, když ještě není
     * nastavená. [after] = co udělat po výběru místo překreslení (např. krok průvodce).
     */
    private fun chooseConsole(after: (() -> Unit)? = null) {
        val all = listOf(Console.PLAYSTATION, Console.XBOX)
        val cur = Prefs.console(this)
        choose(t("c_console"), all.map { t(if (it == Console.XBOX) "c_xbox" else "c_ps") }, all.indexOf(cur)) { i ->
            setConsole(all[i])
            if (after != null) after() else render(true)
        }
    }

    /**
     * Uloží vybranou konzoli (nabídka i volba v průvodci). Překlad, který běží s druhou konzolí,
     * se zastaví; mimo průvodce se otevře stránka konzole, když ještě není nastavená.
     */
    private fun setConsole(pick: Console) {
        if (pick == Prefs.console(this)) return
        Prefs.setConsole(this, pick)
        if (TranslatorService.running && !TranslatorService.demo) stopService(Intent(this, TranslatorService::class.java))
        if (wizard < 0 && !Prefs.consoleReady(this)) section = Section.PS5
    }

    // ======================= akce =======================

    /** Nabídka se seznamem možností (místo opakovaného mačkání OK). Vybraná položka je označená. */
    private fun choose(title: String, labels: List<String>, current: Int, onPick: (Int) -> Unit) {
        try {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setSingleChoiceItems(labels.toTypedArray(), current.coerceAtLeast(0)) { d, i -> d.dismiss(); onPick(i) }
                .show()
        } catch (e: Exception) {
            // nouzově: další možnost v pořadí
            onPick((current + 1).coerceAtLeast(0) % labels.size)
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    /** Pošle běžícímu překladu nové nastavení. */
    private fun applyToService() {
        if (TranslatorService.running) {
            startService(Intent(this, TranslatorService::class.java).setAction(TranslatorService.ACTION_RELOAD))
        }
    }

    private fun startFlow() {
        if (remote) { section = Section.HOME; render(); return }   // překládá TV, ne telefon, který ji nastavuje
        if (!Prefs.license(this).canRun) { section = Section.SETTINGS; render(); return }
        if (TranslatorService.running && !TranslatorService.demo) { moveTaskToBack(true); return }
        if (!SubtitleOverlay.canShow(this) && !Prefs.tvSend(this)) { toast(t("need_overlay")); section = Section.SETTINGS; render(); return }
        if (!Prefs.consoleReady(this)) { section = Section.PS5; render(); return }
        // telefon jako čtečka: jednou se zeptat na běh na pozadí (jinak ho šetření baterie se zhasnutým
        // displejem uspí a spojení s konzolí vypadává); dál jen v ⚙ Nastavení → Obecné, ne na Domů
        val once = getSharedPreferences("ui_jednou", MODE_PRIVATE)
        var asked = false
        if (!Prefs.isTv(this) && !batteryUnrestricted()) {
            if (!once.getBoolean("baterie", false)) { once.edit().putBoolean("baterie", true).apply(); askBatteryUnrestricted(); asked = true }
        }
        // telefon jako čtečka: jednou nabídnout „Nezhasínat displej telefonu“ (od 0.97.105, přání majitele – se zhasnutým
        // displejem Samsung čte titulky až 10× pomaleji, tester NTA7-97BA); ne zároveň s dotazem na běh na pozadí (pak při dalším Spustit)
        if (!asked && NetInfo.isHandheld(this) && !Prefs.screenDim(this)) {
            if (!once.getBoolean("displej", false)) {
                once.edit().putBoolean("displej", true).apply()
                confirmOn(t("dm_row"), t("dm_desc")) { Prefs.setScreenDim(this, true); TranslatorService.applyScreenDim(this); render(true) }
                asked = true
            }
        }
        // sbírání replik se od 1.00 na nic neptá – výchozí zapnuto (Prefs.collectLines), vypnout jde v ⚙ → Čtení a překlad
        busyText = t("prep_model")
        OfflineTranslator.ensureModel(Prefs.lang(this)) { ok ->
            runOnUiThread {
                if (!ok) {
                    busyText = t("model_fail"); render(true); busyText = null
                } else {
                    busyText = null
                    launchService(null)
                }
            }
        }
        if (busyText != null) render(true)   // až po spuštění stahování, ať je vidět pruh
    }

    /** Zkouška bez PlayStationu: ukázková hra jde do aplikace stejnou cestou jako video z konzole. */
    private fun startDemo() {
        if (remote) return
        if (!Prefs.license(this).canRun) { section = Section.SETTINGS; render(); return }
        if (!SubtitleOverlay.canShow(this) && !Prefs.tvSend(this)) { toast(t("need_overlay")); section = Section.SETTINGS; render(); return }
        OfflineTranslator.ensureModel(Prefs.lang(this)) { }
        launchService(TranslatorService.ACTION_START_DEMO)
        main.postDelayed({
            if (TranslatorService.running) startActivity(Intent(this, TestCardActivity::class.java))
        }, 1200)
    }

    private fun launchService(action: String?) {
        try {
            startForegroundService(Intent(this, TranslatorService::class.java).setAction(action))
        } catch (e: Exception) {
            // technická příčina jen do záznamu; stav na Domů ukáže, co chybí
            EventFile.add("START|nejde spustit: $e"); render(true); return
        }
        main.postDelayed({
            val err = TranslatorService.startError
            if (!TranslatorService.running && err != null) {
                EventFile.add("START|nespuštěno: $err")
                // licence / nespárovaná konzole ukazuje už stav na Domů; na obrazovku jen srozumitelná věta o obrazu
                busyText = if (err.startsWith("video")) t("err_video") else null
                render(true); busyText = null
            } else render(true)
        }, 1000)
    }

    private fun downloadModel() {
        busyText = t("prep_model")
        // stažení spuštěné tlačítkem v kroku Offline překlad: po dokončení průvodce sám pokračuje (jen tehdy – balíček
        // stahovaný sám od spuštění aplikace krok neposouvá; zkouška v emulátoru na kroku čeká a pak kliká naslepo)
        val fromStep = wizard == W_LANG
        OfflineTranslator.ensureModel(Prefs.lang(this)) { ok ->
            runOnUiThread {
                busyText = if (ok) null else t("model_fail")
                if (ok && fromStep && wizard == W_LANG && !isFinishing) wizardMove(1)
                render(true); busyText = null
            }
        }
        render(true)   // až po spuštění stahování, ať je vidět pruh
    }

    /** Číslo nejnovější verze v daném kanálu; null, když se nedá zjistit. Volat mimo hlavní vlákno. */
    private fun remoteVersion(channel: UpdateCheck.Channel): UpdateCheck.Remote? = Updater.remoteVersion(channel)

    /** Na pozadí zjistí, jestli není ke stažení novější verze (se zapnutým zkušebním kanálem i v něm). */
    private var lastUpdateCheck = 0L
    /** Ruční kontrola z ⚙ (řádek „Nová verze“): běží / doběhla (pro text řádku). */
    private var updateChecking = false
    private var updateChecked = false
    private val UPDATE_EVERY_MS = 10 * 60_000L

    private fun checkForUpdate(manual: Boolean = false) {
        if (updateChecking) return
        lastUpdateCheck = android.os.SystemClock.elapsedRealtime()
        updateChecking = true
        val test = Prefs.testChannel(this)
        scope.launch {
            val offer = kotlinx.coroutines.withContext(Dispatchers.IO) {
                UpdateCheck.pick(remoteVersion(UpdateCheck.Channel.STABLE),
                    if (test) remoteVersion(UpdateCheck.Channel.TEST) else null, BuildInfo.VERSION_CODE)
            }
            updateChecking = false
            updateChecked = true
            // rozběhnutou aktualizaci (nebo zkoušku aktualizace) nová kontrola nepřepíše
            if (offer != update && !Updater.busy) {
                update = offer
                if (section == Section.HOME && wizard < 0) render(true)
            }
            if (manual && !isFinishing) render(true)
        }
    }

    /** ⚙ → Obecné (dole): „Nová verze · zjistit / aktuální · 0.97.71 / nová 0.97.72 · Domů“ (OK = zjistit hned, nebo Domů). */
    private fun updateRow(top: Int = 8) {
        val o = update
        val value = when {
            updateChecking -> t("uc_busy")
            o != null -> t("uc_new").replace("%s", o.remote.name)
            updateChecked -> t("uc_ok").replace("%s", BuildInfo.VERSION.removePrefix("verze "))
            else -> t("uc_check")
        }
        // aktuální zeleně, nová verze oranžově, zjišťování šedě (přání majitele 22. 11. 2026 – jako Domů)
        val color = when { updateChecking -> Theme.MUTED; o != null -> Theme.WARN; updateChecked -> Theme.OK; else -> Theme.ACCENT }
        add(tagged(ui.settingRow(t("uc_row"), value, color) {
            if (update != null) { section = Section.HOME; wizard = -1; render() }
            else { checkForUpdate(manual = true); render(true) }
        }, "updcheck"), top)
        if (updateChecking) addWaiting(indent = 12, top = 2)
    }

    /** Stáhne nabídnutou verzi a předá ji systému k instalaci; obrazovka ukazuje průběh. */
    private fun startUpdate(o: UpdateCheck.Offer, force: Boolean = false, source: String? = null) {
        Updater.start(this, source ?: UpdateCheck.url(o.channel, Updater.apkFile()), force) {
            runOnUiThread { if (!isFinishing && !isDestroyed && section == Section.HOME && wizard < 0 && currentFocus !is EditText) render(true) }
        }
        render(true)
    }

    /** Řádek o připojení televize k síti; u Wi-Fi s radou, protože slabá síť je nejčastější příčina potíží. */
    /** Hlášení o stavu aplikace přes celou obrazovku (uloží ho i do souboru), viz DiagActivity. */
    private fun makeReport() {
        crashNote = false
        startActivity(Intent(this, DiagActivity::class.java))
    }

    private fun togglePause() {
        startService(Intent(this, TranslatorService::class.java).setAction(TranslatorService.ACTION_PAUSE))
        main.postDelayed({ render(true) }, 300)
    }

    private fun stopTranslation() {
        stopService(Intent(this, TranslatorService::class.java))
        main.postDelayed({ render(true) }, 400)
    }

    private fun openAccessibility() {
        val comp = android.content.ComponentName(this, TitulkyAccessibility::class.java).flattenToString()
        // Android 11+: rovnou stránka služby ReCaption s vypínačem (kde ji systém / výrobce TV má)
        if (Build.VERSION.SDK_INT >= 30) try {
            startActivity(Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME, comp))
            return
        } catch (_: Exception) {}
        try {
            // seznam služeb se zvýrazněnou položkou ReCaption (systémy, které to umí; jinde se doplňky nevšímají)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(":settings:fragment_args_key", comp)
                .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", comp) }))
            toast(t("a11y_hint"))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)); toast(t("a11y_hint")) } catch (_: Exception) {}
        }
    }

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            toast(t("overlay_hint"))
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle(t("adb_title"))
                .setMessage(t("adb_text") + "\n\nadb connect IP\nadb shell appops set $packageName SYSTEM_ALERT_WINDOW allow")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun clearMemory() {
        filesDir.listFiles()?.filter { it.name.startsWith("preklady_") }?.forEach { it.delete() }
        TranslatorService.requestCacheClear()
        toast(t("t_cleared"))
    }

    /** Ukázka pruhu přes obrazovku – nastavíš si vzhled bez hry. */
    private fun showDemo() {
        if (!SubtitleOverlay.canShow(this)) { toast(t("need_overlay")); return }
        demo?.destroy()
        val (w, h) = screenSize()
        val o = SubtitleOverlay(
            this, w, h, Prefs.layout(this), Prefs.textSize(this), Prefs.opacity(this),
            false, Prefs.coloredSpeaker(this),
            Prefs.TEXT_COLORS[Prefs.textColorIndex(this)], Prefs.boldText(this), barPad = Prefs.barPad(this),
            origSp = Prefs.origSp(this),
        )
        demo = o
        o.show(
            listOf(Segment("Marek", t("demo_line"))),
            if (Prefs.showOriginal(this)) "Marek: We need money, Jonas. A lot of money. And fast." else null,
            -1f, -1f
        )
        main.postDelayed({ if (demo === o) { o.destroy(); demo = null } }, 6000)
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }
}

object BuildInfo {
    const val VERSION = "verze 1.3-z8"
    const val VERSION_CODE = 325
}
