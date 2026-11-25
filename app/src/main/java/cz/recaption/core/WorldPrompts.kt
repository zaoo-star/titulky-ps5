package cz.recaption.core

/**
 * Pokyn u předmětu přímo v obraze hry (GTA VI: „□ OPEN DOOR“ u dveří auta, od 0.97.88, volba Titulky → Hra
 * „Pokyny u předmětů“, výchozí vypnuto). Ikona tlačítka a krátký text VERZÁLKAMI kdekoli uprostřed obrazu.
 * Poloha jsou zlomky obrazu a jen text (bez ikony), přes něj se položí překlad.
 */
data class WorldPrompt(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float,
                        /** Výška jednoho řádku pro velikost písma (0 = [bottom]-[top], jako dřív) – u víceřádkového
                         *  rámečku (náhled SMS, [PhoneNotice.sms]) by jinak písmo vyšlo přes celou výšku bubliny. */
                        val lineH: Float = 0f)

object WorldPrompts {
    /**
     * Kde se hledá: střed obrazu bez horního pruhu (upozornění), bez pásu titulků dole, minimapy vlevo dole a menu
     * vpravo dole. Podle snímku testera (ZSDS-5MF6, 22. 11. 2026): pokyn ~53 % výšky, 40–50 % šířky, písmo ~2 % výšky.
     */
    const val ZONE_L = 0.12f
    const val ZONE_T = 0.12f
    const val ZONE_R = 0.88f
    // pokyny bývají i níž (≈ 0,80–0,83, „DO ONE FOR DONNIE“, dávka KJ7U-2FHD; od 0.97.137, dřív 0,72) – titulky
    // jsou malými písmeny, ty pokyn (verzálky s ikonou) nevezme
    const val ZONE_B = 0.86f
    /** Nejvýš pokynů (okna pruhu: ještě jedno navíc na upozornění telefonu, [PhoneNotice]). */
    const val MAX = 3

    /** Výška rámečku řádku ve zlomku obrazu (text ~2 %, rámeček z OCR o něco vyšší). */
    const val MIN_H = 0.012f
    const val MAX_H = 0.045f

    /** Ikona tlačítka, jak ji čtečka občas přečte jako první „slovo“ („© OPEN DOOR“, „(O) ENTER“). */
    private val ICON = Regex("^(?:[©®@○◯□■◻▢◎●OoQ0]|\\(\\S{0,2}\\)|\\[\\S{0,2}\\])\\s+")
    private val WORD = Regex("^[A-Z][A-Z'’-]*$")

    /**
     * Text pokynu bez ikony („© OPEN DOOR“ → „OPEN DOOR“) a jestli ikonu přečetla čtečka. null = nevypadá jako
     * pokyn: 1–4 slova jen z písmen VERZÁLKAMI, aspoň 4 písmena.
     */
    fun clean(raw: String): Pair<String, Boolean>? {
        val t = raw.trim()
        val m = ICON.find(t)
        val text = (if (m != null) t.substring(m.range.last + 1) else t).trim()
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 4) return null
        if (words.any { !WORD.matches(it) }) return null
        if (words.sumOf { w -> w.count { it.isLetter() } } < 4) return null
        return words.joinToString(" ") to (m != null)
    }

    /**
     * Ikona tlačítka vlevo od textu podle jasu (čtečka ji většinou nepřečte; kolečko je světle šedé ~175–195, ne bílé):
     * zprava od textu mezera bez světlých bodů, pak souvislý pruh sloupců se světlými body široký i vysoký 1,0–2,4 výšky
     * řádku a svisle na středu řádku. [gray] = jasová rovina výřezu (0–255 v dolním bajtu), souřadnice v pixelech výřezu.
     *
     * Mezi ikonou a textem bývá ještě malá barevná značka (tečka, kolečko s „i“) – dávka 7N4Y-RTQ8, snímky 48–51
     * (1280×720, řádek 20 bodů): text od x 362, tečka 350–358 (půl řádku), mezera, ikona 316–338. Do 1.00-rc15 se s ní
     * nepočítalo: mezera mezi textem a „ikonou“ vyšla moc malá a pokyn se nepoznal nikdy (13 dávek se zapnutou volbou,
     * žádný přeložený pokyn). Úzký světlý pruh (pod 0,8 řádku) hned u textu se proto přeskočí a ikona se hledá za ním.
     */
    fun iconLeft(gray: IntArray, w: Int, h: Int, left: Int, top: Int, bottom: Int): Boolean {
        val lh = bottom - top
        if (lh <= 3 || left <= 0 || left > w) return false
        val xs = (left - lh * 3.8f).toInt().coerceAtLeast(0)
        val ys = (top - lh * 0.6f).toInt().coerceAtLeast(0)
        val ye = (bottom + lh * 0.6f).toInt().coerceAtMost(h)
        if (ye <= ys || left - xs < lh) return false
        fun lightRows(x: Int): IntArray {   // [počet, nejvyšší, nejnižší]
            var n = 0; var lo = Int.MAX_VALUE; var hi = -1
            for (y in ys until ye) if ((gray[y * w + x] and 0xFF) >= 160) { n++; if (y < lo) lo = y; if (y > hi) hi = y }
            return intArrayOf(n, lo, hi)
        }
        // světlé pruhy od textu doleva; značka mezi ikonou a textem má jas na hraně a rozpadá se na úzké kousky –
        // ty se přeskočí (užší než 0,8 řádku a nejvýš 1,1 vysoké), první širší pruh musí být ikona
        var x = left - 1
        var marks = 0
        while (true) {
            while (x >= xs && lightRows(x)[0] < 2) x--
            if (x < xs) return false
            val end = x
            var lo = Int.MAX_VALUE; var hi = -1
            while (x >= xs) {
                val r = lightRows(x)
                if (r[0] < 2) break
                if (r[1] < lo) lo = r[1]; if (r[2] > hi) hi = r[2]
                x--
            }
            if (x < xs) return false   // pruh sahá až za okraj hledání – není to samostatná ikona
            val width = end - x
            val span = hi - lo + 1
            val gap = left - 1 - end     // od textu k pravému okraji pruhu
            if (width < lh * 0.8f && span <= lh * 1.1f) {
                // kousek značky: jen blízko textu (do 1,6 řádku), jinak je to něco jiného ve scéně
                if (gap > lh * 1.6f || ++marks > 6) return false
                continue
            }
            val mid = (hi + lo) / 2f - (top + bottom) / 2f
            // bez značky: mezera 0,2–1,4 řádku a ikona aspoň 0,9 řádku (dřív 1,0 – tmavá značka u textu se jako světlý
            // kousek nepozná a tlačítko o světlém středu 0,95 řádku pak neprošlo); se značkou může být světlý střed
            // tlačítka menší (0,85–0,95 řádku, celé tlačítko i s tmavým prstencem 1,4) a dál od textu (do 2,2 řádku)
            val min = if (marks > 0) 0.8f else 0.9f
            val far = if (marks > 0) 2.2f else 1.4f
            return gap >= lh * 0.2f && gap <= lh * far &&
                width >= lh * min && width <= lh * 2.4f && span >= lh * min && span <= lh * 2.4f &&
                kotlin.math.abs(mid) <= lh * 0.4f
        }
    }

    /**
     * Pokyny ze řádků OCR (souřadnice v pixelech celého obrazu [fullW]×[fullH]): krátký světlý text VERZÁLKAMI s ikonou
     * (přečtenou, nebo podle [icon] z jasu). Nejvýš [MAX], shora dolů.
     */
    fun read(lines: List<MenuLine>, fullW: Int, fullH: Int, icon: (MenuLine) -> Boolean): List<WorldPrompt> {
        if (fullW <= 0 || fullH <= 0) return emptyList()
        val out = ArrayList<WorldPrompt>()
        for (l in lines) {
            val hf = (l.bottom - l.top).toFloat() / fullH
            if (hf < MIN_H || hf > MAX_H) continue
            if (l.bright in 0..169) continue   // pokyny jsou bílé
            val (text, iconRead) = clean(l.text) ?: continue
            // text bez ikony: když čtečka ikonu přečetla jako slovo, začíná text až druhým slovem
            val textLeft = if (iconRead && l.words.size >= 2) l.words[1].left else l.left
            if (!iconRead && !icon(l)) continue
            out += WorldPrompt(text, textLeft.toFloat() / fullW, l.top.toFloat() / fullH, l.right.toFloat() / fullW, l.bottom.toFloat() / fullH)
        }
        return out.sortedBy { it.top }.take(MAX)
    }
}

/**
 * Pokyny v čase: nová sada textů se ukáže až po dvou stejných čteních (obraz hry je plný nápisů), poloha se u stejné
 * sady mění hned (kamera se hýbe); zmizí po [MISSES] čteních bez nich.
 */
class WorldTracker {
    private var shown: List<WorldPrompt> = emptyList()
    private var pending: String? = null
    private var misses = 0

    data class Result(val prompts: List<WorldPrompt>, val changed: Boolean)

    val current: List<WorldPrompt> get() = shown

    private fun key(p: List<WorldPrompt>) = p.joinToString("|") { it.text }

    fun update(read: List<WorldPrompt>): Result {
        if (read.isEmpty()) {
            pending = null
            if (shown.isEmpty()) return Result(shown, false)
            if (++misses < MISSES) return Result(shown, false)
            shown = emptyList(); misses = 0
            return Result(shown, true)
        }
        misses = 0
        val k = key(read)
        if (shown.isNotEmpty() && key(shown) == k) {
            val moved = read.zip(shown).any { (a, b) -> kotlin.math.abs(a.left - b.left) > 0.004f || kotlin.math.abs(a.top - b.top) > 0.004f }
            if (moved) shown = read
            return Result(shown, moved)
        }
        if (pending != k) { pending = k; return Result(shown, false) }
        pending = null
        shown = read
        return Result(shown, true)
    }

    fun reset() { shown = emptyList(); pending = null; misses = 0 }

    companion object { const val MISSES = 2 }
}
