#!/bin/bash
# SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
# Knihovna pro spojení s PS5 pro iPhone: ios/chiaki/build/PlChiaki.xcframework (statická, iPhone + simulátor arm64).
# Na Macu s Xcode (GitHub: .github/workflows/ios.yml). Předtem: git submodule update --init third_party/chiaki-ng
# a v něm third-party/nanopb, jerasure, gf-complete a curl (ne celý strom).
#   ./sestav.sh            iPhone i simulátor
#   ZDROJE=<složka> …       doplňky (json-c, miniupnp, mbedtls, libevent) z místních kopií místo stahování
#   ./sestav.sh linux       jen zkouška překladu na Linuxu (bez Xcode)
set -euo pipefail
cd "$(dirname "$0")"
OUT=build
rm -rf "$OUT/PlChiaki.xcframework"
mkdir -p "$OUT"

FETCH=()
if [ -n "${ZDROJE:-}" ]; then
  FETCH=(-DFETCHCONTENT_SOURCE_DIR_JSON-C="$ZDROJE/json-c" -DFETCHCONTENT_SOURCE_DIR_MINIUPNPC="$ZDROJE/miniupnp"
         -DFETCHCONTENT_SOURCE_DIR_MBEDTLS="$ZDROJE/mbedtls" -DFETCHCONTENT_SOURCE_DIR_LIBEVENT="$ZDROJE/libevent")
fi

# pkg-config nesmí najít knihovny počítače (brew) – vše potřebné se sestaví tady
export PKG_CONFIG_LIBDIR="$PWD/$OUT/zadne-balicky"
mkdir -p "$PKG_CONFIG_LIBDIR"

sestav() { # $1 = iphoneos | iphonesimulator | linux
  local B="$OUT/$1"
  local PLAT=()
  if [ "$1" != linux ]; then
    PLAT=(-DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_SYSROOT="$1" -DCMAKE_OSX_ARCHITECTURES=arm64
          -DCMAKE_OSX_DEPLOYMENT_TARGET=16.0 -DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY)
  fi
  # CMake 4 (Codemagic, novější Xcode) odmítá doplňky se starým cmake_minimum_required (libevent 2.1) – povolit
  cmake -S . -B "$B" -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5 ${PLAT[@]+"${PLAT[@]}"} ${FETCH[@]+"${FETCH[@]}"} ${CMAKE_NAVIC:-} > "$B.cmake.log" 2>&1 \
    || { tail -40 "$B.cmake.log"; exit 1; }
  cmake --build "$B" --target plchiaki > "$B.build.log" 2>&1 || { tail -60 "$B.build.log"; exit 1; }
  # všechny statické knihovny (chiaki, protobuf, jerasure, gf-complete, mbedtls, json-c, miniupnpc, curl) do jedné
  # (bez mapfile – bash na Macu je starý 3.2)
  LIBS=()
  while IFS= read -r f; do LIBS+=("$f"); done < <(find "$B" -name '*.a' ! -name 'libplchiaki-all.a' | sort)
  if [ "$1" = linux ]; then
    printf '%s\n' "${LIBS[@]}"
  else
    libtool -static -o "$B/libplchiaki-all.a" "${LIBS[@]}"
  fi
}

if [ "${1:-}" = linux ]; then
  sestav linux
  exit 0
fi

sestav iphoneos
sestav iphonesimulator
mkdir -p "$OUT/hlavicky"
cp plchiaki.h "$OUT/hlavicky/"
cat > "$OUT/hlavicky/module.modulemap" <<M
module PlChiaki {
    header "plchiaki.h"
    export *
}
M
xcodebuild -create-xcframework \
  -library "$OUT/iphoneos/libplchiaki-all.a" -headers "$OUT/hlavicky" \
  -library "$OUT/iphonesimulator/libplchiaki-all.a" -headers "$OUT/hlavicky" \
  -output "$OUT/PlChiaki.xcframework"
echo "hotovo: $OUT/PlChiaki.xcframework"
