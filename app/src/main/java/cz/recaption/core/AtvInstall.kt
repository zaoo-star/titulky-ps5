package cz.recaption.core

import java.math.BigInteger
import java.util.Base64

/*
 * Nahrání ReCaptionu z telefonu do Android TV / Google TV / boxu přes ladění po síti (ADB, port 5555) –
 * část bez Androidu: adresa, kroky pomocníka, rozhodnutí o verzi, čtení odpovědí televize a veřejný
 * klíč ADB. Spojení samotné je v cz.recaption.AtvInstaller (knihovna dadb). Zjištění a rozhodnutí:
 * navrhy/instalace-android-tv.md. Testy: AtvInstallTest.
 */

/** Proč nahrání skončilo. Každý kód má text `at_e_<kód malými>` (core/I18nAtv.kt). */
enum class AtvError {
    ADDRESS, PACKAGE, CONNECT, WIRELESS, REFUSED, NO_ANSWER, SIGNATURE, STORAGE, OLD_ANDROID, ABI,
    INSTALL, VERIFY, UNKNOWN_VERSION, TIMEOUT, CANCELLED
}

class AtvException(val code: AtvError, val detail: String? = null) : Exception(code.name + (detail?.let { ": $it" } ?: ""))

/** Co udělat podle verze v televizi. Starší verzi přes novější nikdy nedáváme. */
enum class AtvAction { INSTALL, UPDATE, LAUNCH, LAUNCH_NEWER }

/** Kroky nahrání (text průběhu `at_p_<krok malými>`). */
enum class AtvPhase { PACKAGE, CONNECT, CONFIRM, CHECK, INSTALL, VERIFY, LAUNCH }

object AtvInstall {
    const val ADB_PORT = 5555
    /** Služba „Android TV Remote“ (ovládání z telefonu) – podle ní se pozná Android TV / Google TV i bez ladění. */
    const val REMOTE_PORT = 6466
    /** Google Cast – mají ho skoro všechny Android TV (záloha při hledání). */
    const val CAST_PORT = 8009
    const val PACKAGE = "cz.recaption"
    const val ACTIVITY = "cz.recaption/.MainActivity"
    /** Celé nahrání (spojení, potvrzení v TV, přenos asi 60 MB, instalace) nejvýš 8 minut. */
    const val TIMEOUT_MS = 8 * 60_000L
    /** Jak dlouho čekat, než hráč v TV potvrdí „Povolit ladění?“ (a nejdelší ticho při instalaci). */
    const val ANSWER_MS = 120_000

    /** Cíl: adresa domácí sítě a port (výchozí 5555). */
    data class Target(val ip: String, val port: Int)

    /** „192.168.1.40“ nebo „192.168.1.40:5555“ → cíl; jen číselná adresa domácí sítě. */
    fun address(value: String): Target {
        val v = value.trim().replace(',', '.')
        val ip = v.substringBefore(':')
        val portText = if (':' in v) v.substringAfter(':') else ""
        if (!PhoneLogin.isHomeAddress(ip) || ip.split('.').any { it.length > 1 && it.startsWith("0") }) throw AtvException(AtvError.ADDRESS)
        val last = ip.substringAfterLast('.').toInt()
        if (last == 0 || last == 255) throw AtvException(AtvError.ADDRESS)
        val port = if (':' !in v) ADB_PORT
            else portText.takeIf { it.length in 1..5 && it.all(Char::isDigit) }?.toInt()?.takeIf { it in 1..65535 } ?: throw AtvException(AtvError.ADDRESS)
        return Target(ip, port)
    }

    fun addressOrNull(value: String): Target? = try { address(value) } catch (_: AtvException) { null }

    // ---------------- pomocník krok za krokem ----------------

    enum class Step { FIND_TV, DEBUG, CONFIRM, READY }

    /** Co se o televizi právě ví. */
    data class Probe(
        /** Televize se ozvala (ladění, Android TV Remote nebo Google Cast). */
        val tvFound: Boolean,
        /** Port ladění (5555) je otevřený. */
        val debug: Boolean,
        /** Televize už klíč tohoto telefonu jednou přijala. */
        val allowed: Boolean,
    )

    /** První krok, který ještě není hotový. */
    fun step(p: Probe): Step = when {
        !p.tvFound && !p.debug -> Step.FIND_TV
        !p.debug -> Step.DEBUG
        !p.allowed -> Step.CONFIRM
        else -> Step.READY
    }

    fun done(s: Step, p: Probe): Boolean = step(p).ordinal > s.ordinal

    // ---------------- verze ----------------

    /** ReCaption v televizi: číslo verze (rozhoduje) a název (jen k zobrazení). */
    data class Installed(val code: Long, val name: String?)

    private val PKG_LINE = Regex("^\\s*Package \\[" + Regex.escape(PACKAGE) + "\\]")

    /**
     * Výstup `dumpsys package cz.recaption` → nainstalovaná verze; null = ReCaption v televizi není.
     * Bere se první oddíl „Package [cz.recaption]“; když v něm číslo verze chybí, nic se nepřepisuje.
     */
    fun installed(dumpsys: String): Installed? {
        val lines = dumpsys.lines()
        val start = lines.indexOfFirst { PKG_LINE.containsMatchIn(it) }
        if (start < 0) return null
        val indent = lines[start].length - lines[start].trimStart().length
        var code: Long? = null
        var name: String? = null
        for (l in lines.drop(start + 1)) {
            if (l.isNotBlank() && l.length - l.trimStart().length <= indent) break
            if (code == null) Regex("\\bversionCode=([0-9]{1,12})\\b").find(l)?.let { code = it.groupValues[1].toLong() }
            if (name == null) Regex("\\bversionName=([0-9A-Za-z._\\-]{1,40})").find(l)?.let { name = it.groupValues[1] }
        }
        return Installed(code ?: throw AtvException(AtvError.UNKNOWN_VERSION), name)
    }

    /** installed = verze v TV (null = není); own = verze v telefonu. */
    fun decide(installed: Long?, own: Long): AtvAction = when {
        installed == null -> AtvAction.INSTALL
        installed < own -> AtvAction.UPDATE
        installed == own -> AtvAction.LAUNCH
        else -> AtvAction.LAUNCH_NEWER
    }

    /** Odpověď `pm install` (bez „Success“) → kód chyby pro hráče; detail jen kód Androidu INSTALL_…, nic dalšího. */
    fun installError(reason: String): AtvException {
        val code = Regex("INSTALL_[A-Z_]{3,60}").find(reason)?.value
        val e = when {
            code == null -> AtvError.INSTALL
            code == "INSTALL_FAILED_UPDATE_INCOMPATIBLE" || code == "INSTALL_FAILED_SHARED_USER_INCOMPATIBLE" ||
                code.startsWith("INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES") -> AtvError.SIGNATURE
            code == "INSTALL_FAILED_INSUFFICIENT_STORAGE" -> AtvError.STORAGE
            code == "INSTALL_FAILED_OLDER_SDK" -> AtvError.OLD_ANDROID
            code == "INSTALL_FAILED_NO_MATCHING_ABIS" -> AtvError.ABI
            // novější verze už v TV je (stav mezi kontrolou a instalací) – nic nesnižujeme
            code == "INSTALL_FAILED_VERSION_DOWNGRADE" -> AtvError.VERIFY
            else -> AtvError.INSTALL
        }
        return AtvException(e, code)
    }

    /** Odpověď `am start`: chyba = Error / Exception / neexistuje. */
    fun launched(output: String): Boolean {
        val o = output.lowercase()
        return !("error" in o || "exception" in o || "does not exist" in o || "unable to resolve" in o)
    }

    /** Model televize z `getprop` (výrobce + model), krátce a bez zvláštních znaků; null = nic rozumného. */
    fun model(manufacturer: String, model: String): String? {
        fun clean(s: String) = s.replace(Regex("[^\\p{L}\\p{N} ._\\-+()]"), "").trim().take(40)
        val m = clean(manufacturer); val d = clean(model)
        val text = when {
            d.isEmpty() -> m
            m.isEmpty() || d.lowercase().startsWith(m.lowercase()) -> d
            else -> "$m $d"
        }
        return text.takeIf { it.isNotEmpty() }
    }

    // ---------------- klíč ADB ----------------

    /**
     * Veřejný klíč RSA ve tvaru, který čeká adbd (soubor adbkey.pub): base64 struktury
     * {počet slov, n0inv, n (little-endian), rr = 2^(2·bitů) mod n, exponent} a za mezerou jméno.
     * Jen pro klíče 2048 bitů (jiné adbd nebere).
     */
    fun adbPublicKey(modulus: BigInteger, exponent: BigInteger, name: String = "ReCaption"): String {
        require(modulus.bitLength() == 2048) { "ADB bere jen RSA 2048" }
        val words = 64
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = r32.subtract(modulus.mod(r32).modInverse(r32)).toLong()
        val rr = BigInteger.ONE.shiftLeft(2 * 2048).mod(modulus)
        val out = java.nio.ByteBuffer.allocate(4 + 4 + words * 4 + words * 4 + 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        out.putInt(words)
        out.putInt(n0inv.toInt())
        fun putLe(v: BigInteger) {
            var x = v
            repeat(words) { out.putInt(x.mod(r32).toLong().toInt()); x = x.shiftRight(32) }
        }
        putLe(modulus)
        putLe(rr)
        out.putInt(exponent.toInt())
        return Base64.getEncoder().encodeToString(out.array()) + " " + name
    }
}
