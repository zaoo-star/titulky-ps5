package cz.recaption.core

import java.util.Base64

/** Uložené údaje o spárované konzoli. */
data class Ps5Host(
    val ip: String,
    val name: String,
    /** Klíč registrace (16 bajtů, text doplněný nulami). */
    val registKey: ByteArray,
    /** Tajný klíč relace (16 bajtů). */
    val morning: ByteArray,
    val mac: ByteArray,
) {
    fun serialize(): String =
        listOf(ip, Base64.getEncoder().encodeToString(name.toByteArray(Charsets.UTF_8)),
            Ps5.hex(registKey), Ps5.hex(morning), Ps5.hex(mac)).joinToString("|")

    fun withIp(newIp: String) = copy(ip = newIp)

    override fun equals(other: Any?): Boolean = other is Ps5Host && other.serialize() == serialize()
    override fun hashCode(): Int = serialize().hashCode()

    companion object {
        fun parse(s: String?): Ps5Host? {
            if (s.isNullOrEmpty()) return null
            val p = s.split('|')
            if (p.size != 5) return null
            return try {
                val key = Ps5.unhex(p[2]) ?: return null
                val morning = Ps5.unhex(p[3]) ?: return null
                val mac = Ps5.unhex(p[4]) ?: return null
                if (!Ps5.validHost(p[0]) || key.size != 16 || morning.size != 16) return null
                Ps5Host(p[0], String(Base64.getDecoder().decode(p[1]), Charsets.UTF_8), key, morning, mac)
            } catch (_: Exception) { null }
        }
    }
}

/** Pomocné výpočty kolem připojení k PlayStationu (bez Androidu – dá se testovat). */
object Ps5 {
    const val ACCOUNT_ID_SIZE = 8

    /** Stav spojení s konzolí, jak ho vidí obrazovka aplikace. */
    enum class Link { OFF, CONNECTING, CONNECTED, WAITING, FAILED }

    fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    fun unhex(s: String): ByteArray? {
        if (s.length % 2 != 0) return null
        return try { ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }
        catch (_: NumberFormatException) { null }
    }

    /**
     * ID účtu PSN: buď zakódované (base64, 8 bajtů – tak ho ukazují nástroje pro Remote Play),
     * nebo dlouhé číslo (to se převede na 8 bajtů, nejnižší bajt první).
     */
    fun parseAccountId(input: String): ByteArray? {
        val s = input.trim()
        if (s.isEmpty()) return null
        if (s.all { it.isDigit() } && s.length in 10..20) {
            val n = s.toULongOrNull() ?: return null
            return ByteArray(8) { ((n shr (8 * it)) and 0xFFu).toByte() }
        }
        return try {
            val b = Base64.getDecoder().decode(s.replace('-', '+').replace('_', '/'))
            if (b.size == ACCOUNT_ID_SIZE) b else null
        } catch (_: IllegalArgumentException) { null }
    }

    fun accountIdToBase64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)

    /** PIN z konzole: 8 číslic (mezery mezi čtveřicemi nevadí). */
    fun parsePin(input: String): Int? {
        val s = input.filter { !it.isWhitespace() && it != '-' }
        if (s.length != 8 || !s.all { it.isDigit() }) return null
        return s.toInt()
    }

    /** Adresa konzole v domácí síti (IPv4) nebo jméno počítače. */
    fun validHost(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty() || t.length > 253) return false
        val parts = t.split('.')
        if (parts.size == 4 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) }) {
            return parts.all { it.length <= 3 && it.toInt() in 0..255 }
        }
        return t.all { it.isLetterOrDigit() || it == '.' || it == '-' } && t.any { it.isLetter() }
    }

    /** Číslo pro probuzení konzole z režimu odpočinku (z klíče registrace). */
    fun wakeCredential(registKey: ByteArray): ULong? {
        val end = registKey.indexOfFirst { it == 0.toByte() }.let { if (it >= 0) it else registKey.size }
        val text = String(registKey, 0, end, Charsets.UTF_8)
        return text.toULongOrNull(16)
    }

    /** Odpověď konzole na hledání v síti. */
    data class Reply(val ready: Boolean, val standby: Boolean, val name: String, val id: String, val type: String)

    /** Zpráva, kterou se konzole v síti hledá (PS5 a PS4 používají jiné číslo verze a jiný port). */
    fun searchPacket(ps5: Boolean): ByteArray =
        ("SRCH * HTTP/1.1\ndevice-discovery-protocol-version:" + (if (ps5) "00030010" else "00020020") + "\n").toByteArray()
    const val DISCOVERY_PORT_PS5 = 9302
    const val DISCOVERY_PORT_PS4 = 987

    /** Rozebere odpověď konzole; null = není to odpověď PlayStationu. */
    fun parseDiscoveryReply(text: String): Reply? {
        val lines = text.split('\n').map { it.trim() }
        val first = lines.firstOrNull() ?: return null
        if (!first.startsWith("HTTP/1.1 ")) return null
        val code = first.removePrefix("HTTP/1.1 ").substringBefore(' ').toIntOrNull() ?: return null
        if (code != 200 && code != 620) return null
        val h = HashMap<String, String>()
        for (l in lines.drop(1)) { val i = l.indexOf(':'); if (i > 0) h[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim() }
        if (!h.containsKey("host-type") && !h.containsKey("host-id")) return null
        return Reply(code == 200, code == 620, h["host-name"] ?: "PlayStation", h["host-id"] ?: "", h["host-type"] ?: "")
    }

    /** Adresy, na které se má hledání poslat: všeobecné vysílání, vysílání sítě a každá adresa v síti. */
    fun sweepTargets(localIp: String, prefixLength: Int): List<String> {
        val p = localIp.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4 || p.any { it !in 0..255 }) return listOf("255.255.255.255")
        val out = LinkedHashSet<String>()
        out.add("255.255.255.255")
        val ip = (p[0].toLong() shl 24) or (p[1].toLong() shl 16) or (p[2].toLong() shl 8) or p[3].toLong()
        val bits = prefixLength.coerceIn(8, 30)
        val mask = (0xFFFFFFFFL shl (32 - bits)) and 0xFFFFFFFFL
        fun str(a: Long) = "${(a shr 24) and 255}.${(a shr 16) and 255}.${(a shr 8) and 255}.${a and 255}"
        out.add(str((ip and mask) or (mask.inv() and 0xFFFFFFFFL)))
        // jednotlivé adresy: celá síť, nejvýš ale 254 nejbližších (velké sítě se neprocházejí celé)
        val base = if (bits >= 24) ip and mask else ip and 0xFFFFFF00L
        val count = if (bits >= 24) (1L shl (32 - bits)) else 256L
        for (i in 1 until count - 1) { val a = base + i; if (a != ip) out.add(str(a)) }
        return out.toList()
    }

    private fun validCode(v: String) = v.length in 4..80 && v.all { it.isLetterOrDigit() || it in "-_.~" } && v.all { it.code < 128 }

    /** Z adresy, na kterou Sony po přihlášení přesměruje, vytáhne jednorázový kód. */
    fun authCodeFromUrl(url: String): String? {
        val q = url.substringAfter('?', "").substringBefore('#')
        for (part in q.split('&')) {
            val k = part.substringBefore('=')
            val v = part.substringAfter('=', "")
            if (k == "code" && validCode(v)) return v
        }
        return null
    }

    /**
     * Kód, který uživatel opsal z adresy v telefonu: přijme celou adresu, kus „code=…&…“ i samotný kód.
     */
    fun authCodeFromTyped(input: String): String? {
        val s = input.trim().replace(" ", "")
        if (s.isEmpty()) return null
        authCodeFromUrl(if (s.contains('?')) s else "x?$s")?.let { return it }
        val bare = s.substringAfter("code=", s).substringBefore('&')
        return if (validCode(bare)) bare else null
    }

    /** Z odpovědi Sony o účtu vytáhne číslo účtu a převede ho na zakódované ID (base64). */
    fun accountIdFromUserInfo(json: String): String? {
        val m = Regex("\"user_id\"\\s*:\\s*\"?(\\d{6,20})\"?").find(json) ?: return null
        val b = parseAccountId(m.groupValues[1].padStart(10, '0')) ?: return null
        return accountIdToBase64(b)
    }

    /** Z odpovědi Sony vytáhne přístupový klíč. */
    fun accessTokenFrom(json: String): String? =
        Regex("\"access_token\"\\s*:\\s*\"([A-Za-z0-9._~+/=-]{8,400})\"").find(json)?.groupValues?.get(1)

    /** Za jak dlouho zkusit spojení znovu (ms) – po každém neúspěchu déle, nejvýš 30 s. */
    fun retryDelayMs(failures: Int): Long = when {
        failures <= 0 -> 1500
        failures == 1 -> 3000
        failures == 2 -> 6000
        failures <= 5 -> 12000
        else -> 30000
    }

    /** Připojení tohoto zařízení k domácí síti. */
    enum class Net { CABLE, WIFI_5, WIFI_24, UNKNOWN }

    /** Na Wi-Fi 2,4 GHz se bere vždy menší obraz: stejné pásmo používají ovladače konzole (Bluetooth). */
    fun allowHighRes(net: Net): Boolean = net != Net.WIFI_24

    /** Po spojení, které se rozpadlo, se s dalším pokusem čeká aspoň takhle dlouho. */
    const val AFTER_DROP_MS = 15_000L

    /** Kód důvodu ukončení z chiaki-ng (pořadí ChiakiQuitReason): Remote Play na konzoli už někdo používá. */
    const val QUIT_RP_IN_USE = 4
    /** Konzole spojení ukončila (odpojila nás) – mimo jiné, když se stejným účtem připojí jiné zařízení. */
    const val QUIT_REMOTE_DISCONNECTED = 11

    /**
     * Ukončil spojení PlayStation proto, že Remote Play převzalo jiné zařízení (např. aplikace PS Remote Play
     * přihlášená stejným druhým účtem)? Jisté je to u „already in use“; u odpojení ze strany konzole jen,
     * když to konzole napíše v důvodu.
     */
    fun otherDevice(reason: Int, reasonString: String?): Boolean {
        if (reason == QUIT_RP_IN_USE) return true
        val r = reasonString?.lowercase() ?: return false
        return reason == QUIT_REMOTE_DISCONNECTED && ("in use" in r || "another" in r || "other device" in r)
    }

    /**
     * Proč PS5 odmítla spojení hned na začátku (před obrazem). Knihovna to hlásí jen jako „Unknown Session
     * Request Error“; skutečný důvod je v jejím záznamu (`ps5-spojeni.txt`), řádek „Reported Application
     * Reason: 0x… (…)“ nebo chyba připojení. Od 0.97.31 – Domů pak místo anglické hlášky řekne, co udělat.
     */
    enum class Refusal { ACCOUNT, IN_USE, CRASH, VERSION, UNREACHABLE, UNKNOWN }

    /** Z konce záznamu knihovny (posledních pár řádků) → důvod odmítnutí; null = v záznamu není. */
    fun refusal(logTail: String): Refusal? {
        val lines = logTail.lines().takeLast(60)
        // poslední pokus: od posledního „Session request“/připojování dál
        val start = lines.indexOfLast { "Session request" in it || "Connecting to" in it || "Starting session" in it }
        val tail = (if (start >= 0) lines.drop(start) else lines).joinToString("\n").lowercase()
        return when {
            "invalid psn id" in tail || "regist failed" in tail -> Refusal.ACCOUNT
            "already in use" in tail -> Refusal.IN_USE
            "crash" in tail -> Refusal.CRASH
            "rp-version" in tail || "rp version" in tail -> Refusal.VERSION
            "connect failed" in tail || "connection refused" in tail -> Refusal.UNREACHABLE
            "reported application reason" in tail || "failed to receive session request response" in tail ||
                "failed to parse session request response" in tail || "nonce invalid" in tail -> Refusal.UNKNOWN
            else -> null
        }
    }

    /** Výsledek hlídání obrazu za spojení ([watchdog]). */
    enum class Watchdog { OK, DECODER, NETWORK }

    /** Od kdy se výpadek dat z konzole zapíše do záznamu. */
    const val NET_STALL_LOG_MS = 8_000L
    /** Data z konzole chodí, ale nic se nedekóduje: po této době se spojení naváže znovu (dekodér se zasekl). */
    const val DECODER_STALL_MS = 12_000L
    /**
     * Z konzole nepřichází nic (Wi-Fi zakolísala, telefon na 2,4 GHz…): spojení se nechá být, většinou se
     * samo vzpamatuje za pár sekund. Každé nové spojení PS5 ohlásí („ovladač odpojen / účet připojen“)
     * a hráči to vadí víc než chvilka bez titulků. Do 0.96 se rušilo už po 12 s.
     */
    const val NETWORK_STALL_MS = 45_000L

    /**
     * Má se spojení navázat znovu? [connectedMs] jak dlouho je spojeno, [noFramesMs] bez dekódovaného
     * snímku, [noInputMs] bez jakýchkoli dat z konzole.
     */
    fun watchdog(connectedMs: Long, noFramesMs: Long, noInputMs: Long): Watchdog = when {
        connectedMs <= DECODER_STALL_MS -> Watchdog.OK
        noInputMs >= NETWORK_STALL_MS -> Watchdog.NETWORK
        noFramesMs >= DECODER_STALL_MS && noInputMs < NET_STALL_LOG_MS -> Watchdog.DECODER
        else -> Watchdog.OK
    }

    /** Spojení, které vydrží aspoň takhle dlouho, se bere jako v pořádku. */
    const val STABLE_SESSION_MS = 60_000L

    enum class Unstable { RETRY, LOWER_QUALITY, GIVE_UP }

    /**
     * Co dělat, když se spojení [shortSessions]× po sobě navázalo a hned spadlo:
     * po druhém takovém pokusu s obrazem 1080p přejít na 720p (méně dat, lehčí pro televizi i Wi-Fi),
     * po čtvrtém přestat – opakované připojování ruší hráče u konzole.
     */
    fun afterShortSessions(shortSessions: Int, highRes: Boolean): Unstable = when {
        shortSessions >= 4 -> Unstable.GIVE_UP
        shortSessions == 2 && highRes -> Unstable.LOWER_QUALITY
        else -> Unstable.RETRY
    }
}
