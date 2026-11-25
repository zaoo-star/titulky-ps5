package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Barva jména z obrazu: světlé body písma, bílé jméno = bez barvy, naučí se až po 2 shodách. */
class NameColorTest {
    private fun yuv(rgb: Int): Triple<Int, Int, Int> {
        val r = (rgb shr 16) and 0xFF; val g = (rgb shr 8) and 0xFF; val b = rgb and 0xFF
        val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
        val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
        val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
        return Triple(y, u, v)
    }

    /** Písmo dané barvy (20 bodů) na tmavém pozadí (40 bodů). */
    private fun sample(text: Int): Int? {
        val n = 60
        val ys = IntArray(n); val us = IntArray(n); val vs = IntArray(n)
        for (i in 0 until n) {
            val (y, u, v) = if (i % 3 == 0) yuv(text) else yuv(0x202020)
            ys[i] = y; us[i] = u; vs[i] = v
        }
        return NameColor.fromSamples(ys, us, vs, n)
    }

    @Test fun yellowNameIsYellow() {
        assertEquals(SpeakerColors.PALETTE[0], NameColor.nearest(sample(0xF2C14E)!!))
        assertEquals(SpeakerColors.PALETTE[1], NameColor.nearest(sample(0x6FB8F0)!!))
    }

    @Test fun paleReadingsKeepTheirHue() {
        // skutečná čtení ze hry (záznam majitele 20. 11. 2026): žlutá jména vyhlazená do bílé
        assertEquals(SpeakerColors.PALETTE[0], NameColor.nearest(0xFFDBD899.toInt()))
        assertEquals(SpeakerColors.PALETTE[0], NameColor.nearest(0xFFD7D79E.toInt()))
        // bledě modrá, bledě zelená, oranžová a růžová zůstanou svou barvou
        assertEquals(SpeakerColors.PALETTE[1], NameColor.nearest(0xFFB4D2EB.toInt()))
        assertEquals(SpeakerColors.PALETTE[2], NameColor.nearest(0xFFB0E0B0.toInt()))
        assertEquals(SpeakerColors.PALETTE[5], NameColor.nearest(0xFFF5A55A.toInt()))
        assertEquals(SpeakerColors.PALETTE[3], NameColor.nearest(0xFFF0A0A8.toInt()))
    }

    @Test fun whiteNameHasNoColor() {
        assertNull(sample(0xF0F0F0))
    }

    @Test fun learnedAfterTwo() {
        NameColor.reset()
        NameColor.record("Courier", 0xF5A060, 120)
        assertNull(NameColor.learned("COURIER"))
        NameColor.record("Courier", 0xF0A55A, 90)
        assertEquals(SpeakerColors.PALETTE[5], NameColor.learned("courier")?.let { NameColor.nearest(it) })
        assertEquals(true, NameColor.report().contains("2×, průměr 105 µs, nejdéle 120 µs"))
        NameColor.reset()
    }

    // ---- přesnější čtení: jen výplň písma, převažující odstín, medián ----

    /** Písmo [text] s bílým vyhlazením okrajů ([aa] bodů), tmavým obrysem a pozadím [bg]. */
    private fun glyphs(text: Int, fill: Int = 30, aa: Int = 12, outline: Int = 20, bg: Int = 0x303840, bgN: Int = 40): Int? {
        val cols = ArrayList<Int>()
        repeat(fill) { cols.add(text) }
        // vyhlazení do bílé: půl na půl s bílou a skoro bílé
        repeat(aa) { i -> cols.add(if (i % 2 == 0) mix(text, 0xFFFFFF) else 0xF4F4F4) }
        repeat(outline) { cols.add(0x101010) }
        repeat(bgN) { cols.add(bg) }
        val n = cols.size
        val ys = IntArray(n); val us = IntArray(n); val vs = IntArray(n)
        cols.forEachIndexed { i, c -> val (y, u, v) = yuv(c); ys[i] = y; us[i] = u; vs[i] = v }
        return NameColor.fromSamples(ys, us, vs, n)
    }

    private fun mix(a: Int, b: Int): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) + ((b shr s) and 0xFF)) / 2
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    @Test fun pinkWithWhiteEdgesAndOutlineStaysPink() {
        // růžové jméno s bílým vyhlazením a tmavým obrysem – průměr dřív táhl k bledé žluté / modré
        assertEquals(SpeakerColors.PALETTE[3], NameColor.nearest(glyphs(0xF07890)!!))
        // i s kouskem modrého nebe mezi písmeny (rámeček slova bez okrajů je hlavně písmo)
        assertEquals(SpeakerColors.PALETTE[3], NameColor.nearest(glyphs(0xF07890, bg = 0x4080D0, bgN = 15)!!))
    }

    @Test fun yellowAndBlueGlyphs() {
        assertEquals(SpeakerColors.PALETTE[0], NameColor.nearest(glyphs(0xF2C84A)!!))
        assertEquals(SpeakerColors.PALETTE[1], NameColor.nearest(glyphs(0x70B8F4)!!))
    }

    @Test fun whiteNameOverColourfulSceneHasNoColor() {
        // bílé jméno, za ním pár světlých barevných bodů scény
        assertNull(glyphs(0xF8F8F8, fill = 40, bg = 0xE0B040, bgN = 6))
        // málo barevných bodů vůbec
        assertNull(glyphs(0xF07890, fill = 2, aa = 0, bgN = 200))
    }

    @Test fun nameBoxFromWords() {
        val line = OcrLine("Ben Hartley: Hey", 100, 50, 400, 80, listOf(
            OcrLine("Ben", 100, 50, 140, 80), OcrLine("Hartley:", 150, 50, 270, 80), OcrLine("Hey", 290, 50, 340, 80)))
        val b = NameColor.nameBox(line, "Ben Hartley")!!
        // bez dvojtečky (1/8 šířky posledního slova) a bez okrajů
        assertEquals(listOf(103, 55, 252, 75), b.toList())
        // slova nesedí na jméno nebo chybí → null (odhad podle znaků)
        assertNull(NameColor.nameBox(line, "Tomas"))
        assertNull(NameColor.nameBox(OcrLine("Tomas: hi", 0, 0, 100, 30), "Tomas"))
    }

    // ---- stálá naučená barva ----

    @Test fun learnedColorDoesNotFlipOnOddReadings() {
        NameColor.reset()
        val y = 0xF2C14E; val p = 0xF07890
        NameColor.record("Lena", y, 1); NameColor.record("Lena", y, 1)
        assertEquals(SpeakerColors.PALETTE[0], NameColor.learned("Lena")?.let { NameColor.nearest(it) })
        // dvě divná čtení, pak zase žlutá, pak tři divná – pořád žlutá
        NameColor.record("Lena", p, 1); NameColor.record("Lena", p, 1)
        NameColor.record("Lena", y, 1)
        NameColor.record("Lena", p, 1); NameColor.record("Lena", p, 1); NameColor.record("Lena", p, 1)
        assertEquals(SpeakerColors.PALETTE[0], NameColor.learned("Lena")?.let { NameColor.nearest(it) })
        // čtvrté za sebou → vymění se
        NameColor.record("Lena", p, 1)
        assertEquals(SpeakerColors.PALETTE[3], NameColor.learned("Lena")?.let { NameColor.nearest(it) })
        NameColor.reset()
    }

    @Test fun firstLearningNeedsClearWinnerAndSettles() {
        NameColor.reset()
        val y = 0xF2C14E; val b = 0x70B8F4
        NameColor.record("Tomas", y, 1); NameColor.record("Tomas", b, 1); NameColor.record("Tomas", y, 1)
        // 2:1 stačí
        assertEquals(SpeakerColors.PALETTE[0], NameColor.learned("Tomas")?.let { NameColor.nearest(it) })
        NameColor.reset()
        NameColor.record("Tomas", y, 1); NameColor.record("Tomas", b, 1)
        NameColor.record("Tomas", y, 1); NameColor.record("Tomas", b, 1); NameColor.record("Tomas", b, 1)
        assertEquals(false, NameColor.settled("Tomas"))
        repeat(6) { NameColor.record("Tomas", b, 1) }
        assertEquals(SpeakerColors.PALETTE[1], NameColor.learned("Tomas")?.let { NameColor.nearest(it) })
        assertEquals(true, NameColor.settled("Tomas"))
        NameColor.reset()
    }

    @Test fun learnedIsRealGameColor() {
        // broskvové a světle žluté jméno – obě nejblíž zlaté z palety, ale naučí se skutečná barva
        NameColor.record("Nora", 0xF5C372, 1); NameColor.record("Nora", 0xF7C56F, 1)
        assertEquals(0xFFF6C470.toInt(), NameColor.learned("Nora"))
        NameColor.record("Viktor", 0x9DFFD5, 1); NameColor.record("Viktor", 0x9BFFD3, 1)
        assertEquals(0xFF9CFFD4.toInt(), NameColor.learned("Viktor"))
    }
}
