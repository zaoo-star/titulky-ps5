package cz.recaption.pc

import cz.recaption.core.GameProfile
import cz.recaption.core.MenuLine
import cz.recaption.core.TvSender

/**
 * Obrazovky menu GTA VI s lištou záložek nahoře (JASON & LUCIA · MAP · BRIEF · PROGRESS – majitel 25. 11. 2026:
 * „tohle bych chtěl taky celé překládat“): když jsou v horním pásu aspoň dvě známé záložky, přečte se celá obrazovka,
 * řádky se spojí do odstavců a každý přeložený odstavec se v TV položí přes svůj originál (zpráva `telefon` s „vedle“ –
 * tmavé pozadí, bílé písmo; panel telefonu a menu nejsou nikdy vidět zároveň). Lišta záložek a ovládání vpravo dole
 * se nepřekládají.
 */
internal class MenuPc(
    private val profil: GameProfile,
    private val preloz: (texty: List<String>, mluvci: List<String?>) -> List<String>,
    private val log: (String) -> Unit,
) {
    val zapnuto = profil.id == "gta6"
    private var checkAt = 0L
    private var key = ""
    private var grid: IntArray? = null
    private val words = HashMap<String, String>()
    /** Poprvé přečtené znění odstavce podle písmen (bez mezer) – další čtení téhož textu se slepenými slovy se nepřekládají znovu. */
    private val stejne = HashMap<String, String>()
    var shown = false
        private set

    fun tick(now: Long, helper: PhoneHelper, send: (String) -> Unit) {
        if (!zapnuto || now - checkAt < (if (shown) 700 else 1500)) return
        checkAt = now
        val horni = helper.telefon(floatArrayOf(0.2f, 0f, 0.8f, 0.12f), 1f) ?: return
        val zalozek = horni.lines.count { (l, _) -> jednoduse(l.text) in ZALOZKY }
        if (zalozek < 2) {
            if (shown) { shown = false; key = ""; send(TvSender.PHONE_HIDE); log("MENU schovano") }
            return
        }
        // obsah beze změny (otisk jasu celé obrazovky) → znovu nečíst: celé čtení obrazovky je drahé a brzdilo titulky
        val g = helper.mrizka(floatArrayOf(0f, 0.1f, 1f, 0.95f), 12, 10)
        val prev = grid
        grid = g
        if (shown && g != null && !cz.recaption.core.PhoneScreen.changed(prev, g, 8)) return
        val cele = helper.telefon(floatArrayOf(0f, 0f, 1f, 1f), 1f) ?: return
        val w = cele.w.toFloat(); val h = cele.h.toFloat()
        val radky = cele.lines.map { it.first }.filter { l ->
            jednoduse(l.text) !in ZALOZKY && l.top > h * 0.09f && l.text.count { it.isLetter() } >= 2 &&
                !(l.left > w * 0.8f && l.top > h * 0.88f) && jednoduse(l.text) !in TLACITKA
        }
        val odstavce = seskup(radky, w, h).map { (t, b) -> (stejne[jednoduse(t)] ?: t.also { stejne[jednoduse(t)] = it }) to b }
        // klíč jen z písmen: čtečka střídá „Help Text“ / „HelpText“ – jinak by se tentýž obsah překládal a posílal pořád dokola
        val k = odstavce.joinToString("|") { jednoduse(it.first) }
        if (k == key && shown) return
        key = k
        if (odstavce.isEmpty()) {
            if (shown) { shown = false; send(TvSender.PHONE_HIDE) }
            return
        }
        val chybi = odstavce.map { it.first }.filter { it !in words && pevne(it) == null }.distinct()
        if (chybi.isNotEmpty()) {
            val out = try { preloz(chybi, chybi.map { null }) } catch (_: Exception) { chybi }
            chybi.forEachIndexed { i, t -> words[t] = out.getOrNull(i) ?: t }
        }
        val vyska = radky.map { it.bottom - it.top }.sorted().let { if (it.isEmpty()) 24 else it[it.size / 2] }
        val px = (vyska * 1080f / h * 0.8f).toInt().coerceIn(14, 48)
        send(zprava(odstavce.map { (t, b) -> (pevne(t) ?: words[t] ?: t) to b }, px))
        shown = true
        log("MENU " + odstavce.joinToString(" ¦ ") { (t, _) -> t.take(60) + " → " + (pevne(t) ?: words[t] ?: "").take(60) }.take(700))
    }

    /** Řádky pod sebou se stejným levým okrajem a malou mezerou = jeden odstavec (víceřádkový popis). */
    private fun seskup(radky: List<MenuLine>, w: Float, h: Float): List<Pair<String, FloatArray>> {
        val out = ArrayList<Pair<StringBuilder, IntArray>>()
        for (l in radky.sortedWith(compareBy({ it.top }, { it.left }))) {
            val lh = l.bottom - l.top
            val p = out.lastOrNull { (_, b) -> kotlin.math.abs(b[0] - l.left) < w * 0.02f && l.top - b[3] in -lh / 2..lh }
            if (p != null) {
                p.first.append(' ').append(l.text.trim())
                p.second[1] = minOf(p.second[1], l.top); p.second[2] = maxOf(p.second[2], l.right); p.second[3] = maxOf(p.second[3], l.bottom)
            } else out += StringBuilder(l.text.trim()) to intArrayOf(l.left, l.top, l.right, l.bottom)
        }
        return out.take(10).map { (t, b) ->  // TV ukáže nejvýš 10 kousků přes text
            t.toString() to floatArrayOf(b[0] / w, b[1] / h, b[2] / w, b[3] / h)
        }
    }

    private fun zprava(items: List<Pair<String, FloatArray>>, px: Int): String {
        val z = items.joinToString(",") { (t, b) ->
            val m = b.joinToString(",") { String.format(java.util.Locale.ROOT, "%.4f", it.coerceIn(0f, 1f)) }
            "{\"text\":${TvSender.json(t)},\"misto\":[$m]}"
        }
        return "{\"typ\":\"telefon\",\"rezim\":\"pres\",\"vedle\":true,\"pismo\":$px,\"zpravy\":[$z]}"
    }

    /** Pevný překlad krátkých položek menu (překladač je překládá doslova: „Objective Text“ → „Objektivní text“). */
    private fun pevne(t: String): String? = PEVNE[jednoduse(t)]

    private companion object {
        fun jednoduse(t: String) = t.lowercase().filter { it.isLetter() }
        val PEVNE = mapOf("all" to "Vše", "dialogue" to "Dialogy", "helptext" to "Nápověda", "objectivetext" to "Úkoly",
            "map" to "Mapa", "brief" to "Přehled", "progress" to "Postup", "stats" to "Statistiky", "settings" to "Nastavení",
            "norecententriestodisplayhere" to "Žádné nedávné záznamy", "missions" to "Mise", "activities" to "Aktivity",
            "contacts" to "Kontakty", "legend" to "Legenda")
        /** Záložky horní lišty obrazovek mapy / deníku a menu pauzy GTA VI. */
        val ZALOZKY = setOf("jasonlucia", "jason", "lucia", "map", "brief", "progress", "stats", "settings", "gallery",
            "online", "store", "game", "photos", "collectibles", "help")
        /** Ovládání (vpravo dole) – nepřekládá se. */
        val TLACITKA = setOf("exit", "back", "select", "scroll", "zoom", "waypoint", "legend", "navigate", "close")
    }
}
