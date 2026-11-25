# ReCaption pro televize LG – verze pro obchod

Aplikace, která se podává do obchodu LG (LG Content Store). Televize ukáže hru ze vstupu HDMI
a přes ni pruh s překladem. Číst a překládat musí ReCaption na Androidu (telefon, tablet nebo box; typ TV „LG“
v průvodci nebo v Nastavení) nebo ReCaption pro PC / Mac (⚙ → Použití → Čtečka pro TV) ve stejné domácí síti.
`app.js`, `style.css` a `index.html` jsou společné s aplikací pro Samsung (`../../tizen`).

Tohle je jediná aplikace ReCaption pro televizi; přehled všeho kolem LG je v `../CTI-ME.md`.
Zprávy mezi Androidem a televizí popisuje `app/src/main/java/cz/recaption/core/TvSender.kt`
a v televizi je obsluhuje jen `app/app.js` (`hledej`, `zkus`, `prevezmi`). Diagnostická zkouška
ve `../zkouska` po síti nic nepřijímá.

## Co je ve složce

- `app/` – aplikace (id `cz.recaption.tv`, verze v `app/appinfo.json`). **Id se po podání už nedá
  změnit.** Texty jsou česky (i pro slovenštinu) a anglicky, podle jazyka televize; tlačítko Jazyk na úvodní obrazovce je přepne.
- `ikona-400.png` – ikona, která se nahrává do obchodu zvlášť. `ikony.py` nakreslí ji i ikony v balíčku.
- `snimky/` – snímky obrazovky 1920×1080 pro obchod, česky a anglicky (stav aplikace v TV 1.5.40):
  `1-domu` úvodní obrazovka, `2-ukazka` Ukázka, `3-napoveda` Nápověda, `4-hdmi` obrazovka hry s nabídkou OK.
  Znovu je vyrobí `NODE_PATH=$(npm root -g) node webos/obchod/snimky.js` (Playwright, Chromium) ze současného `app/`.
  Obrazovka hry nemá v počítači vstup HDMI – pro snímek se pod ni podloží nakreslená scéna jako v Ukázce.
- `dokumenty/` – dva dokumenty pro LG vyplněné do jejich šablon (šablony z `documents_for_app_qa.zip` na
  webostv.developer.lge.com/distribute/app-self-checklist – checklist 5.0 a UX scenario 4.3, ověřeno 23. 11. 2026):
  - `ux_scenario_ReCaption.pptx` – popis obrazovek pro testery (anglicky, snímky z `snimky/`). Na titulní straně
    a v historii chybí jméno, e-mail a datum podání („(fill in)“, „__“).
  - `self_evaluation_checklist_5.0.xlsx` – kontrolní seznam. Body, které se aplikace netýkají, mají „NA“,
    obsahové body druhého listu „Pass“. **26 bodů prvního listu je bez výsledku** – musí se vyzkoušet na televizi
    (seznam v oddílu Co udělat majitel); LG vrací podání, kde výsledek neodpovídá skutečnosti. Na druhém listu zbývá
    bod 5 (jestli v obchodě LG už není aplikace stejného jména).

## Sestavení a zkouška

- Balíček: `ares-package --no-minify webos/obchod/app -o webos/obchod` (nástroj LG z `@webos-tools/cli`).
  Vznikne `cz.recaption.tv_<verze>_all.ipk`; necommituje se. Stejný balíček (jen s pevným datem
  souborů) skládá i sestavení APK úlohou `lgBalicek` a ReCaption pro Android ho umí do televize
  ve vývojářském režimu nahrát sám (viz `../CTI-ME.md`, Nahrání do televize z Androidu). Verze
  v `app/appinfo.json` rozhoduje, jestli Android televizi aktualizuje – při změně aplikace ji zvyš.
- Do televize ve vývojářském režimu: `ares-install -d lgtv webos/obchod/cz.recaption.tv_<verze>_all.ipk`.
- V počítači: otevřít `app/index.html` v prohlížeči. Značka v adrese vynutí jazyk a obrazovku:
  `#cs`, `#en`, `#en-ukazka`, `#cs-napoveda`, `#domu` (úvodní obrazovka i se zapamatovaným HDMI), `#hra` (obrazovka hry bez vstupu).
- `requiredACG` v `appinfo.json` vyjmenovává služby televize, které aplikace volá (model televize,
  adresa v síti, jazyk). Od webOS 27 bez něj obchod aplikaci nepřijme. Přibude-li volání další
  služby, doplň i sem.

## Podání u LG (seller.lgappstv.com)

Potřeba: účet prodejce, balíček `.ipk`, `ikona-400.png`, snímky, texty níže a dva dokumenty podle
šablon LG – popis ovládání pro testery (UX scenario) a kontrolní seznam (self-checklist). Podání ani účet prodejce
nic nestojí (aplikace zdarma; poplatek LG neuvádí – viz Zdroje).

### Texty do obchodu

Do obchodu jen „ReCaption“ a adresa pro hráče `recaption.pages.dev` (ne GitHub). Zdrojový kód zůstává na GitHubu
kvůli AGPL – odkaz je v UX scenario (Appendix), do popisu pro hráče nepatří. iPhone se jako čtečka neuvádí (zatím nejde).
Popis musí mít 50–4000 bajtů; aplikace pro více zemí musí mít název a popis anglicky.

**Název:** ReCaption

**Krátký popis (EN):** Translated subtitles for console games, shown in a bar over the game.

**Krátký popis (CS):** Přeložené titulky ze hry v pruhu přes obraz.

**Popis (EN):**
ReCaption shows translated subtitles while you play on PlayStation 5 or Xbox. The TV shows the game from its HDMI
input and draws a bar with the translation over it – in Czech, Slovak, Polish, German, Hungarian, Ukrainian, Spanish,
French or Italian.

Reading and translating the subtitles is done by the free ReCaption app on Android (phone, tablet or Android TV box)
or on a Windows PC / Mac in the same home network. The TV finds it automatically and only displays the text; the TV
app itself connects only to your home network. The look of the subtitles (background, text size, colour, position)
can be changed with the TV remote.

Without the companion app the TV app shows the HDMI input and a demo of the translation (Help → Demo).

Free, no ads, no login. Companion app and help: recaption.pages.dev

Not a product of Sony Interactive Entertainment or Microsoft. “PlayStation” and “PS5” are trademarks of Sony
Interactive Entertainment Inc., “Xbox” is a trademark of Microsoft Corporation.

**Popis (CS):**
ReCaption ukazuje přeložené titulky při hraní na PlayStationu 5 nebo Xboxu. Televize ukáže hru ze vstupu HDMI
a přes ni pruh s překladem – do češtiny, slovenštiny, polštiny, němčiny, maďarštiny, ukrajinštiny, španělštiny,
francouzštiny nebo italštiny.

Titulky čte a překládá bezplatná aplikace ReCaption v Androidu (telefon, tablet nebo box s Android TV) nebo v počítači
s Windows / Macu ve stejné domácí síti. Televize si ji najde sama a jen ukazuje text; aplikace v televizi se připojuje
jen do domácí sítě. Vzhled titulků (pozadí, velikost a barva písma, poloha) jde změnit ovladačem televize.

Bez aplikace v telefonu či počítači ukazuje aplikace v televizi vstup HDMI a ukázku překladu (Nápověda → Ukázka).

Zdarma, bez reklam a bez přihlašování. Aplikace ke stažení a nápověda: recaption.pages.dev

Aplikace není produktem společností Sony Interactive Entertainment ani Microsoft. „PlayStation“ a „PS5“ jsou ochranné
známky Sony Interactive Entertainment Inc., „Xbox“ je ochranná známka Microsoft Corporation.

**Klíčová slova (Tag Keywords):** subtitles, translation, PlayStation, Xbox, HDMI, captions

### Pokyny pro testery (Note for Tester, anglicky – shrnutí UX scenario)

```
ReCaption shows translated game subtitles over the HDMI input. The TV app cannot read the HDMI picture itself:
reading and translating is done by a free companion app (Android or Windows/Mac) in the same home network.
No companion app, console or account is needed to test the TV app:

1. First start: Home. HDMI 1-4 (first row), status "ReCaption · searching..." (orange, stays without the
   companion app), then Help, Language and Exit. Arrow keys / pointer move the yellow focus, OK confirms.
   Language switches English / Czech. BACK or Exit closes the app.
2. Help: five setup steps; top right Demo and Back (Back focused). Back returns Home.
3. Help > Demo: sample game scene with an English subtitle and its translation in the bar; sentences change
   every 3.5 s. BACK or OK returns to Help.
4. Home > HDMI 1-4: the chosen input full screen, status top left. OK (or a pointer click) opens the
   "Subtitle look" menu with a sample sentence - Up/Down rows, Left/Right values, last row switches the
   HDMI input. Works without the companion app. BACK closes the menu, next BACK returns Home.
5. The chosen HDMI input is remembered: the next start opens it directly; BACK returns Home.
6. Optional, with the companion app (https://recaption.pages.dev, TV type "LG", same network): the status
   changes to "ReCaption · connected"; real subtitles need a PS5 or Xbox paired with the companion app.

No login, no purchases, no ads, no sound of its own. Network: local only (WebSocket, port 8787).
```

## Co udělat majitel

Vše ostatní (texty, snímky, dokumenty) je připravené. Tohle za majitele udělat nejde:

1. **Rozhodnout dvě věci v aplikaci v TV, než se podá** (viz Rizika níže; obojí změní hlavní sezení):
   - čtvereček „Podpořit“ v TV LG: nechat a v UX scenario přiznat (tak je dokument teď napsaný), nebo ho ve verzi
     pro LG vypnout a odstavec „Voluntary donation“ z UX scenario (Appendix) a odkaz u Paid Content smazat;
   - opravy, které doporučujeme před podáním: barva dlaždice `iconColor` v `appinfo.json` (#F2C14E jako ikona),
     okraj pod textem pruhu v Ukázce, chování Zpět na první obrazovce u webOS 6+ (dotaz na ukončení).
2. **Účet prodejce** na https://seller.lgappstv.com → SIGN UP → Individual Seller → Other Countries except Korea →
   souhlas s podmínkami → ID (6–12 znaků, písmena a číslice), heslo, jméno, datum narození, e-mail (ověřit odkazem
   v e-mailu), bezpečnostní otázka, Company Location = nejbližší země (Česko / Evropa). Nutno mít 18 let. Zdarma.
   Bankovní a daňové údaje jsou potřeba jen u placených aplikací – tady ne.
3. **Vyzkoušet na televizi LG** balíček, který se bude podávat (stejná verze `.ipk`), a do `dokumenty/self_evaluation_checklist_5.0.xlsx`
   (první list, sloupec N „Test Result“) zapsat **Pass** – nebo opravit, když něco nejde. Body bez výsledku:
   - 1 spuštění z nainstalované aplikace; 3 ukončit (Exit / Zpět na úvodní) a spustit znovu z lišty i z „naposledy použitých“,
     po vypnutí a zapnutí TV (dálkovým i ze zásuvky) – aplikace se rozběhne bez chyby (podruhé rovnou na zvoleném HDMI);
   - 2 úvodní obrazovka celá vidět, nic neuřízlé u okrajů; 5 přechody mezi obrazovkami bez rozbitého rozložení;
     6 texty česky i anglicky bez useknutí a překlepů (i nabídka OK); 7 žlutý výběr se pohybuje šipkami i ukazovátkem;
   - 8 nic nebliká; 9 obraz HDMI přes celou obrazovku (s připojenou konzolí nebo jiným zařízením);
   - 10 všechna tlačítka dělají, co je v UX scenario; 11 tlačítko Zpět v Nápovědě a Ukázce;
     12 Exit na úvodní obrazovce aplikaci úplně ukončí (nezůstane v naposledy použitých);
   - 13 nic nezamrzne, TV se nevypne; 14 bez sítě (vytáhnout kabel / vypnout Wi-Fi) a s pevnou i automatickou adresou
     aplikace nespadne, ukazuje „TV bez sítě“ / „hledá se…“ a obraz HDMI;
   - 26 běžný ovladač: šipky, OK, Zpět; 27–29 Magic Remote: ukazovátko, najetí + OK, kliknutí (při hře otevře nabídku);
     31 šipky ve všech obrazovkách a v nabídce OK; 33 OK; 36 HOME ukáže domovskou lištu a po návratu aplikace pokračuje;
     37 ZPĚT: Ukázka → Nápověda → Úvod → ukončení, při hře nejdřív zavře nabídku; 38 EXIT ukončí aplikaci;
     40 čísla a další tlačítka nic nerozbijí;
   - 41 tlačítko Jazyk přepne češtinu/angličtinu a po novém spuštění zůstane; 42 zvuk HDMI je slyšet, aplikace vlastní nemá;
   - 53 po obnovení továrního nastavení TV se aplikace nainstaluje a spustí.
   Na druhém listu bod 5: v LG Content Store vyhledat „ReCaption“ – když tam nic není, napsat **Pass**.
   Kdyby něco nešlo, zapsat to a poslat hlavnímu sezení – nepodávat s „Fail“.
4. **Doplnit do dokumentů**: v `ux_scenario_ReCaption.pptx` na titulní straně jméno, e-mail a datum podání,
   ve 2. snímku (Document History) datum. Případně verzi, když se mezitím změní (teď 1.5.40).
5. **V Seller Lounge: App Management → App Submission**, po kartách:
   - *File Upload*: App ID `cz.recaption.tv`, Service Platform webOS TV, File Type Web, soubor `.ipk`
     (`ares-package --no-minify webos/obchod/app -o webos/obchod`), File Title „ReCaption“, verze jako v `appinfo.json`,
     rozlišení 1920×1080;
   - *Images*: App Icon `ikona-400.png`; Primary Screenshot `snimky/2-ukazka-en.png`; Secondary `1-domu-en.png`,
     `4-hdmi-en.png`, `3-napoveda-en.png` (pro češtinu, nabízí-li Seller Lounge obrázky po jazycích, verze `-cs`);
     Launcher / Splash Background nenahrávat (aplikace je nemá);
   - *Service Info*: země Select All Countries (nebo vybrané), Rating „All“/nejnižší, kategorie Entertainment,
     Customer Support = e-mail majitele nebo https://recaption.pages.dev, App Service Language English + Czech
     (první English), název a popis z oddílu Texty do obchodu (EN, případně CS);
   - *Test Info*: Self Checklist File = `dokumenty/self_evaluation_checklist_5.0.xlsx`, UX Scenario File =
     `dokumenty/ux_scenario_ReCaption.pptx`, Note for Tester = text z oddílu Pokyny pro testery, Device Requirements
     žádné, Remote Controller Magic Remote i běžný, Geo-IP Block No, Paid Content **Not Applicable**, testovací účet žádný;
   - *Preview and Submit*: zkontrolovat, souhlasit se Seller Agreement, potvrdit žádost o QA.
6. **Čekat na výsledek** (e-mail; QA History → QA Note). Zamítnutí = opravit a podat znovu od začátku. Po schválení
   vyjde aplikace týž nebo další den. **Každá další verze aplikace v TV jde znovu přes QA** – verzi pro Android tím
   nebrzdit (Android nahrává do TV ve vývojářském režimu dál sám).

## Rizika schválení

Ověřeno ze zdrojů LG (odkazy dole) je označeno **[LG]**, ostatní je odhad.

- **Aplikace bez dalšího zařízení „nic nedělá“** (odhad, střední riziko): bez čtečky ukáže jen vstup HDMI a „hledá se…“.
  Pravidla LG výslovně nezakazují aplikaci, která potřebuje druhé zařízení, ale testeři zkoušejí podle UX scenario
  a self-checklistu **[LG]** a na fóru jsou zamítnutí, když se aplikace „nespustí“ nebo ukazuje prázdnou obrazovku.
  Proto je v dokumentech i v Note for Tester popsáno, co jde vyzkoušet bez zařízení (Ukázka, nabídka OK, přepnutí HDMI),
  a úvodní obrazovka hned ukazuje stav. Pozor: po zvolení HDMI se příště spouští rovnou do hry (u testera černá obrazovka
  se stavem vlevo nahoře) – je to v dokumentech popsané, Zpět vede na úvodní obrazovku.
- **Zpět na první obrazovce** **[LG]**: průvodce LG chce u webOS 6.0+ po Zpět na vstupní stránce dotaz, zda aplikaci
  ukončit (`webOS.platformBack()` ho ukáže sám, nebo vlastní dotaz + `window.close()`); u webOS 5 a starších domovskou
  lištu. Self-checklist: „A click on the Back button on the entry page of the app must display the Home screen
  (webOS23 – webOS25 only)“. Aplikace teď na úvodní obrazovce Zpět rovnou zavře (`window.close()`) bez dotazu – na
  novějších TV to může být důvod k vrácení. Navíc při startu rovnou na HDMI je „vstupní stránka“ obrazovka hry a Zpět
  vede na úvodní obrazovku, ne ven. Doporučení pro kód: na webOS 6+ volat jen `platformBack()` (systémový dotaz).
- **Barva dlaždice** **[LG, příručka Seller Lounge]**: „Icon background and tile color should match, or QA may reject“.
  Ikona je od 1.5.39 žlutá (#F2C14E), `iconColor` v `appinfo.json` je ale #0E1014 (tmavá). Doporučení: `"iconColor": "#F2C14E"`.
- **Čtvereček „Podpořit“ (QR na dar)**: ověřeno v `app.js` – ukáže se jen po zprávě `podpora` z Androidu (`kafeKresli`,
  `kafeObrazek`), bez spojení s Androidem se v TV nikdy neobjeví, takže ho testeři LG neuvidí. Šablona UX scenario
  ale říká **[LG]**: „Even though there may be no payment process within the app itself, having any related payment process
  outside the app … will categorize the app as having Paid Content“ – a nepřiznaný placený obsah je důvod k zamítnutí
  a změna schválené aplikace bez QA důvod ke stažení z obchodu **[LG]**. Dar, který nic neodemyká, není prodej obsahu
  (odhad), ale schovat ho před testery je riskantnější než ho přiznat. Proto je v UX scenario (Paid Content a Appendix)
  popsaný. Bezpečnější varianta: ve verzi pro LG čtvereček vůbec neukazovat (podle `P.platforma === 'webos'`).
- **PlayStation / Sony / Xbox v textech** (odhad, nízké riziko): checklist kontroluje „Names or descriptions of app that
  infringe trademarks“ **[LG]**. Název aplikace značky neobsahuje, v popisu jsou jen jako konzole, se kterými funguje,
  s větou, že aplikace není produktem Sony ani Microsoftu. Úvodní obrazovka má „Vstup s PlayStationem / PlayStation input“
  a Nápověda „PlayStation 5“ – v pořádku, jen kdyby LG chtělo, nahradit „konzole“.
- **Ukázka – pruh na samém spodku obrazovky** (zjištěno na snímcích): od 1.5.3 nemá pruh pevnou výšku a v Ukázce (bez
  vzhledu z Androidu) je text přilepený k dolnímu okraji. Checklist bod 2 chce texty uvnitř bezpečné oblasti obrazu.
  Doporučení pro kód: v Ukázce dát pruhu okraj (např. vzhled jako z Androidu s `okraj` 1 a polohou kousek nad spodkem),
  pak snímky znovu (`node webos/obchod/snimky.js`).
- **Hledání v síti na pozadí** (odhad, nízké riziko): aplikace zkouší adresy své sítě na portu 8787 (24 spojení naráz,
  znovu po 5 s), i když je tlačítkem HOME schovaná. LG to v pravidlech výslovně nemá; v dokumentech je to popsané.
- **Doba a cena** **[LG]** / odhad: registrace i podání zdarma (LG žádný poplatek neuvádí, ani příručka Seller Lounge);
  pevnou dobu QA LG neuvádí – podle zkušeností vývojářů 3 týdny až 3 měsíce, schválená aplikace vyjde týž nebo další den.
- **Šablony**: LG nepřijme podání se starší verzí self-checklistu **[LG, FAQ]**; 23. 11. 2026 je aktuální 5.0 (použitá).

### Zdroje

- https://webostv.developer.lge.com/distribute/app-approval-process – kroky QA, povinný checklist a UX scenario
- https://webostv.developer.lge.com/distribute/app-ecosystem – individuální / firemní prodejce
- https://webostv.developer.lge.com/distribute/app-self-checklist – pravidla UX (Zpět na vstupní stránce, ukazovátko),
  šablony `documents_for_app_qa.zip`
- https://webostv.developer.lge.com/develop/guides/back-button – Zpět u webOS 6+ (dotaz na ukončení)
- https://webostv.developer.lge.com/develop/references/appinfo-json – `iconColor` = barva dlaždice
- https://webostv.developer.lge.com/faq – stará verze checklistu = neschváleno
- LG Seller Lounge User Guide 11.5 (archiv: archive.org/stream/lg-seller-lounge-user-guide-en-v-11.5) – registrace,
  pole podání, obrázky (ikona 400×400, primární snímek + aspoň 2 další, nejvýš 5), popis 50–4000 bajtů, vydání po schválení
- Zkušenosti s dobou QA: https://www.ottengine.com/blog/lg-seller-lounge-explained ,
  https://forum.webostv.developer.lge.com/t/qa-rejected-unable-to-install-app/407 (odhady, ne údaj LG)

Aplikace nemá přihlášení, platby uvnitř aplikace, reklamy ani vlastní zvuk a obraz.
