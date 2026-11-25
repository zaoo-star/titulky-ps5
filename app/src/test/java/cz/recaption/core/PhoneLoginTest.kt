package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

/** Přihlášení účtem přes telefon: adresy, stránka z televize a posílání účtu po domácí síti. */
class PhoneLoginTest {
    private val id = Ps5.accountIdToBase64(ByteArray(8) { (it + 1).toByte() })

    @Test fun keyAndLinksRoundTrip() {
        val key = PhoneLogin.newKey()
        assertTrue(key, PhoneLogin.validKey(key))
        val t = PhoneLogin.Target("192.168.1.20", 8765, key)
        assertEquals("http://192.168.1.20:8765/p/$key", PhoneLogin.pageUrl(t))
        val link = PhoneLogin.appLink(t, Lang.DE)
        assertEquals(t, PhoneLogin.parseAppLink(link))
        assertEquals(Lang.DE, PhoneLogin.langFromAppLink(link))
        assertNull(PhoneLogin.langFromAppLink(PhoneLogin.appLink(t)))
        // odkaz z vlastní aplikace v tomtéž telefonu: značka navíc, cíl a jazyk stejné
        val self = PhoneLogin.appLink(t, Lang.CS, self = true)
        assertEquals(t, PhoneLogin.parseAppLink(self))
        assertEquals(Lang.CS, PhoneLogin.langFromAppLink(self))
        assertTrue(PhoneLogin.selfFromAppLink(self))
        assertFalse(PhoneLogin.selfFromAppLink(link))
        assertFalse(PhoneLogin.selfFromAppLink(null))
    }

    @Test fun appLinkAcceptsOnlyHomeNetwork() {
        fun link(h: String, p: String = "8765", k: String = "abcd2345") = "recaption://login?h=$h&p=$p&k=$k"
        assertNotNull(PhoneLogin.parseAppLink(link("10.0.2.15")))
        assertNotNull(PhoneLogin.parseAppLink(link("172.20.1.1")))
        assertNull(PhoneLogin.parseAppLink(link("8.8.8.8")))               // internet
        assertNull(PhoneLogin.parseAppLink(link("172.32.0.1")))            // už mimo domácí rozsah
        assertNull(PhoneLogin.parseAppLink(link("evil.example.com")))
        assertNull(PhoneLogin.parseAppLink(link("192.168.1.20", p = "0")))
        assertNull(PhoneLogin.parseAppLink(link("192.168.1.20", p = "70000")))
        assertNull(PhoneLogin.parseAppLink(link("192.168.1.20", k = "../x")))
        assertNull(PhoneLogin.parseAppLink("https://login?h=192.168.1.20&p=8765&k=abcd2345"))
        assertNull(PhoneLogin.parseAppLink(null))
        assertFalse(PhoneLogin.isHomeAddress("192.168.1"))
        assertFalse(PhoneLogin.isHomeAddress("192.168.1.256"))
    }

    @Test fun requestParsing() {
        assertEquals("GET" to "/p/abc", PhoneLogin.requestLine("GET /p/abc?x=1 HTTP/1.1"))
        assertEquals("POST" to "/p/abc", PhoneLogin.requestLine("post /p/abc HTTP/1.0"))
        assertNull(PhoneLogin.requestLine("nesmysl"))
        assertNull(PhoneLogin.requestLine("GET /p/abc"))
        assertEquals(42, PhoneLogin.contentLength(listOf("Host: x", "Content-Length: 42")))
        assertEquals(0, PhoneLogin.contentLength(listOf("Host: x")))
        val values = mapOf("url" to "https://a/redirect?code=v3.Ab12Cd&cid=0", "name" to "Pepa & spol. ž")
        assertEquals(values, PhoneLogin.parseForm(PhoneLogin.form(values)))
        assertEquals(mapOf("a" to "1"), PhoneLogin.parseForm("a=1&&%zz=2"))
    }

    @Test fun accountsAreRememberedWithoutPassword() {
        val a = PhoneLogin.Account(id, "Druhý účet | test")
        val b = PhoneLogin.cleanAccount("1234567890123456789", "Herní")!!
        var list = PhoneLogin.withAccount(emptyList(), PhoneLogin.cleanAccount(a.id, a.name)!!)
        list = PhoneLogin.withAccount(list, b)
        list = PhoneLogin.withAccount(list, PhoneLogin.cleanAccount(a.id, "Přejmenovaný")!!)
        assertEquals(listOf("Přejmenovaný", "Herní"), list.map { it.name })
        assertEquals(list, PhoneLogin.parseAccounts(PhoneLogin.serializeAccounts(list)))
        assertEquals(listOf(b), PhoneLogin.withoutAccount(list, a.id))
        assertTrue(PhoneLogin.parseAccounts(null).isEmpty())
        assertTrue(PhoneLogin.parseAccounts("nesmysl|???\n\n").isEmpty())
        assertNull(PhoneLogin.cleanAccount("abc", "x"))
        assertEquals("Druhý účet  test", PhoneLogin.cleanAccount(a.id, a.name)!!.name)   // svislítko odděluje pole
        var many = emptyList<PhoneLogin.Account>()
        for (i in 0 until 9) many = PhoneLogin.withAccount(many, PhoneLogin.cleanAccount((1000000000L + i).toString(), "u$i")!!)
        assertEquals(PhoneLogin.MAX_ACCOUNTS, many.size)
        assertEquals("u8", many.first().name)
    }

    @Test fun accountNameFromSonyReply() {
        assertEquals("Pepa_Druhy", PhoneLogin.nameFromUserInfo("{\"user_id\":\"123\",\"online_id\":\"Pepa_Druhy\",\"is_sub_account\":false}"))
        assertNull(PhoneLogin.nameFromUserInfo("{\"user_id\":\"123\"}"))
    }

    @Test fun pageIsTranslatedAndSafe() {
        val t = PhoneLogin.Target("192.168.1.20", 8765, "abcd2345")
        for (lang in Lang.uiLangs) {
            val ask = PhoneLogin.page(lang, PhoneLogin.Page.ASK, t.key, t, "https://sony.example/login?a=1&b=2", "https://example.com/ReCaption.apk")
            assertTrue("$lang formulář", ask.contains("action=\"/p/abcd2345\"") && ask.contains("name=\"url\""))
            assertTrue("$lang přihlášení", ask.contains("href=\"https://sony.example/login?a=1&amp;b=2\""))
            // stránka si přihlášení otevírá sama (aby kartu po úspěchu mohla zavřít) a odesílá na pozadí
            assertTrue("$lang chování stránky", ask.contains("id=\"l\"") && ask.contains("window.sonyTab") && ask.contains("XMLHttpRequest"))
            // Android: odkaz otevře ReCaption v telefonu, bez něj prohlížeč stáhne aplikaci (od 0.97.57)
            assertTrue("$lang odkaz do aplikace", ask.contains("intent://login?h=192.168.1.20&amp;p=8765&amp;k=abcd2345&amp;l=${lang.code}" +
                "#Intent;scheme=recaption;package=cz.recaption;S.browser_fallback_url=https%3A%2F%2Fexample.com%2FReCaption.apk;end"))
            // aplikace je na stránce první, přihlášení v prohlížeči je záloha (sbalitelná, bez skriptu rozbalená)
            assertTrue("$lang pořadí", ask.indexOf("id=\"app\"") in 0 until ask.indexOf("id=\"web\" open"))
            assertTrue("$lang texty", ask.contains(PhoneLogin.esc(I18n.t(lang, "wp_send"))) && ask.contains(PhoneLogin.esc(I18n.t(lang, "wp_app_btn"))))
            for (state in PhoneLogin.Page.values()) {
                val html = PhoneLogin.page(lang, state, t.key, t, "https://sony.example/", "https://example.com/a.apk")
                assertFalse("$lang/$state nepřeložený klíč", Regex("\\b(wp|hp|pa)_[a-z0-9_]+").containsMatchIn(html.substringAfter("</style>").substringBefore("<script>")))
            }
            assertTrue(PhoneLogin.page(lang, PhoneLogin.Page.DONE, t.key, t, "x", "y").contains(PhoneLogin.esc(I18n.t(lang, "wp_ok"))))
        }
        // kartu Sony smí stránka zavřít jen po úspěchu: značka úspěchu je jen ve stránce „hotovo“
        for (state in listOf(PhoneLogin.Page.ASK, PhoneLogin.Page.BAD, PhoneLogin.Page.GONE))
            assertFalse("$state", PhoneLogin.page(Lang.CS, state, t.key, t, "x", "y").contains("class=\"ok\""))
        assertTrue(PhoneLogin.page(Lang.CS, PhoneLogin.Page.DONE, t.key, t, "x", "y").contains("class=\"ok\""))
        // bez adresy televize v domácí síti se odkaz do aplikace nenabízí, formulář ale funguje
        val noApp = PhoneLogin.page(Lang.CS, PhoneLogin.Page.BAD, t.key, null, "x", "y")
        assertFalse(noApp.contains("intent://") || noApp.contains("recaption://"))
        assertTrue(noApp.contains("action=\"/p/abcd2345\"") && noApp.contains(I18n.t(Lang.CS, "wp_bad")))
        assertEquals("&lt;b&gt;&amp;&quot;", PhoneLogin.esc("<b>&\""))
    }

    @Test fun tvPageTakesAddressFromBrowserAndAccountFromApp() {
        val codes = CopyOnWriteArrayList<String>()
        val accounts = CopyOnWriteArrayList<PhoneLogin.Account>()
        val server = LoginServer(
            lang = { Lang.CS }, loginUrl = "https://sony.example/login", apkUrl = { "https://example.com/ReCaption.apk" },
            resolveCode = { code -> codes.add(code); if (code == "v3.Ab12Cd") PhoneLogin.Account(id, "Pepa_Druhy") else null },
            onAccount = { accounts.add(it) },
        )
        assertTrue(server.start(0, InetAddress.getLoopbackAddress()))
        try {
            val host = "127.0.0.1"
            val path = PhoneLogin.path(server.key)
            // stránka s postupem
            val page = LoginClient.request(host, server.port, "GET", path)
            assertEquals(200, page.first)
            assertTrue(page.second.contains("name=\"url\"") && page.second.contains("https://sony.example/login"))
            // cizí klíč nic nedostane a nic nezmění
            assertEquals(404, LoginClient.request(host, server.port, "GET", PhoneLogin.path("zzzz2222")).first)
            assertEquals(404, LoginClient.request(host, server.port, "POST", PhoneLogin.path("zzzz2222"), PhoneLogin.form(mapOf("account" to id, "app" to "1"))).first)
            assertEquals(405, LoginClient.request(host, server.port, "DELETE", path).first)
            assertTrue(accounts.isEmpty())
            // vložená adresa bez kódu a s kódem, který Sony nezná → stránka s chybou
            for (bad in listOf("https://example.com/", "https://remoteplay.dl.playstation.net/remoteplay/redirect?code=stary&cid=1")) {
                val r = LoginClient.request(host, server.port, "POST", path, PhoneLogin.form(mapOf("url" to bad)))
                assertEquals(200, r.first)
                assertTrue(r.second.contains(I18n.t(Lang.CS, "wp_bad")))
            }
            assertEquals(listOf("stary"), codes.toList())
            assertTrue(accounts.isEmpty())
            // vložená celá adresa bílé stránky → účet
            val ok = LoginClient.request(host, server.port, "POST", path,
                PhoneLogin.form(mapOf("url" to "https://remoteplay.dl.playstation.net/remoteplay/redirect?code=v3.Ab12Cd&cid=000")))
            assertEquals(200, ok.first)
            assertTrue(ok.second.contains(PhoneLogin.esc(I18n.t(Lang.CS, "wp_ok"))))
            assertEquals(listOf(PhoneLogin.Account(id, "Pepa_Druhy")), accounts.toList())
            // ReCaption v telefonu pošle rovnou číslo účtu (i jako dlouhé číslo – televize si ho převede)
            val target = PhoneLogin.Target(host, server.port, server.key)
            assertEquals(LoginClient.Result.OK, LoginClient.send(target, PhoneLogin.Account("1234567890123456789", "Herní\n účet")))
            assertEquals(2, accounts.size)
            assertEquals("Herní účet", accounts[1].name)
            assertNotNull(Ps5.parseAccountId(accounts[1].id))
            // nesmyslné číslo účtu a špatný klíč televize odmítne
            assertEquals(LoginClient.Result.REFUSED, LoginClient.send(target, PhoneLogin.Account("nic", "x")))
            assertEquals(LoginClient.Result.REFUSED, LoginClient.send(target.copy(key = "zzzz2222"), PhoneLogin.Account(id, "x")))
            assertEquals(2, accounts.size)
        } finally {
            server.stop()
        }
        assertFalse(server.running)
        assertEquals(LoginClient.Result.UNREACHABLE, LoginClient.send(PhoneLogin.Target("127.0.0.1", server.port, server.key), PhoneLogin.Account(id, "x"), 1500))
    }
}
