package cz.recaption.core

/**
 * Šablona průvodce nastavením – JEDNO místo pravdy pro webového průvodce (`pomoc/pruvodce.html`) i průvodce v aplikaci
 * (přání majitele 24. 11. 2026: web a aplikace se rozjížděly). Celý průvodce je tu jako data:
 *  - hlavní kroky ([KROKY]) a jejich podkroky v pevném pořadí,
 *  - texty (zatím čeština + angličtina – web jiné jazyky nemá; ostatní jazyky aplikace padají na angličtinu, viz [T]),
 *  - kde se co dělá ([Podkrok.kde]: 📱 telefon / 📺 TV / 🎮 konzole / 🌐 web),
 *  - klipy ([Klip] → id `krok-<krok>[-<podkrok>][-<tv>][-<konzole>]`, výběr jako [VideoSteps]),
 *  - podmínky ([Kdy]): pro jakou TV (atv / lg / samsung), konzoli (ps5 / xbox), čím se nastavuje (na: telefon / ovladac / jine),
 *    jak se ReCaption dostane do Android TV (cesta: telefon / downloader / usb), DeepL (dl: ano / ne) a na čem průvodce běží
 *    (beh: web / tv / telefon). Co platí jen někde, má [Kdy] výslovně; bez [Kdy] = všude.
 *
 * Web: [webJs] vyrobí `pomoc/pruvodce-data.js` (commitnutý); `pruvodce.html` jen vykreslí, co dostane. Test `PruvodceTest`
 * hlídá, že soubor odpovídá šabloně a že každý klip z `pomoc/videa/kroky.json` patří k podkroku šablony.
 * Přegenerovat: `gradlew -p ci/mistni-kontrola test -Ppruvodce` (nebo proměnná prostředí PRUVODCE_ZAPSAT=1 při testu).
 *
 * Texty mohou obsahovat HTML (odkazy, tučné) – web ho vykreslí, aplikace ho bere přes [plain]. Zástupné znaky:
 * `{tv}` = jméno TV (Android TV / TV LG / TV Samsung), `{znacka}` = LG / Samsung.
 */
object Pruvodce {

    /**
     * Text v jazycích: [cs] a [en] vždy, ostatní jazyky aplikace v [jine] (kód [Lang.code]) nebo v [PruvodcePreklady]
     * (podle anglického znění – změna anglického textu = nový překlad, hlídá PruvodceTest). Chybí-li: slovenština bere
     * češtinu (jako web), ostatní angličtinu.
     */
    class T(val cs: String, val en: String, val jine: Map<String, String> = emptyMap()) {
        fun get(lang: String): String = when (lang) {
            "cs" -> cs; "en" -> en
            else -> jine[lang] ?: PruvodcePreklady.get(lang, en) ?: if (lang == "sk") cs else en
        }
    }

    /**
     * Podmínka: každé vyplněné pole musí sedět (uvnitř pole stačí jedna hodnota). [sKlipem] = jen když podkrok má klip.
     * `cesta` se na webu jmenuje `in`.
     */
    data class Kdy(
        val tv: Set<String>? = null, val na: Set<String>? = null, val ko: Set<String>? = null,
        val dl: Set<String>? = null, val cesta: Set<String>? = null, val beh: Set<String>? = null, val sKlipem: Boolean = false,
    ) {
        fun plati(s: Stav, maKlip: Boolean = false): Boolean =
            (tv == null || s.tv in tv) && (na == null || s.na in na) && (ko == null || s.ko in ko) && (dl == null || s.dl in dl) &&
                (cesta == null || s.cesta in cesta) && (beh == null || s.beh in beh) && (!sKlipem || maKlip)
    }

    /** Volby hráče. U LG / Samsungu je [na] vždy telefon (čte telefon) – viz [normal]. */
    data class Stav(val tv: String, val na: String, val ko: String, val dl: String, val cesta: String, val beh: String = "web") {
        fun normal(): Stav = if (tv != "atv") copy(na = "telefon") else this
    }

    /** Varianta textu: první, jejíž [kdy] platí. */
    class V(val kdy: Kdy?, val t: T)

    class Text(val varianty: List<V>) {
        fun pro(s: Stav, maKlip: Boolean = false): T? = varianty.firstOrNull { it.kdy?.plati(s, maKlip) ?: true }?.t
    }

    class Moznost(val v: String, val nazev: Text, val popis: Text, val vypnuto: Kdy? = null)

    /** Blok obsahu podkroku: p, pozn, dal (➜ věta, od které vede aplikace), ol, ul, qr (obrázek obrazky/qr-<text>.svg), otazka, volby. */
    class Blok(
        val typ: String, val text: Text? = null, val polozky: List<V> = emptyList(), val kdy: Kdy? = null,
        val id: String? = null, val klic: String? = null, val moznosti: List<Moznost> = emptyList(),
    )

    /** Klip podkroku: [pod] = id podkroku v id klipu (null = klip celého kroku). */
    class Klip(val pod: String?, val kdy: Kdy? = null)

    /**
     * Podkrok. [app] = obrazovka průvodce v aplikaci, kterou podkrok popisuje (MainActivity: langs, device, console, lang,
     * perm, done) – aplikace z něj vezme nadpis a body pod ním ([appNadpis], [appRadky]); ovládací prvky má vlastní.
     */
    class Podkrok(val id: String, val nazev: Text, val kde: Text?, val bloky: List<Blok>, val kdy: Kdy? = null, val klip: Klip? = null, val app: String? = null)

    /** Hlavní krok; [id] je i jméno kroku v id klipů. [kdy] = kdy se krok ukáže (jinak se přeskočí a nepočítá). */
    class Krok(val id: String, val nazev: T, val podkroky: List<Podkrok>, val kdy: Kdy? = null)

    // ---------------- pomocníci zápisu ----------------
    private fun set(s: String?) = s?.split(",")?.toSet()
    private fun k(tv: String? = null, na: String? = null, ko: String? = null, dl: String? = null, cesta: String? = null, klip: Boolean = false, beh: String? = null) =
        Kdy(set(tv), set(na), set(ko), set(dl), set(cesta), set(beh), klip)
    /** Jen na webu (aplikace to má jako vlastní ovládací prvek, nebo to v ní nedává smysl). */
    private val JEN_WEB = k(beh = "web")
    private fun tx(cs: String, en: String) = Text(listOf(V(null, T(cs, en))))
    private fun vs(vararg v: V) = Text(v.toList())
    private fun v(kdy: Kdy?, cs: String, en: String) = V(kdy, T(cs, en))
    private fun i(cs: String, en: String, kdy: Kdy? = null) = V(kdy, T(cs, en))
    private fun p(text: Text, kdy: Kdy? = null) = Blok("p", text, kdy = kdy)
    private fun p(cs: String, en: String, kdy: Kdy? = null) = p(tx(cs, en), kdy)
    private fun pozn(cs: String, en: String, kdy: Kdy? = null) = Blok("pozn", tx(cs, en), kdy = kdy)
    private fun pozn(text: Text, kdy: Kdy? = null) = Blok("pozn", text, kdy = kdy)
    private fun dal(cs: String, en: String) = Blok("dal", tx(cs, en))
    private fun ol(vararg p: V, kdy: Kdy? = null) = Blok("ol", polozky = p.toList(), kdy = kdy)
    private fun ul(vararg p: V, kdy: Kdy? = null) = Blok("ul", polozky = p.toList(), kdy = kdy)
    private fun qr(jmeno: String) = Blok("qr", tx(jmeno, jmeno))
    private fun otazka(cs: String, en: String) = Blok("otazka", tx(cs, en))
    private fun volby(id: String, klic: String, vararg m: Moznost) = Blok("volby", id = id, klic = klic, moznosti = m.toList())
    private fun m(v: String, nazev: Text, popis: Text, vypnuto: Kdy? = null) = Moznost(v, nazev, popis, vypnuto)

    // podmínky, které se opakují
    private val LGS = k(tv = "lg,samsung")          // LG / Samsung: čte telefon s Androidem
    private val ATV = k(tv = "atv")
    private val KAMERA = k(na = "telefon")           // telefon s ReCaptionem (u LG / Samsungu vždy)
    private val WEBEM = k(na = "telefon,jine")       // DeepL klíč se vkládá na webu / v telefonu, ne ovladačem
    private val PS = k(ko = "ps5")
    private val XBOX = k(ko = "xbox")

    // kde se to dělá
    private val TEL = T("📱 telefon", "📱 phone")
    private val TV = T("📺 TV", "📺 TV")
    private val PS5 = T("🎮 PS5", "🎮 PS5")
    private val TEL_TV = T("📱 telefon + 📺 TV", "📱 phone + 📺 TV")
    private val WEB = T("📱 telefon / 💻 počítač", "📱 phone / 💻 computer")
    private fun kde(t: T) = Text(listOf(V(null, t)))
    /** Kde je aplikace: u LG / Samsungu telefon, u Android TV televize. */
    private val KDE_APP = Text(listOf(V(LGS, TEL), V(null, TV)))
    /** Kde se páruje PS5: telefon s aplikací, stránka TV v prohlížeči, nebo TV ovladačem. */
    private val APP_PAR = listOf(V(KAMERA, TEL), V(k(na = "jine"), T("📺 TV / 💻 stránka TV", "📺 TV / 💻 TV page")), V(null, TV))

    private const val APK = "<a class=\"stahnout\" href=\"https://recaption.pages.dev/stahnout\">⬇ ReCaption.apk</a>"
    private val DAL_TV = dal("Dál podle průvodce v TV.", "From here, follow the guide on the TV.")

    /** Společné texty stránky (záhlaví, postup, tlačítka). */
    val UI: Map<String, T> = linkedMapOf(
        "title" to T("Průvodce nastavením · ReCaption", "Setup guide · ReCaption"),
        "desc" to T("Průvodce nastavením ReCaptionu krok za krokem – Android TV, LG, Samsung, PlayStation 5, Xbox.",
            "Step-by-step setup guide for ReCaption – Android TV, LG, Samsung, PlayStation 5, Xbox."),
        "nav_jak" to T("Jak to funguje", "How it works"), "nav_pruvodce" to T("Průvodce", "Setup guide"), "nav_kafe" to T(" Podpořit", " Support"),
        "nav_stahnout" to T("Stáhnout", "Download"), "bok" to T("Kroky jako v aplikaci", "Steps as in the app"),
        "pata" to T("Aplikace není produktem společnosti Sony Interactive Entertainment ani Microsoftu.", "This app is not a product of Sony Interactive Entertainment or Microsoft."),
        "qr" to T("naskenovat telefonem", "scan with the phone"),
        "uvod" to T("Stejné kroky jako průvodce v aplikaci. U každého kroku je vidět, kde se dělá: 📱 telefon, 📺 TV, 🎮 konzole.",
            "The same steps as the guide in the app. Each step shows where it's done: 📱 phone, 📺 TV, 🎮 console."),
        "krok" to T("Krok ", "Step "), "z" to T(" z ", " of "), "podkrok" to T("podkrok ", "sub-step "), "hotovo" to T("Hotovo", "Done"),
        "zpet" to T("← Zpět", "← Back"), "dokoncit" to T("Dokončit ✓", "Finish ✓"), "na_hotovo" to T("Hotovo →", "Done →"), "dal" to T("Pokračovat →", "Continue →"),
        "prehled" to T("Co je potřeba", "What you need"), "prehled_krok" to T("Před začátkem", "Before you start"),
        "prehled_uvod" to T("Co si připravit – podle TV.", "What to get ready – by TV type."),
        "nepovinne" to T("nepovinné", "optional"), "zacit" to T("Začít →", "Start →"), "prehled_bok" to T("i", "i"),
        "tv_atv" to T("Android TV", "Android TV"), "tv_lg" to T("TV LG", "LG TV"), "tv_samsung" to T("TV Samsung", "Samsung TV"),
    )

    // ---------------- 0 · Začínáme (jen web: co se děje před spuštěním ReCaptionu) ----------------
    // Texty pro běžného hráče (přání majitele 24. 11. 2026): krátké body „co zmáčknout, co zavřít, co zkopírovat“,
    // nic o tom, jak to funguje uvnitř; co aplikace udělá sama, nanejvýš jednou větou. Netyká se ani nevyká (infinitiv).

    /** Instalace ReCaptionu do telefonu (APK z prohlížeče) – stejné kroky ve dvou podkrocích. */
    private val DO_TELEFONU_KROKY = ol(
        i("$APK – otevřít v telefonu a stáhnout.", "$APK – open on the phone and download."),
        i("Klepnout na stažený soubor (v oznámení, nebo Soubory → Stažené).", "Tap the downloaded file (in the notification, or Files → Downloads)."),
        i("Když se telefon zeptá: <b>Nastavení</b> → zapnout <b>Povolit z tohoto zdroje</b> → Zpět.", "If the phone asks: <b>Settings</b> → turn on <b>Allow from this source</b> → Back."),
        i("<b>Instalovat</b> → <b>Otevřít</b>.", "<b>Install</b> → <b>Open</b>."),
    )
    private val N_DO_TELEFONU = tx("ReCaption do telefonu", "ReCaption on the phone")
    private val PLAY_PROTECT = i("Když se ukáže <b>Play Protect</b>: <b>Další podrobnosti</b> → <b>Přesto nainstalovat</b>.",
        "If <b>Play Protect</b> appears: <b>More details</b> → <b>Install anyway</b>.")
    private val SESTAVENI = pozn("Položka chybí? Nastavení → Systém → Informace → 7× zmáčknout <b>Sestavení</b>, pak znovu.",
        "Option missing? Settings → System → About → press <b>Build</b> 7 times, then try again.")

    private val ZACINAME = Krok("instalace", T("Začínáme", "Getting started"), listOf(
        // všechny volby kroku 0 na jedné stránce – přání majitele
        Podkrok("volby", tx("TV, nastavení a konzole", "TV, setup and console"), null, listOf(
            otazka("Jaká TV?", "Which TV?"),
            volby("v-tv", "tv",
                m("atv", tx("📺 Android TV / Google TV", "📺 Android TV / Google TV"), tx("Sony, Philips, TCL, Chromecast, Shield…", "Sony, Philips, TCL, Chromecast, Shield…")),
                m("lg", tx("🟥 LG (webOS)", "🟥 LG (webOS)"), tx("potřebuje i telefon s Androidem", "also needs an Android phone")),
                m("samsung", tx("🟦 Samsung (Tizen)", "🟦 Samsung (Tizen)"), tx("potřebuje i telefon s Androidem", "also needs an Android phone"))),
            otazka("Čím nastavovat?", "What to set up with?"),
            volby("v-na", "na",
                m("telefon", vs(v(ATV, "📱 Pomocník v telefonu s Androidem", "📱 Helper on an Android phone"), v(null, "📱 Telefon s Androidem", "📱 Android phone")),
                    vs(v(ATV, "doporučeno – nic se neopisuje, číslo z PS5 načte kamera", "recommended – nothing to type, the camera reads the PS5 number"),
                        v(null, "telefon čte titulky pro TV a provede nastavením", "the phone reads the subtitles for the TV and guides the setup"))),
                m("ovladac", tx("📺 Jen ovladač TV", "📺 TV remote only"), tx("bez telefonu – jen přihlášení k PS5 jednou přes čtvereček", "no phone – only the PS5 sign-in once via a QR code"), vypnuto = LGS),
                m("jine", tx("🌐 Web v prohlížeči", "🌐 Web in a browser"), tx("telefon, iPhone nebo počítač – nic se neinstaluje", "a phone, iPhone or computer – nothing to install"), vypnuto = LGS)),
            pozn(vs(v(null, "Bez telefonu s Androidem jde zkušebně počítač: <a href=\"https://recaption.pages.dev/stahnout/pc\">ReCaption pro Windows</a>.",
                    "Without an Android phone, a computer works too (experimental): <a href=\"https://recaption.pages.dev/stahnout/pc\">ReCaption for Windows</a>.")), kdy = LGS),
            otazka("Jaká konzole?", "Which console?"),
            volby("v-ko", "ko",
                m("ps5", tx("🎮 PlayStation 5", "🎮 PlayStation 5"), tx("potřebuje druhý účet PlayStation (zdarma)", "needs a second PlayStation account (free)")),
                m("xbox", tx("🟩 Xbox Series X|S", "🟩 Xbox Series X|S"), tx("stačí účet, kterým se hraje", "the account you play with is enough"))),
            pozn("Víc konzolí nebo TV se přidá na konci.", "More consoles or TVs are added at the end."),
        )),
        // ReCaption do telefonu: LG / Samsung (telefon čte, vždy) a Android TV s pomocníkem v telefonu
        Podkrok("aplikace", N_DO_TELEFONU, kde(TEL), listOf(
            qr("stahnout"),
            DO_TELEFONU_KROKY,
            pozn("V telefonu se ukáže <b>Přihlášení pro TV</b> – to je správně.", "The phone shows <b>Sign-in for the TV</b> – that's right."),
        ), kdy = KAMERA, klip = Klip("mobil")),
        // Android TV: jak dostat ReCaption do TV – z telefonu jde vždy
        Podkrok("cesta", tx("Jak nahrát do TV?", "How to install on the TV?"), null, listOf(
            p("Stačí jednou – další verze se nahrají samy.", "Only once – later versions install by themselves."),
            volby("v-in", "in",
                m("telefon", tx("📱 Z telefonu", "📱 From the phone"), vs(v(KAMERA, "telefon ho nahraje přes Wi‑Fi", "the phone installs it over Wi‑Fi"),
                    v(null, "potřebuje telefon s Androidem – jen na nahrání", "needs an Android phone – just for installing"))),
                m("downloader", tx("📥 Downloader", "📥 Downloader"), tx("jen ovladačem TV", "just the TV remote")),
                m("usb", tx("💾 USB flash disk", "💾 USB flash drive"), tx("soubor z počítače na flash disk", "a file from a computer on a flash drive"))),
        ), kdy = ATV),
        // jen pro nahrání do TV: aplikace do telefonu i u toho, kdo nastavuje ovladačem nebo webem
        Podkrok("aplikace", N_DO_TELEFONU, kde(TEL), listOf(
            qr("stahnout"),
            DO_TELEFONU_KROKY,
            pozn("V telefonu se ukáže <b>Přihlášení pro TV</b> – to je správně.", "The phone shows <b>Sign-in for the TV</b> – that's right."),
        ), kdy = k(tv = "atv", cesta = "telefon", na = "ovladac,jine"), klip = Klip("mobil")),
        // skutečný postup v aplikaci: PhoneHelperActivity → tlačítko at_open → AtvInstallActivity (karta kroků, Nahrát do TV)
        Podkrok("telefon", tx("ReCaption do TV z telefonu", "ReCaption on the TV from the phone"), kde(TEL_TV), listOf(
            ol(i("V TV: Nastavení → Systém → Informace → 7× zmáčknout <b>Sestavení</b> (starší TV: Předvolby zařízení → Informace).",
                "On the TV: Settings → System → About → press <b>Build</b> 7 times (older TVs: Device Preferences → About)."),
                i("Zpět → <b>Pro vývojáře</b> → zapnout <b>Ladění USB</b> (Fire TV: Moje Fire TV → Možnosti pro vývojáře → <b>Ladění ADB</b>).",
                    "Back → <b>Developer options</b> → turn on <b>USB debugging</b> (Fire TV: My Fire TV → Developer Options → <b>ADB debugging</b>)."),
                i("V telefonu dole zmáčknout <b>Nahrát ReCaption do Android TV</b> → <b>Nahrát do TV</b>.", "On the phone, at the bottom, press <b>Install ReCaption on Android TV</b> → <b>Install on TV</b>."),
                i("V TV u <b>Povolit ladění?</b> zaškrtnout <b>Vždy povolit</b> → <b>OK</b>.", "On the TV, at <b>Allow debugging?</b>, tick <b>Always allow</b> → <b>OK</b>."),
                i("ReCaption se v TV spustí a telefon sám pokračuje nastavením.", "ReCaption starts on the TV and the phone continues with the setup by itself.")),
            pozn("Telefon napíše „Tato TV chce párovací kód“? V 0.1 zvolit <b>Downloader</b>.", "The phone says “This TV asks for a pairing code”? Choose <b>Downloader</b> in 0.1."),
            dal("Dál podle průvodce v telefonu.", "From here, follow the guide on the phone."),
        ), kdy = k(tv = "atv", cesta = "telefon"), klip = Klip("telefon")),
        // Downloader
        Podkrok("downloader", tx("Downloader do TV", "Downloader on the TV"), kde(TV), listOf(
            ol(i("Na ovladači <b>Domů</b> → <b>Google Play</b> (Google TV: Aplikace → Hledat).", "Press <b>Home</b> → <b>Google Play</b> (Google TV: Apps → Search)."),
                i("Najít <b>Downloader</b> od AFTVnews (oranžová ikona) → <b>Instalovat</b> → <b>Otevřít</b>.", "Find <b>Downloader</b> by AFTVnews (orange icon) → <b>Install</b> → <b>Open</b>."),
                i("Přístup k souborům → <b>Povolit</b>, novinky zavřít <b>OK</b>.", "Access to files → <b>Allow</b>, close the news with <b>OK</b>.")),
        ), kdy = k(tv = "atv", cesta = "downloader"), klip = Klip("downloader")),
        Podkrok("stahnout", tx("Stáhnout ReCaption", "Download ReCaption"), kde(TV), listOf(
            ol(i("V Downloaderu zvolit řádek s adresou.", "In Downloader select the address field."),
                i("Napsat <span class=\"kod\">8438934</span> → <b>Go</b>.", "Type <span class=\"kod\">8438934</span> → <b>Go</b>."),
                i("Počkat na stažení. Ukáže se stránka? Zvolit žluté <b>ReCaption.apk</b>.", "Wait for the download. A page appears? Select the yellow <b>ReCaption.apk</b>.")),
            pozn("Kód nejde? Napsat <b>recaption.pages.dev/tv</b>.", "Code not working? Type <b>recaption.pages.dev/tv</b>."),
        ), kdy = k(tv = "atv", cesta = "downloader")),
        Podkrok("povolit", tx("Povolit instalaci", "Allow installing"), kde(TV), listOf(
            ol(i("TV se zeptá → <b>Nastavení</b> → <b>Downloader</b> na <b>Povoleno</b> → Zpět.", "The TV asks → <b>Settings</b> → set <b>Downloader</b> to <b>Allowed</b> → Back."),
                i("Starší TV: Nastavení → Předvolby zařízení → Zabezpečení a omezení → <b>Neznámé zdroje</b> → zapnout Downloader.",
                    "Older TVs: Settings → Device Preferences → Security & restrictions → <b>Unknown sources</b> → turn on Downloader.")),
            SESTAVENI,
        ), kdy = k(tv = "atv", cesta = "downloader"), klip = Klip("povolit")),
        Podkrok("spustit", tx("Nainstalovat a spustit", "Install and start"), kde(TV), listOf(
            ol(i("<b>Instalovat</b> (neukáže se? Stáhnout v Downloaderu znovu).", "<b>Install</b> (doesn't appear? Download again in Downloader)."),
                PLAY_PROTECT,
                i("<b>Otevřít</b>.", "<b>Open</b>.")),
            DAL_TV,
        ), kdy = k(tv = "atv", cesta = "downloader")),
        // USB flash disk
        Podkrok("usb", tx("Nahrát ReCaption na flash disk", "Copy ReCaption to a flash drive"), kde(T("💻 počítač / 📱 telefon", "💻 computer / 📱 phone")), listOf(
            ol(i("$APK – stáhnout v počítači.", "$APK – download it on a computer."),
                i("Soubor <b>ReCaption.apk</b> zkopírovat na flash disk.", "Copy the <b>ReCaption.apk</b> file to a flash drive."),
                i("Flash disk zastrčit do TV.", "Plug the flash drive into the TV.")),
        ), kdy = k(tv = "atv", cesta = "usb"), klip = Klip("usb")),
        Podkrok("soubor", tx("Otevřít soubor v TV", "Open the file on the TV"), kde(TV), listOf(
            ol(i("V TV otevřít <b>Správce souborů</b> / <b>Files</b> (chybí? Z Google Play nainstalovat <b>File Commander</b>).", "On the TV open <b>File manager</b> / <b>Files</b> (missing? Install <b>File Commander</b> from Google Play)."),
                i("Otevřít flash disk → <b>ReCaption.apk</b>.", "Open the flash drive → <b>ReCaption.apk</b>.")),
        ), kdy = k(tv = "atv", cesta = "usb")),
        Podkrok("povolit", tx("Povolit instalaci", "Allow installing"), kde(TV), listOf(
            ol(i("TV se zeptá → <b>Nastavení</b> → správce souborů na <b>Povoleno</b> → Zpět.", "The TV asks → <b>Settings</b> → set the file manager to <b>Allowed</b> → Back."),
                i("Starší TV: Nastavení → Předvolby zařízení → Zabezpečení a omezení → <b>Neznámé zdroje</b> → zapnout správce souborů.",
                    "Older TVs: Settings → Device Preferences → Security & restrictions → <b>Unknown sources</b> → turn on the file manager.")),
            SESTAVENI,
        ), kdy = k(tv = "atv", cesta = "usb"), klip = Klip("povolit")),
        Podkrok("spustit", tx("Nainstalovat a spustit", "Install and start"), kde(TV), listOf(
            ol(i("<b>Instalovat</b>.", "<b>Install</b>."),
                PLAY_PROTECT,
                i("<b>Otevřít</b>. Flash disk jde vyndat.", "<b>Open</b>. The flash drive can be removed.")),
            DAL_TV,
        ), kdy = k(tv = "atv", cesta = "usb")),
        // LG / Samsung: pomocník se přepne na celou aplikaci (čtečka pro TV)
        Podkrok("ctecka", tx("Telefon pro {tv}", "Phone for the {tv}"), kde(TEL), listOf(
            ol(i("V telefonu v <b>Přihlášení pro TV</b> zmáčknout <b>Číst pro TV</b>.", "On the phone, in <b>Sign-in for the TV</b>, press <b>Read for the TV</b>."),
                i("Otevře se průvodce – dál podle něj.", "The guide opens – follow it from here.")),
        ), kdy = LGS),
    ), kdy = k().copy(beh = setOf("web")))

    // ---------------- 1 · Jazyky ----------------
    private val JAZYKY = Krok("jazyky", T("Jazyky", "Languages"), listOf(
        Podkrok("jazyky", tx("Jazyky", "Languages"), KDE_APP, listOf(
            ul(i("<b>Jazyk aplikace</b> – nabídky ReCaptionu.", "<b>App language</b> – ReCaption's menus."),
                i("<b>Překládat z</b> – jazyk titulků ve hře (obvykle angličtina).", "<b>Translate from</b> – the game's subtitle language (usually English)."),
                i("<b>Jazyk překladu</b> – do čeho překládat.", "<b>Translation language</b> – what to translate into.")),
        ), klip = Klip(null), app = "langs"),
        // Android TV: čím se nastavuje zbytek (na) – jen postup, volba je v 0.1
        Podkrok("nastaveni", tx("Jak nastavit", "How to set up"),
            vs(V(k(na = "ovladac"), TV), V(KAMERA, TEL_TV), v(null, "📺 TV + 🌐 prohlížeč", "📺 TV + 🌐 browser")), listOf(
            ol(i("Nechat <b>📺 Jen ovladač TV</b> → <b>Pokračovat</b>.", "Keep <b>📺 TV remote only</b> → <b>Continue</b>."), kdy = k(na = "ovladac", cesta = "downloader,usb")),
            ol(i("Zvolit <b>📱 Pomocník v telefonu s Androidem</b>.", "Choose <b>📱 Helper on an Android phone</b>."),
                i("Čtvereček v TV naskenovat telefonem.", "Scan the QR code on the TV with the phone."),
                i("Zvolit <b>V aplikaci ReCaption (Android)</b>.", "Choose <b>In the ReCaption app (Android)</b>."),
                i("Účet, PS5 a DeepL nastavit v telefonu.", "Set up the account, PS5 and DeepL on the phone."),
                i("V TV zmáčknout <b>Pokračovat na TV</b>.", "On the TV press <b>Continue on the TV</b>."),
                kdy = k(na = "telefon", cesta = "downloader,usb")),
            // nahráno z telefonu: telefon je s TV spojený sám (AtvInstallActivity → TV dostala klíč), nic se neskenuje
            ol(i("Telefon je s TV už spojený – v TV je předvybraný <b>📱 Pomocník v telefonu</b>.", "The phone is already connected to the TV – <b>📱 Helper on an Android phone</b> is preselected on the TV."),
                i("Kroky jdou v telefonu i v TV – pokračovat jde na kterémkoli z nich.", "The steps follow on the phone and the TV – continue on either one."),
                kdy = k(cesta = "telefon")),
            pozn("Raději ovladačem? V TV zvolit <b>📺 Jen ovladač TV</b> – telefon zůstane připojený a ukazuje stejný krok.", "Prefer the remote? On the TV choose <b>📺 TV remote only</b> – the phone stays connected and shows the same step.", kdy = k(na = "ovladac,jine", cesta = "telefon")),
            ol(i("Zvolit <b>🌐 Web v prohlížeči</b>.", "Choose <b>🌐 Web in a browser</b>."),
                i("Čtvereček v TV naskenovat telefonem nebo iPhonem – v počítači napsat adresu pod ním.", "Scan the QR code on the TV with a phone or iPhone – on a computer type the address below it."),
                i("Zvolit <b>Pokračovat v prohlížeči</b>.", "Choose <b>Continue in the browser</b>."),
                i("Účet, PS5 a DeepL nastavit na stránce.", "Set up the account, PS5 and DeepL on the page."),
                i("V TV zmáčknout <b>Pokračovat na TV</b>.", "On the TV press <b>Continue on the TV</b>."),
                kdy = k(na = "jine", cesta = "downloader,usb")),
        ), kdy = ATV),
    ))

    // ---------------- 2 · Zařízení a TV ----------------
    private val ZARIZENI = Krok("zarizeni", T("Zařízení a TV", "Device and TV"), listOf(
        Podkrok("zarizeni", tx("Zařízení", "Device"), KDE_APP, listOf(
            p(vs(v(LGS, "Zvolit <b>Telefon nebo tablet</b> → <b>Pokračovat</b>.", "Choose <b>Phone or tablet</b> → <b>Continue</b>."),
                v(null, "Zvolit <b>Android TV nebo box</b> → <b>Pokračovat</b>.", "Choose <b>Android TV or box</b> → <b>Continue</b>."))),
        ), app = "device"),
        // TV LG: účet, vývojářský režim, Key Server, nahrání z telefonu
        Podkrok("ucet", tx("Účet LG pro vývojáře", "LG developer account"), kde(WEB), listOf(
            ol(i("Otevřít <a href=\"https://webostv.developer.lge.com/\" target=\"_blank\" rel=\"noopener\">webostv.developer.lge.com</a> → <b>Sign In</b> → <b>Create Account</b>.",
                "Open <a href=\"https://webostv.developer.lge.com/\" target=\"_blank\" rel=\"noopener\">webostv.developer.lge.com</a> → <b>Sign In</b> → <b>Create Account</b>."),
                i("Vyplnit e-mail a heslo (zdarma) – poznamenat si je.", "Enter an e-mail and password (free) – note them down.")),
            pozn("Jeden účet LG = vývojářský režim na jedné TV.", "One LG account = Developer Mode on one TV."),
        ), kdy = k(tv = "lg")),
        Podkrok("dev", tx("Vývojářský režim v TV", "Developer Mode on the TV"), kde(TV), listOf(
            ol(i("V TV: <b>Domů</b> → <b>LG Content Store</b> (novější TV: <b>Apps</b>).", "On the TV: <b>Home</b> → <b>LG Content Store</b> (newer TVs: <b>Apps</b>)."),
                i("Najít <b>Developer Mode</b> → <b>Instalovat</b> → <b>Otevřít</b>.", "Find <b>Developer Mode</b> → <b>Install</b> → <b>Open</b>."),
                i("Přihlásit se účtem LG.", "Sign in with the LG account."),
                i("Zapnout <b>Dev Mode Status</b> → potvrdit restart.", "Turn on <b>Dev Mode Status</b> → confirm the restart.")),
        ), kdy = k(tv = "lg"), klip = Klip("dev")),
        Podkrok("key", tx("Key Server", "Key Server"), kde(TV), listOf(
            ol(i("Po restartu znovu otevřít <b>Developer Mode</b>.", "After the restart open <b>Developer Mode</b> again."),
                i("Zapnout <b>Key Server</b>.", "Turn on <b>Key Server</b>."),
                i("Obrazovku nechat otevřenou (je na ní <b>IP</b> a <b>Passphrase</b>).", "Keep the screen open (it shows the <b>IP</b> and <b>Passphrase</b>).")),
            pozn("Režim po čase vyprší – v Developer Mode zmáčknout <b>Extend</b>.", "The mode expires after a while – press <b>Extend</b> in Developer Mode."),
        ), kdy = k(tv = "lg"), klip = Klip("key")),
        Podkrok("nahrat", tx("Nahrát ReCaption do TV LG", "Install ReCaption on the LG TV"), kde(TEL_TV), listOf(
            ol(i("V telefonu v kroku Televize zvolit <b>LG</b> → <b>Nahrát ReCaption do TV LG</b>.", "On the phone, in the TV step, choose <b>LG</b> → <b>Install ReCaption on LG TV</b>."),
                i("IP a Passphrase načíst kamerou (📷) nebo opsat.", "Read the IP and Passphrase with the camera (📷) or type them in."),
                i("<b>Nahrát do TV</b>.", "<b>Install on TV</b>.")),
        ), kdy = k(tv = "lg"), klip = Klip(null)),
        // TV Samsung: vývojářský režim, Apps2Samsung
        Podkrok("dev", tx("Vývojářský režim v TV", "Developer mode on the TV"), kde(TV), listOf(
            ol(i("V TV: <b>Domů</b> → <b>Aplikace</b> → na ovladači zadat <b>1 2 3 4 5</b>.", "On the TV: <b>Home</b> → <b>Apps</b> → enter <b>1 2 3 4 5</b> on the remote."),
                i("<b>Developer mode</b> → <b>On</b>.", "<b>Developer mode</b> → <b>On</b>."),
                i("Do <b>Host PC IP</b> napsat adresu telefonu (ReCaption v telefonu ji ukazuje v řádku <b>Host PC IP</b>) → <b>OK</b>.",
                    "In <b>Host PC IP</b> enter the phone's address (ReCaption on the phone shows it in the <b>Host PC IP</b> row) → <b>OK</b>."),
                i("TV restartovat: podržet vypínač, dokud TV nezhasne a znovu nenaběhne.", "Restart the TV: hold the power button until the TV turns off and back on.")),
        ), kdy = k(tv = "samsung"), klip = Klip("dev")),
        Podkrok("apps", tx("Apps2Samsung do telefonu", "Apps2Samsung on the phone"), kde(TEL), listOf(
            qr("apps2samsung"),
            ol(i("<a class=\"stahnout\" href=\"https://github.com/Apps2Samsung/Apps2Samsung/releases/latest\" target=\"_blank\" rel=\"noopener\">⬇ Apps2Samsung</a> – stáhnout soubor <code>…-android.apk</code>.",
                "<a class=\"stahnout\" href=\"https://github.com/Apps2Samsung/Apps2Samsung/releases/latest\" target=\"_blank\" rel=\"noopener\">⬇ Apps2Samsung</a> – download the <code>…-android.apk</code> file."),
                i("Nainstalovat stejně jako ReCaption.", "Install it the same way as ReCaption.")),
        ), kdy = k(tv = "samsung"), klip = Klip("apps")),
        Podkrok("nahrat", tx("Nahrát ReCaption do TV Samsung", "Install ReCaption on the Samsung TV"), kde(TEL), listOf(
            ol(i("V telefonu v kroku Televize zvolit <b>Samsung</b> → <b>Nahrát ReCaption do TV Samsung</b>.", "On the phone, in the TV step, choose <b>Samsung</b> → <b>Install ReCaption on Samsung TV</b>."),
                i("Zmáčknout <b>Přidat ReCaption do Apps2Samsungu</b> → v Apps2Samsungu potvrdit.", "Press <b>Add ReCaption to Apps2Samsung</b> → confirm in Apps2Samsung.")),
        ), kdy = k(tv = "samsung"), klip = Klip(null)),
        Podkrok("instalace", tx("Nainstalovat v Apps2Samsungu", "Install in Apps2Samsung"), kde(TEL_TV), listOf(
            ol(i("V Apps2Samsungu vybrat TV → u ReCaptionu <b>Install</b>.", "In Apps2Samsung pick the TV → <b>Install</b> next to ReCaption."),
                i("Napoprvé se přihlásit účtem Samsung (zdarma).", "The first time, sign in with a Samsung account (free)."),
                i("V TV spustit ReCaption.", "Start ReCaption on the TV.")),
            pozn("Nová verze: v Apps2Samsungu <b>Update</b>.", "New version: <b>Update</b> in Apps2Samsung."),
        ), kdy = k(tv = "samsung")),
    ))

    // ---------------- 3 · Konzole ----------------
    private val KONZOLE = Krok("konzole", T("Konzole", "Console"), listOf(
        Podkrok("konzole", tx("Konzole", "Console"), KDE_APP, listOf(
            p(vs(v(XBOX, "Zvolit <b>Xbox</b> → <b>Pokračovat</b>.", "Choose <b>Xbox</b> → <b>Continue</b>."),
                v(null, "Zvolit <b>PlayStation 5</b> → <b>Pokračovat</b>.", "Choose <b>PlayStation 5</b> → <b>Continue</b>."))),
        ), klip = Klip(null, PS), app = "console"),
        Podkrok("psn", tx("🔵 Účet pro ReCaption", "🔵 Account for ReCaption"), kde(WEB), listOf(
            Blok("qr", tx("psn-cs", "psn-en")),
            p("ReCaption potřebuje vlastní 🔵 účet. Na 🟢 svém účtu se hraje dál.", "ReCaption needs its own 🔵 account. You keep playing on your 🟢 account."),
            ol(i("<a class=\"stahnout\" href=\"https://www.playstation.com/cs-cz/support/account/create-account-for-psn/\" target=\"_blank\" rel=\"noopener\">Založit účet PlayStation</a>",
                "<a class=\"stahnout\" href=\"https://www.playstation.com/en-gb/support/account/create-account-for-psn/\" target=\"_blank\" rel=\"noopener\">Create a PlayStation account</a>"),
                i("Jiný e-mail než u 🟢 herního účtu → potvrdit v e-mailu.", "A different e-mail than the 🟢 gaming account → confirm it in the e-mail."),
                i("Datum narození dospělého.", "An adult's date of birth.")),
            pozn("PlayStation Plus není potřeba.", "No PlayStation Plus needed."),
        ), kdy = PS, klip = Klip("psn")),
        Podkrok("uzivatel", tx("🔵 Uživatel na PS5", "🔵 User on the PS5"), kde(PS5), listOf(
            ol(i("Na PS5 vpravo nahoře <b>ikona profilu</b> → <b>Přepnout uživatele</b>.", "On the PS5, the <b>profile icon</b> at the top right → <b>Switch User</b>."),
                i("<b>Přidat uživatele</b> → <b>Přidat uživatele do tohoto systému PS5</b> → <b>Začít</b>.", "<b>Add User</b> → <b>Add User to This PS5</b> → <b>Get Started</b>."),
                i("Přihlásit se 🔵 účtem (QR kódem nebo e-mailem).", "Sign in with the 🔵 account (QR code or e-mail)."),
                i("Zapamatovat si jméno 🔵 uživatele.", "Remember the 🔵 user's name.")),
        ), kdy = PS),
        Podkrok("rp", tx("Remote Play na PS5", "Remote Play on the PS5"), kde(PS5), listOf(
            ol(i("Na PS5 přepnout na <b>🔵 uživatele</b>.", "On the PS5 switch to the <b>🔵 user</b>."),
                i("Nastavení → Systém → Aplikace Remote Play → zapnout <b>Povolit aplikaci Remote Play</b>.", "Settings → System → Remote Play → turn on <b>Enable Remote Play</b>."),
                i("Tamtéž zapnout <b>Skrýt stav připojení aplikace Remote Play</b> – u 🔵 i u 🟢 uživatele.", "In the same place turn on <b>Hide Remote Play Connection Status Pop-Up</b> – for both the 🔵 and the 🟢 user.")),
            pozn("<b>Spárovat zařízení</b> zatím ne – až v kroku Připojení.", "Don't use <b>Pair Device</b> yet – that's in the Connecting step."),
        ), kdy = PS),
        Podkrok("xbox", tx("Xbox a účet Microsoft", "Xbox and Microsoft account"), kde(T("🎮 Xbox + 📱 telefon", "🎮 Xbox + 📱 phone")), listOf(
            ol(i("Na Xboxu: Nastavení → Zařízení a připojení → Vzdálené funkce → zapnout <b>Povolit vzdálené funkce</b>.", "On the Xbox: Settings → Devices & connections → Remote features → turn on <b>Enable remote features</b>."),
                i("Nastavení → Obecné → Režim napájení → <b>Spánek</b>.", "Settings → General → Power mode → <b>Sleep</b>."),
                i("V ReCaptionu zmáčknout <b>Přihlásit účet Microsoft</b> – ukáže se kód.", "In ReCaption press <b>Sign in with a Microsoft account</b> – a code appears."),
                i("V telefonu nebo počítači otevřít <a href=\"https://www.microsoft.com/link\" target=\"_blank\" rel=\"noopener\">microsoft.com/link</a> → zadat kód → přihlásit se účtem, kterým se hraje.",
                    "On a phone or computer open <a href=\"https://www.microsoft.com/link\" target=\"_blank\" rel=\"noopener\">microsoft.com/link</a> → enter the code → sign in with the account you play with."),
                i("Vybrat svůj Xbox.", "Pick your Xbox.")),
        ), kdy = XBOX, klip = Klip(null)),
    ))

    // ---------------- 4 · Překlad ----------------
    private val PREKLAD = Krok("preklad", T("Překlad", "Translation"), listOf(
        Podkrok("preklad", tx("Překlad", "Translation"), KDE_APP, listOf(
            ol(i("Zmáčknout <b>Stáhnout</b> (jazykový balíček, jen jednou).", "Press <b>Download</b> (the language pack, only once)."),
                i("<b>DeepL</b> – přesnější online překlad, zdarma s vlastním klíčem:", "<b>DeepL</b> – a more accurate online translation, free with your own key:", JEN_WEB)),
            volby("v-dl", "dl",
                m("ne", tx("Stačí offline překlad", "Offline translation is enough"), tx("zmáčknout <b>Přeskočit – zůstat u offline překladu</b>", "press <b>Skip – stay with offline translation</b>")),
                m("ano", tx("Chci i DeepL", "I want DeepL too"), tx("účet a klíč v dalších podkrocích", "account and key in the next sub-steps"))),
        ), klip = Klip(null), app = "lang"),
        Podkrok("ucet", tx("DeepL – účet", "DeepL – account"), kde(WEB), listOf(
            qr("deepl"),
            ol(i("<a class=\"stahnout\" href=\"https://www.deepl.com/pro#api\" target=\"_blank\" rel=\"noopener\">DeepL – záložka API</a> → u <b>Developer</b> zmáčknout <b>Sign up for free</b>.",
                "<a class=\"stahnout\" href=\"https://www.deepl.com/pro#api\" target=\"_blank\" rel=\"noopener\">DeepL – API tab</a> → on <b>Developer</b> press <b>Sign up for free</b>."),
                i("Vyplnit e-mail a heslo, plán nechat <b>Developer</b> → potvrdit e-mail.", "Enter an e-mail and password, keep the <b>Developer</b> plan → confirm the e-mail."),
                i("Už je účet DeepL? Přihlásit se a zmáčknout totéž tlačítko.", "Already have a DeepL account? Sign in and press the same button.")),
            pozn("Zdarma 1 000 000 znaků. Visí to na „Security check“? Vypnout VPN nebo blokování reklam.", "Free for 1,000,000 characters. Stuck on “Security check”? Turn off VPN or ad blocking."),
        ), kdy = k(dl = "ano"), klip = Klip("ucet")),
        Podkrok("klic", tx("DeepL – klíč", "DeepL – key"), Text(listOf(V(WEBEM, WEB), V(null, TEL_TV))), listOf(
            ol(i("Čtvereček v TV naskenovat telefonem.", "Scan the QR code on the TV with a phone.", k(na = "ovladac")),
                i("Otevřít <a href=\"https://www.deepl.com/your-account/keys\" target=\"_blank\" rel=\"noopener\">stránku s klíči</a>.", "Open <a href=\"https://www.deepl.com/your-account/keys\" target=\"_blank\" rel=\"noopener\">the keys page</a>."),
                i("U klíče zmáčknout <b>ikonu kopírování</b> (žádný není? <b>Create key</b>).", "Press the <b>copy icon</b> next to the key (none there? <b>Create key</b>)."),
                i("V ReCaptionu podržet prst v políčku → <b>Vložit</b>.", "In ReCaption long-press the field → <b>Paste</b>.", LGS),
                i("Na stránce ReCaption podržet prst v políčku → <b>Vložit</b> → <b>Odeslat do TV</b>.", "On the ReCaption page long-press the field → <b>Paste</b> → <b>Send to TV</b>.", ATV),
                i("Hotovo, když svítí <b>✔ DeepL je nastavený</b>.", "Done when <b>✔ DeepL is set up</b> shows.")),
        ), kdy = k(dl = "ano"), klip = Klip("klic")),
    ))

    // ---------------- 5 · Zobrazení titulků (jen Android TV – u LG / Samsungu kreslí pruh TV sama) ----------------
    private val ZOBRAZENI = Krok("zobrazeni", T("Zobrazení titulků", "Showing subtitles"), listOf(
        Podkrok("zobrazeni", tx("Zobrazení titulků", "Showing subtitles"), kde(TEL), listOf(
            p("Jen zmáčknout <b>Pokračovat</b>.", "Just press <b>Continue</b>."),
        ), kdy = LGS),
        Podkrok("povolit", tx("Povolit v Usnadnění", "Allow in Accessibility"), kde(TV), listOf(
            ol(i("Zmáčknout <b>Otevřít nastavení TV</b>.", "Press <b>Open TV settings</b>."),
                i("Dole zvolit <b>ReCaption</b> → zapnout → <b>OK</b>.", "At the bottom choose <b>ReCaption</b> → turn on → <b>OK</b>."),
                i("Zmáčknout <b>Zpět</b> – průvodce pokračuje sám.", "Press <b>Back</b> – the guide continues by itself.")),
            pozn("Titulky po aktualizaci nejdou? V Usnadnění ReCaption vypnout a zapnout.", "Subtitles not showing after an update? Turn ReCaption off and on in Accessibility."),
        ), kdy = ATV, klip = Klip(null), app = "perm"),
    ), kdy = ATV)

    // ---------------- 6 · Připojení (jen PlayStation – Xbox se připojil v kroku Konzole) ----------------
    private val PRIPOJENI = Krok("pripojeni", T("Připojení", "Connecting"), listOf(
        Podkrok("xbox", tx("Xbox připojen", "Xbox connected"), KDE_APP, listOf(
            p("Není co párovat – zmáčknout <b>Pokračovat</b>.", "Nothing to pair – press <b>Continue</b>."),
        ), kdy = XBOX),
        Podkrok("najit", tx("Najít PlayStation", "Find the PlayStation"), kde(PS5), listOf(
            ol(i("Zapnout PS5 – stejná Wi‑Fi nebo kabel do stejného routeru.", "Turn on the PS5 – same Wi‑Fi or a cable to the same router."),
                i("PS5 se najde sama. Nenajde? Na PS5: Nastavení → Síť → Stav připojení → <b>Adresa IPv4</b> → opsat nebo načíst kamerou 📷.",
                    "The PS5 is found by itself. Not found? On the PS5: Settings → Network → Connection Status → <b>IPv4 Address</b> → type it in or read it with the camera 📷.", KAMERA),
                i("PS5 se najde sama. Nenajde? Na PS5: Nastavení → Síť → Stav připojení → <b>Adresa IPv4</b> → opsat.",
                    "The PS5 is found by itself. Not found? On the PS5: Settings → Network → Connection Status → <b>IPv4 Address</b> → type it in.", k(na = "ovladac,jine"))),
            pozn("Video ukazuje celé připojení.", "The video shows the whole connection.", kdy = k(klip = true)),
        ), kdy = PS, klip = Klip(null)),
        Podkrok("ucet", tx("🔵 Účet pro ReCaption", "🔵 Account for ReCaption"), Text(APP_PAR), listOf(
            p("Zmáčknout <b>Použít účet 🔵 JMÉNO</b>, nebo <b>Přihlásit se účtem</b> – vždy 🔵 účtem, ne herním.", "Press <b>Use account 🔵 NAME</b>, or <b>Sign in with the account</b> – always the 🔵 account, not the gaming one."),
        ), kdy = PS, klip = Klip("ucet")),
        Podkrok("prihlaseni", tx("Přihlášení", "Sign-in"), Text(listOf(V(KAMERA, TEL), V(null, WEB))), listOf(
            ol(i("Zadat e-mail a heslo 🔵 účtu.", "Enter the 🔵 account's e-mail and password."),
                i("Hotovo, když svítí <b>Ověřený účet: 🔵 JMÉNO</b>.", "Done when <b>Verified account: 🔵 NAME</b> shows."),
                kdy = KAMERA),
            ol(i("Čtvereček v TV naskenovat telefonem (nebo adresu otevřít v počítači) → <b>Přihlásit se u PlayStation</b>.", "Scan the QR code on the TV with a phone (or open the address on a computer) → <b>Sign in to PlayStation</b>."),
                i("Zadat e-mail a heslo 🔵 účtu.", "Enter the 🔵 account's e-mail and password."),
                i("Na bílé stránce „redirect“ klepnout na adresu nahoře → <b>Kopírovat</b>.", "On the white “redirect” page tap the address at the top → <b>Copy</b>."),
                i("Zpět na kartu ReCaption → <b>Vložit</b>.", "Back to the ReCaption tab → <b>Paste</b>."),
                i("Hotovo, když svítí <b>Ověřený účet: 🔵 JMÉNO</b>.", "Done when <b>Verified account: 🔵 NAME</b> shows."),
                kdy = k(na = "ovladac,jine")),
            pozn("Svítí jméno 🟢 herního účtu? <b>Přihlásit jiný účet</b> a stránku otevřít v anonymním okně.", "Shows the 🟢 gaming account's name? <b>Sign in another account</b> and open the page in a private window.", kdy = k(na = "ovladac,jine")),
        ), kdy = PS, klip = Klip("prihlaseni")),
        Podkrok("rp", tx("Remote Play na PS5", "Remote Play on the PS5"), kde(PS5), listOf(
            ol(i("Na PS5 přepnout na <b>🔵 uživatele</b>.", "On the PS5 switch to the <b>🔵 user</b>."),
                i("Zkontrolovat, že je zapnuté <b>Povolit aplikaci Remote Play</b>.", "Check that <b>Enable Remote Play</b> is on.")),
        ), kdy = PS, klip = Klip("rp")),
        Podkrok("cislo", tx("Číslo z PS5", "Number from the PS5"), Text(APP_PAR.map { V(it.kdy, T("🎮 PS5 → " + it.t.cs, "🎮 PS5 → " + it.t.en)) }), listOf(
            ol(i("Na PS5 pod 🔵: Nastavení → Systém → Aplikace Remote Play → <b>Spárovat zařízení</b>.", "On the PS5 under 🔵: Settings → System → Remote Play → <b>Pair Device</b>."),
                i("Osmimístné číslo opsat nebo načíst kamerou 📷.", "Type in the eight-digit number or read it with the camera 📷.", KAMERA),
                i("Osmimístné číslo opsat.", "Type in the eight-digit number.", k(na = "ovladac,jine")),
                i("Hotovo, když svítí <b>Spárováno</b>.", "Done when <b>Paired</b> shows.")),
            pozn("Chyba <b>CE‑110032‑7</b>? Na PS5 přepnout na uživatele, kterého ukazuje ReCaption, a vyvolat nové číslo.",
                "Error <b>CE‑110032‑7</b>? Switch the PS5 to the user ReCaption shows and get a new number."),
        ), kdy = PS, klip = Klip("cislo")),
    ), kdy = PS)

    // ---------------- Hotovo ----------------
    private val HOTOVO = Krok("hotovo", T("Hotovo", "Done"), listOf(
        Podkrok("hrat", tx("Hrát", "Play"), vs(v(k(ko = "xbox", tv = "lg,samsung"), "🎮 Xbox + 📱 telefon + 📺 TV", "🎮 Xbox + 📱 phone + 📺 TV"), v(XBOX, "🎮 Xbox + 📺 TV", "🎮 Xbox + 📺 TV"),
            v(LGS, "🎮 PS5 + 📱 telefon + 📺 TV", "🎮 PS5 + 📱 phone + 📺 TV"), v(null, "🎮 PS5 + 📺 TV", "🎮 PS5 + 📺 TV")), listOf(
            ol(i("Na PS5 přepnout zpět na 🟢 <b>herní účet</b>.", "On the PS5 switch back to the 🟢 <b>gaming account</b>.", PS),
                i("V TV spustit <b>ReCaption</b> → vybrat HDMI s konzolí.", "On the TV start <b>ReCaption</b> → pick the HDMI input with the console.", LGS),
                i("V telefonu zmáčknout <b>▶</b> (Spustit).", "On the phone press <b>▶</b> (Start).", LGS),
                i("V ReCaptionu zmáčknout <b>▶</b> (Spustit) → <b>Zpět do hry</b>.", "In ReCaption press <b>▶</b> (Start) → <b>Back to game</b>.", ATV),
                i("Ve hře zapnout <b>anglické titulky</b> (GTA VI i pozadí: Settings → UI → Subtitles &amp; Captions → Background).", "Turn on <b>English subtitles</b> in the game (GTA VI also the background: Settings → UI → Subtitles &amp; Captions → Background).")),
            p(vs(v(LGS, "Vzhled titulků při hře: <b>OK</b> na ovladači TV.", "Subtitle look while playing: <b>OK</b> on the TV remote."),
                v(null, "Vzhled titulků: stránka <b>Titulky</b> v ReCaptionu.", "Subtitle look: the <b>Subtitles</b> page in ReCaption."))),
            pozn("Něco nejde? ⚙ Nastavení → Obecné → <b>Poslat vývojáři</b>.", "Something not working? ⚙ Settings → General → <b>Send to developer</b>."),
        ), klip = Klip("hrat"), app = "done"),
        Podkrok("telefon", tx("Telefon při hraní", "The phone while playing"), kde(TEL), listOf(
            ul(i("Telefon na nabíječku, stejná Wi‑Fi jako PS5 a TV.", "Phone on the charger, same Wi‑Fi as the PS5 and TV."),
                i("Zapnout ⚙ Nastavení → Obecné → <b>Nezhasínat displej telefonu</b> (se zhasnutým čte pomalu).", "Turn on ⚙ Settings → General → <b>Keep phone screen on</b> (with it off, reading is slow)."),
                i("Když se zeptá na běh na pozadí → <b>Povolit</b>.", "When asked about background use → <b>Allow</b>.")),
        ), kdy = LGS),
        Podkrok("vic", tx("Víc TV nebo konzolí", "More TVs or consoles"), KDE_APP, listOf(
            ul(i("<b>Další PS5</b>: Konzole → <b>Spárovat další PS5</b>.", "<b>Another PS5</b>: Console → <b>Pair another PS5</b>."),
                i("<b>PS5 i Xbox</b>: ⚙ Nastavení → Obecné → <b>Konzole</b>.", "<b>PS5 and Xbox</b>: ⚙ Settings → General → <b>Console</b>."),
                i("<b>Dvě TV LG / Samsung</b>: stačí jeden telefon.", "<b>Two LG / Samsung TVs</b>: one phone is enough."),
                i("<b>Dvě Android TV</b>: ReCaption do každé.", "<b>Two Android TVs</b>: ReCaption on each.")),
        )),
    ))

    // ---------------- Přehled „Co je potřeba“ (před krokem 0, přání majitele 24. 11. 2026) ----------------

    /** Položka přehledu; [nepovinne] = šedě s „nepovinné“. */
    class Polozka(val t: T, val nepovinne: Boolean = false)

    /** Řádek přehledu = jedno zařízení / věc; [bunky] po sloupcích [PREHLED_SLOUPCE] (prázdná = v tom sloupci není potřeba). */
    class Radek(val ikona: String, val nazev: T, val bunky: List<List<Polozka>>)

    /** Sloupce přehledu: Android TV a LG / Samsung (u nich čte telefon). */
    val PREHLED_SLOUPCE: List<T> = listOf(T("Android TV / Google TV", "Android TV / Google TV"), T("TV LG / Samsung", "LG / Samsung TV"))

    private fun po(cs: String, en: String, nepovinne: Boolean = false) = Polozka(T(cs, en), nepovinne)
    private val KONZOLE_POTREBA = listOf(
        po("<b>PlayStation 5</b>: 🟢 herní účet – hraje se na něm jako dřív", "<b>PlayStation 5</b>: 🟢 gaming account – you play on it as before"),
        po("+ 🔵 druhý účet jen pro ReCaption (zdarma) s povoleným Remote Play", "+ 🔵 a second account just for ReCaption (free) with Remote Play enabled"),
        po("<b>Xbox</b>: účet Microsoft, kterým se hraje (zapnuté vzdálené funkce)", "<b>Xbox</b>: the Microsoft account you play with (remote features on)"),
    )

    /** Co je potřeba – zařízení pod sebou, sloupce podle TV. */
    val PREHLED: List<Radek> = listOf(
        Radek("tv", T("Televize", "TV"), listOf(
            listOf(po("Aplikace <b>ReCaption</b> v TV – čte titulky a ukazuje překlad přes hru", "The <b>ReCaption</b> app on the TV – reads the subtitles and shows the translation over the game"),
                po("jednou povolit pruh v Usnadnění", "allow the bar once in Accessibility")),
            listOf(po("Aplikace <b>ReCaption</b> v TV – ukazuje překlad přes hru", "The <b>ReCaption</b> app on the TV – shows the translation over the game"),
                po("<b>LG</b>: vývojářský režim + účet LG pro vývojáře (zdarma)", "<b>LG</b>: Developer Mode + an LG developer account (free)"),
                po("<b>Samsung</b>: vývojářský režim + Apps2Samsung v telefonu", "<b>Samsung</b>: developer mode + Apps2Samsung on the phone")),
        )),
        Radek("konzole", T("Konzole", "Console"), listOf(KONZOLE_POTREBA, KONZOLE_POTREBA)),
        Radek("telefon", T("Telefon s Androidem", "Android phone"), listOf(
            listOf(po("pomocník při nastavení – nahraje ReCaption do TV, číslo z PS5 přečte kamerou; jde i jen ovladačem nebo na webu", "a setup helper – installs ReCaption on the TV, the camera reads the PS5 number; the remote or the web works too", nepovinne = true)),
            listOf(po("Aplikace <b>ReCaption</b> v telefonu – čte obraz hry, překládá a posílá titulky do TV", "The <b>ReCaption</b> app on the phone – reads the game picture, translates and sends the subtitles to the TV"),
                po("při hraní na nabíječce, Android 8 nebo novější", "on the charger while playing, Android 8 or newer")),
        )),
        Radek("sit", T("Domácí síť", "Home network"), listOf(
            listOf(po("konzole a TV ve stejné síti (stejná Wi‑Fi nebo kabel do stejného routeru)", "the console and the TV on the same network (same Wi‑Fi or a cable to the same router)")),
            listOf(po("konzole, TV a telefon ve stejné síti (stejná Wi‑Fi nebo kabel do stejného routeru)", "the console, the TV and the phone on the same network (same Wi‑Fi or a cable to the same router)")),
        )),
        Radek("deepl", T("Lepší překlad", "Better translation"), listOf(
            listOf(po("klíč DeepL (zdarma) – přirozenější online překlad; bez něj se překládá offline", "a DeepL key (free) – a more natural online translation; without it, translation is offline", nepovinne = true)),
            listOf(po("klíč DeepL (zdarma) – přirozenější online překlad; bez něj se překládá offline", "a DeepL key (free) – a more natural online translation; without it, translation is offline", nepovinne = true)),
        )),
    )

    /** Celý průvodce v pořadí: 0 Začínáme (jen web), 1–6 hlavní kroky jako v aplikaci, poslední Hotovo. */
    val KROKY: List<Krok> = listOf(ZACINAME, JAZYKY, ZARIZENI, KONZOLE, PREKLAD, ZOBRAZENI, PRIPOJENI, HOTOVO)

    /** Možné hodnoty voleb (pořadí = výchozí první). */
    val VOLBY: Map<String, List<String>> = linkedMapOf(
        "tv" to listOf("atv", "lg", "samsung"), "na" to listOf("telefon", "ovladac", "jine"), "ko" to listOf("ps5", "xbox"),
        "dl" to listOf("ne", "ano"), "in" to listOf("telefon", "downloader", "usb"),
    )

    /** Všechny kombinace voleb (po [Stav.normal], bez opakování) – pro testy. */
    fun stavy(beh: String = "web"): List<Stav> = VOLBY.getValue("tv").flatMap { tv -> VOLBY.getValue("na").flatMap { na -> VOLBY.getValue("ko").flatMap { ko ->
        VOLBY.getValue("dl").flatMap { dl -> VOLBY.getValue("in").map { c -> Stav(tv, na, ko, dl, c, beh).normal() } } } } }.distinct()

    /** Podkroky kroku [krok], které se pro volby [s] ukážou (v pořadí šablony). */
    fun podkroky(krok: Krok, s: Stav): List<Podkrok> = krok.podkroky.filter { it.kdy?.plati(s) ?: true }

    /** Kroky, které se pro [s] ukážou (Začínáme a Hotovo podle [Krok.kdy] jako ostatní). */
    fun kroky(s: Stav): List<Krok> = KROKY.filter { it.kdy?.plati(s) ?: true }

    /** Všechny texty šablony, které se překládají (bez jmen obrázků QR) – pro test překladů. */
    fun texty(): List<T> {
        val out = mutableListOf<T>()
        out += UI.values; out += PREHLED_SLOUPCE
        for (r in PREHLED) { out += r.nazev; r.bunky.forEach { c -> c.forEach { out += it.t } } }
        for (kr in KROKY) {
            out += kr.nazev
            for (pk in kr.podkroky) {
                pk.nazev.varianty.forEach { out += it.t }; pk.kde?.varianty?.forEach { out += it.t }
                for (b in pk.bloky) {
                    if (b.typ != "qr") b.text?.varianty?.forEach { out += it.t }
                    b.polozky.forEach { out += it.t }
                    b.moznosti.forEach { m -> m.nazev.varianty.forEach { out += it.t }; m.popis.varianty.forEach { out += it.t } }
                }
            }
        }
        return out
    }

    // ---------------- aplikace ----------------

    /** Hlavní krok podle id ([VideoSteps.STEPS] a „instalace“). */
    fun krok(id: String): Krok = KROKY.first { it.id == id }

    /** Volby hráče v aplikaci: [tv] atv / lg / samsung, [na] telefon / ovladac / jine, [ko] ps5 / xbox; [beh] tv / telefon. */
    fun appStav(tv: String, na: String, ko: String, deepl: Boolean, beh: String) = Stav(tv, na, ko, if (deepl) "ano" else "ne", "telefon", beh).normal()

    /** Název a popis volby [v] z bloku voleb [klic] (tv, na, ko, dl) – v aplikaci stejně jako na webu. */
    fun volba(klic: String, v: String, s: Stav, lang: String): Pair<String, String>? {
        for (kr in KROKY) for (pk in kr.podkroky) for (b in pk.bloky) if (b.klic == klic) {
            val m = b.moznosti.firstOrNull { it.v == v } ?: return null
            val n = m.nazev.pro(s) ?: return null
            return plain(n.get(lang), s, lang) to plain(m.popis.pro(s)?.get(lang) ?: "", s, lang)
        }
        return null
    }

    private fun obrazovka(app: String, s: Stav): List<Podkrok> = KROKY.flatMap { podkroky(it, s) }.filter { it.app == app }

    /** Nadpis obrazovky průvodce v aplikaci; null = obrazovka v šabloně není. */
    fun appNadpis(app: String, s: Stav, lang: String): String? = obrazovka(app, s).firstOrNull()?.nazev?.pro(s)?.let { plain(it.get(lang), s, lang) }

    /** Body pod nadpisem obrazovky v aplikaci: (text, druh p / li / pozn) – odstavce, body seznamů a poznámky, které pro [s] platí. */
    fun appRadky(app: String, s: Stav, lang: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for (pk in obrazovka(app, s)) for (b in pk.bloky) {
            if (b.kdy?.plati(s) == false) continue
            when (b.typ) {
                "p", "pozn" -> b.text?.pro(s)?.let { out += plain(it.get(lang), s, lang) to b.typ }
                "ol", "ul" -> b.polozky.filter { it.kdy?.plati(s) ?: true }.forEach { out += plain(it.t.get(lang), s, lang) to "li" }
            }
        }
        return out
    }

    private fun plain(t: String, s: Stav, lang: String) = plain(t).replace("{tv}", UI.getValue("tv_" + s.tv).get(lang)).replace("{znacka}", if (s.tv == "lg") "LG" else "Samsung")

    /** Text bez HTML (pro aplikaci). */
    fun plain(html: String): String = html.replace(Regex("<[^>]+>"), "").replace("&amp;", "&")

    // ---------------- web: pomoc/pruvodce-data.js ----------------

    /** Obsah `pomoc/pruvodce-data.js`: `window.PRUVODCE = {…};` (JSON, jen cs + en – web jiné jazyky nemá). */
    fun webJs(): String {
        val b = StringBuilder()
        b.append("// VYGENEROVÁNO z app/src/main/java/cz/recaption/core/Pruvodce.kt – needitovat ručně (test PruvodceTest hlídá soulad).\n")
        b.append("// Přegenerovat: gradlew -p ci/mistni-kontrola test -Ppruvodce\n")
        b.append("window.PRUVODCE = {\n\"ui\": {")
        UI.entries.forEachIndexed { n, (key, t) -> if (n > 0) b.append(','); b.append("\n ").append(q(key)).append(": ").append(jt(t)) }
        b.append("\n},\n\"volby\": {")
        VOLBY.entries.forEachIndexed { n, (key, l) -> if (n > 0) b.append(", "); b.append(q(key)).append(": [").append(l.joinToString(", ") { q(it) }).append(']') }
        b.append("},\n\"prehled\": {\"sloupce\": [").append(PREHLED_SLOUPCE.joinToString(", ") { jt(it) }).append("], \"radky\": [")
        PREHLED.forEachIndexed { n, r ->
            if (n > 0) b.append(',')
            b.append("\n {\"ikona\": ").append(q(r.ikona)).append(", \"n\": ").append(jt(r.nazev)).append(", \"bunky\": [")
            b.append(r.bunky.joinToString(", ") { c -> "[" + c.joinToString(", ") { "{\"cs\": ${q(it.t.cs)}, \"en\": ${q(it.t.en)}" + (if (it.nepovinne) ", \"nep\": true" else "") + "}" } + "]" }).append("]}")
        }
        b.append("]},\n\"kroky\": [")
        KROKY.forEachIndexed { n, kr ->
            if (n > 0) b.append(',')
            b.append("\n {\"id\": ").append(q(kr.id)).append(", \"n\": ").append(jt(kr.nazev))
            kr.kdy?.let { b.append(", \"kdy\": ").append(jk(it)) }
            b.append(", \"pod\": [")
            kr.podkroky.forEachIndexed { m, pk ->
                if (m > 0) b.append(',')
                b.append("\n  {\"id\": ").append(q(pk.id)).append(", \"n\": ").append(jtext(pk.nazev))
                pk.kde?.let { b.append(", \"kde\": ").append(jtext(it)) }
                pk.kdy?.let { b.append(", \"kdy\": ").append(jk(it)) }
                pk.app?.let { b.append(", \"app\": ").append(q(it)) }   // obrazovka v aplikaci (čte i studio videonávodů)
                pk.klip?.let { kl -> b.append(", \"klip\": {\"pod\": ").append(kl.pod?.let { q(it) } ?: "null"); kl.kdy?.let { b.append(", \"kdy\": ").append(jk(it)) }; b.append('}') }
                b.append(",\n   \"bloky\": [")
                pk.bloky.forEachIndexed { o, bl -> if (o > 0) b.append(','); b.append("\n    ").append(jblok(bl)) }
                b.append("]}")
            }
            b.append("]}")
        }
        b.append("\n]\n};\n")
        return b.toString()
    }

    private fun jblok(b: Blok): String {
        val s = StringBuilder("{\"t\": ").append(q(b.typ))
        b.kdy?.let { s.append(", \"kdy\": ").append(jk(it)) }
        b.text?.let { s.append(", \"x\": ").append(jtext(it)) }
        if (b.polozky.isNotEmpty()) s.append(", \"polozky\": [").append(b.polozky.joinToString(", ") { jv(it) }).append(']')
        b.id?.let { s.append(", \"id\": ").append(q(it)) }
        b.klic?.let { s.append(", \"klic\": ").append(q(it)) }
        if (b.moznosti.isNotEmpty()) s.append(", \"moznosti\": [").append(b.moznosti.joinToString(", ") { m ->
            "{\"v\": ${q(m.v)}, \"n\": ${jtext(m.nazev)}, \"d\": ${jtext(m.popis)}" + (m.vypnuto?.let { ", \"vypnuto\": ${jk(it)}" } ?: "") + "}"
        }).append(']')
        return s.append('}').toString()
    }

    private fun jt(t: T) = "{\"cs\": ${q(t.cs)}, \"en\": ${q(t.en)}}"
    private fun jv(v: V) = "{" + (v.kdy?.let { "\"kdy\": ${jk(it)}, " } ?: "") + "\"cs\": ${q(v.t.cs)}, \"en\": ${q(v.t.en)}}"
    private fun jtext(t: Text) = "[" + t.varianty.joinToString(", ") { jv(it) } + "]"
    private fun jk(k: Kdy): String {
        val p = mutableListOf<String>()
        fun f(n: String, s: Set<String>?) { if (s != null) p += "${q(n)}: [" + s.joinToString(", ") { q(it) } + "]" }
        f("tv", k.tv); f("na", k.na); f("ko", k.ko); f("dl", k.dl); f("in", k.cesta); f("beh", k.beh)
        if (k.sKlipem) p += "\"klip\": true"
        return "{" + p.joinToString(", ") + "}"
    }

    /** Řetězec JSON (UTF-8 beze změny, `</` rozdělené kvůli <script>). */
    private fun q(s: String): String {
        val b = StringBuilder("\"")
        for (c in s) when {
            c == '"' -> b.append("\\\"")
            c == '\\' -> b.append("\\\\")
            c == '\n' -> b.append("\\n")
            c < ' ' || c == ' ' || c == ' ' -> b.append(String.format("\\u%04x", c.code))
            else -> b.append(c)
        }
        return b.append('"').toString().replace("</", "<\\/")
    }
}
