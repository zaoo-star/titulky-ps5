package cz.recaption.core

/**
 * Co se ve hře překládá navíc k titulkům (Titulky → Hra) – každá hra si to pamatuje zvlášť (přání majitele 22. 11. 2026).
 *
 * Volba se čte pro vybranou hru: nejdřív klíč té hry (`<klíč>@<id hry>`, např. `game_menu@gta6`), když neexistuje,
 * tak dosavadní společný klíč se stejnými pravidly jako dřív (u pokynů, úkolu a menu i přechod ze staré volby
 * `game_texts` přes [GameScope]), jinak výchozí hodnota. Aktualizace tak nikomu nic nezmění: kdo měl volbu zapnutou
 * nebo vypnutou, má ji tak u všech her, dokud ji u konkrétní hry nepřepne. Zápis jde jen do klíče hry; společné klíče
 * se už nemění ani nemažou.
 *
 * Univerzální profil (id [UNIVERSAL]) žádnou z voleb nemá – překládají se jen titulky.
 * Bez Androidu, testy `GameOptionsTest`.
 */
object GameOptions {
    /** Volby a jejich dosavadní společné klíče. [CORNER] je číslo 0–2, ostatní zapnuto / vypnuto. */
    enum class Option(val key: String) {
        /** Pokyny pod titulky (výchozí zapnuto). */
        PROMPTS("game_prompts"),
        /** Úkol pod titulky (výchozí zapnuto; vzhled úkolu `obj_size`/`obj_pos`/`obj_bg` dál podle TV). */
        OBJECTIVE("obj_on"),
        /** Překlad menu hry (výchozí vypnuto, u GTA VI zapnuto – [GAME_DEFAULTS]). */
        MENU("game_menu"),
        /** Menu vpravo dole: 0 vypnuto, 1 nad položkami, 2 přes položky (výchozí 0). */
        CORNER("corner_menu"),
        /** Pokyny u předmětů (výchozí vypnuto). */
        WORLD("pokyny_predmety"),
        /** Překlad telefonu ve hře (výchozí vypnuto, u GTA VI zapnuto – [GAME_DEFAULTS]). */
        PHONE("telefon_preklad"),
        /** Tipy hry vlevo nahoře („Tip: …“, výchozí vypnuto, u GTA VI zapnuto – [GAME_DEFAULTS]; dřív společný klíč nebyl). */
        TIP("tipy_hry"),
    }

    /**
     * Volby výchozí zapnuté u konkrétní hry (od 1.00, přání majitele 24. 11. 2026: u GTA VI menu, telefon a tipy).
     * Platí jen pro toho, kdo volbu nikdy nepřepnul – ani u té hry, ani společným klíčem; ostatní hry beze změny.
     * „Pokyny u předmětů“ a „Menu vpravo dole“ (nápovědy k tlačítkům) zůstávají vypnuté – zatím nefungují dost dobře.
     */
    val GAME_DEFAULTS: Map<String, Set<Option>> = mapOf("gta6" to setOf(Option.MENU, Option.PHONE, Option.TIP))

    private fun defaultOn(o: Option, gameId: String?) = GAME_DEFAULTS[gameId]?.contains(o) == true

    /** Id univerzálního profilu – bez voleb. */
    const val UNIVERSAL = "all"
    private const val SEP = "@"

    /** Uložené nastavení (SharedPreferences v Android části, mapa v testech). */
    interface Store {
        fun contains(key: String): Boolean
        fun bool(key: String, def: Boolean): Boolean
        fun int(key: String, def: Int): Int
    }

    /** Klíč volby pro jednu hru. */
    fun key(o: Option, gameId: String): String = o.key + SEP + gameId

    /** Má hra volby? Univerzální profil (a prázdné id) ne. */
    fun hasOptions(gameId: String?): Boolean = !gameId.isNullOrEmpty() && gameId != UNIVERSAL

    /** Je klíč volbou jedné hry (`game_menu@gta6`)? – kvůli přenosu nastavení z telefonu do TV. */
    fun isGameKey(key: String): Boolean {
        val i = key.indexOf(SEP)
        if (i <= 0 || i == key.length - 1) return false
        val base = key.substring(0, i)
        return Option.values().any { it.key == base }
    }

    /** Platná hodnota volby pro hru [gameId] (zapnuto / vypnuto = 1 / 0, u [Option.CORNER] 0–2). */
    fun value(s: Store, o: Option, gameId: String?): Int {
        if (!hasOptions(gameId)) return 0
        val k = key(o, gameId!!)
        if (s.contains(k)) return if (o == Option.CORNER) s.int(k, 0).coerceIn(0, 2) else if (s.bool(k, false)) 1 else 0
        return shared(s, o, gameId)
    }

    fun on(s: Store, o: Option, gameId: String?): Boolean = value(s, o, gameId) > 0

    /**
     * Dosavadní společná hodnota (do 0.97.97 platila pro všechny hry) – stejná pravidla jako dřív; bez uložené hodnoty
     * výchozí, u hry [gameId] podle [GAME_DEFAULTS].
     */
    fun shared(s: Store, o: Option, gameId: String? = null): Int {
        val def = defaultOn(o, gameId)
        val legacy = if (s.contains("game_texts")) s.bool("game_texts", true) else null
        fun b(v: Boolean) = if (v) 1 else 0
        return when (o) {
            Option.PROMPTS -> b(GameScope.resolve(legacy, s.contains("game_prompts"), s.bool("game_prompts", true)))
            Option.MENU -> b(GameScope.resolve(legacy, s.bool("game_menu_volba", false), s.bool("game_menu", def)))
            Option.OBJECTIVE -> b(GameScope.resolve(legacy, s.bool("obj_volba", false), s.bool("obj_on", true)))
            Option.CORNER -> s.int("corner_menu", 0).coerceIn(0, 2)
            Option.WORLD -> b(s.bool("pokyny_predmety", false))
            Option.PHONE -> b(s.bool("telefon_preklad", def))
            Option.TIP -> b(s.bool("tipy_hry", def))
        }
    }
}
