package cz.recaption.core

import kotlin.math.max

/** Jeden řádek textu rozpoznaný OCR (souřadnice v pixelech snímku). */
data class OcrLine(
    val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int,
    /** Slova řádku s vlastními rámečky (prvky čtečky; pro barvu jména a barevná slova úkolu). Prázdné = neznámé. */
    val words: List<OcrLine> = emptyList(),
    /** Kolik řádků obrazu je v tomhle spojeno (dvouřádkový úkol, od 0.97.149); výška písma = výška / řádky. */
    val rows: Int = 1,
) {
    val height: Int get() = bottom - top
}

/** Blok textu = skupina řádků, které k sobě patří (např. dvouřádkový titulek). */
data class OcrBlock(val lines: List<OcrLine>)

/**
 * Část titulku: volitelné jméno mluvčího ("Marek") a samotný text.
 * [choice] = jedna z voleb odpovědi, které hra nabízí (ukáže se jako položka seznamu).
 */
data class Segment(
    val speaker: String?, val body: String, val choice: Boolean = false,
    /** Pokyn pro hráče pod titulkem (GTA: „Take your shot“) – ne replika; ukazuje se zvlášť menším písmem. */
    val prompt: Boolean = false,
    /** Vybraná položka přeloženého menu hry – černý text na bílém jako ve hře (od 0.97.111, místo značky „»“). */
    val selected: Boolean = false,
    /** Popisek zvuku v závorkách na samostatném řádku („[dog barking]“, [Captions], od 0.97.116). */
    val caption: Boolean = false,
)

/**
 * Výsledek výběru textu z jedné oblasti obrazovky.
 * [top]/[bottom] = svislý rozsah použitých řádků v pixelech oblasti (-1, když nic).
 */
data class Picked(
    val text: String, val lineCount: Int, val top: Int = -1, val bottom: Int = -1,
    /** Vodorovný rozsah použitých řádků v pixelech oblasti (-1, když nic). */
    val left: Int = -1, val right: Int = -1,
    /** Velikost písma titulku v pixelech (0, když nic), odhadnutá z výšky řádků – podle ní se řídí velikost písma překladu. */
    val lineHeight: Int = 0,
    /**
     * Řádek s úkolem pod titulky (GTA: nejspodnější řádek oddělený od titulků volným řádkem), vyčleněný
     * z titulků – ukazuje se zvlášť ([ObjectiveTracker]). V pixelech oblasti; null = žádný.
     */
    val objective: OcrLine? = null,
)

/**
 * Čistá logika bez závislosti na Androidu – dá se testovat na počítači.
 */
object TextLogic {

    /**
     * Horní hrana pásu pro úkol (zlomek výšky obrazu, GTA VI). Měřeno na snímcích testera 21. 11. 2026 (dávky
     * TT4B49AQ, DQWRWGER, GQAKD9Q7, X6UVANQJ): úkol 94,3–94,9 % (spodek 97 %), titulky nejníž 87,8–88,5 % (spodek
     * 91–92 %) – i v cutscénách s černými pruhy a i když je úkol zobrazený. Do 0.97.82 bylo 0,86 a druhý řádek titulku
     * (88 %) se bral jako úkol (krátká druhá část repliky, „What?“).
     */
    const val OBJECTIVE_ZONE_TOP = 0.915f

    private val WS = Regex("\\s+")
    // osamocené "l" nebo "1" na místě zájmena I ("l think" → "I think")
    private val LONE_L = Regex("(^|[\\s\"'(])[l1](?=\\s+[a-z'])")
    // zájmeno "i" a jeho tvary (i'm, i'll, i've, i'd) v textu převedeném na malá písmena
    private val LOWER_I = Regex("(?<![A-Za-z'])i(?=$|[^A-Za-z]|'(?:m|ll|ve|d)\\b)")
    private val MARKS = Regex("\\p{M}+")
    private val CAPTION_PART = Regex("^(.*?)(\\[[^\\]]+])(.*)$")
    private val NAME_COLON = Regex("^[A-Z][a-z][A-Za-z'.\\-]{0,20}(?: [A-Z][a-z][A-Za-z'.\\-]{0,20}){0,2}:")

    /**
     * Nápisy z cedulí slité s popiskem zvuku na jednom řádku (od 0.97.137, dávka KJ7U-2FHD: „D [car door opens, closes]
     * ATERSPORTS Pilot: Hey, Toby!“): slova bez malých písmen před popiskem a mezi popiskem a „Jméno:“ pryč.
     */
    private fun dropSignWords(t: String): String {
        val m = CAPTION_PART.find(t) ?: return t
        if (!Captions.isLine(m.groupValues[2])) return t
        val pre = m.groupValues[1].trim().takeIf { p -> p.any { it.isLowerCase() } }.orEmpty()
        val words = m.groupValues[3].trim().split(' ').filter { it.isNotEmpty() }.toMutableList()
        var k = 0
        while (k < words.size && words[k].none { it.isLowerCase() } && words[k].any { it.isLetter() }) k++
        val rest = words.drop(k).joinToString(" ")
        val post = if (k > 0 && NAME_COLON.containsMatchIn(rest)) rest else words.joinToString(" ")
        return listOf(pre, m.groupValues[2], post).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private val CAPTION_END = Regex("^\\[\\s*([a-z][a-z ]{2,40}?)[lI1)}](?=\\s|$)")

    // "Marek: We need money." / "Uncle Bob: ..." / "Anna-Marie: ..." (max 3 slova, každé s velkým písmenem)
    // Mezera za dvojtečkou není povinná (OCR ji někdy vynechá), ale pak musí následovat písmeno nebo uvozovka.
    private val SPEAKER = Regex("^([A-Z][A-Za-z'.\\-]{0,20}(?: [A-Z][A-Za-z'.\\-]{0,20}){0,2}):\\s*((?=[A-Za-z\"'(.\\[]).+)$")
    // "Kael says: …" / "Kael says; …" (Diablo IV) – jméno a „says“
    private val SPEAKER_SAYS = Regex("^([A-Z][A-Za-z'.\\-]{0,20}(?: [A-Z][A-Za-z'.\\-]{0,20}){0,2}) says[:;]\\s*(.+)$")
    // "[Marek] We need money." – jméno v hranatých závorkách
    // (od 0.97.116 aspoň jedno slovo s velkým písmenem – „[laughs] You know!“ je popisek zvuku, ne mluvčí, viz [Captions];
    // „[old Kessler]“ s přečteným malým písmenem dál mluvčí)
    private val SPEAKER_BRACKET = Regex("^\\[(?=[^\\]]*\\b[A-Z])([A-Za-z][A-Za-z'.\\- ]{0,30})]\\s*(.+)$")
    // samotné jméno na řádku ("MAREK:" nebo "[Marek]"), text je až na dalším řádku
    private val SPEAKER_ONLY = Regex("^(?:[A-Z][A-Za-z'.\\-]{0,20}(?: [A-Z][A-Za-z'.\\-]{0,20}){0,2}:|\\[(?=[^\\]]*\\b[A-Z])[A-Za-z][A-Za-z'.\\- ]{0,30}])$")
    // jedno slovo jako celá replika: "Wait", "RUN" (první písmeno velké, zbytek malý, nebo celé velké)
    private val SINGLE_WORD = Regex("^(?:[A-Z][a-z]{2,}|[A-Z]{3,})[.!?…,]*$")

    // Slova, která vypadají jako jméno mluvčího, ale nejsou (nápovědy, úkoly…)
    private val NOT_SPEAKERS = setOf(
        "Note", "Tip", "Hint", "Objective", "Mission", "Goal", "Reward", "Warning", "Bounty",
        "Wanted", "Status", "Chapter", "Honor", "Help", "Info", "Location", "Time", "Cost", "Price"
    )

    // "Ill"/"IIl"/"I1l" před dalším slovem je skoro vždy špatně přečtené "I'll"
    private val ILL = Regex("\\bI[Il1]l(?=\\s+[a-z])")
    private val CONTRACTIONS = mapOf(
        "im" to "I'm", "ive" to "I've", "dont" to "don't", "didnt" to "didn't", "doesnt" to "doesn't",
        "isnt" to "isn't", "wasnt" to "wasn't", "arent" to "aren't", "werent" to "weren't",
        "couldnt" to "couldn't", "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "havent" to "haven't",
        "hasnt" to "hasn't", "youre" to "you're", "theyre" to "they're", "thats" to "that's",
        "whats" to "what's", "theres" to "there's",
    )
    private val NO_APOSTROPHE = Regex("\\b(?:[Ii]m|[Ii]ve|[Dd]ont|[Dd]idnt|[Dd]oesnt|[Ii]snt|[Ww]asnt|[Aa]rent|[Ww]erent|" +
        "[Cc]ouldnt|[Ww]ouldnt|[Ss]houldnt|[Hh]avent|[Hh]asnt|[Yy]oure|[Tt]heyre|[Tt]hats|[Ww]hats|[Tt]heres)\\b")

    /** Opraví typické chyby OCR a sjednotí mezery. */
    fun clean(raw: String): String {
        var t = raw
            .replace('|', 'I')
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .replace(WS, " ")
            .trim()
        // popisek zvuku s koncovou závorkou přečtenou jako písmeno („[rain patteringl“, dávka 7N4Y-RTQ8) → „[rain pattering]“
        if (t.startsWith("[") && ']' !in t) t = t.replace(CAPTION_END, "[$1]")
        if ('[' in t) t = dropSignWords(t)
        t = t.replace(LONE_L, "$1I")
        // drobné písmo: OCR vynechá apostrof ("Ill take" → "I'll take", "dont" → "don't")
        t = t.replace(ILL) { "I'll" }
        t = t.replace(NO_APOSTROPHE) { m ->
            val w = m.value
            val fixed = CONTRACTIONS[w.lowercase()] ?: return@replace w
            if (w[0].isUpperCase()) fixed.replaceFirstChar { it.uppercaseChar() } else fixed
        }
        return t
    }

    /**
     * Rozhodne, jestli řádek vypadá jako opravdový text (titulek, nápověda),
     * a ne jako HUD (čísla, munice, kompas, peníze) nebo šum z OCR.
     */
    fun isUseful(raw: String): Boolean {
        val t = clean(raw)
        if (t.length < 2) return false
        val nonSpace = t.count { !it.isWhitespace() }
        val letters = t.count { it.isLetter() }
        if (letters < 2) return false
        if (letters.toFloat() / nonSpace < 0.6f) return false
        val words = t.split(' ').count { w -> w.count { it.isLetter() } >= 2 }
        val endsLikeSentence = t.last() in ".!?…"
        if (words >= 2) return true
        // samotné jméno mluvčího ("MIO:") – text přijde na dalším řádku
        if (SPEAKER_ONLY.matches(t)) return true
        // jedno slovo: buď delší ("Hello"), krátká odpověď s interpunkcí ("No."), nebo slovo jako "Wait"
        return letters >= 5 || (endsLikeSentence && letters >= 2) || SINGLE_WORD.matches(t)
    }

    /**
     * Vypadá text jako mluvená replika, a ne jako položka menu, nápis nebo šum?
     * Replika má jméno mluvčího, nebo větnou interpunkci, nebo je to delší věta s malými písmeny,
     * nebo běžné krátké zvolání ("Wait", "Come on"). Položky menu ("New Game", "OPTIONS", "Load")
     * a náhodně přečtené útržky ("Som") nic z toho nemají.
     */
    fun looksLikeDialogue(text: String): Boolean {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return false
        // seznam krátkých položek pod sebou = menu
        if (lines.size >= 3 && lines.all { l -> wordCount(l) <= 3 && l.none { it in ".!?…," } && splitSpeaker(l).speaker == null }) return false
        return lines.any { lineIsDialogue(it) }
    }

    private fun wordCount(t: String) = t.split(' ').count { w -> w.any { it.isLetter() } }

    private fun lineIsDialogue(line: String): Boolean {
        // popisek zvuku („[dog barking]“) je součást titulků (od 0.97.116)
        if (Captions.isLine(line) || Captions.leading(line) != null) return true
        val seg = splitSpeaker(line)
        if (seg.speaker != null) return seg.body.count { it.isLetter() } >= 2
        val t = seg.body
        if (QuickWords.isKnown(t)) return true
        // pokyny hry ("Press to Start", "Press any button to continue", "Select a mode") nejsou repliky
        if (UI_HINT.containsMatchIn(t) && t.lastOrNull()?.let { it in ".!?…" } != true) return false
        val words = wordCount(t)
        val punct = t.any { it in ".!?…," } || t.contains('\'') || t.contains('"')
        val lower = t.count { it.isLowerCase() }
        return when {
            words >= 5 -> lower >= 4 || punct           // dlouhá věta (i psaná VELKÝMI, když má interpunkci)
            words >= 2 -> punct                         // krátká: musí mít interpunkci nebo apostrof
            else -> t.lastOrNull()?.let { it in ".!?…" } == true && t.count { it.isLetter() } >= 2
        }
    }

    // "Press X to mount", "Hold [] to aim", "Tap @ for ..." – nápovědy k ovládání, nejsou to titulky
    private val CONTROL_PROMPT = Regex(
        "^(?i:press|hold|tap|mash|click|push)\\s+" +
            // tlačítko: "X", "R2", "@", "[]", "(X)" – ne obyčejné slovo ("Hold on to me" je replika)
            "(?:[A-Z0-9]{1,3}|[^\\sA-Za-z0-9]{1,3}|\\[[^\\]]{0,4}\\]|\\([^)]{0,4}\\))" +
            "(?:\\s*\\+\\s*(?:[A-Z0-9]{1,3}|[^\\sA-Za-z0-9]{1,3}))?" +
            "\\s+(?i:to|for)\\b"
    )

    private val UI_HINT = Regex("^(?i:press|hold|tap|click|push|select|choose|loading|connecting|signed in|available)\\b")

    /**
     * Velikost písma řádku v pixelech, odhadnutá z jeho výšky. Čtečka měří jen výšku písmen, která
     * v řádku opravdu jsou: řádek bez vysokých písmen a bez písmen pod linku ("we are so sure") je
     * mnohem nižší než řádek s nimi ("Type quickly!"), i když je písmo stejně velké. Proto se výška
     * přepočítá podle toho, jaká písmena řádek obsahuje (poměry běžného bezpatkového písma).
     */
    fun fontSizeOf(line: OcrLine): Int {
        val t = line.text
        val tall = t.any { it.isUpperCase() || it.isDigit() || it in "bdfhklt!?'\"" }
        val below = t.any { it in "gjpqy,;()[]" }
        val part = (if (tall) 0.74f else 0.55f) + (if (below) 0.20f else 0f)
        return (line.height / line.rows.coerceAtLeast(1) / part).toInt()
    }

    /** Značka volby odpovědi na začátku řádku textu pásu ("• I'll take the job."). */
    const val CHOICE_MARK = "• "

    // číslo nebo odrážka před volbou ve hře: "1.", "2)", "•", ">" (pomlčka ne – tou titulky střídají mluvčí)
    private val CHOICE_PREFIX = Regex("^(?:\\d[.)]|[•>▶►])\\s*")
    // volba zní jako řeč: zájmena první a druhé osoby
    private val SPEECH_WORD = Regex("(?<![A-Za-z'])(?i:I|I'm|I'll|I've|I'd|you|you're|you'll|we|we're|we'll|me|my|your|us|let's)(?![A-Za-z'])")
    // položky běžných menu – kde je některá z nich, nejde o volby v rozhovoru
    private val MENU_WORDS = setOf(
        "continue", "new game", "load game", "load", "save", "save game", "options", "settings", "quit", "quit game",
        "exit", "back", "resume", "restart", "controls", "graphics", "audio", "credits", "start", "play", "yes", "no",
        "ok", "accept", "cancel", "confirm", "main menu", "inventory", "map", "journal", "skills", "help", "extras",
        "easy", "normal", "hard", "store", "online", "multiplayer", "single player", "story", "photo mode",
    )

    /** Text volby bez čísla nebo odrážky ze hry, nebo null, když řádek jako volba nevypadá. */
    private fun choiceItem(raw: String): String? {
        val t = raw.replace(CHOICE_PREFIX, "").trim()
        if (t.length < 2 || t.length > 70) return null
        val first = t.first()
        if (!(first.isUpperCase() || first in "[\"(….")) return null
        if (wordCount(t) !in 1..10) return null
        if (splitSpeaker(t).speaker != null) return null
        if (UI_HINT.containsMatchIn(t) || CONTROL_PROMPT.containsMatchIn(t)) return null
        if (simplify(t) in MENU_WORDS) return null
        return t
    }

    /**
     * Volby odpovědí v rozhovoru (3–6 krátkých řádků pod sebou, zarovnaných vlevo nebo očíslovaných),
     * případně pod replikou, na kterou se odpovídá. Vrací text pásu, kde volby začínají [CHOICE_MARK],
     * nebo null, když si tím není jistá – pak se s textem zachází jako dřív.
     * Menu hry ("Continue / New Game / Options"), seznam úkolů ani nápovědy k ovládání volbami nejsou:
     * aspoň jedna volba musí znít jako řeč (zájmeno, otazník, tečka…).
     */
    fun choiceText(lines: List<OcrLine>): String? {
        val ls = lines.sortedBy { it.top }
        if (ls.size < 3) return null
        // dva texty vedle sebe (stejný řádek) nejsou seznam pod sebou
        for (i in 1 until ls.size) if (ls[i].top < ls[i - 1].bottom - ls[i - 1].height / 3) return null
        val texts = ls.map { clean(it.text) }
        for (k in 0..minOf(3, ls.size - 3)) {
            val itemLines = ls.subList(k, ls.size)
            if (itemLines.size > 6) continue
            val items = texts.subList(k, texts.size).map { choiceItem(it) }
            if (items.any { it == null }) continue
            val marked = texts.subList(k, texts.size).all { CHOICE_PREFIX.containsMatchIn(it) }
            val h = itemLines.map { it.height }.sorted()[itemLines.size / 2]
            val leftAligned = itemLines.maxOf { it.left } - itemLines.minOf { it.left } <= maxOf(6, h * 6 / 10)
            val widths = itemLines.map { it.right - it.left }
            // stejně dlouhé řádky zarovnané vlevo mohou být i odstavec – volby mají různou délku
            val varied = widths.max() - widths.min() > h * 3 / 2
            if (!marked && !(leftAligned && varied)) continue
            val speech = items.any { t -> t!!.last() in ".!?…" || t.startsWith("[") || SPEECH_WORD.containsMatchIn(t) }
            if (!speech) continue
            if (!marked) {
                // Bez čísel: dialogové okno se zarovnáním vlevo může mít celé věty pod sebou – ty končí
                // tečkou všechny. A replika nad volbami bývá jinde než ony, ne zarovnaná s nimi.
                if (items.all { it!!.last() in ".!?…" }) continue
                if (k > 0 && kotlin.math.abs(ls[0].left - itemLines[0].left) <= maxOf(6, h * 6 / 10)) continue
            }
            val lead = if (k == 0) null else texts.subList(0, k).joinToString(" ")
            if (lead != null && (!looksLikeDialogue(lead) || lead.last() !in ".!?…\"'")) continue
            return (listOfNotNull(lead) + items.map { CHOICE_MARK + it }).joinToString("\n")
        }
        return null
    }

    /** Jsou v textu volby odpovědí? (Hráč nad nimi přemýšlí déle, než trvá běžný titulek.) */
    fun hasChoices(text: String): Boolean = text.split('\n').any { it.startsWith(CHOICE_MARK) }

    /** Jak dlouho smí volby stát beze změny, než se pruh schová (běžný text stojící déle je nápis nebo menu). */
    const val CHOICES_STALE_MS = 60_000L

    /** Text pásu ze segmentů (pro televizi LG, historii a log): "Jméno: text", volby s odrážkou. */
    fun joinSegments(segments: List<Segment>): String = segments.joinToString("\n") { s ->
        when {
            s.choice -> CHOICE_MARK + s.body
            s.speaker != null -> "${s.speaker}: ${s.body}"
            else -> s.body
        }
    }

    /** Od tolika řádků textu v oblasti je to menu nebo jiná plná obrazovka, ne titulek. */
    const val MENU_LINES = 7

    /** Hudební značky, kterými hry označují texty písní (♪ ♫ ♬ ♩ a emoji not). */
    // 🎵 🎶 🎼 (U+1F3B5, U+1F3B6, U+1F3BC) jako samostatné možnosti – zápis \x{…} Kotlin pro iPhone nezná
    private val MUSIC = Regex("[\u2669\u266A\u266B\u266C\u266D\u266F]|\uD83C\uDFB5|\uD83C\uDFB6|\uD83C\uDFBC")

    /**
     * Text písně (rádio v autě, hudba ve scéně) – hry ho v titulcích označují notou (♪ … ♪).
     * Písně se nepřekládají: rušily by a nejsou to repliky (0.93, GTA VI s rádiem).
     */
    fun isLyrics(raw: String): Boolean = MUSIC.containsMatchIn(raw) && !Captions.isLine(clean(raw))

    private val MODEL_CODE = Regex("^(?:[A-Z]{1,4}\\d{2,}[A-Z]*|\\d{2,}[A-Z]{1,4})[,.]?$")

    /**
     * Název zbraně / věci v rozhraní hry („L M306 RIFLE“ s ikonou L1 – dávka XPXK-QHZ6, od 0.97.116; dřív se přilepil
     * k replice nebo ukázal šedě jako pokyn): bez malých písmen, nejvýš 4 slova a jedno z nich kód s číslem („M306“).
     */
    fun weaponHud(raw: String): Boolean {
        val t = clean(raw)
        if (t.any { it.isLowerCase() }) return false
        val words = t.split(' ').filter { it.isNotEmpty() }
        return words.size in 1..4 && words.any { MODEL_CODE.matches(it) || MODEL_CODE.containsMatchIn(it.take(5)) && it.length > 5 && it.drop(4).all { c -> c.isUpperCase() || c == ',' } }
    }

    /** Nápověda k ovládání hry (ne titulek). */
    fun isControlPrompt(raw: String): Boolean = CONTROL_PROMPT.containsMatchIn(clean(raw))

    /**
     * Z bloků OCR složí text. Bloky řadí shora dolů, řádky v bloku spojí mezerou,
     * bloky mezi sebou novým řádkem. Ignoruje příliš malý text (HUD).
     *
     * Režim "jen titulky" ([subtitlesOnly]): titulky jsou vždy vodorovně vycentrované,
     * takže řádky, jejichž střed je dál než [centerTolerance] px od středu obrazovky
     * ([screenCenterX] v souřadnicích oblasti), se zahodí – stejně jako nápovědy k ovládání.
     * Menu, inventář, HUD a různá hlášení tak aplikace nečte.
     */
    fun assemble(
        blocks: List<OcrBlock>,
        minLineHeight: Int,
        subtitlesOnly: Boolean = false,
        screenCenterX: Int = 0,
        centerTolerance: Int = Int.MAX_VALUE,
        /** Vyšší řádky nejsou titulky (nadpisy, loga, nápisy ve scéně). */
        maxLineHeight: Int = Int.MAX_VALUE,
        /** Hlášky a menu podle profilu hry ([GameProfile.screen]). */
        screen: GameScreen = GameScreen.EMPTY,
        /** Vyčlenit řádek s úkolem pod titulky ([Picked.objective]) – jen hry, jejichž profil to má. */
        objectives: Boolean = false,
        /** Naučená horní hrana řádku s úkolem v pixelech oblasti (-1 = ještě nevíme) – pro úkol bez titulku nad ním. */
        objectiveTop: Int = -1,
        /** Úkol leží jen v pásu úplně dole na obrazovce: horní hrana řádku aspoň tady (px oblasti; -1 = bez omezení). */
        objectiveMinTop: Int = -1,
    ): Picked {
        if (screen.isMenu(blocks.flatMap { b -> b.lines.map { it.text } })) return Picked("", 0)
        val sorted = blocks.sortedBy { b -> b.lines.minOfOrNull { it.top } ?: Int.MAX_VALUE }
        // Obrazovka plná textu (menu, inventář, mapa) není scéna s titulky.
        if (subtitlesOnly) {
            val all = blocks.sumOf { b -> b.lines.count { it.height >= minLineHeight && isUseful(it.text) } }
            if (all >= MENU_LINES) return Picked("", 0)
        }
        // 1) řádky, které vypadají jako titulek (podle velikosti, obsahu a vycentrování)
        val kept = ArrayList<Pair<Int, OcrLine>>()      // (pořadí bloku, řádek)
        sorted.forEachIndexed { bi, block ->
            // titulek o víc řádcích může mít řádky zarovnané vlevo – pak je vycentrovaný jen blok jako celek
            // Stejně tak dialogové okno se zarovnáním vlevo (běžné v RPG): text začíná vlevo, ale sahá přes
            // střed obrazovky. Údaje v rozích (mapa, peníze, úkol) přes střed nesahají nikdy.
            val bl = block.lines.minOfOrNull { it.left } ?: 0
            val br = block.lines.maxOfOrNull { it.right } ?: 0
            val blockCentered = block.lines.isNotEmpty() && (
                kotlin.math.abs((bl + br) / 2 - screenCenterX) <= centerTolerance ||
                    (centerTolerance != Int.MAX_VALUE && bl < screenCenterX && br > screenCenterX)
                )
            block.lines
                .filter { it.height in minLineHeight..maxLineHeight && isUseful(it.text) && !isLyrics(it.text) && !screen.isHud(it.text) && !weaponHud(it.text) }
                .filter { l ->
                    !subtitlesOnly || (
                        !isControlPrompt(l.text) &&
                            (blockCentered || kotlin.math.abs((l.left + l.right) / 2 - screenCenterX) <= centerTolerance)
                        )
                }
                .forEach { kept.add(bi to it) }
        }
        // 1b) řádek s úkolem (GTA): nejspodnější řádek, nad kterým je volný řádek – ke kterému titulku nepatří.
        //     Bez titulku nad ním se pozná podle místa, kde už úkol jednou byl ([objectiveTop]).
        val objective = if (objectives) objectiveLine(kept.map { it.second }, objectiveTop, objectiveMinTop) else null
        // dvouřádkový úkol je spojený řádek – z titulků pryč oba původní řádky (leží v jeho rámečku)
        if (objective != null) kept.removeAll { it.second === objective ||
            (objective.rows > 1 && it.second.top >= objective.top && it.second.bottom <= objective.bottom && it.second.left >= objective.left && it.second.right <= objective.right) }
        // 2) "jen titulky": texty daleko nad sebou k sobě nepatří (nadpis v menu + titulek dole).
        //    Bere se jen nejspodnější skupina – pruh pak zakryje jen titulek, ne půl obrazovky.
        var used: List<Pair<Int, OcrLine>> = kept
        if (subtitlesOnly && kept.size > 1) {
            val byTop = kept.sortedBy { it.second.top }
            var start = 0
            for (k in 1 until byTop.size) {
                val prevBottom = byTop.subList(start, k).maxOf { it.second.bottom }
                val gap = byTop[k].second.top - prevBottom
                if (gap > 1.5f * maxOf(byTop[k - 1].second.height, byTop[k].second.height)) start = k
            }
            used = byTop.subList(start, byTop.size)
        }
        // 2b) nápis ve scéně těsně nad replikou (logo na korbě auta) k titulku nepatří
        used = withoutLabelsAboveSpeaker(used)
        // nápisy z cedulí (VERZÁLKY bez interpunkce a bez mluvčího) vedle opravdového titulku / popisku zvuku (od 0.97.137)
        if (used.any { u -> clean(u.second.text).any { it.isLowerCase() } }) used = used.filterNot { signLine(clean(it.second.text)) }
        if (used.isEmpty()) return Picked("", 0, objective = objective)
        val ls = used.map { it.second }
        fun picked(text: String) = Picked(
            text, ls.size,
            ls.minOf { it.top }, ls.maxOf { it.bottom }, ls.minOf { it.left }, ls.maxOf { it.right },
            // dolní medián (od 0.97.115): u dvou řádků menší – vyšší nápis ve scéně (logo auta) písmo nezvětší
            lineHeight = ls.map { fontSizeOf(it) }.sorted()[(ls.size - 1) / 2],
            objective = objective,
        )
        // volby odpovědí: každá zvlášť, ne slité do jednoho odstavce
        if (subtitlesOnly) choiceText(ls)?.let { return picked(it) }
        // řádky jednoho bloku se spojí mezerou; řádek, který začíná jménem mluvčího, je nová replika
        val parts = ArrayList<String>()
        for (bl in used.groupBy { it.first }.toSortedMap().values) {
            var cur = ""
            for (l in bl.map { it.second }.sortedBy { it.top }) {
                val t = clean(l.text)
                if (cur.isNotEmpty() && !SPEAKER_ONLY.matches(cur) && splitSpeaker(t).speaker != null) { parts.add(cur); cur = t }
                else cur = if (cur.isEmpty()) t else "$cur $t"
            }
            if (cur.isNotEmpty()) parts.add(cur)
        }
        // samotné jméno bez textu není titulek
        if (parts.all { SPEAKER_ONLY.matches(it) }) return Picked("", 0, objective = objective)
        val text = mergeSpeakerLines(parts).joinToString("\n").trim()
        // "jen titulky": položky menu, nápisy a šum se nepřekládají – jen to, co vypadá jako replika
        if (subtitlesOnly && !looksLikeDialogue(text)) return Picked("", 0, objective = objective)
        return picked(text)
    }

    /**
     * Krátký samostatný nápis (logo auta, cedule) těsně nad řádkem, který začíná mluvčím („Viktor: …“), k titulku
     * nepatří – titulek s mluvčím začíná jménem. Dávka testera 5QKC-T4US: logo na korbě pickupu se jednou
     * přečetlo blízko nad titulkem a pruh ukázal nápis slitý se jménem mluvčího.
     * Nápis = bez mluvčího a bez jakékoli interpunkce, nejvýš 3 slova a buď jedno slovo, nebo celý VERZÁLKAMI.
     * Nevyřadí se pokračování předchozí repliky („…what we“ / „need“ / „Tomas: …“): řádek nad nápisem
     * nesmí být nedokončený.
     */
    private fun withoutLabelsAboveSpeaker(used: List<Pair<Int, OcrLine>>): List<Pair<Int, OcrLine>> {
        if (used.size < 2) return used
        val byTop = used.sortedBy { it.second.top }
        val drop = HashSet<OcrLine>()
        for (k in 0 until byTop.size - 1) {
            val t = clean(byTop[k].second.text)
            if (!sceneLabel(t)) continue
            if (splitSpeaker(clean(byTop[k + 1].second.text)).speaker == null) continue
            val above = byTop.getOrNull(k - 1)?.second?.let { clean(it.text) }
            if (above != null && above.isNotEmpty() && above.last() !in ".!?…\"" && !sceneLabel(above)) continue
            drop += byTop[k].second
        }
        return if (drop.isEmpty()) used else used.filter { it.second !in drop }
    }

    /** Nápis z cedule: aspoň 3 písmena, žádné malé, bez mluvčího, popisku a větné interpunkce na konci. */
    private fun signLine(t: String): Boolean {
        if (t.count { it.isLetter() } < 3 || t.any { it.isLowerCase() }) return false
        if (t.last() in ".!?…\"'" || Captions.isLine(t) || t.startsWith("[")) return false
        return splitSpeaker(t).speaker == null && !SPEAKER_ONLY.matches(t)
    }

    /** Nápis ve scéně: bez interpunkce a mluvčího, jedno slovo nebo nejvýš 3 slova VERZÁLKAMI („UAPID“, „Vapid“, „NO PARKING“). */
    private fun sceneLabel(t: String): Boolean {
        if (t.isEmpty() || t.any { it in ".,!?…:;'\"" } || t.startsWith(CHOICE_MARK.trim())) return false
        val words = wordCount(t)
        if (words !in 1..3) return false
        val letters = t.filter { it.isLetter() }
        return words == 1 && t.first().isUpperCase() || (letters.length >= 2 && letters.none { it.isLowerCase() })
    }

    /**
     * Vypadá text jako úkol pro hráče? Úkol v GTA je krátký pokyn s velkým písmenem na začátku („Take your shot“,
     * „Follow Lena“). Ne: začíná malým písmenem nebo spojkou (pokračování věty), končí čárkou/středníkem, má mluvčího
     * „Jméno:“ (i s chybou čtení) nebo zvuk v hranatých závorkách („[thudding]“). Od 0.97.58 – do té doby se jako úkol
     * brala druhá část dvouřádkového titulku a zůstávala viset (záznam majitele 21. 11. 2026).
     */
    fun objectiveShape(text: String): Boolean {
        val t = clean(text).trim()
        val first = t.firstOrNull { it.isLetterOrDigit() } ?: return false
        if (first.isLetter() && first.isLowerCase()) return false
        if (t.endsWith(",") || t.endsWith(";")) return false
        if ('[' in t || ']' in t) return false
        if (SPEAKERISH.containsMatchIn(t)) return false
        val w = t.split(Regex("\\s+")).firstOrNull()?.lowercase()?.trim(',', '.', '!', '?', '"', '“', '”')
        if (w in CONTINUATIONS) return false
        return true
    }

    /** Končí řádek větou (tečka, !, ?, …, uvozovka/závorka za nimi)? */
    fun endsSentence(text: String): Boolean {
        val t = clean(text).trim().trimEnd('"', '“', '”', '\'', ')', '’')
        return t.isEmpty() || t.last() in ".!?…" || t.endsWith("..")
    }

    /** „Stranger:“, „Old Man:“ i přečtené s chybou – slovo/slova s velkým písmenem a dvojtečka na začátku. */
    private val SPEAKERISH = Regex("^[A-Z][\\p{L}.'’ -]{0,30}:")
    /** Spojky a slova, kterými v titulcích začíná pokračování věty na dalším řádku. */
    private val CONTINUATIONS = setOf("and", "or", "but", "so", "because", "cause", "then", "that", "which", "who", "to", "of", "with", "for")

    /**
     * Řádek s úkolem z řádků, které prošly jako titulek: nejspodnější řádek bez mluvčího, nad nímž je mezera aspoň
     * 0,6 výšky řádku a nejvýš 2,5 (volný řádek mezi titulkem a úkolem), nebo – když nad ním nic není – ležící
     * na naučeném místě úkolu ([objectiveTop], ± 0,7 výšky řádku). Od 0.97.58 jen pod [minTop] (pás úplně dole),
     * jen v tvaru úkolu ([objectiveShape]) a jen pod ukončenou větou ([endsSentence]).
     */
    fun objectiveLine(lines: List<OcrLine>, objectiveTop: Int = -1, minTop: Int = -1): OcrLine? {
        if (lines.isEmpty()) return null
        val last = lines.maxByOrNull { it.top } ?: return null
        // úkol je v GTA vždy úplně dole na obrazovce – výš položený řádek je titulek (od 0.97.58)
        if (minTop >= 0 && last.top < minTop) return null
        if (splitSpeaker(clean(last.text)).speaker != null || last.text.trim().startsWith(CHOICE_MARK.trim())) return null
        if (last.text.count { it.isLetter() } < 3) return null
        val h = last.height.coerceAtLeast(1)
        val nearest = lines.filter { it !== last && it.bottom <= last.top + h / 4 }.maxByOrNull { it.bottom }
        // dvouřádkový úkol (od 0.97.149, tester při lovu zvířat: úkol s tlačítky na dva řádky se bral jako titulek a pruh ho
        // překryl): spodní řádek v pásu úkolu, hned nad ním řádek stejně velkého písma, který sám leží těsně nad pásem
        // (titulky končí kolem 0,88 výšky, úkol na dva řádky sahá jen kousek nad 0,915) – oba dohromady jsou jeden úkol
        if (minTop >= 0 && nearest != null) twoLineObjective(lines, nearest, last, minTop)?.let { return it }
        if (!objectiveShape(last.text)) return null
        // V pásu úkolu ([minTop], od 0.97.83 podle snímků testera, [OBJECTIVE_ZONE_TOP]) titulky nikdy nejsou: úkol je
        // i samotný řádek (bez naučeného místa – samotný úkol se dřív přeložil jako titulek) a i pod
        // nedočtenou větou (oblast čtení uřízla „…in VIP.“ na „…in VI“ a úkol se přilepil k titulku).
        // Jen řádek těsně pod jiným (mezera pod 0,6 výšky řádku) je pořád druhý řádek titulku. Měří se výškou
        // menšího z obou řádků (od 0.97.85): titulek velikosti Large (5,5 % výšky obrazu) nad úkolem (2,5 %) má mezeru
        // jen 0,6 své výšky a úkol se přilepil k titulku (dávka SF5525SZ). Dva řádky jednoho titulku mají stejné písmo.
        if (minTop >= 0) {
            if (nearest == null) return last
            return if (last.top - nearest.bottom >= 0.6f * minOf(h, nearest.height.coerceAtLeast(1))) last else null
        }
        // řádek nad ním nekončí větou → spodní řádek je pokračování téhož titulku, ne úkol
        // („…we should go back and“ / „get the others,“ – záznam majitele 21. 11. 2026)
        if (nearest != null && !endsSentence(nearest.text)) return null
        if (nearest != null) {
            val gap = last.top - nearest.bottom
            val ref = maxOf(h, nearest.height)
            if (gap >= 0.6f * ref && gap <= 2.5f * ref) return last
        }
        // bez titulku nad ním (nebo daleko nad ním): jen na místě, kde úkol už byl
        val alone = nearest == null || last.top - nearest.bottom > 2.5f * maxOf(h, nearest.height)
        return if (alone && objectiveTop >= 0 && kotlin.math.abs(last.top - objectiveTop) <= 0.7f * h) last else null
    }

    /**
     * Dva řádky jednoho úkolu: [upper] těsně nad [lower] (mezera pod 0,6 řádku), písmo podobně velké (±30 %), [upper] začíná
     * nejvýš 1,6 řádku nad pásem úkolu ([minTop]), bez mluvčího, v tvaru úkolu, středy řádků blízko sebe (úkol je uprostřed)
     * a nad [upper] těsně nic dalšího (tři řádky = titulek). Vrací spojený řádek ([OcrLine.rows] = 2), jinak null.
     */
    internal fun twoLineObjective(lines: List<OcrLine>, upper: OcrLine, lower: OcrLine, minTop: Int): OcrLine? {
        val hl = lower.height.coerceAtLeast(1); val hu = upper.height.coerceAtLeast(1)
        if (lower.top - upper.bottom >= 0.6f * minOf(hl, hu)) return null
        if (hu > hl * 1.3f || hl > hu * 1.3f) return null
        if (upper.top < minTop - 1.6f * hl) return null
        if (splitSpeaker(clean(upper.text)).speaker != null || !objectiveShape(upper.text)) return null
        val cu = (upper.left + upper.right) / 2f; val cl = (lower.left + lower.right) / 2f
        if (kotlin.math.abs(cu - cl) > maxOf(upper.right - upper.left, lower.right - lower.left) * 0.25f) return null
        val above = lines.filter { it !== upper && it !== lower && it.bottom <= upper.top + hu / 4 }.maxByOrNull { it.bottom }
        if (above != null && upper.top - above.bottom < 0.6f * minOf(hu, above.height.coerceAtLeast(1))) return null
        return OcrLine(upper.text.trim() + " " + lower.text.trim(), minOf(upper.left, lower.left), upper.top,
            maxOf(upper.right, lower.right), lower.bottom, upper.words + lower.words, rows = 2)
    }

    /**
     * Text v jiném jazyce, než ze kterého se překládá (hra už je přeložená, nabídka konzole, šum)?
     * U angličtiny stačí dvě písmena s diakritikou. U jiného jazyka titulků ([source]) diakritika
     * patří k věci – za cizí se bere jen jiné písmo než latinka (azbuka, asijská písma…).
     */
    fun looksForeign(text: String, source: SourceLang = SourceLang.EN): Boolean {
        var n = 0
        for (c in text) if (c.isLetter() && (if (source.english) c.code > 0x7F else !isLatin(c))) { n++; if (n >= 2) return true }
        return false
    }

    private fun isLatin(c: Char): Boolean =
        try { Character.UnicodeScript.of(c.code) == Character.UnicodeScript.LATIN } catch (_: Exception) { true }

    private val WORD = Regex("[\\p{L}\\p{Nd}\\p{Nl}\\p{No}]+(?:['’][\\p{L}]+)?")

    private fun words(s: String) = WORD.findAll(s.lowercase()).map { it.value }.toList()

    /** Stejné slovo i s drobnou chybou čtení (jedno písmeno vedle u slov od čtyř písmen). */
    private fun sameWord(a: String, b: String) =
        a == b || (a.length >= 4 && b.length >= 4 && kotlin.math.abs(a.length - b.length) <= 1 && levenshtein(a, b) <= 1)

    /**
     * Je [read] jen ochuzené čtení textu [shown]? Prázdné čtení, nebo méně slov, která všechna
     * v tomtéž pořadí jsou v [shown] (např. OCR z třířádkového titulku přečetlo jen poslední řádek).
     * Slova navíc nebo jiná slova = jiný (nebo opravený) text, ne ochuzené čtení.
     */
    fun isSubsetRead(read: String, shown: String): Boolean {
        val s = words(shown)
        if (s.isEmpty()) return false
        val r = words(read)
        if (r.isEmpty()) return true
        if (r.size >= s.size) return false
        var i = 0
        for (w in s) if (i < r.size && sameWord(r[i], w)) i++
        return i == r.size
    }

    /** Vypadá text jako celá věta (ne rozpracovaný nebo useknutý titulek)? */
    fun looksComplete(text: String): Boolean {
        val t = text.trim()
        return t.length >= 8 && t.last() in ".!?…\"')"
    }

    private val NON_ALNUM = Regex("[^a-z0-9]+")

    fun simplify(s: String): String {
        val n = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(MARKS, "")
        return n.replace(NON_ALNUM, " ").trim()
    }

    /**
     * Když OCR rozdělí jedno dlouhé souvětí do dvou bloků a druhý nezačíná mluvčím,
     * ponechá je oddělené – titulky s mluvčím ale drží pohromadě.
     */
    private fun mergeSpeakerLines(parts: List<String>): List<String> {
        val out = ArrayList<String>()
        for (p in parts) {
            val prev = out.lastOrNull()
            if (prev != null && SPEAKER_ONLY.matches(prev)) { out[out.size - 1] = "$prev $p"; continue }
            if (prev != null && splitSpeaker(p).speaker == null && splitSpeaker(prev).speaker != null &&
                prev.last() !in ".!?…\"" && p.first().isLowerCase()
            ) {
                out[out.size - 1] = "$prev $p"
            } else out.add(p)
        }
        return out
    }

    /** Oddělí jméno mluvčího ("Jonas: Hello") od textu. */
    fun splitSpeaker(line: String): Segment {
        val t = line.trim()
        val m = SPEAKER_SAYS.find(t) ?: SPEAKER.find(t) ?: SPEAKER_BRACKET.find(t) ?: return Segment(null, t)
        val name = m.groupValues[1].trim()
        if (NOT_SPEAKERS.any { it.equals(name, ignoreCase = true) }) return Segment(null, t)
        return Segment(name, m.groupValues[2].trim())
    }

    /**
     * Text psaný jen VELKÝMI písmeny převede na běžnou větu ("WE HAVE TO GO!" → "We have to go!").
     * Překladač si s velkými písmeny neporadí (bere je jako zkratky a jména). Jiný text nechá být.
     */
    fun normalizeCase(text: String, english: Boolean = true): String {
        val letters = text.count { it.isLetter() }
        if (letters < 4 || text.any { it.isLowerCase() }) return text
        val sb = StringBuilder(text.lowercase())
        var start = true
        for (i in sb.indices) {
            val c = sb[i]
            if (c.isLetter()) {
                if (start) { sb[i] = c.uppercaseChar(); start = false }
            } else if (c == '.' || c == '!' || c == '?' || c == '…') start = true
        }
        // samotné „i“ = anglické „I“ (jen u angličtiny – v italštině, polštině… je to jiné slovo)
        return if (english) sb.toString().replace(LOWER_I, "I") else sb.toString()
    }

    /** Jméno psané velkými písmeny ukáže jako běžné jméno ("ANNA-MARIE" → "Anna-Marie"). */
    fun normalizeName(name: String): String {
        // OCR občas přečte první písmeno jména malé ("old Kessler")
        if (name.isNotEmpty() && name[0].isLowerCase()) return name.replaceFirstChar { it.uppercaseChar() }
        if (name.length < 3 || name.any { it.isLowerCase() }) return name
        val sb = StringBuilder(name.lowercase())
        var start = true
        for (i in sb.indices) {
            if (sb[i].isLetter()) { if (start) { sb[i] = sb[i].uppercaseChar(); start = false } } else start = true
        }
        return sb.toString()
    }

    /**
     * Text připravený pro překladač: samotné slovo bez tečky ("Wait") se překládá špatně,
     * proto dostane tečku. [finishTranslation] ji z překladu zase odebere.
     */
    fun forTranslator(body: String): String {
        val t = body.trim()
        return if (t.isNotEmpty() && !t.contains(' ') && t.last().isLetter()) "$t." else t
    }

    /** Uklidí překlad: pryč s interpunkcí na začátku, tečka navíc pryč, velké písmeno jako v originále. */
    fun finishTranslation(source: String, translated: String): String {
        var t = translated.trim().trimStart(',', '.', ';', ':', ' ')
        val s = source.trim()
        if (s.isNotEmpty() && s.last().isLetter() && t.endsWith(".") && !t.endsWith("..")) t = t.dropLast(1)
        return matchFirstCase(s, t)
    }

    /** Překlad začíná velkým písmenem, když jím začínal i původní text. */
    fun matchFirstCase(source: String, translated: String): String {
        val s = source.firstOrNull { it.isLetter() } ?: return translated
        val i = translated.indexOfFirst { it.isLetter() }
        if (i < 0 || !s.isUpperCase() || translated[i].isUpperCase()) return translated
        return translated.substring(0, i) + translated[i].uppercaseChar() + translated.substring(i + 1)
    }

    /** Rozdělí celý text pásu na segmenty (každý řádek může mít svého mluvčího). */
    /**
     * Pokyn pro hráče na posledním řádku (GTA: „Take your shot“, „Wait your turn“ pod replikou): bez mluvčího,
     * 1–6 slov, začíná velkým písmenem, bez interpunkce na konci, a nad ním je aspoň jeden řádek. Takový řádek
     * se označí [Segment.prompt]. Jen pro hry, jejichž profil to má ([GameProfile.prompts]).
     */
    fun markPrompt(segs: List<Segment>): List<Segment> {
        if (segs.size < 2) return segs
        val last = segs.last()
        val words = last.body.split(' ').count { w -> w.any { it.isLetter() } }
        val ok = last.speaker == null && !last.choice && !last.caption && !Captions.isLine(last.body) && words in 1..6 && last.body.first().isUpperCase() &&
            last.body.last() !in ".!?…,\"'”“-–:;" && !isLyrics(last.body)
        return if (ok) segs.dropLast(1) + last.copy(prompt = true) else segs
    }

    fun segments(text: String): List<Segment> =
        text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.flatMap { l ->
            if (l.startsWith(CHOICE_MARK.trim())) return@flatMap listOf(Segment(null, l.removePrefix(CHOICE_MARK.trim()).trim(), choice = true))
            // popisek zvuku samotný nebo před replikou („[dog barking] Viktor: …“) = vlastní řádek (od 0.97.116)
            val lead = Captions.leading(l)
            if (lead != null && (lead.second.isEmpty() || splitSpeaker(lead.second).speaker != null))
                listOfNotNull(Segment(null, lead.first, caption = true), lead.second.takeIf { it.isNotEmpty() }?.let { splitSpeaker(it) })
            else listOf(splitSpeaker(l))
        }

    /**
     * Doznívající předchozí titulek: když jeden titulek mizí a hned naskočí další, čtečka na jednom
     * snímku přečte oba („Workers:Leavebroalone!“ nad „Workers: C'mon bro…“, GTA VI, 25. 11. 2026)
     * a slepily by se do jednoho. Řádek, který je jen [shown] (to, co už pruh ukazuje), se vynechá –
     * ale jen když zbude aspoň jeden jiný řádek. Mezery a jméno mluvčího se při porovnání nepočítají
     * (mizející text je čtený hůř a často bez mezer).
     */
    fun withoutFading(text: String, shown: String): String {
        if (shown.isEmpty() || '\n' !in text) return text
        val lines = text.split('\n')
        val old = (shown.split('\n') + shown).map { fadingKey(it) }.filter { it.length >= 4 }
        if (old.isEmpty()) return text
        val keep = lines.filter { l -> val k = fadingKey(l); k.length < 4 || old.none { similar(k, it, 0.85f) } }
        return if (keep.isEmpty() || keep.size == lines.size) text else keep.joinToString("\n")
    }

    private fun fadingKey(line: String): String =
        line.replaceFirst(Regex("^\\s*[^:\\n]{1,24}:"), "").lowercase().filter { it.isLetterOrDigit() }

    /** Podobnost dvou textů (tolerance chyb OCR mezi snímky). */
    fun similar(a: String, b: String, threshold: Float = 0.88f): Boolean {
        if (a == b) return true
        if (a.isEmpty() || b.isEmpty()) return false
        val x = a.lowercase()
        val y = b.lowercase()
        if (x == y) return true
        val maxLen = max(x.length, y.length)
        // rychlé odmítnutí: příliš rozdílná délka
        if (1f - kotlin.math.abs(x.length - y.length).toFloat() / maxLen < threshold) return false
        return 1f - levenshtein(x, y).toFloat() / maxLen >= threshold
    }

    fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
