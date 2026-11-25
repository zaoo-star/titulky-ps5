package cz.recaption.core

/**
 * Co aplikace naposledy četla v oblasti titulků a co z toho vzala – pro ladění profilů her
 * (menu, hlášky, kde hra píše titulky). Jde do hlášení a do záznamu v síti, nikam jinam.
 * Ukládá se jen změna (stejné čtení za sebou se sloučí do počtu).
 */
object ScreenReads {
    data class Item(val time: Long, val raw: String, val verdict: String, var count: Int = 1)

    private const val MAX = 150
    private val items = ArrayDeque<Item>()

    @Synchronized fun add(raw: String, verdict: String, time: Long = System.currentTimeMillis()) {
        val r = raw.replace("\n", " | ").trim().take(300)
        if (r.isEmpty()) return
        val last = items.lastOrNull()
        if (last != null && last.raw == r && last.verdict == verdict) { last.count++; return }
        items.addLast(Item(time, r, verdict.take(200)))
        while (items.size > MAX) items.removeFirst()
    }

    /** Nejstarší první. */
    @Synchronized fun all(): List<Item> = items.toList()

    @Synchronized fun clear() = items.clear()
}
