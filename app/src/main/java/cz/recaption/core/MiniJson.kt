package cz.recaption.core

/**
 * Malé čtení JSON bez knihoven (core se překládá i mimo Android). Vrací Map, List, String,
 * Double/Long, Boolean nebo null. Neplatný text = IllegalArgumentException.
 */
object MiniJson {
    fun parse(text: String): Any? {
        val p = P(text)
        val v = p.value()
        p.ws()
        require(p.i == text.length) { "JSON: text navíc" }
        return v
    }

    /** Objekt nebo null (když to objekt není nebo text není JSON). */
    @Suppress("UNCHECKED_CAST")
    fun obj(text: String): Map<String, Any?>? = runCatching { parse(text) as? Map<String, Any?> }.getOrNull()

    private class P(val s: String) {
        var i = 0
        var depth = 0
        fun ws() { while (i < s.length && s[i] in " \t\r\n") i++ }
        fun value(): Any? {
            ws()
            require(i < s.length) { "JSON: konec" }
            return when (s[i]) {
                '{' -> nested { obj() }
                '[' -> nested { arr() }
                '"' -> str()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> num()
            }
        }
        private fun <T> nested(f: () -> T): T {
            require(++depth <= 32) { "JSON: příliš hluboko" }
            try { return f() } finally { depth-- }
        }
        private fun word(w: String, v: Any?): Any? {
            require(s.startsWith(w, i)) { "JSON: $w" }
            i += w.length
            return v
        }
        private fun num(): Any {
            val m = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?").find(s, i)
            require(m != null && m.range.first == i) { "JSON: číslo" }
            i = m.range.last + 1
            return if (m.groupValues[2].isEmpty() && m.groupValues[3].isEmpty()) m.value.toLongOrNull() ?: m.value.toDouble() else m.value.toDouble()
        }
        private fun str(): String {
            i++
            val b = StringBuilder()
            while (true) {
                require(i < s.length) { "JSON: neukončený text" }
                val c = s[i++]
                when {
                    c == '"' -> return b.toString()
                    c == '\\' -> {
                        require(i < s.length)
                        when (val e = s[i++]) {
                            'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> b.append('\r')
                            'b' -> b.append('\b'); 'f' -> b.append('\u000c')
                            'u' -> { require(i + 4 <= s.length); b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> b.append(e)
                        }
                    }
                    else -> b.append(c)
                }
            }
        }
        private fun obj(): Map<String, Any?> {
            i++
            val m = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                ws()
                require(i < s.length && s[i] == '"') { "JSON: klíč" }
                val k = str()
                ws()
                require(i < s.length && s[i] == ':') { "JSON: dvojtečka" }
                i++
                m[k] = value()
                ws()
                require(i < s.length) { "JSON: konec objektu" }
                if (s[i] == ',') { i++; continue }
                require(s[i] == '}') { "JSON: konec objektu" }
                i++
                return m
            }
        }
        private fun arr(): List<Any?> {
            i++
            val l = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                l.add(value())
                ws()
                require(i < s.length) { "JSON: konec pole" }
                if (s[i] == ',') { i++; continue }
                require(s[i] == ']') { "JSON: konec pole" }
                i++
                return l
            }
        }
    }
}
