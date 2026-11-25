package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Aplikace v TV vydaná samostatně (tv-verze.txt + balíčky ve vydání): verze, výběr staženého, kontrola balíčku. */
class TvUpdateTest {
    private val noise = Random(7).nextBytes(4000)

    private fun lgIpk(version: String, id: String = LgIpk.APP_ID) = LgIpk.build(mapOf(
        "appinfo.json" to """{"id": "$id", "version": "$version", "title": "ReCaption"}""".toByteArray(),
        "app.js" to noise,
    ))

    private fun wgt(version: String, extra: Map<String, ByteArray> = emptyMap()) = SamsungWgt.build(mapOf(
        "config.xml" to ("""<?xml version="1.0" encoding="UTF-8"?><widget xmlns="http://www.w3.org/ns/widgets" """ +
            """xmlns:tizen="http://tizen.org/ns/widgets" id="x" version="$version"><tizen:application id="ReCaption1.ReCaption" """ +
            """package="ReCaption1" required_version="2.3"/></widget>""").toByteArray(),
        "app.js" to noise,
    ) + extra)

    @Test fun parse() {
        assertEquals("1.5.44", TvUpdate.parse("1.5.44\n"))
        assertEquals("1.5.44", TvUpdate.parse("  1.5.44 navíc"))
        assertNull(TvUpdate.parse(""))
        assertNull(TvUpdate.parse(null))
        assertNull(TvUpdate.parse("<html>"))
        assertNull(TvUpdate.parse("1.5"))
    }

    @Test fun numericCompare() {
        assertTrue(TvUpdate.compare("1.5.44", "1.5.100")!! < 0)
        assertTrue(TvUpdate.compare("1.10.0", "1.9.99")!! > 0)
        assertEquals(0, TvUpdate.compare("1.5.44", "1.5.44"))
        assertNull(TvUpdate.compare("1.5", "1.5.44"))
        assertTrue(TvUpdate.newer("1.5.100", "1.5.44"))
        assertFalse(TvUpdate.newer("1.5.44", "1.5.44"))
        assertFalse(TvUpdate.newer(null, "1.5.44"))
        assertTrue(TvUpdate.newer("1.5.45", null))
        assertEquals("1.5.100", TvUpdate.best("1.5.44", "1.5.100"))
        assertEquals("1.5.44", TvUpdate.best("1.5.44", null))
        assertNull(TvUpdate.best(null, "x"))
    }

    @Test fun downloadedOrBundled() {
        // stažený jen novější než v APK; po aktualizaci APK na stejnou nebo vyšší verzi zase APK
        assertTrue(TvUpdate.useDownloaded("1.5.44", "1.5.45"))
        assertFalse(TvUpdate.useDownloaded("1.5.45", "1.5.45"))
        assertFalse(TvUpdate.useDownloaded("1.5.46", "1.5.45"))
        assertFalse(TvUpdate.useDownloaded("1.5.44", null))
        // stahovat jen novější než APK i než už stažený
        assertTrue(TvUpdate.shouldDownload("1.5.45", "1.5.44", null))
        assertFalse(TvUpdate.shouldDownload("1.5.45", "1.5.44", "1.5.45"))
        assertTrue(TvUpdate.shouldDownload("1.5.46", "1.5.44", "1.5.45"))
        assertFalse(TvUpdate.shouldDownload("1.5.44", "1.5.44", null))
        assertFalse(TvUpdate.shouldDownload(null, "1.5.44", null))
    }

    @Test fun rateLimit() {
        val h = 3600_000L
        assertTrue(TvUpdate.shouldCheck(0, 10 * h, page = false))
        assertFalse(TvUpdate.shouldCheck(10 * h, 15 * h, page = false))
        assertTrue(TvUpdate.shouldCheck(10 * h, 16 * h, page = false))
        assertFalse(TvUpdate.shouldCheck(10 * h, 10 * h + 5 * 60_000, page = true))
        assertTrue(TvUpdate.shouldCheck(10 * h, 10 * h + 10 * 60_000, page = true))
        assertTrue(TvUpdate.shouldCheck(10 * h, 9 * h, page = false))   // posunuté hodiny
    }

    @Test fun lgPackage() {
        val ipk = lgIpk("1.5.45")
        val m = TvUpdate.checkLg(ipk, "1.5.45")
        assertNotNull(m)
        // popis projde stejnou kontrolou jako přibalený v APK
        assertEquals("1.5.45", LgInstall.bundle(m!!, ipk).version)
        assertNull(TvUpdate.checkLg(ipk, "1.5.46"))                     // jiná verze než v tv-verze.txt
        assertNull(TvUpdate.checkLg(lgIpk("1.5.45", "cz.jina.tv"), "1.5.45"))
        assertNull(TvUpdate.checkLg(ipk.copyOf(ipk.size / 2), "1.5.45"))  // useknutý
        assertNull(TvUpdate.checkLg("<html>nenalezeno</html>".padEnd(2000).toByteArray(), "1.5.45"))
        assertNull(TvUpdate.checkLg(wgt("1.5.45"), "1.5.45"))
    }

    @Test fun samsungPackage() {
        val w = wgt("1.5.45")
        val m = TvUpdate.checkSamsung(w, "1.5.45")
        assertNotNull(m)
        assertEquals("1.5.45", SamsungInstall.bundle(m!!, w)!!.version)
        assertNull(TvUpdate.checkSamsung(w, "1.5.44"))
        assertNull(TvUpdate.checkSamsung(w.copyOf(w.size - 30), "1.5.45"))   // useknutý konec
        assertNull(TvUpdate.checkSamsung(w.copyOf(w.size / 2), "1.5.45"))
        assertNull(TvUpdate.checkSamsung(lgIpk("1.5.45"), "1.5.45"))
        // podepsaný balíček se nevydává za univerzální (build podpis odmítne – podvrhneme ho jako ZIP ručně)
        val zip = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zip).use { z ->
            for ((k, v) in SamsungWgt.read(w) + ("author-signature.xml" to "x".toByteArray())) { z.putNextEntry(java.util.zip.ZipEntry(k)); z.write(v); z.closeEntry() }
        }
        assertNull(TvUpdate.checkSamsung(zip.toByteArray(), "1.5.45"))
    }
}
