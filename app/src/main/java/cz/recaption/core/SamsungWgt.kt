package cz.recaption.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipInputStream

/**
 * Balíček .wgt aplikace pro televize Samsung (tizen/app), sestavený bez nástrojů Samsungu.
 *
 * WGT je obyčejný ZIP se souborem config.xml a obsahem aplikace. Sestavuje se **nepodepsaný**:
 * Samsung pustí do televize jen balíček podepsaný autorským a distribučním certifikátem, a ten
 * distribuční vystavuje Samsung na účet uživatele a jen pro identifikátory (DUID) jeho televizí.
 * Jeden podepsaný balíček pro všechny proto udělat nejde; podpis (author-signature.xml,
 * signature1.xml) přidá až nástroj Samsungu u uživatele (viz tizen/CTI-ME.md).
 *
 * ZIP se skládá ručně s pevným datem 1. 1. 1980 a pevným pořadím, aby stejné zdroje daly stejný
 * soubor i stejný otisk SHA-256. Jen standardní knihovna Javy: soubor se překládá i v sestavení
 * (buildSrc → úloha `samsungBalicek` v app/build.gradle.kts), stejně jako core/LgIpk.kt.
 * Postup podle tizen/build.py z projektu PlayCaption (AGPL-3.0).
 */
object SamsungWgt {
    /** Balíček Tizenu = přesně 10 písmen nebo číslic; id aplikace = balíček + "." + název. */
    const val PACKAGE = "ReCaption1"
    const val APP_ID = "$PACKAGE.ReCaption"

    /** Soubory, které jsou ve webos/obchod/app a tizen/app stejné (liší se jen platforma.js a popis). */
    val SHARED = listOf("app.js", "index.html", "style.css")

    /** Společné složky – každý soubor v nich musí být v obou stejný bajt po bajtu (písma pro hebrejštinu a thajštinu). */
    val SHARED_DIRS = listOf("fonty/")

    private val VERSION = Regex("[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{1,8}")

    fun fileName(version: String) = "ReCaption-Samsung-$version-nepodepsany.wgt"

    /** Podpisy Tizenu – do našeho (nepodepsaného) balíčku nepatří. */
    fun isSignature(name: String) = name == "author-signature.xml" || name.matches(Regex("signature[0-9]+\\.xml"))

    /** (id aplikace, verze) z config.xml (náš vlastní soubor; stačí jednoduché hledání). */
    fun config(xml: String): Pair<String, String> {
        val version = Regex("<widget[^>]*\\sversion=\"([^\"]*)\"").find(xml)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("config.xml: chybí verze")
        val app = Regex("<tizen:application[^>]*\\sid=\"([^\"]*)\"").find(xml)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("config.xml: chybí tizen:application id")
        val pkg = Regex("<tizen:application[^>]*\\spackage=\"([^\"]*)\"").find(xml)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("config.xml: chybí package")
        require(version.matches(VERSION)) { "config.xml: neplatná verze $version" }
        require(pkg.matches(Regex("[0-9A-Za-z]{10}"))) { "config.xml: balíček musí mít 10 písmen/číslic: $pkg" }
        require(app.startsWith("$pkg.") && app.substring(pkg.length + 1).matches(Regex("[0-9A-Za-z]{1,52}"))) { "config.xml: id aplikace $app" }
        return app to version
    }

    /** Verze zapsaná v platforma.js (LG: `verze: '…'`, Samsung: `var VERZE = '…'`). */
    fun platformVersion(js: String): String =
        Regex("(?:VERZE\\s*=|verze\\s*:)\\s*'([^']*)'").find(js)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("platforma.js: chybí verze")

    /**
     * Které společné soubory se ve webos a tizen liší (konce řádků se nepočítají – Windows
     * je při checkoutu může mít jinak). Prázdný seznam = v pořádku; chybějící soubor = liší se.
     */
    fun sharedDiff(webos: Map<String, ByteArray>, tizen: Map<String, ByteArray>): List<String> {
        fun norm(b: ByteArray?) = b?.let { String(it, Charsets.UTF_8).replace("\r\n", "\n") }
        val dirs = (webos.keys + tizen.keys).filter { n -> SHARED_DIRS.any { n.startsWith(it) } }.sorted().distinct()
            .filter { n -> val a = webos[n]; val b = tizen[n]; a == null || b == null || !a.contentEquals(b) }
        return SHARED.filter { norm(webos[it]) == null || norm(webos[it]) != norm(tizen[it]) } + dirs
    }

    /** Sestaví WGT. [files] = cesty uvnitř tizen/app (s „/“) → obsah; musí obsahovat config.xml. */
    fun build(files: Map<String, ByteArray>): ByteArray {
        config(String(files["config.xml"] ?: throw IllegalArgumentException("chybí config.xml"), Charsets.UTF_8))
        val out = ByteArrayOutputStream()
        val central = ByteArrayOutputStream()
        var count = 0
        for ((name, data) in files.toSortedMap()) {
            require(name.isNotEmpty() && !name.startsWith("/") && name.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "neplatná cesta $name" }
            require(name.all { it.code in 0x21..0x7e }) { "název souboru jen anglickými písmeny: $name" }
            require(!isSignature(name.substringAfterLast('/'))) { "podpis do balíčku nepatří: $name" }
            val packed = deflate(data)
            val crc = CRC32().apply { update(data) }.value
            val nameBytes = name.toByteArray(Charsets.US_ASCII)
            val offset = out.size()
            // místní hlavička
            le32(out, 0x04034b50); le16(out, 20); le16(out, 0); le16(out, 8); le16(out, DOS_TIME); le16(out, DOS_DATE)
            le32(out, crc); le32(out, packed.size.toLong()); le32(out, data.size.toLong()); le16(out, nameBytes.size); le16(out, 0)
            out.write(nameBytes); out.write(packed)
            // položka ústředního adresáře (Unix, práva 644)
            le32(central, 0x02014b50); le16(central, 0x0314); le16(central, 20); le16(central, 0); le16(central, 8)
            le16(central, DOS_TIME); le16(central, DOS_DATE); le32(central, crc); le32(central, packed.size.toLong())
            le32(central, data.size.toLong()); le16(central, nameBytes.size); le16(central, 0); le16(central, 0)
            le16(central, 0); le16(central, 0); le32(central, 0x81A4L shl 16); le32(central, offset.toLong())
            central.write(nameBytes)
            count++
        }
        val cdOffset = out.size()
        out.write(central.toByteArray())
        le32(out, 0x06054b50); le16(out, 0); le16(out, 0); le16(out, count); le16(out, count)
        le32(out, central.size().toLong()); le32(out, cdOffset.toLong()); le16(out, 0)
        return out.toByteArray()
    }

    /** Obsah WGT (pro kontrolu po sestavení a v testech). Nesprávný tvar = výjimka. */
    fun read(wgt: ByteArray): Map<String, ByteArray> {
        val m = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(wgt)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                require(!e.isDirectory) { "složka v balíčku: ${e.name}" }
                require(m.put(e.name, z.readBytes()) == null) { "soubor v balíčku dvakrát: ${e.name}" }
            }
        }
        require(m.isNotEmpty()) { "prázdný nebo poškozený balíček" }
        return m
    }

    /** Jména souborů ve WGT; poškozený soubor = prázdný seznam. */
    fun names(wgt: ByteArray): List<String> = runCatching { read(wgt).keys.toList() }.getOrDefault(emptyList())

    /** Ověří sestavený balíček proti zdrojům (stejné soubory a obsah). Vrací (id, verze). */
    fun verify(wgt: ByteArray, files: Map<String, ByteArray>): Pair<String, String> {
        val found = read(wgt)
        require(found.keys == files.keys) { "jiné soubory v balíčku: " + (found.keys - files.keys) + (files.keys - found.keys) }
        for ((k, v) in files) require(found.getValue(k).contentEquals(v)) { "jiný obsah: $k" }
        return config(String(files.getValue("config.xml"), Charsets.UTF_8))
    }

    /** Popis přibaleného balíčku (assets/samsung/receiver-manifest.json). */
    fun manifest(id: String, version: String, wgt: ByteArray): String =
        "{\n  \"schema\": 1,\n  \"platform\": \"tizen\",\n  \"id\": \"$id\",\n  \"package\": \"${id.substringBefore('.')}\",\n" +
            "  \"version\": \"$version\",\n  \"file\": \"${fileName(version)}\",\n  \"size\": ${wgt.size},\n" +
            "  \"sha256\": \"${LgIpk.sha256(wgt)}\",\n  \"signed\": false\n}\n"

    // ---------------- ZIP ----------------

    /** 1. 1. 1980 00:00 ve formátu DOS. */
    private const val DOS_DATE = (0 shl 9) or (1 shl 5) or 1
    private const val DOS_TIME = 0

    private fun le16(o: ByteArrayOutputStream, v: Int) { o.write(v and 0xff); o.write((v shr 8) and 0xff) }
    private fun le32(o: ByteArrayOutputStream, v: Long) { for (i in 0 until 4) o.write(((v shr (8 * i)) and 0xff).toInt()) }
    private fun le32(o: ByteArrayOutputStream, v: Int) = le32(o, v.toLong() and 0xffffffffL)

    private fun deflate(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val d = Deflater(9, true)
        d.setInput(data); d.finish()
        val buf = ByteArray(8192)
        while (!d.finished()) { val n = d.deflate(buf); out.write(buf, 0, n) }
        d.end()
        return out.toByteArray()
    }
}
