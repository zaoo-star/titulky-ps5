package cz.recaption.core

/**
 * Aplikace v TV LG / Samsung bez nové verze pro Android (přání majitele, 24. 11. 2026): vydání `latest` a `zkusebni`
 * nesou vedle APK i `ReCaption-LG.ipk`, `ReCaption-Samsung.wgt` a `tv-verze.txt` (jen číslo, např. „1.5.44“).
 * Ruční workflow `.github/workflows/tv.yml` umí vydat jen tyto tři soubory. Telefon / Android TV si novější balíček
 * stáhne (cz.recaption.TvPackages) a nahraje ho do TV místo přibaleného v APK; jinak (offline, chyba, starší
 * nebo stejná verze) platí přibalený jako dřív. Tady je část bez Androidu: čtení čísla verze, porovnání,
 * kontrola staženého balíčku a výběr mezi staženým a přibaleným.
 */
object TvUpdate {
    /** Soubor s číslem verze aplikace v TV ve vydání (kanál latest / zkusebni). */
    const val VERSION_FILE = "tv-verze.txt"
    const val LG_FILE = "ReCaption-LG.ipk"
    const val SAMSUNG_FILE = "ReCaption-Samsung.wgt"

    /** Víc než tolik bajtů balíček pro TV mít nemá (dnes ~80 kB) – jinak poškozený nebo podvržený soubor. */
    const val MAX_SIZE = 4 * 1024 * 1024
    /** Nejmenší rozumná velikost (prázdný archiv to není). */
    const val MIN_SIZE = 1024

    /** Bez otevřené stránky nahrání se ptá nejvýš jednou za 6 h (jeden malý dotaz na tv-verze.txt). */
    const val CHECK_EVERY_MS = 6 * 3600_000L
    /** Na stránce nahrání (LG / Samsung) a u „Aktualizovat v TV“ nejvýš jednou za 10 min. */
    const val PAGE_EVERY_MS = 10 * 60_000L

    private val VERSION = Regex("[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{1,8}")

    /** Obsah tv-verze.txt: první slovo = verze „1.5.44“. Cokoli jiného = null. */
    fun parse(text: String?): String? = (text ?: "").trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.matches(VERSION) }

    /** Číselné porovnání (1.5.44 < 1.5.100); neplatné číslo = null. */
    fun compare(a: String?, b: String?): Int? {
        if (a == null || b == null || !a.matches(VERSION) || !b.matches(VERSION)) return null
        val x = a.split('.').map { it.toLong() }; val y = b.split('.').map { it.toLong() }
        for (i in 0..2) if (x[i] != y[i]) return x[i].compareTo(y[i])
        return 0
    }

    /** [a] je platná a novější než [b] (neznámé [b] = cokoli platného je novější). */
    fun newer(a: String?, b: String?): Boolean {
        if (a == null || !a.matches(VERSION)) return false
        if (b == null || !b.matches(VERSION)) return true
        return compare(a, b)!! > 0
    }

    /** Vyšší z verzí ve kanálech (tester bere i zkušební); null = žádná platná. */
    fun best(vararg versions: String?): String? = versions.filter { it != null && it.matches(VERSION) }
        .fold(null as String?) { acc, v -> if (newer(v, acc)) v else acc }

    /** Stáhnout balíček? Jen když je verze v kanálu novější než přibalená i než už stažená. */
    fun shouldDownload(remote: String?, bundled: String?, downloaded: String?): Boolean =
        newer(remote, bundled) && newer(remote, downloaded)

    /** Který balíček nahrát: stažený jen tehdy, když je novější než přibalený v APK. */
    fun useDownloaded(bundled: String?, downloaded: String?): Boolean = newer(downloaded, bundled)

    /** Zeptat se teď? [page] = stránka nahrání / tlačítko aktualizace (častěji); posunuté hodiny = ano. */
    fun shouldCheck(lastTry: Long, now: Long, page: Boolean): Boolean =
        lastTry <= 0 || now < lastTry || now - lastTry >= (if (page) PAGE_EVERY_MS else CHECK_EVERY_MS)

    /**
     * Ověří stažený balíček pro LG: archiv ipk se správným id a právě verzí [expected] (control i appinfo.json).
     * Vrací popis (stejný tvar jako assets/lg/receiver-manifest.json), jinak null.
     */
    fun checkLg(data: ByteArray, expected: String): String? = try {
        require(data.size in MIN_SIZE..MAX_SIZE && expected.matches(VERSION))
        val c = LgIpk.read(data)
        val lines = c.control.lines()
        require(lines.contains("Package: ${LgIpk.APP_ID}") && lines.contains("Version: $expected"))
        val info = c.file("usr/palm/applications/${LgIpk.APP_ID}/appinfo.json") ?: throw IllegalArgumentException("appinfo")
        require(LgIpk.appInfo(String(info, Charsets.UTF_8)) == (LgIpk.APP_ID to expected))
        LgIpk.manifest(LgIpk.APP_ID, expected, data)
    } catch (_: Exception) { null }

    /**
     * Ověří stažený balíček pro Samsung: ZIP s config.xml (id ReCaption1.ReCaption, verze [expected]), bez podpisu.
     * Vrací popis (stejný tvar jako assets/samsung/receiver-manifest.json), jinak null.
     */
    fun checkSamsung(data: ByteArray, expected: String): String? = try {
        require(data.size in MIN_SIZE..MAX_SIZE && expected.matches(VERSION))
        require(data[0] == 'P'.code.toByte() && data[1] == 'K'.code.toByte())
        // konec ústředního adresáře (náš balíček je bez komentáře) – useknutý soubor sem nedosáhne
        val e = data.size - 22
        require(data[e] == 0x50.toByte() && data[e + 1] == 0x4b.toByte() && data[e + 2] == 5.toByte() && data[e + 3] == 6.toByte())
        val files = SamsungWgt.read(data)
        require(files.keys.none { SamsungWgt.isSignature(it.substringAfterLast('/')) })
        val config = files["config.xml"] ?: throw IllegalArgumentException("config.xml")
        require(SamsungWgt.config(String(config, Charsets.UTF_8)) == (SamsungWgt.APP_ID to expected))
        SamsungWgt.manifest(SamsungWgt.APP_ID, expected, data)
    } catch (_: Exception) { null }
}
