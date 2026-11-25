package cz.recaption.core

import java.security.MessageDigest

/**
 * Licence: 5 dní zkušební doba (do 0.97.99 3 dny – přání majitele 22. 11. 2026) zdarma, pak je potřeba předplatné – nebo odemykací kód.
 * Kód není v aplikaci uložený čitelně, jen jeho otisk (SHA-256).
 */
object License {
    const val TRIAL_MS = 5L * 24 * 60 * 60 * 1000

    /**
     * Aplikace je zatím zdarma (přání majitele 23. 11. 2026, od 0.97.147): zkušební doba neběží a nic se nezamyká.
     * Při zpoplatnění přepnout na false – a začátek zkušební doby ukládat pod novým klíčem (ne `first_run`),
     * ať ji mají i ti, kdo aplikaci nainstalovali, dokud byla zdarma.
     */
    const val FREE = true

    /** Před tímto datem (1. 1. 2026) hodiny zařízení zjevně nejsou nastavené. */
    const val MIN_SANE = 1_767_225_600_000L

    private const val CODE_HASH = "7085368d3684e2b69af725accc4dab7cdb117e22cba683f462ce1aecc871a5ed"

    enum class Kind { UNLOCKED, TRIAL, EXPIRED }

    data class State(val kind: Kind, val daysLeft: Int = 0) {
        val canRun: Boolean get() = kind != Kind.EXPIRED
    }

    /** Odpovídá zadaný text odemykacímu kódu? (Nezáleží na velikosti písmen a mezerách okolo.) */
    fun checkCode(input: String): Boolean {
        val norm = input.trim().lowercase()
        if (norm.isEmpty()) return false
        val digest = MessageDigest.getInstance("SHA-256").digest("testpreklad:$norm".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) } == CODE_HASH
    }

    /**
     * Začátek zkušební doby. Když ještě není uložený (nebo byl uložený se špatně nastavenými
     * hodinami televize), začíná teď. 0 = hodiny zatím nejsou nastavené, nic neukládat.
     */
    fun firstRun(saved: Long, now: Long): Long = when {
        now < MIN_SANE -> if (saved >= MIN_SANE) saved else 0L
        saved < MIN_SANE -> now
        saved > now -> now          // hodiny byly posunuté dopředu a zpět
        else -> saved
    }

    fun state(unlocked: Boolean, firstRun: Long, now: Long, free: Boolean = FREE): State {
        if (unlocked || free) return State(Kind.UNLOCKED)
        // televize po zapnutí bez internetu nemusí znát čas – kvůli tomu nezamykáme
        if (now < MIN_SANE || firstRun < MIN_SANE) return State(Kind.TRIAL, 5)
        val left = firstRun + TRIAL_MS - now
        if (left <= 0) return State(Kind.EXPIRED)
        val day = 24L * 60 * 60 * 1000
        return State(Kind.TRIAL, ((left + day - 1) / day).toInt().coerceIn(1, 5))
    }
}
