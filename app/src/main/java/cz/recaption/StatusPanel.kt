package cz.recaption

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Stav v bodech přes hru vlevo nahoře na Android TV (od 0.97.23) – stejně jako aplikace v TV LG/Samsung
 * (`stavKresli` v `app.js`): konzole a obraz, jazyk a DeepL, hra ([TvStatus.rows]) a tlačítko rychlé nabídky
 * vzhledu v rámečku, když je nastavené ([QuickLook]). Zelený přehled po 8 s nebo s prvním titulkem zmizí,
 * oranžový zůstává, dokud se to nespraví. Ukáže se znovu při každé změně stavu.
 */
object StatusPanel {
    private val main = Handler(Looper.getMainLooper())
    private var view: LinearLayout? = null
    private var wm: WindowManager? = null
    private var signature = ""
    private var hideAt = 0L
    private var warn = false

    /** Otevřená rychlá nabídka vzhledu: stav je vidět celou dobu a zmizí s ní (jako OK v TV LG, od 0.97.35). */
    @Volatile private var pinned = false

    /** Služba volá každé 2 s (jen Android TV, jen když překlad běží a aplikace není vidět). */
    /**
     * [dynamic] = režim „dynamicky“: po chvíli zmizí i oranžový – zůstane jen při [sticky] (potíže se spojením nebo
     * chybějící jazykový balíček, [TvStatus.sticky]); nejistý odhad hry („GTA VI?“) ho tak nedrží na obrazovce.
     */
    fun update(c: Context, rows: List<Pair<String, String>>, dynamic: Boolean = false, sticky: Boolean = false) {
        val sig = rows.joinToString("|") { it.first + it.second }
        if (sig != signature) {
            signature = sig
            warn = if (dynamic) sticky else rows.any { it.first == "pozor" }
            show(c, rows)
        } else if (view != null && !warn && !pinned && SystemClock.uptimeMillis() > hideAt) hide()
    }

    /** Rychlá nabídka otevřená: ukázat stav hned a nechat ho, dokud se nabídka nezavře ([unpin]). */
    fun pin(c: Context, rows: List<Pair<String, String>>) = main.post {
        pinned = true
        signature = rows.joinToString("|") { it.first + it.second }
        warn = rows.any { it.first == "pozor" }
        show(c, rows)
    }

    /** Rychlá nabídka zavřená: stav zmizí s ní (oranžový – něco chybí – zůstane). */
    fun unpin() = main.post { pinned = false; if (!warn) hide() }

    /** Přišel první titulek: zelený přehled už není potřeba. */
    fun onSubtitle() { if (!warn && view != null) main.post { hide() } }

    /** Konec překladu: panel pryč a příště se ukáže znovu. */
    fun reset() { main.post { hide(); signature = "" } }

    fun hide() {
        val v = view ?: return
        view = null
        OverlayWindows.remove(wm, v)
    }

    private fun show(c: Context, rows: List<Pair<String, String>>) {
        hide()
        // přes hru: přes službu Usnadnění (Google TV), jinak „zobrazení přes jiné aplikace“
        val a11y = TitulkyAccessibility.instance
        val host: Context = a11y ?: c
        val type = if (a11y != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val d = host.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val box = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.argb(225, 14, 16, 20)); cornerRadius = dp(10).toFloat() }
            setPadding(dp(16), dp(10), dp(18), dp(10))
        }
        fun row(marker: View, text: String) = box.addView(LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(3))
            addView(marker)
            addView(TextView(host).apply { this.text = text; textSize = 14f; setTextColor(Theme.TEXT) })
        })
        rows.forEach { (b, text) ->
            val color = when (b) { "ok" -> Theme.OK; "pozor" -> Theme.WARN; else -> Theme.MUTED }
            row(View(host).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
                layoutParams = LinearLayout.LayoutParams(dp(9), dp(9)).apply { rightMargin = dp(10) }
            }, text)
        }
        // tlačítko rychlé nabídky vzhledu v rámečku (jako OK v TV LG)
        val key = Prefs.quickKey(c)
        if (key != 0) row(TextView(host).apply {
            text = QuickLook.keyName(key)
            textSize = 11f
            setTextColor(Theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(1), dp(6), dp(1))
            background = GradientDrawable().apply { setStroke(dp(1), Theme.TEXT); cornerRadius = dp(4).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(10) }
        }, c.tx("qk_title"))
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = dp(20); y = dp(16) }
        val w = host.getSystemService(WindowManager::class.java) ?: return
        if (!OverlayWindows.add(w, host, box, lp)) return
        view = box; wm = w
        hideAt = SystemClock.uptimeMillis() + 8000
    }
}
