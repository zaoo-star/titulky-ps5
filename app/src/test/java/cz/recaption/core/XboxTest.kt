package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XboxAuthTest {
    @Test fun deviceCodeParsed() {
        val c = XboxAuth.parseDeviceCode("""{"user_code":"ABCD1234","device_code":"DAQABAAEAAA","verification_uri":"https://www.microsoft.com/link","expires_in":900,"interval":5,"message":"…"}""", 1000)!!
        assertEquals("ABCD1234", c.userCode)
        assertEquals("DAQABAAEAAA", c.deviceCode)
        assertEquals(5, c.intervalS)
        assertEquals(1000 + 900_000L, c.expiresAt)
        assertEquals("microsoft.com/link", XboxAuth.shortLink(c.verificationUri))
    }

    @Test fun deviceCodeSafeDefaults() {
        // neobvyklé hodnoty se omezí, cizí adresa se nepoužije
        val c = XboxAuth.parseDeviceCode("""{"user_code":"X","device_code":"Y","verification_uri":"http://evil","interval":999,"expires_in":"10"}""", 0)!!
        assertEquals(XboxAuth.DEFAULT_LINK, c.verificationUri)
        assertEquals(15, c.intervalS)
        assertEquals(60_000L, c.expiresAt)
        assertNull(XboxAuth.parseDeviceCode("""{"device_code":"Y"}""", 0))
        assertNull(XboxAuth.parseDeviceCode("není json", 0))
    }

    @Test fun pollStates() {
        assertEquals(XboxAuth.Poll.PENDING, XboxAuth.pollState("""{"error":"authorization_pending"}"""))
        assertEquals(XboxAuth.Poll.PENDING, XboxAuth.pollState(""))
        assertEquals(XboxAuth.Poll.SLOW_DOWN, XboxAuth.pollState("""{"error":"slow_down"}"""))
        assertEquals(XboxAuth.Poll.EXPIRED, XboxAuth.pollState("""{"error":"expired_token"}"""))
        assertEquals(XboxAuth.Poll.DENIED, XboxAuth.pollState("""{"error":"authorization_declined"}"""))
        assertEquals(XboxAuth.Poll.FAILED, XboxAuth.pollState("""{"error":"bad_verification_code"}"""))
    }

    @Test fun tokensAndExpiry() {
        val t = XboxAuth.parseTokens("""{"access_token":"EwA","refresh_token":"M.C1_x","expires_in":3600}""", 0)!!
        assertEquals("EwA", t.access)
        assertEquals("M.C1_x", t.refresh)
        assertEquals(3_600_000L, t.expiresAt)
        assertTrue(XboxAuth.valid(t.expiresAt, 0))
        // rezerva: dvě minuty před koncem už se obnovuje
        assertTrue(XboxAuth.valid(t.expiresAt, t.expiresAt - XboxAuth.EXPIRY_MARGIN_MS - 1))
        assertFalse(XboxAuth.valid(t.expiresAt, t.expiresAt - XboxAuth.EXPIRY_MARGIN_MS))
        assertFalse(XboxAuth.valid(0, 0))
        assertNull(XboxAuth.parseTokens("""{"error":"x"}""", 0))
        assertNull(XboxAuth.parseTokens("""{"access_token":"a"}""", 0)!!.refresh)
    }

    @Test fun refreshNeverSignsOutOnNetworkTrouble() {
        assertEquals(XboxAuth.Refresh.OK, XboxAuth.refreshOutcome(200, """{"access_token":"a"}"""))
        assertEquals(XboxAuth.Refresh.SIGNED_OUT, XboxAuth.refreshOutcome(400, """{"error":"invalid_grant"}"""))
        assertEquals(XboxAuth.Refresh.RETRY, XboxAuth.refreshOutcome(400, """{"error":"temporarily_unavailable"}"""))
        assertEquals(XboxAuth.Refresh.RETRY, XboxAuth.refreshOutcome(0, ""))
        assertEquals(XboxAuth.Refresh.RETRY, XboxAuth.refreshOutcome(503, "<html>"))
        assertEquals(XboxAuth.Refresh.RETRY, XboxAuth.refreshOutcome(200, "{}"))
    }

    @Test fun xboxLiveTokens() {
        assertEquals("u1", XboxAuth.parseUserToken("""{"Token":"u1","DisplayClaims":{}}"""))
        val x = XboxAuth.parseXsts("""{"Token":"x1","DisplayClaims":{"xui":[{"uhs":"123","gtg":"Hrac 42"}]}}""")!!
        assertEquals("x1", x.token)
        assertEquals("Hrac 42", x.gamertag)
        assertNull(XboxAuth.parseXsts("""{"Token":"x1"}""")!!.gamertag)
        assertEquals(XboxFail.PROFILE, XboxAuth.xstsError(401, """{"XErr":2148916233}"""))
        assertEquals(XboxFail.PROFILE, XboxAuth.xstsError(401, """{"XErr":2148916238}"""))
        assertEquals(XboxFail.AUTH, XboxAuth.xstsError(401, ""))
        assertEquals(XboxFail.NETWORK, XboxAuth.xstsError(500, ""))
    }

    @Test fun streamTokenPicksDefaultRegion() {
        val s = XboxAuth.parseStream("""{"offeringSettings":{"regions":[
            {"name":"UkSouth","baseUri":"https://uks.core.gssv-play-prodxhome.xboxlive.com","isDefault":false},
            {"name":"WestEurope","baseUri":"https://weu.core.gssv-play-prodxhome.xboxlive.com/","isDefault":true}]},
            "gsToken":"gs1","durationInSeconds":14400}""", 0)!!
        assertEquals("gs1", s.gsToken)
        assertEquals("https://weu.core.gssv-play-prodxhome.xboxlive.com", s.baseUri)
        assertEquals(14_400_000L, s.expiresAt)
        // bez výchozího regionu první, bez regionu nic
        assertEquals("https://a", XboxAuth.parseStream("""{"gsToken":"g","offeringSettings":{"regions":[{"baseUri":"https://a"}]}}""", 0)!!.baseUri)
        assertNull(XboxAuth.parseStream("""{"gsToken":"g","offeringSettings":{"regions":[]}}""", 0))
        assertNull(XboxAuth.parseStream("""{"gsToken":"g","offeringSettings":{"regions":[{"baseUri":"http://a"}]}}""", 0))
    }

    @Test fun consoles() {
        val l = XboxAuth.parseConsoles("""{"totalItems":2,"results":[
            {"deviceName":"Obývák","serverId":"F4001234ABCD","powerState":"On","consoleType":"XboxSeriesX"},
            {"serverId":"F4005678","powerState":"ConnectedStandby"},{"deviceName":"bez id"}]}""")!!
        assertEquals(2, l.size)
        assertEquals("Obývák", l[0].name)
        assertTrue(l[0].isOn)
        assertEquals("Xbox", l[1].name)
        assertFalse(l[1].isOn)
        assertEquals(emptyList<XboxAuth.XboxConsole>(), XboxAuth.parseConsoles("{}"))
        assertNull(XboxAuth.parseConsoles("chyba"))
    }

    @Test fun consoleChoiceDefaultsToPlayStation() {
        assertEquals(Console.PLAYSTATION, Console.of(null))
        assertEquals(Console.PLAYSTATION, Console.of("cokoli"))
        assertEquals(Console.XBOX, Console.of("xbox"))
        assertEquals(Console.XBOX, Console.of(Console.XBOX.code))
    }
}

class XboxSessionTest {
    @Test fun sessionStates() {
        assertEquals(XboxSession.Step.READY, XboxSession.state("""{"state":"Provisioned"}""").step)
        assertEquals(XboxSession.Step.CONNECT, XboxSession.state("""{"state":"ReadyToConnect"}""").step)
        assertEquals(XboxSession.Step.WAIT, XboxSession.state("""{"state":"Provisioning"}""").step)
        assertEquals(XboxSession.Step.WAIT, XboxSession.state("nesmysl").step)
        val f = XboxSession.state("""{"state":"Failed","errorDetails":{"code":"x","message":"WaitingForServerToRegister"}}""")
        assertEquals(XboxSession.Step.FAILED, f.step)
        assertEquals(XboxFail.NOT_READY, XboxSession.failOf(f.error))
        assertEquals(XboxFail.REFUSED, XboxSession.failOf("něco jiného"))
        assertEquals(XboxFail.REFUSED, XboxSession.failOf(null))
    }

    @Test fun exchange() {
        assertNull(XboxSession.exchangeResponse("""{"exchangeResponse":""}"""))
        assertNull(XboxSession.exchangeResponse(""))
        val sdp = """{"exchangeResponse":"{\"sdp\":\"v=0\\r\\n\",\"status\":\"success\"}"}"""
        val ex = XboxSession.exchangeResponse(sdp)!!
        val a = XboxSession.sdpAnswer(ex)
        assertTrue(a.ok)
        assertEquals("v=0\r\n", a.sdp)
        assertFalse(XboxSession.sdpAnswer("""{"status":"failed","sdp":"v=0"}""").ok)
        assertFalse(XboxSession.sdpAnswer("""{"status":"success"}""").ok)
        assertEquals("busy", XboxSession.exchangeError("""{"errorDetails":{"message":"busy"}}"""))
        assertEquals(30, XboxSession.keepAliveSeconds("""{"keepAlivePulseInSeconds":30}"""))
        assertEquals(10, XboxSession.keepAliveSeconds("""{"keepAlivePulseInSeconds":1}"""))
        assertEquals(20, XboxSession.keepAliveSeconds("{}"))
    }

    @Test fun teredoUnpacked() {
        // RFC 4380, příklad: server 65.54.227.120, klient 192.0.2.45 port 40000
        val c = "a=candidate:1 1 UDP 100 2001:0000:4136:e378:8000:63bf:3fff:fdd2 9002 typ host "
        val t = XboxSession.teredo(c)
        // pořadí jako XStreaming: klient:port z Teredo, server:port, klient:port, pak oba s herním portem 3074
        assertEquals(listOf(
            "a=candidate:10 1 UDP 100 192.0.2.45 40000 typ host ",
            "a=candidate:11 1 UDP 100 65.54.227.120 9002 typ host ",
            "a=candidate:12 1 UDP 100 192.0.2.45 9002 typ host ",
            "a=candidate:13 1 UDP 100 65.54.227.120 3074 typ host ",
            "a=candidate:14 1 UDP 100 192.0.2.45 3074 typ host ",
        ), t)
        // kandidát už s portem 3074 – další 3074 se nepřidávají
        assertEquals(3, XboxSession.teredo("a=candidate:1 1 UDP 100 2001:0:4136:e378:8000:63bf:3fff:fdd2 3074 typ host").size)
        // zkrácený zápis
        assertEquals(5, XboxSession.teredo("a=candidate:1 1 UDP 100 2001:0:4136:e378:8000:63bf:3fff:fdd2 9002 typ host").size)
        // jiné adresy se nemění
        assertTrue(XboxSession.teredo("a=candidate:1 1 UDP 100 192.168.1.5 9002 typ host").isEmpty())
        assertTrue(XboxSession.teredo("a=candidate:1 1 UDP 100 fe80::1 9002 typ host").isEmpty())
        assertTrue(XboxSession.teredo("a=candidate:1 1 UDP").isEmpty())
    }

    @Test fun ipv6Parsing() {
        assertEquals(listOf(0x20, 0x01) + List(13) { 0 } + listOf(1), XboxSession.ipv6("2001::1")!!.toList())
        assertEquals(List(16) { 0 }, XboxSession.ipv6("::")!!.toList())
        assertNull(XboxSession.ipv6("192.168.1.1"))
        assertNull(XboxSession.ipv6("1::2::3"))
        assertNull(XboxSession.ipv6("1:2:3:4:5:6:7"))
        assertNull(XboxSession.ipv6("12345::1"))
        assertNull(XboxSession.ipv6("fe80::1%wlan0"))
    }

    @Test fun remoteCandidatesIncludeTeredo() {
        val ex = """[{"candidate":"a=candidate:1 1 UDP 100 2001:0:4136:e378:8000:63bf:3fff:fdd2 9002 typ host ","sdpMid":"0","sdpMLineIndex":"0"},
            {"candidate":"a=candidate:2 1 UDP 1 192.168.1.9 9002 typ host","sdpMid":"0","sdpMLineIndex":0},
            {"candidate":"a=candidate:3 1 UDP 1 192.168.1.9 9 typ host tcptype active","sdpMid":"0","sdpMLineIndex":0},
            {"candidate":"a=end-of-candidates","sdpMid":"0","sdpMLineIndex":"0"}]"""
        val c = XboxSession.remoteCandidates(ex)
        // původní Teredo, za ním 5 odvozených IPv4, pak běžný; „UDP + tcptype“ a konec se vynechají
        assertEquals(7, c.size)
        assertTrue(c[0].text.contains("2001:0:4136"))
        assertTrue(c.none { "tcptype" in it.text })
        assertTrue(c.all { it.mid == "0" && it.index == 0 })
        assertEquals("a=candidate:2 1 UDP 1 192.168.1.9 9002 typ host", c.last().text)
        assertTrue(XboxSession.remoteCandidates("nesmysl").isEmpty())
    }

    private val offer = listOf(
        "v=0", "o=- 1 2 IN IP4 127.0.0.1", "s=-", "t=0 0",
        "m=audio 9 UDP/TLS/RTP/SAVPF 111", "c=IN IP4 0.0.0.0", "a=rtpmap:111 opus/48000/2",
        "m=video 9 UDP/TLS/RTP/SAVPF 96 97 102 103 104 105", "c=IN IP4 0.0.0.0",
        "a=rtpmap:96 VP8/90000", "a=rtpmap:97 rtx/90000", "a=fmtp:97 apt=96",
        "a=rtpmap:102 H264/90000", "a=fmtp:102 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42001f",
        "a=rtpmap:103 H264/90000", "a=fmtp:103 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f",
        "a=rtpmap:104 H264/90000", "a=fmtp:104 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=4d001f",
        "a=rtpmap:105 VP9/90000", "",
    ).joinToString("\r\n")

    @Test fun h264FirstAndLowBitrate() {
        val out = XboxSession.preferH264(offer, 3000)
        val lines = out.split("\r\n")
        assertEquals("m=video 9 UDP/TLS/RTP/SAVPF 104 103 102 96 97 105", lines.first { it.startsWith("m=video") })
        // b=AS hned za c= v části s obrazem, zvuk beze změny
        val m = lines.indexOfFirst { it.startsWith("m=video") }
        assertEquals("b=AS:3000", lines[m + 2])
        assertEquals(1, lines.count { it.startsWith("b=AS") })
        assertTrue(out.endsWith("\r\n"))
        assertTrue(lines.contains("m=audio 9 UDP/TLS/RTP/SAVPF 111"))
        // podruhé se řádek b= přepíše, nepřidá
        assertEquals(1, XboxSession.preferH264(out, 2000).split("\r\n").count { it.startsWith("b=AS") })
        assertTrue(XboxSession.preferH264(out, 2000).contains("b=AS:2000"))
        // bez obrazu beze změny
        assertEquals("v=0\r\nm=audio 9 X 111\r\n", XboxSession.preferH264("v=0\r\nm=audio 9 X 111\r\n"))
    }
}

class XboxSessionMoreTest {
    @Test fun stateNameAndTransferUri() {
        val s = XboxSession.state("""{"state":"Provisioned","transferUri":"https://wus2.core.gssv-play-prodxhome.xboxlive.com/"}""")
        assertEquals(XboxSession.Step.READY, s.step)
        assertEquals("Provisioned", s.name)
        assertEquals("https://wus2.core.gssv-play-prodxhome.xboxlive.com", s.transferUri)
        // jen https a bez mezer – cizí nebo nesmyslná adresa se nepoužije
        assertNull(XboxSession.state("""{"state":"Provisioning","transferUri":"http://x"}""").transferUri)
        assertNull(XboxSession.state("""{"state":"Provisioning"}""").transferUri)
        assertEquals("Provisioning", XboxSession.state("""{"state":"Provisioning"}""").name)
        assertNull(XboxSession.state("nesmysl").name)
    }

    @Test fun sessionUrlOnRegionOrTransferHost() {
        assertEquals("https://a.x/v5/sessions/home/ABC", XboxSession.sessionUrl("https://a.x/", "/v5/sessions/home/ABC"))
        assertEquals("https://b.x/v5/sessions/home/ABC", XboxSession.sessionUrl("https://b.x", "v5/sessions/home/ABC/"))
        // celá adresa z /play zůstává
        assertEquals("https://c.x/v5/s/1", XboxSession.sessionUrl("https://b.x", "https://c.x/v5/s/1"))
    }

    @Test fun authRetryOnlyFor401And403() {
        assertTrue(XboxSession.authExpired(401))
        assertTrue(XboxSession.authExpired(403))
        assertFalse(XboxSession.authExpired(200))
        assertFalse(XboxSession.authExpired(0))
        assertFalse(XboxSession.authExpired(500))
    }

    @Test fun offerCodecsLogged() {
        val offer = listOf(
            "v=0", "m=audio 9 UDP/TLS/RTP/SAVPF 111", "a=rtpmap:111 opus/48000/2",
            "m=video 9 UDP/TLS/RTP/SAVPF 104 96 97 105", "a=rtpmap:96 VP8/90000", "a=rtpmap:97 rtx/90000",
            "a=rtpmap:104 H264/90000", "a=rtpmap:105 VP9/90000",
            "m=application 9 UDP/DTLS/SCTP webrtc-datachannel", "",
        ).joinToString("\r\n")
        assertEquals(listOf("H264", "VP8", "VP9"), XboxSession.videoCodecs(offer))
        assertEquals("nabídka obraz: H264,VP8,VP9 | H264=ano", XboxSession.offerLine(offer))
        val noH264 = "v=0\nm=video 9 X 96\na=rtpmap:96 VP8/90000\n"
        assertEquals("nabídka obraz: VP8 | H264=NE", XboxSession.offerLine(noH264))
        assertEquals("nabídka obraz: žádný kodek | H264=NE", XboxSession.offerLine("v=0\nm=audio 9 X 111\n"))
    }
}

class XboxInputTest {
    private fun le(b: ByteArray) = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN)

    @Test fun clientMetadataHasNoTouch() {
        val b = XboxInput.clientMetadata(1234.5)
        assertEquals(15, b.size)
        val r = le(b)
        assertEquals(8, r.getShort(0).toInt())
        assertEquals(0, r.getInt(2))
        assertEquals(1234.5, r.getDouble(6), 0.0)
        assertEquals(0, b[14].toInt())
        assertEquals(1, XboxInput.clientMetadata(0.0, 1)[14].toInt())
    }

    @Test fun metadataPacketLayout() {
        val f = XboxInput.Frame(serverDataKey = 0x1_0000_0005L, arrivalMs = 100, submittedMs = 101, decodedMs = 110, renderedMs = 111)
        val b = XboxInput.metadataPacket(sequence = 7, nowMs = 5000, frames = listOf(f))
        // 14 hlavička + 1 počet + 7 × 4 = 43 bajtů na jeden snímek (jako XStreaming)
        assertEquals(43, b.size)
        val r = le(b)
        assertEquals(XboxInput.REPORT_METADATA, r.getShort(0).toInt())
        assertEquals(7, r.getInt(2))
        assertEquals(5000.0, r.getDouble(6), 0.0)
        assertEquals(1, b[14].toInt())
        // u32: vyšší bity se zahodí (JS DataView.setUint32)
        assertEquals(listOf(5, 100, 101, 110, 111, 5000, 5000), (0 until 7).map { r.getInt(15 + it * 4) })
        // dva snímky = 71 bajtů, prázdný paket jen hlavička a počet
        assertEquals(71, XboxInput.metadataPacket(1, 0, listOf(f, f)).size)
        assertEquals(15, XboxInput.metadataPacket(1, 0, emptyList()).size)
        // nejvýš 29 snímků v jednom paketu
        assertEquals(29, XboxInput.metadataPacket(1, 0, List(40) { f })[14].toInt())
    }

    @Test fun feedbackUsesRealFrameWhenFresh() {
        val fresh = XboxInput.feedbackFrame(nowMs = 10_000, lastFrameMs = 9_500, wallMs = 77)
        assertEquals(77L, fresh.serverDataKey)
        assertEquals(9_500L, fresh.decodedMs)
        assertEquals(9_500L, fresh.renderedMs)
        assertEquals(9_490L, fresh.arrivalMs)
        assertEquals(9_490L, fresh.submittedMs)
        // obraz stojí nebo ještě nebyl → „stabilní“ snímek s časem teď (XStreaming pro Android)
        assertEquals(10_000L, XboxInput.feedbackFrame(10_000, 5_000, 1).decodedMs)
        assertEquals(10_000L, XboxInput.feedbackFrame(10_000, 0, 1).decodedMs)
    }
}

class XboxMessagesTest {
    @Test fun parseStripsNulls() {
        val m = XboxMessages.parse("{\"type\":\"TransactionStart\",\"id\":\"a1\",\"target\":\"/streaming/systemUi/messages/ShowMessageDialog\",\"cv\":\"Ab.3\",\"content\":\"{}\"}\u0000\u0000")!!
        assertEquals("TransactionStart", m.type)
        assertEquals("a1", m.id)
        assertEquals("Ab.3", m.cv)
        assertNull(XboxMessages.parse("nesmysl"))
        assertNull(XboxMessages.parse("{\"id\":\"x\"}"))
    }

    @Test fun cvIncrement() {
        assertEquals("Ab.4", XboxMessages.incrementCv("Ab.3"))
        assertEquals("Ab.x.1", XboxMessages.incrementCv("Ab.x"))
        assertEquals("1", XboxMessages.incrementCv(""))
        assertEquals("8", XboxMessages.incrementCv("7"))
    }

    @Test fun unknownRequestsGetUnhandled() {
        val m = XboxMessages.parse("""{"type":"TransactionStart","id":"t1","target":"/streaming/systemUi/messages/ShowMessageDialog","cv":"Ab.3","content":"{}"}""")!!
        val r = MiniJson.obj(XboxMessages.reply(m)!!)!!
        assertEquals("Unhandled", r["type"])
        assertEquals("t1", r["id"])
        assertEquals("/streaming/systemUi/messages/ShowMessageDialog", r["target"])
        assertEquals("Ab.4", r["cv"])
        // i obyčejná zpráva, kterou neobsloužíme (XStreaming _onFireAndForget)
        val f = XboxMessages.parse("""{"type":"Message","id":"m1","target":"/streaming/properties/titleinfo","cv":"","content":"{}"}""")!!
        assertEquals("Unhandled", MiniJson.obj(XboxMessages.reply(f)!!)!!["type"])
    }

    @Test fun touchControlsHandledAndDisconnectNotAnswered() {
        val t = XboxMessages.parse("""{"type":"Message","id":"m2","target":"/streaming/touchcontrols/showtitledefault","cv":"c.1","content":"{}"}""")!!
        assertNull(XboxMessages.reply(t))
        val tt = XboxMessages.parse("""{"type":"TransactionStart","id":"t2","target":"/streaming/touchcontrols/x","cv":"c.1","content":"{}"}""")!!
        val r = MiniJson.obj(XboxMessages.reply(tt)!!)!!
        assertEquals("ReceiverCancel", r["type"])
        assertEquals("c.3", r["cv"])
        val d = XboxMessages.parse("""{"type":"Message","id":"d","target":"/streaming/sessionLifetimeManagement/serverInitiatedDisconnect","cv":"","content":"{\"reason\":\"KickForNoClientConnection\"}"}""")!!
        assertTrue(XboxMessages.isDisconnect(d))
        assertNull(XboxMessages.reply(d))
        // ostatní typy (HandshakeAck, TransactionComplete…) a zprávy bez id bez odpovědi
        assertNull(XboxMessages.reply(XboxMessages.parse("""{"type":"HandshakeAck","id":"h"}""")!!))
        assertNull(XboxMessages.reply(XboxMessages.parse("""{"type":"Message","target":"/x"}""")!!))
    }

    @Test fun logLineShortAndWithoutTitleinfo() {
        val d = XboxMessages.parse("""{"type":"Message","id":"d","target":"/streaming/sessionLifetimeManagement/serverInitiatedDisconnect","content":"{\"reason\":\"KickForNoClientConnection\"}"}""")!!
        assertEquals("zpráva Message /streaming/sessionLifetimeManagement/serverInitiatedDisconnect {\"reason\":\"KickForNoClientConnection\"}", XboxMessages.logLine(d))
        assertNull(XboxMessages.logLine(XboxMessages.parse("""{"type":"Message","id":"x","target":"/streaming/properties/titleinfo","content":"{}"}""")!!))
        val long = XboxMessages.parse("{\"type\":\"Message\",\"id\":\"x\",\"target\":\"/a\",\"content\":\"" + "y".repeat(500) + "\"}")!!
        assertEquals("zpráva Message /a ".length + 120, XboxMessages.logLine(long)!!.length)
        assertEquals("zpráva HandshakeAck", XboxMessages.logLine(XboxMessages.parse("""{"type":"HandshakeAck","id":"h"}""")!!))
    }
}

class XboxRedactTest {
    @Test fun xboxTokensNeverInReport() {
        val jwt = "eyJhbGciOiJSUzI1NiJ9.eyJ4dWkiOlt7InVocyI6IjEyMyJ9XX0.c2lnbmF0dXJl"
        val access = "EwBwA+l3BAAUWm1xSeJRIJK6txKjBez4GzapzqMAAXjQ8vq2ZQ5e5W3P+Qz/abc="
        val refresh = "M.C534_BAY.0.U.-CgABCDEF*12!34\$xyz0987654321"
        val text = """gsToken: gsXYZ1234 · "userToken":"tr4nsf3r" · RpsTicket=d=$access · $jwt · $refresh · device_code=DAQABAAEAAAD · Xbox připraven"""
        val out = Redact.clean(text)
        for (s in listOf("gsXYZ1234", "tr4nsf3r", access, jwt, refresh, "DAQABAAEAAAD", "eyJ", "EwBwA", "M.C534")) assertFalse("$s v: $out", out.contains(s))
        assertTrue(out.contains("Xbox připraven"))
    }

    @Test fun storedRefreshTokenMaskedAsSecret() {
        val rt = "M.C1-short*tok"
        assertFalse(Redact.clean("x $rt y", listOf(rt)).contains(rt))
    }

    @Test fun consoleTextKeys() {
        assertEquals("st_failed", Console.PLAYSTATION.key("st_failed"))
        assertEquals("xb_st_failed", Console.XBOX.key("st_failed"))
        assertEquals("xb_m_ps5", Console.XBOX.key("m_ps5"))
        // text bez varianty pro Xbox zůstává společný
        assertEquals("b_start", Console.XBOX.key("b_start"))
        assertTrue(I18n.t(Lang.CS, Console.XBOX.key("chk_ps5_missing")).contains("Xbox"))
    }
}

class XboxLinkStatusTest {
    @Test fun statusNamesTheChosenConsole() {
        val s = LinkStatus.evaluate(1000, true, false, false, Ps5.Link.CONNECTING, 0, 0)
        assertTrue(LinkStatus.line(Lang.CS, s).contains("PlayStation"))
        assertTrue(LinkStatus.line(Lang.CS, s, Console.XBOX).contains("Xbox"))
        val img = LinkStatus.evaluate(20_000, true, false, false, Ps5.Link.CONNECTED, 1_000, 19_000)
        assertTrue(LinkStatus.line(Lang.EN, img, Console.XBOX).contains("Xbox"))
        assertTrue(LinkStatus.details(Lang.DE, img, Console.XBOX)[1].contains("Xbox"))
        // stavy bez jména konzole zůstávají stejné
        val off = LinkStatus.evaluate(0, false, false, false, Ps5.Link.OFF, 0, 0)
        assertEquals(LinkStatus.line(Lang.CS, off), LinkStatus.line(Lang.CS, off, Console.XBOX))
    }
}

class XboxRetryTest {
    @Test fun signInProblemsStopImmediately() {
        for (f in listOf(XboxFail.AUTH, XboxFail.PROFILE, XboxFail.NOT_FOUND)) {
            val d = XboxRetry().failed(f, 0)
            assertEquals(Ps5.Link.FAILED, d.state)
            assertNull(d.retryInMs)
        }
    }

    @Test fun sleepingXboxIsOnlyCheckedNeverCounted() {
        val r = XboxRetry()
        repeat(20) {
            val d = r.failed(XboxFail.OFFLINE, 0)
            assertEquals(Ps5.Link.WAITING, d.state)
            assertEquals(XboxRetry.OFFLINE_CHECK_MS, d.retryInMs)
        }
        assertEquals(0, r.failures)
    }

    @Test fun repeatedFailuresBackOffThenGiveUp() {
        val r = XboxRetry()
        val delays = (1 until XboxRetry.GIVE_UP_AFTER).map { r.failed(XboxFail.ICE, 0).retryInMs!! }
        assertEquals(delays.sorted(), delays)
        assertTrue(delays.first() >= 5_000)
        val last = r.failed(XboxFail.ICE, 0)
        assertEquals(Ps5.Link.FAILED, last.state)
        assertNull(last.retryInMs)
    }

    @Test fun stableSessionResetsCounterAndDropWaits() {
        val r = XboxRetry()
        repeat(3) { r.failed(XboxFail.TIMEOUT, 0) }
        r.connected(1_000)
        val d = r.failed(XboxFail.KICKED, 1_000 + XboxRetry.STABLE_MS)
        assertEquals(1, r.failures)
        assertEquals(Ps5.Link.WAITING, d.state)
        // po rozpadlém spojení se nezkouší hned
        assertTrue(d.retryInMs!! >= 15_000)
    }

    @Test fun shortSessionsStillCount() {
        val r = XboxRetry()
        repeat(XboxRetry.GIVE_UP_AFTER - 1) { r.connected(0); r.failed(XboxFail.NO_VIDEO, 1_000) }
        r.connected(0)
        assertEquals(Ps5.Link.FAILED, r.failed(XboxFail.KICKED, 1_000).state)
    }

    @Test fun everyFailureHasText() {
        for (f in XboxFail.values()) for (l in Lang.uiLangs) {
            val t = I18n.t(l, f.textKey)
            assertNotNull(t)
            assertFalse("$l ${f.textKey}", t == f.textKey)
        }
    }
}
