package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Čtení adresy a Passphrase z obrazovky LG Developer Mode (texty jako je vrací ML Kit). */
class LgScanTest {
    /** Celá obrazovka, každý údaj ve vlastním bloku (častý tvar z ML Kitu). */
    private val screen = """
        Developer Mode
        Dev Mode Status
        ON
        Remaining Duration
        999:56
        EXTEND SESSION
        Key Server
        ON
        IP : 192.168.1.20
        Passphrase : 4A2C9E
        Sign Out
    """.trimIndent()

    @Test fun readsWholeScreen() {
        val r = LgScan.parse(screen)
        assertEquals("192.168.1.20", r.ip)
        assertEquals("4A2C9E", r.pass)
        assertTrue(r.complete)
        assertFalse(r.passFixed); assertFalse(r.passUnsure)
    }

    /** Novější obrazovka (foto majitele 21. 11. 2026): adresa zvlášť pro Wi-Fi a kabel, Wi-Fi „disconnected“. Passphrase smyšlená. */
    @Test fun wiredAndWirelessAddressScreen() {
        val r = LgScan.parse(listOf(
            "DEVELOPER MODE", "Enable or disable Developer Mode services on this device", "Welcome to Developer Mode!",
            "IP Address (wireless):", "disconnected", "IP Address (wired):", "192.168.3.14",
            "Passphrase:", "7C1E0B", "Remain Session:", "925h:11m",
            "ON", "Dev Mode Status", "ON", "Key Server", "EXTEND", "Extend Session Time", "OFF", "Clear Cache Data",
        ))
        assertEquals("192.168.3.14", r.ip)
        assertEquals("7C1E0B", r.pass)
    }

    @Test fun valueOnLineBelowLabel() {
        val r = LgScan.parse(listOf("Key Server", "ON", "IP", "10.0.0.57", "Passphrase", "B7D31F"))
        assertEquals("10.0.0.57", r.ip)
        assertEquals("B7D31F", r.pass)
    }

    @Test fun twoColumnScreenLabelsThenValues() {
        // ML Kit čte levý sloupec (popisky) a pak pravý (hodnoty)
        val lines = listOf(
            "Dev Mode Status", "Remaining Duration", "Key Server", "IP", "Passphrase",
            "ON", "999:56:12", "ON", "192.168.1.20", "4A2C9E",
        )
        val r = LgScan.parse(lines)
        assertEquals("192.168.1.20", r.ip)
        assertEquals("4A2C9E", r.pass)
        // druhý útvar se směsí písmen a číslic = nejasné, raději nic
        assertNull(LgScan.parse(lines + "Model 55B7V5").pass)
    }

    @Test fun gluedColonAndNoSpaces() {
        val r = LgScan.parse(listOf("IP:192.168.0.105", "Passphrase:8E5A0C"))
        assertEquals("192.168.0.105", r.ip)
        assertEquals("8E5A0C", r.pass)
    }

    @Test fun ocrTyposInLabelAndAddress() {
        // ML Kit občas čte „Passphrase“ zkomoleně a v adrese dá O místo nuly a l místo jedničky
        val r = LgScan.parse(listOf("Key Server   ON", "IP : l92.168.O.2l", "Passphrse : 3F9B21"))
        assertEquals("192.168.0.21", r.ip)
        assertEquals("3F9B21", r.pass)
    }

    @Test fun spacesAroundDotsAndCommaInsteadOfDot() {
        assertEquals("192.168.1.20", LgScan.parse(listOf("IP 192. 168 .1,20")).ip)
    }

    @Test fun hexPassphraseLettersOandIAreFixed() {
        // O a I v šestnáctkovém tvaru být nemůžou → jednoznačně 0 a 1
        val r = LgScan.parse(listOf("Passphrase : 4AOC9I"))
        assertEquals("4A0C91", r.pass)
        assertTrue(r.passFixed); assertFalse(r.passUnsure)
        val lower = LgScan.parse(listOf("Passphrase : 4Aoc9l".replace("c", "C")))
        assertEquals("4A0C91", lower.pass)
    }

    @Test fun nonHexPassphraseWithConfusablesIsMarkedUnsure() {
        // jiné písmeno než A–F → nejde rozhodnout, jestli O je O nebo 0
        val r = LgScan.parse(listOf("Passphrase : K7O2XP"))
        assertEquals("K7O2XP", r.pass)
        assertTrue(r.passUnsure); assertFalse(r.passFixed)
        val ok = LgScan.parse(listOf("Passphrase : K7Z2XP"))
        assertEquals("K7Z2XP", ok.pass)
        assertFalse(ok.passUnsure)
    }

    @Test fun keyServerAnchorWithoutPassphraseLabel() {
        val r = LgScan.parse(listOf("Key Server", "ON", "192.168.1.33", "C0FFEE"))
        assertEquals("192.168.1.33", r.ip)
        assertEquals("C0FFEE", r.pass)
    }

    @Test fun nothingNearLabelMeansNoPassphrase() {
        // šestiznakové útvary jinde na obrazovce se za Passphrase nepovažují
        val r = LgScan.parse(listOf("Dev Mode Status ON", "Remaining Duration 999:56", "IP : 192.168.1.20", "A1B2C3"))
        assertEquals("192.168.1.20", r.ip)
        assertNull(r.pass)
    }

    @Test fun screenWordsAreNotPassphrase() {
        assertNull(LgScan.parse(listOf("Key Server", "Status", "SERVER", "STATUS")).pass)
        assertNull(LgScan.parse(listOf("Passphrase", "Server")).pass)
    }

    @Test fun twoDifferentPassphrasesAreAmbiguous() {
        assertNull(LgScan.parse(listOf("Passphrase : 4A2C9E 7B1D3F")).pass)
    }

    @Test fun publicOrBrokenAddressesIgnored() {
        assertNull(LgScan.parse(listOf("IP : 8.8.8.8")).ip)
        assertNull(LgScan.parse(listOf("IP : 192.168.1.256")).ip)
        assertNull(LgScan.parse(listOf("IP : 192.168.1.255")).ip)
        // čas „999:56“ ani verze „5.30.20“ nejsou adresa
        assertNull(LgScan.parse(listOf("Remaining 999:56", "webOS TV 5.30.20")).ip)
    }

    @Test fun twoAddressesPreferLabeledOne() {
        val r = LgScan.parse(listOf("Router 192.168.1.1", "IP : 192.168.1.20"))
        assertEquals("192.168.1.20", r.ip)
        assertNull(LgScan.parse(listOf("192.168.1.1", "192.168.1.20")).ip)
    }

    @Test fun resultIsValidForInstaller() {
        val r = LgScan.parse(screen)
        assertEquals(r.ip, LgInstall.address(r.ip!!))
        assertEquals(listOf("4A2C9E"), LgInstall.passphrase(r.pass!!))
    }

    @Test fun voteNeedsTwoSameReadings() {
        val v = LgScanVote()
        v.add(LgScanResult("192.168.1.20", "4A2C9E"))
        assertTrue(v.current().empty)
        v.add(LgScanResult("192.168.1.20", null))
        assertEquals("192.168.1.20", v.current().ip)
        assertNull(v.current().pass)
        v.add(LgScanResult(null, "4A2C9F"))
        assertNull(v.current().pass) // 1:1, nerozhodnuto
        v.add(LgScanResult(null, "4A2C9E"))
        assertEquals("4A2C9E", v.current().pass)
        assertTrue(v.current().complete)
        v.clear()
        assertTrue(v.current().empty)
    }

    @Test fun passphraseIsHiddenInReports() {
        val t = Redact.clean("chyba: passphrase=4A2C9E, Passphrase: B7D31F")
        assertFalse(t, t.contains("4A2C9E"))
        assertFalse(t, t.contains("B7D31F"))
    }
}
