package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverAreaTest {
    private val e = 0.0001f
    private val read = Zone(0.08f, 0.70f, 0.92f, 0.98f)
    /** Bez okraje – čísla v testech jsou přímo hrany titulků. */
    private fun area() = CoverArea(padY = 0f, padX = 0f)

    @Test fun dvouradkovyTitulekZustaneZakryty() {
        val c = area()
        c.next(0.81f, 0.89f, 0.25f, 0.75f, read, 0)            // dvouřádková replika
        c.hidden(1000)
        val b = c.next(0.85f, 0.89f, 0.40f, 0.60f, read, 3000)!! // krátká jednořádková
        assertEquals(0.81f, b[0], e); assertEquals(0.89f, b[1], e)
        assertEquals(0.25f, b[2], e); assertEquals(0.75f, b[3], e)
    }

    @Test fun obalkaIPoPauze() {
        // po delší pauze se pole počítá znovu z historie – dvouřádkový titulek z ní ale nezmizí
        val c = area()
        c.next(0.81f, 0.89f, 0.25f, 0.75f, read, 0)
        c.next(0.85f, 0.89f, 0.30f, 0.70f, read, 2000)
        c.hidden(3000)
        val b = c.next(0.85f, 0.89f, 0.40f, 0.60f, read, 30_000)!!
        assertEquals(0.81f, b[0], e); assertEquals(0.25f, b[2], e)
    }

    @Test fun staraHistorieVyprsi() {
        val c = area()
        c.next(0.81f, 0.89f, 0.25f, 0.75f, read, 0)
        c.hidden(1000)
        val b = c.next(0.85f, 0.89f, 0.40f, 0.60f, read, 200_000)!!
        assertEquals(0.85f, b[0], e); assertEquals(0.40f, b[2], e)
    }

    @Test fun odlehleCteniSeNebere() {
        val c = area()
        for (i in 0 until 5) c.next(0.85f, 0.89f, 0.35f, 0.65f, read, i * 1000L)
        c.next(0.72f, 0.89f, 0.35f, 0.65f, read, 6000)          // chybný vysoký rámeček (o 3 řádky výš)
        c.hidden(7000)
        val b = c.next(0.85f, 0.89f, 0.40f, 0.60f, read, 20_000)!!
        assertEquals(0.85f, b[0], e)                            // pole se po pauze nenafoukne o chybné čtení
        assertEquals(0.35f, b[2], e)
    }

    @Test fun pravePrecteneSeZakryjeVzdy() {
        val c = area()
        for (i in 0 until 5) c.next(0.85f, 0.89f, 0.35f, 0.65f, read, i * 1000L)
        val b = c.next(0.80f, 0.89f, 0.10f, 0.90f, read, 6000)!! // nový dlouhý dvouřádkový
        assertEquals(0.80f, b[0], e); assertEquals(0.10f, b[2], e); assertEquals(0.90f, b[3], e)
    }

    @Test fun vRozhovoruSeNezmensuje() {
        val c = area()
        c.next(0.80f, 0.89f, 0.20f, 0.80f, read, 0)
        c.hidden(500)
        val b = c.next(0.86f, 0.89f, 0.45f, 0.55f, read, 1500)!!
        assertEquals(0.80f, b[0], e); assertEquals(0.20f, b[2], e)
    }

    @Test fun jineMistoSeNemicha() {
        val c = area()
        c.next(0.85f, 0.89f, 0.30f, 0.70f, read, 0)
        val r = Zone(0f, 0f, 1f, 1f)
        val b = c.next(0.10f, 0.14f, 0.30f, 0.70f, r, 1000)!!    // titulek nahoře (jiná scéna)
        assertEquals(0.10f, b[0], e); assertEquals(0.14f, b[1], e)
    }

    @Test fun okrajJenUvnitrOblastiCteni() {
        val c = CoverArea(padY = 0.012f, padX = 0.02f)
        // rámeček z OCR přesahuje oblast čtení → okraj jen do oblasti čtení (+ okraj), ne dál
        val b = c.next(0.70f, 0.99f, 0.05f, 0.95f, Zone(0.08f, 0.71f, 0.92f, 0.975f), 0)!!
        assertEquals(0.698f, b[0], e); assertEquals(0.987f, b[1], e)
        assertEquals(0.06f, b[2], e); assertEquals(0.94f, b[3], e)
        val m = CoverArea(padY = 0.012f, padX = 0.02f).next(0.85f, 0.89f, 0.4f, 0.6f, read, 0)!!
        assertEquals(0.838f, m[0], e); assertEquals(0.902f, m[1], e); assertEquals(0.38f, m[2], e)
    }

    @Test fun neznamyTitulek() {
        assertNull(area().next(-1f, -1f, -1f, -1f, read, 0))
        val b = area().next(0.85f, 0.89f, -1f, -1f, read, 0)!!
        assertEquals(-1f, b[2], e); assertEquals(-1f, b[3], e)
    }
}
