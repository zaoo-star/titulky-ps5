package cz.recaption.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramSocket
import java.util.concurrent.CopyOnWriteArrayList

/** Titulky PS5 – povely z TV pro PC a probuzení PC (Wake-on-LAN). */
class PcWakeTest {
    @Test fun povelyTamAZpet() {
        assertEquals("spust", PcWake.parseCommand(PcWake.command("spust")))
        assertEquals("zastav", PcWake.parseCommand(PcWake.command("zastav")))
        assertNull(PcWake.parseCommand("{\"typ\":\"titulkyps5\",\"akce\":\"smaz\"}"))
        assertNull(PcWake.parseCommand("{\"typ\":\"jiny\",\"akce\":\"spust\"}"))
        assertNull(PcWake.parseCommand("nesmysl"))
        assertEquals("Herní PC" to "aa:bb:cc:dd:ee:0f", PcWake.parseReply(PcWake.reply("Herní PC", "AA-BB-CC-DD-EE-0F")))
        assertEquals("PC" to null, PcWake.parseReply(PcWake.reply("PC", null)))
    }

    @Test fun mac() {
        assertEquals("aa:bb:cc:dd:ee:ff", PcWake.normalizeMac("AA-BB-CC-DD-EE-FF"))
        assertEquals("01:23:45:67:89:ab", PcWake.normalizeMac("0123456789AB"))
        assertNull(PcWake.normalizeMac("00:00:00:00:00:00"))
        assertNull(PcWake.normalizeMac("zz:bb:cc:dd:ee:ff"))
        assertNull(PcWake.normalizeMac("aa:bb:cc"))
        assertEquals("0a:1b:2c:3d:4e:5f", PcWake.macString(byteArrayOf(0x0a, 0x1b, 0x2c, 0x3d, 0x4e, 0x5f)))
    }

    @Test fun magickyPaket() {
        val p = PcWake.magicPacket("01:02:03:04:05:06")!!
        assertEquals(102, p.size)
        assertArrayEquals(ByteArray(6) { -1 }, p.copyOfRange(0, 6))
        for (i in 0 until 16) assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), p.copyOfRange(6 + i * 6, 12 + i * 6))
        assertNull(PcWake.magicPacket("neplatna"))
    }

    @Test fun vsesmeroveAdresy() {
        assertEquals(listOf("255.255.255.255", "192.168.1.255"), PcWake.broadcastTargets(listOf("192.168.1.20")))
    }

    @Test fun pcPrijmePovelAOdpovi() {
        val port = DatagramSocket(0).use { it.localPort }
        val got = CopyOnWriteArrayList<String>()
        val l = PcWake.Listener("Herní PC", port) { a, _ -> got += a }
        assertTrue(l.start())
        try {
            val r = PcWake.send("spust", waitMs = 1500, port = port, targets = listOf("127.0.0.1"))
            assertEquals(listOf("spust"), got.toList())
            assertEquals(1, r.size)
            assertEquals("Herní PC", r[0].first)
            PcWake.send("zastav", waitMs = 500, port = port, targets = listOf("127.0.0.1"))
            assertEquals(listOf("spust", "zastav"), got.toList())
        } finally { l.stop() }
    }
}
