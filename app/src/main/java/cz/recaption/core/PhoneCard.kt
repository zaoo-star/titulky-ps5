package cz.recaption.core

/**
 * Karta upozornění na domovské obrazovce telefonu ve hře (GTA VI „Reminders / 4 Notifications“, od 0.97.10x, přání majitele:
 * „Reminders budu chtít taky přeložit“). Podle dávky P2ZTVKQ5 (snímky 0077–0080, 1280×720): tmavé průsvitné zaoblené pole
 * x ~0,60–0,77, y ~0,26–0,35, v něm bílým písmem krátký název (ikona + „Reminders“, řádek ~2,5 % výšky) a pod ním menší
 * „4 Notifications“. [PhoneScreen.read] bere jen tmavé písmo na světlém pozadí, proto kartu vynechá – tady se pozná podle
 * tvaru: krátký název a hned pod ním „N Notification(s)“, obojí světlým písmem. Jede pod volbou „Překlad telefonu“.
 * Bez Androidu – testy `PhoneCardTest`.
 */
object PhoneCard {
    private val COUNT = Regex("^(\\d{1,3})\\s*notif\\w*\\.?$", RegexOption.IGNORE_CASE)
    private val TITLE = Regex("^[A-Za-z][A-Za-z'&.\\- ]{2,23}$")
    /** Značky aplikací ve hře – zůstávají anglicky (jako na telefonu ve hře). */
    private val BRANDS = setOf("whatup", "buckme", "waink", "rydeme", "ifruit", "lifeinvader", "eyefind")

    /** Řádek „N Notifications“ → N, jinak null. */
    fun count(text: String): Int? = COUNT.matchEntire(text.trim())?.groupValues?.get(1)?.toIntOrNull()

    /** Název karty bez ikony na začátku (OCR ji čte jako „@“, „*“, „®“…), nebo null, když to název není. */
    fun title(text: String): String? {
        if (count(text) != null) return null
        val t = text.trim().trimStart { !it.isLetter() }.trim()
        if (!TITLE.matches(t) || t.split(' ').size > 3 || count(t) != null) return null
        return t
    }

    /**
     * Světlé písmo na tmavším poli: 90. percentil aspoň 140 a aspoň o 40 nad mediánem. Na snímku 0078 (JPEG) 10/50/90 %:
     * „Reminders“ 39/82/206, „4 Notifications“ 35/70/178; tmavé písmo telefonu (bublina) 126/142/146 neprojde.
     */
    fun light(gray: IntArray): Boolean {
        if (gray.size < 20) return false
        val s = gray.sortedArray()
        val med = s[s.size / 2]
        val p90 = s[(s.size * 9) / 10]
        // ztlumený displej v noci (dávka 6RKU-RZW3, nadpis seznamu 8/22/96): světlé písmo jen ~100, ale pořád víc než
        // dvojnásobek pozadí
        return p90 - med >= 40 && (p90 >= 140 || (p90 >= 85 && p90 * 10 >= med * 18))
    }

    /**
     * Karty z řádků OCR (pixely celého obrazu [fullW]×[fullH]): název a hned pod ním (mezera nejvýš 1,2 výšky názvu, levý
     * okraj nejvýš 2,5 výšky vedle) řádek „N Notifications“, obojí [light]. Jedna karta = nadpis + „N Notifications“,
     * víc karet = odstavce „Název · N Notifications“ (shora dolů). Tvar jako [PhoneScreen.read], takže ji služba ukáže
     * v panelu telefonu beze změny aplikace v TV. null = žádná karta.
     */
    fun read(lines: List<MenuLine>, fullW: Int, fullH: Int, light: (MenuLine) -> Boolean): PhoneRead? {
        if (fullW <= 0 || fullH <= 0) return null
        list(lines, fullW, fullH, light)?.let { return it }
        val cards = ArrayList<Triple<String, MenuLine, MenuLine>>()
        val used = HashSet<MenuLine>()
        for (c in lines.filter { count(it.text) != null }.sortedBy { it.top }) {
            val name = lines.asSequence()
                .filter { it !== c && it !in used && it.top < c.top && it.bottom <= c.top + (c.bottom - c.top) / 2 }
                .mapNotNull { t -> title(t.text)?.let { it to t } }
                .filter { (_, t) ->
                    val h = (t.bottom - t.top).coerceAtLeast(1)
                    c.top - t.bottom <= h * 1.2f && kotlin.math.abs(c.left - t.left) <= h * 2.5f && t.right - t.left < fullW * 0.25f
                }
                .minByOrNull { (_, t) -> c.top - t.bottom } ?: continue
            if (!light(name.second) || !light(c)) continue
            used += name.second; used += c
            cards += Triple(name.first, name.second, c)
        }
        if (cards.isEmpty()) return null
        val all = cards.flatMap { listOf(it.second, it.third) }
        val l = all.minOf { it.left }.toFloat() / fullW; val t = all.minOf { it.top }.toFloat() / fullH
        val r = all.maxOf { it.right }.toFloat() / fullW; val b = all.maxOf { it.bottom }.toFloat() / fullH
        val counts = cards.map { it.third.text.trim() }
        return if (cards.size == 1) PhoneRead(counts, l, t, r, b, title = cards[0].first, card = true)
        else PhoneRead(cards.map { it.first + SEP + it.third.text.trim() }, l, t, r, b, card = true)
    }

    private val HEADER = Regex("^(.+?)\\s*\\((\\d{1,2})\\)$")

    /** Nadpis rozbaleného seznamu upozornění „Název (N)“ → název a N, jinak null. */
    fun header(text: String): Pair<String, Int>? {
        val m = HEADER.matchEntire(text.trim().trimStart { !it.isLetter() }) ?: return null
        val name = title(m.groupValues[1]) ?: return null
        return name to (m.groupValues[2].toIntOrNull() ?: return null)
    }

    /**
     * Rozbalený seznam upozornění (dávka NDNS-VQZ7, 1280×720: tmavé průsvitné pole, nahoře světlým písmem „Název (N)“,
     * pod ním položky – tučný řádek, kdo / co, a pod ním jeden až dva řádky textu, řádek ~18 px, mezi položkami ~20 px).
     * Do 1.00-rc10 se nepřekládal vůbec ([PhoneScreen.read] bere jen tmavé písmo). Pozná se podle nadpisu se závorkou
     * a aspoň dvou světlých řádků pod ním ve stejném sloupci. Položka = odstavec „první řádek · zbytek“ (služba překládá
     * části zvlášť, jména a @účty nechává), tvar jako karta ([PhoneRead.card]).
     */
    fun list(lines: List<MenuLine>, fullW: Int, fullH: Int, light: (MenuLine) -> Boolean): PhoneRead? {
        val head = lines.filter { header(it.text) != null && it.right - it.left < fullW * 0.25f }.minByOrNull { it.top } ?: return null
        if (!light(head)) return null
        val h = (head.bottom - head.top).coerceAtLeast(1)
        val rows = lines.filter { l ->
            l !== head && l.top >= head.bottom - h / 4 && l.left >= head.left - h && l.left <= head.left + h * 4 &&
                l.right <= head.left + fullW * 0.25f && l.text.count { it.isLetter() } >= 2 && light(l)
        }.sortedBy { it.top }
        if (rows.size < 2) return null
        val items = ArrayList<MutableList<MenuLine>>()
        // krok mezi řádky jedné položky: u natočeného telefonu (chůze) jsou rámečky řádků vyšší a mezery mezi položkami
        // z nich nejsou vidět (dávka 6RKU-RZW3 #0010 – dvě upozornění se slila) – další položka = krok o polovinu delší
        val steps = rows.zipWithNext { a, b -> b.top - a.top }.filter { it > 0 }
        val step = steps.minOrNull() ?: 0
        for (l in rows) {
            val prev = items.lastOrNull()?.last()
            if (prev == null || l.top - prev.bottom > (l.bottom - l.top) * 0.6f || (steps.size >= 2 && l.top - prev.top > step * 1.5f))
                items += mutableListOf(l) else items.last() += l
        }
        val paras = items.map { it.first().text.trim() + if (it.size > 1) SEP + it.drop(1).joinToString(" ") { l -> l.text.trim() } else "" }
        val all = rows + head
        return PhoneRead(paras, all.minOf { it.left }.toFloat() / fullW, all.minOf { it.top }.toFloat() / fullH,
            all.maxOf { it.right }.toFloat() / fullW, all.maxOf { it.bottom }.toFloat() / fullH, title = head.text.trim().trimStart { !it.isLetter() }, card = true)
    }

    /** Oddělovač názvu a počtu, když je karet víc (služba překládá části zvlášť). */
    const val SEP = " · "

    /**
     * Pevný překlad části karty, nebo null (pak ji přeloží překladač): „N Notifications“ česky „N oznámení“ (nesklonné),
     * slovensky 1 upozornenie / 2–4 upozornenia / jinak upozornení; název z pevných překladů profilu hry
     * ([CornerSpec.word] – REMINDERS → Připomínky / Pripomienky); značka aplikace (WhatUp…) zůstává.
     */
    fun fixed(text: String, lang: Lang, spec: CornerSpec?): String? {
        val t = text.trim()
        count(t)?.let { n ->
            return when (lang.code) {
                "cs" -> "$n oznámení"
                "sk" -> "$n " + when (n) { 1 -> "upozornenie"; in 2..4 -> "upozornenia"; else -> "upozornení" }
                else -> null
            }
        }
        if (t.lowercase().replace(" ", "") in BRANDS) return t
        // účet v sociální síti (@jméno) zůstává; nadpis seznamu „Název (N)“ = pevný překlad názvu a počet
        if (PhoneScreen.handle(t) != null && ' ' !in t) return t
        header(t)?.let { (name, n) -> return spec?.word(lang, name)?.let { "$it ($n)" } }
        return spec?.word(lang, t)
    }
}
