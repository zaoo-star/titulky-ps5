package cz.recaption.core

import org.junit.Assert.*
import org.junit.Test

class GlossaryTest {
    @Test fun namesKeptTermsTranslated() {
        val g = Glossary(names = listOf("Kratos", "Atreus"), terms = mapOf("bounty" to "odměna"))
        val p = g.prepare("Kratos, the Bounty is for Atreus.")
        assertEquals("[1], the [2] is for [3].", p.protectedText)
        assertEquals("Kratos, ta odměna je pro Atreus.", OfflinePrep.finish(p, "[1], ta [2] je pro [3]."))
    }

    @Test fun streetNamesStayInOriginal() {
        val g = Glossary()
        fun kept(t: String) = g.prepare(t).names
        assertEquals(listOf("Shore Drive"), kept("Meet me on Shore Drive."))
        assertEquals(listOf("Huevo St"), kept("The lab is on Huevo St, near the bridge."))
        assertEquals(listOf("Ocean Avenue"), kept("Take Ocean Avenue to the beach."))
        assertEquals(listOf("5th Street"), kept("Go down 5th Street."))
        assertEquals(emptyList<String>(), kept("Take the road on the left."))
        assertEquals(emptyList<String>(), kept("Go Drive."))
        // překlad: název ulice se vrátí do věty beze změny
        val out = g.translate("Meet me on Shore Drive.") { it.replace("Meet me on", "Sejdeme se na") }
        assertEquals("Sejdeme se na Shore Drive.", out)
    }

    @Test fun termsMatchWholeWordsOnly() {
        val g = Glossary(terms = mapOf("posse" to "tlupa"))
        assertEquals("He is possessed.", g.prepare("He is possessed.").protectedText)
        assertEquals("That [1] is fast.", g.prepare("That Posse is fast.").protectedText)
    }

    @Test fun sentenceOverrideIgnoresCaseAndPunctuation() {
        val g = Glossary(sentences = mapOf("Get on your horse." to "Nasedni na koně."))
        assertEquals("Nasedni na koně.", g.sentence("get on your horse"))
        assertEquals("Nasedni na koně.", g.translate("Get on your horse!") { error("nemá se volat překladač") })
        assertNull(g.sentence("Get off your horse."))
    }

    @Test fun pipelineFallsBackWhenTokensBroken() {
        val g = Glossary(names = listOf("Geralt"))
        val out = g.translate("Geralt is here.") { s -> if (s.contains("[")) "Někdo je tady." else "Geralt je tady." }
        assertEquals("Geralt je tady.", out)
    }

    @Test fun emptyGlossaryStillStandardizesSlang() {
        val g = Glossary()
        var sent = ""
        g.translate("We ain't goin' nowhere.") { sent = it; "x" }
        assertEquals("We are not going nowhere.", sent)
        assertEquals(0, g.size)
    }

    @Test fun userDictionaryRoundTrip() {
        val m = linkedMapOf("bounty" to "odměna", "Get lost!" to "Zmiz!")
        val back = Glossary.parseUser(Glossary.serializeUser(m))
        assertEquals(m, back)
        assertTrue(Glossary.parseUser(null).isEmpty())
        assertTrue(Glossary.parseUser("bez tabulatoru\n\tprazdne\nx\t").isEmpty())
        assertTrue(Glossary.isSentence("Get lost!"))
        assertTrue(Glossary.isSentence("we ride at dawn boys"))
        assertFalse(Glossary.isSentence("bounty"))
        assertFalse(Glossary.isSentence("bounty hunter"))
    }
}

class GameProfilesTest {
    @Test fun defaultIsUniversalAndCyclesThroughAll() {
        assertEquals("all", GameProfiles.of(null).id)
        assertEquals("all", GameProfiles.of("neexistuje").id)
        var p = GameProfiles.of(null)
        val seen = HashSet<String>()
        repeat(GameProfiles.ALL.size) { seen += p.id; p = GameProfiles.next(p.id) }
        assertEquals(GameProfiles.ALL.size, seen.size)
        assertEquals("all", p.id)
    }

}

class SupportTest {
    @Test fun diagLogKeepsLastLines() {
        DiagLog.clear()
        repeat(500) { DiagLog.add("radek $it", 1000L) }
        assertEquals(300, DiagLog.size())
        assertTrue(DiagLog.dump().endsWith("radek 499"))
        assertFalse(DiagLog.dump().contains("radek 100\n"))
        DiagLog.clear()
    }

    @Test fun updateCheck() {
        assertEquals(UpdateCheck.Remote(12, "0.12"), UpdateCheck.parse("12 0.12\n"))
        assertEquals(UpdateCheck.Remote(7, "7"), UpdateCheck.parse("7"))
        assertNull(UpdateCheck.parse("<html>Not Found</html>"))
        assertNull(UpdateCheck.parse(null)); assertNull(UpdateCheck.parse("-3 x")); assertNull(UpdateCheck.parse(""))
        assertTrue(UpdateCheck.isNewer(UpdateCheck.parse("12 0.12"), 10))
        assertFalse(UpdateCheck.isNewer(UpdateCheck.parse("10 0.10"), 10))
        assertFalse(UpdateCheck.isNewer(null, 10))
    }

    @Test fun updateChannels() {
        val stable = UpdateCheck.Remote(56, "0.56")
        val test = UpdateCheck.Remote(57, "0.57")
        // bez zkušebního kanálu se nabízí jen běžná verze, a jen když je novější
        assertEquals(UpdateCheck.Offer(stable, UpdateCheck.Channel.STABLE), UpdateCheck.pick(stable, null, 55))
        assertNull(UpdateCheck.pick(stable, null, 56))
        assertNull(UpdateCheck.pick(null, null, 55))
        // zkušební verze má přednost, jen když je novější než běžná
        assertEquals(UpdateCheck.Offer(test, UpdateCheck.Channel.TEST), UpdateCheck.pick(stable, test, 55))
        assertEquals(UpdateCheck.Offer(test, UpdateCheck.Channel.TEST), UpdateCheck.pick(stable, test, 56))
        assertEquals(UpdateCheck.Offer(test, UpdateCheck.Channel.TEST), UpdateCheck.pick(null, test, 56))
        // běžný kanál zkušební verzi dohnal nebo předběhl → bere se běžný
        assertEquals(UpdateCheck.Offer(stable, UpdateCheck.Channel.STABLE), UpdateCheck.pick(stable, UpdateCheck.Remote(56, "0.56"), 55))
        assertEquals(UpdateCheck.Offer(test, UpdateCheck.Channel.STABLE), UpdateCheck.pick(test, stable, 55))
        assertNull(UpdateCheck.pick(stable, test, 57))
    }

    @Test fun updateAddressesAndProblems() {
        assertEquals("https://recaption.pages.dev/d/latest/ReCaption.apk",
            UpdateCheck.url(UpdateCheck.Channel.STABLE, "ReCaption.apk"))
        assertEquals("https://recaption.pages.dev/d/zkusebni/version.txt",
            UpdateCheck.url(UpdateCheck.Channel.TEST, "version.txt"))
        assertEquals("upd_cancel", UpdateCheck.problemKey(3))
        assertEquals("upd_sig", UpdateCheck.problemKey(5))
        assertEquals("upd_space", UpdateCheck.problemKey(6))
        assertEquals("upd_fail", UpdateCheck.problemKey(1))
        // každý důvod má text ve všech jazycích (jinak by uživatel viděl jen klíč)
        for (k in listOf("upd_cancel", "upd_sig", "upd_space", "upd_fail", "upd_net", "upd_down", "upd_inst", "upd_btn", "upd_test", "upd_done"))
            for (lang in Lang.uiLangs) assertTrue("$lang/$k", I18n.t(lang, k) != k)
    }
}
