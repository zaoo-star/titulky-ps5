package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Barevná slova úkolu přenesená do překladu – pokus, výchozí vypnuto. */
class ObjectiveColorsTest {
    private val yellow = SpeakerColors.PALETTE[0]
    private val blue = SpeakerColors.PALETTE[1]

    @Test fun runsGroupSameColour() {
        val r = ObjectiveColors.runs(listOf("Go", "to", "the", "Port", "Vale", "safehouse."),
            listOf(null, null, null, 0xF0C040, 0xF2C84A, null))
        assertEquals(listOf(ObjectiveColors.Run("Port Vale", yellow)), r)
        // dvě různé barvy za sebou = dva kusy; samotná interpunkce se nepočítá
        val r2 = ObjectiveColors.runs(listOf("Lose", "the", "cops", "."), listOf(0x70B8F4, null, 0xF2C14E, 0xF2C14E))
        assertEquals(listOf(ObjectiveColors.Run("Lose", blue), ObjectiveColors.Run("cops .", yellow, last = true)), r2)
        assertEquals(emptyList<ObjectiveColors.Run>(), ObjectiveColors.runs(listOf("Go"), listOf(null)))
    }

    @Test fun locateVerbatimAndInflected() {
        // jméno místa se nepřekládá → doslova
        assertEquals(7..15, ObjectiveColors.locate("Jeď do Port Vale.", "Port Vale"))
        // překlad kusu zvlášť: „úkryt“ v „úkrytu“ (koncovka do 3 písmen), bez ohledu na velikost a tečku
        assertEquals(7..12, ObjectiveColors.locate("Jdi do úkrytu.", "Úkryt."))
        // jen od začátku slova, ne uprostřed
        assertNull(ObjectiveColors.locate("Najdi auto.", "to"))
        // obsazené místo se přeskočí
        assertNull(ObjectiveColors.locate("Jdi do úkrytu.", "úkryt", listOf(7..12)))
        // dlouhé pokračování slova není koncovka
        assertNull(ObjectiveColors.locate("Jdi k autobusu.", "auto"))
    }

    /**
     * Barevné slovo v jiném pádě: samostatný překlad kusu je „jachta“, v úkolu ale „k jachtě“ / „na jachtu“ –
     * dřív nenalezeno a slovo zůstalo bílé.
     */
    @Test fun locateOtherCase() {
        assertEquals(6..11, ObjectiveColors.locate("Jeď k jachtě.", "jachta"))
        assertEquals(7..12, ObjectiveColors.locate("Jeď na jachtu", "Jachta"))
        assertEquals(14..19, ObjectiveColors.locate("Cesta autem k jachtě", "jachta"))
        // víc slov v jiném pádě („motorový člun“ → „do motorového člunu“)
        assertEquals(10..25, ObjectiveColors.locate("Nastup do motorového člunu", "motorový člun"))
        // kmen jen u slov od 4 písmen a jen od začátku slova; delší pokračování není koncovka
        assertNull(ObjectiveColors.locate("Jdi k autobusu.", "auto"))
        assertNull(ObjectiveColors.locate("Jdi do vily.", "ve"))
        // obsazené místo se přeskočí i tady
        assertNull(ObjectiveColors.locate("Jeď k jachtě.", "jachta", listOf(6..11)))
    }

    /**
     * Víceslovný cíl, který překladač samostatně přeloží jinak než ve větě (dávka F6PV-UY39 – růžový cíl zůstal bílý;
     * texty tady vymyšlené): první slovo v jiném pádě uvnitř kusu, jiné druhé slovo, kus z konce úkolu do konce věty.
     */
    @Test fun locatePhraseInSentence() {
        assertEquals(7..22, ObjectiveColors.locate("Jdi do skladu s nářadím", "sklad s nářadím"))
        // jiné slovo v samostatném překladu: bez [tail] jen společné slovo, s ním až do konce
        assertEquals(7..12, ObjectiveColors.locate("Jdi do skladu s nářadím", "sklad nástrojů"))
        assertEquals(7..22, ObjectiveColors.locate("Jdi do skladu s nářadím", "sklad nástrojů", tail = true))
        assertEquals(7..22, ObjectiveColors.locate("Jdi do skladu s nářadím.", "sklad nástrojů", tail = true))
        // nic společného → nic (ani s tail)
        assertNull(ObjectiveColors.locate("Jdi do skladu s nářadím", "dílna", tail = true))
        // poslední kus úkolu se pozná i s tečkou jako samostatným slovem
        val r = ObjectiveColors.runs(listOf("Go", "to", "the", "tool", "shed", "."), listOf(null, null, null, 0xF070C0, 0xF070C0, null))
        assertEquals(true, r.single().last)
        assertEquals(false, ObjectiveColors.runs(listOf("Take", "Sam", "home"), listOf(null, 0xF070C0, null)).single().last)
    }

    @Test fun partsSplitText() {
        val t = "Jdi do úkrytu."
        val p = ObjectiveColors.parts(t, listOf(7..12 to yellow))!!
        assertEquals(listOf(ObjectivePart("Jdi do ", null), ObjectivePart("úkrytu", yellow), ObjectivePart(".", null)), p)
        assertEquals(t, p.joinToString("") { it.text })
        assertNull(ObjectiveColors.parts(t, emptyList()))
    }

    @Test fun tvMessageWithParts() {
        val box = floatArrayOf(0.2f, 0.9f, 0.8f, 0.93f)
        val parts = listOf(ObjectivePart("Jdi do ", null), ObjectivePart("úkrytu", yellow), ObjectivePart(".", null))
        val m = TvSender.objectiveMessage("Jdi do úkrytu.", box, 40, 90, "#FFFFFF", parts)
        assertEquals("{\"typ\":\"ukol\",\"text\":\"Jdi do úkrytu.\",\"misto\":[0.2000,0.9000,0.8000,0.9300],\"pismo\":40,\"pozadi\":90,\"barva\":\"#FFFFFF\"," +
            "\"casti\":[{\"text\":\"Jdi do \",\"barva\":null},{\"text\":\"úkrytu\",\"barva\":\"#F2C14E\"},{\"text\":\".\",\"barva\":null}]}", m)
        // bez barevných kusů stejně jako dřív
        assertEquals(TvSender.objectiveMessage("Jdi do úkrytu.", box, 40, 90, "#FFFFFF"),
            TvSender.objectiveMessage("Jdi do úkrytu.", box, 40, 90, "#FFFFFF", listOf(ObjectivePart("Jdi do úkrytu.", null))))
    }
}
