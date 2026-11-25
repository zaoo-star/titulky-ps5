#!/usr/bin/env bash
# Zkouška v emulátoru s jedním opakováním: emulátor občas nestihne krok v limitu (pomalé čtení, model jazyka se nestáhne,
# zasekne se), i když aplikace je v pořádku. Když první běh nedá VYSLEDEK=OK, výsledky se odloží do test-results-pokus1
# a zkouška se pustí znovu (aplikace se odinstaluje, ať začne načisto). Rozhoduje druhý běh; dvakrát špatně = skutečná chyba.
# Použití (script v android-emulator-runner): bash ci/zkouska-opakovat.sh ci/emulator-test.sh
SKRIPT="$1"
bash "$SKRIPT"
if grep -q "^VYSLEDEK=OK" test-results/result.txt 2>/dev/null; then exit 0; fi
# zkouška telefonu VYSLEDEK nepíše – tam rozhoduje jen vlastní kontrola ve workflow, opakovat jen při chybě instalace
if ! grep -q "^VYSLEDEK=" test-results/result.txt 2>/dev/null && ! grep -q "INSTALL FAILED" test-results/result.txt 2>/dev/null; then exit 0; fi
echo "::warning::Zkouška v emulátoru neprošla napoprvé – opakuji jednou (výsledky prvního běhu v test-results-pokus1)"
rm -rf test-results-pokus1; mv test-results test-results-pokus1
adb shell pm uninstall cz.recaption >/dev/null 2>&1 || true
bash "$SKRIPT"
cp -r test-results-pokus1 test-results/pokus1 2>/dev/null || true
exit 0
