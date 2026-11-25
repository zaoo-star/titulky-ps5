package cz.recaption.pc

import java.io.File

/**
 * Titulky PS5: spouštět program po přihlášení do Windows (přání majitele 25. 11. 2026 – TV pak PC zapne sama,
 * povel z TV `cz.recaption.core.PcWake`). Soubor `TitulkyPS5.cmd` ve složce Po spuštění (Startup) uživatele;
 * spustí `TitulkyPS5.exe` s `-Dtitulky.pozadi=1` (okno jen na liště). Jen balíček ke stažení (jpackage ví, kde je
 * exe) a jen Windows.
 */
object Autostart {
    private fun slozka(): File? = System.getenv("APPDATA")?.let { File(it, "Microsoft\\Windows\\Start Menu\\Programs\\Startup") }
    private fun soubor(): File? = slozka()?.let { File(it, "TitulkyPS5.cmd") }
    private fun exe(): File? = System.getProperty("jpackage.app-path")?.let(::File)?.takeIf { it.isFile }

    /** Dá se zapnout? (Windows, balíček ke stažení, složka Po spuštění existuje) */
    fun mozne(): Boolean = !Os.mac && exe() != null && slozka()?.isDirectory == true
    fun zapnuto(): Boolean = soubor()?.isFile == true

    /** Spuštěno ze složky Po spuštění – okno jen na liště. */
    val naPozadi: Boolean get() = System.getProperty("titulky.pozadi") == "1"

    /** Zapne / vypne; vrací výsledný stav. */
    fun nastav(zapnout: Boolean): Boolean {
        val f = soubor() ?: return false
        if (!zapnout) { f.delete(); return zapnuto() }
        val e = exe() ?: return false
        return try {
            f.writeText("@echo off\r\nset \"JAVA_TOOL_OPTIONS=-Dtitulky.pozadi=1\"\r\nstart \"\" \"${e.absolutePath}\"\r\n", Charsets.UTF_8)
            true
        } catch (_: Throwable) { false }
    }
}
