package cz.recaption.core

/**
 * Panel s překladem telefonu „na volné straně“ (přání majitele 24. 11. 2026, inspirace GlossaSub): panel nestojí těsně
 * vedle telefonu ve hře ([PhoneScreen.panel]), ale na opačné polovině obrazu – telefon vpravo → panel u levého okraje
 * a naopak –, svisle zhruba ve výšce telefonu a nejníž [BOTTOM] (pruh s titulky dole zůstane volný). Originální telefon
 * tak zůstane celý vidět.
 *
 * Proti houpání: strana se přepne, až když střed telefonu přejde polovinu obrazu aspoň o [HYST], a výška se mění, až
 * když se horní okraj telefonu posune aspoň o [STILL] – drobný pohyb telefonu (chůze, auto) panel nehýbe vůbec.
 * Plynulý přechod na druhou stranu dělá služba přes [PhoneFollow] (posun od staršího místa k novému).
 *
 * Místo = (vlevo, nahoře, vpravo, nejníž dole) ve zlomcích obrazu jako u [PhoneScreen.panel].
 */
class PhoneFreeSide {
    /** Poslední vrácené místo panelu, null = zatím žádné (nebo telefon uprostřed – viz [place]). */
    var box: FloatArray? = null
        private set

    /**
     * Místo panelu pro telefon [phone] (obdélník textu telefonu, zlomky obrazu). null = na volnou stranu se panel
     * nevejde (telefon přes střed obrazu) – služba pak použije panel vedle telefonu jako dřív.
     */
    fun place(phone: FloatArray): FloatArray? {
        if (phone.size < 4) return null
        val cx = (phone[0] + phone[2]) / 2f
        val prev = box
        val wasLeft = prev?.let { onLeft(it) }
        val left = when (wasLeft) {
            true -> cx > MID - HYST     // panel vlevo zůstává, dokud telefon výrazně nepřejde do levé poloviny
            false -> cx >= MID + HYST
            null -> cx >= MID
        }
        val l: Float
        val r: Float
        if (left) { l = EDGE; r = minOf(EDGE + W, phone[0] - GAP) }
        else { r = 1f - EDGE; l = maxOf(1f - EDGE - W, phone[2] + GAP) }
        if (r - l < MIN_W) { box = null; return null }
        val top = (phone[1] - 0.03f).coerceIn(TOP_MIN, TOP_MAX)
        val t = if (prev != null && wasLeft == left && kotlin.math.abs(prev[1] - top) < STILL) prev[1] else top
        // šířka se drží, dokud se nezmění o víc než [STILL] (telefon u středu by ji jinak pořád měnil)
        val keepW = prev != null && wasLeft == left && kotlin.math.abs((prev[2] - prev[0]) - (r - l)) < STILL
        val b = if (keepW && prev != null) floatArrayOf(prev[0], t, prev[2], BOTTOM) else floatArrayOf(l, t, r, BOTTOM)
        box = b
        return b
    }

    fun reset() { box = null }

    companion object {
        /** Šířka panelu (o něco širší než panel vedle telefonu – na volné straně je místo). */
        const val W = 0.26f
        /** Okraj od kraje obrazu. */
        const val EDGE = 0.03f
        /** Nejmenší mezera mezi panelem a telefonem. */
        const val GAP = 0.03f
        /** Užší panel než tohle se na volnou stranu nedá (telefon přes střed obrazu). */
        const val MIN_W = 0.16f
        const val MID = 0.5f
        /** O kolik musí střed telefonu přejít polovinu obrazu, aby se panel přesunul na druhou stranu. */
        const val HYST = 0.06f
        /** Menší posun horního okraje telefonu panel svisle nehýbe. */
        const val STILL = 0.05f
        const val TOP_MIN = 0.08f
        const val TOP_MAX = 0.5f
        /** Nejníž dole (nad pruhem s titulky, jako [PhoneScreen.panel]). */
        const val BOTTOM = 0.80f

        /** Panel stojí v levé polovině obrazu. */
        fun onLeft(box: FloatArray): Boolean = box[0] + box[2] < 1f
    }
}
