package cz.recaption.core

/**
 * Popisky zvuků v titulcích (closed captions) v hranatých závorkách – např. „[laughs]“, „[dog barking]“,
 * „[clears throat]“, „[♪ upbeat country ♪]“ (dávka testera XPXK-QHZ6, od 0.97.116, přání majitele).
 *
 * Dřív: popisek na začátku řádku vypadal jako jméno mluvčího v závorkách („[Marek] …“) – v pruhu pak „Laughs: Víš!“,
 * samotný popisek byl „jen jméno bez textu“ a nepřekládal se, s ♪ ho vyřadily písně a popisek na posledním řádku
 * se ukázal jako pokyn hry (šedě a menším písmem). Popisek = obsah závorky začíná malým písmenem nebo notou
 * (jména mluvčích v závorkách jsou s velkým). Překládá se zvlášť (vlastní slovník, jinak překladač) a zůstává v závorkách.
 */
object Captions {
    /** Hudební značka, i jak ji čtečka čte („J“ místo ♪ na začátku / konci závorky). */
    private val NOTE = Regex("^[\\u2669\\u266A\\u266B\\u266C]|[\\u2669\\u266A\\u266B\\u266C]$")
    private val OCR_NOTE = Regex("^J\\s+|\\s+J$")
    private val BRACKET = Regex("\\[\\s*([^\\[\\]]{1,48}?)\\s*]")

    /** Obsah závorky je popisek zvuku (ne jméno mluvčího „[Marek]“). */
    fun isInner(inner: String): Boolean {
        val t = inner.trim()
        if (t.isEmpty()) return false
        if (NOTE.containsMatchIn(t) || OCR_NOTE.containsMatchIn(t)) return t.count { it.isLetter() } >= 2 || t.length <= 3
        val first = t.firstOrNull { it.isLetter() } ?: return false
        // žádné slovo s velkým písmenem („[old Kessler]“ je jméno s chybně přečteným prvním písmenem)
        return first.isLowerCase() && t.count { it.isLetter() } >= 3 && t.split(' ').none { w -> w.firstOrNull()?.isUpperCase() == true }
    }

    /** Celý řádek je jeden popisek („[dog barking]“). */
    fun isLine(text: String): Boolean {
        val t = text.trim()
        val m = BRACKET.matchEntire(t) ?: return false
        return isInner(m.groupValues[1])
    }

    /** Popisek na začátku řádku a zbytek („[dog barking] Viktor: Look…“ → „[dog barking]“, „Viktor: Look…“); null = nezačíná popiskem. */
    fun leading(text: String): Pair<String, String>? {
        val t = text.trim()
        val m = BRACKET.find(t) ?: return null
        if (m.range.first != 0 || !isInner(m.groupValues[1])) return null
        return t.substring(0, m.range.last + 1) to t.substring(m.range.last + 1).trim()
    }

    /** Text rozdělený na kusy textu a popisků (true = popisek); bez popisku jeden kus. */
    fun pieces(text: String): List<Pair<String, Boolean>> {
        val out = ArrayList<Pair<String, Boolean>>()
        var from = 0
        for (m in BRACKET.findAll(text)) {
            if (!isInner(m.groupValues[1])) continue
            text.substring(from, m.range.first).trim().takeIf { it.isNotEmpty() }?.let { out += it to false }
            out += m.value to true
            from = m.range.last + 1
        }
        text.substring(from).trim().takeIf { it.isNotEmpty() }?.let { out += it to false }
        return out
    }

    /** Obsah popisku bez závorek a not („[♪ upbeat country ♪]“ → „upbeat country“) a jestli je to hudba. */
    fun inner(caption: String): Pair<String, Boolean> {
        var t = caption.trim().removePrefix("[").removeSuffix("]").trim()
        var music = false
        while (true) {
            val n = NOTE.find(t) ?: OCR_NOTE.find(t) ?: break
            music = true
            t = t.removeRange(n.range).trim()
        }
        return t to music
    }

    /** Hotový popisek z překladu obsahu: malé písmeno na začátku, závorky, u hudby noty. */
    fun wrap(translated: String, music: Boolean): String {
        val t = translated.trim().trimEnd('.', '!').let { s -> if (!keepCase(s) && s.length > 1 && s[1].isLowerCase()) s.replaceFirstChar { it.lowercaseChar() } else s }
        return if (music) "[♪ $t ♪]" else "[$t]"
    }

    /** Popisek z vlastního slovníku s velkým písmenem (němčina „Schuss“) – malé písmeno se mu nedává. */
    private fun keepCase(s: String): Boolean =
        s.firstOrNull()?.isUpperCase() == true && GameProfiles.dict.captions.values.any { s in it.values }

    /** Vlastní překlad běžného popisku (slovník ze seznamu her, [GameDictionaries.captions]), jinak null (přeloží překladač). */
    fun word(inner: String, lang: Lang): String? = GameProfiles.dict.captions[lang.code]?.get(inner.trim().lowercase())
}
