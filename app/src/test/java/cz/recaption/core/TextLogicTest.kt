package cz.recaption.core

import org.junit.Assert.*
import org.junit.Test

class TextLogicTest {

    private fun line(t: String, top: Int, h: Int = 30) = OcrLine(t, 100, top, 900, top + h)

    // ---------- clean ----------
    @Test fun clean_fixesPipeAndSpaces() {
        assertEquals("I don't know, Marek.", TextLogic.clean("  |  don’t   know,  Marek. "))
    }

    @Test fun clean_fixesLowercaseLAsPronoun() {
        assertEquals("I think we should go.", TextLogic.clean("l think we should go."))
        assertEquals("Well, I reckon so.", TextLogic.clean("Well, l reckon so."))
        // nesmí rozbít normální slova
        assertEquals("look at that", TextLogic.clean("look at that"))
        assertEquals("1899 was a year", TextLogic.clean("1899 was a year"))
    }

    // ---------- isUseful ----------
    @Test fun useful_acceptsRealSubtitles() {
        listOf(
            "We need money, Jonas.",
            "Marek: Have some faith.",
            "Press X to hogtie",
            "Ride to Greyford.",
            "Yes.", "No!", "Hello",
            "Get on your horse."
        ).forEach { assertTrue("mělo projít: $it", TextLogic.isUseful(it)) }
    }

    @Test fun useful_rejectsHudAndNoise() {
        listOf(
            "$12.50", "12/24", "N", "E", "1:45 PM", "x3", "100%", "-", "...", "ii", "6", "|", "~~ ~", "a"
        ).forEach { assertFalse("nemělo projít: $it", TextLogic.isUseful(it)) }
    }

    // ---------- assemble ----------
    @Test fun assemble_joinsLinesOfBlockAndSortsBlocks() {
        val subtitle = OcrBlock(listOf(line("we need to get out of here", 640), line("Marek: Jonas,", 600)))
        val hint = OcrBlock(listOf(line("Press X to mount", 50)))
        val p = TextLogic.assemble(listOf(subtitle, hint), minLineHeight = 12)
        assertEquals("Press X to mount\nMarek: Jonas, we need to get out of here", p.text)
        assertEquals(3, p.lineCount)
    }

    @Test fun assemble_dropsTinyTextAndHud() {
        val b = OcrBlock(listOf(line("Ride to Greyford.", 600), line("$45.20", 640), line("tiny tiny text", 680, h = 5)))
        assertEquals("Ride to Greyford.", TextLogic.assemble(listOf(b), 12).text)
    }

    @Test fun assemble_emptyWhenNothingUseful() {
        val b = OcrBlock(listOf(line("12", 600), line("N", 640)))
        val p = TextLogic.assemble(listOf(b), 12)
        assertEquals("", p.text)
        assertEquals(0, p.lineCount)
    }

    @Test fun assemble_mergesContinuationOfSpeakerLine() {
        val a = OcrBlock(listOf(line("Otto: If we keep going like this", 600)))
        val b = OcrBlock(listOf(line("we will all hang.", 640)))
        assertEquals("Otto: If we keep going like this we will all hang.", TextLogic.assemble(listOf(a, b), 12).text)
    }

    // ---------- mluvčí ----------
    @Test fun speaker_detected() {
        assertEquals(Segment("Marek", "We need money."), TextLogic.splitSpeaker("Marek: We need money."))
        assertEquals(Segment("Grandpa", "My back hurts."), TextLogic.splitSpeaker("Grandpa: My back hurts."))
        assertEquals(Segment("Anna-Marie", "Hello."), TextLogic.splitSpeaker("Anna-Marie: Hello."))
        assertEquals(Segment("Rory Mcallen", "Ha!"), TextLogic.splitSpeaker("Rory Mcallen: Ha!"))
    }

    @Test fun speaker_notDetectedForHintsAndPlainText() {
        assertNull(TextLogic.splitSpeaker("Objective: Ride to camp.").speaker)
        assertNull(TextLogic.splitSpeaker("We ride at dawn.").speaker)
        assertNull(TextLogic.splitSpeaker("time: 10:45").speaker)
        assertNull(TextLogic.splitSpeaker("Tip: Press X").speaker)
    }

    @Test fun segments_multiLine() {
        val s = TextLogic.segments("Press X to mount\nMarek: Go!")
        assertEquals(2, s.size)
        assertNull(s[0].speaker)
        assertEquals("Marek", s[1].speaker)
        assertEquals("Go!", s[1].body)
    }

    // ---------- podobnost ----------
    @Test fun similar_toleratesOcrJitter() {
        assertTrue(TextLogic.similar("We need money, Jonas.", "We need rnoney, Jonas."))
        assertTrue(TextLogic.similar("We need money, Jonas.", "We need money, Jonas"))
        assertTrue(TextLogic.similar("", ""))
    }

    @Test fun similar_distinguishesDifferentLines() {
        assertFalse(TextLogic.similar("We need money, Jonas.", "Get on your horse."))
        assertFalse(TextLogic.similar("Yes.", "No."))
        assertFalse(TextLogic.similar("", "Hello"))
        assertFalse(TextLogic.similar("Hello there", ""))
    }

    @Test fun levenshtein_basic() {
        assertEquals(3, TextLogic.levenshtein("kitten", "sitting"))
        assertEquals(0, TextLogic.levenshtein("abc", "abc"))
        assertEquals(3, TextLogic.levenshtein("", "abc"))
    }
}

class SubtitleShapesTest {
    private fun line(t: String, l: Int, r: Int, top: Int = 0) = OcrLine(t, l, top, r, top + 24)

    @Test fun speakerInCapitalsAndWithoutSpace() {
        assertEquals(Segment("MIRA", "Are you sure?"), TextLogic.splitSpeaker("MIRA: Are you sure?"))
        assertEquals(Segment("Tobin", "no idea"), TextLogic.splitSpeaker("Tobin:no idea"))
        assertEquals("Mira", TextLogic.normalizeName("MIRA"))
        assertEquals("Anna-Marie", TextLogic.normalizeName("ANNA-MARIE"))
        assertEquals("Old Kessler", TextLogic.normalizeName("OLD KESSLER"))
        assertEquals("Tobin", TextLogic.normalizeName("Tobin"))
    }

    @Test fun speakerInBrackets() {
        assertEquals(Segment("Old Kessler", "You two were never supposed to meet."),
            TextLogic.splitSpeaker("[Old Kessler] You two were never supposed to meet."))
    }

    @Test fun timeIsNotASpeaker() {
        assertNull(TextLogic.splitSpeaker("At 10:45 we leave.").speaker)
        assertNull(TextLogic.splitSpeaker("OBJECTIVE: Find the key").speaker)
    }

    @Test fun capitalsBecomeASentence() {
        assertEquals("We have to get out of here!", TextLogic.normalizeCase("WE HAVE TO GET OUT OF HERE!"))
        assertEquals("Run! I'm right behind you.", TextLogic.normalizeCase("RUN! I'M RIGHT BEHIND YOU."))
        assertEquals("What did I say?", TextLogic.normalizeCase("WHAT DID I SAY?"))
        // běžný text se nemění
        assertEquals("We need money, Jonas.", TextLogic.normalizeCase("We need money, Jonas."))
        assertEquals("no idea, but OK", TextLogic.normalizeCase("no idea, but OK"))
    }

    @Test fun translationKeepsFirstCapital() {
        assertEquals("Potřebujeme peníze.", TextLogic.matchFirstCase("We need money.", "potřebujeme peníze."))
        assertEquals("netuším", TextLogic.matchFirstCase("no idea", "netuším"))
        assertEquals("„Počkej!“", TextLogic.matchFirstCase("\"Wait!\"", "„počkej!“"))
    }

    @Test fun shortSingleWordsAreSubtitles() {
        assertTrue(TextLogic.isUseful("Wait"))
        assertTrue(TextLogic.isUseful("RUN"))
        assertTrue(TextLogic.isUseful("No."))
        assertFalse(TextLogic.isUseful("x2"))
        assertFalse(TextLogic.isUseful("Il"))
    }

    @Test fun speakerOnItsOwnLineJoinsTheText() {
        val blocks = listOf(
            OcrBlock(listOf(line("MIRA:", 600, 680, 0))),
            OcrBlock(listOf(line("Are you sure this is the right way?", 400, 880, 30))),
        )
        val p = TextLogic.assemble(blocks, 10, subtitlesOnly = true, screenCenterX = 640, centerTolerance = 150)
        assertEquals("MIRA: Are you sure this is the right way?", p.text)
        assertEquals(Segment("MIRA", "Are you sure this is the right way?"), TextLogic.segments(p.text).single())
    }

    @Test fun nameAloneIsNotASubtitle() {
        val p = TextLogic.assemble(listOf(OcrBlock(listOf(line("MIRA:", 600, 680)))), 10, true, 640, 150)
        assertEquals("", p.text)
    }

    @Test fun leftAlignedLinesInsideCenteredSubtitle() {
        // druhý řádek je kratší a zarovnaný vlevo – sám o sobě vycentrovaný není
        val block = OcrBlock(listOf(
            line("If we keep going like this, they will find us before", 240, 1040, 0),
            line("dawn.", 240, 330, 30),
        ))
        val p = TextLogic.assemble(listOf(block), 10, true, 640, 150)
        assertEquals("If we keep going like this, they will find us before dawn.", p.text)
    }

    @Test fun twoSpeakersAtOnce() {
        val segs = TextLogic.segments("Mira: I'll take the left door.\nTobin: Then I'm going right.")
        assertEquals(listOf("Mira", "Tobin"), segs.map { it.speaker })
    }

    @Test fun hudOnTheSideIsStillIgnored() {
        val blocks = listOf(
            OcrBlock(listOf(line("Press X to mount", 40, 300))),
            OcrBlock(listOf(line("New objective added", 1000, 1260))),
            OcrBlock(listOf(line("We ride at dawn.", 520, 760, 60))),
        )
        assertEquals("We ride at dawn.", TextLogic.assemble(blocks, 10, true, 640, 150).text)
    }
}

class SubtitleClusterTest {
    private fun line(t: String, l: Int, r: Int, top: Int) = OcrLine(t, l, top, r, top + 24)

    @Test fun onlyTheLowestGroupIsTheSubtitle() {
        // nadpis uprostřed obrazovky a titulek dole: pruh má zakrýt jen titulek
        val blocks = listOf(
            OcrBlock(listOf(line("Chapter Three", 520, 760, 20))),
            OcrBlock(listOf(line("We ride at dawn.", 500, 780, 300), line("Get some sleep.", 510, 770, 330))),
        )
        val p = TextLogic.assemble(blocks, 10, subtitlesOnly = true, screenCenterX = 640, centerTolerance = 150)
        assertEquals("We ride at dawn. Get some sleep.", p.text)
        assertEquals(300, p.top); assertEquals(354, p.bottom)
        assertEquals(500, p.left); assertEquals(780, p.right)
    }

    @Test fun linesCloseTogetherStayTogether() {
        val blocks = listOf(
            OcrBlock(listOf(line("Mira: I'll take the left door.", 420, 860, 300))),
            OcrBlock(listOf(line("Tobin: Then I'm going right.", 440, 840, 332))),
        )
        val p = TextLogic.assemble(blocks, 10, true, 640, 150)
        assertEquals("Mira: I'll take the left door.\nTobin: Then I'm going right.", p.text)
    }

    @Test fun withoutSubtitleModeEverythingIsRead() {
        val blocks = listOf(
            OcrBlock(listOf(line("Chapter Three", 520, 760, 20))),
            OcrBlock(listOf(line("We ride at dawn.", 500, 780, 300))),
        )
        assertEquals("Chapter Three\nWe ride at dawn.", TextLogic.assemble(blocks, 10).text)
    }
}

class OcrRepairTest {
    @Test fun missingApostrophes() {
        assertEquals("Mira: I'll take the left door.", TextLogic.clean("Mira: Ill take the left door."))
        assertEquals("I'll go.", TextLogic.clean("IIl go."))
        assertEquals("I don't know what's there.", TextLogic.clean("I dont know whats there."))
        assertEquals("Don't move, you're hurt.", TextLogic.clean("Dont move, youre hurt."))
        // skutečná slova zůstávají
        assertEquals("He was ill for days.", TextLogic.clean("He was ill for days."))
        assertEquals("Illness spreads.", TextLogic.clean("Illness spreads."))
    }

    @Test fun bracketNameWithSmallFirstLetter() {
        val s = TextLogic.splitSpeaker("[old Kessler] You two were never supposed to meet.")
        assertEquals("old Kessler", s.speaker)
        assertEquals("Old Kessler", TextLogic.normalizeName(s.speaker!!))
    }

    @Test fun singleWordGetsADotForTheTranslator() {
        assertEquals("Wait.", TextLogic.forTranslator("Wait"))
        assertEquals("Wait!", TextLogic.forTranslator("Wait!"))
        assertEquals("We ride", TextLogic.forTranslator("We ride"))
        assertEquals("Počkej", TextLogic.finishTranslation("Wait", "počkej."))
        assertEquals("Prosím", TextLogic.finishTranslation("Wait", ", Prosím"))
        assertEquals("Počkej!", TextLogic.finishTranslation("Wait!", "Počkej!"))
        assertEquals("Potřebujeme peníze.", TextLogic.finishTranslation("We need money.", "potřebujeme peníze."))
    }

    @Test fun longerReadingFixesTheShownText() {
        val t = SubtitleTracker(fastComplete = true)
        assertEquals(SubtitleTracker.Action.Show("The marshals wil find us."), t.onOcr("The marshals wil find us."))
        t.confirmShown("The marshals wil find us.")
        // oprava až po potvrzení dalším čtením (jedno čtení jiné varianty překlad nemění, od 0.97.83)
        assertEquals(SubtitleTracker.Action.None, t.onOcr("The marshals will find us."))
        assertEquals(SubtitleTracker.Action.Show("The marshals will find us."), t.onOcr("The marshals will find us."))
        t.confirmShown("The marshals will find us.")
        assertEquals(SubtitleTracker.Action.None, t.onOcr("The marshals will find us."))
        assertEquals(SubtitleTracker.Action.None, t.onOcr("The marshals wil find us."))
    }
}

class QuickWordsAndSpeakersTest {
    private fun line(t: String, l: Int, r: Int, top: Int) = OcrLine(t, l, top, r, top + 24)

    @Test fun twoSpeakersInOneBlockAreTwoReplies() {
        val block = OcrBlock(listOf(
            line("Mira: I'll take the left door.", 420, 860, 300),
            line("Tobin: Then I'm going right.", 440, 840, 332),
        ))
        val p = TextLogic.assemble(listOf(block), 10, true, 640, 150)
        assertEquals("Mira: I'll take the left door.\nTobin: Then I'm going right.", p.text)
        assertEquals(listOf("Mira", "Tobin"), TextLogic.segments(p.text).map { it.speaker })
    }

    @Test fun secondLineWithoutSpeakerStaysInTheSameReply() {
        val block = OcrBlock(listOf(
            line("Silas: If we keep going like this,", 420, 860, 300),
            line("the marshals will find us.", 440, 840, 332),
        ))
        assertEquals("Silas: If we keep going like this, the marshals will find us.",
            TextLogic.assemble(listOf(block), 10, true, 640, 150).text)
    }
}

class DialogueBoxTest {
    private fun line(t: String, l: Int, r: Int, top: Int) = OcrLine(t, l, top, r, top + 24)

    @Test fun leftAlignedDialogueBoxIsRead() {
        // okno s rozhovorem: text začíná vlevo a sahá přes střed, poslední řádek je krátký
        val block = OcrBlock(listOf(
            line("I have been waiting for you since the war ended,", 260, 900, 300),
            line("my friend.", 260, 380, 332),
        ))
        val p = TextLogic.assemble(listOf(block), 10, true, 640, 150)
        assertEquals("I have been waiting for you since the war ended, my friend.", p.text)
    }

    @Test fun cornersAreStillIgnored() {
        val blocks = listOf(
            OcrBlock(listOf(line("Find the missing key", 30, 330, 300))),     // úkol vlevo dole
            OcrBlock(listOf(line("Northern Outpost", 980, 1250, 300))),       // místo vpravo dole
        )
        assertEquals("", TextLogic.assemble(blocks, 10, true, 640, 150).text)
    }
}

class DialogueOnlyTest {
    private fun line(t: String, l: Int, r: Int, top: Int, h: Int = 24) = OcrLine(t, l, top, r, top + h)
    private fun pick(vararg ls: OcrLine) =
        TextLogic.assemble(listOf(OcrBlock(ls.toList())), 10, true, 640, 150, maxLineHeight = 60).text

    @Test fun realRepliesAreDialogue() {
        for (t in listOf(
            "We need to leave before sunrise.", "Mira: Are you sure?", "MIRA: no idea", "Wait", "Come on!",
            "No.", "I don't know", "WE HAVE TO GET OUT OF HERE!", "[Old Kessler] You two were never supposed to meet.",
            "Tobin: no idea, but we can't stay here", "Keep going, we're almost there",
            "Silas: If we keep going like this,\nthe marshals will find us.",
        )) assertTrue(t, TextLogic.looksLikeDialogue(t))
    }

    @Test fun menuItemsAndNoiseAreNot() {
        for (t in listOf(
            "Som", "New Game", "OPTIONS", "Load Game", "Continue", "Settings", "Old Mill Road", "Chapter Three",
            "GREAT GAMES GREAT VALUE", "Graphics Audio Controls", "Continue\nNew Game\nOptions\nQuit",
            "Inventory", "Level 12", "Back", "Select",
        )) assertFalse(t, TextLogic.looksLikeDialogue(t))
    }

    @Test fun menuItemInTheMiddleOfTheScreenIsNotTranslated() {
        assertEquals("", pick(line("New Game", 560, 720, 300)))
        assertEquals("", pick(line("Som", 600, 680, 300)))
    }

    @Test fun hugeLettersAreNotSubtitles() {
        // logo nebo nadpis: řádek vyšší než strop
        assertEquals("", pick(line("Welcome back, traveller.", 300, 980, 200, h = 90)))
        assertEquals("Welcome back, traveller.", pick(line("Welcome back, traveller.", 300, 980, 200, h = 40)))
    }

    @Test fun screenFullOfTextIsAMenu() {
        val blocks = (0 until 8).map { i -> OcrBlock(listOf(line("We ride at dawn, friend.", 400, 880, i * 40))) }
        assertEquals("", TextLogic.assemble(blocks, 10, true, 640, 150).text)
        // pár údajů v rozích kolem titulku nevadí
        val hud = listOf(
            OcrBlock(listOf(line("Find the missing key", 30, 330, 0))),
            OcrBlock(listOf(line("Northern Outpost", 980, 1250, 0))),
            OcrBlock(listOf(line("We ride at dawn, friend.", 400, 880, 300))),
        )
        assertEquals("We ride at dawn, friend.", TextLogic.assemble(hud, 10, true, 640, 150).text)
    }

    @Test fun withoutSubtitleModeNothingIsDropped() {
        assertEquals("New Game", TextLogic.assemble(listOf(OcrBlock(listOf(line("New Game", 560, 720, 300)))), 10).text)
    }
}

class GameMenuPhotosTest {
    // přesně to, co aplikace přeložila na fotkách z televize – nic z toho není replika
    @Test fun textsFromTheGameMenuAreNotTranslated() {
        for (t in listOf(
            "Press to Start", "Press any button to continue", "Passes & Fn CREW", "Passes & Fn Crew",
            "GET BATTLE PASS", "Bonus Rewards", "Available through DEC 04 2026", "Choose a Set to Unlock",
            "Signed in as: player", "Loading", "Select a mode",
        )) assertFalse(t, TextLogic.looksLikeDialogue(t))
    }

    @Test fun repliesStartingWithTheSameWordsStillPass() {
        for (t in listOf("Hold on to me.", "Press on, we're almost there!", "Choose wisely, my friend."))
            assertTrue(t, TextLogic.looksLikeDialogue(t))
    }
}

class SlangForAllLanguagesTest {
    // překladač dostane spisovnou angličtinu – pomáhá to ve všech jazycích stejně
    @Test fun spokenFormsBecomeStandardEnglish() {
        assertEquals("come on, we have to go", OfflinePrep.standardize("c'mon, we hafta go"))
        assertEquals("You should have told me.", OfflinePrep.standardize("You shoulda told me."))
        assertEquals("I am trying to help", OfflinePrep.standardize("I am tryna help"))
        assertEquals("what are you doing about it", OfflinePrep.standardize("whatcha doing 'bout it"))
    }

    @Test fun pisneSeNeprekladaji() {
        assertTrue(TextLogic.isLyrics("♪ I'm on my way, baby ♪"))
        assertTrue(TextLogic.isLyrics("Driving down the coast ♫"))
        assertTrue(TextLogic.isLyrics("🎵 Sunshine on the boulevard 🎶"))
        assertFalse(TextLogic.isLyrics("Lena: Turn the radio down."))
        val music = OcrBlock(listOf(OcrLine("♪ Sunshine on the boulevard ♪", 700, 900, 1220, 930)))
        val talk = OcrBlock(listOf(OcrLine("Tomas: Where are we going?", 700, 950, 1220, 980)))
        val p = TextLogic.assemble(listOf(music, talk), 10, subtitlesOnly = true, screenCenterX = 960, centerTolerance = 120)
        assertEquals("Tomas: Where are we going?", p.text)
    }
}
