package cz.recaption.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JadroTest {
    @Test fun textyAJazyky() {
        assertEquals("Domů", ReCaptionJadro.text("cs", "m_home"))
        assertEquals("Home", ReCaptionJadro.text("en", "m_home"))
        assertEquals("Home", ReCaptionJadro.text("ja", "m_home"))
        assertEquals("de", ReCaptionJadro.uiLang(listOf("de-DE", "cs-CZ")))
        assertEquals("sk", ReCaptionJadro.uiLang(listOf("ja-JP", "sk")))
        assertEquals("en", ReCaptionJadro.uiLang(listOf("ja-JP")))
        // jazyků aplikace přibývá (1.00-rc11: 37) – jen že jsou tam aspoň ty původní
        val l = ReCaptionJadro.languages().map { it.substringBefore("|") }
        assertTrue(l.size >= 10 && "cs" in l && "en" in l, l.toString())
    }
}
