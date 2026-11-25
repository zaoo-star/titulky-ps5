# Android TV / Google TV – první instalace ReCaptionu

ReCaption se do televize nebo boxu s Androidem nahraje jen **jednou**. Další verze už se instalují
samy z aplikace: na stránce Domů se ukáže **Aktualizovat**, stačí ho zvolit a instalaci potvrdit.
Spárování i nastavení zůstanou.

**Kód pro Downloader:** **8438934** · krátká adresa (záloha): `recaption.pages.dev/tv`
**Přímý odkaz na aplikaci:** https://recaption.pages.dev/d/latest/ReCaption.apk

Tři cesty, stačí jedna: [Downloader v TV](#downloader) (jen ovladač), [z telefonu s Androidem](#telefon),
[USB flash disk](#usb). Potom [první spuštění](#prvni-spusteni).

**Kde co v TV najít** (názvy se mezi výrobci trochu liší):

| | Google TV (Chromecast, novější Sony, TCL, Philips…) | Starší Android TV (Shield, starší Sony, Philips…) |
|---|---|---|
| Nastavení | ozubené kolo vpravo nahoře na Domů | ozubené kolo vpravo nahoře na Domů |
| Číslo sestavení | Systém → Informace → **Sestavení operačního systému Android TV** | Předvolby zařízení → Informace → **Sestavení** |
| Pro vývojáře | Systém → **Pro vývojáře** | Předvolby zařízení → **Pro vývojáře** |
| Neznámé zdroje | Aplikace → Zabezpečení a omezení → **Neznámé zdroje** | Předvolby zařízení → Zabezpečení a omezení → **Neznámé zdroje** |
| Usnadnění | Systém → **Usnadnění** | Předvolby zařízení → **Usnadnění** |
| Adresa TV (IP) | Síť a internet → připojená síť → **Adresa IP** | Síť → připojená síť → **Adresa IP** |

<a id="instalace"></a>
<a id="downloader"></a>
## Cesta 1: aplikace Downloader (bez počítače a bez telefonu)

Bezplatná aplikace **Downloader** (od AFTVnews, oranžová ikona se šipkou) stáhne ReCaption přímo
do televize. Nic se neposílá z telefonu ani nenahrává na flash disk.

### 1. Nainstalovat Downloader

1. Na ovladači stisknout Domů a otevřít **Google Play** (na Google TV: záložka **Aplikace** → hledat).
2. Vyhledat **Downloader** – správná je ta od **AFTVnews** (ne podobně pojmenované aplikace).
3. Zvolit **Instalovat** a pak **Otevřít**.
4. Při prvním spuštění se Downloader může zeptat na přístup k souborům / médiím → **Povolit**.
   Úvodní okno s novinkami se zavře tlačítkem **OK**.

### 2. Stáhnout ReCaption

1. V Downloaderu je vlevo vybraná položka **Home** a vpravo řádek pro adresu. Po jeho zvolení se objeví
   klávesnice.
2. Napsat kód **8438934** a zvolit **Go**. (Záloha, kdyby kód nešel: adresa **`recaption.pages.dev/tv`**
   – bez `https://`, velká písmena P a L nevadí.)
3. Stažení se rozběhne samo (ukazují se procenta). Trvá chvíli – soubor má desítky MB.
   - Když se místo stahování ukáže stránka s nápisem „Stahuji ReCaption…“, zvolit na ní žlutý
     odkaz **ReCaption.apk**.
   - Když nic nejde, napsat místo krátké adresy celý přímý odkaz výše.

### 3. Povolit Downloaderu instalaci aplikací (jen poprvé)

Android nainstaluje aplikaci jen z aplikace, které to bylo dovoleno. Po stažení se proto většinou ukáže
okno o zabezpečení:

- **Google TV** (Chromecast s Google TV, Google TV Streamer, novější televize Sony, TCL, Philips…):
  v okně zvolit **Nastavení**, v seznamu „Instalace neznámých aplikací“ přepnout **Downloader** na
  **Povoleno** a vrátit se tlačítkem Zpět.
- **Starší Android TV** (a když se okno neukáže): Nastavení → Předvolby zařízení (na některých TV Aplikace) →
  **Zabezpečení a omezení** → **Neznámé zdroje** → zapnout u **Downloader**.
- Když tam položka není, je schovaná ve vývojářských volbách: sedmkrát po sobě zvolit číslo sestavení
  (Google TV: Nastavení → Systém → Informace → **Sestavení operačního systému Android TV**; starší Android TV:
  Nastavení → Předvolby zařízení → Informace → **Sestavení**), až se ukáže „Nyní jste vývojář“. Pak znovu
  Zabezpečení a omezení → Neznámé zdroje.

Názvy se mezi výrobci trochu liší; stačí hledat „neznámé aplikace“ nebo „neznámé zdroje“.

### 4. Nainstalovat

1. Zpátky v Downloaderu se ukáže dotaz systému „Chcete tuto aplikaci nainstalovat?“ → **Instalovat**.
   (Kdyby se neukázal, stáhnout soubor v Downloaderu znovu – teď už se zeptá.)
2. Může se objevit okno **Play Protect** – viz [Když instalace nejde](#play-protect).
3. Po „Aplikace byla nainstalována“ zvolit **Otevřít** – ReCaption se spustí a provede prvním
   nastavením ([první spuštění](#prvni-spusteni)).
4. Downloader pak nabídne smazání staženého souboru → **Smazat** (ušetří místo, už není potřeba).

### 5. Jak ReCaption otevřít příště

- **Google TV:** Domů → **Aplikace** (nebo řada „Vaše aplikace“) → ReCaption.
- **Starší Android TV:** Domů → řada **Aplikace** → ReCaption. Ikona se tam dá přesunout
  dopředu (podržet OK → Přesunout).

Downloader už potom není potřeba – může zůstat, nebo se odinstaluje. **Aktualizace už jdou
v ReCaptionu samy** (Domů → Aktualizovat). Při první aktualizaci se televize jednou zeptá na
povolení instalace z ReCaptionu – povolí se stejně jako výše u Downloaderu.

<a id="telefon"></a>
## Cesta 2: z telefonu s Androidem (bez Downloaderu)

1. Do telefonu nainstalovat ReCaption: [ReCaption.apk](https://recaption.pages.dev/d/latest/ReCaption.apk)
   ([povolit instalaci](telefon.md#android)). V telefonu se otevře **Přihlášení pro TV**.
2. **V TV jednou zapnout ladění přes síť:** sedmkrát zvolit číslo sestavení (Google TV: Nastavení → Systém → Informace →
   **Sestavení operačního systému Android TV**; starší Android TV: Nastavení → Předvolby zařízení → Informace →
   **Sestavení**), až se ukáže „Nyní jste vývojář“ → zpět → **Pro vývojáře** (Systém, případně Předvolby zařízení) →
   zapnout **Ladění USB** (případně **Ladění přes síť** / **ADB**).
   Fire TV: Moje Fire TV → Možnosti pro vývojáře → **Ladění ADB**.
3. V telefonu v **Přihlášení pro TV** dole v kartě „Mám TV LG nebo Samsung“ zvolit **Nahrát ReCaption do Android TV**.
   Telefon TV v síti najde sám (nebo se adresa TV opíše z TV: Nastavení → Síť a internet → připojená síť → Adresa IP).
4. TV se zeptá **„Povolit ladění?“** – zaškrtnout **Vždy povolit z tohoto počítače** a **OK**. ReCaption se nahraje
   a spustí. Pak se ladění v TV může zase vypnout – další verze se instalují z aplikace samy.

Novější Google TV, které nabízejí jen **Bezdrátové ladění** s párovacím kódem, tahle cesta zatím neumí –
zbývá Downloader výš.

<a id="usb"></a>
## Cesta 3: USB flash disk

APK se stáhne z přímého odkazu výše v telefonu nebo počítači, nahraje na flash disk, ten se zapojí do
televize a APK se otevře správcem souborů (např. **File Commander** z Google Play). Povolení
„Neznámé zdroje“ pak dostane správce souborů, ne Downloaderu.

<a id="prvni-spusteni"></a>
## První spuštění – průvodce v TV

Průvodce má 9 kroků: **Jazyky** → Zařízení → Konzole → Televize → Offline překlad → Lepší překlad (DeepL) →
Zobrazení titulků → Připojení PlayStationu → Hotovo.

- V kroku **Jazyky** se pod jazyky volí, jak dál: **Nastavit telefonem nebo počítačem (doporučeno)** – přihlášení,
  spárování PS5 a DeepL na jedné stránce v telefonu nebo počítači – nebo **Nastavit přímo na TV** (vše ovladačem).
  Která cesta pro které zařízení: [Začít s ReCaptionem](zacit.md).
- **Zařízení:** Android TV nebo box s Androidem. **Televize:** Android TV.
- **Připojení PlayStationu:** předem je potřeba 🔵 účet pro ReCaption na PS5 – [návod k PS5](ps5.md#ucet).

<a id="zobrazeni"></a>
### Zobrazení titulků (Usnadnění)

Aby ReCaption mohl kreslit pruh přes hru, musí se mu jednou povolit Usnadnění:

1. V kroku **Zobrazení titulků** zvolit **Otevřít nastavení TV** – otevře se seznam Usnadnění.
   (Ručně: Google TV Nastavení → Systém → **Usnadnění**; starší Android TV Nastavení → Předvolby zařízení →
   **Usnadnění**.)
2. Dole v seznamu zvolit **ReCaption** → zapnout → potvrdit dotaz systému (**OK** / **Povolit**).
3. Tlačítkem **Zpět** na ovladači se vrátit do ReCaptionu – řádek se odškrtne a průvodce pokračuje.

![Krok Zobrazení titulků v průvodci (anglická verze aplikace)](obrazky/pruvodce-zobrazeni-titulku.png)
*Krok 7 průvodce (obrázek z anglické verze aplikace): „Open TV settings“ = **Otevřít nastavení TV**,
„Continue without it“ = **Pokračovat bez toho**.*

Když TV obrazovku Usnadnění nemá, ReCaption zkusí **Zobrazení přes jiné aplikace** (⚙ Nastavení → Obecné).
Po aktualizaci ReCaptionu může být potřeba Usnadnění pro ReCaption vypnout a znovu zapnout.

<a id="play-protect"></a>
## Když instalace nejde

- Okno **Play Protect** („Aplikace byla zablokována“ / „Neznámá aplikace“): zvolit
  **Další podrobnosti** → **Přesto nainstalovat**. ReCaption není v Google Play, proto ho Play Protect
  nezná – je to v pořádku.
- Když tam ta možnost není: Google Play → ikona účtu → **Play Protect** → ozubené kolo → vypnout
  „Kontrolovat aplikace pomocí Play Protect“, nainstalovat a kontrolu zase zapnout.
- **„Aplikace nebyla nainstalována“:** málo místa – smazat v televizi něco nepotřebného (i staré soubory
  v Downloaderu: vlevo **Files**). Pomáhá i restart televize.
- **„Aplikace je v konfliktu s existujícím balíčkem“:** v televizi je stará verze s jiným podpisem
  (verze do 0.60). Odinstalovat ji (Nastavení → Aplikace → ReCaption → Odinstalovat) a nainstalovat znovu.
- **Downloader ukazuje chybu stahování:** zkontrolovat, že je televize připojená k internetu, a zkusit
  přímý odkaz místo krátké adresy.

---
*[English version →](en/android-tv.md)*
