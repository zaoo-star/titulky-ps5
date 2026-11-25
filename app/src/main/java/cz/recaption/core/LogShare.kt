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
 * Záznam pro vývojáře v domácí síti (Nastavení → Experimentální → Ladění a hlášení → „Zpřístupnit záznam v domácí síti“, výchozí
 * vypnuto, samo se vypne po [DURATION_MS]). Zařízení pak na portu [PORT] vydá celé hlášení o stavu
 * (stejné jako obrazovka hlášení + záznam událostí, záznam z televize a pády), vždy přes [Redact]:
 *  - `GET /zaznam?k=KÓD` → text,
 *  - `GET /zaznam.json?k=KÓD` → totéž po oddílech v JSON,
 *  - `GET /hlaseni?k=KÓD` → stránka pro telefon (čtvereček na obrazovce hlášení): odkaz ke stažení
 *    a text k označení – hráč ho pak pošle autorovi chatem nebo e-mailem (bez serveru a klíčů),
 *  - `GET /hlaseni.txt?k=KÓD` → text jako soubor ke stažení (i iPhone ho uloží a nasdílí),
 *  - `GET /davky?k=KÓD` → seznam dávek ze Záznamu snímků (JSON), `GET /davka?k=KÓD&n=3` → dávka jako ZIP ([SnapBatches]).
 * Kód je krátký a náhodný, ukazuje se na obrazovce. Jen čtení, jen adresy domácí sítě.
 * Rozhodování je tady bez Androidu (testy `LogShareTest`); server je [LogServer].
 */
object LogShare {
    const val PORT = 8789
    /** Jak dlouho zůstane zapnuté (pak se samo vypne). */
    const val DURATION_MS = 2 * 60 * 60 * 1000L
    /** Po tolika požadavcích se špatným kódem se server zavře (někdo kód zkouší). */
    const val MAX_WRONG = 30

    /** Bez znaků, které jde splést (0/o, 1/l/i). */
    private const val CODE_CHARS = "abcdefghjkmnpqrstuvwxyz23456789"

    fun newCode(random: java.util.Random = java.security.SecureRandom()): String =
        (1..6).map { CODE_CHARS[random.nextInt(CODE_CHARS.length)] }.joinToString("")

    fun validCode(k: String?) = k != null && k.length in 6..16 && k.all { it in CODE_CHARS }

    /** Porovnání kódu bez prozrazení, kolik znaků sedělo. */
    fun sameCode(a: String?, b: String?): Boolean {
        if (a == null || b == null || a.length != b.length) return false
        var d = 0
        for (i in a.indices) d = d or (a[i].code xor b[i].code)
        return d == 0
    }

    enum class Route { TEXT, JSON, PAGE, DOWNLOAD, IMAGE, BATCHES, BATCH, BAD_CODE, NOT_FOUND, BAD_METHOD, BAD_REQUEST }

    /** Co udělat s požadavkem (první řádek HTTP) při platném kódu [code]. */
    fun route(requestLine: String, code: String): Route {
        val p = requestLine.trim().split(' ')
        if (p.size != 3 || !p[2].startsWith("HTTP/")) return Route.BAD_REQUEST
        if (p[0] != "GET") return Route.BAD_METHOD
        val target = p[1]
        val path = target.substringBefore('?').substringBefore('#')
        val kind = when (path) {
            "/zaznam", "/zaznam.txt" -> Route.TEXT
            "/zaznam.json" -> Route.JSON
            "/hlaseni" -> Route.PAGE
            "/hlaseni.txt" -> Route.DOWNLOAD
            "/snimek.jpg" -> Route.IMAGE
            "/davky" -> Route.BATCHES
            "/davka", "/davka.zip" -> Route.BATCH
            else -> return Route.NOT_FOUND
        }
        val k = query(target)["k"]
        return if (sameCode(k, code)) kind else Route.BAD_CODE
    }

    /** Parametry za otazníkem (jen jednoduché hodnoty; %xx a + se převedou). */
    fun query(target: String): Map<String, String> {
        val q = target.substringAfter('?', "").substringBefore('#')
        if (q.isEmpty()) return emptyMap()
        return q.split('&').mapNotNull { kv ->
            val k = kv.substringBefore('=')
            if (k.isEmpty()) null else k to (runCatching { java.net.URLDecoder.decode(kv.substringAfter('=', ""), "UTF-8") }.getOrNull() ?: "")
        }.toMap()
    }

    /** Smí se tahle adresa ptát? Jen zařízení sám (loopback) a domácí síť. */
    fun allowedPeer(host: String?): Boolean =
        host != null && (host == "127.0.0.1" || host == "::1" || host == "0:0:0:0:0:0:0:1" || PhoneLogin.isHomeAddress(host.removePrefix("::ffff:")))

    /** Kolik zbývá do vypnutí (0 = už mělo být vypnuté). */
    fun remainingMs(until: Long, now: Long): Long = (until - now).coerceAtLeast(0)

    /** Platí zapnutí? (Hodiny posunuté o víc než celou dobu dopředu = neplatí.) */
    fun active(until: Long, now: Long): Boolean = until > now && until - now <= DURATION_MS

    /** Zbývající minuty pro obrazovku (zaokrouhleno nahoru). */
    fun minutesLeft(until: Long, now: Long): Long = (remainingMs(until, now) + 59_999) / 60_000

    fun url(ip: String, port: Int, code: String, json: Boolean = false) =
        "http://$ip:$port/zaznam" + (if (json) ".json" else "") + "?k=$code"

    /** Adresa stránky pro telefon (čtvereček na obrazovce hlášení). */
    fun pageUrl(ip: String, port: Int, code: String) = "http://$ip:$port/hlaseni?k=$code"

    /** Název souboru ke stažení: recaption-hlaseni-2026-11-20-2130.txt. */
    fun fileName(stamp: String) = "recaption-hlaseni-" + stamp.filter { it.isDigit() || it == '-' }.take(20) + ".txt"

    private fun html(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /**
     * Stránka pro telefon: velký odkaz ke stažení a celý text (dá se označit a zkopírovat).
     * Schránku ani sdílení stránka z domácí sítě bez https použít nesmí, proto jen tohle.
     */
    fun page(text: String, code: String, file: String, lang: Lang): String {
        val cz = lang == Lang.CS || lang == Lang.SK
        val title = if (cz) "Hlášení ReCaptionu" else "ReCaption report"
        val how = if (cz) "1. Stáhni soubor tlačítkem níž. 2. Pošli ho autorovi aplikace (Messenger, WhatsApp, e-mail – v iPhonu ze Souborů → Stažené, v Androidu ze Stažených). Hesla, klíče ani číslo účtu v něm nejsou."
            else "1. Download the file with the button below. 2. Send it to the app author (Messenger, WhatsApp, e-mail – on iPhone from Files → Downloads, on Android from Downloads). It contains no passwords, keys or account numbers."
        val btn = if (cz) "Stáhnout hlášení" else "Download the report"
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>${html(title)}</title><style>body{font-family:sans-serif;background:#0E1014;color:#E9E6DF;margin:0;padding:16px}" +
            "h1{color:#F2C14E;font-size:22px}a.b{display:block;text-align:center;background:#F2C14E;color:#0E1014;font-weight:bold;" +
            "padding:16px;border-radius:10px;text-decoration:none;font-size:18px;margin:16px 0}" +
            "pre{white-space:pre-wrap;word-break:break-word;font-size:11px;background:#1A1D23;padding:10px;border-radius:8px}</style></head><body>" +
            "<h1>${html(title)}</h1><p>${html(how)}</p>" +
            "<a class=\"b\" href=\"/hlaseni.txt?k=${html(code)}\" download=\"${html(file)}\">${html(btn)}</a>" +
            "<pre>${html(text)}</pre></body></html>"
    }

    /** Celý text z oddílů (název, obsah); prochází [Redact]. */
    fun text(parts: List<Pair<String, String>>, secrets: Collection<String> = emptyList()): String =
        Redact.clean(parts.joinToString("\n\n") { (t, s) -> "=== $t ===\n${s.trimEnd()}" } + "\n", secrets)

    /** Totéž v JSON: {"oddily":[{"nazev":"…","text":"…"}]}; prochází [Redact]. */
    fun json(parts: List<Pair<String, String>>, secrets: Collection<String> = emptyList()): String =
        parts.joinToString(",", "{\"oddily\":[", "]}") { (t, s) ->
            "{\"nazev\":${TvSender.json(t)},\"text\":${TvSender.json(Redact.clean(s, secrets))}}"
        }

    /** Obrázek (snímek obrazovky hry pro ladění) jako odpověď HTTP. */
    fun jpeg(bytes: ByteArray): ByteArray =
        ("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII) + bytes

    /** Odpověď HTTP (text, JSON, stránka nebo soubor ke stažení [download] = název souboru). */
    fun response(status: Int, body: String, json: Boolean = false, htmlPage: Boolean = false, download: String? = null): ByteArray {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Error" }
        val type = when { download != null -> "application/octet-stream"; json -> "application/json"; htmlPage -> "text/html"; else -> "text/plain" }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: $type; charset=utf-8\r\n" +
            (if (download != null) "Content-Disposition: attachment; filename=\"$download\"\r\n" else "") +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Connection: close\r\n\r\n"
        return head.toByteArray(Charsets.US_ASCII) + bytes
    }
}

/**
 * Server záznamu pro vývojáře (viz [LogShare]). Jen GET, jen domácí síť, jen s kódem.
 * [parts] sestaví oddíly hlášení (volá se mimo hlavní vlákno), [secrets] tajné hodnoty pro [Redact],
 * [open] = smí ještě běžet (po vypršení se sám zavře), [onStop] = zavřel se sám.
 */
class LogServer(
    val code: String,
    private val parts: () -> List<Pair<String, String>>,
    private val secrets: () -> Collection<String> = { emptyList() },
    private val open: () -> Boolean = { true },
    private val onStop: () -> Unit = {},
    private val log: (String) -> Unit = {},
    /** Jazyk stránky pro telefon (čeština/slovenština, jinak anglicky). */
    private val lang: () -> Lang = { Lang.CS },
    /** Snímek obrazovky hry (JPEG) pro ladění; null = žádný obraz. */
    private val image: () -> ByteArray? = { null },
    /** Záznam snímků: seznam dávek (JSON, viz [SnapBatches.json]) a složka dávky podle čísla (null = není). */
    private val batches: () -> String = { "{\"davky\":[]}" },
    private val batchDir: (Int) -> java.io.File? = { null },
) {
    @Volatile var port = 0
        private set
    @Volatile private var server: ServerSocket? = null
    @Volatile private var pool: ExecutorService? = null
    @Volatile private var wrong = 0

    val running: Boolean get() = server?.isClosed == false

    @Synchronized fun start(preferredPort: Int = LogShare.PORT, bind: InetAddress? = null): Boolean {
        if (running) return true
        val s = open(preferredPort, bind) ?: open(0, bind) ?: run { log("log cannot listen"); return false }
        server = s
        port = s.localPort
        val p = Executors.newFixedThreadPool(2) { r -> Thread(r, "tp-zaznam").apply { isDaemon = true } }
        pool = p
        Thread({
            while (!s.isClosed) {
                val c = try { s.accept() } catch (_: Throwable) { break }
                try { p.execute { handle(c) } } catch (_: Throwable) { close(c) }
            }
        }, "tp-zaznam-accept").apply { isDaemon = true; start() }
        log("log on port $port")
        return true
    }

    private fun open(p: Int, bind: InetAddress?): ServerSocket? = try {
        ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(bind, p), 4) }
    } catch (_: Throwable) { null }

    @Synchronized fun stop() {
        val s = server ?: return
        server = null
        try { s.close() } catch (_: Throwable) {}
        pool?.shutdown(); pool = null
        log("log closed")
    }

    private fun close(c: Socket) { try { c.close() } catch (_: Throwable) {} }

    private fun handle(c: Socket) {
        try {
            if (!LogShare.allowedPeer(c.inetAddress?.hostAddress)) return
            if (!open()) { send(c, 404, "?"); stopSelf(); return }
            c.soTimeout = 8000
            val head = readHead(c.getInputStream()) ?: return
            when (val r = LogShare.route(head.substringBefore("\r\n"), code)) {
                LogShare.Route.PAGE, LogShare.Route.DOWNLOAD -> {
                    val text = LogShare.text(parts(), secrets())
                    val file = LogShare.fileName(java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.ROOT).format(java.util.Date()))
                    val bytes = if (r == LogShare.Route.PAGE) LogShare.response(200, LogShare.page(text, code, file, lang()), htmlPage = true)
                        else LogShare.response(200, text, download = file)
                    c.getOutputStream().apply { write(bytes); flush() }
                    log("log served " + (if (r == LogShare.Route.PAGE) "page" else "file"))
                }
                LogShare.Route.TEXT, LogShare.Route.JSON -> {
                    val p = parts()
                    val sec = secrets()
                    if (r == LogShare.Route.JSON) send(c, 200, LogShare.json(p, sec), json = true)
                    else send(c, 200, LogShare.text(p, sec))
                    log("log served " + (if (r == LogShare.Route.JSON) "json" else "text"))
                }
                LogShare.Route.IMAGE -> {
                    val img = image()
                    if (img == null) send(c, 404, "?")
                    else { c.getOutputStream().apply { write(LogShare.jpeg(img)); flush() }; log("log served image") }
                }
                LogShare.Route.BATCHES -> { send(c, 200, batches(), json = true); log("log served batches") }
                LogShare.Route.BATCH -> {
                    val target = head.substringBefore("\r\n").split(' ').getOrNull(1) ?: ""
                    val dir = SnapBatches.requested(target)?.let(batchDir)
                    if (dir == null) send(c, 404, "?")
                    else {
                        // ZIP se posílá průběžně (dávka může mít desítky MB), konec = zavření spojení
                        c.soTimeout = 60_000
                        val out = java.io.BufferedOutputStream(c.getOutputStream(), 64 * 1024)
                        out.write(SnapBatches.zipHead(dir.name))
                        SnapBatches.zip(dir, out)
                        out.flush()
                        log("log served batch ${dir.name}")
                    }
                }
                LogShare.Route.BAD_CODE -> {
                    send(c, 403, "?")
                    if (++wrong > LogShare.MAX_WRONG) { log("log too many wrong codes"); stopSelf() }
                }
                LogShare.Route.BAD_METHOD -> send(c, 405, "?")
                LogShare.Route.NOT_FOUND -> send(c, 404, "?")
                LogShare.Route.BAD_REQUEST -> send(c, 400, "?")
            }
        } catch (_: Throwable) {
        } finally { close(c) }
    }

    private fun stopSelf() { stop(); onStop() }

    private fun send(c: Socket, status: Int, body: String, json: Boolean = false) {
        c.getOutputStream().apply { write(LogShare.response(status, body, json)); flush() }
    }

    private fun readHead(input: InputStream): String? {
        val end = "\r\n\r\n"
        val out = ByteArrayOutputStream()
        var match = 0
        while (out.size() < 8 * 1024) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            match = if (b == end[match].code) match + 1 else if (b == end[0].code) 1 else 0
            if (match == end.length) return String(out.toByteArray(), 0, out.size() - end.length, Charsets.ISO_8859_1)
        }
        return null
    }
}
