package cz.recaption.pc

import cz.recaption.core.GameListFetch
import cz.recaption.core.GameListJson
import cz.recaption.core.GameProfiles
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Seznam her (jména, postavy, menu…) pro PC a Mac – stejný jako na Androidu (`GameListUpdate`): data her v aplikaci
 * nejsou, stahují se ze serveru ([GameListFetch]) a ukládají do `hry-profily.json` ve složce nástrojů.
 * Bez uloženého seznamu se při spuštění čeká na stažení (nejvýš pár sekund), s uloženým se kontroluje na pozadí.
 */
object SeznamHer {
    private val soubor get() = File(Nastroje.slozka, "hry-profily.json")
    /** PC se překládá ze stejného `core` jako aplikace – pole `minAppVerze` pro něj neplatí. */
    private const val VERZE_APLIKACE = Int.MAX_VALUE

    /** Při spuštění: uložený seznam a kontrola nového (bez uloženého hned, jinak na pozadí). */
    fun priprav(log: (String) -> Unit = {}) {
        nacti()
        if (GameProfiles.catalog.downloaded) Thread({ aktualizuj(log) }, "seznam-her").apply { isDaemon = true; start() }
        else aktualizuj(log)
    }

    private fun nacti() {
        // Titulky PS5: vlastní profily her přibalené v balíčku (`app\\hry-profily.json`, stejné jako v APK) – server nemáme;
        // stažený seznam (kdyby byl) s vyšší verzí má přednost
        for (f in listOfNotNull(System.getProperty("recaption.pc")?.let { File(it, "hry-profily.json") }, soubor)) try {
            if (f.isFile && f.length() < GameListJson.MAX_SIZE * 3L) {
                GameListJson.parse(f.readText(Charsets.UTF_8), VERZE_APLIKACE).catalog
                    ?.takeIf { it.version > GameProfiles.catalog.version }?.let { GameProfiles.use(it) }
            }
        } catch (_: Throwable) {}
    }

    private fun aktualizuj(log: (String) -> Unit) {
        val r = GameListFetch.run(GameProfiles.catalog.version, VERZE_APLIKACE, ::stahni)
        if (r.outcome == GameListFetch.Outcome.UPDATED) try {
            soubor.parentFile.mkdirs()
            val tmp = File(soubor.path + ".part")
            tmp.writeText(r.text!!, Charsets.UTF_8)
            if (!tmp.renameTo(soubor)) { soubor.delete(); tmp.renameTo(soubor) }
        } catch (_: Throwable) {}
        r.catalog?.let { GameProfiles.use(it) }
        log("Seznam her: ${r.detail}")
    }

    private fun stahni(url: String, max: Int): String? {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 4000; c.readTimeout = 8000
        c.useCaches = false
        return try {
            if (c.responseCode != 200) null
            else c.inputStream.use { String(it.readNBytes(max * 4 + 1), Charsets.UTF_8) }.takeIf { it.length <= max }
        } finally { c.disconnect() }
    }
}
