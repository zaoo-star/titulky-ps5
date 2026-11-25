package cz.recaption.core

/**
 * Krátké repliky o jednom dvou slovech ("Wait", "Come on!"). Překladač je bez souvislosti
 * překládá špatně, proto mají pevný překlad – ten přichází se seznamem her ([GameDictionaries.quick],
 * soukromé repo, všechny jazyky z [Lang.withFixed]). Bez něj jde běžný překlad.
 */
object QuickWords {
    /** Běžné krátké repliky (anglicky) – podle nich se pozná replika od nápisu nebo šumu. */
    private val KNOWN: Set<String> = setOf(
        "wait", "run", "go", "yes", "no", "okay", "ok", "thanks", "thank you", "sorry", "what", "hello", "hi", "hey", "help",
        "stop", "look", "come on", "hurry", "hurry up", "please", "never", "maybe", "why", "where", "now", "here", "listen",
        "careful", "quiet", "move", "jump", "enough", "really", "sure", "right", "fine", "great", "good", "let's go",
        "watch out", "look out", "of course", "i know", "got it", "goodbye", "bye",
    )

    /** Je to běžná krátká replika ("wait", "come on")? Podle toho se pozná replika od nápisu nebo šumu. */
    fun isKnown(text: String): Boolean = text.trim().trimEnd('.', '!', '?', '…', ',').lowercase() in KNOWN

    /** Pevný překlad krátké repliky, nebo null. Interpunkce na konci ("Wait!") se zachová. */
    fun lookup(langCode: String, text: String): String? {
        val table = GameProfiles.dict.quick[langCode] ?: return null
        val t = text.trim()
        val core = t.trimEnd('.', '!', '?', '…', ',')
        if (core.isEmpty() || core.length > 12) return null
        val hit = table[core.lowercase()] ?: return null
        val raw = t.substring(core.length)
        // řečtina: otazník je „;“ („Why?“ → „Γιατί;“)
        val tail = when {
            langCode == "el" -> raw.replace('?', ';')
            // thajština tečku na konci věty nepíše (výpustka „...“ zůstává)
            langCode == "th" && ".." !in raw -> raw.replace(".", "")
            // hindština: místo tečky na konci věty „।“ (výpustka „...“ zůstává)
            langCode == "hi" && ".." !in raw -> raw.replace('.', '।')
            else -> raw
        }
        // španělština: obrácený otazník / vykřičník na začátku („Wait!“ → „¡Espera!“)
        val lead = if (langCode != "es") "" else if ('?' in tail) "¿" else if ('!' in tail) "¡" else ""
        return lead + hit + tail
    }
}
