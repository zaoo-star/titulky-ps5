# ReCaption a televize LG (webOS)

Televize LG si obraz hry sama přečíst neumí (ověřeno na OLED55B7, viz `zkouska/`). Proto na LG
běží ReCaption jen jako obrazovka: ukáže hru ze vstupu HDMI a přes ni pruh s překladem. Číst
a překládat musí jiné zařízení v téže domácí síti a televizi posílat hotové věty.

```
PlayStation ──HDMI──────────────────────────▶ televize LG  (aplikace z obchod/)
     │                                             ▲
     └─ obraz po síti ─▶ ReCaption na Androidu ────┘ hotové věty po domácí síti (port 8787)
                         (telefon nebo box; čte a překládá)
```

## Co je kde

| Složka / soubor | Co to je | Kdy se použije |
|---|---|---|
| `obchod/` | **Aplikace pro televizi** (id `cz.recaption.tv`), česky a anglicky. Podává se do obchodu LG. `app.js`, `style.css` a `index.html` jsou společné s aplikací pro Samsung (`../tizen/app`, kopie hlídaná sestavením); jen pro LG je `platforma.js`. | Tohle je ReCaption pro LG. |
| `zkouska/` | Diagnostická aplikace (id `cz.recaption.zkouska`): zjistí, co daná televize aplikaci dovolí. | Jen při zkoumání nového modelu televize. |
| `posilac.js` | Zkušební posílač vět z počítače (`node webos/posilac.js`). Nic nečte ani nepřekládá. | Zkouška aplikace v televizi bez Androidu. |
| `../pc/` | Zkušební čtečka pro počítač: čte a překládá jako Android. | Ladění čtení a překladu. |
| `../app/…/core/TvSender.kt` | Strana Androidu: posílání vět do televize a popis zpráv. | Typ televize LG nebo Samsung (Domů / Nastavení). |

Jak spolu televize a zařízení mluví, je popsané na jediném místě: v `TvSender.kt`. V televizi to
obsluhuje jediný soubor, `obchod/app/app.js` (funkce `hledej`, `zkus`, `prevezmi`), společný
pro LG i Samsung. Co se na LG dělá jinak (služby `luna://`, obraz `ext://hdmi`, tlačítko Zpět 461),
je v `obchod/app/platforma.js`. Při změně společného souboru ho zkopíruj i do `../tizen/app`
a zvyš verzi v `appinfo.json` + `platforma.js` (a u Samsungu v `config.xml` + `platforma.js`).
Zkouška bez televize: `node tizen/test/prijimac.test.js`.

## Jak to zprovoznit doma

1. **Telefon:** v ReCaptionu (od 0.63) klepnout na „Použít telefon pro televizi LG“. V boxu
   s Android TV: Domů → Typ televize → LG (dřív Nastavení → „Posílat titulky do televize LG“).
2. **Televize:** spustit ReCaption a vybrat vstup HDMI s PlayStationem. Televize si zařízení
   v síti najde sama a zapamatuje si ho.
3. V telefonu spustit překlad (nebo zkoušku bez PlayStationu).

## Obrazovky v televizi (od aplikace v TV 1.5.17)

- **Úvodní obrazovka jen poprvé:** zvolený vstup HDMI se zapamatuje (localStorage `hdmi`) a příští
  spuštění jde rovnou do hry na něm. Úvodní obrazovka se ukáže, dokud vstup není zapamatovaný, nebo
  po Zpět při hře. Na ní: HDMI 1–4, stav (ReCaption · připojeno / hledá se a řádky z Androidu – jako
  stav při hře), Nápověda, Jazyk, Ukončit. Ukázka je v Nápovědě (tlačítko vpravo nahoře, Zpět z ní
  vede do Nápovědy).
- **Spojení s Androidem běží od startu do ukončení**, nezávisle na obrazovce. Titulky, úkol a popis
  menu se ukazují jen při hře; jinde se titulek jen potvrdí (`hotovo`).
- **Nabídka OK při hře:** pozadí, velikost, poloha, originál a **Vstup** (HDMI 1–4, přepne hned
  a zapamatuje). Jde otevřít i bez spojení: volby jsou uložené v televizi (`vzhled`), změny bez
  spojení čekají (`vzhled_zmeny`) a odejdou běžnými zprávami `zmen` až po první zprávě `nastaveni`
  z Androidu po připojení (jinak by je přepsala). Protokol se nezměnil.
- Zkouška bez televize (`node tizen/test/prijimac.test.js`) projde i start se zapamatovaným HDMI,
  nabídku bez spojení a odeslání uložené změny po připojení.

## Nahrání do televize z Androidu (bez počítače)

ReCaption pro Android má aplikaci pro televizi přibalenou (`assets/lg/receiver.ipk`) a umí ji do
televize nahrát sám: Domů (u typu televize LG, dokud se aplikace z televize nikdy nepřipojila),
v telefonu tlačítko pod „Použít telefon pro televizi LG“, a také na stránce Nápověda. Televize potřebuje vývojářský
režim (příprava níže, bez počítače). Uživatel opíše adresu televize a Passphrase z aplikace
Developer Mode; Passphrase se neukládá.

- **Balíček** skládá při sestavení APK úloha `lgBalicek` v `app/build.gradle.kts` kódem
  `core/LgIpk.kt` (sdíleným přes `buildSrc`, bez Node a Pythonu). Obsah je stejný jako
  z `ares-package` 3.2.6 (ověřeno porovnáním po rozbalení a `ares-package -i`): `debian-binary`,
  `control.tar.gz` (jen `control`), `data.tar.gz` (`usr/palm/applications/cz.recaption.tv/…`,
  `usr/palm/packages/cz.recaption.tv/packageinfo.json`). Rozdíl: pevné datum 1. 1. 2000 (webOS 5
  odmítá rok 1970), vlastník 0, práva 644/755. Vedle balíčku je `lg/receiver-manifest.json`
  (id, verze z `appinfo.json`, velikost, sha256) a aplikace ho před instalací ověří.
- **Instalace** (`LgInstaller.kt`): klíč z Key Serveru (`http://<ip>:9991/webos_rsa`) odemčený
  Passphrase → SSH `prisoner@<ip>:9922` (podpis ssh-rsa povolený jen pro toto spojení) → přečte
  `/media/developer/apps/usr/palm/applications/cz.recaption.tv/appinfo.json`. Stejná nebo novější
  verze v televizi = jen spustit, **nikdy nic staršího**. Jinak SFTP do `/media/developer/temp`,
  kontrola bajtů, `luna-send-pub -i luna://com.webos.appInstallService/dev/install`, kontrola verze
  a spuštění (`com.webos.applicationManager/launch`). Limit 2 minuty, jde zrušit.
- **Spárování**: otisk klíče televize se při prvním spojení zapamatuje; když se změní (jiná
  televize na stejné adrese, tovární nastavení), instalace se odmítne, dokud uživatel nezvolí
  „Spárovat znovu“ a nezadá Passphrase. Soukromý klíč SSH se ukládá jen zašifrovaný klíčem
  z Android Keystore (`LgVault.kt`), takže další aktualizace už Passphrase nepotřebuje.
- **Zbývající čas vývojářského režimu** (`LgDevMode.kt`): při spojení se přečte klíč relace
  z `/var/luna/preferences/devmode_enabled` a aplikace se nejvýš jednou za hodinu zeptá služby LG
  `https://developer.lge.com/secure/CheckDevModeSession.dev` (odpověď `HHH:MM:SS`). Ukazuje zbývající
  dny, pod 3 dny upozorní. **Nic se samo neprodlužuje** – to dělá uživatel v televizi
  (Developer Mode → Extend).
- Logika bez Androidu (rozhodnutí o verzi, odpovědi televize a LG, výpočet času) je v
  `core/LgInstall.kt` s testy v `app/src/test/.../LgInstallTest.kt`. Skutečná televize se
  v emulátoru vyzkoušet nedá – ověřit to umí jen uživatel.
- Postup převzat podle projektu PlayCaption (AGPL-3.0, `webos/build.py`,
  `LgInstalator.kt` a další); hlavičky převzatých souborů to uvádějí.

## Nahrání do televize při vývoji

Televize musí mít zapnutý vývojářský režim (aplikace Developer Mode z LG Content Store, účet
z webostv.developer.lge.com, zapnout Dev Mode Status a Key Server). Na počítači nástroj LG:
`npm install -g @webos-tools/cli`.

```
ares-setup-device -a lgtv -i "host=ADRESA_TELEVIZE" -i "port=9922" -i "username=prisoner"
ares-novacom --device lgtv --getkey --passphrase KOD_Z_TELEVIZE
ares-package --no-minify webos/obchod/app -o webos/obchod
ares-install -d lgtv webos/obchod/cz.recaption.tv_1.0.0_all.ipk
ares-launch  -d lgtv cz.recaption.tv
ares-inspect -d lgtv -a cz.recaption.tv      # živý přístup do běžící aplikace
```

Vývojářský režim se po čase sám vypne a aplikace z televize zmizí; v aplikaci Developer Mode
jde prodloužit (kolik zbývá, ukazuje i ReCaption na Androidu – viz výše). Stejný balíček jako
v APK vznikne i bez nástroje LG: `gradlew :app:lgBalicek` → `app/build/generated/lgAssets/lg/receiver.ipk`. Trvalá cesta pro uživatele je obchod LG – viz `obchod/CTI-ME.md`.

Televize z roku 2017 má prohlížeč Chromium 38: kód pro televizi musí zůstat ve starém zápisu
JavaScriptu (žádné `let`/`const`, šipkové funkce, `Promise`, `fetch` ani šablonové řetězce).
