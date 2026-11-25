@file:Suppress("PackageDirectoryMismatch", "UNUSED_PARAMETER")
package java.text

/**
 * Jen rozklad písmen s diakritikou na písmeno + značku (NFD) pro latinku, kterou ReCaption čte
 * (čeština, slovenština, polština, němčina, maďarština, francouzština, španělština, italština…).
 * Logika pak značky (\p{M}) odstraní – na přesném tvaru značky proto nezáleží.
 */
object Normalizer {
    enum class Form { NFD, NFC, NFKD, NFKC }

    private const val BASES =
        "ÀAÁAÂAÃAÄAÅAÇCÈEÉEÊEËEÌIÍIÎIÏIÑNÒOÓOÔOÕOÖOÙUÚUÛUÜUÝY" +
        "àaáaâaãaäaåaçcèeéeêeëeìiíiîiïiñnòoóoôoõoöoùuúuûuüuýyÿy" +
        "ĀAāaĂAăaĄAąaĆCćcĈCĉcĊCċcČCčcĎDďdĒEēeĔEĕeĖEėeĘEęeĚEěe" +
        "ĜGĝgĞGğgĠGġgĢGģgĤHĥhĨIĩiĪIīiĬIĭiĮIįiİIĴJĵjĶKķkĹLĺlĻLļlĽLľl" +
        "ŃNńnŅNņnŇNňnŌOōoŎOŏoŐOőoŔRŕrŖRŗrŘRřrŚSśsŜSŝsŞSşsŠSšsŢTţtŤTťt" +
        "ŨUũuŪUūuŬUŭuŮUůuŰUűuŲUųuŴWŵwŶYŷyŸYŹZźzŻZżzŽZžz" +
        "ȘSșsȚTțtǍAǎaǏIǐiǑOǒoǓUǔuẞSẽe"

    private val MAP: Map<Char, Char> = HashMap<Char, Char>().also { m ->
        var i = 0
        while (i + 1 < BASES.length) { m[BASES[i]] = BASES[i + 1]; i += 2 }
    }

    fun normalize(s: CharSequence, form: Form): String {
        if (form != Form.NFD && form != Form.NFKD) return s.toString()
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            val base = MAP[c]
            if (base != null) sb.append(base).append('́') else sb.append(c)
        }
        return sb.toString()
    }
}
