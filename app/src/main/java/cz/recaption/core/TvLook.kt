package cz.recaption.core

import java.util.Locale

/**
 * Vzhled a poloha pruhu pro televizi LG / Samsung – nepovinné pole `vzhled` zprávy `ukaz` (viz [TvSender]).
 *
 * Bez něj televize kreslila pruh pořád stejně (černý, dole přes celou šířku), ať měl hráč v Titulkách
 * nastavené cokoli. Televize ukazuje obraz hry přes celou obrazovku 1920×1080, takže místa na obrazovce
 * jdou jako zlomky obrazu (0..1) stejně jako u pruhu na Android TV ([ScreenLayout]). Kam pruh přesně
 * položit, dopočítá televize sama, až zná jeho výšku (`app.js`, `vzhledPruhu`) – stejnými pravidly
 * jako `SubtitleOverlay` (přes titulky / nad oblast čtení / pevné místo).
 *
 * Pole: `pozadi` průhlednost pozadí 0–100 (100 = černé), `pismo` velikost písma v px na obrazovce
 * 1080 řádků, `barva` #RRGGBB, `tucne`, `poloha` „pres“ / „tesne“ / „nad“ / „stred“ (+ `stred` = svislý střed
 * pruhu), `sirka` největší šířka pruhu, `cteni` [horní, dolní] hrana oblasti čtení, `text` [nahoře,
 * dole, vlevo, vpravo] anglického titulku (jen když se našel), `original` anglický text (jen se
 * zapnutým „Ukazovat i originál“), `kryt` = `text` je celé pole titulků (volba „Zakrýt celou oblast titulků“).
 */
object TvLook {
    /** Velikost písma z Nastavení (sp) × tohle = px v televizi; výchozích 22 dá 50 px jako dřív. */
    const val SP_TO_PX = 50f / 22f
    /** Výška obrazovky aplikace v televizi (pro „velikost podle originálu“). */
    const val SCREEN_H = 1080

    /** Písmo v televizi: [matchedPx] (podle originálu, 0 = nepoužít), jinak velikost z Nastavení. */
    fun textPx(textSizeSp: Int, matchedPx: Int): Int =
        (if (matchedPx > 0) matchedPx else Math.round(textSizeSp * SP_TO_PX)).coerceIn(16, 120)

    fun json(
        layout: ScreenLayout, opacityPercent: Int, textPx: Int, color: Int, bold: Boolean,
        textTop: Float, textBottom: Float, textLeft: Float, textRight: Float, original: String?,
        /** Okraj pruhu 0–3 (aplikace v TV od 1.5.4; starší ho přeskočí). */
        pad: Int = 1,
        /** Pevné místo (levý horní roh ve zlomcích obrazu, text zarovnaný vlevo) – překlad menu hry vedle menu (TV od 1.5.9). */
        box: Pair<Float, Float>? = null,
        /** Rozestup řádků (zlomek výšky) – řádky překladu proti položkám menu ve hře (TV od 1.5.10); 0 = běžně. */
        step: Float = 0f,
        /** Velikost originálu v % písma překladu (TV od 1.5.12; starší mají 62 %). */
        origPercent: Int = 62,
        /**
         * „Zakrýt celou oblast titulků“ (TV od 1.5.14): `text` je už celé pole titulků i s okrajem – pruh přesně tak vysoký
         * (bez dalšího okraje a stropu čtvrtiny obrazu). Starší aplikace v TV pole `kryt` přeskočí a kryjí `text` jako „pres“.
         */
        coverAll: Boolean = false,
    ): String {
        val sb = StringBuilder("{")
        sb.append("\"pozadi\":").append(opacityPercent.coerceIn(0, 100))
        sb.append(",\"pismo\":").append(textPx.coerceIn(16, 120))
        sb.append(",\"barva\":\"").append(SpeakerColors.hex(color)).append('"')
        if (bold) sb.append(",\"tucne\":true")
        sb.append(",\"okraj\":").append(pad.coerceIn(0, 3))
        // „tesne“ = těsně nad nalezený titulek; starší aplikace v televizi ho neznají a položí pruh jako „nad“
        val pos = when { box != null -> "misto"; layout.cover -> "pres"; layout.isAuto && layout.hug -> "tesne"; layout.isAuto -> "nad"; else -> "stred" }
        sb.append(",\"poloha\":\"").append(pos).append('"')
        if (box != null) sb.append(",\"misto\":[").append(f(box.first)).append(',').append(f(box.second))
            .append(if (step > 0f) "," + f(step) else "").append(']')
        if (pos == "pres" && coverAll && textTop >= 0f && textBottom > textTop) sb.append(",\"kryt\":true")
        if (pos == "stred") sb.append(",\"stred\":").append(f(layout.barCenterY))
        sb.append(",\"sirka\":").append(f(layout.barWidth))
        sb.append(",\"cteni\":[").append(f(layout.read.top)).append(',').append(f(layout.read.bottom)).append(']')
        if (textTop >= 0f && textBottom > textTop) {
            sb.append(",\"text\":[").append(f(textTop)).append(',').append(f(textBottom))
            if (textLeft >= 0f && textRight > textLeft) sb.append(',').append(f(textLeft)).append(',').append(f(textRight))
            sb.append(']')
        }
        original?.takeIf { it.isNotBlank() }?.let {
            sb.append(",\"original\":").append(TvSender.json(it))
            if (origPercent != 62) sb.append(",\"pismoOrig\":").append(origPercent.coerceIn(30, 100))
        }
        return sb.append('}').toString()
    }

    private fun f(v: Float) = String.format(Locale.ROOT, "%.4f", v.coerceIn(0f, 1f))
}
