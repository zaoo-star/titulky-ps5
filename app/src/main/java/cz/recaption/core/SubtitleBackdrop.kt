package cz.recaption.core

/**
 * Nápis ve scéně na místě titulků (jméno na dresu, značka na autě, cedule) – ne titulek. Hlášení testera 23. 11. 2026:
 * postava stála zády ke kameře a pruh „překládal“ jméno z dresu. Jen u her s bílými titulky (zatím GTA VI), jen řádky
 * bez mluvčího; řádek s „Jméno:“ a popisek zvuku se nevyřadí nikdy. Dvě pravidla podle jasu bodů v rámečku řádku:
 *
 * 1. **Titulky s podkladem.** Když má hráč ve hře zapnutý tmavý podklad titulků, má každý titulek tmavé pozadí. Pozná se
 *    to samo: posledních [NEED] řádků s mluvčím mělo medián jasu nejvýš [DARK]. Pak řádek bez mluvčího na světlém pozadí
 *    (medián aspoň [LIGHT]) titulek není. Bez podkladu ve hře se nic nenaučí a pravidlo neplatí.
 * 2. **Tmavé písmo na světlém.** Krátký nápis (nejvýš 3 slova), jehož písmo je tmavší než pozadí a nic v rámečku není
 *    světlejší než pozadí – titulek je bílý, takže je vždy světlejší než to, na čem leží.
 *
 * Bez Androidu – testy `SubtitleBackdropTest`.
 */
class SubtitleBackdrop {
    private val recent = ArrayDeque<Int>()

    private var lastKey: String? = null

    /**
     * Medián jasu rámečku řádku s mluvčím (jistý titulek). [key] = text řádku: tentýž titulek čtený několikrát po sobě
     * se počítá jednou – jinak by se „podklad“ naučil z jediné repliky v tmavé scéně.
     */
    fun learn(median: Int, key: String? = null) {
        if (key != null) { if (key == lastKey) return; lastKey = key }
        recent.addLast(median)
        while (recent.size > KEEP) recent.removeFirst()
    }

    /** Hra kreslí titulky na tmavý podklad (podle posledních titulků s mluvčím). */
    val boxed: Boolean get() = recent.size >= NEED && recent.all { it <= DARK }

    fun reset() { recent.clear(); lastKey = null }

    /**
     * Je řádek bez mluvčího nápis ve scéně? [gray] = jasy bodů v rámečku (0–255), [words] = počet slov řádku.
     * Málo bodů = neví se = titulek.
     */
    fun sceneText(gray: IntArray, words: Int): Boolean {
        if (gray.size < 20) return false
        val s = gray.sortedArray()
        val p10 = s[s.size / 10]; val med = s[s.size / 2]; val p90 = s[(s.size * 9) / 10]
        if (boxed && med >= LIGHT) return true
        return words <= 3 && med >= 100 && p90 - med <= 15 && med - p10 >= 50
    }

    companion object {
        const val KEEP = 12
        const val NEED = 6
        const val DARK = 90
        const val LIGHT = 130

        fun median(gray: IntArray): Int = if (gray.isEmpty()) 255 else gray.sortedArray()[gray.size / 2]
    }
}
