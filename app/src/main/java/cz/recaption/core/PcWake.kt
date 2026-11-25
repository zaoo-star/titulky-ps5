package cz.recaption.core

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

/**
 * Titulky PS5 – zapnutí PC z TV (přání majitele 25. 11. 2026: „když si vyberu překlad z PC, ať se to na PC samo zapne“).
 *
 * TV (Android, `PcReceiverService`) pošle do domácí sítě krátký povel UDP na port [PORT] – adresu PC nezná, proto
 * všesměrově (255.255.255.255 i x.y.z.255). Program na PC (`pc/`, [Listener]) ho přijme jen z domácí adresy a:
 *  - `spust` = přepne se do režimu Čtečka pro TV a spustí čtení (připojí se k PS5, TV ho pak najde jako dřív),
 *  - `zastav` = čtení zastaví.
 * PC odpoví jménem a adresou MAC své síťové karty; TV si MAC pamatuje a příště PC i probudí ze spánku
 * ([magicPacket], Wake-on-LAN – musí ho dovolit PC v nastavení síťové karty / BIOSu, obvykle jen kabel).
 */
object PcWake {
    const val PORT = 8788
    /** Port Wake-on-LAN (běžně 9). */
    const val WOL_PORT = 9
    private const val TYP = "titulkyps5"

    fun command(action: String) = "{\"typ\":\"$TYP\",\"akce\":\"$action\"}"

    /** Povel z TV: „spust“ / „zastav“, jinak null. */
    fun parseCommand(text: String): String? {
        if (text.length > 512) return null
        val m = MiniJson.obj(text) ?: return null
        if (m["typ"] != TYP) return null
        return (m["akce"] as? String)?.takeIf { it == "spust" || it == "zastav" }
    }

    /** Odpověď PC na povel. [paired] = PC má PS5 spárovanou pro čtečku (null = starší program, neví se). */
    data class Reply(val name: String, val mac: String?, val paired: Boolean?, val from: String? = null)

    fun reply(name: String, mac: String?, paired: Boolean? = null) =
        "{\"typ\":\"${TYP}_pc\",\"pc\":${TvSender.json(name.take(60))}" + (mac?.let { ",\"mac\":\"$it\"" } ?: "") +
            (paired?.let { ",\"sparovano\":$it" } ?: "") + "}"

    /** Odpověď PC: jméno, MAC (normalizovaná „aa:bb:…“) a stav spárování, jinak null. */
    fun parseReply(text: String, from: String? = null): Reply? {
        if (text.length > 512) return null
        val m = MiniJson.obj(text) ?: return null
        if (m["typ"] != "${TYP}_pc") return null
        return Reply((m["pc"] as? String)?.take(60) ?: "PC", (m["mac"] as? String)?.let { normalizeMac(it) }, m["sparovano"] as? Boolean, from)
    }

    /**
     * Spárování PS5 z TV pro PC (přání majitele: „přihlásím se na TV a na PC už bude“). PS5 při spojení ověřuje jen
     * klíč registrace a klíč relace (chiaki-ng `session.c`: `connect_info.regist_key` / `morning`), oba TV má
     * ([Ps5Host]). Jestli PS5 tytéž klíče přijme i od PC, ověří až první spojení – jinak se PC spáruje samo.
     */
    data class Pairing(val name: String, val ip: String, val registKey: ByteArray, val morning: ByteArray, val mac: ByteArray,
                       val accountId: String?, val accountName: String?)

    fun pairingMessage(p: Pairing): String =
        "{\"typ\":\"$TYP\",\"akce\":\"parovani\",\"konzole\":{\"jmeno\":${TvSender.json(p.name.take(60))}," +
            "\"adresa\":${TvSender.json(p.ip)},\"klic\":\"${Ps5.hex(p.registKey)}\",\"relace\":\"${Ps5.hex(p.morning)}\"," +
            "\"mac\":\"${Ps5.hex(p.mac)}\"}" +
            (p.accountId?.takeIf { it.isNotBlank() }?.let { ",\"ucet\":{\"id\":${TvSender.json(it.take(40))},\"jmeno\":${TvSender.json((p.accountName ?: "").take(60))}}" } ?: "") + "}"

    @Suppress("UNCHECKED_CAST")
    fun parsePairing(text: String): Pairing? {
        if (text.length > 1024) return null
        val m = MiniJson.obj(text) ?: return null
        if (m["typ"] != TYP || m["akce"] != "parovani") return null
        val k = m["konzole"] as? Map<String, Any?> ?: return null
        val key = (k["klic"] as? String)?.let { Ps5.unhex(it) }?.takeIf { it.size == 16 } ?: return null
        val morning = (k["relace"] as? String)?.let { Ps5.unhex(it) }?.takeIf { it.size == 16 } ?: return null
        val mac = (k["mac"] as? String)?.let { Ps5.unhex(it) }?.takeIf { it.size == 6 } ?: return null
        val ip = (k["adresa"] as? String)?.takeIf { Ps5.validHost(it) } ?: ""
        val name = (k["jmeno"] as? String)?.take(60)?.takeIf { it.isNotBlank() } ?: "PS5"
        val u = m["ucet"] as? Map<String, Any?>
        val id = (u?.get("id") as? String)?.take(40)?.takeIf { Ps5.parseAccountId(it) != null }
        return Pairing(name, ip, key, morning, mac, id, (u?.get("jmeno") as? String)?.take(60))
    }

    /** TV: pošle spárování jen tomu PC, které odpovědělo (ne všesměrově – jsou v tom klíče). */
    fun sendPairing(p: Pairing, to: String, port: Int = PORT) {
        if (!PhoneLogin.isHomeAddress(to) && to != "127.0.0.1") return
        try {
            DatagramSocket().use { s ->
                val b = pairingMessage(p).toByteArray(Charsets.UTF_8)
                s.send(DatagramPacket(b, b.size, InetAddress.getByName(to), port))
            }
        } catch (_: Throwable) {}
    }

    /** MAC „AA-BB-CC-DD-EE-FF“ / „aabbccddeeff“ → „aa:bb:cc:dd:ee:ff“; neplatná = null. */
    fun normalizeMac(s: String): String? {
        val hex = s.filter { it.isLetterOrDigit() }.lowercase()
        if (hex.length != 12 || hex.any { it !in "0123456789abcdef" } || hex == "000000000000") return null
        return hex.chunked(2).joinToString(":")
    }

    fun macString(b: ByteArray?): String? = b?.takeIf { it.size == 6 }?.joinToString(":") { String.format("%02x", it.toInt() and 0xFF) }

    /** Paket Wake-on-LAN: 6× 0xFF a 16× MAC. */
    fun magicPacket(mac: String): ByteArray? {
        val m = normalizeMac(mac) ?: return null
        val b = m.split(":").map { it.toInt(16).toByte() }
        return ByteArray(6) { 0xFF.toByte() } + ByteArray(16 * 6) { b[it % 6] }
    }

    /** Všesměrové adresy domácí sítě tohoto zařízení (+ 255.255.255.255). */
    fun broadcastTargets(own: List<String> = PcFinder.ownAddresses()): List<String> =
        (listOf("255.255.255.255") + own.mapNotNull { a -> Regex("^(\\d+\\.\\d+\\.\\d+\\.)\\d+$").find(a)?.groupValues?.get(1)?.plus("255") }).distinct()

    /**
     * TV: probudí PC ([mac], když je známá), pošle povel a [waitMs] čeká na odpovědi PC (jméno, MAC).
     * Chyby sítě se spolknou – povel je jen pomoc, TV hledá PC dál jako dřív.
     */
    fun send(action: String, mac: String? = null, waitMs: Int = 800, port: Int = PORT,
             targets: List<String> = broadcastTargets()): List<Reply> {
        val replies = ArrayList<Reply>()
        try {
            DatagramSocket().use { s ->
                s.broadcast = true
                val cmd = command(action).toByteArray(Charsets.UTF_8)
                val wol = if (action == "spust") mac?.let { magicPacket(it) } else null
                for (t in targets) {
                    val a = try { InetAddress.getByName(t) } catch (_: Throwable) { continue }
                    try { s.send(DatagramPacket(cmd, cmd.size, a, port)) } catch (_: Throwable) {}
                    if (wol != null) try { s.send(DatagramPacket(wol, wol.size, a, WOL_PORT)) } catch (_: Throwable) {}
                }
                if (waitMs <= 0) return replies
                val end = System.currentTimeMillis() + waitMs
                val buf = ByteArray(512)
                while (true) {
                    val left = end - System.currentTimeMillis()
                    if (left <= 0) break
                    s.soTimeout = left.toInt().coerceAtLeast(1)
                    val p = DatagramPacket(buf, buf.size)
                    try { s.receive(p) } catch (_: Throwable) { break }
                    parseReply(String(p.data, 0, p.length, Charsets.UTF_8), p.address?.hostAddress)?.let { replies += it }
                }
            }
        } catch (_: Throwable) {}
        return replies
    }

    /**
     * PC: poslouchá povely z TV. [onCommand] dostane akci a adresu TV; odpověď (jméno a MAC karty, přes kterou povel
     * přišel) se pošle zpět. Jen domácí adresy ([PhoneLogin.isHomeAddress]) a smyčka.
     */
    class Listener(private val name: String, private val port: Int = PORT, private val log: (String) -> Unit = {},
                   /** Má PC PS5 spárovanou pro čtečku? (posílá se v odpovědi, TV pak případně pošle své spárování) */
                   private val paired: () -> Boolean? = { null },
                   /** Spárování poslané z TV ([parsePairing]). */
                   private val onPairing: (Pairing, InetAddress) -> Unit = { _, _ -> },
                   private val onCommand: (String, InetAddress) -> Unit) {
        @Volatile private var socket: DatagramSocket? = null

        /** false = port nejde otevřít (už běží jiný program). */
        fun start(): Boolean {
            val s = try {
                DatagramSocket(null as java.net.SocketAddress?).apply { reuseAddress = true; bind(InetSocketAddress(this@Listener.port)) }
            } catch (t: Throwable) { log("povely z TV: port $port nejde otevřít (${t.javaClass.simpleName})"); return false }
            socket = s
            Thread({
                val buf = ByteArray(512)
                while (!s.isClosed) {
                    val p = DatagramPacket(buf, buf.size)
                    try { s.receive(p) } catch (_: Throwable) { break }
                    val from = p.address ?: continue
                    val host = from.hostAddress ?: continue
                    if (!from.isLoopbackAddress && !PhoneLogin.isHomeAddress(host)) continue
                    val text = String(p.data, 0, p.length, Charsets.UTF_8)
                    val pr = parsePairing(text)
                    if (pr != null) {
                        log("spárování z TV $host: ${pr.name}")
                        try { onPairing(pr, from) } catch (_: Throwable) {}
                        continue
                    }
                    val action = parseCommand(text) ?: continue
                    log("povel z TV $host: $action")
                    val mac = try {
                        // karta, přes kterou se k TV odpovídá (podle místní adresy spojení k ní)
                        DatagramSocket().use { t -> t.connect(from, p.port); macString(NetworkInterface.getByInetAddress(t.localAddress)?.hardwareAddress) }
                    } catch (_: Throwable) { null }
                    try { onCommand(action, from) } catch (_: Throwable) {}
                    val r = reply(name, mac, try { paired() } catch (_: Throwable) { null }).toByteArray(Charsets.UTF_8)
                    try { s.send(DatagramPacket(r, r.size, from, p.port)) } catch (_: Throwable) {}
                }
            }, "titulky-povely").apply { isDaemon = true; start() }
            log("povely z TV: poslouchám na portu $port")
            return true
        }

        fun stop() { try { socket?.close() } catch (_: Throwable) {} }
    }
}
