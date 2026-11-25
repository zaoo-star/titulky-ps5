package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvProfilesTest {
    private fun tv(platform: String?, model: String?) = TvAnnounce(platform, "1.2.0", model, null, null)

    @Test fun rozpoznaniTelevize() {
        assertEquals("webos:OLED55B7", TvProfiles.id(tv("webos", "OLED55B7")))
        assertEquals("tizen:QE55 Q80", TvProfiles.id(tv("tizen", "  QE55   Q80 ")))
        // bez modelu nebo platformy se televize nerozliší – nastavení zůstává společné
        assertNull(TvProfiles.id(tv("webos", null)))
        assertNull(TvProfiles.id(tv(null, "OLED55B7")))
    }

    @Test fun nazevProHrace() {
        assertEquals("LG OLED55B7", TvProfiles.display("webos:OLED55B7"))
        assertEquals("Samsung QE55Q80", TvProfiles.display("tizen:QE55Q80"))
    }

    @Test fun prepnutiJenPriJineTelevizi() {
        // stejná televize (znovu připojená) = nic se nemění
        assertNull(TvProfiles.decide("webos:A", "webos:A"))
        assertNull(TvProfiles.decide("webos:A", null))
        // první televize: není kam ukládat, jen se zapamatuje
        assertEquals(TvProfiles.Switch(null, "webos:A"), TvProfiles.decide(null, "webos:A"))
        // jiná televize: dosavadní nastavení k té staré, načíst novou
        assertEquals(TvProfiles.Switch("webos:A", "tizen:B"), TvProfiles.decide("webos:A", "tizen:B"))
    }

    @Test fun klicePatriKeVzhleduAOblastem() {
        // společná nastavení (jazyk, konzole, typ televize) se nesmí přepínat s televizí
        for (k in listOf("lang", "ui_lang", "console", "tv_type", "ps5_host")) assert(k !in TvProfiles.KEYS) { k }
        for (k in listOf("layout", "text_size", "opacity", "auto_zone")) assert(k in TvProfiles.KEYS) { k }
    }
}
