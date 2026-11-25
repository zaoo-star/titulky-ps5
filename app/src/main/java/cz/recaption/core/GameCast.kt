package cz.recaption.core

enum class Gender { M, F }

/** Postava hry: jméno, přezdívky a rod – podle rodu se v překladu opraví tvary („Lena šel“ → „Lena šla“). */
data class GameCharacter(val name: String, val gender: Gender, val aliases: List<String> = emptyList()) {
    val allNames: List<String> get() = listOf(name) + aliases
}

/**
 * Postavy jedné hry (nebo všech her dohromady) a jejich rod.
 *
 * Mluvčí z titulku („LENA: …“) se hledá bez ohledu na velikost písmen a diakritiku, i podle
 * prvního slova víceslovného jména („BEN“ = Ben Hartley). Jméno, které má v různých hrách různý rod,
 * se vynechá – raději nic než špatný tvar.
 */
class Cast(characters: List<GameCharacter>) {
    private val byKey: Map<String, Gender>
    /** Jména tak, jak je píše hra (velká písmena) – podle nich se hledá oslovení a postava v textu. */
    private val names: Map<String, Gender>

    init {
        val keys = HashMap<String, Gender?>()
        val exact = HashMap<String, Gender?>()
        fun put(m: HashMap<String, Gender?>, k: String, g: Gender) {
            if (k.isEmpty()) return
            m[k] = if (m.containsKey(k) && m[k] != g) null else g
        }
        for (c in characters) for (n in c.allNames) {
            put(keys, TextLogic.simplify(n), c.gender)
            put(exact, n, c.gender)
        }
        // první slovo víceslovného jména („Ben Hartley“ → „ben“), jen když nekoliduje s ničím jiným
        for (c in characters) for (n in c.allNames) {
            val first = TextLogic.simplify(n).substringBefore(' ')
            if (first.length >= 3 && first != TextLogic.simplify(n) && !keys.containsKey(first)) keys[first] = c.gender
        }
        byKey = keys.filterValues { it != null }.mapValues { it.value!! }
        names = exact.filterValues { it != null }.mapValues { it.value!! }
    }

    val isEmpty: Boolean get() = byKey.isEmpty()

    /** Rod mluvčího podle jména z titulku; null = neznámá postava. */
    fun genderOf(name: String?): Gender? = name?.let { byKey[TextLogic.simplify(it)] }

    // jednopísmenná jména (M, Q z bondovek) jen jako mluvčí – v textu by se pletla s čímkoli
    private val nameRx: Regex? = names.keys.filter { it.length >= 2 }.takeIf { it.isNotEmpty() }?.let { keys ->
        Regex(keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }) }

    private val vocStart: Regex? = nameRx?.let {
        Regex("^\\s*(?:(?:hey|oh|yo|listen|look|come on|okay|ok|well|damn|man)[,!]?\\s+)?(" + it.pattern + ")\\s*[,!?…]", RegexOption.IGNORE_CASE)
    }
    private val vocEnd: Regex? = nameRx?.let {
        Regex(",\\s*(" + it.pattern + ")\\s*[.!?…\"]*\\s*$", RegexOption.IGNORE_CASE)
    }

    /** Oslovená postava v anglickém originálu („Lena, you were right.“, „You okay, Le?“). */
    fun addressee(source: String): Gender? {
        val m = vocStart?.find(source) ?: vocEnd?.find(source) ?: return null
        return genderOf(m.groupValues[1])
    }

    /** Výskyty jmen postav v přeloženém textu (od, do, rod) – jména se nepřekládají, takže jsou v originále. */
    fun mentions(text: String): List<Triple<Int, Int, Gender>> {
        val r = nameRx ?: return emptyList()
        return r.findAll(text).mapNotNull { m ->
            val before = if (m.range.first > 0) text[m.range.first - 1] else ' '
            val after = if (m.range.last + 1 < text.length) text[m.range.last + 1] else ' '
            if (before.isLetter() || after.isLetter()) null
            else Triple(m.range.first, m.range.last + 1, names[m.value] ?: return@mapNotNull null)
        }.toList()
    }

    companion object {
        val EMPTY = Cast(emptyList())
    }
}

/** Rod v českém překladu – slova a tvary pro [GenderRules]. */
object CzechGender : GenderRules(GenderWords(
    aux1 = setOf("jsem", "nejsem", "bych", "abych", "kdybych"),
    aux2 = setOf("jsi", "nejsi", "bys", "abys", "kdybys", "ses", "sis", "buď", "nebuď"),
    link3 = setOf("je", "není", "bude", "nebude", "byl", "nebyl", "byla", "nebyla"),
    fillers = setOf("mi", "se", "si", "to", "mu", "ho", "jí", "nám", "tam", "tu", "sem", "už", "ještě", "mě",
        "ti", "tě", "tak", "taky", "také", "jen", "opravdu", "fakt", "vždycky", "nikdy", "včera", "dnes", "ale"),
    aux12Extra = setOf("jsme", "nejsme", "jste", "nejste", "bychom", "abychom", "kdybychom", "byste", "abyste", "kdybyste"),
    fillers3Extra = setOf("prý", "pak", "potom", "zase", "znovu", "právě", "zrovna", "hned",
        "konečně", "nakonec", "asi", "možná", "nejspíš", "určitě", "jistě", "dneska", "teď", "ráno", "večer", "skoro",
        "vůbec", "nejdřív", "pořád", "stejně", "jenom", "tady", "ji", "je", "vás", "nás", "jim", "vám", "by", "někde",
        "zpátky", "zpět", "rychle", "brzo", "nic", "něco", "nikoho", "nikomu", "všechno", "brzy", "dávno", "nedávno", "včas", "zas", "ani", "vlastně", "sama", "sám"),
    clause = setOf("a", "ale", "že", "protože", "když", "až", "jestli", "jestliže", "pokud", "aby", "kdyby",
        "než", "zatímco", "ač", "ačkoli", "i", "tak", "nebo", "anebo", "jakmile", "dokud"),
    subord = setOf("že", "který", "která", "které", "kterou", "kterého", "co", "kdo", "jak", "když", "protože",
        "aby", "kde", "kam", "jestli", "pokud", "než", "zatímco"),
    male = setOf("on", "něj", "něho", "jeho", "jemu", "ním", "nějž"),
    notPart = setOf("dál", "přítel", "nepřítel", "manžel", "anděl", "hotel", "kostel", "cíl", "generál",
        "admirál", "král", "ďábel", "model", "tunel", "panel", "level", "signál", "originál", "ideál", "kanál", "pól",
        "styl", "smysl", "úmysl", "průmysl", "kel", "sál", "stůl", "orel", "kozel", "osel", "posel", "kašel", "uhel",
        "fotbal", "basketbal", "festival", "materiál", "seriál", "kapitál", "metal", "rival", "sval", "obal", "účel", "gól",
        "bál", "mol", "apríl", "medail", "potenciál", "personál", "arzenál", "detail", "email", "mail", "kokpit", "pedál",
        "škola", "síla", "kola", "hala", "vila", "pila", "mula", "kapela", "tabule", "dcera"),
    short = mapOf(
        "rád" to "ráda", "nerad" to "nerada", "sám" to "sama", "připraven" to "připravena", "hotov" to "hotova",
        "zvědav" to "zvědava", "zraněn" to "zraněna", "ztracen" to "ztracena", "unaven" to "unavena", "nucen" to "nucena",
        "přesvědčen" to "přesvědčena", "ochoten" to "ochotna", "schopen" to "schopna", "neschopen" to "neschopna",
        "nemocen" to "nemocna", "povinen" to "povinna", "vděčen" to "vděčna", "spokojen" to "spokojena",
        "zatčen" to "zatčena", "mrtev" to "mrtva", "zvyklý" to "zvyklá", "živ" to "živa", "zdráv" to "zdráva",
        "jist" to "jista", "pozván" to "pozvána", "najat" to "najata", "zavřen" to "zavřena",
    ),
    been = setOf("byl", "byla", "nebyl", "nebyla"),
    imperative = setOf("buď", "nebuď"),
    alone = setOf("sám", "sama"),
)) {
    override fun form(w: String, g: Gender, adjectiveOnly: Boolean): String? =
        if (g == Gender.F) {
            when {
                w in short -> short[w]
                w in shortF -> w
                w.length >= 4 && w.endsWith("ý") -> w.dropLast(1) + "á"
                w.length >= 4 && w.endsWith("á") -> w
                adjectiveOnly -> null
                w.endsWith("šel") -> w.dropLast(3) + "šla"
                w.length >= 3 && w.endsWith("l") && !w.endsWith("tel") -> w + "a"
                w.length >= 4 && w.endsWith("la") -> w
                else -> null
            }
        } else {
            when {
                w in shortF -> shortF[w]
                w in short -> w
                w.length >= 4 && w.endsWith("á") -> w.dropLast(1) + "ý"
                w.length >= 4 && w.endsWith("ý") -> w
                adjectiveOnly -> null
                w.endsWith("šla") -> w.dropLast(3) + "šel"
                w.length >= 4 && w.endsWith("la") -> w.dropLast(1)
                w.length >= 3 && w.endsWith("l") && !w.endsWith("tel") -> w
                else -> null
            }
        }
}

/** Slova jednoho jazyka pro [GenderRules] (pomocná slovesa, výplň, spojky, výjimky). */
class GenderWords(
    /** pomocná slovesa první osoby (rod mluvčího) a druhé osoby (rod osloveného) */
    val aux1: Set<String>, val aux2: Set<String>,
    /** sloveso za jménem, za kterým následuje přídavné jméno („Lena je připravený“) */
    val link3: Set<String>,
    /** krátká slova mezi pomocným slovesem a příčestím */
    val fillers: Set<String>,
    /** pomocná slovesa množného čísla – s [aux1] a [aux2] příčestí u nich nepatří k postavě ve 3. osobě */
    val aux12Extra: Set<String>,
    /** příslovce (času, míry) a zájmena, přes která se ve 3. osobě hledá příčestí za jménem; žádná podstatná ani přídavná jména */
    val fillers3Extra: Set<String>,
    /** spojky, za kterými začíná nová (vedlejší) věta – příčestí hned za nimi nemá vyslovený podmět */
    val clause: Set<String>,
    /** spojky uvnitř věty, kde končí příčestí s podmětem jména („Noemi viděl, jak…“) */
    val subord: Set<String>,
    /** mužská zájmena v překladu – kde jsou, mluví se i o muži a zájmenné pravidlo se nepoužije */
    val male: Set<String>,
    /** podstatná jména a příslovce na -l / -la, která nejsou příčestí */
    val notPart: Set<String>,
    /** krátké tvary (po „jsem/jsi“), muž → žena */
    val short: Map<String, String>,
    /** „byl/byla“ – za nimi přídavné jméno („byl jsem mrtvý“) */
    val been: Set<String>,
    /** rozkaz „buď“ – za ním jen přídavné jméno */
    val imperative: Set<String>,
    /** „sám/sama“ hned za příčestím („Noemi přišel sám“) */
    val alone: Set<String>,
    /** „je“ – může být i zájmeno („Noemi je viděl“ = viděla je) */
    val isWord: String = "je",
    /** pomocná slovesa 2. osoby, která mohou být i něco jiného (sk „si“ = zvratné zájmeno) – jen s „you“ v originálu */
    val aux2Doubtful: Set<String> = emptySet(),
    /** slova z [aux2], která přesto nebrání 3. osobě (sk „si“: „Noemi si vzal“) */
    val notAux12: Set<String> = emptySet(),
)

/**
 * Rod v překladu (čeština, slovenčina). Překladač neví, kdo mluví, a píše skoro vždy v mužském rodě („Byl jsem
 * připravený“ i za Lucii). Podle profilu hry se opraví:
 *  - první osoba podle mluvčího z titulku („LUCIA: I was ready.“ → „Byla jsem připravená.“),
 *  - druhá osoba podle oslovené postavy („Lu, you were right.“ → „Měla jsi pravdu.“),
 *  - třetí osoba u jména postavy v textu: za jménem přes pár příslovcí („Noemi už šel“ → „Noemi už šla“,
 *    „…, že Noemi byl pryč“ → „…, že Noemi byla pryč“), dál přes „a/ale“ se stejným podmětem („Noemi přišel
 *    a řekl“ → „Noemi přišla a řekla“) i před jménem na začátku věty („Zavolal Noemi.“ → „Zavolala Noemi.“),
 *  - třetí osoba bez jména, když originál mluví o postavě zájmenem („Noemi called. She said she's coming.“
 *    → „Zavolala Noemi. Řekla, že přijde.“) – jen příčestí na začátku věty nebo vedlejší věty a jen u ženy.
 *
 * Mění se jen tvary těsně u pomocného slovesa nebo jména (nanejvýš přes dvě krátká slova)
 * a jen spolehlivé koncovky. Neznámé slovo zůstane, jak ho překladač napsal.
 * Slova jazyka jsou v [GenderWords], tvary v [form] ([CzechGender], [SlovakGender]).
 */
abstract class GenderRules(words: GenderWords) {
    private val AUX1 = words.aux1
    private val AUX2 = words.aux2
    private val AUX2_DOUBTFUL = words.aux2Doubtful
    private val LINK3 = words.link3
    private val FILLERS = words.fillers
    /** Pomocná slovesa první a druhé osoby včetně množného čísla – příčestí u nich nepatří k postavě ve 3. osobě. */
    private val AUX12 = AUX1 + AUX2 - words.notAux12 + words.aux12Extra
    private val FILLERS3 = FILLERS - "ale" + words.fillers3Extra
    private val CLAUSE = words.clause
    /** Slova, která mohou stát mezi začátkem věty a příčestím, aniž by byla podmětem („Pak mi zavolal…“). */
    private val PRE = FILLERS3 - setOf("to", words.isWord) - words.alone + setOf("by")
    private val SUBORD = words.subord
    private val MALE = words.male
    private val NOT_PART = words.notPart
    private val BEEN = words.been
    private val IMPERATIVE = words.imperative
    private val ALONE = words.alone
    private val IS = words.isWord
    protected val short: Map<String, String> = words.short
    protected val shortF: Map<String, String> = short.entries.associate { it.value to it.key }
    private val SHORT = short
    private val SHORT_F = shortF

    /** Tvar slova [w] (malými písmeny) v rodě [g]; null = slovo není příčestí ani přídavné jméno, které umíme. */
    protected abstract fun form(w: String, g: Gender, adjectiveOnly: Boolean): String?

    private companion object {
        /** Muž / žena v anglickém originálu (zájmena a běžná slova pro osobu) – pro odlišení, o kom věta mluví. */
        val EN_MALE = Regex("\\b(he|him|his|himself|he's|he'd|he'll|guy|man|cop|boss|dude|bro|brother|father|dad|son|boy|" +
            "uncle|husband|boyfriend|sir|mister|mr|king|grandpa|nephew|gentleman|fella|homeboy)\\b", RegexOption.IGNORE_CASE)
        val EN_FEMALE = Regex("\\b(she|her|hers|herself|she's|she'd|she'll|girl|woman|lady|mom|mother|sister|wife|" +
            "girlfriend|daughter|aunt|grandma|niece|madam|ma'am|mrs|ms|miss|queen)\\b", RegexOption.IGNORE_CASE)
        val EN_SHE = Regex("\\b(she|she's|she'd|she'll|her|herself)\\b", RegexOption.IGNORE_CASE)
        val EN_SHE_WAS = Regex("\\bshe(\\s+(was|wasn't|had been|has been|is|isn't)|'s been|'d been|'s)\\b", RegexOption.IGNORE_CASE)
        /** „it“ jako podmět v minulém čase (it was, it fell…) – mohl by to být mužský rod (obchod padl). */
        val EN_IT_PAST = Regex("\\bit(\\s+(was|wasn't|had|hadn't|did|didn't|got|went|came|\\w+ed)\\b|'d\\b)", RegexOption.IGNORE_CASE)
        /** Originál mluví k někomu – jen pak platí dvojznačné pomocné sloveso 2. osoby (sk „si“). */
        val EN_YOU = Regex("\\b(you|you're|you've|you'd|you'll|your|yourself|ya)\\b", RegexOption.IGNORE_CASE)
        /** Originál mluví i o někom třetím – dvojznačné „si“ pak může patřit k němu. */
        val EN_THIRD = Regex("\\b(he|him|his|he's|he'd|he'll|she|her|she's|she'd|she'll)\\b", RegexOption.IGNORE_CASE)
        val TOKEN = Regex("[\\p{L}']+|[^\\p{L}']+")
    }

    /**
     * [source] anglický originál části titulku, [translated] její český překlad, [speaker] jméno mluvčího
     * z titulku (nebo null). Bez známých postav vrátí překlad beze změny.
     */
    fun fix(cast: Cast, source: String, translated: String, speaker: String?): String {
        if (cast.isEmpty) return translated
        val me = cast.genderOf(speaker)
        val you = cast.addressee(source)
        val mentions = cast.mentions(translated)
        if (me == null && you == null && mentions.isEmpty()) return translated

        val tokens = TOKEN.findAll(translated).map { it.value }.toMutableList()
        val starts = IntArray(tokens.size)
        run { var p = 0; for (i in tokens.indices) { starts[i] = p; p += tokens[i].length } }
        val words = tokens.indices.filter { tokens[it].first().isLetter() }
        fun low(i: Int) = tokens[i].lowercase()
        /** Mezi dvěma slovy je čárka nebo konec věty → k sobě nepatří. */
        fun broken(a: Int, b: Int): Boolean {
            val (x, y) = if (a < b) a to b else b to a
            return (x + 1 until y).any { k -> !tokens[k].first().isLetter() && tokens[k].any { it in ",.!?;:…–" } }
        }
        /** Velké písmeno uprostřed věty = jméno („Jsem Daniel“) – to se nemění. */
        fun midSentenceName(i: Int): Boolean {
            if (!tokens[i].first().isUpperCase()) return false
            for (k in i - 1 downTo 0) {
                if (tokens[k].first().isLetter()) return true
                if (tokens[k].any { it in ".!?…" }) return false
            }
            return false
        }
        val done = HashSet<Int>()
        fun set(i: Int, g: Gender, adjectiveOnly: Boolean = false): Boolean {
            if (i in done) return true
            if (midSentenceName(i)) return false
            val to = convert(low(i), g, adjectiveOnly) ?: return false
            if (to != low(i)) tokens[i] = keepCase(tokens[i], to)
            done += i
            return true
        }
        /** Od slova [n] (index ve [words]) ve směru [step] najde první slovo mimo výplň a zkusí ho převést. */
        fun neighbour(n: Int, step: Int, g: Gender, adjectiveOnly: Boolean = false): Int? {
            var k = n + step
            var skipped = 0
            while (k in words.indices && low(words[k]) in FILLERS && skipped < 2) { k += step; skipped++ }
            if (k !in words.indices || broken(words[n], words[k])) return null
            return if (set(words[k], g, adjectiveOnly)) k else null
        }
        /**
         * Za „jsem“ bez příčestí hned u něj: první příčestí dál ve stejné větě, nejvýš 6 slov („ještě jsem nikoho
         * z nich na silnici neviděl“). Zastaví se na čárce a konci věty, spojce, jiném pomocném slovese, jménu
         * (velké písmeno) a přídavném jménu (to má rod předmětu, ne mluvčího) – ve sporných případech nic.
         */
        fun farParticiple(n: Int, g: Gender): Int? {
            var k = n + 1
            while (k in words.indices && k - n <= 6) {
                if (broken(words[k - 1], words[k])) return null
                val w = low(words[k])
                if (w in AUX12 || w in CLAUSE || w in SUBORD || w in LINK3) return null
                if (tokens[words[k]].first().isUpperCase() || w.endsWith("ý") || w.endsWith("á")) return null
                if (w.endsWith("l") || w.endsWith("la")) return if (isParticiple(w, g) && set(words[k], g)) k else null
                k++
            }
            return null
        }
        /** „byl/byla“ + přídavné jméno za ním („byl jsem mrtvý“, „jsi byl připravený“). */
        fun chain(k: Int?, g: Gender) {
            if (k == null) return
            val w = low(words[k])
            if (w in BEEN) neighbour(k, 1, g, adjectiveOnly = true)
        }

        /** Mezi slovy [a] a [b] (indexy ve [words]) je jen mezera, případně čárka („přišel, ale…“). */
        fun onlyCommaBetween(a: Int, b: Int): Boolean =
            (words[a] + 1 until words[b]).all { k -> tokens[k].all { it == ',' || it.isWhitespace() } }
        /** Slovo [k] (index ve [words]) začíná větu: před ním je jen začátek, interpunkce nebo spojka a příslovce. */
        fun clauseStart(k: Int): Boolean {
            var j = k - 1
            var skipped = 0
            while (j >= 0) {
                if (broken(words[j], words[j + 1])) return true
                val w = low(words[j])
                if (w in CLAUSE) return true
                if (w !in PRE || skipped >= 3) return false
                j--; skipped++
            }
            return true
        }
        /** U slova [k] (přes výplň, ve stejné větě) je pomocné sloveso 1. nebo 2. osoby („viděl jsem“, „jsi byl“). */
        fun nearAux(k: Int): Boolean {
            for (step in intArrayOf(-1, 1)) {
                var j = k + step
                var skipped = 0
                while (j in words.indices && !broken(words[j - (if (step > 0) 1 else -1)], words[j])) {
                    val w = low(words[j])
                    if (w in AUX12) return true
                    if (w !in FILLERS3 || skipped >= 2) break
                    j += step; skipped++
                }
            }
            return false
        }
        /** Za příčestím [k] (podmět jména) pokračuje věta „a/ale + příčestí“ se stejným podmětem („přišel a řekl“). */
        fun continueSameSubject(k: Int, g: Gender) {
            var j = k + 1
            var seen = 0
            // přes předmět („vzal auto a odjel“), ale ne přes vedlejší větu nebo konec věty
            while (j in words.indices && seen < 4) {
                val w = low(words[j])
                if (w in AUX12 || w in SUBORD || tokens[words[j]].first().isUpperCase()) return
                if ((w == "a" || w == "ale") && (!broken(words[j - 1], words[j]) || onlyCommaBetween(j - 1, j))) break
                if (broken(words[j - 1], words[j])) return
                j++; seen++
            }
            if (j !in words.indices) return
            var v = j + 1
            var skipped = 0
            while (v in words.indices && !broken(words[v - 1], words[v]) && low(words[v]) in FILLERS3 && low(words[v]) !in AUX12 && skipped < 3) { v++; skipped++ }
            if (v !in words.indices || broken(words[v - 1], words[v])) return
            if (!isParticiple(low(words[v]), g) || nearAux(v)) return
            if (set(words[v], g)) { chain(v, g); continueSameSubject(v, g) }
        }
        /** Od posledního slova jména [last] hledá vpřed (přes nanejvýš tři příslovce/zájmena) přísudek jména. */
        fun after3(last: Int, g: Gender) {
            var k = last + 1
            var skipped = 0
            while (k in words.indices && !broken(words[k - 1], words[k])) {
                val w = low(words[k])
                if (w in AUX12) return
                if (w in LINK3) {
                    set(words[k], g)
                    // „Noemi je viděl“ = viděla je (zájmeno), jinak „je připravený“
                    if (neighbour(k, 1, g, adjectiveOnly = true) == null && w == IS && k + 1 in words.indices &&
                        !broken(words[k], words[k + 1]) && isParticiple(low(words[k + 1]), g) && set(words[k + 1], g)) {
                        chain(k + 1, g); continueSameSubject(k + 1, g)
                    } else if (w in BEEN) continueSameSubject(k, g)
                    return
                }
                if (w in SHORT || w in SHORT_F) { set(words[k], g); k++; continue }   // „Noemi sám přišel“
                if (isParticiple(w, g)) {
                    if (set(words[k], g)) {
                        chain(k, g)
                        // „Noemi přišel sám“ – jen „sám/sama“, jiné přídavné jméno může patřit k předmětu
                        if (k + 1 in words.indices && !broken(words[k], words[k + 1]) && low(words[k + 1]) in ALONE)
                            set(words[k + 1], g)
                        continueSameSubject(k, g)
                    }
                    return
                }
                if (w !in FILLERS3 || skipped >= 3) return
                k++; skipped++
            }
        }

        for ((n, i) in words.withIndex()) {
            val w = low(i)
            val g = when {
                w in AUX1 -> me
                w in AUX2 && (w !in AUX2_DOUBTFUL || (EN_YOU.containsMatchIn(source) && !EN_THIRD.containsMatchIn(source))) -> you
                else -> null
            } ?: continue
            val adjOnlyAfter = w in IMPERATIVE
            val before = if (!adjOnlyAfter) neighbour(n, -1, g) else null
            chain(before, g)
            val after = neighbour(n, 1, g, adjectiveOnly = adjOnlyAfter)
            chain(after, g)
            // první osoba s příčestím dál ve větě („jsem nikoho z nich na silnici neviděl“ ve zprávě od ženské postavy)
            if (w in AUX1 && before == null && after == null) chain(farParticiple(n, g), g)
        }
        // třetí osoba: jméno postavy a za ním (přes pár příslovcí) příčestí, nebo „je/byl“ + přídavné jméno
        val srcMentions = cast.mentions(source)
        for ((from, to, g) in mentions) {
            val n = words.indexOfFirst { starts[it] >= from }.takeIf { it >= 0 } ?: continue
            // víceslovné jméno („Ben Hartley“): pokračuje se od posledního slova jména
            var last = n
            while (last + 1 in words.indices && tokens[words[last + 1]].first().isUpperCase() && !broken(words[last], words[last + 1])) last++
            after3(last, g)
            // obrácený slovosled („Zavolal Noemi.“, „Pak přišel Noemi.“) – jen když originál má jméno jako podmět
            // („Noemi called“) a nemluví o nikom opačného rodu („He called Noemi.“ = Noemi je předmět)
            val name = translated.substring(from, to)
            if (n > 0 && !broken(words[n - 1], words[n]) && subjectInSource(source, name, srcMentions) &&
                !oppositeInSource(source, g, srcMentions) && clauseStart(n - 1) && !nearAux(n - 1) &&
                isParticiple(low(words[n - 1]), g)) {
                if (set(words[n - 1], g)) { chain(n - 1, g); continueSameSubject(n - 1, g) }
            }
        }
        // zájmeno v originálu („Noemi called. She said she's coming.“): jen žena (výchozí mužský rod překladače),
        // jen když originál zmiňuje ženskou postavu a nikoho mužského rodu a příčestí stojí na začátku věty
        if (srcMentions.any { it.third == Gender.F } && srcMentions.none { it.third == Gender.M } &&
            EN_SHE.containsMatchIn(source) && !EN_MALE.containsMatchIn(source) && !EN_IT_PAST.containsMatchIn(source) &&
            words.none { low(it) in MALE }) {
            val sheWas = EN_SHE_WAS.containsMatchIn(source)
            for (k in words.indices) {
                val i = words[k]
                if (i in done || !clauseStart(k) || nearAux(k)) continue
                val w = low(i)
                if (w in LINK3) {
                    // „…, že je/byl připravený“ bez podmětu – jen se „she is/was“ v originálu a přídavným jménem hned za ním
                    if (!sheWas || k + 1 !in words.indices || broken(i, words[k + 1])) continue
                    if (convert(low(words[k + 1]), Gender.F, adjectiveOnly = true) == null) continue
                    set(i, Gender.F); set(words[k + 1], Gender.F, adjectiveOnly = true)
                    continue
                }
                if (!isParticiple(w, Gender.F) || w.endsWith("la")) continue
                // další slovo s velkým písmenem (jiné než ženská postava) může být podmět („Řekl Tomas…“)
                if (k + 1 in words.indices && !broken(i, words[k + 1]) && tokens[words[k + 1]].first().isUpperCase() &&
                    mentions.none { it.first == starts[words[k + 1]] && it.third == Gender.F }) continue
                if (set(i, Gender.F)) chain(k, Gender.F)
            }
        }
        return tokens.joinToString("")
    }

    /** Slovo je minulé příčestí jednotného čísla (na -l / -la), které umíme převést na rod [g]. */
    private fun isParticiple(w: String, g: Gender): Boolean {
        if (w in NOT_PART || w in SHORT || w in SHORT_F || w.endsWith("ý") || w.endsWith("á")) return false
        if (!(w.endsWith("l") || w.endsWith("la"))) return false
        // „-la“ je jen ženský tvar příčestí, když jeho mužský tvar je také příčestí („šla“, „byla“, „řekla“)
        if (w.endsWith("la") && ((w.length < 4 && w != "šla") || w.dropLast(1).endsWith("tel"))) return false
        return convert(w, g, adjectiveOnly = false) != null
    }

    /** Jméno [name] je v originálu podmětem: hned za ním je sloveso malým písmem („Noemi called“, „Noemi's gone“). */
    private fun subjectInSource(source: String, name: String, srcMentions: List<Triple<Int, Int, Gender>>): Boolean =
        srcMentions.any { (a, b, _) ->
            source.substring(a, b) == name && Regex("^(?:'s)?\\s+(?!and\\b|or\\b)[a-z]").containsMatchIn(source.substring(b))
        }

    /** V originálu je řeč i o někom opačného rodu (zájmeno, postava) – pak není jasné, ke komu tvar patří. */
    private fun oppositeInSource(source: String, g: Gender, srcMentions: List<Triple<Int, Int, Gender>>): Boolean =
        srcMentions.any { it.third != g } || (if (g == Gender.F) EN_MALE else EN_FEMALE).containsMatchIn(source)

    /** Převede tvar na rod [g]; null = slovo není příčestí ani přídavné jméno, které umíme. */
    private fun convert(w: String, g: Gender, adjectiveOnly: Boolean): String? {
        if (w in NOT_PART || w.length < 2) return null
        return form(w, g, adjectiveOnly)
    }

    private fun keepCase(original: String, replacement: String): String =
        if (original.first().isUpperCase()) replacement.replaceFirstChar { it.uppercaseChar() } else replacement
}
