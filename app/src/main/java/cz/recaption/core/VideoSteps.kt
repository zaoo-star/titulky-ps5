package cz.recaption.core

/**
 * Krátká videa ke krokům průvodce (od větve nového průvodce, přání majitele): ke každému hlavnímu kroku klip
 * podle toho, co hráč zvolil – typ TV (Android TV / LG / Samsung) a konzole (PlayStation / Xbox). Klipy kreslí
 * video sezení (`ci/videonavody`) a zveřejňuje je na stránkách s pomocí (`pomoc/videa/<id>-<cs|en>.mp4` + `.jpg`)
 * se seznamem `pomoc/videa/kroky.json` – aplikace ukáže řádek „▶ Video“ jen u klipu, který v seznamu je, takže
 * nový klip nepotřebuje nové APK. Od větve videa-pruvodce i klipy k podkrokům (s návratem ke klipu kroku).
 * Zadání klipů: `navrhy/videa-po-krocich.md`.
 */
object VideoSteps {
    /**
     * Hlavní kroky průvodce v pořadí (MainActivity `wizardGroups`): jazyky, zařízení a TV, konzole, překlad, zobrazení,
     * připojení; index 6 = Hotovo (v `wizardGroups` není). Webový průvodce má navíc krok `instalace` (jen klipy podkroků).
     */
    val STEPS = listOf("jazyky", "zarizeni", "konzole", "preklad", "zobrazeni", "pripojeni", "hotovo")

    /** Adresa seznamu klipů. */
    /**
     * Titulky PS5: videa původní aplikace (se jménem a vzhledem ReCaptionu) se nenabízejí – seznam z vlastního repa,
     * dokud tam není, řádky s videem se neukážou.
     */
    const val LIST_URL = "https://raw.githubusercontent.com/zaoo-star/titulky-ps5/main/videa/kroky.json"

    /**
     * Platné id klipu: `krok-<krok>[-<podkrok>][-<tv>][-<konzole>]` v tomto pořadí, jen malá písmena a pomlčky
     * (tv = android / lg / samsung, konzole = ps / xbox; podkrok nikdy není slovo TV ani konzole). Starší APK
     * (do rc25) brala nejvýš 3 části za `krok-` – delší id (podkrok + TV + konzole) tiše vynechají.
     */
    val ID = Regex("^krok-[a-z]+(-[a-z]+){0,3}$")

    /**
     * Kroky, jejichž klipy ukazují konzoli: id bez `-xbox` v nich ukazuje PlayStation, takže s Xboxem se berou
     * jen id končící `-xbox` (oprava: s Xboxem se v kroku Konzole ukazoval klip s PS5).
     */
    private val CONSOLE_STEPS = setOf("konzole", "pripojeni", "hotovo")

    /**
     * Kandidáti klipu pro hlavní krok [step] (0–6) a podkrok [sub] (nebo null), od nejpřesnějšího: nejdřív klipy
     * podkroku (krok-podkrok-TV-konzole, -TV, -konzole, krok-podkrok), pak klipy celého kroku (krok-TV-konzole,
     * krok-TV, krok-konzole, krok). [tv] = "android" / "lg" / "samsung", [console] = "ps" / "xbox".
     */
    fun candidates(step: Int, tv: String, console: String, sub: String? = null): List<String> {
        val s = STEPS.getOrNull(step) ?: return emptyList()
        return candidates(s, tv, console, sub)
    }

    /** Totéž podle jména kroku (např. `instalace`, `pripojeni`). */
    fun candidates(step: String, tv: String, console: String, sub: String? = null): List<String> {
        fun chain(base: String) = listOf("$base-$tv-$console", "$base-$tv", "$base-$console", base)
        val subs = sub?.takeIf { Regex("^[a-z]+$").matches(it) }?.let { chain("krok-$step-$it") } ?: emptyList()
        val all = (subs + chain("krok-$step")).distinct()
        return if (console == "xbox" && step in CONSOLE_STEPS) all.filter { it.endsWith("-xbox") } else all
    }

    /** První kandidát, který je v seznamu [available] (id → délka); null = k tomuto kroku video není. */
    fun pick(step: Int, tv: String, console: String, available: Map<String, String>, sub: String? = null): String? =
        candidates(step, tv, console, sub).firstOrNull { it in available }

    /**
     * Seznam klipů `{"format":1,"klipy":{"krok-jazyky":"0:25", …}}` → id → délka (m:ss). Přísně: jiný formát,
     * neplatné id nebo délka = vynechat; nesmysl / přes 64 kB = prázdný seznam (nikdy pád).
     */
    /**
     * Klipy, které jsou zatím jen česky (`"jen_cs": ["krok-…", …]` v `kroky.json`, mimo „klipy“ – starší aplikace ho
     * přeskočí): ukazují se česky pro všechny jazyky, dokud se nedotočí angličtina. Bez seznamu = prázdná množina.
     */
    fun parseCsOnly(json: String?): Set<String> {
        if (json == null || json.length > 65_536) return emptySet()
        val m = Regex(""""jen_cs"\s*:\s*\[([^\]]*)\]""").find(json) ?: return emptySet()
        return Regex(""""([^"]{1,60})"""").findAll(m.groupValues[1]).map { it.groupValues[1] }.filter { ID.matches(it) }.toSet()
    }

    fun parse(json: String?): Map<String, String> {
        if (json == null || json.length > 65_536) return emptyMap()
        // core se překládá i do PC aplikace (bez org.json) – jednoduché čtení: formát 1 a dvojice "id":"m:ss" v „klipy“
        if (!Regex(""""format"\s*:\s*1\b""").containsMatchIn(json)) return emptyMap()
        val start = json.indexOf("\"klipy\"").takeIf { it >= 0 } ?: return emptyMap()
        val open = json.indexOf('{', start).takeIf { it >= 0 } ?: return emptyMap()
        val close = json.indexOf('}', open).takeIf { it > open } ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        val dur = Regex("""^\d{1,2}:\d{2}$""")
        for (m in Regex(""""([^"]{1,60})"\s*:\s*"([^"]{0,10})"""").findAll(json.substring(open + 1, close))) {
            val (id, d) = m.destructured
            if (ID.matches(id) && (d.isEmpty() || dur.matches(d))) out[id] = d.ifEmpty { VideoGuide.NO_DURATION }
        }
        return out
    }
}
