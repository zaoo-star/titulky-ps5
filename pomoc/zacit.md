# Začít s ReCaptionem

ReCaption čte anglické titulky ze hry na PlayStationu 5 (nebo Xboxu), překládá je a ukazuje v pruhu přes obraz.
Po odpovědi na dvě otázky se ukážou jen kroky, které se dané sestavy týkají – stejně rozdělené jako v průvodci
aplikace, u každého krátké video.

<style>
.volby{display:flex;flex-wrap:wrap;gap:8px;margin:8px 0 18px}
.volby button{font:600 16px system-ui,sans-serif;padding:10px 14px;border-radius:10px;border:2px solid #d0d7de;background:#f6f8fa;cursor:pointer}
.volby button.on{border-color:#f2c14e;background:#fff5d6}

/* kroky jako v appce (shodné s index.html) */
.kroky{list-style:none;margin:0;padding:0;display:grid;gap:14px;counter-reset:k}
.krok{display:grid;grid-template-columns:auto 1fr 220px;gap:18px;align-items:center;background:var(--karta,#1a1f2b);border:1px solid var(--cara,#2a3142);border-radius:16px;padding:16px 18px;counter-increment:k}
.krok:before{content:counter(k);display:grid;place-items:center;width:38px;height:38px;border-radius:50%;background:rgba(242,193,78,.14);color:var(--zluta,#f2c14e);font-weight:800;flex-shrink:0}
.krok h4{margin:0 0 4px;font-size:17px}
.krok p{margin:0;color:var(--slaby,#9aa3b5)!important;font-size:15px}
.krok .kde{display:inline-block;font-size:12px;font-weight:600;color:#b9c4da;background:#263045;border-radius:99px;padding:1px 8px;margin-left:6px;vertical-align:middle}
.krok .klip{position:relative;display:block;aspect-ratio:16/9;border-radius:10px;overflow:hidden;background:#000 center/cover;border:1px solid var(--cara,#2a3142)}
.krok .klip:after{content:"▶";position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:38px;height:38px;border-radius:50%;background:rgba(242,193,78,.95);color:#1a1300;display:grid;place-items:center;font-size:13px;padding-left:3px}
.krok .klip span{position:absolute;right:6px;bottom:5px;font-size:12px;font-weight:600;background:rgba(0,0,0,.7);border-radius:5px;padding:0 6px;color:#fff}
.krok .bez{color:var(--slaby,#9aa3b5);font-size:13px;text-align:center}
@media (max-width:720px){.krok{grid-template-columns:auto 1fr}.krok .klip,.krok .bez{grid-column:1/-1}}
.krok a.maly{display:inline-block;margin-top:6px;font-size:14px;font-weight:600;text-decoration:none}
.vybrano-popis{color:var(--slaby,#9aa3b5);margin:0 0 18px}
.vybrano-pozn{color:var(--slaby,#9aa3b5);font-size:14px;margin:14px 0 0}

/* přehrávač videa */
.prehravac{position:fixed;inset:0;z-index:50;background:rgba(0,0,0,.86);display:grid;place-items:center;padding:16px}
.prehravac[hidden]{display:none}
.prehravac-obal{position:relative;width:min(1000px,100%)}
.prehravac video{width:100%;max-height:80vh;border-radius:12px;background:#000;display:block}
.prehravac-nazev{color:#fff;font-weight:600;margin-top:10px}
.zavrit{position:absolute;right:0;top:-46px;width:38px;height:38px;border-radius:50%;border:0;background:#1a1f2b;color:#eef1f6;font-size:18px;cursor:pointer}
</style>

**Pořadí v aplikaci:** průvodce ReCaptionu začíná vždy krokem **Jazyky** (jazyk aplikace, Překládat z, Jazyk překladu),
pak **Zařízení a TV**, **Konzole**, **Překlad**, (u Android TV) **Zobrazení titulků** a **Připojení**. Tady stačí
odpovědět, jakou TV a na čem se nastavuje.

**Předem na PS5:** ReCaption se k PS5 připojuje **🔵 druhým účtem** (účet pro ReCaption), ne 🟢 herním.
Založit ho a zapnout pod ním Remote Play: [návod k PS5](ps5.md#ucet) (asi 5 minut). Xbox: [návod k Xboxu](xbox.md).

**1. Jakou TV nastavit?**
<div class="volby" id="tv">
<button data-v="atv">Android TV / Google TV<br><small>Sony, Philips, TCL, Chromecast, Shield…</small></button>
<button data-v="lg">LG (webOS)</button>
<button data-v="samsung">Samsung (Tizen)</button>
</div>

**2. Na čem nastavovat?**
<div class="volby" id="na">
<button data-v="pc">💻 Počítač</button>
<button data-v="android">📱 Telefon s Androidem</button>
<button data-v="iphone">📱 iPhone</button>
<button data-v="tv">📺 Jen ovladač TV</button>
</div>

<p id="vyber" style="color:#57606a">Stačí vybrat obojí výš.</p>

<div id="vysledek"></div>

<div class="prehravac" id="prehravac" hidden>
  <div class="prehravac-obal">
    <button class="zavrit" aria-label="Zavřít">✕</button>
    <video id="prehravac-video" controls playsinline preload="none"></video>
    <div class="prehravac-nazev" id="prehravac-nazev"></div>
  </div>
</div>

<script>
(function () {
  // Kroky jako v appce (core/VideoSteps): klipy pomoc/videa/krok-…-cs.mp4, délky z videa/kroky.json.
  // Tenhle objekt je obdoba KROKY v index.html, jen rozepsaný po jednotlivých krocích wizardu a se
  // zněním podle toho, na čem se nastavuje (telefon / počítač / iPhone / jen ovladač TV).
  var DELKY = {};

  var CESTY = [
    { tv: ['atv'], na: ['pc'], nazev: 'Android TV + 💻 počítač',
      popis: 'ReCaption běží přímo v televizi. Nastavuje se z počítače ve stejné síti – adresu ukáže TV.',
      kroky: [
        { n: 'ReCaption do TV', kde: 'TV', t: 'V TV aplikace <b>Downloader</b> a kód <b>8438934</b> – <a href="android-tv.html#downloader">návod krok za krokem</a>. (S nástrojem <code>adb</code>: <code>adb connect &lt;adresa TV&gt;</code> a <code>adb install ReCaption.apk</code>.)', v: 'krok-instalace-downloader' },
        { n: 'Jazyky', kde: 'TV', t: 'Průvodce začne jazyky a pod nimi volbou <b>Nastavit telefonem nebo počítačem (doporučeno)</b>.', v: 'krok-jazyky' },
        { n: 'Nastavit z počítače', kde: 'počítač', t: 'Na stejné síti v prohlížeči adresa, kterou TV ukáže pod čtverečkem (💻 http://192.168…). Na stránce: 1 · přihlášení 🔵 účtem pro ReCaption (adresa skoro prázdné stránky „redirect“ se zkopíruje a vloží zpět na stránku ReCaption), 2 · osmimístné číslo z PS5, 3 · klíč DeepL (nepovinné). TV se spáruje sama a seznam v TV se odškrtá.' },
        { n: 'Offline překlad', kde: 'TV', t: 'Pokračovat na TV → stažení jazyka → Lepší překlad (DeepL, jde přeskočit).', v: 'krok-preklad' },
        { n: 'Zobrazení titulků', kde: 'TV', t: 'Otevřít nastavení TV → Usnadnění → ReCaption → zapnout, tlačítkem Zpět zpátky do aplikace (<a href="android-tv.html#zobrazeni">kde to je</a>).', v: 'krok-zobrazeni-android' },
        { n: 'Hrát', kde: 'PS5', t: 'Na PS5 přepnout zpět na 🟢 herní účet, TV na HDMI s PS5, ve hře zapnout anglické titulky, v ReCaptionu Spustit.' }
      ] },
    { tv: ['atv'], na: ['android'], nazev: 'Android TV + 📱 Android',
      popis: 'ReCaption do TV i do telefonu – telefon jen pomáhá s nastavením a přihlášením.',
      kroky: [
        { n: 'ReCaption do telefonu', kde: 'telefon', t: '<a href="https://recaption.pages.dev/stahnout">Stáhnout ReCaption.apk</a> a nainstalovat (<a href="telefon.html#android">povolit instalaci</a>). V telefonu se otevře <b>Přihlášení pro TV</b> (pomocník).' },
        { n: 'ReCaption do TV z telefonu', kde: 'telefon + TV', t: 'V pomocníkovi dole v kartě tlačítko <b>Nahrát ReCaption do Android TV</b> – v TV jednou zapnout ladění přes síť a potvrdit „Povolit ladění?“ (<a href="android-tv.html#telefon">návod</a>). Nebo bez telefonu přes <a href="android-tv.html#downloader">Downloader</a>.' },
        { n: 'Jazyky', kde: 'TV', t: 'Zvolit <b>Nastavit telefonem nebo počítačem</b> → naskenovat čtvereček fotoaparátem telefonu → v „Jak pokračovat?“ zvolit <b>V aplikaci ReCaption</b> (nic se nekopíruje, číslo z PS5 přečte kamerou) nebo <b>Pokračovat v prohlížeči</b>.', v: 'krok-jazyky' },
        { n: 'Offline překlad', kde: 'TV', t: 'Pokračovat na TV → stažení jazyka → Lepší překlad (DeepL, jde přeskočit).', v: 'krok-preklad' },
        { n: 'Zobrazení titulků', kde: 'TV', t: 'Usnadnění → ReCaption → zapnout (<a href="android-tv.html#zobrazeni">kde to je</a>).', v: 'krok-zobrazeni-android' },
        { n: 'Hrát', kde: 'PS5', t: 'Na PS5 zpět na 🟢 herní účet, TV na HDMI s PS5.' }
      ] },
    { tv: ['atv'], na: ['iphone'], nazev: 'Android TV + 📱 iPhone',
      popis: 'ReCaption pro iPhone není a není potřeba – iPhone jen pomáhá s přihlášením.',
      kroky: [
        { n: 'ReCaption do TV', kde: 'TV', t: 'Aplikace <b>Downloader</b> v TV a kód <b>8438934</b> – <a href="android-tv.html#downloader">návod</a>.', v: 'krok-instalace-downloader' },
        { n: 'Jazyky', kde: 'TV', t: 'Zvolit <b>Nastavit telefonem nebo počítačem</b> → naskenovat čtvereček Fotoaparátem iPhonu (stejná Wi-Fi). V Safari se otevře stránka <b>ReCaption · nastavení TV</b>.', v: 'krok-jazyky' },
        { n: 'Přihlášení a PS5', kde: 'iPhone', t: 'Přihlášení 🔵 účtem pro ReCaption (na stránce „redirect“ klepnout na adresu → <b>Kopírovat</b> → zpět na kartu ReCaption), číslo z PS5 (podržet prst v políčku → <b>Skenovat text</b>) a klíč DeepL. Bez kopírování adresy „redirect“: <a href="zkratka.html">zkratka ReCaption pro iPhone</a>.' },
        { n: 'Offline překlad', kde: 'TV', t: 'Pokračovat na TV → stažení jazyka → Lepší překlad (DeepL, jde přeskočit).', v: 'krok-preklad' },
        { n: 'Zobrazení titulků', kde: 'TV', t: 'Usnadnění → ReCaption → zapnout (<a href="android-tv.html#zobrazeni">kde to je</a>).', v: 'krok-zobrazeni-android' },
        { n: 'Hrát', kde: 'PS5', t: 'Na PS5 zpět na 🟢 herní účet, TV na HDMI s PS5.' }
      ] },
    { tv: ['atv'], na: ['tv'], nazev: 'Android TV + 📺 jen ovladač',
      popis: 'Bez telefonu nebo počítače – celý průvodce (9 kroků) jde ovladačem TV.',
      kroky: [
        { n: 'ReCaption do TV', kde: 'TV', t: 'Aplikace <b>Downloader</b> a kód <b>8438934</b> – <a href="android-tv.html#downloader">návod</a>.', v: 'krok-instalace-downloader' },
        { n: 'Jazyky', kde: 'TV', t: 'Nechat <b>Nastavit přímo na TV</b> a Pokračovat.', v: 'krok-jazyky' },
        { n: 'Konzole', kde: 'TV', t: 'Volba PlayStation nebo Xbox.', v: 'krok-konzole' },
        { n: 'Offline překlad', kde: 'TV', t: 'Stažení jazyka a Lepší překlad (DeepL, jde přeskočit).', v: 'krok-preklad' },
        { n: 'Zobrazení titulků', kde: 'TV', t: 'Usnadnění → ReCaption → zapnout (<a href="android-tv.html#zobrazeni">kde to je</a>).', v: 'krok-zobrazeni-android' },
        { n: 'Připojení PlayStationu', kde: 'PS5 + telefon', t: 'Jen na přihlášení 🔵 účtem PlayStation je jednou potřeba telefon nebo počítač (Sony přihlášení v TV nepustí) – TV ukáže čtvereček, telefon ho naskenuje fotoaparátem. Osmimístné číslo z PS5.', v: 'krok-pripojeni-android-ps', ps5: true },
        { n: 'Hrát', kde: 'PS5', t: 'Na Domů Spustit, na PS5 zpět na 🟢 herní účet, TV na HDMI s PS5.' }
      ] },
    { tv: ['lg'], na: ['android'], nazev: 'LG + 📱 Android',
      popis: 'TV LG si obraz hry sama nepřečte – čte a překládá ReCaption v telefonu a do TV posílá hotové věty.',
      kroky: [
        { n: 'ReCaption do telefonu', kde: 'telefon', t: '<a href="https://recaption.pages.dev/stahnout">Stáhnout ReCaption.apk</a> a nainstalovat (<a href="telefon.html#android">povolit instalaci</a>).' },
        { n: 'Vývojářský režim v TV LG', kde: 'TV', t: 'Zdarma, jednou: účet LG pro vývojáře, aplikace <b>Developer Mode</b> v TV, <b>Dev Mode Status</b> a <b>Key Server</b> – <a href="lg.html">návod</a>.' },
        { n: 'Přihlášení pro TV', kde: 'telefon', t: 'V pomocníkovi zvolit <b>Číst pro TV</b> – otevře se průvodce (9 kroků).' },
        { n: 'Jazyky', kde: 'telefon', t: 'Jazyk aplikace, z čeho se překládá, do čeho.', v: 'krok-jazyky-lg' },
        { n: 'Zařízení a TV', kde: 'telefon + TV', t: 'V kroku Televize tlačítko <b>Nahrát ReCaption do TV LG</b> (adresu a Passphrase přečte kamerou z obrazovky TV).', v: 'krok-zarizeni-lg' },
        { n: 'Konzole', kde: 'telefon', t: 'Volba PlayStation nebo Xbox.', v: 'krok-konzole-lg' },
        { n: 'Offline překlad', kde: 'telefon', t: 'Stažení jazyka a Lepší překlad (DeepL, jde přeskočit).', v: 'krok-preklad-lg' },
        { n: 'Připojení PlayStationu', kde: 'telefon + PS5', t: 'Přihlášení 🔵 účtem pro ReCaption a osmimístné číslo z PS5.', v: 'krok-pripojeni-lg-ps', ps5: true },
        { n: 'Hrát', kde: 'PS5', t: 'V TV spustit ReCaption a vybrat HDMI s PS5, v telefonu na Domů Spustit; telefon na nabíječce.' }
      ] },
    { tv: ['samsung'], na: ['android'], nazev: 'Samsung + 📱 Android',
      popis: 'TV Samsung si obraz hry sama nepřečte – čte a překládá ReCaption v telefonu a do TV posílá hotové věty.',
      kroky: [
        { n: 'ReCaption do telefonu', kde: 'telefon', t: '<a href="https://recaption.pages.dev/stahnout">Stáhnout ReCaption.apk</a> a nainstalovat (<a href="telefon.html#android">povolit instalaci</a>).' },
        { n: 'Přihlášení pro TV', kde: 'telefon', t: 'Zvolit <b>Číst pro TV</b> – otevře se průvodce (9 kroků).' },
        { n: 'Jazyky', kde: 'telefon', t: 'Jazyk aplikace, z čeho se překládá, do čeho.', v: 'krok-jazyky-samsung' },
        { n: 'Zařízení a TV', kde: 'telefon + TV', t: 'V kroku Televize tlačítko <b>Nahrát ReCaption do TV Samsung</b>: vývojářský režim v TV a nahrání přes <b>Apps2Samsung</b> – <a href="samsung.html">návod</a>.', v: 'krok-zarizeni-samsung' },
        { n: 'Konzole', kde: 'telefon', t: 'Volba PlayStation nebo Xbox.', v: 'krok-konzole-samsung' },
        { n: 'Offline překlad', kde: 'telefon', t: 'Stažení jazyka a Lepší překlad (DeepL, jde přeskočit).', v: 'krok-preklad-samsung' },
        { n: 'Připojení PlayStationu', kde: 'telefon + PS5', t: 'Přihlášení 🔵 účtem pro ReCaption a osmimístné číslo z PS5.', v: 'krok-pripojeni-samsung-ps', ps5: true },
        { n: 'Hrát', kde: 'PS5', t: 'V TV spustit ReCaption a vybrat HDMI s PS5, v telefonu na Domů Spustit; telefon na nabíječce.' }
      ] },
    { tv: ['lg', 'samsung'], na: ['pc', 'iphone'], nazev: 'LG / Samsung + 💻 počítač s Windows nebo Mac (zkušební)',
      popis: 'TV LG a Samsung si obraz hry samy nepřečtou – čte a překládá zařízení vedle nich. Bez telefonu s Androidem to může být počítač s Windows nebo Mac s Apple Silicon (M1 a novější) ve stejné síti (iPhone čtečkou být nemůže, poslouží jen k přihlášení).',
      kroky: [
        { n: 'ReCaption do počítače', kde: 'počítač', t: 'Windows: ZIP a spustit ReCaption.exe (<a href="pc.html">návod</a>). Mac: DMG, přetáhnout do Aplikací a povolit Nahrávání obrazovky (<a href="mac.html">návod</a>). Na Domů Připravit a Stáhnout, pak ⚙ Nastavení → Použití → <b>Čtečka pro TV</b> (na Macu výchozí).' },
        { n: 'Spárovat PS5', kde: 'počítač + PS5', t: 'U řádku <b>PS5 · nespárováno</b> zvolit <b>Spárovat</b> – 🔵 účet pro ReCaption (přihlášení telefonem přes čtvereček, i iPhonem) a osmimístné číslo z PS5.', ps5: true },
        { n: 'ReCaption do TV z počítače', kde: 'počítač + TV', t: 'LG – vývojářský režim a webOS Dev Manager (<a href="lg.html#pocitac">návod</a>) / Samsung – Apps2Samsung pro počítač (<a href="samsung.html#pocitac">návod</a>).' },
        { n: 'Hrát', kde: 'PS5', t: 'V TV spustit ReCaption, v počítači Spustit a PS5 zapnout ovladačem.' }
      ],
      pozn: 'S telefonem s Androidem (stačí i starší, Android 8+) je to jednodušší – cesta 📱 Android.' },
    { tv: ['lg', 'samsung'], na: ['tv'], nazev: 'LG / Samsung jen s ovladačem',
      popis: 'TV LG a Samsung si obraz hry samy nepřečtou a ReCaption se do nich nahrává z jiného zařízení – samotným ovladačem to nejde.',
      kroky: [
        { n: 'Potřeba druhé zařízení', kde: 'telefon / box / počítač', t: 'Číst a překládat musí <b>telefon s Androidem</b> (stačí i starší, Android 8+), box s Android TV, nebo počítač (<a href="pc.html">Windows</a> / <a href="mac.html">Mac</a>) ve stejné síti. Pak platí jejich cesta: nahoře zvolit 📱 Android, nebo 💻 Počítač.' }
      ] }
  ];

  function el(tag, cls, html) { var e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; }

  function vyhledej(tv, na) {
    for (var i = 0; i < CESTY.length; i++) {
      var c = CESTY[i];
      if (c.tv.indexOf(tv) >= 0 && c.na.indexOf(na) >= 0) return c;
    }
    return null;
  }

  function kresli(tv, na) {
    var box = document.getElementById('vysledek');
    box.innerHTML = '';
    var c = vyhledej(tv, na);
    if (!c) return;
    box.appendChild(el('h3', null, c.nazev));
    box.appendChild(el('p', 'vybrano-popis', c.popis));
    var ol = el('ol', 'kroky');
    for (var i = 0; i < c.kroky.length; i++) {
      var s = c.kroky[i];
      var li = el('li', 'krok'), txt = el('div');
      var h = el('h4', null, s.n); h.appendChild(el('span', 'kde', s.kde)); txt.appendChild(h);
      txt.appendChild(el('p', null, s.t));
      if (s.ps5) { var a5 = el('a', 'maly', 'Jak připravit PS5 →'); a5.href = 'ps5.html'; txt.appendChild(a5); }
      li.appendChild(txt);
      if (s.v) {
        var a = el('a', 'klip'); a.href = 'videa/' + s.v + '-cs.mp4'; a.setAttribute('data-video', s.v);
        a.setAttribute('data-nazev', s.n); a.style.backgroundImage = 'url(videa/' + s.v + '-cs.jpg)';
        if (DELKY[s.v]) a.appendChild(el('span', null, DELKY[s.v]));
        li.appendChild(a);
      } else li.appendChild(el('div', 'bez', s.n === 'Hrát' ? '🎮' : ''));
      ol.appendChild(li);
    }
    box.appendChild(ol);
    if (c.pozn) box.appendChild(el('p', 'vybrano-pozn', c.pozn));
  }

  try {
    var dreq = new XMLHttpRequest();
    dreq.open('GET', 'videa/kroky.json');
    dreq.onload = function () { try { DELKY = JSON.parse(dreq.responseText).klipy || {}; if (vyber.tv && vyber.na) kresli(vyber.tv, vyber.na); } catch (e) {} };
    dreq.send();
  } catch (e) {}

  // dvě otázky → vyhledání a vykreslení cesty; jedno hlášení serveru na kombinaci za návštěvu
  var vyber = { tv: null, na: null };
  var odeslano = {};
  function odeslatVolbu(tv, na) {
    var klic = tv + '|' + na;
    if (odeslano[klic]) return;
    odeslano[klic] = true;
    try {
      fetch('https://recaption-hlaseni.recaption.workers.dev/v1/krok', {
        method: 'POST',
        body: JSON.stringify({ tv: tv, na: na })
      }).catch(function () {});
    } catch (e) {}
  }
  function ukaz() {
    var hotovo = vyber.tv && vyber.na;
    document.getElementById('vyber').style.display = hotovo ? 'none' : '';
    if (hotovo) {
      kresli(vyber.tv, vyber.na);
      odeslatVolbu(vyber.tv, vyber.na);
    } else {
      document.getElementById('vysledek').innerHTML = '';
    }
  }
  ['tv', 'na'].forEach(function (id) {
    var tl = document.querySelectorAll('#' + id + ' button');
    for (var i = 0; i < tl.length; i++) tl[i].addEventListener('click', function () {
      for (var j = 0; j < tl.length; j++) tl[j].className = '';
      this.className = 'on';
      vyber[id] = this.getAttribute('data-v');
      ukaz();
    });
  });
  ukaz();

  // přehrávač videa (odkaz funguje i bez skriptu – otevře video)
  var box = document.getElementById('prehravac'), vid = document.getElementById('prehravac-video');
  function zavri() { vid.pause(); vid.removeAttribute('src'); vid.load(); box.hidden = true; }
  document.addEventListener('click', function (e) {
    var a = e.target.closest ? e.target.closest('[data-video]') : null;
    if (!a) return;
    e.preventDefault();
    var id = a.getAttribute('data-video');
    vid.poster = 'videa/' + id + '-cs.jpg';
    vid.src = 'videa/' + id + '-cs.mp4';
    document.getElementById('prehravac-nazev').textContent = a.getAttribute('data-nazev') || (a.textContent || '').trim();
    box.hidden = false;
    var p = vid.play(); if (p && p.catch) p.catch(function () {});
  });
  box.addEventListener('click', function (e) { if (e.target === box || e.target.className === 'zavrit') zavri(); });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && !box.hidden) zavri(); });
})();
</script>

## Víc TV nebo PlayStationů

- **Dvě Android TV:** ReCaption do každé zvlášť – každá má vlastní nastavení i čtvereček **Nastavit z telefonu**
  (⚙ Nastavení → Obecné, nebo stránka Konzole).
- **Dvě TV LG / Samsung, jeden telefon:** telefon si pamatuje vzhled titulků pro každou TV zvlášť.
- **Dva PlayStationy:** v ReCaptionu na stránce Konzole **Spárovat další PS5**, přepínání řádkem **Aktivní PS5**.

## Všechny návody

- [PlayStation 5 – 🔵 účet pro ReCaption a nastavení](ps5.md)
- [Android TV / Google TV – první instalace ReCaptionu](android-tv.md)
- [Televize LG – vývojářský režim](lg.md)
- [Televize Samsung](samsung.md)
- [Telefon (Android i iPhone)](telefon.md) · [iPhone: zkratka ReCaption](zkratka.md)
- Počítač – čtečka pro TV LG / Samsung (zkušební): [Windows](pc.md) · [Mac](mac.md)
- [Xbox](xbox.md)
- [DeepL – lepší překlad](deepl.md)

Na tyhle stránky vedou i čtverečky (QR kódy) z aplikace. Když LG, Sony nebo Samsung své stránky přesunou,
opraví se odkaz tady a aplikace se měnit nemusí.

---
*[English version →](en/zacit.md)*
