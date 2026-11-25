package cz.recaption

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import cz.recaption.core.ScreenLayout
import cz.recaption.core.TvSettings
import cz.recaption.core.TvType

/**
 * Rychlá nabídka vzhledu titulků ovladačem na Android TV (od 0.97.15) – totéž, co má aplikace v TV LG/Samsung
 * po stisku OK při hře: pozadí pruhu, velikost a barva písma, okraj pruhu, poloha, originál (a jeho velikost), barevná jména
 * (od 0.97.81 stejné řádky jako v TV 1.5.18). Otevře ji tlačítko, které si hráč
 * zvolí v ⚙ Nastavení → Obecné („Tlačítko vzhledu titulků“, [Prefs.quickKey]); OK to být nesmí – televize
 * ho při HDMI často posílá do PlayStationu.
 *
 * Tlačítka chytá služba Usnadnění ([TitulkyAccessibility.onKeyEvent], `flagRequestFilterKeyEvents`) – jen
 * zvolené tlačítko, a dokud je nabídka otevřená, i šipky a Zpět. Ostatní tlačítka jdou dál beze změny.
 * Změny jdou stejnou cestou jako z TV LG ([TvSettings.Change] → [TranslatorService.quickChange]).
 */
object QuickLook {
    private enum class Row { OPACITY, SIZE, COLOR, PAD, POSITION, ORIGINAL, ORIG_SIZE, NAMES }

    /** Řádky nabídky – velikost originálu jen se zapnutým originálem (jako v TV LG/Samsung). */
    private fun rows(c: android.content.Context): List<Row> =
        Row.values().filter { it != Row.ORIG_SIZE || Prefs.showOriginal(c) }
    @Volatile var open = false
        private set
    private var row = 0
    private var view: LinearLayout? = null

    /** Tlačítka, která na nabídku dát nejde (ovládají hru, televizi nebo samotnou nabídku). */
    val FORBIDDEN = setOf(
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_TV_POWER, KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_ASSIST,
    )

    /** Název tlačítka pro Nastavení („PROG_RED“ → „Prog red“). */
    fun keyName(code: Int): String =
        KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ').lowercase().replaceFirstChar { it.uppercaseChar() }

    /** Volá služba Usnadnění pro každé tlačítko; true = tlačítko si nechala nabídka (dál nejde). */
    fun onKey(svc: TitulkyAccessibility, e: KeyEvent): Boolean {
        val key = Prefs.quickKey(svc)
        val active = key != 0 && TranslatorService.running && !UiState.visible && Prefs.tvType(svc) == TvType.ANDROID
        if (!active) { if (open) close(); return false }
        if (e.keyCode == key) {
            if (e.action == KeyEvent.ACTION_UP) { if (open) close() else show(svc) }
            return true
        }
        if (!open) return false
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (down) { val n = rows(svc).size; row = (row + n - 1) % n; draw(svc) }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (down) { row = (row + 1) % rows(svc).size; draw(svc) }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (down) change(svc, -1)
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (down) change(svc, 1)
            KeyEvent.KEYCODE_BACK -> if (!down) close()
            else -> return false
        }
        return true
    }

    private fun show(svc: TitulkyAccessibility) {
        row = 0
        open = true
        draw(svc)
        TranslatorService.quickPreview(true)
    }

    fun close() {
        if (!open) return
        open = false
        val v = view ?: run { TranslatorService.quickPreview(false); return }
        view = null
        OverlayWindows.remove(TitulkyAccessibility.instance?.getSystemService(WindowManager::class.java), v)
        TranslatorService.quickPreview(false)
    }

    /** Další hodnota zvolené volby (krok −1 / +1) – stejné seznamy jako v TV LG ([TvSettings]). */
    private fun change(svc: TitulkyAccessibility, step: Int) {
        val sizes = listOf(0) + TvSettings.TEXT_SIZES
        val ch: TvSettings.Change = when (rows(svc).getOrElse(row) { Row.OPACITY }) {
            Row.OPACITY -> TvSettings.Change.Opacity(next(TvSettings.OPACITIES, Prefs.opacity(svc), step))
            Row.SIZE -> TvSettings.Change.TextSize(next(sizes, if (Prefs.matchSize(svc)) 0 else Prefs.textSize(svc), step))
            Row.COLOR -> TvSettings.Change.TextColor(next((0 until TvSettings.COLORS).toList(), Prefs.textColorIndex(svc), step))
            Row.PAD -> TvSettings.Change.Pad(next((0 until TvSettings.PADS).toList(), Prefs.barPad(svc), step))
            Row.POSITION -> TvSettings.Change.Position(next(TvSettings.POSITIONS.values.toList(), Prefs.layout(svc).position, step))
            Row.ORIGINAL -> TvSettings.Change.Original(!Prefs.showOriginal(svc))
            Row.ORIG_SIZE -> TvSettings.Change.OrigSize(next(sizes, Prefs.origSp(svc), step))
            Row.NAMES -> TvSettings.Change.Names(!Prefs.coloredSpeaker(svc))
        }
        TranslatorService.quickChange(ch)
        // změna se uloží na hlavním vlákně až po tomto stisku – překreslit za ní (pořadí zpráv), ať nabídka ukazuje novou hodnotu
        android.os.Handler(android.os.Looper.getMainLooper()).post { if (open) draw(svc) }
    }

    private fun <T> next(list: List<T>, cur: T, step: Int): T {
        val i = list.indexOf(cur)
        return if (i < 0) list[0] else list[(i + step + list.size) % list.size]
    }

    private fun value(svc: TitulkyAccessibility, r: Row): String = when (r) {
        Row.OPACITY -> "${Prefs.opacity(svc)} %"
        Row.SIZE -> if (Prefs.matchSize(svc)) svc.tx("qk_size_game") else Prefs.textSize(svc).toString()
        Row.COLOR -> svc.tx("color_${Prefs.textColorIndex(svc)}")
        Row.PAD -> Prefs.barPad(svc).toString()
        Row.POSITION -> svc.tx("pos_" + Prefs.layout(svc).position.name.lowercase())
        Row.ORIGINAL -> svc.tx(if (Prefs.showOriginal(svc)) "on" else "off")
        Row.ORIG_SIZE -> Prefs.origSp(svc).let { if (it == 0) svc.tx("orig_auto") else it.toString() }
        Row.NAMES -> svc.tx(if (Prefs.coloredSpeaker(svc)) "on" else "off")
    }

    private fun label(svc: TitulkyAccessibility, r: Row): String = svc.tx(when (r) {
        Row.OPACITY -> "l_opacity"; Row.SIZE -> "l_size"; Row.COLOR -> "l_color"; Row.PAD -> "l_pad"
        Row.POSITION -> "l_pos"; Row.ORIGINAL -> "l_original"; Row.ORIG_SIZE -> "l_orig_size"; Row.NAMES -> "l_speaker"
    })

    /** Nakreslí (nebo překreslí) nabídku vpravo nahoře přes hru. */
    private fun draw(svc: TitulkyAccessibility) {
        val d = svc.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val box = view ?: LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.argb(240, 14, 16, 20)); cornerRadius = dp(10).toFloat() }
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }.also { v ->
            val lp = WindowManager.LayoutParams(dp(380), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
                .apply { gravity = Gravity.TOP or Gravity.END; x = dp(24); y = dp(24) }
            if (!OverlayWindows.add(svc.getSystemService(WindowManager::class.java), svc, v, lp)) { open = false; return }
            view = v
        }
        box.removeAllViews()
        fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(svc).apply {
            text = s; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        box.addView(text(svc.tx("qk_title"), 18f, Theme.ACCENT, bold = true))
        val list = rows(svc)
        if (row >= list.size) row = list.size - 1
        list.forEachIndexed { i, r ->
            val label = label(svc, r)
            val sel = i == row
            box.addView(LinearLayout(svc).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(10), dp(7), dp(10), dp(7))
                if (sel) background = GradientDrawable().apply { setColor(Theme.ACCENT); cornerRadius = dp(6).toFloat() }
                val c = if (sel) Theme.ACCENT_DARK else Theme.TEXT
                addView(text(label, 15f, c, sel), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(text("◀ " + value(svc, r) + " ▶", 15f, c, sel))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        box.addView(text(svc.tx("qk_hint"), 12f, Theme.MUTED), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        box.visibility = View.VISIBLE
    }
}
