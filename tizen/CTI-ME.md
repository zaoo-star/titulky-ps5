# ReCaption a televize Samsung (Tizen)

Stejně jako LG ani Samsung neumí aplikaci dát obraz hry ke čtení. Na Samsungu proto ReCaption běží
jen jako obrazovka: ukáže hru ze vstupu HDMI a přes ni pruh s překladem. Čte a překládá ReCaption
na Androidu (telefon nebo box) a televizi posílá hotové věty po domácí síti – úplně stejně jako
u LG (viz `../webos/CTI-ME.md`).

```
PlayStation ──HDMI──────────────────────────▶ televize Samsung (aplikace z tizen/app)
     │                                             ▲
     └─ obraz po síti ─▶ ReCaption na Androidu ────┘ hotové věty po domácí síti (port 8787)
                         (telefon nebo box; čte a překládá)
```

## Co je kde

| Soubor | Co to je |
|---|---|
| `app/config.xml` | Popis aplikace pro Tizen: balíček `ReCaption1`, id `ReCaption1.ReCaption`, verze, oprávnění (`tv.window`, `tv.inputdevice`, `internet`, `system`). |
| `app/platforma.js` | **Jediný soubor jen pro Samsung**: obraz ze vstupu HDMI (`tizen.tvwindow`, vrstva `BEHIND` pod průhlednou stránkou), model / jazyk / adresa v síti (`tizen.systeminfo`), tlačítko Zpět (kód 10009), ukončení. Obsahuje `VERZE` – musí být stejná jako v `config.xml`. |
| `app/app.js`, `app/style.css`, `app/index.html` | **Kopie** z `../webos/obchod/app`. Spojení s Androidem, pruh, texty česky/anglicky, tlačítko Jazyk – všechno společné s LG. |
| `app/icon.png` | Ikona (stejná jako velká ikona LG). |
| `test/prijimac.test.js` | Zkouška bez televize: `node tizen/test/prijimac.test.js` pustí `app.js` pro Samsung i LG v napodobeném prohlížeči a ověří hledání v síti, ohlášení s verzí, text i `casti`, Zpět. |

**Společné soubory se upravují ve `webos/obchod/app` a kopírují sem.** Sestavení APK (úloha
`samsungBalicek` v `app/build.gradle.kts`) i unit test `SamsungTest.repositoryTvAppsInSync`
selžou, když se rozejdou (konce řádků se nepočítají). Hlídají i to, že verze v `platforma.js`
sedí s `config.xml` (Samsung) a s `appinfo.json` (LG). Při změně aplikace zvyš verzi na obou
místech – podle ní Android pozná, jestli je v televizi stará verze.

Zprávy jsou popsané v `../app/src/main/java/cz/recaption/core/TvSender.kt`. Od verze 1.1.0
televize po připojení posílá ve zprávě `pripojeno` i `platforma` („webos“ / „tizen“) a `verze`;
ReCaption na Androidu to ukazuje na stránce Domů a podle toho průvodce pozná, že je aplikace
v televizi nainstalovaná (`core/TvReceiver.kt`: `TvAnnounce`, `TvCheck`).

Kód je ve starém zápisu JavaScriptu (ES5) – Samsung z let 2015–2016 (Tizen 2.3/2.4) má starý
WebKit, 2017 (Tizen 3.0) Chromium 47, 2018 (Tizen 4.0) Chromium 56. `required_version` je 2.3.

## Balíček v APK

Úloha `samsungBalicek` složí z `tizen/app` **nepodepsaný** `receiver.wgt` (obyčejný ZIP, pevné
datum 1. 1. 1980 a pořadí – stejné zdroje dají stejný otisk; kód `core/SamsungWgt.kt` přes
`buildSrc`, bez Node, Pythonu i Tizen Studia) a `receiver-manifest.json` (id, balíček, verze,
soubor, velikost, sha256, `"signed": false`). Obojí jde do APK jako `assets/samsung/…`.
Aplikace balíček před uložením ověří (`SamsungInstall.bundle`) a podepsaný by odmítla.
Ručně: `gradlew :app:samsungBalicek` → `app/build/generated/samsungAssets/samsung/receiver.wgt`.

## Proč ReCaption do Samsungu nenahraje samo (na rozdíl od LG)

Zjištěno při přípravě (listopad 2026; zdroje: dokumentace Samsung Developers – TV Device /
Developer Mode, Creating Certificates; komunitní projekty Apps2Samsung, tvinstaller,
tizen-youtube-installer, jellyfin-tizen; podklady projektu PlayCaption, `tizen/INSTALACE-Z-ANDROIDU.md`):

1. **Podpis je vázaný na konkrétní televizi.** Balíček musí nést autorský a distribuční podpis.
   Distribuční certifikát vydává Samsung na účet Samsung a obsahuje seznam DUID (identifikátorů)
   televizí, kam se smí instalovat. Televize mimo seznam balíček odmítne (chyba certifikátu 118).
   Obecný certifikát „Tizen“ spotrebitelské televize Samsung nepřijímají, od Tizenu 7 určitě ne.
   **Jeden podepsaný balíček pro všechny proto udělat nejde** – v APK je nepodepsaný.
2. **Vydání certifikátu** chce přihlášení účtem Samsung (OAuth ve webovém prohlížeči), DUID
   televize a podpis XML (XMLDSig, `author-signature.xml` + `signature1.xml`) – v Androidu
   by to znamenalo vlastní implementaci celého řetězce Samsungu bez oficiální knihovny.
3. **Nahrání** jde přes SDB (Smart Development Bridge, protokol podobný ADB) na port 26101 televize.
   Televize ve vývojářském režimu přijme spojení jen z adresy zapsané v „Host PC IP“ – může to být
   i telefon. Pak `push` souboru a příkaz `vd_appinstall`. Komunitní projekty ukazují, že to
   z Androidu jde, ale protokol je neoficiální a nedokumentovaný.
4. Aktualizace podepsaná jiným autorským certifikátem selže („Author certificate not match“),
   takže klíče by se musely bezpečně uchovávat (jako u LG v Android Keystore).

Bez skutečné televize Samsung by se to nedalo ověřit a pravidlo „úspěch až po potvrzené instalaci“
by nešlo splnit; body 2–3 navíc pracují s účtem a klíči uživatele. **Přímá instalace proto není.**
Průvodce v Androidu (`SamsungInstallActivity`, z Domů u typu televize Samsung, z Nápovědy
a z pomocníka v telefonu) místo toho:

1. vysvětlí vývojářský režim: Apps → na ovladači 1 2 3 4 5 → Developer mode On → Host PC IP =
   adresa zařízení, ze kterého se bude nahrávat (průvodce ukáže adresu tohoto zařízení) → restart,
2. uloží `receiver.wgt` přes systémový dialog (Storage Access Framework, bez oprávnění k úložišti),
3. pojmenuje nástroje: **Tizen Studio** od Samsungu (Device Manager, Certificate Manager →
   certifikát Samsung, `tizen package -t wgt -s <profil>`, `tizen install`) nebo komunitní
   instalátory, které umí nahrát vlastní .wgt a podepsat ho účtem Samsung (např. Apps2Samsung),
4. **úspěch ohlásí, až se aplikace z televize opravdu připojí** a ohlásí verzi (`TvCheck`): stejná
   nebo novější = hotovo (do televize pak pošle zkušební větu), starší / bez verze = nahrát znovu,
   jiná platforma = připojila se LG. Když na televizi nečeká běžící překlad, otevře si průvodce
   port 8787 sám.

**Od 0.97.52 vede průvodce hlavně přes Apps2Samsung** (komunitní instalátor pro Android, Windows, macOS,
Linux; https://github.com/Apps2Samsung/Apps2Samsung): ten najde televizi, jedním přihlášením účtem Samsung
vyrobí certifikát pro její DUID a nahraje libovolný `.wgt`. ReCaption mu předá zdroj odkazem
`apps2samsung://source?url=https://github.com/adusa89/ReCaption` (umí od 3.3.1) – ve vydání
`latest` je `ReCaption-Samsung.wgt` (týž nepodepsaný balíček jako v APK, přidává ho `build.yml`). Host PC IP v televizi
je pak adresa telefonu. Kroky výše (uložit .wgt, Tizen Studio) zůstaly jako rozbalovací záloha „Bez telefonu
s Androidem: počítač“. Logika kroků je v `core/SamsungSetup.kt`.

Další krok, až bude k dispozici skutečná televize Samsung: SDB jen na čtení (DUID, model,
nainstalovaná verze), pak nahrání balíčku podepsaného jinde, nakonec certifikát a podpis v telefonu.

## Nahrání při vývoji (Tizen Studio)

```
sdb connect ADRESA_TELEVIZE                          # Host PC IP v televizi = tento počítač
tizen package -t wgt -s MUJ_PROFIL -- app/build/generated/samsungAssets/samsung/receiver.wgt
tizen install -n receiver.wgt -t <jméno zařízení ze `sdb devices`>
tizen run -p ReCaption1.ReCaption -t <jméno zařízení>
```

Složku `tizen/app` jde v Tizen Studiu otevřít i přímo jako projekt (Tizen Web Application).

## Co není ověřené

- Nic z toho zatím neběželo na skutečné televizi Samsung: `tizen.tvwindow` s vrstvou `BEHIND`,
  adresa ze `systeminfo` (`ETHERNET_NETWORK` / `WIFI_NETWORK`), jazyk z `LOCALE`, WebSocket
  ze stránky na adresy domácí sítě (`<access origin="*">`), vzhled písma. Ověřeno jen
  `node tizen/test/prijimac.test.js` a unit testy.
- Instalace balíčku komunitními instalátory (podpis cizího .wgt).

Převzato podle přijímače PlayCaption pro Samsung (AGPL-3.0, © Adam Růžička: `tizen/app/js/main.js`,
`tizen/build.py`, `tizen/INSTALACE-Z-ANDROIDU.md`) – hlavičky souborů to uvádějí.
