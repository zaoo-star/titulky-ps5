package cz.recaption

import android.content.Context
import cz.recaption.core.VideoSteps
import java.net.HttpURLConnection
import java.net.URL

/**
 * Seznam klipů ke krokům průvodce (`pomoc/videa/kroky.json`, [VideoSteps]): uložený se načte hned, nový se stáhne
 * nejvýš jednou za 6 h na pozadí (malý soubor, 4 s limit). Bez sítě / bez seznamu = žádné řádky „▶ Video“.
 */
object VideoStepList {
    @Volatile var clips: Map<String, String> = emptyMap(); private set
    /** Klipy zatím jen česky ([VideoSteps.parseCsOnly]) – ukázat česky ve všech jazycích. */
    @Volatile var csOnly: Set<String> = emptySet(); private set
    @Volatile private var loaded = false
    @Volatile private var fetchedAt = 0L
    private const val EVERY_MS = 6L * 60 * 60 * 1000

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("videa", Context.MODE_PRIVATE)

    /** Uložený seznam hned, nový ze stránek na pozadí; [onNew] po stažení jiného seznamu (z vlákna – překreslit přes UI). */
    fun load(c: Context, onNew: () -> Unit) {
        if (!loaded) { loaded = true; prefs(c).getString("kroky", null).let { clips = VideoSteps.parse(it); csOnly = VideoSteps.parseCsOnly(it) } }
        val now = System.currentTimeMillis()
        if (now - fetchedAt < EVERY_MS) return
        fetchedAt = now
        val app = c.applicationContext
        Thread({
            try {
                val conn = URL(VideoSteps.LIST_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 4000; conn.readTimeout = 4000
                conn.setRequestProperty("Cache-Control", "no-cache")
                val text = if (conn.responseCode == 200) conn.inputStream.use { it.readBytes() }.takeIf { it.size <= 65_536 }?.toString(Charsets.UTF_8) else null
                conn.disconnect()
                val parsed = VideoSteps.parse(text)
                val cs = VideoSteps.parseCsOnly(text)
                if (text != null && (parsed != clips || cs != csOnly)) {
                    clips = parsed; csOnly = cs
                    prefs(app).edit().putString("kroky", text).apply()
                    onNew()
                }
            } catch (_: Throwable) {}
        }, "tp-videa-kroky").apply { isDaemon = true }.start()
    }
}
