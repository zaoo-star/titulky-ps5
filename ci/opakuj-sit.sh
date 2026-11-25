#!/usr/bin/env bash
# Spustí příkaz a při SÍŤOVÉ chybě (přetížené úložiště knihoven 429, 5xx, výpadek spojení, timeout) ho zopakuje,
# nejvýš 5×, s pauzou 30, 60, 90 a 120 s. Skutečná chyba (překlad, test, podpis) se neopakuje – skončí hned.
# Použití: bash ci/opakuj-sit.sh ./gradlew assembleRelease --no-daemon
set -o pipefail
LOG=$(mktemp)
for pokus in 1 2 3 4 5; do
  "$@" 2>&1 | tee "$LOG"
  kod=${PIPESTATUS[0]}
  [ "$kod" -eq 0 ] && { rm -f "$LOG"; exit 0; }
  if [ "$pokus" -lt 5 ] && grep -qE "Could not (resolve|GET|HEAD|download)|status code (429|5[0-9][0-9])|Connection (reset|refused|timed out)|Read timed out|Remote host terminated|Temporary failure in name resolution|Unable to resolve host" "$LOG"; then
    echo "::warning::Síťová chyba (pokus $pokus) – opakuji za $((pokus * 30)) s"
    sleep $((pokus * 30))
    continue
  fi
  rm -f "$LOG"; exit "$kod"
done
