package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** „Poslat vývojáři“: hlaseni.json, odpověď serveru, tvar kódu, snímky do limitu, info.json, nabízené dávky. */
class ReportUploadTest {

    private val mb = 1_000_000L

    @Test fun configFromHelpPages() {
        val c = ReportUpload.config("""{"format":1,"url":"https://hlaseni.example.workers.dev/","maxMB":45}""")
        assertNotNull(c)
        assertEquals("https://hlaseni.example.workers.dev", c!!.url)
        assertEquals("https://hlaseni.example.workers.dev/v1/nahrat", c.uploadUrl)
        assertEquals(45 * mb, c.maxBytes)
        // bez maxMB / nesmyslné maxMB = výchozí 45 MB
        assertEquals(45 * mb, ReportUpload.config("""{"format":1,"url":"https://a.workers.dev"}""")!!.maxBytes)
        assertEquals(45 * mb, ReportUpload.config("""{"format":1,"url":"https://a.workers.dev","maxMB":0}""")!!.maxBytes)
        assertEquals(45 * mb, ReportUpload.config("""{"format":1,"url":"https://a.workers.dev","maxMB":5000}""")!!.maxBytes)
        assertEquals(10 * mb, ReportUpload.config("""{"format":1,"url":"https://a.workers.dev","maxMB":10}""")!!.maxBytes)
    }

    @Test fun missingOrInvalidConfigMeansNotSetUp() {
        listOf(
            null, "", "  ", "<html>404</html>", "{}", "[]",
            """{"format":1,"url":""}""",
            """{"format":2,"url":"https://a.workers.dev"}""",
            """{"url":"https://a.workers.dev"}""",
            """{"format":1,"url":"http://a.workers.dev"}""",              // jen https
            """{"format":1,"url":"https://a.workers.dev/?x=1"}""",
            """{"format":1,"url":"javascript:alert(1)"}""",
            """{"format":1,"url":"https://a b.dev"}""",
            """{"format":1,"url":42}""",
        ).forEach { assertNull(it, ReportUpload.config(it)) }
    }

    @Test fun serverAnswers() {
        val ok = ReportUpload.answer(201, """{"kod":"K7M2QX9P","platnost_dni":30}""")
        assertTrue(ok.ok)
        assertEquals("K7M2QX9P", ok.code)
        assertEquals(30, ok.days)
        assertEquals(30, ReportUpload.answer(201, """{"kod":"K7M2QX9P"}""").days)
        assertEquals(7, ReportUpload.answer(201, """{"kod":"k7m2-qx9p","platnost_dni":7}""").days)
        assertEquals("K7M2QX9P", ReportUpload.answer(201, """{"kod":"k7m2-qx9p"}""").code)
        // 201 bez platného kódu = chyba
        assertEquals("snd_e_fail", ReportUpload.answer(201, "{}").error)
        assertEquals("snd_e_fail", ReportUpload.answer(201, """{"kod":"<b>"}""").error)
        assertEquals("snd_e_fail", ReportUpload.answer(201, null).error)
        assertEquals("snd_e_bad", ReportUpload.answer(400, """{"chyba":"zip"}""").error)
        assertEquals("snd_e_big", ReportUpload.answer(413, null).error)
        assertEquals("snd_e_busy", ReportUpload.answer(429, null).error)
        assertEquals("snd_e_limit", ReportUpload.answer(503, null).error)
        assertEquals("snd_e_fail", ReportUpload.answer(500, null).error)
        assertEquals("snd_e_fail", ReportUpload.answer(302, null).error)
        assertFalse(ReportUpload.answer(500, null).ok)
    }

    @Test fun errorKeysHaveTexts() {
        listOf(ReportUpload.E_NET, ReportUpload.E_BAD, ReportUpload.E_BIG, ReportUpload.E_BUSY, ReportUpload.E_LIMIT,
            ReportUpload.E_FAIL, ReportUpload.E_PACK, ReportUpload.E_OFF).forEach { assertTrue(it, I18n.has(it)) }
    }

    @Test fun codeShownWithDash() {
        assertEquals("K7M2-QX9P", ReportUpload.display("K7M2QX9P"))
        assertEquals("K7M2-QX9P", ReportUpload.display("k7m2qx9p"))
        assertEquals("K7M2-QX9P", ReportUpload.display("K7M2-QX9P"))
        assertEquals("ABC-DEF", ReportUpload.display("ABCDEF"))
        assertEquals("ABCD-EFG", ReportUpload.display("ABCDEFG"))
        assertNull(ReportUpload.normalize("ABC"))
        assertNull(ReportUpload.normalize("K7M2QX9P!"))
    }

    @Test fun wholeBatchWhenItFits() {
        val sizes = List(40) { 200_000L }                 // 8 MB
        val p = ReportUpload.plan(sizes, 500_000, 45 * mb, null)
        assertFalse(p.shrink)
        assertTrue(p.all)
        assertEquals(40, p.picked.size)
        assertTrue(p.bytes <= 45 * mb)
    }

    @Test fun shrinkFirstThenEveryNth() {
        val sizes = List(600) { 150_000L }                // 90 MB = 10 minut po snímku za sekundu
        val limit = 45 * mb
        assertFalse(ReportUpload.plan(sizes, 500_000, limit, null).all)
        // zmenšené na ~45 % se vejdou celé
        val small = ReportUpload.plan(sizes, 500_000, limit, 0.45)
        assertTrue(small.shrink)
        assertTrue(small.all)
        // zmenšené jen na 70 % → každý druhý
        val half = ReportUpload.plan(sizes, 500_000, limit, 0.7)
        assertEquals(2, half.step)
        assertEquals(300, half.picked.size)
        assertEquals(listOf(0, 2, 4), half.picked.take(3))
        assertTrue(half.bytes <= limit - ReportUpload.MARGIN)
    }

    @Test fun planNeverExceedsLimit() {
        val sizes = (1..300).map { (50_000L + (it * 7919L % 400_000L)) }      // nerovnoměrné velikosti
        for (limit in listOf(5 * mb, 10 * mb, 20 * mb, 45 * mb)) for (ratio in listOf(null, 0.3, 0.8)) {
            val p = ReportUpload.plan(sizes, 300_000, limit, ratio)
            val cost = p.picked.sumOf { Math.ceil(sizes[it] * (ratio ?: 1.0)).toLong() + ReportUpload.ENTRY_OVERHEAD }
            assertTrue("$limit $ratio", 300_000 + cost <= limit - ReportUpload.MARGIN)
            assertTrue(p.picked.all { it % p.step == 0 })
            // o krok méně by se nevešlo
            if (p.step > 1 && p.picked.isNotEmpty()) {
                val denser = sizes.indices.filter { it % (p.step - 1) == 0 }
                    .sumOf { Math.ceil(sizes[it] * (ratio ?: 1.0)).toLong() + ReportUpload.ENTRY_OVERHEAD }
                assertTrue(300_000 + denser > limit - ReportUpload.MARGIN)
            }
        }
    }

    @Test fun nothingFitsOrNoSnaps() {
        // hlášení samo zabere skoro celý limit → žádný snímek, pošle se jen hlášení a index
        val p = ReportUpload.plan(List(10) { 2 * mb }, 45 * mb - 100_000, 45 * mb, 0.5)
        assertTrue(p.picked.isEmpty())
        // jeden obří snímek
        assertTrue(ReportUpload.plan(listOf(60 * mb), 0, 45 * mb, null).picked.isEmpty())
        val none = ReportUpload.plan(emptyList(), 1000, 45 * mb, null)
        assertTrue(none.picked.isEmpty())
        assertTrue(none.all)
    }

    @Test fun shrinkRatioFromSamples() {
        assertNull(ReportUpload.ratio(emptyList(), emptyList()))
        assertNull(ReportUpload.ratio(listOf(0L), listOf(0L)))
        assertEquals(0.4 * 1.15, ReportUpload.ratio(listOf(100_000L, 200_000L), listOf(40_000L, 80_000L))!!, 1e-9)
        assertEquals(1.0, ReportUpload.ratio(listOf(100L), listOf(500L))!!, 1e-9)
    }

    @Test fun hardCapWhilePacking() {
        assertTrue(ReportUpload.fits(10 * mb, 1 * mb, 45 * mb))
        assertFalse(ReportUpload.fits(45 * mb - 10_000, 100_000, 45 * mb))
    }

    @Test fun infoJsonIsValidAndPlain() {
        val j = ReportUpload.infoJson(listOf(
            "verze" to "0.97.67", "verzeKod" to 164, "jeTv" to true, "tvVerze" to null,
            "zarizeni" to "Sony \"BRAVIA\"\nVH2", "pomer" to 0.5,
        ))
        val o = MiniJson.obj(j)
        assertNotNull(j, o)
        assertEquals("0.97.67", o!!["verze"])
        assertEquals(164L, o["verzeKod"])
        assertEquals(true, o["jeTv"])
        assertTrue(o.containsKey("tvVerze") && o["tvVerze"] == null)
        assertEquals("Sony \"BRAVIA\"\nVH2", o["zarizeni"])
        assertEquals(0.5, o["pomer"])
    }

    @Test fun offeredBatchesNewestFirstWithoutRecording() {
        fun i(n: Int, snaps: Int) = SnapBatches.Info(n, SnapBatches.dirName(n, "21300$n"), snaps, 1000, 0, 0)
        val list = listOf(i(1, 5), i(2, 0), i(3, 8), i(4, 3))
        assertEquals(listOf(3, 1), ReportUpload.offered(list, SnapBatches.dirName(4, "213004")).map { it.n })
        assertEquals(listOf(4, 3, 1), ReportUpload.offered(list, null).map { it.n })
    }

    @Test fun configLivesOnHelpPages() {
        assertEquals(HelpLinks.BASE + "hlaseni.json", ReportUpload.CONFIG_URL)
        assertTrue(ReportUpload.CONFIG_URL.startsWith("https://"))
    }
}
