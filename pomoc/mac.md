# ReCaption pro Mac (zkušební)

Mac může dělat čtečku pro **TV LG nebo Samsung** místo telefonu s Androidem – hodí se hlavně
s iPhonem. Hra jde z PS5 kabelem do TV a hraje se 🟢 herním účtem jako vždy. Mac se k PS5 připojí
přes Remote Play 🔵 účtem pro ReCaption, čte obraz, překládá a hotové věty posílá do ReCaptionu v TV.

Potřeba: **Mac s Apple Silicon (M1 a novější), macOS 12 nebo novější** ve stejné domácí síti jako PS5 a TV,
asi 2 GB místa. Mac s procesorem Intel zatím ne.
Windows: **[ReCaption pro počítač s Windows](pc.md)** – stejná aplikace, stejné kroky.

<a id="stazeni"></a>
## 1. Stáhnout a spustit

1. **ReCaption-Mac.dmg zatím ke stažení není** – verze pro Mac se ještě připravuje. Tady se odkaz objeví, až bude hotová.
   <!-- po prvním vydání „mac“ vrátit: **[Stáhnout ReCaption-Mac.dmg](https://recaption.pages.dev/d/mac/ReCaption-Mac.dmg)**. -->
2. Soubor otevřít a **ReCaption přetáhnout do složky Aplikace**, pak ho tam spustit.
   Nová verze = stáhnout a přetáhnout znovu, nastavení zůstává.
3. Aplikace není podepsaná u Applu, macOS ji proto poprvé odmítne:
   **Nastavení systému → Soukromí a zabezpečení →** dole u „ReCaption“ **Přesto otevřít**.
   (Starší macOS: pravé tlačítko na ReCaption → **Otevřít** → **Otevřít**.)

<a id="povoleni"></a>
## 2. Povolení (jen poprvé)

- **Místní síť** a **příchozí spojení** – na dotaz macOS **Povolit** (PS5 a TV se hledají v domácí síti,
  TV a telefon se k Macu připojují).
- **Nahrávání obrazovky** – bez něj Mac obraz okna hry nevydá. Na stránce Domů řádek
  „Nahrávání obrazovky · chybí povolení“ → **Povolit**, v Nastavení systému zapnout ReCaption a aplikaci
  otevřít znovu. Po stažení nové verze ReCaptionu se povolení dává znovu.

<a id="priprava"></a>
## 3. Příprava (jen poprvé)

Na stránce **Domů**:

- **Překlad · chybí → Připravit** – stáhne překladový model pro zvolený jazyk (asi minuta).
- **chiaki-ng · chybí → Stáhnout** – program pro Remote Play, přes který se Mac k PS5 připojí.

<a id="ctecka"></a>
## 4. Čtečka pro TV

1. **⚙ Nastavení → Použití → Čtečka pro TV** (na Macu je výchozí, obvykle není co měnit).
2. **PS5 · nespárováno → Spárovat** – 🔵 účet pro ReCaption (druhý uživatel na PS5, viz [návod k PS5](ps5.md#ucet)):
   přihlásit se telefonem (i iPhonem) přes čtvereček, pak na PS5 pod 🔵 uživatelem **Nastavení → Systém →
   Remote Play → Spárovat zařízení** (ve starším systému Propojit zařízení) a osmimístné číslo opsat do ReCaptionu.
   Potom na PS5 zpět na 🟢 herní účet.
3. ReCaption do TV: s telefonem s Androidem podle [LG](lg.md) / [Samsung](samsung.md); jen z Macu
   [LG přes webOS Dev Manager](lg.md#pocitac) / [Samsung přes Apps2Samsung pro počítač](samsung.md#pocitac).
4. V TV spustit **ReCaption** a vybrat HDMI s PS5 – TV najde Mac v síti sama.
5. **Spustit.** Pak PS5 zapnout ovladačem – Mac se připojí sám, PS5 sám nebudí. Okno chiaki-ng může zůstat
   za jinými okny, jen ho **neminimalizovat** (minimalizované okno Mac nepozná a ReCaption ho neohlásí).
   Zvuk z Macu je vypnutý.
6. Mac nesmí usnout: na napájení s otevřeným víkem a v ReCaptionu **⚙ → Použití → Nezhasínat Mac při překladu · Ano**
   (dokud běží překlad, Mac neusne a displej nezhasne; po Zastavit platí zase nastavení macOS).

<a id="hrani"></a>
## 5. Hraní přímo na Macu

**⚙ → Použití → Hraji na Macu** a tlačítko **Hrát**: hra běží v okně chiaki-ng na Macu 🟢 herním účtem
a překlad se ukáže v pruhu přes okno hry (⚙ → Výstup: pruh na Macu, TV, nebo obojí).

---

## Společné pro Windows i Mac

- **[Který účet PlayStation (podle použití)](pc.md#ucet)** – čtečka pro TV 🔵 účtem pro ReCaption, hraní na Macu
  🟢 herním účtem; obě spárování zůstávají vedle sebe.
- **[Windows a Mac – v čem se liší](pc.md#rozdily)**.

---
*[English version →](en/mac.md)*
