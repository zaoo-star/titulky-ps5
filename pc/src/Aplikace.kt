@file:JvmName("AplikaceKt")
package cz.recaption.pc

import cz.recaption.core.Deepl
import cz.recaption.core.GameProfiles
import cz.recaption.core.I18n
import cz.recaption.core.TvSettings
import cz.recaption.core.TvAnnounce
import cz.recaption.core.TvSender
import cz.recaption.core.Lang
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.concurrent.thread

/**
 * ReCaption pro počítač – okno aplikace ve stejném duchu jako aplikace pro telefon a TV:
 * vlevo menu (Domů, Titulky, ⚙ Nastavení), Domů = velký stav, Spustit/Zastavit a stavové řádky
 * „Popis · hodnota“ s tečkou (zelená = jede, oranžová = je potřeba zásah, šedá = informace).
 *
 * Čtení a překlad dělá [Ctecka], pruh přes hru [Pruh]. Spouští se `ReCaption.cmd`.
 */

/** Barvy aplikace – stejné jako `Theme` v aplikaci pro Android. */
internal object B {
    val BG = Color(0x0E, 0x10, 0x14)
    val SIDEBAR = Color(0x14, 0x17, 0x1D)
    val CARD = Color(0x1B, 0x1F, 0x27)
    val CARD2 = Color(0x24, 0x29, 0x33)
    val FOCUS = Color(0x4A, 0x40, 0x22)
    val ACCENT_FOCUS = Color(0xFF, 0xE3, 0x8F)
    val TEXT = Color(0xF2, 0xF2, 0xF2)
    val MUTED = Color(0x9A, 0xA4, 0xAE)
    val ACCENT = Color(0xF2, 0xC1, 0x4E)
    val ACCENT_DARK = Color(0x1A, 0x14, 0x05)
    val OK = Color(0x4A, 0xDE, 0x80)
    val WARN = Color(0xF5, 0x9E, 0x0B)
}

internal fun pismo(size: Int, tucne: Boolean = false) = Font(Os.PISMO, if (tucne) Font.BOLD else Font.PLAIN, size)

internal fun Graphics.hladce(): Graphics2D = (this as Graphics2D).apply {
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
}

internal fun popisek(text: String, size: Int = 16, barva: Color = B.TEXT, tucne: Boolean = false) =
    JLabel(text).apply { font = pismo(size, tucne); foreground = barva }

/** Svislý sloupec bez pozadí. */
internal fun sloupec(vararg deti: Component) = JPanel().apply {
    isOpaque = false
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    deti.forEach { add(it) }
}

internal fun JComponent.vlevo() = apply { alignmentX = Component.LEFT_ALIGNMENT }

/** Karta se zaoblenými rohy jako `ui.card()` na Androidu. */
internal class Karta : JPanel() {
    init {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = EmptyBorder(10, 14, 10, 14)
        alignmentX = Component.LEFT_ALIGNMENT
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.hladce()
        g2.color = B.CARD
        g2.fillRoundRect(0, 0, width, height, 24, 24)
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

/** Tlačítko – hlavní (žluté) nebo obyčejné; vybrané ovladačem/klávesnicí má zlatý rámeček. */
internal class Tlacitko(text: String, private val hlavni: Boolean = false, akce: () -> Unit) : JButton(text) {
    init {
        isContentAreaFilled = false
        isFocusPainted = false
        isBorderPainted = false
        isRolloverEnabled = true
        font = pismo(if (hlavni) 18 else 15, true)
        foreground = if (hlavni) B.ACCENT_DARK else B.TEXT
        border = EmptyBorder(if (hlavni) 12 else 7, if (hlavni) 34 else 16, if (hlavni) 12 else 7, if (hlavni) 34 else 16)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addActionListener { akce() }
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = repaint()
            override fun focusLost(e: FocusEvent) = repaint()
        })
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.hladce()
        val aktivni = model.isRollover || isFocusOwner
        g2.color = when {
            hlavni && aktivni -> B.ACCENT_FOCUS
            hlavni -> B.ACCENT
            aktivni -> B.FOCUS
            else -> B.CARD2
        }
        g2.fillRoundRect(0, 0, width, height, 20, 20)
        if (isFocusOwner) {
            g2.color = if (hlavni) Color.WHITE else B.ACCENT
            g2.stroke = BasicStroke(3f)
            g2.drawRoundRect(1, 1, width - 3, height - 3, 20, 20)
        }
        super.paintComponent(g)
    }
}

/** Řádek nastavení: název vlevo, hodnota a „›“ vpravo; klik / Enter = [akce]. */
internal class Radek(nazev: String, private val hodnota: () -> String, private val akce: (Radek) -> Unit) : JPanel(BorderLayout()) {
    private val vpravo = popisek("", 16, B.ACCENT)
    private val vlevo = popisek(nazev, 16)
    private var nad = false
    /** Popisek řádku (mění se jen u řádku, který závisí na hře). */
    var nazev: String get() = vlevo.text; set(v) { vlevo.text = v }

    init {
        isOpaque = false
        isFocusable = true
        border = EmptyBorder(11, 12, 11, 12)
        alignmentX = Component.LEFT_ALIGNMENT
        add(vlevo, BorderLayout.WEST)
        add(vpravo, BorderLayout.EAST)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        obnov()
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) { requestFocusInWindow(); akce(this@Radek) }
            override fun mouseEntered(e: MouseEvent) { nad = true; repaint() }
            override fun mouseExited(e: MouseEvent) { nad = false; repaint() }
        })
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_SPACE) akce(this@Radek)
            }
        })
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) { repaint(); scrollRectToVisible(bounds.let { java.awt.Rectangle(0, 0, width, height) }) }
            override fun focusLost(e: FocusEvent) = repaint()
        })
    }

    fun obnov() { vpravo.text = hodnota().let { if (it == "▼" || it == "▲") it else "$it  ›" } }

    override fun paintComponent(g: Graphics) {
        if (!nad && !isFocusOwner) return
        val g2 = g.hladce()
        g2.color = B.FOCUS
        g2.fillRoundRect(0, 0, width, height, 18, 18)
        if (isFocusOwner) {
            g2.color = B.ACCENT
            g2.stroke = BasicStroke(3f)
            g2.drawRoundRect(1, 1, width - 3, height - 3, 18, 18)
        }
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

/** Stavový řádek: tečka + „Popis · hodnota“ + případně tlačítko pod tím. */
internal class StavRadek : JPanel() {
    private val tecka = object : JComponent() {
        var barva = B.MUTED
        init { preferredSize = Dimension(14, 14); maximumSize = preferredSize }
        override fun paintComponent(g: Graphics) {
            val g2 = g.hladce(); g2.color = barva; g2.fillOval(1, 1, 12, 12)
        }
    }
    private val text = popisek("", 17)
    private val tlacitka = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
    private var akce: (() -> Unit)? = null

    init {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        border = EmptyBorder(7, 4, 7, 4)
        val horni = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            add(tecka); add(Box.createHorizontalStrut(12)); add(text)
        }
        add(horni)
        tlacitka.alignmentX = Component.LEFT_ALIGNMENT
        tlacitka.border = EmptyBorder(8, 26, 0, 0)
        add(tlacitka)
    }

    /** [tlacitko] = popisek tlačítka pod řádkem (jen u oranžových, kde je potřeba zásah). */
    fun nastav(barva: Color, obsah: String, tlacitko: String? = null, akce: (() -> Unit)? = null) {
        tecka.barva = barva; tecka.repaint()
        text.text = obsah
        isVisible = obsah.isNotEmpty()
        if (tlacitko != (tlacitka.components.firstOrNull() as? JButton)?.text) {
            tlacitka.removeAll()
            if (tlacitko != null) tlacitka.add(Tlacitko(tlacitko) { this.akce?.invoke() })
            tlacitka.isVisible = tlacitko != null
            revalidate()
        }
        this.akce = akce
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

/** Nabídka možností pod řádkem (jako výběr na Androidu). */
internal fun vyber(kde: Component, moznosti: List<String>, aktualni: Int, vybrano: (Int) -> Unit) {
    val menu = JPopupMenu().apply {
        background = B.CARD2
        border = BorderFactory.createLineBorder(B.ACCENT, 2)
    }
    moznosti.forEachIndexed { i, m ->
        menu.add(JMenuItem((if (i == aktualni) "✔  " else "     ") + m).apply {
            font = Font(Os.PISMO_ZNAKY, if (i == aktualni) Font.BOLD else Font.PLAIN, 16)
            background = B.CARD2
            foreground = if (i == aktualni) B.ACCENT else B.TEXT
            border = EmptyBorder(8, 12, 8, 24)
            addActionListener { vybrano(i) }
        })
    }
    menu.show(kde, kde.width - menu.preferredSize.width, kde.height)
}

private class Aplikace {
    private val nast = Nastaveni()
    private val okno = JFrame("ReCaption")
    private val obsah = JPanel(CardLayout()).apply { background = B.BG }
    private val polozkyMenu = ArrayList<Pair<String, JComponent>>()
    private var stranka = "Domů"

    // čtení
    private var hra = OknoHry(nast.okno)
    private val pruh = Pruh { if (nast.ukazka) celaObrazovka() else hra.oblast() }
    /** Zkušební pruh, když okno hry není otevřené – přes celou obrazovku (dřív se pak neukázalo nic, 23. 11. 2026). */
    private val pruhBezHry by lazy { Pruh { celaObrazovka() } }

    /** Ukázka pruhu (Titulky → Zkušební pruh): nad oknem hry, a když hra neběží, přes celou obrazovku. */
    private fun zkusebniPruh() {
        if (nast.ukazka || this.hra.oblast() != null) { pruh.zkouska(); return }
        pruhBezHry.vzhled = pruh.vzhled
        pruhBezHry.zkouska()
    }
    /** Pruh s přeloženým úkolem pod titulky (GTA) – přes anglický úkol. */
    private val pruhUkolu = Pruh(naMiste = true) { if (nast.ukazka) celaObrazovka() else hra.oblast() }

    /** Vzhled z Nastavení do obou pruhů (titulky i úkol). */
    private fun novyVzhled() { nast.vzhled().let { pruh.vzhled = it; pruhUkolu.vzhled = it } }
    private var ctecka: Ctecka? = null
    private var vlakno: Thread? = null
    @Volatile private var stav = Stav(Stav.Faze.STOJI)
    @Volatile private var priprava: Process? = null
    private var pripravaChyba = false
    private var oknoNalezeno = false

    // chiaki-ng (zdroj „chiaki“): hra spuštěná ReCaptionem
    @Volatile private var chiaki: Process? = null
    /** Co se právě chystá před hrou („Hledání PS5…“, „Probouzení PS5…“, „Spouštění hry…“). */
    @Volatile private var krok: String? = null
    /** Stahování chiaki-ng v % (null = nestahuje se). */
    @Volatile private var stahovani: Int? = null
    @Volatile private var konzoleSeznam: List<Chiaki.Konzole> = emptyList()
    @Volatile private var ps5: Chiaki.Nalez? = null
    @Volatile private var ps5Hledano = false
    private var tiku = 0

    // čtečka pro TV (Použití = čtečka): PS5 v TV, PC čte obraz z chiaki-ng a posílá překlad do TV
    @Volatile private var cteckaTv: CteckaTv? = null
    private var cteckaVlakno: Thread? = null
    /** Okno chiaki-ng čtečky je minimalizované (hlídá se každou sekundu, čte i vlákno čtení pro stav v TV). */
    @Volatile private var chiakiMinim = false
    private fun cteckaBezi() = cteckaVlakno?.isAlive == true
    /** Nezhasínat počítač při překladu – volá se jen z vlákna okna (stav Windows patří vláknu). */
    private val nezhasinat = Nezhasinat(::zapis)
    /** Řádky Nastavení jen pro hraní na PC (schovávají se ve čtečce). */
    private val jenPc = ArrayList<JComponent>()
    private val zaznam = JTextArea().apply {
        isEditable = false; background = B.CARD; foreground = B.MUTED
        font = Font(Os.PISMO_ZAZNAM, Font.PLAIN, 13); border = EmptyBorder(10, 12, 10, 12); lineWrap = true
    }

    // Domů
    private val velkyStav = popisek("", 34, B.TEXT, true)
    private val hlavniTlacitko = Tlacitko("Spustit", hlavni = true) { spustitNeboZastavit() }
    private val postup = JProgressBar().apply {
        isIndeterminate = true; isVisible = false; foreground = B.ACCENT; background = B.CARD2
        border = null; maximumSize = Dimension(360, 6); preferredSize = Dimension(360, 6); alignmentX = Component.LEFT_ALIGNMENT
    }
    private val rPouziti = StavRadek()
    /** Jen Mac: chybějící povolení „Nahrávání obrazovky“ (bez něj systém nevydá obraz okna hry). */
    private val rPovoleni = StavRadek()
    private val rOkno = StavRadek()
    private val rPs5 = StavRadek()
    private val rPreklad = StavRadek()
    private val rHra = StavRadek()
    private val rVystup = StavRadek()
    private val rCteni = StavRadek()
    private val rTitulek = StavRadek()
    private val radky = ArrayList<Radek>()

    init {
        novyVzhled()
        okno.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        okno.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) = konec()
        })
        okno.contentPane.background = B.BG
        okno.layout = BorderLayout()
        obsah.add(svislePosuvne(domu()), "Domů")
        obsah.add(svislePosuvne(titulky()), "Titulky")
        obsah.add(svislePosuvne(nastaveni()), "Nastavení")
        okno.add(menu(), BorderLayout.WEST)
        okno.add(obsah, BorderLayout.CENTER)
        okno.size = Dimension(1000, 680)
        okno.minimumSize = Dimension(760, 520)
        okno.setLocationRelativeTo(null)
        obnovDomu()
        Timer(1000) { hlidejOkno() }.start()
        // ověřit uložený klíč DeepL (zbytek znaků do Nastavení), bez čekání
        if (DeeplPc.ma()) thread(isDaemon = true) { DeeplPc.over(); SwingUtilities.invokeLater { radky.forEach { it.obnov() } } }
        // jen pro snímky při ladění: -Drecaption.stranka=Titulky, -Drecaption.autostart=1 (jako Spustit)
        System.getProperty("recaption.stranka")?.let { prepni(it) }
        if (System.getProperty("recaption.autostart") == "1" && Nastroje.pripraveno(nast.jazyk)) spustit()
        okno.isVisible = true
        hlavniTlacitko.requestFocusInWindow()
        // jen pro snímky při ladění: -Drecaption.parovani=1 otevře průvodce spárováním
        if (System.getProperty("recaption.parovani") == "1") sparovat()
        // jinak -Drecaption.snimek=<složka> uloží po 3 s stránky okna (Domů, Titulky, Nastavení) a zavře ho
        else System.getProperty("recaption.snimek")?.let { slozka ->
            Timer(3000) {
                java.io.File(slozka).mkdirs()
                for (s in listOf("Domů", "Titulky", "Nastavení")) {
                    prepni(s)
                    okno.contentPane.validate()
                    val c = okno.contentPane
                    val img = java.awt.image.BufferedImage(c.width, c.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
                    c.paint(img.graphics)
                    javax.imageio.ImageIO.write(img, "png", java.io.File(slozka, "okno-$s.png"))
                }
                // zkušební pruh přes celou obrazovku – vyfotí se jen jeho obdélník
                val zkusebni = Pruh { celaObrazovka() }.apply { vzhled = nast.vzhled().copy(original = true); zkouska() }
                Timer(1200) {
                    zkusebni.obdelnik()?.let { b ->
                        javax.imageio.ImageIO.write(java.awt.Robot().createScreenCapture(b), "png", java.io.File(slozka, "pruh.png"))
                    }
                    konec()
                }.apply { isRepeats = false; start() }
            }.apply { isRepeats = false; start() }
        }
    }

    private fun svislePosuvne(c: JComponent) = JScrollPane(c).apply {
        border = null
        viewport.background = B.BG
        verticalScrollBar.unitIncrement = 24
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    }

    // zbylé místo dole, ať se řádky nerozjedou po výšce okna
    private fun stranka(vararg deti: Component) = sloupec(*deti, Box.createVerticalGlue()).apply {
        isOpaque = true; background = B.BG; border = EmptyBorder(28, 32, 28, 32)
    }

    // ---- menu vlevo ----

    private fun menu(): JComponent {
        val panel = object : JPanel() {
            override fun paintComponent(g: Graphics) { g.color = B.SIDEBAR; g.fillRect(0, 0, width, height) }
        }
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = EmptyBorder(24, 14, 20, 14)
        panel.preferredSize = Dimension(220, 0)
        panel.add(popisek("ReCaption", 26, B.ACCENT, true).vlevo().apply { border = EmptyBorder(0, 12, 26, 0) })
        listOf("Domů", "Titulky").forEach { panel.add(polozkaMenu(it, it)) }
        panel.add(Box.createVerticalGlue())
        panel.add(polozkaMenu("⚙  Nastavení", "Nastavení"))
        return panel
    }

    private fun polozkaMenu(text: String, cil: String): JComponent {
        val p = object : JPanel(BorderLayout()) {
            var nad = false
            override fun paintComponent(g: Graphics) {
                val vybrana = stranka == cil
                if (!vybrana && !nad && !isFocusOwner) return
                val g2 = g.hladce()
                g2.color = if (vybrana || isFocusOwner) B.FOCUS else B.CARD
                g2.fillRoundRect(0, 0, width, height, 18, 18)
                if (vybrana) { g2.color = B.ACCENT; g2.fillRoundRect(0, 8, 5, height - 16, 4, 4) }
                if (isFocusOwner) {
                    g2.color = B.ACCENT; g2.stroke = BasicStroke(3f); g2.drawRoundRect(1, 1, width - 3, height - 3, 18, 18)
                }
            }
            override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
        p.isOpaque = false
        p.isFocusable = true
        p.alignmentX = Component.LEFT_ALIGNMENT
        p.border = EmptyBorder(12, 18, 12, 12)
        p.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        // ozubené kolo v písmu Segoe UI není
        p.add(popisek(text, 18, B.TEXT, true).apply { if ('⚙' in text) font = Font(Os.PISMO_ZNAKY, Font.BOLD, 18) })
        val otevri = { prepni(cil) }
        p.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = otevri()
            override fun mouseEntered(e: MouseEvent) { p.nad = true; p.repaint() }
            override fun mouseExited(e: MouseEvent) { p.nad = false; p.repaint() }
        })
        p.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) { if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_SPACE) otevri() }
        })
        p.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = p.repaint()
            override fun focusLost(e: FocusEvent) = p.repaint()
        })
        polozkyMenu += cil to p
        return sloupec(p, Box.createVerticalStrut(6)).vlevo()
    }

    private fun prepni(cil: String) {
        stranka = cil
        (obsah.layout as CardLayout).show(obsah, cil)
        polozkyMenu.forEach { it.second.repaint() }
        radky.forEach { it.obnov() }
    }

    // ---- Domů ----

    private fun domu(): JComponent {
        val karta = Karta().apply { listOf(rPouziti, rPovoleni, rOkno, rPs5, rPreklad, rHra, rVystup, rCteni, rTitulek).forEach { add(it) } }
        return stranka(
            velkyStav.vlevo(),
            Box.createVerticalStrut(10),
            postup,
            Box.createVerticalStrut(18),
            object : JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)) {
                override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
            }.apply { isOpaque = false; add(hlavniTlacitko) }.vlevo(),
            Box.createVerticalStrut(26),
            karta,
        )
    }

    private fun jazyk() = Lang.of(nast.jazyk)

    private fun obnovDomu() {
        if (nast.ctecka) { obnovDomuCtecka(); return }
        val s = stav
        val bezi = vlakno?.isAlive == true
        val hraje = chiaki?.isAlive == true
        val k = krok
        val pripravuje = priprava?.isAlive == true
        val pripraveno = Nastroje.pripraveno(nast.jazyk)
        val sChiaki = nast.zdroj == "chiaki"
        val exe = if (sChiaki) Chiaki.exe() else null
        val konzole = vybranaKonzole()
        velkyStav.text = when {
            pripravuje -> "Příprava…"
            k != null -> k
            stahovani != null -> "Stahování chiaki-ng…"
            !pripraveno -> "Chybí příprava"
            !bezi -> if (s.faze == Stav.Faze.CHYBA) "Chyba" else "Připraveno"
            s.faze == Stav.Faze.NACITANI -> "Načítání…"
            s.faze == Stav.Faze.BEZ_OKNA -> "Čeká na okno hry"
            else -> "Běží"
        }
        obarviStav()
        postup.isVisible = pripravuje || k != null || stahovani != null || (bezi && s.faze == Stav.Faze.NACITANI)
        postup.isIndeterminate = stahovani == null
        stahovani?.let { postup.value = it }
        val zaneprazdneno = bezi || hraje || k != null
        hlavniTlacitko.text = when {
            zaneprazdneno -> "Zastavit"
            sChiaki -> "Hrát"
            else -> "Spustit"
        }
        hlavniTlacitko.isEnabled = !pripravuje && (zaneprazdneno ||
            (pripraveno && (!sChiaki || (exe != null && konzole != null && stahovani == null))))
        rPouziti.nastav(B.MUTED, "Použití · " + nazevPouziti())
        obnovPovoleni()

        // zdroj obrazu
        when (nast.zdroj) {
            "ukazka" -> { rOkno.nastav(B.MUTED, "Zdroj · ukázková hra"); rPs5.nastav(B.MUTED, "") }
            "okno" -> {
                rPs5.nastav(B.MUTED, "")
                if (bezi && s.faze == Stav.Faze.BEZI || oknoNalezeno) rOkno.nastav(B.OK, "Okno hry · ${nast.okno} · nalezeno")
                else rOkno.nastav(B.WARN, "Okno hry · ${nast.okno} · nenalezeno", "Vybrat okno") { vyberOkno(rOkno) }
            }
            else -> {
                when {
                    stahovani != null -> rOkno.nastav(B.MUTED, "chiaki-ng · stahování ${stahovani} %")
                    exe == null -> rOkno.nastav(B.WARN, "chiaki-ng · chybí", "Stáhnout") { stahnoutChiaki() }
                    hraje -> rOkno.nastav(B.OK, "chiaki-ng · hraje se")
                    else -> rOkno.nastav(B.OK, "chiaki-ng · připraveno")
                }
                val n = ps5
                when {
                    exe == null -> rPs5.nastav(B.MUTED, "")
                    konzole == null -> rPs5.nastav(B.WARN, "PS5 · nespárováno", "Spárovat") { sparovat() }
                    n?.pripravena == true -> rPs5.nastav(B.OK, "PS5 · ${konzole.jmeno} · zapnutá")
                    n?.klid == true -> rPs5.nastav(B.MUTED, "PS5 · ${konzole.jmeno} · klidový režim")
                    ps5Hledano -> rPs5.nastav(B.WARN, "PS5 · ${konzole.jmeno} · nenalezena", "Hledat") { hledatPs5() }
                    else -> rPs5.nastav(B.MUTED, "PS5 · ${konzole.jmeno} · hledání")
                }
            }
        }

        obnovPreklad(s, pripravuje, pripraveno)

        // výstup
        val nazevVystupu = Nastaveni.VYSTUPY.firstOrNull { it.first == nast.vystup }?.second ?: Os.text("Pruh na PC")
        when {
            nast.vystup == "pc" -> rVystup.nastav(B.OK, "Výstup · $nazevVystupu")
            !bezi -> rVystup.nastav(B.MUTED, "Výstup · $nazevVystupu")
            s.televize > 0 -> rVystup.nastav(B.OK, "TV · připojeno")
            else -> rVystup.nastav(B.WARN, "TV · nepřipojeno")
        }

        obnovCteni(s, bezi, null)
        okno.contentPane.revalidate()
    }

    private fun obarviStav() {
        velkyStav.foreground = when (velkyStav.text) {
            "Běží" -> B.OK
            "Chybí příprava", "Čeká na okno hry", "Chyba", "Čeká na zapnutí PS5", "Čeká na PS5" -> B.WARN
            else -> B.TEXT
        }
    }

    /** Překlad a hra – stejné řádky pro hraní na PC i čtečku pro TV. */
    private fun obnovPreklad(s: Stav, pripravuje: Boolean, pripraveno: Boolean) {
        val rezim = if (deeplZapnuty()) "DeepL online + offline" else "offline"
        when {
            pripravuje -> rPreklad.nastav(B.MUTED, "Překlad · ${jazyk().label} · stahování")
            pripraveno && deeplZapnuty() && s.deeplChyba == Deepl.Problem.BAD_KEY ->
                rPreklad.nastav(B.WARN, "DeepL · neplatný klíč", "Zadat klíč") { zadatKlic() }
            pripraveno && deeplZapnuty() && s.deeplChyba == Deepl.Problem.QUOTA ->
                rPreklad.nastav(B.WARN, "DeepL · vyčerpaný limit · offline")
            pripraveno && deeplZapnuty() && s.deeplChyba != null ->
                rPreklad.nastav(B.MUTED, "Překlad · ${jazyk().label} · DeepL nedostupný · offline")
            pripraveno -> rPreklad.nastav(B.OK, "Překlad · ${jazyk().label} · $rezim")
            pripravaChyba -> rPreklad.nastav(B.WARN, "Překlad · ${jazyk().label} · chyba přípravy", "Zkusit znovu") { pripravit() }
            else -> rPreklad.nastav(B.WARN, "Překlad · ${jazyk().label} · chybí", "Připravit") { pripravit() }
        }

        // hra (univerzální profil = řádek není, jako na Androidu)
        if (nast.hra != "all") rHra.nastav(B.OK, "Hra · " + nazevHry(nast.hra))
        else rHra.nastav(B.MUTED, "")
    }

    /** Rychlost čtení a poslední titulek (nebo chyba; [chybaNavic] = chyba čtečky pro TV). */
    private fun obnovCteni(s: Stav, bezi: Boolean, chybaNavic: String?) {
        if (bezi && s.ms > 0) {
            rCteni.nastav(B.MUTED, "Čtení · ${s.ms} ms" + if (deeplZapnuty() && s.deeplMs > 0) " · DeepL ${s.deeplMs} ms" else "")
        } else rCteni.nastav(B.MUTED, "")
        when {
            chybaNavic != null -> rTitulek.nastav(B.WARN, "Chyba · $chybaNavic")
            s.faze == Stav.Faze.CHYBA && s.chyba.isNotEmpty() -> rTitulek.nastav(B.WARN, "Chyba · ${s.chyba}")
            s.preklad.isNotEmpty() -> rTitulek.nastav(B.MUTED, "Titulek · ${s.preklad}")
            else -> rTitulek.nastav(B.MUTED, "")
        }
    }

    /** Mac: řádek jen když povolení chybí; po povolení v Nastavení systému se ReCaption otevírá znovu. */
    private fun obnovPovoleni() {
        if (!Os.mac || nast.ukazka && !nast.ctecka || MacOkna.povoleno()) rPovoleni.nastav(B.MUTED, "")
        else rPovoleni.nastav(B.WARN, "Nahrávání obrazovky · chybí povolení", "Povolit") { MacOkna.pozadej() }
    }

    private fun nazevPouziti() = Nastaveni.POUZITI.firstOrNull { it.first == nast.pouziti }?.second?.substringBefore(" (") ?: Os.text("Hraji na PC")

    /** „LG OLED55B7“ / „Samsung …“ z ohlášení aplikace v TV. */
    private fun nazevTv(a: TvAnnounce): String {
        val znacka = when (a.platform) { TvAnnounce.WEBOS -> "LG"; TvAnnounce.TIZEN -> "Samsung"; else -> "TV" }
        return znacka + (a.model?.let { " $it" } ?: "")
    }

    /**
     * Domů ve čtečce pro TV: PS5 (spárovaná účtem pro ReCaption, nebudí se), chiaki-ng v okně bez zvuku,
     * TV LG / Samsung podle ohlášení, překlad. Stejné krátké stavy jako čtečka na Androidu.
     */
    private fun obnovDomuCtecka() {
        val s = stav
        val bezi = vlakno?.isAlive == true
        val tBezi = cteckaBezi()
        val t = cteckaTv
        val faze = t?.faze
        val pripravuje = priprava?.isAlive == true
        val pripraveno = Nastroje.pripraveno(nast.jazyk)
        val exe = Chiaki.exe()
        val konzole = vybranaKonzole()
        val chybaTv = t?.takeIf { it.faze == CteckaTv.Faze.CHYBA }?.chyba?.ifEmpty { null }
        velkyStav.text = when {
            pripravuje -> "Příprava…"
            stahovani != null -> "Stahování chiaki-ng…"
            !pripraveno -> "Chybí příprava"
            !tBezi && !bezi -> if (chybaTv != null || s.faze == Stav.Faze.CHYBA) "Chyba" else "Připraveno"
            faze == CteckaTv.Faze.KLID -> "Čeká na zapnutí PS5"
            faze == CteckaTv.Faze.NENALEZENA -> "Čeká na PS5"
            faze == CteckaTv.Faze.HLEDANI -> "Hledání PS5…"
            faze == CteckaTv.Faze.PRIPOJOVANI -> "Připojování k PS5…"
            s.faze == Stav.Faze.NACITANI -> "Načítání…"
            chiakiMinim || s.faze == Stav.Faze.BEZ_OKNA -> "Čeká na okno hry"
            else -> "Běží"
        }
        obarviStav()
        postup.isVisible = pripravuje || stahovani != null || (bezi && s.faze == Stav.Faze.NACITANI) ||
            (tBezi && (faze == CteckaTv.Faze.HLEDANI || faze == CteckaTv.Faze.PRIPOJOVANI))
        postup.isIndeterminate = stahovani == null
        stahovani?.let { postup.value = it }
        val zaneprazdneno = bezi || tBezi
        hlavniTlacitko.text = if (zaneprazdneno) "Zastavit" else "Spustit"
        hlavniTlacitko.isEnabled = !pripravuje && (zaneprazdneno ||
            (pripraveno && exe != null && konzole != null && stahovani == null))
        rPouziti.nastav(B.MUTED, "Použití · " + nazevPouziti())
        obnovPovoleni()

        // chiaki-ng: okno smí být za jinými, ne minimalizované
        val spojeno = tBezi && faze == CteckaTv.Faze.BEZI
        when {
            stahovani != null -> rOkno.nastav(B.MUTED, "chiaki-ng · stahování ${stahovani} %")
            exe == null -> rOkno.nastav(B.WARN, "chiaki-ng · chybí", "Stáhnout") { stahnoutChiaki() }
            spojeno && chiakiMinim -> rOkno.nastav(B.WARN, "chiaki-ng · minimalizované", "Obnovit okno") {
                thread(isDaemon = true) { cteckaTv?.okno?.obnov() }
            }
            // např. „okno je moc malé“ (z cteni.py)
            spojeno && bezi && s.faze == Stav.Faze.BEZ_OKNA && s.chyba.isNotEmpty() && !s.chyba.contains(CEKA_NA_OKNO) ->
                rOkno.nastav(B.WARN, "chiaki-ng · ${s.chyba}")
            spojeno -> rOkno.nastav(B.OK, "chiaki-ng · bez zvuku · připojeno")
            else -> rOkno.nastav(B.OK, "chiaki-ng · připraveno")
        }

        // PS5 – spárovaná účtem pro ReCaption; nebudí se
        val n = ps5
        val herni = konzole != null && nast.ucetId.isNotEmpty() && nast.ucetParovani("ctecka", konzole.jmeno) == nast.ucetId
        when {
            exe == null -> rPs5.nastav(B.MUTED, "")
            konzole == null -> rPs5.nastav(B.WARN, "PS5 · nespárováno", "Spárovat") { sparovat() }
            herni -> rPs5.nastav(B.WARN, "PS5 · spárováno herním účtem", "Spárovat znovu") { sparovat() }
            tBezi -> when (faze) {
                CteckaTv.Faze.BEZI -> rPs5.nastav(B.OK, "PS5 · ${konzole.jmeno} · připojeno")
                CteckaTv.Faze.KLID -> rPs5.nastav(B.WARN, "PS5 · ${konzole.jmeno} · v klidovém režimu")
                CteckaTv.Faze.NENALEZENA -> rPs5.nastav(B.WARN, "PS5 · ${konzole.jmeno} · nenalezena")
                CteckaTv.Faze.PRIPOJOVANI -> rPs5.nastav(B.MUTED, "PS5 · ${konzole.jmeno} · připojování")
                else -> rPs5.nastav(B.MUTED, "PS5 · ${konzole.jmeno} · hledání")
            }
            n?.pripravena == true -> rPs5.nastav(B.OK, "PS5 · ${konzole.jmeno} · zapnutá")
            n?.klid == true -> rPs5.nastav(B.MUTED, "PS5 · ${konzole.jmeno} · v klidovém režimu")
            ps5Hledano -> rPs5.nastav(B.WARN, "PS5 · ${konzole.jmeno} · nenalezena", "Hledat") { hledatPs5() }
            else -> rPs5.nastav(B.MUTED, "PS5 · ${konzole.jmeno} · hledání")
        }

        obnovPreklad(s, pripravuje, pripraveno)

        // TV LG / Samsung podle ohlášení aplikace v TV
        val tv = nast.tvNazev.ifEmpty { "TV LG / Samsung" }
        when {
            bezi && s.televize > 0 -> rVystup.nastav(B.OK, "$tv · připojeno")
            bezi -> rVystup.nastav(B.WARN, "$tv · nepřipojeno")
            else -> rVystup.nastav(B.MUTED, "$tv · nepřipojeno")
        }

        obnovCteni(s, bezi && spojeno, chybaTv)
        okno.contentPane.revalidate()
    }

    /** Je okno hry otevřené? Hlídá se i před Spustit, ať Domů hned ukáže, jestli ho aplikace vidí. */
    private fun hlidejOkno() {
        oknoNalezeno = !nast.ctecka && nast.zdroj == "okno" && hra.oblast() != null
        // u chiaki-ng: spárované konzole hned, PS5 v síti každých 10 s (jen když se nehraje / čtečka nehledá sama)
        if ((nast.ctecka || nast.zdroj == "chiaki") && tiku++ % 10 == 0 && chiaki?.isAlive != true && krok == null && !cteckaBezi()) hledatPs5()
        chiakiMinim = cteckaTv?.okno?.minimalizovane() == true
        // poslední ohlášená TV („LG OLED55B7“) – na Domů i před dalším spuštěním
        TvSender.lastSeen?.announce?.let { a -> nazevTv(a).let { if (it != nast.tvNazev) nast.tvNazev = it } }
        hlidejSpanek()
        obnovDomu()
    }

    /** Nezhasínat počítač, dokud běží překlad (hraní na PC i čtečka pro TV) – jen s volbou. */
    private fun hlidejSpanek() =
        nezhasinat.drzet(nast.nezhasinat && (vlakno?.isAlive == true || chiaki?.isAlive == true || cteckaBezi()))

    private fun spustitNeboZastavit() {
        when {
            vlakno?.isAlive == true || chiaki?.isAlive == true || krok != null || cteckaBezi() -> zastavit()
            nast.ctecka -> cistProTv()
            nast.zdroj == "chiaki" -> hrat()
            else -> spustit()
        }
    }

    // ---- chiaki-ng ----

    private fun vybranaKonzole(): Chiaki.Konzole? {
        val jmeno = if (nast.ctecka) nast.konzoleCtecka else nast.konzole
        return konzoleSeznam.firstOrNull { it.jmeno == jmeno } ?: konzoleSeznam.firstOrNull()
    }

    /** Profil chiaki-ng: čtečka pro TV má vlastní (spárování účtem pro ReCaption), hraní na PC výchozí. */
    private fun profilChiaki() = if (nast.ctecka) Chiaki.PROFIL_CTECKY else null

    private fun najdiPs5(k: Chiaki.Konzole): Chiaki.Nalez? {
        val nalezene = Chiaki.hledej()
        return nalezene.firstOrNull { it.jmeno.equals(k.jmeno, ignoreCase = true) } ?: nalezene.singleOrNull()
    }

    @Volatile private var hledaSe = false

    private fun hledatPs5() {
        if (hledaSe) return
        hledaSe = true
        thread(isDaemon = true) {
            try {
                konzoleSeznam = Chiaki.konzole(profilChiaki())
                ps5 = vybranaKonzole()?.let { najdiPs5(it) }
                ps5Hledano = true
            } finally { hledaSe = false }
            SwingUtilities.invokeLater { obnovDomu(); radky.forEach { it.obnov() } }
        }
    }

    private fun stahnoutChiaki() {
        if (stahovani != null) return
        stahovani = 0
        obnovDomu()
        thread(isDaemon = true) {
            val chyba = Chiaki.stahni({ p -> stahovani = p; SwingUtilities.invokeLater { obnovDomu() } }, ::zapis)
            stahovani = null
            zapis(chyba?.let { "chiaki-ng: $it" } ?: "chiaki-ng připraven.")
            if (chyba != null) stav = Stav(Stav.Faze.CHYBA, chyba = "chiaki-ng · $chyba")
            SwingUtilities.invokeLater { obnovDomu(); radky.forEach { it.obnov() } }
            hledatPs5()
        }
    }

    /** Spárování s PS5 v průvodci ReCaptionu ([Parovani]); výsledek si chiaki-ng vezme z registru. */
    private var parovani: Parovani? = null

    private fun sparovat() {
        if (parovani != null) return
        val ctecka = nast.ctecka
        parovani = Parovani(okno, nast, ::zapis, ctecka = ctecka, zavreno = { parovani = null }) { jmeno ->
            if (ctecka) nast.konzoleCtecka = jmeno else nast.konzole = jmeno
            ps5 = null
            hledatPs5()
        }
    }

    private fun selhalo(proc: String) {
        zapis(proc)
        stav = Stav(Stav.Faze.CHYBA, chyba = proc)
    }

    /** Hrát: najít PS5, případně probudit, spustit hru v chiaki-ng přes celou obrazovku a číst titulky. */
    private fun hrat() {
        val exe = Chiaki.exe() ?: return
        val k = vybranaKonzole() ?: return
        krok = "Hledání PS5…"
        stav = Stav(Stav.Faze.STOJI)
        obnovDomu()
        thread(name = "hrat", isDaemon = true) {
            fun ui() = SwingUtilities.invokeLater { obnovDomu() }
            try {
                var n = najdiPs5(k) ?: return@thread selhalo("PS5 · nenalezena v síti")
                if (!n.pripravena) {
                    val cislo = k.probuzeni ?: return@thread selhalo("PS5 · klidový režim · zapni ji ovladačem")
                    krok = "Probouzení PS5…"; ui()
                    Chiaki.probud(n.adresa, cislo)
                    for (i in 1..20) {
                        if (krok == null) return@thread
                        Thread.sleep(1500)
                        n = najdiPs5(k) ?: n
                        if (n.pripravena) break
                        if (i % 4 == 0) Chiaki.probud(n.adresa, cislo)
                    }
                    if (!n.pripravena) return@thread selhalo("PS5 · neprobudila se")
                }
                if (krok == null) return@thread
                ps5 = n
                krok = "Spouštění hry…"; ui()
                val p = Chiaki.hraj(exe, k.jmeno, n.adresa)
                chiaki = p
                zapis("chiaki-ng: ${k.jmeno} (${n.adresa})")
                val oknoChiaki = OknoHry("pid:${p.pid()}")
                for (i in 1..60) {
                    if (oknoChiaki.oblast() != null || !p.isAlive || krok == null) break
                    Thread.sleep(500)
                }
                if (!p.isAlive) {
                    // poslední řádky výpisu chiaki-ng do Záznamu (bez klíčů – ty chiaki-ng nevypisuje)
                    runCatching { java.io.File(Nastroje.slozka, "chiaki-vystup.txt").readLines().takeLast(5).forEach { zapis("chiaki-ng: $it") } }
                    return@thread selhalo("chiaki-ng · spojení s PS5 se nepovedlo")
                }
                if (krok == null) { p.destroy(); return@thread }
                hra = oknoChiaki
                krok = null
                SwingUtilities.invokeLater { spustit("pid:${p.pid()}") }
                p.waitFor()
                zapis("Hraní skončilo.")
                ctecka?.zastav()
            } finally {
                krok = null
                chiaki = null
                ui()
            }
        }
    }

    /**
     * Čtečka pro TV: čtení běží hned (TV se připojí, modely se načtou) a čeká na okno chiaki-ng;
     * [CteckaTv] hlídá PS5, připojí se, až je zapnutá, a předá čtení okno.
     */
    private fun cistProTv() {
        val exe = Chiaki.exe() ?: return
        val k = vybranaKonzole() ?: return
        if (cteckaBezi()) return
        spustit(CEKA_NA_OKNO)
        val t = CteckaTv(exe, k, { ctecka }, ::zapis) { SwingUtilities.invokeLater { obnovDomu() } }
        cteckaTv = t
        cteckaVlakno = thread(name = "ctecka-tv", isDaemon = true) {
            t.bez()
            SwingUtilities.invokeLater { obnovDomu() }
        }
        // okno chiaki-ng se otevře přes celou plochu – ReCaption zpět dopředu, ať je vidět stav
        Timer(12_000) { if (cteckaTv?.faze == CteckaTv.Faze.BEZI) okno.toFront() }.apply { isRepeats = false; start() }
        obnovDomu()
    }

    private fun spustit(oknoHry: String? = null) {
        if (vlakno?.isAlive == true) return
        stav = Stav(Stav.Faze.NACITANI)
        // čtečka pro TV: výstup jen do TV, pruh na PC ne
        val vystup = if (nast.ctecka) "tv" else nast.vystup
        val c = Ctecka(
            jazyk = nast.jazyk,
            okno = when {
                nast.ukazka -> ""
                oknoHry != null -> oknoHry
                else -> nast.okno
            },
            vystup = vystup,
            pruh = if (vystup == "tv") null else pruh,
            pruhUkolu = if (vystup == "tv") null else pruhUkolu,
            log = ::zapis,
            zmena = { s -> stav = s; SwingUtilities.invokeLater { obnovDomu() } },
            chyby = Nastroje.chyby,
            deepl = deeplZapnuty(),
            zaznamy = if (nast.zaznamHry) Nastroje.zaznamy() else null,
            hra = nast.hra,
            pokyny = nast.pokyny,
            ukol = nast.ukol,
            menu = nast.menuHry,
            vzhled = { pruh.vzhled },
            zmenaZTv = ::zmenaZTv,
            stavTv = ::stavProTv,
        )
        ctecka = c
        vlakno = thread(name = "ctecka", isDaemon = true) {
            c.bez()
            // čtečka pro TV bez čtení nemá smysl (např. port pro TV obsazený) – nepřipojovat se k PS5
            if (stav.faze == Stav.Faze.CHYBA) cteckaTv?.zastav()
            SwingUtilities.invokeLater { obnovDomu() }
        }
        obnovDomu()
    }

    private fun zastavit() {
        krok = null
        ctecka?.zastav()
        chiaki?.destroy()
        cteckaTv?.zastav()
        thread(isDaemon = true) {
            vlakno?.join(3000)
            SwingUtilities.invokeLater { obnovDomu() }
        }
    }

    /** Po změně okna, jazyka nebo výstupu se běžící čtení spustí znovu s novým nastavením. */
    private fun restart() {
        if (vlakno?.isAlive != true) { obnovDomu(); return }
        // hra v chiaki-ng běží dál, jen čtení se rozjede znovu nad jejím oknem
        val hraOkno = chiaki?.takeIf { it.isAlive }?.let { "pid:${it.pid()}" }
        ctecka?.zastav()
        thread(isDaemon = true) {
            vlakno?.join(5000)
            // čtečka pro TV: okno chiaki-ng ví CteckaTv (spojení s PS5 se kvůli novému nastavení nepřeruší)
            SwingUtilities.invokeLater { spustit(if (cteckaBezi()) cteckaTv?.oknoNazev ?: CEKA_NA_OKNO else hraOkno) }
        }
    }

    private fun zapis(m: String) {
        val radek = "${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))} $m\n"
        SwingUtilities.invokeLater {
            zaznam.append(radek)
            val navic = zaznam.lineCount - 400
            if (navic > 0) zaznam.replaceRange("", 0, zaznam.getLineStartOffset(navic))
        }
    }

    // ---- příprava (stažení knihoven a překladového modelu) ----

    private fun pripravit() {
        if (priprava?.isAlive == true) return
        pripravaChyba = false
        zapis("Příprava pro jazyk ${nast.jazyk}…")
        val p = runCatching {
            // balíček ke stažení má Python s knihovnami s sebou – příprava pak stáhne jen překladový model
            val pribaleny = Nastroje.pribaleny.exists()
            (if (Os.windows) ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", Nastroje.priprava.path,
                "-Jazyk", nast.jazyk, "-BezSestaveni",
                *(if (pribaleny) arrayOf("-Python", Nastroje.pribaleny.path) else emptyArray()),
            ) else ProcessBuilder(
                // Mac: priprava.sh <jazyk> <složka nástrojů> [přibalený Python]
                "/bin/bash", Nastroje.priprava.path, nast.jazyk, Nastroje.slozka.path,
                *(if (pribaleny) arrayOf(Nastroje.pribaleny.path) else emptyArray()),
            )).redirectErrorStream(true).start()
        }.getOrElse { zapis("Příprava se nespustila: ${it.message}"); pripravaChyba = true; obnovDomu(); return }
        priprava = p
        obnovDomu()
        thread(isDaemon = true) {
            p.inputStream.bufferedReader().forEachLine { if (it.isNotBlank()) zapis(it) }
            val kod = p.waitFor()
            pripravaChyba = kod != 0 || !Nastroje.pripraveno(nast.jazyk)
            zapis(if (pripravaChyba) "Příprava skončila chybou ($kod)." else "Příprava hotová.")
            SwingUtilities.invokeLater { obnovDomu() }
        }
    }

    // ---- Titulky ----

    private fun titulky(): JComponent {
        fun zmenaVzhledu() { novyVzhled(); radky.forEach { it.obnov() } }
        val hra = Karta()
        val profily = GameProfiles.ALL
        // co se překládá navíc k titulkům: každý text hry má vlastní přepínač (jako na Androidu od 0.97.59, dřív jedna
        // volba „Rozsah čtení“); řádek se ukáže jen u her, jejichž profil ten text zná
        val anoNe = listOf("Zapnuto", "Vypnuto")
        val rPokyny = volba("Pokyny pod titulky", anoNe, { if (nast.pokyny) 0 else 1 }) { nast.pokyny = it == 0; zmenaVzhledu() }
        val rUkol = volba("Úkol pod titulky", anoNe, { if (nast.ukol) 0 else 1 }) { nast.ukol = it == 0; restart() }
        val rMenu = volba("Překlad menu hry", anoNe, { if (nast.menuHry) 0 else 1 }) { nast.menuHry = it == 0; restart() }
        // rada, jak hru nastavit, aby se titulky četly (GTA VI: pozadí titulků) – texty z aplikace pro Android
        val rRada = Radek("", { GameProfiles.of(nast.hra).hint?.let { I18n.t(Lang.CS, it + "_val") } ?: "" }) { _ ->
            GameProfiles.of(nast.hra).hint?.let { h ->
                javax.swing.JOptionPane.showMessageDialog(okno, I18n.t(Lang.CS, h + "_desc"), I18n.t(Lang.CS, h),
                    javax.swing.JOptionPane.PLAIN_MESSAGE)
            }
        }
        fun podleHry() {
            val g = GameProfiles.of(nast.hra)
            rRada.isVisible = g.hint != null
            g.hint?.let { h -> rRada.nazev = I18n.t(Lang.CS, h); rRada.obnov() }
            rPokyny.isVisible = g.prompts
            rUkol.isVisible = g.prompts
            rMenu.isVisible = g.menuSpec != null
            hra.revalidate(); hra.repaint()
        }
        hra.add(volba("Hra", profily.map { nazevHry(it.id) }, { profily.indexOfFirst { p -> p.id == nast.hra }.coerceAtLeast(0) }) {
            nast.hra = profily[it].id; podleHry(); restart()
        })
        hra.add(rRada)
        hra.add(rPokyny)
        hra.add(rMenu)
        hra.add(rUkol)
        podleHry()
        val karta = Karta()
        karta.add(volba("Velikost písma", listOf("Podle titulků") + Nastaveni.PISMA.map { "$it" },
            { if (nast.pismo == 0) 0 else Nastaveni.PISMA.indexOf(nast.pismo) + 1 }) {
            nast.pismo = if (it == 0) 0 else Nastaveni.PISMA[it - 1]; zmenaVzhledu()
        })
        karta.add(volba("Barva textu", Nastaveni.BARVY.map { it.first }, { nast.barva }) { nast.barva = it; zmenaVzhledu() })
        karta.add(volba("Tučné písmo", listOf("Ano", "Ne"), { if (nast.tucne) 0 else 1 }) { nast.tucne = it == 0; zmenaVzhledu() })
        karta.add(volba("Neprůhlednost pozadí", Nastaveni.POZADI.map { "$it %" },
            { Nastaveni.POZADI.indexOf(nast.pozadi).coerceAtLeast(0) }) { nast.pozadi = Nastaveni.POZADI[it]; zmenaVzhledu() })
        karta.add(volba("Okraj pruhu", (0..3).map { "$it" }, { nast.okraj }) { nast.okraj = it; zmenaVzhledu() })
        karta.add(volba("Poloha", Poloha.values().map { it.nazev }, { nast.poloha.ordinal }) { nast.poloha = Poloha.values()[it]; zmenaVzhledu() })
        karta.add(volba("Originál pod překladem", listOf("Ne", "Ano"), { if (nast.original) 1 else 0 }) { nast.original = it == 1; zmenaVzhledu() })
        karta.add(Radek("Zkušební pruh", { "" }) { zkusebniPruh() }.also { radky += it })
        return stranka(
            popisek("Hra", 15, B.MUTED, true).vlevo(), Box.createVerticalStrut(8), hra,
            Box.createVerticalStrut(20),
            popisek("Pruh s překladem", 15, B.MUTED, true).vlevo(), Box.createVerticalStrut(8), karta,
        )
    }

    private fun nazevHry(id: String) = GameProfiles.of(id).let { if (it.id == "all") "Univerzální" else it.title }

    /** Vzhled změněný ovladačem TV (OK při hře) – uloží se jako z okna PC (`TvLookPrefs.apply` na Androidu). */
    private fun zmenaZTv(ch: TvSettings.Change) = SwingUtilities.invokeLater {
        when (ch) {
            is TvSettings.Change.Opacity -> nast.pozadi = ch.percent
            is TvSettings.Change.TextSize -> nast.pismo = ch.sp
            is TvSettings.Change.Position -> nast.poloha = Poloha.zTv(ch.position)
            is TvSettings.Change.Original -> nast.original = ch.show
            is TvSettings.Change.Pad -> nast.okraj = ch.pad
            is TvSettings.Change.TextColor -> nast.barva = ch.index
            // barevná jména a velikost originálu PC zatím nemá
            is TvSettings.Change.Names, is TvSettings.Change.OrigSize -> return@invokeLater
        }
        novyVzhled()
        radky.forEach { it.obnov() }
    }

    /** Stav pro TV vlevo nahoře – stejné body jako Domů (konzole, překlad, hra). */
    private fun stavProTv(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        if (nast.ctecka) vybranaKonzole()?.let { k ->
            val t = cteckaTv
            out += when (t?.faze) {
                CteckaTv.Faze.BEZI -> if (chiakiMinim) "pozor" to "chiaki-ng · minimalizované" else "ok" to "${k.jmeno} · připojeno"
                CteckaTv.Faze.KLID -> "pozor" to "${k.jmeno} · v klidovém režimu"
                CteckaTv.Faze.NENALEZENA -> "pozor" to "${k.jmeno} · nenalezena"
                CteckaTv.Faze.CHYBA -> "pozor" to "${k.jmeno} · spojení selhalo"
                CteckaTv.Faze.PRIPOJOVANI -> "info" to "${k.jmeno} · připojování"
                else -> "info" to "${k.jmeno} · hledání"
            }
        } else if (nast.zdroj == "chiaki") vybranaKonzole()?.let { k ->
            out += (if (chiaki?.isAlive == true) "ok" to "${k.jmeno} · hraje se" else "info" to "${k.jmeno} · spárováno")
        }
        val s = stav
        out += when {
            deeplZapnuty() && s.deeplChyba == Deepl.Problem.BAD_KEY -> "pozor" to "DeepL · neplatný klíč"
            deeplZapnuty() && s.deeplChyba == Deepl.Problem.QUOTA -> "pozor" to "DeepL · vyčerpaný limit · offline"
            deeplZapnuty() -> "ok" to "${jazyk().label} · DeepL online + offline"
            else -> "ok" to "${jazyk().label} · offline"
        }
        if (nast.hra != "all") out += "ok" to nazevHry(nast.hra)
        return out
    }

    private fun volba(nazev: String, moznosti: List<String>, aktualni: () -> Int, nastav: (Int) -> Unit): Radek =
        Radek(nazev, { moznosti.getOrElse(aktualni()) { "" } }) { r ->
            vyber(r, moznosti, aktualni()) { nastav(it); r.obnov(); obnovDomu() }
        }.also { radky += it }

    // ---- Nastavení ----

    private fun nastaveni(): JComponent {
        val pole = JTextField(nast.okno).apply {
            font = pismo(16); background = B.CARD2; foreground = B.TEXT; caretColor = B.TEXT
            border = EmptyBorder(9, 12, 9, 12); columns = 18
            maximumSize = Dimension(320, preferredSize.height)
        }
        // název okna se uloží chvilku po psaní (ne po každém písmenu restart čtení)
        val ulozit = Timer(900) {
            val t = pole.text.trim()
            if (t.isNotEmpty() && t != nast.okno) { nast.okno = t; if (chiaki?.isAlive != true) hra = OknoHry(t); restart() }
        }.apply { isRepeats = false }
        pole.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = ulozit.restart()
            override fun removeUpdate(e: DocumentEvent) = ulozit.restart()
            override fun changedUpdate(e: DocumentEvent) = ulozit.restart()
        })
        poleOkna = pole
        val oknoRadek = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            border = EmptyBorder(8, 12, 8, 12)
            add(popisek("Okno hry", 16)); add(Box.createHorizontalGlue()); add(pole); add(Box.createHorizontalStrut(10))
            add(Tlacitko("Vybrat") { vyberOkno(this) })
        }
        val pouziti = Karta().apply {
            add(volba("Použití", Nastaveni.POUZITI.map { it.second },
                { Nastaveni.POUZITI.indexOfFirst { z -> z.first == nast.pouziti }.coerceAtLeast(0) }) { zmenPouziti(it) })
            // obě použití; popis v dialogu při zapnutí (jako confirmOn na Androidu)
            add(volba(Os.text("Nezhasínat PC při překladu"), listOf("Ne", "Ano"), { if (nast.nezhasinat) 1 else 0 }) {
                nast.nezhasinat = it == 1
                if (nast.nezhasinat) javax.swing.JOptionPane.showMessageDialog(okno, Os.text(
                    "<html><b>Nezhasínat PC při překladu</b><br><br>" +
                        "Dokud běží překlad, PC neusne a displej nezhasne ani se nezamkne.<br>" +
                        "Jinak se čtení okna chiaki-ng zastaví nebo zpomalí.<br>" +
                        "Po zastavení překladu nebo zavření ReCaptionu platí zase nastavení systému.</html>"),
                    "ReCaption", javax.swing.JOptionPane.PLAIN_MESSAGE)
                hlidejSpanek()
            })
        }
        val zdroj = Karta().apply {
            add(volba("Hra běží v", Nastaveni.ZDROJE.map { it.second },
                { Nastaveni.ZDROJE.indexOfFirst { z -> z.first == nast.zdroj }.coerceAtLeast(0) }) {
                if (vlakno?.isAlive == true || chiaki?.isAlive == true) zastavit()
                nast.zdroj = Nastaveni.ZDROJE[it].first
                if (nast.zdroj == "chiaki") hledatPs5() else hra = OknoHry(nast.okno)
            }.also { jenPc += it })
            add(Radek("Konzole", { vybranaKonzole()?.jmeno ?: "nespárováno" }) { r ->
                val jmena = konzoleSeznam.map { it.jmeno }
                if (jmena.isEmpty()) sparovat()
                else vyber(r, jmena, jmena.indexOf(vybranaKonzole()?.jmeno)) {
                    if (nast.ctecka) nast.konzoleCtecka = jmena[it] else nast.konzole = jmena[it]
                    ps5 = null; hledatPs5(); r.obnov()
                }
            }.also { radky += it })
            add(Radek("Spárovat PS5", { "" }) { sparovat() }.also { radky += it })
            add(Radek("chiaki-ng", { if (stahovani != null) "stahování" else if (Chiaki.exe() != null) "připraveno" else "chybí" }) {
                if (Chiaki.exe() == null) stahnoutChiaki() else Chiaki.exe()?.parentFile?.let { runCatching { java.awt.Desktop.getDesktop().open(it) } }
            }.also { radky += it })
            add(oknoRadek.also { jenPc += it })
        }
        jenPc.forEach { it.isVisible = !nast.ctecka }
        val preklad = Karta().apply {
            // jen jazyky s texty aplikace: offline modely Argos (priprava.ps1/.sh) pro jazyky jen pro překlad
            // (Lang.ui = false) zatím nikdo neověřil – chorvatštinu, srbštinu ani makedonštinu Argos nejspíš nemá
            val jazyky = Lang.targets.filter { it.ui }
            add(volba("Jazyk překladu", jazyky.map { it.label }, { jazyky.indexOf(jazyk()).coerceAtLeast(0) }) {
                nast.jazyk = jazyky[it].code; pripravaChyba = false; restart()
            })
            // čtečka pro TV posílá jen do TV (uložená volba pro hraní na PC se nemění)
            add(Radek("Výstup", {
                if (nast.ctecka) "TV LG / Samsung"
                else Nastaveni.VYSTUPY.firstOrNull { v -> v.first == nast.vystup }?.second ?: Os.text("Pruh na PC")
            }) { r ->
                if (!nast.ctecka) vyber(r, Nastaveni.VYSTUPY.map { it.second },
                    Nastaveni.VYSTUPY.indexOfFirst { v -> v.first == nast.vystup }.coerceAtLeast(0)) {
                    nast.vystup = Nastaveni.VYSTUPY[it].first; r.obnov(); obnovDomu(); restart()
                }
            }.also { radky += it })
            add(Radek("Příprava", { if (Nastroje.pripraveno(nast.jazyk)) "hotová" else "chybí" }) { pripravit() }.also { radky += it })
        }
        val deeplKarta = Karta().apply {
            add(volba("DeepL online", listOf("Ne", "Ano"), { if (nast.deepl) 1 else 0 }) {
                nast.deepl = it == 1
                if (nast.deepl && !DeeplPc.ma()) zadatKlic() else restart()
            })
            add(Radek("Klíč DeepL", { popisKlice() }) { zadatKlic() }.also { radky += it })
            add(Radek("Klíč na webu DeepL", { "" }) {
                runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(Deepl.KEYS_URL)) }
            }.also { radky += it })
        }
        val zaznamOtevreny = booleanArrayOf(false)
        val zaznamPosuv = JScrollPane(zaznam).apply {
            border = null; preferredSize = Dimension(600, 260); maximumSize = Dimension(Int.MAX_VALUE, 260)
            alignmentX = Component.LEFT_ALIGNMENT; isVisible = false
        }
        val zaznamKarta = Karta()
        zaznamKarta.add(Radek("Záznam", { if (zaznamOtevreny[0]) "▲" else "▼" }) { r ->
            zaznamOtevreny[0] = !zaznamOtevreny[0]; zaznamPosuv.isVisible = zaznamOtevreny[0]; r.obnov(); zaznamKarta.revalidate()
        })
        zaznamKarta.add(zaznamPosuv)
        zaznamKarta.add(volba("Záznam hry do složky", listOf("Ne", "Ano"), { if (nast.zaznamHry) 1 else 0 }) {
            nast.zaznamHry = it == 1; restart()
        })
        zaznamKarta.add(Radek("Složka záznamů", { "" }) {
            val f = Nastroje.zaznamy().apply { mkdirs() }
            runCatching { java.awt.Desktop.getDesktop().open(f) }
        }.also { radky += it })
        return stranka(
            popisek("Použití", 15, B.MUTED, true).vlevo(), Box.createVerticalStrut(8), pouziti,
            Box.createVerticalStrut(20),
            popisek("Hra", 15, B.MUTED, true).vlevo(), Box.createVerticalStrut(8), zdroj,
            Box.createVerticalStrut(20),
            popisek("Překlad", 15, B.MUTED, true).vlevo(), Box.createVerticalStrut(8), preklad,
            Box.createVerticalStrut(20),
            popisek("DeepL", 15, B.MUTED, true).vlevo(), Box.createVerticalStrut(8), deeplKarta,
            Box.createVerticalStrut(20),
            popisek("Ladění", 15, B.MUTED, true).vlevo(), Box.createVerticalStrut(8), zaznamKarta,
        )
    }

    private var poleOkna: JTextField? = null

    /**
     * Použití: hraní na PC / čtečka pro TV. Spárování je pro každé zvlášť (čtečka má vlastní profil
     * chiaki-ng s účtem pro ReCaption), takže přepnutí nic nemaže; popis čtečky v dialogu při zapnutí.
     */
    private fun zmenPouziti(i: Int) {
        val nove = Nastaveni.POUZITI[i].first
        if (nove == nast.pouziti) return
        if (vlakno?.isAlive == true || chiaki?.isAlive == true || krok != null || cteckaBezi()) zastavit()
        nast.pouziti = nove
        konzoleSeznam = emptyList()
        ps5 = null
        ps5Hledano = false
        cteckaTv = null
        jenPc.forEach { it.isVisible = !nast.ctecka }
        hledatPs5()
        radky.forEach { it.obnov() }
        obnovDomu()
        okno.contentPane.revalidate()
        if (nast.ctecka) javax.swing.JOptionPane.showMessageDialog(okno, Os.text(
            "<html><b>Čtečka pro TV LG / Samsung</b><br><br>" +
                "Hra jde z PS5 do TV a hraje se dál ${Parovani.ZELENA} herním účtem.<br>" +
                "PC se připojí přes Remote Play ${Parovani.MODRA} účtem pro ReCaption (druhý uživatel na PS5)<br>" +
                "a překlad pošle do aplikace ReCaption v TV.<br><br>" +
                "PS5 se pro čtečku páruje zvlášť tímto druhým účtem · spárování pro hraní na PC zůstává.<br>" +
                "PS5 se nebudí · připojení až po zapnutí ovladačem.<br>" +
                "Okno chiaki-ng smí být za jinými okny, ne minimalizované · zvuk z PC vypnutý.</html>"),
            "ReCaption", javax.swing.JOptionPane.PLAIN_MESSAGE)
    }

    // ---- DeepL ----

    private fun deeplZapnuty() = nast.deepl && DeeplPc.ma() && Deepl.target(jazyk()) != null

    /** „1a2b…:fx · zbývá 950 000 znaků“ – klíč jen zkráceně. */
    private fun popisKlice(): String {
        val k = DeeplPc.klic() ?: return "chybí"
        val u = DeeplPc.vyuziti ?: return Deepl.masked(k)
        return Deepl.masked(k) + " · zbývá " + String.format("%,d", u.left).replace(',', ' ') + " znaků"
    }

    private fun zadatKlic() {
        val vlozeno = javax.swing.JOptionPane.showInputDialog(okno, "Klíč DeepL (prázdné = smazat)", "ReCaption",
            javax.swing.JOptionPane.PLAIN_MESSAGE) ?: run { radky.forEach { it.obnov() }; obnovDomu(); return }
        if (!DeeplPc.uloz(vlozeno)) {
            javax.swing.JOptionPane.showMessageDialog(okno, "Tohle klíč DeepL není.", "ReCaption", javax.swing.JOptionPane.PLAIN_MESSAGE)
            return
        }
        if (DeeplPc.ma()) nast.deepl = true
        radky.forEach { it.obnov() }
        restart()
        thread(isDaemon = true) {
            val p = DeeplPc.over()
            zapis(when (p) {
                null -> "DeepL: klíč ověřen."
                Deepl.Problem.BAD_KEY -> "DeepL: klíč neplatí."
                Deepl.Problem.QUOTA -> "DeepL: vyčerpaný limit."
                Deepl.Problem.NETWORK -> "DeepL: bez spojení."
                else -> "DeepL: chyba ověření ($p)."
            })
            if (p != null) stav = stav.copy(deeplChyba = p)
            SwingUtilities.invokeLater { radky.forEach { it.obnov() }; obnovDomu() }
        }
    }

    /** Nabídka otevřených oken; z titulku se vezme krátké slovo, které vydrží i změnu titulku. */
    private fun vyberOkno(kde: Component) {
        val okna = otevrenaOkna().filter { it != okno.title }.take(20)
        if (okna.isEmpty()) return
        vyber(kde, okna, -1) { i ->
            val t = okna[i]
            val kratce = listOf("chiaki", "PS Remote Play", "Xbox").firstOrNull { t.contains(it, ignoreCase = true) } ?: t
            nast.okno = kratce
            nast.zdroj = "okno"
            hra = OknoHry(kratce)
            poleOkna?.text = kratce
            radky.forEach { it.obnov() }
            restart()
        }
    }

    private fun konec() {
        nezhasinat.uvolnit()
        ctecka?.zastav()
        chiaki?.destroy()
        cteckaTv?.zastav()
        priprava?.destroy()
        okno.isVisible = false
        thread {
            vlakno?.join(3000)
            cteckaVlakno?.join(2000)
            System.exit(0)
        }
    }
}

fun main() {
    // příkazy z příkazové řádky i z hotové aplikace (ReCaption.app nemá vlastní java):
    // JAVA_TOOL_OPTIONS=-Drecaption.prikaz=--zkouska-mac,--nechat (slova dělí čárka nebo mezera) … viz Main.kt
    System.getProperty("recaption.prikaz")?.let { main(it.split(' ', ',').filter { c -> c.isNotEmpty() }.toTypedArray()); return }
    // seznam her (jména, postavy, menu) ze serveru – bez uloženého se na něj chvilku počká, ať je v nabídce her
    SeznamHer.priprav()
    // Okno aplikace se zvětšuje podle Windows (na handheldu bývá 150–200 %); pruh si polohu okna
    // hry ze skutečných bodů přepočítá sám (Pruh.obnov).
    System.setProperty("awt.useSystemAAFontSettings", "on")
    SwingUtilities.invokeLater {
        UIManager.put("PopupMenu.background", B.CARD2)
        UIManager.put("MenuItem.selectionBackground", B.FOCUS)
        UIManager.put("MenuItem.selectionForeground", B.ACCENT_FOCUS)
        UIManager.put("ScrollBar.thumb", B.CARD2)
        UIManager.put("ScrollBar.track", B.BG)
        UIManager.put("ScrollBar.width", 12)
        UIManager.put("ProgressBar.foreground", B.ACCENT)
        UIManager.put("ProgressBar.background", B.CARD2)
        UIManager.put("Button.disabledText", B.MUTED)
        UIManager.put("OptionPane.background", B.CARD)
        UIManager.put("Panel.background", B.CARD)
        UIManager.put("OptionPane.messageForeground", B.TEXT)
        UIManager.put("OptionPane.messageFont", Font(Os.PISMO, Font.PLAIN, 16))
        UIManager.put("TextField.background", B.CARD2)
        UIManager.put("TextField.foreground", B.TEXT)
        UIManager.put("TextField.caretForeground", B.TEXT)
        UIManager.put("Button.background", B.CARD2)
        UIManager.put("Button.foreground", B.TEXT)
        Aplikace()
    }
}
