package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Brána „telefon ve hře je otevřený“ ([PhoneGate], [PhoneOpen]). Kreslené obrazy tady; na skutečných snímcích dávek
 * (CW8G-3SHU = jízda s nápisy nad silnicí, telefon zavřený; 6RKU-RZW3 a JJAG-MBQB = telefon otevřený) se pouští
 * [skutecneDavky] – jen když proměnná RECAPTION_DAVKY ukazuje na složku s rozbalenými dávkami (snímky jsou soukromé).
 */
class PhoneGateTest {
    private val W = 1280
    private val H = 720
    private val AUTO = floatArrayOf(0.57f, 0.15f, 0.80f, 0.88f)
    private val PESKY = floatArrayOf(0.34f, 0.15f, 0.64f, 0.88f)

    /** Obraz: pozadí [bg] se šumem, do něj [draw]. */
    private fun frame(bg: Int, draw: (IntArray) -> Unit = {}): IntArray {
        val rnd = java.util.Random(7)
        val f = IntArray(W * H) { (bg + rnd.nextInt(9) - 4).coerceIn(0, 255) }
        draw(f)
        return f
    }

    /** Světlý displej [l]–[r] × [t]–[b] (body) s tmavým rámečkem, hrany nakloněné o [slope] (vodorovně na bod výšky). */
    private fun phone(f: IntArray, l: Int, t: Int, r: Int, b: Int, light: Int = 190, slope: Float = 0f) {
        for (y in t - 8 until b + 8) {
            val s = (slope * (y - (t + b) / 2)).toInt()
            for (x in l - 8 + s until r + 8 + s) if (x in 0 until W && y in 0 until H)
                f[y * W + x] = if (y in t until b && x in l + s until r + s) light - (if ((y / 14) % 3 == 0 && x % 5 < 3) 60 else 0) else 8
        }
    }

    /** Ikona tlačítka v kroužku na místě posledního řádku ovládání vpravo dole. */
    private fun icon(f: IntArray, dx: Int = 0, dy: Int = 0) {
        val cx = PhoneGate.ICON_X * W + dx; val cy = PhoneGate.ICON_Y * H + dy
        for (y in H - 80 until H) for (x in (W - 80) until W) {
            val d = Math.hypot((x - cx).toDouble(), (y - cy).toDouble())
            if (d in 7.0..9.6 || d < 3.5) f[y * W + x] = 240 else if (d < 7.0) f[y * W + x] = 20
        }
    }

    private fun quad(f: IntArray, spots: List<FloatArray> = listOf(AUTO, PESKY)) =
        PhoneGate.quad(PhoneGate.sample(W, H, PhoneZone.WIDE, PhoneGate.COLS, PhoneGate.ROWS) { x, y -> f[y * W + x] }, W, H, spots)

    private fun hasIcon(f: IntArray): Boolean {
        val (box, n) = PhoneGate.iconBox(W, H)
        return PhoneGate.icon(PhoneGate.sample(W, H, box, n, n) { x, y -> f[y * W + x] }, n)
    }

    @Test fun displejVAute() {
        val q = quad(frame(20) { phone(it, 742, 150, 1010, 600) })
        assertNotNull(q)
        assertEquals(742f / W, q!![0], 0.01f); assertEquals(1010f / W, q[2], 0.01f)
        assertTrue(q[1] < 0.30f && q[3] > 0.75f)
    }

    @Test fun displejVeDne_svetlaScena() {
        assertNotNull(quad(frame(150) { phone(it, 742, 150, 1010, 600, light = 235) }))
    }

    @Test fun nakloneny_priChuzi() {
        assertNotNull(quad(frame(25) { phone(it, 520, 140, 740, 610, slope = 0.12f) }))
        assertNotNull(quad(frame(25) { phone(it, 520, 140, 740, 610, slope = -0.18f) }))
    }

    @Test fun prazdnaScena_neniTelefon() {
        assertNull(quad(frame(20)))
        assertNull(quad(frame(160)))
    }

    @Test fun cedule_sirsiNezVyssi_neniTelefon() {
        // billboard / dopravní cedule nad silnicí: světlý obdélník naležato
        assertNull(quad(frame(90) { phone(it, 600, 120, 1000, 260, light = 230) }))
    }

    @Test fun uzkySloup_aSirokaZed_nejsouTelefon() {
        assertNull(quad(frame(60) { phone(it, 800, 100, 860, 650, light = 220) }))      // sloup: moc úzký
        assertNull(quad(frame(60) { phone(it, 500, 100, 1100, 650, light = 220) }))     // zeď: moc široká
    }

    @Test fun mimoPolohyZProfilu_neniTelefon() {
        // světlé dveře úplně vpravo (0,81–0,97): profil GTA tam telefon nemá; bez profilu by prošly
        val f = frame(40) { phone(it, 1040, 150, 1240, 600) }
        assertNull(quad(f))
        assertNotNull(quad(f, emptyList()))
    }

    @Test fun ikonaOvladani() {
        assertTrue(hasIcon(frame(15) { icon(it) }))
        assertTrue(hasIcon(frame(15) { icon(it, dx = 3, dy = -2) }))
        assertTrue(hasIcon(frame(110) { icon(it) }))
        assertFalse(hasIcon(frame(15)))
        assertFalse(hasIcon(frame(230)))        // světlá plocha bez kroužku
    }

    @Test fun ikona_iVeFullHd() {
        val w = 1920; val h = 1080
        val f = IntArray(w * h) { 12 }
        val cx = PhoneGate.ICON_X * w; val cy = PhoneGate.ICON_Y * h
        for (y in h - 120 until h) for (x in w - 120 until w) {
            val d = Math.hypot((x - cx).toDouble(), (y - cy).toDouble())
            if (d in 10.5..14.4 || d < 5.0) f[y * w + x] = 240
        }
        val (box, n) = PhoneGate.iconBox(w, h)
        assertEquals(54, n)
        assertTrue(PhoneGate.icon(PhoneGate.sample(w, h, box, n, n) { x, y -> f[y * w + x] }, n))
    }

    @Test fun stavBrany() {
        val o = PhoneOpen()
        val q = floatArrayOf(0.58f, 0.2f, 0.79f, 0.84f)
        assertFalse(o.open); assertEquals("ovladani", o.reason)
        // ovládání vpravo dole bez displeje (posilovna: „RERACK ⊙“) → zavřeno, důvod tvar
        assertTrue(o.update(icon = true, quad = null)); assertFalse(o.open); assertEquals("tvar", o.reason)
        assertFalse(o.update(icon = true, quad = null))
        // bez ovládání se displej ani neměří
        assertTrue(o.update(icon = false, quad = null)); assertEquals("ovladani", o.reason)
        // obojí → otevřeno
        assertTrue(o.update(icon = true, quad = q)); assertTrue(o.open); assertTrue(q.contentEquals(o.box))
        // tmavá domovská obrazovka: hrany nejsou, ovládání ano → zůstává otevřeno, čte se kolem posledního displeje
        assertFalse(o.update(icon = true, quad = null)); assertTrue(o.open); assertNotNull(o.box)
        // ikona jednou problikne → ještě otevřeno; podruhé → zavřeno
        assertFalse(o.update(icon = false, quad = null)); assertTrue(o.open)
        assertTrue(o.update(icon = false, quad = null)); assertFalse(o.open); assertNull(o.box)
    }

    @Test fun vyrezAOtisk() {
        val z = PhoneGate.zone(floatArrayOf(0.58f, 0.40f, 0.79f, 0.70f))
        // do stran jen kolem displeje, na výšku celý telefon – ne široký pás
        assertEquals(0.52f, z[0], 0.001f); assertEquals(0.85f, z[2], 0.001f)
        assertTrue(z[1] <= 0.16f && z[3] >= 0.89f)
        assertTrue((z[2] - z[0]) < (PhoneZone.WIDE[2] - PhoneZone.WIDE[0]) / 2)
        val g = PhoneGate.grid(z)
        assertEquals(36, g.size)
        assertTrue(g.all { it[0] >= z[0] && it[2] <= z[2] && it[1] >= z[1] && it[3] <= z[3] && it[0] < it[2] && it[1] < it[3] })
    }

    // Zkouška na skutečných dávkách snímků (javax.imageio – na GitHubu v testech pro Android není) je v soukromém
    // repu Recaption-data/hry (PhoneGateDavkyTest), kde jsou i dávky.
}
