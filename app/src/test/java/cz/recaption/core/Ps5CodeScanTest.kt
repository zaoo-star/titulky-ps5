package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ps5CodeScanTest {
    @Test fun cisloSMezerou() {
        assertEquals("12345678", Ps5CodeScan.parse(listOf("Link Device", "Enter this number on the device", "1234 5678", "Time remaining: 4:59")))
    }

    @Test fun cisloVcelkuANaDvouRadcich() {
        assertEquals("87654321", Ps5CodeScan.parse(listOf("Propojit zařízení", "87654321")))
        assertEquals("11223344", Ps5CodeScan.parse(listOf("1122", "3344", "Zbývající čas 4:12")))
    }

    @Test fun nejisteNeboNic() {
        assertNull(Ps5CodeScan.parse(listOf("Remote Play", "192.168.3.72")))
        assertNull(Ps5CodeScan.parse(listOf("1234 5678", "8765 4321")))
        assertNull(Ps5CodeScan.parse(listOf("123456789")))
    }

    @Test fun adresaUPopisku() {
        // obrazovka Stav připojení: víc adres, platí ta u „IPv4“
        assertEquals("192.168.3.72", Ps5CodeScan.ip(listOf("Adresa IPv4 192.168.3.72", "Maska podsítě 255.255.255.0", "Výchozí brána 192.168.3.1", "Primární DNS 192.168.3.1")))
        assertEquals("192.168.1.40", Ps5CodeScan.ip(listOf("IPv4 Address", "192.168.1.40", "Default Gateway", "192.168.1.1")))
    }

    @Test fun adresaBezPopisku() {
        // popisky a hodnoty ve dvou sloupcích: router (.1) a maska se vynechají
        assertEquals("10.0.0.23", Ps5CodeScan.ip(listOf("IP Address Settings", "Automatic", "10.0.0.23", "255.255.255.0", "10.0.0.1", "10.0.0.1")))
        assertNull(Ps5CodeScan.ip(listOf("192.168.1.20", "192.168.1.30")))
        assertNull(Ps5CodeScan.ip(listOf("8.8.8.8", "1.1.1.1")))
        assertNull(Ps5CodeScan.ip(listOf("Verze 24.06.01.02")))
    }

    @Test fun hlasovani() {
        val v = Ps5CodeVote()
        assertNull(v.add("12345678"))
        assertNull(v.add(null))
        assertEquals("12345678", v.add("12345678"))
        v.clear()
        assertNull(v.current())
    }
}
