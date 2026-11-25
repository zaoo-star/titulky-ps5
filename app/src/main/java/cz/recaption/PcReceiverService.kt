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
import cz.recaption.core.PcWake
import cz.recaption.core.WsClient

/**
 * Titulky PS5 – „Překlad z PC“ (přání majitele 25. 11. 2026): Android TV nečte obraz ani nepřekládá, jen ukazuje
 * titulky, které posílá ReCaption pro PC v režimu Čtečka pro TV (pc/, PC je připojené k PS5 druhým účtem).
 * PC je stejný server jako pro aplikace v TV LG / Samsung ([cz.recaption.core.TvSender]); tahle služba dělá totéž
 * co `tizen/app/app.js`: najde PC v síti ([PcFinder]), přečte zprávy ([PcMessage]) a kreslí je stejným pruhem jako
 * běžný překlad ([SubtitleOverlay] s nastavením z Titulků).
 */
class PcReceiverService : Service() {
    enum class State { OFF, SEARCHING, CONNECTED }

    companion object {
        @Volatile var state = State.OFF
            private set
        /** Adresa připojeného PC (jen pro Domů). */
        @Volatile var pcAddress: String? = null
            private set
        /** Kolik titulků z PC přišlo od spuštění (jen pro Domů). */
        @Volatile var shownCount = 0
            private set
        /**
         * PC, které odpovědělo, že PS5 spárovanou nemá, a ještě mu nebylo spárování posláno (hráč ho potvrdí na Domů –
         * klíče PS5 se nepošlou jen tak komukoli v síti, kdo na povel odpoví).
         */
        @Volatile var pairRequest: PcWake.Reply? = null
            private set

        /** Hráč potvrdil: poslat spárování z TV do PC [pairRequest] a tomu PC dál věřit. */
        fun sendPairing(c: Context) {
            val r = pairRequest ?: return
            val to = r.from ?: return
            pairRequest = null
            val app = c.applicationContext
            Thread({ pairTo(app, to, r.name); Prefs.setPcTrusted(app, to) }, "tp-pc-pair").apply { isDaemon = true; start() }
        }

        private fun pairTo(c: Context, to: String, name: String) {
            val host = Prefs.ps5Host(c) ?: return
            PcWake.sendPairing(PcWake.Pairing(host.name, host.ip, host.registKey, host.morning, host.mac,
                Prefs.psnId(c).ifBlank { null }, Prefs.psnName(c).ifBlank { null }), to)
            try { DiagLog.add("PC|spárování PS5 posláno do PC $name") } catch (_: Throwable) {}
        }

        /** Jméno PC z odpovědi na povel (jen pro Domů). */
        @Volatile var pcName: String? = null
            private set

        const val ACTION_STOP = "cz.titulkyps5.PC_STOP"
        /** Po takovém tichu od PC se ověří, že PC pořád odpovídá. */
        private const val IDLE_MS = 30_000
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
        // program na PC čtení zastaví (PC zůstane zapnuté)
        Thread({ PcWake.send("zastav", waitMs = 0) }, "tp-pc-stop").apply { isDaemon = true; start() }
        running = false
        client?.close()
        worker?.interrupt()
        overlay?.destroy(); overlay = null
        state = State.OFF; pcAddress = null; pairRequest = null
        log("zastaveno")
        super.onDestroy()
    }

    private fun log(s: String) { try { DiagLog.add("PC|$s") } catch (_: Throwable) {} }

    private fun loop() {
        while (running) {
            state = State.SEARCHING; pcAddress = null
            update()
            // povel pro program na PC: přepnout na Čtečku pro TV a spustit (a probudit PC, když známe jeho MAC)
            val replies = PcWake.send("spust", Prefs.pcMac(this))
            replies.firstOrNull { it.mac != null }?.let { r ->
                if (r.mac != Prefs.pcMac(this)) Prefs.setPcMac(this, r.mac)
                pcName = r.name
            }
            if (replies.isNotEmpty()) log("PC odpovědělo na povel: ${replies.joinToString { it.name }}")
            // PC ještě nemá PS5 spárovanou: spárování z TV jen PC, kterému to hráč už jednou potvrdil; jinak se zeptá na Domů
            if (Prefs.ps5Host(this) != null) for (r in replies) if (r.paired == false && r.from != null) {
                if (r.from == Prefs.pcTrusted(this)) pairTo(this, r.from, r.name)
                else if (pairRequest?.from != r.from) { pairRequest = r; log("PC ${r.name} nemá PS5 spárovanou · čeká na potvrzení") }
            }
            if (replies.any { it.paired == true && it.from == pairRequest?.from }) pairRequest = null
            val c = PcFinder.find(Prefs.pcAddress(this), stop = { !running })
            if (c == null) { sleep(5000); continue }
            client = c
            pcAddress = c.address
            Prefs.setPcAddress(this, c.address)
            state = State.CONNECTED
            log("připojeno k ${c.address}")
            update()
            c.send(PcMessage.announce(Build.MODEL ?: "Android TV", PcMessage.RECEIVER_VERSION))
            // PC posílá jen změny, takže dlouhé ticho je běžné; po 30 s ticha se jen ověří, že PC pořád odpovídá
            // (uspané PC spojení samo neukončí a TV by na něj čekala navždy)
            c.idleTimeout(IDLE_MS)
            try {
                while (running) {
                    val t = try { c.read() } catch (_: WsClient.Idle) {
                        val alive = PcFinder.probe(c.address, timeoutMs = 2000)?.also { it.close() } != null
                        if (!alive) { log("PC neodpovídá"); break }
                        continue
                    } ?: break
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
                // vzhled z PC (písmo, pozadí, barva, poloha…): jiný než minule = nový pruh s ním
                val lk = m.look
                val use = if (lk != null && lk != look) {
                    look = lk
                    o.destroy()
                    makeOverlay(lk).also { overlay = it }
                } else o
                // originál posílá PC jen se zapnutou volbou na PC; bez vzhledu z PC (starší PC) podle nastavení v TV
                use.show(m.segments, if (lk != null || Prefs.showOriginal(this)) m.original else null, m.top, m.bottom, m.left, m.right)
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

    /** Poslední vzhled z PC (null = zatím žádný, platí nastavení v TV). */
    @Volatile private var look: cz.recaption.core.PcLook? = null

    private fun makeOverlay(pc: cz.recaption.core.PcLook? = look): SubtitleOverlay {
        val (w, h) = screenSize()
        return SubtitleOverlay(
            this, w, h, pc?.layout(Prefs.layout(this)) ?: Prefs.layout(this), pc?.textSp ?: Prefs.textSize(this), pc?.opacity ?: Prefs.opacity(this),
            false, Prefs.coloredSpeaker(this),
            pc?.color ?: Prefs.TEXT_COLORS[Prefs.textColorIndex(this)], pc?.bold ?: Prefs.boldText(this), false,
            barPad = pc?.pad ?: Prefs.barPad(this),
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
