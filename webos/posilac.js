#!/usr/bin/env node
/*
 * Zkušební posílač titulků z počítače do aplikace ReCaption v televizi LG.
 *
 * Použití:  node webos/posilac.js [port]
 * Pak na televizi spustit ReCaption (webos/obchod) a vybrat vstup – počítač si najde sama.
 *
 * Nic nečte ani nepřekládá. Jen dokola posílá zkušební věty a měří, za jak dlouho
 * je televize vykreslí. Ověřuje tím cestu „jiné zařízení čte a překládá, televize
 * jen ukazuje pruh“. Nepotřebuje žádné knihovny – WebSocket si obslouží sám.
 */
'use strict';
const http = require('http');
const crypto = require('crypto');

const PORT = Number(process.argv[2] || 8787);
const VETY = [
  'Marek: Potřebujeme peníze, Jonasi.',
  'Jonas: Myslím, že v Greyfordu nic nenajdeme.',
  'Nasedni na koně, vyrážíme za svítání.',
  'Silas: Jestli budeme takhle pokračovat, maršálové nás najdou.',
  'Mira: Jsi si jistý, že je to správná cesta?',
  'Musíme se odsud dostat!',
  'Počkej.',
  // řádky zvlášť (pole casti): barevná jména a volby odpovědí; starší televize ukáže jen text
  {
    text: 'Mira: Vezmu levé dveře.\nTobin: Tak já jdu doprava.',
    casti: [
      { jmeno: 'Mira', barva: '#F2C14E', text: 'Vezmu levé dveře.' },
      { jmeno: 'Tobin', barva: '#7FC8F8', text: 'Tak já jdu doprava.' },
    ],
  },
  {
    text: 'Kessler: Tak co bude?\n• Vezmu tu práci.\n• Řekni mi víc o výplatě.\n• Nemám zájem.',
    casti: [
      { jmeno: 'Kessler', barva: '#8EDB8A', text: 'Tak co bude?' },
      { volba: true, text: 'Vezmu tu práci.' },
      { volba: true, text: 'Řekni mi víc o výplatě.' },
      { volba: true, text: 'Nemám zájem.' },
    ],
  },
];
const UKAZAT_MS = 3000;
const PAUZA_MS = 1000;

function cas() { return new Date().toTimeString().slice(0, 8); }

// Jedna textová zpráva WebSocketu směrem k televizi (server zprávy nemaskuje).
function ramec(text) {
  const data = Buffer.from(text, 'utf8');
  const hlava = data.length < 126
    ? Buffer.from([0x81, data.length])
    : Buffer.from([0x81, 126, data.length >> 8, data.length & 255]);
  return Buffer.concat([hlava, data]);
}

// Rozebere příchozí data na zprávy od televize (ty jsou vždy maskované a krátké).
function ctecka(naZpravu, naKonec) {
  let zasoba = Buffer.alloc(0);
  return (kus) => {
    zasoba = Buffer.concat([zasoba, kus]);
    for (;;) {
      if (zasoba.length < 2) { return; }
      const druh = zasoba[0] & 0x0f;
      let delka = zasoba[1] & 0x7f;
      let zacatek = 2;
      if (delka === 126) {
        if (zasoba.length < 4) { return; }
        delka = zasoba.readUInt16BE(2);
        zacatek = 4;
      } else if (delka === 127) { naKonec(); return; }
      const maskovano = (zasoba[1] & 0x80) !== 0;
      const celkem = zacatek + (maskovano ? 4 : 0) + delka;
      if (zasoba.length < celkem) { return; }
      const maska = maskovano ? zasoba.subarray(zacatek, zacatek + 4) : null;
      const telo = Buffer.from(zasoba.subarray(celkem - delka, celkem));
      if (maska) { for (let i = 0; i < telo.length; i++) { telo[i] ^= maska[i % 4]; } }
      zasoba = zasoba.subarray(celkem);
      if (druh === 8) { naKonec(); return; }
      if (druh === 1) { naZpravu(telo.toString('utf8')); }
    }
  };
}

const server = http.createServer((pozadavek, odpoved) => {
  odpoved.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
  odpoved.end('ReCaption – zkušební posílač titulků běží.\n');
});

server.on('upgrade', (pozadavek, spojeni) => {
  const klic = pozadavek.headers['sec-websocket-key'];
  if (!klic) { spojeni.destroy(); return; }
  const podpis = crypto.createHash('sha1')
    .update(klic + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64');
  spojeni.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n' +
    'Sec-WebSocket-Accept: ' + podpis + '\r\n\r\n');
  // podle pozdravu televize pozná, že na téhle adrese opravdu čeká ReCaption
  spojeni.write(ramec(JSON.stringify({ typ: 'vitej' })));
  console.log(cas(), 'televize se připojila z', spojeni.remoteAddress);

  let id = 0;
  let poradi = 0;
  let zije = true;
  const odeslano = new Map();
  const doby = [];
  let casovac = null;

  function posli(zprava) {
    zprava.id = ++id;
    odeslano.set(id, { kdy: process.hrtime.bigint(), popis: zprava.typ === 'ukaz' ? zprava.text : '(skrýt pruh)' });
    spojeni.write(ramec(JSON.stringify(zprava)));
  }
  function dalsi() {
    if (!zije) { return; }
    const veta = VETY[poradi++ % VETY.length];
    posli(typeof veta === 'string' ? { typ: 'ukaz', text: veta } : Object.assign({ typ: 'ukaz' }, veta));
    casovac = setTimeout(() => {
      if (!zije) { return; }
      posli({ typ: 'skryj' });
      casovac = setTimeout(dalsi, PAUZA_MS);
    }, UKAZAT_MS);
  }
  function konec() {
    if (!zije) { return; }
    zije = false;
    clearTimeout(casovac);
    spojeni.destroy();
    if (doby.length) {
      const serazene = doby.slice().sort((a, b) => a - b);
      const prumer = doby.reduce((a, b) => a + b, 0) / doby.length;
      console.log(cas(), 'televize se odpojila. Vykreslení: průměr ' + prumer.toFixed(0) + ' ms, nejdéle ' +
        serazene[serazene.length - 1].toFixed(0) + ' ms, zpráv ' + doby.length);
    } else {
      console.log(cas(), 'televize se odpojila');
    }
  }

  spojeni.on('data', ctecka((text) => {
    let z;
    try { z = JSON.parse(text); } catch (e) { return; }
    if (z.typ === 'pripojeno') {
      console.log(cas(), 'televize:', z.televize, '|', z.system, '| HDMI', z.hdmi);
      dalsi();
    } else if (z.typ === 'hotovo' && odeslano.has(z.id)) {
      const o = odeslano.get(z.id);
      odeslano.delete(z.id);
      const ms = Number(process.hrtime.bigint() - o.kdy) / 1e6;
      doby.push(ms);
      console.log(cas(), 'vykresleno za ' + ms.toFixed(0) + ' ms:', o.popis);
    }
  }, konec));
  spojeni.on('close', konec);
  spojeni.on('error', konec);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(cas(), 'Posílač běží na portu ' + PORT + '. Čekám na televizi…');
});
