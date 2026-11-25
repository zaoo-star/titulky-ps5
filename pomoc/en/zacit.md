---
lang: en
---
# Getting started with ReCaption

ReCaption reads the English subtitles from a game on PlayStation 5 (or Xbox), translates them and shows them in a bar over the picture.
After you answer two questions, only the steps that apply to your setup are shown – split the same way as in the app’s
setup guide, each with a short video.

<style>
.volby{display:flex;flex-wrap:wrap;gap:8px;margin:8px 0 18px}
.volby button{font:600 16px system-ui,sans-serif;padding:10px 14px;border-radius:10px;border:2px solid #d0d7de;background:#f6f8fa;cursor:pointer}
.volby button.on{border-color:#f2c14e;background:#fff5d6}

/* steps as in the app (same as index.html) */
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

/* video player */
.prehravac{position:fixed;inset:0;z-index:50;background:rgba(0,0,0,.86);display:grid;place-items:center;padding:16px}
.prehravac[hidden]{display:none}
.prehravac-obal{position:relative;width:min(1000px,100%)}
.prehravac video{width:100%;max-height:80vh;border-radius:12px;background:#000;display:block}
.prehravac-nazev{color:#fff;font-weight:600;margin-top:10px}
.zavrit{position:absolute;right:0;top:-46px;width:38px;height:38px;border-radius:50%;border:0;background:#1a1f2b;color:#eef1f6;font-size:18px;cursor:pointer}
</style>

**Order in the app:** the ReCaption setup guide always starts with the **Languages** step (App language, Translate from, Translation language),
then **Device and TV**, **Console**, **Translation**, (for Android TV) **Showing subtitles** and **Connection**. Here you only
need to answer which TV you are setting up and what you are setting it up with.

**First, on the PS5:** ReCaption connects to the PS5 with a **🔵 second account** (the ReCaption account), not the 🟢 game account.
Creating it and turning on Remote Play under it: [PS5 guide](ps5.md#ucet) (about 5 minutes). Xbox: [Xbox guide](xbox.md).

**1. Which TV to set up?**
<div class="volby" id="tv">
<button data-v="atv">Android TV / Google TV<br><small>Sony, Philips, TCL, Chromecast, Shield…</small></button>
<button data-v="lg">LG (webOS)</button>
<button data-v="samsung">Samsung (Tizen)</button>
</div>

**2. What to set it up with?**
<div class="volby" id="na">
<button data-v="pc">💻 Computer</button>
<button data-v="android">📱 Android phone</button>
<button data-v="iphone">📱 iPhone</button>
<button data-v="tv">📺 Just the TV remote</button>
</div>

<p id="vyber" style="color:#57606a">Just pick both above.</p>

<div id="vysledek"></div>

<div class="prehravac" id="prehravac" hidden>
  <div class="prehravac-obal">
    <button class="zavrit" aria-label="Close">✕</button>
    <video id="prehravac-video" controls playsinline preload="none"></video>
    <div class="prehravac-nazev" id="prehravac-nazev"></div>
  </div>
</div>

<script>
(function () {
  // Steps as in the app (core/VideoSteps): clips pomoc/videa/krok-…-en.mp4, durations from videa/kroky.json.
  // This object mirrors KROKY in index.html, only split into the individual setup-guide steps and
  // worded by what you set up with (phone / computer / iPhone / just the TV remote).
  var DELKY = {}, JENCS = [];   // clips only in Czech for now (kroky.json "jen_cs") – shown in Czech
  function vj(id) { return JENCS.indexOf(id) < 0 ? 'en' : 'cs'; }

  var CESTY = [
    { tv: ['atv'], na: ['pc'], nazev: 'Android TV + 💻 computer',
      popis: 'ReCaption runs right on the TV. You set it up from a computer on the same network – the TV shows the address.',
      kroky: [
        { n: 'ReCaption on the TV', kde: 'TV', t: 'On the TV the <b>Downloader</b> app and code <b>8438934</b> – <a href="android-tv.html#downloader">step-by-step guide</a>. (With the <code>adb</code> tool: <code>adb connect &lt;TV address&gt;</code> and <code>adb install ReCaption.apk</code>.)', v: 'krok-instalace-downloader' },
        { n: 'Languages', kde: 'TV', t: 'The setup guide starts with languages and below them the option <b>Set up with a phone or computer (recommended)</b>.', v: 'krok-jazyky' },
        { n: 'Set up from the computer', kde: 'computer', t: 'On the same network, open in a browser the address the TV shows under the QR code (💻 http://192.168…). On the page: 1 · sign in with the 🔵 ReCaption account (copy the address of the almost empty “redirect” page and paste it back on the ReCaption page), 2 · the eight-digit number from the PS5, 3 · the DeepL key (optional). The TV pairs by itself and the list on the TV gets ticked off.' },
        { n: 'Offline translation', kde: 'TV', t: 'Continue on the TV → download the language → Better translation (DeepL, can be skipped).', v: 'krok-preklad' },
        { n: 'Showing subtitles', kde: 'TV', t: 'Open TV settings → Accessibility → ReCaption → turn on, then press Back to return to the app (<a href="android-tv.html#zobrazeni">where to find it</a>).', v: 'krok-zobrazeni-android' },
        { n: 'Play', kde: 'PS5', t: 'On the PS5 switch back to the 🟢 game account, set the TV to the PS5’s HDMI input, turn on English subtitles in the game, and press Start in ReCaption.' }
      ] },
    { tv: ['atv'], na: ['android'], nazev: 'Android TV + 📱 Android',
      popis: 'ReCaption on the TV and on the phone – the phone only helps with setup and sign-in.',
      kroky: [
        { n: 'ReCaption on the phone', kde: 'phone', t: '<a href="https://recaption.pages.dev/stahnout">Download ReCaption.apk</a> and install it (<a href="telefon.html#android">allow installing</a>). The phone opens <b>Sign-in for the TV</b> (the helper).' },
        { n: 'ReCaption on the TV from the phone', kde: 'phone + TV', t: 'In the helper, at the bottom of the card, the <b>Install ReCaption on Android TV</b> button – on the TV turn on network debugging once and confirm “Allow debugging?” (<a href="android-tv.html#telefon">guide</a>). Or without the phone via <a href="android-tv.html#downloader">Downloader</a>.' },
        { n: 'Languages', kde: 'TV', t: 'Choose <b>Set up with a phone or computer</b> → scan the QR code with the phone camera → under “How to continue?” choose <b>In the ReCaption app</b> (nothing to copy, the number from the PS5 is read with the camera) or <b>Continue in the browser</b>.', v: 'krok-jazyky' },
        { n: 'Offline translation', kde: 'TV', t: 'Continue on the TV → download the language → Better translation (DeepL, can be skipped).', v: 'krok-preklad' },
        { n: 'Showing subtitles', kde: 'TV', t: 'Accessibility → ReCaption → turn on (<a href="android-tv.html#zobrazeni">where to find it</a>).', v: 'krok-zobrazeni-android' },
        { n: 'Play', kde: 'PS5', t: 'On the PS5 back to the 🟢 game account, TV on the PS5’s HDMI input.' }
      ] },
    { tv: ['atv'], na: ['iphone'], nazev: 'Android TV + 📱 iPhone',
      popis: 'There is no ReCaption for iPhone and none is needed – the iPhone only helps with sign-in.',
      kroky: [
        { n: 'ReCaption on the TV', kde: 'TV', t: 'The <b>Downloader</b> app on the TV and code <b>8438934</b> – <a href="android-tv.html#downloader">guide</a>.', v: 'krok-instalace-downloader' },
        { n: 'Languages', kde: 'TV', t: 'Choose <b>Set up with a phone or computer</b> → scan the QR code with the iPhone Camera (same Wi-Fi). The <b>ReCaption · TV settings</b> page opens in Safari.', v: 'krok-jazyky' },
        { n: 'Sign-in and PS5', kde: 'iPhone', t: 'Sign in with the 🔵 ReCaption account (on the “redirect” page tap the address → <b>Copy</b> → back to the ReCaption tab), the number from the PS5 (hold your finger in the field → <b>Scan Text</b>) and the DeepL key. Without copying the “redirect” address: <a href="zkratka.html">the ReCaption shortcut for iPhone</a>.' },
        { n: 'Offline translation', kde: 'TV', t: 'Continue on the TV → download the language → Better translation (DeepL, can be skipped).', v: 'krok-preklad' },
        { n: 'Showing subtitles', kde: 'TV', t: 'Accessibility → ReCaption → turn on (<a href="android-tv.html#zobrazeni">where to find it</a>).', v: 'krok-zobrazeni-android' },
        { n: 'Play', kde: 'PS5', t: 'On the PS5 back to the 🟢 game account, TV on the PS5’s HDMI input.' }
      ] },
    { tv: ['atv'], na: ['tv'], nazev: 'Android TV + 📺 just the remote',
      popis: 'No phone or computer – the whole setup guide (9 steps) works with the TV remote.',
      kroky: [
        { n: 'ReCaption on the TV', kde: 'TV', t: 'The <b>Downloader</b> app and code <b>8438934</b> – <a href="android-tv.html#downloader">guide</a>.', v: 'krok-instalace-downloader' },
        { n: 'Languages', kde: 'TV', t: 'Keep <b>Set up right on the TV</b> and Continue.', v: 'krok-jazyky' },
        { n: 'Console', kde: 'TV', t: 'Choose PlayStation or Xbox.', v: 'krok-konzole' },
        { n: 'Offline translation', kde: 'TV', t: 'Download the language and Better translation (DeepL, can be skipped).', v: 'krok-preklad' },
        { n: 'Showing subtitles', kde: 'TV', t: 'Accessibility → ReCaption → turn on (<a href="android-tv.html#zobrazeni">where to find it</a>).', v: 'krok-zobrazeni-android' },
        { n: 'Connecting the PlayStation', kde: 'PS5 + phone', t: 'Only for signing in with the 🔵 PlayStation account you need a phone or computer once (Sony won’t allow sign-in on the TV) – the TV shows a QR code, the phone scans it with the camera. The eight-digit number from the PS5.', v: 'krok-pripojeni-android-ps', ps5: true },
        { n: 'Play', kde: 'PS5', t: 'Start on Home, on the PS5 back to the 🟢 game account, TV on the PS5’s HDMI input.' }
      ] },
    { tv: ['lg'], na: ['android'], nazev: 'LG + 📱 Android',
      popis: 'An LG TV can’t read the game picture by itself – ReCaption on the phone reads and translates and sends finished lines to the TV.',
      kroky: [
        { n: 'ReCaption on the phone', kde: 'phone', t: '<a href="https://recaption.pages.dev/stahnout">Download ReCaption.apk</a> and install it (<a href="telefon.html#android">allow installing</a>).' },
        { n: 'Developer Mode on the LG TV', kde: 'TV', t: 'Free, once: an LG developer account, the <b>Developer Mode</b> app on the TV, <b>Dev Mode Status</b> and <b>Key Server</b> – <a href="lg.html">guide</a>.' },
        { n: 'Sign-in for the TV', kde: 'phone', t: 'In the helper choose <b>Read for the TV</b> – the setup guide opens (9 steps).' },
        { n: 'Languages', kde: 'phone', t: 'App language, what to translate from, what to translate into.', v: 'krok-jazyky-lg' },
        { n: 'Device and TV', kde: 'phone + TV', t: 'In the Television step the <b>Install ReCaption on LG TV</b> button (the address and Passphrase are read with the camera from the TV screen).', v: 'krok-zarizeni-lg' },
        { n: 'Console', kde: 'phone', t: 'Choose PlayStation or Xbox.', v: 'krok-konzole-lg' },
        { n: 'Offline translation', kde: 'phone', t: 'Download the language and Better translation (DeepL, can be skipped).', v: 'krok-preklad-lg' },
        { n: 'Connecting the PlayStation', kde: 'phone + PS5', t: 'Sign in with the 🔵 ReCaption account and the eight-digit number from the PS5.', v: 'krok-pripojeni-lg-ps', ps5: true },
        { n: 'Play', kde: 'PS5', t: 'On the TV start ReCaption and choose the PS5’s HDMI input, on the phone press Start on Home; keep the phone on the charger.' }
      ] },
    { tv: ['samsung'], na: ['android'], nazev: 'Samsung + 📱 Android',
      popis: 'A Samsung TV can’t read the game picture by itself – ReCaption on the phone reads and translates and sends finished lines to the TV.',
      kroky: [
        { n: 'ReCaption on the phone', kde: 'phone', t: '<a href="https://recaption.pages.dev/stahnout">Download ReCaption.apk</a> and install it (<a href="telefon.html#android">allow installing</a>).' },
        { n: 'Sign-in for the TV', kde: 'phone', t: 'Choose <b>Read for the TV</b> – the setup guide opens (9 steps).' },
        { n: 'Languages', kde: 'phone', t: 'App language, what to translate from, what to translate into.', v: 'krok-jazyky-samsung' },
        { n: 'Device and TV', kde: 'phone + TV', t: 'In the Television step the <b>Install ReCaption on Samsung TV</b> button: Developer Mode on the TV and installing via <b>Apps2Samsung</b> – <a href="samsung.html">guide</a>.', v: 'krok-zarizeni-samsung' },
        { n: 'Console', kde: 'phone', t: 'Choose PlayStation or Xbox.', v: 'krok-konzole-samsung' },
        { n: 'Offline translation', kde: 'phone', t: 'Download the language and Better translation (DeepL, can be skipped).', v: 'krok-preklad-samsung' },
        { n: 'Connecting the PlayStation', kde: 'phone + PS5', t: 'Sign in with the 🔵 ReCaption account and the eight-digit number from the PS5.', v: 'krok-pripojeni-samsung-ps', ps5: true },
        { n: 'Play', kde: 'PS5', t: 'On the TV start ReCaption and choose the PS5’s HDMI input, on the phone press Start on Home; keep the phone on the charger.' }
      ] },
    { tv: ['lg', 'samsung'], na: ['pc', 'iphone'], nazev: 'LG / Samsung + 💻 Windows PC or Mac (experimental)',
      popis: 'LG and Samsung TVs can’t read the game picture by themselves – a device next to them reads and translates. Without an Android phone this can be a Windows PC or a Mac with Apple Silicon (M1 and newer) on the same network (an iPhone can’t be the reader, it only helps with sign-in).',
      kroky: [
        { n: 'ReCaption on the computer', kde: 'computer', t: 'Windows: the ZIP, then run ReCaption.exe (<a href="pc.html">guide</a>). Mac: the DMG, drag it to Applications and allow Screen Recording (<a href="mac.html">guide</a>). On Home choose Prepare and Download, then ⚙ Settings → Usage → <b>TV reader</b> (the default on a Mac).' },
        { n: 'Pair the PS5', kde: 'computer + PS5', t: 'Next to the <b>PS5 · not paired</b> line choose <b>Pair</b> – the 🔵 ReCaption account (sign in with a phone via the QR code, an iPhone works too) and the eight-digit number from the PS5.', ps5: true },
        { n: 'ReCaption on the TV from the computer', kde: 'computer + TV', t: 'LG – Developer Mode and webOS Dev Manager (<a href="lg.html#pocitac">guide</a>) / Samsung – Apps2Samsung for computer (<a href="samsung.html#pocitac">guide</a>).' },
        { n: 'Play', kde: 'PS5', t: 'On the TV start ReCaption, on the computer press Start and turn on the PS5 with the controller.' }
      ],
      pozn: 'With an Android phone (even an older one, Android 8+) it’s easier – the 📱 Android path.' },
    { tv: ['lg', 'samsung'], na: ['tv'], nazev: 'LG / Samsung with just the remote',
      popis: 'LG and Samsung TVs can’t read the game picture by themselves and ReCaption is installed on them from another device – it can’t be done with the remote alone.',
      kroky: [
        { n: 'A second device is needed', kde: 'phone / box / computer', t: 'Reading and translating must be done by an <b>Android phone</b> (even an older one, Android 8+), an Android TV box, or a computer (<a href="pc.html">Windows</a> / <a href="mac.html">Mac</a>) on the same network. Then follow its path: choose 📱 Android or 💻 Computer above.' }
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
      if (s.ps5) { var a5 = el('a', 'maly', 'How to prepare the PS5 →'); a5.href = 'ps5.html'; txt.appendChild(a5); }
      li.appendChild(txt);
      if (s.v) {
        var a = el('a', 'klip'); a.href = '../videa/' + s.v + '-' + vj(s.v) + '.mp4'; a.setAttribute('data-video', s.v);
        a.setAttribute('data-nazev', s.n); a.style.backgroundImage = 'url(../videa/' + s.v + '-' + vj(s.v) + '.jpg)';
        if (DELKY[s.v]) a.appendChild(el('span', null, DELKY[s.v]));
        li.appendChild(a);
      } else li.appendChild(el('div', 'bez', s.n === 'Play' ? '🎮' : ''));
      ol.appendChild(li);
    }
    box.appendChild(ol);
    if (c.pozn) box.appendChild(el('p', 'vybrano-pozn', c.pozn));
  }

  try {
    var dreq = new XMLHttpRequest();
    dreq.open('GET', '../videa/kroky.json');
    dreq.onload = function () { try { var k = JSON.parse(dreq.responseText); DELKY = k.klipy || {}; JENCS = k.jen_cs || []; if (vyber.tv && vyber.na) kresli(vyber.tv, vyber.na); } catch (e) {} };
    dreq.send();
  } catch (e) {}

  // two questions → find and draw the path; one report to the server per combination per visit
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

  // video player (the link works without the script too – it opens the video)
  var box = document.getElementById('prehravac'), vid = document.getElementById('prehravac-video');
  function zavri() { vid.pause(); vid.removeAttribute('src'); vid.load(); box.hidden = true; }
  document.addEventListener('click', function (e) {
    var a = e.target.closest ? e.target.closest('[data-video]') : null;
    if (!a) return;
    e.preventDefault();
    var id = a.getAttribute('data-video');
    vid.poster = '../videa/' + id + '-' + vj(id) + '.jpg';
    vid.src = '../videa/' + id + '-' + vj(id) + '.mp4';
    document.getElementById('prehravac-nazev').textContent = a.getAttribute('data-nazev') || (a.textContent || '').trim();
    box.hidden = false;
    var p = vid.play(); if (p && p.catch) p.catch(function () {});
  });
  box.addEventListener('click', function (e) { if (e.target === box || e.target.className === 'zavrit') zavri(); });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && !box.hidden) zavri(); });
})();
</script>

## More TVs or PlayStations

- **Two Android TVs:** install ReCaption on each one separately – each has its own settings and its own **Set up from phone** QR code
  (⚙ Settings → General, or the Console page).
- **Two LG / Samsung TVs, one phone:** the phone remembers the subtitle look for each TV separately.
- **Two PlayStations:** in ReCaption on the Console page **Pair another PS5**, switch with the **Active PS5** line.

## All guides

- [PlayStation 5 – 🔵 ReCaption account and settings](ps5.md)
- [Android TV / Google TV – first install of ReCaption](android-tv.md)
- [LG TVs – Developer Mode](lg.md)
- [Samsung TVs](samsung.md)
- [Phone (Android and iPhone)](telefon.md) · [iPhone: the ReCaption shortcut](zkratka.md)
- Computer – reader for LG / Samsung TVs (experimental): [Windows](pc.md) · [Mac](mac.md)
- [Xbox](xbox.md)
- [DeepL – better translation](deepl.md)

The QR codes in the app lead to these pages too. When LG, Sony or Samsung move their pages,
the link is fixed here and the app doesn’t need to change.
