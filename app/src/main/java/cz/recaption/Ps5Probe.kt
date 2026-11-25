package cz.recaption

import cz.recaption.core.Console
import cz.recaption.core.Ps5

/**
 * Stav spárované PS5, když překlad neběží (od 0.97.75, majitel: Domů ukazovalo vypnutou PS5 zeleně „spárováno“).
 * Krátké hledání v síti (1,5 s) na pozadí nejvýš jednou za 20 s – jen když o stav někdo stojí (Domů v aplikaci,
 * stav pro aplikaci v TV). Za překladu se nehledá – stav pak hlásí spojení (`Ps5Link`).
 * ON = PS5 v síti odpověděla zapnutá, REST = v klidovém režimu, UNKNOWN = neodpověděla (vypnutá úplně,
 * nebo síť hledání nepropouští – proto ne „vypnutá“).
 */
object Ps5Probe {
    enum class State { UNKNOWN, ON, REST }

    @Volatile var state = State.UNKNOWN
        private set
    @Volatile private var lastAt = 0L
    @Volatile private var busy = false
    private const val EVERY_MS = 20_000L

    /** Zavolat z tickeru / stavu pro TV; hledání proběhne jen, když je na řadě. Nic neblokuje. */
    fun poke(c: android.content.Context) {
        if (busy || TranslatorService.running || Prefs.console(c) != Console.PLAYSTATION) return
        val host = Prefs.ps5Host(c) ?: run { state = State.UNKNOWN; return }
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastAt != 0L && now - lastAt < EVERY_MS) return
        lastAt = now
        busy = true
        Thread {
            try {
                val mac = Ps5.hex(host.mac)
                val f = Ps5Pairing.discover(1500, quiet = true)
                    .firstOrNull { it.ip == host.ip || (mac.isNotEmpty() && it.id.equals(mac, ignoreCase = true)) }
                state = when {
                    f == null -> State.UNKNOWN
                    f.standby -> State.REST
                    else -> State.ON
                }
            } catch (_: Throwable) {
                state = State.UNKNOWN
            } finally {
                busy = false
            }
        }.apply { isDaemon = true; name = "ps5-stav" }.start()
    }

    /** Po spárování / změně PS5 hledat hned znovu. */
    fun reset() { lastAt = 0L; state = State.UNKNOWN }
}
