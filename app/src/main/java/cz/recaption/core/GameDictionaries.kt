package cz.recaption.core

/**
 * Herní slovníky překladu – data, která do 0.97.148 byla v kódu a od 0.97.149 přicházejí se staženým seznamem her
 * (pole `slovniky`, [GameListJson]; data jsou v soukromém repu). Do stažení jsou prázdné a překlad jede bez nich.
 *
 * - [idioms] – obraty, které offline překladač překládá doslova, a jejich obyčejná anglická podoba ([OfflinePrep.normalize]),
 * - [phrases] – hotové překlady krátkých herních hlášek po jazycích ([BuiltinPhrases]),
 * - [quick] – pevný překlad krátkých replik („Wait“, „Come on“) po jazycích ([QuickWords.lookup]),
 * - [captions] – popisky zvuků v závorkách („laughs“) po jazycích ([Captions.word]),
 * - [menuTerms] – oprava slov v překladu menu hry po jazycích, v pořadí ([MenuTerms.fix]),
 * - [footer] – překlad tlačítek patičky menu hry po jazycích ([GameMenu.footerWord]),
 * - [names] – jména, která offline překlad schová před překladačem ([OfflinePrep.prepare]).
 */
class GameDictionaries(
    val idioms: List<Pair<Regex, String>> = emptyList(),
    val phrases: Map<String, Map<String, String>> = emptyMap(),
    val quick: Map<String, Map<String, String>> = emptyMap(),
    val captions: Map<String, Map<String, String>> = emptyMap(),
    val menuTerms: Map<String, List<Pair<String, String>>> = emptyMap(),
    val footer: Map<String, Map<String, String>> = emptyMap(),
    val names: List<String> = emptyList(),
) {
    /** Jména jako jeden výraz (delší dřív než kratší), null = žádná. */
    val nameRx: Regex? by lazy {
        if (names.isEmpty()) null
        else Regex("(?<![A-Za-z'\\-])(" + names.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")(?![A-Za-z\\-])")
    }

    /** Oprava slov menu jako připravené výrazy (počítá se jednou). */
    val menuTermRx: Map<String, List<Pair<Regex, String>>> by lazy {
        menuTerms.mapValues { (_, l) -> l.map { (from, to) -> Regex("\\b$from\\b", RegexOption.IGNORE_CASE) to to } }
    }

    val isEmpty: Boolean get() = idioms.isEmpty() && phrases.isEmpty() && quick.isEmpty() && captions.isEmpty() &&
        menuTerms.isEmpty() && footer.isEmpty() && names.isEmpty()

    companion object { val EMPTY = GameDictionaries() }
}
