package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Panel telefonu na volné straně obrazu ([PhoneFreeSide]); polohy telefonu vymyšlené (zlomky obrazu). */
class PhoneFreeSideTest {
    private val vpravo = floatArrayOf(0.70f, 0.40f, 0.86f, 0.75f)
    private val vlevo = floatArrayOf(0.12f, 0.40f, 0.28f, 0.75f)

    @Test
    fun telefonVpravoPanelVlevo() {
        val b = PhoneFreeSide().place(vpravo)!!
        assertTrue(PhoneFreeSide.onLeft(b))
        assertEquals(PhoneFreeSide.EDGE, b[0], 1e-4f)
        assertEquals(PhoneFreeSide.EDGE + PhoneFreeSide.W, b[2], 1e-4f)
        // nezakrývá telefon a stojí zhruba v jeho výšce, nad pruhem titulků
        assertTrue(b[2] <= vpravo[0] - PhoneFreeSide.GAP + 1e-4f)
        assertEquals(0.37f, b[1], 1e-4f)
        assertEquals(PhoneFreeSide.BOTTOM, b[3], 1e-4f)
    }

    @Test
    fun telefonVlevoPanelVpravo() {
        val b = PhoneFreeSide().place(vlevo)!!
        assertFalse(PhoneFreeSide.onLeft(b))
        assertEquals(1f - PhoneFreeSide.EDGE, b[2], 1e-4f)
        assertTrue(b[0] >= vlevo[2] + PhoneFreeSide.GAP - 1e-4f)
    }

    @Test
    fun malyPohybPanelNehybe() {
        val f = PhoneFreeSide()
        val a = f.place(vpravo)!!.copyOf()
        // telefon se pohupuje (dech, chůze) – o pár setin vodorovně i svisle
        for (d in listOf(0.01f, -0.02f, 0.03f, -0.01f)) {
            val b = f.place(floatArrayOf(vpravo[0] + d, vpravo[1] + d, vpravo[2] + d, vpravo[3] + d))!!
            assertTrue(a.contentEquals(b))
        }
    }

    @Test
    fun vetsiPosunSvisleSeProjevi() {
        val f = PhoneFreeSide()
        f.place(vpravo)
        val b = f.place(floatArrayOf(0.70f, 0.20f, 0.86f, 0.55f))!!
        assertEquals(0.17f, b[1], 1e-4f)
    }

    @Test
    fun stranaSeMeniAzZaPolovinou() {
        val f = PhoneFreeSide()
        assertTrue(PhoneFreeSide.onLeft(f.place(vpravo)!!))
        // střed telefonu těsně za polovinou → panel zůstává vlevo (nehoupe se mezi stranami)
        assertTrue(PhoneFreeSide.onLeft(f.place(floatArrayOf(0.40f, 0.4f, 0.56f, 0.75f))!!))
        // telefon výrazně vlevo → panel přejde doprava
        assertFalse(PhoneFreeSide.onLeft(f.place(vlevo)!!))
        // a zpět jen výrazným přesunem doprava
        assertFalse(PhoneFreeSide.onLeft(f.place(floatArrayOf(0.46f, 0.4f, 0.62f, 0.75f))!!))
        assertTrue(PhoneFreeSide.onLeft(f.place(vpravo)!!))
    }

    @Test
    fun telefonPresStredNevejdeSe() {
        val f = PhoneFreeSide()
        // široký telefon přes celý střed obrazu → na volnou stranu se panel nevejde, služba použije panel vedle
        assertNull(f.place(floatArrayOf(0.20f, 0.3f, 0.80f, 0.7f)))
        assertNull(f.box)
        assertNotNull(f.place(vpravo))
    }

    @Test
    fun vysokoNahoreAniDole() {
        val f = PhoneFreeSide()
        assertEquals(PhoneFreeSide.TOP_MIN, f.place(floatArrayOf(0.7f, 0.0f, 0.86f, 0.3f))!![1], 1e-4f)
        f.reset()
        assertEquals(PhoneFreeSide.TOP_MAX, f.place(floatArrayOf(0.7f, 0.9f, 0.86f, 0.98f))!![1], 1e-4f)
    }

    @Test
    fun doTvJenNepovinnePole() {
        val misto = floatArrayOf(0.03f, 0.37f, 0.29f, 0.8f)
        val a = TvSender.phoneMessage("Nora", listOf("Ahoj" to 'l'), misto, 22, 60)
        val b = TvSender.phoneMessage("Nora", listOf("Ahoj" to 'l'), misto, 22, 60, volna = true)
        assertFalse(a.contains("volna"))
        assertTrue(b.contains("\"volna\":true"))
        // bez pole je zpráva stejná jako dřív (starší TV)
        assertEquals(a, b.replace("\"volna\":true,", ""))
    }
}
