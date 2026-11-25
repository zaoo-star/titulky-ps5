package cz.recaption.core

import cz.recaption.core.SubtitleTracker.Action
import org.junit.Assert.*
import org.junit.Test

/**
 * Víceřádkový titulek se nesmí ztratit, když OCR na nezměněném obraze přečte jen jeho část
 * (např. jen poslední řádek). Hlášeno u podobné aplikace: z třířádkového textu zůstal
 * v pruhu jen překlad posledního řádku „Botník na temném místě.“
 */
class LostLinesTest {
    private val full = "The Shoebox can store your items.\nAlan can access stored items from\nany Shoebox in the Dark Place."
    private val last = "any Shoebox in the Dark Place."

    private val cell = 100

    /** Otisk obrazu; [seed] jiný = jiný obraz (posun jasu v buňkách nad hranicí změny). */
    private fun sig(seed: Int): IntArray = IntArray(FrameSignature.SIZE) { (it % 7 + seed * 20) * cell }

    private fun tracker() = SubtitleTracker(hideAfterEmpty = 3, fastComplete = true)

    /** Projede čtení (text, otisk) a potvrzuje zobrazení jako služba. */
    private fun run(t: SubtitleTracker, reads: List<Pair<String, IntArray?>>): List<Action> = reads.map { (text, s) ->
        val a = t.onOcr(text, sig = s, cellPixels = cell)
        if (a is Action.Show) t.confirmShown(a.text)
        a
    }

    @Test fun lastLineOnSameImageDoesNotReplaceFullText() {
        val t = tracker()
        val a = run(t, listOf(full to sig(1), last to sig(1), last to sig(1).copyOf()))
        assertEquals(Action.Show(full), a[0])
        assertEquals(Action.None, a[1])
        assertEquals(Action.None, a[2])
        assertEquals(full, t.shown)
        assertFalse("ochuzené čtení není nový titulek", t.isChanging)
    }

    @Test fun withoutSignatureOldBehaviourStays() {
        // bez otisku (neznámý obraz) se nedá říct, že se obraz nezměnil → kratší čtení text nahradí
        // (od 0.97.83 až po druhém shodném čtení)
        val t = tracker()
        run(t, listOf(full to null, last to null, last to null))
        assertEquals(last, t.shown)
    }

    @Test fun middleLinesAndOcrTyposAreStillSubset() {
        val t = tracker()
        run(t, listOf(
            full to sig(1),
            "Alan can access stored items from\nany Shoebax in the Dark Place." to sig(1),
            "Shoebox can store" to sig(1),
        ))
        assertEquals(full, t.shown)
    }

    @Test fun emptyReadOnSameImageDoesNotHide() {
        val t = tracker()
        val a = run(t, listOf(full to sig(1)) + List(5) { "" to sig(1) })
        assertFalse(a.contains(Action.Hide))
        assertEquals(full, t.shown)
    }

    @Test fun changedImageMayShrinkText() {
        // zkrácení se ukáže po druhém shodném čtení (od 0.97.83 – jedno čtení jen části titulku pruh nepřepíše)
        val t = tracker()
        val a = run(t, listOf(full to sig(1), last to sig(2), last to sig(2)))
        assertEquals(Action.None, a[1])
        assertEquals(Action.Show(last), a[2])
        assertEquals(last, t.shown)
    }

    @Test fun changedImageMayHideText() {
        val t = tracker()
        val a = run(t, listOf(full to sig(1), "" to sig(2), "" to sig(2), "" to sig(2)))
        assertEquals(Action.Hide, a[3])
        assertEquals("", t.shown)
    }

    @Test fun textMayGrowOnSameImage() {
        // první čtení zachytilo jen poslední řádek, další celý titulek → pruh se doplní
        val t = tracker()
        val a = run(t, listOf(last to sig(1), full to sig(1)))
        assertEquals(Action.Show(full), a[1])
        assertEquals(full, t.shown)
    }

    @Test fun correctionOnSameImageStillWorks() {
        val t = tracker()
        // oprava po potvrzení dalším čtením (od 0.97.83)
        val a = run(t, listOf("The marshals wil find us." to sig(1), "The marshals will find us." to sig(1), "The marshals will find us." to sig(1)))
        assertEquals(Action.Show("The marshals will find us."), a[2])
    }

    @Test fun differentTextOnSameImageIsNotSubset() {
        val t = tracker()
        val a = run(t, listOf("Hello there, Jonas." to sig(1), "Ride with me." to sig(1)))
        assertEquals(Action.Show("Ride with me."), a[1])
    }

    @Test fun ignoredReadsStillLetImageSettle() {
        val t = tracker()
        run(t, listOf(full to sig(1), last to sig(1), last to sig(1), last to sig(1)))
        assertTrue("obraz se nemění → přestane se zbytečně číst", t.isSettled)
    }

    @Test fun referenceFollowsImageWhileTextStays() {
        // obraz se pohne (scéna za titulkem), text zůstává celý → nový obraz je nový vzor
        val t = tracker()
        run(t, listOf(full to sig(1), full to sig(2), last to sig(2)))
        assertEquals(full, t.shown)
    }

    @Test fun simulatedPlayKeepsAllThreeLines() {
        // simulace hraní přes skládání řádků z OCR (jako ve službě)
        fun block(vararg lines: String) = listOf(OcrBlock(lines.mapIndexed { i, s -> OcrLine(s, 300, 600 + i * 32, 980, 626 + i * 32) }))
        val frames = listOf(
            block("The Shoebox can store your items.", "Alan can access stored items from", "any Shoebox in the Dark Place.") to sig(1),
            block("any Shoebox in the Dark Place.") to sig(1),
            block("any Shoebox in the Dark Place.") to sig(1),
            block() to sig(1),
        )
        val t = tracker()
        val shown = ArrayList<String>()
        for ((f, s) in frames) {
            val a = t.onOcr(TextLogic.assemble(f, minLineHeight = 12).text, sig = s, cellPixels = cell)
            if (a is Action.Show) { shown += a.text; t.confirmShown(a.text) }
            assertNotEquals(Action.Hide, a)
        }
        assertEquals(1, shown.size)
        assertTrue(shown[0].startsWith("The Shoebox") && shown[0].endsWith("Dark Place."))
        assertEquals(shown[0], t.shown)
    }

    // ---------- zastaralé výsledky ----------

    @Test fun lateTranslationAfterResetIsDropped() {
        val t = tracker()
        val a = t.onOcr(full, sig = sig(1), cellPixels = cell)
        val token = t.revision
        assertTrue(a is Action.Show)
        t.reset()   // pauza / změna jazyka / výpadek spojení, než překlad doběhl
        assertFalse(t.isCurrent(token))
        assertFalse(t.confirmShown(full, token))
        assertEquals("", t.shown)
    }

    @Test fun lateResultAfterRegionChangeIsDropped() {
        val t = tracker()
        val token = t.revision
        t.regionChanged()
        assertFalse(t.isCurrent(token))
        assertFalse(t.confirmShown(full, token))
        assertEquals("", t.shown)
    }

    @Test fun currentTokenIsAccepted() {
        val t = tracker()
        val token = t.revision
        t.onOcr(full, sig = sig(1), cellPixels = cell)
        assertTrue(t.isCurrent(token))
        assertTrue(t.confirmShown(full, token))
        assertEquals(full, t.shown)
    }

    @Test fun afterRegionChangeImagesAreNotCompared() {
        // jiná oblast = otisky nejdou porovnat → kratší čtení se bere jako dřív
        val t = tracker()
        run(t, listOf(full to sig(1)))
        t.regionChanged()
        run(t, listOf(last to sig(1), last to sig(1)))   // zkrácení po druhém shodném čtení (od 0.97.83)
        assertEquals(last, t.shown)
    }

    // ---------- TextLogic.isSubsetRead ----------

    @Test fun subsetRead() {
        assertTrue(TextLogic.isSubsetRead("", full))
        assertTrue(TextLogic.isSubsetRead(last, full))
        assertTrue(TextLogic.isSubsetRead("The Shoebox can store your items.", full))
        assertTrue(TextLogic.isSubsetRead("any Shoebax in the Dark Place", full))   // chyba OCR
        assertFalse(TextLogic.isSubsetRead(full, full))                             // stejně dlouhé
        assertFalse(TextLogic.isSubsetRead("$full Press X.", full))                 // delší
        assertFalse(TextLogic.isSubsetRead("Ride with me.", full))                  // jiný text
        assertFalse(TextLogic.isSubsetRead("Dark Place the in", full))              // jiné pořadí
        assertFalse(TextLogic.isSubsetRead("", ""))
    }
}
