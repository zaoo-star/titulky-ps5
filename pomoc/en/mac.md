---
lang: en
---
# ReCaption for Mac (experimental)

A Mac can be the reader for an **LG or Samsung TV** instead of an Android phone – handy especially
if you have an iPhone. The game goes from the PS5 to the TV by cable and you play on your 🟢 gaming account as always. The Mac connects to the PS5
via Remote Play with the 🔵 account for ReCaption, reads the picture, translates and sends the finished lines to ReCaption on the TV.

You need: **a Mac with Apple Silicon (M1 and newer), macOS 12 or newer** on the same home network as the PS5 and the TV,
about 2 GB of space. Macs with an Intel processor aren't supported yet.
Windows: **[ReCaption for Windows PCs](pc.md)** – the same app, the same steps.

<a id="stazeni"></a>
## 1. Download and run

1. **ReCaption-Mac.dmg isn't available for download yet** – the Mac version is still being prepared. The link will appear here once it's ready.
   <!-- after the first "mac" release restore: **[Download ReCaption-Mac.dmg](https://recaption.pages.dev/d/mac/ReCaption-Mac.dmg)**. -->
2. Open the file and **drag ReCaption into the Applications folder**, then run it from there.
   New version = download and drag it in again, your settings are kept.
3. The app isn't signed by Apple, so macOS refuses it the first time:
   **System Settings → Privacy & Security →** at the bottom next to "ReCaption" choose **Open Anyway**.
   (Older macOS: right-click ReCaption → **Open** → **Open**.)

<a id="povoleni"></a>
## 2. Permissions (first time only)

- **Local Network** and **incoming connections** – when macOS asks, choose **Allow** (the PS5 and TV are searched for on the home network,
  and the TV and phone connect to the Mac).
- **Screen Recording** – without it the Mac won't provide the game window's picture. On the Home page, the line
  "Screen Recording · permission missing" → **Allow**, turn ReCaption on in System Settings and open the app
  again. After downloading a new version of ReCaption the permission has to be given again.

<a id="priprava"></a>
## 3. Preparation (first time only)

On the **Home** page:

- **Translation · missing → Prepare** – downloads the translation model for the chosen language (about a minute).
- **chiaki-ng · missing → Download** – the Remote Play program the Mac uses to connect to the PS5.

<a id="ctecka"></a>
## 4. TV reader

1. **⚙ Settings → Usage → TV reader** (the default on a Mac, usually nothing to change).
2. **PS5 · not paired → Pair** – the 🔵 account for ReCaption (a second user on the PS5, see the [PS5 guide](ps5.md#ucet)):
   sign in with a phone (an iPhone works too) via the square, then on the PS5 under the 🔵 user go to **Settings → System →
   Remote Play → Pair Device** (in older system software Link Device) and type the eight-digit number into ReCaption.
   Then switch the PS5 back to the 🟢 gaming account.
3. ReCaption on the TV: with an Android phone follow [LG](lg.md) / [Samsung](samsung.md); from the Mac only:
   [LG via webOS Dev Manager](lg.md#pocitac) / [Samsung via Apps2Samsung for computers](samsung.md#pocitac).
4. On the TV start **ReCaption** and select the HDMI input with the PS5 – the TV finds the Mac on the network by itself.
5. **Start.** Then turn the PS5 on with the controller – the Mac connects by itself; it doesn't wake the PS5 on its own. The chiaki-ng window can stay
   behind other windows, just **don't minimise it** (the Mac can't detect a minimised window and ReCaption won't report it).
   Sound from the Mac is muted.
6. The Mac must not go to sleep: keep it plugged in with the lid open and in ReCaption set **⚙ → Usage → Keep Mac awake while translating · Yes**
   (while translation is running, the Mac won't sleep and the display won't turn off; after Stop the macOS settings apply again).

<a id="hrani"></a>
## 5. Playing directly on the Mac

**⚙ → Usage → Play on Mac** and the **Play** button: the game runs in the chiaki-ng window on the Mac with the 🟢 gaming account
and the translation appears in a bar over the game window (⚙ → Output: bar on the Mac, the TV, or both).

---

## Common to Windows and Mac

- **[Which PlayStation account (by usage)](pc.md#ucet)** – TV reader with the 🔵 account for ReCaption, playing on the Mac
  with the 🟢 gaming account; both pairings are kept side by side.
- **[Windows and Mac – how they differ](pc.md#rozdily)**.
