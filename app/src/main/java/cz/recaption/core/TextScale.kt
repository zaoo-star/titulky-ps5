package cz.recaption.core

/**
 * Zvětšení výřezu před čtením. Čtečka textu potřebuje písmo vysoké aspoň kolem 20 pixelů;
 * drobné herní titulky v obrazu 720p mívají 11–16. Když jsou řádky v obraze malé, výřez se před
 * čtením zvětší; když jsou velké, čte se bez zvětšení (rychleji).
 */
class TextScale(private val maxPixels: Int = 950_000) {
    /** Aktuální zvětšení: 1, 1,5 nebo 2. */
    var scale = 1f
        private set
    private var wanted = 1f
    private var votes = 0

    /**
     * [lineHeights] = výšky přečtených řádků v pixelech PŮVODNÍHO obrazu (bez zvětšení),
     * [cropPixels] = počet pixelů výřezu. Vrací true, když se zvětšení změnilo.
     */
    fun update(lineHeights: List<Int>, cropPixels: Int): Boolean {
        val hs = lineHeights.filter { it >= 6 }.sorted()
        if (hs.isEmpty()) return false
        val median = hs[hs.size / 2]
        var target = when {
            median < 14 -> 2f
            median < 21 -> 1.5f
            median > 28 -> 1f
            else -> scale                       // mezi tím nic neměnit (ať to nekmitá)
        }
        // strop: zvětšený výřez nesmí být tak velký, aby čtení trvalo neúměrně dlouho
        while (target > 1f && cropPixels * target * target > maxPixels) target -= 0.5f
        if (target == scale) { votes = 0; return false }
        if (target == wanted) votes++ else { wanted = target; votes = 1 }
        if (votes < 2) return false             // až po dvou shodných čteních
        scale = target; votes = 0
        return true
    }
}

/** Velikost písma překladu podle anglického originálu. */
object MatchSize {
    /**
     * [lineHeight] = velikost písma originálu jako zlomek výšky obrazu (viz TextLogic.fontSizeOf),
     * [screenH] = výška obrazovky v pixelech.
     * Vrací velikost písma v pixelech.
     * Drží se v rozumných mezích a zaokrouhluje na sudé pixely, aby velikost mezi čteními neposkakovala.
     */
    fun textPx(lineHeight: Float, screenH: Int): Int {
        if (lineHeight <= 0f || screenH <= 0) return 0
        val px = lineHeight * screenH
        val lo = screenH * 0.022f
        val hi = screenH * 0.075f
        return (px.coerceIn(lo, hi) / 2f).toInt() * 2
    }
}

/**
 * Drží velikost písma překladu stálou. Hra má titulky pořád stejně velké, ale čtečka naměří každý
 * řádek trochu jinak. Bere se proto prostřední hodnota z posledních měření; na opravdu jinou
 * velikost (jiná scéna, jiné nastavení hry) se přejde, až když ji potvrdí dvě měření po sobě.
 */
class SizeKeeper {
    private val recent = ArrayList<Int>()
    private var odd = 0
    private var oddCount = 0

    /** [measured] = právě naměřená velikost písma v pixelech (0 = neznámá). Vrací velikost, která se má použít. */
    fun next(measured: Int): Int {
        if (measured <= 0) return current()
        val cur = current()
        if (cur > 0 && kotlin.math.abs(measured - cur) > cur * 0.3f) {
            // výrazně jiná velikost: nejdřív jen poznamenat, přejít až napodruhé
            if (oddCount > 0 && kotlin.math.abs(measured - odd) <= odd * 0.3f) {
                recent.clear(); recent.add(odd); recent.add(measured); oddCount = 0
            } else { odd = measured; oddCount = 1 }
            return current()
        }
        oddCount = 0
        recent.add(measured)
        if (recent.size > 9) recent.removeAt(0)
        return current()
    }

    private fun current(): Int = if (recent.isEmpty()) 0 else recent.sorted()[recent.size / 2]
}
