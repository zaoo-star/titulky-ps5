# ReCaption – stručný návod

Aplikace pro Android TV, boxy, Chromecast a projektory. Sama se po domácí síti připojí
k PlayStationu 5, přečte anglické titulky hry, přeloží je (offline, zdarma) a ukáže je
v černém pruhu přes obraz. Hra jde do televize dál kabelem HDMI.

**Nejrychlejší start:** https://recaption.pages.dev/ – po výběru TV a toho, na čem se nastavuje (počítač,
Android, iPhone, jen ovladač), stránka ukáže jen potřebné kroky.

## 1. APK
Ke stažení `ReCaption.apk`:
https://recaption.pages.dev/d/latest/ReCaption.apk

## 2. Instalace
Nejjednodušší je aplikace **Downloader** (od AFTVnews, zdarma v Google Play v televizi) – nic se
neposílá z telefonu:
1. V televizi nainstalovat z Google Play **Downloader** a otevřít ho (přístup k souborům → Povolit).
2. Do řádku pro adresu napsat kód **8438934** a zvolit **Go** – ReCaption se stáhne. (Záloha: adresa
   `recaption.pages.dev/tv`.)
3. Povolit Downloaderu instalaci: na Google TV v okně, které se ukáže, **Nastavení** → Downloader →
   Povoleno; na starší Android TV Nastavení → Aplikace → Zabezpečení a omezení → Neznámé zdroje.
4. Zvolit **Instalovat** a pak **Otevřít**. Stažený soubor pak Downloader nabídne smazat.

Podrobný postup i pro různé značky televizí: [pomoc/android-tv.md](pomoc/android-tv.md#instalace).
Jinak jde APK nahrát i z USB flash disku správcem souborů, nebo z ReCaptionu v telefonu s Androidem (níž).

Když aplikace nejde nainstalovat nebo aktualizovat:
1. Objeví-li se okno Play Protect, zvolit „Další podrobnosti“ → „Přesto nainstalovat“.
2. Když tam ta možnost není: Google Play → ikona účtu → Play Protect → ozubené kolo → vypnout
   „Kontrolovat aplikace pomocí Play Protect“, nainstalovat a pak kontrolu zase zapnout.
3. Hlásí-li televize „instalace z neznámých zdrojů“: Nastavení → Aplikace → Zabezpečení a omezení →
   Neznámé zdroje → povolit aplikaci, ze které se instaluje (Downloader nebo správce souborů).
4. Skončí-li instalace chybou: uvolnit místo v televizi, případně starou verzi odinstalovat a nainstalovat znovu.

Stejný postup je i v aplikaci na stránce Instalace.

**Bez Downloaderu, z telefonu s Androidem (zkušební):** nainstalovat ReCaption do telefonu, v televizi
zapnout Pro vývojáře (Google TV: Nastavení → Systém → Informace → 7× Sestavení operačního systému Android TV;
starší Android TV: Nastavení → Předvolby zařízení → Informace → 7× Sestavení) a v nich Ladění USB /
Ladění přes síť (Fire TV: Ladění ADB). V telefonu v ReCaptionu (Přihlášení pro TV) zvolit „Nahrát ReCaption do Android TV“ –
televizi najde sám, nahraje do ní ReCaption a spustí ho. Televize se jednou zeptá „Povolit ladění?“ –
zaškrtnout Vždy povolit a potvrdit. Potom jde ladění v televizi zase vypnout. Bezdrátové ladění
s párovacím kódem (Android 11+) zatím nejde.

**Další verze už se ručně nenahrávají.** Když vyjde nová verze, aplikace ji nabídne na stránce Domů.
Stačí zvolit „Aktualizovat“ a instalaci potvrdit na ovladači – spárování i nastavení zůstanou.
Aplikace se po nové verzi dívá při spuštění a každých 10 minut, dokud je otevřená; když běží jen překlad, každých
30 minut, a když neběží vůbec, zhruba jednou za 3 hodiny – novou verzi pak ohlásí upozorněním „Nová verze ReCaption“.
Hned se dá zjistit v ⚙ Nastavení → Obecné → **Nová verze** (dole).

**Sbírání replik ze hry** (⚙ Nastavení → Čtení a překlad, výchozí zapnuto): pomoc s předem přeloženou hrou. Posílají se
anglické repliky z titulků, které ReCaption ukázal (text, mluvčí, hra, kolikrát se objevila, pořadí v rozhovoru) – bez snímků,
překladů, účtů a údajů o zařízení; popis je i v Instalaci → Volby v Nastavení. Jen se zvoleným profilem hry. Odesílá se samo
občas na pozadí, ručně přes řádek → Odeslat teď. Vypnout a smazat sbírání vypne a smaže vše nasbírané z telefonu / boxu.
Kdo ho dřív vypnul nebo na dotaz odpověděl „Nesbírat“, má ho dál vypnuté.

**Podpořit ReCaption ☕** (úplně dole na Domů): dobrovolný příspěvek na vývoj. V telefonu se otevře stránka
v prohlížeči, na televizi se ukáže čtvereček k naskenování telefonem. V aplikaci v TV LG / Samsung je čtvereček
na úvodní obrazovce a vedle nabídky OK. Za příspěvek se nic neodemyká – aplikace funguje stejně i bez něj.

## Víc TV nebo víc PlayStationů
- **Dvě Android TV:** ReCaption se nahraje do každé zvlášť a každá má vlastní nastavení i vlastní čtvereček
  „Nastavit z telefonu“. Aplikace ReCaption v telefonu (režim „Nastavuji TV“) se připojí k té TV, jejíž čtvereček
  se naskenuje.
- **Dvě TV LG / Samsung s jedním telefonem:** telefon si pamatuje vzhled titulků a oblasti pro každou TV zvlášť
  a použije ty pro TV, ve které je ReCaption právě spuštěný.
- **Dva PlayStationy:** na stránce Konzole **Spárovat další PS5** (dosavadní zůstane spárovaná) a řádkem **Aktivní PS5**
  se mezi nimi přepíná (při běžícím překladu se ReCaption spojí znovu). Když je v síti víc PS5, **Najít** nic nevybírá
  samo – ukáže je k výběru (už spárované s poznámkou „spárováno“). **Zrušit spárování** zruší jen tu aktivní.

## 3. První nastavení (provede průvodce)

Průvodce má nejvýš 9 kroků, na každé obrazovce jedna věc. Co je podle zařízení jasné, přeskočí: na Android TV
Zařízení i Televizi, v telefonu Zařízení (u LG / Samsungu i Zobrazení titulků). „Krok X z N“ nahoře i seznam vlevo
počítají jen kroky, které se ukážou. Na PlayStationu se zatím nic nenastavuje – co je potřeba,
ukáže až krok Připojení konzole (bod 4).

1. **Jazyky:** všechny tři jazyky na jednom místě – jazyk aplikace (texty se hned přepnou), **Překládat z**
   (v jakém jazyce jsou titulky ve hře; výchozí angličtina, jen jazyky v latince – čtení obrazu jiné písmo neumí)
   a **Jazyk překladu** (do čeho se překládá). Vše jde později změnit (jazyk aplikace v ⚙ Nastavení → Obecné,
   ostatní v Titulkách).
   Pod tím (Android TV s PlayStationem) **Nastavit telefonem nebo počítačem (doporučeno)**, nebo **Nastavit přímo
   na TV**. Telefonem či počítačem ukáže TV jeden čtvereček a pod ním adresu pro počítač – v telefonu (i iPhonu)
   nebo v prohlížeči počítače ve stejné síti se otevře stránka **ReCaption · nastavení TV**
   se vším, co se jinak píše ovladačem:
   - **1 · 🔵 Účet pro ReCaption** – Android: tlačítko **Otevřít v aplikaci ReCaption** (nic se nekopíruje);
     iPhone: přihlášení v prohlížeči s obrázkem, kde adresu „redirect“ zkopírovat, nebo zkratka **ReCaption**
     v Sdílet ([návod](https://recaption.pages.dev/zkratka.html)).
   - **2 · PS5 pod 🔵 účtem** – na PS5 se přepnout na 🔵 účet, zapnout Remote Play a **Spárovat zařízení** (starší systém: Propojit zařízení); osmimístné
     číslo se opíše do telefonu (ne do TV) a TV se spáruje sama.
   - **3 · DeepL** (nepovinné) – klíč pro lepší překlad.
   TV mezitím ukazuje seznam, který se sám odškrtává (✔ účet · ✔ PS5 spárovaná · ✔ DeepL). Na konci se na PS5
   přepnout zpět na 🟢 herní účet a v TV zvolit **Pokračovat na TV** – průvodce pokračuje jazykem překladu a hotové
   kroky se už jen potvrdí. Totéž kdykoli později – i u už nastavené TV – v ⚙ Nastavení → Obecné nebo na stránce
   Konzole → **Nastavit z telefonu**: stránka ukáže, co v TV už je, a u hotového nabídne **Změnit** / **Spárovat
   znovu** (jiný účet, nové spárování, jiný klíč DeepL).
   - **Android:** nahoře volba **Jak pokračovat?** – **V aplikaci ReCaption** (bez kopírování, číslo z PS5 kamerou,
     celé nastavení TV včetně vzhledu titulků; bez aplikace ji tlačítko stáhne), nebo **Pokračovat v prohlížeči**
     (nic se neinstaluje). iPhone a počítač vidí rovnou prohlížeč.
   - **Číslo z PS5 kamerou:** Android – **📷 Načíst číslo kamerou** (čtečka v ReCaptionu v telefonu, číslo jde rovnou
     do TV); iPhone – podržet prst v políčku → **Skenovat text** a namířit na číslo.
   - **Kde se co dělá** (rozbalovací řádek na stránce i v TV): 📱 telefon jen přihlášení 🔵 účtem · 🎮 PS5 přepnout
     na 🔵 účet, Remote Play, Spárovat zařízení → číslo, pak zpět na 🟢 herní účet · 📺 TV nic, jen čtvereček.
   - **Webová obdoba aplikace (iPhone, počítač):** na stejné stránce je nahoře **Stav TV** (stejné body jako na Domů)
     a dole **4 · Titulky a hra** – jazyk překladu, Překládat z, hra, velikost písma, pozadí, poloha pruhu, originál,
     pokyny / úkol / překlad menu (u her, které je znají) a DeepL zapnout / vypnout. Změna se v TV projeví hned.
     Méně častá nastavení zůstávají v TV nebo v aplikaci ReCaption v telefonu.
2. **Zařízení:** na čem ReCaption běží – Android TV nebo box s Androidem, nebo telefon či tablet
   (ten jen čte a hotové titulky posílá do televize LG nebo Samsung, viz bod 8). Na Android TV a v telefonu
   se krok přeskočí (uloží se totéž, co by se zvolilo); ukáže se jen tam, kde to jasné není, nebo když bylo
   dřív zvoleno jinak. Změnit jde v ⚙ Nastavení → Obecné → Zařízení.
3. **Konzole:** PlayStation 5 (přes Remote Play, s druhým bezplatným účtem), nebo Xbox (účet Microsoft, druhý účet
   není potřeba – viz bod 7b). U PlayStationu je pod volbami odkaz na přípravu 🔵 účtu (na TV čtvereček,
   v telefonu tlačítko).
4. **Televize:** Android TV (pruh ukazuje toto zařízení), LG, nebo Samsung – na Android TV se krok přeskočí
   (je to Android TV; LG / Samsung jde zvolit v ⚙ Nastavení → Obecné). U LG a Samsungu tu je tlačítko
   pro nahrání aplikace do televize – jde přeskočit a aplikaci nahrát později (Domů, Instalace).
   V telefonu průvodce sám hledá televizi v domácí síti: najde-li jen LG, nebo jen Samsung, rovnou ji
   předvybere a ukáže „✔ V síti · …“ (volbu jde změnit).
5. **Offline překlad:** počkat na stažení jazykového balíčku pro zvolenou dvojici jazyků (jednou, asi 30 MB).
6. **Lepší překlad (DeepL):** jedna věta proč a jeden čtvereček pro telefon – stránka v telefonu provede
   registrací na DeepL, zkopírováním klíče i jeho vložením – viz bod 9. Bez klíče zvolit **Přeskočit – zůstat
   u offline překladu**; DeepL jde nastavit kdykoli později v Titulkách → Kvalita překladu. Po přijetí klíče
   se ukáže „✔ DeepL je nastavený“ a Pokračovat. Při **Napsat klíč ručně** ukáže TV malý čtvereček na stránku
   DeepL API, ať se adresa nepíše ovladačem.
7. **Zobrazení titulků** (jen Android TV): **Otevřít nastavení TV** a zapnout ReCaption v Usnadnění (Google TV:
   Nastavení → Systém → Usnadnění; starší Android TV: Nastavení → Předvolby zařízení → Usnadnění) → ReCaption → Zapnout. Po návratu do ReCaptionu s povolením průvodce sám pokračuje. U LG a Samsungu jen
   krátké vysvětlení.
8. **Připojení konzole:** spárování PlayStationu v šesti malých krocích – viz bod 4 (Xbox: bod 7b). Má
   jen tlačítka spárování a pod nimi malé **Spárovat později** (průvodce skočí na Hotovo). Co jde, udělá
   sám: zapnutou PS5 najde v síti hned na prvním kroku a přeskočí hledání; po napsání (nebo přečtení
   kamerou) osmimístného čísla z PS5 rovnou spáruje a po spárování přejde na Hotovo.
9. **Hotovo:** jak se hraje, řádek **Víc TV nebo konzolí** (další PS5, PS5 i Xbox, dvě TV – v okně, totéž
   v Instalaci, kapitola 9); se spárovanou konzolí tlačítko Spustit.

U více televizí LG nebo Samsung si ReCaption pamatuje vzhled titulků a oblasti pro každou zvlášť
a přepne je sám podle televize, ke které se připojí (v Titulkách je nahoře vidět, pro kterou).

Každou volbu jde později změnit v **Nastavení** – ozubené kolo (⚙), které je vidět pořád (v telefonu vpravo nahoře, na televizi dole v levém sloupci). Nastavení má záložky Obecné, Čtení a překlad a Experimentální; řádky jsou rovnou pod sebou, bez rozbalování. Co která volba dělá, je v Instalaci v kapitole „Volby v Nastavení“.
V Obecné je i povolení, jazyk aplikace a dole Nová verze, Poslat vývojáři a hlášení chyby; ve Čtení a překlad sbírání replik, statistika a vlastní slovník; v Experimentální jen pokusy a ladění (Jen nové repliky, Diagnostický režim, záznam v domácí síti). Snímky ze hry (Záznam snímků, Snímky na Domů) jsou tam pro všechny, výchozí vypnuto; zkušební verze vidí jen testeři. Hra (profil hry) a DeepL
(Kvalita překladu) jsou v Titulkách. V menu jsou dál stránky Domů, Konzole (párování, nadpis podle konzole:
PlayStation nebo Xbox), Titulky a Instalace.

Stránka **Domů** ukazuje jen to podstatné: stav překladu, tlačítka jako u přehrávače ▶ (spustit, zeleně) ❚❚ (pozastavit, žlutě) ■ (zastavit, červeně) –
co teď nejde, je šedé – a pod stavem přehled
v bodech – zelená tečka jede, oranžová potřebuje pozornost (a má rovnou tlačítko), šedá je jen informace:
konzole a jestli přichází obraz, televize (u Android TV povolení pruhu), síť tohoto zařízení, rychlost čtení,
jazyk a způsob překladu a verze aplikace (i v televizi). OK na řádku konzole nebo
televize je změní. Náhled obrazu z konzole je na stránce Konzole.

**Instalace** (dřív Nápověda) má nahoře tlačítko **Spustit průvodce nastavením znovu** a pod ním postup od začátku v kapitolách (Instalace, Příprava konzole, Televize, Jazyk a kvalita
překladu, Spárování, Hraní, Když něco nejde, Volby v Nastavení, Víc TV nebo konzolí). Kapitola se rozbalí po OK a ukazuje
jen to, co platí pro zvolenou televizi a konzoli; ✔ znamená hotovo. Nahoře jde přepnout „Instalace pro“
jinou televizi. Kapitoly Příprava konzole, Televize (LG/Samsung), Jazyk a kvalita překladu a Spárování mají řádek
**▶ Video k tomuto kroku** – krátký klip podle zvolené TV a konzole (česky nebo anglicky, potřebuje internet; stejný jako
v průvodci). OK video spustí, OK ho pozastaví, ◀ ▶ přetáčí o 10 s, Zpět ho zavře. Samostatná záložka Videonávody od
1.00-rc20 není.

## 4. Spárování PlayStationu (jen jednou)

**Důležité: aplikace se připojuje DRUHÝM účtem PlayStation, ne tím, na kterém se hraje.**
Kdyby se připojila herním účtem, konzole by při hraní vypínala ovladač a zhasínala obraz
(bere to tak, že se hra přesunula jinam). Po spárování se na ovladači přepne zpět na herní účet
a hraje se jako vždy, se stejnými hrami. Druhý účet se jen „dívá“.

Aby se účty nepletly, značí je aplikace (i Nápověda, stránka v telefonu a tento návod) vždy stejně:

- 🔵 **Účet pro ReCaption** – druhý účet, jen pro ReCaption: přihlašuje se jím v telefonu, na PS5 se na něj
  přepne, pod ním se zapne Remote Play a vyvolá osmimístné číslo. U jména účtu je vždy 🔵 (např. „🔵 JMÉNO“).
- 🟢 **Herní účet** – ten, na kterém se hraje. Na něj se na PS5 po spárování přepne zpátky.

**PS5 spáruje jen tehdy, když osmimístné číslo vyvolá na PS5 stejný uživatel, jakým se ReCaption
přihlásil.** Proto aplikace účet nikdy nebere potichu a jeho jméno (Online ID) ukazuje až do konce.

**Kde se píše e-mail a heslo:** vždy jen na stránce Sony v telefonu nebo v prohlížeči počítače – nikdy do televize
a nikdy do ReCaptionu. Podle toho, na čem ReCaption běží:

| ReCaption běží na | Účet | Kde se přihlašuje |
|---|---|---|
| Android TV / box s ovladačem | 🔵 | telefon (Android i iPhone) nebo prohlížeč PC přes čtvereček z TV |
| telefon s Androidem (pomocník, čtečka pro TV LG / Samsung) | 🔵 | přímo v ReCaptionu v telefonu (stránka Sony, vždy načisto) |
| iPhone (jen stránka z TV) | 🔵 | Safari: stránka Sony, pak zkopírovat adresu „redirect“ |
| PC – Čtečka pro TV | 🔵 | telefon přes čtvereček z okna PC, nebo prohlížeč PC |
| PC – Hraji na PC (chiaki-ng) | 🟢 | telefon nebo prohlížeč PC; druhý účet tu není potřeba ([pomoc/pc.md](pomoc/pc.md#ucet)) |

**🔵 uživatel musí být na PS5.** Účet založený na webu je potřeba na PS5 ještě přidat: ikona profilu → Přepnout
uživatele → **Přidat uživatele** a přihlásit se 🔵 účtem (ne jako host). Bez toho se na něj v kroku 5 nedá přepnout.

**Prohlížeč přihlášený herním účtem:** když Sony na stránce v telefonu nebo PC heslo nechce a hned ukáže „redirect“,
je v prohlížeči přihlášený jiný účet (často 🟢 herní) a ReCaption pak ukáže „Ověřený účet“ s jeho jménem. Zvolit
**Přihlásit jiný účet** a stránku ReCaption otevřít v anonymním okně. ReCaption v telefonu s Androidem začíná vždy načisto.

Aplikace provede šesti kroky, na každé obrazovce je jedna věc. V průvodci je nahoře „Krok 8 z 9“
a pod ním malý podtitul „Spárování · 3 z 6“, na stránce Konzole pruh s dílky.

1. **Zapnout PlayStation.** Stejná domácí síť jako zařízení s ReCaptionem (nejlépe obojí kabelem).
   Na PS5 se zatím nic nenastavuje.
2. **Najít PlayStation.** Aplikace ho v síti najde sama. Když ne, zadat adresu ručně
   (PS5 → Nastavení → Síť → Stav připojení → Adresa IPv4).
3. **🔵 Účet pro ReCaption.** Nahoře je legenda obou značek (🔵 / 🟢). Druhý účet PlayStation, na kterém se nehraje. Ještě není? Založí se zdarma –
   návod je pod tlačítky (čtvereček, v telefonu tlačítko; stránka [pomoc/ps5.md](pomoc/ps5.md)).
   - Když už je účet uložený (i po „Zrušit spárování“), ukáže se **Použít účet 🔵 JMÉNO** a **Přihlásit jiný účet**.
   - Když jsou účty uložené v ReCaptionu v telefonu, nabídnou se se jmény.
4. **Přihlásit se 🔵 účtem pro ReCaption** (telefon), ne 🟢 herním. V telefonu otevřít fotoaparát, namířit ho na čtvereček v televizi
   a klepnout na odkaz – otevře se stránka ReCaption z této televize (telefon musí být na stejné Wi-Fi).
   **Android:** nahoře je žluté **Otevřít v aplikaci ReCaption** – když ReCaption v telefonu není, tlačítko
   ho stáhne (po instalaci se vrátit na stránku a klepnout znovu); přihlášení proběhne v aplikaci a nic se nekopíruje.
   **iPhone** (nebo „Přihlášení v prohlížeči“ níž na stránce): klepnout na „Přihlásit se u PlayStation“, zadat e-mail a heslo **🔵 druhého** účtu (na „passkey“ zvolit
   „Remind Me Later“). Zůstane skoro prázdná stránka „redirect“ – zkopírovat její adresu a vložit ji na
   stránku ReCaption. Televize pak pokračuje sama.
   **Bez telefonu:** pod čtverečkem je adresa stránky (např. `http://192.168.1.20:8765/p/k7mq2xra`) –
   stačí ji otevřít v prohlížeči počítače ve stejné síti.
   **Záloha – ruční opsání kódu:** když se stránka v telefonu neotevře, zvolit „Stránka se neotevřela“.
   Krok má vlastní čtvereček rovnou na přihlášení Sony; na stránce „redirect“ najít v adrese „code=“
   a opsat do televize to, co je za ním, až po znak & (i s tečkou a velkými písmeny). Kód platí pár minut.
   Výsledek: **„✔ Ověřený účet: 🔵 JMÉNO“** (s malým tlačítkem Přihlásit jiný účet).
5. **Na PS5 přepnout na uživatele 🔵 JMÉNO** (tlačítko PS → ikona profilu → Přepnout uživatele) a v
   **Nastavení → Systém → Aplikace Remote Play** zapnout **Povolit aplikaci Remote Play** a **Skrýt stav připojení aplikace Remote Play**
   (anglicky Hide Remote Play Connection Status – jinak ve hře svítí „Aplikace Remote Play je připojena“;
   nastavit ho i pod 🟢 herním uživatelem). Když tam volba není, aktualizovat systém PS5.
6. **Kód z PS5.** Pod uživatelem 🔵 JMÉNO zvolit Nastavení → Systém → Aplikace Remote Play → **Spárovat zařízení** (ve starším systému Propojit zařízení).
   Osmimístné číslo opsat do ReCaptionu (v telefonu ho přečte kamerou) a zvolit Spárovat. Po úspěchu:
   „Spárováno (účet 🔵 JMÉNO). Na ovladači se přepnout zpět na 🟢 herní účet.“ Totéž připomene poslední
   krok průvodce (Hotovo): „Na PS5 se přepnout zpět na 🟢 herní účet“.

**Chyba CE‑110032‑7 na PS5 / „Spárování se nepovedlo“ v aplikaci:** číslo nejspíš vyvolal jiný
uživatel než 🔵 JMÉNO. Aplikace to napíše a nabídne **Zkusit znovu** a **Přihlásit jiný účet**: buď se na PS5
přepnout na 🔵 JMÉNO a vyvolat nové číslo, nebo se přihlásit tím účtem, pod kterým se číslo vyvolává (musí to být
🔵 druhý účet, ne 🟢 herní).

**Probouzení z režimu odpočinku** je volba v ⚙ Nastavení → Obecné („Probouzet PlayStation“, výchozí
vypnutá). Na PS5 k tomu: Nastavení → Systém → Úspora energie → Funkce dostupné v režimu odpočinku →
„Zůstaňte připojeni k internetu“ a „Povolit zapnutí systému PS5 ze sítě“.

**ReCaption i v telefonu (jen Android, nepovinné).** Stejnou aplikaci jde nainstalovat i do
telefonu – tam nepřekládá, jen pomáhá s přihlášením. Na stránce z čtverečku zvolit „Otevřít
v aplikaci ReCaption“, přihlásit se druhým účtem přímo v aplikaci a ta pošle účet do televize sama,
bez kopírování. Účet si zapamatuje (jen číslo a jméno, heslo ne), takže při příštím párování nebo
na další televizi stačí naskenovat čtvereček a klepnout na „Odeslat do televize“. Přihlášení u Sony
v pomocníkovi začíná vždy načisto (e-mail a heslo se píší pokaždé), ať se omylem nepoužije herní účet.
Když telefon sám čte pro televizi LG / Samsung, uložené účty nabídne krok 3 „Účet pro ReCaption“ se jmény.

## 5. Hraní
1. Na stránce Domů zvolit ▶ (Spustit překlad) a počkat na „Překlad běží“ (objeví se i malý náhled
   toho, co aplikace z PlayStationu vidí).
2. Přepnout televizi na HDMI s PlayStationem a hrát. Ve hře mít zapnuté anglické titulky.
3. Titulky si aplikace na obrazovce najde sama.
4. Každá postava má v pruhu jméno ve své barvě. Když hra nabídne volby odpovědi pod sebou
   (očíslované nebo zarovnané vlevo), ukáže je pruh jako seznam s odrážkami, každou přeloženou
   zvlášť. Menu hry a pokyny k ovládání se dál nepřekládají.

**Co se u hry překládá navíc (Titulky → Hra):** titulky vždy. U her, jejichž profil to zná (zatím GTA), má každý další
text vlastní přepínač: **Pokyny pod titulky** (krátký šedý řádek „Take your shot“ pod replikou), **Úkol pod titulky**
(řádek úkolu jako samostatný pruh, s volbami písma, polohy a pozadí – výchozí písmo 12, těsně pod úkolem, pozadí 40 %) a u GTA VI
**Překlad menu hry** (u GTA VI výchozí zapnuto, jinak vypnuto).
Žádná jiná volba je nepřebíjí. Kdo měl do verze 0.97.57 „Rozsah čtení: jen titulky“, má je po aktualizaci vypnuté.

**GTA VI: pozadí titulků musí být zapnuté.** Bez něj se text ztrácí v obraze a ReCaption nic nepřečte. Ve hře:
Settings → UI → Subtitles & Captions → Background – cokoli kromě Off. Připomíná to i řádek „Ve hře: pozadí titulků“
v Titulky → Hra a Nápověda → Hraní.

**Menu hry (zatím GTA VI, základ):** po otevření menu zleva (Mapa, Hra, Obchod, Nápověda, Fotostudio, Nastavení) nebo
Nastavení hry, ukáže pruh přeložené položky – vybraná je se šipkou ▸ – a u Nastavení i přeložený nadpis a popis vybrané volby.
Jednotlivé volby Nastavení se zatím nepřekládají. Funguje s profilem hry GTA VI a se zapnutým Titulky → Hra → Překlad menu hry.

**Menu vpravo dole (zatím GTA VI, výchozí vypnuto – zatím nefunguje dost dobře):** nápovědy k tlačítkům vpravo dole při jízdě a u postav (Vypnout
autopilota, Rychlost 1/2/3, Kamera 1/2/3, Mluvit, Držet se za ruce…). Titulky → Hra → „Menu vpravo dole“: **nad položkami**
(překlad nad anglickým seznamem, originál zůstane vidět) nebo **přes položky** (každý řádek přes svůj anglický). Ikony tlačítek
zůstanou vidět, šedé (teď nedostupné) položky jsou šedé i v překladu, čísla 1/2/3 se nemění. Běží souběžně s titulky i úkolem,
v televizi LG/Samsung od aplikace v TV 1.5.19. Vodorovná lišta v menu hry (Photo Studio · Select · Back) se nepřekládá.

**Tipy hry (zatím GTA VI, výchozí zapnuto):** Titulky → Hra → „Tipy hry“ přeloží nápovědu hry v tmavém rámečku vlevo nahoře
(„Tip: …“, „Press … to …“) do okna hned pod ním, a když se tam nevejde, vedle něj. Při otevřeném menu hry se schová.

**Seznam her:** jména, postavy a pojmy jednotlivých her si aplikace stahuje z internetu (v aplikaci samotné nejsou) a dají se
aktualizovat bez nové verze aplikace. Poprvé se seznam stáhne sám hned po spuštění; do té doby se překládá jen obecně (bez jmen
postav). V Titulky → Hra je řádek „Seznam her · verze N“ – OK zjistí, jestli není novější, a stáhne ho. Novější si aplikace stahuje i sama
(nejvýš jednou za 6 hodin). Zvolená hra se tím nikdy nemění.

**Výchozí nastavení nové instalace (od 1.00, podle majitele):** překlad těsně nad anglickým titulkem, písmo 14, neprůhlednost
pruhu 60 %; úkol písmo 12, těsně pod úkolem, pozadí 40 %; u GTA VI zapnutý Překlad menu hry, Překlad telefonu a Tipy hry
(Pokyny u předmětů a Menu vpravo dole vypnuté); Rychlejší čtení zapnuto; Nechat překlad déle na obrazovce +1 s; Nejkratší
doba titulku 1 s. Kdo si volbu už jednou změnil, má dál svou hodnotu.

Texty písní (rádio v autě, hudba ve scéně), které hra označuje notou ♪, se nepřekládají.

## 7. Problémy
- **Stránka z čtverečku se v telefonu neotevře** – telefon nejspíš není na stejné Wi-Fi jako televize (nebo je na mobilních datech). Připojit ho k domácí Wi-Fi a naskenovat čtvereček znovu. Když to nejde, zvolit v televizi „Stránka se neotevřela“: záložní čtvereček vede rovnou na přihlášení Sony a kód se pak opíše ručně (krok 5).
- **Adresa nebo kód z telefonu neplatí** – platí jen pár minut; přihlásit se v telefonu znovu a vložit novou adresu.
- **ReCaption v telefonu nepustí přihlášení Sony** – zvolit v něm „Nejde to? Přihlásit se v prohlížeči“ a prázdnou stránku „redirect“ pak nasdílet do ReCaptionu (Sdílet → ReCaption).
- **Nejde spárovat** – číslo z PS5 platí jen pár minut, je potřeba vyžádat nové. Zkontrolovat adresu a účet.
- **Spárováno, ale po Spustit se nic nepřipojí** – řádek „PS5 · spárováno“ znamená jen, že spárování proběhlo; spojení vzniká až po Spustit. Když ho PS5 odmítne, Domů napíše proč – nejčastěji chybí **Povolit aplikaci Remote Play** pod účtem pro ReCaption (PS5 pak hlásí jen obecnou chybu, Domů radí právě tohle) (nepřijatý účet – na PS5 se přepnout na ten účet, ať se přihlásí do PSN, nebo spárovat znovu; Remote Play spadl – PS5 úplně vypnout a zapnout; PS5 nepřijímá spojení – zkontrolovat Povolit aplikaci Remote Play a režim odpočinku). Řádky „zdroj: … video …“ dole na obrazovce ukazuje Diagnostický režim (⚙ Nastavení → Experimentální), vypnout jde tamtéž.
- **„PlayStation neodpovídá“** – je konzole zapnutá (ne vypnutá ze zásuvky) a ve stejné síti?
- **Ve hře svítí „Aplikace Remote Play je připojena“** – na PS5 zapnout Nastavení → Systém → Aplikace Remote Play → Skrýt stav připojení aplikace Remote Play (pod herním i druhým uživatelem), viz bod 3.
- **Účet na PS5 má přihlašovací kód** – PlayStation ho při spojení odmítne a na stránce PlayStation se objeví políčko pro kód (4 číslice); tam se zadá.
- **Pruh se neukáže** – zkontrolovat Usnadnění (bod 3.5, nebo ⚙ Nastavení → Obecné → Povolení) a v Titulcích nastavit vzhled – pruh se při tom ukáže na zkoušku.
- **Vzhled titulků během hry (Android TV)**: v ⚙ Nastavení → Obecné zvolit „Tlačítko vzhledu titulků“ a stisknout tlačítko ovladače, které se ve hře nepoužívá (barevné, Info…). Při hře pak otevře nabídku vzhledu přímo přes obraz (pozadí, velikost a barva písma, okraj pruhu, poloha, originál a jeho velikost, barevná jména); šipky mění hodnoty, Zpět zavře. (V TV LG/Samsung je totéž pod OK při hře – i bez spojení s telefonem a navíc s barvou písma, okrajem pruhu, velikostí originálu a barevnými jmény; v téže nabídce jde přepnout i vstup HDMI. TV si vstup pamatuje a příště se spustí rovnou do hry, úvodní obrazovka je jen poprvé a pod tlačítkem Zpět.)
- **Anglický titulek vyčuhuje nad pruh nebo po stranách** (hlavně dvouřádkový) – v Titulky → Pruh s překladem nechat polohu „Přes anglické titulky“ a zapnout „Zakrýt celou oblast titulků“: pruh pak kryje celé místo, kde se titulky naposledy ukazovaly, ne jen právě přečtený řádek (Android TV i TV LG/Samsung s aplikací v TV od 1.5.14).
- **Nic se nečte** – zapnout ⚙ Nastavení → Experimentální → Diagnostický režim; ukáže, co aplikace čte.
- **Víc replik pod sebou najednou (rozhovory lidí kolem v GTA VI) a pruh se pořád mění** – zkusit ⚙ Nastavení → Experimentální → **Jen nové repliky**: v pruhu se ukážou jen repliky, které v něm ještě nebyly. Stejná replika má v pruhu vždy stejný překlad, i když ji čtečka přečte pokaždé trochu jinak.
- **Na PS5 pořád naskakuje „ovladač odpojen / účet připojen“** – s druhým účtem se připojuje i jiné zařízení
  (třeba aplikace PS Remote Play) a PS5 pak spojení přehazuje. Domů to od 0.97 napíše. Druhý účet ať slouží jen ReCaptionu.
- **Telefon jako čtečka: spojení vypadává, když telefon zhasne displej** – v ⚙ Nastavení → Obecné zapnout
  „Povolit běh na pozadí“ (jinak ho šetření baterie uspí; poprvé se na to aplikace zeptá sama při Spustit).
- **Telefon jako čtečka: se zhasnutým displejem se překlad zpomalí nebo skoro zastaví** – Samsung pak čte titulky až 10 s místo
  zlomku sekundy (tester 22. 11. 2026). ⚙ → Obecné → „Nezhasínat displej telefonu“ (displej zůstane ztlumený; od 0.97.105 se na to
  aplikace zeptá při prvním Spustit). Bere víc baterie – telefon ideálně na nabíječce. Zhasnutí tlačítkem napájení to nezabrání.
  Od 0.97 drží ReCaption telefon při překladu vzhůru sám.
- **Spojení vypadává na Wi-Fi 2,4 GHz** – ve stejném pásmu vysílají ovladače konzole. Pomůže konzole na kabelu
  nebo zařízení s ReCaptionem na 5 GHz. Když telefon sám přechází na 2,4 GHz, je to router (stejný název sítě
  pro obě pásma a slabší 5 GHz signál); pomůže samostatný název sítě pro 5 GHz.
- **Překlad běží, ale titulky nenaskakují** – stačí se podívat na stránku Domů na řádek pod stavem překladu. Ukazuje, jak daleko to došlo: jestli se aplikace s PlayStationem spojila a jestli z něj opravdu přichází obraz. Když hlásí „Spojeno s PlayStationem, ale … nepřišel žádný obraz“, aplikace spojení po chvíli sama obnoví; když to trvá dlouho, překlad zastavit, zkontrolovat Wi-Fi a spustit ho znovu.
- **Aplikace minule spadla** – po dalším spuštění to napíše na stránce Domů. Kdyby se to opakovalo, pomůže hlášení (další bod).
- **Hlášení chyby**: Instalace → Když něco nejde (nebo ⚙ Nastavení → Obecné, dole) → „Vytvořit hlášení chyby“ otevře přes celou obrazovku hlášení o stavu aplikace: verzi, televizi, stav spojení, poslední pád a poslední události. Stačí ho vyfotit (šipkami ▲▼ se posouvá, v telefonu prstem) a poslat. Hesla, klíče ani číslo účtu v něm nejsou. Delší verze se zároveň uloží do souboru, jehož cesta je na konci hlášení. Tlačítko **Kopírovat** ho dá do schránky, **Sdílet** ho pošle jako soubor (e-mailem, do chatu…). Na televizi nejjednodušší: tlačítko **Do telefonu** ukáže čtvereček – naskenovat ho telefonem (i iPhonem) na stejné Wi-Fi, stáhnout hlášení a poslat ho autorovi chatem nebo e-mailem. S televizí LG nebo Samsung je v hlášení i oddíl Televize – co se dělo v aplikaci v televizi.
- **Poslat vývojáři** (na obrazovce hlášení, nebo ⚙ Nastavení → Obecné, dole): pošle hlášení – a tester i dávku
  snímků ze hry (Záznam snímků) – na server ReCaptionu a ukáže kód, např. **K7M2-QX9P**. Ten stačí poslat autorovi.
  Klíče, PIN ani čísla účtu se neposílají; na serveru se balík po 30 dnech sám smaže.
- **Snímky na Domů** (⚙ Nastavení → Experimentální → ZAP): zkratka pro opakované posílání snímků. Na Domů
  úplně dole přibude řádek Snímky s tlačítky **▶** (spustit záznam, zeleně, jen při běžícím překladu) a **■** (zastavit, červeně);
  během záznamu řádek ukazuje počet snímků. **Odeslat** rovnou pošle
  poslední dávku s hlášením a ukáže kód s tlačítkem Zkopírovat. Odeslaná dávka se z telefonu smaže.
- **Uložit snímky do telefonu** (⚙ Nastavení → Experimentální): dávku ze Záznamu snímků
  **sdílet jako ZIP** (chat, e-mail, Disk), uložit ZIP do **Stažených souborů** nebo snímky do **Galerie** (album ReCaption;
  obojí od Androidu 10). Snímky se pak dají poslat i rovnou z Galerie.
- **Záznam pro vývojáře v domácí síti**: ⚙ Nastavení → záložka Experimentální → „Zpřístupnit záznam v domácí síti“ → ZAP. Pod řádkem se ukáže adresa (např. `http://192.168.1.23:8789/zaznam?k=ab3k7m`), přes kterou si vývojář na počítači ve stejné síti stáhne celé hlášení (bez hesel, klíčů a čísla účtu). Po 2 hodinách se to samo vypne; jde to vypnout i hned.
- **Záznam snímků**: při běžícím překladu ⚙ Nastavení → Experimentální → „Záznam snímků“ → OK = Start. Aplikace ukládá snímky obrazovky hry (jen když se obraz změní, nejvýš asi jeden za sekundu) a k nim, co v tu chvíli četla. Ve hře udělat, co je potřeba ukázat (menu, úkol, dvouřádkové titulky…), a znovu OK = Stop. Každé Start…Stop je samostatná dávka (v řádku „Nahrává · dávka 3 · 42 snímků“). Se zapnutým záznamem v domácí síti jde Start/Stop i tlačítkem „● Snímky“ / „■ Snímky“ v notifikaci. Samo se zastaví po 10 minutách nebo se zastavením překladu; všechny dávky dohromady zaberou nejvýš 300 MB (nejstarší se mažou). Vývojář si je stáhne přes záznam v domácí síti.

## 7b. Xbox (Series X|S / One)
ReCaption umí číst titulky i z Xboxu. Hra jde do televize dál kabelem HDMI; ReCaption si z Xboxu
přes vzdálené hraní (Remote Play) bere jen kopii obrazu v nižší kvalitě. Nic neovládá a zvuk
nepřehrává – hraje se normálně ovladačem.

1. **Na Xboxu (jen poprvé):** Nastavení → Zařízení a připojení → Vzdálené funkce → zapnout
   „Povolit vzdálené funkce“. Režim napájení nastavit na „Spánek“.
2. **V ReCaptionu:** vybrat **Xbox** v průvodci (krok Konzole), v ⚙ Nastavení → Konzole, nebo na
   stránce Domů OK na souhrnném řádku → Konzole. Položka menu **Konzole** pak ukazuje stránku Xbox.
3. Na stránce Xbox (menu Konzole) zvolit **Přihlásit účet Microsoft**. Na obrazovce se ukáže kód. Na telefonu
   nebo počítači otevřít **microsoft.com/link** (nebo načíst čtvereček telefonem), zadat kód
   a přihlásit se účtem, kterým se hraje na Xboxu – druhý účet jako u PlayStationu tu není potřeba. E-mail a heslo
   se píšou jen na stránce Microsoftu, ne do televize ani do ReCaptionu. V telefonu stačí tlačítko „Otevřít stránku“.
4. Objeví se seznam Xboxů na účtu – vybrat ten, na kterém se hraje (jediný se vybere sám).
5. Spustit překlad jako obvykle a přepnout televizi na HDMI s Xboxem.

Když Xbox spí nebo je vypnutý, ReCaption ho nebudí – počká, až se zapne, a pak se připojí sám.
Přihlášení si ReCaption pamatuje (zašifrované v zařízení); odhlásit se jde na stránce Xbox.
Stav spojení na stránce Domů funguje stejně jako u PlayStationu.

## 8. Televize LG a Samsung
Televize LG a Samsung si obraz hry samy nepřečtou. ReCaption v nich jen ukazuje hru s pruhem
a číst a překládat musí telefon nebo box s Androidem ve stejné domácí síti (zkušebně i počítač
s Windows – https://recaption.pages.dev/pc.html). Typ televize se
volí v průvodci (krok Televize) a mění v ⚙ Nastavení → Typ televize nebo OK na souhrnném řádku
na stránce Domů: Android TV (pruh ukazuje toto zařízení, jako dřív), LG (webOS) nebo Samsung
(Tizen). V telefonu (Přihlášení pro TV) jsou tlačítka „Použít telefon pro TV LG“ a „Použít telefon pro TV Samsung“. Souhrnný řádek
na Domů ukazuje, jestli je aplikace z televize připojená; dokud se nikdy nepřipojila, je na Domů
upozornění s tlačítkem „Nahrát do TV“ (u starší verze „Aktualizovat v TV“). Nahrát (i aktualizovat) aplikaci v televizi jde
kdykoli i z Instalace (kapitola 3). Kdo měl dřív zapnuté „Posílat titulky do televize LG“, má typ LG.

**Displej telefonu, který čte, nesmí zhasnout** – se zhasnutým displejem telefon (hlavně Samsung) čte titulky až 10× pomaleji.
⚙ Nastavení → Obecné → „Nezhasínat displej telefonu“ (aplikace se na to zeptá při prvním Spustit).
Když překlad běží, tlačítko ☾ (Černý displej) na Domů displej celý zčerná – nezhasne, takže čtení se nezpomalí, ale nesvítí
a telefon tolik nehřeje. Zpět tlačítkem Zpět nebo jedním klepnutím na displej; po zastavení překladu se zavře samo.
Pod tlačítky přehrávače je v telefonu „≡ Titulky ke čtení“: přeložené repliky ze hry pod sebou (nejnovější dole, jména
v barvách jako v pruhu, se zapnutým Originálem i anglický text), aby šly číst i v telefonu. Displej na té stránce nezhasíná;
☾ vpravo nahoře ho zčerná a klepnutím se vrátí zpět k titulkům.

Pruh v televizi vypadá podle Titulků v ReCaptionu (průhlednost, písmo, barva, poloha). Když je
v televizi starší ReCaption, než nese aplikace v telefonu, upozorní na to Domů s tlačítkem k nahrání.

**Vzhled titulků přímo v televizi** (aplikace v televizi od 1.4.0): při hře stisknout na ovladači
**OK**. Otevře se malá nabídka s ukázkou titulku: pozadí pruhu, velikost a barva písma, okraj pruhu,
poloha pruhu, originál (a jeho velikost), barevná jména a vstup HDMI. Šipky
▲▼ vybírají, ◀▶ mění, Zpět nabídku zavře. Telefon a televize mají vždycky stejné nastavení. Všechno ostatní se nastavuje jen v telefonu.

### Televize LG

**Nejjednodušší je pomocník v aplikaci** („Nahrát ReCaption do TV LG“): ukáže postup krok
za krokem a kroky, které pozná sám, odškrtne. Televizi v síti najde sám (adresa se psát nemusí), pozná,
že je zapnutý vývojářský režim i Key Server, a na odkaz na účet LG ukáže čtvereček.

**Nahrání ReCaption do televize LG z telefonu nebo boxu (bez počítače):**
1. Na webu webostv.developer.lge.com založit účet LG (zdarma).
2. V televizi otevřít obchod LG Content Store, nainstalovat aplikaci **Developer Mode**, přihlásit se
   v ní tím účtem a zapnout **Dev Mode Status**. Televize se restartuje.
3. Znovu otevřít Developer Mode a zapnout **Key Server**. Ukáže se adresa televize (IP) a **Passphrase**.
4. V ReCaptionu zvolit „Nahrát ReCaption do TV LG“ (krok Televize v průvodci,
   upozornění na Domů, pomocník v telefonu nebo Instalace), opsat adresu a Passphrase a zvolit „Nahrát do TV“ (u starší verze „Aktualizovat v TV“). ReCaption se v televizi
   nainstaluje a spustí. Příště už Passphrase není potřeba – stejné tlačítko televizi
   aktualizuje. Starší verzi přes novější nikdy nenahraje. Novější verze aplikace pro TV může vyjít i bez nové
   verze ReCaptionu pro Android – telefon (nebo Android TV) si ji sám stáhne (jen se sítí, nejvýš jednou za pár hodin,
   na stránce nahrání hned) a na Domů nabídne „Aktualizovat v TV“. Bez sítě nahraje verzi, kterou má v sobě.

   **V telefonu se nic neopisuje:** zvolit „Načíst z obrazovky TV kamerou“, povolit kameru
   a namířit telefon na obrazovku Developer Mode tak, aby byla vidět adresa i Passphrase. Jakmile je
   ReCaption přečte dvakrát stejně, ukáže je; zvolit „Použít“, porovnat údaje s televizí a zvolit
   „Nahrát do TV“ (u starší verze „Aktualizovat v TV“). Pokud upozorní na podobné znaky (0/O, 1/I), zkontrolovat hlavně je –
   špatná Passphrase nic nepokazí, televize ji jen odmítne. Kamera nic nefotí, neukládá ani
   neposílá. Na televizi a boxu (bez kamery) se tlačítko neukazuje.

Vývojářský režim po čase vyprší a ReCaption pak z televize zmizí. Kolik dní zbývá, ukazuje
ReCaption u tlačítka (pod 3 dny upozorní i na stránce Domů). Prodloužit ho jde v televizi: Developer Mode →
**Extend**. Nebo zapnout v ReCaptionu ⚙ Nastavení → Obecné → **Prodlužovat vývojářský režim** (výchozí vypnuto; řádek
je i na stránce nahrání do TV u dnů režimu): ReCaption pak režim jednou denně prodlouží u služby LG sám – při otevření
aplikace a během překladu, klíčem relace z posledního nahrání do TV. Potřebuje internet; když se prodloužení nepovede,
ukáže to řádek dnů režimu („prodloužení se nepovedlo“) a zkusí to znovu za pár hodin.

Jiné cesty: z počítače programem webOS Dev Manager (soubor `receiver.ipk` z ReCaption.apk, postup na
https://recaption.pages.dev/lg.html#pocitac), nástrojem LG `ares` (postup ve `webos/CTI-ME.md`),
nebo z obchodu LG, až tam ReCaption bude.

### Televize Samsung
Samsung pustí do televize vlastní aplikaci jen ve vývojářském režimu a jen podepsanou
certifikátem, který Samsung vystaví na vlastní účet Samsung (zdarma) právě pro danou televizi.
Podpis i nahrání zařídí zdarma aplikace **Apps2Samsung** v telefonu s Androidem – bez
počítače, asi 10 minut. Průvodce „Nahrát ReCaption do TV Samsung“ (krok Televize v průvodci,
upozornění na Domů, pomocník v telefonu, Instalace) provede krok za krokem; kroky
se odškrtávají samy, kde to jde (jinak tlačítkem Hotovo):
1. **Vývojářský režim v TV:** Aplikace (Apps) → na ovladači 1 2 3 4 5 → **Developer mode** On →
   do **Host PC IP** adresu **telefonu** s Apps2Samsungem (průvodce v telefonu ji ukáže velkým
   písmem) → TV restartovat (podržet vypínač). Když průvodce najde TV Samsung v síti s vývojářským
   režimem, odškrtne krok sám.
2. **Apps2Samsung do telefonu** (tlačítko Stáhnout Apps2Samsung, na televizi čtvereček na stránku
   s odkazy). Je-li už v telefonu, průvodce to pozná.
3. **Přidat ReCaption do Apps2Samsungu:** tlačítko otevře Apps2Samsung s předvyplněným zdrojem
   ReCaption (na televizi čtvereček), stačí potvrdit. Ručně: ⋯ → Add a source →
   `https://github.com/adusa89/ReCaption`.
4. **Nainstalovat:** v Apps2Samsungu vybrat TV a u ReCaptionu Install (napoprvé přihlášení účtem
   Samsung). Pak v TV spustit ReCaption – průvodce ukáže „hotovo“, až se aplikace z televize
   opravdu připojí, a do televize pošle zkušební větu. Aktualizace: v Apps2Samsungu Update.

Bez telefonu s Androidem: rozbalovací řádek „Bez telefonu s Androidem: počítač“ uloží soubor
s aplikací (.wgt, nepodepsaný) a vede na Tizen Studio v počítači. Odkazy jsou na stránce
https://recaption.pages.dev/samsung.html.

Podrobnosti a důvody jsou v `tizen/CTI-ME.md`.

## 9. Lepší překlad: DeepL (doporučeno)
Offline překlad je zdarma a bez internetu, ale hodně doslovný. **DeepL** překládá přirozeně, rozumí
slangu i nadávkám a pro hráče je zdarma – stačí vlastní bezplatný účet DeepL API. Nastaví se v průvodci
(krok **Lepší překlad (DeepL)**) nebo kdykoli v **Titulky → Kvalita překladu** (rozbalovací skupina hned
pod jazykem překladu; vede tam i řádek s jazykem na Domů). Tamtéž je zapnutí a vypnutí, ověření klíče,
kolik znaků zbývá a odebrání klíče.

Jak to funguje: ReCaption počká na DeepL (nejvýš 2,5 s) a ukáže jeho překlad – i v televizi LG / Samsung.
Když DeepL nejde (bez internetu, vyčerpaný limit, špatný klíč), ukáže se offline překlad a důvod je vidět
v Titulkách → Kvalita překladu.

### Nastavení klíče – nic se nepíše ovladačem
Klíč je dlouhý (asi 40 znaků), proto se do televize **nepíše** – zkopíruje a vloží se jen v telefonu.
Televize ukáže **jeden čtvereček**; naskenuje se telefonem (Android i iPhone, stejná Wi‑Fi). Otevře se
stránka „ReCaption · DeepL“, která provede třemi kroky:

1. **Účet DeepL API zdarma** (jen poprvé) – tlačítko otevře ceník DeepL na záložce **API**
   (https://www.deepl.com/pro#api). U plánu **Developer** klepnout na **Vytvořte si účet zdarma**
   (anglicky **Sign up for free**).
   - **Bez účtu DeepL:** vyplnit e-mail a heslo (aspoň 8 znaků), u **Váš plán** nechat **Developer**
     (Growth je placený), zvolit **Pokračovat** (**Continue**) a **potvrdit e-mail** odkazem, který DeepL pošle.
   - **S účtem DeepL, i jen na překládání (Translator):** nejdřív se na deepl.com **přihlásit**, pak otevřít
     ceník → API a klepnout na totéž tlačítko u Developeru – plán se **přidá k tomuto účtu**, nový e-mail
     není potřeba (ověřeno 21. 11. 2026).
   - **Pozor:** odkazy „Získejte API klíč zdarma“ / „API zdarma“ v menu DeepL přihlášeného posílají zpátky
     do Translatoru. Platí jen tlačítko u plánu Developer v ceníku.
   - Plán je zdarma: **1 000 000 znaků celkem**, každý měsíc se neobnovuje. Platební kartu DeepL nechce.
2. **Stránka s klíčem** – https://www.deepl.com/your-account/keys (záložka **API Keys & Limits**). Celý klíč
   se tam nezobrazuje: zkopíruje se **ikonou kopírování** vedle klíče. Když tam klíč není, zvolit **Create key**.
   Když se místo klíčů ukáže **Přihlásit se k účtu**, přihlásit se účtem z kroku 1.
3. **Vložit klíč** – vrátit se na stránku „ReCaption · DeepL“, **podržet prst v políčku → Vložit** a zvolit
   **Odeslat do TV**. Televize mezitím
   ukazuje „Čekám na klíč z telefonu…“.
4. Televize klíč ověří, uloží a DeepL zapne: „✔ DeepL je nastavený“.

Když telefon stránku neotevře (mobilní data, jiná Wi‑Fi), připojit ho ke stejné Wi‑Fi a naskenovat znovu.
Ruční psaní ovladačem je jen záloha: **Napsat klíč ručně**. **Telefon jako čtečka** čtverečky nemá – má
tlačítka **Otevřít DeepL**, **Otevřít stránku s klíčem** a políčko, kam se klíč vloží.

### Dobré vědět
- **Mám DeepL, ale klíč nevidím / všechno mě přesměruje do Translatoru:** účet ještě nemá plán API.
  Přihlásit se, otevřít https://www.deepl.com/pro#api a u plánu **Developer** klepnout na **Vytvořte si účet
  zdarma** – plán se přidá k účtu. Pak stránka s klíči → **Create key**.
- **Registrace visí na „Security check“:** vypnout VPN nebo blokování reklam (na iPhonu i Soukromý přenos
  iCloud), případně zkusit jiný prohlížeč nebo mobilní data.
- **Limit:** plán Developer má 1 000 000 znaků celkem (aplikace po ověření ukáže, kolik zbývá).
  Opakované věty se znovu neposílají. Placený klíč (API Pro / Growth) funguje taky – aplikace pozná druh klíče sama.
- **Soukromí:** se zapnutým DeepL se **rozpoznaný text titulků posílá přes internet do DeepL**. Klíč patří
  jen hráči, zůstává zašifrovaný v zařízení s ReCaptionem a nikam jinam se neposílá (ani do hlášení).
- Klíč jde kdykoli odebrat tlačítkem **Odebrat klíč** a DeepL vypnout v Titulkách → Kvalita překladu.

## Licence a zdrojový kód
Spojení s PlayStationem používá otevřenou knihovnu chiaki-ng
(https://github.com/streetpea/chiaki-ng, licence AGPL-3.0). Zdrojový kód aplikace včetně
úprav je v tomto repozitáři. Aplikace není produktem společnosti Sony.
Nahrání do televize LG používá knihovnu JSch (https://github.com/mwiede/jsch, licence BSD) a postup
podle projektu PlayCaption (licence AGPL-3.0).
Spojení s Xboxem používá knihovnu WebRTC (sestavení stream-webrtc-android, licence BSD/Apache 2.0)
a postup podle projektů PlayCaption (AGPL-3.0), xbox-xcloud-player (MIT) a XStreaming (AGPL-3.0).
Aplikace není produktem společnosti Microsoft.
