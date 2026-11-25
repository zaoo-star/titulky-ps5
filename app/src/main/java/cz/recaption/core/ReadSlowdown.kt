package cz.recaption.core

/**
 * Pozná, že čtení obrazu hry náhle výrazně zpomalilo (24. 11. 2026, přání majitele). Typicky když hráč v telefonu přepne
 * z ReCaptionu do jiné aplikace nebo zhasne displej: Android aplikaci přibrzdí a vezme jí dekodér videa – titulky v TV
 * pak chodí pozdě nebo vůbec a hráč neví proč. Televize dostane oranžový řádek stavu (`TvStatus.rows`), který zmizí,
 * jakmile se čtení vrátí k obvyklé rychlosti.
 *
 * Měří se počet snímků videa, které smyčka čtení za poslední úsek zpracovala (ne počet čtení textu – ten při
 * nehybném obrazu klesá záměrně). „Obvykle“ = klouzavý průměr z doby, kdy čtení běželo dobře; pomalé úseky ho nemění.
 * Bez Androidu (testy `ReadSlowdownTest`); vzorek se bere každé 2 s spolu se stavem pro televizi.
 */
class ReadSlowdown {
    companion object {
        /** Pod tímto zlomkem obvyklé rychlosti je čtení pomalé… */
        const val SLOW = 0.35
        /** …a od tohoto zlomku zase v pořádku (mezi tím se stav nemění). */
        const val FINE = 0.6
        /** Kolik vzorků po sobě musí být pomalých (při 2 s ≈ 6 s – krátké zaškobrtnutí neohlásit). */
        const val SLOW_SAMPLES = 3
        /** Kolik vzorků po sobě v pořádku upozornění zruší. */
        const val FINE_SAMPLES = 2
        /** Prvních pár vzorků jen učí obvyklou rychlost. */
        const val WARM_SAMPLES = 5
        /** Pomalejší „obvyklá“ rychlost (snímků/s) se nehlídá – není z čeho poznat zpomalení. */
        const val MIN_USUAL = 3.0
        /** Kratší úsek mezi vzorky se nepočítá (dva vzorky těsně po sobě). */
        const val MIN_GAP_MS = 500L
        /** Po takhle dlouhém zpomalení při viditelné aplikaci se pomalá rychlost bere jako nová obvyklá. */
        const val ADOPT_MS = 90_000L
    }

    /** Obvyklá rychlost (snímků/s); 0 = ještě se neví. */
    var usual = 0.0
        private set
    var slow = false
        private set

    private var lastAt = 0L
    private var lastCount = 0L
    private var have = false
    private var warm = 0
    private var lowRun = 0
    private var fineRun = 0
    private var slowSince = 0L

    /**
     * Vzorek: [count] = kolik snímků smyčka čtení dosud zpracovala, [active] = má se teď číst (překlad běží, obraz
     * z konzole chodí, není pauza ani otevřené menu hry), [visible] = aplikace je na zařízení vidět. Vrací [slow].
     */
    fun sample(now: Long, count: Long, active: Boolean, visible: Boolean = false): Boolean {
        if (!active) {
            // nečte se záměrně: nic nehlásit a úsek začít znovu (obvyklá rychlost zůstává)
            have = false; lowRun = 0; fineRun = 0; slow = false
            return false
        }
        if (!have || count < lastCount) { have = true; lastAt = now; lastCount = count; return slow }
        val dt = now - lastAt
        if (dt < MIN_GAP_MS) return slow
        val rate = (count - lastCount) * 1000.0 / dt
        lastAt = now; lastCount = count

        if (warm < WARM_SAMPLES) {
            warm++
            usual = if (warm == 1) rate else maxOf(usual, rate) * 0.5 + minOf(usual, rate) * 0.5
            return false
        }
        if (usual < MIN_USUAL) {
            // zatím moc pomalé na hlídání – učit se dál
            usual = usual * 0.8 + rate * 0.2
            return false
        }
        when {
            rate < usual * SLOW -> {
                fineRun = 0
                if (++lowRun >= SLOW_SAMPLES && !slow) { slow = true; slowSince = now }
            }
            rate >= usual * FINE -> {
                lowRun = 0
                usual = usual * 0.9 + rate * 0.1
                if (slow && ++fineRun >= FINE_SAMPLES) { slow = false; fineRun = 0 }
            }
            else -> { lowRun = 0; fineRun = 0 }
        }
        // aplikace je vidět a čtení je pomalé už dlouho → tak teď zařízení čte (jiná hra, slabší síť); nedržet řádek navždy
        if (slow && visible && now - slowSince > ADOPT_MS) {
            slow = false; lowRun = 0; fineRun = 0; warm = 0; usual = 0.0
        }
        return slow
    }

    /** Nové spuštění překladu: všechno zapomenout. */
    fun reset() {
        usual = 0.0; slow = false; have = false; warm = 0; lowRun = 0; fineRun = 0; slowSince = 0L
    }
}
