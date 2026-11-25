package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Anonymní počítání uživatelů: číslo zařízení, tvar pingu, kdy poslat. */
class UsageCountTest {

    @Test fun idIsRandomAndValid() {
        val r = Random(7)
        val a = UsageCount.newId { r.nextInt(it) }
        val b = UsageCount.newId { r.nextInt(it) }
        assertEquals(UsageCount.ID_LENGTH, a.length)
        assertTrue(UsageCount.validId(a))
        assertTrue(a != b)
        assertFalse(UsageCount.validId(null))
        assertFalse(UsageCount.validId("kratke"))
        assertFalse(UsageCount.validId("a".repeat(33)))
        assertFalse(UsageCount.validId("abcdefgh-ijklmnop"))
        assertFalse(UsageCount.validId("abcdefghijklmnopá"))
    }

    @Test fun bodyHasOnlyIdVersionTypeAndSubscription() {
        val id = "Abc123Abc123Abc123Abc123"
        val body = UsageCount.body(id, 171, UsageCount.TYPE_TV)
        assertEquals("""{"id":"$id","v":171,"typ":"tv","predplatne":false}""", body)
        val o = MiniJson.obj(UsageCount.body(id, 5, UsageCount.TYPE_PHONE, subscription = true))!!
        assertEquals(setOf("id", "v", "typ", "predplatne"), o.keys)
        assertEquals("telefon", o["typ"])
        assertEquals(true, o["predplatne"])
        assertTrue(runCatching { UsageCount.body("x", 1, "tv") }.isFailure)
        assertTrue(runCatching { UsageCount.body(id, 1, "hodinky") }.isFailure)
    }

    @Test fun bodyWithOptionalFieldsOnlyWhenKnown() {
        val id = "Abc123Abc123Abc123Abc123"
        // nic nepovinného = jako dřív, žádné navíc klíče
        val plain = MiniJson.obj(UsageCount.body(id, 1, UsageCount.TYPE_TV))!!
        assertEquals(setOf("id", "v", "typ", "predplatne"), plain.keys)

        val full = UsageCount.body(
            id, 171, UsageCount.TYPE_TV,
            tvTarget = UsageCount.TARGET_LG, deeplActive = true, lang = "cs",
            hasGame = true, wizardStep = 7, fastRead = false, advanced = true,
        )
        val o = MiniJson.obj(full)!!
        assertEquals(setOf("id", "v", "typ", "predplatne", "tv", "dl", "jaz", "hra", "krok", "fr", "adv"), o.keys)
        assertEquals("lg", o["tv"])
        assertEquals(true, o["dl"])
        assertEquals("cs", o["jaz"])
        assertEquals(true, o["hra"])
        assertEquals(7L, o["krok"])
        assertEquals(false, o["fr"])
        assertEquals(true, o["adv"])

        // jen některé nepovinné pole
        val partial = MiniJson.obj(UsageCount.body(id, 1, UsageCount.TYPE_PHONE, lang = "en"))!!
        assertEquals(setOf("id", "v", "typ", "predplatne", "jaz"), partial.keys)
        assertEquals("en", partial["jaz"])
    }

    @Test fun bodyValidatesOptionalFields() {
        val id = "Abc123Abc123Abc123Abc123"
        assertTrue(runCatching { UsageCount.body(id, 1, UsageCount.TYPE_TV, tvTarget = "lg2") }.isFailure)
        assertTrue(runCatching { UsageCount.body(id, 1, UsageCount.TYPE_TV, tvTarget = "") }.isFailure)
        assertTrue(runCatching { UsageCount.body(id, 1, UsageCount.TYPE_TV, lang = "cze") }.isFailure)
        assertTrue(runCatching { UsageCount.body(id, 1, UsageCount.TYPE_TV, lang = "CS") }.isFailure)
        assertTrue(runCatching { UsageCount.body(id, 1, UsageCount.TYPE_TV, lang = "c") }.isFailure)
        assertTrue(runCatching { UsageCount.body(id, 1, UsageCount.TYPE_TV, wizardStep = -1) }.isFailure)
        assertTrue(runCatching { UsageCount.body(id, 1, UsageCount.TYPE_TV, wizardStep = 10) }.isFailure)
        // hraniční platné hodnoty projdou
        UsageCount.body(id, 1, UsageCount.TYPE_TV, wizardStep = 0)
        UsageCount.body(id, 1, UsageCount.TYPE_TV, wizardStep = UsageCount.WIZARD_STEP_MAX)
        for (t in UsageCount.TV_TARGETS) UsageCount.body(id, 1, UsageCount.TYPE_TV, tvTarget = t)
    }

    @Test fun urlFromServer() {
        assertEquals("https://a.workers.dev/v1/ping", UsageCount.url("https://a.workers.dev/"))
        assertEquals("https://a.workers.dev/v1/ping", UsageCount.url("https://a.workers.dev"))
    }

    @Test fun atMostOncePerTenMinutes() {
        val t = 1_000_000_000L
        assertTrue(UsageCount.due(0, t))
        assertFalse(UsageCount.due(t, t + 1))
        assertFalse(UsageCount.due(t, t + UsageCount.INTERVAL_MS - 1))
        assertTrue(UsageCount.due(t, t + UsageCount.INTERVAL_MS))
        // hodiny šly zpátky
        assertTrue(UsageCount.due(t, t - 5000))
    }

    @Test fun openPingAndSettingNames() {
        val id = "A".repeat(24)
        val b = UsageCount.body(id, 253, "tv", open = true, settings = listOf("opacity", "text_size"))
        assertTrue(b.contains(""""akt":"o""""))
        assertTrue(b.endsWith(""""nast":["opacity","text_size"]}"""))
        // bez otevření žádné "akt"
        assertFalse(UsageCount.body(id, 253, "tv").contains("akt"))
        // názvy voleb: bez interních klíčů, bez přípony hry, bez duplicit, seřazené
        assertEquals(listOf("game_menu", "opacity", "text_size"),
            UsageCount.settingKeys(listOf("text_size", "ps5_host", "psn_id", "game_menu@hra1", "game_menu@hra2", "opacity", "first_run", "Velke")))
        assertEquals(UsageCount.SETTINGS_MAX, UsageCount.settingKeys((1..60).map { "k$it" }).size)
        // otevření nejvýš 1× za 6 h
        assertTrue(UsageCount.openDue(0, 1000))
        assertFalse(UsageCount.openDue(1000, 1000 + UsageCount.OPEN_INTERVAL_MS - 1))
        assertTrue(UsageCount.openDue(1000, 1000 + UsageCount.OPEN_INTERVAL_MS))
    }
}
