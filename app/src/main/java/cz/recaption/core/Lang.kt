package cz.recaption.core

/**
 * Jazyk. Slouží dvěma nastavením, která jsou na sobě nezávislá:
 *  - **jazyk překladu** – do čeho se titulky překládají ([targets]),
 *  - **jazyk aplikace** – v čem jsou nabídky a nápověda ([uiLangs], jedenáct jazyků včetně angličtiny).
 * Angličtina je jen jazyk aplikace: titulky jsou anglicky, takže do ní se nepřekládá.
 *
 * **Jen jazyky překladu** ([ui] = false, za angličtinou): aplikace pro ně texty nemá, takže nejsou
 * v nabídce jazyka aplikace, [ui] je nevrátí a [I18n.t] pro ně dává angličtinu. Nový takový jazyk
 * patří jen sem (s `ui = false`) – testy textů (`AllLanguagesTest` …) procházejí jen [uiLangs].
 * [mlkit] = kód jazyka offline překladače Google ML Kit; srbština (latinkou) ML Kit nemá, offline
 * proto jede přes chorvatský model. Zda jazyk umí DeepL, říká [Deepl.target].
 */
enum class Lang(val code: String, val label: String, val ui: Boolean = true, val mlkit: String = code) {
    CS("cs", "Čeština"),
    SK("sk", "Slovenčina"),
    PL("pl", "Polski"),
    DE("de", "Deutsch"),
    HU("hu", "Magyar"),
    UK("uk", "Українська"),
    ES("es", "Español"),
    FR("fr", "Français"),
    IT("it", "Italiano"),
    // hindština (od 24. 11. 2026, přání majitele): plný jazyk aplikace i překladu; písmo dévanágarí, věta končí „।“
    HI("hi", "हिन्दी"),
    EN("en", "English"),
    // jen jazyky překladu (bez textů aplikace)
    RO("ro", "Română", ui = false),
    HR("hr", "Hrvatski", ui = false),
    SR("sr", "Srpski (latinica)", ui = false, mlkit = "hr"),
    SL("sl", "Slovenščina", ui = false),
    BG("bg", "Български", ui = false),
    MK("mk", "Македонски", ui = false),
    SQ("sq", "Shqip", ui = false),
    LT("lt", "Lietuvių", ui = false),
    LV("lv", "Latviešu", ui = false),
    ET("et", "Eesti", ui = false),
    EL("el", "Ελληνικά", ui = false),
    TR("tr", "Türkçe", ui = false),
    // vlna 1 (přání majitele 23. 11. 2026): ve hrách obvykle bez překladu, umí je ML Kit i DeepL
    NL("nl", "Nederlands", ui = false),
    SV("sv", "Svenska", ui = false),
    DA("da", "Dansk", ui = false),
    NB("nb", "Norsk (bokmål)", ui = false, mlkit = "no"),
    FI("fi", "Suomi", ui = false),
    ID("id", "Bahasa Indonesia", ui = false),
    VI("vi", "Tiếng Việt", ui = false),
    // vlna 2: vlastní písmo – hebrejština se píše zprava doleva, thajština bez tečky na konci věty
    HE("he", "עברית", ui = false),
    TH("th", "ไทย", ui = false),
    // vlna 3: jen offline (DeepL je neumí), latinkou
    CA("ca", "Català", ui = false),
    TL("tl", "Filipino", ui = false),
    MS("ms", "Bahasa Melayu", ui = false),
    IS("is", "Íslenska", ui = false),
    SW("sw", "Kiswahili", ui = false),
    AF("af", "Afrikaans", ui = false);

    /** Dá se do tohoto jazyka překládat? */
    val isTarget: Boolean get() = this != EN

    /** Další jazyk aplikace v pořadí (přepínání jedním tlačítkem); jen [uiLangs]. */
    fun next(): Lang {
        val all = uiLangs
        return all[(all.indexOf(this) + 1) % all.size]
    }

    companion object {
        /** Jazyky, do kterých se překládá (dosavadní devět, pak jazyky jen pro překlad). */
        val targets: List<Lang> = values().filter { it.isTarget }

        /** Jazyky aplikace (mají texty). */
        val uiLangs: List<Lang> = values().filter { it.ui }

        /**
         * Jazyky překladu s pevnými překlady (krátké repliky `QuickWords`, hlášky `BuiltinPhrases`,
         * popisky zvuků `Captions.word`) – `FixedTranslationsTest` u nich hlídá stejné klíče jako čeština.
         * Ostatní jazyky překladu pevné překlady zatím nemají (jde jen překladač). Jazyk sem přidat
         * až s daty ve všech třech tabulkách.
         */
        val withFixed: List<Lang> = listOf(CS, SK, PL, DE, HU, UK, ES, FR, IT, RO, HR, SR, SL, BG, MK, SQ, LT, LV, ET, EL, TR, NL, SV, DA, NB, FI, ID, VI, HE, TH, CA, TL, MS, IS, SW, AF, HI)

        /** Jazyk překladu podle kódu; neznámý kód (i angličtina) = čeština. */
        fun of(code: String?): Lang = targets.firstOrNull { it.code == code } ?: CS

        /** Jazyk systému → jazyk překladu, umí-li ho ReCaption (null = ne, i angličtina). Norština nb/no, hebrejština iw/he, indonéština in/id. */
        fun system(code: String?): Lang? {
            val k = when (code?.lowercase()) { "no", "nn" -> "nb"; "iw" -> "he"; "in" -> "id"; "fil" -> "tl"; else -> code?.lowercase() }
            return targets.firstOrNull { it.code == k }
        }

        /** Jazyk aplikace podle kódu; null, když pro něj aplikace texty nemá. */
        fun ui(code: String?): Lang? = uiLangs.firstOrNull { it.code == code }
    }
}
