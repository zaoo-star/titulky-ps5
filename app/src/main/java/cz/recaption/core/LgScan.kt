package cz.recaption.core

/*
 * Načtení adresy televize a Passphrase z obrazovky aplikace LG Developer Mode kamerou telefonu –
 * část bez Androidu: z řádků přečtených ML Kitem vybere adresu (IPv4 v domácí síti) a Passphrase
 * (6 znaků, velká písmena a číslice) a rozhodne, kdy je výsledek jistý. Kamera a ML Kit jsou
 * v cz.recaption.LgScanActivity. Nic se neukládá ani nevypisuje do logu.
 *
 * Obrazovka Developer Mode (webOS 3–24) vypadá zhruba takto (pořadí řádků se podle verze liší):
 *   Dev Mode Status   ON
 *   Remaining Duration   999:58
 *   Key Server   ON
 *   IP : 192.168.1.20        (u některých verzí jen „192.168.1.20“ bez popisku)
 *   Passphrase : 4A2C9E
 */

/**
 * Co se z jednoho snímku (nebo z více snímků dohromady) podařilo přečíst.
 * @param passFixed Passphrase obsahovala O nebo I a byly jednoznačně opraveny na 0 a 1
 * @param passUnsure Passphrase obsahuje znaky, které se snadno spletou (0/O, 1/I) a nedají se
 *   jednoznačně rozhodnout – uživatel je má porovnat s televizí
 */
data class LgScanResult(
    val ip: String? = null,
    val pass: String? = null,
    val passFixed: Boolean = false,
    val passUnsure: Boolean = false,
) {
    val complete: Boolean get() = ip != null && pass != null
    val empty: Boolean get() = ip == null && pass == null
}

object LgScan {
    private const val PASS_LEN = 6
    private val IP_CANDIDATE = Regex("(?<![0-9.])([0-9OoIl|]{1,3})\\s*[.,]\\s*([0-9OoIl|]{1,3})\\s*[.,]\\s*([0-9OoIl|]{1,3})\\s*[.,]\\s*([0-9OoIl|]{1,3})(?![0-9.])")
    private val HEX_LETTERS = "ABCDEF"
    /** Slova z obrazovky Developer Mode, která mají 6 znaků a nesmí se splést s Passphrase. */
    private val WORDS = setOf("SERVER", "STATUS", "REMAIN", "DEVICE", "LOGOUT", "SIGNIN", "EXTEND", "ENABLE")

    /**
     * Přečte adresu a Passphrase z řádků jednoho snímku (pořadí čtení shora dolů, jak je vrací ML Kit).
     * Nejisté nebo nejednoznačné údaje vrací jako null – raději nic než špatně.
     */
    fun parse(lines: List<String>): LgScanResult {
        val clean = lines.map { it.replace(' ', ' ').trim() }.filter { it.isNotEmpty() }
        val ip = findIp(clean)
        val p = findPass(clean)
        return LgScanResult(ip, p?.first, p?.second ?: false, p?.third ?: false)
    }

    /** Text jednoho snímku najednou (bloky oddělené novými řádky). */
    fun parse(text: String): LgScanResult = parse(text.lines())

    // ---- adresa ----

    private fun findIp(lines: List<String>): String? {
        val found = LinkedHashMap<String, Boolean>() // adresa → stála u popisku „IP“
        for ((i, line) in lines.withIndex()) {
            for (m in IP_CANDIDATE.findAll(line)) {
                val parts = m.groupValues.drop(1).map { digits(it) }
                if (parts.any { it == null }) continue
                val ip = parts.joinToString(".") { it!!.toInt().toString() }
                if (parts.any { it!!.length > 1 && it.startsWith("0") }) continue
                val ok = runCatching { LgInstall.address(ip) }.isSuccess
                if (!ok) continue
                val labeled = hasIpLabel(line.substring(0, m.range.first)) || (i > 0 && hasIpLabel(lines[i - 1]) && lines[i - 1].length <= 12)
                found[ip] = (found[ip] ?: false) || labeled
            }
        }
        if (found.size == 1) return found.keys.first()
        val labeled = found.filterValues { it }.keys
        return if (labeled.size == 1) labeled.first() else null
    }

    private fun hasIpLabel(s: String) = Regex("(?i)(^|[^a-z])ip([^a-z]|$)").containsMatchIn(s)

    /** Uvnitř adresy jsou jen číslice, takže O→0 a I/l/|→1 je jednoznačné. */
    private fun digits(s: String): String? {
        val d = s.map { when (it) { 'O', 'o' -> '0'; 'I', 'l', '|' -> '1'; else -> it } }.joinToString("")
        return if (d.all { it.isDigit() }) d else null
    }

    // ---- Passphrase ----

    private fun isPassLabel(line: String): Boolean {
        val l = line.lowercase().filter { it.isLetter() }
        return l.contains("passphr") || l.contains("phrase") || l.contains("passph") || l.contains("pasphrase")
    }

    private fun isKeyServer(line: String): Boolean {
        val l = line.lowercase().filter { it.isLetter() }
        return l.contains("keyserver") || l.contains("keyserv")
    }

    /**
     * Hledá jen u popisku: za „Passphrase“ na stejném řádku nebo na dvou řádcích pod ním (ML Kit
     * občas dá hodnotu do vlastního bloku). Bez „Passphrase“ zkusí nejvýš čtyři řádky pod „Key Server“.
     * Víc různých kandidátů = nejednoznačné = null.
     */
    private fun findPass(lines: List<String>): Triple<String, Boolean, Boolean>? {
        val labels = lines.indices.filter { isPassLabel(lines[it]) }
        val candidates = LinkedHashSet<String>()
        if (labels.isNotEmpty()) {
            for (i in labels) {
                candidates += tokens(afterLabel(lines[i]))
                if (candidates.isEmpty()) for (j in i + 1..minOf(i + 2, lines.lastIndex)) {
                    if (isPassLabel(lines[j]) || isKeyServer(lines[j])) break
                    candidates += tokens(lines[j])
                    if (candidates.isNotEmpty()) break
                }
            }
            // Dvousloupcová obrazovka: ML Kit přečte nejdřív všechny popisky a pak všechny hodnoty,
            // takže hodnota není u popisku. Na obrazovce s nápisem Passphrase pak stačí jediný
            // útvar se směsí písmen a číslic kdekoli (víc takových = nic).
            if (candidates.isEmpty()) {
                val all = lines.flatMap { tokens(it) }.filter { t -> t.any { it.isDigit() } && t.any { it.isLetter() } }
                candidates += all
            }
        } else {
            val k = lines.indexOfFirst { isKeyServer(it) }
            if (k < 0) return null
            for (j in k + 1..minOf(k + 4, lines.lastIndex)) candidates += tokens(lines[j])
        }
        val resolved = candidates.mapNotNull { resolve(it) }.distinctBy { it.first }
        return resolved.singleOrNull()
    }

    private fun afterLabel(line: String): String {
        val m = Regex("(?i)[a-z]*ph\\s*r?\\s*a\\s*s\\s*e|passph[a-z]*").find(line) ?: return line
        return line.substring(m.range.last + 1)
    }

    /** Šestiznakové útvary z písmen a číslic; mezi slovy může být dvojtečka, mezera, závorka. */
    private fun tokens(s: String): List<String> =
        s.split(Regex("[^A-Za-z0-9|]+")).filter { it.length == PASS_LEN }.filter { t ->
            // televize ukazuje Passphrase velkými písmeny: malé písmeno (kromě l a o, které ML Kit plete s 1 a 0) = obyčejné slovo
            t.count { it.isLowerCase() && it != 'l' && it != 'o' } == 0 && t.uppercase() !in WORDS
        }

    /**
     * Sjednotí podobné znaky. Malé l a | jsou vždy I nebo 1, malé o je O nebo 0.
     * Rozhodnutí 0/O a 1/I: Passphrase z televizí LG bývá v šestnáctkovém tvaru (číslice a A–F),
     * takže když ostatní písmena jsou jen A–F, O a I v ní být nemůže → jednoznačně 0 a 1
     * (passFixed). Když v ní jsou i jiná písmena, nejde to rozhodnout: nechá se, jak to přečetl
     * ML Kit, a uživatel dostane upozornění (passUnsure). Špatná Passphrase nic nepokazí –
     * televize ji jen odmítne a průvodce řekne, ať ji opíše znovu.
     */
    private fun resolve(token: String): Triple<String, Boolean, Boolean>? {
        val t = token.map { when (it) { 'l', '|' -> 'I'; 'o' -> 'O'; else -> it.uppercaseChar() } }.joinToString("")
        if (!t.all { it in 'A'..'Z' || it in '0'..'9' }) return null
        // bez číslic a bez písmen A–F je to nejspíš slovo z obrazovky, ne Passphrase
        if (t.none { it.isDigit() } && t.none { it in HEX_LETTERS } ) return null
        val confusable = t.any { it == 'O' || it == 'I' || it == '0' || it == '1' }
        if (!confusable) return Triple(t, false, false)
        val others = t.filter { it.isLetter() && it != 'O' && it != 'I' }
        val hexLike = others.all { it in HEX_LETTERS }
        return if (hexLike) {
            val fixed = t.replace('O', '0').replace('I', '1')
            Triple(fixed, fixed != t, false)
        } else Triple(t, false, true)
    }
}

/**
 * Výsledek z více snímků za sebou: údaj platí, až když ho ML Kit přečetl stejně aspoň [need]krát.
 * Jiné čtení téhož údaje počítání nezahodí – vyhraje to, co se přečetlo vícekrát.
 */
class LgScanVote(private val need: Int = 2) {
    private val ips = HashMap<String, Int>()
    private val passes = HashMap<String, Int>()
    private val passInfo = HashMap<String, Pair<Boolean, Boolean>>()

    fun add(r: LgScanResult) {
        r.ip?.let { ips[it] = (ips[it] ?: 0) + 1 }
        r.pass?.let { passes[it] = (passes[it] ?: 0) + 1; passInfo[it] = r.passFixed to r.passUnsure }
    }

    private fun best(m: Map<String, Int>): String? {
        val top = m.entries.maxByOrNull { it.value } ?: return null
        if (top.value < need) return null
        // dvě různá čtení se stejným počtem = zatím nerozhodnuto
        return if (m.values.count { it == top.value } > 1) null else top.key
    }

    /** Co je zatím jisté (i jen část). */
    fun current(): LgScanResult {
        val p = best(passes)
        val info = p?.let { passInfo[it] }
        return LgScanResult(best(ips), p, info?.first ?: false, info?.second ?: false)
    }

    fun clear() { ips.clear(); passes.clear(); passInfo.clear() }
}
