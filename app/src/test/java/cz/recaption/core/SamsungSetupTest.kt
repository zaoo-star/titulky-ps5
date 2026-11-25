package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Průvodce TV Samsung přes Apps2Samsung: odkazy, hledání TV a pořadí kroků. */
class SamsungSetupTest {
    @Test fun linksCarryOnlyTheLatestRelease() {
        assertEquals("apps2samsung://source?url=https%3A%2F%2Fgithub.com%2Fadusa89%2FReCaption", SamsungSetup.sourceLink())
        assertEquals("https://apps2samsung.com/source?url=https%3A%2F%2Fgithub.com%2Fadusa89%2FReCaption", SamsungSetup.sourceWebLink())
        assertTrue(SamsungSetup.WGT_ASSET.endsWith(".wgt"))
    }

    @Test fun samsungAnswerGivesHomeAddress() {
        val m = SamsungSetup.searchMessage()
        assertTrue(m.startsWith("M-SEARCH * HTTP/1.1\r\n") && m.endsWith("\r\n\r\n"))
        val ok = "HTTP/1.1 200 OK\r\nST: urn:samsung.com:device:RemoteControlReceiver:1\r\nSERVER: SHP, UPnP/1.0, Samsung UPnP SDK/1.0\r\n\r\n"
        assertEquals("192.168.1.40", SamsungSetup.parseResponse(ok, "192.168.1.40"))
        assertNull(SamsungSetup.parseResponse(ok, "8.8.8.8"))
        assertNull(SamsungSetup.parseResponse("HTTP/1.1 200 OK\r\nSERVER: WebOS/4.1.0\r\n\r\n", "192.168.1.41"))
        assertNull(SamsungSetup.parseResponse("NOTIFY * HTTP/1.1\r\nSERVER: Samsung\r\n\r\n", "192.168.1.40"))
    }

    private fun p(dev: Boolean = false, inst: Boolean = false, src: Boolean = false, check: TvCheck = TvCheck.WAITING) =
        SamsungSetup.Probe(dev, inst, src, check)

    @Test fun stepsInOrder() {
        assertEquals(SamsungSetup.Step.DEV_MODE, SamsungSetup.step(p()))
        // Apps2Samsung už v telefonu je, ale TV ještě není ve vývojářském režimu → pořád krok 1
        assertEquals(SamsungSetup.Step.DEV_MODE, SamsungSetup.step(p(inst = true)))
        assertEquals(SamsungSetup.Step.INSTALLER, SamsungSetup.step(p(dev = true)))
        assertEquals(SamsungSetup.Step.SOURCE, SamsungSetup.step(p(dev = true, inst = true)))
        assertEquals(SamsungSetup.Step.INSTALL, SamsungSetup.step(p(dev = true, inst = true, src = true)))
        assertTrue(SamsungSetup.done(SamsungSetup.Step.SOURCE, p(dev = true, inst = true, src = true)))
        assertFalse(SamsungSetup.done(SamsungSetup.Step.INSTALL, p(dev = true, inst = true, src = true)))
    }

    @Test fun tvAnswerDecides() {
        // ReCaption se z TV ohlásilo → hotovo, ať už se kroky odškrtly nebo ne
        assertEquals(SamsungSetup.Step.READY, SamsungSetup.step(p(check = TvCheck.SAME)))
        assertEquals(SamsungSetup.Step.READY, SamsungSetup.step(p(check = TvCheck.NEWER)))
        // starší ReCaption v TV: jen aktualizovat v Apps2Samsungu
        assertEquals(SamsungSetup.Step.INSTALL, SamsungSetup.step(p(check = TvCheck.OLDER)))
        assertEquals(SamsungSetup.Step.INSTALL, SamsungSetup.step(p(check = TvCheck.NO_VERSION)))
        // ohlásila se LG: u Samsungu se nic neodškrtává
        assertEquals(SamsungSetup.Step.DEV_MODE, SamsungSetup.step(p(check = TvCheck.OTHER)))
    }

    // Odpověď TV Samsung na GET /api/v2/ (tvar podle samsungtv-ws a Apps2Samsungu, zkrácená; isSupport je řetězec s JSONem)
    private fun api(mode: String, ip: String) = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: 999\r\n\r\n" +
        "{\"device\":{\"FrameTVSupport\":\"false\",\"OS\":\"Tizen\",\"PowerState\":\"on\",\"developerIP\":\"$ip\",\"developerMode\":\"$mode\"," +
        "\"id\":\"uuid:1234\",\"ip\":\"192.168.1.40\",\"modelName\":\"UE55TU7172UXXH\",\"name\":\"[TV] Samsung 7 Series (55)\"," +
        "\"networkType\":\"wireless\",\"ssid\":\"Domov\",\"type\":\"Samsung SmartTV\",\"wifiMac\":\"aa:bb:cc:dd:ee:ff\"}," +
        "\"id\":\"uuid:1234\",\"isSupport\":\"{\\\"DMP_DRM_PLAYREADY\\\":\\\"false\\\"}\",\"name\":\"[TV] Samsung 7 Series (55)\"," +
        "\"remote\":\"1.0\",\"type\":\"Samsung SmartTV\",\"uri\":\"http://192.168.1.40:8001/api/v2/\",\"version\":\"2.0.25\"}"

    @Test fun restApiIsRead() {
        val on = SamsungSetup.parseApi(api("1", "192.168.1.20"))!!
        assertEquals(true, on.developerMode)
        assertEquals("192.168.1.20", on.developerIp)
        assertEquals("UE55TU7172UXXH", on.model)
        assertEquals("[TV] Samsung 7 Series (55)", on.name)
        val off = SamsungSetup.parseApi(api("0", "0.0.0.0"))!!
        assertEquals(false, off.developerMode)
        assertNull(off.developerIp)
        // tělo bez hlavičky HTTP
        assertEquals(true, SamsungSetup.parseApi("{\"device\":{\"developerMode\":\"1\",\"developerIP\":\"10.0.0.5\"}}")?.developerMode)
        // pole chybí (starší TV) = nevíme
        assertNull(SamsungSetup.parseApi("{\"device\":{\"modelName\":\"UE40H6200\"}}")!!.developerMode)
        // ne REST API Samsungu: chyba, HTML ze směrovače, JSON bez device, nesmysl v adrese
        assertNull(SamsungSetup.parseApi("HTTP/1.1 404 Not Found\r\n\r\n{}"))
        assertNull(SamsungSetup.parseApi("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<html><body>Router</body></html>"))
        assertNull(SamsungSetup.parseApi("HTTP/1.1 200 OK\r\n\r\n{\"status\":\"ok\"}"))
        assertNull(SamsungSetup.parseApi("{\"device\":{\"developerMode\":\"1\",\"developerIP\":\"999.1.1.1\"}}")!!.developerIp)
        assertTrue(SamsungSetup.apiRequest("192.168.1.40").startsWith("GET /api/v2/ HTTP/1.0\r\nHost: 192.168.1.40:8001\r\n"))
    }

    @Test fun devModeStates() {
        val phone = "192.168.1.20"
        fun st(mode: String, ip: String, sdb: Boolean, me: String? = phone) = SamsungSetup.devState(SamsungSetup.parseApi(api(mode, ip)), sdb, me)
        assertEquals(SamsungSetup.DevState.OFF, st("0", "0.0.0.0", false))
        assertEquals(SamsungSetup.DevState.NO_IP, st("1", "0.0.0.0", false))
        assertEquals(SamsungSetup.DevState.OTHER_IP, st("1", "192.168.1.33", false))
        // jiná adresa = TV pustí jen ten počítač, i když port odpovídá
        assertEquals(SamsungSetup.DevState.OTHER_IP, st("1", "192.168.1.33", true))
        assertEquals(SamsungSetup.DevState.REVERSED_IP, st("1", "20.1.168.192", false))
        // pozpátku, ale port otevřený = TV ji tak jen ukazuje a funguje to
        assertEquals(SamsungSetup.DevState.READY, st("1", "20.1.168.192", true))
        assertEquals(SamsungSetup.DevState.RESTART, st("1", phone, false))
        assertEquals(SamsungSetup.DevState.READY, st("1", phone, true))
        // přeposílání přes jinou aplikaci v TV (127.0.0.1) není jiné zařízení
        assertEquals(SamsungSetup.DevState.RESTART, st("1", "127.0.0.1", false))
        // z TV/boxu se adresa neporovnává (Host PC IP je telefon s Apps2Samsungem)
        assertEquals(SamsungSetup.DevState.READY, st("1", "192.168.1.33", false, me = null))
        assertEquals(SamsungSetup.DevState.OFF, st("0", "0.0.0.0", true, me = null))
        // REST mlčí: jako dřív jen port
        assertEquals(SamsungSetup.DevState.READY, SamsungSetup.devState(null, true, phone))
        assertEquals(SamsungSetup.DevState.UNKNOWN, SamsungSetup.devState(null, false, phone))
        assertEquals("20.1.168.192", SamsungSetup.reversed(phone))
        // každý problém má svůj text
        for (s in SamsungSetup.DevState.values()) {
            val hasText = SamsungSetup.devStateKey(s) != null
            assertEquals(s.name, s != SamsungSetup.DevState.READY && s != SamsungSetup.DevState.UNKNOWN, hasText)
        }
    }
}
