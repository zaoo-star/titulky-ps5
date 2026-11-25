package cz.recaption.core

/**
 * Webová obdoba aplikace v telefonu (od 0.97.67, přání majitele: „obdoba appky jako web, kde taky vše uvidím a nastavím“ –
 * pro iPhone a počítač, kde ReCaption v telefonu není). Na stránce z TV ([SetupPage]) je přehled stavu (stejné body jako Domů
 * a stav v TV, `TvStatus.rows`) a sekce „Titulky a hra“ s hlavními volbami. Méně častá nastavení zůstávají v TV / aplikaci.
 *
 * Zápis jde přes `op=web&klic=…&hodnota=…` ([OP]); měnit jde jen tyhle volby a jen na hodnoty, které nabízí i aplikace
 * (vzhled pruhu stejnými pravidly jako ovladač TV LG – [TvSettings]). Testy `WebSettingsTest`.
 */
object WebSettings {
    const val OP = "web"

    sealed class Change {
        data class Target(val lang: Lang) : Change()
        data class Source(val lang: SourceLang) : Change()
        data class Game(val id: String) : Change()
        data class Look(val change: TvSettings.Change) : Change()
        data class Prompts(val on: Boolean) : Change()
        data class Objective(val on: Boolean) : Change()
        data class Menu(val on: Boolean) : Change()
        data class Deepl(val on: Boolean) : Change()
    }

    /** Žádost ze stránky → změna; null = neznámá volba nebo nepovolená hodnota. [games] = id profilů her. */
    fun parse(klic: String?, hodnota: String?, games: Collection<String>): Change? {
        val v = hodnota?.trim() ?: return null
        if (v.length > 40) return null
        fun bool() = when (v) { "1", "true" -> true; "0", "false" -> false; else -> null }
        return when (klic) {
            "lang" -> Lang.targets.firstOrNull { it.code == v }?.let { Change.Target(it) }
            "src" -> SourceLang.values().firstOrNull { it.code == v }?.let { Change.Source(it) }
            "game" -> v.takeIf { it in games }?.let { Change.Game(it) }
            "pruhlednost", "velikost" -> v.takeIf { it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) }
                ?.let { TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"$klic\",\"hodnota\":$it}") }?.let { Change.Look(it) }
            "poloha" -> TvSettings.POSITIONS[v]?.let { Change.Look(TvSettings.Change.Position(it)) }
            "original" -> bool()?.let { Change.Look(TvSettings.Change.Original(it)) }
            "pokyny" -> bool()?.let { Change.Prompts(it) }
            "ukol" -> bool()?.let { Change.Objective(it) }
            "menu" -> bool()?.let { Change.Menu(it) }
            "deepl" -> bool()?.let { Change.Deepl(it) }
            else -> null
        }
    }

    /** Současné hodnoty pro stránku. [size] 0 = písmo podle hry. [rows] = přehled stavu (barva ok/pozor/info, text). */
    data class State(
        val lang: String, val src: String, val game: String,
        val opacity: Int, val size: Int, val position: String, val original: Boolean,
        val prompts: Boolean, val objective: Boolean, val menu: Boolean,
        val deeplKey: Boolean, val deeplOn: Boolean,
        val rows: List<Pair<String, String>>,
    )

    fun json(s: State): String {
        fun q(x: String) = RemoteSetup.json(x)
        return "{\"lang\":${q(s.lang)},\"src\":${q(s.src)},\"game\":${q(s.game)},\"pruhlednost\":${s.opacity}," +
            "\"velikost\":${s.size},\"poloha\":${q(s.position)},\"original\":${s.original},\"pokyny\":${s.prompts}," +
            "\"ukol\":${s.objective},\"menu\":${s.menu},\"deeplKey\":${s.deeplKey},\"deepl\":${s.deeplOn}," +
            "\"rows\":[" + s.rows.joinToString(",") { "[${q(it.first)},${q(it.second)}]" } + "]}"
    }

    /** Volby pro stránku: jazyky, hry (s příznakem pokynů a menu), velikosti, průhlednosti a polohy (s popisky). */
    data class Options(
        val langs: List<Pair<String, String>>,
        val sources: List<Pair<String, String>>,
        /** id, název, umí pokyny/úkol, umí překlad menu */
        val games: List<GameOption>,
        val positions: List<Pair<String, String>>,
        val sizeByGame: String,
    )

    data class GameOption(val id: String, val title: String, val prompts: Boolean, val menu: Boolean)

    fun optionsJson(o: Options): String {
        fun q(x: String) = RemoteSetup.json(x)
        fun pairs(l: List<Pair<String, String>>) = l.joinToString(",", "[", "]") { "[${q(it.first)},${q(it.second)}]" }
        return "{\"langs\":${pairs(o.langs)},\"srcs\":${pairs(o.sources)}," +
            "\"games\":" + o.games.joinToString(",", "[", "]") { "[${q(it.id)},${q(it.title)},${it.prompts},${it.menu}]" } +
            ",\"sizes\":" + (listOf(0) + TvSettings.TEXT_SIZES).joinToString(",", "[", "]") +
            ",\"opac\":" + TvSettings.OPACITIES.joinToString(",", "[", "]") +
            ",\"pos\":${pairs(o.positions)},\"sizeGame\":${q(o.sizeByGame)}}"
    }
}
