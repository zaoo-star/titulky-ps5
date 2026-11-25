# ReCaption pro počítač s Windows (zkušební)

Počítač může dělat čtečku pro **TV LG nebo Samsung** místo telefonu s Androidem – hodí se hlavně
s iPhonem. Hra jde z PS5 kabelem do TV a hraje se 🟢 herním účtem jako vždy. Počítač se k PS5 připojí
přes Remote Play 🔵 účtem pro ReCaption, čte obraz, překládá a hotové věty posílá do ReCaptionu v TV.

Potřeba: **Windows 10 nebo 11 (64 bit)** ve stejné domácí síti jako PS5 a TV, asi 1,5 GB místa.
Mac: **[ReCaption pro Mac](mac.md)** – stejná aplikace, stejné kroky.

<a id="stazeni"></a>
## 1. Stáhnout a spustit

1. **[Stáhnout ReCaption-PC.zip](https://recaption.pages.dev/d/pc/ReCaption-PC.zip)**
   (asi 250 MB).
2. ZIP rozbalit (pravé tlačítko → **Extrahovat vše**) třeba do Dokumentů a ve složce spustit **ReCaption.exe**.
   Nic se neinstaluje; smazáním složky ReCaption zmizí. Nová verze = stáhnout a rozbalit znovu, nastavení zůstává.
3. Aplikace není podepsaná, Windows proto může ukázat „Systém Windows ochránil váš počítač“ –
   **Další informace → Přesto spustit**.

<a id="povoleni"></a>
## 2. Povolení (jen poprvé)

- **Brána firewall** – na dotaz Windows **Povolit přístup** (PS5 a TV se hledají v domácí síti, TV a telefon
  se k počítači připojují).
- Obraz okna hry Windows vydá bez dalšího povolení.

<a id="priprava"></a>
## 3. Příprava (jen poprvé)

Na stránce **Domů**:

- **Překlad · chybí → Připravit** – stáhne překladový model pro zvolený jazyk (asi minuta).
- **chiaki-ng · chybí → Stáhnout** – program pro Remote Play, přes který se počítač k PS5 připojí (asi 120 MB).

<a id="ctecka"></a>
## 4. Čtečka pro TV

1. **⚙ Nastavení → Použití → Čtečka pro TV** (ve Windows je výchozí Hraji na PC).
2. **PS5 · nespárováno → Spárovat** – 🔵 účet pro ReCaption (druhý uživatel na PS5, viz [návod k PS5](ps5.md#ucet)):
   přihlásit se telefonem (i iPhonem) přes čtvereček, pak na PS5 pod 🔵 uživatelem **Nastavení → Systém →
   Remote Play → Spárovat zařízení** (ve starším systému Propojit zařízení) a osmimístné číslo opsat do ReCaptionu.
   Potom na PS5 zpět na 🟢 herní účet.
3. ReCaption do TV: s telefonem s Androidem podle [LG](lg.md) / [Samsung](samsung.md); jen z počítače
   [LG přes webOS Dev Manager](lg.md#pocitac) / [Samsung přes Apps2Samsung pro počítač](samsung.md#pocitac).
4. V TV spustit **ReCaption** a vybrat HDMI s PS5 – TV najde počítač v síti sama.
5. **Spustit.** Pak PS5 zapnout ovladačem – počítač se připojí sám, PS5 sám nebudí. Okno chiaki-ng může zůstat
   za jinými okny, jen ho **neminimalizovat** (minimalizované okno ReCaption na Domů ohlásí s tlačítkem Obnovit okno).
   Zvuk z počítače je vypnutý.
6. Počítač nesmí usnout: na napájení a v ReCaptionu **⚙ → Použití → Nezhasínat PC při překladu · Ano** (dokud běží překlad,
   PC neusne a displej nezhasne; po Zastavit platí zase nastavení Windows).

<a id="hrani"></a>
## 5. Hraní přímo na počítači

**⚙ → Použití → Hraji na PC** a tlačítko **Hrát**: hra běží v okně chiaki-ng na počítači 🟢 herním účtem
a překlad se ukáže v pruhu přes okno hry (⚙ → Výstup: pruh na PC, TV, nebo obojí).

---

## Společné pro Windows i Mac

<a id="ucet"></a>
### Který účet PlayStation (podle použití)

Obě použití mají vlastní spárování a vlastní účet – přepnutí v ⚙ → Použití nic nesmaže.

| ⚙ → Použití | Kde se hraje | Účet při spárování | Na PS5 při spárování |
|---|---|---|---|
| **Čtečka pro TV** | na PS5, obraz kabelem do TV | 🔵 **účet pro ReCaption** (druhý uživatel na PS5, [návod](ps5.md#ucet)) | přepnout na 🔵, Remote Play → Spárovat zařízení; pak zpět na 🟢 |
| **Hraji na PC / na Macu** | v okně chiaki-ng na počítači | 🟢 **herní účet** – druhý účet tu není potřeba | pod 🟢 herním uživatelem Povolit aplikaci Remote Play a Spárovat zařízení |

- E-mail a heslo se píšou jen na stránce Sony – v telefonu přes čtvereček z okna ReCaptionu, nebo tlačítkem
  **Přihlásit v prohlížeči** na tomto počítači. Do ReCaptionu ani do chiaki-ng se heslo nepíše.
- **Pozor u Čtečky pro TV:** prohlížeč v počítači bývá přihlášený 🟢 herním účtem (z hraní nebo z PlayStation Store)
  a Sony se pak na heslo nezeptá a vezme ten. Proto se přihlásit v anonymním okně nebo telefonem. (Herní účet, kterým
  už bylo spárováno Hraji na PC / na Macu, ReCaption pozná sám: „Účet · herní účet“.)
- Ve hře na PS5 (Čtečka pro TV) se hraje dál 🟢 herním účtem. Spárovaný 🔵 účet se k PS5 připojuje sám po Spustit.

<a id="rozdily"></a>
### Windows a Mac

Okno aplikace, stránky Domů / Titulky / ⚙ Nastavení, čtení, překlad, DeepL, profily her i posílání do TV jsou stejné.
Liší se jen to, co dovolí systém:

| | Windows | Mac |
|---|---|---|
| Počítač | Windows 10 / 11, 64 bit | Apple Silicon (M1 a novější), macOS 12+ |
| Balíček | `ReCaption-PC.zip` → ReCaption.exe | `ReCaption-Mac.dmg` → složka Aplikace |
| Výchozí použití | Hraji na PC | Čtečka pro TV |
| Povolení | brána firewall | Nahrávání obrazovky, Místní síť |
| Minimalizované okno chiaki-ng | ReCaption pozná, tlačítko Obnovit okno | nepozná – okno nechat otevřené |
| Klíč DeepL | zašifrovaný přes Windows | Klíčenka |
| Nezhasínat při překladu | ⚙ → Nezhasínat PC (PC neusne, displej nezhasne) | ⚙ → Nezhasínat Mac (navíc víko nechat otevřené) |
| Záznam hry do složky | `zaznamy` vedle složky ReCaption | `~/Library/Application Support/ReCaptionTools/zaznamy` |

---
*[English version →](en/pc.md)*
