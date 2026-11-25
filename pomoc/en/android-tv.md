---
lang: en
---
# Android TV / Google TV – installing ReCaption for the first time

ReCaption only needs to be put onto a TV or Android box **once**. After that, new versions install
from inside the app: the Home page shows **Update** – just choose it and confirm the installation.
Pairing and settings are kept.

**Code for Downloader:** **8438934** · short address (fallback): `recaption.pages.dev/tv`
**Direct link to the app:** https://recaption.pages.dev/d/latest/ReCaption.apk

Three ways, you only need one: [Downloader on the TV](#downloader) (remote only), [from an Android phone](#telefon),
[USB flash drive](#usb). Then the [first start](#prvni-spusteni).

**Where to find things on the TV** (names differ slightly between manufacturers):

| | Google TV (Chromecast, newer Sony, TCL, Philips…) | Older Android TV (Shield, older Sony, Philips…) |
|---|---|---|
| Settings | gear icon at the top right of Home | gear icon at the top right of Home |
| Build number | System → About → **Android TV OS build** | Device Preferences → About → **Build** |
| Developer options | System → **Developer options** | Device Preferences → **Developer options** |
| Unknown sources | Apps → Security & restrictions → **Unknown sources** | Device Preferences → Security & restrictions → **Unknown sources** |
| Accessibility | System → **Accessibility** | Device Preferences → **Accessibility** |
| TV address (IP) | Network & Internet → connected network → **IP address** | Network → connected network → **IP address** |

<a id="instalace"></a>
<a id="downloader"></a>
## Way 1: the Downloader app (no computer, no phone)

The free **Downloader** app (by AFTVnews, orange icon with an arrow) downloads ReCaption straight
onto the TV. Nothing is sent from a phone or copied to a flash drive.

### 1. Install Downloader

1. Press Home on the remote and open **Google Play** (on Google TV: the **Apps** tab → search).
2. Search for **Downloader** – the right one is by **AFTVnews** (not apps with similar names).
3. Choose **Install** and then **Open**.
4. On first start Downloader may ask for access to files / media → **Allow**.
   Close the "what's new" window with **OK**.

### 2. Download ReCaption

1. In Downloader, **Home** is selected on the left and there is an address box on the right. Select it and a
   keyboard appears.
2. Type the code **8438934** and choose **Go**. (Fallback if the code doesn't work: the address **`recaption.pages.dev/tv`**
   – without `https://`; capital letters don't matter.)
3. The download starts by itself (a percentage is shown). It takes a moment – the file is tens of MB.
   - If instead of downloading you see a page saying "Downloading ReCaption…", choose the yellow
     **ReCaption.apk** link on it.
   - If nothing works, type the full direct link above instead of the short address.

### 3. Allow Downloader to install apps (first time only)

Android only installs an app from another app that has been allowed to do so. So after the download a
security window usually appears:

- **Google TV** (Chromecast with Google TV, Google TV Streamer, newer Sony, TCL, Philips TVs…):
  choose **Settings** in the window, in the "Install unknown apps" list switch **Downloader** to
  **Allowed** and go back with Back.
- **Older Android TV** (and when the window doesn't appear): Settings → Device Preferences (on some TVs Apps) →
  **Security & restrictions** → **Unknown sources** → turn it on for **Downloader**.
- If the item isn't there, it is hidden in the developer options: select the build number seven times in a row
  (Google TV: Settings → System → About → **Android TV OS build**; older Android TV:
  Settings → Device Preferences → About → **Build**) until "You are now a developer" appears. Then go to
  Security & restrictions → Unknown sources again.

Names differ slightly between manufacturers; just look for "unknown apps" or "unknown sources".

### 4. Install

1. Back in Downloader, the system asks "Do you want to install this application?" → **Install**.
   (If it doesn't ask, download the file in Downloader again – now it will.)
2. A **Play Protect** window may appear – see [If the installation fails](#play-protect).
3. After "App installed" choose **Open** – ReCaption starts and guides you through the first
   setup ([first start](#prvni-spusteni)).
4. Downloader then offers to delete the downloaded file → **Delete** (saves space, it's no longer needed).

### 5. How to open ReCaption next time

- **Google TV:** Home → **Apps** (or the "Your apps" row) → ReCaption.
- **Older Android TV:** Home → the **Apps** row → ReCaption. You can move the icon
  to the front there (hold OK → Move).

After that you don't need Downloader any more – keep it or uninstall it. **Updates then happen
inside ReCaption** (Home → Update). On the first update the TV asks once for permission to install
from ReCaption – allow it the same way as for Downloader above.

<a id="telefon"></a>
## Way 2: from an Android phone (no Downloader)

1. Install ReCaption on the phone: [ReCaption.apk](https://recaption.pages.dev/d/latest/ReCaption.apk)
   ([allow the installation](telefon.md#android)). On the phone it opens **Sign-in for the TV**.
2. **Turn on network debugging on the TV once:** select the build number seven times (Google TV: Settings → System → About →
   **Android TV OS build**; older Android TV: Settings → Device Preferences → About →
   **Build**) until "You are now a developer" appears → back → **Developer options** (System, or Device Preferences) →
   turn on **USB debugging** (or **Network debugging** / **ADB**).
   Fire TV: My Fire TV → Developer Options → **ADB debugging**.
3. On the phone, in **Sign-in for the TV**, at the bottom of the card "I have an LG or Samsung TV" choose **Install ReCaption on Android TV**.
   The phone finds the TV on the network by itself (or copy the TV's address from the TV: Settings → Network & Internet → connected network → IP address).
4. The TV asks **"Allow debugging?"** – tick **Always allow from this computer** and press **OK**. ReCaption is installed
   and started. You can then turn debugging off again on the TV – new versions install from the app by themselves.

Newer Google TVs that only offer **Wireless debugging** with a pairing code can't use this way yet –
use Downloader above.

<a id="usb"></a>
## Way 3: USB flash drive

Download the APK from the direct link above on a phone or computer, copy it to a flash drive, plug that into
the TV and open the APK with a file manager (e.g. **File Commander** from Google Play). The
"Unknown sources" permission then goes to the file manager instead of Downloader.

<a id="prvni-spusteni"></a>
## First start – the guide on the TV

The guide has 9 steps: **Languages** → Device → Console → TV → Offline translation → Better translation (DeepL) →
Showing subtitles → Connecting the PlayStation → Done.

- In the **Languages** step, below the languages, you choose how to continue: **Set up with a phone or computer (recommended)** – sign-in,
  PS5 pairing and DeepL on one page on a phone or computer – or **Set up right on the TV** (everything with the remote).
  Which way suits which device: [Getting started with ReCaption](zacit.md).
- **Device:** Android TV or Android box. **TV:** Android TV.
- **Connecting the PlayStation:** you need a 🔵 account for ReCaption on the PS5 first – [PS5 guide](ps5.md#ucet).

<a id="zobrazeni"></a>
### Showing subtitles (Accessibility)

For ReCaption to draw its bar over the game, it has to be allowed in Accessibility once:

1. In the **Showing subtitles** step choose **Open TV settings** – the Accessibility list opens.
   (Manually: Google TV Settings → System → **Accessibility**; older Android TV Settings → Device Preferences →
   **Accessibility**.)
2. At the bottom of the list choose **ReCaption** → turn it on → confirm the system prompt (**OK** / **Allow**).
3. Press **Back** on the remote to return to ReCaption – the line gets ticked and the guide continues.

![The Showing subtitles step in the guide](../obrazky/pruvodce-zobrazeni-titulku.png)
*Step 7 of the guide: **Open TV settings** and **Continue without it**.*

If the TV has no Accessibility screen, ReCaption tries **Showing over other apps** (⚙ Settings → General).
After a ReCaption update you may need to turn Accessibility for ReCaption off and on again.

<a id="play-protect"></a>
## If the installation fails

- A **Play Protect** window ("App blocked" / "Unknown app"): choose
  **More details** → **Install anyway**. ReCaption is not on Google Play, so Play Protect
  doesn't know it – that's fine.
- If that option isn't there: Google Play → account icon → **Play Protect** → gear icon → turn off
  "Scan apps with Play Protect", install, and turn scanning back on.
- **"App not installed":** not enough space – delete something you don't need on the TV (including old files
  in Downloader: **Files** on the left). Restarting the TV also helps.
- **"App conflicts with an existing package":** the TV has an old version with a different signature
  (versions up to 0.60). Uninstall it (Settings → Apps → ReCaption → Uninstall) and install again.
- **Downloader shows a download error:** check that the TV is connected to the internet and try
  the direct link instead of the short address.
