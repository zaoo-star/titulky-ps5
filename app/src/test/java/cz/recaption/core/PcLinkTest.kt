package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

/** Titulky PS5 – „Překlad z PC“: klient proti skutečnému serveru [TvSender] (ten běží v PC verzi) a čtení zpráv. */
class PcLinkTest {
    private fun freePort() = ServerSocket(0).use { it.localPort }

    private fun server(): Pair<TvSender, Int> {
        val s = TvSender()
        val p = freePort()
        assertTrue(s.start(p, InetAddress.getLoopbackAddress()))
        return s to p
    }

    /** Další zpráva daného typu (server může poslat i jiné). */
    private fun next(c: WsClient, ms: Long = 3000, ok: (PcMessage) -> Boolean): PcMessage? {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            val t = c.read() ?: return null
            val m = PcMessage.parse(t) ?: continue
            if (ok(m)) return m
        }
        return null
    }

    @Test fun pripojeniAVitej() {
        val (s, p) = server()
        try {
            val c = PcFinder.probe("127.0.0.1", p, 2000)
            assertNotNull("server se neozval vitej", c)
            c!!.close()
        } finally { s.stop() }
    }

    @Test fun nikdoNeposloucha() {
        assertNull(PcFinder.probe("127.0.0.1", freePort(), 500))
    }

    @Test fun titulekSeJmenemBarvouAPolohou() {
        val (s, p) = server()
        try {
            val c = PcFinder.probe("127.0.0.1", p, 2000)!!
            c.send(PcMessage.announce("Zkušební TV", "1.3-z1"))
            val segs = listOf(Segment("Jason", "Musíme jet."), Segment(null, "Hned teď."))
            val look = TvLook.json(ScreenLayout(), 60, 40, 0xFFFFFFFF.toInt(), false, 0.88f, 0.92f, 0.30f, 0.70f, "Jason: We gotta go.")
            s.show(TextLogic.joinSegments(segs), TvSender.partsJson(segs, mapOf("Jason" to 0xFFFFD54F.toInt())), look)
            val m = next(c) { it is PcMessage.Show } as PcMessage.Show?
            assertNotNull(m)
            assertEquals(2, m!!.segments.size)
            assertEquals("Jason", m.segments[0].speaker)
            assertEquals("Musíme jet.", m.segments[0].body)
            assertEquals(0.88f, m.top, 0.001f); assertEquals(0.92f, m.bottom, 0.001f)
            assertEquals(0.30f, m.left, 0.001f); assertEquals(0.70f, m.right, 0.001f)
            assertEquals("Jason: We gotta go.", m.original)
            assertTrue(c.send(PcMessage.done(m.id)))
            s.hide()
            assertNotNull(next(c) { it is PcMessage.Hide })
            c.close()
        } finally { s.stop() }
    }

    @Test fun ukolATelefon() {
        val (s, p) = server()
        try {
            val c = PcFinder.probe("127.0.0.1", p, 2000)!!
            s.sendObjective(TvSender.objectiveMessage("Jeď do garáže.", floatArrayOf(0.3f, 0.9f, 0.7f, 0.95f), 30))
            val o = next(c) { it is PcMessage.Objective } as PcMessage.Objective?
            assertEquals("Jeď do garáže.", o!!.text)
            assertEquals(0.9f, o.box!![1], 0.001f)
            s.sendPhone(TvSender.phoneMessage("Vicki", listOf("Ahoj" to 'l', "Čau" to 'p'), floatArrayOf(0.1f, 0.2f, 0.3f, 0.8f), 22, 80))
            val ph = next(c) { it is PcMessage.Phone } as PcMessage.Phone?
            assertEquals("Vicki", ph!!.title)
            assertEquals(listOf("Ahoj" to 'l', "Čau" to 'p'), ph.msgs)
            s.sendPhone(TvSender.phoneOverMessage(listOf("Ahoj" to floatArrayOf(0.6f, 0.3f, 0.75f, 0.35f)), 22))
            val over = next(c) { it is PcMessage.PhoneOver } as PcMessage.PhoneOver?
            assertEquals(1, over!!.items.size)
            c.close()
        } finally { s.stop() }
    }

    @Test fun dlouhaZpravaPres64kB() {
        val (s, p) = server()
        try {
            val c = PcFinder.probe("127.0.0.1", p, 2000)!!
            val long = "a".repeat(70_000)
            s.sendPhone(TvSender.phoneMessage(null, listOf(long.take(3000) to 'l'), floatArrayOf(0.1f, 0.2f, 0.3f, 0.8f), 22, 80,
                images = listOf("data:image/jpeg;base64," + long)))
            assertNotNull(next(c) { it is PcMessage.Phone })
            c.close()
        } finally { s.stop() }
    }

    @Test fun hledaniVSiti() {
        val (s, p) = server()
        try {
            // „síť“ 127.0.0.x: PC na .1, toto zařízení na .5
            val c = PcFinder.find(null, p, parallel = 8, own = listOf("127.0.0.5"))
            assertNotNull(c)
            assertEquals("127.0.0.1", c!!.address)
            c.close()
        } finally { s.stop() }
    }

    @Test fun nesmyslneZpravy() {
        assertNull(PcMessage.parse(""))
        assertNull(PcMessage.parse("{"))
        assertNull(PcMessage.parse("{\"typ\":\"neznamy\"}"))
        assertEquals(PcMessage.Hello, PcMessage.parse(TvSender.HELLO))
        val o = PcMessage.parse("{\"typ\":\"ukol\",\"text\":\"\"}") as PcMessage.Objective
        assertEquals("", o.text)
        assertEquals(253, PcFinder.candidates("192.168.1.20").size)
        assertTrue(PcFinder.candidates("neplatne").isEmpty())
    }
}

class PcAnnounceTest {
    @org.junit.Test fun pcPoznaVerziANoveZpravy() {
        val a = TvAnnounce.parse(PcMessage.announce("Sony Bravia", PcMessage.RECEIVER_VERSION))!!
        org.junit.Assert.assertEquals("androidtv", a.platform)
        org.junit.Assert.assertEquals(PcMessage.RECEIVER_VERSION, a.version)
        org.junit.Assert.assertTrue(TvSender.atLeast(a.version, TvSender.PHONE_OVER_TV))
        org.junit.Assert.assertTrue(TvSender.atLeast(a.version, TvSender.PHONE_IMAGE_TV))
    }
}
