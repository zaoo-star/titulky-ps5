package cz.recaption.core

/**
 * Stránky s pomocí krok za krokem (složka `pomoc` v repozitáři na GitHubu). Vedou na ně čtverečky
 * a tlačítka v aplikaci. Na nich je hlavní odkaz na registraci / návod LG, Sony, Samsungu… a pod
 * ním záložní odkazy a co hledat. Když výrobce stránku přesune, opraví se odkaz ve složce `pomoc`
 * a aplikace se měnit nemusí. Fungují v jakémkoli telefonu, i v iPhonu (jen webová stránka).
 */
object HelpLinks {
    /**
     * Stránky ze složky `pomoc` – od 1.00-rc2 na Cloudflare Pages (`recaption.pages.dev`, přání majitele: v odkazech pro hráče
     * nemá být GitHub; nahrává `.github/workflows/pomoc.yml`), do 1.00-rc1 GitHub Pages – obyčejný web bez přihlášení.
     * Dřív github.com/…/blob/…: telefon s aplikací GitHub ji otevřel v ní a ta chtěla přihlášení.
     */
    // Titulky PS5: vlastní nápověda (složka docs/ → GitHub Pages), ne stránky původní aplikace
    const val BASE = "https://zaoo-star.github.io/titulky-ps5/"

    val LG_ACCOUNT = page("lg", "ucet")
    val PS5_PREP = page("ps5", "ucet")
    val ANDROID_TV = page("android-tv")
    val PHONE = page("telefon")
    val SAMSUNG = page("samsung")
    /** Stažení Apps2Samsung (hlavní odkaz + F-Droid, Obtainium, stránka vydání). */
    val SAMSUNG_INSTALLER = page("samsung", "apps2samsung")
    /** Záloha bez telefonu s Androidem: Tizen Studio v počítači. */
    val SAMSUNG_PC = page("samsung", "pocitac")
    val XBOX = page("xbox")
    /**
     * Dobrovolná podpora vývoje (řádek dole na Domů a čtvereček v TV, od ReCaptionu 1.00): krátká adresa, která přesměruje
     * na stránku s příspěvkem (QR platba převodem) – cíl se mění jen v `pomoc/kafe.html`, bez nového APK.
     */
    val SUPPORT = BASE
    /** Stránka ReCaptionu na Facebooku (od 1.0.7, přání majitele) – Domů a stránka nastavení z TV. */
    const val FACEBOOK = BASE
    /**
     * iPhone: zkratka „ReCaption“ (Sdílet na stránce „redirect“ → adresa jde do TV bez kopírování). Stránka si z odkazu
     * `?tv=` zapamatuje stránku TV a s `?u=` na ni adresu „redirect“ přepošle (`pomoc/zkratka.md`, od 0.97.63).
     */
    val IPHONE_SHORTCUT = page("telefon")

    fun page(name: String, anchor: String? = null) = BASE + name + ".html" + (anchor?.let { "#$it" } ?: "")
}
