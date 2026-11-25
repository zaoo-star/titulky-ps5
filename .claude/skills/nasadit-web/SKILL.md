---
name: nasadit-web
description: Nasazení webu recaption.pages.dev (složka pomoc/) hned, bez vydání nové verze aplikace – když majitel řekne „nasaď web“, „dej to na web“, „podívám se“. Také jak ověřit, že je web živý.
---

# Nasazení webu

Web (`pomoc/`, Jekyll) se nasazuje z `main` přes `.github/workflows/pomoc.yml`: Cloudflare Pages (hlavní adresa
https://recaption.pages.dev) + záložní GitHub Pages. Push s `[skip ci]` nasazení přeskočí → spustit ručně.

## Rychle (se souhlasem majitele – jde to do main)
```bash
bash ci/nasad-web.sh rc-zkusebni "Web: co se mění"
```
Vezme `pomoc/` z větve, commitne ho na `origin/main` s `[skip ci]` (aplikace se nesestavuje), pushne a spustí pomoc.yml.

## Před nasazením
- `node ci/pruvodce-web.test.js` – projde všechny stránky průvodce (spouští se i v pomoc.yml).
- Průvodce se generuje ze šablony `core/Pruvodce.kt` → `pomoc/pruvodce-data.js`; po změně šablony
  `./gradlew -p ci/mistni-kontrola test -Ppruvodce` (přegeneruje) – ručně `pruvodce-data.js` neupravovat.
- Místní náhled: `python -m http.server <port> --bind 127.0.0.1` ve složce `pomoc` (prohlížeč: `http://localhost:<port>`).

## Ověření živého webu
`curl -s "https://recaption.pages.dev/<stránka>?n=$RANDOM" | grep -c "<nový text>"` – stránky jsou chvíli v mezipaměti.
