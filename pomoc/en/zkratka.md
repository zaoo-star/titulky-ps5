---
lang: en
---
# iPhone: the “ReCaption” shortcut – no copying the address

<p id="stav" style="font-weight:600"></p>

When you sign in with the 🔵 **ReCaption** account, Sony ends up on an almost empty “redirect” page. Its address
would otherwise have to be copied and pasted. With the shortcut you just tap **Share → ReCaption** on that page
and the address goes to the TV by itself.

<a id="pridat"></a>
## 1. Add the shortcut (only once)

<p id="icloud"></p>

If there is no button above, create the shortcut by hand (takes about a minute):

1. Open the **Shortcuts** app → **+** (new shortcut) and name it **ReCaption**.
2. Tap **ⓘ** (details) → turn on **Show in Share Sheet** → accept only **URLs**.
3. Add the **URL Encode** action – input: **Shortcut Input**.
4. Add the **Open URL** action and as the address type
   `https://recaption.pages.dev/zkratka.html?u=` and right after it insert the **Encoded Text** variable.
5. Done – save it.

<a id="tv"></a>
## 2. Link the shortcut to your TV (only once)

On the **ReCaption · TV settings** page (the QR code on the TV, Android TV) tap **iPhone without copying:
the ReCaption shortcut**. This page opens and “✔ TV saved” appears at the top.

## 3. When signing in

1. On the ReCaption page tap **Sign in to PlayStation** and sign in with the 🔵 ReCaption account
   (not the 🟢 game account).
2. On the almost empty “redirect” page tap **Share** (the square with an arrow) → **ReCaption**.
3. The page from the TV opens and the account is verified by itself.

<script>
(function () {
  var KEY = 'recaption_tv';
  var stav = document.getElementById('stav');
  function q(name) {
    var m = new RegExp('[?&]' + name + '=([^&]+)').exec(location.search);
    return m ? decodeURIComponent(m[1]) : null;
  }
  function domaci(url) {
    // only the TV page on the home network (http://192.168.x.x:port/p/key) – the address is never sent anywhere else
    return /^http:\/\/(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)[0-9.]+:\d+\/p\/[A-Za-z0-9]+$/.test(url || '');
  }
  var store = null;
  try { store = window.localStorage; } catch (e) {}
  var tv = q('tv');
  if (tv && domaci(tv)) {
    try { store.setItem(KEY, tv); } catch (e) {}
    stav.textContent = '✔ TV saved – the shortcut will now send the address to it.';
  }
  var u = q('u');
  if (u) {
    var saved = null;
    try { saved = store.getItem(KEY); } catch (e) {}
    if (saved && domaci(saved) && u.indexOf('code=') >= 0) {
      stav.textContent = '⏳ Sending to the TV…';
      location.replace(saved + '?u=' + encodeURIComponent(u));
    } else if (!saved) {
      stav.textContent = '○ The shortcut doesn’t know your TV yet – do step 2 first.';
    } else {
      stav.textContent = '○ That is not a “redirect” page address.';
    }
  }
  // link to the ready-made shortcut on iCloud – the owner fills it in once it exists
  var ICLOUD = '';
  if (ICLOUD) document.getElementById('icloud').innerHTML = '<a href="' + ICLOUD + '"><b>➕ Add the ReCaption shortcut</b></a>';
})();
</script>
