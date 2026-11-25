package cz.recaption.core

import java.io.File

/**
 * Záznam událostí z aplikace v televizi (LG / Samsung). Televize si drží posledních pár set řádků
 * a po spojení je posílá sem zprávou `{"typ":"zaznam","radky":["12:00:01 start …", …]}`
 * (viz [TvSender]); tady se ukládají do souboru [FILE] s omezenou velikostí. Do hlášení o stavu
 * a do záznamu pro vývojáře jdou vždy přes [Redact].
 * Starší aplikace v televizi zprávu neposílá; starší ReCaption na Androidu ji jen přečte a zahodí.
 */
object TvLog {
    const val FILE = "tv-zaznam.txt"
    /** Kolik znaků souboru se drží (asi 600 řádků). */
    const val MAX_CHARS = 60_000
    /** Delší řádek se zkrátí. */
    const val MAX_LINE = 300
    /** Víc řádků v jedné zprávě se nebere (televize posílá po menších dávkách). */
    const val MAX_LINES_PER_MESSAGE = 100

    /** Soubor, kam se záznam ukládá (nastaví Android část); null = jen se zahodí. */
    @Volatile var file: File? = null

    /** Řádky ze zprávy od televize; null, když to není záznam. */
    fun parse(msg: String): List<String>? {
        val m = MiniJson.obj(msg) ?: return null
        if (m["typ"] != "zaznam") return null
        val rows = m["radky"] as? List<*> ?: return emptyList()
        return rows.take(MAX_LINES_PER_MESSAGE).mapNotNull { (it as? String)?.let(::cleanLine)?.takeIf { l -> l.isNotEmpty() } }
    }

    /** Řídicí znaky pryč (žádné nové řádky ani escape sekvence z televize), délka omezená. */
    fun cleanLine(s: String): String = s.replace(Regex("[\\p{C}]+"), " ").trim().take(MAX_LINE)

    /**
     * Přidá řádky k dosavadnímu obsahu a nechá jen konec do [max] znaků (ořízne se po celých
     * řádcích, nejstarší pryč).
     */
    fun merge(old: String?, lines: List<String>, max: Int = MAX_CHARS): String {
        val all = (old ?: "") + lines.joinToString("") { "$it\n" }
        if (all.length <= max) return all
        val cut = all.takeLast(max)
        val nl = cut.indexOf('\n')
        return if (nl in 0 until cut.length - 1) cut.substring(nl + 1) else cut
    }

    /** Posledních [n] řádků. */
    fun tail(all: String?, n: Int): String = (all ?: "").lines().filter { it.isNotBlank() }.takeLast(n).joinToString("\n")

    /** Uloží řádky z televize do souboru (když je nastavený). */
    @Synchronized fun add(lines: List<String>) {
        val f = file ?: return
        if (lines.isEmpty()) return
        try {
            val old = if (f.exists()) f.readText(Charsets.UTF_8) else ""
            f.writeText(merge(old, lines), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    /** Celý uložený záznam ("" když žádný není). */
    @Synchronized fun read(): String = try { file?.takeIf { it.exists() }?.readText(Charsets.UTF_8) ?: "" } catch (_: Exception) { "" }
}
