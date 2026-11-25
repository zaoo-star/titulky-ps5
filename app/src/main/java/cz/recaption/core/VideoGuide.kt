package cz.recaption.core

/**
 * Videonávody: krátké klipy ke krokům průvodce na stránkách s pomocí
 * (`pomoc/videa/<id>-<jazyk>.mp4` a náhled `.jpg`, https://recaption.pages.dev/videa/…; seznam klipů [VideoSteps]).
 * Přehrává je `VideoGuideActivity` (z průvodce a ze stránky Videonávody v menu). Celá videa (tv, android, iphone, lg,
 * samsung; od 0.97.71) jsou od 1.00-rc14 zrušená – přání majitele, po každé změně průvodce zastarala všechna naráz.
 * Jazyk videa: čeština pro českou a slovenskou aplikaci, jinak angličtina (video je bez řeči,
 * liší se jen texty v obraze). Klip zatím jen v češtině (seznam `jen_cs` v `kroky.json`, [VideoSteps.parseCsOnly])
 * se ukáže česky ve všech jazycích, dokud k němu není anglická verze (přání majitele 24. 11. 2026: angličtina se dotočí později).
 */
object VideoGuide {
    /** Složka s videi na stránkách s pomocí. */
    const val BASE = HelpLinks.BASE + "videa/"

    /** Délka videa, která ještě není známá. */
    const val NO_DURATION = "–:––"

    /** Jazyk videa podle jazyka aplikace: `cs` pro češtinu a slovenštinu, jinak `en`. */
    fun lang(ui: Lang, csOnly: Boolean = false): String = if (csOnly || ui == Lang.CS || ui == Lang.SK) "cs" else "en"

    /** Adresa videa (MP4, H.264 + AAC); [csOnly] = klip zatím jen česky (pro všechny jazyky). */
    fun video(id: String, ui: Lang, csOnly: Boolean = false): String = BASE + id + "-" + lang(ui, csOnly) + ".mp4"

    /** Adresa náhledu (obrázek, než se video rozběhne). */
    fun poster(id: String, ui: Lang, csOnly: Boolean = false): String = BASE + id + "-" + lang(ui, csOnly) + ".jpg"
}
