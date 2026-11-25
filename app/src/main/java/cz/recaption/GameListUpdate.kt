package cz.recaption

import android.content.Context
import android.util.Log
import cz.recaption.core.DiagLog
import cz.recaption.core.GameListFetch
import cz.recaption.core.GameListJson
import cz.recaption.core.GameProfiles
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Seznam her (profily: jména, postavy s rodem, pojmy, nápisy, menu…) jako třetí aktualizace vedle aplikace
 * a aplikace v TV. Data her v aplikaci ani ve veřejném repu nejsou – stahují se z našeho serveru ([GameListFetch]).
 * Dokud žádný seznam stažený není, jede univerzální profil bez jmen a aplikace se o stažení pokusí sama
 * (při otevření aplikace i při spuštění překladu, nejvýš jednou za 2 min), bez ohledu na volbu „Seznam her automaticky“.
 * Volba hráče (`Prefs.gameId`) se nikdy nemění – hra, která v seznamu chybí, jede s univerzálním profilem.
 */
object GameListUpdate {
    private const val FILE = "hry-profily.json"
    /** Automatická kontrola nejvýš jednou za 6 h (od 1.00 vždy – volba „Seznam her automaticky“ zmizela). */
    private const val AUTO_EVERY_MS = 6 * 3600_000L
    /** Bez staženého seznamu se zkouší znovu nejdřív po 2 min (aplikace i služba). */
    private const val NEEDED_EVERY_MS = 2 * 60_000L
    @Volatile private var lastTry = 0L

    enum class State { IDLE, CHECKING, UPDATED, CURRENT, FAILED }

    /** Stav pro řádek v Titulky → Hra (čte se z paměti, nic se nepočítá). */
    @Volatile var state = State.IDLE
        private set

    private fun log(msg: String) { Log.i("TP_TEST", "GAMES|$msg"); DiagLog.add("GAMES|$msg"); EventFile.add("GAMES|$msg") }

    private fun file(c: Context) = File(c.filesDir, FILE)

    /** Při spuštění aplikace: uložený stažený seznam, když je novější než vestavěný a jde přečíst. */
    fun load(c: Context) {
        try {
            val f = file(c)
            if (!f.exists() || f.length() > GameListJson.MAX_SIZE * 3L) { loadBundled(c); return }
            val r = GameListJson.parse(f.readText(Charsets.UTF_8), BuildInfo.VERSION_CODE)
            val cat = r.catalog
            if (cat != null && cat.version > GameProfiles.BUILTIN.version) GameProfiles.use(cat)
            else { log("ulozeny nepouzit: ${r.problem ?: "verze ${r.version}"}"); loadBundled(c) }
        } catch (t: Throwable) { log("load ${t.javaClass.simpleName}"); loadBundled(c) }
    }

    /**
     * Titulky PS5: vlastní profily her přibalené v APK (`assets/hry-profily.json`) – bez serveru ReCaptionu. Načtou se,
     * když v zařízení není jiný (stažený) seznam. Tvar jako stažený seznam ([GameListJson]).
     */
    private fun loadBundled(c: Context) {
        try {
            val text = c.assets.open(BUNDLED).use { it.readBytes().toString(Charsets.UTF_8) }
            val r = GameListJson.parse(text, BuildInfo.VERSION_CODE)
            val cat = r.catalog
            if (cat != null) { GameProfiles.use(cat); log("pribaleny verze ${cat.version}") }
            else log("pribaleny nepouzit: ${r.problem}")
        } catch (t: Throwable) { log("pribaleny ${t.javaClass.simpleName}") }
    }

    private const val BUNDLED = "hry-profily.json"

    /**
     * Automatická kontrola (po otevření aplikace a při spuštění překladu): bez staženého seznamu vždy (nejvýš jednou
     * za 2 min), jinak nejvýš jednou za 6 h (od 1.00 vždy, přání majitele); jen se sítí.
     */
    fun maybeAuto(c: Context, done: (Boolean) -> Unit) {
        if (state == State.CHECKING) return
        val now = System.currentTimeMillis()
        if (!GameProfiles.catalog.downloaded) {
            if (now - lastTry in 0 until NEEDED_EVERY_MS) return
        } else {
            if (now - Prefs.gamesChecked(c) in 0 until AUTO_EVERY_MS) return
        }
        if (!online(c)) return
        check(c, done)
    }

    /**
     * Zjistí verzi na serveru a novější seznam stáhne, ověří, uloží a začne používat. Běží na pozadí;
     * [done] dostane true, když se seznam změnil (volá se mimo hlavní vlákno).
     */
    fun check(c: Context, done: (Boolean) -> Unit) {
        if (state == State.CHECKING) return
        state = State.CHECKING
        val app = c.applicationContext
        lastTry = System.currentTimeMillis()
        Thread({
            var changed = false
            try {
                Prefs.setGamesChecked(app, System.currentTimeMillis())
                val r = GameListFetch.run(GameProfiles.catalog.version, BuildInfo.VERSION_CODE, ::get)
                log(r.detail)
                state = when (r.outcome) {
                    GameListFetch.Outcome.FAILED -> State.FAILED
                    GameListFetch.Outcome.CURRENT -> State.CURRENT
                    GameListFetch.Outcome.UPDATED -> {
                        save(app, r.text!!)
                        GameProfiles.use(r.catalog)
                        changed = true
                        State.UPDATED
                    }
                }
            } catch (t: Throwable) { state = State.FAILED; log("chyba ${t.javaClass.simpleName}") }
            // běžící překlad si nový seznam načte hned (jména, rod, menu…), i když aplikace není otevřená
            if (changed && TranslatorService.running) try {
                app.startService(android.content.Intent(app, TranslatorService::class.java).setAction(TranslatorService.ACTION_RELOAD))
            } catch (_: Throwable) {}
            try { done(changed) } catch (_: Throwable) {}
        }, "seznam-her").start()
    }

    /** Uloží seznam přes dočasný soubor – rozepsaný soubor nikdy nezůstane jako platný. */
    private fun save(c: Context, text: String) {
        val f = file(c)
        val tmp = File(c.filesDir, "$FILE.part")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) throw java.io.IOException("rename") }
    }

    /** Text z adresy (nejvýš [max] znaků), null = nejde. Volat mimo hlavní vlákno. */
    private fun get(url: String, max: Int): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 10000
        c.useCaches = false
        c.setRequestProperty("Cache-Control", "no-cache")
        return try {
            if (c.responseCode != 200) null
            else c.inputStream.bufferedReader(Charsets.UTF_8).use { r ->
                val b = StringBuilder()
                val buf = CharArray(8192)
                while (true) {
                    val n = r.read(buf)
                    if (n < 0) break
                    b.append(buf, 0, n)
                    if (b.length > max) return null
                }
                b.toString()
            }
        } finally { c.disconnect() }
    }

    @Suppress("DEPRECATION")
    private fun online(c: Context): Boolean = try {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.activeNetworkInfo?.isConnected == true
    } catch (_: Throwable) { true }
}
