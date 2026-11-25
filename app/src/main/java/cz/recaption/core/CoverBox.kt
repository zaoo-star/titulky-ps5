package cz.recaption.core

/**
 * Kolik má pruh v poloze „Přes titulky“ zakrýt (od 0.97.56, tester: „občas je vidět původní anglický text“).
 *
 * Rámeček z rozpoznání textu sedí natěsno na písmenech – obrys a stín písma, háčky a krajní písmena z něj vyčuhují.
 * Proto se kolem anglického titulku přidá okraj ([padY] nahoře i dole, [padX] vlevo i vpravo, zlomky obrazu).
 * A během souvislého rozhovoru (pruh mezitím nezmizel) se zakrytá plocha jen zvětšuje: po delší replice následuje
 * kratší, pruh se zmenší a další delší anglický řádek by chvíli vyčuhoval, než se přeloží. Po schování pruhu se začíná
 * znovu ([reset]).
 */
class CoverBox(private val padY: Float = 0.012f, private val padX: Float = 0.02f) {
    private var box: FloatArray? = null

    /**
     * Anglický titulek [top]..[bottom] (a [left]..[right], -1 = neznámé) → plocha k zakrytí [nahoře, dole, vlevo, vpravo];
     * vlevo/vpravo -1, když vodorovný rozsah neznáme. null = titulek neznámý (pruh jde podle oblasti čtení jako dřív).
     */
    fun next(top: Float, bottom: Float, left: Float, right: Float): FloatArray? {
        if (top < 0f || bottom <= top) return null
        val hasX = left >= 0f && right > left
        val t = (top - padY).coerceAtLeast(0f)
        val b = (bottom + padY).coerceAtMost(1f)
        val l = if (hasX) (left - padX).coerceAtLeast(0f) else -1f
        val r = if (hasX) (right + padX).coerceAtMost(1f) else -1f
        val prev = box
        // plocha se jen zvětšuje, dokud je pruh vidět (a jen když je nový titulek zhruba na stejném místě)
        val merged = if (prev != null && t < prev[1] + 0.05f && b > prev[0] - 0.05f) floatArrayOf(
            minOf(prev[0], t), maxOf(prev[1], b),
            if (l >= 0f && prev[2] >= 0f) minOf(prev[2], l) else l,
            if (r >= 0f && prev[3] >= 0f) maxOf(prev[3], r) else r,
        ) else floatArrayOf(t, b, l, r)
        box = merged
        return merged.copyOf()
    }

    /** Pruh zmizel – další titulek začne s vlastní plochou. */
    fun reset() { box = null }
}
