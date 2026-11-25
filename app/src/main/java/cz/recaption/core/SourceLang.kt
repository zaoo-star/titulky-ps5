package cz.recaption.core

/**
 * Jazyk titulků ve hře – z čeho se překládá (volba „Překládat z“, výchozí angličtina).
 *
 * Nabízí se jen to, co celá cesta opravdu zvládne:
 *  - čtení obrazu (ML Kit) umí jen latinku → žádná azbuka, řečtina ani asijská písma,
 *  - offline překladač Google ML Kit jazyk zná ([code] = jeho kód),
 *  - DeepL ho zná jako zdrojový jazyk ([deepl]).
 *
 * Úpravy pro angličtinu (slang, ustálené obraty, rychlá slovíčka, tykání podle anglického originálu,
 * rod podle she/her) běží jen u angličtiny ([english]) – u jiného jazyka by spíš škodily.
 */
enum class SourceLang(val code: String, val label: String, val deepl: String) {
    EN("en", "English", "EN"),
    DE("de", "Deutsch", "DE"),
    FR("fr", "Français", "FR"),
    ES("es", "Español", "ES"),
    IT("it", "Italiano", "IT"),
    PT("pt", "Português", "PT"),
    NL("nl", "Nederlands", "NL"),
    PL("pl", "Polski", "PL"),
    CS("cs", "Čeština", "CS"),
    SK("sk", "Slovenčina", "SK"),
    HU("hu", "Magyar", "HU"),
    SV("sv", "Svenska", "SV"),
    DA("da", "Dansk", "DA"),
    FI("fi", "Suomi", "FI"),
    RO("ro", "Română", "RO"),
    TR("tr", "Türkçe", "TR");

    /** Angličtina = běží i úpravy, které čtou anglický originál. */
    val english: Boolean get() = this == EN

    companion object {
        /** Jazyk podle kódu; neznámý (i nezvolený) = angličtina, jak to bylo vždycky. */
        fun of(code: String?): SourceLang = values().firstOrNull { it.code == code } ?: EN

        /** Co nabídnout při jazyku překladu [target]: všechno kromě něj samotného (do stejného jazyka se nepřekládá). */
        fun offered(target: Lang): List<SourceLang> = values().filter { it.code != target.code && it.code != target.mlkit }

        /**
         * Jazyk, ze kterého se opravdu překládá: uložený, a když je stejný jako jazyk překladu
         * (hráč mezitím změnil jazyk překladu), angličtina.
         */
        fun effective(code: String?, target: Lang): SourceLang = of(code).takeIf { it.code != target.code && it.code != target.mlkit } ?: EN
    }
}
