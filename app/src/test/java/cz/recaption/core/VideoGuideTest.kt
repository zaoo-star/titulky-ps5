package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoGuideTest {
    @Test fun czechAndSlovakWatchCzechOthersEnglish() {
        assertEquals("cs", VideoGuide.lang(Lang.CS))
        assertEquals("cs", VideoGuide.lang(Lang.SK))
        for (l in Lang.uiLangs.filter { it != Lang.CS && it != Lang.SK }) assertEquals("en", VideoGuide.lang(l))
        assertEquals("https://recaption.pages.dev/videa/krok-jazyky-en.mp4", VideoGuide.video("krok-jazyky", Lang.DE))
        assertEquals("https://recaption.pages.dev/videa/krok-jazyky-lg-cs.mp4", VideoGuide.video("krok-jazyky-lg", Lang.SK))
    }

    @Test fun posterNextToVideo() {
        assertEquals("https://recaption.pages.dev/videa/krok-konzole-en.jpg", VideoGuide.poster("krok-konzole", Lang.EN))
        for (l in Lang.uiLangs)
            assertEquals(VideoGuide.video("krok-preklad", l).removeSuffix(".mp4"), VideoGuide.poster("krok-preklad", l).removeSuffix(".jpg"))
    }

    @Test fun onHelpPagesSite() {
        assertTrue(VideoGuide.BASE.startsWith(HelpLinks.BASE))
    }

    @Test fun csOnlyForAllLanguages() {
        for (l in Lang.uiLangs) {
            assertEquals("https://recaption.pages.dev/videa/krok-jazyky-cs.mp4", VideoGuide.video("krok-jazyky", l, csOnly = true))
            assertEquals("https://recaption.pages.dev/videa/krok-jazyky-cs.jpg", VideoGuide.poster("krok-jazyky", l, csOnly = true))
        }
    }
}
