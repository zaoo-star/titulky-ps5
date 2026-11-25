package cz.recaption.core

import kotlin.math.abs
import kotlin.math.ceil

/**
 * Stálý překlad téhož řádku (od 0.97.83, podle dávek snímků testera z GTA VI 21. 11. 2026 – „skákající překlad“).
 *
 * Čtečka čte stejný anglický titulek v každém snímku trochu jinak („Ben: So good tO see you“, „IBen: Let's
 * go…“, „Let's pop somne bottles!“). Každá taková varianta byla pro překladač nový text a dostala jiný překlad
 * (DeepL i offline), takže pruh se stejným titulkem v originále měnil češtinu. Tady jsou pomocné testy podobnosti
 * a [RecentLines] – paměť naposledy ukázaných řádků s jejich překladem.
 */
object StableLines {
    private val WORD = Regex("[\\p{L}\\p{Nd}\\p{Nl}\\p{No}]+(?:['’][\\p{L}]+)?")

    fun words(s: String): List<String> = WORD.findAll(s.lowercase()).map { it.value }.toList()

    /** Stejné slovo i s drobnou chybou čtení (jedno písmeno vedle u slov od čtyř písmen). */
    fun sameWord(a: String, b: String) =
        a == b || (a.length >= 4 && b.length >= 4 && abs(a.length - b.length) <= 1 && TextLogic.levenshtein(a, b) <= 1)

    // znaky typické pro zkomolené čtení: malé písmeno hned před velkým („tO“, „HartleY“, „lIn“, „oLena“),
    private val CASE_FLIP = Regex("\\p{Ll}\\p{Lu}")
    // dvě velká a pak malé („IBen“ – svislá čára přilepená ke jménu, „VWe“, „LOoks“),
    private val CAPS_THEN_LOWER = Regex("^\\p{Lu}{2}\\p{Ll}")
    // číslice uprostřed slova („A0ore“, „to0“),
    private val DIGIT_IN_WORD = Regex("\\p{L}\\d|\\d\\p{L}")
    // mluvčí s malým písmenem na začátku („ueia: Hi!“ – useknuté jméno při prolínání titulků)
    private val LOWER_SPEAKER = Regex("^\\p{Ll}\\p{L}*:")
    // výjimky: McDonald, MacBook, iPhone; zkratky v množném čísle (TVs, DJs); řadové číslovky (2nd, 90s)
    private val OK_FLIP = Regex("^(?:Mc|Mac|i)\\p{Lu}\\p{Ll}+(?:'s)?$")
    private val OK_PLURAL = Regex("^\\p{Lu}{2,}s$")
    private val OK_NUMBER = Regex("^\\d+(?:st|nd|rd|th|s)$")

    /**
     * Kolik slov vypadá zkomoleně (chyba čtení, ne skutečné slovo). 0 = čisté čtení. Slouží jen k porovnání
     * variant téhož titulku a k tomu, aby se zkomolené první čtení neukázalo bez potvrzení dalším čtením.
     */
    fun noise(text: String): Int {
        var n = if (LOWER_SPEAKER.containsMatchIn(text.trim())) 1 else 0
        for (raw in text.split(' ', '\n')) {
            val t = raw.trim { !it.isLetterOrDigit() }
            if (t.length < 2) continue
            val bad = (CASE_FLIP.containsMatchIn(t) && !OK_FLIP.matches(t)) ||
                (CAPS_THEN_LOWER.containsMatchIn(t) && !OK_PLURAL.matches(t)) ||
                (DIGIT_IN_WORD.containsMatchIn(t) && !OK_NUMBER.matches(t))
            if (bad) n++
        }
        return n
    }

    /** Tentýž řádek přečtený jinak (drobné chyby čtení, velikost písmen, interpunkce)? Přísné – jde o shodný překlad. */
    fun variant(a: String, b: String): Boolean {
        val x = TextLogic.simplify(a); val y = TextLogic.simplify(b)
        if (x.isEmpty() || y.isEmpty()) return false
        return TextLogic.similar(x, y)
    }

    /**
     * Je [b] tentýž řádek jako [a], jen přečtený jinak nebo celý (aspoň [ratio] slov kratšího je i v delším)?
     * Volnější než [variant]: „Ben iab Aoe! Look who it is!“ ~ „Ben: Hey, hey! Look who it is!“.
     * Krátký řádek (do dvou slov) musí sedět celý.
     */
    fun sameLine(a: String, b: String, ratio: Float = 0.6f): Boolean {
        val x = words(a); val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return false
        val (s, l) = if (x.size <= y.size) x to y else y to x
        val used = BooleanArray(l.size)
        var hit = 0
        for (w in s) {
            val i = l.indices.firstOrNull { !used[it] && sameWord(w, l[it]) } ?: continue
            used[i] = true; hit++
        }
        val need = if (s.size <= 2) s.size else ceil(ratio * s.size).toInt()
        return hit >= need
    }

    /**
     * Nahrazuje titulek [new] titulek [old] (čekající ve frontě nebo právě v pruhu)? Ano, když je každá replika
     * z [old] (po řadě) i v [new] – tentýž titulek přečtený lépe, nebo doplněný o další repliku.
     */
    fun supersedes(old: String, new: String): Boolean {
        val o = TextLogic.segments(old).map { line(it) }
        val n = TextLogic.segments(new).map { line(it) }
        if (o.isEmpty() || n.isEmpty() || n.size < o.size) return false
        var j = 0
        for (s in o) {
            while (j < n.size && !sameLine(s, n[j])) j++
            if (j == n.size) return false
            j++
        }
        return true
    }

    fun line(s: Segment): String = (s.speaker?.let { "$it: " } ?: "") + s.body
}

/**
 * Paměť naposledy ukázaných řádků (anglicky + překlad) – každá replika má v pruhu pořád stejný překlad:
 *
 * - [stabilize]: řádek, který se v posledních [keepMs] už ukázal (i jako jiná varianta čtení), dostane překlad,
 *   který se ukázal tehdy. Novější varianta ho nahradí, jen když je čistší ([StableLines.noise]) nebo když ji
 *   čtečka potvrdila jako opravu ([preferNew]). Platí i pro repliky v bloku několika replik pod sebou (rozhovory
 *   lidí kolem v GTA VI) – blok se mění, ale už přeložená replika se nepřekládá znovu jinak.
 * - [fresh]: které repliky z bloku ještě v tomto rozhovoru nebyly (pokus „Jen nové repliky“, výchozí vypnuto).
 *   Rozhovor končí zmizením titulku na déle než [gapMs] ([endConversation]); replika se zapomene po [keepMs].
 *
 * Čas se předává zvenku (testy).
 */
class RecentLines(private val keepMs: Long = 60_000, private val max: Int = 60, private val gapMs: Long = 1500) {
    private class Line(var en: String, var tr: Segment, var noise: Int, var at: Long)

    private val lines = ArrayList<Line>()
    /** Repliky právě probíhajícího rozhovoru (anglicky) – pro [fresh]. */
    private val conversation = ArrayList<Pair<String, Long>>()
    /** Kdy titulek zmizel (0 = nezmizel); rozhovor končí, až se do [gapMs] nevrátí. */
    private var endedAt = 0L

    /**
     * Překlad k zobrazení: [en] = anglické části titulku, [tr] = jejich překlad (stejné pořadí a počet).
     * Vrací [tr] s překlady už ukázaných řádků z paměti a zapamatuje si nové.
     */
    @Synchronized fun stabilize(en: List<Segment>, tr: List<Segment>, now: Long, preferNew: Boolean = false): List<Segment> {
        if (en.size != tr.size) return tr
        prune(now)
        val out = tr.toMutableList()
        val used = HashSet<Line>()
        for (i in en.indices) {
            val e = StableLines.line(en[i])
            val noise = StableLines.noise(e)
            val old = lines.firstOrNull { it !in used && StableLines.variant(it.en, e) }
            if (old == null) {
                val l = Line(e, tr[i], noise, now); lines.add(l); used.add(l)
                continue
            }
            used.add(old)
            old.at = now
            if (old.en == e) { out[i] = tr[i].copy(body = old.tr.body); continue }
            if (preferNew || noise < old.noise) {
                old.en = e; old.tr = tr[i]; old.noise = noise
            } else out[i] = tr[i].copy(body = old.tr.body)
        }
        while (lines.size > max) lines.removeAt(0)
        return out
    }

    /** Indexy částí [en], které v tomto rozhovoru ještě v pruhu nebyly; zároveň si je zapamatuje jako ukázané. */
    @Synchronized fun fresh(en: List<Segment>, now: Long): List<Int> {
        if (endedAt > 0) { if (now - endedAt > gapMs) conversation.clear(); endedAt = 0 }
        conversation.removeAll { now - it.second > keepMs }
        val used = BooleanArray(conversation.size)
        val out = ArrayList<Int>()
        for (i in en.indices) {
            val e = StableLines.line(en[i])
            val k = conversation.indices.firstOrNull { !used[it] && seen(conversation[it].first, e) }
            if (k == null) out.add(i) else { used[k] = true; conversation[k] = conversation[k].first to now }
        }
        for (i in out) conversation.add(StableLines.line(en[i]) to now)
        while (conversation.size > max) conversation.removeAt(0)
        return out
    }

    /** Už ukázaná replika: varianta čtení, nebo ochuzené čtení (čtečka vynechala řádek zalomené repliky). */
    private fun seen(old: String, new: String) =
        StableLines.variant(old, new) || (TextLogic.isSubsetRead(new, old) && StableLines.words(new).size >= 3)

    /**
     * Titulek zmizel – když se do [gapMs] nevrátí (jen krátký výpadek čtení), jsou příště všechny repliky zase nové.
     * Překlady v paměti zůstávají.
     */
    @Synchronized fun endConversation(now: Long) { endedAt = now }

    @Synchronized fun clear() { lines.clear(); conversation.clear(); endedAt = 0 }

    private fun prune(now: Long) { lines.removeAll { now - it.at > keepMs } }
}
