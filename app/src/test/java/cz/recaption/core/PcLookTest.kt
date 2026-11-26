package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vzhled pruhu z PC (vše se nastavuje na PC) – TV ho přečte ze zprávy `ukaz`. */
class PcLookTest {
    private val layout = ScreenLayout()

    @Test
    fun vzhledZPcSePrecte() {
        val json = TvLook.json(layout.withPosition(ScreenLayout.Position.ABOVE_TEXT), 70, TvLook.textPx(20, 0),
            0xFFFFE066.toInt(), true, 0.8f, 0.9f, 0.2f, 0.8f, null, pad = 2)
        val m = PcMessage.parse("{\"typ\":\"ukaz\",\"id\":3,\"text\":\"Ahoj\",\"vzhled\":$json}") as PcMessage.Show
        assertNotNull(m.look)
        val l = m.look!!
        assertEquals(70, l.opacity)
        assertEquals(20, l.textSp)
        assertEquals(0xFFFFE066.toInt(), l.color)
        assertTrue(l.bold)
        assertEquals(2, l.pad)
        assertEquals("tesne", l.position)
        assertEquals(ScreenLayout.Position.ABOVE_TEXT, l.layout(ScreenLayout()).position)
    }

    @Test
    fun bezVzhleduPlatiNastaveniTv() {
        val m = PcMessage.parse("{\"typ\":\"ukaz\",\"id\":1,\"text\":\"Ahoj\"}") as PcMessage.Show
        assertNull(m.look)
    }

    @Test
    fun predcitaniZPc() {
        val json = TvLook.json(layout, 60, TvLook.textPx(20, 0), 0xFFFFFFFF.toInt(), false, 0.8f, 0.9f, 0.2f, 0.8f, null)
        val sHlasem = json.dropLast(1) + ",\"hlas\":130,\"hlasJazyk\":\"cs\"}"
        val casti = "[{\"jmeno\":\"Lucia\",\"text\":\"Byl to jen instinkt.\"},{\"text\":\"Stiskni X\",\"pokyn\":true}]"
        val m = PcMessage.parse("{\"typ\":\"ukaz\",\"id\":2,\"text\":\"x\",\"casti\":$casti,\"vzhled\":$sHlasem}") as PcMessage.Show
        assertEquals(130, m.speechRate)
        assertEquals("cs", m.speechLang)
        // čte se jen replika – bez jména mluvčího a bez pokynu hry
        assertEquals("Byl to jen instinkt.", m.speechText)
        // vzhled se předčítáním nemění (pruh se kvůli němu nepřestaví)
        assertEquals((PcMessage.parse("{\"typ\":\"ukaz\",\"id\":2,\"text\":\"x\",\"vzhled\":$json}") as PcMessage.Show).look, m.look)
        val bez = PcMessage.parse("{\"typ\":\"ukaz\",\"id\":2,\"text\":\"x\",\"vzhled\":$json}") as PcMessage.Show
        assertEquals(0, bez.speechRate)
        val spatny = PcMessage.parse("{\"typ\":\"ukaz\",\"id\":2,\"text\":\"x\",\"vzhled\":${json.dropLast(1)},\"hlas\":100,\"hlasJazyk\":\"c\\\"s\"}}") as PcMessage.Show
        assertNull(spatny.speechLang)
    }
}
