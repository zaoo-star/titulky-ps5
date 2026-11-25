package cz.recaption.core

/**
 * Tmavé písmo na světlém pozadí – text z telefonu ve hře (GTA VI: bubliny zpráv, seznam SMS, „Fleeca, Laguna Lane“),
 * ne titulek (titulky jsou bílé). Dávka 3WMD-6WFC (22. 11. 2026): spodek displeje telefonu zasahoval do oblasti čtení
 * a 65 ze 74 „titulků“ byl text z telefonu. Od 0.97.94, jen u her, jejichž titulky jsou bílé (zatím GTA VI).
 */
object TextPolarity {
    /**
     * [gray] = jasy bodů v rámečku řádku (0–255). Titulek GTA má vždy bílé písmo (90. percentil 240+ i na snímcích
     * zmenšených do JPEG), text telefonu ne: světlé pozadí (medián ≥ 100), nic bílého (90. percentil < 220) a nic
     * výrazně světlejšího než pozadí (90. percentil nejvýš o 40 nad mediánem). Na snímku 0035 dávky: bublina
     * „Been busy handyman?“ 10/50/90 % = 126/142/146 (písmo v JPEG rozmazané), titulky 13/28/244 a 9/27/240.
     */
    fun darkOnLight(gray: IntArray): Boolean {
        if (gray.size < 20) return false
        val s = gray.sortedArray()
        val med = s[s.size / 2]
        val p90 = s[(s.size * 9) / 10]
        return med >= 100 && p90 < 220 && p90 - med <= 40
    }
}
