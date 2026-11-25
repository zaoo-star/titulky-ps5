package cz.recaption

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import cz.recaption.core.Ps5
import cz.recaption.core.XboxFail
import cz.recaption.core.XboxInput
import cz.recaption.core.XboxMessages
import cz.recaption.core.XboxRetry
import cz.recaption.core.XboxSession
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Spojení s vlastním Xboxem (vzdálené hraní „xHome“ přes WebRTC) – jen příjem obrazu pro čtení
 * titulků. Hraje se dál ovladačem u konzole, obraz i zvuk jdou do televize kabelem HDMI.
 * Neposílá se žádné ovládání a zvuk se nepřehrává (stopa je vypnutá, reproduktor ztlumený).
 *
 * Obraz: nízký datový tok, 720p, přednostně H.264. Každý snímek, o který čtečka stojí, se převede
 * na I420 a jeho jas jde do [StreamReader.feedI420] – dál stejná cesta jako u PlayStationu.
 * Stav spojení je ve stejném tvaru jako u PlayStationu ([Ps5.Link]), takže stav na stránce Domů
 * a v hlášení funguje pro obě konzole. Kdy to zkusit znovu, rozhoduje [XboxRetry] (core, s testy).
 * Vypnutý nebo spící Xbox se nebudí – jen se občas zeptá, jestli už je zapnutý.
 *
 * Podle XboxSpojeni.kt z projektu PlayCaption (AGPL-3.0), který vychází z xbox-xcloud-player
 * (unknownskl, MIT) a XStreaming (Geocld, AGPL-3.0); upraveno pro ReCaption (zdroj snímků pro
 * StreamReader, opakování spojení, čtení odpovědí a úpravy SDP v core/Xbox.kt).
 * Navíc podle XStreaming (1.0.x): zpětná vazba o zobrazených snímcích na kanálu input
 * ([XboxInput.metadataPacket]), odpovědi „neumíme“ na zprávy Xboxu ([XboxMessages.reply]),
 * adresa relace z „transferUri“, obnova streamovacího tokenu po 401/403 a kandidáti s portem 3074.
 * „Připojeno“ až s prvním snímkem (jako PlayCaption), ne už po spojení ICE.
 */
class XboxLink(
    private val ctx: Context,
    private val consoleId: String,
    private val reader: StreamReader,
    /** Dekódovat na grafickém čipu (televize, box); false = do paměti (telefony – některé z GPU vracely jen černou). */
    private val gpu: Boolean,
) : ConsoleLink {
    companion object {
        @Volatile var state: Ps5.Link = Ps5.Link.OFF
            private set
        /** Poslední důvod odpojení (klíč textu xb_e_…), nebo null. */
        @Volatile var reason: String? = null
            private set
        /** Kdy se navázalo právě běžící spojení (uptime, 0 = nespojeno). */
        @Volatile var connectedSince = 0L
            private set

        @Volatile private var webrtcReady = false

        private fun log(m: String) = XboxAccount.log(m)
    }

    private val thread = HandlerThread("tp-xbox").apply { start() }
    private val handler = Handler(thread.looper)
    private val policy = XboxRetry()
    @Volatile private var session: Session? = null
    @Volatile private var stopped = false
    private var seenOff = false

    override fun start() {
        stopped = false
        handler.post { attempt() }
    }

    override val connectedForMs: Long get() = if (state == Ps5.Link.CONNECTED && connectedSince > 0) SystemClock.uptimeMillis() - connectedSince else 0

    override fun restart(why: String) {
        handler.post {
            if (stopped) return@post
            log("restart: $why")
            session?.close(); session = null
            failed(XboxFail.NO_VIDEO)
        }
    }

    override fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        val s = session
        session = null
        s?.close()
        state = Ps5.Link.OFF
        connectedSince = 0
        thread.quitSafely()
    }

    /** Jeden pokus o spojení (na vlákně tp-xbox; smí blokovat). */
    private fun attempt() {
        if (stopped || session != null) return
        state = Ps5.Link.CONNECTING
        connectedSince = 0
        try {
            val c = XboxAccount.consoles(ctx).firstOrNull { it.id == consoleId }
                ?: throw XboxAccount.XboxException(XboxFail.NOT_FOUND, "konzole není na účtu")
            if (!c.isOn) {
                if (!seenOff) log("Xbox ${c.powerState} – čekám, až ho hráč zapne")
                seenOff = true
                failed(XboxFail.OFFLINE)
                return
            }
            if (seenOff) {
                // Xbox se právě zapnul: nejdřív ať se připojí ovladač hráče
                seenOff = false
                state = Ps5.Link.WAITING
                handler.postDelayed({ attempt() }, XboxRetry.AFTER_WAKE_MS)
                return
            }
            val s = Session()
            session = s
            s.open()
        } catch (e: XboxAccount.XboxException) {
            log("chyba ${e.fail}: ${e.message}")
            session?.close(); session = null
            failed(e.fail)
        } catch (e: Throwable) {
            log("chyba ${e.javaClass.simpleName}: ${e.message?.take(80)}")
            session?.close(); session = null
            failed(XboxFail.NETWORK)
        }
    }

    private fun failed(f: XboxFail) {
        if (stopped) return
        val d = policy.failed(f, SystemClock.uptimeMillis())
        reason = f.textKey
        state = d.state
        connectedSince = 0
        log("stav ${d.state} (${f.name.lowercase()})" + (d.retryInMs?.let { ", znovu za ${it / 1000} s" } ?: ""))
        d.retryInMs?.let { handler.postDelayed({ attempt() }, it) }
    }

    /** Relace skončila (z vlákna WebRTC nebo relace) → uklidit a rozhodnout na vlákně tp-xbox. */
    private fun ended(s: Session, f: XboxFail) {
        handler.post {
            if (session !== s) return@post
            session = null
            s.close()
            failed(f)
        }
    }

    /** První snímek z Xboxu – teprve teď je „připojeno“ (dřív už po spojení ICE, i když obraz nešel). */
    private fun onConnected(s: Session, at: Long) {
        handler.post {
            if (session !== s || stopped) return@post
            log("spojeno (obraz chodí)")
            state = Ps5.Link.CONNECTED
            reason = null
            connectedSince = at
            policy.connected(connectedSince)
        }
    }

    // ------------------------------------------------------------------ jedna relace

    private inner class Session {
        @Volatile private var closed = false
        @Volatile private var stream = XboxAccount.stream(ctx)
        private var url: String? = null
        /** Cesta relace z /play; adresa [url] = tahle cesta na regionu nebo na „transferUri“. */
        private var path: String? = null
        private var lastState: String? = null
        private var egl: EglBase? = null
        private var adm: JavaAudioDeviceModule? = null
        private var factory: PeerConnectionFactory? = null
        private var pc: PeerConnection? = null
        private val channels = HashMap<String, DataChannel>()
        private val seenChannels = HashSet<String>()
        private val local = ArrayList<IceCandidate>()
        private val gathered = CountDownLatch(1)
        private val st = HandlerThread("tp-xbox-relace").apply { start() }
        private val h = Handler(st.looper)
        @Volatile private var frames = 0L
        @Volatile private var lastFrame = 0L
        @Volatile private var lastKeyframeAsk = 0L
        @Volatile private var iceConnectedAt = 0L
        /** Pořadové číslo paketů na kanálu input (0 = popis klienta, pak zpětná vazba 1, 2, …). */
        private var inputSeq = 0L
        private var feedbackSent = 0L

        private fun get(what: String) = call("GET", what, null)
        private fun post(what: String, body: String) = call("POST", what, body)

        /**
         * Požadavek relace. Když služba řekne, že streamovací token už neplatí (401/403), token se
         * obnoví a požadavek jednou zopakuje (XStreaming requestWithAuthRetry) – při delším hraní
         * token vyprší a keepalive by jinak jen hlásil 401, než relace spadne.
         */
        private fun call(method: String, what: String, body: String?): Pair<Int, String> {
            val r = XboxAccount.http(method, "$url/$what", body, headers = XboxAccount.gssvHeaders(stream))
            if (!XboxSession.authExpired(r.first) || closed) return r
            log("$what ${r.first} → obnovuji token")
            val fresh = try { XboxAccount.renewStream(ctx) } catch (e: XboxAccount.XboxException) {
                log("obnova tokenu: ${e.fail}"); return r
            }
            stream = fresh
            val again = XboxAccount.http(method, "$url/$what", body, headers = XboxAccount.gssvHeaders(stream))
            log("$what po obnově ${again.first}")
            return again
        }

        fun open() {
            // 1) založit relaci
            val start = JSONObject()
                .put("clientSessionId", "").put("titleId", "").put("systemUpdateGroup", "")
                .put("settings", JSONObject()
                    .put("nanoVersion", "V3;WebrtcTransport.dll")
                    .put("enableTextToSpeech", false).put("highContrast", 0)
                    .put("locale", "en-US").put("useIceConnection", false)
                    .put("timezoneOffsetMinutes", 60).put("sdkType", "web").put("osName", "android"))
                .put("serverId", consoleId).put("fallbackRegionNames", JSONArray())
            val (s1, t1) = XboxAccount.http("POST", "${stream.baseUri}/v5/sessions/home/play", start.toString(), headers = XboxAccount.gssvHeaders(stream))
            if (s1 == 401 || s1 == 403) throw XboxAccount.XboxException(XboxFail.AUTH, "play $s1")
            if (s1 !in 200..299) throw XboxAccount.XboxException(if (s1 == 0) XboxFail.NETWORK else XboxFail.REFUSED, "play $s1")
            val p = runCatching { JSONObject(t1).getString("sessionPath") }.getOrNull()
                ?: throw XboxAccount.XboxException(XboxFail.REFUSED, "play bez relace")
            path = p
            url = XboxSession.sessionUrl(stream.baseUri, p)
            log("relace založena")

            // 2) počkat, až je konzole připravená
            waitProvisioned()
            if (closed) return
            val keepAlive = runCatching { XboxSession.keepAliveSeconds(get("configuration").second) }.getOrDefault(20)
            log("keepalive ${keepAlive} s")

            // 3) WebRTC: nabídka, odpověď
            createPeer()
            if (closed) return
            val offer = createOffer()
            log(XboxSession.offerLine(offer.description))
            setDescription(offer, local = true)
            val body = JSONObject()
                .put("messageType", "offer").put("sdp", offer.description)
                .put("configuration", JSONObject()
                    .put("chatConfiguration", JSONObject().put("bytesPerSample", 2).put("expectedClipDurationMs", 20)
                        .put("format", JSONObject().put("codec", "opus").put("container", "webm"))
                        .put("numChannels", 1).put("sampleFrequencyHz", 24000))
                    .put("chat", ver(1, 1)).put("control", ver(1, 3))
                    .put("input", ver(1, 8)).put("message", ver(1, 1)))
            val (s2, _) = post("sdp", body.toString())
            if (s2 !in 200..299) throw XboxAccount.XboxException(XboxFail.REFUSED, "sdp $s2")
            val answer = XboxSession.sdpAnswer(waitExchange("sdp"))
            if (!answer.ok) throw XboxAccount.XboxException(XboxFail.REFUSED, "sdp odmítnuto")
            if (closed) return
            setDescription(SessionDescription(SessionDescription.Type.ANSWER, answer.sdp), local = false)

            // 4) ICE
            gathered.await(4, TimeUnit.SECONDS)
            val mine = JSONArray()
            synchronized(local) {
                for (c in local) mine.put(JSONObject().put("candidate", c.sdp).put("sdpMLineIndex", c.sdpMLineIndex).put("sdpMid", c.sdpMid))
            }
            val (s3, _) = post("ice", JSONObject().put("messageType", "iceCandidate").put("candidate", mine).toString())
            if (s3 !in 200..299) throw XboxAccount.XboxException(XboxFail.ICE, "ice $s3")
            val theirs = XboxSession.remoteCandidates(waitExchange("ice"))
            if (closed) return
            for (c in theirs) pc?.addIceCandidate(IceCandidate(c.mid, c.index, c.text))
            log("ICE: naše ${mine.length()}, Xbox ${theirs.size}")

            // relaci udržovat naživu
            h.postDelayed(object : Runnable {
                override fun run() {
                    if (closed) return
                    val v = post("keepalive", "{}").first
                    if (v !in 200..299) log("keepalive $v")
                    if (feedbackSent > 0) log("zpětná vazba snímků: $feedbackSent, snímků $frames")
                    h.postDelayed(this, keepAlive * 1000L)
                }
            }, keepAlive * 1000L)
        }

        private fun ver(min: Int, max: Int) = JSONObject().put("minVersion", min).put("maxVersion", max)

        private fun waitProvisioned() {
            val until = SystemClock.uptimeMillis() + 90_000
            var connectSent = false
            while (!closed && SystemClock.uptimeMillis() < until) {
                val (s, t) = get("state")
                if (s == 401 || s == 403) throw XboxAccount.XboxException(XboxFail.AUTH, "state $s")
                if (s in 200..299) {
                    val r = XboxSession.state(t)
                    if (r.name != lastState) { lastState = r.name; log("stav relace ${r.name ?: "?"}") }
                    // Xbox nabídl jinou adresu relace → další požadavky tam (XStreaming xCloud/index.ts)
                    val p = path
                    if (r.transferUri != null && p != null) {
                        val moved = XboxSession.sessionUrl(r.transferUri, p)
                        if (moved != url) { url = moved; log("relace přesunuta (transferUri)") }
                    }
                    when (r.step) {
                        XboxSession.Step.READY -> { log("Xbox připraven"); return }
                        XboxSession.Step.CONNECT -> if (!connectSent) {
                            connectSent = true
                            val (sc, _) = post("connect", JSONObject().put("userToken", XboxAccount.transferToken(ctx)).toString())
                            if (sc !in 200..299) throw XboxAccount.XboxException(XboxFail.AUTH, "connect $sc")
                        }
                        XboxSession.Step.FAILED -> throw XboxAccount.XboxException(XboxSession.failOf(r.error), "relace: ${r.error?.take(60)}")
                        XboxSession.Step.WAIT -> {}
                    }
                }
                SystemClock.sleep(1000)
            }
            if (!closed) throw XboxAccount.XboxException(XboxFail.TIMEOUT, "stav")
        }

        private fun waitExchange(what: String): String {
            val until = SystemClock.uptimeMillis() + 30_000
            while (!closed && SystemClock.uptimeMillis() < until) {
                val (s, t) = get(what)
                if (s == 200 && t.isNotBlank()) {
                    XboxSession.exchangeResponse(t)?.let { return it }
                    XboxSession.exchangeError(t)?.let { throw XboxAccount.XboxException(XboxFail.REFUSED, "$what: ${it.take(60)}") }
                }
                SystemClock.sleep(700)
            }
            throw XboxAccount.XboxException(XboxFail.TIMEOUT, what)
        }

        // ---------------- WebRTC

        private fun createPeer() {
            if (!webrtcReady) {
                PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(ctx.applicationContext).createInitializationOptions())
                webrtcReady = true
            }
            val e = if (gpu) EglBase.create().also { egl = it } else null
            // zvuk hraje televize sama z HDMI – tady nic nepřehrávat ani nenahrávat
            val a = JavaAudioDeviceModule.builder(ctx.applicationContext)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .createAudioDeviceModule()
            a.setSpeakerMute(true); a.setMicrophoneMute(true)
            adm = a
            val f = PeerConnectionFactory.builder()
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(e?.eglBaseContext))
                // kodér jako ověřený PlayCaption – bez něj může libwebrtc sestavit nabídku s jinou sadou
                // kodeků obrazu (co v ní je, zapisuje řádek „nabídka obraz“)
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(e?.eglBaseContext, true, true))
                .setAudioDeviceModule(a)
                // jen domácí síť: ne mobilní data, VPN ani loopback (hodnoty z WebRTC: 4, 8, 16)
                .setOptions(PeerConnectionFactory.Options().apply { networkIgnoreMask = 4 or 8 or 16 })
                .createPeerConnectionFactory()
            factory = f
            val cfg = PeerConnection.RTCConfiguration(emptyList()).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.LOW_COST
            }
            val p = f.createPeerConnection(cfg, observer) ?: throw XboxAccount.XboxException(XboxFail.ICE, "WebRTC")
            pc = p
            // kanály jako webový přehrávač Xboxu (bez nich Xbox spojení nepřijme)
            openChannel("chat", "chatV1")
            openChannel("control", "controlV1")
            openChannel("input", "1.0")
            openChannel("message", "messageV1")
            p.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV))
            p.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
        }

        private fun openChannel(name: String, protocol: String) {
            val init = DataChannel.Init().apply { ordered = true; this.protocol = protocol }
            val k = pc?.createDataChannel(name, init) ?: return
            channels[name] = k
            k.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) {}
                override fun onStateChange() { if (k.state() == DataChannel.State.OPEN) h.post { channelOpen(name) } }
                override fun onMessage(buffer: DataChannel.Buffer) {
                    val b = ByteArray(buffer.data.remaining()).also { buffer.data.get(it) }
                    h.post { message(name, b) }
                }
            })
        }

        private fun send(channel: String, data: ByteArray) {
            val k = channels[channel] ?: return
            if (closed || k.state() != DataChannel.State.OPEN) return
            runCatching { k.send(DataChannel.Buffer(ByteBuffer.wrap(data), true)) }
        }
        private fun send(channel: String, text: String) = send(channel, text.toByteArray())

        private fun channelOpen(name: String) {
            if (name == "message") send("message", JSONObject()
                .put("type", "Handshake").put("version", "messageV1")
                .put("id", UUID.randomUUID().toString()).put("cv", "0").toString())
        }

        private fun keyframe() = send("control", JSONObject().put("message", "videoKeyframeRequested").put("ifrRequested", true).toString())

        private fun message(channel: String, data: ByteArray) {
            if (closed) return
            if (channel != "message") {
                // control a chat posílají JSON, input binární hlášení – do záznamu jen první z každého kanálu
                if (channel !in seenChannels) {
                    seenChannels.add(channel)
                    val text = String(data)
                    log("kanál $channel: " + if (text.startsWith("{")) text.replace(Regex("\\s+"), " ").take(120) else "${data.size} B")
                }
                return
            }
            val m = XboxMessages.parse(String(data)) ?: return
            XboxMessages.logLine(m)?.let { log(it) }
            XboxMessages.reply(m)?.let { send("message", it) }
            when (m.type) {
                "HandshakeAck" -> {
                    log("handshake")
                    send("control", JSONObject().put("message", "authorizationRequest")
                        .put("accessKey", "4BDB3609-C1F1-4195-9B37-FEFF45DA8B8E").toString())
                    // ovladač „přidat a hned odebrat“ jako webový přehrávač – bez toho Xbox po minutě
                    // spojení ukončí (KickForNoClientConnection). Žádné ovládání se neposílá.
                    send("control", JSONObject().put("message", "gamepadChanged").put("gamepadIndex", 0).put("wasAdded", true).toString())
                    send("control", JSONObject().put("message", "gamepadChanged").put("gamepadIndex", 0).put("wasAdded", false).toString())
                    // input: popis klienta (15 bajtů), bez dotykové obrazovky (0 bodů jako XStreaming na TV)
                    send("input", XboxInput.clientMetadata(SystemClock.uptimeMillis().toDouble(), 0, inputSeq))
                    // zpětná vazba o zobrazených snímcích – bez ní Xbox nejspíš usoudí, že se nikdo
                    // nedívá (KickForNoClientConnection); každou sekundu, jakmile chodí obraz
                    h.postDelayed(object : Runnable {
                        override fun run() {
                            if (closed) return
                            if (frames > 0) {
                                val now = SystemClock.uptimeMillis()
                                inputSeq++
                                val f = XboxInput.feedbackFrame(now, lastFrame, System.currentTimeMillis())
                                send("input", XboxInput.metadataPacket(inputSeq, now, listOf(f)))
                                if (feedbackSent++ == 0L) log("zpětná vazba snímků zapnuta (každou 1 s)")
                            }
                            h.postDelayed(this, XboxInput.FEEDBACK_MS)
                        }
                    }, XboxInput.FEEDBACK_MS)
                    sendSettings()
                    keyframe()
                    lastKeyframeAsk = SystemClock.uptimeMillis()
                    // obraz stojí (ztracené pakety) → požádat o nový snímek; každých 5 s se ozvat i tak,
                    // jinak Xbox spojení po minutě ukončí
                    h.postDelayed(object : Runnable {
                        override fun run() {
                            if (closed) return
                            val now = SystemClock.uptimeMillis()
                            val stuck = lastFrame > 0 && now - lastFrame > 1200 && now - lastKeyframeAsk > 2000
                            if (stuck || now - lastKeyframeAsk > 5_000) { lastKeyframeAsk = now; keyframe() }
                            h.postDelayed(this, 500)
                        }
                    }, 2000)
                }
                "TransactionStart", "Message" -> {
                    if (XboxMessages.isDisconnect(m)) {
                        val min = if (iceConnectedAt > 0) (SystemClock.uptimeMillis() - iceConnectedAt) / 6_000 / 10.0 else 0.0
                        log("Xbox ukončil spojení po $min min")
                        ended(this, XboxFail.KICKED)
                    }
                }
            }
        }

        private fun sendSettings() {
            fun msg(target: String, content: JSONObject) = send("message", JSONObject()
                .put("type", "Message").put("content", content.toString())
                .put("id", UUID.randomUUID().toString()).put("target", target).put("cv", "").toString())
            msg("/streaming/systemUi/configuration", JSONObject().put("version", JSONArray(listOf(0, 2, 0))).put("systemUis", JSONArray()))
            msg("/streaming/properties/clientappinstallidchanged", JSONObject().put("clientAppInstallId", UUID.randomUUID().toString()))
            msg("/streaming/characteristics/orientationchanged", JSONObject().put("orientation", 0))
            msg("/streaming/characteristics/touchinputenabledchanged", JSONObject().put("touchInputEnabled", false))
            msg("/streaming/characteristics/clientdevicecapabilities", JSONObject())
            msg("/streaming/characteristics/dimensionschanged", JSONObject()
                .put("horizontal", 1280).put("vertical", 720).put("preferredWidth", 1280).put("preferredHeight", 720)
                .put("safeAreaLeft", 0).put("safeAreaTop", 0).put("safeAreaRight", 1280).put("safeAreaBottom", 720)
                .put("supportsCustomResolution", true))
        }

        /** Snímek z dekodéru WebRTC: do čtečky jen tehdy, když o něj stojí (převod něco stojí). */
        private val sink = VideoSink { f ->
            if (closed) return@VideoSink
            frames++
            lastFrame = SystemClock.uptimeMillis()
            if (frames == 1L) {
                log("první snímek ${f.rotatedWidth}x${f.rotatedHeight} (${f.buffer.javaClass.simpleName}" + (if (gpu) ")" else ", bez GPU)"))
                onConnected(this@Session, lastFrame)
            }
            if (!reader.wantsFrame()) { reader.frameSkipped(); return@VideoSink }
            val i = try { f.buffer.toI420() } catch (_: Throwable) { null } ?: return@VideoSink
            try {
                reader.feedI420(i.dataY, i.strideY, i.dataU, i.strideU, i.dataV, i.strideV, i.width, i.height)
            } finally { i.release() }
        }

        private val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
                log("ICE $s")
                when (s) {
                    // „připojeno“ až s prvním snímkem; když nepřijde, zkusit to znovu
                    PeerConnection.IceConnectionState.CONNECTED -> if (iceConnectedAt == 0L) {
                        iceConnectedAt = SystemClock.uptimeMillis()
                        h.postDelayed({
                            if (!closed && frames == 0L) { log("po spojení žádný snímek"); ended(this@Session, XboxFail.NO_VIDEO) }
                        }, XboxSession.FIRST_FRAME_MS)
                    }
                    PeerConnection.IceConnectionState.FAILED -> ended(this@Session, XboxFail.ICE)
                    PeerConnection.IceConnectionState.CLOSED -> if (!closed) ended(this@Session, XboxFail.KICKED)
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {
                if (s == PeerConnection.IceGatheringState.COMPLETE) gathered.countDown()
            }
            override fun onIceCandidate(c: IceCandidate) { synchronized(local) { local.add(c) } }
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
            override fun onAddStream(s: MediaStream?) {}
            override fun onRemoveStream(s: MediaStream?) {}
            override fun onDataChannel(k: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver?, s: Array<out MediaStream>?) {
                when (val t = r?.track()) {
                    is VideoTrack -> t.addSink(sink)
                    is AudioTrack -> t.setEnabled(false)
                }
            }
        }

        private fun createOffer(): SessionDescription {
            var out: SessionDescription? = null
            var err: String? = null
            val l = CountDownLatch(1)
            val c = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
            pc?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(d: SessionDescription) { out = d; l.countDown() }
                override fun onCreateFailure(e: String?) { err = e; l.countDown() }
                override fun onSetSuccess() {}
                override fun onSetFailure(e: String?) {}
            }, c)
            l.await(10, TimeUnit.SECONDS)
            val d = out ?: throw XboxAccount.XboxException(XboxFail.ICE, "offer: $err")
            return SessionDescription(d.type, XboxSession.preferH264(d.description))
        }

        private fun setDescription(d: SessionDescription, local: Boolean) {
            var err: String? = null
            val l = CountDownLatch(1)
            val o = object : SdpObserver {
                override fun onCreateSuccess(d: SessionDescription?) {}
                override fun onCreateFailure(e: String?) {}
                override fun onSetSuccess() { l.countDown() }
                override fun onSetFailure(e: String?) { err = e ?: "?"; l.countDown() }
            }
            if (local) pc?.setLocalDescription(o, d) else pc?.setRemoteDescription(o, d)
            l.await(10, TimeUnit.SECONDS)
            err?.let { throw XboxAccount.XboxException(XboxFail.REFUSED, (if (local) "local" else "remote") + " SDP: ${it.take(60)}") }
        }

        /** Ukončí relaci na Xboxu i WebRTC. Dá se volat víckrát a z kteréhokoli vlákna. */
        fun close() {
            if (closed) return
            closed = true
            gathered.countDown()
            val u = url
            val s = stream
            Thread({
                if (u != null) runCatching { XboxAccount.http("DELETE", u, null, headers = XboxAccount.gssvHeaders(s)) }
                // pořadí podle WebRTC: nejdřív zavřít spojení, pak kanály, spojení a továrnu
                // (jiné pořadí může nativní knihovnu shodit)
                runCatching { pc?.close() }
                runCatching { channels.values.forEach { it.unregisterObserver(); it.dispose() } }
                channels.clear()
                runCatching { pc?.dispose() }
                pc = null
                runCatching { factory?.dispose() }
                factory = null
                runCatching { adm?.release() }
                runCatching { egl?.release() }
                runCatching { st.quitSafely() }
                log("relace ukončena (snímků $frames, zpětná vazba $feedbackSent)")
            }, "tp-xbox-konec").apply { isDaemon = true }.start()
        }
    }
}
