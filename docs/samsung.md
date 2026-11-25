# TV Samsung (Tizen) – titulky z PC

V TV Samsung nejde obraz hry přečíst, proto čte a překládá **program Titulky PS5 na PC** a TV Samsung jen ukazuje
hotové titulky přes hru z HDMI. Potřeba je PC s Windows ve stejné domácí síti jako TV ([návod pro PC](pc.md)).

> Cesta s TV Samsung je nová a zatím neověřená na skutečné TV. Když něco nesedí, napsat na stránku Titulky PS5.

<a id="nahrat"></a>
## 1. Nahrát Titulky PS5 do TV (jen poprvé)

Nejjednodušší je zdarma aplikace **Apps2Samsung** v telefonu s Androidem (bez počítače):

1. V TV otevřít **Aplikace**, na ovladači zadat **1 2 3 4 5**, zapnout **Developer mode** a jako **Host PC IP**
   zadat IP adresu **telefonu** (Nastavení telefonu → Wi-Fi → připojená síť). TV restartovat.
2. Do telefonu nainstalovat **Apps2Samsung** a přihlásit se v něm účtem Samsung (vyrobí podpis pro tuto TV).
3. V Apps2Samsung přidat zdroj **https://github.com/zaoo-star/titulky-ps5**
   ([otevřít v Apps2Samsung](apps2samsung://source?url=https%3A%2F%2Fgithub.com%2Fzaoo-star%2Ftitulky-ps5)).
4. Vybrat **Titulky PS5** a **Install**. Po chvíli je aplikace v seznamu aplikací TV.

Bez telefonu s Androidem jde balíček
[TitulkyPS5-Samsung.wgt](https://github.com/zaoo-star/titulky-ps5/releases/download/latest/TitulkyPS5-Samsung.wgt)
nahrát z PC přes Tizen Studio (s certifikátem Samsung pro tuto TV) – je to složitější cesta.

<a id="hrat"></a>
## 2. Hrát

1. Na PC v Titulky PS5 nastavit **⚙ → Použití → Čtečka pro TV**, spárovat PS5 a v **Titulky → Hra** vybrat hru.
2. Na PC **Spustit**.
3. V TV otevřít **Titulky PS5** a vybrat vstup HDMI s PlayStationem. TV si PC v síti najde sama.
4. Při hře **OK** na ovladači TV otevře vzhled titulků.

[Zpět na nápovědu](./)
