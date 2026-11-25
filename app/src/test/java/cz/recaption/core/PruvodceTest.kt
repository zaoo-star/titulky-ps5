package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/** Šablona průvodce ([Pruvodce]) a její soulad s webem (`pomoc/pruvodce-data.js`) a klipy (`pomoc/videa/kroky.json`). */
class PruvodceTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "pomoc/pruvodce.html").isFile }

    /** Web bere data jen ze šablony: soubor musí být přesně výstup [Pruvodce.webJs]. Přegenerovat: PRUVODCE_ZAPSAT=1. */
    @Test fun webOdpovidaSablone() {
        val f = File(root, "pomoc/pruvodce-data.js")
        val novy = Pruvodce.webJs()
        if (System.getenv("PRUVODCE_ZAPSAT") == "1") f.writeText(novy)
        assertTrue("pomoc/pruvodce-data.js chybí – přegenerovat: gradlew -p ci/mistni-kontrola test -Ppruvodce", f.isFile)
        assertEquals("pomoc/pruvodce-data.js neodpovídá šabloně Pruvodce.kt – přegenerovat: gradlew -p ci/mistni-kontrola test -Ppruvodce",
            novy, f.readText().replace("\r\n", "\n"))
    }

    /** Stránka vykresluje jen data: žádné texty kroků v ní nezůstaly a data načítá. */
    @Test fun strankaNactiData() {
        val html = File(root, "pomoc/pruvodce.html").readText()
        assertTrue(html.contains("<script src=\"pruvodce-data.js\"></script>"))
        assertTrue("v pruvodce.html zůstal text kroku", !html.contains("Downloader do TV") && !html.contains("Key Server"))
    }

    /** Každý klip ze seznamu patří k podkroku šablony (krok-<krok>[-<podkrok>][-<tv>][-<konzole>]). */
    @Test fun klipyPatriKPodkrokum() {
        val klipy = VideoSteps.parse(File(root, "pomoc/videa/kroky.json").readText())
        assertTrue(klipy.isNotEmpty())
        for (id in klipy.keys) {
            var casti = id.removePrefix("krok-").split("-")
            if (casti.last() in setOf("ps", "xbox")) casti = casti.dropLast(1)
            if (casti.last() in setOf("android", "lg", "samsung")) casti = casti.dropLast(1)
            val krok = Pruvodce.KROKY.firstOrNull { it.id == casti[0] } ?: run { fail("klip $id: krok ${casti[0]} v šabloně není"); return }
            val pod = casti.getOrNull(1)
            assertTrue("klip $id: v kroku ${krok.id} žádný podkrok s klipem ${pod ?: "(celý krok)"}", krok.podkroky.any { it.klip != null && it.klip.pod == pod })
            assertTrue("klip $id: víc než krok-podkrok", casti.size <= 2)
        }
    }

    /** Id klipů v šabloně platí pro aplikaci ([VideoSteps.ID]) a kroky 1–7 jsou kroky aplikace ve stejném pořadí. */
    @Test fun klipyAKrokyJakoAplikace() {
        assertEquals(VideoSteps.STEPS, Pruvodce.KROKY.drop(1).map { it.id })
        for (kr in Pruvodce.KROKY) for (pk in kr.podkroky) pk.klip?.let {
            val id = "krok-${kr.id}" + (it.pod?.let { p -> "-$p" } ?: "") + "-android-ps"
            assertTrue(id, VideoSteps.ID.matches(id))
            if (it.pod != null) assertTrue(it.pod, it.pod !in setOf("android", "lg", "samsung", "ps", "xbox"))
        }
    }

    /** Pro každou kombinaci voleb: viditelný krok má podkroky, id podkroků se v kroku neopakují, texty se vyřeší. */
    @Test fun kazdaKombinaceSedi() {
        for (s in Pruvodce.stavy()) for (kr in Pruvodce.kroky(s)) {
            val pody = Pruvodce.podkroky(kr, s)
            assertTrue("$s: krok ${kr.id} bez podkroků", pody.isNotEmpty())
            assertEquals("$s: krok ${kr.id} – opakované id podkroku", pody.size, pody.map { it.id }.toSet().size)
            for (pk in pody) {
                assertNotNull("$s ${kr.id}.${pk.id}: název", pk.nazev.pro(s))
                pk.kde?.let { assertNotNull("$s ${kr.id}.${pk.id}: kde", it.pro(s)) }
                for (b in pk.bloky) if (b.kdy?.plati(s, true) != false) {
                    b.text?.let { assertNotNull("$s ${kr.id}.${pk.id}: blok ${b.typ}", it.pro(s, true)) }
                    for (m in b.moznosti) { assertNotNull(m.nazev.pro(s)); assertNotNull(m.popis.pro(s)) }
                    if (b.typ == "ol" || b.typ == "ul") assertTrue("$s ${kr.id}.${pk.id}: prázdný seznam", b.polozky.any { it.kdy?.plati(s) ?: true })
                }
            }
        }
    }

    /** Přehled „Co je potřeba“: každý řádek má buňku pro každý sloupec a v každém sloupci aspoň něco nutného. */
    @Test fun prehled() {
        for (r in Pruvodce.PREHLED) assertEquals(r.ikona, Pruvodce.PREHLED_SLOUPCE.size, r.bunky.size)
        for (c in Pruvodce.PREHLED_SLOUPCE.indices) assertTrue(Pruvodce.PREHLED.any { r -> r.bunky[c].any { !it.nepovinne } })
    }

    /**
     * Každý text šablony je ve všech jazycích aplikace ([Lang.uiLangs]); jazykově neutrální (cs = en) se nepřekládá.
     * Překlad drží HTML značky, odkazy a zástupné znaky {tv} / {znacka} jako angličtina.
     */
    @Test fun prekladyVeVsechJazycich() {
        val chybi = mutableListOf<String>()
        val znacky = Regex("""<[^>]+>|\{tv\}|\{znacka\}""")
        for (t in Pruvodce.texty().distinctBy { it.en }) if (t.cs != t.en) for (l in Lang.uiLangs) {
            if (l.code == "cs" || l.code == "en") continue
            val x = t.jine[l.code] ?: PruvodcePreklady.get(l.code, t.en)
            if (x == null) { chybi += "${l.code}: ${t.en}"; continue }
            // odkaz na stránky PlayStation smí vést na verzi v daném jazyce (cs-cz, pl-pl…)
            fun z(s: String) = znacky.findAll(s.replace(Regex("""playstation\.com/[a-z]{2}-[a-z]{2}/"""), "playstation.com/xx/")).map { it.value }.toList().sorted()
            assertEquals("${l.code}: značky v „$x“", z(t.en), z(x))
        }
        assertTrue("chybí překlad (${chybi.size}) – doplnit do PruvodcePreklady*.kt:\n" + chybi.take(30).joinToString("\n"), chybi.isEmpty())
    }

    /** Překlady bez textu, který v šabloně už není (po změně anglického znění zůstal starý). */
    @Test fun prekladyBezStarych() {
        val en = Pruvodce.texty().map { it.en }.toSet()
        for ((l, m) in PruvodcePreklady.MAPY) assertTrue("$l: navíc ${m.keys - en}", (m.keys - en).isEmpty())
    }

    /** Aplikace: každá obrazovka průvodce má ze šablony nadpis a volby rozcestníku / konzole / TV mají názvy – ve všech jazycích. */
    @Test fun aplikaceBereZeSablony() {
        for (l in Lang.uiLangs) for (tv in listOf("atv", "lg", "samsung")) for (ko in listOf("ps5", "xbox")) {
            val s = Pruvodce.appStav(tv, "ovladac", ko, false, if (tv == "atv") "tv" else "telefon")
            for (scr in listOf("langs", "device", "console", "lang", "done")) assertNotNull("$scr $s ${l.code}", Pruvodce.appNadpis(scr, s, l.code))
            assertEquals(tv == "atv", Pruvodce.appNadpis("perm", s, l.code) != null)
            assertTrue(Pruvodce.appRadky("done", s, l.code).isNotEmpty())
            for (v in listOf("telefon", "jine", "ovladac")) assertNotNull(Pruvodce.volba("na", v, s, l.code))
            for (v in listOf("ps5", "xbox")) assertNotNull(Pruvodce.volba("ko", v, s, l.code))
            for (v in listOf("atv", "lg", "samsung")) assertTrue(Pruvodce.volba("tv", v, s, l.code)!!.first.isNotBlank())
            // žádné HTML ani zástupné znaky v textech pro aplikaci
            for ((t, _) in Pruvodce.appRadky("done", s, l.code) + Pruvodce.appRadky("langs", s, l.code)) assertTrue(t, !t.contains('<') && !t.contains('{'))
        }
        // Xbox: krok Připojení se v aplikaci přeskočí (přihlášení je v kroku Konzole), Zobrazení titulků jen Android TV
        assertEquals(false, Pruvodce.krok("pripojeni").kdy!!.plati(Pruvodce.appStav("atv", "ovladac", "xbox", false, "tv")))
        assertEquals(false, Pruvodce.krok("zobrazeni").kdy!!.plati(Pruvodce.appStav("lg", "telefon", "ps5", false, "telefon")))
    }

    /** Volby na webu odpovídají tomu, co šablona v podmínkách používá. */
    @Test fun podminkyZnajiVolby() {
        fun chk(k: Pruvodce.Kdy?) {
            if (k == null) return
            k.tv?.let { assertTrue(it.toString(), Pruvodce.VOLBY.getValue("tv").containsAll(it)) }
            k.na?.let { assertTrue(it.toString(), Pruvodce.VOLBY.getValue("na").containsAll(it)) }
            k.ko?.let { assertTrue(it.toString(), Pruvodce.VOLBY.getValue("ko").containsAll(it)) }
            k.dl?.let { assertTrue(it.toString(), Pruvodce.VOLBY.getValue("dl").containsAll(it)) }
            k.cesta?.let { assertTrue(it.toString(), Pruvodce.VOLBY.getValue("in").containsAll(it)) }
        }
        for (kr in Pruvodce.KROKY) { chk(kr.kdy); for (pk in kr.podkroky) {
            chk(pk.kdy); pk.kde?.varianty?.forEach { chk(it.kdy) }; pk.nazev.varianty.forEach { chk(it.kdy) }
            for (b in pk.bloky) { chk(b.kdy); b.polozky.forEach { chk(it.kdy) }; b.text?.varianty?.forEach { chk(it.kdy) }
                b.klic?.let { assertTrue(it, it in Pruvodce.VOLBY) }
                b.moznosti.forEach { m -> assertTrue(m.v, m.v in Pruvodce.VOLBY.getValue(b.klic!!)); chk(m.vypnuto) } }
        } }
    }
}
