package cz.recaption.core

/**
 * Upozornění telefonu ve hře vpravo dole (GTA VI: název aplikace a text upozornění, dávka testera 6WA3-ETJV #67–73,
 * 22. 11. 2026): tmavé zaoblené pole x 0,78–0,98 × y 0,90–0,975, vlevo barevná ikona, nahoře název aplikace (krátce,
 * skoro celé verzálkami), pod ním text upozornění (malá písmena), písmo ~1,7 % výšky, drží se ~7 s. Od 0.97.98 se
 * překládá s volbou „Překlad telefonu“; překlad přes anglický text upozornění, název aplikace zůstane.
 */
data class Notice(val app: String, val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float,
                  /** Upozornění na SMS (od 1.00-rc21): [app] = jméno odesílatele, nepřekládá se. */
                  val sms: Boolean = false,
                  /** Výška jednoho řádku zprávy (0 = [bottom]-[top], jako dřív) – u víceřádkové SMS (1.0.3, hlášení
                   *  845E-5DJ3: „obrovské písmo“) je jinak [bottom]-[top] výška CELÉ bubliny a písmo vyjde přes obrazovku. */
                  val lineH: Float = 0f)

object PhoneNotice {
    const val ZONE_L = 0.74f
    // od 1.00-rc21 i upozornění na SMS – bublina je výš (dávka N6CU-NTGR: y 0,83–0,92, pod ní příjemce do 0,97)
    const val ZONE_T = 0.78f
    const val ZONE_R = 0.995f
    const val ZONE_B = 0.995f

    /** Mřížka pro poznání změny (3 × 2 políčka přes oblast) – beze změny se znovu nečte. */
    val GRID: List<FloatArray> = (0 until 3).flatMap { r -> (0 until 3).map { c ->
        val x = 0.77f + c * 0.075f; val y = 0.81f + r * 0.055f
        floatArrayOf(x, y, x + 0.05f, y + 0.03f)
    } }

    private fun letters(t: String) = t.count { it.isLetter() }
    private fun upperShare(t: String): Float = letters(t).let { n -> if (n == 0) 0f else t.count { it.isUpperCase() }.toFloat() / n }

    /** Název aplikace: 1–2 slova, aspoň 3 písmena, aspoň 70 % verzálek („CHATAPP“). */
    fun appName(t: String): Boolean {
        val w = t.trim().split(Regex("\\s+"))
        return w.size in 1..2 && letters(t) >= 3 && upperShare(t) >= 0.7f
    }

    /** Text upozornění: aspoň 2 slova, malá písmena (ne verzálky menu vpravo dole), nejvýš 80 znaků. */
    fun message(t: String): Boolean {
        val s = t.trim()
        return s.split(Regex("\\s+")).size >= 2 && letters(s) >= 5 && s.any { it.isLowerCase() } && upperShare(s) < 0.6f && s.length <= 80
    }

    /**
     * Upozornění z řádků OCR (pixely celého obrazu): řádek s názvem aplikace a hned pod ním (mezera do 1,2 řádku,
     * levý okraj do 1 řádku) text upozornění. Poloha = text upozornění (to se překrývá). null = žádné.
     * Řádky ovládání vpravo dole (verzálky u sloupce ikon, [CornerPrompts.looksLikePrompt]) se neberou – seznam
     * „HOOK FISH / LURE FISH / RESET CAST / GRIP“ při rybaření vypadal jako SMS (jméno, zpráva, příjemce) a jeho
     * „překlad“ se kreslil přes ovládání (dávka 38K7-5K4G, od 1.0.04).
     */
    fun read(all: List<MenuLine>, fullW: Int, fullH: Int): Notice? {
        if (fullW <= 0 || fullH <= 0) return null
        val lines = all.filterNot { CornerPrompts.looksLikePrompt(it, fullW, fullH) }
        sms(lines, fullW, fullH)?.let { return it }
        val sorted = lines.sortedBy { it.top }
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]; val b = sorted[i + 1]
            val lh = (a.bottom - a.top).coerceAtLeast(1)
            if (a.left < fullW * 0.76f || !appName(a.text) || !message(b.text)) continue
            if (b.top - a.bottom > lh * 1.2f || kotlin.math.abs(b.left - a.left) > lh * 1.0f) continue
            return Notice(a.text.trim(), b.text.trim(), b.left.toFloat() / fullW, b.top.toFloat() / fullH,
                b.right.toFloat() / fullW, b.bottom.toFloat() / fullH)
        }
        return null
    }
}

/** Jméno odesílatele / příjemce bez ikony před ním (čtečka ji čte jako „&“, „@“, „→“…), nebo null. */
private fun smsName(t: String): String? {
    val n = t.trim().trimStart { !it.isLetter() }.trim().trimEnd { !it.isLetterOrDigit() }
    if (n.length !in 2..24 || n.split(' ').size > 3 || !n[0].isUpperCase() || n.any { it in ".!?,:" }) return null
    return n
}

/**
 * Upozornění na SMS vpravo dole bez telefonu v ruce (dávka N6CU-NTGR #0003, majitel chce překládat i náhledy SMS):
 * šedá bublina – nahoře ikona a jméno odesílatele (vpravo ✓✓), pod tím text zprávy (1–3 řádky), pod bublinou řádek
 * „→ příjemce“ s ikonou. Pozná se podle jména a hned pod ním zprávy, s řádkem příjemce pod nimi (šipku čtečka čte
 * různě, proto stačí krátké jméno pod zprávou). Odesílatel ani příjemce se nepřekládají; poloha = text zprávy.
 */
fun PhoneNotice.sms(lines: List<MenuLine>, fullW: Int, fullH: Int): Notice? {
    val sorted = lines.filter { it.left >= fullW * 0.74f && it.top >= fullH * 0.70f && !CornerPrompts.looksLikePrompt(it, fullW, fullH) }.sortedBy { it.top }
    for (i in 0 until sorted.size - 2) {
        val a = sorted[i]
        val sender = smsName(a.text) ?: continue
        val lh = (a.bottom - a.top).coerceAtLeast(1)
        val body = ArrayList<MenuLine>()
        var j = i + 1
        while (j < sorted.size) {
            val b = sorted[j]
            val prev = body.lastOrNull() ?: a
            if (b.top - prev.bottom > lh * 1.2f || b.left > a.left + lh * 0.5f || a.left - b.left > lh * 3f) break
            if (body.isNotEmpty() && smsName(b.text) != null && b.text.trim().split(' ').size <= 2 && b.top - prev.bottom > lh * 0.4f) break
            body += b; j++
        }
        if (body.isEmpty() || body.none { l -> l.text.count { it.isLetter() } >= 2 }) continue
        // příjemce pod bublinou: krátké jméno, zarovnané vpravo (končí u pravého okraje bubliny)
        val to = sorted.getOrNull(j) ?: continue
        if (smsName(to.text) == null || to.top - body.last().bottom > lh * 2.5f || to.right < body.maxOf { it.right } - lh * 3) continue
        val text = body.joinToString(" ") { it.text.trim() }
        return Notice(sender, text, body.minOf { it.left }.toFloat() / fullW, body.first().top.toFloat() / fullH,
            body.maxOf { it.right }.toFloat() / fullW, body.last().bottom.toFloat() / fullH, sms = true, lineH = lh.toFloat() / fullH)
    }
    return null
}

/** Upozornění v čase: ukáže se po dvou stejných čteních (useknuté čtení se nebere jako jiné), zmizí po 2 bez něj. */
class NoticeTracker {
    private var shown: Notice? = null
    private var pending: Notice? = null
    private var misses = 0

    val current: Notice? get() = shown

    data class Result(val notice: Notice?, val changed: Boolean)

    private fun same(a: Notice?, b: Notice?) = a != null && b != null &&
        (a.text == b.text || TextLogic.similar(a.text, b.text) || a.text.startsWith(b.text) || b.text.startsWith(a.text))

    fun update(read: Notice?): Result {
        if (read == null) {
            pending = null
            if (shown == null) return Result(null, false)
            if (++misses < 2) return Result(shown, false)
            shown = null; misses = 0
            return Result(null, true)
        }
        misses = 0
        if (same(shown, read)) {
            // delší (celé) čtení téhož textu nahradí useknuté
            val s = shown!!
            if (read.text.length > s.text.length) { shown = read; return Result(read, true) }
            return Result(s, false)
        }
        // upozornění na SMS hned (drží se jen pár sekund; má jasný tvar – jméno, zpráva, příjemce)
        if (read.sms && shown == null) { pending = null; shown = read; return Result(read, true) }
        if (!same(pending, read)) { pending = read; return Result(shown, false) }
        pending = null
        shown = read
        return Result(read, true)
    }

    fun reset() { shown = null; pending = null; misses = 0 }
}
