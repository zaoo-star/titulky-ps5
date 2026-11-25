package cz.recaption.core

import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

/**
 * Záznam snímků pro ladění (⚙ → Experimentální → Ladění a hlášení → „Záznam snímků“, od 0.97.59, výchozí vypnuto).
 * Každé Start…Stop je jedna **dávka** ve složce `snimky/davka-<n>-<HHmmss>/`: snímky celé obrazovky hry
 * (`snimek-0001.jpg` …, jen když se obraz změnil) a `index.txt` – ke každému snímku čas a co aplikace v tu chvíli
 * četla a rozhodla (řádky záznamu událostí a čtení z oblasti titulků). Vývojář si dávky stáhne přes záznam
 * v domácí síti (`GET /davky?k=…` seznam, `GET /davka?k=…&n=3` ZIP) skriptem `ci/stahni-davky.ps1`.
 * Tady je všechno bez Androidu (testy `SnapBatchesTest`); nahrávání je `SnapRecorder`.
 */
object SnapBatches {
    /** Složka v datech aplikace. */
    const val DIR = "snimky"
    const val INDEX = "index.txt"
    /** Všechny dávky dohromady nejvýš tolik – starší se mažou. */
    const val CAP_BYTES = 300L * 1024 * 1024
    /** Nahrávání se samo zastaví po 10 minutách (zapomenuté by plnilo paměť). */
    const val MAX_MS = 10 * 60 * 1000L
    /** Jak často se obraz porovnává (ne každý snímek videa). */
    const val POLL_MS = 250L
    /** Běžná změna se uloží nejvýš jednou za sekundu. */
    const val INTERVAL_MS = 1000L
    /** Velká změna po klidu (otevření menu, nový titulek) se uloží hned. */
    const val FAST_MS = 250L
    /** Jak dlouho musí obraz stát, aby velká změna šla hned. */
    const val CALM_MS = 600L
    /** Šířka uloženého snímku. */
    const val MAX_W = 1280
    const val JPEG_QUALITY = 80

    private val NAME = Regex("davka-(\\d{1,6})-(\\d{6})")
    private val SNAP = Regex("snimek-\\d{4,6}\\.jpg")

    fun dirName(n: Int, hhmmss: String) = "davka-$n-$hhmmss"
    fun dirName(n: Int, time: Long) = dirName(n, SimpleDateFormat("HHmmss", Locale.ROOT).format(Date(time)))

    /** Číslo dávky z názvu složky, nebo null (cizí složka). */
    fun number(name: String): Int? = NAME.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()

    /** Číslo nové dávky: o 1 víc než nejvyšší existující. */
    fun next(names: Collection<String>): Int = (names.mapNotNull(::number).maxOrNull() ?: 0) + 1

    fun snapName(i: Int) = "snimek-" + i.toString().padStart(4, '0') + ".jpg"
    fun isSnap(name: String) = SNAP.matches(name)

    /** Jedna dávka: číslo, složka, počet snímků, velikost a čas prvního / posledního souboru. */
    data class Info(val n: Int, val name: String, val snaps: Int, val bytes: Long, val start: Long, val end: Long)

    /** Všechny dávky ve složce [root], od nejstarší (podle čísla). */
    fun scan(root: File): List<Info> =
        (root.listFiles() ?: emptyArray()).filter { it.isDirectory }.mapNotNull { d ->
            val n = number(d.name) ?: return@mapNotNull null
            val files = (d.listFiles() ?: emptyArray()).filter { it.isFile }
            val snaps = files.filter { isSnap(it.name) }
            val times = files.map { it.lastModified() }.filter { it > 0 }
            Info(n, d.name, snaps.size, files.sumOf { it.length() }, times.minOrNull() ?: d.lastModified(), times.maxOrNull() ?: d.lastModified())
        }.sortedBy { it.n }

    /** Složka dávky [n] (jen podle seznamu – cesta z požadavku se nikdy neskládá). */
    fun find(root: File, n: Int): File? =
        (root.listFiles() ?: emptyArray()).firstOrNull { it.isDirectory && number(it.name) == n }

    /** Které dávky smazat, aby celkem nepřesáhly [cap]: od nejstarší, nikdy ne [keep] (právě nahrávaná). */
    fun toDelete(list: List<Info>, cap: Long, keep: String?): List<Info> {
        var total = list.sumOf { it.bytes }
        val out = ArrayList<Info>()
        for (i in list.sortedBy { it.n }) {
            if (total <= cap) break
            if (i.name == keep) continue
            out += i
            total -= i.bytes
        }
        return out
    }

    /** Smaže nejstarší dávky nad [cap]; vrací počet smazaných. */
    fun prune(root: File, cap: Long = CAP_BYTES, keep: String? = null): Int {
        val del = toDelete(scan(root), cap, keep)
        del.forEach { File(root, it.name).deleteRecursively() }
        return del.size
    }

    private fun stamp(t: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(t))

    /** Seznam pro `GET /davky`: {"davky":[{"n":3,"nazev":"davka-3-213005","snimku":42,"bajtu":…,"zacatek":…,"konec":…,"nahrava":false}]}. */
    fun json(list: List<Info>, recording: String?): String =
        list.joinToString(",", "{\"davky\":[", "]}") { i ->
            "{\"n\":${i.n},\"nazev\":${TvSender.json(i.name)},\"snimku\":${i.snaps},\"bajtu\":${i.bytes}," +
                "\"zacatek\":${TvSender.json(stamp(i.start))},\"konec\":${TvSender.json(stamp(i.end))},\"nahrava\":${i.name == recording}}"
        }

    /** Číslo dávky z adresy `/davka?k=…&n=3`, nebo null. */
    fun requested(target: String): Int? =
        LogShare.query(target)["n"]?.takeIf { it.length in 1..6 && it.all(Char::isDigit) }?.toIntOrNull()

    /**
     * Celá dávka jako ZIP rovnou do [out] (soubor po souboru, nic se neskládá v paměti). Jen `index.txt`
     * a hotové snímky (rozepsaný `.tmp` ne). JPEG se už nezmenší – ukládá se bez komprese.
     */
    fun zip(dir: File, out: OutputStream) {
        val files = (dir.listFiles() ?: emptyArray()).filter { it.isFile && (it.name == INDEX || isSnap(it.name)) }.sortedBy { it.name }
        val z = ZipOutputStream(out)
        val buf = ByteArray(64 * 1024)
        for (f in files) {
            z.setLevel(if (f.name == INDEX) Deflater.DEFAULT_COMPRESSION else Deflater.NO_COMPRESSION)
            z.putNextEntry(ZipEntry(f.name).apply { time = f.lastModified() })
            try { f.inputStream().use { inp -> while (true) { val r = inp.read(buf); if (r < 0) break; z.write(buf, 0, r) } } } catch (_: Throwable) {}
            z.closeEntry()
        }
        z.finish()
        z.flush()
    }

    /** Hlavička odpovědi se ZIPem (bez délky – posílá se průběžně, konec = zavření spojení). */
    fun zipHead(name: String): ByteArray =
        ("HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Disposition: attachment; filename=\"$name.zip\"\r\n" +
            "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)

    // ---------------- index.txt ----------------

    /** Hlavička `index.txt` (verze, zařízení, zdroj obrazu…). */
    fun header(n: Int, start: Long, lines: List<String>): String =
        "Titulky PS5 – dávka snímků $n, začátek ${stamp(start)}\n" + lines.joinToString("") { "$it\n" } +
            "Formát: #číslo čas +sekundy od začátku soubor šířka×výška důvod (prvni/zmena/velka); pod tím co aplikace od minulého snímku zapsala (události) a četla (CTENI: surový text → co z něj vzala).\n\n"

    /**
     * Jeden snímek v `index.txt`: řádek se souborem a pod ním odsazené [events] (záznam událostí od minulého
     * snímku) a [reads] (surové čtení oblasti titulků → verdikt). Dlouhé seznamy se zkrátí na posledních 40.
     */
    fun entry(i: Int, time: Long, sinceStartMs: Long, file: String, w: Int, h: Int, why: String,
              events: List<String>, reads: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date(time))
        sb.append('#').append(i.toString().padStart(4, '0')).append(' ').append(clock)
            .append(" +").append(String.format(Locale.ROOT, "%.1f", sinceStartMs / 1000.0)).append("s ")
            .append(file).append(' ').append(w).append('×').append(h).append(' ').append(why).append('\n')
        events.takeLast(40).forEach { sb.append("  ").append(it.replace('\n', ' ').take(300)).append('\n') }
        reads.takeLast(40).forEach { (raw, verdict) -> sb.append("  CTENI: ").append(raw.take(300)).append(" → ").append(verdict.take(200)).append('\n') }
        return sb.toString()
    }

    fun whyText(c: Change, first: Boolean) = when { first -> "prvni"; c == Change.BIG -> "velka"; else -> "zmena" }

    // ---------------- kdy uložit snímek ----------------

    /** Zmenšený obraz pro porovnání: mřížka [TW]×[TH] buněk, v každé průměrný jas z 8×8 vzorků. */
    const val TW = 64
    const val TH = 36

    /** Mřížka jasu z jasové složky [y] (řádek [stride] bajtů, obraz [w]×[h]). */
    fun thumb(y: ByteArray, stride: Int, w: Int, h: Int): IntArray? {
        if (w < TW || h < TH || stride < w || y.size < stride * (h - 1) + w) return null
        val out = IntArray(TW * TH)
        for (cy in 0 until TH) for (cx in 0 until TW) {
            val x0 = cx * w / TW; val x1 = (cx + 1) * w / TW
            val y0 = cy * h / TH; val y1 = (cy + 1) * h / TH
            val sx = maxOf(1, (x1 - x0) / 8); val sy = maxOf(1, (y1 - y0) / 8)
            var sum = 0; var n = 0
            var yy = y0
            while (yy < y1) {
                var xx = x0
                val row = yy * stride
                while (xx < x1) { sum += y[row + xx].toInt() and 0xFF; n++; xx += sx }
                yy += sy
            }
            out[cy * TW + cx] = if (n > 0) sum / n else 0
        }
        return out
    }

    enum class Change { NONE, SMALL, BIG }

    /** Drobná změna = některá buňka o [SMALL_LEVEL]+ stupňů; velká = aspoň 8 % buněk o [BIG_LEVEL]+. */
    const val SMALL_LEVEL = 6
    const val BIG_LEVEL = 20

    fun diff(a: IntArray?, b: IntArray?): Change {
        if (a == null || b == null || a.size != b.size) return Change.BIG
        var any = false
        var big = 0
        for (i in a.indices) {
            val d = abs(a[i] - b[i])
            if (d >= SMALL_LEVEL) any = true
            if (d >= BIG_LEVEL) big++
        }
        return when { big * 100 >= a.size * 8 -> Change.BIG; any -> Change.SMALL; else -> Change.NONE }
    }
}

/**
 * Rozhoduje, kdy uložit snímek: jen když se obraz od posledního uloženého změnil – běžně nejvýš jednou
 * za [SnapBatches.INTERVAL_MS], velká změna po klidném obrazu hned (otevřené menu, nový titulek).
 * Na rychle se měnící scéně (jízda autem) tak vznikne nejvýš snímek za sekundu.
 */
class SnapPacer {
    private var prev: IntArray? = null
    private var saved: IntArray? = null
    private var lastSave = Long.MIN_VALUE / 4
    private var calmSince = Long.MIN_VALUE / 4

    /** Nový obraz (mřížka z [SnapBatches.thumb]) v čase [now]; vrací důvod uložení, nebo null = neukládat. */
    fun offer(thumb: IntArray, now: Long): SnapBatches.Change? {
        val vsPrev = SnapBatches.diff(prev, thumb)
        val calm = prev != null && now - calmSince >= SnapBatches.CALM_MS
        if (vsPrev != SnapBatches.Change.NONE) calmSince = now
        prev = thumb
        val vsSaved = SnapBatches.diff(saved, thumb)
        val since = now - lastSave
        val save = when (vsSaved) {
            SnapBatches.Change.NONE -> false
            SnapBatches.Change.SMALL -> since >= SnapBatches.INTERVAL_MS
            SnapBatches.Change.BIG -> since >= SnapBatches.INTERVAL_MS || (calm && since >= SnapBatches.FAST_MS)
        }
        if (!save) return null
        saved = thumb
        lastSave = now
        return vsSaved
    }
}
