package cz.recaption

import android.content.Context
import android.os.Build
import cz.recaption.core.DiagLog
import cz.recaption.core.LineBank
import cz.recaption.core.ReportUpload
import cz.recaption.core.TvSender
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * „Sbírání replik ze hry“ (⚙ → Čtení a překlad, od 0.97.139; od 1.00 výchozí zapnuto bez dotazu – `Prefs.collectLines`).
 * Služba po každém ukázaném titulku (a od 1.00 i úkolu, menu, telefonu, tipu… – druh v [LineBank]) zavolá [add] (jen zápis do paměti); ukládání do `filesDir/repliky/repliky.tsv`
 * a odesílání do schránky vývojáře (stejný server jako „Poslat vývojáři“, druh `repliky`) běží na jednom vlákně
 * na pozadí. Odesílá se samo, když se nasbírá aspoň [LineBank.MIN_BATCH] čtení nebo uplyne den (bez vlastních mezí
 * replik na serveru – `Prefs.linesServer` – nejvýš 1× denně); ručně
 * v nastavení (Odeslat teď). Vypnutí smaže všechno nasbírané ze zařízení. Logika: core/LineBank.kt.
 */
object LineCollector {
    private val bank = LineBank()
    @Volatile private var loaded = false
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "tp-repliky").apply { isDaemon = true } }
    @Volatile private var lastSave = 0L
    @Volatile private var lastTry = 0L
    @Volatile private var sending = false
    /** Poslední výsledek odeslání (pro nastavení): null = nic, jinak klíč textu chyby, "" = povedlo se. */
    @Volatile var lastResult: String? = null
        private set

    private const val SAVE_EVERY_MS = 30_000L

    private fun file(c: Context) = File(File(c.filesDir, "repliky"), "repliky.tsv")

    /** Načte uložený seznam (na vlákně sběru, jednou). */
    private fun ensureLoaded(c: Context) {
        if (loaded) return
        val text = try { file(c).takeIf { it.isFile }?.readText() } catch (_: Throwable) { null }
        synchronized(bank) { if (!loaded) { bank.load(text); loaded = true } }
    }

    /** Počty pro nastavení: (různých replik, čtení k odeslání); null = ještě nenačteno (načte se na pozadí). */
    fun summary(c: Context, onReady: (() -> Unit)? = null): Pair<Int, Int>? {
        if (!loaded) { val app = c.applicationContext; worker.execute { ensureLoaded(app); onReady?.invoke() }; return null }
        return synchronized(bank) { bank.size to bank.pendingCount }
    }

    /**
     * Replika ukázaná v pruhu (nebo jiný text hry – [kind], viz [LineBank.KINDS]): [game] = id profilu hry,
     * [source] = jazyk titulků. Volá služba – jen zápis do paměti, uložení a odeslání se naplánují na pozadí.
     */
    fun add(c: Context, game: String, source: String, speaker: String?, body: String, kind: String = LineBank.REPLIKA) {
        val app = c.applicationContext
        if (!loaded) { worker.execute { ensureLoaded(app); add(app, game, source, speaker, body, kind) }; return }
        val now = System.currentTimeMillis()
        val (changed, pending) = synchronized(bank) { bank.add(game, source, speaker, body, now, kind) to bank.pendingCount }
        if (!changed) return
        if (now - lastSave > SAVE_EVERY_MS) { lastSave = now; worker.execute { save(app) } }
        val gap = if (Prefs.linesServer(app)) 0L else LineBank.DAY_MS
        if (!sending && LineBank.due(pending, Prefs.linesSentAt(app), lastTry, now, gap)) {
            sending = true; lastTry = now
            worker.execute { upload(app) }
        }
    }

    /** Konec překladu: uložit, co je v paměti. */
    fun flush(c: Context) {
        if (!loaded) return
        val app = c.applicationContext
        worker.execute { save(app) }
    }

    /** Odeslat teď (z nastavení); [done] na hlavním vlákně s výsledkem (null = povedlo se, jinak klíč chyby). */
    fun sendNow(c: Context, done: (String?) -> Unit) {
        val app = c.applicationContext
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        if (sending) { main.post { done("snd_e_busy") }; return }
        sending = true; lastTry = System.currentTimeMillis()
        worker.execute {
            ensureLoaded(app)
            val r = upload(app)
            main.post { done(r) }
        }
    }

    /** Vypnutí: souhlas pryč a nasbírané repliky smazané ze zařízení. */
    fun turnOff(c: Context) {
        Prefs.setCollectLines(c, false)
        val app = c.applicationContext
        worker.execute {
            synchronized(bank) { bank.clear(); bank.dirty = false }
            loaded = true
            try { file(app).delete() } catch (_: Throwable) {}
            DiagLog.add("LINES|vypnuto, smazáno")
        }
    }

    private fun save(c: Context) {
        val text = synchronized(bank) { if (!bank.dirty) return; bank.dirty = false; bank.save() }
        try {
            val f = file(c)
            f.parentFile?.mkdirs()
            val part = File(f.parentFile, f.name + ".part")
            part.writeText(text)
            if (!part.renameTo(f)) { f.delete(); part.renameTo(f) }
        } catch (_: Throwable) { synchronized(bank) { bank.dirty = true } }
    }

    /** Odeslání na vlákně sběru; vrací null (povedlo se / nic k odeslání) nebo klíč chyby. */
    private fun upload(c: Context): String? {
        try {
            if (!Prefs.collectLines(c)) return null
            val (list, counts) = synchronized(bank) { bank.pendingEntries().let { l -> l to l.map { it.count } } }
            if (list.isEmpty()) { lastResult = ""; return null }
            val tsv = synchronized(bank) { LineBank.upload(list) }
            val cfg = try { ReportUpload.config(get(ReportUpload.CONFIG_URL)) } catch (_: IOException) { return fail(ReportUpload.E_NET) }
                ?: return fail(ReportUpload.E_OFF)
            // má server pro repliky vlastní meze? (jinak by sdílely denní mez hlášení testerů → odesílá se nejvýš 1× denně)
            try { stav(cfg)?.let { Prefs.setLinesServer(c, it) } } catch (_: IOException) {}
            val games = list.map { it.game }.distinct().joinToString(",")
            val zip = zip(tsv, info(games, list.size, counts.sum()))
            val ans = post(c, cfg, zip, games)
            if (!ans.ok) return fail(ans.error ?: ReportUpload.E_FAIL)
            synchronized(bank) { bank.markSent(list, counts) }
            Prefs.setLinesSentAt(c, System.currentTimeMillis())
            save(c)
            DiagLog.add("LINES|odeslano ${list.size} replik (${counts.sum()} čtení) · ${ans.code}")
            lastResult = ""
            return null
        } catch (_: IOException) {
            return fail(ReportUpload.E_NET)
        } catch (_: Throwable) {
            return fail(ReportUpload.E_FAIL)
        } finally { sending = false }
    }

    private fun fail(key: String): String { lastResult = key; DiagLog.add("LINES|neodeslano $key"); return key }

    /** `info.json`: jen verze aplikace, hra, jazyk a počty (nic o zařízení ani hráči). */
    private fun info(games: String, lines: Int, readings: Int): String = ReportUpload.infoJson(listOf(
        "druh" to ReportUpload.KIND_LINES,
        "format" to 1,
        "verze" to BuildInfo.VERSION.removePrefix("verze "),
        "verzeKod" to BuildInfo.VERSION_CODE,
        "hra" to games,
        "replik" to lines,
        "cteni" to readings,
    ))

    private fun zip(tsv: String, info: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry(ReportUpload.ZIP_LINES)); z.write(tsv.toByteArray(Charsets.UTF_8)); z.closeEntry()
            z.putNextEntry(ZipEntry(ReportUpload.ZIP_INFO)); z.write(info.toByteArray(Charsets.UTF_8)); z.closeEntry()
        }
        return out.toByteArray()
    }

    private fun get(url: String): String? {
        val h = URL(url).openConnection() as HttpURLConnection
        h.connectTimeout = 8000; h.readTimeout = 10000; h.useCaches = false
        h.setRequestProperty("Cache-Control", "no-cache")
        return try { if (h.responseCode != 200) null else read(h.inputStream) } finally { h.disconnect() }
    }

    /** `GET /v1/stav` → umí server vlastní meze replik? null = nezjištěno. */
    private fun stav(cfg: ReportUpload.Config): Boolean? {
        val o = get(cfg.url + "/v1/stav")?.let { cz.recaption.core.MiniJson.obj(it) } ?: return null
        return o["repliky"] == true
    }

    /** Stejné nepovinné hlavičky se zařízením/TV/hrou jako `ReportSender` – jen pro zběžné roztřídění na stránce počtů. */
    private fun headerSafe(s: String?): String? =
        s?.trim()?.filter { it.code >= 32 }?.take(ReportUpload.HEADER_MAX)?.takeIf { it.isNotEmpty() }

    private fun post(c: Context, cfg: ReportUpload.Config, body: ByteArray, games: String): ReportUpload.Answer {
        if (body.size > cfg.maxBytes) return ReportUpload.error(ReportUpload.E_BIG)
        val h = URL(cfg.uploadUrl).openConnection() as HttpURLConnection
        try {
            h.requestMethod = "POST"; h.doOutput = true; h.useCaches = false; h.instanceFollowRedirects = false
            h.connectTimeout = 15000; h.readTimeout = 60000
            h.setFixedLengthStreamingMode(body.size)
            h.setRequestProperty("Content-Type", "application/zip")
            h.setRequestProperty(ReportUpload.HEADER_VERSION, BuildInfo.VERSION_CODE.toString())
            h.setRequestProperty(ReportUpload.HEADER_KIND, ReportUpload.KIND_LINES)
            val tv = TvSender.lastSeen?.announce
            headerSafe(Build.MANUFACTURER + " " + Build.MODEL)?.let { h.setRequestProperty(ReportUpload.HEADER_DEVICE, it) }
            headerSafe(Prefs.tvType(c).key)?.let { h.setRequestProperty(ReportUpload.HEADER_TV_TYPE, it) }
            headerSafe(listOfNotNull(tv?.platform, tv?.version).joinToString(" "))?.let { h.setRequestProperty(ReportUpload.HEADER_TV_INFO, it) }
            headerSafe(games)?.let { h.setRequestProperty(ReportUpload.HEADER_GAME, it) }
            h.outputStream.use { it.write(body) }
            val status = h.responseCode
            val text = try { read(if (status < 400) h.inputStream else h.errorStream) } catch (_: IOException) { null }
            return ReportUpload.answer(status, text)
        } finally { h.disconnect() }
    }

    private fun read(s: java.io.InputStream?): String? = s?.use { inp ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (out.size() <= ReportUpload.MAX_ANSWER) { val n = inp.read(buf); if (n < 0) break; out.write(buf, 0, n) }
        if (out.size() > ReportUpload.MAX_ANSWER) null else out.toString("UTF-8")
    }
}
