#!/usr/bin/env bash
# Počká na sestavení a vydání APK z dané větve a ověří, že kanál na webu hlásí novou verzi.
#   bash ci/hlidej-vydani.sh rc-zkusebni     # zkušební kanál (zkusebni)
#   bash ci/hlidej-vydani.sh main            # ostrá (latest)
# Pouštět na pozadí (run_in_background) – trvá 5–10 min. Potřebuje gh (účet s právy k repu; jinak GH_TOKEN).
set -uo pipefail
cd "$(dirname "$0")/.."
VETEV="${1:?větev}"; REPO=adusa89/ReCaption
KANAL=zkusebni; [ "$VETEV" = main ] && KANAL=latest
KOD=$(grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | grep -oE '[0-9]+')
[ -z "${GH_TOKEN:-}" ] && GH_TOKEN=$(gh auth token --user adusa89 2>/dev/null || gh auth token) && export GH_TOKEN
sleep 15
R=$(gh run list -R $REPO -w "Sestavit APK" -b "$VETEV" -L 1 --json databaseId -q '.[0].databaseId')
echo "běh $R (větev $VETEV, čekám na versionCode $KOD v kanálu $KANAL)"
until [ "$(gh run view "$R" -R $REPO --json jobs -q '[.jobs[]|select(.name=="Vydat APK ke stažení")|.status][0]' 2>/dev/null)" = completed ]; do
  [ "$(gh run view "$R" -R $REPO --json status -q .status)" = completed ] && break
  sleep 30
done
gh run view "$R" -R $REPO --json jobs -q '.jobs[]|select(.name=="build" or .name=="Vydat APK ke stažení")|"\(.name)=\(.conclusion)"'
gh run view "$R" -R $REPO --log-failed 2>/dev/null | grep -E "::error|error:|FAILED" | head -8 | cut -c1-250
for i in $(seq 1 30); do
  V=$(curl -s "https://recaption.pages.dev/d/$KANAL/version.txt?n=$RANDOM$RANDOM")
  case "$V" in "$KOD "*) echo "web: $V – vydáno"; exit 0;; esac
  sleep 20
done
echo "web pořád hlásí: $V (čekal jsem 10 min)"; exit 1
