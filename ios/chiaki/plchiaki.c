// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
//
// ReCaption pro iPhone – viz plchiaki.h. Podle app/src/main/cpp/src/main/cpp/chiaki-jni.c a log.c (Android),
// ty podle chiaki-ng android/app/src/main/cpp/chiaki-jni.c (AGPL-3.0). Bez JNI, bez zvuku a bez dekodéru videa.

#include "plchiaki.h"

#include <chiaki/common.h>
#include <chiaki/log.h>
#include <chiaki/regist.h>
#include <chiaki/session.h>

#include <errno.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// ---- záznam knihovny do souboru (jako log.c v Androidu) ----

static void log_cb_file(ChiakiLogLevel level, const char *msg, void *user)
{
	FILE *f = user;
	if(!f)
		return;
	char c = '?';
	switch(level)
	{
		case CHIAKI_LOG_DEBUG: c = 'D'; break;
		case CHIAKI_LOG_VERBOSE: c = 'V'; break;
		case CHIAKI_LOG_INFO: c = 'I'; break;
		case CHIAKI_LOG_WARNING: c = 'W'; break;
		case CHIAKI_LOG_ERROR: c = 'E'; break;
	}
	fprintf(f, "[%c] %s\n", c, msg);
	fflush(f);
}

static void log_cb_none(ChiakiLogLevel level, const char *msg, void *user)
{
	(void)level; (void)msg; (void)user;
}

static ChiakiLog *log_new(const char *file, bool verbose)
{
	ChiakiLog *log = calloc(1, sizeof(ChiakiLog));
	if(!log)
		return NULL;
	uint32_t level = verbose ? CHIAKI_LOG_ALL : (CHIAKI_LOG_ALL & ~CHIAKI_LOG_VERBOSE);
	FILE *f = file ? fopen(file, "w+") : NULL;
	chiaki_log_init(log, level, f ? log_cb_file : log_cb_none, f);
	if(f)
		CHIAKI_LOGI(log, "Logging to file %s", file);
	return log;
}

static void log_free(ChiakiLog *log)
{
	if(!log)
		return;
	if(log->user)
		fclose((FILE *)log->user);
	free(log);
}

// ---- knihovna ----

int pl_lib_init(void) { return (int)chiaki_lib_init(); }
const char *pl_error_string(int err) { return chiaki_error_string((ChiakiErrorCode)err); }
const char *pl_quit_reason_string(int reason) { return chiaki_quit_reason_string((ChiakiQuitReason)reason); }
bool pl_quit_reason_is_error(int reason) { return chiaki_quit_reason_is_error((ChiakiQuitReason)reason); }

// ---- spojení ----

struct pl_session_t
{
	ChiakiSession session;
	ChiakiLog *log;
	pl_event_cb event_cb;
	pl_video_cb video_cb;
	void *ctx;
};

static bool video_sample_cb(uint8_t *buf, size_t buf_size, int32_t frames_lost, bool frame_recovered, void *user)
{
	PLSession *s = user;
	if(!s || !s->video_cb || !buf || buf_size == 0)
		return true;
	return s->video_cb(buf, buf_size, frames_lost, frame_recovered, s->ctx);
}

static void event_cb(ChiakiEvent *event, void *user)
{
	PLSession *s = user;
	if(!s || !s->event_cb)
		return;
	switch(event->type)
	{
		case CHIAKI_EVENT_CONNECTED:
			s->event_cb(PL_EVENT_CONNECTED, 0, NULL, s->ctx);
			break;
		case CHIAKI_EVENT_LOGIN_PIN_REQUEST:
			s->event_cb(PL_EVENT_LOGIN_PIN_REQUEST, event->login_pin_request.pin_incorrect ? 1 : 0, NULL, s->ctx);
			break;
		case CHIAKI_EVENT_QUIT:
			s->event_cb(PL_EVENT_QUIT, (int)event->quit.reason, event->quit.reason_str, s->ctx);
			break;
		default:
			break;
	}
}

PLSession *pl_session_create(bool ps5, const char *host, const uint8_t *regist_key, size_t regist_key_len,
                             const uint8_t *morning, size_t morning_len, int resolution, int fps, int codec,
                             const char *log_file, bool log_verbose, pl_event_cb ev, pl_video_cb video,
                             void *ctx, int *err)
{
	ChiakiErrorCode e = CHIAKI_ERR_SUCCESS;
	PLSession *s = NULL;
	ChiakiLog *log = log_new(log_file, log_verbose);
	if(!log)
	{
		e = CHIAKI_ERR_MEMORY;
		goto beach;
	}

	ChiakiConnectInfo info;
	memset(&info, 0, sizeof(info));
	info.ps5 = ps5;
	info.host = host;
	if(!host || regist_key_len != sizeof(info.regist_key) || morning_len != sizeof(info.morning))
	{
		CHIAKI_LOGE(log, "ReCaption: invalid connect info");
		e = CHIAKI_ERR_INVALID_DATA;
		goto beach;
	}
	memcpy(info.regist_key, regist_key, sizeof(info.regist_key));
	memcpy(info.morning, morning, sizeof(info.morning));
	chiaki_connect_video_profile_preset(&info.video_profile, (ChiakiVideoResolutionPreset)resolution,
	                                    fps >= 60 ? CHIAKI_VIDEO_FPS_PRESET_60 : CHIAKI_VIDEO_FPS_PRESET_30);
	info.video_profile.codec = (ChiakiCodec)codec;
	info.video_profile_auto_downgrade = true;

	s = calloc(1, sizeof(PLSession));
	if(!s)
	{
		e = CHIAKI_ERR_MEMORY;
		goto beach;
	}
	s->log = log;
	s->event_cb = ev;
	s->video_cb = video;
	s->ctx = ctx;
	e = chiaki_session_init(&s->session, &info, log);
	if(e != CHIAKI_ERR_SUCCESS)
	{
		CHIAKI_LOGE(log, "ReCaption: session init failed");
		free(s);
		s = NULL;
		goto beach;
	}
	chiaki_session_set_event_cb(&s->session, event_cb, s);
	// zvuk se nepřehrává (hra hraje z TV přes HDMI) – žádný audio sink, stejně jako na Androidu
	chiaki_session_set_video_sample_cb(&s->session, video_sample_cb, s);

beach:
	if(!s)
		log_free(log);
	if(err)
		*err = (int)e;
	return s;
}

int pl_session_start(PLSession *s) { return (int)chiaki_session_start(&s->session); }
int pl_session_stop(PLSession *s) { return (int)chiaki_session_stop(&s->session); }
int pl_session_join(PLSession *s) { return (int)chiaki_session_join(&s->session); }
int pl_session_request_idr(PLSession *s) { return (int)chiaki_session_request_idr(&s->session); }

void pl_session_free(PLSession *s)
{
	if(!s)
		return;
	chiaki_session_fini(&s->session);
	log_free(s->log);
	free(s);
}

void pl_session_set_login_pin(PLSession *s, const char *pin)
{
	if(s && pin)
		chiaki_session_set_login_pin(&s->session, (const uint8_t *)pin, strlen(pin));
}

void pl_session_set_controller_idle(PLSession *s)
{
	if(!s)
		return;
	ChiakiControllerState state;
	chiaki_controller_state_set_idle(&state);
	chiaki_session_set_controller_state(&s->session, &state);
}

// ---- spárování ----

struct pl_regist_t
{
	ChiakiRegist regist;
	ChiakiLog *log;
	pl_regist_cb cb;
	void *ctx;
};

static void regist_cb(ChiakiRegistEvent *event, void *user)
{
	PLRegist *r = user;
	if(!r || !r->cb)
		return;
	switch(event->type)
	{
		case CHIAKI_REGIST_EVENT_TYPE_FINISHED_CANCELED:
			r->cb(PL_REGIST_CANCELED, NULL, r->ctx);
			break;
		case CHIAKI_REGIST_EVENT_TYPE_FINISHED_FAILED:
			r->cb(PL_REGIST_FAILED, NULL, r->ctx);
			break;
		case CHIAKI_REGIST_EVENT_TYPE_FINISHED_SUCCESS:
		{
			ChiakiRegisteredHost *h = event->registered_host;
			PLRegistHost out;
			memset(&out, 0, sizeof(out));
			out.target = (int)h->target;
			memcpy(out.server_nickname, h->server_nickname, sizeof(h->server_nickname));
			memcpy(out.server_mac, h->server_mac, sizeof(out.server_mac));
			memcpy(out.rp_regist_key, h->rp_regist_key, sizeof(out.rp_regist_key));
			out.rp_key_type = h->rp_key_type;
			memcpy(out.rp_key, h->rp_key, sizeof(out.rp_key));
			r->cb(PL_REGIST_SUCCESS, &out, r->ctx);
			break;
		}
	}
}

PLRegist *pl_regist_start(int target, const char *host, bool broadcast, const char *psn_online_id,
                          const uint8_t *psn_account_id, size_t psn_account_id_len, uint32_t pin,
                          const char *log_file, pl_regist_cb cb, void *ctx, int *err)
{
	ChiakiErrorCode e = CHIAKI_ERR_SUCCESS;
	PLRegist *r = calloc(1, sizeof(PLRegist));
	if(!r)
	{
		e = CHIAKI_ERR_MEMORY;
		goto beach;
	}
	r->log = log_new(log_file, true);
	r->cb = cb;
	r->ctx = ctx;

	ChiakiRegistInfo info;
	memset(&info, 0, sizeof(info));
	info.target = (ChiakiTarget)target;
	info.host = host;
	info.broadcast = broadcast;
	info.psn_online_id = psn_online_id;
	if(psn_account_id && psn_account_id_len == sizeof(info.psn_account_id))
		memcpy(info.psn_account_id, psn_account_id, sizeof(info.psn_account_id));
	info.pin = pin;

	e = chiaki_regist_start(&r->regist, r->log, &info, regist_cb, r);
	if(e != CHIAKI_ERR_SUCCESS)
	{
		log_free(r->log);
		free(r);
		r = NULL;
	}
beach:
	if(err)
		*err = (int)e;
	return r;
}

void pl_regist_stop(PLRegist *r)
{
	if(r)
		chiaki_regist_stop(&r->regist);
}

void pl_regist_free(PLRegist *r)
{
	if(!r)
		return;
	chiaki_regist_fini(&r->regist);
	log_free(r->log);
	free(r);
}
