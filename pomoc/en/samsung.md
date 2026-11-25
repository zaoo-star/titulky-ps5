---
lang: en
---
# Samsung TV

A Samsung TV can't read the game picture by itself: **ReCaption on an Android phone** (or an Android box,
or experimentally a computer – [Windows](pc.md) / [Mac](mac.md)) reads and translates, and sends the finished lines to the TV. Only a small ReCaption app is put onto the TV.

Samsung only lets your own app onto the TV in developer mode, and only when it's signed for that particular
TV. The free **Apps2Samsung** app on an Android phone takes care of both – no computer needed,
about 10 minutes. In ReCaption on the phone: the guide, step **TV** (Samsung selected) → button
**Install ReCaption on Samsung TV** (later also from Home or Setup → chapter 3). The page walks you through everything
and recognises by itself when ReCaption on the TV reports in.

<a id="vyvojarsky"></a>
## 1. Developer mode on the TV

On the remote press **Home** → the **Apps** tile → there, enter **1 2 3 4 5** on the remote (remote without numbers:
the **123** button and the numbers on screen) → Developer mode window: **On** → in **Host PC IP** enter the address of the **phone**
that will run Apps2Samsung (not a computer). The ReCaption page on the phone shows the address (the line **Host PC IP · …**);
otherwise it's on the phone under: Settings → Wi‑Fi → connected network → details (IP address). **OK** and restart the TV:
hold the power button on the remote until the TV turns off and starts again. After the restart, Apps shows
**Developer mode** at the top.

<a id="apps2samsung"></a>
## 2. Apps2Samsung on the phone (Android)

👉 [Download Apps2Samsung (releases page, the `…-android.apk` file)](https://github.com/Apps2Samsung/Apps2Samsung/releases/latest)

Fallback options:
- [Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/Apps2Samsung/Apps2Samsung) – keeps track of updates itself,
- F-Droid / Droid-ify / Neo Store: add the repository `https://apps2samsung.com/fdroid/repo`,
- home page: [apps2samsung.com](https://apps2samsung.com) · source: [github.com/Apps2Samsung/Apps2Samsung](https://github.com/Apps2Samsung/Apps2Samsung).

You need version **3.3.1 or newer** (it supports the link for adding ReCaption). On an iPhone, Apps2Samsung only works
via SideStore (once, with a computer and a cable) – the guide is on the Apps2Samsung site.

<a id="zdroj"></a>
## 3. ReCaption into Apps2Samsung

In the ReCaption guide press **Add ReCaption to Apps2Samsung** (on a TV, scan the square
with the phone) and confirm adding it in Apps2Samsung. Or use this link:

👉 [Add ReCaption to Apps2Samsung](https://apps2samsung.com/source?url=https%3A%2F%2Fgithub.com%2Fadusa89%2FReCaption%2Freleases%2Ftag%2Flatest)

Manually: in Apps2Samsung **⋯ → Add a source** and paste the address
`https://github.com/adusa89/ReCaption`

<a id="instalace"></a>
## 4. Install

In Apps2Samsung select the TV and choose **Install** next to ReCaption. The first time you need to sign in with a Samsung
account (free) – Apps2Samsung uses it to create a signature for this TV and reuses it next time.
Then start ReCaption on the TV; the guide in ReCaption shows "done".

**Updates:** when ReCaption reports an older app on the TV, just choose **Update** next to ReCaption in Apps2Samsung
(same Samsung account as last time).

If something doesn't work, Apps2Samsung tells you itself what the TV is missing (developer mode, Host PC IP…).

<a id="dal"></a>
## 5. What's next

1. In the ReCaption guide on the phone choose **Continue**: Offline translation, Better translation (DeepL, optional) and in the
   **Connecting the PlayStation** step pair the PS5 with the 🔵 account for ReCaption ([PS5 guide](ps5.md#ucet)).
2. When playing: on the TV start **ReCaption** (Home → Apps) and select the HDMI input with the PS5 in it; on the phone on Home choose
   **Start translation**. The phone finds the TV by itself – the same Wi‑Fi is enough.
3. **The phone's screen must not turn off.** With the screen off, a phone (especially Samsung) reads subtitles up to 10× slower and translation
   almost stops. In ReCaption: **⚙ Settings → General → Keep phone screen on** (the screen stays dimmed; the app asks about this
   the first time you start). Ideally keep the phone on a charger.

<a id="pocitac"></a>
## Without an Android phone: computer (Tizen Studio)

The reader is then ReCaption on a computer ([Windows](pc.md) / [Mac](mac.md), experimental), or an Android box.

1. Developer mode as in step 1, but in **Host PC IP** enter the address of the **computer** (Windows: Settings → Network & internet →
   Wi‑Fi or Ethernet → Properties → IPv4 address; Mac: System Settings → Wi‑Fi → Details → TCP/IP → IP address).
2. Easiest: [Apps2Samsung for computers](https://github.com/Apps2Samsung/Apps2Samsung/releases/latest)
   (Windows, macOS, Linux) – add the ReCaption source as in step 3 (**⋯ → Add a source** and the address
   `https://github.com/adusa89/ReCaption`) and continue as in step 4.
3. Otherwise Tizen Studio: download [ReCaption-Samsung.wgt](https://recaption.pages.dev/d/latest/ReCaption-Samsung.wgt)
   (in ReCaption for Android also via the Samsung guide → **No Android phone: computer** → Save file) and
   👉 [install Tizen Studio (Samsung)](https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/installing-tv-sdk.html)
   – if the link doesn't work, search for **"Tizen Studio install TV SDK"**. In Tizen Studio sign the file with
   a Samsung certificate and install it on the TV (the first time takes 30–60 minutes).
