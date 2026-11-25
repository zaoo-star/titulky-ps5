// Náhrady za třídy Javy, které logika ReCaptionu importuje (java.util.Base64, java.util.Locale, java.net.URLEncoder,
// java.security.MessageDigest, java.text.Normalizer…), pro Kotlin na iPhonu. Jen v rozsahu, v jakém je logika používá.
@file:Suppress("PackageDirectoryMismatch", "UNUSED_PARAMETER")
package java.util

import kotlin.io.encoding.ExperimentalEncodingApi

class Locale private constructor(val tag: String) {
    companion object {
        val ROOT = Locale("")
        val US = Locale("en-US")
        val ENGLISH = Locale("en")
    }
}

@OptIn(ExperimentalEncodingApi::class)
object Base64 {
    class Encoder internal constructor(private val url: Boolean) {
        fun encodeToString(b: ByteArray): String =
            (if (url) kotlin.io.encoding.Base64.UrlSafe else kotlin.io.encoding.Base64.Default).encode(b)
        fun encode(b: ByteArray): ByteArray = encodeToString(b).encodeToByteArray()
    }
    class Decoder internal constructor(private val url: Boolean) {
        fun decode(s: String): ByteArray = try {
            (if (url) kotlin.io.encoding.Base64.UrlSafe else kotlin.io.encoding.Base64.Default).decode(s)
        } catch (e: IllegalArgumentException) { throw e } catch (e: Exception) { throw IllegalArgumentException(e.message) }
        fun decode(b: ByteArray): ByteArray = decode(b.decodeToString())
    }
    fun getEncoder() = Encoder(false)
    fun getDecoder() = Decoder(false)
    fun getUrlEncoder() = Encoder(true)
    fun getUrlDecoder() = Decoder(true)
}

open class Random(seed: Long? = null) {
    private val r = if (seed == null) kotlin.random.Random.Default else kotlin.random.Random(seed)
    open fun nextInt(bound: Int): Int = r.nextInt(bound)
    open fun nextInt(): Int = r.nextInt()
    open fun nextBytes(b: ByteArray) { r.nextBytes(b) }
}
