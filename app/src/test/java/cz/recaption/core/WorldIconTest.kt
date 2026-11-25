package cz.recaption.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ikona tlačítka vlevo od pokynu ([WorldPrompts.iconLeft]) na nakresleném obrázku – rozměry podle dávek 7N4Y-RTQ8
 * a KJ7U-2FHD (1280×720, řádek 20 bodů): tlačítko, malá barevná značka, text.
 */
class WorldIconTest {
    private val w = 400
    private val h = 120
    private val top = 50
    private val bottom = 70          // řádek 20 bodů
    private val textLeft = 300

    /** Tmavý obrázek se světlými obdélníky [x0, x1) × [y0, y1). */
    private fun image(bg: Int, vararg boxes: IntArray): IntArray {
        val g = IntArray(w * h) { bg }
        for (b in boxes) for (y in b[2] until b[3]) for (x in b[0] until b[1]) g[y * w + x] = b[4]
        return g
    }
    private fun icon(right: Int, size: Int = 23) = intArrayOf(right - size, right, 60 - size / 2, 60 + size - size / 2, 250)
    private fun found(g: IntArray) = WorldPrompts.iconLeft(g, w, h, textLeft, top, bottom)

    @Test fun iconNextToText() {
        // tlačítko hned vedle textu (mezera půl řádku), bez značky
        assertTrue(found(image(30, icon(textLeft - 10))))
        // nic vlevo od textu
        assertFalse(found(image(30)))
    }

    @Test fun markerBetweenIconAndText() {
        // značka (tečka půl řádku) těsně u textu, za ní mezera a tlačítko – dřív se nepoznalo
        val dot = intArrayOf(textLeft - 12, textLeft - 4, 55, 65, 170)
        assertTrue("tecka", found(image(30, dot, icon(textLeft - 24))))
        // značka s jasem na hraně se rozpadne na kousky
        val a = intArrayOf(textLeft - 5, textLeft - 4, 57, 62, 165); val b = intArrayOf(textLeft - 12, textLeft - 7, 55, 65, 165)
        assertTrue("kousky", found(image(30, a, b, icon(textLeft - 24))))
        // se značkou stačí menší světlý střed tlačítka (0,85 řádku)
        assertTrue("mensi", found(image(30, dot, icon(textLeft - 24, 17))))
        // tmavá značka se nepozná vůbec: tlačítko dál od textu (1,3 řádku) se světlým středem 0,95 řádku
        assertTrue("tmava znacka", found(image(30, icon(textLeft - 27, 19))))
    }

    @Test fun notAButton() {
        val dot = intArrayOf(textLeft - 12, textLeft - 4, 55, 65, 170)
        // jen značka, žádné tlačítko
        assertFalse(found(image(30, dot)))
        // světlá plocha přes celé hledání (světlé pozadí scény) není samostatná ikona
        assertFalse(found(image(200)))
        // moc velký světlý útvar (cedule, okno)
        assertFalse(found(image(30, intArrayOf(textLeft - 80, textLeft - 10, 20, 100, 250))))
        // tlačítko mimo řádek (o řádek výš)
        assertFalse(found(image(30, intArrayOf(textLeft - 33, textLeft - 10, 20, 43, 250))))
        // drobné kousky daleko od textu nejsou značka
        assertFalse(found(image(30, intArrayOf(textLeft - 45, textLeft - 40, 55, 65, 200), icon(textLeft - 50))))
    }
}
