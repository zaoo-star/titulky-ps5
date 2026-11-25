package cz.recaption

import android.content.Context
import cz.recaption.core.LgAction
import cz.recaption.core.LgError
import cz.recaption.core.LgException
import cz.recaption.core.LgInstall
import cz.recaption.core.LgPhase

/**
 * Aktualizace aplikace v TV LG přímo z Domů (od 0.97.93, přání majitele: „Aktualizovat“ otevíralo další stránku a nahrávalo
 * až tam). Jen u spárované TV se známou adresou ([direct]); jinak dál stránka s přípravou ([LgInstallActivity]).
 * Běží na pozadí, Domů ukazuje krok a pruh; po chybě řádek s cestou na stránku s přípravou.
 */
object LgUpdate {
    @Volatile var running = false; private set
    @Volatile var phase: LgPhase? = null; private set
    @Volatile var error: LgError? = null; private set
    @Volatile private var installer: LgInstaller? = null

    /** Spárovaná TV se známou adresou – dá se aktualizovat bez stránky s přípravou. */
    fun direct(c: Context): Boolean = runCatching {
        val v = LgVault(c)
        v.lastIp.isNotEmpty() && v.paired(LgInstall.address(v.lastIp))
    }.getOrDefault(false)

    fun start(c: Context, onChange: () -> Unit) {
        if (running) return
        val app = c.applicationContext
        val ip = LgVault(app).lastIp
        val inst = LgInstaller(app)
        installer = inst
        running = true; phase = LgPhase.PACKAGE; error = null
        onChange()
        Thread({
            try {
                val r = inst.run(ip, "", false) { p -> phase = p; onChange() }
                recordInstalled(app, r)
            } catch (e: LgException) {
                if (e.code != LgError.CANCELLED) fail(e)
            } catch (e: Exception) {
                fail(LgException(LgError.SSH, e.javaClass.simpleName))
            } finally {
                running = false; installer = null; phase = null
                onChange()
            }
        }, "tp-lg-update").start()
    }

    fun cancel() { installer?.cancel() }

    /** Chyba už je vidět / hráč šel na stránku s přípravou. */
    fun clearError() { error = null }

    private fun fail(e: LgException) {
        error = e.code
        val line = "LG|chyba ${e.code}" + (e.detail?.let { " · $it" } ?: "") + " (z Domů)"
        EventFile.add(line); cz.recaption.core.DiagLog.add(line)
    }

    /**
     * Verze v TV je teď ta nahraná: Domů ji ví hned, i když se aplikace v TV ještě znovu nepřipojila
     * (do 0.97.21 tam zůstávalo staré ohlášení a Domů dál hlásilo „nová …“). Společné s [LgInstallActivity].
     */
    fun recordInstalled(c: Context, r: LgInstaller.Result) {
        if (r.action != LgAction.INSTALL && r.action != LgAction.UPDATE) return
        val old = cz.recaption.core.TvSender.lastSeen?.announce?.takeIf { it.platform == cz.recaption.core.TvAnnounce.WEBOS }
        if (old != null) cz.recaption.core.TvSender.lastSeen = cz.recaption.core.TvSender.Companion.Seen(old.copy(version = r.version), System.currentTimeMillis())
        Prefs.setTvSeen(c, cz.recaption.core.TvAnnounce.WEBOS, listOfNotNull(old?.model, r.version).joinToString(" · "))
    }
}
