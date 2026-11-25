package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Přechod ze staré volby „Rozsah čtení“ na samostatné přepínače textů hry (0.97.58). */
class GameScopeTest {
    @Test
    fun neverChangedKeepsDefaults() {
        // nikdo nevolil: pokyny a úkol zapnuté jako dřív, menu vypnuté jako v 0.97.57
        assertEquals(true, GameScope.resolve(null, false, true))
        assertEquals(false, GameScope.resolve(null, false, false))
    }

    @Test
    fun subtitlesAndGameTextsKeepsSavedSwitches() {
        assertEquals(true, GameScope.resolve(true, false, true))
        assertEquals(false, GameScope.resolve(true, false, false))
        assertEquals(true, GameScope.resolve(true, true, true))
    }

    @Test
    fun subtitlesOnlyTurnsEverythingOffUntilChosen() {
        // „jen titulky“: nic navíc se nepřekládá, ani když je pod tím uložené zapnuto
        assertEquals(false, GameScope.resolve(false, false, true))
        assertEquals(false, GameScope.resolve(false, false, false))
        // hráč přepínač sám zapnul → platí jeho volba, stará volba ho nepřebije
        assertEquals(true, GameScope.resolve(false, true, true))
        assertEquals(false, GameScope.resolve(false, true, false))
    }
}
