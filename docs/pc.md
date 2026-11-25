# Překlad z PC (Windows)

Titulky čte a překládá počítač, TV s Android TV jen ukazuje hotové titulky. Počítač má víc výkonu,
takže čtení a překlad bývají rychlejší. Hra jde z PS5 kabelem do TV a hraje se dál 🟢 herním účtem;
počítač se k PS5 připojí přes Remote Play 🔵 účtem pro Titulky PS5 (druhý uživatel na PS5, [návod](ps5.md#ucet)).

Potřeba: **Windows 10 nebo 11 (64 bit)** ve stejné domácí síti jako PS5 a TV, asi 1,5 GB místa.

Místo Android TV jde i **TV LG** nebo **Samsung** s aplikací Titulky PS5 – návod [TV LG](lg.md), [TV Samsung](samsung.md).

<a id="stazeni"></a>
## 1. Stáhnout a spustit

1. **[Stáhnout TitulkyPS5-PC.zip](https://github.com/zaoo-star/titulky-ps5/releases/download/pc/TitulkyPS5-PC.zip)**
   (asi 200 MB).
2. ZIP rozbalit (pravé tlačítko → **Extrahovat vše**) třeba do Dokumentů a ve složce spustit **TitulkyPS5.exe**.
   Nic se neinstaluje; smazáním složky zmizí.
3. Aplikace není podepsaná, Windows proto může ukázat „Systém Windows ochránil váš počítač“ –
   **Další informace → Přesto spustit**.
4. **Brána firewall** – na dotaz Windows **Povolit přístup** (soukromá síť). Bez toho počítač TV nenajde.
   Když se Windows nezeptá a TV PC nenajde: Start → **Povolit aplikaci přes bránu Windows Firewall** →
   **Změnit nastavení** → u **TitulkyPS5** zaškrtnout **Soukromá** (když v seznamu není: **Povolit jinou aplikaci** →
   TitulkyPS5.exe). Domácí síť musí být ve Windows nastavená jako **Soukromá** (Nastavení → Síť a internet →
   vlastnosti sítě).

<a id="nastaveni"></a>
## 2. Nastavení v PC (jen poprvé)

- **⚙ Nastavení → Zapnout z TV (Překlad z PC) · Ano** (výchozí) – TV pak čtení na PC zapíná a vypíná sama.
- **⚙ Nastavení → Spouštět s Windows · Ano** – program naběhne po zapnutí PC jen na liště a čeká na TV.
- Překladový model a chiaki-ng (program pro Remote Play) si PC stáhne samo, když je TV poprvé zavolá
  (nebo ručně na Domů: **Připravit** a **Stáhnout**).
- **Titulky → Hra** – vybrat hru, kterou hraješ (třeba **Grand Theft Auto VI**). Hru si PC z TV nebere; jen s vybranou
  hrou umí číst i telefon a menu ve hře, s „Univerzální“ jen titulky.
- Režim **⚙ → Použití → Čtečka pro TV** se zapne sám, když TV poprvé zavolá.

<a id="tv"></a>
## 3. V TV

1. **⚙ Nastavení → Obecné → Překlad z PC · zapnuto.**
2. Na Domů musí svítit **Pruh přes obraz · povoleno** – jinak povolit zobrazení titulků
   ([návod](android-tv.md#zobrazeni)).
3. Na Domů **Spustit překlad z PC** – TV najde PC v síti a zapne na něm čtení. PS5 musí být zapnutá.
4. Když PC PS5 ještě spárovanou nemá a TV ji spárovanou má, ukáže se na Domů **Poslat spárování z TV** –
   jedno potvrzení a PC použije spárování z TV.
5. Jinak se PS5 spáruje přímo na PC: Domů → **Spárovat**. Přihlásit se 🔵 účtem pro Titulky PS5 (čtvereček telefonem,
   nebo **Přihlásit v prohlížeči**) a na PS5 **pod stejným 🔵 uživatelem** vyvolat **Spárovat zařízení** a osmimístné
   číslo opsat do PC. Když je číslo z jiného účtu než přihlášení (třeba z 🟢 herního), PS5 spárování odmítne
   (chyba 403 / 0x80108b02) – pak vyvolat číslo pod správným uživatelem. Po spárování se na PS5 přepnout zpět
   na 🟢 herní účet.
6. **Zastavit** na TV čtení na PC zase vypne.

<a id="deepl"></a>
## DeepL na PC (volitelné)

**⚙ Nastavení → DeepL → Klíč DeepL** – vložit klíč ([kde ho vzít](deepl.md)). **DeepL online**: **Ano** = DeepL pro
všechno, **Jen telefon** = DeepL jen pro zprávy v telefonu ve hře, titulky offline. Bezplatný klíč má omezený počet
znaků; když dojde, PC samo přepne na offline překlad.

<a id="probuzeni"></a>
## Probuzení PC ze spánku

Po prvním spojení si TV zapamatuje síťovou kartu PC a příště ho zkusí probudit (Wake-on-LAN). Musí to dovolit
PC: ve Správci zařízení u síťové karty **Řízení spotřeby → Povolit tomuto zařízení probudit počítač** a obvykle
i v BIOSu. Většinou funguje jen přes kabel, ne přes Wi-Fi.

<a id="hrani"></a>
## Hraní přímo na počítači

**⚙ → Použití → Hraji na PC** a tlačítko **Hrát**: hra běží v okně chiaki-ng na počítači 🟢 herním účtem
a překlad se ukáže v pruhu přes okno hry. Spárování pro hraní na PC je zvlášť (herní účet) a přepnutí nic nesmaže.
