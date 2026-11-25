#!/bin/bash
# Příprava čtečky na Macu (obdoba priprava.ps1 pro Windows). Do systému nic neinstaluje – vše jde do
# složky nástrojů (~/Library/Application Support/ReCaptionTools):
#   - překladový model angličtina → zvolený jazyk (Argos, běží bez internetu),
#   - bez přibaleného Pythonu (běh z repozitáře) i prostředí Pythonu s knihovnami pro čtení a překlad.
#
#   priprava.sh JAZYK SLOZKA_NASTROJU [PYTHON]
#
#   PYTHON   Python s knihovnami už je (aplikace ReCaption.app ho má v sobě) – stáhne se jen model.
# Spouští ji okno aplikace tlačítkem Připravit; výpis jde do Záznamu.
# Pozor: proměnné jen jako ${JMENO} – bash na Macu (3.2) jinak vezme písmeno s diakritikou za jménem jako jeho součást.
set -euo pipefail

JAZYK="${1:-cs}"
SLOZKA="${2:-$HOME/Library/Application Support/ReCaptionTools}"
PYTHON="${3:-}"
SEZNAM="https://raw.githubusercontent.com/argosopentech/argospm-index/main/index.json"

mkdir -p "${SLOZKA}/modely"
# pomocné soubory Pythonu (.pyc) mimo aplikaci – zápis do ReCaption.app by porušil její podpis
export PYTHONPYCACHEPREFIX="${PYTHONPYCACHEPREFIX:-${SLOZKA}/pyc}"

if [ -z "${PYTHON}" ]; then
    PYTHON="${SLOZKA}/venv/bin/python3"
    if [ ! -x "${PYTHON}" ]; then
        echo "Zakládám prostředí Pythonu…"
        # čtení textu (RapidOCR) chce Python 3.10–3.12; novější python3 by dostal starou, nefunkční verzi
        ZAKLAD=python3
        for P in python3.12 python3.11 python3.10; do
            if command -v "${P}" >/dev/null 2>&1; then ZAKLAD="${P}"; break; fi
        done
        "${ZAKLAD}" -m venv "${SLOZKA}/venv"
    fi
    KNIHOVNY="ctranslate2 subword-nmt sacremoses rapidocr-onnxruntime>=1.3 pillow numpy"
    if [ "$(uname)" = "Darwin" ]; then
        KNIHOVNY="${KNIHOVNY} pyobjc-framework-Quartz pyobjc-framework-ScreenCaptureKit"
    fi
    echo "Stahuji knihovny pro čtení a překlad…"
    # shellcheck disable=SC2086
    "${PYTHON}" -m pip install -q ${KNIHOVNY}
fi

MODEL="${SLOZKA}/modely/en_${JAZYK}"
if [ ! -d "${MODEL}" ] || [ -z "$(ls -A "${MODEL}" 2>/dev/null)" ]; then
    DOCASNE="$(mktemp -d "${SLOZKA}/modely/stahovani.XXXXXX")"
    trap 'rm -rf "${DOCASNE}"' EXIT
    echo "Hledám překladový model pro jazyk ${JAZYK}…"
    curl -fsSL --retry 3 --connect-timeout 15 "${SEZNAM}" -o "${DOCASNE}/seznam.json"
    ADRESA="$("${PYTHON}" -c '
import json, sys
jazyk = sys.argv[2]
for b in json.load(open(sys.argv[1], encoding="utf-8")):
    if b.get("from_code") == "en" and b.get("to_code") == jazyk and b.get("links"):
        print(b["links"][0])
        break
' "${DOCASNE}/seznam.json" "${JAZYK}")"
    if [ -z "${ADRESA}" ]; then
        echo "Pro jazyk '${JAZYK}' překladový model není."
        exit 1
    fi
    echo "Stahuji překladový model…"
    curl -fSL --retry 3 --connect-timeout 15 -sS "${ADRESA}" -o "${DOCASNE}/model.zip"
    mkdir "${DOCASNE}/model"
    # rozbalení Pythonem – stejně na Macu i jinde, bez závislosti na nástrojích systému
    "${PYTHON}" -c '
import sys, zipfile
zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])
' "${DOCASNE}/model.zip" "${DOCASNE}/model"
    rm -rf "${MODEL}"
    mv "${DOCASNE}/model" "${MODEL}"
fi
echo "Hotovo."
