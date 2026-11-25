package cz.recaption.core

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Titulky PS5 – „Překlad z PC“: Android TV jen ukazuje titulky, čte a překládá ReCaption pro PC (pc/, režim Čtečka pro TV).
 *
 * PC je server ([TvSender], port [TvSender.PORT]) – stejný jako pro aplikace v televizích LG / Samsung
 * (tizen/app/app.js): televize projde adresy své sítě, kdo odpoví `{"typ":"vitej"}`, je ReCaption. Pak chodí zprávy
 * `ukaz` / `skryj` / `ukol` / `telefon` ([PcMessage]); televize po `ukaz` a `skryj` odpoví `hotovo`.
 *
 * Bez Androidu, aby šlo vyzkoušet v unit testech proti skutečnému [TvSender].
 */
class WsClient private constructor(private val socket: Socket, private val input: InputStream, private val out: OutputStream) {
    @Volatile var closed = false
        private set

    /** PC po dobu limitu ([idleTimeout]) nic neposlalo – spojení ale platí dál (nic se nepřečetlo napůl). */
    class Idle : Exception()

    /**
     * Limit ticha v ms (0 = čekat bez omezení). Po spojení je 0 – dřív zůstal limit 1,5 s z hledání a chvíle bez
     * titulků spojení shodila (TV pak PC pořád hledala znovu).
     */
    fun idleTimeout(ms: Int) { try { socket.soTimeout = ms } catch (_: Throwable) {} }

    /** Další textová zpráva; "" = jiný rámec (ping…), null = konec spojení. Dlouhé ticho = [Idle]. */
    fun read(): String? {
        val b0 = try { input.read() } catch (_: java.net.SocketTimeoutException) { throw Idle() } catch (_: Throwable) { -1 }
        val r = if (b0 < 0) null else try { readFrame(input, b0) } catch (_: Throwable) { null }
        if (r == null) close()
        return r
    }

    @Synchronized fun send(text: String): Boolean = try {
        out.write(maskedFrame(text)); out.flush(); true
    } catch (_: Throwable) { close(); false }

    fun close() {
        closed = true
        try { socket.close() } catch (_: Throwable) {}
    }

    val address: String get() = socket.inetAddress?.hostAddress ?: "?"

    companion object {
        /** Nejdelší zpráva z PC (telefon s obrázky ~45 kB; rezerva). */
        const val MAX_IN = 4 * 1024 * 1024
        private val rnd = SecureRandom()

        /**
         * Připojí se a domluví WebSocket. null = nikdo neposlouchá, odpověď není WebSocket nebo nesedí klíč.
         * [timeoutMs] platí pro spojení i odpověď; potom se čte bez časového limitu.
         */
        fun connect(host: String, port: Int = TvSender.PORT, timeoutMs: Int = 1500): WsClient? {
            val s = Socket()
            try {
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host, port), timeoutMs)
                s.soTimeout = timeoutMs
                val key = Base64.getEncoder().encodeToString(ByteArray(16).also { rnd.nextBytes(it) })
                val out = s.getOutputStream()
                out.write(("GET / HTTP/1.1\r\nHost: $host:$port\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray(Charsets.US_ASCII))
                out.flush()
                val input = BufferedInputStream(s.getInputStream())
                val head = readHead(input) ?: run { s.close(); return null }
                val lines = head.split("\r\n")
                if (lines.firstOrNull()?.contains(" 101") != true) { s.close(); return null }
                val accept = lines.drop(1).firstOrNull { it.substringBefore(':').trim().equals("Sec-WebSocket-Accept", true) }
                    ?.substringAfter(':')?.trim()
                if (accept != TvSender.acceptKey(key)) { s.close(); return null }
                return WsClient(s, input, out)
            } catch (_: Throwable) {
                try { s.close() } catch (_: Throwable) {}
                return null
            }
        }

        /** Rámec od klienta musí být maskovaný (RFC 6455). */
        fun maskedFrame(text: String): ByteArray {
            val data = text.toByteArray(Charsets.UTF_8)
            val o = ByteArrayOutputStream(data.size + 14)
            o.write(0x81)
            when {
                data.size < 126 -> o.write(0x80 or data.size)
                data.size < 65536 -> { o.write(0x80 or 126); o.write(data.size shr 8 and 0xFF); o.write(data.size and 0xFF) }
                else -> { o.write(0x80 or 127); for (sh in 56 downTo 0 step 8) o.write(((data.size.toLong() shr sh) and 0xFF).toInt()) }
            }
            val mask = ByteArray(4).also { rnd.nextBytes(it) }
            o.write(mask)
            for (i in data.indices) o.write(data[i].toInt() xor mask[i % 4].toInt())
            return o.toByteArray()
        }

        /** Rámec od serveru (nemaskovaný, i dlouhý – obrázky telefonu). */
        fun readFrame(input: InputStream): String? {
            val b0 = input.read()
            if (b0 < 0) return null
            return readFrame(input, b0)
        }

        /** Zbytek rámce, když první bajt [b0] už je přečtený. */
        fun readFrame(input: InputStream, b0: Int): String? {
            val b1 = input.read()
            if (b1 < 0) return null
            val opcode = b0 and 0x0F
            var len = (b1 and 0x7F).toLong()
            if (len == 126L) {
                val hi = input.read(); val lo = input.read()
                if (hi < 0 || lo < 0) return null
                len = ((hi shl 8) or lo).toLong()
            } else if (len == 127L) {
                len = 0
                repeat(8) { val b = input.read(); if (b < 0) return null; len = (len shl 8) or b.toLong() }
            }
            if (len < 0 || len > MAX_IN) return null
            val mask = if (b1 and 0x80 != 0) ByteArray(4).also { if (!fully(input, it)) return null } else null
            val data = ByteArray(len.toInt())
            if (!fully(input, data)) return null
            if (mask != null) for (i in data.indices) data[i] = (data[i].toInt() xor mask[i % 4].toInt()).toByte()
            return when (opcode) {
                0x8 -> null
                0x1 -> String(data, Charsets.UTF_8)
                else -> ""
            }
        }

        private fun fully(input: InputStream, buf: ByteArray): Boolean {
            var got = 0
            while (got < buf.size) { val n = input.read(buf, got, buf.size - got); if (n < 0) return false; got += n }
            return true
        }

        private fun readHead(input: InputStream): String? {
            val end = "\r\n\r\n"
            val o = ByteArrayOutputStream()
            var match = 0
            while (o.size() < 8192) {
                val b = input.read()
                if (b < 0) return null
                o.write(b)
                match = if (b == end[match].code) match + 1 else if (b == end[0].code) 1 else 0
                if (match == end.length) return String(o.toByteArray(), 0, o.size() - end.length, Charsets.ISO_8859_1)
            }
            return null
        }
    }
}

/**
 * Vzhled pruhu, jak ho nastavilo PC (`TvLook.json`): Titulky PS5 se nastavují jen na PC (přání majitele 25. 11. 2026),
 * TV ho jen použije. [textPx] = písmo na obrazovce 1080 (sp × 50/22), [position] pres / tesne / nad / stred.
 */
data class PcLook(val opacity: Int, val textPx: Int, val color: Int, val bold: Boolean, val pad: Int,
                  val position: String, val width: Float, val center: Float, val origPercent: Int) {
    /** Písmo v sp jako v nastavení TV (obrácené `TvLook.textPx`). */
    val textSp: Int get() = kotlin.math.round(textPx * 22f / 50f).toInt().coerceIn(12, 48)

    fun layout(base: ScreenLayout): ScreenLayout {
        val l = when (position) {
            "pres" -> base.withPosition(ScreenLayout.Position.OVER)
            "tesne" -> base.withPosition(ScreenLayout.Position.ABOVE_TEXT)
            "nad" -> base.withPosition(ScreenLayout.Position.ABOVE)
            "stred" -> if (center > 0f) base.withBarCenter(center) else base
            else -> base
        }
        return if (width > 0f) l.withBarWidth(width) else l
    }

    companion object {
        fun parse(m: Map<String, Any?>): PcLook? {
            fun num(k: String) = (m[k] as? Number)
            val px = num("pismo")?.toInt() ?: return null
            val color = (m["barva"] as? String)?.removePrefix("#")?.takeIf { it.length == 6 }
                ?.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() } ?: 0xFFFFFFFF.toInt()
            return PcLook(
                opacity = (num("pozadi")?.toInt() ?: 60).coerceIn(0, 100),
                textPx = px.coerceIn(16, 120), color = color, bold = m["tucne"] == true,
                pad = (num("okraj")?.toInt() ?: 1).coerceIn(0, 3),
                position = (m["poloha"] as? String)?.take(10) ?: "",
                width = (num("sirka")?.toFloat() ?: 0f).coerceIn(0f, 1f),
                center = (num("stred")?.toFloat() ?: -1f).coerceIn(-1f, 1f),
                origPercent = (num("pismoOrig")?.toInt() ?: 62).coerceIn(30, 100),
            )
        }
    }
}

/** Zpráva z PC, kterou Android TV umí ukázat. Pole jsou z cizího zařízení – ořezaná a hlídaná. */
sealed class PcMessage {
    /** Titulek: řádky (mluvčí, volby, pokyny), originál a kde byl anglický titulek ([top] < 0 = neví se). */
    data class Show(val id: Long, val segments: List<Segment>, val original: String?,
                    val top: Float, val bottom: Float, val left: Float, val right: Float,
                    /** Vzhled pruhu z PC (vše se nastavuje na PC); null = PC ho neposlalo, platí nastavení v TV. */
                    val look: PcLook? = null) : PcMessage()
    data class Hide(val id: Long) : PcMessage()
    /** Úkol; prázdný [text] = schovat. [box] = kde byl anglický úkol. */
    data class Objective(val text: String, val box: FloatArray?) : PcMessage()
    /** Panel telefonu (bubliny): [box] = vlevo, nahoře, šířka, nejnižší spodek; prázdné [msgs] = schovat. */
    data class Phone(val title: String?, val msgs: List<Pair<String, Char>>, val box: FloatArray?) : PcMessage()
    /** Překlad telefonu přímo přes text: odstavce na svých místech, [px] = písmo na obrazovce 1080. */
    data class PhoneOver(val items: List<Pair<String, FloatArray>>, val px: Int, val beside: Boolean) : PcMessage()
    object Hello : PcMessage()

    companion object {
        private const val MAX_TEXT = 4000

        @Suppress("UNCHECKED_CAST")
        fun parse(text: String): PcMessage? {
            if (text.isEmpty() || text.length > WsClient.MAX_IN) return null
            val m = MiniJson.obj(text) ?: return null
            fun str(v: Any?, max: Int = MAX_TEXT) = (v as? String)?.take(max)
            fun box(v: Any?): FloatArray? = (v as? List<*>)?.takeIf { it.size >= 4 }?.take(4)
                ?.map { ((it as? Number)?.toFloat() ?: return null).coerceIn(0f, 1f) }?.toFloatArray()
            return when (m["typ"]) {
                "vitej" -> Hello
                "skryj" -> Hide((m["id"] as? Number)?.toLong() ?: 0)
                "ukaz" -> {
                    val id = (m["id"] as? Number)?.toLong() ?: 0
                    val all = str(m["text"]) ?: ""
                    val parts = (m["casti"] as? List<*>)?.take(40)?.mapNotNull { p ->
                        val o = p as? Map<String, Any?> ?: return@mapNotNull null
                        val t = str(o["text"]) ?: return@mapNotNull null
                        Segment(speaker = str(o["jmeno"], 60)?.takeIf { it.isNotBlank() }, body = t,
                            choice = o["volba"] == true, prompt = o["pokyn"] == true, selected = o["vybrana"] == true)
                    }
                    val segments = parts?.takeIf { it.isNotEmpty() } ?: TextLogic.segments(all)
                    val look = m["vzhled"] as? Map<String, Any?>
                    val t = (look?.get("text") as? List<*>)?.mapNotNull { (it as? Number)?.toFloat()?.coerceIn(0f, 1f) }
                    val ok = t != null && t.size >= 2 && t[1] > t[0]
                    val lr = ok && t!!.size >= 4 && t[3] > t[2]
                    Show(id, segments, str(look?.get("original"))?.takeIf { it.isNotBlank() },
                        if (ok) t!![0] else -1f, if (ok) t!![1] else -1f, if (lr) t!![2] else -1f, if (lr) t!![3] else -1f,
                        look?.let { PcLook.parse(it) })
                }
                "ukol" -> Objective(str(m["text"]) ?: "", box(m["misto"]))
                "telefon" -> {
                    val z = (m["zpravy"] as? List<*>).orEmpty().take(40).mapNotNull { it as? Map<String, Any?> }
                    if (m["rezim"] == "pres") PhoneOver(
                        z.mapNotNull { o -> val t = str(o["text"]) ?: return@mapNotNull null; val b = box(o["misto"]) ?: return@mapNotNull null; t to b },
                        ((m["pismo"] as? Number)?.toInt() ?: 22).coerceIn(12, 60), m["vedle"] == true)
                    else Phone(str(m["nadpis"], 200), z.mapNotNull { o ->
                        val t = str(o["text"]) ?: return@mapNotNull null
                        t to when (o["strana"]) { "p" -> 'p'; "l" -> 'l'; "c" -> 'c'; else -> 's' }
                    }, box(m["misto"]))
                }
                else -> null
            }
        }

        /** Odpověď po ukázání / schování (jako aplikace v TV) – PC podle ní nic nečeká, ale vidí, že TV žije. */
        fun done(id: Long) = "{\"typ\":\"hotovo\",\"id\":$id}"

        /**
         * Verze, kterou se Android TV ohlásí PC: PC podle ní volí tvar zpráv (telefon přes text od 1.5.28, obrázky 1.5.24…,
         * [TvSender.PHONE_OVER_TV]). Odpovídá aplikaci pro TV LG/Samsung (`webos/obchod/app/appinfo.json`), kterou tu napodobujeme.
         */
        const val RECEIVER_VERSION = "1.5.61"

        /** Ohlášení po připojení (jako `pripojeno` z aplikace v TV, [TvAnnounce]). */
        fun announce(model: String, version: String) =
            "{\"typ\":\"pripojeno\",\"televize\":${TvSender.json(model.take(60))},\"system\":\"Android TV\"," +
                "\"aplikace\":\"Titulky PS5\",\"platforma\":\"androidtv\",\"verze\":${TvSender.json(version)}}"
    }
}

/** Hledání PC s ReCaptionem v domácí síti (jako `hledej` v tizen/app/app.js). */
object PcFinder {
    /** Adresy domácí sítě tohoto zařízení (IPv4, ne smyčka). */
    fun ownAddresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }.filter { PhoneLogin.isHomeAddress(it) }
    } catch (_: Throwable) { emptyList() }

    /** Ostatní adresy téže sítě /24 (bez vlastní), nejdřív ty nejblíž běžným adresám routeru a PC. */
    fun candidates(own: String): List<String> {
        val m = Regex("^(\\d+\\.\\d+\\.\\d+\\.)(\\d+)$").find(own) ?: return emptyList()
        val prefix = m.groupValues[1]; val me = m.groupValues[2].toInt()
        return (1..254).filter { it != me }.map { prefix + it }
    }

    /** Ozve se na [host] ReCaption? Vrátí otevřené spojení (už po `vitej`), jinak null. */
    fun probe(host: String, port: Int = TvSender.PORT, timeoutMs: Int = 1500): WsClient? {
        val c = WsClient.connect(host, port, timeoutMs) ?: return null
        val first = try { c.read() } catch (_: Throwable) { null }
        if (first != null && PcMessage.parse(first) == PcMessage.Hello) { c.idleTimeout(0); return c }
        c.close()
        return null
    }

    /**
     * Najde PC: nejdřív [remembered] (minulá adresa), pak celou síť po [parallel] adresách naráz.
     * [stop] = přerušit hledání (vrátí null).
     */
    fun find(remembered: String?, port: Int = TvSender.PORT, parallel: Int = 24, stop: () -> Boolean = { false },
             own: List<String> = ownAddresses()): WsClient? {
        remembered?.takeIf { it.isNotBlank() }?.let { r -> probe(r, port, 2000)?.let { return it } }
        val all = own.flatMap { candidates(it) }.distinct().filter { it != remembered }
        if (all.isEmpty() || stop()) return null
        val found = AtomicReference<WsClient?>(null)
        val pool = Executors.newFixedThreadPool(parallel.coerceIn(1, 64))
        try {
            for (a in all) pool.execute {
                if (found.get() != null || stop()) return@execute
                val c = probe(a, port, 1500) ?: return@execute
                if (!found.compareAndSet(null, c)) c.close()
            }
            pool.shutdown()
            while (!pool.awaitTermination(200, TimeUnit.MILLISECONDS)) {
                if (found.get() != null || stop()) { pool.shutdownNow(); break }
            }
        } finally { pool.shutdownNow() }
        return if (stop()) { found.get()?.close(); null } else found.get()
    }
}
