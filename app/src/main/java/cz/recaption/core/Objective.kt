package cz.recaption.core

/**
 * Řádek s úkolem pod titulky (GTA: „Go to the safehouse.“ dole pod replikou, mezi nimi volný řádek) jako samostatná
 * věc vedle titulků (od 0.97.43, přání majitele): vyčlení ho [TextLogic.objectiveLine], ukazuje se ve vlastním pruhu
 * na místě anglického úkolu a drží se, dokud je úkol ve hře vidět – nezávisle na tom, kdy zmizí titulek.
 *
 * [update] dostane po každém čtení obrazu přečtený úkol (nebo null) a řekne, co udělat. Drobné chyby čtení
 * (jedno písmeno) se za nový úkol nepovažují; zmizí až po [misses] čteních bez něj (čtečka ho občas vynechá).
 */
class ObjectiveTracker(private val misses: Int = 2) {
    sealed class Change {
        /** Nový úkol – přeložit a ukázat. */
        data class Show(val text: String) : Change()
        /** Úkol zmizel – schovat. */
        object Hide : Change()
        /** Nic se nemění. */
        object Keep : Change()
    }

    /** Právě zobrazený úkol (anglicky), null = žádný. */
    var current: String? = null
        private set
    private var missed = 0

    /** Naučená horní hrana úkolu jako zlomek výšky obrazu (-1 = ještě nevíme). */
    var top = -1f
        private set

    fun update(read: String?, topFraction: Float = -1f): Change {
        val t = read?.let { TextLogic.clean(it) }?.takeIf { it.isNotBlank() }
        if (t == null) {
            if (current == null) return Change.Keep
            missed++
            if (missed < misses) return Change.Keep
            current = null; missed = 0
            return Change.Hide
        }
        missed = 0
        if (topFraction in 0f..1f) top = if (top < 0f) topFraction else top * 0.7f + topFraction * 0.3f
        val cur = current
        if (cur != null && same(cur, t)) return Change.Keep
        // useknuté čtení téhož úkolu („Get in the“ mezi „Get in the car“) – tester 21. 11. 2026: úkol se
        // kvůli tomu dvakrát znovu překládal a blikal
        if (cur != null && truncated(t, cur)) return Change.Keep
        current = t
        return Change.Show(t)
    }

    /** Konec čtení (zastavení, jiná hra, pauza): úkol pryč, místo se pamatuje dál. */
    fun reset() { current = null; missed = 0 }

    companion object {
        /** [part] je začátek úkolu [full] po celých slovech (čtečka konec řádku vynechala). */
        fun truncated(part: String, full: String): Boolean {
            val p = TextLogic.simplify(part); val f = TextLogic.simplify(full)
            return p.length >= 6 && f.length > p.length && f.startsWith("$p ")
        }

        /** Stejný úkol i s drobnou chybou čtení (nejvýš 1 znak na 12). */
        fun same(a: String, b: String): Boolean {
            val x = TextLogic.simplify(a); val y = TextLogic.simplify(b)
            if (x == y) return true
            val limit = maxOf(1, maxOf(x.length, y.length) / 12)
            return kotlin.math.abs(x.length - y.length) <= limit && TextLogic.levenshtein(x, y) <= limit
        }
    }
}

/** Kus textu úkolu s barvou ([color] null = výchozí barva pruhu) – do TV jako `casti`, na Androidu jako span. */
data class ObjectivePart(val text: String, val color: Int?)

/**
 * Barevná slova úkolu (GTA: „Go to the <žlutě>safehouse</žlutě>.“) přenesená do překladu (pokus, výchozí vypnuto).
 * Barva každého slova se přečte z obrazu ([NameColor]); sousední slova stejné barvy jsou jeden [Run]. Do překladu se
 * barva přenese opatrně: kus najdený v překladu doslova (jména a místa se nepřekládají), jinak jeho samostatný
 * překlad; nic nenalezeno = výchozí barva.
 */
object ObjectiveColors {
    /** Souvislý kus anglického úkolu v jedné barvě (z palety [SpeakerColors.PALETTE]). */
    data class Run(val text: String, val color: Int, /** Kus končí posledním slovem úkolu („Go to the <liquor store>“). */ val last: Boolean = false)

    /** Slova a jejich barvy (null = bílé / neznámé) → barevné kusy; barvy se sjednotí na nejbližší z palety. */
    fun runs(words: List<String>, colors: List<Int?>): List<Run> {
        val out = ArrayList<Run>()
        var cur: Int? = null
        val sb = StringBuilder()
        // poslední slovo úkolu (samotná interpunkce za ním se nepočítá)
        val end = words.indexOfLast { w -> w.any { it.isLetterOrDigit() } }
        var last = false
        fun flush() { cur?.let { c -> sb.toString().trim().takeIf { t -> t.any { it.isLetterOrDigit() } }?.let { out.add(Run(it, c, last)) } }; sb.setLength(0); last = false }
        for (i in words.indices) {
            val c = colors.getOrNull(i)?.let { NameColor.nearest(it) }
            if (c != cur) { flush(); cur = c }
            if (c != null) { sb.append(words[i]).append(' '); if (i == end) last = true }
        }
        flush()
        return out
    }

    /** Kus bez interpunkce na krajích („safehouse.“ → „safehouse“). */
    fun core(s: String): String = s.trim().trim { !it.isLetterOrDigit() }

    /**
     * Kde je [needle] v [text] (bez ohledu na velikost písmen, i bez interpunkce na krajích): jen od začátku slova;
     * konec na hranici slova, nebo se protáhne do konce slova o nejvýš 3 písmena (české koncovky: úkryt → úkrytu).
     * Pomíjí místa překrytá [taken]. null = nenalezeno. [tail] = kus je v originále na konci úkolu (viz [locateLoose]).
     */
    fun locate(text: String, needle: String, taken: List<IntRange> = emptyList(), tail: Boolean = false): IntRange? {
        for (n in listOf(needle.trim(), core(needle)).distinct()) {
            if (n.count { it.isLetterOrDigit() } < 2) continue
            var from = 0
            while (true) {
                val i = text.indexOf(n, from, ignoreCase = true)
                if (i < 0) break
                from = i + 1
                if (i > 0 && text[i - 1].isLetterOrDigit() && n[0].isLetterOrDigit()) continue
                var end = i + n.length
                var extra = 0
                while (end < text.length && text[end].isLetter() && n.last().isLetterOrDigit()) { end++; extra++ }
                if (extra > 3) continue
                val r = i until end
                if (taken.any { it.first <= r.last && r.first <= it.last }) continue
                return r
            }
        }
        return locateStem(text, needle, taken) ?: locateLoose(text, needle, taken, tail)
    }

    private val WORD = Regex("[\\p{L}\\p{N}]+(?:['’-][\\p{L}\\p{N}]+)*")

    /** Stejné slovo v jiném pádě: aspoň 4 písmena, společný začátek a za ním u obou nejvýš 3 písmena koncovky. */
    private fun sameStem(a: String, b: String): Boolean {
        if (a.length < 4 || b.length < 4) return false
        var p = 0
        while (p < a.length && p < b.length && a[p].lowercaseChar() == b[p].lowercaseChar()) p++
        return p >= 4 && a.length - p <= 3 && b.length - p <= 3
    }

    /**
     * Víceslovný kus, který překladač samostatně přeložil trochu jinak než ve větě („liquor store“ → „obchod
     * s alkoholem“ / „obchod s lihovinami“, ve větě „Přejdi do obchodu s alkoholem“ – dávka F6PV-UY39, růžové místo zůstalo
     * bílé): stačí aspoň polovina delších slov kusu ve stejném kmeni; místo = od prvního po poslední takové slovo
     * (nejvýš o slovo delší než kus). Kus z konce úkolu ([tail]) se protáhne do konce překladu (nejvýš o 3 slova) –
     * cíl bývá na konci i v překladu.
     */
    private fun locateLoose(text: String, needle: String, taken: List<IntRange>, tail: Boolean): IntRange? {
        val want = WORD.findAll(core(needle)).map { it.value }.toList()
        val content = want.filter { it.length >= 4 }
        if (content.isEmpty()) return null
        val words = WORD.findAll(text).toList()
        val hits = words.indices.filter { i -> content.any { sameStem(it, words[i].value) } }
        for (first in hits) {
            val inWin = hits.filter { it >= first && it - first <= want.size }
            if (inWin.size * 2 < content.size) continue
            var lastIdx = inWin.last()
            if (tail && words.size - 1 - lastIdx <= 3) lastIdx = words.size - 1
            val r = words[first].range.first..words[lastIdx].range.last
            if (taken.none { it.first <= r.last && r.first <= it.last }) return r
        }
        return null
    }

    /** Samohlásky, kterými končí základní tvar slova (jachta, loďka, auto, Port Vale…) a které se v pádech mění. */
    private const val ENDINGS = "aeiouyáéíóúůýě"

    /**
     * Kus v jiném pádě, než ve kterém ho vrátil překladač: „jachta“ (samostatný překlad kusu) v „Jeď k jachtě“
     * (záznam testera 21. 11. 2026 – barevné slovo zůstalo bílé). Každé slovo kusu od 4 písmen bez koncové
     * samohlásky, za ním nejvýš 3 písmena koncovky; slova jdou za sebou jen přes mezery.
     */
    private fun locateStem(text: String, needle: String, taken: List<IntRange>): IntRange? {
        val words = core(needle).split(Regex("\\s+")).filter { it.isNotEmpty() }
        // od 1.0.04 i slova končící souhláskou („obchod s alkoholem“ ve větě „do obchodu s alkoholem“)
        if (words.isEmpty() || words.none { it.length >= 4 }) return null
        val pattern = words.joinToString("\\s+") { w ->
            val stem = if (w.length >= 4 && w.last().lowercaseChar() in ENDINGS) w.dropLast(1) else w
            Regex.escape(stem) + "\\p{L}{0,3}"
        }
        val re = Regex("(?<![\\p{L}\\p{N}])$pattern(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        for (m in re.findAll(text)) {
            val r = m.range
            if (taken.none { it.first <= r.last && r.first <= it.last }) return r
        }
        return null
    }

    /** Text rozdělený na kusy podle obarvených míst ([spans]: rozsah → barva); bez míst null. */
    fun parts(text: String, spans: List<Pair<IntRange, Int>>): List<ObjectivePart>? {
        if (spans.isEmpty()) return null
        val out = ArrayList<ObjectivePart>()
        var pos = 0
        for ((r, c) in spans.sortedBy { it.first.first }) {
            if (r.first < pos || r.last >= text.length) continue
            if (r.first > pos) out.add(ObjectivePart(text.substring(pos, r.first), null))
            out.add(ObjectivePart(text.substring(r.first, r.last + 1), c))
            pos = r.last + 1
        }
        if (pos < text.length) out.add(ObjectivePart(text.substring(pos), null))
        return out.takeIf { p -> p.any { it.color != null } }
    }
}
