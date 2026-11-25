package cz.recaption

import android.content.Context
import android.os.SystemClock
import android.util.Log
import cz.recaption.core.DiagLog
import cz.recaption.core.GameOptions
import cz.recaption.core.ReportUpload
import cz.recaption.core.TvType
import cz.recaption.core.UsageCount
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * Anonymní počítání uživatelů (vždy, bez volby – přání majitele; popis v Nápovědě, kapitola voleb): během překladu nejvýš jednou
 * za 10 min malý ping na server schránky (náhodné číslo zařízení, verze, typ zařízení). Volá ho služba při startu
 * překladu a pak v pravidelném kroku ([maybe] je levné – jen porovnání času); síť běží na vlastním vlákně
 * s krátkým časovým limitem, chyby se jen zapíšou (`PING|chyba`). Logika a tvar pingu: core/UsageCount.kt.
 */
object UsagePing {
    private const val FILE = "pocitani"
    private const val KEY_ID = "id"
    private const val KEY_OPEN = "otevreno_kdy"
    /** Adresa serveru (`hlaseni.json`) se čte znovu nejdřív po této době. */
    private const val CONFIG_MS = 6 * 3600_000L

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "tp-pocitani").apply { isDaemon = true } }
    @Volatile private var lastTry = 0L
    @Volatile private var busy = false
    @Volatile private var server: String? = null
    @Volatile private var serverAt = 0L

    /** Poslat ping, pokud od posledního pokusu uběhlo aspoň 10 min. Hlavní vlákno nic nečeká. */
    fun maybe(c: Context) {
        if (busy) return
        val now = SystemClock.elapsedRealtime()
        if (!UsageCount.due(lastTry, now)) return
        busy = true; lastTry = now
        val app = c.applicationContext
        try { worker.execute { try { send(app, open = false) } finally { busy = false } } } catch (_: Throwable) { busy = false }
    }

    /**
     * Otevření aplikace (od 1.00-rc9): nejvýš jednou za 6 h i bez překladu – podle toho server pozná, kdo aplikaci otevřel
     * a nepřekládá a na kterém kroku průvodce skončil. Čas posledního se pamatuje (i přes nová spuštění). Levné: jen čtení času.
     */
    fun opened(c: Context) {
        if (busy || emulator) return
        val sp = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!UsageCount.openDue(sp.getLong(KEY_OPEN, 0L), now)) return
        sp.edit().putLong(KEY_OPEN, now).apply()
        busy = true
        val app = c.applicationContext
        try { worker.execute { try { send(app, open = true) } finally { busy = false } } } catch (_: Throwable) { busy = false }
    }

    /** Emulátor (zkouška na GitHubu otevírá aplikaci mnohokrát) – otevření se nepočítá. */
    private val emulator: Boolean by lazy {
        val f = android.os.Build.FINGERPRINT ?: ""; val h = android.os.Build.HARDWARE ?: ""
        f.startsWith("generic") || f.contains("emulator") || h == "goldfish" || h == "ranchu"
    }

    /** Názvy voleb, které hráč někdy změnil (uložené klíče nastavení; jen názvy, žádné hodnoty). */
    private fun settings(c: Context): List<String> = try {
        UsageCount.settingKeys(c.getSharedPreferences("test_preklad", Context.MODE_PRIVATE).all.keys)
    } catch (_: Throwable) { emptyList() }

    private fun log(msg: String) { Log.i("TP_TEST", msg); DiagLog.add(msg) }

    /** Náhodné číslo tohoto zařízení – vznikne jednou a uloží se zvlášť (ne mezi nastavením vzhledu). */
    private fun deviceId(c: Context): String {
        val sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        sp.getString(KEY_ID, null)?.takeIf { UsageCount.validId(it) }?.let { return it }
        val rnd = SecureRandom()
        val id = UsageCount.newId { rnd.nextInt(it) }
        sp.edit().putString(KEY_ID, id).apply()
        return id
    }

    private fun type(c: Context): String =
        if (Prefs.isTv(c) || NetInfo.isTvDevice(c)) UsageCount.TYPE_TV else UsageCount.TYPE_PHONE

    private fun tvTarget(c: Context): String = when (Prefs.tvType(c)) {
        TvType.ANDROID -> UsageCount.TARGET_ATV
        TvType.LG -> UsageCount.TARGET_LG
        TvType.SAMSUNG -> UsageCount.TARGET_SAMSUNG
    }

    /** Má hráč vybraný konkrétní profil hry (ne univerzální/žádný)? */
    private fun hasGame(c: Context): Boolean = GameOptions.hasOptions(Prefs.gameId(c))

    /** Aspoň jedna pokročilejší volba hry u aktuálně vybrané hry – vedlejší metrika, stačí aproximace. */
    private fun advancedGame(c: Context): Boolean =
        Prefs.gameMenu(c) || Prefs.cornerMenu(c) > 0 || Prefs.worldPrompts(c) || Prefs.phonePanel(c)

    private fun send(c: Context, open: Boolean) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (server == null || now - serverAt > CONFIG_MS) {
                server = ReportUpload.config(get(ReportUpload.CONFIG_URL))?.url
                serverAt = now
            }
            // prázdná adresa v hlaseni.json = počítání (a odesílání) není nastavené → nic
            val base = server ?: return
            // aplikace je zatím zdarma (License.FREE) – předplatné zatím nikdo nemá
            val body = UsageCount.body(
                deviceId(c), BuildInfo.VERSION_CODE, type(c), subscription = false,
                tvTarget = tvTarget(c),
                deeplActive = DeeplTranslator.active(c),
                lang = Prefs.lang(c).code,
                hasGame = hasGame(c),
                wizardStep = Prefs.wizardMaxStep(c),
                fastRead = Prefs.fastRead(c),
                advanced = advancedGame(c),
                open = open,
                settings = settings(c),
            ).toByteArray(Charsets.UTF_8)
            val h = URL(UsageCount.url(base)).openConnection() as HttpURLConnection
            val status = try {
                h.requestMethod = "POST"; h.doOutput = true; h.useCaches = false; h.instanceFollowRedirects = false
                h.connectTimeout = UsageCount.TIMEOUT_MS; h.readTimeout = UsageCount.TIMEOUT_MS
                h.setFixedLengthStreamingMode(body.size)
                h.setRequestProperty("Content-Type", "application/json")
                h.outputStream.use { it.write(body) }
                h.responseCode
            } finally { h.disconnect() }
            log((if (status in 200..299) "PING|ok" else "PING|chyba $status") + if (open) " otevreno" else "")
        } catch (_: Throwable) {
            log("PING|chyba")
        }
    }

    private fun get(url: String): String? {
        val h = URL(url).openConnection() as HttpURLConnection
        h.connectTimeout = UsageCount.TIMEOUT_MS; h.readTimeout = UsageCount.TIMEOUT_MS; h.useCaches = false
        h.setRequestProperty("Cache-Control", "no-cache")
        return try {
            if (h.responseCode != 200) null else h.inputStream.use { inp ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(1024)
                while (out.size() <= ReportUpload.MAX_ANSWER) { val n = inp.read(buf); if (n < 0) break; out.write(buf, 0, n) }
                if (out.size() > ReportUpload.MAX_ANSWER) null else out.toString("UTF-8")
            }
        } finally { h.disconnect() }
    }
}
