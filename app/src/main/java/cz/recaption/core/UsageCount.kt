package cz.recaption.core

/**
 * Anonymní počítání uživatelů (vždy během překladu, bez volby – přání majitele; co se posílá, říká Nápověda, kapitola voleb).
 * Během překladu aplikace nejvýš jednou za [INTERVAL_MS] pošle na server schránky (stejná adresa jako „Poslat vývojáři“,
 * [ReportUpload.config]) malý ping: náhodné číslo zařízení, číslo verze a typ zařízení. Žádné jméno, účet, adresa ani
 * text ze hry. Server z toho počítá, kolik zařízení ReCaption používá (teď / dnes / 7 / 30 dní).
 * Tady je všechno bez Androidu (testy `UsageCountTest`); odesílání je `UsagePing`.
 *
 * Smlouva se serverem: `POST <url>/v1/ping`, `Content-Type: application/json`, tělo
 * `{"id":"<16–32 znaků A-Za-z0-9>","v":<versionCode>,"typ":"tv|telefon|pc|mac","predplatne":false}` → 204 / 200;
 * 400 = neplatné tělo. Odpověď se nečte.
 *
 * **Od 1.00-rc8 nepovinná pole** (jen když je hodnota známá – chybějící klíč, ne `null`; server je přísně
 * validuje, neznámý klíč navíc = odmítne): `"tv":"atv"/"lg"/"samsung"` (cílová televize, `Prefs.tvType`),
 * `"dl":bool` (DeepL skutečně aktivní, `DeeplTranslator.active`), `"jaz":"xx"` (jazyk překladu, `Lang.code`),
 * `"hra":bool` (vybraný konkrétní profil hry, ne univerzální/žádný), `"krok":0–9` (nejdál dosažený krok
 * průvodce, `Prefs.wizardMaxStep`), `"fr":bool` (Rychlejší čtení, `Prefs.fastRead`), `"adv":bool` (aspoň
 * jedna pokročilejší volba hry – menu, roh, předměty nebo telefon). `"adv"` je vedlejší metrika (časem
 * se možná zruší), stačí rozumná aproximace.
 *
 * **Od 1.00-rc9:** `"akt":"o"` = jen otevření aplikace (nejvýš 1× za [OPEN_INTERVAL_MS], i bez překladu – kolik lidí to po stažení
 * vzdalo a na kterém kroku průvodce), server ho ukládá zvlášť a do uživatelů nepočítá; `"nast":["text_size",…]` = názvy voleb,
 * které hráč někdy změnil (jen názvy klíčů nastavení, žádné hodnoty – [settingKeys]), nejvýš [SETTINGS_MAX].
 */
object UsageCount {
    const val PATH = "/v1/ping"
    /** Nejvýš jeden ping za tuto dobu (i po chybě – neopakuje se hned). */
    const val INTERVAL_MS = 10 * 60_000L
    const val TIMEOUT_MS = 4000
    const val ID_LENGTH = 24

    const val TYPE_TV = "tv"
    const val TYPE_PHONE = "telefon"
    const val TYPE_PC = "pc"
    const val TYPE_MAC = "mac"
    val TYPES = setOf(TYPE_TV, TYPE_PHONE, TYPE_PC, TYPE_MAC)

    const val TARGET_ATV = "atv"
    const val TARGET_LG = "lg"
    const val TARGET_SAMSUNG = "samsung"
    val TV_TARGETS = setOf(TARGET_ATV, TARGET_LG, TARGET_SAMSUNG)

    const val WIZARD_STEP_MAX = 9
    /** Ping „otevřeno“ nejvýš jednou za 6 h (i přes nová spuštění – čas se pamatuje). */
    const val OPEN_INTERVAL_MS = 6 * 3600_000L
    const val SETTINGS_MAX = 40
    private val SETTING_KEY = Regex("[a-z0-9_]{1,24}")
    /**
     * Uložené klíče, které nejsou volbou hráče (interní stav, účty, adresy, statistiky) – do „nast“ nepatří.
     * Klíče s „@hra“ se berou bez přípony (volby textů hry po hrách).
     */
    private val NOT_SETTINGS = setOf(
        "first_run", "seen_version", "stats", "gloss_sig", "hry_kontrola", "wizard_max_step", "klic", "unlocked",
        "ps5_host", "ps5_hosts", "ps5_ip", "ps5_login_pin", "psn_id", "psn_name", "phone_accounts", "game_guess", "game_guess_no",
        "repliky_dotaz", "repliky_odeslano", "repliky_server", "layout", "test_channel", "glossary", "tv_seen", "upd_ohlaseno",
    )
    private val NOT_SETTINGS_PREFIX = listOf("ps5", "psn", "lg_", "deepl_k", "xbox_", "ui_jednou", "obj_volba", "game_menu_volba")

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val ID = Regex("[A-Za-z0-9]{16,32}")
    private val LANG_CODE = Regex("[a-z]{2}")

    /** Nové náhodné číslo zařízení; [random] vrací náhodné číslo 0 ≤ n < meze (v aplikaci SecureRandom). */
    fun newId(random: (Int) -> Int): String = buildString { repeat(ID_LENGTH) { append(ALPHABET[random(ALPHABET.length)]) } }

    fun validId(id: String?): Boolean = id != null && ID.matches(id)

    /** Adresa pingu ze serveru schránky (`https://….workers.dev`). */
    fun url(server: String): String = server.trimEnd('/') + PATH

    /**
     * Tělo pingu (JSON); [type] mimo [TYPES] nebo neplatné id = chyba programu. Nepovinné parametry
     * (`null` = nepřidávat do JSON) jsou stejně přísně ověřené jako server – chyba v logice appky ať
     * spadne na `require`, ne ať pošle neplatný ping.
     */
    fun body(
        id: String,
        versionCode: Int,
        type: String,
        subscription: Boolean = false,
        tvTarget: String? = null,
        deeplActive: Boolean? = null,
        lang: String? = null,
        hasGame: Boolean? = null,
        wizardStep: Int? = null,
        fastRead: Boolean? = null,
        advanced: Boolean? = null,
        open: Boolean = false,
        settings: List<String>? = null,
    ): String {
        require(validId(id)) { "id" }
        require(type in TYPES) { "typ" }
        require(versionCode >= 0) { "v" }
        if (tvTarget != null) require(tvTarget in TV_TARGETS) { "tv" }
        if (lang != null) require(LANG_CODE.matches(lang)) { "jaz" }
        if (wizardStep != null) require(wizardStep in 0..WIZARD_STEP_MAX) { "krok" }
        return buildString {
            append("""{"id":"$id","v":$versionCode,"typ":"$type","predplatne":$subscription""")
            tvTarget?.let { append(""","tv":"$it"""") }
            deeplActive?.let { append(""","dl":$it""") }
            lang?.let { append(""","jaz":"$it"""") }
            hasGame?.let { append(""","hra":$it""") }
            wizardStep?.let { append(""","krok":$it""") }
            fastRead?.let { append(""","fr":$it""") }
            advanced?.let { append(""","adv":$it""") }
            if (open) append(""","akt":"o"""")
            settings?.let { l ->
                require(l.size <= SETTINGS_MAX && l.all { SETTING_KEY.matches(it) }) { "nast" }
                append(""","nast":[""" + l.joinToString(",") { "\"$it\"" } + "]")
            }
            append("}")
        }
    }

    /**
     * Poslat teď? [lastTry] = čas posledního pokusu (0 = ještě nebyl, i po chybě se čeká celý interval).
     * Čas jdoucí pozpátku (změna hodin) = poslat.
     */
    /** Názvy voleb ze všech uložených klíčů nastavení (bez interních, bez přípony „@hra“, nejvýš [SETTINGS_MAX], seřazené). */
    fun settingKeys(saved: Collection<String>): List<String> = saved.asSequence()
        .map { it.substringBefore('@') }
        .filter { SETTING_KEY.matches(it) && it !in NOT_SETTINGS && NOT_SETTINGS_PREFIX.none { p -> it.startsWith(p) } }
        .distinct().sorted().take(SETTINGS_MAX).toList()

    /** Poslat „otevřeno“? [last] = čas (System.currentTimeMillis) posledního, 0 = ještě nebyl. */
    fun openDue(last: Long, now: Long): Boolean = last <= 0L || now < last || now - last >= OPEN_INTERVAL_MS

    fun due(lastTry: Long, now: Long): Boolean = lastTry <= 0L || now < lastTry || now - lastTry >= INTERVAL_MS
}
