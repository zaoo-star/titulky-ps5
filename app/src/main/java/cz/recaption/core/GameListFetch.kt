package cz.recaption.core

/**
 * Stažení seznamu her ze serveru (společné pro Android `GameListUpdate` a PC `SeznamHer`, testy `GameListFetchTest`).
 *
 * Data her nejsou ve veřejném repozitáři ani v aplikaci: leží v soukromém repu a na serveru (Cloudflare, stejný jako
 * „Poslat vývojáři“). Adresa serveru je na stránkách s pomocí (`hlaseni.json`, [ReportUpload.config]), takže nová adresa
 * nepotřebuje novou aplikaci. Postup: `hlaseni.json` → `GET <server>/v1/hry/verze` → je-li vyšší než [current],
 * `GET <server>/v1/hry/profily` → přísné čtení ([GameListJson.parse]).
 */
object GameListFetch {
    const val PATH_VERSION = "/v1/hry/verze"
    const val PATH_LIST = "/v1/hry/profily"

    enum class Outcome { UPDATED, CURRENT, FAILED }

    /** Výsledek: při [Outcome.UPDATED] text seznamu (k uložení) a přečtený seznam; [detail] do záznamu. */
    class Result(val outcome: Outcome, val text: String?, val catalog: GameCatalog?, val detail: String)

    /**
     * [current] = verze seznamu, podle kterého aplikace jede; [appVersion] = versionCode (pole `minAppVerze`);
     * [get] = text z adresy nejvýš o daném počtu znaků, null = nejde (smí hodit výjimku – vezme se jako chyba sítě).
     */
    fun run(current: Int, appVersion: Int, get: (url: String, max: Int) -> String?): Result = try {
        val cfg = ReportUpload.config(get(ReportUpload.CONFIG_URL, ReportUpload.MAX_ANSWER))
        val remote = cfg?.let { c -> get(c.url + PATH_VERSION, 100)?.let { GameListJson.versionOf(it) } }
        when {
            cfg == null -> fail("server nenastaveny")
            remote == null -> fail("verze nezjistena")
            remote <= current -> Result(Outcome.CURRENT, null, null, "aktualni $current")
            else -> {
                val text = get(cfg.url + PATH_LIST, GameListJson.MAX_SIZE)
                val r = text?.let { GameListJson.parse(it, appVersion) }
                val cat = r?.catalog
                when {
                    text == null || cat == null -> fail("seznam neplatny: ${r?.problem ?: "stazeni"}")
                    cat.version <= current -> Result(Outcome.CURRENT, null, null, "aktualni $current")
                    else -> Result(Outcome.UPDATED, text, cat, "novy ${cat.version} (${cat.games.size} her)")
                }
            }
        }
    } catch (t: Throwable) { fail("chyba ${t.javaClass.simpleName}") }

    private fun fail(detail: String) = Result(Outcome.FAILED, null, null, detail)
}
