package cz.recaption.core

/**
 * Telefon ve hře (GTA VI: zprávy, kontakty, sociální síť, zprávy z novin…) – překlad obsahu do panelu vedle
 * telefonu (od 0.97.95, volba Titulky → Hra „Překlad telefonu“, výchozí vypnuto). Podle dávky testera 3WMD-6WFC
 * (22. 11. 2026): pěšky / vsedě displej x 0,355–0,60, y 0,17–0,83, v autě vpravo; světle šedé pozadí, tmavé písmo
 * (vlastní zprávy bílé v modrých bublinách), řádek ~2,2 % výšky.
 */
/**
 * Přečtený telefon: odstavce, kde je jeho text (zlomky obrazu), u každého odstavce strana ([sides]: 'l' příchozí
 * bublina vlevo, 'p' vlastní bublina vpravo, 's' jiný text – seznam, článek) a nadpis (jméno nahoře uprostřed).
 */
data class PhoneRead(
    val paragraphs: List<String>, val left: Float, val top: Float, val right: Float, val bottom: Float,
    val sides: List<Char> = paragraphs.map { 's' }, val title: String? = null,
    /** U čáry ('c') obdélník obrázku ve hře (zlomky obrazu: vlevo, nahoře, vpravo, dole), jinak null (od 0.97.100). */
    val images: List<FloatArray?> = paragraphs.map { null },
    /** Karta upozornění na domovské obrazovce ([PhoneCard]): nadpis i odstavce se překládají (pevně, jinak překladačem). */
    val card: Boolean = false,
    /**
     * Kde je každý odstavec v telefonu (zlomky obrazu: vlevo, nahoře, vpravo, dole; u čáry null) a výška řádku písma –
     * pro překlad přímo přes původní text (od 0.97.110, volba „přes text v telefonu“).
     */
    val boxes: List<FloatArray?> = paragraphs.map { null },
    val lineH: Float = 0f,
    /** Příspěvek v sociální síti (od 0.97.114): [title] = @jméno autora, odstavce = jen text příspěvku. */
    val feed: Boolean = false,
    /**
     * Seznam konverzací (řádek = jméno kontaktu a pod ním náhled poslední zprávy): jméno ke každému odstavci, nebo null.
     * Jméno se nepřekládá a ukáže se před překladem náhledu („Jméno: …“) – dřív se překládalo slité s náhledem.
     */
    val names: List<String?> = paragraphs.map { null },
    /**
     * Příloha pod zprávou v konverzaci (karta s ikonou vlevo: místo na mapě, odkaz na článek) – není to zpráva kontaktu,
     * v panelu se ukáže se značkou a překládá se bez rodu mluvčího. Dřív splývala se zprávou nad sebou nebo vypadala jako další zpráva.
     */
    val attach: List<Boolean> = paragraphs.map { false },
    /** Natočení telefonu (sklon řádků, [PhoneScreen.tilt]; 0 = rovně) – překlad přes text se natočí stejně (od 1.00-rc21). */
    val tilt: Float = 0f,
)

object PhoneScreen {
    /**
     * Kde se hledá: telefon není vždy na stejném místě (majitel 22. 11. 2026 – pěšky uprostřed x 0,36–0,60, v autě
     * vpravo x 0,60–0,85 až dolů), proto široký pás vpravo od minimapy. Poloha se bere z nalezeného textu.
     */
    const val ZONE_L = 0.28f
    const val ZONE_T = 0.10f
    // náhled zprávy vpravo dole sahá až k 0,97 (dávka F684-V2JJ, od 0.97.134; dřív 0,94)
    const val ZONE_R = 0.985f
    const val ZONE_B = 0.99f

    /**
     * Nápověda ovládání vpravo dole („SELECT ×“, „CONTROL Ⓛ / REEL IN Ⓡ“, „VIEW COMMENTS ×“) není telefon: telefon končí
     * nejvýš u 0,85 šířky, nápověda začíná od ~0,86. Dřív se četla jako telefon a překlad skákal přes originální ovládání
     * (majitel 24. 11. 2026, dávka 38K7-5K4G). Patří k menu vpravo dole, ne k telefonu.
     */
    fun controlHint(l: MenuLine, fullW: Int, fullH: Int): Boolean = l.left >= fullW * 0.86f && l.top >= fullH * 0.55f

    /** Mřížka pro poznání změny obsahu (6 × 6 políček přes pás) – beze změny se znovu nečte. */
    val GRID: List<FloatArray> = (0 until 6).flatMap { r -> (0 until 6).map { c ->
        val x = 0.30f + c * 0.105f; val y = 0.12f + r * 0.14f
        floatArrayOf(x, y, x + 0.05f, y + 0.06f)
    } }

    fun changed(a: IntArray?, b: IntArray?, limit: Int = 10): Boolean {
        if (a == null || b == null || a.size != b.size) return true
        return a.indices.any { kotlin.math.abs(a[it] - b[it]) > limit }
    }

    /** Jasy bodů v obdélníku (nejvýš 48 × 10 vzorků) z jasové roviny [gray] (0–255 v dolním bajtu) šířky [w]. */
    fun samples(gray: IntArray, w: Int, h: Int, left: Int, top: Int, right: Int, bottom: Int): IntArray {
        val x0 = left.coerceIn(0, w - 1); val x1 = right.coerceIn(x0 + 1, w)
        val y0 = top.coerceIn(0, h - 1); val y1 = bottom.coerceIn(y0 + 1, h)
        val sx = maxOf(1, (x1 - x0) / 48); val sy = maxOf(1, (y1 - y0) / 10)
        val out = ArrayList<Int>(512)
        var y = y0
        while (y < y1) { var x = x0; while (x < x1) { out += gray[y * w + x] and 0xFF; x += sx }; y += sy }
        return out.toIntArray()
    }

    private val TIME = Regex("^\\d{1,2}[:.]\\d{2}\\s*([ap]\\.?m\\.?)?$", RegexOption.IGNORE_CASE)
    private val NUMBERISH = Regex("^[^A-Za-z]*$")
    /** Názvy aplikací a stavový řádek – nepřekládají se (jména zůstávají anglicky jako ve hře). */
    private val SKIP = setOf("back", "whatup", "buckme", "waink", "rydeme", "ifruit", "fitness", "contacts", "news", "weather",
        "settings", "gallery", "camera", "reminders", "notifications", "typing", "new message")

    private val SKIP_TIGHT = SKIP.map { it.replace(" ", "") }.toSet()

    /** Řádek, který do překladu nepatří: čas, čísla a symboly, název aplikace, kousek (méně než 3 písmena). */
    fun junk(text: String): Boolean {
        val t = text.trim()
        if (t.count { it.isLetter() } < 3) return true
        if (TIME.matches(t) || NUMBERISH.matches(t) || likes(t)) return true
        // i s tečkami a ikonou kolem („••• Typing...“ pod konverzací – slévalo se s poslední zprávou, dávka NDNS-VQZ7)
        // čtečka občas vynechá mezeru („Newmessage“)
        return t.lowercase().trim { !it.isLetter() }.let { it in SKIP || it.replace(" ", "") in SKIP_TIGHT }
    }

    private val MARKS = Regex("^[?!.…¿¡ ]{1,8}$")

    /**
     * Zpráva jen z otazníků / vykřičníků („??“, „?!“ – dávka JJAG-MBQB, majitel: „chyběly tam otazníky“): není to kousek
     * textu, ale celá zpráva – do překladu jde beze změny (dřív ji [junk] zahodil, má méně než 3 písmena).
     */
    fun marks(text: String): Boolean = text.trim().let { t -> MARKS.matches(t) && t.any { it == '?' || it == '!' } }

    /**
     * Řádek se srdíčkem a komentáři pod příspěvkem v sociální síti („♡ 52K ◯ 6“ – čtečka ho čte „V52KD6“, „?484D6“,
     * „8KD5“; dávka 4KHZ-AJKC): krátký, s číslem a nejvýš 4 písmeny (ikony a K/M).
     */
    fun likes(text: String): Boolean {
        val t = text.trim()
        return t.length in 2..16 && t.any { it.isDigit() } && t.count { it.isLetter() } <= 4 && !TIME.matches(t)
    }

    /** Jméno autora příspěvku („@ThisThat“; čtečka před zavináč občas přidá kousek ikony: „G@Lambda_Bro_69“). */
    fun handle(text: String): String? {
        val t = text.trim()
        val at = t.indexOf('@')
        if (at !in 0..2) return null
        val name = t.substring(at).substringBefore(' ')
        return name.takeIf { n -> n.length >= 3 && n.drop(1).all { it.isLetterOrDigit() || it == '_' || it == '.' } }
    }

    /**
     * Tmavé písmo na světlém pozadí v telefonu (od 0.97.114): jako [TextPolarity.darkOnLight], navíc bílá karta příspěvku
     * v sociální síti (pozadí 210–230 podle jasu fotky nad ní – dřív se střídalo „telefon / není“ a překlad se neukázal):
     * světlé pozadí, nic světlejšího než pozadí a v rámečku opravdu tmavé písmo.
     */
    fun darkText(gray: IntArray): Boolean {
        if (TextPolarity.darkOnLight(gray)) return true
        if (gray.size < 20) return false
        val s = gray.sortedArray()
        val p10 = s[s.size / 10]; val med = s[s.size / 2]; val p90 = s[(s.size * 9) / 10]
        return (med >= 150 && p90 < 248 && p90 - med <= 25 && med - p10 >= 50) || dimText(s)
    }

    /**
     * Ztlumený displej (dávka 6RKU-RZW3: noc, postava stojí ve tmě nebo jde – hra telefon přitmaví, pozadí displeje má jas
     * jen 55–95 a písmo 25–60): stejnoměrné pozadí (90. percentil skoro jako medián) a písmo zřetelně tmavší. Dřív se
     * takový telefon vůbec nepoznal (všechny řádky „světlé písmo není“) a SMS ani seznam zpráv se nepřekládaly.
     * [s] = seřazené jasy.
     */
    private fun dimText(s: IntArray): Boolean {
        val p10 = s[s.size / 10]; val med = s[s.size / 2]; val p90 = s[(s.size * 9) / 10]
        return med in 45..150 && p90 - med <= maxOf(6, med / 8) && med - p10 >= maxOf(14, med * 27 / 100)
    }

    /**
     * Tmavé písmo na úplně bílé kartě (příspěvek v sociální síti za jasného displeje, dávka NDNS-VQZ7: pozadí 245–255,
     * 90. percentil 248–255 – [darkText] ho kvůli stropu 248 nebral a příspěvky se četly jen občas). Samotné nestačí:
     * stejně vypadá bílý titulek s tmavým obrysem přes přesvětlené nebe, proto ho [read] bere jen s dalším řádkem
     * se stejným levým okrajem a nad pásem titulků ([WHITE_B]).
     */
    fun whiteCard(gray: IntArray): Boolean {
        if (gray.size < 20) return false
        val s = gray.sortedArray()
        val p10 = s[s.size / 10]; val med = s[s.size / 2]; val p90 = s[(s.size * 9) / 10]
        return med >= 225 && p90 >= 248 && med - p10 >= 50
    }

    /** Hashtagy z textu příspěvku pryč („text #Neco #Jine“ → text a „#Neco #Jine“) – nepřekládají se, ukážou se za překladem. */
    fun hashtags(text: String): Pair<String, String> {
        val words = text.trim().split(Regex("\\s+"))
        val tags = words.filter { it.length >= 2 && it[0] == '#' }
        if (tags.isEmpty()) return text to ""
        return words.filter { it !in tags }.joinToString(" ").trim() to tags.joinToString(" ")
    }

    /**
     * Jméno autora bez zavináče: čtečka ho občas nepřečte („SomeUser_99“ nad fotkou). Jedno slovo aspoň 6 znaků
     * s velkým písmenem uvnitř, číslicí nebo podtržítkem.
     */
    fun bareHandle(text: String): String? {
        val t = text.trim().trimStart('<', '(', ' ')
        if (t.length < 6 || ' ' in t || !t.all { it.isLetterOrDigit() || it == '_' || it == '.' } || !t[0].isLetter()) return null
        return if (t.drop(1).any { it.isUpperCase() || it.isDigit() || it == '_' }) "@$t" else null
    }

    /** Modrost pozadí odstavce z barevných bodů (ARGB): průměr prostředních jasů (bez písma), b − (r + g) / 2; null = málo bodů. */
    fun blueness(argb: IntArray): Int? {
        if (argb.size < 12) return null
        val byLuma = argb.sortedBy { p -> ((p shr 16) and 0xFF) * 3 + ((p shr 8) and 0xFF) * 6 + (p and 0xFF) }
        val mid = byLuma.subList(byLuma.size * 3 / 10, maxOf(byLuma.size * 3 / 10 + 1, byLuma.size * 7 / 10))
        val r = mid.sumOf { (it shr 16) and 0xFF } / mid.size
        val g = mid.sumOf { (it shr 8) and 0xFF } / mid.size
        val b = mid.sumOf { it and 0xFF } / mid.size
        return b - (r + g) / 2
    }

    /**
     * Strana bubliny podle barvy (od 0.97.114, dávka 4KHZ-AJKC – podle odsazení vyšly všechny bubliny vlastní/modré):
     * vlastní zprávy jsou v modré bublině (sytě modrá s bílým písmem, nebo světle modrá s tmavým), příchozí šedé.
     * [blue] = [blueness] pozadí každého odstavce (u čáry null). Bez barev nebo u karty upozornění beze změny.
     */
    fun withBubbleColors(r: PhoneRead, blue: List<Int?>, lift: Int? = null): PhoneRead {
        if (r.card || r.feed || blue.size != r.paragraphs.size) return r
        val texts = r.sides.indices.filter { r.sides[it] != 'c' }
        if (texts.isEmpty() || texts.any { blue[it] == null }) return r
        // za denního světla hra tónuje obraz do žluta (dávka F6PV-UY39: světle modrá vlastní bublina −10, šedá příchozí −35,
        // obě pod [BLUE]) – SMS pak neměly bubliny vlevo / vpravo. Proto i o [BLUE_REL] modřejší než nejméně modrý odstavec.
        val least = texts.minOf { blue[it]!! }
        val own = texts.filter { blue[it]!! >= BLUE || blue[it]!! - least >= BLUE_REL }
        // SMS, nebo jiná aplikace? (od 1.00-rc21, majitel: „neumíš poznat, co je sms a co ne“) – zprávy jsou v bublinách:
        // vlastní modrá, příchozí světlejší než pozadí displeje ([lift] = o kolik je okraj kolem textu světlejší než okraj
        // displeje). Nadpis nahoře mají i jiné aplikace (zprávy z novin), proto bez modré bubliny jen s bublinami příchozími.
        val chat = own.isNotEmpty() || (r.title != null && (lift == null || lift >= BUBBLE_LIFT))
        val sides = r.sides.indices.map { i ->
            if (r.sides[i] == 'c') 'c' else if (i in own) 'p' else if (chat) 'l' else 's'
        }
        // modrá „příloha“ je vlastní široká bublina, ne karta
        return r.copy(sides = sides, attach = r.attach.indices.map { i -> r.attach[i] && i !in own })
    }

    /** Řádek barevného snímku v rozsahu telefonu: [y] zlomek výšky, [frac] podíl modrých bodů sloupce, [left]–[right] kde jsou (zlomky šířky). */
    data class BlueRow(val y: Float, val frac: Float, val left: Float, val right: Float)

    /**
     * Vlastní bubliny bez textu (jen emoji – palec nahoru; dávka JJAG-MBQB, majitel: „chyběly tam palce“): čtečka v nich nic
     * nepřečte, ale modrá bublina je vidět v barvách. Souvislý pás řádků s modrou ([BlueRow.frac] ≥ [BLANK_FRAC]) aspoň
     * na výšku řádku písma, který se nepřekrývá s žádným odstavcem s textem, je odstavec bez textu se stranou 'p' a obrázkem
     * ([PhoneRead.images]) – služba z něj vyřízne obrázek a ukáže ho v bublině (TV od aplikace 1.5.24). Jen v konverzaci.
     */
    fun withBlankBubbles(r: PhoneRead, rows: List<BlueRow>): PhoneRead {
        if (r.card || r.feed || rows.size < 3 || r.lineH <= 0f || !(r.title != null || 'p' in r.sides)) return r
        val runs = ArrayList<FloatArray>()
        var start = -1
        for (i in 0..rows.size) {
            val on = i < rows.size && rows[i].frac >= BLANK_FRAC
            if (on && start < 0) start = i
            if (!on && start >= 0) {
                val part = rows.subList(start, i)
                runs += floatArrayOf(part.minOf { it.left }, part.first().y, part.maxOf { it.right }, part.last().y)
                start = -1
            }
        }
        val texts = r.boxes.indices.mapNotNull { i -> r.boxes[i]?.takeIf { r.paragraphs[i].isNotBlank() } }
        val margin = r.lineH * 0.2f
        val blank = runs.filter { b ->
            b[3] - b[1] >= r.lineH && b[3] - b[1] <= r.lineH * 4 && texts.none { t -> b[1] < t[3] + margin && b[3] > t[1] - margin }
        }
        if (blank.isEmpty()) return r
        data class E(val top: Float, val text: String, val side: Char, val image: FloatArray?, val box: FloatArray?, val name: String?, val attach: Boolean)
        val all = r.paragraphs.indices.map { i ->
            E(r.boxes.getOrNull(i)?.get(1) ?: r.images.getOrNull(i)?.get(1) ?: -1f, r.paragraphs[i], r.sides[i], r.images.getOrNull(i),
                r.boxes.getOrNull(i), r.names.getOrNull(i), r.attach.getOrNull(i) == true)
        }.toMutableList()
        for (b in blank) {
            // za poslední odstavec, který je nad bublinou (odstavce bez místa – čáry – zůstávají, kde jsou)
            val at = all.indexOfLast { it.top in 0f..b[1] } + 1
            all.add(at, E(b[1], "", 'p', b, b, null, false))
        }
        return r.copy(paragraphs = all.map { it.text }, sides = all.map { it.side }, images = all.map { it.image },
            boxes = all.map { it.box }, names = all.map { it.name }, attach = all.map { it.attach })
    }

    /**
     * Telefon z řádků OCR (souřadnice v pixelech celého obrazu [fullW]×[fullH]): otevřený je, když aspoň [MIN_DARK]
     * řádky mají tmavé písmo na světlém pozadí ([dark]) a leží pod sebou ve sloupci široké nejvýš 0,3 obrazu;
     * pak se berou všechny řádky v tom sloupci (i bílé v modrých bublinách) od 14 řádků nad prvním tmavým řádkem (jméno nahoře, pod ním i vysoká fotka příspěvku)
     * po 16 řádků pod posledním (mezi nimi bývá fotka), shora dolů. Nový odstavec po mezeře větší než 0,8 řádku nebo při jiném levém okraji
     * (bubliny vlevo a vpravo). null = telefon není otevřený / nic ke čtení. Tmavé aplikace (fitness, počasí) zatím ne.
     */
    fun read(lines: List<MenuLine>, fullW: Int, fullH: Int, dark: (MenuLine) -> Boolean): PhoneRead? =
        read(lines, fullW, fullH, { false }, dark)

    /**
     * Sklon telefonu: o kolik bodů se levý okraj textu posouvá na jeden bod výšky (pěšky drží postava telefon šikmo –
     * dávka 3WMD-6WFC: levý okraj řádků seznamu ujede shora dolů o 60 bodů na 390, tj. −0,16; v autě je telefon rovně).
     * Medián sklonu mezi dvojicemi řádků aspoň 3 řádky od sebe; bere se jen sklon 0,03–0,3, po kterém se víc řádků
     * sejde na stejném levém okraji než bez něj. 0 = rovně / nejde určit.
     */
    fun slant(c: List<MenuLine>): Float {
        if (c.size < 4) return 0f
        val lh = c.map { it.bottom - it.top }.sorted()[c.size / 2].coerceAtLeast(1)
        val slopes = ArrayList<Float>()
        for (i in c.indices) for (j in c.indices) {
            val dy = c[j].top - c[i].top
            if (dy >= lh * 3) slopes += (c[j].left - c[i].left).toFloat() / dy
        }
        if (slopes.size < 3) return 0f
        slopes.sort()
        val k = slopes[slopes.size / 2]
        if (kotlin.math.abs(k) < 0.03f || kotlin.math.abs(k) > 0.3f) return 0f
        val y0 = c.minOf { it.top }
        fun aligned(q: Float): Int {
            val xs = c.map { it.left - q * (it.top - y0) }
            return xs.maxOf { x -> xs.count { kotlin.math.abs(it - x) <= lh * 0.4f } }
        }
        return if (aligned(k) > aligned(0f)) k else 0f
    }

    /**
     * Jako [read], navíc [white] = řádek s tmavým písmem na úplně bílé kartě ([whiteCard]). Šikmo držený telefon se
     * napřed narovná ([slant]) – jinak levé okraje řádků neseděly a seznam, odstavce i strany bublin se četly špatně.
     */
    fun read(lines: List<MenuLine>, fullW: Int, fullH: Int, white: (MenuLine) -> Boolean, dark: (MenuLine) -> Boolean): PhoneRead? {
        // jas se měří v původním obraze (rámečky čtečky), až pak se řádky narovnávají
        val d = lines.map { dark(it) }
        val w = lines.indices.map { !d[it] && white(lines[it]) }
        val a = tilt(lines.filterIndexed { i, l -> (d[i] || w[i]) && !junk(l.text) })
        if (a == 0f) return sheared(lines, fullW, fullH, d, w)
        // natočený telefon (chůze, dávka 6RKU-RZW3 – řádky skloněné o 0,07–0,13): rámečky čtečky jsou kolem šikmých řádků,
        // takže se překrývaly (článek z 23 řádků = 20 „odstavců“, obsah se nikdy nepotvrdil). Řádky se otočí zpátky
        // kolem středu textu, přečtou se jako rovný telefon a místa odstavců se otočí zase do obrazu.
        val cx = lines.map { (it.left + it.right) / 2f }.sorted()[lines.size / 2]
        val cy = lines.map { (it.top + it.bottom) / 2f }.sorted()[lines.size / 2]
        val cos = 1f / kotlin.math.sqrt(1f + a * a); val sin = a * cos
        val sa = kotlin.math.abs(sin); val det = (cos * cos - sa * sa).coerceAtLeast(0.3f)
        val flat = lines.map { l ->
            val bw = (l.right - l.left).toFloat(); val bh = (l.bottom - l.top).toFloat()
            // skutečná šířka a výška řádku z rámečku kolem natočeného obdélníku
            val ww = ((bw * cos - bh * sa) / det).coerceIn(1f, bw)
            val hh = ((bh * cos - bw * sa) / det).coerceIn(maxOf(1f, bh * 0.35f), bh)
            val dx = (l.left + l.right) / 2f - cx; val dy = (l.top + l.bottom) / 2f - cy
            val x = cx + dx * cos + dy * sin; val y = cy - dx * sin + dy * cos
            l.copy(left = Math.round(x - ww / 2), right = Math.round(x + ww / 2), top = Math.round(y - hh / 2), bottom = Math.round(y + hh / 2))
        }
        val r = sheared(flat, fullW, fullH, d, w) ?: return null
        // zpátky do obrazu (zlomky): střed se otočí, velikost zůstane
        fun back(x: Float, y: Float): Pair<Float, Float> {
            val dx = x * fullW - cx; val dy = y * fullH - cy
            return (cx + dx * cos - dy * sin) / fullW to (cy + dx * sin + dy * cos) / fullH
        }
        fun turn(b: FloatArray?): FloatArray? = b?.let {
            val (x, y) = back((it[0] + it[2]) / 2f, (it[1] + it[3]) / 2f)
            val hw = (it[2] - it[0]) / 2f; val hh = (it[3] - it[1]) / 2f
            floatArrayOf(x - hw, y - hh, x + hw, y + hh)
        }
        val corners = listOf(back(r.left, r.top), back(r.right, r.top), back(r.left, r.bottom), back(r.right, r.bottom))
        return r.copy(left = corners.minOf { it.first }, top = corners.minOf { it.second }, right = corners.maxOf { it.first },
            bottom = corners.maxOf { it.second }, boxes = r.boxes.map { turn(it) }, images = r.images.map { turn(it) }, tilt = a)
    }

    /**
     * Natočení telefonu z řádků čtečky ([MenuLine.angle]): medián sklonu delších řádků (aspoň 8 znaků), aspoň 3 a většina
     * blízko mediánu; bere se 0,03–0,35. 0 = rovně nebo čtečka sklon nedává (pak jen [slant]).
     */
    fun tilt(c: List<MenuLine>): Float {
        val a = c.filter { it.text.trim().length >= 8 && it.angle != 0f }.map { it.angle }.sorted()
        if (a.size < 3) return 0f
        val m = a[a.size / 2]
        if (kotlin.math.abs(m) < 0.03f || kotlin.math.abs(m) > 0.35f) return 0f
        return if (a.count { kotlin.math.abs(it - m) <= 0.04f } * 10 >= a.size * 6) m else 0f
    }

    /** Jako [read] nad (už narovnanými) řádky; [d] / [w] = tmavé písmo / bílá karta po řádcích (měřené v původním obraze). */
    private fun sheared(lines: List<MenuLine>, fullW: Int, fullH: Int, d: List<Boolean>, w: List<Boolean>): PhoneRead? {
        val k = slant(lines.filterIndexed { i, l -> (d[i] || w[i]) && !junk(l.text) })
        if (k == 0f) return straight(lines, fullW, fullH, { l -> lines.indexOfFirst { it === l }.let { it >= 0 && w[it] } },
            { l -> lines.indexOfFirst { it === l }.let { it >= 0 && d[it] } }, clipTop = true)
        val y0 = lines.map { it.top }.sorted()[lines.size / 2]
        fun dx(y: Float) = k * (y - y0)
        // šikmý řádek má rámeček tím vyšší, čím je delší (krátké jméno 21 bodů, dlouhý řádek 29–37) – mezery mezi řádky pak
        // mizely a odstavce se slévaly; o kolik na bod šířky, se změří z řádků samých (medián mezi dvojicemi s rozdílem
        // šířky aspoň 60 bodů) a výška se o to zmenší, střed zůstává
        val grow = ArrayList<Float>()
        for (a in lines) for (b in lines) {
            val dw = (b.right - b.left) - (a.right - a.left)
            if (dw >= 60) grow += ((b.bottom - b.top) - (a.bottom - a.top)).toFloat() / dw
        }
        val g = if (grow.size < 3) 0f else grow.sorted()[grow.size / 2].coerceIn(0f, 0.2f)
        val adj = lines.map { l ->
            val s = dx(l.top.toFloat()).let { Math.round(it) }
            val h = l.bottom - l.top
            val h2 = Math.round(h - (l.right - l.left) * g).coerceIn((h * 0.5f).toInt().coerceAtLeast(1), h)
            val top = l.top + (h - h2) / 2
            l.copy(left = l.left - s, right = l.right - s, top = top, bottom = top + h2)
        }
        val r = straight(adj, fullW, fullH, { l -> adj.indexOfFirst { it === l }.let { it >= 0 && w[it] } },
            { l -> adj.indexOfFirst { it === l }.let { it >= 0 && d[it] } }) ?: return null
        // místa odstavců zpátky do šikmého obrazu (překlad přes text, barva bubliny)
        return r.copy(boxes = r.boxes.map { b -> b?.let { val s = dx((it[1] + it[3]) / 2f * fullH) / fullW
            floatArrayOf(it[0] + s, it[1], it[2] + s, it[3]) } })
    }

    private fun straight(lines: List<MenuLine>, fullW: Int, fullH: Int, white: (MenuLine) -> Boolean, dark: (MenuLine) -> Boolean,
                         clipTop: Boolean = false): PhoneRead? {
        if (fullW <= 0 || fullH <= 0) return null
        // řádek uříznutý pravým okrajem čteného pásu není telefon, ale nápis ve scéně (světelná tabule u silnice vedle telefonu
        // v autě se četla jako telefon a schovala kartu upozornění, dávka 4KHZ-AJKC); náhled zprávy vpravo dole končí u 0,975
        val clipped = fullW * ZONE_R - 4
        val strict = lines.filter { it.right < clipped && dark(it) && !junk(it.text) }
        // bílá karta: jen řádky nad pásem titulků, které mají souseda se stejným levým okrajem (titulky jsou na střed)
        val pale = lines.filter { it !in strict && it.right < clipped && it.bottom <= fullH * WHITE_B && !junk(it.text) && white(it) }
        // řádek se srdíčky pod příspěvkem (do překladu nepatří, ale je to spolehlivá známka příspěvku)
        val hearts = lines.filter { likes(it.text) && (dark(it) || white(it)) }
        val cand = strict + pale + hearts
        val dark0 = strict + pale.filter { w -> cand.any { c -> c !== w && kotlin.math.abs(c.left - w.left) <= (w.bottom - w.top) &&
            kotlin.math.abs(c.top - w.top) <= (w.bottom - w.top) * 4 } }
        // jednořádkový příspěvek: stačí jeden řádek, když jsou hned pod ním srdíčka se stejným levým okrajem
        val need = if (dark0.size == 1 && dark0[0].let { d -> hearts.any { c -> kotlin.math.abs(c.left - d.left) <= (d.bottom - d.top) &&
                c.top > d.top && c.top - d.bottom <= (d.bottom - d.top) * 2 } }) 1 else MIN_DARK
        if (dark0.size < need) return null
        val lh0 = dark0.map { it.bottom - it.top }.sorted()[dark0.size / 2]
        // levý okraj telefonu = okraj, u kterého začíná nejvíc tmavých řádků (bubliny vlevo, text příspěvku); tmavý nápis ve scéně
        // vlevo od telefonu (cedule „PREPAID TOLLS“ nad telefonem v autě, dávka 4KHZ-AJKC) se nebere – dřív byl nahoře jako nesmyslný
        // řádek a posunul levý okraj, takže všechny bubliny vypadaly jako vlastní (vpravo)
        val edge = dark0.maxWith(compareBy<MenuLine>({ d -> dark0.count { kotlin.math.abs(it.left - d.left) <= lh0 } }, { -it.left })).left
        // a nic daleko vpravo od toho okraje: tmavý nápis na světlé ceduli za telefonem („TRAVEL C“ na budově vpravo nahoře,
        // dávka F6PV-UY39) dřív roztáhl sloupec přes 0,3 obrazu – příspěvek v sociální síti se pak vůbec nečetl a nad
        // konverzací byl nesmyslný nadpis „Cestování c“. Vlastní bublina vpravo začíná nejvýš ~0,15 obrazu od okraje.
        val darkLines = dark0.filter { it.left >= edge - lh0 * 1.5f && it.left <= edge + fullW * 0.2f && it.right <= edge + fullW * 0.3f }
        if (darkLines.size < need) return null
        val left = darkLines.minOf { it.left }; val right = darkLines.maxOf { it.right }
        if (right - left > fullW * 0.3f) return null
        val w = right - left
        val lh = darkLines.map { it.bottom - it.top }.sorted()[darkLines.size / 2]
        val top = darkLines.minOf { it.top } - lh * 14; val bottom = darkLines.maxOf { it.bottom } + lh * 16
        val colAll = lines.filter { l -> l.left >= left - lh * 1.5f && l.right <= right + w / 4 && l.top >= top && l.bottom <= bottom }
            .sortedBy { it.top }
        // sociální síť (od 0.97.114): příspěvek = @jméno, text a řádek se srdíčky; co je nad jménem (konec předchozího příspěvku)
        // a pod srdíčky (místo, kousek dalšího příspěvku dole na displeji) se nebere – majitel: „ignoroval bych ten kousek dalšího“
        var feedName: String? = null
        var from = 0; var to = colAll.size
        // jméno autora: se zavináčem, nebo bez něj jedno „slepené“ slovo, pod kterým je fotka (mezera přes 4 řádky)
        fun author(i: Int): String? = handle(colAll[i].text) ?: bareHandle(colAll[i].text)?.takeIf {
            i + 1 < colAll.size && colAll[i + 1].top - colAll[i].bottom > (colAll[i].bottom - colAll[i].top) * 4 }
        val likesAt = colAll.indices.firstOrNull { i -> likes(colAll[i].text) && (0 until i).any { author(it) != null } }
        // řádek se srdíčky čtečka někdy nepřečte: pak se bere jen první odstavec pod jménem (pod ním bývá místo a další příspěvek)
        var firstOnly = false
        if (likesAt != null) {
            val h0 = (0 until likesAt).last { author(it) != null }
            feedName = author(h0)
            from = h0 + 1; to = likesAt
        } else colAll.indices.firstOrNull { author(it) != null && ' ' !in colAll[it].text.trim() }?.let { h0 ->   // jen samotné jméno na řádku
            if (h0 + 1 < colAll.size) { feedName = author(h0); from = h0 + 1; firstOnly = true }
        }
        val col0 = colAll.subList(from, to).filter { !junk(it.text) || marks(it.text) }
        if (col0.isEmpty()) return null
        // řádek napůl schovaný pod jménem nahoře (konverzace odrolovaná – horní půlka písmen je pod lištou se jménem): čtečka
        // z něj dělá nesmyslná slova, která se slila s první zprávou (dávka 6RKU-RZW3). Pozná se
        // tak, že začíná hned pod jménem (mezera pod 0,3 řádku), mezi jménem a první zprávou je jinak volno. Jen u rovně drženého
        // telefonu – u šikmého jsou rámečky řádků vyšší a mezery mezi nimi mizí
        val col = if (clipTop && feedName == null && col0.size >= 3 && col0[0].text.trim().length <= 30 &&
            col0[0].right - col0[0].left < (col0.maxOf { it.right } - col0.minOf { it.left }) / 2 && col0[1].top - col0[0].bottom < (col0[0].bottom - col0[0].top) * 0.3f &&
            kotlin.math.abs((col0[0].left + col0[0].right) / 2f - (col0.minOf { it.left } + col0.maxOf { it.right }) / 2f) <
                (col0.maxOf { it.right } - col0.minOf { it.left }) * 0.12f) col0.filterIndexed { i, _ -> i != 1 } else col0
        class Para(val text: StringBuilder, var left: Int, var right: Int, var lines: Int, val divider: Boolean = false,
                   val imgTop: Int = 0, val imgBottom: Int = 0, var top: Int = 0, var bottom: Int = 0, val first: String = "")
        val paras = ArrayList<Para>()
        var prev: MenuLine? = null
        for (l in col) {
            val p = prev
            val h = l.bottom - l.top
            val newPara = p == null || l.top - p.bottom > h * 0.8f || kotlin.math.abs(l.left - p.left) > h * 1.2f
            // velká mezera (víc než 4 řádky) = obrázek (příspěvek v sociální síti, fotka v konverzaci) → čára (od 0.97.98)
            if (p != null && l.top - p.bottom > h * 4) paras += Para(StringBuilder(), l.left, l.right, 0, divider = true,
                imgTop = p.bottom + h / 2, imgBottom = l.top - h / 2)
            if (newPara) paras += Para(StringBuilder(l.text.trim()), l.left, l.right, 1, top = l.top, bottom = l.bottom, first = l.text.trim())
            else paras.last().let { q -> q.text.append(' ').append(l.text.trim()); q.left = minOf(q.left, l.left); q.right = maxOf(q.right, l.right); q.lines++
                q.bottom = maxOf(q.bottom, l.bottom) }
            prev = l
        }
        if (firstOnly) paras.indexOfFirst { !it.divider }.let { i -> if (i >= 0) { val keep = paras[i]; paras.clear(); paras += keep } }
        // místo pod příspěvkem („Some Beach“ za ikonou špendlíku), když čtečka nepřečte jméno autora ani srdíčka: poslední
        // jednořádkový název hned za odstavcem s hashtagem do překladu nepatří (střídal se s čistým čtením a překlad poblikával)
        if (feedName == null && paras.size >= 2 && paras.last().let { q -> !q.divider && q.lines == 1 &&
                nameLike(q.text.toString().trimStart { !it.isLetter() }) } && '#' in paras[paras.size - 2].text) paras.removeAt(paras.size - 1)
        if (paras.isEmpty()) return null
        val colL = col.minOf { it.left }; val colR = col.maxOf { it.right }
        val colW = (colR - colL).coerceAtLeast(1); val colC = (colL + colR) / 2f
        // nadpis: první odstavec, jeden krátký řádek uprostřed (jméno v konverzaci, „Messages“…)
        val first = paras.first()
        val title = feedName ?: first.text.toString().takeIf {
            paras.size > 1 && first.lines == 1 && it.length <= 30 &&
                kotlin.math.abs((first.left + first.right) / 2f - colC) < colW * 0.15f && first.left - colL > colW * 0.15f
        }
        val body = if (title != null && feedName == null) paras.drop(1) else paras
        if (body.isEmpty()) return null
        // vlastní zpráva = bublina zarovnaná vpravo (začíná daleko od levého okraje); s ní je to konverzace a ostatní
        // odstavce jsou příchozí bubliny vlevo, jinak obyčejný text (seznam, článek); přesněji podle barvy ([withBubbleColors])
        val onRight = body.map { !it.divider && feedName == null && it.left - colL > colW * 0.25f }
        val chat = onRight.any { it }
        val sides = body.indices.map { i -> if (body[i].divider) 'c' else if (onRight[i]) 'p' else if (chat) 'l' else 's' }
        // seznam konverzací: aspoň [LIST_MIN] odstavce o dvou řádcích, první řádek krátké jméno (bez věty) – jméno zvlášť
        val named = body.map { b -> b.first.takeIf { !chat && feedName == null && !b.divider && b.lines == 2 && nameLike(it) } }
        // v seznamu i kontakt s náhledem jen z emoji (čtečka náhled nepřečte → jeden řádek): jméno se nepřekládá (dřív se
        // jméno ze dvou běžných slov přeložilo, dávka 6RKU-RZW3)
        val names0 = if (named.count { it != null } >= LIST_MIN) body.indices.map { i ->
            named[i] ?: body[i].first.takeIf { !body[i].divider && body[i].lines == 1 && nameLike(it) }
        } else named.map { null }
        // příloha: krátký odstavec odsazený o ikonu (1,2–2,6 řádku od levého okraje sloupce) těsně pod zprávou u levého okraje
        // (dávka NDNS-VQZ7: zpráva x 770, karta x 805–811, mezera 10–20 px při řádku ~20 px); vlastní širokou bublinu,
        // která začíná podobně, vyřadí barva ([withBubbleColors])
        val lhB = body.filter { !it.divider }.map { (it.bottom - it.top) / it.lines.coerceAtLeast(1) }.sorted().let { if (it.isEmpty()) 1 else it[it.size / 2] }
        val attach0 = body.indices.map { i ->
            val b = body[i]; val prev = body.getOrNull(i - 1)
            (title != null || chat) && feedName == null && names0.all { it == null } && !b.divider && b.lines <= 3 && prev != null && !prev.divider &&
                b.left - colL >= lhB * 1.2f && b.left - colL <= lhB * 2.6f && prev.left - colL <= lhB * 0.6f && b.top - prev.bottom <= lhB * 1.6f
        }
        val (texts0, kept0) = cut(body.indices.map { i ->
            val t = body[i].text.toString()
            names0[i]?.let { t.removePrefix(it).trim() } ?: t
        }, newestLast = chat)
        // čára jen mezi texty: ne na začátku, na konci ani dvě za sebou
        val keep = kept0.indices.filter { k ->
            sides[kept0[k]] != 'c' || (k > 0 && k < kept0.size - 1 && sides[kept0[k - 1]] != 'c')
        }
        val texts = keep.map { texts0[it] }; val kept = keep.map { kept0[it] }
        if (texts.none { it.isNotBlank() }) return null
        // obrázek přes celou šířku sloupce (bubliny bývají užší než fotka – o kus širší, ale ne za telefon)
        val images = kept.map { i ->
            val b = body[i]
            if (!b.divider || b.imgBottom - b.imgTop < 8) null
            else floatArrayOf((colL - colW * 0.04f).coerceAtLeast(0f) / fullW, b.imgTop.toFloat() / fullH,
                (colR + colW * 0.04f).coerceAtMost(fullW.toFloat()) / fullW, b.imgBottom.toFloat() / fullH)
        }
        val boxes = kept.map { i -> body[i].takeIf { !it.divider }?.let { b ->
            floatArrayOf(b.left.toFloat() / fullW, b.top.toFloat() / fullH, b.right.toFloat() / fullW, b.bottom.toFloat() / fullH) } }
        return PhoneRead(texts, colL.toFloat() / fullW, col.minOf { it.top }.toFloat() / fullH,
            colR.toFloat() / fullW, col.maxOf { it.bottom }.toFloat() / fullH, kept.map { sides[it] }, title, images,
            boxes = boxes, lineH = col.map { it.bottom - it.top }.sorted()[col.size / 2].toFloat() / fullH, feed = feedName != null,
            names = kept.map { names0[it] }, attach = kept.map { attach0[it] })
    }

    /** Jméno kontaktu v seznamu konverzací: 1–3 slova, nejvýš 22 znaků, velké písmeno nebo @ na začátku, žádný konec věty. */
    fun nameLike(text: String): Boolean {
        val t = text.trim()
        if (t.length !in 2..22 || t.split(' ').size > 3 || t.count { it.isLetter() } < 2) return false
        return (t[0].isUpperCase() || t[0] == '@') && t.last() !in ".!?…,:"
    }

    /**
     * Nejvýš [MAX_CHARS] znaků (panel vedle telefonu): celé odstavce, v konverzaci ([newestLast]) ty nejnovější (dole),
     * jinak od začátku; jediný dlouhý se zkrátí na hranici slova. Vrací texty a jejich původní pořadí.
     */
    fun cut(paras: List<String>, newestLast: Boolean = false): Pair<List<String>, List<Int>> {
        val order = if (newestLast) paras.indices.reversed() else paras.indices
        val keep = ArrayList<Int>()
        var n = 0
        var single: String? = null
        for (i in order) {
            val p = paras[i]
            if (n + p.length <= MAX_CHARS) { keep += i; n += p.length; continue }
            if (keep.isEmpty()) { keep += i; single = p.take(MAX_CHARS).substringBeforeLast(' ') + " …" }
            break
        }
        keep.sort()
        return keep.map { if (single != null) single else paras[it] } to keep
    }

    /**
     * Panel s překladem vedle telefonu: vpravo od něj, když je telefon v levé části (pěšky), jinak vlevo (v autě vpravo);
     * široký 0,24, shora od telefonu, dole nejníž 0,80 (pod tím titulky).
     */
    fun panel(r: PhoneRead): FloatArray {
        val w = 0.24f
        val top = (r.top - 0.03f).coerceIn(0.08f, 0.5f)
        val phoneL = r.left - 0.03f; val phoneR = r.right + 0.03f
        return if ((r.left + r.right) / 2f < 0.58f && phoneR + w <= 0.97f) floatArrayOf(phoneR, top, phoneR + w, 0.80f)
        else floatArrayOf((phoneL - w).coerceAtLeast(0.02f), top, phoneL.coerceAtLeast(0.02f + w), 0.80f)
    }

    /** Podíl modrých bodů v řádku sloupce telefonu, od kterého je v řádku vlastní bublina. */
    const val BLANK_FRAC = 0.12f
    const val MIN_DARK = 2
    const val LIST_MIN = 3
    /** Bílá karta se bere jen nad touto výškou obrazu (pod ní jsou titulky). */
    const val WHITE_B = 0.80f
    /** Modrost pozadí, od které je bublina vlastní (šedá ~0, světle modrá ~20, sytě modrá ~50 – dávka 4KHZ-AJKC). */
    const val BLUE = 10
    /** Výchozí bubliny překladu telefonu (0xRRGGBB): vlastní sytě modrá, příchozí světle modrošedá (dřív šedá do růžova). */
    const val OWN_COLOR = 0x3A78D8
    const val IN_COLOR = 0x9DB2D2
    /** Vlastní bublina proti nejméně modrému odstavci v téže konverzaci (tónovaný obraz, [withBubbleColors]). */
    const val BLUE_REL = 15
    /** O kolik je bublina příchozí zprávy světlejší než pozadí displeje (jas; ve hře ~12–20, ztlumený displej ~6). */
    const val BUBBLE_LIFT = 3
    const val MAX_CHARS = 700
}

/**
 * Obsah telefonu v čase: nový obsah se ukáže po dvou podobných čteních (čtečka čte tentýž text pokaždé trochu jinak),
 * stejný obsah se znovu nepřekládá; zmizí po [MISSES] čteních bez telefonu.
 *
 * Porovnání je po odstavcích a snese kolísání čtečky (dávka NDNS-VQZ7, 23. 11. 2026 – seznam zpráv a konverzace se
 * 15 s nepřeložily, protože počet odstavců mezi čteními kolísal 5/6/7 a dvě „stejná“ čtení po sobě nepřišla): mezery
 * se nepočítají, odstavec jednou slitý a jednou rozdělený je tentýž, potvrzení stačí při shodě [CONFIRM] odstavců.
 * Nový odstavec (i krátká nová zpráva) je vždy změna.
 */
class PhoneTracker {
    private var shown: List<String> = emptyList()
    private var pending: List<String>? = null
    private var misses = 0
    private var pendingMisses = 0

    data class Result(val paragraphs: List<String>, val changed: Boolean)

    val current: List<String> get() = shown

    /** Čeká se na druhé (potvrzující) čtení – služba pak čte znovu hned, ne až po 3 s. */
    val waiting: Boolean get() = pending != null

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Tentýž odstavec přečtený dvakrát: podobný a skoro stejně dlouhý (krátká zpráva přilepená na konec je nový text). */
    private fun alike(x: String, y: String) =
        kotlin.math.abs(x.length - y.length) <= maxOf(1, maxOf(x.length, y.length) / 25) && TextLogic.similar(x, y)

    /** [whole] = [parts] za sebou (odstavec jednou slitý, jednou rozdělený): rozdíl nejvýš třetina nejkratší části. */
    private fun joined(parts: List<String>, whole: String): Boolean {
        val run = parts.joinToString("")
        return alike(run, whole) && TextLogic.levenshtein(run, whole) <= parts.minOf { it.length } / 3
    }

    /** Kolik odstavců z [b] je i v [a]: stejný / podobný, slitý ze 2–3 sousedních v [a], nebo část odstavce z [a] rozděleného v [b]. */
    private fun known(a: List<String>, b: List<String>): Int {
        if (a.isEmpty()) return 0
        var n = 0
        for (i in b.indices) {
            val p = b[i]
            val hit = a.any { alike(it, p) } ||
                a.indices.any { j -> (2..3).any { k -> j + k <= a.size && joined(a.subList(j, j + k), p) } } ||
                (2..3).any { k -> (maxOf(0, i - k + 1)..minOf(i, b.size - k)).any { from -> a.any { joined(b.subList(from, from + k), it) } } }
            if (hit) n++
        }
        return n
    }

    fun update(read: List<String>): Result {
        if (read.isEmpty()) {
            // jedno čtení bez telefonu (rozmazaný snímek, jas karty na hranici) rozpracovaný obsah neruší – dřív se tak
            // příspěvky v sociální síti nikdy nepotvrdily (dávka 4KHZ-AJKC)
            if (++pendingMisses >= MISSES) { pending = null; pendingMisses = 0 }
            if (shown.isEmpty()) return Result(shown, false)
            if (++misses < MISSES) return Result(shown, false)
            shown = emptyList(); misses = 0
            return Result(shown, true)
        }
        misses = 0; pendingMisses = 0
        val r = read.map(::norm).filter { it.isNotEmpty() }   // čáry místo obrázků se neporovnávají
        if (r.isEmpty()) return Result(shown, false)
        val s = shown.map(::norm).filter { it.isNotEmpty() }
        // nic nového a nic nechybí = stejný obsah
        if (s.isNotEmpty() && known(s, r) == r.size && known(r, s) == s.size) { pending = null; return Result(shown, false) }
        val p = pending
        // nový obsah, když nic není ukázané: ukázat hned po prvním čtení (od 0.97.134 jen krátký – náhled zprávy vpravo dole;
        // od 1.00-rc21 každý, dávka 6RKU-RZW3: SMS v autě se ukázaly až po 4–5 s, protože čtečka četla konverzaci pokaždé
        // trochu jinak a potvrzení trvalo; další čtení obsah jen zpřesní a stejné odstavce se znovu nepřekládají)
        if (shown.isEmpty() && p == null) { shown = read; return Result(shown, true) }
        // nová zpráva v otevřené konverzaci (všechno ukázané je dál vidět a přibyl nejvýš [QUICK_PARAS] odstavce) – hned
        if (s.isNotEmpty() && known(r, s) == s.size && r.size - known(s, r) in 1..QUICK_PARAS) {
            pending = null; shown = read; return Result(shown, true)
        }
        // úplně jiná obrazovka (jiná konverzace, ze seznamu do konverzace a zpět – z ukázaného zbyla nejvýš třetina): hned,
        // jako když nic ukázané není (dávka JJAG-MBQB, majitel: „mění se to po moc dlouhé době“ – nová konverzace čekala
        // na dvě shodná čtení 4–7 s)
        if (s.isNotEmpty() && known(s, r) * 3 <= r.size && known(r, s) * 3 <= s.size) {
            pending = null; shown = read; return Result(shown, true)
        }
        val q = p?.map(::norm)?.filter { it.isNotEmpty() }
        if (q == null || known(q, r) < r.size * CONFIRM || known(r, q) < q.size * CONFIRM) { pending = read; return Result(shown, false) }
        pending = null
        shown = read
        return Result(shown, true)
    }

    fun reset() { shown = emptyList(); pending = null; misses = 0; pendingMisses = 0 }

    companion object { const val MISSES = 2; const val QUICK_PARAS = 2; const val CONFIRM = 0.7f }
}
