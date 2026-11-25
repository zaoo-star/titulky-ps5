// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
//
// ReCaption pro iPhone: tenká vrstva mezi knihovnou chiaki-ng (spojení s PS5) a Swiftem.
// Dělá totéž co app/src/main/cpp/src/main/cpp/chiaki-jni.c v Androidu (spárování, spojení, video bez zvuku,
// důvod ukončení, kód účtu), jen místo Javy obyčejné funkce s ukazatelem na kontext. Spojení se chová stejně jako
// na Androidu – nic navíc se konzoli neposílá (CLAUDE.md: „Spojení s konzolí neměň bez výslovného zadání“).
#ifndef PLCHIAKI_H
#define PLCHIAKI_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/** Musí se zavolat jednou před vším ostatním (0 = v pořádku). */
int pl_lib_init(void);
const char *pl_error_string(int err);
const char *pl_quit_reason_string(int reason);
bool pl_quit_reason_is_error(int reason);

// ---- spojení ----

enum { PL_EVENT_CONNECTED = 1, PL_EVENT_LOGIN_PIN_REQUEST = 2, PL_EVENT_QUIT = 3 };

/** Událost spojení (z vlákna knihovny): připojeno; žádost o kód účtu (value = 1, když byl špatný); konec (value = důvod, text = popis). */
typedef void (*pl_event_cb)(int type, int value, const char *text, void *ctx);
/** Jeden snímek videa (H.264/H.265 s počátečními kódy 00 00 00 01) z vlákna příjmu; false = vyžádat klíčový snímek. */
typedef bool (*pl_video_cb)(const uint8_t *buf, size_t len, int32_t frames_lost, bool frame_recovered, void *ctx);

typedef struct pl_session_t PLSession;

/**
 * Spojení s PS5 (nebo PS4) se spárovanými údaji. regist_key 16 bajtů, morning 16 bajtů.
 * resolution: 1 = 360p, 2 = 540p, 3 = 720p, 4 = 1080p (ChiakiVideoResolutionPreset); fps: 30 nebo 60;
 * codec: 0 = H.264, 1 = H.265 (ChiakiCodec). log_file = soubor záznamu knihovny (jako ps5-spojeni.txt na Androidu)
 * nebo NULL. Při chybě vrací NULL a kód v *err.
 */
PLSession *pl_session_create(bool ps5, const char *host, const uint8_t *regist_key, size_t regist_key_len,
                             const uint8_t *morning, size_t morning_len, int resolution, int fps, int codec,
                             const char *log_file, bool log_verbose, pl_event_cb event_cb, pl_video_cb video_cb,
                             void *ctx, int *err);
int pl_session_start(PLSession *s);
int pl_session_stop(PLSession *s);
int pl_session_join(PLSession *s);
/** Po join: uvolní vše (zavře i soubor záznamu). */
void pl_session_free(PLSession *s);
int pl_session_request_idr(PLSession *s);
/** Čtyřmístný kód účtu na PS5 (když o něj konzole požádala). */
void pl_session_set_login_pin(PLSession *s, const char *pin);
/** Ovladač v klidu (jako Android, když nic neposílá). */
void pl_session_set_controller_idle(PLSession *s);

// ---- spárování ----

enum { PL_REGIST_CANCELED = 0, PL_REGIST_FAILED = 1, PL_REGIST_SUCCESS = 2 };

typedef struct pl_regist_host_t
{
	int target;
	char server_nickname[0x21];
	uint8_t server_mac[6];
	uint8_t rp_regist_key[0x10];
	uint32_t rp_key_type;
	uint8_t rp_key[0x10];
} PLRegistHost;

/** Výsledek spárování (z vlákna knihovny); host jen při PL_REGIST_SUCCESS. */
typedef void (*pl_regist_cb)(int type, const PLRegistHost *host, void *ctx);

typedef struct pl_regist_t PLRegist;

/**
 * Spárování osmimístným číslem z PS5. target = ChiakiTarget (PS5 1000100 apod. – stejné hodnoty jako Target v Androidu),
 * psn_account_id = 8 bajtů (číslo účtu z přihlášení Sony), psn_online_id může být NULL.
 */
PLRegist *pl_regist_start(int target, const char *host, bool broadcast, const char *psn_online_id,
                          const uint8_t *psn_account_id, size_t psn_account_id_len, uint32_t pin,
                          const char *log_file, pl_regist_cb cb, void *ctx, int *err);
void pl_regist_stop(PLRegist *r);
void pl_regist_free(PLRegist *r);

#ifdef __cplusplus
}
#endif

#endif
