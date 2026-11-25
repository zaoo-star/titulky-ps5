package cz.recaption.core

import org.junit.Assert.*
import org.junit.Test

class SpeakerColorsTest {
    @Test fun sameNameKeepsItsColor() {
        val c = SpeakerColors()
        val mira = c.colorOf("Mira")
        c.colorOf("Tobin"); c.colorOf("Kessler")
        assertEquals(mira, c.colorOf("Mira"))
        // jméno velkými písmeny je tatáž postava
        assertEquals(mira, c.colorOf("MIRA"))
    }

    @Test fun firstSpeakerIsYellowLikeBefore() {
        assertEquals(SpeakerColors.PALETTE[0], SpeakerColors().colorOf("Marek"))
    }

    @Test fun speakersInOneConversationDiffer() {
        val c = SpeakerColors()
        val names = listOf("Mira", "Tobin", "Kessler", "Lena", "Davor", "Silas", "Jonas")
        val colors = names.map { c.colorOf(it) }
        assertEquals(names.size, colors.toSet().size)
    }

    @Test fun sameOrderGivesSameColorsEveryTime() {
        val names = listOf("Jonas", "Marek", "Vera", "Jonas", "Otto", "Marek")
        val a = SpeakerColors().let { c -> names.map { c.colorOf(it) } }
        val b = SpeakerColors().let { c -> names.map { c.colorOf(it) } }
        assertEquals(a, b)
    }

    @Test fun manySpeakersStillGetPaletteColors() {
        val c = SpeakerColors()
        for (i in 0 until 300) assertTrue(c.colorOf("Npc$i") in SpeakerColors.PALETTE)
    }

    @Test fun hexForTv() {
        assertEquals("#F2C14E", SpeakerColors.hex(SpeakerColors.PALETTE[0]))
    }
}

class DialogueChoicesTest {
    // řádek o výšce 24 px; šířka podle délky textu (12 px na znak)
    private fun at(t: String, left: Int, top: Int) = OcrLine(t, left, top, left + t.length * 12, top + 24)
    private fun leftList(vararg t: String, left: Int = 400, top: Int = 500) =
        t.mapIndexed { i, s -> at(s, left, top + i * 36) }
    private fun centered(vararg t: String, top: Int = 500) =
        t.mapIndexed { i, s -> at(s, 640 - s.length * 6, top + i * 36) }

    @Test fun numberedChoicesUnderQuestion() {
        val ls = listOf(at("Kessler: So, what will it be?", 470, 460)) +
            centered("1. I'll take the job.", "2. Tell me more about the pay.", "3. Not interested.")
        assertEquals(
            "Kessler: So, what will it be?\n• I'll take the job.\n• Tell me more about the pay.\n• Not interested.",
            TextLogic.choiceText(ls),
        )
    }

    @Test fun leftAlignedChoicesWithoutNumbers() {
        assertEquals(
            "• Ask about the money.\n• Threaten him\n• I'm leaving.",
            TextLogic.choiceText(leftList("Ask about the money.", "Threaten him", "I'm leaving.")),
        )
    }

    @Test fun gameMenuIsNotAChoice() {
        assertNull(TextLogic.choiceText(leftList("Continue", "New Game", "Options", "Quit")))
        assertNull(TextLogic.choiceText(leftList("1. Easy", "2. Normal", "3. Hard")))
        assertNull(TextLogic.choiceText(leftList("Resume", "Photo Mode", "Settings", "Quit Game")))
    }

    @Test fun objectivesAndHintsAreNotChoices() {
        assertNull(TextLogic.choiceText(leftList("Find the missing key", "Talk to the sheriff", "Return to camp")))
        assertNull(TextLogic.choiceText(leftList("Press X to talk", "Hold R2 to aim", "Select a weapon")))
    }

    @Test fun centeredSubtitleStaysOneSubtitle() {
        // tři krátké věty pod sebou na středu – obyčejný titulek, ne volby
        assertNull(TextLogic.choiceText(centered("Wait.", "Did you hear that?", "Someone's coming.")))
        // pomlčky střídají mluvčí ve filmových titulcích
        assertNull(TextLogic.choiceText(leftList("- We need to go.", "- Wait!", "- What now?")))
    }

    @Test fun wrappedParagraphIsNotAChoice() {
        // dialogové okno zarovnané vlevo: zalomená věta pokračuje malým písmenem
        assertNull(TextLogic.choiceText(leftList("I told you we should never", "have come back to this town,", "Jonas.")))
        // stejně dlouhé řádky zarovnané vlevo bez čísel – může to být odstavec
        assertNull(TextLogic.choiceText(leftList("Are you with me?", "I said I'm here.", "So let's go now.")))
        // dialogové okno RPG: celé věty pod sebou, všechny s tečkou, jméno zarovnané s nimi
        assertNull(TextLogic.choiceText(leftList("Mira: I can't believe it.", "We lost everything.", "I don't know what to do.", "Maybe we should leave.")))
    }

    @Test fun choicesUnderAQuestionPlacedElsewhere() {
        val ls = listOf(at("Marek: Well, Jonas?", 520, 440)) + leftList("Ask about the money", "Threaten him", "I'll do it.", top = 480)
        assertEquals("Marek: Well, Jonas?\n• Ask about the money\n• Threaten him\n• I'll do it.", TextLogic.choiceText(ls))
    }

    @Test fun twoLinesAreNeverChoices() {
        assertNull(TextLogic.choiceText(leftList("1. Yes, I will.", "2. No, I won't.")))
    }

    @Test fun assembleKeepsChoicesAsList() {
        val block = OcrBlock(leftList("Ask about the money.", "Threaten him", "I'm leaving.", left = 560))
        val p = TextLogic.assemble(listOf(block), 10, true, 640, 150, maxLineHeight = 60)
        assertEquals("• Ask about the money.\n• Threaten him\n• I'm leaving.", p.text)
        assertEquals(3, p.lineCount)
        val segs = TextLogic.segments(p.text)
        assertTrue(segs.all { it.choice && it.speaker == null })
        assertEquals("Threaten him", segs[1].body)
        assertEquals(p.text, TextLogic.joinSegments(segs))
        assertTrue(TextLogic.hasChoices(p.text))
        assertFalse(TextLogic.hasChoices("Mira: Hello.\nTobin: Hi."))
    }

    @Test fun assembleStillDropsMenus() {
        // položky menu čte OCR každou zvlášť
        val blocks = leftList("Continue", "New Game", "Options", "Quit", left = 560).map { OcrBlock(listOf(it)) }
        assertEquals("", TextLogic.assemble(blocks, 10, true, 640, 150, maxLineHeight = 60).text)
    }

    @Test fun ordinarySubtitlesAreUnchanged() {
        val block = OcrBlock(listOf(at("Mira: I'll take the left door.", 460, 500), at("Tobin: Then I'm going right.", 470, 536)))
        val p = TextLogic.assemble(listOf(block), 10, true, 640, 150, maxLineHeight = 60)
        assertEquals("Mira: I'll take the left door.\nTobin: Then I'm going right.", p.text)
        assertTrue(TextLogic.segments(p.text).none { it.choice })
    }

    @Test fun demoGameHasTwoSpeakersAndChoices() {
        val choice = DemoGame.lines.first { it.startsWith("Kessler:") }
        val rows = choice.split('\n')
        // jako v TestCard: otázka na středu, volby zarovnané vlevo
        val ls = rows.mapIndexed { i, s -> if (DemoGame.isChoiceLine(s)) at(s, 460, 440 + i * 36) else at(s, 640 - s.length * 6, 440 + i * 36) }
        val text = TextLogic.choiceText(ls)!!
        val segs = TextLogic.segments(text)
        assertEquals("Kessler", segs[0].speaker)
        assertEquals(3, segs.count { it.choice })
        assertTrue(DemoGame.lines.any { l -> TextLogic.segments(l).mapNotNull { it.speaker }.distinct().size == 2 })
    }
}

class TvPartsTest {
    @Test fun oldFieldsStayAndPartsAreOptional() {
        assertEquals("{\"typ\":\"ukaz\",\"id\":3,\"text\":\"Ahoj\"}", TvSender.showMessage(3, "Ahoj"))
        val segs = listOf(Segment("Mira", "Vezmu levé dveře."), Segment(null, "Vezmu tu práci.", choice = true), Segment(null, "Tak."))
        val parts = TvSender.partsJson(segs, mapOf("Mira" to SpeakerColors.PALETTE[1]))
        assertEquals(
            "[{\"jmeno\":\"Mira\",\"barva\":\"#7FC8F8\",\"text\":\"Vezmu levé dveře.\"}," +
                "{\"volba\":true,\"text\":\"Vezmu tu práci.\"},{\"text\":\"Tak.\"}]",
            parts,
        )
        val msg = TvSender.showMessage(4, TextLogic.joinSegments(segs), parts)
        assertTrue(msg.startsWith("{\"typ\":\"ukaz\",\"id\":4,\"text\":\"Mira: Vezmu levé dveře.\\n• Vezmu tu práci.\\nTak.\",\"casti\":["))
    }

    @Test fun withoutColorsNoColorIsSent() {
        assertEquals("[{\"jmeno\":\"Mira\",\"text\":\"Ahoj.\"}]", TvSender.partsJson(listOf(Segment("Mira", "Ahoj."))))
    }
}
