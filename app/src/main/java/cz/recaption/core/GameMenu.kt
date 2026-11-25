package cz.recaption.core

/**
 * Překlad základního menu hry (od 0.97.34, zatím GTA VI): menu otevřené zleva (Map / Game / Store…), horní lišta
 * Nastavení (Control Scheme, Controller…) a nadpis s popisem vybrané volby vpravo. Jednotlivé volby Nastavení
 * zatím ne (je jich moc). Podle snímků ze hry majitele 20. 11. 2026 (`zaznamy/hra-…`, obraz 16 : 9):
 *  - menu zleva: položky vlevo (x < 25 %), výška 15–70 %, vybraná má bílý pruh a tmavé písmo – čtečka ji často
 *    nepřečte, proto se pozná i jako ta, která v čtení chybí ([MenuTracker]),
 *  - lišta Nastavení: nahoře (text kolem 8 % výšky), vybraná záložka bílá s tmavým písmem,
 *  - popis vybrané volby: vpravo (x > 50 %), nadpis VERZÁLKAMI a pod ním 1–3 řádky textu.
 * Od 0.97.36 se menu zleva ukazuje jako seznam pod sebou hned vedle menu ([MenuView.box] → `TvLook` poloha „misto“).
 * Od 0.97.58 Nastavení zvlášť: jen vybraná záložka těsně nad lištou ([tabPlace], v pruhu) a nadpis s popisem
 * ve vlastním okně ([MenuView.descBox], v TV zpráva `menupopis`; od 0.97.59 pod anglickým popisem, [descPlace]).
 * Bez Androidu – testy `GameMenuTest`.
 */
class MenuSpec(
    /** Položky menu otevřeného zleva. */
    val pause: List<String>,
    /** Záložky horní lišty Nastavení. */
    val tabs: List<String>,
    /** Pevné překlady položek a záložek: kód jazyka → (anglicky → překlad). Jiné jazyky přeloží překladač. */
    val words: Map<String, Map<String, String>> = emptyMap(),
    /** Podmenu ve stejném vzhledu jako menu zleva (GTA VI „GAME“: Save Game / Load Game / New Game; od 0.97.139). */
    val sub: List<String> = emptyList(),
    /** Nadpisy panelu Fotostudia vlevo nahoře (CAMERA…; od 0.97.139) – volby pod nimi se čtou jakékoli. */
    val photoHeads: List<String> = emptyList(),
    /** Tlačítka Fotostudia ve sloupci vpravo dole (ZOOM, MOVE, SAVE PHOTO…). */
    val photoButtons: List<String> = emptyList(),
    /** Velikost písma a místo překladu z profilu hry (od 1.00-rc21) – menu hry se nemění, nemusí se měřit při každém čtení. */
    val layout: MenuLayout = MenuLayout(),
) {
    internal val pauseKeys = pause.associateBy { TextLogic.simplify(it) }
    internal val subKeys = sub.associateBy { TextLogic.simplify(it) }
    internal val photoHeadKeys = photoHeads.associateBy { TextLogic.simplify(it) }
    internal val photoButtonKeys = photoButtons.associateBy { TextLogic.simplify(it) }
    internal val tabKeys = tabs.associateBy { TextLogic.simplify(it) }

    private val wordKeys: Map<String, Map<String, String>> = words.mapValues { (_, m) -> m.entries.associate { key(it.key) to it.value } }

    /** Pevný překlad položky / názvu volby, nebo null (pak ji přeloží překladač). Malá/velká písmena a čtení „0“ místo „o“ nevadí. */
    fun word(lang: Lang, item: String): String? = wordKeys[lang.code]?.get(key(item))

    private fun key(t: String) = t.lowercase().replace('0', 'o').filter { it.isLetterOrDigit() }

    /**
     * Pevný překlad i pro název přečtený s jednou chybou („Rol“ = Roll; od 0.97.139 – názvy voleb Fotostudia čtečka čte
     * na průsvitném panelu hůř). Jen proti názvům aspoň 4 znaky a jen jediná shoda; jinak null.
     */
    fun near(lang: Lang, item: String): String? {
        word(lang, item)?.let { return it }
        val k = key(item)
        if (k.length < 3) return null
        val m = wordKeys[lang.code] ?: return null
        val hits = m.entries.filter { (kk, _) -> kk.length >= 4 && kk.length in (k.length - 1)..(k.length + 1) && kk.length <= 24 && TextLogic.levenshtein(k, kk) <= 1 }
        return hits.singleOrNull()?.value
    }

    private class Desc(val toks: List<String>, val tr: String)

    /** Popisy (aspoň 4 slova delší než 2 znaky) podle prvního slova, delší dřív. */
    private val descs: Map<String, Map<String, List<Desc>>> = words.mapValues { (_, m) ->
        m.entries.mapNotNull { (en, tr) -> sig(en).takeIf { it.size >= 4 }?.let { Desc(it, tr) } }
            .groupBy { it.toks[0] }.mapValues { (_, l) -> l.sortedByDescending { it.toks.size } }
    }

    /** Slova delší než 2 znaky – krátká („a“, „to“, zbytky ikon „R“, „L2“, „©“) se při porovnání popisu přeskočí. */
    private fun sig(t: String) = t.split(WS).map { key(it) }.filter { it.length > 2 }

    /**
     * Popis volby po částech (od 0.97.114): odstavce, které známe ([words]), dostanou vlastní překlad, zbytek (null) přeloží
     * překladač. Popis z obrazu je jeden řetězec všech odstavců a čtečka v něm čte i ikony tlačítek („when using (R) to look
     * around“) – porovnávají se jen delší slova. null = nic známého.
     */
    fun cover(lang: Lang, text: String): List<Pair<String, String?>>? {
        val m = descs[lang.code] ?: return null
        val ws = text.split(WS).filter { it.isNotEmpty() }
        val norm = ws.map { key(it) }
        val idx = norm.indices.filter { norm[it].length > 2 }
        val out = ArrayList<Pair<String, String?>>()
        var wordPos = 0
        fun flush(until: Int) {
            if (until > wordPos && (wordPos until until).any { norm[it].length > 2 }) out += ws.subList(wordPos, until).joinToString(" ") to null
        }
        var k = 0
        while (k < idx.size) {
            val hit = m[norm[idx[k]]]?.firstOrNull { d -> k + d.toks.size <= idx.size && d.toks.indices.all { norm[idx[k + it]] == d.toks[it] } }
            if (hit == null) { k++; continue }
            // krátká slova před popisem („A marker…“) patří k němu
            var start = idx[k]
            while (start > wordPos && norm[start - 1].length <= 2) start--
            flush(start)
            val end = idx[k + hit.toks.size - 1] + 1
            out += ws.subList(start, end).joinToString(" ") to hit.tr
            wordPos = end; k += hit.toks.size
        }
        flush(ws.size)
        return out.takeIf { o -> o.any { it.second != null } }
    }

    private companion object { val WS = Regex("\\s+") }
}

/**
 * Rozložení menu hry uložené v profilu (od 1.00-rc21, přání majitele: „menu bude na stejném místě většinu času a velikost
 * písma taky“): výška přečteného textu položek menu zleva ([pauseLine]), podmenu ([subLine]) a voleb Fotostudia
 * ([photoLine]) jako zlomek výšky obrazu a pravý okraj překladu tlačítek vpravo dole v menu zleva ([footerRight], zlomek
 * šířky – tam, kde končí ikona posledního tlačítka). 0 = neznámé, změří se z čtení. Rychlá kontrola: když se změřená
 * hodnota výrazně liší ([MenuTracker.LAYOUT_TOLERANCE], [GameMenu.FOOTER_TOLERANCE]), hra se nejspíš změnila → platí změřená.
 */
data class MenuLayout(val pauseLine: Float = 0f, val subLine: Float = 0f, val photoLine: Float = 0f, val footerRight: Float = 0f) {
    val isEmpty: Boolean get() = pauseLine <= 0f && subLine <= 0f && photoLine <= 0f && footerRight <= 0f
}

/**
 * Přeložený popisek menu hry na svém místě (od 0.97.115): [box] = obdélník ve zlomcích obrazu (výška = řádek),
 * [right] = zarovnat vpravo k box[2] (jinak vlevo od box[0]), [selected] = položka v bílém pruhu hry (černé písmo bez pozadí),
 * [px] = písmo v px na obrazovce 1080.
 */
data class MenuLabel(val text: String, val box: FloatArray, val px: Int, val right: Boolean = false, val selected: Boolean = false,
                     /** Pozadí 0–100 % (0 = bez pozadí, bílé písmo se stínem). */
                     val bg: Int = 0,
                     /** Ikona položky ze hry před textem (od 0.97.121): klíč do mapy ikon služby (název položky), null = bez ikony. */
                     val icon: String? = null,
                     /**
                      * Části s ikonou tlačítka za textem („ZPĚT ○“, od 0.97.124 – patička): text a kód ikony ([ButtonIcons.KNOWN]:
                      * „x“, „o“, „s“, „t“), null = bez ikony. Prázdné = jen [text].
                      */
                     val parts: List<Pair<String, String?>> = emptyList())

/** Slovo řádku (rámeček z čtečky) s průměrným jasem pod ním – záložky Nastavení čtečka často spojí do jednoho řádku. */
data class MenuWord(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val bright: Int,
                    /**
                     * Nejsvětlejší body písmen (95. percentil jasu v rámečku slova; −1 = nevíme; od 0.97.144) – vybraná záložka
                     * mapy / Briefu je růžové písmo (~186) mezi bílými (~253), bez bílého rámečku (dávka GVFD-XYV8).
                     */
                    val peak: Int = -1)

/**
 * Řádek z celé horní části obrazu: text, poloha v pixelech a průměrný jas pod ním (0–255; vybraná položka ~200).
 * [words] = slova s vlastními rámečky (od 0.97.58; prázdné = nevíme).
 */
data class MenuLine(
    val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val bright: Int,
    val words: List<MenuWord> = emptyList(),
    /**
     * Sklon řádku (o kolik bodů klesne účaří na bod šířky; z rohů řádku čtečky). Telefon ve hře při chůzi drží postava
     * natočený (dávka 6RKU-RZW3: 0,07–0,13) – rámeček [left]…[bottom] je pak kolem natočeného řádku, ne kolem písma.
     */
    val angle: Float = 0f,
)

/**
 * Co je na obrazovce za menu. [kind] = „pause“ (menu zleva) / „settings“; [items] v pořadí shora (zleva), [tops]
 * jejich poloha (px, pro řazení při doplňování); [selected] −1 = nepoznáno; [box] = kam položit překlad (levý horní
 * roh ve zlomcích obrazu).
 */
data class MenuView(
    val kind: String, val items: List<String>, val selected: Int, val title: String? = null, val description: String? = null,
    val tops: List<Int> = emptyList(), val box: Pair<Float, Float>? = null,
    /** Rozestup řádků menu ve hře (zlomek výšky obrazu; 0 = neznámý) – překlad řádek proti řádku (od 0.97.40). */
    val step: Float = 0f,
    /**
     * Kde se u každé položky pozná výběr podle jasu (vlevo, nahoře, vpravo, dole ve zlomcích obrazu; od 0.97.58):
     * u menu zleva kus pruhu vpravo za textem, u záložek rámeček záložky. Podle nich služba pozná vybranou položku
     * na každém novém snímku bez čtečky ([MenuTracker.fast]). Prázdné pole = nevíme.
     */
    val rects: List<FloatArray> = emptyList(),
    /** Nastavení: kam dát překlad nadpisu a popisu vybrané volby ([GameMenu.descPlace], zlomky; dole = nejníž) a výška řádku popisu ve hře. */
    val descBox: FloatArray? = null,
    val descLine: Float = 0f,
    /**
     * Nastavení: kam dát překlad nadpisu volby – nad anglický nadpis (od 0.97.102, přání majitele: nadpis nad nadpis,
     * popis pod popis, různá velikost písma); null = nad nadpisem není místo → nadpis jde s popisem do [descBox].
     * [titleLine] = výška řádku nadpisu ve hře (zlomek výšky).
     */
    val titleBox: FloatArray? = null,
    val titleLine: Float = 0f,
    /**
     * Seznam pod popisem volby (barvy v „Color Adjustments“: Player, Companion…; od 0.97.111) – položky pod sebou, každá
     * přeložená zvlášť vpravo od originálu ([listBox] vlevo, nahoře, vpravo, dole; [listStep] rozestup řádků, zlomky).
     */
    val list: List<String> = emptyList(),
    val listBox: FloatArray? = null,
    val listStep: Float = 0f,
    /** Tlačítka v patičce Nastavení vpravo dole (RESTORE ALL △ …; od 0.97.111): text a jeho obdélník (zlomky obrazu). */
    val footer: List<Pair<String, FloatArray>> = emptyList(),
    /** Menu zleva: rámeček názvu každé položky v pixelech (vlevo, nahoře, vpravo, dole) – jen z čtení, pro ikonu (dřív pro ikonu; od 0.97.131 se nepoužívá). */
    val textBoxes: List<IntArray> = emptyList(),
    /**
     * Menu zleva: panel mise vpravo (od 0.97.133, dávka Y4HT-JEJE – „CHAPTER 1“, „Retry Checkpoint“, „Restart Mission“, „Quit Mission“):
     * text a obdélník (zlomky obrazu) řádků, které mají vlastní překlad v profilu hry; název mise („SWAMPED“) se nepřekládá.
     */
    val side: List<Pair<String, FloatArray>> = emptyList(),
    /**
     * Nadpis podmenu / panelu Fotostudia vlevo nahoře („GAME“, „CAMERA“; od 0.97.139) a jeho obdélník (zlomky) – překlad vedle něj.
     * Jiný nadpis = jiné menu (tracker začne znovu).
     */
    val head: Pair<String, FloatArray>? = null,
    /** Vybraná položka panelu mise vpravo (index do [side]; −1 = výběr je v menu zleva; od 0.97.140 podle jasu). */
    val sideSel: Int = -1,
    /**
     * Schéma ovládání (od 0.97.144, dávka GVFD-XYV8): popisky kolem ovladače – text, obdélník (zlomky) a true = levý sloupec
     * (zarovnaný doprava k ikoně), false = pravý (zarovnaný doleva). Překlad pod každým popiskem ([GameMenu.controlLabels]).
     */
    val controls: List<Triple<String, FloatArray, Boolean>> = emptyList(),
    /** Nastavení / Nápověda: volby v seznamu vlevo (název a obdélník, zlomky obrazu; od 1.0.1) – překlad ke každé ([GameMenu.optionLabels]). */
    val options: List<Pair<String, FloatArray>> = emptyList(),
    /**
     * Menu zleva a Fotostudio: výška řádku položky pro písmo překladu (zlomek výšky; 0 = neznámá), jedna na celé otevřené menu
     * nebo podmenu ([MenuTracker], z profilu hry [MenuLayout]) – výška přečteného textu se mění podle písmen (g, p, y) i čtení
     * od čtení a písmo skákalo.
     */
    val itemLine: Float = 0f,
) {
    /** Jen na porovnání, jestli se menu změnilo (jiná vybraná položka, jiný popis). */
    val key: String get() = listKey + "#" + descKey
    /** Seznam / lišta a vybraná položka (u Nastavení zvlášť od popisu – každý má svůj překlad). */
    val listKey: String get() = kind + "|" + (head?.first ?: "") + "|" + items.joinToString("|") + "#" + selected + (if (side.isEmpty()) "" else "#" + side.joinToString("|") { it.first } + "#" + sideSel) +
        (if (controls.isEmpty()) "" else "#" + controls.joinToString("|") { it.first }) +
        (if (options.isEmpty()) "" else "#" + options.joinToString("|") { it.first })
    val descKey: String get() = (title ?: "") + "#" + (description ?: "")
    val listKey2: String get() = list.joinToString("|")
}

object GameMenu {
    /** Kolik položek / záložek musí být vidět, aby to bylo menu (ne náhodné slovo „Map“ ve hře). */
    const val MIN_ITEMS = 3
    /** Značka vybrané položky – „»“ je v každém písmu (šipku ▸ televize LG neuměla, byl čtvereček). */
    const val MARK = "» "

    /** Rozpozná menu z řádků celé horní části obrazu ([w] × [h] = celý snímek). null = žádné menu. */
    /**
     * [bar] (od 0.97.108): jas pásu pod písmeny lišty Nastavení po sloupcích (svisle od–do v bodech → průměr každého sloupce),
     * podle něj se vybraná záložka najde přímo jako bílý rámeček – nemusí se přečíst všechny záložky (přání majitele).
     */
    fun read(lines: List<MenuLine>, w: Int, h: Int, spec: MenuSpec, inSettings: Boolean = false,
             bar: ((Int, Int) -> IntArray?)? = null): MenuView? {
        if (w <= 0 || h <= 0) return null
        pause(lines, w, h, spec)?.let { return it }
        subMenu(lines, w, h, spec)?.let { return it }
        settings(lines, w, h, spec, bar = bar)?.let { return it }
        photo(lines, w, h, spec)?.let { return it }
        // Nastavení už je otevřené a lišta záložek se nepřečetla (obraz z konzole se na chvíli rozpadl na kostky –
        // tester 22. 11. 2026, P2ZT-VKQ5: překlad menu pak „přestal úplně“): stačí nadpis a popis vpravo
        return if (inSettings) settings(lines, w, h, spec, needTabs = false) else null
    }

    /**
     * Bílé úseky v jasu pásu pod písmeny lišty ([bar] po sloupcích): souvislé úseky výrazně světlejší než okolí (nad 190
     * a o 70 nad mediánem), aspoň [minWidth] bodů. Vrací (od, do) v bodech zleva doprava.
     */
    internal fun whiteRuns(bar: IntArray, minWidth: Int, maxGap: Int = 1): List<Pair<Int, Int>> {
        if (bar.size < 8) return emptyList()
        val med = bar.sorted()[bar.size / 2]
        val limit = maxOf(190, med + 70)
        val out = ArrayList<Pair<Int, Int>>()
        var i = 0
        while (i < bar.size) {
            if (bar[i] >= limit) {
                var j = i
                // přes tmavá písmena uvnitř rámečku (nejvýš [maxGap] sloupců) dál
                while (true) {
                    var k = j + 1
                    while (k < bar.size && k <= j + maxGap + 1 && bar[k] < limit) k++
                    if (k < bar.size && k <= j + maxGap + 1) j = k else break
                }
                if (j - i + 1 >= minWidth) out += i to j
                i = j + 1
            } else i++
        }
        return out
    }

    /**
     * Rámeček vybrané záložky: bílý úsek se středem přečtené záložky; jinak (vybraná se nepřečetla – tmavé písmo na bílém)
     * úsek nejblíž přečteným záložkám (bílé ikonky L1 / R1 po stranách lišty jsou dál než krajní záložka).
     */
    internal fun pickRun(runs: List<Pair<Int, Int>>, tabs: List<Tab>): Pair<Int, Int>? {
        if (runs.isEmpty() || tabs.isEmpty()) return null
        runs.firstOrNull { r -> tabs.any { (it.left + it.right) / 2 in r.first..r.second } }?.let { return it }
        val lo = tabs.minOf { it.left }; val hi = tabs.maxOf { it.right }
        fun dist(r: Pair<Int, Int>) = when { r.second < lo -> lo - r.second; r.first > hi -> r.first - hi; else -> 0 }
        return runs.minByOrNull { dist(it) }
    }

    /**
     * Vybraná záložka podle bílého rámečku: záložka, jejíž střed v něm leží; když se její text nepřečetl, odvodí se
     * z pořadí v profilu hry podle sousední záložky vlevo (nebo vpravo). Vrací název, nebo null.
     */
    internal fun tabByRun(tabs: List<Tab>, run: Pair<Int, Int>, spec: MenuSpec): String? {
        tabs.firstOrNull { (it.left + it.right) / 2 in run.first..run.second }?.let { return it.name }
        tabs.filter { it.right <= run.first }.maxByOrNull { it.right }?.let { left ->
            spec.tabs.getOrNull(spec.tabs.indexOf(left.name) + 1)?.let { return it }
        }
        tabs.filter { it.left >= run.second }.minByOrNull { it.left }?.let { right ->
            spec.tabs.getOrNull(spec.tabs.indexOf(right.name) - 1)?.let { return it }
        }
        return null
    }

    /** Menu otevřené zleva: aspoň [MIN_ITEMS] položek pod sebou v levé čtvrtině. */
    private fun pause(lines: List<MenuLine>, w: Int, h: Int, spec: MenuSpec): MenuView? {
        val found = lines.filter { it.left < w * 0.25f && it.top > h * 0.15f && it.top < h * 0.7f }
            .mapNotNull { l -> spec.pauseKeys[TextLogic.simplify(stripIcon(l.text))]?.let { it to l } }
            .distinctBy { it.first }.sortedBy { it.second.top }
        if (found.size < MIN_ITEMS) return null
        // překlad hned vpravo od menu (menu má v GTA VI pevnou šířku ~27 % obrazu), od první položky dolů
        val x = maxOf(found.maxOf { it.second.right }.toFloat() / w + 0.03f, 0.3f)
        val y = found.first().second.top.toFloat() / h
        return MenuView("pause", found.map { it.first }, selected(found.map { it.second.bright }),
            tops = found.map { it.second.top }, box = x.coerceAtMost(0.6f) to y, step = rowStep(found.map { it.second.top }, h),
            rects = found.map { pauseRect(it.second, w, h) }, footer = footer(lines, w, h),
            textBoxes = found.map { intArrayOf(it.second.left, it.second.top, it.second.right, it.second.bottom) },
            side = sidePanel(lines, w, h, spec))
    }

    /**
     * Podmenu ve vzhledu menu zleva (od 0.97.139, dávka DH7E-VPFV – majitel: „menu pro uložení vypadá stejně jako menu“):
     * nadpis verzálkami vlevo nahoře („GAME“) a aspoň 2 známé položky pod ním. Ukazuje se stejně jako menu zleva.
     */
    private fun subMenu(lines: List<MenuLine>, w: Int, h: Int, spec: MenuSpec): MenuView? {
        if (spec.sub.isEmpty()) return null
        val found = lines.filter { it.left < w * 0.25f && it.top > h * 0.12f && it.top < h * 0.7f }
            .mapNotNull { l -> spec.subKeys[TextLogic.simplify(stripIcon(l.text))]?.let { it to l } }
            .distinctBy { it.first }.sortedBy { it.second.top }
        if (found.size < 2) return null
        val head = lines.filter { it.left < w * 0.1f && it.top < found.first().second.top && it.top < h * 0.15f && isHeading(it.text) }
            .minByOrNull { it.top }
        // jedna položka může chybět (vybraná – tmavé písmo na bílém); bez nadpisu jen se všemi třemi
        if (head == null && found.size < MIN_ITEMS) return null
        val x = maxOf(found.maxOf { it.second.right }.toFloat() / w + 0.03f, 0.3f)
        return MenuView("pause", found.map { it.first }, selected(found.map { it.second.bright }),
            tops = found.map { it.second.top }, box = x.coerceAtMost(0.6f) to found.first().second.top.toFloat() / h,
            step = rowStep(found.map { it.second.top }, h), rects = found.map { pauseRect(it.second, w, h) }, footer = footer(lines, w, h),
            head = head?.let { it.text.trim() to frac(it, w, h) })
    }

    private fun frac(l: MenuLine, w: Int, h: Int) = floatArrayOf(l.left.toFloat() / w, l.top.toFloat() / h, l.right.toFloat() / w, l.bottom.toFloat() / h)

    /** Hodnota volby v řádku („Speed 100%“, „Roll 0°“) – nepřekládá se. */
    private val PHOTO_VALUE = Regex("\\s+[-+]?[0-9][0-9.,]*\\s*[%°]?$")

    /**
     * Fotostudio (od 0.97.139, dávka DH7E-VPFV – majitel: „vypadají podobně jako settings“): průsvitný panel vlevo nahoře
     * s nadpisem ([MenuSpec.photoHeads], „CAMERA“), pod ním volby (vlevo název, vpravo hodnota), pod nimi popis vybrané volby
     * a vpravo dole sloupec tlačítek ([MenuSpec.photoButtons]). Volby se čtou jakékoli (jiné záložky neznáme), přeloží se
     * vlastním překladem nebo překladačem; [MenuView.kind] = „photo“.
     */
    private fun photo(lines: List<MenuLine>, w: Int, h: Int, spec: MenuSpec): MenuView? {
        if (spec.photoHeads.isEmpty()) return null
        val head = lines.filter { it.left < w * 0.08f && it.top < h * 0.2f }
            .firstOrNull { spec.photoHeadKeys.containsKey(TextLogic.simplify(it.text)) } ?: return null
        val lineH = maxOf(1, head.bottom - head.top)
        // popis: první řádek ve sloupci panelu, který vypadá jako věta (tečka na konci nebo aspoň 4 slova)
        val panel = lines.filter { it.top > head.bottom && it.top < h * 0.75f && it.left < w * 0.15f && it.right < w * 0.4f }.sortedBy { it.top }
        val desc = panel.firstOrNull { l -> val t = l.text.trim(); t.endsWith(".") || t.split(Regex("\\s+")).size >= 4 }
        val opts = panel.filter { l -> (desc == null || l.top < desc.top) && l.left < w * 0.08f && l.text.count { it.isLetter() } >= 2 &&
            !l.text.trim().endsWith(".") && l.text.trim().split(Regex("\\s+")).size <= 4 }
            .map { l -> l.text.trim().replace(PHOTO_VALUE, "").trim() to l }
            .filter { it.first.count { c -> c.isLetter() } >= 2 }.distinctBy { it.first.lowercase() }
        val buttons = lines.filter { it.left > w * 0.7f && it.top > h * 0.5f }.mapNotNull { l ->
            val ws = l.words.filter { it.text.any { c -> c.isLetter() } }.map { it.text }.ifEmpty { l.text.trim().split(Regex("\\s+")) }
            // ikona tlačítka se občas přilepí k názvu („MOVEL)“) – nejvýš 2 znaky navíc
            (minOf(3, ws.size) downTo 1).firstNotNullOfOrNull { n ->
                val t = TextLogic.simplify(ws.take(n).joinToString(" "))
                spec.photoButtonKeys[t] ?: spec.photoButtonKeys.entries.firstOrNull { (k, _) -> k.length >= 4 && t.startsWith(k) && t.length - k.length <= 2 }?.value
            }?.let { it to l }
        }.distinctBy { it.first }.sortedBy { it.second.top }
        // nadpis CAMERA sám nestačí (nápis ve hře) – aspoň 2 volby, nebo volba a tlačítko / popis
        if (opts.size < 2 && !(opts.isNotEmpty() && (buttons.isNotEmpty() || desc != null))) return null
        val descText = desc?.let { d ->
            panel.filter { it.top >= d.top && it.top - d.bottom < lineH * 3 && it.left < w * 0.15f }.joinToString(" ") { it.text.trim() }
        }
        val descBox = desc?.let { d ->
            val b = panel.filter { it.top >= d.top && it.top - d.bottom < lineH * 3 }.maxOf { it.bottom }
            floatArrayOf((d.left.toFloat() / w - 0.006f).coerceAtLeast(0f), b.toFloat() / h + 0.012f, 0.45f, minOf(b.toFloat() / h + 0.2f, DESC_BOTTOM))
        }
        return MenuView("photo", opts.map { it.first }, selected(opts.map { it.second.bright }),
            tops = opts.map { it.second.top }, box = opts.firstOrNull()?.let { PHOTO_COL to it.second.top.toFloat() / h }, step = rowStep(opts.map { it.second.top }, h),
            rects = opts.map { pauseRect(it.second, w, h) },
            description = descText, descBox = descBox, descLine = desc?.let { (it.bottom - it.top).toFloat() / h } ?: 0f,
            side = buttons.map { (n, l) ->
                // jen název tlačítka bez ikony: rámeček ze slov názvu
                val ws = l.words.filter { it.text.any { c -> c.isLetter() } }.take(n.split(' ').size)
                n to (if (ws.isNotEmpty()) floatArrayOf(ws.minOf { it.left }.toFloat() / w, ws.minOf { it.top }.toFloat() / h,
                    ws.maxOf { it.right }.toFloat() / w, ws.maxOf { it.bottom }.toFloat() / h) else frac(l, w, h))
            },
            head = head.text.trim() to frac(head, w, h))
    }

    /**
     * Místo, kde se u položky menu zleva pozná bílý pruh: kousek vpravo za textem (tam je jen pozadí – tmavé, nebo
     * bílé u vybrané; samotný text by rozdíl zmenšil). GTA VI: pruh je široký ~27 % obrazu, nejdelší položka končí ~13 %.
     */
    internal fun pauseRect(l: MenuLine, w: Int, h: Int): FloatArray {
        val r = l.right.toFloat() / w
        val x0 = r + 0.01f
        val top = l.top.toFloat() / h
        val bottom = maxOf(l.bottom, l.top + 1).toFloat() / h
        return if (x0 + 0.03f <= 0.25f) floatArrayOf(x0, top, minOf(x0 + 0.05f, 0.25f), bottom)
        else floatArrayOf(l.left.toFloat() / w, top, r, bottom)
    }

    /** Nastavení: záložky v horní liště a nadpis + popis vybrané volby vpravo. */
    private fun settings(lines: List<MenuLine>, w: Int, h: Int, spec: MenuSpec, needTabs: Boolean = true,
                         bar: ((Int, Int) -> IntArray?)? = null): MenuView? {
        // záložky: čtečka je někdy spojí do jednoho řádku („CONTROL SCHEME CONTROLLER GAMEPLAY…“) → rozdělit;
        // text lišty je v GTA VI kolem 8 % výšky (41–52 bodů z 540), proto do 12 %
        val tabs = ArrayList<Tab>()
        for (l in lines.filter { it.top < h * 0.12f }.sortedBy { it.left }) {
            for (t in splitTabs(l, spec)) if (tabs.none { it.name == t.name }) tabs += t
        }
        if (needTabs && tabs.size < MIN_ITEMS) return null
        // vybraná záložka přímo podle bílého rámečku pod písmeny (od 0.97.108) – přednost před jasem jednotlivých slov
        var picked: String? = null
        if (bar != null && tabs.isNotEmpty()) {
            val top = tabs.minOf { it.top }; val bottom = tabs.maxOf { it.bottom }
            val th = maxOf(3, bottom - top)
            // průměr přes celou výšku záložky i s okrajem (od 0.97.115): rámeček končí těsně pod písmeny, pás pod nimi ho
            // míjel (dávka 8NYP-7QSJ – „Control Scheme“ se nikdy nevybrala); písmena uvnitř rámečku přemostí maxGap
            val prof = bar(top - Math.round(th * 0.3f), bottom + Math.round(th * 0.3f) + 1)
            val run = prof?.let { pickRun(whiteRuns(it, th, maxGap = th), tabs) }
            if (run != null) {
                picked = tabByRun(tabs, run, spec)
                // vybraná záložka se nepřečetla (tmavé písmo na bílém) → doplnit s rámečkem podle bílého úseku
                if (picked != null && tabs.none { it.name == picked }) {
                    tabs += Tab(picked, 250, run.first, top, run.second, bottom)
                    tabs.sortBy { it.left }
                }
            }
        }
        // popis vybrané volby: nadpis verzálkami vpravo nahoře a řádky pod ním ve stejném sloupci
        val head = lines.filter { it.left > w * 0.5f && it.top > h * 0.05f && it.top < h * 0.5f }.sortedBy { it.top }
            .firstOrNull { isHeading(it.text) }
        if (!needTabs && head == null) return null
        var description: String? = null
        var descBox: FloatArray? = null
        var descLine = 0f
        var titleBox: FloatArray? = null
        var list: List<String> = emptyList(); var listBox: FloatArray? = null; var listStep = 0f
        if (head != null) {
            val lineH = maxOf(1, head.bottom - head.top)
            val body0 = descBody(head, lines, w, h)
            if (!needTabs && body0.isEmpty()) return null
            // seznam pod popisem (krátké řádky odsazené za ikonkou – barvy): zvlášť, položka na řádek
            val listLines = listTail(body0, w)
            val body = body0.dropLast(listLines.size)
            if (listLines.isNotEmpty()) {
                list = listLines.map { dropIcons(it.text).ifEmpty { it.text.trim() } }
                val steps = listLines.zipWithNext { a, b -> b.top - a.top }.sorted()
                listStep = (steps.getOrNull(steps.size / 2) ?: (listLines[0].bottom - listLines[0].top)).toFloat() / h
                val r = listLines.maxOf { it.right }.toFloat() / w + 0.015f
                listBox = floatArrayOf(r, listLines.first().top.toFloat() / h - listStep * 0.25f, 0.99f, listLines.last().bottom.toFloat() / h + listStep * 0.25f)
            }
            description = dropIcons(body.joinToString(" ") { it.text.trim() }).ifEmpty { null }
            descBox = descPlace(head, body, lines, w, h)
            descLine = (body.map { it.bottom - it.top }.sorted().getOrNull(body.size / 2) ?: lineH).toFloat() / h
            titleBox = titlePlace(head, lines, descBox, w, h)
        }
        val x = (head?.left?.toFloat()?.div(w) ?: 0.55f)
        // lišta mapy / Briefu nahoře uprostřed (od 0.97.144): vybraná záložka je růžové písmo bez rámečku → nejtmavší písmo
        val topBar = tabs.isNotEmpty() && tabs.minOf { it.top } < h * TOP_BAR
        val sel = picked?.let { p -> tabs.indexOfFirst { it.name == p } }?.takeIf { it >= 0 }
            ?: (if (topBar) dimmest(tabs.map { it.peak }) else -1).takeIf { it >= 0 }
            ?: selected(tabs.map { it.bright })
        val selName = tabs.getOrNull(sel)?.name
        // Brief (záznam rozhovorů, tipů a úkolů; od 0.97.144): text vpravo bez nadpisu → přeložit celý pod něj
        if (head == null && selName == "Brief") {
            val body = lines.filter { it.left > w * 0.4f && it.top > h * 0.07f && it.top < h * BODY_BOTTOM && it.text.count { c -> c.isLetter() } >= 3 }
                .sortedBy { it.top }
            if (body.isNotEmpty()) {
                description = dropIcons(body.joinToString(" ") { it.text.trim() })
                descBox = descPlace(body.first(), body.drop(1), lines, w, h)
                descLine = (body.map { it.bottom - it.top }.sorted()[body.size / 2]).toFloat() / h
            }
        }
        // Schéma ovládání: popisky kolem ovladače (od 0.97.144)
        val controls = if (selName == "Control Scheme") controls(lines, head, w, h) else emptyList()
        val options = if (selName == null || topBar) emptyList() else options(lines, w, h)
        return MenuView("settings", tabs.map { it.name }, sel,
            head?.text?.let { dropIcons(it).ifEmpty { it.trim() } }?.let { titleCase(it) }, description, tabs.map { it.left }, x to 0.34f,
            // rámeček jen ze slov čtečky (jas < 0 = celý řádek, k měření nepoužitelný – tracker si nechá dřívější)
            rects = tabs.map { if (it.bright < 0) FloatArray(0) else floatArrayOf(it.left.toFloat() / w, it.top.toFloat() / h, it.right.toFloat() / w, maxOf(it.bottom, it.top + 1).toFloat() / h) },
            descBox = descBox, descLine = descLine, titleBox = titleBox, list = list, listBox = listBox, listStep = listStep,
            footer = footer(lines, w, h),
            titleLine = head?.let { (it.bottom - it.top).toFloat() / h } ?: 0f, controls = controls, options = options)
    }

    /**
     * Pás těsně pod písmeny záložky (svisle v pixelech: od, do), kde se pozná vybraná záložka: vybraná je bílý rámeček
     * s tmavým písmem, ostatní bílé písmo na barevném pozadí. Na písmenech je proto vybraná nejtmavší a krátké husté slovo
     * (AUDIO) nejsvětlejší – v překladu pak pořád svítila „Zvuk“ (tester 22. 11. 2026, P2ZT-VKQ5; změřeno na snímcích:
     * na písmenech vybraná 130 / AUDIO 180, pod písmeny vybraná 250 / ostatní ~105).
     */
    fun under(top: Int, bottom: Int): IntArray {
        val th = maxOf(1, bottom - top)
        return intArrayOf(bottom + maxOf(1, Math.round(th * 0.08f)), bottom + maxOf(2, Math.round(th * 0.38f)))
    }

    /** [under] pro rámeček ve zlomcích obrazu (vlevo, nahoře, vpravo, dole) – místo k měření jasu záložky na snímku. */
    fun underFrac(r: FloatArray): FloatArray {
        val th = r[3] - r[1]
        return floatArrayOf(r[0], r[3] + th * 0.08f, r[2], r[3] + th * 0.38f)
    }

    /**
     * Konec popisu, který je seznam: aspoň 3 krátké řádky (do 3 slov, bez tečky na konci) odsazené vůči prvnímu řádku popisu
     * (ikonka barvy před nimi) a pod sebou. Vrací ty řádky (prázdné = seznam není).
     */
    internal fun listTail(body: List<MenuLine>, w: Int): List<MenuLine> {
        if (body.size < 4) return emptyList()
        val first = body.first()
        val tail = body.drop(1).takeLastWhile { l ->
            l.left - first.left > w * 0.012f && l.text.trim().split(Regex("\\s+")).size <= 3 && !l.text.trim().endsWith(".")
        }
        return if (tail.size >= 3) tail else emptyList()
    }

    /** Tlačítka v patičce menu (GTA VI) a jejich pevný překlad – jiné jazyky přes překladač. */
    val FOOTER = listOf("RESTORE ALL", "RESTORE DEFAULT", "RESTORE DEFAULTS", "BACK", "SELECT", "CONFIRM", "CANCEL", "APPLY", "RESET", "EXIT", "DELETE", "CHANGE",
        // od 0.97.126 (dávka DR7N-3W65): Schéma ovládání a menu zleva
        "PAUSE PREVIEW", "RESUME PREVIEW", "PHOTO STUDIO",
        // od 0.97.144 (dávka GVFD-XYV8): seznam vpravo dole na mapě
        "RECENTER", "2D VIEW", "3D VIEW", "ADD MARKER", "REMOVE MARKER", "SET DESTINATION", "REMOVE DESTINATION")
    /** Pevný překlad tlačítka patičky (slovník ze seznamu her, [GameDictionaries.footer]), jinak null (přeloží překladač). */
    fun footerWord(lang: String, item: String): String? = GameProfiles.dict.footer[lang]?.get(item)
    private val FOOTER_KEYS = FOOTER.associateBy { TextLogic.simplify(it) }

    /** Ikona tlačítka u položky patičky (GTA VI na PS5: SELECT ×, BACK ○, RESTORE ALL △, RESTORE DEFAULT □), jinak null. */
    val FOOTER_ICONS = mapOf("SELECT" to "x", "CONFIRM" to "x", "BACK" to "o", "CANCEL" to "o", "EXIT" to "o",
        "RESTORE ALL" to "t", "RESTORE DEFAULT" to "s", "RESTORE DEFAULTS" to "s", "PAUSE PREVIEW" to "R3", "RESUME PREVIEW" to "R3",
        "PHOTO STUDIO" to "s", "RECENTER" to "L3", "2D VIEW" to "R3", "3D VIEW" to "R3", "ADD MARKER" to "s", "REMOVE MARKER" to "s",
        "SET DESTINATION" to "x", "REMOVE DESTINATION" to "x")

    /**
     * Patička Nastavení vpravo dole (od 0.97.111, přání majitele: „přeložit i ty tři tlačítka“): řádky pod 90 % výšky v pravé
     * polovině, známé názvy tlačítek ze slov čtečky (ikonky se přečtou jako znaky navíc a vynechají se). Obdélník = slova názvu.
     */
    internal fun footer(lines: List<MenuLine>, w: Int, h: Int): List<Pair<String, FloatArray>> {
        val out = ArrayList<Pair<String, FloatArray>>()
        // od 0.97.144 i svislý seznam vpravo dole na mapě (RECENTER … EXIT, od ~0,76 výšky); jen slova bez malých písmen
        for (l in lines.filter { (it.top > h * 0.9f && it.left > w * 0.5f) || (it.top > h * 0.72f && it.left > w * 0.78f) }.sortedBy { it.left }) {
            val ws = l.words.filter { it.text.any { c -> c.isLetter() } && it.text.none { c -> c.isLowerCase() } }
            var i = 0
            while (i < ws.size) {
                var hit: String? = null; var len = 0
                for (n in 2 downTo 1) {
                    if (i + n > ws.size) continue
                    FOOTER_KEYS[TextLogic.simplify(ws.subList(i, i + n).joinToString(" ") { it.text })]?.let { hit = it; len = n }
                    if (hit != null) break
                }
                val name = hit
                if (name != null) {
                    val g = ws.subList(i, i + len)
                    out += name to floatArrayOf(g.minOf { it.left }.toFloat() / w, g.minOf { it.top }.toFloat() / h, g.maxOf { it.right }.toFloat() / w, g.maxOf { it.bottom }.toFloat() / h)
                    i += len
                } else i++
            }
        }
        return out.distinctBy { it.first }
    }

    /** Dolní hranice čtení popisu (pod ní jsou v GTA VI jen nápovědy tlačítek). */
    const val BODY_BOTTOM = 0.85f

    /**
     * Řádky popisu pod nadpisem ve stejném sloupci – až po [BODY_BOTTOM] výšky: u voleb s obrázkem je popis až pod
     * obrázkem (~64–75 % výšky, tester P2ZT-VKQ5 – dřív se bralo jen 8 řádků pod nadpisem a popis se nenašel).
     * Krátké kousky (ikonky z obrázku, „(?)“) se nepočítají. Když je mezi řádky velká mezera (obrázek), bere se text za ní.
     */
    internal fun descBody(head: MenuLine, lines: List<MenuLine>, w: Int, h: Int): List<MenuLine> {
        val lineH = maxOf(1, head.bottom - head.top)
        val col = lines.filter { it.top > head.top && it.top < h * BODY_BOTTOM && !isHeading(it.text) &&
            kotlin.math.abs(it.left - head.left) < w * 0.06f && it.text.count { c -> c.isLetter() } >= 3 }.sortedBy { it.top }
        if (col.isEmpty()) return col
        // skupiny řádků oddělené velkou mezerou (obrázek, konec popisu)
        val groups = ArrayList<MutableList<MenuLine>>()
        for (l in col) {
            val g = groups.lastOrNull()
            if (g == null || l.top - g.last().bottom > lineH * 4) groups += mutableListOf(l) else g += l
        }
        // popis hned pod nadpisem → první skupina; pod obrázkem → poslední (popisky v obrázku jsou výš)
        return if (groups[0][0].top - head.bottom <= lineH * 4) groups[0] else groups.last()
    }

    /**
     * Kam překlad nadpisu: nad anglický nadpis do volného místa (nahoře lišta záložek / jiný text ve sloupci), písmo
     * podle nadpisu ve hře. null = nad nadpisem je méně než 1,3 řádku nadpisu → nadpis jde s popisem.
     */
    internal fun titlePlace(head: MenuLine, lines: List<MenuLine>, descBox: FloatArray?, w: Int, h: Int): FloatArray? {
        val th = maxOf(1, head.bottom - head.top)
        val above = lines.filter { it !== head && it.bottom <= head.top && it.right > head.left && it.left < w * 0.995f }
            .maxOfOrNull { it.bottom } ?: 0
        val top = maxOf(above + th / 3, (h * 0.11f).toInt(), head.top - th * 3)
        val bottom = head.top - maxOf(2, th / 6)
        if (bottom - top < th * 1.3f) return null
        val left = (head.left.toFloat() / w - 0.006f).coerceAtLeast(0f)
        val right = descBox?.get(2) ?: 0.97f
        return floatArrayOf(left, top.toFloat() / h, right, bottom.toFloat() / h)
    }

    /** Dolní hranice překladu popisu, když pod ním ve hře nic není (dole bývá nápověda tlačítek). */
    const val DESC_BOTTOM = 0.88f

    /**
     * Kam dát překlad nadpisu a popisu volby (od 0.97.59, přání majitele: přes anglický popis zasahoval doleva do seznamu
     * voleb): anglický popis nechat, překlad pod něj do volného místa – vlevo nejdál k levé hraně nadpisu, nahoře hned pod
     * posledním řádkem popisu, vpravo k okraji sloupce, dole po nejbližší text ve sloupci pod popisem (jinak [DESC_BOTTOM]).
     * Výsledek = vlevo, nahoře, vpravo, nejníž dole (zlomky); text se zalomí na šířku a když se nevejde, zmenší se písmo.
     * Když pod popisem skoro nic volného není (méně než 6 % výšky), jde překlad přes anglický nadpis a popis jako dřív.
     */
    internal fun descPlace(head: MenuLine, body: List<MenuLine>, lines: List<MenuLine>, w: Int, h: Int): FloatArray {
        val all = listOf(head) + body
        val left = (head.left.toFloat() / w - 0.006f).coerceAtLeast(0f)
        val right = maxOf(all.maxOf { it.right }.toFloat() / w + 0.01f, 0.97f).coerceAtMost(0.995f)
        val textBottom = all.maxOf { it.bottom }
        val below = lines.filter { it.top > textBottom && it.right > head.left && it !in all }.minOfOrNull { it.top }
        val limit = minOf(below?.let { it.toFloat() / h - 0.008f } ?: DESC_BOTTOM, DESC_BOTTOM)
        val top = textBottom.toFloat() / h + 0.012f
        return if (limit - top >= 0.06f) floatArrayOf(left, top, right, limit)
        else floatArrayOf(left, (head.top.toFloat() / h - 0.008f).coerceAtLeast(0f), right, maxOf(limit, textBottom.toFloat() / h + 0.008f))
    }

    /** Záložka lišty Nastavení: název z profilu, jas pod ní (−1 = nevíme) a rámeček v pixelech. */
    internal data class Tab(val name: String, val bright: Int, val left: Int, val top: Int, val right: Int, val bottom: Int, val peak: Int = -1)

    /**
     * Záložky v jednom řádku: najde známé názvy zleva doprava. S rámečky slov ([MenuLine.words], od 0.97.58) má každá
     * záložka svůj rámeček a jas (vybraná se pozná, i když čtečka lištu spojí); bez nich jeden jas na řádek.
     */
    internal fun splitTabs(l: MenuLine, spec: MenuSpec): List<Tab> {
        // slova z rámečků čtečky (od 0.97.107): text řádku se dřív dělil podle mezer a když počet nesouhlasil s rámečky
        // (uříznuté „ACCO“, spojené slovo), jas záložek se nepočítal → vybraná se hádala (tester TUAT-DWY4: pořád „Zvuk“)
        val boxes = l.words.filter { it.text.isNotBlank() }.takeIf { it.isNotEmpty() }
        val words = boxes?.map { it.text.trim() } ?: l.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = ArrayList<Tab>()
        var i = 0
        while (i < words.size) {
            var hit: String? = null; var len = 0
            for (n in 3 downTo 1) {
                if (i + n > words.size) continue
                spec.tabKeys[TextLogic.simplify(words.subList(i, i + n).joinToString(" "))]?.let { hit = it; len = n }
                if (hit != null) break
            }
            val name = hit
            if (name != null) {
                val ws = boxes?.subList(i, i + len)
                out += if (ws != null) Tab(name, ws.sumOf { it.bright } / ws.size, ws.minOf { it.left }, ws.minOf { it.top },
                    ws.maxOf { it.right }, ws.maxOf { it.bottom }, if (ws.any { it.peak < 0 }) -1 else ws.sumOf { it.peak } / ws.size)
                else Tab(name, l.bright, l.left, l.top, l.right, l.bottom)
                i += len
            } else i++
        }
        // bez rámečků slov: jeden řádek = jeden jas; víc záložek v řádku se rozliší jen když čtečka dá každou zvlášť
        return if (out.size > 1 && boxes == null) out.map { it.copy(bright = -1) } else out
    }

    /**
     * Kam položit překlad vybrané záložky: těsně nad ni (nad lištou je v GTA VI jen nadpis SETTINGS), levý okraj
     * jako záložka. Vrací levý horní roh a výšku řádku (zlomky; jako [MenuView.box] a [MenuView.step] – pruh se
     * položí od `y − 0,25 × řádek` a je vysoký jeden řádek). null = rámeček záložky neznáme.
     */
    /**
     * Znak otevřeného menu zleva / Nastavení z jasu (od 0.97.132): sloupec 16 políček vpravo v pruhu položek (x 0,17–0,25,
     * y 0,18–0,66) – tmavý stejnoměrný panel a v něm jeden světlý pruh vybrané položky. Na snímcích testerů chytí všechna
     * menu a ze stovek herních snímků se spletl dvakrát (pak jen jedno čtení navíc). Levné – čte se na každém snímku.
     */
    val PAUSE_GATE: List<FloatArray> = (0 until 16).map { i -> val y = 0.18f + i * 0.03f; floatArrayOf(0.17f, y, 0.25f, y + 0.022f) }

    fun pauseGate(v: IntArray): Boolean {
        if (v.size < 8) return false
        val m = v.indices.maxByOrNull { v[it] } ?: return false
        if (v[m] < 150) return false
        val lo = v.indices.filter { kotlin.math.abs(it - m) > 1 }.map { v[it] }
        if (lo.size < 6) return false
        return lo.max() <= 110 && lo.max() - lo.min() <= 25 && v[m] - lo.max() >= 90
    }

    /**
     * Panel mise vpravo s výběrem (od 0.97.140, fotky majitele 23. 11. 2026 – Retry Checkpoint / Restart Mission / Quit Mission v bílém
     * pruhu, menu zleva pak žádný pruh nemá): sloupec políček v pruhu vlevo od textu položek (x 0,775–0,835, y 0,20–0,44).
     */
    val MISSION_GATE: List<FloatArray> = (0 until 9).map { i -> val y = 0.20f + i * 0.03f; floatArrayOf(0.775f, y, 0.835f, y + 0.022f) }

    /**
     * Menu otevřené s výběrem v panelu mise: v [MISSION_GATE] jeden světlý pruh nad tmavším pozadím a menu zleva ([left] = jas
     * [PAUSE_GATE]) je tmavý stejnoměrný panel bez pruhu. Pozadí vpravo je rozmazaná hra – proto bez podmínky stejnoměrnosti.
     */
    fun missionGate(right: IntArray, left: IntArray): Boolean {
        if (right.size < 6 || left.size < 8) return false
        if (left.max() > 110 || left.max() - left.min() > 25) return false
        val m = right.indices.maxByOrNull { right[it] } ?: return false
        if (right[m] < 150) return false
        val lo = right.indices.filter { kotlin.math.abs(it - m) > 1 }.map { right[it] }
        if (lo.size < 4) return false
        return lo.max() <= 120 && right[m] - lo.max() >= 70
    }

    /**
     * Kde se u položky panelu mise pozná bílý pruh: kus vlevo od textu (pruh sahá k levému konci čar panelu ~0,755 šířky).
     * Kapitola („CHAPTER 1“) se vybrat nedá – prázdné pole.
     */
    fun sideRects(side: List<Pair<String, FloatArray>>): List<FloatArray> = side.map { (t, b) ->
        val l = maxOf(0.765f, b[0] - 0.07f); val r = b[0] - 0.015f
        if (chapter(t) != null || b.size < 4 || r - l < 0.02f) FloatArray(0) else floatArrayOf(l, b[1], r, b[3])
    }

    private val CHAPTER = Regex("^chapter\\s+(\\d{1,2})$", RegexOption.IGNORE_CASE)

    /** Kapitola mise („CHAPTER 2“) – číslo se nepřekládá; vrací číslo, nebo null. */
    fun chapter(t: String): String? = CHAPTER.find(t.trim())?.groupValues?.get(1)

    /** Panel mise vpravo v menu zleva ([MenuView.side]): řádky vpravo nahoře, které profil hry umí přeložit. */
    internal fun sidePanel(lines: List<MenuLine>, w: Int, h: Int, spec: MenuSpec): List<Pair<String, FloatArray>> =
        lines.filter { it.left > w * 0.6f && it.top > h * 0.04f && it.bottom < h * 0.5f }
            .filter { l -> chapter(l.text) != null || spec.word(Lang.CS, l.text.trim()) != null }
            .sortedBy { it.top }.take(8)
            .map { l -> l.text.trim() to floatArrayOf(l.left.toFloat() / w, l.top.toFloat() / h, l.right.toFloat() / w, l.bottom.toFloat() / h) }

    /**
     * Popisky panelu mise: překlad vlevo od řádku (zarovnaný doprava k jeho začátku), bez pozadí se stínem – originál zůstane vidět.
     */
    fun sideLabels(side: List<Pair<String, FloatArray>>, texts: List<String>, bg: Int = 0, selected: Int = -1): List<MenuLabel> {
        if (side.isEmpty()) return emptyList()
        val hgt = side.map { it.second[3] - it.second[1] }.sorted()[side.size / 2]
        return side.indices.mapNotNull { i ->
            val (_, b) = side[i]
            val t = texts.getOrNull(i)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val cy = (b[1] + b[3]) / 2f
            MenuLabel(t, floatArrayOf(0.35f, cy - hgt * 0.6f, b[0] - 0.012f, cy + hgt * 0.6f), Math.round(hgt * 1080 * PAUSE_FONT), right = true,
                // vybraná v bílém pruhu hry: černě na bílém (pruh hry pod ní pokračuje – od 0.97.140)
                selected = i == selected, bg = if (i == selected) 100 else bg)
        }
    }

    /** Pravý okraj bílého pruhu vybrané položky menu zleva (GTA VI ~26,5 % šířky). */
    const val PAUSE_BAR_RIGHT = 0.262f
    /**
     * Pozadí překladů menu hry (Nastavení vpravo – nadpis, popis, seznam; patička; Fotostudio) v % neprůhlednosti.
     * Od 0.97.143 30 % = 70 % průhlednost (přání majitele 23. 11. 2026, platí i pro další obrazovky menu); dřív 70 %. Od 1.00-rc24 40 % = 60 % průhlednost.
     */
    const val SETTINGS_BG = 40   // od 1.00-rc24 40 % (majitel: „průhlednost uber 10 %“)

    /**
     * Menu zleva (od 0.97.115, přání majitele): překlad každé položky hned vpravo od anglického názvu v jejím řádku, bez
     * vlastního pozadí – vybraná leží v bílém pruhu hry, proto černým písmem. Písmo podle výšky názvu.
     */
    fun pauseLabels(v: MenuView, texts: List<String>): List<MenuLabel> {
        val ok = v.items.indices.filter { i -> v.rects.getOrNull(i)?.let { it.size >= 4 && it[3] > it[1] && it[0] < PAUSE_BAR_RIGHT - 0.03f } == true }
        if (ok.isEmpty()) return emptyList()
        // vlastní sloupec zarovnaný vlevo za nejdelším názvem (od 0.97.121, majitel: „ne nalepit za text“); od 0.97.131 bez ikon,
        // text na stejném místě jako s ikonou (posun o šířku bývalé ikony) a jedno písmo pro všechny položky (dřív podle výšky
        // rámečku každé – velikost skákala)
        // od 1.00-rc20 výška řádku z [MenuView.itemLine] – jedna na celé menu, z profilu hry nebo prvních čtení; písmo neposkakuje
        val hgt = v.itemLine.takeIf { it > 0f } ?: itemHeight(ok.map { v.rects[it] })
        val px = Math.round(hgt * 1080 * PAUSE_FONT)
        // od 1.0.1 blíž k názvům (dřív o šířku bývalé ikony 1,4 písma, majitel: „nevejdou se do pásu vybrané položky, napravo o kousek“)
        val col = ok.maxOf { v.rects[it][0] } + 0.006f + px * 0.6f / 1920f
        return ok.mapNotNull { i ->
            val r = v.rects[i]
            val t = texts.getOrNull(i)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val cy = (r[1] + r[3]) / 2f
            MenuLabel(t, floatArrayOf(col, cy - hgt * 0.6f, PAUSE_BAR_RIGHT, cy + hgt * 0.6f), px)
        }
    }

    /**
     * Fotostudio (od 0.97.139): překlad každé volby ve sloupci hned vpravo od průsvitného panelu (v panelu je vpravo hodnota),
     * pozadí 70 %, vybraná černě na bílém jako ve hře.
     */
    fun photoLabels(v: MenuView, texts: List<String>): List<MenuLabel> {
        val ok = v.items.indices.filter { i -> v.rects.getOrNull(i)?.let { it.size >= 4 && it[3] > it[1] } == true }
        if (ok.isEmpty()) return emptyList()
        val hgt = v.itemLine.takeIf { it > 0f } ?: itemHeight(ok.map { v.rects[it] })
        return ok.mapNotNull { i ->
            val r = v.rects[i]
            val t = texts.getOrNull(i)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val cy = (r[1] + r[3]) / 2f
            val sel = i == v.selected
            MenuLabel(t, floatArrayOf(PHOTO_COL, cy - hgt * 0.65f, 0.62f, cy + hgt * 0.65f), Math.round(hgt * 1080 * 1.05f),
                selected = sel, bg = if (sel) 100 else SETTINGS_BG)
        }
    }

    /**
     * Rámeček z nového čtení, nebo dřívější, když se liší jen o chvění čtečky (od 1.00-rc24): vodorovně do [STEADY_X]
     * šířky obrazu, svisle do [STEADY_Y] výšky dřívějšího rámečku na každé straně. Skutečný posun (jiný řádek, posun
     * seznamu Fotostudia) je větší – vezme se nový.
     */
    internal fun steady(old: FloatArray?, new: FloatArray): FloatArray {
        if (old == null || old.size < 4 || new.size < 4) return new
        val tolY = (old[3] - old[1]) * STEADY_Y
        val ok = kotlin.math.abs(new[0] - old[0]) <= STEADY_X && kotlin.math.abs(new[2] - old[2]) <= STEADY_X &&
            kotlin.math.abs(new[1] - old[1]) <= tolY && kotlin.math.abs(new[3] - old[3]) <= tolY
        return if (ok) old else new
    }

    /** [steady] po pojmenovaných rámečcích (patička, panel mise): stejný název → dřívější místo, když se nepohnul. */
    internal fun steadyAll(old: List<Pair<String, FloatArray>>, new: List<Pair<String, FloatArray>>): List<Pair<String, FloatArray>> =
        new.map { (n, r) -> n to steady(old.firstOrNull { it.first == n }?.second, r) }

    /**
     * Odstín písma překladů menu (od 1.00-rc24, majitel: „viditelnější odstín textu, aby byl rozdíl oproti originálu, když to
     * nepřekrýváme“ – mapa, menu zleva, patička): světle zlatá místo bílé jako ve hře; vybraná položka (černě na bílém) zůstává.
     */
    const val TINT = 0xFFFFE28A.toInt()
    const val TINT_HEX = "#FFE28A"

    /** Ztlumený odstín [TINT] pro zašedlou (vypnutou) položku ovládání – pořád poznat, že jde o překlad (1.0.2, přání majitele). */
    const val TINT_DIM = 0xFF8C7C4C.toInt()
    const val TINT_DIM_HEX = "#8C7C4C"

    /** [steady] pro popisky ovladače: nové čtení, které jen vynechá některé z dřívějších, dřívější nemění. */
    internal fun steadyControls(old: List<Triple<String, FloatArray, Boolean>>, new: List<Triple<String, FloatArray, Boolean>>): List<Triple<String, FloatArray, Boolean>> {
        if (old.isEmpty()) return new
        if (new.all { n -> old.any { it.first == n.first } }) return old
        // stejný počet jako dřív (čtení jen zadrhlo na textu jedné položky, ne že se seznam změnil) – drž místo i
        // velikost podle pořadí, ne podle přesné shody textu (hlášení DSXW-MJSD: popisky „poskakovaly“ v menu
        // Layout ovladače, kde se stejný seznam jen občas přečetl s drobnou chybou v textu jedné položky)
        if (new.size == old.size) return new.mapIndexed { i, (t, r, left) -> Triple(t, steady(old[i].second, r), left) }
        return new.map { (t, r, left) -> Triple(t, steady(old.firstOrNull { it.first == t }?.second, r), left) }
    }

    const val STEADY_X = 0.012f
    const val STEADY_Y = 0.35f

    /** Výška řádku položek z jednoho čtení: medián výšek rámečků (zlomky); 0 = žádný použitelný. */
    internal fun itemHeight(rects: List<FloatArray>): Float {
        val hs = rects.filter { it.size >= 4 && it[3] > it[1] }.map { it[3] - it[1] }.sorted()
        return hs.getOrNull(hs.size / 2) ?: 0f
    }

    /** Levý okraj sloupce s překladem voleb Fotostudia (panel ve hře končí ~28,5 % šířky). */
    const val PHOTO_COL = 0.296f

    /** Překlad nadpisu podmenu / Fotostudia hned vpravo od anglického („GAME HRA“), stejně vysokým písmem. */
    fun headLabel(head: Pair<String, FloatArray>?, text: String?, bg: Int): MenuLabel? {
        val b = head?.second ?: return null
        if (text.isNullOrBlank() || b.size < 4 || b[3] <= b[1]) return null
        val hh = b[3] - b[1]
        return MenuLabel(text, floatArrayOf(b[2] + 0.015f, b[1] - hh * 0.15f, minOf(1f, b[2] + 0.45f), b[3] + hh * 0.15f), Math.round(hh * 1080 * 0.95f), bg = bg)
    }

    /** Písmo překladu menu zleva vůči výšce rámečku názvu (od 0.97.121 větší – majitel: „písmo vypadá menší“). */
    const val PAUSE_FONT = 1.0f   // od 1.0.1 menší (dřív 1,15 – přetékalo z pásu vybrané položky)


    /**
     * Nastavení: překlad vybrané záložky pod ní (od 0.97.122, přání majitele: „pod tu položku, tam je místa dost“) –
     * černě na bílém jako vybraná záložka ve hře, bez černého pruhu (do 0.97.121 bílé písmo na černém).
     */
    fun tabLabel(rect: FloatArray?, text: String?): MenuLabel? {
        if (text.isNullOrBlank() || rect == null || rect.size < 4 || rect[3] <= rect[1]) return null
        val tabH = rect[3] - rect[1]
        // lišta mapy / Briefu / Postupu nahoře (od 0.97.144, přání majitele: „jen vybranou a možná se to vejde nad“):
        // nad záložkou je místo, pod ní mapa / seznam → překlad nad ni, menším písmem, pozadí jako ostatní překlady menu
        if (rect[1] < TOP_BAR && rect[1] >= tabH * 1.3f) {
            val bottom = rect[1] - tabH * 0.1f
            val top = maxOf(0.002f, bottom - tabH * 1.15f)
            return MenuLabel(text, floatArrayOf((rect[0] - 0.004f).coerceAtLeast(0f), top, minOf(1f, rect[0] + 0.4f), bottom),
                Math.round((bottom - top) * 1080 * 0.85f), bg = SETTINGS_BG)
        }
        val top = rect[3] + tabH * 0.45f
        return MenuLabel(text, floatArrayOf((rect[0] - 0.004f).coerceAtLeast(0f), top, minOf(1f, rect[0] + 0.4f), top + tabH * 1.25f),
            Math.round(tabH * 1080 * 0.95f), selected = true, bg = 100)
    }

    /**
     * Patička (tlačítka vpravo dole, od 0.97.115 – přes původní se kryla): v menu zleva jeden řádek nad tlačítky zarovnaný
     * doprava (od 0.97.142), v Nastavení jeden řádek u levého okraje ve výšce patičky (nad tlačítky je tam popis volby).
     */
    fun footerLabels(footer: List<Pair<String, FloatArray>>, texts: List<String>, settings: Boolean, fixedRight: Float = 0f): List<MenuLabel> {
        val items = footer.indices.mapNotNull { i -> texts.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { Triple(it, footer[i].second, FOOTER_ICONS[footer[i].first]) } }
            .filter { it.second.size >= 4 }
        if (items.isEmpty()) return emptyList()
        val hFrac = items.map { it.second[3] - it.second[1] }.sorted()[items.size / 2]
        val top = items.minOf { it.second[1] }; val bottom = items.maxOf { it.second[3] }
        // svislý seznam (mapa: RECENTER / 2D VIEW / ADD MARKER / SET DESTINATION / EXIT; od 0.97.144): každý řádek zvlášť
        // vlevo od originálu, zarovnaný doprava, ve stejné výšce
        val rows = items.map { it.second[1] }.sorted().fold(ArrayList<Float>()) { acc, t -> if (acc.isEmpty() || t - acc.last() > hFrac * 0.8f) acc += t; acc }
        if (rows.size >= 3) return items.map { (t, b, icon) ->
            val r = b[0] - 0.012f
            MenuLabel(t, floatArrayOf(maxOf(0.3f, r - 0.4f), b[1] - hFrac * 0.15f, r, b[3] + hFrac * 0.15f),
                Math.round(hFrac * 1080 * FOOTER_FONT), right = true, bg = SETTINGS_BG, parts = listOf(t to icon))
        }
        // menu zleva (od 0.97.142, dávka GVFD-XYV8, přání majitele): jeden řádek NAD tlačítky vpravo dole, zarovnaný doprava
        // k poslednímu tlačítku – vlevo dole zakrýval text hry („Build …“ v podmenu Hra)
        if (!settings) {
            // pravý okraj = konec ikony posledního tlačítka ve hře (od 1.00-rc20, majitel: „mírně offsetnuté vlevo“): čtečka vrací
            // jen text názvu, ikona hry je vpravo za ním (dávka DH7E-VPFV: BACK končí 930, ikona ○ 941 z 960; HVWD-HYWD: 920 → 937 –
            // i s mezerou ~0,4–0,8 výšky textu v zlomcích, bere se 0,6); u nás ikona končí před vnitřním okrajem popisku (0,25 písma) – o obojí doprava,
            // ať ikony leží nad sebou. Profil hry ([MenuLayout.footerRight]) má přednost, když se od změřeného liší málo.
            val last = items.maxBy { it.second[2] }
            val px = hFrac * FOOTER_FONT
            val shift = (if (last.third != null) hFrac * 0.6f else 0f) + px * 0.25f * 1080f / 1920f
            val measured = minOf(0.998f, last.second[2] + shift)
            val right = if (fixedRight > 0f && kotlin.math.abs(measured - fixedRight) <= FOOTER_TOLERANCE) fixedRight else measured
            val lineBottom = top - hFrac * 0.5f
            val lineTop = lineBottom - hFrac * 1.4f
            return listOf(MenuLabel(items.joinToString("   ") { it.first }, floatArrayOf(maxOf(0.3f, right - 0.6f), lineTop, right, lineBottom),
                Math.round(hFrac * 1080 * FOOTER_FONT), right = true, bg = SETTINGS_BG, parts = items.map { it.first to it.third }))
        }
        // Nastavení: jeden řádek u levého okraje obrazovky ve výšce patičky, menším písmem (od 0.97.125, dávka DR7N-3W65 – nad
        // tlačítky se u víc položek překlady překrývaly s originálem)
        val right = minOf(items.minOf { it.second[0] } - 0.02f, 0.75f)
        return listOf(MenuLabel(items.joinToString("   ") { it.first }, floatArrayOf(FOOTER_LEFT, top - hFrac * 0.15f, right, bottom + hFrac * 0.15f),
            Math.round(hFrac * 1080 * FOOTER_FONT), bg = SETTINGS_BG, parts = items.map { it.first to it.third }))
    }

    /** Levý okraj řádku s překladem patičky a písmo vůči výšce názvu tlačítka (od 0.97.125). */
    const val FOOTER_LEFT = 0.025f
    const val FOOTER_FONT = 0.85f
    /** Rychlá kontrola pravého okraje patičky z profilu: změřený dál než tohle (zlomek šířky) = hra se změnila, platí změřený. */
    const val FOOTER_TOLERANCE = 0.03f

    /** Lišta záložek nahoře uprostřed (mapa, Brief, Postup – horní hrana ~3,5 % výšky; Nastavení a Nápověda ~7,5 %). */
    const val TOP_BAR = 0.06f

    /**
     * Vybraná záložka lišty mapy / Briefu: písmo výrazně tmavší než ostatní (růžové ~186 proti bílým ~253, dávka GVFD-XYV8).
     * −1 = nepoznáno (chybí jas, nebo žádná výrazně tmavší).
     */
    internal fun dimmest(peaks: List<Int>): Int {
        if (peaks.size < 3 || peaks.any { it < 0 }) return -1
        val sorted = peaks.sorted()
        val min = sorted[0]; val next = sorted[1]
        return if (next >= 215 && next - min >= 35) peaks.indexOf(min) else -1
    }

    /** Zbytky ikon tlačítek u popisků ovladače („L1“, „R3“, „O“, „(R3“). */
    private val ICON_TOKEN = Regex("^[(\\[]?(?:[LR][123]?|[OX0©®]|[^\\p{L}]+)[)\\]]?$")

    /**
     * Popisky kolem ovladače ve Schématu ovládání (od 0.97.144, dávka GVFD-XYV8): řádky VERZÁLKAMI mezi ~20 a 80 % výšky,
     * levý sloupec (vpravo zarovnaný, končí před ikonou ~0,5 šířky) a pravý sloupec (od ~0,84). Ikony tlačítek se přečtou
     * jako „L1“, „R3“, „O“ – vynechají se. Vrací text, obdélník textu (zlomky) a zda je to levý sloupec.
     */
    internal fun controls(lines: List<MenuLine>, head: MenuLine?, w: Int, h: Int): List<Triple<String, FloatArray, Boolean>> {
        val out = ArrayList<Triple<String, FloatArray, Boolean>>()
        for (l in lines) {
            if (l === head || l.top < h * 0.18f || l.bottom > h * 0.82f || l.left < w * 0.3f) continue
            val ws = (l.words.ifEmpty { listOf(MenuWord(l.text, l.left, l.top, l.right, l.bottom, l.bright)) })
                .filter { !ICON_TOKEN.matches(it.text.trim()) }
            if (ws.isEmpty()) continue
            val text = ws.joinToString(" ") { it.text.trim() }.trim()
            val letters = text.filter { it.isLetter() }
            if (letters.length < 2 || letters.any { it.isLowerCase() }) continue
            val r = floatArrayOf(ws.minOf { it.left }.toFloat() / w, ws.minOf { it.top }.toFloat() / h, ws.maxOf { it.right }.toFloat() / w, ws.maxOf { it.bottom }.toFloat() / h)
            val left = r[2] < 0.62f
            if (!left && r[0] < 0.72f) continue
            out += Triple(text, r, left)
        }
        // aspoň 3 popisky v jednom ze sloupců – jinak to není schéma ovladače
        return if (out.count { it.third } >= 3 || out.count { !it.third } >= 3) out.sortedBy { it.second[1] } else emptyList()
    }

    /**
     * Překlady popisků ovladače: menším písmem hned pod každým popiskem (mezi řádky je ve hře volno), v levém sloupci
     * zarovnané doprava k jeho konci, v pravém doleva od jeho začátku; pozadí jako ostatní překlady menu.
     */
    /**
     * Volby v seznamu vlevo v Nastavení a Nápovědě (od 1.0.1, majitel: „v settings zkusit dát překlad i vlevo ke každé položce“):
     * řádky s malými písmeny (nadpisy oddílů jsou verzálkami) od levého okraje, pod lištou a nad počítadlem „1 / 9“.
     * Text bez ikon ([dropIcons]).
     */
    internal fun options(lines: List<MenuLine>, w: Int, h: Int): List<Pair<String, FloatArray>> =
        lines.filter { it.left < w * 0.06f && it.right < w * 0.4f && it.top > h * 0.18f && it.bottom < h * 0.78f }
            .mapNotNull { l ->
                val t = dropIcons(l.text)
                if (t.count { it.isLetter() } < 3 || t.none { it.isLowerCase() }) null
                else t to floatArrayOf(l.left.toFloat() / w, l.top.toFloat() / h, l.right.toFloat() / w, l.bottom.toFloat() / h)
            }.sortedBy { it.second[1] }.take(12)

    /**
     * Překlady voleb seznamu vlevo: každý ve sloupci hned za seznamem ([OPTION_COL], v Nápovědě za nejdelší položkou) ve
     * výšce své volby, menším písmem, pozadí jako ostatní překlady menu. Místa je málo – ještě se bude ladit.
     */
    fun optionLabels(options: List<Pair<String, FloatArray>>, texts: List<String>): List<MenuLabel> {
        if (options.isEmpty()) return emptyList()
        val col = maxOf(OPTION_COL, options.maxOf { it.second[2] } + 0.02f)
        return options.indices.mapNotNull { i ->
            val (_, r) = options[i]
            val t = texts.getOrNull(i)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val hh = r[3] - r[1]
            val cy = (r[1] + r[3]) / 2f
            MenuLabel(t, floatArrayOf(col, cy - hh * 0.6f, minOf(0.98f, col + 0.3f), cy + hh * 0.6f), Math.round(hh * 1080 * 0.85f), bg = SETTINGS_BG)
        }
    }

    /** Levý okraj sloupce s překlady voleb (seznam Nastavení končí ~28 % šířky). */
    const val OPTION_COL = 0.29f

    fun controlLabels(controls: List<Triple<String, FloatArray, Boolean>>, texts: List<String>): List<MenuLabel> =
        controls.indices.mapNotNull { i ->
            val (_, r, left) = controls[i]
            val t = texts.getOrNull(i)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val hh = r[3] - r[1]
            val top = r[3] + hh * 0.2f
            val bottom = top + hh * 1.05f
            if (left) MenuLabel(t, floatArrayOf(maxOf(0.28f, r[2] - 0.3f), top, r[2], bottom), Math.round(hh * 1080 * 0.8f), right = true, bg = SETTINGS_BG)
            else MenuLabel(t, floatArrayOf(r[0], top, minOf(0.995f, r[0] + 0.3f), bottom), Math.round(hh * 1080 * 0.8f), bg = SETTINGS_BG)
        }

    fun tabPlace(rect: FloatArray?): Pair<Pair<Float, Float>, Float>? {
        if (rect == null || rect.size < 4 || rect[3] <= rect[1]) return null
        val tabH = rect[3] - rect[1]
        val bottom = rect[1] - 0.004f
        if (bottom <= 0.01f) return null
        val step = minOf(tabH * 2.4f, bottom)
        val top = (bottom - step).coerceAtLeast(0f)
        return ((rect[0] - 0.006f).coerceAtLeast(0f) to top + step * 0.25f) to step
    }

    /**
     * Pásy levého sloupce Nastavení (seznam voleb, zlomky obrazu), jejichž jas služba hlídá na každém snímku:
     * posun bílého pruhu na jinou volbu = jiný popis vpravo → číst hned, ne až po intervalu.
     */
    val WATCH: List<FloatArray> = (0 until 20).map { i -> val y = 0.15f + i * 0.035f; floatArrayOf(0.02f, y, 0.48f, y + 0.03f) }

    /** Změnil se jas některého pásu o víc než [limit] (0–255)? Bez předchozího měření ne. */
    fun watchChanged(prev: IntArray?, cur: IntArray, limit: Int = 35): Boolean {
        if (prev == null || prev.size != cur.size) return false
        for (i in cur.indices) if (kotlin.math.abs(cur[i] - prev[i]) > limit) return true
        return false
    }

    /**
     * Rozestup položek pod sebou: nejmenší rozdíl sousedních přečtených položek (chybějící položka by rozdíl
     * zdvojnásobila). 0 = neznámý (méně než 2 položky).
     */
    internal fun rowStep(tops: List<Int>, h: Int): Float {
        val d = tops.sorted().zipWithNext { a, b -> b - a }.filter { it > 0 }.sorted()
        if (d.isEmpty() || h <= 0) return 0f
        // medián rozestupů blízkých obvyklému (vynechaná položka by rozestup zdvojnásobila, jeden zkreslený malý rozdíl
        // by písmo zmenšil – od 0.97.107, tester TUAT-DWY4: „zmenšily se položky v hlavním menu“)
        val m = d[d.size / 2]
        val near = d.filter { it >= m * 0.6f && it <= m * 1.6f }.ifEmpty { d }
        return near[near.size / 2].toFloat() / h
    }

    /** Vybraná = výrazně nejsvětlejší (bílý pruh); jinak −1. */
    internal fun selected(bright: List<Int>): Int {
        if (bright.size < 2 || bright.any { it < 0 }) return -1
        val sorted = bright.sorted()
        val max = sorted.last(); val second = sorted[sorted.size - 2]
        return if (max >= 130 && max - second >= 40) bright.indexOf(max) else -1
    }

    /** Nadpis popisu: verzálky, krátký („HORIZONTAL LOOK SENSITIVITY“). */
    internal fun isHeading(t: String): Boolean {
        val letters = t.filter { it.isLetter() }
        return letters.length >= 2 && letters.length <= 40 && letters.all { it.isUpperCase() }
    }

    /** „HORIZONTAL LOOK SENSITIVITY“ → „Horizontal look sensitivity“ (překladač verzálky překládá hůř). */
    /**
     * Text bez ikon, které čtečka přečte jako znaky (od 1.0.1, majitel: „v nápovědě četlo spoustu ikon jako text a bylo to
     * zmatené“ – ikony zbraní, tašek, skříňky, kufru a tlačítek v nápovědě GTA VI): slova bez písmen a číslic („©“, „@“, „|“),
     * krátké závorky („(9)“, „[R]“), osamělá písmena kromě „a“/„A“/„I“ a dvojznaky číslice+písmeno („1F“). Tlačítka
     * L1/R1/L2/R2/L3/R3 zůstanou (patří do věty „Hold L1 to open…“); čísla a „1/17“ taky.
     */
    internal fun dropIcons(t: String): String = t.split(WS).filter { w0 ->
        val w = w0.trim()
        when {
            w.isEmpty() -> false
            BUTTON_WORD.matches(w) -> true
            w in KEEP_MARKS -> true
            w.none { it.isLetterOrDigit() } -> false
            ICON_BRACKET.matches(w) -> false
            // „L“ / „R“ = páčka v textu („when using R to look around“), zůstává
            w.length == 1 && w[0].isLetter() -> w == "a" || w == "A" || w == "I" || w == "L" || w == "R"
            ICON_PAIR.matches(w) -> false
            else -> true
        }
    }.joinToString(" ").trim()

    private val WS = Regex("\\s+")
    private val BUTTON_WORD = Regex("^[(\\[]?[LR][123][)\\]]?[.,:;!?]?$")
    private val ICON_BRACKET = Regex("^[(\\[][^\\s]{1,2}[)\\]][.,:;!?]?$")
    private val KEEP_MARKS = setOf("&", "/", "-", "–", "+", "%")
    private val ICON_PAIR = Regex("^(\\d[A-Za-z]|[A-Za-z]\\d)$")

    internal fun titleCase(t: String): String = t.lowercase().replaceFirstChar { it.uppercase() }

    /** Ikonka před položkou se čte jako znak navíc („[] Map“, „© Help“) – vzít jen slova. */
    internal fun stripIcon(t: String): String = t.trim().dropWhile { !it.isLetter() }.trim()

    /**
     * Text překladu: u menu zleva položky pod sebou (jako v menu), u Nastavení záložky v řádku jako v liště;
     * vybraná se značkou [MARK]. Pod tím nadpis a popis vybrané volby.
     */
    fun text(kind: String, items: List<String>, selected: Int, title: String?, description: String?): String {
        val marked = items.mapIndexed { i, s -> if (i == selected) MARK + s else s }
        val list = if (kind == "pause") marked.joinToString("\n") else marked.joinToString(" · ")
        val desc = listOfNotNull(title, description).joinToString(": ").ifEmpty { null }
        return listOfNotNull(list, desc).joinToString("\n")
    }
}

/**
 * Menu otevřené na obrazovce v čase: položky se **doplňují** (jedno čtení občas některou vynechá – menu tím
 * neblikne), vybraná se pozná i jako ta, která v čtení chybí (bílý pruh s tmavým písmem se čte špatně), a menu
 * zmizí až po [MISSES] čteních bez něj za sebou (4 × 0,4 s – při posunu výběru hra menu na okamžik překreslí). [update] vrací, co ukázat (null = nic), a jestli se to změnilo.
 *
 * Od 0.97.58 se vybraná položka pozná hlavně **podle jasu na každém novém snímku** ([fast]): služba změří jas
 * známých míst položek ([MenuView.rects]) bez čtečky (pár set bodů) – posun výběru je v překladu do jednoho snímku,
 * ne až po dalším čtení (čtení s čekáním trvá přes 1 s). Čtení jen doplňuje položky, popis a zavření menu;
 * jeho výběr (ze staršího snímku) nepřebije novější výběr podle jasu.
 */
class MenuTracker(private val spec: MenuSpec) {
    private var kind: String? = null
    private val tops = LinkedHashMap<String, Int>()
    private val rects = HashMap<String, FloatArray>()
    private var misses = 0
    private var noSelSince = -1L
    private var gateGoneSince = -1L
    private var last: MenuView? = null
    private var prevStep = 0f
    private val steps = ArrayList<Float>()
    private var descLine0 = 0f
    private var titleLine0 = 0f
    /** Výška řádku položek menu zleva / Fotostudia ([MenuView.itemLine]): výšky z prvních čtení a platná hodnota (od 1.00-rc20). */
    private val itemLines = ArrayList<Float>()
    private var itemLine0 = 0f
    /** Výběr podle jasu: kdy naposledy a která položka (viz [fast]). */
    private var fastAt = -1L
    private var fastItem: String? = null
    /** Které menu téhož druhu (podmenu / nadpis Fotostudia) – jiné = začít znovu (od 0.97.139). */
    private var group: String? = null

    data class Result(val view: MenuView?, val changed: Boolean)

    /** Co je teď ukázané (null = menu zavřené). */
    val view: MenuView? get() = last

    fun reset() { kind = null; tops.clear(); rects.clear(); misses = 0; last = null; prevStep = 0f; fastAt = -1L; fastItem = null; noSelSince = -1L; gateGoneSince = -1L
        steps.clear(); descLine0 = 0f; titleLine0 = 0f; itemLines.clear(); itemLine0 = 0f }

    /** Místa k měření jasu pro [fast] – v pořadí položek ukázaného menu; prázdné = nejde (nějaké místo chybí). */
    fun sampleRects(): List<FloatArray> {
        val v = last ?: return emptyList()
        val out = v.items.mapNotNull { rects[it]?.takeIf { r -> r.size >= 4 && r[2] > r[0] && r[3] > r[1] } }
            // záložky: jas pás pod písmeny ([GameMenu.under]), ne na nich
            .map { r -> if (v.kind == "settings") GameMenu.underFrac(r) else r }
        return if (out.size == v.items.size && out.size >= 2) out else emptyList()
    }

    /**
     * Jas míst ze [sampleRects] na novém snímku (čas [now]) → vybraná položka bez čtečky. Výrazně nejsvětlejší
     * (bílý pruh, [GameMenu.selected]) = vybraná; nejasné měření (přechod, menu se zavírá) nic nemění.
     */
    fun fast(bright: IntArray, now: Long): Result {
        val v = last ?: return Result(null, false)
        if (bright.size != v.items.size) return Result(v, false)
        val sel = GameMenu.selected(bright.toList())
        // bílý pruh vybrané položky zmizel → menu se nejspíš zavírá ([vanishing], od 0.97.117)
        if (sel < 0 || bright.max() < 150) { if (noSelSince < 0) noSelSince = now; return Result(v, false) }
        noSelSince = -1L
        fastAt = now; fastItem = v.items[sel]
        if (sel == v.selected) return Result(v, false)
        val nv = v.copy(selected = sel)
        last = nv
        return Result(nv, true)
    }

    /** Výsledek čtení snímku pořízeného v [grabbedAt] (0 = bez času – výběr podle jasu se nebere v úvahu). */
    /**
     * Bílý pruh vybrané položky není vidět aspoň [VANISH_MS] (jas na každém snímku, [fast]) – menu se zavřelo: stačí jedno
     * čtení bez menu (od 0.97.117, majitel: po zavření menu překlad zbytečně svítil, změnu položky přitom umí hned).
     */
    fun vanishing(now: Long): Boolean = (noSelSince >= 0 && now - noSelSince >= VANISH_MS) || (gateGoneSince >= 0 && now - gateGoneSince >= VANISH_MS)

    /**
     * Výběr v panelu mise vpravo podle jasu na novém snímku (od 0.97.140): [sel] = index do [MenuView.side], −1 = žádný pruh.
     * S pruhem vpravo menu drží otevřené (menu zleva pak pruh nemá – jinak by se bralo jako zavírající se).
     */
    fun side(sel: Int, now: Long): Result {
        val v = last ?: return Result(null, false)
        if (sel >= 0) { noSelSince = -1L; gateGoneSince = -1L }
        if (sel == v.sideSel || sel >= v.side.size) return Result(v, false)
        val nv = v.copy(sideSel = sel)
        last = nv
        return Result(nv, true)
    }

    /** Menu zleva: znak menu z jasu ([GameMenu.pauseGate]) na tomto snímku je / není (od 0.97.132 – zavření se pozná i tak). */
    fun gate(present: Boolean, now: Long) { if (present) gateGoneSince = -1L else if (gateGoneSince < 0) gateGoneSince = now }

    fun update(read: MenuView?, grabbedAt: Long = 0L, now: Long = -1L): Result {
        if (read == null) {
            if (last == null) return Result(null, false)
            if (++misses < (if (now >= 0 && vanishing(now)) 1 else MISSES)) return Result(last, false)
            reset(); return Result(null, true)
        }
        misses = 0
        val g = when (read.kind) {
            "photo" -> read.head?.first?.uppercase() ?: group ?: ""
            "pause" -> if (read.items.any { it in spec.sub }) "sub" else ""
            else -> ""
        }
        // podmenu (Hra → Uložit/Načíst) má ve hře vlastní velikost písma – výška řádku zvlášť ([MenuLayout.subLine])
        if (read.kind != kind || g != group) { itemLines.clear(); itemLine0 = 0f; kind = read.kind; group = g; tops.clear(); rects.clear(); prevStep = 0f; fastAt = -1L; fastItem = null; steps.clear(); last = null }
        if (read.kind == "photo") {
            // seznam voleb se posouvá: dřív viděná volba na místě, kde je teď jiná, už tam není
            val st = (steps.sorted().getOrNull(steps.size / 2) ?: read.step).takeIf { it > 0f } ?: 0.04f
            val now0 = read.rects.mapNotNull { it.takeIf { r -> r.size >= 4 }?.get(1) }
            tops.keys.filter { it !in read.items }.filter { old -> rects[old]?.let { r -> r.size >= 4 && now0.any { kotlin.math.abs(it - r[1]) < st * 0.5f } } == true }
                .forEach { tops.remove(it); rects.remove(it) }
        }
        read.items.forEachIndexed { i, item ->
            tops[item] = read.tops.getOrElse(i) { tops[item] ?: 0 }
            // místo položky drží, dokud se opravdu nepohne (od 1.00-rc24, majitel: „překlady menu pozičně mírně poskakovaly“ –
            // rámeček z čtečky je čtení od čtení o bod dva jinde, a s ním překlad)
            read.rects.getOrNull(i)?.takeIf { it.size >= 4 }?.let { rects[item] = GameMenu.steady(rects[item], it) }
        }
        // pořadí položek: menu zleva podle výšky, lišta podle pořadí v profilu hry (zleva doprava)
        val order = if (read.kind == "pause") spec.pause else spec.tabs
        val items = tops.keys.sortedWith(compareBy<String>({ tops[it] ?: 0 }, { order.indexOf(it) }))
        var sel = read.selected.takeIf { it >= 0 }?.let { items.indexOf(read.items[it]) } ?: -1
        val keep = last?.takeIf { it.kind == read.kind }?.let { l -> items.indexOf(l.items.getOrNull(l.selected)) } ?: -1
        // lišta se v tomhle čtení nepřečetla vůbec (rozpadlý obraz, [GameMenu.read] s inSettings) → výběr zůstává;
        // u Nastavení se nehádá „ta, která chybí“ (od 0.97.107: chyběla často AUDIO vedle uříznutého „ACCO“ → pořád „Zvuk“)
        if (read.items.isEmpty() || (sel < 0 && read.kind == "settings")) sel = keep
        else if (sel < 0) {
            // vybraná položka se nepřečetla (tmavé písmo na bílém pruhu) → ta jediná, která v tomhle čtení chybí
            val missing = items.filter { it !in read.items }
            if (missing.size == 1) sel = items.indexOf(missing[0])
        }
        // novější výběr podle jasu má přednost před čtením staršího snímku
        if (grabbedAt > 0L && fastAt >= grabbedAt) fastItem?.let { f -> items.indexOf(f).takeIf { it >= 0 }?.let { sel = it } }
        // místo překladu drží během otevřeného menu pozici: nikdy se neposune dolů (vybraná horní položka se občas
        // nepřečte a seznam by „ujel“ o řádek – majitel 20. 11. 2026), jen nahoru, když se najde výš položená položka
        val prev = last?.takeIf { it.kind == read.kind }?.box
        val box = when {
            prev == null -> read.box
            read.box == null -> prev
            else -> prev.first to minOf(prev.second, read.box.second)
        }
        // rozestup řádků: medián rozestupů z čtení tohoto menu (od 0.97.107 – nejmenší známý se jedním zkresleným čtením
        // zmenšil natrvalo a s ním písmo)
        if (read.step > 0f) { steps += read.step; if (steps.size > 15) steps.removeAt(0) }
        val step = steps.sorted().getOrNull(steps.size / 2) ?: 0f
        prevStep = step
        // písmo položek: z profilu hry ([MenuLayout]) hned od prvního čtení; bez něj medián výšek z prvních [ITEM_READS] čtení.
        // Pak se do zavření menu (podmenu) nemění – výška přečteného textu se mění podle písmen (g, p, y) a písmo skákalo.
        if ((read.kind == "pause" || read.kind == "photo") && itemLines.size < ITEM_READS) {
            val prof = when { read.kind == "photo" -> spec.layout.photoLine; g == "sub" -> spec.layout.subLine; else -> spec.layout.pauseLine }
            GameMenu.itemHeight(read.rects).takeIf { it > 0f }?.let { itemLines += it }
            val m = itemLines.sorted().getOrNull((itemLines.size - 1) / 2)
            itemLine0 = when {
                prof <= 0f -> m ?: 0f
                // rychlá kontrola: změřené výrazně jiné než v profilu → vývojáři hru změnili, platí změřené
                m != null && itemLines.size >= ITEM_READS && kotlin.math.abs(m - prof) > prof * LAYOUT_TOLERANCE -> m
                else -> prof
            }
        }
        // písmo nadpisu a popisu Nastavení: první známá výška řádku na celé menu (od 0.97.107 – měnila se volbu od volby, „skáče“)
        if (descLine0 <= 0f && read.descLine > 0f) descLine0 = read.descLine
        if (titleLine0 <= 0f && read.titleLine > 0f) titleLine0 = read.titleLine
        val v = read.copy(items = items, selected = sel, tops = items.map { tops[it] ?: 0 }, box = box, step = step,
            rects = items.map { rects[it] ?: FloatArray(0) },
            descLine = if (descLine0 > 0f) descLine0 else read.descLine, titleLine = if (titleLine0 > 0f) titleLine0 else read.titleLine,
            itemLine = itemLine0)
            .let { nv ->
                // seznam pod popisem stejné volby: čtení občas některý řádek vynechá – delší (úplnější) seznam zůstává,
                // ať překlad neposkakuje (tester HVWD-HYWD, barvy v Color Adjustments)
                val l = last
                val a = if (l != null && l.kind == nv.kind && l.title == nv.title && l.list.size > nv.list.size) nv.copy(list = l.list, listBox = l.listBox, listStep = l.listStep)
                else nv
                // patička: čtení občas některé tlačítko vynechá – úplnější zůstává
                val b0 = if (l != null && l.kind == a.kind && l.footer.size > a.footer.size) a.copy(footer = l.footer) else a
                // panel mise vpravo: stejně – úplnější zůstává
                val c0 = if (l != null && l.kind == b0.kind && l.side.size > b0.side.size) b0.copy(side = l.side) else b0
                // nadpis podmenu / Fotostudia se občas nepřečte – zůstává
                val d0 = if (c0.head == null && l != null && l.kind == c0.kind) c0.copy(head = l.head) else c0
                // patička, panel mise a nadpis: místo drží jako u položek ([GameMenu.steady])
                val same = l != null && l.kind == d0.kind
                val d = if (!same) d0 else d0.copy(
                    footer = GameMenu.steadyAll(l!!.footer, d0.footer), side = GameMenu.steadyAll(l.side, d0.side),
                    head = d0.head?.let { h -> l.head?.takeIf { it.first == h.first }?.let { o -> h.first to GameMenu.steady(o.second, h.second) } ?: h },
                    // schéma ovladače (od 1.0.1, majitel: „pořád poskakovalo i na statické obrazovce“): čtení občas popisek vynechá
                    // nebo přečte jinak – úplnější seznam téže záložky zůstává a místa drží jako u položek
                    controls = GameMenu.steadyControls(if (l.selected == d0.selected) l.controls else emptyList(), d0.controls),
                    options = GameMenu.steadyAll(l.options, d0.options))
                // výběr v panelu mise drží jas na snímcích ([side]), čtení ho nemění
                if (l != null && l.kind == d.kind && l.sideSel < d.side.size) d.copy(sideSel = l.sideSel) else d
            }
        val changed = v.key != last?.key
        last = v
        return Result(v, changed)
    }

    // od 0.97.132 dvě čtení bez menu (dřív 4 – majitel: zavření trvalo dlouho; posun výběru zachytí jas na každém snímku)
    companion object { const val MISSES = 2; const val VANISH_MS = 300L; const val ITEM_READS = 3
        /** Rychlá kontrola výšky písma z profilu: změřená se liší o víc (podíl) = hra se změnila. */
        const val LAYOUT_TOLERANCE = 0.25f }
}
