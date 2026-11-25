#!/usr/bin/env bash
# Nasadí web (pomoc/) z dané větve hned, bez vydání aplikace: v dočasné pracovní kopii vezme origin/main,
# přepíše složku pomoc/ (a ci/pruvodce-web.test.js) stavem z větve, commit s [skip ci], push do main
# a ručně spustí pomoc.yml (Cloudflare + záložní GitHub Pages). Jen se souhlasem majitele (je to main).
#   bash ci/nasad-web.sh rc-zkusebni "Web: co se mění"
set -euo pipefail
cd "$(dirname "$0")/.."
VETEV="${1:?větev, např. rc-zkusebni}"; ZPRAVA="${2:?popis změny}"; REPO=adusa89/ReCaption
TMP="$(mktemp -d)"; trap 'git worktree remove --force "$TMP" >/dev/null 2>&1 || true' EXIT
git fetch -q origin
git worktree add -q --detach "$TMP" origin/main
( cd "$TMP"
  git checkout "origin/$VETEV" -- pomoc ci/pruvodce-web.test.js 2>/dev/null || git checkout "origin/$VETEV" -- pomoc
  if git diff --cached --quiet; then echo "web v main už je stejný jako v $VETEV"; exit 0; fi
  git diff --cached --stat | tail -1
  git commit -q -m "$ZPRAVA [skip ci]" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
  git -c credential.helper= -c 'credential.helper=!gh auth git-credential' push -q origin HEAD:main )
[ -z "${GH_TOKEN:-}" ] && GH_TOKEN=$(gh auth token --user adusa89 2>/dev/null || gh auth token) && export GH_TOKEN
gh workflow run pomoc.yml -R $REPO --ref main
sleep 8
W=$(gh run list -R $REPO -w "Stránky s pomocí" -L 1 --json databaseId -q '.[0].databaseId')
until [ "$(gh run view "$W" -R $REPO --json status -q .status)" = completed ]; do sleep 15; done
gh run view "$W" -R $REPO --json jobs -q '.jobs[]|"\(.name)=\(.conclusion)"'
