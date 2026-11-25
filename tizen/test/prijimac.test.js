/*
 * Zkouška aplikace pro televizi bez televize: `node tizen/test/prijimac.test.js`
 *
 * Pustí společný app.js s platforma.js pro Samsung (tizen/app) i pro LG (webos/obchod/app)
 * v napodobeném prohlížeči (bez knihoven) a ověří: televize najde ReCaption v síti, ohlásí se
 * platformou a verzí (zpráva „pripojeno“), ukáže obyčejný text i řádky `casti`, použije vzhled pruhu
 * z Androidu (pole `vzhled`) a odpoví „hotovo“,
 * pošle svůj záznam událostí (zpráva „zaznam“, bez textu titulků) a že nabídka Vzhled titulků
 * (OK při hře) posílá žádosti „zmen“, a že úkol ve hře (zpráva „ukol“, od 1.5.11) má vlastní prvek,
 * který skrytí pruhu neschová.
 * Od 1.5.17: zapamatovaný vstup HDMI = start rovnou do hry, nabídka OK jde i bez spojení (změny se uloží
 * a odejdou jako „zmen“ až po „nastaveni“ z Androidu), řádek Vstup v nabídce přepne HDMI, stav na úvodní obrazovce.
 * Od 1.5.18: pod řádkem „ReCaption · hledám v síti…“ slabý běžící pruh, se spojením ne.
 * Od 1.5.19: menu vpravo dole (zpráva „roh“) ve vlastním prvku #roh, položka na řádek zarovnaná doprava;
 * od 1.5.25 za textem ikona tlačítka (pole „ikona“) poskládaná z prvků, bez znaků Unicode.
 * Od 1.5.20: pokyny u předmětů ve hře (zpráva „predmety“) ve vlastním prvku #predmety, od levého horního rohu misto.
 * Od 1.5.22: telefon ve hře (zpráva „telefon“) ve vlastním prvku #telefon – nadpis a bubliny podle strany (l / p / s); od 1.5.23 čára „c“ místo obrázku, od 1.5.24 obrázek ze hry.
 * Hebrejština (příprava jazyků): řádek s hebrejštinou má dir="rtl" a jméno v <bdi>, čeština atribut nemá.
 * Na Samsungu navíc, že se obraz ze vstupu HDMI pustí přes tizen.tvwindow pod stránku.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var vm = require('vm');
var assert = require('assert');

var ROOT = path.join(__dirname, '..', '..');

function prvek(id) {
  var e = {
    id: id, style: {}, className: '', textContent: '', innerHTML: '', children: [], posluchaci: {},
    offsetHeight: 150,
    getAttribute: function (k) { return (e.atributy || {})[k] || null; },
    setAttribute: function (k, v) { e.atributy = e.atributy || {}; e.atributy[k] = v; },
    appendChild: function (c) { e.children.push(c); return c; },
    addEventListener: function (n, f) { (e.posluchaci[n] = e.posluchaci[n] || []).push(f); }
  };
  Object.defineProperty(e, 'textContent', {
    get: function () { return e._text !== undefined ? e._text : e.children.map(function (c) { return c.textContent; }).join(''); },
    set: function (v) { e._text = v; if (v === '') { e._text = undefined; e.children = []; } }
  });
  return e;
}

// moznosti: { pamet: {klíč: hodnota} – localStorage na začátku, android: { vypnuty: true } – ReCaption neodpovídá }
function spust(platforma, moznosti) {
  moznosti = moznosti || {};
  var android = moznosti.android || {};
  var prvky = {};
  var tlacitka = [1, 2, 3, 4].map(function (n) { var t = prvek('hdmi' + n); t.setAttribute('data-akce', 'hdmi'); t.setAttribute('data-hdmi', String(n)); return t; })
    .concat(['napoveda', 'jazyk', 'konec'].map(function (a) { var t = prvek(a); t.setAttribute('data-akce', a); return t; }));
  var klavesy = [];
  var odeslano = [];
  var spojeni = [];
  var tvwindow = [];
  var casovace = [];
  var document = {
    documentElement: prvek('html'),
    body: prvek('body'),
    hidden: false,
    getElementById: function (id) { return prvky[id] || (prvky[id] = prvek(id)); },
    querySelectorAll: function (s) { return s === '#domu .tl' ? tlacitka : []; },
    createElement: function (t) { return prvek(t); },
    createTextNode: function (t) { var e = prvek('#text'); e.textContent = t; return e; },
    addEventListener: function (n, f) { if (n === 'keydown') { klavesy.push(f); } }
  };
  function WebSocket(adresa) {
    var ws = this;
    ws.adresa = adresa;
    ws.send = function (z) { odeslano.push(JSON.parse(z)); };
    ws.close = function () {};
    spojeni.push(ws);
    setTimeout(function () {
      if (adresa === 'ws://192.168.1.20:8787' && !android.vypnuty) { ws.onmessage && ws.onmessage({ data: '{"typ":"vitej"}' }); }
      else if (ws.onerror) { ws.onerror(); }
    }, 1);
  }
  var okno = {
    WebSocket: WebSocket,
    location: { hash: '' },
    localStorage: { data: moznosti.pamet || {}, getItem: function (k) { return this.data[k] || null; }, setItem: function (k, v) { this.data[k] = String(v); } },
    setTimeout: function (f, ms) { var t = setTimeout(f, Math.min(ms, 20)); casovace.push(t); return t; },
    clearTimeout: clearTimeout, setInterval: function () { return 0; }, clearInterval: function () {},
    requestAnimationFrame: function (f) { setTimeout(f, 0); },
    close: function () {}
  };
  if (platforma === 'tizen') {
    okno.tizen = {
      systeminfo: {
        getPropertyValue: function (n, ok) {
          var v = {
            BUILD: { model: 'UE55TU7172', buildVersion: 'T-NKLAKUC' },
            LOCALE: { language: 'cs_CZ' },
            ETHERNET_NETWORK: { ipAddress: '' },
            WIFI_NETWORK: { ipAddress: '192.168.1.10' },
            VIDEOSOURCE: { connected: [{ type: 'TV', number: 1 }, { type: 'HDMI', number: 1 }, { type: 'HDMI', number: 2 }] }
          }[n];
          setTimeout(function () { ok(v); }, 1);
        },
        getCapability: function () { return '5.5'; }
      },
      tvwindow: {
        setSource: function (zdroj, ok) { tvwindow.push('setSource HDMI' + zdroj.number); setTimeout(ok, 1); },
        show: function (ok, chyba, obdelnik, okno, vrstva) { tvwindow.push('show ' + vrstva); ok(); },
        hide: function (ok) { tvwindow.push('hide'); ok(); }
      },
      application: { getCurrentApplication: function () { return { exit: function () {}, getRequestedAppControl: function () { return null; } }; } }
    };
  } else {
    // LG: služby televize přes PalmServiceBridge
    okno.PalmServiceBridge = function () {
      var most = this;
      most.call = function (adresa) {
        var o = /getStatus/i.test(adresa) ? { returnValue: true, wifi: { ipAddress: '192.168.1.10' } }
          : /getSystemInfo/.test(adresa) ? { returnValue: true, modelName: 'OLED55B7', sdkVersion: '3.4', firmwareVersion: '05.00' }
          : { returnValue: true, settings: { localeInfo: { locales: { UI: 'en-GB' } } } };
        setTimeout(function () { most.onservicecallback(JSON.stringify(o)); }, 1);
      };
    };
  }
  okno.window = okno;
  okno.document = document;
  okno.navigator = { language: 'en-US' };
  var kontext = vm.createContext(okno);
  var slozka = platforma === 'tizen' ? path.join(ROOT, 'tizen', 'app') : path.join(ROOT, 'webos', 'obchod', 'app');
  ['platforma.js', 'app.js'].forEach(function (f) {
    vm.runInContext(fs.readFileSync(path.join(slozka, f), 'utf8'), kontext, { filename: f });
  });
  return {
    prvky: prvky, odeslano: odeslano, tvwindow: tvwindow, document: document, spojeni: spojeni, pamet: okno.localStorage.data, ls: okno.localStorage,
    stiskni: function (kod) { klavesy.forEach(function (f) { f({ keyCode: kod, preventDefault: function () {} }); }); }
  };
}

function pockej(podminka, hotovo, popis) {
  var konec = Date.now() + 5000;
  (function znovu() {
    if (podminka()) { hotovo(); return; }
    if (Date.now() > konec) { throw new Error('nedočkal jsem se: ' + popis); }
    setTimeout(znovu, 5);
  })();
}

function zkus(platforma, verze, dalsi) {
  var tv = spust(platforma);
  // úvodní obrazovka (vstup ještě není zapamatovaný), HDMI 1 je vybrané – OK spustí hru
  assert.strictEqual(tv.document.body.className, 's-domu');
  setTimeout(function () {
    // stav na úvodní obrazovce (od 1.5.17): ReCaption se hledá hned od startu
    var domuStav = tv.prvky['domu-stav'].children;
    assert.ok(domuStav.length >= 1, 'na úvodní obrazovce je stav');
    assert.ok(/^Titulky PS5 · /.test(domuStav[0].children[1].textContent), 'stav: Titulky PS5 · …');
    tv.stiskni(13);
    assert.strictEqual(tv.document.documentElement.className, 's-hra', 'při hře je průhledná celá stránka');
    assert.strictEqual(tv.pamet.hdmi, '1', 'vstup se zapamatuje');
    // spojení mohlo vzniknout už na úvodní obrazovce – počkat i na obraz ze vstupu (Samsung asynchronně)
    pockej(function () { return tv.odeslano.length > 0 && (platforma !== 'tizen' || tv.tvwindow[tv.tvwindow.length - 1] === 'show BEHIND'); }, function () {
      var p = tv.odeslano[0];
      assert.strictEqual(p.typ, 'pripojeno');
      assert.strictEqual(p.platforma, platforma);
      assert.strictEqual(p.verze, verze);
      // spojení mohlo vzniknout už na úvodní obrazovce (vstup ještě nebyl zvolený)
      assert.ok(p.hdmi === undefined || p.hdmi === 1, 'hdmi v ohlášení');
      if (platforma === 'tizen') {
        assert.deepStrictEqual(tv.tvwindow.slice(-2), ['setSource HDMI1', 'show BEHIND']);
        assert.strictEqual(p.televize, 'UE55TU7172');
      } else {
        assert.strictEqual(tv.prvky.obraz.children[0].children[0].atributy.src, 'ext://hdmi:1');
        assert.strictEqual(p.televize, 'OLED55B7');
      }
      // zprávy z Androidu: obyčejný text, pak řádky casti (jméno v barvě, volby)
      var ws = tv.spojeni.filter(function (w) { return w.adresa === 'ws://192.168.1.20:8787'; }).pop();
      // s polem vzhled (od ReCaptionu 0.86): průhlednost, písmo a pruh nad anglickými titulky
      ws.onmessage({ data: JSON.stringify({ typ: 'ukaz', id: 1, text: 'Počkej tady.', vzhled: {
        pozadi: 40, pismo: 44, barva: '#FFE066', poloha: 'nad', sirka: 0.84, cteni: [0.74, 0.98], text: [0.85, 0.9, 0.3, 0.7] } }) });
      assert.strictEqual(tv.prvky.titulek.textContent, 'Počkej tady.');
      assert.strictEqual(tv.prvky.pruh.style.display, 'flex');
      assert.strictEqual(tv.prvky.pruh.style.background, 'rgba(0,0,0,0.4)');
      assert.strictEqual(tv.prvky.titulek.style.fontSize, '44px');
      assert.strictEqual(tv.prvky.titulek.style.color, '#FFE066');
      // výška pruhu 150 px nad oblast čtení (0,74 × 1080 = 799) → nahoře 643 px, ne dole
      assert.strictEqual(tv.prvky.pruh.style.top, Math.round((0.74 - 0.006 - 150 / 1080) * 1080) + 'px');
      assert.strictEqual(tv.prvky.pruh.style.bottom, 'auto');
      // „tesne“: pruh těsně nad anglický titulek (0,85 × 1080), ne nad celou oblast čtení
      ws.onmessage({ data: JSON.stringify({ typ: 'ukaz', id: 9, text: 'Jo.', vzhled: { pozadi: 100, pismo: 50, poloha: 'tesne', cteni: [0.74, 0.98], text: [0.85, 0.9] } }) });
      assert.strictEqual(tv.prvky.pruh.style.top, Math.round((0.85 - 0.006 - 150 / 1080) * 1080) + 'px');
      ws.onmessage({ data: JSON.stringify({ typ: 'ukaz', id: 2, text: 'Mira: Ahoj\n• Ano', casti: [
        { jmeno: 'Mira', barva: '#F2C14E', text: 'Ahoj' }, { volba: true, text: 'Ano' }, { jmeno: 'X', barva: 'red;x', text: 'B' }] }) });
      var radky = tv.prvky.titulek.children;
      assert.strictEqual(radky.length, 3);
      assert.strictEqual(radky[0].children[0].style.color, '#F2C14E');
      assert.strictEqual(radky[1].className, 'volby');
      assert.strictEqual(radky[2].children[0].style.color, undefined, 'cizí barva se do stylu nedostane');
      assert.strictEqual(radky[0].dir, undefined, 'čeština bez atributu dir – vypadá jako dřív');
      assert.strictEqual(radky[0].children[0].textContent, 'Mira: ');
      // hebrejština: řádek zprava doleva (i když začíná latinkou jména), jméno izolované v <bdi>
      ws.onmessage({ data: JSON.stringify({ typ: 'ukaz', id: 21, text: 'LUCIA: שלום!\nOK', casti: [
        { jmeno: 'LUCIA', barva: '#F2C14E', text: 'שלום!' }, { text: 'GTA זה משחק.' }, { text: 'Hello.' }] }) });
      radky = tv.prvky.titulek.children;
      assert.strictEqual(radky[0].dir, 'rtl', 'hebrejský řádek zprava doleva');
      assert.strictEqual(radky[0].children[0].children[0].id, 'bdi', 'jméno v <bdi>');
      assert.strictEqual(radky[0].children[0].textContent, 'LUCIA: ');
      assert.strictEqual(radky[1].dir, 'rtl', 'řádek začínající latinkou s hebrejštinou je zprava doleva');
      assert.strictEqual(radky[2].dir, undefined, 'anglický řádek beze změny');
      // starší tvar bez casti: řádky zvlášť, každý se svým směrem
      ws.onmessage({ data: JSON.stringify({ typ: 'ukaz', id: 22, text: 'שלום!\nHello!' }) });
      radky = tv.prvky.titulek.children;
      assert.deepStrictEqual(radky.map(function (r) { return [r.textContent, r.dir]; }), [['שלום!', 'rtl'], ['Hello!', undefined]]);
      ws.onmessage({ data: JSON.stringify({ typ: 'ukaz', id: 23, text: 'Ahoj.' }) });
      assert.strictEqual(tv.prvky.titulek.textContent, 'Ahoj.');
      ws.onmessage({ data: JSON.stringify({ typ: 'ukaz', id: 2, text: 'Mira: Ahoj\n• Ano', casti: [
        { jmeno: 'Mira', barva: '#F2C14E', text: 'Ahoj' }, { volba: true, text: 'Ano' }, { jmeno: 'X', barva: 'red;x', text: 'B' }] }) });
      // bez pole vzhled (starší ReCaption) je pruh zase výchozí: černý a dole
      assert.strictEqual(tv.prvky.pruh.style.background, '');
      assert.strictEqual(tv.prvky.pruh.style.top, '');
      // úkol ve hře (od 1.5.11): vlastní prvek #ukol uprostřed obdélníku anglického úkolu, na pruhu nezávislý
      ws.onmessage({ data: JSON.stringify({ typ: 'ukol', text: 'Jdi do úkrytu.', misto: [0.3, 0.9, 0.7, 0.95], pismo: 40, pozadi: 90, barva: '#FFFFFF' }) });
      var ukol = tv.prvky.ukol;
      assert.strictEqual(ukol.style.display, 'block', 'úkol je vidět');
      assert.strictEqual(ukol.textContent, 'Jdi do úkrytu.');
      assert.strictEqual(ukol.dir, undefined, 'čeština bez dir');
      // hebrejský úkol zprava doleva, další český zase bez směru (prvek se používá znovu)
      ws.onmessage({ data: JSON.stringify({ typ: 'ukol', text: 'סע ליאכטה', misto: [0.3, 0.9, 0.7, 0.95], pismo: 40 }) });
      assert.strictEqual(ukol.dir, 'rtl');
      ws.onmessage({ data: JSON.stringify({ typ: 'ukol', text: 'Jdi do úkrytu.', misto: [0.3, 0.9, 0.7, 0.95], pismo: 40, pozadi: 90, barva: '#FFFFFF' }) });
      assert.strictEqual(ukol.dir, '', 'po hebrejštině směr zrušen');
      assert.strictEqual(ukol.style.fontSize, '40px');
      assert.strictEqual(ukol.style.background, 'rgba(0,0,0,0.9)');
      assert.strictEqual(ukol.style.color, '#FFFFFF');
      // napodobený prohlížeč neměří šířku → 90 % obrazovky (1728 px) se středem na 0,5; výška 150 px se středem na 0,925
      assert.strictEqual(ukol.style.left, Math.round(0.5 * 1920 - 1728 / 2) + 'px');
      assert.strictEqual(ukol.style.top, Math.round(0.925 * 1080 - 75) + 'px');
      assert.strictEqual(tv.prvky.titulek.textContent.indexOf('úkrytu'), -1, 'úkol nejde do pruhu');
      // popis volby Nastavení hry (od 1.5.13): vlastní prvek #menupopis od levého horního rohu misto, šířka misto
      var hotovoPred = tv.odeslano.length;
      ws.onmessage({ data: JSON.stringify({ typ: 'menupopis', nadpis: 'Citlivost', text: 'Rychlost kamery.', misto: [0.6, 0.18, 0.98, 0.32], pismo: 30, pozadi: 90, barva: '#FFFFFF' }) });
      var popis = tv.prvky.menupopis;
      assert.strictEqual(popis.style.display, 'block', 'popis je vidět');
      assert.strictEqual(popis.textContent, 'Citlivost\nRychlost kamery.');
      assert.strictEqual(popis.style.left, Math.round(0.6 * 1920) + 'px');
      assert.strictEqual(popis.style.width, Math.round(0.38 * 1920) + 'px');
      assert.strictEqual(popis.style.fontSize, '30px');
      assert.strictEqual(tv.odeslano.length, hotovoPred, 'popis nečeká na hotovo');
      assert.strictEqual(tv.prvky.titulek.textContent.indexOf('kamery'), -1, 'popis nejde do pruhu');
      ws.onmessage({ data: '{"typ":"menupopis","text":""}' });
      assert.strictEqual(popis.style.display, 'none', 'prázdný popis = schovat');
      // od 1.5.25: nadpis zvlášť nad anglickým nadpisem (nadpisMisto), větší písmo; popis bez nadpisu
      ws.onmessage({ data: JSON.stringify({ typ: 'menupopis', nadpis: 'Výška', text: 'Výška kamery při chůzi.', misto: [0.6, 0.3, 0.98, 0.5],
        pismo: 28, pozadi: 90, barva: '#FFFFFF', nadpisMisto: [0.61, 0.11, 0.98, 0.17], pismoNadpis: 40 }) });
      var nadpis = tv.prvky.menunadpis;
      assert.strictEqual(nadpis.style.display, 'block', 'nadpis je vidět');
      assert.strictEqual(nadpis.textContent, 'Výška');
      assert.strictEqual(nadpis.style.fontSize, '40px');
      assert.strictEqual(nadpis.style.left, Math.round(0.61 * 1920) + 'px');
      assert.strictEqual(popis.textContent, 'Výška kamery při chůzi.', 'nadpis není v popisu');
      ws.onmessage({ data: '{"typ":"menupopis","text":""}' });
      assert.strictEqual(nadpis.style.display, 'none', 'schová se i nadpis');
      // menu vpravo dole (od 1.5.19): položka na řádek zarovnaná doprava k misto[2], šedá položka šedě
      ws.onmessage({ data: JSON.stringify({ typ: 'roh', rezim: 'pres', pismo: 30, pozadi: 85, barva: '#FFFFFF', polozky: [
        { text: 'Rychlost 1/2/3', misto: [0.85, 0.89, 0.961, 0.93], seda: false },
        { text: 'Zapnout autopilota', misto: [0.84, 0.94, 0.961, 0.98], seda: true },
        { text: 'mimo', misto: ['x', 1] }] }) });
      var roh = tv.prvky.roh;
      assert.strictEqual(roh.style.display, 'block', 'menu vpravo dole je vidět');
      assert.strictEqual(roh.children.length, 2, 'neplatná položka se vynechá');
      assert.strictEqual(roh.children[0].textContent, 'Rychlost 1/2/3');
      assert.strictEqual(roh.children[0].style.right, (1920 - Math.round(0.961 * 1920)) + 'px');
      assert.strictEqual(roh.children[0].style.top, Math.round(0.89 * 1080) + 'px');
      assert.strictEqual(roh.children[0].style.fontSize, '30px');
      assert.strictEqual(roh.children[0].style.color, '#FFFFFF');
      assert.strictEqual(roh.children[1].style.color, '#8C7C4C');
      assert.strictEqual(roh.children[1].style.background, 'rgba(0,0,0,0.85)');
      assert.strictEqual(tv.prvky.titulek.textContent.indexOf('autopilota'), -1, 'menu vpravo dole nejde do pruhu');
      // ikony tlačítek (od 1.5.25): kotouč se znakem z rámečků, L2/R2 obdélník s textem, neznámá ikona nic
      ws.onmessage({ data: JSON.stringify({ typ: 'roh', rezim: 'nad', pismo: 30, pozadi: 60, barva: '#FFFFFF', polozky: [
        { text: 'Rychlost 1/2/3', misto: [0.98, 0.70, 0.98, 0.75], seda: false, ikona: 'L2 R2' },
        { text: 'Vybrat', misto: [0.98, 0.75, 0.98, 0.80], seda: false, ikona: 'x' },
        { text: 'Zpět', misto: [0.98, 0.80, 0.98, 0.85], seda: true, ikona: 't' },
        { text: 'Konec', misto: [0.98, 0.85, 0.98, 0.90], seda: false, ikona: '<b>' }] }) });
      assert.strictEqual(roh.children.length, 4);
      var ik = roh.children[0].children;
      assert.strictEqual(ik.length, 3, 'text a dvě ikony');
      assert.strictEqual(ik[0].textContent, 'Rychlost 1/2/3');
      assert.strictEqual(ik[1].className, 'roh-tl');
      assert.strictEqual(ik[1].textContent, 'L2');
      assert.strictEqual(ik[2].textContent, 'R2');
      ik = roh.children[1].children;
      assert.strictEqual(ik[1].className, 'roh-ik roh-ik-x');
      assert.strictEqual(ik[1].children.length, 2, '× ze dvou čar');
      assert.strictEqual(roh.children[1].textContent, 'Vybrat', 'kotouč nemá text');
      assert.strictEqual(roh.children[2].children[1].className, 'roh-ik roh-ik-t roh-seda', 'šedá položka = šedý kotouč');
      assert.strictEqual(roh.children[3].children.length, 1, 'neznámá ikona se neukáže');
      assert.strictEqual(roh.children[3].textContent, 'Konec');
      // páčka (od 1.5.42): kotouč s písmenem a čtyřmi šipkami
      ws.onmessage({ data: JSON.stringify({ typ: 'roh', rezim: 'nad', pismo: 30, pozadi: 60, barva: '#FFFFFF', polozky: [
        { text: 'Posunout', misto: [0.98, 0.85, 0.98, 0.90], seda: false, ikona: 'RS' }] }) });
      ik = roh.children[0].children;
      assert.strictEqual(ik[1].className, 'roh-ik roh-ik-pa');
      assert.strictEqual(ik[1].children.length, 5, 'písmeno a čtyři šipky');
      assert.strictEqual(ik[1].textContent, 'R');
      ws.onmessage({ data: '{"typ":"roh","polozky":[]}' });
      assert.strictEqual(roh.style.display, 'none', 'prázdný seznam = schovat');
      // pokyny u předmětů (od 1.5.20): od levého horního rohu misto, jeden řádek, nejvýš do 97 % šířky, nejvýš 3 položky
      ws.onmessage({ data: JSON.stringify({ typ: 'predmety', pismo: 28, pozadi: 70, barva: '#FFFFFF', polozky: [
        { text: '□ ROZBÍT OKNO', misto: [0.42, 0.5, 0.52, 0.53] },
        { text: 'mimo', misto: [0.5, 'x'] },
        { text: new Array(60).join('A'), misto: [0.9, 0.6, 0.95, 0.63] },
        { text: 'B', misto: [0.1, 0.1, 0.2, 0.13] },
        { text: 'C', misto: [0.1, 0.2, 0.2, 0.23] }] }) });
      var predmety = tv.prvky.predmety;
      assert.strictEqual(predmety.style.display, 'block', 'pokyny u předmětů jsou vidět');
      assert.strictEqual(predmety.children.length, 3, 'neplatná položka se vynechá, nejvýš 3');
      var pr0 = predmety.children[0];
      assert.strictEqual(pr0.textContent, '□ ROZBÍT OKNO');
      assert.strictEqual(pr0.style.left, Math.round(0.42 * 1920) + 'px');
      assert.strictEqual(pr0.style.top, Math.round(0.5 * 1080) + 'px');
      assert.strictEqual(pr0.style.lineHeight, Math.round(0.03 * 1080) + 'px');
      assert.strictEqual(pr0.style.minWidth, Math.round(0.1 * 1920) + 'px');
      assert.strictEqual(pr0.style.maxWidth, (Math.round(0.97 * 1920) - Math.round(0.42 * 1920)) + 'px');
      assert.strictEqual(pr0.style.fontSize, '28px');
      assert.strictEqual(pr0.style.background, 'rgba(0,0,0,0.7)');
      assert.strictEqual(predmety.children[1].textContent.length, 40, 'text nejvýš 40 znaků');
      assert.strictEqual(tv.prvky.titulek.textContent.indexOf('OKNO'), -1, 'pokyny u předmětů nejdou do pruhu');
      ws.onmessage({ data: '{"typ":"predmety","polozky":[]}' });
      assert.strictEqual(predmety.style.display, 'none', 'prázdný seznam = schovat');
      // telefon ve hře (od 1.5.22): nadpis a bubliny podle strany, od levého horního rohu misto, šířka misto, dole nejníž misto[3]
      ws.onmessage({ data: JSON.stringify({ typ: 'telefon', misto: [0.05, 0.1, 0.3, 0.6], pismo: 30, pozadi: 80, nadpis: 'Lucia' + new Array(60).join('x'), zpravy: [
        { text: 'Kde jsi?', strana: 'l' }, { text: 'Už jedu.', strana: 'p' }, { text: '   ', strana: 'p' },
        { text: 'Seznam úkolů', strana: 's' }, { text: new Array(600).join('b'), strana: 'neco' }] }) });
      var telefon = tv.prvky.telefon;
      assert.strictEqual(telefon.style.display, 'block', 'telefon je vidět');
      assert.strictEqual(telefon.children.length, 2, 'nadpis a seznam zpráv');
      assert.strictEqual(telefon.children[0].className, 'tel-nadpis');
      assert.strictEqual(telefon.children[0].textContent, ('Lucia' + new Array(60).join('x')).substring(0, 40), 'nadpis nejvýš 40 znaků');
      var bub = telefon.children[1].children;
      assert.strictEqual(bub.length, 4, 'prázdná zpráva se vynechá');
      assert.deepStrictEqual(bub.map(function (b) { return b.className; }),
        ['tel-zprava tel-l', 'tel-zprava tel-p', 'tel-zprava tel-s', 'tel-zprava tel-l'], 'strana: l / p / s, neznámá = příchozí');
      assert.strictEqual(bub[0].textContent, 'Kde jsi?');
      assert.strictEqual(bub[3].textContent.length, 400, 'text nejvýš 400 znaků');
      assert.strictEqual(telefon.style.left, Math.round(0.05 * 1920) + 'px');
      assert.strictEqual(telefon.style.top, Math.round(0.1 * 1080) + 'px');
      assert.strictEqual(telefon.style.width, Math.round(0.25 * 1920) + 'px');
      assert.strictEqual(telefon.style.maxHeight, Math.round(0.5 * 1080) + 'px');
      assert.strictEqual(telefon.style.fontSize, '30px', 'vejde se – písmo beze změny');
      assert.strictEqual(telefon.style.background, 'rgba(18,20,26,0.8)');
      assert.ok(bub.every(function (b) { return b.style.display !== 'none'; }), 'vejde se – všechny zprávy');
      assert.strictEqual(tv.prvky.titulek.textContent.indexOf('jedu'), -1, 'telefon nejde do pruhu');
      // nejvýš 12 zpráv (posledních), bez nadpisu
      var mnoho = [];
      for (var t = 1; t <= 15; t++) { mnoho.push({ text: 'Zpráva ' + t, strana: t % 2 ? 'l' : 'p' }); }
      ws.onmessage({ data: JSON.stringify({ typ: 'telefon', misto: [0.05, 0.1, 0.3, 0.6], pismo: 30, zpravy: mnoho }) });
      assert.strictEqual(telefon.children.length, 1, 'bez nadpisu jen seznam');
      bub = telefon.children[0].children;
      assert.strictEqual(bub.length, 12, 'nejvýš 12 zpráv');
      assert.strictEqual(bub[0].textContent, 'Zpráva 4');
      assert.strictEqual(bub[11].textContent, 'Zpráva 15', 'nejnovější dole');
      // nevejde se (napodobený prohlížeč měří vždy 150 px, misto má 108 px): písmo na 70 %, pak skrýt nejstarší, poslední zůstane
      ws.onmessage({ data: JSON.stringify({ typ: 'telefon', misto: [0.05, 0.1, 0.3, 0.2], pismo: 30, nadpis: 'Jason', zpravy: [
        { text: 'A', strana: 'l' }, { text: 'B', strana: 'p' }, { text: 'C', strana: 'l' }] }) });
      assert.strictEqual(telefon.style.fontSize, '21px', 'písmo nejvýš na 70 %');
      bub = telefon.children[1].children;
      assert.deepStrictEqual(bub.map(function (b) { return b.style.display === 'none'; }), [true, true, false], 'skryjí se nejstarší');
      // od 1.5.28: překlad přímo přes text v telefonu – odstavce na místě originálu, panel schovaný
      ws.onmessage({ data: JSON.stringify({ typ: 'telefon', rezim: 'pres', pismo: 22, zpravy: [{ text: 'Všechno, co jsi mohl SNÍST!', misto: [0.59, 0.63, 0.7, 0.65] }] }) });
      var pres = tv.prvky.telefonpres;
      assert.strictEqual(pres.style.display, 'block', 'přes text je vidět');
      assert.strictEqual(telefon.style.display, 'none', 'panel vedle schovaný');
      assert.strictEqual(pres.children[0].textContent, 'Všechno, co jsi mohl SNÍST!');
      assert.strictEqual(pres.children[0].style.fontSize, '22px');
      ws.onmessage({ data: '{"typ":"telefon","zpravy":[]}' });
      assert.strictEqual(pres.style.display, 'none', 'prázdné zprávy schovají i překlad přes text');
      ws.onmessage({ data: '{"typ":"telefon","zpravy":[]}' });
      assert.strictEqual(telefon.style.display, 'none', 'prázdné zprávy = schovat');
      ws.onmessage({ data: JSON.stringify({ typ: 'telefon', misto: [0.05, 0.1, 0.3, 0.6], zpravy: [{ text: 'Ahoj', strana: 'l' }] }) });
      assert.strictEqual(telefon.style.display, 'block');
      assert.strictEqual(telefon.style.fontSize, '28px', 'bez písma výchozí');
      // čára místo obrázku (od 1.5.23): mezi zprávami ano, na začátku ne
      ws.onmessage({ data: JSON.stringify({ typ: 'telefon', misto: [0.05, 0.1, 0.3, 0.6], zpravy: [
        { text: '', strana: 'c' }, { text: 'Příspěvek', strana: 's' }, { text: '', strana: 'c' }, { text: 'Další', strana: 's' }] }) });
      bub = telefon.children[0].children;
      assert.deepStrictEqual(bub.map(function (b) { return b.className; }), ['tel-zprava tel-s', 'tel-cara', 'tel-zprava tel-s'], 'čára mezi příspěvky');
      // obrázek ze hry (od 1.5.24): platná data: adresa = img, cizí adresa = jen čára
      ws.onmessage({ data: JSON.stringify({ typ: 'telefon', misto: [0.05, 0.1, 0.3, 0.6], zpravy: [
        { text: 'Příspěvek', strana: 's' }, { text: '', strana: 'c', obrazek: 'data:image/jpeg;base64,/9j/AAA=' },
        { text: 'Další', strana: 's' }, { text: '', strana: 'c', obrazek: 'http://zle.example/x.jpg' }, { text: 'Konec', strana: 's' }] }) });
      bub = telefon.children[0].children;
      assert.strictEqual(bub[1].className, 'tel-obrazek', 'obrázek ze hry');
      assert.strictEqual(bub[1].src, 'data:image/jpeg;base64,/9j/AAA=');
      assert.strictEqual(bub[3].className, 'tel-cara', 'cizí adresa = čára');
      // skrytí pruhu úkol neschová
      ws.onmessage({ data: '{"typ":"skryj","id":3}' });
      assert.strictEqual(tv.prvky.pruh.style.display, 'none');
      assert.strictEqual(ukol.style.display, 'block', 'skrytí pruhu neschová úkol');
      // barevné kusy (casti): span s barvou, null = výchozí; cizí barva i nesmyslné misto se zahodí
      ws.onmessage({ data: JSON.stringify({ typ: 'ukol', text: 'Jdi do úkrytu.', misto: ['x', 2, null], barva: 'red;x', casti: [
        { text: 'Jdi do ', barva: null }, { text: 'úkrytu', barva: '#FFD200' }, { text: '.', barva: 'url(x)' }] }) });
      assert.strictEqual(ukol.children.length, 3);
      assert.strictEqual(ukol.textContent, 'Jdi do úkrytu.');
      assert.strictEqual(ukol.children[0].style.color, undefined);
      assert.strictEqual(ukol.children[1].style.color, '#FFD200');
      assert.strictEqual(ukol.children[2].style.color, undefined, 'cizí barva se do stylu nedostane');
      assert.strictEqual(ukol.style.color, '#FFFFFF', 'neplatná barva = bílá');
      assert.strictEqual(ukol.style.top, Math.round(0.9 * 1080 - 75) + 'px', 'bez platného misto dole uprostřed');
      // titulek po úkolu: pruh se ukáže a úkol zůstane
      ws.onmessage({ data: JSON.stringify({ typ: 'ukaz', id: 7, text: 'Rychle!' }) });
      assert.strictEqual(tv.prvky.pruh.style.display, 'flex');
      assert.strictEqual(ukol.style.display, 'block', 'titulek úkol neschová');
      // dlouhý text se zkrátí na 300 znaků
      ws.onmessage({ data: JSON.stringify({ typ: 'ukol', text: new Array(500).join('a') }) });
      assert.strictEqual(ukol.textContent.length, 300);
      // prázdný text = schovat; pruhu se to netýká
      ws.onmessage({ data: '{"typ":"ukol","text":""}' });
      assert.strictEqual(ukol.style.display, 'none', 'prázdný úkol se schová');
      assert.strictEqual(tv.prvky.pruh.style.display, 'flex', 'schování úkolu neschová pruh');
      ws.onmessage({ data: '{"typ":"skryj","id":8}' });
      // znovu úkol – po ztrátě spojení (níže) musí zmizet
      ws.onmessage({ data: JSON.stringify({ typ: 'ukol', text: 'Ujeď policii.', misto: [0.35, 0.92, 0.65, 0.96] }) });
      assert.strictEqual(ukol.style.display, 'block');
      // neznámý typ zprávy nic nerozbije
      ws.onmessage({ data: '{"typ":"neco_noveho","text":"x"}' });
      ws.onmessage({ data: 'neni json' });
      assert.strictEqual(ukol.style.display, 'block');
      // nabídka Vzhled titulků (od 1.4.0): Android pošle volby, OK je při hře otevře, šipky požádají o změnu
      ws.onmessage({ data: '{"typ":"nastaveni","pruhlednost":90,"velikost":22,"podleHry":true,"poloha":"tesne","original":false}' });
      // Podpořit ReCaption (od 1.5.39): čtvereček jen z Androidu, vlevo od nabídky OK; cizí obrázek se nevezme
      assert.notStrictEqual(tv.prvky.kafe.style.display, 'block', 'bez zprávy podpora žádný čtvereček');
      ws.onmessage({ data: '{"typ":"podpora","obrazek":"javascript:alert(1)"}' });
      assert.notStrictEqual(tv.prvky.kafe.style.display, 'block', 'jen obrázek PNG');
      ws.onmessage({ data: '{"typ":"podpora","obrazek":"data:image/png;base64,AAAA"}' });
      assert.notStrictEqual(tv.prvky.kafe.style.display, 'block', 'při hře bez nabídky nic nezakrývá');
      tv.stiskni(13);
      assert.strictEqual(tv.prvky.panel.style.display, 'block', 'OK otevře nabídku');
      assert.strictEqual(tv.prvky.kafe.style.display, 'block', 'čtvereček vedle nabídky');
      assert.strictEqual(tv.prvky.kafe.className, 'kafe-panel');
      assert.strictEqual(tv.prvky.kafe.children[0].src, 'data:image/png;base64,AAAA');
      assert.strictEqual(tv.prvky.pruh.style.display, 'flex', 'v nabídce je vidět ukázka pruhu');
      tv.stiskni(39);
      var zmeny = tv.odeslano.filter(function (z) { return z.typ === 'zmen'; });
      assert.deepStrictEqual(zmeny.map(function (z) { return z.klic + '=' + z.hodnota; }), ['pruhlednost=100']);
      assert.strictEqual(tv.prvky.pruh.style.background, 'rgba(0,0,0,1)', 'náhled hned po změně');
      tv.stiskni(40); tv.stiskni(39);
      // od 1.5.18: barva písma, okraj, (poloha), originál, velikost originálu jen s originálem, barevná jména
      tv.stiskni(40); tv.stiskni(37);   // barva bílá → modrá
      assert.strictEqual(tv.prvky.titulek.style.color, '#9FD8FF', 'náhled v barvě');
      tv.stiskni(40); tv.stiskni(39);   // okraj 1 → 2
      tv.stiskni(40); tv.stiskni(37);   // poloha
      assert.strictEqual(tv.prvky.panel.children.filter(function (c) { return /pn-radek/.test(c.className); }).length, 8, 'bez originálu 8 řádků');
      tv.stiskni(40); tv.stiskni(39);   // originál zapnout → přibude Velikost originálu
      assert.strictEqual(tv.prvky.panel.children.filter(function (c) { return /pn-radek/.test(c.className); }).length, 9);
      tv.stiskni(40); tv.stiskni(39);   // velikost originálu automaticky → 12
      tv.stiskni(40); tv.stiskni(39);   // jména barevně vypnout
      zmeny = tv.odeslano.filter(function (z) { return z.typ === 'zmen'; });
      assert.deepStrictEqual(zmeny.map(function (z) { return z.klic + '=' + z.hodnota; }), ['pruhlednost=100', 'velikost=12', 'barvaPisma=3',
        'okraj=2', 'poloha=pres', 'original=true', 'velikostOrig=12', 'jmena=false']);
      tv.stiskni(40);
      assert.ok(/HDMI/.test(tv.prvky.panel.children.filter(function (c) { return /pn-radek/.test(c.className); })[8].children[1].textContent), 'poslední řádek je Vstup');
      tv.stiskni(38); tv.stiskni(39); tv.stiskni(38); tv.stiskni(37); tv.stiskni(38); tv.stiskni(38); tv.stiskni(39);   // zpět: jména, originál 12 → automaticky, originál vypnout
      // řádek Vstup (od 1.5.17) do Androidu nic neposílá
      assert.strictEqual(tv.prvky.panel.children.filter(function (c) { return c.className === 'pn-offline'; }).length, 0, 'se spojením bez řádku nepřipojeno');
      tv.stiskni(platforma === 'tizen' ? 10009 : 461);
      assert.strictEqual(tv.prvky.panel.style.display, 'none', 'Zpět zavře nabídku');
      assert.strictEqual(tv.prvky.kafe.style.display, 'none', 'čtvereček zmizí s nabídkou');
      assert.strictEqual(tv.prvky.pruh.style.display, 'none', 'ukázka po zavření zmizí');
      assert.strictEqual(tv.document.body.className, 's-hra', 'Zpět v nabídce neopustí hru');
      // stav z Androidu (od 1.5.0): body s tečkou pod řádkem „ReCaption · připojeno“; HDMI jen při potížích se vstupem (od 1.5.16)
      ws.onmessage({ data: JSON.stringify({ typ: 'stav', radky: [{ barva: 'pozor', text: 'PS5-151 · bez obrazu' }, { barva: 'zla', text: 'Čeština' }] }) });
      var st = tv.prvky.hlaseni.children;
      assert.strictEqual(tv.prvky.hlaseni.style.display, 'block', 'oranžový stav je vidět');
      assert.ok(/^Titulky PS5 · /.test(st[0].children[1].textContent), 'bez potíží se vstupem žádný řádek HDMI');
      assert.strictEqual(st[0].children.length, 2, 'se spojením pod řádkem ReCaptionu žádný běžící pruh');
      assert.ok(!st.some(function (r) { return r.className !== 'st-radek'; }), 'se spojením se nic nečeká');
      assert.strictEqual(st[1].children[1].textContent, 'PS5-151 · bez obrazu');
      assert.strictEqual(st[1].children[0].className, 'tecka pozor');
      assert.strictEqual(st[2].children[0].className, 'tecka info', 'neznámá barva = šedá');
      // záznam událostí: hned po ohlášení odešlo, co se nasbíralo (start, hledání, připojení)
      var zaznam1 = tv.odeslano.filter(function (z) { return z.typ === 'zaznam'; });
      assert.ok(zaznam1.length >= 1, 'po připojení odešel záznam');
      var radky1 = [].concat.apply([], zaznam1.map(function (z) { return z.radky; }));
      assert.ok(radky1.some(function (r) { return r.indexOf('start ReCaption ' + platforma + ' ' + verze) >= 0; }), 'záznam má start s verzí');
      assert.ok(radky1.some(function (r) { return r.indexOf('připojeno k ws://192.168.1.20:8787') >= 0; }), 'záznam má připojení');
      pockej(function () { return tv.odeslano.filter(function (z) { return z.typ === 'hotovo'; }).length === 10; }, function () {
        // spojení spadne → televize se připojí znovu a pošle jen nové řádky, bez textu titulků
        var pred = tv.odeslano.length;
        ws.onclose();
        assert.strictEqual(tv.prvky.ukol.style.display, 'none', 'po ztrátě spojení úkol zmizí');
        assert.strictEqual(tv.prvky.telefon.style.display, 'none', 'po ztrátě spojení telefon zmizí');
        pockej(function () { return tv.odeslano.slice(pred).some(function (z) { return z.typ === 'zaznam'; }); }, function () {
          var radky2 = [].concat.apply([], tv.odeslano.slice(pred).filter(function (z) { return z.typ === 'zaznam'; }).map(function (z) { return z.radky; }));
          assert.ok(radky2.some(function (r) { return /ukaž #1: 12 znaků/.test(r); }), 'záznam má délku titulku');
          assert.ok(radky2.some(function (r) { return /spojení se ztratilo/.test(r); }), 'záznam má ztrátu spojení');
          assert.ok(radky2.some(function (r) { return /úkol 14 znaků, 3 částí/.test(r); }), 'záznam má délku úkolu');
          assert.ok(!radky2.some(function (r) { return /start ReCaption/.test(r); }), 'staré řádky se neposílají znovu');
          assert.ok(!radky1.concat(radky2).some(function (r) { return /Počkej|Ahoj|úkrytu|policii|Rychle/.test(r); }), 'text titulků do záznamu nejde');
          // Zpět vrátí úvodní obrazovku a na Samsungu schová obraz ze vstupu
          tv.stiskni(platforma === 'tizen' ? 10009 : 461);
          assert.strictEqual(tv.document.body.className, 's-domu');
          if (platforma === 'tizen') { assert.strictEqual(tv.tvwindow[tv.tvwindow.length - 1], 'hide'); }
          // spojení na úvodní obrazovce zůstává (od 1.5.17) a stav to ukazuje zeleně
          var radekDomu = tv.prvky['domu-stav'].children[0];
          assert.strictEqual(radekDomu.children[0].className, 'tecka ok', 'úvodní obrazovka: připojeno');
          // titulek mimo hru se neukáže, jen potvrdí
          var ws2 = tv.spojeni.filter(function (w) { return w.adresa === 'ws://192.168.1.20:8787'; }).pop();
          var hotovoPred2 = tv.odeslano.filter(function (z) { return z.typ === 'hotovo'; }).length;
          ws2.onmessage({ data: JSON.stringify({ typ: 'ukaz', id: 50, text: 'Mimo hru.' }) });
          assert.strictEqual(tv.prvky.pruh.style.display, 'none', 'na úvodní obrazovce se titulek neukáže');
          pockej(function () { return tv.odeslano.filter(function (z) { return z.typ === 'hotovo'; }).length === hotovoPred2 + 1; }, function () {
            dalsi(tv);
          }, platforma + ': hotovo mimo hru');
        }, platforma + ': záznam po novém připojení');
      }, platforma + ': hotovo');    }, platforma + ': ohlášení');
  }, 30);
}

// Od 1.5.17: zapamatovaný vstup HDMI 2 → start rovnou do hry; bez ReCaptionu na Androidu jde nabídka OK
// otevřít, změna se uloží v televizi a po připojení odejde jako „zmen“, až když Android pošle „nastaveni“.
function zkusBezSpojeni(platforma, dalsi) {
  var android = { vypnuty: true };
  var tv = spust(platforma, { pamet: { hdmi: '2' }, android: android });
  var zpet = platforma === 'tizen' ? 10009 : 461;
  function radkyPanelu() { return tv.prvky.panel.children.filter(function (c) { return /pn-radek/.test(c.className); }); }
  assert.strictEqual(tv.document.body.className, 's-hra', 'se zapamatovaným vstupem rovnou hra');
  setTimeout(function () {
    if (platforma === 'tizen') { assert.deepStrictEqual(tv.tvwindow.slice(-2), ['setSource HDMI2', 'show BEHIND']); }
    else { assert.strictEqual(tv.prvky.obraz.children[0].children[0].atributy.src, 'ext://hdmi:2'); }
    var st = tv.prvky.hlaseni.children;
    // bez spojení jediný řádek o ReCaptionu (od 1.5.16)
    assert.strictEqual(st.length, 1);
    assert.strictEqual(st[0].children[0].className, 'tecka pozor', 'ReCaption se hledá');
    // pod řádkem „hledám v síti…“ slabý běžící pruh (od 1.5.18)
    assert.strictEqual(st[0].className, 'st-radek ceka');
    assert.strictEqual(st[0].children[2].className, 'st-ceka', 'při hledání běžící pruh');
    assert.strictEqual(st[0].children[2].children[0].className, 'st-ceka-beh');
    // nabídka OK bez spojení: náhled ukázkovou větou; uložení se nehlásí (od 1.5.18), jen když selže
    function radekNeulozeno() { return tv.prvky.panel.children.filter(function (c) { return c.className === 'pn-offline'; }).length; }
    tv.stiskni(13);
    assert.strictEqual(tv.prvky.panel.style.display, 'block', 'OK otevře nabídku i bez spojení');
    assert.strictEqual(radekNeulozeno(), 0, 'nic se nehlásí'); 
    assert.strictEqual(tv.prvky.pruh.style.display, 'flex', 'náhled bez spojení');
    tv.stiskni(39);   // pozadí 100 → 0
    assert.strictEqual(tv.odeslano.filter(function (z) { return z.typ === 'zmen'; }).length, 0, 'bez spojení nic neodejde');
    assert.strictEqual(tv.prvky.pruh.style.background, 'rgba(0,0,0,0)', 'náhled hned po změně');
    assert.deepStrictEqual(JSON.parse(tv.pamet.vzhled_zmeny), { pruhlednost: 0 }, 'změna čeká v televizi');
    assert.strictEqual(JSON.parse(tv.pamet.vzhled).pruhlednost, 0);
    assert.strictEqual(radekNeulozeno(), 0, 'povedené uložení se nehlásí');
    // paměť televize nejde zapsat → řádek „Změny se nepodařilo uložit“; po povedeném uložení zmizí
    var zapis = tv.ls.setItem;
    tv.ls.setItem = function () { throw new Error('plno'); };
    tv.stiskni(37);   // pozadí 0 → 100
    assert.strictEqual(radekNeulozeno(), 1, 'nepovedené uložení se hlásí');
    tv.ls.setItem = zapis;
    tv.stiskni(39);   // pozadí 100 → 0
    assert.strictEqual(radekNeulozeno(), 0);
    assert.deepStrictEqual(JSON.parse(tv.pamet.vzhled_zmeny), { pruhlednost: 0 });
    // řádek Vstup: ◀ = HDMI 1, přepne hned a zapamatuje
    tv.stiskni(38);   // nahoru z prvního řádku = poslední (Vstup)
    assert.ok(/vybrane/.test(radkyPanelu()[7].className), 'poslední řádek je Vstup');
    assert.strictEqual(radkyPanelu()[7].children[1].textContent, '◀ HDMI 2 ▶');
    tv.stiskni(37);
    assert.strictEqual(radkyPanelu()[7].children[1].textContent, '◀ HDMI 1 ▶');
    assert.strictEqual(tv.pamet.hdmi, '1', 'nový vstup se zapamatuje');
    pockej(function () {
      if (platforma === 'tizen') { return tv.tvwindow[tv.tvwindow.length - 2] === 'setSource HDMI1'; }
      var v = tv.prvky.obraz.children;
      return v.length && v[v.length - 1].children[0].atributy.src === 'ext://hdmi:1';
    }, function () {
      // řádek HDMI ve stavu jen při potížích se vstupem (od 1.5.16) – vstup ověřuje zdroj obrazu výš
      tv.stiskni(zpet);
      assert.strictEqual(tv.prvky.panel.style.display, 'none');
      assert.strictEqual(tv.document.body.className, 's-hra');
      // ReCaption na Androidu se zapne: televize se připojí, ale změnu pošle až po „nastaveni“
      android.vypnuty = false;
      pockej(function () { return tv.odeslano.some(function (z) { return z.typ === 'pripojeno'; }); }, function () {
        var p = tv.odeslano.filter(function (z) { return z.typ === 'pripojeno'; })[0];
        assert.strictEqual(p.hdmi, 1);
        var stP = tv.prvky.hlaseni.children;
        assert.strictEqual(stP[0].children[0].className, 'tecka ok', 'po připojení zeleně');
        assert.strictEqual(stP[0].children.length, 2, 'po připojení běžící pruh zmizí');
        assert.strictEqual(tv.odeslano.filter(function (z) { return z.typ === 'zmen'; }).length, 0, 'před nastaveni nic');
        var ws = tv.spojeni.filter(function (w) { return w.adresa === 'ws://192.168.1.20:8787'; }).pop();
        ws.onmessage({ data: '{"typ":"nastaveni","pruhlednost":90,"velikost":22,"podleHry":false,"poloha":"nad","original":false}' });
        var zmeny = tv.odeslano.filter(function (z) { return z.typ === 'zmen'; });
        assert.deepStrictEqual(zmeny.map(function (z) { return z.klic + '=' + z.hodnota; }), ['pruhlednost=0'], 'uložená změna odešla');
        assert.deepStrictEqual(JSON.parse(tv.pamet.vzhled_zmeny), {}, 'nic už nečeká');
        // Android potvrdí novými volbami – nic dalšího se neposílá
        ws.onmessage({ data: '{"typ":"nastaveni","pruhlednost":0,"velikost":22,"podleHry":false,"poloha":"nad","original":false}' });
        assert.strictEqual(tv.odeslano.filter(function (z) { return z.typ === 'zmen'; }).length, 1);
        assert.strictEqual(JSON.parse(tv.pamet.vzhled).pruhlednost, 0);
        // se spojením nabídka bez řádku nepřipojeno a změna jde hned
        tv.stiskni(13);
        assert.strictEqual(tv.prvky.panel.children.filter(function (c) { return c.className === 'pn-offline'; }).length, 0);
        tv.stiskni(39);
        assert.deepStrictEqual(tv.odeslano.filter(function (z) { return z.typ === 'zmen'; }).map(function (z) { return z.klic + '=' + z.hodnota; }),
          ['pruhlednost=0', 'pruhlednost=20']);
        tv.stiskni(zpet);
        // Zpět při hře = úvodní obrazovka; Nápověda (◀ Ukázka) → ukázka → Zpět do nápovědy
        tv.stiskni(zpet);
        assert.strictEqual(tv.document.body.className, 's-domu');
        tv.stiskni(40);   // z HDMI 1 dolů na Nápovědu
        tv.stiskni(13);
        assert.strictEqual(tv.document.body.className, 's-napoveda');
        tv.stiskni(37); tv.stiskni(13);
        assert.strictEqual(tv.document.body.className, 's-ukazka', 'ukázka z nápovědy');
        tv.stiskni(zpet);
        assert.strictEqual(tv.document.body.className, 's-napoveda', 'z ukázky zpět do nápovědy');
        tv.stiskni(13);   // vybrané je Zpět
        assert.strictEqual(tv.document.body.className, 's-domu');
        dalsi();
      }, platforma + ': připojení po zapnutí Androidu');
    }, platforma + ': přepnutí vstupu');
  }, 30);
}

var vysledky = [];
function hotovo(platforma) {
  vysledky.push(platforma);
  console.log('OK: ' + platforma);
  if (vysledky.length === 4) { console.log('Aplikace pro televizi: LG i Samsung v pořádku.'); process.exit(0); }
}

['tizen', 'webos'].forEach(function (platforma) {
  var verzeSoubor = platforma === 'tizen'
    ? /version="([0-9.]+)"/.exec(fs.readFileSync(path.join(ROOT, 'tizen', 'app', 'config.xml'), 'utf8').split('<widget')[1])[1]
    : JSON.parse(fs.readFileSync(path.join(ROOT, 'webos', 'obchod', 'app', 'appinfo.json'), 'utf8')).version;
  zkus(platforma, verzeSoubor, function (tv) {
    hotovo(platforma);
  });
  zkusBezSpojeni(platforma, function () { hotovo(platforma + ' (zapamatované HDMI, bez spojení)'); });
});
