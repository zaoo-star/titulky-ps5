package cz.recaption.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BidiTest {
    @Test fun hebrewIsRtlEvenAfterLatinName() {
        assertTrue(Bidi.hasRtl("שלום!"))
        assertTrue(Bidi.hasRtl("LENA: אני כאן."))
        assertTrue(Bidi.hasRtl("PS5 זה משחק"))
    }

    @Test fun otherLanguagesStayLeftToRight() {
        assertFalse(Bidi.hasRtl(null))
        assertFalse(Bidi.hasRtl(""))
        assertFalse(Bidi.hasRtl("LENA: Jsem tady, věř mi!"))
        assertFalse(Bidi.hasRtl("Привіт, як справи?"))
        assertFalse(Bidi.hasRtl("สวัสดีครับ"))   // thajština se píše zleva doprava
        assertFalse(Bidi.hasRtl("¿Dónde está? Ελλάδα 日本"))
    }
}
