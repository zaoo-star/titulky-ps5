package cz.recaption.pc

import com.sun.jna.platform.win32.Crypt32Util
import cz.recaption.core.Deepl
import cz.recaption.core.DeeplBackoff
import cz.recaption.core.Lang
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64

/**
 * DeepL na počítači – stejná logika jako na Androidu (`core/Deepl.kt`: požadavek, odpověď, chyby,
 * přestávky po chybách), tady jen síť a uložení klíče.
 *
 * Klíč patří jen hráči: ukládá se zašifrovaný přes Windows (DPAPI – rozšifrovat ho jde jen pod
 * stejným uživatelem na stejném počítači) do `deepl.bin` ve složce nástrojů. Nikam se nevypisuje,
 * ani do záznamu; na obrazovce jen [Deepl.masked]. Na Macu je klíč v Klíčence ([MacKlicenka], položka
 * „ReCaption“ / účet „deepl“) – chrání ji přihlášení uživatele Macu.
 */
object DeeplPc {
    private val soubor = File(Nastroje.slozka, "deepl.bin")
    private const val UCET = "deepl"
    @Volatile private var klicVPameti: String? = null
    @Volatile var backoff = DeeplBackoff()
        private set
    /** Poslední zjištěný zbytek znaků (null = zatím neověřeno). */
    @Volatile var vyuziti: Deepl.Usage? = null
        private set

    /** Mac: do Klíčenky se sahá jen jednou za běh (dotaz spouští systémový nástroj – ne při každém překreslení). */
    @Volatile private var macNacteno = false

    fun klic(): String? = klicVPameti ?: runCatching {
        if (Os.mac) { if (macNacteno) null else MacKlicenka.nacti(UCET).also { macNacteno = true } } else if (!Os.windows) null else String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(soubor.readText().trim())), Charsets.UTF_8)
    }.getOrNull()?.takeIf { it.isNotBlank() }?.also { klicVPameti = it }

    fun ma() = klic() != null

    /** Uloží klíč (prázdný = smazat). Vrací false, když vložený text klíč DeepL není. */
    fun uloz(vlozeno: String): Boolean {
        val k = Deepl.cleanKey(vlozeno)
        if (k.isEmpty()) {
            if (Os.mac) MacKlicenka.smaz(UCET)
            soubor.delete(); klicVPameti = null; vyuziti = null; backoff = DeeplBackoff()
            return true
        }
        if (Deepl.keyKind(k) == null) return false
        if (Os.mac) {
            if (!MacKlicenka.uloz(UCET, k)) return false
        } else if (Os.windows) {
            soubor.parentFile?.mkdirs()
            soubor.writeText(Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(k.toByteArray(Charsets.UTF_8))))
        }
        klicVPameti = k
        vyuziti = null
        backoff = DeeplBackoff()
        return true
    }

    /** Ověření klíče a zbytku znaků (/v2/usage). Volat mimo vlákno okna. null = v pořádku. */
    fun over(): Deepl.Problem? {
        val k = klic() ?: return Deepl.Problem.BAD_KEY
        return try {
            val (status, body) = http("GET", Deepl.usageUrl(k), k, null, 5000, 8000)
            val p = Deepl.problemFor(status)
            val u = if (p == null) Deepl.parseUsage(body) else null
            if (u != null) { vyuziti = u; backoff = DeeplBackoff() }
            if (u == null) p ?: Deepl.Problem.BAD_REPLY else null
        } catch (_: Exception) { Deepl.Problem.NETWORK }
    }

    /**
     * Překlad částí titulku (anglické texty už upravené pro překladač). Čeká se nejvýš asi
     * [Deepl.WAIT_MS]; null = DeepL teď nepomůže (bez klíče, přestávka po chybě, síť) → offline.
     * Jména a pojmy ze slovníku hry ([slovnik]) se DeepL neposílají k překladu (jako na Androidu).
     */
    fun preloz(jazyk: Lang, texty: List<String>, slovnik: cz.recaption.core.Glossary? = null): List<String>? {
        val k = klic() ?: return null
        val cil = Deepl.target(jazyk) ?: return null
        if (texty.isEmpty() || !backoff.allowed(System.currentTimeMillis())) return null
        val b = backoff
        return try {
            val zdroje = texty.map { Deepl.source(it, slovnik?.prepare(it)) }
            val (status, body) = http("POST", Deepl.translateUrl(k), k, Deepl.requestJson(zdroje, cil), 1500, 2500)
            val p = Deepl.problemFor(status)
            val vysledek = if (p == null) Deepl.parseTranslations(body, zdroje.size) else null
            if (vysledek == null) b.onProblem(p ?: Deepl.Problem.BAD_REPLY, System.currentTimeMillis()) else b.onSuccess()
            vysledek
        } catch (_: Exception) {
            // výjimku nevypisovat: nic v ní není potřeba a klíč se nemá nikam dostat ani omylem
            b.onProblem(Deepl.Problem.NETWORK, System.currentTimeMillis())
            null
        }
    }

    private fun http(method: String, url: String, key: String, json: String?, connectMs: Int, readMs: Int): Pair<Int, String> {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = connectMs
            c.readTimeout = readMs
            c.useCaches = false
            c.setRequestProperty("Authorization", Deepl.authHeader(key))
            c.setRequestProperty("User-Agent", "Titulky PS5")
            if (json != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            val status = c.responseCode
            val body = (if (status in 200..299) c.inputStream else c.errorStream)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
            return status to body
        } finally { c.disconnect() }
    }
}
