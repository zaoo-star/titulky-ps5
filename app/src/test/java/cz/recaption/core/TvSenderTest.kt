package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket

/** Posílání titulků do televize LG: zprávy, navázání spojení a co televize dostane. */
class TvSenderTest {
    private val local: InetAddress = InetAddress.getLoopbackAddress()

    @Test fun messagesAreValidJson() {
        assertEquals("{\"typ\":\"ukaz\",\"id\":3,\"text\":\"Mira: Řekl \\\"ne\\\".\\nA šel.\"}",
            TvSender.showMessage(3, "Mira: Řekl \"ne\".\nA šel."))
        assertEquals("{\"typ\":\"skryj\",\"id\":4}", TvSender.hideMessage(4))
        assertEquals("\"a\\\\b c\"", TvSender.json("a\\b\tc\r"))
        assertEquals("\"\\u0001\"", TvSender.json("\u0001"))
    }

    @Test fun acceptKeyMatchesTheStandardExample() {
        // příklad přímo z popisu WebSocketu (RFC 6455)
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", TvSender.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="))
    }

    @Test fun framesCarryLength() {
        val short = TvSender.frame("ahoj")
        assertEquals(listOf(0x81, 4), short.take(2).map { it.toInt() and 0xFF })
        val long = TvSender.frame("x".repeat(300))
        assertEquals(listOf(0x81, 126, 1, 44), long.take(4).map { it.toInt() and 0xFF })
        assertEquals(304, long.size)
    }

    @Test fun keyIsFoundOnlyInWebSocketRequest() {
        assertEquals("abc==", TvSender.keyFromHead("GET / HTTP/1.1\r\nHost: x\r\nsec-websocket-key:  abc== \r\nUpgrade: websocket"))
        assertNull(TvSender.keyFromHead("GET / HTTP/1.1\r\nHost: x"))
    }

    /** Přečte jednu textovou zprávu od serveru (ty nejsou maskované). */
    private fun readMessage(input: InputStream): String {
        assertEquals(0x81, input.read())
        var len = input.read()
        if (len == 126) len = (input.read() shl 8) or input.read()
        val buf = ByteArray(len)
        var got = 0
        while (got < len) got += input.read(buf, got, len - got)
        return String(buf, Charsets.UTF_8)
    }

    private fun connect(port: Int): Socket {
        val s = Socket(local, port)
        s.soTimeout = 5000
        s.getOutputStream().apply {
            write(("GET / HTTP/1.1\r\nHost: tv\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray())
            flush()
        }
        val head = StringBuilder()
        val input = s.getInputStream()
        while (!head.endsWith("\r\n\r\n")) head.append(input.read().toChar())
        assertTrue(head.toString(), head.startsWith("HTTP/1.1 101"))
        assertTrue(head.toString(), head.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo="))
        assertEquals(TvSender.HELLO, readMessage(input))
        return s
    }

    private fun waitFor(what: () -> Boolean) {
        val end = System.currentTimeMillis() + 5000
        while (!what() && System.currentTimeMillis() < end) Thread.sleep(10)
        assertTrue(what())
    }

    @Test fun tvGetsSubtitlesAndHides() {
        val sender = TvSender()
        assertTrue(sender.start(0, local))
        try {
            val tv = connect(sender.port)
            waitFor { sender.connected == 1 }
            sender.show("Počkej.")
            assertEquals("{\"typ\":\"ukaz\",\"id\":1,\"text\":\"Počkej.\"}", readMessage(tv.getInputStream()))
            sender.show("Počkej.")          // stejný text podruhé se neposílá
            sender.hide()
            assertEquals("{\"typ\":\"skryj\",\"id\":2}", readMessage(tv.getInputStream()))
            sender.hide()                   // schovaný pruh se neschovává znovu
            sender.show("Jdeme.")
            assertEquals("{\"typ\":\"ukaz\",\"id\":3,\"text\":\"Jdeme.\"}", readMessage(tv.getInputStream()))

            // televize, která se připojí později, dostane hned to, co je právě na obrazovce
            val second = connect(sender.port)
            assertEquals("{\"typ\":\"ukaz\",\"id\":4,\"text\":\"Jdeme.\"}", readMessage(second.getInputStream()))
            waitFor { sender.connected == 2 }

            tv.close(); second.close()
            waitFor { sender.connected == 0 }
            sender.show("Nikdo neposlouchá.")   // bez televize se nic nestane
        } finally { sender.stop() }
        assertFalse(sender.running)
    }

    @Test fun plainRequestGetsShortAnswer() {
        val sender = TvSender()
        assertTrue(sender.start(0, local))
        try {
            Socket(local, sender.port).use { s ->
                s.soTimeout = 5000
                s.getOutputStream().apply { write("GET / HTTP/1.1\r\nHost: tv\r\n\r\n".toByteArray()); flush() }
                val all = String(s.getInputStream().readBytes(), Charsets.UTF_8)
                assertTrue(all, all.startsWith("HTTP/1.1 200") && all.endsWith("ReCaption"))
            }
            assertEquals(0, sender.connected)
        } finally { sender.stop() }
    }

    @Test fun statusMessageForTv() {
        val m = TvSender.statusMessage(listOf("ok" to "PS5-151 · připojeno", "pozor" to "Překlad · \"neběží\""))
        assertEquals("{\"typ\":\"stav\",\"radky\":[{\"barva\":\"ok\",\"text\":\"PS5-151 · připojeno\"}," +
            "{\"barva\":\"pozor\",\"text\":\"Překlad · \\\"neběží\\\"\"}]}", m)
    }
}
