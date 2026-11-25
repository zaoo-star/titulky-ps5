package cz.recaption.core

import kotlin.math.ceil

/**
 * „Poslat vývojáři“ (obrazovka hlášení a ⚙ → Experimentální → Ladění a hlášení): hlášení o stavu a případně
 * jedna dávka snímků ze hry jako ZIP na úložiště vývojáře; aplikace ukáže kód (`K7M2-QX9P`), hráč ho pošle
 * majiteli a ten si balík stáhne. Tady je všechno bez Androidu (testy `ReportUploadTest`); odesílání je `ReportSender`.
 *
 * Smlouva se serverem:
 * - adresa serveru je na stránkách s pomocí: `GET` [CONFIG_URL] → `{"format":1,"url":"https://….workers.dev","maxMB":45}`
 *   (chybí / neplatné / prázdné `url` = odesílání není nastavené),
 * - `POST <url>/v1/nahrat`, tělo ZIP (`application/zip`, pevná délka), hlavičky [HEADER_VERSION] a [HEADER_KIND],
 * - 201 `{"kod":"K7M2QX9P","platnost_dni":30}`; 400 není ZIP, 413 moc velké, 429 moc odeslání, 503 denní limit.
 */
object ReportUpload {
    const val CONFIG_URL = HelpLinks.BASE + "hlaseni.json"
    const val FORMAT = 1
    const val DEFAULT_MB = 45
    /** Víc se nikdy neposílá, ať by v `hlaseni.json` stálo cokoli. */
    const val MAX_MB = 200
    const val PATH = "/v1/nahrat"
    const val HEADER_VERSION = "X-ReCaption-Verze"
    const val HEADER_KIND = "X-ReCaption-Druh"
    /**
     * Nepovinné hlavičky s týmiž údaji, jaké jsou i v `info.json` v balíku (žádný nový údaj navíc) – server
     * si je jen uloží k balíku (bez otevírání ZIPu), ať jde testerská hlášení na stránce počtů aspoň zběžně
     * roztřídit podle zařízení a TV, aniž by se musel stahovat celý balík (přání majitele 24. 11. 2026).
     */
    const val HEADER_DEVICE = "X-ReCaption-Zarizeni"
    const val HEADER_TV_TYPE = "X-ReCaption-TypTv"
    const val HEADER_TV_INFO = "X-ReCaption-Tv"
    const val HEADER_GAME = "X-ReCaption-Hra"
    /** Hlavička se ořízne na tuhle délku (ochrana serveru), delší údaj je prostě zkrácený. */
    const val HEADER_MAX = 80
    const val KIND_REPORT = "hlaseni"
    const val KIND_SNAPS = "snimky"
    /** Sbírání replik ze hry (LineCollector, od 0.97.139). */
    const val KIND_LINES = "repliky"
    const val DEFAULT_DAYS = 30
    /** Největší čtená odpověď serveru / `hlaseni.json` (znaků). */
    const val MAX_ANSWER = 4096

    /** Názvy v ZIPu. */
    const val ZIP_REPORT = "hlaseni.txt"
    const val ZIP_INFO = "info.json"
    const val ZIP_SNAPS = "snimky/"
    const val ZIP_LINES = "repliky.tsv"

    /** Zmenšení snímků, když se celá dávka do limitu nevejde. */
    const val SHRINK_W = 960
    const val SHRINK_QUALITY = 70
    /** Rezerva pod limitem (hlavičky ZIPu, odhad zmenšení). */
    const val MARGIN = 256L * 1024
    /** Odhad místa v ZIPu na jeden soubor navíc k datům (místní hlavička + položka v adresáři + název). */
    const val ENTRY_OVERHEAD = 160L

    /** Nastavení serveru: kam posílat a nejvýš kolik bajtů. */
    data class Config(val url: String, val maxBytes: Long) {
        val uploadUrl: String get() = url + PATH
    }

    private val HOST = Regex("https://[A-Za-z0-9.-]+(:[0-9]{1,5})?(/[A-Za-z0-9._~/-]*)?")

    /** Obsah `hlaseni.json` → nastavení, nebo null (soubor chybí, je neplatný, jiný formát, prázdná / ne-https adresa). */
    fun config(text: String?): Config? {
        if (text.isNullOrBlank() || text.length > MAX_ANSWER) return null
        val o = MiniJson.obj(text) ?: return null
        if ((o["format"] as? Number)?.toLong() != FORMAT.toLong()) return null
        val url = (o["url"] as? String)?.trim()?.trimEnd('/') ?: return null
        if (url.isEmpty() || url.length > 200 || !HOST.matches(url)) return null
        val mb = (o["maxMB"] as? Number)?.toDouble()?.takeIf { it >= 1 && it <= MAX_MB } ?: DEFAULT_MB.toDouble()
        // MB jako 1 000 000 bajtů – menší z obou výkladů, ať server neodmítne balík těsně pod limitem (413)
        return Config(url, (mb * 1_000_000).toLong())
    }

    /** Výsledek odeslání: kód pro vývojáře, nebo klíč textu chyby (`snd_e_…`). */
    data class Answer(val code: String?, val days: Int, val error: String?) {
        val ok: Boolean get() = code != null
    }

    const val E_NET = "snd_e_net"
    const val E_BAD = "snd_e_bad"
    const val E_BIG = "snd_e_big"
    const val E_BUSY = "snd_e_busy"
    const val E_LIMIT = "snd_e_limit"
    const val E_FAIL = "snd_e_fail"
    const val E_PACK = "snd_e_pack"
    const val E_OFF = "snd_off"

    fun error(key: String) = Answer(null, 0, key)

    /** Odpověď serveru (stav HTTP a tělo) → kód, nebo chyba. */
    fun answer(status: Int, body: String?): Answer = when (status) {
        200, 201 -> {
            val o = body?.takeIf { it.length <= MAX_ANSWER }?.let { MiniJson.obj(it) }
            val code = (o?.get("kod") as? String)?.let(::normalize)
            val days = (o?.get("platnost_dni") as? Number)?.toInt()?.takeIf { it in 1..3650 } ?: DEFAULT_DAYS
            if (code != null) Answer(code, days, null) else error(E_FAIL)
        }
        400 -> error(E_BAD)
        413 -> error(E_BIG)
        429 -> error(E_BUSY)
        503 -> error(E_LIMIT)
        else -> error(E_FAIL)
    }

    private val CODE = Regex("[A-Z0-9]{6,16}")

    /** Kód ze serveru (i s pomlčkou, mezerami, malými písmeny) → tvar bez pomlčky, nebo null (neplatný). */
    fun normalize(code: String): String? = code.uppercase().filter { it != '-' && it != ' ' }.takeIf { CODE.matches(it) }

    /** Kód k zobrazení: pomlčka uprostřed (`K7M2QX9P` → `K7M2-QX9P`), lichá délka pomlčku dostane za delší půlkou. */
    fun display(code: String): String {
        val c = normalize(code) ?: return code
        val mid = (c.length + 1) / 2
        return c.substring(0, mid) + "-" + c.substring(mid)
    }

    // ---------------- snímky do limitu ----------------

    /**
     * Které snímky dávky poslat: [shrink] = zmenšit (delší strana [SHRINK_W], JPEG [SHRINK_QUALITY]),
     * [picked] = indexy snímků (každý [step]-tý), [bytes] = odhad celého ZIPu.
     */
    data class SnapPlan(val shrink: Boolean, val step: Int, val picked: List<Int>, val bytes: Long) {
        val all: Boolean get() = step == 1
    }

    /**
     * Plán pro snímky o velikostech [sizes] (bajty, v pořadí) vedle pevné části [fixed] (hlášení, info, index)
     * do limitu [limit] (bez rezervy [MARGIN]). [ratio] = poměr velikosti zmenšeného snímku k původnímu
     * (null = snímky beze změny). Bere každý n-tý snímek s nejmenším n, které se vejde; nevejde-li se ani
     * jeden, je seznam prázdný (pošle se jen hlášení a index).
     */
    fun plan(sizes: List<Long>, fixed: Long, limit: Long, ratio: Double?): SnapPlan {
        val shrink = ratio != null
        val r = (ratio ?: 1.0).coerceIn(0.01, 1.0)
        val room = limit - MARGIN - fixed
        fun cost(i: Int) = ceil(sizes[i] * r).toLong() + ENTRY_OVERHEAD
        if (sizes.isEmpty()) return SnapPlan(shrink, 1, emptyList(), fixed)
        var step = 1
        if (room > 0) {
            // od kroku 1 po jedné: při nerovnoměrných velikostech se může vejít i hustší výběr, než říká průměr
            // (dávka má nejvýš pár tisíc snímků – stojí to zlomek milisekundy)
            while (step <= sizes.size) {
                val picked = sizes.indices.step(step).toList()
                var sum = 0L
                for (i in picked) sum += cost(i)
                if (sum <= room) return SnapPlan(shrink, step, picked, fixed + sum)
                step++
            }
        }
        return SnapPlan(shrink, sizes.size + 1, emptyList(), fixed)
    }

    /**
     * Poměr zmenšení z vyzkoušených snímků ([original] → [shrunk] bajtů), s rezervou 15 %; null = nic
     * nezměřeno. Nikdy víc než 1 (zmenšený snímek nebývá větší – kdyby byl, pošle se původní).
     */
    fun ratio(original: List<Long>, shrunk: List<Long>): Double? {
        val o = original.sum(); val s = shrunk.sum()
        if (original.isEmpty() || o <= 0 || s <= 0) return null
        return (s.toDouble() / o * 1.15).coerceIn(0.01, 1.0)
    }

    /** Vejde se další soubor o [next] bajtech, když už je zapsáno [written]? (Pojistka při skládání ZIPu.) */
    fun fits(written: Long, next: Long, limit: Long): Boolean = written + next + ENTRY_OVERHEAD <= limit - MARGIN / 4

    // ---------------- info.json ----------------

    /** `info.json` v balíku: dvojice název → hodnota (String, Number, Boolean, null); jen technické údaje. */
    fun infoJson(fields: List<Pair<String, Any?>>): String =
        fields.joinToString(",\n  ", "{\n  ", "\n}\n") { (k, v) ->
            TvSender.json(k) + ": " + when (v) {
                null -> "null"
                is Boolean -> v.toString()
                is Int, is Long -> v.toString()
                is Number -> v.toDouble().takeIf { it.isFinite() }?.toString() ?: "null"
                else -> TvSender.json(v.toString())
            }
        }

    /** Které dávky nabídnout: jen hotové (ne právě nahrávaná) se snímky, nejnovější nahoře. */
    fun offered(list: List<SnapBatches.Info>, recording: String?): List<SnapBatches.Info> =
        list.filter { it.snaps > 0 && it.name != recording }.sortedByDescending { it.n }
}
