---
lang: en
---
# LG TV – developer mode (first time only)

An LG TV can't read the game picture by itself: **ReCaption on an Android phone** (or an Android box,
or experimentally a computer – [Windows](pc.md) / [Mac](mac.md)) reads and translates, and sends the finished lines to the TV. So only a small ReCaption
app is put onto the TV – from the phone – and for that the TV needs **developer mode** turned on (free).

Where to do it in ReCaption on the phone: the guide, step **TV** → button **Install ReCaption on LG TV**
(later also from Home or Setup → chapter 3). The page on the phone shows the same steps as here and ticks off the finished ones by itself.

<a id="ucet"></a>
## 1. LG developer account (free)

👉 **[Open the LG developer site](https://webostv.developer.lge.com/)** → **Sign In** at the top right
→ **Create Account** (creates an LG account). An e-mail and password are enough; the account is free.

If the link doesn't work:
- [LG's guide to the Developer Mode app](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app)
- [Community guide by webOS Brew](https://www.webosbrew.org/devmode/)
- or search for: **"LG webOS TV developer account sign up"**

It's a good idea to note down the e-mail and password – on the TV you type them with the remote.

<a id="aplikace"></a>
## 2. The Developer Mode app on the TV

1. Press **Home** (the house) on the remote and open the **LG Content Store** (on newer TVs the **Apps** tile).
2. Search for **Developer Mode** with the magnifying glass and choose **Install**.
3. Start it (Home → app list → **Developer Mode**) and sign in with the account from step 1.
4. Turn on **Dev Mode Status** and confirm the restart. The TV restarts.

<a id="key-server"></a>
## 3. Key Server

After the restart open the **Developer Mode** app again and turn on **Key Server**. Its screen shows the TV's address
(**IP**) and the **Passphrase** (6 characters). On the phone, ReCaption reads them with the camera using the **📷** buttons under the fields – nothing
needs to be copied by hand (though you can type them manually).

## 4. Install ReCaption

On the **ReCaption on your LG TV** page on the phone choose **Install on TV** (for an older version **Update on TV**). ReCaption
is installed on the TV and started. Next time the Passphrase isn't needed.

## 5. What's next

1. In the guide on the phone choose **Continue**: Offline translation, Better translation (DeepL, optional) and in the
   **Connecting the PlayStation** step pair the PS5 with the 🔵 account for ReCaption ([PS5 guide](ps5.md#ucet)).
2. When playing: on the TV start **ReCaption** (Home → app list) and select the HDMI input with the PS5 in it; on the phone on Home choose
   **Start translation**. The phone finds the TV by itself – the same Wi‑Fi is enough.
3. **The phone's screen must not turn off.** With the screen off, a phone (especially Samsung) reads subtitles up to 10× slower and translation
   almost stops. In ReCaption: **⚙ Settings → General → Keep phone screen on** (the screen stays dimmed; the app asks about this
   the first time you start). Ideally keep the phone on a charger.

<a id="pocitac"></a>
## Without an Android phone: computer (experimental)

When the reader is ReCaption on a computer ([Windows](pc.md) / [Mac](mac.md)) and there's no Android phone (for example only an iPhone), the app is put
onto the TV from the computer with the free community program **webOS Dev Manager** (Windows / macOS / Linux):

1. Steps 1–3 above (LG account, Developer Mode, Dev Mode Status, Key Server).
2. The TV app file is inside ReCaption for Android: download
   [ReCaption.apk](https://recaption.pages.dev/d/latest/ReCaption.apk), rename it to
   `ReCaption.zip`, open it and copy **receiver.ipk** from the `assets\lg` folder, for example to the desktop.
3. 👉 [Download webOS Dev Manager](https://github.com/webosbrew/dev-manager-desktop/releases/latest)
   (Windows: the `…_x64_en-US.msi` file, Mac: `….dmg`) and install it.
4. In webOS Dev Manager add the TV (**Add device**: the address from the Developer Mode app, the Passphrase from the same screen),
   then **Apps** → **Install** → select `receiver.ipk`. The **ReCaption** app appears on the TV.

Updates work the same way (newer `ReCaption.apk` → new `receiver.ipk`). The webOS Dev Manager route hasn't been
tested with ReCaption yet – if it doesn't work, the [webOS Brew guide](https://www.webosbrew.org/devmode/) can help.

## Good to know
- Developer mode expires after a while (ReCaption shows how much time is left). It's extended on the TV:
  Developer Mode app → **Extend**, or ReCaption extends it by itself once a day with the option
  ⚙ → General → **Extend Developer Mode** (off by default). If it expires completely, ReCaption disappears from the TV – then repeat steps 2–4.
- One LG account can have developer mode on only one TV at a time.
