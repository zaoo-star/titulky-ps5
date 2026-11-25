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

    fun reply(name: String, mac: String?) =
        "{\"typ\":\"${TYP}_pc\",\"pc\":${TvSender.json(name.take(60))}" + (mac?.let { ",\"mac\":\"$it\"" } ?: "") + "}"

    /** Odpověď PC: jméno a MAC (normalizovaná „aa:bb:…“), jinak null. */
    fun parseReply(text: String): Pair<String, String?>? {
        if (text.length > 512) return null
        val m = MiniJson.obj(text) ?: return null
        if (m["typ"] != "${TYP}_pc") return null
        return ((m["pc"] as? String)?.take(60) ?: "PC") to (m["mac"] as? String)?.let { normalizeMac(it) }
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
             targets: List<String> = broadcastTargets()): List<Pair<String, String?>> {
        val replies = ArrayList<Pair<String, String?>>()
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
                    parseReply(String(p.data, 0, p.length, Charsets.UTF_8))?.let { replies += it }
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
                    val action = parseCommand(String(p.data, 0, p.length, Charsets.UTF_8)) ?: continue
                    log("povel z TV $host: $action")
                    val mac = try {
                        // karta, přes kterou se k TV odpovídá (podle místní adresy spojení k ní)
                        DatagramSocket().use { t -> t.connect(from, p.port); macString(NetworkInterface.getByInetAddress(t.localAddress)?.hardwareAddress) }
                    } catch (_: Throwable) { null }
                    try { onCommand(action, from) } catch (_: Throwable) {}
                    val r = reply(name, mac).toByteArray(Charsets.UTF_8)
                    try { s.send(DatagramPacket(r, r.size, from, p.port)) } catch (_: Throwable) {}
                }
            }, "titulky-povely").apply { isDaemon = true; start() }
            log("povely z TV: poslouchám na portu $port")
            return true
        }

        fun stop() { try { socket?.close() } catch (_: Throwable) {} }
    }
}
