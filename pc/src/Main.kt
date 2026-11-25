package cz.recaption.pc

import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Čtečka z příkazové řádky (`spustit.ps1`) – pro ladění, výpis jde do okna konzole.
 * Běžně se spouští okno aplikace (`Aplikace.kt`, `ReCaption.cmd`).
 *
 * Bez `--okno` běží ukázka: věty ukázkové hry se kreslí na umělý obrázek (zkouška bez PlayStationu).
 */
fun main(args: Array<String>) {
    fun arg(name: String, default: String = ""): String {
        val i = args.indexOf(name)
        return if (i >= 0 && i + 1 < args.size) args[i + 1] else default
    }
    // rozbor uložených snímků (pc\rozbor.cmd, viz Rozbor.kt) – nic nečte z obrazovky ani nepřekládá
    // seznam her (data her nejsou v aplikaci – ze serveru, viz SeznamHer.kt)
    SeznamHer.priprav { println(it) }
    if ("--rozbor" in args) { Rozbor.spust(args.toList()); return }
    val window = arg("--okno")
    val lang = arg("--jazyk", "cs")
    // kam s překladem: pruh na obrazovce počítače (pc), do televize LG/Samsung (tv), nebo obojí
    val output = arg("--vystup", "pc")
    if (output !in listOf("pc", "tv", "oboje")) {
        println("Použití: [--python CESTA] [--pomocnik cteni.py] [--okno NÁZEV_OKNA] [--jazyk cs] [--vystup pc|tv|oboje] [--port 8787] [--deepl] [--deepl-klic SOUBOR] [--zaznam] [--hra gta6|gta5|rdr2|diablo4|…] [--jen-titulky] [--menu]")
        return
    }
    fun log(m: String) = println("${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))} $m")
    // zkouška částí pro Mac (sestavení na GitHubu); --zkouska-okno jen ukáže okno s anglickým titulkem
    if ("--zkouska-mac" in args) System.exit(MacZkouska.spust("--nechat" in args))
    if ("--zkouska-okno" in args) {
        javax.swing.SwingUtilities.invokeLater { MacZkouska.zkusebniOkno().apply { setLocation(40, 60); isVisible = true } }
        println("OKNO pid:${ProcessHandle.current().pid()}")
        Thread.sleep(arg("--zkouska-okno", "120").toLongOrNull()?.times(1000) ?: 120_000)
        System.exit(0)
    }
    // stažení chiaki-ng stejně jako tlačítkem Stáhnout na stránce Domů (zkouška na GitHubu)
    if ("--stahni-chiaki" in args) {
        val chyba = Chiaki.stahni({ }, ::log)
        log(if (chyba == null) "chiaki-ng: ${Chiaki.exe()}" else "chiaki-ng se nestáhl: $chyba")
        System.exit(if (chyba == null) 0 else 1)
    }
    // jen hledání PS5 v síti a spárované konzole z chiaki-ng (ladění Hrát)
    if ("--hledej-ps5" in args) {
        log("chiaki-ng: ${Chiaki.exe() ?: "chybí"}; spárované: ${Chiaki.konzole().joinToString { it.jmeno + if (it.probuzeni != null) " (probuzení ok)" else "" }.ifEmpty { "žádné" }}")
        log("čtečka pro TV (profil ${Chiaki.PROFIL_CTECKY}): ${Chiaki.konzole(Chiaki.PROFIL_CTECKY).joinToString { it.jmeno }.ifEmpty { "nespárováno" }}")
        Chiaki.hledej(3000).ifEmpty { log("PS5 v síti nenalezena."); emptyList() }
            .forEach { log("PS5 ${it.jmeno} · ${it.adresa} · " + if (it.pripravena) "zapnutá" else if (it.klid) "klidový režim" else "?") }
        return
    }
    // klíč DeepL ze souboru (uloží se zašifrovaný jako z okna aplikace); obsah se nikam nevypisuje
    arg("--deepl-klic").takeIf { it.isNotEmpty() }?.let { f ->
        log(if (DeeplPc.uloz(File(f).readText())) "Klíč DeepL uložen." else "V souboru není klíč DeepL.")
    }

    val game = if (output == "tv" || window.isEmpty()) null else OknoHry(window)
    val oblast = { game?.oblast() ?: if (window.isEmpty()) celaObrazovka() else null }
    val bar = if (output == "tv") null else Pruh(oblast = oblast)
    val barUkolu = if (output == "tv") null else Pruh(naMiste = true, oblast = oblast)
    val reader = Ctecka(
        jazyk = lang, okno = window, vystup = output, pruh = bar, pruhUkolu = barUkolu, log = ::log,
        port = arg("--port", "8787").toInt(),
        python = arg("--python", Nastroje.python.path),
        pomocnik = File(arg("--pomocnik", Nastroje.pomocnik.path)),
        deepl = "--deepl" in args && DeeplPc.ma(),
        zaznamy = if ("--zaznam" in args) Nastroje.zaznamy() else null,
        hra = arg("--hra", "all"),
        // --jen-titulky = bez pokynů a úkolu pod titulky; --menu = překlad menu hry (výchozí vypnuto)
        pokyny = "--jen-titulky" !in args,
        ukol = "--jen-titulky" !in args,
        menu = "--menu" in args,
    )
    Runtime.getRuntime().addShutdownHook(Thread { reader.zastav() })
    reader.bez()
    System.exit(0)
}
