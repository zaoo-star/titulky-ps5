package cz.recaption.pc

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import cz.recaption.core.Lang
import cz.recaption.core.LoginServer
import cz.recaption.core.PhoneLogin
import cz.recaption.core.Ps5
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.swing.Box
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.border.EmptyBorder
import kotlin.concurrent.thread

/**
 * Průvodce spárováním s PS5 přímo v ReCaptionu – do okna chiaki-ng se nic nezadává.
 *
 * 1. **PS5** – najde konzoli v síti (musí být zapnutá, ne v klidovém režimu).
 * 2. **Účet** – číslo účtu PlayStation (hlavní účet, na kterém se hraje) bez opisování: buď telefonem
 *    přes stránku z PC (čtvereček, `core/LoginServer` jako na Android TV), nebo přihlášením v prohlížeči
 *    tohoto PC – ReCaption si adresu „redirect“ samo vezme ze schránky. Uloží se jen číslo a jméno účtu.
 * 3. **Kód** – 8 číslic z PS5 (Nastavení → Systém → Remote Play → Spárovat zařízení (ve starším systému Propojit zařízení)), spárování
 *    [Registrace.sparuj] a zápis pro chiaki-ng [Chiaki.uloz].
 *
 * S [ctecka] (čtečka pro TV) se páruje **účtem pro ReCaption** – druhým uživatelem na PS5 s povoleným
 * Remote Play, ne herním účtem (ten by PS5 při hraní vypnul ovladač). Účet se ukládá zvlášť
 * (`ucetCteckaId`), herní účet z hraní na PC se nepřijme a konzole se zapíše do profilu chiaki-ng
 * čtečky ([Chiaki.PROFIL_CTECKY]), takže spárování pro hraní na PC zůstává.
 */
class Parovani(
    private val vlastnik: JFrame,
    private val nast: Nastaveni,
    private val log: (String) -> Unit,
    /** Čtečka pro TV: účet pro ReCaption a profil chiaki-ng čtečky. */
    private val ctecka: Boolean = false,
    private val zavreno: () -> Unit = {},
    /** Po úspěchu: jméno spárované konzole. */
    private val hotovo: (String) -> Unit,
) {
    private val okno = JDialog(vlastnik, "Spárování s PS5", false)
    private val obsah = JPanel(BorderLayout()).apply { background = B.BG; border = EmptyBorder(22, 26, 22, 26) }
    private var krok = 1
    private val casovac = Timer(700) { tik() }

    // 1. PS5
    @Volatile private var nalezene: List<Chiaki.Nalez> = emptyList()
    @Volatile private var hledani = false
    @Volatile private var hledano = false
    private var vybrana = 0
    private val ps5: Chiaki.Nalez? get() = nalezene.getOrNull(vybrana)

    // 2. účet
    @Volatile private var ucet: PhoneLogin.Account? =
        (if (ctecka) nast.ucetCteckaId else nast.ucetId).takeIf { it.isNotEmpty() && !(ctecka && it == nast.ucetId) }
            ?.let { PhoneLogin.cleanAccount(it, if (ctecka) nast.ucetCteckaJmeno else nast.ucetJmeno) }
    @Volatile private var overovani = false
    @Volatile private var spatnaAdresa = false
    /** Čtečka: přihlášený účet je herní (ten, na kterém se hraje na PC) – nepřijme se. */
    @Volatile private var herniUcet = false
    private var server: LoginServer? = null
    private var posledniSchranka = ""

    // 3. kód
    @Volatile private var paruje = false
    @Volatile private var chyba: String? = null
    private var vysledek: String? = null
    private val polePin = JTextField(10).apply {
        font = pismo(30, true); background = B.CARD2; foreground = B.TEXT; caretColor = B.TEXT
        border = EmptyBorder(10, 14, 10, 14); horizontalAlignment = JTextField.CENTER
        maximumSize = Dimension(260, preferredSize.height); alignmentX = Component.LEFT_ALIGNMENT
        addActionListener { sparuj() }
    }

    // řádky stavu (obnovují se v [tik] bez přestavění stránky)
    private val rPs5 = StavRadek()
    private val rUcet = StavRadek()
    private val rKod = StavRadek()
    private val rChiaki = StavRadek()
    private val postup = JProgressBar().apply {
        isIndeterminate = true; foreground = B.ACCENT; background = B.CARD2; border = null
        maximumSize = Dimension(360, 6); preferredSize = Dimension(360, 6); alignmentX = Component.LEFT_ALIGNMENT
    }
    private val dalsi = Tlacitko("Pokračovat", hlavni = true) { pokracuj() }

    init {
        okno.contentPane = obsah
        okno.size = Dimension(760, 600)
        okno.minimumSize = Dimension(640, 520)
        okno.setLocationRelativeTo(vlastnik)
        okno.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) = zavri()
        })
        okno.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        posledniSchranka = schranka()?.trim() ?: "" // starou adresu ze schránky nebrat
        ukaz(1)
        casovac.start()
        hledej()
        okno.isVisible = true
        // jen pro snímky při ladění: -Drecaption.snimek=<složka> uloží po 4 s všechny kroky jako obrázky
        System.getProperty("recaption.snimek")?.let { slozka -> Timer(4000) { snimky(java.io.File(slozka)) }.apply { isRepeats = false; start() } }
    }

    private fun snimky(slozka: java.io.File) {
        slozka.mkdirs()
        for (k in 1..4) {
            if (k == 4) vysledek = "PS5-151"
            ukaz(k)
            obsah.validate()
            val img = BufferedImage(obsah.width, obsah.height, BufferedImage.TYPE_INT_RGB)
            obsah.paint(img.graphics)
            javax.imageio.ImageIO.write(img, "png", java.io.File(slozka, "parovani-$k.png"))
        }
        okno.dispose()
    }

    // ---- stránky ----

    private fun ukaz(k: Int) {
        krok = k
        obsah.removeAll()
        // stránka pro telefon běží jen v kroku s účtem (a jen dokud účet chybí)
        if (k == 2 && ucet == null) spustStranku() else zastavStranku()
        val (nadpis, telo) = when (k) {
            1 -> "PS5 v síti" to stranka1()
            2 -> (if (ctecka) "<html>$MODRA Účet pro Titulky PS5</html>" else "Účet PlayStation") to stranka2()
            3 -> "Kód z PS5" to stranka3()
            else -> "Hotovo" to stranka4()
        }
        val hlava = sloupec(
            popisek(if (k <= 3) "Krok $k z 3" else "Spárováno", 14, B.MUTED).vlevo(),
            Box.createVerticalStrut(4),
            popisek(nadpis, 28, B.TEXT, true).vlevo(),
            Box.createVerticalStrut(18),
        )
        obsah.add(hlava, BorderLayout.NORTH)
        obsah.add(telo, BorderLayout.CENTER)
        obsah.add(dole(), BorderLayout.SOUTH)
        tik()
        obsah.revalidate(); obsah.repaint()
        if (k == 3) SwingUtilities.invokeLater { polePin.requestFocusInWindow() } else dalsi.requestFocusInWindow()
    }

    private fun dole(): JComponent = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0)).apply {
        isOpaque = false
        border = EmptyBorder(16, 0, 0, 0)
        if (krok in 2..3) add(Tlacitko("Zpět") { if (!paruje) ukaz(krok - 1) })
        dalsi.text = when (krok) { 3 -> "Spárovat"; 4 -> "Zavřít"; else -> "Pokračovat" }
        add(dalsi)
    }

    private fun stranka1(): JComponent {
        val karta = Karta().apply { add(rPs5) }
        return sloupec(
            karta, Box.createVerticalStrut(14),
            popisek(Os.text("PS5 zapnutá · stejná síť jako PC"), 15, B.MUTED).vlevo(),
            Box.createVerticalGlue(),
        )
    }

    private fun stranka2(): JComponent {
        val u = ucet
        val legenda = popisek("<html>$MODRA Účet pro Titulky PS5 – druhý uživatel na PS5, jen pro Titulky PS5<br>" +
            "$ZELENA Herní účet – ten, na kterém se hraje</html>", 15, B.MUTED).vlevo()
        if (u != null) {
            return sloupec(Karta().apply { add(rUcet) }, Box.createVerticalStrut(14),
                if (ctecka) legenda else popisek("Hlavní účet · ten, na kterém se na PS5 hraje", 15, B.MUTED).vlevo(),
                Box.createVerticalGlue())
        }
        val qr = server?.let { s -> mojeAdresa(ps5?.adresa)?.let { PhoneLogin.pageUrl(PhoneLogin.Target(it, s.port, s.key)) } }
        val telefon = sloupec(
            popisek("Telefonem", 17, B.ACCENT, true).vlevo(),
            Box.createVerticalStrut(8),
            (qr?.let { JLabel(ImageIcon(ctverecek(it))) } ?: popisek("Stránka pro telefon · nedostupná", 15, B.MUTED)).vlevo(),
            Box.createVerticalStrut(6),
            popisek("Fotoaparát · stejná Wi-Fi", 14, B.MUTED).vlevo(),
        )
        val pc = sloupec(
            popisek(Os.text("V tomto PC"), 17, B.ACCENT, true).vlevo(),
            Box.createVerticalStrut(10),
            object : JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)) {
                override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
            }.apply {
                isOpaque = false; alignmentX = Component.LEFT_ALIGNMENT
                add(Tlacitko("Přihlásit v prohlížeči") { prihlasitVPc() })
            },
            Box.createVerticalStrut(12),
            popisek("<html>1. " + (if (ctecka) "$MODRA Účet pro Titulky PS5, ne herní" else "Hlavní účet PS5") +
                "<br>2. Passkey → Remind Me Later<br>3. Zkopírovat adresu<br>&nbsp;&nbsp;&nbsp;&nbsp;stránky „redirect“</html>", 15, B.MUTED).vlevo(),
        )
        val dvaSloupce = JPanel().apply {
            isOpaque = false; alignmentX = Component.LEFT_ALIGNMENT
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS)
            add(telefon.apply { alignmentY = Component.TOP_ALIGNMENT })
            add(Box.createHorizontalStrut(36))
            add(pc.apply { alignmentY = Component.TOP_ALIGNMENT })
            add(Box.createHorizontalGlue())
        }
        return if (ctecka) sloupec(Karta().apply { add(rUcet) }, Box.createVerticalStrut(10), legenda,
            Box.createVerticalStrut(14), dvaSloupce, Box.createVerticalGlue())
        else sloupec(Karta().apply { add(rUcet) }, Box.createVerticalStrut(16), dvaSloupce, Box.createVerticalGlue())
    }

    private fun stranka3(): JComponent = sloupec(
        if (ctecka) popisek("<html>Na PS5:<br>1. Přepnout na uživatele $MODRA ${PhoneLogin.esc(ucet?.name?.ifEmpty { null } ?: "pro Titulky PS5")}" +
            " (tlačítko PS → profil → Přepnout uživatele)<br>" +
            "2. Nastavení → Systém → Remote Play → zapnout Povolit Remote Play<br>" +
            "3. Tamtéž zapnout Skrýt stav připojení Remote Play<br>" +
            "4. Spárovat zařízení (starší: Propojit zařízení) → osmimístné číslo opsat sem</html>", 15, B.MUTED).vlevo()
        else popisek("PS5: Nastavení → Systém → Remote Play → Spárovat zařízení (ve starším systému Propojit zařízení)", 15, B.MUTED).vlevo(),
        Box.createVerticalStrut(18),
        popisek("Kód · 8 číslic", 16).vlevo(),
        Box.createVerticalStrut(6),
        polePin,
        Box.createVerticalStrut(12),
        postup,
        Box.createVerticalStrut(8),
        Karta().apply { add(rKod); add(rChiaki) },
        Box.createVerticalGlue(),
    )

    private fun stranka4(): JComponent = sloupec(
        Karta().apply { add(StavRadek().apply { nastav(B.OK, "${vysledek ?: "PS5"} · spárováno") }) },
        Box.createVerticalStrut(14),
        if (ctecka) popisek("<html>Na PS5 se přepnout zpět na $ZELENA herní účet</html>", 15, B.MUTED).vlevo()
        else Box.createVerticalStrut(0),
        Box.createVerticalGlue(),
    )

    // ---- stav (každých 0,7 s) ----

    private fun tik() {
        when (krok) {
            1 -> {
                val n = ps5
                when {
                    n?.pripravena == true -> rPs5.nastav(B.OK, "${n.jmeno} · zapnutá · ${n.adresa}",
                        if (nalezene.size > 1) "Jiná PS5" else null) { vybrana = (vybrana + 1) % nalezene.size; tik() }
                    n?.klid == true -> rPs5.nastav(B.WARN, "${n.jmeno} · klidový režim", "Hledat znovu") { hledej() }
                    hledani || !hledano -> rPs5.nastav(B.MUTED, "PS5 · hledání")
                    else -> rPs5.nastav(B.WARN, "PS5 · nenalezena", "Hledat znovu") { hledej() }
                }
                // dokud PS5 není zapnutá, hledá se znovu (zapnutí ovladačem se projeví samo)
                if (n?.pripravena != true && !hledani && hledano) hledej()
                dalsi.isEnabled = n?.pripravena == true
            }
            2 -> {
                val u = ucet
                when {
                    u != null -> rUcet.nastav(B.OK, "Účet · ${u.name.ifEmpty { "ověřen" }}", "Jiný účet") { jinyUcet() }
                    overovani -> rUcet.nastav(B.MUTED, "Účet · ověřování")
                    herniUcet -> rUcet.nastav(B.WARN, "Účet · herní účet · přihlásit účtem pro Titulky PS5")
                    spatnaAdresa -> rUcet.nastav(B.WARN, "Účet · adresa neplatí · přihlásit znovu")
                    else -> rUcet.nastav(B.WARN, "Účet · čeká na přihlášení")
                }
                if (u == null) zkusSchranku()
                dalsi.isEnabled = u != null
            }
            3 -> {
                postup.isVisible = paruje
                val c = chyba
                when {
                    paruje -> rKod.nastav(B.MUTED, "Párování · ${ps5?.jmeno ?: "PS5"}")
                    c != null -> rKod.nastav(B.WARN, c)
                    else -> rKod.nastav(B.MUTED, "")
                }
                val bezi = Chiaki.bezi().isNotEmpty()
                if (bezi) rChiaki.nastav(B.WARN, "chiaki-ng · otevřený", "Zavřít chiaki-ng") {
                    Chiaki.bezi().forEach { it.destroy() }; tik()
                } else rChiaki.nastav(B.MUTED, "")
                dalsi.isEnabled = !paruje && !bezi && Ps5.parsePin(polePin.text) != null
            }
            else -> dalsi.isEnabled = true
        }
    }

    private fun pokracuj() {
        when (krok) {
            1, 2 -> if (dalsi.isEnabled) ukaz(krok + 1)
            3 -> sparuj()
            else -> okno.dispose()
        }
    }

    // ---- 1. hledání PS5 ----

    private fun hledej() {
        if (hledani) return
        hledani = true
        thread(isDaemon = true, name = "parovani-hledani") {
            val nove = runCatching { Chiaki.hledej(1500) }.getOrDefault(emptyList())
            val predtim = ps5?.adresa
            nalezene = nove.sortedByDescending { it.pripravena }
            vybrana = nalezene.indexOfFirst { it.adresa == predtim }.coerceAtLeast(0)
            hledano = true
            hledani = false
            SwingUtilities.invokeLater { tik() }
        }
    }

    // ---- 2. účet ----

    private fun prijmiUcet(a: PhoneLogin.Account) {
        overovani = false
        spatnaAdresa = false
        // čtečka: herní účet (ten, na kterém se hraje na PC) by PS5 při hraní vypnul ovladač
        herniUcet = ctecka && nast.ucetId.isNotEmpty() && a.id == nast.ucetId
        if (herniUcet) {
            log("Přihlášen herní účet · pro čtečku je potřeba účet pro Titulky PS5 (druhý uživatel na PS5).")
            SwingUtilities.invokeLater { tik() }
            return
        }
        ucet = a
        if (ctecka) { nast.ucetCteckaId = a.id; nast.ucetCteckaJmeno = a.name }
        else { nast.ucetId = a.id; nast.ucetJmeno = a.name }
        log("Účet PlayStation ověřen" + if (a.name.isNotEmpty()) " (${a.name})." else ".")
        SwingUtilities.invokeLater { if (krok == 2) ukaz(2) }
    }

    private fun jinyUcet() {
        ucet = null
        herniUcet = false
        if (ctecka) { nast.ucetCteckaId = ""; nast.ucetCteckaJmeno = "" }
        else { nast.ucetId = ""; nast.ucetJmeno = "" }
        ukaz(2)
    }

    private fun prihlasitVPc() {
        posledniSchranka = schranka() ?: ""
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(PsnPc.LOGIN_URL)) }
            .onFailure { log("Prohlížeč se neotevřel: ${it.message}") }
    }

    private fun schranka(): String? = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
    }.getOrNull()

    /** Adresa „redirect“ zkopírovaná v prohlížeči PC → účet (každá zkopírovaná adresa jen jednou). */
    private fun zkusSchranku() {
        if (overovani) return
        val t = schranka()?.trim() ?: return
        if (t == posledniSchranka || t.length > 2000 || !t.contains("code=")) return
        posledniSchranka = t
        val kod = Ps5.authCodeFromTyped(t) ?: return
        overuj(kod)
    }

    private fun overuj(kod: String) {
        overovani = true
        spatnaAdresa = false
        tik()
        thread(isDaemon = true, name = "parovani-ucet") {
            val a = PsnPc.ucet(kod, log)
            if (a != null) prijmiUcet(a) else { overovani = false; spatnaAdresa = true }
            SwingUtilities.invokeLater { tik() }
        }
    }

    private fun spustStranku() {
        if (server?.running == true) return
        val s = LoginServer.forPage({ Lang.CS }, StrankaPc { a -> prijmiUcet(a) }, { log("stránka pro telefon: $it") })
        if (s.start()) server = s
    }

    private fun zastavStranku() {
        server?.stop()
        server = null
    }

    /** Stránka pro telefon: přihlášení u Sony a vložení adresy „redirect“ (hlavní účet, výsledek jde do PC). */
    private inner class StrankaPc(private val prijato: (PhoneLogin.Account) -> Unit) : LoginServer.Custom {
        override fun get(path: String) = strankaTelefonu(path, null, ctecka)
        override fun gone() = strankaTelefonu("", false, ctecka)
        override fun post(path: String, form: Map<String, String>): String {
            val kod = Ps5.authCodeFromTyped(form["url"] ?: "") ?: return strankaTelefonu(path, false, ctecka)
            overovani = true
            SwingUtilities.invokeLater { tik() }
            val a = PsnPc.ucet(kod, log)
            if (a == null) { overovani = false; spatnaAdresa = true; SwingUtilities.invokeLater { tik() } }
            else prijato(a)
            return strankaTelefonu(path, a != null && !herniUcet, ctecka, herni = a != null && herniUcet)
        }
    }

    // ---- 3. spárování ----

    private fun sparuj() {
        if (paruje || !dalsi.isEnabled) return
        val n = ps5 ?: return ukaz(1)
        val u = ucet ?: return ukaz(2)
        val pin = Ps5.parsePin(polePin.text) ?: return
        val cislo = Ps5.parseAccountId(u.id) ?: return ukaz(2)
        paruje = true
        chyba = null
        tik()
        thread(isDaemon = true, name = "parovani") {
            try {
                log("Párování s ${n.jmeno} (${n.adresa})…")
                val k = Registrace.sparuj(n.adresa, cislo, pin, log)
                Chiaki.uloz(k, if (ctecka) Chiaki.PROFIL_CTECKY else null)
                // kterým účtem se párovalo – Domů pak pozná spárování herním účtem ve čtečce
                nast.ucetParovani(if (ctecka) "ctecka" else "pc", k.jmeno, u.id)
                log("Spárováno: ${k.jmeno}.")
                vysledek = k.jmeno
                SwingUtilities.invokeLater { hotovo(k.jmeno); ukaz(4) }
            } catch (e: Registrace.Chyba) {
                chyba = e.stav
            } catch (e: Exception) {
                log("Párování: ${e.javaClass.simpleName} ${e.message ?: ""}")
                chyba = "Párování · chyba"
            } finally {
                paruje = false
                SwingUtilities.invokeLater { tik() }
            }
        }
    }

    private fun zavri() {
        casovac.stop()
        zastavStranku()
        zavreno()
    }

    companion object {
        /** Barevné tečky účtů (jako 🔵 / 🟢 na Androidu – barevné emoji Swing neumí). */
        const val MODRA = "<font color='#5AA9FF'>●</font>"
        const val ZELENA = "<font color='#4ADE80'>●</font>"

        /** Adresa PC v domácí síti pro čtvereček – přednostně ve stejné síti jako PS5. */
        fun mojeAdresa(ps5: String?): String? {
            val adresy = runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                    .flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { it.hostAddress }
                    .filter { PhoneLogin.isHomeAddress(it) }
            }.getOrDefault(emptyList())
            val sit = ps5?.substringBeforeLast('.')
            return adresy.firstOrNull { sit != null && it.substringBeforeLast('.') == sit } ?: adresy.firstOrNull()
        }

        /** Čtvereček (QR kód) s adresou stránky. */
        fun ctverecek(text: String, bod: Int = 5): BufferedImage {
            val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2))
            val img = BufferedImage(m.width * bod, m.height * bod, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until m.height) for (x in 0 until m.width) {
                val c = if (m.get(x, y)) 0x000000 else 0xFFFFFF
                for (dy in 0 until bod) for (dx in 0 until bod) img.setRGB(x * bod + dx, y * bod + dy, c)
            }
            return img
        }

        /**
         * Stránka pro telefon (vzhled jako `PhoneLogin.page` v aplikaci). [ok] = null postup,
         * true hotovo, false chyba (u prázdné cesty: odkaz už neplatí).
         */
        fun strankaTelefonu(path: String, ok: Boolean?, ctecka: Boolean = false, herni: Boolean = false): String {
            val e = PhoneLogin::esc
            val sb = StringBuilder()
            sb.append("<!doctype html><html lang=\"cs\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Titulky PS5</title><style>")
                .append("body{margin:0;background:#0e1014;color:#f2f2f2;font:17px/1.45 system-ui,sans-serif}")
                .append("main{max-width:34em;margin:0 auto;padding:18px 16px 44px}h1{font-size:24px;margin:6px 0 12px}")
                .append("p{margin:8px 0}.card{background:#1b1f27;border-radius:14px;padding:14px 16px;margin:10px 0}")
                .append(".note{color:#f2c14e}.muted{color:#9aa4ae;font-size:15px}")
                .append(".ok{color:#4ade80;font-size:20px;font-weight:600}.bad{color:#ef4444;font-weight:600}")
                .append("a.btn,button{display:block;width:100%;box-sizing:border-box;text-align:center;background:#f2c14e;color:#1a1405;")
                .append("font:600 18px system-ui,sans-serif;border:0;border-radius:12px;padding:14px;margin:12px 0 4px;text-decoration:none}")
                .append("input{width:100%;box-sizing:border-box;font-size:16px;padding:13px;border-radius:12px;border:2px solid #3a4250;")
                .append("background:#0e1014;color:#f2f2f2;margin-top:6px}</style></head><body><main><h1>").append(Os.text("Titulky PS5 pro PC")).append("</h1>")
            when {
                ok == true -> sb.append(Os.text("<p class=\"ok\">Hotovo ✔ Účet ověřen · pokračování v PC</p>"))
                path.isEmpty() -> sb.append(Os.text("<p class=\"bad\">Odkaz už neplatí · v PC znovu otevřít Spárovat</p>"))
                else -> {
                    if (herni) sb.append("<p class=\"bad\">Tohle je 🟢 herní účet · přihlásit 🔵 účtem pro Titulky PS5</p>")
                    else if (ok == false) sb.append("<p class=\"bad\">Adresa neplatí nebo vypršela · přihlásit znovu</p>")
                    sb.append(if (ctecka) "<div class=\"card note\">🔵 Účet pro Titulky PS5 · druhý uživatel na PS5, ne 🟢 herní účet</div><div class=\"card\">"
                        else "<div class=\"card note\">Hlavní účet PlayStation · ten, na kterém se na PS5 hraje</div><div class=\"card\">")
                        .append("<p>1. Přihlášení u Sony (nová karta)</p>")
                        .append("<a id=\"l\" class=\"btn\" target=\"_blank\" rel=\"noopener\" href=\"").append(e(PsnPc.LOGIN_URL)).append("\">Přihlásit se u PlayStation</a>")
                        .append("<p class=\"muted\">Passkey → Remind Me Later. Na konci zůstane prázdná stránka „redirect“.</p>")
                        .append("<form method=\"post\" action=\"").append(e(path)).append("\">")
                        .append("<p>2. Adresu stránky „redirect“ zkopírovat a vložit sem:</p>")
                        .append("<input name=\"url\" id=\"u\" autocomplete=\"off\" autocapitalize=\"off\" spellcheck=\"false\" placeholder=\"https://…/redirect?code=…\">")
                        .append(Os.text("<button type=\"submit\">Odeslat do PC</button></form></div>"))
                        .append(SKRIPT)
                }
            }
            return sb.append("</main></body></html>").toString()
        }

        /** Jako `SCRIPT` v `core/PhoneLogin.kt`: Sony na nové kartě, vložená adresa se odešle sama. */
        private const val SKRIPT = "<script>(function(){" +
            "var u=document.getElementById('u'),f=u.form,l=document.getElementById('l'),b=f.querySelector('button'),busy=false;" +
            "l.addEventListener('click',function(e){var w=null;try{w=window.open(l.href,'_blank')}catch(x){}" +
            "if(w){window.sonyTab=w;e.preventDefault()}});" +
            "function send(){if(busy)return;busy=true;b.style.opacity='.5';var x=new XMLHttpRequest();" +
            "x.open('POST',f.getAttribute('action'));x.setRequestHeader('Content-Type','application/x-www-form-urlencoded');" +
            "x.onload=function(){if(x.responseText.indexOf('class=\"o'+'k\"')>=0){try{if(window.sonyTab)window.sonyTab.close()}catch(e){}}" +
            "document.open();document.write(x.responseText);document.close()};" +
            "x.onerror=function(){f.submit()};x.send('url='+encodeURIComponent(u.value))}" +
            "f.addEventListener('submit',function(e){e.preventDefault();send()});" +
            "u.addEventListener('paste',function(){setTimeout(function(){if(u.value.indexOf('code=')>=0)send()},80)});" +
            "document.addEventListener('visibilitychange',function(){if(!document.hidden&&window.sonyTab){" +
            "try{u.focus();u.scrollIntoView({block:'center'})}catch(e){}}});" +
            "})();</script>"
    }
}
