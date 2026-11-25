package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Titulky ke čtení v telefonu: pořadí, bez pokynů, oprava téže repliky, nejvýš MAX. */
class ReadFeedTest {
    @Test fun poradiBezPokynuAOprava() {
        ReadFeed.clear()
        ReadFeed.add("Alex: Where are you going?", listOf(Segment("Alex", "Kam jdeš?"), Segment(null, "Stisknout X", prompt = true)), emptyMap(), time = 1_000)
        ReadFeed.add("I told you already.", listOf(Segment(null, "Už jsem ti to řekl.")), emptyMap(), time = 4_000)
        // varianta čtení téhož titulku hned potom = přepsat, ne přidat
        ReadFeed.add("I told you already", listOf(Segment(null, "Už jsem ti to říkal.")), emptyMap(), time = 5_000)
        val all = ReadFeed.all()
        assertEquals(2, all.size)
        assertEquals(listOf(Segment("Alex", "Kam jdeš?")), all[0].segments)
        assertEquals("Už jsem ti to říkal.", all[1].segments.single().body)
        // jen pokyn = nic
        val v = ReadFeed.version
        ReadFeed.add("Press X", listOf(Segment(null, "Stisknout X", prompt = true)), emptyMap(), time = 30_000)
        assertEquals(v, ReadFeed.version)
    }

    @Test fun nejvyMax() {
        ReadFeed.clear()
        for (i in 0 until ReadFeed.MAX + 20) ReadFeed.add("Line number $i is here", listOf(Segment(null, "Řádek $i")), emptyMap(), time = i * 20_000L)
        val all = ReadFeed.all()
        assertEquals(ReadFeed.MAX, all.size)
        assertEquals("Řádek ${ReadFeed.MAX + 19}", all.last().segments.single().body)
    }
}
