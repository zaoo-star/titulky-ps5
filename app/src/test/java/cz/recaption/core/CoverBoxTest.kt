package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverBoxTest {
    private val e = 0.0001f

    @Test fun okrajKolemTitulku() {
        val c = CoverBox(0.012f, 0.02f)
        val b = c.next(0.80f, 0.86f, 0.30f, 0.70f)!!
        assertEquals(0.788f, b[0], e); assertEquals(0.872f, b[1], e)
        assertEquals(0.28f, b[2], e); assertEquals(0.72f, b[3], e)
    }

    @Test fun vRozhovoruSePruhNezmensuje() {
        val c = CoverBox(0.0f, 0.0f)
        c.next(0.78f, 0.86f, 0.20f, 0.80f)              // dlouhá dvouřádková replika
        val b = c.next(0.82f, 0.86f, 0.40f, 0.60f)!!     // krátká – pruh zůstane velký
        assertEquals(0.78f, b[0], e); assertEquals(0.20f, b[2], e); assertEquals(0.80f, b[3], e)
        c.reset()                                        // pruh zmizel → znovu podle titulku
        val n = c.next(0.82f, 0.86f, 0.40f, 0.60f)!!
        assertEquals(0.82f, n[0], e); assertEquals(0.40f, n[2], e)
    }

    @Test fun jinyMistoSeNeslucuje() {
        val c = CoverBox(0.0f, 0.0f)
        c.next(0.80f, 0.86f, 0.3f, 0.7f)
        val b = c.next(0.10f, 0.14f, 0.3f, 0.7f)!!       // titulek nahoře (jiná scéna) – nespojovat přes půl obrazu
        assertEquals(0.10f, b[0], e); assertEquals(0.14f, b[1], e)
    }

    @Test fun neznamyTitulek() {
        assertNull(CoverBox().next(-1f, -1f, -1f, -1f))
        val b = CoverBox(0.01f, 0.02f).next(0.8f, 0.85f, -1f, -1f)!!
        assertEquals(-1f, b[2], e); assertEquals(-1f, b[3], e)
    }
}
