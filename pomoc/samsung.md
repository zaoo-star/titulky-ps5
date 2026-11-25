# Televize Samsung

TV Samsung si obraz hry sama nepřečte: čte a překládá **ReCaption v telefonu s Androidem** (nebo box s Androidem,
zkušebně počítač – [Windows](pc.md) / [Mac](mac.md)) a do TV posílá hotové věty. Do TV se nahrává jen malá aplikace ReCaption.

Samsung pustí do televize vlastní aplikaci jen ve vývojářském režimu a jen podepsanou pro danou
televizi. Obojí zařídí zdarma aplikace **Apps2Samsung** v telefonu s Androidem – bez počítače,
asi 10 minut. V ReCaptionu v telefonu: průvodce, krok **Televize** (zvolená Samsung) → tlačítko
**Nahrát ReCaption do TV Samsung** (později také Domů nebo Instalace → kapitola 3). Stránka provede vším
a sama pozná, až se ReCaption z televize ozve.

<a id="vyvojarsky"></a>
## 1. Vývojářský režim v televizi

Na ovladači **Domů** → dlaždice **Aplikace** (Apps) → v ní na ovladači zadat **1 2 3 4 5** (ovladač bez čísel:
tlačítko **123** a čísla na obrazovce) → okno Developer mode: **On** → do **Host PC IP** napsat adresu **telefonu**,
ve kterém bude Apps2Samsung (ne počítače). Adresu ukáže stránka ReCaptionu v telefonu (řádek **Host PC IP · …**),
jinak je v telefonu: Nastavení → Wi‑Fi → připojená síť → podrobnosti (Adresa IP). **OK** a televizi restartovat:
podržet vypínač na ovladači, dokud TV nezhasne a znovu nenaběhne. Po restartu je v Aplikacích nahoře nápis
**Developer mode**.

<a id="apps2samsung"></a>
## 2. Apps2Samsung do telefonu (Android)

👉 [Stáhnout Apps2Samsung (stránka vydání, soubor `…-android.apk`)](https://github.com/Apps2Samsung/Apps2Samsung/releases/latest)

Záložní cesty:
- [Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/Apps2Samsung/Apps2Samsung) – sám hlídá aktualizace,
- F-Droid / Droid-ify / Neo Store: přidat repozitář `https://apps2samsung.com/fdroid/repo`,
- domovská stránka: [apps2samsung.com](https://apps2samsung.com) · zdroj: [github.com/Apps2Samsung/Apps2Samsung](https://github.com/Apps2Samsung/Apps2Samsung).

Je potřeba verze **3.3.1 nebo novější** (umí odkaz pro přidání ReCaptionu). Na iPhonu jde Apps2Samsung
jen přes SideStore (jednou s počítačem a kabelem) – návod je na stránce Apps2Samsung.

<a id="zdroj"></a>
## 3. ReCaption do Apps2Samsungu

V průvodci ReCaptionu stisknout **Přidat ReCaption do Apps2Samsungu** (na televizi načíst čtvereček
telefonem) a v Apps2Samsungu přidání potvrdit. Nebo tento odkaz:

👉 [Přidat ReCaption do Apps2Samsungu](https://apps2samsung.com/source?url=https%3A%2F%2Fgithub.com%2Fadusa89%2FReCaption%2Freleases%2Ftag%2Flatest)

Ručně: v Apps2Samsungu **⋯ → Add a source** a vložit adresu
`https://github.com/adusa89/ReCaption`

<a id="instalace"></a>
## 4. Nainstalovat

V Apps2Samsungu vybrat televizi a u ReCaptionu dát **Install**. Napoprvé je potřeba přihlášení účtem
Samsung (zdarma) – Apps2Samsung tím vyrobí podpis pro tuto televizi a příště ho použije znovu.
Pak v televizi spustit ReCaption; průvodce v ReCaptionu ukáže „hotovo“.

**Aktualizace:** když ReCaption hlásí starší aplikaci v televizi, stačí v Apps2Samsungu u ReCaptionu dát
**Update** (stejný účet Samsung jako minule).

Když něco nejde, Apps2Samsung sám napíše, co v televizi chybí (vývojářský režim, Host PC IP…).

<a id="dal"></a>
## 5. Co dál

1. V průvodci ReCaptionu v telefonu **Pokračovat**: Offline překlad, Lepší překlad (DeepL, nepovinné) a v kroku
   **Připojení PlayStationu** spárování PS5 🔵 účtem pro ReCaption ([návod k PS5](ps5.md#ucet)).
2. Při hraní: v TV spustit **ReCaption** (Domů → Aplikace) a v něm vybrat HDMI s PS5; v telefonu na Domů
   **Spustit**. Telefon najde TV sám, stačí stejná Wi‑Fi.
3. **Displej telefonu nesmí zhasnout.** Se zhasnutým displejem telefon (hlavně Samsung) čte titulky až 10× pomaleji a překlad
   skoro stojí. V ReCaptionu **⚙ Nastavení → Obecné → Nezhasínat displej telefonu** (displej zůstane ztlumený; aplikace se na to
   zeptá při prvním Spustit). Telefon ideálně na nabíječce.

<a id="pocitac"></a>
## Bez telefonu s Androidem: počítač (Tizen Studio)

Čtečkou pak je ReCaption v počítači ([Windows](pc.md) / [Mac](mac.md), zkušební), nebo box s Androidem.

1. Vývojářský režim jako v bodu 1, do **Host PC IP** ale adresu **počítače** (Windows: Nastavení → Síť a internet →
   Wi‑Fi nebo Ethernet → Vlastnosti → Adresa IPv4; Mac: Nastavení systému → Wi‑Fi → Podrobnosti → TCP/IP → Adresa IP).
2. Nejjednodušší: [Apps2Samsung pro počítač](https://github.com/Apps2Samsung/Apps2Samsung/releases/latest)
   (Windows, macOS, Linux) – zdroj ReCaption přidat jako v bodu 3 (**⋯ → Add a source** a adresa
   `https://github.com/adusa89/ReCaption`) a dál jako v bodu 4.
3. Jinak Tizen Studio: stáhnout [ReCaption-Samsung.wgt](https://recaption.pages.dev/d/latest/ReCaption-Samsung.wgt)
   (v ReCaptionu pro Android také průvodce Samsung → **Bez telefonu s Androidem: počítač** → Uložit soubor) a
   👉 [nainstalovat Tizen Studio (Samsung)](https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/installing-tv-sdk.html)
   – když odkaz nefunguje, hledat **„Tizen Studio install TV SDK“**. V Tizen Studiu soubor podepsat
   certifikátem Samsung a nahrát do televize (poprvé zabere 30–60 minut).

---
*[English version →](en/samsung.md)*
