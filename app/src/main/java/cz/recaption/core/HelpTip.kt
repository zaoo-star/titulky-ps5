package cz.recaption.core

/**
 * Tip hry vlevo nahoře (GTA VI, dávka GVFDXYV8 #101, 1280×720): tmavý poloprůhledný obdélník asi x 0,017–0,24 × y 0,27–0,40,
 * bílé písmo ~2,2 % výšky (rámeček řádku čtečky ~2,5 %), řádky zarovnané vlevo (x ~0,026) s rozestupem ~2,9 %, jedno slovo
 * tučně a ikonka uprostřed řádku („Tip: …“ přes 4 řádky). Jiné nápovědy hry bývají na stejném místě i bez „Tip:“ („Press … to …“), box má 1–6 řádků.
 *
 * Překládá se s volbou Titulky → Hra „Tipy hry“ (výchozí vypnuto, jen GTA VI) do okna popisu menu hned pod anglickým
 * boxem, případně vpravo vedle něj ([place]). Bez Androidu, testy `HelpTipTest` (soukromé repo).
 */
data class Tip(
    val text: String,
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    /** Výška řádku (zlomek výšky obrazu, medián rámečků řádků). */
    val lineH: Float,
    val lines: Int,
)

object HelpTip {
    /** Pole čtení (zlomky obrazu). Box tipu začíná u levého okraje, takže stačí levá třetina. */
    const val ZONE_L = 0f
    const val ZONE_T = 0.03f
    const val ZONE_R = 0.34f
    const val ZONE_B = 0.55f

    /** Levý okraj textu tipu nejvýš tady (box je u levého okraje obrazu; menu hry zleva má text kolem 0,17). */
    const val MAX_LEFT = 0.10f
    /** Výška řádku (rámeček čtečky) ve zlomku výšky obrazu. */
    const val MIN_LINE = 0.016f
    const val MAX_LINE = 0.045f
    /** Levé okraje řádků jednoho boxu se smí lišit nejvýš o tolik (zlomek šířky). */
    const val LEFT_TOL = 0.015f
    /** Mezera mezi řádky boxu nejvýš tolik výšek řádku. */
    const val MAX_GAP = 0.8f
    /** Box překladu nejvýš do této šířky obrazu (pod originálem trochu širší než on, čeština je delší). */
    const val MAX_RIGHT = 0.36f
    /** Pod originálem jen když se box překladu vejde nad tuto výšku (níž je mapa a titulky). */
    const val BELOW_LIMIT = 0.74f

    /** Mřížka jasu pro poznání změny (3 × 4 políčka přes pole) – beze změny se znovu nečte. */
    val GRID: List<FloatArray> = (0 until 4).flatMap { r -> (0 until 3).map { c ->
        val x = 0.01f + c * 0.10f; val y = 0.06f + r * 0.12f
        floatArrayOf(x, y, x + 0.07f, y + 0.06f)
    } }

    private val WS = Regex("\\s+")
    /** Zbytek ikonky uprostřed řádku („😈“ čtené jako „©“, „@“, „®“) – slovo bez písmen a číslic. */
    private val ICON = Regex("^[^\\p{L}\\p{Nd}\\p{Nl}\\p{No}]{1,3}$")

    private fun letters(t: String) = t.count { it.isLetter() }

    /**
     * Text vypadá jako věta nápovědy: aspoň 3 slova s malými písmeny a aspoň jedno začínající malým písmenem
     * („Press X to aim“; ne verzálky HUDu, nápisy ve scéně ani položky menu „Photo Studio“ s velkými počátečními),
     * většina písmen malá, ne hlavně čísla / peníze.
     */
    fun sentence(t: String): Boolean {
        val words = t.trim().split(WS).filter { it.isNotEmpty() }
        val lower = words.count { w -> w.any { it.isLowerCase() } }
        val startLower = words.count { w -> w.firstOrNull { it.isLetter() }?.isLowerCase() == true }
        val n = letters(t)
        if (lower < 3 || startLower < 1 || n < 10) return false
        val upper = t.count { it.isUpperCase() }.toFloat() / n
        val digits = t.count { it.isDigit() }
        return upper < 0.4f && digits * 3 < n
    }

    /** Spojené řádky bez zbytků ikonky; „Tip::“ → „Tip:“, rozdělené slovo na konci řádku („con-“ + „tribute“) dohromady. */
    fun clean(rows: List<String>): String {
        val sb = StringBuilder()
        for (r in rows) {
            val s = r.trim().split(WS).filterNot { ICON.matches(it) }.joinToString(" ")
            if (s.isEmpty()) continue
            if (sb.isNotEmpty()) {
                if (sb.endsWith("-") && s.first().isLowerCase()) sb.setLength(sb.length - 1) else sb.append(' ')
            }
            sb.append(s)
        }
        return sb.toString().replace(Regex("^(\\p{L}+)::"), "$1:").replace(WS, " ").trim()
    }

    /** Řádek obrazu: části, které čtečka rozdělila (u ikonky uprostřed), spojené zleva doprava. */
    private class Row(val parts: MutableList<MenuLine>) {
        val left get() = parts.minOf { it.left }
        val right get() = parts.maxOf { it.right }
        val top get() = parts.minOf { it.top }
        val bottom get() = parts.maxOf { it.bottom }
        val h get() = parts.map { it.bottom - it.top }.sorted()[parts.size / 2]
        val text get() = parts.sortedBy { it.left }.joinToString(" ") { it.text.trim() }
    }

    /**
     * Tip z řádků OCR (pixely celého obrazu [fullW] × [fullH]): sloupec řádků zarovnaných vlevo u levého okraje
     * (±[LEFT_TOL]), mezery do [MAX_GAP] řádku, výška řádku [MIN_LINE]–[MAX_LINE], dohromady věta ([sentence]).
     * [light] = je písmo řádku světlé (bílé písmo tipu; null = nezkoumat). Víc kandidátů = ten s nejvíc slovy.
     */
    fun read(lines: List<MenuLine>, fullW: Int, fullH: Int, light: ((MenuLine) -> Boolean)? = null): Tip? {
        if (fullW <= 0 || fullH <= 0) return null
        val cand = lines.filter { l ->
            val h = (l.bottom - l.top).toFloat() / fullH
            val cx = (l.left + l.right) / 2f / fullW; val cy = (l.top + l.bottom) / 2f / fullH
            l.text.isNotBlank() && h in MIN_LINE..MAX_LINE && cx in ZONE_L..ZONE_R && cy in ZONE_T..ZONE_B
        }.sortedBy { it.left }
        // řádky obrazu: části se svisle překrývají aspoň z poloviny menší výšky
        val rows = ArrayList<Row>()
        for (l in cand) {
            val lh = l.bottom - l.top
            val row = rows.firstOrNull { r ->
                val ov = minOf(r.bottom, l.bottom) - maxOf(r.top, l.top)
                ov >= minOf(lh, r.bottom - r.top) * 0.5f && l.left >= r.right - lh
            }
            if (row != null) row.parts += l else rows += Row(mutableListOf(l))
        }
        rows.sortBy { it.top }
        // sloupce: navazující řádky se stejným levým okrajem a malou mezerou
        val tol = LEFT_TOL * fullW
        val groups = ArrayList<MutableList<Row>>()
        for (r in rows) {
            val g = groups.lastOrNull { g ->
                val last = g.last()
                val lh = maxOf(last.h, r.h).toFloat()
                val gap = (r.top - last.bottom).toFloat()
                kotlin.math.abs(r.left - g.first().left) <= tol && gap <= lh * MAX_GAP && gap >= -lh * 0.4f &&
                    r.h.toFloat() / last.h in 0.6f..1.6f
            }
            if (g != null) g += r else groups += mutableListOf(r)
        }
        var best: Tip? = null
        var bestWords = 0
        for (g in groups) {
            if (g.first().left > MAX_LEFT * fullW || g.size > 8) continue
            val text = clean(g.map { it.text })
            if (!sentence(text)) continue
            if (light != null && g.count { r -> r.parts.any(light) } * 2 < g.size) continue
            val words = text.split(WS).size
            if (words <= bestWords) continue
            val lh = g.map { it.h }.sorted()[g.size / 2].toFloat() / fullH
            bestWords = words
            best = Tip(text, g.minOf { it.left }.toFloat() / fullW, g.first().top.toFloat() / fullH,
                g.maxOf { it.right }.toFloat() / fullW, g.last().bottom.toFloat() / fullH, lh, g.size)
        }
        return best
    }

    /**
     * Kam překlad ([left, top, right, bottom], zlomky obrazu): hned pod anglický box (pod jeho tmavé pozadí), stejně vlevo,
     * o čtvrtinu širší (nejvýš [MAX_RIGHT]); když se pod něj nevejde (nad [BELOW_LIMIT]), vpravo vedle něj.
     * Výška = odhad (čeština je asi o třetinu delší), okno se stejně řídí textem.
     */
    fun place(t: Tip): FloatArray {
        val padX = 0.008f
        val width = (t.right - t.left).coerceAtLeast(0.12f)
        val pitch = if (t.lines > 1) (t.bottom - t.top) / t.lines else t.lineH * 1.2f
        val need = t.lines * pitch * 1.35f + t.lineH * 0.8f
        val below = t.bottom + t.lineH * 0.9f
        if (below + need <= BELOW_LIMIT) {
            val l = (t.left - padX).coerceAtLeast(0.003f)
            val r = maxOf(t.right + padX, minOf(MAX_RIGHT, t.left + width * 1.25f + padX))
            return floatArrayOf(l, below, r, (below + need).coerceAtMost(1f))
        }
        val l = (t.right + 0.02f).coerceAtMost(0.6f)
        val top = (t.top - t.lineH * 0.5f).coerceAtLeast(0f)
        return floatArrayOf(l, top, (l + width * 1.25f + padX).coerceAtMost(0.98f), (top + need).coerceAtMost(1f))
    }
}

/** Tip v čase: ukáže se po dvou podobných čteních (useknuté / neúplné čtení téhož = totéž), zmizí po 2 čteních bez něj. */
class TipTracker {
    private var shown: Tip? = null
    private var pending: Tip? = null
    private var misses = 0

    val current: Tip? get() = shown

    data class Result(val tip: Tip?, val changed: Boolean)

    private fun same(a: Tip?, b: Tip?) = a != null && b != null &&
        (a.text == b.text || TextLogic.similar(a.text, b.text, 0.85f) || a.text.startsWith(b.text) || b.text.startsWith(a.text))

    fun update(read: Tip?): Result {
        if (read == null) {
            pending = null
            if (shown == null) return Result(null, false)
            if (++misses < 2) return Result(shown, false)
            shown = null; misses = 0
            return Result(null, true)
        }
        misses = 0
        if (same(shown, read)) {
            // delší (celé) čtení téhož tipu nahradí useknuté
            val s = shown!!
            if (read.text.length > s.text.length && read.lines >= s.lines) { shown = read; return Result(read, true) }
            return Result(s, false)
        }
        if (!same(pending, read)) { pending = read; return Result(shown, false) }
        // ze dvou podobných čtení to delší
        val p = pending!!
        pending = null
        shown = if (p.text.length > read.text.length) p else read
        return Result(shown, true)
    }

    fun reset() { shown = null; pending = null; misses = 0 }
}
