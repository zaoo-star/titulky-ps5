---
lang: en
---
# ReCaption for Windows PCs (experimental)

A computer can be the reader for an **LG or Samsung TV** instead of an Android phone – handy especially
if you have an iPhone. The game goes from the PS5 to the TV by cable and you play on your 🟢 gaming account as always. The computer connects to the PS5
via Remote Play with the 🔵 account for ReCaption, reads the picture, translates and sends the finished lines to ReCaption on the TV.

You need: **Windows 10 or 11 (64-bit)** on the same home network as the PS5 and the TV, about 1.5 GB of space.
Mac: **[ReCaption for Mac](mac.md)** – the same app, the same steps.

<a id="stazeni"></a>
## 1. Download and run

1. **[Download ReCaption-PC.zip](https://recaption.pages.dev/d/pc/ReCaption-PC.zip)**
   (about 250 MB).
2. Extract the ZIP (right-click → **Extract All**), for example into Documents, and run **ReCaption.exe** in the folder.
   Nothing gets installed; deleting the folder removes ReCaption. New version = download and extract again, your settings are kept.
3. The app isn't signed, so Windows may show "Windows protected your PC" –
   **More info → Run anyway**.

<a id="povoleni"></a>
## 2. Permissions (first time only)

- **Firewall** – when Windows asks, choose **Allow access** (the PS5 and TV are searched for on the home network, and the TV and phone
  connect to the computer).
- Windows provides the game window's picture without any further permission.

<a id="priprava"></a>
## 3. Preparation (first time only)

On the **Home** page:

- **Translation · missing → Prepare** – downloads the translation model for the chosen language (about a minute).
- **chiaki-ng · missing → Download** – the Remote Play program the computer uses to connect to the PS5 (about 120 MB).

<a id="ctecka"></a>
## 4. TV reader

1. **⚙ Settings → Usage → TV reader** (on Windows the default is Play on PC).
2. **PS5 · not paired → Pair** – the 🔵 account for ReCaption (a second user on the PS5, see the [PS5 guide](ps5.md#ucet)):
   sign in with a phone (an iPhone works too) via the square, then on the PS5 under the 🔵 user go to **Settings → System →
   Remote Play → Pair Device** (in older system software Link Device) and type the eight-digit number into ReCaption.
   Then switch the PS5 back to the 🟢 gaming account.
3. ReCaption on the TV: with an Android phone follow [LG](lg.md) / [Samsung](samsung.md); from the computer only:
   [LG via webOS Dev Manager](lg.md#pocitac) / [Samsung via Apps2Samsung for computers](samsung.md#pocitac).
4. On the TV start **ReCaption** and select the HDMI input with the PS5 – the TV finds the computer on the network by itself.
5. **Start.** Then turn the PS5 on with the controller – the computer connects by itself; it doesn't wake the PS5 on its own. The chiaki-ng window can stay
   behind other windows, just **don't minimise it** (ReCaption reports a minimised window on Home with a Restore window button).
   Sound from the computer is muted.
6. The computer must not go to sleep: keep it plugged in and in ReCaption set **⚙ → Usage → Keep PC awake while translating · Yes** (while translation is running,
   the PC won't sleep and the display won't turn off; after Stop the Windows settings apply again).

<a id="hrani"></a>
## 5. Playing directly on the computer

**⚙ → Usage → Play on PC** and the **Play** button: the game runs in the chiaki-ng window on the computer with the 🟢 gaming account
and the translation appears in a bar over the game window (⚙ → Output: bar on the PC, the TV, or both).

---

## Common to Windows and Mac

<a id="ucet"></a>
### Which PlayStation account (by usage)

Each usage has its own pairing and its own account – switching in ⚙ → Usage deletes nothing.

| ⚙ → Usage | Where you play | Account when pairing | On the PS5 when pairing |
|---|---|---|---|
| **TV reader** | on the PS5, picture by cable to the TV | 🔵 **account for ReCaption** (second user on the PS5, [guide](ps5.md#ucet)) | switch to 🔵, Remote Play → Pair Device; then back to 🟢 |
| **Play on PC / on Mac** | in the chiaki-ng window on the computer | 🟢 **gaming account** – no second account needed here | under the 🟢 gaming user: Enable Remote Play and Pair Device |

- E-mail and password are typed only on Sony's page – on a phone via the square from the ReCaption window, or with the
  **Sign in in browser** button on this computer. The password is never typed into ReCaption or chiaki-ng.
- **Watch out with TV reader:** the computer's browser is often signed in with the 🟢 gaming account (from playing or from PlayStation Store)
  and Sony then doesn't ask for a password and takes that one. So sign in in a private/incognito window or with a phone. (A gaming account that
  has already been paired for Play on PC / on Mac is recognised by ReCaption itself: "Account · gaming account".)
- In the game on the PS5 (TV reader) you keep playing on your 🟢 gaming account. The paired 🔵 account connects to the PS5 by itself after Start.

<a id="rozdily"></a>
### Windows and Mac

The app window, the Home / Subtitles / ⚙ Settings pages, reading, translation, DeepL, game profiles and sending to the TV are the same.
Only what the system allows differs:

| | Windows | Mac |
|---|---|---|
| Computer | Windows 10 / 11, 64-bit | Apple Silicon (M1 and newer), macOS 12+ |
| Package | `ReCaption-PC.zip` → ReCaption.exe | `ReCaption-Mac.dmg` → Applications folder |
| Default usage | Play on PC | TV reader |
| Permissions | firewall | Screen Recording, Local Network |
| Minimised chiaki-ng window | ReCaption detects it, Restore window button | not detected – keep the window open |
| DeepL key | encrypted by Windows | Keychain |
| Stay awake while translating | ⚙ → Keep PC awake (PC won't sleep, display won't turn off) | ⚙ → Keep Mac awake (also keep the lid open) |
| Game recording to a folder | `zaznamy` next to the ReCaption folder | `~/Library/Application Support/ReCaptionTools/zaznamy` |
