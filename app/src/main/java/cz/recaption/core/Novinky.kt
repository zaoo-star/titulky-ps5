package cz.recaption.core

/**
 * Novinky mezi verzemi (přání majitele, 24. 11. 2026): na webu leží `pomoc/novinky.json` s pár body ke každé
 * verzi od 1.00 (česky a anglicky), doplňuje se při vydání stejně jako `version.txt`. Řádek „Co je nového“ vedle
 * Aktualizovat na Domů ukáže body od verze, kterou má hráč nainstalovanou, po tu nabízenou – stahuje se stejně
 * jako `videa/kroky.json` ([cz.recaption.VideoStepList]), ne při každém spuštění.
 *
 * Tvar: `{"zaznamy":[{"vc":261,"verze":"1.00-rc17","cs":["bod",…],"en":["item",…]},…]}`. Čtení je ruční (regulární
 * výrazy), ne `org.json` – `core` se překládá i do PC aplikace, která tu knihovnu nemá (stejně jako [VideoSteps]).
 * Neplatná položka / chybějící pole se vynechá, nesmysl nebo přes 300 kB = prázdný seznam (nikdy pád).
 */
object Novinky {
    data class Zaznam(val vc: Int, val verze: String, val cs: List<String>, val en: List<String>)

    const val URL = HelpLinks.BASE + "novinky.json"

    private val VC = Regex("\"vc\"\\s*:\\s*(\\d+)")
    private val VERZE = Regex("\"verze\"\\s*:\\s*\"([^\"]{1,20})\"")
    private val RETEZEC = Regex("\"([^\"]{1,200})\"")

    fun parse(text: String?): List<Zaznam> {
        if (text.isNullOrBlank() || text.length > 300_000) return emptyList()
        val start = text.indexOf("\"zaznamy\"").takeIf { it >= 0 } ?: return emptyList()
        val open = text.indexOf('[', start).takeIf { it >= 0 } ?: return emptyList()
        val close = zavorka(text, open, '[', ']') ?: return emptyList()
        return objekty(text.substring(open + 1, close)).mapNotNull { zaznam(it) }
    }

    /** Index odpovídající uzavírací závorky k otevírací na [open] (ignoruje závorky uvnitř řetězců). */
    private fun zavorka(text: String, open: Int, znak: Char, protizn: Char): Int? {
        var hloubka = 0
        var vRetezci = false
        var i = open
        while (i < text.length) {
            val c = text[i]
            if (vRetezci) {
                if (c == '\\') i++ else if (c == '"') vRetezci = false
            } else if (c == '"') {
                vRetezci = true
            } else if (c == znak) {
                hloubka++
            } else if (c == protizn) {
                hloubka--
                if (hloubka == 0) return i
            }
            i++
        }
        return null
    }

    /** Rozdělí obsah pole na jednotlivé `{…}` objekty nejvyšší úrovně. */
    private fun objekty(uvnitr: String): List<String> {
        val out = mutableListOf<String>()
        var i = uvnitr.indexOf('{')
        while (i >= 0) {
            val konec = zavorka(uvnitr, i, '{', '}') ?: break
            out.add(uvnitr.substring(i, konec + 1))
            i = uvnitr.indexOf('{', konec + 1)
        }
        return out
    }

    private fun zaznam(o: String): Zaznam? {
        val vc = VC.find(o)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val verze = VERZE.find(o)?.groupValues?.get(1) ?: return null
        if (vc <= 0 || verze.isEmpty()) return null
        return Zaznam(vc, verze, pole(o, "cs"), pole(o, "en"))
    }

    private fun pole(o: String, klic: String): List<String> {
        val start = o.indexOf("\"" + klic + "\"").takeIf { it >= 0 } ?: return emptyList()
        val open = o.indexOf('[', start).takeIf { it >= 0 } ?: return emptyList()
        val close = zavorka(o, open, '[', ']') ?: return emptyList()
        return RETEZEC.findAll(o.substring(open + 1, close)).map { it.groupValues[1] }.take(20).toList()
    }

    /** Body od verze [odVc] (bez ní, bývalá) po [doVc] (včetně, nabízenou), nejnovější nahoře. */
    fun mezi(zaznamy: List<Zaznam>, odVc: Int, doVc: Int): List<Zaznam> =
        zaznamy.filter { it.vc > odVc && it.vc <= doVc }.sortedByDescending { it.vc }
}
