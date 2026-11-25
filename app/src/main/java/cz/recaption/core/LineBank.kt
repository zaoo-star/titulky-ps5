package cz.recaption.core

/**
 * „Sbírání replik ze hry“ (⚙ → Čtení a překlad, od 0.97.139; od 1.00 výchozí zapnuto bez dotazu – návrh
 * `navrhy/preklad-cele-hry.md`): anglické repliky, které aplikace ve hře přečetla a ukázala, se v zařízení skládají
 * do seznamu (každá jen jednou, s počtem čtení) a nové se posílají do schránky vývojáře. Z replik od všech hráčů
 * časem vznikne předem přeložená paměť celé hry.
 *
 * Co se ukládá: hra (id profilu), jazyk titulků, mluvčí z titulku, text repliky, kolikrát se přečetla, číslo
 * rozhovoru a pořadí v něm (kvůli překladu celých rozhovorů) a druh textu. Nic jiného – žádné snímky, překlady, čas, zařízení.
 * Tady je jen logika bez Androidu (testy `LineBankTest`); ukládání a odesílání je `LineCollector`.
 *
 * **Druhy (od 1.00, přání majitele – offline balíčky překladu pro celé hry):** kromě replik ([REPLIKA]) i ostatní texty hry,
 * které aplikace přečte a ukáže přeložené: pokyny pod titulky [POKYN], úkol [UKOL], menu hry [MENU], telefon ve hře [TELEFON],
 * tipy [TIP], pokyny u předmětů [PREDMET], menu vpravo dole [ROH]. Stejný text jiného druhu = jiná položka. Ostatní druhy
 * nemají rozhovor (`conv` = 0) a mají vlastní strop [MAX_OTHER] – menu nevytlačí repliky (a naopak). Druh je v uloženém
 * i odesílaném souboru poslední sloupec; starý řádek bez něj = replika.
 */
class LineBank(private val maxLines: Int = MAX_LINES, private val maxOther: Int = MAX_OTHER) {

    data class Entry(
        val game: String,
        val source: String,
        var speaker: String?,
        var text: String,
        var count: Int,
        /** Kolik čtení už je odesláno (odesílá se rozdíl [count] − [sent]). */
        var sent: Int,
        /** Rozhovor (pořadové číslo v zařízení) a pořadí repliky v něm – při prvním přečtení. */
        val conv: Int,
        val order: Int,
        /** Druh textu ([REPLIKA], [MENU]…). */
        val kind: String = REPLIKA,
    ) {
        val pending: Int get() = count - sent
    }

    private val byKey = LinkedHashMap<String, Entry>()
    /** Číslo dalšího rozhovoru (pokračuje i po uložení a načtení). */
    var nextConv = 1
        private set
    private var conv = 0
    private var order = 0
    private var lastAt = Long.MIN_VALUE / 2
    private var lastGame = ""
    /** Změna od posledního uložení. */
    var dirty = false

    val size: Int get() = byKey.size
    /** Kolik čtení čeká na odeslání. */
    val pendingCount: Int get() = byKey.values.sumOf { it.pending.coerceAtLeast(0) }
    /** Kolik různých replik má něco k odeslání. */
    val pendingLines: Int get() = byKey.values.count { it.pending > 0 }
    val entries: Collection<Entry> get() = byKey.values

    /**
     * Ukázaná replika ([speaker] = jméno z titulku, nebo null) ve hře [game] z jazyka [source] v čase [now] (ms).
     * Nový rozhovor začne po pauze delší než [CONV_GAP_MS] nebo v jiné hře. Vrací true, když se seznam změnil.
     * [kind] = druh textu; jiný než [REPLIKA] rozhovory nemění a má vlastní strop [maxOther].
     */
    fun add(game: String, source: String, speaker: String?, body: String, now: Long, kind: String = REPLIKA): Boolean {
        val line = kind == REPLIKA
        if (!line && kind !in KINDS) return false
        val text = clean(body, if (line) MAX_TEXT else MAX_TEXT_OTHER) ?: return false
        val who = speaker?.let { clean(it) }?.takeIf { it.length <= MAX_SPEAKER }
        if (line) {
            if (now - lastAt > CONV_GAP_MS || game != lastGame) { conv = nextConv++; order = 0 }
            lastAt = now; lastGame = game
        }
        val k = key(game, source, text, kind)
        val e = byKey[k]
        if (e != null) {
            // stejná replika znovu: počet, a čistší čtení nahradí zkomolené
            e.count++
            if (StableLines.noise(text) < StableLines.noise(e.text)) e.text = text
            if (e.speaker == null && who != null) e.speaker = who
        } else {
            if (groupSize(line) >= (if (line) maxLines else maxOther) && !evict(line)) return false
            byKey[k] = if (line) Entry(game, source, who, text, 1, 0, conv, order++)
                else Entry(game, source, who, text, 1, 0, 0, 0, kind)
        }
        dirty = true
        return true
    }

    /** Kolik položek má skupina: repliky ([line] = true), nebo ostatní druhy. */
    private fun groupSize(line: Boolean): Int = byKey.values.count { (it.kind == REPLIKA) == line }

    /** Plná skupina: vyhodí její nejstarší už odeslanou položku; když nic odeslané není, nic se nepřidá. */
    private fun evict(line: Boolean): Boolean {
        val it = byKey.entries.iterator()
        while (it.hasNext()) { val e = it.next().value; if ((e.kind == REPLIKA) == line && e.pending <= 0) { it.remove(); return true } }
        return false
    }

    /** Co poslat (nejvýš [max] položek s neodeslanými čteními): napřed repliky, pak ostatní druhy, v pořadí přečtení. */
    fun pendingEntries(max: Int = MAX_UPLOAD): List<Entry> =
        byKey.values.filter { it.pending > 0 }.sortedBy { if (it.kind == REPLIKA) 0 else 1 }.take(max)

    /** Odesláno: [list] se počítá jako odeslaný až po [counts] (počty v okamžiku balení). */
    fun markSent(list: List<Entry>, counts: List<Int>) {
        list.forEachIndexed { i, e -> e.sent = maxOf(e.sent, counts[i]) }
        dirty = true
    }

    fun clear() { byKey.clear(); dirty = true }

    // ---------------- soubory ----------------

    /** Uložený tvar (TSV, jen v zařízení): hlavička s dalším rozhovorem, pak řádek na položku (druh poslední sloupec). */
    fun save(): String = buildString {
        append(HEADER).append('\t').append(nextConv).append('\n')
        for (e in byKey.values) {
            append(tsv(e.game)).append('\t').append(tsv(e.source)).append('\t').append(e.count).append('\t').append(e.sent)
                .append('\t').append(e.conv).append('\t').append(e.order).append('\t').append(tsv(e.speaker ?: ""))
                .append('\t').append(tsv(e.text)).append('\t').append(e.kind).append('\n')
        }
    }

    /** Načte uložený tvar (neplatné řádky přeskočí); jiný / poškozený soubor = prázdný seznam. */
    fun load(text: String?) {
        byKey.clear(); nextConv = 1
        val lines = text?.split('\n') ?: return
        val head = lines.firstOrNull()?.split('\t') ?: return
        if (head.firstOrNull() != HEADER) return
        nextConv = head.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        for (l in lines.drop(1)) {
            val f = l.split('\t')
            // 8 sloupců = soubor do 1.00 (jen repliky), 9. sloupec = druh
            if (f.size != 8 && f.size != 9) continue
            val kind = if (f.size == 9) untsv(f[8]).takeIf { it in KINDS } ?: continue else REPLIKA
            val line = kind == REPLIKA
            val count = f[2].toIntOrNull()?.takeIf { it > 0 } ?: continue
            val sent = f[3].toIntOrNull()?.coerceIn(0, count) ?: continue
            val c = f[4].toIntOrNull() ?: continue
            val o = f[5].toIntOrNull() ?: continue
            val text = clean(untsv(f[7]), if (line) MAX_TEXT else MAX_TEXT_OTHER) ?: continue
            val e = Entry(untsv(f[0]), untsv(f[1]), untsv(f[6]).ifEmpty { null }, text, count, sent, c, o, kind)
            if (groupSize(line) < (if (line) maxLines else maxOther)) byKey[key(e.game, e.source, e.text, kind)] = e
        }
        dirty = false
    }

    companion object {
        const val HEADER = "recaption-repliky-1"
        /** Hlavička odesílaného souboru `repliky.tsv`. */
        const val UPLOAD_HEADER = "hra\tjazyk\tpocet\tmluvci\ttext\trozhovor\tporadi\tdruh"
        /** Druhy textu (poslední sloupec souborů). */
        const val REPLIKA = "replika"
        const val POKYN = "pokyn"
        const val UKOL = "ukol"
        const val MENU = "menu"
        const val TELEFON = "telefon"
        const val TIP = "tip"
        const val PREDMET = "predmet"
        const val ROH = "roh"
        val KINDS = setOf(REPLIKA, POKYN, UKOL, MENU, TELEFON, TIP, PREDMET, ROH)
        const val MAX_LINES = 30_000
        /** Strop pro ostatní druhy dohromady (menu, telefon…) – repliky mají vlastní [MAX_LINES]. */
        const val MAX_OTHER = 10_000
        /** Delší texty u ostatních druhů (popis volby v menu, tip, zpráva v telefonu). */
        const val MAX_TEXT_OTHER = 600
        const val MAX_UPLOAD = 5_000
        const val MAX_TEXT = 300
        const val MAX_SPEAKER = 40
        /** Pauza mezi replikami, po které začíná nový rozhovor. */
        const val CONV_GAP_MS = 8_000L

        /** Klíč položky: hra, jazyk a text bez velikosti písmen a interpunkce (varianty čtení splynou); jiný druh než replika navíc druh. */
        fun key(game: String, source: String, text: String, kind: String = REPLIKA): String =
            game + "|" + source + "|" + TextLogic.simplify(text).split(' ').filter { it.isNotEmpty() }.joinToString(" ") +
                (if (kind == REPLIKA) "" else "|" + kind)

        /** Text k uložení: jeden řádek bez tabulátorů, aspoň 2 písmena, nejvýš [max] znaků; jinak null. */
        fun clean(s: String, max: Int = MAX_TEXT): String? {
            val t = s.replace('\t', ' ').replace('\n', ' ').replace(Regex(" {2,}"), " ").trim()
            if (t.length > max || t.count { it.isLetter() } < 2) return null
            return t
        }

        private fun tsv(s: String) = s.replace('\t', ' ').replace('\n', ' ')
        private fun untsv(s: String) = s.trim('\r')

        /** Odesílaný `repliky.tsv`: hlavička a řádek na položku s počtem neodeslaných čtení (druh poslední sloupec). */
        fun upload(list: List<Entry>): String = buildString {
            append(UPLOAD_HEADER).append('\n')
            for (e in list) {
                append(tsv(e.game)).append('\t').append(tsv(e.source)).append('\t').append(e.pending).append('\t')
                    .append(tsv(e.speaker ?: "")).append('\t').append(tsv(e.text)).append('\t').append(e.conv)
                    .append('\t').append(e.order).append('\t').append(e.kind).append('\n')
            }
        }

        /** Poslat teď? Aspoň [MIN_BATCH] čtení, nebo cokoli po [DAY_MS] od posledního odeslání. */
        const val MIN_BATCH = 150
        const val DAY_MS = 24 * 3_600_000L
        /** Další pokus po nepovedeném nejdřív za tuhle dobu. */
        const val RETRY_MS = 30 * 60_000L

        /** [minGap] = nejkratší odstup od posledního odeslání (bez vlastních mezí replik na serveru [DAY_MS]). */
        fun due(pending: Int, lastSent: Long, lastTry: Long, now: Long, minGap: Long = 0L): Boolean {
            if (pending <= 0 || now - lastTry < RETRY_MS || now - lastSent < minGap) return false
            return pending >= MIN_BATCH || now - lastSent >= DAY_MS
        }
    }
}
