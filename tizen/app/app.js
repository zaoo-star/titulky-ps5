/*
 * ReCaption pro televize LG (webOS) a Samsung (Tizen).
 *
 * Televize ukáže hru ze vstupu HDMI a přes ni pruh s překladem titulků. Sama si obraz
 * přečíst neumí, proto čte a překládá ReCaption na Androidu (telefon nebo box) v téže
 * domácí síti a sem posílá hotové věty. Televize si ho najde sama: zná svou adresu,
 * tak postupně zkusí všechny adresy téže sítě na portu PORT. Kdo odpoví zprávou
 * {"typ":"vitej"}, je ReCaption. Zprávy jsou popsané v app/…/core/TvSender.kt.
 *
 * Tohle je jediné místo, kde televize zprávy přijímá. Zkoušet jde i bez Androidu:
 * `node webos/posilac.js` posílá z počítače zkušební věty.
 *
 * Soubor je stejný ve webos/obchod/app i v tizen/app (hlídá to sestavení APK, úloha
 * samsungBalicek). Co se na LG a Samsungu dělá jinak, je v platforma.js (window.ReCaptionTv).
 *
 * Schválně psáno postaru (ES5: var, function, žádné šipky ani šablony),
 * protože televize z let 2016–2017 mají prohlížeč Chromium 38 (LG) a Samsung z let
 * 2015–2016 ještě starší WebKit.
 */
(function () {
  'use strict';

  var P = window.ReCaptionTv;

  var TEXTY = {
    cs: {
      nadpis: 'Překlad titulků ze hry',
      otazka: 'Vstup s PlayStationem',
      tl_ukazka: 'Ukázka',
      tl_napoveda: 'Nápověda',
      tl_jazyk: 'Jazyk: čeština',
      tl_zpet: 'Zpět',
      tl_konec: 'Ukončit',
      ovladani: '◀ ▶ ▲ ▼ výběr · OK potvrdit · Zpět ukončit',
      st_hledam: 'hledá se…',
      st_pripojeno: 'připojeno',
      st_vstup: 'nejde otevřít',
      st_sit: 'TV bez sítě',
      st_ok: 'vzhled titulků',
      uk_stitek: 'Ukázka',
      uk_popis: 'Hra ukazuje titulky anglicky. Titulky PS5 je přečtou, přeloží a překlad ukážou v pruhu dole.',
      np_nadpis: 'Jak to funguje',
      np_1: 'Do PC s Windows nainstalovat Titulky PS5 pro PC: zaoo-star.github.io/titulky-ps5/pc',
      np_2: 'V programu na PC spárovat PlayStation 5 a v Titulky → Hra vybrat hru. PC musí být ve stejné domácí síti jako televize.',
      np_3: 'Na PC zvolit ⚙ Nastavení → Použití → Čtečka pro TV a Spustit.',
      np_4: 'Tady v televizi vybrat vstup HDMI s PlayStationem. Volba se zapamatuje – příště se hra ukáže hned po spuštění. Televize si PC v síti najde sama.',
      np_5: 'Při hře: OK otevře nabídku (pozadí, písmo, barva, okraj, poloha, originál, jména, vstup HDMI). Jde i bez spojení s PC. Zpět vrátí úvodní obrazovku.',
      np_sony: 'Aplikace není produktem společnosti Sony Interactive Entertainment. „PlayStation“ a „PS5“ jsou jejich ochranné známky.',
      pn_nadpis: 'Vzhled titulků',
      kafe: 'Návody Titulky PS5',
      kafe_pod: 'zaoo-star.github.io/titulky-ps5',
      pn_pruhlednost: 'Pozadí pruhu',
      pn_velikost: 'Velikost písma',
      pn_poloha: 'Poloha pruhu',
      pn_original: 'Ukazovat i originál',
      pn_vstup: 'Vstup',
      pn_barva: 'Barva písma',
      pn_barvy: ['Bílá', 'Žlutá', 'Zelená', 'Modrá'],
      pn_okraj: 'Okraj pruhu',
      pn_vel_orig: 'Velikost originálu',
      pn_auto: 'Automaticky',
      pn_jmena: 'Jména barevně',
      pn_offline: 'Změny se nepodařilo uložit',
      pn_podle_hry: 'Podle hry',
      pn_polohy: ['Přes titulky', 'Těsně nad titulkem', 'Nad oblastí čtení', 'Nahoře'],
      pn_vlastni: 'Vlastní (z PC)',
      pn_ano: 'Zapnuto',
      pn_ne: 'Vypnuto',
      pn_ovladani: '▲▼ volba · ◀▶ změna · Zpět zavře. Ostatní nastavení je v programu na PC.',
      pn_ukazka: 'Takhle bude vypadat překlad titulků.',
      pn_ukazka_orig: 'This is how the subtitle translation will look.'
    },
    en: {
      nadpis: 'Translated game subtitles',
      otazka: 'PlayStation input',
      tl_ukazka: 'Demo',
      tl_napoveda: 'Help',
      tl_jazyk: 'Language: English',
      tl_zpet: 'Back',
      tl_konec: 'Exit',
      ovladani: '◀ ▶ ▲ ▼ select · OK confirm · Back exit',
      st_hledam: 'searching…',
      st_pripojeno: 'connected',
      st_vstup: 'cannot open',
      st_sit: 'TV offline',
      st_ok: 'subtitle look',
      uk_stitek: 'Demo',
      uk_popis: 'The game shows subtitles in English. Titulky PS5 reads them, translates them and shows ' +
        'the translation in the black bar below (here: Czech).',
      np_nadpis: 'How it works',
      np_1: 'Install Titulky PS5 for PC on a Windows PC: zaoo-star.github.io/titulky-ps5/pc',
      np_2: 'In the PC program, pair your PlayStation 5 and pick the game. The PC must be in the same home network as the TV.',
      np_3: 'On the PC, choose ⚙ Settings → Use → Reader for TV and press Start.',
      np_4: 'Here on the TV, choose the HDMI input of your PlayStation. The choice is remembered – next time the game shows right after start. The TV finds the PC in the network by itself.',
      np_5: 'While playing: OK opens the menu (background, text, colour, padding, position, original, names, HDMI input). It works without the PC too. Back returns to the start screen.',
      np_sony: 'This app is not a product of Sony Interactive Entertainment. “PlayStation” and “PS5” are their trademarks.',
      pn_nadpis: 'Subtitle look',
      kafe: 'Titulky PS5 guides',
      kafe_pod: 'zaoo-star.github.io/titulky-ps5',
      pn_pruhlednost: 'Bar background',
      pn_velikost: 'Text size',
      pn_poloha: 'Bar position',
      pn_original: 'Show the original too',
      pn_vstup: 'Input',
      pn_barva: 'Text colour',
      pn_barvy: ['White', 'Yellow', 'Green', 'Blue'],
      pn_okraj: 'Bar padding',
      pn_vel_orig: 'Original size',
      pn_auto: 'Automatic',
      pn_jmena: 'Coloured names',
      pn_offline: 'Changes could not be saved',
      pn_podle_hry: 'Like the game',
      pn_polohy: ['Over the subtitles', 'Right above the subtitle', 'Above the reading area', 'At the top'],
      pn_vlastni: 'Custom (from the PC)',
      pn_ano: 'On',
      pn_ne: 'Off',
      pn_ovladani: '▲▼ choose · ◀▶ change · Back closes. Everything else is in the PC program.',
      pn_ukazka: 'This is how the translation will look.',
      pn_ukazka_orig: 'This is how the subtitle translation will look.'
    }
  };

  // Ukázka: titulek ve hře a jeho překlad.
  var UKAZKA = [
    ['We need to get across the river.', 'Musíme se dostat na druhou stranu řeky.'],
    ['Wait here. I\'ll be right back.', 'Počkej tady. Hned jsem zpátky.'],
    ['I don\'t like this at all.', 'Tohle se mi vůbec nelíbí.'],
    ['Saddle up, we ride at dawn.', 'Nasedni na koně, vyrážíme za svítání.'],
    // dva mluvčí – každý má jméno ve své barvě
    ['Mira: I\'ll take the left door.\nTobin: Then I\'m going right.', '', [
      { jmeno: 'Mira', barva: '#F2C14E', text: 'Vezmu levé dveře.' },
      { jmeno: 'Tobin', barva: '#7FC8F8', text: 'Tak já jdu doprava.' }
    ]],
    // otázka a volby odpovědi – v pruhu jako seznam
    ['Kessler: So, what will it be?\n1. I\'ll take the job.\n2. Tell me more about the pay.\n3. Not interested.', '', [
      { jmeno: 'Kessler', barva: '#8EDB8A', text: 'Tak co bude?' },
      { volba: true, text: 'Vezmu tu práci.' },
      { volba: true, text: 'Řekni mi víc o výplatě.' },
      { volba: true, text: 'Nemám zájem.' }
    ]]
  ];

  var PORT = 8787;
  var jazyk = 'en';
  var T = TEXTY.en;
  var strana = 'domu';
  var vybrane = 0;
  var casovace = [];
  var tlacitka = document.querySelectorAll('#domu .tl');
  var udaje = { model: '?', system: '?' };

  // Spojení s ReCaptionem na Androidu běží od startu až do ukončení, nezávisle na obrazovce (od 1.5.17):
  // úvodní obrazovka ukazuje jeho stav a nabídka vzhledu jde použít i bez něj.
  var spojeni = null;
  var sitBezi = false;    // hledá se / drží se spojení (od startu do ukončení)
  var pevna = null;       // pevná adresa z parametrů spuštění (jen zkoušky), jinak null
  var sitCasovace = [];   // opakované hledání – přepnutí obrazovky je nemaže
  var prijem = null;      // při hře { hdmi: číslo vstupu, bezVstupu: jen snímek v počítači }, jinak null
  var hledani = 0;        // pořadové číslo hledání; starší rozběhnuté pokusy se podle něj zahodí
  var zpetCasovac = null;
  var posledniUkol = null;  // poslední úkol z Androidu – ukáže se znovu po návratu do hry

  // ---- Záznam událostí (pro vývojáře) ----------------------------------
  // Posledních ZAZNAM_MAX řádků toho, co se v televizi dělo (start, hledání, spojení, chyby).
  // Text titulků se nezapisuje, jen délka. Po připojení se pošle do ReCaptionu na Androidu
  // zprávou {"typ":"zaznam","radky":[…]} a pak po dávkách (viz TvSender.kt, TvLog.kt);
  // starší ReCaption na Androidu ji jen zahodí.
  var ZAZNAM_MAX = 300;
  var ZAZNAM_DAVKA = 25;
  var zaznam = [];
  var zaznamCelkem = 0;     // kolik řádků kdy vzniklo
  var zaznamOdeslano = 0;   // kolik z nich už odešlo

  function dvojmistne(n) { return (n < 10 ? '0' : '') + n; }

  var zaznamPosledni = [];  // poslední různé texty – hledání se opakuje dokola, zapíše se jen počet
  var zaznamOpakovani = 0;

  // Ticho mezi titulky (24. 11. 2026, přání majitele: ověřit, jestli spořič naskakuje spíš v dlouhé
  // pauze bez titulků, ne proto, že by appka titulky "počítala" jako aktivitu pro spořič – ta aktivitu
  // stejně nijak nehlásí, jen se to má tímhle potvrdit nebo vyvrátit podle skutečných hlášení). Hlásí se
  // jen začátek a konec ticha delšího než TICHO_PRAH, ne každých pár sekund.
  var TICHO_PRAH = 120000;
  var posledniTitulekCas = Date.now();
  var tichoOhlaseno = false;
  function oznamTitulek() {
    if (tichoOhlaseno) { zapis('TICHO|konec po ' + Math.round((Date.now() - posledniTitulekCas) / 1000) + ' s'); tichoOhlaseno = false; }
    posledniTitulekCas = Date.now();
  }
  setInterval(function () {
    var ticho = Date.now() - posledniTitulekCas;
    if (!tichoOhlaseno && ticho >= TICHO_PRAH) { zapis('TICHO|' + Math.round(ticho / 1000) + ' s bez titulku'); tichoOhlaseno = true; }
  }, 30000);

  function pridejRadek(text) {
    var d = new Date();
    zaznam.push(dvojmistne(d.getHours()) + ':' + dvojmistne(d.getMinutes()) + ':' + dvojmistne(d.getSeconds()) + ' ' + text);
    if (zaznam.length > ZAZNAM_MAX) { zaznam.shift(); }
    zaznamCelkem++;
  }

  function zapis(text) {
    text = String(text).replace(/[\r\n]+/g, ' ').substring(0, 200);
    if (zaznamPosledni.indexOf(text) >= 0) { zaznamOpakovani++; return; }
    if (zaznamOpakovani) { pridejRadek('(předchozí řádky se ' + zaznamOpakovani + '× opakovaly)'); zaznamOpakovani = 0; }
    zaznamPosledni.push(text);
    if (zaznamPosledni.length > 3) { zaznamPosledni.shift(); }
    pridejRadek(text);
  }

  // Pošle řádky, které ještě neodešly (nejvýš celý buffer), po dávkách ZAZNAM_DAVKA.
  function posliZaznam(ws) {
    var nove, radky, i;
    if (!ws) { return; }
    nove = Math.min(zaznam.length, zaznamCelkem - zaznamOdeslano);
    if (nove <= 0) { return; }
    radky = zaznam.slice(zaznam.length - nove);
    try {
      for (i = 0; i < radky.length; i += ZAZNAM_DAVKA) {
        ws.send(JSON.stringify({ typ: 'zaznam', radky: radky.slice(i, i + ZAZNAM_DAVKA) }));
      }
      zaznamOdeslano = zaznamCelkem;
    } catch (e) { /* spojení spadlo, pošle se příště */ }
  }

  window.onerror = function (zprava, soubor, radek) {
    zapis('CHYBA: ' + zprava + ' (' + String(soubor || '').split('/').pop() + ':' + radek + ')');
    return false;
  };

  function el(id) { return document.getElementById(id); }

  function sitPozdeji(fn, ms) {
    var t = setTimeout(fn, ms);
    sitCasovace.push(t);
    if (sitCasovace.length > 20) { sitCasovace.shift(); }
    return t;
  }

  // Zapamatovaný vstup HDMI 1–4, jinak 0.
  function pametHdmi() {
    var n = parseInt(pamet('hdmi'), 10);
    return n >= 1 && n <= 4 ? n : 0;
  }

  // Čtení vrací text, zápis true/false (povedl se?).
  function pamet(klic, hodnota) {
    try {
      if (hodnota === undefined) { return window.localStorage.getItem(klic) || ''; }
      window.localStorage.setItem(klic, hodnota);
      return true;
    } catch (e) { /* bez paměti se jen hledá déle */ }
    return hodnota === undefined ? '' : false;
  }

  // ---- Texty ------------------------------------------------------------

  function nastavJazyk(kod) {
    var i;
    var prvky = document.querySelectorAll('[data-t]');
    jazyk = /^(cs|sk)/i.test(kod || '') ? 'cs' : 'en';
    T = TEXTY[jazyk];
    document.documentElement.lang = jazyk;
    for (i = 0; i < prvky.length; i++) {
      prvky[i].textContent = T[prvky[i].getAttribute('data-t')] || '';
    }
    domuPodpis = '';
    domuStavKresli();
    kafeKresli();
  }

  // ---- Dotazy na systém televize (platforma.js) ---------------------

  function zjistiTelevizi() {
    P.zjistiTelevizi(function (model, system) {
      udaje.model = model;
      udaje.system = system;
      zapis('televize: ' + model + ', ' + system);
    });
  }

  // ---- Obrazovky --------------------------------------------------------

  // ---- Stav při hře (od 1.5.0): body s tečkou jako Domů v ReCaptionu na Androidu --------------
  // Vlastní řádky televize (vstup HDMI, spojení s ReCaptionem) a pod nimi řádky z Androidu
  // (zpráva {"typ":"stav","radky":[{"barva":"ok|pozor|info","text":"…"}]}, od ReCaptionu 0.97.14).
  // Když je všechno zelené, po chvíli zmizí; oranžový řádek zůstává, dokud se to nespraví.
  var stav = { vstupChyba: false, bezSite: false, android: [] };
  var stavPodpis = '';
  var stavCasovac = null;

  // naDomu: řádky pro úvodní obrazovku – bez vstupu HDMI a bez nápovědy k OK (od 1.5.17)
  function stavRadky(naDomu) {
    var r = [];
    if (!naDomu && !prijem) { return r; }
    // čeká na spojení: jediný řádek o ReCaptionu (od 1.5.16, přání majitele – HDMI ani řádky z Androidu tam nedávají smysl)
    // ceka: pod řádkem slabý běžící pruh (od 1.5.18, přání majitele) – jen když se opravdu hledá
    if (!spojeni) { r.push(stav.bezSite ? { barva: 'pozor', text: T.st_sit } : { barva: 'pozor', text: 'Titulky PS5 · ' + T.st_hledam, ceka: true }); return r; }
    // vstup HDMI jen při potížích (jako na Android TV, kde řádek HDMI není); na úvodní obrazovce ne
    if (!naDomu && stav.vstupChyba) { r.push({ barva: 'pozor', text: 'HDMI ' + prijem.hdmi + ' · ' + T.st_vstup }); }
    r.push({ barva: 'ok', text: 'Titulky PS5 · ' + T.st_pripojeno });
    r = r.concat(stav.android);
    // nápověda k OK: tlačítko v rámečku místo šedé tečky (od 1.5.4); jen při hře
    if (!naDomu) { r.push({ barva: 'klavesa', text: T.st_ok }); }
    return r;
  }

  // Slabý běžící pruh pod řádkem, na který se čeká (od 1.5.18): odsazený jako text za tečkou, animace jen v CSS.
  function cekaPruh() {
    var p = document.createElement('div'), beh = document.createElement('div');
    p.className = 'st-ceka';
    beh.className = 'st-ceka-beh';
    p.appendChild(beh);
    return p;
  }

  // Podpis řádků ke zjištění změny (barva, text a čekání).
  function radkyPodpis(r) {
    var i, podpis = '';
    for (i = 0; i < r.length; i++) { podpis += r[i].barva + (r[i].ceka ? '~' : '') + r[i].text + '|'; }
    return podpis;
  }

  // Řádky s tečkou do prvku e (stav při hře i na úvodní obrazovce).
  function radkyDo(e, r) {
    var i, radek, tecka, text;
    e.textContent = '';
    for (i = 0; i < r.length; i++) {
      radek = document.createElement('div');
      radek.className = 'st-radek';
      tecka = document.createElement('span');
      if (r[i].barva === 'klavesa') { tecka.className = 'klavesa'; tecka.textContent = 'OK'; }
      else { tecka.className = 'tecka ' + r[i].barva; }
      text = document.createElement('span');
      text.textContent = r[i].text;
      radek.appendChild(tecka);
      radek.appendChild(text);
      if (r[i].ceka) { radek.className = 'st-radek ceka'; radek.appendChild(cekaPruh()); }
      e.appendChild(radek);
    }
  }

  // Stav na úvodní obrazovce (od 1.5.17): ReCaption · připojeno / hledá se a řádky z Androidu.
  var domuPodpis = '';
  function domuStavKresli() {
    var r, podpis;
    if (strana !== 'domu') { domuPodpis = ''; return; }
    r = stavRadky(true);
    podpis = radkyPodpis(r);
    if (podpis === domuPodpis) { return; }
    domuPodpis = podpis;
    radkyDo(el('domu-stav'), r);
  }

  // ukazat: true = ukázat znovu; jinak se jen překreslí, když je vidět (nebo když se stav změnil)
  function stavKresli(ukazat) {
    var e = el('hlaseni'), r = stavRadky(false), i, pozor = false, podpis = radkyPodpis(r);
    domuStavKresli();
    for (i = 0; i < r.length; i++) { if (r[i].barva === 'pozor') { pozor = true; } }
    if (podpis !== stavPodpis) { stavPodpis = podpis; ukazat = true; }
    if (!r.length) { e.style.display = 'none'; return; }
    if (!ukazat && e.style.display !== 'block') { return; }
    radkyDo(e, r);
    e.style.display = 'block';
    if (stavCasovac) { clearTimeout(stavCasovac); stavCasovac = null; }
    // při otevřené nabídce vzhledu (OK) zůstává stav vidět, dokud se nabídka nezavře
    if (!pozor && panelRadek < 0) { stavCasovac = setTimeout(function () { el('hlaseni').style.display = 'none'; }, 8000); }
  }

  function stavSkryj() {
    if (stavCasovac) { clearTimeout(stavCasovac); stavCasovac = null; }
    el('hlaseni').style.display = 'none';
  }

  function prectiStav(z) {
    var out = [], i, b, rr = z.radky;
    if (!rr || !rr.length) { return out; }
    for (i = 0; i < rr.length && i < 6; i++) {
      if (!rr[i] || typeof rr[i].text !== 'string') { continue; }
      b = rr[i].barva === 'ok' || rr[i].barva === 'pozor' ? rr[i].barva : 'info';
      out.push({ barva: b, text: rr[i].text.substring(0, 200) });
    }
    return out;
  }

  function pruh(text) {
    if (text === null) { el('pruh').style.display = 'none'; return; }
    var t = el('titulek'), radky, i, d;
    if (jeRtl(text)) {
      // hebrejština: řádek po řádku, každý se svým směrem (originál pod překladem zůstane zleva)
      t.textContent = '';
      radky = String(text).split('\n');
      for (i = 0; i < radky.length; i++) {
        d = smer(document.createElement('div'), radky[i]);
        d.textContent = radky[i];
        t.appendChild(d);
      }
    } else {
      t.textContent = text;
    }
    el('pruh').style.display = 'flex';
  }

  // Barva jména z Androidu – jen zápis #RRGGBB, nic jiného se do stylu nedostane.
  function barvaJmena(b) {
    b = String(b || '');
    return /^#[0-9A-Fa-f]{6}$/.test(b) ? b : '';
  }

  // ---- Směr textu (hebrejština, od přípravy jazyků 23. 11. 2026) -------
  // Řádek s písmem psaným zprava doleva dostane dir="rtl" – celý řádek zprava doleva i se jménem „LUCIA:“
  // na začátku a tečkou na správné straně (stejné pravidlo jako core/Bidi.kt a TEXT_DIRECTION_ANY_RTL na Androidu;
  // dir="auto" by podle prvního písmene latinky „LUCIA“ zvolil zleva). Ostatní jazyky: atribut se nenastaví,
  // vypadají přesně jako dřív. Prvky s pevným text-align (left / right / center) zarovnání nemění.
  var RTL_ZNAKY = /[\u0590-\u08FF\uFB1D-\uFDFF\uFE70-\uFEFC]/;

  function jeRtl(t) {
    return RTL_ZNAKY.test(t === undefined || t === null ? '' : String(t));
  }

  // nastaví / zruší směr prvku podle textu; prvek, který zprava doleva nikdy nebyl, se nemění
  function smer(e, text) {
    if (!e) { return e; }
    if (jeRtl(text)) { e.dir = 'rtl'; }
    else if (e.dir === 'rtl') { e.dir = ''; }
    return e;
  }

  // jméno mluvčího „Jméno: “ do kusu; v řádku zprava doleva je jméno izolované (bdi), ať latinka jména
  // nepřehází pořadí s hebrejským textem
  function jmenoDo(kus, jmeno, rtl) {
    var b;
    if (!rtl) { kus.textContent = jmeno + ': '; return; }
    kus.textContent = '';
    b = document.createElement('bdi');
    b.textContent = jmeno;
    kus.appendChild(b);
    kus.appendChild(document.createTextNode(': '));
  }

  function cislo(x, od, az, vychozi) {
    x = Number(x);
    return isFinite(x) ? Math.max(od, Math.min(az, x)) : vychozi;
  }

  // Kam položit pruh nad oblast čtení / na pevné místo, aby nezakryl anglické titulky, které se
  // čtou (stejná pravidla jako ScreenLayout.barTop na Androidu). Vše jako zlomky výšky obrazu.
  function mimoCteni(chci, h, cteni) {
    var mezera = 0.006, nad, pod;
    if (chci + h > cteni[0] - mezera && chci < cteni[1] + mezera) {
      nad = cteni[0] - mezera - h;
      pod = cteni[1] + mezera;
      if (nad >= 0 && (chci + h / 2 <= (cteni[0] + cteni[1]) / 2 || pod + h > 1)) { chci = nad; }
      else if (pod + h <= 1) { chci = pod; }
      else { chci = Math.max(0, nad); }
    }
    return Math.max(0, Math.min(Math.max(0, 1 - h), chci));
  }

  // Vzhled a poloha pruhu podle Nastavení v ReCaptionu na Androidu (pole vzhled, viz core/TvLook.kt):
  // průhlednost, písmo, barva a poloha přes titulky / těsně nad titulkem / nad oblastí čtení / na pevném místě.
  // Bez pole vzhled (starší ReCaption) zůstane pruh jako dřív – černý, dole přes celou šířku.
  // Barva jména z částí titulku (casti) pro řádek originálu – stejné jméno, stejná barva.
  function barvaPodleJmena(casti, jmeno) {
    var i, k = String(jmeno).toLowerCase();
    if (!casti || !casti.length) { return null; }
    for (i = 0; i < casti.length; i++) {
      if (casti[i] && casti[i].jmeno && String(casti[i].jmeno).toLowerCase() === k) { return barvaJmena(casti[i].barva); }
    }
    return null;
  }

  // Originál pod překladem po řádcích; jméno mluvčího v barvě jako v překladu (od 1.5.2).
  function puvodniRadky(puvodni, text, casti) {
    var radky = String(text).split('\n'), i, m, radek, kus, b;
    for (i = 0; i < radky.length; i++) {
      radek = document.createElement('div');
      m = /^([^:]{1,40}):\s*(.*)$/.exec(radky[i]);
      b = m ? barvaPodleJmena(casti, m[1]) : null;
      if (m && b) {
        kus = document.createElement('span');
        kus.className = 'jmeno barevne';
        kus.style.color = b;
        kus.textContent = m[1] + ': ';
        radek.appendChild(kus);
        radek.appendChild(document.createTextNode(m[2]));
      } else {
        radek.textContent = radky[i];
      }
      puvodni.appendChild(radek);
    }
  }

  function vzhledPruhu(v, casti) {
    var p = el('pruh'), t = el('titulek'), s = p.style;
    var W = 1920, H = 1080;
    var cteni, text, pres, maxW, w, h, top, stred, puvodni, x;
    s.left = ''; s.top = ''; s.bottom = ''; s.width = ''; s.maxWidth = ''; s.minHeight = ''; s.background = ''; s.borderRadius = '';
    t.style.fontSize = ''; t.style.color = ''; t.style.fontWeight = ''; t.style.padding = ''; t.style.textShadow = ''; t.style.textAlign = ''; t.style.lineHeight = '';
    if (!v || typeof v !== 'object' || p.style.display === 'none') { return; }
    if (typeof v.original === 'string' && v.original) {
      puvodni = document.createElement('div');
      puvodni.className = 'puvodni';
      puvodniRadky(puvodni, v.original, casti);
      // velikost originálu z Nastavení v telefonu (od 1.5.12; bez pole 62 % jako dřív)
      if (typeof v.pismoOrig === 'number') { puvodni.style.fontSize = (cislo(v.pismoOrig, 30, 100, 62) / 100) + 'em'; }
      t.appendChild(puvodni);
    }
    s.background = 'rgba(0,0,0,' + (cislo(v.pozadi, 0, 100, 100) / 100) + ')';
    // průhledný pruh (pod 60 %, od 1.5.7 až 0 %): černý obrys písma, ať je text čitelný i na světlém obrazu
    if (cislo(v.pozadi, 0, 100, 100) < 60) { t.style.textShadow = '0 0 3px #000000, 0 0 6px #000000, 0 2px 4px #000000'; }
    // zaoblené rohy jako na Android TV (od 1.5.2)
    s.borderRadius = '18px';
    t.style.fontSize = cislo(v.pismo, 16, 120, 50) + 'px';
    if (/^#[0-9A-Fa-f]{6}$/.test(String(v.barva || ''))) { t.style.color = v.barva; }
    if (v.tucne === true) { t.style.fontWeight = 'bold'; }
    // okraje podle velikosti písma – menší písmo = nižší pruh (od 1.5.3)
    // okraj nad a pod textem podle volby 0–3 v telefonu (od 1.5.4; bez ní 1)
    x = [0.04, 0.16, 0.32, 0.5][Math.round(cislo(v.okraj, 0, 3, 1))];
    t.style.padding = x + 'em 0.8em ' + (x + 0.04) + 'em';
    cteni = [cislo(v.cteni && v.cteni[0], 0, 1, 0.74), cislo(v.cteni && v.cteni[1], 0, 1, 0.98)];
    text = null;
    if (v.text && v.text.length >= 2) {
      text = [cislo(v.text[0], 0, 1, 0), cislo(v.text[1], 0, 1, 0), cislo(v.text[2], 0, 1, -1), cislo(v.text[3], 0, 1, -1)];
      if (!(text[1] > text[0])) { text = null; }
    }
    pres = v.poloha === 'pres';
    // šířka podle textu (ne přes celou obrazovku), nejvýš podle nastavení
    maxW = Math.round(W * cislo(v.sirka, 0.2, 1, 0.84));
    s.bottom = 'auto'; s.top = '0px'; s.left = '0px'; s.width = 'auto'; s.maxWidth = maxW + 'px';
    if (pres && text && v.kryt === true) {
      // zakrýt celou oblast titulků (od 1.5.14): text = celé pole posledních titulků i s okrajem → pruh přesně tak vysoký
      s.minHeight = Math.round(Math.max(0.02, Math.min(0.33, text[1] - text[0])) * H) + 'px';
    } else if (pres) {
      // přes titulky: pruh aspoň tak vysoký, aby zakryl anglický text, nejvýš čtvrtina obrazovky
      x = text ? text[1] - text[0] : cteni[1] - cteni[0];
      s.minHeight = Math.min(Math.round(Math.max(0.02, Math.min(1, x + 0.024)) * H), H / 4) + 'px';
    } else {
      s.minHeight = '0px';
    }
    // zaokrouhlit nahoru: o zlomek pixelu užší pruh by zalomil poslední slovo na další řádek
    w = p.getBoundingClientRect ? Math.ceil(p.getBoundingClientRect().width) : p.offsetWidth;
    w = w || maxW;
    if (pres && text && text[3] > text[2]) { w = Math.max(w, Math.round((text[3] - text[2]) * W) + (v.kryt === true ? 0 : 40)); }
    w = Math.max(160, Math.min(W, w));
    s.width = w + 'px';
    s.maxWidth = '';
    h = (p.offsetHeight || 150) / H;
    if (pres) {
      top = text ? (text[0] + text[1]) / 2 - h / 2 : (cteni[0] + cteni[1]) / 2 - h / 2;
      top = Math.max(0, Math.min(Math.max(0, 1 - h), top));
    } else if (v.poloha === 'tesne' && text) {
      // těsně nad nalezený anglický titulek (posouvá se s ním)
      top = Math.max(0, text[0] - 0.006 - h);
    } else if (v.poloha === 'stred') {
      top = mimoCteni(cislo(v.stred, 0, 1, 0.08) - h / 2, h, cteni);
    } else if (text && cteni[1] - cteni[0] > 0.6) {
      // automatika ještě hledá (čte se celá obrazovka) → těsně nad nalezený text
      top = Math.max(0, text[0] - 0.006 - h);
    } else {
      top = mimoCteni(cteni[0] - 0.006 - h, h, cteni);
    }
    // pevné místo (od 1.5.9): překlad menu hry hned vedle menu, řádky pod sebou zarovnané vlevo
    if (v.poloha === 'misto' && v.misto && v.misto.length >= 2) {
      t.style.textAlign = 'left';
      // řádek proti řádku (od 1.5.10): výška řádku = rozestup položek menu ve hře, písmo se jí přizpůsobí
      x = v.misto.length >= 3 ? cislo(v.misto[2], 0, 0.2, 0) * H : 0;
      if (x > 0) {
        t.style.lineHeight = Math.round(x) + 'px';
        t.style.fontSize = Math.min(cislo(v.pismo, 16, 120, 50), Math.round(x * 0.62)) + 'px';
        t.style.padding = '0 0.6em';
        w = Math.ceil(p.getBoundingClientRect ? p.getBoundingClientRect().width : p.offsetWidth) || w;
        h = (p.offsetHeight || 150) / H;
      }
      s.left = Math.round(Math.max(0, Math.min(W - w, cislo(v.misto[0], 0, 1, 0.3) * W))) + 'px';
      s.top = Math.round(Math.max(0, Math.min(H - h * H, cislo(v.misto[1], 0, 1, 0.2) * H - x * 0.25))) + 'px';
      return;
    }
    stred = pres && text && text[3] > text[2] ? (text[2] + text[3]) / 2 : 0.5;
    s.left = Math.round(Math.max(0, Math.min(W - w, stred * W - w / 2))) + 'px';
    s.top = Math.round(Math.max(0, Math.min(H - h * H, top * H))) + 'px';
  }

  // Pruh po řádcích (pole casti, viz TvSender.kt): jména v barvách mluvčích a volby odpovědí
  // jako seznam s odrážkami. Bez casti (starší ReCaption na Androidu) jen obyčejný text.
  // vzhled = nastavení z Androidu (viz vzhledPruhu), bez něj výchozí pruh.
  function pruhCasti(casti, text, vzhled) {
    var t = el('titulek');
    var i, c, radek, kus, seznam = null;
    if (!casti || Object.prototype.toString.call(casti) !== '[object Array]' || !casti.length) {
      pruh(text);
      vzhledPruhu(vzhled, null);
      return;
    }
    t.textContent = '';
    for (i = 0; i < casti.length; i++) {
      c = casti[i] || {};
      radek = document.createElement('div');
      if (c.vybrana) {
        // vybraná položka přeloženého menu hry – černý text na bílém jako ve hře (od 1.5.29)
        seznam = null;
        kus = document.createElement('span');
        kus.className = 'vybrana';
        kus.textContent = String(c.text || '');
        radek.appendChild(kus);
        t.appendChild(smer(radek, c.text));
        continue;
      }
      if (c.pokyn) {
        // pokyn pro hráče (GTA) – menší a šedý řádek, ne replika (od 1.5.5)
        seznam = null;
        radek.className = 'pokyn';
        radek.textContent = String(c.text || '');
        t.appendChild(smer(radek, c.text));
        continue;
      }
      if (c.volba) {
        // volby pod sebou zarovnané vlevo, celý seznam uprostřed pruhu
        if (!seznam) {
          seznam = document.createElement('div');
          seznam.className = 'volby';
          t.appendChild(seznam);
        }
        kus = document.createElement('span');
        kus.className = 'odrazka';
        kus.textContent = '• ';
        radek.appendChild(kus);
        radek.appendChild(document.createTextNode(String(c.text || '')));
        seznam.appendChild(smer(radek, c.text));
      } else {
        seznam = null;
        if (c.jmeno) {
          kus = document.createElement('span');
          kus.className = 'jmeno';
          jmenoDo(kus, String(c.jmeno), jeRtl(c.text));
          // bez barvy (uživatel má jména bez barvy) zůstane jméno bílé jako text
          if (barvaJmena(c.barva)) { kus.style.color = barvaJmena(c.barva); kus.className = 'jmeno barevne'; }
          radek.appendChild(kus);
        }
        radek.appendChild(document.createTextNode(String(c.text || '')));
        t.appendChild(smer(radek, c.text));
      }
    }
    el('pruh').style.display = 'flex';
    vzhledPruhu(vzhled, casti);
  }

  // ---- Úkol ve hře (od 1.5.11) ------------------------------------------
  // Řádek s úkolem (GTA VI dole pod titulky) má vlastní prvek #ukol, nezávislý na pruhu:
  // skrytí pruhu (skryj, vypršení) ho neschová a naopak. Zpráva z Androidu
  // {"typ":"ukol","text":…,"misto":[vlevo,nahoře,vpravo,dole],"pismo":…,"pozadi":…,"barva":…,"casti":[…]}
  // – misto = obdélník anglického úkolu jako zlomky obrazu (jako vzhled.text), pismo v px na 1080 řádků,
  // pozadi 0–100 (100 = černé), casti = barevné kusy textu (barva null = výchozí). Prázdný text = schovat.
  var UKOL_MAX = 300;
  var ukolPodpis = '';   // co je naposledy v záznamu, ať se stejný úkol nezapisuje dokola

  function ukolSkryj() {
    var u = el('ukol');
    u.style.display = 'none';
    u.textContent = '';
    ukolPodpis = '';
  }

  function ukolUkaz(z) {
    var u = el('ukol'), s = u.style;
    var W = 1920, H = 1080;
    var text = String(z.text || '').substring(0, UKOL_MAX);
    var casti = z.casti, i, c, kus, zbyva, barva, m, cx, cy, maxW, w, h, pozadi, podpis;
    if (!/\S/.test(text)) {
      if (ukolPodpis) { zapis('úkol skryt'); }
      ukolSkryj();
      return;
    }
    u.textContent = '';
    if (casti && Object.prototype.toString.call(casti) === '[object Array]' && casti.length) {
      // barevné kusy jako span, celkem nejvýš UKOL_MAX znaků
      zbyva = UKOL_MAX;
      for (i = 0; i < casti.length && i < 40 && zbyva > 0; i++) {
        c = casti[i] || {};
        kus = document.createElement('span');
        kus.textContent = String(c.text || '').substring(0, zbyva);
        zbyva -= kus.textContent.length;
        if (barvaJmena(c.barva)) { kus.style.color = barvaJmena(c.barva); }
        u.appendChild(kus);
      }
    } else {
      casti = null;
      u.textContent = text;
    }
    smer(u, text);
    pozadi = cislo(z.pozadi, 0, 100, 90);
    barva = barvaJmena(z.barva);
    s.color = barva || '#FFFFFF';
    s.background = 'rgba(0,0,0,' + (pozadi / 100) + ')';
    s.fontSize = cislo(z.pismo, 16, 120, 40) + 'px';
    // nejdřív změřit: šířka podle textu, nejvýš 90 % obrazovky
    maxW = Math.round(W * 0.9);
    s.left = '0px'; s.top = '0px'; s.width = 'auto'; s.maxWidth = maxW + 'px';
    s.display = 'block';
    w = u.getBoundingClientRect ? Math.ceil(u.getBoundingClientRect().width) : u.offsetWidth;
    w = Math.max(1, Math.min(maxW, w || maxW));
    h = u.offsetHeight || Math.round(cislo(z.pismo, 16, 120, 40) * 1.6);
    // střed obdélníku anglického úkolu; bez platného misto dole uprostřed
    m = z.misto;
    cx = 0.5; cy = 0.9;
    if (m && Object.prototype.toString.call(m) === '[object Array]' && m.length >= 4) {
      m = [cislo(m[0], 0, 1, -1), cislo(m[1], 0, 1, -1), cislo(m[2], 0, 1, -1), cislo(m[3], 0, 1, -1)];
      if (m[0] >= 0 && m[1] >= 0 && m[2] > m[0] && m[3] > m[1]) {
        cx = (m[0] + m[2]) / 2;
        cy = (m[1] + m[3]) / 2;
      }
    }
    s.left = Math.round(Math.max(0, Math.min(W - w, cx * W - w / 2))) + 'px';
    s.top = Math.round(Math.max(0, Math.min(H - h, cy * H - h / 2))) + 'px';
    // jen délka a počet kusů, ne text úkolu (zásada soukromí záznamu)
    podpis = (z.id !== undefined ? '#' + String(z.id).substring(0, 20) + ': ' : '') + text.length + ' znaků' +
      (casti ? ', ' + casti.length + ' částí' : '');
    if (podpis !== ukolPodpis) { zapis('úkol ' + podpis); ukolPodpis = podpis; }
  }

  // ---- Menu vpravo dole (od 1.5.19) ------------------------------------
  // Nápovědy tlačítek vpravo dole (GTA VI: autopilot, kamera, postavy) mají vlastní prvek #roh, nezávislý na pruhu
  // i úkolu. Zpráva z Androidu {"typ":"roh","rezim":"nad"|"pres","pismo":…,"pozadi":…,"barva":…,
  // "polozky":[{"text":…,"misto":[vlevo,nahoře,vpravo,dole],"seda":false},…]} – každá položka zarovnaná doprava
  // k misto[2], nejméně široká misto[2] − misto[0], výška řádku misto, doleva nejdál k 55 % šířky.
  // Prázdný seznam = schovat. Od 1.5.25 nepovinné "ikona" u položky: ikona tlačítka za textem jako ve hře –
  // "x" / "o" / "s" / "t" (bílý kotouč se znakem), "L1" / "R1" / "L2" / "R2", víc mezerou ("L2 R2").
  var ROH_MAX = 8;
  // Od 1.5.42 i "LS" / "RS" – páčka (písmeno v kolečku se čtyřmi šipkami).
  var ROH_IKONY = { x: 1, o: 1, s: 1, t: 1, L1: 1, R1: 1, L2: 1, R2: 1, L3: 1, R3: 1, LS: 1, RS: 1 };

  // Ikony tlačítka za textem položky: kotouč se znakem poskládaným z rámečků (× dvě čáry, ○ kroužek, □ čtverec,
  // △ dva trojúhelníky z okrajů) – znaky Unicode starší webOS neumí; L1…R2 bílý obdélník s textem. Neznámá = nic.
  function rohIkony(d, ikona, seda) {
    var casti = String(ikona).split(' '), i, j, k, s, c, n = 0;
    for (i = 0; i < casti.length && n < 2; i++) {
      k = casti[i];
      if (!ROH_IKONY.hasOwnProperty(k)) { continue; }
      s = document.createElement('span');
      if (k === 'LS' || k === 'RS') {
        s.className = 'roh-ik roh-ik-pa';
        c = document.createElement('b');
        c.textContent = k.charAt(0);
        s.appendChild(c);
        for (j = 1; j <= 4; j++) {
          c = document.createElement('i');
          c.className = 'z' + j;
          s.appendChild(c);
        }
      } else if (k.length === 2) {
        s.className = 'roh-tl';
        s.textContent = k;
      } else {
        s.className = 'roh-ik roh-ik-' + k;
        for (j = 0; j < (k === 'x' || k === 't' ? 2 : 1); j++) {
          c = document.createElement('i');
          c.className = 'z' + (j + 1);
          s.appendChild(c);
        }
      }
      if (seda) { s.className += ' roh-seda'; }
      d.appendChild(s);
      n++;
    }
  }

  function rohSkryj() {
    var u = el('roh');
    if (!u) { return; }
    u.style.display = 'none';
    u.textContent = '';
  }

  function rohUkaz(z) {
    var u = el('roh'), W = 1920, H = 1080, p = z.polozky, i, c, m, d, s, t, pismo, pozadi, barva, prava, sirka, vyska, n = 0;
    if (!u) { return; }
    if (!p || Object.prototype.toString.call(p) !== '[object Array]' || !p.length) { rohSkryj(); return; }
    u.textContent = '';
    pismo = cislo(z.pismo, 12, 120, 32);
    pozadi = cislo(z.pozadi, 0, 100, 85);
    barva = barvaJmena(z.barva) || '#FFFFFF';
    for (i = 0; i < p.length && n < ROH_MAX; i++) {
      c = p[i] || {};
      m = c.misto;
      if (!/\S/.test(String(c.text || '')) || !m || Object.prototype.toString.call(m) !== '[object Array]' || m.length < 4) { continue; }
      m = [cislo(m[0], 0, 1, -1), cislo(m[1], 0, 1, -1), cislo(m[2], 0, 1, -1), cislo(m[3], 0, 1, -1)];
      if (m[0] < 0 || m[1] < 0 || m[2] <= 0.55 || m[3] <= m[1] || m[0] > m[2]) { continue; }
      d = document.createElement('div');
      d.className = 'roh-pol';
      smer(d, c.text);
      if (typeof c.ikona === 'string' && c.ikona) {
        t = document.createElement('span');
        t.textContent = String(c.text).substring(0, 60);
        d.appendChild(t);
        rohIkony(d, c.ikona, c.seda === true);
      } else {
        d.textContent = String(c.text).substring(0, 60);
      }
      s = d.style;
      prava = Math.round(m[2] * W);
      sirka = Math.round((m[2] - Math.max(m[0], 0.55)) * W);
      vyska = Math.max(1, Math.round((m[3] - m[1]) * H));
      s.right = (W - prava) + 'px';
      s.top = Math.round(m[1] * H) + 'px';
      s.height = vyska + 'px';
      s.lineHeight = vyska + 'px';
      s.minWidth = sirka + 'px';
      s.maxWidth = Math.round((m[2] - 0.55) * W) + 'px';
      s.fontSize = pismo + 'px';
      s.color = c.seda === true ? '#8C7C4C' : barva;
      s.background = 'rgba(0,0,0,' + (pozadi / 100) + ')';
      u.appendChild(d);
      n++;
    }
    if (!n) { rohSkryj(); return; }
    u.style.display = 'block';
  }

  // ---- Pokyny u předmětů (od 1.5.20) -----------------------------------
  // Krátké pokyny VERZÁLKAMI přímo v obraze hry (GTA VI: „□ SMASH WINDOW“ u okna auta), na libovolném místě.
  // Vlastní prvek #predmety, nezávislý na pruhu, úkolu i menu vpravo dole. Zpráva z Androidu
  // {"typ":"predmety","pismo":…,"pozadi":…,"barva":…,"polozky":[{"text":…,"misto":[vlevo,nahoře,vpravo,dole]},…]}
  // – text od levého horního rohu misto, výška řádku misto, nejméně široký misto, nejvýš do 97 % šířky obrazu,
  // jeden řádek. Prázdný seznam = schovat.
  var PREDMETY_MAX = 3;

  function predmetySkryj() {
    var u = el('predmety');
    if (!u) { return; }
    u.style.display = 'none';
    u.textContent = '';
  }

  function predmetyUkaz(z) {
    var u = el('predmety'), W = 1920, H = 1080, p = z.polozky, i, c, m, d, s, pismo, pozadi, barva, levo, vyska, n = 0;
    if (!u) { return; }
    if (!p || Object.prototype.toString.call(p) !== '[object Array]' || !p.length) { predmetySkryj(); return; }
    u.textContent = '';
    pismo = cislo(z.pismo, 12, 120, 32);
    pozadi = cislo(z.pozadi, 0, 100, 85);
    barva = barvaJmena(z.barva) || '#FFFFFF';
    for (i = 0; i < p.length && n < PREDMETY_MAX; i++) {
      c = p[i] || {};
      m = c.misto;
      if (!/\S/.test(String(c.text || '')) || !m || Object.prototype.toString.call(m) !== '[object Array]' || m.length < 4) { continue; }
      m = [cislo(m[0], 0, 1, -1), cislo(m[1], 0, 1, -1), cislo(m[2], 0, 1, -1), cislo(m[3], 0, 1, -1)];
      if (m[0] < 0 || m[1] < 0 || m[2] < 0 || m[3] <= m[1] || m[0] > m[2] || m[0] >= 0.97) { continue; }
      d = document.createElement('div');
      d.className = 'predmet';
      d.textContent = String(c.text).substring(0, 40);
      smer(d, c.text);
      s = d.style;
      levo = Math.round(m[0] * W);
      vyska = Math.max(1, Math.round((m[3] - m[1]) * H));
      s.left = levo + 'px';
      s.top = Math.round(m[1] * H) + 'px';
      s.height = vyska + 'px';
      s.lineHeight = vyska + 'px';
      s.minWidth = Math.round((m[2] - m[0]) * W) + 'px';
      s.maxWidth = Math.max(1, Math.round(0.97 * W) - levo) + 'px';
      s.fontSize = pismo + 'px';
      s.color = barva;
      s.background = 'rgba(0,0,0,' + (pozadi / 100) + ')';
      u.appendChild(d);
      n++;
    }
    if (!n) { predmetySkryj(); return; }
    u.style.display = 'block';
  }

  // ---- Popisky menu hry na svém místě (od 1.5.31) -----------------------
  // Zpráva z Androidu {"typ":"menupolozky","polozky":[{"text":…,"misto":[vlevo,nahoře,vpravo,dole],"pismo":…,
  // "zarovnani":"l"|"r","pozadi":0–100,"vybrana":true?}]} – překlad položky menu hned vpravo od originálu (vybraná
  // černým písmem v bílém pruhu hry), záložka Nastavení, patička nad / vlevo od tlačítek. Prázdný seznam = schovat.
  // od 1.5.38 až 32 položek (popisky kolem ovladače ve Schématu ovládání, 0.97.144); dřív 16
  var MENUPOLOZKY_MAX = 32;

  function menuPolozkySkryj() {
    var u = el('menupolozky');
    if (!u) { return; }
    u.style.display = 'none';
    u.textContent = '';
  }

  function menuPolozkyUkaz(z) {
    var u = el('menupolozky'), W = 1920, H = 1080, p = z.polozky, i, c, m, d, s, pismo, pozadi, stred, n = 0;
    if (!u) { return; }
    if (!p || Object.prototype.toString.call(p) !== '[object Array]' || !p.length) { menuPolozkySkryj(); return; }
    u.textContent = '';
    for (i = 0; i < p.length && n < MENUPOLOZKY_MAX; i++) {
      c = p[i] || {};
      m = c.misto;
      if (!/\S/.test(String(c.text || '')) || !m || Object.prototype.toString.call(m) !== '[object Array]' || m.length < 4) { continue; }
      m = [cislo(m[0], 0, 1, -1), cislo(m[1], 0, 1, -1), cislo(m[2], 0, 1, -1), cislo(m[3], 0, 1, -1)];
      if (m[0] < 0 || m[1] < 0 || m[2] < 0 || m[3] <= m[1] || m[0] > m[2]) { continue; }
      d = document.createElement('div');
      d.className = 'menupolozka' + (c.vybrana === true ? ' mp-vybrana' : '');
      // odstín písma překladu (od 1.5.42, nepovinné "barva": "#RRGGBB") – ať je vidět, že to je překlad, ne originál
      if (c.vybrana !== true && typeof c.barva === 'string' && /^#[0-9A-Fa-f]{6}$/.test(c.barva)) { d.style.color = c.barva; }
      smer(d, c.text);
      // ikona položky ze hry před textem (od 1.5.32) – bílé PNG, u vybrané černě (CSS filtr)
      if (typeof c.ikona === 'string' && c.ikona.indexOf('data:image/png;base64,') === 0 && c.ikona.length < 4000) {
        var obr = document.createElement('img');
        obr.className = 'mp-ikona';
        obr.src = c.ikona;
        d.appendChild(obr);
      }
      if (c.casti && Object.prototype.toString.call(c.casti) === '[object Array]' && c.casti.length) {
        // patička (od 1.5.35): text a za ním ikona tlačítka jako ve hře
        for (var k = 0; k < c.casti.length && k < 6; k++) {
          if (k > 0) { d.appendChild(document.createTextNode('\u00A0\u00A0\u00A0')); }
          d.appendChild(document.createTextNode(String((c.casti[k] || {}).text || '').substring(0, 60)));
          if (typeof (c.casti[k] || {}).tlacitko === 'string') { rohIkony(d, c.casti[k].tlacitko, false); }
        }
      } else {
        d.appendChild(document.createTextNode(String(c.text).substring(0, 120)));
      }
      s = d.style;
      pismo = cislo(c.pismo, 12, 120, 28);
      pozadi = cislo(c.pozadi, 0, 100, 0);
      stred = Math.round((m[1] + m[3]) / 2 * H);
      s.fontSize = pismo + 'px';
      s.lineHeight = Math.round(pismo * 1.25) + 'px';
      s.top = Math.round(stred - pismo * 0.625) + 'px';
      if (c.zarovnani === 'r') { s.right = Math.max(0, W - Math.round(m[2] * W)) + 'px'; }
      else { s.left = Math.round(m[0] * W) + 'px'; }
      // vybraná s pozadím (záložka Nastavení, od 1.5.33) = černě na bílém jako ve hře
      // od 1.5.47 vybraná na světle žlutém (barva překladu menu), ne na bílém – ať je vidět, že to je překlad
      if (pozadi > 0) { s.background = (c.vybrana === true ? 'rgba(255,226,138,' : 'rgba(0,0,0,') + (pozadi / 100) + ')'; }
      u.appendChild(d);
      n++;
    }
    if (!n) { menuPolozkySkryj(); return; }
    u.style.display = 'block';
  }

  // ---- Telefon ve hře (od 1.5.22) --------------------------------------
  // Přeložený obsah telefonu ve hře (GTA VI: zprávy, seznamy) jako malý telefon s bublinami ve vlastním prvku #telefon.
  // Zpráva z Androidu {"typ":"telefon","misto":[vlevo,nahoře,vpravo,dole],"pismo":…,"pozadi":…,"nadpis":…,
  // "zpravy":[{"text":…,"strana":"l"|"p"|"s"},…]} – panel od levého horního rohu misto, šířka misto, výška podle obsahu,
  // dole nejníž misto[3]. Když se nevejde: písmo menší (po 10 % až na 70 %), pak se skryjí nejstarší (horní) zprávy.
  // „l“ = příchozí (vlevo, šedá), „p“ = vlastní (vpravo, modrá), „s“ = jiný text přes celou šířku. Prázdné zprávy = schovat.
  // Od 1.5.46 nepovinně "volna":true – panel na volné straně obrazu (opačná polovina než telefon ve hře); přesun na druhou
  // stranu dojede plynule (třída tel-volna s přechodem left/top), bez pole se panel jen přeloží jako dřív.
  var TELEFON_MAX = 12;

  function telefonSkryj() {
    var u = el('telefon'), o = el('telefonpres');
    if (o) { o.style.display = 'none'; o.textContent = ''; }
    if (!u) { return; }
    u.style.display = 'none';
    u.textContent = '';
  }

  // Překlad telefonu přímo přes text v telefonu (od 1.5.28): {"typ":"telefon","rezim":"pres","pismo":px,
  // "zpravy":[{"text":…,"misto":[vlevo,nahoře,vpravo,dole]}]} – každý odstavec na místě originálu, světlé pozadí, tmavé písmo.
  function telefonPres(z) {
    var o = el('telefonpres'), u = el('telefon'), W = 1920, H = 1080, p = z.zpravy, i, m, d, pismo, w, n = 0;
    if (!o) { return; }
    if (u) { u.style.display = 'none'; u.textContent = ''; }
    o.textContent = '';
    pismo = cislo(z.pismo, 14, 60, 22);
    for (i = 0; i < p.length && i < 10; i++) {
      m = p[i] && p[i].misto;
      if (!m || Object.prototype.toString.call(m) !== '[object Array]' || m.length < 4) { continue; }
      m = [cislo(m[0], 0, 1, -1), cislo(m[1], 0, 1, -1), cislo(m[2], 0, 1, -1), cislo(m[3], 0, 1, -1)];
      if (m[0] < 0 || m[1] < 0 || m[2] <= m[0]) { continue; }
      d = document.createElement('div');
      d.className = z.vedle === true ? 'tp-odstavec tp-vedle' : 'tp-odstavec';
      d.textContent = String(p[i].text || '').substring(0, 400);
      smer(d, p[i].text);
      w = Math.max(60, Math.round((m[2] - m[0] + 0.012) * W));
      d.style.left = Math.round(Math.max(0, (m[0] - 0.006) * W)) + 'px';
      d.style.top = Math.round(Math.max(0, (m[1] - 0.004) * H)) + 'px';
      d.style.width = w + 'px';
      d.style.fontSize = pismo + 'px';
      o.appendChild(d);
      n++;
    }
    o.style.display = n ? 'block' : 'none';
  }

  // barva bubliny z profilu hry ("barvy":{"p":"#RRGGBB","l":"#RRGGBB"}, ReCaption 1.0.10) – průhledná jako výchozí v CSS;
  // bez pole nebo s neplatnou hodnotou zůstane výchozí modrá z CSS
  function telBarva(d, barvy, strana) {
    var b = barvy && (strana === 'p' || strana === 'l') ? barvy[strana] : null;
    if (typeof b !== 'string' || !/^#[0-9A-Fa-f]{6}$/.test(b)) { return; }
    d.style.background = 'rgba(' + parseInt(b.substr(1, 2), 16) + ', ' + parseInt(b.substr(3, 2), 16) + ', ' + parseInt(b.substr(5, 2), 16) + ', 0.4)';
  }

  function telefonUkaz(z) {
    var u = el('telefon'), W = 1920, H = 1080, p = z.zpravy, m, i, c, d, s, nadpis, strana, text, bubliny = [], seznam,
      pismo, krok, vyska, h, skryto;
    if (!u) { return; }
    if (z.rezim === 'pres' && p && Object.prototype.toString.call(p) === '[object Array]' && p.length) { telefonPres(z); return; }
    if (el('telefonpres')) { el('telefonpres').style.display = 'none'; el('telefonpres').textContent = ''; }
    m = z.misto;
    if (!p || Object.prototype.toString.call(p) !== '[object Array]' || !p.length ||
        !m || Object.prototype.toString.call(m) !== '[object Array]' || m.length < 4) { telefonSkryj(); return; }
    m = [cislo(m[0], 0, 1, -1), cislo(m[1], 0, 1, -1), cislo(m[2], 0, 1, -1), cislo(m[3], 0, 1, -1)];
    if (m[0] < 0 || m[1] < 0 || m[2] <= m[0] || m[3] <= m[1]) { telefonSkryj(); return; }
    u.textContent = '';
    nadpis = String(z.nadpis || '').substring(0, 40);
    if (/\S/.test(nadpis)) {
      d = document.createElement('div');
      d.className = 'tel-nadpis';
      d.textContent = nadpis;
      u.appendChild(d);
    }
    seznam = document.createElement('div');
    seznam.className = 'tel-zpravy';
    // nejvýš 12 posledních zpráv (nejnovější je dole)
    for (i = Math.max(0, p.length - TELEFON_MAX * 3); i < p.length; i++) {
      c = p[i] || {};
      // „c“ = čára místo obrázku (příspěvek v sociální síti, fotka v konverzaci; od 1.5.23) – starší TV ji jako
      // prázdnou zprávu vynechají
      if (c.strana === 'c') {
        if (!bubliny.length) { continue; }
        // od 1.5.24 obrázek ze hry (JPEG jako data: adresa) místo čáry
        if (typeof c.obrazek === 'string' && c.obrazek.indexOf('data:image/jpeg;base64,') === 0 && c.obrazek.length < 70000) {
          d = document.createElement('img');
          d.className = 'tel-obrazek';
          d.src = c.obrazek;
        } else {
          d = document.createElement('div');
          d.className = 'tel-cara';
        }
        bubliny.push(d);
        continue;
      }
      text = String(c.text === undefined || c.text === null ? '' : c.text);
      strana = c.strana === 'p' ? 'p' : (c.strana === 's' ? 's' : 'l');
      // bublina jen s emoji (ReCaption 1.00-rc21): bez textu, s obrázkem ze hry
      if (!/\S/.test(text) && strana !== 's' && typeof c.obrazek === 'string' && c.obrazek.indexOf('data:image/jpeg;base64,') === 0 &&
          c.obrazek.length < 70000) {
        d = document.createElement('div');
        d.className = 'tel-zprava tel-' + strana + ' tel-emoji';
        telBarva(d, z.barvy, strana);
        s = document.createElement('img');
        s.src = c.obrazek;
        d.appendChild(s);
        bubliny.push(d);
        continue;
      }
      if (!/\S/.test(text)) { continue; }
      d = document.createElement('div');
      d.className = 'tel-zprava tel-' + strana;
      telBarva(d, z.barvy, strana);
      d.textContent = text.substring(0, 400);
      smer(d, text);
      bubliny.push(d);
    }
    if (bubliny.length > TELEFON_MAX) { bubliny = bubliny.slice(bubliny.length - TELEFON_MAX); }
    if (!bubliny.length) { telefonSkryj(); return; }
    for (i = 0; i < bubliny.length; i++) { seznam.appendChild(bubliny[i]); }
    u.appendChild(seznam);
    u.className = z.volna === true ? 'tel-volna' : '';
    s = u.style;
    pismo = cislo(z.pismo, 12, 60, 28);
    vyska = Math.max(1, Math.round((m[3] - m[1]) * H));
    s.left = Math.round(m[0] * W) + 'px';
    s.top = Math.round(m[1] * H) + 'px';
    s.width = Math.max(1, Math.round((m[2] - m[0]) * W)) + 'px';
    s.maxHeight = vyska + 'px';
    s.fontSize = pismo + 'px';
    s.background = 'rgba(18,20,26,' + (cislo(z.pozadi, 0, 100, 90) / 100) + ')';
    s.display = 'block';
    // nevejde se: nejdřív menší písmo (nejvýš na 70 %), pak pryč nejstarší zprávy – poslední zůstane vždy
    h = u.offsetHeight || 0;
    for (krok = 9; h > vyska && krok >= 7; krok--) {
      s.fontSize = Math.round(pismo * krok / 10) + 'px';
      h = u.offsetHeight || 0;
    }
    skryto = 0;
    while (h > vyska && skryto < bubliny.length - 1) {
      bubliny[skryto].style.display = 'none';
      skryto++;
      h = u.offsetHeight || 0;
    }
    // delší překlad poslední (jediné zbylé) zprávy se nemusí vejít ani s nejmenším písmem – dřív se zbytek jen
    // oříznul (majitel: „originál má poslední zprávu dole celou, u nás polovina chybí“); okno smí být vyšší,
    // posune se nahoru, ať nic nezmizí
    if (h > vyska) {
      s.maxHeight = h + 'px';
      s.top = Math.max(0, Math.round(m[1] * H) - (h - vyska)) + 'px';
    }
  }

  // ---- Popis volby Nastavení hry (od 1.5.13) ----------------------------
  // Nadpis a popis vybrané volby v Nastavení hry (GTA VI vpravo) má vlastní prvek #menupopis přes anglický popis;
  // v pruhu je mezitím jen vybraná záložka. Zpráva z Androidu
  // {"typ":"menupopis","nadpis":…,"text":…,"misto":[vlevo,nahoře,vpravo,dole],"pismo":…,"pozadi":…,"barva":…}
  // – text od levého horního rohu misto, zalomený na šířku misto, dole nejníž misto[3] (od 1.5.14 se písmo zmenší).
  // Prázdný text i nadpis = schovat.
  var POPIS_MAX = 600;

  function popisSkryj() {
    var u = el('menupopis'), n = el('menunadpis'), l = el('menuseznam');
    if (l) { l.style.display = 'none'; l.textContent = ''; }
    if (n) { n.style.display = 'none'; n.textContent = ''; }
    if (!u) { return; }
    u.style.display = 'none';
    u.textContent = '';
  }

  // Nadpis volby zvlášť nad anglickým nadpisem (od 1.5.25, pole nadpisMisto): dolní hrana na misto[3], větší písmo.
  // Vrací true, když se nadpis ukázal (pak nepatří do #menupopis).
  function nadpisUkaz(z, nadpis, barva) {
    var e = el('menunadpis'), m = z.nadpisMisto, s, W = 1920, H = 1080, w, h, top;
    if (!e || !/\S/.test(nadpis) || !m || Object.prototype.toString.call(m) !== '[object Array]' || m.length < 4) {
      if (e) { e.style.display = 'none'; e.textContent = ''; }
      return false;
    }
    m = [cislo(m[0], 0, 1, -1), cislo(m[1], 0, 1, -1), cislo(m[2], 0, 1, -1), cislo(m[3], 0, 1, -1)];
    if (m[0] < 0 || m[3] <= 0 || m[2] <= m[0]) { e.style.display = 'none'; return false; }
    s = e.style;
    e.textContent = nadpis;
    smer(e, nadpis);
    s.color = barva || '#FFFFFF';
    s.background = 'rgba(0,0,0,' + (cislo(z.pozadi, 0, 100, 90) / 100) + ')';
    s.fontSize = cislo(z.pismoNadpis, 16, 140, 40) + 'px';
    w = Math.max(200, Math.round((m[2] - m[0]) * W));
    s.maxWidth = w + 'px';
    s.left = Math.round(Math.max(0, Math.min(W - 100, m[0] * W))) + 'px';
    s.top = '0px';
    s.display = 'block';
    h = e.offsetHeight || 0;
    top = Math.max(Math.round(m[3] * H) - h, Math.round(m[1] * H) - Math.round(h / 3));
    s.top = Math.max(0, top) + 'px';
    return true;
  }

  // Seznam pod popisem volby (od 1.5.29, barvy v Color Adjustments): položky pod sebou vpravo od originálu,
  // výška řádku = rozestup ve hře (seznamKrok px), písmo 60 % řádku.
  function seznamUkaz(z) {
    var e = el('menuseznam'), p = z.seznam, m = z.seznamMisto, i, d, krok, W = 1920, H = 1080;
    if (!e) { return; }
    e.textContent = '';
    if (!p || Object.prototype.toString.call(p) !== '[object Array]' || !p.length || !m ||
        Object.prototype.toString.call(m) !== '[object Array]' || m.length < 4) { e.style.display = 'none'; return; }
    krok = cislo(z.seznamKrok, 12, 120, 36);
    for (i = 0; i < p.length && i < 16; i++) {
      d = document.createElement('div');
      d.textContent = String(p[i] || '').substring(0, 60);
      smer(d, p[i]);
      d.style.height = krok + 'px';
      d.style.lineHeight = krok + 'px';
      e.appendChild(d);
    }
    e.style.fontSize = Math.round(krok * 0.6) + 'px';
    e.style.left = Math.round(cislo(m[0], 0, 1, 0.7) * W) + 'px';
    e.style.top = Math.round(cislo(m[1], 0, 1, 0.3) * H) + 'px';
    e.style.maxWidth = Math.max(100, Math.round((cislo(m[2], 0, 1, 0.99) - cislo(m[0], 0, 1, 0.7)) * W)) + 'px';
    e.style.color = barvaJmena(z.barva) || '#FFFFFF';
    e.style.display = 'block';
  }

  function popisUkaz(z) {
    var u = el('menupopis'), s, W = 1920, H = 1080, m, nadpis, text, n, w, h, barva;
    if (!u) { return; }
    s = u.style;
    nadpis = String(z.nadpis || '').substring(0, 120);
    text = String(z.text || '').substring(0, POPIS_MAX);
    m = z.misto;
    if ((!/\S/.test(text) && !/\S/.test(nadpis)) || !m || Object.prototype.toString.call(m) !== '[object Array]' || m.length < 4) {
      popisSkryj();
      return;
    }
    m = [cislo(m[0], 0, 1, -1), cislo(m[1], 0, 1, -1), cislo(m[2], 0, 1, -1), cislo(m[3], 0, 1, -1)];
    if (m[0] < 0 || m[1] < 0 || m[2] <= m[0]) { popisSkryj(); return; }
    seznamUkaz(z);
    u.textContent = '';
    if (nadpisUkaz(z, nadpis, barvaJmena(z.barva))) { nadpis = ''; }
    if (!/\S/.test(text) && !/\S/.test(nadpis)) { u.style.display = 'none'; return; }
    if (/\S/.test(nadpis)) {
      n = document.createElement('b');
      n.textContent = nadpis;
      u.appendChild(n);
      if (/\S/.test(text)) { u.appendChild(document.createTextNode('\n')); }
    }
    if (/\S/.test(text)) { u.appendChild(document.createTextNode(text)); }
    smer(u, nadpis + text);
    barva = barvaJmena(z.barva);
    s.color = barva || '#FFFFFF';
    s.background = 'rgba(0,0,0,' + (cislo(z.pozadi, 0, 100, 90) / 100) + ')';
    s.fontSize = cislo(z.pismo, 16, 120, 32) + 'px';
    w = Math.max(200, Math.round((m[2] - m[0]) * W));
    s.width = w + 'px';
    s.left = Math.round(Math.max(0, Math.min(W - w, m[0] * W))) + 'px';
    s.top = '0px';
    s.display = 'block';
    h = u.offsetHeight || 0;
    // od 1.5.26 stálé písmo (zmenšování podle délky textu dělalo „skákání“ velikosti); když se nevejde, okno jde níž
    // nejvýš k 96 % výšky a teprve pak nahoru
    s.top = Math.round(Math.max(0, Math.min(H * 0.96 - h, m[1] * H))) + 'px';
  }

  // ---- Vzhled titulků z ovladače (od 1.4.0) -----------------------------
  // Všechno nastavení patří ReCaptionu na Androidu. Televize dostane zprávou `nastaveni` pár voleb,
  // ukáže je v malé nabídce (OK při hře) a o změnu požádá zprávou `zmen`; Android ji ověří, uloží
  // a pošle zpět nové hodnoty (viz core/TvSettings.kt). Hodnoty jsou stejné jako v telefonu.
  var PRUHLEDNOSTI = [0, 20, 40, 60, 80, 90, 100];
  var VELIKOSTI = [0, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48];
  var POLOHY = ['pres', 'tesne', 'nad', 'nahore'];
  //
  // Od 1.5.17 jde nabídka otevřít i bez spojení: volby se drží i v televizi (localStorage „vzhled“,
  // poslední hodnoty z Androidu nebo změněné v televizi) a změny bez spojení čekají v „vzhled_zmeny“.
  // Pošlou se dnešními zprávami `zmen` až po první zprávě `nastaveni` z Androidu po připojení (jinak
  // by je přepsala); Android pak pošle nové `nastaveni`. Protokol se nemění.
  // barvy písma jako Prefs.TEXT_COLORS v telefonu (index), okraj 0–3 jako Prefs.BAR_PADS
  var BARVY_PISMA = ['#FFFFFF', '#FFE066', '#A8F0A8', '#9FD8FF'];
  var OKRAJE = [0, 1, 2, 3];
  var nastaveni = null;     // volby v nabídce (z Androidu, nebo uložené v televizi)
  var zAndroidu = false;    // v tomto spojení už přišlo `nastaveni` → změny jdou rovnou do Androidu
  var cekajici = {};        // klíč → hodnota: změny, které ještě neodešly
  var panelRadek = -1;
  var posledni = null;
  var ukazkaVidet = false;

  function prectiNastaveni(z) {
    var n = {
      pruhlednost: cislo(z.pruhlednost, 0, 100, 100),
      velikost: z.podleHry === true ? 0 : cislo(z.velikost, 0, 120, 22),
      poloha: POLOHY.indexOf(z.poloha) >= 0 ? z.poloha : 'vlastni',
      original: z.original === true,
      // od 1.5.18: okraj, barva písma, velikost originálu, barevná jména (od staršího Androidu výchozí)
      okraj: Math.round(cislo(z.okraj, 0, 3, 1)),
      barvaPisma: Math.round(cislo(z.barvaPisma, 0, BARVY_PISMA.length - 1, 0)),
      velikostOrig: VELIKOSTI.indexOf(z.velikostOrig) >= 0 ? z.velikostOrig : 0,
      jmena: z.jmena !== false
    };
    return n;
  }

  // Řádky nabídky OK (od 1.5.18 podle seznamu): velikost originálu jen se zapnutým originálem.
  function panelRadky() {
    var r = ['pruhlednost', 'velikost', 'barvaPisma', 'okraj', 'poloha', 'original'];
    if (nastaveni && nastaveni.original) { r.push('velikostOrig'); }
    r.push('jmena', 'vstup');
    return r;
  }

  // Jen volby a hodnoty, které přijme i Android (core/TvSettings.kt).
  function platnaZmena(klic, hodnota) {
    if (klic === 'pruhlednost') { return PRUHLEDNOSTI.indexOf(hodnota) >= 0; }
    if (klic === 'velikost') { return VELIKOSTI.indexOf(hodnota) >= 0; }
    if (klic === 'poloha') { return POLOHY.indexOf(hodnota) >= 0; }
    if (klic === 'original' || klic === 'jmena') { return hodnota === true || hodnota === false; }
    if (klic === 'okraj') { return OKRAJE.indexOf(hodnota) >= 0; }
    if (klic === 'barvaPisma') { return hodnota >= 0 && hodnota < BARVY_PISMA.length && hodnota === Math.round(hodnota); }
    if (klic === 'velikostOrig') { return VELIKOSTI.indexOf(hodnota) >= 0; }
    return false;
  }

  // Uloží volby; nepovedené uložení změny, která neodešla do Androidu, se v nabídce ukáže (povedené se nehlásí).
  var neulozeno = false;
  function ulozVzhled() {
    var ok = pamet('vzhled', JSON.stringify(nastaveni)) === true;
    ok = pamet('vzhled_zmeny', JSON.stringify(cekajici)) === true && ok;
    neulozeno = !ok && maCekajici();
    if (neulozeno) { zapis('vzhled se nepodařilo uložit v televizi'); }
  }

  // Volby uložené v televizi (při startu); bez nich výchozí jako v telefonu.
  function nactiVzhled() {
    var n = null, z = null, k;
    try { n = JSON.parse(pamet('vzhled') || 'null'); } catch (e) { n = null; }
    nastaveni = prectiNastaveni(n && typeof n === 'object' ? n : { pruhlednost: 100, velikost: 0, poloha: 'pres', original: false });
    try { z = JSON.parse(pamet('vzhled_zmeny') || 'null'); } catch (e) { z = null; }
    cekajici = {};
    if (z && typeof z === 'object') {
      for (k in z) {
        if (Object.prototype.hasOwnProperty.call(z, k) && platnaZmena(k, z[k])) { cekajici[k] = z[k]; nastaveni[k] = z[k]; }
      }
    }
  }

  function maCekajici() {
    var k;
    for (k in cekajici) { if (Object.prototype.hasOwnProperty.call(cekajici, k)) { return true; } }
    return false;
  }

  // Pošle změny udělané bez spojení (po první zprávě `nastaveni` z Androidu v tomto spojení).
  function posliCekajici() {
    var klice = [], k, i, poslano = 0;
    if (!spojeni || !zAndroidu) { return; }
    for (k in cekajici) { if (Object.prototype.hasOwnProperty.call(cekajici, k)) { klice.push(k); } }
    for (i = 0; i < klice.length; i++) {
      k = klice[i];
      try { spojeni.send(JSON.stringify({ typ: 'zmen', klic: k, hodnota: cekajici[k] })); } catch (e) { zapis('uložené změny se nepodařilo poslat'); break; }
      // do potvrzení z Androidu (nové `nastaveni`) ukazuje nabídka změněnou hodnotu
      nastaveni[k] = cekajici[k];
      delete cekajici[k];
      poslano++;
    }
    if (poslano) { zapis('uložené změny vzhledu odeslány: ' + poslano); }
  }

  // Poslední vzhled z Androidu upravený podle voleb v nabídce (náhled hned po změně).
  function nahled(ukazka) {
    var v = {}, k, zdroj = (posledni && posledni.vzhled) || { pozadi: 100, pismo: 50, poloha: 'nad', cteni: [0.74, 0.98], sirka: 0.84 };
    for (k in zdroj) { if (Object.prototype.hasOwnProperty.call(zdroj, k)) { v[k] = zdroj[k]; } }
    if (!nastaveni) { return v; }
    v.pozadi = nastaveni.pruhlednost;
    if (nastaveni.velikost > 0) { v.pismo = Math.round(nastaveni.velikost * 50 / 22); }
    if (nastaveni.poloha === 'nahore') { v.poloha = 'stred'; v.stred = 0.08; }
    else if (nastaveni.poloha !== 'vlastni') { v.poloha = nastaveni.poloha; }
    if (!nastaveni.original) { delete v.original; }
    else if (ukazka && !v.original) { v.original = UKAZKA_JMENO + ': ' + T.pn_ukazka_orig; }
    v.okraj = nastaveni.okraj;
    v.barva = BARVY_PISMA[nastaveni.barvaPisma] || '#FFFFFF';
    // velikost originálu jako podíl písma překladu v % (jako SubtitleOverlay), 0 = 62 %
    v.pismoOrig = nastaveni.velikostOrig > 0 ? Math.round(nastaveni.velikostOrig * 50 / 22 * 100 / cislo(v.pismo, 16, 120, 50)) : 62;
    return v;
  }

  // Ukázková věta v nabídce se jménem mluvčího – je vidět i volba Jména barevně.
  var UKAZKA_JMENO = 'Alex';
  function ukazkaCasti() {
    return [{ jmeno: UKAZKA_JMENO, barva: nastaveni && nastaveni.jmena ? '#F2C14E' : '', text: T.pn_ukazka }];
  }

  // Poslední titulek pro náhled; s vypnutými barevnými jmény bez barev.
  function castiNahledu(seznam) {
    var r = [], i, c, k, kopie;
    if (!seznam || (nastaveni && nastaveni.jmena)) { return seznam; }
    for (i = 0; i < seznam.length; i++) {
      c = seznam[i] || {};
      kopie = {};
      for (k in c) { if (Object.prototype.hasOwnProperty.call(c, k) && k !== 'barva') { kopie[k] = c[k]; } }
      r.push(kopie);
    }
    return r;
  }

  var PANEL_NAZVY = { pruhlednost: 'pn_pruhlednost', velikost: 'pn_velikost', barvaPisma: 'pn_barva', okraj: 'pn_okraj',
    poloha: 'pn_poloha', original: 'pn_original', velikostOrig: 'pn_vel_orig', jmena: 'pn_jmena', vstup: 'pn_vstup' };

  function panelHodnota(id) {
    var n = nastaveni;
    if (id === 'pruhlednost') { return n.pruhlednost + ' %'; }
    // velikost jako číslo (stejné jako v telefonu), 0 = podle velikosti titulků ve hře
    if (id === 'velikost') { return n.velikost > 0 ? String(n.velikost) : T.pn_podle_hry; }
    if (id === 'barvaPisma') { return T.pn_barvy[n.barvaPisma] || T.pn_barvy[0]; }
    if (id === 'okraj') { return String(n.okraj); }
    if (id === 'poloha') { return POLOHY.indexOf(n.poloha) >= 0 ? T.pn_polohy[POLOHY.indexOf(n.poloha)] : T.pn_vlastni; }
    if (id === 'original') { return n.original ? T.pn_ano : T.pn_ne; }
    if (id === 'velikostOrig') { return n.velikostOrig > 0 ? String(n.velikostOrig) : T.pn_auto; }
    if (id === 'jmena') { return n.jmena ? T.pn_ano : T.pn_ne; }
    return 'HDMI ' + (prijem ? prijem.hdmi : '?');
  }

  function panelKresli() {
    var p = el('panel'), i, radek, popis, hodnota, radky;
    if (panelRadek < 0 || !nastaveni) { p.style.display = 'none'; return; }
    radky = panelRadky();
    if (panelRadek >= radky.length) { panelRadek = radky.length - 1; }
    p.textContent = '';
    radek = document.createElement('div');
    radek.className = 'pn-nadpis';
    radek.textContent = T.pn_nadpis;
    p.appendChild(radek);
    for (i = 0; i < radky.length; i++) {
      radek = document.createElement('div');
      radek.className = 'pn-radek' + (i === panelRadek ? ' vybrane' : '');
      popis = document.createElement('span');
      popis.className = 'pn-popis';
      popis.textContent = T[PANEL_NAZVY[radky[i]]];
      hodnota = document.createElement('span');
      hodnota.className = 'pn-hodnota';
      hodnota.textContent = '◀ ' + panelHodnota(radky[i]) + ' ▶';
      radek.appendChild(popis);
      radek.appendChild(hodnota);
      p.appendChild(radek);
    }
    // bez spojení s Androidem se změny uloží v televizi a pošlou po připojení – hlásí se jen, když se uložit nepovedlo (1.5.18)
    if (neulozeno) {
      radek = document.createElement('div');
      radek.className = 'pn-offline';
      radek.textContent = T.pn_offline;
      p.appendChild(radek);
    }
    radek = document.createElement('div');
    radek.className = 'pn-ovladani';
    radek.textContent = T.pn_ovladani;
    p.appendChild(radek);
    p.style.display = 'block';
    kafeKresli();
    // náhled: skutečný titulek, nebo ukázková věta, když zrovna žádný není
    if (!posledni || ukazkaVidet || el('pruh').style.display === 'none' ||
        (nastaveni.original && !(posledni.vzhled && posledni.vzhled.original))) {
      ukazkaVidet = true;
      pruhCasti(ukazkaCasti(), T.pn_ukazka, nahled(true));
    } else if (posledni) {
      pruhCasti(castiNahledu(posledni.casti), posledni.text, nahled(false));
    }
  }

  // ---- Podpořit ReCaption (od 1.5.39, přání majitele) -------------------
  // Čtvereček s odkazem na dobrovolný příspěvek: na úvodní obrazovce vpravo dole a vlevo od nabídky OK.
  // Obrázek posílá Android (zpráva „podpora“) – bez spojení s Androidem se neukáže nic.
  var kafeObrazek = null;
  function kafeKresli() {
    var k = el('kafe'), img, text, pod;
    var kde = !kafeObrazek ? '' : (strana === 'domu' ? 'domu' : (strana === 'hra' && panelRadek >= 0 ? 'panel' : ''));
    if (!kde) { k.style.display = 'none'; return; }
    if (k.getAttribute('data-obrazek') !== kafeObrazek || k.getAttribute('data-jazyk') !== jazyk) {
      k.textContent = '';
      img = document.createElement('img');
      img.src = kafeObrazek;
      text = document.createElement('div');
      text.className = 'kafe-text';
      text.textContent = T.kafe;
      pod = document.createElement('div');
      pod.className = 'kafe-pod';
      pod.textContent = T.kafe_pod;
      k.appendChild(img);
      k.appendChild(text);
      k.appendChild(pod);
      k.setAttribute('data-obrazek', kafeObrazek);
      k.setAttribute('data-jazyk', jazyk);
    }
    k.className = 'kafe-' + kde;
    k.style.display = 'block';
  }

  function panelOtevri() {
    if (!nastaveni || strana !== 'hra') { return; }
    panelRadek = 0;
    zapis('nabídka vzhledu otevřena');
    panelKresli();
    stavKresli(true);
  }

  function panelZavri() {
    if (panelRadek < 0) { return; }
    panelRadek = -1;
    el('panel').style.display = 'none';
    kafeKresli();
    if (ukazkaVidet) { ukazkaVidet = false; pruh(null); }
    // stav zmizí s nabídkou; oranžový (něco chybí) zůstane
    if (stavPodpis.indexOf('pozor') < 0) { stavSkryj(); }
  }

  function dalsi(seznam, hodnota, krok) {
    var i = seznam.indexOf(hodnota);
    if (i < 0) { return seznam[0]; }
    return seznam[(i + krok + seznam.length) % seznam.length];
  }

  function panelZmen(krok) {
    var n = nastaveni, klic, hodnota, poslano = false, id;
    if (!n) { return; }
    id = panelRadky()[panelRadek];
    if (id === 'vstup') {
      // vstup HDMI (od 1.5.17): přepne se hned a zapamatuje; do Androidu nic nejde
      if (prijem) { zmenVstup(dalsi([1, 2, 3, 4], prijem.hdmi, krok)); }
      panelKresli();
      return;
    }
    if (id === 'pruhlednost') { n.pruhlednost = dalsi(PRUHLEDNOSTI, n.pruhlednost, krok); hodnota = n.pruhlednost; }
    else if (id === 'velikost') { n.velikost = dalsi(VELIKOSTI, n.velikost, krok); hodnota = n.velikost; }
    else if (id === 'barvaPisma') { n.barvaPisma = dalsi([0, 1, 2, 3], n.barvaPisma, krok); hodnota = n.barvaPisma; }
    else if (id === 'okraj') { n.okraj = dalsi(OKRAJE, n.okraj, krok); hodnota = n.okraj; }
    else if (id === 'poloha') { n.poloha = dalsi(POLOHY, n.poloha, krok); hodnota = n.poloha; }
    else if (id === 'velikostOrig') { n.velikostOrig = dalsi(VELIKOSTI, n.velikostOrig, krok); hodnota = n.velikostOrig; }
    else if (id === 'jmena') { n.jmena = !n.jmena; hodnota = n.jmena; }
    else { n.original = !n.original; id = 'original'; hodnota = n.original; }
    klic = id;
    if (spojeni && zAndroidu) {
      try { spojeni.send(JSON.stringify({ typ: 'zmen', klic: klic, hodnota: hodnota })); poslano = true; } catch (e) { zapis('změnu se nepodařilo poslat'); }
    }
    // bez spojení (nebo než Android pošle své volby) změna počká a odejde po připojení
    if (poslano) { delete cekajici[klic]; } else { cekajici[klic] = hodnota; }
    ulozVzhled();
    zapis('změna vzhledu: ' + klic + (poslano ? '' : ' (uložena, odešle se po připojení)'));
    panelKresli();
  }

  // Přepne vstup HDMI při hře (nabídka OK) a zapamatuje ho pro příští spuštění.
  function zmenVstup(cislo) {
    if (!prijem || prijem.hdmi === cislo) { return; }
    prijem.hdmi = cislo;
    pamet('hdmi', String(cislo));
    oznac(cislo - 1);
    stav.vstupChyba = false;
    zapis('vstup změněn: HDMI ' + cislo);
    if (!prijem.bezVstupu) {
      el('obraz').innerHTML = '';
      ukazVstup(cislo);
    }
    stavKresli(true);
  }

  // Klávesy při hře: OK otevře nabídku; v ní šipky a Zpět. Vrací true, když klávesu vyřídila.
  function panelKlavesa(klavesa, zpet) {
    if (panelRadek < 0) {
      if (klavesa === 13 && nastaveni) { panelOtevri(); return true; }
      return false;
    }
    if (zpet) { panelZavri(); }
    else if (klavesa === 38) { panelRadek = (panelRadek + panelRadky().length - 1) % panelRadky().length; panelKresli(); }
    else if (klavesa === 40) { panelRadek = (panelRadek + 1) % panelRadky().length; panelKresli(); }
    else if (klavesa === 37) { panelZmen(-1); }
    else if (klavesa === 39 || klavesa === 13) { panelZmen(1); }
    return true;
  }

  function ukazZpet(ano) {
    if (zpetCasovac !== null) { clearTimeout(zpetCasovac); zpetCasovac = null; }
    el('zpet').style.display = ano ? 'block' : 'none';
  }

  function oznac(index) {
    var i;
    vybrane = Math.max(0, Math.min(tlacitka.length - 1, index));
    for (i = 0; i < tlacitka.length; i++) {
      tlacitka[i].className = tlacitka[i].className.replace(/ ?vybrane/, '') + (i === vybrane ? ' vybrane' : '');
    }
  }

  // Uklidí vše, co po sobě nechala hra, ukázka nebo nápověda, a ukáže danou obrazovku.
  function prejdi(kam) {
    var i;
    for (i = 0; i < casovace.length; i++) {
      clearTimeout(casovace[i]);
      clearInterval(casovace[i]);
    }
    casovace = [];
    // spojení s Androidem a jeho stav zůstávají (od 1.5.17), mění se jen obrazovka
    prijem = null;
    stav.vstupChyba = false;
    stavPodpis = '';
    stavSkryj();
    panelZavri();
    pruh(null);
    ukolSkryj();
    popisSkryj();
    rohSkryj();
    predmetySkryj();
    menuPolozkySkryj();
    telefonSkryj();
    P.skryjVstup();
    el('obraz').innerHTML = '';
    strana = kam;
    zapis('obrazovka: ' + kam);
    // i na <html>: Samsung kreslí obraz ze vstupu pod celou stránku, takže musí být průhledná celá
    document.documentElement.className = 's-' + kam;
    document.body.className = 's-' + kam;
    ukazZpet(kam === 'ukazka' || kam === 'napoveda');
    if (kam === 'napoveda') { napOznac(1); }
    if (kam === 'ukazka') { el('zpet').className = 'tl vybrane'; }
    domuStavKresli();
    kafeKresli();
  }

  // Nápověda (od 1.5.17): dvě tlačítka vpravo nahoře – Ukázka (0) a Zpět (1), výchozí Zpět.
  var napVybrane = 1;
  function napOznac(i) {
    napVybrane = i;
    el('np-ukazka').className = 'tl' + (i === 0 ? ' vybrane' : '');
    el('zpet').className = 'tl' + (i === 1 ? ' vybrane' : '');
  }

  function spustUkazku() {
    var poradi = 0;
    function ukaz() {
      var u = UKAZKA[poradi];
      el('original').textContent = u[0];
      pruhCasti(u[2], u[1]);
      // anglický titulek nad pruhem, i když je pruh vyšší (víc řádků)
      el('original').style.bottom = Math.max(190, el('pruh').offsetHeight + 40) + 'px';
      poradi = (poradi + 1) % UKAZKA.length;
    }
    prejdi('ukazka');
    ukaz();
    casovace.push(setInterval(ukaz, 3500));
  }

  // ---- Obraz ze vstupu HDMI přímo v aplikaci ---------------------------

  function ukazVstup(cislo) {
    P.ukazVstup(cislo, el('obraz'), function () {
      zapis('vstup HDMI ' + cislo + ' se nepodařilo otevřít');
      // jen když je to pořád zvolený vstup (po přepnutí v nabídce se starý neozve)
      if (strana === 'hra' && prijem && prijem.hdmi === cislo) { stav.vstupChyba = true; stavKresli(true); }
    });
  }

  // Hra na vstupu HDMI. Vstup se zapamatuje: příští spuštění jde rovnou sem (od 1.5.17).
  // adresa: pevná adresa ReCaptionu z parametrů spuštění (jen zkoušky).
  // bezVstupu: jen pro snímek obrazovky v počítači, kde žádný vstup HDMI není (nic se nezapamatuje).
  function spustHru(cislo, adresa, bezVstupu) {
    prejdi('hra');
    prijem = { hdmi: cislo, bezVstupu: !!bezVstupu };
    if (!bezVstupu) { pamet('hdmi', String(cislo)); }
    oznac(cislo - 1);
    zapis('hra: HDMI ' + cislo + (adresa ? ', pevná adresa ' + adresa : ''));
    if (adresa && adresa !== pevna) { nastavPevnou(adresa); }
    stavKresli(true);
    if (!bezVstupu) { ukazVstup(cislo); }
    if (posledniUkol) { try { ukolUkaz(posledniUkol); } catch (e) { ukolSkryj(); } }
  }

  // ---- Titulky po síti ------------------------------------------------

  function poVykresleni(fn) {
    if (window.requestAnimationFrame) { window.requestAnimationFrame(fn); } else { setTimeout(fn, 0); }
  }

  function zavri(ws) {
    if (!ws) { return; }
    try { ws.onopen = ws.onmessage = ws.onerror = ws.onclose = null; ws.close(); } catch (e) { /* už zavřené */ }
  }

  // Zkusí jednu adresu. Když se do `ms` ozve ReCaption, vrátí otevřené spojení, jinak null.
  function zkus(adresa, ms, hotovo) {
    var ws = null;
    var vyrizeno = false;
    function konec(ok) {
      if (vyrizeno) { return; }
      vyrizeno = true;
      if (!ok) { zavri(ws); }
      hotovo(ok ? ws : null);
    }
    try { ws = new WebSocket(adresa); } catch (e) { konec(false); return; }
    ws.onmessage = function (udalost) {
      var z = null;
      try { z = JSON.parse(udalost.data); } catch (e) { /* není to ReCaption */ }
      konec(!!z && z.typ === 'vitej');
    };
    ws.onerror = function () { konec(false); };
    ws.onclose = function () { konec(false); };
    setTimeout(function () { konec(false); }, ms);
  }

  // Nalezené spojení. Titulky, úkol a popis menu se ukazují jen při hře; na ostatních obrazovkách
  // se jen potvrdí (hotovo), ať Android nečeká.
  function prevezmi(ws, adresa) {
    spojeni = ws;
    zAndroidu = false;
    zapis('připojeno k ' + adresa);
    if (!pevna) { pamet('titulky', adresa); }
    if (strana === 'hra') {
      if (panelRadek < 0) { pruh(null); }
      ukolSkryj();
      popisSkryj();
      rohSkryj();
      predmetySkryj();
      menuPolozkySkryj();
      telefonSkryj();
    }
    posledni = null;
    posledniUkol = null;
    stav.bezSite = false;
    if (panelRadek >= 0) { panelKresli(); }
    stavKresli(true);
    ws.onerror = null;
    ws.onmessage = function (udalost) {
      var z;
      try { z = JSON.parse(udalost.data); } catch (e) { return; }
      if (z.typ === 'nastaveni') {
        // volby vzhledu z Androidu (po připojení a po každé změně)
        nastaveni = prectiNastaveni(z);
        zAndroidu = true;
        // změny udělané v televizi bez spojení teď odejdou (od 1.5.17); Android pak pošle nové volby
        posliCekajici();
        ulozVzhled();
        if (panelRadek >= 0) { panelKresli(); }
        stavKresli(false);
        // zkušební volby proti spořiči (od 1.5.60) – jednorázově, viz blokujSporic
        blokujSporic({ luna: z.sporicLuna !== false, video: z.sporicVideo === 2 ? 2 : (z.sporicVideo === 1 ? 1 : 0) });
        return;
      }
      if (z.typ === 'podpora') {
        // čtvereček „Podpořit ReCaption“ (od 1.5.39) – jen z Androidu, sama televize ho nemá
        kafeObrazek = typeof z.obrazek === 'string' && z.obrazek.indexOf('data:image/png;base64,') === 0 ? z.obrazek : null;
        kafeKresli();
        return;
      }
      if (z.typ === 'stav') {
        // stav z Androidu (konzole, překlad, hra) – body s tečkou jako na Domů
        stav.android = prectiStav(z);
        stavKresli(false);
        return;
      }
      if (z.typ === 'ukol') {
        // úkol ve hře (od 1.5.11) – vlastní prvek, pruhu se netýká; bez odpovědi hotovo
        posledniUkol = /\S/.test(String(z.text || '')) ? z : null;
        if (strana !== 'hra') { return; }
        try { ukolUkaz(z); } catch (e) { zapis('úkol se nepodařilo ukázat'); ukolSkryj(); }
        return;
      }
      if (z.typ === 'menupopis') {
        // popis volby Nastavení hry (od 1.5.13) – vlastní prvek, pruhu se netýká; bez odpovědi hotovo
        if (strana !== 'hra') { return; }
        try { popisUkaz(z); } catch (e) { zapis('popis menu se nepodařilo ukázat'); popisSkryj(); }
        return;
      }
      if (z.typ === 'roh') {
        // menu vpravo dole (od 1.5.19) – vlastní prvek, pruhu ani úkolu se netýká; bez odpovědi hotovo
        if (strana !== 'hra') { return; }
        try { rohUkaz(z); } catch (e) { zapis('menu vpravo dole se nepodařilo ukázat'); rohSkryj(); }
        return;
      }
      if (z.typ === 'menupolozky') {
        // popisky menu hry na svém místě (od 1.5.31) – vlastní prvek; bez odpovědi hotovo
        if (strana !== 'hra') { return; }
        try { menuPolozkyUkaz(z); } catch (e) { zapis('popisky menu se nepodařilo ukázat'); menuPolozkySkryj(); }
        return;
      }
      if (z.typ === 'predmety') {
        // pokyny u předmětů ve hře (od 1.5.20) – vlastní prvek, pruhu ani úkolu se netýká; bez odpovědi hotovo
        if (strana !== 'hra') { return; }
        try { predmetyUkaz(z); } catch (e) { zapis('pokyny u předmětů se nepodařilo ukázat'); predmetySkryj(); }
        return;
      }
      if (z.typ === 'telefon') {
        // telefon ve hře (od 1.5.22) – vlastní prvek, pruhu ani úkolu se netýká; bez odpovědi hotovo
        if (strana !== 'hra') { return; }
        try { telefonUkaz(z); } catch (e) { zapis('telefon se nepodařilo ukázat'); telefonSkryj(); }
        return;
      }
      if ((z.typ === 'ukaz' || z.typ === 'skryj') && strana !== 'hra') {
        // mimo hru (úvodní obrazovka, nápověda, ukázka) se titulek neukazuje, jen potvrdí
        posledni = null;
      } else if (z.typ === 'ukaz') {
        // první titulek: zelený přehled už není potřeba (oranžový zůstane)
        if (stavPodpis.indexOf('pozor') < 0) { stavSkryj(); }
        posledni = { casti: z.casti, text: String(z.text || ''), vzhled: z.vzhled };
        ukazkaVidet = false;
        pruhCasti(z.casti, String(z.text || ''), panelRadek >= 0 ? nahled(false) : z.vzhled);
        oznamTitulek();
        // jen délka a počet řádků, ne text titulku
        zapis('ukaž #' + z.id + ': ' + String(z.text || '').length + ' znaků' +
          (z.casti && z.casti.length ? ', ' + z.casti.length + ' částí' : '') + (z.vzhled ? ', vzhled ' + String(z.vzhled.poloha || '?') + ' ' + String(z.vzhled.pozadi) + ' %' : ''));
      } else if (z.typ === 'skryj') {
        posledni = null;
        if (panelRadek >= 0) { ukazkaVidet = true; pruhCasti(ukazkaCasti(), T.pn_ukazka, nahled(true)); } else { pruh(null); }
        zapis('skryj #' + z.id);
      } else {
        return;
      }
      poVykresleni(function () {
        try { ws.send(JSON.stringify({ typ: 'hotovo', id: z.id })); } catch (e) { /* spojení mezitím spadlo */ }
      });
    };
    ws.onclose = function () {
      if (spojeni !== ws) { return; }
      zapis('spojení se ztratilo, hledám znovu');
      odpojeno();
      sitPozdeji(hledej, 2000);
    };
    // platforma a verze: podle nich průvodce na Androidu pozná, že je aplikace v televizi nainstalovaná;
    // hdmi = zvolený (nebo zapamatovaný) vstup, na úvodní obrazovce před první volbou chybí
    try {
      ws.send(JSON.stringify({ typ: 'pripojeno', televize: udaje.model, system: udaje.system,
        hdmi: prijem ? prijem.hdmi : (pametHdmi() || undefined),
        aplikace: 'ReCaption', platforma: P.platforma, verze: P.verze }));
    } catch (e) { zapis('ohlášení se nepodařilo poslat'); }
    // záznam: co se nasbíralo do teď (i z hledání), dál po dávkách (setInterval při startu)
    posliZaznam(ws);
  }

  // Po ztrátě spojení: volby v nabídce zůstanou (uložené v televizi), titulky z Androidu zmizí.
  function odpojeno() {
    spojeni = null;
    zAndroidu = false;
    stav.android = [];
    posledni = null;
    posledniUkol = null;
    ulozVzhled();
    if (strana === 'hra') {
      // otevřená nabídka zůstane (jde i bez spojení) a ukáže ukázkovou větu
      if (panelRadek >= 0) { ukazkaVidet = true; panelKresli(); } else { pruh(null); }
      ukolSkryj();
      popisSkryj();
      rohSkryj();
      predmetySkryj();
      menuPolozkySkryj();
      telefonSkryj();
    }
    stavKresli(true);
  }

  // Pevná adresa z parametrů spuštění (jen zkoušky): případné jiné spojení se zavře a hledá se znovu.
  function nastavPevnou(adresa) {
    pevna = adresa;
    if (spojeni) {
      var ws = spojeni;
      zavri(ws);
      odpojeno();
    }
    hledej();
  }

  // Spořič LG – zkušebně (přání majitele 24. 11. 2026, tester: video/plátno trik spořič nezastavil napořád).
  // Skutečná cesta je nedokumentované volání LG služby tvpower (popsané vývojáři na blog.illixion.com):
  // appka se přihlásí k odběru upozornění „spořič se chystá spustit“ a pokaždé ho zamítne (ack:false).
  // Zkouší se obě varianty rozhraní (power/ i power2/, liší se podle verze firmwaru) – WebOSServiceBridge
  // je dostupný jen appce nainstalované v TV (ne stránce v obyčejném prohlížeči). Plátno se skrytým <video>
  // (dřívější pokus) zůstává jako záloha, kdyby tohle na dané TV nešlo.
  // Záznam (tester 24. 11. 2026, firmware 04.60.92: spořič naskočil i s touhle službou) – dřív úplně tiché
  // try/catch, takže ze záznamu nešlo poznat, jestli se appka vůbec přihlásila, nebo jestli žádost přišla
  // a odpověď se neposlala včas. Teď SPORIC|přihlášeno/chyba/žádost/odpověď/chyba-odpovědi pro obě služby zvlášť.
  function blokujSporicLuna() {
    if (typeof WebOSServiceBridge !== 'function') { zapis('SPORIC|bez WebOSServiceBridge'); return; }
    function pretrvej(sluzba) {
      try {
        var bridge = new WebOSServiceBridge();
        bridge.onservicecallback = function (zprava) {
          try {
            var m = JSON.parse(zprava);
            if (m && m.state === 'Active' && m.timestamp) {
              zapis('SPORIC|' + sluzba + '|žádost (timestamp=' + m.timestamp + ')');
              var odpoved = new WebOSServiceBridge();
              odpoved.onservicecallback = function (odp) {
                zapis('SPORIC|' + sluzba + '|odpověď: ' + String(odp).substring(0, 120));
              };
              odpoved.call('luna://com.webos.service.tvpower/' + sluzba + '/responseScreenSaverRequest',
                JSON.stringify({ clientName: 'ReCaption', ack: false, timestamp: m.timestamp }));
            }
          } catch (e) { zapis('SPORIC|' + sluzba + '|chyba odpovědi: ' + e); }
        };
        bridge.call('luna://com.webos.service.tvpower/' + sluzba + '/registerScreenSaverRequest',
          JSON.stringify({ subscribe: true, clientName: 'ReCaption' }));
        zapis('SPORIC|' + sluzba + '|přihlášeno k odběru');
      } catch (e) { zapis('SPORIC|' + sluzba + '|chyba přihlášení: ' + e); }
    }
    pretrvej('power');
    pretrvej('power2');
  }

  // Zkušební volby proti spořiči (od 1.5.60, přání majitele: nechat testerům zapnout/vypnout jednotlivě,
  // ať se podle SPORIC|/TICHO| ve hlášeních pozná, co na které TV pomáhá). Volby chodí z telefonu
  // (⚙ → Experimentální → Vypnutí spořiče) ve zprávě nastaveni jako sporicLuna (bool) a sporicVideo
  // (0 vypnuto, 1 nehybné plátno, 2 plátno s měnícím se pixelem) - start jen jednou, při první zprávě
  // nastaveni po připojení (vypnutí za běhu appka v TV neumí, projeví se při příštím spojení).
  var sporicSpusteno = false;
  function blokujSporic(volby) {
    if (sporicSpusteno) { return; }
    sporicSpusteno = true;
    if (!volby || volby.luna !== false) { blokujSporicLuna(); }
    var video = volby && volby.video ? volby.video : 0;
    if (!video) { return; }
    // Tenhle trik je mířený jen na LG (předpoklad „na Samsungu (Chromium 38) captureStream není, neděje se nic“ byl
    // špatný – novější Tizen captureStream má, trik se spustil i tam a bez object-fit na čtvercovém plátně vznikl
    // vycentrovaný černý pruh). Pro Samsung má TV vlastní cestu (Product API, viz tizen/app/platforma.js).
    if (P.platforma !== 'webos') { return; }
    try {
      var platno = document.createElement('canvas');
      platno.width = 2; platno.height = 2;
      var ctx = platno.getContext('2d');
      ctx.fillStyle = '#000'; ctx.fillRect(0, 0, 2, 2);
      if (!platno.captureStream) { zapis('SPORIC|video|bez captureStream'); return; }
      var el = document.getElementById('protiSporici');
      if (!el) { zapis('SPORIC|video|chybí prvek'); return; }
      el.srcObject = platno.captureStream();
      var hraj = function () { el.play(); };
      el.addEventListener('loadedmetadata', hraj);
      hraj();
      zapis('SPORIC|video|spuštěno (' + (video === 2 ? 'měnící se pixel' : 'nehybné') + ')');
      if (video === 2) {
        var tik = 0;
        setInterval(function () {
          tik = (tik + 1) % 2;
          ctx.fillStyle = tik ? '#000001' : '#000000';
          ctx.fillRect(0, 0, 2, 2);
        }, 5000);
      }
    } catch (e) { zapis('SPORIC|video|chyba: ' + e); }
  }

  function hledej() {
    if (!sitBezi || spojeni) { return; }
    var moje = ++hledani;
    function plati() { return sitBezi && !spojeni && moje === hledani; }
    function znovu() { if (plati()) { sitPozdeji(hledej, 5000); } }
    function jedna(adresa, ms, jinak) {
      zkus(adresa, ms, function (ws) {
        if (ws && plati()) { prevezmi(ws, adresa); return; }
        zavri(ws);
        if (plati()) { jinak(); }
      });
    }
    function celaSit() {
      P.adresaSite(function (ip, odpovedela) {
        if (!plati()) { return; }
        var m = /^(\d+\.\d+\.\d+\.)(\d+)$/.exec(ip || '');
        if (!m) {
          zapis('adresa televize neznámá (' + (ip || '-') + (odpovedela ? '' : ', systém neodpověděl') + ')');
          if (odpovedela) { stav.bezSite = true; stavKresli(false); }
          znovu();
          return;
        }
        zapis('hledám v síti ' + m[1] + 'x (televize .' + m[2] + ', port ' + PORT + ')');
        var fronta = [];
        var bezi = 0;
        var nalezeno = false;
        var n;
        for (n = 1; n < 255; n++) {
          if (String(n) !== m[2]) { fronta.push('ws://' + m[1] + n + ':' + PORT); }
        }
        function spust(adresa) {
          bezi++;
          zkus(adresa, 1500, function (ws) {
            bezi--;
            if (ws && !nalezeno && plati()) { nalezeno = true; prevezmi(ws, adresa); return; }
            zavri(ws);
            dalsi();
          });
        }
        function dalsi() {
          if (nalezeno || !plati()) { return; }
          while (bezi < 24 && fronta.length) { spust(fronta.shift()); }
          if (bezi === 0 && !fronta.length) { zapis('v síti nic nenalezeno, zkusím za 5 s'); znovu(); }
        }
        dalsi();
      });
    }
    if (pevna) { zapis('zkouším ' + pevna); jedna(pevna, 3000, znovu); return; }
    var minula = pamet('titulky');
    if (minula) {
      zapis('zkouším minulou adresu ' + minula);
      jedna(minula, 2000, function () { zapis('minulá adresa neodpověděla'); celaSit(); hlidejMinulou(); });
    } else { celaSit(); }
    // Během hledání v celé síti (desítky sekund) se minulá adresa zkouší dál každé 3 s – telefon mívá stejnou adresu
    // a po otevření aplikace se tak TV připojí hned, ne až po projití sítě (od 1.5.18, majitel: „TV zase nepřipojeno“).
    function hlidejMinulou() {
      sitPozdeji(function () {
        if (!plati()) { return; }
        zkus(minula, 1500, function (ws) {
          if (ws && plati()) { prevezmi(ws, minula); return; }
          zavri(ws);
          hlidejMinulou();
        });
      }, 3000);
    }
  }

  // Parametry spuštění (jen pro zkoušky): {"titulky": "ws://adresa:port", "hdmi": 2}.
  // Odkud se berou, ví platforma.js.
  function podleParametru(p) {
    if (typeof p === 'string') {
      try { p = JSON.parse(p || '{}'); } catch (e) { p = {}; }
    }
    if (p && typeof p.titulky === 'string' && /^wss?:\/\//.test(p.titulky)) {
      spustHru(parseInt(p.hdmi, 10) || 1, p.titulky);
    }
  }

  // ---- Ovládání ---------------------------------------------------------

  function potvrd(index) {
    oznac(index);
    var akce = tlacitka[vybrane].getAttribute('data-akce');
    if (akce === 'hdmi') { spustHru(parseInt(tlacitka[vybrane].getAttribute('data-hdmi'), 10)); }
    else if (akce === 'jazyk') {
      // Jazyk aplikace v televizi (česky / anglicky). Jazyk překladu se volí v ReCaptionu na Androidu.
      nastavJazyk(jazyk === 'cs' ? 'en' : 'cs');
      pamet('jazyk', jazyk);
    }
    else if (akce === 'konec') { ukonci(); }
    else { prejdi('napoveda'); }
  }

  // Úplné ukončení (i hledání v síti). Jak, ví platforma.js.
  function ukonci() {
    var i;
    sitBezi = false;
    hledani++;
    for (i = 0; i < sitCasovace.length; i++) { clearTimeout(sitCasovace[i]); }
    sitCasovace = [];
    zavri(spojeni);
    spojeni = null;
    zAndroidu = false;
    prejdi('domu');
    P.ukonci();
  }

  // Domů jsou dvě řady: čtyři vstupy (0–3) a pod nimi (pod stavem) tři menší tlačítka – Nápověda (4),
  // Jazyk (5), Ukončit (6). Šipka nahoru/dolů jde na nejbližší tlačítko druhé řady.
  function sipka(klavesa) {
    var v = vybrane, posledni2 = tlacitka.length - 1, dole = posledni2 - 4;
    if (klavesa === 37) { if (v !== 0 && v !== 4) { oznac(v - 1); } }
    else if (klavesa === 39) { if (v !== 3 && v !== posledni2) { oznac(v + 1); } }
    else if (klavesa === 40) { if (v < 4) { oznac(4 + Math.round(v * dole / 3)); } }
    else if (klavesa === 38) { if (v >= 4) { oznac(Math.round((v - 4) * 3 / dole)); } }
  }

  document.addEventListener('keydown', function (udalost) {
    var klavesa = udalost.keyCode;
    var zpet = P.jeZpet(klavesa);
    if (strana === 'hra' && panelKlavesa(klavesa, zpet)) { udalost.preventDefault(); return; }
    if (strana === 'napoveda' && !zpet) {
      // Nápověda: ◀ ▶ mezi Ukázka a Zpět, OK potvrdí vybrané
      if (klavesa === 37 || klavesa === 39) { napOznac(klavesa === 37 ? 0 : 1); udalost.preventDefault(); }
      else if (klavesa === 13) { if (napVybrane === 0) { spustUkazku(); } else { prejdi('domu'); } udalost.preventDefault(); }
      return;
    }
    if (strana === 'ukazka') {
      // z ukázky zpět do nápovědy, odkud se spouští (od 1.5.17)
      if (zpet || klavesa === 13) { prejdi('napoveda'); udalost.preventDefault(); }
      return;
    }
    if (strana !== 'domu') {
      if (zpet) { prejdi('domu'); udalost.preventDefault(); }
      return;
    }
    if (klavesa >= 37 && klavesa <= 40) { sipka(klavesa); udalost.preventDefault(); }
    else if (klavesa === 13) { potvrd(vybrane); }
    else if (zpet) { ukonci(); udalost.preventDefault(); }
  });

  // Ukazovátko ovladače Magic Remote.
  (function () {
    var i;
    function pripoj(index) {
      tlacitka[index].addEventListener('mouseover', function () { if (strana === 'domu') { oznac(index); } });
      tlacitka[index].addEventListener('click', function (udalost) {
        if (strana !== 'domu') { return; }
        // kliknutí na HDMI spustí hru – nesmí pak doběhnout do kliknutí při hře (otevřelo by nabídku)
        if (udalost && udalost.stopPropagation) { udalost.stopPropagation(); }
        potvrd(index);
      });
    }
    for (i = 0; i < tlacitka.length; i++) { pripoj(i); }
    el('zpet').addEventListener('click', function () { prejdi(strana === 'ukazka' ? 'napoveda' : 'domu'); });
    el('zpet').addEventListener('mouseover', function () { if (strana === 'napoveda') { napOznac(1); } });
    el('np-ukazka').addEventListener('mouseover', function () { if (strana === 'napoveda') { napOznac(0); } });
    el('np-ukazka').addEventListener('click', function () { if (strana === 'napoveda') { spustUkazku(); } });
    // Při hře se tlačítko Zpět neukazuje (majitel: rušilo, svítilo po každém pohybu ukazovátka) – Zpět je
    // na ovladači. Se zapnutým ukazovátkem Magic Remote posílá televize OK jako kliknutí, ne jako klávesu 13:
    // kliknutí při hře proto dělá totéž co OK (otevře nabídku vzhledu, v ní mění vybranou volbu). Od 1.5.6.
    document.addEventListener('click', function () {
      if (strana !== 'hra') { return; }
      panelKlavesa(13, false);
    });
  })();

  // ---- Start --------------------------------------------------------------

  // Značka v adrese je jen pro snímky obrazovky a zkoušení v počítači:
  // index.html#cs, #en, #domu, #ukazka, #napoveda, #hra (jde spojit: #en-ukazka).
  var znacka = String(window.location.hash || '').replace('#', '').split('-');
  var vynuceny = znacka.indexOf('cs') >= 0 ? 'cs' : (znacka.indexOf('en') >= 0 ? 'en' : '');

  // Pořadí: značka v adrese (snímky), jazyk zvolený tlačítkem, jinak jazyk televize.
  var zvoleny = vynuceny || pamet('jazyk');
  zapis('start ReCaption ' + P.platforma + ' ' + P.verze + ' · ' + String(navigator.userAgent || '?').substring(0, 120));
  // nové řádky záznamu jdou do Androidu po dávkách (mimo `casovace`, ty maže každé přepnutí obrazovky)
  setInterval(function () { posliZaznam(spojeni); }, 10000);
  nastavJazyk(zvoleny || navigator.language);
  zapis('jazyk: ' + jazyk + (zvoleny ? ' (zvolený)' : ''));
  nactiVzhled();
  prejdi('domu');
  oznac((pametHdmi() || 1) - 1);
  zjistiTelevizi();
  if (!zvoleny) {
    // Jazyk nabídky televize je spolehlivější než jazyk prohlížeče.
    P.jazykTelevize(function (kod) {
      if (kod && !pamet('jazyk')) { nastavJazyk(kod); }
    });
  }

  // ReCaption na Androidu se hledá hned od startu (stav na úvodní obrazovce, nabídka při hře);
  // volby proti spořiči se spustí až s první zprávou nastaveni (viz blokujSporic výš).
  sitBezi = true;
  hledej();

  // Úvodní obrazovka jen poprvé (od 1.5.17): se zapamatovaným vstupem HDMI se jde rovnou do hry.
  if (znacka.indexOf('ukazka') >= 0) { spustUkazku(); }
  else if (znacka.indexOf('napoveda') >= 0) { prejdi('napoveda'); }
  else if (znacka.indexOf('hra') >= 0) { spustHru(1, null, true); }
  else if (znacka.indexOf('domu') < 0 && pametHdmi()) { spustHru(pametHdmi()); }

  P.parametry(podleParametru);
})();
