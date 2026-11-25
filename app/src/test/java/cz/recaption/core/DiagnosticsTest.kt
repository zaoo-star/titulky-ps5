package cz.recaption.core

import org.junit.Assert.*
import org.junit.Test

/** Stav spojení ve třech úrovních – čas je simulovaný (ms od začátku). */
class LinkStatusTest {
    private fun ev(now: Long, link: Ps5.Link = Ps5.Link.CONNECTED, connectedAt: Long = 1_000, lastFrame: Long = 0,
                   running: Boolean = true, paused: Boolean = false, demo: Boolean = false) =
        LinkStatus.evaluate(now, running, paused, demo, link, connectedAt, lastFrame)

    @Test fun stoppedServiceIsOffOnAllLevels() {
        val s = ev(5_000, running = false, lastFrame = 4_999)
        assertEquals(LinkStatus.Stage.OFF, s.stage)
        assertFalse(s.service || s.console || s.image)
    }

    @Test fun runningServiceIsNotConnection() {
        for (l in listOf(Ps5.Link.OFF, Ps5.Link.CONNECTING)) {
            val s = ev(5_000, link = l, connectedAt = 0)
            assertEquals(LinkStatus.Stage.CONNECTING, s.stage)
            assertTrue(s.service); assertFalse(s.console); assertFalse(s.image)
        }
        assertEquals(LinkStatus.Stage.RETRYING, ev(5_000, link = Ps5.Link.WAITING).stage)
        val failed = ev(5_000, link = Ps5.Link.FAILED)
        assertEquals(LinkStatus.Stage.FAILED, failed.stage)
        assertTrue(failed.warning)
    }

    @Test fun connectionWithoutPictureWarnsAfterTenSeconds() {
        val waiting = ev(1_000 + 9_999)
        assertEquals(LinkStatus.Stage.WAIT_IMAGE, waiting.stage)
        assertTrue(waiting.console); assertFalse(waiting.image); assertFalse(waiting.warning)
        val none = ev(1_000 + 12_000)
        assertEquals(LinkStatus.Stage.NO_IMAGE, none.stage)
        assertEquals(12_000L, none.noImageMs)
        assertTrue(none.warning)
        assertTrue(LinkStatus.line(Lang.CS, none).contains("12 s"))
    }

    @Test fun frameFromPreviousConnectionDoesNotCount() {
        // obraz chodil v minulém spojení (900), nové se navázalo v 1000 → čeká se na nový
        val s = ev(2_000, lastFrame = 900)
        assertEquals(LinkStatus.Stage.WAIT_IMAGE, s.stage)
        assertNull(s.frameAgeMs)
    }

    @Test fun pictureArrivesStopsAndRecovers() {
        val ok = ev(3_000, lastFrame = 2_900)
        assertEquals(LinkStatus.Stage.IMAGE, ok.stage)
        assertTrue(ok.service && ok.console && ok.image)
        assertEquals(100L, ok.frameAgeMs)
        assertEquals(LinkStatus.Stage.IMAGE, ev(2_900 + LinkStatus.STALL_MS, lastFrame = 2_900).stage)
        val stalled = ev(2_900 + 8_000, lastFrame = 2_900)
        assertEquals(LinkStatus.Stage.STALLED, stalled.stage)
        assertEquals(8_000L, stalled.noImageMs)
        assertTrue(stalled.warning)
        assertTrue(LinkStatus.line(Lang.CS, stalled).contains("8 s"))
        assertTrue(LinkStatus.details(Lang.CS, stalled)[2].startsWith("✘"))
        assertEquals(LinkStatus.Stage.IMAGE, ev(11_000, lastFrame = 10_999).stage)
    }

    @Test fun pausedAndDemoAreNeverConsoleWarnings() {
        val p = ev(50_000, paused = true, lastFrame = 1_100)
        assertEquals(LinkStatus.Stage.PAUSED, p.stage)
        assertFalse(p.warning)
        val d = ev(5_000, link = Ps5.Link.OFF, connectedAt = 0, demo = true, lastFrame = 4_800)
        assertEquals(LinkStatus.Stage.DEMO, d.stage)
        assertTrue(d.image); assertFalse(d.console); assertFalse(d.warning)
    }

    @Test fun clockGoingBackDoesNotGiveNegativeAge() {
        val s = ev(2_000, lastFrame = 2_500, connectedAt = 1_000)
        assertEquals(0L, s.frameAgeMs)
    }

    @Test fun everyStageHasTextInEveryLanguageWithoutPlaceholderLeft() {
        val samples = listOf(
            ev(0, running = false), ev(0, paused = true), ev(0, demo = true), ev(0, link = Ps5.Link.CONNECTING),
            ev(0, link = Ps5.Link.WAITING), ev(0, link = Ps5.Link.FAILED), ev(2_000), ev(20_000),
            ev(3_000, lastFrame = 2_900), ev(30_000, lastFrame = 2_900),
        )
        assertEquals(LinkStatus.Stage.values().toSet(), samples.map { it.stage }.toSet())
        for (lang in Lang.uiLangs) for (s in samples) {
            val line = LinkStatus.line(lang, s)
            assertFalse("$lang ${s.stage}: $line", line.startsWith("ls_") || line.contains("%d"))
            for (d in LinkStatus.details(lang, s)) assertFalse("$lang $d", d.contains("lv_") || d.contains("%d"))
        }
    }
}

class CrashReportTest {
    private fun boom(): Throwable = try {
        try { error("vnitřní") } catch (e: Exception) { throw RuntimeException("venkovní", e) }
    } catch (e: Throwable) { e }

    @Test fun entryHasTimeVersionThreadAndReason() {
        val e = CrashReport.format("19. 11. 2026 10:00:00", "verze 0.71 (71)", "main", boom())
        val lines = e.lines()
        assertTrue(lines[0].contains("19. 11. 2026") && lines[0].contains("0.71") && lines[0].contains("main"))
        assertTrue(lines[1].contains("RuntimeException") && lines[1].contains("venkovní"))
        assertTrue(e.contains("Caused by") && e.contains("vnitřní"))
        assertEquals(2, CrashReport.summary(e).lines().size)
    }

    @Test fun keepsOnlyLastFewCrashes() {
        var file: String? = null
        for (i in 1..5) file = CrashReport.append(file, "pád $i\nstopa")
        val all = CrashReport.entries(file)
        assertEquals(CrashReport.KEEP, all.size)
        assertEquals("pád 5\nstopa", CrashReport.last(file))
        assertTrue(all.first().startsWith("pád 3"))
        assertNull(CrashReport.last(null))
        assertNull(CrashReport.last(""))
    }

    @Test fun longStackIsShortenedButDeepCauseKept() {
        fun deep(n: Int): Nothing = if (n == 0) throw IllegalStateException("dno") else deep(n - 1)
        val e = try { try { deep(60) } catch (x: Exception) { throw RuntimeException("obal", x) } } catch (t: Throwable) { t }
        val text = CrashReport.format("t", "v", "vlákno", e)
        assertTrue(text.lines().size <= 33)
        assertTrue(text.contains("Caused by") && text.contains("dno"))
    }

    @Test fun crashTextIsRedacted() {
        val e = IllegalStateException("token=abcDEF1234567890ghijKLMN account 1234567890123456789")
        val text = CrashReport.format("t", "v", "main", e)
        assertFalse(text.contains("abcDEF1234567890ghijKLMN"))
        assertFalse(text.contains("1234567890123456789"))
    }
}

class RedactTest {
    @Test fun knownSecretsAreRemoved() {
        val text = "účet AQIDBAUGBwg= klíč 1a2b3c4d PIN 4321 host 192.168.1.20"
        val out = Redact.clean(text, listOf("AQIDBAUGBwg=", "1a2b3c4d", "4321", ""))
        assertFalse(out.contains("AQIDBAUGBwg=") || out.contains("1a2b3c4d") || out.contains("4321"))
        assertTrue(out.contains("192.168.1.20"))
    }

    @Test fun typicalSecretShapesAreRemovedWithoutKnowingThem() {
        val cases = mapOf(
            "redirect?code=v3.AbCdEf&cid=1" to "v3.AbCdEf",
            "access_token: eyJhbGciOiJIUzI1NiJ9xyz" to "eyJhbGciOiJIUzI1NiJ9xyz",
            "npsso=Qw9XrT5aZ2Lp7Mn0Bc4Vd8Ke1Hs6Jg3Fy" to "Qw9XrT5aZ2Lp7Mn0Bc4Vd8Ke1Hs6Jg3Fy",
            "regist key 31613262336334640000000000000000" to "31613262336334640000000000000000",
            "účet 6153468327654321987" to "6153468327654321987",
            "id AQIDBAUGBwg= ok" to "AQIDBAUGBwg=",
            "password: tajne123" to "tajne123",
            "PIN 1234" to "1234",
        )
        for ((text, secret) in cases) {
            val out = Redact.clean(text)
            assertFalse("$text → $out", out.contains(secret))
        }
    }

    @Test fun ordinaryDiagnosticLinesStayReadable() {
        val lines = listOf(
            "PS5|login pin requested",
            "LINK|CONNECTED||dec=c2.android.avc.decoder|1280x720|fed=1234|decoded=1200",
            "VIDEO|fed=55|decoded=50|1920x1080|dec=OMX.amlogic.avc|err=null",
            "PSN|account ok",
            "at cz.recaption.TranslatorService\$loop\$2.invokeSuspend(TranslatorService.kt:412)",
            "ReCaption verze 0.71 (71) · 19. 11. 2026 10:00:00",
            "vlákno DefaultDispatcher-worker-12",
            "PS5|connect 192.168.1.20 → SUCCESS",
        )
        for (l in lines) assertEquals(l, Redact.clean(l))
    }
}
