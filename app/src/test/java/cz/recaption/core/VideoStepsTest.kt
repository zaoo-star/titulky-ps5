package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoStepsTest {
    private val list = """{"format":1,"klipy":{"krok-jazyky":"0:25","krok-zarizeni-lg":"1:10","krok-zarizeni":"0:40",
        |"krok-pripojeni-ps":"1:30","krok-pripojeni-lg-ps":"1:45","spatne id":"0:10","krok-preklad":"x"}}""".trimMargin()

    @Test fun parseIsStrict() {
        val m = VideoSteps.parse(list)
        assertEquals("0:25", m["krok-jazyky"])
        assertEquals("1:45", m["krok-pripojeni-lg-ps"])
        assertTrue("spatne id" !in m)
        assertTrue("krok-preklad" !in m)   // neplatná délka
        assertTrue(VideoSteps.parse("""{"format":2,"klipy":{"krok-jazyky":"0:25"}}""").isEmpty())
        assertTrue(VideoSteps.parse("nesmysl").isEmpty())
        assertTrue(VideoSteps.parse(null).isEmpty())
    }

    @Test fun picksMostSpecific() {
        val m = VideoSteps.parse(list)
        assertEquals("krok-jazyky", VideoSteps.pick(0, "android", "ps", m))
        assertEquals("krok-zarizeni-lg", VideoSteps.pick(1, "lg", "ps", m))
        assertEquals("krok-zarizeni", VideoSteps.pick(1, "samsung", "ps", m))       // obecný klip kroku
        assertEquals("krok-pripojeni-lg-ps", VideoSteps.pick(5, "lg", "ps", m))
        assertEquals("krok-pripojeni-ps", VideoSteps.pick(5, "android", "ps", m))
        assertNull(VideoSteps.pick(5, "android", "xbox", m))                        // k Xboxu klip zatím není
        assertNull(VideoSteps.pick(9, "android", "ps", m))
    }

    // dnešní seznam (rc25): krok-konzole* ukazuje PS5
    private val today = mapOf("krok-konzole" to "0:12", "krok-konzole-lg" to "0:11", "krok-konzole-samsung" to "0:11",
        "krok-pripojeni-android-ps" to "1:42", "krok-pripojeni-lg-ps" to "1:34", "krok-jazyky" to "0:15", "krok-preklad-lg" to "1:06")

    @Test fun xboxGetsNoPlayStationClip() {
        assertNull(VideoSteps.pick(2, "android", "xbox", today))
        assertNull(VideoSteps.pick(2, "lg", "xbox", today))
        assertNull(VideoSteps.pick(5, "lg", "xbox", today, sub = "rp"))
        assertEquals("krok-konzole-lg", VideoSteps.pick(2, "lg", "ps", today))
        // kroky bez konzole v obraze: Xbox dostane obecný klip
        assertEquals("krok-jazyky", VideoSteps.pick(0, "android", "xbox", today))
        assertEquals("krok-preklad-lg", VideoSteps.pick(3, "lg", "xbox", today))
        val withXbox = today + ("krok-konzole-xbox" to "0:20")
        assertEquals("krok-konzole-xbox", VideoSteps.pick(2, "samsung", "xbox", withXbox))
        assertEquals("krok-konzole-samsung", VideoSteps.pick(2, "samsung", "ps", withXbox))
    }

    @Test fun substepFirstThenStep() {
        // bez klipů podkroků stejné chování jako dřív
        assertEquals("krok-pripojeni-lg-ps", VideoSteps.pick(5, "lg", "ps", today, sub = "cislo"))
        assertEquals(VideoSteps.pick(5, "android", "ps", today), VideoSteps.pick(5, "android", "ps", today, sub = "ucet"))
        val m = VideoSteps.parse("""{"format":1,"klipy":{"krok-pripojeni-lg-ps":"1:34","krok-pripojeni-cislo":"0:20",
            |"krok-pripojeni-rp-lg-ps":"0:30","krok-preklad-klic":"0:25","krok-hotovo-hrat":"0:15"}}""".trimMargin())
        assertEquals("0:30", m["krok-pripojeni-rp-lg-ps"])   // 4 části za krok- jsou platné
        assertEquals("krok-pripojeni-cislo", VideoSteps.pick(5, "lg", "ps", m, sub = "cislo"))
        assertEquals("krok-pripojeni-rp-lg-ps", VideoSteps.pick(5, "lg", "ps", m, sub = "rp"))
        assertEquals("krok-pripojeni-lg-ps", VideoSteps.pick(5, "lg", "ps", m, sub = "prihlaseni"))   // podkrok bez klipu → klip kroku
        assertEquals("krok-pripojeni-lg-ps", VideoSteps.pick(5, "lg", "ps", m))
        assertEquals("krok-preklad-klic", VideoSteps.pick(3, "samsung", "ps", m, sub = "klic"))
        assertEquals("krok-hotovo-hrat", VideoSteps.pick(6, "android", "ps", m, sub = "hrat"))
        assertNull(VideoSteps.pick(6, "android", "xbox", m, sub = "hrat"))                          // klip hraní ukazuje PS5
        assertEquals("krok-pripojeni-lg-ps", VideoSteps.pick(5, "lg", "ps", m, sub = "Zle"))       // neplatný podkrok se ignoruje
        assertEquals(listOf("krok-instalace-usb-android-ps", "krok-instalace-usb-android", "krok-instalace-usb-ps", "krok-instalace-usb",
            "krok-instalace-android-ps", "krok-instalace-android", "krok-instalace-ps", "krok-instalace"),
            VideoSteps.candidates("instalace", "android", "ps", "usb"))
        assertTrue(VideoSteps.candidates(9, "lg", "ps", "rp").isEmpty())
    }

    @Test fun idsAndOldApks() {
        for (id in listOf("krok-instalace-downloader", "krok-zarizeni-dev-lg", "krok-zarizeni-apps-samsung", "krok-konzole-psn",
                "krok-pripojeni-rp-samsung-ps", "krok-hotovo-hrat")) assertTrue(id, VideoSteps.ID.matches(id))
        for (id in listOf("krok-a-b-c-d-e", "krok-zarizeni-a2s", "krok-", "Krok-jazyky", "video-jazyky")) assertTrue(id, !VideoSteps.ID.matches(id))
        // starší APK (regex do rc25) id se 4 částmi tiše vynechá, kratší id podkroku vezme do seznamu, ale samo ho nikdy nehledá
        val old = Regex("^krok-[a-z]+(-[a-z]+){0,2}$")
        assertTrue(!old.matches("krok-pripojeni-rp-lg-ps"))
        val oldCandidates = listOf("android", "lg", "samsung").flatMap { tv -> listOf("ps", "xbox").flatMap { c ->
            VideoSteps.STEPS.take(6).flatMap { s -> listOf("krok-$s-$tv-$c", "krok-$s-$tv", "krok-$s-$c", "krok-$s") } } }
        for (id in listOf("krok-instalace-usb", "krok-zarizeni-dev-lg", "krok-konzole-psn", "krok-pripojeni-cislo", "krok-preklad-klic"))
            assertTrue(id, id !in oldCandidates)
    }

    @Test fun csOnlyList() {
        val json = """{"format": 1, "klipy": {"krok-jazyky": "0:15", "krok-konzole": "0:12"}, "jen_cs": ["krok-jazyky", "Spatne ID", "krok-instalace-mobil"]}"""
        assertEquals(setOf("krok-jazyky", "krok-instalace-mobil"), VideoSteps.parseCsOnly(json))
        // seznam klipů se kvůli jen_cs nemění (starší aplikace ho přeskočí stejně)
        assertEquals(mapOf("krok-jazyky" to "0:15", "krok-konzole" to "0:12"), VideoSteps.parse(json))
        assertEquals(emptySet<String>(), VideoSteps.parseCsOnly("""{"format": 1, "klipy": {"krok-jazyky": "0:15"}}"""))
        assertEquals(emptySet<String>(), VideoSteps.parseCsOnly(null))
    }
}
