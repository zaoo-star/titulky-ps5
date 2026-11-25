package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Panel telefonu pro aplikaci v TV 1.5.22 (zpráva „telefon“). */
class TvPhoneMessageTest {
    @Test fun message() {
        val m = TvSender.phoneMessage("Lena", listOf("Ahoj" to 'l', "Jo \"jasně\"" to 'p', "Seznam" to 'x'),
            floatArrayOf(0.62f, 0.15f, 0.86f, 0.8f), 23, 90)
        assertTrue(m.startsWith("{\"typ\":\"telefon\""))
        assertTrue(m.contains("\"nadpis\":\"Lena\""))
        assertTrue(m.contains("\"strana\":\"l\"") && m.contains("\"strana\":\"p\"") && m.contains("\"strana\":\"s\""))
        assertTrue(m.contains("\\\"jasně\\\""))
        assertTrue(m.contains("\"misto\":[0.6200,0.1500,0.8600,0.8000]"))
        assertEquals(TvSender.PHONE_HIDE, TvSender.phoneMessage(null, emptyList(), floatArrayOf(0f, 0f, 1f, 1f), 20, 90))
        assertFalse(TvSender.phoneMessage(null, listOf("a" to 'l'), floatArrayOf(0f, 0f, 1f, 1f), 20, 90).contains("nadpis"))
    }

    @Test fun imageOnlyOnDivider() {
        val m = TvSender.phoneMessage(null, listOf("Text" to 's', "" to 'c', "Další" to 's'), floatArrayOf(0f, 0f, 0.5f, 0.5f), 20, 90,
            listOf("data:image/jpeg;base64,AAA", "data:image/jpeg;base64,BBB", null))
        assertTrue(m.contains("\"strana\":\"c\",\"obrazek\":\"data:image/jpeg;base64,BBB\""))
        assertFalse(m.contains("AAA"))   // obrázek jen u čáry
        assertFalse(TvSender.phoneMessage(null, listOf("" to 'c', "a" to 's'), floatArrayOf(0f, 0f, 1f, 1f), 20, 90,
            listOf("http://x/y.jpg", null)).contains("obrazek"))
    }

    @Test fun bigFrameHasLongLength() {
        val f = TvSender.frame("x".repeat(70_000))
        assertEquals(127, f[1].toInt() and 0xFF)
        assertEquals(70_000 + 10, f.size)
    }

    @Test fun versions() {
        assertTrue(TvSender.atLeast("1.5.22", "1.5.22"))
        assertTrue(TvSender.atLeast("1.6", "1.5.22"))
        assertFalse(TvSender.atLeast("1.5.21", "1.5.22"))
        assertFalse(TvSender.atLeast("abc", "1.5.22"))
        assertFalse(TvSender.atLeast(null, "1.5.22"))
    }
}
