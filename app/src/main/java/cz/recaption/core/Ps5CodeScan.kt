package cz.recaption.core

/**
 * Osmimístné číslo pro spárování z obrazovky PlayStationu (Nastavení → Systém → Remote Play →
 * Propojit zařízení) přečtené kamerou telefonu (`LgScanActivity` v režimu PS5, bez Androidu, s testy).
 * Na obrazovce bývá rozdělené mezerou („1234 5678“) a čtečka ho občas rozdělí i na dva řádky.
 * Víc různých osmimístných čísel najednou = nejisté → nic.
 */
object Ps5CodeScan {
    private val EIGHT = Regex("(?<!\\d)\\d{8}(?!\\d)")
    private val FOUR = Regex("^\\d{4}$")

    fun parse(lines: List<String>): String? {
        val found = LinkedHashSet<String>()
        val clean = lines.map { it.trim() }
        for ((i, line) in clean.withIndex()) {
            // „1234 5678“, „1234-5678“ → jedno číslo
            val joined = line.replace(Regex("(?<=\\d)[ \\u00A0\\-](?=\\d)"), "")
            EIGHT.findAll(joined).forEach { found.add(it.value) }
            if (FOUR.matches(line) && i + 1 < clean.size && FOUR.matches(clean[i + 1])) found.add(line + clean[i + 1])
        }
        return found.singleOrNull()
    }

    private val IP = Regex("(?<![\\d.])(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?![\\d.])")
    private val IPV4_LABEL = Regex("(?i)ipv4|ip\\s*(address|adresa)|adresa\\s*ip")

    /**
     * Adresa PlayStationu z obrazovky Nastavení → Síť → Stav připojení. Na té obrazovce je víc adres
     * (brána, DNS, maska), proto: adresa u popisku „IPv4“ (na stejném nebo hned dalším řádku), jinak
     * jediná adresa domácí sítě, která nekončí .1 ani .254 (to bývá router). Nejisté = null.
     */
    fun ip(lines: List<String>): String? {
        val labeled = LinkedHashSet<String>()
        val all = LinkedHashSet<String>()
        val clean = lines.map { it.trim() }
        for ((i, line) in clean.withIndex()) {
            for (m in IP.findAll(line)) {
                val parts = m.groupValues.drop(1).map { it.toInt() }
                if (parts.any { it > 255 } || m.groupValues.drop(1).any { it.length > 1 && it.startsWith("0") }) continue
                val ip = parts.joinToString(".")
                if (!home(parts)) continue
                all.add(ip)
                if (IPV4_LABEL.containsMatchIn(line.substring(0, m.range.first)) || (i > 0 && IPV4_LABEL.containsMatchIn(clean[i - 1]) && IP.find(clean[i - 1]) == null)) labeled.add(ip)
            }
        }
        labeled.singleOrNull()?.let { return it }
        if (labeled.size > 1) return null
        return all.filter { !it.endsWith(".1") && !it.endsWith(".254") }.singleOrNull()
    }

    /** Adresa domácí sítě (10.x, 172.16–31.x, 192.168.x), ne adresa sítě ani vysílání. */
    private fun home(p: List<Int>): Boolean {
        val private = p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168)
        return private && p[3] in 1..254
    }
}

/** Číslo platí, až když ho čtečka přečte [need]× stejně (jako [LgScanVote]). */
class Ps5CodeVote(private val need: Int = 2) {
    private val counts = HashMap<String, Int>()

    fun add(code: String?): String? {
        if (code != null) counts[code] = (counts[code] ?: 0) + 1
        return current()
    }

    fun current(): String? = counts.filter { it.value >= need }.maxByOrNull { it.value }?.key

    fun clear() = counts.clear()
}
