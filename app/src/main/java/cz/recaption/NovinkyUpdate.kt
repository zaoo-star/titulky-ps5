package cz.recaption

import android.content.Context
import cz.recaption.core.Novinky
import java.net.HttpURLConnection
import java.net.URL

/**
 * Seznam novinek (`pomoc/novinky.json`, [Novinky]): uložený se načte hned, nový se stáhne nejvýš jednou za 6 h
 * na pozadí (malý soubor, 4 s limit) – stejný vzor jako [VideoStepList]. Bez sítě / bez seznamu řádek „Co je
 * nového“ prostě nic neukáže.
 */
object NovinkyUpdate {
    @Volatile var zaznamy: List<Novinky.Zaznam> = emptyList(); private set
    @Volatile private var loaded = false
    @Volatile private var fetchedAt = 0L
    private const val EVERY_MS = 6L * 60 * 60 * 1000

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("novinky", Context.MODE_PRIVATE)

    /** Uložený seznam hned, nový ze stránek na pozadí; [onNew] po stažení jiného seznamu (z vlákna – překreslit přes UI). */
    fun load(c: Context, onNew: () -> Unit = {}) {
        if (!loaded) { loaded = true; zaznamy = Novinky.parse(prefs(c).getString("json", null)) }
        val now = System.currentTimeMillis()
        if (now - fetchedAt < EVERY_MS) return
        fetchedAt = now
        val app = c.applicationContext
        Thread({
            try {
                val conn = URL(Novinky.URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 4000; conn.readTimeout = 4000
                conn.setRequestProperty("Cache-Control", "no-cache")
                val text = if (conn.responseCode == 200) conn.inputStream.use { it.readBytes() }.takeIf { it.size <= 300_000 }?.toString(Charsets.UTF_8) else null
                conn.disconnect()
                val parsed = Novinky.parse(text)
                if (text != null && parsed != zaznamy) {
                    zaznamy = parsed
                    prefs(app).edit().putString("json", text).apply()
                    onNew()
                }
            } catch (_: Throwable) {}
        }, "tp-novinky").apply { isDaemon = true }.start()
    }
}
