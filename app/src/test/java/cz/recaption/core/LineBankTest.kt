package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sbírání replik ze hry: jedna replika jednou, počty, rozhovory, plný seznam, uložení a odesílaný soubor. */
class LineBankTest {

    @Test fun sameLineCountedOnceWithVariants() {
        val b = LineBank()
        assertTrue(b.add("hra1", "en", "Lena", "Get in the car, Tomas!", 0))
        assertTrue(b.add("hra1", "en", "Lena", "Get in the car,  Tomas", 1000))
        assertTrue(b.add("hra1", "en", null, "get in the car tomas!", 2000))
        assertEquals(1, b.size)
        val e = b.entries.first()
        assertEquals(3, e.count)
        assertEquals("Lena", e.speaker)
        assertEquals(3, b.pendingCount)
    }

    @Test fun cleanerReadingReplacesNoisy() {
        val b = LineBank()
        b.add("hra1", "en", null, "We gOtta go now", 0)
        b.add("hra1", "en", null, "We gotta go now", 100)
        assertEquals("We gotta go now", b.entries.first().text)
    }

    @Test fun rejectsJunkAndLongText() {
        val b = LineBank()
        assertFalse(b.add("hra1", "en", null, "1", 0))
        assertFalse(b.add("hra1", "en", null, "x".repeat(LineBank.MAX_TEXT + 1), 0))
        assertNull(LineBank.clean("  ?! "))
        assertEquals("a b", LineBank.clean("a\tb"))
    }

    @Test fun conversationsByPauseAndGame() {
        val b = LineBank()
        b.add("hra1", "en", "A", "First line here", 0)
        b.add("hra1", "en", "B", "Second line here", 3000)
        b.add("hra1", "en", "A", "After a long pause", 3000 + LineBank.CONV_GAP_MS + 1)
        b.add("hra2", "en", "M", "Other game now", 3000 + LineBank.CONV_GAP_MS + 2)
        val l = b.entries.toList()
        assertEquals(l[0].conv, l[1].conv)
        assertEquals(0, l[0].order); assertEquals(1, l[1].order)
        assertTrue(l[2].conv > l[1].conv)
        assertEquals(0, l[2].order)
        assertTrue(l[3].conv > l[2].conv)
    }

    @Test fun sendOnlyNewReadings() {
        val b = LineBank()
        b.add("hra1", "en", null, "Hello there man", 0)
        b.add("hra1", "en", null, "Hello there man", 1)
        val list = b.pendingEntries()
        val counts = list.map { it.count }
        assertTrue(LineBank.upload(list).contains("\t2\t\tHello there man\t"))
        b.add("hra1", "en", null, "Hello there man", 2) // přečteno během odesílání
        b.markSent(list, counts)
        assertEquals(1, b.pendingCount)
        assertTrue(LineBank.upload(b.pendingEntries()).lines()[1].startsWith("hra1\ten\t1\t"))
    }

    @Test fun fullBankDropsOldestSentOnly() {
        val b = LineBank(maxLines = 2)
        b.add("g", "en", null, "line one", 0)
        b.add("g", "en", null, "line two", 1)
        assertFalse(b.add("g", "en", null, "line three", 2)) // nic odeslaného – nepřidá se
        val first = b.pendingEntries().take(1)
        b.markSent(first, first.map { it.count })
        assertTrue(b.add("g", "en", null, "line three", 3))
        assertEquals(listOf("line two", "line three"), b.entries.map { it.text })
    }

    @Test fun saveAndLoad() {
        val b = LineBank()
        b.add("hra1", "en", "Lena", "Get in the car", 0)
        b.add("hra1", "en", null, "Move it", 1)
        val list = b.pendingEntries(1)
        b.markSent(list, list.map { it.count })
        val c = LineBank()
        c.load(b.save())
        assertEquals(2, c.size)
        assertEquals(1, c.pendingCount)
        assertEquals("Lena", c.entries.first().speaker)
        assertEquals(b.nextConv, c.nextConv)
        assertFalse(c.dirty)
        val d = LineBank(); d.load("něco jiného\nx"); assertEquals(0, d.size)
        d.load(null); assertEquals(0, d.size)
    }

    @Test fun whenToSend() {
        val day = LineBank.DAY_MS
        assertFalse(LineBank.due(0, 0, 0, day * 3))
        assertTrue(LineBank.due(LineBank.MIN_BATCH, day * 3, 0, day * 3))
        assertFalse(LineBank.due(10, day * 3, 0, day * 3))
        assertTrue(LineBank.due(10, 0, 0, day * 3))
        assertFalse(LineBank.due(LineBank.MIN_BATCH, 0, day * 3 - 1000, day * 3)) // nedávno nepovedený pokus
        // server bez vlastních mezí replik: nejvýš 1× denně, i s plnou dávkou
        assertFalse(LineBank.due(LineBank.MIN_BATCH, day * 3 - 1000, 0, day * 3, minGap = day))
        assertTrue(LineBank.due(LineBank.MIN_BATCH, day * 2 - 1000, 0, day * 3, minGap = day))
    }

    // ---------------- druhy textu (od 1.00) ----------------

    @Test fun kindIsSeparateEntry() {
        val b = LineBank()
        assertTrue(b.add("hra1", "en", null, "Open the map", 0))
        assertTrue(b.add("hra1", "en", null, "Open the map", 100, LineBank.MENU))
        assertTrue(b.add("hra1", "en", null, "open the MAP!", 200, LineBank.MENU))
        assertEquals(2, b.size)
        val menu = b.entries.first { it.kind == LineBank.MENU }
        assertEquals(2, menu.count)
        assertEquals(0, menu.conv) // ostatní druhy nemají rozhovor
        assertEquals(LineBank.REPLIKA, b.entries.first().kind)
        assertFalse(b.add("hra1", "en", null, "Something else", 300, "neznamy"))
    }

    @Test fun otherKindsDoNotBreakConversation() {
        val b = LineBank()
        b.add("hra1", "en", "A", "First line here", 0)
        b.add("hra1", "en", null, "Reach the old pier", 1000, LineBank.UKOL)
        b.add("hra1", "en", "B", "Second line here", 2000)
        val l = b.entries.filter { it.kind == LineBank.REPLIKA }
        assertEquals(l[0].conv, l[1].conv)
        assertEquals(1, l[1].order)
    }

    @Test fun otherKindsHaveOwnLimit() {
        val b = LineBank(maxLines = 2, maxOther = 2)
        assertTrue(b.add("g", "en", null, "menu item one", 0, LineBank.MENU))
        assertTrue(b.add("g", "en", null, "menu item two", 1, LineBank.MENU))
        assertFalse(b.add("g", "en", null, "menu item three", 2, LineBank.MENU)) // plno, nic odeslané
        // repliky mají vlastní místo – menu je nevytlačí
        assertTrue(b.add("g", "en", null, "line one", 3))
        assertTrue(b.add("g", "en", null, "line two", 4))
        // odeslaná položka menu uvolní místo jen menu
        val sentMenu = b.pendingEntries().filter { it.kind == LineBank.MENU }.take(1)
        b.markSent(sentMenu, sentMenu.map { it.count })
        assertFalse(b.add("g", "en", null, "line three", 5))
        assertTrue(b.add("g", "en", null, "menu item three", 6, LineBank.MENU))
        assertEquals(listOf("line one", "line two"), b.entries.filter { it.kind == LineBank.REPLIKA }.map { it.text })
    }

    @Test fun longerTextForOtherKinds() {
        val b = LineBank()
        val long = "word ".repeat(80).trim() // ~400 znaků
        assertFalse(b.add("g", "en", null, long, 0))
        assertTrue(b.add("g", "en", null, long, 0, LineBank.TIP))
    }

    @Test fun repliesUploadedFirstAndKindIsLastColumn() {
        val b = LineBank()
        b.add("g", "en", null, "Pause menu entry", 0, LineBank.MENU)
        b.add("g", "en", "Lena", "Hold on tight", 1)
        val list = b.pendingEntries()
        assertEquals(LineBank.REPLIKA, list[0].kind)
        val rows = LineBank.upload(list).lines()
        assertTrue(rows[0].endsWith("\tdruh"))
        assertEquals(8, rows[1].split('\t').size)
        assertTrue(rows[1].endsWith("\treplika"))
        assertTrue(rows[2].endsWith("\tmenu"))
    }

    @Test fun oldSavedRowWithoutKindIsReply() {
        val old = LineBank.HEADER + "\t5\n" +
            "hra1\ten\t3\t1\t4\t0\tLena\tGet in the car\n" +
            "hra1\ten\t1\t0\t0\t0\t\tOpen the map\tmenu\n" +
            "hra1\ten\t1\t0\t0\t0\t\tBad kind\tneznamy\n"
        val b = LineBank()
        b.load(old)
        assertEquals(2, b.size)
        val e = b.entries.first()
        assertEquals(LineBank.REPLIKA, e.kind)
        assertEquals(3, e.count); assertEquals(4, e.conv)
        assertEquals(LineBank.MENU, b.entries.last().kind)
        // a zpátky: uloží se s druhem a načte stejně
        val c = LineBank(); c.load(b.save())
        assertEquals(b.entries.map { it.kind to it.text }, c.entries.map { it.kind to it.text })
        // stará replika splyne s nově přečtenou (stejný klíč jako před druhy)
        c.add("hra1", "en", null, "Get in the car!", 0)
        assertEquals(4, c.entries.first().count)
    }
}
