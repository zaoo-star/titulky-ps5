package cz.recaption.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Titulky PS5: scrollování konverzace v telefonu ve hře. Dřív se při posunu čekalo na dvě shodná čtení – při scrollování
 * je každé čtení jiné, takže se nový obsah nikdy neukázal. Vymyšlené zprávy (žádné texty ze skutečné hry).
 */
class PhoneScrollTest {

    private val chat = listOf(
        "Hey are you coming tonight or not",
        "I told you I have work until nine",
        "Fine but bring the car this time please",
        "Which car the red one or the van",
        "The van obviously we need the space",
        "Ok see you at the docks around ten",
        "Dont be late again I mean it",
        "Relax I will be there on time",
    )

    @Test fun scrollDolu_ukazeSeHned() {
        val t = PhoneTracker()
        assertTrue(t.update(chat.subList(0, 4)).changed)
        // posun o dvě zprávy dolů: nahoře dvě zmizely, dole dvě přibyly
        val r = t.update(chat.subList(2, 6))
        assertTrue("posun dolů se má ukázat hned", r.changed)
        assertEquals(chat.subList(2, 6), r.paragraphs)
    }

    @Test fun scrollNahoru_ukazeSeHned() {
        val t = PhoneTracker()
        t.update(chat.subList(4, 8))
        val r = t.update(chat.subList(2, 6))
        assertTrue("posun nahoru se má ukázat hned", r.changed)
        assertEquals(chat.subList(2, 6), r.paragraphs)
    }

    @Test fun plynuleScrollovani_kazdeCteniJine() {
        val t = PhoneTracker()
        t.update(chat.subList(0, 4))
        // po jedné zprávě: každé čtení jiné – dřív se nepotvrdilo ani jedno
        for (from in 1..4) {
            val r = t.update(chat.subList(from, from + 4))
            assertTrue("krok $from se má ukázat", r.changed)
            assertEquals(chat.subList(from, from + 4), r.paragraphs)
        }
        assertFalse(t.waiting)
    }

    @Test fun napulUseknutaZpravaNaKraji() {
        val t = PhoneTracker()
        t.update(chat.subList(0, 4))
        // nahoře je vidět jen konec třetí zprávy („the car this time please“), dole dvě nové
        val read = listOf("the car this time please", chat[3], chat[4], chat[5])
        val r = t.update(read)
        assertTrue(r.changed)
        assertEquals(read, r.paragraphs)
    }

    @Test fun stejnyObsah_nicSeNemeni() {
        val t = PhoneTracker()
        t.update(chat.subList(0, 4))
        assertFalse(t.update(chat.subList(0, 4)).changed)
    }

    @Test fun drobnaChybaCteni_neniPosun() {
        val a = chat.subList(0, 4)
        // jedno písmeno jinak = tentýž obsah, ne posun
        val b = listOf(a[0], a[1].replace("work", "wark"), a[2], a[3])
        assertFalse(PhoneTracker().scrolled(a, b))
    }

    @Test fun prohazeneZpravy_neniPosun() {
        val a = chat.subList(0, 4)
        assertFalse(PhoneTracker().scrolled(a, listOf(a[2], chat[6], a[0], chat[7])))
    }

    @Test fun uplneJinaKonverzace_neniPosun() {
        assertFalse(PhoneTracker().scrolled(chat.subList(0, 3), chat.subList(5, 8)))
    }
}
