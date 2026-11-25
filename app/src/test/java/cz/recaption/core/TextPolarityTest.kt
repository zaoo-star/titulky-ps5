package cz.recaption.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPolarityTest {
    /** [text] bodů písma o jasu [fg], zbytek pozadí [bg] (100 bodů celkem). */
    private fun line(bg: Int, fg: Int, text: Int = 25) = IntArray(100) { if (it < text) fg else bg }

    @Test fun phoneBubbleIsDarkOnLight() {
        assertTrue(TextPolarity.darkOnLight(line(bg = 192, fg = 55)))     // bublina zprávy
        assertTrue(TextPolarity.darkOnLight(line(bg = 160, fg = 60)))
        // snímek 0035 (JPEG, písmo rozmazané): 10/50/90 % = 126/142/146
        assertTrue(TextPolarity.darkOnLight(IntArray(100) { when { it < 10 -> 120; it < 50 -> 135; it < 90 -> 142; else -> 146 } }))
    }

    @Test fun subtitlesStay() {
        assertFalse(TextPolarity.darkOnLight(line(bg = 40, fg = 250)))    // bílé písmo na tmavém pozadí titulků
        assertFalse(TextPolarity.darkOnLight(line(bg = 200, fg = 252)))   // bílé písmo bez pozadí na světlé scéně
        // bílé písmo s tmavým obrysem na světlé scéně: obrys tmavý, ale bílé písmo zvedne 90. percentil
        val outlined = IntArray(100) { when { it < 15 -> 30; it < 40 -> 250; else -> 190 } }
        assertFalse(TextPolarity.darkOnLight(outlined))
    }

    @Test fun tooFewNothing() {
        assertFalse(TextPolarity.darkOnLight(IntArray(10) { if (it < 3) 50 else 200 }))
    }
}
