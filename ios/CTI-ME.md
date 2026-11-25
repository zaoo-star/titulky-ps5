# ReCaption pro iPhone (zkušební)

Přání majitele 23. 11. 2026: dostat ReCaption do iPhonu jako **čtečku pro TV LG a Samsung** (dnes to umí jen Android
a počítač). Šíření přes **TestFlight** (rozhodnutí majitele po rozboru možností – App Store, AltStore PAL, sideloading,
viz návrh `navrhy/iphone.md` v soukromém repu).

## Jak to bude fungovat

Stejně jako telefon s Androidem: iPhone se přes Remote Play připojí k PS5 🔵 účtem pro ReCaption, čte titulky
z obrazu, překládá je a hotové věty posílá do aplikace ReCaption v TV (TV si iPhone najde sama na portu 8787,
zprávy jako `core/TvSender.kt`). **ReCaption musí být při hraní otevřené** – iOS aplikaci na pozadí zastaví;
displej proto nezhasíná (`isIdleTimerDisabled`).

## Díly

1. **Hotovo:** kostra aplikace, ikona podle loga majitele (`logo-recaption.png`), stránka Domů se stavem (TV · připojeno /
   nepřipojeno, adresa iPhonu), spojení s aplikací v TV a tlačítko „Zkušební věta do TV“. Sestavení a zkouška v simulátoru
   `.github/workflows/ios.yml` (`zkouska/tv.mjs` předstírá TV), nepodepsaný `.ipa`, nahrání do TestFlightu (až s účtem Apple).
   **Společná logika z Androidu (`jadro/`)**: `app/src/main/java/cz/recaption/core` přeložený Kotlinem pro iPhone beze změny
   souborů (framework `Jadro.xcframework`, most pro Swift `jadro/src/most/Jadro.kt` → `ReCaptionJadro.shared`). Co Kotlin
   na iPhonu z Javy nemá, nahrazují `jadro/src/nahrady` (Math, String.format, Charsets, LinkedHashMap s pořadím použití,
   Base64, URLEncoder, SHA-256, Normalizer…). Vynechané soubory se sítí a soubory (TvSender, LoginServer, LogShare, SnapBatches,
   LgIpk, SamsungWgt, TvLog, AtvInstall) má iPhone po svém ve Swiftu. Testy z Androidu (`app/src/test/.../core`, 510 testů)
   běží i v Kotlinu bez Javy: místně `gradle -p ios/jadro linuxX64Test` (Linux, bez Macu), na GitHubu `macosArm64Test`.
   Texty aplikace v 10 jazycích jdou z `core/I18n*.kt` (`Texty.t` ve Swiftu; jen texty, které má zatím jen iPhone, jsou
   ve `Texty.swift` cs/sk/en).
2. Spojení s PS5 – **rozpracováno:** knihovna chiaki-ng pro iPhone (`chiaki/`: `CMakeLists.txt` – nastavení jako
   v Androidu, bez zvuku a dekodéru, mbedtls místo OpenSSL, curl bez TLS, libevent; `sestav.sh` → `PlChiaki.xcframework`)
   a vrstva pro Swift `chiaki/plchiaki.c/.h` podle `chiaki-jni.c` z Androidu (spárování, spojení, video, důvod konce,
   kód účtu). Na Linuxu ověřeno sestavení celé knihovny i běh vrstvy (`./sestav.sh linux` se `ZDROJE=` místními kopiemi
   doplňků; pozor: vrstva se musí překládat s `CHIAKI_LIB_ENABLE_MBEDTLS`, jinak nesedí velikost struktur). Na Macu vše
   připraví `priprav.sh` (podmoduly, protoc, Python s protobuf, Kotlin). V aplikaci zatím `Ps5Knihovna.swift` (zkouška,
   že knihovna je uvnitř). Dál: spárování (adresa PS5 ručně / kamerou – hledání v síti potřebuje svolení Applu
   „multicast“), přihlášení Sony v telefonu (ASWebAuthenticationSession), spojení, dekódování videa (VideoToolbox),
   `ITSAppUsesNonExemptEncryption` (šifrování spojení s PS5).
3. Čtení a překlad: Apple Vision (čtení textu) + Google ML Kit Translate (offline čeština, stejné balíčky jako Android),
   DeepL; výběr a skládání titulků ze společné logiky (`TextLogic`, `SubtitleTracker`…).
4. Průvodce a obrazovky jako v Androidu (Domů ▶ ❚❚ ■, Titulky, Konzole, ⚙ Nastavení), stránka `pomoc/iphone.md`,
   nabídka na webu „Začít s ReCaptionem“ (stránky pro hráče jsou do vydání 1.00 stažené – přání majitele 23. 11. 2026).
5. Navíc podle Androidu: úkol, menu a telefon ve hře, snímky, hlášení a Poslat vývojáři.

### Co Kotlin pro iPhone v hledání textu (Regex) neumí – v `core` nepoužívat
Zjištěno při překladu `core` (23. 11. 2026); Java to umí, Kotlin/Native ne, a ve společné logice to pak na iPhonu spadne
nebo hledá špatně (hlídá `macosArm64Test` / `linuxX64Test` v `ios/jadro`):
- `\p{N}` → psát `\p{Nd}\p{Nl}\p{No}`; `\x{1F3B5}` → dvojice znaků `\uD83C\uDFB5` (mimo hranaté závorky),
- `(?i:` + `Regex.escape(…)` (tj. `\Q…\E`) → escapovat po znacích,
- hledání dozadu `(?<!…)` těsně před skupinou s `(?!…)` uvnitř opakování (`Glossary`) hledá špatně → znak před nálezem
  vzít do nálezu a vrátit.

<a id="zdarma"></a>
## Zdarma na 7 dní (bez účtu Apple Developer) – Sideloadly

Pro první zkoušky u majitele (23. 11. 2026). Každý push do `ios/` připraví nepodepsaný `ReCaption-iPhone.ipa`
(úloha `ipa` v `ios.yml`): vydání `ios-zkusebni` z větve / `ios` z `main`, a vždy i jako artefakt běhu „ReCaption-iPhone“.
Podepíše ho až Sideloadly běžným Apple ID. Omezení: po **7 dnech** aplikace přestane jít spustit (nahrát znovu),
nejvýš 3 takové aplikace v iPhonu, hledání PS5 v síti nepůjde (adresa ručně).

Na PC s Windows, jednou:
1. Nainstalovat **iTunes** a **iCloud** ze stránek apple.com (verze z Microsoft Storu Sideloadly nemusí najít).
2. Nainstalovat **Sideloadly** (sideloadly.io).
3. V iPhonu zapnout **Režim pro vývojáře**: Nastavení → Soukromí a zabezpečení → Režim pro vývojáře → zapnout,
   iPhone se restartuje a zeptá se ještě jednou. (Položka se někdy ukáže až po prvním nahrání aplikace.)

Nahrání (a po 7 dnech znovu):
1. Stáhnout `ReCaption-iPhone.ipa` (vydání `ios-zkusebni`, nebo artefakt posledního běhu „ReCaption pro iPhone“).
2. iPhone kabelem k PC, v iPhonu „Důvěřovat tomuto počítači“.
3. Sideloadly: přetáhnout `.ipa`, vyplnit Apple ID, Start, heslo a kód z druhého zařízení. Kdyby hlásil obsazené
   Bundle ID, v Advanced options zaškrtnout změnu Bundle ID.
4. Poprvé v iPhonu: Nastavení → Obecné → Správa VPN a zařízení → profil s vlastním Apple ID → Důvěřovat.
5. Spustit ReCaption, povolit přístup k místní síti, zapnout aplikaci ReCaption v TV → „TV · připojeno“ →
   „Zkušební věta do TV“.

<a id="codemagic"></a>
## Sestavení na Codemagic (když GitHub nedá Mac)

23. 11. 2026 GitHub hodiny nedával Mac (úlohy ve frontě a pak zrušené), proto je v kořeni repa `codemagic.yaml`
se stejnými kroky jako `ios.yml` (testy společné logiky, knihovna pro PS5, testy aplikace, zkouška se „TV“
v simulátoru, nepodepsaný `ReCaption-iPhone.ipa`). Bezplatný plán: Mac mini M2, asi 500 minut měsíčně; jedno
sestavení trvá odhadem 20–40 min, proto se spouští jen ručně.

Jednou (majitel):
1. https://codemagic.io → **Sign up** → přihlásit se účtem **GitHub** (adusa89) a povolit Codemagicu přístup
   k repu `adusa89/ReCaption` (stačí jen toto repo).
2. **Add application** → GitHub → `adusa89/ReCaption` → typ projektu „Other“ / nastavení z `codemagic.yaml`.
3. Bez platební karty, bez účtu Apple – nic dalšího se nevyplňuje.

Sestavení: aplikace ReCaption v Codemagicu → **Start new build** → větev (např. `claude/festive-knuth-grc5il`)
→ workflow **ReCaption pro iPhone** → Start. Výsledek: v sestavení záložka Artifacts – `ReCaption-iPhone.ipa`
(nahrát Sideloadly, viz výše) a snímek ze simulátoru `domu-pripojeno.png`; při chybě log kroku, který spadl.

## Co musí udělat majitel (jednou) – TestFlight

1. **Apple Developer Program** – developer.apple.com/programs/enroll, 99 USD/rok. Jako fyzická osoba se v TestFlightu
   a App Storu ukáže jméno majitele jako vývojář; jako firma je potřeba číslo D-U-N-S.
2. **Identifikátor aplikace** – developer.apple.com → Certificates, Identifiers & Profiles → Identifiers → + → App IDs
   → App → Bundle ID `cz.recaption` (Explicit), popis ReCaption.
3. **Aplikace v App Store Connect** – appstoreconnect.apple.com → Aplikace → + → Nová aplikace: iOS, název ReCaption
   (kdyby byl obsazený, např. „ReCaption – titulky“), jazyk čeština, Bundle ID `cz.recaption`, SKU `recaption`.
4. **Klíč pro GitHub** – App Store Connect → Uživatelé a přístup → Integrace → App Store Connect API → vygenerovat klíč
   s rolí **Admin** (jen Admin smí nechat Apple vyrobit podpisový certifikát). Soubor `.p8` jde stáhnout jen jednou.
5. **Tajné údaje v GitHubu** – repo ReCaption → Settings → Secrets and variables → Actions → New repository secret:
   `APPLE_TEAM_ID` (developer.apple.com → Membership → Team ID), `ASC_KEY_ID` (Key ID), `ASC_ISSUER_ID` (Issuer ID
   nad seznamem klíčů), `ASC_KEY_P8` (celý text souboru `.p8`). Klíč nikam jinam nekopírovat.
6. Spustit Actions → **ReCaption pro iPhone** → Run workflow. Za 10–30 min je sestavení v App Store Connect → TestFlight.
7. **TestFlight**: majitel se přidá do interních testerů (bez kontroly Applu, hned) a do iPhonu si nainstaluje aplikaci
   TestFlight. Pro ostatní hráče externí skupina s veřejným odkazem – první sestavení projde krátkou kontrolou Applu
   (Beta App Review, obvykle do 1–2 dnů). Sestavení platí 90 dní.
8. Později (díl 2): žádost o svolení „Multicast Networking“ (developer.apple.com/contact/request/networking-multicast),
   aby iPhone sám našel PS5 v síti.

## Sestavení

- Projekt pro Xcode se skládá z `project.yml` nástrojem XcodeGen; `.xcodeproj` se necommituje.
- Bez Macu se aplikace místně sestavit nedá – ověřuje ji jen běh na GitHubu (Mac, simulátor iPhonu). Společnou logiku
  ale ano: `gradle -p ios/jadro linuxX64Test` (přeloží `core` Kotlinem bez Javy a pustí testy z Androidu).
- Číslo verze `MARKETING_VERSION` v `project.yml` (0.1, …), číslo sestavení = číslo běhu na GitHubu.
- Push jen do `ios/` APK pro Android nesestavuje (`paths-ignore` v `build.yml`).
- Kód aplikace pro iPhone patří do tohoto veřejného repa (AGPL – chiaki-ng), stejně jako Android.
