package cz.recaption.core

/**
 * Menu vpravo dole (nápovědy tlačítek při hraní, zatím GTA VI – „DISABLE AUTO DRIVE“, „SPEED 1/2/3“, „CAMERA 1/2/3“,
 * u postav „TALK / TAUNT / HOLD HANDS / JASON“, fotoaparát v telefonu „TAKE PHOTO … ZOOM (X1.0) … EXIT“ – 8 položek,
 * horní na ~62,5 % výšky). Podle snímků testerů (dávky v `zaznamy/hlaseni-…`, obraz 16 : 9):
 *  - svislý seznam vpravo dole, VERZÁLKY bílým úzkým písmem, zarovnané doprava, vpravo ikona tlačítka,
 *  - pravý okraj textu ~95,8 % šířky, spodní řádek střed ~96 % výšky, rozestup ~4,8 % (u postav nad čarou ~6,4 %),
 *  - neaktivní položka je šedá (jas písma ~115 proti ~250),
 *  - ve stejném rohu bývá v menu vodorovná lišta (PHOTO STUDIO · SELECT · BACK) – ta se nepřekládá (null).
 * Nic se v něm nevybírá – jen se překládá, kdykoli je vidět, i souběžně s titulky a úkolem.
 * Ikona tlačítka u položky ([CornerItem.icon]): L1/R1/L2/R2 z textu, kotouč × ○ □ △ z jasu výřezu ([ButtonIcons]);
 * ukáže se za překladem jen „nad položkami“ ([icons]).
 * Volba Titulky → Hra → „Menu vpravo dole“ (výchozí vypnuto). Bez Androidu – testy `CornerPromptsTest`.
 */
class CornerSpec(
    /** Známé položky verzálkami, bez přípony „1/2/3“ / „(X1.0)“ / „(1/17)“ (ta se nepřekládá). */
    val items: List<String>,
    /** Pevné překlady: kód jazyka → (položka → překlad). Jiné jazyky přeloží překladač. */
    val words: Map<String, Map<String, String>> = emptyMap(),
    /** Jména postav v seznamu (JASON) – známá, nepřekládají se. */
    val keep: List<String> = emptyList(),
) {
    // jména postav až od 3 písmen (od 1.0.04): dvoupísmenná přezdívka z profilu („LU“) v seznamu ovládání nebývá a useknuté
    // čtení položky („LU…“ z „LURE FISH“, dávka 38K7-5K4G) by se za ni vydávalo – jméno se nepřekládá a seznam by „znal“
    internal val keys: Map<String, String> = (items + keep.filter { CornerPrompts.compact(it).length >= 3 })
        .associateBy { CornerPrompts.compact(it) }.filterKeys { it.length >= 2 }

    /** Pevný překlad položky (jméno zůstává), nebo null (pak ji přeloží překladač). */
    fun word(lang: Lang, item: String): String? {
        if (keep.any { it.equals(item, ignoreCase = true) }) return item
        return words[lang.code]?.entries?.firstOrNull { it.key.equals(item, ignoreCase = true) }?.value
    }
}

/**
 * Položka seznamu: [text] = položka ze slovníku (u neznámé přečtený text), [suffix] = „1/2/3“, „(X1.0)“, „(1/17)“,
 * „($10)“ (beze změny),
 * [dim] = šedá (neaktivní), [known] = ze slovníku profilu. Poloha přečteného textu ve zlomcích obrazu.
 * [icon] = ikona tlačítka vpravo od textu ([ButtonIcons]: „x“, „o“, „s“, „t“, nebo z textu „L1“ / „R2“, víc ikon
 * mezerou „L2 R2“); null = nevíme.
 */
data class CornerItem(
    val text: String, val suffix: String, val dim: Boolean, val known: Boolean,
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    val icon: String? = null,
) {
    val center: Float get() = (top + bottom) / 2f
}

/**
 * Seznam vpravo dole v pořadí shora dolů. [step] = rozestup řádků (zlomek výšky), [top] = horní okraj první položky,
 * [right] = pravý okraj textu (zlomky obrazu).
 */
data class CornerView(val items: List<CornerItem>, val step: Float, val top: Float, val right: Float) {
    /** Jen na porovnání, jestli se seznam změnil. */
    val key: String get() = items.joinToString("|") { it.text + (if (it.suffix.isEmpty()) "" else " " + it.suffix) + (if (it.dim) "~" else "") }

    /** Ikony tlačítek po položkách (jen na porovnání). */
    val iconKey: String get() = items.joinToString("|") { it.icon ?: "" }

    /** Položky bez ikony z textu doplní [find] (ikona z obrazu, [ButtonIcons.find]); null = nevíme. */
    fun withIcons(find: (CornerItem) -> String?): CornerView =
        copy(items = items.map { if (it.icon != null) it else it.copy(icon = find(it) ?: CornerPrompts.DEFAULT_ICONS[it.text]) })
}

object CornerPrompts {
    /**
     * Ikona položky, kterou čtečka ani jas nepoznají (od 1.00-rc21, majitel: „ikona pro tlačítko R nebo L se šipkami“):
     * SCROLL v telefonu GTA VI = pravá páčka (R v kolečku se šipkami).
     */
    val DEFAULT_ICONS = mapOf("SCROLL" to ButtonIcons.RIGHT_STICK)

    /** Výřez pro čtení (zlomky obrazu): vpravo dole. Shora 0,60 – fotoaparát má 8 položek, horní na ~0,625. */
    const val ZONE_LEFT = 0.60f
    const val ZONE_TOP = 0.60f
    /**
     * Roh vyhrazený ovládání (od 1.0.04, dávka 38K7-5K4G – rybaření: „HOOK FISH / LURE FISH / RESET CAST / GRIP“ se bralo
     * jako upozornění telefonu a kusy řádků jako titulek): řádek, který **začíná** vpravo od 0,78 šířky a pod 0,60 výšky,
     * patří ovládání (nebo upozornění telefonu, to má vlastní čtení) – titulek ani úkol tam nezačínají (jsou na středu).
     * [left], [top] = levý horní roh řádku ve zlomcích obrazu.
     */
    const val RESERVED_LEFT = 0.78f
    fun reserved(left: Float, top: Float): Boolean = left >= RESERVED_LEFT && top >= ZONE_TOP

    /**
     * Řádek ovládání vpravo dole podle tvaru (bez slovníku): verzálky, nejvýš 4 slova, konec u sloupce ikon. Upozornění
     * telefonu (jméno odesílatele / text zprávy malými písmeny) takhle nevypadá – [PhoneNotice] takové řádky nebere.
     */
    fun looksLikePrompt(l: MenuLine, w: Int, h: Int): Boolean {
        if (w <= 0 || h <= 0 || !reserved(l.left.toFloat() / w, l.top.toFloat() / h)) return false
        val ls = l.text.filter { it.isLetter() }
        if (ls.length < 3 || l.text.trim().split(Regex("\\s+")).size > 5) return false
        return ls.count { it.isUpperCase() } >= ls.length * 0.8f && l.right >= w * 0.90f
    }

    /** Jas písma (90. percentil v rámečku) pod touhle hranicí = šedá neaktivní položka. */
    const val DIM = 190
    /** Nejvíc položek (fotoaparát v telefonu jich má 8). */
    const val MAX_ITEMS = 8
    /** Rozestup, když je vidět jen jedna položka. */
    const val DEFAULT_STEP = 0.048f
    /** Obecné položky (telefon ve hře, menu): samy o sobě (jediná) seznam netvoří – je jich potřeba aspoň dvě známé. */
    val GENERIC = setOf("BACK", "SELECT", "EXIT")

    /** Držený čitelný předmět (plakát, leták – GTA VI), ne telefon: v seznamu je „PUT DOWN“ místo „BACK“/„EXIT“
     *  (dávky 5DY4-ZRGC, MT9Z-KGY9 – appka ho četla jako otevřený telefon, poloha i tvar jsou stejné jako u telefonu). */
    fun holdingItem(view: CornerView?): Boolean = view?.items?.any { it.text.equals("PUT DOWN", ignoreCase = true) } == true

    /**
     * Levná brána před čtením: pásy ve sloupci ikon (x 0,962–0,984) kolem řádků seznamu. Když se jejich jas od minula
     * nezměnil a seznam není vidět, čtení se přeskočí (nejdéle 3 s).
     */
    val GATE: List<FloatArray> = listOf(0.960f, 0.912f, 0.864f, 0.816f, 0.768f, 0.720f, 0.672f, 0.624f).map { c -> floatArrayOf(0.962f, c - 0.012f, 0.984f, c + 0.012f) }

    /** Řádek, který vypadá jako vodorovná lišta v menu (položky vedle sebe) – pak se nepřekládá nic. */
    private val BAR = CornerItem("", "", false, false, 0f, 0f, 0f, 0f)

    /** „SPEED 1/2/3“, „1J2“, „I/2“, „1/ 2“: čísla voleb za položkou. */
    private val SUFFIX = Regex("(?<=[A-Za-z\\s])\\s*([0-9IlZ])\\s*[/J]\\s*([0-9IlZ])(?:\\s*[/J]\\s*([0-9IlZ]))?")
    /**
     * Hodnota v závorce za položkou („ZOOM (X1.0)“, „FILTER (1/17)“, „GALLERY (3/32)“) – nepřekládá se. Uvnitř jen číslice
     * (čtečka čte i l/I/O), tečka, čárka, lomítko a x na začátku; aspoň jedna číslice a tečka, lomítko nebo x.
     */
    private val PAREN = Regex("\\s*\\(\\s*([xX×]?\\s*[0-9IlOo][0-9IlOo.,/ ]*?)\\s*[)）]")
    /** Cena za položkou („TIP ($10)“, čtečka čte i „(S10）“) – nepřekládá se. */
    private val PRICE = Regex("\\s*\\(\\s*[\$S]\\s*([0-9][0-9,.]*)\\s*[)）]?")
    /** Za příponou smí být jen zbytky ikon („SPEED1/2/3L2R2“ – dvě ikony slepené). */
    private val REST = Regex("^([lr][12li]|[o0x9c])*$")
    /** Zbytky ikony tlačítka za textem: „(9)“, „©“, „@“, „L2“, „R1“, „Ll“, „X“, „O“. */
    private val JUNK = Regex("^(\\(.{0,3}\\)?|\\(|[©@®°•×△□○◯]+|[OoXx0]|[LR][12lI]|Ll|Rl)$")
    /** Totéž přilepené k poslednímu slovu (po [compact]): „TAUNTO“, „MARKERSR1“. */
    private val TAIL = Regex("^([o0x9c]|[lr][12li])$")

    /** Zjednodušený tvar bez mezer („DISABLEAUTO DRIVE“ = „DISABLE AUTO DRIVE“). */
    fun compact(s: String): String = TextLogic.simplify(s).replace(" ", "")

    /** Rozpozná seznam z řádků výřezu (souřadnice v celém obrazu [w] × [h]). null = žádný seznam. */
    fun read(lines: List<MenuLine>, w: Int, h: Int, spec: CornerSpec): CornerView? {
        if (w <= 0 || h <= 0) return null
        val zone = lines.filter { it.top >= h * ZONE_TOP && it.right > w * 0.5f }
        val cands = ArrayList<Pair<CornerItem, MenuLine>>()
        for (l in zone) {
            val c = item(l, w, h, spec) ?: continue
            if (c === BAR) return null
            cands += c to l
        }
        if (cands.isEmpty()) return null
        val sorted = cands.sortedByDescending { it.first.bottom }
        val bottom = sorted.firstOrNull { it.first.center in 0.94f..0.975f } ?: return null
        val stack = arrayListOf(bottom)
        var prev = bottom.first
        for (c in sorted) {
            if (stack.size >= MAX_ITEMS) break
            val it = c.first
            if (c === bottom || it.center >= prev.center - 0.02f) continue
            if (kotlin.math.abs(it.right - bottom.first.right) > 0.03f) continue
            val gap = prev.center - it.center
            // rozestup položek, nebo jedna položka vynechaná čtením
            if (gap in 0.040f..0.070f || gap in 0.085f..0.135f) { stack += c; prev = it }
            else if (gap > 0.135f) break
        }
        // aspoň jedna známá položka; obecné (BACK, SELECT, EXIT – bývají skoro všude) stačí jen dvě
        val known = stack.filter { it.first.known }.map { it.first.text }
        if (known.isEmpty() || (known.all { it in GENERIC } && known.size < 2)) return null
        // vodorovná lišta: vedle položky (vlevo od ní, v pravé části obrazu) jiný text na stejném řádku
        for ((it, line) in stack) {
            val lh = (it.bottom - it.top) * h
            if (zone.any { o -> o !== line && letters(o.text) >= 3 && o.left > w * 0.55f && o.right < it.left * w &&
                    kotlin.math.abs((o.top + o.bottom) / 2f - it.center * h) < lh / 2f }) return null
        }
        val used = stack.map { it.second }
        val items = stack.map { (c, _) -> withShoulderLine(c, zone.filter { o -> used.none { u -> u === o } }, w, h) }.sortedBy { it.top }
        val gaps = items.zipWithNext { a, b -> b.center - a.center }.filter { it in 0.040f..0.070f }
        val step = gaps.minOrNull() ?: DEFAULT_STEP
        val right = items.map { it.right }.sorted()[items.size / 2]
        return CornerView(items, step, items.first().top, right)
    }

    private fun letters(s: String) = s.count { it.isLetter() }

    /** „SPEED 1/2/3 [L2] [R2]“: ikonu, kterou čtečka přečetla jako samostatný řádek vpravo od položky, přidá k ní. */
    private fun withShoulderLine(item: CornerItem, others: List<MenuLine>, w: Int, h: Int): CornerItem {
        val extra = others.filter { o -> o.left >= item.right * w - w * 0.01f && o.left <= item.right * w + w * 0.05f &&
                kotlin.math.abs((o.top + o.bottom) / 2f - item.center * h) < (item.bottom - item.top) * h / 2f }
            .sortedBy { o -> o.left }
            .flatMap { o ->
                val t = o.text.trim().split(WS).filter { s -> s.isNotEmpty() }
                val icons = t.mapNotNull { s -> ButtonIcons.shoulder(s) }
                if (icons.isNotEmpty() && icons.size == t.size) icons else emptyList()
            }
        if (extra.isEmpty()) return item
        return item.copy(icon = (listOfNotNull(item.icon) + extra).distinct().take(2).joinToString(" "))
    }

    private val WS = Regex("\\s+")
    /** Ikony ramenních tlačítek ve zbytku řádku za příponou („l2r2“). */
    private val SHOULDERS = Regex("[lr][12li]")

    private fun shoulders(rest: String): List<String> = SHOULDERS.findAll(rest).mapNotNull { ButtonIcons.shoulder(it.value) }.toList()

    /** Obsah závorky „x1.0“, „1/l7“ → „X1.0“, „1/17“; null = není to hodnota (jen číslo jako zbytek ikony „(9)“). */
    private fun paren(s: String): String? {
        var t = s.replace(" ", "").replace(',', '.')
        val x = t.firstOrNull()?.let { it == 'x' || it == 'X' || it == '×' } == true
        if (x) t = t.substring(1)
        t = t.map { when (it) { 'I', 'l' -> '1'; 'O', 'o' -> '0'; else -> it } }.joinToString("")
        if (t.isEmpty() || !t[0].isDigit() || !t.last().isDigit() || t.none { it.isDigit() }) return null
        if (!x && '.' !in t && '/' !in t) return null
        if (t.count { it == '/' } > 1 || t.count { it == '.' } > 1) return null
        return (if (x) "X" else "") + t
    }

    /** Jeden řádek → položka, [BAR] (vodorovná lišta), nebo null (není to položka). */
    private fun item(l: MenuLine, w: Int, h: Int, spec: CornerSpec): CornerItem? {
        val hFrac = (l.bottom - l.top).toFloat() / h
        // výška rámečku řádku (písmena 1,5–1,9 % + okraj čtečky)
        if (hFrac < 0.011f || hFrac > 0.040f) return null
        // slova s písmeny (bez zbytků ikon) – podle nich pravý okraj, jas a mezery
        val words = l.words.filter { letters(it.text) >= 2 && !JUNK.matches(it.text.trim()) }
        val lineH = maxOf(1, l.bottom - l.top)
        if (words.size >= 2) {
            val ws = words.sortedBy { it.left }
            // mezera mezi slovy přes 1,5 výšky řádku = položky vedle sebe (lišta v menu)
            if (ws.zipWithNext().any { (a, b) -> b.left - a.right > lineH * 1.5f }) return BAR
        }
        val raw = l.text.trim()
        var base = raw
        var suffix = ""
        val icons = ArrayList<String>()
        SUFFIX.find(raw)?.let { m ->
            val rest = compact(raw.substring(m.range.last + 1))
            if (m.value.any { it.isDigit() } && rest.length <= 6 && REST.matches(rest)) {
                icons += shoulders(rest)
                base = raw.substring(0, m.range.first)
                suffix = listOfNotNull(m.groups[1], m.groups[2], m.groups[3]).joinToString("/") { g ->
                    when (g.value) { "I", "l" -> "1"; "Z" -> "2"; else -> g.value }
                }
            }
        }
        if (suffix.isEmpty()) PAREN.find(raw)?.let { m ->
            val rest = compact(raw.substring(m.range.last + 1))
            val v = paren(m.groups[1]!!.value)
            if (m.range.first > 0 && v != null && rest.length <= 6 && REST.matches(rest)) {
                icons += shoulders(rest)
                base = raw.substring(0, m.range.first)
                suffix = "($v)"
            }
        }
        if (suffix.isEmpty()) PRICE.find(raw)?.let { m ->
            val rest = compact(raw.substring(m.range.last + 1))
            if (m.range.first > 0 && rest.length <= 6 && REST.matches(rest)) {
                icons += shoulders(rest)
                base = raw.substring(0, m.range.first)
                suffix = "($" + m.groups[1]!!.value + ")"
            }
        }
        val tokens = base.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        // samotné „L“ / „R“ za položkou = useknutá ikona L1/R1 („TAB L“, dávka 4KHZ-AJKC; od 0.97.114)
        while (tokens.isNotEmpty() && (JUNK.matches(tokens.last()) || (tokens.size >= 2 && tokens.last() in setOf("L", "R")))) {
            ButtonIcons.shoulder(tokens.last())?.let { icons.add(0, it) }
            tokens.removeAt(tokens.size - 1)
        }
        base = tokens.joinToString(" ")
        val ls = base.filter { it.isLetter() }
        if (ls.length < 2 || tokens.size > 4 || base.length > 30) return null
        if (ls.count { it.isUpperCase() } < ls.length * 0.8f) return null
        if (base.any { it in ":.?!" }) return null
        // pravý okraj i s hodnotou v závorce („GALLERY (3/32)“ – závorka je vpravo), bez zbytků ikon
        val rightWords = if (suffix.startsWith("(")) l.words.filter { wd -> !JUNK.matches(wd.text.trim()) &&
            (letters(wd.text) >= 2 || wd.text.any { it.isDigit() }) } else words
        val right = (rightWords.maxOfOrNull { it.right } ?: l.right).toFloat() / w
        if (right < 0.92f || right > 0.99f) return null
        val left = (words.minOfOrNull { it.left } ?: l.left).toFloat() / w
        val known = match(base, spec)
        // ikona přilepená k poslednímu slovu („MARKERSR1“)
        if (known != null) {
            val k = compact(base); val kk = compact(known)
            if (k.length > kk.length && k.startsWith(kk)) ButtonIcons.shoulder(k.substring(kk.length))?.let { icons.add(0, it) }
        }
        val bright = if (words.isNotEmpty()) words.maxOf { it.bright } else l.bright
        return CornerItem(known ?: base, suffix, bright in 0 until DIM, known != null,
            left, l.top.toFloat() / h, right, l.bottom.toFloat() / h,
            icons.distinct().take(2).joinToString(" ").ifEmpty { null })
    }

    /** Položka ze slovníku profilu: přesně, s přilepeným zbytkem ikony, nebo s drobnou chybou čtení (dlouhé). */
    internal fun match(text: String, spec: CornerSpec): String? {
        val k = compact(text)
        if (k.length < 2) return null
        spec.keys[k]?.let { return it }
        spec.keys.entries.filter { k.length > it.key.length && k.startsWith(it.key) && TAIL.matches(k.substring(it.key.length)) }
            .maxByOrNull { it.key.length }?.let { return it.value }
        if (k.length <= 6) return null
        val scored = spec.keys.entries.filter { it.key.length > 6 }.map { it to distance(k, it.key, 2) }.filter { it.second <= 2 }
        val best = scored.minOfOrNull { it.second } ?: return null
        val top = scored.filter { it.second == best }
        return if (top.size == 1) top[0].first.value else null
    }

    /** Vzdálenost úprav (nejvýš [limit] + 1). */
    internal fun distance(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                rowMin = minOf(rowMin, cur[j])
            }
            if (rowMin > limit) return limit + 1
            prev = cur
        }
        return prev[b.length]
    }

    // ---------- kam položit překlad ----------

    /** Nejdál doleva, kam smí překlad růst (zlomek šířky). */
    const val LEFT_LIMIT = 0.55f

    /**
     * Obdélník pro překlad každé položky (vlevo, nahoře, vpravo, dole ve zlomcích obrazu), v pořadí položek.
     * Text se zarovná doprava k pravému okraji; levý okraj = nejmenší šířka (u „přes položky“ zakryje anglický text),
     * doleva smí růst nejvýš k [LEFT_LIMIT].
     *  - [mode] 1 „nad položkami“: řádky nad horní položkou (dolní okraj o 0,8 % výš), pravý okraj jako text ve hře
     *    (ikony zůstanou vidět), výška řádku = rozestup ve hře, pořadí jako ve hře; s ikonou tlačítka ([icons]) pravý
     *    okraj až za ikonou ve sloupci ikon hry,
     *  - [mode] 2 „přes položky“: každý řádek přes svůj anglický řádek (střed ± 0,45 rozestupu), o kousek přes okraje.
     */
    fun boxes(v: CornerView, mode: Int): List<FloatArray> {
        val step = if (v.step > 0f) v.step else DEFAULT_STEP
        val n = v.items.size
        return if (mode == 1) {
            // od 1.0.01 (přání majitele): každý překlad vlevo vedle své položky ve stejné výšce, zarovnaný doprava k jejímu
            // začátku – originál i s ikonou zůstane vidět, překlad žlutě. Dřív řádky nad seznamem (překrývaly se s ním).
            v.items.map { it ->
                val r = (it.left - 0.012f).coerceAtLeast(LEFT_LIMIT + 0.05f)
                floatArrayOf(maxOf(LEFT_LIMIT, r - 0.4f), it.center - 0.45f * step, r, it.center + 0.45f * step)
            }
        } else {
            val r = minOf(1f, v.right + 0.003f)
            v.items.map { it ->
                floatArrayOf((it.left - 0.006f).coerceIn(LEFT_LIMIT, r), it.center - 0.45f * step, r, it.center + 0.45f * step)
            }
        }
    }

    /**
     * Ikona tlačítka za překladem každé položky ([CornerItem.icon], jen známé tvary [ButtonIcons.KNOWN]), nebo null.
     * Jen „nad položkami“ – u „přes položky“ je hned vedle vidět ikona hry.
     */
    // od 1.0.01 překlad vlevo vedle originálu – ikona hry je hned vedle vidět, vlastní se nekreslí
    @Suppress("UNUSED_PARAMETER")
    fun icons(v: CornerView, mode: Int): List<String?> = v.items.map { null }

    /** Výška písma překladu (zlomek výšky obrazu): ~62 % řádku. */
    // písmo ~53 % řádku (od 0.97.124 menší – majitel: „trochu menším písmem“; dřív 62 %)
    fun textHeight(v: CornerView): Float = (if (v.step > 0f) v.step else DEFAULT_STEP) * 0.53f

    /** Text řádku: překlad a beze změny přípona „1/2/3“ / „(X1.0)“. */
    fun line(translation: String, item: CornerItem): String = if (item.suffix.isEmpty()) translation else "$translation ${item.suffix}"

    /** „DISABLE AUTO DRIVE“ → „Disable auto drive“ (překladač verzálky překládá hůř). */
    fun titleCase(t: String): String = t.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Seznam vpravo dole v čase: ukáže se po jednom čtení, když jsou všechny položky ze slovníku, jinak až po dvou stejných
 * čteních; změna (jiné položky, jiná šedá) hned; zmizí po [MISSES] čteních bez seznamu (neblikne).
 */
class CornerTracker {
    private var shown: CornerView? = null
    private var pending: String? = null
    private var misses = 0
    private var unsure = 0

    data class Result(val view: CornerView?, val changed: Boolean)

    /** Co je teď ukázané (null = nic). */
    val view: CornerView? get() = shown

    fun reset() { shown = null; pending = null; misses = 0; unsure = 0 }

    fun update(read: CornerView?): Result {
        if (read == null) {
            pending = null; unsure = 0
            val s = shown ?: return Result(null, false)
            if (++misses < MISSES) return Result(s, false)
            reset()
            return Result(null, true)
        }
        misses = 0
        val s = shown
        if (s != null && s.key == read.key) {
            unsure = 0
            // stejný seznam: ikona se jen doplní nebo změní, jedno čtení bez ní ji neschová
            // a čtení jen s částí ikon („TAB“ s L1 R1 → jen R1, dávka 6RKU-RZW3) je nezmenší – ikony neblikají
            val merged = s.copy(items = s.items.zip(read.items) { a, b -> a.copy(icon = mergeIcon(a.icon, b.icon)) })
            if (merged.iconKey == s.iconKey) return Result(s, false)
            shown = merged
            return Result(merged, true)
        }
        val sure = read.items.all { it.known } || read.key == pending
        // nejisté čtení bez potvrzení viselo klidně napořád (zmizelá položka, např. R3, tak zůstala zobrazená –
        // dávka DSXW-MJSD) – po pár nejistých čteních za sebou se věří novému i bez druhého stejného potvrzení
        if (!sure && ++unsure < UNSURE_LIMIT) { pending = read.key; return Result(s, false) }
        pending = null; unsure = 0
        val next = if (s != null) keepIcons(read, s) else read
        shown = next
        return Result(next, true)
    }

    /** Položky bez ikony vezmou ikonu stejné položky z [old] (změnila se jiná položka). */
    private fun keepIcons(read: CornerView, old: CornerView): CornerView = read.copy(items = read.items.map { it ->
        if (it.icon != null) it else it.copy(icon = old.items.firstOrNull { o -> o.text == it.text && o.suffix == it.suffix }?.icon)
    })

    companion object {
        const val MISSES = 2
        /** Kolik nejistých čtení za sebou se ještě drží dřívější seznam, než se věří novému i bez potvrzení. */
        const val UNSURE_LIMIT = 4

        /** Ikona po novém čtení: nic nebo jen část dřívějších ikon → zůstávají dřívější; jinak nová. */
        fun mergeIcon(old: String?, new: String?): String? {
            if (new == null || old == null) return new ?: old
            val o = old.split(' '); val n = new.split(' ')
            return if (n.all { it in o }) old else new
        }
    }
}
