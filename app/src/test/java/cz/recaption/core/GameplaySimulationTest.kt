package cz.recaption.core

import cz.recaption.core.SubtitleTracker.Action
import org.junit.Assert.*
import org.junit.Test

/**
 * Simulace hraní: skládá snímky tak, jak by je vracelo OCR ze spodního pásu obrazovky
 * ve westernové hře – s HUD (peníze, čas, munice), nabíháním titulků, chybami OCR a výpadky.
 * Ověřuje, že se každá replika přeloží právě jednou, ve správném pořadí,
 * a že se pruh schová, když postavy přestanou mluvit.
 */
class GameplaySimulationTest {

    private fun hud(i: Int) = listOf(
        OcrLine("$${12 + i}.50", 1100, 650, 1200, 675),
        OcrLine("${10 + i % 2}:4${i % 10} PM", 1100, 680, 1200, 700),
        OcrLine("x${30 - i}", 50, 680, 90, 700),
    )

    private fun subtitle(vararg lines: String, h: Int = 26): List<OcrLine> =
        lines.mapIndexed { i, t -> OcrLine(t, 300, 600 + i * 32, 980, 600 + i * 32 + h) }

    private fun frame(i: Int, sub: List<OcrLine>): List<OcrBlock> =
        listOf(OcrBlock(sub), OcrBlock(hud(i)))

    @Test fun fullConversation() {
        val frames = ArrayList<List<OcrBlock>>()
        var i = 0
        // 1) jen HUD
        repeat(3) { frames += frame(i++, emptyList()) }
        // 2) titulek nabíhá – první snímek rozmazaný/neúplný
        frames += frame(i++, subtitle("Dut", h = 26))
        repeat(4) { frames += frame(i++, subtitle("Marek: We need money, Jonas.")) }
        // chyba OCR uprostřed
        frames += frame(i++, subtitle("Marek: We need rnoney, Jonas."))
        repeat(2) { frames += frame(i++, subtitle("Marek: We need money, Jonas.")) }
        // 3) dvouřádková odpověď
        repeat(5) { frames += frame(i++, subtitle("Jonas: I know, Marek. I'll talk to", "Hollis about the debts.")) }
        // 4) krátké ticho (1 prázdný snímek) a pak stejná replika pokračuje – nemá blikat
        frames += frame(i++, emptyList())
        repeat(2) { frames += frame(i++, subtitle("Jonas: I know, Marek. I'll talk to", "Hollis about the debts.")) }
        // 5) konec rozhovoru
        repeat(4) { frames += frame(i++, emptyList()) }
        // 6) nápověda
        repeat(3) { frames += frame(i++, subtitle("Press X to mount your horse")) }

        val tracker = SubtitleTracker()
        val shown = ArrayList<String>()
        var hides = 0
        for (f in frames) {
            val text = TextLogic.assemble(f, minLineHeight = 12).text
            when (val a = tracker.onOcr(text)) {
                is Action.Show -> { shown += a.text; tracker.confirmShown(a.text) }
                Action.Hide -> hides++
                Action.None -> {}
            }
        }

        assertEquals(
            listOf(
                "Marek: We need money, Jonas.",
                "Jonas: I know, Marek. I'll talk to Hollis about the debts.",
                "Press X to mount your horse",
            ),
            shown
        )
        assertEquals(1, hides)

        val seg = TextLogic.segments(shown[1])
        assertEquals("Jonas", seg[0].speaker)
        assertEquals("I know, Marek. I'll talk to Hollis about the debts.", seg[0].body)
    }

    @Test fun cacheMakesRepeatedLinesFree() {
        val cache = TranslationCache()
        var apiCalls = 0
        fun translate(s: String): String = cache.get(s) ?: "CZ($s)".also { apiCalls++; cache.put(s, it) }
        repeat(5) { translate("Press X to mount your horse") }
        translate("Ride to Greyford.")
        assertEquals(2, apiCalls)
    }
}
