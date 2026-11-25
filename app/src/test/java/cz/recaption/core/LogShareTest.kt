package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.Socket

/** Záznam pro vývojáře: záznam z televize (TvLog) a jeho zpřístupnění v domácí síti (LogShare, LogServer). */
class LogShareTest {

    // ---------------- záznam z televize ----------------

    @Test fun tvLogMessageIsParsedAndCleaned() {
        assertEquals(listOf("12:00:01 start", "a b"), TvLog.parse("{\"typ\":\"zaznam\",\"radky\":[\"12:00:01 start\",\"a\\u001b\\nb\",\"\",3]}"))
        assertNull(TvLog.parse("{\"typ\":\"hotovo\",\"id\":1}"))
        assertNull(TvLog.parse("nesmysl"))
        assertEquals(emptyList<String>(), TvLog.parse("{\"typ\":\"zaznam\"}"))
        assertEquals(TvLog.MAX_LINE, TvLog.cleanLine("x".repeat(1000)).length)
        val many = (1..500).joinToString(",", "[", "]") { "\"r$it\"" }
        assertEquals(TvLog.MAX_LINES_PER_MESSAGE, TvLog.parse("{\"typ\":\"zaznam\",\"radky\":$many}")!!.size)
    }

    @Test fun tvLogKeepsOnlyTheEndByWholeLines() {
        var all = ""
        for (i in 1..100) all = TvLog.merge(all, listOf("řádek číslo $i"), max = 200)
        assertTrue(all.length <= 200)
        assertTrue(all.endsWith("řádek číslo 100\n"))
        assertTrue(all.lines().first().startsWith("řádek číslo "))   // nezačíná useknutým řádkem
        assertEquals("řádek číslo 99\nřádek číslo 100", TvLog.tail(all, 2))
        assertEquals("", TvLog.tail(null, 5))
    }

    @Test fun tvLogFromTvIsStoredToFile() {
        val f = File.createTempFile("tv-zaznam", ".txt").apply { delete() }
        val local = InetAddress.getByName("127.0.0.1")
        val sender = TvSender()
        TvLog.file = f
        assertTrue(sender.start(0, local))
        try {
            Socket(local, sender.port).use { s ->
                s.soTimeout = 5000
                s.getOutputStream().apply {
                    write(("GET / HTTP/1.1\r\nHost: tv\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray())
                    write(clientFrame("{\"typ\":\"zaznam\",\"radky\":[\"10:00:00 start ReCaption webos 1.2.0\",\"10:00:02 připojeno\"]}"))
                    flush()
                }
                val end = System.currentTimeMillis() + 5000
                while (!TvLog.read().contains("připojeno") && System.currentTimeMillis() < end) Thread.sleep(10)
                assertEquals("10:00:00 start ReCaption webos 1.2.0\n10:00:02 připojeno\n", TvLog.read())
            }
        } finally { sender.stop(); TvLog.file = null; f.delete() }
    }

    private fun clientFrame(text: String): ByteArray {
        val data = text.toByteArray(Charsets.UTF_8)
        val mask = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val out = ByteArrayOutputStream()
        out.write(0x81)
        if (data.size < 126) out.write(0x80 or data.size) else { out.write(0x80 or 126); out.write(data.size shr 8); out.write(data.size and 0xff) }
        out.write(mask)
        for (i in data.indices) out.write(data[i].toInt() xor mask[i % 4].toInt())
        return out.toByteArray()
    }

    // ---------------- kód, požadavky, adresy, čas ----------------

    @Test fun codeIsShortRandomAndCheckedExactly() {
        val a = LogShare.newCode(java.util.Random(1))
        assertEquals(6, a.length)
        assertTrue(LogShare.validCode(a))
        assertFalse(LogShare.validCode("ab1"))          // krátký, a 1 v abecedě kódu není
        assertFalse(LogShare.validCode(null))
        assertTrue(LogShare.sameCode("abc234", "abc234"))
        assertFalse(LogShare.sameCode("abc234", "abc235"))
        assertFalse(LogShare.sameCode("abc234", "abc2345"))
        assertFalse(LogShare.sameCode(null, "abc234"))
    }

    @Test fun requestsAreRouted() {
        val k = "abc234"
        assertEquals(LogShare.Route.TEXT, LogShare.route("GET /zaznam?k=abc234 HTTP/1.1", k))
        assertEquals(LogShare.Route.TEXT, LogShare.route("GET /zaznam.txt?x=1&k=abc234 HTTP/1.1", k))
        assertEquals(LogShare.Route.JSON, LogShare.route("GET /zaznam.json?k=abc234 HTTP/1.1", k))
        assertEquals(LogShare.Route.IMAGE, LogShare.route("GET /snimek.jpg?k=abc234 HTTP/1.1", k))
        assertEquals(LogShare.Route.BAD_CODE, LogShare.route("GET /snimek.jpg?k=zzz234 HTTP/1.1", k))
        assertEquals(LogShare.Route.BAD_CODE, LogShare.route("GET /zaznam?k=abc235 HTTP/1.1", k))
        assertEquals(LogShare.Route.BAD_CODE, LogShare.route("GET /zaznam HTTP/1.1", k))
        assertEquals(LogShare.Route.BAD_METHOD, LogShare.route("POST /zaznam?k=abc234 HTTP/1.1", k))
        assertEquals(LogShare.Route.NOT_FOUND, LogShare.route("GET /favicon.ico HTTP/1.1", k))
        assertEquals(LogShare.Route.NOT_FOUND, LogShare.route("GET /zaznam/../x?k=abc234 HTTP/1.1", k))
        assertEquals(LogShare.Route.BAD_REQUEST, LogShare.route("nesmysl", k))
        assertEquals(mapOf("k" to "a b", "x" to "ž"), LogShare.query("/z?k=a+b&x=%C5%BE#kotva"))
    }

    @Test fun onlyHomeNetworkMayAsk() {
        assertTrue(LogShare.allowedPeer("192.168.1.5"))
        assertTrue(LogShare.allowedPeer("10.0.0.2"))
        assertTrue(LogShare.allowedPeer("127.0.0.1"))
        assertTrue(LogShare.allowedPeer("::ffff:192.168.0.3"))
        assertFalse(LogShare.allowedPeer("8.8.8.8"))
        assertFalse(LogShare.allowedPeer("2001:db8::1"))
        assertFalse(LogShare.allowedPeer(null))
    }

    @Test fun turnsOffAfterTwoHours() {
        val start = 1_000_000L
        val until = start + LogShare.DURATION_MS
        assertTrue(LogShare.active(until, start))
        assertTrue(LogShare.active(until, until - 1))
        assertFalse(LogShare.active(until, until))
        assertFalse(LogShare.active(until, start - 1000))          // hodiny šly pozpátku o víc než celou dobu
        assertFalse(LogShare.active(0, start))
        assertEquals(120, LogShare.minutesLeft(until, start))
        assertEquals(1, LogShare.minutesLeft(until, until - 1))
        assertEquals(0, LogShare.remainingMs(until, until + 5))
        assertEquals("http://192.168.1.5:8789/zaznam?k=abc234", LogShare.url("192.168.1.5", 8789, "abc234"))
    }

    @Test fun reportIsRedacted() {
        val parts = listOf("aplikace" to "ReCaption 0.75\nrefresh_token=M.C123_abcdefghijklmnopqrstuv", "spojení" to "účet 1234567890123456789 a \"uvozovky\"")
        val text = LogShare.text(parts, listOf("tajne-heslo"))
        assertTrue(text.startsWith("=== aplikace ===\nReCaption 0.75\n"))
        assertFalse(text.contains("M.C123"))
        assertFalse(text.contains("1234567890123456789"))
        val json = LogShare.json(parts + ("x" to "tajne-heslo"), listOf("tajne-heslo"))
        val m = MiniJson.obj(json)!!
        val oddily = m["oddily"] as List<*>
        assertEquals(3, oddily.size)
        assertEquals("spojení", (oddily[1] as Map<*, *>)["nazev"])
        assertTrue(((oddily[1] as Map<*, *>)["text"] as String).contains("\"uvozovky\""))
        assertFalse(json.contains("1234567890123456789"))
        assertFalse(json.contains("tajne-heslo"))
    }

    // ---------------- server ----------------

    private fun get(port: Int, line: String): Pair<Int, String> {
        Socket(InetAddress.getByName("127.0.0.1"), port).use { s ->
            s.soTimeout = 5000
            s.getOutputStream().apply { write("$line\r\nHost: x\r\nConnection: close\r\n\r\n".toByteArray()); flush() }
            val all = String(s.getInputStream().readBytes(), Charsets.UTF_8)
            return (all.substringBefore("\r\n").split(' ').getOrNull(1)?.toIntOrNull() ?: 0) to all.substringAfter("\r\n\r\n", "")
        }
    }

    @Test fun serverGivesReportOnlyWithCode() {
        var open = true
        var stopped = 0
        val server = LogServer("abc234", parts = { listOf("aplikace" to "ReCaption 0.75 token=abcdefgh12345678") },
            open = { open }, onStop = { stopped++ })
        assertTrue(server.start(0, InetAddress.getByName("127.0.0.1")))
        try {
            val (st, body) = get(server.port, "GET /zaznam?k=abc234 HTTP/1.1")
            assertEquals(200, st)
            assertTrue(body.contains("=== aplikace ===\nReCaption 0.75"))
            assertFalse(body.contains("abcdefgh12345678"))
            val (stj, bodyj) = get(server.port, "GET /zaznam.json?k=abc234 HTTP/1.1")
            assertEquals(200, stj)
            assertNotNull(MiniJson.obj(bodyj))
            assertEquals(403, get(server.port, "GET /zaznam?k=zzzzzz HTTP/1.1").first)
            assertEquals(405, get(server.port, "POST /zaznam?k=abc234 HTTP/1.1").first)
            assertEquals(404, get(server.port, "GET / HTTP/1.1").first)
            // po vypršení odpoví naposledy „není“ a zavře se
            open = false
            assertEquals(404, get(server.port, "GET /zaznam?k=abc234 HTTP/1.1").first)
            assertFalse(server.running)
            assertEquals(1, stopped)
        } finally { server.stop() }
    }

    @Test fun serverClosesAfterTooManyWrongCodes() {
        val server = LogServer("abc234", parts = { emptyList() })
        assertTrue(server.start(0, InetAddress.getByName("127.0.0.1")))
        try {
            repeat(LogShare.MAX_WRONG + 1) { get(server.port, "GET /zaznam?k=zzzzz$it HTTP/1.1") }
            val end = System.currentTimeMillis() + 3000
            while (server.running && System.currentTimeMillis() < end) Thread.sleep(10)
            assertFalse(server.running)
        } finally { server.stop() }
    }

    @Test fun phonePageAndDownload() {
        assertEquals(LogShare.Route.PAGE, LogShare.route("GET /hlaseni?k=ab3k7m HTTP/1.1", "ab3k7m"))
        assertEquals(LogShare.Route.DOWNLOAD, LogShare.route("GET /hlaseni.txt?k=ab3k7m HTTP/1.1", "ab3k7m"))
        assertEquals(LogShare.Route.BAD_CODE, LogShare.route("GET /hlaseni?k=xxxxxx HTTP/1.1", "ab3k7m"))
        assertEquals("http://192.168.1.23:8789/hlaseni?k=ab3k7m", LogShare.pageUrl("192.168.1.23", 8789, "ab3k7m"))
        val page = LogShare.page("<script>x</script> & ok", "ab3k7m", "recaption-hlaseni-2026-11-20-2130.txt", Lang.CS)
        // text hlášení se do stránky dostane jen jako text, ne jako kód
        assertTrue(page.contains("&lt;script&gt;x&lt;/script&gt; &amp; ok"))
        assertTrue(page.contains("href=\"/hlaseni.txt?k=ab3k7m\""))
        val file = String(LogShare.response(200, "abc", download = "recaption-hlaseni-x.txt"), Charsets.UTF_8)
        assertTrue(file.contains("Content-Disposition: attachment; filename=\"recaption-hlaseni-x.txt\""))
        assertTrue(file.contains("Content-Type: application/octet-stream"))
        assertEquals("recaption-hlaseni-2026-11-20-2130.txt", LogShare.fileName("2026-11-20-2130\"x"))
    }
}
