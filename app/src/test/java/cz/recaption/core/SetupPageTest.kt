package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nastavení TV z telefonu na jedné stránce (účet, číslo z PS5, DeepL) a rozcestník v průvodci. */
class SetupPageTest {
    private val t = PhoneLogin.Target("192.168.1.20", 8766, "abcd2345efgh6789")

    private fun page(lang: Lang, target: PhoneLogin.Target? = t) = SetupPage.page(lang, "Sony BRAVIA", target,
        "https://example.com/ReCaption.apk", "https://sony.example/login?a=1&b=2", "https://www.deepl.com/pro#api",
        "https://www.deepl.com/your-account/keys", "https://recaption.pages.dev/zkratka.html")

    @Test fun pageIsTranslatedAndComplete() {
        for (lang in Lang.uiLangs) {
            val html = page(lang)
            // žádný nepřeložený klíč v textu stránky ani v textech pro skript
            assertFalse("$lang nepřeložený klíč", Regex("\\b(sp|wp|dl|rs|acc|wz)_[a-z0-9_]+").containsMatchIn(
                html.substringAfter("</style>").substringBefore("<script type=")))
            for (k in listOf("sp_1_h", "sp_3_h", "sp_done", "acc_legend", "wp_login_btn", "dl_tv_1", "sp_shortcut"))
                assertTrue("$lang $k", html.contains(PhoneLogin.esc(I18n.t(lang, k)).replace("\n", "")) || html.contains(I18n.t(lang, k).substringBefore("\n")))
            // tři části: účet (adresa redirect), číslo z PS5, klíč DeepL – a stav z TV
            assertTrue(html.contains("id=\"u\"") && html.contains("id=\"pin\"") && html.contains("id=\"dk\""))
            assertTrue(html.contains("op=info") && html.contains("op=ucet") && html.contains("op=kod") && html.contains("op=deepl"))
            // Android: ReCaption v telefonu na tutéž stránku TV (stejný klíč), bez aplikace stažení
            assertTrue("$lang odkaz do aplikace", html.contains("intent://login?h=192.168.1.20&amp;p=8766&amp;k=abcd2345efgh6789&amp;l=${lang.code}"))
            assertTrue(html.contains("href=\"https://sony.example/login?a=1&amp;b=2\""))
            // texty pro skript se zástupným jménem
            assertTrue(I18n.t(lang, "sp_acc_ok").contains("%s") && I18n.t(lang, "sp_ps5_h").contains("%s") &&
                I18n.t(lang, "sp_ps5_how").contains("%s") && I18n.t(lang, "sp_pair_fail").contains("%s") && I18n.t(lang, "sp_pair_ok").contains("%s"))
        }
        // bez adresy TV v domácí síti se odkaz do aplikace nenabízí
        assertFalse(page(Lang.CS, null).contains("intent://"))
    }

    @Test fun waitBarsUnderStatesThatWait() {
        val html = page(Lang.CS)
        // slabý běžící pruh pod stavem účtu, spárování a DeepL – skrytý, ukáže ho skript, dokud se čeká
        for (id in listOf("accw", "pairw", "dlw")) assertTrue(id, html.contains("<div class=\"wb\" id=\"$id\"><i></i></div>"))
        assertTrue(html.contains(".wb{display:none") && html.contains("@-webkit-keyframes wb") && html.contains("@keyframes wb"))
        assertTrue(html.contains("#f2c14e") && html.contains("opacity:.45"))
        // spárování běží (stav z TV nebo právě odeslané číslo) → pruh pod stavem spárování
        assertTrue(html.contains("bar('pairw',busy)") && html.contains("busy=s.pair=='busy'"))
        // pruh hned pod svým stavovým řádkem
        for ((st, bar) in listOf("accst" to "accw", "pairst" to "pairw", "dlst" to "dlw"))
            assertTrue(bar, html.contains("id=\"$st\" class=\"st wait\">") && html.indexOf("id=\"$bar\"") > html.indexOf("id=\"$st\""))
    }

    @Test fun scriptTextsCannotBreakOutOfTheirBlock() {
        val html = page(Lang.CS)
        val json = html.substringAfter("<script type=\"application/json\" id=\"t\">").substringBefore("</script>")
        assertFalse(json.contains("</"))
        assertTrue(MiniJson.obj(json.replace("<\\/", "</"))!!.containsKey("sp_pair_ok"))
    }

    @Test fun statusForThePage() {
        assertEquals("{\"acc\":\"Hrac\",\"pair\":\"ok\",\"ps5\":\"PS5-151\",\"deepl\":true}",
            SetupPage.status(SetupPage.Status("Hrac", SetupPage.Pair.OK, "PS5-151", true)))
        assertEquals("{\"acc\":null,\"pair\":\"idle\",\"ps5\":null,\"deepl\":false}",
            SetupPage.status(SetupPage.Status(null, SetupPage.Pair.IDLE, null, false)))
        val s = MiniJson.obj(SetupPage.status(SetupPage.Status("a\"b", SetupPage.Pair.BUSY, null, false)))!!
        assertEquals("a\"b", s["acc"])
    }

    @Test fun cameraAndAppLinks() {
        // čtečka v telefonu: odkaz na tutéž TV, zpět se přijme jen adresa domácí sítě
        val scan = SetupPage.scanLink(t)
        assertEquals("recaption://kod?h=192.168.1.20&p=8766&k=abcd2345efgh6789", scan)
        assertEquals(t, SetupPage.parseScanLink(scan))
        assertNull(SetupPage.parseScanLink("recaption://kod?h=8.8.8.8&p=8766&k=abcd2345efgh6789"))
        assertNull(SetupPage.parseScanLink("recaption://login?h=192.168.1.20&p=8766&k=abcd2345efgh6789"))
        assertNull(SetupPage.parseScanLink(null))
        // prohlížeč Androidu: aplikace je → otevře se, není → stáhne se
        assertEquals("intent://kod?h=1#Intent;scheme=recaption;package=cz.recaption;S.browser_fallback_url=https%3A%2F%2Fx.cz%2Fa.apk;end",
            SetupPage.intentLink("recaption://kod?h=1", "https://x.cz/a.apk"))
        val html = SetupPage.page(Lang.CS, "TV", t, "https://x.cz/a.apk", "https://sony.example/", "https://d/", "https://k/",
            appModeLink = "recaption://tv?h=192.168.1.20&p=8766&k=abcd2345efgh6789&n=TV")
        assertTrue(html.contains("intent://tv?h=192.168.1.20&amp;p=8766"))
        assertTrue(html.contains("intent://kod?h=192.168.1.20&amp;p=8766"))
        // hotové části jde znovu otevřít („Změnit“, „Spárovat znovu“)
        assertTrue(html.contains("id=\"accx\"") && html.contains("id=\"pairx\"") && html.contains("id=\"dlx\""))
        assertTrue(html.contains(PhoneLogin.esc(I18n.t(Lang.CS, "sp_where_h"))))
    }

    @Test fun pinFromThePhone() {
        assertEquals(12345678, SetupPage.pin("1234 5678"))
        assertEquals(12345678, SetupPage.pin("1234-5678"))
        assertNull(SetupPage.pin("1234567"))
        assertNull(SetupPage.pin("abc"))
        assertNull(SetupPage.pin(null))
    }
}
