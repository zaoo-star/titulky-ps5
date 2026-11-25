package cz.recaption

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Nová verze i bez otevřené aplikace (od 0.97.74, přání majitele: „kontrolu i mimo otevření“):
 *  - když běží jen překlad (aplikace zavřená, hraje se), služba se podívá každých 30 min ([TranslatorService]),
 *  - když neběží nic, systém spustí [UpdateJob] zhruba jednou za 3 hodiny (jen se sítí; JobScheduler, žádná knihovna).
 * Novou verzi ohlásí upozornění „Nová verze ReCaption X“ (klepnutí otevře aplikaci, kde je Aktualizovat na Domů); každá verze
 * jen jednou (`upd_ohlaseno`). Samo nic neinstaluje – Android to bez potvrzení nedovolí. Jeden malý dotaz na `version.txt`.
 */
object UpdateNotifier {
    private const val CHANNEL = "nove_verze"
    private const val NOTIFY_ID = 7301
    private const val JOB_ID = 7302
    private const val KEY_NOTIFIED = "upd_ohlaseno"
    const val SERVICE_EVERY_MS = 30 * 60_000L
    private const val JOB_EVERY_MS = 3 * 60 * 60_000L

    /** Naplánuje kontrolu na pozadí (volá se při každém otevření aplikace; stejná úloha se jen přepíše). */
    fun schedule(c: Context) {
        try {
            val js = c.getSystemService(JobScheduler::class.java) ?: return
            if (js.getPendingJob(JOB_ID) != null) return
            js.schedule(JobInfo.Builder(JOB_ID, ComponentName(c, UpdateJob::class.java))
                .setPeriodic(JOB_EVERY_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build())
        } catch (_: Throwable) {}
    }

    /** Zjistí novou verzi a případně ohlásí (volat mimo hlavní vlákno). */
    fun check(c: Context) {
        val app = c.applicationContext
        if (Updater.busy || UiState.visible) return       // s otevřenou aplikací to ukáže Domů
        val offer = try { Updater.offer(Prefs.testChannel(app)) } catch (_: Throwable) { null } ?: return
        val sp = app.getSharedPreferences("aktualizace", Context.MODE_PRIVATE)
        if (sp.getInt(KEY_NOTIFIED, 0) >= offer.remote.code) return
        sp.edit().putInt(KEY_NOTIFIED, offer.remote.code).apply()
        notify(app, offer.remote.name)
        EventFile.add("UPD|ohlášena nová verze ${offer.remote.name}")
    }

    private fun notify(c: Context, name: String) {
        try {
            val nm = c.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(NotificationChannel(CHANNEL, c.tx("un_channel"), NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(c, 7303, Intent(c, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(c.tx("un_title").replace("%s", name))
                .setContentText(c.tx("un_text"))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFY_ID, n)
        } catch (_: Throwable) {}
    }
}

/** Kontrola nové verze, kterou spouští systém, i když ReCaption neběží ([UpdateNotifier.schedule]). */
class UpdateJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread({
            try { UpdateNotifier.check(this) } catch (_: Throwable) {}
            jobFinished(params, false)
        }, "tp-nova-verze").apply { isDaemon = true; start() }
        return true
    }

    override fun onStopJob(params: JobParameters) = false
}
