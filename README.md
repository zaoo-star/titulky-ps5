# ReCaption

**Nejnovější APK ke stažení:** https://github.com/adusa89/ReCaption/releases/download/latest/ReCaption.apk  
Číslo verze je vidět v aplikaci v sekci Nápověda.  
**Pomoc krok za krokem (i pro iPhone):** [pomoc](pomoc/README.md) – první instalace do Android TV, televize LG, PS5, telefon.

Aplikace pro Android TV: sama se po domácí síti připojí k PlayStationu 5 (nebo přes vzdálené hraní k Xboxu), přečte anglické
titulky hry a v reálném čase je ukáže přeložené (čeština, slovenština a dalších 7 jazyků)
v černém pruhu přes obraz. Hra jde do televize dál kabelem HDMI. Překlad je offline a zdarma.

- `app/src/main/java/cz/recaption/core` – logika bez Androidu (čtení jen titulků, stabilizace, oblasti, slovník, paměť překladů, texty), pokrytá testy
- `app/src/main/java/cz/recaption` – Android část (spojení s PlayStationem, dekódování videa, ML Kit OCR, offline překlad, překryv, obrazovky)
- `app/src/main/java/com/metallic/chiaki/lib`, `app/src/main/cpp`, `third_party/chiaki-ng` – spojení s PlayStationem (knihovna chiaki-ng, AGPL-3.0; naše úpravy jsou popsané v hlavičkách souborů)
- `app/src/test` – unit testy včetně simulace hraní
- `.github/workflows/build.yml`, `ci/emulator-test.sh` – sestavení APK a automatická zkouška v emulátoru Android TV (Android 9, 11, 13, 14); APK se vydá až po úspěšné zkoušce
- `webos` – ReCaption pro televize LG: televize ukazuje hru s pruhem, titulky jí posílá ReCaption na Androidu; přehled je ve `webos/CTI-ME.md`
- `tizen` – totéž pro televize Samsung (společný kód s LG, nepodepsaný balíček .wgt v APK); přehled je v `tizen/CTI-ME.md`
- `pc` – zkušební čtečka pro počítač s Windows (ladění čtení a překladu)

Návod: NAVOD.md

## Licence a převzatý kód

- Aplikace je pod AGPL-3.0 (kvůli knihovně chiaki-ng), zdrojový kód zůstává veřejný.
- Nahrání aplikace do televize LG z Androidu (`LgInstaller.kt`, `LgVault.kt`, `LgDevMode.kt`,
  `core/LgInstall.kt`, `core/LgIpk.kt`) vychází z projektu PlayCaption
  (https://github.com/AdamRuzick4/PlayCaption-app, AGPL-3.0: `LgInstalator.kt`, `LgDevUloziste.kt`,
  `LgDevMode.kt`, `webos/build.py`); hlavičky souborů to uvádějí.
- Spojení SSH/SFTP s televizí: knihovna JSch (https://github.com/mwiede/jsch, licence BSD).
- Xbox – přihlášení účtem Microsoft a spojení přes vzdálené hraní (`XboxAccount.kt`, `XboxLink.kt`,
  `core/Xbox.kt`) vychází z projektu PlayCaption (`XboxUcet.kt`, `XboxSpojeni.kt`, AGPL-3.0), který
  postup převzal z projektů **xbox-xcloud-player** (https://github.com/unknownskl/xbox-xcloud-player,
  unknownskl, MIT) a **XStreaming** (https://github.com/Geocld/XStreaming, Geocld, AGPL-3.0).
- Přenos obrazu z Xboxu: **WebRTC** (https://webrtc.org/), Android sestavení stream-webrtc-android
  (https://github.com/GetStream/webrtc-android, BSD/Apache 2.0).
- Aplikace pro televize Samsung (`tizen/app/platforma.js`, `core/SamsungWgt.kt`, průvodce
  `SamsungInstallActivity.kt`) vychází z přijímače PlayCaption pro Samsung (AGPL-3.0: `tizen/app/js/main.js`,
  `tizen/build.py`, `tizen/INSTALACE-Z-ANDROIDU.md`, `SamsungPruvodce.kt`).

Aplikace není produktem společnosti Sony Interactive Entertainment. „PlayStation“ a „PS5“ jsou jejich ochranné známky.
Aplikace není ani produktem společnosti Microsoft. „Xbox“ je ochranná známka společnosti Microsoft.
