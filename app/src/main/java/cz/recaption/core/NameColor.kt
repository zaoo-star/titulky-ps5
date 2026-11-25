package cz.recaption.core

/**
 * Barva jména mluvčího přímo z obrazu hry (od 0.97.28). Čtečka textu barvu nevrací a čte se jen jas,
 * proto se barva bere zvlášť: jen když se ukáže nový titulek se jménem, a jen z obdélníku slova se jménem
 * (rámeček slova od čtečky, zmenšený o okraje – [cz.recaption.StreamReader.requestColor]). Z bodů se vezmou
 * jen světlé a syté (výplň písma), bílé/šedé (vyhlazení okrajů, bílý text) a tmavé (obrys, pozadí) se zahodí;
 * barva = převažující odstín (histogram), z jeho bodů medián – ne průměr (ten míchal pozadí a bílou).
 * Bílé / šedé jméno = hra ho nebarví → nic.
 *
 * Naučená barva se drží (hra jméno v rozhovoru nepřebarví): jiná ji vystřídá až po [SWITCH_STREAK] čteních
 * za sebou nebo s [SWITCH_RATIO]× víc hlasy – jedno dvě divná čtení ji nezmění.
 *
 * Naučené barvy a měření času jdou do záznamu v síti (oddíl „barvy jmen ze hry“) – podle nich se barvy
 * přepíšou natvrdo do profilu hry ([GameProfile.colors]).
 */
object NameColor {
    /** Počet přihrádek odstínu (po 15°). */
    private const val BINS = 24
    /** Nejmenší rozdíl nejsilnější a nejslabší složky bodu, aby se počítal jako barevný. */
    private const val MIN_CHROMA = 45
    /** Za sebou tolik čtení jiné barvy → naučená barva se vymění. */
    const val SWITCH_STREAK = 4
    /** Nebo jiná barva má tolikrát víc hlasů než naučená. */
    const val SWITCH_RATIO = 3
    /** Naučená barva s tolika hlasy je jistá – jméno se už nečte. */
    const val SETTLED_VOTES = 6

    /**
     * Barva písma z bodů obdélníku (YUV). Jen světlé (vůči nejsvětlejším bodům) a syté body; podle odstínu
     * do [BINS] přihrádek vážených sytostí, vítězí okno tří sousedních přihrádek; výsledek = medián složek
     * sytější poloviny jeho bodů. null = bez barvy (bílé/šedé písmo, málo barevných bodů, směs barev).
     */
    fun fromSamples(ys: IntArray, us: IntArray, vs: IntArray, n: Int): Int? {
        if (n < 8) return null
        val rgb = IntArray(n); val mx = IntArray(n)
        for (i in 0 until n) {
            val c = Yuv.pixel(ys[i], us[i], vs[i]); rgb[i] = c
            mx[i] = maxOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
        }
        val ref = mx.copyOf().also { it.sort() }[(n * 9) / 10]
        val bright = maxOf(110, (ref * 0.55f).toInt())
        val bins = FloatArray(BINS)
        val bin = IntArray(n) { -1 }
        var white = 0; var colored = 0; var total = 0f
        for (i in 0 until n) {
            val m = mx[i]
            if (m < bright) continue
            val c = rgb[i]
            val chroma = m - minOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
            if (chroma < MIN_CHROMA || chroma < m * 0.2f) { if (chroma < 30) white++; continue }
            val b = (hue(c) / (360f / BINS)).toInt().coerceIn(0, BINS - 1)
            val wgt = chroma.toFloat() / m
            bin[i] = b; bins[b] += wgt; total += wgt; colored++
        }
        if (colored < maxOf(4, n / 60)) return null
        // bílé písmo s pár barevnými body pozadí (nebe, světla) není barevné jméno
        if (white > colored * 3) return null
        var best = 0; var bestW = -1f
        for (b in 0 until BINS) {
            val w = bins[(b + BINS - 1) % BINS] + bins[b] + bins[(b + 1) % BINS]
            if (w > bestW) { bestW = w; best = b }
        }
        if (bestW < total * 0.4f) return null
        val inWin = ArrayList<Int>()
        for (i in 0 until n) {
            val b = bin[i]
            if (b < 0) continue
            val d = Math.floorMod(b - best, BINS)
            if (d == 0 || d == 1 || d == BINS - 1) inWin.add(rgb[i])
        }
        if (inWin.size < 3) return null
        val chromaOf = { c: Int ->
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val bl = c and 0xFF
            maxOf(r, g, bl) - minOf(r, g, bl)
        }
        val top = inWin.sortedByDescending(chromaOf).take(maxOf(3, (inWin.size + 1) / 2))
        fun med(shift: Int) = top.map { (it shr shift) and 0xFF }.sorted()[top.size / 2]
        val out = (0xFF shl 24) or (med(16) shl 16) or (med(8) shl 8) or med(0)
        return if (chromaOf(out) >= MIN_CHROMA) out else null
    }

    /**
     * Obdélník slova zmenšený o okraje (vyhlazení, obrys, pozadí kolem písmen): [l, t, r, b], nebo null, když je
     * moc malý. Svisle o 18 % výšky (rámeček čtečky sahá od horních dotažnic po spodní), vodorovně o 10 % výšky.
     */
    fun inset(l: Int, t: Int, r: Int, b: Int): IntArray? {
        val h = b - t
        val dx = maxOf(1, h / 10); val dy = maxOf(1, h * 18 / 100)
        if (r - l - 2 * dx < 3 || h - 2 * dy < 3) return null
        return intArrayOf(l + dx, t + dy, r - dx, b - dy)
    }

    /**
     * Obdélník jména mluvčího [name] na řádku „Jméno: text“ přesně ze slov čtečky ([OcrLine.words]), bez dvojtečky
     * a zmenšený o okraje ([inset]); null = slova neznámá nebo nesedí (pak odhad podle počtu znaků).
     */
    fun nameBox(line: OcrLine, name: String): IntArray? {
        val n = name.trim().split(Regex("\\s+")).size
        if (line.words.size < n) return null
        val ws = line.words.take(n)
        if (!TextLogic.simplify(ws.joinToString(" ") { it.text }).startsWith(TextLogic.simplify(name))) return null
        val last = ws.last()
        var r = ws.maxOf { it.right }
        val lt = last.text.trimEnd()
        if (lt.length > 1 && lt.endsWith(":")) r -= (last.right - last.left) / lt.length
        return inset(ws.minOf { it.left }, ws.minOf { it.top }, r, ws.maxOf { it.bottom })
    }

    /** Je to opravdu barva (ne bílá/šedá)? Rozdíl nejsilnější a nejslabší složky. */
    fun isColored(rgb: Int): Boolean {
        val r = (rgb shr 16) and 0xFF; val g = (rgb shr 8) and 0xFF; val b = rgb and 0xFF
        return maxOf(r, g, b) - minOf(r, g, b) >= 50
    }

    /**
     * Nejbližší barva z palety pruhu (čitelná na černém podkladu) – podle **odstínu**, ne podle rozdílu RGB.
     * Písmo jména je na okrajích vyhlazené do bílé, takže z obrazu vychází bledá barva (žluté jméno
     * #DBD899 místo sytě žluté). Podle RGB byla bledě žlutá „blíž“ růžové #F59A9A a všechna jména byla
     * růžová (0.97.28–0.97.31, záznam majitele 20. 11. 2026). Odstín se bledostí nemění.
     */
    fun nearest(rgb: Int, palette: IntArray = SpeakerColors.PALETTE): Int {
        val h = hue(rgb)
        return palette.minByOrNull { p -> val d = Math.abs(hue(p) - h); minOf(d, 360f - d) } ?: palette[0]
    }

    /** Odstín 0–360° (0 červená, 60 žlutá, 120 zelená, 240 modrá). */
    fun hue(rgb: Int): Float {
        val r = (rgb shr 16) and 0xFF; val g = (rgb shr 8) and 0xFF; val b = rgb and 0xFF
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        if (max == min) return 0f
        val d = (max - min).toFloat()
        val h = when (max) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return if (h < 0) h + 360f else h
    }

    // ---- naučené barvy a měření (pro záznam v síti) ----

    /** Hlasy jednoho jména (barva z palety → počet), naučená barva a „vyzyvatel“ s počtem čtení za sebou. */
    private class Votes {
        val count = HashMap<Int, Int>()
        /** Součty skutečně změřených barev po barvách palety (r, g, b) – naučená barva je jejich průměr. */
        val sums = HashMap<Int, LongArray>()
        var color: Int? = null
        var rival: Int? = null
        var streak = 0
    }

    private val seen = LinkedHashMap<String, Votes>()
    private var samples = 0
    private var sumMicros = 0L
    private var maxMicros = 0L

    /** Změřené čtení barvy (čas v µs) a výsledek (null = jméno bez barvy). */
    @Synchronized fun record(name: String, rgb: Int?, micros: Long) {
        samples++; sumMicros += micros; if (micros > maxMicros) maxMicros = micros
        if (rgb == null) return
        val v = seen.getOrPut(SpeakerColors.key(name)) { Votes() }
        val p = nearest(rgb)
        val n = (v.count[p] ?: 0) + 1
        v.count[p] = n
        v.sums.getOrPut(p) { LongArray(3) }.let { it[0] += (rgb shr 16) and 0xFF; it[1] += (rgb shr 8) and 0xFF; it[2] += rgb and 0xFF }
        val cur = v.color
        if (cur == null) {
            // první naučení: aspoň 2 shody a aspoň dvakrát víc než kterákoli jiná barva
            val second = v.count.filterKeys { it != p }.values.maxOrNull() ?: 0
            if (n >= 2 && n >= 2 * second) v.color = p
        } else if (p == cur) {
            v.rival = null; v.streak = 0
        } else {
            if (p == v.rival) v.streak++ else { v.rival = p; v.streak = 1 }
            if (v.streak >= SWITCH_STREAK || n >= SWITCH_RATIO * (v.count[cur] ?: 0)) {
                v.color = p; v.rival = null; v.streak = 0
            }
        }
        if (seen.size > 200) seen.remove(seen.keys.first())
    }

    /**
     * Naučená barva jména, nebo null. Drží se, dokud ji jiná jasně nepřehlasuje ([record]). Od 0.97.109 **skutečná barva
     * ze hry** (průměr změřených barev, které hlasovaly pro vítěznou barvu palety) – dřív barva palety, takže broskvové jméno
     * vyšlo stejně zlaté jako světle žluté a mátové tyrkysové (záznam testera). Paleta dál slouží k hlasování.
     */
    @Synchronized fun learned(name: String): Int? = seen[SpeakerColors.key(name)]?.let { v ->
        val c = v.color ?: return@let null
        val s = v.sums[c]; val n = v.count[c] ?: 0
        if (s == null || n <= 0) c else (0xFF shl 24) or ((s[0] / n).toInt() shl 16) or ((s[1] / n).toInt() shl 8) or (s[2] / n).toInt()
    }

    /** Naučená barva je jistá ([SETTLED_VOTES] shod) – jméno už není třeba číst. */
    @Synchronized fun settled(name: String): Boolean =
        seen[SpeakerColors.key(name)]?.let { v -> v.color?.let { (v.count[it] ?: 0) >= SETTLED_VOTES } } ?: false

    /** Barvy z profilu právě hrané hry – v záznamu se u nich ukáže neshoda s obrazem. */
    @Volatile var profile: Map<String, Int> = emptyMap()

    /** Pro záznam: měření, naučené barvy („jason #F2C14E ×12“) a neshody s profilem hry (oprava profilu). */
    @Synchronized fun report(): String {
        val avg = if (samples > 0) sumMicros / samples else 0
        val sb = StringBuilder("čtení barvy: $samples×, průměr $avg µs, nejdéle $maxMicros µs\n")
        for ((k, v) in seen) {
            sb.append(k).append(' ').append(v.count.entries.sortedByDescending { it.value }
                .joinToString(", ") { SpeakerColors.hex(it.key) + " ×" + it.value })
            val p = profile[k]
            val best = v.color
            if (best != null) sb.append("  → ").append(SpeakerColors.hex(best)).append(" (ve hře ").append(learned(k)?.let { SpeakerColors.hex(it) } ?: "?").append(")")
            if (p != null && best != null && best != p) sb.append("  ← NESHODA: profil ").append(SpeakerColors.hex(p))
                .append(", ve hře ").append(SpeakerColors.hex(best))
            sb.append('\n')
        }
        return sb.toString().trimEnd()
    }

    @Synchronized fun reset() { seen.clear(); samples = 0; sumMicros = 0; maxMicros = 0 }
}
