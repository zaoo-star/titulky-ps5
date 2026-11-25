package cz.recaption

/**
 * Zdroj obrazu z konzole pro čtení titulků: PlayStation ([Ps5Link], video H.264 do [StreamReader.feed])
 * nebo Xbox ([XboxLink], hotové snímky WebRTC do [StreamReader.feedI420]). Čtení, OCR a překlad
 * v [TranslatorService] jsou pro obě konzole stejné – berou výřezy z [StreamReader.grab].
 */
interface ConsoleLink {
    fun start()
    fun stop()
    /** Spojení „visí“ (připojeno, ale obraz nechodí) → začít znovu. */
    fun restart(why: String)
    /** Jak dlouho je právě spojeno (0 = nespojeno). */
    val connectedForMs: Long
}
