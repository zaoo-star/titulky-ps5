package cz.recaption.pc

import com.sun.jna.Native
import com.sun.jna.win32.StdCallLibrary

/**
 * „Nezhasínat počítač při překladu“ (obdoba `Prefs.screenDim` na Androidu): dokud běží čtení, počítač neusne
 * a displej nezhasne ani se nezamkne – jinak se čtení okna chiaki-ng zastaví nebo zpomalí (na Macu App Nap).
 *
 * - Windows: `SetThreadExecutionState(ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_DISPLAY_REQUIRED)` přes JNA, uvolnění
 *   `ES_CONTINUOUS`. Stav patří vláknu, které ho nastavilo – proto se volá jen z vlákna okna (Swing), které žije
 *   po celou dobu aplikace; s koncem procesu ho Windows zruší samy.
 * - Mac: `/usr/bin/caffeinate -dimsu -w <pid aplikace>` – skončí sám, když skončí ReCaption (i po pádu).
 *
 * [drzet] je levné volat každou sekundu – systému se ozve jen při změně.
 */
internal class Nezhasinat(private val log: (String) -> Unit) {
    private var drzi = false
    private var kofein: Process? = null

    private interface Kernel32 : StdCallLibrary {
        fun SetThreadExecutionState(esFlags: Int): Int
    }

    private val kernel32: Kernel32? by lazy {
        if (!Os.windows) null else runCatching { Native.load("kernel32", Kernel32::class.java) }.getOrNull()
    }

    fun drzet(chce: Boolean) {
        if (chce == drzi && (!chce || !Os.mac || kofein?.isAlive == true)) return
        if (chce) zapnout() else uvolnit()
    }

    private fun zapnout() {
        try {
            when {
                Os.windows -> {
                    val k = kernel32 ?: return
                    if (k.SetThreadExecutionState(ES_CONTINUOUS or ES_SYSTEM_REQUIRED or ES_DISPLAY_REQUIRED) == 0) {
                        if (!drzi) log("SCREEN|nezhasínat se nepovedlo")
                        drzi = true   // znovu nezkoušet každou sekundu
                        return
                    }
                }
                Os.mac -> {
                    kofein?.destroy()
                    kofein = ProcessBuilder("/usr/bin/caffeinate", "-dimsu", "-w", ProcessHandle.current().pid().toString())
                        .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                }
                else -> return
            }
            if (!drzi) log(Os.text("SCREEN|PC neusne a displej nezhasne, dokud běží překlad"))
            drzi = true
        } catch (e: Throwable) {
            if (!drzi) log("SCREEN|nezhasínat se nepovedlo: ${e.message}")
            drzi = true
        }
    }

    fun uvolnit() {
        if (!drzi && kofein == null) return
        runCatching { if (Os.windows) kernel32?.SetThreadExecutionState(ES_CONTINUOUS) }
        runCatching { kofein?.destroy() }
        kofein = null
        if (drzi) log(Os.text("SCREEN|PC smí zase usnout"))
        drzi = false
    }

    private companion object {
        const val ES_CONTINUOUS = 0x80000000.toInt()
        const val ES_SYSTEM_REQUIRED = 0x00000001
        const val ES_DISPLAY_REQUIRED = 0x00000002
    }
}
