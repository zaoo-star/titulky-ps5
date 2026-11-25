package cz.recaption.core

/** Překlady šablony průvodce ([Pruvodce]) do jazyků aplikace kromě češtiny a angličtiny; klíč = anglické znění. */
object PruvodcePreklady {
    /** Jazyk → (anglický text → překlad). Každý jazyk ve vlastním souboru (jinak by inicializace byla na JVM moc velká). */
    val MAPY: Map<String, Map<String, String>> by lazy { mapOf(
        "sk" to PruvodcePrekladySk.MAPA,
        "pl" to PruvodcePrekladyPl.MAPA,
        "de" to PruvodcePrekladyDe.MAPA,
        "hu" to PruvodcePrekladyHu.MAPA,
        "uk" to PruvodcePrekladyUk.MAPA,
        "es" to PruvodcePrekladyEs.MAPA,
        "fr" to PruvodcePrekladyFr.MAPA,
        "it" to PruvodcePrekladyIt.MAPA,
        "hi" to PruvodcePrekladyHi.MAPA,
    ) }

    fun get(lang: String, en: String): String? = MAPY[lang]?.get(en)
}
