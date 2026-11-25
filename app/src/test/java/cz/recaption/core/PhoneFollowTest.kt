package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telefon ve hře od 1.00-rc21: natočený telefon při chůzi, ztlumený displej, sledování polohy, polohy z profilu hry,
 * otazníky a bubliny jen s emoji. Rozměry obraz 1280×720, texty i polohy vymyšlené.
 */
class PhoneFollowTest {
    private val w = 1280
    private val h = 720
    private val darkAll: (MenuLine) -> Boolean = { true }

    /** Řádek s rovným obdélníkem [x0]…[y1], natočený o sklon [a] kolem bodu ([cx], [cy]) – rámeček jako od čtečky. */
    private fun rot(t: String, x0: Int, y0: Int, x1: Int, y1: Int, a: Float, cx: Float = 620f, cy: Float = 360f): MenuLine {
        val c = 1f / kotlin.math.sqrt(1f + a * a); val s = a * c
        val pts = listOf(x0 to y0, x1 to y0, x0 to y1, x1 to y1).map { (x, y) ->
            val dx = x - cx; val dy = y - cy
            (cx + dx * c - dy * s) to (cy + dx * s + dy * c)
        }
        return MenuLine(t, pts.minOf { it.first }.toInt(), pts.minOf { it.second }.toInt(), pts.maxOf { it.first }.toInt(),
            pts.maxOf { it.second }.toInt(), -1, angle = a)
    }

    /** Článek v telefonu: dva odstavce po čtyřech řádcích (řádek 17 bodů, mezera mezi řádky 0, mezi odstavci 14). */
    private fun article(a: Float): List<MenuLine> {
        val texts = listOf("A long quiet evening on the", "harbour ended with a loud", "crash when two old boats", "hit the wooden pier.",
            "Nobody was hurt, the harbour", "master said, but the pier", "will stay closed for repairs", "until next month.")
        return texts.mapIndexed { i, t ->
            val top = 200 + i * 17 + (if (i >= 4) 14 else 0)
            rot(t, 520, top, 520 + 180 - (i % 4) * 6, top + 17, a)
        }
    }

    @Test fun tiltedPhoneParagraphsAsStraight() {
        // bez narovnání: rámečky natočených řádků se překrývají a z článku vznikla změť (dávka 6RKU-RZW3 – 20 „odstavců“ z 23 řádků)
        val straight = PhoneScreen.read(article(0f), w, h, darkAll)!!
        val tilted = PhoneScreen.read(article(0.1f), w, h, darkAll)!!
        assertEquals(straight.paragraphs.toString() + straight.sides, 2, straight.paragraphs.size)
        assertEquals(straight.paragraphs, tilted.paragraphs)
        assertEquals(0.1f, tilted.tilt, 0.001f)
        // místo telefonu zahrnuje celý natočený text (panel vedle ho nepřekryje)
        val lines = article(0.1f)
        assertTrue(tilted.left <= lines.minOf { it.left } / w.toFloat() + 0.01f)
        assertTrue(tilted.right >= lines.maxOf { it.right } / w.toFloat() - 0.01f)
        // místa odstavců jsou v obraze (natočená zpátky), ne v narovnaných souřadnicích
        val first = tilted.boxes[0]!!
        assertTrue(first[1] * h < lines[0].bottom && first[3] * h > lines[3].top)
    }

    @Test fun tiltNeedsAgreeingLines() {
        val few = article(0.1f).take(2)
        assertEquals(0f, PhoneScreen.tilt(few), 0f)
        assertEquals(0f, PhoneScreen.tilt(article(0.01f)), 0f)
        val mixed = article(0.1f).mapIndexed { i, l -> if (i % 2 == 0) l.copy(angle = -0.1f) else l }
        assertEquals(0f, PhoneScreen.tilt(mixed), 0f)
    }

    @Test fun dimScreenStillDarkText() {
        // ztlumený displej v noci (dávka 6RKU-RZW3): pozadí ~60, písmo ~30 – dřív „není telefon“
        val dim = IntArray(100) { if (it < 20) 28 + it % 5 else 57 + it % 4 }
        assertTrue(PhoneScreen.darkText(dim))
        // tmavá scéna s bílým titulkem telefon není
        val subtitle = IntArray(100) { if (it < 20) 230 else 30 + it % 5 }
        assertFalse(PhoneScreen.darkText(subtitle))
        // rovnoměrná tma bez písma taky ne
        assertFalse(PhoneScreen.darkText(IntArray(100) { 50 + it % 3 }))
    }

    @Test fun dimNotificationCard() {
        // nadpis seznamu upozornění na ztlumeném displeji: písmo jen ~100, pozadí ~22
        assertTrue(PhoneCard.light(IntArray(100) { if (it >= 85) 100 else 8 + it % 20 }))
        assertFalse(PhoneCard.light(IntArray(100) { 60 + it % 10 }))
    }

    @Test fun questionMarksStay() {
        assertTrue(PhoneScreen.marks("??"))
        assertTrue(PhoneScreen.marks(" ?! "))
        assertFalse(PhoneScreen.marks("..."))
        assertFalse(PhoneScreen.marks("Ok?"))
        val lines = listOf(
            MenuLine("Nora", 850, 156, 890, 172, -1),
            MenuLine("Did you bring the boxes back", 770, 210, 960, 230, -1),
            MenuLine("to the shop yesterday?", 770, 230, 920, 250, -1),
            MenuLine("??", 950, 300, 975, 320, -1),
            MenuLine("Call me later, I am driving.", 770, 360, 960, 380, -1),
        )
        val r = PhoneScreen.read(lines, w, h, darkAll)!!
        assertTrue(r.paragraphs.contains("??"))
        assertEquals('p', r.sides[r.paragraphs.indexOf("??")])
    }

    @Test fun blankBlueBubbleBecomesEmojiParagraph() {
        val base = PhoneRead(listOf("Did you bring the boxes back?", "Call me later."), 0.60f, 0.25f, 0.78f, 0.60f,
            sides = listOf('l', 'l'), title = "Nora",
            boxes = listOf(floatArrayOf(0.60f, 0.25f, 0.75f, 0.30f), floatArrayOf(0.60f, 0.52f, 0.70f, 0.55f)), lineH = 0.028f)
        // modrá bublina bez textu mezi zprávami (0,40–0,44), modrý okraj textové bubliny se nebere
        val rows = (0 until 60).map { i ->
            val y = 0.25f + i * 0.006f
            val blue = (y in 0.40f..0.44f) || (y in 0.24f..0.31f)
            PhoneScreen.BlueRow(y, if (blue) 0.3f else 0f, 0.72f, 0.77f)
        }
        val r = PhoneScreen.withBlankBubbles(base, rows)
        assertEquals(listOf("Did you bring the boxes back?", "", "Call me later."), r.paragraphs)
        assertEquals(listOf('l', 'p', 'l'), r.sides)
        assertNotNull(r.images[1])
        assertEquals(0.72f, r.images[1]!![0], 0.001f)
        // bez konverzace (článek) nic
        assertSame(base.copy(title = null, sides = listOf('s', 's')).let { PhoneScreen.withBlankBubbles(it, rows) }.paragraphs.size, 2)
        // TV dostane obrázek i u bubliny bez textu
        val msg = TvSender.phoneMessage("Nora", listOf("Ahoj" to 'l', "" to 'p'), floatArrayOf(0.1f, 0.1f, 0.3f, 0.8f), 22, 30,
            listOf(null, "data:image/jpeg;base64,AAAA"))
        assertTrue(msg.contains("{\"text\":\"\",\"strana\":\"p\",\"obrazek\":\"data:image/jpeg;base64,AAAA\"}"))
    }

    @Test fun conversationListNameOnlyRow() {
        // seznam konverzací: kontakt s náhledem jen z emoji (čtečka přečte jen jméno) zůstává jménem, nepřekládá se
        val lines = listOf(
            MenuLine("Nora", 808, 196, 850, 212, -1), MenuLine("See you at six", 806, 212, 914, 230, -1),
            MenuLine("Peter", 808, 270, 850, 288, -1), MenuLine("Thanks a lot.", 806, 288, 900, 306, -1),
            MenuLine("Blue Harbor", 808, 345, 900, 365, -1),
            MenuLine("Mia", 808, 400, 840, 418, -1), MenuLine("Where are you?", 806, 418, 930, 436, -1),
        )
        val r = PhoneScreen.read(lines, w, h, darkAll)!!
        assertEquals(listOf("Nora", "Peter", "Blue Harbor", "Mia"), r.names)
        assertEquals("", r.paragraphs[2])
    }

    @Test fun trackerShowsNewScreenAtOnce() {
        // jiná konverzace se ukáže hned (dávka JJAG-MBQB: čekalo se 4–7 s na dvě shodná čtení)
        val t = PhoneTracker()
        val a = listOf("Nora: See you at six", "Peter: Thanks a lot.", "Mia: Where are you?")
        assertTrue(t.update(a).changed)          // nic ukázané → hned
        assertFalse(t.update(a).changed)
        val b = listOf("Did you bring the boxes back to the shop?", "Not yet, tomorrow.", "Ok, thanks.")
        assertTrue(t.update(b).changed)
        // malé kolísání čtení téhož obsahu dál čeká na potvrzení
        val c = listOf("Did you bring the boxes back to the shop?", "Not yet, tomorow.", "Ok, thanks.", "Fine")
        assertFalse(t.update(c.take(2) + "Something else entirely here.").changed)
    }

    // ---- sledování polohy ----

    /** Jas obrazu: tmavá scéna 20, světlý displej 150 v obdélníku [x0]…[y1] (body). */
    private fun frame(x0: Int, y0: Int, x1: Int, y1: Int): IntArray = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        if (x in x0 until x1 && y in y0 until y1) 150 - (if ((y - y0) % 20 < 4) 60 else 0) else 20
    }

    @Test fun profileShiftFollowsPhone() {
        val box = PhoneMotion.area(floatArrayOf(0.55f, 0.2f, 0.8f, 0.85f))
        val a = PhoneMotion.profile(frame(720, 150, 1000, 610), w, h, box)
        val b = PhoneMotion.profile(frame(727, 144, 1007, 604), w, h, box)
        val (dx, dy) = PhoneMotion.shift(a, b)!!
        assertEquals(7f / w, dx, 0.0015f)
        assertEquals(-6f / h, dy, 0.0025f)
        // bez telefonu (plochá tma) se posun neurčí
        val dark = PhoneMotion.profile(IntArray(w * h) { 20 }, w, h, box)
        assertNull(PhoneMotion.shift(dark, dark))
    }

    @Test fun followIgnoresBreathingButFollowsWalk() {
        val f = PhoneFollow(PhoneFollow.PANEL)
        // pohupování o pár bodů tam a zpět panel nehýbe
        for (d in listOf(0.004f, -0.003f, 0.005f, -0.004f)) { f.target(d, d); f.step() }
        assertEquals(0f, f.x, 0f)
        // posun při chůzi dojede plynule (žádný skok)
        f.target(0.05f, 0f)
        assertTrue(f.step())
        assertEquals(0.025f, f.x, 0.0001f)
        repeat(12) { f.step() }
        assertEquals(0.05f, f.x, 0.0001f)
        assertFalse(f.step())
        // nové místo z čtení: nic neskočí
        f.rebase(-0.05f, 0f)
        assertEquals(0f, f.x, 0.0001f)
    }

    @Test fun deltaFromSameParagraphs() {
        val a = PhoneRead(listOf("Hello there", "See you"), 0.5f, 0.2f, 0.7f, 0.6f,
            boxes = listOf(floatArrayOf(0.5f, 0.2f, 0.6f, 0.25f), floatArrayOf(0.55f, 0.4f, 0.7f, 0.45f)))
        val b = a.copy(left = 0.45f, boxes = a.boxes.map { it!!.let { x -> floatArrayOf(x[0] + 0.01f, x[1] - 0.02f, x[2] + 0.01f, x[3] - 0.02f) } })
        val (dx, dy) = PhoneMotion.delta(a, b)
        assertEquals(0.01f, dx, 0.0001f)
        assertEquals(-0.02f, dy, 0.0001f)
    }

    // ---- polohy z profilu hry ----

    @Test fun zoneUsesRecentPhoneThenSpotsThenWide() {
        val car = PhoneSpot("auto", floatArrayOf(0.55f, 0.2f, 0.82f, 0.9f))
        val walk = PhoneSpot("pesky", floatArrayOf(0.3f, 0.2f, 0.6f, 0.9f), 0.1f)
        val z = PhoneZone(listOf(car, walk))
        // napřed polohy z profilu, střídavě se širokým pásem
        val first = z.next(0)
        assertEquals(PhoneZone.WIDE[0], first[0], 0.0001f)   // oříznuto na široký pás
        assertEquals(0.9f + PhoneZone.MARGIN, first[3], 0.0001f)
        assertSame(PhoneZone.WIDE, z.next(100))
        // telefon nalezen → jen kolem něj
        z.found(floatArrayOf(0.6f, 0.2f, 0.77f, 0.85f), 1000)
        val near = z.next(1500)
        assertEquals(0.6f - PhoneZone.MARGIN, near[0], 0.0001f)
        assertEquals(car, z.spotOf(floatArrayOf(0.6f, 0.2f, 0.77f, 0.85f)))
        // po chvíli bez telefonu zase hledání
        z.found(null, 6000)
        assertFalse(z.next(6100)[0] == near[0])
        // telefon dvakrát jinde než v profilu = hra vypadá jinak → profil se nepoužije
        z.found(floatArrayOf(0.05f, 0.05f, 0.2f, 0.3f), 7000)
        z.found(floatArrayOf(0.05f, 0.05f, 0.2f, 0.3f), 8000)
        assertFalse(z.spotsOk)
        assertSame(PhoneZone.WIDE, z.next(20000))
        // bez profilu jen široký pás
        assertSame(PhoneZone.WIDE, PhoneZone(emptyList()).next(0))
    }

    @Test fun phoneSpotsRoundTrip() {
        val text = """{"format":1,"verze":5,"minAppVerze":1,"hry":[
            {"id":"a","nazev":"A","telefon":{"polohy":[{"nazev":"auto","misto":[0.55,0.2,0.82,0.9]},
              {"nazev":"pesky","misto":[0.3,0.2,0.6,0.9],"sklon":0.1},{"misto":[0.9,0.1,0.2,0.5]},{"misto":[0.1,0.1,0.2]}]}},
            {"id":"b","nazev":"B"}]}"""
        val c = GameListJson.parse(text, 999, null).catalog!!
        val s = c.of("a").phoneSpots
        assertEquals(2, s.size)
        assertEquals(PhoneSpot("pesky", floatArrayOf(0.3f, 0.2f, 0.6f, 0.9f), 0.1f), s[1])
        assertTrue(c.of("b").phoneSpots.isEmpty())
        val again = GameListJson.parse(GameListJson.write(c), 999, null).catalog!!
        assertEquals(s, again.of("a").phoneSpots)
        assertFalse(GameListJson.write(c).substringAfter("\"id\":\"b\"").contains("telefon"))
    }

    @Test fun smsNotificationBottomRight() {
        // upozornění na SMS bez telefonu v ruce: ikona + jméno, zpráva, pod bublinou „→ příjemce“ (rozměry jako dávka N6CU-NTGR)
        val lines = listOf(
            MenuLine("&Nora", 1068, 612, 1113, 628, -1),
            MenuLine("Bring the keys back tonight", 1037, 638, 1238, 655, -1),
            MenuLine("> Peter", 1170, 678, 1219, 693, -1),
        )
        val n = PhoneNotice.read(lines, w, h)!!
        assertTrue(n.sms)
        assertEquals("Nora", n.app)
        assertEquals("Bring the keys back tonight", n.text)
        assertEquals(638f / h, n.top, 0.001f)
        // ukáže se hned po prvním čtení
        assertTrue(NoticeTracker().update(n).changed)
        // bez řádku příjemce to upozornění na SMS není (jméno a věta nad sebou mohou být cokoli)
        assertNull(PhoneNotice.sms(lines.take(2), w, h))
    }

    @Test fun besideMessageForTv() {
        val m = TvSender.phoneOverMessage(listOf("Nora: Ahoj" to floatArrayOf(0.3f, 0.2f, 0.54f, 0.25f)), 22, beside = true)
        assertTrue(m.startsWith("{\"typ\":\"telefon\",\"rezim\":\"pres\",\"vedle\":true,\"pismo\":22,"))
        assertFalse(TvSender.phoneOverMessage(listOf("x" to floatArrayOf(0.3f, 0.2f, 0.54f, 0.25f)), 22).contains("vedle"))
    }
}
