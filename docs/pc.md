# Překlad z PC (Windows)

Titulky čte a překládá počítač, TV s Android TV jen ukazuje hotové titulky. Počítač má víc výkonu,
takže čtení a překlad bývají rychlejší. Hra jde z PS5 kabelem do TV a hraje se dál 🟢 herním účtem;
počítač se k PS5 připojí přes Remote Play 🔵 účtem pro Titulky PS5 (druhý uživatel na PS5, [návod](ps5.md#ucet)).

Potřeba: **Windows 10 nebo 11 (64 bit)** ve stejné domácí síti jako PS5 a TV, asi 1,5 GB místa.

<a id="stazeni"></a>
## 1. Stáhnout a spustit

1. **[Stáhnout TitulkyPS5-PC.zip](https://github.com/zaoo-star/titulky-ps5/releases/download/pc/TitulkyPS5-PC.zip)**
   (asi 250 MB).
2. ZIP rozbalit (pravé tlačítko → **Extrahovat vše**) třeba do Dokumentů a ve složce spustit **TitulkyPS5.exe**.
   Nic se neinstaluje; smazáním složky zmizí.
3. Aplikace není podepsaná, Windows proto může ukázat „Systém Windows ochránil váš počítač“ –
   **Další informace → Přesto spustit**.
4. **Brána firewall** – na dotaz Windows **Povolit přístup** (soukromá síť). Bez toho počítač TV nenajde.

<a id="nastaveni"></a>
## 2. Nastavení v PC (jen poprvé)

- **⚙ Nastavení → Zapnout z TV (Překlad z PC) · Ano** (výchozí) – TV pak čtení na PC zapíná a vypíná sama.
- **⚙ Nastavení → Spouštět s Windows · Ano** – program naběhne po zapnutí PC jen na liště a čeká na TV.
- Překladový model a chiaki-ng (program pro Remote Play) si PC stáhne samo, když je TV poprvé zavolá
  (nebo ručně na Domů: **Připravit** a **Stáhnout**).

<a id="tv"></a>
## 3. V TV

1. V Titulkách PS5 na TV mít PS5 spárovanou jako obvykle (🔵 účet pro Titulky PS5).
2. **Nastavení → Obecné → Překlad z PC · zapnuto.**
3. Na Domů **Spustit** – TV najde PC v síti a zapne na něm čtení.
4. Když PC PS5 ještě spárovanou nemá, na Domů se ukáže **Poslat spárování z TV** – jedno potvrzení a PC se
   spáruje stejně jako TV. (Kdyby PS5 spojení z PC odmítla, spáruje se PC samo: na PC Domů → **Spárovat**.)
5. **Zastavit** na TV čtení na PC zase vypne.

<a id="probuzeni"></a>
## Probuzení PC ze spánku

Po prvním spojení si TV zapamatuje síťovou kartu PC a příště ho zkusí probudit (Wake-on-LAN). Musí to dovolit
PC: ve Správci zařízení u síťové karty **Řízení spotřeby → Povolit tomuto zařízení probudit počítač** a obvykle
i v BIOSu. Většinou funguje jen přes kabel, ne přes Wi-Fi.

<a id="hrani"></a>
## Hraní přímo na počítači

**⚙ → Použití → Hraji na PC** a tlačítko **Hrát**: hra běží v okně chiaki-ng na počítači 🟢 herním účtem
a překlad se ukáže v pruhu přes okno hry. Spárování pro hraní na PC je zvlášť (herní účet) a přepnutí nic nesmaže.
