package cz.recaption.core

/**
 * Titulky PS5 – titulek poznaný podle pruhu hry (přání majitele 25. 11. 2026: „ať se ví, co a kde překládat“).
 *
 * Hry jako GTA VI kreslí pod titulky poloprůhledný pruh: každý bod pod ním je ztlumený podle pevného vzorce
 * `uvnitř ≈ alpha · venku + offset` ([Look]; GTA VI změřeno ze snímků majitele: 0,334 · venku + 22,6, odchylka
 * v mediánu 1,9 úrovně jasu ve dne i v noci). Řádek textu, který nad sebou i pod sebou má hranu přesně tohoto ztlumení
 * přes většinu své šířky, je titulek; cedule, billboard, text v telefonu ani nápisy ovládání takovou hranu nemají
 * (na snímcích ≤ 0,6 na jedné straně a skoro 0 na druhé, titulky 0,85–1,0 na obou).
 *
 * Bez Androidu – jas bodů výřezu po řádcích (0–255).
 */
object SubtitleBox {
    data class Look(val alpha: Float, val offset: Float)

    /** Kolik bodů v řádku nad/pod textem musí sedět na vzorec. */
    const val MIN_FIT = 0.7f

    /** Sedí bod uvnitř pruhu [inside] na bod venku [outside]? (A jasy se liší – jinak sedí každá rovná plocha.) */
    fun fits(outside: Int, inside: Int, look: Look): Boolean {
        val want = look.alpha * outside + look.offset
        return Math.abs(inside - want) <= 4f + 0.05f * outside && Math.abs(outside - inside) >= 4
    }

    /** Podíl bodů hrany v řádku [y] (venku [y]∓[k], uvnitř [y]±[k]) mezi [a] a [b]. */
    private fun edge(luma: IntArray, w: Int, y: Int, a: Int, b: Int, k: Int, top: Boolean, look: Look): Float {
        val o = (if (top) y - k else y + k) * w
        val i = (if (top) y + k else y - k) * w
        var n = 0
        for (x in a until b) if (fits(luma[o + x], luma[i + x], look)) n++
        return n.toFloat() / (b - a)
    }

    /** Nejlepší shoda hrany nad ([top]) / pod řádkem [l..r]×[t..b] (body výřezu). */
    fun edgeScore(luma: IntArray, w: Int, h: Int, l: Int, t: Int, r: Int, b: Int, top: Boolean, look: Look): Float {
        if (w <= 0 || h <= 0 || luma.size < w * h) return 0f
        val lh = (b - t).coerceAtLeast(1)
        val k = maxOf(1, Math.round(lh / 10f))
        val a = (l + lh / 2).coerceIn(0, w); val e = (r - lh / 2).coerceIn(0, w)
        if (e - a < lh) return 0f
        val span = (1.2f * lh).toInt()
        val ys = if (top) (maxOf(k, t - span) until t - k + 1) else (b + k until minOf(h - k, b + span + 1))
        var best = 0f
        for (y in ys) best = maxOf(best, edge(luma, w, y, a, e, k, top, look))
        return best
    }

    /** Je řádek textu v pruhu titulků? */
    fun boxed(luma: IntArray, w: Int, h: Int, line: OcrLine, look: Look): Boolean =
        edgeScore(luma, w, h, line.left, line.top, line.right, line.bottom, true, look) >= MIN_FIT &&
            edgeScore(luma, w, h, line.left, line.top, line.right, line.bottom, false, look) >= MIN_FIT

    /**
     * Pamatuje si, jestli hra pruh pod titulky opravdu kreslí (hráč ho mohl ve hře vypnout). Dokud pruh nebyl vidět
     * (nebo není vidět [FORGET_MS]), nic se nevyřazuje – titulky bez pruhu se čtou jako dřív.
     */
    class Filter(private val look: Look) {
        @Volatile var lastSeen = 0L
            private set

        /**
         * Řádky nad pásem úkolu ([keepFrom] = horní hrana pásu v bodech výřezu, -1 = žádný), které nejsou v pruhu,
         * se vyřadí – jen když pruh hra kreslí. Vrací řádky a počet vyřazených.
         */
        fun apply(blocks: List<OcrBlock>, luma: IntArray, w: Int, h: Int, keepFrom: Int, now: Long): Pair<List<OcrBlock>, Int> {
            val inBox = HashSet<OcrLine>()
            for (b in blocks) for (l in b.lines) if (boxed(luma, w, h, l, look)) inBox += l
            if (inBox.isNotEmpty()) lastSeen = now
            if (lastSeen == 0L || now - lastSeen > FORGET_MS) return blocks to 0
            var dropped = 0
            val out = blocks.map { b ->
                OcrBlock(b.lines.filter { l ->
                    val keep = l in inBox || (keepFrom >= 0 && l.top >= keepFrom)
                    if (!keep) dropped++
                    keep
                })
            }.filter { it.lines.isNotEmpty() }
            return out to dropped
        }
    }

    /** Po takové době bez pruhu se zase čte všechno (hráč pruh ve hře vypnul). */
    const val FORGET_MS = 10 * 60_000L
}
