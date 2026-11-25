package cz.recaption.core

/**
 * Profil hry: jména postav a míst, která se nemají překládat, a postavy s rodem
 * (podle něj se opraví tvary v překladu, viz [CzechGender]).
 */
data class GameProfile(
    val id: String, val title: String, val names: List<String>, val characters: List<GameCharacter> = emptyList(),
    /** Herní pojmy s pevným překladem: kód jazyka → (anglicky → překlad). Vlastní slovník má přednost. */
    val terms: Map<String, Map<String, String>> = emptyMap(),
    /** Hlášky na obrazovce, které nejsou replika (MISSION PASSED, WASTED…) – nečtou se. */
    val hud: List<String> = emptyList(),
    /** Nápisy z menu hry – obrazovka s aspoň dvěma z nich je menu a nečte se. */
    val menu: List<String> = emptyList(),
    /** Řádky, které se nečtou podle tvaru (chat v Diablu IV: „[Trade] …“). */
    val skip: List<Regex> = emptyList(),
    /** Hra píše pod repliku pokyny pro hráče (GTA) – poslední krátký řádek se ukáže zvlášť ([TextLogic.markPrompt]). */
    val prompts: Boolean = false,
    /** Barvy jmen jako v titulcích hry (jméno postavy → barva 0xAARRGGBB); platí i pro její přezdívky. */
    val colors: Map<String, Int> = emptyMap(),
    /** Základní menu hry k překladu (menu zleva, lišta Nastavení, popis volby – [GameMenu]); null = menu se nepřekládá. */
    val menuSpec: MenuSpec? = null,
    /** Menu vpravo dole – nápovědy tlačítek při hraní ([CornerPrompts]); null = hra ho nemá / neznáme. */
    val cornerSpec: CornerSpec? = null,
    /**
     * Rada, jak hru nastavit, aby se titulky četly (od 0.97.58): klíč textu – `<hint>` popisek řádku v Titulky → Hra,
     * `<hint>_val` hodnota, `<hint>_desc` delší vysvětlení (dialog po OK a Nápověda → Hraní). null = žádná rada.
     */
    val hint: String? = null,
    /**
     * Jména z [names], která jsou zároveň běžná anglická slova (od seznamu her ke stažení): v profilu hry zůstávají
     * v originále, ale univerzální profil a odhad hry je vynechají ([GameCatalog.ambiguous]).
     */
    val common: List<String> = emptyList(),
    /**
     * Přibližné polohy telefonu ve hře (od 1.00-rc21, např. v autě a při chůzi): kde ho hledat napřed ([PhoneZone]);
     * prázdné = hledá se v celém širokém pásu jako dřív.
     */
    val phoneSpots: List<PhoneSpot> = emptyList(),
    /**
     * Barvy bublin překladu telefonu (koordinátor 24. 11. 2026: „telefon celý modře, už nikdy zpět na růžovou“): vlastní
     * a příchozí jako 0xRRGGBB, 0 = výchozí modré ([PhoneScreen.OWN_COLOR] / [PhoneScreen.IN_COLOR]). V profilu, protože
     * se ve hře nemění.
     */
    val phoneOwn: Int = 0,
    val phoneIn: Int = 0,
    /** Titulky PS5: vzhled pruhu pod titulky ([SubtitleBox.Look]) – titulek se pozná podle něj; null = hra ho nemá / neznáme. */
    val subtitleLook: SubtitleBox.Look? = null,
) {
    /** Barvy pro [SpeakerColors]: zjednodušené jméno i všechny přezdívky postavy → barva. */
    val colorsByKey: Map<String, Int> by lazy {
        val out = HashMap<String, Int>()
        for ((name, color) in colors) {
            val ch = characters.firstOrNull { it.name == name }
            (ch?.allNames ?: listOf(name)).forEach { out[SpeakerColors.key(it)] = color }
        }
        out
    }

    val cast: Cast by lazy { Cast(characters) }
    val screen: GameScreen by lazy { GameScreen(hud, menu, skip) }
    fun termsFor(lang: Lang): Map<String, String> = terms[lang.code] ?: emptyMap()
}

/** Co hra ukazuje mimo titulky: hlášky (jednotlivě se přeskočí) a nápisy menu (celá obrazovka se přeskočí). */
class GameScreen(hud: List<String>, menu: List<String>, private val skip: List<Regex> = emptyList()) {
    private val hudKeys = hud.map { TextLogic.simplify(it) }.filter { it.isNotEmpty() }.toSet()
    private val menuKeys = menu.map { TextLogic.simplify(it) }.filter { it.isNotEmpty() }.toSet()
    val isEmpty: Boolean get() = hudKeys.isEmpty() && menuKeys.isEmpty() && skip.isEmpty()
    fun isHud(line: String): Boolean = (hudKeys.isNotEmpty() && TextLogic.simplify(line) in hudKeys) || skip.any { it.containsMatchIn(line) }
    fun isMenu(lines: List<String>): Boolean = menuKeys.isNotEmpty() && lines.count { TextLogic.simplify(it) in menuKeys } >= 2

    companion object { val EMPTY = GameScreen(emptyList(), emptyList()) }
}

/**
 * Odhad hry podle jmen v titulcích: jména, která má jen jedna hra (Caminos, Port Gellhorn…).
 * Nic nepřepíná – jen nabídne řádek v Nastavení („Grand Theft Auto VI?“ s Přepnout).
 * Hra platí, když se objevila aspoň [MIN_NAMES] různá její jména a žádná jiná hra jich nemá ani polovinu.
 */
class GameGuess(games: List<GameProfile> = GameProfiles.GAMES, ambiguous: Set<String> = GameProfiles.AMBIGUOUS) {
    private val owner: Map<String, GameProfile>
    private val longest: Int
    init {
        val m = HashMap<String, GameProfile?>()
        for (g in games) for (n in (g.names + g.characters.flatMap { it.allNames }).distinct()) {
            if (n in ambiguous) continue
            val k = TextLogic.simplify(n)
            if (k.length < 4) continue          // krátké přezdívky (Lu, Jay) jsou moc náhodné
            m[k] = if (m.containsKey(k) && m[k]?.id != g.id) null else g
        }
        owner = m.filterValues { it != null }.mapValues { it.value!! }
        longest = owner.keys.maxOfOrNull { it.count { c -> c == ' ' } + 1 } ?: 1
    }
    private val seen = HashMap<String, MutableSet<String>>()

    /** Přečtený titulek (anglicky, i se jménem mluvčího). */
    @Synchronized fun onText(text: String) {
        val words = TextLogic.simplify(text).split(' ').filter { it.isNotEmpty() }
        for (i in words.indices) for (len in 1..longest) {
            if (i + len > words.size) break
            val k = words.subList(i, i + len).joinToString(" ")
            val g = owner[k] ?: continue
            seen.getOrPut(g.id) { HashSet() }.add(k)
        }
    }

    /** Odhadnutá hra, nebo null (zatím málo jmen, nebo se to plete). */
    @Synchronized fun guess(): GameProfile? {
        val ranked = seen.entries.sortedByDescending { it.value.size }
        val best = ranked.firstOrNull() ?: return null
        if (best.value.size < MIN_NAMES) return null
        val second = ranked.getOrNull(1)?.value?.size ?: 0
        if (second * 2 >= best.value.size) return null
        return GameProfiles.of(best.key)
    }

    @Synchronized fun reset() = seen.clear()

    companion object { const val MIN_NAMES = 3 }
}

/**
 * Seznam her: profily a běžná slova, která se do univerzálního profilu nedávají. Vestavěný je v APK
 * ([GameProfiles.BUILTIN]), novější se dá stáhnout ([GameListJson], aplikace `GameListUpdate`).
 * [version] = verze seznamu, [downloaded] = stažený (ne vestavěný).
 */
class GameCatalog(
    val games: List<GameProfile>, extraAmbiguous: Collection<String>, val version: Int, val downloaded: Boolean = false,
    /** Herní slovníky překladu (obraty, hlášky, popisky zvuků…), stahují se se seznamem. */
    val dict: GameDictionaries = GameDictionaries.EMPTY,
) {
    /** Běžná slova mimo profily her (pro zápis seznamu). */
    val extraAmbiguous: List<String> = extraAmbiguous.distinct()
    /** Slova, která jsou zároveň běžná anglická slova – do univerzálního profilu nepatří. */
    val ambiguous: Set<String> = extraAmbiguous.toSet() + games.flatMap { it.common }

    /** Univerzální profil: nezaměnitelná jména ze všech her dohromady (bez běžných slov). */
    val universal = GameProfile("all", "*", games.flatMap { it.names }.filter { it !in ambiguous }.distinct(),
        // rod postav platí i v univerzálním profilu (postava je muž v každé hře); jména s různým rodem vynechá [Cast]
        games.flatMap { it.characters },
        // hlášky a menu všech her (přesná shoda celého řádku – v replice se neobjeví)
        hud = games.flatMap { it.hud }.distinct(), menu = games.flatMap { it.menu }.distinct())

    val all: List<GameProfile> = listOf(universal) + games
    private val byId = all.associateBy { it.id }

    fun of(id: String?): GameProfile = byId[id] ?: universal
}

/**
 * Profily her. Data her (jména, postavy, pojmy, menu…) v aplikaci nejsou – leží v soukromém repu a aplikace si je
 * stáhne ze serveru ([GameListFetch], Android `GameListUpdate`, PC `SeznamHer`). Do stažení jede univerzální profil bez jmen.
 */
object GameProfiles {
    /** Nejnižší versionCode aplikace, která seznam v tomto tvaru umí přečíst (pole `minAppVerze`). */
    const val MIN_APP = 156

    /** Vestavěný seznam = prázdný (verze 0): jakýkoli stažený je novější. */
    val BUILTIN = GameCatalog(emptyList(), emptyList(), 0)

    /** Seznam, podle kterého aplikace právě jede (vestavěný nebo stažený). */
    @Volatile var catalog: GameCatalog = BUILTIN
        private set

    /** Přepne na jiný seznam (stažený); null = zpět na vestavěný. */
    fun use(c: GameCatalog?) { catalog = c ?: BUILTIN }

    val GAMES: List<GameProfile> get() = catalog.games
    val AMBIGUOUS: Set<String> get() = catalog.ambiguous
    val UNIVERSAL: GameProfile get() = catalog.universal
    val ALL: List<GameProfile> get() = catalog.all
    /** Herní slovníky překladu ze staženého seznamu (do stažení prázdné). */
    val dict: GameDictionaries get() = catalog.dict

    fun of(id: String?): GameProfile = catalog.of(id)
    fun next(id: String?): GameProfile = ALL.let { all -> all[(all.indexOf(of(id)) + 1) % all.size] }
}

/**
 * Předpřipravené překlady krátkých herních hlášek, které offline překladač často kazí. Data přicházejí se seznamem her
 * ([GameDictionaries.phrases], soukromé repo); do stažení žádné.
 */
object BuiltinPhrases {
    fun forLang(lang: Lang): Map<String, String> = GameProfiles.dict.phrases[lang.code] ?: emptyMap()
}
