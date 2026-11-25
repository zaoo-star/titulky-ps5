package cz.recaption.core

/**
 * Okna přes hru (Android: [cz.recaption.OverlayWindows]) – kolik jich aplikace smí mít a kdy znovu zkusit přidat okno.
 *
 * Proč (1.0.00, Samsung SM-G781B, hlášení R932-QZ86): systém má na proces strop oken. Když ho popisky menu, rohu, telefonu
 * a další okna (dřív okno na každý popisek, a popisky menu se při novém nastavení nemazaly) vyčerpaly, nešlo otevřít
 * ani dialog Poslat vývojáři („tlačítko nic nedělá“) a další obrazovka aplikace spadla („window count is over max“).
 * Proto: vlastní strop [max] hluboko pod systémovým a po nepovedeném přidání pauza [pauseMs] (bez oprávnění k překryvu
 * se okno nezkouší přidat při každém čtení znovu).
 */
class WindowBudget(val max: Int = MAX, private val pauseMs: Long = PAUSE_MS) {
    companion object {
        /** Víc oken přes hru aplikace nikdy nepotřebuje (pruh, úkol, popis, nadpis, seznam, telefon, skupiny popisků, stav…). */
        const val MAX = 24
        const val PAUSE_MS = 5000L
    }

    private var blockedUntil = 0L

    /** Smí se teď přidat další okno, když jich aplikace má [live]? */
    @Synchronized fun mayAdd(live: Int, now: Long): Boolean = live < max && now >= blockedUntil

    /** Přidání se nepovedlo (plno, chybí oprávnění, systém odmítl) – chvíli nezkoušet. */
    @Synchronized fun failed(now: Long) { blockedUntil = now + pauseMs }

    /** Okno se povedlo přidat – pauza po dřívější chybě končí. */
    @Synchronized fun ok() { blockedUntil = 0L }
}

/**
 * Jedno okno pro celou skupinu popisků (popisky menu, menu vpravo dole, pokyny u předmětů, překlad přes text telefonu,
 * patička menu): okno je obdélník, který obalí všechny viditelné popisky; popisky v něm leží na svých místech.
 */
object OverlayBounds {
    /** Obdélník kolem všech [rects] (každý `x, y, šířka, výška`) jako `x, y, šířka, výška`; null = žádný (nic není vidět). */
    fun union(rects: List<IntArray>): IntArray? {
        if (rects.isEmpty()) return null
        var l = Int.MAX_VALUE; var t = Int.MAX_VALUE; var r = Int.MIN_VALUE; var b = Int.MIN_VALUE
        for (q in rects) {
            l = minOf(l, q[0]); t = minOf(t, q[1])
            r = maxOf(r, q[0] + maxOf(0, q[2])); b = maxOf(b, q[1] + maxOf(0, q[3]))
        }
        return intArrayOf(l, t, maxOf(1, r - l), maxOf(1, b - t))
    }
}
