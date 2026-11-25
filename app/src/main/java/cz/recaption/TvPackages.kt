package cz.recaption

import android.content.Context
import android.util.Log
import cz.recaption.core.DiagLog
import cz.recaption.core.LgInstall
import cz.recaption.core.MiniJson
import cz.recaption.core.SamsungInstall
import cz.recaption.core.TvType
import cz.recaption.core.TvUpdate
import cz.recaption.core.UpdateCheck
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Aplikace v TV LG / Samsung novější než ta v APK (od 1.0.3, přání majitele: vydat novou verzi pro TV bez nové verze
 * pro Android). Vydání `latest` (a pro testery `zkusebni`) nese `tv-verze.txt` a balíčky `ReCaption-LG.ipk`
 * a `ReCaption-Samsung.wgt` (`build.yml`, samostatně `.github/workflows/tv.yml`). Když je tam novější verze než
 * v APK, stáhne se do `files/tv/<lg|samsung>` (ověřená – [TvUpdate.checkLg] / [TvUpdate.checkSamsung]) a nahrání
 * do TV („Aktualizovat v TV“, LgInstaller, průvodce Samsung) vezme ji. Offline nebo při jakékoli chybě platí
 * balíček z APK jako dřív. Dotaz nejvýš 1× za 6 h (Domů), na stránce nahrání 1× za 10 min; vždy mimo hlavní vlákno.
 */
object TvPackages {
    private const val PREFS = "tv_balicky"
    private const val MANIFEST = "receiver-manifest.json"
    @Volatile private var running = false

    private fun assetDir(type: TvType): String? = when (type) { TvType.LG -> "lg"; TvType.SAMSUNG -> "samsung"; TvType.ANDROID -> null }
    private fun dataName(type: TvType): String = if (type == TvType.LG) "receiver.ipk" else "receiver.wgt"
    private fun dir(c: Context, type: TvType): File? = assetDir(type)?.let { File(File(c.filesDir, "tv"), it) }

    private fun log(msg: String) { Log.i("TP_TEST", "TVB|$msg"); DiagLog.add("TVB|$msg") }

    /** Verze balíčku přibaleného v APK (assets/<lg|samsung>/receiver-manifest.json). */
    private fun assetVersion(c: Context, type: TvType): String? {
        val d = assetDir(type) ?: return null
        return try {
            MiniJson.obj(c.assets.open("$d/$MANIFEST").use { String(it.readBytes(), Charsets.UTF_8) })?.get("version") as? String
        } catch (_: Exception) { null }
    }

    private fun versionOf(manifest: String): String? = MiniJson.obj(manifest)?.get("version") as? String

    /** Stažený balíček, když je novější než v APK a sedí s popisem (velikost, otisk, id, verze); jinak null. */
    private fun downloaded(c: Context, type: TvType): Pair<String, ByteArray>? = try { readDownloaded(c, type) } catch (_: Exception) { null }

    private fun readDownloaded(c: Context, type: TvType): Pair<String, ByteArray>? {
        val d = dir(c, type) ?: return null
        val m = File(d, MANIFEST); val f = File(d, dataName(type))
        if (!m.isFile || !f.isFile || f.length() > TvUpdate.MAX_SIZE) return null
        val manifest = m.readText(Charsets.UTF_8)
        if (!TvUpdate.useDownloaded(assetVersion(c, type), versionOf(manifest))) return null
        val data = f.readBytes()
        val ok = when (type) {
            TvType.LG -> runCatching { LgInstall.bundle(manifest, data) }.isSuccess
            TvType.SAMSUNG -> SamsungInstall.bundle(manifest, data) != null
            TvType.ANDROID -> false
        }
        return if (ok) manifest to data else null
    }

    /** Popis a obsah balíčku, který se nahraje do TV: stažený (novější a v pořádku), jinak z APK. */
    fun load(c: Context, type: TvType): Pair<String, ByteArray> {
        downloaded(c, type)?.let { return it }
        val d = assetDir(type) ?: throw IOException("android")
        val manifest = c.assets.open("$d/$MANIFEST").use { String(it.readBytes(), Charsets.UTF_8) }
        val data = c.assets.open("$d/${dataName(type)}").use { it.readBytes() }
        return manifest to data
    }

    /** Verze, kterou telefon do TV nahraje (stažená novější, jinak z APK); null = nejde zjistit. */
    fun version(c: Context, type: TvType): String? = downloaded(c, type)?.let { versionOf(it.first) } ?: assetVersion(c, type)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Zjistí (mimo hlavní vlákno), jestli je ve vydání novější aplikace pro TV [type], a stáhne ji. [page] = stránka
     * nahrání (častěji). [onNew] se zavolá mimo hlavní vlákno, jen když přibyl novější balíček.
     */
    fun refresh(c: Context, type: TvType, page: Boolean, onNew: () -> Unit = {}) {
        if (!type.sends || running) return
        val app = c.applicationContext
        val now = System.currentTimeMillis()
        val p = prefs(app)
        if (!TvUpdate.shouldCheck(p.getLong("zkouseno", 0L), now, page)) return
        running = true
        p.edit().putLong("zkouseno", now).apply()
        Thread({
            try { if (check(app, type)) { TvChoice.forgetBundled(); onNew() } }
            catch (e: Throwable) { log("chyba ${e.javaClass.simpleName}") }
            finally { running = false }
        }, "tp-tv-balicek").start()
    }

    private fun check(c: Context, type: TvType): Boolean {
        val bundled = assetVersion(c, type)
        val d = dir(c, type) ?: return false
        val have = downloaded(c, type)?.let { versionOf(it.first) }
        // po aktualizaci APK je stažený balíček stejný nebo starší než přibalený → pryč (místo v telefonu)
        if (have == null && d.exists()) d.deleteRecursively()
        val stable = fetchText(UpdateCheck.url(UpdateCheck.Channel.STABLE, TvUpdate.VERSION_FILE))
        val test = if (Tester.active(c)) fetchText(UpdateCheck.url(UpdateCheck.Channel.TEST, TvUpdate.VERSION_FILE)) else null
        val channel = if (TvUpdate.newer(test, stable)) UpdateCheck.Channel.TEST else UpdateCheck.Channel.STABLE
        val remote = TvUpdate.best(stable, test)
        log("${type.key} kanál=${remote ?: "?"} apk=${bundled ?: "?"} stažená=${have ?: "-"}")
        if (remote == null || !TvUpdate.shouldDownload(remote, bundled, have)) return false
        val file = if (type == TvType.LG) TvUpdate.LG_FILE else TvUpdate.SAMSUNG_FILE
        val data = fetchBytes(UpdateCheck.url(channel, file)) ?: run { log("stažení selhalo"); return false }
        val manifest = (if (type == TvType.LG) TvUpdate.checkLg(data, remote) else TvUpdate.checkSamsung(data, remote))
            ?: run { log("balíček nesedí s verzí $remote (${data.size} B)"); return false }
        d.mkdirs()
        val m = File(d, MANIFEST); val f = File(d, dataName(type))
        val mt = File(d, "$MANIFEST.part"); val ft = File(d, dataName(type) + ".part")
        ft.writeBytes(data); mt.writeText(manifest, Charsets.UTF_8)
        // nejdřív pryč starý popis – napůl zapsaná dvojice pak neprojde kontrolou (otisk) a platí APK
        m.delete(); f.delete()
        if (!ft.renameTo(f) || !mt.renameTo(m)) { d.deleteRecursively(); log("zápis selhal"); return false }
        log("stažena ${type.key} $remote z ${channel.tag}")
        return downloaded(c, type) != null
    }

    /** Malý textový soubor (tv-verze.txt), vždy čerstvý; null = nejde. */
    private fun fetchText(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 5000
        c.useCaches = false
        c.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (c.responseCode == 200) TvUpdate.parse(c.inputStream.bufferedReader().use { it.readText().take(100) }) else null
        } finally { c.disconnect() }
    } catch (_: Exception) { null }

    /** Balíček do paměti (nejvýš [TvUpdate.MAX_SIZE]); null = nejde. */
    private fun fetchBytes(url: String): ByteArray? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 20000
        try {
            if (c.responseCode != 200 || c.contentLengthLong > TvUpdate.MAX_SIZE) null
            else c.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(32 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > TvUpdate.MAX_SIZE) throw IOException("moc velké")
                }
                out.toByteArray()
            }
        } finally { c.disconnect() }
    } catch (_: Exception) { null }
}
