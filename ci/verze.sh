#!/usr/bin/env bash
# Zvýší verzi aplikace na obou místech najednou (app/build.gradle.kts + BuildInfo na konci MainActivity.kt):
# versionCode +1, versionName = zadaný. Číslování po 1.00: 1.0.N (přání majitele 24. 11. 2026).
#   bash ci/verze.sh 1.0.9
# Konce řádků (CRLF) zachová. Vypíše novou verzi; při nesouladu obou míst skončí chybou.
set -euo pipefail
cd "$(dirname "$0")/.."
NOVA="${1:?Použití: bash ci/verze.sh <versionName>, např. 1.0.9}"
python -X utf8 - "$NOVA" <<'PY'
import re, sys
nova = sys.argv[1]
g = 'app/build.gradle.kts'; m = 'app/src/main/java/cz/recaption/MainActivity.kt'
def cti(p):
    s = open(p, encoding='utf-8', newline='').read()
    return s, ('\r\n' if '\r\n' in s else '\n')
s, nl = cti(g)
kod = int(re.search(r'versionCode = (\d+)', s).group(1))
s = re.sub(r'versionCode = \d+', 'versionCode = %d' % (kod + 1), s, count=1)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "%s"' % nova, s, count=1)
open(g, 'w', encoding='utf-8', newline='').write(s)
t, nl = cti(m)
if not re.search(r'const val VERSION_CODE = %d\b' % kod, t):
    sys.exit('BuildInfo v MainActivity.kt nesedí s versionCode %d – oprav ručně' % kod)
t = re.sub(r'const val VERSION_CODE = \d+', 'const val VERSION_CODE = %d' % (kod + 1), t, count=1)
t = re.sub(r'const val VERSION = "verze [^"]+"', 'const val VERSION = "verze %s"' % nova, t, count=1)
open(m, 'w', encoding='utf-8', newline='').write(t)
print('verze: %d %s (dřív %d)' % (kod + 1, nova, kod))
PY
