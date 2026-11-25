#!/usr/bin/env bash
# Místní zkoušky před odesláním (Windows / Git Bash i Linux). Bez parametrů vše, jinak jen vybrané:
#   bash ci/zkousky.sh                 # aplikace, hry, pc, web, tv
#   bash ci/zkousky.sh aplikace web    # jen vybrané
# aplikace = ci/mistni-kontrola test (překlad celé aplikace se stuby + unit testy, AllLanguagesTest, PruvodceTest…)
# android  = testDebugUnitTest jako na GitHubu (Android SDK, bez nativní části) – odhalí třídy, které v Androidu nejsou
#            (např. javax.imageio v testu); potřebuje Android SDK (ANDROID_HOME nebo %LOCALAPPDATA%\Android\Sdk)
# hry      = testy s daty her ze soukromého repa (jen při změně čtení/překladu: core, TranslatorService)
# pc       = ReCaption pro PC (core se překládá i pro něj)
# web      = ci/pruvodce-web.test.js (všechny stránky průvodce)
# tv       = tizen/test/prijimac.test.js (aplikace v TV; časově citlivý – při selhání se jednou zopakuje)
# Výstupy do ci/build-zkousky/<název>.log; na konci souhrn a kód 1, když něco neprošlo.
set -uo pipefail
cd "$(dirname "$0")/.."
KOREN="$PWD"
DATA="${RECAPTION_DATA:-$KOREN/../Recaption-data}"
[ -d "$DATA/hry" ] || DATA="/c/Users/veks/source/repos/Recaption-data"
if [ -z "${JAVA_HOME:-}" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"17'; then
  for j in "/c/Program Files/Eclipse Adoptium"/jdk-17* "/c/Program Files/Java"/jdk-17* /usr/lib/jvm/*17*; do
    [ -x "$j/bin/java" ] && { export JAVA_HOME="$j"; break; }
  done
fi
GW=./gradlew; [ -f gradlew.bat ] && [ "${OS:-}" = "Windows_NT" ] && GW=./gradlew.bat
LOGY="$KOREN/ci/build-zkousky"; mkdir -p "$LOGY"
CO=("$@"); [ ${#CO[@]} -eq 0 ] && CO=(aplikace android hry pc web tv)
SOUHRN=""; CHYBA=0
spust() { # název, příkaz…
  local n="$1"; shift
  if "$@" > "$LOGY/$n.log" 2>&1; then SOUHRN="$SOUHRN $n=OK"; else SOUHRN="$SOUHRN $n=CHYBA"; CHYBA=1; fi
}
for c in "${CO[@]}"; do
  case "$c" in
    aplikace) spust aplikace "$GW" -q -p ci/mistni-kontrola test ;;
    android)
      [ -z "${ANDROID_HOME:-}" ] && [ -n "${LOCALAPPDATA:-}" ] && export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
      mkdir -p ci/build-zkousky/nativni/arm64-v8a
      NZ="$KOREN/ci/build-zkousky/nativni"; command -v cygpath >/dev/null && NZ=$(cygpath -w "$NZ")
      spust android "$GW" -q testDebugUnitTest "-PnativniZ=$NZ" -Pabis=arm64-v8a ;;
    hry) spust hry "$GW" -q -p "$DATA/hry" test -Pverejne="$KOREN" ;;
    pc) spust pc "$GW" -q -p pc installDist -Dorg.gradle.jvmargs=-Xmx3g ;;
    web) spust web node ci/pruvodce-web.test.js ;;
    tv) spust tv bash -c 'node tizen/test/prijimac.test.js || node tizen/test/prijimac.test.js' ;;
    *) echo "neznámá zkouška: $c"; CHYBA=1 ;;
  esac
done
echo "zkoušky:$SOUHRN"
if [ $CHYBA -ne 0 ]; then
  echo "--- co neprošlo (konec logů; neúspěšné unit testy: ci/mistni-kontrola/build/test-results/test/*.xml) ---"
  for f in "$LOGY"/*.log; do n=$(basename "$f" .log); case "$SOUHRN" in *"$n=CHYBA"*) echo "## $n"; tail -15 "$f";; esac; done
  [ -d ci/mistni-kontrola/build/test-results/test ] && grep -h "<failure message" ci/mistni-kontrola/build/test-results/test/*.xml 2>/dev/null | cut -c1-300 | head -8
fi
exit $CHYBA
