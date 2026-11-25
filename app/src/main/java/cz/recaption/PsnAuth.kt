package cz.recaption

import android.util.Base64
import android.util.Log
import cz.recaption.core.DiagLog
import cz.recaption.core.PhoneLogin
import cz.recaption.core.Ps5
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Přihlášení účtem PlayStation. Uživatel se přihlásí na stránce Sony ve svém telefonu a aplikace
 * dostane jednorázový kód: z adresy vložené na stránce televize, z ReCaptionu v telefonu, nebo
 * (záloha) opsaný ručně. Aplikace z něj potřebuje jedinou věc: číslo účtu, které konzole vyžaduje
 * při párování. Heslo aplikace neukládá. Stejný postup používá otevřený klient chiaki-ng.
 */
object PsnAuth {
    private const val TAG = "TestPreklad"
    private const val CLIENT_ID = "ba495a24-818c-472b-b12d-ff231c1b5745"
    private const val CLIENT_SECRET = "mvaiZkRsAsI1IBkY"
    /** Sem Sony po přihlášení přesměruje; v adresě je jednorázový kód. */
    const val REDIRECT = "https://remoteplay.dl.playstation.net/remoteplay/redirect"
    private const val TOKEN_URL = "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/token"

    /** Přihlašovací stránka Sony (do QR kódu). */
    const val LOGIN_URL = "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/authorize" +
        "?service_entity=urn:service-entity:psn&response_type=code&client_id=$CLIENT_ID" +
        "&redirect_uri=$REDIRECT&scope=psn:clientapp&request_locale=en_US&ui=pr&service_logo=ps" +
        "&layout_type=popup&smcid=remoteplay&prompt=always&PlatformPrivacyWs1=minimal&"

    /** Vymění jednorázový kód za číslo účtu. Volat mimo hlavní vlákno. */
    fun accountIdForCode(code: String): String? = accountForCode(code)?.id

    /** Vymění jednorázový kód za číslo účtu a jméno účtu (Online ID). Volat mimo hlavní vlákno. */
    fun accountForCode(code: String): PhoneLogin.Account? {
        val auth = "Basic " + Base64.encodeToString("$CLIENT_ID:$CLIENT_SECRET".toByteArray(), Base64.NO_WRAP)
        return try {
            val body = "grant_type=authorization_code&code=" + URLEncoder.encode(code, "UTF-8") + "&redirect_uri=$REDIRECT&"
            val c = URL(TOKEN_URL).openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 10000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Authorization", auth)
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(body.toByteArray()) }
            val tokenJson = try {
                if (c.responseCode != 200) { DiagLog.add("PSN|token http ${c.responseCode}"); return null }
                c.inputStream.bufferedReader().use { it.readText().take(8000) }
            } finally { c.disconnect() }
            val token = Ps5.accessTokenFrom(tokenJson) ?: run { DiagLog.add("PSN|no token"); return null }

            val c2 = URL(TOKEN_URL + "/" + URLEncoder.encode(token, "UTF-8")).openConnection() as HttpURLConnection
            c2.connectTimeout = 10000; c2.readTimeout = 10000
            c2.setRequestProperty("Authorization", auth)
            val info = try {
                if (c2.responseCode != 200) { DiagLog.add("PSN|info http ${c2.responseCode}"); return null }
                c2.inputStream.bufferedReader().use { it.readText().take(8000) }
            } finally { c2.disconnect() }
            val id = Ps5.accountIdFromUserInfo(info)
            DiagLog.add("PSN|account " + if (id != null) "ok" else "missing")
            id?.let { PhoneLogin.Account(it, PhoneLogin.nameFromUserInfo(info) ?: "") }
        } catch (e: Exception) {
            Log.w(TAG, "psn", e)
            DiagLog.add("PSN|error " + e.javaClass.simpleName)
            null
        }
    }
}
