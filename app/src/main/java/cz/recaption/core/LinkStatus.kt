package cz.recaption.core

/**
 * Stav spojení s konzolí (PlayStation nebo Xbox) ve třech úrovních, jak je vidí hráč:
 *  1. překlad (služba) běží,
 *  2. aplikace je spojená s konzolí,
 *  3. z konzole opravdu přichází obraz.
 *
 * Běžící překlad ani navázané spojení ještě neznamenají, že chodí obraz – proto se to rozlišuje.
 * Čas dodává volající (v aplikaci SystemClock.uptimeMillis, v testech simulovaný).
 */
object LinkStatus {
    /** Po navázání spojení se na první obraz čeká nejdéle tak dlouho, pak se hlásí „žádný obraz“. */
    const val FIRST_IMAGE_MS = 10_000L
    /** Když obraz chodil a takhle dlouho nepřišel, hlásí se „obraz nepřichází“. */
    const val STALL_MS = 5_000L

    enum class Stage { OFF, PAUSED, DEMO, CONNECTING, RETRYING, FAILED, WAIT_IMAGE, NO_IMAGE, IMAGE, STALLED }

    data class Status(
        val stage: Stage,
        /** Úroveň 1: překlad běží. */
        val service: Boolean,
        /** Úroveň 2: spojeno s konzolí. */
        val console: Boolean,
        /** Úroveň 3: obraz právě přichází. */
        val image: Boolean,
        /** Před kolika ms přišel poslední snímek tohoto spojení; null = zatím žádný. */
        val frameAgeMs: Long?,
        /** Jak dlouho už obraz nepřichází (0 = přichází, nebo se na něj teprve chvilku čeká). */
        val noImageMs: Long,
    ) {
        /** Je důvod hráče upozornit (spojeno, ale obraz nechodí)? */
        val warning: Boolean get() = stage == Stage.NO_IMAGE || stage == Stage.STALLED || stage == Stage.FAILED
    }

    /**
     * @param connectedAt kdy se navázalo právě běžící spojení (0 = neznámo)
     * @param lastFrameAt kdy přišel poslední snímek (0 = žádný); starší než [connectedAt] patří
     *   předchozímu spojení a nepočítá se
     */
    fun evaluate(
        now: Long, running: Boolean, paused: Boolean, demo: Boolean,
        link: Ps5.Link, connectedAt: Long, lastFrameAt: Long,
    ): Status {
        if (!running) return Status(Stage.OFF, false, false, false, null, 0)
        val connected = link == Ps5.Link.CONNECTED
        if (paused) return Status(Stage.PAUSED, true, connected, false, null, 0)
        if (demo) {
            val age = if (lastFrameAt > 0) (now - lastFrameAt).coerceAtLeast(0) else null
            return Status(Stage.DEMO, true, false, age != null && age <= STALL_MS, age, 0)
        }
        when (link) {
            Ps5.Link.OFF, Ps5.Link.CONNECTING -> return Status(Stage.CONNECTING, true, false, false, null, 0)
            Ps5.Link.WAITING -> return Status(Stage.RETRYING, true, false, false, null, 0)
            Ps5.Link.FAILED -> return Status(Stage.FAILED, true, false, false, null, 0)
            Ps5.Link.CONNECTED -> {}
        }
        val since = if (connectedAt > 0) (now - connectedAt).coerceAtLeast(0) else 0
        val ownFrame = lastFrameAt > 0 && lastFrameAt >= connectedAt
        if (!ownFrame) {
            return if (since >= FIRST_IMAGE_MS) Status(Stage.NO_IMAGE, true, true, false, null, since)
            else Status(Stage.WAIT_IMAGE, true, true, false, null, 0)
        }
        val age = (now - lastFrameAt).coerceAtLeast(0)
        return if (age > STALL_MS) Status(Stage.STALLED, true, true, false, age, age)
        else Status(Stage.IMAGE, true, true, true, age, 0)
    }

    private fun secs(ms: Long): String = (ms / 1000).toString()

    /** Krátký jednořádkový stav pro stránku Domů. */
    fun line(lang: Lang, s: Status, console: Console = Console.PLAYSTATION): String {
        val text = I18n.t(lang, console.key("ls_" + s.stage.name.lowercase()))
        return text.replace("%d", secs(s.noImageMs))
    }

    /** Podrobnosti po úrovních (✔ / ✘) pro diagnostiku. */
    fun details(lang: Lang, s: Status, console: Console = Console.PLAYSTATION): List<String> {
        fun mark(ok: Boolean) = if (ok) "✔ " else "✘ "
        val img = when {
            s.image -> I18n.t(lang, "lv_img_on").replace("%d", secs(s.frameAgeMs ?: 0))
            s.frameAgeMs != null -> I18n.t(lang, "lv_img_off").replace("%d", secs(s.frameAgeMs))
            else -> I18n.t(lang, "lv_img_none")
        }
        return listOf(
            mark(s.service) + I18n.t(lang, if (s.service) "lv_svc_on" else "ls_off"),
            mark(s.console) + I18n.t(lang, console.key(if (s.console) "lv_con_on" else "lv_con_off")),
            mark(s.image) + img,
        )
    }
}
