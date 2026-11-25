package cz.recaption.core

/**
 * Herní slova v překladu menu hry (od 0.97.111, přání majitele): „camera“ je v Nastavení GTA VI **kamera** (pohled hráče),
 * překladač z ní dělá „fotoaparát“. Po překladu popisu / názvu volby se tvary fotoaparátu nahradí kamerou (čeština,
 * slovenština; slova přicházejí se seznamem her – [GameDictionaries.menuTerms]). Jen pro menu Nastavení – v telefonu ve hře je fotoaparát opravdu fotoaparát. Testy `MenuTermsTest`.
 */
object MenuTerms {
    fun fix(text: String, lang: Lang): String {
        val rules = GameProfiles.dict.menuTermRx[lang.code] ?: return text
        var out = text
        for ((rx, to) in rules) {
            out = rx.replace(out) { m ->
                if (m.value.first().isUpperCase()) to.replaceFirstChar { it.uppercaseChar() } else to
            }
        }
        return out
    }
}
