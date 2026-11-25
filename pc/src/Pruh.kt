package cz.recaption.pc

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import cz.recaption.core.ScreenLayout
import cz.recaption.core.Segment
import cz.recaption.core.Zone
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pruh s překladem přímo na obrazovce počítače, přes okno s hrou (chiaki-ng, Remote Play…).
 *
 * Okno pruhu je vždy navrchu, průhledné, nebere si zaměření a myš i dotyky jím propadají do hry.
 * Hra se tak dál ovládá úplně stejně. Pruh jede s oknem hry, když se posune nebo změní velikost.
 *
 * Windows hlásí polohu okna hry ve skutečných bodech, Swing kreslí v bodech po zvětšení obrazovky
 * (na handheldu 150–200 %) – [obnov] to přepočítá podle [meritko]. Funguje pro hlavní obrazovku.
 * Na Macu obstará okna [MacOknoHry] a vlastnosti okna pruhu [MacPruh] (`Mac.kt`).
 */

/** Obdélník na obrazovce ve skutečných bodech. */
data class Obdelnik(val x: Int, val y: Int, val w: Int, val h: Int)

/** Funkce Windows, které v knihovně JNA nejsou ve všech verzích. */
private interface U32 : StdCallLibrary {
    fun IsIconic(hwnd: WinDef.HWND): Boolean
    fun IsWindow(hwnd: WinDef.HWND): Boolean
    fun ClientToScreen(hwnd: WinDef.HWND, p: WinDef.POINT): Boolean

    companion object {
        val I: U32 = Native.load("user32", U32::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

/**
 * Okno s hrou podle části titulku – stejné hledání jako v `cteni.py` (první viditelné shora).
 * „pid:1234“ = největší viditelné okno procesu 1234 (chiaki-ng spuštěný ReCaptionem).
 */
class OknoHry(nazev: String) {
    private val hledat = nazev.lowercase()
    private val procesu = hledat.removePrefix("pid:").toIntOrNull()?.takeIf { hledat.startsWith("pid:") }
    private val mojePid = ProcessHandle.current().pid().toInt()
    private var hwnd: WinDef.HWND? = null
    /** Na Macu se okna hledají jinak ([MacOknoHry]); jinde než ve Windows a na Macu okno hry není. */
    private val mac = if (Os.mac) MacOknoHry(nazev) else null

    private fun najdi(): WinDef.HWND? {
        if (!Os.windows) return null
        var nalezeno: WinDef.HWND? = null
        var nejvetsi = 0
        User32.INSTANCE.EnumWindows({ h, _ ->
            if (User32.INSTANCE.IsWindowVisible(h)) {
                val pid = IntByReference()
                User32.INSTANCE.GetWindowThreadProcessId(h, pid)
                if (procesu != null) {
                    if (pid.value == procesu) {
                        val r = WinDef.RECT()
                        User32.INSTANCE.GetWindowRect(h, r)
                        val plocha = (r.right - r.left) * (r.bottom - r.top)
                        if (plocha > nejvetsi) { nejvetsi = plocha; nalezeno = h }
                    }
                    return@EnumWindows true
                }
                val n = User32.INSTANCE.GetWindowTextLength(h)
                if (n > 0) {
                    val buf = CharArray(n + 1)
                    User32.INSTANCE.GetWindowText(h, buf, n + 1)
                    if (pid.value != mojePid && Native.toString(buf).lowercase().contains(hledat)) {
                        nalezeno = h
                        return@EnumWindows false
                    }
                }
            }
            true
        }, null)
        return nalezeno
    }

    /** Okno je otevřené, ale minimalizované (pak se z něj nedá číst). */
    fun minimalizovane(): Boolean {
        if (!Os.windows) return false      // Mac minimalizované okno od okna na jiné ploše nerozliší
        val h = hwnd?.takeIf { U32.I.IsWindow(it) } ?: najdi().also { hwnd = it } ?: return false
        return U32.I.IsIconic(h)
    }

    /** Je okno otevřené (i minimalizované)? */
    fun existuje(): Boolean = if (mac != null) mac.existuje() else (hwnd?.takeIf { U32.I.IsWindow(it) } ?: najdi().also { hwnd = it }) != null

    /** Minimalizované okno vrátí na obrazovku, bez převzetí zaměření (SW_SHOWNOACTIVATE). */
    fun obnov() {
        if (!Os.windows) return
        val h = hwnd?.takeIf { U32.I.IsWindow(it) } ?: najdi().also { hwnd = it } ?: return
        if (U32.I.IsIconic(h)) User32.INSTANCE.ShowWindow(h, 4)
    }

    /** Vnitřek okna hry (bez rámečku a titulku) na obrazovce; null = okno není nebo je minimalizované. */
    fun oblast(): Obdelnik? {
        if (mac != null) return mac.oblast()
        val h = hwnd?.takeIf { U32.I.IsWindow(it) } ?: najdi().also { hwnd = it } ?: return null
        if (U32.I.IsIconic(h)) return null
        val r = WinDef.RECT()
        if (!User32.INSTANCE.GetClientRect(h, r)) return null
        val p = WinDef.POINT(0, 0)
        if (!U32.I.ClientToScreen(h, p)) return null
        val w = r.right - r.left
        val v = r.bottom - r.top
        return if (w < 320 || v < 180) null else Obdelnik(p.x, p.y, w, v)
    }
}

private interface Dwm : StdCallLibrary {
    fun DwmGetWindowAttribute(hwnd: WinDef.HWND, attr: Int, value: IntByReference, size: Int): Int

    companion object {
        val I: Dwm = Native.load("dwmapi", Dwm::class.java)
    }
}

/** Programy pro hraní na dálku – ve výběru okna jsou nahoře. */
private val HERNI = listOf("chiaki", "PS Remote Play", "Xbox")

/**
 * Titulky oken, ve kterých může běžet hra – nabídka „Vybrat okno“. Bez vlastních oken, bez
 * skrytých systémových (Nastavení, Vstupní funkce…, ty Windows jen „zahalí“), bez nástrojových
 * a malých oken; programy pro hraní na dálku první.
 */
fun otevrenaOkna(): List<String> {
    if (Os.mac) return macOtevrenaOkna().sortedBy { t -> if (HERNI.any { t.contains(it, ignoreCase = true) }) 0 else 1 }
    if (!Os.windows) return emptyList()
    val moje = ProcessHandle.current().pid().toInt()
    val vysledek = LinkedHashSet<String>()
    User32.INSTANCE.EnumWindows({ h, _ ->
        if (User32.INSTANCE.IsWindowVisible(h)) {
            val n = User32.INSTANCE.GetWindowTextLength(h)
            val styl = User32.INSTANCE.GetWindowLong(h, WinUser.GWL_EXSTYLE)
            val zahalene = IntByReference()
            runCatching { Dwm.I.DwmGetWindowAttribute(h, 14 /* DWMWA_CLOAKED */, zahalene, 4) }
            val r = WinDef.RECT()
            User32.INSTANCE.GetWindowRect(h, r)
            if (n > 0 && zahalene.value == 0 && styl and 0x80 /* WS_EX_TOOLWINDOW */ == 0 &&
                r.right - r.left >= 320 && r.bottom - r.top >= 180) {
                val buf = CharArray(n + 1)
                User32.INSTANCE.GetWindowText(h, buf, n + 1)
                val pid = IntByReference()
                User32.INSTANCE.GetWindowThreadProcessId(h, pid)
                val t = Native.toString(buf).trim()
                if (pid.value != moje && t.isNotEmpty() && t != "Program Manager") vysledek += t
            }
        }
        true
    }, null)
    return vysledek.sortedBy { t -> if (HERNI.any { t.contains(it, ignoreCase = true) }) 0 else 1 }
}

/** Zvětšení hlavní obrazovky ve Windows (1.0 = 100 %, 2.0 = 200 %), jak ho používá Swing. */
fun meritko(): Double =
    GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX

/** Celá hlavní obrazovka ve skutečných bodech – pro ukázku bez PlayStationu, kde žádné okno s hrou není. */
fun celaObrazovka(): Obdelnik {
    val b = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
    val m = meritko()
    return Obdelnik((b.x * m).toInt(), (b.y * m).toInt(), (b.width * m).toInt(), (b.height * m).toInt())
}

/**
 * Kde na obrazovce hry byl anglický titulek – poměry šířky/výšky okna hry (0..1).
 * [nahore]/[dole] < 0 = nevíme; [radek] = výška anglického řádku (0 = nevíme).
 */
data class Misto(val stredX: Float, val nahore: Float, val dole: Float, val radek: Float)

/**
 * Kam pruh položit – jako na Androidu (Titulky → Pruh s překladem → Poloha). [tv] = stejná poloha pro
 * aplikaci v TV LG/Samsung (`ScreenLayout.Position`, „Dole“ je tam pevné místo).
 */
enum class Poloha(val nazev: String, val tv: ScreenLayout.Position) {
    PRES("Přes titulky", ScreenLayout.Position.OVER),
    NAD("Nad titulky", ScreenLayout.Position.ABOVE_TEXT),
    NAHORE("Nahoře", ScreenLayout.Position.TOP),
    DOLE("Dole", ScreenLayout.Position.CUSTOM);

    companion object {
        /** Poloha zvolená ovladačem TV → nejbližší poloha na PC. */
        fun zTv(p: ScreenLayout.Position): Poloha = when (p) {
            ScreenLayout.Position.OVER -> PRES
            ScreenLayout.Position.ABOVE_TEXT, ScreenLayout.Position.ABOVE -> NAD
            ScreenLayout.Position.TOP -> NAHORE
            ScreenLayout.Position.CUSTOM -> DOLE
        }
    }
}

/** Vzhled pruhu ze stránky Titulky (stejné volby jako na Androidu). */
data class Vzhled(
    /** Velikost písma 12–48 (jako sp na Androidu); 0 = podle anglického titulku ve hře. */
    val pismo: Int = 0,
    /** Neprůhlednost pozadí v % (0 = bez pozadí, písmo s obrysem). */
    val pozadi: Int = 90,
    val barva: Color = Color.WHITE,
    val tucne: Boolean = true,
    val poloha: Poloha = Poloha.PRES,
    /** Okraj nad a pod textem 0–3 (`Prefs.BAR_PADS` na Androidu). */
    val okraj: Int = 1,
    /** Anglický originál menším písmem pod překladem. */
    val original: Boolean = false,
    /** Pokyny pro hráče (GTA) se ukazují jen s volbou Pokyny pod titulky. */
    val pokyny: Boolean = true,
) {
    /** Rozložení pro TV ([cz.recaption.core.TvLook]): čte se spodní třetina, „Dole“ = pevné místo dole. */
    fun layout(): ScreenLayout {
        val l = ScreenLayout(read = Zone(0.05f, 2f / 3f, 0.95f, 1f))
        return if (poloha == Poloha.DOLE) l.copy(cover = false, hug = false, barCenterY = 0.92f) else l.withPosition(poloha.tv)
    }

    companion object {
        /** Okraj nad a pod textem jako násobek písma (stejné hodnoty jako `Prefs.BAR_PADS`). */
        val OKRAJE = floatArrayOf(0.04f, 0.16f, 0.32f, 0.5f)
        /** Písmo 22 dá na obrazovce 1080 bodů vysoké 50 px – jako v TV ([cz.recaption.core.TvLook.SP_TO_PX]). */
        fun px(pismo: Int, vyskaObrazu: Int): Float = pismo * cz.recaption.core.TvLook.SP_TO_PX * vyskaObrazu / 1080f
    }
}

/**
 * Pruh. [oblast] říká, kde je teď okno hry; volá se pořád dokola, aby pruh jel s oknem.
 *
 * Kam pruh patří, se zadává poměrem v okně hry (0..1) – stejně jako na Android TV leží přes
 * anglický titulek, ze kterého překlad vznikl. Velikost písma se řídí výškou anglického řádku,
 * nebo číslem z Titulků. Překlad menu hry ([ukazMenu]) leží vedle menu, řádky pod sebou zarovnané vlevo.
 */
/**
 * Pruh s překladem přes okno hry. [naMiste] = pruh úkolu pod titulky (GTA): vždy přes anglický text
 * bez ohledu na zvolenou polohu titulků a bez originálu.
 */
class Pruh(private val naMiste: Boolean = false, private val oblast: () -> Obdelnik?) {
    private class Obsah(
        val casti: List<Segment>, val barvy: Map<String, Int>, val misto: Misto,
        val original: String? = null,
        /** Levý horní roh ve zlomcích okna hry – překlad menu (řádky vlevo, bez centrování). */
        val box: Pair<Float, Float>? = null,
    )

    @Volatile var vzhled = Vzhled()
        set(v) { field = v; SwingUtilities.invokeLater { naposledy = null; obnov() } }

    /** Kousek řádku jednou barvou. */
    private class Kus(val text: String, val barva: Color)
    /** Řádek pruhu: kousky a písmo (pokyn a originál menším). */
    private class Radka(val kusy: List<Kus>, val pismo: Font)

    private val okno = JWindow()
    private val plocha = Plocha()
    @Volatile private var obsah: Obsah? = null
    private var naposledy: Pair<Obsah?, Obdelnik?>? = null
    private var stylNastaven = false
    private var stylPokusy = 0

    init {
        val nastav = {
            okno.background = Color(0, 0, 0, 0)
            okno.focusableWindowState = false
            okno.isAlwaysOnTop = true
            okno.contentPane = plocha
            okno.setBounds(0, 0, 1, 1)
            Timer(150) { obnov() }.start()
        }
        if (SwingUtilities.isEventDispatchThread()) nastav() else SwingUtilities.invokeAndWait(nastav)
    }

    /** Ukáže překlad tam, kde byl anglický titulek ([misto]); [original] = anglický text (ukáže se jen s volbou Originál). */
    fun ukaz(casti: List<Segment>, barvy: Map<String, Int>, misto: Misto, original: String? = null) {
        obsah = Obsah(casti, barvy, misto, original)
        SwingUtilities.invokeLater { obnov() }
    }

    /** Překlad menu hry: řádky pod sebou od levého horního rohu [box] (zlomky okna hry). */
    fun ukazMenu(radky: List<String>, box: Pair<Float, Float>?) {
        obsah = Obsah(radky.map { Segment(null, it) }, emptyMap(), Misto(0.5f, -1f, -1f, 0f), null, box ?: (0.3f to 0.2f))
        SwingUtilities.invokeLater { obnov() }
    }

    /** Zkušební věta na pár vteřin – náhled vzhledu ze stránky Titulky. */
    fun zkouska() {
        ukaz(
            listOf(Segment("Marek", "Potřebujeme peníze, Jonasi. Ráno vyrážíme do Greyfordu."), Segment(null, "Stiskni X a nastup", prompt = true)),
            mapOf("Marek" to 0xFF7FC8F8.toInt()), Misto(0.5f, 0.84f, 0.9f, 0.05f),
            "MAREK: We need money, Jonas. We ride out to Greyford at dawn.",
        )
        val ted = obsah
        Timer(4000) { if (obsah === ted) schovej() }.apply { isRepeats = false; start() }
    }

    /** Kde pruh právě je (body Swingu); null = schovaný. Jen pro snímky při ladění. */
    fun obdelnik(): java.awt.Rectangle? = if (okno.isVisible) okno.bounds else null

    fun schovej() {
        obsah = null
        SwingUtilities.invokeLater { obnov() }
    }

    // ---- jen ve vlákně Swingu ----

    private fun obnov() {
        val o = obsah
        // Windows hlásí okno hry ve skutečných bodech, Swing kreslí v bodech po zvětšení
        val r = (if (o == null) null else oblast())?.let { s ->
            val m = meritko()
            Obdelnik((s.x / m).roundToInt(), (s.y / m).roundToInt(), (s.w / m).roundToInt(), (s.h / m).roundToInt())
        }
        if (naposledy?.first === o && naposledy?.second == r) {
            if (Os.mac && !stylNastaven && okno.isVisible) nastavStyl()
            return
        }
        naposledy = o to r
        if (o == null || r == null) {
            okno.isVisible = false
            return
        }
        val v = vzhled
        val m = o.misto
        // písmo: číslo z Titulků (jako sp na Androidu), nebo zhruba jako anglický titulek (výška řádku ze čtení
        // zahrnuje i okraj kolem písmen), ale aby se dalo číst i na malém displeji handheldu
        val px = if (v.pismo > 0) Vzhled.px(v.pismo, r.h).coerceIn(10f, r.h * 0.12f)
            else (if (m.radek > 0f) m.radek * r.h * 0.7f else r.h * 0.035f).coerceIn(max(16f, r.h * 0.025f), r.h * 0.05f)
        val hlavni = Font(Os.PISMO, if (v.tucne) Font.BOLD else Font.PLAIN, px.roundToInt())
        val male = Font(Os.PISMO, Font.PLAIN, (px * 0.78f).roundToInt())
        plocha.pozadi = Color(0, 0, 0, (255 * v.pozadi / 100).coerceIn(0, 255))
        plocha.obrys = v.pozadi < 40
        val bokem = (px * 0.6f).roundToInt()
        val nahoreDole = (px * Vzhled.OKRAJE[v.okraj.coerceIn(0, 3)]).roundToInt().coerceAtLeast(2)
        val maxSirka = (r.w * (if (o.box != null) 0.6f else 0.9f)).roundToInt() - 2 * bokem
        val radky = zalom(o, hlavni, male, maxSirka, v)
        if (radky.isEmpty()) { okno.isVisible = false; return }
        plocha.radky = radky
        plocha.bokem = bokem
        plocha.nahoreDole = nahoreDole
        plocha.vlevo = o.box != null
        val sirka = radky.maxOf { rd -> plocha.getFontMetrics(rd.pismo).let { fm -> rd.kusy.sumOf { fm.stringWidth(it.text) } } } + 2 * bokem
        val vyska = radky.sumOf { plocha.getFontMetrics(it.pismo).height } + 2 * nahoreDole
        val box = o.box
        val (x, y) = if (box != null) {
            (r.x + box.first * r.w).roundToInt() to (r.y + box.second * r.h).roundToInt()
        } else {
            val znamo = m.nahore >= 0f && m.dole >= 0f
            val poloha = if (naMiste && znamo) Poloha.PRES else if (znamo || v.poloha == Poloha.NAHORE) v.poloha else Poloha.DOLE
            val stredX = if (poloha == Poloha.DOLE || poloha == Poloha.NAHORE) 0.5f else m.stredX
            val yf = when (poloha) {
                Poloha.PRES -> r.y + (m.nahore + m.dole) / 2f * r.h - vyska / 2f
                Poloha.NAD -> r.y + m.nahore * r.h - vyska - px * 0.3f
                Poloha.NAHORE -> r.y + r.h * 0.04f
                Poloha.DOLE -> r.y + r.h * 0.97f - vyska
            }
            (r.x + stredX * r.w - sirka / 2f).roundToInt() to yf.roundToInt()
        }
        okno.setBounds(x.coerceIn(r.x, max(r.x, r.x + r.w - sirka)), y.coerceIn(r.y, max(r.y, r.y + r.h - vyska)), sirka, vyska)
        if (!okno.isVisible) okno.isVisible = true
        nastavStyl()
        okno.toFront()
        plocha.repaint()
    }

    /** Myš a dotyky skrz pruh do hry, bez zaměření a bez tlačítka na hlavním panelu. */
    private fun nastavStyl() {
        if (stylNastaven) return
        if (Os.mac) {
            // okno se v seznamu oken systému objeví až chvíli po zobrazení – zkouší se, dokud se nenajde
            if (MacPruh.nastav(okno) || ++stylPokusy > 40) stylNastaven = true
            return
        }
        if (!Os.windows) { stylNastaven = true; return }
        val hwnd = WinDef.HWND(Native.getComponentPointer(okno) ?: return)
        val styl = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
        User32.INSTANCE.SetWindowLong(
            hwnd, WinUser.GWL_EXSTYLE,
            styl or WS_EX_LAYERED or WS_EX_TRANSPARENT or WS_EX_TOOLWINDOW or WS_EX_NOACTIVATE or WS_EX_TOPMOST,
        )
        stylNastaven = true
    }

    /**
     * Rozdělí části titulku do řádků, které se vejdou do [max] bodů. Každá část začíná novým řádkem;
     * pokyn pro hráče (GTA) a originál menším šedým písmem.
     */
    private fun zalom(o: Obsah, hlavni: Font, male: Font, max: Int, v: Vzhled): List<Radka> {
        val radky = ArrayList<Radka>()
        fun pridej(slova: List<Kus>, pismo: Font) {
            val fm = plocha.getFontMetrics(pismo)
            var radek = ArrayList<Kus>()
            var sirka = 0
            for (s in slova) {
                val w = fm.stringWidth(s.text)
                if (radek.isNotEmpty() && sirka + w - fm.charWidth(' ') > max) {
                    radky += Radka(radek, pismo); radek = ArrayList(); sirka = 0
                }
                radek += s
                sirka += w
            }
            if (radek.isNotEmpty()) radky += Radka(radek, pismo)
        }
        fun slova(text: String, barva: Color) = text.split(' ').filter { it.isNotEmpty() }.map { Kus("$it ", barva) }
        for (c in o.casti) {
            if (c.prompt && !v.pokyny) continue
            val zacatek = when {
                c.choice -> listOf(Kus("› ", ZLUTA))
                c.speaker != null -> listOf(Kus("${c.speaker}: ", Color((o.barvy[c.speaker] ?: 0xF2C14E) and 0xFFFFFF)))
                else -> emptyList()
            }
            if (c.prompt) pridej(slova(c.body, SEDA), male) else pridej(zacatek + slova(c.body, v.barva), hlavni)
        }
        if (v.original && !naMiste) o.original?.let { pridej(slova(it, SEDA), male) }
        // mezera na konci řádku se do šířky nepočítá
        return radky.map { r -> Radka(r.kusy.dropLast(1) + Kus(r.kusy.last().text.trimEnd(), r.kusy.last().barva), r.pismo) }
    }

    private inner class Plocha : JComponent() {
        var radky: List<Radka> = emptyList()
        var bokem = 12
        var nahoreDole = 6
        var pozadi = Color(0, 0, 0, 215)
        /** Bez pozadí (nebo skoro) – písmo dostane tmavý obrys, ať je čitelné i na světlé scéně. */
        var obrys = false
        var vlevo = false

        init { isOpaque = false }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.composite = java.awt.AlphaComposite.Src
            g2.color = Color(0, 0, 0, 0)
            g2.fillRect(0, 0, width, height)
            g2.color = pozadi
            val oblouk = min(bokem * 2, height)
            g2.fillRoundRect(0, 0, width, height, oblouk, oblouk)
            g2.composite = java.awt.AlphaComposite.SrcOver
            var y = nahoreDole
            for (r in radky) {
                g2.font = r.pismo
                val fm = g2.fontMetrics
                y += fm.ascent
                var x = if (vlevo) bokem else (width - r.kusy.sumOf { fm.stringWidth(it.text) }) / 2
                val d = max(1, r.pismo.size / 16)
                for (k in r.kusy) {
                    if (obrys) {
                        g2.color = Color(0, 0, 0, 230)
                        for ((dx, dy) in STIN) g2.drawString(k.text, x + dx * d, y + dy * d)
                    }
                    g2.color = k.barva
                    g2.drawString(k.text, x, y)
                    x += fm.stringWidth(k.text)
                }
                y += fm.descent + fm.leading
            }
        }
    }

    private companion object {
        val ZLUTA = Color(0xFFD54F)
        val SEDA = Color(0xB4BCC4)
        val STIN = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, 1 to 1, -1 to 1, 1 to -1, -1 to -1)
        const val WS_EX_TOPMOST = 0x00000008
        const val WS_EX_TRANSPARENT = 0x00000020
        const val WS_EX_TOOLWINDOW = 0x00000080
        const val WS_EX_LAYERED = 0x00080000
        const val WS_EX_NOACTIVATE = 0x08000000
    }
}
