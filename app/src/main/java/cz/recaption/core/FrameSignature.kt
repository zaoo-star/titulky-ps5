package cz.recaption.core

import kotlin.math.abs

/**
 * Otisk oblasti s titulky: mřížka [COLS]×[ROWS] buněk, v každé součet jasu VŠECH pixelů buňky.
 * Slouží k přeskočení čtení, když se obraz od minula nezměnil (pauza, nehybná scéna).
 *
 * Počítá se ze všech pixelů, ne ze vzorků: drobný titulek zabere jen pár procent plochy a vzorkování
 * by jeho objevení nebo změnu snadno minulo – aplikace by pak nový titulek vůbec nečetla.
 */
object FrameSignature {
    const val COLS = 64
    const val ROWS = 16
    const val SIZE = COLS * ROWS

    /**
     * Převede výřez jasové složky ([y], řádek má [stride] bajtů) na šedé pixely [argb] (w×h)
     * a zároveň z něj spočítá otisk do [sig] (délka [SIZE], musí být vynulovaný).
     */
    fun cropGray(y: ByteArray, stride: Int, x0: Int, y0: Int, w: Int, h: Int, argb: IntArray, sig: IntArray) {
        var o = 0
        for (row in 0 until h) {
            var i = (y0 + row) * stride + x0
            val base = row * ROWS / h * COLS
            // sloupec buňky se mění jen jednou za w/COLS pixelů – počítá se po úsecích, ne pro každý pixel
            var cell = 0
            var nextEdge = if (COLS > 1) (w + COLS - 1) / COLS else w
            var acc = 0
            for (x in 0 until w) {
                if (x == nextEdge) {
                    sig[base + cell] += acc; acc = 0
                    cell = x * COLS / w
                    nextEdge = ((cell + 1) * w + COLS - 1) / COLS
                }
                val v = y[i++].toInt() and 0xFF
                argb[o++] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                acc += v
            }
            sig[base + cell] += acc
        }
    }

    /**
     * Změnil se obraz? Ano, když se v některé buňce průměrný jas posunul aspoň o [level] stupňů
     * (z 255). Objevení, zmizení i výměna titulku posune jas buňky o desítky stupňů; šum
     * komprimovaného videa na nehybné scéně zůstává pod hranicí.
     * [cellPixels] = kolik pixelů připadá na jednu buňku.
     */
    fun changed(a: IntArray?, b: IntArray?, cellPixels: Int, level: Int = 3): Boolean {
        if (a == null || b == null || a.size != b.size || a.isEmpty()) return true
        val limit = level.toLong() * maxOf(1, cellPixels)
        for (i in a.indices) if (abs(a[i] - b[i]).toLong() >= limit) return true
        return false
    }
}
