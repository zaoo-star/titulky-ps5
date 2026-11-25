// Most mezi aplikací pro iPhone (Swift) a společnou logikou ReCaptionu: jednoduché funkce s řetězci,
// ať se ve Swiftu nemusí pracovat s Kotlin enumy a kolekcemi.
package cz.recaption.ios

import cz.recaption.core.I18n
import cz.recaption.core.Lang
import cz.recaption.core.TextLogic

object ReCaptionJadro {
    /** Text aplikace v jazyce [lang] („cs“, „sk“, „en“…); neznámý jazyk = angličtina, neznámý klíč = klíč. */
    fun text(lang: String, key: String): String = I18n.t(Lang.ui(lang) ?: Lang.EN, key)

    /** Jazyk aplikace podle jazyků zařízení (první, který ReCaption umí), jinak angličtina – jako na Androidu. */
    fun uiLang(preferred: List<String>): String =
        preferred.map { it.substringBefore('-').substringBefore('_').lowercase() }.firstNotNullOfOrNull { Lang.ui(it) }?.code ?: Lang.EN.code

    /** Jazyky aplikace: kód → název („cs“ → „Čeština“). */
    fun languages(): List<String> = Lang.values().map { it.code + "|" + it.label }

    /** Úprava přečteného řádku titulku (jako na Androidu po čtení z obrazu). */
    fun clean(line: String): String = TextLogic.clean(line)
}
