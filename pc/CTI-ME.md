# ReCaption pro počítač (zkušební)

Dělá na počítači s Windows totéž co překlad v aplikaci pro Android TV: čte anglické titulky
z obrazu hry a přeloží je bez internetu. Překlad ukáže v pruhu přímo na obrazovce počítače přes
okno s hrou (výchozí), nebo ho pošle do televize LG, případně obojí.

Je to základ budoucí aplikace pro Windows a handheldy (Lenovo Legion Go apod.): hráč hraje přes
chiaki-ng na svém **hlavním účtu** a ReCaption se k PlayStationu vůbec nepřipojuje, jen čte obraz
z okna. Druhý účet ani přihlášení přes telefon tu nejsou potřeba. Zatím běží z repozitáře
(potřebuje Javu 17 a Python), ne jako samostatný instalační balíček.

Druhé použití (⚙ → **Použití → Čtečka pro TV**): PC místo telefonu s Androidem jako čtečka pro TV LG /
Samsung – hra běží na PS5 v TV, PC se připojí druhým účtem a posílá překlad do TV. Viz oddíl Čtečka pro TV.

## Balíček ke stažení (pro hráče)

`ReCaption-PC.zip` skládá na GitHubu `.github/workflows/pc.yml` (Windows, při každé změně `pc/` nebo `core`):
`ReCaption.exe` s vlastní Javou (jpackage), ve složce `app` jary, `cteni.py`, `priprava.ps1` a **vložený Python 3.12
s knihovnami** (`app\python`, RapidOCR, CTranslate2…). Nic se neinstaluje; aplikace pozná přibalený Python
(`Nastroje.pribaleny`) a Připravit pak stáhne jen překladový model (`priprava.ps1 -Python`). Běh balíček vyzkouší:
přibalený Python přečte a přeloží ukázkový titulek (`zkouska_balicku.py`) a okno uloží snímky stránek (artefakt
`snimky-pc`). Z větve je balíček jen artefakt běhu (Actions), z `main` vydání **`pc`**
(`releases/download/pc/ReCaption-PC.zip`) – na něj vede návod pro hráče `pomoc/pc.md`. Pushe jen do `pc/`
a `navrhy/` APK nesestavují (`paths-ignore` v `build.yml`), takže tady `[skip ci]` netřeba – ten by přeskočil i balíček.

## Spuštění

**Dvojklik na `pc\ReCaption.cmd`** otevře okno aplikace (při prvním spuštění a po změně kódu se
předtím asi minutu sestavuje). Potřebuje Javu 17 (Temurin) a pro Připravit i Python 3.10+.

- **Domů** – velký stav (Připraveno / Hledání PS5… / Probouzení PS5… / Spouštění hry… / Běží /
  Čeká na okno hry / Chybí příprava), Hrát (u chiaki-ng) nebo Spustit / Zastavit a řádky
  „Popis · hodnota“ s tečkou jako v aplikaci pro TV: chiaki-ng (chybí = Stáhnout), PS5 (nespárováno
  = Spárovat, zapnutá / klidový režim / nenalezena = Hledat), u jiného programu okno hry
  (nenalezeno = tlačítko Vybrat okno), překlad (chybí = tlačítko Připravit – stáhne knihovny
  a model asi 0,6 GB do `%LOCALAPPDATA%\ReCaptionTools`, do systému nic neinstaluje), výstup,
  rychlost čtení a poslední titulek.
- **Titulky** – nahoře **Hra** (profil hry z `core/GameProfiles.kt`, výchozí Univerzální) a jen u her, které je
  znají, přepínače jako na Androidu od 0.97.59: **Pokyny pod titulky** a **Úkol pod titulky** (GTA, výchozí zapnuto),
  **Překlad menu hry** (GTA VI, výchozí vypnuto) a rada „Ve hře: pozadí titulků · zapnout“ (GTA VI, klik = vysvětlení;
  texty `gp_bg_gta6*` z `core/I18nGame.kt`). Dřívější „Rozsah čtení“ (klíč `textyHry`) se jen čte: kdo měl „Jen titulky“,
  má přepínače vypnuté, dokud některý sám nepřepne (`core/GameScope`); pod tím vzhled pruhu se stejnými hodnotami jako na Androidu: velikost
  písma (Podle titulků nebo 12–48), barva, tučné, neprůhlednost pozadí 0–100 % (pod 40 % má písmo tmavý
  obrys), okraj pruhu 0–3, poloha (přes titulky / nad titulky / nahoře / dole), originál pod překladem
  a Zkušební pruh na 4 s. Vzhled platí hned, i za běhu; změna hry, úkolu nebo menu spustí čtení znovu.
  Zvolená hra je i na Domů („Hra · Grand Theft Auto VI“; univerzální = řádek není).
- **⚙ Nastavení** – nahoře **Použití** (Hraji na PC – výchozí / Čtečka pro TV), okno hry (část titulku okna, Vybrat = nabídka oken, kde může běžet hra:
  chiaki / PS Remote Play / Xbox nahoře, skrytá systémová okna ne), ukázková hra
  bez PlayStationu, jazyk překladu, výstup (Pruh na PC / TV LG / Samsung / PC i TV), příprava,
  DeepL (zapnutí, klíč, odkaz na klíče v účtu DeepL) a Záznam (výpis čtení). Změna okna, jazyka,
  výstupu nebo DeepL za běhu čtení sama spustí znovu.

**Profil hry a překlad jako na Androidu (od 21. 11. 2026, `src/Ctecka.kt`):**
- jména, místa, auta a ulice zůstávají anglicky (`Glossary.prepare` – offline i DeepL), herní pojmy mají
  pevný překlad, ručně zadané věty (`BuiltinPhrases`), slang se upraví a čeština prochází `Natural.czech`;
- rod postav (`Glossary.genders` → `CzechGender`: „Lucia šla“), hlášky a menu hry se nečtou
  (`GameProfile.screen`), v Diablu IV se nečte chat a pozná se mluvčí „Jachal says:“;
- **paměť překladů** (`TranslationCache`) – stejná věta se nepřekládá znovu; soubor
  `%LOCALAPPDATA%\ReCaptionTools\pamet-<jazyk>-<hra>.txt`;
- **barvy jmen** z profilu hry (GTA VI Jason žlutě, Lucia modře) a naučené z obrazu: u nového titulku se
  jménem `cteni.py` (příkaz `BARVA`) vrátí barvu nejsvětlejších bodů jména, po 2 shodách platí (`NameColor`);
- **pokyny GTA** („Take your shot“) menším šedým řádkem pod replikou, bez volby Pokyny pod titulky se vynechají;
- **úkol pod titulky GTA** (`Picked.objective`, `ObjectiveTracker`): vyčlení se z titulků vždy, s volbou se přeloží
  a ukáže ve vlastním pruhu přes anglický úkol (`Pruh(naMiste = true)`) a do TV zprávou `ukol`; drží se, dokud je
  vidět, v menu hry se schová. Na skutečné hře neověřeno;
- **překlad menu GTA VI** (`core/GameMenu.kt`, jen s volbou Překlad menu hry): když v pruhu není titulek,
  jednou za 1,5 s (v menu za 0,4 s) se přečte horních 60 % obrazu (`cteni.py`, příkaz `MENU`) a překlad
  menu se ukáže vedle menu, řádky pod sebou; titulky se mezitím nečtou. Na skutečné hře neověřeno.

**Výstup do TV LG / Samsung jako z telefonu:** každý titulek nese vzhled (`TvLook`: písmo, pozadí, barva,
okraj, poloha, kde byl anglický titulek, originál, u menu místo vedle menu), TV dostává volby pro nabídku
**OK při hře** (`TvSettings`) – změna z ovladače TV se uloží jako z okna PC – a každé 2 s stav vlevo nahoře
(konzole, překlad, hra).

**DeepL** (`src/DeeplPc.kt`) jede stejně jako na Androidu od 0.97.2: počká se na překlad DeepL
(nejvýš ~2,5 s) a ukáže se jen jedna varianta, offline jen když DeepL nepomůže; čeština prochází
`Natural.czech`. Požadavek, odpověď, chyby a přestávky jsou z `core/Deepl.kt`. Klíč je zašifrovaný
přes Windows (DPAPI) v `%LOCALAPPDATA%\ReCaptionTools\deepl.bin` a nikam se nevypisuje. Z příkazové
řádky: `--deepl-klic SOUBOR` klíč uloží, `--deepl` zapne překlad přes DeepL.

Nastavení se ukládá do `%LOCALAPPDATA%\ReCaptionTools\aplikace.properties`.

**Hraní přes chiaki-ng v ReCaptionu** (Nastavení → Hra běží v → chiaki-ng v ReCaptionu, výchozí;
`src/Chiaki.kt`): hráč nemusí obsluhovat druhý program.

1. Domů → „chiaki-ng · chybí“ → **Stáhnout**: přenosná verze z posledního vydání
   `streetpea/chiaki-ng` (x64 nebo ARM, ~120 MB) do `%LOCALAPPDATA%\ReCaptionTools\chiaki-ng`,
   nic se neinstaluje. Nainstalovaný chiaki-ng (Program Files) se najde taky.
2. „PS5 · nespárováno“ → **Spárovat** (nebo ⚙ → Spárovat PS5): průvodce ReCaptionu ve třech krocích,
   do chiaki-ng se nic nezadává (`src/Parovani.kt`):
   1. **PS5 v síti** – najde zapnutou PS5 (klidový režim nestačí), u více konzolí „Jiná PS5“.
   2. **Účet PlayStation** (hlavní účet, na kterém se hraje) – bez opisování čísla účtu:
      - telefonem: čtvereček vede na stránku z PC (`core/LoginServer.kt` jako na Android TV, vlastní
        text stránky pro PC); po přihlášení u Sony se na ni vloží adresa „redirect“ a PC si účet vezme;
      - nebo **Přihlásit v prohlížeči** tohoto PC: po přihlášení stačí adresu stránky „redirect“
        zkopírovat, ReCaption ji samo převezme ze schránky.
      Uloží se jen číslo a jméno účtu (`aplikace.properties`), příště se krok rovnou odškrtne („Jiný účet“).
      Windows se při prvním otevření stránky může zeptat na povolení sítě pro Javu – bez něj funguje
      jen přihlášení v prohlížeči PC.
   3. **Kód z PS5** – 8 číslic (PS5: Nastavení → Systém → Remote Play → Spárovat zařízení (ve starším systému Propojit zařízení)) → Spárovat.
   Spárování dělá ReCaption samo (`src/Registrace.kt`, protokol z `chiaki-ng/lib/src/regist.c`
   a `rpcrypt.c` pro PS5) a výsledek zapíše tam, kam si ho ukládá chiaki-ng
   (`HKCU\Software\Chiaki\Chiaki\registered_hosts`, `Chiaki.uloz` – stejný tvar jako QSettings
   v chiaki-ng). Odtud ho chiaki-ng vezme při hře a ReCaption jméno konzole a číslo pro probuzení.
   Otevřený chiaki-ng by si seznam konzolí při zavření přepsal, proto ho průvodce nabídne zavřít.
   Chyby jsou krátké stavy: „Kód · neplatný“, „PS5 · obrazovka s kódem zavřená“, „Účet · neplatný“…
3. **Hrát**: ReCaption najde PS5 v síti (zpráva z `core/Ps5.kt`), z klidového režimu ji probudí
   (zpráva WAKEUP jako v `chiaki-ng/lib/src/discovery.c`), spustí
   `chiaki --exit-app-on-stream-exit --fullscreen stream <konzole> <adresa>` a čte okno toho procesu
   (`pid:<číslo>` v `OknoHry` i `cteni.py`). Konec hry v chiaki-ng = konec čtení; Zastavit zavře obojí.
   Výpis chiaki-ng je v `%LOCALAPPDATA%\ReCaptionTools\chiaki-vystup.txt`.

Ladění: `recaption-pc.bat --hledej-ps5` vypíše chiaki-ng, spárované konzole a PS5 v síti.

**Hra v jiném programu** (Hra běží v → Jiný program, např. oficiální PS Remote Play): čte se okno
podle části titulku. Okno může být přes celou obrazovku (ne výhradní režim celé obrazovky) nebo
zakryté jiným oknem, ale nesmí být minimalizované.

## Čtečka pro TV (PS5 v TV, PC místo telefonu)

Pro hráče s iPhonem (iPhone čtečkou být nemůže) nebo bez telefonu s Androidem: PC dělá totéž co čtečka
na Androidu pro TV LG / Samsung. Zapíná se v ⚙ → **Použití → Čtečka pro TV (hra na PS5 v TV)**
(klíč `pouziti` = `ctecka`; výchozí `pc` = dosavadní hraní na PC, uložené klíče se nemění). Při
zapnutí se ukáže krátký popis v dialogu.

- **Hra** jde z PS5 kabelem HDMI do TV a hraje se dál 🟢 herním účtem. PC se k PS5 připojí přes
  Remote Play 🔵 **účtem pro ReCaption** – druhým uživatelem na PS5 s povoleným Remote Play (jako
  čtečka na Androidu; herním účtem by PS5 hráči vypnula ovladač a zhasla obraz).
- **Spárování** (Domů → PS5 · nespárováno → Spárovat) je tentýž průvodce, jen krok 2 chce 🔵 účet pro
  ReCaption (uloží se zvlášť: `ucetCteckaId` / `ucetCteckaJmeno`; účet z hraní na PC, `ucetId`, se jako
  herní nepřijme: „Účet · herní účet · přihlásit účtem pro ReCaption“) a krok 3 radí na PS5 přepnout na
  uživatele 🔵, povolit Remote Play, skrýt stav připojení a Spárovat zařízení. Na konci: přepnout zpět
  na 🟢 herní účet. Konzole se zapíše do **vlastního profilu chiaki-ng** `recaption`
  (`HKCU\Software\Chiaki\Chiaki-recaption`, `Chiaki.PROFIL_CTECKY`) – spárování herním účtem pro hraní na
  PC zůstává a přepínání použití nic nemaže. Kterým účtem se párovalo, je v `parovaniUcet.<pc|ctecka>.<konzole>`;
  když je to herní účet, Domů ukáže „PS5 · spárováno herním účtem“ s tlačítkem Spárovat znovu.
- **Spustit** (`src/CteckaTv.kt`): čtení (`Ctecka`, výstup jen do TV, pruh na PC ne) běží hned – TV se
  připojí a dostává stav – a čeká na obraz (okno `pid:0`). PS5 se **nebudí** (probuzení by ji zapnulo
  na účet 🔵): každé ~4 s se jen zjistí, jestli je zapnutá („Čeká na zapnutí PS5“), a po zapnutí
  ovladačem se spustí `chiaki --profile recaption --exit-app-on-stream-exit stream <konzole> <adresa>`
  v okně (bez `--fullscreen`). Okno chiaki-ng dostane čtení za běhu (`cteni.py` příkaz `OKNO`).
  Když PS5 během hry usne, chiaki-ng skončí a znovu se nepřipojuje, dokud PS5 není zase zapnutá
  (druhé „zapnutá“ po 15 s + 3 s). Po 4 spojeních kratších než minuta za sebou se pokusy zastaví.
- **Zvuk z PC je vypnutý**: v profilu `recaption` je `settings/audio_volume = 0` (chiaki-ng s nulovou
  hlasitostí zvuk vůbec nepouští, `StreamSession::PushAudioFrame`). Profil se při prvním spuštění
  založí s hráčovým nastavením obrazu z výchozího profilu (vykreslování, dekodér, rozlišení), bez okna,
  zvuku a přihlášení PSN, okno „Selected Resolution“. Hráčův výchozí profil chiaki-ng se nemění.
  chiaki-ng s `--profile` si přepíše „naposledy použitý profil“ (`settings/current_profile`) – ReCaption
  ho po otevření okna a po konci vrátí. Profil `recaption` zůstane vidět v seznamu profilů chiaki-ng.
- **Okno chiaki-ng** se otevře maximalizované (chiaki-ng bez `--fullscreen` a bez uložené polohy) a čte
  se i za jinými okny; ReCaption se po ~12 s vrátí dopředu. Minimalizované se číst nedá: Domů ukáže
  „chiaki-ng · minimalizované“ s tlačítkem **Obnovit okno** (bez převzetí zaměření). Zmenšit okno lze
  ručně; pro čtení stačí obsah aspoň kolem 1280×720.
- **Domů** ve čtečce: Použití · Čtečka pro TV, chiaki-ng (chybí / bez zvuku · připojeno / minimalizované),
  PS5 (nespárováno / zapnutá / v klidovém režimu / nenalezena / připojeno), překlad, hra a
  **TV** podle ohlášení aplikace v TV („LG OLED55B7 · připojeno / nepřipojeno“; jméno se pamatuje
  v `tvNazev`). Stav vlevo nahoře v TV má první řádek PS5 („PS5-151 · v klidovém režimu“).
- Výstup je ve čtečce vždy TV LG / Samsung (uložená volba výstupu pro hraní na PC se nemění);
  řádky „Hra běží v“ a „Okno hry“ jsou schované.
- **Nezhasínat PC při překladu** (⚙ → Použití, klíč `nezhasinat`, výchozí vypnuto, popis v dialogu při zapnutí; obě použití,
  `src/Nezhasinat.kt`): dokud běží čtení / chiaki-ng / čtečka, počítač neusne a displej nezhasne ani se nezamkne (jinak se
  čtení okna chiaki-ng zastaví nebo zpomalí, na Macu App Nap). Windows `SetThreadExecutionState(ES_CONTINUOUS | ES_SYSTEM_REQUIRED
  | ES_DISPLAY_REQUIRED)` přes JNA z vlákna okna (stav patří vláknu), uvolní `ES_CONTINUOUS`; Mac `/usr/bin/caffeinate -dimsu -w <pid>`
  (skončí s ReCaptionem i po pádu). Hlídá se každou sekundu (`hlidejSpanek`), uvolní se po Zastavit, vypnutí volby a zavření. Záznam
  `SCREEN|PC neusne…` / `SCREEN|PC smí zase usnout`. Na Macu s PS5 neověřeno (zavřené víko Mac uspí i tak).
- Přihlašovací kód účtu 🔵 na PS5 (4 číslice) ReCaption nezadává – když ho PS5 chce, zeptá se okno chiaki-ng.

Ověřeno jen překladem kódu; spojení, zvuk, okno a čtení na skutečném PC s PS5 a TV ověří majitel.

## Mac

Stejné okno, čtení i překlad; jiné je jen to, na co se ptá systém (`src/Mac.kt`, funkce pro Windows na Macu
hned na začátku odbočí – kód pro Windows se nemění). Výchozí použití na Macu je **Čtečka pro TV**.
Texty se slovem PC jdou přes `Os.text` (na Macu „Mac“ / „na Macu“, i na stránce pro telefon při přihlášení). Co se liší
pro hráče, je v tabulce `pomoc/pc.md#rozdily` – při novém rozdílu ji doplnit a `pc.md` / `mac.md` držet se stejnými oddíly.

- **Balíček** `ReCaption-Mac.dmg` (Apple Silicon) skládá a zkouší `.github/workflows/mac.yml` na Macu (`macos-26`) a souběžně Codemagic (`codemagic.yaml`, Mac mini M2 –
  od 23. 11. 2026, kdy GitHub Macy s Apple Silicon kvůli nedostatku kapacity nespouštěl; sestavení v obou drž stejné):
  `ReCaption.app` s vlastní Javou (jpackage), v `Contents/app` jary, `cteni.py`, `priprava.sh` a přenosný
  Python 3.12 s knihovnami (`python/bin/python3`). Podpis jen místní („ad hoc“) – první otevření se povoluje
  v Nastavení systému; povolení Nahrávání obrazovky se po každé nové verzi dává znovu. Z větve vydání
  `mac-zkusebni`, z `main` vydání `mac`; návod pro hráče `pomoc/mac.md`.
- **Okna** (`MacOkna`, `MacOknoHry`): seznam a poloha od CoreGraphics přes JNA, hledání podle „program – titulek“
  nebo `pid:`. Titulky cizích oken systém vydá jen s povolením Nahrávání obrazovky; Domů ukáže
  „Nahrávání obrazovky · chybí povolení“ s tlačítkem Povolit. Minimalizované okno Mac od okna na jiné ploše
  nerozliší – stav „minimalizované“ ani Obnovit okno na Macu nejsou.
- **Snímek okna** (`cteni.py`, `OknoMac`): Quartz (`CGWindowListCreateImage`), když obraz nevydá, ScreenCaptureKit
  (`RECAPTION_SNIMEK=sck` ho vynutí). Běžné okno se ořízne o titulek (28 bodů) – stejně jako `MacOknoHry.oblast`.
  `cteni.py --okna` vypíše okna a stav povolení.
- **Pruh přes hru** (`MacPruh`): myš skrz pruh, nad ostatními okny i nad hrou přes celou obrazovku – AppKit
  z hlavního vlákna. Na skutečné hře neověřeno.
- **chiaki-ng** (`ChiakiMac`): `chiaki-ng.app` z obrazu `.dmg` posledního vydání do složky nástrojů (nebo ze
  složky Aplikace), nastavení v předvolbách macOS `com.chiaki.Chiaki` (profil čtečky `com.chiaki.Chiaki-recaption`),
  čte a zapisuje se příkazem `defaults` (`MacPredvolby`).
- **Klíč DeepL** v Klíčence (`MacKlicenka`, položka „ReCaption“ / účet „deepl“).
- **Složka nástrojů** `~/Library/Application Support/ReCaptionTools` (nastavení, model, chiaki-ng, záznamy).
- **Zkoušky**: `--zkouska-mac` (okna, předvolby, Klíčenka, pruh), `--zkouska-okno` (okno s titulkem pro zkoušku
  snímku), `--stahni-chiaki`; z hotové aplikace přes `JAVA_TOOL_OPTIONS=-Drecaption.prikaz=--zkouska-mac`.
- Běh z repozitáře na Macu: `./gradlew -p pc installDist`, `bash pc/priprava.sh cs "$HOME/Library/Application Support/ReCaptionTools"`
  (Python 3.10–3.12), pak `java -Drecaption.pc=pc -cp "pc/build/install/recaption-pc/lib/*" cz.recaption.pc.AplikaceKt`.

Spojení s PS5, zvuk, čtení okna chiaki-ng za jinými okny a posílání do TV na skutečném Macu ověří majitel.

## Z čeho se skládá

- `src/Aplikace.kt` – okno aplikace (barvy jako `Theme` v aplikaci pro Android, Swing). Ovládá se
  myší, dotykem i klávesnicí (Tab / šipky, Enter); vybraný prvek má zlatý rámeček.
- `src/CteckaTv.kt` – čtečka pro TV: hlídání PS5 bez buzení, chiaki-ng v okně bez zvuku, předání okna čtení.
- `src/Ctecka.kt` – čtení a překlad. Výběr titulků, rozhodování, kdy větu ukázat a schovat,
  i posílání do televize jsou **tytéž soubory** jako v aplikaci (`app/src/main/java/cz/recaption/core`),
  jen přeložené pro počítač. Oprava v `core` se tak projeví na Androidu i tady.
- `src/Pruh.kt` – pruh s překladem na obrazovce: okno vždy navrchu, průhledné, myš a dotyky jím
  propadají do hry a nebere si zaměření. Leží přes anglický titulek (jako na Android TV), velikost
  písma podle něj a jede s oknem hry, když se posune nebo změní velikost. Polohu okna zjišťuje od
  Windows přes knihovnu JNA a přepočítá ji podle zvětšení obrazovky ve Windows (handheld 150–200 %).
- `src/Nastaveni.kt` – uložené nastavení a nabídky hodnot (pozadí a barvy jako na Android TV).
- `src/Main.kt` – totéž z příkazové řádky (`spustit.ps1`), výpis do konzole.
- `src/Rozbor.kt` + `rozbor.cmd` – rozbor uložených snímků (viz Rozbor uložených snímků).
- `cteni.py` – pomocník pro to, co Android dělá svými knihovnami: snímek okna s hrou, čtení
  textu (RapidOCR) a překlad (model Argos, jádro CTranslate2). Jeho chyby jdou do
  `%LOCALAPPDATA%\ReCaptionTools\cteni-chyby.txt`.
- `src/Mac.kt`, `src/MacZkouska.kt`, `priprava.sh`, `ikona_mac.py` + `ReCaption.icns` – Mac (viz oddíl Mac).
- `ReCaption.cmd` + `aplikace.ps1` – spuštění okna; `priprava.ps1` – příprava (z okna s `-BezSestaveni`).

## Záznam hry pro ladění

⚙ Nastavení → Ladění → **Záznam hry do složky · Ano** (z příkazové řádky `--zaznam`). Každé spuštění
čtení pak dostane vlastní složku `zaznamy\pc-<čas>\` v repozitáři (v `.gitignore`), řádek „Složka
záznamů“ ji otevře:

- `zaznam.txt` – vše ze Záznamu v okně a k tomu řádky `CTENI`: co čtečka přečetla ve spodní třetině,
  kde to leželo v celém obraze (`[vlevo,nahoře-vpravo,dole]`) a co z toho vzala jako titulek
  (stejný přečtený text za sebou jen jednou). Čas na milisekundy.
- `snimek-HHMMSS-mmm.jpg` – celý obraz hry barevně v plném rozlišení, nejvýš jednou za 2 s a jen
  když se obraz změnil (`cteni.py`, ukládá v pozadí, čtení titulků nečeká). Text z celé obrazovky se
  za hry nečte – ze snímků se přečte až při rozboru (tentýž RapidOCR z `%LOCALAPPDATA%\ReCaptionTools\venv`).

Podle toho se ladí profily her (např. repliky nad postavami v Diablu IV) – Claude čte složku přímo,
bez předávání ze zařízení.

## Rozbor uložených snímků

`pc\rozbor.cmd <složka> [-Obrazky] [-Hra gta6] [-ZonaUkolu 0.915]` (`src/Rozbor.kt`, `cteni.py --rozbor`) projde
snímky `snimek-*.jpg` ve složce a jejích podsložkách – dávky ze Záznamu snímků na Androidu (`ci/stahni-davky.ps1`,
`zaznamy\davky-…\davka-…` s `index.txt`), starší `zaznamy\hra-…` (`zaznam.txt`) i `pc-…`. Každý snímek se celý
přečte (týž RapidOCR) a pustí se na něj tytéž kroky z `core` jako v aplikaci: řádky v oblasti čtení
(`ScreenLayout.DEFAULT_READ`) → `TextLogic.assemble` s úkolem a hranicí úkolu (0.915 = `OBJECTIVE_ZONE_TOP`)
a horních 60 % → `GameMenu.read`. Vedle snímků vznikne `rozbor.txt` (všechny řádky s polohou v %, co se vzalo
jako titulek, úkol – i bez hranice –, menu, a co ke snímku zapsala aplikace), `rozbor.csv` a na konci souhrn:
rozložení dolní hrany titulků a horní hrany úkolu, mezera mezi nimi, neshody s aplikací a návrh hranice úkolu.
S `-Obrazky` i `rozbor\<snímek>.jpg` s rámečky (titulek zeleně, úkol oranžově, menu modře, oblast čtení šedě).
RapidOCR čte jinak než ML Kit na Androidu (např. slévá slova bez mezer) – rozdíly jsou vidět v řádcích „aplikace“.

## Příkazová řádka (ladění)

1. Jednou: `powershell -File pc\priprava.ps1 [-Jazyk cs]`.
2. Ukázka: `powershell -File pc\spustit.ps1`; hra: `powershell -File pc\spustit.ps1 -Okno chiaki`.
   `-Vystup pc | tv | oboje` – kam s překladem (výchozí pruh na počítači). Přímo `recaption-pc.bat`
   přijme navíc `--hra gta6|gta|rdr2|diablo4|…`, `--jen-titulky` (bez pokynů a úkolu), `--menu` (překlad menu), `--port` jiný než 8787 pro zkoušku vedle
   běžící čtečky.
3. Jen s výstupem do TV: na televizi LG spustit ReCaption a vybrat vstup HDMI. Televize si počítač najde sama.

Pro snímky při ladění okno přijme `-Drecaption.stranka=Titulky` a `-Drecaption.autostart=1`;
`-Drecaption.parovani=1` otevře průvodce spárováním a s `-Drecaption.snimek=<složka>` uloží po 4 s
všechny jeho kroky jako `parovani-1…4.png` (jen okno průvodce, ne celá obrazovka) a zavře ho.
Bez `parovani` uloží `-Drecaption.snimek=<složka>` stránky okna (`okno-Domů.png`, `okno-Titulky.png`,
`okno-Nastavení.png`) a zkušební pruh (`pruh.png`, jen jeho obdélník) a aplikaci zavře.

Konzole pustí jen jedno takové spojení najednou: chiaki-ng na počítači a ReCaption na Androidu
se u jedné konzole nedají použít současně. Stejně tak smí na portu 8787 v počítači poslouchat jen
jedno z dvojice čtečka / `webos/posilac.js`.

Změny jen ve složce `pc` nemění verzi aplikace a commitují se s `[skip ci]`.
