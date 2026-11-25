package cz.recaption.core

/**
 * Slovník pro překlad: jména, která se nepřekládají, výrazy s pevným překladem
 * a celé věty s ručně zadaným překladem.
 *
 * Jména a výrazy se před překladem schovají za značky [1], [2]… a po překladu vrátí
 * (jméno v originále, výraz ve správném překladu). Když překladač značky poškodí,
 * přeloží se text bez nich – nikdy se nic neztratí.
 */
class Glossary(
    names: Collection<String> = emptyList(),
    /** anglický výraz → překlad (cílový jazyk) */
    terms: Map<String, String> = emptyMap(),
    /** celá anglická věta → překlad */
    sentences: Map<String, String> = emptyMap(),
    /** postavy hry s rodem – po překladu podle nich [CzechGender] / [SlovakGender] opraví tvary */
    val cast: Cast = Cast.EMPTY,
    /** Výrazy z vlastního slovníku hráče – mají přednost i před jménem z profilu hry (od 0.97.54). */
    userTerms: Collection<String> = emptyList(),
) {
    private val userKeys = userTerms.map { it.trim().lowercase() }.toHashSet()
    private val nameList = names.map { it.trim() }.filter { it.length >= 2 }.distinct()
    private val termMap = terms.entries
        .map { it.key.trim().lowercase() to it.value.trim() }
        .filter { it.first.length >= 2 && it.second.isNotEmpty() }
        .toMap()
    private val sentenceMap = sentences.entries
        .map { TextLogic.simplify(it.key) to it.value.trim() }
        .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
        .toMap()

    private val rx: Regex? = run {
        // escape po znacích, ne Regex.escape (\Q…\E): Kotlin pro iPhone nepřečte (?i:\Q…\E)
        val alts = (nameList.map { literal(it) } + termMap.keys.map { "(?i:" + literal(it) + ")" })
            .sortedByDescending { it.length } + STREET
        // znak před názvem (ne písmeno) je součást nálezu (skupina 1) a vrací se zpět – Kotlin pro iPhone
        // s hledáním dozadu (?<!…) před touto skupinou hledá špatně
        Regex("(^|[^A-Za-z'\\-])(" + alts.joinToString("|") + ")(?![A-Za-z\\-])")
    }

    private fun literal(s: String): String = s.replace(REGEX_SPECIAL) { "\\" + it.value }

    val size: Int get() = nameList.size + termMap.size + sentenceMap.size

    /**
     * Doladění hotového překladu jedné části titulku podle postav (rod mluvčího, osloveného
     * a postavy v textu). [source] je anglický originál části, [speaker] jméno mluvčího z titulku.
     */
    fun genders(lang: Lang, source: String, translated: String, speaker: String?, english: Boolean = true): String =
        // originál v jiném jazyce než angličtině se nečte (oslovení, she/her) – zůstane mluvčí a jméno v překladu
        when (lang) {
            Lang.CS -> CzechGender.fix(cast, if (english) source else "", translated, speaker)
            Lang.SK -> SlovakGender.fix(cast, if (english) source else "", translated, speaker)
            else -> translated
        }

    /** Ručně zadaný překlad celé věty, pokud existuje. */
    fun sentence(text: String): String? = sentenceMap[TextLogic.simplify(text)]

    fun prepare(text: String, english: Boolean = true): OfflinePrep.Prepared {
        val plain = if (english) OfflinePrep.standardize(text) else text
        val r = rx ?: return OfflinePrep.Prepared(plain, plain, emptyList())
        val replacements = ArrayList<String>()
        val protectedText = r.replace(plain) { m ->
            val before = m.groupValues[1]
            val found = m.groupValues[2]
            // jméno zůstává, výraz se nahradí překladem
            // vlastní slovník hráče přebije i jméno z profilu hry (chtěl ho přeložit jinak)
            val out = termMap[found.lowercase()]?.takeIf { found !in nameList || found.lowercase() in userKeys } ?: found
            var idx = replacements.indexOf(out)
            if (idx < 0) { replacements.add(out); idx = replacements.size - 1 }
            before + "[${idx + 1}]"
        }
        return OfflinePrep.Prepared(plain, protectedText, replacements)
    }

    /** Celý postup s libovolným překladačem. */
    inline fun translate(text: String, translator: (String) -> String?): String? {
        sentence(text)?.let { return it }
        val p = prepare(text)
        if (p.names.isNotEmpty()) {
            val withTokens = translator(p.protectedText)
            if (withTokens != null) OfflinePrep.finish(p, withTokens)?.let { return it }
        }
        return translator(p.plain)
    }

    companion object {
        private val REGEX_SPECIAL = Regex("[\\\\^$.|?*+()\\[\\]{}]")
        /**
         * Názvy ulic ve všech hrách zůstávají v originále (přání majitele: v telefonu a na mapě hry jsou
         * anglicky, přeložené by mátly): 1–3 slova s velkým písmenem + Street/Avenue/Drive… („Shore Drive“,
         * „Huevo St“, „Ocean Avenue“). Slova jako „The“, „Take“ na začátku se do názvu nepočítají.
         */
        private const val STREET =
            "(?:(?!(?:The|A|An|Take|Go|On|At|To|In|Down|Up|Our|My|Your|His|Her|Their|This|That|No|Hit|Get|Head|Meet|Off|Cross|Across|Turn|Back|Into|Onto|Near|Past|From|Via)\\b)" +
                "(?:[A-Z][A-Za-z'\\-]+|\\d+(?:st|nd|rd|th)?) ){1,3}" +
                "(?:Street|St|Avenue|Ave|Boulevard|Blvd|Drive|Road|Rd|Lane|Highway|Hwy|Parkway|Causeway|Expressway|Freeway|Court)"

        /** Řádky "anglicky = překlad" → mapa. Poškozené řádky se přeskočí. */
        fun parseUser(data: String?): LinkedHashMap<String, String> {
            val out = LinkedHashMap<String, String>()
            for (line in (data ?: "").split('\n')) {
                val i = line.indexOf('\t')
                if (i <= 0) continue
                val k = line.substring(0, i).trim()
                val v = line.substring(i + 1).trim()
                if (k.isNotEmpty() && v.isNotEmpty()) out[k] = v
            }
            return out
        }

        fun serializeUser(m: Map<String, String>): String =
            m.entries.joinToString("\n") { it.key.replace('\t', ' ').replace('\n', ' ') + "\t" + it.value.replace('\t', ' ').replace('\n', ' ') }

        /** Uživatelský záznam je "celá věta", když končí interpunkcí nebo má víc než 3 slova. */
        fun isSentence(english: String): Boolean {
            val t = english.trim()
            return t.isNotEmpty() && (t.last() in ".!?…" || t.split(Regex("\\s+")).size > 3)
        }
    }
}
