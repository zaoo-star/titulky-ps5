package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GameListFetchTest {
    private val server = "https://schranka.example.workers.dev"
    private val config = "{\"format\":1,\"url\":\"$server\",\"maxMB\":45}"
    private val list = "{\"format\":1,\"verze\":12,\"minAppVerze\":1,\"hry\":[{\"id\":\"ukazka\",\"nazev\":\"Ukázka\",\"jmena\":[\"Zorg\"]}]}"

    private fun web(pages: Map<String, String?>): (String, Int) -> String? = { url, max ->
        pages[url]?.takeIf { it.length <= max }
    }
    private val all = mapOf(
        ReportUpload.CONFIG_URL to config,
        server + GameListFetch.PATH_VERSION to "12\n",
        server + GameListFetch.PATH_LIST to list,
    )

    @Test fun newerListIsDownloaded() {
        val r = GameListFetch.run(0, 200, web(all))
        assertEquals(GameListFetch.Outcome.UPDATED, r.outcome)
        assertEquals(list, r.text)
        assertEquals(12, r.catalog!!.version)
        assertEquals("ukazka", r.catalog!!.of("ukazka").id)
    }

    @Test fun sameVersionIsNotDownloaded() {
        var asked = false
        val r = GameListFetch.run(12, 200) { url, max -> if (url.endsWith(GameListFetch.PATH_LIST)) asked = true; web(all)(url, max) }
        assertEquals(GameListFetch.Outcome.CURRENT, r.outcome)
        assertEquals(false, asked)
        assertNull(r.catalog)
    }

    @Test fun missingServerOrBrokenListFails() {
        assertEquals(GameListFetch.Outcome.FAILED, GameListFetch.run(0, 200, web(all - ReportUpload.CONFIG_URL)).outcome)
        assertEquals(GameListFetch.Outcome.FAILED, GameListFetch.run(0, 200, web(all + (server + GameListFetch.PATH_VERSION to "<html>"))).outcome)
        assertEquals(GameListFetch.Outcome.FAILED, GameListFetch.run(0, 200, web(all + (server + GameListFetch.PATH_LIST to "{}"))).outcome)
        // síť hodí výjimku → chyba, ne pád
        val r = GameListFetch.run(0, 200) { _, _ -> throw java.io.IOException("x") }
        assertEquals(GameListFetch.Outcome.FAILED, r.outcome)
        assertNotNull(r.detail)
    }

    @Test fun listForNewerAppFails() {
        val r = GameListFetch.run(0, 1, web(all + (server + GameListFetch.PATH_LIST to list.replace("\"minAppVerze\":1", "\"minAppVerze\":999"))))
        assertEquals(GameListFetch.Outcome.FAILED, r.outcome)
    }
}
