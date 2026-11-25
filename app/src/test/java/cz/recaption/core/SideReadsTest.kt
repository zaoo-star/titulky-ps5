package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plán vedlejších čtení: titulky mají přednost, vedle běží nejvýš jedno čtení a žádná část nevyhladoví. */
class SideReadsTest {
    private val S = SideReads

    @Test fun jenJednoCteniNajednou() {
        val p = SideReads()
        assertTrue(p.mayStart(S.TIP, 1000, false)); p.started(S.TIP, 1000)
        assertTrue(p.busy)
        assertFalse(p.mayStart(S.PHONE, 1000, false))
        assertFalse(p.mayStart(S.CORNER, 1100, false))
        p.finished(S.TIP, 1200)
        assertFalse(p.busy)
    }

    @Test fun poCteniPauzaStejneDlouha() {
        val p = SideReads()
        assertTrue(p.mayStart(S.TIP, 0, false)); p.started(S.TIP, 0); p.finished(S.TIP, 300)
        // čtení trvalo 300 ms → dalších 300 ms má čtečku jen titulek
        assertFalse(p.mayStart(S.PHONE, 500, false))
        assertTrue(p.mayStart(S.PHONE, 600, false))
        // krátké čtení: aspoň MIN_GAP_MS, dlouhé: nejvýš MAX_GAP_MS
        p.started(S.PHONE, 600); p.finished(S.PHONE, 620)
        assertFalse(p.mayStart(S.TIP, 620 + S.MIN_GAP_MS - 1, false))
        assertTrue(p.mayStart(S.TIP, 620 + S.MIN_GAP_MS, false))
        p.started(S.TIP, 1000); p.finished(S.TIP, 3000)
        assertFalse(p.mayStart(S.PHONE, 3000 + S.MAX_GAP_MS - 1, false))
        assertTrue(p.mayStart(S.PHONE, 3000 + S.MAX_GAP_MS, false))
    }

    @Test fun titulekSeUstaluje_vedlejsiCekaji_aleNeVecne() {
        val p = SideReads()
        assertFalse(p.mayStart(S.PHONE, 0, true))
        assertFalse(p.mayStart(S.PHONE, 500, true))
        assertFalse(p.mayStart(S.PHONE, S.MAX_WAIT_MS - 1, true))
        // zprávy v telefonu chodí i s titulky – po nejdelším čekání se čte i tak
        assertTrue(p.mayStart(S.PHONE, S.MAX_WAIT_MS, true))
    }

    @Test fun titulekUstaleny_cteSeHned() {
        val p = SideReads()
        assertFalse(p.mayStart(S.PHONE, 0, true))
        assertTrue(p.mayStart(S.PHONE, 250, false))
    }

    @Test fun naRadeJeKdoCekaNejdele() {
        val p = SideReads()
        assertTrue(p.mayStart(S.CORNER, 0, false)); p.started(S.CORNER, 0)
        assertFalse(p.mayStart(S.TIP, 100, false))
        assertFalse(p.mayStart(S.PHONE, 200, false))
        p.finished(S.CORNER, 300)
        // roh se ptá první v kole, ale tip a telefon čekají déle
        assertFalse(p.mayStart(S.CORNER, 700, false))
        assertFalse(p.mayStart(S.PHONE, 700, false))
        assertTrue(p.mayStart(S.TIP, 700, false)); p.started(S.TIP, 700); p.finished(S.TIP, 750)
        assertFalse(p.mayStart(S.CORNER, 900, false))
        assertTrue(p.mayStart(S.PHONE, 900, false))
    }

    @Test fun svoleniBezCteniNikohoNezdrzi() {
        val p = SideReads()
        // tip dostal svolení, ale jeho výřez se nezměnil (nečte) → ve stejném kole může číst telefon
        assertTrue(p.mayStart(S.TIP, 0, false))
        assertTrue(p.mayStart(S.PHONE, 0, false))
    }

    @Test fun kdoSePrestalPtatRaduNedrzi() {
        val p = SideReads()
        assertTrue(p.mayStart(S.CORNER, 0, false)); p.started(S.CORNER, 0)
        assertFalse(p.mayStart(S.TIP, 100, false))      // tip čeká… a pak ho hráč vypne
        p.finished(S.CORNER, 200)
        assertFalse(p.mayStart(S.PHONE, 500, false))    // tip je pořád první
        assertTrue(p.mayStart(S.PHONE, 100 + S.STALE_MS + 1, false))
    }

    @Test fun zaseklaUlohaRaduNeblokuje() {
        val p = SideReads()
        assertTrue(p.mayStart(S.TIP, 0, false)); p.started(S.TIP, 0)
        assertFalse(p.mayStart(S.PHONE, S.STUCK_MS, false))
        assertTrue(p.mayStart(S.PHONE, S.STUCK_MS + 1, false))
    }

    @Test fun menuZJasuMimoPoradi() {
        val p = SideReads()
        assertTrue(p.mayStart(S.TIP, 0, false)); p.started(S.TIP, 0)
        // otevření menu hry poznané z jasu nečeká na svolení, jen se zapíše – ostatní pak čekají na něj
        p.started(S.MENU, 50)
        p.finished(S.TIP, 100)          // doběhlé starší čtení řadu neuvolní
        assertTrue(p.busy)
        p.finished(S.MENU, 400)
        assertFalse(p.busy)
        assertEquals(false, p.mayStart(S.TIP, 500, false))
        assertTrue(p.mayStart(S.TIP, 750, false))
    }
}
