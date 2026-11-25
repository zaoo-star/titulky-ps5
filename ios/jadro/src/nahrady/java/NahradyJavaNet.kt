@file:Suppress("PackageDirectoryMismatch", "UNUSED_PARAMETER")
package java.net

/** Jako java.net.URLEncoder v UTF-8 (mezera → +, ostatní mimo A–Z a–z 0–9 . - * _ → %XX). */
object URLEncoder {
    fun encode(s: String, enc: String): String {
        val sb = StringBuilder()
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            when {
                c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c == '.'.code || c == '-'.code ||
                    c == '*'.code || c == '_'.code -> sb.append(c.toChar())
                c == ' '.code -> sb.append('+')
                else -> sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
            }
        }
        return sb.toString()
    }
}

object URLDecoder {
    fun decode(s: String, enc: String): String {
        val out = ArrayList<Byte>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '+' -> { out.add(' '.code.toByte()); i++ }
                c == '%' -> {
                    require(i + 3 <= s.length) { "URLDecoder: neúplné %" }
                    out.add(s.substring(i + 1, i + 3).toInt(16).toByte()); i += 3
                }
                else -> { for (b in c.toString().encodeToByteArray()) out.add(b); i++ }
            }
        }
        return out.toByteArray().decodeToString()
    }
}
