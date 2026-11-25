package cz.recaption

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import cz.recaption.core.DiagLog
import cz.recaption.core.PcFinder
import cz.recaption.core.PcMessage
import cz.recaption.core.WsClient

/**
 * Titulky PS5 – „Překlad z PC“ (přání majitele 25. 11. 2026): Android TV nečte obraz ani nepřekládá, jen ukazuje
 * titulky, které posílá ReCaption pro PC v režimu Čtečka pro TV (pc/, PC je připojené k PS5 druhým účtem).
 * PC je stejný server jako pro aplikace v TV LG / Samsung ([cz.recaption.core.TvSender]); tahle služba dělá totéž
 * co `tizen/app/app.js`: najde PC v síti ([PcFinder]), přečte zprávy ([PcMessage]) a kreslí je stejným pruhem jako
 * běžný překlad ([SubtitleOverlay] s nastavením z Titulků).
 */
class PcReceiverService : Service() {
    companion object {
        enum class State { OFF, SEARCHING, CONNECTED }
        @Volatile var state = State.OFF
            private set
        /** Adresa připojeného PC (jen pro Domů). */
        @Volatile var pcAddress: String? = null
            private set
        /** Kolik titulků z PC přišlo od spuštění (jen pro Domů). */
        @Volatile var shownCount = 0
            private set

        const val ACTION_STOP = "cz.titulkyps5.PC_STOP"
        private const val CHANNEL = "pc_prijem"
        private const val NOTIF_ID = 7301

        fun start(c: Context) {
            val i = Intent(c, PcReceiverService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }
        fun stop(c: Context) { c.stopService(Intent(c, PcReceiverService::class.java)) }
    }

    @Volatile private var running = false
    @Volatile private var client: WsClient? = null
    private var worker: Thread? = null
    private var overlay: SubtitleOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        startForegroundCompat(notification())
        if (!running) {
            running = true
            shownCount = 0
            overlay = makeOverlay()
            worker = Thread({ loop() }, "tp-pc").apply { isDaemon = true; start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        client?.close()
        worker?.interrupt()
        overlay?.destroy(); overlay = null
        state = State.OFF; pcAddress = null
        log("zastaveno")
        super.onDestroy()
    }

    private fun log(s: String) { try { DiagLog.add("PC|$s") } catch (_: Throwable) {} }

    private fun loop() {
        while (running) {
            state = State.SEARCHING; pcAddress = null
            update()
            val c = PcFinder.find(Prefs.pcAddress(this), stop = { !running })
            if (c == null) { sleep(5000); continue }
            client = c
            pcAddress = c.address
            Prefs.setPcAddress(this, c.address)
            state = State.CONNECTED
            log("připojeno k ${c.address}")
            update()
            c.send(PcMessage.announce(Build.MODEL ?: "Android TV", PcMessage.RECEIVER_VERSION))
            try {
                while (running) {
                    val t = c.read() ?: break
                    val m = PcMessage.parse(t) ?: continue
                    handle(m, c)
                }
            } catch (_: Throwable) {}
            c.close(); client = null
            overlay?.hide(); overlay?.hideObjective(); overlay?.hidePhone()
            if (running) { log("spojení s PC se ztratilo, hledám znovu"); sleep(2000) }
        }
    }

    private fun handle(m: PcMessage, c: WsClient) {
        val o = overlay ?: return
        when (m) {
            is PcMessage.Show -> {
                o.show(m.segments, if (Prefs.showOriginal(this)) m.original else null, m.top, m.bottom, m.left, m.right)
                shownCount++
                c.send(PcMessage.done(m.id))
            }
            is PcMessage.Hide -> { o.hide(); c.send(PcMessage.done(m.id)) }
            is PcMessage.Objective ->
                if (m.text.isBlank() || m.box == null) o.hideObjective() else o.showObjective(m.text, m.box, 0f)
            is PcMessage.Phone ->
                if (m.msgs.isEmpty() || m.box == null) o.hidePhone() else o.showPhone(m.title, m.msgs, m.box)
            is PcMessage.PhoneOver -> {
                if (m.items.isEmpty()) { o.hidePhone(); return }
                val b = m.items.map { it.second }
                val side = floatArrayOf(b.minOf { it[0] }, b.minOf { it[1] }, b.maxOf { it[2] }, b.maxOf { it[3] })
                o.showPhoneOver(m.items, m.px / 1080f, side, m.beside)
            }
            PcMessage.Hello -> {}
        }
    }

    private fun sleep(ms: Long) { try { Thread.sleep(ms) } catch (_: InterruptedException) {} }

    private fun makeOverlay(): SubtitleOverlay {
        val (w, h) = screenSize()
        return SubtitleOverlay(
            this, w, h, Prefs.layout(this), Prefs.textSize(this), Prefs.opacity(this),
            false, Prefs.coloredSpeaker(this),
            Prefs.TEXT_COLORS[Prefs.textColorIndex(this)], Prefs.boldText(this), false,
            barPad = Prefs.barPad(this),
            coverAll = Prefs.coverAll(this),
            origSp = Prefs.origSp(this),
            objSizeSp = Prefs.objSize(this),
            objPosPercent = Prefs.objPos(this),
            objBgPercent = Prefs.objBg(this),
            showPrompts = Prefs.gamePrompts(this),
        )
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Titulky PS5 – překlad z PC", NotificationManager.IMPORTANCE_LOW))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val stopPi = PendingIntent.getService(this, 11, Intent(this, PcReceiverService::class.java).setAction(ACTION_STOP), flags)
        val openPi = PendingIntent.getActivity(this, 12, Intent(this, MainActivity::class.java), flags)
        val text = when (state) {
            State.CONNECTED -> "PC · připojeno"
            else -> "PC · hledám v síti"
        }
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Titulky PS5 · překlad z PC")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Zastavit", stopPi).build())
            .build()
    }

    private fun update() {
        try { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification()) } catch (_: Exception) {}
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIF_ID, n)
    }
}
