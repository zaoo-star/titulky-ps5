package cz.recaption.pc

import cz.recaption.core.PhoneLogin
import cz.recaption.core.Ps5
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Spárování počítače s PS5 bez chiaki-ng: totéž, co dělá chiaki-ng v okně „Register Console“
 * (`third_party/chiaki-ng/lib/src/regist.c` a `rpcrypt.c`, cíl PS5_1, spojení v domácí síti).
 *
 * 1. UDP „SRC3“ na port 9295 → PS5 odpoví „RES3“ (jen když je otevřená obrazovka s kódem),
 * 2. TCP na týž port: hlavička + 0x1E0 bajtů výplně se zašifrovaným „Np-AccountId“ (AES-128-CFB,
 *    klíč z tabulky chiaki-ng a 8místného kódu, IV = HMAC-SHA256 z náhodného „ambassador“),
 * 3. odpověď se rozšifruje stejným klíčem: jméno konzole, klíč registrace, klíč relace, MAC.
 *
 * Výplň je jako v chiaki-ng samá „A“, takže z tabulek `ps5_keys_0` / `ps5_keys_1` stačí jediný
 * sloupec (posun 0x41 & 0x1F = 1, resp. 0x41 >> 3 = 8) – ten je níže.
 */
object Registrace {
    private const val PORT = 9295
    /** `ps5_keys_0[i * 0x20 + 1]` z `chiaki_rpcrypt_init_regist`. */
    private val KLIC_0 = bajty(0xd8, 0x60, 0xe1, 0x46, 0xbd, 0xb0, 0xbd, 0x94, 0xb4, 0x60, 0x07, 0x0b, 0xb1, 0x4f, 0xe7, 0x23)
    /** `ps5_keys_1[i * 0x20 + 8]` z `chiaki_rpcrypt_aeropause`. */
    private val KLIC_1 = bajty(0xa8, 0x37, 0x61, 0xb5, 0x27, 0xfc, 0x07, 0x6e, 0x62, 0x43, 0x3a, 0xa7, 0x01, 0x95, 0x8a, 0x85)
    private val HMAC_PS5 = bajty(0x46, 0x46, 0x87, 0xb3, 0x49, 0xca, 0x8c, 0xe8, 0x59, 0xc5, 0x27, 0x0f, 0x5d, 0x7a, 0x69, 0xd6)
    private const val CLIENT_TYPE = "dabfa2ec873de5839bee8d3f4c0239c4282c07c25c6077a2931afcf0adc0d34f"

    private fun bajty(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    /** Co PS5 při spárování vydá (chiaki-ng si to ukládá jako `registered_hosts`). */
    class Konzole(
        val jmeno: String,
        val mac: ByteArray,
        /** 16 bajtů (text v šestnáctkové soustavě doplněný nulami). */
        val klicRegistrace: ByteArray,
        val klicRelace: ByteArray,
        val typKlice: Int,
        val ap: Map<String, String>,
    )

    /** Proč se to nepovedlo – krátký stav do okna. */
    class Chyba(val stav: String) : Exception(stav)

    /** Šifra jako `ChiakiRPCrypt`: [bright] = klíč AES, [ambassador] = základ IV. */
    private class Sifra(val bright: ByteArray, val ambassador: ByteArray) {
        private fun iv(counter: Long): ByteArray {
            val m = Mac.getInstance("HmacSHA256")
            m.init(SecretKeySpec(HMAC_PS5, "HmacSHA256"))
            m.update(ambassador)
            m.update(ByteArray(8) { (counter ushr (56 - 8 * it)).toByte() })
            return m.doFinal().copyOf(16)
        }

        fun sifruj(data: ByteArray, sifrovat: Boolean): ByteArray {
            val c = Cipher.getInstance("AES/CFB/NoPadding")
            c.init(if (sifrovat) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(bright, "AES"), IvParameterSpec(iv(0)))
            return c.doFinal(data)
        }
    }

    /** Tělo požadavku (`chiaki_regist_request_payload_format` pro PS5 bez PSN). */
    private fun telo(ucet: ByteArray, pin: Int, ambassador: ByteArray): Pair<ByteArray, Sifra> {
        val vypln = ByteArray(0x1E0) { 'A'.code.toByte() }
        val bright = ByteArray(16) { KLIC_0[it] }
        bright[0xC] = (bright[0xC].toInt() xor (pin ushr 24)).toByte()
        bright[0xD] = (bright[0xD].toInt() xor (pin ushr 16)).toByte()
        bright[0xE] = (bright[0xE].toInt() xor (pin ushr 8)).toByte()
        bright[0xF] = (bright[0xF].toInt() xor pin).toByte()
        val sifra = Sifra(bright, ambassador)
        // „aeropause“: (ambassador ^ klíč) + (-0x2d) + i
        val aeropause = ByteArray(16) { i -> (((ambassador[i].toInt() xor KLIC_1[i].toInt()) and 0xFF) + 0xD3 + i).toByte() }
        System.arraycopy(aeropause, 8, vypln, 0xC7, 8)
        System.arraycopy(aeropause, 0, vypln, 0x191, 8)
        val vnitrni = "Client-Type: $CLIENT_TYPE\r\nNp-AccountId: ${Base64.getEncoder().encodeToString(ucet)}\r\n"
        return (vypln + sifra.sifruj(vnitrni.toByteArray(Charsets.US_ASCII), true)) to sifra
    }

    /**
     * Spáruje s PS5 na [adresa]. [ucet] = číslo účtu (8 bajtů), [pin] = 8 číslic z PS5.
     * Trvá pár sekund, volat mimo okno. Neúspěch = [Chyba] s krátkým stavem.
     */
    fun sparuj(adresa: String, ucet: ByteArray, pin: Int, log: (String) -> Unit = {}): Konzole {
        val ambassador = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val (telo, sifra) = telo(ucet, pin, ambassador)
        val cil = najdi(adresa, log)
        Thread.sleep(100) // PS5 nepřijme požadavek hned po odpovědi
        val odpoved = try {
            Socket().use { s ->
                s.connect(InetSocketAddress(cil, PORT), 3000)
                s.soTimeout = 6000
                val hlavicka = "POST /sie/ps5/rp/sess/rgst HTTP/1.1\r\n HTTP/1.1\r\n" +
                    "HOST: 10.0.2.15\r\n" +
                    "User-Agent: remoteplay Windows\r\n" +
                    "Connection: close\r\n" +
                    "Content-Length: ${telo.size}\r\n" +
                    "RP-Version: 1.0\r\n\r\n"
                s.getOutputStream().apply { write(hlavicka.toByteArray(Charsets.US_ASCII)); write(telo); flush() }
                precti(s.getInputStream())
            }
        } catch (e: Chyba) { throw e } catch (e: Exception) {
            log("párování: spojení ${e.javaClass.simpleName}")
            throw Chyba("PS5 · neodpovídá")
        }
        val (kod, hlavicky, data) = odpoved
        if (kod != 200) {
            val duvod = hlavicky["rp-application-reason"]?.toLongOrNull(16)
            log("párování: PS5 odpověděla $kod, důvod ${duvod?.let { "0x" + it.toString(16) } ?: "–"}")
            throw Chyba(when (duvod) {
                0x80108b09L -> "Kód · neplatný"
                0x80108b02L -> "Účet · neplatný"
                0x80108b10L -> "Remote Play · používá jiné zařízení"
                0x80108b11L -> "PS5 · nepodporovaná verze"
                else -> "Párování · chyba $kod"
            })
        }
        val text = String(sifra.sifruj(data, false), Charsets.ISO_8859_1)
        val pole = LinkedHashMap<String, String>()
        for (radek in text.split("\r\n")) {
            val i = radek.indexOf(':')
            if (i > 0) pole[radek.substring(0, i).trim()] = radek.substring(i + 1).trim()
        }
        val regist = pole["PS5-RegistKey"]?.let { Ps5.unhex(it) }?.takeIf { it.size <= 16 }
        val relace = pole["RP-Key"]?.let { Ps5.unhex(it) }?.takeIf { it.size == 16 }
        val mac = pole["PS5-Mac"]?.let { Ps5.unhex(it) }?.takeIf { it.size == 6 }
        if (regist == null || relace == null || mac == null) {
            log("párování: neúplná odpověď (${pole.keys.joinToString()})")
            throw Chyba("Párování · neúplná odpověď PS5")
        }
        return Konzole(
            jmeno = pole["PS5-Nickname"]?.takeIf { it.isNotBlank() } ?: "PS5",
            mac = mac,
            klicRegistrace = regist.copyOf(16),
            klicRelace = relace,
            typKlice = pole["RP-KeyType"]?.toIntOrNull() ?: 0,
            ap = listOf("AP-Ssid" to "ap_ssid", "AP-Bssid" to "ap_bssid", "AP-Key" to "ap_key", "AP-Name" to "ap_name")
                .associate { (k, n) -> n to (pole[k] ?: "") },
        )
    }

    /** UDP „SRC3“ → „RES3“: PS5 tím potvrdí, že má otevřenou obrazovku s kódem. */
    private fun najdi(adresa: String, log: (String) -> Unit): InetAddress {
        val cil = InetAddress.getByName(adresa)
        DatagramSocket().use { s ->
            s.soTimeout = 3000
            val zprava = "SRC3\u0000".toByteArray(Charsets.US_ASCII)
            val buf = ByteArray(256)
            repeat(2) {
                s.send(DatagramPacket(zprava, zprava.size, cil, PORT))
                try {
                    while (true) {
                        val p = DatagramPacket(buf, buf.size)
                        s.receive(p)
                        if (p.length >= 4 && String(p.data, 0, 4, Charsets.US_ASCII) == "RES3") return p.address
                    }
                } catch (_: SocketTimeoutException) {
                } catch (e: Exception) {
                    // PS5 hledání odmítla (port zavřený) = obrazovka s kódem není otevřená
                    log("párování: hledání ${e.javaClass.simpleName}")
                    throw Chyba("PS5 · obrazovka s kódem zavřená")
                }
            }
        }
        throw Chyba("PS5 · obrazovka s kódem zavřená")
    }

    /** HTTP odpověď: kód, hlavičky (malými písmeny) a tělo podle Content-Length. */
    private fun precti(vstup: InputStream): Triple<Int, Map<String, String>, ByteArray> {
        val hlava = ByteArrayOutputStream()
        var shoda = 0
        val konec = "\r\n\r\n"
        while (shoda < 4) {
            val b = vstup.read()
            if (b < 0) throw Chyba("PS5 · spojení přerušeno")
            hlava.write(b)
            shoda = if (b == konec[shoda].code) shoda + 1 else if (b == '\r'.code) 1 else 0
            if (hlava.size() > 8192) throw Chyba("PS5 · nečitelná odpověď")
        }
        val radky = String(hlava.toByteArray(), Charsets.ISO_8859_1).split("\r\n")
        val kod = radky.first().split(' ').getOrNull(1)?.toIntOrNull() ?: 0
        val hlavicky = radky.drop(1).filter { ':' in it }
            .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        val delka = hlavicky["content-length"]?.toIntOrNull()?.coerceIn(0, 4096) ?: 0
        val data = ByteArray(delka)
        var mam = 0
        while (mam < delka) {
            val n = vstup.read(data, mam, delka - mam)
            if (n < 0) break
            mam += n
        }
        return Triple(kod, hlavicky, data.copyOf(mam))
    }
}

/**
 * Číslo účtu PlayStation z přihlášení u Sony (jako `PsnAuth` v aplikaci pro Android, bez Androidu).
 * Heslo ani přihlášení se neukládá, jen číslo účtu a jméno.
 */
object PsnPc {
    private const val CLIENT_ID = "ba495a24-818c-472b-b12d-ff231c1b5745"
    private const val CLIENT_SECRET = "mvaiZkRsAsI1IBkY"
    private const val REDIRECT = "https://remoteplay.dl.playstation.net/remoteplay/redirect"
    private const val TOKEN_URL = "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/token"

    const val LOGIN_URL = "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/authorize" +
        "?service_entity=urn:service-entity:psn&response_type=code&client_id=$CLIENT_ID" +
        "&redirect_uri=$REDIRECT&scope=psn:clientapp&request_locale=en_US&ui=pr&service_logo=ps" +
        "&layout_type=popup&smcid=remoteplay&prompt=always&PlatformPrivacyWs1=minimal&"

    /** Jednorázový kód z adresy „redirect“ → účet; null = kód neplatí. Volat mimo okno. */
    fun ucet(kod: String, log: (String) -> Unit = {}): PhoneLogin.Account? {
        val auth = "Basic " + Base64.getEncoder().encodeToString("$CLIENT_ID:$CLIENT_SECRET".toByteArray())
        return try {
            val c = URI(TOKEN_URL).toURL().openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 10000
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Authorization", auth)
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(("grant_type=authorization_code&code=" + URLEncoder.encode(kod, "UTF-8") + "&redirect_uri=$REDIRECT&").toByteArray()) }
            val tokenJson = try {
                if (c.responseCode != 200) { log("přihlášení: Sony odpověděla ${c.responseCode}"); return null }
                c.inputStream.bufferedReader().use { it.readText().take(8000) }
            } finally { c.disconnect() }
            val token = Ps5.accessTokenFrom(tokenJson) ?: return null
            val c2 = URI(TOKEN_URL + "/" + URLEncoder.encode(token, "UTF-8")).toURL().openConnection() as HttpURLConnection
            c2.connectTimeout = 10000; c2.readTimeout = 10000
            c2.setRequestProperty("Authorization", auth)
            val info = try {
                if (c2.responseCode != 200) { log("přihlášení: účet ${c2.responseCode}"); return null }
                c2.inputStream.bufferedReader().use { it.readText().take(8000) }
            } finally { c2.disconnect() }
            Ps5.accountIdFromUserInfo(info)?.let { PhoneLogin.Account(it, PhoneLogin.nameFromUserInfo(info) ?: "") }
        } catch (e: Exception) {
            log("přihlášení: ${e.javaClass.simpleName}")
            null
        }
    }
}
