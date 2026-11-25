package cz.recaption.core

/**
 * Titulky ke čtení v telefonu (od 1.0.5, přání majitele – podle GlossaSubu): přeložené repliky v pořadí,
 * jak se ukázaly v pruhu, s barvami mluvčích z pruhu. V paměti posledních [MAX]; od 1.0.5 i v souboru [file]
 * (nejvýš [FILE_MAX] replik, zápis na vlastním vlákně), aby šly číst i po zastavení překladu a po novém spuštění
 * aplikace, a starší se daly načíst tlačítkem „Starší titulky“ ([older]).
 * Plní ji služba při ukázání titulku (`TranslatorService.display`), čte `ReadFeedActivity` jen při změně [version].
 * Menu hry, úkol, telefon, tipy ani pokyny sem nejdou – jen repliky (pokyny pod titulkem se vyřadí).
 */
object ReadFeed {
    /** Soubor s historií (nastaví `App.onCreate`); null = jen paměť (testy, PC). */
    @Volatile var file: java.io.File? = null
    /** Kolik replik drží soubor (pak se zkrátí). */
    const val FILE_MAX = 3000
    const val FILE = "titulky-cteni.tsv"
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "tp-readfeed").apply { isDaemon = true } }
    /** Řádků v souboru od posledního zkrácení (odhad; zkracuje se při 1,5× [FILE_MAX]). */
    private var fileLines = 0

    /** Jedna replika: překlad po mluvčích, barvy jmen jako v pruhu, anglický originál. */
    class Entry(val id: Long, val time: Long, val segments: List<Segment>, val colors: Map<String, Int>, val original: String)

    const val MAX = 150
    /** Tentýž titulek do takové doby po předchozím = oprava (varianta čtení, DeepL), ne nová replika. */
    const val REPLACE_MS = 15_000L

    private val items = ArrayDeque<Entry>()
    /** Čísla replik podle času spuštění – po novém spuštění vždy vyšší než ta v souboru. */
    private var nextId = System.currentTimeMillis()

    /** Roste s každou změnou – stránka se překreslí jen, když se změní. */
    @Volatile var version = 0L
        private set

    @Synchronized
    fun add(original: String, segments: List<Segment>, colors: Map<String, Int>, time: Long = System.currentTimeMillis()) {
        val segs = segments.filter { !it.prompt && it.body.isNotBlank() }
        if (segs.isEmpty()) return
        val last = items.lastOrNull()
        val e: Entry
        if (last != null && time - last.time < REPLACE_MS && original.isNotEmpty() && TextLogic.similar(last.original, original)) {
            if (last.segments == segs) return
            items.removeLast()
            e = Entry(last.id, time, segs, colors, original)
            items.addLast(e)
        } else {
            e = Entry(nextId++, time, segs, colors, original)
            items.addLast(e)
            while (items.size > MAX) items.removeFirst()
        }
        version++
        save(e)
    }

    /** Připsat do souboru (oprava téže repliky = řádek se stejným id, při čtení vyhrává poslední). */
    private fun save(e: Entry) {
        val f = file ?: return
        val line = encode(e)
        io.execute {
            try {
                f.appendText(line + "\n")
                if (++fileLines > FILE_MAX * 3 / 2) {
                    val keep = readAll(f).takeLast(FILE_MAX)
                    val tmp = java.io.File(f.path + ".part")
                    tmp.writeText(keep.joinToString("") { encode(it) + "\n" })
                    if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
                    fileLines = keep.size
                }
            } catch (_: Exception) {}
        }
    }

    /** Po spuštění aplikace: posledních [MAX] replik ze souboru do paměti (na vlastním vlákně). */
    fun load() {
        val f = file ?: return
        io.execute {
            try {
                if (!f.exists()) return@execute
                val all = readAll(f)
                synchronized(this) {
                    fileLines = all.size
                    val have = items.mapTo(HashSet()) { it.id }
                    val old = all.filter { it.id !in have }.takeLast(MAX - items.size.coerceAtMost(MAX))
                    old.asReversed().forEach { items.addFirst(it) }
                    nextId = maxOf(nextId, (all.maxOfOrNull { it.id } ?: 0) + 1)
                    if (old.isNotEmpty()) version++
                }
            } catch (_: Exception) {}
        }
    }

    /** Starší repliky ze souboru před [beforeId] (nejvýš [count], nejstarší první). Volat mimo hlavní vlákno. */
    fun older(beforeId: Long, count: Int = 150): List<Entry> {
        val f = file ?: return emptyList()
        return try { readAll(f).filter { it.id < beforeId }.takeLast(count) } catch (_: Exception) { emptyList() }
    }

    private fun readAll(f: java.io.File): List<Entry> {
        val byId = LinkedHashMap<Long, Entry>()
        f.forEachLine { l -> decode(l)?.let { byId[it.id] = it } }
        return byId.values.toList()
    }

    // ---- řádek souboru: id, čas, originál, segmenty, barvy (oddělené tabulátorem; uvnitř \u001E / \u001F) ----
    private const val RS = '\u001E'
    private const val US = '\u001F'

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
        .replace(RS.toString(), "").replace(US.toString(), "")
    private fun unesc(s: String): String {
        val b = StringBuilder(); var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) { b.append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> s[i + 1] }); i += 2 }
            else { b.append(c); i++ }
        }
        return b.toString()
    }

    fun encode(e: Entry): String {
        val segs = e.segments.joinToString(RS.toString()) { s ->
            val flags = (if (s.choice) "c" else "") + (if (s.caption) "k" else "") + (if (s.prompt) "p" else "")
            flags + US + esc(s.speaker ?: "") + US + esc(s.body)
        }
        val cols = e.colors.entries.joinToString(RS.toString()) { esc(it.key) + US + it.value }
        return listOf(e.id.toString(), e.time.toString(), esc(e.original), segs, cols).joinToString("\t")
    }

    fun decode(line: String): Entry? = try {
        val p = line.split('\t')
        if (p.size < 5) null else {
            val segs = p[3].split(RS).mapNotNull { x ->
                val q = x.split(US); if (q.size != 3) null
                else Segment(unesc(q[1]).ifEmpty { null }, unesc(q[2]), choice = 'c' in q[0], prompt = 'p' in q[0], caption = 'k' in q[0])
            }
            val cols = if (p[4].isEmpty()) emptyMap() else p[4].split(RS).mapNotNull { x ->
                val q = x.split(US); if (q.size != 2) null else unesc(q[0]) to q[1].toInt()
            }.toMap()
            if (segs.isEmpty()) null else Entry(p[0].toLong(), p[1].toLong(), segs, cols, unesc(p[2]))
        }
    } catch (_: Exception) { null }

    /** Nejstarší první (nejnovější dole). */
    @Synchronized
    fun all(): List<Entry> = items.toList()

    @Synchronized
    fun clear() { items.clear(); version++ }
}
