package cz.recaption.pc

import cz.recaption.core.CornerPrompts
import cz.recaption.core.CornerView
import cz.recaption.core.GameMenu
import cz.recaption.core.GameProfiles
import cz.recaption.core.MenuLine
import cz.recaption.core.MenuView
import cz.recaption.core.MenuWord
import cz.recaption.core.ObjectiveTracker
import cz.recaption.core.OcrLine
import cz.recaption.core.Picked
import cz.recaption.core.ScreenLayout
import cz.recaption.core.TextLogic
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

/**
 * Rozbor uložených snímků (ladění oblastí titulků a úkolu a čtení menu GTA VI ze skutečných snímků, ne odhadem).
 *
 *   pc\rozbor.cmd <složka> [--hra gta6] [--zona-ukolu 0.915] [--obrazky]
 *
 * Složka = dávka ze Záznamu snímků na Androidu (`zaznamy\davky-…\davka-…`, `ci/stahni-davky.ps1`), celé `davky-…`,
 * nebo starší `zaznamy\hra-…` / `pc-…`; projdou se všechny podsložky se snímky `snimek-*.jpg`.
 * Každý snímek se celý přečte (tentýž RapidOCR jako čtečka pro PC, `cteni.py --rozbor`) a pak se na něj pustí
 * tytéž kroky z `core` jako v aplikaci: řádky v oblasti čtení (výchozí `ScreenLayout.DEFAULT_READ`) →
 * `TextLogic.assemble` s úkolem (`objectiveMinTop` = hranice úkolu jako `TranslatorService.OBJECTIVE_ZONE_TOP`)
 * a horních 60 % → `GameMenu.read`, pravý dolní roh → `CornerPrompts.read` (menu vpravo dole; šedé položky se tu
 * nepoznají – jas z rozboru je průměr řádku, ne jas písma). Vedle snímků vznikne `rozbor.txt` (snímek po snímku + souhrn) a `rozbor.csv`,
 * s `--obrazky` i `rozbor\<snímek>.jpg` s rámečky (titulek zeleně, úkol oranžově, menu modře, oblast čtení šedě).
 *
 * Čtení je jiné než na Androidu (RapidOCR místo ML Kit, celý snímek místo výřezu) – co rozbor ukáže, je poloha
 * textu na skutečných snímcích a co s ní udělá logika z `core`; rozdíly proti aplikaci jsou v řádcích „aplikace“.
 */
object Rozbor {
    private val SNIMEK = Regex("snimek-[0-9-]+\\.jpg")
    /** Řádek dávky v `index.txt`: `#0001 21:30:05.123 +1.2s snimek-0001.jpg 1280×720 zmena`. */
    private val ZAZNAM_INDEXU = Regex("^#\\d+ (\\d\\d):(\\d\\d):(\\d\\d)\\S* \\S+ (snimek-\\S+\\.jpg)")
    /** Řádek starších záznamů (`zaznam.txt`): `15:35:07 SHOW|…` nebo `15:35:07.123 …`. */
    private val ZAZNAM_CASU = Regex("^(\\d\\d):(\\d\\d):(\\d\\d)(?:\\.\\d+)?\\s+(.*)$")
    /** Čas ze jména snímku `snimek-154110.jpg` / `snimek-154110-123.jpg` (starší záznamy). */
    private val CAS_SNIMKU = Regex("snimek-(\\d\\d)(\\d\\d)(\\d\\d)(?:-\\d+)?\\.jpg")

    /** OCR uloženého snímku přes `cteni.py --rozbor`. */
    private class Ocr(python: String, skript: File) {
        private val proces: Process
        private val ven: BufferedWriter
        private val vstup: BufferedReader

        init {
            val pb = ProcessBuilder(python, skript.absolutePath, "--rozbor")
            pb.redirectError(ProcessBuilder.Redirect.INHERIT)
            pb.environment()["PYTHONIOENCODING"] = "utf-8"
            proces = pb.start()
            ven = proces.outputStream.bufferedWriter(Charsets.UTF_8)
            vstup = proces.inputStream.bufferedReader(Charsets.UTF_8)
            while (true) {
                val l = vstup.readLine() ?: error("cteni.py --rozbor se nespustil (chybí knihovny Pythonu? spusť pc\\priprava.ps1)")
                if (l == "PRIPRAVENO") break
            }
        }

        class Snimek(val w: Int, val h: Int, val ms: Long, val radky: List<MenuLine>)

        fun cti(f: File): Snimek? {
            ven.write("SOUBOR\t" + f.absolutePath + "\n"); ven.flush()
            val hlava = (vstup.readLine() ?: error("cteni.py skončil")).split('\t')
            if (hlava[0] != "OK") { System.err.println("  ${f.name}: ${hlava.getOrElse(1) { "?" }}"); return null }
            val n = hlava[1].toInt()
            val radky = (0 until n).mapNotNull {
                val p = (vstup.readLine() ?: error("cteni.py skončil")).split('\t', limit = 7)
                if (p.size < 7) return@mapNotNull null
                val text = p[6]
                val slovaText = text.split(' ').filter { it.isNotBlank() }
                val slovaBox = p[5].split(';').filter { it.isNotBlank() }.mapNotNull { s ->
                    s.split(',').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 5 }
                }
                val slova = if (slovaBox.size == slovaText.size)
                    slovaText.zip(slovaBox) { t, b -> MenuWord(t, b[0], b[1], b[2], b[3], b[4]) } else emptyList()
                MenuLine(text, p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt(), slova)
            }
            return Snimek(hlava[2].toInt(), hlava[3].toInt(), hlava[4].toLong(), radky)
        }

        fun stop() { runCatching { ven.close() }; runCatching { proces.destroy() } }
    }

    /** Co ke snímku zapsala aplikace (index.txt dávky, nebo události ze `zaznam.txt` mezi snímky). */
    private class Aplikace(val radky: List<String>) {
        /** Poslední verdikt čtení: text titulku, "" = nic nevzato, null = nevíme. */
        val titulek: String? = radky.lastOrNull { it.startsWith("CTENI:") }?.let { r ->
            val v = r.substringAfterLast(" → ", "")
            if (v.startsWith("titulek: ")) v.removePrefix("titulek: ") else ""
        } ?: radky.lastOrNull { it.contains("SHOW|") }?.substringAfter("SHOW|")?.substringBefore('|')
        val ukol: String? = radky.lastOrNull { it.contains("OBJ|show|") || it.contains("OBJ|hide") }?.let {
            if (it.contains("OBJ|hide")) "" else it.substringAfter("OBJ|show|")
        }
        val menu: String? = radky.lastOrNull { it.contains("MENU|") }?.substringAfter("MENU|")?.let {
            if (it.startsWith("off")) "" else it
        }
    }

    private class Vysledek(
        val soubor: String, val w: Int, val h: Int, val radky: List<MenuLine>,
        val picked: Picked, val volny: OcrLine?, val menu: MenuView?, val zx: Int, val zy: Int,
        val app: Aplikace?, val neshody: List<String>,
        val roh: CornerView? = null,
    )

    fun spust(args: List<String>) {
        fun arg(name: String, default: String = ""): String {
            val i = args.indexOf(name)
            return if (i >= 0 && i + 1 < args.size) args[i + 1] else default
        }
        val koren = File(arg("--rozbor")).absoluteFile
        if (!koren.isDirectory) { println("Složka $koren neexistuje."); return }
        val profil = GameProfiles.of(arg("--hra", "gta6"))
        val zona = arg("--zona-ukolu", UKOL_ZONA.toString()).toFloat()
        val obrazky = "--obrazky" in args
        val davky = koren.walkTopDown().onEnter { it.name != "rozbor" }
            .filter { d -> d.isDirectory && (d.list() ?: emptyArray()).any { SNIMEK.matches(it) } }.sortedBy { it.path }.toList()
        if (davky.isEmpty()) { println("Ve složce $koren nejsou snímky snimek-*.jpg."); return }
        println("Rozbor: ${davky.size} složek, hra ${profil.id}, hranice úkolu ${pct(zona)}")
        val ocr = Ocr(arg("--python", Nastroje.python.path), File(arg("--pomocnik", Nastroje.pomocnik.path)))
        val vse = ArrayList<Vysledek>()
        try {
            for (d in davky) {
                val v = davka(d, ocr, profil, zona, obrazky)
                vse += v
                println("  ${d.name}: ${v.size} snímků → ${File(d, "rozbor.txt")}")
            }
        } finally { ocr.stop() }
        if (davky.size > 1) {
            val s = souhrn(vse, zona)
            File(koren, "rozbor-souhrn.txt").writeText(s, Charsets.UTF_8)
            println("\n" + s)
            println("Souhrn: ${File(koren, "rozbor-souhrn.txt")}")
        } else println("\n" + souhrn(vse, zona))
    }

    private fun davka(d: File, ocr: Ocr, profil: cz.recaption.core.GameProfile, zona: Float, obrazky: Boolean): List<Vysledek> {
        val snimky = (d.listFiles() ?: emptyArray()).filter { SNIMEK.matches(it.name) }.sortedBy { it.name }
        val app = aplikace(d, snimky.map { it.name })
        val ukoly = ObjectiveTracker()
        val read = ScreenLayout.DEFAULT_READ
        val out = ArrayList<Vysledek>()
        val txt = StringBuilder()
        val csv = StringBuilder("soubor;sirka;vyska;radku;titulek;tit_nahore;tit_dole;tit_vlevo;tit_vpravo;ukol;ukol_nahore;ukol_dole;" +
            "ukol_bez_hranice;ukol_bez_hranice_nahore;menu;menu_polozky;menu_vybrana;menu_nadpis;app_titulek;app_ukol;app_menu;neshoda\n")
        txt.append("Rozbor ${d.name} · hra ${profil.id} · oblast čtení ${pct(read.left)}–${pct(read.right)} × ${pct(read.top)}–${pct(read.bottom)}" +
            " · hranice úkolu ${pct(zona)} · ${snimky.size} snímků\n")
        txt.append("Poloha = vlevo,nahoře–vpravo,dole v % šířky a výšky celého snímku.\n\n")
        val slozkaObrazku = if (obrazky) File(d, "rozbor").apply { mkdirs() } else null
        for ((i, f) in snimky.withIndex()) {
            if (i > 0 && i % 50 == 0) println("    ${d.name}: $i/${snimky.size}")
            val s = ocr.cti(f) ?: continue
            val w = s.w; val h = s.h
            val zx = (w * read.left).toInt(); val zy = (h * read.top).toInt()
            val zr = (w * read.right).toInt(); val zb = (h * read.bottom).toInt()
            // řádky v oblasti čtení (jako výřez v aplikaci: co do ní aspoň napůl zasahuje, oříznuté), v px oblasti
            val vOblasti = s.radky.mapNotNull { l ->
                val prekryv = min(l.bottom, zb) - max(l.top, zy)
                if (prekryv * 2 < (l.bottom - l.top) || min(l.right, zr) <= max(l.left, zx)) return@mapNotNull null
                OcrLine(l.text, max(l.left, zx) - zx, max(l.top, zy) - zy, min(l.right, zr) - zx, min(l.bottom, zb) - zy,
                    l.words.map { OcrLine(it.text, it.left - zx, it.top - zy, it.right - zx, it.bottom - zy) })
            }
            val bloky = toBlocks(vOblasti)
            val foreign = TextLogic.looksForeign(vOblasti.joinToString("\n") { it.text })
            fun assemble(minTop: Int) = if (foreign) Picked("", 0) else TextLogic.assemble(
                bloky,
                minLineHeight = (h * 0.013f).toInt(),
                subtitlesOnly = true,
                screenCenterX = w / 2 - zx,
                centerTolerance = (w * 0.14f).toInt(),
                maxLineHeight = (h * 0.085f).toInt(),
                screen = profil.screen,
                objectives = profil.prompts,
                objectiveTop = if (profil.prompts && ukoly.top >= 0f) (ukoly.top * h).toInt() - zy else -1,
                objectiveMinTop = minTop,
            )
            val picked = assemble(if (profil.prompts) (h * zona).toInt() - zy else -1)
            // tentýž výběr bez hranice úkolu: kde by úkol ležel, kdyby hranice nebyla (podklad pro její nastavení)
            val volny = if (profil.prompts) assemble(-1).objective else null
            picked.objective.let { o -> ukoly.update(o?.text, o?.let { (zy + it.top).toFloat() / h } ?: -1f) }
            val menu = profil.menuSpec?.let { spec -> GameMenu.read(s.radky.filter { it.top < h * 0.6f }, w, h, spec) }
            // menu vpravo dole: jas z rozboru je průměr řádku (ne 90. percentil písma) → bez šedé
            val roh = profil.cornerSpec?.let { spec ->
                CornerPrompts.read(s.radky.map { l -> l.copy(bright = -1, words = l.words.map { it.copy(bright = -1) }) }, w, h, spec)
            }
            val a = app[f.name]
            val neshody = ArrayList<String>()
            if (a != null) {
                a.titulek?.let { t ->
                    val r = picked.text.replace('\n', ' ')
                    if (t.isEmpty() != r.isEmpty() || (t.isNotEmpty() && !TextLogic.similar(TextLogic.simplify(t), TextLogic.simplify(r), 0.7f)))
                        neshody += "titulek"
                }
                a.ukol?.let { t ->
                    val r = picked.objective?.text ?: ""
                    if (t.isEmpty() != r.isEmpty() || (t.isNotEmpty() && !TextLogic.similar(TextLogic.simplify(t), TextLogic.simplify(r), 0.7f)))
                        neshody += "úkol"
                }
                a.menu?.let { m -> if (m.isEmpty() != (menu == null) || (menu != null && !m.startsWith(menu.kind))) neshody += "menu" }
            }
            val v = Vysledek(f.name, w, h, s.radky, picked, volny, menu, zx, zy, a, neshody, roh)
            out += v
            txt.append(popis(v, s.ms))
            csv.append(radekCsv(v))
            slozkaObrazku?.let { runCatching { nakresli(f, File(it, f.name), v, read, zona) } }
        }
        txt.append("\n").append(souhrn(out, zona))
        File(d, "rozbor.txt").writeText(txt.toString(), Charsets.UTF_8)
        File(d, "rozbor.csv").writeText(csv.toString(), Charsets.UTF_8)
        return out
    }

    /** Řádky aplikace ke každému snímku: z `index.txt` dávky, jinak z `zaznam.txt` podle času (starší záznamy). */
    private fun aplikace(d: File, snimky: List<String>): Map<String, Aplikace> {
        val index = File(d, "index.txt")
        if (index.isFile) {
            val mapa = LinkedHashMap<String, MutableList<String>>()
            var cur: MutableList<String>? = null
            index.forEachLine(Charsets.UTF_8) { l ->
                val m = ZAZNAM_INDEXU.find(l)
                when {
                    m != null -> cur = mapa.getOrPut(m.groupValues[4]) { ArrayList() }
                    l.startsWith("  ") -> cur?.add(l.trim())
                }
            }
            return mapa.mapValues { Aplikace(it.value) }
        }
        val zaznam = File(d, "zaznam.txt").takeIf { it.isFile } ?: return emptyMap()
        fun sek(h: String, m: String, s: String) = h.toInt() * 3600 + m.toInt() * 60 + s.toInt()
        val casy = snimky.mapNotNull { n -> CAS_SNIMKU.matchEntire(n)?.groupValues?.let { n to sek(it[1], it[2], it[3]) } }
        if (casy.isEmpty()) return emptyMap()
        val od = casy.first().second - 5; val doCasu = casy.last().second
        val mapa = LinkedHashMap<String, MutableList<String>>()
        zaznam.forEachLine(Charsets.UTF_8) { l ->
            val m = ZAZNAM_CASU.matchEntire(l) ?: return@forEachLine
            val t = sek(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            if (t < od || t > doCasu) return@forEachLine
            // událost patří ke snímku, který přišel po ní (jako v index.txt: „co se stalo od minulého snímku“)
            val k = casy.firstOrNull { it.second >= t } ?: return@forEachLine
            mapa.getOrPut(k.first) { ArrayList() }.add(m.groupValues[4].trim())
        }
        return mapa.mapValues { Aplikace(it.value) }
    }

    private fun pct(f: Float) = String.format(Locale.ROOT, "%.1f %%", f * 100)
    private fun p(v: Int, z: Int) = String.format(Locale.ROOT, "%.1f", v * 100f / z)
    private fun box(l: Int, t: Int, r: Int, b: Int, w: Int, h: Int) = "${p(l, w)},${p(t, h)}–${p(r, w)},${p(b, h)}"

    private fun popis(v: Vysledek, ms: Long): String {
        val sb = StringBuilder()
        sb.append("== ${v.soubor} ${v.w}×${v.h} · ${v.radky.size} řádků · OCR $ms ms")
        if (v.neshody.isNotEmpty()) sb.append(" · NESHODA: ").append(v.neshody.joinToString(", "))
        sb.append('\n')
        for (l in v.radky.sortedBy { it.top }) sb.append("   [${box(l.left, l.top, l.right, l.bottom, v.w, v.h)}] jas ${l.bright}  ${l.text}\n")
        val pk = v.picked
        if (pk.text.isNotEmpty()) sb.append("  titulek [${box(v.zx + pk.left, v.zy + pk.top, v.zx + pk.right, v.zy + pk.bottom, v.w, v.h)}] " +
            "výška řádku ${p(pk.lineHeight, v.h)} %: ${pk.text.replace("\n", " / ")}\n")
        pk.objective?.let { o -> sb.append("  úkol [${box(v.zx + o.left, v.zy + o.top, v.zx + o.right, v.zy + o.bottom, v.w, v.h)}]: ${o.text}\n") }
        v.volny?.takeIf { pk.objective == null || it.top != pk.objective.top }?.let { o ->
            sb.append("  úkol bez hranice [${box(v.zx + o.left, v.zy + o.top, v.zx + o.right, v.zy + o.bottom, v.w, v.h)}]: ${o.text}\n")
        }
        v.menu?.let { m ->
            sb.append("  menu ${m.kind}: ${m.items.mapIndexed { i, s -> if (i == m.selected) "»$s" else s }.joinToString(", ")} · vybraná ${m.selected}")
            m.box?.let { sb.append(" · místo ${pct(it.first)},${pct(it.second)}") }
            if (m.step > 0f) sb.append(" · rozestup ${pct(m.step)}")
            sb.append('\n')
            if (m.title != null || m.description != null) sb.append("    nadpis: ${m.title ?: "–"} · popis: ${m.description ?: "–"}\n")
            m.descBox?.let { b -> sb.append("    descBox ${b.joinToString(",") { String.format(Locale.ROOT, "%.3f", it) }}\n") }
        }
        v.roh?.let { r ->
            sb.append("  vpravo dole: ${r.items.joinToString(" / ") { it.text + (if (it.suffix.isEmpty()) "" else " " + it.suffix) + (if (it.known) "" else "?") }}" +
                " · nahoře ${pct(r.top)} · vpravo ${pct(r.right)} · rozestup ${pct(r.step)}\n")
        }
        v.app?.takeIf { it.titulek != null || it.ukol != null || it.menu != null }?.let { a ->
            sb.append("  aplikace: titulek ${a.titulek?.let { if (it.isEmpty()) "–" else it } ?: "?"} · úkol ${a.ukol?.let { if (it.isEmpty()) "–" else it } ?: "?"}" +
                " · menu ${a.menu?.let { if (it.isEmpty()) "–" else it } ?: "?"}\n")
            a.radky.filter { r -> listOf("SHOW|", "OBJ|", "MENU|", "READ|", "CTENI:").any { r.contains(it) } }.takeLast(8)
                .forEach { sb.append("    | ").append(it.take(220)).append('\n') }
        }
        return sb.append('\n').toString()
    }

    private fun csvPole(s: String) = "\"" + s.replace("\"", "\"\"").replace("\n", " ") + "\""

    private fun radekCsv(v: Vysledek): String {
        val pk = v.picked
        val o = pk.objective
        val m = v.menu
        return listOf(
            v.soubor, "${v.w}", "${v.h}", "${v.radky.size}", csvPole(pk.text),
            if (pk.text.isEmpty()) "" else p(v.zy + pk.top, v.h), if (pk.text.isEmpty()) "" else p(v.zy + pk.bottom, v.h),
            if (pk.text.isEmpty()) "" else p(v.zx + pk.left, v.w), if (pk.text.isEmpty()) "" else p(v.zx + pk.right, v.w),
            csvPole(o?.text ?: ""), o?.let { p(v.zy + it.top, v.h) } ?: "", o?.let { p(v.zy + it.bottom, v.h) } ?: "",
            csvPole(v.volny?.text ?: ""), v.volny?.let { p(v.zy + it.top, v.h) } ?: "",
            m?.kind ?: "", csvPole(m?.items?.joinToString("|") ?: ""), m?.selected?.toString() ?: "", csvPole(m?.title ?: ""),
            csvPole(v.app?.titulek ?: ""), csvPole(v.app?.ukol ?: ""), csvPole(v.app?.menu ?: ""), v.neshody.joinToString("|"),
        ).joinToString(";") + "\n"
    }

    private fun rozlozeni(nazev: String, hodnoty: List<Float>): String {
        if (hodnoty.isEmpty()) return "$nazev: žádné\n"
        val s = hodnoty.sorted()
        fun q(x: Double) = s[min(s.size - 1, (x * (s.size - 1) + 0.5).toInt())]
        return "$nazev (${s.size}×): min ${pct(s.first())} · medián ${pct(q(0.5))} · p95 ${pct(q(0.95))} · max ${pct(s.last())}\n"
    }

    private fun souhrn(vse: List<Vysledek>, zona: Float): String {
        val sb = StringBuilder("=== Souhrn (${vse.size} snímků) ===\n")
        val tit = vse.filter { it.picked.text.isNotEmpty() }
        val dolni = tit.map { (it.zy + it.picked.bottom).toFloat() / it.h }
        sb.append(rozlozeni("Dolní hrana titulku", dolni))
        sb.append(rozlozeni("Horní hrana titulku", tit.map { (it.zy + it.picked.top).toFloat() / it.h }))
        sb.append(rozlozeni("Výška řádku titulku", tit.map { it.picked.lineHeight.toFloat() / it.h }))
        val ukoly = vse.mapNotNull { v -> v.picked.objective?.let { (v.zy + it.top).toFloat() / v.h } }
        sb.append(rozlozeni("Horní hrana úkolu (s hranicí ${pct(zona)})", ukoly))
        val volne = vse.mapNotNull { v -> v.volny?.let { (v.zy + it.top).toFloat() / v.h } }
        sb.append(rozlozeni("Horní hrana úkolu bez hranice", volne))
        val mezery = vse.mapNotNull { v ->
            val o = v.picked.objective ?: return@mapNotNull null
            if (v.picked.text.isEmpty()) null else (o.top - v.picked.bottom).toFloat() / v.h
        }
        sb.append(rozlozeni("Mezera titulek → úkol", mezery))
        val vyrazene = vse.count { v -> v.volny != null && v.picked.objective == null }
        sb.append("Úkol bez hranice nalezen, s hranicí ne: $vyrazene snímků\n")
        val menu = vse.mapNotNull { it.menu }
        sb.append("Menu: ${menu.size} snímků (" + menu.groupingBy { it.kind }.eachCount().entries.joinToString { "${it.key} ${it.value}" } + ")" +
            ", vybraná nepoznaná ${menu.count { it.selected < 0 }}×\n")
        // jen snímky, ke kterým aplikace zapsala titulek, úkol nebo menu (jiné události nestačí k porovnání)
        val sApp = vse.filter { v -> v.app?.let { it.titulek != null || it.ukol != null || it.menu != null } == true }
        val neshody = vse.filter { it.neshody.isNotEmpty() }
        sb.append("Se záznamem aplikace: ${sApp.size} snímků, neshoda ${neshody.size}×" +
            (if (neshody.isEmpty()) "" else " (" + neshody.flatMap { it.neshody }.groupingBy { it }.eachCount().entries.joinToString { "${it.key} ${it.value}" } + ")") + "\n")
        neshody.take(30).forEach { sb.append("  ${it.soubor}: ${it.neshody.joinToString(", ")}\n") }
        // návrh hranice úkolu: nad ní titulky nekončí, pod ní úkoly začínají
        val maxTit = dolni.maxOrNull()
        val minUkol = volne.minOrNull()
        sb.append("Návrh hranice úkolu: ")
        when {
            maxTit == null && minUkol == null -> sb.append("nelze (žádné titulky ani úkoly)\n")
            minUkol == null -> sb.append("aspoň ${pct(maxTit!! + 0.005f)} (nejnižší titulek ${pct(maxTit)} + 0,5 %; úkol na snímcích není)\n")
            maxTit == null -> sb.append("nejvýš ${pct(minUkol)} (nejvyšší úkol; titulky na snímcích nejsou)\n")
            maxTit < minUkol -> sb.append("${pct((maxTit + minUkol) / 2)} (titulky končí nejníž ${pct(maxTit)}, úkoly začínají nejvýš ${pct(minUkol)})\n")
            else -> {
                val p95 = dolni.sorted()[min(dolni.size - 1, (0.95 * (dolni.size - 1) + 0.5).toInt())]
                val prekryv = volne.count { it <= maxTit }
                sb.append("${pct(p95 + 0.005f)} (p95 titulku + 0,5 %) – titulky a úkoly se překrývají: nejnižší titulek ${pct(maxTit)}, " +
                    "nejvyšší úkol ${pct(minUkol)}, úkolů nad nejnižším titulkem $prekryv; projít snímky ručně\n")
            }
        }
        sb.append("Současná hranice: ${pct(zona)} (TranslatorService.OBJECTIVE_ZONE_TOP)\n")
        return sb.toString()
    }

    private fun nakresli(src: File, dst: File, v: Vysledek, read: cz.recaption.core.Zone, zona: Float) {
        val img = ImageIO.read(src) ?: return
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val w = v.w; val h = v.h
        g.stroke = BasicStroke(1f)
        g.color = Color(160, 160, 160)
        g.drawRect((read.left * w).toInt(), (read.top * h).toInt(), (read.width * w).toInt(), (read.height * h).toInt())
        g.color = Color(255, 160, 40, 140)
        g.drawLine(0, (zona * h).toInt(), w, (zona * h).toInt())
        g.color = Color(255, 255, 255, 120)
        v.radky.forEach { g.drawRect(it.left, it.top, it.right - it.left, it.bottom - it.top) }
        g.stroke = BasicStroke(2f)
        val pk = v.picked
        if (pk.text.isNotEmpty()) {
            g.color = Color(60, 220, 90)
            g.drawRect(v.zx + pk.left, v.zy + pk.top, pk.right - pk.left, pk.bottom - pk.top)
        }
        (pk.objective ?: v.volny)?.let { o ->
            g.color = if (pk.objective != null) Color(255, 150, 30) else Color(255, 150, 30, 120)
            g.drawRect(v.zx + o.left, v.zy + o.top, o.right - o.left, o.bottom - o.top)
        }
        v.menu?.let { m ->
            g.color = Color(70, 140, 255)
            m.rects.forEach { r -> g.drawRect((r[0] * w).toInt(), (r[1] * h).toInt(), ((r[2] - r[0]) * w).toInt(), ((r[3] - r[1]) * h).toInt()) }
            m.descBox?.let { b ->
                g.stroke = BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(6f, 4f), 0f)
                g.drawRect((b[0] * w).toInt(), (b[1] * h).toInt(), ((b[2] - b[0]) * w).toInt(), ((b[3] - b[1]) * h).toInt())
            }
            g.font = Font(Font.SANS_SERIF, Font.BOLD, max(12, h / 40))
            g.drawString("menu ${m.kind} · vybraná ${m.selected}", 8, h - 8)
        }
        g.dispose()
        ImageIO.write(img, "jpg", dst)
    }
}
