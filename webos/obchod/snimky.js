// Snímky obrazovky pro obchod LG (1920×1080, česky a anglicky) ze současné aplikace v app/.
// Spuštění: NODE_PATH=$(npm root -g) node webos/obchod/snimky.js   (Playwright s Chromiem)
// Výsledek přepíše webos/obchod/snimky/<n>-<obrazovka>-<cs|en>.png.
// Obrazovka hry nemá v počítači žádný vstup HDMI, proto se jen pro snímek podloží
// stejnou nakreslenou „scénou“ jako Ukázka (na televizi je pod stránkou obraz z HDMI).
const path = require('path');
const { chromium } = require('playwright');

const SLOZKA = __dirname;
const SCENA = 'html.s-hra, body.s-hra { background: linear-gradient(#27405C, #16222F 55%, #2C2A22 56%, #14130F) !important; }';
const SNIMKY = [
  // [soubor, značka v adrese, otevřít nabídku OK]
  ['1-domu', 'domu', false],
  ['2-ukazka', 'ukazka', false],
  ['3-napoveda', 'napoveda', false],
  ['4-hdmi', 'hra', true]
];

(async () => {
  const prohlizec = await chromium.launch(process.env.PW_CHANNEL ? { channel: process.env.PW_CHANNEL } : {});
  for (const jazyk of ['cs', 'en']) {
    for (const [soubor, znacka, nabidka] of SNIMKY) {
      const stranka = await prohlizec.newPage({ viewport: { width: 1920, height: 1080 } });
      await stranka.goto('file://' + path.join(SLOZKA, 'app', 'index.html') + '#' + jazyk + '-' + znacka);
      await stranka.waitForTimeout(1200);
      if (nabidka) {
        await stranka.addStyleTag({ content: SCENA });
        await stranka.keyboard.press('Enter');
        await stranka.waitForTimeout(800);
      }
      await stranka.screenshot({ path: path.join(SLOZKA, 'snimky', soubor + '-' + jazyk + '.png') });
      await stranka.close();
    }
  }
  await prohlizec.close();
})();
