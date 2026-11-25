package cz.recaption

import android.content.Context
import android.content.SharedPreferences
import cz.recaption.core.BuiltinPhrases
import cz.recaption.core.Console
import cz.recaption.core.GameOptions
import cz.recaption.core.GameProfiles
import cz.recaption.core.Glossary
import cz.recaption.core.I18n
import cz.recaption.core.Lang
import cz.recaption.core.License
import cz.recaption.core.PhoneLogin
import cz.recaption.core.ScreenLayout
import cz.recaption.core.SourceLang
import cz.recaption.core.Ps5Host
import cz.recaption.core.TvType
import cz.recaption.core.UsageStats

/** Nastavení aplikace. */
object Prefs {
    /** Stejné hodnoty nabízí i televize (core/TvSettings, app.js) – měň všude najednou. */
    val TEXT_SIZES = intArrayOf(12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48)
    /** Výchozí písmo překladu – od 1.00 14 (přání majitele 24. 11. 2026, dřív 22); platí jen pro toho, kdo velikost nikdy neměnil. */
    const val DEFAULT_TEXT_SIZE = 14
    /** Stejné hodnoty nabízí i televize (core/TvSettings, app.js) – měň všude najednou. */
    val OPACITIES = intArrayOf(0, 20, 40, 60, 80, 90, 100)
    /** Výchozí neprůhlednost pruhu s titulky – od 1.00 60 % (přání majitele 24. 11. 2026, dřív 100 %). */
    const val DEFAULT_OPACITY = 60
    val BAR_WIDTHS = intArrayOf(50, 60, 70, 84, 100)

    /** Barvy textu titulků: bílá, žlutá, světle zelená, světle modrá. */
    val TEXT_COLORS = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFE066.toInt(), 0xFFA8F0A8.toInt(), 0xFF9FD8FF.toInt())
    fun textColorIndex(c: Context): Int = p(c).getInt("text_color", 0).coerceIn(0, TEXT_COLORS.size - 1)
    fun setTextColorIndex(c: Context, v: Int) = p(c).edit().putInt("text_color", v).apply()

    fun boldText(c: Context) = bool(c, "bold_text", false)
    fun setBoldText(c: Context, v: Boolean) = setBool(c, "bold_text", v)

    /**
     * Nastavení, se kterým aplikace pracuje. V telefonu v režimu „Nastavuji TV“ ([RemoteClient]) je to nastavení
     * té TV (`dalkove_tv`, každá změna odejde do TV) – vlastní nastavení telefonu zůstává nedotčené.
     */
    private fun p(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(if (RemoteClient.on(c)) RemoteClient.TV_FILE else "test_preklad", Context.MODE_PRIVATE)

    /** Vlastní nastavení tohoto zařízení i v režimu „Nastavuji TV“ (účty v telefonu, zkušební verze, licence…). */
    private fun own(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("test_preklad", Context.MODE_PRIVATE)

    private fun bool(c: Context, k: String, d: Boolean) = p(c).getBoolean(k, d)
    private fun setBool(c: Context, k: String, v: Boolean) = p(c).edit().putBoolean(k, v).apply()

    /**
     * Jazyk překladu. Dokud ho uživatel nezvolil, bere se jazyk aplikace (je-li to jazyk, do kterého
     * se překládá), pak jazyk systému, umí-li ho ReCaption (od 1.0.10, přání majitele – Němec dostane němčinu,
     * Polák polštinu…), jinak čeština.
     */
    fun lang(c: Context): Lang = p(c).getString("lang", null)?.let { Lang.of(it) }
        ?: Lang.ui(p(c).getString("ui_lang", null))?.takeIf { it.isTarget }
        ?: Lang.system(java.util.Locale.getDefault().language) ?: Lang.CS
    fun setLang(c: Context, v: Lang) {
        if (v.isTarget) p(c).edit().putString("lang", v.code).apply()
        // stejný jazyk titulků jako jazyk překladu nejde – pak se překládá z angličtiny ([SourceLang.effective])
        OfflineTranslator.source = sourceLang(c)
    }

    /**
     * Z čeho se překládá (jazyk titulků ve hře, průvodce krok Jazyky a Titulky). Nový klíč `src_lang`,
     * bez něj angličtina – dosavadní hráči nic nepoznají.
     */
    fun sourceLang(c: Context): SourceLang = SourceLang.effective(p(c).getString("src_lang", null), lang(c))
    /** Jazyk překladu k zobrazení; s jiným jazykem titulků než angličtinou dvojice „Français → Čeština“. */
    fun langPair(c: Context): String = sourceLang(c).let { if (it.english) lang(c).label else it.label + " → " + lang(c).label }
    fun setSourceLang(c: Context, v: SourceLang) {
        p(c).edit().putString("src_lang", v.code).apply()
        OfflineTranslator.source = sourceLang(c)
    }

    /**
     * Jazyk aplikace (nabídky, nápověda). Dokud ho uživatel nezvolil: kdo má zvolený jazyk překladu,
     * má aplikaci v něm (tak to bylo do verze 0.63); nová instalace se řídí jazykem zařízení
     * a pro jazyk, který aplikace neumí, je anglicky.
     */
    fun uiLang(c: Context): Lang = Lang.ui(p(c).getString("ui_lang", null))
        ?: p(c).getString("lang", null)?.let { Lang.of(it) }?.takeIf { it.ui }   // jazyk jen pro překlad texty nemá
        ?: Lang.ui(java.util.Locale.getDefault().language) ?: Lang.EN
    fun setUiLang(c: Context, v: Lang) = p(c).edit().putString("ui_lang", v.code).apply()
    fun uiLangChosen(c: Context) = p(c).contains("ui_lang") || p(c).contains("lang")
    /** Vybral si už uživatel jazyk (nebo se jen bere výchozí)? */
    fun langChosen(c: Context) = p(c).contains("lang")

    fun textSize(c: Context): Int = p(c).getInt("text_size", DEFAULT_TEXT_SIZE)
    fun setTextSize(c: Context, v: Int) = p(c).edit().putInt("text_size", v).apply()
    /**
     * Písmo překladu stejně velké jako anglický originál. Vypnuto = pevná velikost [textSize].
     * Od 1.00 výchozí vypnuto (přání majitele 24. 11. 2026: pevné písmo 14), dřív zapnuto.
     */
    fun matchSize(c: Context) = bool(c, "match_size", false)
    fun setMatchSize(c: Context, v: Boolean) = setBool(c, "match_size", v)

    fun opacity(c: Context): Int = p(c).getInt("opacity", DEFAULT_OPACITY)
    fun setOpacity(c: Context, v: Int) = p(c).edit().putInt("opacity", v).apply()

    fun debug(c: Context) = bool(c, "debug", false)
    fun setDebug(c: Context, v: Boolean) = setBool(c, "debug", v)
    /** Brát i zkušební verze (sestavené z rozpracované větve, ještě neschválené pro všechny). */
    // zkušební kanál jen pro testery (od 1.00-rc7, Tester.kt); dřívější klíč test_channel zůstává uložený, ale už se nečte
    fun testChannel(c: Context) = Tester.active(c)
    fun setTestChannel(c: Context, v: Boolean) = Tester.set(c, v)
    /**
     * Typ televize (průvodce, Nastavení, souhrnný řádek na Domů): Android TV ukazuje pruh samo, LG a Samsung dostávají
     * hotové věty po síti. Kdo ho nezvolil, řídí se starou volbou „Posílat titulky do televize LG“
     * (`tv_send`) – zapnutá = LG.
     */
    fun tvType(c: Context): TvType = TvType.from(p(c).getString("tv_type", null), bool(c, "tv_send", false))
    fun setTvType(c: Context, v: TvType) = p(c).edit().putString("tv_type", v.key).putBoolean("tv_send", v.sends).apply()
    /** Posílat přeložené titulky do televize LG nebo Samsung, kde ReCaption běží jen jako obrazovka (výchozí: ne). */
    fun tvSend(c: Context) = tvType(c).sends
    /** Z televize s danou platformou („webos“, „tizen“) se už ohlásila aplikace ReCaption: „model · verze“, jinak null. */
    fun tvSeen(c: Context, platform: String): String? = p(c).getString("tv_seen_$platform", null)
    fun setTvSeen(c: Context, platform: String, v: String) = p(c).edit().putString("tv_seen_$platform", v).apply()
    /** Číslo verze, která běžela naposledy (0 = první spuštění) – aby šlo po aktualizaci říct, že proběhla. */
    fun seenVersion(c: Context): Int = own(c).getInt("seen_version", 0)
    fun setSeenVersion(c: Context, v: Int) = own(c).edit().putInt("seen_version", v).apply()

    fun onlySubtitles(c: Context) = bool(c, "only_subs", true)
    fun setOnlySubtitles(c: Context, v: Boolean) = setBool(c, "only_subs", v)

    fun showOriginal(c: Context) = bool(c, "show_original", false)
    fun setShowOriginal(c: Context, v: Boolean) = setBool(c, "show_original", v)
    /**
     * Velikost písma anglického originálu jako u překladu (12–48; 0 = automaticky, 62 % písma překladu jako dřív).
     * Od 0.97.50 na přání majitele velikost písma místo procent (0.97.44–0.97.49 klíč `orig_size` v %, už se nečte).
     */
    fun origSp(c: Context): Int = p(c).getInt("orig_sp", 0).let { v -> if (v <= 0) 0 else TEXT_SIZES.minByOrNull { kotlin.math.abs(it - v) } ?: 0 }
    fun setOrigSp(c: Context, v: Int) = p(c).edit().putInt("orig_sp", v).apply()
    /**
     * Texty hry navíc k titulkům (Titulky → Hra): každý má vlastní přepínač (od 0.97.59) a od 0.97.98 si ho každá hra
     * pamatuje zvlášť (klíč `<klíč>@<id hry>`). Hra bez vlastní hodnoty jede podle dosavadního společného klíče (a přechodu
     * ze staré volby `game_texts`), jinak výchozí – viz [GameOptions]. Zápis jen pro vybranou hru; univerzální profil nemá nic.
     * Přepínače se ukazují jen u her, jejichž profil daný text zná.
     */
    private fun optionStore(c: Context) = object : GameOptions.Store {
        val sp = p(c)
        override fun contains(key: String) = sp.contains(key)
        override fun bool(key: String, def: Boolean) = try { sp.getBoolean(key, def) } catch (_: ClassCastException) { def }
        override fun int(key: String, def: Int) = try { sp.getInt(key, def) } catch (_: ClassCastException) { def }
    }
    /** Id profilu, podle kterého aplikace jede (hra, která v seznamu chybí = univerzální). */
    private fun optionGame(c: Context) = GameProfiles.of(gameId(c)).id
    fun gameOption(c: Context, o: GameOptions.Option): Int = GameOptions.value(optionStore(c), o, optionGame(c))
    private fun setGameOption(c: Context, o: GameOptions.Option, v: Int) {
        val id = optionGame(c).takeIf { GameOptions.hasOptions(it) } ?: return
        val k = GameOptions.key(o, id)
        if (o == GameOptions.Option.CORNER) p(c).edit().putInt(k, v.coerceIn(0, 2)).apply() else setBool(c, k, v > 0)
    }
    /** Pokyny pro hráče pod titulky (GTA: „Take your shot“) – menší šedý řádek; výchozí zapnuto (jako dřív). */
    fun gamePrompts(c: Context) = gameOption(c, GameOptions.Option.PROMPTS) > 0
    fun setGamePrompts(c: Context, v: Boolean) = setGameOption(c, GameOptions.Option.PROMPTS, if (v) 1 else 0)
    /** Překlad menu hry (GTA VI: Mapa, Hra, Nastavení…), od 0.97.57 volba – výchozí vypnuto (přání majitele). */
    fun gameMenu(c: Context) = gameOption(c, GameOptions.Option.MENU) > 0
    fun setGameMenu(c: Context, v: Boolean) = setGameOption(c, GameOptions.Option.MENU, if (v) 1 else 0)
    /** Překlad telefonu ve hře do panelu vedle něj (GTA VI, od 0.97.95, výchozí vypnuto). */
    fun phonePanel(c: Context): Boolean = gameOption(c, GameOptions.Option.PHONE) > 0
    fun setPhonePanel(c: Context, v: Boolean) = setGameOption(c, GameOptions.Option.PHONE, if (v) 1 else 0)
    /** Překlad telefonu přímo přes text v telefonu místo panelu vedle (od 0.97.110, výchozí vypnuto = panel vedle). */
    fun phoneOver(c: Context): Boolean = bool(c, "telefon_pres", false)
    fun setPhoneOver(c: Context, v: Boolean) = setBool(c, "telefon_pres", v)
    /**
     * Panel s překladem telefonu na volné straně obrazu – na opačné polovině než telefon ve hře, ať ho nezakrývá (od 1.00,
     * přání majitele, výchozí vypnuto = panel vedle). Vlastní klíč – [phoneOver] má přednost, jeho uložená hodnota se nemění.
     */
    fun phoneFree(c: Context): Boolean = bool(c, "telefon_volna", false)
    fun setPhoneFree(c: Context, v: Boolean) = setBool(c, "telefon_volna", v)
    /** Pokyny u předmětů ve hře (GTA VI „□ OPEN DOOR“, od 0.97.88, výchozí vypnuto; čte se jen bez titulků v pruhu). */
    fun worldPrompts(c: Context): Boolean = gameOption(c, GameOptions.Option.WORLD) > 0
    fun setWorldPrompts(c: Context, v: Boolean) = setGameOption(c, GameOptions.Option.WORLD, if (v) 1 else 0)
    /** Tipy hry vlevo nahoře (GTA VI „Tip: …“, výchozí vypnuto): překlad do okna pod anglickým boxem. */
    fun gameTips(c: Context): Boolean = gameOption(c, GameOptions.Option.TIP) > 0
    fun setGameTips(c: Context, v: Boolean) = setGameOption(c, GameOptions.Option.TIP, if (v) 1 else 0)
    /** Menu vpravo dole (nápovědy tlačítek, GTA VI): 0 vypnuto (výchozí – nové chování), 1 nad položkami, 2 přes položky. */
    fun cornerMenu(c: Context): Int = gameOption(c, GameOptions.Option.CORNER)
    fun setCornerMenu(c: Context, v: Int) = setGameOption(c, GameOptions.Option.CORNER, v)
    /**
     * Úkol pod titulky zapnutý – podle hry (od 0.97.98). Společný `obj_on` (do 0.97.97 zvlášť pro každou TV) je jen
     * výchozí hodnota pro hry bez vlastní; vzhled úkolu (`obj_size`, `obj_pos`, `obj_bg`, `obj_colors`) zůstává podle TV.
     */
    fun objectiveOn(c: Context) = gameOption(c, GameOptions.Option.OBJECTIVE) > 0
    fun setObjectiveOn(c: Context, v: Boolean) = setGameOption(c, GameOptions.Option.OBJECTIVE, if (v) 1 else 0)
    /**
     * Úkol pod titulky (GTA): písmo (0 = podle hry / jako titulky), poloha (-1 = na místě úkolu ve hře, jinak 0–100 % výšky).
     * Od 1.00 výchozí písmo 12, poloha těsně pod úkolem a pozadí 40 % (přání majitele 24. 11. 2026; dřív 0 / -1 / -1).
     */
    const val DEFAULT_OBJ_SIZE = 12
    const val DEFAULT_OBJ_POS = -5
    const val DEFAULT_OBJ_BG = 40
    fun objSize(c: Context): Int = p(c).getInt("obj_size", DEFAULT_OBJ_SIZE)
    fun setObjSize(c: Context, v: Int) = p(c).edit().putInt("obj_size", v).apply()
    /** -1 na místě ve hře, -2 těsně nad úkolem, -3 nad pruhem s titulky, -4 nahoře (od 0.97.50), -5 těsně pod úkolem, jinak střed 0–100 %. */
    fun objPos(c: Context): Int = p(c).getInt("obj_pos", DEFAULT_OBJ_POS).let { if (it < 0) it.coerceIn(-5, -1) else it.coerceIn(0, 100) }
    /** Pozadí úkolu 0–100 % (-1 = jako pruh s titulky; výchozí od 1.00 40 %) a barevná slova úkolu (pokus, výchozí vypnuto), od 0.97.50. */
    fun objBg(c: Context): Int = p(c).getInt("obj_bg", DEFAULT_OBJ_BG).let { v -> if (v < 0) -1 else OPACITIES.minByOrNull { kotlin.math.abs(it - v) } ?: -1 }
    fun setObjBg(c: Context, v: Int) = p(c).edit().putInt("obj_bg", v).apply()
    fun objColors(c: Context) = bool(c, "obj_colors", false)
    fun setObjColors(c: Context, v: Boolean) = setBool(c, "obj_colors", v)
    fun setObjPos(c: Context, v: Int) = p(c).edit().putInt("obj_pos", v).apply()
    /** Vlastní výška pruhu / úkolu: střed v % výšky obrazovky. */
    val HEIGHTS = (0..100 step 5).toList()

    fun coloredSpeaker(c: Context) = bool(c, "colored_speaker", true)
    fun setColoredSpeaker(c: Context, v: Boolean) = setBool(c, "colored_speaker", v)

    /** Jak dlouho nechat překlad na obrazovce po zmizení anglického titulku (ms). */
    val HOLD_TIMES = intArrayOf(0, 1000, 2000)
    fun holdMs(c: Context): Int = p(c).getInt("hold_ms", 1000)
    fun setHoldMs(c: Context, v: Int) = p(c).edit().putInt("hold_ms", v).apply()
    /** „Rychlejší čtení“: čte se jen pás s textem (core/TextBand, od 0.97.47); od 1.00 výchozí zapnuto (přání majitele 24. 11. 2026). */
    fun fastRead(c: Context) = bool(c, "fast_read", true)
    fun setFastRead(c: Context, v: Boolean) = setBool(c, "fast_read", v)
    /** Pokus „Jen nové repliky“ (od 0.97.83, výchozí vypnuto): z bloku replik pod sebou jen ty, které v pruhu ještě nebyly. */
    fun onlyNewLines(c: Context) = bool(c, "jen_nove", false)
    fun setOnlyNewLines(c: Context, v: Boolean) = setBool(c, "jen_nove", v)
    /** Nejkratší doba titulku v pruhu (fronta rychlých replik, od 0.97.46); 0 = vypnuto. Od 1.00 výchozí 1 s (přání majitele 24. 11. 2026). */
    val MIN_SHOW_TIMES = intArrayOf(0, 500, 1000, 1500, 2000, 3000)
    const val DEFAULT_MIN_SHOW_MS = 1000
    fun minShowMs(c: Context): Int = p(c).getInt("min_show_ms", DEFAULT_MIN_SHOW_MS).let { v -> MIN_SHOW_TIMES.minByOrNull { kotlin.math.abs(it - v) } ?: 0 }
    fun setMinShowMs(c: Context, v: Int) = p(c).edit().putInt("min_show_ms", v).apply()

    /**
     * Vypnutí spořiče LG/Samsung - zkušebně (od 1.5.60, přání majitele: nechat testerům zapnout/vypnout jednotlivě,
     * ať podle SPORIC|/TICHO| v hlášeních poznáme, co na které TV pomáhá). Posílá se do TV jen jako nepovinná pole
     * zprávy nastaveni (core/TvSettings.kt) - TV volby uloží a jednou při spojení podle nich spustí/nespustí
     * blokujSporicLuna / video trik (webos/obchod/app/app.js, tizen/app/app.js).
     */
    fun sporicLuna(c: Context) = bool(c, "sporic_luna", true)
    fun setSporicLuna(c: Context, v: Boolean) = setBool(c, "sporic_luna", v)
    /** 0 vypnuto, 1 nehybné plátno, 2 plátno s měnícím se pixelem (LG only, Samsung ho ignoruje). */
    fun sporicVideo(c: Context): Int = p(c).getInt("sporic_video", 0).coerceIn(0, 2)
    fun setSporicVideo(c: Context, v: Int) = p(c).edit().putInt("sporic_video", v.coerceIn(0, 2)).apply()

    /**
     * Výchozí rozložení: pruh s překladem těsně nad anglickým titulkem (od 1.00, přání majitele 24. 11. 2026; dřív přes titulky).
     * Oblast čtení a šířka pruhu beze změny.
     */
    val DEFAULT_LAYOUT: ScreenLayout = ScreenLayout().withPosition(ScreenLayout.Position.ABOVE_TEXT)
    fun layout(c: Context): ScreenLayout = p(c).getString("layout", null)?.let { ScreenLayout.parse(it) } ?: DEFAULT_LAYOUT
    fun setLayout(c: Context, v: ScreenLayout) = p(c).edit().putString("layout", v.serialize()).apply()

    fun stats(c: Context): UsageStats = UsageStats.parse(p(c).getString("stats", null))
    fun setStats(c: Context, v: UsageStats) = p(c).edit().putString("stats", v.serialize()).apply()

    /** Hledat titulky na obrazovce automaticky (bez ručního nastavování oblasti). */
    fun autoZone(c: Context) = bool(c, "auto_zone", true)
    fun setAutoZone(c: Context, v: Boolean) = setBool(c, "auto_zone", v)

    /** Průvodce prvním spuštěním už proběhl. */
    fun wizardDone(c: Context) = bool(c, "wizard_done", false)
    fun setWizardDone(c: Context, v: Boolean) = setBool(c, "wizard_done", v)

    /** Nejdál dosažený krok průvodce prvním spuštěním (0 = žádný; kvůli anonymnímu počítání, `UsageCount`). */
    fun wizardMaxStep(c: Context): Int = p(c).getInt("wizard_max_step", 0).coerceIn(0, 9)
    fun setWizardMaxStep(c: Context, v: Int) = p(c).edit().putInt("wizard_max_step", v.coerceIn(0, 9)).apply()

    // ---- konzole ----
    /** Na které konzoli se hraje (výchozí PlayStation; dosavadní uživatelé to nemají uložené). */
    fun console(c: Context): Console = Console.of(p(c).getString("console", null))
    fun setConsole(c: Context, v: Console) = p(c).edit().putString("console", v.code).apply()

    /** Je vybraná konzole připravená ke spojení (PS5 spárovaný / Xbox přihlášený a vybraný)? */
    fun consoleReady(c: Context): Boolean = when (console(c)) {
        Console.PLAYSTATION -> ps5Host(c) != null
        Console.XBOX -> XboxAccount.ready(c)
    }

    // ---- PlayStation ----
    /** Spárovaná konzole (null = ještě nespárováno). */
    fun ps5Host(c: Context): Ps5Host? = Ps5Host.parse(p(c).getString("ps5_host", null))

    /** Všechny spárované PlayStationy (aktivní první; od 0.97.67 – klíč `ps5_hosts`, starší uložení = jen aktivní). */
    fun ps5Hosts(c: Context): List<Ps5Host> =
        cz.recaption.core.Ps5Hosts.withActive(cz.recaption.core.Ps5Hosts.parse(p(c).getString("ps5_hosts", null)), ps5Host(c))

    /** Zapomene aktivní PS5; když je spárovaná další, stane se aktivní ona. */
    fun forgetPs5Host(c: Context) {
        val cur = ps5Host(c) ?: return
        val rest = cz.recaption.core.Ps5Hosts.remove(ps5Hosts(c), cur)
        p(c).edit().putString("ps5_host", rest.firstOrNull()?.serialize()).putString("ps5_hosts", cz.recaption.core.Ps5Hosts.serialize(rest)).apply()
        Ps5Probe.reset()
    }
    /**
     * Aktivní PS5. Nová / přepnutá / s novou adresou se zároveň uloží do seznamu spárovaných (`ps5_hosts`); null = aktivní
     * zrušit a vyřadit ji ze seznamu (ostatní spárované zůstanou – přepnout na ně jde na stránce Konzole).
     */
    fun setPs5Host(c: Context, v: Ps5Host?) {
        val list = ps5Hosts(c)
        val next = when {
            v != null -> cz.recaption.core.Ps5Hosts.upsert(list, v)
            else -> ps5Host(c)?.let { cz.recaption.core.Ps5Hosts.remove(list, it) } ?: list
        }
        p(c).edit().putString("ps5_host", v?.serialize()).putString("ps5_hosts", cz.recaption.core.Ps5Hosts.serialize(next)).apply()
        Ps5Probe.reset()   // jiná PS5: stav na Domů zjistit znovu
    }

    /** Naposledy zadané údaje pro párování (ať se nemusí psát znovu). */
    fun ps5Ip(c: Context): String = p(c).getString("ps5_ip", "") ?: ""
    fun setPs5Ip(c: Context, v: String) = p(c).edit().putString("ps5_ip", v).apply()
    fun psnId(c: Context): String = p(c).getString("psn_id", "") ?: ""
    fun setPsnId(c: Context, v: String) = p(c).edit().putString("psn_id", v).apply()
    /** Jméno účtu PlayStation (Online ID) k uloženému číslu účtu; prázdné = neznámé (účty uložené před 0.97.28). */
    fun psnName(c: Context): String = p(c).getString("psn_name", "") ?: ""
    /** Uloží ověřený účet (číslo + jméno) najednou; prázdné hodnoty = účet zapomenout. */
    fun setPsnAccount(c: Context, id: String, name: String) =
        p(c).edit().putString("psn_id", id).putString("psn_name", name).apply()

    /** S obrazem 1080p spojení na tomto zařízení nedrželo → automatika bere 720p. */
    fun highResFailed(c: Context) = bool(c, "high_res_failed", false)
    fun setHighResFailed(c: Context, v: Boolean) = setBool(c, "high_res_failed", v)

    /** Přesnost čtení titulků: "auto" (podle zařízení), "std" (720p), "high" (1080p). */
    fun readQuality(c: Context): String = p(c).getString("read_quality", "auto") ?: "auto"
    fun setReadQuality(c: Context, v: String) = p(c).edit().putString("read_quality", v).apply()

    /** Čtyřmístný přihlašovací kód účtu na konzoli (jen pokud ho uživatel na PS5 má zapnutý). */
    fun ps5LoginPin(c: Context): String = p(c).getString("ps5_login_pin", "") ?: ""
    fun setPs5LoginPin(c: Context, v: String) = p(c).edit().putString("ps5_login_pin", v).apply()

    /** Je to televize s vestavěným Androidem (ne samostatný box nebo Chromecast)? */
    fun isTv(c: Context): Boolean = try {
        c.packageManager.hasSystemFeature("android.software.live_tv")
    } catch (_: Exception) { false }

    // ---- ReCaption v telefonu (pomocník pro přihlášení účtem PlayStation) ----
    /** Účty, které si telefon pamatuje: jen číslo účtu a jméno, žádné heslo. */
    fun phoneAccounts(c: Context): List<PhoneLogin.Account> = PhoneLogin.parseAccounts(own(c).getString("phone_accounts", null))
    fun setPhoneAccounts(c: Context, v: List<PhoneLogin.Account>) =
        own(c).edit().putString("phone_accounts", PhoneLogin.serializeAccounts(v)).apply()

    /** Uživatel řekl, že tohle zařízení je televize nebo box, i když se tváří jako telefon. */
    fun forceTv(c: Context) = own(c).getBoolean("force_tv", false)
    fun setForceTv(c: Context, v: Boolean) = own(c).edit().putBoolean("force_tv", v).apply()

    /**
     * Na čem ReCaption běží (průvodce, Nastavení → Zařízení): true = telefon nebo tablet, který jen čte
     * a posílá titulky do televize LG / Samsung; false = Android TV nebo box. Kdo nevolil, odhadne se
     * podle zařízení. Sama volba nic nepřepíná – pomocník v telefonu se dál řídí [forceTv].
     */
    fun readerDevice(c: Context): Boolean =
        if (p(c).contains("reader_device")) bool(c, "reader_device", false) else NetInfo.isHandheld(c)
    fun setReaderDevice(c: Context, v: Boolean) = setBool(c, "reader_device", v)

    // ---- hra a slovník ----
    // `game_texts` (Rozsah čtení do 0.97.57) se už nenastavuje, čte se jen pro přechod – viz [gamePrompts].

    /** Okraj pruhu nad a pod textem 0–3 (násobek velikosti písma, viz [BAR_PADS]); výchozí 1. */
    val BAR_PADS = floatArrayOf(0.04f, 0.16f, 0.32f, 0.5f)
    /** „Zakrýt celou oblast titulků“ v poloze Přes titulky (od 0.97.59, výchozí vypnuto – nové chování). */
    fun coverAll(c: Context) = bool(c, "cover_all", false)
    fun setCoverAll(c: Context, v: Boolean) = setBool(c, "cover_all", v)
    fun barPad(c: Context): Int = p(c).getInt("bar_pad", 1).coerceIn(0, 3)
    fun setBarPad(c: Context, v: Int) = p(c).edit().putInt("bar_pad", v.coerceIn(0, 3)).apply()

    /** Budit PlayStation z klidového režimu při Spustit (zapne ho na spárovaném druhém účtu); výchozí ne (od 0.97.18). */
    /** Od 0.97.41 nový klíč (dřív `ps5_wake`): po aktualizaci je probouzení u všech vypnuté, zapnout jde znovu v Nastavení. */
    /** Telefon jako čtečka: displej při překladu nezhasne, jen se ztlumí (od 0.97.85, výchozí vypnuto) – zhasnutý displej Samsung zpomalil čtení obrazu na ~1 s. */
    /** Titulky PS5: „Překlad z PC“ – TV jen ukazuje titulky z PC (ReCaption pro PC, Čtečka pro TV); výchozí vypnuto. */
    fun pcMode(c: Context): Boolean = own(c).getBoolean("pc_prijem", false)
    fun setPcMode(c: Context, v: Boolean) = own(c).edit().putBoolean("pc_prijem", v).apply()
    /** Poslední adresa PC, které se ozvalo (zkouší se jako první). */
    fun pcAddress(c: Context): String? = own(c).getString("pc_adresa", null)
    fun setPcAddress(c: Context, v: String?) = own(c).edit().putString("pc_adresa", v).apply()
    /** MAC síťové karty PC (z jeho odpovědi na povel) – pro probuzení PC ze spánku (Wake-on-LAN). */
    fun pcMac(c: Context): String? = own(c).getString("pc_mac", null)
    fun setPcMac(c: Context, v: String?) = own(c).edit().putString("pc_mac", v).apply()

    fun screenDim(c: Context): Boolean = bool(c, "displej_tlumeny", false)
    fun setScreenDim(c: Context, v: Boolean) = setBool(c, "displej_tlumeny", v)
    /**
     * TV LG: vývojářský režim prodlužovat u služby LG jednou denně ([LgDevMode.renew]); výchozí ne (přání majitele
     * 23. 11. 2026). Vlastní nastavení zařízení – klíč relace TV má jen toto zařízení ([LgVault]).
     */
    fun lgDevRenew(c: Context): Boolean = own(c).getBoolean("lg_dev_prodluzovat", false)
    fun setLgDevRenew(c: Context, v: Boolean) = own(c).edit().putBoolean("lg_dev_prodluzovat", v).apply()
    /** Kdo aspoň jednou nahrával snímky, má tlačítko „● Snímky“ v upozornění i bez záznamu v síti (od 0.97.87). */
    fun snapButton(c: Context): Boolean = bool(c, "snimky_tlacitko", false)
    fun setSnapButton(c: Context) = setBool(c, "snimky_tlacitko", true)
    /** Řádek Snímky na Domů: Start/Stop záznamu snímků a Odeslat poslední dávku (⚙ → Experimentální, výchozí vypnuto, přání majitele). */
    fun snapHome(c: Context): Boolean = bool(c, "snimky_domu", false)
    fun setSnapHome(c: Context, v: Boolean) = setBool(c, "snimky_domu", v)
    /**
     * Sbírání replik ze hry (⚙ → Čtení a překlad, od 0.97.139): `repliky_souhlas` = čas zapnutí, 0 = vypnuto / odvolaný souhlas.
     * Od 1.00 výchozí zapnuto bez dotazu (přání majitele) – jen pro toho, kdo nikdy nerozhodl: klíč chybí a na dotaz
     * při Spustit („Nesbírat“, `repliky_dotaz`) neodpověděl. Kdo vypnul nebo odmítl, má dál vypnuto.
     * Vlastní nastavení zařízení, které čte titulky (ne nastavení TV v režimu „Nastavuji TV“).
     */
    fun collectLines(c: Context): Boolean = own(c).getLong("repliky_souhlas", -1L).let { if (it < 0L) !linesAsked(c) else it > 0L }
    fun setCollectLines(c: Context, on: Boolean) =
        own(c).edit().putLong("repliky_souhlas", if (on) System.currentTimeMillis() else 0L).apply()
    /** Do 1.00 dotaz na souhlas při Spustit: true = odpověděl (souhlas nebo „Nesbírat“). Jen se čte – bez souhlasu = odmítl. */
    private fun linesAsked(c: Context): Boolean = own(c).getBoolean("repliky_dotaz", false)
    /** Server schránky má pro repliky vlastní meze (`/v1/stav` → `repliky`); bez nich se posílá nejvýš 1× denně. */
    fun linesServer(c: Context): Boolean = own(c).getBoolean("repliky_server", false)
    fun setLinesServer(c: Context, v: Boolean) = own(c).edit().putBoolean("repliky_server", v).apply()
    fun linesSentAt(c: Context): Long = own(c).getLong("repliky_odeslano", 0L)
    fun setLinesSentAt(c: Context, t: Long) = own(c).edit().putLong("repliky_odeslano", t).apply()
    fun ps5Wake(c: Context): Boolean = bool(c, "ps5_wake2", false)
    fun setPs5Wake(c: Context, v: Boolean) = setBool(c, "ps5_wake2", v)
    /** Stav (konzole, překlad, hra) vlevo nahoře přes hru na Android TV – výchozí vypnuto (od 0.97.40), stav je na Domů. */
    /**
     * Stav přes hru (Android TV): 0 vypnuto, 1 dynamicky (výchozí od 0.97.51, přání majitele) (ukáže se při změně a po chvíli zmizí; zůstane jen
     * při potížích se spojením nebo chybějícím jazykovém balíčku), 2 vždy (jako do 0.97.49: oranžový zůstává).
     * Kdo měl do 0.97.49 zapnutý přepínač `stav_pres_hru`, má „vždy“ (uložené nastavení se nemění).
     */
    fun statusMode(c: Context): Int = p(c).getInt("stav_rezim", -1).let { if (it in 0..2) it else if (bool(c, "stav_pres_hru", false)) 2 else 1 }
    fun setStatusMode(c: Context, v: Int) = p(c).edit().putInt("stav_rezim", v.coerceIn(0, 2)).apply()
    fun statusOverlay(c: Context): Boolean = statusMode(c) != 0

    /** Tlačítko ovladače pro rychlou nabídku vzhledu na Android TV ([QuickLook]); 0 = nenastaveno. */
    fun quickKey(c: Context): Int = p(c).getInt("quick_key", 0)
    fun setQuickKey(c: Context, v: Int) = p(c).edit().putInt("quick_key", v).apply()

    fun gameId(c: Context): String = p(c).getString("game", "all") ?: "all"
    fun setGameId(c: Context, v: String) = p(c).edit().putString("game", v).remove("game_guess").apply()
    /** Hra odhadnutá podle titulků (nabídka „Přepnout“ v Nastavení); null = nic. */
    fun gameGuess(c: Context): String? = p(c).getString("game_guess", null)?.takeIf { it != gameId(c) && it != gameGuessNo(c) }
    /** Hráč nabídku odmítl („Nechat“) – stejná hra se už nenabízí. */
    fun gameGuessNo(c: Context): String? = p(c).getString("game_guess_no", null)
    fun dismissGameGuess(c: Context) = p(c).edit().putString("game_guess_no", p(c).getString("game_guess", null)).remove("game_guess").apply()
    fun setGameGuess(c: Context, v: String?) = p(c).edit().apply { if (v == null) remove("game_guess") else putString("game_guess", v) }.apply()

    // Seznam her se od 1.00 stahuje vždy sám (přání majitele); dřívější volba „Seznam her automaticky“ (klíč hry_auto) zůstává uložená, ale už se nečte.
    /** Kdy se naposledy zjišťovala verze seznamu her (ms, System.currentTimeMillis). */
    fun gamesChecked(c: Context): Long = p(c).getLong("hry_kontrola", 0L)
    fun setGamesChecked(c: Context, v: Long) = p(c).edit().putLong("hry_kontrola", v).apply()

    /** Vlastní slovník uživatele pro daný jazyk (anglicky → překlad). */
    fun userDict(c: Context, lang: Lang): LinkedHashMap<String, String> =
        Glossary.parseUser(p(c).getString("dict_${lang.code}", null))
    fun setUserDict(c: Context, lang: Lang, m: Map<String, String>) =
        p(c).edit().putString("dict_${lang.code}", Glossary.serializeUser(m)).apply()

    /** Slovník pro překlad: jména podle hry + předpřipravené hlášky + vlastní slovník. */
    fun glossary(c: Context): Glossary {
        val lang = lang(c)
        val user = userDict(c, lang)
        val sentences = LinkedHashMap(BuiltinPhrases.forLang(lang))
        val game = GameProfiles.of(gameId(c))
        val terms = LinkedHashMap(game.termsFor(lang))     // pojmy hry; vlastní slovník je přepíše
        for ((k, v) in user) if (Glossary.isSentence(k)) sentences[k] = v else terms[k] = v
        return Glossary(game.names, terms, sentences, game.cast, user.keys.filterNot { Glossary.isSentence(it) })
    }

    /** Otisk nastavení slovníku – když se změní, paměť překladů se smaže. */
    fun glossarySignature(c: Context): String =
        gameId(c) + "|" + lang(c).code + "|" + userDict(c, lang(c)).hashCode() + "|" +
            GameProfiles.of(gameId(c)).let { it.names.hashCode() + 31 * it.termsFor(lang(c)).hashCode() } +
            // stažený seznam her (postavy a rod se mohly změnit); s vestavěným podpis beze změny
            GameProfiles.catalog.let { if (it.downloaded) "|hry" + it.version else "" } +
            // jiný jazyk titulků → paměť překladů neplatí (u angličtiny podpis beze změny, paměť hráčům zůstane)
            sourceLang(c).let { if (it.english) "" else "|" + it.code }
    fun savedGlossarySignature(c: Context): String = p(c).getString("gloss_sig", "") ?: ""
    fun setSavedGlossarySignature(c: Context, v: String) = p(c).edit().putString("gloss_sig", v).apply()

    // ---- nastavení Android TV z telefonu (core/RemoteSetup, RemoteHost) ----
    /** Přenášené volby tohoto zařízení (pro telefon, který TV nastavuje). */
    fun remoteValues(c: Context): Map<String, Any?> = cz.recaption.core.RemoteSetup.filter(p(c).all)

    /** Uloží volby poslané z telefonu (jen přenášené; null = volbu smazat). */
    fun applyRemote(c: Context, values: Map<String, Any?>) {
        // starší telefon (před 0.97.69) posílá jen aktivní PS5 – seznam spárovaných pak srovnat tady
        val keepList = "ps5_host" in values && "ps5_hosts" !in values
        val oldActive = if (keepList) ps5Host(c) else null
        val oldList = if (keepList) ps5Hosts(c) else emptyList()
        val e = p(c).edit()
        for ((k, v) in values) {
            if (!cz.recaption.core.RemoteSetup.transfers(k)) continue
            @Suppress("UNCHECKED_CAST")
            when (v) {
                null -> e.remove(k)
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
                is String -> e.putString(k, v)
                is Set<*> -> e.putStringSet(k, v as Set<String>)
            }
        }
        e.commit()
        if (keepList) {
            val active = ps5Host(c)
            val list = if (active != null) cz.recaption.core.Ps5Hosts.upsert(oldList, active)
                else oldActive?.let { cz.recaption.core.Ps5Hosts.remove(oldList, it) } ?: oldList
            p(c).edit().putString("ps5_hosts", cz.recaption.core.Ps5Hosts.serialize(list)).commit()
        }
        OfflineTranslator.source = sourceLang(c)
    }

    /** Klíč stránky nastavení poslaný telefonem, který ReCaption do TV právě nahrál (adb `am start --es …`). */
    fun setRemoteKey(c: Context, k: String) {
        if (PhoneLogin.validKey(k)) c.applicationContext.getSharedPreferences("nastaveni_z_telefonu", Context.MODE_PRIVATE).edit().putString("klic", k).commit()
    }

    /** Stálý klíč stránky nastavení v TV (ve čtverečku; telefon se s ním připojuje i později). */
    fun remoteKey(c: Context): String {
        val sp = c.applicationContext.getSharedPreferences("nastaveni_z_telefonu", Context.MODE_PRIVATE)
        sp.getString("klic", null)?.takeIf { PhoneLogin.validKey(it) }?.let { return it }
        val k = PhoneLogin.newKey() + PhoneLogin.newKey()
        sp.edit().putString("klic", k).apply()
        return k
    }

    // ---- licence ----
    fun unlocked(c: Context) = own(c).getBoolean("unlocked", false)
    fun setUnlocked(c: Context, v: Boolean) = own(c).edit().putBoolean("unlocked", v).apply()

    /** Stav licence; při prvním spuštění si zapamatuje začátek zkušební doby. */
    fun license(c: Context): License.State {
        if (License.FREE) return License.state(unlocked(c), 0L, 0L)   // zatím zdarma: začátek zkušební doby se neukládá
        val now = System.currentTimeMillis()
        val saved = own(c).getLong("first_run", 0L)
        val first = License.firstRun(saved, now)
        if (first != saved && first != 0L) own(c).edit().putLong("first_run", first).apply()
        return License.state(unlocked(c), first, now)
    }
}

/** Zkratka pro texty v jazyce, který si uživatel vybral. */
fun Context.tx(key: String): String = I18n.t(Prefs.uiLang(this), key).let {
    // značka herního účtu 🟢 je až v Androidu 10 – na starších TV a boxech zelené srdce, ať není prázdný čtvereček
    if (android.os.Build.VERSION.SDK_INT < 29) it.replace(cz.recaption.core.AccountMarks.GAME, cz.recaption.core.AccountMarks.GAME_OLD) else it
}
