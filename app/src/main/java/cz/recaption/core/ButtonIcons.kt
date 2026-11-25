package cz.recaption.core

/**
 * Ikona tlačítka ovladače vpravo od položky menu vpravo dole ([CornerPrompts], GTA VI): bílý plný kotouč s tmavým
 * znakem × ○ □ △ (průměr ~1,37 % šířky obrazu, střed ~1,4 % šířky vpravo od textu, na výšce řádku). Čtečka textu ho
 * čte jako smetí („©“, „O“, „(9)“), proto se pozná z jasu výřezu, který služba pro čtení menu stejně má (nic navíc
 * za snímek, jen při čtení menu):
 *  1. v pásu vpravo od textu světlé body (práh mezi 98. percentilem a mediánem) → sloupce a řádky se světlými body
 *     (mezery do 0,2 průměru se přemostí – znak kotouč rozdělí) → kotouč = úsek široký a vysoký 0,6–1,5 průměru,
 *  2. kotouč → mřížka [N]×[N] (průměrný jas) → tmavé buňky uvnitř kruhu (bez okraje kotouče) = znak,
 *  3. obdélník znaku a 9 sond (rohy, středy stran, střed) – tmavá/světlá – porovnané se 4 vzory:
 *     × rohy a střed, ○ středy stran, □ rohy a středy stran, △ hrot nahoře a spodní strana.
 *     Sondy jsou v obdélníku znaku, takže nezáleží na tloušťce čar ani na rozlišení (720p i 1080p).
 * L1 / R1 / L2 / R2 (širší tvar s textem) čtečka přečte správně – ty bere [CornerPrompts] z textu ([shoulder]).
 * Podle snímků testera (dávka P2ZTVKQ5, 1280 × 720, i zvětšené na 1,5× a 2×): všech 123 jasných ikon poznaných, žádná
 * záměna; nejisté = null (ikona se neukáže). Testy `ButtonIconsTest` (soukromé repo, data ze snímků hry).
 */
object ButtonIcons {
    const val CROSS = "x"
    const val CIRCLE = "o"
    const val SQUARE = "s"
    const val TRIANGLE = "t"

    /** Průměr kotouče ve hře (zlomek šířky obrazu). */
    const val DIAMETER = 0.0137f
    /** Střed kotouče vpravo od pravého okraje textu (zlomek šířky obrazu). */
    const val OFFSET = 0.014f
    /** Mřížka kotouče. */
    const val N = 16

    /** Vzory sond v pořadí vlevo nahoře, nahoře, vpravo nahoře, vlevo, střed, vpravo, vlevo dole, dole, vpravo dole. */
    private val PATTERNS = linkedMapOf(
        CROSS to intArrayOf(1, 0, 1, 0, 1, 0, 1, 0, 1),
        CIRCLE to intArrayOf(0, 1, 0, 1, 0, 1, 0, 1, 0),
        SQUARE to intArrayOf(1, 1, 1, 1, 0, 1, 1, 1, 1),
        TRIANGLE to intArrayOf(0, 1, 0, 0, 0, 0, 1, 1, 1),
    )

    /**
     * Levá / pravá páčka (pohyb – písmeno v kolečku se šipkami, GTA VI „SCROLL“ v telefonu; od 1.00-rc21). Čtečka ji
     * nepřečte a z jasu se nepozná (není to × ○ □ △) – bere se ze známé položky ([CornerPrompts.DEFAULT_ICONS]).
     */
    const val LEFT_STICK = "LS"
    const val RIGHT_STICK = "RS"

    /** Ikony, které umí aplikace nakreslit (i v TV; páčky LS/RS od TV aplikace 1.5.42, starší je vynechá). */
    val KNOWN = setOf(CROSS, CIRCLE, SQUARE, TRIANGLE, "L1", "R1", "L2", "R2", "L3", "R3", LEFT_STICK, RIGHT_STICK)

    /** Páčka (LS/RS) – kreslí se jako kotouč s písmenem a šipkami, ne jako obdélník tlačítka L1/R2. */
    fun stick(icon: String): Boolean = icon == LEFT_STICK || icon == RIGHT_STICK

    private val SHOULDER = Regex("^([LlRr])([12lIi|])$")

    /** „L2“, „Rl“, „LI“ → „L2“, „R1“, „L1“; jinak null. */
    fun shoulder(token: String): String? {
        val m = SHOULDER.matchEntire(token.trim()) ?: return null
        val n = if (m.groupValues[2] == "2") "2" else "1"
        return m.groupValues[1].uppercase() + n
    }

    /**
     * Ikona vpravo od položky: [gray] = výřez [w]×[h] (jas v dolních 8 bitech, ARGB jde taky), posunutý o [x0]/[y0]
     * v obraze [fullW]×[fullH]; [right] = pravý okraj textu položky a [center] = střed řádku (zlomky obrazu).
     * null = žádná nebo nejistá.
     */
    fun find(gray: IntArray, w: Int, h: Int, x0: Int, y0: Int, fullW: Int, fullH: Int, right: Float, center: Float): String? {
        if (w <= 0 || h <= 0 || gray.size < w * h || fullW <= 0 || fullH <= 0) return null
        val d0 = DIAMETER * fullW
        if (d0 < 6f) return null
        val rx = right * fullW - x0
        val cy = center * fullH - y0
        val cands = discs(gray, w, h, (rx - 1.5f * d0).toInt(), (rx + 3f * d0).toInt(), (cy - 1.2f * d0).toInt(), (cy + 1.2f * d0).toInt(), d0)
        // zprava: ikona je nejvíc vpravo; kotouč vlevo od textu (písmeno) se nebere
        for (b in cands.sortedByDescending { it[0] }) {
            if ((b[0] + b[2]) / 2f < rx - 0.6f * d0) break
            classify(cells(gray, w, b))?.let { return it }
        }
        return null
    }

    /** Úseky, kde [cnt] ≥ [need]; mezery do [gap] se přemostí. Konec je bez [cnt] (vylučně). */
    internal fun runs(cnt: IntArray, need: Int, gap: Int): List<IntArray> {
        val out = ArrayList<IntArray>()
        var i = 0
        val n = cnt.size
        while (i < n) {
            if (cnt[i] < need) { i++; continue }
            val s = i
            var e = i + 1
            var j = i + 1
            while (j < n) {
                if (cnt[j] >= need) { e = j + 1; j++ } else if (j - e + 1 <= gap) j++ else break
            }
            out += intArrayOf(s, e)
            i = e
        }
        return out
    }

    /** Kandidáti na kotouč v obdélníku (souřadnice výřezu): čtverce [vlevo, nahoře, vpravo, dole]. */
    internal fun discs(gray: IntArray, w: Int, h: Int, xa0: Int, xb0: Int, ya0: Int, yb0: Int, d0: Float): List<IntArray> {
        val xa = maxOf(0, xa0); val ya = maxOf(0, ya0); val xb = minOf(w, xb0); val yb = minOf(h, yb0)
        if (xb - xa < 4 || yb - ya < 4) return emptyList()
        val rw = xb - xa; val rh = yb - ya
        val hist = IntArray(256)
        for (y in ya until yb) { val row = y * w; for (x in xa until xb) hist[gray[row + x] and 0xFF]++ }
        val total = rw * rh
        val hi = percentile(hist, total, 98)
        val med = percentile(hist, total, 50)
        if (hi < 90 || hi - med < 50) return emptyList()
        val t = (hi + med) / 2
        val bright = BooleanArray(total)
        val cols = IntArray(rw)
        for (y in 0 until rh) {
            val row = (ya + y) * w + xa
            for (x in 0 until rw) if ((gray[row + x] and 0xFF) >= t) { bright[y * rw + x] = true; cols[x]++ }
        }
        val gap = maxOf(1, (d0 * 0.2f).toInt())
        val out = ArrayList<IntArray>()
        for (c in runs(cols, 2, gap)) {
            val s = c[0]; val e = c[1]; val wd = e - s
            if (wd < 0.6f * d0 || wd > 1.5f * d0 || s == 0 || e == rw) continue
            val rows = IntArray(rh)
            for (y in 0 until rh) { var k = 0; for (x in s until e) if (bright[y * rw + x]) k++; rows[y] = k }
            val r = runs(rows, 2, gap).maxByOrNull { it[1] - it[0] } ?: continue
            val ht = r[1] - r[0]
            if (ht < 0.6f * d0 || ht > 1.5f * d0 || wd * 3 > ht * 4 || wd * 4 < ht * 3) continue
            if (r[0] == 0 || r[1] == rh) continue
            val side = (wd + ht) / 2
            val bx = xa + floorDiv(s + e - side, 2); val by = ya + floorDiv(r[0] + r[1] - side, 2)
            out += intArrayOf(bx, by, bx + side, by + side)
        }
        return out
    }

    private fun floorDiv(a: Int, b: Int) = Math.floorDiv(a, b)

    private fun percentile(hist: IntArray, total: Int, p: Int): Int {
        val k = total.toLong() * p / 100
        var acc = 0L
        for (v in 0..255) { acc += hist[v]; if (acc > k) return v }
        return 255
    }

    /** Průměrný jas kotouče [box] v mřížce [N]×[N] (řádek po řádku). */
    internal fun cells(gray: IntArray, w: Int, box: IntArray): IntArray {
        val bx = box[0]; val by = box[1]; val bw = box[2] - box[0]; val bh = box[3] - box[1]
        val out = IntArray(N * N)
        val h = gray.size / w
        for (j in 0 until N) {
            val ya = (by + j * bh / N).coerceIn(0, h - 1)
            val yb = maxOf(ya + 1, by + (j + 1) * bh / N).coerceAtMost(h)
            for (i in 0 until N) {
                val xa = (bx + i * bw / N).coerceIn(0, w - 1)
                val xb = maxOf(xa + 1, bx + (i + 1) * bw / N).coerceAtMost(w)
                var sum = 0
                for (y in ya until yb) for (x in xa until xb) sum += gray[y * w + x] and 0xFF
                out[j * N + i] = sum / ((yb - ya) * (xb - xa))
            }
        }
        return out
    }

    /** Znak v mřížce kotouče ([N]×[N] jasů, řádek po řádku) → ikona, nebo null (nejisté, plná skvrna, málo kontrastu). */
    fun classify(cells: IntArray): String? {
        if (cells.size != N * N) return null
        val sorted = cells.sortedArray()
        val hi = sorted[N * N * 9 / 10]; val lo = sorted[0]
        if (hi - lo < 60) return null
        val t = (hi + lo) / 2
        val dark = BooleanArray(N * N)
        var gx0 = N; var gx1 = -1; var gy0 = N; var gy1 = -1; var count = 0
        for (j in 0 until N) for (i in 0 until N) {
            // jen uvnitř kruhu o poloměru 0,86 kotouče (okraj kotouče a pozadí v rozích mřížky ne)
            val dx = 2 * i + 1 - N; val dy = 2 * j + 1 - N
            if ((dx * dx + dy * dy) * 10000 < 86 * 86 * N * N && cells[j * N + i] < t) {
                dark[j * N + i] = true; count++
                gx0 = minOf(gx0, i); gx1 = maxOf(gx1, i); gy0 = minOf(gy0, j); gy1 = maxOf(gy1, j)
            }
        }
        if (count < 4) return null
        // okrajový řádek / sloupec s pár body (šum pod znakem) se odřízne – nejvýš jeden na každé straně
        val rows = IntArray(gy1 - gy0 + 1); val cols = IntArray(gx1 - gx0 + 1)
        for (j in gy0..gy1) for (i in gx0..gx1) if (dark[j * N + i]) { rows[j - gy0]++; cols[i - gx0]++ }
        val mr = rows.max(); val mc = cols.max()
        val ty0 = gy0; val tx0 = gx0
        if (rows[0] * 3 < mr) gy0++
        if (rows[rows.size - 1] * 3 < mr) gy1--
        if (cols[0] * 3 < mc) gx0++
        if (cols[cols.size - 1] * 3 < mc) gx1--
        if (gy0 != ty0 || gx0 != tx0 || gy1 - gy0 + 1 != rows.size || gx1 - gx0 + 1 != cols.size) {
            count = 0
            for (j in gy0..gy1) for (i in gx0..gx1) if (dark[j * N + i]) count++
        }
        val gw = gx1 - gx0 + 1; val gh = gy1 - gy0 + 1
        if (gw <= 0 || gh <= 0) return null
        if (gw * 20 < N * 7 || gh * 20 < N * 7 || gw * 10 > gh * 15 || gh * 10 > gw * 15) return null
        // plná skvrna (písmeno O, stín) – znak ikony je jen obrys
        if (count * 100 > gw * gh * 65) return null
        val probes = DoubleArray(9)
        for (py in 0..2) for (px in 0..2) {
            val cx2 = 2 * gx0 + (gx1 - gx0) * px; val cy2 = 2 * gy0 + (gy1 - gy0) * py
            var n = 0; var d = 0
            for (j in gy0..gy1) for (i in gx0..gx1) {
                val ax = kotlin.math.abs(2 * i - cx2); val ay = kotlin.math.abs(2 * j - cy2)
                val ok = when {
                    // střed horní / dolní strany: jen krajní řádek znaku
                    px == 1 && py != 1 -> ax <= 2 && j == (if (py == 0) gy0 else gy1)
                    // střed levé / pravé strany: jen krajní sloupec znaku
                    py == 1 && px != 1 -> ay <= 2 && i == (if (px == 0) gx0 else gx1)
                    else -> ax <= 2 && ay <= 2
                }
                if (ok) { n++; if (dark[j * N + i]) d++ }
            }
            probes[py * 3 + px] = if (n == 0) 0.0 else d.toDouble() / n
        }
        val scores = PATTERNS.mapValues { (_, p) -> p.indices.sumOf { kotlin.math.abs(probes[it] - p[it]) } }
        val order = scores.entries.sortedBy { it.value }
        val best = order[0]; val second = order[1]
        return if (best.value <= 1.6 && second.value - best.value >= 1.5) best.key else null
    }
}
