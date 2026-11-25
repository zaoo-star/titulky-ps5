// Náhrady za to, co logika ReCaptionu (app/src/main/java/cz/recaption/core) bere z Javy a Kotlin pro iPhone nemá.
// Jsou ve stejném balíčku, takže soubory z Androidu se překládají beze změny. Chovají se jako Java v tom rozsahu,
// v jakém je logika používá (formáty %s %d %x %X %.Nf, zaokrouhlení Math.round…).
package cz.recaption.core

import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.ptr

/** Na iPhonu nic nezamyká: logika běží na jednom vlákně (aplikace ji volá z jedné fronty). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class Synchronized

typealias Volatile = kotlin.concurrent.Volatile

inline fun <R> synchronized(@Suppress("UNUSED_PARAMETER") lock: Any, block: () -> R): R = block()

object Math {
    const val PI = kotlin.math.PI
    fun round(v: Float): Int = if (v.isNaN()) 0 else kotlin.math.floor(v + 0.5f).let {
        when { it >= Int.MAX_VALUE.toFloat() -> Int.MAX_VALUE; it <= Int.MIN_VALUE.toFloat() -> Int.MIN_VALUE; else -> it.toInt() }
    }
    fun round(v: Double): Long = if (v.isNaN()) 0 else kotlin.math.floor(v + 0.5).let {
        when { it >= Long.MAX_VALUE.toDouble() -> Long.MAX_VALUE; it <= Long.MIN_VALUE.toDouble() -> Long.MIN_VALUE; else -> it.toLong() }
    }
    fun abs(v: Int) = kotlin.math.abs(v)
    fun abs(v: Long) = kotlin.math.abs(v)
    fun abs(v: Float) = kotlin.math.abs(v)
    fun abs(v: Double) = kotlin.math.abs(v)
    fun floorDiv(a: Int, b: Int): Int { val q = a / b; return if ((a % b != 0) && ((a xor b) < 0)) q - 1 else q }
    fun floorMod(a: Int, b: Int): Int = a - floorDiv(a, b) * b
    fun floorDiv(a: Long, b: Long): Long { val q = a / b; return if ((a % b != 0L) && ((a xor b) < 0)) q - 1 else q }
    fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b
    fun max(a: Int, b: Int) = kotlin.math.max(a, b)
    fun min(a: Int, b: Int) = kotlin.math.min(a, b)
    fun max(a: Float, b: Float) = kotlin.math.max(a, b)
    fun min(a: Float, b: Float) = kotlin.math.min(a, b)
    fun sqrt(v: Double) = kotlin.math.sqrt(v)
    fun ceil(v: Double) = kotlin.math.ceil(v)
    fun floor(v: Double) = kotlin.math.floor(v)
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
object System {
    /** Čas od 1. 1. 1970 v ms jako v Javě. */
    fun currentTimeMillis(): Long = clock(platform.posix.CLOCK_REALTIME) / 1_000_000L
    fun nanoTime(): Long = clock(platform.posix.CLOCK_MONOTONIC)

    private fun clock(id: Int): Long = kotlinx.cinterop.memScoped {
        val ts = alloc<platform.posix.timespec>()
        platform.posix.clock_gettime(id.convert(), ts.ptr)
        ts.tv_sec.toLong() * 1_000_000_000L + ts.tv_nsec.toLong()
    }
}

class Charset(val name: String)
object Charsets {
    val UTF_8 = Charset("UTF-8")
    val US_ASCII = Charset("US-ASCII")
    val ISO_8859_1 = Charset("ISO-8859-1")
}

fun String.toByteArray(charset: Charset): ByteArray = when (charset.name) {
    "UTF-8" -> encodeToByteArray()
    "US-ASCII" -> ByteArray(length) { i -> this[i].code.let { if (it < 128) it else '?'.code }.toByte() }
    else -> ByteArray(length) { i -> this[i].code.let { if (it < 256) it else '?'.code }.toByte() }
}

fun String(bytes: ByteArray): String = bytes.decodeToString()

fun String(bytes: ByteArray, charset: Charset): String = String(bytes, 0, bytes.size, charset)

fun String(bytes: ByteArray, offset: Int, length: Int, charset: Charset): String = when (charset.name) {
    "UTF-8" -> bytes.decodeToString(offset, offset + length)
    "US-ASCII" -> buildString { for (i in offset until offset + length) append((bytes[i].toInt() and 0x7F).toChar()) }
    else -> buildString { for (i in offset until offset + length) append((bytes[i].toInt() and 0xFF).toChar()) }
}

fun String.Companion.format(format: String, vararg args: Any?): String = jformat(format, args)

@Suppress("UNUSED_PARAMETER")
fun String.Companion.format(locale: java.util.Locale?, format: String, vararg args: Any?): String = jformat(format, args)

fun String.format(vararg args: Any?): String = jformat(this, args)

/** printf jako v Javě, pro %s %d %x %X %c %f %% a %n s příznaky -, 0, +, mezera, čárka, šířkou a přesností. */
internal fun jformat(format: String, args: Array<out Any?>): String {
    val out = StringBuilder()
    var i = 0
    var next = 0
    while (i < format.length) {
        val c = format[i]
        if (c != '%') { out.append(c); i++; continue }
        var j = i + 1
        var index = -1
        val digitsStart = j
        while (j < format.length && format[j].isDigit()) j++
        if (j < format.length && format[j] == '$' && j > digitsStart) { index = format.substring(digitsStart, j).toInt() - 1; j++ } else j = digitsStart
        val flags = StringBuilder()
        while (j < format.length && format[j] in "-0+ ,#") flags.append(format[j++])
        val wStart = j
        while (j < format.length && format[j].isDigit()) j++
        val width = if (j > wStart) format.substring(wStart, j).toInt() else 0
        var precision = -1
        if (j < format.length && format[j] == '.') {
            j++; val pStart = j
            while (j < format.length && format[j].isDigit()) j++
            precision = if (j > pStart) format.substring(pStart, j).toInt() else 0
        }
        if (j >= format.length) throw IllegalArgumentException("formát: $format")
        val conv = format[j]
        i = j + 1
        if (conv == '%') { out.append('%'); continue }
        if (conv == 'n') { out.append('\n'); continue }
        val arg = args.getOrNull(if (index >= 0) index else next++)
        var body = when (conv) {
            's', 'S' -> (arg?.toString() ?: "null").let { if (precision >= 0) it.take(precision) else it }.let { if (conv == 'S') it.uppercase() else it }
            'c' -> when (arg) { is Char -> arg.toString(); is Number -> arg.toInt().toChar().toString(); else -> arg.toString() }
            'd' -> {
                val n = (arg as Number).toLong()
                val s = kotlin.math.abs(n).toString().let { if (n == Long.MIN_VALUE) "9223372036854775808" else it }
                val grouped = if (',' in flags) s.reversed().chunked(3).joinToString(" ").reversed() else s
                (if (n < 0) "-" else if ('+' in flags) "+" else if (' ' in flags) " " else "") + grouped
            }
            'x', 'X' -> {
                val s = when (arg) {
                    is Byte -> (arg.toInt() and 0xFF).toString(16)
                    is Short -> (arg.toInt() and 0xFFFF).toString(16)
                    is Int -> arg.toUInt().toString(16)
                    is Long -> arg.toULong().toString(16)
                    else -> (arg as Number).toLong().toULong().toString(16)
                }
                if (conv == 'X') s.uppercase() else s
            }
            'f' -> {
                val v = (arg as Number).toDouble()
                fixed(v, if (precision < 0) 6 else precision).let { if (v >= 0 && '+' in flags) "+$it" else it }
            }
            else -> throw IllegalArgumentException("formát: %$conv")
        }
        if (body.length < width) {
            body = when {
                '-' in flags -> body.padEnd(width)
                '0' in flags && conv != 's' && conv != 'c' -> {
                    val sign = if (body.startsWith("-") || body.startsWith("+")) body.substring(0, 1) else ""
                    sign + body.substring(sign.length).padStart(width - sign.length, '0')
                }
                else -> body.padStart(width)
            }
        }
        out.append(body)
    }
    return out.toString()
}

/** Číslo s pevným počtem desetinných míst, zaokrouhlené od nuly jako v Javě (HALF_UP). */
private fun fixed(v: Double, decimals: Int): String {
    if (v.isNaN()) return "NaN"
    if (v.isInfinite()) return if (v > 0) "Infinity" else "-Infinity"
    var scale = 1.0
    repeat(decimals) { scale *= 10 }
    val neg = v < 0
    val scaled = kotlin.math.floor(kotlin.math.abs(v) * scale + 0.5 + 1e-9)
    val whole = (scaled / scale).toLong()
    val frac = (scaled - whole * scale).toLong()
    val s = if (decimals == 0) "$whole" else "$whole." + frac.toString().padStart(decimals, '0')
    return if (neg && scaled != 0.0) "-$s" else s
}

/** Jen co logika potřebuje: zda je znak latinka. */
object Character {
    enum class UnicodeScript {
        LATIN, OTHER;
        companion object {
            fun of(cp: Int): UnicodeScript = when (cp) {
                in 0x41..0x5A, in 0x61..0x7A, 0xAA, 0xBA, in 0xC0..0xD6, in 0xD8..0xF6, in 0xF8..0x24F,
                in 0x1E00..0x1EFF, in 0x2C60..0x2C7F, in 0xA720..0xA7FF, in 0xFF21..0xFF3A, in 0xFF41..0xFF5A -> LATIN
                else -> OTHER
            }
        }
    }
}

fun <K : Comparable<K>, V> Map<K, V>.toSortedMap(): Map<K, V> =
    entries.sortedBy { it.key }.associate { it.key to it.value }

val Any.javaClass: kotlin.reflect.KClass<out Any> get() = this::class

// --- kousky souborů, které se pro iPhone nepřekládají (síť, soubory) – stejné hodnoty jako v Androidu ---

/** Z `TvSender.kt`: jen převod textu na řetězec JSON (zbytek – spojení s TV – má iPhone ve Swiftu). */
object TvSender {
    fun json(s: String): String {
        val sb = StringBuilder(s.length + 2).append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> {}
            c == '\t' -> sb.append(' ')
            c < ' ' || c == ' ' || c == ' ' -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }
}

/** Z `SnapBatches.kt`: jen popis dávky snímků (pro `ReportUpload.offered`). */
object SnapBatches {
    data class Info(val n: Int, val name: String, val snaps: Int, val bytes: Long, val start: Long, val end: Long)
}

/** Z `SamsungWgt.kt`: jen jméno balíčku a souboru (pro `TvReceiver`). */
object SamsungWgt {
    const val PACKAGE = "ReCaption1"
    const val APP_ID = "$PACKAGE.ReCaption"
    fun fileName(version: String) = "ReCaption-Samsung-$version-nepodepsany.wgt"
    /** Balíček pro Samsung iPhone nepřibaluje (`SamsungInstall.bundle` se na něm nevolá) – nic neprohlíží. */
    fun names(@Suppress("UNUSED_PARAMETER") data: ByteArray): List<String> = emptyList()
    fun isSignature(name: String) = name == "author-signature.xml" || name.matches(Regex("signature[0-9]+\\.xml"))
}


fun String.toByteArray(): ByteArray = encodeToByteArray()

/**
 * LinkedHashMap jako v Javě: s pořadím podle posledního použití (`accessOrder`) a `removeEldestEntry`
 * (paměť překladů, barvy postav). Kotlin na iPhonu má LinkedHashMap jen podle vložení a nedá se rozšířit.
 */
open class LinkedHashMap<K, V> private constructor(
    private val inner: kotlin.collections.LinkedHashMap<K, V>,
    private val accessOrder: Boolean,
) : MutableMap<K, V> by inner {
    constructor(initialCapacity: Int = 16, @Suppress("UNUSED_PARAMETER") loadFactor: Float = 0.75f, accessOrder: Boolean = false) :
        this(kotlin.collections.LinkedHashMap<K, V>(initialCapacity), accessOrder)
    constructor(original: Map<out K, V>) : this(kotlin.collections.LinkedHashMap<K, V>(original), false)

    protected open fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = false

    override fun get(key: K): V? {
        val v = inner[key]
        if (accessOrder && (v != null || inner.containsKey(key))) {
            @Suppress("UNCHECKED_CAST") inner.remove(key); inner.put(key, v as V)
        }
        return v
    }

    override fun put(key: K, value: V): V? {
        val prev = if (accessOrder && inner.containsKey(key)) inner.remove(key) else null
        val r = inner.put(key, value)
        val eldest = inner.entries.firstOrNull()
        if (eldest != null && removeEldestEntry(eldest)) inner.remove(eldest.key)
        return prev ?: r
    }

    override fun putAll(from: Map<out K, V>) { for ((k, v) in from) put(k, v) }

    override fun equals(other: Any?): Boolean = inner == other
    override fun hashCode(): Int = inner.hashCode()
    override fun toString(): String = inner.toString()
}

/** Z `LgIpk.kt`: jen jméno aplikace v TV LG, jméno souboru a otisk (pro `LgInstall` a `SamsungInstall`). */
object LgIpk {
    const val APP_ID = "cz.recaption.tv"
    fun fileName(id: String, version: String) = "${id}_${version}_all.ipk"
    fun sha256(data: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
}
