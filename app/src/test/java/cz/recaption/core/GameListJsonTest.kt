package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameListJsonTest {
    private fun read(text: String, app: Int = GameProfiles.MIN_APP) = GameListJson.parse(text, app)

    /** Vymyšlená hra se vším, co seznam umí (skutečná data her jsou jen v soukromém repu – test GameDataTest tam). */
    private val sample = """{"format":1,"verze":4,"minAppVerze":1,"bezneSlova":["Bill"],"hry":[
        {"id":"ukazka","nazev":"Ukázková hra","jmena":["Zorg","Kessler Bay","Grayford"],"bezne":["Grayford"],
         "postavy":[{"jmeno":"Zorg","rod":"f","prezdivky":["Zo"]},{"jmeno":"Kessler","rod":"m"}],
         "pojmy":{"cs":{"heist":"loupež"}},"napisy":["WASTED"],"menu":["Map","Help"],
         "preskocit":[{"vzor":"^\\[chat\\]","velikost":false}],"pokyny":true,"barvy":{"Zorg":"#FF00FF00"}}]}"""

    @Test fun roundTripGivesSameText() {
        val c = read(sample).catalog!!
        assertTrue(c.downloaded)
        assertEquals(4, c.version)
        val again = read(GameListJson.write(c)).catalog!!
        assertEquals(GameListJson.write(c), GameListJson.write(again))
        val g = again.of("ukazka")
        assertEquals(Gender.F, g.cast.genderOf("ZO"))
        assertTrue(g.screen.isHud("WASTED"))
        assertTrue(g.prompts)
        assertFalse("Grayford" in again.universal.names)       // běžné slovo – univerzální profil ho nebere
        assertTrue("Kessler Bay" in again.universal.names)
    }

    @Test fun menuLayoutRoundTrip() {
        // od 1.00-rc21: velikost písma a místo patičky menu z profilu hry; neplatné hodnoty = neznámé, bez pole = prázdné
        val text = """{"format":1,"verze":5,"minAppVerze":1,"hry":[
            {"id":"a","nazev":"A","menuHry":{"polozky":["Map","Help"],"zalozky":[],"rozlozeni":{"radekPodmenu":0.0296,"patickaVpravo":0.983,"radek":7,"radekFoto":"x"}}},
            {"id":"b","nazev":"B","menuHry":{"polozky":["Map"],"zalozky":[]}}]}"""
        val c = read(text).catalog!!
        val l = c.of("a").menuSpec!!.layout
        assertEquals(MenuLayout(subLine = 0.0296f, footerRight = 0.983f), l)
        assertTrue(c.of("b").menuSpec!!.layout.isEmpty)
        val again = read(GameListJson.write(c)).catalog!!
        assertEquals(l, again.of("a").menuSpec!!.layout)
        assertFalse(GameListJson.write(c).substringAfter("\"id\":\"b\"").contains("rozlozeni"))
    }

    @Test fun builtinIsEmpty() {
        assertEquals(0, GameProfiles.BUILTIN.version)
        assertTrue(GameProfiles.BUILTIN.games.isEmpty())
        assertEquals(listOf("all"), GameProfiles.BUILTIN.all.map { it.id })
    }

    @Test fun badInputNeverCrashes() {
        for (bad in listOf("", "x", "[]", "{}", "{\"format\":2,\"verze\":5,\"hry\":[]}", "{\"format\":1,\"verze\":0,\"hry\":[]}",
            "{\"format\":1,\"verze\":3,\"hry\":[]}", "{\"format\":1,\"verze\":3,\"hry\":[{\"id\":\"all\",\"nazev\":\"x\"}]}",
            "{\"format\":1,\"verze\":3,\"hry\":\"x\"}", "{".repeat(100))) {
            assertNull(bad, read(bad).catalog)
        }
        assertNull(read("{\"format\":1,\"verze\":3,\"hry\":[]}" + " ".repeat(GameListJson.MAX_SIZE)).catalog)
    }

    @Test fun invalidEntriesAreSkipped() {
        val text = """{"format":1,"verze":7,"minAppVerze":1,"hry":[
            {"id":"Bad Id","nazev":"x"},
            {"id":"nova","nazev":"Nová hra","jmena":["Zorg",5,"","${"x".repeat(200)}"],
             "postavy":[{"jmeno":"Zorg","rod":"f","prezdivky":["Zo"]},{"jmeno":"Kdo","rod":"x"}],
             "pojmy":{"cs":{"heist":"loupež"},"xx":{"a":"b"}},
             "preskocit":[{"vzor":"(unclosed"},{"vzor":"^\\[chat\\]"}],
             "barvy":{"Zorg":"#FF00FF00","Kdo":"red"},
             "rada":"neexistuje"},
            {"id":"nova","nazev":"Duplicitní"}]}"""
        val c = read(text).catalog!!
        assertEquals(listOf("nova"), c.games.map { it.id })
        val g = c.of("nova")
        assertEquals("Nová hra", g.title)
        assertEquals(listOf("Zorg"), g.names)
        assertEquals(listOf(GameCharacter("Zorg", Gender.F, listOf("Zo"))), g.characters)
        assertEquals(mapOf("cs" to mapOf("heist" to "loupež")), g.terms)
        assertEquals(1, g.skip.size)
        assertTrue(g.screen.isHud("[CHAT] ahoj"))
        assertEquals(mapOf("Zorg" to 0xFF00FF00.toInt()), g.colors)
        assertNull(g.hint)
        assertEquals(7, c.version)
    }

    @Test fun listForNewerAppIsSkipped() {
        val text = "{\"format\":1,\"verze\":9,\"minAppVerze\":999,\"hry\":[{\"id\":\"a\",\"nazev\":\"A\"}]}"
        assertNull(read(text, 500).catalog)
        assertNotNull(read(text, 999).catalog)
        assertEquals(9, read(text, 500).version)
    }

    @Test fun switchingCatalogKeepsUnknownIdOnUniversal() {
        val c = read("{\"format\":1,\"verze\":9,\"hry\":[{\"id\":\"jina\",\"nazev\":\"Jiná\",\"jmena\":[\"Qwerty\"]}]}").catalog!!
        try {
            GameProfiles.use(c)
            assertEquals("all", GameProfiles.of("jina-hra").id)       // hra v seznamu chybí → univerzální, volba se nemění
            assertEquals("jina", GameProfiles.of("jina").id)
            assertTrue("Qwerty" in GameProfiles.UNIVERSAL.names)
        } finally { GameProfiles.use(null) }
        assertEquals("jina", c.of("jina").id)
        assertEquals("all", GameProfiles.of("jina").id)      // zpět na vestavěný (prázdný)
    }

    @Test fun versionFile() {
        assertEquals(12, GameListJson.versionOf(" 12\n"))
        assertNull(GameListJson.versionOf("<html>"))
        assertNull(GameListJson.versionOf("0"))
    }

}
