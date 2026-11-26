package cz.recaption.pc

import cz.recaption.core.Deepl
import cz.recaption.core.BuiltinPhrases
import cz.recaption.core.GameMenu
import cz.recaption.core.GameProfiles
import cz.recaption.core.Glossary
import cz.recaption.core.MatchSize
import cz.recaption.core.MenuLine
import cz.recaption.core.MenuTracker
import cz.recaption.core.NameColor
import cz.recaption.core.ObjectiveTracker
import cz.recaption.core.OfflinePrep
import cz.recaption.core.TranslationCache
import cz.recaption.core.TvLook
import cz.recaption.core.TvSettings
import cz.recaption.core.DemoGame
import cz.recaption.core.Lang
import cz.recaption.core.Natural
import cz.recaption.core.OcrBlock
import cz.recaption.core.OcrLine
import cz.recaption.core.Picked
import cz.recaption.core.QuickWords
import cz.recaption.core.Segment
import cz.recaption.core.SpeakerColors
import cz.recaption.core.SubtitleTracker
import cz.recaption.core.TextLogic
import cz.recaption.core.TvSender
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Čtení a překlad titulků – totéž co překlad v aplikaci pro Android TV, jen na počítači.
 *
 * Výběr titulků, rozhodování, kdy větu ukázat a schovat, i posílání do televize jsou tytéž soubory
 * ze složky `core` – nic se tu neopisuje. Liší se jen to, co Android dělá svými knihovnami: obraz,
 * čtení textu a překlad obstarává pomocník `cteni.py`.
 *
 * Spouští ji okno aplikace ([main] v `Aplikace.kt`) i příkazová řádka (`Main.kt`).
 */

private const val STALE_MS = 15_000L      // text, který stojí beze změny déle, je nápis nebo menu
private const val HOLD_MS = 1_000L        // překlad zůstane chvíli po zmizení angličtiny, ať se dá dočíst
private const val INTERVAL_MS = 120L
private const val TELEFON_TITULKY_MS = 3_000L  // titulky při otevřeném telefonu ve hře (procesor patří telefonu)
/** Okno, které neexistuje: čtečka pro TV běží (TV připojená, modely načtené) a čeká na chiaki-ng. */
const val CEKA_NA_OKNO = "pid:0"
/** Úkol pod titulky (GTA) jen úplně dole – jako `TranslatorService.OBJECTIVE_ZONE_TOP` (`TextLogic.OBJECTIVE_ZONE_TOP`). */
internal const val UKOL_ZONA = cz.recaption.core.TextLogic.OBJECTIVE_ZONE_TOP

/** Kde leží nástroje z přípravy (`priprava.ps1`) a soubory čtečky. */
object Nastroje {
    val slozka =
        if (Os.mac) File(System.getProperty("user.home"), "Library/Application Support/TitulkyPS5")
        else File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "TitulkyPS5")
    /** Složka `pc` v repozitáři – nastavuje ji spouštěcí skript; v balíčku ke stažení složka `app` vedle ReCaption.exe. */
    val pc = File(System.getProperty("recaption.pc", "."))
    /** Python s knihovnami přibalený v balíčku ke stažení (`ReCaption-PC.zip`, `.github/workflows/pc.yml`). */
    val pribaleny = File(pc, if (Os.windows) "python/python.exe" else "python/bin/python3")
    /** Přibalený Python, jinak prostředí z přípravy (`priprava.ps1`, potřebuje nainstalovaný Python). */
    val python: File get() = if (pribaleny.exists()) pribaleny else File(slozka, if (Os.windows) "venv/Scripts/python.exe" else "venv/bin/python3")
    val pomocnik = File(pc, "cteni.py")
    val priprava = File(pc, if (Os.windows) "priprava.ps1" else "priprava.sh")
    val chyby = File(slozka, "cteni-chyby.txt")

    /** Záznamy pro ladění: `zaznamy\pc-<čas>\` v repozitáři (v .gitignore). */
    fun zaznamy(): File =
        // Mac: složka `pc` je uvnitř aplikace (ReCaption.app), kam se zapisovat nemá – záznamy jdou k nástrojům
        if (Os.mac) File(slozka, "zaznamy") else File(pc.absoluteFile.parentFile ?: pc.absoluteFile, "zaznamy")

    fun model(jazyk: String): File? =
        File(slozka, "modely/en_$jazyk").listFiles()?.firstOrNull { it.isDirectory }

    /** Je všechno připravené pro tento jazyk? */
    fun pripraveno(jazyk: String) = python.exists() && model(jazyk) != null && pomocnik.exists()
}

/** Spojení s pomocníkem `cteni.py` (viz popis řádků tam). */
private class Helper(python: String, script: File, model: String, lang: String, window: String, errors: File?, record: File?,
                     vedlejsi: Boolean = false) : PhoneHelper {
    sealed class Read {
        object Same : Read()
        data class Error(val text: String) : Read()
        data class Frame(val lines: List<OcrLine>, val w: Int, val h: Int, val x0: Int, val y0: Int, val ms: Long) : Read()
    }

    private val process: Process
    private val out: BufferedWriter
    private val input: BufferedReader

    init {
        val cmd = mutableListOf(python, script.absolutePath, "--model", model, "--jazyk", lang)
        if (window.isNotEmpty()) { cmd += "--okno"; cmd += window }
        if (record != null) { cmd += "--zaznam"; cmd += record.absolutePath }
        if (vedlejsi) cmd += "--vedlejsi"
        val pb = ProcessBuilder(cmd)
        // Mac: Python je uvnitř aplikace – své pomocné soubory (.pyc) nesmí zapisovat do ní (porušil by se její podpis)
        if (Os.mac) pb.environment()["PYTHONPYCACHEPREFIX"] = File(Nastroje.slozka, "pyc").path
        if (errors != null) {
            errors.parentFile?.mkdirs()
            pb.redirectError(ProcessBuilder.Redirect.to(errors))
        } else pb.redirectError(ProcessBuilder.Redirect.INHERIT)
        process = pb.start()
        out = process.outputStream.bufferedWriter(Charsets.UTF_8)
        input = process.inputStream.bufferedReader(Charsets.UTF_8)
        // první řádek přijde, až jsou načtené modely
        while (true) {
            val l = input.readLine() ?: error("pomocník cteni.py se nespustil (chybí knihovny Pythonu nebo model?)")
            if (l == "PRIPRAVENO") break
        }
    }

    private fun ask(vararg parts: String): String {
        out.write(parts.joinToString("\t")); out.write("\n"); out.flush()
        return input.readLine() ?: error("pomocník cteni.py skončil")
    }

    fun read(force: Boolean, demoText: String?): Read {
        val head = (if (demoText == null) ask("CTI", if (force) "1" else "0")
            else ask("CTI", if (force) "1" else "0", demoText.replace("\n", "\\n"))).split('\t')
        return when (head[0]) {
            "STEJNE" -> Read.Same
            "OK" -> {
                val n = head[1].toInt()
                val lines = (0 until n).mapNotNull {
                    val p = (input.readLine() ?: error("pomocník cteni.py skončil")).split('\t', limit = 5)
                    if (p.size < 5) null else OcrLine(p[4], p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt())
                }
                Read.Frame(lines, head[2].toInt(), head[3].toInt(), head[4].toInt(), head[5].toInt(), head[6].toLong())
            }
            else -> Read.Error(head.getOrElse(1) { "?" })
        }
    }

    /** Jiné okno hry za běhu (čtečka pro TV: nové okno chiaki-ng po každém připojení k PS5). */
    fun window(name: String) { ask("OKNO", name.replace('\t', ' ')) }

    fun translate(text: String): String? {
        val r = ask("PRELOZ", text.replace('\t', ' ').replace('\n', ' ')).split('\t', limit = 2)
        return if (r[0] == "OK") r.getOrElse(1) { "" }.takeIf { it.isNotBlank() } else null
    }

    /** Barva písma v obdélníku posledního snímku (celý obraz); null = málo bodů. */
    fun barva(l: Int, t: Int, r: Int, b: Int): Int? {
        val p = ask("BARVA", "$l", "$t", "$r", "$b").split('\t')
        if (p[0] != "OK" || p.size < 4) return null
        return (0xFF shl 24) or (p[1].toInt() shl 16) or (p[2].toInt() shl 8) or p[3].toInt()
    }

    class Menu(val lines: List<MenuLine>, val w: Int, val h: Int, val ms: Long)

    /** Horní část posledního snímku pro menu hry ([cz.recaption.core.GameMenu]). */
    fun menu(): Menu? {
        val head = ask("MENU").split('\t')
        if (head[0] != "OK") return null
        val n = head[1].toInt()
        val lines = (0 until n).mapNotNull {
            val p = (input.readLine() ?: error("pomocník cteni.py skončil")).split('\t', limit = 6)
            if (p.size < 6) null else MenuLine(p[5], p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt())
        }
        return Menu(lines, head[2].toInt(), head[3].toInt(), head[4].toLong())
    }

    private fun zlomky(b: FloatArray) = b.take(4).joinToString("\t") { String.format(java.util.Locale.ROOT, "%.5f", it) }

    /** Jen sejmout obraz okna (druhý pomocník pro telefon); false = obraz není. */
    fun snimek(): Boolean = ask("SNIMEK").startsWith("OK")

    override fun rozmer(): IntArray? {
        val p = ask("ROZMER").split('\t')
        if (p[0] != "OK" || p.size < 5) return null
        return intArrayOf(p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt())
    }

    override fun pixely(box: FloatArray): IntArray? {
        val p = ask("PIXELY", zlomky(box)).split('\t')
        if (p[0] != "OK" || p.size < 2 || p[1].isEmpty()) return null
        return p[1].split(',').mapNotNull { it.toIntOrNull() }.map { it or (0xFF shl 24) }.toIntArray()
    }

    override fun mrizka(box: FloatArray, cols: Int, rows: Int): IntArray? {
        val p = ask("MRIZKA", zlomky(box), "$cols", "$rows").split('\t')
        if (p[0] != "OK" || p.size < 2) return null
        val v = p[1].split(',').mapNotNull { it.toIntOrNull() }
        return if (v.size == cols * rows) v.toIntArray() else null
    }

    override fun telefon(box: FloatArray, zvetseni: Float?): PhoneCrop? {
        val head = (if (zvetseni == null) ask("TELEFON", zlomky(box))
            else ask("TELEFON", zlomky(box), String.format(java.util.Locale.ROOT, "%.2f", zvetseni))).split('\t')
        if (head[0] != "OK" || head.size < 5) return null
        val n = head[1].toInt()
        val lines = (0 until n).mapNotNull {
            val p = (input.readLine() ?: error("pomocník cteni.py skončil")).split('\t', limit = 7)
            if (p.size < 7) null else {
                val vz = if (p[5].isEmpty()) IntArray(0) else p[5].split(',').mapNotNull { it.toIntOrNull() }.toIntArray()
                MenuLine(p[6], p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt(), -1, angle = p[4].toFloatOrNull() ?: 0f) to vz
            }
        }
        val w = head[2].toInt(); val h = head[3].toInt()
        return if (w <= 0 || h <= 0) null else PhoneCrop(lines, w, h, head[4].toLong())
    }

    fun stop() { try { process.destroy() } catch (_: Throwable) {} }
}

/**
 * Řádky, které k sobě patří (dvouřádkový titulek), spojí do bloků: leží těsně pod sebou
 * a vodorovně se překrývají. Android dostává bloky hotové od své čtečky textu.
 */
internal fun toBlocks(lines: List<OcrLine>): List<OcrBlock> {
    val blocks = ArrayList<MutableList<OcrLine>>()
    for (l in lines.sortedBy { it.top }) {
        val home = blocks.firstOrNull { b ->
            val last = b.last()
            val gap = l.top - last.bottom
            val overlap = min(l.right, last.right) - max(l.left, last.left)
            gap < 0.8f * max(l.height, last.height) && gap > -0.5f * l.height && overlap > 0
        }
        if (home != null) home.add(l) else blocks.add(mutableListOf(l))
    }
    return blocks.map { OcrBlock(it) }
}

/** Co se s čtením právě děje – pro stránku Domů. */
data class Stav(
    val faze: Faze,
    /** Doba posledního čtení textu v ms (0 = zatím nic). */
    val ms: Long = 0,
    val televize: Int = 0,
    val original: String = "",
    val preklad: String = "",
    val chyba: String = "",
    /** Jak dlouho odpovídal DeepL naposledy (0 = zatím ne). */
    val deeplMs: Long = 0,
    /** Proč DeepL naposledy nepomohl (null = v pořádku). */
    val deeplChyba: Deepl.Problem? = null,
) {
    enum class Faze { STOJI, NACITANI, BEZI, BEZ_OKNA, CHYBA }
}

class Ctecka(
    private val jazyk: String,
    /** Část titulku okna s hrou; prázdné = ukázka bez PlayStationu. */
    private val okno: String,
    /** pc | tv | oboje */
    private val vystup: String,
    private val pruh: Pruh?,
    /** Pruh s přeloženým úkolem (GTA) přes anglický úkol; null = úkol se na PC neukazuje. */
    private val pruhUkolu: Pruh? = null,
    private val log: (String) -> Unit,
    private val zmena: (Stav) -> Unit = {},
    private val port: Int = TvSender.PORT,
    private val python: String = Nastroje.python.path,
    private val pomocnik: File = Nastroje.pomocnik,
    private val chyby: File? = null,
    /** Překládat přes DeepL (klíč v [DeeplPc]); offline zůstává jako záloha. */
    private val deepl: Boolean = false,
    /** DeepL jen pro telefon ve hře, titulky a ostatní offline (šetří limit znaků). */
    private val deeplJenTelefon: Boolean = false,
    /**
     * Záznam pro ladění: každé spuštění čtení dostane vlastní složku `pc-<čas>` s `zaznam.txt`
     * (co se přečetlo a kde, co se vzalo, překlady) a barevnými snímky celé hry (`cteni.py`).
     */
    private val zaznamy: File? = null,
    /** Profil hry (`core/GameProfiles.kt`): jména, rod postav, pojmy, co přeskočit, barvy, pokyny, menu. */
    private val hra: String = "all",
    /** Texty hry navíc k titulkům (jen u her, jejichž profil je zná): pokyny pod titulky, úkol pod titulky, menu hry. */
    private val pokyny: Boolean = true,
    private val ukol: Boolean = true,
    private val menu: Boolean = false,
    /** Aktuální vzhled pruhu – posílá se i do TV (mění se za běhu). */
    private val vzhled: () -> Vzhled = { Vzhled() },
    /** Předčítání překladu v TV: 0 = ne, jinak rychlost hlasu v % (mění se za běhu). */
    private val hlas: () -> Int = { 0 },
    /** Vzhled změněný ovladačem TV (OK při hře) – uložit jako z okna PC. */
    private val zmenaZTv: (TvSettings.Change) -> Unit = {},
    /** Řádky stavu pro TV vlevo nahoře (barva „ok“/„pozor“/„info“, text) – jako Domů. */
    private val stavTv: () -> List<Pair<String, String>> = { emptyList() },
) {
    private var zapisovac: java.io.Writer? = null
    private val casZaznamu = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    /** Řádek do `zaznam.txt` (když záznam běží). */
    private fun zapis(m: String) {
        val w = zapisovac ?: return
        runCatching { w.write("${java.time.LocalTime.now().format(casZaznamu)} $m\n"); w.flush() }
    }

    private fun hlas(m: String) { log(m); zapis(m) }

    @Volatile private var konec = false
    private var stav = Stav(Stav.Faze.NACITANI)
        set(v) { if (v != field) { field = v; zmena(v) } }

    fun zastav() { konec = true }

    /** Okno, ze kterého se má číst dál („pid:1234“) – převezme se před dalším čtením. */
    @Volatile private var noveOkno: String? = null
    fun okno(nazev: String) { noveOkno = nazev }

    /** Běží, dokud se nezavolá [zastav]. Vrací se po úklidu (pruh schovaný, port zavřený). */
    fun bez() {
        val demo = okno.isEmpty()
        var sender: TvSender? = null
        var helper: Helper? = null
        var telHelper: Helper? = null
        try {
            zmena(stav)
            val model = Nastroje.model(jazyk) ?: error("chybí překladový model pro jazyk $jazyk")
            if (vystup != "pc") {
                sender = TvSender { log("televize: $it") }
                // vzhled změněný ovladačem TV (OK při hře): uložit jako z okna PC, nové volby se pošlou zpět
                sender.onChange = { ch -> hlas("TV: změna vzhledu ${ch.javaClass.simpleName}"); zmenaZTv(ch) }
                if (!sender.start(port)) error("port pro televizi je obsazený – neběží už jiná čtečka?")
            }
            nactiPamet()
            val slozka = zaznamy?.let { File(it, "pc-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))) }
            if (slozka != null) {
                slozka.mkdirs()
                zapisovac = File(slozka, "zaznam.txt").bufferedWriter(Charsets.UTF_8)
                log("Záznam do ${slozka.path}")
                zapis("Titulky PS5 pro PC · jazyk $jazyk · okno „$okno“ · výstup $vystup · DeepL ${if (deepl) "ano" else "ne"} · " +
                    "hra ${profil.id} · pokyny ${if (pokyny) "ano" else "ne"} · úkol ${if (ukol) "ano" else "ne"} · menu ${if (menu) "ano" else "ne"}")
                zapis("CTENI = text ze spodní třetiny obrazu, souřadnice v celém obraze [vlevo,nahoře-vpravo,dole]; snímky celé hry jsou snimek-*.jpg")
            }
            log("Načítám čtení textu a překladač…")
            helper = Helper(python, pomocnik, model.path, jazyk, okno, chyby, slozka)
            if (konec) return
            hlas((if (demo) "Běží ukázka (bez PlayStationu)." else if (okno == CEKA_NA_OKNO) "Čeká na obraz z PS5." else "Čtu okno „$okno“.") +
                (if (deepl) " Překlad DeepL online + offline." else " Překlad offline."))
            stav = Stav(Stav.Faze.BEZI)
            // telefon ve hře se čte ve vlastním pomocníkovi a vlákně – titulky na jeho čtení (s DeepL i pár sekund) nečekají
            if (sender != null && !demo && GameProfiles.of(hra).phoneSpots.isNotEmpty()) telHelper = try {
                Helper(python, pomocnik, model.path, jazyk, okno, null, null, vedlejsi = true)
            } catch (e: Throwable) { hlas("telefon: pomocník se nespustil (${e.message})"); null }
            smycka(helper, sender, demo, telHelper)
            stav = Stav(Stav.Faze.STOJI)
        } catch (e: Throwable) {
            hlas("Chyba: ${e.message}")
            stav = Stav(Stav.Faze.CHYBA, chyba = e.message ?: e.toString())
        } finally {
            pruh?.schovej()
            pruhUkolu?.schovej()
            sender?.hide(); sender?.stop()
            helper?.stop()
            telHelper?.stop()
            ulozPamet()
            runCatching { zapisovac?.close() }
            zapisovac = null
            if (stav.faze != Stav.Faze.CHYBA) stav = Stav(Stav.Faze.STOJI)
        }
    }

    // ---- profil hry a slovník (jako `Prefs.glossary` na Androidu, bez vlastního slovníku) ----

    private val lang = Lang.of(jazyk)
    private val profil = GameProfiles.of(hra)
    private val slovnik = Glossary(
        profil.names, profil.termsFor(lang), BuiltinPhrases.forLang(lang), profil.cast,
    )
    private val menuSpec = if (menu) profil.menuSpec else null

    /** Paměť offline překladů – stejná věta se nepřekládá znovu. Zvlášť pro jazyk a hru (jiná jména). */
    private val pamet = TranslationCache()
    private val souborPameti = File(Nastroje.slozka, "pamet-$jazyk-${profil.id}.txt")

    private fun nactiPamet() {
        runCatching { if (souborPameti.exists()) pamet.load(souborPameti.readText(Charsets.UTF_8)) }
    }

    private fun ulozPamet() {
        synchronized(pamet) {
            if (pamet.dirty == 0) return
            runCatching {
                souborPameti.parentFile?.mkdirs()
                val tmp = File(souborPameti.path + ".tmp")
                tmp.writeText(pamet.dump(), Charsets.UTF_8)
                if (!tmp.renameTo(souborPameti)) { souborPameti.delete(); tmp.renameTo(souborPameti) }
            }
        }
    }

    /**
     * Offline překlad jako `OfflineTranslator.translate` na Androidu: ručně zadaná věta, jména a pojmy hry
     * schované za značky (nepřekládají se), slang → spisovná angličtina, u češtiny tykání ([Natural]).
     */
    private fun prelozOffline(helper: Helper, text: String): String? {
        synchronized(pamet) { pamet.get(text) }?.let { return it }
        slovnik.sentence(text)?.let { return it }
        val p = slovnik.prepare(text)
        var vysledek: String? = null
        if (p.names.isNotEmpty()) helper.translate(p.protectedText)?.let { vysledek = OfflinePrep.finish(p, it) }
        val hotovo = (vysledek ?: helper.translate(p.plain) ?: return null).let { Natural.fix(lang, text, it) }
        synchronized(pamet) {
            pamet.put(text, hotovo)
            if (pamet.dirty >= 20) ulozPamet()
        }
        return hotovo
    }

    private fun smycka(helper: Helper, sender: TvSender?, demo: Boolean, telHelper: Helper?) {
        val tracker = SubtitleTracker(hideAfterEmpty = 3, fastComplete = true)
        val start = System.currentTimeMillis()
        var hideAt = 0L
        var shown = false
        var steadyText = ""
        var steadySince = 0L
        // text v místě, kde hra ukazuje telefon: od kdy tam je a jaký (titulkem až po kontrole, že telefon zavřený není)
        var sporneOd = 0L
        var sporneText = ""
        var lastError = ""
        var lastTvs = -1
        // každý mluvčí má svou barvu jména; postavy z profilu hry jako ve hře (např. jedna postava žlutě, druhá tyrkysově)
        val speakerColors = SpeakerColors(profil.colorsByKey)
        NameColor.reset()
        NameColor.profile = profil.colorsByKey
        val colorAsked = HashMap<String, Long>()
        // kde na snímku byl anglický titulek (poměry v obraze) – podle toho se položí pruh
        var spot = Misto(0.5f, -1f, -1f, 0f)
        var spotLR = -1f to -1f
        var lastRead = ""
        // menu hry (GTA VI): čte se horní část obrazu, jen když v pruhu není titulek
        val menuTracker = menuSpec?.let { MenuTracker(it) }
        val menuWords = HashMap<String, String>()
        var menuShown = false
        var menuKey = ""
        var menuAt = 0L
        var statusAt = 0L
        // úkol pod titulky (GTA): vyčlení se z titulků vždy (jako na Androidu), překládá a ukazuje jen s volbou
        val ukoly = ObjectiveTracker()

        // Jako na Androidu od 0.97.2: s DeepL se počká na jeho překlad (nejvýš ~2,5 s) a ukáže se jen
        // jedna varianta; offline jen když DeepL nepomůže. Krátká slova ze slovníčku zůstávají offline.
        fun translateSegments(text: String, sDeepl: Boolean = deepl && !deeplJenTelefon): List<Segment>? {
            val parts = TextLogic.segments(text).let { if (profil.prompts) TextLogic.markPrompt(it) else it }
            val bodies = parts.map { TextLogic.normalizeCase(it.body) }
            val quick = bodies.map { QuickWords.lookup(jazyk, it) }
            val ask = bodies.indices.filter { quick[it] == null && slovnik.sentence(TextLogic.forTranslator(bodies[it])) == null }
            val online = if (sDeepl && ask.isNotEmpty()) {
                val t0 = System.currentTimeMillis()
                DeeplPc.preloz(lang, ask.map { TextLogic.forTranslator(bodies[it]) }, slovnik)?.also {
                    stav = stav.copy(deeplMs = System.currentTimeMillis() - t0, deeplChyba = null)
                } ?: run {
                    stav = stav.copy(deeplChyba = DeeplPc.backoff.lastProblem); null
                }
            } else null
            val result = ArrayList<Segment>()
            parts.forEachIndexed { i, s ->
                val body = bodies[i]
                val fromDeepl = online?.getOrNull(ask.indexOf(i))?.let { Natural.fix(lang, body, it) }
                val raw = quick[i] ?: fromDeepl ?: prelozOffline(helper, TextLogic.forTranslator(body)) ?: return null
                // rod postav (mluvčí, oslovený) – až po paměti překladů, ta je jen podle textu
                val tr = slovnik.genders(lang, body, raw, s.speaker)
                result.add(Segment(s.speaker?.let { TextLogic.normalizeName(it) }, TextLogic.finishTranslation(body, tr), s.choice, s.prompt))
            }
            return result
        }

        /** Vzhled pro TV (`TvLook`) – stejné volby jako pruh na PC; [box] = překlad menu vedle menu. */
        fun look(original: String?, box: Pair<Float, Float>? = null, mluvit: Boolean = false): String {
            val v = vzhled()
            val matched = if (v.pismo == 0 && spot.radek > 0f) MatchSize.textPx(spot.radek, TvLook.SCREEN_H) else 0
            val tvOriginal = original?.takeIf { v.original }?.let { o ->
                TextLogic.segments(o).joinToString("\n") { s -> (s.speaker?.let { TextLogic.normalizeName(it) + ": " } ?: "") + s.body }
            }
            val json = TvLook.json(
                v.layout(), v.pozadi, TvLook.textPx(if (v.pismo > 0) v.pismo else 22, matched), v.barva.rgb, v.tucne,
                spot.nahore, spot.dole, spotLR.first, spotLR.second, tvOriginal, v.okraj, box,
            )
            // předčítání v TV (Android TV od 1.3-z7; LG/Samsung pole přeskočí): rychlost a jazyk hlasu
            val h = if (mluvit) hlas() else 0
            return if (h > 0 && json.endsWith("}")) json.dropLast(1) + ",\"hlas\":$h,\"hlasJazyk\":\"$jazyk\"}" else json
        }

        fun hide() { if (shown) { shown = false; sender?.hide(); pruh?.schovej() } }

        fun schovejUkol() { pruhUkolu?.schovej(); sender?.sendObjective(TvSender.objectiveMessage(null)) }

        // telefon ve hře (GTA VI): čte se jen pro TV – panel s překladem kreslí aplikace v TV; vlastní vlákno a pomocník
        fun prelozTelefon(h: Helper, texty: List<String>, mluvci: List<String?>): List<String> {
            val tela = texty.map { TextLogic.normalizeCase(it) }
            val online = if (deepl && tela.isNotEmpty()) DeeplPc.preloz(lang, tela.map { TextLogic.forTranslator(it) }, slovnik) else null
            return tela.indices.map { i ->
                val body = tela[i]
                val raw = QuickWords.lookup(jazyk, body) ?: online?.getOrNull(i)?.let { Natural.fix(lang, body, it) }
                    ?: prelozOffline(h, TextLogic.forTranslator(body)) ?: return@map texty[i]
                TextLogic.finishTranslation(body, slovnik.genders(lang, body, raw, mluvci.getOrNull(i)))
            }
        }
        val telefon = if (sender != null && telHelper != null) TelefonPc(profil, lang, { t, m -> prelozTelefon(telHelper, t, m) },
            { m -> zapis(m); if (m.startsWith("TELEFON brana") || (m.startsWith("TELEFON ") && "→" in m)) log(m) }).takeIf { it.zapnuto } else null
        fun telefonPryc() { telefon?.schovejPozdeji() }
        val menuPc = if (telefon != null && telHelper != null) MenuPc(profil, { t, m -> prelozTelefon(telHelper, t, m) },
            { m -> zapis(m); log(m) }).takeIf { it.zapnuto } else null
        val oknoTel = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val telVlakno = if (telefon != null && telHelper != null && sender != null) kotlin.concurrent.thread(name = "telefon", isDaemon = true) {
            while (!konec) {
                try {
                    oknoTel.getAndSet(null)?.let { o -> telHelper.window(o) }
                    val snimekAt = System.currentTimeMillis()
                    if (telHelper.snimek()) {
                        val v = vzhled()
                        val now = System.currentTimeMillis()
                        telefon.tick(now, telHelper, if (v.pismo > 0) v.pismo else 22, v.pozadi, { sender.sendPhone(it) }, snimekAt)
                        // obrazovky menu hry (mapa, Brief, Progress…) celé – jen když není otevřený telefon
                        if (!telefon.shown && !telefon.ikonaVidet) menuPc?.tick(now, telHelper) { sender.sendPhone(it) }
                    }
                } catch (e: Exception) { zapis("TELEFON chyba ${e.message}"); Thread.sleep(1000) }
                Thread.sleep(150)
            }
        } else null

        /**
         * Úkol pod titulky jako na Androidu (`TranslatorService.handleObjective`): nový úkol přeložit a ukázat
         * přes anglický úkol, drží se, dokud ho čtení vidí; s vypnutou volbou se jen vyčlení z titulků.
         */
        fun ukolZeCteni(line: OcrLine?, r: Helper.Read.Frame) {
            val top = line?.let { (r.y0 + it.top).toFloat() / r.h } ?: -1f
            when (val ch = ukoly.update(line?.text, top)) {
                is ObjectiveTracker.Change.Show -> {
                    val l = line ?: return
                    if (!ukol) return
                    val segs = translateSegments(ch.text) ?: return
                    val text = segs.joinToString(" ") { it.body }.trim()
                    if (text.isEmpty()) return
                    val vyska = TextLogic.fontSizeOf(l).toFloat() / r.h
                    pruhUkolu?.ukaz(listOf(Segment(null, text)), emptyMap(),
                        Misto((r.x0 + (l.left + l.right) / 2f) / r.w, (r.y0 + l.top).toFloat() / r.h, (r.y0 + l.bottom).toFloat() / r.h, vyska))
                    if (sender != null) {
                        val v = vzhled()
                        val box = floatArrayOf((r.x0 + l.left).toFloat() / r.w, (r.y0 + l.top).toFloat() / r.h,
                            (r.x0 + l.right).toFloat() / r.w, (r.y0 + l.bottom).toFloat() / r.h)
                        val px = TvLook.textPx(if (v.pismo > 0) v.pismo else 22, if (v.pismo == 0) MatchSize.textPx(vyska, TvLook.SCREEN_H) else 0)
                        sender.sendObjective(TvSender.objectiveMessage(text, box, px, v.pozadi, SpeakerColors.hex(v.barva.rgb)))
                    }
                    hlas("úkol: ${ch.text}  →  $text")
                }
                ObjectiveTracker.Change.Hide -> { schovejUkol(); zapis("úkol schován") }
                ObjectiveTracker.Change.Keep -> {}
            }
        }

        /** Barva jména z obrazu hry: jednou za 3 s pro jméno, které ještě nemá naučenou ([NameColor]). */
        fun barvaJmena(r: Helper.Read.Frame, text: String) {
            val now = System.currentTimeMillis()
            for (line in r.lines) {
                val name = TextLogic.splitSpeaker(line.text).speaker ?: continue
                if (!text.contains(name)) continue
                val key = SpeakerColors.key(name)
                if (NameColor.learned(name) != null || now - (colorAsked[key] ?: 0L) < 3000) continue
                colorAsked[key] = now
                val frac = (name.length + 1).toFloat() / max(1, line.text.length)
                val nameRight = line.left + ((line.right - line.left) * frac).toInt()
                val t0 = System.nanoTime()
                val rgb = helper.barva(r.x0 + line.left, r.y0 + line.top, r.x0 + nameRight, r.y0 + line.bottom)
                    ?.takeIf { NameColor.isColored(it) }
                val jmeno = TextLogic.normalizeName(name)
                NameColor.record(jmeno, rgb, (System.nanoTime() - t0) / 1000)
                zapis("BARVA $jmeno ${rgb?.let { SpeakerColors.hex(it) } ?: "-"}")
                NameColor.learned(jmeno)?.let { c -> speakerColors.learn(jmeno, c) }
                return
            }
        }

        /** Menu hry: vrací true, když je menu vidět (pak se titulky nečtou). */
        fun menu(now: Long): Boolean {
            val spec = menuSpec ?: return false
            val mt = menuTracker ?: return false
            if (now - menuAt < (if (menuShown) 400 else 1500)) return menuShown
            if (!menuShown && shown) return false
            menuAt = now
            val m = helper.menu() ?: return menuShown
            val (view, changed) = mt.update(GameMenu.read(m.lines, m.w, m.h, spec))
            if (view == null) {
                if (menuShown) {
                    menuShown = false; menuKey = ""
                    pruh?.schovej(); sender?.hide()
                    zapis("MENU off")
                }
                return false
            }
            if (changed || view.key != menuKey) {
                menuKey = view.key
                fun preloz(t: String) = menuWords.getOrPut(t) {
                    translateSegments(t)?.let { TextLogic.joinSegments(it) }?.replace('\n', ' ') ?: t
                }
                val items = view.items.map { spec.word(lang, it) ?: preloz(it) }
                val text = GameMenu.text(view.kind, items, view.selected, view.title?.let { preloz(it) }, view.description?.let { preloz(it) })
                val radky = text.split('\n')
                pruh?.ukazMenu(radky, view.box)
                sender?.show(text, TvSender.partsJson(radky.map { Segment(null, it) }), look(null, view.box))
                hlas("menu ${view.kind}: ${view.items.joinToString(", ")} · vybraná ${view.selected} (${m.ms} ms)")
            }
            // titulky se během menu nečtou – po zavření menu začne čtení od začátku; úkol se v menu schová
            if (!menuShown) { hideAt = 0; tracker.reset(); if (ukoly.current != null) { ukoly.reset(); schovejUkol() } }
            menuShown = true
            return true
        }

        var cteniPriTelefonu = 0L
        while (!konec) {
            val now = System.currentTimeMillis()
            noveOkno?.let { o ->
                noveOkno = null
                helper.window(o)
                oknoTel.set(o)
                hideAt = 0; hide(); tracker.reset(); lastError = ""
                if (o != CEKA_NA_OKNO) hlas("Čtu okno „$o“.")
            }
            if (sender != null) {
                if (sender.connected != lastTvs) {
                    lastTvs = sender.connected
                    log("připojených televizí: $lastTvs")
                    stav = stav.copy(televize = lastTvs)
                }
                // volby vzhledu pro nabídku OK v TV (posílá se jen změna) a stav vlevo nahoře každé 2 s
                val v = vzhled()
                sender.sendSettings(TvSettings.message(v.pozadi, if (v.pismo > 0) v.pismo else 22, v.pismo == 0, v.poloha.tv, v.original,
                    pad = v.okraj, colorIndex = Nastaveni.BARVY.indexOfFirst { it.second == (v.barva.rgb and 0xFFFFFF) }.coerceAtLeast(0)))
                if (now - statusAt > 2000) { statusAt = now; sender.sendStatus(TvSender.statusMessage(stavTv())) }
            }
            // otevřený telefon ve hře: titulky se čtou jen 3 s po minulém čtení – jinak čtení spodní třetiny (bubliny telefonu)
            // a čtení telefonu běží souběžně a dělí se o procesor (záznam majitele 26. 11. 2026: telefon 5–10 s místo ~1 s)
            // (pauza se počítá od KONCE čtení – čtení titulků trvá přes 1 s, s pauzou od začátku běželo skoro pořád)
            val telefonOtevreny = telefon != null && (telefon.shown || telefon.ikonaVidet)
            if (telefonOtevreny && now - cteniPriTelefonu < TELEFON_TITULKY_MS) {
                Thread.sleep(100)
                continue
            }
            val demoText = if (demo) DemoGame.subtitleAt(now - start) else null
            val r = helper.read(force = !tracker.isSettled, demoText = demoText)
            cteniPriTelefonu = System.currentTimeMillis()
            // menu hry otevřené → jeho překlad vedle menu, titulky se nečtou
            if (r !is Helper.Read.Error && menu(now)) {
                telefonPryc()
                Thread.sleep(max(20L, INTERVAL_MS - (System.currentTimeMillis() - now)))
                continue
            }
            when (r) {
                Helper.Read.Same -> {}
                is Helper.Read.Error -> {
                    // čtečka pro TV před spojením s PS5 okno nemá – to není chyba
                    if (r.text != lastError) { lastError = r.text; if (!r.text.contains(CEKA_NA_OKNO)) hlas("obraz není: ${r.text}") }
                    stav = stav.copy(faze = Stav.Faze.BEZ_OKNA, chyba = r.text)
                    hideAt = 0; hide(); tracker.reset()
                    if (ukoly.current != null) { ukoly.reset(); schovejUkol() }
                    telefonPryc()
                }
                is Helper.Read.Frame -> {
                    // text z displeje telefonu není titulek (bubliny zpráv ve spodní třetině obrazu)
                    val pb = telefon?.boxVeSnimku(r.w, r.h)
                    val radky0 = if (pb == null || r.w <= 0 || r.h <= 0) r.lines else r.lines.filterNot { l ->
                        val cx = (r.x0 + (l.left + l.right) / 2f) / r.w; val cy = (r.y0 + (l.top + l.bottom) / 2f) / r.h
                        cx in (pb[0] - 0.02f)..(pb[2] + 0.02f) && cy in (pb[1] - 0.02f)..(pb[3] + 0.04f)
                    }
                    // telefon ještě nenahlášený (kontrola jen 1× za s): text v místě telefonu počká na kontrolu po svém
                    // objevení – dřív mezi otevřením telefonu a kontrolou proklouzla zpráva jako titulek
                    // (majitel 25. 11. 2026: „Stulrofffor ti nechal další balíček“)
                    val vMiste = if (telefon == null || pb != null || r.w <= 0 || r.h <= 0) emptyList() else radky0.filter { l ->
                        telefon.vMisteTelefonu(r.w, r.h, (r.x0 + (l.left + l.right) / 2f) / r.w, (r.y0 + (l.top + l.bottom) / 2f) / r.h)
                    }
                    val radky = if (vMiste.isEmpty()) { sporneOd = 0L; sporneText = ""; radky0 } else {
                        val t = vMiste.joinToString(" ") { it.text }
                        if (sporneOd == 0L || !TextLogic.similar(t, sporneText)) { sporneOd = now; sporneText = t }
                        when (telefon?.zavrenoPo(sporneOd)) {
                            true -> radky0
                            else -> radky0 - vMiste.toSet()
                        }
                    }
                    if (lastError.isNotEmpty()) { lastError = ""; hlas("obraz je zpět (${r.w}×${r.h})") }
                    stav = stav.copy(faze = Stav.Faze.BEZI, chyba = "", ms = r.ms)
                    // Stejné hranice jako v aplikaci pro Android TV (TranslatorService.process).
                    val foreign = TextLogic.looksForeign(radky.joinToString("\n") { it.text })
                    val picked = if (foreign) Picked("", 0) else TextLogic.assemble(
                        toBlocks(radky),
                        minLineHeight = (r.h * 0.013f).toInt(),
                        subtitlesOnly = true,
                        screenCenterX = r.w / 2 - r.x0,
                        centerTolerance = (r.w * 0.14f).toInt(),
                        maxLineHeight = (r.h * 0.085f).toInt(),
                        screen = profil.screen,
                        objectives = profil.prompts,
                        objectiveTop = if (profil.prompts && ukoly.top >= 0f) (ukoly.top * r.h).toInt() - r.y0 else -1,
                        // úkol jen ve spodních 14 % obrazu (v GTA je vždy úplně dole, pod titulky)
                        objectiveMinTop = if (profil.prompts) (r.h * UKOL_ZONA).toInt() - r.y0 else -1,
                    )
                    if (profil.prompts && !foreign) ukolZeCteni(picked.objective, r)
                    if (zapisovac != null) {
                        val read = r.lines.joinToString(" ; ") { "[${r.x0 + it.left},${r.y0 + it.top}-${r.x0 + it.right},${r.y0 + it.bottom}] ${it.text}" }
                        val line = "CTENI ${r.ms} ms ${r.w}×${r.h} | " + read.ifEmpty { "–" } +
                            " | vzato: " + picked.text.replace('\n', ' ').ifEmpty { "–" }
                        if (read != lastRead) { lastRead = read; zapis(line) }
                    }
                    if (picked.top >= 0 && picked.left >= 0 && r.w > 0 && r.h > 0) {
                        spot = Misto(
                            (r.x0 + (picked.left + picked.right) / 2f) / r.w,
                            (r.y0 + picked.top).toFloat() / r.h,
                            (r.y0 + picked.bottom).toFloat() / r.h,
                            picked.lineHeight.toFloat() / r.h,
                        )
                        spotLR = (r.x0 + picked.left).toFloat() / r.w to (r.x0 + picked.right).toFloat() / r.w
                    }
                    if (picked.text.isEmpty() || !TextLogic.looksForeign(picked.text)) {
                        if (picked.text.isEmpty() || !TextLogic.similar(picked.text, steadyText)) { steadyText = picked.text; steadySince = now }
                        // volby odpovědí smí stát déle – hráč se rozhoduje
                        val staleMs = if (TextLogic.hasChoices(picked.text)) TextLogic.CHOICES_STALE_MS else STALE_MS
                        if (picked.text.isNotEmpty() && now - steadySince > staleMs) {
                            if (shown) { hideAt = 0; hide(); hlas("schováno (text stojí dlouho – není to titulek)") }
                        } else when (val action = tracker.onOcr(TextLogic.withoutFading(picked.text, tracker.shown), slowDevice = r.ms > 500)) {
                            is SubtitleTracker.Action.Show -> translateSegments(action.text)?.let { all ->
                                tracker.confirmShown(action.text)
                                hideAt = 0
                                shown = true
                                // pokyny pro hráče (GTA) jen s volbou Pokyny pod titulky
                                val segs = if (pokyny) all else all.filter { !it.prompt }
                                barvaJmena(r, action.text)
                                val text = TextLogic.joinSegments(segs)
                                val colors = speakerColors.colorsFor(segs)
                                sender?.show(text, TvSender.partsJson(segs, colors), look(action.text, mluvit = true))
                                pruh?.ukaz(segs, colors, spot, action.text.replace('\n', ' '))
                                val orig = action.text.replace('\n', ' ')
                                val prel = text.replace('\n', ' ')
                                stav = stav.copy(original = orig, preklad = prel)
                                hlas("${r.ms} ms  $orig  →  $prel")
                            }
                            SubtitleTracker.Action.Hide -> hideAt = now + HOLD_MS
                            SubtitleTracker.Action.None ->
                                // na obrazovce je už jiný titulek → starý překlad hned schovej
                                if (tracker.isChanging && picked.text.isNotEmpty() && shown) { hideAt = 0; hide() }
                        }
                    }
                }
            }
            if (hideAt in 1..System.currentTimeMillis()) { hideAt = 0; hide() }
            Thread.sleep(max(20L, INTERVAL_MS - (System.currentTimeMillis() - now)))
        }
    }
}
