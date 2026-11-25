/*
 * ReCaption – zkušební aplikace pro televize LG (webOS).
 *
 * Zjišťuje na skutečné televizi, co je pro překlad titulků potřeba:
 *   - jestli aplikace umí ukázat obraz ze vstupu HDMI a nakreslit přes něj pruh,
 *   - jestli si aplikace může ten obraz sama přečíst (pak by nebylo potřeba
 *     připojovat se k PlayStationu po síti),
 *   - jakou verzi systému a prohlížeče televize má.
 *
 * Schválně psáno postaru (ES5: var, function, žádné šipky ani šablony),
 * protože televize z let 2016–2017 mají prohlížeč Chromium 38.
 */
(function () {
  'use strict';

  var VETY = [
    'Tady by byl překlad titulků ze hry.',
    'Musíme se dostat na druhou stranu řeky.',
    'Počkej tady. Hned jsem zpátky.',
    'Tohle se mi vůbec nelíbí.'
  ];

  var udaje = { model: '?', system: '?', jadro: '?' };
  var vybrane = 0;
  var hraje = false;
  var casovace = [];
  var tlacitka = document.getElementById('tlacitka').children;

  function el(id) { return document.getElementById(id); }

  function napis(id, text, stav) {
    var e = el(id);
    e.textContent = text;
    e.className = stav || '';
  }

  function pozdeji(fn, ms) {
    var t = setTimeout(fn, ms);
    casovace.push(t);
    return t;
  }

  // ---- Dotazy na systém televize -------------------------------------

  // Zavolá službu televize. Odpověď (nebo chybu) vrátí vždy přesně jednou.
  function sluzba(adresa, parametry, hotovo) {
    var Most = window.WebOSServiceBridge || window.PalmServiceBridge;
    var vyrizeno = false;
    function konec(odpoved) {
      if (vyrizeno) { return; }
      vyrizeno = true;
      hotovo(odpoved);
    }
    if (!Most) {
      konec({ returnValue: false, errorText: 'mimo televizi' });
      return;
    }
    try {
      var most = new Most();
      most.onservicecallback = function (zprava) {
        var o;
        try { o = JSON.parse(zprava); } catch (e) { o = { returnValue: false, errorText: String(zprava) }; }
        konec(o);
      };
      most.call(adresa, JSON.stringify(parametry));
    } catch (e) {
      konec({ returnValue: false, errorText: String(e && e.message || e) });
    }
    setTimeout(function () { konec({ returnValue: false, errorText: 'bez odpovědi' }); }, 5000);
  }

  function zjistiTelevizi() {
    var m = /Chrom(?:e|ium)\/(\d+)/.exec(navigator.userAgent);
    udaje.jadro = m ? 'Chromium ' + m[1] : 'neznámé';
    napis('u-jadro', udaje.jadro);

    sluzba('luna://com.webos.service.tv.systemproperty/getSystemInfo',
      { keys: ['modelName', 'firmwareVersion', 'sdkVersion'] },
      function (o) {
        if (o && o.modelName) {
          udaje.model = o.modelName;
          udaje.system = 'webOS ' + (o.sdkVersion || '?') + ' (' + (o.firmwareVersion || '?') + ')';
        } else {
          udaje.model = 'nezjištěno';
          udaje.system = 'nezjištěno';
        }
        napis('u-model', udaje.model);
        napis('u-system', udaje.system);
        if (hraje) { vypisUdaje(); }
      });
  }

  function vypisUdaje() {
    napis('v-model', udaje.model);
    napis('v-system', udaje.system);
    napis('v-jadro', udaje.jadro);
  }

  // ---- Zkouška --------------------------------------------------------

  function spustZkousku(cislo) {
    hraje = true;
    document.body.className = 'hraje';
    napis('v-vstup', '– HDMI ' + cislo);
    vypisUdaje();
    napis('v-hdmi', 'čekám…');
    napis('v-cteni', 'čekám…');
    napis('v-snimek', 'čekám…');
    napis('v-wasm', typeof WebAssembly === 'object' ? 'ano' : 'ne',
      typeof WebAssembly === 'object' ? 'ano' : 'ne');

    // Obraz ze vstupu HDMI přímo v aplikaci.
    var stav = { hraje: false, chyba: false };
    var video = document.createElement('video');
    video.setAttribute('autoplay', 'autoplay');
    var zdroj = document.createElement('source');
    zdroj.setAttribute('src', 'ext://hdmi:' + cislo);
    zdroj.setAttribute('type', 'service/webos-external');
    video.addEventListener('playing', function () { stav.hraje = true; });
    video.addEventListener('loadedmetadata', function () { stav.hraje = true; });
    video.addEventListener('error', function () { stav.chyba = true; });
    zdroj.addEventListener('error', function () { stav.chyba = true; });
    video.appendChild(zdroj);
    el('obraz').appendChild(video);

    // Titulky v pruhu se střídají jako při hře.
    var poradi = 0;
    el('titulek').textContent = VETY[0];
    casovace.push(setInterval(function () {
      poradi = (poradi + 1) % VETY.length;
      el('titulek').textContent = VETY[poradi];
    }, 3500));

    pozdeji(function () {
      if (stav.chyba) {
        napis('v-hdmi', 'televize vstup odmítla', 'ne');
      } else if (stav.hraje) {
        napis('v-hdmi', 'televize hlásí, že hraje', 'ano');
      } else {
        napis('v-hdmi', 'bez hlášení – rozhodne, co vidíte');
      }
      zkusCteni(video);
      zkusSnimek();
    }, 5000);
  }

  // Umí si aplikace přečíst, co je na obraze? (zmenšený snímek do plátna)
  function zkusCteni(video) {
    try {
      var platno = el('platno');
      var kresba = platno.getContext('2d');
      kresba.clearRect(0, 0, 160, 90);
      kresba.drawImage(video, 0, 0, 160, 90);
      var body = kresba.getImageData(0, 0, 160, 90).data;
      var svetlych = 0;
      var i;
      for (i = 0; i < body.length; i += 4) {
        if (body[i] + body[i + 1] + body[i + 2] > 60) { svetlych++; }
      }
      if (svetlych > 150) {
        napis('v-cteni', 'ano, obraz jde přečíst', 'ano');
      } else {
        napis('v-cteni', 'ne, aplikace vidí jen černou', 'ne');
      }
    } catch (e) {
      napis('v-cteni', 'ne, televize to nedovolí', 'ne');
    }
  }

  // Druhá cesta k obrazu: snímek obrazovky od systému (na různých
  // televizích se ta služba jmenuje různě, zkusí se obě).
  function zkusSnimek() {
    var parametry = {
      path: '/tmp/recaption-zkouska.jpg',
      method: 'DISPLAY',
      format: 'JPG',
      width: 960,
      height: 540
    };
    sluzba('luna://com.webos.service.tv.capture/executeOneShot', parametry, function (a) {
      if (a && a.returnValue) {
        napis('v-snimek', 'ano (služba A)', 'ano');
        return;
      }
      sluzba('luna://com.webos.service.capture/executeOneShot', parametry, function (b) {
        if (b && b.returnValue) {
          napis('v-snimek', 'ano (služba B)', 'ano');
        } else {
          var duvod = (b && b.errorText) || (a && a.errorText) || 'zakázáno';
          napis('v-snimek', 'ne – ' + String(duvod).substring(0, 34), 'ne');
        }
      });
    });
  }

  function ukonciZkousku() {
    var i;
    for (i = 0; i < casovace.length; i++) {
      clearTimeout(casovace[i]);
      clearInterval(casovace[i]);
    }
    casovace = [];
    el('obraz').innerHTML = '';
    hraje = false;
    document.body.className = '';
  }

  // ---- Ovládání dálkovým ovladačem -----------------------------------

  function oznac(index) {
    var i;
    vybrane = (index + tlacitka.length) % tlacitka.length;
    for (i = 0; i < tlacitka.length; i++) {
      tlacitka[i].className = i === vybrane ? 'tl vybrane' : 'tl';
    }
  }

  function potvrd(index) {
    oznac(index);
    spustZkousku(tlacitka[vybrane].getAttribute('data-hdmi'));
  }

  document.addEventListener('keydown', function (udalost) {
    var klavesa = udalost.keyCode;
    var zpet = klavesa === 461 || klavesa === 27 || klavesa === 8;
    if (hraje) {
      if (zpet) { ukonciZkousku(); udalost.preventDefault(); }
      return;
    }
    if (klavesa === 37) { oznac(vybrane - 1); }
    else if (klavesa === 39) { oznac(vybrane + 1); }
    else if (klavesa === 13) { potvrd(vybrane); }
    else if (zpet) {
      if (window.PalmSystem && window.PalmSystem.platformBack) { window.PalmSystem.platformBack(); }
      else { window.close(); }
    }
  });

  // Ukazovátko ovladače Magic Remote.
  (function () {
    var i;
    function pripoj(index) {
      tlacitka[index].addEventListener('mouseover', function () { if (!hraje) { oznac(index); } });
      tlacitka[index].addEventListener('click', function () { if (!hraje) { potvrd(index); } });
    }
    for (i = 0; i < tlacitka.length; i++) { pripoj(i); }
  })();

  oznac(0);
  zjistiTelevizi();
})();
