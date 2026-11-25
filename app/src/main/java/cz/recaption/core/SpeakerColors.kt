package cz.recaption.core

/**
 * Barvy jmen mluvčích v pruhu: každá postava má svou, aby se v rozhovoru dalo poznat, kdo mluví.
 *
 * Stejné jméno má po celou dobu čtení stejnou barvu ("MIRA" i "Mira" jsou jedna postava).
 * První mluvčí dostane žlutou jako dřív; další barva se vybere podle jména (pořád stejně),
 * ale tak, aby se nepotkala s barvou nikoho z posledních mluvčích. Všechny barvy jsou světlé,
 * dobře čitelné na černém pruhu, a liší se od bílého textu i šedého originálu.
 */
class SpeakerColors(
    /**
     * Pevné barvy postav z profilu hry ([GameProfile.colorsByKey]: zjednodušené jméno i přezdívky → barva), jako
     * ve hře (např. jedna postava žlutě, druhá tyrkysově). Ostatní postavy dostanou barvu z palety, jinou než tyto.
     */
    private val preset: Map<String, Int> = emptyMap(),
) {

    companion object {
        /** Žlutá (dosavadní barva jmen), modrá, zelená, růžová, fialová, oranžová, tyrkysová.
         * Růžová (1.0.3, hlášení 8JJX-5P5Q): odstín #F59A9A byl spíš lososový, skutečná růžová barva úkolu ve hře
         * (GTA VI) je sytější, blíž purpurové – změřeno ze snímku na ~#F981B1. */
        val PALETTE: IntArray = longArrayOf(
            0xFFF2C14E, 0xFF7FC8F8, 0xFF8EDB8A, 0xFFF981B1, 0xFFC7A6F7, 0xFFF5A55A, 0xFF6FE0D2,
        ).map { it.toInt() }.toIntArray()

        private const val MAX_NAMES = 200

        /** Jméno sjednocené pro porovnání (velikost písmen, diakritika, interpunkce). */
        fun key(name: String): String = TextLogic.simplify(name)

        /** Barva jako "#RRGGBB" (pro televizi LG). */
        fun hex(color: Int): String = String.format("#%06X", color and 0xFFFFFF)
    }

    // v pořadí, jak naposledy mluvili (nejnovější na konci)
    private val known = LinkedHashMap<String, Int>(16, 0.75f, true)

    @Synchronized fun colorOf(name: String): Int {
        val k = key(name)
        if (k.isEmpty()) return PALETTE[0]
        learnedColors[k]?.let { return it }
        preset[k]?.let { return it }
        known[k]?.let { return it }
        // paleta bez barev, které mají postavy z profilu hry (ať se nepletou)
        val palette = PALETTE.filter { it !in preset.values }.ifEmpty { PALETTE.toList() }
        val recent = known.values.toList().takeLast(palette.size - 1).toSet()
        val start = if (known.isEmpty()) 0 else Math.floorMod(k.hashCode(), palette.size)
        var color = palette[start]
        for (i in palette.indices) {
            val c = palette[(start + i) % palette.size]
            if (c !in recent) { color = c; break }
        }
        known[k] = color
        if (known.size > MAX_NAMES) known.remove(known.keys.first())
        return color
    }

    /** Barvy naučené z obrazu hry – mají přednost i před profilem (hra po aktualizaci mohla barvy změnit). */
    private val learnedColors = HashMap<String, Int>()

    /** Barva naučená z obrazu hry (≥ 2 shody) – nahradí přidělenou i barvu z profilu hry. */
    @Synchronized fun learn(name: String, color: Int) {
        val k = key(name)
        if (k.isEmpty()) return
        learnedColors[k] = color
        known[k] = color
    }

    /** Barvy všech mluvčích v pruhu (jméno → barva). */
    fun colorsFor(segments: List<Segment>): Map<String, Int> =
        segments.mapNotNull { it.speaker }.distinct().associateWith { colorOf(it) }
}
