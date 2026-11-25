package cz.recaption

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import cz.recaption.core.DiagLog
import cz.recaption.core.Redact
import cz.recaption.core.XboxAuth
import cz.recaption.core.XboxFail
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Účet Microsoft pro Xbox: přihlášení „zadej kód na microsoft.com/link“ (jako Netflix v televizi),
 * pak token Xbox Live → XSTS → streamovací token „xhome“ pro vlastní konzoli.
 *
 * Ukládá se jen obnovovací token (zašifrovaný klíčem z Android Keystore, stejně jako klíče televize
 * LG v [LgVault]), jméno hráče a vybraný Xbox. Přístupové tokeny jsou jen v paměti. Žádný token
 * se nikam nevypisuje – do záznamu jdou jen čísla odpovědí a kódy chyb.
 *
 * Podle XboxUcet.kt z projektu PlayCaption (AGPL-3.0), který vychází z xbox-xcloud-player
 * (unknownskl, MIT) a XStreaming (Geocld, AGPL-3.0); upraveno pro ReCaption (šifrované uložení,
 * logika a čtení odpovědí v core/Xbox.kt s testy).
 */
object XboxAccount {
    private const val PREFS = "xbox"
    private const val ALIAS = "recaption-xbox-v1"
    private const val AAD = "xbox:refresh"
    private val LOCK = Any()

    class XboxException(val fail: XboxFail, message: String) : Exception(message)

    @Volatile private var ms: XboxAuth.MsTokens? = null
    @Volatile private var stream: XboxAuth.Stream? = null

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun now() = System.currentTimeMillis()

    /** Řádek XBOX| do záznamu; vždy přes [Redact] i s tokeny z paměti (zprávy od Xboxu se zapisují s obsahem). */
    fun log(msg: String) {
        val m = Redact.clean(msg, memorySecrets())
        Log.i("TP_TEST", "XBOX|$m"); DiagLog.add("XBOX|$m"); EventFile.add("XBOX|$m")
    }

    fun signedIn(c: Context) = prefs(c).contains("rt")
    fun ready(c: Context) = signedIn(c) && consoleId(c) != null
    fun gamertag(c: Context): String = prefs(c).getString("gamertag", "") ?: ""
    fun consoleId(c: Context): String? = prefs(c).getString("console_id", null)
    fun consoleName(c: Context): String = prefs(c).getString("console_name", "Xbox") ?: "Xbox"

    fun setConsole(c: Context, id: String, name: String) =
        prefs(c).edit().putString("console_id", id).putString("console_name", name).apply()

    fun signOut(c: Context) {
        prefs(c).edit().clear().apply()
        ms = null; stream = null
        log("signed out")
    }

    /** Obnovovací token (jen pro [DiagReport.secrets], aby se v hlášení nikdy neobjevil). */
    fun refreshToken(c: Context): String? = prefs(c).getString("rt", null)?.let {
        runCatching { String(LgVault.open(key(), AAD, it), Charsets.UTF_8) }.getOrNull()
    }

    /** Tokeny v paměti (pro skrytí v hlášení). */
    fun memorySecrets(): List<String> = listOfNotNull(ms?.access, stream?.gsToken)

    private fun saveRefresh(c: Context, rt: String) {
        prefs(c).edit().putString("rt", LgVault.seal(key(), AAD, rt.toByteArray(Charsets.UTF_8))).apply()
    }

    private fun key(): SecretKey = synchronized(LOCK) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    // ------------------------------------------------------------------ 1) kód pro microsoft.com/link

    /** Vyžádá kód pro stránku microsoft.com/link. Volat mimo hlavní vlákno. */
    fun requestCode(): XboxAuth.DeviceCode {
        val (st, body) = form(XboxAuth.DEVICE_CODE_URL, "client_id=${XboxAuth.CLIENT_ID}&scope=" + enc(XboxAuth.SCOPE))
        log("kód zařízení: odpověď $st")
        if (st != 200) throw XboxException(XboxFail.NETWORK, "devicecode $st")
        return XboxAuth.parseDeviceCode(body, now()) ?: throw XboxException(XboxFail.NETWORK, "devicecode")
    }

    /**
     * Čeká, až člověk kód zadá: true = přihlášeno, false = kód vypršel / odmítnuto / chyba,
     * null = zrušeno. Volat mimo hlavní vlákno.
     */
    fun waitForLogin(c: Context, code: XboxAuth.DeviceCode, cancelled: () -> Boolean): Boolean? {
        var interval = code.intervalS
        while (!cancelled() && now() < code.expiresAt) {
            for (i in 0 until interval * 10) { if (cancelled()) return null; SystemClock.sleep(100) }
            val (st, body) = runCatching {
                form(XboxAuth.TOKEN_URL, "grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code") +
                    "&client_id=${XboxAuth.CLIENT_ID}&device_code=" + enc(code.deviceCode))
            }.getOrElse { 0 to "" }
            if (st == 200) {
                val t = XboxAuth.parseTokens(body, now())
                if (t?.refresh == null) { log("login: odpověď bez tokenu"); return false }
                saveRefresh(c, t.refresh)
                ms = t; stream = null
                log("signed in")
                // jméno hráče (gamertag) hned, ať ho stránka může ukázat; nevadí, když se nepovede
                runCatching { stream(c) }.onFailure { log("po přihlášení: ${(it as? XboxException)?.fail ?: it.javaClass.simpleName}") }
                return true
            }
            when (val p = XboxAuth.pollState(body)) {
                XboxAuth.Poll.PENDING -> {}
                XboxAuth.Poll.SLOW_DOWN -> interval += 5
                else -> { log("login: $p"); return false }
            }
        }
        if (!cancelled()) log("login: kód vypršel")
        return if (cancelled()) null else false
    }

    // ------------------------------------------------------------------ 2) tokeny pro streamování

    /** Platný token Microsoft (obnoví ho přes obnovovací token). */
    private fun msToken(c: Context): String {
        ms?.let { if (XboxAuth.valid(it.expiresAt, now())) return it.access }
        val rt = refreshToken(c) ?: throw XboxException(XboxFail.AUTH, "nepřihlášeno")
        val (st, body) = runCatching {
            form(XboxAuth.TOKEN_URL, "client_id=${XboxAuth.CLIENT_ID}&grant_type=refresh_token&refresh_token=" + enc(rt) + "&scope=" + enc(XboxAuth.SCOPE))
        }.getOrElse { 0 to "" }
        when (XboxAuth.refreshOutcome(st, body)) {
            XboxAuth.Refresh.OK -> {}
            XboxAuth.Refresh.SIGNED_OUT -> {
                log("refresh $st → přihlášení neplatí")
                // vybraný Xbox si nechat, jen přihlášení zahodit
                prefs(c).edit().remove("rt").apply(); ms = null; stream = null
                throw XboxException(XboxFail.AUTH, "refresh $st")
            }
            XboxAuth.Refresh.RETRY -> { log("refresh $st"); throw XboxException(XboxFail.NETWORK, "refresh $st") }
        }
        val t = XboxAuth.parseTokens(body, now()) ?: throw XboxException(XboxFail.NETWORK, "refresh")
        t.refresh?.let { saveRefresh(c, it) }
        ms = t
        return t.access
    }

    private val xblHeaders = mapOf(
        "x-xbl-contract-version" to "1",
        "Cache-Control" to "no-cache",
        "Origin" to "https://www.xbox.com",
        "Referer" to "https://www.xbox.com/",
    )

    /** Streamovací přístup k vlastní konzoli (drží se v paměti). Volat mimo hlavní vlákno. */
    fun stream(c: Context): XboxAuth.Stream {
        stream?.let { if (XboxAuth.valid(it.expiresAt, now())) return it }
        val (s1, t1) = http("POST", XboxAuth.USER_AUTH_URL, JSONObject()
            .put("Properties", JSONObject().put("AuthMethod", "RPS").put("RpsTicket", "d=" + msToken(c)).put("SiteName", "user.auth.xboxlive.com"))
            .put("RelyingParty", "http://auth.xboxlive.com").put("TokenType", "JWT").toString(), headers = xblHeaders)
        if (s1 != 200) { log("user auth $s1"); throw XboxException(if (s1 == 401) XboxFail.AUTH else XboxFail.NETWORK, "user $s1") }
        val user = XboxAuth.parseUserToken(t1) ?: throw XboxException(XboxFail.NETWORK, "user")

        val (s2, t2) = http("POST", XboxAuth.XSTS_URL, JSONObject()
            .put("Properties", JSONObject().put("SandboxId", "RETAIL").put("UserTokens", JSONArray().put(user)))
            .put("RelyingParty", "http://gssv.xboxlive.com/").put("TokenType", "JWT").toString(), headers = xblHeaders)
        if (s2 != 200) { log("xsts $s2"); throw XboxException(XboxAuth.xstsError(s2, t2), "xsts $s2") }
        val xsts = XboxAuth.parseXsts(t2) ?: throw XboxException(XboxFail.NETWORK, "xsts")
        xsts.gamertag?.let { prefs(c).edit().putString("gamertag", it).apply() }

        val (s3, t3) = http("POST", XboxAuth.XHOME_LOGIN_URL,
            JSONObject().put("token", xsts.token).put("offeringId", "xhome").toString(),
            headers = mapOf("Cache-Control" to "no-store, must-revalidate, no-cache", "x-gssv-client" to "XboxComBrowser"))
        if (s3 != 200) { log("xhome login $s3"); throw XboxException(if (s3 == 401 || s3 == 403) XboxFail.PROFILE else XboxFail.NETWORK, "xhome $s3") }
        val s = XboxAuth.parseStream(t3, now()) ?: throw XboxException(XboxFail.NETWORK, "xhome")
        log("region ${s.baseUri.removePrefix("https://").substringBefore('.')}")
        stream = s
        return s
    }

    /**
     * Streamovací token zahodit a získat nový (služba vzdáleného hraní vrátila 401/403 – token
     * vypršel dřív, než jsme čekali). Volat mimo hlavní vlákno.
     */
    fun renewStream(c: Context): XboxAuth.Stream {
        stream = null
        return stream(c)
    }

    /** Token „transfer“ – Xbox ho chce, když relace hlásí ReadyToConnect. */
    fun transferToken(c: Context): String {
        msToken(c) // obnoví obnovovací token, ať je čerstvý
        val rt = refreshToken(c) ?: throw XboxException(XboxFail.AUTH, "nepřihlášeno")
        val (st, body) = form(XboxAuth.TRANSFER_URL,
            "client_id=${XboxAuth.CLIENT_ID}&grant_type=refresh_token&refresh_token=" + enc(rt) + "&scope=" + enc(XboxAuth.TRANSFER_SCOPE))
        if (st != 200) { log("transfer $st"); throw XboxException(XboxFail.AUTH, "transfer $st") }
        return XboxAuth.parseTokens(body, now())?.access ?: throw XboxException(XboxFail.AUTH, "transfer")
    }

    // ------------------------------------------------------------------ konzole

    /** Hlavičky pro služby vzdáleného hraní. */
    fun gssvHeaders(s: XboxAuth.Stream) = mapOf(
        "Authorization" to "Bearer ${s.gsToken}",
        "X-MS-Device-Info" to deviceInfo,
        "X-Gssv-Client" to "XboxComBrowser",
    )

    private val deviceInfo: String by lazy {
        JSONObject()
            .put("appInfo", JSONObject().put("env", JSONObject()
                .put("clientAppId", "www.xbox.com").put("clientAppType", "browser")
                .put("clientAppVersion", "29.9.35").put("clientSdkVersion", "10.6.8")
                .put("httpEnvironment", "prod").put("sdkInstallId", "")))
            .put("dev", JSONObject()
                .put("hw", JSONObject().put("make", "Microsoft").put("model", "unknown").put("platformType", "desktop").put("sdktype", "web"))
                .put("os", JSONObject().put("name", "android").put("ver", "22631.2715").put("platform", "desktop"))
                .put("displayInfo", JSONObject()
                    .put("dimensions", JSONObject().put("widthInPixels", 1280).put("heightInPixels", 720))
                    .put("pixelDensity", JSONObject().put("dpiX", 1).put("dpiY", 1)))
                .put("browser", JSONObject().put("browserName", "edge").put("browserVersion", "140.0.3485.66")))
            .toString()
    }

    /** Xboxy na účtu. Volat mimo hlavní vlákno. */
    fun consoles(c: Context): List<XboxAuth.XboxConsole> {
        val s = stream(c)
        val (st, body) = http("GET", "${s.baseUri}/v6/servers/home?mr=50", null, headers = gssvHeaders(s))
        if (st == 401 || st == 403) { stream = null; throw XboxException(XboxFail.AUTH, "servers $st") }
        if (st != 200) { log("servers $st"); throw XboxException(XboxFail.NETWORK, "servers $st") }
        return XboxAuth.parseConsoles(body) ?: throw XboxException(XboxFail.NETWORK, "servers")
    }

    // ------------------------------------------------------------------ HTTP

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun form(url: String, body: String) = http("POST", url, body, "application/x-www-form-urlencoded")

    /** Jeden požadavek; vrací (stav, text). Výpadek sítě = (0, ""). */
    fun http(method: String, url: String, body: String?, type: String = "application/json",
             headers: Map<String, String> = emptyMap()): Pair<Int, String> = try {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000; c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/json")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", type)
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = runCatching { (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: "" }.getOrDefault("")
            code to text
        } finally { c.disconnect() }
    } catch (e: Exception) {
        log("síť: ${e.javaClass.simpleName}")
        0 to ""
    }
}
