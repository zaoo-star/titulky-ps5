package cz.recaption.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Levná brána „telefon ve hře je otevřený“ (přání majitele po dávce CW8G-3SHU: při jízdě se jako telefon četly nápisy
 * nad silnicí – bez otevřeného telefonu se nemá velká část obrazu číst vůbec). Jen z jasu snímku, bez čtečky:
 *
 * 1. [icon] – vpravo dole je ovládání hry a jeho poslední řádek („BACK ⊙“ / „EXIT ⊙“) má ikonu tlačítka v kroužku. S telefonem
 *    v ruce tam je vždy (dávky 6RKU-RZW3 a JJAG-MBQB: 250 z 252 snímků s telefonem), při jízdě a chůzi ne.
 * 2. [quad] – displej: dvě dlouhé rovné skoro svislé hrany (zleva tmavá → světlá, zprava světlá → tmavá) ve vzdálenosti šířky
 *    telefonu, vyšší než širší, uvnitř světlejší než těsně vedle, v poloze z profilu hry ([GameProfile.phoneSpots]).
 *
 * Otevření chce obojí; pak stačí ovládání vpravo dole ([PhoneOpen]) – tmavá domovská obrazovka v noci ani fotka přes celý
 * displej ostré hrany nemají. Na dávce CW8G-3SHU (297 snímků bez telefonu) projdou obě podmínky jen na dvou snímcích.
 */
object PhoneGate {
    /** Mřížka jasu pro [quad]: [ROWS] vodorovných řezů širokým pásem telefonu ([PhoneZone.WIDE]), každý [COLS] bodů. */
    const val ROWS = 48
    const val COLS = 300

    /** Nejmenší skok jasu hrany displeje. */
    private const val EDGE = 24
    /** Hrana smí uhnout o tolik bodů mřížky (zaokrouhlení sklonu). */
    private const val TOL = 2
    /** Jaká část řezů v úseku hrany ji musí mít. */
    private const val FILL = 0.7f
    /** Nejmenší výška úseku s oběma hranami, šířka displeje (zlomky obrazu) a poměr výšky k šířce v bodech. */
    private const val MIN_H = 0.38f
    private const val MIN_W = 0.12f
    private const val MAX_W = 0.27f
    private const val MIN_ASPECT = 1.5f
    private const val MAX_ASPECT = 4.5f
    /** O kolik musí být vnitřek displeje světlejší než pás těsně vedle hran. */
    private const val MIN_LIGHTER = 10
    /** Největší rozdíl sklonu levé a pravé hrany (perspektiva) a o kolik smí displej přesahovat polohu z profilu. */
    private const val MAX_SLOPE_DIFF = 0.12f
    private const val SPOT_SLACK = 0.03f
    /** Zkoušené sklony hran (posun vodorovně na jednotku výšky, v bodech obrazu) – telefon v ruce při chůzi je natočený. */
    private val SLOPES = FloatArray(13) { (it - 6) * 0.04f }

    /**
     * Mřížka jasu [cols] × [rows] z obdélníku [box] (zlomky obrazu) snímku [fullW] × [fullH]: z každého řádku mřížky jeden
     * vodorovný řez (jeho středem), bod = průměr jasu přes šířku políčka. [luma] vrací jas bodu obrazu (0–255).
     */
    inline fun sample(fullW: Int, fullH: Int, box: FloatArray, cols: Int, rows: Int, luma: (x: Int, y: Int) -> Int): IntArray {
        val x0 = (box[0] * fullW).toInt().coerceIn(0, fullW - 1); val x1 = (box[2] * fullW).toInt().coerceIn(x0 + 1, fullW)
        val y0 = (box[1] * fullH).toInt().coerceIn(0, fullH - 1); val y1 = (box[3] * fullH).toInt().coerceIn(y0 + 1, fullH)
        val out = IntArray(cols * rows)
        for (r in 0 until rows) {
            val y = (y0 + (y1 - y0) * (2 * r + 1) / (2 * rows)).coerceAtMost(fullH - 1)
            for (c in 0 until cols) {
                val a = x0 + (x1 - x0) * c / cols; val b = maxOf(a + 1, x0 + (x1 - x0) * (c + 1) / cols)
                var sum = 0
                for (x in a until b) sum += luma(minOf(x, fullW - 1), y)
                out[r * cols + c] = sum / (b - a)
            }
        }
        return out
    }

    private class Edge(val x: Int, val from: Int, val to: Int, val slope: Float, val k: Float)

    /**
     * Displej telefonu v mřížce jasu [s] ([ROWS] × [COLS] hodnot 0–255 z pásu [PhoneZone.WIDE] snímku [fullW] × [fullH]):
     * obdélník (zlomky obrazu: vlevo, nahoře, vpravo, dole), nebo null. [spots] = polohy z profilu (prázdné = kdekoli v pásu).
     */
    fun quad(s: IntArray, fullW: Int, fullH: Int, spots: List<FloatArray> = emptyList()): FloatArray? {
        if (s.size != ROWS * COLS || fullW <= 0 || fullH <= 0) return null
        val z = PhoneZone.WIDE
        val cw = (z[2] - z[0]) * fullW / COLS          // bodů obrazu na sloupec mřížky
        val rh = (z[3] - z[1]) * fullH / ROWS          // bodů obrazu na řez
        val minRows = (MIN_H * fullH / rh).toInt()
        val wMin = MIN_W * fullW / cw; val wMax = MAX_W * fullW / cw
        // skok jasu přes dva body na každou stranu; hrana = místní vrchol skoku
        val e = IntArray(ROWS * COLS)
        for (r in 0 until ROWS) { val o = r * COLS; for (c in 2 until COLS - 2) e[o + c] = (s[o + c + 1] + s[o + c + 2] - s[o + c - 1] - s[o + c - 2]) / 2 }
        val posR = ArrayList<Int>(); val posC = ArrayList<Int>(); val negR = ArrayList<Int>(); val negC = ArrayList<Int>()
        for (r in 0 until ROWS) { val o = r * COLS; for (c in 2 until COLS - 2) {
            val v = e[o + c]
            if (v >= EDGE && v >= e[o + c - 1] && v >= e[o + c + 1]) { posR += r; posC += c }
            else if (v <= -EDGE && v <= e[o + c - 1] && v <= e[o + c + 1]) { negR += r; negC += c }
        } }
        if (posR.size < minRows * FILL || negR.size < minRows * FILL) return null
        val lefts = ArrayList<Edge>(); val rights = ArrayList<Edge>()
        val pad = 100; val n = COLS + 2 * pad
        val acc = LongArray(n); val wide = LongArray(n)
        for (slope in SLOPES) {
            val k = slope * rh / cw
            val shift = IntArray(ROWS) { (k * (it - ROWS / 2)).roundToInt() }
            for (side in 0..1) {
                val rs = if (side == 0) posR else negR; val cs = if (side == 0) posC else negC
                acc.fill(0L)
                for (i in rs.indices) { val x = cs[i] - shift[rs[i]] + pad; if (x in 0 until n) acc[x] = acc[x] or (1L shl rs[i]) }
                for (x in 0 until n) { var m = 0L; for (d in -TOL..TOL) if (x + d in 0 until n) m = m or acc[x + d]; wide[x] = m }
                for (x in 1 until n - 1) {
                    val cnt = java.lang.Long.bitCount(acc[x])
                    if (cnt == 0 || cnt < java.lang.Long.bitCount(acc[x - 1]) || cnt < java.lang.Long.bitCount(acc[x + 1])) continue
                    if (java.lang.Long.bitCount(wide[x]) < FILL * minRows) continue
                    val run = longestRun(wide[x], minRows) ?: continue
                    (if (side == 0) lefts else rights) += Edge(x - pad, run.first, run.second, slope, k)
                }
            }
        }
        var best: FloatArray? = null; var bestScore = 0f
        for (l in lefts) for (r in rights) {
            if (abs(l.slope - r.slope) > MAX_SLOPE_DIFF) continue
            val w = r.x - l.x
            if (w < wMin || w > wMax) continue
            val a = maxOf(l.from, r.from); val b = minOf(l.to, r.to)
            if (b - a + 1 < minRows) continue
            val aspect = (b - a + 1) * rh / (w * cw)
            if (aspect < MIN_ASPECT || aspect > MAX_ASPECT) continue
            var inSum = 0L; var inN = 0; var outSum = 0L; var outN = 0; var rows = 0
            for (row in a..b) {
                val xl = l.x + (l.k * (row - ROWS / 2)).roundToInt(); val xr = r.x + (r.k * (row - ROWS / 2)).roundToInt()
                if (xl - 6 < 0 || xr + 7 > COLS || xr - xl < 8) continue
                val o = row * COLS
                for (c in xl + 3 until xr - 2) { inSum += s[o + c]; inN++ }
                for (c in xl - 6 until xl - 2) { outSum += s[o + c]; outN++ }
                for (c in xr + 3 until xr + 7) { outSum += s[o + c]; outN++ }
                rows++
            }
            if (rows < minRows * FILL || inN == 0 || outN == 0) continue
            val lighter = inSum.toFloat() / inN - outSum.toFloat() / outN
            if (lighter < MIN_LIGHTER) continue
            val box = floatArrayOf(z[0] + (z[2] - z[0]) * l.x / COLS, z[1] + (z[3] - z[1]) * a / ROWS,
                z[0] + (z[2] - z[0]) * r.x / COLS, z[1] + (z[3] - z[1]) * (b + 1) / ROWS)
            if (spots.isNotEmpty() && spots.none { it[0] - SPOT_SLACK <= box[0] && box[2] <= it[2] + SPOT_SLACK }) continue
            val score = (b - a + 1) * lighter
            if (score > bestScore) { bestScore = score; best = box }
        }
        return best
    }

    /** Nejdelší úsek řezů (první, poslední) v masce [rows], který začíná i končí hranou, má aspoň [minRows] řezů a hranu v [FILL] z nich. */
    private fun longestRun(rows: Long, minRows: Int): Pair<Int, Int>? {
        val idx = (0 until ROWS).filter { rows and (1L shl it) != 0L }
        var best: Pair<Int, Int>? = null
        for (i in idx.indices) for (j in idx.indices.reversed()) {
            if (j <= i) break
            val span = idx[j] - idx[i] + 1
            if (span < minRows) break
            if (j - i + 1 >= FILL * span) { if (best == null || span > best.second - best.first + 1) best = idx[i] to idx[j]; break }
        }
        return best
    }

    /** Střed ikony posledního řádku ovládání vpravo dole (zlomky obrazu) – první políčko [CornerPrompts.GATE]. */
    val ICON_X = (CornerPrompts.GATE[0][0] + CornerPrompts.GATE[0][2]) / 2
    val ICON_Y = (CornerPrompts.GATE[0][1] + CornerPrompts.GATE[0][3]) / 2
    /** Polovina strany čtverce kolem ikony v bodech obrazu vysokého 720 (ikona má poloměr ~9). */
    const val ICON_HALF = 18

    /** Čtverec kolem ikony (zlomky obrazu) a jeho strana v bodech pro snímek [fullW] × [fullH] – výřez pro [icon]. */
    fun iconBox(fullW: Int, fullH: Int): Pair<FloatArray, Int> {
        val u = fullH / 720f
        val hx = ICON_HALF * u / fullW; val hy = ICON_HALF * u / fullH
        return floatArrayOf(ICON_X - hx, ICON_Y - hy, minOf(1f, ICON_X + hx), minOf(1f, ICON_Y + hy)) to (2 * ICON_HALF * u).roundToInt()
    }

    /**
     * Je ve čtverci jasu [p] ([n] × [n] bodů z [iconBox]) ikona tlačítka v kroužku? Světlý prstenec (13 ze 16 směrů) a tmavší
     * okolí těsně za ním (13 ze 16), střed se hledá v okolí pár bodů. Co je uvnitř kroužku (tečka, křížek, písmeno), nevadí.
     */
    fun icon(p: IntArray, n: Int): Boolean {
        if (n < 20 || p.size != n * n) return false
        val u = n / (2f * ICON_HALF)
        val ring = IntArray(16); val out = IntArray(16)
        val c = n / 2f
        for (dy in -3..3) for (dx in -4..4) {
            val x0 = c + dx * u; val y0 = c + dy * u
            var ok = true
            for (i in 0 until 16) {
                val a = i * Math.PI / 8
                val rx = (x0 + 8.2f * u * cos(a)).roundToInt(); val ry = (y0 + 8.2f * u * sin(a)).roundToInt()
                val ox = (x0 + 13.5f * u * cos(a)).roundToInt(); val oy = (y0 + 13.5f * u * sin(a)).roundToInt()
                if (rx !in 0 until n || ry !in 0 until n || ox !in 0 until n || oy !in 0 until n) { ok = false; break }
                ring[i] = p[ry * n + rx]; out[i] = p[oy * n + ox]
            }
            if (!ok) continue
            ring.sort(); out.sort()
            if (ring[3] >= ICON_BRIGHT && ring[3] - out[12] >= ICON_CONTRAST) return true
        }
        return false
    }
    private const val ICON_BRIGHT = 150
    private const val ICON_CONTRAST = 60

    /** Výřez pro čtení kolem displeje [box] z brány: do stran s okrajem, na výšku celý telefon (hrany bývají vidět jen zčásti). */
    fun zone(box: FloatArray): FloatArray = PhoneZone.around(floatArrayOf(box[0], minOf(box[1], 0.21f), box[2], maxOf(box[3], 0.84f)))

    /** Otisk výřezu [zone]: 6 × 6 políček jasu – beze změny se výřez znovu nečte (porovnání [PhoneScreen.changed]). */
    fun grid(zone: FloatArray): List<FloatArray> {
        val w = (zone[2] - zone[0]) / 6; val h = (zone[3] - zone[1]) / 6
        return (0 until 6).flatMap { r -> (0 until 6).map { c ->
            floatArrayOf(zone[0] + (c + 0.25f) * w, zone[1] + (r + 0.25f) * h, zone[0] + (c + 0.75f) * w, zone[1] + (r + 0.75f) * h) } }
    }
}

/**
 * Stav brány telefonu mezi kontrolami: otevře se, když je vpravo dole ovládání s kroužkem a v obraze displej ([PhoneGate]);
 * otevřená zůstává, dokud je ovládání vidět (jedno vynechání se promine – ikona problikne při výměně seznamu).
 */
class PhoneOpen {
    var open = false
        private set
    /** Displej z poslední kontroly, která ho našla (zlomky obrazu) – kolem něj se čte. */
    var box: FloatArray? = null
        private set
    /** Proč je zavřeno: „ovladani“ = vpravo dole není ovládání telefonu, „tvar“ = v obraze není světlý displej. */
    var reason = "ovladani"
        private set
    private var misses = 0

    /** Nová kontrola; [quad] se měří jen s [icon]. Vrací true, když se stav (otevřeno / důvod zavření) změnil. */
    fun update(icon: Boolean, quad: FloatArray?): Boolean {
        val was = open; val why = reason
        if (!icon) {
            if (!open || ++misses >= MISSES) { open = false; box = null; reason = "ovladani"; misses = 0 }
        } else {
            misses = 0
            if (quad != null) { open = true; box = quad }
            else if (!open) reason = "tvar"
        }
        return was != open || (!open && why != reason)
    }

    fun reset() { open = false; box = null; reason = "ovladani"; misses = 0 }

    companion object { const val MISSES = 2 }
}
