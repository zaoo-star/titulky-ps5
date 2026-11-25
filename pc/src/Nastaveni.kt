package cz.recaption.pc

import cz.recaption.core.GameScope
import java.awt.Color
import java.io.File
import java.util.Properties

/** Nastavení aplikace pro počítač – soubor `aplikace.properties` ve složce nástrojů. */
class Nastaveni(private val soubor: File = File(Nastroje.slozka, "aplikace.properties")) {
    private val p = Properties().apply {
        runCatching { soubor.inputStream().use { load(it.reader(Charsets.UTF_8)) } }
    }

    private fun uloz() {
        runCatching {
            soubor.parentFile?.mkdirs()
            soubor.outputStream().use { p.store(it.writer(Charsets.UTF_8), "Titulky PS5 pro počítač") }
        }
    }

    private fun text(k: String, d: String) = p.getProperty(k) ?: d
    private fun cislo(k: String, d: Int) = p.getProperty(k)?.toIntOrNull() ?: d
    private fun nastav(k: String, v: Any) { p.setProperty(k, v.toString()); uloz() }

    /** Část titulku okna s hrou. */
    var okno: String get() = text("okno", "chiaki"); set(v) = nastav("okno", v)
    /**
     * Odkud je obraz hry: chiaki (ReCaption samo spustí hru v chiaki-ng), okno (hra běží v jiném
     * programu, čte se okno podle [okno]), ukazka (ukázková hra bez PlayStationu).
     */
    var zdroj: String
        get() = p.getProperty("zdroj") ?: if (text("ukazka", "false") == "true") "ukazka" else "chiaki"
        set(v) = nastav("zdroj", v)
    val ukazka: Boolean get() = zdroj == "ukazka"
    /** Jméno spárované konzole v chiaki-ng (prázdné = první). */
    var konzole: String get() = text("konzole", ""); set(v) = nastav("konzole", v)
    /**
     * Použití: pc = hraje se na PC v chiaki-ng herním účtem (dosavadní, výchozí), ctecka = čtečka pro TV
     * LG / Samsung (hra běží na PS5 v TV, PC se připojí účtem pro ReCaption a posílá překlad do TV).
     */
    var pouziti: String get() = text("pouziti", VYCHOZI_POUZITI).let { v -> if (POUZITI.any { it.first == v }) v else VYCHOZI_POUZITI }; set(v) = nastav("pouziti", v)
    val ctecka: Boolean get() = pouziti == "ctecka"
    /** Konzole čtečky pro TV (spárovaná zvlášť v profilu chiaki-ng [Chiaki.PROFIL_CTECKY]); prázdné = první. */
    var konzoleCtecka: String get() = text("konzoleCtecka", ""); set(v) = nastav("konzoleCtecka", v)
    /** Účet pro ReCaption (druhý uživatel na PS5) pro čtečku pro TV – jen číslo a jméno, jako [ucetId]. */
    var ucetCteckaId: String get() = text("ucetCteckaId", ""); set(v) = nastav("ucetCteckaId", v)
    var ucetCteckaJmeno: String get() = text("ucetCteckaJmeno", ""); set(v) = nastav("ucetCteckaJmeno", v)
    /** Kterým účtem byla konzole spárovaná ([rezim] pc / ctecka) – pozná se tak spárování herním účtem. */
    fun ucetParovani(rezim: String, konzole: String): String = text("parovaniUcet.$rezim.$konzole", "")
    fun ucetParovani(rezim: String, konzole: String, ucet: String) = nastav("parovaniUcet.$rezim.$konzole", ucet)
    /** Poslední ohlášená TV („LG OLED55B7“) – na Domů i před spuštěním. */
    var tvNazev: String get() = text("tvNazev", ""); set(v) = nastav("tvNazev", v)
    // výchozí jazyk překladu = jazyk Windows / macOS, umí-li ho ReCaption (od 1.0.10, přání majitele), jinak čeština
    var jazyk: String get() = text("jazyk", cz.recaption.core.Lang.system(java.util.Locale.getDefault().language)?.code ?: "cs"); set(v) = nastav("jazyk", v)
    /** pc | tv | oboje */
    var vystup: String get() = text("vystup", "pc"); set(v) = nastav("vystup", v)
    /** Překlad přes DeepL (klíč je zvlášť a zašifrovaný, viz DeeplPc). */
    var deepl: Boolean get() = text("deepl", "false") == "true"; set(v) = nastav("deepl", v)
    /** DeepL jen pro telefon ve hře (titulky offline) – šetří bezplatný limit znaků DeepL. */
    var deeplJenTelefon: Boolean get() = text("deeplTelefon", "false") == "true"; set(v) = nastav("deeplTelefon", v)
    /** Záznam hry pro ladění (snímky + co se četlo) do `zaznamy\pc-<čas>\`. */
    var zaznamHry: Boolean get() = text("zaznamHry", "false") == "true"; set(v) = nastav("zaznamHry", v)
    /** Nezhasínat počítač při překladu (počítač neusne, displej nezhasne) – výchozí vypnuto, viz [Nezhasinat]. */
    var nezhasinat: Boolean get() = text("nezhasinat", "false") == "true"; set(v) = nastav("nezhasinat", v)
    /** Titulky PS5: povel z TV („Překlad z PC“) přepne na Čtečku pro TV a spustí / zastaví čtení; výchozí zapnuto. */
    var zTv: Boolean get() = text("z_tv", "true") == "true"; set(v) = nastav("z_tv", v)
    /** Účet PlayStation pro spárování (zakódované číslo účtu a jméno; heslo se nikdy neukládá). */
    var ucetId: String get() = text("ucetId", ""); set(v) = nastav("ucetId", v)
    var ucetJmeno: String get() = text("ucetJmeno", ""); set(v) = nastav("ucetJmeno", v)

    /** Velikost písma v pruhu jako na Androidu (12–48, jednotky jako sp na TV); 0 = podle titulků ve hře. */
    var pismo: Int get() = cislo("pismo", 0).let { if (it == 0 || it in PISMA) it else 0 }; set(v) = nastav("pismo", v)
    /** Neprůhlednost pozadí pruhu v % (0 = jen písmo se stínem). */
    var pozadi: Int get() = cislo("pozadi", 90).let { v -> POZADI.minByOrNull { Math.abs(it - v) } ?: 90 }; set(v) = nastav("pozadi", v)
    var barva: Int get() = cislo("barva", 0).coerceIn(0, BARVY.size - 1); set(v) = nastav("barva", v)
    var tucne: Boolean get() = text("tucne", "true") == "true"; set(v) = nastav("tucne", v)
    var poloha: Poloha
        get() = runCatching { Poloha.valueOf(text("poloha", "PRES")) }.getOrDefault(Poloha.PRES)
        set(v) = nastav("poloha", v.name)
    /** Okraj pruhu nad a pod textem 0–3 (jako na Androidu). */
    var okraj: Int get() = cislo("okraj", 1).coerceIn(0, 3); set(v) = nastav("okraj", v)
    /** Anglický originál pod překladem. */
    var original: Boolean get() = text("original", "false") == "true"; set(v) = nastav("original", v)

    /** Profil hry (`core/GameProfiles.kt`); „all“ = univerzální. */
    var hra: String get() = text("hra", "all"); set(v) = nastav("hra", v)
    /**
     * Texty hry navíc k titulkům – každý má vlastní přepínač jako na Androidu od 0.97.59 (`Prefs.gamePrompts` /
     * `gameMenu` / `objectiveOn`, [cz.recaption.core.GameScope]). Dřív jedna volba „Rozsah čtení“ (klíč `textyHry`,
     * už se nenastavuje ani nemaže): kdo měl „Jen titulky“, má přepínače vypnuté, dokud některý sám nepřepne
     * (značky: uložené `pokyny` / `ukolVolba` / `menuVolba`).
     */
    private val stareTexty: Boolean? get() = p.getProperty("textyHry")?.let { it == "true" }
    /** Pokyny pro hráče pod titulky (GTA: „Take your shot“) – výchozí zapnuto. */
    var pokyny: Boolean
        get() = GameScope.resolve(stareTexty, p.containsKey("pokyny"), text("pokyny", "true") == "true")
        set(v) = nastav("pokyny", v)
    /** Úkol pod titulky (GTA) ve vlastním pruhu – výchozí zapnuto. */
    var ukol: Boolean
        get() = GameScope.resolve(stareTexty, text("ukolVolba", "false") == "true", text("ukol", "true") == "true")
        set(v) { p.setProperty("ukolVolba", "true"); nastav("ukol", v) }
    /** Překlad menu hry (GTA VI) – výchozí vypnuto. */
    var menuHry: Boolean
        get() = GameScope.resolve(stareTexty, text("menuVolba", "false") == "true", text("menuHry", "false") == "true")
        set(v) { p.setProperty("menuVolba", "true"); nastav("menuHry", v) }

    fun vzhled() = Vzhled(pismo, pozadi, Color(BARVY[barva].second), tucne, poloha, okraj, original, pokyny)

    companion object {
        /** Mac je hlavně čtečka pro TV (hra běží na PS5 v TV) – tam je výchozí; ve Windows zůstává hraní na PC. */
        val VYCHOZI_POUZITI = if (Os.mac) "ctecka" else "pc"
        /** Stejné hodnoty jako na Androidu a v TV (`TvSettings.TEXT_SIZES`, `TvSettings.OPACITIES`). */
        val PISMA = cz.recaption.core.TvSettings.TEXT_SIZES
        val POZADI = cz.recaption.core.TvSettings.OPACITIES
        /** Stejné barvy jako na Android TV (Prefs.TEXT_COLORS). */
        val BARVY = listOf("Bílá" to 0xFFFFFF, "Žlutá" to 0xFFE066, "Zelená" to 0xA8F0A8, "Modrá" to 0x9FD8FF)
        val ZDROJE = listOf("chiaki" to "chiaki-ng v Titulkách PS5", "okno" to "Jiný program", "ukazka" to "Ukázková hra")
        val VYSTUPY = listOf("pc" to Os.text("Pruh na PC"), "tv" to "Jen TV", "oboje" to Os.text("PC i TV"))
        val POUZITI = listOf("pc" to Os.text("Hraji na PC"), "ctecka" to "Čtečka pro TV (hra na PS5 v TV)")
    }
}
