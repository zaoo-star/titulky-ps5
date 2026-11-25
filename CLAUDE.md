# ReCaption – pokyny pro Claude Code

Aplikace pro Android TV. Po domácí síti se připojí k PlayStationu 5 (stejně jako Remote Play),
z kopie obrazu čte anglické titulky, offline je přeloží a ukáže v černém pruhu přes obraz.
Hra sama jde do televize kabelem HDMI. Návod pro uživatele je v `NAVOD.md`.

## Jak se tu píše

- Všechno česky: commity, komentáře v kódu, texty aplikace, návod.
- Commit popisuje, co se změnilo z pohledu hráče, ne jak je to naprogramované
  (např. „0.45: pokyny hry (Press to Start, Select…) nejsou repliky“).
- Uživateli odpovídej česky a bez programátorského žargonu.
- **Nezávislé úkoly souběžně (přání majitele, 20. 11. 2026):** když majitel napíše víc věcí, které na sobě nezávisí
  (jiné soubory, jiná část aplikace), pusť je paralelně přes subagenty (nástroj Agent, každý s jasným zadáním
  a seznamem souborů, na které smí sahat) a sám dělej jen jednu. Verzi, commit a vydání dělá jen hlavní sezení,
  až jsou všechny části hotové a otestované.

## Nové jméno ReCaption (od 1.00-rc14, 23. 11. 2026, přání majitele)

- Aplikace se do 1.00-rc13 jmenovala **PlayLingo** (jméno už bylo obsazené). Od rc14 je to **nová aplikace ReCaption**: jiné vnitřní
  označení (`cz.recaption`, v TV LG `cz.recaption.tv`, Samsung `ReCaption1`), stránky `recaption.pages.dev`, odkazy `recaption://`.
  Aktualizace z PlayLinga na ReCaption není a nebude – testeři si nainstalují ReCaption znovu a znovu spárují (majitel s tím počítá).
  PlayLingo 1.00-rc13 zůstává u testerů jako poslední verze; **`playlingo.pages.dev` (Cloudflare projekt `playlingo`) nechat běžet**.
- Píše se **ReCaption** (velké C, přání majitele); malými písmeny jen v adresách a vnitřních označeních. Rod: ReCaption je v češtině a slovenštině rodu mužského (ReCaption spároval, do ReCaptionu, s ReCaptionem; sk s ReCaptionom), PlayLingo bylo střední.
- Podpisový klíč je stejný jako u PlayLinga (otisk v `build.yml`), od 23. 11. 2026 s vnitřním jménem `recaption`. Se starým jménem zůstal záměrně jen archiv `PlayLingo-archiv`, zbytky „TestPreklad“.
- Server hlášení `recaption-hlaseni.recaption.workers.dev` přijímá i hlavičky `x-playlingo-…` starých aplikací (od 23. 11. 2026, data zkopírovaná ze starého úložiště).
- Klon s historií pod starým jménem: složka `PlayLingo-novy` na PC majitele (záloha).

## Tři repa (od 23. 11. 2026, rozdělení na dvě pak tři stejný den)

- **Veřejné `adusa89/ReCaption`** (toto repo) – aplikace pro Android, PC/Mac, TV LG/Samsung, stránky s pomocí (`pomoc/`, GitHub Pages
  i Cloudflare Pages), zkoušky a vydání. Začíná jedním commitem (stav 0.97.145 + `LICENSE`); adresy repa i stránek zůstaly stejné,
  takže aplikace u hráčů dál dostávají aktualizace a nápovědu. AGPL (chiaki-ng, PlayCaption) chce zdrojový kód toho, co se hráčům
  dává – to je tady.
- **Soukromé `adusa89/ReCaption-data`** – aktivní soukromá práce: **data her** (`hry/` – profily, překlady menu, testy se
  skutečnými texty her, viz Seznam her ke stažení), `server/` (schránka hlášení a replik na Cloudflare, anonymní počítadlo
  uživatelů, server předplatného), `navrhy/`, `ci/videonavody/` (kreslení videonávodů), `ci/repliky.py`, `ci/stahni-hlaseni.ps1` / `.sh`.
  Cloudové sezení si ho připojí přes `add_repo`.
- **Historie veřejného repa byla 23. 11. 2026 přepsána** (se souhlasem majitele): z commitů před 1.00-rc4 zmizela data her (profily, slova menu,
  herní slovníky, testy s texty her, `pomoc/hry`); obsah commitů od rc4 dál se nezměnil, změnila se jen jejich čísla. Starý klon ani větev ze
  staré historie NIKDY neslučovat ani neposílat (data by se vrátila) – klon zahodit, nebo `git fetch` + `git reset --hard origin/<větev>`.
  Testy se skutečnými texty her jsou jen v soukromém `ReCaption-data/hry` (veřejné testy mají vymyšlená jména a texty).
- **Soukromé `adusa89/PlayLingo-archiv`** – jen mrtvá záloha staré historie (vydání do 0.97.145/146). Nic se tam aktivně
  nepíše; krátce byla aktivní práce i tam (23. 11. 2026, než vzniklo `ReCaption-data`), to už neplatí.
- **Data her (jména, postavy, překlady menu, texty ze snímků her) se píšou jen do `ReCaption-data`**, nikdy sem – ani do testů.
- **Kód serveru (předplatné, schránka) se píše jen do `ReCaption-data`**, nikdy sem. Zmínky o `server/`, `navrhy/`, `ci/videonavody`
  a stahování hlášení níže platí pro `ReCaption-data`.

## Kde co je

- `app/src/main/java/cz/recaption/core` – logika bez Androidu (výběr a skládání titulků, oblasti,
  slovník, paměť překladů, texty aplikace, licence). Má unit testy v `app/src/test`.
- `app/src/main/java/cz/recaption` – Android část:
  - `TranslatorService.kt` – srdce aplikace (čtení → OCR → překlad → pruh)
  - `StreamReader.kt` – dekódování videa z konzole
  - `Ps5Link.kt`, `Ps5Pairing.kt`, `PsnAuth.kt` – spojení, párování, přihlášení účtem (přes telefon)
  - `PhoneHelperActivity.kt` – ReCaption v telefonu: pomocník pro přihlášení (viz Přihlášení přes telefon)
  - `SubtitleOverlay.kt`, `TitulkyAccessibility.kt` – pruh s překladem přes Usnadnění
  - `MainActivity.kt` – všechny obrazovky a průvodce (rozložení viz Obrazovky a průvodce)
  - `DiagActivity.kt` – hlášení o stavu aplikace přes celou obrazovku (k vyfocení); vše v něm
    prochází `core/CrashReport.kt` → `Redact` (žádné klíče, čísla účtu ani PIN)
  - `App.kt`, `CrashLog.kt` – zápis pádů do `pady.txt` (posledních pár) a upozornění po dalším spuštění
  - `Updater.kt` – aktualizace z aplikace (stažení nové verze a předání systému k instalaci)
  - `GameListUpdate.kt` – stažení seznamu her ze serveru (data her jsou soukromá, viz Seznam her ke stažení)
  - `LgInstallActivity.kt`, `LgInstaller.kt`, `LgVault.kt`, `LgDevMode.kt` – nahrání aplikace
    do televize LG ve vývojářském režimu a zbývající dny režimu (viz Televize LG);
    `LgScanActivity.kt` – adresa a Passphrase z obrazovky televize kamerou telefonu
  - `AtvInstallActivity.kt`, `AtvInstaller.kt`, `AtvVault.kt` – nahrání ReCaptionu z telefonu do Android TV
    přes ladění po síti (viz Android TV – nahrání z telefonu)
  - `TvProfile.kt` (+ `core/TvProfiles.kt`) – vzhled pruhu a oblasti zvlášť pro každou televizi LG/Samsung:
    aktivní je ta, která se naposledy ohlásila (platforma + model z `TvAnnounce`), hodnoty aktivní jsou
    v běžném Prefs (klíče `TvProfiles.KEYS`), ostatní v SharedPreferences `tv_profily`. Přepíná
    `TvSender.onAnnounce` v `TranslatorService`; v Titulkách šedý řádek „Vzhled a oblasti pro televizi …“.
    Nová televize začne s kopií posledního nastavení. Nový klíč vzhledu/oblastí přidej do `KEYS`.
  - `TvChoice.kt` – typ televize (Android TV / LG / Samsung): nabídka, televize v souhrnném řádku na Domů, nahrání do televize
  - `TvPackages.kt` (+ `core/TvUpdate.kt`, testy `TvUpdateTest`) – novější aplikace pro TV LG/Samsung stažená z vydání
    bez nové verze pro Android (viz Aplikace v TV bez nové verze pro Android)
  - `SamsungInstallActivity.kt` – průvodce ReCaptionem pro televizi Samsung (viz Televize Samsung)
  - `DemoFeeder.kt`, `TestCardActivity.kt` – ukázková „hra“ pro zkoušku bez PlayStationu
  - `ConsoleLink.kt` – společné rozhraní zdroje obrazu (PlayStation `Ps5Link`, Xbox `XboxLink`)
  - `XboxAccount.kt`, `XboxLink.kt`, `XboxForm.kt` – Xbox: přihlášení účtem Microsoft, spojení
    přes vzdálené hraní (WebRTC) a stránka konzole pro Xbox (viz Xbox)
  - `DeeplTranslator.kt`, `DeeplSection.kt` – DeepL (viz DeepL – Kvalita překladu)
  - `NetLogShare.kt` – záznam pro vývojáře v domácí síti, Kopírovat/Sdílet hlášení (viz Záznam ze zařízení)
  - `RemoteHost.kt` (TV) a `RemoteClient.kt` (telefon, režim „Nastavuji TV“: `Prefs` čte `dalkove_tv`, každá změna
    přenášené volby jde do TV; `core/RemoteSetup.kt`, `core/RemoteSync.kt`) – nastavení Android TV z telefonu
    (`navrhy/nastaveni-z-telefonu.md`)
- `app/src/main/cpp`, `app/src/main/java/com/metallic/chiaki/lib` – naše upravená vrstva nad
  knihovnou chiaki-ng (bez zvuku a nativního dekodéru).
- `third_party/chiaki-ng` – submodul. Needituj ho; před sestavením se do něj kopíruje
  `app/src/main/cpp` (úloha `patchChiaki` v `app/build.gradle.kts`).
- `.github/workflows/build.yml`, `ci/emulator-test.sh` – sestavení, zkouška, vydání.
- `ci/mistni-kontrola` – překlad a unit testy bez Android SDK (viz níže).
- `buildSrc` – jen zpřístupní `core/LgIpk.kt` a `core/SamsungWgt.kt` sestavení (úlohy `lgBalicek`
  a `samsungBalicek`, viz Televize LG a Televize Samsung).
- `webos` – ReCaption pro televize LG (viz Televize LG): `webos/obchod` je aplikace pro televizi,
  `webos/zkouska` diagnostika. Do APK jde jen balíček z `webos/obchod/app` (úloha `lgBalicek`).
- `tizen` – ReCaption pro televize Samsung (viz Televize Samsung): `tizen/app` se společným kódem
  s `webos/obchod/app`, do APK jako nepodepsaný `.wgt` (úloha `samsungBalicek`).
- `ios` – ReCaption pro iPhone (zkušební, od 23. 11. 2026, přání majitele: čtečka pro TV LG/Samsung, šíření přes **TestFlight**;
  přehled, díly a co musí majitel nastavit u Applu v `ios/CTI-ME.md`, rozbor možností `navrhy/iphone.md`). SwiftUI, projekt z `ios/project.yml`
  (XcodeGen), sestavení, testy a zkouška v simulátoru `.github/workflows/ios.yml` (`ios/zkouska/tv.mjs` předstírá TV), do TestFlightu z `main`
  a ručním spuštěním s tajnými údaji `APPLE_TEAM_ID`, `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8`. Zatím bez účtu (přání majitele 23. 11. 2026): nepodepsaný
  `ReCaption-iPhone.ipa` (úloha `ipa`, vydání `ios-zkusebni` / `ios`) k nahrání Sideloadly – platí 7 dní (`ios/CTI-ME.md#zdarma`). Zprávy do TV stejné jako `core/TvSender.kt`
  (`ios/ReCaption/TvZpravy.swift`) – mění-li se tvar zpráv, uprav obojí. Bez Macu se místně nesestaví. Push jen do `ios/` APK nesestavuje.
  Ikona = logo majitele (`ios/logo-recaption.png`).
- `pc` – ReCaption pro počítač s Windows (viz `pc/CTI-ME.md`), zkušební: okno aplikace
  (`pc/ReCaption.cmd`, `src/Aplikace.kt` – Domů / Titulky / ⚙ Nastavení ve vzhledu aplikace pro TV)
  čte titulky z okna s obrazem hry, přeloží je a ukáže v pruhu přes okno hry (`src/Pruh.kt`) nebo
  pošle do televize LG. Základ budoucí aplikace pro Windows / handheld: hráč hraje v chiaki-ng na
  hlavním účtu, ReCaption se ke konzoli nepřipojuje. Texty zatím jen česky. Překládá se proti téže složce `core` jako aplikace,
  takže změna v `core` se týká i jí – po úpravě `core` ověř i `gradlew -p pc installDist`.
  Do APK se nedostane. **Balíček ke stažení** `ReCaption-PC.zip` (Java a Python s knihovnami uvnitř, `ReCaption.exe`) skládá
  a zkouší `.github/workflows/pc.yml` (Windows) při změně `pc/` nebo `core`; z větve artefakt běhu, z `main` vydání `pc`, návod
  pro hráče `pomoc/pc.md`. Pushe jen do `pc/` (a `ios/`) APK nesestavují (`paths-ignore` v `build.yml`) – proto u nich `[skip ci]`
  nepsat (přeskočil by i balíček). **Mac** (Apple Silicon): tentýž kód, rozdíly v `pc/src/Mac.kt` a `OknoMac` v `cteni.py`, balíček
  `ReCaption-Mac.dmg` skládá a na Macu zkouší `.github/workflows/mac.yml` (větev → vydání `mac-zkusebni`, `main` → `mac`), návod `pomoc/mac.md`,
  podrobnosti v `pc/CTI-ME.md` (oddíl Mac). Změnu v `pc/` dělej tak, aby kód pro Windows zůstal beze změny (na Macu odbočit hned na začátku funkce).
  **Windows a Mac stejně (přání majitele 22. 11. 2026):** stejné řádky, pořadí, texty a výchozí hodnoty; texty se slovem PC jdou
  přes `Os.text` (na Macu „Mac“). Rozdíly jen tam, kde to systém nedovolí – tabulka v `pomoc/pc.md#rozdily`, návody `pc.md`
  a `mac.md` mají stejné oddíly (společné části jen v `pc.md`).
  **Čtečka pro TV** (⚙ → Použití): počítač místo Androidu čte pro TV LG/Samsung, 🔵 účtem.
  **Rozbor dávek snímků** (`pc\rozbor.cmd <složka> [-Obrazky]`, `src/Rozbor.kt`): snímky z `ci/stahni-davky.ps1`
  (i starší `zaznamy\hra-…`) přečte RapidOCR a pustí přes `TextLogic.assemble` (úkol) a `GameMenu.read`;
  `rozbor.txt`/`rozbor.csv` vedle snímků s polohou řádků, neshodami s `index.txt` a návrhem hranice úkolu. Oblasti a menu ladit podle něj.

## Sestavení a zkouška

Každý push (na jakoukoli větev) spustí GitHub Actions (`build.yml`, popis i v jeho hlavičce):

1. testy logiky (`./gradlew testDebugUnitTest`) a podepsané APK pro televize (ARM) – úloha `build`,
2. hned potom vydání (asi 10 min od pushe) – úloha `publish`:
   - z `main` → `latest` (nabídne se všem) a vydání pod číslem verze – **push na `main` jen se souhlasem majitele**,
   - z větví `zaloha-…` → jen vydání pod číslem verze,
   - z ostatních větví → zkušební kanál `zkusebni` (nabídne se jen testerům, `Tester.kt`),
3. souběžně a **jen pro informaci** zkoušky v emulátorech (úloha `emulator-apk` + `emulator`, `phone`): Android TV 9, 11, 13, 14
   (`ci/emulator-test.sh`) a telefon 11, 14 (`ci/phone-test.sh`). Vydání na ně nečeká (přání majitele 23. 11. 2026);
   neúspěšná se jednou zopakuje (`ci/zkouska-opakovat.sh`). Po změně chování nebo vzhledu se na výsledek podívej.

Fronta po větvích: novější push zruší rozběhnuté starší sestavení (nejnovější se neruší nikdy – vždy něco vyjde), rozběhnuté
vydání se neruší. Kanály nejdou zpátky: starší běh nevydá nižší `versionCode`, než v kanálu je. Síťové chyby (Maven 429 apod.)
se opakují až 5× (`ci/opakuj-sit.sh`).

Když v sezení není Android SDK (`ANDROID_HOME` je prázdné), nejde APK sestavit místně. Kód ale
jde přeložit a unit testy pustit: `cd ci/mistni-kontrola && gradle test` (asi minuta). Dělej to
před každým pushem – odhalí překlepy a chyby v typech dřív než zkouška na GitHubu. Nezná zdroje
(`res/`), manifest ani nativní část; nový zdroj `R.…` nebo novou funkci knihoven Google použitou
v kódu je potřeba doplnit do `ci/mistni-kontrola/stub`.

**Sestavení na PC uživatele (Windows)**: `.\sestavit-na-pc.ps1` (s `-Instalovat` nahraje APK přes
`adb`). První běh asi 1,5 minuty, další po změně kódu kolem 15 s. Výsledek `ReCaption-pc.apk`
je podepsaný stálým klíčem, takže jde instalovat přes verze z GitHubu a naopak. Klíč a heslo leží
mimo repozitář v `%USERPROFILE%\.recaption\klic` – nikam je nekopíruj ani nevypisuj. Nativní část
(chiaki-ng) se na Windows přeložit nedá (OpenSSL chce linuxové nástroje), proto ji skript bere
hotovou z vydání `latest` (`-PnativniZ=…` v `app/build.gradle.kts`). Když se změní
`app/src/main/cpp`, `com/metallic` nebo `third_party`, skript varuje: taková změna se ověří jen
sestavením na GitHubu. Nástroje: Java 17 (Temurin), Android SDK v `%LOCALAPPDATA%\Android\Sdk`.

**Zkouška v emulátoru telefonu** (`ci/phone-test.sh`, Android 11 a 14):
pomocník, celá aplikace v telefonu v šesti velikostech displeje a písma (malý, běžný, velké písmo,
velký, na šířku, tablet) a překlad ukázkové hry. Výsledky a snímky jsou ve větvích
`test-results-phone30` a `-phone34` (snímky `p1_…` pomocník, `p2_<velikost>_<stránka>` celá
aplikace). Po každé změně rozložení si ty snímky prohlédni.
Přihlašovací stránka Sony uvnitř pomocníka se v telefonu zkouší jen na Androidu 11 a v Android TV
jen do Androidu 13 (snímek `12p`): emulátor Androidu 14 při jejím vykreslení zamrzne celý (telefon
dvakrát ze dvou běhů, Android TV 19. 11. 2026 třikrát ze čtyř – zkouška pak stála do limitu 50 minut). Když emulátor během zkoušky přestane
odpovídat, je v `result.txt` řádek `EMULATOR SPADL` s částí, před kterou se to stalo – to je
chyba emulátoru, ne aplikace, a stačí běh pustit znovu.

Přirozenost překladu (od 0.69): `core/Natural.kt` sjednocuje v češtině oslovení na tykání
(překladač střídá tykání a vykání; od 1.00-rc2 i ve slovenštině – `Natural.slovak`, volá se přes `Natural.fix(lang, …)`,
jen seznam známých tvarů + rozkazy na -jte a -ujete, „vás/vám“ za předložkou „teba/tebe“, testy `NaturalSlovakTest`; PC aplikace
`pc/src/Ctecka.kt` volá zatím jen `Natural.czech`) a obraty ze slovníku (`GameDictionaries.idioms`, data v soukromém repu – `hry/…/GameDictData.kt`)
nahrazují ty, které překladač překládá doslova. Obojí se ladí jen podle skutečných výstupů v `preklad.txt`, ne odhadem.
Pevné překlady krátkých replik, hlášek a popisků zvuků mají všechny jazyky z `Lang.withFixed` (36) se stejnými klíči (data i test
`FixedTranslationsTest` v soukromém repu, od 1.00-rc6 i 12 jazyků jen pro překlad); nový klíč doplnit do všech.

**Hry – rozcestník ke kódu.** Podrobnosti (polohy na obrazovce, měření, záznamy testerů, příklady ze her) jsou v soukromém repu
(CLAUDE.md, oddíl Hry), data her a testy se skutečnými texty her v `hry/`.
- **Popisky zvuků v závorkách** (od 0.97.116): `core/Captions.kt` (testy `CaptionsTest` v soukromém repu), `Segment.caption`, `TextLogic.clean` / `weaponHud`.
- **Rod postav** (od 0.97.3; jméno uvnitř věty od 0.97.50; telefon ve hře přes `menuTranslate(…, speaker)`): `GameCharacter`, `Glossary.cast` /
  `genders` → `CzechGender` v `core/GameCast.kt`, testy v soukromém repu. Čeština, od 1.00-rc2 i slovenština (`core/SlovakGender.kt`, testy `SlovakGenderTest`).
- **Zásada profilů her (přání majitele 20. 11. 2026):** univerzální chování (čte se jen pás s titulky) zůstává pro všechny hry, profil hry ho
  jen vylepšuje. Každý další text hry má vlastní přepínač v Titulky → Hra (jen u profilu, který ten text zná, žádná obecná volba ho nepřebíjí);
  od 0.97.98 si ho každá hra pamatuje zvlášť – klíč `<klíč>@<id hry>`, `core/GameOptions.kt` (testy `GameOptionsTest`), přechod ze staré volby
  `game_texts` v `core/GameScope.kt` (testy `GameScopeTest`). Rada k nastavení hry `GameProfile.hint`. Bez profilu jen titulky.
- **Barvy jmen** (z profilu od 0.97.27, z obrazu od 0.97.28): `GameProfile.colors` → `SpeakerColors(preset)`, `core/NameColor.kt`
  (testy `NameColorTest`), služba `askNameColor`, `StreamReader.requestColor`, `SubtitleOverlay.learnColor`. Záznam `COLOR|…`.
- **Překlad telefonu ve hře** (od 0.97.95): volba `Prefs.phonePanel` (klíč `telefon_preklad`, výchozí vypnuto, u GTA VI od 1.00 zapnuto – `GameOptions.GAME_DEFAULTS`), umístění `Prefs.phoneOver`
  (klíč `telefon_pres`); `core/PhoneScreen.kt` (`PhoneRead`, `PhoneTracker`, testy `PhoneScreenTest` v soukromém repu, `TvPhoneMessageTest`), `core/PhoneCard.kt`,
  `core/PhoneNotice.kt` (testy `PhoneNoticeTest` v soukromém repu), `withoutPhone` + `core/TextPolarity.kt` (testy `TextPolarityTest`); služba `startPhone`,
  `SubtitleOverlay.showPhone` / `showPhoneOver`, do TV `telefon` (`TvSender.phoneMessage`, `TvPhoneImages`). Záznam `PHONEP|…`, `PHONE|…`.
  **Od 1.00-rc21** (dávky 6RKU-RZW3, JJAG-MBQB): natočený telefon při chůzi (`MenuLine.angle` z rohů řádků ML Kit, `PhoneScreen.tilt` – řádky se
  narovnají a místa otočí zpět), ztlumený displej v noci (`dimText`, `PhoneCard.light`), sledování polohy mezi čteními (`core/PhoneFollow.kt`:
  `PhoneMotion` jasový profil kolem telefonu ~10×/s přes `StreamReader.grayProfile`, `PhoneFollow` plynule s mrtvým pásmem – dech v autě panel
  nehýbe; `SubtitleOverlay.movePhone`, do TV nejvýš ~3×/s), polohy telefonu z profilu hry (`GameProfile.phoneSpots`, pole `telefon.polohy`,
  `PhoneZone` – výřez kolem telefonu, kontrola: 2× jinde = profil se nepoužije), nový obsah / jiná konverzace hned (`PhoneTracker`), odstavce
  se překládají souběžně, bubliny SMS jen u SMS (`withBubbleColors(…, lift)`), světle modré a průhledné jako menu (`GameMenu.SETTINGS_BG`, i TV CSS),
  „??“ zůstávají (`PhoneScreen.marks`), modrá bublina jen s emoji jako obrázek (`withBlankBubbles`, TV `tel-emoji`), náhledy seznamu zpráv
  u svých řádků (`showPhoneOver(…, beside = true)`, TV `"vedle":true`). Testy `PhoneFollowTest`, v soukromém repu `PhoneBatchTest`.
  **Od 1.0.5 umístění „na volné straně obrazu“** (přání majitele, podle GlossaSub; `Prefs.phoneFree`, klíč `telefon_volna`, výchozí vypnuto,
  `telefon_pres` má přednost a nemění se; text `gp_phone_free` v `I18nGame.kt`): panel na opačné polovině než telefon, ve výšce telefonu,
  nejníž 0,80 – `core/PhoneFreeSide.kt` (hystereze strany a výšky, testy `PhoneFreeSideTest`), telefon přes střed = panel vedle jako dřív.
  Za pohybem telefonu se nehýbe; přechod na druhou stranu dojede přes `PhoneFollow.rebase` / `target` (`phoneFreeBase` v `TranslatorService`).
  Do TV `"volna":true` (aplikace v TV 1.5.46: `#telefon.tel-volna` s přechodem left/top; starší TV panel jen přeloží).
- **Menu hry** – viz rozcestník níž (za Od 0.97.35).
- **Dvouřádkový úkol** (1.00-rc5, tester při lovu zvířat – úkol s tlačítky na dva řádky se bral jako titulek): `TextLogic.twoLineObjective`,
  `OcrLine.rows` = 2; rozměry odhadem bez snímku, podle dávky doladit. Testy `ObjectiveGamesTest.dvouradkovyUkol` v soukromém repu.

**Aplikace v TV 1.5.6:** se zapnutým ukazovátkem Magic Remote posílá TV OK jako kliknutí – kliknutí při hře = OK
(nabídka vzhledu); tlačítko Zpět se při hře neukazuje (rušilo).

**Od 0.97.35 (aplikace v TV 1.5.8):** OK v TV otevře s nabídkou vzhledu i stav vlevo nahoře (nezmizí po 8 s, zavře se s nabídkou;
oranžový zůstane), stejně rychlá nabídka na Android TV (`StatusPanel.pin`/`unpin` z `quickPreview`). Náhled s volbou „originál“
ukáže i druhý řádek (ukázková anglická věta `pn_ukazka_orig`, `nahled(true)`). Poloha „přes titulky“ má záměrně vyšší pruh
(zakrývá celou oblast anglických titulků), ostatní polohy jsou úzké. Služba otevírá port pro TV s opakováním
(`TvSender.start(retry = true)`, každé 2 s) – po aktualizaci ho na okamžik držela obrazovka aplikace (`TvIdle`) a TV jen hledala.
Neprůhlednost pruhu 0–100 % (`OPACITIES`: 0, 20, 40, 60, 80, 90, 100 na třech místech, od 0.97.30 / TV 1.5.7).

**Hry – rozcestník ke kódu (pokračování)**, podrobnosti v soukromém repu (CLAUDE.md, oddíl Hry):
- **Menu hry** (od 0.97.34; od 0.97.57 volba Titulky → Hra „Překlad menu hry“ `Prefs.gameMenu`, klíč `game_menu@<hra>`, výchozí vypnuto, u GTA VI od 1.00 zapnuto –
  bez ní se menu nečte): `core/GameMenu.kt` (`MenuSpec`, `MenuView`, `MenuTracker`, `MenuLabel`), `core/MenuTerms.kt` (slova ze slovníku, testy menu
  v soukromém repu); služba `checkMenu`, `menuTranslate(…, deepl = false)`, `StreamReader.grabMenu` / `sampleGray`;
  Android `SubtitleOverlay.showMenuLabels` / `showMenuDesc` / `menuIcons`, do TV `menupolozky` / `menupopis` (`TvSender.MENU_LABELS_TV`,
  `menuLabelsMessage`). Vlastní překlady menu jsou data her (soukromé repo). Záznam `MENU|…`.
- **Ikony tlačítek** (od 0.97.107): `core/ButtonIcons.kt` (testy `ButtonIconsTest` v soukromém repu), `ButtonIconSpan` v `SubtitleOverlay`.
- **Menu vpravo dole** (aplikace v TV 1.5.19): volba `Prefs.cornerMenu` (klíč `corner_menu` 0/1/2, výchozí 0, jen profil s `GameProfile.cornerSpec`),
  `core/CornerPrompts.kt` (`CornerSpec`, `CornerTracker`, `GENERIC`; testy v soukromém repu), služba `startCorner` / `dropCorner`,
  `SubtitleOverlay.showCorner`, do TV `roh` (`TvSender.cornerMessage`). Záznam `CORNER|…`. Okno s kódem po odeslání ukazuje i dávku
  (`Packed.batch`, `batchLabel`, od 0.97.130).
  **Roh je vyhrazený ovládání** (dávka 38K7-5K4G, rybaření): `CornerPrompts.reserved` (řádek začínající vpravo od 0,78 a pod 0,60) –
  čtení titulků a úkolu takové řádky zahodí (`withoutCorner`, u her s `cornerSpec` i bez zapnuté volby), `CornerPrompts.looksLikePrompt`
  (verzálky u sloupce ikon) nebere `PhoneNotice` (seznam se četl jako SMS: jméno / zpráva / příjemce), telefon `PhoneScreen.controlHint`.
  Jména postav (`CornerSpec.keep`) až od 3 písmen. Barevný kus úkolu v překladu: `ObjectiveColors.locate(…, tail)` – i volně po kmenech
  slov (`locateLoose`), kus z konce úkolu (`Run.last`) do konce překladu (dávka F6PV-UY39).
- **Tipy hry** (od 0.97.145): volba `Prefs.gameTips` (klíč `tipy_hry@<hra>`, výchozí vypnuto, u GTA VI od 1.00 zapnuto), `core/HelpTip.kt` (`TipTracker`, testy `HelpTipTest` v soukromém repu),
  služba `startTip` / `checkTip` / `dropTip`. Záznam `TIP|…`.
- **Přednost titulků** (větev `rychlost-titulku`, přání majitele): čtečka textu bere obrázky po jednom, proto vedlejší čtení (hledání
  zavřeného menu, roh, předměty, telefon, upozornění, tipy) startuje až po titulcích kola (`startSide` v `tick`), nejvýš jedno,
  s pauzou po každém a s čekáním, dokud se titulek ustaluje – `core/SideReads.kt` (testy `SideReadsTest`), služba `sideMay` / `sideLaunch`;
  zavřené menu se čte ve vlastní úloze (`startMenuLook`, `readMenu`), otevřené dál v kole. Vlákna: titulky `tp-titulky` (priorita DISPLAY),
  vedlejší `tp-vedle`, na Androidu 12+ `PerformanceHintManager` (`ReadPriority.kt`, záznam `PRIO|…`). Čítač `frames` se počítá dál
  za každý přijatý snímek (`ReadSlowdown`).
- **Brána „telefon je otevřený“** (dávka CW8G-3SHU – při jízdě se jako telefon četly nápisy nad silnicí): `core/PhoneGate.kt`
  (`icon` = kroužek tlačítka v posledním řádku ovládání vpravo dole, `quad` = světlý displej s rovnými hranami v poloze z profilu,
  `PhoneOpen` = stav; testy `PhoneGateTest`, se skutečnými dávkami přes proměnnou `RECAPTION_DAVKY`), `StreamReader.grayGrid`.
  Bez brány se telefon nečte vůbec, s ní jen výřez kolem displeje (`PhoneGate.zone`, `PhoneZone.next(now, open)`). Záznam
  `PHONEP|brana|otevreny` / `zavreny|ovladani` / `zavreny|tvar`, `PHONEP|off|brana`.
- **Pokyny u předmětů** (od 0.97.88, aplikace v TV 1.5.20): volba `Prefs.worldPrompts` (klíč `pokyny_predmety`, výchozí vypnuto),
  `core/WorldPrompts.kt` (`WorldTracker`, testy `WorldPromptsTest` v soukromém repu), služba `startWorld`, `SubtitleOverlay.showWorld`, do TV `predmety`. Záznam `WORLD|…`.
- **Pokyny pod titulky** (od 0.97.25, aplikace v TV 1.5.5): `Prefs.gamePrompts` (klíč `game_prompts`), `TextLogic.markPrompt`, `Segment.prompt`,
  do TV `{"pokyn":true}`; co v titulcích přeskočit `GameProfile.skip`, mluvčí `SPEAKER_SAYS`.
- **Úkol pod titulky** (od 0.97.43, aplikace v TV 1.5.11): `TextLogic.assemble(objectives = true)` (`objectiveLine`, `OBJECTIVE_ZONE_TOP`),
  `core/Objective.kt` (`ObjectiveTracker`, testy `ObjectiveTest`), barevná slova `ObjectiveColors` (`Prefs.objColors`, výchozí vypnuto, testy
  `ObjectiveColorsTest`), úkol jako rozkaz `core/ObjectiveImperative.kt` (testy `ObjectiveImperativeTest`); služba `handleObjective`,
  `SubtitleOverlay.showObjective`, do TV `ukol` (`TvSender.objectiveMessage`). Volby `Prefs.objectiveOn` / `objSize` / `objPos` / `objBg` (od 1.00 výchozí 12 / těsně pod úkolem -5 / 40 %, dřív podle hry / na místě / jako pruh). Záznam `OBJ|…`.
- **Offline překlad herních obratů** (0.97.98): obraty ze slovníku (`GameDictionaries.idioms`, data `hry/…/GameDictData.kt` v soukromém repu)
  a `INTENSIFIER` v `core/OfflinePrep.kt` (testy `OfflinePrepTest` v soukromém repu); ladí se jen podle `preklad.txt` a dávek.

**Fronta rychlých replik (od 0.97.46, přání majitele):** `core/ShowQueue.kt` (testy `ShowQueueTest`) – každý titulek je v pruhu
aspoň „Nejkratší doba titulku“ (Titulky → Pruh s překladem, `Prefs.minShowMs`, 0/0,5/1/1,5/2/3 s, od 0.97.49 výchozí vypnuto – nové chování, od 1.00 výchozí 1 s (přání majitele); doporučená hodnota 0,5 s, delší by frontu nafukovala); rychlejší repliky
čekají (nejvýš 3, žádná přes 5 s) a fronta se dožene v mezeře. Služba: `present` (pořadí `presentedSeq` – starší po novějším se
nezařadí), `pollQueue` na hlavním vlákně podle `dueIn`, schování po zmizení titulku přes `requestHide(teď + Nechat déle)`. S frontou
se pozdní odpověď DeepL nezahodí (`deeplThenShow`), dokud se neukázalo nic novějšího. Fronta se maže při pauze, menu hry a nové volbě.
V záznamu `QUEUE|wait n` / `QUEUE|next n`. Čtení samo běží hned po sobě (pauza 50–200 ms, `Perf`), brzdí ho délka OCR (~1 s na TV);
PlayCaption zrychluje OCR výřezem jen řádků s textem (`pasSTextem`) – u nás od 0.97.47 **„Rychlejší čtení“**
(⚙ → Čtení a překlad pod Přesností čtení – přání majitele, dřív Experimentální; `Prefs.fastRead`, výchozí vypnuto, od 1.00 zapnuto (přání majitele), dialog `fr_desc` říká, že může být chybovější a u delších řádků občas vynechá překlad; dávky majitele: pás o 10–25 % rychlejší než celá oblast, velký rozdíl dělá „Nezhasínat displej“): `core/TextBand` (testy `TextBandTest`) najde
řádky se světlými ostrými body, OCR čte jen ten pás (nová bitmapa, posun v `toBlocks`); nenalezeno = celá oblast jako dřív.
V záznamu `READ|ocr=…ms|band=ŠxV` (nebo `band=cela`) – podle toho porovnat čas čtení s volbou a bez ní.

**Hry – rozcestník ke kódu (pokračování)**, podrobnosti v soukromém repu (CLAUDE.md, oddíl Hry):
- **Stálý překlad téhož titulku** (od 0.97.83, výchozí zapnuto – oprava chyby): `core/StableLines.kt` (testy `StableLinesTest` v soukromém repu), `SubtitleTracker`,
  `RecentLines.stabilize` / `fresh`, služba `stableSegments`, `ShowQueue(replaces = StableLines.supersedes)`; pokus „Jen nové repliky“
  (⚙ → Experimentální, `Prefs.onlyNewLines`, klíč `jen_nove`, výchozí vypnuto, texty `nr_…` v `I18nQuick.kt`). Záznam `SHOW|stejne`, `SHOW|bez novych`.
- **Nápis ve scéně nad replikou**: `TextLogic.withoutLabelsAboveSpeaker` (testy `SceneLabelTest` v soukromém repu); nápisy z cedulí `TextLogic.signLine`, `dropSignWords`.

**Vlastní výška pruhu (od 0.97.45):** v nabídce Poloha poslední volba „Vlastní výška“ → střed pruhu 0–100 % po 5 (`withBarCenter`,
`Position.CUSTOM`, do TV `stred`). **Od 0.97.97 bez ručního ladění** (přání majitele): řádky „Číst jen titulky“, „Nastavit oblasti nad snímkem“ a „Jemné doladění polohy“ i obrazovka `CalibrationActivity` (modrý rámeček) jsou pryč – na telefonu nešly ovládat prstem; uložené hodnoty platí dál, „Obnovit výchozí oblasti“ zapne i automatiku a `only_subs`.

**Profil hry dál (od 0.97.4):** typy v `core/GameProfiles.kt` (`terms`, `hud`, `menu` → `GameScreen` → `TextLogic.assemble(screen = …)`),
odhad hry `GameGuess` → `Prefs.gameGuess` / `dismissGameGuess` (nic nepřepíná – přání majitele), Titulky → Hra (`gameSection`, texty `gp_…`
v `core/I18nGame.kt`), ulice `Glossary.STREET` (testy `GlossaryTest`). Názvy aut, ulic, čtvrtí a věcí ve hře se nepřekládají (přání majitele,
od 0.97.5); profil jen pro hry bez oficiální češtiny. Data profilů v soukromém repu (`hry/`) – podrobnosti v soukromém repu (CLAUDE.md, oddíl Hry).

**Seznam her ke stažení – data her jsou SOUKROMÁ (od 1.00-rc3, přání majitele 23. 11. 2026):** profily her (jména, postavy s rodem,
pojmy, nápisy, menu, vlastní překlady menu a pokynů) **a od 1.00-rc4 i herní slovníky překladu** (`core/GameDictionaries.kt`: obraty pro offline
překlad, hotové hlášky `BuiltinPhrases`, překlady krátkých replik `QuickWords` – anglický seznam pro rozpoznání repliky zůstává v kódu –, popisky zvuků
`Captions.word`, slova menu `MenuTerms`, tlačítka patičky `GameMenu.footerWord`, chráněná jména z westernu; pole `slovniky`, data
`hry/…/GameDictData.kt`, testy `OfflinePrepTest`, `MenuTermsTest`, `DictionariesTest`) **nejsou ve veřejném repu ani v APK**. Do stažení jsou slovníky prázdné. Leží v soukromém repu
`adusa89/ReCaption-data` ve složce `hry/` (`GameData.kt`, `GameProfilesMore.kt`, `Gta6MenuWords.kt` + testy s daty ze hry: `GameCast*`,
`CornerPrompts`, `GameMenu*`, `PhoneCard`, `SubtitleSizes`, `GameData`, `GlossaryGames`, `NameColorGames`) a na serveru (Cloudflare, schránka
hlášení, `/v1/hry/verze` + `/v1/hry/profily`). Veřejný `core` má jen logiku a typy; `GameProfiles.BUILTIN` je **prázdný** (verze 0, jen
univerzální profil bez jmen). Do veřejného repa **nepiš** jména postav, místa, překlady menu ani testy se skutečnými texty her – to patří do
`hry/` v soukromém repu (cloudové sezení si ho připojí přes `add_repo`, klon vedle veřejného: `../ReCaption-data`). Logika (rod, menu, úkol…)
zůstává ve veřejném kódu; seznam nese jen data a příznaky, které aplikace už umí (`pokyny`, `menuHry`, `rada` = klíč existujícího textu).
- **Tvar** (`core/GameListJson.kt`, testy `GameListJsonTest` s vymyšlenou hrou): `{"format":1,"verze":N,"minAppVerze":156,"bezneSlova":[…],"hry":[{"id",
  "nazev","jmena","bezne","postavy":[{"jmeno","rod":"m|f","prezdivky"}],"pojmy":{"cs":{…}},"napisy","menu","preskocit":[{"vzor",
  "velikost"}],"pokyny","barvy":{"Alex":"#AARRGGBB"},"menuHry":{"polozky","zalozky","preklady"},"rada"}]}`. `bezne` = jména, která
  jsou i běžná slova, `bezneSlova` = ostatní běžná slova. Čtení je přísné: neplatná položka se vynechá, neplatný celek / jiný `format` /
  `minAppVerze` vyšší než versionCode / přes 2 MB = seznam se nevezme (nikdy pád); regulární výrazy nejvýš 200 znaků, rada jen s existujícími texty.
- **Stažení** (`core/GameListFetch.kt`, testy `GameListFetchTest`, společné pro Android `GameListUpdate.kt` a PC `pc/src/SeznamHer.kt`):
  `pomoc/hlaseni.json` (adresa serveru, `ReportUpload.config`) → `GET <server>/v1/hry/verze` → vyšší než má aplikace → `/v1/hry/profily` →
  ověřit, uložit přes `.part` (`filesDir/hry-profily.json`, PC `ReCaptionTools/hry-profily.json`) a `GameProfiles.use`. Uložený se načte
  v `App.onCreate`. **Bez staženého seznamu se stahuje samo** (otevření aplikace, spuštění překladu a pak každé 2 min během překladu –
  `gamesTick` v `TranslatorService`), bez ohledu na volbu; se staženým jen s ⚙ → Experimentální „Seznam her automaticky“ (`Prefs.gamesAuto`,
  klíč `hry_auto`, výchozí vypnuto, nejvýš 1× za 6 h) nebo OK na řádku Titulky → Hra „Seznam her · verze N“ (texty `gl_…` v `I18nGame.kt`).
  Nový seznam = služba ho načte sama (`ACTION_RELOAD`). PC: bez uloženého se při spuštění chvilku čeká, s uloženým kontrola na pozadí.
  Volba hráče (`Prefs.gameId`) se nemění – hra, která v seznamu chybí, jede s univerzálním profilem. Podpis slovníku
  (`Prefs.glossarySignature`) má se staženým seznamem navíc `|hryN` (paměť překladů se obnoví), služba sestaví znovu i `GameGuess`.
- **Jak přidat hru / opravit jména (v soukromém repu):** uprav `hry/src/main/kotlin/cz/recaption/core/GameData.kt` (případně `GameProfilesMore.kt`,
  `Gta6MenuWords.kt`) → zvyš `GameData.VERSION` → `gradle -p hry seznamHer` (zapíše `hry/vystup/`; bez zvýšení verze skončí chybou) →
  `gradle -p hry test` (překládá se s veřejným `core` z `../ReCaption`, jinak `-Pverejne=…`) → commit do soukromého repa → `hry/nahrat.sh`
  (nebo `nahrat.ps1`; klíč `CTENI_KLIC`) nahraje na server → aplikace si ho stáhnou (server drží 5 min v mezipaměti).
  Nové pole nebo nové chování = nový kód ve veřejném `core`; pak zvyš `GameProfiles.MIN_APP` na versionCode té verze, ať ho starší
  aplikace nevezmou (případně `GameListJson.FORMAT`). Hra s novou radou potřebuje nové texty → jen s novou verzí aplikace.
  Změna veřejného `core`, kterou testy dat v soukromém repu pokrývají (menu, rod, roh, telefon…), → pusť i `gradle -p hry test` tam.

**Ladění profilu ze hry (od 0.97.4):** záznam v síti má navíc `GET /snimek.jpg?k=…` (celý poslední snímek,
šedý, nanejvýš 1280 px – `StreamReader.still`, čtení titulků neovlivní) a hlášení oddíly „titulky ze hry“
(`SubtitleHistory`, originál → překlad) a „co aplikace četla v oblasti titulků“ (`core/ScreenReads.kt`: surový
text z OCR a co se z něj vzalo). Během hraní na PC: `.\ci\sbirej-hru.ps1 <adresa ze zařízení>` ukládá snímky
(jen změněné) a záznam do `zaznamy\hra-<čas>\`. Podle toho se ladí `hud`, `menu`, jména a pojmy – ne odhadem.
Jednodušší cesta je ReCaption pro PC (`pc/`, hra v chiaki-ng) se zapnutým ⚙ → Ladění → Záznam hry do složky:
barevné snímky celé hry a `zaznam.txt` s přečteným textem a polohou jdou rovnou do `zaznamy\pc-<čas>\` (viz `pc/CTI-ME.md`).
**Záznam snímků po dávkách (od 0.97.59, přání majitele):** ⚙ → Experimentální → Ladění a hlášení → „Záznam snímků“ (výchozí
vypnuto, jen při běžícím překladu; se zapnutým záznamem v síti, a od 0.97.87 i u každého, kdo už snímky nahrával – `Prefs.snapButton`, klíč `snimky_tlacitko` –, tlačítko „● Snímky“/„■ Snímky“ v notifikaci, `ACTION_SNAPS`).
Každé Start…Stop = dávka `filesDir/snimky/davka-<n>-<HHmmss>/` (`snimek-0001.jpg`…, `index.txt`: čas, důvod, řádky `DiagLog`
od minulého snímku – `DiagLog.mark/since` – a `CTENI:` ze `ScreenReads`, přes `Redact`). `SnapRecorder` = vlastní vlákno každých
250 ms: `StreamReader.thumb` (mřížka 64×36) → `SnapPacer` (změna nejvýš 1×/s, velká změna po klidu hned) → `colorStill` (barevně
≤ 1280 px z nejbližšího snímku, záložní dekodér a ukázka šedě přes `still`). Stop po 10 min / se službou; všechny dávky ≤ 300 MB
(nejstarší se mažou). Vypnuté = nic za snímek (jen kontrola `stillWanted`). Logika a testy: `core/SnapBatches.kt`, `SnapBatchesTest`.
Stažení na PC (záznam v síti zapnutý): `.\ci\stahni-davky.ps1 <IP> <kód>` (`-Davka 3`, `-Vse`) → `GET /davky?k=…` (JSON)
a `GET /davka?k=…&n=3` (ZIP posílaný průběžně) → `zaznamy\davky-<čas>\davka-<n>-<HHmmss>\`; nahrávaná dávka se přeskočí.

Zkouška na Android TV navíc nechá přeložit typické herní repliky (`core/TranslationProbe.kt`)
a uloží je do `preklad.txt` ve větvích s výsledky – podle něj se ladí přirozenost překladu.
Na Androidu 13 navíc tytéž repliky do všech jazyků překladu (`trtest` + `--es trlangs "cs,sk,…"`, řádky `TRL|kód|…`)
→ `preklad-<kód>.txt` a `preklad-stav.txt` (balíček stažen?) ve větvi `test-results-api33` – ladit jiné jazyky jen podle nich.

Výsledky zkoušky se ukládají do větví `test-results-api28`, `-api30`, `-api33`, `-api34`
(přepisují se při každém běhu): `result.txt`, `crash.txt`, `log.txt` a snímky obrazovky.
Když zkouška spadne nebo jde o vzhled, stáhni si větev a na snímky se podívej:
`git fetch origin test-results-api34`.

Změny jen v dokumentaci commituj s `[skip ci]` v názvu. Pozor: `[skip ci]` přeskočí i nasazení webu (`pomoc.yml`) –
po změně `pomoc/` ho pak spusť ručně (`gh workflow run pomoc.yml`) nebo `[skip ci]` nepiš.

Emulátor se občas zasekne sám od sebe (řádek `EMULATOR SPADL` v `result.txt`). Jedno selhání může být
náhoda, opakované selhání po změně kódu ne.

## Nová verze

**Od 1.00-rc7 (přání majitele 23. 11. 2026) – přednost před starším popisem níž:**
- **Zkušební verze jen pro testery** (`Tester.kt`): APK z běžných větví má v manifestu `recaption.kanal=zkusebni` (`build.yml` → `-Pkanal`,
  `app/build.gradle.kts` → `manifestPlaceholders`), z `main` a `zaloha-…` `latest`. První běh každé verze testerského APK zapne testera
  (SharedPreferences `tester`); `Prefs.testChannel` = `Tester.active` (starý klíč `test_channel` se nečte, zůstává uložený). Řádek Zkušební verze
  (Experimentální, pomocník v telefonu) vidí jen tester, vypnutím zmizí. Tester se přidá stažením `recaption.pages.dev/d/zkusebni/ReCaption.apk`.
- **Snímky ze hry pro všechny** (od 1.00-rc19, přání majitele – dřív jen testeři): Záznam snímků, Snímky na Domů, Uložit snímky, tlačítko Snímky v notifikaci, dávka v Poslat vývojáři – vše v Experimentální, výchozí vypnuto. Jen testerům zůstává řádek Zkušební verze.
- **Nastavení bez rozbalovacích skupin:** Obecné (… a dole Nová verze, Poslat vývojáři, Vytvořit hlášení chyby), Čtení a překlad (… Sbírání replik,
  Statistika, Vlastní slovník), Experimentální (Jen nové repliky, Diagnostický režim, záznam v síti, testerské řádky). „Zapnuto“ zeleně i s doplňkem.
- **Seznam her se stahuje vždy sám** (nejvýš 1× za 6 h, volba `hry_auto` se nečte). **Sbírání replik výchozí zapnuto** bez dotazu (kdo odmítl nebo
  vypnul, zůstává vypnuto; popis `ln_help` v Nápovědě).
- **Od 1.00-rc9 i otevření aplikace** (`UsagePing.opened`, nejvýš 1× za 6 h, ne v emulátoru, `"akt":"o"` → server `otevr/<den>/<id>`, do uživatelů se nepočítá) a názvy změněných voleb (`"nast"`, `UsageCount.settingKeys`, bez hodnot); na `/pocty` „otevřeli, nepřekládají“, kde v průvodci skončili a nejčastěji měněné volby.
- **Anonymní počítadlo uživatelů** (nejde vypnout): `UsagePing.kt` + `core/UsageCount.kt` – během překladu (ne v ukázce) nejvýš 1× za 10 min
  `POST <url z pomoc/hlaseni.json>/v1/ping` s náhodným id (SharedPreferences `pocitani`), versionCode, typem (tv/telefon) a `predplatne` (zatím false);
  záznam `PING|ok`. Server (soukromé repo, `server/hlaseni`): `GET /v1/pocty` s klíčem, stránka `/pocty` (čísla teď / dnes / 7 / 30 dní, podle typu
  a verze, předplatné), úklid po 35 dnech. Text `anon_desc` v Nápovědě.
  **Od 1.00-rc8 nepovinná pole** (posílají se jen, když je hodnota známá – server neznámý klíč navíc odmítne): hlavní – `tv` (cílová televize
  atv/lg/samsung, `Prefs.tvType`), `dl` (DeepL skutečně aktivní, `DeeplTranslator.active`), `jaz` (jazyk překladu, `Lang.code`), `hra` (vybraný
  konkrétní profil hry, ne univerzální), `fr` (Rychlejší čtení, `Prefs.fastRead`); vedlejší, časem možná zrušené – `adv` (aspoň jedna pokročilejší
  volba hry u vybraného profilu: menu, roh, předměty nebo telefon ve hře – jen aproximace). `krok` (0–9, nejdál dosažený krok průvodce prvním
  spuštěním) čte nové trvalé pole `Prefs.wizardMaxStep` – zapisuje se při každém vykreslení kroku průvodce (`renderWizard`), nikdy se nesnižuje.


- **Výchozí nastavení nové instalace podle majitele (24. 11. 2026, od 1.00):** pruh těsně nad anglickým titulkem
  (`Prefs.DEFAULT_LAYOUT`), pevné písmo 14 (`match_size` výchozí vypnuto, `DEFAULT_TEXT_SIZE`), neprůhlednost pruhu 60 %
  (`DEFAULT_OPACITY`); úkol 12 / těsně pod úkolem / pozadí 40 %; u GTA VI menu, telefon a tipy zapnuté (`GameOptions.GAME_DEFAULTS`;
  Pokyny u předmětů a Menu vpravo dole vypnuté – zatím nefungují dost dobře); Rychlejší čtení zapnuto; Nechat déle +1 s
  (beze změny); Nejkratší doba titulku 1 s. Jen výchozí hodnoty v getterech – kdo volbu změnil, má dál svou. Stejné pro
  telefon i Android TV (žádná volba nemá výchozí hodnotu podle zařízení); „Obnovit výchozí vzhled / oblasti“ vrací tyto hodnoty.
- **Nové chování je výchozí vypnuté (přání majitele, 21. 11. 2026):** aplikace už funguje dobře, takže každá novinka, která mění
  dosavadní chování (čtení, zobrazení, časování, spojení…), přijde jako volba s výchozím **vypnuto**; pokusy navíc do
  ⚙ → Experimentální. Majitel je vyzkouší a sám rozhodne, co zapnout. Výjimka jen na jeho výslovné přání.
- **Uložené nastavení hráče se aktualizací nemění (přání majitele, 20. 11. 2026).** Nová verze nesmí smazat, přejmenovat
  ani „vyresetovat“ žádný uložený klíč v `Prefs` (ani v `tv_profily`, `LgVault`, `DeeplKey`, `XboxAccount`). Nová volba =
  nový klíč s výchozí hodnotou; změna výchozí hodnoty platí jen pro ty, kdo volbu nikdy neměnili. Když se změní nabídka
  hodnot (velikosti písma, průhlednosti…), stará uložená hodnota musí dál fungovat (nejbližší platná při čtení, ne výchozí).
  Jednorázový reset (nový klíč místo starého, jako `ps5_wake2` v 0.97.41) jen na výslovné přání majitele a v commitu to napiš.
- Při každé změně aplikace zvyš v `app/build.gradle.kts` obojí: `versionCode` (o 1, např. 97 → 98)
  i `versionName`, a stejná čísla v `BuildInfo` na konci `MainActivity.kt`.
  **Číslování (přání majitele):** do 0.97.147 šlo 0.97.N (`versionName` = "0.97.N"). **Od 23. 11. 2026 kandidáti na první veřejné
  vydání 1.00:** `versionName` = "1.00-rcN" (1.00-rc1 = versionCode 245, další RC = další versionCode) – do `main` (= `latest`)
  vždy až na dotaz a souhlas majitele (předtím testy); **1.00 vydaná 24. 11. 2026 (versionCode 275, versionName "1.00")**; **po 1.00 (přání majitele 24. 11. 2026): `versionName` = "1.0.N"** (1.0.1, 1.0.2 … 1.0.10), každá nová verze +1; zkušební i ostrá jsou jedna řada (ostrá = ověřená zkušební beze změny čísla), `versionCode` dál +1 (1.0.1 = 276). Vydání a čísla verzí dělá jen koordinační sezení na PC, ostatní sezení posílají větve. `versionCode` vždy roste o 1;
  aktualizace se řídí jen `versionCode` (`UpdateCheck.parse`), název je jen k zobrazení. Název commitu začíná číslem verze: `1.00-rc1: …`.
- Vydání pod číslem verze (`v0.55`) vznikne jen jednou a už se nepřepisuje. Push na `main`
  bez zvýšení čísla by tedy nechal pod starým číslem starý soubor.
- Vydání `latest` z `main` se nabídne všem uživatelům jako aktualizace (aplikace čte
  `version.txt`). Co je v `main` a projde testy logiky, jde rovnou k lidem.
- Záloha verze, ke které se má jít vrátit: větev `zaloha-<číslo>`.
- **Kontrola nové verze (od 0.97.74, přání majitele):** při spuštění, po návratu do aplikace (nejvýš 1× za 5 min) a když je aplikace
  otevřená, každých 10 min (`UPDATE_EVERY_MS` v tickeru – jeden malý dotaz na `version.txt`); ručně ⚙ → Experimentální → „Nová verze“
  (`updateRow`, tag `updcheck`: zjistit / aktuální / nová X · Domů). Texty `uc_…` v `core/I18nUi.kt`.
  **I bez otevřené aplikace (od 0.97.74, majitel to chtěl už dřív):** `UpdateNotifier` – během překladu každých 30 min (`updateTick`
  v `TranslatorService`), jinak `UpdateJob` (JobScheduler, ~3 h, jen se sítí; naplánuje se při otevření aplikace) → upozornění
  „Nová verze ReCaption X“ (kanál `nove_verze`, každá verze jen jednou – `upd_ohlaseno` v SharedPreferences `aktualizace`). Texty `un_…`.
- **Odmítnutý dotaz na instalaci (od 0.97.112, majitel: „omylem kliknu na odmítnout a nejde to dát znovu“):** `Updater.resumed` – po návratu
  z dotazu systému bez odpovědi do 8 s se instalace bere jako zrušená (`upd_cancel`) a Aktualizovat je hned zpět (Samsung / Play Protect
  po „Neinstalovat“ nic nepošle; dřív „instaluje se“ a tlačítko až po minutě).
- **Ruční adresa při nepovedené aktualizaci je klikatelná a kopírovatelná (24. 11. 2026, testeři: Play Protect
  instalaci poprvé odmítne a text adresy se nedal ani zkopírovat):** na Domů u `Updater.State.FAILED` adresa
  APK (`UpdateCheck.url`) otevře po klepnutí stažení v prohlížeči (systém pak instalaci řeší sám, obchází
  nepovedené `PackageInstaller`), vedle ní tlačítko Kopírovat (`zz_copy`, schránka + `upd_url_copied`).
- Aplikace nabízí novou verzi tlačítkem „Aktualizovat“ na stránce Domů (`Updater.kt`). Bere
  vyšší číslo z `latest` a (se zapnutým zkušebním kanálem) ze `zkusebni`.
  Od 0.65 nabízí aktualizaci i pomocník v telefonu (`PhoneHelperActivity`), kde jde zapnout
  i zkušební kanál. Odpověď systému na instalaci chodí vždy do `MainActivity`; v telefonu ji jen
  vyřídí a zmizí.

## Podpis

- Všechny verze musí být podepsané stejným klíčem. S jiným podpisem televize novou verzi přes
  starou nenainstaluje a uživatel přijde o spárování i nastavení. Verze do 0.55 měly každá
  jiný (dočasný) podpis – proto se musely pokaždé odinstalovat.
- Klíč v repozitáři není a nesmí být (repozitář je veřejný a co se jednou dostane do historie,
  tam zůstane). Je jen v tajných údajích repozitáře na GitHubu: `RECAPTION_KEY_BASE64` (soubor
  p12 v base64) a `RECAPTION_KEY_PASSWORD`. Sestavení ho při běhu rozbalí do dočasného souboru
  a po sobě smaže. Zálohu klíče a hesla má uživatel na svém PC (`%USERPROFILE%\.recaption\klic`,
obnovená z tajných údajů 19. 11. 2026). Klíč ani heslo nikam nezapisuj,
  nevypisuj je do logu a neukládej do repozitáře; `*.p12`, `*.jks`, `*.keystore` jsou v `.gitignore`.
- Krok „Kontrola podpisu“ porovná otisk certifikátu s otiskem stálého klíče zapsaným přímo
  v `build.yml` (otisk je veřejný údaj). Verze s jiným podpisem se **nevydá**: ve zkušebním
  kanálu zůstane předchozí verze a na `main` sestavení skončí chybou.
- Klíč se nemění. Po výměně si každý musí aplikaci odinstalovat a znovu spárovat. Jediná výměna
  proběhla ve verzi 0.61: první stálý klíč (0.56–0.60) ležel zašifrovaný v repozitáři, proto byl
  nahrazen novým, který v repozitáři nikdy nebyl. Starý soubor `podpis/recaption.p12` zůstal
  v historii větví, ale nic se jím už nepodepisuje.
- K tajným údajům se při sestavení dostane každý, kdo může do repozitáře zapisovat (stačí upravit
  `build.yml` ve své větvi). Právo zápisu proto dávej jen lidem, kterým bys svěřil i klíč.

## Přihlášení přes telefon

Aplikace z přihlášení potřebuje jen číslo účtu PlayStation (nemění se). Od 0.57 se nic neopisuje:

- **Stránka z televize.** Po dobu kroků přihlášení (kroky 2–4 průvodce spárováním) televize
  vystaví v domácí síti malou stránku (`core/LoginServer.kt`, text stránky `core/PhoneLogin.kt`).
  Čtvereček na obrazovce vede na ni, ne na Sony. Uživatel se přihlásí u Sony na nové kartě,
  zkopíruje adresu bílé stránky „redirect“ a vloží ji na stránku; televize si z ní vezme kód
  a pokračuje sama. Funguje i na iPhonu. Stránka přijme jen požadavky s jednorázovým klíčem
  z čtverečku a mimo tyto kroky neběží.
- **Co stránka v prohlížeči umí a neumí** (`SCRIPT` v `core/PhoneLogin.kt`, od 0.67): přihlášení
  Sony otevírá sama na nové kartě a po úspěchu ji zavře; vloženou adresu odešle sama na pozadí.
  Adresu z karty Sony si přečíst nemůže (cizí stránka) a schránku prohlížeč stránce z domácí sítě
  bez zabezpečeného spojení nevydá. Zkopírovat adresu a vrátit se na kartu ReCaption proto musí
  člověk – na iPhonu to jinak nejde, nezkoušej to obcházet. Úplně bez kopírování je jen pomocník
  v telefonu s Androidem.
- **ReCaption v telefonu.** Stejné APK nainstalované v telefonu (pozná se podle mobilní sítě,
  `NetInfo.isPhone`) ukáže místo aplikace jen pomocníka. Otevírá ho odkaz `recaption://login?…`
  ze stránky televize. Přihlášení Sony běží v něm, kód si vezme sám, číslo účtu pošle televizi
  (`LoginClient`) a zapamatuje si ho (`Prefs.phoneAccounts`; jen číslo a jméno, žádné heslo).
  Účet posílá jen na adresy domácí sítě. Záloha: přihlášení v prohlížeči a Sdílet → ReCaption.
  **Od 0.97.57 je na stránce z televize v Androidu ReCaption v telefonu první cestou** (majitel čekal, že na Android TV
  se adresa „redirect“ nepřepisuje): žluté tlačítko `wp_app_btn` s odkazem `PhoneLogin.appIntentLink` (`intent://login?…
  #Intent;scheme=recaption;package=cz.recaption;S.browser_fallback_url=<APK>;end` – aplikace je → pomocník se přihlásí sám,
  není → prohlížeč stáhne APK; `wp_easy_h`, `wp_app_inst`) a přihlášení v prohlížeči s kopírováním je pod ním sbalené
  (`<details id="web" open>`, skript ho sbalí jen na Androidu). iPhone vidí jen přihlášení v prohlížeči jako dřív.
  Před otevřením přihlášení Sony smaže cookies a úložiště WebView (`openLogin`, od 0.97.28) – hráč vždy píše e-mail
  a heslo a vědomě volí účet (jinak Sony potichu vzala naposledy přihlášený, často herní účet → CE-110032-7).
- **Jméno účtu a žádný účet potichu (od 0.97.28).** Ověřený účet se ukládá s jménem (Online ID):
  `Prefs.setPsnAccount(id, name)` / `Prefs.psnName` ze všech cest – stránka z televize (`Account.name`), pomocník
  v telefonu, ruční opsání kódu (`PsnAuth.accountForCode` → `/userinfo`, `PhoneLogin.nameFromUserInfo`).
  `PhoneHelperActivity.useForTv` už nebere první účet z telefonu – účty z `Prefs.phoneAccounts` nabídne krok
  „Účet pro ReCaption“ se jmény. Účet bez jména (uložený před 0.97.28) = „(jméno neznámé)“.
- **ReCaption v telefonu jako čtečka (od 0.97.99, přání majitele):** krok přihlášení (index 2) místo čtverečku, který by tentýž telefon nenaskenoval, ukáže text `pa_1_phone` a tlačítko `pa_1_btn` → `PhoneLogin.appLink` na vlastní `LoginServer` (`setPackage`) – přihlášení Sony v pomocníkovi téhož telefonu, účet přijde sem; ruční záloha (index 4) tlačítko `pa_3_btn` do prohlížeče (`pa_3_phone`). Texty v `I18nPs5.kt`. Na skutečném telefonu neověřeno.
- Ruční opsání kódu (index 4, na obrazovce krok 4 spárování) zůstává jako poslední záloha a od 0.66 je úplné samo
  o sobě: má vlastní čtvereček, který vede rovnou na přihlášení Sony (jako před 0.57), a z kroku
  se čtverečkem na něj vede tlačítko „Stránka se neotevřela“. Je pro případ, že se stránka
  z televize v telefonu neotevře (telefon na jiné Wi-Fi nebo na mobilních datech). Tuhle cestu
  neodstraňuj.
- **Pořadí spárování PS5 od 23. 11. 2026** (`ps5Form`, `pairNext`, Zpět; do 1.00-rc15 šest kroků – přání majitele:
  „zapnout PS5“ a „najít PS5“ byly jen jedna věta na obrazovku): index → krok na obrazovce: **0 PlayStation v síti
  + Účet pro ReCaption (1)** – appka hledá potichu sama a dřívější účet bere sama, obě věci jsou v krátkém seznamu
  s fajfkou (✔ hotovo / ○ na řadě / ○ šedě čeká), rozepsaná je jen ta, co ještě chybí (najít: políčko IP, tlačítko
  Najít, kamera, nalezené PS5; účet: „Použít účet JMÉNO“ / „Přihlásit jiný účet“ / účty z telefonu se jmény,
  čtvereček `HelpLinks.PS5_PREP` – na TV od 24. 11. 2026 v pravém sloupci vedle seznamu (`qrSide`, tag `phelp`, text ho
  obtéká, vejde se bez posouvání), v telefonu tlačítko pod tlačítky); 1–3 přihlášení tím účtem (2); 4 na PS5 přepnout na JMÉNO a Remote Play
  (3, `pg_1_text` s `%s`); 5 kód z PS5 (4, `pg_4_text`). V krocích 4 a 5 řádek „✔ Ověřený účet: JMÉNO“ s malým
  „Přihlásit jiný účet“ (`otherAccount`: zapomene účet i jméno, zpět na přípravu). Chyba párování: `ps5_fail` +
  `ps5_fail_user` (CE-110032-7, jméno), hlavní tlačítko „Zkusit znovu“ (`pairFailed`) a vedle „Přihlásit jiný účet“.
  Úspěch: `ps5_ok` se jménem. Účet se volí PŘED nastavením na PS5, protože PS5 spáruje jen, když číslo vyvolá
  stejný uživatel, jakým se aplikace přihlásila; uložený účet se i tak nikdy nepoužije potichu – tlačítko dole
  jmenuje účet, kterým se bude pokračovat. Indexy 0–5 a `pairstep=1` drží zkouška.
- **Nastavení TV z telefonu na jedné stránce (od 0.97.63, přání majitele: „nejhorší byly účty na PS a redirect“).**
  Stavěné na přenosu nastavení druhého sezení (`RemoteHost` + `core/RemoteSetup`, větev `nastaveni-z-telefonu`, návrh
  `navrhy/nastaveni-z-telefonu.md` – díl 3 „aplikace v telefonu v režimu TV“ dělá to sezení). GET stránky z TV =
  `core/SetupPage.page` (iPhone i Android, ES5, testy `SetupPageTest`, texty `sp_…` + rozcestník `wz_how_…`/`wz_ph_…`
  v `core/I18nSetup.kt`): 1. 🔵 účet (Android `PhoneLogin.appIntentLink` na tutéž stránku TV – pomocník v telefonu pošle
  pole `account`, které `RemoteHost.handle` přijme; jinak přihlášení Sony + obrázek „klepni na adresu → Kopírovat“ +
  `op=ucet&url=`), 2. PS5: osmimístné číslo `op=kod&pin=` → `RemoteHost.startPair` (adresa z `Prefs.ps5Ip`, jinak
  `Ps5Pairing.discover`; stav `RemoteHost.pair`, záznam `PAIR|z telefonu: …`), 3. DeepL `op=deepl`. Stránka se každé 2 s
  ptá `op=info` (`SetupPage.status`). **Rozcestník** v prvním kroku průvodce (W_LANGS, jen `RemoteHost.eligible` +
  PlayStation): volby „Telefonem (doporučeno)“ (`wphone` → `phoneSetup`, obrazovka `phoneSetupScreen`: čtvereček +
  seznam ✔/○ účet · PS5 · DeepL, „Pokračovat na TV“ → W_LANG) a „Přímo na TV“; výchozí fokus dál `wnext` (zkouška kliká
  naslepo, počet kroků se nemění). **iPhone zkratka:** `pomoc/zkratka.md` (`HelpLinks.IPHONE_SHORTCUT`) – `?tv=` si
  zapamatuje stránku TV (localStorage, jen adresa domácí sítě `/p/<klíč>`), `?u=<adresa redirect>` ji na stránku TV
  přepošle (`SetupPage` JS si `?u=` přečte a odešle `op=ucet`). Zkratku (Sdílet → URL Encode → Otevřít URL
  `…/zkratka.html?u=`) si hráč vytvoří podle stránky; hotový odkaz z iCloudu doplní majitel do `ICLOUD` ve skriptu
  stránky (zkratku umí podepsat jen iPhone). Na skutečném telefonu, iPhonu ani PS5 neověřeno.
  **Od 0.97.64 (spojeno s dílem 3 druhého sezení):** na stránce nahoře pro Android „Nastavit vše v aplikaci ReCaption“
  (`appModeLink` = `RemoteHost.link` → `recaption://tv`, přes `SetupPage.intentLink` se stažením APK, když aplikace není);
  rozbalovací „Kde se co dělá“ (`sp_where`, i v TV v kroku 1 spárování a na obrazovce `phoneSetupScreen`); u hotových
  částí „Změnit“ / „Spárovat znovu“ (`accx`, `pairx`, `dlx` – část se znovu otevře; nepovedené nové spárování ukáže TV
  ve stavu ještě 3 min, `RemoteHost.failAt`); **📷 Načíst číslo kamerou** (Android): `recaption://kod?h=&p=&k=`
  (`SetupPage.scanLink`/`parseScanLink`) otevře `ScanCodeActivity` (bez vzhledu: povolení kamery → `LgScanActivity`
  `MODE_PS5` → POST `op=kod` → zavřít), iPhone jen nápověda „Skenovat text“ (`sp_scan_ios`). Řádek „Nastavit
  z telefonu“ (`remotePhoneRow`) je v ⚙ → Obecné i na stránce Konzole. Vzdálená pomoc z jiné sítě zatím ne (majitel).
  **Od 0.97.66 (přání majitele – první setkání je vždy web):** úvodní stránka pomoci `pomoc/README.md` = **„Začít s ReCaptionem“**
  (`https://adusa89.github.io/ReCaption/`): dvě otázky (TV: Android TV / LG / Samsung · na čem nastavuji: počítač / Android /
  iPhone / jen ovladač), skript ukáže jen odpovídající cesty (`.cesta` s `data-tv` / `data-na`; bez JS všechny). LG/Samsung
  vždy potřebují Android (čtečka) – bez něj stránka řekne proč. Na stránce z TV pro Android karta **„Jak pokračovat?“**
  (`appmode`: „V aplikaci ReCaption (Android)“ / „Pokračovat v prohlížeči“, texty `sp_how_h`, `sp_web_go`, `sp_web_d`).
  `pomoc/android-tv.md#telefon` = nahrání z telefonu (ADB). Stránky se zveřejní až z `main`.
  **Webová obdoba aplikace (od 0.97.67, přání majitele: aplikace v telefonu je hlavní, web jako možnost pro iPhone/počítač):** na
  stránce z TV nahoře **Stav TV** (`TvStatus.rows`, tečky) a sekce **4 · Titulky a hra** (`core/WebSettings.kt`, testy `WebSettingsTest`):
  jazyk překladu, Překládat z, hra, velikost písma, pozadí, poloha (jen polohy z `TvSettings`), originál, pokyny / úkol / menu (jen
  u hry, která je zná), DeepL zapnout. Čtení v `op=info` (pole `web`, `RemoteHost.webState` – nic drahého, dotaz každé 2 s), zápis
  `op=web&klic=&hodnota=` → `WebSettings.parse` (whitelist) → Prefs / `TvLookPrefs.apply` → `changed`. Výběry ve stránce se při
  obnovení nepřepíšou, dokud jsou ve fokusu. Texty `web_h`, `web_status_h` v `core/I18nMulti.kt`.
- Zkouška v emulátoru umí ověřit jen stránku a posílání účtu, ne přihlášení u Sony. Jestli Sony
  pustí přihlášení uvnitř pomocníka na skutečném telefonu, umí ověřit jen uživatel.

## Televize LG (webOS)

Přehled pro lidi je ve `webos/CTI-ME.md`; tady jen to, co je potřeba vědět při úpravách.

- Televize LG si obraz hry nepřečte (ověřeno na OLED55B7: aplikace vidí jen černou a snímek
  obrazovky jí systém nedovolí; výsledky jsou ve `webos/zkouska/CTI-ME.md`). Na LG proto ReCaption
  běží jen jako obrazovka – hra z HDMI a přes ni pruh. Čte a překládá ReCaption na Androidu
  (telefon nebo box) a televizi posílá hotové věty po domácí síti.
- **`webos/obchod` je jediná aplikace ReCaption pro televizi** (id `cz.recaption.tv`, česky
  a anglicky, podává se do obchodu LG; postup podání je ve `webos/obchod/CTI-ME.md`).
  `webos/zkouska` je jen diagnostika, co která televize dovolí, a po síti nic nepřijímá.
- Zprávy mezi Androidem a televizí jsou popsané na jednom místě, v `core/TvSender.kt`. V televizi
  je obsluhuje jen `webos/obchod/app/app.js` (`hledej`, `zkus`, `prevezmi`). Televize si zařízení
  najde sama: projde adresy své sítě na portu 8787. Spojení je WebSocket obsloužený ručně na obou
  stranách. Novinky ve zprávách přidávej jen jako nepovinná pole (tak přibylo `casti` s barvami jmen
  a volbami odpovědí) – starší aplikace v televizi musí dál ukázat aspoň obyčejný `text`.
- **Vzhled pruhu v televizi** (od 0.86, aplikace v televizi 1.3.0): `ukaz` nese pole `vzhled`
  (`core/TvLook.kt`, s testy `TvLookTest`) – průhlednost, písmo, barva, tučně, poloha (přes titulky /
  nad titulky / pevné místo), oblast čtení a kde byl anglický titulek, případně originál. Skládá ho
  `SubtitleOverlay.show` ze stejných hodnot jako pruh na Android TV; televize (`vzhledPruhu` v `app.js`)
  pruh položí stejnými pravidly jako `ScreenLayout`. Do té doby televize kreslila pruh pořád stejně
  (černý, dole) bez ohledu na Nastavení. Písmo: sp × 50/22 = px na obrazovce 1080.
- **Lepší krytí „Přes titulky“ (od 0.97.56, tester: občas vyčuhoval anglický text):** `core/CoverBox` (testy `CoverBoxTest`)
  přidá kolem rámečku z OCR okraj (1,2 % výšky nahoře/dole, 2 % šířky vlevo/vpravo – obrys, stín a krajní písmena z rámečku
  vyčuhují) a během souvislého rozhovoru plochu jen zvětšuje (po schování pruhu `reset`). `SubtitleOverlay.show` posílá
  rozšířenou plochu i do TV (`TvLook.text`), takže kryje lépe i se starší aplikací v TV. Poloha „těsně nad“ se nemění.
- **Zakrýt celou oblast titulků (od 0.97.59, přání majitele, aplikace v TV 1.5.14):** volba v Titulky → Pruh s překladem jen u polohy
  Přes titulky (`Prefs.coverAll`, klíč `cover_all` v `TvProfiles.KEYS`, výchozí vypnuto, popis v `confirmOn`, texty `l_cover_all…`
  v `I18nQuick.kt`). `core/CoverArea` (testy `CoverAreaTest`, jedna sdílená instance `SubtitleOverlay.coverArea`) kryje obálku
  posledních titulků místo právě přečteného řádku: nejvýš 12 titulků za 90 s, jen ty svisle do 0,12 od právě zobrazeného, odlehlá
  krajní hrana staršího titulku (dál než 2,5 výšky řádku, vodorovně 0,2) se nebere, právě zobrazený vždy; okraj 1,2 % / 2 %,
  jen v oblasti čtení (+ okraj), výška nejvýš 0,3 (jinak jen právě zobrazený); během rozhovoru (pauza pod 10 s) jen roste.
  Oblast čtení (hledání) se nemění. Pruh na Android TV přesně tak vysoký (strop 33 % místo čtvrtiny) a široký aspoň jako obálka;
  do TV `text` = obálka + `"kryt":true` (TV bez dalšího okraje a stropu čtvrtiny; starší TV kryje `text` jako „pres“).
- **Poloha „Těsně nad anglickým titulkem“** (od 0.88, `ScreenLayout.hug`, `Position.ABOVE_TEXT`, v televizi
  `poloha: "tesne"`, aplikace v televizi 1.3.1): pruh jde těsně nad nalezený anglický řádek. „Nad oblastí
  čtení“ (`ABOVE`) stojí na místě nad celou oblastí. V uloženém rozložení je to 7. číslo = 2 (starší verze
  ho čtou jako „přes titulky“).
- **Vzhled z ovladače televize** (od 0.89, aplikace v televizi 1.4.0, `core/TvSettings.kt` s testy
  `TvSettingsTest`): Android pošle `{"typ":"nastaveni",…}` (po připojení a po každé změně, v
  `reloadSettings`), televize při hře na OK ukáže nabídku (pozadí, velikost, poloha, originál; `panel…`
  v `app.js`, náhled hned po změně) a pošle `{"typ":"zmen","klic":…,"hodnota":…}`. `TvSender` ji předá
  jen po `TvSettings.parse` (jen tyhle 4 volby a hodnoty z `Prefs.OPACITIES`/`TEXT_SIZES`/polohy bez
  vlastní), `TranslatorService.applyTvChange` uloží do Prefs (profil té televize) a `reloadSettings`.
  Telefon zůstává jediné místo s celým nastavením. Seznamy hodnot jsou na třech místech (Prefs,
  TvSettings, app.js) – měň je najednou.
- **Stav v televizi a nová úvodní obrazovka** (od 0.97.14, aplikace v televizi 1.5.0): při hře je vlevo nahoře
  přehled v bodech jako Domů (`stavKresli` v `app.js`): vlastní řádky televize (HDMI, ReCaption · připojeno / hledám)
  a pod nimi řádky z Androidu – zpráva `{"typ":"stav","radky":[{"barva":"ok|pozor|info","text":…}]}`
  (`TvSender.statusMessage` / `sendStatus`, obsah `TvStatus.rows` v `TvChoice.kt`: konzole a obraz, překlad
  neběží, jazyk + DeepL, hra). Posílá ji služba každé 2 s (jen změna) a `TvIdle` z otevřené aplikace. Zelený
  přehled po 8 s / prvním titulku zmizí, oranžový zůstává. Žádné věty („Hledám ReCaption…“) – jen stavy.
  **Od aplikace v TV 1.5.17 (0.97.76, přání majitele 22. 11. 2026):** úvodní obrazovka jen poprvé – vstup HDMI se pamatuje
  (localStorage `hdmi`) a příště se jde rovnou do hry; Zpět při hře = úvodní obrazovka (HDMI 1–4, stav připojení `stavKresli`, Nápověda,
  Jazyk, Ukončit – bez Vzhledu titulků a Ukázky; Ukázka jen z Nápovědy kvůli testerům obchodu LG). Nabídka OK při hře má řádek
  **Vstup · HDMI n** a funguje i bez spojení s Androidem: volby se uloží v TV (`vzhled`, čekající `vzhled_zmeny`) a pošlou se jako `zmen`
  až po první zprávě `nastaveni` po připojení. Spojení s Androidem drží TV po celou dobu běhu aplikace.
  **Od 0.97.79 (aplikace v TV 1.5.18, přání majitele):** nabídka OK má i Barvu písma, Okraj pruhu, Velikost originálu (jen se zapnutým
  originálem) a Jména barevně – `nastaveni` nese `okraj`, `jmena`, `velikostOrig`, `barvaPisma` (index `Prefs.TEXT_COLORS`), `zmen` tytéž
  klíče (`TvSettings.Change.Pad/Names/OrigSize/TextColor`). Řádky nabídky jsou seznam (`panelRadky` v `app.js`), ukázka v nabídce má
  mluvčího „Alex“, ať je vidět barva jména. Rychlá nabídka na Android TV (`QuickLook`, enum `Row`) má od 0.97.82 stejné řádky (bez HDMI).
  **Spojení drží (0.97.79, majitel: po zavření a otevření aplikace TV zase „nepřipojeno“):** `TvIdle.stopLater` – po schování aplikace
  port ještě 10 min otevřený; TV během hledání v celé síti zkouší minulou adresu každé 3 s (`hlidejMinulou`). Na Domů pod oranžovým
  „· nepřipojeno“ slabý běžící pruh, dokud se čeká na TV (`tvConnected == 0`).
- **Rychlá nabídka vzhledu na Android TV** (od 0.97.17, `QuickLook.kt`): stejné 4 volby jako OK v TV LG, otevře ji
  tlačítko zvolené v ⚙ → Obecné („Tlačítko vzhledu titulků“, `Prefs.quickKey`, `learnQuickKey`; OK, šipky, Zpět,
  Home, hlasitost nejdou – `QuickLook.FORBIDDEN`). Tlačítka chytá `TitulkyAccessibility.onKeyEvent`; filtr tlačítek
  (`FLAG_REQUEST_FILTER_KEY_EVENTS`) se zapíná jen když je tlačítko nastavené (`updateKeyFilter`) – jinak by systém
  posílal přes službu každé tlačítko. Změny přes `TranslatorService.quickChange` → `applyTvChange` (jako z TV LG),
  bez titulku ukázková věta (`quickSample`). Jen když běží překlad a aplikace není vidět. Funguje-li tlačítko při HDMI,
  záleží na výrobci TV – ověří jen majitel; po aktualizaci může být potřeba vypnout a zapnout službu v Usnadnění.
- **Stav přes hru i na Android TV** (od 0.97.23, `StatusPanel.kt`): stejné body jako v TV LG (`TvStatus.rows`) vlevo nahoře
  přes obraz, tlačítko rychlé nabídky v rámečku. Služba každé 2 s (`panelTick`, jen typ TV Android TV, ne v ukázce ani když
  je aplikace vidět); ukáže se při změně, zelený zmizí po 8 s / s prvním titulkem, oranžový zůstává.
  **Od 0.97.40 výchozí vypnuto** (přání majitele – stav stačí na Domů): volba ⚙ → Obecné „Stav přes hru“ (`Prefs.statusOverlay`,
  klíč `stav_pres_hru`, texty `so_row`/`so_desc` v `I18nQuick.kt`); bez ní se panel neukáže ani s rychlou nabídkou vzhledu.
  **Od 0.97.50 tři režimy** (`Prefs.statusMode`, klíč `stav_rezim`: 0 vypnuto, 1 dynamicky – výchozí od 0.97.51 (přání majitele), 2 vždy; starý zapnutý
  `stav_pres_hru` = vždy): dynamicky se ukáže na začátku a při změně stavu, po 8 s zmizí i oranžový, zůstane jen při
  `TvStatus.sticky` (PS5 spí, obraz nejde / spojení selhalo, chybí jazykový balíček) – odhad hry „GTA VI?“ ho už nedrží (u kolegy
  na Android TV visel pořád). TV LG/Samsung beze změny.
- **Od 0.97.22 (aplikace v televizi 1.5.4):** okraj pruhu nad a pod textem 0–3 (`Prefs.barPad`, `BAR_PADS`, klíč `bar_pad`
  v `TvProfiles.KEYS`, Titulky → Pruh s překladem; do TV pole `okraj` v `TvLook`). Ve stavu v TV je OK jako tlačítko
  v rámečku (`klavesa`), ne šedá tečka. Po nahrání do TV LG se hned uloží nová verze (`TvSender.lastSeen`,
  `Prefs.tvSeen`) – dřív Domů hlásilo starou, dokud se TV znovu nepřipojila – a ukáže se dialog s potvrzením.
  Tlačítka v kartách (Domů, Nápověda) jsou `ui.smallButton` (žlutá; běžné `ui.button` v kartě splývá).
- **Od 0.97.21 (aplikace v televizi 1.5.3):** velikost písma je číslo 12–48 po dvou (`Prefs.TEXT_SIZES`,
  `TvSettings.TEXT_SIZES`, `VELIKOSTI` v `app.js` – měň najednou, hlídá `TvSettingsTest`), slovní popisky nejsou. Okraje
  pruhu jsou podle velikosti písma (Android `SubtitleOverlay.show`, TV `padding` v em), bez pevné výšky 150 px.
- **Od 0.97.20 (aplikace v televizi 1.5.2):** Originál pod překladem je po
  mluvčích (řádek = mluvčí, v `TvLook` s `
`) a jméno má stejnou barvu jako v překladu (`SubtitleOverlay.format`,
  `puvodniRadky` v `app.js`). Pruh v TV má zaoblené rohy. Barvy jmen přiděluje ReCaption (`SpeakerColors`), z obrazu je nečte.
- **Úkol ve hře** (aplikace v televizi 1.5.11): zpráva `{"typ":"ukol","text":…,"misto":[l,t,r,b],"pismo","pozadi","barva","casti":[{text,barva}]}`
  ukáže řádek úkolu (GTA VI) ve vlastním prvku `#ukol` (`ukolUkaz` v `app.js`) uprostřed `misto` (zlomky obrazu), šířka podle
  textu do 90 %; prázdný text = schovat. Na pruhu nezávislý (`skryj` ho neschová), mizí při ztrátě spojení a na Domů; `hotovo` neposílá.
- **Velikost originálu (od 0.97.44, aplikace v TV 1.5.12; od 0.97.50 velikost písma místo procent – přání majitele):** Titulky →
  Písmo → „Velikost originálu“ (jen se zapnutým originálem, `Prefs.origSp`, klíč `orig_sp` v `TvProfiles.KEYS`, 0 = automaticky
  62 % překladu, jinak 12–48 jako překlad). Android `AbsoluteSizeSpan` (sp), do TV pole `pismoOrig` = podíl px originálu a px
  překladu v % (TV ho omezí na 30–100, takže originál v TV nebude větší než překlad); bez volby se nic neposílá.
- **Domů hlásí starou aplikaci v televizi** (`TvChoice.outdated`: ohlášená verze vs. `receiver-manifest.json`
  v APK, text `w_tv_old`), s tlačítkem k nahrání.
- Android: **Typ televize** (`Prefs.tvType`: Android TV / LG / Samsung, `core/TvReceiver.kt` →
  `TvType`). Kdo ho nezvolil, řídí se starým klíčem `tv_send` (zapnuto = LG), takže dřívější
  uživatelé LG o nic nepřijdou; `setTvType` klíč `tv_send` drží v souladu. `Prefs.tvSend` = typ
  LG nebo Samsung. Volí se v průvodci (krok Televize, `TvChoice.set`), v Nastavení a ze souhrnného
  řádku na Domů – nabídka přes `TvChoice.show(activity) { … }`. Bývalý přepínač „Posílat titulky do
  televize LG“ není. Souhrnný řádek na Domů ukazuje pro LG/Samsung stav spojení (`TvChoice.short`);
  upozornění s tlačítkem k nahrání aplikace do televize je na Domů jen do chvíle, než se z ní aplikace
  poprvé ohlásí (`TvChoice.needsInstall`, `Prefs.tvSeen`). Krok Televize v průvodci a Nastavení
  nabízejí nahrání (i aktualizaci) vždy. Pro Android TV se na Domů upozorňuje na chybějící povolení
  pruhu. `SubtitleOverlay` předává `TvSender` každou změnu pruhu. U Android TV se nic
  neotevírá ani neposílá a aplikace se chová jako dřív. U LG/Samsungu se čte i tehdy, když je
  vidět aplikace, a nevyžaduje se povolení pruhu.
- Televize po připojení pošle `pripojeno` a od verze 1.1.0 v něm i `platforma` („webos“/„tizen“)
  a `verze`. `TvSender` zprávy od televize čte (maskované rámce WebSocketu, `readFrame`) a poslední
  ohlášení drží v `TvSender.lastSeen` (`TvAnnounce`); podle něj Domů a průvodce Samsung poznají,
  co v televizi běží.
- Telefon: pomocník (`PhoneHelperActivity`) má od 0.63 tlačítko „Použít telefon pro televizi LG“
  a vedle něj „… Samsung“ (z celé aplikace zpět: ⚙ → Obecné → Pomocník pro přihlášení otevře pomocníka s `EXTRA_FROM_APP` –
  od 0.97.55 nahoře „‹ Zpět do aplikace“ a Zpět telefonu vrací do aplikace, `forceTv` se nemění; dřív cesta zpět nebyla). Zapne `Prefs.forceTv` a nastaví typ televize, předá už ověřený účet do párování a otevře celou
  aplikaci. Jestli se celá aplikace dá na telefonu pohodlně ovládat, umí ověřit jen uživatel.
- **Nahrání do televize z Androidu** (přehled ve `webos/CTI-ME.md`): tlačítko „Nahrát ReCaption do
  televize LG“ v průvodci (krok Televize), v Nastavení, upozornění na Domů (typ LG, dokud se televize
  neohlásila), v pomocníkovi v telefonu a v Nápovědě otevře
  `LgInstallActivity`. APK nese `assets/lg/receiver.ipk` + `receiver-manifest.json`; skládá je při
  sestavení úloha `lgBalicek` (`app/build.gradle.kts`) kódem `core/LgIpk.kt`, který `buildSrc`
  sdílí se sestavením – žádný Node ani Python. Rozložení odpovídá `ares-package` (ověřeno), datum
  je pevně 1. 1. 2000 (webOS 5 odmítá 1970). Instalace (`LgInstaller`, JSch): Key Server
  `:9991/webos_rsa` + Passphrase (neukládá se) → SSH `prisoner@:9922` → verze v
  `/media/developer/apps/usr/palm/applications/cz.recaption.tv/appinfo.json` → stejná/novější jen
  spustit, **nikdy nesnižovat verzi** → jinak SFTP, `appInstallService/dev/install`, kontrola, spuštění.
  Otisk klíče televize se pamatuje, změnu musí potvrdit uživatel („Spárovat znovu“); klíč SSH
  a klíč relace jsou zašifrované přes Android Keystore (`LgVault`). Zbývající dny režimu
  (`LgDevMode`) se ptají služby LG nejvýš jednou za hodinu; **nic se neprodlužuje automaticky**.
  Rozhodování a čtení odpovědí je v `core/LgInstall.kt` s testy. Skutečnou televizi zkouška
  v emulátoru nemá – instalaci ověří jen uživatel. Při změně `webos/obchod/app` zvyš verzi
  v `appinfo.json` i `platforma.js`, jinak Android televizi neaktualizuje (stejná verze = jen spustit).
  Převzato podle PlayCaption (AGPL-3.0) – hlavičky souborů zachovej.
- **Načtení adresy a Passphrase kamerou** (telefon): tlačítko „Načíst z obrazovky televize kamerou“
  v `LgInstallActivity` jen tam, kde je kamera a nejde o televizi/box (`LgScanActivity.available`:
  bez `leanback` a UI televize). Povolení CAMERA se žádá až po stisku tlačítka. `LgScanActivity` =
  Camera2 ze systému (náhled TextureView + snímky YUV do ML Kitu) – záměrně bez CameraX, ať APK
  nepřibírá knihovny a nepotřebuje AndroidX. Čtení řádků (`core/LgScan.kt`: IPv4 v domácí síti,
  Passphrase jen u popisku „Passphrase“, případně pod „Key Server“; víc kandidátů = nic) a hlasování
  (`LgScanVote`: údaj platí po dvou stejných čteních) mají testy `LgScanTest` s ukázkami textu
  obrazovky. O/I v Passphrase se opraví na 0/1 jen u šestnáctkového tvaru (ostatní písmena A–F),
  jinak upozornění `km_unsure`. Výsledek jde zpět průvodci k potvrzení, uživatel stiskne „Nahrát“.
  Nic se neukládá ani nevypisuje do logu; `Redact` skrývá i `passphrase=…`. V manifestu musí
  zůstat `uses-feature` kamery s `required="false"` – jinak by povolení CAMERA vyloučilo
  televize a boxy bez kamery (ověř `aapt dump badging`: kamera jen `uses-feature-not-required`).
  Texty `km_…` v `core/I18nKamera.kt`. Na skutečné televizi LG čtení ověří jen uživatel.
  Tlačítko skeneru je v `LgInstallActivity` pod políčkem adresy (dokud není adresa, je hlavní); k čemu kamera
  je (`km_why`), řekne dialog před žádostí o povolení.
  **Aktualizace z Domů (od 0.97.93, přání majitele – „Aktualizovat“ otevíralo další stránku):** spárovaná TV se známou adresou se aktualizuje přímo na Domů (`LgUpdate`: vlastní vlákno s `LgInstaller`, řádek „ReCaption v TV LG · <krok>“ s pruhem, po chybě červený řádek `lg_e_…` a tlačítko na stránku s přípravou); jinak `LgInstallActivity` jako dřív. „Spustit ReCaption v TV“ u aktuální verze ukazuje celou dobu jen `lg_p_launch` (kroky nahrávání vypadaly jako nová instalace).
  **Dva tvary `LgInstallActivity`:** *stručný* (TV spárovaná v `LgVault` a známá adresa) = stav „LG OLED55B7 ·
  1.5.6 → 1.5.8“, jedno velké tlačítko („Aktualizovat v TV“ při starší verzi podle `TvChoice.outdated`, „Nahrát
  do TV“ poprvé, „Spustit ReCaption v TV“ při aktuální), postup, řádek dnů vývojářského režimu a řádek „Jiná TV
  nebo potíže…“; *příprava* (nespárovaná TV, chyba nahrání, nebo první zjištění neskončí krokem READY, tj. TV /
  vývojářský režim nejde najít) = karta krok za krokem, adresa, kamera, Passphrase. Příprava se rozbalí
  natrvalo (`expanded`), ať stránka neskáče. Delší vysvětlení (síť, prodloužení režimu, nahrání z počítače)
  je v Nápovědě (`hc3_lg`), ne na stránce.
  **Od 1.0.5 (přání majitele, inspirace GlossaSub, ne doslova):** v přípravě seznam **Nalezené TV** (`foundCard`: každá TV se stavem
  připraveno / vývojářský režim vypnutý / Key Server vypnutý, ✔ vybraná, OK = použít adresu) a „⟳ Hledat znovu“ (`forceSearch`, hledá se i se
  známou adresou a jednou při otevření); **stav hned vedle políčka adresy** (`addrStatus`, mění se bez překreslení stránky); nad hlavním tlačítkem
  **jedna věta, co je na řadě** (`nextLine`, texty `lg_next_<krok>`); **průběh nahrávání jako seznam** kroků `LgPhase` (✔ / ➜ se slabým pruhem / ○,
  `phaseList`) s „Stránku nechat otevřenou“ a rozbalovacími **Podrobnostmi** (trvání kroků); po prvním úspěšném nahrání **nabídka prodlužování
  vývojářského režimu** (`renewOffer`, jednou – `ui_jednou`/`lg_prodlouzeni`, volba `Prefs.lgDevRenew` dál výchozí vypnutá; z průvodce se stránka
  zavře až po odpovědi). Prodlužování samo je `LgDevMode.renew` (jednou denně, jen se zapnutou volbou).
  **Pomocník krok za krokem** (od 0.91, `core/LgSetup.kt` s testy `LgSetupTest`, síť `LgFinder.kt`): karta
  v přípravě `LgInstallActivity` se každé 3 s (jen když je vidět) sama odškrtává – televize v síti (SSDP
  `urn:lge-com:service:webos-second-screen:1`, záloha projití /24 na portech 3000/9922; nalezenou adresu
  sama vyplní), vývojářský režim (otevřený port 9922), Key Server (9991; u spárované televize se
  přeskočí), Passphrase (6 znaků 0–9/A–F). Rozepsaný je jen krok na řadě; u vývojářského režimu odkaz
  na účet LG (telefon: tlačítko, televize: čtvereček). Dřívější texty `lg_prep…` nahradily `lg_s_…`.
  Od 0.86 umí `LgScanActivity` s `EXTRA_MODE = MODE_PS5` přečíst i osmimístné číslo pro spárování
  z obrazovky PS5 (`core/Ps5CodeScan.kt`, testy `Ps5CodeScanTest`); tlačítko „Načíst číslo…“ je
  v posledním kroku párování v MainActivity (jen telefon s kamerou). `MODE_PS5_IP` čte adresu PS5
  z Nastavení → Síť → Stav připojení (`Ps5CodeScan.ip`: adresa u „IPv4“, jinak jediná domácí adresa
  nekončící .1/.254); tlačítko je od 0.97.37 vždy hned pod políčkem adresy (malé „📷 …“), stejně pod číslem z PS5
  a v LgInstallActivity pod adresou i pod Passphrase (jen telefon s kamerou – `LgScanActivity.available`).
- Políčka pro adresu (PS5 i LG) nastavuje `IpField`: klávesnice pro adresy a čárka/mezera → tečka
  (česká číselná klávesnice má místo tečky čárku).
- **Ikona v TV LG / Samsung od aplikace v TV 1.5.39 (ReCaption 1.00, přání majitele)** = logo aplikace pro Android (bublina s ▶, `ic_logo.xml`)
  na plné žluté dlaždici; kreslí ji `python3 webos/obchod/ikony.py` (LG `icon.png`, `largeIcon.png`, `ikona-400.png` i `tizen/app/icon.png`).
- `webos/obchod/app/app.js`, `style.css` a `index.html` jsou **společné s aplikací pro Samsung**
  (kopie v `tizen/app`); co je jen pro LG, je v `platforma.js` (`window.ReCaptionTv`). Při změně
  společného souboru ho zkopíruj do `tizen/app` – jinak spadne sestavení APK (`samsungBalicek`)
  i unit test `SamsungTest`. Zkouška obou bez televize: `node tizen/test/prijimac.test.js`.
- Televize uživatele má prohlížeč Chromium 38. Kód pro televizi proto musí zůstat ve starém zápisu
  JavaScriptu: žádné `let`/`const`, šipkové funkce, `Promise`, `fetch` ani šablonové řetězce.
- **Hebrejština a thajština (příprava jazyků, 23. 11. 2026):** směr podle obsahu řádku – `core/Bidi.kt` (testy `BidiTest`): řádek
  s písmem psaným zprava doleva je celý zprava doleva (i „LUCIA: …“), ostatní jazyky beze změny. Android: `TextView.bidiFor` na konci
  `SubtitleOverlay.kt` u každého okna s překladem (`TEXT_DIRECTION_ANY_RTL`, END → RIGHT, ať menu vpravo dole a patička zůstanou vpravo).
  TV: `smer()` v `app.js` dá řádku `dir="rtl"` (ne `dir="auto"` – ten by podle „LUCIA“ zvolil zleva), jméno v `<bdi>` (`jmenoDo`);
  bez hebrejštiny se atribut nenastaví. Písma pro TV: `webos/obchod/app/fonty` (= `tizen/app/fonty`, hlídá `SamsungWgt.SHARED_DIRS`)
  – Noto Sans Hebrew / Thai Regular + Bold, WOFF zúžené `pyftsubset` na hebrejské / thajské znaky (~8–10 kB), `@font-face`
  s `unicode-range` na začátku `style.css`, licence `fonty/OFL.txt`. Na skutečné TV ani telefonu neověřeno.
- Zkoušení bez Androidu: `node webos/posilac.js` posílá z počítače zkušební věty, `pc/` čte
  a překládá na počítači. Nástroj LG (`ares-package`, `ares-install`, `ares-launch`, `ares-inspect`
  z `@webos-tools/cli`) nahraje aplikaci do televize ve vývojářském režimu; `ares-inspect` dá živý
  přístup do běžící aplikace.
- Změny jen ve složkách `webos`, `tizen` a `pc` nemění verzi aplikace pro Android TV a commitují se
  s `[skip ci]`. Balíčky `.ipk` a `.wgt` se necommitují. Jdou do APK, ale **od 1.0.3 jde nová verze aplikace pro TV
  vydat i samostatně** (bez nové verze pro Android) – viz Aplikace v TV bez nové verze pro Android.

### Aplikace v TV bez nové verze pro Android (od 1.0.3, přání majitele 24. 11. 2026)

- Vydání `latest` a `zkusebni` nesou vedle APK `ReCaption-LG.ipk`, `ReCaption-Samsung.wgt` a `tv-verze.txt` (jen číslo, „1.5.44“),
  přes Cloudflare `recaption.pages.dev/d/<kanál>/<soubor>`. `build.yml` je bere z APK (assets), ale **nesníží** je: je-li v kanálu
  samostatně vydaná vyšší verze pro TV, nechá ji tam (`tv_do_kanalu`). Vydání `v<verze>` nese balíčky z APK.
- **Samostatné vydání = ruční workflow `.github/workflows/tv.yml`** („Vydat aplikaci pro TV“, vstup `kanal`: `zkusebni` / `latest` / `oba`;
  `latest` jen z `main`). Sestaví balíčky týmiž úlohami jako APK (`./gradlew :app:lgBalicek :app:samsungBalicek` – bez NDK a submodulu),
  pustí `node tizen/test/prijimac.test.js` a testy balíčků, ověří, že LG a Samsung mají stejnou verzi, a v kanálu přepíše (`gh release
  upload --clobber`) jen ty tři soubory – `ReCaption.apk` ani `version.txt` se nemění. Nižší verzi než v kanálu nevydá.
  Apps2Samsung bere `.wgt` z `latest`, takže vydáním do `latest` dostanou novou verzi i uživatelé Samsungu (v Apps2Samsungu Update).
- **Postup:** 1. upravit `webos/obchod/app` (+ kopie společných souborů do `tizen/app`) a zvýšit verzi na **4 místech**: `webos/obchod/app/appinfo.json`,
  `webos/obchod/app/platforma.js` (`verze:`), `tizen/app/config.xml` (`version=`), `tizen/app/platforma.js` (`VERZE`); verzi pro Android neměnit.
  2. `node tizen/test/prijimac.test.js`, commit s `[skip ci]` (APK se nesestavuje). 3. Push větve a spustit workflow s `kanal=zkusebni`
  (`gh workflow run tv.yml --ref <větev> -f kanal=zkusebni`) – vyzkouší testeři. 4. Se souhlasem majitele na `main` a `kanal=latest`.
  (`tv.yml` musí být v `main`, aby šel spustit z webu GitHubu; z příkazové řádky jde i z větve, kde je.)
- **Telefon / Android TV** (`TvPackages.refresh`, vždy mimo hlavní vlákno): `tv-verze.txt` z `latest` (tester i ze `zkusebni`, vyšší z nich)
  při otevření aplikace nejvýš 1× za 6 h (jen pro zvolený typ TV LG/Samsung) a na stránce nahrání LG / průvodci Samsung nejvýš 1× za 10 min
  (`TvUpdate.shouldCheck`, SharedPreferences `tv_balicky`). Je-li číslo vyšší než v APK i než už stažené, stáhne balíček (nejvýš 4 MB),
  ověří ho (`TvUpdate.checkLg`: ar/ipk, id `cz.recaption.tv`, verze v control i appinfo.json; `checkSamsung`: ZIP celý, config.xml s id a verzí,
  bez podpisu) a uloží s popisem ve tvaru `receiver-manifest.json` do `files/tv/<lg|samsung>`. `TvPackages.load` / `version` pak dávají
  stažený balíček, jen když je novější než v APK a projde `LgInstall.bundle` / `SamsungInstall.bundle` (velikost, otisk); jinak (offline,
  chyba, nová APK se stejnou nebo vyšší verzí) platí APK jako dřív. `TvChoice.bundledVersion` = verze, kterou telefon nahraje, takže Domů
  ukáže „ReCaption v TV LG 1.5.44 · nová 1.5.45 – Aktualizovat v TV“. Záznam `TVB|…` v DiagLog. Cloudflare drží velké soubory 5 min
  v mezipaměti – hned po vydání může balíček nesedět s `tv-verze.txt`; stažení se pak zahodí a zkusí se při další kontrole.

## Televize Samsung (Tizen)

Přehled a zjištění jsou v `tizen/CTI-ME.md`; tady jen to, co je potřeba vědět při úpravách.

- Stejný princip jako LG: televize ukazuje hru z HDMI a pruh, věty dostává z Androidu stejnými
  zprávami (`core/TvSender.kt`). `tizen/app` = kopie `app.js`, `style.css`, `index.html` z
  `webos/obchod/app` + vlastní `platforma.js` (obraz `tizen.tvwindow` pod průhlednou stránkou,
  `tizen.systeminfo`, Zpět = 10009) a `config.xml` (balíček `ReCaption1`, id `ReCaption1.ReCaption`).
  Verze v `config.xml` a `platforma.js` se musí shodovat (hlídá sestavení). Starý zápis JS (ES5).
- APK nese `assets/samsung/receiver.wgt` + `receiver-manifest.json` (`signed: false`); skládá je
  úloha `samsungBalicek` kódem `core/SamsungWgt.kt` (ZIP s pevným datem, přes `buildSrc`).
- **Od 0.97.52 hlavní cesta přes Apps2Samsung** (komunitní instalátor v telefonu s Androidem, zdarma, bez počítače; umí najít TV,
  vyrobit podpis jedním přihlášením účtem Samsung a nahrát libovolný `.wgt`). `SamsungInstallActivity` = pomocník krok za krokem
  jako u LG (`core/SamsungSetup.kt`, testy `SamsungSetupTest`): 1. vývojářský režim (Host PC IP = adresa **telefonu**; odškrtne se
  sám, když TV Samsung nalezená přes SSDP `LgFinder.samsung` otevře port SDB 26101), 2. Apps2Samsung v telefonu (balíček
  `nl.madebypatrick.apps2samsung`, `<queries>` v manifestu), 3. zdroj ReCaption odkazem `apps2samsung://source?url=…`
  (web `https://apps2samsung.com/source?url=…`, umí od Apps2Samsung 3.3.1) na **`github.com/adusa89/ReCaption`**,
  4. Install v Apps2Samsungu, 5. hotovo podle ohlášení z TV (`TvCheck`). Kroky bez zjištění mají tlačítko Hotovo. Stará cesta
  (uložit `.wgt` + Tizen Studio) je rozbalovací řádek „Bez telefonu s Androidem: počítač“. Vydání `latest` a `v…` nesou
  `ReCaption-Samsung.wgt` (vytažený z APK v `build.yml`, nebo novější samostatně vydaný `tv.yml`; od 1.0.3 i `zkusebni`, ale
  Apps2Samsung bere jen `latest`). Odkazy na stažení
  Apps2Samsungu jsou na `pomoc/samsung.md` (kotvy `apps2samsung`, `pocitac`). Texty `sa_…` v `core/I18nSamsung.kt`.
  Se skutečnou TV Samsung zatím nevyzkoušeno (ani jestli port 26101 odpoví i jinému zařízení než Host PC IP).
- **Stav vývojářského režimu přes REST API televize (24. 11. 2026):** TV Samsung odpovídá v domácí síti bez vývojářského
  režimu na `GET http://<TV>:8001/api/v2/` JSONem s polem `device.developerMode`/`developerIP` (tvar podle Apps2Samsungu
  a knihovny samsungtv-ws) – `LgFinder.samsungInfo` (čte ze soketu, bez povolení HTTP bez šifrování) a
  `core/SamsungSetup.parseApi` / `devState` z toho pozná vypnutý režim, prázdnou nebo jinou Host PC IP, adresu uloženou
  pozpátku (`sa_dm_reversed`) i zapnutý režim čekající na restart TV (port SDB ještě zavřený). Když REST neodpoví (starší
  TV), platí jen otevřený port 26101 jako dřív. `SamsungInstallActivity` ukazuje konkrétní stav pod nalezenou TV
  (`devStateKey`) a krátké rady (`sa_dev_tip`, `sa_install_tip`, `sa_ready_tip`) u jednotlivých kroků; totéž shrnuté
  (`sa_tips`: restart TV, adresa pozpátku, Anynet+/herní režim, chyby Apps2Samsungu, aktualizace systému TV) je v
  Nápovědě. Testy `SamsungSetupTest.restApiIsRead` / `devModeStates`. Na skutečné TV neověřeno.
- **Spořič obrazovky v TV (aplikace v TV 1.5.50):** vedle `tizen.power.request` (od 1.5.44) hlavní cestou Samsung Product
  API `webapis.appcommon.setScreenSaver` (knihovna `$WEBAPIS/webapis/webapis.js`, bez oprávnění, úroveň Public) –
  `tizen/app/platforma.js` ji načte jen tam (webOS tento skript nemá) a zavolá při startu i po návratu aplikace do
  popředí (`visibilitychange`). Beze změny na LG.
- **Přímá instalace z Androidu (bez Apps2Samsungu) není a záměrně**: Samsung přijme jen balíček podepsaný
  distribučním certifikátem vystaveným na účet Samsung pro DUID konkrétní televize; podpis
  a nahrání (SDB, port 26101) se bez skutečné televize nedají ověřit. Průvodce
  `SamsungInstallActivity` vysvětlí vývojářský režim (Apps → 12345 → Host PC IP), uloží .wgt
  přes systémový dialog, pojmenuje Tizen Studio / komunitní instalátory a **úspěch hlásí jen
  podle ohlášení aplikace z televize** (`TvCheck`: stejná/novější verze = hotovo). Když neběží
  překlad, otevře si port 8787 sám (vlastní `TvSender`) a po úspěchu pošle zkušební větu.
- Rozhodování a čtení zpráv: `core/TvReceiver.kt` (`TvType`, `TvAnnounce`, `TvCheck`,
  `SamsungInstall`), testy `SamsungTest`. Skutečná televize Samsung zatím nikdy nebyla vyzkoušená.
- Převzato podle přijímače PlayCaption pro Samsung (AGPL-3.0) – hlavičky souborů zachovej.

## Android TV – nahrání z telefonu (zkušební, od větve nastaveni-z-telefonu)

Zjištění a rozhodnutí: `navrhy/instalace-android-tv.md`. Bez aplikace v televizi (Downloader…).

- Pomocník v telefonu → „Nahrát ReCaption do Android TV“ (`at_open`) → `AtvInstallActivity` (vzor `LgInstallActivity`):
  karta kroků, které se každé 3 s samy odškrtávají (TV v síti – projití /24 na portech 5555 / 6466 Android TV Remote /
  8009 Cast; ladění zapnuté = port 5555; TV klíč přijala), adresa (`IpField`, i `ip:port`), velké tlačítko, průběh.
- `AtvInstaller` přes knihovnu **`dev.mobile:dadb:2.0.0`** (Apache-2.0, čisté Kotlin + okio, asi 0,4 MB): ADB po TCP
  s klíčem RSA → poprvé TV ukáže „Povolit ladění?“ (čeká se až 120 s, krok `CONFIRM`) → `dumpsys package cz.recaption`
  → **stejná/novější verze jen spustit, nikdy nesnižovat** → `cmd package install -r` vlastního APK z telefonu
  (`applicationInfo.sourceDir`, se stálým podpisem; split APK přes `installMultiple`) s pruhem podle bajtů → kontrola
  verze → `am start`. Nic neodinstalovává (jiný podpis = `at_e_signature`). Limit 8 min, Zrušit.
- Klíč ADB telefonu: RSA 2048 z `KeyPairGenerator`, soukromý zašifrovaný přes Android Keystore (`AtvVault`, alias
  `recaption-atv-v1`, `LgVault.seal/open`); veřejný v tvaru adbkey.pub počítá `AtvInstall.adbPublicKey` (test ho
  porovnává s výstupem dadb). Rozhodování, adresa, čtení odpovědí, chyby `AtvError` → `at_e_…`: `core/AtvInstall.kt`,
  testy `AtvInstallTest`. Texty `at_…` v `core/I18nAtv.kt` (`ATV_S`, `ATV_X`), společné s LG `lg_go`, `lg_ip`….
- **Neumí** Bezdrátové ladění s párovacím kódem (Android 11+, TLS + SPAKE2) – dadb to nemá; port, který odpoví jinak
  než ADB, hlásí `at_e_wireless`. Další krok viz návrh.
- Skutečnou televizi zkouška nemá: spojení, dotaz „Povolit ladění?“, instalaci a spuštění **ověří jen majitel**
  (Sony/Philips/TCL, Chromecast s Google TV, Fire TV, Shield). Neověřeno ani to, že dadb běží na Androidu
  (podpis `RSA/ECB/NoPadding` přes Conscrypt) – první sestavení na GitHubu a ruční zkouška.

## Xbox

- Druhá konzole vedle PlayStationu (`Prefs.console`, výchozí PlayStation – dosavadní uživatelé si
  nic nevšimnou). Volba: krok Konzole v průvodci, řádek „Konzole“ v Nastavení (`consoleRow`, výběr
  `chooseConsole` → `setConsole` v MainActivity) a souhrnný řádek na Domů. Položka menu se jmenuje
  Konzole; nadpis stránky je PlayStation nebo Xbox a u Xboxu ukazuje `XboxForm` místo párování PS5.
- Přihlášení: kód na microsoft.com/link (device code; čtvereček na stránku, v telefonu tlačítko
  „Otevřít stránku“) → token Microsoft → Xbox Live → XSTS → streamovací token xhome. Ukládá se jen
  obnovovací token, zašifrovaný klíčem z Android Keystore (`recaption-xbox-v1`, stejně jako
  `LgVault`), jméno hráče a vybraný Xbox. Tokeny se nevypisují; `Redact` skrývá i tokeny Microsoft
  a hlášení dostává obnovovací token mezi tajné hodnoty. Odhlásit při chybě sítě se nesmí – jen
  když Microsoft řekne invalid_grant (`XboxAuth.refreshOutcome`).
- Spojení (`XboxLink`): seznam konzolí → spící Xbox se **nebudí** (jen se každých 20 s zeptá) →
  relace `/v5/sessions/home/play` → WebRTC jen příjem obrazu, H.264 napřed a `b=AS:3000`
  (`XboxSession.preferH264`, kodér `DefaultVideoEncoderFactory` jako PlayCaption), kandidáti Teredo
  rozbalení na IPv4 i s herním portem 3074 (`XboxSession.teredo`). Zvuk se nepřehrává, ovládání se
  neposílá (jen „ovladač přidán a hned odebrán“ jako webový přehrávač). „Připojeno“ se hlásí až
  s prvním snímkem (`onConnected`), ne už po spojení ICE – bez snímku do 25 s (`FIRST_FRAME_MS`)
  se spojení zkusí znovu. **Od 24. 11. 2026 zpětná vazba o zobrazených snímcích** na kanálu `input`
  každou sekundu (`XboxInput.metadataPacket`) – bez ní Xbox usoudí, že se nikdo nedívá, a spojení
  po minutě ukončí (`KickForNoClientConnection`); zprávy na kanálu `message` čte a odpovídá na ně
  `XboxMessages` (co neobsloužíme, dostane „Unhandled“/„ReceiverCancel“, ať na odpověď nečekají).
  Streamovací token se při 401/403 obnoví a požadavek zopakuje (`XboxAccount.renewStream`).
  Snímky I420 jdou do `StreamReader.feedI420` jen tehdy, když o ně čtečka stojí – dál
  stejná cesta jako u PS5 (`grab` → `Crop`, `FrameSignature`, OCR, překlad). Televize dekóduje na
  GPU, telefon do paměti. Opakování řídí `XboxRetry` (core, s testy); stav je `Ps5.Link`, takže
  `LinkStatus`, stránka Domů i hlášení fungují stejně (texty `xb_ls_…`). Na skutečném Xboxu
  tahle zpětná vazba a obnova tokenu zatím neověřené.
- Logika bez Androidu je v `core/Xbox.kt` s testy (`XboxTest.kt`). Knihovna WebRTC
  (`io.getstream:stream-webrtc-android`) přidává do APK asi 10 MB (jen ARM); místní kontrola ji
  překládá proti `classes.jar` z .aar (`ci/mistni-kontrola/build.gradle.kts`).
- Emulátor Xbox nemá: zkouška ukáže jen stránku Xboxu (snímek `11b_stranka_xbox`). Přihlášení,
  spojení a čtení z obrazu ze skutečného Xboxu **umí ověřit jen uživatel**. Převzato podle PlayCaption
  (AGPL-3.0; ten podle xbox-xcloud-player, MIT, a XStreaming, AGPL-3.0) – hlavičky souborů zachovej.

## DeepL – Kvalita překladu (od 0.97.28 v ostrém provozu, výchozí vypnuto do vložení klíče)

- **Kde:** Titulky → rozbalovací skupina **Kvalita překladu** hned pod jazykem překladu (`subtitlesPage`, `lookOpen ==
  LOOK_DEEPL`, tag `lkdl`) s celou `DeeplSection` (zapnout/vypnout, soukromí, klíč, ověření, zbývající znaky, měření,
  odebrat) a krok průvodce **Lepší překlad (DeepL)** (`W_TRANSLATE`, `build(content, wizard = true)`). Do 0.97.27 byl
  DeepL pokus v ⚙ Nastavení → Experimentální – tam už není (zůstala Zkušební verze a Ladění a hlášení se záznamem
  v síti). Řádek jazyka na Domů, řádek Režim překladu (⚙ → Čtení a překlad) a Nápověda kap. 4 vedou do Titulků
  s otevřenou skupinou (`openQuality()`). Jedna instance `deeplSection()` (drží rozepsaný klíč).
- **Cesta ke klíči ve třech krocích** (`keySteps`): televize = od 0.97.57 **jen jeden čtvereček** (`phoneQr`) na stránku
  z televize (`DeeplPage` – v telefonu provede 1. ceníkem na záložce API `Deepl.SIGNUP_URL`, 2. stránkou s klíči `KEYS_URL`
  i 3. vložením, s dlouhým vysvětlením `dl_howto`), vedle něj krátké body `dl_tv_scan`, `dl_tv_1`–`3`, `dl_tv_where`,
  „⏳ Čekám na klíč z telefonu…“ a malé „Napsat klíč ručně“ (`manual`). Do 0.97.56 tři čtverečky vedle sebe a odstavec –
  majitel: „těžko říct k čemu“. `Deepl.SIGNUP_URL` = `https://www.deepl.com/pro#api` (od 0.97.61; tlačítko u Developeru
  nepřihlášeného pošle na registraci, přihlášenému – i s účtem jen pro Translator – přidá plán k účtu. Přímá registrace
  `/signup?…&productId=api-developer` přihlášeného přesměruje do Translatoru, proto ne; ověřeno majitelem 21. 11. 2026). Telefon / tablet (ne
  `Prefs.isTv`) = tlačítka Otevřít DeepL / Otevřít stránku s klíčem a políčko. Stránka pro telefon má stejné 3 kroky.
  První uložený klíč DeepL rovnou zapne (`store`). Průvodce: bez klíče hlavní tlačítko „Přeskočit – zůstat u offline
  překladu“ (`wnext`) a Zpět (bez Přeskočit průvodce), s klíčem „✔ DeepL je nastavený“ a Pokračovat.
- **DeepL API (ověřeno na deepl.com 20. 11. 2026):** registrace API = plán **Developer** („Test out the API for free“,
  1 000 000 znaků **celkem, bez měsíčního obnovení**, pak Growth); stránka s klíči = záložka „API Keys & Limits“, celý
  klíč se nezobrazuje, kopíruje se ikonou. Jestli registrace chce platební kartu, se z webu ověřit nedalo (texty proto
  jen „kdyby chtěl kartu, plán nic nestojí“). Klíč Developer nemusí končit `:fx` – texty to netvrdí; `Deepl.host` podle
  `:fx` dál volí api-free / api. Odkazy jsou přímo v APK (ne přes `pomoc/`) – když je DeepL přesune, oprav
  `SIGNUP_URL` / `KEYS_URL` i `pomoc/deepl.md`.
- Vypnuté = služba se chová přesně jako dřív (žádný dotaz do internetu), zkouška v emulátoru se tím nemění.
- **Klíč patří jen hráči.** V repozitáři, testech, APK ani CI žádný klíč není a nesmí být (ani náš
  „sdílený“). Ukládá se zašifrovaný přes Android Keystore (`DeeplKey`, stejný postup jako `LgVault`),
  nikam se nevypisuje; `Redact` maže tvar klíče DeepL (UUID s `:fx`) a `DiagReport.secrets` i uložený
  klíč. Majitel má pro ruční zkoušku klíč mimo repozitář v `%USERPROFILE%\.recaption\deepl.txt` –
  nikam ho nekopíruj ani nevypisuj; jen pár ručních dotazů, žádné opakované zkoušení.
- Vložení klíče z telefonu: stejný server jako přihlášení (`LoginServer.forPage` + `LoginServer.Custom`,
  jednorázový klíč ve čtverečku), stránka `DeeplPage` v `core/Deepl.kt` (kroky 1–3, odkazy na DeepL na nové
  kartě, políčko pro vložení – schránku stránka číst nesmí). Běží jen, dokud je vidět její čtvereček: v Titulkách
  s otevřenou skupinou Kvalita překladu nebo v kroku průvodce (podmínka `deeplUi?.stopPage()` v `render()`).
  Na televizi bez klíče se čtvereček ukáže sám – dlouhý klíč se ovladačem psát nedá; ruční políčko je až po
  „Napsat klíč ručně“ (`DeeplSection.manual`).
- Se zapnutým DeepL ukazuje i karta „Režim překladu“ (Nastavení → Čtení a překlad) „Offline + DeepL“
  (`dl_mode_on`), zapnutý bez klíče `dl_mode_idle` – dřív tam stálo jen „Offline“ a mátlo to.
- **Od 0.97.2 se ukazuje vždy jen jedna varianta** (`deeplThenShow`): počká se na DeepL (nejvýš
  `Deepl.WAIT_MS` = 2,5 s) a ukáže se jeho překlad; offline jen když DeepL nepřijde včas / selže
  (`DEEPL|…|deepl / timeout-offline / error-…-offline / stale / hidden`). **Titulek mezitím zmizel (`HIDE`) = nic**
  (`hiddenSeq`, `swap=hidden`): dřív pozdní odpověď po `HIDE` prošla `present` → `ShowQueue.submit` / `hideAt = 0` zrušily
  schování a překlad visel do další repliky (záznam testera v soukromém repu). Výměna offline → DeepL v pruhu
  vypadala jako nová věta a majitel ji nechtěl. Následující popis výměny platí do 0.97.1:
- Překlad (`TranslatorService` → `improveWithDeepl`): offline překlad se ukáže hned, souběžně jde
  `POST /v2/translate` (původní anglický text bez obratů ze slovníku, jména ze slovníku ve značce `<k>` s
  `ignore_tags`, `formality=prefer_less`). Odpověď do 1,5 s, jiná a k témuž zobrazenému řádku
  (`SubtitleTracker.revision` + pořadí zobrazení `showSeq`) pruh potichu vymění, i v televizi LG.
  Čeština dál prochází `Natural.czech` (DeepL v češtině tykání nastavit neumí a občas vyká).
  Vlastní paměť výsledků DeepL (jen v paměti procesu), offline paměť se nemíchá. Chyby (403, 456,
  429, síť) → offline a přestávka (`DeeplBackoff`). Rozhodování, čtení odpovědí a přestávky jsou
  v `core/Deepl.kt` s testy (`DeeplTest`). Měření v logu: `TP_TEST` řádky `DEEPL|ms=…|swap=yes/no/late/stale/cache|chars=…`
  (bez textu a klíče); souhrn v Nastavení a v hlášení o stavu.
- Limit znaků se nebere napevno: ukazuje se to, co vrátí `/v2/usage` (účet majitele má 1 000 000 – plán Developer).
  Texty neříkají „tento měsíc“ (`dl_usage`, `dl_p_quota`) – u plánu Developer se limit neobnovuje.
- Texty `dl_…` a `wz_tr_…` jsou v `core/I18nDeepl.kt` (`DEEPL_S`, `DEEPL_X`); kroky `dl_s1`–`dl_s3`, `dl_s3_here`.

## Záznam ze zařízení (pro Claude na PC majitele)

Když je potřeba vidět, co se na zařízení majitele děje (Android i aplikace v televizi LG/Samsung):

1. Majitel na zařízení s Androidem zapne Nastavení → úplně dole Experimentální → **„Zpřístupnit záznam v domácí síti“**
   (od 0.97.15 ve skupině Ladění a hlášení, `logShareRow()` volané ze `settingsDiag()`; výchozí vypnuto, samo se vypne po 2 hodinách) a řekne adresu,
   která se ukáže pod řádkem (i nahoře na obrazovce hlášení), např. `http://192.168.1.23:8789/zaznam?k=ab3k7m`.
2. Claude ji stáhne: `.\ci\stahni-zaznam.ps1 http://192.168.1.23:8789/zaznam?k=ab3k7m`
   (nebo `… 192.168.1.23 ab3k7m`, s `-Json` po oddílech). Uloží se do `zaznamy\zaznam-<čas>.txt`
   a `zaznamy\posledni.txt` – složka je v `.gitignore`, záznamy do repozitáře nepatří.
   403 = špatný kód (po 30 špatných se server zavře), 404 / nedostupné = vypnuto nebo vypršelo.

Obsah = celé hlášení o stavu (`DiagReport.build` → `parts`): aplikace a zařízení, spojení, pády
(`pady.txt`), záznam událostí `udalosti.txt`, události z paměti, spojení s PlayStationem a záznam
z televize. Vše prochází `Redact` s `DiagReport.secrets` (i v JSON). Server (`core/LogShare.kt`:
`LogShare` rozhodování s testy `LogShareTest`, `LogServer` sockety podle `LoginServer`) umí jen
`GET /zaznam?k=…` a `/zaznam.json?k=…` (a `/hlaseni`, `/snimek.jpg`, `/davky`, `/davka` – viz Ladění profilu ze hry), odpovídá jen loopbacku a privátním adresám, nic nepřijímá.
Konec platnosti a kód jsou uložené (`NetLogShare`, vlastní SharedPreferences `zaznam_sit`), takže po
pádu a novém spuštění běží dál se stejným kódem, ale jen do konce 2 hodin.

Záznam z televize: `app.js` drží posledních 300 řádků (`zapis`: start s platformou a verzí, televize,
obrazovky, hledání v síti, připojení/ztráta, u titulků jen id a délka – **nikdy text**, `window.onerror`;
opakované hledání se sloučí do počtu). Po připojení pošle neodeslané řádky zprávou
`{"typ":"zaznam","radky":[…]}` (po 25) a dál každých 10 s. `TvSender` je předá `core/TvLog.kt` →
`filesDir/tv-zaznam.txt` (nejvýš 60 000 znaků). V hlášení je oddíl Televize (posledních 25 řádků).
Záznam z televize umí aplikace v televizi od verze 1.2.0.

Bez sítě: obrazovka hlášení má tlačítka **Kopírovat** (schránka) a **Sdílet** (soubor přes
`ReportProvider`, jediný soubor v `cacheDir/sdileni`, jen ke čtení).

**Poslat vývojáři (od 0.97.68, přání majitele – testeři her, které majitel nemá):** obrazovka hlášení (tlačítko `diagsend`) a ⚙ →
Experimentální → Ladění a hlášení (řádek `rsend`) → `ReportSender` (dialog: Jen hlášení / Hlášení + dávka snímků → potvrzení → průběh →
kód `K7M2-QX9P`, Kopírovat/Sdílet). **Zkratka na Domů** (přání majitele: „dělám to pořád dokola“): ⚙ → Experimentální → Ladění a hlášení
„Snímky na Domů“ (`Prefs.snapHome`, klíč `snimky_domu`, výchozí vypnuto, texty `snd_home_…`) → na Domů úplně dole řádek „Snímky“ / zeleně „Snímky · nahrávám (n)“ (tag `hsnap`, počet v tickeru) a pod ním
tlačítka (`snapHomeRow`, od 0.97.127 jako přehrávač): ▶ / ■ (`mediaButton`, zeleně aktuální stav) = `SnapRecorder.toggle` (tagy `hsnap_rec`, `hsnap_stop`) (jen při běžícím překladu, počet snímků v tickeru `tickSnap`), Odeslat = `ReportSender.sendLatest`
(nejnovější hotová dávka + hlášení bez volby a potvrzení, pak kód; hotové dávky podle `SnapRecorder.summary`). ZIP = `hlaseni.txt` (jako Kopírovat, přes `Redact`), `info.json`, případně `snimky/` (velká dávka se
zmenší na 960 px / JPEG 70 a vezme se každý n-tý snímek, `index.txt` celý). Logika `core/ReportUpload.kt` (testy `ReportUploadTest`), texty `snd_…`
v `core/I18nSend.kt` (pozor, `rs_…` jsou texty nastavení z telefonu v `I18nRemote.kt`). Adresa serveru se čte z `pomoc/hlaseni.json` (`url` prázdné = „Odesílání · zatím nenastavené“) – nová adresa nepotřebuje APK.
Server = **schránka na Cloudflare** `server/hlaseni/` (Worker + R2, návod k nasazení `server/hlaseni/CTI-ME.md`, zkouška `node --test
server/hlaseni/test/server.test.mjs`): `POST /v1/nahrat` bez klíče (≤ 45 MB, 30/h z adresy, 300 balíků / 3 GB denně, mazání po 30 dnech),
čtení/seznam/mazání jen s `CTENI_KLIC` (jen v Cloudflare a u majitele v `%USERPROFILE%\.recaption\hlaseni.txt` – nikam nekopírovat). Stažení:
`ci/stahni-hlaseni.ps1 <kód>` / `ci/stahni-hlaseni.sh <kód>` (`-Seznam`) → `zaznamy/hlaseni-<kód>/`. V cloudovém sezení: host schránky povolit
v síti prostředí a klíč uložit jako síťový tajný údaj (`request_credential`). Po rozboru balík smazat. Na skutečném serveru zatím neověřeno.
**Zařízení/TV/hra i jako hlavička, ne jen v `info.json` (24. 11. 2026, přání majitele – stránka `/hlaseni` v soukromém repu je teď
seznam hlášení od testerů, zběžně rozparsovaný podle záložek hlášení/repliky/snímky):** `ReportSender.upload` i
`LineCollector.post` (repliky mají vlastní, nezávislou cestu odeslání – na první verzi stránky chyběly, protože jsem je zapomněl,
25. 11. 2026) posílají navíc nepovinné `X-ReCaption-Zarizeni/-TypTv/-Tv/-Hra` (stejné hodnoty jako v balíku, `ReportUpload.HEADER_…`,
ořízlé přes `headerSafe` – bez konců řádků, nejvýš `HEADER_MAX`); server (soukromý repo) si je uloží k balíku bez otevírání ZIPu.
Rozkliknutí řádku na stránce ZIP přece jen otevře (`/v1/hlaseni/<kod>/info`, jen `info.json`). **Nová cesta odeslání = nezapomeň na
obě** (`ReportSender` i `LineCollector`), nový sloupec/hlavičku přidávej na obou koncích (appka i server) najednou.

**Podpořit ReCaption ☕ (od 0.97.84, přání majitele; od 1.00 na Domů a v TV):** dobrovolný příspěvek převodem (QR platba), nic se jím neodemyká
(skrytá licence `SHOW_LICENSE` s ním nesouvisí – dar za odemčení by byl prodej). Na Domů úplně dole šedý řádek `hsupport` (`openSupport`: telefon
= prohlížeč, TV / box = okno se čtverečkem); v ⚙ → Experimentální už není. Adresa `HelpLinks.SUPPORT` = `pomoc/kafe.html` (přesměrování – cíl
se mění jen tam, teď `podporit.html`). Stránka `pomoc/podporit.md` = QR platba převodem pro Česko (SPD) a Slovensko
(PAY by square) na účty majitele u Air Bank – Česko korunový 2329017034/3030, Slovensko a EU účet v eurech 2329017042/3030 (eura bez převodu; IBANy jsou na stránce veřejně, přání majitele), obrázky `pomoc/obrazky/qr-platba-cz.png` / `-sk.png` vyrábí
`ci/qr-platba.py` (ručně, `pip install segno`). Ko-fi ani jiné platby kartou ne – poplatky za platbu (přání majitele 23. 11. 2026). **Aplikace v TV 1.5.39:** zpráva `{"typ":"podpora","obrazek":"data:image/png;base64,…"}`
(`TvSender.support`, složí ji `App.onCreate` z `QrImage`, každá TV ji dostane po připojení) → `#kafe` (`kafeKresli` v `app.js`) na úvodní
obrazovce vpravo dole a při hře vlevo od nabídky OK; při hře bez nabídky nic. Bez spojení s Androidem čtvereček není (testeři obchodu LG ho
neuvidí – odkaz na platbu by mohl schválení ohrozit). Texty `sup_row`, `sup_qr` v `core/I18nUi.kt`, v TV `kafe`, `kafe_pod`.

**Odeslat hned po Stop záznamu snímků (hlášení 22VF-GGWU, 24. 11. 2026):** v 1.0.00 to byl plný strop oken (`OverlayWindows`, opraveno 1.0.1). Navíc: Odeslat na Domů je aktivní hned po Stop (`SnapRecorder.justStopped`, dřív šedé do přepočtu souhrnu), odeslání počká na dopsání dávky (`SnapRecorder.settle`) a odesílání, které 45 s nepošle ani bajt, se vzdá s chybou sítě (`STALL_MS` v `ReportSender.upload`).
**Upozornění v TV na zpomalené čtení (24. 11. 2026, přání majitele):** `core/ReadSlowdown` (testy `ReadSlowdownTest`) – vzorek každé 2 s v `tvStatusTick` (`sampleReadSpeed`: snímky zpracované smyčkou čtení proti obvyklé rychlosti; < 35 % třikrát po sobě = pomalé, ≥ 60 % dvakrát = v pořádku) → `TranslatorService.readSlow` → oranžový řádek v `TvStatus.rows` („Telefon · čtení je pomalé – otevři v telefonu ReCaption“ `tvs_slow_bg`, když aplikace není vidět; jinak `tvs_slow`). Jde existující zprávou `stav`, aplikace v TV se neměnila. Záznam `SLOW|…`. Telefon Samsung (`NetInfo.isSamsungPhone`) a aplikace není vidět: za řádek se doplní „· Samsung: Nikdy neuspávané aplikace“ (`tvs_slow_ss`); v ⚙ Obecné pod „Povolit běh na pozadí“ je jen na Samsungu řádek „Nikdy neuspávat (Samsung)“ (`h_never_sleep`, tag `neversleep`, `openSamsungNeverSleep` – zkouší obrazovky Péče o zařízení `com.samsung.android.lool`/`sm`, pak obecné šetření baterie, pak Nastavení). Návod `pomoc/telefon.md#pozadi` (cs i en).
**Mazání snímků (od 0.97.104, přání majitele):** ⚙ → Experimentální → Ladění a hlášení → „Smazat snímky“ (jen když nějaké dávky jsou, s potvrzením,
`SnapRecorder.delete`, nahrávaná dávka nikdy) a dávka úspěšně odeslaná přes Poslat vývojáři se z telefonu smaže sama (`snd_deleted`). Po Zkopírovat
se okno s kódem zavře.
**Karta upozornění na telefonu ve hře (od 0.97.104, „Reminders · 4 Notifications“):** `core/PhoneCard` (světlé písmo, testy `PhoneCardTest`) pod volbou
Překlad telefonu, když `PhoneScreen.read` nic nenajde; pevné překlady názvu z `CornerSpec`, „N oznámení“.
**Uložit snímky do telefonu (přání majitele 21. 11. 2026, od 0.97.74):** ⚙ → Experimentální → Ladění a hlášení → řádek
`snapsave` → `SnapSaver` (seznam hotových dávek `ReportUpload.offered` → Sdílet jako ZIP přes `ReportProvider` – druhý soubor
`ReCaption-snimky.zip` v `cacheDir/sdileni`, `application/zip`; Uložit ZIP do `Download/ReCaption` a snímky do `Pictures/ReCaption/<dávka>`
přes MediaStore, jen Android 10+ – bez oprávnění k úložišti). Texty `snap_…` v `core/I18nSend.kt`. Záznam `SNAPSAVE|…`.

**Hlášení od jiných hráčů (od 0.90):** tlačítko **Do telefonu** na obrazovce hlášení zapne záznam v síti
(když neběží) a ukáže čtvereček na `GET /hlaseni?k=…` – stránka pro telefon (i iPhone) s tlačítkem ke
stažení (`/hlaseni.txt`, `Content-Disposition: attachment`) a textem k označení. Hráč soubor pošle chatem
nebo e-mailem. Záměrně bez e-mailové služby (Resend apod.): klíč by musel být v APK a šel by zneužít.
Pro rozbor odpojování zapisuje služba i `NET|` (síť ztracena/připojena), `SCREEN|` (televize se uspává),
`MEM|` (dochází paměť), `STOP|tlačítkem v notifikaci` a `PS5|quit … | trvalo=…s | síť=… | pokus=…`;
seznam ukončení ze strany PS5 je v hlášení („PlayStation ukončil spojení“) a důvod na Domů (`ls_ended`).

**Sbírání replik ze hry (od 0.97.139, přání majitele: u všech hráčů, návrh `navrhy/preklad-cele-hry.md`):** ⚙ → Experimentální „Sbírání replik ze hry“
(řádek `lines`, `linesRow` v MainActivity), zapnutí jen souhlasem (`ln_consent` v `core/I18nLines.kt` – **musí přesně odpovídat tomu, co se
ukládá a posílá**), `Prefs.collectLines` = čas souhlasu (klíč `repliky_souhlas` ve vlastním nastavení zařízení, `own`). **Bez souhlasu se nesbírá nic**
(GDPR – majitel chtěl výchozí zapnuto, zvolil dotaz u všech): `startFlow` se při Spustit s profilem hry jednou zeptá (`linesConsent(ask = true)`,
předvybrané Souhlasím, „Nesbírat“ = `repliky_dotaz`, už se neptá; Zpět = zeptá se příště; ne zároveň s dotazem na baterii / displej).
Jen s profilem hry (ne univerzální), ne v ukázce, bez pokynů hry. Služba po ukázání titulku (`finish` → `collectLines`) → `LineCollector.add`
(jen paměť; uložení `filesDir/repliky/repliky.tsv` a odeslání na vlastním vlákně). Logika `core/LineBank.kt` (testy `LineBankTest`): jedna replika
jednou s počtem, rozhovor = pauza > 8 s, nejvýš 30 000, posílají se jen nová čtení; samo při ≥ 150 čteních nebo po dni, po chybě za 30 min.
Na schránku „Poslat vývojáři“ jako druh `repliky` (`repliky.tsv` + `info.json`). **Server má od 0.97.139 pro repliky vlastní meze** (`REPLIKY_…`
v `server/hlaseni/src/index.js`: ≤ 512 kB, 12/h z adresy, 20 000 / 200 MB denně, vlastní počítadla – hlášení testerů nikdy nezablokují) a `/v1/stav`
vrací `repliky: true`; aplikace si to zjistí před odesláním (`Prefs.linesServer`) – **dokud server není znovu nasazený, posílá nejvýš 1× denně**.
Stažení a sloučení `python3 ci/repliky.py` → `zaznamy/repliky/`. Vypnout a smazat = souhlas pryč a soubor smazán. Na skutečné hře neověřeno.
**Od 1.00 i ostatní texty hry (přání majitele – offline balíčky překladu):** položka má **druh** (`LineBank.KINDS`: `replika`, `pokyn` pod titulky,
`ukol`, `menu`, `telefon`, `tip`, `predmet`, `roh`) – v `repliky.tsv` v zařízení i v odesílaném souboru **poslední sloupec** (`…\trozhovor\tporadi\tdruh`);
starý řádek bez druhu = replika. Stejný text jiného druhu = jiná položka; ostatní druhy bez rozhovoru (`rozhovor` 0), text až 600 znaků, vlastní strop
`MAX_OTHER` 10 000 (repliky dál 30 000 – menu je nevytlačí), při odesílání napřed repliky. Služba: `collectTexts(druh, originály)` u ukázání úkolu,
menu (seznam, popis, patička), telefonu, tipu, předmětů a rohu – počítá se jen text nový proti minulému ukázání téže části. Texty `ln_help` / `ln_on` / `ln_status`
mluví o „textech ze hry“ (jen texty hry, nic jiného). **`ci/repliky.py` v soukromém `ReCaption-data` je potřeba upravit, aby druh (8. sloupec) četl.**

## Návrhy na později (`navrhy/` – v soukromém `ReCaption-data`)

- `navrhy/preklad-menu.md` – překlad menu a UI hry (box s přeloženým menu vedle originálu, zvýraznění vybrané
  položky, tooltipy v Diablu IV). GTA VI hotovo (0.97.34–0.97.60), Diablo IV (úkol, menu, tooltipy) nezačato – čeká na snímky ze hry.
- `navrhy/preklad-cele-hry.md` – předem přeložená hra: repliky od hráčů se souhlasem (díl 1 v 0.97.139, dotaz u všech při Spustit), po vydání
  od všech (vlastní adresa na serveru, nabídka souhlasu), překlad po rozhovorech a paměť ke stažení s otisky místo textu.
- `navrhy/ctecka-pc-mac.md` – počítač (Windows, později Mac) jako čtečka pro TV LG/Samsung místo Androidu, hlavně pro
  hráče s iPhonem (přání majitele 21. 11. 2026). Díl 1 (režim „Čtečka pro TV“ v `pc/`) a díl 2 (balíček `ReCaption-PC.zip`, `pc.yml`) hotové, na Windows s PS5 neověřené;
  nabídka na webu „Začít s ReCaptionem“ (LG/Samsung + počítač/iPhone → `pomoc/pc.md`) se zveřejní z `main`. Mac (díl 3) napsaný, na skutečném Macu s PS5 neověřený.

## Stránky s pomocí (`pomoc/`)

**Od 1.00-rc2 je adresa pro hráče Cloudflare Pages `https://recaption.pages.dev`** (přání majitele 23. 11. 2026: v odkazech nemá být
GitHub; bezplatný plán, účet majitele veks@centrum.cz, projekt `recaption`). Stejné stránky skládá `pomoc.yml` (job `cloudflare`, Jekyll,
odkazy bez `/ReCaption/`) a přidá `cloudflare/_worker.js`: `/d/<kanál>/<soubor>` podá soubor z vydání na GitHubu (kanály latest, zkusebni,
pc, mac, mac-zkusebni, ios; jen bezpečné názvy souborů), krátké `/stahnout`, `/stahnout/pc`, `/stahnout/mac` (ne `/pc` a `/mac` – tam Pages zkracuje návody pc.html a mac.html). Aplikace bere aktualizace z `…/d/<kanál>/`
(`UpdateCheck.BASE` v `core/Support.kt`) a nápovědu z `HelpLinks.BASE`. Nahrání vyžaduje tajné údaje `CLOUDFLARE_API_TOKEN`
a `CLOUDFLARE_ACCOUNT_ID` v repu; bez nich je výsledek jen v artefaktu `cloudflare-stranky` (ručně `npx wrangler pages deploy <složka>
--project-name recaption --branch main`). GitHub Pages zatím běží dál jako záloha. Apps2Samsung umí jen GitHub (`SamsungSetup.SOURCE_URL`),
zdrojový kód je kvůli AGPL na GitHubu. Starší verze aplikace (do 1.00-rc1) chodí na GitHub – majitel je nepotřebuje.

Čtverečky a tlačítka v aplikaci (`core/HelpLinks.kt`) vedou od 0.97.30 na **GitHub Pages**
`https://adusa89.github.io/ReCaption/<stránka>.html` (zdroj `pomoc/*.md`, zveřejňuje `.github/workflows/pomoc.yml`
jen z `main`, Jekyll s `pomoc/_config.yml`), ne přímo na stránky LG, Sony, Samsungu: na každé stránce je hlavní odkaz,
záložní odkazy a co hledat. Do 0.97.29 vedly na github.com/…/blob/… – telefon s aplikací GitHub je otevřel v ní a ta chtěla
přihlášení (majitel 20. 11. 2026). Pages musí v repozitáři zapnout správce (Settings → Pages → Source: GitHub Actions).
Když výrobce stránku přesune, oprav odkaz v `pomoc/*.md` – aplikace se měnit nemusí. Commit s `[skip ci]` přeskočí
i zveřejnění stránek: pak spusť „Stránky s pomocí“ ručně v záložce Actions (z `main`).
**Šablona průvodce (od 24. 11. 2026, přání majitele – web a aplikace se rozjížděly):** kroky, podkroky (pevné pořadí), texty, klipy,
kde se co dělá a podmínky (TV, konzole, čím se nastavuje `na`, cesta do Android TV `in`, DeepL, na čem běží) jsou jen v `core/Pruvodce.kt`.
Web `pomoc/pruvodce.html` je jen vykreslovač dat `pomoc/pruvodce-data.js`, která se ze šablony generují (`gradlew -p ci/mistni-kontrola test -Ppruvodce`,
soubor se commituje). `PruvodceTest` hlídá soulad souboru se šablonou a že každý klip z `videa/kroky.json` patří k podkroku šablony;
`node ci/pruvodce-web.test.js` (i v `pomoc.yml` před nasazením) projde všechny kombinace voleb. Texty kroků do `pruvodce.html` nepsat.
**Úvodní stránka** `pomoc/index.html` (od 1.00-rc6, samostatné HTML: výběr TV jako v aplikaci → kroky s klipy z `videa/kroky.json` a celé video,
stažení přes `/stahnout`, `/stahnout/pc`, `/stahnout/mac`); průvodce podle TV je `zacit.md`. Všechny návody mají tmavý vzhled z `pomoc/_layouts/default.html` (ikona
`obrazky/ikona.svg` = `ic_logo.xml`, ☕ Podpořit v liště) – nic na stránkách nemá být černobílé (přání majitele).
**Videonávody** (od 1.00-rc14 jen krátké klipy `pomoc/videa/krok-…-<cs|en>.mp4` + `kroky.json`; celá videa zrušená, `videa.html` přesměruje na `zacit.html`) se kreslí ze `ci/videonavody/studio.html`
(postup v `ci/videonavody/CTI-ME.md`) – po změně obrazovek průvodce, spárování nebo DeepL je přegenerovat.
**Jen česky (od 24. 11. 2026, přání majitele: angličtina se dotočí později):** `kroky.json` může mít vedle „klipy“ pole
`"jen_cs": ["krok-…", …]` – tyto klipy se ukazují česky ve všech jazycích: aplikace (`VideoSteps.parseCsOnly` → `VideoStepList.csOnly`
→ `VideoGuide.video/poster(…, csOnly)`), web `pruvodce.html` (`JENCS`) a `en/zacit.md` (`vj()`). Po dotočení angličtiny id ze seznamu
smazat. Starší aplikace pole přeskočí a chtějí `-en.mp4` (stará anglická verze, u nových id chyba přehrávače).
**Id klipů** (`core/VideoSteps`, web `klip()` v `pomoc/pruvodce.html`): `krok-<krok>[-<podkrok>][-<tv>][-<konzole>]` v tomto pořadí
(tv `android`/`lg`/`samsung`, konzole `ps`/`xbox`, jen malá písmena, nejvýš 4 části za `krok-`; podkrok nikdy není slovo TV ani konzole;
`-lg`/`-samsung` v id = vzhled telefonu pro TV ve studiu). Vybírá se nejpřesnější: klip podkroku (-tv-konzole, -tv, -konzole, bez přípony),
pak klip kroku; jen id, které je v `kroky.json`. Kroky `konzole`, `pripojeni`, `hotovo` ukazují konzoli: id bez `-xbox` = PS5, s Xboxem
jen id končící `-xbox`. Plánované podkroky (aplikace / web): `instalace-downloader`, `-povolit`, `-usb`, `-telefon` (jen web, ATV cesty),
`zarizeni-dev-lg`, `zarizeni-key-lg`, `zarizeni-dev-samsung`, `zarizeni-apps-samsung` (web), `konzole-psn` (web 🔵 účet), `preklad-ucet`
(web), `preklad-klic` (W_TRANSLATE / web DeepL klíč), `pripojeni-ucet`, `-prihlaseni`, `-rp`, `-cislo` (W_PAIR podle `pairStep` / web),
`hotovo-hrat` (Hotovo / web Hrát). `krok-konzole-xbox` = klip kroku pro Xbox (web u podkroku Xbox a účet Microsoft).
Web podkrok bez vlastního klipu video nemá (klip kroku nese jeden podkrok); aplikace se vrací ke klipu kroku. Starší APK (regex do rc25)
id se 4 částmi vynechá a podkroky nehledá. `krok-zarizeni-android` se už nepoužívá (krok Zařízení na Android TV se přeskakuje).
Kotvy jsou `<a id="…">` (např. `lg.md#ucet`, `ps5.md#ucet`). Stránky jsou česky s anglickým shrnutím, pro iPhone i Android.
Odkazy občas ověř: `grep -oh "https://[^) ]*" pomoc/*.md | sort -u` a `curl -L -o /dev/null -w "%{http_code}"`
(microsoft.com/link vrací robotům 403, v prohlížeči funguje).

## Texty aplikace a jazyky

Aplikace má dvě na sobě nezávislá nastavení jazyka (`core/Lang.kt`):

- **jazyk překladu** (`Prefs.lang`, stránka Titulky a průvodce) – do čeho se titulky překládají;
  jen `Lang.targets`, tedy bez angličtiny,
- **jazyk aplikace** (`Prefs.uiLang`, Nastavení a pomocník v telefonu) – nabídky a nápověda;
  všech jedenáct jazyků včetně angličtiny (od 24. 11. 2026 i hindština). Texty na obrazovce ber vždy přes `tx()` / `Prefs.uiLang`,
  nikdy podle jazyka překladu.
- **z čeho se překládá** („Překládat z“, `Prefs.sourceLang`, klíč `src_lang`, `core/SourceLang.kt`, testy `SourceLangTest`;
  průvodce krok Jazyky a Titulky pod jazykem překladu) – jazyk titulků ve hře, výchozí angličtina (bez klíče = jako dřív).
  Jen latinka (OCR ML Kit), kterou umí ML Kit Translate i DeepL jako zdroj; stejný jako jazyk překladu = angličtina
  (`SourceLang.effective`). Překladač ji má v `OfflineTranslator.source` (nastavuje `App`/`Prefs`, balíček pro dvojici).
  Anglické úpravy (`OfflinePrep.standardize` + obraty ze slovníku, `QuickWords`, `Natural`, rod podle originálu, „i“ → „I“
  v `normalizeCase`, `looksForeign` na diakritiku) běží jen u angličtiny – nový anglický kousek hlídej `SourceLang.english`.

Kdo si jazyk aplikace nezvolil, má ji v jazyce překladu (tak to bylo do 0.63); nová instalace se
řídí jazykem zařízení a pro jazyk, který aplikace neumí, je anglicky. Další jazyk překladu, pro
který aplikace texty nemá, se přidá jen do `Lang.targets`.

**Jazyky jen pro překlad (větev `preklad-jazyky-2`, zatím ne do vydání):** rumunština, chorvatština, srbština (latinkou), slovinština,
bulharština, makedonština, albánština, litevština, lotyština, estonština, řečtina, turečtina = položky `Lang` za angličtinou s `ui = false`.
Nejsou v nabídce jazyka aplikace (`Lang.uiLangs`, `Lang.ui()`, `next()`), `I18n.t` pro ně dává angličtinu, `Prefs.uiLang` je nikdy nevrátí;
testy textů procházejí `Lang.uiLangs`, ne `Lang.values()`. Offline překlad podle `Lang.mlkit` (kód ML Kit) – **srbštinu ML Kit nemá, jede přes
chorvatský model** (`SR.mlkit = "hr"`, v nabídce „Srpski (latinica)“). **DeepL** (`Deepl.target`, cílové jazyky API) od 24. 11. 2026 umí všechny jazyky
překladu včetně HI, HR, MK, SQ a vlny 3 (ověřeno na /v2/languages?type=target účtu majitele); jen SR ne (DeepL píše cyrilicí) → `null` = jen offline, `DeeplTranslator.active`/`activeKey` DeepL nepoužijí a Domů / Režim překladu ukazují offline
(ne „zapnuto bez klíče“). **Pevné překlady** (`QuickWords`, `BuiltinPhrases`, `Captions.word`) mají jen jazyky v `Lang.withFixed`
(`FixedTranslationsTest` u nich hlídá stejné klíče, ostatní nesmí mít tabulku vůbec) – jazyk přidat do `withFixed` až s daty ve všech třech.
PC aplikace nabízí zatím jen jazyky s `ui` (modely Argos pro nové jazyky neověřené). Zkouška na Androidu 13 (krok 6d) je překládá
v dalších dávkách, jen když zkouška ještě nepřekročila čas. **Větev `jazyky-vychod`** přidává vlnu 1 (NL, SV, DA, NB – ML Kit `no`, FI, ID, VI;
s DeepL), vlnu 2 (HE, TH; s DeepL, zprava doleva a písma v TV) a vlnu 3 (CA, TL, MS, IS, SW, AF; od 24. 11. 2026 i s DeepL) –
všechny s pevnými překlady, bez kontroly rodilým mluvčím.

**Hindština (od 24. 11. 2026, přání majitele) je plný jazyk aplikace i překladu** (`Lang.HI`, „हिन्दी“, hned za italštinou):
všechny texty aplikace má v **jednom** souboru `core/I18nHi.kt` (`I18N_HI` – obecné i ty, které ostatní jazyky mají v mapách `…_X`),
průvodce v `core/PruvodcePrekladyHi.kt`. Překlad je strojový, bez kontroly rodilým mluvčím; názvy značek a položek cizích nabídek
(PS5, TV, DeepL) zůstávají latinkou. Offline překlad ML Kit `hi`; **DeepL zatím ne** (`Deepl.target` = null – u DeepL neověřeno, jestli
hindštinu umí jako stálý cílový jazyk; až ano, stačí doplnit kód). Pevné překlady (`Lang.withFixed`) nemá – data jsou v soukromém repu.
Písmo dévanágarí pro TV LG/Samsung: `fonty/NotoSansDevanagari-*.woff` (OFL, aplikace v TV 1.5.45). Na telefonu, TV ani PC neověřeno.

Každý nový text musí být ve všech jedenácti jazycích (hindský do `core/I18nHi.kt`), jinak neprojde test `AllLanguagesTest`:

- obecné texty: čeština + slovenština v `core/I18n.kt`, angličtina v `core/I18nEn.kt`, ostatní
  v `core/I18nPl.kt`, `I18nDe.kt`, `I18nHu.kt`, `I18nUk.kt`, `I18nEs.kt`, `I18nFr.kt`, `I18nIt.kt`;
- texty kolem PlayStationu: `core/I18nPs5.kt` (`PS5_S` pro češtinu a slovenštinu, `PS5_X` pro ostatní
  včetně angličtiny);
- texty přihlášení přes telefon (kroky `pa_…`, stránka z televize `wp_…`, pomocník v telefonu
  `hp_…`): `core/I18nLogin.kt` (`LOGIN_S`, `LOGIN_X`);
- stav spojení (`ls_…`, `lv_…`, logika v `core/LinkStatus.kt`), hlášení o stavu (`dg_…`) a upozornění
  na pád (`cr_…`): `core/I18nDiag.kt` (`DIAG_S`, `DIAG_X`).
- texty nahrání do televize LG (`lg_…`; chyby `lg_e_<LgError>`, kroky `lg_p_<LgPhase>`):
  `core/I18nLg.kt` (`LG_S`, `LG_X`).
- texty nahrání do Android TV z telefonu (`at_…`; chyby `at_e_<AtvError>`, kroky `at_p_<AtvPhase>`):
  `core/I18nAtv.kt` (`ATV_S`, `ATV_X`).
- texty Xboxu a volby konzole (`c_…`, `xb_…`; chyby `xb_e_<XboxFail>`): `core/I18nXbox.kt`
  (`XBOX_S`, `XBOX_X`). Text, který se u Xboxu liší jen jménem konzole, má klíč `xb_<klíč PS>`
  (např. `xb_st_failed`, `xb_ls_image`) a bere se přes `Console.key()` / `ct()` v MainActivity.
- typ televize (`tv_…`, společné `s_tv_desc`, `st_tv_…`, `hp_lg_title`/`hp_lg_text`) a průvodce
  Samsung (`ss_…`): `core/I18nSamsung.kt` (`SS_S`, `SS_X`).
- texty DeepL (`dl_…`, krok průvodce `wz_tr_…`; důvody `dl_p_<Deepl.Problem>`): `core/I18nDeepl.kt` (`DEEPL_S`, `DEEPL_X`).
- texty rozložení od přestavby (průvodce `wz_dev…`, `dev_…`, souhrnný řádek a upozornění na Domů
  `sum_…`, `w_…`, části Nastavení `s_setup`, `s_diag`): `core/I18nUi.kt` (`UI_S`, `UI_X`).
- záznam pro vývojáře, oddíl Televize a Kopírovat/Sdílet v hlášení (`zz_…`): `core/I18nZaznam.kt`
  (`ZAZNAM_S`, `ZAZNAM_X`).
- načtení údajů televize LG kamerou (`km_…`): `core/I18nKamera.kt` (`KAMERA_S`, `KAMERA_X`).
- odeslání hlášení vývojáři (`snd_…`): `core/I18nSend.kt` (`SEND_S`, `SEND_X`); `rs_…` = nastavení z telefonu (`I18nRemote.kt`).

Texty, které se přestaly používat, smaž ze všech jazyků. Když se mění postup pro uživatele,
uprav i `NAVOD.md` a stránku Nápověda v aplikaci.

Aplikace pro televizi LG (`webos/obchod`) a Samsung (`tizen/app`, týž `app.js`) má vlastní texty
česky a anglicky přímo v `app.js` a tlačítko Jazyk na úvodní obrazovce; do obchodu LG se podává
s anglickými podklady. Texty v televizi nejmenují značku (aplikace pro LG nemá zmiňovat Samsung).

## Kontrola před každým vydáním (přání majitele, 20. 11. 2026)

Než zvýšíš verzi a pošleš ji, projdi tohle a v odpovědi napiš, co bylo zkontrolováno a co ne:

1. **Obě zařízení**: stejné APK běží na Android TV i v telefonu (čtečka pro LG/Samsung). Co se změnilo na jednom,
   zkontroluj na druhém (`Prefs.isTv`, `Prefs.tvType`, `NetInfo.isHandheld`), a u LG/Samsungu i aplikaci v televizi.
2. **Výkon**: nic drahého v `render()`, tickeru MainActivity, `TvStatus`, ve čtení snímku (`TranslatorService.tick`,
   `StreamReader`) – žádné dešifrování klíčů, čtení souborů, překreslování celé stránky bez potřeby, nic navíc
   při každém snímku. Aplikace bývá otevřená celou hru (telefon).
3. **Vzhled**: krátké stavové řádky, rozbalovací skupiny, žádné vysvětlující věty mezi řádky, žádné tykání, „TV“.
4. **Texty** ve všech 11 jazycích, nepoužívané smazané (`AllLanguagesTest`), Nápověda a `NAVOD.md` podle nových cest.
5. **Testy**: `gradlew -p ci/mistni-kontrola test`, při změně `core` i `gradlew -p pc installDist`, při změně
   aplikace v televizi `node tizen/test/prijimac.test.js` a kopie do `tizen/app`. **Testy her** (soukromé repo,
   `gradlew -p <Recaption-data>/hry test -Pverejne=<toto repo>` – veřejný kód čtení a překladu na skutečných textech her)
   jen při změně logiky čtení/překladu (`core`, `TranslatorService`); u změn webu, workflow nebo vzhledu je nepouštěj.

## Zásady vzhledu (přání majitele, platí pro všechny obrazovky)

- **Stránky jsou věcné, hezké, bez balastu.** Domů ukazuje jen stav v krátkých bodech s tečkou (zelená = jede,
  oranžová = hráč musí něco udělat – a pak s tlačítkem, šedá = jen informace). Žádné vysvětlující věty,
  rady, varování „může vypadávat“ ani návody (např. ne „Wi-Fi 2,4 GHz – může vypadávat“, ne „telefon může
  při zhasnutém displeji…“ – Wi-Fi 2,4 GHz je prostě zelený řádek „Síť: Wi-Fi 2,4 GHz“).
- **Vysvětlení patří do Nápovědy** (kapitoly v `help()`), popis volby do **dialogu při jejím zapnutí**
  (`confirmOn` v MainActivity) – ne jako šedý text mezi řádky.
- **Rozbalená skupina = odsazená karta (od 0.97.53, přání majitele):** obsah otevřené skupiny jde do tmavší odsazené karty se
  žlutou čarou vlevo (`openNest` / `closeNests` v MainActivity; `add()` přidává do poslední otevřené karty, karta končí dalším nadpisem
  skupiny nebo koncem stránky; vnořená „Postavy, místa a pojmy“ s `inner = true`). Po rozbalení (`toggled`) se karta plynule zjeví
  a stránka se posune na nadpis. Novou rozbalovací skupinu dělej přes `group()` nebo stejně (`closeNests` před nadpisem, `openNest` po něm).
- **Související volby do rozbalovacích skupin** po vzoru Nápovědy (`settingRow` s ▼/▲, barevná šipka) –
  majitel Nápovědu v tomhle stylu výslovně chválil. Akce jako „Nastavit oblasti“ jsou řádky ve stejném
  stylu (s „›“), ne velká žlutá tlačítka; velké žluté je jen hlavní akce stránky (Spustit, Spárovat).
- **V aplikaci ani v návodech se netyká (přání majitele 21. 11. 2026: „To tykání z appky pryč!“, od 0.97.67).** Pokyny v infinitivu
  („Zapnout PS5“, „Naskenovat čtvereček“), trpný rod / zvratné „se“ („Adresa se vyplní sama“), otázky v infinitivu („Jakou TV nastavit?“,
  „Na čem nastavovat?“); žádné „máš / zapni / tvůj“ a ani vykání. Totéž v ostatních jazycích (bez du/tu/ty/ти/tú/te – infinitiv nebo
  neosobní vazba, ne Sie/vous), angličtina beze změny. Neplatí pro překlad titulků ze hry (`Natural.kt` tyká záměrně).
- **Stavové hlášky, ne věty a bez tykání** (přání majitele, 20. 11. 2026): na Domů, v řádcích a v menu jen
  „Popis · hodnota“ („LG OLED55B7 · připojeno“, nejistý odhad jen „Grand Theft Auto VI?“), ne „Hraješ asi …“ ani jiné oslovení hráče. Kratší
  i v úzkém menu telefonu. Platí pro nové texty ve všech jazycích; staré s tykáním přepisuj, když na ně narazíš.
- **„TV“ místo „televize“** v krátkých textech (řádky, tlačítka, stavy, kroky nahrávání): „Nahrát do TV“,
  „ReCaption v TV LG 1.4.0“. Slovo televize jen v Nápovědě a delších popisech. „DeepL online“, ne „přes internet“.
- **Krátké stavové řádky:** „PS5-151 · připojeno“, „LG OLED55B7 · připojeno“ – jméno a jedno slovo stavu,
  žádné dlouhé věty. Popisky voleb bez závorek s vysvětlením („Diagnostický režim“, ne „… (co aplikace čte)“).
- **Uložení se nehlásí (přání majitele 22. 11. 2026, od 0.97.78 / aplikace v TV 1.5.18):** změna nastavení se uloží potichu –
  žádné „Uloženo“, „změny se uloží“, „ukládám“ (v aplikaci, v TV, na stránce z TV). Hlásí se jen, když se uložit **nepovedlo**
  (v TV řádek `pn_offline` „Změny se nepodařilo uložit“, jen když zápis do localStorage selže). Výsledek akce, kterou hráč spustil
  sám a potřebuje vědět kam (uložení snímků do Galerie, souboru .wgt), zůstává. Hlášky aktualizace mají znění pro telefon
  (`upd_*_ph`, `Updater.text` podle `NetInfo.isTvDevice`) – telefon nemá mluvit o „televizi“.
- **Chyba a výsledek nahoře (přání majitele 22. 11. 2026):** červený chybový řádek (a zelené „hotovo“) hned pod nadpisem / stavem
  stránky nebo kroku, ne pod kroky a políčky, kde je odscrollovaný (nahrání do TV LG / Android TV, kroky spárování PS5). Úspěch
  stačí zeleným řádkem na stránce – žádný dialog „v pořádku“. Spárovaná TV LG, která není v síti, ukáže jen „TV LG nenalezena ·
  zapnout TV“ (`lg_wait_tv`) a čeká – průvodce přípravou se rozbalí, až když TV v síti je a vývojářský režim / Key Server chybí.
- **Rozlišuj zařízení:** co dává smysl jen na Android TV / boxu (Zpět do hry HDMI, povolení pruhu) a co jen
  v telefonu jako čtečce pro LG/Samsung (běh na pozadí) – `Prefs.isTv`, `Prefs.tvType`.

## Obrazovky a průvodce

- **Menu** (`Section` v MainActivity, v tomto pořadí; od 0.97.52 Titulky před Konzolí – přání majitele): Domů, Titulky
  (vzhled, jazyk překladu, oblasti), Konzole (nadpis stránky PlayStation / Xbox), Nápověda (Instalace; Videonávody od 1.00-rc20 nejsou – klipy u kapitol
  Instalace a v průvodci) a jako poslední **⚙ Nastavení** – to není
  v řadě menu, ale ozubené kolo vidět pořád (`gear`): v telefonu vpravo nahoře v liště, na televizi dole
  v levém sloupci (pořád poslední položka při procházení šipkou dolů – zkouška `07_menu_5`). Historie se
  otevírá z Nastavení (záložka Čtení a překlad).
- **Aplikace je zatím zdarma (od 0.97.147, přání majitele 23. 11. 2026):** `License.FREE = true` – zkušební doba neběží, nic se nezamyká a začátek zkušební doby se neukládá (`Prefs.license`). Při zpoplatnění `FREE = false` a začátek zkušební doby pod novým klíčem (starý `first_run` mají jen ti, kdo instalovali do 0.97.146), majitel chce předem ohlásit.
- **Licence a platby jsou zatím skryté** (`SHOW_LICENSE = false` v MainActivity, odkryjí se při vydání):
  ukáže se jen, když zkušební doba skončila (`!canRun`), jinak by hráč nevěděl, proč aplikace nejde.
- **Průvodce prvním spuštěním** (`wizardSteps`, indexy `W_…`): Jazyky (přání majitele 21. 11. 2026: první krok, `W_LANGS` –
  úvodní věta, řádek Jazyk aplikace (hned přepne texty) a Překládat z (`sourceRow`)) → Zařízení (Android TV/box, nebo
  telefon/tablet jako čtečka pro LG/Samsung, `Prefs.readerDevice`; výchozí podle
  `NetInfo.isHandheld`; samo nic nepřepíná – pomocník v telefonu se dál řídí `Prefs.forceTv`) →
  Konzole → Televize (u LG/Samsungu tlačítko nahrání) → Jazyk překladu → Kvalita překladu (od 0.94,
  `W_TRANSLATE`, od 0.97.28 „Lepší překlad (DeepL)“ bez volby DeepL / offline – rovnou věta proč, čtverečky a čekání na
  klíč, viz DeepL) → Zobrazení titulků (povolení; u LG/Samsungu jen vysvětlení) → Připojení konzole → Hotovo.
  **Videa ke krokům (větev nového průvodce, přání majitele):** pod názvem kroku řádek „▶ Video k tomuto kroku“ (`vs_row`, tag `wvideo`) s klipem podle podkroku (`videoSubstep()`), typu TV a konzole (`core/VideoSteps`, id viz Videonávody, testy `VideoStepsTest`; seznam klipů `pomoc/videa/kroky.json` stahuje `VideoStepList` nejvýš 1× za 6 h, bez seznamu řádek není – zkouška v emulátoru beze změny). Zadání klipů `navrhy/videa-po-krocich.md`.
  **Od 0.97.98 (přání majitele: méně kroků nahoře, podkroky uvnitř):** Televize je hned za Zařízením (`W_TV` = 2, `W_CONSOLE` = 3) a záhlaví i levý seznam ukazují 6 hlavních kroků (`wizardGroups`: Jazyky · Zařízení a TV · Konzole · Překlad · Zobrazení titulků · Připojení; Hotovo mimo počet) – „Krok 2 z 6 · Zařízení a TV · 2/2“, texty `wz_g_…` v `I18nUi.kt`. Kroky samotné, jejich počet a výchozí `wnext` se nemění (zkouška kliká naslepo; snímky `01c_…televize` je teď před `01b_…konzole`).
  **Od 0.97.65 (majitel: „Jazyky“ a „Jazyk překladu“ se kryly – kontroluj názvy kroků):** krok 1 Jazyky má všechny tři jazyky
  (aplikace, Překládat z, Jazyk překladu `langRow("wlang")`), krok 5 se jmenuje **Offline překlad** (`wz_s2`) a je jen stažení
  balíčku. Rozcestník bez nadpisu: „Nastavit telefonem nebo počítačem (doporučeno)“ / „Nastavit přímo na TV“; obrazovka nastavení
  telefonem ukáže pod čtverečkem i adresu pro počítač (💻). Při změně kroků průvodce projdi názvy všech kroků v jazycích – nesmí se krýt.
  **Připojení konzole (od 0.97.28):** nahoře jen „Krok 8 z 9“, spárování místo druhého pruhu malý podtitul
  „Spárování · 3 z 6“ (`pg_sub`, tag `psub`; mimo průvodce dál pruh `ui.steps`). Jedna řada tlačítek – ta ze spárování
  (`pnext`, `pback`; v kroku 1 spárování vede Zpět na předchozí krok průvodce) a pod ní malé „Spárovat později“
  (`wlater`, skočí na Hotovo); u Xboxu (`XboxForm`) navíc malé Zpět. Vnější Pokračovat/Zpět/Přeskočit průvodce
  se ukáže, až je konzole spárovaná. **Od 1.00 (přání majitele, podle webového průvodce):** volby v kroku Konzole mají krátký
  popis (`c_ps_d` – přes Remote Play s druhým účtem, `c_xbox_d`) a u PS5 odkaz na přípravu 🔵 účtu (`HelpLinks.PS5_PREP`, `helpLink`:
  TV čtvereček 150 dp, telefon tlačítko); celý postup přípravy zůstává v Připojení konzole. Hotovo má řádek „Víc TV nebo konzolí ›“
  (`hm_row`, okno s `hc9_text`, totéž kapitola 9 v Instalaci).
  **Kroky jasné podle zařízení se přeskočí (od 1.00, `autoStep` / `applyAutoStep`):** Android TV (`NetInfo.isTvDevice`) Zařízení
  i Televizi (jen když `readerDevice` = false a typ TV Android TV), telefon (`NetInfo.isHandheld`, bez `forceTv` nebo už s LG/Samsungem)
  Zařízení; přeskočení uloží totéž co ruční volba (`setReader`, `TvChoice.set`), jiná dřívější volba krok zase ukáže. „Krok X z N“
  i levý seznam počítají jen hlavní kroky, ve kterých něco zbývá (`shownGroups`, `groupSteps` – jako web: LG/Samsung bez Zobrazení
  titulků). Zkouška Android TV proto jde z Jazyků rovnou na Konzoli, zkouška telefonu krok 1 nesnímá.
  **Co průvodce udělá sám (od 0.97.53, přání majitele):** krok Televize v telefonu (`readerDevice`) hledá jednou TV v síti
  (`findTv`: `LgFinder.ssdp` + `LgFinder.samsung`, `core/TvDetect.suggest` – jen jediný druh) a předvybere ji, když hráč
  nevolil sám a na zvolené TV se ReCaption ještě neohlásilo („✔ V síti · …“, `wz_tv_found`); krok Povolení po návratu
  z Usnadnění s povolením pokračuje sám (`permAsked` v `onResume`); spárování: krok 1 (zapni PS5) tiše hledá PS5
  (`autoFindPs5`, jednou za 20 s, jen jedna PS5 → rovnou krok 3 Účet, po „Zpět“ už ne), jediná PS5 z tlačítka Najít nebo
  adresa z kamery → krok 3, osm číslic v políčku / z kamery → spárovat hned (`autoPairOk`: účet, adresa, bez kódu účtu PS5),
  spárováno v průvodci → rovnou Hotovo se „✔ Spárováno …“. Emulátor PS5 ani TV v síti nemá, zkoušku to nemění.
  Příprava PS5 (druhý účet, Remote Play, skrytí hlášky, probouzení ze sítě, čtvereček na `pomoc/ps5.md`)
  je od 0.93 až v prvním kroku párování (Připojení konzole), ne u volby konzole – tam byla moc brzo.
- **Tlačítka překladu na Domů (od 0.97.127, přání majitele):** jako přehrávač ▶ ❚❚ ■ vedle sebe (`mediaButton`, tagy `start`/`pause`/`stop`),
  **od 0.97.128 barva podle akce (běžná praxe, přání majitele):** ▶ zeleně, ❚❚ žlutě, ■ růžovočerveně (`STOP_RED`), co teď nejde, šedě – stav ukazuje
  řádek nad tím; **od 0.97.137 jedna řada stejně velkých tlačítek přes celou šířku** (`mediaRow`, výška 56 dp, mezery 10 dp; co nejde, ztlumený
  podklad `MEDIA_OFF`; Snímky dole stejně, Odeslat jako třetí tlačítko řady); **od 0.97.129 tlačítka nahoře nad přehledem a bez velkého nadpisu „Překlad běží“ / „Připraveno“** (nadpis jen u stavů, které
  je potřeba vědět – připojuje se, PS5 spí, chyba, čeká na TV, pozastaveno); na Android TV za běhu navíc „Zpět do hry“ (tag `start`, ▶ pak `play`). Nespárovaná PS5 = „Spárovat“. Stejně Snímky: ▶ ■, Odeslat modře (`SEND_BLUE`).
- **Domů** (od 0.97): velký stav + Spustit/Zastavit a pod nadpisem přehled v bodech (`statusRows`, tečka
  zelená/oranžová/šedá; řádek, který potřebuje zásah, má pod sebou tlačítko): konzole + obraz (`LinkStatus`,
  při ukončení ze strany PS5 důvod, při převzetí jiným zařízením `ls_other_device`), televize (Android TV =
  povolení pruhu; LG/Samsung = `TvChoice.short`, nenahraná / zastaralá aplikace s tlačítkem nahrání – jinak
  žádné tlačítko), síť (`h_net_…`, vždy zeleně), rychlost čtení, jazyk + offline/DeepL (chybějící balíček
  s tlačítkem) a verze.
  **Od 0.97.71 (přání majitele: po zastavení překladu psal telefon i TV „nepřipojeno“, až po Spustit „připojeno“):** spojení s TV
  se při Spustit / Zastavit nepřeruší – služba si vezme otevřené spojení obrazovky aplikace (`TvIdle.take`) a po zastavení ho
  aplikaci vrátí (`TvIdle.adopt`, jen když je aplikace vidět – jinak se zavře jako dřív). Stav do TV bez běžícího překladu je
  jediný oranžový řádek „PS5-151 · nepřipojeno“ (dřív navíc „Překlad · neběží“, text `tvs_stopped` smazán). **Od 0.97.90 (majitel: TV ukazovala zelené řádky a „ReCaption · připojeno“, i když překlad v telefonu stál):** u TV LG/Samsung je první řádek z Androidu „Telefon · překlad neběží“ oranžově / „Telefon · překlad běží“ zeleně (`tvs_dev_phone` / `tvs_dev_box`, `tvs_run` / `tvs_off` v `I18nSamsung.kt`) a bez běžícího překladu jsou jazyk a hra šedě – od 0.97.101 stejně i na Domů; zapnutá PS5 je v obou zeleně. Aplikace v TV 1.5.16:
  dokud čeká na spojení, jen řádek „ReCaption · hledám v síti…“; řádek HDMI jen při potížích se vstupem.
  Od 0.97.7: televize LG/Samsung = jen spojení („LG OLED55B7 · připojeno“ zeleně / „nepřipojeno“ oranžově,
  `tv_st_off`); spojení drží `TvIdle` (v `TvChoice.kt`) i před Spustit, dokud je aplikace vidět (služba ho před
  vlastním `TvSender` zavře). Bez připojené televize velký stav „Čeká na televizi“ (`st_wait_tv`), ne Připraveno.
  Verze: řádek ReCaption (aktuální zeleně, novější oranžově „· nová X“ s tlačítkem Aktualizovat v řádku a postupem
  stahování pod ním – samostatná karta aktualizace není) a řádek ReCaption v televizi (chybí / starší oranžově
  s tlačítkem nahrání, aktuální zeleně).
  Od 0.97.71 řádek rychlosti čtení (`h_speed`) úplně dole pod tenkou čarou, oranžový od 450 ms a zpět zelený pod 350 ms (`speedWarn`).
  **Od 0.97.75 stav PS5 před Spustit podle sítě** (majitel: vypnutá PS5 svítila zeleně „spárováno“): `Ps5Probe` – na Domů (ticker) a se stavem
  pro TV (`TvIdle.ensure`) nejvýš 1× za 20 s `Ps5Pairing.discover(1500, quiet = true)` na pozadí, jen když překlad neběží. Domů: zapnutá
  zeleně „PS5-151 · zapnutá“ (`h_con_on`), klidový režim oranžově `h_con_sleep`, neodpověděla oranžově „spárováno“ (vypnutá úplně, nebo síť
  hledání nepropouští – proto ne „vypnutá“). V TV LG/Samsung týž text, vždy oranžově (překlad neběží). Změna PS5 = `Ps5Probe.reset()` v Prefs.
  Od 0.97.9 řádek hry jen s názvem: vybraná hra zeleně („Grand Theft Auto VI“, OK = výběr hry `pickGame`); když
  `GameGuess` z titulků pozná jinou hru, oranžově s otazníkem za vybranou hrou („Call of Duty · Grand Theft Auto VI?“, s univerzálním
  profilem jen „Grand Theft Auto VI?“) a tlačítkem Přepnout – jeden řádek, vybraná hra se nepřepíše.
  Od 0.97.10: „ReCaption v TV LG 1.4.0“ (`h_ver_tv` dostává typ + verzi), překlad „Čeština · DeepL online + offline“. Univerzální profil bez odhadu = řádek není. Na novou verzi se aplikace ptá i po návratu do ní (nejvýš jednou za 5 min). Běh na pozadí (telefon, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) se
  zeptá jednou při prvním Spustit (`ui_jednou`) a dál je jen v ⚙ Nastavení → Obecné, na Domů ne.
  Řádek konzole/televize OK = změna. Samostatný souhrnný řádek ani návody v textu tam už nejsou. Náhled obrazu
  je jen na stránce Konzole (tlačítko Spustit tam od 0.97.13 není – jen na Domů, telefon i Android TV) (s popiskem `pv_stale`, když obraz nechodí). „Zpět do hry (HDMI)“ jen na zařízení
  v televizi (`Prefs.isTv`); telefon jako čtečka má jen Pozastavit a Zastavit. Jednorázové volby na Domů nepatří.
- **Profil hry vs. obecné volby (od 0.97.54, přání majitele):** skupina Hra je v Titulkách hned pod jazykem (před Písmem,
  Pruhem a Oblastmi). Profil u běžných titulků nemění písmo, polohu, šířku ani oblast čtení; přebíjí jen: barvy postav (řádek
  Barevná jména ukáže „· profil hry“, naučená barva z obrazu má přednost), překlad menu (vlastní místo vedle menu) a úkol (vlastní
  volby). Vlastní slovník hráče přebije i jméno z profilu (`Glossary(userTerms)`, testy `GlossaryUserTest`).
  **Řádky jen když dávají smysl:** „Jen titulky“ (oblasti) bez automatiky, Přesnost čtení jen PS5, Tlačítko vzhledu jen s Usnadněním,
  Zobrazení přes jiné aplikace jen bez Usnadnění, Pokyny a Úkol jen u her s `prompts`, Překlad menu jen s `menuSpec`, Písmo úkolu
  „podle hry“ / „jako titulky“ podle `matchSize`.
- **Titulky** (od 0.97.1, `subtitlesPage`): nahoře jazyk překladu (zkouška `13_nabidka_jazyk` na něj míří
  šipkou vpravo + OK), pod ním rozbalovací skupiny Písmo / Pruh s překladem / Oblasti titulků
  (`lookOpen`) a dole řádek Obnovit vzhled. Automatika oblastí ukáže popis v dialogu jen při zapnutí.
  Volba „Černý pruh přes anglické titulky“ zmizela – je to totéž co poloha „Přes titulky“.
- **Instalace** (do 0.97.1 se jmenovala Nápověda; klíč `m_help`, `help()`) – návodné věci patří sem, ne do
  Nastavení: nahrání aplikace do televize je v kapitole 3 (z ⚙ Nastavení → Obecné zmizelo v 0.97.2).
- **Nápověda / Instalace** (od 0.97, `help()`, texty `core/I18nHelp.kt`): nahoře „Nápověda pro“ (typ televize, výchozí
  zvolený), pod tím kapitoly 1–8 jako `settingRow` (OK rozbalí, `helpOpen`): instalace, příprava konzole (druhý
  účet, Remote Play, `rp_hide`, `ps5_rest` / Xbox), televize (povolení pruhu / pomocník LG / průvodce Samsung),
  jazyk a DeepL, spárování, hraní, když něco nejde (+ hlášení), volby v Nastavení. ✔/○ podle skutečného stavu
  jen pro zvolenou televizi. Stránky s návodem (`HelpLinks`): telefon tlačítko, televize čtvereček.
- **Videa ke krokům** (záložka Videonávody zrušená v 1.00-rc20, přání majitele): v Instalaci u kapitol hc2/hc3 (LG, Samsung)/hc4/hc5
  řádek „▶ Video k tomuto kroku“ (tag `hvid_<skupina>`, jen pro zvolenou TV) a v průvodci (`wvideo`); Instalace má nahoře `wagain`. Klip podle zvolené TV a konzole (`VideoSteps.pick` + `VideoStepList`, seznam `pomoc/videa/kroky.json`); bez staženého
  seznamu jen slabý běžící pruh. OK → `VideoGuideActivity` s `EXTRA_ID` (VideoView + MediaController, OK = pauza, ◀ ▶ = 10 s, Zpět = zavřít;
  chyba `vg_error` s adresou; bez platného id se zavře). Adresa `VideoGuide.video(id, jazyk)` (`cs` pro češtinu a slovenštinu).
  **Celá videa (tv, android, iphone, lg, samsung) jsou od 1.00-rc14 zrušená** (přání majitele: po každé změně průvodce zastarala všechna naráz).
- **Nahrání do televize** se nabízí jen, když tam aplikace ještě nikdy nebyla nebo je starší (`TvChoice.upToDate`,
  `outdated`): Domů v přehledu, krok Televize v průvodci jinak ukáže „✔ ReCaption je v televizi (verze X)“;
  vždy je k dispozici v Instalaci (kapitola 3). Pomocník LG u aktuální verze píše „V televizi je aktuální…“ a tlačítko
  „Spustit ReCaption v televizi“; u spárované televize je políčko Passphrase schované pod tlačítkem
  „Zadat Passphrase znovu“.
- **Nastavení (⚙)** (od 0.97.15): každá záložka jen řádky `settingRow` a rozbalovací skupiny `group(id, název)`
  (`openGroups`), žádné nadpisy sekcí ani popisné věty. Čtení a překlad: Kvalita čtení, Režim překladu (OK → Titulky →
  Kvalita překladu), Jazykový model, skupiny Statistika (s Historií) a úplně dole Vlastní slovník. Experimentální:
  Zkušební verze a skupina Ladění a hlášení (diagnostika, hlášení, záznam v síti); DeepL je od 0.97.28 v Titulkách. Hra je v Titulkách
  (skupina `gameGroup`). Náhled obrazu na stránce Konzole je skupina – snímek se počítá jen, když je rozbalená.
  Starší popis níž platí jen zčásti:
- **Nastavení (⚙)** má záložky (`SettingsTab`) a ukazuje jen obsah vybrané: **Obecné** (konzole, typ
  televize + nahrání, jazyk aplikace, zařízení; povolení pruhu jen u Android TV),
  **Čtení a překlad** (kvalita čtení, překlad, hra, slovník, statistika, historie), **Experimentální**
  (zkušební verze, DeepL, záznam v síti a dole rozbalovací skupina Ladění a hlášení – diagnostický režim, hlášení).
  Od 0.97.6: záložky Demo režim a Ladění a hlášení nejsou (demo pro hráče nahradí videa; ukázkovou hru dál
  spouští zkouška v emulátoru parametrem `autostart`), záložky jsou s nadpisem v pevné hlavičce `pinned`
  nad posuvnou částí a přepnutí záložky posune obsah na začátek (`pageScroll`). Stránky nemají nadpis
  (název je v menu), jen Historie. U voleb jsou jen názvy;
  dlouhé popisy jsou v Nápovědě v části „Volby v Nastavení“ (`help_settings`). Nová volba = krátký název
  v Nastavení, popis do Nápovědy.

## Ukazatele postupu (od 0.96)

`Ui.progress(fraction)` – pruh (0..1; `null` = nevíme kolik, pruh běhá) a `Ui.steps(current, total, label)` –
„Krok 3 z 8“ (text `steps_of`) s pruhem na dílky. Používá se: stahování aktualizace (Domů i pomocník
v telefonu, procenta po 2 %; instalace = běhající pruh), stahování jazykového balíčku (ML Kit procenta neříká –
`OfflineTranslator.isDownloading`), připojování ke konzoli a čekání na obraz na Domů, hledání/párování PS5
(`pairBusy`), přihlášení Xboxu, nahrávání do LG (podle `LgPhase`), kroky průvodce, spárování PS5 (6 kroků; v průvodci
jen podtitul „Spárování · 3 z 6“, ať nejsou dva pruhy nad sebou) a pomocník LG (5 kroků). Nový postup o víc krocích = přidej `ui.steps`, nové čekání = `ui.progress`.
**Slabý běžící pruh `ui.waiting()` (od 0.97.79–0.97.80, přání majitele: „progress bar je super věc“):** tenký (3 dp), poloprůhledný,
odsazený pod řádek / text, na který se čeká a který se sám vyřeší: Domů – TV LG/Samsung nepřipojená (TV telefon hledá), PS5 spí za běhu
(čeká na ovladač), obraz nechodí; horní pruh „Nastavuji TV“ s nedostupnou TV (`remoteBarWait`, jen viditelnost); pomocník v telefonu
během `busy` (připojování k TV, dotaz na Sony, posílání účtu); DeepL „Čekám na klíč z telefonu“; nastavení telefonem (čeká na telefon,
párování běží); párování krok 4 (TV čeká na účet ze stránky); průvodce hledá TV v síti; kontrola nové verze / seznamu her; krok na řadě
v pomocníkovi LG / Android TV / Samsung, který se sám odškrtne; Samsung čeká na ohlášení z TV; LG „TV nenalezena · zapnout TV“.
V TV (1.5.18) pod „ReCaption · hledám v síti…“ (`ceka: true` v řádku stavu, `cekaPruh`, CSS `.st-ceka`), na stránce z TV (`SetupPage`,
`.wb` – `accw`/`pairw`/`dlw` podle `bars()`) při odesílání účtu / čekání na přihlášení, párování a ověřování DeepL. Velký pruh
`ui.progress(null)` zůstává pro aktivní práci (připojování ke konzoli, stahování, nahrávání). Nové čekání = `ui.waiting()` / `addWaiting()`.

## Na co zkouška spoléhá (neměnit jen tak)

- Řádky v logu se značkou `TP_TEST` (`SHOW|`, `VIDEO|`, `LINK|`, `PAIR|`, `START|`…) –
  podle nich `ci/emulator-test.sh` počítá výsledek. Před ukázkovou hrou obě zkoušky čekají (nejvýš 3 min)
  na `MODEL|<jazyk>|ok` – jazykový balíček se stahuje od Googlu a občas nestihne (0.80: Android 11
  přeložil jen 2/15, v logu „Translation model files not found“). Aplikace po neúspěchu stahuje znovu
  (`prepareModel`, 20 s, nejvýš 6×; při chybě překladu nejvýš jednou za 30 s). Výsledek čekání je v `model.txt`.
- Věty ukázkové hry v `core/DemoGame.kt` – skript hledá 15 z nich podle textu. Měníš-li věty,
  uprav i skript.
- Parametry spuštění `nowizard`, `autostart`, `ps5test`, `phonelogin`, `pairstep`, `updtest`,
  `updself`, `logintest`, `helperlogin`, `fullapp`, `page` (jména `Section`: HOME, PS5, LOOK, HELP, VIDEO,
  SETTINGS; staré LICENSE = SETTINGS), `wizard` (číslo kroku průvodce, snímky `p2_<velikost>_pruvodce_<krok>`),
  `trtest`, `console` (ps / xbox, snímek `11b_stranka_xbox`), `open` (samsung = průvodce TV Samsung, snímky telefonu `p2_<velikost>_samsung`) a `TestCardActivity`, `PhoneHelperActivity`.
- Zkouška přihlášení přes telefon (řádky `LOGIN|`, `HELPER|`, snímky `12n`–`12p`) se do výsledku
  nepočítá. V `result.txt` má být `ucet_z_telefonu` aspoň 1 (stránka z televize přijala účet)
  a `pomocnik` aspoň 1 (pomocník přijal odkaz ze stránky televize); na snímku `12n` má být
  „Ověřený účet: Zkouška“ (krok nastavení na PS5) a na `12o` „Televize čeká na účet“.
- Kroky průvodce spárováním se mají vejít na obrazovku i s tlačítky (snímky `12j`–`12l`).
  Podrobný postup proto patří na stránku pro telefon, v televizi jen krátce.
- Zkouška aktualizace (řádky `UPD|`, snímky `15a`–`15d`) se do výsledku nepočítá. `updtest`
  stáhne vydanou verzi (závisí na internetu; emulátor ji nenainstaluje). `updself` nainstaluje
  aplikaci samu přes sebe – na snímku `15c_dotaz` má být dotaz systému na instalaci a v
  `result.txt` má být `aktualizace_nahrazeno` aspoň 1.
- Pořadí položek menu a kroků průvodce – skript se jimi prokliká naslepo šipkami (viz Obrazovky
  a průvodce). Na každém kroku průvodce je po vykreslení vybrané tlačítko Pokračovat (`wnext`, u párování
  `pnext`), takže OK posune dál. Snímky průvodce: `00_pruvodce_jazyky`, `01b_…konzole` (Zařízení a Televize Android TV
  od 1.00 přeskakuje), `03_…jazyk`, `03b_…po_stazeni`, `03c_…preklad`, `02_…povoleni`, `04_…ps5_krok1`, `04b_…hledani`;
  stránky menu `07_menu_1`–`5` (od 0.97.52 Titulky, Konzole, Nápověda; od 0.97.71 Videonávody, Nastavení jako `07_menu_5`).
- Zbytky starého názvu „TestPreklad“ (značka logu, `rootProject.name`, názvy souborů
  v sestavování, `TestPreklad.apk` ve vydání, sůl odemykacího kódu v `License.kt`).
  Nepřejmenovávej je: visí na nich zkouška, odkaz ve starších verzích a platnost kódů.

## Poučení z historie (co už se zkoušelo)

- **Spojení s konzolí neměň bez výslovného zadání.** Emulátor PlayStation nemá, takže zkouška
  projde, i když to na skutečné konzoli nefunguje. Ověřit to umí jen uživatel na své televizi.
  Dva pokusy nehlásit se konzoli jako ovladač (0.34 „jen se dívat“, 0.38 „tichý režim“)
  se musely vrátit.
- **Zhasnutý displej telefonu zpomalí čtení (dávka testera, 22. 11. 2026):** OCR z ~0,05 s na ~1 s, krátké repliky
  propadnou. Volba ⚙ → Obecné „Nezhasínat displej telefonu“ (jen telefon, `Prefs.screenDim`, klíč `displej_tlumeny`, výchozí vypnuto, texty `dm_…`
  v `I18nQuick.kt`) → `TranslatorService.updateScreenDim` drží `SCREEN_DIM_WAKE_LOCK` jen při běžícím překladu (`SCREEN|displej ztlumený`).
  Totéž v PC aplikaci (Windows i Mac): ⚙ → Použití „Nezhasínat PC/Mac při překladu“ (klíč `nezhasinat` v `pc/src/Nastaveni.kt`, výchozí vypnuto,
  `pc/src/Nezhasinat.kt`: Windows `SetThreadExecutionState` přes JNA, Mac `caffeinate -dimsu -w <pid>`; viz `pc/CTI-ME.md`).
  **Černý displej (od 1.0.5, přání majitele, nápad z GlossaSubu):** na Domů v telefonu čtvrté tlačítko ☾ (tag `black`, jen `isHandheld`, jen při běžícím
  překladu) → `BlackScreenActivity`: černá přes celou obrazovku, `FLAG_KEEP_SCREEN_ON`, jas 0,01 – displej nezhasne (čtení rychlé), ale nesvítí; zpět Zpět
  nebo jedno klepnutí na displej (`bs_hint` „Klepnutím zpět“ 3 s po otevření; dřív dvě klepnutí), po zastavení překladu se zavře samo. Texty `bs_…` v `I18nQuick.kt`.
  **Titulky ke čtení (od 1.0.5, přání majitele, podle GlossaSubu):** na Domů v telefonu pod řadou přehrávače tlačítko „≡ Titulky ke čtení“
  (tag `readfeed`, jen `isHandheld` a ne `isTv`, jen při běžícím překladu) → `ReadFeedActivity`: celá obrazovka bez menu, jen repliky v pořadí
  pruhu (bez menu, úkolu, telefonu, tipů a pokynů), jména tučně v barvách pruhu, písmo 20 sp, nejnovější dole a samo dolů (když hráč
  neodscrolloval), originál menším šedým jen s `Prefs.showOriginal`, `FLAG_KEEP_SCREEN_ON`, vpravo nahoře ☾ → černý displej → klepnutím zpět sem.
  Data `core/ReadFeed.kt` (jen paměť, nejvýš 150, oprava téže repliky do 15 s se přepíše; plní `TranslatorService.display` přes
  `SubtitleOverlay.feedColors`), stránka se obnoví každých 500 ms jen při změně `ReadFeed.version`. Texty `rf_…` v `I18nQuick.kt`.
  **Od 1.0.5 (přání majitele):** tlačítko na Domů vždy (i bez překladu – po zastavení dočíst); `ReadFeed` ukládá i do souboru
  `filesDir/titulky-cteni.tsv` (nejvýš 3000 replik, zápis na vlastním vlákně, `App.onCreate` → `ReadFeed.file` + `load`; čísla replik podle času
  spuštění), na stránce nahoře „▲ Starší titulky“ (`ReadFeed.older`, po 150, text `rf_older`; jen když text přetéká obrazovku, `updateOlderBtn`). Hotovo v průvodci: věta `wz_lines_note`
  (pro lepší překlady se sbírají jen texty ze hry), jen se zapnutým sběrem.
- **Probouzení PS5 je volba, výchozí vypnutá** (od 0.97.18, `Prefs.ps5Wake`, ⚙ → Obecné „Probouzet PlayStation“):
  probuzení přes síť zapne konzoli na spárovaném (druhém) účtu, ne na výběr účtu – to Remote Play neumí. Bez volby
  `Ps5Link.connectWhenAwake` jen každých 8 s zjistí (hledání v síti, `standby`), jestli konzole spí; Domů a stav v TV
  ukazují „PS5 · v klidovém režimu“ (`Ps5Link.asleep`, `h_con_sleep`) a po zapnutí ovladačem se připojí samo.
  Nenalezená konzole = připojí se jako dřív. Ověřit umí jen majitel na skutečné PS5.
  **Od 0.97.41 klíč `ps5_wake2`** (dřív `ps5_wake`): po aktualizaci je probouzení u všech vypnuté (přání majitele), kdo chce, zapne znovu.
  **Od 0.97.37 i po přerušeném spojení:** když hráč PS5 během překladu vypne (klidový režim), aplikace se znovu nepřipojuje
  (pokus o spojení by ji zapnul – hlásili testeři), jen čeká (`sleeping()` před každým pokusem, i s volbou Probouzet).
  **Od 0.97.56 bez falešné chyby:** spojení s vypínanou PS5 končí chybou knihovny „Unknown Session Request Error“; jakmile
  `connectWhenAwake` pozná klidový režim, smaže `reason` / `endedByConsole` / `refusal` (v hlášení zůstává `recentEndings`)
  a Domů ukáže „PlayStation · Čeká na zapnutí PS5 ovladačem“ (`st_asleep`) bez pruhu „pracuji“ a bez řádku `ls_ended`.
- **Víc PlayStationů (od 0.97.67, přání majitele):** aktivní PS5 dál pod `ps5_host` (spojení beze změny), všechny spárované navíc
  v `ps5_hosts` (`core/Ps5Hosts.kt`, testy `Ps5HostsTest`; stejná konzole = stejná MAC). `Prefs.setPs5Host` seznam drží (null = vyřadit
  aktivní), `Prefs.forgetPs5Host` (Zrušit spárování jen té jedné, aktivní je pak další). Konzole: řádek „Aktivní PS5“ (`switchPs5` – při
  běžícím překladu se spojí znovu), „Spárovat další PS5“ (`pairAnother`, tlačítko Zrušit). Najít s víc PS5 v síti nic nevybírá samo
  (`foundPs5` → řádky k výběru, už spárované „· spárováno“); tiché hledání v kroku 1 jen při jediné PS5 a ne při `pairAnother`. Stránka
  z TV při víc PS5 v síti a neznámé adrese nespáruje (`RemoteHost.multi` → `"multi":true`, `mp_multi`). Texty `mp_…` v `core/I18nMulti.kt`.
- **Přihlašovací kód účtu na PS5** (4 číslice, `Prefs.ps5LoginPin`): políčko se od 0.86 ukáže jen,
  když o něj PlayStation při spojení požádal (`Ps5Link.needsLoginPin`) nebo když už je nějaký uložený.
- **Hláška „Aplikace Remote Play je připojena“**: průvodce (u přípravy PS5) a Nápověda radí zapnout na
  PS5 „Skrýt stav připojení Remote Play“ (text `rp_hide` v `core/I18nUi.kt`).
- **Druhý účet PlayStation je záměr.** Když se aplikace připojí herním účtem, konzole hráči
  vypne ovladač a zhasne obraz. Průvodce proto vede na druhý účet.
  **Zadaný herní účet omylem (24. 11. 2026, 54KW-U73B a další – appka odmítala spojení hned při každém pokusu,
  `fed=0, decoded=0` po celou dobu, ne jen po pádu):** víc testerů si spletlo, který účet se má do appky přihlásit.
  Texty `acc2` (přihlášení přes telefon), `acc_legend` a `pg_acc_text` (příprava spárování) teď výslovně říkají
  „druhý (sekundární)“ a že herní účet se při nastavování appky i televize nepoužívá vůbec – jediná výjimka je
  skrytí hlášky Remote Play (`rp_hide`, to se dělá i pod herním účtem). Nejde o stejnou poruchu jako zotavení po
  pádu spojení výš – tahle appka se k obrazu nikdy ani nedostane.
- **Obraz 1080p jen pro televize s 3 GB paměti a víc** (`core/Perf.kt`). S 1080p na slabších
  televizích se od 0.29 odpojovaly ovladače; od 0.32 berou zase 720p.
- **Hlídání obrazu za spojení** (`Ps5.watchdog`, testy `Ps5WatchdogTest`): spojení se naváže znovu po 12 s
  jen tehdy, když data z konzole chodí, ale nic se nedekóduje; když nechodí nic (Wi-Fi zakolísala), čeká
  se 45 s – do 0.96 se rušilo už po 12 s a PS5 pak hráči hlásila „ovladač odpojen / účet připojen“ (0.95,
  telefon na 2,4 GHz se zhasnutým displejem). Výpadek dat se zapíše `PS5|stall`. Služba drží
  `PARTIAL_WAKE_LOCK` a Wi-Fi zámky (i `LOW_LATENCY`), jinak telefon se zhasnutým displejem uspal
  procesor. PERF má navíc paměť aplikace a Wi-Fi (MHz/dBm), `NET|Wi-Fi přešla na …` zapíše změnu pásma.
- **Odmítnutí spojení před obrazem (od 0.97.31)**: knihovna hlásí jen „Unknown Session Request Error“; skutečný důvod
  je v jejím záznamu `ps5-spojeni.txt`. `Ps5Link` při ukončení bez spojení přečte konec souboru, `Ps5.refusal` (testy
  `Ps5Test.refusalFromLibraryLog`) z něj pozná ACCOUNT (Invalid PSN ID / Regist failed), IN_USE, CRASH, VERSION, UNREACHABLE
  (connect failed) nebo UNKNOWN; řádky knihovny jdou do záznamu jako `PS5|refused: …` a `PS5|refusal …`. Domů pod řádkem
  konzole ukáže `ps5_ref_<důvod>` (u účtu s tlačítkem Spárovat znovu), UNKNOWN dál anglický text `ls_ended`. Řádek
  konzole před Spustit je „spárováno“ (`h_con_ready`, dřív „připraveno“ – mátlo, že je spojeno). Spojení se tím nemění.
- **Zotavení po pádu skutečného spojení (testeři MPJX-7HM6, 5BWK-VX7S, X64Q-75Q9, 24. 11. 2026 – „odpojilo se a
  nedaří se připojit“, zotavení trvalo i přes 5 minut):** po pádu dlouho drženého spojení (heartbeat selhal, „Server shutting
  down“…) PS5 nějakou dobu odmítá i nové žádosti („Unknown Session Request Error“ hned, bez spojení). `Ps5Link.recoveringFromDrop`
  drží spodní limit `Ps5.AFTER_DROP_MS` mezi pokusy po celou dobu zotavování (ne jen u prvního pokusu po pádu jako dřív) – appka
  tak PS5 „neklepe“ zbytečně často, dokud se znovu neochotná. Zotavení (počet odmítnutí, jak dlouho trvalo) jde do záznamu jako
  `PS5|recovered: …`. Čtení konce `ps5-spojeni.txt` kvůli `Ps5.refusal` se zpozdilo o 200 ms (`Thread.sleep` ve vlastním vlákně
  `tp-ps5`) – hned při `QuitEvent` knihovna poslední řádky ještě nestihla dopsat, proto refusal skoro vždy vyšel nerozpoznaný
  (`refusal ?`) a žádné `refused: …` řádky se nezapsaly; filtr řádků k zalogování je širší (i „Timeout“, „Error“, „failed“) a
  nerozpoznaný důvod ukáže víc řádků – pro doplnění `Ps5.refusal()` podle dalších hlášení.
- **Klíčový snímek po výměně dekodéru (od 0.97.103, se souhlasem majitele):** při výjimce dekodéru / „nevydává obraz“
  dostával nový dekodér jen hlavičku z prvního snímku a změnové snímky → obraz napořád zelený s kostičkami. `StreamReader.needKey`: po výměně
  se snímky dekodéru nedávají, dokud nepřijde IDR (`core/H264.nalTypes`, typ 5; hlavička = poslední snímek s SPS), nejvýš 5 s; `Ps5Link`
  mezitím 1× za 1 s `Session.requestIdr()` (JNI `sessionRequestIdr` → `chiaki_session_request_idr`). V záznamu `DEC|…`, `PS5|klíčový snímek vyžádán`.
- **Převzetí Remote Play jiným zařízením** (`Ps5.otherDevice`: „already in use“, nebo odpojení s takovým
  důvodem) → `Ps5Link.otherDevice`, Domů `ls_other_device`, v záznamu „Remote Play převzalo jiné zařízení“.
- **Opakované připojování ruší ovladače u konzole.** Proto se pokusy po čtyřech krátkých
  spojeních zastaví (0.32) a další pokus se odkládá aspoň o 15 s (0.37).
- **Zvětšený výřez pro drobné titulky nekresli do jedné znovupoužívané bitmapy.** Ve 0.53 se
  to na Androidu 9 třikrát po sobě zaseklo, 0.54 to vrátila.
- **Otisk snímku se počítá ze všech pixelů výřezu** (0.31). Ze vzorků se drobný titulek
  na klidné tmavé scéně přehlédl.
- **Pruh se neschovává podle toho, co je na obrazovce** (0.20) – při HDMI to televize nehlásí.
- **Překládají se jen repliky**, ne menu hry, pokyny k ovládání ani text v jiném jazyce
  (0.33, 0.43, 0.45). Čte se jen spodní třetina obrazu.
  Ani texty písní označené notou ♪ (`TextLogic.isLyrics`, od 0.93 – rádio v GTA VI).
- **Přihlášení účtem PlayStation jde jen přes telefon** (QR kód, od 0.19). Přihlášení přímo
  v televizi v přibaleném prohlížeči GeckoView ochrana Sony odmítala a prohlížeč dělal z APK
  176 MB; ve 0.55 bylo obojí odstraněno. Nevracej to. Od 0.57 se v telefonu nic neopisuje
  (viz Přihlášení přes telefon).
- **Velikost výřezu čtení nezrychlí.** Čas jednoho čtení (ML Kit) je ve zkoušce stejný při
  zvětšení 1× i 1,5× i při menší oblasti (měřeno na 0.54, řádky `READ|ocr=…` a `SCALE|`
  ve větvích `test-results-api…`). Menší výřez ani vypnuté zvětšování proto čtení nezrychlí.
- **Co aplikace dělá, když je otevřená** (0.97.14): barevný náhled hry jen pro stránku Konzole a nastavení
  oblastí (`UiState.previewWanted`), Domů se kvůli náhledu nepřekresluje; stav DeepL k zobrazení přes
  `DeeplTranslator.active` (bez dešifrování klíče v Keystore), verze aplikace pro TV z APK se čte jednou.
  Telefon jako čtečka mívá aplikaci otevřenou celou hru – nic drahého nedávej do `render()` ani do tickeru.
- Úpravy výkonu ověřuj na snímcích a časech ze zkoušky, ne odhadem. Emulátor čte jeden snímek
  3–6 s, skutečná televize zlomek toho – z emulátoru jde brát jen porovnání, ne skutečné časy.
- **Hlavní čtení titulků se umělo zaseknout beze stopy (tester 5HZ4-XSEF, 24. 11. 2026, GTA VI, rychlejší
  čtení zapnuté), přesná příčina nepotvrzená:** `čtení=` v `PERF|` 14 minut neporostlo (žádný pád, žádná chyba
  v záznamu), zatímco řádek `PERF|` se dál zapisoval každých 30 s. Watchdog spojení (`Ps5.watchdog`) by měl
  zastavené dekódování poznat do pár sekund, ale `VIDEO|` řádky u delšího běhu vytlačily z bufferu záznamu
  dřív, takže se nedalo ověřit, jestli `decoded` mezitím rostl. `PERF|` proto od teď nese i `fed=`/`decoded=`/
  `menu=` (stav `menuShown`), ať je to příště vidět. Zaseklo se těsně po náporu zmatených přečtení HUD nápisu
  „Get on the dirt bike“ (desítky variant za sekundu). **Pojistka (`TranslatorService.OCR_TIMEOUT_MS` = 8 s,
  `process()`):** `recognizer.process(...).await()` je teď v `withTimeoutOrNull` – zaseknuté rozpoznání (ML Kit)
  kolo jen přeskočí (`OCRHANG|…` v záznamu, nejvýš 1 z 5×) místo aby čtení zůstalo stát navždy beze stopy. Je to
  obecná pojistka proti zaseknutému volání knihovny, ne potvrzená oprava týhle konkrétní příčiny – `core/TextBand`
  (stateless, počítá se z aktuálního snímku) jsem jako příčinu vyloučil.
  **Aby se `OCRHANG|` příště nemohlo ztratit z hlášení** (mazatelný záznam `EventFile`/`udalosti.txt` drží jen
  posledních ~48 kB a delší běh s hodně událostmi starší řádky zahodí – to je i důvod, proč `VIDEO|` z téhle
  relace chybělo): `OCRHANG|` je teď v `TranslatorService.KEEP` (ukládá se do `EventFile`, ne jen do mazatelnějšího
  `DiagLog`), a navíc je celkový počet (`TranslatorService.ocrHangsTotal`) vidět rovnou v `=== spojení ===`
  (`zaseknutí čtení: N`) – to přežije i kdyby záznam starší řádky zahodil.

## Licence

- chiaki-ng je pod AGPL-3.0, proto musí zdrojový kód aplikace zůstat veřejný. Hlavičky
  převzatých souborů s popisem našich úprav zachovej.
- Věta „Aplikace není produktem společnosti Sony Interactive Entertainment…“ v README zůstává.
