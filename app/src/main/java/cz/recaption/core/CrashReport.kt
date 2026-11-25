package cz.recaption.core

/**
 * Záznam o pádu aplikace: kdy, ve které verzi, proč a kde. Ukládá se do souboru (dělá to Android
 * část, `CrashLog`) a drží se jen posledních pár pádů, aby soubor nerostl.
 */
object CrashReport {
    /** Kolik posledních pádů se drží. */
    const val KEEP = 3
    /** Oddělovač záznamů v souboru. */
    const val SEP = "\n=== pád ===\n"
    /** Kolik řádků stopy se uloží (hlavní příčina bývá nahoře, „Caused by“ se bere navíc). */
    private const val MAX_LINES = 30

    /** Text jednoho záznamu. Čas a verzi dodává volající (v testech pevné). */
    fun format(time: String, version: String, thread: String, e: Throwable): String {
        val lines = e.stackTraceToString().lines().filter { it.isNotBlank() }
        val head = lines.take(MAX_LINES)
        // nejhlubší příčina (Caused by) bývá to nejdůležitější – když se do limitu nevešla, přidat ji
        val cause = lines.drop(MAX_LINES).lastOrNull { it.startsWith("Caused by") }
        val body = (head + listOfNotNull(cause?.let { "… $it" })).joinToString("\n")
        return Redact.clean("$time · Titulky PS5 $version · vlákno ${thread.take(40)}\n$body")
    }

    /** Přidá záznam k dosavadnímu obsahu souboru a nechá jen posledních [KEEP]. */
    fun append(old: String?, entry: String): String =
        (entries(old) + entry.trim()).takeLast(KEEP).joinToString(SEP, prefix = SEP)

    /** Jednotlivé záznamy ze souboru, od nejstaršího. */
    fun entries(all: String?): List<String> =
        (all ?: "").split(SEP).map { it.trim() }.filter { it.isNotEmpty() }

    /** Poslední pád, nebo null. */
    fun last(all: String?): String? = entries(all).lastOrNull()

    /** Krátké shrnutí záznamu: řádek s časem a verzí a řádek s důvodem. */
    fun summary(entry: String): String = entry.lines().take(2).joinToString("\n")
}

/**
 * Odstraní z textu pro hlášení údaje, které do něj nepatří: klíče a čísla účtu PlayStation,
 * přístupové kódy z přihlášení, PIN. Hlášení si lidé fotí a posílají, takže se filtruje vždy.
 */
object Redact {
    const val MASK = "•••"

    /** klíč=hodnota / klíč: hodnota u citlivých názvů → hodnota se skryje */
    private val NAMED = Regex(
        "(?i)\\b(code|token|access_token|refresh_token|npsso|account_?id|accountid|psn_?id|pin|passphrase|password|passwd|heslo|secret|regist_?key|rp_?key|morning|authorization|bearer|credential|auth_?key|deepl-auth-key|gs_?token|user_?token|device_?code|user_?code|rps_?ticket)(\"?\\s*[=:]\\s*\"?|\\s+)([^\\s,;&|)\"]+)"
    )
    /**
     * Tokeny Microsoft / Xbox Live i bez názvu: JWT (eyJ…), přístupové tokeny Microsoft (EwA…, EwB…)
     * a obnovovací tokeny (M.C…), které obsahují i tečky, vykřičníky a hvězdičky.
     */
    private val MS_TOKEN = Regex("(?<![A-Za-z0-9])(eyJ[A-Za-z0-9_\\-]{10,}(\\.[A-Za-z0-9_\\-]+)*|Ew[A-Z][A-Za-z0-9+/=_\\-]{20,}|M\\.[A-Za-z0-9_.!*$\\-]{20,})")
    /** klíč DeepL (UUID, u verze Free s „:fx“) a cokoli dlouhého s koncovkou „:fx“ */
    private val DEEPL = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(:fx)?\\b|[A-Za-z0-9-]{16,}:fx\\b")
    /** dlouhé šestnáctkové řetězce (klíče, otisky) */
    private val HEX = Regex("\\b[0-9a-fA-F]{16,}\\b")
    /** dlouhá čísla (číslo účtu PlayStation má kolem 19 číslic) */
    private val LONG_NUM = Regex("\\b\\d{15,}\\b")
    /** base64 s doplněním (číslo účtu PlayStation v base64) */
    private val B64 = Regex("(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{8,}={1,2}")
    /** dlouhé tokeny ze směsi písmen a číslic (kódy z přihlášení) */
    private val TOKEN = Regex("(?<![A-Za-z0-9_])(?=[A-Za-z0-9_]*\\d)(?=[A-Za-z0-9_]*[A-Za-z])[A-Za-z0-9_]{24,}(?![A-Za-z0-9_])")

    /**
     * @param secrets konkrétní hodnoty, které se v textu nesmí objevit (uložené číslo účtu, klíče…);
     *   příliš krátké (pod 4 znaky) se ignorují, aby se neskrývala běžná čísla
     */
    fun clean(text: String, secrets: Collection<String> = emptyList()): String {
        var s = text
        for (v in secrets.map { it.trim() }.filter { it.length >= 4 }.sortedByDescending { it.length }) {
            s = s.replace(v, MASK, ignoreCase = true)
        }
        s = DEEPL.replace(s, MASK)
        s = NAMED.replace(s) { m ->
            val value = m.groupValues[3]
            // „pin requested“, „code=true“ – slova a ano/ne nejsou tajná
            if (value.all { it.isLetter() } && value.length < 12 && m.groupValues[2].isBlank()) m.value
            else m.groupValues[1] + m.groupValues[2] + MASK
        }
        s = MS_TOKEN.replace(s, MASK)
        s = HEX.replace(s, MASK)
        s = LONG_NUM.replace(s, MASK)
        s = B64.replace(s, MASK)
        s = TOKEN.replace(s, MASK)
        return s
    }
}
