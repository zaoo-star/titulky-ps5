package cz.recaption.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayBudgetTest {
    @Test fun stropOken() {
        val b = WindowBudget(max = 3)
        assertTrue(b.mayAdd(0, 1000))
        assertTrue(b.mayAdd(2, 1000))
        assertFalse("plno – další okno se nepřidá", b.mayAdd(3, 1000))
    }

    @Test fun pauzaPoChybe() {
        val b = WindowBudget(max = 10, pauseMs = 5000)
        b.failed(1000)
        assertFalse("hned po chybě se nezkouší znovu", b.mayAdd(0, 2000))
        assertTrue("po pauze ano", b.mayAdd(0, 6000))
        b.failed(10_000)
        b.ok()
        assertTrue("povedené přidání pauzu ruší", b.mayAdd(0, 10_001))
    }

    @Test fun obdelnikKolemPopisku() {
        assertNull(OverlayBounds.union(emptyList()))
        assertArrayEquals(intArrayOf(10, 20, 30, 40), OverlayBounds.union(listOf(intArrayOf(10, 20, 30, 40))))
        // popisek vlevo nahoře a vpravo dole → jedno okno přes oba
        assertArrayEquals(intArrayOf(10, 20, 490, 380),
            OverlayBounds.union(listOf(intArrayOf(10, 20, 100, 30), intArrayOf(400, 350, 100, 50))))
        // prázdný rozměr nikdy nedá okno 0×0
        assertArrayEquals(intArrayOf(5, 5, 1, 1), OverlayBounds.union(listOf(intArrayOf(5, 5, 0, 0))))
    }
}
