package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLookTest {
    private val white = 0xFFFFFFFF.toInt()

    @Test fun pruhlednyPruhNadTitulky() {
        val l = ScreenLayout().withPosition(ScreenLayout.Position.ABOVE)
        val j = TvLook.json(l, 40, 50, white, false, 0.85f, 0.9f, 0.3f, 0.7f, null)
        assertTrue(j, j.contains("\"pozadi\":40"))
        assertTrue(j, j.contains("\"poloha\":\"nad\""))
        assertTrue(j, j.contains("\"cteni\":[0.7400,0.9800]"))
        assertTrue(j, j.contains("\"text\":[0.8500,0.9000,0.3000,0.7000]"))
        assertTrue(j, j.contains("\"barva\":\"#FFFFFF\""))
        assertFalse(j, j.contains("tucne"))
        assertFalse(j, j.contains("original"))
    }

    @Test fun presTitulkyANahore() {
        assertTrue(TvLook.json(ScreenLayout(), 100, 50, white, true, -1f, -1f, -1f, -1f, null).let {
            it.contains("\"poloha\":\"pres\"") && it.contains("\"tucne\":true") && !it.contains("\"text\"")
        })
        val top = TvLook.json(ScreenLayout().withPosition(ScreenLayout.Position.TOP), 100, 50, white, false, -1f, -1f, -1f, -1f, null)
        assertTrue(top, top.contains("\"poloha\":\"stred\",\"stred\":0.0800"))
    }

    @Test fun zakrytCelouOblast() {
        val j = TvLook.json(ScreenLayout(), 100, 50, white, false, 0.8f, 0.9f, 0.2f, 0.8f, null, coverAll = true)
        assertTrue(j, j.contains("\"poloha\":\"pres\",\"kryt\":true"))
        // bez volby, bez titulku nebo v jiné poloze se pole neposílá
        assertFalse(TvLook.json(ScreenLayout(), 100, 50, white, false, 0.8f, 0.9f, 0.2f, 0.8f, null).contains("kryt"))
        assertFalse(TvLook.json(ScreenLayout(), 100, 50, white, false, -1f, -1f, -1f, -1f, null, coverAll = true).contains("kryt"))
        assertFalse(TvLook.json(ScreenLayout().withPosition(ScreenLayout.Position.ABOVE), 100, 50, white, false, 0.8f, 0.9f, 0.2f, 0.8f, null, coverAll = true).contains("kryt"))
    }

    @Test fun originalAPismo() {
        val j = TvLook.json(ScreenLayout(), 100, 50, white, false, 0.8f, 0.9f, -1f, -1f, "He said \"no\"\nok")
        assertTrue(j, j.contains("\"original\":\"He said \\\"no\\\"\\nok\""))   // řádek = mluvčí (od 0.97.20)
        assertTrue(j, j.contains("\"text\":[0.8000,0.9000]"))
        assertEquals(50, TvLook.textPx(22, 0))
        assertEquals(40, TvLook.textPx(22, 40))
        assertEquals(120, TvLook.textPx(80, 0))
    }

    @Test fun tesneNadTitulkem() {
        val l = ScreenLayout().withPosition(ScreenLayout.Position.ABOVE_TEXT)
        assertEquals(ScreenLayout.Position.ABOVE_TEXT, l.position)
        val j = TvLook.json(l, 100, 50, white, false, 0.85f, 0.9f, -1f, -1f, null)
        assertTrue(j, j.contains("\"poloha\":\"tesne\""))
        // uloží se a načte zpět; jiné polohy volbu zruší
        assertEquals(ScreenLayout.Position.ABOVE_TEXT, ScreenLayout.parse(l.serialize()).position)
        assertEquals(ScreenLayout.Position.ABOVE, ScreenLayout.parse(l.withPosition(ScreenLayout.Position.ABOVE).serialize()).position)
        assertEquals(ScreenLayout.Position.OVER, ScreenLayout.parse(ScreenLayout().serialize()).position)
        assertEquals(ScreenLayout.Position.CUSTOM, l.withBarCenter(0.5f).position)
    }

    @Test fun velikostOriginalu() {
        val j = TvLook.json(ScreenLayout(), 100, 50, white, false, -1f, -1f, -1f, -1f, "Hi", origPercent = 90)
        assertTrue(j, j.contains("\"pismoOrig\":90"))
        // výchozích 62 % se neposílá (starší TV mají 62 % samy)
        assertFalse(TvLook.json(ScreenLayout(), 100, 50, white, false, -1f, -1f, -1f, -1f, "Hi").contains("pismoOrig"))
        assertFalse(TvLook.json(ScreenLayout(), 100, 50, white, false, -1f, -1f, -1f, -1f, null, origPercent = 90).contains("pismoOrig"))
    }

    @Test fun zpravaUkazSVzhledem() {
        val m = TvSender.showMessage(3, "Ahoj", null, "{\"pozadi\":50}")
        assertEquals("{\"typ\":\"ukaz\",\"id\":3,\"text\":\"Ahoj\",\"vzhled\":{\"pozadi\":50}}", m)
    }
}
