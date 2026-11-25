package cz.recaption.core

import cz.recaption.core.License.Kind
import org.junit.Assert.*
import org.junit.Test

class LicenseTest {
    private val t0 = License.MIN_SANE + 1_000_000L
    private val hour = 60L * 60 * 1000
    private val day = 24 * hour

    @Test fun codeAccepted() {
        assertTrue(License.checkCode("Test1989"))
        assertTrue(License.checkCode("  test1989 "))
        assertTrue(License.checkCode("TEST1989"))
    }

    @Test fun wrongCodesRejected() {
        listOf("", "   ", "Test1988", "Test 1989", "test", "1989", "Test19890").forEach {
            assertFalse("kód '$it'", License.checkCode(it))
        }
    }

    @Test fun trialCountsDown() {
        // od 0.97.100 5 dní (dřív 3)
        assertEquals(License.State(Kind.TRIAL, 5), License.state(false, t0, t0, free = false))
        assertEquals(License.State(Kind.TRIAL, 5), License.state(false, t0, t0 + 23 * hour, free = false))
        assertEquals(License.State(Kind.TRIAL, 4), License.state(false, t0, t0 + 25 * hour, free = false))
        assertEquals(License.State(Kind.TRIAL, 1), License.state(false, t0, t0 + 5 * day - 1, free = false))
        assertEquals(Kind.TRIAL, License.state(false, t0, t0 + 3 * day, free = false).kind)
        assertEquals(Kind.EXPIRED, License.state(false, t0, t0 + 5 * day, free = false).kind)
        assertEquals(Kind.EXPIRED, License.state(false, t0, t0 + 30 * day, free = false).kind)
        assertFalse(License.state(false, t0, t0 + 5 * day, free = false).canRun)
        assertTrue(License.state(false, t0, t0 + day, free = false).canRun)
    }

    @Test fun unlockedIsForever() {
        val s = License.state(true, t0, t0 + 1000 * day, free = false)
        assertEquals(Kind.UNLOCKED, s.kind)
        assertTrue(s.canRun)
    }

    @Test fun unsetClockNeverLocks() {
        assertEquals(Kind.TRIAL, License.state(false, t0, 5000L, free = false).kind)          // TV po startu: rok 1970
        assertEquals(Kind.TRIAL, License.state(false, 0L, t0, free = false).kind)
    }

    @Test fun firstRunHandling() {
        assertEquals(t0, License.firstRun(0L, t0))                 // první spuštění
        assertEquals(t0, License.firstRun(t0, t0 + 5 * day))       // uložené se nemění
        assertEquals(0L, License.firstRun(0L, 5000L))              // hodiny nenastavené → neukládat
        assertEquals(t0, License.firstRun(t0, 5000L))              // hodiny se rozbily → drž uložené
        assertEquals(t0, License.firstRun(4000L, t0))              // uloženo se špatnými hodinami → oprav
        assertEquals(t0, License.firstRun(t0 + 9 * day, t0))       // začátek v budoucnosti → teď
    }

    @Test
    fun zatimZdarmaNicSeNezamkne() {
        assertTrue(License.FREE)
        assertTrue(License.state(false, t0, t0 + 30 * day).canRun)
        assertTrue(License.state(false, 0L, 0L).canRun)
    }
}
