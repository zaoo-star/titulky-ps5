package cz.recaption

import android.content.Context
import android.os.Build
import cz.recaption.core.AtvAction
import cz.recaption.core.AtvError
import cz.recaption.core.AtvException
import cz.recaption.core.AtvInstall
import cz.recaption.core.AtvPhase
import dadb.AdbAuthException
import dadb.AdbKeyPair
import dadb.Dadb
import dadb.InstallResult
import okio.Buffer
import okio.ForwardingSource
import okio.source
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException

/**
 * Nahraje ReCaption z tohoto telefonu do Android TV / Google TV / boxu přes ladění po síti (ADB, port 5555) –
 * totéž, co na počítači `adb install`, bez aplikace v televizi. Instaluje se přesně APK nainstalované
 * v telefonu (`applicationInfo.sourceDir`), tedy se stálým podpisem: v televizi pak funguje aktualizace z aplikace.
 *
 *  1. spojení s televizí klíčem ADB tohoto telefonu ([AtvVault]); poprvé se televize zeptá „Povolit ladění?“,
 *  2. verze ReCaptionu v televizi (`dumpsys package`): stejná nebo novější → jen spustit (nikdy nic staršího),
 *  3. jinak `cmd package install -r` (přes knihovnu dadb), kontrola verze a spuštění.
 *
 * Nic neodinstalovává. Celé to má limit 8 minut a jde zrušit. Volat mimo hlavní vlákno.
 * Rozhodování a čtení odpovědí: core/AtvInstall.kt (testy AtvInstallTest).
 */
class AtvInstaller(private val context: Context) {
    class Result(val action: AtvAction, val version: String, val launched: Boolean, val model: String?)

    private val vault = AtvVault(context)
    @Volatile private var cancelled = false
    @Volatile private var adb: Dadb? = null
    @Volatile private var timedOut = false
    private var phase = AtvPhase.PACKAGE

    fun cancel() {
        cancelled = true
        Thread { runCatching { adb?.close() } }.apply { isDaemon = true }.start()
    }

    private fun check() {
        if (cancelled) throw AtvException(AtvError.CANCELLED)
        if (timedOut) throw AtvException(AtvError.TIMEOUT)
    }

    /** [progress]: krok a u přenosu podíl 0..1 (null = nevíme). */
    /**
     * Klíč, který se TV pošle při spuštění ReCaptionu ([MainActivity.EXTRA_PHONE_KEY]); telefon se pak připojí k její
     * stránce nastavení sám ([AtvInstallActivity]). Null = spustit bez klíče jako dřív.
     */
    var phoneKey: String? = null

    fun run(address: String, progress: (AtvPhase, Float?) -> Unit): Result {
        val timer = java.util.Timer(true)
        timer.schedule(object : java.util.TimerTask() {
            override fun run() { timedOut = true; runCatching { adb?.close() } }
        }, AtvInstall.TIMEOUT_MS)
        fun step(p: AtvPhase, f: Float? = null) { check(); phase = p; progress(p, f) }
        var target: AtvInstall.Target? = null
        try {
            val t = AtvInstall.address(address).also { target = it }
            step(AtvPhase.PACKAGE)
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION") val ownCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            val ownName = info.versionName ?: ownCode.toString()
            val apks = (listOf(context.applicationInfo.sourceDir) + (context.applicationInfo.splitSourceDirs ?: emptyArray()))
                .map { File(it) }
            if (apks.any { !it.isFile || it.length() <= 0 }) throw AtvException(AtvError.PACKAGE)

            step(AtvPhase.CONNECT)
            if (!LgFinder.open(t.ip, t.port, 4000)) throw AtvException(AtvError.CONNECT)
            val (priv, pub) = vault.adbKey()
            // dadb čte adbkey.pub včetně koncové nuly
            val keys = AdbKeyPair(priv, pub.toByteArray(Charsets.US_ASCII) + 0.toByte())
            val d = Dadb.create(t.ip, t.port, keys, 8000, AtvInstall.ANSWER_MS).also { adb = it }
            // první dotaz naváže spojení; neznámý klíč = televize se zeptá „Povolit ladění?“ a čeká na hráče
            if (!vault.allowed(t.ip)) step(AtvPhase.CONFIRM)
            val hello = d.shell("echo recaption")
            if (!hello.output.contains("recaption")) throw AtvException(AtvError.CONNECT)
            vault.setAllowed(t.ip, true)
            vault.lastIp = t.ip
            val model = runCatching {
                AtvInstall.model(d.shell("getprop ro.product.manufacturer").output, d.shell("getprop ro.product.model").output)
            }.getOrNull()
            model?.let { vault.setModel(t.ip, it) }

            step(AtvPhase.CHECK)
            val before = AtvInstall.installed(d.shell("dumpsys package ${AtvInstall.PACKAGE}").output)
            val action = AtvInstall.decide(before?.code, ownCode)
            before?.let { vault.setTvVersion(t.ip, it.name ?: it.code.toString(), it.code) }
            if (action == AtvAction.LAUNCH || action == AtvAction.LAUNCH_NEWER) {
                step(AtvPhase.LAUNCH)
                val have = before!!
                return Result(action, have.name ?: have.code.toString(), launch(d), model)
            }

            step(AtvPhase.INSTALL, 0f)
            val result = if (apks.size == 1) {
                val total = apks[0].length()
                var sent = 0L
                var shown = -1
                val src = object : ForwardingSource(apks[0].source()) {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        check()
                        val n = super.read(sink, byteCount)
                        if (n > 0) {
                            sent += n
                            // pruh po 2 % – víc překreslování nemá smysl
                            val pct = (sent * 50 / total).toInt()
                            if (pct != shown) { shown = pct; progress(AtvPhase.INSTALL, (sent.toFloat() / total).coerceAtMost(1f)) }
                        }
                        return n
                    }
                }
                src.use { d.install(it, total, "-r") }
            } else d.installMultiple(apks, "-r")
            check()
            if (result is InstallResult.Failure) throw AtvInstall.installError(result.reason)

            step(AtvPhase.VERIFY)
            val after = AtvInstall.installed(d.shell("dumpsys package ${AtvInstall.PACKAGE}").output)
            if (after?.code != ownCode) throw AtvException(AtvError.VERIFY)
            vault.setTvVersion(t.ip, after.name ?: ownName, after.code)
            step(AtvPhase.LAUNCH)
            return Result(action, ownName, launch(d), model)
        } catch (e: Exception) {
            if (cancelled) throw AtvException(AtvError.CANCELLED)
            if (timedOut) throw AtvException(AtvError.TIMEOUT)
            if (e is AtvException) throw e
            throw AtvException(classify(e, target))
        } finally {
            timer.cancel()
            runCatching { adb?.close() }
            adb = null
        }
    }

    /** Výjimka ze spojení → co říct hráči (podle kroku, ve kterém nastala). */
    private fun classify(e: Exception, t: AtvInstall.Target?): AtvError {
        val chain = generateSequence<Throwable>(e) { it.cause }.take(8).toList()
        val timeout = chain.any { it is SocketTimeoutException }
        val early = phase == AtvPhase.CONNECT || phase == AtvPhase.CONFIRM
        if (chain.any { it is AdbAuthException }) return AtvError.REFUSED
        if (chain.any { it is ConnectException || it is NoRouteToHostException }) return AtvError.CONNECT
        if (early) return when {
            // čekali jsme na „Povolit ladění?“ v televizi a nikdo nepotvrdil
            timeout && phase == AtvPhase.CONFIRM -> AtvError.NO_ANSWER
            timeout -> AtvError.CONNECT
            // port odpověděl, ale ne jako ladění přes síť: nejspíš Bezdrátové ladění (Android 11+, TLS s párováním)
            t != null && t.port != AtvInstall.ADB_PORT -> AtvError.WIRELESS
            // televize spojení při prvním dotazu zavřela = hráč zvolil Zrušit
            phase == AtvPhase.CONFIRM -> AtvError.REFUSED
            else -> AtvError.CONNECT
        }
        if (timeout) return AtvError.TIMEOUT
        return when (phase) {
            AtvPhase.PACKAGE -> AtvError.PACKAGE
            AtvPhase.INSTALL -> AtvError.INSTALL
            else -> if (e is IOException) AtvError.CONNECT else AtvError.VERIFY
        }
    }

    private fun launch(d: Dadb): Boolean = runCatching {
        AtvInstall.launched(d.shell("am start -n ${AtvInstall.ACTIVITY}" + (phoneKey?.let { " --es ${MainActivity.EXTRA_PHONE_KEY} $it" } ?: "")).allOutput)
    }.getOrDefault(false)
}
