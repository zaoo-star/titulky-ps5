package cz.recaption.core

/**
 * Směr textu překladu (od přípravy hebrejštiny, 23. 11. 2026). Hebrejština (a případně další jazyky psané
 * zprava doleva) se v pruhu, úkolu, menu i telefonu kreslí zprava doleva; ostatní jazyky beze změny.
 * Rozhoduje obsah řádku (jako `TEXT_DIRECTION_ANY_RTL` v Androidu a `dir="rtl"` v aplikaci v TV):
 * řádek s aspoň jedním písmenem psaným zprava doleva je celý zprava doleva – i když začíná latinkou
 * („LUCIA: …“, „GTA …“). Anglický originál pod překladem žádné takové písmeno nemá, takže zůstane zleva.
 */
object Bidi {
    /** Písmo psané zprava doleva: hebrejština, arabština, syrština, thaana, NKo…, prezentační tvary. */
    fun isRtl(c: Char): Boolean =
        c in '֐'..'ࣿ' || c in 'יִ'..'﷿' || c in 'ﹰ'..'ﻼ'

    /** Má text aspoň jeden znak psaný zprava doleva? `null` / prázdný = ne. */
    fun hasRtl(s: CharSequence?): Boolean {
        if (s == null) return false
        for (i in 0 until s.length) if (isRtl(s[i])) return true
        return false
    }
}
