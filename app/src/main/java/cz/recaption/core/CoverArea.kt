package cz.recaption.core

/**
 * Volba „Zakrýt celou oblast titulků“ (od 0.97.59, přání majitele: dvouřádkové titulky občas vyčuhovaly nad pruh nebo
 * po stranách). Pruh v poloze „Přes titulky“ nekryje jen právě přečtený řádek ([CoverBox]), ale celé pole, kde se anglické
 * titulky v téhle hře naposledy ukazovaly – obálku rámečků posledních titulků.
 *
 * Oblast čtení zůstává větší (hledá se v ní), tahle plocha je přísnější: jen kde text opravdu byl, s malým okrajem.
 *
 * - Historie: nejvýš [maxBoxes] posledních titulků ne starších než [maxAgeMs].
 * - Jen titulky zhruba na stejném místě jako ten právě zobrazený (střed svisle do [sameRow]) – titulek nahoře v jiné scéně
 *   pruh přes půl obrazu neroztáhne.
 * - Odlehlé čtení: krajní hrana staršího titulku, která je od další nejbližší dál než [outlierLines] výšek řádku, se nebere
 *   (jeden špatně přečtený rámeček pruh nenafoukne); vodorovně se délka řádků liší víc, tam je hranice [outlierX]. Právě zobrazený titulek se bere vždy – ten musí být zakrytý celý.
 * - Okraj [padY] / [padX] (zlomky obrazu) a pak jen uvnitř oblasti čtení (rozšířené o ten okraj).
 * - Plocha je nejvýš [maxHeight] vysoká; jinak jen právě zobrazený titulek.
 * - Během rozhovoru se plocha nezmenšuje (pruh neposkakuje): dokud pruh nebyl schovaný déle než [holdMs], jen roste.
 */
class CoverArea(
    private val maxBoxes: Int = 12,
    private val maxAgeMs: Long = 90_000,
    private val padY: Float = 0.012f,
    private val padX: Float = 0.02f,
    private val sameRow: Float = 0.12f,
    private val outlierLines: Float = 2.5f,
    private val outlierX: Float = 0.2f,
    private val maxHeight: Float = 0.30f,
    private val holdMs: Long = 10_000,
) {
    private class Box(val top: Float, val bottom: Float, val left: Float, val right: Float, val at: Long)

    private val boxes = ArrayList<Box>()
    private var held: FloatArray? = null
    private var hiddenAt = -1L

    /**
     * Nový titulek [top]..[bottom], [left]..[right] (zlomky obrazu, vlevo/vpravo -1 = neznámé) → plocha k zakrytí
     * [nahoře, dole, vlevo, vpravo] (vlevo/vpravo -1, když vodorovný rozsah neznáme). null = titulek neznámý.
     */
    @Synchronized
    fun next(top: Float, bottom: Float, left: Float, right: Float, read: Zone, now: Long): FloatArray? {
        if (top < 0f || bottom <= top) return null
        val hasX = left >= 0f && right > left
        // pauza v rozhovoru delší než holdMs → plocha se smí zase zmenšit
        if (hiddenAt >= 0 && now - hiddenAt > holdMs) held = null
        hiddenAt = -1
        val cur = Box(top, bottom, if (hasX) left else -1f, if (hasX) right else -1f, now)
        boxes.removeAll { now - it.at > maxAgeMs || it.at > now }
        boxes.add(cur)
        while (boxes.size > maxBoxes) boxes.removeAt(0)

        val cy = (top + bottom) / 2
        val near = boxes.filter { it !== cur && kotlin.math.abs((it.top + it.bottom) / 2 - cy) <= sameRow }
        // výška řádku = nejnižší rámeček (jednořádkový titulek)
        val lineH = (near.map { it.bottom - it.top } + (bottom - top)).minOrNull()!!.coerceAtLeast(0.015f)
        val limit = outlierLines * lineH

        var t = edge(near.map { it.top }, top, limit, lower = true)
        var b = edge(near.map { it.bottom }, bottom, limit, lower = false)
        val withX = near.filter { it.left >= 0f }
        var l = if (hasX) edge(withX.map { it.left }, left, outlierX, lower = true) else -1f
        var r = if (hasX) edge(withX.map { it.right }, right, outlierX, lower = false) else -1f

        // příliš vysoká obálka (titulky na dvou místech) → jen právě zobrazený titulek
        if (b - t > maxHeight) { t = top; b = bottom; l = if (hasX) left else -1f; r = if (hasX) right else -1f }

        t = (t - padY).coerceAtLeast(maxOf(0f, read.top - padY))
        b = (b + padY).coerceAtMost(minOf(1f, read.bottom + padY))
        if (hasX) {
            l = (l - padX).coerceAtLeast(maxOf(0f, read.left - padX))
            r = (r + padX).coerceAtMost(minOf(1f, read.right + padX))
        }
        if (b <= t) return null
        val prev = held
        val area = if (prev != null && t < prev[1] + 0.05f && b > prev[0] - 0.05f && b.coerceAtLeast(prev[1]) - t.coerceAtMost(prev[0]) <= maxHeight + 2 * padY) floatArrayOf(
            minOf(prev[0], t), maxOf(prev[1], b),
            if (l >= 0f && prev[2] >= 0f) minOf(prev[2], l) else l,
            if (r >= 0f && prev[3] >= 0f) maxOf(prev[3], r) else r,
        ) else floatArrayOf(t, b, l, r)
        held = area
        return area.copyOf()
    }

    /** Pruh zmizel (začátek pauzy v rozhovoru). */
    @Synchronized
    fun hidden(now: Long) { if (hiddenAt < 0) hiddenAt = now }

    /** Nová hra / jiná oblast – zapomenout. */
    @Synchronized
    fun clear() { boxes.clear(); held = null; hiddenAt = -1 }

    /**
     * Krajní hrana: [current] se bere vždy; ze starších titulků jen ty, které nejsou odlehlé. Seřadí se od kraje
     * ([lower] = nejmenší hodnota je kraj) a z kraje se zahazuje hodnota, která je od další dál než [limit].
     */
    private fun edge(others: List<Float>, current: Float, limit: Float, lower: Boolean): Float {
        if (others.isEmpty()) return current
        val sorted = if (lower) others.sorted() else others.sortedDescending()
        var i = 0
        // nejvýš dvě odlehlá čtení, a jen když zbude aspoň jedno další
        while (i < 2 && i + 1 < sorted.size && kotlin.math.abs(sorted[i] - sorted[i + 1]) > limit) i++
        val e = sorted[i]
        // samotný starší titulek bez souseda: odlehlý, když je od právě zobrazeného dál než limit
        val outer = if (i + 1 >= sorted.size && kotlin.math.abs(e - current) > limit) current else e
        return if (lower) minOf(outer, current) else maxOf(outer, current)
    }
}
