package cz.recaption.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Malá stránka, kterou televize vystaví v domácí síti po dobu přihlašování účtem PlayStation
 * (viz [PhoneLogin]). Přijme jen požadavky s jednorázovým klíčem ze čtverečku na obrazovce:
 *  - GET  → stránka s postupem,
 *  - POST s adresou z telefonu (url=…) → z adresy se vezme kód a [resolveCode] z něj zjistí účet,
 *  - POST s číslem účtu (account=…, posílá ReCaption v telefonu) → účet se rovnou přijme.
 * Bez Androidu, aby šla vyzkoušet v unit testech.
 *
 * Tentýž server slouží i jiné stránce pro telefon ([custom], např. vložení klíče DeepL,
 * viz [forPage]): jednorázový klíč i ochrana zůstávají, text a zpracování formuláře dodá [custom].
 */
class LoginServer(
    private val lang: () -> Lang,
    private val loginUrl: String,
    private val apkUrl: () -> String,
    /** Jednorázový kód z adresy → účet (dotaz na Sony, může trvat pár sekund); null = kód neplatí. */
    private val resolveCode: (String) -> PhoneLogin.Account?,
    private val onAccount: (PhoneLogin.Account) -> Unit,
    private val log: (String) -> Unit = {},
    private val custom: Custom? = null,
    /** Stálý klíč (nastavení TV z telefonu – telefon se připojuje i později); null = nový při každém spuštění. */
    fixedKey: String? = null,
) {
    /** Jiná stránka než přihlášení: [path] = adresa stránky s klíčem (kam se odešle formulář). */
    interface Custom {
        fun get(path: String): String
        /** Odeslaný formulář → stránka s výsledkem (smí trvat pár sekund, běží mimo hlavní vlákno). */
        fun post(path: String, form: Map<String, String>): String
        fun gone(): String
    }

    val key: String = fixedKey?.takeIf { PhoneLogin.validKey(it) } ?: PhoneLogin.newKey()

    @Volatile var port = 0
        private set

    @Volatile private var server: ServerSocket? = null
    @Volatile private var pool: ExecutorService? = null
    @Volatile private var acceptor: Thread? = null
    /** Požadavky se špatným klíčem; po mnoha pokusech se stránka zavře (někdo klíč jen zkouší). */
    @Volatile private var wrongKeys = 0

    val running: Boolean get() = server?.isClosed == false

    /** Začne poslouchat. Vrací false, když se nepodařilo otevřít žádný port. */
    @Synchronized fun start(preferredPort: Int = PhoneLogin.PORT, bind: InetAddress? = null): Boolean {
        if (running) return true
        val s = open(preferredPort, bind) ?: open(0, bind) ?: run { log("page cannot listen"); return false }
        server = s
        port = s.localPort
        val p = Executors.newFixedThreadPool(4) { r -> Thread(r, "tp-login").apply { isDaemon = true } }
        pool = p
        acceptor = Thread({
            while (!s.isClosed) {
                val c = try { s.accept() } catch (_: Throwable) { break }
                try { p.execute { handle(c) } } catch (_: Throwable) { close(c) }
            }
        }, "tp-login-accept").apply { isDaemon = true; start() }
        log("page on port $port")
        return true
    }

    private fun open(p: Int, bind: InetAddress?): ServerSocket? = try {
        ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(bind, p), 8) }
    } catch (_: Throwable) { null }

    @Synchronized fun stop() {
        val s = server ?: return
        server = null
        try { s.close() } catch (_: Throwable) {}
        // Port se opravdu uvolní, až když skončí vlákno, které na něm čekalo. Bez počkání by televize
        // ještě okamžik po zavření stránky navazovala spojení (a hned je zase zavírala).
        try { acceptor?.takeIf { it !== Thread.currentThread() }?.join(1000) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        acceptor = null
        // rozpracované odpovědi se nechají doběhnout (telefon má dostat „hotovo“)
        pool?.shutdown(); pool = null
        log("page closed")
    }

    private fun close(c: Socket) { try { c.close() } catch (_: Throwable) {} }

    private fun handle(c: Socket) {
        try {
            c.soTimeout = 8000
            val input = c.getInputStream()
            val head = readHead(input) ?: return
            val lines = head.split("\r\n")
            val (method, path) = PhoneLogin.requestLine(lines.first()) ?: run { send(c, 400, "?", false); return }
            // tělo se přečte vždy, i když požadavek skončí odmítnutím – jinak by se odpověď mohla ztratit
            val len = PhoneLogin.contentLength(lines.drop(1))
            if (len > MAX_BODY) { send(c, 400, "?", false); return }
            val form = if (method == "POST") PhoneLogin.parseForm(String(readBody(input, len), Charsets.UTF_8)) else emptyMap()
            if (path != PhoneLogin.path(key)) {
                // prohlížeč si sám říká i o ikonu stránky – počítají se jen pokusy o jiný klíč
                if (path.startsWith("/p/") && ++wrongKeys > 40) { log("page too many wrong keys"); stop() }
                send(c, 404, custom?.gone() ?: page(PhoneLogin.Page.GONE, null))
                return
            }
            custom?.let { cu ->
                when (method) {
                    "GET" -> send(c, 200, cu.get(path))
                    "POST" -> send(c, 200, try { cu.post(path, form) } catch (_: Throwable) { cu.get(path) })
                    else -> send(c, 405, "?", false)
                }
                return
            }
            val target = c.localAddress?.hostAddress?.takeIf { PhoneLogin.isHomeAddress(it) }?.let { PhoneLogin.Target(it, port, key) }
            when (method) {
                "GET" -> send(c, 200, page(PhoneLogin.Page.ASK, target))
                "POST" -> {
                    val fromApp = form["app"] == "1"
                    val acc = if (form.containsKey("account")) PhoneLogin.cleanAccount(form["account"], form["name"])
                        else Ps5.authCodeFromTyped(form["url"] ?: "")?.let { code -> try { resolveCode(code) } catch (_: Throwable) { null } }
                    if (acc == null) {
                        log("page bad " + if (fromApp) "account" else "address")
                        if (fromApp) send(c, 400, "BAD", false) else send(c, 200, page(PhoneLogin.Page.BAD, target))
                    } else {
                        log("page account " + if (fromApp) "from app" else "from address")
                        onAccount(acc)
                        if (fromApp) send(c, 200, "OK", false) else send(c, 200, page(PhoneLogin.Page.DONE, target))
                    }
                }
                else -> send(c, 405, "?", false)
            }
        } catch (_: Throwable) {
        } finally { close(c) }
    }

    private fun page(state: PhoneLogin.Page, target: PhoneLogin.Target?) =
        PhoneLogin.page(lang(), state, key, target, loginUrl, apkUrl())

    private fun send(c: Socket, status: Int, body: String, html: Boolean = true) {
        c.getOutputStream().apply { write(PhoneLogin.response(status, body, html)); flush() }
    }

    /** Hlavičky požadavku (po prázdný řádek); null, když jsou podezřele dlouhé nebo spojení skončilo. */
    private fun readHead(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        var match = 0
        while (out.size() < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            match = if (b == END[match].code) match + 1 else if (b == END[0].code) 1 else 0
            if (match == END.length) return String(out.toByteArray(), 0, out.size() - END.length, Charsets.ISO_8859_1)
        }
        return null
    }

    private fun readBody(input: InputStream, len: Int): ByteArray {
        val buf = ByteArray(len)
        var got = 0
        while (got < len) {
            val n = input.read(buf, got, len - got)
            if (n < 0) break
            got += n
        }
        return buf.copyOf(got)
    }

    companion object {
        /** Server pro jinou stránku než přihlášení účtem (viz [Custom]). */
        fun forPage(lang: () -> Lang, custom: Custom, log: (String) -> Unit = {}, fixedKey: String? = null) =
            LoginServer(lang, "", { "" }, { null }, {}, log, custom, fixedKey)

        private const val END = "\r\n\r\n"
        private const val MAX_HEAD = 16 * 1024
        private const val MAX_BODY = 64 * 1024   // celé nastavení z telefonu (vlastní slovník, oblasti)
    }
}

/** Telefon → televize: ReCaption v telefonu pošle televizi číslo účtu. */
object LoginClient {
    enum class Result { OK, REFUSED, UNREACHABLE }

    /** Volat mimo hlavní vlákno. */
    fun send(t: PhoneLogin.Target, a: PhoneLogin.Account, timeoutMs: Int = 6000): Result = try {
        val (status, body) = request(t.host, t.port, "POST", PhoneLogin.path(t.key),
            PhoneLogin.form(mapOf("account" to a.id, "name" to a.name, "app" to "1")), timeoutMs)
        if (status == 200 && body.trim() == "OK") Result.OK else Result.REFUSED
    } catch (_: Throwable) { Result.UNREACHABLE }

    /** Jednoduchý požadavek po domácí síti; vrací stav a tělo odpovědi. */
    fun request(host: String, port: Int, method: String, path: String, body: String = "", timeoutMs: Int = 6000): Pair<Int, String> {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), timeoutMs)
            s.soTimeout = timeoutMs
            val bytes = body.toByteArray(Charsets.UTF_8)
            val head = "$method $path HTTP/1.1\r\nHost: $host:$port\r\n" +
                (if (method == "POST") "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: ${bytes.size}\r\n" else "") +
                "Connection: close\r\n\r\n"
            s.getOutputStream().apply { write(head.toByteArray(Charsets.US_ASCII)); write(bytes); flush() }
            val all = String(s.getInputStream().readBytes(), Charsets.UTF_8)
            val status = all.substringBefore("\r\n").split(' ').getOrNull(1)?.toIntOrNull() ?: 0
            return status to all.substringAfter("\r\n\r\n", "")
        }
    }
}
