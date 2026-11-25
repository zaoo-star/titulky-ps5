package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rozbalený seznam upozornění v telefonu ve hře ([PhoneCard.list]) – rozměry podle dávky NDNS-VQZ7 (1280×720),
 * texty vymyšlené (data her do veřejného repa nepatří).
 */
class PhoneListTest {
    private fun m(t: String, left: Int, top: Int, right: Int = left + 110, hgt: Int = 18) = MenuLine(t, left, top, right, top + hgt, -1)
    private val lightAll: (MenuLine) -> Boolean = { true }
    private val list = listOf(
        m("Alerts (3)", 774, 154, 867, 22),
        m("Marta", 809, 202, 847), m("1 new message", 807, 221, 906),
        m("@Some_Account", 810, 257, 909), m("View recent posts", 807, 275, 927),
        m("Tired", 807, 315, 860), m("Stamina drops faster", 806, 335, 988), m("until rested.", 807, 353, 866),
        // menu vpravo dole do seznamu nepatří
        m("SELECT", 1180, 649, 1234), m("EXIT", 1198, 683, 1229),
    )

    @Test fun listUnderHeader() {
        val r = PhoneCard.read(list, 1280, 720, lightAll)!!
        assertTrue(r.card)
        assertEquals("Alerts (3)", r.title)
        assertEquals(listOf("Marta · 1 new message", "@Some_Account · View recent posts", "Tired · Stamina drops faster until rested."), r.paragraphs)
        assertTrue(r.right < 0.80f)
    }

    @Test fun needsHeaderAndLightText() {
        // bez nadpisu se závorkou to seznam není
        assertNull(PhoneCard.list(list.drop(1), 1280, 720, lightAll))
        // tmavé písmo (otevřená aplikace) sem nepatří
        assertNull(PhoneCard.list(list, 1280, 720) { false })
        assertEquals("Alerts" to 3, PhoneCard.header("* Alerts(3)"))
        assertNull(PhoneCard.header("Call me (maybe)"))
    }

    @Test fun fixedParts() {
        val spec = CornerSpec(listOf("ALERTS"), mapOf("cs" to mapOf("ALERTS" to "Výstrahy")))
        assertEquals("Výstrahy (3)", PhoneCard.fixed("Alerts (3)", Lang.CS, spec))
        assertEquals("@Some_Account", PhoneCard.fixed("@Some_Account", Lang.CS, spec))
        // ostatní přeloží překladač
        assertNull(PhoneCard.fixed("View recent posts", Lang.CS, spec))
    }
}
