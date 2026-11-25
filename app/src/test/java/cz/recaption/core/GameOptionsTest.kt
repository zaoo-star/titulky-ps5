package cz.recaption.core

import cz.recaption.core.GameOptions.Option
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameOptionsTest {
    private class Map(vararg pairs: Pair<String, Any>) : GameOptions.Store {
        val m = mutableMapOf(*pairs)
        override fun contains(key: String) = key in m
        override fun bool(key: String, def: Boolean) = m[key] as? Boolean ?: def
        override fun int(key: String, def: Int) = m[key] as? Int ?: def
    }

    @Test fun nothingStoredGivesDefaults() {
        val s = Map()
        assertTrue(GameOptions.on(s, Option.PROMPTS, "hra1"))
        assertTrue(GameOptions.on(s, Option.OBJECTIVE, "hra1"))
        assertFalse(GameOptions.on(s, Option.MENU, "hra1"))
        assertEquals(0, GameOptions.value(s, Option.CORNER, "hra1"))
        assertFalse(GameOptions.on(s, Option.WORLD, "hra1"))
        assertFalse(GameOptions.on(s, Option.PHONE, "hra1"))
        assertFalse(GameOptions.on(s, Option.TIP, "hra1"))
    }

    @Test fun gta6DefaultsMenuPhoneTipsOn() {
        // od 1.00 (přání majitele): u GTA VI menu, telefon a tipy výchozí zapnuto; předměty a roh vypnuto; jiné hry beze změny
        val s = Map()
        assertTrue(GameOptions.on(s, Option.MENU, "gta6"))
        assertTrue(GameOptions.on(s, Option.PHONE, "gta6"))
        assertTrue(GameOptions.on(s, Option.TIP, "gta6"))
        assertFalse(GameOptions.on(s, Option.WORLD, "gta6"))
        assertEquals(0, GameOptions.value(s, Option.CORNER, "gta6"))
        assertFalse(GameOptions.on(s, Option.MENU, GameOptions.UNIVERSAL))
        // kdo volbu přepnul (u hry nebo společně), má svou hodnotu
        val t = Map("game_menu@gta6" to false, "telefon_preklad" to false, "game_texts" to false)
        assertFalse(GameOptions.on(t, Option.MENU, "gta6"))
        assertFalse(GameOptions.on(t, Option.PHONE, "gta6"))
        assertTrue(GameOptions.on(t, Option.TIP, "gta6"))
    }

    @Test fun sharedKeyStillAppliesToEveryGame() {
        // hráč měl před aktualizací volby nastavené pro všechny hry – po aktualizaci platí dál u každé hry
        val s = Map("game_prompts" to false, "obj_on" to false, "obj_volba" to true, "game_menu" to true, "game_menu_volba" to true,
            "corner_menu" to 2, "pokyny_predmety" to true, "telefon_preklad" to true)
        for (g in listOf("hra1", "hra2")) {
            assertFalse(GameOptions.on(s, Option.PROMPTS, g))
            assertFalse(GameOptions.on(s, Option.OBJECTIVE, g))
            assertTrue(GameOptions.on(s, Option.MENU, g))
            assertEquals(2, GameOptions.value(s, Option.CORNER, g))
            assertTrue(GameOptions.on(s, Option.WORLD, g))
            assertTrue(GameOptions.on(s, Option.PHONE, g))
        }
    }

    @Test fun gameKeyWinsOverShared() {
        val s = Map("game_menu" to true, "game_menu_volba" to true, "game_menu@hra1" to false, "corner_menu" to 1, "corner_menu@hra1" to 2)
        assertFalse(GameOptions.on(s, Option.MENU, "hra1"))
        assertEquals(2, GameOptions.value(s, Option.CORNER, "hra1"))
        // jiná hra bez vlastní hodnoty jede podle společného klíče
        assertTrue(GameOptions.on(s, Option.MENU, "hra2"))
        assertEquals(1, GameOptions.value(s, Option.CORNER, "hra2"))
    }

    @Test fun gamesAreIndependent() {
        val s = Map("pokyny_predmety@hra1" to true, "telefon_preklad@hra2" to true)
        assertTrue(GameOptions.on(s, Option.WORLD, "hra1"))
        assertFalse(GameOptions.on(s, Option.WORLD, "hra2"))
        assertTrue(GameOptions.on(s, Option.PHONE, "hra2"))
        assertFalse(GameOptions.on(s, Option.PHONE, "hra1"))
        val t = Map("tipy_hry@hra1" to true)
        assertTrue(GameOptions.on(t, Option.TIP, "hra1"))
        assertFalse(GameOptions.on(t, Option.TIP, "hra2"))
    }

    @Test fun legacyOnlySubtitlesTransition() {
        // do 0.97.57 „Rozsah čtení: jen titulky“ – pokyny, úkol a menu vypnuté, dokud je hráč sám nepřepne
        val s = Map("game_texts" to false)
        assertFalse(GameOptions.on(s, Option.PROMPTS, "hra1"))
        assertFalse(GameOptions.on(s, Option.OBJECTIVE, "hra1"))
        assertFalse(GameOptions.on(s, Option.MENU, "hra1"))
        // společný přepínač přepnutý po 0.97.59 platí
        s.m["game_prompts"] = true
        assertTrue(GameOptions.on(s, Option.PROMPTS, "hra1"))
        // volba jedné hry přebije přechod jen u té hry
        s.m["obj_on@hra1"] = true
        assertTrue(GameOptions.on(s, Option.OBJECTIVE, "hra1"))
        assertFalse(GameOptions.on(s, Option.OBJECTIVE, "hra2"))
        // „titulky a texty hry“ = výchozí hodnoty
        val t = Map("game_texts" to true)
        assertTrue(GameOptions.on(t, Option.PROMPTS, "hra1"))
        assertTrue(GameOptions.on(t, Option.OBJECTIVE, "hra1"))
        assertFalse(GameOptions.on(t, Option.MENU, "hra1"))
    }

    @Test fun universalProfileHasNoOptions() {
        val s = Map("game_prompts" to true, "game_menu" to true, "game_menu_volba" to true, "corner_menu" to 2, "pokyny_predmety" to true)
        for (o in Option.values()) {
            assertEquals(o.name, 0, GameOptions.value(s, o, GameOptions.UNIVERSAL))
            assertEquals(o.name, 0, GameOptions.value(s, o, null))
        }
        assertFalse(GameOptions.hasOptions("all"))
        assertTrue(GameOptions.hasOptions("hra1"))
    }

    @Test fun gameKeys() {
        assertEquals("game_menu@hra1", GameOptions.key(Option.MENU, "hra1"))
        assertTrue(GameOptions.isGameKey("telefon_preklad@hra1"))
        assertTrue(GameOptions.isGameKey("tipy_hry@hra1"))
        assertTrue(GameOptions.isGameKey("corner_menu@hra2"))
        assertFalse(GameOptions.isGameKey("game_menu"))
        assertFalse(GameOptions.isGameKey("game_menu@"))
        assertFalse(GameOptions.isGameKey("lang@hra1"))
        // volby jednotlivých her se přenášejí z telefonu do TV
        assertTrue(RemoteSetup.transfers("pokyny_predmety@hra1"))
        assertTrue(RemoteSetup.transfers("obj_on@hra1"))
        assertFalse(RemoteSetup.transfers("tv_type@hra1"))
    }

    @Test fun objectiveOnIsNoLongerPerTv() {
        assertFalse("obj_on" in TvProfiles.KEYS)
        for (k in listOf("obj_size", "obj_pos", "obj_bg")) assertTrue(k, k in TvProfiles.KEYS)
    }
}
