package cz.recaption.core

/**
 * Pomocník „krok za krokem“ pro první nahrání ReCaptionu do televize LG (bez Androidu, s testy
 * `LgSetupTest`). Telefon / box většinu kroků pozná sám, takže se odškrtávají bez ptaní:
 *  1. televize LG je zapnutá a ve stejné síti – najde se sama (SSDP, záloha: projít síť),
 *  2. aplikace Developer Mode je v televizi a vývojářský režim zapnutý – otevřený port SSH 9922,
 *  3. Key Server je zapnutý – otevřený port 9991 (u už spárované televize není potřeba),
 *  4. Passphrase je zadaná (nebo televize už spárovaná),
 *  5. nahrát.
 * Hledání a zkoušení portů dělá `LgFinder` v Android části.
 */
object LgSetup {
    const val SSH_PORT = 9922
    const val KEY_PORT = 9991
    /** Port, na kterém webOS poslouchá i bez vývojářského režimu (ovládání z telefonu) – podle něj se pozná zapnutá televize. */
    const val WEBOS_PORT = 3000
    /** Novější webOS nechává otevřený jen zabezpečený port ovládání. */
    const val WEBOS_SSL_PORT = 3001
    const val SSDP_ST = "urn:lge-com:service:webos-second-screen:1"

    fun searchMessage(): String =
        "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $SSDP_ST\r\n\r\n"

    /** Odpověď SSDP od televize LG → její adresa (odesílatel [from]), jen v domácí síti; jinak null. */
    fun parseResponse(text: String, from: String): String? {
        if (text.length > 4096 || !text.startsWith("HTTP/1.1 200")) return null
        val lower = text.lowercase()
        if (!lower.contains("lge-com") && !lower.contains("webos")) return null
        return runCatching { LgInstall.address(from) }.getOrNull()
    }

    enum class Step { FIND_TV, DEV_MODE, KEY_SERVER, PASSPHRASE, READY }

    /** Co se o televizi právě ví. */
    data class Probe(
        /** Televize se ozvala (SSDP nebo port webOS / SSH). */
        val tvFound: Boolean,
        /** Port SSH 9922 je otevřený = vývojářský režim běží. */
        val devMode: Boolean,
        /** Port 9991 je otevřený = Key Server je zapnutý. */
        val keyServer: Boolean,
        /** Tohle zařízení už má od televize klíč (Passphrase není potřeba). */
        val paired: Boolean,
        /** Je zadaná Passphrase ve správném tvaru. */
        val passphrase: Boolean,
    )

    /** První krok, který ještě není hotový. */
    fun step(p: Probe): Step = when {
        !p.tvFound && !p.devMode -> Step.FIND_TV
        !p.devMode -> Step.DEV_MODE
        p.paired -> Step.READY
        !p.keyServer -> Step.KEY_SERVER
        !p.passphrase -> Step.PASSPHRASE
        else -> Step.READY
    }

    /** Je krok [s] hotový? (Pro odškrtnutí v seznamu.) */
    fun done(s: Step, p: Probe): Boolean = step(p).ordinal > s.ordinal

    /** Passphrase od Key Serveru: 6 znaků 0–9 a A–F (bez ohledu na velikost písmen). */
    fun passphraseLooksOk(s: String): Boolean = s.trim().matches(Regex("[0-9A-Fa-f]{6}"))
}
