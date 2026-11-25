package cz.recaption

import android.content.Context
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.UserInfo
import cz.recaption.core.LgAction
import cz.recaption.core.LgBundle
import cz.recaption.core.LgError
import cz.recaption.core.LgException
import cz.recaption.core.LgInstall
import cz.recaption.core.LgPhase
import cz.recaption.core.MiniJson
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/**
 * Nahraje ReCaption (přibalené assets/lg/receiver.ipk) do televize LG ve vývojářském režimu –
 * totéž, co na počítači dělá nástroj LG `ares-install`, jen z telefonu nebo boxu:
 *
 *  1. zašifrovaný klíč z Key Serveru televize (`http://<ip>:9991/webos_rsa`), odemčený Passphrase
 *     z aplikace Developer Mode (Passphrase se neukládá; klíč ano, zašifrovaný – viz [LgVault]),
 *  2. SSH `prisoner@<ip>:9922` (starý podpis ssh-rsa povolený jen pro toto spojení),
 *     otisk klíče televize se při prvním spojení zapamatuje a jeho změna se odmítne,
 *  3. přečte verzi ReCaption v televizi: stejná nebo novější → jen spustit (nikdy nic staršího),
 *     jinak SFTP do /media/developer/temp, kontrola bajtů, instalace službou
 *     `com.webos.appInstallService/dev/install`, kontrola verze a spuštění.
 *
 * Při spojení se přečte i klíč relace vývojářského režimu (pro zjištění zbývajících dní, [LgDevMode]).
 * Nic se neprodlužuje. Celé to má limit 2 minuty a jde zrušit. Volat mimo hlavní vlákno.
 *
 * Postup převzat podle LgInstalator.kt a LgSshOtisk.kt z projektu PlayCaption (AGPL-3.0),
 * zjednodušeno a upraveno pro ReCaption.
 */
class LgInstaller(private val context: Context) {
    class Result(val action: LgAction, val version: String, val launched: Boolean)

    private val vault = LgVault(context)
    @Volatile private var cancelled = false
    @Volatile private var session: Session? = null
    @Volatile private var socket: Socket? = null
    private var deadline = 0L
    private var phase = LgPhase.PACKAGE

    fun cancel() {
        cancelled = true
        Thread { runCatching { socket?.close() }; runCatching { session?.disconnect() } }.apply { isDaemon = true }.start()
    }

    private fun check() {
        if (cancelled) throw LgException(LgError.CANCELLED)
        if (System.nanoTime() > deadline) throw LgException(LgError.TIMEOUT)
    }

    /**
     * [passphrase] prázdná = použít uložené spárování. [repair] = uživatel potvrdil, že televize
     * má nový klíč (např. po obnovení továrního nastavení) – starý otisk se zapomene.
     */
    fun run(ipText: String, passphrase: String, repair: Boolean, progress: (LgPhase) -> Unit): Result {
        deadline = System.nanoTime() + LgInstall.TIMEOUT_MS * 1_000_000
        val timer = java.util.Timer(true)
        timer.schedule(object : java.util.TimerTask() {
            override fun run() { runCatching { socket?.close() }; runCatching { session?.disconnect() } }
        }, LgInstall.TIMEOUT_MS)
        var key: ByteArray? = null
        val jsch = JSch()
        fun step(p: LgPhase) { check(); phase = p; progress(p) }
        try {
            val ip = LgInstall.address(ipText)
            val pass = LgInstall.passphrase(passphrase)
            if (repair && pass.isEmpty()) throw LgException(LgError.PASSPHRASE)
            step(LgPhase.PACKAGE)
            val bundle = loadBundle()
            step(LgPhase.KEY)
            key = if (pass.isEmpty()) {
                if (!vault.paired(ip)) throw LgException(LgError.NOT_PAIRED)
                vault.sshKey(ip) ?: throw LgException(LgError.NOT_PAIRED)
            } else {
                val enc = fetchKey(ip)
                try { unlock(jsch, enc, pass) } finally { enc.fill(0) }
            }
            jsch.addIdentity("recaption-lg", key, null, null)
            step(LgPhase.CONNECT)
            val known = if (repair) null else vault.hostKey(ip)
            val hostKeys = HostKeys(known)
            jsch.hostKeyRepository = hostKeys
            val ssh = jsch.getSession(LgInstall.SSH_USER, ip, LgInstall.SSH_PORT).also { session = it }
            // Developer Mode na starších televizích nabízí jen podpis ssh-rsa: povolený jen tomuto spojení.
            for (k in listOf("server_host_key", "PubkeyAcceptedAlgorithms")) ssh.setConfig(k, JSch.getConfig(k) + ",ssh-rsa")
            ssh.setConfig("PreferredAuthentications", "publickey")
            ssh.setConfig("StrictHostKeyChecking", "yes")
            ssh.timeout = 10000
            try { ssh.connect(10000) } catch (e: Exception) {
                if (hostKeys.changed) throw LgException(LgError.HOST_CHANGED)
                throw e
            }
            val host = hostKeys.seen ?: throw LgException(LgError.SSH)
            // První spojení důvěřuje domácí síti; pozdější změnu klíče televize odmítáme (viz výše).
            vault.savePairing(ip, host, key)
            vault.lastIp = ip
            runCatching { readFile(ssh, LgInstall.SESSION_PATH, 1024) }.getOrNull()
                ?.let { LgInstall.sessionToken(String(it, Charsets.US_ASCII)) }?.let { vault.saveToken(ip, it) }

            step(LgPhase.CHECK)
            val installed = installedVersion(ssh)
            val action = LgInstall.decide(installed, bundle.version)
            installed?.let { vault.setTvVersion(ip, it) }
            if (action == LgAction.LAUNCH || action == LgAction.LAUNCH_NEWER) {
                step(LgPhase.LAUNCH)
                return Result(action, installed!!, launch())
            }
            install(ssh, bundle, ::step)
            vault.setTvVersion(ip, bundle.version)
            step(LgPhase.LAUNCH)
            return Result(action, bundle.version, launch())
        } catch (e: Exception) {
            if (cancelled) throw LgException(LgError.CANCELLED)
            if (e is LgException) throw e
            if (e is SocketTimeoutException || System.nanoTime() > deadline) throw LgException(LgError.TIMEOUT)
            throw LgException(when (phase) {
                LgPhase.PACKAGE -> LgError.PACKAGE
                LgPhase.KEY -> LgError.KEY_SERVER
                LgPhase.CONNECT -> LgError.SSH
                LgPhase.CHECK, LgPhase.VERIFY -> LgError.VERIFY
                LgPhase.UPLOAD -> LgError.TRANSFER
                LgPhase.INSTALL -> LgError.INSTALL
                LgPhase.LAUNCH -> LgError.VERIFY
            })
        } finally {
            timer.cancel()
            key?.fill(0)
            runCatching { jsch.removeAllIdentity() }
            runCatching { socket?.close() }
            socket = null
            session?.disconnect()
            session = null
        }
    }

    private fun loadBundle(): LgBundle {
        // přibalený v APK, nebo novější stažený z vydání (TvPackages, od 1.0.3)
        val (manifest, data) = TvPackages.load(context, cz.recaption.core.TvType.LG)
        return LgInstall.bundle(manifest, data)
    }

    /** Key Server je místní HTTP bez šifrování; čte se přímo ze soketu, aby aplikace nemusela povolit HTTP všude. */
    private fun fetchKey(ip: String): ByteArray {
        val s = Socket().also { socket = it }
        s.use {
            it.soTimeout = 6000
            it.connect(InetSocketAddress(ip, LgInstall.KEY_PORT), 6000)
            check()
            it.getOutputStream().write("GET /webos_rsa HTTP/1.0\r\nHost: $ip:${LgInstall.KEY_PORT}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
            return LgInstall.keyFromHttp(limited(it.getInputStream(), 24576, LgError.KEY_SERVER))
        }
    }

    private fun unlock(jsch: JSch, enc: ByteArray, candidates: List<String>): ByteArray {
        val pair = KeyPair.load(jsch, enc, null)
        try {
            if (!pair.isEncrypted) throw LgException(LgError.KEY_SERVER)
            if (candidates.none { p -> p.toByteArray(Charsets.UTF_8).let { b -> try { pair.decrypt(b) } finally { b.fill(0) } } })
                throw LgException(LgError.PASSPHRASE)
            val out = ByteArrayOutputStream()
            pair.writePrivateKey(out)
            return out.toByteArray()
        } finally { pair.dispose() }
    }

    private fun installedVersion(ssh: Session): String? {
        val data = try { readFile(ssh, LgInstall.APPINFO_PATH, 16384) } catch (e: SftpException) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) return null
            throw e
        }
        return LgInstall.installedVersion(String(data, Charsets.UTF_8))
    }

    private fun install(ssh: Session, b: LgBundle, step: (LgPhase) -> Unit) {
        step(LgPhase.UPLOAD)
        val path = LgInstall.TEMP_DIR + "/recaption-" + UUID.randomUUID() + ".ipk"
        var requested = false
        var confirmed = false
        val sftp = ssh.openChannel("sftp") as ChannelSftp
        try {
            sftp.connect(10000)
            runCatching { sftp.mkdir(LgInstall.TEMP_DIR) }
            b.data.inputStream().use { sftp.put(it, path) }
            check()
            val copy = sftp.get(path).use { limited(it, b.data.size + 1, LgError.TRANSFER) }
            if (!copy.contentEquals(b.data)) throw LgException(LgError.TRANSFER)
            step(LgPhase.INSTALL)
            requested = true
            // Jen pevné id a námi vytvořená cesta – nic, co by zadal uživatel.
            luna("luna://com.webos.appInstallService/dev/install",
                "{\"id\":\"${LgInstall.APP_ID}\",\"ipkUrl\":\"$path\",\"subscribe\":true}", true) { LgInstall.installEvent(it) == true }
            confirmed = true
            step(LgPhase.VERIFY)
            val v = installedVersion(ssh)
            if (v != b.version) throw LgException(LgError.VERIFY)
        } finally {
            // Jen náš dočasný soubor; když instalace běží dál (přerušené čekání), televize ho ještě čte.
            if (!requested || confirmed) runCatching { sftp.rm(path) }
            sftp.disconnect()
        }
    }

    private fun launch(): Boolean = runCatching {
        luna("luna://com.webos.applicationManager/launch", "{\"id\":\"${LgInstall.APP_ID}\"}", false) {
            when (MiniJson.obj(it)?.get("returnValue")) {
                true -> true
                false -> throw LgException(LgError.VERIFY)
                else -> false
            }
        }
    }.isSuccess

    private fun luna(uri: String, payload: String, subscribe: Boolean, done: (String) -> Boolean) {
        val ch = session!!.openChannel("exec") as ChannelExec
        ch.setCommand("/usr/bin/luna-send-pub ${if (subscribe) "-i" else "-n 1"} '$uri' '$payload'")
        // -i drží odběr, dokud je otevřený vstup; zavře se až po konečném výsledku.
        val stdin = ch.outputStream
        val input = ch.inputStream
        try {
            ch.connect(10000)
            val line = StringBuilder()
            var total = 0
            while (true) {
                check()
                if (input.available() == 0) {
                    if (ch.isClosed && input.available() == 0) {
                        if (line.isNotBlank() && done(line.toString())) return
                        throw LgException(LgError.INSTALL)
                    }
                    Thread.sleep(40)
                    continue
                }
                val c = input.read()
                if (c < 0) throw LgException(LgError.INSTALL)
                if (++total > 65536) throw LgException(LgError.INSTALL)
                if (c == '\n'.code) {
                    val s = line.toString(); line.setLength(0)
                    if (s.isNotBlank() && done(s)) return
                } else line.append(c.toChar())
            }
        } finally { runCatching { stdin.close() }; ch.disconnect() }
    }

    private fun readFile(ssh: Session, path: String, max: Int): ByteArray {
        val sftp = ssh.openChannel("sftp") as ChannelSftp
        try {
            sftp.connect(10000)
            return sftp.get(path).use { limited(it, max, LgError.VERIFY) }
        } finally { sftp.disconnect() }
    }

    private fun limited(input: InputStream, max: Int, err: LgError): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (true) {
            check()
            val n = input.read(buf)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > max) throw LgException(err)
            out.write(buf, 0, n)
        }
    }

    /** Kontrola otisku klíče televize: první spojení ho zapamatuje, jiný otisk = odmítnout. */
    private class HostKeys(private val known: String?) : HostKeyRepository {
        @Volatile var seen: String? = null
        @Volatile var changed = false
        override fun check(host: String?, key: ByteArray): Int {
            val fp = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(key))
            seen = fp
            if (!LgInstall.hostKeyOk(known, fp)) { changed = true; return HostKeyRepository.CHANGED }
            return HostKeyRepository.OK
        }
        override fun add(hostkey: HostKey?, ui: UserInfo?) {}
        override fun remove(host: String?, type: String?) {}
        override fun remove(host: String?, type: String?, key: ByteArray?) {}
        override fun getKnownHostsRepositoryID() = "Titulky PS5 LG"
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    }
}
