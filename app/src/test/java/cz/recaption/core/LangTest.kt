package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Jazyk aplikace a jazyk překladu jsou dvě různá nastavení; angličtina je jen jazyk aplikace. */
class LangTest {
    @Test fun englishIsNotATranslationTarget() {
        assertFalse(Lang.EN.isTarget)
        assertFalse(Lang.EN in Lang.targets)
        assertEquals(Lang.values().size - 1, Lang.targets.size)
        // uložený kód „en“ ani neznámý kód nesmí z jazyka překladu udělat angličtinu
        assertEquals(Lang.CS, Lang.of("en"))
        assertEquals(Lang.CS, Lang.of(null))
        assertEquals(Lang.CS, Lang.of("xx"))
        assertEquals(Lang.DE, Lang.of("de"))
    }

    @Test fun appLanguageIncludesEnglish() {
        assertEquals(Lang.EN, Lang.ui("en"))
        assertEquals(Lang.UK, Lang.ui("uk"))
        assertNull(Lang.ui("ja"))      // pro japonštinu aplikace texty nemá → volající zvolí angličtinu
        assertNull(Lang.ui(null))
    }

    @Test fun switchingGoesThroughAllAppLanguages() {
        var l = Lang.CS
        val seen = HashSet<Lang>()
        repeat(Lang.uiLangs.size) { seen.add(l); l = l.next() }
        assertEquals(Lang.uiLangs.toSet(), seen)
        assertEquals(Lang.CS, l)
    }

    @Test fun englishTextsAreReallyEnglish() {
        assertEquals("Settings", I18n.t(Lang.EN, "m_settings"))
        assertEquals("Translation language", I18n.t(Lang.EN, "l_lang"))
        assertEquals("App language", I18n.t(Lang.EN, "l_ui_lang"))
        // žádný anglický text nesmí zůstat česky (poznalo by se podle háčků a čárek)
        val czech = Regex("[ěščřžýáíéúůďťňĚŠČŘŽÝÁÍÉÚŮĎŤŇ]")
        for (k in I18n.keys) assertFalse("$k: " + I18n.t(Lang.EN, k), czech.containsMatchIn(I18n.t(Lang.EN, k)))
        // obě nastavení mají v každém jazyce jiný název, ať se nepletou
        for (lang in Lang.uiLangs) assertTrue("$lang", I18n.t(lang, "l_lang") != I18n.t(lang, "l_ui_lang"))
    }

    @Test fun targetOnlyLanguages() {
        val only = listOf(Lang.RO, Lang.HR, Lang.SR, Lang.SL, Lang.BG, Lang.MK, Lang.SQ, Lang.LT, Lang.LV, Lang.ET, Lang.EL, Lang.TR, Lang.NL, Lang.SV, Lang.DA, Lang.NB, Lang.FI, Lang.ID, Lang.VI, Lang.HE, Lang.TH, Lang.CA, Lang.TL, Lang.MS, Lang.IS, Lang.SW, Lang.AF)
        assertEquals(only, Lang.values().filter { !it.ui })
        // dosavadní jazyky překladu zůstávají první a ve stejném pořadí
        assertEquals(listOf("cs", "sk", "pl", "de", "hu", "uk", "es", "fr", "it"), Lang.targets.take(9).map { it.code })
        assertEquals(Lang.values().size - 1, Lang.targets.size)
        assertEquals(11, Lang.uiLangs.size)
        for (l in only) {
            assertTrue(l.isTarget)
            assertEquals(l, Lang.of(l.code))
            // jazyk aplikace z nich nikdy není (ani z jazyka zařízení) a texty jsou anglicky
            assertNull(Lang.ui(l.code))
            assertFalse(l in Lang.uiLangs)
            assertEquals(I18n.t(Lang.EN, "m_settings"), I18n.t(l, "m_settings"))
        }
        // přepínání jazyka aplikace z nich nejde
        assertEquals(Lang.CS, Lang.EN.next())
    }

    @Test fun everyTargetHasMlKitLanguage() {
        // kódy offline překladače Google ML Kit (TranslateLanguage); srbštinu ML Kit nemá → chorvatský model
        val mlkit = setOf("cs", "sk", "pl", "de", "hu", "uk", "es", "fr", "it", "hi", "ro", "hr", "sl", "bg", "mk", "sq", "lt", "lv", "et", "el", "tr", "nl", "sv", "da", "no", "fi", "id", "vi", "he", "th", "ca", "tl", "ms", "is", "sw", "af")
        for (l in Lang.targets) assertTrue("$l", l.mlkit in mlkit)
        assertEquals("hr", Lang.SR.mlkit)
        // norština: ML Kit „no“, DeepL a aplikace „nb“
        assertEquals("no", Lang.NB.mlkit)
        for (l in Lang.targets - Lang.SR - Lang.NB) assertEquals(l.code, l.mlkit)
    }

    @Test fun systemLanguageAsDefaultTarget() {
        assertEquals(Lang.DE, Lang.system("de"))
        assertEquals(Lang.PL, Lang.system("pl"))
        assertEquals(Lang.HI, Lang.system("hi"))
        assertEquals(Lang.NB, Lang.system("no"))
        assertEquals(Lang.HE, Lang.system("iw"))
        assertEquals(Lang.ID, Lang.system("in"))
        org.junit.Assert.assertNull(Lang.system("en"))
        org.junit.Assert.assertNull(Lang.system("ja"))
        org.junit.Assert.assertNull(Lang.system(null))
    }
}
