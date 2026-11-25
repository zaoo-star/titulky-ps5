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
        assertEquals(PcWake.Reply("Herní PC", "aa:bb:cc:dd:ee:0f", false), PcWake.parseReply(PcWake.reply("Herní PC", "AA-BB-CC-DD-EE-0F", false)))
        assertEquals(PcWake.Reply("PC", null, null), PcWake.parseReply(PcWake.reply("PC", null)))
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
        val l = PcWake.Listener("Herní PC", port, paired = { false }) { a, _ -> got += a }
        assertTrue(l.start())
        try {
            val r = PcWake.send("spust", waitMs = 1500, port = port, targets = listOf("127.0.0.1"))
            assertEquals(listOf("spust"), got.toList())
            assertEquals(1, r.size)
            assertEquals("Herní PC", r[0].name)
            assertEquals(false, r[0].paired)
            assertEquals("127.0.0.1", r[0].from)
            PcWake.send("zastav", waitMs = 500, port = port, targets = listOf("127.0.0.1"))
            assertEquals(listOf("spust", "zastav"), got.toList())
        } finally { l.stop() }
    }

    private val pairing = PcWake.Pairing("PS5-151", "192.168.1.40", ByteArray(16) { (0x30 + it % 10).toByte() },
        ByteArray(16) { it.toByte() }, byteArrayOf(1, 2, 3, 4, 5, 6), "1234567890123456789", "Druhy_ucet")

    @Test fun sparovaniTamAZpet() {
        val p = PcWake.parsePairing(PcWake.pairingMessage(pairing))!!
        assertEquals("PS5-151", p.name); assertEquals("192.168.1.40", p.ip)
        assertArrayEquals(pairing.registKey, p.registKey); assertArrayEquals(pairing.morning, p.morning); assertArrayEquals(pairing.mac, p.mac)
        assertEquals("1234567890123456789", p.accountId); assertEquals("Druhy_ucet", p.accountName)
        // bez účtu i s neplatnými klíči
        assertNull(PcWake.parsePairing(PcWake.pairingMessage(pairing.copy(accountId = null)))!!.accountId)
        assertNull(PcWake.parsePairing(PcWake.pairingMessage(pairing.copy(registKey = ByteArray(5)))))
        assertNull(PcWake.parseCommand(PcWake.pairingMessage(pairing)))
    }

    @Test fun pcPrijmeSparovaniJenOdNekohoKdoOdpovedel() {
        val port = DatagramSocket(0).use { it.localPort }
        val got = CopyOnWriteArrayList<PcWake.Pairing>()
        val l = PcWake.Listener("PC", port, onPairing = { p, _ -> got += p }) { _, _ -> }
        assertTrue(l.start())
        try {
            PcWake.sendPairing(pairing, "127.0.0.1", port)
            val end = System.currentTimeMillis() + 2000
            while (got.isEmpty() && System.currentTimeMillis() < end) Thread.sleep(20)
            assertEquals(1, got.size)
            assertEquals("PS5-151", got[0].name)
            // cizí (ne domácí) adresa se nepošle
            PcWake.sendPairing(pairing, "8.8.8.8", port)
        } finally { l.stop() }
    }
}
