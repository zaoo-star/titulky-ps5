package cz.recaption.core

/**
 * Barevné značky účtů PlayStation, všude stejné (přání majitele, 21. 11. 2026): hráč má na první pohled
 * poznat, kdy jde o druhý účet pro ReCaption a kdy o jeho herní účet.
 *
 * - [RECAPTION] 🔵 = účet pro ReCaption (druhý účet): přihlašuje se jím telefon, na PS5 se na něj přepne,
 *   zapne Remote Play a vyvolá osmimístné číslo. U jména účtu vždy „🔵 JMÉNO“ ([named]).
 * - [GAME] 🟢 = herní účet, na který se hráč po spárování na PS5 přepne zpátky a hraje.
 *
 * Texty v I18n mají značky vepsané přímo (překladatel je vidí ve větě); v kódu se berou odsud.
 */
object AccountMarks {
    const val RECAPTION = "🔵"
    const val GAME = "🟢"
    /** 🟢 umí Android až od 10 (Emoji 12); starší TV a boxy by ukázaly prázdný čtvereček → tam 💚 (`Context.tx`). */
    const val GAME_OLD = "💚"

    /** Jméno účtu pro ReCaption se značkou: „🔵 JMÉNO“. */
    fun named(name: String): String = "$RECAPTION $name"
}
