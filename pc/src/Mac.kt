package cz.recaption.pc

import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.DoubleByReference
import org.w3c.dom.Element
import java.awt.GraphicsEnvironment
import java.awt.Window
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Co je na Macu jinak než ve Windows (viz `CTI-ME.md`, oddíl Mac). Stejné okno aplikace, stejné čtení
 * a překlad; liší se jen to, co se ptá systému:
 *
 * - okna na obrazovce a jejich poloha ([MacOkna], CoreGraphics přes JNA) a povolení „Nahrávání obrazovky“,
 * - pruh přes hru: myš skrz pruh a pruh i nad hrou přes celou obrazovku ([MacPruh], AppKit),
 * - nastavení chiaki-ng: místo registru Windows předvolby macOS ([MacPredvolby], `com.chiaki.Chiaki`),
 * - klíč DeepL: místo DPAPI Klíčenka ([MacKlicenka]),
 * - chiaki-ng se stahuje jako `.dmg` ([MacDmg]).
 *
 * Kód pro Windows se nemění – jeho funkce na Macu jen hned na začátku odbočí sem.
 */
object Os {
    private val jmeno = System.getProperty("os.name", "").lowercase()
    val mac = jmeno.startsWith("mac") || jmeno.contains("darwin")
    val windows = jmeno.startsWith("windows")
    /** Písmo okna a pruhu. */
    val PISMO = if (mac) "Helvetica Neue" else "Segoe UI"
    /** Písmo se znaky, které hlavní písmo nemá (⚙). */
    val PISMO_ZNAKY = if (mac) PISMO else "Segoe UI Symbol"      // Mac chybějící znaky doplní sám
    val PISMO_ZAZNAM = if (mac) "Menlo" else "Consolas"
    /** Jak se v textech říká tomuto počítači. */
    val POCITAC = if (mac) "Mac" else "PC"

    /** Text okna pro tento počítač: na Macu „Mac“ místo „PC“ (jen celé slovo; „na PC“ → „na Macu“). */
    fun text(s: String) = if (!mac) s else s.replace(Regex("\\b(na|z|v|do) PC\\b")) { m ->
        m.groupValues[1] + " Macu"
    }.replace(Regex("\\bPC\\b"), "Mac")
}

/** Krátký příkaz systému; vrací kód a výstup. [vstup] jde na standardní vstup (nikdy do parametrů – ty jsou vidět ve výpisu procesů). */
internal fun prikaz(vararg cmd: String, vstup: String? = null, sekund: Long = 20): Pair<Int, String> {
    val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
    if (vstup != null) p.outputStream.use { it.write(vstup.toByteArray(Charsets.UTF_8)) } else p.outputStream.close()
    val ven = p.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
    if (!p.waitFor(sekund, TimeUnit.SECONDS)) { p.destroyForcibly(); return -1 to ven }
    return p.exitValue() to ven
}

// ---- okna na obrazovce ----

/** Okno na obrazovce Macu; poloha v bodech obrazovky od levého horního rohu (stejně jako Swing). */
internal data class MacOkno(
    val cislo: Long, val pid: Int, val vlastnik: String, val titulek: String,
    val x: Double, val y: Double, val w: Double, val h: Double, val vrstva: Int,
) {
    /** Jak se okno ukazuje v nabídce „Vybrat okno“ a podle čeho se hledá: program a titulek. */
    val nazev: String get() = if (titulek.isEmpty() || titulek == vlastnik) vlastnik else "$vlastnik – $titulek"
}

internal object MacOkna {
    private interface CG : Library {
        fun CGWindowListCopyWindowInfo(option: Int, relativeToWindow: Int): Pointer?
        fun CGPreflightScreenCaptureAccess(): Byte
        fun CGRequestScreenCaptureAccess(): Byte
    }

    private interface CF : Library {
        fun CFArrayGetCount(a: Pointer): Long
        fun CFArrayGetValueAtIndex(a: Pointer, i: Long): Pointer?
        fun CFDictionaryGetValue(d: Pointer, key: Pointer): Pointer?
        fun CFStringCreateWithCString(alloc: Pointer?, text: String, encoding: Int): Pointer?
        fun CFStringGetCString(s: Pointer, buf: ByteArray, size: Long, encoding: Int): Byte
        fun CFNumberGetValue(n: Pointer, type: Int, out: DoubleByReference): Byte
        fun CFRelease(p: Pointer)
    }

    private val cg: CG by lazy { Native.load("CoreGraphics", CG::class.java) }
    private val cf: CF by lazy { Native.load("CoreFoundation", CF::class.java) }
    private const val UTF8 = 0x08000100
    private const val CISLO_DOUBLE = 13      // kCFNumberDoubleType
    private val klice = HashMap<String, Pointer>()

    private fun klic(k: String): Pointer = synchronized(klice) {
        klice.getOrPut(k) { cf.CFStringCreateWithCString(null, k, UTF8) ?: error("CFString") }
    }

    private fun text(d: Pointer, k: String): String {
        val v = cf.CFDictionaryGetValue(d, klic(k)) ?: return ""
        val buf = ByteArray(1024)
        return if (cf.CFStringGetCString(v, buf, buf.size.toLong(), UTF8).toInt() != 0) Native.toString(buf, "UTF-8") else ""
    }

    private fun cislo(d: Pointer, k: String): Double {
        val v = cf.CFDictionaryGetValue(d, klic(k)) ?: return 0.0
        val out = DoubleByReference()
        return if (cf.CFNumberGetValue(v, CISLO_DOUBLE, out).toInt() != 0) out.value else 0.0
    }

    /**
     * Okna na obrazovce odpředu dozadu. [vse] = i okna mimo obrazovku (minimalizovaná, na jiné ploše).
     * Titulky cizích oken systém vydá jen s povolením Nahrávání obrazovky; jméno programu vždy.
     */
    fun seznam(vse: Boolean = false): List<MacOkno> {
        if (!Os.mac) return emptyList()
        val pole = cg.CGWindowListCopyWindowInfo((if (vse) 0 else 1) or 16, 0) ?: return emptyList()
        try {
            val n = cf.CFArrayGetCount(pole)
            val vysledek = ArrayList<MacOkno>(n.toInt())
            for (i in 0 until n) {
                val d = cf.CFArrayGetValueAtIndex(pole, i) ?: continue
                val b = cf.CFDictionaryGetValue(d, klic("kCGWindowBounds")) ?: continue
                vysledek += MacOkno(
                    cislo(d, "kCGWindowNumber").toLong(), cislo(d, "kCGWindowOwnerPID").toInt(),
                    text(d, "kCGWindowOwnerName"), text(d, "kCGWindowName"),
                    cislo(b, "X"), cislo(b, "Y"), cislo(b, "Width"), cislo(b, "Height"), cislo(d, "kCGWindowLayer").toInt(),
                )
            }
            return vysledek
        } finally { cf.CFRelease(pole) }
    }

    /** Má ReCaption povolené Nahrávání obrazovky (bez něj dostane místo okna hry prázdný obraz)? */
    fun povoleno(): Boolean = !Os.mac || runCatching { cg.CGPreflightScreenCaptureAccess().toInt() != 0 }.getOrDefault(true)

    /** Požádá o povolení (systém ukáže dotaz jen poprvé) a otevře Nastavení systému na správné stránce. */
    fun pozadej() {
        runCatching { cg.CGRequestScreenCaptureAccess() }
        runCatching { ProcessBuilder("open", "x-apple.systempreferences:com.apple.preference.security?Privacy_ScreenCapture").start() }
    }

    /**
     * Výška titulku okna v bodech: okno přes celou obrazovku ho nemá, běžné okno 28 bodů.
     * Stejné pravidlo má `cteni.py` (`OknoMac`), aby se poloha titulků ve snímku a na obrazovce shodla.
     */
    fun titulek(o: MacOkno): Double {
        for (s in GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices) {
            val b = s.defaultConfiguration.bounds
            val pres = Math.abs(o.x - b.x) < 2 && Math.abs(o.w - b.width) < 2 &&
                Math.abs((o.y + o.h) - (b.y + b.height)) < 2 && o.h >= b.height - 60
            if (pres) return 0.0
        }
        return TITULEK
    }

    const val TITULEK = 28.0
}

/** [OknoHry] na Macu: stejné hledání jako `OknoMac` v `cteni.py`. */
internal class MacOknoHry(nazev: String) {
    private val hledat = nazev.lowercase()
    private val procesu = hledat.removePrefix("pid:").toIntOrNull()?.takeIf { hledat.startsWith("pid:") }
    private val mojePid = ProcessHandle.current().pid().toInt()

    private fun najdi(vse: Boolean): MacOkno? {
        val okna = MacOkna.seznam(vse).filter { it.vrstva == 0 && it.w >= 100 && it.h >= 100 }
        return if (procesu != null) okna.filter { it.pid == procesu }.maxByOrNull { it.w * it.h }
        else okna.firstOrNull { it.pid != mojePid && it.nazev.lowercase().contains(hledat) }
    }

    fun existuje(): Boolean = najdi(true) != null

    /** Vnitřek okna (bez titulku) ve skutečných bodech obrazovky – jako [OknoHry.oblast] ve Windows. */
    fun oblast(): Obdelnik? {
        val o = najdi(false) ?: najdi(true) ?: return null
        val t = MacOkna.titulek(o)
        val m = meritko()
        val w = o.w
        val v = o.h - t
        return if (w < 320 || v < 180) null
        else Obdelnik((o.x * m).toInt(), ((o.y + t) * m).toInt(), (w * m).toInt(), (v * m).toInt())
    }
}

/** Nabídka „Vybrat okno“ na Macu: okna jiných programů, aspoň 320×180. */
internal fun macOtevrenaOkna(): List<String> {
    val moje = ProcessHandle.current().pid().toInt()
    val systemova = setOf("Dock", "Window Server", "Control Center", "Ovládací centrum", "Spotlight", "Notification Center")
    return MacOkna.seznam().filter { it.vrstva == 0 && it.pid != moje && it.w >= 320 && it.h >= 180 && it.vlastnik !in systemova }
        .map { it.nazev }.filter { it.isNotBlank() }.distinct()
}

// ---- pruh přes hru ----

/**
 * Okno pruhu na Macu: myš skrz něj do hry, drží se nad ostatními okny a je vidět i nad programem přes
 * celou obrazovku (ten má na Macu vlastní plochu). Swing tohle neumí, proto přímo AppKit – a ten se smí
 * volat jen z hlavního vlákna aplikace, kam se práce posílá přes `dispatch_async_f`.
 */
internal object MacPruh {
    private interface Prace : Callback { fun invoke(kontext: Pointer?) }
    private interface Dispatch : Library { fun dispatch_async_f(fronta: Pointer, kontext: Pointer?, prace: Prace) }

    /** Jak dopadlo poslední nastavení okna (jen pro zkoušku): „nastaveno“ / „okno nenalezeno“ / chyba. */
    @Volatile var stav = ""
        private set

    /** Rozpracované úkoly – aby je Java neuklidila dřív, než je hlavní vlákno vykoná. */
    private val cekajici = java.util.Collections.synchronizedSet(HashSet<Prace>())

    /** Vrací true, když se okno našlo a nastavení odešlo hlavnímu vláknu. */
    fun nastav(okno: Window): Boolean = runCatching {
        val b = okno.bounds
        val moje = ProcessHandle.current().pid().toInt()
        val cislo = MacOkna.seznam(true).firstOrNull {
            it.pid == moje && Math.abs(it.x - b.x) < 2 && Math.abs(it.y - b.y) < 2 &&
                Math.abs(it.w - b.width) < 2 && Math.abs(it.h - b.height) < 2
        }?.cislo ?: run { stav = "okno nenalezeno"; return false }
        val objc = NativeLibrary.getInstance("objc")
        val posli: Function = objc.getFunction("objc_msgSend")
        val trida: Function = objc.getFunction("objc_getClass")
        val sel: Function = objc.getFunction("sel_registerName")
        fun s(n: String) = sel.invokePointer(arrayOf<Any?>(n))
        val system = NativeLibrary.getInstance("System")
        val fronta = system.getGlobalVariableAddress("_dispatch_main_q")
        val dispatch = Native.load("System", Dispatch::class.java)
        lateinit var prace: Prace
        prace = object : Prace {
            override fun invoke(kontext: Pointer?) {
                try {
                    val app = posli.invokePointer(arrayOf<Any?>(trida.invokePointer(arrayOf<Any?>("NSApplication")), s("sharedApplication")))
                    val okna = posli.invokePointer(arrayOf<Any?>(app, s("windows")))
                    val n = posli.invokeLong(arrayOf<Any?>(okna, s("count")))
                    for (i in 0 until n) {
                        val w = posli.invokePointer(arrayOf<Any?>(okna, s("objectAtIndex:"), i)) ?: continue
                        if (posli.invokeLong(arrayOf<Any?>(w, s("windowNumber"))) != cislo) continue
                        posli.invokeLong(arrayOf<Any?>(w, s("setIgnoresMouseEvents:"), 1L))
                        posli.invokeLong(arrayOf<Any?>(w, s("setLevel:"), UROVEN))
                        posli.invokeLong(arrayOf<Any?>(w, s("setCollectionBehavior:"), CHOVANI))
                        posli.invokeLong(arrayOf<Any?>(w, s("setHasShadow:"), 0L))
                        stav = "nastaveno"
                    }
                } catch (e: Throwable) {
                    stav = e.toString()
                } finally { cekajici.remove(prace) }
            }
        }
        cekajici.add(prace)
        dispatch.dispatch_async_f(fronta, null, prace)
        true
    }.getOrDefault(false)

    /** NSStatusWindowLevel – nad běžnými okny i nad hrou přes celou obrazovku. */
    private const val UROVEN = 25L
    /** Na všech plochách, i vedle programu přes celou obrazovku, mimo přepínání oken (Cmd+Tab / Mission Control). */
    private const val CHOVANI = (1L shl 0) or (1L shl 4) or (1L shl 6) or (1L shl 8)
}

// ---- předvolby macOS (nastavení chiaki-ng) ----

/**
 * Nastavení chiaki-ng na Macu. Qt (QSettings) ho ukládá do předvoleb macOS: doména `com.chiaki.Chiaki`
 * (profil `com.chiaki.Chiaki-<profil>`), klíče skupin spojené tečkou (`registered_hosts.1.rp_key`),
 * text jako text, čísla jako čísla a bajty jako data. Čte i zapisuje se příkazem `defaults` – soubor
 * `.plist` se přímo nepřepisuje, systém si předvolby drží v paměti a přepsal by ho zpátky.
 */
internal object MacPredvolby {
    fun domena(profil: String?) = if (profil.isNullOrEmpty()) "com.chiaki.Chiaki" else "com.chiaki.Chiaki-$profil"

    /** Všechny hodnoty domény: String, Long, Double, Boolean nebo ByteArray. Neexistující doména = prázdné. */
    fun nacti(domena: String): Map<String, Any> {
        val (kod, xml) = prikaz("/usr/bin/defaults", "export", domena, "-")
        return if (kod == 0) zXml(xml) else emptyMap()
    }

    /** Výpis `defaults export` (XML) na hodnoty; vnořené slovníky a pole se přeskočí (Qt je nepoužívá). */
    fun zXml(xml: String): Map<String, Any> {
        val f = DocumentBuilderFactory.newInstance()
        // hlavička výpisu odkazuje na popis formátu na webu Applu – ten se nestahuje
        runCatching { f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        val b = f.newDocumentBuilder()
        b.setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
        val koren = b.parse(org.xml.sax.InputSource(java.io.StringReader(xml))).documentElement
        val dict = (0 until koren.childNodes.length).map { koren.childNodes.item(it) }.filterIsInstance<Element>()
            .firstOrNull { it.tagName == "dict" } ?: return emptyMap()
        val vysledek = LinkedHashMap<String, Any>()
        var klic: String? = null
        for (i in 0 until dict.childNodes.length) {
            val e = dict.childNodes.item(i) as? Element ?: continue
            if (e.tagName == "key") { klic = e.textContent; continue }
            val k = klic ?: continue
            klic = null
            val v: Any? = when (e.tagName) {
                "string" -> e.textContent
                "integer" -> e.textContent.trim().toLongOrNull()
                "real" -> e.textContent.trim().toDoubleOrNull()
                "true" -> true
                "false" -> false
                "data" -> runCatching { Base64.getMimeDecoder().decode(e.textContent.trim()) }.getOrNull()
                else -> null
            }
            if (v != null) vysledek[k] = v
        }
        return vysledek
    }

    /** Parametry `defaults write` pro hodnotu. */
    fun parametry(v: Any): List<String> = when (v) {
        is ByteArray -> listOf("-data", v.joinToString("") { "%02x".format(it) })
        is Boolean -> listOf("-bool", if (v) "true" else "false")
        is Int, is Long -> listOf("-int", v.toString())
        is Double, is Float -> listOf("-float", v.toString())
        else -> listOf("-string", v.toString())
    }

    fun zapis(domena: String, klic: String, v: Any) {
        val (kod, ven) = prikaz("/usr/bin/defaults", "write", domena, klic, *parametry(v).toTypedArray())
        if (kod != 0) error("předvolby macOS: zápis selhal (${ven.trim().take(120)})")
    }

    fun smaz(domena: String, klic: String) { prikaz("/usr/bin/defaults", "delete", domena, klic) }
}

// ---- Klíčenka ----

/**
 * Tajný údaj hráče (klíč DeepL) v Klíčence macOS, přes nástroj `security`. Klíč se předává standardním
 * vstupem, ne parametrem – parametry příkazů jsou vidět ve výpisu procesů.
 */
internal object MacKlicenka {
    private const val SLUZBA = "Titulky PS5"

    fun nacti(ucet: String): String? {
        val (kod, ven) = prikaz("/usr/bin/security", "find-generic-password", "-a", ucet, "-s", SLUZBA, "-w")
        return if (kod == 0) ven.trim().takeIf { it.isNotEmpty() } else null
    }

    fun uloz(ucet: String, tajne: String): Boolean {
        // jen znaky, které příkaz v uvozovkách nezmění (klíč DeepL jsou písmena, číslice, pomlčky a dvojtečka)
        if (tajne.any { it == '"' || it == '\\' || it == '\n' || it == '\r' }) return false
        val (kod, _) = prikaz("/usr/bin/security", "-i", vstup = "add-generic-password -U -a \"$ucet\" -s \"$SLUZBA\" -w \"$tajne\"\n")
        return kod == 0 && nacti(ucet) == tajne
    }

    fun smaz(ucet: String) { prikaz("/usr/bin/security", "delete-generic-password", "-a", ucet, "-s", SLUZBA) }
}

// ---- obraz disku (.dmg) ----

internal object MacDmg {
    /** Zkopíruje aplikaci `<jmeno>.app` z obrazu disku [dmg] do složky [kam]. */
    fun rozbal(dmg: File, kam: File, jmeno: String) {
        val bod = File(kam.parentFile, "${kam.name}-dmg")
        bod.deleteRecursively()
        bod.mkdirs()
        val (kod, ven) = prikaz("/usr/bin/hdiutil", "attach", "-nobrowse", "-readonly", "-noautoopen", "-mountpoint", bod.path, dmg.path, sekund = 120)
        if (kod != 0) error("obraz disku nejde otevřít (${ven.trim().takeLast(120)})")
        try {
            val app = File(bod, jmeno).takeIf { it.isDirectory }
                ?: bod.listFiles()?.firstOrNull { it.isDirectory && it.name.endsWith(".app") }
                ?: error("v obrazu disku není aplikace")
            kam.mkdirs()
            // ditto zachová odkazy, práva i podpis aplikace
            val (k2, v2) = prikaz("/usr/bin/ditto", app.path, File(kam, app.name).path, sekund = 300)
            if (k2 != 0) error("kopírování aplikace selhalo (${v2.trim().takeLast(120)})")
        } finally {
            prikaz("/usr/bin/hdiutil", "detach", bod.path, "-force", sekund = 60)
            bod.delete()
        }
    }
}
