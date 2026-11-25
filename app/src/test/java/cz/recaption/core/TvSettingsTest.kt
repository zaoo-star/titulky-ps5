package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvSettingsTest {
    @Test fun zpravaSVolbami() {
        assertEquals("{\"typ\":\"nastaveni\",\"pruhlednost\":90,\"velikost\":22,\"podleHry\":true,\"poloha\":\"tesne\",\"original\":false," +
            "\"okraj\":1,\"jmena\":true,\"velikostOrig\":0,\"barvaPisma\":0,\"sporicLuna\":true,\"sporicVideo\":0}",
            TvSettings.message(90, 22, true, ScreenLayout.Position.ABOVE_TEXT, false))
        assertEquals(true, TvSettings.message(90, 22, true, ScreenLayout.Position.OVER, false, 3, false, 30, 2, false, 1)
            .endsWith("\"okraj\":3,\"jmena\":false,\"velikostOrig\":30,\"barvaPisma\":2,\"sporicLuna\":false,\"sporicVideo\":1}"))
        assertEquals(true, TvSettings.message(100, 22, false, ScreenLayout.Position.CUSTOM, true).contains("\"poloha\":\"vlastni\""))
    }

    @Test fun povoleneZmeny() {
        assertEquals(TvSettings.Change.Opacity(80), TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"pruhlednost\",\"hodnota\":80}"))
        assertEquals(TvSettings.Change.TextSize(0), TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"velikost\",\"hodnota\":0}"))
        assertEquals(TvSettings.Change.TextSize(32), TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"velikost\",\"hodnota\":32}"))
        assertEquals(TvSettings.Change.Position(ScreenLayout.Position.TOP), TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"poloha\",\"hodnota\":\"nahore\"}"))
        assertEquals(TvSettings.Change.Original(true), TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"original\",\"hodnota\":true}"))
        // od aplikace v TV 1.5.18: okraj, barevná jména, velikost originálu, barva písma
        assertEquals(TvSettings.Change.Pad(3), TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"okraj\",\"hodnota\":3}"))
        assertEquals(TvSettings.Change.Names(false), TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"jmena\",\"hodnota\":false}"))
        assertEquals(TvSettings.Change.OrigSize(0), TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"velikostOrig\",\"hodnota\":0}"))
        assertEquals(TvSettings.Change.OrigSize(26), TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"velikostOrig\",\"hodnota\":26}"))
        assertEquals(TvSettings.Change.TextColor(3), TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"barvaPisma\",\"hodnota\":3}"))
        assertNull(TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"okraj\",\"hodnota\":4}"))
        assertNull(TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"velikostOrig\",\"hodnota\":13}"))
        assertNull(TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"barvaPisma\",\"hodnota\":4}"))
        assertNull(TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"jmena\",\"hodnota\":1}"))
    }

    @Test fun nepovoleneSeZahodi() {
        // jiné hodnoty, než nabízí telefon, jiné volby a jiné zprávy televize změnit nemůže
        assertNull(TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"pruhlednost\",\"hodnota\":5}"))
        assertNull(TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"velikost\",\"hodnota\":500}"))
        assertNull(TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"poloha\",\"hodnota\":\"vlastni\"}"))
        assertNull(TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"lang\",\"hodnota\":\"en\"}"))
        assertNull(TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"original\",\"hodnota\":\"ano\"}"))
        assertNull(TvSettings.parse("{\"typ\":\"hotovo\",\"id\":3}"))
        assertNull(TvSettings.parse("nesmysl"))
        assertNull(TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"pruhlednost\",\"hodnota\":80,\"x\":\"" + "a".repeat(300) + "\"}"))
    }

    @Test fun hodnotyJakoVTelefonu() {
        assertEquals(TvSettings.OPACITIES, listOf(0, 20, 40, 60, 80, 90, 100))
        assertEquals(TvSettings.TEXT_SIZES, listOf(12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48))
        assertEquals(TvSettings.TEXT_SIZES, cz.recaption.Prefs.TEXT_SIZES.toList())
    }
}
