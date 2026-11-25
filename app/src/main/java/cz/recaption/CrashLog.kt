package cz.recaption

import android.content.Context
import cz.recaption.core.CrashReport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Když aplikace spadne, zapíše důvod, verzi a čas do souboru `pady.txt` (posledních pár pádů)
 * a do záznamu událostí. Při dalším spuštění pak hlavní obrazovka hráči řekne, že aplikace
 * minule spadla, a pád je vidět v hlášení o stavu aplikace. Původní obsluha pádu (systém)
 * se volá dál, takže se nic jiného nemění.
 */
object CrashLog {
    private const val FILE = "pady.txt"
    /** Značka „pád, o kterém hráč ještě neví“ (smaže se, když se mu ukáže upozornění). */
    private const val UNSEEN = "pad-neohlaseny"
    @Volatile private var installed = false

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val app = ctx.applicationContext
        EventFile.init(app)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val time = SimpleDateFormat("d. M. yyyy HH:mm:ss", Locale.US).format(Date())
                val entry = CrashReport.format(time, "${BuildInfo.VERSION} (${BuildInfo.VERSION_CODE})", thread.name, e)
                val f = File(app.filesDir, FILE)
                val old = try { if (f.exists()) f.readText() else null } catch (_: Throwable) { null }
                f.writeText(CrashReport.append(old, entry))
                File(app.filesDir, UNSEEN).writeText("1")
                EventFile.add("CRASH|" + entry.lines().drop(1).firstOrNull().orEmpty())
            } catch (_: Throwable) {
                // zápis se nepovedl – hlavně nebránit systému, aby pád dokončil
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Všechny uložené pády (od nejstaršího), nebo null. */
    fun all(ctx: Context): String? = try {
        File(ctx.applicationContext.filesDir, FILE).takeIf { it.exists() }?.readText()
    } catch (_: Throwable) { null }

    /** Poslední pád, nebo null. */
    fun last(ctx: Context): String? = CrashReport.last(all(ctx))

    /** Spadla aplikace od posledního upozornění? Upozornění se tím odbaví (ukáže se jen jednou). */
    fun takeUnseen(ctx: Context): Boolean = try {
        val f = File(ctx.applicationContext.filesDir, UNSEEN)
        f.exists().also { if (it) f.delete() }
    } catch (_: Throwable) { false }
}
