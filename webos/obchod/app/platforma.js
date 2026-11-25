/*
 * ReCaption v televizi – část jen pro televize LG (webOS).
 *
 * Všechno ostatní (app.js, style.css, index.html) je stejné i v aplikaci pro televize Samsung
 * (tizen/app); sestavení APK hlídá, že se soubory nerozešly (úloha samsungBalicek). Tady je jen
 * to, co se na LG dělá jinak: dotazy na systém televize, obraz ze vstupu HDMI, adresa v síti,
 * tlačítko Zpět, ukončení a parametry spuštění. app.js volá jen funkce z window.ReCaptionTv.
 *
 * Verze musí být stejná jako v appinfo.json (hlídá sestavení). Televize ji posílá do Androidu
 * ve zprávě „pripojeno“, aby průvodce poznal, co v televizi běží.
 *
 * Schválně psáno postaru (ES5), protože televize z let 2016–2017 mají prohlížeč Chromium 38.
 */
(function () {
  'use strict';

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

  // Spořič obrazovky (od 1.5.44, přání majitele – testerovi LG pouštěla spořič přes hru, protože se ovladačem nic
  // nedělá): televize se před spuštěním spořiče zeptá aplikací, které se přihlásily, a ReCaption odpoví „ne“.
  // Odběr musí žít celou dobu běhu aplikace, proto vlastní most (odkaz na něm, ať ho nesmaže úklid paměti).
  (function neusinat() {
    var Most = window.WebOSServiceBridge || window.PalmServiceBridge;
    if (!Most) { return; }
    try {
      var most = new Most();
      window.ReCaptionSporic = most;
      most.onservicecallback = function (zprava) {
        var o;
        try { o = JSON.parse(zprava); } catch (e) { return; }
        if (o && o.timestamp !== undefined) {
          sluzba('luna://com.webos.service.tvpower/power/responseScreenSaverRequest',
            { clientName: 'ReCaption', ack: false, timestamp: o.timestamp }, function () {});
        }
      };
      most.call('luna://com.webos.service.tvpower/power/registerScreenSaverRequest',
        JSON.stringify({ subscribe: true, clientName: 'ReCaption' }));
    } catch (e) { /* starší televize službu nemá – spořič pak řídí televize sama */ }
  })();

  function stavSite(hotovo) {
    sluzba('luna://com.palm.connectionmanager/getStatus', {}, function (a) {
      if (a && a.returnValue) { hotovo(a); return; }
      sluzba('luna://com.palm.connectionmanager/getstatus', {}, hotovo);
    });
  }

  window.ReCaptionTv = {
    platforma: 'webos',
    verze: '1.5.61',

    // hotovo(model, system) – jen když televize odpoví
    zjistiTelevizi: function (hotovo) {
      sluzba('luna://com.webos.service.tv.systemproperty/getSystemInfo',
        { keys: ['modelName', 'firmwareVersion', 'sdkVersion'] },
        function (o) {
          if (o && o.modelName) {
            hotovo(o.modelName, 'webOS ' + (o.sdkVersion || '?') + ' (' + (o.firmwareVersion || '?') + ')');
          }
        });
    },

    // Jazyk nabídky televize je spolehlivější než jazyk prohlížeče. hotovo(kod nebo '')
    jazykTelevize: function (hotovo) {
      sluzba('luna://com.webos.settingsservice/getSystemSettings', { keys: ['localeInfo'] }, function (o) {
        var kod = o && o.settings && o.settings.localeInfo && o.settings.localeInfo.locales && o.settings.localeInfo.locales.UI;
        hotovo(kod || '');
      });
    },

    // Obraz ze vstupu HDMI přímo ve stránce (prvek video). chyba() = vstup nejde otevřít.
    ukazVstup: function (cislo, obal, chyba) {
      var video = document.createElement('video');
      var zdroj = document.createElement('source');
      video.setAttribute('autoplay', 'autoplay');
      zdroj.setAttribute('src', 'ext://hdmi:' + cislo);
      zdroj.setAttribute('type', 'service/webos-external');
      video.addEventListener('error', chyba);
      zdroj.addEventListener('error', chyba);
      video.appendChild(zdroj);
      obal.appendChild(video);
    },

    // Na LG stačí, že app.js vyprázdní obal s videem.
    skryjVstup: function () {},

    // hotovo(adresa IPv4 nebo '', odpovedela) – odpovedela = televize odpověděla, jen nemá síť.
    // LG službu píše getStatus, televize z roku 2017 odpovídá na getstatus – zkusí se obojí.
    adresaSite: function (hotovo) {
      stavSite(function (o) {
        var ip = o && ((o.wired && o.wired.ipAddress) || (o.wifi && o.wifi.ipAddress));
        hotovo(ip || '', !!(o && o.returnValue));
      });
    },

    jeZpet: function (klavesa) { return klavesa === 461 || klavesa === 27 || klavesa === 8; },

    // Úplné ukončení. Samotné platformBack aplikaci jen schová do pozadí, kde by dál hledala
    // čtečku v síti; window.close() ji zavře.
    ukonci: function () {
      try { window.close(); } catch (e) { /* jinak aspoň do pozadí */ }
      if (window.PalmSystem && window.PalmSystem.platformBack) { window.PalmSystem.platformBack(); }
    },

    // Parametry spuštění (jen pro zkoušky). Při startu je má PalmSystem, při spuštění už běžící
    // aplikace je nese událost webOSRelaunch.
    parametry: function (fn) {
      document.addEventListener('webOSRelaunch', function (udalost) { fn(udalost.detail); });
      fn(window.PalmSystem && window.PalmSystem.launchParams);
    }
  };
})();
