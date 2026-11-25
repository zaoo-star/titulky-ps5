package cz.recaption

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import cz.recaption.core.DiagLog
import cz.recaption.core.Ps5
import cz.recaption.core.UpdateCheck
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Aktualizace přímo z aplikace: stáhne novou verzi a předá ji systému k instalaci – uživatel
 * instalaci jen potvrdí na ovladači. Nová verze se instaluje přes stávající, takže spárování
 * s PlayStationem i nastavení zůstanou. Podmínkou je stejný podpis obou verzí.
 */
object Updater {
    private val DEVICE_KEYS = setOf("upd_inst", "upd_net", "upd_space", "upd_sig", "upd_cancel", "upd_fail")

    /**
     * Hlášky aktualizace mluví o televizi (okno televize, místo v televizi). V telefonu / tabletu (bez znaků TV) se vezme
     * znění `…_ph` – majitel 22. 11. 2026: telefon psal „v okně, které televize ukázala“.
     */
    fun text(c: Context, key: String): String =
        c.tx(if (key in DEVICE_KEYS && !NetInfo.isTvDevice(c)) key + "_ph" else key)

    private const val TAG = "TestPreklad"
    const val ACTION_STATUS = "cz.recaption.UPDATE_STATUS"
    private const val FILE = "TitulkyPS5-update.apk"

    /** Celé APK (všechny procesory) – web, Downloader, USB a záloha, když dělené APK ve vydání není. */
    const val UNIVERSAL = "TitulkyPS5.apk"

    /**
     * APK pro procesor tohoto zařízení (od 1.00-rc23, zhruba poloviční – rychlejší stažení i instalace):
     * 64bitový ARM → ReCaption-arm64.apk, 32bitový ARM (většina televizí) → ReCaption-arm32.apk, jinak celé.
     */
    fun apkFile(): String {
        val abis = Build.SUPPORTED_ABIS?.toList() ?: emptyList()
        return when {
            "arm64-v8a" in abis -> "TitulkyPS5-arm64.apk"
            "armeabi-v7a" in abis -> "TitulkyPS5-arm32.apk"
            else -> UNIVERSAL
        }
    }

    enum class State { IDLE, DOWNLOADING, INSTALLING, FAILED }

    @Volatile var state = State.IDLE; private set
    /** Kolik procent je staženo; -1 = neví se. */
    @Volatile var percent = -1; private set
    /** Klíč textu s důvodem, proč se aktualizace nepovedla. */
    @Volatile var problem: String? = null; private set
    @Volatile private var since = 0L
    /** Číslo právě běžící instalace; odpovědi systému k dřívějším (opuštěným) pokusům se ignorují. */
    @Volatile private var session = -1
    /** Kdy systém ukázal dotaz na instalaci (0 = neukázal). */
    @Volatile private var confirmAt = 0L

    /**
     * Návrat do aplikace z dotazu systému na instalaci (od 0.97.112, majitel: „omylem kliknu na odmítnout a pak to nejde dát
     * znovu“): když po návratu do 8 s (kontrola Play Protect trvá i pár sekund) nepřišla žádná odpověď (Samsung / Play Protect po „Neinstalovat“ nic nepošle), instalace
     * se bere jako zrušená – tlačítko Aktualizovat je hned zpátky (dřív až po minutě a s hláškou, že se instaluje).
     */
    fun resumed(onChange: () -> Unit) {
        if (state != State.INSTALLING || confirmAt == 0L || android.os.SystemClock.uptimeMillis() - confirmAt < 1500) return
        val s = session
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (state == State.INSTALLING && session == s && confirmAt != 0L) {
                log("dotaz zavřen bez instalace")
                confirmAt = 0L
                fail("upd_cancel", "zavřeno bez odpovědi")
                onChange()
            }
        }, 8000)
    }

    /**
     * Právě se stahuje nebo se čeká na potvrzení instalace. Čekání na potvrzení se po minutě
     * přestane počítat – uživatel mohl od dotazu systému odejít a musí jít aktualizaci spustit znovu.
     */
    val busy: Boolean get() = state == State.DOWNLOADING ||
        (state == State.INSTALLING && android.os.SystemClock.uptimeMillis() - since < 60_000)

    private fun log(msg: String) { Log.i("TP_TEST", "UPD|$msg"); DiagLog.add("UPD|$msg"); EventFile.add("UPD|$msg") }

    private fun fail(key: String, detail: String?) {
        state = State.FAILED; problem = key
        log("fail $key" + (detail?.let { " $it" } ?: ""))
    }

    /** Číslo nejnovější verze v daném kanálu; null, když se nedá zjistit. Volat mimo hlavní vlákno. */
    fun remoteVersion(channel: UpdateCheck.Channel): UpdateCheck.Remote? = try {
        val c = java.net.URL(UpdateCheck.url(channel, "version.txt")).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 5000
        // vždy čerstvé číslo verze, ne uložené z minula (nová verze se jinak ukázala až po pár spuštěních)
        c.useCaches = false
        c.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (c.responseCode == 200) UpdateCheck.parse(c.inputStream.bufferedReader().use { it.readText().take(100) }) else null
        } finally { c.disconnect() }
    } catch (_: Exception) { null }

    /** Novější verze, která je ke stažení (se zkušebním kanálem i v něm); null = žádná. Volat mimo hlavní vlákno. */
    fun offer(test: Boolean): UpdateCheck.Offer? = UpdateCheck.pick(remoteVersion(UpdateCheck.Channel.STABLE),
        if (test) remoteVersion(UpdateCheck.Channel.TEST) else null, BuildInfo.VERSION_CODE)

    /** Smaže zbytky po stahování (volá se při spuštění aplikace). */
    fun cleanUp(ctx: Context) {
        try { File(ctx.cacheDir, FILE).delete(); File(ctx.cacheDir, "$FILE.part").delete() } catch (_: Exception) {}
    }

    /**
     * Stáhne a nainstaluje novou verzi z adresy [source]. [onChange] se volá při každé změně
     * stavu, mimo hlavní vlákno.
     * Jen pro automatickou zkoušku: [source] může být i cesta k souboru v zařízení (nic se
     * nestahuje) a [force] přeskočí kontrolu, že je verze novější a má stejný podpis.
     */
    fun start(ctx: Context, source: String, force: Boolean, onChange: () -> Unit) {
        if (busy) return
        val app = ctx.applicationContext
        val local = source.startsWith("/")
        state = State.DOWNLOADING; percent = -1; problem = null; session = -1
        log("start " + (if (local) "file" else source.substringAfter("/download/")) +
            " allowed=" + app.packageManager.canRequestPackageInstalls())
        Thread({
            val file = if (local) File(source) else File(app.cacheDir, FILE)
            try {
                if (!local) {
                    val progress = { p: Int -> if (p != percent) { percent = p; onChange() } }
                    // dělené APK podle procesoru ve vydání chybí (starší vydání) → celé APK
                    try { download(source, file, progress) }
                    catch (e: IOException) {
                        if (e.message != "http 404" || !source.contains("/TitulkyPS5-arm")) throw e
                        log("abi apk 404 → celé")
                        download(source.substringBeforeLast('/') + "/" + UNIVERSAL, file, progress)
                    }
                    log("downloaded bytes=${file.length()}")
                }
                val why = check(app, file, force)
                if (why != null) fail(why, null)
                else { since = android.os.SystemClock.uptimeMillis(); state = State.INSTALLING; onChange(); install(app, file) }
            } catch (e: Exception) {
                Log.w(TAG, "update", e)
                val space = e is IOException && app.cacheDir.usableSpace < 80L * 1024 * 1024
                fail(if (space) "upd_space" else if (state == State.INSTALLING) "upd_fail" else "upd_net", e.javaClass.simpleName + " " + (e.message ?: ""))
            }
            // soubor už je předaný systému (nebo k ničemu) – v televizi nesmí zabírat místo
            if (!local) try { file.delete() } catch (_: Exception) {}
            onChange()
        }, "tp-update").start()
    }

    private fun download(url: String, to: File, progress: (Int) -> Unit) {
        val part = File(to.path + ".part")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        try {
            if (c.responseCode != 200) throw IOException("http ${c.responseCode}")
            val total = c.contentLengthLong
            var done = 0L
            c.inputStream.use { input ->
                FileOutputStream(part).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n); done += n
                        // po pěti procentech, ať se obrazovka nepřekresluje zbytečně často
                        if (total > 0) progress((done * 100 / total).toInt() / 2 * 2)
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("neúplné $done/$total")
        } finally { c.disconnect() }
        to.delete()
        if (!part.renameTo(to)) throw IOException("rename")
    }

    /** Je stažený soubor novější verze této aplikace se stejným podpisem? Vrací klíč textu problému, nebo null. */
    private fun check(ctx: Context, file: File, force: Boolean): String? {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_SIGNATURES or (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0)
        @Suppress("DEPRECATION")
        val apk = pm.getPackageArchiveInfo(file.path, flags)
        // něco jiného než naše APK (třeba chybová stránka místo souboru)
        if (apk == null || apk.packageName != ctx.packageName) { log("apk invalid"); return "upd_net" }
        val code = versionCode(apk)
        log("apk code=$code")
        if (force) return null
        if (code <= BuildInfo.VERSION_CODE) return "upd_fail"
        @Suppress("DEPRECATION")
        val mine = try { cert(pm.getPackageInfo(ctx.packageName, flags)) } catch (_: Exception) { null }
        val theirs = cert(apk)
        // S jiným podpisem systém novou verzi přes starou nepustí – lepší to říct hned a srozumitelně.
        if (mine != null && theirs != null && mine != theirs) { log("signature differs"); return "upd_sig" }
        return null
    }

    @Suppress("DEPRECATION")
    private fun versionCode(p: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()

    /** Otisk podpisu balíčku; null, když ho systém u souboru nevrátí (některé verze Androidu). */
    @Suppress("DEPRECATION")
    private fun cert(p: PackageInfo): String? {
        val sig = (if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners?.firstOrNull() else null)
            ?: p.signatures?.firstOrNull() ?: return null
        return Ps5.hex(MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()))
    }

    private fun install(ctx: Context, file: File) {
        val installer = ctx.packageManager.packageInstaller
        // nedokončené pokusy z dřívějška zabírají místo
        try { installer.mySessions.forEach { installer.abandonSession(it.sessionId) } } catch (_: Exception) {}
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        params.setSize(file.length())
        // Android 12+: když aplikaci naposledy instalovala ona sama, systém další aktualizaci pustí bez dotazu.
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = installer.createSession(params)
        session = id
        try {
            installer.openSession(id).use { s ->
                s.openWrite("TitulkyPS5.apk", 0, file.length()).use { out ->
                    FileInputStream(file).use { it.copyTo(out, 256 * 1024) }
                    s.fsync(out)
                }
                // odpověď systému (dotaz na potvrzení, výsledek) přijde do hlavní obrazovky
                val back = Intent(ctx, MainActivity::class.java).setAction(ACTION_STATUS)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                s.commit(PendingIntent.getActivity(ctx, 77, back, flags).intentSender)
            }
        } catch (e: Exception) {
            try { installer.abandonSession(id) } catch (_: Exception) {}
            throw e
        }
        log("committed session=$id")
    }

    /**
     * Odpověď systému na instalaci. Vrací true, když [i] byla zpráva o aktualizaci.
     * Volá se z hlavní obrazovky (onCreate / onNewIntent).
     */
    fun onStatus(act: Activity, i: Intent?): Boolean {
        if (i?.action != ACTION_STATUS) return false
        val status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        // Zpráva o dřívějším pokusu (např. že byl opuštěn) nesmí přepsat stav toho, který právě běží.
        if (i.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != session) { log("status=$status old session"); return true }
        log("status=$status" + (msg?.let { " $it" } ?: ""))
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // systém se chce zeptat uživatele (potvrzení instalace, případně povolení neznámých zdrojů)
                @Suppress("DEPRECATION")
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else i.getParcelableExtra(Intent.EXTRA_INTENT)
                try {
                    act.startActivity(confirm ?: throw IllegalStateException("no intent"))
                    confirmAt = android.os.SystemClock.uptimeMillis()
                    log("confirm shown")
                } catch (e: Exception) { fail("upd_fail", e.javaClass.simpleName) }
            }
            // po úspěšné instalaci systém aplikaci ukončí; sem se dostane už nová verze
            PackageInstaller.STATUS_SUCCESS -> { state = State.IDLE; problem = null }
            else -> fail(UpdateCheck.problemKey(status), msg)
        }
        return true
    }
}

/**
 * Po aktualizaci aplikaci znovu otevře, aby uživatel viděl, že běží nová verze (a mohl hned
 * spustit překlad). Když to systém nedovolí, nestane se nic – aplikaci otevře uživatel sám.
 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Log.i("TP_TEST", "UPD|replaced")
        val app = c.applicationContext
        val open = { try { app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }
        open()
        // systém otevření dovolí, až když má aplikace znovu připojenou službu Usnadnění – zkusit ještě jednou
        Handler(Looper.getMainLooper()).postDelayed({ open() }, 2500)
    }
}
