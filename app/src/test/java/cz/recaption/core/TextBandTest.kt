package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextBandTest {
    /** Tmavý obraz w×h s „textem“: svislé bílé čárky po 6 bodech v obdélníku. */
    private fun frame(w: Int, h: Int, l: Int, t: Int, r: Int, b: Int): IntArray {
        val a = IntArray(w * h) { 20 }
        for (y in t until b) { var x = l; while (x < r) { a[y * w + x] = 240; a[y * w + x + 1] = 240; x += 6 } }
        return a
    }

    @Test fun pasKolemTextu() {
        val band = TextBand.find(frame(1920, 360, 600, 250, 1300, 290), 1920, 360)!!
        assertTrue(band.top in 200..250 && band.bottom in 290..340)
        assertTrue(band.left in 550..600 && band.right in 1300..1360)
        assertTrue(band.width * band.height < 1920 * 360 / 5)
    }

    @Test fun bezTextuNic() {
        assertNull(TextBand.find(IntArray(1920 * 360) { 20 }, 1920, 360))
        // světlá plocha bez hran (nebe) není text
        assertNull(TextBand.find(IntArray(1920 * 360) { 230 }, 1920, 360))
    }

    @Test fun textPresCelouOblastNeusetri() {
        assertNull(TextBand.find(frame(400, 100, 0, 0, 398, 100), 400, 100))
    }

    @Test fun dvaRadky() {
        val a = frame(1920, 360, 700, 200, 1200, 230)
        val b = frame(1920, 360, 650, 250, 1250, 280)
        val both = IntArray(a.size) { maxOf(a[it], b[it]) }
        val band = TextBand.find(both, 1920, 360)!!
        assertTrue(band.top <= 200 && band.bottom >= 280)
        assertEquals(true, band.left <= 650 && band.right >= 1250)
    }
}
