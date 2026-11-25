package cz.recaption.core

import java.net.URLEncoder

/**
 * Průvodce ReCaptionem pro televizi Samsung přes Apps2Samsung (od 0.97.52, SamsungInstallActivity):
 * aplikace Apps2Samsung (zdarma, Android; https://github.com/Apps2Samsung/Apps2Samsung) sama najde televizi,
 * jedním přihlášením účtem Samsung vyrobí podpis pro tu televizi a nahraje do ní libovolný soubor .wgt.
 * ReCaption jí předá zdroj = naše vydání `latest` na GitHubu, ve kterém je `ReCaption-Samsung.wgt`
 * (přidává ho `.github/workflows/build.yml` z APK). Odkaz `apps2samsung://source?url=…` (a webová podoba na
 * apps2samsung.com, která ho předá aplikaci) umí Apps2Samsung od 3.3.1; sám nic neinstaluje, hráč potvrdí.
 *
 * Kroky se odškrtávají samy, kde to jde (vývojářský režim podle REST API televize a portu SDB 26101 na nalezené
 * TV Samsung, Apps2Samsung v tomto telefonu, ReCaption z TV se ohlásilo), jinak tlačítkem „Hotovo“.
 *
 * Vývojářský režim (od aplikace v TV 1.5.47 / ReCaption 1.0.x): TV Samsung odpovídá v domácí síti na
 * `GET http://<TV>:8001/api/v2/` JSONem `{"device":{"developerMode":"1","developerIP":"192.168.1.20","modelName":…,"name":…},…}`
 * (tvar podle Apps2Samsungu a dokumentace samsungtv-ws). Z něj [devState] pozná vypnutý režim, Host PC IP jinou než
 * adresa telefonu, adresu uloženou pozpátku i zapnutý režim bez restartu TV (port 26101 ještě zavřený). Když REST
 * neodpoví, platí jen otevřený port jako dřív.
 */
object SamsungSetup {
    enum class Step { DEV_MODE, INSTALLER, SOURCE, INSTALL, READY }

    /** Port, na kterém TV Samsung ve vývojářském režimu přijímá nástroje (sdb). */
    const val SDB_PORT = 26101

    /** Balíček aplikace Apps2Samsung v Androidu. */
    const val INSTALLER_PACKAGE = "nl.madebypatrick.apps2samsung"

    /** Zdroj pro Apps2Samsung: holý odkaz na repozitář (ne `/releases/tag/latest` – tester 24. 11. 2026 dostal
     *  „use a github repository or release link .wgt or .tpk file", odkaz na stránku vydání appka odmítla;
     *  Apps2Samsung si v repozitáři sám najde nejnovější vydání s `.wgt`). Na skutečné TV ověřil jen tenhle tvar. */
    const val SOURCE_URL = "https://github.com/adusa89/ReCaption"

    /** Název souboru ve vydání (Apps2Samsung bere soubory .wgt). */
    const val WGT_ASSET = "ReCaption-Samsung.wgt"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Odkaz, který otevře Apps2Samsung s předvyplněným zdrojem ReCaption. */
    fun sourceLink(): String = "apps2samsung://source?url=" + enc(SOURCE_URL)

    /** Totéž přes web (čtvereček pro telefon; stránka předá odkaz aplikaci, jinak nabídne její stažení). */
    fun sourceWebLink(): String = "https://apps2samsung.com/source?url=" + enc(SOURCE_URL)

    /** Dotaz SSDP na televize Samsung (ovladač Tizen) – kvůli adrese TV a zkoušce vývojářského režimu. */
    fun searchMessage(): String =
        "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
            "ST: urn:samsung.com:device:RemoteControlReceiver:1\r\n\r\n"

    /** Odpověď SSDP od televize Samsung → její adresa (odesílatel [from]), jen v domácí síti; jinak null. */
    fun parseResponse(text: String, from: String): String? {
        if (text.length > 4096 || !text.startsWith("HTTP/1.1 200")) return null
        if (!text.lowercase().contains("samsung")) return null
        return runCatching { LgInstall.address(from) }.getOrNull()
    }

    /** Port REST API televize Samsung (odpovídá i bez vývojářského režimu). */
    const val API_PORT = 8001

    /** Dotaz HTTP/1.0 na REST API (čte se přímo ze soketu, aby aplikace nemusela povolit HTTP bez šifrování). */
    fun apiRequest(ip: String): String = "GET /api/v2/ HTTP/1.0\r\nHost: $ip:$API_PORT\r\nConnection: close\r\n\r\n"

    /** Co o sobě TV říká v `/api/v2/`. [developerMode] null = pole chybí. */
    data class TvInfo(
        val developerMode: Boolean?,
        /** Host PC IP z vývojářského režimu; null = nevyplněná (prázdná nebo 0.0.0.0). */
        val developerIp: String?,
        val model: String?,
        val name: String?,
    )

    /**
     * Odpověď HTTP (s hlavičkou i bez ní) z `/api/v2/` → [TvInfo]; null = nejde o REST API televize Samsung
     * (jiný stav než 200, HTML ze směrovače, JSON bez `device`, přes 64 kB).
     */
    fun parseApi(raw: String): TvInfo? {
        if (raw.length > 65536) return null
        var body = raw
        if (raw.startsWith("HTTP/")) {
            if (!Regex("^HTTP/1\\.[01] 200\\b").containsMatchIn(raw)) return null
            val end = raw.indexOf("\r\n\r\n").takeIf { it >= 0 }?.plus(4) ?: raw.indexOf("\n\n").takeIf { it >= 0 }?.plus(2) ?: return null
            body = raw.substring(end)
        }
        val root = MiniJson.obj(body.trim()) ?: return null
        @Suppress("UNCHECKED_CAST")
        val d = root["device"] as? Map<String, Any?> ?: return null
        fun str(k: String) = (d[k] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val mode = when (str("developerMode")) { "1" -> true; "0" -> false; else -> null }
        val ip = str("developerIP")?.takeIf { isIpv4(it) && it != "0.0.0.0" }
        return TvInfo(mode, ip, str("modelName")?.take(40), str("name")?.take(60))
    }

    private fun isIpv4(s: String) = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$").matchEntire(s)
        ?.groupValues?.drop(1)?.all { it.toInt() <= 255 } == true

    /** Adresa s obrácenými čísly: 192.168.1.20 → 20.1.168.192 (tak ji některé TV Samsung uloží). */
    fun reversed(ip: String): String = ip.split('.').reversed().joinToString(".")

    /** Stav vývojářského režimu v TV, jak ho ukáže průvodce. */
    enum class DevState {
        /** Nic se neví (REST neodpověděl a port SDB je zavřený). */
        UNKNOWN,
        /** Developer mode vypnutý. */
        OFF,
        /** Zapnutý, ale Host PC IP prázdná. */
        NO_IP,
        /** Host PC IP je jiné zařízení než tento telefon. */
        OTHER_IP,
        /** Host PC IP je adresa telefonu pozpátku (TV ji tak uložila). */
        REVERSED_IP,
        /** Zapnutý a adresa sedí, ale port SDB je zavřený = TV se po zapnutí nerestartovala. */
        RESTART,
        /** Hotovo. */
        READY,
    }

    /**
     * Rozhodnutí podle REST API ([info], null = neodpovědělo), otevřeného portu SDB a adresy zařízení
     * s Apps2Samsungem ([phoneIp] = adresa tohoto telefonu; na TV/boxu null – Host PC IP je jiný telefon, adresa se neporovnává).
     */
    fun devState(info: TvInfo?, sdbOpen: Boolean, phoneIp: String?): DevState {
        if (info?.developerMode == null) return if (sdbOpen) DevState.READY else DevState.UNKNOWN
        if (!info.developerMode) return DevState.OFF
        val dev = info.developerIp ?: return DevState.NO_IP
        // 127.0.0.1 = přeposílání přes jinou aplikaci v TV (TV-Store) – není to „jiné zařízení“
        if (phoneIp != null && dev != phoneIp && dev != "127.0.0.1") {
            if (dev == reversed(phoneIp)) return if (sdbOpen) DevState.READY else DevState.REVERSED_IP
            return DevState.OTHER_IP
        }
        if (sdbOpen || phoneIp == null) return DevState.READY
        return DevState.RESTART
    }

    /** Klíč textu stavu pro průvodce (null = nic neukazovat). */
    fun devStateKey(s: DevState): String? = when (s) {
        DevState.OFF -> "sa_dm_off"
        DevState.NO_IP -> "sa_dm_noip"
        DevState.OTHER_IP -> "sa_dm_other"
        DevState.REVERSED_IP -> "sa_dm_reversed"
        DevState.RESTART -> "sa_dm_restart"
        DevState.UNKNOWN, DevState.READY -> null
    }

    /** Co se o přípravě ví. Ručně potvrzené kroky se počítají jako hotové. */
    data class Probe(
        /** Vývojářský režim v TV (port SDB otevřený, nebo potvrzeno). */
        val devMode: Boolean,
        /** Apps2Samsung je v tomto telefonu (nebo potvrzeno). */
        val installer: Boolean,
        /** Zdroj ReCaption byl předán Apps2Samsungu (nebo potvrzeno). */
        val source: Boolean,
        /** Co se ohlásilo z televize. */
        val check: TvCheck,
    )

    /** První krok, který ještě není hotový. Starší ReCaption v TV = jen aktualizovat v Apps2Samsungu. */
    fun step(p: Probe): Step = when {
        p.check.ok -> Step.READY
        p.check == TvCheck.OLDER || p.check == TvCheck.NO_VERSION -> Step.INSTALL
        !p.devMode -> Step.DEV_MODE
        !p.installer -> Step.INSTALLER
        !p.source -> Step.SOURCE
        else -> Step.INSTALL
    }

    /** Je krok [s] hotový? (Pro odškrtnutí v seznamu.) */
    fun done(s: Step, p: Probe): Boolean = step(p).ordinal > s.ordinal
}
