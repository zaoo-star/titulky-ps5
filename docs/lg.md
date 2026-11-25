# TV LG (webOS) – titulky z PC

V TV LG nejde obraz hry přečíst, proto čte a překládá **program Titulky PS5 na PC** a TV LG jen ukazuje hotové titulky
přes hru z HDMI. Potřeba je PC s Windows ve stejné domácí síti jako TV ([návod pro PC](pc.md)).

> Cesta s TV LG je nová a zatím neověřená na skutečné TV. Když něco nesedí, napsat na stránku Titulky PS5.

<a id="vyvojar"></a>
## 1. Vývojářský režim v TV (jen poprvé)

1. Na PC založit zdarma účet vývojáře LG na [webostv.developer.lge.com](https://webostv.developer.lge.com/).
2. V TV z obchodu LG (LG Content Store / Apps) nainstalovat aplikaci **Developer Mode**.
3. V ní se přihlásit účtem vývojáře, zapnout **Dev Mode Status** a TV restartovat.
4. Po restartu v Developer Mode zapnout i **Key Server** a opsat si **IP adresu** a **Passphrase** (6 znaků).

Vývojářský režim po čase vyprší (počet hodin ukazuje aplikace Developer Mode) – prodlouží se v ní tlačítkem
**Extend**. Když vyprší, aplikace Titulky PS5 z TV zmizí a nahraje se znovu stejně.

<a id="nahrat"></a>
## 2. Nahrát Titulky PS5 do TV

**Z PC (doporučeno):**

1. Stáhnout [TitulkyPS5-LG.ipk](https://github.com/zaoo-star/titulky-ps5/releases/download/latest/TitulkyPS5-LG.ipk).
2. Nainstalovat do PC program **webOS Dev Manager** (zdarma, [stažení](https://github.com/webosbrew/dev-manager-desktop/releases) –
   pro Windows soubor `…_x64_en-US.msi`).
3. V něm **+ Add Device** → **Use Developer Mode** → vyplnit IP adresu a Passphrase z aplikace Developer Mode → Finish.
4. **Apps → Install** → vybrat stažený `TitulkyPS5-LG.ipk`. Po chvíli je Titulky PS5 v seznamu aplikací TV.

**Z telefonu s Androidem:** nainstalovat do telefonu [TitulkyPS5.apk](https://github.com/zaoo-star/titulky-ps5/releases/download/latest/TitulkyPS5.apk),
v pomocníkovi zvolit **Použít telefon pro televizi LG** a pak **Nahrát do TV** (adresa a Passphrase jako výš).

<a id="hrat"></a>
## 3. Hrát

1. Na PC v Titulky PS5 nastavit **⚙ → Použití → Čtečka pro TV**, spárovat PS5 a v **Titulky → Hra** vybrat hru.
2. Na PC **Spustit**.
3. V TV otevřít **Titulky PS5** a vybrat vstup HDMI s PlayStationem. TV si PC v síti najde sama
   (stav vlevo nahoře: „Titulky PS5 · připojeno“).
4. Při hře **OK** na ovladači TV otevře vzhled titulků (pozadí, písmo, poloha…).

[Zpět na nápovědu](./)
