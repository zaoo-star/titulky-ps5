package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FadingTest {
    @Test fun doznivajiciRadekSeVynecha() {
        val read = "Workers:Leavebroalone!\nWorkers: C'mon bro, why you stuntin'? What?"
        assertEquals("Workers: C'mon bro, why you stuntin'? What?",
            TextLogic.withoutFading(read, "Workers: Leave bro alone!"))
    }

    @Test fun samotnyStaryTitulekZustane() {
        assertEquals("Workers: Leave bro alone!", TextLogic.withoutFading("Workers: Leave bro alone!", "Workers: Leave bro alone!"))
    }

    @Test fun novyDvouradkovyTitulekSeNemeni() {
        val read = "Lucia: We need to go,\nright now."
        assertEquals(read, TextLogic.withoutFading(read, "Jason: Wait for me."))
    }

    @Test fun bezPredchozihoNicNemeni() {
        val read = "A: one thing\nB: other thing"
        assertEquals(read, TextLogic.withoutFading(read, ""))
    }

    @Test fun obaRadkyStareZustanouCele() {
        val shown = "Lucia: We need to go,\nright now."
        assertEquals("Lucia: We need to go,\nrightnow.", TextLogic.withoutFading("Lucia: We need to go,\nrightnow.", shown))
    }
}
