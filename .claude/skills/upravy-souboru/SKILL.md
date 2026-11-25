---
name: upravy-souboru
description: Jak spolehlivě upravovat soubory ReCaptionu na Windows (CRLF, čeština, velké soubory jako MainActivity.kt, I18n*.kt) bez opakovaných chyb „nenašlo se“ – použij před hromadnou úpravou skriptem nebo přidáním textů do všech jazyků.
---

# Úpravy souborů bez opakovaných chyb

- **CRLF**: většina zdrojů má konce řádků CRLF. Pythonem:
  ```python
  s = open(p, encoding='utf-8', newline='').read(); nl = '\r\n' if '\r\n' in s else '\n'; s = s.replace('\r\n', '\n')
  assert s.count(a) == 1, (a[:80], s.count(a)); s = s.replace(a, b)
  open(p, 'w', encoding='utf-8', newline='').write(s.replace('\n', nl))
  ```
  Spouštět `python -X utf8`; skript s uvozovkami a `\'` piš do souboru (Write) a pak spusť – heredoc v bashi escapuje jinak.
- Pro jednu-dvě změny raději nástroj Edit (po Read) – CRLF řeší sám.
- **Nový text aplikace** = klíč ve všech 11 jazycích: `core/I18n*.kt` (cs/sk dvojice, en, pl, de, hu, uk, es, fr, it) **a** `core/I18nHi.kt`.
  Nepoužívaný text smazat (AllLanguagesTest hlídá obojí).
- **Text průvodce** jen v šabloně `core/Pruvodce.kt` (cs+en) + `core/PruvodcePreklady*.kt` (klíčem je anglický text);
  web se z ní generuje (`-Ppruvodce`), nic neupravovat v `pomoc/pruvodce-data.js`.
- **Git Bash**: `git show ref:cesta` → `MSYS_NO_PATHCONV=1`; cesty k repům `/c/Users/...`; `rm -rf "${X:?}"`.
- **Dlouhé příkazy** (gradle, sestavení) na pozadí, výsledek číst až po oznámení; ne `sleep` ve smyčce v popředí.
- Klíče a hesla (`%USERPROFILE%\.recaption\…`) nikdy nevypisovat ani nekopírovat do repa.
