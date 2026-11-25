package cz.recaption.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream

/**
 * Balíček .ipk aplikace pro televizi LG (webos/obchod/app), sestavený bez nástroje LG.
 *
 * Skládá se stejně jako výstup `ares-package` (@webos-tools/cli 3.2.6, ověřeno rozbalením):
 * archiv `ar` se třemi soubory `debian-binary` („2.0\n“), `control.tar.gz` (jen soubor `control`)
 * a `data.tar.gz` (`usr/palm/applications/<id>/…` a `usr/palm/packages/<id>/packageinfo.json`).
 * Rozdíly proti ares-package jsou záměrné: pevné datum 1. 1. 2000 (webOS 5 odmítá balíček
 * s rokem 1970 a stejné zdroje tak dají stejný balíček), vlastník 0/0 a práva 644/755.
 *
 * Soubor používá jen standardní knihovnu Javy: překládá se i v sestavení (buildSrc → úloha
 * `lgBalicek` v app/build.gradle.kts), aby balíček v APK vznikal stejným kódem, jaký pokrývají
 * unit testy. Postup převzat podle webos/build.py z projektu PlayCaption (AGPL-3.0).
 */
object LgIpk {
    /** 2000-01-01 00:00:00 UTC. */
    const val TIME = 946684800L
    const val APP_ID = "cz.recaption.tv"

    class Entry(val name: String, val mode: Int, val uid: Int, val gid: Int, val mtime: Long, val dir: Boolean, val data: ByteArray)

    class Contents(val members: List<String>, val control: String, val data: List<Entry>, val controlEntries: List<Entry>, val arTimes: List<Long>) {
        fun file(name: String): ByteArray? = data.firstOrNull { !it.dir && it.name == name }?.data
    }

    /** Id a verze z appinfo.json (náš vlastní soubor; stačí jednoduché hledání). */
    fun appInfo(json: String): Pair<String, String> {
        fun field(k: String) = Regex("\"$k\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("appinfo.json: chybí $k")
        val id = field("id"); val version = field("version")
        require(id.matches(Regex("[a-z0-9.+-]+"))) { "appinfo.json: neplatné id" }
        require(version.matches(Regex("[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{1,8}"))) { "appinfo.json: neplatná verze" }
        return id to version
    }

    fun fileName(id: String, version: String) = "${id}_${version}_all.ipk"

    fun packageInfo(id: String, version: String) =
        "{\n  \"id\": \"$id\",\n  \"version\": \"$version\",\n  \"app\": \"$id\"\n}\n"

    fun control(id: String, version: String, installedSize: Long) = listOf(
        "Package: $id", "Version: $version", "Section: misc", "Priority: optional", "Architecture: all",
        "Installed-Size: $installedSize", "Maintainer: N/A <nobody@example.com>",
        "Description: This is a webOS application.", "webOS-Package-Format-Version: 2",
        "webOS-Packager-Version: x.y.x",
    ).joinToString("\n", postfix = "\n")

    /**
     * Sestaví IPK. [files] = cesty uvnitř složky aplikace (s „/“) → obsah; musí obsahovat appinfo.json.
     */
    fun build(files: Map<String, ByteArray>): ByteArray {
        val (id, version) = appInfo(String(files["appinfo.json"] ?: throw IllegalArgumentException("chybí appinfo.json"), Charsets.UTF_8))
        val app = "usr/palm/applications/$id/"
        val pkg = "usr/palm/packages/$id/"
        val content = sortedMapOf<String, ByteArray>()
        for ((name, data) in files) {
            require(name.isNotEmpty() && !name.startsWith("/") && name.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "neplatná cesta $name" }
            require(name.all { it.code in 0x21..0x7e }) { "název souboru jen anglickými písmeny: $name" }
            content[app + name] = data
        }
        content[pkg + "packageinfo.json"] = packageInfo(id, version).toByteArray(Charsets.UTF_8)
        val dirs = sortedSetOf<String>()
        for (name in content.keys) {
            var i = name.indexOf('/')
            while (i >= 0) { dirs.add(name.substring(0, i + 1)); i = name.indexOf('/', i + 1) }
        }
        val data = tar(dirs.map { it to null } + content.map { it.key to it.value })
        val size = content.values.sumOf { it.size.toLong() }
        val control = tar(listOf("control" to control(id, version, size).toByteArray(Charsets.UTF_8)))
        return ar(listOf(
            "debian-binary" to "2.0\n".toByteArray(Charsets.US_ASCII),
            "control.tar.gz" to gzip(control),
            "data.tar.gz" to gzip(data),
        ))
    }

    /** Přečte IPK (pro kontrolu po sestavení a v testech). Nesprávný tvar = výjimka. */
    fun read(ipk: ByteArray): Contents {
        require(ipk.size > 8 && String(ipk, 0, 8, Charsets.US_ASCII) == "!<arch>\n") { "není to archiv ar" }
        var o = 8
        val members = ArrayList<String>()
        val times = ArrayList<Long>()
        var control: List<Entry> = emptyList()
        var data: List<Entry> = emptyList()
        while (o < ipk.size) {
            require(o + 60 <= ipk.size) { "useknutá hlavička ar" }
            val h = String(ipk, o, 60, Charsets.US_ASCII)
            require(h.endsWith("`\n")) { "vadná hlavička ar" }
            val name = h.substring(0, 16).trim().removeSuffix("/")
            times.add(h.substring(16, 28).trim().toLong())
            val size = h.substring(48, 58).trim().toInt()
            require(o + 60 + size <= ipk.size) { "useknutý archiv" }
            val body = ipk.copyOfRange(o + 60, o + 60 + size)
            members.add(name)
            when (name) {
                "debian-binary" -> require(String(body, Charsets.US_ASCII) == "2.0\n") { "debian-binary" }
                "control.tar.gz" -> control = untar(GZIPInputStream(ByteArrayInputStream(body)).readBytes())
                "data.tar.gz" -> data = untar(GZIPInputStream(ByteArrayInputStream(body)).readBytes())
            }
            o += 60 + size + size % 2
        }
        require(members == listOf("debian-binary", "control.tar.gz", "data.tar.gz")) { "nečekané pořadí souborů: $members" }
        val ctl = control.firstOrNull { it.name == "control" } ?: throw IllegalArgumentException("chybí control")
        return Contents(members, String(ctl.data, Charsets.UTF_8), data, control, times)
    }

    /**
     * Ověří sestavený balíček proti zdrojům: přesně tytéž soubory, packageinfo a control s verzí
     * z appinfo.json, pevné datum a vlastník 0. Vrací (id, verze).
     */
    fun verify(ipk: ByteArray, files: Map<String, ByteArray>): Pair<String, String> {
        val (id, version) = appInfo(String(files.getValue("appinfo.json"), Charsets.UTF_8))
        val c = read(ipk)
        val all = c.data + c.controlEntries
        require(all.all { it.mtime == TIME && it.uid == 0 && it.gid == 0 }) { "čas nebo vlastník v tar" }
        require(c.arTimes.all { it == TIME }) { "čas v ar" }
        val expected = files.mapKeys { "usr/palm/applications/$id/" + it.key } +
            ("usr/palm/packages/$id/packageinfo.json" to packageInfo(id, version).toByteArray(Charsets.UTF_8))
        val found = c.data.filter { !it.dir }.associate { it.name to it.data }
        require(found.size == c.data.count { !it.dir }) { "soubor v balíčku dvakrát" }
        require(found.keys == expected.keys) { "jiné soubory v balíčku: " + (found.keys - expected.keys) + (expected.keys - found.keys) }
        for ((k, v) in expected) require(found.getValue(k).contentEquals(v)) { "jiný obsah: $k" }
        require(c.control.lines().contains("Package: $id") && c.control.lines().contains("Version: $version")) { "control" }
        return id to version
    }

    fun sha256(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    /** Popis přibaleného balíčku (assets/lg/receiver-manifest.json). */
    fun manifest(id: String, version: String, ipk: ByteArray): String =
        "{\n  \"schema\": 1,\n  \"platform\": \"webos\",\n  \"id\": \"$id\",\n  \"version\": \"$version\",\n" +
            "  \"file\": \"${fileName(id, version)}\",\n  \"size\": ${ipk.size},\n  \"sha256\": \"${sha256(ipk)}\"\n}\n"

    // ---------------- ar, tar, gzip ----------------

    private fun ar(members: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("!<arch>\n".toByteArray(Charsets.US_ASCII))
        for ((name, data) in members) {
            val h = name.padEnd(16) + TIME.toString().padEnd(12) + "0".padEnd(6) + "0".padEnd(6) +
                "100644".padEnd(8) + data.size.toString().padEnd(10) + "`\n"
            check(h.length == 60)
            out.write(h.toByteArray(Charsets.US_ASCII))
            out.write(data)
            if (data.size % 2 == 1) out.write('\n'.code)
        }
        return out.toByteArray()
    }

    /** POSIX ustar; null = složka. */
    private fun tar(entries: List<Pair<String, ByteArray?>>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((name, data) in entries) {
            val h = ByteArray(512)
            fun put(off: Int, len: Int, s: String) {
                val b = s.toByteArray(Charsets.US_ASCII)
                require(b.size <= len) { "příliš dlouhé: $s" }
                System.arraycopy(b, 0, h, off, b.size)
            }
            fun oct(off: Int, len: Int, v: Long) = put(off, len, java.lang.Long.toOctalString(v).padStart(len - 1, '0') + "\u0000")
            var short = name
            var prefix = ""
            if (name.length > 100) {
                val cut = name.lastIndexOf('/', name.length - 2).also { require(it in 1..155) { "příliš dlouhá cesta $name" } }
                prefix = name.substring(0, cut); short = name.substring(cut + 1)
            }
            put(0, 100, short)
            oct(100, 8, if (data == null) 0x1ED else 0x1A4)   // 755 / 644
            oct(108, 8, 0); oct(116, 8, 0)
            oct(124, 12, (data?.size ?: 0).toLong())
            oct(136, 12, TIME)
            h[156] = (if (data == null) '5' else '0').code.toByte()
            put(257, 6, "ustar\u0000"); put(263, 2, "00")
            put(345, 155, prefix)
            for (i in 148 until 156) h[i] = ' '.code.toByte()
            val sum = h.sumOf { it.toInt() and 0xff }
            put(148, 8, java.lang.Integer.toOctalString(sum).padStart(6, '0') + "\u0000 ")
            out.write(h)
            if (data != null) {
                out.write(data)
                val pad = (512 - data.size % 512) % 512
                out.write(ByteArray(pad))
            }
        }
        out.write(ByteArray(1024))
        val pad = (10240 - out.size() % 10240) % 10240
        out.write(ByteArray(pad))
        return out.toByteArray()
    }

    private fun untar(t: ByteArray): List<Entry> {
        val list = ArrayList<Entry>()
        var o = 0
        fun str(off: Int, len: Int): String {
            var end = off
            while (end < off + len && t[end] != 0.toByte()) end++
            return String(t, off, end - off, Charsets.US_ASCII)
        }
        fun num(off: Int, len: Int) = str(off, len).trim().ifEmpty { "0" }.toLong(8)
        while (o + 512 <= t.size) {
            if ((0 until 512).all { t[o + it] == 0.toByte() }) break
            val stored = num(o + 148, 8)
            var sum = 0L
            for (i in 0 until 512) sum += if (i in 148 until 156) ' '.code else t[o + i].toInt() and 0xff
            require(sum == stored) { "vadný kontrolní součet tar" }
            val prefix = str(o + 345, 155)
            val name = (if (prefix.isEmpty()) "" else "$prefix/") + str(o, 100)
            val type = t[o + 156].toInt().toChar()
            require(type == '0' || type == '5' || type == '\u0000') { "nepodporovaný typ položky: $type" }
            val size = num(o + 124, 12).toInt()
            val dir = type == '5'
            val data = t.copyOfRange(o + 512, o + 512 + size)
            list.add(Entry(name.removeSuffix("/"), num(o + 100, 8).toInt(), num(o + 108, 8).toInt(), num(o + 116, 8).toInt(), num(o + 136, 12), dir, data))
            o += 512 + (size + 511) / 512 * 512
        }
        return list
    }

    /** gzip s nulovým časem v hlavičce – stejné zdroje dají stejné bajty. */
    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x1f, 0x8b.toByte(), 8, 0, 0, 0, 0, 0, 2, 0xff.toByte()))
        val d = Deflater(9, true)
        d.setInput(data); d.finish()
        val buf = ByteArray(8192)
        while (!d.finished()) { val n = d.deflate(buf); out.write(buf, 0, n) }
        d.end()
        val crc = CRC32().apply { update(data) }.value
        for (v in listOf(crc, data.size.toLong())) for (i in 0 until 4) out.write(((v shr (8 * i)) and 0xff).toInt())
        return out.toByteArray()
    }
}
