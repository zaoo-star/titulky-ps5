package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

class AtvInstallTest {
    private fun bad(v: String) = try { AtvInstall.address(v); fail("prošlo: $v") } catch (e: AtvException) { assertEquals(AtvError.ADDRESS, e.code) }

    @Test fun adresa() {
        assertEquals(AtvInstall.Target("192.168.1.40", 5555), AtvInstall.address(" 192.168.1.40 "))
        assertEquals(AtvInstall.Target("10.0.0.7", 5555), AtvInstall.address("10.0.0.7:5555"))
        assertEquals(AtvInstall.Target("192.168.1.40", 37123), AtvInstall.address("192.168.1.40:37123"))
        // česká číselná klávesnice: čárka místo tečky
        assertEquals("192.168.1.40", AtvInstall.address("192,168,1,40").ip)
        bad("8.8.8.8"); bad("192.168.1.0"); bad("192.168.1.255"); bad("192.168.1"); bad("tv.local")
        bad("192.168.1.40:0"); bad("192.168.1.40:99999"); bad("192.168.1.40:"); bad("192.168.01.40")
        assertNull(AtvInstall.addressOrNull(""))
    }

    @Test fun kroky() {
        fun p(tv: Boolean = false, dbg: Boolean = false, ok: Boolean = false) = AtvInstall.Probe(tv, dbg, ok)
        assertEquals(AtvInstall.Step.FIND_TV, AtvInstall.step(p()))
        assertEquals(AtvInstall.Step.DEBUG, AtvInstall.step(p(tv = true)))
        assertEquals(AtvInstall.Step.CONFIRM, AtvInstall.step(p(tv = true, dbg = true)))
        // otevřený port ladění = TV určitě běží
        assertEquals(AtvInstall.Step.CONFIRM, AtvInstall.step(p(dbg = true)))
        assertEquals(AtvInstall.Step.READY, AtvInstall.step(p(tv = true, dbg = true, ok = true)))
        assertTrue(AtvInstall.done(AtvInstall.Step.DEBUG, p(dbg = true)))
        assertFalse(AtvInstall.done(AtvInstall.Step.CONFIRM, p(dbg = true)))
    }

    private val dumpsys = """
        Activity Resolver Table:
          Non-Data Actions:
              android.intent.action.MAIN:
                1a2b3c cz.titulkyps5/.MainActivity filter 4d5e6f
        Packages:
          Package [cz.titulkyps5] (8f1e2d3):
            userId=10142
            pkg=Package{9a8b7c cz.titulkyps5}
            codePath=/data/app/~~abc==/cz.titulkyps5-xyz==
            versionCode=158 minSdk=26 targetSdk=34
            versionName=0.97.61
            signatures=PackageSignatures{...}
        Queries:
          system apps queryable: false
            versionCode=999
    """.trimIndent()

    @Test fun verzeVTelevizi() {
        val i = AtvInstall.installed(dumpsys)!!
        assertEquals(158L, i.code)
        assertEquals("0.97.61", i.name)
        // ReCaption v TV není
        assertNull(AtvInstall.installed("Dexopt state:\n  Unable to find package: cz.titulkyps5\n"))
        assertNull(AtvInstall.installed(""))
        // jiný balíček se stejným začátkem se nepočítá
        assertNull(AtvInstall.installed("Packages:\n  Package [cz.titulkyps5.test] (1):\n    versionCode=3\n"))
        // oddíl bez čísla verze = nic nepřepisovat
        try { AtvInstall.installed("Packages:\n  Package [cz.titulkyps5] (1):\n    userId=1\n"); fail() } catch (e: AtvException) { assertEquals(AtvError.UNKNOWN_VERSION, e.code) }
    }

    @Test fun nikdyNesnizovat() {
        assertEquals(AtvAction.INSTALL, AtvInstall.decide(null, 158))
        assertEquals(AtvAction.UPDATE, AtvInstall.decide(150, 158))
        assertEquals(AtvAction.LAUNCH, AtvInstall.decide(158, 158))
        assertEquals(AtvAction.LAUNCH_NEWER, AtvInstall.decide(160, 158))
    }

    @Test fun chybyInstalace() {
        assertEquals(AtvError.SIGNATURE, AtvInstall.installError("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package cz.titulkyps5 signatures do not match]").code)
        assertEquals("INSTALL_FAILED_INSUFFICIENT_STORAGE", AtvInstall.installError("Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]").detail)
        assertEquals(AtvError.STORAGE, AtvInstall.installError("Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]").code)
        assertEquals(AtvError.OLD_ANDROID, AtvInstall.installError("Failure [INSTALL_FAILED_OLDER_SDK: Requires newer sdk version #26]").code)
        assertEquals(AtvError.ABI, AtvInstall.installError("Failure [INSTALL_FAILED_NO_MATCHING_ABIS: ...]").code)
        assertEquals(AtvError.VERIFY, AtvInstall.installError("Failure [INSTALL_FAILED_VERSION_DOWNGRADE]").code)
        val other = AtvInstall.installError("Failure [INSTALL_FAILED_VERIFICATION_FAILURE]")
        assertEquals(AtvError.INSTALL, other.code)
        assertEquals("INSTALL_FAILED_VERIFICATION_FAILURE", other.detail)
        // bez kódu Androidu nic z odpovědi do hlášky nejde
        assertNull(AtvInstall.installError("Failure something /data/local/tmp").detail)
    }

    @Test fun spusteni() {
        assertTrue(AtvInstall.launched("Starting: Intent { cmp=cz.titulkyps5/.MainActivity }"))
        assertTrue(AtvInstall.launched("Starting: Intent { cmp=cz.titulkyps5/.MainActivity }\nWarning: Activity not started, its current task has been brought to the front"))
        assertFalse(AtvInstall.launched("Error: Activity class {cz.titulkyps5/cz.titulkyps5.MainActivity} does not exist."))
    }

    @Test fun model() {
        assertEquals("Sony BRAVIA 4K VH2", AtvInstall.model("Sony\n", "BRAVIA 4K VH2\n"))
        assertEquals("Google Chromecast", AtvInstall.model("Google", "Chromecast"))
        assertEquals("NVIDIA SHIELD Android TV", AtvInstall.model("NVIDIA", "SHIELD Android TV"))
        assertEquals("AFTKA", AtvInstall.model("", "AFTKA"))
        assertEquals("Amazon AFTKA", AtvInstall.model("Amazon", "AFTKA"))
        // model už výrobce obsahuje – neopakovat
        assertEquals("xiaomi MiBOX4", AtvInstall.model("Xiaomi", "xiaomi MiBOX4"))
        assertNull(AtvInstall.model(" ", "\u0000"))
    }

    /** Veřejný klíč ADB musí být bajt po bajtu stejný jako ten, který vyrábí knihovna dadb (a tedy adb). */
    @Test fun verejnyKlicJakoAdb() {
        val dir = java.nio.file.Files.createTempDirectory("atvklic").toFile()
        try {
            val priv = java.io.File(dir, "adbkey"); val pub = java.io.File(dir, "adbkey.pub")
            dadb.AdbKeyPair.generate(priv, pub)
            val pem = priv.readText().lines().filter { !it.startsWith("-----") }.joinToString("")
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(pem))) as RSAPrivateCrtKey
            val ours = AtvInstall.adbPublicKey(key.modulus, key.publicExponent, "unknown@unknown")
            assertEquals(pub.readText(), ours)
        } finally { dir.deleteRecursively() }
        try { AtvInstall.adbPublicKey(BigInteger.valueOf(65537), BigInteger.valueOf(3)); fail() } catch (_: IllegalArgumentException) {}
    }
}
