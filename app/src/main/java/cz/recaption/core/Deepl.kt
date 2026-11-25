package cz.recaption.core

/**
 * POKUS (výchozí vypnuto): lepší překlad přes internet službou DeepL s vlastním klíčem hráče.
 *
 * Offline překlad se ukáže hned jako vždy; DeepL se zeptá souběžně a když odpoví včas a jinak,
 * text v pruhu se potichu vymění. Tady je jen logika bez Androidu a bez sítě: tvar klíče, adresa,
 * jazyky, text požadavku, čtení odpovědí, rozhodnutí o výměně, přestávky po chybách a stránka
 * pro vložení klíče z telefonu. Síť je v Android části (`DeeplTranslator.kt`).
 *
 * Klíč patří jen hráči – v repozitáři žádný není a nikam se nevypisuje (viz [Redact]).
 */
object Deepl {
    const val FREE_HOST = "api-free.deepl.com"
    const val PRO_HOST = "api.deepl.com"
    /**
     * Ceník DeepL na záložce API (plán „Developer“ zdarma, 1 000 000 znaků celkem). Tlačítko u Developeru
     * nepřihlášeného pošle na registraci, přihlášenému (i s účtem jen pro Translator) přidá plán k účtu.
     * Přímý odkaz na registraci (`/signup?…&productId=api-developer`) přihlášeného přesměruje do Translatoru –
     * proto ne (ověřeno na skutečném účtu 21. 11. 2026). Jazyk stránky si DeepL doplní sám.
     */
    const val SIGNUP_URL = "https://www.deepl.com/pro#api"
    /** Stránka s klíči v účtu DeepL – přímo, bez hledání v menu (na mobilu se tam jinak špatně trefuje). */
    const val KEYS_URL = "https://www.deepl.com/your-account/keys"
    /** Odpověď, která přijde později, se už do pruhu nedává (hráč mezitím čte), jen se zapamatuje. */
    const val LATE_MS = 1500L
    /**
     * Jak dlouho se čeká na DeepL, než se místo něj ukáže offline překlad (od 0.97.2 se ukazuje vždy jen
     * jedna varianta, bez výměny v pruhu). DeepL odpovídá obvykle za 0,1–0,6 s.
     */
    const val WAIT_MS = 2500L
    /** Značka, ve které jsou jména a výrazy ze slovníku – DeepL je nepřekládá. */
    const val KEEP_TAG = "k"

    enum class KeyKind { FREE, PRO }

    // klíč DeepL: UUID, u verze Free s koncovkou „:fx“ (pro jistotu bereme i jiný dlouhý tvar)
    private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(:fx)?")
    private val LOOSE = Regex("[A-Za-z0-9][A-Za-z0-9-]{23,79}(:fx)?")

    /** Uklidí vložený klíč: mezery, uvozovky, případná předpona „DeepL-Auth-Key“. */
    fun cleanKey(raw: String?): String {
        var s = (raw ?: "").trim().trim('"', '\'', '`').trim()
        if (s.startsWith("DeepL-Auth-Key", ignoreCase = true)) s = s.substring("DeepL-Auth-Key".length)
        return s.filter { !it.isWhitespace() }
    }

    /** Druh klíče podle tvaru; null = tohle klíč DeepL není. */
    fun keyKind(key: String): KeyKind? {
        if (!UUID.matches(key) && !LOOSE.matches(key)) return null
        return if (key.endsWith(":fx")) KeyKind.FREE else KeyKind.PRO
    }

    /** Free a Pro mají každý svou adresu. */
    fun host(key: String): String = if (key.endsWith(":fx")) FREE_HOST else PRO_HOST

    fun translateUrl(key: String) = "https://${host(key)}/v2/translate"
    fun usageUrl(key: String) = "https://${host(key)}/v2/usage"
    fun authHeader(key: String) = "DeepL-Auth-Key $key"

    /** Klíč na obrazovce: jen začátek a konec. */
    fun masked(key: String): String = if (key.length < 10) "•••" else key.take(4) + "…" + key.takeLast(if (key.endsWith(":fx")) 5 else 2)

    /**
     * Jazyk překladu → kód cílového jazyka DeepL; null = DeepL ho neumí (zůstane jen offline, služba DeepL
     * nevolá a Domů / Režim překladu ukazují jen offline – [cz.recaption.DeeplTranslator.active]).
     * Cílové jazyky podle DeepL API (/v2/languages?type=target): všechny jazyky překladu kromě srbštiny (DeepL ji píše
     * cyrilicí, aplikace má latinku) – ověřeno 24. 11. 2026.
     */
    fun target(lang: Lang): String? = when (lang) {
        Lang.CS -> "CS"
        Lang.SK -> "SK"
        Lang.PL -> "PL"
        Lang.DE -> "DE"
        Lang.HU -> "HU"
        Lang.UK -> "UK"
        Lang.ES -> "ES"
        Lang.FR -> "FR"
        Lang.IT -> "IT"
        Lang.RO -> "RO"
        Lang.SL -> "SL"
        Lang.BG -> "BG"
        Lang.LT -> "LT"
        Lang.LV -> "LV"
        Lang.ET -> "ET"
        Lang.EL -> "EL"
        Lang.TR -> "TR"
        Lang.NL -> "NL"
        Lang.SV -> "SV"
        Lang.DA -> "DA"
        Lang.NB -> "NB"
        Lang.FI -> "FI"
        Lang.ID -> "ID"
        Lang.VI -> "VI"
        Lang.HE -> "HE"
        Lang.TH -> "TH"
        // hindština: ověřeno 24. 11. 2026 na /v2/languages?type=target (HI, bez formality) i zkušebním překladem
        Lang.HI -> "HI"
        // od 24. 11. 2026 (přání majitele) i další jazyky jen pro překlad – DeepL je má mezi cílovými (ověřeno na účtu
        // majitele: /v2/languages?type=target a zkušební překlad)
        Lang.HR -> "HR"
        Lang.MK -> "MK"
        Lang.SQ -> "SQ"
        Lang.CA -> "CA"
        Lang.TL -> "TL"
        Lang.MS -> "MS"
        Lang.IS -> "IS"
        Lang.SW -> "SW"
        Lang.AF -> "AF"
        // srbština: DeepL píše cyrilicí, aplikace má srbštinu latinkou → jen offline
        Lang.EN, Lang.SR -> null
    }

    // ---------------- požadavek ----------------

    /**
     * Text jedné části titulku pro DeepL (XML, viz [requestJson]). Posílá se původní anglický text –
     * DeepL obratům rozumí sám, úpravy pro offline překladač (`IDIOMS`) nepotřebuje.
     * Jména a výrazy ze slovníku ([prepared] z [Glossary.prepare]) se zabalí do značky [KEEP_TAG],
     * aby je DeepL nepřekládal („Dutch“ je jméno, ne „Holanďan“). Když výraz ze slovníku v původním
     * textu doslova není (nahrazuje se překladem), vezme se upravený text se značkami.
     */
    fun source(raw: String, prepared: OfflinePrep.Prepared?): String {
        val names = prepared?.names.orEmpty()
        if (names.isEmpty()) return xmlEsc(raw)
        if (names.all { nameRx(it).containsMatchIn(raw) }) {
            val rx = Regex(names.sortedByDescending { it.length }.joinToString("|") { "(?<![A-Za-z'\\-])" + Regex.escape(it) + "(?![A-Za-z\\-])" })
            val sb = StringBuilder()
            var last = 0
            for (m in rx.findAll(raw)) {
                sb.append(xmlEsc(raw.substring(last, m.range.first))).append(keep(m.value))
                last = m.range.last + 1
            }
            return sb.append(xmlEsc(raw.substring(last))).toString()
        }
        val token = Regex("\\[(\\d{1,2})]")
        val sb = StringBuilder()
        var last = 0
        for (m in token.findAll(prepared!!.protectedText)) {
            sb.append(xmlEsc(prepared.protectedText.substring(last, m.range.first)))
            sb.append(names.getOrNull(m.groupValues[1].toInt() - 1)?.let { keep(it) } ?: xmlEsc(m.value))
            last = m.range.last + 1
        }
        return sb.append(xmlEsc(prepared.protectedText.substring(last))).toString()
    }

    private fun nameRx(n: String) = Regex("(?<![A-Za-z'\\-])" + Regex.escape(n) + "(?![A-Za-z\\-])")
    private fun keep(s: String) = "<$KEEP_TAG>" + xmlEsc(s) + "</$KEEP_TAG>"

    /**
     * Tělo POST /v2/translate. `formality=prefer_less` = tykání tam, kde ho DeepL umí nastavit
     * (němčina, francouzština, italština, španělština, polština); u ostatních jazyků ho DeepL
     * tiše ignoruje (varianta „prefer_“ nikdy neskončí chybou). [source] = jazyk titulků ([SourceLang.deepl]).
     */
    fun requestJson(texts: List<String>, target: String, source: String = "EN"): String =
        "{\"text\":[" + texts.joinToString(",") { jsonStr(it) } + "],\"source_lang\":" + jsonStr(source) + ",\"target_lang\":" + jsonStr(target) +
            ",\"formality\":\"prefer_less\",\"tag_handling\":\"xml\",\"ignore_tags\":[\"$KEEP_TAG\"],\"preserve_formatting\":true}"

    /** Kolik znaků se účtuje (DeepL počítá text bez značek). */
    fun billedChars(texts: List<String>): Int = texts.sumOf { unwrap(it).length }

    // ---------------- odpověď ----------------

    /** Překlady z odpovědi /v2/translate (ve stejném pořadí jako texty); null = odpověď nedává smysl. */
    fun parseTranslations(json: String, expected: Int): List<String>? {
        val o = MiniJson.obj(json) ?: return null
        val list = o["translations"] as? List<*> ?: return null
        if (list.size != expected) return null
        return list.map { item -> ((item as? Map<*, *>)?.get("text") as? String)?.let { unwrap(it) } ?: return null }
    }

    /** Zbavení značek a XML entit; značka [KEEP_TAG] chrání jména, po překladu už není potřeba. */
    fun unwrap(s: String): String =
        s.replace("<$KEEP_TAG>", "").replace("</$KEEP_TAG>", "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    data class Usage(val count: Long, val limit: Long) {
        val left: Long get() = (limit - count).coerceAtLeast(0)
    }

    /** Odpověď /v2/usage: {"character_count":…, "character_limit":…}. */
    fun parseUsage(json: String): Usage? {
        val o = MiniJson.obj(json) ?: return null
        val count = (o["character_count"] as? Number)?.toLong() ?: return null
        val limit = (o["character_limit"] as? Number)?.toLong() ?: return null
        return Usage(count, limit)
    }

    /** Proč DeepL nepomohl. Kód textu pro hráče: `dl_p_<name.lowercase()>`. */
    enum class Problem { BAD_KEY, QUOTA, RATE, SERVER, NETWORK, BAD_REPLY }

    /** Stav HTTP → problém; null = v pořádku (200). */
    fun problemFor(status: Int): Problem? = when (status) {
        200 -> null
        401, 403 -> Problem.BAD_KEY
        456 -> Problem.QUOTA
        429 -> Problem.RATE
        in 500..599 -> Problem.SERVER
        else -> Problem.BAD_REPLY
    }

    // ---------------- výměna textu v pruhu ----------------

    enum class Swap { YES, SAME, LATE, STALE }

    /**
     * Vyměnit offline překlad za překlad DeepL? [current] = v pruhu je pořád týž titulek
     * (žádný novější řádek, žádný nový začátek), [elapsedMs] = jak dlouho DeepL odpovídal.
     */
    fun decide(offline: String, deepl: String, current: Boolean, elapsedMs: Long): Swap = when {
        !current -> Swap.STALE
        elapsedMs > LATE_MS -> Swap.LATE
        !differs(offline, deepl) -> Swap.SAME
        else -> Swap.YES
    }

    /** Liší se překlady opravdu (ne jen velikostí písmen, mezerami nebo interpunkcí)? */
    fun differs(a: String, b: String): Boolean {
        fun core(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        return b.isNotBlank() && core(a) != core(b)
    }

    /** Řádek pro zkoušku a měření (bez textu titulků, bez klíče). */
    fun logLine(ms: Long, swap: String, chars: Int) = "DEEPL|ms=$ms|swap=$swap|chars=$chars"

    // ---------------- JSON a XML ----------------

    fun xmlEsc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun jsonStr(s: String): String = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append(String.format("\\u%04x", c.code))
            else -> append(c)
        }
        append('"')
    }
}

/**
 * Kdy se DeepL zase zkusí po chybě. Hráč o chybách nic neví – překlad jede offline dál a důvod
 * je vidět v Nastavení → Experimentální a v hlášení o stavu.
 *  - špatný klíč: do změny klíče (nemá smysl zkoušet),
 *  - vyčerpaný měsíční limit: 6 hodin,
 *  - moc dotazů: 1 minuta,
 *  - síť nebo chyba serveru: po třech chybách po sobě 2 minuty, pak 4, 8, nejvýš 15.
 */
class DeeplBackoff {
    @Volatile var pausedUntil = 0L
        private set
    @Volatile var keyRejected = false
        private set
    @Volatile var lastProblem: Deepl.Problem? = null
        private set
    private var failures = 0

    @Synchronized fun allowed(now: Long): Boolean = !keyRejected && now >= pausedUntil

    @Synchronized fun onSuccess() { failures = 0; lastProblem = null }

    @Synchronized fun onProblem(p: Deepl.Problem, now: Long) {
        lastProblem = p
        when (p) {
            Deepl.Problem.BAD_KEY -> keyRejected = true
            Deepl.Problem.QUOTA -> pausedUntil = now + 6 * HOUR
            Deepl.Problem.RATE -> pausedUntil = now + MINUTE
            else -> {
                failures++
                if (failures >= 3) pausedUntil = now + minOf(15 * MINUTE, 2 * MINUTE shl minOf(failures - 3, 3))
            }
        }
    }

    /** Za kolik minut se DeepL zkusí znovu (0 = hned, -1 = až s novým klíčem). */
    fun minutesLeft(now: Long): Long = when {
        keyRejected -> -1
        now >= pausedUntil -> 0
        else -> (pausedUntil - now + MINUTE - 1) / MINUTE
    }

    companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}

/** Měření pro majitele: jak rychle DeepL odpovídá a kolik řádků opravdu vylepšil. Jen tento běh. */
class DeeplStats {
    var requests = 0; private set
    var msSum = 0L; private set
    var improved = 0; private set
    var late = 0; private set
    var same = 0; private set
    var stale = 0; private set
    var cached = 0; private set
    var failed = 0; private set
    var chars = 0L; private set

    @Synchronized fun answer(ms: Long, swap: Deepl.Swap, sentChars: Int) {
        requests++; msSum += ms; chars += sentChars
        when (swap) {
            Deepl.Swap.YES -> improved++
            Deepl.Swap.LATE -> late++
            Deepl.Swap.SAME -> same++
            Deepl.Swap.STALE -> stale++
        }
    }

    @Synchronized fun fromCache() { cached++ }
    @Synchronized fun failure(sentChars: Int) { failed++; chars += sentChars }

    val avgMs: Long get() = if (requests == 0) 0 else msSum / requests
}

/** Stránka, kterou televize vystaví po dobu „Vložit klíč z telefonu“ (přes [LoginServer]). */
object DeeplPage {
    enum class State { ASK, BAD_SHAPE, REJECTED, DONE, GONE }

    fun page(lang: Lang, state: State, path: String, detail: String? = null): String {
        fun t(k: String) = PhoneLogin.esc(I18n.t(lang, k))
        val sb = StringBuilder()
        sb.append("<!doctype html><html lang=\"").append(lang.code).append("\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Titulky PS5</title><style>")
            .append("body{margin:0;background:#0e1014;color:#f2f2f2;font:17px/1.45 system-ui,sans-serif}")
            .append("main{max-width:34em;margin:0 auto;padding:18px 16px 44px}h1{font-size:24px;margin:6px 0 4px}")
            .append("p{margin:8px 0}.muted{color:#9aa4ae;font-size:15px}.ok{color:#4ade80;font-size:20px;font-weight:600}")
            .append(".bad{color:#ef4444;font-weight:600}")
            .append("a.btn,button{display:block;width:100%;box-sizing:border-box;text-align:center;background:#f2c14e;color:#1a1405;font:600 18px system-ui,sans-serif;")
            .append("border:0;border-radius:12px;padding:14px;margin:12px 0 4px;text-decoration:none}a.btn{background:#2a303b;color:#f2f2f2}")
            .append(".card{background:#1b1f27;border-radius:14px;padding:14px 16px;margin:10px 0}")
            .append("input{width:100%;box-sizing:border-box;font-size:16px;padding:13px;border-radius:12px;border:2px solid #3a4250;")
            .append("background:#0e1014;color:#f2f2f2;margin-top:6px}</style></head><body><main><h1>Titulky PS5 · DeepL</h1>")
        when (state) {
            State.DONE -> sb.append("<p class=\"ok\">").append(t("dl_w_ok")).append("</p>")
                .append(detail?.let { "<p>" + PhoneLogin.esc(it) + "</p>" } ?: "")
            State.GONE -> sb.append("<p class=\"bad\">").append(t("wp_gone")).append("</p>")
            else -> {
                if (state == State.BAD_SHAPE) sb.append("<p class=\"bad\">").append(t("dl_e_shape")).append("</p>")
                if (state == State.REJECTED) sb.append("<p class=\"bad\">").append(detail?.let { PhoneLogin.esc(it) } ?: t("dl_p_bad_key")).append("</p>")
                // postup ve třech krocích: 1. registrace a 2. stránka s klíči na nové kartě (obyčejný odkaz – funguje
                // i v Safari na iPhonu), 3. vložit klíč ručně (schránku stránka z domácí sítě bez zabezpečení číst nesmí)
                sb.append("<div class=\"card\"><p><b>").append(t("dl_s1")).append("</b></p>")
                    .append("<a class=\"btn\" target=\"_blank\" rel=\"noopener\" href=\"").append(PhoneLogin.esc(Deepl.SIGNUP_URL)).append("\">")
                    .append(t("dl_open")).append("</a>")
                    .append("<p class=\"muted\">").append(t("dl_howto")).append("</p></div>")
                    .append("<div class=\"card\"><p><b>").append(t("dl_s2")).append("</b></p>")
                    .append("<a class=\"btn\" target=\"_blank\" rel=\"noopener\" href=\"").append(PhoneLogin.esc(Deepl.KEYS_URL)).append("\">")
                    .append(t("dl_keys_open")).append("</a>")
                    .append("<p class=\"muted\">").append(t("dl_keys_hint")).append("</p></div>")
                    .append("<p><b>").append(t("dl_s3_here")).append("</b></p>")
                    .append("<form method=\"post\" action=\"").append(PhoneLogin.esc(path)).append("\">")
                    .append("<input name=\"key\" autocomplete=\"off\" autocapitalize=\"off\" autocorrect=\"off\" spellcheck=\"false\" placeholder=\"xxxxxxxx-xxxx-…\">")
                    .append("<button type=\"submit\">").append(t("dl_w_send")).append("</button></form>")
                    .append("<p class=\"muted\">").append(t("dl_privacy")).append("</p>")
            }
        }
        return sb.append("</main></body></html>").toString()
    }
}
