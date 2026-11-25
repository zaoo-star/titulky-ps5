package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Titulky PS5 – titulek podle pruhu hry. Vymyšlený obraz: šum scény, poloprůhledný pruh a světlé písmo. */
class SubtitleBoxTest {
    private val look = SubtitleBox.Look(0.334f, 22.6f)
    private val w = 640; private val h = 200

    /**
     * Scéna s plynulou texturou ([base] ± [noise], sousední body si jsou podobné jako ve skutečném obrazu – tráva,
     * asfalt, auto) a drobným šumem kódování; pruh podle vzorce; text = světlé svislé čárky.
     */
    private fun scene(base: Int, noise: Int, seed: Long = 1): IntArray {
        val r = Random(seed)
        val ph = r.nextDouble() * 6.28
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val v = base + noise * (0.6 * Math.sin(x / 23.0 + ph) * Math.cos(y / 31.0) + 0.4 * Math.sin((x + 2 * y) / 57.0))
            (v + r.nextInt(3) - 1).toInt().coerceIn(0, 255)
        }
    }
    private fun box(px: IntArray, l: Int, t: Int, r: Int, b: Int) {
        for (y in t until b) for (x in l until r) px[y * w + x] = Math.round(look.alpha * px[y * w + x] + look.offset).coerceIn(0, 255)
    }
    private fun text(px: IntArray, l: Int, t: Int, r: Int, b: Int) {
        for (y in t until b) for (x in l until r) if ((x / 3) % 2 == 0) px[y * w + x] = 235
    }
    private val line = OcrLine("Now you're pissing me off!", 220, 120, 420, 140)

    @Test fun titulekVPruhuVeDneIVNoci() {
        for ((base, noise) in listOf(60 to 25, 10 to 4, 140 to 40)) {
            val px = scene(base, noise)
            box(px, 200, 108, 440, 152)
            text(px, 220, 120, 420, 140)
            assertTrue("pozadí $base", SubtitleBox.boxed(px, w, h, line, look))
        }
    }

    @Test fun cedulaBezPruhuNeni() {
        for ((base, noise) in listOf(60 to 25, 10 to 4, 140 to 40)) {
            val px = scene(base, noise)
            text(px, 220, 120, 420, 140)
            assertFalse("pozadí $base", SubtitleBox.boxed(px, w, h, line, look))
        }
    }

    @Test fun pruhJinehoZtlumeniNeni() {
        val px = scene(60, 25)
        // tmavý, ale úplně jiný pruh (jiná hra nebo stín)
        for (y in 108 until 152) for (x in 200 until 440) px[y * w + x] = px[y * w + x] / 6
        text(px, 220, 120, 420, 140)
        assertFalse(SubtitleBox.boxed(px, w, h, line, look))
    }

    @Test fun filtrVyradiCeduluJenKdyzHraPruhKresli() {
        val f = SubtitleBox.Filter(look)
        val sign = OcrLine("RUBBER UP", 40, 20, 160, 40)
        val px = scene(60, 25)
        text(px, 40, 20, 160, 40)
        // pruh zatím nikdy nebyl vidět → nic se nevyřazuje
        val (a, da) = f.apply(listOf(OcrBlock(listOf(sign))), px, w, h, -1, 1000)
        assertEquals(1, a.size); assertEquals(0, da)
        // titulek v pruhu + cedule → cedule pryč
        box(px, 200, 108, 440, 152); text(px, 220, 120, 420, 140)
        val (b, db) = f.apply(listOf(OcrBlock(listOf(sign)), OcrBlock(listOf(line))), px, w, h, -1, 2000)
        assertEquals(listOf(line), b.flatMap { it.lines }); assertEquals(1, db)
        // řádek v pásu úkolu zůstává vždy
        val obj = OcrLine("Go to the garage", 250, 170, 390, 185)
        val (c, _) = f.apply(listOf(OcrBlock(listOf(sign, obj))), px, w, h, 165, 3000)
        assertEquals(listOf(obj), c.flatMap { it.lines })
        // dlouho bez pruhu (hráč ho vypnul) → zase se čte všechno
        val clean = scene(60, 25); text(clean, 40, 20, 160, 40)
        val (d, dd) = f.apply(listOf(OcrBlock(listOf(sign))), clean, w, h, -1, 3000 + SubtitleBox.FORGET_MS + 1)
        assertEquals(1, d.size); assertEquals(0, dd)
    }

    @Test fun profilNeseVzhledPruhu() {
        val json = """{"format":1,"verze":5,"minAppVerze":1,"bezneSlova":[],"hry":[{"id":"hra","nazev":"Hra","jmena":[],"titulky":{"pruh":[0.334,22.6]}}]}"""
        val g = GameListJson.parse(json, 999).catalog!!.games.single()
        assertNotNull(g.subtitleLook)
        assertEquals(0.334f, g.subtitleLook!!.alpha, 0.0001f)
        val bad = json.replace("[0.334,22.6]", "[2,22.6]")
        assertEquals(null, GameListJson.parse(bad, 999).catalog!!.games.single().subtitleLook)
    }

    private val first = OcrLine("Dunk: we give these guys Dr. Bill,", 200, 100, 440, 120)
    private val second = OcrLine("and then they give us the pills.", 240, 128, 400, 148)

    @Test fun dvaRadkyVeSpolecnemPruhu() {
        for ((base, noise) in listOf(60 to 25, 10 to 4, 140 to 40)) {
            val px = scene(base, noise)
            box(px, 185, 90, 455, 158)
            text(px, 200, 100, 440, 120); text(px, 240, 128, 400, 148)
            // každý řádek sám neprojde (pod prvním / nad druhým je text, ne hrana)…
            assertFalse(SubtitleBox.boxed(px, w, h, first, look))
            // …dohromady ano
            assertEquals("pozadí $base", setOf(first, second), SubtitleBox.boxedLines(px, w, h, listOf(second, first), look))
        }
    }

    @Test fun kazdyRadekVeVlastnimPruhu() {
        val px = scene(60, 25)
        box(px, 190, 90, 450, 124); box(px, 230, 124, 410, 158)
        text(px, 200, 100, 440, 120); text(px, 240, 132, 400, 152)
        val a = OcrLine("a", 200, 100, 440, 120); val b = OcrLine("b", 240, 132, 400, 152)
        assertEquals(setOf(a, b), SubtitleBox.boxedLines(px, w, h, listOf(a, b), look))
    }

    @Test fun dveCeduleNadSebouNejsou() {
        val px = scene(60, 25)
        text(px, 200, 100, 440, 120); text(px, 240, 128, 400, 148)
        assertTrue(SubtitleBox.boxedLines(px, w, h, listOf(first, second), look).isEmpty())
    }
}
