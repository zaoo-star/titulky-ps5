package cz.recaption.core

/**
 * Plán vedlejších čtení (menu hry, ovládání vpravo dole, pokyny u předmětů, telefon, upozornění, tipy) – titulky mají
 * přednost (přání majitele: „překlad titulku nesmí nic brzdit“). Čtečka textu zpracovává obrázky jeden po druhém, takže
 * každé vedlejší čtení ve frontě zdrží čtení titulku, které přijde po něm. Dřív si každá část hlídala jen svůj rytmus
 * a v jednom kole se jich mohlo spustit i pět najednou, ještě před čtením titulků. Teď:
 *
 * - vedlejší čtení se spouští až po čtení titulků v kole a běží nejvýš jedno,
 * - po něm je pauza aspoň tak dlouhá, jak trvalo ([MIN_GAP_MS]–[MAX_GAP_MS]) – čtečka je nejméně půl času volná pro titulky,
 * - dokud se titulek ustaluje (nový text čeká na potvrzení a překlad), vedlejší čtení čekají, nejdéle [maxWaitMs],
 * - na řadu jde ta část, která čeká nejdéle (žádná nevyhladoví).
 *
 * Volá se z jedné smyčky čtení; [finished] i z úlohy, která četla.
 */
class SideReads(private val maxWaitMs: Long = MAX_WAIT_MS) {
    private var running: String? = null
    private var startedAt = 0L
    private var freeAt = 0L
    /** Od kdy která část čeká a kdy se naposledy ptala (kdo se přestal ptát, řadu nedrží). */
    private val waiting = HashMap<String, Long>()
    private val asked = HashMap<String, Long>()

    /** Smí část [slot] teď číst? [subtitleBusy] = titulek se právě ustaluje. Kdo dostane svolení a čte, zavolá [started]. */
    @Synchronized
    fun mayStart(slot: String, now: Long, subtitleBusy: Boolean): Boolean {
        waiting.keys.retainAll { it == slot || now - (asked[it] ?: 0L) <= STALE_MS }
        asked[slot] = now
        val since = waiting.getOrPut(slot) { now }
        // úloha, která se neozvala (čtečka neodpověděla), řadu nedrží věčně
        if (running != null && now - startedAt > STUCK_MS) { running = null; freeAt = now }
        if (running != null || now < freeAt) return false
        if (subtitleBusy && now - since < maxWaitMs) { stat(slot).heldBySub = true; return false }
        if (waiting.any { it.key != slot && it.value < since }) return false
        waiting.remove(slot)
        stat(slot).pendingWait = now - since
        return true
    }

    /** Část [slot] začala číst (i mimo pořadí – otevření menu hry poznané z jasu nečeká). */
    @Synchronized
    fun started(slot: String, now: Long) {
        running = slot; startedAt = now; waiting.remove(slot)
        val s = stat(slot)
        s.count++; s.waitSum += s.pendingWait; s.waitMax = maxOf(s.waitMax, s.pendingWait)
        if (s.heldBySub) s.heldCount++
        s.pendingWait = 0L; s.heldBySub = false
    }

    @Synchronized
    fun finished(slot: String, now: Long) {
        if (running != slot) return
        running = null
        stat(slot).runSum += now - startedAt
        freeAt = now + (now - startedAt).coerceIn(MIN_GAP_MS, MAX_GAP_MS)
    }

    // ---- měření čekání (od 1.0.06, majitel: „musíme sledovat, jestli to nebude na škodu čekat“) ----

    private class Stat { var count = 0; var waitSum = 0L; var waitMax = 0L; var runSum = 0L; var heldCount = 0
        var pendingWait = 0L; var heldBySub = false }
    private val stats = LinkedHashMap<String, Stat>()
    private fun stat(slot: String) = stats.getOrPut(slot) { Stat() }

    /**
     * Souhrn od minula do záznamu (řádek `VEDLE|` v hlášení) a vynulování: po částech počet čtení, průměrné a nejdelší
     * čekání ve frontě v ms, průměrná doba čtení v ms a kolikrát čekala kvůli ustalujícímu se titulku – např.
     * „roh 12× čekání 140/900 ms čtení 85 ms kvůli titulku 3“. null = nic nečetlo.
     */
    @Synchronized
    fun summary(): String? {
        val parts = stats.filterValues { it.count > 0 }.map { (k, s) ->
            "$k ${s.count}× čekání ${s.waitSum / s.count}/${s.waitMax} ms čtení ${s.runSum / s.count} ms kvůli titulku ${s.heldCount}"
        }
        stats.values.forEach { it.count = 0; it.waitSum = 0; it.waitMax = 0; it.runSum = 0; it.heldCount = 0 }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** Běží právě vedlejší čtení? */
    val busy: Boolean @Synchronized get() = running != null

    @Synchronized
    fun reset() { running = null; freeAt = 0L; waiting.clear(); asked.clear() }

    companion object {
        const val MAX_WAIT_MS = 2000L
        const val MIN_GAP_MS = 120L
        const val MAX_GAP_MS = 600L
        const val STALE_MS = 1500L
        const val STUCK_MS = 6000L
        const val MENU = "menu"
        const val CORNER = "roh"
        const val WORLD = "predmety"
        const val PHONE = "telefon"
        const val NOTICE = "upozorneni"
        const val TIP = "tip"
    }
}
