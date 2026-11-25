---
name: vydani
description: Vydání nové verze ReCaptionu – zařazení větve od jiné session, zvýšení verze, místní zkoušky, odeslání do zkušebního kanálu nebo do ostré a hlídání sestavení. Použij vždy, když se má něco zařadit, vydat, „poslat do zkušební / ostré“ nebo zvednout číslo verze.
---

# Vydání ReCaptionu

Vydání a čísla verzí dělá jen **koordinační sezení** (majitel to tak chce). Ostatní sezení verzi nezvyšují, do `main`
ani `rc-zkusebni` nepushují – pushnou vlastní větev a pošlou koordinátorovi hash a co ověřit.

## Kanály a číslování
- `rc-zkusebni` → zkušební kanál (testeři): posílat **bez ptaní**, hned jak je hotovo a zkoušky prošly.
- `main` → `latest` = ostrá pro všechny: **jen se souhlasem majitele** (zeptat se, i když už jednou souhlasil).
- Číslo: `versionName` = `1.0.N` (1.0.1, 1.0.2 … 1.0.10), každá nová verze +1; zkušební i ostrá jsou jedna řada
  (do ostré jde ověřená zkušební beze změny čísla). `versionCode` +1. Obojí naráz: `bash ci/verze.sh 1.0.N`.
- Aplikace v TV má vlastní číslo (1.5.x) ve 4 místech (webos/obchod/app/appinfo.json, obě platforma.js,
  tizen/app/config.xml) – zvýšit při každé změně `webos/obchod/app` / `tizen/app`. Samostatné vydání jen TV: `tv.yml` (CLAUDE.md).

## Postup
1. Pracovní kopie: vlastní worktree z `origin/rc-zkusebni` (nikdy ne ve složce `Recaption` samotné ani v `PlayLingo-novy`).
2. Zařazení větve: `git fetch -q origin && git merge --no-edit origin/<větev>` (u přestavěných větví `--ff-only`).
   Konflikt v `pomoc/videa/*` nebo `kroky.json` při slučování s `main` → vzít verzi z rc-zkusebni (`git checkout --ours`).
3. `bash ci/verze.sh 1.0.N`
4. `bash ci/zkousky.sh` (vše) – nebo jen relevantní (`aplikace web`; `hry` jen při změně čtení/překladu).
   Nový text aplikace musí být ve **všech 11 jazycích** včetně hindštiny (`core/I18nHi.kt`), text průvodce
   v `core/Pruvodce.kt` + `PruvodcePreklady*.kt` – jinak spadne AllLanguagesTest / PruvodceTest.
5. Commit (český popis začínající číslem verze, např. `1.0.9: …`; poslední commit **bez** `[skip ci]`, jinak se nic nesestaví).
6. Push: zkušební `git push -f origin HEAD:refs/heads/rc-zkusebni`, ostrá (po souhlasu) `git push origin HEAD:main`
   (+ stejný stav do rc-zkusebni). Přihlašování: `git -c credential.helper= -c 'credential.helper=!gh auth git-credential' push …`.
7. Hlídání **na pozadí**: `bash ci/hlidej-vydani.sh rc-zkusebni` (nebo `main`) – skončí, až web hlásí novou verzi.
8. Seznam her (soukromé Recaption-data, `hry/nahrat.sh`) smí koordinátor nahrát sám, když k němu jde verze (jen nepovinná pole).
9. Ostrá: s ní pushnout i lokální `main` repa Recaption-data.

## Na co nezapomenout (opakované chyby)
- Soubory mají CRLF: úpravy Pythonem vždy `newline=''` a normalizovat (`s.replace('\r\n','\n')` … zpět), jinak „nenašlo se“.
- Git Bash přepisuje cesty: `git show origin/main:cesta` → `MSYS_NO_PATHCONV=1 git show …`, nebo číst ze souboru v pracovní kopii.
- Žádné `sleep` v popředí delší než pár sekund – dlouhé čekání jen na pozadí (skript výše).
- `rm -rf` jen s `"${PROMENNA:?}"`.
- `node tizen/test/prijimac.test.js` je časově citlivý – pod zátěží může jednou spadnout; `ci/zkousky.sh` ho zopakuje.
