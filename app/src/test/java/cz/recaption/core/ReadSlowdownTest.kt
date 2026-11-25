package cz.recaption.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadSlowdownTest {
    /** Pomocník: vzorky po 2 s s danou rychlostí (snímků/s). */
    private class Feed(val d: ReadSlowdown) {
        var now = 1000L
        var count = 0L
        fun run(samples: Int, fps: Int, active: Boolean = true, visible: Boolean = false): Boolean {
            var r = d.slow
            repeat(samples) {
                now += 2000; count += fps * 2L
                r = d.sample(now, count, active, visible)
            }
            return r
        }
    }

    @Test fun rovnomerneCteniNehlasi() {
        val f = Feed(ReadSlowdown())
        assertFalse(f.run(60, 30))
        assertFalse(f.run(10, 24))   // mírný pokles není zpomalení
    }

    @Test fun nahlyPropadOhlasiAzPoTrechVzorcich() {
        val f = Feed(ReadSlowdown())
        f.run(20, 30)
        assertFalse(f.run(2, 2))     // 4 s – krátké zaškobrtnutí
        assertTrue(f.run(1, 2))      // 6 s
        assertTrue(f.run(30, 0))     // úplně stojí – drží
    }

    @Test fun kratkeZaskobrtnutiSeNehlasi() {
        val f = Feed(ReadSlowdown())
        f.run(20, 30)
        f.run(2, 0)
        assertFalse(f.run(5, 30))
    }

    @Test fun poNavratuZmizi() {
        val f = Feed(ReadSlowdown())
        f.run(20, 30)
        assertTrue(f.run(10, 1))
        assertTrue(f.run(1, 30))     // jeden dobrý vzorek nestačí
        assertFalse(f.run(1, 30))
        assertFalse(f.run(20, 30))
    }

    @Test fun dlouhePomaleCteniNezmeniObvyklouRychlost() {
        val f = Feed(ReadSlowdown())
        f.run(20, 30)
        f.run(300, 3)                // 10 minut v jiné aplikaci
        assertTrue(f.d.slow)
        assertTrue(f.d.usual > 20)
    }

    @Test fun pauzaAMenuNehlasi() {
        val f = Feed(ReadSlowdown())
        f.run(20, 30)
        assertFalse(f.run(20, 0, active = false))
        // po návratu se úsek počítá znovu – první vzorek po pauze nesmí vyjít jako propad
        assertFalse(f.run(5, 30))
        assertTrue(f.run(5, 0))
        assertFalse(f.run(1, 0, active = false))   // pauza upozornění zruší hned
    }

    @Test fun pomalyZdrojSeNehlida() {
        val f = Feed(ReadSlowdown())
        f.run(20, 2)                 // např. snímky jen na požádání
        assertFalse(f.run(20, 0))
    }

    @Test fun viditelnaAplikaceSiPoCaseZvykne() {
        val f = Feed(ReadSlowdown())
        f.run(20, 30)
        assertTrue(f.run(10, 5, visible = true))
        assertFalse(f.run(50, 5, visible = true))  // přes 90 s vidět a stále pomalé → nová obvyklá rychlost
        assertFalse(f.run(20, 5, visible = true))
    }

    @Test fun pocitadloOdZnovaNeniPropad() {
        val f = Feed(ReadSlowdown())
        f.run(20, 30)
        f.count = 0                  // nové spojení, počítadlo od nuly
        assertFalse(f.run(10, 30))
    }
}
