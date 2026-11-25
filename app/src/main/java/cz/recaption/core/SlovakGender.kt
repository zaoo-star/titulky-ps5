package cz.recaption.core

/**
 * Rod v slovenském překladu – stejná pravidla jako [CzechGender] („Bol som pripravený“ za Lenu →
 * „Bola som pripravená“, „Le, mal si pravdu“ → „mala si pravdu“, „Lena išiel“ → „Lena išla“).
 *
 * Rozdíly proti češtině:
 *  - „si“ je i zvratné zájmeno („vzal si to“ = vzal pro sebe), proto platí jako 2. osoba jen tehdy, když
 *    originál mluví k někomu („you“) a nemluví o nikom třetím (he/she); 3. osobě („Noemi si vzal“) nebrání,
 *  - „by“ stojí i ve 3. osobě („on by prišiel“), 1. osoba je „by som“ – pomocné sloveso je jen „som“,
 *  - příčestí na -ol (mohol → mohla, niesol → niesla) jen ze seznamu, ostatní -ol (kostol, uhol) nic;
 *    „šiel“ → „šla“ (išiel, prišiel, odišiel…), jinak samohláska + l → + a,
 *  - přídavná jména -ý → -á a po dlouhé slabice -y → -a (rytmické krácení: krásny → krásna).
 *
 * Mění se jen spolehlivé tvary; ve sporných případech nic.
 */
object SlovakGender : GenderRules(GenderWords(
    aux1 = setOf("som"),
    aux2 = setOf("si", "buď", "nebuď"),
    aux2Doubtful = setOf("si"),
    notAux12 = setOf("si"),
    link3 = setOf("je", "bude", "nebude", "bol", "nebol", "bola", "nebola"),
    fillers = setOf("mi", "sa", "si", "to", "mu", "ho", "jej", "nám", "tam", "tu", "sem", "už", "ešte", "ma", "mňa",
        "ti", "ťa", "tak", "tiež", "len", "naozaj", "fakt", "vždy", "nikdy", "včera", "dnes", "ale", "by", "nie"),
    aux12Extra = setOf("sme", "ste"),
    fillers3Extra = setOf("vraj", "potom", "zase", "zas", "znova", "znovu", "práve", "hneď", "konečne", "nakoniec", "asi",
        "možno", "určite", "isto", "teraz", "ráno", "večer", "skoro", "vôbec", "najprv", "stále", "rovnako", "iba",
        "ju", "je", "ich", "vás", "nás", "im", "vám", "niekde", "späť", "naspäť", "rýchlo", "nič", "niečo", "nikoho",
        "nikomu", "všetko", "dávno", "nedávno", "včas", "ani", "vlastne", "sama", "sám"),
    clause = setOf("a", "ale", "že", "pretože", "lebo", "keď", "keďže", "až", "či", "ak", "aby", "keby", "než", "zatiaľ",
        "kým", "hoci", "aj", "i", "tak", "alebo", "akonáhle"),
    subord = setOf("že", "ktorý", "ktorá", "ktoré", "ktorú", "ktorého", "čo", "kto", "ako", "keď", "pretože", "lebo",
        "aby", "kde", "kam", "či", "ak", "než", "kým"),
    male = setOf("on", "neho", "jeho", "jemu", "nemu", "ňom", "ním"),
    notPart = setOf("ďalej", "manžel", "anjel", "hotel", "generál", "admirál", "model", "tunel", "panel", "level",
        "signál", "originál", "ideál", "kanál", "štýl", "sál", "fotbal", "futbal", "basketbal", "festival", "materiál",
        "seriál", "kapitál", "metal", "rival", "sval", "obal", "účel", "gól", "bál", "mol", "apríl", "potenciál",
        "personál", "arzenál", "detail", "email", "mail", "pedál", "profil", "mobil", "automobil", "modul", "pastel",
        "škola", "sila", "kola", "hala", "vila", "pila", "mula", "kapela", "strela", "včela", "cela", "tela", "dila"),
    short = mapOf("rád" to "rada", "nerád" to "nerada", "sám" to "sama"),
    been = setOf("bol", "bola", "nebol", "nebola"),
    imperative = setOf("buď", "nebuď"),
    alone = setOf("sám", "sama"),
)) {
    /** Příčestí, kde se mužský a ženský tvar liší víc než o „a“ (mohol → mohla). */
    private val IRREGULAR = mapOf(
        "mohol" to "mohla", "nemohol" to "nemohla", "pomohol" to "pomohla", "nepomohol" to "nepomohla",
        "niesol" to "niesla", "priniesol" to "priniesla", "odniesol" to "odniesla", "doniesol" to "doniesla",
        "zabudol" to "zabudla", "nezabudol" to "nezabudla", "padol" to "padla", "spadol" to "spadla",
        "ukradol" to "ukradla", "sadol" to "sadla", "zjedol" to "zjedla", "utiekol" to "utiekla",
        "vliezol" to "vliezla", "rástol" to "rástla", "vyrástol" to "vyrástla",
    )
    private val IRREGULAR_F = IRREGULAR.entries.associate { it.value to it.key }
    /** Samohlásky, za kterými mužské příčestí jen přibere „a“ (bez „o“ – mohol / mohla). */
    private const val VOWELS = "aeiuyáéíúý"

    /** Přídavné jméno s krátkou koncovkou po dlouhé slabice (krásny, múdry, biely). */
    private fun rhythmic(w: String): Boolean {
        if (w.length < 5 || !w.endsWith("y")) return false
        val stem = w.dropLast(1)
        val i = stem.indexOfLast { it in "aeiouyáéíóúýôä" }
        if (i < 0) return false
        return stem[i] in "áéíóúýô" || (stem[i] in "aeu" && i > 0 && stem[i - 1] == 'i')
    }

    private fun vowelBefore(w: String, end: Int): Boolean = w.length > end && w[w.length - end - 1] in VOWELS

    override fun form(w: String, g: Gender, adjectiveOnly: Boolean): String? =
        if (g == Gender.F) {
            when {
                w in short -> short[w]
                w in shortF -> w
                w.length >= 4 && w.endsWith("ý") -> w.dropLast(1) + "á"
                w.length >= 4 && w.endsWith("á") -> w
                rhythmic(w) -> w.dropLast(1) + "a"
                adjectiveOnly -> null
                w.endsWith("šiel") -> w.dropLast(4) + "šla"
                w.endsWith("šla") -> w
                w in IRREGULAR -> IRREGULAR[w]
                w in IRREGULAR_F -> w
                w == "bol" || w == "nebol" -> w + "a"
                w == "bola" || w == "nebola" -> w
                w.length >= 3 && w.endsWith("l") && vowelBefore(w, 1) -> w + "a"
                w.length >= 4 && w.endsWith("la") && vowelBefore(w, 2) -> w
                else -> null
            }
        } else {
            when {
                w in shortF -> shortF[w]
                w in short -> w
                w.length >= 4 && w.endsWith("á") -> w.dropLast(1) + "ý"
                w.length >= 4 && w.endsWith("ý") -> w
                adjectiveOnly -> null
                w.endsWith("šla") -> w.dropLast(3) + "šiel"
                w.endsWith("šiel") -> w
                w in IRREGULAR_F -> IRREGULAR_F[w]
                w in IRREGULAR -> w
                w == "bola" || w == "nebola" -> w.dropLast(1)
                w == "bol" || w == "nebol" -> w
                w.length >= 4 && w.endsWith("la") && vowelBefore(w, 2) -> w.dropLast(1)
                w.length >= 3 && w.endsWith("l") && vowelBefore(w, 1) -> w
                else -> null
            }
        }
}
