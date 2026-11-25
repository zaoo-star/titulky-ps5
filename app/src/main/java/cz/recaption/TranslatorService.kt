package cz.recaption

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import cz.recaption.core.AutoZone
import cz.recaption.core.Console
import cz.recaption.core.DemoGame
import cz.recaption.core.QuickWords
import cz.recaption.core.TextScale
import cz.recaption.core.DiagLog
import cz.recaption.core.FrameSignature
import cz.recaption.core.Glossary
import cz.recaption.core.I18n
import cz.recaption.core.Lang
import cz.recaption.core.LinkStatus
import cz.recaption.core.OcrBlock
import cz.recaption.core.OcrLine
import cz.recaption.core.Perf
import cz.recaption.core.Picked
import cz.recaption.core.Ps5
import cz.recaption.core.ScreenLayout
import cz.recaption.core.Segment
import cz.recaption.core.SubtitleHistory
import cz.recaption.core.SubtitleTracker
import cz.recaption.core.TextLogic
import cz.recaption.core.TranslationCache
import cz.recaption.core.TvSender
import cz.recaption.core.TvSettings
import cz.recaption.core.UsageStats
import cz.recaption.core.sms
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.max

/**
 * Srdce aplikace. Připojí se k PlayStationu po domácí síti (stejně jako Remote Play), z kopie
 * obrazu několikrát za sekundu přečte anglické titulky, přeloží je a položí přes obraz
 * televize černý pruh s překladem. Hra samotná běží do televize kabelem HDMI v plné kvalitě.
 */
class TranslatorService : Service() {

    companion object {
        /** Horní hrana pásu pro úkol (zlomek výšky obrazu): úkol GTA leží jen pod ní, úplně dole (od 0.97.58). */
        const val OBJECTIVE_ZONE_TOP = TextLogic.OBJECTIVE_ZONE_TOP
        const val ACTION_STOP = "cz.recaption.STOP"
        const val ACTION_PAUSE = "cz.recaption.PAUSE"
        const val ACTION_RELOAD = "cz.recaption.RELOAD"
        /** Spustit nad ukázkovým videem místo PlayStationu (zkouška bez konzole). */
        const val ACTION_START_DEMO = "cz.recaption.START_DEMO"

        /** Velikost obrazu, se kterou se počítá, než přijde první snímek videa. */
        const val VIDEO_W = 1280
        const val VIDEO_H = 720

        /** Poslední chyba při spuštění – pro obrazovku aplikace. */
        @Volatile var startError: String? = null
            private set

        @Volatile var running = false
            private set
        /** Velikost obrazu, který právě přichází z konzole ("1920×1080"), a co aplikace naposledy přečetla. */
        @Volatile var videoSize = ""
            private set
        @Volatile var lastRead = ""
            private set
        /** Jak dlouho v průměru trvá jedno přečtení titulku (ms); 0 = zatím se nečetlo. */
        @Volatile var readMs = 0L
            private set
        /**
         * Kolikrát za tohle spuštění rozpoznání textu nestihlo [OCR_TIMEOUT_MS] (viz [process]) – vždy v hlášení,
         * ne jen v mazatelném záznamu (`OCRHANG|` v něm se mohl u dlouhého běhu s hodně událostmi ztratit).
         */
        @Volatile var ocrHangsTotal = 0
            private set
        @Volatile var paused = false
            private set
        /** Čtení obrazu hry je výrazně pomalejší než obvykle ([cz.recaption.core.ReadSlowdown]) – TV dostane oranžový řádek stavu. */
        @Volatile var readSlow = false
            private set
        /** Běží nad ukázkovým videem (ne nad PlayStationem)? */
        @Volatile var demo = false
            private set

        @Volatile private var activeReader: StreamReader? = null

        @Volatile private var activeTv: TvSender? = null

        /** Kolik televizí LG si právě bere titulky; -1 = posílání neběží (vypnuté, nebo nejde otevřít port). */
        val tvConnected: Int get() = activeTv?.takeIf { it.running }?.connected ?: TvIdle.connected ?: -1

        /** Poslední obrázek ze hry (zmenšený, barevný) – pro náhled a nastavení oblastí. */
        val snapshot: Bitmap? get() = activeReader?.colorSnapshot

        @Volatile private var self: TranslatorService? = null

        /** Volba „Nezhasínat displej telefonu“ se změnila – běžící služba hned drží / pustí ztlumený displej. */
        fun applyScreenDim(@Suppress("UNUSED_PARAMETER") c: Context) {
            val s = self ?: return
            android.os.Handler(android.os.Looper.getMainLooper()).post { s.updateScreenDim() }
        }

        /** Záznam snímků Start/Stop z notifikace (tlačítko je jen se zapnutým záznamem v domácí síti nebo při nahrávání). */
        const val ACTION_SNAPS = "cz.recaption.SNAPS"

        /** Čtečka obrazu pro záznam snímků ([SnapRecorder]); null = obraz nechodí. */
        val frameReader: StreamReader? get() = activeReader

        /** Obnoví notifikaci (záznam snímků se zapnul / vypnul, záznam v síti se zapnul). */
        fun refreshNotification() {
            android.os.Handler(android.os.Looper.getMainLooper()).post { self?.takeIf { running }?.updateNotification() }
        }

        /** Změna vzhledu z rychlé nabídky ovladačem na Android TV ([QuickLook]) – stejně jako z TV LG. */
        fun quickChange(ch: TvSettings.Change) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { self?.takeIf { running }?.let { it.applyTvChange(ch); it.quickSample() } }
        }

        /** Nabídka otevřená / zavřená: ukázková věta v pruhu, když zrovna žádný titulek není. */
        fun quickPreview(on: Boolean) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                self?.let {
                    if (on) it.quickSample() else it.quickSampleOff()
                    // s nabídkou i stav připojení vlevo nahoře, zavře se spolu s ní (jako OK v TV LG)
                    if (!demo && Prefs.tvType(it) == cz.recaption.core.TvType.ANDROID && Prefs.statusOverlay(it)) {
                        if (on) StatusPanel.pin(it, TvStatus.rows(it)) else StatusPanel.unpin()
                    }
                }
            }
        }

        /** Snímek obrazovky hry jako JPEG pro záznam v síti (ladění menu a oblastí); null = obraz nechodí. */
        fun stillJpeg(): ByteArray? {
            val b = activeReader?.still() ?: return null
            return try {
                java.io.ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
            } catch (_: Throwable) { null } finally { b.recycle() }
        }

        /** Kolik snímků videa už aplikace dostala a dekódovala (pro obrazovku aplikace). */
        val framesDecoded: Long get() = activeReader?.decoded ?: 0

        /** Obraz právě bere z Xboxu (jinak z PlayStationu nebo ukázky). */
        @Volatile var xbox = false
            private set

        /** Stav spojení s konzolí, ze které se právě čte (PlayStation nebo Xbox). */
        val linkState: Ps5.Link get() = if (xbox) XboxLink.state else Ps5Link.state
        /** Důvod odpojení pro člověka (u Xboxu klíč textu xb_e_…), nebo null. */
        val linkReason: String? get() = if (xbox) XboxLink.reason else Ps5Link.reason

        /** Stav ve třech úrovních: překlad běží / spojeno s konzolí / obraz opravdu přichází. */
        fun linkStatus(now: Long = SystemClock.uptimeMillis()): LinkStatus.Status =
            LinkStatus.evaluate(now, running, paused, demo, linkState,
                if (xbox) XboxLink.connectedSince else Ps5Link.connectedSince, activeReader?.lastFrameAt ?: 0)

        /** Statistika sdílená s obrazovkou aplikace. */
        @Volatile private var stats: UsageStats? = null

        fun statsFor(c: Context): UsageStats = stats ?: Prefs.stats(c).also { stats = it }

        @Volatile private var clearCacheRequested = false

        /** Smazání paměti překladů z obrazovky aplikace (provede se v běžící službě). */
        fun requestCacheClear() { clearCacheRequested = true }

        private const val TAG = "TestPreklad"
        private const val CHANNEL = "test_preklad"
        private const val NOTIF_ID = 1

        private const val CACHE_PREFIX = "preklady_"

        /** Jak dlouho smí stejný text stát na obrazovce, než se přestane brát jako titulek. */
        private const val STALE_MS = 15_000L

        /** Události, které se ukládají i do souboru (viz [EventFile]). */
        private val KEEP = listOf("START|", "LINK|", "VIDEO|", "PERF|", "VEDLE|", "SLOW|", "SOURCE|", "STOP|", "SCALE|", "WARM|", "IMAGE|",
            "NET|", "SCREEN|", "MEM|", "TVSET|", "OCRHANG|")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var reader: StreamReader? = null
    private var link: ConsoleLink? = null
    private var feeder: DemoFeeder? = null
    /** Drží Wi-Fi v plném výkonu, ať video z konzole nevypadává kvůli úspornému režimu. */
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    /** Druhý zámek Wi-Fi s nejnižší odezvou (Android 10+, platí jen při rozsvíceném displeji). */
    private var lowLatencyLock: android.net.wifi.WifiManager.WifiLock? = null
    /**
     * Procesor vzhůru i při zhasnutém displeji. Telefon jako čtečka pro LG/Samsung displej zhasíná;
     * bez zámku ho systém uspal a obraz z PS5 přestal chodit → aplikace spojení rušila a navazovala
     * znovu (0.95: „klávesnice odpojena / účet připojen“ dokola). Android TV box displej nezhasíná.
     */
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    /** Ztlumený, ale nezhasnutý displej telefonu při překladu (volba „Nezhasínat displej telefonu“, od 0.97.85). */
    private var dimLock: android.os.PowerManager.WakeLock? = null

    /** Podle volby drží / pustí ztlumený displej (jen telefon jako čtečka; na TV nic). */
    private fun updateScreenDim() {
        val want = running && Prefs.screenDim(this) && NetInfo.isHandheld(this)
        try {
            if (want && dimLock?.isHeld != true) {
                @Suppress("DEPRECATION")
                dimLock = (getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
                    .newWakeLock(android.os.PowerManager.SCREEN_DIM_WAKE_LOCK, "Titulky PS5:displej")
                    .also { it.setReferenceCounted(false); it.acquire() }
                tp("SCREEN|displej ztlumený, nezhasne")
            } else if (!want && dimLock != null) {
                dimLock?.let { if (it.isHeld) it.release() }
                dimLock = null
            }
        } catch (e: Throwable) { Log.w(TAG, "dim lock", e) }
    }
    @Volatile private var overlay: SubtitleOverlay? = null
    /** Posílání titulků do televize LG; null = vypnuté (výchozí). */
    @Volatile private var tv: TvSender? = null
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val cache = TranslationCache()
    // schovat až po třetím prázdném čtení: jeden nepovedený snímek videa nesmí překlad shodit
    private val tracker = SubtitleTracker(hideAfterEmpty = 3, fastComplete = true)
    @Volatile private var layout = ScreenLayout()
    @Volatile private var lang = Lang.CS
    private var screenW = 0
    private var screenH = 0
    private var capW = VIDEO_W
    private var capH = VIDEO_H
    @Volatile private var debug = false
    @Volatile private var onlySubtitles = true
    @Volatile private var autoFind = true
    private val autoZone = AutoZone()

    private var lastLicenseCheck = 0L

    /** Výkon podle paměti zařízení – slabší televize čtou méně často. */
    private val perf: Perf by lazy {
        try {
            val am = getSystemService(android.app.ActivityManager::class.java)
            val mi = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            Perf.forDevice(mi.totalMem / (1024 * 1024), am.isLowRamDevice)
        } catch (_: Exception) { Perf.forDevice(0, false) }.withQuality(
            // automatika: když s obrazem 1080p spojení na tomto zařízení nedrželo, zůstává u 720p
            Prefs.readQuality(this).let { q -> if (q == "auto" && Prefs.highResFailed(this)) "std" else q })
    }

    /** Kdy schovat pruh (překlad zůstane chvíli po zmizení angličtiny, ať se dá dočíst). 0 = nic. */
    private var hideAt = 0L
    @Volatile private var holdMs = 1000L
    @Volatile private var showOriginal = false
    @Volatile private var lastSig: IntArray? = null
    private var hiddenForOtherApp = false

    // diagnostika
    private var frames = 0
    private var ocrRuns = 0
    private var lastOcrText = ""
    private var lastMs = 0L
    @Volatile private var lastOcrMs = 0L
    /** Čtení titulků pomalejší než tohle = pomalé zařízení: pokyny u předmětů se při titulku nečtou. */
    private val WORLD_SLOW_MS = 300L
    /**
     * Pojistka proti zaseknutému rozpoznání (ML Kit): běžně trvá pod 150 ms, 8 s je velká rezerva i pro
     * pomalé zařízení. Bez limitu by se zaseklé volání `.await()` nikdy nevrátilo a čtení titulků by
     * stálo navždy beze stopy v záznamu (tester 5HZ4-XSEF, 24. 11. 2026: 14 minut beze čtení, bez chyby).
     */
    private val OCR_TIMEOUT_MS = 8000L
    private var ocrHangs = 0
    private var ticksLogged = 0
    private var lastLinkLogged: Ps5.Link? = null
    private var lastVideoLog = 0L
    private var lastPerfLog = 0L
    private var ocrMsSum = 0L
    private var shows = 0

    /** Slovník: jména podle hry, předpřipravené hlášky a vlastní slovník uživatele. */
    @Volatile private var glossary: Glossary? = null
    /** Z čeho se překládá (volba „Překládat z“, výchozí angličtina). */
    @Volatile private var source = cz.recaption.core.SourceLang.EN
    /** Hlášky a menu podle profilu hry (přeskočí se při čtení). */
    @Volatile private var screen: cz.recaption.core.GameScreen = cz.recaption.core.GameScreen.EMPTY
    /** Profil hry píše pod repliky pokyny pro hráče (GTA) – poslední krátký řádek se označí jako pokyn. */
    @Volatile private var prompts = false
    /** Hra, ze které se sbírají repliky (LineCollector), nebo null = nesbírá se. */
    @Volatile private var collectGame: String? = null
    /** Odhad hry podle jmen v titulcích – jen nabídka v Nastavení, nic se nepřepíná. */
    @Volatile private var gameGuess = cz.recaption.core.GameGuess()
    /** Seznam her, ze kterého je [gameGuess] (po stažení nového seznamu se odhad sestaví znovu). */
    private var guessCatalog = cz.recaption.core.GameProfiles.catalog

    private val textScale = TextScale()
    /** Jazyk, jehož překladový model už je načtený v paměti. */
    @Volatile private var warmedLang: Lang? = null
    private var shownW = 0
    private var shownH = 0
    /** Text, který je právě v oblasti, a od kdy tam beze změny stojí (viz [STALE_MS]). */
    private var steadyText = ""
    private var steadySince = 0L

    /** Záznam pro automatickou zkoušku i pro hlášení chyby. */
    private fun tp(msg: String) {
        Log.i("TP_TEST", msg); DiagLog.add(msg)
        if (KEEP.any { msg.startsWith(it) }) EventFile.add(msg)
    }

    private val menuBoxDiag = HashMap<String, String>()

    /**
     * Jen pro ladění z dávky snímků (core/SnapBatches, Nastavení → Experimentální → Ladění a hlášení → Záznam
     * snímků): poloha a text vykreslených popisků menu vedle snímku v `index.txt`, ať jde offline poskládat
     * náhled, jak pruh s překladem na tom místě vypadá – appka sama s tím nic nedělá, žádné chování se nemění.
     */
    private fun tpLabels(tag: String, labels: List<cz.recaption.core.MenuLabel>) {
        if (labels.isEmpty()) return
        val line = labels.joinToString(";") { lb ->
            "%.3f,%.3f,%.3f,%.3f=%s".format(java.util.Locale.ROOT, lb.box[0], lb.box[1], lb.box[2], lb.box[3], lb.text)
        }
        if (menuBoxDiag[tag] == line) return
        menuBoxDiag[tag] = line
        tp("MENUBOX|$tag|$line")
    }

    @Volatile private var lastError: String? = null

    /** Překlady spuštěné dopředu, hned po prvním přečtení titulku (text → běžící překlad). */
    private val inflight = java.util.concurrent.ConcurrentHashMap<String, Deferred<List<Segment>?>>()
    private var statsStart = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        EventFile.init(this)
        when (intent?.action) {
            ACTION_STOP -> { tp("STOP|tlačítkem v notifikaci"); stopSelf(); return START_NOT_STICKY }
            ACTION_PAUSE -> {
                if (running) {
                    paused = !paused
                    if (paused) { clearQueue(); overlay?.hide(); dropObjective(); dropMenu(); dropCorner(); dropWorld(); dropPhone(); dropNotice(); dropTip() } else resetReading()
                    updateNotification()
                }
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                if (running) reloadSettings()
                return START_NOT_STICKY
            }
            ACTION_SNAPS -> {
                if (running) SnapRecorder.toggle(this)
                return START_NOT_STICKY
            }
        }

        val wantDemo = intent?.action == ACTION_START_DEMO
        startForegroundCompat(buildNotification())
        if (running) {
            // už běží → jen případně přepni zdroj (ukázka ↔ PlayStation)
            if (wantDemo != demo) startSource(wantDemo)
            return START_NOT_STICKY
        }
        startError = null
        if (!Prefs.license(this).canRun) { startError = "licence"; stopSelf(); return START_NOT_STICKY }
        val console = Prefs.console(this)
        if (!wantDemo && !Prefs.consoleReady(this)) { startError = if (console == Console.XBOX) "xbox" else "ps5"; stopSelf(); return START_NOT_STICKY }

        setupScreenSize()
        val r = StreamReader(perf.captureWidth, perf.captureHeight)
        // Xbox posílá hotové snímky (WebRTC) – dekodér H.264 se otevře, až by byl potřeba (ukázka)
        if (!r.start(openDecoder = wantDemo || console != Console.XBOX)) {
            startError = "video: " + (r.lastError ?: "?")
            tp("ERROR|decoder|" + r.lastError)
            r.stop(); stopSelf(); return START_NOT_STICKY
        }
        reader = r
        activeReader = r
        try {
            @Suppress("DEPRECATION")
            wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager)
                .createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "TestPreklad").also { it.acquire() }
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                lowLatencyLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager)
                    .createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "TestPreklad:odezva").also { it.acquire() }
            }
        } catch (e: Throwable) { Log.w(TAG, "wifi lock", e) }
        try {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
                .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Titulky PS5:preklad")
                .also { it.setReferenceCounted(false); it.acquire() }
        } catch (e: Throwable) { Log.w(TAG, "wake lock", e) }
        beginRunning()
        updateScreenDim()
        startSource(wantDemo)
        return START_NOT_STICKY
    }

    /** Zapne zdroj videa: PlayStation po síti, nebo ukázkové video. */
    private fun startSource(wantDemo: Boolean) {
        val r = reader ?: return
        link?.stop(); link = null
        feeder?.stop(); feeder = null
        demo = wantDemo
        xbox = false
        overlay?.hide(); resetReading()
        r.clearFrames()
        if (wantDemo) {
            // ukázka má stejnou velikost obrazu, jakou by toto zařízení bralo z konzole
            feeder = DemoFeeder(perf.captureWidth, perf.captureHeight, { r.feed(it) }, { y, w, h -> r.feedRaw(y, w, h) }, { r.decoded }).also { it.start() }
            tp("SOURCE|demo")
        } else if (Prefs.console(this) == Console.XBOX) {
            val id = XboxAccount.consoleId(this) ?: return
            // telefon: obraz do paměti (některé telefony z grafického čipu vracely jen černou)
            link = XboxLink(applicationContext, id, r, gpu = !NetInfo.isHandheld(this)).also { xbox = true; it.start() }
            tp("SOURCE|xbox")
        } else {
            val host = Prefs.ps5Host(this) ?: return
            link = Ps5Link(applicationContext, host, perf.captureWidth >= 1920, needKey = { r.needKey }) { r.feed(it) }.also { it.start() }
            tp("SOURCE|ps5 ${host.ip}")
        }
        updateNotification()
    }

    private fun beginRunning() {
        lastLicenseCheck = SystemClock.uptimeMillis()
        tp("A11Y|" + (TitulkyAccessibility.instance != null))
        tp("START|video=${perf.captureWidth}x${perf.captureHeight}|interval=${perf.intervalMs}|screen=${screenW}x${screenH}|eco=${perf.eco}|decoder=${reader?.decoderName}|lib=${Ps5Link.selfTest()}")
        lang = Prefs.lang(this)
        statsFor(this)
        loadCache()
        reloadSettings()
        running = true
        paused = false
        statsStart = SystemClock.uptimeMillis()
        scope.launch(subtitleThread) { loop() }
        tvStatusHandler.removeCallbacks(gamesTick)
        tvStatusHandler.post(gamesTick)
        tvStatusHandler.removeCallbacks(usageTick)
        tvStatusHandler.post(usageTick)
    }

    /**
     * Anonymní počítání uživatelů ([UsagePing]): při startu překladu a pak nejvýš 1× za 10 min, dokud překlad běží.
     * Tady jen porovnání času, síť běží na vlastním vlákně. Ukázka (i zkouška v emulátoru) se nepočítá.
     */
    private val usageTick = object : Runnable {
        override fun run() {
            if (!running) return
            if (!demo) try { UsagePing.maybe(this@TranslatorService) } catch (_: Throwable) {}
            tvStatusHandler.postDelayed(this, 60_000)
        }
    }

    /**
     * Seznam her (data her v aplikaci nejsou): dokud žádný stažený není, zkouší se při překladu každé 2 min
     * ([GameListUpdate.maybeAuto]); nový si služba načte sama (ACTION_RELOAD). Se staženým nic nedělá.
     */
    private val gamesTick = object : Runnable {
        override fun run() {
            if (!running || cz.recaption.core.GameProfiles.catalog.downloaded) return
            try { GameListUpdate.maybeAuto(this@TranslatorService) { } } catch (_: Throwable) {}
            tvStatusHandler.postDelayed(this, 120_000)
        }
    }

    /** Načte nastavení (po spuštění i po změně v aplikaci) a znovu vytvoří pruh. */
    /** Stav pro televizi (TvStatus) každé 2 s – posílá se jen změna, takže to televizi ani síť nezatěžuje. */
    private val tvStatusTick = object : Runnable {
        override fun run() {
            val s = tv ?: return
            // port pro TV drží ještě obrazovka aplikace (TvIdle) → uvolnit, služba si ho pak sama otevře (TvSender.start(retry))
            if (!s.running) try { TvIdle.stop() } catch (_: Throwable) {}
            sampleReadSpeed()
            try { s.sendStatus(TvStatus.message(this@TranslatorService)) } catch (_: Throwable) {}
            tvStatusHandler.postDelayed(this, 2000)
        }
    }
    private val tvStatusHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Zpomalilo čtení? (24. 11. 2026) Vzorek každé 2 s spolu se stavem pro televizi: kolik snímků videa smyčka čtení zpracovala.
     * Počítá se jen, když se číst má (spojení s konzolí drží, není pauza, ukázka ani otevřené menu hry). Změna jde do záznamu.
     */
    private val readSlowdown = cz.recaption.core.ReadSlowdown()
    private fun sampleReadSpeed() {
        // i když obraz přestal chodit úplně (dekodér v pozadí padá) – jinak by upozornění s každým výpadkem zmizelo
        val active = running && !demo && !paused && !menuShown && linkStatus().stage in IMAGE_STAGES
        val was = readSlow
        val now = try { readSlowdown.sample(SystemClock.uptimeMillis(), frames.toLong(), active, UiState.visible) } catch (_: Throwable) { false }
        if (now != was) {
            readSlow = now
            tp("SLOW|" + (if (now) "čtení zpomalilo" else "čtení zase v pořádku") + "|obvykle=${readSlowdown.usual.toInt()}/s|aplikace vidět=${UiState.visible}")
        }
    }
    /**
     * Nová verze, když běží jen překlad a aplikace je zavřená (každých 30 min, upozornění – [UpdateNotifier]).
     * Zároveň TV LG: prodloužit vývojářský režim, je-li zapnuto (sám se ptá nejvýš jednou denně – [LgDevMode.renew]).
     */
    private val updateTick = object : Runnable {
        override fun run() {
            if (!running) return
            Thread({ try { UpdateNotifier.check(this@TranslatorService) } catch (_: Throwable) {} }, "tp-nova-verze").apply { isDaemon = true; start() }
            try { LgDevMode.renew(this@TranslatorService) } catch (_: Throwable) {}
            tvStatusHandler.postDelayed(this, UpdateNotifier.SERVICE_EVERY_MS)
        }
    }
    private fun startTvStatus() {
        readSlowdown.reset(); readSlow = false
        tvStatusHandler.removeCallbacks(tvStatusTick)
        tvStatusHandler.removeCallbacks(panelTick)
        tvStatusHandler.removeCallbacks(updateTick)
        tvStatusHandler.postDelayed(updateTick, UpdateNotifier.SERVICE_EVERY_MS)
        // TV LG: prodloužení vývojářského režimu i hned po spuštění překladu (jen se zapnutou volbou, nejvýš 1× denně)
        try { LgDevMode.renew(this) } catch (_: Throwable) {}
        if (tv != null) tvStatusHandler.post(tvStatusTick)
        // Android TV: stejný stav v bodech přes hru vlevo nahoře (StatusPanel), jako má TV LG
        else if (Prefs.tvType(this) == cz.recaption.core.TvType.ANDROID) tvStatusHandler.post(panelTick)
    }

    /** Stav přes hru na Android TV každé 2 s (ukáže se jen při změně; jen když aplikace není vidět). */
    private val panelTick = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                // výchozí vypnuto (přání majitele): stav stačí na Domů, přes hru jen se zapnutou volbou
                if (demo || UiState.visible || !Prefs.statusOverlay(this@TranslatorService)) StatusPanel.hide()
                else StatusPanel.update(this@TranslatorService, TvStatus.rows(this@TranslatorService),
                    dynamic = Prefs.statusMode(this@TranslatorService) == 1, sticky = TvStatus.sticky(this@TranslatorService))
            } catch (_: Throwable) {}
            tvStatusHandler.postDelayed(this, 2000)
        }
    }

    private fun reloadSettings() {
        val newLang = Prefs.lang(this)
        if (newLang != lang) {
            saveCache(); cache.clear(); lang = newLang; loadCache()
        }
        layout = Prefs.layout(this)
        debug = Prefs.debug(this)
        onlySubtitles = Prefs.onlySubtitles(this)
        val wasAuto = autoFind
        autoFind = Prefs.autoZone(this)
        if (autoFind != wasAuto) autoZone.reset()
        if (autoFind) layout = layout.copy(read = autoZone.current)
        glossary = Prefs.glossary(this)
        source = Prefs.sourceLang(this); OfflineTranslator.source = source
        if (guessCatalog !== cz.recaption.core.GameProfiles.catalog) {
            guessCatalog = cz.recaption.core.GameProfiles.catalog; gameGuess = cz.recaption.core.GameGuess()
        }
        screen = cz.recaption.core.GameProfiles.of(Prefs.gameId(this)).screen
        prompts = cz.recaption.core.GameProfiles.of(Prefs.gameId(this)).prompts
        // sbírání replik (⚙ → Čtení a překlad, výchozí zapnuto): jen s vybraným profilem hry, ne s univerzálním
        collectGame = Prefs.gameId(this).takeIf { Prefs.collectLines(this) && it != cz.recaption.core.GameProfiles.UNIVERSAL.id }
        // překlad menu hry jen se zapnutou volbou (od 0.97.57 výchozí vypnuto – majitel ho nechce)
        menuSpec = if (Prefs.gameMenu(this)) cz.recaption.core.GameProfiles.of(Prefs.gameId(this)).menuSpec else null
        menuShown = false; menuKey = ""; menuDescKey = ""; menuWatch = null; menuWords.clear(); menuTracker = null
        coloredSpeaker = Prefs.coloredSpeaker(this)
        cz.recaption.core.NameColor.profile = cz.recaption.core.GameProfiles.of(Prefs.gameId(this)).colorsByKey
        // změna hry nebo slovníku → dřívější překlady už neplatí
        val sig = Prefs.glossarySignature(this)
        if (sig != Prefs.savedGlossarySignature(this)) {
            cache.clear(); try { cacheFile().delete() } catch (_: Exception) {}
            Prefs.setSavedGlossarySignature(this, sig)
        }
        deeplKey = DeeplTranslator.activeKey(this)
        holdMs = Prefs.holdMs(this).toLong()
        showQueue.minMs = Prefs.minShowMs(this).toLong()
        fastRead = Prefs.fastRead(this)
        onlyNewLines = Prefs.onlyNewLines(this)
        objectiveColors = Prefs.objColors(this)
        clearQueue()
        recentLines.clear()   // jiný jazyk, hra nebo slovník → zapamatované překlady neplatí
        showOriginal = Prefs.showOriginal(this)
        hideAt = 0
        overlay?.destroy()
        // Titulky do televize LG: jen když si to uživatel zapnul. Jinak se nic neotevírá ani neposílá.
        if (Prefs.tvSend(this)) {
            // otevřené spojení z obrazovky aplikace (TvIdle) se převezme – televize zůstane připojená (od 0.97.71)
            val kept = if (tv == null) TvIdle.take() else null
            if (tv == null) (kept ?: TvSender { tp("TV|$it") }).also {
                // jiná televize než minule → její vzhled pruhu a oblasti (TvProfile)
                it.onAnnounce = { a ->
                    if (TvProfile.onAnnounce(this, a)) android.os.Handler(android.os.Looper.getMainLooper()).post { if (running) reloadSettings() }
                }
                // vzhled změněný ovladačem televize: uložit jako z telefonu a načíst znovu
                it.onChange = { ch -> android.os.Handler(android.os.Looper.getMainLooper()).post { if (running) applyTvChange(ch) } }
                if (it !== kept) it.start(retry = true)
                tv = it; activeTv = it
            }
        } else {
            tv?.stop(); tv = null; activeTv = null
        }
        val sender = tv
        startTvStatus()
        sender?.sendSettings(TvLookPrefs.message(this))
        // jiná oblast, poloha nebo hra → plocha „Zakrýt celou oblast titulků“ se počítá znovu
        SubtitleOverlay.coverArea.clear()
        overlay = SubtitleOverlay(
            this, screenW, screenH, layout, Prefs.textSize(this), Prefs.opacity(this),
            debug, Prefs.coloredSpeaker(this),
            Prefs.TEXT_COLORS[Prefs.textColorIndex(this)], Prefs.boldText(this), Prefs.matchSize(this),
            barPad = Prefs.barPad(this),
            coverAll = Prefs.coverAll(this),
            origSp = Prefs.origSp(this),
            objSizeSp = Prefs.objSize(this),
            objPosPercent = Prefs.objPos(this),
            objBgPercent = Prefs.objBg(this),
            showPrompts = Prefs.gamePrompts(this),
            speakerPreset = cz.recaption.core.GameProfiles.of(Prefs.gameId(this)).colorsByKey,
            onText = sender?.let { s -> { text: String?, parts: String?, look: String? -> if (text == null) s.hide() else s.show(text, parts, look) } },
            onObjective = sender?.let { s -> { m: String -> s.sendObjective(m) } },
            onMenuDesc = sender?.let { s -> { m: String -> s.sendMenuDesc(m) } },
            onCorner = sender?.let { s -> { m: String -> s.sendCorner(m) } },
            onWorld = sender?.let { s -> { m: String -> s.sendWorld(m) } },
            onPhone = sender?.let { s -> { m: String -> s.sendPhone(m) } },
            onMenuLabels = sender?.let { s -> { m: String -> s.sendMenuLabels(m) } },
        )
        loadCornerSettings(); loadWorldSettings(); loadPhoneSettings(); loadTipSettings()
        whiteSubtitles = cz.recaption.core.GameProfiles.of(Prefs.gameId(this)).cornerSpec != null
        // Titulky PS5: titulky jen v pruhu hry (core/SubtitleBox), jen u hry, jejíž profil pruh zná
        subtitleBoxFilter = cz.recaption.core.GameProfiles.of(Prefs.gameId(this)).subtitleLook
            ?.takeIf { Prefs.subtitleBox(this) }?.let { cz.recaption.core.SubtitleBox.Filter(it) }
        subtitleBackdrop = cz.recaption.core.SubtitleBackdrop()
        objectiveTracker.reset(); objectiveSeq++
        resetReading()
        val l = lang
        modelTries = 0
        prepareModel(l)
        updateNotification()
    }

    /** Změna vzhledu z ovladače televize ([TvSettings]): stejné hodnoty jako v Titulkách v telefonu. */
    /** Barvy jmen: požádat čtečku obrazu o barvu jména, které ještě nemá naučenou (jednou za jméno a chvíli). */
    @Volatile private var coloredSpeaker = true
    private val colorAsked = HashMap<String, Long>()
    private fun askNameColor(blocks: List<cz.recaption.core.OcrBlock>, text: String, x0: Int, y0: Int) {
        val r = reader ?: return
        val now = SystemClock.uptimeMillis()
        for (line in blocks.flatMap { it.lines }) {
            val seg = TextLogic.splitSpeaker(line.text)
            val name = seg.speaker ?: continue
            if (!text.contains(name)) continue
            val key = cz.recaption.core.SpeakerColors.key(name)
            // jistá naučená barva → už se nečte; naučená, ale ještě ne jistá → občas ověřit (oprava špatného
            // naučení; i postavy z profilu – hra mohla po aktualizaci barvy změnit); jinak nejvýš jednou za 3 s
            if (cz.recaption.core.NameColor.settled(name)) continue
            val wait = if (cz.recaption.core.NameColor.learned(name) != null) 15_000 else 3000
            if (now - (colorAsked[key] ?: 0L) < wait) continue
            colorAsked[key] = now
            // přesně slova jména (rámečky od čtečky, bez okrajů); bez nich odhad podle počtu znaků jako dřív
            val box = cz.recaption.core.NameColor.nameBox(line, name) ?: run {
                val frac = (name.length + 1).toFloat() / maxOf(1, line.text.length)
                intArrayOf(line.left, line.top, line.left + ((line.right - line.left) * frac).toInt(), line.bottom)
            }
            r.requestColor(StreamReader.ColorRequest(TextLogic.normalizeName(name),
                (x0 + box[0]).toFloat() / capW, (y0 + box[1]).toFloat() / capH,
                (x0 + box[2]).toFloat() / capW, (y0 + box[3]).toFloat() / capH))
            return
        }
    }

    /** Výsledek čtení barvy: zapsat (měření, naučené barvy) a dát pruhu naučenou barvu ([cz.recaption.core.NameColor.learned]). */
    private fun takeNameColor() {
        val r = reader ?: return
        val (name, rgb, us) = r.colorResult ?: return
        r.colorResult = null
        cz.recaption.core.NameColor.record(name, rgb, us)
        tp("COLOR|$name|${rgb?.let { cz.recaption.core.SpeakerColors.hex(it) } ?: "-"}|us=$us")
        cz.recaption.core.NameColor.learned(name)?.let { c -> overlay?.learnColor(name, c) }
    }

    /** Ukázková věta v pruhu (rychlá nabídka vzhledu), jen když není vidět skutečný titulek. */
    private var sampleShown = false
    private fun quickSample() {
        if (!QuickLook.open) return
        val ov = overlay ?: return
        if (ov.isShown && !sampleShown) return
        // s volbou „originál“ i druhý řádek, ať je náhled celý (anglická ukázková věta jako originál)
        ov.show(listOf(Segment(null, tx("qk_sample"))), if (showOriginal) "This is how the subtitle translation will look." else null, -1f, -1f)
        sampleShown = true
    }
    private fun quickSampleOff() {
        if (sampleShown) { overlay?.hide(); sampleShown = false }
    }

    private fun applyTvChange(ch: TvSettings.Change) {
        TvLookPrefs.apply(this, ch)
        tp("TVSET|${ch.javaClass.simpleName}")
        reloadSettings()
    }

    /** Kolikrát se za tohoto běhu zkoušelo stáhnout jazykový balíček (po neúspěchu se zkouší znovu). */
    @Volatile private var modelTries = 0
    @Volatile private var lastModelRetry = 0L

    /**
     * Jazykový balíček pro překlad: stáhnout, když chybí, a připravit. Když stažení selže (výpadek sítě,
     * serveru Googlu), zkusí se za 20 s znovu, nejvýš 6× za běh – jinak by se do restartu nepřekládalo nic.
     */
    private fun prepareModel(l: Lang) {
        modelTries++
        OfflineTranslator.ensureModel(l) { ok ->
            tp("MODEL|${l.code}|${if (ok) "ok" else "fail"}|pokus=$modelTries")
            if (!ok && running && l == lang && modelTries < 6) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (running && l == lang) prepareModel(l) }, 20_000)
            }
            // překladač se připraví hned, ne až s prvním titulkem
            if (ok && warmedLang != l) {
                warmedLang = l
                scope.launch { OfflineTranslator.warmUp(l)?.let { (cold, warm) -> tp("WARM|${l.code}|první=${cold}ms|další=${warm}ms") } }
            }
        }
    }

    // ---------------- notifikace ----------------

    private fun buildNotification(): Notification {
        val l = Prefs.uiLang(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Titulky PS5", NotificationManager.IMPORTANCE_LOW))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val stopPi = PendingIntent.getService(this, 1, Intent(this, TranslatorService::class.java).setAction(ACTION_STOP), flags)
        val pausePi = PendingIntent.getService(this, 2, Intent(this, TranslatorService::class.java).setAction(ACTION_PAUSE), flags)
        val openPi = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java), flags)
        val noIcon: android.graphics.drawable.Icon? = null
        val b = Notification.Builder(this, CHANNEL)
            .setContentTitle(I18n.t(l, if (paused) "n_paused" else "n_running"))
            .setContentText(I18n.t(l, "n_text"))
            .setSmallIcon(R.drawable.ic_notify)
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(noIcon, I18n.t(l, if (paused) "b_resume" else "b_pause"), pausePi).build())
            .addAction(Notification.Action.Builder(noIcon, I18n.t(l, "b_stop"), stopPi).build())
        // záznam snímků pro ladění: se záznamem v síti nebo když už snímky nahrával (obojí jen po zapnutí v Experimentální)
        if (SnapRecorder.active || NetLogShare.active(this) || Prefs.snapButton(this)) {
            val snapPi = PendingIntent.getService(this, 4, Intent(this, TranslatorService::class.java).setAction(ACTION_SNAPS), flags)
            b.addAction(Notification.Action.Builder(noIcon, I18n.t(l, if (SnapRecorder.active) "sn_n_stop" else "sn_n_start"), snapPi).build())
        }
        return b.build()
    }

    private fun updateNotification() {
        try { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification()) } catch (_: Exception) {}
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIF_ID, n)
    }

    // ---------------- obrazovka ----------------

    @Suppress("DEPRECATION")
    private fun setupScreenSize() {
        val wm = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            screenW = b.width(); screenH = b.height()
        } else {
            val dm = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            screenW = dm.widthPixels; screenH = dm.heightPixels
        }
        if (screenW <= 0 || screenH <= 0) { screenW = 1920; screenH = 1080 }
    }


    // ---------------- hlavní smyčka ----------------

    /**
     * Přednost titulků u procesoru ([ReadPriority]): smyčka čtení titulků na vlastním vlákně s vyšší prioritou, vedlejší čtení
     * na vlastním s nižší; na Androidu 12+ se systému hlásí délka kola čtení ([ReadHint]).
     */
    private val subtitleThread by lazy { ReadPriority.dispatcher("tp-titulky", ReadPriority.SUBTITLES) }
    private val sideThread by lazy { ReadPriority.dispatcher("tp-vedle", ReadPriority.SIDE) }
    private val readHint = ReadHint()

    private suspend fun loop() {
        // jen jednou na smyčku; běží na vlákně titulků, takže hlášení platí pro něj
        val hinted = readHint.open(this, perf.intervalMs)
        tp("PRIO|titulky=${ReadPriority.SUBTITLES}|vedle=${ReadPriority.SIDE}|hlaseni vykonu=" + (if (hinted) "ano" else "ne"))
        try { loopRounds() } finally { readHint.close() }
    }

    private suspend fun loopRounds() {
        while (currentCoroutineContext().isActive) {
            val t0 = SystemClock.uptimeMillis()
            try {
                watchLink(t0)
                if (!paused && appAllowed()) tick(t0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "smyčka", e)
            }
            if (hideAt in 1..SystemClock.uptimeMillis()) { hideAt = 0; overlay?.hide() }
            if (autoFind && !paused && autoZone.onTick(t0)) zoneChanged()
            if (clearCacheRequested) {
                clearCacheRequested = false
                cache.clear(); cacheFile().delete()
            }
            val ov = overlay
            if (debug && ov != null) publishDebug(ov)
            if (cache.dirty >= 20) saveCache()
            // jednou za minutu: neskončila zkušební doba během hraní?
            if (t0 - lastLicenseCheck > 60_000) {
                lastLicenseCheck = t0
                if (!Prefs.license(this).canRun) { stopSelf(); return }
            }
            val spent = SystemClock.uptimeMillis() - t0
            // Po každém čtení krátká pauza úměrná jeho délce: procesor televize musí stíhat i příjem
            // videa a spojení s konzolí, jinak se spojení rozpadá.
            delay(max(minOf(100L, max(20L, spent / 4)), perf.intervalMs - spent))
        }
    }

    /**
     * Čtení začíná znovu (pauza, nové nastavení, spojení, jiný zdroj): zapomene se rozpracovaný
     * titulek i překlady spuštěné dopředu. Čtení nebo překlad, které ještě běží, po návratu poznají
     * podle [SubtitleTracker.revision], že jsou zastaralé, a nic neukážou.
     */
    private fun resetReading() {
        tracker.reset()
        lastSig = null
        // jen zapomenout, nerušit: smyčka může na překlad právě čekat a zrušení by ji ukončilo
        inflight.clear()
    }

    private var lastImageStage = LinkStatus.Stage.OFF
    private val IMAGE_STAGES = setOf(LinkStatus.Stage.IMAGE, LinkStatus.Stage.NO_IMAGE, LinkStatus.Stage.STALLED)

    /** Hlídá spojení s konzolí: zapisuje změny stavu a obnoví spojení, když video přestane chodit. */
    private fun watchLink(now: Long) {
        val r = reader ?: return
        if (now - lastVideoLog > 5000) {
            lastVideoLog = now
            checkWifiBand()
            if (ticksLogged < 400) tp("VIDEO|fed=${r.fed}|decoded=${r.decoded}|${r.frameWidth}x${r.frameHeight}|dec=${r.decoderName}|err=${r.lastError}" +
                (feeder?.let { "|demo sent=${it.sent} raw=${it.raw} err=${it.error}" } ?: ""))
        }
        // jednou za půl minuty souhrn rychlosti (pro hlášení chyby)
        if (now - lastPerfLog > 30_000 && ocrRuns > 0) {
            lastPerfLog = now
            // paměť aplikace: kdyby rostla, je to v hlášení vidět (0.95: telefon hlásil nedostatek paměti a čtení se zpomalovalo)
            val rt = Runtime.getRuntime()
            val heapMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
            val nativeMb = android.os.Debug.getNativeHeapAllocatedSize() / 1_048_576
            // fed/decoded a stav menu u každého řádku (ne jen ve VIDEO|, to se u dlouhého běhu vytlačí z bufferu) –
            // ať jde z hlášení poznat, jestli dál přichází obraz, i když čtení titulků dlouho nepokročilo
            // (tester 5HZ4-XSEF, 24. 11. 2026: čtení 14 min neběželo, bez jediné chyby v záznamu).
            tp("PERF|čtení=${ocrRuns}|průměr=${ocrMsSum / ocrRuns}ms|poslední=${lastOcrMs}ms|titulků=${shows}|fed=${r.fed}|decoded=${r.decoded}|menu=$menuShown|video=${r.frameWidth}x${r.frameHeight}|oblast=${(layout.read.height * 100).toInt()}%|paměť=${heapMb}+${nativeMb}MB" + (wifiNow()?.let { (f, rssi) -> "|wifi=${f}MHz/${rssi}dBm" } ?: ""))
            // vedlejší čtení (ovládání, telefon, tipy, menu…): jak dlouho čekala za titulky – sleduje se, jestli čekání neškodí
            sideReads.summary()?.let { tp("VEDLE|$it") }
        }
        if (demo) return
        val st = linkState
        if (st != lastLinkLogged) {
            lastLinkLogged = st
            tp("LINK|$st|" + (linkReason ?: "") + "|dec=${r.decoderName}|${r.frameWidth}x${r.frameHeight}|fed=${r.fed}|decoded=${r.decoded}")
            // spojení spadlo → starý překlad nenechávej viset na obrazovce
            if (st != Ps5.Link.CONNECTED) { hideAt = 0; overlay?.hide(); resetReading(); r.clearFrames() }
            updateNotification()
        }
        // obraz: přichází / nepřichází (do záznamu jen změny, ať je v hlášení vidět, kdy se zastavil)
        val img = linkStatus(now)
        if (img.stage != lastImageStage) {
            if (img.stage in IMAGE_STAGES || lastImageStage in IMAGE_STAGES) tp("IMAGE|${img.stage}|decoded=${r.decoded}")
            lastImageStage = img.stage
        }
        val l = link ?: return
        // kdy naposledy přišla z konzole nějaká data (počet přijatých snímků se změnil)
        if (r.fed != lastFedSeen) { lastFedSeen = r.fed; lastFedAt = now }
        if (st != Ps5.Link.CONNECTED) { lastFedAt = now; stallLogged = false; return }
        val noFrames = now - r.lastFrameAt
        // Xbox posílá hotové snímky bez fed – dekódovaný snímek je taky důkaz, že data chodí
        val noInput = now - maxOf(lastFedAt, r.lastFrameAt)
        if (noInput > Ps5.NET_STALL_LOG_MS && !stallLogged) {
            stallLogged = true
            tp("PS5|stall: z konzole nic nepřichází ${noInput / 1000} s, síť=${NetInfo.kind(this)}")
        } else if (noInput < Ps5.NET_STALL_LOG_MS && stallLogged) {
            stallLogged = false
            tp("PS5|stall: data zase chodí")
        }
        when (Ps5.watchdog(l.connectedForMs, noFrames, noInput)) {
            Ps5.Watchdog.DECODER -> l.restart("no video")
            Ps5.Watchdog.NETWORK -> l.restart("no data")
            Ps5.Watchdog.OK -> {}
        }
    }

    // ---- Wi-Fi: pásmo a síla signálu (0.95: telefon sám přešel z 5 GHz na 2,4 GHz a spojení vypadávalo) ----
    private var lastBand: String? = null

    /** Frekvence (MHz) a síla signálu (dBm) Wi-Fi tohoto zařízení; null = není na Wi-Fi. */
    private fun wifiNow(): Pair<Int, Int>? = try {
        @Suppress("DEPRECATION")
        val i = (applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager).connectionInfo
        if (i == null || i.frequency <= 0) null else i.frequency to i.rssi
    } catch (_: Throwable) { null }

    /** Zapíše, když Wi-Fi přejde na jiné pásmo (a s jakým signálem) – podle toho jde poznat, proč. */
    private fun checkWifiBand() {
        val (f, rssi) = wifiNow() ?: return
        val band = if (f >= 4900) "5 GHz" else "2,4 GHz"
        if (band != lastBand) {
            tp("NET|Wi-Fi " + (if (lastBand == null) "na" else "přešla na") + " $band ($f MHz, signál $rssi dBm)")
            lastBand = band
        }
    }

    // hlídání obrazu: data z konzole (fed) vs. dekódované snímky
    private var lastFedSeen = -1L
    private var lastFedAt = 0L
    private var stallLogged = false

    /**
     * Pruh se neukazuje přes obrazovky této aplikace. Jinak běží vždy, když je spojení s konzolí:
     * televize při přepnutí na HDMI nemusí oznámit, co je na obrazovce, a hádat to by znamenalo,
     * že překlad nad hrou občas zmizí.
     */
    private fun appAllowed(): Boolean {
        // S posíláním do televize LG se čte pořád: titulky jdou na jinou obrazovku, takže nevadí,
        // že je na tomto zařízení (typicky telefonu) vidět aplikace.
        val allowed = tv != null || (if (demo) UiState.testCard else !UiState.visible)
        if (!allowed && !hiddenForOtherApp) {
            hideAt = 0; overlay?.hide(); resetReading()
        }
        hiddenForOtherApp = !allowed
        return allowed
    }

    // ---------------- menu hry (od 0.97.34, zatím GTA VI) ----------------

    /** Menu ke překladu z profilu hry (jen se zapnutým „Překlad menu hry“); null = menu se nečte. */
    @Volatile private var menuSpec: cz.recaption.core.MenuSpec? = null
    private var menuCheckAt = 0L
    /** V pruhu je přeložené menu – titulky se mezitím nečtou (v menu žádné nejsou). */
    private var menuShown = false
    private var menuKey = ""
    /** Otevřené menu v čase: položky se doplňují, vybraná se pozná i podle chybějící (viz [cz.recaption.core.MenuTracker]). */
    @Volatile private var menuTracker: cz.recaption.core.MenuTracker? = null
    /** Překlady popisů a položek menu (jen v paměti; pevné překlady položek jsou v profilu hry). */
    private val menuWords = HashMap<String, String>()
    /** Nastavení hry: popis vybrané volby je ve vlastním okně (od 0.97.58) – co v něm je. */
    private var menuDescKey = ""
    /** Tlačítka v patičce Nastavení, která jsou teď přeložená („RESTORE ALL|BACK“; prázdné = nic). */
    private var menuFooterKey = ""
    /** Jas pásů seznamu voleb Nastavení ([cz.recaption.core.GameMenu.WATCH]) – změna = číst hned. */
    private var menuWatch: IntArray? = null
    private var menuReadNow = false
    /** Zvýší se při každém [dropMenu] (i z hlavního vlákna při pauze) – čtení menu, které mezitím doběhlo, se zahodí. */
    @Volatile private var menuGen = 0

    /**
     * Jednou za 1,5 s (v menu za 0,4 s) přečte horní část obrazu a pozná menu hry ([cz.recaption.core.GameMenu]).
     * Nečte se, když je v pruhu titulek (hraje se). Vrací true, když je menu vidět – pak se titulky nečtou.
     * Od 0.97.58 se při otevřeném menu na každém novém snímku změří jas známých položek ([menuFast]) – posun výběru
     * se ukáže hned, bez čtečky; v Nastavení posun bílého pruhu v seznamu voleb spustí čtení hned (nový popis).
     */
    private suspend fun checkMenu(now: Long, ov: SubtitleOverlay): Boolean {
        val spec = menuSpec ?: return false
        val tracker = menuTracker ?: cz.recaption.core.MenuTracker(spec).also { menuTracker = it }
        // čtení zavřeného menu běží vedle ([startMenuLook]) – dokud nedoběhne, nic dalšího
        if (menuJob?.isActive == true) return menuShown
        if (menuShown) menuFast(now, ov, spec, tracker)
        // otevření menu se pozná z jasu na každém snímku (od 0.97.132, majitel: překlad menu naskakoval pomalu) – čte se hned,
        // i když je v pruhu titulek; po planém poplachu nejdřív za 3 s (titulky se tím nebrzdí)
        var gateHit = false
        if (!menuShown && now - menuGateMissAt > 3000 && now - menuCheckAt >= 300) {
            // i s výběrem v panelu mise vpravo (od 0.97.140 – menu zleva pak pruh nemá)
            reader?.grayNow(GATES)?.let { g -> if (gate(g)) { gateHit = true; menuReadNow = true } }
        }
        if (!menuShown) {
            // zavřené menu: znak menu z jasu → čte se hned, ale ve vlastní úloze (titulky tohoto kola na čtení nečekají);
            // hledání naslepo (bez znaku, 1× za 1,5 s) až po titulcích v [startMenuLook]
            if (gateHit) { menuCheckAt = now; menuReadNow = false; menuJob = sideLaunch(cz.recaption.core.SideReads.MENU, now) { readMenu(now, ov, spec, tracker, true) } }
            return false
        }
        if (!menuReadNow && now - menuCheckAt < 400) return true
        menuCheckAt = now
        menuReadNow = false
        return readMenu(now, ov, spec, tracker, false)
    }

    @Volatile private var menuJob: kotlinx.coroutines.Job? = null

    /**
     * Hledání zavřeného menu naslepo (menu bez znaku z jasu – Nastavení, mapa): jako dřív nejvýš 1× za 1,5 s a jen když
     * v pruhu není titulek, ale až po čtení titulků a ve vlastní úloze – čtení větší části obrazu už nezdrží titulek.
     */
    private fun startMenuLook(now: Long, ov: SubtitleOverlay) {
        val spec = menuSpec ?: return
        if (menuShown || menuJob?.isActive == true || ov.isShown) return
        if (!menuReadNow && now - menuCheckAt < 1500) return
        if (!sideMay(cz.recaption.core.SideReads.MENU, now)) return
        val tracker = menuTracker ?: cz.recaption.core.MenuTracker(spec).also { menuTracker = it }
        menuCheckAt = now
        menuReadNow = false
        menuJob = sideLaunch(cz.recaption.core.SideReads.MENU, now) { readMenu(now, ov, spec, tracker, false) }
    }

    /** Přečte menu hry z obrazu a ukáže překlad; true = menu je vidět. Zavřené menu se čte ve vlastní úloze, otevřené v kole čtení. */
    private suspend fun readMenu(now: Long, ov: SubtitleOverlay, spec: cz.recaption.core.MenuSpec, tracker: cz.recaption.core.MenuTracker, gateHit: Boolean): Boolean {
        // v Nastavení i spodní část: u voleb s obrázkem je popis až pod ním (~64–75 % výšky, tester P2ZT-VKQ5)
        val inSettings = menuShown && tracker.view?.kind == "settings"
        // celá výška, když je menu otevřené: dole je patička s tlačítky (RESTORE ALL, SELECT, BACK; od 0.97.111, menu zleva od 0.97.113)
        val crop = reader?.grabMenu(if (menuShown) 1f else 0.6f) ?: return menuShown
        val gen = menuGen
        val result = try { recognizer.process(InputImage.fromBitmap(crop.bmp, 0)).await() } catch (_: Exception) { return menuShown }
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val bb = l.boundingBox ?: return@mapNotNull null
            // slova s vlastním rámečkem a jasem: záložky Nastavení čtečka spojí do jednoho řádku (od 0.97.58)
            // jas slova = pás těsně pod písmeny (vybraná záložka = bílý rámeček s tmavým písmem, od 0.97.102 – na písmenech
            // vycházela nejsvětlejší krátká hustá slova, v překladu pak pořád „Zvuk“)
            val words = l.elements.mapNotNull { e -> e.boundingBox?.let { b ->
                val u = cz.recaption.core.GameMenu.under(b.top, b.bottom)
                cz.recaption.core.MenuWord(e.text, b.left, b.top, b.right, b.bottom, meanGray(crop, android.graphics.Rect(b.left, u[0], b.right, u[1])),
                    // nejsvětlejší body písmen – vybraná záložka mapy / Briefu je růžové písmo (od 0.97.144); jen v horních 12 %
                    if (b.top < crop.fullH * 0.12f) peakGray(crop, b) else -1) } }
            cz.recaption.core.MenuLine(l.text, bb.left, bb.top, bb.right, bb.bottom, meanGray(crop, bb), words)
        }
        // pauza nebo zavření menu během čtení → výsledek zahodit (jinak by menu zůstalo v pruhu celou pauzu)
        if (paused || gen != menuGen) return false
        // jas pásu pod lištou Nastavení po sloupcích (vybraná záložka = bílý rámeček, od 0.97.108) – jen pár řádků výřezu
        val bar = { y0: Int, y1: Int ->
            val a = y0.coerceIn(0, crop.h - 1); val b = y1.coerceIn(a + 1, crop.h)
            IntArray(crop.w) { x -> var sum = 0; for (y in a until b) sum += crop.gray[y * crop.w + x] and 0xFF; sum / (b - a) }
        }
        val read = cz.recaption.core.GameMenu.read(lines, crop.fullW, crop.fullH, spec, inSettings, bar)
        val (view, changed) = tracker.update(read, now, now)
        if (gateHit && view == null) menuGateMissAt = now
        if (view == null) {
            if (menuShown) { dropMenu(); tp("MENU|off|" + lines.take(10).joinToString(" / ") { it.text.take(30) }) }
            return false
        }
        if (!menuShown) { resetReading(); dropObjective(); clearQueue(); dropCorner(); dropWorld(); dropPhone(); dropNotice(); dropTip() }
        menuShown = true
        hideAt = 0
        if (changed || view.listKey != menuKey) showMenuList(view, spec, ov, gen)
        if (gen != menuGen) return false
        if (view.kind == "settings" || view.kind == "photo") {
            // popis zvlášť ve vlastním okně přes anglický popis (od 0.97.58; Fotostudio pod popisem, od 0.97.139)
            if (view.descKey + "#" + view.listKey2 != menuDescKey) {
                menuDescKey = view.descKey + "#" + view.listKey2
                val box = view.descBox
                if (box == null || (view.title == null && view.description == null)) ov.hideMenuDesc()
                else {
                    // název volby: vlastní herní překlad z profilu hry (od 0.97.107, „Targeting“ = Míření), jinak překladač
                    val title = view.title?.let { spec.word(lang, it) ?: cz.recaption.core.MenuTerms.fix(menuTranslate(it, deepl = false), lang) }
                        // nadpis je ve hře verzálkami – překlad taky (od 0.97.108, přání majitele: kopírovat vzhled)
                        ?.uppercase(java.util.Locale.forLanguageTag(lang.code))
                    // „camera“ je v Nastavení kamera (pohled), ne fotoaparát (od 0.97.111, přání majitele)
                    // menu jen offline + vlastní překlady (od 0.97.113, majitel: „menu bez DeepL je pěkně svižné“)
                    // popis: celý známý, jinak po odstavcích – známé vlastním překladem, zbytek překladačem (od 0.97.114)
                    val desc = view.description?.let { d ->
                        spec.word(lang, d) ?: spec.cover(lang, d)?.map { (en, tr) ->
                            tr ?: cz.recaption.core.MenuTerms.fix(menuTranslate(en, deepl = false), lang)
                        }?.joinToString(" ") ?: cz.recaption.core.MenuTerms.fix(menuTranslate(d, deepl = false), lang)
                    }
                    // anglický popis do záznamu – podle balíků od testerů se dají psát vlastní herní překlady
                    view.description?.let { tp("MENU|popis|" + view.title + "|" + it.take(160)) }
                    if (paused || gen != menuGen) return false
                    // seznam pod popisem (barvy): každá položka zvlášť, pevný překlad z profilu, jinak překladač
                    val items = view.list.map { spec.word(lang, it) ?: cz.recaption.core.MenuTerms.fix(menuTranslate(it, deepl = false), lang) }
                    if (paused || gen != menuGen) return false
                    ov.showMenuDesc(title, desc, box, view.descLine, titleBox = view.titleBox, titleLine = view.titleLine,
                        list = items, listBox = view.listBox, listStep = view.listStep)
                    collectTexts(cz.recaption.core.LineBank.MENU, listOf(view.title, view.description) + view.list, "menu-popis")
                }
            }
        } else if (menuDescKey.isNotEmpty()) { menuDescKey = ""; ov.hideMenuDesc() }
        // tlačítka v patičce Nastavení (od 0.97.111, přání majitele): překlad přes anglický název, ikona hry zůstane vidět
        val fk = view.footer.joinToString("|") { it.first }
        if (fk != menuFooterKey) {
            menuFooterKey = fk
            if (fk.isEmpty()) { ov.hideMenuFooter(); ov.showMenuLabels(1, emptyList()) }
            else {
                val loc = java.util.Locale.forLanguageTag(lang.code)
                val items = view.footer.map { (n, b) ->
                    (cz.recaption.core.GameMenu.footerWord(lang.code, n) ?: menuTranslate(n.lowercase().replaceFirstChar { it.uppercase() }, deepl = false)).uppercase(loc) to b
                }
                if (paused || gen != menuGen) return false
                // od 0.97.115: v menu zleva nad tlačítky, v Nastavení vlevo od nich (přes původní se kryly)
                if (menuLabelsOk()) ov.showMenuLabels(1, cz.recaption.core.GameMenu.footerLabels(view.footer, items.map { it.first }, view.kind == "settings", spec.layout.footerRight))
                else ov.showMenuFooter(items)
                collectTexts(cz.recaption.core.LineBank.MENU, view.footer.map { it.first }, "menu-paticka")
            }
        }
        return true
    }

    /**
     * Vybraná položka podle jasu na novém snímku ([cz.recaption.core.MenuTracker.fast]) – pár set bodů z jasové
     * roviny, žádná bitmapa ani čtečka. V Nastavení se zároveň hlídá seznam voleb: posun pruhu → číst hned.
     */
    /** Kdy znak menu z jasu naposledy spustil čtení, které menu nenašlo (planý poplach) – pak 3 s nic. */
    private var menuGateMissAt = 0L

    /** Políčka jasu znaku menu: menu zleva ([cz.recaption.core.GameMenu.PAUSE_GATE]) a panel mise vpravo (MISSION_GATE). */
    private val GATES = cz.recaption.core.GameMenu.PAUSE_GATE + cz.recaption.core.GameMenu.MISSION_GATE

    private fun gate(g: IntArray): Boolean {
        val n = cz.recaption.core.GameMenu.PAUSE_GATE.size
        if (g.size < n) return false
        val left = g.copyOfRange(0, n)
        return cz.recaption.core.GameMenu.pauseGate(left) || cz.recaption.core.GameMenu.missionGate(g.copyOfRange(n, g.size), left)
    }

    private suspend fun menuFast(now: Long, ov: SubtitleOverlay, spec: cz.recaption.core.MenuSpec, tracker: cz.recaption.core.MenuTracker) {
        // menu zleva: znak menu z jasu zmizel → zavírá se (od 0.97.132)
        if (tracker.view?.kind == "pause") reader?.grayNow(GATES)?.let { tracker.gate(gate(it), now) }
        val rects = tracker.sampleRects()
        val settings = tracker.view?.kind == "settings"
        // panel mise vpravo: výběr podle jasu vlevo od textu položek (od 0.97.140)
        val sideAll = tracker.view?.takeIf { it.kind == "pause" }?.side?.let { cz.recaption.core.GameMenu.sideRects(it) } ?: emptyList()
        val sideIdx = sideAll.indices.filter { sideAll[it].size >= 4 }
        val sideR = if (sideIdx.size >= 2) sideIdx.map { sideAll[it] } else emptyList()
        if (rects.isEmpty() && !settings && sideR.isEmpty()) { if (tracker.vanishing(now)) menuReadNow = true; return }
        val watch = if (settings) cz.recaption.core.GameMenu.WATCH else emptyList()
        val g = reader?.sampleGray(rects + watch + sideR) ?: return
        var sideChanged: cz.recaption.core.MenuView? = null
        if (sideR.isNotEmpty()) {
            val sb = g.copyOfRange(g.size - sideR.size, g.size).toList()
            val sel = cz.recaption.core.GameMenu.selected(sb).let { if (it >= 0) sideIdx[it] else -1 }
            val r = tracker.side(sel, now)
            if (r.changed) sideChanged = r.view
        }
        if (tracker.vanishing(now)) menuReadNow = true
        if (settings) {
            val w = g.copyOfRange(rects.size, g.size)
            val prev = menuWatch
            if (prev == null || cz.recaption.core.GameMenu.watchChanged(prev, w)) { if (prev != null) menuReadNow = true; menuWatch = w }
        }
        if (rects.isEmpty()) { sideChanged?.let { showMenuList(it, spec, ov, menuGen) }; return }
        val (view0, changed0) = tracker.fast(g.copyOfRange(0, rects.size), now)
        // pruh vpravo drží menu otevřené i bez pruhu zleva ([MenuTracker.side] po [MenuTracker.fast])
        if (sideR.isNotEmpty()) tracker.side(tracker.view?.sideSel ?: -1, now)
        val view = if (sideChanged != null) tracker.view else view0
        val changed = changed0 || sideChanged != null
        // bílý pruh zmizel → přečíst hned (zavření menu se pozná do jednoho čtení, od 0.97.117)
        if (tracker.vanishing(now)) menuReadNow = true
        if (view != null && changed) {
            showMenuList(view, spec, ov, menuGen)
            // jiná záložka = jiný seznam voleb i popis → přečíst hned
            if (settings) menuReadNow = true
        }
    }

    /** Menu zleva: seznam vedle menu. Nastavení: jen vybraná záložka těsně nad lištou (od 0.97.58). */
    private suspend fun showMenuList(view: cz.recaption.core.MenuView, spec: cz.recaption.core.MenuSpec, ov: SubtitleOverlay, gen: Int) {
        menuKey = view.listKey
        val l = lang
        val labels = menuLabelsOk()
        if (view.kind == "pause" || view.kind == "photo") {
            val photo = view.kind == "photo"
            val items = view.items.map { spec.near(l, it) ?: menuTranslate(it, deepl = false) }
            // nadpis podmenu / Fotostudia („GAME“ → „HRA“, od 0.97.139) – verzálkami jako ve hře
            val head = view.head?.first?.let { h -> (spec.word(l, h) ?: menuTranslate(cz.recaption.core.GameMenu.titleCase(h), deepl = false)).uppercase(java.util.Locale.forLanguageTag(l.code)) }
            // panel mise vpravo (od 0.97.133): vlastní překlad, „CHAPTER 2“ → „KAPITOLA 2“; verzálky jako originál
            val loc = java.util.Locale.forLanguageTag(l.code)
            val side = view.side.map { (t, _) ->
                val ch = cz.recaption.core.GameMenu.chapter(t)
                val tr = if (ch != null) (spec.word(l, "Chapter 1")?.substringBeforeLast(' ')?.let { "$it $ch" } ?: menuTranslate("Chapter $ch", deepl = false))
                    else spec.word(l, t) ?: menuTranslate(t, deepl = false)
                if (t.none { it.isLowerCase() }) tr.uppercase(loc) else tr
            }
            if (paused || gen != menuGen) return
            synchronized(showLock) {
                showQueue.clear()   // repliky z fronty by menu přepsaly
                if (labels) {
                    // od 0.97.115 (přání majitele): každá položka hned vpravo od originálu, vybraná černě v bílém pruhu hry
                    ov.hide()
                    val list = if (photo) cz.recaption.core.GameMenu.photoLabels(view, items) else cz.recaption.core.GameMenu.pauseLabels(view, items)
                    val all0 = list + cz.recaption.core.GameMenu.sideLabels(view.side, side, bg = if (photo) cz.recaption.core.GameMenu.SETTINGS_BG else 0, selected = view.sideSel) +
                        listOfNotNull(cz.recaption.core.GameMenu.headLabel(view.head, head, if (photo) cz.recaption.core.GameMenu.SETTINGS_BG else 0))
                    ov.showMenuLabels(0, all0)
                    tpLabels("menu", all0)
                } else {
                    // starší aplikace v TV: vedle menu, řádky pod sebou (od 0.97.36), vybraná černě na bílém (od 0.97.111)
                    ov.show(items.mapIndexed { i, s -> Segment(null, s, selected = i == view.selected) }, null, -1f, -1f, box = view.box, step = view.step)
                }
                ++showSeq
            }
        } else {
            val tab = view.items.getOrNull(view.selected)
            val rect = view.rects.getOrNull(view.selected)
            val place = cz.recaption.core.GameMenu.tabPlace(rect)
            // záložky jsou ve hře verzálkami – překlad taky (od 0.97.108)
            val word = tab?.let { spec.word(l, it) ?: menuTranslate(it, deepl = false) }?.uppercase(java.util.Locale.forLanguageTag(l.code))
            // popisky kolem ovladače ve Schématu ovládání (od 0.97.144): vlastní herní překlad, jinak překladač; verzálkami
            val loc = java.util.Locale.forLanguageTag(l.code)
            val controls = view.controls.map { (t, _, _) -> (spec.word(l, t) ?: menuTranslate(cz.recaption.core.GameMenu.titleCase(t), deepl = false)).uppercase(loc) }
            // volby v seznamu vlevo (od 1.0.1): vlastní herní překlad, jinak překladač
            val options = if (labels) view.options.map { (t, _) -> spec.near(l, t) ?: menuTranslate(t, deepl = false) } else emptyList()
            if (paused || gen != menuGen) return
            synchronized(showLock) {
                showQueue.clear()
                if (labels) {
                    // od 0.97.115: bez bílého rámečku, bílé písmo na pozadí 70 % (majitel nechtěl bílé a pod tím černé)
                    ov.hide()
                    val all0 = listOfNotNull(cz.recaption.core.GameMenu.tabLabel(rect, word)) +
                        cz.recaption.core.GameMenu.controlLabels(view.controls, controls) +
                        cz.recaption.core.GameMenu.optionLabels(view.options, options)
                    ov.showMenuLabels(0, all0)
                    tpLabels("settings", all0)
                } else if (word == null || place == null) ov.hide()
                else ov.show(listOf(Segment(null, word, selected = true)), null, -1f, -1f, box = place.first, step = place.second)
                ++showSeq
            }
        }
        collectTexts(cz.recaption.core.LineBank.MENU, view.items + view.head?.first + view.side.map { it.first } + view.controls.map { it.first })
        tp("MENU|${view.kind}|${view.items.joinToString(",")}|vybraná=${view.selected}|popis=${view.title ?: "-"}|krok=${"%.3f".format(java.util.Locale.ROOT, view.step)}")
    }

    /** Menu pryč (zavřené, pauza): pruh i popis schovat. */
    private fun dropMenu() {
        val was = menuShown
        menuGen++
        menuShown = false; menuKey = ""; menuDescKey = ""; menuWatch = null; menuReadNow = false; menuFooterKey = ""
        // nový tracker místo reset(): dropMenu běží i z hlavního vlákna, rozběhnuté čtení si drží starý
        menuTracker = null
        menuBoxDiag.clear()
        if (was) { overlay?.hide(); overlay?.hideMenuDesc(); overlay?.hideMenuFooter(); overlay?.hideMenuLabels() }
    }

    /** Popisky menu na svém místě: bez TV LG/Samsung vždy, s ní jen od aplikace v TV [TvSender.MENU_LABELS_TV] (jinak dřívější pruh). */
    private fun menuLabelsOk(): Boolean {
        if (tv == null) return true
        val v = TvSender.lastSeen?.announce?.version ?: return true
        return TvSender.atLeast(v, TvSender.MENU_LABELS_TV)
    }

    // ---------------- menu vpravo dole (nápovědy tlačítek, zatím GTA VI) ----------------

    /** Seznam vpravo dole z profilu hry – jen s volbou Titulky → Hra → „Menu vpravo dole“ (výchozí vypnuto); null = nečte se. */
    @Volatile private var cornerSpec: cz.recaption.core.CornerSpec? = null
    /** 1 = nad položkami, 2 = přes položky ([Prefs.cornerMenu]). */
    @Volatile private var cornerMode = 0
    @Volatile private var cornerTracker = cz.recaption.core.CornerTracker()
    @Volatile private var cornerJob: kotlinx.coroutines.Job? = null
    @Volatile private var cornerGen = 0
    private var cornerCheckAt = 0L
    private var cornerReadAt = 0L
    private var cornerGate: IntArray? = null
    /** Obdélníky zobrazených položek (zlomky obrazu) – čtení titulků je vynechá; prázdné = nic. */
    @Volatile private var cornerRects: List<FloatArray> = emptyList()

    private fun loadCornerSettings() {
        val mode = Prefs.cornerMenu(this)
        cornerSpec = if (mode > 0) cz.recaption.core.GameProfiles.of(Prefs.gameId(this)).cornerSpec else null
        cornerMode = mode
        cornerGen++
        cornerTracker = cz.recaption.core.CornerTracker()
        cornerRects = emptyList(); cornerGate = null; cornerCheckAt = 0L
        cornerPrint = null; cornerSame = 0
    }

    /**
     * Čtení rohu běží vedle čtení titulků (vlastní výřez a úloha, tick na něj nečeká): bez seznamu nejvýš 1× za 1 s,
     * se seznamem 1× za 0,5 s. Před čtením levná brána – jas sloupce ikon; beze změny a bez seznamu se nečte (nejdéle 3 s).
     */
    private fun startCorner(now: Long, ov: SubtitleOverlay) {
        if (cornerSpec == null || paused || cornerJob?.isActive == true) return
        val shown = cornerTracker.view != null
        if (now - cornerCheckAt < (if (shown) 500 else 1000)) return
        if (!sideMay(cz.recaption.core.SideReads.CORNER, now)) return
        cornerCheckAt = now
        val all = reader?.grayNow(CORNER_PRINT) ?: return
        val gate = all.copyOfRange(0, cz.recaption.core.CornerPrompts.GATE.size)
        val prev = cornerGate
        cornerGate = gate
        // ukázaný seznam: otisk výřezu (ikony i místo textu) dvakrát po sobě beze změny → nečte se (nejdéle 3 s);
        // dvě čtení po změně, protože nový seznam se potvrzuje druhým čtením
        val prevPrint = cornerPrint
        cornerPrint = all
        cornerSame = if (cz.recaption.core.PhoneScreen.changed(prevPrint, all, 8)) 0 else cornerSame + 1
        if (shown && cornerSame >= 2 && now - cornerReadAt < 3000) return
        if (!shown && prev != null && !cz.recaption.core.GameMenu.watchChanged(prev, gate, 20) && now - cornerReadAt < 3000) return
        cornerReadAt = now
        cornerJob = sideLaunch(cz.recaption.core.SideReads.CORNER, now) { checkCorner(ov) }
    }

    /** Otisk výřezu vpravo dole: políčka ikon ([cz.recaption.core.CornerPrompts.GATE]) a dvě políčka textu vlevo od každé. */
    private val CORNER_PRINT: List<FloatArray> = cz.recaption.core.CornerPrompts.GATE.let { g ->
        g + g.flatMap { listOf(floatArrayOf(0.80f, it[1], 0.88f, it[3]), floatArrayOf(0.88f, it[1], 0.96f, it[3])) }
    }
    private var cornerPrint: IntArray? = null
    private var cornerSame = 0

    private suspend fun checkCorner(ov: SubtitleOverlay) {
        val spec = cornerSpec ?: return
        val crop = reader?.grabCorner(cz.recaption.core.CornerPrompts.ZONE_LEFT, cz.recaption.core.CornerPrompts.ZONE_TOP) ?: return
        val gen = cornerGen
        val tracker = cornerTracker
        val result = recognizer.process(InputImage.fromBitmap(crop.bmp, 0)).await()
        if (paused || gen != cornerGen) return
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val bb = l.boundingBox ?: return@mapNotNull null
            val words = l.elements.mapNotNull { e -> e.boundingBox?.let { b ->
                cz.recaption.core.MenuWord(e.text, b.left + crop.x0, b.top + crop.y0, b.right + crop.x0, b.bottom + crop.y0, brightP90(crop, b)) } }
            cz.recaption.core.MenuLine(l.text, bb.left + crop.x0, bb.top + crop.y0, bb.right + crop.x0, bb.bottom + crop.y0, brightP90(crop, bb), words)
        }
        // od 1.0.05 bez hledání ikon tlačítek v obraze: překlad je vlevo vedle originálu a ikona hry je vidět (přání majitele –
        // rychlost a ikony nebyly vždy přesné); ikony z textu (L1, R2) a výchozí ikony položek zůstávají v datech
        val read = cz.recaption.core.CornerPrompts.read(lines, crop.fullW, crop.fullH, spec)
        val (view, changed) = tracker.update(read)
        if (!changed) return
        if (view == null) {
            cornerRects = emptyList()
            ov.hideCorner()
            tp("CORNER|off")
            return
        }
        val l = lang
        val texts = view.items.map { item ->
            // verzálkami jako ve hře (od 0.97.124, přání majitele)
            val tr = (spec.word(l, item.text) ?: menuTranslate(cz.recaption.core.CornerPrompts.titleCase(item.text))).uppercase(java.util.Locale.forLanguageTag(l.code))
            cz.recaption.core.CornerPrompts.line(tr, item) to item.dim
        }
        if (paused || gen != cornerGen) return
        // nic k překladu (jen jméno postavy „LUCIA“ – tečka a L2 ve hře): nezakrývat – nad seznamem se po L2 objeví volby
        // („+♥ ×“), které by překlad nad seznamem schoval (od 0.97.130, dávka S5F6-VDZZ)
        if (view.items.indices.all { i -> texts[i].first.equals(cz.recaption.core.CornerPrompts.line(view.items[i].text, view.items[i]), ignoreCase = true) }) {
            cornerRects = emptyList(); ov.hideCorner(); tp("CORNER|beze zmeny|" + view.items.joinToString(",") { it.text })
            return
        }
        cornerRects = view.items.map { floatArrayOf(it.left, it.top, it.right, it.bottom) }
        ov.showCorner(texts, view, cornerMode)
        collectTexts(cz.recaption.core.LineBank.ROH, view.items.map { it.text })
        tp("CORNER|show|" + view.items.joinToString(",") { it.text + (if (it.suffix.isEmpty()) "" else " " + it.suffix) } +
            "|seda=" + view.items.count { it.dim } + "|step=" + String.format(java.util.Locale.ROOT, "%.3f", view.step) +
            "|ikony=" + view.items.joinToString(",") { it.icon ?: "-" })
    }

    // ---------------- pokyny u předmětů (GTA VI „□ OPEN DOOR“, od 0.97.88) ----------------

    @Volatile private var worldOn = false
    @Volatile private var worldTracker = cz.recaption.core.WorldTracker()
    @Volatile private var worldJob: kotlinx.coroutines.Job? = null
    @Volatile private var worldGen = 0
    private var worldCheckAt = 0L
    /** Kdy naposledy byl v pruhu titulek – pokyny se čtou až chvíli po něm (při rozhovoru se nic navíc nečte). */
    @Volatile private var worldSubtitleAt = 0L

    /** Volba „Pokyny u předmětů“ jen u her, jejichž pokyny známe (zatím GTA VI – stejné písmo jako menu vpravo dole). */
    private fun loadWorldSettings() {
        worldOn = Prefs.worldPrompts(this) && cz.recaption.core.GameProfiles.of(Prefs.gameId(this)).cornerSpec != null
        worldGen++
        worldTracker = cz.recaption.core.WorldTracker()
        worldCheckAt = 0L
        if (!worldOn) overlay?.hideWorld()
    }

    /**
     * Vlastní úloha (tick na ni nečeká), nejvýš 1× za 1 s (s ukázaným pokynem 0,7 s, s titulkem v pruhu 2 s). Na pomalém
     * zařízení jen když v pruhu není titulek a poslední zmizel před víc než 1,5 s – čtení titulků se tím nezpomalí.
     * Ne při menu vpravo dole (čte se).
     */
    private fun startWorld(now: Long, ov: SubtitleOverlay) {
        if (!worldOn || paused || worldJob?.isActive == true) return
        // s rohem se střídá, ale nečeká na něj donekonečna: čtení rohu trvá na pomalejším telefonu ~1 s a běží pořád,
        // takže pokyny se dřív nečetly vůbec (dávka KJ7U-2FHD, od 0.97.137)
        if (cornerJob?.isActive == true && now - worldCheckAt < 2500) return
        // s titulkem v pruhu: na pomalém zařízení (čtení přes 0,3 s, TV) se pokyny nečtou jako dřív – čtení titulků má
        // přednost; na rychlém (telefon ~0,05 s) se čtou dál, jen řidčeji (1× za 2 s). Dřív se při rozhovoru nečetly
        // nikde, a pokyny bývají na obraze právě s titulky (dávka 7N4Y-RTQ8) – testeři je se zapnutou volbou neviděli
        val slow = lastOcrMs > WORLD_SLOW_MS
        if (ov.isShown && slow) { worldSubtitleAt = now; if (worldTracker.current.isNotEmpty()) dropWorld(); return }
        if (slow && now - worldSubtitleAt < 1500) return
        if (now - worldCheckAt < (if (ov.isShown) 2000 else if (worldTracker.current.isEmpty()) 1000 else 700)) return
        if (!sideMay(cz.recaption.core.SideReads.WORLD, now)) return
        worldCheckAt = now
        worldJob = sideLaunch(cz.recaption.core.SideReads.WORLD, now) { checkWorld(ov) }
    }

    private suspend fun checkWorld(ov: SubtitleOverlay) {
        val W = cz.recaption.core.WorldPrompts
        val crop = reader?.grabWorld(W.ZONE_L, W.ZONE_T, W.ZONE_R, W.ZONE_B) ?: return
        val gen = worldGen
        val tracker = worldTracker
        val t0 = SystemClock.uptimeMillis()
        val result = recognizer.process(InputImage.fromBitmap(crop.bmp, 0)).await()
        if (paused || gen != worldGen) return
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val bb = l.boundingBox ?: return@mapNotNull null
            val words = l.elements.mapNotNull { e -> e.boundingBox?.let { b ->
                cz.recaption.core.MenuWord(e.text, b.left + crop.x0, b.top + crop.y0, b.right + crop.x0, b.bottom + crop.y0, -1) } }
            cz.recaption.core.MenuLine(l.text, bb.left + crop.x0, bb.top + crop.y0, bb.right + crop.x0, bb.bottom + crop.y0, brightP90(crop, bb), words)
        }
        val read = W.read(lines, crop.fullW, crop.fullH) { l ->
            W.iconLeft(crop.gray, crop.w, crop.h, l.left - crop.x0, l.top - crop.y0, l.bottom - crop.y0)
        }
        val (prompts, changed) = tracker.update(read)
        if (!changed) return
        if (prompts.isEmpty()) { worldItems = emptyList(); showWorldAll(ov); tp("WORLD|off"); return }
        val texts = prompts.map { p ->
            menuTranslate(cz.recaption.core.CornerPrompts.titleCase(p.text)).uppercase(java.util.Locale.ROOT) to p
        }
        if (paused || gen != worldGen || (ov.isShown && lastOcrMs > WORLD_SLOW_MS)) return
        worldItems = texts
        showWorldAll(ov)
        collectTexts(cz.recaption.core.LineBank.PREDMET, prompts.map { it.text })
        tp("WORLD|show|" + prompts.joinToString(",") { it.text } + "|ocr=" + (SystemClock.uptimeMillis() - t0) + "ms")
    }

    // ---------------- telefon ve hře (GTA VI, od 0.97.95) ----------------

    @Volatile private var phoneOn = false
    @Volatile private var phoneTracker = cz.recaption.core.PhoneTracker()
    @Volatile private var phoneJob: kotlinx.coroutines.Job? = null
    @Volatile private var phoneGen = 0
    @Volatile private var phoneShown = false
    private var phoneCheckAt = 0L
    private var phoneReadAt = 0L
    private var phoneGrid: IntArray? = null
    @Volatile private var phoneDiag = ""
    /** Pevné překlady profilu hry (REMINDERS → Připomínky) pro kartu upozornění ([cz.recaption.core.PhoneCard]). */
    @Volatile private var phoneSpec: cz.recaption.core.CornerSpec? = null
    /** Kde telefon hledat (od 1.00-rc21): kolem posledního nalezeného, polohy z profilu hry, jinak široký pás. */
    @Volatile private var phoneZone = cz.recaption.core.PhoneZone(emptyList())

    /**
     * Sledování telefonu (od 1.00-rc21): [phoneBase] = čtení, podle kterého je překlad položený; [phoneRef] = jasový profil
     * kolem telefonu ze snímku posledního čtení, [phoneRefX]/[phoneRefY] = kde byl telefon na tom snímku proti [phoneBase].
     * Mezi čteními úloha [phoneFollowJob] (~10× za s) měří posun profilu a [phoneFollow] posouvá překlad plynule.
     */
    @Volatile private var phoneBase: cz.recaption.core.PhoneRead? = null
    @Volatile private var phoneRef: cz.recaption.core.PhoneMotion.Profile? = null
    @Volatile private var phoneRefX = 0f
    @Volatile private var phoneRefY = 0f
    @Volatile private var phoneFollow = cz.recaption.core.PhoneFollow(cz.recaption.core.PhoneFollow.PANEL)
    @Volatile private var phoneFollowJob: kotlinx.coroutines.Job? = null

    /** Volba „Překlad telefonu“ jen u her, jejichž telefon známe (zatím GTA VI). */
    /** Překlad přímo přes text v telefonu ([Prefs.phoneOver]), jinak panel vedle. */
    @Volatile private var phoneOver = false
    /** Panel na volné straně obrazu ([Prefs.phoneFree], od 1.00) – jen bez [phoneOver]. */
    @Volatile private var phoneFree = false
    private val phoneFreeSide = cz.recaption.core.PhoneFreeSide()
    /** Místo panelu na volné straně z posledního ukázání (posun [phoneFollow] je od něj); null = panel vedle telefonu. */
    @Volatile private var phoneFreeBase: FloatArray? = null
    /** Ukázaný je seznam konverzací (náhledy ve výšce svých řádků), ne panel. */
    @Volatile private var phoneFreeList = false

    /** Postavy profilu hry (jméno → žena?) pro úkol: oprava useknutého jména a 4. pád („Následuj Briana“, od 0.97.115). */
    @Volatile private var objCast: Map<String, Boolean> = emptyMap()

    private fun loadPhoneSettings() {
        val profile = cz.recaption.core.GameProfiles.of(Prefs.gameId(this))
        objCast = profile.characters.flatMap { c -> c.allNames.map { it to (c.gender == cz.recaption.core.Gender.F) } }.toMap()
        phoneSpec = profile.cornerSpec
        phoneOn = Prefs.phonePanel(this) && phoneSpec != null
        phoneOver = Prefs.phoneOver(this)
        phoneFree = !phoneOver && Prefs.phoneFree(this)
        phoneZone = cz.recaption.core.PhoneZone(profile.phoneSpots)
        phoneSpots = profile.phoneSpots.map { it.box }
        phoneOpen.reset()
        overlay?.phoneColors(profile.phoneOwn, profile.phoneIn)
        phoneGen++
        phoneTracker = cz.recaption.core.PhoneTracker()
        phoneCheckAt = 0L; phoneGrid = null
        resetPhoneFollow()
        if (!phoneOn && phoneShown) { phoneShown = false; overlay?.hidePhone() }
    }

    private fun resetPhoneFollow() {
        phoneBase = null; phoneRef = null; phoneRefX = 0f; phoneRefY = 0f
        phoneFreeSide.reset(); phoneFreeBase = null; phoneFreeList = false
        phoneFollow = cz.recaption.core.PhoneFollow(if (phoneOver) cz.recaption.core.PhoneFollow.OVER else cz.recaption.core.PhoneFollow.PANEL)
        overlay?.phoneShift(0f, 0f)
    }

    /**
     * Vlastní úloha (tick na ni nečeká), nejvýš 1× za 1 s (ukázaný telefon 0,6 s); obsah pásu se pozná levně z mřížky jasu
     * ([PhoneScreen.GRID]) – beze změny se znovu čte nejdřív po 3 s. Čte se i během rozhovoru (zprávy jdou s titulky), ale jen s volbou.
     */
    private fun startPhone(now: Long, ov: SubtitleOverlay) {
        if (!phoneOn || paused || phoneJob?.isActive == true) return
        // ukázaný telefon se čte častěji – telefon se při chůzi hýbe a nová zpráva má přijít hned
        if (now - phoneCheckAt < (if (phoneShown) 600 else 1000)) return
        // brána „telefon je otevřený“ ([cz.recaption.core.PhoneGate]): ovládání telefonu vpravo dole a světlý displej v poloze
        // z profilu – jen pár bodů jasu. Bez ní se telefon nečte vůbec (dřív se při jízdě četla velká část obrazu a jako
        // telefon se překládaly nápisy nad silnicí, dávka CW8G-3SHU). Do záznamu jen změna stavu a důvod.
        val r = reader ?: return
        val G = cz.recaption.core.PhoneGate
        val fw = r.frameWidth; val fh = r.frameHeight
        if (fw <= 0 || fh <= 0) return
        val (iconBox, iconN) = G.iconBox(fw, fh)
        // držený čitelný předmět (plakát, leták – ovládání „PUT DOWN“ místo BACK/EXIT) má stejnou polohu a tvar jako
        // telefon, ale telefon to není (dávky 5DY4-ZRGC, MT9Z-KGY9 – appka ho četla a překládala jako telefon)
        val holding = cz.recaption.core.CornerPrompts.holdingItem(cornerTracker.view)
        val icon = if (holding) false else (r.grayGrid(iconBox, iconN, iconN)?.let { G.icon(it, iconN) } ?: return)
        val quad = if (!icon) null else r.grayGrid(cz.recaption.core.PhoneZone.WIDE, G.COLS, G.ROWS)?.let { G.quad(it, fw, fh, phoneSpots) }
        if (phoneOpen.update(icon, quad)) tp("PHONEP|brana|" + (if (phoneOpen.open) "otevreny" else "zavreny|" + phoneOpen.reason))
        val box = phoneOpen.box
        if (!phoneOpen.open || box == null) {
            phoneCheckAt = now; phoneGrid = null
            // telefon zavřený: ukázaný překlad pryč hned, bez čtení
            if (phoneShown || phoneTracker.waiting) { dropPhone(); tp("PHONEP|off|brana") }
            return
        }
        if (!sideMay(cz.recaption.core.SideReads.PHONE, now)) return
        phoneCheckAt = now
        // čte se jen výřez kolem telefonu; jeho otisk beze změny → znovu nejdřív po 3 s
        val zone = phoneZone.next(now, G.zone(box))
        val grid = r.grayNow(G.grid(zone)) ?: return
        val prev = phoneGrid
        phoneGrid = grid
        // rozpracovaný obsah (čeká na potvrzující čtení) se čte znovu hned – dřív až po 3 s a překlad se o to zpozdil
        if (!cz.recaption.core.PhoneScreen.changed(prev, grid) && !phoneTracker.waiting && now - phoneReadAt < 3000) return
        phoneReadAt = now
        phoneJob = sideLaunch(cz.recaption.core.SideReads.PHONE, now) { checkPhone(ov, zone) }
    }

    /** Brána „telefon je otevřený“ a polohy telefonu z profilu hry (zlomky obrazu). */
    private val phoneOpen = cz.recaption.core.PhoneOpen()
    @Volatile private var phoneSpots: List<FloatArray> = emptyList()

    /** Řádek čtečky s natočením z rohů (od 1.00-rc21 – telefon v ruce při chůzi je natočený). */
    private fun phoneLine(l: com.google.mlkit.vision.text.Text.Line, crop: StreamReader.MenuCrop): cz.recaption.core.MenuLine? {
        val bb = l.boundingBox ?: return null
        val angle = l.cornerPoints?.takeIf { it.size >= 2 }?.let { p ->
            val dx = (p[1].x - p[0].x).toFloat()
            if (dx > 8f) (p[1].y - p[0].y) / dx else 0f
        } ?: 0f
        return cz.recaption.core.MenuLine(l.text, bb.left + crop.x0, bb.top + crop.y0, bb.right + crop.x0, bb.bottom + crop.y0, -1, angle = angle)
    }

    /** Jasový profil kolem telefonu z výřezu, ze kterého se četlo (týž snímek jako čtení); null = telefon mimo výřez. */
    private fun cropProfile(c: StreamReader.MenuCrop, phone: FloatArray): cz.recaption.core.PhoneMotion.Profile? {
        val a = cz.recaption.core.PhoneMotion.area(phone)
        val x0 = (a[0] * c.fullW).toInt().coerceIn(c.x0, c.x0 + c.w - 1); val x1 = (a[2] * c.fullW).toInt().coerceIn(x0 + 1, c.x0 + c.w)
        val y0 = (a[1] * c.fullH).toInt().coerceIn(c.y0, c.y0 + c.h - 1); val y1 = (a[3] * c.fullH).toInt().coerceIn(y0 + 1, c.y0 + c.h)
        if (x1 - x0 < 16 || y1 - y0 < 16) return null
        val box = floatArrayOf(x0.toFloat() / c.fullW, y0.toFloat() / c.fullH, x1.toFloat() / c.fullW, y1.toFloat() / c.fullH)
        return cz.recaption.core.PhoneMotion.profile(box, x1 - x0, y1 - y0) { x, y -> c.gray[(y0 - c.y0 + y) * c.w + (x0 - c.x0 + x)] and 0xFF }
    }

    private suspend fun checkPhone(ov: SubtitleOverlay, zone: FloatArray) {
        val S = cz.recaption.core.PhoneScreen
        val crop = reader?.grabPhone(zone[0], zone[1], zone[2], zone[3]) ?: return
        val gen = phoneGen
        val tracker = phoneTracker
        val t0 = SystemClock.uptimeMillis()
        val result = recognizer.process(InputImage.fromBitmap(crop.bmp, 0)).await()
        if (paused || gen != phoneGen) return
        val ocrMs = SystemClock.uptimeMillis() - t0
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { phoneLine(it, crop) }
        fun gray(l: cz.recaption.core.MenuLine) = S.samples(crop.gray, crop.w, crop.h, l.left - crop.x0, l.top - crop.y0, l.right - crop.x0, l.bottom - crop.y0)
        // otevřená aplikace (tmavé písmo na světlém), jinak karta upozornění na domovské obrazovce („Reminders / 4 Notifications“)
        // nápověda ovládání vpravo dole do telefonu nepatří ([S.controlHint]); upozornění na SMS dole níž dostává všechny řádky
        val own = lines.filterNot { S.controlHint(it, crop.fullW, crop.fullH) }
        val read00 = S.read(own, crop.fullW, crop.fullH, { l -> S.whiteCard(gray(l)) }) { l -> S.darkText(gray(l)) }
            ?: cz.recaption.core.PhoneCard.read(own, crop.fullW, crop.fullH) { l -> cz.recaption.core.PhoneCard.light(gray(l)) }
        // upozornění na SMS vpravo dole (bez telefonu v ruce) překládá [checkNotice] na jeho místě – telefon to není
        val read0 = read00?.takeIf { r -> !(r.top > 0.7f && cz.recaption.core.PhoneNotice.sms(lines, crop.fullW, crop.fullH) != null) }
        phoneZone.found(read0?.let { floatArrayOf(it.left, it.top, it.right, it.bottom) }, SystemClock.uptimeMillis())
        val (paras, changed) = tracker.update(read0?.paragraphs ?: emptyList())
        // proč se překlad (ne)ukázal – jen při změně stavu, bez textu (dávka NDNS-VQZ7: ze záznamu nešlo poznat, kde to vázlo);
        // od 1.00-rc21 i jestli se četl jen výřez kolem telefonu a jestli byl telefon natočený
        val diag = "PHONEP|read|lines=${lines.size}|paras=${read0?.paragraphs?.size ?: 0}|" +
            (if (read0 == null) "none" else if (changed) "new" else if (tracker.waiting) "wait" else "same") +
            (if (zone !== cz.recaption.core.PhoneZone.WIDE) "|vyrez" else "") + (if (read0 != null && read0.tilt != 0f) "|naklon" else "")
        if (diag != phoneDiag) { phoneDiag = diag; tp(diag) }
        // stejný obsah, telefon se jen pohnul: překlad se posune (bez nového překladu) a profil pro sledování se obnoví
        if (!changed) {
            val base = phoneBase
            // panel na volné straně: telefonu se nesleduje, jen když přejde na druhou stranu (nebo výrazně výš / níž),
            // panel tam plynule dojede ([PhoneFreeSide] drží místo při malém pohybu)
            val fb = phoneFreeBase
            if (phoneFree && fb != null) {
                if (read0 != null && base != null && phoneShown) {
                    phoneFreeSide.place(floatArrayOf(read0.left, read0.top, read0.right, read0.bottom))?.let { nb ->
                        val dy = if (phoneFreeList) cz.recaption.core.PhoneMotion.delta(base, read0).second else nb[1] - fb[1]
                        phoneFollow.target(nb[0] - fb[0], dy)
                    }
                }
                return
            }
            if (read0 != null && base != null && phoneShown && !read0.card && !base.card) {
                val (dx, dy) = cz.recaption.core.PhoneMotion.delta(base, read0)
                cropProfile(crop, floatArrayOf(read0.left, read0.top, read0.right, read0.bottom))?.let { p ->
                    phoneRef = p; phoneRefX = dx; phoneRefY = dy
                }
                phoneFollow.target(dx, dy)
            }
            return
        }
        // vlastní / příchozí bublina podle barvy (od 0.97.114) – jeden barevný snímek jen při změně obsahu
        phoneBlankImages = emptyList()
        val read = read0?.let { r -> if (r.card || r.feed) r else withPhoneColors(r) }
        if (paras.isEmpty() || read == null) {
            if (phoneShown) { phoneShown = false; ov.hidePhone(); tp("PHONEP|off") }
            resetPhoneFollow()
            return
        }
        // odstavce se překládají souběžně (od 1.00-rc21 – dřív jeden po druhém, konverzace SMS v autě se ukázala až ~2 s po
        // přečtení); co už bylo přeložené, vrátí paměť [menuWords] hned
        val texts = if (read.card) paras.map { cardTranslate(it) }
            // (odstavce z [read] – barvy mohly přidat bubliny jen s emoji)
            else kotlinx.coroutines.coroutineScope { read.paragraphs.mapIndexed { i, p -> async { phoneParagraph(read, i, p) } }.map { it.await() } }
        // u karty upozornění se překládá i nadpis (Reminders → Připomínky), jinak jméno kontaktu zůstává
        val title = if (read.card) read.title?.let { cardTranslate(it) } else read.title
        if (paused || gen != phoneGen) return
        // nové místo překladu: dosavadní posun se přepočte, ať nic neskočí (překlad pak dojede plynule); panel na druhé
        // straně telefonu se položí rovnou
        val old = phoneBase
        val isList = !read.card && read.names.count { it != null } >= cz.recaption.core.PhoneScreen.LIST_MIN
        // panel na volné straně (od 1.00): místo z [PhoneFreeSide]; null = telefon přes střed → panel vedle jako dřív
        val freeBox = if (phoneFree) phoneFreeSide.place(floatArrayOf(read.left, read.top, read.right, read.bottom)) else null
        val oldFree = phoneFreeBase
        if (freeBox != null && oldFree != null && old != null && phoneShown && isList == phoneFreeList) {
            // dosavadní místo se přepočte na nové – přesun na druhou stranu dojede plynule, malý rozdíl nic nehýbe
            val dy = if (isList) -cz.recaption.core.PhoneMotion.delta(old, read).second else oldFree[1] - freeBox[1]
            phoneFollow.rebase(oldFree[0] - freeBox[0], dy)
        } else if (freeBox == null && oldFree == null && old != null && phoneShown && !old.card && !read.card &&
            (S.panel(old)[0] < old.left) == (S.panel(read)[0] < read.left)) {
            val (dx, dy) = cz.recaption.core.PhoneMotion.delta(old, read)
            phoneFollow.rebase(-dx, -dy)
        } else phoneFollow.reset()
        phoneFreeBase = freeBox; phoneFreeList = isList
        val side = freeBox ?: S.panel(read)
        phoneBase = read
        phoneRef = if (read.card) null else cropProfile(crop, floatArrayOf(read.left, read.top, read.right, read.bottom))
        phoneRefX = 0f; phoneRefY = 0f
        ov.phoneShift(phoneFollow.x, phoneFollow.y)
        phoneShown = true
        if (phoneOver && !read.card) {
            // přímo přes původní text v telefonu (od 0.97.110): každý odstavec na svém místě
            val items = texts.indices.mapNotNull { i -> read.boxes.getOrNull(i)?.takeIf { texts[i].isNotBlank() }?.let { texts[i] to it } }
            ov.showPhoneOver(items, read.lineH, side)
        } else if (isList) {
            // seznam konverzací (od 1.00-rc21, majitel: „náhledy sms by to chtělo lépe pozičně, teď je to moc u sebe“): každý
            // přeložený náhled vedle telefonu ve výšce svého řádku, ne natěsno pod sebou v panelu
            val items = texts.indices.mapNotNull { i ->
                read.boxes.getOrNull(i)?.takeIf { texts[i].isNotBlank() }?.let { b -> texts[i] to floatArrayOf(side[0], b[1], side[2], b[3]) }
            }
            ov.showPhoneOver(items, read.lineH, side, beside = true)
        } else {
            // panel vedle telefonu: jméno nahoře se nepřekládá (kontakt), zprávy po odstavcích se svou stranou (bublina vlevo / vpravo);
            // bez obrázků a čar místo nich (od 0.97.110, přání majitele – panel je přehlednější)
            // bublina jen s emoji (od 1.00-rc21): bez textu, s obrázkem vyříznutým ze hry
            val pics = phoneBlankImages
            val keep = texts.indices.filter { i -> read.sides.getOrElse(i) { 's' } != 'c' && (texts[i].isNotBlank() || pics.getOrNull(i) != null) }
            val msgs = keep.map { i -> texts[i] to read.sides.getOrElse(i) { 's' } }
            ov.showPhone(title, msgs, side, keep.map { pics.getOrNull(it) }, free = freeBox != null)
        }
        startPhoneFollow(ov)
        tp("PHONEP|show|odstavcu=${paras.size}|znaku=${paras.sumOf { it.length }}|ocr=${ocrMs}ms|celkem=${SystemClock.uptimeMillis() - t0}ms")
        // originál a překlad do záznamu (od 0.97.134 – „chybělo slovo na konci“ se podle záznamu nedalo dohledat)
        val src = if (read.card) paras else read.paragraphs
        collectTexts(cz.recaption.core.LineBank.TELEFON, if (read.card) src + read.title else src)
        tp("PHONEP|tr|" + src.indices.joinToString(" ¦ ") { i -> src[i].take(90) + " → " + texts.getOrElse(i) { "" }.take(90) }.take(900))
    }

    /** Překlad jednoho odstavce telefonu podle jeho druhu (zpráva, náhled v seznamu, příloha, příspěvek). */
    private suspend fun phoneParagraph(read: cz.recaption.core.PhoneRead, i: Int, p: String): String {
        val name = read.names.getOrNull(i)
        // seznam konverzací: jméno kontaktu zůstává, překládá se jen náhled zprávy (náhled jen z emoji = samotné jméno)
        if (name != null) return if (p.isBlank()) name else name + ": " + menuTranslate(p)
        if (p.isBlank()) return p   // prázdný = čára místo obrázku nebo bublina jen s emoji
        // zpráva jen z otazníků / vykřičníků („??“) zůstává, jak je
        if (cz.recaption.core.PhoneScreen.marks(p)) return p.trim()
        // příloha pod zprávou (místo na mapě, odkaz na článek): bez rodu mluvčího, v panelu se značkou
        if (read.attach.getOrNull(i) == true) return (if (phoneOver) "" else "› ") + menuTranslate(p)
        // příspěvek v sociální síti: hashtagy se nepřekládají (z „#Neco“ vznikala nesmyslná slova), zůstanou za překladem
        if (read.feed) return cz.recaption.core.PhoneScreen.hashtags(p).let { (body, tags) ->
            listOf(if (body.count { it.isLetter() } >= 3) menuTranslate(body) else body, tags).filter { it.isNotBlank() }.joinToString(" ") }
        // příchozí zprávy (bublina vlevo) píše kontakt z nadpisu → rod v 1. osobě podle něj („Nora: …neviděla“);
        // vlastní zprávy píše postava hráče – rod neznáme, zůstanou jak je překladač napsal
        return menuTranslate(p, speaker = read.title?.takeIf { read.sides.getOrNull(i) == 'l' })
    }

    /**
     * Sledování telefonu mezi čteními (od 1.00-rc21, majitel: při chůzi se telefon naklání, v autě pohupuje): ~10× za s
     * jasový profil kolem telefonu proti profilu z posledního čtení → posun → [cz.recaption.core.PhoneFollow] (plynule,
     * malé pohupování panel nehýbe). Nesedí-li profil (obsah se mění, telefon zmizel), překlad stojí do dalšího čtení.
     */
    private fun startPhoneFollow(ov: SubtitleOverlay) {
        if (phoneFollowJob?.isActive == true) return
        phoneFollowJob = scope.launch {
            while (isActive && phoneShown && phoneOn && !paused) {
                // panel na volné straně se za pohybem telefonu nehýbe (jen dojíždí přesun z [checkPhone])
                val ref = if (phoneFreeBase != null) null else phoneRef
                val cur = ref?.let { reader?.grayProfile(it.box) }
                val sh = if (ref != null && cur != null) cz.recaption.core.PhoneMotion.shift(ref, cur) else null
                // velký skok profilu = jiný obsah (rolování), ne pohyb – počká na čtení
                if (sh != null && kotlin.math.abs(sh.first) < 0.08f && kotlin.math.abs(sh.second) < 0.08f)
                    phoneFollow.target(phoneRefX + sh.first, phoneRefY + sh.second)
                if (phoneFollow.step()) ov.movePhone(phoneFollow.x, phoneFollow.y)
                delay(100)
            }
        }
    }

    /**
     * Barvy telefonu z jednoho barevného snímku (nejvýš 400 ms; nepřijde = beze změny, strany podle odsazení): modrost pozadí
     * každého odstavce (vlastní / příchozí bublina, od 0.97.114) a od 1.00-rc21 vlastní bubliny bez textu (emoji) – ty se
     * vyříznou jako obrázek ([phoneBlankImages]).
     */
    private fun withPhoneColors(r: cz.recaption.core.PhoneRead): cz.recaption.core.PhoneRead {
        val S = cz.recaption.core.PhoneScreen
        val still = reader?.colorStill(960, 400) ?: return r
        try {
            val bw = still.width; val bh = still.height
            val blue = r.boxes.map { b ->
                if (b == null) return@map null
                val x0 = (b[0] * bw).toInt().coerceIn(0, bw - 1); val x1 = (b[2] * bw).toInt().coerceIn(x0 + 1, bw)
                val y0 = (b[1] * bh).toInt().coerceIn(0, bh - 1); val y1 = (b[3] * bh).toInt().coerceIn(y0 + 1, bh)
                val sx = maxOf(1, (x1 - x0) / 40); val sy = maxOf(1, (y1 - y0) / 8)
                val px = ArrayList<Int>(400)
                var y = y0
                while (y < y1) { var x = x0; while (x < x1) { px += still.getPixel(x, y); x += sx }; y += sy }
                S.blueness(px.toIntArray())
            }
            // bubliny příchozích zpráv: okraj hned vlevo od textu proti okraji displeje (u článku stejné, u bubliny světlejší)
            fun luma(x0f: Float, y0f: Float, x1f: Float, y1f: Float): Int? {
                val x0 = (x0f * bw).toInt().coerceIn(0, bw - 1); val x1 = (x1f * bw).toInt().coerceIn(x0 + 1, bw)
                val y0 = (y0f * bh).toInt().coerceIn(0, bh - 1); val y1 = (y1f * bh).toInt().coerceIn(y0 + 1, bh)
                var sum = 0L; var n = 0
                var y = y0
                while (y < y1) { var x = x0; while (x < x1) { val p = still.getPixel(x, y)
                    sum += (((p shr 16) and 0xFF) * 3 + ((p shr 8) and 0xFF) * 6 + (p and 0xFF)) / 10; n++; x++ }; y += 2 }
                return if (n == 0) null else (sum / n).toInt()
            }
            // pozadí displeje = prostředek mezery mezi dvěma odstavci (mezi bublinami je vidět pozadí, v článku je tam totéž co vedle textu)
            val lifts = (0 until r.boxes.size - 1).mapNotNull { i ->
                val b = r.boxes[i]?.takeIf { r.sides[i] != 'c' && b0(it, r) } ?: return@mapNotNull null
                val n = r.boxes[i + 1] ?: return@mapNotNull null
                if (n[1] - b[3] < r.lineH * 0.5f) return@mapNotNull null
                val mid = (b[3] + n[1]) / 2f
                val pad = luma(b[0] - 0.009f, b[1], b[0] - 0.004f, b[3]) ?: return@mapNotNull null
                val gap = luma(b[0] - 0.009f, mid - 0.002f, b[0] + 0.02f, mid + 0.002f) ?: return@mapNotNull null
                pad - gap
            }.sorted()
            val colored = S.withBubbleColors(r, blue, lifts.getOrNull(lifts.size / 2))
            // řádky sloupce telefonu: kolik bodů je modrých (vlastní bublina) a kde
            val x0 = ((r.left - 0.01f) * bw).toInt().coerceIn(0, bw - 1); val x1 = ((r.right + 0.02f) * bw).toInt().coerceIn(x0 + 1, bw)
            val y0 = (r.top * bh).toInt().coerceIn(0, bh - 1); val y1 = (r.bottom * bh).toInt().coerceIn(y0 + 1, bh)
            val rows = ArrayList<cz.recaption.core.PhoneScreen.BlueRow>()
            var y = y0
            while (y < y1) {
                var n = 0; var hit = 0; var lo = x1; var hi = x0
                var x = x0
                while (x < x1) {
                    val p = still.getPixel(x, y)
                    val cr = (p shr 16) and 0xFF; val cg = (p shr 8) and 0xFF; val cb = p and 0xFF
                    n++
                    if (cb >= 60 && cb - (cr + cg) / 2 >= S.BLUE) { hit++; if (x < lo) lo = x; if (x > hi) hi = x }
                    x += 2
                }
                rows += cz.recaption.core.PhoneScreen.BlueRow(y.toFloat() / bh, if (n == 0) 0f else hit.toFloat() / n,
                    lo.toFloat() / bw, (hi + 1).toFloat() / bw)
                y += 2
            }
            val out = S.withBlankBubbles(colored, rows)
            phoneBlankImages = out.paragraphs.indices.map { i ->
                val b = out.images.getOrNull(i)
                if (b == null || out.paragraphs[i].isNotBlank() || out.sides[i] == 'c') null else try {
                    val ix = (b[0] * bw).toInt().coerceIn(0, bw - 1); val iy = (b[1] * bh).toInt().coerceIn(0, bh - 1)
                    val iw = ((b[2] - b[0]) * bw).toInt().coerceIn(1, bw - ix); val ih = ((b[3] - b[1]) * bh).toInt().coerceIn(1, bh - iy)
                    android.graphics.Bitmap.createBitmap(still, ix, iy, iw, ih)
                } catch (_: Exception) { null }
            }
            return out
        } catch (_: Exception) { return r } finally { still.recycle() }
    }

    /** Odstavec u levého okraje sloupce (příchozí bublina nebo text článku) – jen u něj se měří okraj bubliny. */
    private fun b0(b: FloatArray, r: cz.recaption.core.PhoneRead): Boolean = b[0] - r.left < (r.right - r.left) * 0.1f

    /** Obrázky bublin bez textu (emoji) k odstavcům posledního čtení s barvami; jinak null. */
    @Volatile private var phoneBlankImages: List<android.graphics.Bitmap?> = emptyList()

    /** Část karty upozornění: pevně („4 oznámení“, „Připomínky“, značka aplikace), jinak překladačem; „Název · N …“ po částech. */
    private suspend fun cardTranslate(text: String): String =
        text.split(cz.recaption.core.PhoneCard.SEP).map { part ->
            cz.recaption.core.PhoneCard.fixed(part, lang, phoneSpec) ?: menuTranslate(part)
        }.joinToString(cz.recaption.core.PhoneCard.SEP)

    /** Telefon pryč (pauza, menu hry, nová volba): schovat a začít znovu. */
    private fun dropPhone() {
        phoneGen++
        phoneTracker = cz.recaption.core.PhoneTracker()
        if (phoneShown) { phoneShown = false; overlay?.hidePhone() }
        resetPhoneFollow()
    }

    /** Pokyny pryč (titulek, pauza, menu hry): schovat a začít znovu. */
    private fun dropWorld() {
        worldGen++
        worldTracker = cz.recaption.core.WorldTracker()
        worldItems = emptyList()
        overlay?.let { showWorldAll(it) }
    }

    // ---------------- upozornění telefonu vpravo dole (GTA VI, od 0.97.98) ----------------

    /** Ukázané pokyny u předmětů a upozornění telefonu – jdou do stejných oken přes text ([SubtitleOverlay.showWorld]). */
    @Volatile private var worldItems: List<Pair<String, cz.recaption.core.WorldPrompt>> = emptyList()
    @Volatile private var noticeItem: Pair<String, cz.recaption.core.WorldPrompt>? = null
    @Volatile private var noticeTracker = cz.recaption.core.NoticeTracker()
    @Volatile private var noticeJob: kotlinx.coroutines.Job? = null
    @Volatile private var noticeGen = 0
    private var noticeCheckAt = 0L
    private var noticeReadAt = 0L
    private var noticeGrid: IntArray? = null

    private fun showWorldAll(ov: SubtitleOverlay) {
        val all = worldItems + listOfNotNull(noticeItem)
        if (all.isEmpty()) ov.hideWorld() else ov.showWorld(all)
    }

    /** S volbou „Překlad telefonu“: nejvýš 1× za 1 s, beze změny mřížky jasu nejdřív po 3 s (malý výřez vpravo dole). */
    private fun startNotice(now: Long, ov: SubtitleOverlay) {
        if (!phoneOn || paused || noticeJob?.isActive == true) return
        if (now - noticeCheckAt < 1000) return
        if (!sideMay(cz.recaption.core.SideReads.NOTICE, now)) return
        noticeCheckAt = now
        val grid = reader?.grayNow(cz.recaption.core.PhoneNotice.GRID) ?: return
        val prev = noticeGrid
        noticeGrid = grid
        if (!cz.recaption.core.PhoneScreen.changed(prev, grid) && now - noticeReadAt < 3000) return
        noticeReadAt = now
        noticeJob = sideLaunch(cz.recaption.core.SideReads.NOTICE, now) { checkNotice(ov) }
    }

    private suspend fun checkNotice(ov: SubtitleOverlay) {
        val N = cz.recaption.core.PhoneNotice
        val crop = reader?.grabArea("upozorneni", N.ZONE_L, N.ZONE_T, N.ZONE_R, N.ZONE_B) ?: return
        val gen = noticeGen
        val result = recognizer.process(InputImage.fromBitmap(crop.bmp, 0)).await()
        if (paused || gen != noticeGen) return
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val bb = l.boundingBox ?: return@mapNotNull null
            cz.recaption.core.MenuLine(l.text, bb.left + crop.x0, bb.top + crop.y0, bb.right + crop.x0, bb.bottom + crop.y0, -1)
        }
        val (n, changed) = noticeTracker.update(N.read(lines, crop.fullW, crop.fullH))
        if (!changed) return
        if (n == null) { noticeItem = null; showWorldAll(ov); tp("NOTICE|off"); return }
        // SMS: jméno odesílatele zůstává, rod v 1. osobě podle něj (jako příchozí zpráva v telefonu)
        val tr = if (n.sms) menuTranslate(n.text, speaker = n.app) else menuTranslate(n.text)
        if (paused || gen != noticeGen) return
        noticeItem = tr to cz.recaption.core.WorldPrompt(n.text, n.left, n.top, n.right, n.bottom, n.lineH)
        showWorldAll(ov)
        tp("NOTICE|show|" + n.app + "|" + n.text)
    }

    private fun dropNotice() {
        noticeGen++
        noticeTracker = cz.recaption.core.NoticeTracker()
        noticeItem = null
        overlay?.let { showWorldAll(it) }
    }

    // ---------------- tipy hry vlevo nahoře (GTA VI „Tip: …“, dávka GVFDXYV8 #101) ----------------

    @Volatile private var tipOn = false
    @Volatile private var tipTracker = cz.recaption.core.TipTracker()
    @Volatile private var tipJob: kotlinx.coroutines.Job? = null
    @Volatile private var tipGen = 0
    /** Překlad tipu je v okně popisu menu ([SubtitleOverlay.showMenuDesc]) – menu hry má přednost (tip se při něm schová). */
    @Volatile private var tipShown = false
    private var tipCheckAt = 0L
    private var tipReadAt = 0L
    private var tipGrid: IntArray? = null

    /** Volba „Tipy hry“ jen u her, jejichž tipy známe (zatím GTA VI – profil s menu vpravo dole). */
    private fun loadTipSettings() {
        tipOn = Prefs.gameTips(this) && cz.recaption.core.GameProfiles.of(Prefs.gameId(this)).cornerSpec != null
        tipGen++
        tipTracker = cz.recaption.core.TipTracker()
        tipCheckAt = 0L; tipGrid = null
        // okno popisu se při nové volbě vytváří znovu, nic schovávat netřeba
        tipShown = false
    }

    /**
     * Vlastní úloha (tick na ni nečeká), nejvýš 1× za 1 s; beze změny mřížky jasu vlevo nahoře ([cz.recaption.core.HelpTip.GRID])
     * nejdřív po 3 s. Volá se jen mimo menu hry (tick při menu končí dřív).
     */
    private fun startTip(now: Long, ov: SubtitleOverlay) {
        if (!tipOn || paused || tipJob?.isActive == true) return
        if (now - tipCheckAt < 1000) return
        if (!sideMay(cz.recaption.core.SideReads.TIP, now)) return
        tipCheckAt = now
        val grid = reader?.grayNow(cz.recaption.core.HelpTip.GRID) ?: return
        val prev = tipGrid
        tipGrid = grid
        if (!cz.recaption.core.PhoneScreen.changed(prev, grid) && now - tipReadAt < 3000) return
        tipReadAt = now
        tipJob = sideLaunch(cz.recaption.core.SideReads.TIP, now) { checkTip(ov) }
    }

    private suspend fun checkTip(ov: SubtitleOverlay) {
        val H = cz.recaption.core.HelpTip
        val crop = reader?.grabArea("tip", H.ZONE_L, H.ZONE_T, H.ZONE_R, H.ZONE_B, 1000) ?: return
        val gen = tipGen
        val tracker = tipTracker
        val t0 = SystemClock.uptimeMillis()
        val result = recognizer.process(InputImage.fromBitmap(crop.bmp, 0)).await()
        if (paused || gen != tipGen) return
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val bb = l.boundingBox ?: return@mapNotNull null
            cz.recaption.core.MenuLine(l.text, bb.left + crop.x0, bb.top + crop.y0, bb.right + crop.x0, bb.bottom + crop.y0, brightP90(crop, bb))
        }
        // bílé písmo tipu (90. percentil jasu v rámečku), ne tmavý nápis ve scéně
        val (tip, changed) = tracker.update(H.read(lines, crop.fullW, crop.fullH) { it.bright >= 170 })
        if (!changed) return
        if (tip == null) {
            if (tipShown) { tipShown = false; ov.hideMenuDesc(); tp("TIP|off") }
            return
        }
        // jako telefon: DeepL, když ho hráč má (delší věty), jinak offline
        val tr = menuTranslate(tip.text)
        if (paused || gen != tipGen || menuShown) return
        tipShown = true
        // tipy světle žlutě jako překlad menu (od 1.0.1, přání majitele; dřív bíle)
        ov.showMenuDesc(null, tr, H.place(tip), tip.lineH * 0.95f)
        collectTexts(cz.recaption.core.LineBank.TIP, listOf(tip.text))
        tp("TIP|show|radku=${tip.lines}|ocr=${SystemClock.uptimeMillis() - t0}ms|" + tip.text.take(160))
    }

    /** Tip pryč (pauza, menu hry, nová volba): schovat a začít znovu. Běží i z hlavního vlákna. */
    private fun dropTip() {
        tipGen++
        tipTracker = cz.recaption.core.TipTracker()
        tipGrid = null
        if (tipShown) { tipShown = false; overlay?.hideMenuDesc() }
    }

    /** Roh pryč (pauza, menu hry): schovat a začít znovu. Běží i z hlavního vlákna. */
    private fun dropCorner() {
        cornerGen++
        cornerTracker = cz.recaption.core.CornerTracker()
        cornerRects = emptyList()
        overlay?.hideCorner()
    }

    /** Řádky titulků bez položek menu vpravo dole (jen se zapnutou volbou a zobrazeným seznamem). */
    private fun withoutCorner(blocks: List<OcrBlock>, x0: Int, y0: Int): List<OcrBlock> {
        val rects = cornerRects
        // od 1.0.04: u her s ovládáním vpravo dole je ten roh vyhrazený jen jemu ([CornerPrompts.reserved]) – titulek ani
        // úkol tam nezačíná, i když seznam zrovna není rozpoznaný nebo je volba vypnutá
        val reserve = whiteSubtitles
        if ((rects.isEmpty() && !reserve) || capW <= 0 || capH <= 0) return blocks
        fun inCorner(l: OcrLine): Boolean {
            if (reserve && cz.recaption.core.CornerPrompts.reserved((x0 + l.left).toFloat() / capW, (y0 + l.top).toFloat() / capH)) return true
            val cx = (x0 + (l.left + l.right) / 2f) / capW; val cy = (y0 + (l.top + l.bottom) / 2f) / capH
            return rects.any { r -> cx >= r[0] - 0.01f && cx <= r[2] + 0.02f && cy >= r[1] - 0.01f && cy <= r[3] + 0.01f }
        }
        return blocks.map { b -> OcrBlock(b.lines.filterNot { inCorner(it) }) }.filter { it.lines.isNotEmpty() }
    }

    /** Hra s bílými titulky (zatím GTA VI – profil s menu vpravo dole): tmavý text na světlém = telefon ve hře, ne titulek. */
    @Volatile private var whiteSubtitles = false

    /**
     * Řádky s tmavým písmem na světlém pozadí pryč (text z telefonu ve hře, [cz.recaption.core.TextPolarity], od 0.97.94).
     * Jen u her s bílými titulky; jas se bere ze vzorků v rámečku řádku (nejvýš 48 × 10 bodů), nic navíc za snímek.
     */
    private fun withoutPhone(blocks: List<OcrBlock>, crop: Bitmap): List<OcrBlock> {
        if (!whiteSubtitles || blocks.isEmpty()) return blocks
        var dropped = 0
        var scene = 0
        fun gray(l: OcrLine): IntArray {
            val x0 = l.left.coerceIn(0, crop.width - 1); val x1 = l.right.coerceIn(x0 + 1, crop.width)
            val y0 = l.top.coerceIn(0, crop.height - 1); val y1 = l.bottom.coerceIn(y0 + 1, crop.height)
            val sx = maxOf(1, (x1 - x0) / 48); val sy = maxOf(1, (y1 - y0) / 10)
            val g = ArrayList<Int>(500)
            var y = y0
            while (y < y1) { var x = x0; while (x < x1) { val c = crop.getPixel(x, y); g += ((c shr 16 and 0xFF) * 3 + (c shr 8 and 0xFF) * 6 + (c and 0xFF)) / 10; x += sx }; y += sy }
            return g.toIntArray()
        }
        // jas každého řádku jednou; text telefonu pryč, z řádků s mluvčím se učí, jestli hra kreslí titulky na tmavý podklad
        val backdrop = subtitleBackdrop
        val kept = blocks.map { b -> b.lines.mapNotNull { l ->
            val g = gray(l)
            if (cz.recaption.core.TextPolarity.darkOnLight(g)) { dropped++; return@mapNotNull null }
            val t = TextLogic.clean(l.text)
            val speaker = TextLogic.splitSpeaker(t).speaker != null
            if (speaker) backdrop.learn(cz.recaption.core.SubtitleBackdrop.median(g), t)
            Triple(l, g, speaker || cz.recaption.core.Captions.isLine(t) || t.startsWith("["))
        } }
        // nápis ve scéně na místě titulků (jméno na dresu, značka auta) – jen řádky bez mluvčího ([SubtitleBackdrop])
        val out = kept.map { ls -> OcrBlock(ls.filterNot { (l, g, sure) ->
            (!sure && backdrop.sceneText(g, l.text.split(' ').count { w -> w.any { it.isLetter() } })).also { if (it) scene++ }
        }.map { it.first }) }.filter { it.lines.isNotEmpty() }
        if (dropped > 0) tp("PHONE|vynechano $dropped")
        if (scene > 0) tp("SCENE|vynechano $scene|podklad=${if (backdrop.boxed) 1 else 0}")
        return out
    }

    /** Podklad titulků ve hře a nápisy ve scéně ([cz.recaption.core.SubtitleBackdrop]) – nové při změně hry / nastavení. */
    @Volatile private var subtitleBackdrop = cz.recaption.core.SubtitleBackdrop()

    /** Jas písma v rámečku (souřadnice ve výřezu): 90. percentil (bílé písmo ~250, šedé ~115, pozadí je tmavší). */
    private fun brightP90(c: StreamReader.MenuCrop, r: android.graphics.Rect): Int {
        val x0 = r.left.coerceIn(0, c.w - 1); val x1 = r.right.coerceIn(x0 + 1, c.w)
        val y0 = r.top.coerceIn(0, c.h - 1); val y1 = r.bottom.coerceIn(y0 + 1, c.h)
        val hist = IntArray(256)
        val sx = maxOf(1, (x1 - x0) / 48); val sy = maxOf(1, (y1 - y0) / 10)
        var n = 0
        var y = y0
        while (y < y1) { var x = x0; while (x < x1) { hist[c.gray[y * c.w + x] and 0xFF]++; n++; x += sx }; y += sy }
        if (n == 0) return -1
        var left = n - (n * 9) / 10
        for (v in 255 downTo 0) { left -= hist[v]; if (left <= 0) return v }
        return 0
    }

    /** Překlad textu z menu (offline, případně DeepL jako titulky); výsledek se pamatuje. */
    /** [speaker] = kdo text píše (příchozí zpráva v telefonu = kontakt z nadpisu) – jen pro rod v 1. osobě („neviděla“). */
    private suspend fun menuTranslate(text: String, deepl: Boolean = true, speaker: String? = null): String {
        val key = if (speaker == null) text else "$speaker\u0001$text"
        menuWords[key]?.let { return it }
        val offline = try { translateSegments(text, speaker = speaker) } catch (_: Exception) { null }
        // DeepL i pro menu, telefon a pokyny hry, když ho hráč má (od 0.97.112, majitel: „v překladu menu dost často nesmysly“) –
        // paměť DeepL i [menuWords], takže každý text jen jednou; nepřijde-li do 2,5 s, zůstane offline
        val viaDeepl = offline?.takeIf { deepl }?.let { off ->
            deeplKey?.let { DeeplTranslator.plan(it, lang, text, glossary, source) }?.let { job ->
                val texts = DeeplTranslator.cached(job) ?: if (DeeplTranslator.tryStart())
                    withTimeoutOrNull(cz.recaption.core.Deepl.WAIT_MS) { DeeplTranslator.fetch(job) }?.texts else null
                texts?.let { DeeplTranslator.merge(off, job, it, speaker) }
            }
        }
        val out = (viaDeepl ?: offline)?.let { TextLogic.joinSegments(it) }?.replace('\n', ' ') ?: text
        if (menuWords.size > 300) menuWords.clear()
        menuWords[key] = out
        return out
    }

    /** Průměrný jas pod řádkem (0–255): vybraná položka menu má bílý pruh, ostatní tmavé pozadí. */
    private fun meanGray(c: StreamReader.MenuCrop, r: android.graphics.Rect): Int {
        val x0 = r.left.coerceIn(0, c.w - 1); val x1 = r.right.coerceIn(x0 + 1, c.w)
        val y0 = r.top.coerceIn(0, c.h - 1); val y1 = r.bottom.coerceIn(y0 + 1, c.h)
        val sx = maxOf(1, (x1 - x0) / 40); val sy = maxOf(1, (y1 - y0) / 6)
        var s = 0L; var n = 0
        var y = y0
        while (y < y1) { var x = x0; while (x < x1) { s += c.gray[y * c.w + x] and 0xFF; n++; x += sx }; y += sy }
        return if (n == 0) 0 else (s / n).toInt()
    }

    /** 95. percentil jasu v obdélníku (nejsvětlejší body písma; od 0.97.144) – nejvýš ~240 vzorků. */
    private fun peakGray(c: StreamReader.MenuCrop, r: android.graphics.Rect): Int {
        val x0 = r.left.coerceIn(0, c.w - 1); val x1 = r.right.coerceIn(x0 + 1, c.w)
        val y0 = r.top.coerceIn(0, c.h - 1); val y1 = r.bottom.coerceIn(y0 + 1, c.h)
        val sx = maxOf(1, (x1 - x0) / 30); val sy = maxOf(1, (y1 - y0) / 8)
        val v = IntArray(256)
        var n = 0
        var y = y0
        while (y < y1) { var x = x0; while (x < x1) { v[c.gray[y * c.w + x].toInt() and 0xFF]++; n++; x += sx }; y += sy }
        if (n == 0) return -1
        var need = n - n * 95 / 100
        for (g in 255 downTo 0) { need -= v[g]; if (need <= 0) return g }
        return 0
    }

    private suspend fun tick(now: Long) {
        val ov = overlay ?: return
        // menu hry otevřené → v pruhu je jeho překlad, titulky se nečtou
        val inMenu = checkMenu(now, ov)
        // Xbox posílá snímek jen na požádání: při otevřeném menu (výběr podle jasu) a při záznamu snímků chtít každý
        reader?.eager = inMenu || SnapRecorder.active
        if (inMenu) { lastMs = SystemClock.uptimeMillis() - now; return }
        // titulky napřed: vedlejší čtení (roh, předměty, telefon, upozornění, tipy, hledání menu) se spouští až po nich
        // a nejvýš jedno ([cz.recaption.core.SideReads]) – dřív se zařadila do čtečky před titulek téhož kola
        val z = layout.read
        val frame = reader?.grab(z.left, z.top, z.right, z.bottom)      // null = žádný nový snímek videa
        if (frame != null) {
            capW = frame.fullW
            capH = frame.fullH
            frames++
            process(frame, ov)
            lastMs = SystemClock.uptimeMillis() - now
            // systému: jak dlouho kolo s titulky trvalo a jak dlouho trvat má (Android 12+, jinak nic)
            readHint.target(perf.intervalMs)
            readHint.report(lastMs)
        }
        startSide(SystemClock.uptimeMillis(), ov)
    }

    /** Plán vedlejších čtení: nejvýš jedno najednou, s pauzou po každém a až po titulcích ([cz.recaption.core.SideReads]). */
    private val sideReads = cz.recaption.core.SideReads()

    /** Smí vedlejší část [slot] teď číst? Dokud se titulek ustaluje (čeká na potvrzení a překlad), čeká. */
    private fun sideMay(slot: String, now: Long): Boolean = sideReads.mayStart(slot, now, !tracker.isSettled)

    /** Spustí vedlejší čtení [slot] ve vlastní úloze a po doběhnutí uvolní řadu. */
    private fun sideLaunch(slot: String, now: Long, block: suspend () -> Unit): kotlinx.coroutines.Job {
        sideReads.started(slot, now)
        return scope.launch(sideThread) {
            try { block() } catch (_: Exception) {} finally { sideReads.finished(slot, SystemClock.uptimeMillis()) }
        }
    }

    /** Vedlejší čtení po titulcích; pořadí tady nerozhoduje – na řadu jde část, která čeká nejdéle. */
    private fun startSide(now: Long, ov: SubtitleOverlay) {
        if (menuShown || paused) return
        startCorner(now, ov)
        startPhone(now, ov)
        startNotice(now, ov)
        startTip(now, ov)
        startWorld(now, ov)
        startMenuLook(now, ov)
    }

    private suspend fun process(frame: StreamReader.Crop, ov: SubtitleOverlay) {
        val z = layout.read
        val x0 = frame.x0
        val y0 = frame.y0
        val crop = frame.bmp      // znovupoužívaná bitmapa – nerecyklovat

        // přeskoč čtení, když se oblast od posledního čtení nezměnila
        val sig = frame.sig
        if (tracker.isSettled && !FrameSignature.changed(lastSig, sig, frame.cellPixels)) {
            return
        }
        lastSig = sig
        // výsledek čtení i překladu platí jen pro tento „začátek“ (viz SubtitleTracker.revision)
        val token = tracker.revision

        val ocrStart = SystemClock.uptimeMillis()
        // drobné titulky: výřez se před čtením zvětší (viz TextScale)
        val k = textScale.scale
        // pokus „Rychlejší čtení“ (výchozí vypnuto): čte se jen pás s řádky textu, ne celá oblast (core/TextBand)
        val band = if (fastRead) textBand(crop) else null
        val src = if (band != null) Bitmap.createBitmap(crop, band.left, band.top, band.width, band.height) else crop
        val input = if (k > 1f) Bitmap.createScaledBitmap(src, (src.width * k).toInt(), (src.height * k).toInt(), true) else src
        // s limitem: zaseknuté rozpoznání (viz OCR_TIMEOUT_MS) kolo jen přeskočí, místo aby čtení titulků zastavilo navždy
        val result = try { withTimeoutOrNull(OCR_TIMEOUT_MS) { recognizer.process(InputImage.fromBitmap(input, 0)).await() } } finally {
            if (input !== crop && input !== src) input.recycle()
            if (src !== crop) src.recycle()
        }
        if (result == null) {
            ocrHangs++
            ocrHangsTotal = ocrHangs
            if (ocrHangs % 5 == 1) tp("OCRHANG|${ocrHangs}×|limit=${OCR_TIMEOUT_MS}ms|fastRead=$fastRead|band=${band?.let { "${it.width}x${it.height}" } ?: "-"}")
            return
        }
        lastTextBand = band?.let { "${it.width}x${it.height}" } ?: if (fastRead) "cela" else ""
        // mezitím se čtení začalo znovu (pauza, jiná oblast, nové nastavení) → výsledek je zastaralý
        if (!tracker.isCurrent(token)) return
        ocrRuns++
        lastOcrMs = SystemClock.uptimeMillis() - ocrStart
        val blocks = boxFilter(withoutPhone(withoutCorner(toBlocks(result, k, band?.left ?: 0, band?.top ?: 0), x0, y0), crop), crop, y0)
        if (textScale.update(blocks.flatMap { b -> b.lines.filter { it.text.count { c -> c.isLetter() } >= 3 }.map { it.height } }, crop.width * crop.height))
            tp("SCALE|${textScale.scale}")

        // V oblasti je text v jiném jazyce než v angličtině (česká nabídka konzole, hra už je přeložená):
        // to není anglický titulek ze hry – nepřekládá se nic, ani anglické nápisy vedle.
        val foreignScreen = (onlySubtitles || autoFind) && TextLogic.looksForeign(result.text, source)
        val picked = if (foreignScreen) Picked("", 0) else TextLogic.assemble(
            blocks,
            minLineHeight = (capH * 0.013f).toInt(),
            subtitlesOnly = onlySubtitles || autoFind,   // automatika čte velkou plochu → vždy jen titulky
            screenCenterX = capW / 2 - x0,
            centerTolerance = (capW * 0.14f).toInt(),
            maxLineHeight = (capH * 0.085f).toInt(),
            screen = screen,
            objectives = prompts,
            objectiveTop = if (prompts && objectiveTracker.top >= 0f) (objectiveTracker.top * capH).toInt() - y0 else -1,
            // úkol jen ve spodních 14 % obrazu (v GTA je vždy úplně dole, pod titulky)
            objectiveMinTop = if (prompts) (capH * OBJECTIVE_ZONE_TOP).toInt() - y0 else -1,
        )
        if (prompts) handleObjective(picked.objective, x0, y0)
        if (picked.text.isNotEmpty()) lastOcrText = picked.text
        takeNameColor()
        if (coloredSpeaker && picked.text.isNotEmpty()) askNameColor(blocks, picked.text, x0, y0)
        // záznam pro ladění profilů her: co bylo v oblasti čtení a co se z toho vzalo
        cz.recaption.core.ScreenReads.add(result.text, when {
            foreignScreen -> "jiný jazyk"
            picked.text.isEmpty() -> "nevzato"
            else -> "titulek: " + picked.text.replace('\n', ' ')
        })

        // text, který není anglicky (hra už je v jiném jazyce, nebo je to šum), se nepřekládá
        if (picked.text.isNotEmpty() && TextLogic.looksForeign(picked.text, source)) {
            if (ticksLogged++ < 400) tp("SKIP|" + picked.text.replace('\n', ' ').take(40))
            return
        }
        // záznam pro automatickou zkoušku (jen prvních pár set čtení)
        if (ticksLogged++ < 400) tp("READ|ocr=${lastOcrMs}ms|zone=${(z.height * 100).toInt()}%|" +
            (if (lastTextBand.isNotEmpty()) "band=$lastTextBand|" else "") + picked.text.replace('\n', ' ').take(40))

        ocrMsSum += lastOcrMs
        readMs = if (readMs == 0L) lastOcrMs else (readMs * 4 + lastOcrMs) / 5
        if (capW != shownW || capH != shownH) { shownW = capW; shownH = capH; videoSize = "${capW}×${capH}" }
        if (picked.text.isNotEmpty()) lastRead = picked.text.replace('\n', ' ').take(60)
        // Titulek je na obrazovce pár sekund. Text, který stojí beze změny dlouho, je nápis nebo menu:
        // pruh se schová a znovu se ukáže, až se text změní.
        val nowMs = SystemClock.uptimeMillis()
        if (picked.text.isEmpty() || !TextLogic.similar(picked.text, steadyText)) { steadyText = picked.text; steadySince = nowMs }
        // volby odpovědí smí stát déle – hráč se rozhoduje
        val staleMs = if (TextLogic.hasChoices(picked.text)) TextLogic.CHOICES_STALE_MS else STALE_MS
        val stale = (onlySubtitles || autoFind) && picked.text.isNotEmpty() && nowMs - steadySince > staleMs
        if (stale) {
            if (overlay?.isShown == true) { overlay?.hide(); tp("HIDE|stale") }
            synchronized(showLock) { hiddenSeq = showSeq }
            return
        }
        // slabé zařízení (jedno čtení trvá déle než půl sekundy): pruh se schová hned po prvním prázdném čtení
        // otisk obrazu: na nezměněném obrazu ochuzené čtení (jen poslední řádek…) nenahradí celý titulek
        when (val action = tracker.onOcr(TextLogic.withoutFading(picked.text, tracker.shown), slowDevice = lastOcrMs > 500, sig = sig, cellPixels = frame.cellPixels)) {
            is SubtitleTracker.Action.Show -> {
                // jiná varianta téhož titulku, kterou čtečka potvrdila jako opravu (ne nový titulek)
                val correction = tracker.shown.isNotEmpty() && TextLogic.similar(action.text, tracker.shown)
                // překlad už většinou běží (spuštěný po prvním přečtení) – jen se počká na výsledek
                val ready = inflight.remove(action.text)
                val offline = (if (ready != null) ready.await() else null) ?: translateSegments(action.text) ?: return
                // překlad doběhl až po novém začátku (pauza, změna jazyka, jiná oblast) → neukazuje se
                if (!tracker.confirmShown(action.text, token)) return
                // pokus DeepL (výchozí vypnuto): řádek, který už DeepL překládal, se vezme rovnou z jeho paměti
                val deeplJob = deeplKey?.let { DeeplTranslator.plan(it, lang, action.text, glossary, source) }
                val fromDeepl = deeplJob?.let { DeeplTranslator.cached(it) }
                val segs = if (deeplJob != null && fromDeepl != null) {
                    DeeplTranslator.stats.fromCache(); tp(cz.recaption.core.Deepl.logLine(0, "cache", 0))
                    DeeplTranslator.merge(offline, deeplJob, fromDeepl)
                } else offline
                val top = if (picked.top >= 0) (y0 + picked.top).toFloat() / capH else -1f
                val bottom = if (picked.bottom >= 0) (y0 + picked.bottom).toFloat() / capH else -1f
                hideAt = 0
                shows++
                tp("SHOW|" + action.text.replace('\n', ' ') + "|" + segs.joinToString(" ") { (it.speaker?.plus(": ") ?: "") + it.body })
                // ukázka: za jak dlouho po objevení titulku je překlad na obrazovce (z toho samotné čtení)
                if (demo) tp("LAT|${DemoGame.shownFor(SystemClock.uptimeMillis() - DemoFeeder.epoch)}ms|ocr=${lastOcrMs}ms")
                val left = if (picked.left >= 0) (x0 + picked.left).toFloat() / capW else -1f
                val right = if (picked.right >= 0) (x0 + picked.right).toFloat() / capW else -1f
                val lineH = picked.lineHeight.toFloat() / capH
                val en = TextLogic.segments(action.text).let { if (prompts) TextLogic.markPrompt(it) else it }
                // každá replika má v pruhu pořád stejný překlad (varianty čtení, DeepL / offline) a s pokusem
                // „Jen nové repliky“ se z bloku replik ukážou jen ty, které v pruhu ještě nebyly
                // Historie a záznam s tím, co se opravdu ukáže (od 0.97.90): SHOW| se píše hned s offline překladem,
                // s DeepL jde skutečný text do DLTXT| (dávka 6WA3-ETJV – v záznamu i Historii byl offline, v pruhu DeepL)
                val finish = { chosen: List<Segment> ->
                    SubtitleHistory.add(action.text, TextLogic.joinSegments(chosen))
                    collectLines(en)
                    if (chosen !== segs) tp("DLTXT|" + chosen.joinToString(" ") { (it.speaker?.plus(": ") ?: "") + it.body })
                    stableSegments(action.text, en, chosen, correction)
                }
                // DeepL zapnutý: ukáže se JEN jedna varianta – překlad z DeepL (chvilku se na něj počká),
                // offline jen když DeepL neodpoví. Do 0.97 se ukázal offline a DeepL ho pak přepsal –
                // vypadalo to, jako by naskočila nová věta (majitel: „hrozné přeblikávání“).
                if (deeplJob != null && fromDeepl == null && DeeplTranslator.tryStart()) {
                    val seq = synchronized(showLock) { ++showSeq }
                    deeplThenShow(deeplJob, segs, token, seq, ov) { chosen ->
                        finish(chosen)?.let { (out, orig) -> present(Shown(ov, out, orig, top, bottom, left, right, lineH, action.text), seq) }
                    }
                } else {
                    synchronized(showLock) {
                        finish(segs)?.let { (out, orig) -> present(Shown(ov, out, orig, top, bottom, left, right, lineH, action.text), ++showSeq) }
                    }
                }
                statsFor(this).line()
                // automatika: zapamatuj si, kde na obrazovce titulky jsou
                if (autoFind && autoZone.onSubtitle(top, bottom, SystemClock.uptimeMillis())) zoneChanged()
                gameGuess.onText(action.text)
                gameGuess.guess()?.let { g ->
                    if (g.id != Prefs.gameId(this) && g.id != Prefs.gameGuess(this) && g.id != Prefs.gameGuessNo(this)) { Prefs.setGameGuess(this, g.id); tp("GAME|guess ${g.id}") }
                }
            }
            // anglický titulek zmizel – překlad nech chvíli dočíst
            SubtitleTracker.Action.Hide -> {
                tp("HIDE")
                // odpověď DeepL, která na titulek ještě čeká, se už neukáže ([deeplThenShow])
                synchronized(showLock) { hiddenSeq = showSeq }
                recentLines.endConversation(SystemClock.uptimeMillis())
                if (showQueue.minMs > 0) { showQueue.requestHide(SystemClock.uptimeMillis() + holdMs); scheduleQueue() }
                else if (holdMs <= 0) ov.hide() else hideAt = SystemClock.uptimeMillis() + holdMs
            }
            SubtitleTracker.Action.None -> {
                // na obrazovce je už jiný titulek → starý překlad hned schovej
                // (s frontou až po nejkratší době a když nic nečeká – jinak by se rychlá replika nestihla přečíst)
                if (tracker.isChanging && picked.text.isNotEmpty() && ov.isShown &&
                    (showQueue.minMs <= 0 || (showQueue.minElapsed(SystemClock.uptimeMillis()) && showQueue.waitingCount == 0))) {
                    hideAt = 0; ov.hide(); showQueue.clear()
                }
                prefetch(tracker.pendingText)
            }
        }
    }

    /** Sbírání replik: ukázané repliky a pokyny hry pod nimi (druh `pokyn`) do LineCollector; ne v ukázce. */
    private fun collectLines(en: List<Segment>) {
        val game = collectGame ?: return
        if (demo) return
        for (s in en) {
            if (s.prompt) LineCollector.add(this, game, source.code, null, s.body, cz.recaption.core.LineBank.POKYN)
            else LineCollector.add(this, game, source.code, s.speaker, s.body)
        }
    }

    /** Co z kterého druhu (menu, telefon…) bylo naposledy ukázané – počítá se jen nově ukázaný text, ne každé překreslení. */
    private val collectSeen = HashMap<String, Set<String>>()

    /**
     * Sbírání ostatních textů hry (od 1.00, přání majitele): anglické originály ukázaného úkolu, menu, telefonu, tipu,
     * pokynů u předmětů a menu vpravo dole ([kind] = druh z [cz.recaption.core.LineBank]). Stejné podmínky jako repliky;
     * jen zápis do paměti. Text, který byl v minulém ukázání téže části ([slot]), se znovu nepočítá (posun výběru v menu apod.).
     */
    private fun collectTexts(kind: String, texts: List<String?>, slot: String = kind) {
        val game = collectGame ?: return
        if (demo) return
        val now = texts.mapNotNull { it?.trim()?.takeIf { t -> t.isNotEmpty() } }.toSet()
        val prev = synchronized(collectSeen) { collectSeen.put(slot, now) } ?: emptySet()
        for (t in now) if (t !in prev) LineCollector.add(this, game, source.code, null, t, kind)
    }

    /** Automatika změnila oblast čtení (našla titulky, nebo se vrátila k hledání). */
    private fun zoneChanged() {
        tp("ZONE|" + (autoZone.learned?.let { "learned ${it.top}-${it.bottom}" } ?: "scan"))
        layout = layout.copy(read = autoZone.current)
        overlay?.setLayout(layout)
        lastSig = null
        tracker.regionChanged()
    }

    /** Řádky z čtečky; souřadnice se přepočítají zpět na nezvětšený výřez ([k] = zvětšení). */
    /** Řádky z čtení v bodech oblasti: [k] = zvětšení, [dx]/[dy] = posun pásu s textem (Rychlejší čtení). */
    private fun toBlocks(t: Text, k: Float, dx: Int = 0, dy: Int = 0): List<OcrBlock> = t.textBlocks.map { block ->
        OcrBlock(block.lines.mapNotNull { l ->
            val bb = l.boundingBox ?: return@mapNotNull null
            // slova s vlastními rámečky (přesný obdélník jména / slov úkolu pro barvu z obrazu)
            val words = l.elements.mapNotNull { e ->
                val eb = e.boundingBox ?: return@mapNotNull null
                OcrLine(e.text, (eb.left / k).toInt() + dx, (eb.top / k).toInt() + dy, (eb.right / k).toInt() + dx, (eb.bottom / k).toInt() + dy)
            }
            OcrLine(l.text, (bb.left / k).toInt() + dx, (bb.top / k).toInt() + dy, (bb.right / k).toInt() + dx, (bb.bottom / k).toInt() + dy, words)
        })
    }

    // ---------------- pokus: Rychlejší čtení (od 0.97.47) ----------------

    @Volatile private var fastRead = false
    /** Velikost posledního pásu („1200x90“, „cela“ = text nenalezen, čte se celá oblast) – do záznamu READ. */
    @Volatile private var lastTextBand = ""
    private var bandPixels = IntArray(0)

    /** Pás s řádky textu ve výřezu, nebo null (čte se celý výřez jako dřív). */
    private fun textBand(crop: Bitmap): cz.recaption.core.TextBand.Band? {
        val w = crop.width; val h = crop.height
        if (bandPixels.size < w * h) bandPixels = IntArray(w * h)
        val px = bandPixels
        crop.getPixels(px, 0, w, 0, 0, w, h)
        for (i in 0 until w * h) {
            val c = px[i]
            px[i] = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
        }
        return cz.recaption.core.TextBand.find(px, w, h)
    }

    // ---------------- Titulky PS5: titulky jen v pruhu hry ----------------

    @Volatile private var subtitleBoxFilter: cz.recaption.core.SubtitleBox.Filter? = null
    private var boxPixels = IntArray(0)

    /** Řádky nad pásem úkolu, které nejsou v pruhu hry pod titulky, se vyřadí (cedule, billboardy) – viz core/SubtitleBox. */
    private fun boxFilter(blocks: List<OcrBlock>, crop: Bitmap, y0: Int): List<OcrBlock> {
        val f = subtitleBoxFilter ?: return blocks
        if (blocks.isEmpty()) return blocks
        val w = crop.width; val h = crop.height
        if (boxPixels.size < w * h) boxPixels = IntArray(w * h)
        val px = boxPixels
        crop.getPixels(px, 0, w, 0, 0, w, h)
        for (i in 0 until w * h) {
            val c = px[i]
            px[i] = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
        }
        val keepFrom = if (capH > 0) (capH * TextLogic.OBJECTIVE_ZONE_TOP).toInt() - y0 else -1
        val (out, dropped) = f.apply(blocks, px, w, h, keepFrom, SystemClock.uptimeMillis())
        if (dropped > 0) tp("BOX|mimo pruh $dropped")
        return out
    }

    // ---------------- úkol pod titulky (GTA, od 0.97.43) ----------------

    private val objectiveTracker = cz.recaption.core.ObjectiveTracker()
    /** Pořadí zobrazení úkolu – pozdě dokončený překlad staršího úkolu se už neukáže. */
    @Volatile private var objectiveSeq = 0L

    /**
     * Úkol je samostatný pruh: ukáže se na místě anglického úkolu a drží se, dokud ho čtení vidí (titulek nad ním
     * může mezitím zmizet). S vypnutým „Úkol pod titulky“ se úkol jen vyčlení z titulků a nepřekládá se.
     */
    private fun handleObjective(line: cz.recaption.core.OcrLine?, x0: Int, y0: Int) {
        val top = line?.let { (y0 + it.top).toFloat() / capH } ?: -1f
        val cast = objCast
        when (val ch = objectiveTracker.update(line?.text?.let { if (cast.isEmpty()) it else cz.recaption.core.ObjectiveImperative.fixNames(it, cast.keys) }, top)) {
            is cz.recaption.core.ObjectiveTracker.Change.Show -> {
                val l = line ?: return
                val seq = ++objectiveSeq
                val ov = overlay ?: return
                if (!Prefs.objectiveOn(this)) { ov.hideObjective(); return }
                val box = floatArrayOf((x0 + l.left).toFloat() / capW, (y0 + l.top).toFloat() / capH,
                    (x0 + l.right).toFloat() / capW, (y0 + l.bottom).toFloat() / capH)
                val lineH = TextLogic.fontSizeOf(l).toFloat() / capH
                tp("OBJ|show|" + ch.text.take(60))
                collectTexts(cz.recaption.core.LineBank.UKOL, listOf(ch.text))
                // pokus: barvy slov úkolu z obrazu (jen u nového úkolu, čtou se u nejbližšího snímku)
                val words = l.words.take(OBJ_WORDS)
                val colorsJob = if (objectiveColors) askObjectiveColors(words, x0, y0) else null
                scope.launch {
                    val offline = translateSegments(ch.text, imperative = true) ?: return@launch
                    // DeepL jako u titulků (paměť, nejvýš 2,5 s), jinak offline. Pokyn („Drive to the harbor“) ne:
                    // DeepL z něj dělá podstatné jméno („Cesta autem k jachtě“, záznam testera 21. 11. 2026), offline
                    // překlad jako rozkaz („Jeď k jachtě“) je to, co hráč čeká.
                    val command = cz.recaption.core.ObjectiveImperative.applies(ch.text, lang, source.english)
                    val segs = (if (command) null else deeplKey)?.let { DeeplTranslator.plan(it, lang, ch.text, glossary, source) }?.let { job ->
                        val texts = DeeplTranslator.cached(job) ?: if (DeeplTranslator.tryStart())
                            withTimeoutOrNull(cz.recaption.core.Deepl.WAIT_MS) { DeeplTranslator.fetch(job) }?.texts else null
                        texts?.let { DeeplTranslator.merge(offline, job, it) }
                    } ?: offline
                    val text = cz.recaption.core.ObjectiveImperative.leave(ch.text,
                        cz.recaption.core.ObjectiveImperative.accusative(ch.text, segs.joinToString(" ") { it.body }.trim(), lang) { cast[it] }, lang)
                    if (text.isEmpty() || seq != objectiveSeq || overlay !== ov) return@launch
                    tp("OBJ|tr|" + text.take(60))
                    // barvy ještě nejsou → hned bez nich (úkol se kvůli barvám nezdrží), obarví se, až přijdou
                    val plainFirst = colorsJob == null || !colorsJob.isCompleted
                    if (plainFirst) ov.showObjective(text, box, lineH)
                    val cj = colorsJob ?: return@launch
                    val colors = withTimeoutOrNull(3000) { cj.await() } ?: return@launch
                    val parts = objectiveParts(words.map { it.text }, colors, text)
                    if (seq != objectiveSeq || overlay !== ov) return@launch
                    if (parts != null) {
                        tp("OBJ|colors|" + parts.count { it.color != null } + "|" + parts.filter { it.color != null }
                            .joinToString(",") { it.text + "=" + cz.recaption.core.SpeakerColors.hex(it.color!!) })
                        ov.showObjective(text, box, lineH, parts)
                    } else {
                        tp("OBJ|colors|0")
                        if (!plainFirst) ov.showObjective(text, box, lineH)
                    }
                }
            }
            cz.recaption.core.ObjectiveTracker.Change.Hide -> { objectiveSeq++; overlay?.hideObjective(); tp("OBJ|hide") }
            cz.recaption.core.ObjectiveTracker.Change.Keep -> {}
        }
    }

    /**
     * Pokus: barevná slova úkolu (GTA „Go to the <žlutě>safehouse</žlutě>.“) i v překladu – v pruhu na Androidu
     * a do TV jako `casti` ([cz.recaption.core.ObjectiveColors]). Výchozí vypnuto (nové chování); zapíná nastavení.
     */
    @Volatile var objectiveColors = false

    /** Nejvýš tolik slov úkolu se čte na barvu (jedna malá žádost za slovo, jen u nového úkolu). */
    private val OBJ_WORDS = 20

    /**
     * Barvy slov úkolu z obrazu: za každé slovo (rámeček čtečky bez okrajů) jedna žádost u nejbližšího snímku.
     * Výsledek = barva každého slova (null = bílé / nečitelné); null = slova neznámá.
     */
    private fun askObjectiveColors(words: List<OcrLine>, x0: Int, y0: Int): kotlinx.coroutines.CompletableDeferred<List<Int?>>? {
        val r = reader ?: return null
        if (words.isEmpty()) return null
        val out = arrayOfNulls<Int>(words.size)
        val left = java.util.concurrent.atomic.AtomicInteger(words.size)
        val d = kotlinx.coroutines.CompletableDeferred<List<Int?>>()
        val one = { if (left.decrementAndGet() == 0) d.complete(out.toList()) }
        words.forEachIndexed { i, w ->
            val b = cz.recaption.core.NameColor.inset(w.left, w.top, w.right, w.bottom)
            if (b == null) { one(); return@forEachIndexed }
            r.requestColor(StreamReader.ColorRequest("úkol",
                (x0 + b[0]).toFloat() / capW, (y0 + b[1]).toFloat() / capH,
                (x0 + b[2]).toFloat() / capW, (y0 + b[3]).toFloat() / capH) { rgb, _ -> out[i] = rgb; one() })
        }
        return d
    }

    /**
     * Barevné kusy anglického úkolu přenesené do překladu [translation]: kus najdený v překladu doslova (jména, místa),
     * jinak jeho samostatný offline překlad; nenalezený kus zůstane ve výchozí barvě. null = nic barevného.
     */
    private suspend fun objectiveParts(words: List<String>, colors: List<Int?>, translation: String): List<cz.recaption.core.ObjectivePart>? {
        val runs = cz.recaption.core.ObjectiveColors.runs(words, colors)
        if (runs.isEmpty()) return null
        val l = lang
        val spans = ArrayList<Pair<IntRange, Int>>()
        for (run in runs.take(6)) {
            val taken = spans.map { it.first }
            val hit = cz.recaption.core.ObjectiveColors.locate(translation, run.text, taken)
                ?: translate(l, TextLogic.forTranslator(cz.recaption.core.ObjectiveColors.core(run.text)))
                    ?.let { cz.recaption.core.ObjectiveColors.locate(translation, it, taken, tail = run.last) }
            if (hit != null) spans.add(hit to run.color)
        }
        return cz.recaption.core.ObjectiveColors.parts(translation, spans)
    }

    /** Úkol pryč (menu hry, nové nastavení, pauza). */
    private fun dropObjective() {
        objectiveTracker.reset(); objectiveSeq++
        overlay?.hideObjective()
    }

    // ---------------- překlad ----------------

    /** Začne překládat titulek hned po prvním přečtení, ještě před potvrzením druhým snímkem. */
    private fun prefetch(text: String) {
        if (text.isEmpty() || inflight.containsKey(text)) return
        if (inflight.size > 6) inflight.clear()   // staré nepotřebné (titulek se mezitím změnil)
        inflight[text] = scope.async { translateSegments(text) }
    }

    /** Přeloží text po částech; jména mluvčích se nepřekládají. */
    /** [imperative] = úkol ve hře: pokyn přeložit jako rozkaz ([cz.recaption.core.ObjectiveImperative], „Jeď k jachtě“, ne „Cesta k jachtě“). */
    /** [speaker] = mluvčí pro části bez jména v textu (zpráva v telefonu od kontaktu) – jen pro rod, do výsledku nejde. */
    private suspend fun translateSegments(text: String, imperative: Boolean = false, speaker: String? = null): List<Segment>? {
        val l = lang   // jazyk se může během překladu změnit – celý text se překládá do jednoho
        val en = source.english
        val out = ArrayList<Segment>()
        val segs = TextLogic.segments(text).let { if (prompts) TextLogic.markPrompt(it) else it }
        for (s in segs) {
            val body = TextLogic.normalizeCase(s.body, en)
            // rychlá slovíčka jsou anglická – u jiného jazyka titulků jde všechno překladači
            val asCommand = imperative && cz.recaption.core.ObjectiveImperative.applies(body, l, en)
            // popisky zvuků v závorkách („[laughs]“) zvlášť: vlastní slovník, jinak překladač, a zpátky do závorek (od 0.97.116)
            val pieces = if (imperative) emptyList() else cz.recaption.core.Captions.pieces(body)
            if (pieces.any { it.second }) {
                val parts = ArrayList<String>()
                for ((t, cap) in pieces) {
                    if (cap) {
                        val (inner, music) = cz.recaption.core.Captions.inner(t)
                        val w = cz.recaption.core.Captions.word(inner, l) ?: (if (inner.isEmpty()) "" else translate(l, TextLogic.forTranslator(inner)) ?: return null)
                        parts += if (inner.isEmpty()) t else cz.recaption.core.Captions.wrap(TextLogic.finishTranslation(inner, w), music)
                    } else {
                        val tr0 = (if (en) QuickWords.lookup(l.code, t) else null) ?: translate(l, TextLogic.forTranslator(t)) ?: return null
                        parts += TextLogic.finishTranslation(t, glossary?.genders(l, t, tr0, s.speaker ?: speaker, en) ?: tr0)
                    }
                }
                out.add(Segment(s.speaker?.let { TextLogic.normalizeName(it) }, parts.joinToString(" "), s.choice, s.prompt, caption = s.caption))
                continue
            }
            val raw = (if (asCommand) translate(l, cz.recaption.core.ObjectiveImperative.prepare(body))?.let { cz.recaption.core.ObjectiveImperative.finish(it) } else null)
                ?: (if (en) QuickWords.lookup(l.code, body) else null) ?: translate(l, TextLogic.forTranslator(body)) ?: return null
            // rod postav (mluvčí, oslovený) – až po paměti překladů, ta je jen podle textu
            val tr = glossary?.genders(l, body, raw, s.speaker ?: speaker, en) ?: raw
            out.add(Segment(s.speaker?.let { TextLogic.normalizeName(it) }, TextLogic.finishTranslation(body, tr), s.choice, s.prompt))
        }
        return out
    }

    private suspend fun translate(l: Lang, text: String): String? {
        if (l == lang) cache.get(text)?.let { statsFor(this).cacheHit(); return it }
        val out = try { OfflineTranslator.translate(l, text, glossary) } catch (e: Exception) {
            lastError = e.javaClass.simpleName; Log.w(TAG, "offline", e)
            // balíček chybí (stažení se nepovedlo nebo ho systém smazal) → stáhnout znovu, nejvýš jednou za 30 s
            val now = SystemClock.uptimeMillis()
            if (e.message?.contains("model", ignoreCase = true) == true && now - lastModelRetry > 30_000) {
                lastModelRetry = now
                android.os.Handler(android.os.Looper.getMainLooper()).post { if (running) prepareModel(l) }
            }
            null
        }
        // jazyk se mezitím změnil → do paměti nového jazyka nepatří překlad do starého
        if (out != null) { if (l == lang) cache.put(text, out); lastError = null }
        return out
    }

    // ---------------- pokus: DeepL ----------------

    /** Klíč DeepL, když je pokus zapnutý (jinak null a vše běží jako dřív). Nikam se nevypisuje. */
    @Volatile private var deeplKey: String? = null
    /** Pořadí zobrazení v pruhu: odpověď DeepL smí vyměnit jen ten řádek, ke kterému patří. */
    private val showLock = Any()

    /** Jeden titulek k zobrazení (pro frontu [showQueue]). */
    private class Shown(val ov: SubtitleOverlay, val segs: List<Segment>, val original: String?,
                        val top: Float, val bottom: Float, val left: Float, val right: Float, val lineH: Float,
                        /** Anglický titulek, ze kterého překlad je. */
                        val en: String = "")

    /** Fronta rychlých replik s nejkratší dobou zobrazení ([cz.recaption.core.ShowQueue], od 0.97.46). */
    private val showQueue = cz.recaption.core.ShowQueue<Shown>(0, replaces = { old, new ->
        old.ov === new.ov && cz.recaption.core.StableLines.supersedes(old.en, new.en)
    })
    /** Paměť ukázaných řádků a jejich překladů ([cz.recaption.core.RecentLines], od 0.97.83). */
    private val recentLines = cz.recaption.core.RecentLines()
    /** Pokus „Jen nové repliky“ (⚙ → Experimentální, výchozí vypnuto). */
    @Volatile private var onlyNewLines = false
    /** Naposledy poslaný překlad do pruhu nebo fronty – stejný se znovu neposílá. */
    private var lastPresented: Shown? = null

    /**
     * Překlad k zobrazení: stejná replika dostane překlad, se kterým už v pruhu byla ([cz.recaption.core.RecentLines.stabilize]);
     * s pokusem „Jen nové repliky“ jen repliky, které v tomto rozhovoru ještě nebyly. null = nic nového k ukázání.
     * Vrací segmenty a originál (když se ukazuje).
     */
    private fun stableSegments(text: String, en: List<Segment>, tr: List<Segment>, correction: Boolean): Pair<List<Segment>, String?>? {
        val now = SystemClock.uptimeMillis()
        val fresh = if (onlyNewLines && en.size == tr.size) recentLines.fresh(en, now) else null
        val out = recentLines.stabilize(en, tr, now, preferNew = correction)
        if (fresh == null || fresh.size == out.size) return out to (if (showOriginal) text else null)
        if (fresh.isEmpty()) { tp("SHOW|bez novych"); return null }
        return fresh.map { out[it] } to (if (showOriginal) TextLogic.joinSegments(fresh.map { en[it] }) else null)
    }
    /** Pořadí posledního titulku, který šel do pruhu nebo do fronty – starší se už nezařadí. */
    private var presentedSeq = 0L
    private val queueHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val queueTick = Runnable { pollQueue() }

    /** Titulek do pruhu: hned, nebo do fronty, když předchozí ještě nebyl vidět nejkratší dobu. Volat pod [showLock]. */
    private fun present(s: Shown, seq: Long) {
        if (seq <= presentedSeq) return
        // stejný překlad, jaký už je v pruhu nebo čeká ve frontě (varianta čtení, titulek se vrátil po krátkém
        // výpadku čtení) → neposílat znovu: pruh by blikl a ve frontě by stejná replika byla dvakrát
        val last = lastPresented
        if (last != null && last.ov === s.ov && last.segs == s.segs && last.original == s.original &&
            (s.ov.isShown || showQueue.waitingCount > 0)) {
            presentedSeq = seq
            showQueue.cancelHide(); hideAt = 0
            tp("SHOW|stejne")
            return
        }
        lastPresented = s
        presentedSeq = seq
        when (val a = showQueue.submit(s, SystemClock.uptimeMillis())) {
            is cz.recaption.core.ShowQueue.Action.Show -> display(a.value)
            else -> tp("QUEUE|wait ${showQueue.waitingCount}")
        }
        hideAt = 0
        scheduleQueue()
    }

    private fun display(s: Shown) {
        if (overlay !== s.ov) return
        sampleShown = false   // skutečný titulek nahradil ukázku rychlé nabídky
        StatusPanel.onSubtitle()
        s.ov.show(s.segs, s.original, s.top, s.bottom, s.left, s.right, lineHeight = s.lineH)
        // titulky ke čtení v telefonu: jen repliky v pořadí, jak se ukázaly (bez pokynů), barvy jmen jako v pruhu
        val replies = s.segs.filter { !it.prompt }
        cz.recaption.core.ReadFeed.add(s.en, replies, s.ov.feedColors(replies))
    }

    private fun pollQueue() {
        synchronized(showLock) {
            when (val a = showQueue.poll(SystemClock.uptimeMillis())) {
                is cz.recaption.core.ShowQueue.Action.Show -> { display(a.value); tp("QUEUE|next ${showQueue.waitingCount}") }
                cz.recaption.core.ShowQueue.Action.Hide -> overlay?.hide()
                else -> {}
            }
        }
        scheduleQueue()
    }

    private fun scheduleQueue() {
        queueHandler.removeCallbacks(queueTick)
        val d = showQueue.dueIn(SystemClock.uptimeMillis())
        if (d >= 0) queueHandler.postDelayed(queueTick, d + 10)
    }

    /** Fronta pryč (pauza, menu hry, nové nastavení, konec). */
    private fun clearQueue() {
        queueHandler.removeCallbacks(queueTick)
        synchronized(showLock) { showQueue.clear(); lastPresented = null }
    }
    private var showSeq = 0L
    /**
     * [showSeq] v chvíli, kdy anglický titulek zmizel ([SubtitleTracker.Action.Hide]) – odpověď DeepL pro titulek
     * s pořadím do tohohle čísla přišla pozdě a neukáže se. Dávka 5QKC-T4US: DeepL odpověděl 460 ms po HIDE,
     * `present` → `ShowQueue.submit` zrušil čekající schování a nápis slitý s replikou visel v pruhu
     * dalších 10 s až do další repliky. Totéž bez fronty (`hideAt = 0` v [present]).
     */
    private var hiddenSeq = 0L

    /**
     * DeepL zapnutý: počká se na jeho překlad (nejvýš [cz.recaption.core.Deepl.WAIT_MS]) a ukáže se jen ten;
     * když nepřijde včas nebo selže, ukáže se offline překlad. Pruh se mezitím nemění (žádné přeblikávání).
     * Neukáže se nic, když mezitím titulek zmizel nebo přišel jiný (stejný začátek čtení i pořadí zobrazení).
     */
    private fun deeplThenShow(job: DeeplTranslator.Job, offline: List<Segment>, token: Long, seq: Long, ov: SubtitleOverlay,
                              show: (List<Segment>) -> Unit) {
        scope.launch {
            val started = SystemClock.uptimeMillis()
            val pending = async { DeeplTranslator.fetch(job) }
            val r = withTimeoutOrNull(cz.recaption.core.Deepl.WAIT_MS) { pending.await() }
            val texts = r?.texts
            val chosen = if (texts != null) DeeplTranslator.merge(offline, job, texts) else offline
            val shown = synchronized(showLock) {
                // s frontou se pozdní překlad nezahodí (rychlé repliky za sebou), jen když se už ukázalo něco novějšího
                // titulek mezitím zmizel (HIDE) → nic: pruh by po schování znovu naskočil a už by se neschoval
                (seq > hiddenSeq &&
                    (if (showQueue.minMs > 0) seq > presentedSeq else tracker.isCurrent(token) && seq == showSeq) && overlay === ov)
                    .also { if (it) show(chosen) }
            }
            val ms = r?.ms ?: (SystemClock.uptimeMillis() - started)
            val label = when {
                !shown && seq <= hiddenSeq -> "hidden"
                !shown -> "stale"
                texts != null -> "deepl"
                r == null -> "timeout-offline"
                else -> "error-" + r.problem?.name?.lowercase() + "-offline"
            }
            DeeplTranslator.stats.answer(ms, when {
                !shown -> cz.recaption.core.Deepl.Swap.STALE
                texts != null -> cz.recaption.core.Deepl.Swap.YES
                else -> cz.recaption.core.Deepl.Swap.LATE
            }, r?.chars ?: 0)
            tp(cz.recaption.core.Deepl.logLine(ms, label, r?.chars ?: 0))
        }
    }

    // ---------------- paměť překladů ----------------

    private fun cacheFile() = File(filesDir, "$CACHE_PREFIX${lang.code}.tsv")

    private fun loadCache() {
        try {
            val f = cacheFile()
            if (f.exists()) cache.load(f.readText(Charsets.UTF_8))
        } catch (e: Exception) { Log.w(TAG, "cache load", e) }
    }

    private fun saveCache() {
        try {
            val f = cacheFile()
            val tmp = File(filesDir, f.name + ".tmp")
            tmp.writeText(cache.dump(), Charsets.UTF_8)
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        } catch (e: Exception) { Log.w(TAG, "cache save", e) }
        stats?.let { Prefs.setStats(this, it) }
    }

    // ---------------- diagnostika ----------------

    private fun publishDebug(ov: SubtitleOverlay) {
        val secs = max(1L, (SystemClock.uptimeMillis() - statsStart) / 1000)
        val pair = (if (source.english) "" else source.code + "→") + lang.code
        val mode = if (OfflineTranslator.isReady(lang)) "offline $pair" else "offline $pair – model se stahuje"
        val r = reader
        val src = if (demo) "ukázka" else (if (xbox) "Xbox " else "PS5 ") + linkState + (linkReason?.let { " ($it)" } ?: "")
        val ecoInfo = if (perf.eco) " · úsporný režim" else ""
        val zoneInfo = if (!autoFind) "ručně" else if (autoZone.learned != null) "auto – titulky nalezeny" else "auto – hledám"
        val err = lastError?.let { "\nchyba překladu: ${it.take(60)}" } ?: ""
        ov.debug(
            "zdroj: $src · video ${r?.frameWidth}×${r?.frameHeight} · přijato ${r?.fed} · dekódováno ${r?.decoded}\n" +
                "čtení ${ocrRuns / secs}/s · ${lastMs} ms · překlad: $mode · paměť ${cache.size()}$ecoInfo\n" +
                "oblast: $zoneInfo · na obrazovce: " + (TitulkyAccessibility.foregroundPackage ?: "?") + "\n" +
                "čtu: ${lastOcrText.replace('\n', ' ').take(70)}$err"
        )
    }

    // ---------------- záznam okolností (proč se spojení rozpadlo) ----------------
    // Jen se zapisuje do záznamu událostí a hlášení – na čtení ani spojení to nemá vliv.

    private var netCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var screenReceiver: android.content.BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        self = this
        // nastavení z telefonu jde měnit i při hraní (jen Android TV / box)
        try { RemoteHost.start(this) } catch (_: Throwable) {}
        try {
            val cm = getSystemService(android.net.ConnectivityManager::class.java)
            val cb = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(n: android.net.Network) {
                    tp("NET|připojeno ${NetInfo.kind(this@TranslatorService)}")
                    // nová síť = možná nová adresa: záznam pro vývojáře přesunout (adresa se přidělí za chvilku)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ NetLogShare.refresh(this@TranslatorService) }, 4000)
                }
                override fun onLost(n: android.net.Network) { tp("NET|síť ztracena") }
            }
            cm.registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (_: Throwable) {}
        try {
            val r = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    tp("SCREEN|" + when (i.action) { Intent.ACTION_SCREEN_OFF -> if (NetInfo.isHandheld(this@TranslatorService)) "displej telefonu zhasl" else "obrazovka zhasla (televize se uspává)"; Intent.ACTION_SCREEN_ON -> "obrazovka rozsvícena"; else -> i.action })
                }
            }
            registerReceiver(r, android.content.IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) })
            screenReceiver = r
        } catch (_: Throwable) {}
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // jen opravdový nedostatek paměti (ne odchod z aplikace – úrovně 20 a 40), ať záznam nemate
        if (level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) tp("MEM|systému dochází paměť (úroveň $level)")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        tp("MEM|systému došla paměť")
    }

    override fun onDestroy() {
        queueHandler.removeCallbacks(queueTick)
        tvStatusHandler.removeCallbacks(tvStatusTick)
        tvStatusHandler.removeCallbacks(updateTick); tvStatusHandler.removeCallbacks(panelTick); tvStatusHandler.removeCallbacks(gamesTick)
        tvStatusHandler.removeCallbacks(usageTick)
        StatusPanel.reset()
        if (self === this) self = null
        QuickLook.close()
        try { netCallback?.let { getSystemService(android.net.ConnectivityManager::class.java).unregisterNetworkCallback(it) } } catch (_: Throwable) {}
        netCallback = null
        try { screenReceiver?.let { unregisterReceiver(it) } } catch (_: Throwable) {}
        screenReceiver = null
        SnapRecorder.stop("překlad zastaven")
        if (running) tp("STOP|čtení=$ocrRuns|průměr=${if (ocrRuns > 0) ocrMsSum / ocrRuns else 0}ms|titulků=$shows")
        readSlow = false
        running = false
        paused = false
        demo = false
        xbox = false
        scope.cancel()
        try { subtitleThread.close(); sideThread.close() } catch (_: Throwable) {}
        saveCache()
        LineCollector.flush(this)
        overlay?.destroy(); overlay = null
        // aplikace je vidět → spojení s televizí si převezme (TvIdle), televize zůstane připojená; jinak zavřít jako dřív
        tv?.let { t -> if (UiState.visible && t.running) { tv = null; activeTv = null; try { TvIdle.adopt(this, t) } catch (_: Throwable) { t.stop() } } }
        tv?.stop(); tv = null; activeTv = null
        link?.stop(); link = null
        feeder?.stop(); feeder = null
        try { wifiLock?.let { if (it.isHeld) it.release() } } catch (_: Throwable) {}
        wifiLock = null
        try { lowLatencyLock?.let { if (it.isHeld) it.release() } } catch (_: Throwable) {}
        lowLatencyLock = null
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Throwable) {}
        wakeLock = null
        try { dimLock?.let { if (it.isHeld) it.release() } } catch (_: Throwable) {}
        dimLock = null
        activeReader = null
        reader?.stop(); reader = null
        try { recognizer.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
