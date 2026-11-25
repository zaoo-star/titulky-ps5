package cz.recaption

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import cz.recaption.core.DiagLog
import cz.recaption.core.Redact
import cz.recaption.core.ScreenReads
import cz.recaption.core.SnapBatches
import cz.recaption.core.SnapPacer
import java.io.File

/**
 * Záznam snímků pro ladění (⚙ → Experimentální, od 1.00-rc19 pro všechny; „Záznam snímků“, výchozí vypnuto; i tlačítkem
 * v notifikaci, když je zapnutý záznam v domácí síti). Start…Stop = jedna dávka (viz [SnapBatches]): vlastní vlákno
 * každých [SnapBatches.POLL_MS] porovná zmenšený obraz a při změně uloží celý snímek (barevně, když ho dekodér dá,
 * jinak šedě) a řádek do `index.txt`. Čtení titulků se nedotkne; vypnuté = nic neběží ani za snímek.
 * Jen při běžícím překladu (bez něj nejsou snímky); po [SnapBatches.MAX_MS] a se zastavením překladu se zastaví samo.
 */
object SnapRecorder {
    @Volatile var active = false
        private set
    /** Číslo a složka právě nahrávané (nebo naposledy nahrané) dávky a počet snímků v ní. */
    @Volatile var batch = 0
        private set
    @Volatile var count = 0
        private set
    @Volatile private var current: String? = null
    @Volatile private var thread: Thread? = null
    /** Pořadí spuštění: vlákno starší dávky (rychlé Stop → Start) už nic nezapíše ani nezastaví. */
    @Volatile private var gen = 0
    /** Souhrn uložených dávek (počet dávek, snímků, bajtů) – počítá se mimo hlavní vlákno; null = ještě nespočítán. */
    @Volatile var summary: Triple<Int, Int, Long>? = null
        private set
    @Volatile private var summing = false
    /** Vlákno právě zastavené dávky – může ještě dopisovat poslední snímek a řádek indexu ([settle]). */
    @Volatile private var stopping: Thread? = null
    /**
     * Právě zastavená dávka má snímky a souhrn ještě není přepočítaný: Odeslat na Domů je k dispozici hned po Stop
     * (dřív bylo tlačítko do přepočtu šedé a nedělalo nic – „Odeslat hned po zastavení se zasekne“, hlášení 22VF-GGWU).
     */
    @Volatile var justStopped = false
        private set

    fun root(c: Context) = File(c.applicationContext.filesDir, SnapBatches.DIR)

    /** Název právě nahrávané dávky (pro seznam `/davky`), nebo null. */
    val recording: String? get() = if (active) current else null

    /** Start / Stop; false = nejde spustit (překlad neběží). */
    fun toggle(c: Context): Boolean = if (active) { stop("tlačítkem"); true } else start(c)

    @Synchronized fun start(c: Context): Boolean {
        if (active) return true
        if (!TranslatorService.running) return false
        val ctx = c.applicationContext
        val root = root(ctx)
        root.mkdirs()
        val now = System.currentTimeMillis()
        val n = SnapBatches.next((root.list() ?: emptyArray()).toList())
        val dir = File(root, SnapBatches.dirName(n, now))
        if (!dir.mkdirs()) return false
        val head = listOf(
            "ReCaption ${BuildInfo.VERSION} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}",
            "zdroj: " + (if (TranslatorService.demo) "ukázka" else if (TranslatorService.xbox) "Xbox" else "PlayStation") +
                " · hra: ${Prefs.gameId(ctx)}",
        )
        val my = ++gen
        batch = n; count = 0; current = dir.name; active = true; justStopped = false
        if (!Prefs.snapButton(ctx)) Prefs.setSnapButton(ctx)
        DiagLog.add("SNAP|start ${dir.name}")
        // mazání starých dávek, klíče (Keystore) a hlavička indexu až ve vlákně – start běží z hlavního vlákna
        thread = Thread({
            SnapBatches.prune(root, SnapBatches.CAP_BYTES, dir.name)
            // tajné hodnoty jednou na začátku (čtení klíčů něco stojí), řádky indexu jdou přes Redact
            val secrets = try { DiagReport.secrets(ctx) } catch (_: Throwable) { emptyList() }
            try { File(dir, SnapBatches.INDEX).writeText(Redact.clean(SnapBatches.header(n, now, head), secrets), Charsets.UTF_8) } catch (_: Throwable) {}
            loop(ctx, root, dir, now, secrets, my)
        }, "tp-snimky").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
        TranslatorService.refreshNotification()
        return true
    }

    @Synchronized fun stop(why: String) {
        if (!active) return
        active = false
        justStopped = count > 0
        stopping = thread
        thread?.interrupt(); thread = null
        DiagLog.add("SNAP|stop $current · $count snímků · $why")
        TranslatorService.refreshNotification()
        summary = null
    }

    /**
     * Počká (nejvýš [maxMs]), až zastavená dávka dopíše poslední snímek a řádek indexu – volá odeslání a uložení těsně po Stop,
     * ať se nebalí rozepsaná dávka (mimo hlavní vlákno). Bez zastavované dávky se vrátí hned.
     */
    fun settle(maxMs: Long = 2000) {
        val t = stopping ?: return
        if (t === Thread.currentThread()) return
        try { t.join(maxMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        if (!t.isAlive && stopping === t) stopping = null
    }

    /** Stop jen z vlákna dávky [my] – starší vlákno novou dávku nezastaví. */
    @Synchronized private fun stopIf(my: Int, why: String) { if (my == gen) stop(why) }

    /** Souhrn uložených dávek pro Nastavení: spočítá se jednou na pozadí, [done] se zavolá na hlavním vlákně. */
    /**
     * Smaže dávky snímků v telefonu (od 0.97.104, přání majitele: po odeslání je v telefonu nechce): [n] = jen ta jedna,
     * null = všechny. Nahrávaná dávka se nesmaže nikdy. Vrací počet smazaných; souhrn se spočítá znovu.
     */
    fun delete(c: Context, n: Int? = null): Int {
        val root = root(c)
        val keep = recording
        val del = SnapBatches.scan(root).filter { (n == null || it.n == n) && it.name != keep }
        del.forEach { File(root, it.name).deleteRecursively() }
        summary = null
        if (!active) justStopped = false
        if (del.isNotEmpty()) EventFile.add("SNAP|smazáno dávek ${del.size}" + (n?.let { " (dávka $it po odeslání)" } ?: ""))
        return del.size
    }

    fun refreshSummary(c: Context, done: () -> Unit) {
        if (summary != null || summing) return
        summing = true
        val root = root(c)
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        Thread({
            val saved = try { SnapBatches.scan(root) } catch (_: Throwable) { emptyList() }
            summary = Triple(saved.size, saved.sumOf { it.snaps }, saved.sumOf { it.bytes })
            if (!active) justStopped = false
            summing = false
            main.post(done)
        }, "tp-snimky-souhrn").apply { isDaemon = true; start() }
    }

    private fun loop(ctx: Context, root: File, dir: File, startWall: Long, secrets: Collection<String>, my: Int) {
        val pacer = SnapPacer()
        val startUp = SystemClock.uptimeMillis()
        var mark = DiagLog.mark()
        var readsAt = startWall - 1
        val index = File(dir, SnapBatches.INDEX)
        try {
            while (active && my == gen) {
                try { Thread.sleep(SnapBatches.POLL_MS) } catch (_: InterruptedException) { break }
                if (!active || my != gen) break
                val up = SystemClock.uptimeMillis()
                if (up - startUp > SnapBatches.MAX_MS) { stopIf(my, "10 min"); break }
                if (!TranslatorService.running) { stopIf(my, "překlad zastaven"); break }
                val r = TranslatorService.frameReader ?: continue
                val th = r.thumb() ?: continue
                val why = pacer.offer(th, up) ?: continue
                val bmp: Bitmap = r.colorStill(SnapBatches.MAX_W, 600) ?: r.still(SnapBatches.MAX_W) ?: continue
                if (my != gen) { bmp.recycle(); break }
                val i = count + 1
                val name = SnapBatches.snapName(i)
                val w = bmp.width; val h = bmp.height
                try {
                    val tmp = File(dir, "$name.tmp")
                    tmp.outputStream().buffered().use { bmp.compress(Bitmap.CompressFormat.JPEG, SnapBatches.JPEG_QUALITY, it) }
                    if (!tmp.renameTo(File(dir, name))) { tmp.delete(); continue }
                } catch (_: Throwable) { continue } finally { bmp.recycle() }
                count = i
                val wall = System.currentTimeMillis()
                val events = DiagLog.since(mark).filterNot { it.contains("ZAZNAM|") }
                mark = DiagLog.mark()
                val reads = ScreenReads.all().filter { it.time > readsAt }.map { it.raw + (if (it.count > 1) " (${it.count}×)" else "") to it.verdict }
                readsAt = wall
                try {
                    index.appendText(Redact.clean(SnapBatches.entry(i, wall, up - startUp, name, w, h, SnapBatches.whyText(why, i == 1), events, reads), secrets), Charsets.UTF_8)
                } catch (_: Throwable) {}
                // místo: po každých 20 snímcích smazat nejstarší dávky; když nestačí ani to, zastavit
                if (i % 20 == 0) {
                    SnapBatches.prune(root, SnapBatches.CAP_BYTES, dir.name)
                    if (SnapBatches.scan(root).sumOf { it.bytes } > SnapBatches.CAP_BYTES) { stopIf(my, "plné místo"); break }
                }
            }
        } catch (_: Throwable) {
            stopIf(my, "chyba")
        }
    }
}
