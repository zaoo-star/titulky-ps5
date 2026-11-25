# SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
# ReCaption pro iPhone: knihovna chiaki-ng (spojení s PS5) + vrstva pro Swift (plchiaki.c) jako jedna statická knihovna.
# Nastavení jako v Androidu (app/build.gradle.kts): bez zvuku (opus), dekodéru, GUI a testů. Místo OpenSSL mbedtls
# (na iPhonu OpenSSL není); curl bez TLS – používá ho jen hraní přes internet (RUDP), které ReCaption nepotřebuje.
# Sestavení: ./sestav.sh (iPhone + simulátor → PlChiaki.xcframework).
cmake_minimum_required(VERSION 3.16)
project(plchiaki C CXX)

set(CHIAKI_ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(CHIAKI_ENABLE_CLI OFF CACHE BOOL "" FORCE)
set(CHIAKI_ENABLE_GUI OFF CACHE BOOL "" FORCE)
set(CHIAKI_ENABLE_SETSU OFF CACHE STRING "" FORCE)
set(CHIAKI_ENABLE_FFMPEG_DECODER OFF CACHE STRING "" FORCE)
set(CHIAKI_ENABLE_PI_DECODER OFF CACHE STRING "" FORCE)
set(CHIAKI_LIB_ENABLE_OPUS OFF CACHE BOOL "" FORCE)
set(CHIAKI_ENABLE_SPEEX OFF CACHE STRING "" FORCE)
set(CHIAKI_LIB_ENABLE_MBEDTLS ON CACHE BOOL "" FORCE)
set(CHIAKI_LIB_MBEDTLS_EXTERNAL_PROJECT ON CACHE BOOL "" FORCE)
# mbedtls 2.x jinak bere varování jako chyby a novější clang z Xcode 26 hlásí nové (bignum.c: unused-but-set-variable)
set(MBEDTLS_FATAL_WARNINGS OFF CACHE BOOL "" FORCE)
set(CHIAKI_LIB_JSONC_EXTERNAL_PROJECT ON CACHE BOOL "" FORCE)
set(CHIAKI_LIB_MINIUPNPC_EXTERNAL_PROJECT ON CACHE BOOL "" FORCE)
set(CHIAKI_ENABLE_STEAM_SHORTCUT OFF CACHE BOOL "" FORCE)
set(CHIAKI_ENABLE_STEAMDECK_NATIVE OFF CACHE STRING "" FORCE)
set(CHIAKI_USE_SYSTEM_CURL OFF CACHE STRING "" FORCE)
# curl bez TLS a bez doplňků (jen pro hraní přes internet, ReCaption ho nepoužívá)
set(CURL_ENABLE_SSL OFF CACHE BOOL "" FORCE)
set(CURL_USE_OPENSSL OFF CACHE BOOL "" FORCE)
set(CURL_USE_LIBPSL OFF CACHE BOOL "" FORCE)
set(CURL_USE_LIBSSH2 OFF CACHE BOOL "" FORCE)
set(USE_LIBIDN2 OFF CACHE BOOL "" FORCE)
set(USE_NGHTTP2 OFF CACHE BOOL "" FORCE)
set(CURL_ZLIB OFF CACHE STRING "" FORCE)
set(CURL_BROTLI OFF CACHE STRING "" FORCE)
set(CURL_ZSTD OFF CACHE STRING "" FORCE)

# pkg-config načíst tady nahoře, ať ho chiaki-ng v lib/ má celý (jinak chyba „Unknown arguments“ v pkg_search_module);
# sestav.sh mu nastaví prázdnou složku balíčků, takže nic z Macu (brew) do knihovny pro iPhone nepřibere
find_package(PkgConfig QUIET)

# libevent: chiaki-ng ho mimo Android vyžaduje (jen hraní přes internet – holepunch.c); sestaví se tady staticky
include(FetchContent)
set(EVENT__LIBRARY_TYPE STATIC CACHE STRING "" FORCE)
set(EVENT__DISABLE_OPENSSL ON CACHE BOOL "" FORCE)
set(EVENT__DISABLE_MBEDTLS ON CACHE BOOL "" FORCE)
set(EVENT__DISABLE_BENCHMARK ON CACHE BOOL "" FORCE)
set(EVENT__DISABLE_TESTS ON CACHE BOOL "" FORCE)
set(EVENT__DISABLE_REGRESS ON CACHE BOOL "" FORCE)
set(EVENT__DISABLE_SAMPLES ON CACHE BOOL "" FORCE)
set(EVENT__DISABLE_DEBUG_MODE ON CACHE BOOL "" FORCE)
FetchContent_Declare(libevent
	GIT_REPOSITORY https://github.com/libevent/libevent.git
	GIT_TAG release-2.1.12-stable
	GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(libevent)
set(PL_LIBEVENT_INCLUDE_DIRS "${libevent_SOURCE_DIR}/include;${libevent_BINARY_DIR}/include")
set(libevent_DIR "${CMAKE_CURRENT_SOURCE_DIR}/cmake" CACHE PATH "" FORCE)

# chiaki-ng na Macu s Apple Silicon vnutí CMAKE_OSX_DEPLOYMENT_TARGET = verze macOS stroje (26.0) do cache (FORCE) –
# pro iPhone by pak knihovna chtěla iOS 26. Běžná proměnná s CMP0126 NEW má přednost před cache i v podsložkách.
if(CMAKE_SYSTEM_NAME STREQUAL "iOS")
  set(CMAKE_POLICY_DEFAULT_CMP0126 NEW)
  set(CMAKE_OSX_DEPLOYMENT_TARGET 16.0) # jako deploymentTarget v ios/project.yml
endif()
add_subdirectory(../../third_party/chiaki-ng chiaki-ng EXCLUDE_FROM_ALL)

add_library(plchiaki STATIC plchiaki.c plchiaki.h)
target_link_libraries(plchiaki PUBLIC chiaki-lib)
# stejné nastavení jako knihovna (chiaki-ng ho dává jen svým složkám) – jinak by struktury měly jinou velikost
target_compile_definitions(plchiaki PRIVATE CHIAKI_LIB_ENABLE_MBEDTLS)
target_include_directories(plchiaki PRIVATE "${CMAKE_CURRENT_BINARY_DIR}/chiaki-ng/lib/include")
