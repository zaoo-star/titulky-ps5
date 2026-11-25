package cz.recaption.core

/**
 * Nastavení výkonu podle paměti zařízení. Slabší televize (1–2 GB RAM) čtou menší obraz
 * a méně často – aplikace jim nebere paměť ani výkon a nespadne.
 *
 * [captureWidth] = šířka obrazu, který si aplikace bere z PlayStationu (1920 = drobné titulky
 * se čtou spolehlivěji, 1280 = šetrnější k Wi-Fi a výkonu). [intervalMs] = nejkratší pauza mezi čteními.
 */
data class Perf(val captureWidth: Int, val intervalMs: Long, val eco: Boolean) {
    val captureHeight: Int get() = captureWidth * 9 / 16

    /** Uživatel si může přesnost čtení vynutit: "std" = 1280, "high" = 1920, jinak podle zařízení. */
    fun withQuality(choice: String?): Perf = when (choice) {
        "std" -> copy(captureWidth = 1280)
        "high" -> copy(captureWidth = 1920)
        else -> this
    }

    companion object {
        fun forDevice(totalRamMb: Long, lowRamFlag: Boolean): Perf = when {
            lowRamFlag || totalRamMb in 1..1600 -> Perf(1280, 200, true)    // ~1 GB: čte méně často
            totalRamMb in 1601..2600 -> Perf(1280, 100, true)               // ~2 GB
            else -> Perf(1920, 50, false)                                   // 3 GB a víc (nebo neznámo)
        }
    }
}
