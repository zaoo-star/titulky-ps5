package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telefon v autě za dne (dávky F6PV-UY39, 38K7-5K4G – rozměry 1280×720, texty vymyšlené): cedule za telefonem,
 * nápověda ovládání vpravo dole a do žluta tónované bubliny.
 */
class PhoneDayTest {
    private fun m(t: String, left: Int, top: Int, right: Int = left + 110, hgt: Int = 18) = MenuLine(t, left, top, right, top + hgt, -1)
    private val darkAll: (MenuLine) -> Boolean = { true }

    @Test fun signBehindPhoneDoesNotHideFeedPost() {
        // příspěvek v sociální síti; tmavý nápis na světlé ceduli vpravo nahoře dřív roztáhl sloupec přes 0,3 obrazu
        val lines = listOf(
            m("TRAVEL C", 1135, 80, 1190, 20),
            m("@SomeAuthor", 768, 422, 862), m("Sample heading", 746, 451, 826), m("A short post text here", 746, 469, 920),
            m("6K 10", 746, 495, 835),
        )
        val r = PhoneScreen.read(lines, 1280, 720, darkAll)
        assertNotNull(r)
        assertTrue(r!!.feed)
        assertTrue(r.right < 0.80f)
        assertFalse(r.paragraphs.any { "TRAVEL" in it })
    }

    @Test fun controlHintIsNotPhone() {
        assertTrue(PhoneScreen.controlHint(m("CONTROL", 1168, 648, 1228), 1280, 720))
        assertTrue(PhoneScreen.controlHint(m("VIEW COMMENTS", 1123, 648, 1228), 1280, 720))
        // telefon v autě (x do ~0,80) ani náhled zprávy nahoře nejsou nápověda
        assertFalse(PhoneScreen.controlHint(m("Some message text", 770, 560, 985), 1280, 720))
        assertFalse(PhoneScreen.controlHint(m("TRAVEL C", 1135, 80, 1190), 1280, 720))
    }

    @Test fun tintedOwnBubbleStillOwn() {
        // za dne: vlastní světle modrá −10, příchozí −35 (pod pevným prahem obě)
        val r = PhoneRead(listOf("Mine", "Theirs", "Mine again"), 0.6f, 0.3f, 0.8f, 0.8f, listOf('s', 's', 's'), "Contact")
        val c = PhoneScreen.withBubbleColors(r, listOf(-10, -35, -11))
        assertEquals(listOf('p', 'l', 'p'), c.sides)
        // jen příchozí (stejně šedé) – žádná vlastní
        val d = PhoneScreen.withBubbleColors(r, listOf(-34, -35, -33))
        assertFalse('p' in d.sides)
    }

    @Test fun nightBlueScreenKeepsIncomingLeft() {
        // v noci (záznam 26. 11. 2026 21:58, Donnie): celý displej do modra – příchozí bílé +10…+12, vlastní +27…+33
        val r = PhoneRead(listOf("Map Pin Expired", "I'll smooth this over", "Good luck", "Im the Don", "Lmk how it goes", "Dropped that", "Left you"),
            0.4f, 0.2f, 0.6f, 0.8f, List(7) { 's' }, "Donnie")
        val c = PhoneScreen.withBubbleColors(r, listOf(12, 10, 27, 12, 30, 33, 30))
        assertEquals(listOf('l', 'l', 'p', 'l', 'p', 'p', 'p'), c.sides)
        // jen vlastní (všechny modré, malý rozdíl) – zůstávají vlastní
        assertEquals(List(3) { 'p' }, PhoneScreen.withBubbleColors(r.copy(paragraphs = r.paragraphs.take(3), sides = List(3) { 's' }), listOf(28, 30, 33)).sides)
    }
}
