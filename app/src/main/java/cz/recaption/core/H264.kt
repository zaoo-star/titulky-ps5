package cz.recaption.core

/**
 * Pár věcí o snímku H.264 (Annex B, start kódy 00 00 01 / 00 00 00 01) pro čtečku obrazu (od 0.97.102):
 * jaké jednotky NAL obsahuje – klíčový snímek (IDR, typ 5) a hlavička (SPS 7, PPS 8). Po výměně dekodéru se mu
 * smí dát jen hlavička a klíčový snímek, jinak dekóduje změny proti snímku, který nikdy neviděl (rozbitý obraz).
 */
object H264 {
    const val IDR = 5
    const val SPS = 7

    /** Typy jednotek NAL ve snímku (prochází se nejvýš [limit] bajtů – hlavička a začátek snímku stačí). */
    fun nalTypes(buf: ByteArray, limit: Int = 4096): Set<Int> {
        val out = HashSet<Int>()
        val end = minOf(buf.size, limit) - 3
        var i = 0
        while (i < end) {
            if (buf[i].toInt() == 0 && buf[i + 1].toInt() == 0 && buf[i + 2].toInt() == 1) {
                out += buf[i + 3].toInt() and 0x1F
                i += 3
            } else i++
        }
        return out
    }

    fun isKey(buf: ByteArray): Boolean = IDR in nalTypes(buf)
}
