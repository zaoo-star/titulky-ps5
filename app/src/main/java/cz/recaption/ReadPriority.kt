package cz.recaption

import android.content.Context
import android.os.Build
import android.os.Process
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Přednost titulků i u procesoru (přání majitele): smyčka, která čte a překládá titulky, běží na vlastním vlákně s vyšší
 * prioritou ([Process.THREAD_PRIORITY_DISPLAY]), vedlejší čtení (menu, telefon, tipy, ovládání vpravo dole, pokyny) na vlastním
 * vlákně s nižší. Na Androidu 12+ se systému navíc hlásí, jak dlouho kolo čtení titulků trvá a jak dlouho trvat má
 * ([android.os.PerformanceHintManager]) – systém podle toho vláknu přidá výkon (rychlejší jádra, vyšší takt).
 * Všechno je jen rada systému: když cokoli selže nebo zařízení funkci nemá, čte se dál jako dřív.
 */
object ReadPriority {
    /** Vlákno čtení titulků: před běžnými vlákny aplikace, jako vykreslování. */
    const val SUBTITLES = Process.THREAD_PRIORITY_DISPLAY
    /** Vedlejší čtení: za běžnými vlákny, ale ne „na pozadí“ (to systém na některých zařízeních škrtí na pomalá jádra). */
    const val SIDE = 5

    /** Jedno vlákno [name] s prioritou [priority] pro korutiny; priorita se nastaví na vlákně samém, chyba se přejde. */
    fun dispatcher(name: String, priority: Int): ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { r ->
            Thread({
                try { Process.setThreadPriority(priority) } catch (_: Throwable) {}
                r.run()
            }, name).apply { isDaemon = true }
        }.asCoroutineDispatcher()

    /** Nastaví prioritu volajícího vlákna (dekódování videa); chyba se přejde. */
    fun raiseCurrent(priority: Int = SUBTITLES) {
        try { Process.setThreadPriority(priority) } catch (_: Throwable) {}
    }
}

/**
 * Hlášení délky kola čtení titulků systému (Android 12+). [open] se volá na vlákně čtení (bere jeho číslo), [report] po každém
 * kole, které četlo snímek, [close] na konci. Na starším Androidu a po jakékoli chybě nedělá nic.
 */
class ReadHint {
    private var session: Any? = null
    private var targetNs = 0L

    /** Otevře hlášení pro volající vlákno; [targetMs] = jak dlouho má kolo čtení nejvýš trvat. Vrací, jestli se povedlo. */
    fun open(c: Context, targetMs: Long): Boolean {
        close()
        if (Build.VERSION.SDK_INT < 31 || targetMs <= 0) return false
        return try {
            val m = c.getSystemService(android.os.PerformanceHintManager::class.java) ?: return false
            targetNs = targetMs * 1_000_000L
            session = m.createHintSession(intArrayOf(Process.myTid()), targetNs)
            session != null
        } catch (_: Throwable) { session = null; false }
    }

    /** Kolo čtení titulků trvalo [ms]. */
    fun report(ms: Long) {
        if (Build.VERSION.SDK_INT < 31 || ms <= 0) return
        val s = session as? android.os.PerformanceHintManager.Session ?: return
        try { s.reportActualWorkDuration(ms * 1_000_000L) } catch (_: Throwable) {}
    }

    /** Jiná cílová doba kola (změna nastavení výkonu). */
    fun target(targetMs: Long) {
        if (Build.VERSION.SDK_INT < 31 || targetMs <= 0 || targetMs * 1_000_000L == targetNs) return
        val s = session as? android.os.PerformanceHintManager.Session ?: return
        try { targetNs = targetMs * 1_000_000L; s.updateTargetWorkDuration(targetNs) } catch (_: Throwable) {}
    }

    fun close() {
        if (Build.VERSION.SDK_INT >= 31) try { (session as? android.os.PerformanceHintManager.Session)?.close() } catch (_: Throwable) {}
        session = null
    }
}
