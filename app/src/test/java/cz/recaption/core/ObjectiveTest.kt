package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Řádek s úkolem pod titulky – vyčlenění z titulků a sledování (od 0.97.43). Vymyšlené texty; případy ze skutečných
 * záznamů a dávek her jsou v soukromém repu (`ObjectiveGamesTest`).
 */
class ObjectiveTest {
    private fun line(t: String, top: Int, h: Int = 30) = OcrLine(t, 400, top, 1500, top + h)
    private fun block(vararg l: OcrLine) = OcrBlock(l.toList())

    @Test fun ukolPodTitulkemSVolnymRadkem() {
        // titulek 600–630, volný řádek, úkol 670–700
        val p = TextLogic.assemble(
            listOf(block(line("TOMAS: We need to get out of here.", 600)), block(line("Go to the safehouse.", 670))),
            minLineHeight = 10, subtitlesOnly = true, screenCenterX = 950, centerTolerance = 300, objectives = true,
        )
        assertEquals("TOMAS: We need to get out of here.", p.text)
        assertEquals("Go to the safehouse.", p.objective?.text)
    }

    @Test fun ukolJenUplneDole() {
        // stejné dva řádky, úkol by byl na 670 px – s pásem pro úkol od 680 px (výš = titulek) úkol není
        fun obj(minTop: Int) = TextLogic.assemble(
            listOf(block(line("TOMAS: Let's go.", 600)), block(line("Follow Lena", 670))),
            minLineHeight = 10, subtitlesOnly = true, screenCenterX = 950, centerTolerance = 300, objectives = true,
            objectiveMinTop = minTop,
        ).objective
        assertNull(obj(680))
        assertEquals("Follow Lena", obj(650)?.text)
    }

    @Test fun dvouradkovyTitulekBezMezeryNeniUkol() {
        val p = TextLogic.assemble(
            listOf(block(line("We need to get out", 600), line("of here right now.", 636))),
            minLineHeight = 10, subtitlesOnly = true, screenCenterX = 950, centerTolerance = 300, objectives = true,
        )
        assertNull(p.objective)
        assertEquals("We need to get out of here right now.", p.text)
    }

    @Test fun bezVolbyVProfiluSeNevycleni() {
        val p = TextLogic.assemble(
            listOf(block(line("We need to get out of here.", 600)), block(line("Go to the safehouse.", 670))),
            minLineHeight = 10, subtitlesOnly = true, screenCenterX = 950, centerTolerance = 300,
        )
        assertNull(p.objective)
    }

    @Test fun samotnyUkolNaNaucenemMiste() {
        val alone = listOf(block(line("Go to the safehouse.", 672)))
        // místo ještě neznámé → běžný řádek (titulek)
        assertNull(TextLogic.assemble(alone, 10, true, 950, 300, objectives = true).objective)
        // na naučeném místě → úkol, titulek prázdný
        val p = TextLogic.assemble(alone, 10, true, 950, 300, objectives = true, objectiveTop = 670)
        assertEquals("Go to the safehouse.", p.objective?.text)
        assertEquals("", p.text)
        // jinde → titulek
        assertNull(TextLogic.assemble(listOf(block(line("Where are you going?", 600))), 10, true, 950, 300,
            objectives = true, objectiveTop = 670).objective)
    }

    @Test fun replikaSMluvcimNeniUkol() {
        val p = TextLogic.assemble(
            listOf(block(line("We need to get out of here.", 600)), block(line("LENA: Hurry up!", 670))),
            minLineHeight = 10, subtitlesOnly = true, screenCenterX = 950, centerTolerance = 300, objectives = true,
        )
        assertNull(p.objective)
    }

    @Test fun sledovaniUkolu() {
        val t = ObjectiveTracker(misses = 2)
        assertTrue(t.update("Go to the safehouse.", 0.9f) is ObjectiveTracker.Change.Show)
        // drobná chyba čtení = stejný úkol
        assertEquals(ObjectiveTracker.Change.Keep, t.update("Go to the safehcuse.", 0.9f))
        // jedno vynechané čtení úkol neschová, druhé ano
        assertEquals(ObjectiveTracker.Change.Keep, t.update(null))
        assertEquals(ObjectiveTracker.Change.Hide, t.update(null))
        assertEquals(ObjectiveTracker.Change.Keep, t.update(null))
        // nový úkol
        assertTrue(t.update("Follow Lena.", 0.9f) is ObjectiveTracker.Change.Show)
        assertTrue(t.update("Take the car.", 0.9f) is ObjectiveTracker.Change.Show)
        assertTrue(t.top in 0.89f..0.91f)
    }

    @Test fun zpravaProTelevizi() {
        val m = TvSender.objectiveMessage("Jdi do úkrytu.", floatArrayOf(0.2f, 0.9f, 0.8f, 0.93f), 40, 90, "#FFFFFF")
        assertEquals("{\"typ\":\"ukol\",\"text\":\"Jdi do úkrytu.\",\"misto\":[0.2000,0.9000,0.8000,0.9300],\"pismo\":40,\"pozadi\":90,\"barva\":\"#FFFFFF\"}", m)
        assertEquals("{\"typ\":\"ukol\",\"text\":\"\"}", TvSender.objectiveMessage(null))
    }
}
