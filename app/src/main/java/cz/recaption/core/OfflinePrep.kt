package cz.recaption.core

/**
 * Úprava textu pro offline překlad (Google ML Kit), aby byl výsledek lepší:
 *
 * 1) Slang a nářečí (ain't, reckon, gonna, ridin'…) převede do spisovné angličtiny,
 *    které offline překladač rozumí.
 * 2) Jména postav, míst a gangů schová za zástupné značky [1], [2]…, aby je překladač
 *    nepřeložil ("Hunter" → "Lovec", "Rose" → "Růže"), a po překladu je vrátí.
 *    Když překladač značky poškodí, použije se překlad bez značek (nikdy nic nezmizí).
 */
object OfflinePrep {


    // slang → spisovná angličtina (celá slova, bez ohledu na velikost písmen)
    private val SLANG: List<Pair<String, String>> = listOf(
        "gonna" to "going to",
        "tryna" to "trying to",
        "hafta" to "have to",
        "oughta" to "ought to",
        "shoulda" to "should have",
        "coulda" to "could have",
        "woulda" to "would have",
        "musta" to "must have",
        "lotta" to "lot of",
        "lotsa" to "lots of",
        "c'mon" to "come on",
        "gotcha" to "got you",
        "whatcha" to "what are you",
        "betcha" to "bet you",
        "d'you" to "do you",
        "cuz" to "because",
        "'bout" to "about",
        "'round" to "around",
        "s'pose" to "suppose",
        "prob'ly" to "probably",
        "somethin'" to "something",
        "nothin'" to "nothing",
        "wanna" to "want to",
        "gotta" to "have to",
        "y'all" to "you all",
        "ya'll" to "you all",
        "y'know" to "you know",
        "lemme" to "let me",
        "gimme" to "give me",
        "kinda" to "kind of",
        "sorta" to "sort of",
        "outta" to "out of",
        "dunno" to "do not know",
        "'cause" to "because",
        "'cos" to "because",
        "'til" to "until",
        "'em" to "them",
        "ol'" to "old",
        "o'" to "of",
        "reckon" to "think",
        "reckoned" to "thought",
        "fella" to "man",
        "fellas" to "men",
        "feller" to "man",
        "howdy" to "hello",
        "yonder" to "over there",
        "gal" to "girl",
        "gals" to "girls",
        "ya" to "you",
        "yer" to "your",
        "nope" to "no",
        "yep" to "yes",
        "yup" to "yes",
        "nah" to "no",
        "aye" to "yes",
    )

    private val SLANG_RX: List<Pair<Regex, String>> = SLANG.map { (from, to) ->
        // hranice slova: před a za nesmí být písmeno ani apostrof
        Regex("(?<![A-Za-z'])" + Regex.escape(from) + "(?![A-Za-z'])", RegexOption.IGNORE_CASE) to to
    }

    // "ain't" podle osoby: I ain't → I am not, we ain't → we are not, he ain't → he is not
    private val AINT_I = Regex("(?<![A-Za-z'])(I)\\s+ain't(?![A-Za-z'])")
    private val AINT_ARE = Regex("(?<![A-Za-z'])(you all|you|we|they|y'all|ya'll|ya)\\s+ain't(?![A-Za-z'])", RegexOption.IGNORE_CASE)
    private val AINT = Regex("(?<![A-Za-z'])ain't(?![A-Za-z'])", RegexOption.IGNORE_CASE)

    // "them Pinkertons" → "those Pinkertons" (them jako ukazovací zájmeno před podstatným jménem)
    private val THEM_DET = Regex("(?<![A-Za-z'])([Tt])hem\\s+(?=[A-Z][A-Za-z']*s\\b|(?:boys|men|folks|fellas|horses|guns|people|ones|days|lawmen|bastards|cows|wagons)\\b)")

    // ridin' → riding, nothin' → nothing (apostrof místo "g")
    private val DROPPED_G = Regex("\\b([A-Za-z]{2,}in)'(?![A-Za-z])")

    private val TOKEN_RX = Regex("\\[\\s*(\\d{1,2})\\s*]")
    private val SPACES = Regex(" {2,}")

    /**
     * Ustálené obraty, které offline překladač překládá doslova („Hang on a second“ → „Viset na sekundu“), nahrazené
     * obyčejnou větou. Data přicházejí se seznamem her ([GameDictionaries.idioms], soukromé repo; každá dvojice ověřená
     * na výstupu ze zkoušky překladu nebo z dávek ze hry) – do stažení žádné.
     */

    /**
     * Zesilující „fucking / freaking“ před slovem („the whole fuckin' thing“ → „celou kurva“, slovo za ním překladač
     * ztratil). Vypouští se; ne před zájmenem nebo předložkou, kde jde o sloveso („fucking with me“, „fucking around“).
     */
    private val INTENSIFIER = Regex(
        "\\b(?:fucking|freaking|frigging|fricking|effing)\\s+(?=[A-Za-z])(?!(?:around|with|up|off|out|over|in|on|me|you|him|her|it|them|us|a|an|the|this|that|my|your)\\b)",
        RegexOption.IGNORE_CASE,
    )

    /** „G.D.“ = goddamn (zkratka velkými písmeny, proto mimo obraty ze slovníku – náhrada je vždy malými). */
    private val GD = Regex("\\bG\\.\\s?D\\.?(?=\\s|$)")

    /** Převede slang do spisovné angličtiny. Zachová velké písmeno na začátku slova. */
    fun normalize(text: String): String {
        // typografický apostrof (’) jako obyčejný, ať platí pravidla níž („C’mon“, „ain’t“, „freakin’“)
        var t = text.replace('’', '\'').replace('‘', '\'')
        t = DROPPED_G.replace(t) { it.groupValues[1] + "g" }
        t = GD.replace(t, "goddamn")
        for ((rx, to) in GameProfiles.dict.idioms) t = rx.replace(t) { m -> matchCase(m.value, expand(to, m)) }
        t = INTENSIFIER.replace(t) { m -> if (m.value.first().isUpperCase()) "\u0000" else "" }
        // vypuštěné slovo na začátku věty: další slovo dostane velké písmeno
        if ('\u0000' in t) t = Regex("\u0000([a-z])").replace(t) { it.groupValues[1].uppercase() }.replace("\u0000", "")
        t = AINT_I.replace(t) { "${it.groupValues[1]} am not" }
        t = AINT_ARE.replace(t) { "${it.groupValues[1]} are not" }
        t = AINT.replace(t) { m -> matchCase(m.value, "is not") }
        for ((rx, to) in SLANG_RX) {
            t = rx.replace(t) { m -> matchCase(m.value, to) }
        }
        return t
    }

    /** „$1“ v náhradě = text skupiny (náhrada z lambdy se jinak bere doslova). */
    private fun expand(to: String, m: MatchResult): String =
        if ('$' !in to) to else Regex("\\$(\\d)").replace(to) { g -> m.groupValues.getOrNull(g.groupValues[1].toInt()) ?: "" }

    private fun matchCase(original: String, replacement: String): String {
        val first = original.firstOrNull { it.isLetter() } ?: return replacement
        if (!first.isUpperCase()) return replacement
        val i = replacement.indexOfFirst { it.isLetter() }
        return if (i < 0) replacement else replacement.substring(0, i) + replacement[i].uppercaseChar() + replacement.substring(i + 1)
    }

    /** Text připravený k překladu. */
    data class Prepared(
        /** Spisovný text bez značek – záloha, když značky nepřežijí překlad. */
        val plain: String,
        /** Spisovný text se jmény schovanými za [1], [2]… */
        val protectedText: String,
        /** Schovaná jména v pořadí značek. */
        val names: List<String>,
    )

    /** Slang → spisovná angličtina, včetně "them boys" → "those boys". */
    fun standardize(text: String): String =
        THEM_DET.replace(normalize(text)) { m -> m.groupValues[1] + "hose " }

    /** [english] = titulky jsou anglicky (výchozí); jinak se slang a obraty neupravují, jen se schovají jména. */
    fun prepare(text: String, english: Boolean = true): Prepared {
        val plain = if (english) standardize(text) else text
        val names = ArrayList<String>()
        val rx = GameProfiles.dict.nameRx
        val protectedText = if (rx == null) plain else rx.replace(plain) { m ->
            val name = m.value
            var idx = names.indexOf(name)
            if (idx < 0) { names.add(name); idx = names.size - 1 }
            "[${idx + 1}]"
        }
        return Prepared(plain, protectedText, names)
    }

    /**
     * Vrátí jména na místo značek. Null = překladač značky poškodil (chybí, přebývají)
     * → je potřeba použít překlad textu [Prepared.plain].
     */
    fun finish(p: Prepared, translated: String): String? {
        if (p.names.isEmpty()) return translated
        val found = TOKEN_RX.findAll(translated).map { it.groupValues[1].toInt() }.toList()
        val expected = (1..p.names.size).toSet()
        if (found.toSet() != expected) return null
        return TOKEN_RX.replace(translated) { m ->
            val i = m.groupValues[1].toInt()
            p.names.getOrNull(i - 1) ?: m.value
        }.replace(SPACES, " ").trim()
    }

    /** Celý postup s libovolným překladačem (pro testy a jednoduché použití). */
    inline fun translate(text: String, translator: (String) -> String?): String? {
        val p = prepare(text)
        if (p.names.isNotEmpty()) {
            val withTokens = translator(p.protectedText)
            if (withTokens != null) finish(p, withTokens)?.let { return it }
        }
        return translator(p.plain)
    }
}
