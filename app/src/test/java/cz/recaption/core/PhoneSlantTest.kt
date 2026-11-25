package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Šikmo držený telefon ve hře ([PhoneScreen.slant]) a nápis ve scéně uříznutý okrajem obrazu – rozměry podle dávek
 * 3WMD-6WFC a 4KHZ-AJKC (1280×720), texty vymyšlené (data her do veřejného repa nepatří).
 */
class PhoneSlantTest {
    private fun m(t: String, left: Int, top: Int, right: Int, bottom: Int) = MenuLine(t, left, top, right, bottom, -1)
    private val darkAll: (MenuLine) -> Boolean = { true }

    /** Konverzace pěšky: levý okraj ujíždí o 0,16 bodu na bod výšky, dlouhé řádky mají vyšší rámeček. */
    private val tilted = listOf(
        m("Marta", 624, 137, 662, 158),
        m("Bring the blue folder to", 545, 159, 674, 185), m("the office tomorrow and", 543, 176, 721, 205),
        m("do not forget the keys", 541, 193, 689, 219), m("for the garage please.", 538, 209, 720, 238),
        m("Thanks for the lift last", 531, 247, 709, 279), m("night, it was a big help.", 528, 266, 717, 297),
        m("Where are you now? Come", 504, 417, 683, 454), m("to the yard.", 501, 434, 572, 460),
    )

    @Test fun slantFromLeftEdges() {
        val k = PhoneScreen.slant(tilted.drop(1))
        assertTrue("sklon $k", k < -0.12f && k > -0.20f)
        // rovně držený telefon (v autě) = 0, málo řádků = 0
        val straight = tilted.drop(1).mapIndexed { i, l -> l.copy(left = 540, right = 540 + (l.right - l.left)) }
        assertEquals(0f, PhoneScreen.slant(straight), 0.0001f)
        assertEquals(0f, PhoneScreen.slant(tilted.take(3)), 0.0001f)
    }

    @Test fun tiltedConversationReadsAsStraight() {
        val r = PhoneScreen.read(tilted, 1280, 720, darkAll)!!
        assertEquals("Marta", r.title)
        assertEquals(listOf("Bring the blue folder to the office tomorrow and do not forget the keys for the garage please.",
            "Thanks for the lift last night, it was a big help.", "", "Where are you now? Come to the yard."), r.paragraphs)
        // všechno jsou příchozí zprávy u levého okraje – bez narovnání vyšly horní jako vlastní (vpravo)
        assertEquals(listOf('s', 's', 'c', 's'), r.sides)
        assertTrue(r.attach.none { it })
    }

    @Test fun signCutByScreenEdgeIsNotThePhone() {
        // světelná tabule u pravého okraje obrazu: řádky končí přesně na hraně čteného pásu
        val edge = (1280 * PhoneScreen.ZONE_R).toInt() - 1
        val sign = listOf(m("Bridge Clos", 1197, 195, edge, 213), m("AHEAD", 1205, 214, edge, 231), m("USE DETOU", 1182, 242, edge, 257))
        assertNull(PhoneScreen.read(sign, 1280, 720, darkAll))
        // stejné řádky o kus vlevo (náhled zprávy vpravo dole končí před hranou) se čtou
        assertTrue(PhoneScreen.read(sign.map { it.copy(left = it.left - 20, right = it.right - 20) }, 1280, 720, darkAll) != null)
    }
}
