# Diagnostická zkouška pro televize LG

Není to ReCaption pro LG (to je ve složce `../obchod`). Tahle malá aplikace na skutečné televizi
zjistí, co televize aplikaci dovolí:

1. jestli aplikace umí ukázat hru ze vstupu HDMI a nakreslit přes ni pruh s titulkem,
2. jestli si aplikace umí obraz sama přečíst,
3. jakou verzi systému a prohlížeče televize má.

Hodí se, když se ReCaption zkouší na jiném modelu televize, než na jakém už ověřené je.

## Co zjistila na OLED55B7 (rok 2017, webOS 3.9, Chromium 38)

| Otázka | Výsledek |
|---|---|
| Hra z HDMI v aplikaci s pruhem přes obraz | ano, 1920×1080, bez znatelného zpoždění |
| Přečíst si obraz v aplikaci (plátno, WebGL) | ne, aplikace vidí jen černou |
| Snímek obrazovky od systému | ne – služba `com.webos.service.tv.capture` existuje, ale běžné aplikaci je zakázaná |
| Rychlé výpočty (WebAssembly) | ne |
| Spojení po síti (WebSocket), vlastní adresa v síti | ano |

Závěr: televize je jen obrazovka, číst a překládat musí jiné zařízení (viz `../CTI-ME.md`).

## Sestavení a spuštění

`python webos/zkouska/sestavit.py` vytvoří `webos/zkouska/ReCaption-zkouska.ipk` (nic se
neinstaluje, soubor se necommituje). Do televize se nahraje stejně jako hlavní aplikace
(`ares-install -d lgtv …`, postup je v `../CTI-ME.md`), spouští se jako **ReCaption zkouška**.
Vyberte vstup s PlayStationem a výslednou tabulku vyfoťte.
