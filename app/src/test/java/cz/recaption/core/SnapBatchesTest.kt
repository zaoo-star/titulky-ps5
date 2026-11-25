package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files
import java.util.zip.ZipInputStream

/** Záznam snímků pro ladění: názvy dávek, index, mazání nad limit, kdy uložit snímek, /davky a /davka v záznamu v síti. */
class SnapBatchesTest {

    private fun tempRoot(): File = Files.createTempDirectory("snimky").toFile()

    private fun batch(root: File, n: Int, time: String, snaps: Int, size: Int = 10): File {
        val d = File(root, SnapBatches.dirName(n, time)).apply { mkdirs() }
        File(d, SnapBatches.INDEX).writeText("index $n\n")
        for (i in 1..snaps) File(d, SnapBatches.snapName(i)).writeBytes(ByteArray(size) { i.toByte() })
        return d
    }

    @Test fun namesAndNumbers() {
        assertEquals("davka-3-213005", SnapBatches.dirName(3, "213005"))
        assertEquals(3, SnapBatches.number("davka-3-213005"))
        assertNull(SnapBatches.number("davka-3"))
        assertNull(SnapBatches.number("../davka-3-213005"))
        assertNull(SnapBatches.number("jine"))
        assertEquals(1, SnapBatches.next(emptyList()))
        assertEquals(8, SnapBatches.next(listOf("davka-2-100000", "davka-7-101010", "smeti")))
        assertEquals("snimek-0042.jpg", SnapBatches.snapName(42))
        assertTrue(SnapBatches.isSnap("snimek-0042.jpg"))
        assertTrue(!SnapBatches.isSnap("snimek-0042.jpg.tmp"))
    }

    @Test fun scanCountsSnapsAndSorts() {
        val root = tempRoot()
        try {
            batch(root, 10, "120000", 2)
            batch(root, 2, "110000", 3)
            File(root, "cizi").mkdirs()
            File(File(root, "davka-2-110000"), "snimek-0004.jpg.tmp").writeBytes(ByteArray(5))
            val list = SnapBatches.scan(root)
            assertEquals(listOf(2, 10), list.map { it.n })
            assertEquals(3, list[0].snaps)
            assertNotNull(SnapBatches.find(root, 10))
            assertNull(SnapBatches.find(root, 5))
        } finally { root.deleteRecursively() }
    }

    @Test fun pruneDeletesOldestButNeverTheRecordingOne() {
        val l = listOf(
            SnapBatches.Info(1, "davka-1-100000", 1, 100, 0, 0),
            SnapBatches.Info(2, "davka-2-100000", 1, 100, 0, 0),
            SnapBatches.Info(3, "davka-3-100000", 1, 100, 0, 0),
        )
        assertEquals(emptyList<SnapBatches.Info>(), SnapBatches.toDelete(l, 300, null))
        assertEquals(listOf(1), SnapBatches.toDelete(l, 250, null).map { it.n })
        assertEquals(listOf(1, 2), SnapBatches.toDelete(l, 150, null).map { it.n })
        // právě nahrávaná (i když je nejstarší) zůstane
        assertEquals(listOf(2, 3), SnapBatches.toDelete(l, 100, "davka-1-100000").map { it.n })

        val root = tempRoot()
        try {
            batch(root, 1, "100000", 3, 100)
            batch(root, 2, "100100", 3, 100)
            val keep = batch(root, 3, "100200", 3, 100)
            assertEquals(2, SnapBatches.prune(root, 400, keep.name))
            assertEquals(listOf(3), SnapBatches.scan(root).map { it.n })
        } finally { root.deleteRecursively() }
    }

    @Test fun requestedBatchNumber() {
        assertEquals(3, SnapBatches.requested("/davka?k=abc234&n=3"))
        assertNull(SnapBatches.requested("/davka?k=abc234"))
        assertNull(SnapBatches.requested("/davka?k=abc234&n=../1"))
        assertNull(SnapBatches.requested("/davka?k=abc234&n=-1"))
        assertNull(SnapBatches.requested("/davka?k=abc234&n=12345678"))
        assertEquals(LogShare.Route.BATCHES, LogShare.route("GET /davky?k=abc234 HTTP/1.1", "abc234"))
        assertEquals(LogShare.Route.BATCH, LogShare.route("GET /davka?k=abc234&n=1 HTTP/1.1", "abc234"))
        assertEquals(LogShare.Route.BAD_CODE, LogShare.route("GET /davka?k=zzzzzz&n=1 HTTP/1.1", "abc234"))
    }

    @Test fun jsonListsBatches() {
        val j = SnapBatches.json(listOf(SnapBatches.Info(3, "davka-3-213005", 42, 1000, 0, 0)), "davka-3-213005")
        assertTrue(j, j.startsWith("{\"davky\":[{\"n\":3,\"nazev\":\"davka-3-213005\",\"snimku\":42,\"bajtu\":1000,"))
        assertTrue(j, j.endsWith("\"nahrava\":true}]}"))
        assertEquals("{\"davky\":[]}", SnapBatches.json(emptyList(), null))
    }

    @Test fun indexEntry() {
        val e = SnapBatches.entry(7, 0, 3250, "snimek-0007.jpg", 1280, 720, "velka",
            listOf("123 SHOW|Ahoj", "124 MENU|on"), listOf("Press X | Hello" to "titulek"))
        val lines = e.trimEnd().split('\n')
        assertTrue(lines[0], lines[0].startsWith("#0007 "))
        assertTrue(lines[0], lines[0].endsWith(" +3.3s snimek-0007.jpg 1280×720 velka") || lines[0].endsWith(" +3.2s snimek-0007.jpg 1280×720 velka"))
        assertEquals("  123 SHOW|Ahoj", lines[1])
        assertEquals("  CTENI: Press X | Hello → titulek", lines[3])
        assertEquals("prvni", SnapBatches.whyText(SnapBatches.Change.BIG, true))
        assertEquals("zmena", SnapBatches.whyText(SnapBatches.Change.SMALL, false))
    }

    @Test fun diagLogSinceMark() {
        DiagLog.clear()
        DiagLog.add("A", 1000)
        val m = DiagLog.mark()
        assertEquals(emptyList<String>(), DiagLog.since(m))
        DiagLog.add("B", 2000); DiagLog.add("C", 3000)
        assertEquals(listOf("2 B", "3 C"), DiagLog.since(m))
        // víc, než se drží v paměti → jen to, co ještě je
        repeat(400) { DiagLog.add("x$it", 4000) }
        assertEquals(300, DiagLog.since(m).size)
        DiagLog.clear()
    }

    private fun flat(v: Int) = IntArray(SnapBatches.TW * SnapBatches.TH) { v }

    @Test fun thumbAveragesCells() {
        val w = 640; val h = 360
        val y = ByteArray(w * h) { 100 }
        for (r in 0 until 10) for (x in 0 until 10) y[r * w + x] = 200.toByte()
        val t = SnapBatches.thumb(y, w, w, h)!!
        assertEquals(200, t[0])
        assertEquals(100, t[1])
        assertNull(SnapBatches.thumb(ByteArray(10), 5, 5, 2))
    }

    @Test fun diffLevels() {
        val a = flat(100)
        assertEquals(SnapBatches.Change.NONE, SnapBatches.diff(a, flat(103)))
        val small = flat(100).also { it[5] = 140 }
        assertEquals(SnapBatches.Change.SMALL, SnapBatches.diff(a, small))
        assertEquals(SnapBatches.Change.BIG, SnapBatches.diff(a, flat(150)))
        assertEquals(SnapBatches.Change.BIG, SnapBatches.diff(null, a))
    }

    @Test fun pacerSavesChangesAtMostOncePerSecondButBigChangeAfterCalmAtOnce() {
        val p = SnapPacer()
        assertEquals(SnapBatches.Change.BIG, p.offer(flat(100), 0))          // první
        assertNull(p.offer(flat(100), 250))                                    // nic se nezměnilo
        assertNull(p.offer(flat(100), 2000))
        // velká změna po klidu (menu se otevřelo) → hned
        assertEquals(SnapBatches.Change.BIG, p.offer(flat(160), 2250))
        // pohyb dál: nejvýš jednou za sekundu
        assertNull(p.offer(flat(30), 2500))
        assertNull(p.offer(flat(160), 2750))
        assertNull(p.offer(flat(30), 3000))
        assertEquals(SnapBatches.Change.BIG, p.offer(flat(90), 3250))
        // drobná změna (vybraná položka menu) nejdřív po sekundě
        val sel = flat(90).also { it[100] = 130 }
        assertNull(p.offer(sel, 3500))
        assertNull(p.offer(sel, 4000))
        assertEquals(SnapBatches.Change.SMALL, p.offer(sel, 4250))
        // obraz stál od 3500 → velká změna jde hned, i když poslední snímek byl před 250 ms
        assertEquals(SnapBatches.Change.BIG, p.offer(flat(200), 4500))
        assertNull(p.offer(flat(200), 6000))
    }

    @Test fun zipContainsIndexAndSnapsOnly() {
        val root = tempRoot()
        try {
            val d = batch(root, 1, "100000", 2, 50)
            File(d, "snimek-0003.jpg.tmp").writeBytes(ByteArray(3))
            val out = ByteArrayOutputStream()
            SnapBatches.zip(d, out)
            val names = ArrayList<String>()
            ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
                while (true) { val e = z.nextEntry ?: break; names += e.name; if (e.name == "snimek-0001.jpg") assertEquals(50, z.readBytes().size) }
            }
            assertEquals(listOf("index.txt", "snimek-0001.jpg", "snimek-0002.jpg"), names)
        } finally { root.deleteRecursively() }
    }

    @Test fun serverListsAndStreamsBatchesOnlyWithCode() {
        val root = tempRoot()
        try {
            batch(root, 4, "101010", 2, 2000)
            val server = LogServer("abc234", parts = { emptyList() },
                batches = { SnapBatches.json(SnapBatches.scan(root), null) },
                batchDir = { n -> SnapBatches.find(root, n) })
            assertTrue(server.start(0, InetAddress.getByName("127.0.0.1")))
            try {
                val list = String(raw(server.port, "GET /davky?k=abc234 HTTP/1.1"), Charsets.UTF_8)
                assertTrue(list, list.startsWith("HTTP/1.1 200") && list.contains("\"nazev\":\"davka-4-101010\""))
                val bad = String(raw(server.port, "GET /davka?k=zzzzzz&n=4 HTTP/1.1"), Charsets.UTF_8)
                assertTrue(bad, bad.startsWith("HTTP/1.1 403"))
                val missing = String(raw(server.port, "GET /davka?k=abc234&n=9 HTTP/1.1"), Charsets.UTF_8)
                assertTrue(missing, missing.startsWith("HTTP/1.1 404"))
                val zip = raw(server.port, "GET /davka?k=abc234&n=4 HTTP/1.1")
                val text = String(zip, Charsets.ISO_8859_1)
                assertTrue(text.startsWith("HTTP/1.1 200") && text.contains("application/zip") && text.contains("davka-4-101010.zip"))
                val body = zip.copyOfRange(text.indexOf("\r\n\r\n") + 4, zip.size)
                var n = 0
                ZipInputStream(ByteArrayInputStream(body)).use { z -> while (z.nextEntry != null) n++ }
                assertEquals(3, n)
            } finally { server.stop() }
        } finally { root.deleteRecursively() }
    }

    private fun raw(port: Int, line: String): ByteArray =
        Socket(InetAddress.getByName("127.0.0.1"), port).use { s ->
            s.soTimeout = 5000
            s.getOutputStream().apply { write("$line\r\nHost: x\r\nConnection: close\r\n\r\n".toByteArray()); flush() }
            s.getInputStream().readBytes()
        }
}
