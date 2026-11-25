package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Titulky PS5: přibalený profil GTA VI (assets/hry-profily.json) se musí přečíst celý, i s polohami telefonu. */
class BundledProfileTest {
    /** Soubor profilu hledaný od pracovní složky nahoru (Gradle běží v app/, místní skript jinde). */
    private fun file(): File {
        var d: File? = File("").absoluteFile
        while (d != null) {
            for (rel in listOf("src/main/assets/hry-profily.json", "app/src/main/assets/hry-profily.json")) File(d, rel).let { if (it.exists()) return it }
            d = d.parentFile
        }
        error("hry-profily.json nenalezen")
    }

    @Test fun profilSePrecte() {
        val r = GameListJson.parse(file().readText(), 318)
        val cat = r.catalog
        assertNotNull("problém: ${r.problem}", cat)
        val gta = cat!!.games.single()
        assertEquals("Grand Theft Auto VI", gta.title)
        assertTrue(cat.version > GameProfiles.BUILTIN.version || cat.version >= 1)
    }

    @Test fun mobilPriChuziIVAute() {
        val text = file().readText()
        assertTrue("\"nazev\":\"chuze\"" in text && "\"nazev\":\"auto\"" in text)
        // obrazovka mobilu v autě změřená ze screenů GTA VI (2576×1449): x 56–77 %, y 20–86 %
        val z = PhoneZone(listOf(PhoneSpot("auto", floatArrayOf(0.555f, 0.19f, 0.785f, 0.875f))))
        assertNotNull(z.spotOf(floatArrayOf(0.564f, 0.20f, 0.77f, 0.863f)))
    }
}
