// Zkouška webového průvodce bez prohlížeče: node ci/pruvodce-web.test.js
// Spustí pomoc/pruvodce.html (vykreslovač) s daty pomoc/pruvodce-data.js (ze šablony core/Pruvodce.kt) v malém
// náhradním DOM a projde všechny kombinace voleb (tv × in × na × ko × dl) × kroky × podkroky × cs/en. Hlídá: počet
// podkroků v záhlaví sedí, nikde „undefined“ / „NaN“ / „null“, prázdný nadpis nebo obsah, a každé video na stránce je
// v pomoc/videa/kroky.json. Podle harnessu koordinačního sezení (24. 11. 2026). Konec s kódem 1 při chybě.
'use strict';
const fs = require('fs');
const path = require('path');
const koren = path.join(__dirname, '..', 'pomoc');
const skript = fs.readFileSync(path.join(koren, 'pruvodce-data.js'), 'utf8') + '\n' +
  [...fs.readFileSync(path.join(koren, 'pruvodce.html'), 'utf8').matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]).join('\n');
const kroky = JSON.parse(fs.readFileSync(path.join(koren, 'videa', 'kroky.json'), 'utf8')).klipy;

function run(hash, lang) {
  const els = {};
  const el = (id) => els[id] || (els[id] = { id, innerHTML: '', textContent: '', hidden: false, querySelectorAll: () => [] });
  const document = { documentElement: {}, title: '', getElementById: el, querySelector: (q) => q === '.lista' ? { querySelectorAll: () => [] } : null, querySelectorAll: () => [] };
  const location = { hash, search: '?lang=' + lang, pathname: '/pruvodce.html', href: '' };
  const history = { replaceState: (a, b, u) => { location.hash = u.slice(u.indexOf('#')); } };
  const window = { addEventListener() {}, scrollTo() {} };
  let xhr;
  function XMLHttpRequest() { xhr = this; this.open = () => {}; this.send = () => {}; }
  new Function('document', 'location', 'history', 'window', 'XMLHttpRequest', 'localStorage', 'navigator', skript)(
    document, location, history, window, XMLHttpRequest, { getItem: () => null, setItem() {} }, { language: lang });
  xhr.responseText = JSON.stringify({ format: 1, klipy: kroky }); xhr.onload();
  const subs = [...els.bok.innerHTML.matchAll(/data-p="\d+"[^>]*>(?:✓ )?([^<]*)</g)].map(m => m[1]);
  const vids = [...els.panel.innerHTML.matchAll(/src="videa\/([^"]+)-(cs|en)\.mp4"/g)].map(m => m[1]);
  return { subs, pod: els['postup-pod'].textContent, krok: els['postup-krok'].textContent, panel: els.panel.innerHTML, nadpis: els.nadpis.innerHTML, hash: location.hash, vids };
}

const chyby = new Set();
let n = 0;
for (const lang of ['cs', 'en']) for (const tv of ['atv', 'lg', 'samsung']) for (const inn of ['downloader', 'usb', 'telefon'])
  for (const na of ['ovladac', 'jine', 'telefon']) for (const ko of ['ps5', 'xbox']) for (const dl of ['ne', 'ano']) for (let k = 0; k < 8; k++) {
    const zaklad = `#tv=${tv}&na=${na}&ko=${ko}&dl=${dl}&in=${inn}&k=${k}`;
    const pocet = Math.max(1, run(zaklad + '&p=0', lang).subs.length);
    for (let p = 0; p < pocet; p++) {
      const r = run(zaklad + '&p=' + p, lang); n++;
      if (!r.pod.includes('/' + pocet + ' ')) chyby.add('POČET ' + r.hash + ' ' + r.pod);
      if (/undefined|NaN|null/.test(r.panel + r.pod + r.nadpis + r.krok)) chyby.add('UNDEFINED ' + r.hash + ' ' + lang);
      if (!r.nadpis.trim() || !r.panel.trim()) chyby.add('PRÁZDNÉ ' + r.hash + ' ' + lang);
      for (const v of r.vids) if (!kroky[v]) chyby.add('VIDEO CHYBÍ ' + v);
    }
  }
// přehled „Co je potřeba“ (stránka bez kroku v adrese)
for (const lang of ['cs', 'en']) {
  const r = run('', lang); n++;
  if (!/class="prehled"/.test(r.panel) || /undefined|NaN|null/.test(r.panel + r.nadpis)) chyby.add('PŘEHLED ' + lang);
}
if (chyby.size) { console.log([...chyby].join('\n')); console.log('CHYBA: ' + chyby.size); process.exit(1); }
console.log('Průvodce na webu: ' + n + ' stránek v pořádku.');
