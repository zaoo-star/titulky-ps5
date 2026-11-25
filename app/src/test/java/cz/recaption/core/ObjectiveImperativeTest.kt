package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Úkol ve hře jako rozkaz: „Drive to the harbor.“ → „Jeď k přístavu.“, ne „Cesta k přístavu“. */
class ObjectiveImperativeTest {
    @Test fun onlyCommandsInCzechAndSlovak() {
        assertTrue(ObjectiveImperative.applies("Drive to the harbor.", Lang.CS))
        assertTrue(ObjectiveImperative.applies("Go to the marked location", Lang.SK))
        assertFalse(ObjectiveImperative.applies("The harbor is waiting.", Lang.CS))
        assertFalse(ObjectiveImperative.applies("Drive to the harbor.", Lang.DE))
        assertFalse(ObjectiveImperative.applies("Drive to the harbor.", Lang.CS, english = false))
        assertEquals("Please drive to the harbor.", ObjectiveImperative.prepare("Drive to the harbor"))
    }

    @Test fun pleaseCutAndSingular() {
        assertEquals("Jeď k přístavu.", ObjectiveImperative.finish("Prosím, jeďte k přístavu."))
        assertEquals("Následuj Silvii.", ObjectiveImperative.finish("Prosím, následujte Silvii."))
        assertEquals("Jdi na označené místo.", ObjectiveImperative.finish("Prosím jděte na označené místo."))
        assertEquals("Setřes policii.", ObjectiveImperative.finish("Prosím, setřeste policii."))
        assertEquals("Zastav u domu.", ObjectiveImperative.finish("Prosím vás, zastavte u domu."))
        assertNull(ObjectiveImperative.finish("Cesta k přístavu."))      // bez „prosím“ → přeložit obyčejně
    }

    @Test fun singularForms() {
        mapOf("vraťte" to "vrať", "seberte" to "seber", "běžte" to "běž", "najděte" to "najdi", "vezměte" to "vezmi",
            "zabijte" to "zabij", "počkejte" to "počkej", "utečte" to "uteč", "Choďte" to "Choď", "jeď" to "jeď", "jedete" to "jedete")
            .forEach { (a, b) -> assertEquals(a, b, ObjectiveImperative.singular(a)) }
    }

    @Test fun brokenNameInObjective() {
        // čtečka jméno v úkolu zkomolí („Follow Viktor“ jako Vikt / Vitor / Viktar)
        val names = listOf("Viktor", "Tomas", "Silvia", "Noemi", "Benny")
        assertEquals("Follow Viktor", ObjectiveImperative.fixNames("Follow Vikt", names))
        assertEquals("Follow Viktor", ObjectiveImperative.fixNames("Follow Vitor", names))
        assertEquals("Follow Viktor.", ObjectiveImperative.fixNames("Follow Viktar.", names))
        assertEquals("Follow Viktor", ObjectiveImperative.fixNames("Follow Viktor", names))
        // první slovo je sloveso, malá písmena nejsou jméno, cizí slovo zůstane
        assertEquals("Bring the car to Tomas", ObjectiveImperative.fixNames("Bring the car to Tomas", names))
        assertEquals("Go to the bring zone", ObjectiveImperative.fixNames("Go to the bring zone", names))
        assertEquals("Go to Port Vale", ObjectiveImperative.fixNames("Go to Port Vale", names))
    }

    @Test fun nameInAccusative() {
        val f = mapOf("Viktor" to false, "Silvia" to true, "Noemi" to true)
        assertEquals("Následuj Viktora", ObjectiveImperative.accusative("Follow Viktor", "Následuj Viktor", Lang.CS) { f[it] })
        assertEquals("Následuj Viktora.", ObjectiveImperative.accusative("Follow Viktor.", "Následuj Viktor.", Lang.CS) { f[it] })
        assertEquals("Následuj Silvii", ObjectiveImperative.accusative("Follow Silvia", "Následuj Silvia", Lang.CS) { f[it] })
        assertEquals("Nasleduj Silviu", ObjectiveImperative.accusative("Follow Silvia", "Nasleduj Silvia", Lang.SK) { f[it] })
        assertEquals("Najdi Noemi", ObjectiveImperative.accusative("Find Noemi", "Najdi Noemi", Lang.CS) { f[it] })
        // už skloňované, jiné sloveso nebo jiný jazyk – beze změny
        assertEquals("Následuj Viktora", ObjectiveImperative.accusative("Follow Viktor", "Následuj Viktora", Lang.CS) { f[it] })
        assertEquals("Promluv si s Viktor", ObjectiveImperative.accusative("Talk to Viktor", "Promluv si s Viktor", Lang.CS) { f[it] })
        assertEquals("Folge Viktor", ObjectiveImperative.accusative("Follow Viktor", "Folge Viktor", Lang.DE) { f[it] })
    }

    @Test fun leavePlace() {
        assertEquals("Opusť Lake Moro", ObjectiveImperative.leave("Leave Lake Moro", "Odejdi Lake Moro", Lang.CS))
        assertEquals("Opusť Tobyho přívěs", ObjectiveImperative.leave("Leave Toby's trailer", "Nech Tobyho přívěs", Lang.CS))
        assertEquals("Opusť dům", ObjectiveImperative.leave("Leave the house", "Odejdi z dům", Lang.CS))
        assertEquals("Opusti Lake Moro", ObjectiveImperative.leave("Leave Lake Moro", "Odíď Lake Moro", Lang.SK))
        assertEquals("Jeď domů", ObjectiveImperative.leave("Drive home", "Jeď domů", Lang.CS))
    }
}
