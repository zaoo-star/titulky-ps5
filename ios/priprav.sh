#!/bin/bash
# Příprava pro Xcode na Macu (GitHub i ručně): knihovna pro spojení s PS5 (ios/chiaki → PlChiaki.xcframework),
# společná logika z Androidu (ios/jadro → Jadro.xcframework) a projekt pro Xcode (XcodeGen).
# Potřebuje Xcode, Javu 17 (Kotlin) a brew (protobuf, ninja, xcodegen se doinstalují).
set -euo pipefail
cd "$(dirname "$0")/.."

git submodule update --init --depth 1 third_party/chiaki-ng
git -C third_party/chiaki-ng submodule update --init --depth 1 \
  third-party/nanopb third-party/jerasure third-party/gf-complete third-party/curl
for n in protobuf:protoc ninja:ninja xcodegen:xcodegen; do
  command -v "${n#*:}" >/dev/null || brew install "${n%%:*}"
done
# generátor zpráv pro PS5 (nanopb) potřebuje Python s protobuf
python3 -m venv "${TMPDIR:-/tmp}/recaption-venv"
"${TMPDIR:-/tmp}/recaption-venv/bin/pip" -q install protobuf
CMAKE_NAVIC="-DPYTHON_EXECUTABLE=${TMPDIR:-/tmp}/recaption-venv/bin/python -Wno-dev" ios/chiaki/sestav.sh

bash ./gradlew -p ios/jadro assembleJadroReleaseXCFramework --console=plain
(cd ios && xcodegen generate)
