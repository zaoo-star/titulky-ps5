package cz.recaption.core

/**
 * Co se u hry s profilem překládá navíc k titulkům (Titulky → Hra, od 0.97.59): každý text hry má vlastní
 * přepínač – Pokyny pod titulky, Úkol pod titulky, Překlad menu hry. Žádná obecná volba je nepřebíjí.
 *
 * Do 0.97.57 byla jedna volba „Rozsah čtení“ (klíč `game_texts`): „jen titulky“ (false) vypnula všechno navíc.
 * Uložené nastavení se nemění, takže kdo měl „jen titulky“, má přepínače vypnuté, dokud některý sám nezmění.
 */
object GameScope {
    /**
     * Platná hodnota přepínače.
     * @param legacyTexts uložená stará volba `game_texts` (null = nikdy nezměněná)
     * @param chosen hráč tenhle přepínač od 0.97.59 sám přepnul
     * @param value uložená (nebo výchozí) hodnota přepínače
     */
    fun resolve(legacyTexts: Boolean?, chosen: Boolean, value: Boolean): Boolean =
        if (legacyTexts == false && !chosen) false else value
}
