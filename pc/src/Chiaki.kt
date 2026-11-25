package cz.recaption.pc

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import cz.recaption.core.Ps5
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URI
import java.util.zip.ZipInputStream

/**
 * chiaki-ng – program pro Remote Play, ve kterém se na počítači hraje. ReCaption ho obsluhuje
 * samo: stáhne přenosnou verzi z GitHubu (do složky nástrojů, nic se neinstaluje), najde PS5
 * v síti, probudí ji a spustí hru rovnou přes celou obrazovku (`chiaki --exit-app-on-stream-exit
 * --fullscreen stream <konzole> <adresa>`). Hráč tak vidí jen hru s pruhem ReCaptionu.
 *
 * Spárování s PS5 dělá ReCaption samo ([Registrace], průvodce v `Parovani.kt`) a výsledek zapíše
 * tam, kam si ho ukládá chiaki-ng: do registru Windows (`HKCU\Software\Chiaki\Chiaki\registered_hosts`,
 * [uloz]). Odtud ho chiaki-ng při `chiaki stream` vezme a ReCaption odtud bere jméno konzole a číslo
 * pro probuzení. Klíče z registru se nikam nevypisují.
 *
 * **Čtečka pro TV** (Použití = čtečka, `CteckaTv.kt`) má v chiaki-ng vlastní profil [PROFIL_CTECKY]
 * (`--profile`, registr `HKCU\Software\Chiaki\Chiaki-recaption`): konzole spárovaná účtem pro ReCaption
 * tak nepřepíše spárování herním účtem pro hraní na PC a ztlumený zvuk ([pripravProfil]) neplatí pro
 * hráčovo vlastní nastavení chiaki-ng.
 *
 * **Mac**: chiaki-ng je aplikace `chiaki-ng.app` (stahuje se jako `.dmg`) a nastavení má v předvolbách
 * macOS místo registru – funkce níž na Macu odbočí do [ChiakiMac].
 */
object Chiaki {
    val slozka = File(Nastroje.slozka, "chiaki-ng")
    private const val REPO = "streetpea/chiaki-ng"
    private const val REG = "Software\\Chiaki\\Chiaki"
    /** Profil chiaki-ng pro čtečku pro TV (vlastní spárované konzole a nastavení). */
    const val PROFIL_CTECKY = "recaption"

    /** Klíč nastavení profilu v registru (Qt: aplikace „Chiaki-<profil>“); null = výchozí profil chiaki-ng. */
    private fun reg(profil: String?) = if (profil.isNullOrEmpty()) REG else "$REG-$profil"

    /** chiaki.exe – stažený do složky nástrojů, nebo nainstalovaný. */
    fun exe(): File? {
        if (Os.mac) return ChiakiMac.exe()
        val mista = listOf(
            slozka,
            File(System.getenv("ProgramFiles") ?: "C:\\Program Files", "chiaki-ng"),
            File(System.getenv("LOCALAPPDATA") ?: "", "Programs\\chiaki-ng"),
        )
        for (m in mista) {
            if (!m.isDirectory) continue
            m.walkTopDown().maxDepth(3).firstOrNull {
                it.isFile && it.name.equals("chiaki.exe", ignoreCase = true)
            }?.let { return it }
        }
        return null
    }

    // ---- stažení ----

    /** Přenosná verze pro tento počítač (x64 / ARM) z posledního vydání. Vrací chybu, nebo null = hotovo. */
    fun stahni(postup: (Int) -> Unit, log: (String) -> Unit): String? = try {
        val arch = if (System.getProperty("os.arch").contains("aarch64")) "win_arm64" else "win_x64"
        // Mac: příloha má stálé jméno, jde to bez dotazu na seznam vydání (ten GitHub ze sdílených adres odmítá)
        val prima = if (Os.mac) ChiakiMac.adresa(REPO) else null
        val vydani = if (prima != null && runCatching { http(prima).disconnect() }.isSuccess) "\"browser_download_url\": \"$prima\""
            else http("https://api.github.com/repos/$REPO/releases/latest").readText()
        // jen adresy příloh: …/releases/download/<verze>/chiaki-ng-win_x64-MSYS2-portable-<verze>.zip
        // (na Macu obraz disku chiaki-ng-macos_arm64-Release.dmg)
        val adresa = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"").findAll(vydani).map { it.groupValues[1] }
            .firstOrNull { if (Os.mac) ChiakiMac.jeBalicek(it) else it.contains(arch) && it.contains("portable") && it.endsWith(".zip") }
            ?: error("ve vydání chiaki-ng není ${if (Os.mac) "verze pro Mac" else "přenosná verze pro $arch"}")
        log("Stahuji ${adresa.substringAfterLast('/')}…")
        val zip = File(Nastroje.slozka, if (Os.mac) "chiaki-ng.dmg" else "chiaki-ng.zip")
        zip.parentFile?.mkdirs()
        val c = http(adresa)
        val celkem = c.contentLengthLong
        var hotovo = 0L
        var procent = -1
        c.inputStream.use { vstup ->
            zip.outputStream().use { vystup ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = vstup.read(buf)
                    if (n < 0) break
                    vystup.write(buf, 0, n)
                    hotovo += n
                    if (celkem > 0) {
                        val p = (hotovo * 100 / celkem).toInt()
                        if (p != procent) { procent = p; postup(p) }
                    }
                }
            }
        }
        log("Rozbaluji…")
        slozka.deleteRecursively()
        if (Os.mac) MacDmg.rozbal(zip, slozka, "chiaki-ng.app") else rozbal(zip, slozka)
        zip.delete()
        if (exe() == null) "v balíčku chiaki-ng chybí ${if (Os.mac) "aplikace chiaki-ng" else "chiaki.exe"}" else null
    } catch (e: Exception) { e.message ?: e.toString() }

    private fun http(url: String): HttpURLConnection {
        var u = url
        repeat(5) {
            val c = URI(u).toURL().openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 10_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "Titulky PS5")
            val s = c.responseCode
            if (s in 300..399) { u = c.getHeaderField("Location") ?: error("přesměrování bez adresy"); c.disconnect(); return@repeat }
            if (s != 200) error("GitHub odpověděl $s")
            return c
        }
        error("moc přesměrování")
    }

    private fun HttpURLConnection.readText() = inputStream.use { String(it.readBytes(), Charsets.UTF_8) }

    private fun rozbal(zip: File, kam: File) {
        val koren = kam.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val cil = File(koren, e.name).canonicalFile
                // nic mimo cílovou složku (cesty s „..“ v balíčku)
                if (!cil.path.startsWith(koren.path + File.separator)) continue
                if (e.isDirectory) cil.mkdirs() else {
                    cil.parentFile.mkdirs()
                    cil.outputStream().use { z.copyTo(it) }
                }
            }
        }
    }

    // ---- spárované konzole (z nastavení chiaki-ng) ----

    data class Konzole(val jmeno: String, val probuzeni: ULong?)

    fun konzole(profil: String? = null): List<Konzole> = runCatching {
        if (Os.mac) return ChiakiMac.konzole(profil)
        if (!Os.windows) return emptyList()
        val k = "${reg(profil)}\\registered_hosts"
        if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, k)) return emptyList()
        Advapi32Util.registryGetKeys(WinReg.HKEY_CURRENT_USER, k).sortedBy { it.toIntOrNull() ?: 0 }.mapNotNull { i ->
            val h = "$k\\$i"
            val jmeno = hodnota(h, "server_nickname")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Konzole(jmeno, hodnota(h, "rp_regist_key")?.let { probuzeni(it) })
        }
    }.getOrDefault(emptyList())

    /** Hodnota z registru jako text – chiaki-ng (Qt) ukládá bajty jako „@ByteArray(…)“, případně v UTF-16. */
    private fun hodnota(klic: String, nazev: String): String? {
        if (!Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, klic, nazev)) return null
        return when (val v = Advapi32Util.registryGetValue(WinReg.HKEY_CURRENT_USER, klic, nazev)) {
            is String -> v
            is ByteArray -> String(v, Charsets.UTF_16LE)
            else -> v?.toString()
        }
    }

    /** Číslo pro probuzení z klíče registrace (šestnáctkový text na začátku, jako [Ps5.wakeCredential]). */
    internal fun probuzeni(ulozeno: String): ULong? {
        val text = ulozeno.removePrefix("@ByteArray(")
        val hex = text.takeWhile { it.isLetterOrDigit() }
        return hex.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }?.toULongOrNull(16)
    }

    // ---- PS5 v síti ----

    data class Nalez(val adresa: String, val jmeno: String, val pripravena: Boolean, val klid: Boolean)

    /** Hledání PlayStationů v domácí síti (stejná zpráva jako v aplikaci, `core/Ps5.kt`). */
    fun hledej(ms: Int = 1500): List<Nalez> {
        val vysledek = LinkedHashMap<String, Nalez>()
        DatagramSocket().use { s ->
            s.broadcast = true
            s.soTimeout = 250
            val zprava = Ps5.searchPacket(true)
            val cile = vysilani()
            val konec = System.currentTimeMillis() + ms
            var dalsi = 0L
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < konec) {
                if (System.currentTimeMillis() >= dalsi) {
                    for (c in cile) runCatching { s.send(DatagramPacket(zprava, zprava.size, c, Ps5.DISCOVERY_PORT_PS5)) }
                    dalsi = System.currentTimeMillis() + 500
                }
                try {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    val r = Ps5.parseDiscoveryReply(String(p.data, 0, p.length, Charsets.UTF_8)) ?: continue
                    if (!r.type.contains("PS5", ignoreCase = true) && r.type.isNotEmpty()) continue
                    val a = p.address.hostAddress
                    vysledek[a] = Nalez(a, r.name, r.ready, r.standby)
                } catch (_: SocketTimeoutException) {}
            }
        }
        return vysledek.values.toList()
    }

    private fun vysilani(): List<InetAddress> {
        val out = LinkedHashSet<InetAddress>()
        out += InetAddress.getByName("255.255.255.255")
        runCatching {
            for (n in NetworkInterface.getNetworkInterfaces()) {
                if (!n.isUp || n.isLoopback || n.isVirtual) continue
                n.interfaceAddresses.mapNotNullTo(out) { it.broadcast }
            }
        }
        return out.toList()
    }

    /** Probuzení PS5 z klidového režimu (formát zprávy jako v chiaki-ng, `lib/src/discovery.c`). */
    fun probud(adresa: String, cislo: ULong) {
        val zprava = ("WAKEUP * HTTP/1.1\nclient-type:vr\nauth-type:R\nmodel:w\napp-type:r\n" +
            "user-credential:$cislo\ndevice-discovery-protocol-version:00030010\n").toByteArray()
        DatagramSocket().use { s ->
            repeat(3) { s.send(DatagramPacket(zprava, zprava.size, InetAddress.getByName(adresa), Ps5.DISCOVERY_PORT_PS5)) }
        }
    }

    // ---- spuštění ----

    /**
     * Hra v chiaki-ng; chiaki-ng se po konci spojení sám zavře. [celaObrazovka] = přes celou obrazovku
     * (hraní na PC); bez ní okno (čtečka pro TV – obraz se jen čte, okno může být za jinými).
     * [profil] = profil chiaki-ng (spárované konzole a nastavení zvlášť).
     *
     * Volby musí být před „stream“: chiaki-ng bere všechno za prvním slovem bez pomlček jako slova
     * příkazu (`ParseAsPositionalArguments` v `gui/src/main.cpp`) a adresa konzole je poslední slovo.
     */
    fun hraj(exe: File, konzole: String, adresa: String, profil: String? = null, celaObrazovka: Boolean = true): Process {
        val cmd = mutableListOf(exe.path)
        if (!profil.isNullOrEmpty()) { cmd += "--profile"; cmd += profil }
        cmd += "--exit-app-on-stream-exit"
        if (celaObrazovka) cmd += "--fullscreen"
        cmd += listOf("stream", konzole, adresa)
        return ProcessBuilder(cmd)
            .directory(exe.parentFile).redirectErrorStream(true)
            .redirectOutput(File(Nastroje.slozka, "chiaki-vystup.txt")).start()
    }

    // ---- profil čtečky pro TV ----

    /** Co se z hráčova nastavení chiaki-ng do profilu čtečky nepřebírá (okno, zvuk, účet PSN). */
    private val NEKOPIROVAT = setOf(
        "current_profile", "geometry", "stream_geometry", "window_type", "audio_volume",
        "audio_out_device", "audio_in_device", "auto_connect_mac",
    )

    /**
     * Profil chiaki-ng pro čtečku pro TV. Poprvé převezme hráčovo nastavení obrazu (vykreslování,
     * dekodér, rozlišení – co na tomto PC funguje), ne okno, zvuk ani přihlášení PSN; okno ve velikosti
     * obrazu („Selected Resolution“). Pokaždé nastaví hlasitost 0: hra zní z TV a chiaki-ng s nulovou
     * hlasitostí zvuk vůbec nepouští (`StreamSession::PushAudioFrame`). Hráčův výchozí profil se nemění.
     */
    fun pripravProfil(profil: String = PROFIL_CTECKY) {
        if (Os.mac) return ChiakiMac.pripravProfil(profil, NEKOPIROVAT)
        val hk = WinReg.HKEY_CURRENT_USER
        val cil = "${reg(profil)}\\settings"
        if (!Advapi32Util.registryKeyExists(hk, cil)) {
            Advapi32Util.registryCreateKey(hk, REG)
            Advapi32Util.registryCreateKey(hk, reg(profil))
            Advapi32Util.registryCreateKey(hk, cil)
            val zdroj = "$REG\\settings"
            if (Advapi32Util.registryKeyExists(hk, zdroj)) {
                for ((n, v) in Advapi32Util.registryGetValues(hk, zdroj)) {
                    if (n in NEKOPIROVAT || n.startsWith("psn")) continue
                    runCatching {
                        when (v) {
                            is String -> Advapi32Util.registrySetStringValue(hk, cil, n, v)
                            is Int -> Advapi32Util.registrySetIntValue(hk, cil, n, v)
                            is Long -> Advapi32Util.registrySetLongValue(hk, cil, n, v)
                            is ByteArray -> Advapi32Util.registrySetBinaryValue(hk, cil, n, v)
                        }
                    }
                }
            }
            Advapi32Util.registrySetStringValue(hk, cil, "window_type", "Selected Resolution")
        }
        Advapi32Util.registrySetIntValue(hk, cil, "audio_volume", 0)
    }

    /**
     * Profil, se kterým se chiaki-ng otevře příště (`settings/current_profile`). chiaki-ng spuštěný
     * s `--profile` si ho přepíše natrvalo – ReCaption ho po spuštění vrátí ([obnovProfil]), ať hráčovo
     * chiaki-ng neotevírá profil čtečky. null = žádný (výchozí profil).
     */
    fun aktualniProfil(): String? = runCatching {
        if (Os.mac) return ChiakiMac.aktualniProfil()
        if (!Os.windows) return null
        val k = "$REG\\settings"
        val hk = WinReg.HKEY_CURRENT_USER
        if (!Advapi32Util.registryKeyExists(hk, k) || !Advapi32Util.registryValueExists(hk, k, "current_profile")) null
        else Advapi32Util.registryGetStringValue(hk, k, "current_profile")
    }.getOrNull()

    fun obnovProfil(puvodni: String?) {
        runCatching {
            if (Os.mac) return ChiakiMac.obnovProfil(puvodni)
            if (!Os.windows) return
            val k = "$REG\\settings"
            val hk = WinReg.HKEY_CURRENT_USER
            if (!Advapi32Util.registryKeyExists(hk, k)) return
            val ted = if (Advapi32Util.registryValueExists(hk, k, "current_profile")) Advapi32Util.registryGetStringValue(hk, k, "current_profile") else null
            if (ted == puvodni) return
            if (puvodni == null) Advapi32Util.registryDeleteValue(hk, k, "current_profile")
            else Advapi32Util.registrySetStringValue(hk, k, "current_profile", puvodni)
        }
    }

    /** Běží chiaki-ng? Při zavření by si přepsal seznam konzolí tím, co měl v paměti. */
    fun bezi(): List<ProcessHandle> = ProcessHandle.allProcesses().filter { p ->
        p.info().command().map { File(it).name.equals(if (Os.windows) "chiaki.exe" else "chiaki", ignoreCase = true) }.orElse(false)
    }.toList()

    // ---- zápis spárované konzole (tvar jako QSettings v chiaki-ng, `RegisteredHost::SaveToSettings`) ----

    /**
     * Uloží spárovanou konzoli mezi `registered_hosts` chiaki-ng (profilu [profil], null = výchozí). Stejná konzole (podle MAC) se
     * přepíše, jinak se přidá na konec. Qt ukládá pole jako podklíče 1…N a hodnotu `size`, čísla
     * jako REG_DWORD, text jako REG_SZ a bajty jako text „@ByteArray(…)“ (Latin-1) v UTF-16 –
     * s nulami uvnitř jako REG_BINARY.
     */
    fun uloz(k: Registrace.Konzole, profil: String? = null) {
        if (Os.mac) return ChiakiMac.uloz(k, profil)
        val hk = WinReg.HKEY_CURRENT_USER
        val pole = "${reg(profil)}\\registered_hosts"
        if (!Advapi32Util.registryKeyExists(hk, pole)) Advapi32Util.registryCreateKey(hk, pole)
        val indexy = Advapi32Util.registryGetKeys(hk, pole).mapNotNull { it.toIntOrNull() }
        val stejny = indexy.firstOrNull { i -> bajty(hodnota("$pole\\$i", "server_mac"))?.contentEquals(k.mac) == true }
        val i = stejny ?: ((indexy.maxOrNull() ?: 0) + 1)
        val h = "$pole\\$i"
        if (!Advapi32Util.registryKeyExists(hk, h)) Advapi32Util.registryCreateKey(hk, h)
        Advapi32Util.registrySetIntValue(hk, h, "target", 1000100) // CHIAKI_TARGET_PS5_1
        for ((n, v) in k.ap) Advapi32Util.registrySetStringValue(hk, h, n, qtText(v))
        Advapi32Util.registrySetStringValue(hk, h, "server_nickname", qtText(k.jmeno))
        Advapi32Util.registrySetBinaryValue(hk, h, "server_mac", qtBajty(k.mac))
        Advapi32Util.registrySetBinaryValue(hk, h, "rp_regist_key", qtBajty(k.klicRegistrace))
        Advapi32Util.registrySetIntValue(hk, h, "rp_key_type", k.typKlice)
        Advapi32Util.registrySetBinaryValue(hk, h, "rp_key", qtBajty(k.klicRelace))
        Advapi32Util.registrySetStringValue(hk, h, "console_pin", "")
        Advapi32Util.registrySetIntValue(hk, pole, "size", maxOf(i, indexy.maxOrNull() ?: 0))
    }

    /** Text tak, jak ho zapisuje Qt (začátek „@“ se zdvojí). */
    internal fun qtText(s: String) = if (s.startsWith("@")) "@$s" else s

    private fun qtBajty(b: ByteArray): ByteArray =
        ("@ByteArray(" + String(b, Charsets.ISO_8859_1) + ")").toByteArray(Charsets.UTF_16LE)

    /** „@ByteArray(…)“ z registru zpět na bajty. */
    private fun bajty(ulozeno: String?): ByteArray? {
        val s = ulozeno ?: return null
        if (!s.startsWith("@ByteArray(") || !s.endsWith(")")) return null
        return s.substring(11, s.length - 1).toByteArray(Charsets.ISO_8859_1)
    }
}

/**
 * chiaki-ng na Macu: aplikace `chiaki-ng.app` a nastavení v předvolbách macOS ([MacPredvolby]). Tvar hodnot
 * je stejný jako v `RegisteredHost::SaveToSettings` chiaki-ng – jen klíče skupin dělí tečka
 * (`registered_hosts.1.rp_key`) a bajty jsou data, ne text „@ByteArray(…)“.
 */
internal object ChiakiMac {
    /** Spustitelný soubor uvnitř aplikace – stažené do složky nástrojů, nebo nainstalované ve složce Aplikace. */
    fun exe(): File? = listOf(
        File(Chiaki.slozka, "chiaki-ng.app"),
        File("/Applications/chiaki-ng.app"),
        File(System.getProperty("user.home"), "Applications/chiaki-ng.app"),
    ).map { File(it, "Contents/MacOS/chiaki") }.firstOrNull { it.isFile }

    /** Příloha vydání chiaki-ng pro tento Mac (Apple Silicon `macos_arm64`, Intel `macos_x64` / bez označení). */
    fun jeBalicek(adresa: String): Boolean {
        val n = adresa.substringAfterLast('/').lowercase()
        if (!n.endsWith(".dmg") || !n.contains("mac")) return false
        val arm = n.contains("arm64") || n.contains("aarch64")
        return arm == System.getProperty("os.arch").contains("aarch64")
    }

    /** Přímá adresa obrazu disku z posledního vydání (jen Apple Silicon – pro Intel se hledá v seznamu příloh). */
    fun adresa(repo: String): String? =
        if (System.getProperty("os.arch").contains("aarch64")) "https://github.com/$repo/releases/latest/download/chiaki-ng-macos_arm64-Release.dmg" else null

    private fun bajty(v: Any?): ByteArray? = v as? ByteArray

    /** Čísla položek pole `registered_hosts` (Qt čísluje od 1). */
    private fun indexy(hodnoty: Map<String, Any>): List<Int> =
        hodnoty.keys.mapNotNull { Regex("^registered_hosts\\.(\\d+)\\.").find(it)?.groupValues?.get(1)?.toIntOrNull() }.distinct().sorted()

    fun konzole(profil: String?): List<Chiaki.Konzole> {
        val h = MacPredvolby.nacti(MacPredvolby.domena(profil))
        return indexy(h).mapNotNull { i ->
            val jmeno = (h["registered_hosts.$i.server_nickname"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val klic = bajty(h["registered_hosts.$i.rp_regist_key"])?.let { String(it, Charsets.ISO_8859_1) }
            Chiaki.Konzole(jmeno, klic?.let { Chiaki.probuzeni(it) })
        }
    }

    fun uloz(k: Registrace.Konzole, profil: String?) {
        val d = MacPredvolby.domena(profil)
        val h = MacPredvolby.nacti(d)
        val ix = indexy(h)
        val stejny = ix.firstOrNull { i -> bajty(h["registered_hosts.$i.server_mac"])?.contentEquals(k.mac) == true }
        val i = stejny ?: ((ix.maxOrNull() ?: 0) + 1)
        val p = "registered_hosts.$i."
        MacPredvolby.zapis(d, p + "target", 1000100) // CHIAKI_TARGET_PS5_1
        for ((n, v) in k.ap) MacPredvolby.zapis(d, p + n, Chiaki.qtText(v))
        MacPredvolby.zapis(d, p + "server_nickname", Chiaki.qtText(k.jmeno))
        MacPredvolby.zapis(d, p + "server_mac", k.mac)
        MacPredvolby.zapis(d, p + "rp_regist_key", k.klicRegistrace)
        MacPredvolby.zapis(d, p + "rp_key_type", k.typKlice)
        MacPredvolby.zapis(d, p + "rp_key", k.klicRelace)
        MacPredvolby.zapis(d, p + "console_pin", "")
        MacPredvolby.zapis(d, "registered_hosts.size", maxOf(i, ix.maxOrNull() ?: 0))
    }

    fun pripravProfil(profil: String, nekopirovat: Set<String>) {
        val cil = MacPredvolby.domena(profil)
        val ma = MacPredvolby.nacti(cil).keys.any { it.startsWith("settings.") }
        if (!ma) {
            for ((n, v) in MacPredvolby.nacti(MacPredvolby.domena(null))) {
                if (!n.startsWith("settings.")) continue
                val jmeno = n.removePrefix("settings.")
                if (jmeno in nekopirovat || jmeno.startsWith("psn")) continue
                runCatching { MacPredvolby.zapis(cil, n, v) }
            }
            MacPredvolby.zapis(cil, "settings.window_type", "Selected Resolution")
        }
        MacPredvolby.zapis(cil, "settings.audio_volume", 0)
    }

    fun aktualniProfil(): String? =
        MacPredvolby.nacti(MacPredvolby.domena(null))["settings.current_profile"] as? String

    fun obnovProfil(puvodni: String?) {
        val d = MacPredvolby.domena(null)
        val h = MacPredvolby.nacti(d)
        if (h.isEmpty()) return
        val ted = h["settings.current_profile"] as? String
        if (ted == puvodni) return
        if (puvodni == null) MacPredvolby.smaz(d, "settings.current_profile")
        else MacPredvolby.zapis(d, "settings.current_profile", puvodni)
    }
}
