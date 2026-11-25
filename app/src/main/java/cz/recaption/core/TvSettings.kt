package cz.recaption.core

/**
 * Pár voleb vzhledu pruhu, které jdou změnit i ovladačem v televizi LG / Samsung (nabídka „Vzhled
 * titulků“ po stisku OK při hře, `app.js`). Všechno nastavení dál patří telefonu / boxu s Androidem:
 * televize jen ukáže, co jí Android pošle, a požádá o změnu; Android ji ověří, uloží (v profilu té
 * televize, viz TvProfile) a pošle nové hodnoty zpět.
 *
 * Zprávy (viz TvSender):
 *  - Android → televize `{"typ":"nastaveni","pruhlednost":90,"velikost":22,"podleHry":true,"poloha":"tesne","original":false,
 *    "okraj":1,"jmena":true,"velikostOrig":0,"barvaPisma":0,"sporicLuna":true,"sporicVideo":0}` (poslední dvě od 1.5.60, jen
 *    jednorázově spustí/nespustí zkušební volby proti spořiči v TV - ne kolečkem v televizi, staré TV je přeskočí)
 *    po připojení a po každé změně (z telefonu i z televize),
 *  - televize → Android `{"typ":"zmen","klic":"pruhlednost","hodnota":80}`.
 *
 * Bezpečnost: zprávy chodí jen od televize v domácí síti (TvSender jiné adresy odmítne) a měnit jdou
 * jen tyhle volby vzhledu a jen na hodnoty, které nabízí i telefon. Nic jiného (účty, klíče, párování,
 * jazyk) televize změnit nemůže.
 */
object TvSettings {
    /** Stejné hodnoty jako v Titulkách v telefonu (Prefs.OPACITIES, Prefs.TEXT_SIZES). */
    val OPACITIES = listOf(0, 20, 40, 60, 80, 90, 100)
    val TEXT_SIZES = listOf(12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48)
    /** Polohy, které jdou zvolit v televizi (vlastní poloha jen v telefonu). */
    val POSITIONS = linkedMapOf(
        "pres" to ScreenLayout.Position.OVER,
        "tesne" to ScreenLayout.Position.ABOVE_TEXT,
        "nad" to ScreenLayout.Position.ABOVE,
        "nahore" to ScreenLayout.Position.TOP,
    )

    sealed class Change {
        data class Opacity(val percent: Int) : Change()
        /** Velikost písma v sp; 0 = podle velikosti titulků ve hře. */
        data class TextSize(val sp: Int) : Change()
        data class Position(val position: ScreenLayout.Position) : Change()
        data class Original(val show: Boolean) : Change()
        /** Okraj pruhu nad a pod textem 0–3 (Prefs.BAR_PADS). */
        data class Pad(val pad: Int) : Change()
        /** Jména mluvčích barevně. */
        data class Names(val colored: Boolean) : Change()
        /** Velikost originálu v sp; 0 = automaticky (62 % překladu). */
        data class OrigSize(val sp: Int) : Change()
        /** Barva textu – index do Prefs.TEXT_COLORS (bílá, žlutá, zelená, modrá). */
        data class TextColor(val index: Int) : Change()
    }

    const val PADS = 4
    const val COLORS = 4

    fun message(opacity: Int, textSize: Int, matchSize: Boolean, position: ScreenLayout.Position, showOriginal: Boolean,
                pad: Int = 1, coloredNames: Boolean = true, origSp: Int = 0, colorIndex: Int = 0,
                sporicLuna: Boolean = true, sporicVideo: Int = 0): String {
        val pos = POSITIONS.entries.firstOrNull { it.value == position }?.key ?: "vlastni"
        return "{\"typ\":\"nastaveni\",\"pruhlednost\":$opacity,\"velikost\":$textSize,\"podleHry\":$matchSize," +
            "\"poloha\":\"$pos\",\"original\":$showOriginal,\"okraj\":${pad.coerceIn(0, PADS - 1)},\"jmena\":$coloredNames," +
            "\"velikostOrig\":$origSp,\"barvaPisma\":${colorIndex.coerceIn(0, COLORS - 1)}," +
            "\"sporicLuna\":$sporicLuna,\"sporicVideo\":${sporicVideo.coerceIn(0, 2)}}"
    }

    /** Žádost televize o změnu; null = není to žádost, neznámá volba nebo nepovolená hodnota. */
    fun parse(text: String): Change? {
        if (text.length > 256) return null
        val m = MiniJson.obj(text) ?: return null
        if (m["typ"] != "zmen") return null
        val v = m["hodnota"]
        return when (m["klic"]) {
            "pruhlednost" -> (v as? Long)?.toInt()?.takeIf { it in OPACITIES }?.let { Change.Opacity(it) }
            "velikost" -> (v as? Long)?.toInt()?.takeIf { it == 0 || it in TEXT_SIZES }?.let { Change.TextSize(it) }
            "poloha" -> POSITIONS[v as? String]?.let { Change.Position(it) }
            "original" -> (v as? Boolean)?.let { Change.Original(it) }
            "okraj" -> (v as? Long)?.toInt()?.takeIf { it in 0 until PADS }?.let { Change.Pad(it) }
            "jmena" -> (v as? Boolean)?.let { Change.Names(it) }
            "velikostOrig" -> (v as? Long)?.toInt()?.takeIf { it == 0 || it in TEXT_SIZES }?.let { Change.OrigSize(it) }
            "barvaPisma" -> (v as? Long)?.toInt()?.takeIf { it in 0 until COLORS }?.let { Change.TextColor(it) }
            else -> null
        }
    }
}
