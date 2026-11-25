package cz.recaption.core

/**
 * Na čem uživatel ukazuje překlad (volba „Typ televize“ na stránce Domů):
 *  - [ANDROID] – pruh ukazuje toto zařízení (Android TV, box); tak to bylo vždycky,
 *  - [LG], [SAMSUNG] – televize sama obraz nepřečte, toto zařízení čte a překládá a hotové věty
 *    posílá aplikaci ReCaption v televizi (core/TvSender.kt; LG = webos/obchod, Samsung = tizen).
 *
 * Ukládá se jako text ([key]). Kdo typ ještě nezvolil, ale měl zapnuté staré „Posílat titulky do
 * televize LG“ (Prefs `tv_send`), má LG – nic se mu nezmění.
 */
enum class TvType(val key: String) {
    ANDROID("android"), LG("lg"), SAMSUNG("samsung");

    /** Posílají se titulky po síti do televize? */
    val sends: Boolean get() = this != ANDROID

    /** Platforma, kterou hlásí aplikace v televizi (pole `platforma` ve zprávě `pripojeno`). */
    val platform: String? get() = when (this) { LG -> TvAnnounce.WEBOS; SAMSUNG -> TvAnnounce.TIZEN; ANDROID -> null }

    companion object {
        fun from(stored: String?, legacyTvSend: Boolean): TvType =
            values().firstOrNull { it.key == stored } ?: if (legacyTvSend) LG else ANDROID
    }
}

/**
 * Ohlášení aplikace v televizi: zpráva `{"typ":"pripojeno",…}`, kterou televize pošle hned po
 * připojení (webos/obchod/app/app.js, `prevezmi`). Od verze 1.1.0 nese i `aplikace`, `platforma`
 * („webos“ / „tizen“) a `verze`; starší aplikace v LG posílala jen `televize`, `system` a `hdmi`.
 */
data class TvAnnounce(
    val platform: String?,
    val version: String?,
    val model: String?,
    val system: String?,
    val hdmi: Int?,
) {
    companion object {
        const val WEBOS = "webos"
        const val TIZEN = "tizen"
        private val VERSION = Regex("[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{1,8}")

        /** null = není to ohlášení (jiná zpráva, nečitelné). Pole jsou z cizího zařízení – ořezaná. */
        fun parse(text: String): TvAnnounce? {
            if (text.length > 4096) return null
            val m = MiniJson.obj(text) ?: return null
            if (m["typ"] != "pripojeno") return null
            fun clean(k: String, max: Int) = (m[k] as? String)?.replace(Regex("[\\p{C}]+"), " ")?.trim()?.take(max)?.takeIf { it.isNotEmpty() && it != "?" }
            val platform = clean("platforma", 20)?.lowercase()?.takeIf { it.matches(Regex("[a-z0-9]+")) }
            val version = clean("verze", 30)?.takeIf { it.matches(VERSION) }
            val hdmi = (m["hdmi"] as? Long)?.toInt()?.takeIf { it in 1..9 }
            // bez platformy: starší aplikace pro LG (jiná televize ReCaption dřív neměla)
            return TvAnnounce(platform ?: WEBOS.takeIf { version == null }, version, clean("televize", 60), clean("system", 80), hdmi)
        }
    }
}

/** Co průvodce nahráním do televize Samsung ví o aplikaci v televizi. */
enum class TvCheck {
    /** Zatím se nic neohlásilo. */
    WAITING,
    /** Ohlásila se televize jiného typu (např. LG místo Samsungu). */
    OTHER,
    /** Ohlásila se bez verze – starší aplikace, je potřeba nahrát novou. */
    NO_VERSION,
    /** V televizi je starší verze než v tomto zařízení. */
    OLDER,
    /** Stejná verze – hotovo. */
    SAME,
    /** V televizi je novější verze – nic nepřepisovat. */
    NEWER;

    val ok: Boolean get() = this == SAME || this == NEWER

    companion object {
        /** [seen] = poslední ohlášení (null = žádné), [platform] = očekávaná platforma, [bundled] = verze v APK. */
        fun of(seen: TvAnnounce?, platform: String, bundled: String): TvCheck {
            seen ?: return WAITING
            if (seen.platform != null && seen.platform != platform) return OTHER
            val v = seen.version ?: return NO_VERSION
            val d = try { LgInstall.compare(v, bundled) } catch (_: LgException) { return NO_VERSION }
            return when {
                d < 0 -> OLDER
                d == 0 -> SAME
                else -> NEWER
            }
        }
    }
}

/** Ověřený balíček pro Samsung z APK (assets/samsung). */
class SamsungBundle(val id: String, val version: String, val fileName: String, val sha256: String, val data: ByteArray)

object SamsungInstall {
    /** Kolik bajtů se v APK nejvýš čeká – víc znamená poškozený nebo podvržený soubor. */
    private const val MAX_SIZE = 4 * 1024 * 1024

    /**
     * Ověří přibalený balíček .wgt proti jeho popisu (id, verze, velikost, otisk, nepodepsaný).
     * null = chybí nebo nesedí (uživateli se nabídne aktualizace ReCaptionu, nic se neuloží).
     */
    fun bundle(manifest: String, data: ByteArray): SamsungBundle? {
        val m = MiniJson.obj(manifest) ?: return null
        val v = m["version"] as? String ?: return null
        val id = m["id"] as? String ?: return null
        val file = m["file"] as? String ?: return null
        val ok = (m["schema"] as? Long) == 1L && m["platform"] == "tizen" && id == SamsungWgt.APP_ID &&
            v.matches(Regex("[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{1,8}")) && m["signed"] == false &&
            file == SamsungWgt.fileName(v) && (m["size"] as? Long) == data.size.toLong() &&
            data.size in 22..MAX_SIZE && data[0] == 'P'.code.toByte() && data[1] == 'K'.code.toByte()
        if (!ok) return null
        val hash = LgIpk.sha256(data)
        if (m["sha256"] != hash) return null
        // podepsaný balíček by se vydával za univerzální – takový v APK být nesmí
        if (SamsungWgt.names(data).any { SamsungWgt.isSignature(it) }) return null
        return SamsungBundle(id, v, file, hash, data)
    }
}

/**
 * Průvodce, krok Televize (telefon jako čtečka): podle televizí nalezených v domácí síti (LG přes SSDP webOS
 * [LgSetup], Samsung přes SSDP Tizen [SamsungSetup]) předvybere typ – jen když je v síti jediný druh, jinak nic.
 */
object TvDetect {
    fun suggest(lg: List<String>, samsung: List<String>): TvType? = when {
        lg.isNotEmpty() && samsung.isEmpty() -> TvType.LG
        samsung.isNotEmpty() && lg.isEmpty() -> TvType.SAMSUNG
        else -> null
    }
}
