package cz.recaption

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import cz.recaption.core.MatchSize
import cz.recaption.core.ScreenLayout
import cz.recaption.core.Segment
import cz.recaption.core.SizeKeeper
import cz.recaption.core.SpeakerColors
import cz.recaption.core.TextLogic
import cz.recaption.core.TvLook
import cz.recaption.core.TvSender
import kotlin.math.max

/**
 * Černý pruh s překladem, zobrazený přes obraz televize (i přes vstup HDMI s hrou).
 *
 * Okno se vytváří přes službu Usnadnění, když je zapnutá (funguje na Google TV / Chromecastu
 * bez ADB), jinak přes povolení „Zobrazení přes jiné aplikace“.
 */
class SubtitleOverlay(
    appContext: Context,
    private val screenW: Int,
    private val screenH: Int,
    layout: ScreenLayout,
    textSizeSp: Int,
    private val opacityPercent: Int,
    private val showDebug: Boolean,
    private val coloredSpeaker: Boolean,
    private val textColor: Int = Color.WHITE,
    private val bold: Boolean = false,
    /** Velikost písma překladu podle anglického originálu (jinak pevná [textSizeSp]). */
    private val matchOriginalSize: Boolean = false,
    /** Okraj nad a pod textem 0–3 ([Prefs.BAR_PADS]). */
    private val barPad: Int = 1,
    /** Velikost písma anglického originálu v sp ([Prefs.origSp]); 0 = automaticky 62 % písma překladu. */
    private val origSp: Int = 0,
    /** Písmo úkolu v sp (0 = podle úkolu ve hře / jako titulky) a poloha (-1 = na místě úkolu, jinak střed v % výšky). */
    private val objSizeSp: Int = 0,
    private val objPosPercent: Int = -1,
    /** Pozadí úkolu 0–100 % (-1 = jako pruh s titulky), od 0.97.50. */
    private val objBgPercent: Int = -1,
    /** Pokyny pro hráče ([Segment.prompt]) ukazovat (Titulky → Hra → Pokyny pod titulky), nebo vynechat. */
    private val showPrompts: Boolean = true,
    /** „Zakrýt celou oblast titulků“ (poloha Přes titulky): pruh přes celé pole posledních titulků ([cz.recaption.core.CoverArea]). */
    private val coverAll: Boolean = false,
    /** Pevné barvy postav z profilu hry ([cz.recaption.core.GameProfile.colorsByKey]). */
    private val speakerPreset: Map<String, Int> = emptyMap(),
    /**
     * Dostane každou změnu pruhu: text překladu a jeho řádky zvlášť s barvami jmen a volbami
     * ([TvSender.partsJson]) a vzhled pruhu pro televizi ([TvLook]), nebo null při schování. Tudy jdou titulky i do televize LG
     * (viz [TvSender]); volá se i tehdy, když se pruh na tomto zařízení zobrazit nepodařilo.
     */
    private val onText: ((String?, String?, String?) -> Unit)? = null,
    /** Úkol pod titulky pro televizi LG/Samsung – hotová zpráva [TvSender.objectiveMessage] (od 0.97.43). */
    private val onObjective: ((String) -> Unit)? = null,
    /** Popis volby Nastavení hry pro televizi LG/Samsung – hotová zpráva [TvSender.menuDescMessage] (od 0.97.58). */
    private val onMenuDesc: ((String) -> Unit)? = null,
    /** Menu vpravo dole pro televizi LG/Samsung – hotová zpráva [TvSender.cornerMessage] (aplikace v TV 1.5.19). */
    private val onCorner: ((String) -> Unit)? = null,
    /** Pokyny u předmětů pro televizi LG/Samsung – hotová zpráva [TvSender.worldMessage] (aplikace v TV 1.5.20). */
    private val onWorld: ((String) -> Unit)? = null,
    /** Panel telefonu pro televizi LG/Samsung – hotová zpráva [TvSender.phoneMessage] (aplikace v TV 1.5.22). */
    private val onPhone: ((String) -> Unit)? = null,
    /** Popisky menu hry na svém místě pro televizi LG/Samsung – hotová zpráva [TvSender.menuLabelsMessage] (aplikace v TV 1.5.31). */
    private val onMenuLabels: ((String) -> Unit)? = null,
) {
    companion object {
        private const val TAG = "TestPreklad"
        /** Posun telefonu, od kterého se překlad telefonu pošle do TV znovu (zlomky obrazu, ~5 bodů na 1280). */
        private const val TV_MOVE = 0.004f
        private val MENU_BG_BESIDE = Color.argb(255 * cz.recaption.core.GameMenu.SETTINGS_BG / 100, 0, 0, 0)
        /** Náhledy seznamu zpráv vedle telefonu: písmo o tolik větší než řádek v telefonu. */
        private const val PHONE_BESIDE_GROW = 1.25f
        private fun bubble(rgb: Int) = Color.argb(255 * cz.recaption.core.GameMenu.SETTINGS_BG / 100, rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
        /** Pozadí oken překladu menu Nastavení (nadpis, popis, seznam) – [cz.recaption.core.GameMenu.SETTINGS_BG] (od 1.00-rc24 40 %, od 0.97.143 30 %, dřív 70 %). */
        private val MENU_ALPHA = 255 * cz.recaption.core.GameMenu.SETTINGS_BG / 100

        /** Dá se teď vůbec něco zobrazit přes jiné aplikace? */
        fun canShow(c: Context): Boolean = TitulkyAccessibility.instance != null || Settings.canDrawOverlays(c)

        /** Pole posledních titulků – sdílené, ať ho nová instance po změně nastavení nezačíná znovu. */
        val coverArea = cz.recaption.core.CoverArea()
    }

    private val a11y = TitulkyAccessibility.instance
    private val host: Context = a11y ?: appContext
    private val windowType = if (a11y != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
    else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private val wm = host.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val density = host.resources.displayMetrics.density
    /** Každý mluvčí má svou barvu jména (po dobu čtení stálou). */
    private val speakerColors = SpeakerColors(speakerPreset)

    /** Barva jména naučená z obrazu hry ([cz.recaption.core.NameColor]); barva z profilu hry má přednost. */
    fun learnColor(name: String, color: Int) = speakerColors.learn(name, color)
    /** Barvy jmen tak, jak je má pruh (pro titulky ke čtení v telefonu, [cz.recaption.core.ReadFeed]); bez barevných jmen prázdné. */
    fun feedColors(segments: List<Segment>): Map<String, Int> = if (coloredSpeaker) speakerColors.colorsFor(segments) else emptyMap()
    private val choiceColor = SpeakerColors.PALETTE[0]
    private val originalColor = Color.rgb(0xA8, 0xB0, 0xB8)

    @Volatile private var bar: TextView? = null
    @Volatile private var barParams: WindowManager.LayoutParams? = null
    @Volatile private var debugView: TextView? = null
    /** Samostatný pruh s úkolem pod titulky (GTA) – nezávislý na pruhu s titulkem (od 0.97.43). */
    @Volatile private var objView: TextView? = null
    @Volatile private var objParams: WindowManager.LayoutParams? = null
    /** Nadpis a popis vybrané volby Nastavení hry – vlastní okno přes anglický popis, vedle pruhu se záložkou (od 0.97.58). */
    @Volatile private var descView: TextView? = null
    @Volatile private var descParams: WindowManager.LayoutParams? = null
    /** Překlad nadpisu volby Nastavení nad anglickým nadpisem (od 0.97.102) – vlastní okno, větší a tučně. */
    @Volatile private var titleView: TextView? = null
    @Volatile private var titleParams: WindowManager.LayoutParams? = null
    @Volatile private var layout: ScreenLayout = layout
    private val textSize = textSizeSp
    private val sizeKeeper = SizeKeeper()
    private val tvSizeKeeper = SizeKeeper()
    private val bgAlpha = (255 * opacityPercent / 100).coerceIn(0, 255)
    /** Pozadí úkolu: vlastní, nebo jako pruh. */
    private val objOpacity = if (objBgPercent in 0..100) objBgPercent else opacityPercent
    private val objAlpha = (255 * objOpacity / 100).coerceIn(0, 255)
    /** Kde naposledy stál pruh s titulky (horní hrana, zlomek výšky; -1 = ještě nebyl) – pro úkol „nad pruhem“. */
    @Volatile private var lastBarTop = -1f

    /** Je pruh právě zobrazený? */
    @Volatile var isShown = false
        private set
    private val coverBox = cz.recaption.core.CoverBox()

    init {
        main.post {
            val tv = TextView(host).apply {
                setTextColor(textColor)
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", if (bold) Typeface.BOLD else Typeface.NORMAL)
                // průhledný pruh (pod 60 %, od 0.97.29 až 0 %): výraznější stín, ať je text čitelný i na světlém obrazu
                if (opacityPercent < 60) setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), Color.BLACK)
                else setShadowLayer(3f, 0f, 1f, Color.BLACK)
                setLineSpacing(0f, 1.08f)
                setPadding(dp(22), dp(7), dp(22), dp(9))
                // černý podklad s lehce zaoblenými rohy
                background = GradientDrawable().apply {
                    setColor(Color.argb(bgAlpha, 0, 0, 0))
                    cornerRadius = dp(14).toFloat()
                }
                visibility = View.GONE
            }
            val lp = params((screenW * this.layout.barWidth).toInt()).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
            if (add(tv, lp)) { bar = tv; barParams = lp }

            // úkol: stejný vzhled jako pruh, vlastní okno a místo (tam, kde je anglický úkol)
            val ov = TextView(host).apply {
                // jako úkol ve hře: bílé, středně silné písmo (od 0.97.109, přání majitele – „okopírovat font“); barevná slova zvlášť
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                if (opacityPercent < 60) setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), Color.BLACK)
                else setShadowLayer(3f, 0f, 1f, Color.BLACK)
                background = GradientDrawable().apply {
                    setColor(Color.argb(objAlpha, 0, 0, 0))
                    cornerRadius = dp(10).toFloat()
                }
                visibility = View.GONE
            }
            val olp = params(WindowManager.LayoutParams.WRAP_CONTENT)
            if (add(ov, olp)) { objView = ov; objParams = olp }

            // popis volby Nastavení hry: text zarovnaný vlevo, šířka sloupce popisu ve hře
            val dv = TextView(host).apply {
                setTextColor(textColor)
                gravity = Gravity.START or Gravity.TOP
                typeface = Typeface.create("sans-serif-medium", if (bold) Typeface.BOLD else Typeface.NORMAL)
                setShadowLayer(3f, 0f, 1f, Color.BLACK)
                setLineSpacing(0f, 1.1f)
                background = GradientDrawable().apply {
                    setColor(Color.argb(MENU_ALPHA, 0, 0, 0))
                    cornerRadius = dp(8).toFloat()
                }
                visibility = View.GONE
            }
            val dlp0 = params(WindowManager.LayoutParams.WRAP_CONTENT)
            if (add(dv, dlp0)) { descView = dv; descParams = dlp0 }
            val ttl = TextView(host).apply {
                setTextColor(textColor)
                gravity = Gravity.START or Gravity.BOTTOM
                // zúžené tučné písmo jako nadpisy v menu hry (od 0.97.108)
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                setShadowLayer(3f, 0f, 1f, Color.BLACK)
                maxLines = 2
                background = GradientDrawable().apply {
                    setColor(Color.argb(MENU_ALPHA, 0, 0, 0))
                    cornerRadius = dp(8).toFloat()
                }
                visibility = View.GONE
            }
            val tlp0 = params(WindowManager.LayoutParams.WRAP_CONTENT)
            if (add(ttl, tlp0)) { titleView = ttl; titleParams = tlp0 }

            if (showDebug) {
                val dv = TextView(host).apply {
                    setTextColor(Color.rgb(0x9E, 0xF0, 0x9E))
                    textSize = 12f
                    typeface = Typeface.MONOSPACE
                    setBackgroundColor(Color.argb(170, 0, 0, 0))
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    maxLines = 6
                }
                val dlp = params(WindowManager.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.TOP or Gravity.END
                    x = dp(8); y = dp(8)
                }
                if (add(dv, dlp)) debugView = dv
            }
        }
    }

    private fun dp(v: Int) = (v * density).toInt()

    private fun params(width: Int) = WindowManager.LayoutParams(
        width,
        WindowManager.LayoutParams.WRAP_CONTENT,
        windowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0; y = 0
    }

    /** Okno přes hru přes [OverlayWindows] (strop oken, bez oprávnění se nezkouší, nikdy nespadne). */
    private fun add(v: View, lp: WindowManager.LayoutParams): Boolean = OverlayWindows.add(wm, host, v, lp)

    /** Skupina popisků v jednom okně ([OverlayLayer], od 1.0.1 – dřív okno na každý popisek). */
    private fun layer(onDrop: () -> Unit) =
        OverlayLayer(host, wm, screenW, { params(WindowManager.LayoutParams.WRAP_CONTENT) }, onDrop)
    private val cornerLayer = layer { cornerViews.clear(); cornerParams.clear() }
    private val worldLayer = layer { worldViews.clear(); worldParams.clear() }
    private val phoneOverLayer = layer { phoneOverViews.clear(); phoneOverParams.clear() }
    private val footLayer = layer { footViews.clear(); footParams.clear() }
    private val labelLayer = layer { labelViews.clear(); labelParams.clear() }

    fun setLayout(l: ScreenLayout) { layout = l }

    private fun format(segments: List<Segment>, original: String?, colors: Map<String, Int>): CharSequence {
        val sb = SpannableStringBuilder()
        segments.forEachIndexed { i, s ->
            if (i > 0) sb.append('\n')
            val lineStart = sb.length
            if (s.prompt) {
                // pokyn pro hráče (GTA): menší a šedý, ať se neplete s replikou
                sb.append(s.body)
                sb.setSpan(ForegroundColorSpan(originalColor), lineStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(RelativeSizeSpan(0.75f), lineStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                return@forEachIndexed
            }
            if (s.choice) {
                // volba odpovědi: odrážka a řádek zarovnaný vlevo, ať je vidět, že jde o seznam
                sb.append(TextLogic.CHOICE_MARK)
                sb.setSpan(ForegroundColorSpan(choiceColor), lineStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (s.speaker != null) {
                sb.append(s.speaker).append(": ")
                if (coloredSpeaker) {
                    val c = colors[s.speaker] ?: choiceColor
                    sb.setSpan(ForegroundColorSpan(c), lineStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), lineStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            if (s.selected) {
                // vybraná položka menu hry: černý text na bílém jako ve hře (od 0.97.111)
                sb.append('\u00A0').append(s.body).append('\u00A0')
                sb.setSpan(android.text.style.BackgroundColorSpan(Color.WHITE), lineStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(Color.BLACK), lineStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                return@forEachIndexed
            }
            sb.append(s.body)
            if (s.choice) sb.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL), lineStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (!original.isNullOrBlank()) {
            // originál po mluvčích, jméno ve stejné barvě jako v překladu (od 0.97.20)
            val start = sb.length
            TextLogic.segments(original).forEach { s ->
                sb.append('\n')
                val ls = sb.length
                if (s.speaker != null) {
                    val name = TextLogic.normalizeName(s.speaker)
                    sb.append(name).append(": ")
                    val c = if (coloredSpeaker) colors[name] else null
                    if (c != null) {
                        sb.setSpan(ForegroundColorSpan(c), ls, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(StyleSpan(Typeface.BOLD), ls, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else sb.setSpan(ForegroundColorSpan(originalColor), ls, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val bs = sb.length
                sb.append(s.body)
                sb.setSpan(ForegroundColorSpan(originalColor), bs, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val span = if (origSp > 0) android.text.style.AbsoluteSizeSpan(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, origSp.toFloat(), host.resources.displayMetrics).toInt())
            else RelativeSizeSpan(0.62f)
            sb.setSpan(span, start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    /**
     * Zobrazí překlad. [textTop]/[textBottom] = kde na obrazovce (0..1) je anglický text;
     * v režimu "přes titulky" se pruh položí přesně přes něj.
     */
    fun show(
        allSegments: List<Segment>, original: String?, rawTop: Float, rawBottom: Float,
        rawLeft: Float = -1f, rawRight: Float = -1f,
        /** Výška řádku originálu jako zlomek výšky obrazu (0 = neznámá). */
        lineHeight: Float = 0f,
        /** Pevné místo (levý horní roh ve zlomcích obrazu, text vlevo) – překlad menu hry vedle menu (od 0.97.36). */
        box: Pair<Float, Float>? = null,
        /** Rozestup řádků menu ve hře (zlomek výšky) – každý řádek překladu ve výšce své položky (od 0.97.40). */
        step: Float = 0f,
    ) {
        // „přes titulky“: zakrýt anglický text i s okrajem a v rozhovoru se nezmenšovat (CoverBox, od 0.97.56) –
        // rozšířená plocha jde i do TV LG/Samsung, takže kryje lépe i se stávající aplikací v TV
        if (!isShown) coverBox.reset()
        // volba „Zakrýt celou oblast titulků“ (od 0.97.59): celé pole, kde titulky naposledy byly, ne jen tenhle řádek
        val all = coverAll && layout.cover && box == null && rawTop >= 0f
        val cb = when {
            all -> coverArea.next(rawTop, rawBottom, rawLeft, rawRight, layout.read, android.os.SystemClock.uptimeMillis())
            layout.cover && box == null -> coverBox.next(rawTop, rawBottom, rawLeft, rawRight)
            else -> null
        }
        val textTop = cb?.get(0) ?: rawTop
        val textBottom = cb?.get(1) ?: rawBottom
        val textLeft = cb?.get(2) ?: rawLeft
        val textRight = cb?.get(3) ?: rawRight
        isShown = true
        val segments = if (showPrompts) allSegments else allSegments.filter { !it.prompt }
        val colors = if (coloredSpeaker) speakerColors.colorsFor(segments) else emptyMap()
        onText?.let { send ->
            // televize LG/Samsung: stejný vzhled a poloha jako tady (obrazovka televize 1920×1080)
            val tvMatched = if (matchOriginalSize) tvSizeKeeper.next(MatchSize.textPx(lineHeight, TvLook.SCREEN_H)) else 0
            // originál po mluvčích (řádek = jeden mluvčí) – televize podle jmen obarví jako překlad
            val tvOriginal = original?.let { o -> TextLogic.segments(o).joinToString("\n") { s ->
                (s.speaker?.let { TextLogic.normalizeName(it) + ": " } ?: "") + s.body } }
            // originál v TV: zvolená velikost písma jako podíl písma překladu (TV zná jen poměr, od 1.5.12)
            val tvMain = TvLook.textPx(textSize, tvMatched)
            val origPercent = if (origSp > 0) Math.round(TvLook.textPx(origSp, 0) * 100f / tvMain) else 62
            val look = TvLook.json(layout, opacityPercent, tvMain, textColor, bold,
                textTop, textBottom, textLeft, textRight, tvOriginal, barPad, box, step, origPercent = origPercent, coverAll = all)
            send(TextLogic.joinSegments(segments), TvSender.partsJson(segments, colors), look)
        }
        main.post {
            val tv = bar ?: return@post
            val lp = barParams ?: return@post
            val l = layout
            val maxWidth = (screenW * l.barWidth).toInt()
            val matched = if (matchOriginalSize) sizeKeeper.next(MatchSize.textPx(lineHeight, screenH)) else 0
            if (matched > 0) tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, matched.toFloat())
            else tv.textSize = textSize.toFloat()
            // výška řádku zpět na běžnou: překlad menu (box) ji nastaví na rozestup položek a titulky po menu
            // by jinak měly pruh vysoký jako řádek menu (0.97.42 – „titulky se občas zvětší“)
            tv.setLineSpacing(0f, 1.08f)
            // okraje podle velikosti písma (dřív pevné) – menší písmo = nižší a užší pruh
            val em = tv.textSize
            val pad = Prefs.BAR_PADS[barPad.coerceIn(0, 3)]
            tv.setPadding((em * 0.8f).toInt(), (em * pad).toInt(), (em * 0.8f).toInt(), (em * (pad + 0.04f)).toInt())
            tv.text = format(segments, original, colors)
            tv.gravity = if (box != null) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
            tv.bidiFor(tv.text)

            val hasText = textTop >= 0f && textBottom > textTop
            val tTop = if (hasText) textTop else l.read.top
            val tBottom = if (hasText) textBottom else l.read.bottom
            // v režimu "přes titulky" pruh zakryje anglický text, ale nikdy není vyšší než čtvrtina obrazovky
            tv.minHeight = when {
                // celé pole titulků: plocha už má okraj, pruh přesně tak vysoký (nejvýš 30 % + okraj, hlídá CoverArea)
                all && hasText -> ((tBottom - tTop) * screenH).toInt().coerceAtMost((screenH * 0.33f).toInt())
                l.cover && box == null -> (l.coverHeight(tTop, tBottom) * screenH).toInt().coerceAtMost(screenH / 4)
                else -> 0
            }
            // Šířka pruhu podle textu: jen tak široký, aby se vešel překlad a zakryla se angličtina
            // (ne pruh přes celou obrazovku).
            val hasSpan = hasText && textLeft >= 0f && textRight > textLeft
            tv.measure(
                View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            var width = tv.measuredWidth
            if (l.cover && hasSpan) width = max(width, ((textRight - textLeft) * screenW).toInt() + if (all) 0 else dp(20))
            width = width.coerceIn(dp(80), screenW)
            tv.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val h = tv.measuredHeight
            val hf = h.toFloat() / screenH
            val top = when {
                l.cover -> l.coverTop(tTop, tBottom, hf)
                // volba „těsně nad anglickým titulkem“: pruh jde s titulkem, ne nad celou oblast čtení
                l.hug && l.isAuto && hasText -> (tTop - 0.006f - hf).coerceAtLeast(0f)
                // automatika ještě hledá (čte se celá obrazovka) → pruh těsně nad nalezený text
                l.isAuto && hasText && l.read.height > 0.6f -> (tTop - 0.006f - hf).coerceAtLeast(0f)
                else -> l.barTop(hf)
            }
            lp.width = width
            if (box != null) {
                // řádek proti řádku: výška řádku = rozestup položek ve hře, písmo se jí přizpůsobí
                val rowPx = step * screenH
                if (rowPx > 0f && android.os.Build.VERSION.SDK_INT >= 28) {
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, minOf(tv.textSize, rowPx * 0.62f))
                    tv.lineHeight = rowPx.toInt()
                    tv.setPadding(tv.paddingLeft, 0, tv.paddingRight, 0)
                    tv.measure(View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    lp.width = tv.measuredWidth.coerceIn(dp(80), screenW)
                }
                // okno je zarovnané na střed: x = posun od středu, aby levý okraj pruhu byl na box.first
                val limit = max(0, (screenW - lp.width) / 2)
                lp.x = ((box.first * screenW + lp.width / 2f) - screenW / 2f).toInt().coerceIn(-limit, limit)
                // řádek proti položce: horní okraj textu položky ≈ horní čtvrtina řádku
                lp.y = (box.second * screenH - (if (rowPx > 0f) rowPx * 0.25f else 0f)).toInt().coerceIn(0, max(0, screenH - tv.measuredHeight))
                tv.visibility = View.VISIBLE
                try { wm.updateViewLayout(tv, lp) } catch (_: Exception) {}
                return@post
            }
            // vodorovně na střed anglického textu (okno je zarovnané na střed, x je posun od středu)
            val centerX = if (l.cover && hasSpan) (textLeft + textRight) / 2 else 0.5f
            val limit = max(0, (screenW - width) / 2)
            lp.x = ((centerX - 0.5f) * screenW).toInt().coerceIn(-limit, limit)
            lp.y = (top * screenH).toInt().coerceIn(0, max(0, screenH - h))
            lastBarTop = lp.y.toFloat() / screenH
            tv.visibility = View.VISIBLE
            try { wm.updateViewLayout(tv, lp) } catch (_: Exception) {}
        }
    }

    /**
     * Ukáže přeložený úkol ve vlastním pruhu přes anglický úkol. [box] = vlevo, nahoře, vpravo, dole (zlomky obrazu),
     * [lineHeight] = velikost písma originálu jako zlomek výšky (0 = neznámá → písmo z Nastavení).
     * [parts] = barevná slova úkolu (pokus, [cz.recaption.core.ObjectiveColors]); null = celý úkol výchozí barvou.
     */
    fun showObjective(text: String, gameBox: FloatArray, lineHeight: Float, parts: List<cz.recaption.core.ObjectivePart>? = null) {
        // od 1.0.01 písmo úkolu podle originálu vždy, když ho známe – nezávisle na volbě velikosti titulků (přání majitele:
        // „vlastní nižší pruh, ať může jít ještě dolů“)
        val byGame = objSizeSp <= 0 && lineHeight > 0f
        val px = when {
            objSizeSp > 0 -> objSizeSp * density * host.resources.configuration.fontScale
            byGame -> MatchSize.textPx(lineHeight, screenH).toFloat()
            else -> 0f
        }
        val tvPx = when {
            objSizeSp > 0 -> TvLook.textPx(objSizeSp, 0)
            byGame -> TvLook.textPx(textSize, MatchSize.textPx(lineHeight, TvLook.SCREEN_H))
            else -> TvLook.textPx(textSize, 0)
        }
        // poloha (od 0.97.50 jako pruh): na místě úkolu ve hře / těsně nad ním / nad pruhem s titulky / nahoře / vlastní výška;
        // výška pruhu úkolu odhadem z písma (≈ 1,45 řádku), střed pak jde do TV jako úzké `misto`
        val hFrac = (tvPx * 1.25f) / TvLook.SCREEN_H
        val cyFrac: Float? = when (objPosPercent) {
            -1 -> null
            -2 -> (gameBox[1] - 0.006f - hFrac / 2f)
            -3 -> ((if (lastBarTop >= 0f) lastBarTop else layout.read.top) - 0.006f - hFrac / 2f)
            -4 -> 0.08f + hFrac / 2f
            // těsně pod anglickým úkolem (od 0.97.109, přání majitele – v GTA VI je pod ním ještě kousek místa)
            // od 0.97.137 ještě níž (přání majitele): přesah přes odhad výšky pruhu
            // od 1.0.01 ještě níž (přání majitele) – nižší pruh smí skoro k okraji
            // od 1.0.3 ještě níž (hlášení 8JJX-5P5Q)
            -5 -> (gameBox[3] + hFrac * 0.6f)
            else -> objPosPercent / 100f
        }?.coerceIn(hFrac / 2f, 1f - hFrac * (if (objPosPercent == -5) 0.1f else 0.5f))
        // „pod anglickým úkolem“ smí až k dolnímu okraji (od 0.97.115, přání majitele „ještě trochu níž“ – úkol GTA VI je
        // dole kolem 96 %, odhad výšky pruhu 1,45 řádku ho držel výš, než bylo nutné)
        val box = if (cyFrac == null) gameBox else {
            val cx = if (objPosPercent == -2 || objPosPercent == -5) (gameBox[0] + gameBox[2]) / 2f else 0.5f
            floatArrayOf(cx - 0.2f, cyFrac - 0.001f, cx + 0.2f, cyFrac + 0.001f)
        }
        onObjective?.invoke(TvSender.objectiveMessage(text, box, tvPx, objOpacity, "#FFFFFF", parts))
        val shown: CharSequence = parts?.takeIf { p -> p.joinToString("") { it.text } == text }?.let { p ->
            val sb = SpannableStringBuilder()
            for (part in p) {
                val start = sb.length
                sb.append(part.text)
                part.color?.let { c -> sb.setSpan(ForegroundColorSpan(c or 0xFF000000.toInt()), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            }
            sb
        } ?: text
        main.post {
            val v = objView ?: return@post
            val lp = objParams ?: return@post
            if (px > 0f) v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px) else v.textSize = textSize.toFloat()
            val em = v.textSize
            // menší okraj nad a pod textem (od 0.97.138, přání majitele – řádek úkolu jde níž)
            v.includeFontPadding = false
            v.setPadding((em * 0.6f).toInt(), 0, (em * 0.6f).toInt(), (em * 0.04f).toInt())
            v.text = shown
            v.bidiFor(shown)
            v.measure(View.MeasureSpec.makeMeasureSpec((screenW * 0.9f).toInt(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val w = v.measuredWidth; val h = v.measuredHeight
            val cx = (box[0] + box[2]) / 2f * screenW; val cy = (box[1] + box[3]) / 2f * screenH
            lp.width = w
            lp.x = (cx - w / 2f).toInt().coerceIn(0, maxOf(0, screenW - w))
            lp.y = (cy - h / 2f).toInt().coerceIn(0, maxOf(0, screenH - h))
            v.visibility = View.VISIBLE
            try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
        }
    }

    /**
     * Ukáže přeložený nadpis a popis vybrané volby Nastavení hry pod anglickým popisem: [box] = vlevo, nahoře, vpravo,
     * nejníž dole (zlomky obrazu; text od levého horního rohu, zalomený na šířku boxu, písmo se zmenší, když se nevejde), [lineHeight] = výška řádku popisu ve hře
     * (zlomek výšky; písmo podle něj, nejvýš jako titulky). Pozadí aspoň 80 %, ať anglický text pod ním neplete.
     */
    fun showMenuDesc(title: String?, text: String?, box: FloatArray, lineHeight: Float, white: Boolean = false,
                     titleBox: FloatArray? = null, titleLine: Float = 0f,
                     list: List<String> = emptyList(), listBox: FloatArray? = null, listStep: Float = 0f) {
        val tvMax = TvLook.textPx(textSize, 0)
        val tvPx = if (lineHeight > 0f) Math.round(lineHeight * TvLook.SCREEN_H * 1.1f).coerceIn(22, tvMax.coerceAtLeast(22)) else tvMax
        // nadpis zvlášť nad anglickým nadpisem (od 0.97.102, přání majitele: nadpis nad nadpis, popis pod popis, jiná velikost)
        val apart = titleBox != null && !title.isNullOrBlank()
        val tvTitlePx = if (titleLine > 0f) Math.round(titleLine * TvLook.SCREEN_H * 1.15f).coerceIn(tvPx + 4, maxOf(tvPx + 4, (tvMax * 1.3f).toInt())) else tvPx + 6
        // panel telefonu (od 0.97.95) bíle jako texty hry, ne barvou titulků (žlutá bez pozadí byla nečitelná)
        onMenuDesc?.invoke(TvSender.menuDescMessage(title, text, box, tvPx, cz.recaption.core.GameMenu.SETTINGS_BG, if (white) "#FFFFFF" else cz.recaption.core.GameMenu.TINT_HEX,
            if (apart) titleBox else null, tvTitlePx, list, listBox, Math.round(listStep * TvLook.SCREEN_H)))
        showMenuTitle(if (apart) title else null, titleBox, titleLine, white)
        showMenuItems(list, listBox, listStep, white)
        val sb = SpannableStringBuilder()
        if (!title.isNullOrBlank() && !apart) {
            sb.append(title)
            sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (!text.isNullOrBlank()) { if (sb.isNotEmpty()) sb.append('\n'); sb.append(text) }
        main.post {
            val v = descView ?: return@post
            val lp = descParams ?: return@post
            val maxPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat(), host.resources.displayMetrics)
            val px = if (lineHeight > 0f) (lineHeight * screenH * 1.1f).coerceIn(0.02f * screenH, maxOf(maxPx, 0.02f * screenH)) else maxPx
            v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
            v.setTextColor(if (white) Color.WHITE else cz.recaption.core.GameMenu.TINT)   // menu hry světle žlutě (od 1.0.1)
            val em = v.textSize
            v.setPadding((em * 0.5f).toInt(), (em * 0.3f).toInt(), (em * 0.5f).toInt(), (em * 0.35f).toInt())
            v.text = sb
            v.bidiFor(sb)
            val w = ((box[2] - box[0]) * screenW).toInt().coerceIn(dp(120), screenW)
            lp.width = w
            lp.x = (box[0] * screenW).toInt().coerceIn(0, maxOf(0, screenW - w))
            // od 0.97.107 stálé písmo (dřív se u delšího popisu zmenšovalo a velikost „skákala“ volbu od volby – tester TUAT-DWY4);
            // když se pod anglický popis nevejde, okno jde níž, nejvýš k 96 % výšky, a teprve pak nahoru
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            lp.y = (box[1] * screenH).toInt().coerceIn(0, maxOf(0, (screenH * 0.96f).toInt() - v.measuredHeight))
            v.visibility = View.VISIBLE
            try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
        }
    }

    /** Okna menu vpravo dole – jedno na položku, vytvoří se až při prvním zobrazení (s vypnutou volbou žádná). */
    private val cornerViews = ArrayList<TextView>()
    private val cornerParams = ArrayList<WindowManager.LayoutParams>()
    @Volatile private var cornerShown = false

    /**
     * Přeložené menu vpravo dole ([cz.recaption.core.CornerPrompts]): [lines] = text každé položky (překlad + „1/2/3“)
     * a šedá, [mode] 1 = nad položkami (pozadí aspoň 60 %), 2 = přes položky (aspoň 85 %). Každý řádek zarovnaný doprava
     * ve vlastním okně ([cz.recaption.core.CornerPrompts.boxes]); písmo ~62 % řádku ve hře.
     * U „nad položkami“ za překladem ikona tlačítka jako ve hře ([cz.recaption.core.CornerItem.icon], [ButtonIconSpan]);
     * u „přes položky“ je hned vedle vidět ikona hry.
     */
    fun showCorner(lines: List<Pair<String, Boolean>>, view: cz.recaption.core.CornerView, mode: Int) {
        val boxes = cz.recaption.core.CornerPrompts.boxes(view, mode)
        if (lines.isEmpty() || boxes.size != lines.size) { hideCorner(); return }
        val opacity = maxOf(opacityPercent, if (mode == 1) 60 else 85)
        val hFrac = cz.recaption.core.CornerPrompts.textHeight(view)
        val icons = cz.recaption.core.CornerPrompts.icons(view, mode)
        onCorner?.invoke(TvSender.cornerMessage(mode, lines, boxes, Math.round(hFrac * TvLook.SCREEN_H), opacity, cz.recaption.core.GameMenu.TINT_HEX, icons))
        cornerShown = true
        val alpha = (255 * opacity / 100).coerceIn(0, 255)
        main.post {
            while (cornerViews.size < lines.size && cornerViews.size < cz.recaption.core.CornerPrompts.MAX_ITEMS) {
                val v = TextView(host).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    // užší písmo jako originál ovládání ve hře (1.0.3, hlášení DSXW-MJSD: „moc velké písmo“)
                    typeface = Typeface.create("sans-serif-condensed", if (bold) Typeface.BOLD else Typeface.NORMAL)
                    letterSpacing = -0.02f
                    setShadowLayer(3f, 0f, 1f, Color.BLACK)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                    visibility = View.GONE
                }
                val lp = params(WindowManager.LayoutParams.WRAP_CONTENT)
                if (!cornerLayer.add(v, lp)) break
                cornerViews += v; cornerParams += lp
            }
            val px = maxOf(hFrac * screenH, 10f)
            for (i in cornerViews.indices) {
                val v = cornerViews[i]; val lp = cornerParams[i]
                val line = lines.getOrNull(i); val b = boxes.getOrNull(i)
                if (line == null || b == null) { v.visibility = View.GONE; continue }
                v.background = GradientDrawable().apply { setColor(Color.argb(alpha, 0, 0, 0)); cornerRadius = dp(4).toFloat() }
                // ovládání vpravo dole světle žlutě jako překlad menu (od 1.0.1, přání majitele)
                v.setTextColor(if (line.second) cz.recaption.core.GameMenu.TINT_DIM else cz.recaption.core.GameMenu.TINT)
                v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
                val icon = icons.getOrNull(i)
                v.setPadding((px * 0.35f).toInt(), 0, (px * (if (icon != null) 0.12f else 0.35f)).toInt(), 0)
                v.text = if (icon == null) line.first else SpannableStringBuilder(line.first).append("\uFFFC",
                    ButtonIconSpan(icon.split(' '), line.second), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                v.bidiFor(line.first)
                val right = b[2] * screenW
                val maxW = maxOf(1, (right - cz.recaption.core.CornerPrompts.LEFT_LIMIT * screenW).toInt())
                val minW = ((b[2] - b[0]) * screenW).toInt().coerceIn(0, maxW)
                v.measure(View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                val w = v.measuredWidth.coerceIn(minW, maxW)
                val hh = maxOf(1, ((b[3] - b[1]) * screenH).toInt())
                lp.width = w; lp.height = hh
                lp.x = (right - w).toInt().coerceIn(0, maxOf(0, screenW - w))
                lp.y = (b[1] * screenH).toInt().coerceIn(0, maxOf(0, screenH - hh))
                v.visibility = View.VISIBLE
            }
            cornerLayer.update()
        }
    }

    private val worldViews = ArrayList<TextView>()
    private val worldParams = ArrayList<WindowManager.LayoutParams>()
    @Volatile private var worldShown = false

    /**
     * Pokyny u předmětů (od 0.97.88): překlad přes anglický text („OTEVŘÍT DVEŘE“ přes „OPEN DOOR“, ikona tlačítka zůstane
     * vidět), od levého okraje textu, stejně vysoký jako originál, verzálkami jako ve hře. Okno na pokyn až při prvním zobrazení.
     */
    fun showWorld(items: List<Pair<String, cz.recaption.core.WorldPrompt>>) {
        if (items.isEmpty()) { hideWorld(); return }
        val opacity = maxOf(opacityPercent, 85)
        // rámeček z OCR je o kus vyšší než písmo – řádek pruhu o 30 % vyšší, písmo ~80 % rámečku
        fun box(p: cz.recaption.core.WorldPrompt): FloatArray {
            val hh = (p.bottom - p.top) * 1.3f
            val cy = (p.top + p.bottom) / 2f
            return floatArrayOf(p.left, cy - hh / 2f, p.right, cy + hh / 2f)
        }
        val boxes = items.map { box(it.second) }
        // písmo o kus menší než rámeček (od 0.97.124, majitel: „trochu menším písmem“); u víceřádkového rámečku
        // (náhled SMS, PhoneNotice.sms) se bere výška jednoho řádku, ne celé bubliny (1.0.3, hlášení 845E-5DJ3)
        val fontFrac = items.map { val w = it.second; (if (w.lineH > 0f) w.lineH else w.bottom - w.top) * 0.68f }.average().toFloat()
        onWorld?.invoke(TvSender.worldMessage(items.indices.map { items[it].first to boxes[it] },
            Math.round(fontFrac * TvLook.SCREEN_H), opacity, "#FFFFFF"))
        worldShown = true
        val alpha = (255 * opacity / 100).coerceIn(0, 255)
        main.post {
            while (worldViews.size < items.size && worldViews.size < cz.recaption.core.WorldPrompts.MAX + 1) {
                val v = TextView(host).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    setShadowLayer(3f, 0f, 1f, Color.BLACK)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                    visibility = View.GONE
                }
                val lp = params(WindowManager.LayoutParams.WRAP_CONTENT)
                if (!worldLayer.add(v, lp)) break
                worldViews += v; worldParams += lp
            }
            val px = maxOf(fontFrac * screenH, 10f)
            for (i in worldViews.indices) {
                val v = worldViews[i]; val lp = worldParams[i]
                val item = items.getOrNull(i); val b = boxes.getOrNull(i)
                if (item == null || b == null) { v.visibility = View.GONE; continue }
                v.background = GradientDrawable().apply { setColor(Color.argb(alpha, 0, 0, 0)); cornerRadius = dp(4).toFloat() }
                v.setTextColor(Color.WHITE)
                v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
                v.setPadding((px * 0.35f).toInt(), 0, (px * 0.35f).toInt(), 0)
                v.text = item.first
                v.bidiFor(item.first)
                val left = (b[0] * screenW).toInt()
                val maxW = maxOf(1, (screenW * 0.97f).toInt() - left)
                val minW = ((b[2] - b[0]) * screenW).toInt().coerceIn(0, maxW)
                v.measure(View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                lp.width = v.measuredWidth.coerceIn(minW, maxW)
                lp.height = maxOf(1, ((b[3] - b[1]) * screenH).toInt())
                lp.x = left.coerceIn(0, maxOf(0, screenW - lp.width))
                lp.y = (b[1] * screenH).toInt().coerceIn(0, maxOf(0, screenH - lp.height))
                v.visibility = View.VISIBLE
            }
            worldLayer.update()
        }
    }

    private var phoneView: LinearLayout? = null
    private var phoneParams: WindowManager.LayoutParams? = null
    @Volatile private var phoneShown = false
    /** Starší aplikace v TV panel telefonu nezná – dostala obyčejný popis (`menupopis`), schovává se stejně. */
    @Volatile private var phoneAsDesc = false

    /**
     * Panel telefonu (od 0.97.96, přání majitele): jako malý telefon – tmavý zaoblený rámeček, nahoře jméno, příchozí
     * zprávy šedě vlevo, vlastní modře vpravo, ostatní text bez bubliny; písmo menší než titulky (~2 % výšky obrazu jako
     * v telefonu ve hře). Co se nevejde: písmo menší (nejvýš na 70 %), pak pryč nejstarší zprávy (nejnovější dole).
     * TV LG/Samsung od 1.5.22 stejně (`telefon`), starší dostane prostý text přes `menupopis`.
     */
    /** Posun překladu telefonu za telefonem ve hře (zlomky obrazu, [movePhone]); místa z čtení jsou bez něj. */
    @Volatile private var phoneDx = 0f
    @Volatile private var phoneDy = 0f
    /** Přeložení ukázaného překladu telefonu na posunuté místo (hlavní vlákno) a znovu poslání do TV. */
    private var phonePlace: (() -> Unit)? = null
    @Volatile private var phoneResend: ((Float, Float) -> Unit)? = null
    private var phoneTvAt = 0L
    private var phoneTvDx = 0f
    private var phoneTvDy = 0f

    private fun shifted(b: FloatArray, dx: Float = phoneDx, dy: Float = phoneDy) = floatArrayOf(b[0] + dx, b[1] + dy, b[2] + dx, b[3] + dy)

    /** Posun pro příští [showPhone] / [showPhoneOver] (služba ho nastaví před novým překladem, ať nic neskočí). */
    fun phoneShift(dx: Float, dy: Float) { phoneDx = dx; phoneDy = dy; phoneTvDx = dx; phoneTvDy = dy }

    /**
     * Telefon ve hře se pohnul (od 1.00-rc21, chůze / pohupování v autě): ukázaný překlad se posune bez nového čtení
     * a překladu; do TV nejvýš ~3× za sekundu a jen při posunu aspoň o [TV_MOVE] (zpráva se v TV skládá znovu).
     */
    fun movePhone(dx: Float, dy: Float) {
        if (!phoneShown) return
        val moved = dx != phoneDx || dy != phoneDy
        phoneDx = dx; phoneDy = dy
        if (moved) main.post { phonePlace?.invoke() }
        val now = android.os.SystemClock.uptimeMillis()
        val far = maxOf(kotlin.math.abs(dx - phoneTvDx), kotlin.math.abs(dy - phoneTvDy))
        if ((far >= TV_MOVE || (far >= TV_MOVE / 4 && now - phoneTvAt >= 1000)) && now - phoneTvAt >= 300) {
            phoneTvAt = now; phoneTvDx = dx; phoneTvDy = dy
            phoneResend?.invoke(dx, dy)
        }
    }

    /** [free] = panel na volné straně obrazu ([cz.recaption.core.PhoneFreeSide]) – do TV `volna`, přesun na druhou stranu dojede plynule. */
    /**
     * Bubliny překladu telefonu (0xRRGGBB z profilu hry, [cz.recaption.core.GameProfile.phoneOwn]): od 1.0.10 modré
     * (koordinátor: „telefon celý modře, už nikdy zpět na růžovou“ – příchozí světle šedá F2F0F2 působila do růžova),
     * průhledné jako menu. 0 = výchozí z [cz.recaption.core.PhoneScreen].
     */
    @Volatile private var phoneOwnRgb = cz.recaption.core.PhoneScreen.OWN_COLOR
    @Volatile private var phoneInRgb = cz.recaption.core.PhoneScreen.IN_COLOR
    private val PHONE_OWN_BUBBLE get() = bubble(phoneOwnRgb)
    private val PHONE_IN_BUBBLE get() = bubble(phoneInRgb)

    fun phoneColors(own: Int, incoming: Int) {
        phoneOwnRgb = if (own != 0) own else cz.recaption.core.PhoneScreen.OWN_COLOR
        phoneInRgb = if (incoming != 0) incoming else cz.recaption.core.PhoneScreen.IN_COLOR
    }

    fun showPhone(title: String?, msgs: List<Pair<String, Char>>, box: FloatArray, images: List<android.graphics.Bitmap?> = emptyList(),
                  free: Boolean = false) {
        if (msgs.isEmpty()) { hidePhone(); return }
        hidePhoneOver()
        // průhledný panel (od 0.97.110, přání majitele: „dá se přes to i řídit“); od 0.97.114 průhlednější a průsvitné bubliny –
        // konverzace SMS zabírá velkou část obrazu (dávka 4KHZ-AJKC); od 1.00-rc21 průhledný jako ostatní překlady
        // ([cz.recaption.core.GameMenu.SETTINGS_BG], přání majitele: „chtěl jsem průhlednější smsky“)
        val opacity = cz.recaption.core.GameMenu.SETTINGS_BG
        val fontFrac = 0.021f
        val tvVersion = TvSender.lastSeen?.announce?.version
        phoneResend = null
        if (onPhone != null) {
            if (tvVersion == null || TvSender.atLeast(tvVersion, TvSender.PHONE_TV)) {
                phoneAsDesc = false
                // obrázky jen pro aplikaci v TV, která je umí (1.5.24); JPEG zmenšený, dohromady nejvýš ~45 kB
                val pics = if (tvVersion != null && TvSender.atLeast(tvVersion, TvSender.PHONE_IMAGE_TV)) TvPhoneImages.encode(images) else emptyList()
                val send = { dx: Float, dy: Float ->
                    onPhone.invoke(TvSender.phoneMessage(title, msgs, shifted(box, dx, dy), Math.round(fontFrac * TvLook.SCREEN_H), opacity, pics, free,
                        intArrayOf(phoneOwnRgb, phoneInRgb))) }
                send(phoneDx, phoneDy)
                phoneResend = send
            } else {
                phoneAsDesc = true
                onMenuDesc?.invoke(TvSender.menuDescMessage(title, msgs.joinToString("\n\n") { if (it.second == 'c') "———" else (if (it.second == 'p') "» " else "") + it.first },
                    box, Math.round(fontFrac * TvLook.SCREEN_H), opacity, "#FFFFFF"))
            }
        }
        phoneShown = true
        main.post {
            var root = phoneView
            var lp = phoneParams
            if (root == null || lp == null) {
                root = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
                lp = params(WindowManager.LayoutParams.WRAP_CONTENT)
                if (!add(root, lp)) return@post
                phoneView = root; phoneParams = lp
            }
            val w = ((box[2] - box[0]) * screenW).toInt().coerceIn(dp(140), screenW)
            val room = ((box[3] - box[1]) * screenH).toInt().coerceAtLeast(dp(80))
            val alpha = (255 * opacity / 100).coerceIn(0, 255)
            fun build(px: Float, from: Int) {
                root.removeAllViews()
                val pad = (px * 0.6f).toInt()
                root.setPadding(pad, pad, pad, pad)
                root.background = GradientDrawable().apply {
                    setColor(Color.argb(alpha, 18, 20, 26)); cornerRadius = px * 0.9f
                    setStroke(maxOf(1, dp(1)), Color.argb(40, 255, 255, 255))
                }
                if (!title.isNullOrBlank()) root.addView(TextView(host).apply {
                    text = title; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); gravity = Gravity.CENTER
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, 0, 0, (px * 0.4f).toInt())
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                for (i in from until msgs.size) {
                    val (t, side) = msgs[i]
                    // čára místo obrázku (příspěvek, fotka) – tenká světlá linka
                    if (side == 'c') {
                        if (i == from) continue
                        // obrázek ze hry (fotka, příspěvek) zmenšený na šířku panelu; bez něj tenká čára
                        val pic = images.getOrNull(i)?.takeIf { !it.isRecycled }
                        if (pic != null) {
                            val iw = w - root.paddingLeft - root.paddingRight
                            val ih = minOf((iw.toLong() * pic.height / maxOf(1, pic.width)).toInt(), (room * 0.4f).toInt())
                            root.addView(android.widget.ImageView(host).apply {
                                setImageBitmap(pic); scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxOf(1, ih)).apply { topMargin = (px * 0.4f).toInt() })
                            continue
                        }
                        root.addView(View(host).apply { setBackgroundColor(Color.argb(56, 255, 255, 255)) },
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxOf(1, dp(1))).apply {
                                topMargin = (px * 0.6f).toInt(); leftMargin = (w * 0.08f).toInt(); rightMargin = (w * 0.08f).toInt()
                            })
                        continue
                    }
                    // bublina jen s emoji (od 1.00-rc21 – čtečka emoji nepřečte): obrázek ze hry v průhledné bublině
                    val emoji = images.getOrNull(i)?.takeIf { t.isBlank() && !it.isRecycled }
                    if (emoji != null) {
                        val ih = (px * 1.6f).toInt()
                        val iw = (ih.toLong() * emoji.width / maxOf(1, emoji.height)).toInt().coerceIn(1, (w * 0.6f).toInt())
                        val r = px * 0.8f; val small = px * 0.2f
                        root.addView(android.widget.ImageView(host).apply {
                            setImageBitmap(emoji); scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                            background = GradientDrawable().apply {
                                setColor(if (side == 'p') PHONE_OWN_BUBBLE else PHONE_IN_BUBBLE)
                                cornerRadii = if (side == 'p') floatArrayOf(r, r, r, r, small, small, r, r) else floatArrayOf(r, r, r, r, r, r, small, small)
                            }
                            setPadding((px * 0.3f).toInt(), (px * 0.2f).toInt(), (px * 0.3f).toInt(), (px * 0.2f).toInt())
                        }, LinearLayout.LayoutParams(iw + (px * 0.6f).toInt(), ih + (px * 0.4f).toInt()).apply {
                            topMargin = (px * 0.4f).toInt(); gravity = if (side == 'p') Gravity.END else Gravity.START
                        })
                        continue
                    }
                    val tv = TextView(host).apply {
                        text = t; setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
                        bidiFor(t)
                        // barvy jako telefon ve hře (od 0.97.114): příchozí světle šedá, vlastní modrá. Od 1.00-rc21 (přání majitele:
                        // modré bubliny byly tmavší než originál a neprůhledné) světle modrá jako ve hře (změřeno v dávce 6RKU-RZW3 –
                        // displej ztlumený na ~60 %, převedeno na plný jas) a obě bubliny průhledné jako ostatní překlady
                        // ([cz.recaption.core.GameMenu.SETTINGS_BG]); přes průhlednou bublinu je čitelné jen světlé písmo se stínem
                        setTextColor(if (side == 's') Color.rgb(0xE6, 0xE8, 0xEC) else Color.WHITE)
                        setShadowLayer(px * 0.15f, 0f, 0f, Color.BLACK)
                        if (side != 's') {
                            val r = px * 0.8f; val small = px * 0.2f
                            background = GradientDrawable().apply {
                                setColor(if (side == 'p') PHONE_OWN_BUBBLE else PHONE_IN_BUBBLE)
                                cornerRadii = if (side == 'p') floatArrayOf(r, r, r, r, small, small, r, r) else floatArrayOf(r, r, r, r, r, r, small, small)
                            }
                            setPadding((px * 0.6f).toInt(), (px * 0.35f).toInt(), (px * 0.6f).toInt(), (px * 0.35f).toInt())
                        }
                    }
                    val blp = LinearLayout.LayoutParams(if (side == 's') LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = (px * 0.4f).toInt()
                        gravity = if (side == 'p') Gravity.END else Gravity.START
                        if (side == 'p') leftMargin = (w * 0.18f).toInt() else if (side == 'l') rightMargin = (w * 0.18f).toInt()
                    }
                    root.addView(tv, blp)
                }
                root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            }
            val base = maxOf(fontFrac * screenH, 9f * density)
            var px = base
            var from = 0
            build(px, from)
            while (root.measuredHeight > room) {
                if (px > base * 0.72f) px *= 0.9f
                else if (from < msgs.size - 1) from++
                else break
                build(px, from)
            }
            lp.width = w
            // delší překlad poslední (nejnovější) zprávy se do "room" (výška z originálu) nemusí vejít ani s nejmenším
            // písmem – dřív se okno prostě oříznulo na "room" a kus zprávy zmizel (majitel: "poslední zpráva dole
            // má v originále vidět celou, u nás jí půlka chybí"); teď smí být o kus vyšší, posune se nahoru ([place])
            lp.height = root.measuredHeight.coerceAtMost((screenH * 0.96f).toInt())
            val place = {
                lp.x = ((box[0] + phoneDx) * screenW).toInt().coerceIn(0, maxOf(0, screenW - w))
                lp.y = ((box[1] + phoneDy) * screenH).toInt().coerceIn(0, maxOf(0, screenH - lp.height))
                try { wm.updateViewLayout(root, lp) } catch (_: Exception) {}
            }
            root.visibility = View.VISIBLE
            place()
            phonePlace = place
        }
    }

    fun hidePhone() {
        if (!phoneShown) return
        phoneShown = false
        phoneResend = null
        if (phoneAsDesc) onMenuDesc?.invoke(TvSender.menuDescMessage(null, null)) else onPhone?.invoke(TvSender.PHONE_HIDE)
        main.post { phoneView?.visibility = View.GONE; phoneOverViews.forEach { it.visibility = View.GONE }; phoneOverLayer.update() }
    }

    /** Okna překladu přímo přes text v telefonu (od 0.97.110) – jedno na odstavec, vytvoří se až při prvním zobrazení. */
    private val phoneOverViews = ArrayList<TextView>()
    private val phoneOverParams = ArrayList<WindowManager.LayoutParams>()

    private fun hidePhoneOver() { main.post { phoneOverViews.forEach { it.visibility = View.GONE }; phoneOverLayer.update() } }

    /**
     * Překlad telefonu přímo přes původní text (volba „přes text v telefonu“, od 0.97.110): každý odstavec na místě
     * originálu ([items] = text a obdélník ve zlomcích obrazu), světlé pozadí a tmavé písmo jako v telefonu ve hře, písmo
     * podle výšky řádku [lineH]. Do TV od aplikace 1.5.28 (`rezim":"pres"`), starší TV dostane panel vedle ([sideBox]).
     */
    fun showPhoneOver(items: List<Pair<String, FloatArray>>, lineH: Float, sideBox: FloatArray, beside: Boolean = false) {
        if (items.isEmpty()) { hidePhone(); return }
        val tvVersion = TvSender.lastSeen?.announce?.version
        // náhledy vedle telefonu větším písmem než řádek v telefonu (majitel 24. 11. 2026: „seznam sms má trochu malé písmo“)
        val grow = if (beside) PHONE_BESIDE_GROW else 1f
        val tvPx = Math.round((if (lineH > 0f) lineH else 0.021f) * TvLook.SCREEN_H * 0.95f * grow).coerceIn(14, 60)
        phoneResend = null
        if (onPhone != null) {
            phoneAsDesc = false
            val send = { dx: Float, dy: Float ->
                onPhone.invoke(if (tvVersion != null && TvSender.atLeast(tvVersion, TvSender.PHONE_OVER_TV))
                    TvSender.phoneOverMessage(items.map { it.first to shifted(it.second, dx, dy) }, tvPx, beside)
                    else TvSender.phoneMessage(null, items.map { it.first to 's' }, shifted(sideBox, dx, dy), Math.round(0.021f * TvLook.SCREEN_H), 80))
            }
            send(phoneDx, phoneDy)
            phoneResend = send
        }
        phoneShown = true
        main.post {
            phoneView?.visibility = View.GONE
            while (phoneOverViews.size < items.size && phoneOverViews.size < 10) {
                val v = TextView(host).apply {
                    setTextColor(Color.rgb(0x16, 0x16, 0x18))
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    gravity = Gravity.START or Gravity.TOP
                    background = GradientDrawable().apply { setColor(Color.rgb(0xF4, 0xF4, 0xF6)); cornerRadius = dp(3).toFloat() }
                    visibility = View.GONE
                }
                val lp = params(WindowManager.LayoutParams.WRAP_CONTENT)
                if (!phoneOverLayer.add(v, lp)) break
                phoneOverViews += v; phoneOverParams += lp
            }
            val px = ((if (lineH > 0f) lineH else 0.021f) * screenH * 0.95f * grow).coerceAtLeast(8f)
            // posun za telefonem ([movePhone]) – jen místa oken, text zůstává
            phonePlace = {
                for (i in phoneOverViews.indices) {
                    val b = items.getOrNull(i)?.second ?: continue
                    val lp = phoneOverParams[i]
                    lp.x = ((b[0] - 0.006f + phoneDx) * screenW).toInt().coerceIn(0, maxOf(0, screenW - lp.width))
                    lp.y = ((b[1] - 0.004f + phoneDy) * screenH).toInt().coerceAtLeast(0)
                }
                phoneOverLayer.update()
            }
            for (i in phoneOverViews.indices) {
                val v = phoneOverViews[i]
                val it = items.getOrNull(i)
                if (it == null) { v.visibility = View.GONE; continue }
                val b = it.second
                val lp = phoneOverParams[i]
                v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
                v.setPadding((px * 0.15f).toInt(), 0, (px * 0.15f).toInt(), (px * 0.1f).toInt())
                v.text = it.first
                v.bidiFor(it.first)
                // vedle telefonu (náhledy seznamu zpráv, od 1.00-rc21): průhledné jako ostatní překlady, bílé písmo se stínem;
                // přes text v telefonu světlé neprůhledné, ať originál zakryje
                if (beside) {
                    v.setTextColor(Color.WHITE); v.setShadowLayer(px * 0.15f, 0f, 0f, Color.BLACK)
                    v.background = GradientDrawable().apply { setColor(MENU_BG_BESIDE); cornerRadius = dp(3).toFloat() }
                } else {
                    v.setTextColor(Color.rgb(0x16, 0x16, 0x18)); v.setShadowLayer(0f, 0f, 0f, 0)
                    v.background = GradientDrawable().apply { setColor(Color.rgb(0xF4, 0xF4, 0xF6)); cornerRadius = dp(3).toFloat() }
                }
                val w = (((b[2] - b[0]) + 0.012f) * screenW).toInt().coerceIn(dp(40), screenW)
                lp.width = w
                lp.x = ((b[0] - 0.006f + phoneDx) * screenW).toInt().coerceIn(0, maxOf(0, screenW - w))
                lp.y = ((b[1] - 0.004f + phoneDy) * screenH).toInt().coerceAtLeast(0)
                v.visibility = View.VISIBLE
            }
            phoneOverLayer.update()
        }
    }

    fun hideWorld() {
        if (!worldShown) return
        worldShown = false
        onWorld?.invoke(TvSender.WORLD_HIDE)
        main.post { worldViews.forEach { it.visibility = View.GONE }; worldLayer.update() }
    }

    fun hideCorner() {
        if (!cornerShown) return
        cornerShown = false
        onCorner?.invoke(TvSender.CORNER_HIDE)
        main.post { cornerViews.forEach { it.visibility = View.GONE }; cornerLayer.update() }
    }

    fun hideMenuDesc() {
        onMenuDesc?.invoke(TvSender.menuDescMessage(null, null))
        main.post { descView?.visibility = View.GONE; titleView?.visibility = View.GONE; listView?.visibility = View.GONE }
    }

    /** Překlad tlačítek v patičce Nastavení – okno na tlačítko (od 0.97.111). */
    private val footViews = ArrayList<TextView>()
    private val footParams = ArrayList<WindowManager.LayoutParams>()
    @Volatile private var footShown = false

    /**
     * Tlačítka v patičce Nastavení hry (RESTORE ALL △ …): překlad přímo přes anglický název, zarovnaný doprava k jeho konci
     * (ikona hry vpravo zůstane vidět), písmo podle výšky názvu. Do TV jako menu vpravo dole přes položky (`roh`, `pres`).
     */
    fun showMenuFooter(items: List<Pair<String, FloatArray>>) {
        if (items.isEmpty()) { hideMenuFooter(); return }
        val hFrac = items.map { it.second[3] - it.second[1] }.sorted()[items.size / 2]
        val boxes = items.map { (_, b) -> floatArrayOf((b[0] - 0.004f).coerceAtLeast(0f), b[1] - hFrac * 0.25f, minOf(1f, b[2] + 0.003f), b[3] + hFrac * 0.25f) }
        onCorner?.invoke(TvSender.cornerMessage(2, items.map { it.first to false }, boxes, Math.round(hFrac * TvLook.SCREEN_H * 1.05f), 92, cz.recaption.core.GameMenu.TINT_HEX))
        footShown = true
        main.post {
            while (footViews.size < items.size && footViews.size < 6) {
                val v = TextView(host).apply {
                    setTextColor(Color.WHITE)
                    typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply { setColor(Color.argb(235, 20, 12, 28)); cornerRadius = dp(3).toFloat() }
                    includeFontPadding = false
                    visibility = View.GONE
                }
                val lp = params(WindowManager.LayoutParams.WRAP_CONTENT)
                if (!footLayer.add(v, lp)) break
                footViews += v; footParams += lp
            }
            val px = (hFrac * screenH * 1.05f).coerceAtLeast(8f)
            for (i in footViews.indices) {
                val v = footViews[i]; val it = items.getOrNull(i)
                if (it == null) { v.visibility = View.GONE; continue }
                val b = boxes[i]
                val lp = footParams[i]
                v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
                v.setPadding((px * 0.2f).toInt(), 0, (px * 0.15f).toInt(), 0)
                v.text = it.first
                v.bidiFor(it.first)
                v.measure(View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                lp.width = WindowManager.LayoutParams.WRAP_CONTENT
                lp.x = ((b[2] * screenW).toInt() - v.measuredWidth).coerceAtLeast(0)
                lp.y = (((b[1] + b[3]) / 2f) * screenH - v.measuredHeight / 2f).toInt().coerceAtLeast(0)
                v.visibility = View.VISIBLE
            }
            footLayer.update()
        }
    }

    /** Popisky menu hry na svém místě (od 0.97.115): skupina 0 = položky / záložka, 1 = patička; okna se vytvoří až při prvním zobrazení. */
    private val labelViews = ArrayList<TextView>()
    private val labelParams = ArrayList<WindowManager.LayoutParams>()
    private val labelGroups = arrayOf<List<cz.recaption.core.MenuLabel>>(emptyList(), emptyList())
    /** Ikony položek menu ze hry (bílé na průhledném) a tytéž jako PNG data: adresa pro TV – klíč = název položky (od 0.97.121). */
    @Volatile var menuIcons: Map<String, android.graphics.Bitmap> = emptyMap()
    @Volatile var menuIconUris: Map<String, String> = emptyMap()

    /**
     * Přeložené popisky menu hry přímo u originálu ([cz.recaption.core.GameMenu.pauseLabels], [tabLabel][cz.recaption.core.GameMenu.tabLabel],
     * [footerLabels][cz.recaption.core.GameMenu.footerLabels]): text v obdélníku, zarovnaný vlevo nebo vpravo; vybraná položka menu
     * černě bez pozadí (bílý pruh hry), ostatní bíle se stínem, případně na pozadí položky. Do TV jako `menupolozky` (obě skupiny).
     */
    fun showMenuLabels(group: Int, items: List<cz.recaption.core.MenuLabel>) {
        val all = synchronized(labelGroups) {
            labelGroups[group.coerceIn(0, 1)] = items
            labelGroups[0] + labelGroups[1]
        }
        onMenuLabels?.invoke(TvSender.menuLabelsMessage(all, menuIconUris))
        val icons = menuIcons
        main.post {
            while (labelViews.size < all.size && labelViews.size < 32) {
                val v = TextView(host).apply {
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    includeFontPadding = false
                    maxLines = 1
                    visibility = View.GONE
                }
                val lp = params(WindowManager.LayoutParams.WRAP_CONTENT)
                if (!labelLayer.add(v, lp)) break
                labelViews += v; labelParams += lp
            }
            for (i in labelViews.indices) {
                val v = labelViews[i]; val it = all.getOrNull(i)
                if (it == null) { v.visibility = View.GONE; continue }
                val b = it.box
                val lp = labelParams[i]
                val px = (it.px / 1080f * screenH).coerceAtLeast(8f)
                v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
                // patička: za každým textem ikona tlačítka jako ve hře (od 0.97.124)
                v.text = if (it.parts.isEmpty()) it.text else SpannableStringBuilder().also { sb ->
                    it.parts.forEachIndexed { k, (pt, ic) ->
                        if (k > 0) sb.append("   ")
                        sb.append(pt)
                        if (ic != null) sb.append("\uFFFC", ButtonIconSpan(listOf(ic), false), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                if (it.selected) { v.setTextColor(Color.BLACK); v.setShadowLayer(0f, 0f, 0f, 0) }
                else { v.setTextColor(cz.recaption.core.GameMenu.TINT); v.setShadowLayer(px * 0.22f, 0f, px * 0.05f, Color.BLACK) }
                // pozadí: černé, u vybrané (záložka Nastavení) bílé s černým písmem jako ve hře; vybraná bez pozadí leží v bílém pruhu hry
                v.background = if (it.bg > 0) GradientDrawable().apply {
                    val a = (255 * it.bg / 100).coerceIn(0, 255)
                    // vybraná (záložka Nastavení, Fotostudio, panel mise): černě na světle žlutém (od 1.0.1, majitel: „settings bar světle žlutou pozadí“)
                    setColor(if (it.selected) (cz.recaption.core.GameMenu.TINT and 0x00FFFFFF) or (a shl 24) else Color.argb(a, 0, 0, 0)); cornerRadius = dp(3).toFloat()
                } else null
                v.setPadding((px * 0.25f).toInt(), 0, (px * 0.25f).toInt(), 0)
                // ikona položky ze hry před textem (od 0.97.121), u vybrané černě jako ve hře
                val ic = it.icon?.let { k -> icons[k] }?.takeIf { b -> !b.isRecycled }
                if (ic != null) {
                    val d = android.graphics.drawable.BitmapDrawable(host.resources, ic)
                    val ih = (px * 0.95f).toInt().coerceAtLeast(6)
                    d.setBounds(0, 0, (ih.toLong() * ic.width / maxOf(1, ic.height)).toInt().coerceAtLeast(6), ih)
                    // bílá s černým lemem (od 0.97.123); černě jen u vybrané se stylem „černě“
                    d.colorFilter = if (it.selected) android.graphics.PorterDuffColorFilter(Color.BLACK, android.graphics.PorterDuff.Mode.SRC_IN) else null
                    v.setCompoundDrawables(d, null, null, null)
                    v.compoundDrawablePadding = (px * 0.4f).toInt()
                } else v.setCompoundDrawables(null, null, null, null)
                v.gravity = (if (it.right) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
                v.bidiFor(v.text)
                v.measure(View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                val w = v.measuredWidth
                lp.width = WindowManager.LayoutParams.WRAP_CONTENT
                lp.x = (if (it.right) (b[2] * screenW).toInt() - w else (b[0] * screenW).toInt()).coerceIn(0, maxOf(0, screenW - w))
                lp.y = (((b[1] + b[3]) / 2f) * screenH - v.measuredHeight / 2f).toInt().coerceAtLeast(0)
                v.visibility = View.VISIBLE
            }
            labelLayer.update()
        }
    }

    fun hideMenuLabels() {
        val had = synchronized(labelGroups) { val h = labelGroups.any { it.isNotEmpty() }; labelGroups[0] = emptyList(); labelGroups[1] = emptyList(); h }
        if (!had) return
        onMenuLabels?.invoke(TvSender.MENU_LABELS_HIDE)
        main.post { labelViews.forEach { it.visibility = View.GONE }; labelLayer.update() }
    }

    fun hideMenuFooter() {
        if (!footShown) return
        footShown = false
        onCorner?.invoke(TvSender.CORNER_HIDE)
        main.post { footViews.forEach { it.visibility = View.GONE }; footLayer.update() }
    }

    /** Seznam pod popisem volby (barvy) – položky pod sebou vpravo od originálu, řádek = rozestup ve hře (od 0.97.111). */
    @Volatile private var listView: TextView? = null
    @Volatile private var listParams: WindowManager.LayoutParams? = null

    private fun showMenuItems(items: List<String>, box: FloatArray?, step: Float, white: Boolean) {
        main.post {
            if (items.isEmpty() || box == null || box.size < 4 || step <= 0f) { listView?.visibility = View.GONE; return@post }
            var v = listView; var lp = listParams
            if (v == null || lp == null) {
                v = TextView(host).apply {
                    gravity = Gravity.START or Gravity.TOP
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setShadowLayer(3f, 0f, 1f, Color.BLACK)
                    background = GradientDrawable().apply { setColor(Color.argb(MENU_ALPHA, 0, 0, 0)); cornerRadius = dp(6).toFloat() }
                    includeFontPadding = false
                }
                lp = params(WindowManager.LayoutParams.WRAP_CONTENT)
                if (!add(v, lp)) return@post
                listView = v; listParams = lp
            }
            val stepPx = step * screenH
            val px = stepPx * 0.6f
            v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
            v.setTextColor(if (white) Color.WHITE else cz.recaption.core.GameMenu.TINT)   // menu hry světle žlutě (od 1.0.1)
            if (android.os.Build.VERSION.SDK_INT >= 28) v.lineHeight = stepPx.toInt().coerceAtLeast(1)
            else v.setLineSpacing(maxOf(0f, stepPx - v.paint.fontSpacing), 1f)
            v.setPadding((px * 0.4f).toInt(), 0, (px * 0.4f).toInt(), 0)
            v.text = items.joinToString("\n")
            v.bidiFor(v.text)
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT
            v.maxWidth = ((box[2] - box[0]) * screenW).toInt().coerceAtLeast(dp(80))
            lp.x = (box[0] * screenW).toInt().coerceIn(0, screenW - dp(60))
            lp.y = (box[1] * screenH).toInt().coerceAtLeast(0)
            v.visibility = View.VISIBLE
            try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
        }
    }

    /** Překlad nadpisu volby nad anglickým nadpisem (dole zarovnaný na [box] 3), písmo podle nadpisu ve hře; null = schovat. */
    private fun showMenuTitle(title: String?, box: FloatArray?, line: Float, white: Boolean) {
        main.post {
            val v = titleView ?: return@post
            val lp = titleParams ?: return@post
            if (title.isNullOrBlank() || box == null || box.size < 4) { v.visibility = View.GONE; return@post }
            val maxPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat(), host.resources.displayMetrics) * 1.3f
            val px = if (line > 0f) (line * screenH * 1.15f).coerceIn(0.025f * screenH, maxOf(maxPx, 0.025f * screenH)) else maxPx
            v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
            v.setTextColor(if (white) Color.WHITE else cz.recaption.core.GameMenu.TINT)   // menu hry světle žlutě (od 1.0.1)
            val em = v.textSize
            v.setPadding((em * 0.4f).toInt(), (em * 0.15f).toInt(), (em * 0.4f).toInt(), (em * 0.15f).toInt())
            v.text = title
            v.bidiFor(title)
            val w = ((box[2] - box[0]) * screenW).toInt().coerceIn(dp(120), screenW)
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT
            v.maxWidth = w
            lp.x = (box[0] * screenW).toInt().coerceIn(0, maxOf(0, screenW - dp(60)))
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            // dolní hrana těsně nad anglickým nadpisem; když se nevejde, aspoň horní hrana na horní hranu místa
            lp.y = maxOf((box[3] * screenH).toInt() - v.measuredHeight, (box[1] * screenH).toInt() - v.measuredHeight / 3).coerceAtLeast(0)
            v.visibility = View.VISIBLE
            try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
        }
    }

    fun hideObjective() {
        onObjective?.invoke(TvSender.objectiveMessage(null))
        main.post { objView?.visibility = View.GONE }
    }

    fun hide() {
        if (isShown && coverAll) coverArea.hidden(android.os.SystemClock.uptimeMillis())
        isShown = false
        onText?.invoke(null, null, null)
        main.post { bar?.visibility = View.GONE }
    }

    fun debug(text: String) {
        val dv = debugView ?: return
        main.post { dv.text = text }
    }

    fun destroy() {
        isShown = false
        onText?.invoke(null, null, null)
        onObjective?.invoke(TvSender.objectiveMessage(null))
        onMenuDesc?.invoke(TvSender.menuDescMessage(null, null))
        if (cornerShown) { cornerShown = false; onCorner?.invoke(TvSender.CORNER_HIDE) }
        if (worldShown) { worldShown = false; onWorld?.invoke(TvSender.WORLD_HIDE) }
        if (phoneShown) { phoneShown = false; onPhone?.invoke(TvSender.PHONE_HIDE) }
        main.post {
            listOfNotNull(bar, debugView, objView, descView, titleView, listView, phoneView).forEach { v -> OverlayWindows.remove(wm, v) }
            // skupiny popisků i s popisky menu (ty se do 1.0.00 při novém nastavení neodebíraly – okna přibývala až do pádu)
            listOf(cornerLayer, worldLayer, phoneOverLayer, footLayer, labelLayer).forEach { it.destroy() }
            footViews.clear(); footParams.clear()
            listView = null; listParams = null
            phoneOverViews.clear(); phoneOverParams.clear()
            phoneView = null; phoneParams = null
            cornerViews.clear(); cornerParams.clear(); worldViews.clear(); worldParams.clear()
            labelViews.clear(); labelParams.clear()
            bar = null; barParams = null; debugView = null; objView = null; objParams = null; descView = null; descParams = null; titleView = null; titleParams = null
        }
    }
}

/**
 * Ikona tlačítka ovladače za překladem (menu vpravo dole, [cz.recaption.core.ButtonIcons]): bílý kotouč se znakem
 * kresleným čarami (× ○ □ △ – nezávislé na písmu), L1 / R1 / L2 / R2 bílý zaoblený obdélník s textem; víc ikon za sebou.
 * Kotouč ~0,8 výšky písma, svisle na středu verzálek. [dim] = šedá položka (neaktivní).
 */
private class ButtonIconSpan(private val icons: List<String>, private val dim: Boolean) : android.text.style.ReplacementSpan() {
    private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL }
    private val line = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    private val label = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun shoulder(icon: String) = icon.length == 2 && !cz.recaption.core.ButtonIcons.stick(icon)
    private fun width(icon: String, d: Float) = if (shoulder(icon)) d * 1.6f else d

    override fun getSize(paint: android.graphics.Paint, text: CharSequence?, start: Int, end: Int, fm: android.graphics.Paint.FontMetricsInt?): Int {
        val d = paint.textSize * 1.05f   // od 1.0.01 větší, jako ikony ve hře (dřív 0,8)
        return Math.ceil(icons.sumOf { (paint.textSize * 0.4f + width(it, d)).toDouble() }).toInt()
    }

    override fun draw(canvas: android.graphics.Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int,
                      paint: android.graphics.Paint) {
        val d = paint.textSize * 1.05f   // od 1.0.01 větší, jako ikony ve hře (dřív 0,8)
        val cy = y - paint.textSize * 0.36f
        val dark = Color.rgb(0x1A, 0x1A, 0x1A)
        fill.color = if (dim) Color.rgb(0x8A, 0x8A, 0x8A) else Color.WHITE
        line.color = dark
        line.strokeWidth = maxOf(1.5f, d * 0.11f)
        var left = x
        for (icon in icons) {
            left += paint.textSize * 0.4f
            val w = width(icon, d)
            val cx = left + w / 2f
            if (shoulder(icon)) {
                val h = d * 0.84f
                canvas.drawRoundRect(android.graphics.RectF(left, cy - h / 2f, left + w, cy + h / 2f), h * 0.3f, h * 0.3f, fill)
                label.color = dark
                label.textSize = d * 0.58f
                canvas.drawText(icon, cx, cy + label.textSize * 0.36f, label)
            } else {
                canvas.drawCircle(cx, cy, d / 2f, fill)
                val r = d * 0.23f
                when (icon) {
                    "x" -> { canvas.drawLine(cx - r, cy - r, cx + r, cy + r, line); canvas.drawLine(cx - r, cy + r, cx + r, cy - r, line) }
                    "o" -> canvas.drawCircle(cx, cy, d * 0.26f, line)
                    "s" -> canvas.drawRect(cx - d * 0.235f, cy - d * 0.235f, cx + d * 0.235f, cy + d * 0.235f, line)
                    "t" -> canvas.drawPath(android.graphics.Path().apply {
                        moveTo(cx, cy - d * 0.27f); lineTo(cx + d * 0.26f, cy + d * 0.18f); lineTo(cx - d * 0.26f, cy + d * 0.18f); close()
                    }, line)
                    // páčka (od 1.00-rc21): písmeno L / R uprostřed a čtyři malé šipky u okraje jako ve hře
                    cz.recaption.core.ButtonIcons.LEFT_STICK, cz.recaption.core.ButtonIcons.RIGHT_STICK -> {
                        label.color = dark
                        label.textSize = d * 0.5f
                        canvas.drawText(icon.substring(0, 1), cx, cy + label.textSize * 0.36f, label)
                        fill.color = dark
                        val a = d * 0.42f; val s = d * 0.075f
                        for (k in 0 until 4) {
                            val (ux, uy) = when (k) { 0 -> 0f to -1f; 1 -> 1f to 0f; 2 -> 0f to 1f; else -> -1f to 0f }
                            val tx = cx + ux * a; val ty = cy + uy * a
                            canvas.drawPath(android.graphics.Path().apply {
                                moveTo(tx, ty)
                                lineTo(tx - ux * s * 1.4f - uy * s * 1.4f, ty - uy * s * 1.4f + ux * s * 1.4f)
                                lineTo(tx - ux * s * 1.4f + uy * s * 1.4f, ty - uy * s * 1.4f - ux * s * 1.4f)
                                close()
                            }, fill)
                        }
                        fill.color = if (dim) Color.rgb(0x8A, 0x8A, 0x8A) else Color.WHITE
                    }
                }
            }
            left += w
        }
    }
}

/**
 * Směr překladu v okně (hebrejština, od přípravy jazyků 23. 11. 2026): text s písmem psaným zprava doleva
 * ([cz.recaption.core.Bidi.hasRtl]) se kreslí po odstavcích zprava doleva (`TEXT_DIRECTION_ANY_RTL` – odstavec
 * s hebrejštinou celý zprava doleva i se jménem „LUCIA:“ a tečkou na správné straně, anglický originál pod ním
 * zůstane zleva). Okna zarovnaná k pravému okraji (END – menu vpravo dole, patička) zůstanou vpravo (RIGHT),
 * okna zarovnaná k začátku (START) mají hebrejštinu u pravého okraje vlastního okna – poloha okna se nemění.
 * Ostatní jazyky: směr se nemění (INHERIT jako dřív), takže vypadají přesně jako dosud.
 */
internal fun TextView.bidiFor(text: CharSequence?) {
    if (cz.recaption.core.Bidi.hasRtl(text)) {
        textDirection = View.TEXT_DIRECTION_ANY_RTL
        if (gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK == Gravity.END)
            gravity = (gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK.inv()) or Gravity.RIGHT
    } else if (textDirection != View.TEXT_DIRECTION_INHERIT) {
        textDirection = View.TEXT_DIRECTION_INHERIT
    }
}
