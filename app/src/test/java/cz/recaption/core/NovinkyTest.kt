package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovinkyTest {
    private val json = """{"zaznamy":[
        |{"vc":245,"verze":"1.00-rc1","cs":["Začátek 1.00"],"en":["Start of 1.00"]},
        |{"vc":258,"verze":"1.00-rc14","cs":["Nové jméno ReCaption","Xbox"],"en":["New name ReCaption","Xbox"]},
        |{"vc":"zle","verze":"neplatne","cs":[],"en":[]},
        |{"vc":261,"verze":"1.00-rc17","cs":["Spárování PS5 jedním krokem"],"en":["PS5 pairing in one step"]}
        |]}""".trimMargin()

    @Test fun parseIsStrict() {
        val z = Novinky.parse(json)
        assertEquals(3, z.size)
        assertEquals("1.00-rc14", z[1].verze)
        assertEquals(listOf("Nové jméno ReCaption", "Xbox"), z[1].cs)
        assertEquals(listOf("New name ReCaption", "Xbox"), z[1].en)
        assertTrue(Novinky.parse("nesmysl").isEmpty())
        assertTrue(Novinky.parse(null).isEmpty())
        assertTrue(Novinky.parse("""{"zaznamy":[{"verze":"bez vc"}]}""").isEmpty())
    }

    @Test fun mezi_vraci_jen_novejsi_nez_moje_az_po_nabizenou() {
        val z = Novinky.parse(json)
        val m = Novinky.mezi(z, 245, 261)
        assertEquals(listOf(261, 258), m.map { it.vc })
        assertTrue(Novinky.mezi(z, 261, 261).isEmpty())
    }
}
