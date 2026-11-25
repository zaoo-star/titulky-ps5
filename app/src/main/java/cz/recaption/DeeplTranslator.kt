package cz.recaption

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import cz.recaption.core.Deepl
import cz.recaption.core.DeeplBackoff
import cz.recaption.core.DeeplStats
import cz.recaption.core.Glossary
import cz.recaption.core.Lang
import cz.recaption.core.Natural
import cz.recaption.core.QuickWords
import cz.recaption.core.Segment
import cz.recaption.core.SourceLang
import cz.recaption.core.TextLogic
import cz.recaption.core.TranslationCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.net.ssl.HttpsURLConnection

/**
 * POKUS: klíč DeepL hráče a co si o něm aplikace pamatuje. Klíč je jen zašifrovaný klíčem
 * z Android Keystore (stejně jako klíče televize LG v [LgVault]) a nikam se nevypisuje.
 */
object DeeplKey {
    private const val ALIAS = "recaption-deepl-v1"
    private const val AAD = "deepl:key"
    private val LOCK = Any()

    /** V telefonu v režimu „Nastavuji TV“ ([RemoteClient]) zvlášť – klíč telefonu jako čtečky zůstává. */
    private fun p(c: Context) = c.applicationContext.getSharedPreferences(
        if (RemoteClient.on(c)) RemoteClient.DEEPL_FILE else "deepl", Context.MODE_PRIVATE)

    /** Je pokus zapnutý? (výchozí vypnuto) */
    fun enabled(c: Context) = p(c).getBoolean("on", false)
    fun setEnabled(c: Context, v: Boolean) {
        p(c).edit().putBoolean("on", v).apply()
        if (RemoteClient.on(c)) RemoteClient.deeplOn(v)   // režim TV: zapnout / vypnout i v TV
    }

    fun get(c: Context): String? = p(c).getString("key", null)?.let {
        runCatching { String(LgVault.open(secret(), AAD, it), Charsets.UTF_8) }.getOrNull()
    }

    /** Klíč je uložený; v režimu TV i tehdy, když ho má jen TV (klíč z TV se do telefonu neposílá). */
    fun has(c: Context) = p(c).contains("key") || (RemoteClient.on(c) && RemoteClient.tvHasDeeplKey)

    /** Režim TV: klíč má jen TV, telefon ho nezná (ukazuje se „Klíč DeepL je v TV“). */
    fun onlyInTv(c: Context) = RemoteClient.on(c) && RemoteClient.tvHasDeeplKey && !p(c).contains("key")

    fun save(c: Context, key: String) {
        p(c).edit().putString("key", LgVault.seal(secret(), AAD, key.toByteArray(Charsets.UTF_8)))
            .remove("count").remove("limit").remove("problem").apply()
        DeeplTranslator.newKey()
        if (RemoteClient.on(c)) RemoteClient.deeplKey(key)   // režim TV: klíč do TV (ověří si ho a uloží zašifrovaný)
    }

    fun remove(c: Context) {
        p(c).edit().remove("key").remove("count").remove("limit").remove("problem").putBoolean("on", false).apply()
        DeeplTranslator.newKey()
        if (RemoteClient.on(c)) RemoteClient.deeplOn(false)   // režim TV: v TV klíč zůstane, DeepL se vypne
    }

    /** Výsledek posledního ověření klíče (zbývající znaky, nebo důvod, proč nešel). */
    fun saveCheck(c: Context, r: DeeplTranslator.Check) {
        val e = p(c).edit()
        r.usage?.let { e.putLong("count", it.count).putLong("limit", it.limit) }
        if (r.problem != null) e.putString("problem", r.problem.name) else e.remove("problem")
        e.apply()
    }

    fun lastUsage(c: Context): Deepl.Usage? =
        if (p(c).contains("limit")) Deepl.Usage(p(c).getLong("count", 0), p(c).getLong("limit", 0)) else null

    fun lastProblem(c: Context): Deepl.Problem? =
        p(c).getString("problem", null)?.let { n -> Deepl.Problem.values().firstOrNull { it.name == n } }

    private fun secret(): SecretKey = synchronized(LOCK) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }
}

/**
 * POKUS: překlad titulků přes DeepL vedle offline překladu (viz [Deepl]). Služba ukáže offline
 * překlad hned a tímhle se souběžně zeptá DeepL; jestli text v pruhu vyměnit, rozhoduje
 * [Deepl.decide]. Výsledky DeepL mají vlastní paměť (offline paměť překladů se nemíchá).
 */
object DeeplTranslator {
    @Volatile var backoff = DeeplBackoff()
        private set
    /** Měření tohoto běhu aplikace. */
    val stats = DeeplStats()
    /** Paměť výsledků podle dvojice jazyků („en>cs“). */
    private val caches = ConcurrentHashMap<String, TranslationCache>()
    private val running = AtomicInteger(0)

    /** Nový nebo odebraný klíč: dřívější chyby (např. odmítnutý klíč) už neplatí. */
    fun newKey() { backoff = DeeplBackoff() }

    /**
     * Jen ke zobrazení („DeepL online + offline“): zapnuto, jazyk DeepL umí a klíč je uložený. Klíč se
     * nedešifruje (Keystore) – Domů a stav pro TV se ptají často, služba si klíč bere jednou přes [activeKey].
     */
    fun active(c: Context): Boolean = DeeplKey.enabled(c) && Deepl.target(Prefs.lang(c)) != null && DeeplKey.has(c)

    /** Klíč, se kterým má služba DeepL používat; null = pokus vypnutý, chybí klíč, jazyk DeepL neumí. */
    fun activeKey(c: Context): String? {
        if (!DeeplKey.enabled(c) || Deepl.target(Prefs.lang(c)) == null) return null
        return DeeplKey.get(c)
    }

    private fun cache(s: SourceLang, l: Lang) = caches.getOrPut(s.code + ">" + l.code) { TranslationCache(1500) }

    /** Co se z titulku pošle do DeepL: části (index segmentu), jejich anglický text a text pro DeepL. */
    class Job(val key: String, val lang: Lang, val index: List<Int>, val bodies: List<String>, val sources: List<String>,
              val glossary: Glossary? = null, val source: SourceLang = SourceLang.EN)

    /**
     * Rozdělí titulek na části stejně jako offline překlad ([TranslatorService.translateSegments]).
     * Jména mluvčích a odrážky voleb zůstávají; krátká slova ze slovníčku a ručně zadané věty
     * zůstanou z offline překladu (ty jsou přesné). null = nic k poslání.
     */
    fun plan(key: String, lang: Lang, text: String, glossary: Glossary?, source: SourceLang = SourceLang.EN): Job? {
        if (Deepl.target(lang) == null) return null
        val en = source.english
        val idx = ArrayList<Int>(); val bodies = ArrayList<String>(); val src = ArrayList<String>()
        TextLogic.segments(text).forEachIndexed { i, s ->
            val body = TextLogic.normalizeCase(s.body, en)
            // rychlá slovíčka jsou anglická – u jiného jazyka titulků se nepoužijí
            if (en && QuickWords.lookup(lang.code, body) != null) return@forEachIndexed
            val input = TextLogic.forTranslator(body)
            if (glossary?.sentence(input) != null) return@forEachIndexed
            idx += i; bodies += body; src += Deepl.source(input, glossary?.prepare(input, en))
        }
        return if (idx.isEmpty()) null else Job(key, lang, idx, bodies, src, glossary, source)
    }

    /** Překlady všech částí z paměti DeepL; null = aspoň jedna tam není. */
    fun cached(job: Job): List<String>? {
        val c = cache(job.source, job.lang)
        return job.sources.map { c.get(it) ?: return null }
    }

    /**
     * Offline segmenty s těly z DeepL. U češtiny se oslovení dál sjednocuje na tykání ([Natural]):
     * DeepL v češtině tykání nastavit neumí (`formality` pro ni nemá). Úpravy obratů (`IDIOMS`)
     * se nedělají – ty jsou jen pro offline překladač a DeepL dostává původní anglický text.
     * [speaker] = mluvčí pro části bez jména (zpráva v telefonu od kontaktu) – jen pro rod.
     */
    fun merge(offline: List<Segment>, job: Job, texts: List<String>, speaker: String? = null): List<Segment> {
        val out = offline.toMutableList()
        job.index.forEachIndexed { k, i ->
            if (i !in out.indices) return@forEachIndexed
            val body = job.bodies[k]
            // tykání podle anglického originálu – u jiného jazyka titulků se nemění nic
            val natural = if (job.source.english) Natural.fix(job.lang, body, texts[k]) else texts[k]
            // rod postav: DeepL mluvčího nevidí (posílá se jen text repliky)
            val tr = job.glossary?.genders(job.lang, body, natural, out[i].speaker ?: speaker, job.source.english) ?: natural
            out[i] = out[i].copy(body = TextLogic.finishTranslation(body, tr))
        }
        return out
    }

    class Fetched(val texts: List<String>?, val problem: Deepl.Problem?, val ms: Long, val chars: Int)

    /** Najednou nanejvýš 3 dotazy; víc znamená, že titulky běží rychleji, než DeepL odpovídá. */
    fun tryStart(now: Long = SystemClock.uptimeMillis()): Boolean {
        if (!backoff.allowed(now)) return false
        if (running.incrementAndGet() > 3) { running.decrementAndGet(); return false }
        return true
    }

    /** Dotaz na DeepL (volat jen po úspěšném [tryStart]). */
    suspend fun fetch(job: Job): Fetched = withContext(Dispatchers.IO) {
        val t0 = SystemClock.uptimeMillis()
        val b = backoff
        try {
            val (status, body) = http("POST", Deepl.translateUrl(job.key), job.key,
                Deepl.requestJson(job.sources, Deepl.target(job.lang) ?: "CS", job.source.deepl), 2000, 3500)
            val ms = SystemClock.uptimeMillis() - t0
            val p = Deepl.problemFor(status)
            val texts = if (p == null) Deepl.parseTranslations(body, job.sources.size) else null
            if (texts == null) {
                val problem = p ?: Deepl.Problem.BAD_REPLY
                b.onProblem(problem, SystemClock.uptimeMillis()); stats.failure(0)
                Fetched(null, problem, ms, 0)
            } else {
                b.onSuccess()
                val c = cache(job.source, job.lang)
                job.sources.forEachIndexed { i, s -> c.put(s, texts[i]) }
                Fetched(texts, null, ms, Deepl.billedChars(job.sources))
            }
        } catch (_: Exception) {
            // výjimku nevypisovat: nic v ní není potřeba a nemá se nikam dostat ani omylem
            b.onProblem(Deepl.Problem.NETWORK, SystemClock.uptimeMillis()); stats.failure(0)
            Fetched(null, Deepl.Problem.NETWORK, SystemClock.uptimeMillis() - t0, 0)
        } finally { running.decrementAndGet() }
    }

    class Check(val usage: Deepl.Usage?, val problem: Deepl.Problem?)

    /** Ověření klíče dotazem na zbývající znaky (/v2/usage). Volat mimo hlavní vlákno. */
    fun check(key: String): Check = try {
        val (status, body) = http("GET", Deepl.usageUrl(key), key, null, 5000, 8000)
        val p = Deepl.problemFor(status)
        val u = if (p == null) Deepl.parseUsage(body) else null
        // 456 = klíč platí, jen je vyčerpaný limit
        Check(u, if (u == null) p ?: Deepl.Problem.BAD_REPLY else null)
    } catch (_: Exception) { Check(null, Deepl.Problem.NETWORK) }

    private fun http(method: String, url: String, key: String, json: String?, connectMs: Int, readMs: Int): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpsURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = connectMs
            c.readTimeout = readMs
            c.useCaches = false
            c.setRequestProperty("Authorization", Deepl.authHeader(key))
            c.setRequestProperty("User-Agent", "ReCaption")
            if (json != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            val status = c.responseCode
            val stream = if (status in 200..299) c.inputStream else c.errorStream
            val text = stream?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
            return status to text
        } finally { c.disconnect() }
    }

    /** Řádek pro hlášení o stavu (bez klíče a bez textu titulků). */
    fun diagLine(c: Context): String {
        if (!DeeplKey.has(c)) return "DeepL: bez klíče, " + (if (DeeplKey.enabled(c)) "zapnuto" else "vypnuto")
        val now = SystemClock.uptimeMillis()
        val b = backoff
        val s = stats
        val u = DeeplKey.lastUsage(c)
        return "DeepL: " + (if (DeeplKey.enabled(c)) "zapnuto" else "vypnuto") +
            ", ověření: " + (DeeplKey.lastProblem(c)?.name ?: u?.let { "zbývá ${it.left}/${it.limit}" } ?: "?") +
            ", teď: " + (b.lastProblem?.name ?: "ok") + (b.minutesLeft(now).let { if (it != 0L) ", pauza $it min" else "" }) +
            ", dotazů ${s.requests}, ${s.avgMs} ms, vylepšeno ${s.improved}, pozdě ${s.late}, beze změny ${s.same}," +
            " z paměti ${s.cached}, chyb ${s.failed}, znaků ${s.chars}"
    }
}
