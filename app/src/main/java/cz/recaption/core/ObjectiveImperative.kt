package cz.recaption.core

/**
 * Úkol ve hře je pokyn („Drive to the harbor.“, „Go to the marked location.“, „Follow Lena.“). Offline překladač
 * bez kontextu z něj dělá podstatné jméno („Cesta k přístavu“) – majitel 21. 11. 2026. Proto: úkol, který začíná
 * slovesem z [VERBS], jde překladači jako „Please drive to the harbor.“ – ten vrátí rozkaz („Prosím, jeďte k přístavu.“),
 * [finish] uřízne „Prosím,“ a druhou osobu množného čísla převede na jednotné („Jeď k přístavu.“ – v aplikaci se ve
 * hře tyká, viz [Natural]). Zatím čeština a slovenština (pro ně jsou pravidla); jiné jazyky beze změny.
 * DeepL rozkaz pozná sám, tohle je jen pro offline překlad. Testy `ObjectiveImperativeTest`.
 */
object ObjectiveImperative {
    /** Slovesa, kterými úkoly v GTA a podobných hrách začínají (rozkaz). */
    val VERBS = setOf(
        "go", "drive", "get", "follow", "take", "lose", "kill", "find", "pick", "reach", "meet", "return", "head",
        "escape", "enter", "park", "collect", "deliver", "chase", "steal", "destroy", "protect", "wait", "walk", "run",
        "fly", "board", "leave", "stop", "use", "talk", "approach", "investigate", "defend", "search", "climb", "swim",
        "bring", "eliminate", "evade", "hide", "photograph", "call", "answer", "buy", "sell", "rob", "grab", "hack",
        "break", "open", "shoot", "take down", "save", "rescue", "help", "keep", "stay", "catch", "visit", "check",
        "look", "pull", "push", "drop", "flee", "outrun", "ride", "sail", "land", "head", "cross", "dock", "tail",
    )

    private val LANGS = setOf(Lang.CS, Lang.SK)

    private fun firstWord(en: String) = en.trim().substringBefore(' ').trimEnd('.', ',', '!', ':').lowercase()

    /** Má se úkol překládat jako rozkaz? */
    fun applies(en: String, lang: Lang, english: Boolean = true): Boolean =
        english && lang in LANGS && firstWord(en) in VERBS

    /** Text pro překladač: „Please drive to the harbor.“ */
    fun prepare(en: String): String {
        val t = en.trim()
        return "Please " + t.replaceFirstChar { it.lowercaseChar() }.let { if (it.last().isLetter()) "$it." else it }
    }

    private val PLEASE = Regex("^\\s*(prosím(\\s+vás)?|prosíme|prosim)\\s*,?\\s*", RegexOption.IGNORE_CASE)

    /** Překlad s „Prosím,“ → rozkaz v jednotném čísle s velkým písmenem; null = „prosím“ v překladu není (přeložit bez něj). */
    fun finish(tr: String): String? {
        val m = PLEASE.find(tr) ?: return null
        var rest = tr.substring(m.range.last + 1).trim()
        if (rest.isEmpty()) return null
        val first = rest.substringBefore(' ')
        val sing = singular(first)
        rest = sing + rest.substring(first.length)
        return rest.replaceFirstChar { it.uppercaseChar() }
    }

    /** „jeďte“ → „jeď“, „následujte“ → „následuj“, „jděte“ → „jdi“, „seberte“ → „seber“; jinak beze změny. */
    fun singular(word: String): String {
        val w = word.lowercase()
        val keepCase = { s: String -> if (word.first().isUpperCase()) s.replaceFirstChar { it.uppercaseChar() } else s }
        if (!w.endsWith("te") || w.length < 4) return word
        val stem = w.dropLast(2)
        val out = when {
            w.endsWith("ěte") -> w.dropLast(3) + "i"                  // jděte → jdi, najděte → najdi, vezměte → vezmi
            stem.last() in "jťďň" -> stem                             // následujte, vraťte, jeďte, dostaňte
            stem.last() in "rvžlsšzčcbpmtdnk" && stem.length >= 2 && stem[stem.length - 2] !in "aeiouyáéíóúůý" -> stem  // seberte, zastavte, běžte
            stem.last() in "rvžlsšzčcbpm" -> stem                     // zapalte → zapal, položte → polož
            else -> return word
        }
        return keepCase(out)
    }

    // ---- jméno postavy v úkolu (od 0.97.115, dávka E7PU-UU8C: „Follow Bria“ / „Follow Brin“ / „Follow Brita“ → „Následuj Bria“) ----

    private fun lev(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val t = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = t
            }
        }
        return d[b.length]
    }

    /**
     * Useknuté nebo špatně přečtené jméno postavy v úkolu → celé jméno: slovo s velkým písmenem (ne první – to je sloveso),
     * které začíná stejnými dvěma písmeny jako jméno a je jeho začátkem (aspoň 3 písmena), nebo se liší nejvýš o 1 písmeno
     * (o 2 u slov od 5 písmen). Víc možných jmen = nic.
     */
    fun fixNames(en: String, names: Collection<String>): String {
        val single = names.filter { it.isNotBlank() && ' ' !in it && it.length >= 3 }
        if (single.isEmpty()) return en
        val words = en.split(' ')
        val out = words.mapIndexed { i, w ->
            if (i == 0) return@mapIndexed w
            val core = w.trimEnd('.', ',', '!', ':', ';')
            val tail = w.substring(core.length)
            if (core.length < 3 || !core.first().isUpperCase() || !core.all { it.isLetter() } || single.any { it == core }) return@mapIndexed w
            val c = core.lowercase()
            val hits = single.filter { n ->
                val l = n.lowercase()
                l.take(2) == c.take(2) && (l.startsWith(c) || lev(l, c) <= (if (c.length >= 5) 2 else 1))
            }.distinct()
            if (hits.size == 1) hits[0] + tail else w
        }
        return out.joinToString(" ")
    }

    /** Slovesa úkolu, po kterých je jméno ve 4. pádě („Follow Brian“ → „Následuj Briana“). */
    private val ACC = setOf("follow", "find", "chase", "protect", "defend", "rescue", "save", "catch", "kill", "tail", "escort",
        "pick up", "wait for", "take down", "lose")

    /**
     * Jméno na konci úkolu po slovesu z [ACC] ve 4. pádě (cs/sk; překladač jméno ze slovníku nechá v 1. pádě):
     * mužské na souhlásku + „a“ (Viktor → Viktora), ženské na „-ia“ → „-ii“ / sk „-iu“ (Silvia → Silvii), na „-a“ → „-u“;
     * jiné beze změny. [female] = rod postavy (null = podle koncovky).
     */
    fun accusative(en: String, tr: String, lang: Lang, female: (String) -> Boolean?): String {
        if (lang !in LANGS) return tr
        val t = en.trim().trimEnd('.', '!')
        val low = t.lowercase()
        val verb = ACC.firstOrNull { low.startsWith("$it ") } ?: return tr
        val name = t.substring(verb.length).trim()
        if (name.isEmpty() || ' ' in name || !name.first().isUpperCase()) return tr
        val m = Regex("\\b" + Regex.escape(name) + "(\\.?)\\s*$").find(tr) ?: return tr
        val f = female(name) ?: name.endsWith("a")
        val l = name.lowercase()
        val acc = when {
            f && l.endsWith("ia") -> name.dropLast(1) + (if (lang == Lang.SK) "u" else "i")
            f && l.endsWith("a") -> name.dropLast(1) + "u"
            !f && l.last() !in "aeiouyáéíóúůý" -> name + "a"
            else -> return tr
        }
        return tr.substring(0, m.range.first) + acc + m.groupValues[1]
    }

    private val LEAVE_CS = Regex("^(Odejdi|Odejděte|Nech|Opusťte|Opusť|Odjeď|Vypadni)(\\s+(?:z|ze)\\b)?", RegexOption.IGNORE_CASE)
    private val LEAVE_SK = Regex("^(Odíď|Odíďte|Nechaj|Opusťte|Opusti|Odíďte)(\\s+(?:z|zo)\\b)?", RegexOption.IGNORE_CASE)

    /**
     * „Leave <místo>“ = opustit místo (od 0.97.138, dávky XTUZ-JPFR a KJ7U-2FHD): překladač dával „Odejdi Lake Moro“,
     * „Nech Tobyho přívěs“ → „Opusť …“ (sk „Opusti …“). Jen čeština a slovenština, jinak beze změny.
     */
    fun leave(en: String, tr: String, lang: Lang): String {
        if (!en.trim().startsWith("Leave ", ignoreCase = true)) return tr
        return when (lang) {
            Lang.CS -> LEAVE_CS.find(tr)?.let { "Opusť" + tr.substring(it.range.last + 1) } ?: tr
            Lang.SK -> LEAVE_SK.find(tr)?.let { "Opusti" + tr.substring(it.range.last + 1) } ?: tr
            else -> tr
        }
    }
}
