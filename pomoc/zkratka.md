# iPhone: zkratka „ReCaption“ – bez kopírování adresy

<p id="stav" style="font-weight:600"></p>

Při přihlášení účtem 🔵 **pro ReCaption** skončí Sony na skoro prázdné stránce „redirect“. Její adresa
by se jinak musela zkopírovat a vložit. Se zkratkou stačí na té stránce klepnout na **Sdílet → ReCaption**
a adresa dojde do TV sama.

<a id="pridat"></a>
## 1. Přidat zkratku (jen jednou)

<p id="icloud"></p>

Když tlačítko výš chybí, zkratka se vytvoří ručně (asi minuta):

1. Otevřít aplikaci **Zkratky** → **+** (nová zkratka) a pojmenovat ji **ReCaption**.
2. Klepnout na **ⓘ** (podrobnosti) → zapnout **Zobrazit v listu sdílení** → přijímat jen **URL adresy**.
3. Přidat akci **Zakódovat URL** (URL Encode) – vstup: **Vstup zkratky**.
4. Přidat akci **Otevřít URL** a jako adresu napsat
   `https://recaption.pages.dev/zkratka.html?u=` a za ni vložit proměnnou **Zakódovaný text**.
5. Hotovo – uložit.

<a id="tv"></a>
## 2. Propojit zkratku s TV (jen jednou)

Na stránce **ReCaption · nastavení TV** (čtvereček v TV, Android TV) klepnout na **iPhone bez kopírování:
zkratka ReCaption**. Otevře se tahle stránka a nahoře se ukáže „✔ TV je uložená“.

## 3. Při přihlášení

1. Na stránce ReCaption klepnout na **Přihlásit se u PlayStation** a přihlásit se 🔵 účtem pro ReCaption
   (ne 🟢 herním).
2. Na skoro prázdné stránce „redirect“ klepnout na **Sdílet** (čtvereček se šipkou) → **ReCaption**.
3. Otevře se stránka z TV a účet se ověří sám.

<script>
(function () {
  var KEY = 'recaption_tv';
  var stav = document.getElementById('stav');
  function q(name) {
    var m = new RegExp('[?&]' + name + '=([^&]+)').exec(location.search);
    return m ? decodeURIComponent(m[1]) : null;
  }
  function domaci(url) {
    // jen stránka z TV v domácí síti (http://192.168.x.x:port/p/klic) – nikam jinam adresu nepošleme
    return /^http:\/\/(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)[0-9.]+:\d+\/p\/[A-Za-z0-9]+$/.test(url || '');
  }
  var store = null;
  try { store = window.localStorage; } catch (e) {}
  var tv = q('tv');
  if (tv && domaci(tv)) {
    try { store.setItem(KEY, tv); } catch (e) {}
    stav.textContent = '✔ TV je uložená – zkratka teď pošle adresu do ní. / TV saved.';
  }
  var u = q('u');
  if (u) {
    var saved = null;
    try { saved = store.getItem(KEY); } catch (e) {}
    if (saved && domaci(saved) && u.indexOf('code=') >= 0) {
      stav.textContent = '⏳ Posílám do TV… / Sending to the TV…';
      location.replace(saved + '?u=' + encodeURIComponent(u));
    } else if (!saved) {
      stav.textContent = '○ Zkratka ještě nezná TV – nejdřív krok 2. / Do step 2 first.';
    } else {
      stav.textContent = '○ To není adresa stránky „redirect“. / Not a “redirect” address.';
    }
  }
  // odkaz na hotovou zkratku z iCloudu – doplní ho majitel, až ji vytvoří (viz CLAUDE.md, Přihlášení přes telefon)
  var ICLOUD = '';
  if (ICLOUD) document.getElementById('icloud').innerHTML = '<a href="' + ICLOUD + '"><b>➕ Přidat zkratku ReCaption</b></a>';
})();
</script>

---
*[English version →](en/zkratka.md)*
