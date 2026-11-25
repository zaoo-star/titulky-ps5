package cz.recaption.core

/**
 * Seznam her jako data (třetí aktualizace vedle aplikace a aplikace v TV): zápis vestavěných profilů do JSON
 * (`pomoc/hry/profily.json` na stránkách s pomocí) a přísné čtení staženého seznamu zpět na [GameCatalog].
 *
 * Tvar (format 1):
 * ```
 * {"format":1,"verze":N,"minAppVerze":156,"bezneSlova":[…],
 *  "hry":[{"id":"gta6","nazev":"Grand Theft Auto VI",
 *          "jmena":[…],                       // nepřekládají se
 *          "bezne":[…],                       // z jmen ta, co jsou i běžná slova (univerzální profil je vynechá)
 *          "postavy":[{"jmeno":"Alex","rod":"m","prezdivky":["Al",…]}],
 *          "pojmy":{"cs":{"heist":"loupež"}},  // pevný překlad po jazycích
 *          "napisy":[…], "menu":[…],           // hlášky a nápisy menu (nečtou se)
 *          "preskocit":[{"vzor":"^…","velikost":false}],   // regulární výrazy řádků, které se nečtou
 *          "pokyny":true,                      // pokyny pod titulky (GTA)
 *          "barvy":{"Alex":"#FFFFD54F"},
 *          "menuHry":{"polozky":[…],"zalozky":[…],"preklady":{"cs":{…}},"podmenu":[…],"foto":[…],"fotoTlacitka":[…],
 *                   "rozlozeni":{"radek":0.0222,"radekPodmenu":0.0296,"radekFoto":…,"patickaVpravo":0.983}},   // od 1.00-rc21, nepovinné
 *          "roh":{"polozky":[…],"jmena":[…],"preklady":{"cs":{…}}},   // menu vpravo dole (nepovinné, starší aplikace ho přeskočí)
 *          "telefon":{"polohy":[{"nazev":"auto","misto":[0.6,0.2,0.8,0.9],"sklon":0.0}]},   // od 1.00-rc21, nepovinné
 *          "rada":"gp_bg_gta6"}],              // klíč textu rady v aplikaci (jen existující texty)
 *  "slovniky":{"obraty":[{"vzor","nahrada","velikost"}],"hlasky":{"cs":{…}},"kratka":{…},"zvuky":{…},   // od 0.97.149
 *              "menuSlova":{"cs":[["fotoaparát","kamera"],…]},"paticka":{…},"jmena":[…]}}
 * ```
 * Logika (rod, menu, úkol…) zůstává v kódu, seznam nese jen data a příznaky, které aplikace už umí.
 * Čtení nikdy nespadne: neplatná položka se vynechá, neplatný celek = null.
 */
object GameListJson {
    const val FORMAT = 1
    /** Největší seznam, který se přijme (znaků). */
    const val MAX_SIZE = 2_000_000
    private const val MAX_GAMES = 300
    private const val MAX_ITEMS = 5000
    private const val MAX_TEXT = 120
    /** Překlady menu (od 0.97.114 i celé popisy voleb Nastavení GTA VI); starší aplikace delší vynechají. */
    private const val MAX_WORD = 400
    private const val MAX_REGEX = 200
    private const val MAX_REGEXES = 20
    private val ID = Regex("^[a-z0-9_]{1,32}$")
    private val COLOR = Regex("^#[0-9A-Fa-f]{8}$")

    // ---------- zápis ----------

    /** Celý seznam jako JSON (jedna hra na víc řádků, ať jsou změny v gitu čitelné). */
    fun write(c: GameCatalog, minApp: Int = GameProfiles.MIN_APP): String {
        val b = StringBuilder()
        b.append("{\"format\":").append(FORMAT).append(",\"verze\":").append(c.version)
            .append(",\"minAppVerze\":").append(minApp).append(",\n")
        b.append("\"bezneSlova\":").append(arr(c.extraAmbiguous)).append(",\n")
        if (!c.dict.isEmpty) b.append("\"slovniky\":").append(dict(c.dict)).append(",\n")
        b.append("\"hry\":[\n")
        c.games.forEachIndexed { i, g ->
            if (i > 0) b.append(",\n")
            b.append(game(g))
        }
        b.append("\n]}\n")
        return b.toString()
    }

    private fun game(g: GameProfile): String {
        val f = ArrayList<String>()
        f += "\"id\":" + str(g.id)
        f += "\"nazev\":" + str(g.title)
        f += "\"jmena\":" + arr(g.names)
        if (g.common.isNotEmpty()) f += "\"bezne\":" + arr(g.common)
        if (g.characters.isNotEmpty()) f += "\"postavy\":[" + g.characters.joinToString(",") { ch ->
            "{\"jmeno\":" + str(ch.name) + ",\"rod\":" + str(if (ch.gender == Gender.F) "f" else "m") +
                (if (ch.aliases.isNotEmpty()) ",\"prezdivky\":" + arr(ch.aliases) else "") + "}"
        } + "]"
        if (g.terms.isNotEmpty()) f += "\"pojmy\":" + langMap(g.terms)
        if (g.hud.isNotEmpty()) f += "\"napisy\":" + arr(g.hud)
        if (g.menu.isNotEmpty()) f += "\"menu\":" + arr(g.menu)
        if (g.skip.isNotEmpty()) f += "\"preskocit\":[" + g.skip.joinToString(",") { r ->
            "{\"vzor\":" + str(r.pattern) + ",\"velikost\":" + (RegexOption.IGNORE_CASE !in r.options) + "}"
        } + "]"
        if (g.prompts) f += "\"pokyny\":true"
        if (g.colors.isNotEmpty()) f += "\"barvy\":{" + g.colors.entries.joinToString(",") { (k, v) ->
            str(k) + ":" + str("#" + String.format(java.util.Locale.ROOT, "%08X", v))
        } + "}"
        g.menuSpec?.let { m ->
            f += "\"menuHry\":{\"polozky\":" + arr(m.pause) + ",\"zalozky\":" + arr(m.tabs) +
                (if (m.sub.isNotEmpty()) ",\"podmenu\":" + arr(m.sub) else "") +
                (if (m.photoHeads.isNotEmpty()) ",\"foto\":" + arr(m.photoHeads) else "") +
                (if (m.photoButtons.isNotEmpty()) ",\"fotoTlacitka\":" + arr(m.photoButtons) else "") +
                (if (m.layout.isEmpty) "" else ",\"rozlozeni\":" + layout(m.layout)) +
                (if (m.words.isNotEmpty()) ",\"preklady\":" + langMap(m.words) else "") + "}"
        }
        // menu vpravo dole (nápovědy tlačítek); starší aplikace pole neznají a přeskočí ho
        g.cornerSpec?.let { c ->
            f += "\"roh\":{\"polozky\":" + arr(c.items) + (if (c.keep.isNotEmpty()) ",\"jmena\":" + arr(c.keep) else "") +
                (if (c.words.isNotEmpty()) ",\"preklady\":" + langMap(c.words) else "") + "}"
        }
        // přibližné polohy telefonu ve hře (od 1.00-rc21); starší aplikace pole přeskočí
        // a barvy bublin překladu telefonu (vlastní / příchozí, „#RRGGBB“)
        val phoneColors = listOf("vlastni" to g.phoneOwn, "prichozi" to g.phoneIn).filter { it.second != 0 }
        if (g.phoneSpots.isNotEmpty() || phoneColors.isNotEmpty()) f += "\"telefon\":{\"polohy\":[" + g.phoneSpots.joinToString(",") { sp ->
            "{\"nazev\":" + str(sp.name) + ",\"misto\":[" + sp.box.joinToString(",") { num(it) } + "]" +
                (if (sp.tilt != 0f) ",\"sklon\":" + num(sp.tilt) else "") + "}"
        } + "]" + (if (phoneColors.isEmpty()) "" else ",\"barvy\":" + phoneColors.joinToString(",", "{", "}") { (k, c) ->
            "\"" + k + "\":\"#" + String.format(java.util.Locale.ROOT, "%06X", c and 0xFFFFFF) + "\"" }) + "}"
        g.hint?.let { f += "\"rada\":" + str(it) }
        return "{" + f.joinToString(",\n ") + "}"
    }

    /** Rozložení menu hry (od 1.00-rc21): jen známé hodnoty, zlomky obrazu na 4 místa. */
    private fun layout(l: MenuLayout): String =
        listOf("radek" to l.pauseLine, "radekPodmenu" to l.subLine, "radekFoto" to l.photoLine, "patickaVpravo" to l.footerRight)
            .filter { it.second > 0f }
            .joinToString(",", "{", "}") { (k, v) -> "\"" + k + "\":" + String.format(java.util.Locale.ROOT, "%.4f", v) }

    private fun num(v: Float): String = String.format(java.util.Locale.ROOT, "%.4f", v)

    /** Polohy telefonu (od 1.00-rc21): obdélník 0–1 (vlevo < vpravo, nahoře < dole), sklon −0,4…0,4; nejvýš 8, neplatné se vynechají. */
    private fun spots(v: Any?): List<PhoneSpot> = ((v as? Map<*, *>)?.get("polohy") as? List<*>).orEmpty().take(8).mapNotNull { x ->
        val m = x as? Map<*, *> ?: return@mapNotNull null
        val b = (m["misto"] as? List<*>)?.takeIf { it.size == 4 }?.map { (it as? Number)?.toFloat() ?: return@mapNotNull null } ?: return@mapNotNull null
        if (b.any { it < 0f || it > 1f } || b[0] >= b[2] || b[1] >= b[3]) return@mapNotNull null
        val tilt = (m["sklon"] as? Number)?.toFloat() ?: 0f
        if (tilt < -0.4f || tilt > 0.4f) return@mapNotNull null
        PhoneSpot(text(m["nazev"], 30) ?: "", b.toFloatArray(), tilt)
    }

    /** Barva bubliny telefonu z „telefon.barvy.<klic>“ („#RRGGBB“), 0 = není / neplatná. */
    private fun phoneColor(v: Any?, key: String): Int =
        (((v as? Map<*, *>)?.get("barvy") as? Map<*, *>)?.get(key) as? String)?.takeIf { RGB.matches(it) }?.substring(1)?.toInt(16) ?: 0
    private val RGB = Regex("^#[0-9A-Fa-f]{6}$")

    /** Zlomek obrazu z rozložení menu (0 < x < 1), jinak 0. */
    private fun frac(v: Any?): Float = (v as? Number)?.toFloat()?.takeIf { it > 0f && it < 1f } ?: 0f

    /** Herní slovníky překladu (od 0.97.149): obraty, hlášky, krátké repliky, zvuky, slova menu, patička, jména. */
    private fun dict(d: GameDictionaries): String {
        val f = ArrayList<String>()
        if (d.idioms.isNotEmpty()) f += "\"obraty\":[\n  " + d.idioms.joinToString(",\n  ") { (r, to) ->
            "{\"vzor\":" + str(r.pattern) + ",\"nahrada\":" + str(to) + ",\"velikost\":" + (RegexOption.IGNORE_CASE !in r.options) + "}"
        } + "]"
        if (d.phrases.isNotEmpty()) f += "\"hlasky\":" + langMap(d.phrases)
        if (d.quick.isNotEmpty()) f += "\"kratka\":" + langMap(d.quick)
        if (d.captions.isNotEmpty()) f += "\"zvuky\":" + langMap(d.captions)
        if (d.menuTerms.isNotEmpty()) f += "\"menuSlova\":{" + d.menuTerms.entries.joinToString(",") { (lang, l) ->
            str(lang) + ":[" + l.joinToString(",") { (a, b) -> "[" + str(a) + "," + str(b) + "]" } + "]"
        } + "}"
        if (d.footer.isNotEmpty()) f += "\"paticka\":" + langMap(d.footer)
        if (d.names.isNotEmpty()) f += "\"jmena\":" + arr(d.names)
        return "{" + f.joinToString(",\n ") + "}"
    }

    private fun langMap(m: Map<String, Map<String, String>>): String = "{" + m.entries.joinToString(",") { (lang, words) ->
        str(lang) + ":{" + words.entries.joinToString(",") { (k, v) -> str(k) + ":" + str(v) } + "}"
    } + "}"

    private fun arr(l: Collection<String>): String = l.joinToString(",", "[", "]") { str(it) }

    private fun str(s: String): String {
        val b = StringBuilder("\"")
        for (c in s) when {
            c == '"' -> b.append("\\\"")
            c == '\\' -> b.append("\\\\")
            c == '\n' -> b.append("\\n")
            c == '\t' -> b.append("\\t")
            c == '\r' -> b.append("\\r")
            c < ' ' -> b.append(String.format("\\u%04x", c.code))
            else -> b.append(c)
        }
        return b.append('"').toString()
    }

    // ---------- čtení ----------

    /** Výsledek čtení: seznam, nebo důvod, proč ne (do záznamu, hráči se neukazuje). */
    class Result(val catalog: GameCatalog?, val problem: String?, val version: Int = 0)

    /** Jen verze z `verze.txt` (jedno číslo), nebo null. */
    fun versionOf(text: String): Int? = text.trim().takeIf { it.length <= 9 }?.toIntOrNull()?.takeIf { it > 0 }

    /**
     * Přečte stažený seznam. [appVersion] = versionCode aplikace (seznam pro novější aplikaci se nevezme),
     * [hasText] = existuje text aplikace s tímto klíčem (rada u hry); null = rady se neověřují.
     */
    fun parse(text: String, appVersion: Int, hasText: ((String) -> Boolean)? = { I18n.has(it) }): Result {
        if (text.length > MAX_SIZE) return Result(null, "size")
        val root = try { MiniJson.parse(text) as? Map<*, *> } catch (_: Throwable) { null } ?: return Result(null, "json")
        val format = (root["format"] as? Long)?.toInt() ?: return Result(null, "format")
        if (format != FORMAT) return Result(null, "format $format")
        val version = (root["verze"] as? Long)?.takeIf { it in 1..999_999_999 }?.toInt() ?: return Result(null, "verze")
        val minApp = (root["minAppVerze"] as? Long)?.toInt() ?: 0
        if (minApp > appVersion) return Result(null, "app $minApp", version)
        val list = root["hry"] as? List<*> ?: return Result(null, "hry", version)
        val games = ArrayList<GameProfile>()
        val ids = HashSet<String>()
        for (item in list.take(MAX_GAMES)) {
            val g = try { game(item as? Map<*, *> ?: continue, hasText) } catch (_: Throwable) { null } ?: continue
            if (ids.add(g.id)) games += g
        }
        if (games.isEmpty()) return Result(null, "prazdny", version)
        val extra = texts(root["bezneSlova"])
        val dict = try { dict(root["slovniky"] as? Map<*, *>) } catch (_: Throwable) { GameDictionaries.EMPTY }
        return try { Result(GameCatalog(games, extra, version, downloaded = true, dict = dict), null, version) }
        catch (_: Throwable) { Result(null, "katalog", version) }
    }

    private fun game(o: Map<*, *>, hasText: ((String) -> Boolean)?): GameProfile? {
        val id = (o["id"] as? String)?.takeIf { ID.matches(it) && it != "all" } ?: return null
        val title = text(o["nazev"]) ?: return null
        val names = texts(o["jmena"])
        val chars = (o["postavy"] as? List<*>).orEmpty().take(MAX_ITEMS).mapNotNull { c ->
            val m = c as? Map<*, *> ?: return@mapNotNull null
            val name = text(m["jmeno"]) ?: return@mapNotNull null
            val gender = when (m["rod"]) { "m" -> Gender.M; "f" -> Gender.F; else -> return@mapNotNull null }
            GameCharacter(name, gender, texts(m["prezdivky"]).take(30))
        }
        val skip = (o["preskocit"] as? List<*>).orEmpty().take(MAX_REGEXES).mapNotNull { r ->
            val m = r as? Map<*, *> ?: return@mapNotNull null
            val p = (m["vzor"] as? String)?.takeIf { it.isNotEmpty() && it.length <= MAX_REGEX } ?: return@mapNotNull null
            try { if (m["velikost"] == true) Regex(p) else Regex(p, RegexOption.IGNORE_CASE) } catch (_: Throwable) { null }
        }
        val colors = LinkedHashMap<String, Int>()
        (o["barvy"] as? Map<*, *>)?.entries?.take(100)?.forEach { (k, v) ->
            val name = text(k) ?: return@forEach
            val hex = (v as? String)?.takeIf { COLOR.matches(it) } ?: return@forEach
            colors[name] = hex.substring(1).toLong(16).toInt()
        }
        val menuSpec = (o["menuHry"] as? Map<*, *>)?.let { m ->
            val pause = texts(m["polozky"]).take(40)
            val tabs = texts(m["zalozky"]).take(40)
            if (pause.isEmpty() && tabs.isEmpty()) null else MenuSpec(pause, tabs, langMap(m["preklady"]),
                sub = texts(m["podmenu"]).take(40), photoHeads = texts(m["foto"]).take(40), photoButtons = texts(m["fotoTlacitka"]).take(40),
                layout = (m["rozlozeni"] as? Map<*, *>)?.let { r ->
                    MenuLayout(frac(r["radek"]), frac(r["radekPodmenu"]), frac(r["radekFoto"]), frac(r["patickaVpravo"]))
                } ?: MenuLayout())
        }
        val cornerSpec = (o["roh"] as? Map<*, *>)?.let { m ->
            val items = texts(m["polozky"]).take(60)
            if (items.isEmpty()) null else CornerSpec(items, langMap(m["preklady"]), texts(m["jmena"]).take(200))
        }
        val hint = (o["rada"] as? String)?.takeIf { h ->
            ID.matches(h) && (hasText == null || (hasText(h) && hasText(h + "_val") && hasText(h + "_desc")))
        }
        return GameProfile(id, title, names, chars, langMap(o["pojmy"]), texts(o["napisy"]), texts(o["menu"]), skip,
            prompts = o["pokyny"] == true, colors = colors, menuSpec = menuSpec, cornerSpec = cornerSpec, hint = hint,
            common = texts(o["bezne"]).filter { it in names }, phoneSpots = spots(o["telefon"]),
            phoneOwn = phoneColor(o["telefon"], "vlastni"), phoneIn = phoneColor(o["telefon"], "prichozi"))
    }

    /** Slovníky překladu; neplatná položka se vynechá, chybějící pole = prázdné. */
    private fun dict(o: Map<*, *>?): GameDictionaries {
        if (o == null) return GameDictionaries.EMPTY
        val idioms = (o["obraty"] as? List<*>).orEmpty().take(MAX_ITEMS).mapNotNull { r ->
            val m = r as? Map<*, *> ?: return@mapNotNull null
            val p = (m["vzor"] as? String)?.takeIf { it.isNotEmpty() && it.length <= MAX_WORD } ?: return@mapNotNull null
            val to = (m["nahrada"] as? String)?.takeIf { it.length <= MAX_WORD && it.none { c -> c < ' ' } } ?: return@mapNotNull null
            try { (if (m["velikost"] == true) Regex(p) else Regex(p, RegexOption.IGNORE_CASE)) to to } catch (_: Throwable) { null }
        }
        val menuTerms = LinkedHashMap<String, List<Pair<String, String>>>()
        (o["menuSlova"] as? Map<*, *>)?.forEach { (lang, l) ->
            if (lang !in LANGS) return@forEach
            val pairs = (l as? List<*>).orEmpty().take(MAX_ITEMS).mapNotNull { x ->
                val a = x as? List<*> ?: return@mapNotNull null
                val from = text(a.getOrNull(0))?.takeIf { w -> w.all { it.isLetter() } } ?: return@mapNotNull null
                text(a.getOrNull(1))?.let { from to it }
            }
            if (pairs.isNotEmpty()) menuTerms[lang as String] = pairs
        }
        return GameDictionaries(idioms, langMap(o["hlasky"]), langMap(o["kratka"]), langMap(o["zvuky"]), menuTerms,
            langMap(o["paticka"]), texts(o["jmena"]))
    }

    private val LANGS = Lang.values().map { it.code }.toSet()

    private fun langMap(v: Any?): Map<String, Map<String, String>> {
        val out = LinkedHashMap<String, Map<String, String>>()
        (v as? Map<*, *>)?.forEach { (lang, words) ->
            if (lang !in LANGS) return@forEach
            val m = LinkedHashMap<String, String>()
            (words as? Map<*, *>)?.entries?.take(MAX_ITEMS)?.forEach { (k, t) ->
                val en = text(k, MAX_WORD) ?: return@forEach
                m[en] = text(t, MAX_WORD) ?: return@forEach
            }
            if (m.isNotEmpty()) out[lang as String] = m
        }
        return out
    }

    private fun text(v: Any?, max: Int = MAX_TEXT): String? = (v as? String)?.takeIf { it.isNotBlank() && it.length <= max && it.none { c -> c < ' ' } }

    private fun texts(v: Any?): List<String> = (v as? List<*>).orEmpty().take(MAX_ITEMS).mapNotNull { text(it) }
}
