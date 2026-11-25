package cz.recaption.core

import cz.recaption.core.SubtitleTracker.Action
import org.junit.Assert.*
import org.junit.Test

class SubtitleTrackerTest {

    /** Pomůcka: projede sekvenci OCR výsledků a vrátí akce (a potvrzuje zobrazení jako služba). */
    private fun run(t: SubtitleTracker, frames: List<String>): List<Action> = frames.map { f ->
        val a = t.onOcr(f)
        if (a is Action.Show) t.confirmShown(a.text)
        a
    }

    @Test fun showsAfterTwoStableFrames() {
        val t = SubtitleTracker()
        val a = run(t, listOf("Hello there, Jonas.", "Hello there, Jonas."))
        assertEquals(Action.None, a[0])
        assertEquals(Action.Show("Hello there, Jonas."), a[1])
    }

    @Test fun doesNotRepeatSameSubtitle() {
        val t = SubtitleTracker()
        val a = run(t, List(6) { "Hello there, Jonas." })
        assertEquals(1, a.count { it is Action.Show })
    }

    @Test fun ocrJitterDoesNotRetrigger() {
        val t = SubtitleTracker()
        val a = run(t, listOf("We need money.", "We need money.", "We need rnoney.", "We need money", "We need money."))
        assertEquals(1, a.count { it is Action.Show })
    }

    @Test fun hidesAfterTextDisappears() {
        val t = SubtitleTracker()
        val a = run(t, listOf("Ride on.", "Ride on.", "", "", ""))
        assertEquals(Action.Show("Ride on."), a[1])
        assertEquals(Action.None, a[2])          // jeden prázdný snímek ještě neschová (blikání)
        assertEquals(Action.Hide, a[3])
        assertEquals(Action.None, a[4])          // neschovává opakovaně
        assertEquals("", t.shown)
    }

    @Test fun singleEmptyFrameDoesNotHide() {
        val t = SubtitleTracker()
        val a = run(t, listOf("Ride on.", "Ride on.", "", "Ride on.", "Ride on."))
        assertFalse(a.contains(Action.Hide))
        assertEquals(1, a.count { it is Action.Show })
    }

    @Test fun switchesToNextLine() {
        val t = SubtitleTracker()
        val a = run(t, listOf("First line here.", "First line here.", "Second one now.", "Second one now."))
        assertEquals(listOf(Action.Show("First line here."), Action.Show("Second one now.")), a.filterIsInstance<Action.Show>())
    }

    @Test fun singleGarbageFrameIgnored() {
        val t = SubtitleTracker()
        val a = run(t, listOf("Hello friend.", "Hello friend.", "Hcl1o fr", "Hello friend."))
        assertEquals(1, a.count { it is Action.Show })
    }

    @Test fun prefersLongerVariantOfSimilarText() {
        val t = SubtitleTracker()
        val a = run(t, listOf("We need money, Jonas", "We need money, Jonas."))
        assertEquals(Action.Show("We need money, Jonas."), a[1])
    }

    @Test fun failedTranslationRetriesNextFrame() {
        val t = SubtitleTracker()
        assertEquals(Action.None, t.onOcr("Hello there."))
        assertTrue(t.onOcr("Hello there.") is Action.Show)   // překlad selže -> nepotvrdíme
        assertTrue(t.onOcr("Hello there.") is Action.Show)   // zkusí se znovu
    }

    @Test fun notSettledWhileTranslationPending() {
        val t = SubtitleTracker()
        t.onOcr("Hello there."); t.onOcr("Hello there.")   // Show, ale překlad selhal (bez confirm)
        assertFalse("musí dál číst a zkoušet překlad", t.isSettled)
        t.confirmShown("Hello there.")
        assertTrue(t.isSettled)
    }

    @Test fun settledFlag() {
        val t = SubtitleTracker()
        assertFalse(t.isSettled)
        run(t, listOf("Go.", "Go."))
        assertTrue(t.isSettled)
        t.onOcr("Something else entirely")
        assertFalse(t.isSettled)
    }

    @Test fun reappearingSameTextAfterHideIsShownAgain() {
        val t = SubtitleTracker()
        val a = run(t, listOf("Okay.", "Okay.", "", "", "Okay.", "Okay."))
        assertEquals(2, a.count { it is Action.Show })
    }

    @Test fun completeSubtitleShowsAfterFirstReadWhenFast() {
        val t = SubtitleTracker(fastComplete = true)
        // celá věta → hned
        assertEquals(SubtitleTracker.Action.Show("We need money, Jonas."), t.onOcr("We need money, Jonas."))
        t.confirmShown("We need money, Jonas.")
        assertEquals(SubtitleTracker.Action.None, t.onOcr("We need money, Jonas."))
        // i text bez tečky na konci se ukáže hned
        assertEquals(SubtitleTracker.Action.Show("Get on your horse and"), t.onOcr("Get on your horse and"))
        t.confirmShown("Get on your horse and")
        // titulek se dopsal → překlad se doplní
        assertEquals(SubtitleTracker.Action.Show("Get on your horse and ride with me."), t.onOcr("Get on your horse and ride with me."))
    }
}
