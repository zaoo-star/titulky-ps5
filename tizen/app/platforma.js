/*
 * ReCaption v televizi – část jen pro televize Samsung (Tizen).
 *
 * Všechno ostatní (app.js, style.css, index.html) je kopie z webos/obchod/app; sestavení APK
 * hlídá, že se nerozešly (úloha samsungBalicek v app/build.gradle.kts). Tady je jen to, co se na
 * Samsungu dělá jinak než na LG:
 *  - obraz ze vstupu HDMI ukazuje televize sama pod stránkou (tizen.tvwindow, vrstva BEHIND),
 *    stránka je při hře průhledná (style.css, html.s-hra),
 *  - model, jazyk a adresa v síti jsou z tizen.systeminfo,
 *  - tlačítko Zpět ovladače Samsung má kód 10009, aplikace se ukončí přes tizen.application.
 * Spojení s Androidem (WebSocket na port 8787) je v app.js stejné jako na LG.
 *
 * Hlášení vstupu HDMI a tvwindow podle přijímače PlayCaption pro Samsung (tizen/app/js/main.js,
 * AGPL-3.0, © Adam Růžička); upraveno pro ReCaption.
 *
 * Verze musí být stejná jako v config.xml (hlídá sestavení). Televize ji posílá do Androidu
 * ve zprávě „pripojeno“; podle ní průvodce v Androidu pozná, že je aplikace nainstalovaná.
 *
 * Schválně psáno postaru (ES5): Samsung z let 2015–2016 (Tizen 2.3/2.4) má starý WebKit,
 * 2017 (Tizen 3) Chromium 47.
 */
(function () {
  'use strict';

  // Spořič obrazovky (od 1.5.44, přání majitele): obrazovka zůstane zapnutá, dokud ReCaption běží (televize jinak
  // spořič pustí přes hru, protože se ovladačem nic nedělá). Stejně jako na LG (neusinat v webos/obchod/app/platforma.js)
  // jedno místo, které se spustí hned při startu.
  // Od 1.5.50: hlavní cesta na TV Samsung je Samsung Product API (webapis.appcommon.setScreenSaver, bez oprávnění,
  // úroveň Public). Knihovnu webapis.js načte běhové prostředí televize z „$WEBAPIS/webapis/webapis.js“ – index.html
  // je společný s LG, proto se skript vkládá jen tady (na LG tento soubor není). Záloha tizen.power (oprávnění power
  // v config.xml). Znovu po návratu aplikace do popředí – televize stav spořiče při přepnutí aplikací vrací.
  function neusinat() {
    try {
      var ac = window.webapis && window.webapis.appcommon;
      if (ac && ac.setScreenSaver) {
        ac.setScreenSaver(ac.AppCommonScreenSaverState ? ac.AppCommonScreenSaverState.SCREEN_SAVER_OFF : 0,
          function () {}, function () {});
      }
    } catch (e) { /* starší televize nebo mimo televizi */ }
    try {
      if (window.tizen && window.tizen.power) { window.tizen.power.request('SCREEN', 'SCREEN_NORMAL'); }
    } catch (e2) { /* bez oprávnění nebo starší televize – spořič řídí televize sama */ }
  }
  (function nactiWebapis() {
    if (!window.tizen) { neusinat(); return; }   // mimo televizi Samsung nic nenačítat
    if (window.webapis) { neusinat(); return; }
    try {
      var skript = document.createElement('script');
      skript.setAttribute('src', '$WEBAPIS/webapis/webapis.js');
      skript.onload = neusinat;
      skript.onerror = neusinat;
      (document.head || document.body || document.documentElement).appendChild(skript);
    } catch (e) { /* bez Product API zůstává tizen.power */ }
    neusinat();
  })();

  var VERZE = '1.5.61';
  // Pořadové číslo požadavku na obraz: odpověď na starší (uživatel mezitím odešel) se zahodí.
  var obrazPokus = 0;

  function maTizen() { return typeof window.tizen !== 'undefined' && window.tizen; }

  // Jedna vlastnost ze systeminfo; hotovo(hodnota nebo null) vždy přesně jednou.
  function vlastnost(nazev, hotovo) {
    var vyrizeno = false;
    function konec(v) {
      if (vyrizeno) { return; }
      vyrizeno = true;
      hotovo(v);
    }
    if (!maTizen() || !window.tizen.systeminfo) { konec(null); return; }
    try {
      window.tizen.systeminfo.getPropertyValue(nazev, function (v) { konec(v); }, function () { konec(null); });
    } catch (e) {
      konec(null);
    }
    setTimeout(function () { konec(null); }, 5000);
  }

  function hdmiVstup(seznam, cislo) {
    var i;
    for (i = 0; i < (seznam || []).length; i++) {
      if (seznam[i] && seznam[i].type === 'HDMI' && Number(seznam[i].number) === cislo) { return seznam[i]; }
    }
    return null;
  }

  function adresa(v) {
    var ip = v && v.ipAddress;
    return /^\d+\.\d+\.\d+\.\d+$/.test(ip || '') && ip !== '0.0.0.0' ? ip : '';
  }

  window.ReCaptionTv = {
    platforma: 'tizen',
    verze: VERZE,

    zjistiTelevizi: function (hotovo) {
      vlastnost('BUILD', function (b) {
        var verze = '';
        try { verze = window.tizen.systeminfo.getCapability('http://tizen.org/feature/platform.version'); } catch (e) { /* neznámá */ }
        if (b && b.model) { hotovo(String(b.model), 'Tizen ' + (verze || '?') + ' (' + (b.buildVersion || '?') + ')'); }
      });
    },

    // jazyk nabídky televize, např. „cs_CZ“
    jazykTelevize: function (hotovo) {
      vlastnost('LOCALE', function (l) { hotovo(l && l.language ? String(l.language) : ''); });
    },

    // Obraz ze vstupu HDMI: televize ho ukáže sama pod stránkou přes celou obrazovku.
    ukazVstup: function (cislo, obal, chyba) {
      var moje = ++obrazPokus;
      function plati() { return moje === obrazPokus; }
      function spatne() { if (plati()) { chyba(); } }
      if (!maTizen() || !window.tizen.tvwindow) { spatne(); return; }
      vlastnost('VIDEOSOURCE', function (info) {
        if (!plati()) { return; }
        var zdroj = info && (hdmiVstup(info.connected, cislo) || hdmiVstup(info.supported, cislo));
        if (!zdroj) { spatne(); return; }
        try {
          window.tizen.tvwindow.setSource(zdroj, function () {
            if (!plati()) { return; }
            try {
              window.tizen.tvwindow.show(function () {}, spatne, ['0px', '0px', '1920px', '1080px'], 'MAIN', 'BEHIND');
            } catch (e) { spatne(); }
          }, spatne, 'MAIN');
        } catch (e) { spatne(); }
      });
    },

    skryjVstup: function () {
      obrazPokus++;
      if (!maTizen() || !window.tizen.tvwindow) { return; }
      try { window.tizen.tvwindow.hide(function () {}, function () {}, 'MAIN'); } catch (e) { /* nebylo zobrazené */ }
    },

    // hotovo(adresa IPv4 nebo '', odpovedela): nejdřív kabel, pak Wi-Fi.
    adresaSite: function (hotovo) {
      vlastnost('ETHERNET_NETWORK', function (kabel) {
        if (adresa(kabel)) { hotovo(adresa(kabel), true); return; }
        vlastnost('WIFI_NETWORK', function (wifi) {
          hotovo(adresa(wifi), !!(kabel || wifi));
        });
      });
    },

    // 10009 = Zpět (Return) na ovladači Samsung; 27 a 8 pro zkoušení v počítači
    jeZpet: function (klavesa) { return klavesa === 10009 || klavesa === 27 || klavesa === 8; },

    ukonci: function () {
      try { window.tizen.application.getCurrentApplication().exit(); } catch (e) {
        try { window.close(); } catch (e2) { /* mimo televizi */ }
      }
    },

    // Parametry spuštění (jen pro zkoušky): data „titulky“ a „hdmi“ v požadavku na spuštění
    // (např. `tizen run … ` s app control). Bez nich se nic neděje.
    parametry: function (fn) {
      var p = {};
      var i, data;
      try {
        data = window.tizen.application.getCurrentApplication().getRequestedAppControl().appControl.data || [];
        for (i = 0; i < data.length; i++) {
          if ((data[i].key === 'titulky' || data[i].key === 'hdmi') && data[i].value && data[i].value.length) {
            p[data[i].key] = String(data[i].value[0]);
          }
        }
      } catch (e) { /* spuštěno bez parametrů nebo mimo televizi */ }
      fn(p);
    }
  };

  // Barevná a další tlačítka ovladače aplikace nepotřebuje; šipky, OK a Zpět chodí bez registrace.
  // Když aplikace přejde do pozadí, obraz ze vstupu se schová (jinak by zůstal přes jinou aplikaci).
  document.addEventListener('visibilitychange', function () {
    if (document.hidden) { window.ReCaptionTv.skryjVstup(); } else { neusinat(); }
  });
})();
