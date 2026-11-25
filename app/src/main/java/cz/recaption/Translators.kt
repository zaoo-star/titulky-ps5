package cz.recaption

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import cz.recaption.core.Glossary
import cz.recaption.core.Lang
import cz.recaption.core.Natural
import cz.recaption.core.OfflinePrep
import cz.recaption.core.QuickWords
import cz.recaption.core.SourceLang
import cz.recaption.core.TextLogic
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Offline překlad Google ML Kit (angličtina → čeština, slovenština, polština, němčina…).
 * Zdarma a bez internetu. Jazykový model (cca 30 MB) se stáhne jen jednou při prvním použití.
 * Jazyk titulků je [source] (volba „Překládat z“, výchozí angličtina); u jiného jazyka než
 * angličtiny ML Kit stáhne i jeho balíček – `ensureModel` je pro celou dvojici.
 */
object OfflineTranslator {
    /** Z čeho se překládá; nastavuje ho [Prefs] (při spuštění aplikace a po změně volby). */
    @Volatile var source: SourceLang = SourceLang.EN

    private val clients = ConcurrentHashMap<String, Translator>()
    private val ready = ConcurrentHashMap<String, Boolean>()

    /** Dvojice jazyků („en>cs“) – podle ní se drží překladač, připravenost i stahování. */
    private fun pair(lang: Lang) = source.code + ">" + lang.code

    private fun client(lang: Lang): Translator {
        val s = source
        return clients.getOrPut(s.code + ">" + lang.code) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(s.code)
                    // kód ML Kit (srbština latinkou jede přes chorvatský model – Lang.mlkit)
                    .setTargetLanguage(lang.mlkit)
                    .build()
            )
        }
    }

    fun isReady(lang: Lang): Boolean = ready[pair(lang)] == true

    /** Dvojice jazyků, jejichž balíček se právě stahuje (pro ukazatel postupu; ML Kit procenta neříká). */
    private val downloading = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun isDownloading(lang: Lang): Boolean = pair(lang) in downloading

    fun ensureModel(lang: Lang, callback: (Boolean) -> Unit) {
        val key = pair(lang)
        try {
            downloading.add(key)
            client(lang).downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener { downloading.remove(key); ready[key] = true; callback(true) }
                .addOnFailureListener { e -> downloading.remove(key); Log.w("TestPreklad", "model", e); callback(false) }
        } catch (e: Exception) {
            downloading.remove(key); Log.w("TestPreklad", "model", e); callback(false)
        }
    }

    /** Surový překlad jednoho textu. */
    suspend fun translateRaw(lang: Lang, text: String): String = client(lang).translate(text).await()

    /**
     * Načte jazykový model do paměti předem. Bez toho by ho načítal až první titulek ve hře –
     * a ten by se objevil později než ostatní.
     * Vrací, jak dlouho trval první (studený) a druhý (už připravený) překlad v ms; null = nepovedlo se.
     */
    suspend fun warmUp(lang: Lang): Pair<Long, Long>? = try {
        val t0 = android.os.SystemClock.uptimeMillis()
        translateRaw(lang, "Hello.")
        val t1 = android.os.SystemClock.uptimeMillis()
        translateRaw(lang, "Wait here.")
        (t1 - t0) to (android.os.SystemClock.uptimeMillis() - t1)
    } catch (e: Exception) { Log.w("TestPreklad", "warm-up", e); null }

    /**
     * Překlad s úpravami pro hry: slang → spisovná angličtina, jména postav a míst se nepřekládají.
     * Když se značky jmen při překladu poškodí, přeloží se text bez nich (nic se neztratí).
     */
    suspend fun translate(lang: Lang, text: String, glossary: Glossary? = null): String {
        glossary?.sentence(text)?.let { return it }      // ručně zadaný překlad celé věty
        val en = source.english
        val p = glossary?.prepare(text, en) ?: OfflinePrep.prepare(text, en)
        if (p.names.isNotEmpty()) {
            OfflinePrep.finish(p, translateRaw(lang, p.protectedText))?.let { return natural(lang, text, it) }
        }
        return natural(lang, text, translateRaw(lang, p.plain))
    }

    /**
     * Doladění překladu, aby zněl přirozeně (čeština a slovenština: sjednocení oslovení na tykání). Čte anglický
     * originál (oslovení více lidí) – u jiného jazyka titulků se nemění nic.
     */
    private fun natural(lang: Lang, original: String, translated: String): String =
        if (source.english) Natural.fix(lang, original, translated) else translated

    /**
     * Celý titulek tak, jak ho překládá služba: po řádcích, jména mluvčích zůstávají, krátká slova
     * ze slovníčku. Používá ho zkouška překladu (služba dělá totéž, jen s pamětí překladů).
     */
    suspend fun translateSubtitle(lang: Lang, text: String, glossary: Glossary? = null): String {
        val out = ArrayList<String>()
        val en = source.english
        for (s in TextLogic.segments(text)) {
            val body = TextLogic.normalizeCase(s.body, en)
            val raw = (if (en) QuickWords.lookup(lang.code, body) else null) ?: translate(lang, TextLogic.forTranslator(body), glossary)
            val tr = glossary?.genders(lang, body, raw, s.speaker, en) ?: raw
            out.add((s.speaker?.let { TextLogic.normalizeName(it) + ": " } ?: "") + TextLogic.finishTranslation(body, tr))
        }
        return out.joinToString(" ")
    }
}
