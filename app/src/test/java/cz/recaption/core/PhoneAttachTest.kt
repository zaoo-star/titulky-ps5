package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Příloha pod zprávou v konverzaci telefonu ve hře ([PhoneRead.attach]) – rozměry podle dávky NDNS-VQZ7 (1280×720),
 * texty vymyšlené (data her do veřejného repa nepatří).
 */
class PhoneAttachTest {
    private fun m(t: String, left: Int, top: Int, right: Int = left + 180, hgt: Int = 20) = MenuLine(t, left, top, right, top + hgt, -1)
    private val darkAll: (MenuLine) -> Boolean = { true }
    private val chat = listOf(
        m("Marta", 856, 157, 894),
        m("Meet me at the old", 772, 202, 946), m("warehouse after dark", 772, 220, 964), m("and come alone.", 771, 238, 918),
        m("Location Expired", 811, 276, 917),                      // karta s ikonou hned pod zprávou
        m("Did you find the place?", 771, 330, 981),
        m("Read this before you", 769, 398, 961), m("come over tonight", 769, 416, 968),
        m("City Council", 807, 444, 923), m("votes on Friday", 807, 462, 910),   // odkaz na článek těsně pod zprávou
        m("On my way.", 935, 520, 987),                            // vlastní zpráva vpravo
    )

    @Test fun cardUnderMessage() {
        val r = PhoneScreen.read(chat, 1280, 720, darkAll)!!
        assertEquals("Marta", r.title)
        assertEquals(listOf("Meet me at the old warehouse after dark and come alone.", "Location Expired", "Did you find the place?",
            "Read this before you come over tonight", "City Council votes on Friday", "On my way."), r.paragraphs)
        assertEquals(listOf(false, true, false, false, true, false), r.attach)
        assertEquals(listOf('l', 'l', 'l', 'l', 'l', 'p'), r.sides)
    }

    @Test fun wideOwnBubbleIsNotACard() {
        // vlastní široká bublina začíná podobně odsazená jako karta – pozná se podle modré barvy
        val lines = listOf(m("Marta", 856, 157, 894), m("Bring the keys with you.", 772, 202, 960),
            m("Sure, I will bring them", 805, 232, 985), m("On my way.", 935, 300, 987))
        val r = PhoneScreen.read(lines, 1280, 720, darkAll)!!
        assertEquals(listOf(false, true, false), r.attach)
        val c = PhoneScreen.withBubbleColors(r, listOf(-12, 8 + PhoneScreen.BLUE, 8 + PhoneScreen.BLUE))
        assertEquals(listOf(false, false, false), c.attach)
        assertEquals(listOf('l', 'p', 'p'), c.sides)
        // šedá karta přílohou zůstává
        assertEquals(listOf(false, true, false), PhoneScreen.withBubbleColors(r, listOf(-12, -14, 8 + PhoneScreen.BLUE)).attach)
    }
}
