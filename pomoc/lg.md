# Televize LG – vývojářský režim (jen poprvé)

TV LG si obraz hry sama nepřečte: čte a překládá **ReCaption v telefonu s Androidem** (nebo box s Androidem,
zkušebně počítač – [Windows](pc.md) / [Mac](mac.md)) a do TV posílá hotové věty. Do TV se proto nahrává jen malá aplikace
ReCaption – z telefonu, a TV k tomu potřebuje zapnutý **vývojářský režim** (zdarma).

Kde se to v ReCaptionu v telefonu dělá: průvodce, krok **Televize** → tlačítko **Nahrát ReCaption do TV LG**
(později také Domů nebo Instalace → kapitola 3). Stránka v telefonu ukazuje stejné kroky jako tady a hotové sama odškrtne.

<a id="ucet"></a>
## 1. Účet LG pro vývojáře (zdarma)

👉 **[Otevřít stránku LG pro vývojáře](https://webostv.developer.lge.com/)** → vpravo nahoře **Sign In**
→ **Create Account** (založení účtu LG). Stačí e-mail a heslo; účet je zdarma.

Když odkaz nefunguje:
- [Návod LG k aplikaci Developer Mode](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app)
- [Komunitní návod webOS Brew](https://www.webosbrew.org/devmode/)
- nebo hledat: **„LG webOS TV developer account sign up“**

E-mail a heslo je dobré si poznamenat – v televizi se píšou ovladačem.

<a id="aplikace"></a>
## 2. Aplikace Developer Mode v televizi

1. Na ovladači stisknout **Domů** (domeček) a otevřít obchod **LG Content Store** (na novějších TV dlaždice **Apps**).
2. Lupou vyhledat **Developer Mode** a zvolit **Instalovat**.
3. Spustit ji (Domů → seznam aplikací → **Developer Mode**) a přihlásit se účtem z kroku 1.
4. Zapnout **Dev Mode Status** a potvrdit restart. Televize se restartuje.

<a id="key-server"></a>
## 3. Key Server

Po restartu znovu otevřít aplikaci **Developer Mode** a zapnout **Key Server**. Na její obrazovce je adresa televize
(**IP**) a **Passphrase** (6 znaků). V telefonu je ReCaption přečte tlačítky **📷** pod políčky kamerou – nic
se neopisuje (jde to i ručně).

## 4. Nahrát ReCaption

Na stránce **ReCaption v TV LG** v telefonu zvolit **Nahrát do TV** (u starší verze **Aktualizovat v TV**). ReCaption
se v TV nainstaluje a spustí. Příště už Passphrase potřeba není.

## 5. Co dál

1. V průvodci v telefonu **Pokračovat**: Offline překlad, Lepší překlad (DeepL, nepovinné) a v kroku
   **Připojení PlayStationu** spárování PS5 🔵 účtem pro ReCaption ([návod k PS5](ps5.md#ucet)).
2. Při hraní: v TV spustit **ReCaption** (Domů → seznam aplikací) a v něm vybrat HDMI s PS5; v telefonu na Domů
   **Spustit**. Telefon najde TV sám, stačí stejná Wi‑Fi.
3. **Displej telefonu nesmí zhasnout.** Se zhasnutým displejem telefon (hlavně Samsung) čte titulky až 10× pomaleji a překlad
   skoro stojí. V ReCaptionu **⚙ Nastavení → Obecné → Nezhasínat displej telefonu** (displej zůstane ztlumený; aplikace se na to
   zeptá při prvním Spustit). Telefon ideálně na nabíječce.

<a id="pocitac"></a>
## Bez telefonu s Androidem: počítač (zkušební)

Když je čtečkou ReCaption v počítači ([Windows](pc.md) / [Mac](mac.md)) a telefon s Androidem není (třeba jen iPhone), nahraje se aplikace
do TV z počítače komunitním programem **webOS Dev Manager** (zdarma, Windows / macOS / Linux):

1. Kroky 1–3 výš (účet LG, Developer Mode, Dev Mode Status, Key Server).
2. Soubor aplikace pro TV je uvnitř ReCaptionu pro Android: stáhnout
   [ReCaption.apk](https://recaption.pages.dev/d/latest/ReCaption.apk), přejmenovat na
   `ReCaption.zip`, otevřít a ze složky `assets\lg` zkopírovat **receiver.ipk** třeba na plochu.
3. 👉 [Stáhnout webOS Dev Manager](https://github.com/webosbrew/dev-manager-desktop/releases/latest)
   (Windows: soubor `…_x64_en-US.msi`, Mac: `….dmg`) a nainstalovat.
4. Ve webOS Dev Manageru přidat TV (**Add device**: adresa z aplikace Developer Mode, Passphrase z téže obrazovky),
   pak **Apps** → **Install** → vybrat `receiver.ipk`. V TV se objeví aplikace **ReCaption**.

Aktualizace stejně (novější `ReCaption.apk` → nový `receiver.ipk`). Postup s webOS Dev Managerem zatím nebyl
s ReCaptionem vyzkoušený – kdyby nešel, poradí [návod webOS Brew](https://www.webosbrew.org/devmode/).

## Dobré vědět
- Vývojářský režim po čase vyprší (ReCaption ukáže, kolik zbývá). Prodlužuje se v televizi:
  aplikace Developer Mode → **Extend**, nebo ho ReCaption prodlouží sám jednou denně s volbou
  ⚙ → Obecné → **Prodlužovat vývojářský režim** (výchozí vypnuto). Když vyprší úplně, ReCaption z TV zmizí – pak znovu krok 2–4.
- Jedním účtem LG jde mít vývojářský režim jen na jedné televizi najednou.

---
*[English version →](en/lg.md)*
