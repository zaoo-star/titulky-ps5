package cz.recaption

import android.content.Context
import cz.recaption.core.LgDevTime
import cz.recaption.core.LgInstall
import cz.recaption.core.TvType
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/**
 * Kolik dní zbývá vývojářskému režimu televize LG. Klíč relace se přečte z televize při spojení
 * instalace ([LgInstaller]); tady se jen zeptá služby LG (HTTPS, bez přesměrování), nejvýš jednou
 * za hodinu (ručně kdykoli). Prodlužuje se jen se zapnutou volbou „Prodlužovat vývojářský režim“
 * ([renew], nejvýš jednou denně, přání majitele 23. 11. 2026); jinak to dělá uživatel v televizi
 * v aplikaci Developer Mode (Extend). Klíč relace ani adresa s ním se nikam nepíšou.
 *
 * Podle LgDevMode.kt z projektu PlayCaption (AGPL-3.0).
 */
object LgDevMode {
    private val busy = AtomicBoolean(false)
    @Volatile var failed = false
        private set

    /** Poslední známý stav pro televizi, kam se naposledy instalovalo (null = zatím nevíme). */
    fun known(context: Context): LgDevTime? {
        val v = LgVault(context)
        val ip = v.lastIp.ifEmpty { return null }
        return v.devTime(ip)?.takeIf { it.fresh(System.currentTimeMillis()) }
    }

    fun hasSession(context: Context): Boolean = LgVault(context).let { v -> v.lastIp.isNotEmpty() && v.token(v.lastIp) != null }

    /** Na pozadí se zeptá služby LG; [done] se zavolá (z vlákna na pozadí), jen když se opravdu ptala. */
    fun refresh(context: Context, manual: Boolean, done: () -> Unit) {
        val app = context.applicationContext
        val vault = LgVault(app)
        val ip = vault.lastIp.ifEmpty { return }
        val now = System.currentTimeMillis()
        if (!LgDevTime.shouldRefresh(vault.lastTry(ip), now, manual)) return
        val token = vault.token(ip) ?: return
        if (!busy.compareAndSet(false, true)) return
        vault.setLastTry(ip, now)
        Thread({
            try {
                val t = LgDevTime.parse(fetch(token), System.currentTimeMillis())
                if (vault.token(ip) == token) vault.saveDevTime(ip, t)
                failed = false
            } catch (_: Exception) {
                failed = true
            } finally {
                busy.set(false)
                done()
            }
        }, "tp-lg-devmode").apply { isDaemon = true }.start()
    }

    private val renewing = AtomicBoolean(false)
    /** Poslední pokus o prodloužení se nepovedl (řádek dnů režimu to řekne). */
    @Volatile var renewFailed = false
        private set

    /**
     * Prodloužení vývojářského režimu u služby LG (jako Extend v aplikaci Developer Mode v TV). Jen se zapnutou
     * volbou ([Prefs.lgDevRenew]), typem TV LG a známým klíčem relace; nejvýš jednou denně, po chybě znovu za 3 h
     * ([LgDevTime.shouldRenew]). Na pozadí; před vláknem jen levné čtení nastavení (klíč se dešifruje až ve vlákně),
     * takže se smí volat často (otevření aplikace, služba). Po úspěchu se rovnou zjistí nový zbývající čas.
     * [done] se zavolá (z vlákna na pozadí), jen když se opravdu prodlužovalo.
     */
    fun renew(context: Context, done: (() -> Unit)? = null) {
        val app = context.applicationContext
        if (!Prefs.lgDevRenew(app) || Prefs.tvType(app) != TvType.LG) return
        val vault = LgVault(app)
        val ip = vault.lastIp.ifEmpty { return }
        val now = System.currentTimeMillis()
        if (!LgDevTime.shouldRenew(vault.renewTry(ip), now, vault.devTime(ip))) return
        if (!renewing.compareAndSet(false, true)) return
        Thread({
            var asked = false
            try {
                // bez klíče relace (TV ještě nikdy nenahraná) nic – a znovu se klíč nedešifruje dřív než za 3 h
                val token = vault.token(ip) ?: run { vault.setRenewTry(ip, LgDevTime.failedTry(now)); return@Thread }
                asked = true
                vault.setRenewTry(ip, now)
                LgDevTime.renewed(fetch(LgInstall.RESET_URL, token))
                renewFailed = false
                log("LGDEV|renew|ok")
                // nový zbývající čas (a hodinová kontrola se hned znovu neptá)
                try {
                    val t = LgDevTime.parse(fetch(LgInstall.CHECK_URL, token), System.currentTimeMillis())
                    if (vault.token(ip) == token) { vault.saveDevTime(ip, t); vault.setLastTry(ip, System.currentTimeMillis()) }
                    failed = false
                } catch (_: Exception) {}
            } catch (e: Exception) {
                renewFailed = true
                vault.setRenewTry(ip, LgDevTime.failedTry(now))
                // jen vlastní popis chyby nebo druh výjimky – nikdy adresa s klíčem relace
                val why = if (e is IllegalArgumentException || e is IllegalStateException) e.message.orEmpty() else e.javaClass.simpleName
                log("LGDEV|renew|chyba $why")
            } finally {
                renewing.set(false)
                if (asked) done?.invoke()
            }
        }, "tp-lg-renew").apply { isDaemon = true }.start()
    }

    private fun log(line: String) {
        try { EventFile.add(line); cz.recaption.core.DiagLog.add(line) } catch (_: Throwable) {}
    }

    private fun fetch(token: String): String = fetch(LgInstall.CHECK_URL, token)

    private fun fetch(base: String, token: String): String {
        require(token.matches(Regex("[A-Za-z0-9]{8,256}"))) { "LG: tvar klíče relace" }
        val conn = URL(base + token).openConnection() as HttpsURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val code = conn.responseCode
            check(code == 200) { "LG: http $code" }
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(2048)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    require(out.size() + n <= 16384)
                    out.write(buf, 0, n)
                }
            }
            return out.toString("UTF-8")
        } finally { conn.disconnect() }
    }
}
