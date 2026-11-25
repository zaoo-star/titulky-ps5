package cz.recaption.pc

import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Zkouška částí pro Mac bez PlayStationu (`recaption-pc --zkouska-mac`) – pouští ji sestavení na GitHubu
 * (`.github/workflows/mac.yml`) na skutečném Macu. Zapisuje do zkušebního profilu chiaki-ng
 * `recaption-zkouska` a pod zkušební účet v Klíčence; hráčova nastavení se netýká. S `--nechat` po sobě
 * zkušební profil nesmaže (sestavení se pak zeptá chiaki-ng, jestli konzoli vidí: `chiaki list`).
 *
 * Výstup: řádky „OK …“ / „CHYBA …“, kód ukončení 0 jen když prošlo vše.
 */
object MacZkouska {
    const val PROFIL = "recaption-zkouska"
    const val KONZOLE = "PS5-Zkouska"
    private var chyb = 0

    private fun over(co: String, ok: Boolean, podrobnosti: String = "") {
        if (!ok) chyb++
        println((if (ok) "OK    " else "CHYBA ") + co + if (podrobnosti.isEmpty()) "" else " · $podrobnosti")
    }

    private fun zkus(co: String, f: () -> Pair<Boolean, String>) {
        val (ok, p) = try { f() } catch (e: Throwable) { false to e.toString() }
        over(co, ok, p)
    }

    /** Výpis `defaults export` – jde vyzkoušet na jakémkoli počítači. */
    private fun predvolbyZXml() = zkus("předvolby: čtení výpisu") {
        val h = MacPredvolby.zXml(
            """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>registered_hosts.1.rp_key</key>
	<data>
	AAECAwQFBgcICQoLDA0ODw==
	</data>
	<key>registered_hosts.1.server_nickname</key>
	<string>PS5 &amp; spol.</string>
	<key>registered_hosts.size</key>
	<integer>1</integer>
	<key>settings.fullscreen</key>
	<true/>
	<key>settings.zoom</key>
	<real>1.5</real>
	<key>vnorene</key>
	<dict><key>a</key><string>b</string></dict>
</dict>
</plist>""")
        val ok = (h["registered_hosts.1.rp_key"] as? ByteArray)?.contentEquals(ByteArray(16) { it.toByte() }) == true &&
            h["registered_hosts.1.server_nickname"] == "PS5 & spol." && h["registered_hosts.size"] == 1L &&
            h["settings.fullscreen"] == true && h["settings.zoom"] == 1.5 && "vnorene" !in h &&
            MacPredvolby.parametry(byteArrayOf(0, 10, -1)) == listOf("-data", "000aff") &&
            MacPredvolby.parametry(7) == listOf("-int", "7")
        ok to "${h.size} hodnot"
    }

    private fun konzole(jmeno: String, posledni: Int) = Registrace.Konzole(
        jmeno = jmeno,
        mac = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, posledni.toByte()),
        // klíč registrace: text v šestnáctkové soustavě doplněný nulami (z něj je číslo pro probuzení)
        klicRegistrace = "1a2b3c4d".toByteArray().copyOf(16),
        klicRelace = ByteArray(16) { (it * 7 + posledni).toByte() },
        typKlice = 2,
        ap = mapOf("ap_ssid" to "sit", "ap_bssid" to "001122334455", "ap_key" to "@heslo", "ap_name" to "PS5"),
    )

    private fun chiaki() {
        val d = MacPredvolby.domena(PROFIL)
        prikaz("/usr/bin/defaults", "delete", d)
        zkus("chiaki-ng: zápis a čtení spárované konzole") {
            Chiaki.uloz(konzole(KONZOLE, 1), PROFIL)
            Chiaki.uloz(konzole(KONZOLE, 1), PROFIL)        // stejná konzole se přepíše, nepřidá
            Chiaki.uloz(konzole("PS5-Druha", 2), PROFIL)
            val k = Chiaki.konzole(PROFIL)
            val h = MacPredvolby.nacti(d)
            val ok = k.map { it.jmeno } == listOf(KONZOLE, "PS5-Druha") && k[0].probuzeni == 0x1a2b3c4dUL &&
                h["registered_hosts.size"] == 2L && h["registered_hosts.1.target"] == 1000100L &&
                h["registered_hosts.1.rp_key_type"] == 2L && h["registered_hosts.1.ap_key"] == "@@heslo" &&
                (h["registered_hosts.1.server_mac"] as? ByteArray)?.size == 6 &&
                (h["registered_hosts.2.rp_key"] as? ByteArray)?.contentEquals(ByteArray(16) { (it * 7 + 2).toByte() }) == true
            ok to k.joinToString { it.jmeno }
        }
        zkus("chiaki-ng: profil čtečky bez zvuku") {
            Chiaki.pripravProfil(PROFIL)
            val h = MacPredvolby.nacti(d)
            (h["settings.audio_volume"] == 0L && h["settings.window_type"] == "Selected Resolution") to
                "hlasitost ${h["settings.audio_volume"]}"
        }
    }

    private fun klicenka() = zkus("Klíčenka: uložení, čtení a smazání") {
        val ucet = "zkouska"
        val tajne = "0123abcd-4567-89ef-0000-zkouska:fx"
        MacKlicenka.smaz(ucet)
        val ulozeno = MacKlicenka.uloz(ucet, tajne)
        val precteno = MacKlicenka.nacti(ucet)
        MacKlicenka.smaz(ucet)
        (ulozeno && precteno == tajne && MacKlicenka.nacti(ucet) == null) to ""
    }

    private fun okna() {
        zkus("okna: seznam od systému") {
            val o = MacOkna.seznam()
            (o.isNotEmpty()) to "${o.size} oken · Nahrávání obrazovky ${if (MacOkna.povoleno()) "povoleno" else "nepovoleno"}"
        }
        // vlastní okno: systém ho musí vrátit tam, kde ho Swing ukázal (bez titulku okna)
        var okno: JFrame? = null
        SwingUtilities.invokeAndWait {
            okno = zkusebniOkno().apply { setLocation(60, 80); isVisible = true }
        }
        Thread.sleep(2500)
        zkus("okna: poloha okna hry") {
            val o = OknoHry("pid:${ProcessHandle.current().pid()}").oblast()
            val m = meritko()
            val b = okno!!.contentPane.locationOnScreen
            val c = okno!!.contentPane.size
            val ok = o != null && Math.abs(o.x / m - b.x) <= 3 && Math.abs(o.y / m - b.y) <= 6 &&
                Math.abs(o.w / m - c.width) <= 3 && Math.abs(o.h / m - c.height) <= 6
            ok to "systém $o · Swing ${b.x},${b.y} ${c.width}×${c.height} · měřítko $m"
        }
        zkus("okna: nabídka Vybrat okno") {
            val o = otevrenaOkna()
            true to "${o.size}: ${o.take(6).joinToString(" | ")}"
        }
        zkus("pruh: okno pruhu (myš skrz, nad ostatními)") {
            val pruh = Pruh { celaObrazovka() }
            pruh.zkouska()
            Thread.sleep(2500)
            (MacPruh.stav == "nastaveno" && pruh.obdelnik() != null) to "${MacPruh.stav} · ${pruh.obdelnik()}"
        }
        SwingUtilities.invokeAndWait { okno?.dispose() }
    }

    /** Okno s anglickým „titulkem“ dole – stojí místo okna hry při zkoušce snímku (`--zkouska-okno`). */
    fun zkusebniOkno(): JFrame = JFrame("ReCaption zkouska").apply {
        contentPane = object : JComponent() {
            override fun paintComponent(g: Graphics) {
                g.color = Color(30, 40, 60)
                g.fillRect(0, 0, width, height)
                val g2 = g.hladce()
                g2.color = Color.WHITE
                g2.font = Font(Os.PISMO, Font.BOLD, 34)
                val text = "Where are you going tonight?"
                g2.drawString(text, (width - g2.fontMetrics.stringWidth(text)) / 2, height - 70)
            }
        }
        setSize(1280, 760)
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
    }

    fun spust(nechat: Boolean): Int {
        println("ReCaption – zkouška částí pro Mac (${System.getProperty("os.name")} ${System.getProperty("os.version")}, ${System.getProperty("os.arch")})")
        predvolbyZXml()
        if (!Os.mac) {
            println("Není Mac – zbytek se přeskakuje.")
            return if (chyb == 0) 0 else 1
        }
        chiaki()
        klicenka()
        okna()
        if (!nechat) prikaz("/usr/bin/defaults", "delete", MacPredvolby.domena(PROFIL))
        println(if (chyb == 0) "VŠE V POŘÁDKU" else "CHYB: $chyb")
        return if (chyb == 0) 0 else 1
    }
}
