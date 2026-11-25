package cz.recaption.core

/*
 * Instalace ReCaption do televize LG z Androidu přes vývojářský režim (Developer Mode) – část
 * bez Androidu: rozhodnutí o verzi, kontrola odpovědí televize a služby LG, výpočet zbývajícího
 * času vývojářského režimu. Spojení samotné je v cz.recaption.LgInstaller.
 *
 * Postup a kontroly převzaty podle LgInstalacniProtokol.kt, LgDevCas.kt a LgBalicek.kt z projektu
 * PlayCaption (AGPL-3.0), upraveno pro ReCaption.
 */

/** Proč instalace skončila. Každý kód má text `lg_e_<kód malými>` (core/I18nLg.kt). */
enum class LgError { ADDRESS, PACKAGE, KEY_SERVER, PASSPHRASE, NOT_PAIRED, HOST_CHANGED, SSH, TRANSFER, INSTALL, VERIFY, UNKNOWN_VERSION, TIMEOUT, CANCELLED }

class LgException(val code: LgError, val detail: String? = null) : Exception(code.name + (detail?.let { ": $it" } ?: ""))

/** Co udělat podle verze, která je v televizi. Starší verzi přes novější nikdy nedáváme. */
enum class LgAction { INSTALL, UPDATE, LAUNCH, LAUNCH_NEWER }

/** Kroky instalace (pro text průběhu `lg_p_<krok malými>`). */
enum class LgPhase { PACKAGE, KEY, CONNECT, CHECK, UPLOAD, INSTALL, VERIFY, LAUNCH }

/** Přibalený balíček z assets/lg (ověřený proti popisu). */
class LgBundle(val version: String, val sha256: String, val data: ByteArray)

object LgInstall {
    const val APP_ID = LgIpk.APP_ID
    /** Kde ve vývojářském režimu leží nainstalované aplikace (ověřeno podle PlayCaption a ares-install). */
    const val APPINFO_PATH = "/media/developer/apps/usr/palm/applications/$APP_ID/appinfo.json"
    const val TEMP_DIR = "/media/developer/temp"
    /** Soubor, ve kterém televize drží klíč relace vývojářského režimu. */
    const val SESSION_PATH = "/var/luna/preferences/devmode_enabled"
    const val KEY_PORT = 9991
    const val SSH_PORT = 9922
    const val SSH_USER = "prisoner"
    const val CHECK_URL = "https://developer.lge.com/secure/CheckDevModeSession.dev?sessionToken="
    /**
     * Prodloužení vývojářského režimu (totéž co Extend v aplikaci Developer Mode v TV) – stejný klíč relace
     * jako [CHECK_URL]. Adresa a tvar odpovědi podle webosbrew Dev Manager a gabe565/webos-dev-mode.
     */
    const val RESET_URL = "https://developer.lge.com/secure/ResetDevModeSession.dev?sessionToken="
    const val TIMEOUT_MS = 120_000L

    private val VERSION = Regex("[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{1,8}")

    /** Jen číselná adresa v domácí síti (žádné jméno, žádné znaky navíc). */
    fun address(value: String): String {
        val ip = value.trim()
        val parts = ip.split('.')
        if (parts.size != 4 || parts.any { !it.matches(Regex("0|[1-9][0-9]{0,2}")) || it.toInt() > 255 }) throw LgException(LgError.ADDRESS)
        val b = parts.map { it.toInt() }
        val private = b[0] == 10 || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) || (b[0] == 169 && b[1] == 254)
        if (!private || b[3] == 0 || b[3] == 255) throw LgException(LgError.ADDRESS)
        return ip
    }

    /**
     * Passphrase z aplikace Developer Mode: 6 znaků (písmena a číslice). Prázdné = už spárováno.
     * Televize ji ukazuje velkými písmeny; když ji někdo opíše malými, zkusí se obě podoby.
     */
    fun passphrase(value: String): List<String> {
        val p = value.trim()
        if (p.isEmpty()) return emptyList()
        if (!p.matches(Regex("[A-Za-z0-9]{6}"))) throw LgException(LgError.PASSPHRASE)
        return listOf(p, p.uppercase()).distinct()
    }

    fun compare(a: String, b: String): Int {
        if (!a.matches(VERSION) || !b.matches(VERSION)) throw LgException(LgError.UNKNOWN_VERSION)
        val x = a.split('.').map { it.toLong() }; val y = b.split('.').map { it.toLong() }
        for (i in 0..2) if (x[i] != y[i]) return x[i].compareTo(y[i])
        return 0
    }

    /** installed = verze v televizi (null = ReCaption tam není). Neznámý tvar verze = nic nepřepisovat. */
    fun decide(installed: String?, bundled: String): LgAction {
        if (installed == null) { compare(bundled, bundled); return LgAction.INSTALL }
        val d = compare(installed, bundled)
        return when {
            d < 0 -> LgAction.UPDATE
            d == 0 -> LgAction.LAUNCH
            else -> LgAction.LAUNCH_NEWER
        }
    }

    /** Verze z appinfo.json nainstalované aplikace; cizí id nebo nečitelný soubor = chyba. */
    fun installedVersion(appinfo: String): String {
        val m = MiniJson.obj(appinfo) ?: throw LgException(LgError.UNKNOWN_VERSION)
        if (m["id"] != APP_ID) throw LgException(LgError.VERIFY)
        val v = m["version"] as? String ?: throw LgException(LgError.UNKNOWN_VERSION)
        if (!v.matches(VERSION)) throw LgException(LgError.UNKNOWN_VERSION)
        return v
    }

    /** Ověří přibalený balíček proti jeho popisu (id, verze, velikost, otisk). */
    fun bundle(manifest: String, data: ByteArray): LgBundle {
        val m = MiniJson.obj(manifest) ?: throw LgException(LgError.PACKAGE)
        val v = m["version"] as? String ?: throw LgException(LgError.PACKAGE)
        val ok = (m["schema"] as? Long) == 1L && m["platform"] == "webos" && m["id"] == APP_ID && v.matches(VERSION) &&
            m["file"] == LgIpk.fileName(APP_ID, v) && (m["size"] as? Long) == data.size.toLong() &&
            data.size > 8 && String(data, 0, 8, Charsets.US_ASCII) == "!<arch>\n"
        if (!ok) throw LgException(LgError.PACKAGE)
        val hash = LgIpk.sha256(data)
        if (m["sha256"] != hash) throw LgException(LgError.PACKAGE)
        return LgBundle(v, hash, data)
    }

    /** Odpověď Key Serveru (HTTP/1.0 bez šifrování, jen v domácí síti) → zašifrovaný klíč. */
    fun keyFromHttp(data: ByteArray): ByteArray {
        val text = String(data, Charsets.ISO_8859_1)
        val split = text.indexOf("\r\n\r\n")
        if (split < 0 || split > 8192 || !text.lineSequence().first().matches(Regex("HTTP/1\\.[01] 200(?: .*)?"))) throw LgException(LgError.KEY_SERVER)
        val head = text.substring(0, split)
        if ("transfer-encoding:" in head.lowercase()) throw LgException(LgError.KEY_SERVER)
        val body = data.copyOfRange(split + 4, data.size)
        val len = Regex("(?im)^content-length: *([0-9]+)\\r?$").find(head)?.groupValues?.get(1)?.toLongOrNull()
        if (len != null && len != body.size.toLong()) throw LgException(LgError.KEY_SERVER)
        val s = String(body, Charsets.US_ASCII)
        if (body.size > 16384 || !s.startsWith("-----BEGIN RSA PRIVATE KEY-----") || !s.contains("-----END RSA PRIVATE KEY-----")) throw LgException(LgError.KEY_SERVER)
        return body
    }

    /** Klíč relace vývojářského režimu ze souboru v televizi; null = nečitelný. */
    fun sessionToken(text: String): String? = text.trim().takeIf { it.matches(Regex("[A-Za-z0-9]{8,256}")) }

    /**
     * Jedna řádka z `luna-send-pub -i …/dev/install`. null = instalace ještě běží, true = hotovo,
     * chyba = výjimka s kódem a důvodem od LG (bez ničeho dalšího z odpovědi).
     */
    fun installEvent(line: String): Boolean? {
        val json = MiniJson.obj(line) ?: return null
        @Suppress("UNCHECKED_CAST") val details = json["details"] as? Map<String, Any?>
        fun nonZero(v: Any?) = v != null && v.toString() != "0" && v.toString() != "0.0"
        if (json["returnValue"] == false || nonZero(json["errorCode"]) || nonZero(details?.get("errorCode"))) throw installError(json)
        val state = (details?.get("state") as? String).orEmpty().uppercase()
        if ("FAIL" in state || "ERROR" in state || state == "CANCELLED" || state == "CANCELED") throw installError(json)
        return if (state == "INSTALLED" || state == "SUCCESS" || state.startsWith("SUCCESS_")) true else null
    }

    fun installError(json: Map<String, Any?>): LgException {
        @Suppress("UNCHECKED_CAST") val d = json["details"] as? Map<String, Any?>
        fun clean(v: Any?, max: Int) = (v as? String)?.replace(Regex("[\\p{C}\\s]+"), " ")?.trim()?.take(max)?.takeIf { it.isNotEmpty() }
        val code = sequenceOf(d?.get("errorCode"), json["errorCode"]).mapNotNull { v ->
            v?.let { (if (it is Number) it.toLong().toString() else it.toString()) }?.takeIf { it.matches(Regex("-?[0-9]{1,10}")) && it != "0" }
        }.firstOrNull()
        val reason = sequenceOf(d?.get("reason"), d?.get("errorText"), json["errorText"], json["reason"]).mapNotNull { clean(it, 200) }.firstOrNull()
        val detail = listOfNotNull(code?.let { "errorCode=$it" }, reason).joinToString(" · ").takeIf { it.isNotEmpty() }
        return LgException(LgError.INSTALL, detail)
    }

    /** Otisk klíče televize: null = první spojení (zapamatuje se), jiný než uložený = odmítnout. */
    fun hostKeyOk(saved: String?, seen: String): Boolean = saved == null || saved == seen
}

/** Kolik zbývá vývojářskému režimu: [seconds] podle služby LG v čase [checkedAt] (ms). */
data class LgDevTime(val seconds: Long, val checkedAt: Long) {
    fun remaining(now: Long): Long = (seconds - (now - checkedAt).coerceAtLeast(0) / 1000).coerceAtLeast(0)
    /** Celé dny (zaokrouhleno dolů); 0 = méně než den. */
    fun days(now: Long): Long = remaining(now) / 86400
    fun hours(now: Long): Long = remaining(now) / 3600
    /** Údaj starší než den už neplatí (mezitím mohl uživatel režim prodloužit). */
    fun fresh(now: Long) = now >= checkedAt && now - checkedAt <= 24 * 3600_000L
    /** Pod 3 dny upozornit, ať si uživatel režim v televizi prodlouží. */
    fun warn(now: Long) = remaining(now) < WARN_SECONDS

    companion object {
        const val WARN_SECONDS = 3 * 86400L
        const val REFRESH_MS = 3600_000L

        /** Odpověď CheckDevModeSession: {"result":"success","errorMsg":"HHH:MM:SS"}. Jiný tvar = chyba (ne nula). */
        fun parse(text: String, now: Long): LgDevTime {
            val json = MiniJson.obj(text) ?: throw IllegalArgumentException("LG: není JSON")
            require(json["result"] == "success") { "LG: relace neplatí" }
            val m = Regex("([0-9]{1,5}):([0-5][0-9]):([0-5][0-9])").matchEntire((json["errorMsg"] as? String).orEmpty().trim())
                ?: throw IllegalArgumentException("LG: neznámý tvar času")
            val h = m.groupValues[1].toLong()
            require(h <= 87600 && now > 0)
            return LgDevTime(h * 3600 + m.groupValues[2].toLong() * 60 + m.groupValues[3].toLong(), now)
        }

        /** Zeptat se služby LG znovu? Nejvýš jednou za hodinu (ručně kdykoli). */
        fun shouldRefresh(lastTry: Long, now: Long, manual: Boolean): Boolean =
            manual || lastTry <= 0 || now < lastTry || now - lastTry >= REFRESH_MS

        /** Plná doba režimu po zapnutí nebo prodloužení (služba LG hlásí 999:59:59 = 1000 h). */
        const val FULL_SECONDS = 1000 * 3600L
        /** Prodlužovat nejvýš jednou za den (volba „Prodlužovat vývojářský režim“). */
        const val RENEW_MS = 24 * 3600_000L
        /** Po nepovedeném prodloužení zkusit znovu nejdřív za 3 h (ne až zítra, ne pořád dokola). */
        const val RENEW_RETRY_MS = 3 * 3600_000L

        /**
         * Prodloužit teď? Nejvýš jednou za [RENEW_MS] od posledního pokusu ([lastTry], posunuté hodiny = ano)
         * a ne, když čerstvý údaj [known] říká, že režim byl za poslední den prodloužen jinak (v TV, novým nahráním).
         */
        fun shouldRenew(lastTry: Long, now: Long, known: LgDevTime?): Boolean {
            if (lastTry > 0 && now >= lastTry && now - lastTry < RENEW_MS) return false
            if (known != null && known.fresh(now) && known.remaining(now) > FULL_SECONDS - RENEW_MS / 1000) return false
            return true
        }

        /** Čas pokusu, který se zapíše po nepovedeném prodloužení – další pokus pak za [RENEW_RETRY_MS]. */
        fun failedTry(now: Long): Long = now - RENEW_MS + RENEW_RETRY_MS

        /**
         * Odpověď ResetDevModeSession: {"result":"success","errorCode":"200","errorMsg":…}. Jiný výsledek nebo kód
         * = výjimka (režim se neprodloužil – vypršel, nebo klíč relace už neplatí).
         */
        fun renewed(text: String) {
            val json = MiniJson.obj(text) ?: throw IllegalArgumentException("LG: není JSON")
            require(json["result"] == "success") { "LG: prodloužení odmítnuto" }
            val code = json["errorCode"]
            require(code == null || code == "200" || code == 200L) { "LG: kód odpovědi" }
        }
    }
}
