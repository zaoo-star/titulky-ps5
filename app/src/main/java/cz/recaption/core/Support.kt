package cz.recaption.core

/** Záznam posledních událostí – pro hlášení chyby. Drží jen posledních pár set řádků v paměti. */
object DiagLog {
    private const val MAX = 300
    private val lines = ArrayDeque<String>()

    /** Kolik řádků kdy přibylo (i těch, které už vypadly) – značka pro [since]. */
    private var added = 0L

    @Synchronized fun add(line: String, now: Long = System.currentTimeMillis()) {
        lines.addLast("${now / 1000 % 100000} $line".take(300))
        added++
        while (lines.size > MAX) lines.removeFirst()
    }

    /** Značka „teď“ pro [since] (záznam snímků: co přibylo od minulého snímku). */
    @Synchronized fun mark(): Long = added

    /** Řádky přidané od značky [mark] (ty, které ještě nevypadly), nejstarší první. */
    @Synchronized fun since(mark: Long): List<String> {
        val n = (added - mark).coerceIn(0L, lines.size.toLong()).toInt()
        return lines.toList().takeLast(n)
    }

    @Synchronized fun dump(): String = lines.joinToString("\n")
    @Synchronized fun size(): Int = lines.size
    @Synchronized fun clear() = lines.clear()
}

/** Kontrola nové verze: na serveru leží malý soubor s číslem a názvem nejnovější verze. */
object UpdateCheck {
    data class Remote(val code: Int, val name: String)

    /** Obsah souboru: "12 0.12" (číslo verze, název). Vrací null, když obsah nedává smysl. */
    fun parse(text: String?): Remote? {
        val parts = (text ?: "").trim().split(Regex("\\s+"))
        val code = parts.getOrNull(0)?.toIntOrNull() ?: return null
        if (code <= 0 || code > 1_000_000) return null
        return Remote(code, parts.getOrNull(1)?.take(20) ?: code.toString())
    }

    fun isNewer(remote: Remote?, currentCode: Int): Boolean = remote != null && remote.code > currentCode

    /**
     * Odkud se nová verze bere. Běžný kanál dostávají všichni; zkušební jen ten, kdo si ho
     * v Nastavení zapne (verze sestavené z rozpracované větve, ještě neschválené pro všechny).
     */
    enum class Channel(val tag: String) { STABLE("latest"), TEST("zkusebni") }

    /** Nabídka nové verze: co a ze kterého kanálu. */
    data class Offer(val remote: Remote, val channel: Channel)

    /** Od 1.00-rc2 přes naši adresu (Cloudflare přepošle soubor z vydání na GitHubu – `cloudflare/_worker.js`). */
    private const val BASE = "https://recaption.pages.dev/d/"

    /** Adresa souboru ve vydání daného kanálu (ReCaption.apk, version.txt). */
    fun url(channel: Channel, file: String): String = BASE + channel.tag + "/" + file

    /**
     * Která verze se má nabídnout. Zkušební jen tehdy, když je novější než běžná –
     * jakmile běžný kanál zkušební verzi dožene nebo předběhne, bere se zase běžný.
     */
    fun pick(stable: Remote?, test: Remote?, currentCode: Int): Offer? {
        val s = stable?.takeIf { isNewer(it, currentCode) }
        val t = test?.takeIf { isNewer(it, currentCode) }
        return when {
            t != null && (s == null || t.code > s.code) -> Offer(t, Channel.TEST)
            s != null -> Offer(s, Channel.STABLE)
            else -> null
        }
    }

    /**
     * Proč systém aktualizaci nenainstaloval → klíč textu pro uživatele.
     * Čísla jsou stavy z android.content.pm.PackageInstaller (3 zrušeno, 5 kolize, 6 místo).
     */
    fun problemKey(status: Int): String = when (status) {
        3 -> "upd_cancel"
        5 -> "upd_sig"
        6 -> "upd_space"
        else -> "upd_fail"
    }
}
