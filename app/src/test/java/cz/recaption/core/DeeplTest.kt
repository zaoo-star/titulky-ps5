package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pokus DeepL: vše bez sítě a bez skutečného klíče (klíče níž jsou vymyšlené). */
class DeeplTest {
    private val free = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0:fx"
    private val pro = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"

    @Test fun keyShapeAndEndpoint() {
        assertEquals(Deepl.KeyKind.FREE, Deepl.keyKind(free))
        assertEquals(Deepl.KeyKind.PRO, Deepl.keyKind(pro))
        assertNull(Deepl.keyKind(""))
        assertNull(Deepl.keyKind("abc:fx"))
        assertNull(Deepl.keyKind("https://www.deepl.com/your-account/keys"))
        assertNull(Deepl.keyKind("klíč s mezerou a diakritikou"))
        assertEquals("https://api-free.deepl.com/v2/translate", Deepl.translateUrl(free))
        assertEquals("https://api.deepl.com/v2/translate", Deepl.translateUrl(pro))
        assertEquals("https://api-free.deepl.com/v2/usage", Deepl.usageUrl(free))
        assertEquals("DeepL-Auth-Key $free", Deepl.authHeader(free))
    }

    @Test fun pastedKeyIsCleaned() {
        assertEquals(free, Deepl.cleanKey("  \"$free\"\n"))
        assertEquals(free, Deepl.cleanKey("DeepL-Auth-Key $free"))
        assertEquals(free, Deepl.cleanKey(free.substring(0, 10) + " " + free.substring(10)))
        assertEquals("", Deepl.cleanKey(null))
    }

    @Test fun maskedShowsOnlyEnds() {
        val m = Deepl.masked(free)
        assertTrue(m.startsWith("0f1e") && m.endsWith("f0:fx"))
        assertFalse(m.contains("4b5a"))
    }

    @Test fun everyTranslationLanguageHasDeeplCode() {
        // DeepL umí všech devět jazyků s texty aplikace a z jazyků jen pro překlad RO, SL, BG, LT, LV, ET, EL, TR;
        // chorvatština, srbština, makedonština, albánština a vlna 3 (CA, TL, MS, IS, SW, AF) zůstávají jen offline
        // hindština (jazyk aplikace od 24. 11. 2026): u DeepL neověřená → zatím jen offline
        for (l in Lang.targets.filter { it.ui }) assertTrue("$l", Deepl.target(l) != null)
        val deepl = listOf(Lang.RO, Lang.SL, Lang.BG, Lang.LT, Lang.LV, Lang.ET, Lang.EL, Lang.TR, Lang.NL, Lang.SV, Lang.DA, Lang.NB, Lang.FI, Lang.ID, Lang.VI, Lang.HE, Lang.TH,
            Lang.HR, Lang.MK, Lang.SQ, Lang.CA, Lang.TL, Lang.MS, Lang.IS, Lang.SW, Lang.AF)
        for (l in deepl) assertEquals(l.code.uppercase(), Deepl.target(l))
        val offline = listOf(Lang.SR)
        for (l in offline) assertNull("$l", Deepl.target(l))
        assertEquals(Lang.targets.toSet(), (Lang.targets.filter { it.ui } + deepl + offline).toSet())
        assertEquals("CS", Deepl.target(Lang.CS))
        assertEquals("UK", Deepl.target(Lang.UK))
        assertNull(Deepl.target(Lang.EN))
    }

    @Test fun requestJson() {
        val j = Deepl.requestJson(listOf("Hello \"there\"", "a\\b\nc"), "CS")
        val o = MiniJson.obj(j)!!
        assertEquals(listOf("Hello \"there\"", "a\\b\nc"), o["text"])
        assertEquals("EN", o["source_lang"])
        assertEquals("CS", o["target_lang"])
        assertEquals("prefer_less", o["formality"])
        assertEquals("xml", o["tag_handling"])
        assertEquals(listOf("k"), o["ignore_tags"])
    }

    @Test fun sourceKeepsGlossaryNamesAndOriginalEnglish() {
        val g = Glossary(names = listOf("Marek", "Jonas"))
        // původní text (žádné úpravy pro offline překladač), jména ve značce
        val raw = "Marek, we ain't got nothin' & no time."
        assertEquals("<k>Marek</k>, we ain't got nothin' &amp; no time.", Deepl.source(raw, g.prepare(raw)))
        // bez jmen jen XML
        assertEquals("a &lt; b", Deepl.source("a < b", null))
        assertEquals("Fine.", Deepl.source("Fine.", Glossary().prepare("Fine.")))
    }

    @Test fun sourceUsesTermTranslationWhenNotLiteral() {
        val g = Glossary(terms = mapOf("slow shot" to "Pomalá rána"))
        val raw = "Use Slow Shot now."
        assertEquals("Use <k>Pomalá rána</k> now.", Deepl.source(raw, g.prepare(raw)))
    }

    @Test fun parseTranslationsFromDocsSample() {
        val json = """{"translations":[{"detected_source_language":"EN","text":"Hallo, Welt!"},{"detected_source_language":"EN","text":"<k>Marek</k> &amp; ich"}]}"""
        assertEquals(listOf("Hallo, Welt!", "Marek & ich"), Deepl.parseTranslations(json, 2))
        assertNull(Deepl.parseTranslations(json, 1))
        assertNull(Deepl.parseTranslations("<html>502</html>", 1))
        assertNull(Deepl.parseTranslations("""{"message":"Quota Exceeded"}""", 1))
    }

    /** Skutečná odpověď DeepL (19. 11. 2026) na požadavek z [Deepl.requestJson] se jménem ve značce. */
    @Test fun realAnswerAndTykani() {
        val json = """{"translations":[{"text":"<k>Marek</k>u, potřebujeme peníze a potřebujeme je hned.","detected_source_language":"EN","tag_handling_version":"v2"},{"text":"Jste si tím jistí?","detected_source_language":"EN","tag_handling_version":"v2"}]}"""
        val t = Deepl.parseTranslations(json, 2)!!
        assertEquals("Mareku, potřebujeme peníze a potřebujeme je hned.", t[0])
        // DeepL v češtině tyká jen někdy (formality pro češtinu nemá) → Natural dál sjednocuje na tykání
        assertEquals("Jsi si tím jistý?", Natural.czech("Are you sure about this?", t[1]))
    }

    @Test fun parseUsageFromDocsSample() {
        val u = Deepl.parseUsage("""{"character_count":180118,"character_limit":1250000}""")!!
        assertEquals(180118L, u.count)
        assertEquals(1250000L - 180118L, u.left)
        assertEquals(1_000_000L, Deepl.parseUsage("""{"character_count":0,"character_limit":1000000}""")!!.left)
        assertEquals(0L, Deepl.Usage(600, 500).left)
        assertNull(Deepl.parseUsage("""{"message":"Forbidden"}"""))
    }

    @Test fun billedCharsWithoutTags() {
        assertEquals("Marek & me".length, Deepl.billedChars(listOf("<k>Marek</k> &amp; me")))
    }

    @Test fun statusToProblem() {
        assertNull(Deepl.problemFor(200))
        assertEquals(Deepl.Problem.BAD_KEY, Deepl.problemFor(403))
        assertEquals(Deepl.Problem.QUOTA, Deepl.problemFor(456))
        assertEquals(Deepl.Problem.RATE, Deepl.problemFor(429))
        assertEquals(Deepl.Problem.SERVER, Deepl.problemFor(503))
        assertEquals(Deepl.Problem.BAD_REPLY, Deepl.problemFor(400))
    }

    @Test fun everyProblemHasText() {
        for (p in Deepl.Problem.values()) assertTrue(p.name, I18n.keys.contains("dl_p_" + p.name.lowercase()))
    }

    @Test fun swapDecision() {
        val off = "Potřebujeme peníze, Jonasi."
        assertEquals(Deepl.Swap.YES, Deepl.decide(off, "Potřebujeme prachy, Jonasi.", true, 400))
        assertEquals(Deepl.Swap.SAME, Deepl.decide(off, "potřebujeme peníze Jonasi", true, 400))
        assertEquals(Deepl.Swap.LATE, Deepl.decide(off, "Potřebujeme prachy, Jonasi.", true, 1600))
        assertEquals(Deepl.Swap.STALE, Deepl.decide(off, "Potřebujeme prachy, Jonasi.", false, 300))
        assertEquals(Deepl.Swap.SAME, Deepl.decide(off, "  ", true, 300))
    }

    @Test fun logLineHasNoText() {
        assertEquals("DEEPL|ms=312|swap=yes|chars=27", Deepl.logLine(312, "yes", 27))
    }

    @Test fun backoff() {
        val b = DeeplBackoff()
        assertTrue(b.allowed(0))
        b.onProblem(Deepl.Problem.NETWORK, 0); b.onProblem(Deepl.Problem.NETWORK, 0)
        assertTrue(b.allowed(0))                                  // dvě chyby ještě nevadí
        b.onProblem(Deepl.Problem.NETWORK, 1000)
        assertFalse(b.allowed(1000))
        assertEquals(2L, b.minutesLeft(1000))
        assertTrue(b.allowed(1000 + 2 * DeeplBackoff.MINUTE))
        b.onProblem(Deepl.Problem.NETWORK, 0)                     // další chyba → delší pauza
        assertFalse(b.allowed(3 * DeeplBackoff.MINUTE))
        b.onSuccess()
        assertNull(b.lastProblem)

        val q = DeeplBackoff()
        q.onProblem(Deepl.Problem.QUOTA, 0)
        assertFalse(q.allowed(5 * DeeplBackoff.HOUR))
        assertTrue(q.allowed(6 * DeeplBackoff.HOUR))

        val k = DeeplBackoff()
        k.onProblem(Deepl.Problem.BAD_KEY, 0)
        assertFalse(k.allowed(100 * DeeplBackoff.HOUR))
        assertEquals(-1L, k.minutesLeft(0))
    }

    @Test fun backoffNeverLongerThan15Minutes() {
        val b = DeeplBackoff()
        repeat(20) { b.onProblem(Deepl.Problem.SERVER, 0) }
        assertTrue(b.allowed(15 * DeeplBackoff.MINUTE))
    }

    @Test fun stats() {
        val s = DeeplStats()
        s.answer(200, Deepl.Swap.YES, 30)
        s.answer(400, Deepl.Swap.LATE, 20)
        s.answer(300, Deepl.Swap.SAME, 10)
        s.fromCache()
        assertEquals(3, s.requests); assertEquals(300L, s.avgMs)
        assertEquals(1, s.improved); assertEquals(1, s.late); assertEquals(1, s.same); assertEquals(1, s.cached)
        assertEquals(60L, s.chars)
    }

    @Test fun redactRemovesDeeplKey() {
        val t = Redact.clean("Authorization: DeepL-Auth-Key $free a taky $pro a jiný-dlouhy-klic-1234567890:fx")
        assertFalse(t, t.contains("4b5a"))
        assertFalse(t, t.contains("1234567890"))
        assertFalse(t, t.contains(":fx"))
    }

    @Test fun phonePage() {
        val ask = DeeplPage.page(Lang.CS, DeeplPage.State.ASK, "/p/abcdefgh")
        assertTrue(ask.contains("action=\"/p/abcdefgh\""))
        assertTrue(ask.contains("name=\"key\""))
        // DeepL se otevírá obyčejným odkazem na nové kartě (iPhone i Android) ve třech krocích:
        // 1. ceník API (plán Developer), 2. stránka s klíči (ikona kopírování), 3. vložit klíč
        assertTrue(ask.contains("target=\"_blank\" rel=\"noopener\" href=\"${PhoneLogin.esc(Deepl.SIGNUP_URL)}\""))
        assertTrue(ask.contains("href=\"${Deepl.KEYS_URL}\""))
        // ceník na záložce API, ne přímá registrace (ta přihlášeného přesměruje do Translatoru)
        assertTrue(Deepl.SIGNUP_URL.endsWith("/pro#api"))
        assertTrue(ask.indexOf(I18n.t(Lang.CS, "dl_s1")) in 0 until ask.indexOf(I18n.t(Lang.CS, "dl_s2")))
        assertTrue(ask.indexOf(I18n.t(Lang.CS, "dl_s2")) < ask.indexOf(I18n.t(Lang.CS, "dl_s3_here")))
        assertTrue(ask.contains("API Keys &amp; Limits"))
        // vysvětlení registrace (plán Developer, karta, Security check) je jen tady – televize ukazuje jen krátké kroky
        assertTrue(ask.contains(PhoneLogin.esc(I18n.t(Lang.CS, "dl_howto"))))
        assertTrue(ask.contains("Security check"))
        assertFalse(ask.contains("kartu nechce"))
        assertTrue(ask.contains(I18n.t(Lang.CS, "dl_privacy")))
        val done = DeeplPage.page(Lang.EN, DeeplPage.State.DONE, "/p/x", "<b>")
        assertTrue(done.contains("&lt;b&gt;"))
    }

    @Test fun phonePageThroughLoginServer() {
        var got: String? = null
        val server = LoginServer.forPage({ Lang.CS }, object : LoginServer.Custom {
            override fun get(path: String) = DeeplPage.page(Lang.CS, DeeplPage.State.ASK, path)
            override fun gone() = DeeplPage.page(Lang.CS, DeeplPage.State.GONE, "")
            override fun post(path: String, form: Map<String, String>): String {
                got = Deepl.cleanKey(form["key"])
                return DeeplPage.page(Lang.CS, DeeplPage.State.DONE, path)
            }
        })
        assertTrue(server.start(0, java.net.InetAddress.getLoopbackAddress()))
        try {
            val (st, body) = LoginClient.request("127.0.0.1", server.port, "GET", PhoneLogin.path(server.key))
            assertEquals(200, st); assertTrue(body.contains("name=\"key\""))
            val (st2, _) = LoginClient.request("127.0.0.1", server.port, "POST", PhoneLogin.path(server.key), PhoneLogin.form(mapOf("key" to " $free ")))
            assertEquals(200, st2); assertEquals(free, got)
            val (st3, _) = LoginClient.request("127.0.0.1", server.port, "GET", PhoneLogin.path("wrongkey"))
            assertEquals(404, st3)
        } finally { server.stop() }
    }
}
