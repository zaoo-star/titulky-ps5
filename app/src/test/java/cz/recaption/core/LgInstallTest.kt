package cz.recaption.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class LgIpkTest {
    private val appinfo = """{"id": "cz.recaption.tv", "version": "1.2.3", "title": "ReCaption"}"""
    private val files = mapOf(
        "appinfo.json" to appinfo.toByteArray(),
        "index.html" to "<html></html>".toByteArray(),
        "app.js" to ByteArray(1500) { (it % 251).toByte() },
    )

    @Test fun layoutMatchesAresPackage() {
        val ipk = LgIpk.build(files)
        val c = LgIpk.read(ipk)
        assertEquals(listOf("debian-binary", "control.tar.gz", "data.tar.gz"), c.members)
        assertEquals(listOf("control"), c.controlEntries.map { it.name })
        val lines = c.control.lines()
        assertTrue(lines.contains("Package: cz.recaption.tv"))
        assertTrue(lines.contains("Version: 1.2.3"))
        assertTrue(lines.contains("Architecture: all"))
        assertTrue(lines.contains("webOS-Package-Format-Version: 2"))
        val pkgInfo = String(c.file("usr/palm/packages/cz.recaption.tv/packageinfo.json")!!)
        assertTrue(pkgInfo.contains("\"version\": \"1.2.3\"") && pkgInfo.contains("\"app\": \"cz.recaption.tv\""))
        // Installed-Size = součet souborů (jako ares-package)
        val size = files.values.sumOf { it.size } + pkgInfo.length
        assertTrue(lines.contains("Installed-Size: $size"))
        assertTrue(c.data.any { it.dir && it.name == "usr/palm/applications/cz.recaption.tv" })
        assertArrayEquals(files["app.js"], c.file("usr/palm/applications/cz.recaption.tv/app.js"))
    }

    @Test fun fixedTimeOwnerAndModes() {
        val c = LgIpk.read(LgIpk.build(files))
        // webOS 5 odmítá rok 1970 → 1. 1. 2000
        assertTrue((c.data + c.controlEntries).all { it.mtime == 946684800L && it.uid == 0 && it.gid == 0 })
        assertTrue(c.arTimes.all { it == 946684800L })
        assertTrue(c.data.filter { it.dir }.all { it.mode == "755".toInt(8) })
        assertTrue(c.data.filter { !it.dir }.all { it.mode == "644".toInt(8) })
    }

    @Test fun sameSourcesGiveSameBytes() {
        assertArrayEquals(LgIpk.build(files), LgIpk.build(files.toList().reversed().toMap()))
    }

    @Test fun verifyCatchesChangedOrExtraFiles() {
        val ipk = LgIpk.build(files)
        assertEquals("cz.recaption.tv" to "1.2.3", LgIpk.verify(ipk, files))
        for (other in listOf(files + ("extra.js" to "x".toByteArray()), files + ("app.js" to "jiný".toByteArray()), files - "index.html")) {
            try { LgIpk.verify(ipk, other); fail("přijato") } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun rejectsBadNamesAndVersions() {
        for (bad in listOf("../x" to "a", "a//b" to "a", "čeština.js" to "a")) {
            try { LgIpk.build(files + (bad.first to bad.second.toByteArray())); fail(bad.first) } catch (_: IllegalArgumentException) {}
        }
        try { LgIpk.build(mapOf("appinfo.json" to """{"id":"cz.recaption.tv","version":"1.0"}""".toByteArray())); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun longPathsUsePrefix() {
        val long = "a".repeat(60) + "/" + "b".repeat(60) + ".js"
        val c = LgIpk.read(LgIpk.build(files + (long to "x".toByteArray())))
        assertArrayEquals("x".toByteArray(), c.file("usr/palm/applications/cz.recaption.tv/$long"))
    }

    @Test fun manifestIsAcceptedByInstaller() {
        val ipk = LgIpk.build(files)
        val b = LgInstall.bundle(LgIpk.manifest("cz.recaption.tv", "1.2.3", ipk), ipk)
        assertEquals("1.2.3", b.version)
        assertEquals(LgIpk.sha256(ipk), b.sha256)
    }

    /** Skutečná aplikace pro televizi z webos/obchod/app: balíček jde sestavit a odpovídá zdrojům. */
    @Test fun realTvAppBuilds() {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "webos/obchod/app/appinfo.json").isFile) dir = dir.parentFile
        val app = File(dir ?: return, "webos/obchod/app")
        val src = app.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }
            .associate { it.relativeTo(app).invariantSeparatorsPath to it.readBytes() }
        val ipk = LgIpk.build(src)
        val (id, v) = LgIpk.verify(ipk, src)
        assertEquals("cz.recaption.tv", id)
        assertEquals(LgIpk.appInfo(String(src.getValue("appinfo.json"))).second, v)
    }
}

class LgInstallTest {
    private fun err(code: LgError, f: () -> Unit) {
        try { f(); fail("čekal jsem $code") } catch (e: LgException) { assertEquals(code, e.code) }
    }

    @Test fun onlyHomeNetworkNumericAddress() {
        for (v in listOf("192.168.0.10", "10.0.1.5", "172.16.2.254", " 192.168.1.20 ")) assertEquals(v.trim(), LgInstall.address(v))
        for (v in listOf("127.0.0.1", "8.8.8.8", "0.0.0.0", "224.0.0.1", "tv.local", "192.168.0.1;id", "192.168.0.999", "192.168.00.1", "192.168.1.255", "172.32.0.1", ""))
            err(LgError.ADDRESS) { LgInstall.address(v) }
    }

    @Test fun passphraseSixCharsTriesUppercase() {
        assertEquals(emptyList<String>(), LgInstall.passphrase("  "))
        assertEquals(listOf("A1B2C3"), LgInstall.passphrase("A1B2C3"))
        assertEquals(listOf("a1b2c3", "A1B2C3"), LgInstall.passphrase("a1b2c3"))
        for (v in listOf("12345", "1234567", "12 345", "ABC-12")) err(LgError.PASSPHRASE) { LgInstall.passphrase(v) }
    }

    @Test fun versionDecisionNeverDowngrades() {
        assertEquals(LgAction.INSTALL, LgInstall.decide(null, "1.0.0"))
        assertEquals(LgAction.UPDATE, LgInstall.decide("1.0.0", "1.0.1"))
        assertEquals(LgAction.UPDATE, LgInstall.decide("1.9.0", "1.10.0"))   // čísla, ne abeceda
        assertEquals(LgAction.LAUNCH, LgInstall.decide("1.0.1", "1.0.1"))
        assertEquals(LgAction.LAUNCH_NEWER, LgInstall.decide("2.0.0", "1.0.1"))
        err(LgError.UNKNOWN_VERSION) { LgInstall.decide("1.0", "1.0.1") }
        err(LgError.UNKNOWN_VERSION) { LgInstall.decide("abc", "1.0.1") }
    }

    @Test fun installedAppinfo() {
        assertEquals("1.0.0", LgInstall.installedVersion("""{"id":"cz.recaption.tv","version":"1.0.0","title":"ReCaption"}"""))
        err(LgError.VERIFY) { LgInstall.installedVersion("""{"id":"cizi.app","version":"1.0.0"}""") }
        err(LgError.UNKNOWN_VERSION) { LgInstall.installedVersion("""{"id":"cz.recaption.tv","version":"x"}""") }
        err(LgError.UNKNOWN_VERSION) { LgInstall.installedVersion("není json") }
    }

    @Test fun keyServerResponse() {
        val key = "-----BEGIN RSA PRIVATE KEY-----\nProc-Type: 4,ENCRYPTED\nFAKE\n-----END RSA PRIVATE KEY-----\n"
        fun r(status: String = "200 OK", extra: String = "", body: String = key) = "HTTP/1.1 $status\r\n${extra}Connection: close\r\n\r\n$body".toByteArray()
        assertArrayEquals(key.toByteArray(), LgInstall.keyFromHttp(r(extra = "Content-Length: ${key.length}\r\n")))
        assertArrayEquals(key.toByteArray(), LgInstall.keyFromHttp(r()))
        for (bad in listOf(r("302 Found"), r("404 Not Found"), r(body = "<html>error</html>"), r(extra = "Transfer-Encoding: chunked\r\n"), r(extra = "Content-Length: 9999\r\n"), "nic".toByteArray()))
            err(LgError.KEY_SERVER) { LgInstall.keyFromHttp(bad) }
    }

    @Test fun installEvents() {
        assertNull(LgInstall.installEvent("""{"returnValue":true,"subscribed":true}"""))
        assertNull(LgInstall.installEvent("""{"id":"cz.recaption.tv","details":{"state":"installing","progress":40}}"""))
        assertNull(LgInstall.installEvent("rozbitý řádek"))
        assertEquals(true, LgInstall.installEvent("""{"id":"cz.recaption.tv","details":{"state":"installed"}}"""))
        assertEquals(true, LgInstall.installEvent("""{"returnValue":true,"details":{"state":"SUCCESS"}}"""))
        val e = try { LgInstall.installEvent("""{"id":"cz.recaption.tv","details":{"state":"install failed","errorCode":-5,"reason":"ipk verified failed"}}"""); null } catch (x: LgException) { x }
        assertEquals(LgError.INSTALL, e!!.code)
        assertTrue(e.detail!!.contains("errorCode=-5") && e.detail!!.contains("ipk verified failed"))
        err(LgError.INSTALL) { LgInstall.installEvent("""{"returnValue":false,"errorCode":-1,"errorText":"Not enough storage"}""") }
        err(LgError.INSTALL) { LgInstall.installEvent("""{"returnValue":true,"errorCode":-5,"details":{"state":"installed"}}""") }
    }

    @Test fun installErrorShowsNoSecrets() {
        val json = mapOf<String, Any?>("returnValue" to false, "errorCode" to -5L, "errorText" to "bad\n\u0000" + "x".repeat(1000), "token" to "SECRET")
        val d = LgInstall.installError(json).detail!!
        assertFalse(d.contains("SECRET") || d.contains('\n') || d.contains('\u0000'))
        assertTrue(d.length < 300)
    }

    @Test fun sessionTokenAndHostKey() {
        assertEquals("abcDEF123456", LgInstall.sessionToken("abcDEF123456\n"))
        assertNull(LgInstall.sessionToken("short"))
        assertNull(LgInstall.sessionToken("abc def 1234"))
        assertTrue(LgInstall.hostKeyOk(null, "SHA256:x"))
        assertTrue(LgInstall.hostKeyOk("SHA256:x", "SHA256:x"))
        assertFalse(LgInstall.hostKeyOk("SHA256:x", "SHA256:y"))
    }

    @Test fun bundleMustMatchManifest() {
        val data = "!<arch>\nsyntetický balíček".toByteArray()
        val ok = LgIpk.manifest("cz.recaption.tv", "1.0.0", data)
        assertEquals("1.0.0", LgInstall.bundle(ok, data).version)
        err(LgError.PACKAGE) { LgInstall.bundle(ok, data + 1) }
        err(LgError.PACKAGE) { LgInstall.bundle(ok.replace("cz.recaption.tv", "cizi.app"), data) }
        err(LgError.PACKAGE) { LgInstall.bundle(ok.replace("\"schema\": 1", "\"schema\": 2"), data) }
        err(LgError.PACKAGE) { LgInstall.bundle("nesmysl", data) }
    }
}

class LgDevTimeTest {
    private val now = 1_800_000_000_000L

    @Test fun parsesLgAnswer() {
        val t = LgDevTime.parse("""{"result":"success","errorCode":"200","errorMsg":"999:59:59"}""", now)
        assertEquals(999 * 3600L + 59 * 60 + 59, t.seconds)
        assertEquals(41, t.days(now))
        assertFalse(t.warn(now))
    }

    @Test fun unknownAnswerIsNotZero() {
        for (bad in listOf("""{"result":"fail","errorMsg":"10:00:00"}""", """{"result":"success","errorMsg":"brzy"}""", "<html>", ""))
            try { LgDevTime.parse(bad, now); fail(bad) } catch (_: IllegalArgumentException) {}
    }

    @Test fun countsDownAndWarnsUnderThreeDays() {
        val t = LgDevTime(80 * 3600L, now)
        assertEquals(3, t.days(now))
        assertFalse(t.warn(now))
        val later = now + 9 * 3600_000L
        assertEquals(71 * 3600L, t.remaining(later))
        assertEquals(2, t.days(later))
        assertTrue(t.warn(later))
        assertEquals(0, t.remaining(now + 100 * 3600_000L))
        assertTrue(t.fresh(now + 3600_000L))
        assertFalse(t.fresh(now + 25 * 3600_000L))
    }

    @Test fun refreshAtMostHourly() {
        assertTrue(LgDevTime.shouldRefresh(0, now, false))
        assertFalse(LgDevTime.shouldRefresh(now - 59 * 60_000L, now, false))
        assertTrue(LgDevTime.shouldRefresh(now - 59 * 60_000L, now, true))
        assertTrue(LgDevTime.shouldRefresh(now - 61 * 60_000L, now, false))
        assertTrue(LgDevTime.shouldRefresh(now + 60_000L, now, false))   // posunuté hodiny
    }

    @Test fun renewAnswer() {
        LgDevTime.renewed("""{"result":"success","errorCode":"200","errorMsg":"OK"}""")
        LgDevTime.renewed("""{"result":"success","errorCode":200}""")
        LgDevTime.renewed("""{"result":"success"}""")
        for (bad in listOf("""{"result":"fail","errorCode":"200"}""", """{"result":"success","errorCode":"401"}""",
                """{"result":"FAIL","errorMsg":"Not found Session"}""", "<html>", ""))
            try { LgDevTime.renewed(bad); fail(bad) } catch (_: IllegalArgumentException) {}
    }

    @Test fun renewAtMostDaily() {
        val day = LgDevTime.RENEW_MS
        assertTrue(LgDevTime.shouldRenew(0, now, null))
        assertFalse(LgDevTime.shouldRenew(now - day + 60_000L, now, null))
        assertTrue(LgDevTime.shouldRenew(now - day, now, null))
        assertTrue(LgDevTime.shouldRenew(now + 60_000L, now, null))   // posunuté hodiny
        // čerstvě prodloužený jinak (TV, nové nahrání) = ne; jinak (zbývá míň) = ano
        assertFalse(LgDevTime.shouldRenew(0, now, LgDevTime(999 * 3600L, now - 3600_000L)))
        assertTrue(LgDevTime.shouldRenew(0, now, LgDevTime(900 * 3600L, now - 3600_000L)))
        // starý údaj (přes den) se nebere
        assertTrue(LgDevTime.shouldRenew(0, now, LgDevTime(999 * 3600L, now - 30 * 3600_000L)))
        // po nepovedeném pokusu znovu za 3 h
        val failed = LgDevTime.failedTry(now)
        assertFalse(LgDevTime.shouldRenew(failed, now + LgDevTime.RENEW_RETRY_MS - 60_000L, null))
        assertTrue(LgDevTime.shouldRenew(failed, now + LgDevTime.RENEW_RETRY_MS, null))
    }
}

class LgTextsTest {
    @Test fun everyErrorAndPhaseHasText() {
        val keys = LgError.values().map { "lg_e_" + it.name.lowercase() } + LgPhase.values().map { "lg_p_" + it.name.lowercase() }
        for (k in keys) {
            assertTrue("chybí $k", I18n.keys.contains(k))
            for (lang in Lang.uiLangs) assertTrue("$lang/$k", lang == Lang.CS || lang == Lang.SK || I18n.hasOwn(lang, k))
        }
        for (lang in Lang.uiLangs) {
            assertTrue(I18n.t(lang, "lg_r_install").contains("%s"))
            assertTrue(I18n.t(lang, "lg_dev_days").contains("%d") && I18n.t(lang, "lg_dev_hours").contains("%d"))
        }
    }
}

class MiniJsonTest {
    @Test fun parsesNested() {
        val m = MiniJson.obj("""{"a":[1,2.5,"x\"y",true,null],"b":{"c":-3},"u":"č"}""")!!
        assertEquals(listOf(1L, 2.5, "x\"y", true, null), m["a"])
        assertEquals(mapOf("c" to -3L), m["b"])
        assertEquals("č", m["u"])
        assertNull(MiniJson.obj("[1]"))
        assertNull(MiniJson.obj("{\"a\":1} navíc"))
        assertNull(MiniJson.obj("{\"a\":"))
    }
}
