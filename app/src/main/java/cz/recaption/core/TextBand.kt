package cz.recaption.core

/**
 * Rychlejší čtení (pokus od 0.97.47, výchozí vypnuto – ⚙ → Experimentální): před rozpoznáním textu se z oblasti
 * čtení vyřízne jen pás, kde jsou řádky textu. Rozpoznání pak čte mnohem méně bodů. Podle PlayCaption (AGPL-3.0,
 * `pasSTextem` v RemotePlayService.kt) je to na televizi několikrát rychlejší.
 *
 * Text = světlé body (jas nad [BRIGHT]) s ostrou hranou vůči bodu o 2 vedle (rozdíl nad [EDGE]) – titulky jsou
 * světlé písmo s tmavým okrajem. Řádek obrazu s aspoň pár takovými body patří k textu. Nic nenalezeno = null
 * (čte se celá oblast jako dřív, takže se nic neztratí).
 */
object TextBand {
    const val BRIGHT = 150
    const val EDGE = 45

    /** Výsledek: obdélník v bodech oblasti [left, top, right, bottom) (right/bottom bez posledního bodu). */
    data class Band(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /** [luma] = jas bodů oblasti po řádcích (0–255), [w]×[h]. */
    fun find(luma: IntArray, w: Int, h: Int): Band? {
        if (w < 8 || h < 4 || luma.size < w * h) return null
        val rowCount = IntArray(h)
        var minX = w; var maxX = -1
        for (y in 0 until h) {
            val r = y * w
            var n = 0
            var x = 2
            while (x < w - 2) {
                val v = luma[r + x]
                if (v > BRIGHT && (v - luma[r + x - 2] > EDGE || v - luma[r + x + 2] > EDGE)) {
                    n++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                }
                x += 2
            }
            rowCount[y] = n
        }
        val need = maxOf(3, w / 400)
        var top = -1; var bottom = -1
        for (y in 0 until h) if (rowCount[y] >= need) { if (top < 0) top = y; bottom = y }
        if (top < 0 || maxX < minX) return null
        // okraj: 0,6 výšky nalezeného textu, aspoň 12 bodů (písmena přesahují hranu, jednoduché řádky jsou nízké)
        val pad = maxOf(12, ((bottom - top + 1) * 0.6f).toInt())
        val band = Band(
            (minX - pad).coerceAtLeast(0), (top - pad).coerceAtLeast(0),
            (maxX + pad + 1).coerceAtMost(w), (bottom + pad + 1).coerceAtMost(h),
        )
        // skoro celá oblast → výřez nic neušetří
        return if (band.width.toLong() * band.height > w.toLong() * h * 0.8) null else band
    }
}
