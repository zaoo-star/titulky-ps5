package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSetupTest {
    @Test fun onlySettingsTransfer() {
        // nastavení hry a titulků ano
        for (k in listOf("lang", "text_size", "game", "ps5_host", "psn_id", "layout", "obj_size", "dict_cs", "wizard_done"))
            assertTrue(k, RemoteSetup.transfers(k))
        // věci vázané na zařízení nikdy
        for (k in listOf("tv_type", "reader_device", "force_tv", "tv_send", "stats", "unlocked", "first_run",
            "quick_key", "seen_version", "phone_accounts", "test_channel", "debug", "high_res_failed", "game_guess",
            "gloss_sig", "hry_kontrola", "obj_", "dict_"))
            assertFalse(k, RemoteSetup.transfers(k))
    }

    @Test fun roundTripKeepsTypes() {
        val values = linkedMapOf<String, Any?>(
            "text_size" to 26, "bold_text" to true, "first_run" to 5L, "hold_ms" to 1500,
            "lang" to "cs", "dict_cs" to "Wasted=Mrtvý\n\"Bro\"=Brácho", "game_menu_volba" to setOf("b", "a"),
            "obj_size" to 1.5f, "ps5_ip" to null, "stats" to "nic",
        )
        val back = RemoteSetup.decode(RemoteSetup.encode(values))!!
        assertEquals(26, back["text_size"]); assertTrue(back["text_size"] is Int)
        assertEquals(true, back["bold_text"])
        assertEquals("Wasted=Mrtvý\n\"Bro\"=Brácho", back["dict_cs"])
        assertEquals(setOf("a", "b"), back["game_menu_volba"])
        assertEquals(1.5f, back["obj_size"])
        assertTrue(back.containsKey("ps5_ip")); assertNull(back["ps5_ip"])
        // nepřenášené se do zprávy nedostanou
        assertFalse(back.containsKey("first_run")); assertFalse(back.containsKey("stats"))
    }

    @Test fun longStaysLong() {
        val back = RemoteSetup.decode("""{"hry_auto":["l",1732190000000]}""".replace("hry_auto", "text_size"))!!
        assertEquals(1732190000000L, back["text_size"])
    }

    @Test fun badMessagesAreRefused() {
        assertNull(RemoteSetup.decode("nic"))
        assertNull(RemoteSetup.decode("""{"text_size":22}"""))
        assertNull(RemoteSetup.decode("""{"text_size":["i","22"]}"""))
        assertNull(RemoteSetup.decode("""{"text_size":["i",99999999999]}"""))
        assertNull(RemoteSetup.decode("""{"text_size":["?",1]}"""))
        // neznámá (nepřenášená) volba se tiše zahodí, zbytek platí
        assertEquals(mapOf("lang" to "de"), RemoteSetup.decode("""{"tv_type":["s","LG"],"lang":["s","de"]}"""))
    }

    @Test fun diffSendsOnlyChanges() {
        val before = mapOf("text_size" to 22, "lang" to "cs", "ps5_ip" to "192.168.1.5", "stats" to "a")
        val after = mapOf("text_size" to 24, "lang" to "cs", "stats" to "b", "bold_text" to true)
        assertEquals(mapOf("text_size" to 24, "bold_text" to true, "ps5_ip" to null), RemoteSetup.diff(before, after))
    }

    @Test fun snapshotRoundTrip() {
        val text = RemoteSetup.snapshot("Obývák \"TV\"", mapOf("lang" to "cs", "text_size" to 30, "tv_type" to "ANDROID"), true)
        val s = RemoteSetup.parseSnapshot(text)!!
        assertEquals(RemoteSetup.VERSION, s.version)
        assertEquals("Obývák \"TV\"", s.tvName)
        assertTrue(s.hasDeeplKey)
        assertEquals(mapOf("lang" to "cs", "text_size" to 30), s.values)
        assertNull(s.overlay)   // starší TV pole „pruh“ neposílá
        assertEquals(true, RemoteSetup.parseSnapshot(RemoteSetup.snapshot("TV", mapOf("lang" to "cs"), false, overlay = true))!!.overlay)
        assertEquals(false, RemoteSetup.parseSnapshot(RemoteSetup.snapshot("TV", mapOf("lang" to "cs"), false, overlay = false))!!.overlay)
        assertNull(RemoteSetup.parseSnapshot("""{"name":"x"}"""))
    }

    @Test fun linkOnlyToHomeNetwork() {
        val t = PhoneLogin.Target("192.168.1.20", 8766, "abcdefgh")
        val l = RemoteSetup.parseLink(RemoteSetup.appLink(t, "Sony Bravia & Co"))!!
        assertEquals(t, l.target)
        assertEquals("Sony Bravia & Co", l.tvName)
        // adresa mimo domácí síť, špatný klíč nebo jiný odkaz – nic
        assertNull(RemoteSetup.parseLink("recaption://tv?h=8.8.8.8&p=8766&k=abcdefgh"))
        assertNull(RemoteSetup.parseLink("recaption://tv?h=192.168.1.20&p=8766&k=a"))
        assertNull(RemoteSetup.parseLink("recaption://login?h=192.168.1.20&p=8766&k=abcdefgh"))
    }

    @Test fun pageHasAppButtonInEveryLanguage() {
        for (lang in Lang.uiLangs) {
            val html = RemoteSetup.page(lang, "recaption://tv?h=192.168.1.20&p=8766&k=abcdefgh&n=TV")
            assertTrue(html.contains("href=\"recaption://tv?h=192.168.1.20&amp;p=8766"))
            assertTrue(html.contains(PhoneLogin.esc(I18n.t(lang, "rs_page_app"))))
        }
    }
}
