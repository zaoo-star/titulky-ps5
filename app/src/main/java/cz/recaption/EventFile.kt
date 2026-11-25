package cz.recaption

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Záznam důležitých událostí (spojení s konzolí, dekodér, rychlost) do souboru. Přežije i vypnutí
 * aplikace, takže v hlášení je vidět, co se dělo PŘED tím, než ji uživatel kvůli chybě zavřel.
 */
object EventFile {
    private const val MAX = 48_000L
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    @Volatile private var file: File? = null

    fun init(ctx: Context) { if (file == null) file = File(ctx.applicationContext.filesDir, "udalosti.txt") }

    @Synchronized fun add(line: String) {
        val f = file ?: return
        try {
            if (f.length() > MAX) f.writeText(f.readText().takeLast((MAX / 2).toInt()).substringAfter('\n'))
            f.appendText(fmt.format(Date()) + " " + line.take(220) + "\n")
        } catch (_: Exception) {}
    }

    @Synchronized fun tail(lines: Int): String {
        val f = file ?: return ""
        return try { f.readLines().takeLast(lines).joinToString("\n") } catch (_: Exception) { "" }
    }
}
