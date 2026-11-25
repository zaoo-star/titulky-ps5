package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LgSetupTest {
    private fun p(tv: Boolean = false, dev: Boolean = false, key: Boolean = false, paired: Boolean = false, pass: Boolean = false) =
        LgSetup.Probe(tv, dev, key, paired, pass)

    @Test fun krokyPoporade() {
        assertEquals(LgSetup.Step.FIND_TV, LgSetup.step(p()))
        assertEquals(LgSetup.Step.DEV_MODE, LgSetup.step(p(tv = true)))
        assertEquals(LgSetup.Step.KEY_SERVER, LgSetup.step(p(tv = true, dev = true)))
        assertEquals(LgSetup.Step.PASSPHRASE, LgSetup.step(p(tv = true, dev = true, key = true)))
        assertEquals(LgSetup.Step.READY, LgSetup.step(p(tv = true, dev = true, key = true, pass = true)))
        // spárovaná televize: Key Server ani Passphrase už nejsou potřeba
        assertEquals(LgSetup.Step.READY, LgSetup.step(p(tv = true, dev = true, paired = true)))
        assertTrue(LgSetup.done(LgSetup.Step.KEY_SERVER, p(tv = true, dev = true, paired = true)))
        assertFalse(LgSetup.done(LgSetup.Step.DEV_MODE, p(tv = true)))
        // otevřený port SSH = televize určitě běží, i když se neozvala na hledání
        assertEquals(LgSetup.Step.KEY_SERVER, LgSetup.step(p(dev = true)))
    }

    @Test fun odpovedTelevize() {
        val lg = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLOCATION: http://192.168.1.50:1952/\r\nSERVER: WebOS/4.1.0 UPnP/1.0\r\nST: urn:lge-com:service:webos-second-screen:1\r\n\r\n"
        assertEquals("192.168.1.50", LgSetup.parseResponse(lg, "192.168.1.50"))
        assertNull(LgSetup.parseResponse("HTTP/1.1 200 OK\r\nSERVER: Linux UPnP/1.0 Sonos\r\n\r\n", "192.168.1.60"))
        assertNull(LgSetup.parseResponse(lg, "8.8.8.8"))
        assertNull(LgSetup.parseResponse("NOTIFY * HTTP/1.1\r\nNT: webos\r\n\r\n", "192.168.1.50"))
        assertTrue(LgSetup.searchMessage().contains("ST: urn:lge-com:service:webos-second-screen:1"))
    }

    @Test fun passphrase() {
        assertTrue(LgSetup.passphraseLooksOk("A1B2C3"))
        assertTrue(LgSetup.passphraseLooksOk(" a1b2c3 "))
        assertFalse(LgSetup.passphraseLooksOk("A1B2C"))
        assertFalse(LgSetup.passphraseLooksOk("G1B2C3"))
    }
}
