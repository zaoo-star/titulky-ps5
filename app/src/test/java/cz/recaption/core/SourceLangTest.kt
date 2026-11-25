package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Volba „Překládat z“: co se nabízí a že úpravy pro angličtinu běží jen u angličtiny. */
class SourceLangTest {

    @Test fun defaultIsEnglish() {
        assertEquals(SourceLang.EN, SourceLang.of(null))
        assertEquals(SourceLang.EN, SourceLang.of("xx"))
        assertEquals(SourceLang.FR, SourceLang.of("fr"))
        // dosavadní hráči klíč nemají → angličtina pro každý jazyk překladu
        for (t in Lang.targets) assertEquals(SourceLang.EN, SourceLang.effective(null, t))
    }

    @Test fun offeredWithoutTarget() {
        for (t in Lang.targets) {
            val o = SourceLang.offered(t)
            assertTrue(SourceLang.EN in o)
            assertFalse(o.any { it.code == t.code })
        }
        assertTrue(SourceLang.DE in SourceLang.offered(Lang.CS))
        assertFalse(SourceLang.CS in SourceLang.offered(Lang.CS))
    }

    @Test fun onlyLatinScriptLanguages() {
        // čtení obrazu umí jen latinku: žádná ukrajinština, ruština, řečtina, japonština…
        val codes = SourceLang.values().map { it.code }
        for (c in listOf("uk", "ru", "el", "ja", "zh", "ko", "ar", "bg")) assertFalse(c, c in codes)
        // kódy jsou unikátní a DeepL kód je velkými písmeny
        assertEquals(codes.size, codes.toSet().size)
        for (s in SourceLang.values()) assertEquals(s.code.uppercase(), s.deepl)
    }

    @Test fun sameAsTargetFallsBackToEnglish() {
        assertEquals(SourceLang.EN, SourceLang.effective("de", Lang.DE))
        assertEquals(SourceLang.DE, SourceLang.effective("de", Lang.CS))
    }

    @Test fun foreignTextDependsOnSource() {
        val fr = "Où est la clé ? Je l’ai laissée là."
        // u angličtiny je francouzština „jiný jazyk“ (hra už je přeložená) – jako dřív
        assertTrue(TextLogic.looksForeign(fr))
        assertTrue(TextLogic.looksForeign(fr, SourceLang.EN))
        // u francouzských titulků je diakritika v pořádku
        assertFalse(TextLogic.looksForeign(fr, SourceLang.FR))
        assertFalse(TextLogic.looksForeign("Güle güle, şimdi gidiyoruz.", SourceLang.TR))
        assertFalse(TextLogic.looksForeign("Hello there.", SourceLang.DE))
        // jiné písmo (azbuka) je cizí vždy
        assertTrue(TextLogic.looksForeign("Привет, как дела?", SourceLang.FR))
    }

    @Test fun upperCaseWithoutEnglishI() {
        assertEquals("We have to go, I think.", TextLogic.normalizeCase("WE HAVE TO GO, I THINK."))
        // italské „i“ (členy) zůstane malé
        assertEquals("Dove sono i ragazzi?", TextLogic.normalizeCase("DOVE SONO I RAGAZZI?", english = false))
    }

    @Test fun noEnglishSlangForOtherLanguages() {
        val t = "Nothin' to say, hang on."
        assertEquals(OfflinePrep.standardize(t), OfflinePrep.prepare(t).plain)
        assertEquals(t, OfflinePrep.prepare(t, english = false).plain)
        val g = Glossary(names = listOf("Lena"))
        assertEquals(t, g.prepare(t, english = false).plain)
        // jména se schovají i u jiného jazyka
        assertEquals(listOf("Lena"), g.prepare("Lena, attends-moi !", english = false).names)
    }

    @Test fun genderIgnoresNonEnglishOriginal() {
        val g = Glossary(cast = Cast(listOf(GameCharacter("Lena", Gender.F, listOf("Le")))))
        // mluvčí z titulku platí v každém jazyce (nečte se originál)
        assertEquals(g.genders(Lang.CS, "x", "Byl jsem tam.", "Lena"), g.genders(Lang.CS, "x", "Byl jsem tam.", "Lena", english = false))
        // anglický originál se u jiného jazyka nečte: bez mluvčího a jména v překladu zůstane překlad beze změny
        assertEquals("Byl jsi tam?", g.genders(Lang.CS, "Lena, you were there?", "Byl jsi tam?", null, english = false))
    }

    @Test fun deeplSourceLanguage() {
        assertEquals("EN", MiniJson.obj(Deepl.requestJson(listOf("Hi"), "CS"))!!["source_lang"])
        assertEquals("FR", MiniJson.obj(Deepl.requestJson(listOf("Salut"), "CS", SourceLang.FR.deepl))!!["source_lang"])
    }
}
