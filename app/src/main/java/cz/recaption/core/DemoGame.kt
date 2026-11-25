package cz.recaption.core

/**
 * Ukázková „hra“: anglické titulky, které se střídají podle času. Stejný rozvrh používá
 * obrazovka ukázky i ukázkové video, takže pruh s překladem sedí na to, co je vidět.
 */
object DemoGame {
    val lines = listOf(
        "Marek: We need money, Jonas.",
        "",
        "Jonas: I reckon we won't find anything in Greyford.",
        "",
        "Get on your horse, we ride at dawn.",
        "",
        "Silas: If we keep going like this,\nthe marshals will find us.",
        "",
        // různé podoby titulků, jak je hry píšou
        "MIRA: Are you sure this is the right way?",          // jméno velkými písmeny
        "",
        "Tobin: no idea, but we can't stay here",             // malé písmeno na začátku, bez tečky
        "",
        "WE HAVE TO GET OUT OF HERE!",                        // celé velkými písmeny
        "",
        "Wait",                                               // jedno krátké slovo
        "",
        "[Old Kessler] You two were never supposed to meet.", // jméno v závorkách
        "",
        "Mira: I'll take the left door.\nTobin: Then I'm going right.",   // dva mluvčí najednou
        "",
        // drobné písmo a neklidné pozadí, jak to bývá ve skutečných hrách (viz [style])
        "Lena: Keep your voice down, they can hear us.",
        "",
        "I don't think anyone has been here for years.",
        "",
        "Davor: Hand me the lantern and stay close.",
        "",
        "Lena: The bridge is out, we have to swim.",
        "",
        "Davor: Run for the gate!",                           // velké písmo
        "",
        // otázka a pod ní volby odpovědi – v pruhu jako seznam, každá volba přeložená zvlášť
        "Kessler: So, what will it be?\n1. I'll take the job.\n2. Tell me more about the pay.\n3. Not interested.",
        "",
    )

    /** Řádek s volbou odpovědi ("1. …") – kreslí se zarovnaný vlevo jako ve hrách. */
    fun isChoiceLine(line: String): Boolean = CHOICE_LINE.containsMatchIn(line)
    private val CHOICE_LINE = Regex("^\\d\\. ")
    /** Jak se titulek kreslí: výška písma jako zlomek výšky obrazu a jestli je pod ním světlé neklidné pozadí. */
    data class Style(val size: Float, val busy: Boolean)

    fun style(subtitle: String): Style = when {
        subtitle.startsWith("Lena: Keep") -> Style(0.024f, false)
        subtitle.startsWith("I don't think") -> Style(0.020f, false)
        subtitle.startsWith("Davor: Hand") -> Style(0.016f, false)
        subtitle.startsWith("Lena: The bridge") -> Style(0.026f, true)
        subtitle.startsWith("Davor: Run") -> Style(0.060f, false)
        subtitle.startsWith("Kessler:") -> Style(0.028f, false)
        else -> Style(0.032f, false)
    }

    const val SHOWN_MS = 6000L
    const val GAP_MS = 1500L
    val cycleMs: Long = lines.sumOf { if (it.isEmpty()) GAP_MS else SHOWN_MS }

    /** Pořadí titulku, který je vidět v čase [ms] od začátku. */
    fun indexAt(ms: Long): Int {
        var t = ((ms % cycleMs) + cycleMs) % cycleMs
        for ((i, l) in lines.withIndex()) {
            val d = if (l.isEmpty()) GAP_MS else SHOWN_MS
            if (t < d) return i
            t -= d
        }
        return 0
    }

    fun subtitleAt(ms: Long): String = lines[indexAt(ms)]

    /** Kolik ms je právě zobrazený titulek na obrazovce (pro měření zpoždění překladu). */
    fun shownFor(ms: Long): Long {
        var t = ((ms % cycleMs) + cycleMs) % cycleMs
        for (l in lines) {
            val d = if (l.isEmpty()) GAP_MS else SHOWN_MS
            if (t < d) return t
            t -= d
        }
        return 0
    }
}
