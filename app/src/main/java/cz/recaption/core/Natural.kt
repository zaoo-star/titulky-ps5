package cz.recaption.core

/**
 * Přirozenější čeština a slovenština po strojovém překladu: sjednotí oslovení na tykání.
 *
 * Offline překladač střídá tykání a vykání větu od věty („Co tady děláš?“ a hned „Jste si jistý?“),
 * což v herních dialozích zní strojově. Postavy si ve hrách většinou tykají, proto se vykání
 * jedné osobě převede na tykání. Věty, které mluví k více lidem, zůstávají („Vy dva, pojďte“):
 * pozná se to z anglického originálu.
 *
 * Převádí se jen tvary, u kterých je to jisté (seznam běžných sloves a spolehlivé koncovky).
 * Neznámé slovo zůstane, jak ho překladač napsal – raději nic než špatný tvar.
 */
object Natural {
    /** Anglická věta mluví k více lidem → oslovení se nemění. */
    private val PLURAL_EN = Regex(
        "\\b(you (all|two|three|four|both|guys|lot|boys|men|people|folks|fools|idiots)|(all|both|two|three|some|many|any|none|each|one|rest) of you" +
            "|y'all|ya'll|everyone|everybody|boys|guys|gentlemen|ladies|folks|men|lads|fellas|people|friends|brothers|soldiers|you and (your|the|my|his|her|that|those) \\w+)\\b",
        RegexOption.IGNORE_CASE)

    private val WORDS: Map<String, String> = mapOf(
        // být a podmiňovací způsob
        "jste" to "jsi", "nejste" to "nejsi", "byste" to "bys", "abyste" to "abys", "kdybyste" to "kdybys",
        // zájmena
        "vy" to "ty", "vás" to "tě", "vám" to "ti", "vámi" to "tebou",
        "váš" to "tvůj", "vaše" to "tvoje", "vašeho" to "tvého", "vašemu" to "tvému", "vaší" to "tvojí", "vaši" to "tvoji",
        "vašich" to "tvých", "vašim" to "tvým", "vašimi" to "tvými", "vaším" to "tvým", "vašem" to "tvém",
        // krátká slovesa (na obecné koncovky jsou moc krátká)
        "víte" to "víš", "nevíte" to "nevíš", "máte" to "máš", "nemáte" to "nemáš", "znáte" to "znáš", "dáte" to "dáš",
        // slovesa s koncovkou -ete (sama koncovka je dvojznačná, proto jen známá)
        "chcete" to "chceš", "nechcete" to "nechceš", "můžete" to "můžeš", "nemůžete" to "nemůžeš", "budete" to "budeš",
        "nebudete" to "nebudeš", "jdete" to "jdeš", "nejdete" to "nejdeš", "jedete" to "jedeš", "přijdete" to "přijdeš",
        "půjdete" to "půjdeš", "nepůjdete" to "nepůjdeš", "dostanete" to "dostaneš", "najdete" to "najdeš", "řeknete" to "řekneš",
        "vezmete" to "vezmeš", "zemřete" to "zemřeš", "pomůžete" to "pomůžeš", "smíte" to "smíš", "nesmíte" to "nesmíš",
        "zůstanete" to "zůstaneš", "přestanete" to "přestaneš", "stanete" to "staneš", "necháte" to "necháš",
        // rozkazy, které nejdou poznat podle koncovky
        "pojďte" to "pojď", "nechte" to "nech", "držte" to "drž", "běžte" to "běž", "buďte" to "buď", "nebuďte" to "nebuď",
        "vraťte" to "vrať", "pusťte" to "pusť", "zůstaňte" to "zůstaň", "přestaňte" to "přestaň", "pomozte" to "pomoz",
        "mlčte" to "mlč", "věřte" to "věř", "ukažte" to "ukaž", "zrušte" to "zruš", "zahoďte" to "zahoď", "pospěšte" to "pospěš",
        "dostaňte" to "dostaň", "postavte" to "postav", "vstaňte" to "vstaň", "zastavte" to "zastav", "odpusťte" to "odpusť",
        "promiňte" to "promiň", "zapomeňte" to "zapomeň", "poslyšte" to "poslyš", "mluvte" to "mluv", "hoďte" to "hoď",
        "přineste" to "přines", "pojeďte" to "pojeď", "otevřete" to "otevři", "zavřete" to "zavři", "položte" to "polož",
        "odložte" to "odlož", "střelte" to "střel", "zabte" to "zab", "berte" to "ber", "neste" to "nes", "kupte" to "kup",
        "jděte" to "jdi", "nejděte" to "nejdi", "odejděte" to "odejdi", "přijďte" to "přijď", "vypadněte" to "vypadni",
        // přídavná jména a příčestí po „jsi“ / „buď“ (množné číslo → jednotné)
        "opatrní" to "opatrný", "jistí" to "jistý", "připraveni" to "připraven", "připravení" to "připravený", "hotovi" to "hotov",
        "hotoví" to "hotový", "klidní" to "klidný", "šílení" to "šílený", "zticha" to "zticha",
    )

    /** Příčestí minulá, která se z množného čísla nepřevedou jen odtržením „i“. */
    private val PARTICIPLES: Map<String, String> = mapOf(
        "šli" to "šel", "přišli" to "přišel", "odešli" to "odešel", "našli" to "našel", "vešli" to "vešel", "nešli" to "nešel",
        "mohli" to "mohl", "nemohli" to "nemohl", "řekli" to "řekl", "neřekli" to "neřekl",
    )
    private val NOT_PARTICIPLE = setOf("jestli", "zdali", "chvíli", "neděli", "kvůli", "postupně", "zatímco", "pokud")
    private val AUX = setOf("jste", "nejste", "byste", "abyste", "kdybyste")
    /** Krátká slova, která mohou stát mezi pomocným slovesem a příčestím („kdybyste mi poslouchali“). */
    private val FILLERS = setOf("mi", "se", "si", "to", "mu", "ho", "jí", "je", "nám", "tam", "tu", "sem", "už", "ještě", "mě", "ti", "tě")
    /** Oslovení končící stejně jako sloveso („Piráte!“) – nepřevádět. */
    private val NOT_VERB = setOf("piráte", "kamaráte", "magnáte", "renegáte", "kandidáte", "advokáte", "demokrate")

    private val TOKEN = Regex("[\\p{L}]+|[^\\p{L}]+")

    /** Sjednotí oslovení v českém překladu na tykání; [source] je anglický originál. */
    fun czech(source: String, translated: String): String {
        if (PLURAL_EN.containsMatchIn(source)) return translated
        val tokens = TOKEN.findAll(translated).map { it.value }.toMutableList()
        val words = tokens.indices.filter { tokens[it].first().isLetter() }
        fun low(i: Int) = tokens[i].lowercase()
        // příčestí v množném čísle u pomocného slovesa („neměli byste“, „kdybyste mi poslouchali“) → jednotné
        for ((n, i) in words.withIndex()) {
            if (low(i) !in AUX) continue
            for (step in listOf(-1, 1)) {
                var k = n + step
                var skipped = 0
                while (k in words.indices && low(words[k]) in FILLERS && skipped < 2) { k += step; skipped++ }
                if (k !in words.indices) continue
                val w = low(words[k])
                if (w !in PARTICIPLES && (w.length < 4 || !w.endsWith("li") || w in NOT_PARTICIPLE)) continue
                tokens[words[k]] = keepCase(tokens[words[k]], PARTICIPLES[w] ?: w.dropLast(1))
            }
        }
        for (i in words) {
            val w = low(i)
            val to = WORDS[w] ?: bySuffix(w) ?: continue
            tokens[i] = keepCase(tokens[i], to)
        }
        return tokens.joinToString("")
    }

    /** Spolehlivé koncovky: oznamovací způsob -áte/-íte/-ujete a rozkazy na -jte/-něte/-měte. */
    private fun bySuffix(w: String): String? {
        if (w.length < 5 || w in NOT_VERB) return null
        return when {
            w.endsWith("ujete") -> w.dropLast(5) + "uješ"
            w.endsWith("jte") -> w.dropLast(2)                 // počkejte → počkej, stůjte → stůj
            w.endsWith("něte") -> w.dropLast(4) + "ni"         // řekněte → řekni, sedněte → sedni
            w.endsWith("měte") -> w.dropLast(4) + "mi"         // vezměte → vezmi
            w.endsWith("áte") -> w.dropLast(3) + "áš"          // máte → máš, děláte → děláš
            w.endsWith("íte") -> w.dropLast(3) + "íš"          // víte → víš, musíte → musíš
            else -> null
        }
    }

    /** Tykání podle jazyka překladu (čeština, slovenština); ostatní jazyky beze změny. [source] je anglický originál. */
    fun fix(lang: Lang, source: String, translated: String): String = when (lang) {
        Lang.CS -> czech(source, translated)
        Lang.SK -> slovak(source, translated)
        else -> translated
    }

    // ---------- slovenština ----------

    private val SK_WORDS: Map<String, String> = mapOf(
        // byť (by ste, aby ste, keby ste, nie ste → by si…)
        "ste" to "si",
        // zájmena („vás“ a „vám“ podle předložky zvlášť)
        "vy" to "ty", "vami" to "tebou",
        "váš" to "tvoj", "vaša" to "tvoja", "vaše" to "tvoje", "vašu" to "tvoju", "vašej" to "tvojej", "vašom" to "tvojom",
        "vášho" to "tvojho", "vášmu" to "tvojmu", "vaši" to "tvoji", "vašich" to "tvojich", "vašim" to "tvojim", "vašimi" to "tvojimi",
        // přítomný čas – jen známá slovesa (koncovky -te jsou dvojznačné)
        "viete" to "vieš", "neviete" to "nevieš", "máte" to "máš", "nemáte" to "nemáš", "chcete" to "chceš", "nechcete" to "nechceš",
        "môžete" to "môžeš", "nemôžete" to "nemôžeš", "musíte" to "musíš", "nemusíte" to "nemusíš", "idete" to "ideš",
        "nejdete" to "nejdeš", "pôjdete" to "pôjdeš", "nepôjdete" to "nepôjdeš", "robíte" to "robíš", "nerobíte" to "nerobíš",
        "budete" to "budeš", "nebudete" to "nebudeš", "poznáte" to "poznáš", "dáte" to "dáš", "vidíte" to "vidíš",
        "nevidíte" to "nevidíš", "počujete" to "počuješ", "rozumiete" to "rozumieš", "nerozumiete" to "nerozumieš",
        "hovoríte" to "hovoríš", "myslíte" to "myslíš", "chápete" to "chápeš", "nechápete" to "nechápeš",
        "dostanete" to "dostaneš", "nájdete" to "nájdeš", "poviete" to "povieš", "vezmete" to "vezmeš", "zomriete" to "zomrieš",
        "pomôžete" to "pomôžeš", "smiete" to "smieš", "nesmiete" to "nesmieš", "zostanete" to "zostaneš", "ostanete" to "ostaneš",
        "prestanete" to "prestaneš", "necháte" to "necháš", "prídete" to "prídeš", "neprídete" to "neprídeš", "odídete" to "odídeš",
        "chodíte" to "chodíš", "hľadáte" to "hľadáš", "čakáte" to "čakáš", "veríte" to "veríš", "zaplatíte" to "zaplatíš",
        "bojíte" to "bojíš", "pamätáte" to "pamätáš", "mýlite" to "mýliš",
        // rozkazy, které nejdou poznat podle koncovky -jte
        "poďte" to "poď", "pozrite" to "pozri", "choďte" to "choď", "nechoďte" to "nechoď", "hýbte" to "hýb", "nehýbte" to "nehýb",
        "buďte" to "buď", "nebuďte" to "nebuď", "vráťte" to "vráť", "zostaňte" to "zostaň", "ostaňte" to "ostaň",
        "prestaňte" to "prestaň", "pomôžte" to "pomôž", "mlčte" to "mlč", "verte" to "ver", "ukážte" to "ukáž",
        "odhoďte" to "odhoď", "postavte" to "postav", "vstaňte" to "vstaň", "zastavte" to "zastav", "odpusťte" to "odpusť",
        "prepáčte" to "prepáč", "zabudnite" to "zabudni", "hovorte" to "hovor", "hoďte" to "hoď", "prineste" to "prines",
        "otvorte" to "otvor", "zatvorte" to "zatvor", "položte" to "polož", "zabite" to "zabi", "berte" to "ber",
        "zoberte" to "zober", "vezmite" to "vezmi", "kúpte" to "kúp", "povedzte" to "povedz", "sadnite" to "sadni",
        "bežte" to "bež", "držte" to "drž", "pusťte" to "pusť", "odíďte" to "odíď", "príďte" to "príď", "vypadnite" to "vypadni",
        "pohnite" to "pohni", "skryte" to "skry", "nastúpte" to "nastúp", "vystúpte" to "vystúp", "nasadnite" to "nasadni",
        "robte" to "rob", "nerobte" to "nerob", "spravte" to "sprav", "urobte" to "urob", "nájdite" to "nájdi",
        "zlezte" to "zlez", "vylezte" to "vylez", "ustúpte" to "ustúp", "sklapnite" to "sklapni",
    )
    /** Množná přídavná jména u „ste“ / „buďte“ → jednotné (mužský rod; ženský opraví [SlovakGender]). */
    private val SK_ADJ = mapOf(
        "opatrní" to "opatrný", "istí" to "istý", "pripravení" to "pripravený", "hotoví" to "hotový", "pokojní" to "pokojný",
        "šialení" to "šialený", "zranení" to "zranený", "vďační" to "vďačný", "radi" to "rád",
    )
    /** Příčestí, která se z množného čísla nepřevedou jen odtržením „i“ (išli → išiel, mohli → mohol). */
    private val SK_PARTICIPLES = mapOf(
        "šli" to "šiel", "išli" to "išiel", "prišli" to "prišiel", "odišli" to "odišiel", "našli" to "našiel", "vošli" to "vošiel",
        "nešli" to "nešiel", "ušli" to "ušiel", "vyšli" to "vyšiel", "prešli" to "prešiel", "došli" to "došiel",
        "mohli" to "mohol", "nemohli" to "nemohol", "pomohli" to "pomohol", "niesli" to "niesol", "priniesli" to "priniesol",
        "zabudli" to "zabudol", "padli" to "padol", "spadli" to "spadol", "ukradli" to "ukradol", "sadli" to "sadol",
        "zjedli" to "zjedol", "utiekli" to "utiekol", "boli" to "bol", "neboli" to "nebol",
    )
    private val SK_FILLERS = setOf("mi", "sa", "si", "to", "mu", "ho", "jej", "nám", "tam", "tu", "sem", "už", "ešte", "ma",
        "ti", "ťa", "by", "mňa")
    /** Předložky – za nimi „teba / tebe“, jinak „ťa / ti“. */
    private val SK_PREP = setOf("pre", "na", "o", "za", "do", "bez", "od", "u", "pri", "k", "ku", "s", "so", "v", "vo", "z",
        "zo", "cez", "okolo", "kvôli", "pred", "nad", "pod", "medzi", "proti", "oproti", "vedľa", "podľa", "voči", "mimo")
    private val SK_NOT_PARTICIPLE = setOf("kvôli", "chvíli", "veli", "keli")

    /** Sjednotí oslovení ve slovenském překladu na tykání; [source] je anglický originál. Pravidla jako [czech]. */
    fun slovak(source: String, translated: String): String {
        if (PLURAL_EN.containsMatchIn(source)) return translated
        val tokens = TOKEN.findAll(translated).map { it.value }.toMutableList()
        val words = tokens.indices.filter { tokens[it].first().isLetter() }
        fun low(i: Int) = tokens[i].lowercase()
        /** Slovo u [n] ve směru [step] přes nanejvýš dvě krátká slova. */
        fun near(n: Int, step: Int): Int? {
            var k = n + step
            var skipped = 0
            while (k in words.indices && low(words[k]) in SK_FILLERS && skipped < 2) { k += step; skipped++ }
            return if (k in words.indices) k else null
        }
        for ((n, i) in words.withIndex()) {
            val aux = low(i)
            if (aux != "ste" && aux != "buďte" && aux != "nebuďte") continue
            for (step in listOf(-1, 1)) {
                val k = near(n, step) ?: continue
                val w = low(words[k])
                val to = SK_ADJ[w] ?: if (aux != "ste") null else SK_PARTICIPLES[w] ?: if (
                    w.length >= 4 && w.endsWith("li") && w[w.length - 3] in "aeiuyáé" && w !in SK_NOT_PARTICIPLE) w.dropLast(1) else null
                if (to != null) tokens[words[k]] = keepCase(tokens[words[k]], to)
            }
        }
        for ((n, i) in words.withIndex()) {
            val w = low(i)
            val afterPrep = n > 0 && low(words[n - 1]) in SK_PREP
            val to = when (w) {
                "vás" -> if (afterPrep || n == 0) "teba" else "ťa"
                "vám" -> if (afterPrep || n == 0) "tebe" else "ti"
                else -> SK_WORDS[w] ?: skSuffix(w)
            } ?: continue
            tokens[i] = keepCase(tokens[i], to)
        }
        return tokens.joinToString("")
    }

    /** Spolehlivé koncovky: rozkazy na -jte (počkajte → počkaj, nebojte → neboj) a přítomný čas na -ujete. */
    private fun skSuffix(w: String): String? = when {
        w.length >= 6 && w.endsWith("ujete") -> w.dropLast(5) + "uješ"   // potrebujete → potrebuješ
        w.length >= 5 && w.endsWith("jte") -> w.dropLast(2)             // počkajte → počkaj, pokračujte → pokračuj
        else -> null
    }

    private fun keepCase(original: String, replacement: String): String =
        if (original.first().isUpperCase()) replacement.replaceFirstChar { it.uppercaseChar() } else replacement
}
