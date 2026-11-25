package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Webová obdoba aplikace: jen povolené volby a hodnoty, stav pro stránku, sekce na stránce z TV. */
class WebSettingsTest {
    private val games = listOf("all", "hra1")

    @Test fun onlyAllowedValues() {
        assertEquals(WebSettings.Change.Target(Lang.CS), WebSettings.parse("lang", "cs", games))
        assertNull(WebSettings.parse("lang", "en", games))          // angličtina není jazyk překladu
        assertEquals(WebSettings.Change.Game("hra1"), WebSettings.parse("game", "hra1", games))
        assertNull(WebSettings.parse("game", "neznama", games))
        assertEquals(WebSettings.Change.Look(TvSettings.Change.TextSize(22)), WebSettings.parse("velikost", "22", games))
        assertEquals(WebSettings.Change.Look(TvSettings.Change.TextSize(0)), WebSettings.parse("velikost", "0", games))
        assertNull(WebSettings.parse("velikost", "23", games))
        assertNull(WebSettings.parse("velikost", "22,\"x\":1", games))
        assertEquals(WebSettings.Change.Look(TvSettings.Change.Opacity(80)), WebSettings.parse("pruhlednost", "80", games))
        assertEquals(WebSettings.Change.Look(TvSettings.Change.Position(ScreenLayout.Position.ABOVE_TEXT)), WebSettings.parse("poloha", "tesne", games))
        assertNull(WebSettings.parse("poloha", "vlastni", games))
        assertEquals(WebSettings.Change.Prompts(false), WebSettings.parse("pokyny", "0", games))
        assertEquals(WebSettings.Change.Deepl(true), WebSettings.parse("deepl", "1", games))
        assertNull(WebSettings.parse("ucet", "1", games))
        assertNull(WebSettings.parse("lang", null, games))
    }

    @Test fun stateForThePage() {
        val json = WebSettings.json(WebSettings.State("cs", "en", "hra1", 80, 0, "tesne", true, true, false, false, true, true,
            listOf("ok" to "PS5-151 · připojeno", "pozor" to "a\"b")))
        val m = MiniJson.obj(json)!!
        assertEquals("cs", m["lang"]); assertEquals("tesne", m["poloha"]); assertEquals(true, m["deepl"])
        assertEquals(listOf(listOf("ok", "PS5-151 · připojeno"), listOf("pozor", "a\"b")), m["rows"])
        // stav stránky nese webová data vedle stavu účtu a spárování
        val st = MiniJson.obj(SetupPage.status(SetupPage.Status(null, SetupPage.Pair.IDLE, null, false), json))!!
        assertTrue(st["web"] is Map<*, *>)
        assertEquals("idle", st["pair"])
    }

    @Test fun pageHasSettingsOnlyWhenOffered() {
        val t = PhoneLogin.Target("192.168.1.20", 8766, "abcd2345efgh6789")
        val opts = WebSettings.Options(listOf("cs" to "Čeština"), listOf("en" to "English"),
            listOf(WebSettings.GameOption("all", "Univerzální", false, false), WebSettings.GameOption("hra1", "Ukázková hra", true, true)),
            listOf("pres" to "Přes titulky"), "podle hry")
        val with = SetupPage.page(Lang.CS, "TV", t, "https://x/a.apk", "https://s/", "https://d/", "https://k/", web = opts)
        assertTrue(with.contains("id=\"c4\"") && with.contains("op=web") && with.contains("id=\"stav\""))
        val o = with.substringAfter("<script type=\"application/json\" id=\"o\">").substringBefore("</script>")
        assertTrue((MiniJson.obj(o)!!["sizes"] as List<*>).contains(0L))
        assertFalse(SetupPage.page(Lang.CS, "TV", t, "https://x/a.apk", "https://s/", "https://d/", "https://k/").contains("id=\"c4\""))
    }
}
