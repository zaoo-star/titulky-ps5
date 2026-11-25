package cz.recaption

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Jednotný vzhled aplikace – barvy, písmo a prvky ovladatelné ovladačem od TV. */
object Theme {
    // Titulky PS5: barvy ve stylu menu PS5 (tmavě modrá, modrý výběr).
    val BG = Color.rgb(0x05, 0x0A, 0x1A)
    val SIDEBAR = Color.rgb(0x0B, 0x18, 0x38)
    val CARD = Color.rgb(0x10, 0x20, 0x48)
    /** Plocha vybraného prvku – výrazně světlejší modrá. */
    val FOCUS = Color.rgb(0x1E, 0x3F, 0x8F)
    val ACCENT_FOCUS = Color.rgb(0x5C, 0x8D, 0xFF)
    val TEXT = Color.rgb(0xF2, 0xF5, 0xFF)
    val MUTED = Color.rgb(0x9A, 0xA8, 0xC8)
    val ACCENT = Color.rgb(0x2F, 0x6B, 0xFF)
    /** Text na ploše barvy [ACCENT] (modrá → bílý text). */
    val ACCENT_DARK = Color.WHITE
    val OK = Color.rgb(0x3D, 0xDC, 0x84)
    val WARN = Color.rgb(0xFF, 0xD4, 0x5C)
    val BAD = Color.rgb(0xEF, 0x44, 0x44)
}

class Ui(private val ctx: Context) {
    /** Podklad prázdné části ukazatelů postupu. */
    private val TRACK = 0x33FFFFFF

    private val density = ctx.resources.displayMetrics.density
    fun dp(v: Int): Int = (v * density).toInt()

    private fun rounded(color: Int, radius: Int = 12, stroke: Int = 0, strokeColor: Int = 0) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke > 0) setStroke(dp(stroke), strokeColor)
        }

    /**
     * Pozadí, které je při výběru ovladačem vidět na první pohled: světlejší plocha se silným
     * zlatým rámečkem (u zlatých tlačítek bílý rámeček).
     */
    fun focusBg(normal: Int, focused: Int = Theme.FOCUS, radius: Int = 12): Drawable =
        StateListDrawable().apply {
            val gold = normal == Theme.ACCENT
            addState(intArrayOf(android.R.attr.state_focused), rounded(if (gold) Theme.ACCENT_FOCUS else focused, radius, 5, if (gold) Color.WHITE else Theme.ACCENT))
            addState(intArrayOf(android.R.attr.state_pressed), rounded(focused, radius))
            addState(intArrayOf(), rounded(normal, radius))
        }

    /** Vybraný prvek se navíc lehce zvětší, takže je jasné, kde ovladač právě stojí. */
    fun pop(v: View) {
        v.setOnFocusChangeListener { view, has ->
            val scale = if (has) 1.04f else 1f
            // Po překreslení obrazovky (nová verze, průběh stahování, změna stavu) dostane stejné tlačítko
            // výběr znovu – tehdy bez animace, jinak by vybrané tlačítko při každém překreslení „pulzovalo“.
            if (android.os.SystemClock.uptimeMillis() < quietUntil) { view.animate().cancel(); view.scaleX = scale; view.scaleY = scale }
            else view.animate().scaleX(scale).scaleY(scale).setDuration(110).start()
        }
    }

    /** Do kdy se výběr po překreslení obnovuje bez animace (viz [pop], [restoringFocus]). */
    private var quietUntil = 0L

    /** Volá se těsně před obnovením výběru po překreslení obrazovky. */
    fun restoringFocus() { quietUntil = android.os.SystemClock.uptimeMillis() + 250 }

    fun cardBg(): Drawable = rounded(Theme.CARD, 14)

    fun text(s: CharSequence, size: Float = 16f, color: Int = Theme.TEXT, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setLineSpacing(0f, 1.15f)
        }

    // clipChildren = false: vybraný prvek se lehce zvětší a nesmí se mu přitom uříznout rohy
    fun vbox(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false; clipToPadding = false
    }
    fun hbox(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false; clipToPadding = false
    }

    fun card(): LinearLayout = vbox().apply {
        background = cardBg()
        setPadding(dp(22), dp(18), dp(22), dp(18))
    }

    /**
     * Ukazatel postupu (stahování, nahrávání, čekání). [fraction] 0..1; null = nevíme, kolik zbývá –
     * pak pruh běhá sem a tam, ať je vidět, že se něco děje.
     */
    fun progress(fraction: Float?): View {
        if (fraction == null) return android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Theme.ACCENT)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10))
        }
        val f = fraction.coerceIn(0f, 1f)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(TRACK, 5)
            clipToOutline = true
            addView(View(ctx).apply { background = rounded(Theme.ACCENT, 5) }, LinearLayout.LayoutParams(0, dp(10), f))
            addView(View(ctx), LinearLayout.LayoutParams(0, dp(10), 1f - f))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10))
        }
    }

    /**
     * Slabý běžící pruh pod řádkem, na který se právě čeká (připojování, hledání v síti, čekání na telefon / TV) – přání majitele
     * 22. 11. 2026. Tenký a poloprůhledný, odsazený pod text řádku s tečkou ([indent] v dp). Vrací hotový pohled i s okraji.
     */
    fun waiting(indent: Int = 30): View = progress(null).apply {
        alpha = 0.45f
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3)).apply {
            leftMargin = dp(indent); rightMargin = dp(6)
        }
    }

    /**
     * Kde v postupu hráč je: text („Krok 3 z 8“, [label] už s dosazenými čísly) a pod ním pruh
     * rozdělený na [total] dílků, hotové a právě probíhající dílky zlaté.
     */
    fun steps(current: Int, total: Int, label: String): View = vbox().apply {
        addView(text(label, 14f, Theme.ACCENT, bold = true))
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            for (i in 1..total) addView(View(ctx).apply { background = rounded(if (i <= current) Theme.ACCENT else TRACK, 3) },
                LinearLayout.LayoutParams(0, dp(6), 1f).apply { if (i > 1) leftMargin = dp(4) })
        }, lp(top = 6))
    }

    fun lp(w: Int = LinearLayout.LayoutParams.MATCH_PARENT, h: Int = LinearLayout.LayoutParams.WRAP_CONTENT, top: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top) }

    /** Velké tlačítko. primary = zlaté. */
    fun button(label: String, primary: Boolean = false, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = label
            textSize = if (primary) 20f else 17f
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            setTextColor(if (primary) Theme.ACCENT_DARK else Theme.TEXT)
            typeface = Typeface.create("sans-serif-medium", if (primary) Typeface.BOLD else Typeface.NORMAL)
            background = if (primary) focusBg(Theme.ACCENT) else focusBg(Theme.CARD)
            setPadding(dp(26), dp(14), dp(26), dp(14))
            setOnClickListener { onClick() }
            pop(this)
        }

    /**
     * Menší žluté tlačítko uvnitř karty (řádky na Domů, kapitoly Nápovědy). Běžné tlačítko má barvu karty
     * a v kartě splývá s textem.
     */
    fun smallButton(label: String, onClick: () -> Unit): TextView = button(label, onClick = onClick).apply {
        textSize = 15f
        setTextColor(Theme.ACCENT_DARK)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        background = focusBg(Theme.ACCENT, radius = 20)
        setPadding(dp(20), dp(8), dp(20), dp(8))
    }

    /**
     * Řádek nastavení: vlevo popis, vpravo hodnota (ZAP/VYP, velikost…).
     * Kliknutí OK na ovladači hodnotu změní.
     */
    fun settingRow(label: String, value: String, valueColor0: Int = Theme.ACCENT, onClick: () -> Unit): LinearLayout {
        // ZAP vždy zeleně, VYP šedě (přání majitele 22. 11. 2026 – ZAP bylo někde zelené, jinde žluté); jen u výchozí barvy,
        // řádky s vlastní barvou (varování) zůstávají
        // i „Zapnuto · profil hry“ (hodnota s doplňkem za tečkou) je zapnuto
        val on = ctx.tx("on"); val off = ctx.tx("off")
        val valueColor = if (valueColor0 != Theme.ACCENT) valueColor0 else when {
            value == on || value.startsWith("$on · ") -> Theme.OK
            value == off || value.startsWith("$off · ") -> Theme.MUTED
            else -> valueColor0
        }
        // Dlouhá hodnota (např. „Přes titulky (zakryje angličtinu)“) by na úzkém telefonu vzala skoro celou
        // šířku a popis by se lámal po slabikách – proto je tam hodnota pod popisem.
        val narrow = ctx.resources.configuration.screenWidthDp in 1 until 600
        val stacked = narrow && value.length > 12
        return (if (stacked) vbox() else hbox()).apply {
            isFocusable = true
            isClickable = true
            background = focusBg(Theme.CARD)
            pop(this)
            setPadding(dp(20), dp(14), dp(20), dp(14))
            if (stacked) {
                addView(text(label, 17f))
                addView(text(value, 16f, valueColor, bold = true), lp(top = 6))
            } else {
                addView(text(label, 17f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(text(value, 16f, valueColor, bold = true).apply { setPadding(dp(16), 0, 0, 0) })
            }
            setOnClickListener { onClick() }
        }
    }

    /**
     * Stavový řádek (jen informace, kliknout na něj nejde – od 1.00-rc21, přání majitele): stejné rozložení jako [settingRow],
     * ale bez výplně, jen s tenkým obrysem, a nejde vybrat ovladačem – na první pohled se liší od řádků, které něco dělají.
     */
    fun infoRow(label: String, value: String, valueColor: Int = Theme.TEXT): LinearLayout {
        val narrow = ctx.resources.configuration.screenWidthDp in 1 until 600
        val stacked = narrow && value.length > 12
        return (if (stacked) vbox() else hbox()).apply {
            background = rounded(Color.TRANSPARENT, 12, 1, Theme.CARD)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            if (stacked) {
                addView(text(label, 16f, Theme.MUTED))
                addView(text(value, 16f, valueColor, bold = true), lp(top = 4))
            } else {
                addView(text(label, 16f, Theme.MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(text(value, 16f, valueColor, bold = true).apply { setPadding(dp(16), 0, 0, 0) })
            }
        }
    }

    /** Položka kontrolního seznamu: tečka (zelená/oranžová), popis a případně tlačítko. */
    fun checkRow(ok: Boolean, optional: Boolean, label: String, okText: String, action: String?, onAction: (() -> Unit)?): LinearLayout {
        // úzký telefon: tlačítko pod popisem, jinak by popis zbyl úzký sloupec a lámal se po slovech
        if (!ok && action != null && onAction != null && ctx.resources.configuration.screenWidthDp in 1 until 600) {
            return vbox().apply {
                setPadding(0, dp(8), 0, dp(8))
                addView(hbox().apply {
                    addView(View(ctx).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (optional) Theme.MUTED else Theme.WARN) } },
                        LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(14) })
                    addView(text(label, 17f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                })
                addView(button(action) { onAction() }.apply { textSize = 15f; setPadding(dp(18), dp(8), dp(18), dp(8)) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(26); topMargin = dp(8) })
            }
        }
        return hbox().apply {
            setPadding(0, dp(8), 0, dp(8))
            val dotColor = if (ok) Theme.OK else if (optional) Theme.MUTED else Theme.WARN
            addView(View(ctx).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(dotColor) } },
                LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(14) })
            addView(text(label, 17f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (ok || action == null || onAction == null) {
                addView(text(if (ok || optional) okText else "✕", 15f, if (ok) Theme.OK else if (optional) Theme.MUTED else Theme.WARN))
            } else {
                addView(button(action) { onAction() }.apply { textSize = 15f; setPadding(dp(18), dp(8), dp(18), dp(8)) })
            }
        }
    }

    fun section(title: String): TextView = text(title, 15f, Theme.ACCENT, bold = true).apply {
        setPadding(0, dp(26), 0, dp(10))
    }
}
