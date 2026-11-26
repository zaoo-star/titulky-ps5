package cz.recaption

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import cz.recaption.core.DiagLog
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Titulky PS5 – předčítání překladu z PC (přání majitele 26. 11. 2026: „hlasový překlad“, sluchátka Bluetooth k TV).
 * Zapíná se jen na PC (Titulky → Pruh s překladem → Předčítat překlad v TV, výchozí vypnuto); TV čte hlasem systému
 * (převod textu na řeč v Android TV). Hlas jde tam, kam zvuk TV – do repráků i do sluchátek připojených k TV.
 *
 * Fronta: věta se přidá za tu, která se právě čte; čekají-li už dvě, fronta se zahodí a čte se jen nejnovější
 * (rychlý rozhovor by se jinak opožďoval čím dál víc).
 */
class PcSpeech(context: Context) {
    private val app = context.applicationContext
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var lang: String? = null
    private val waiting = AtomicInteger(0)
    private var next = 0L
    private var failed = false
    @Volatile private var pending: Triple<String, String?, Int>? = null

    private fun log(m: String) { try { DiagLog.add("HLAS|$m") } catch (_: Throwable) {} }

    private fun ensure() {
        if (tts != null || failed) return
        tts = try {
            TextToSpeech(app) { status ->
                ready = status == TextToSpeech.SUCCESS
                log(if (ready) "pripraveno" else "nejde ($status)")
                if (!ready) failed = true
                // věta, která přišla, než byl hlas připravený
                if (ready && tts != null) pending?.let { (text, code, rate) -> pending = null; say(text, code, rate) }
            }.apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { waiting.updateAndGet { maxOf(0, it - 1) } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { waiting.updateAndGet { maxOf(0, it - 1) } }
                    override fun onStop(id: String?, interrupted: Boolean) { waiting.updateAndGet { maxOf(0, it - 1) } }
                })
            }
        } catch (e: Throwable) { log("nejde (${e.javaClass.simpleName})"); failed = true; null }
    }

    /** Přečte [text] jazykem [code] („cs“…) rychlostí [ratePercent] (100 = běžně). */
    fun say(text: String, code: String?, ratePercent: Int) {
        if (text.isBlank() || ratePercent <= 0) return
        ensure()
        val t = tts ?: return
        if (!ready) { pending = Triple(text, code, ratePercent); return }  // hlas se teprve připravuje
        val c = code ?: "cs"
        if (c != lang) {
            val r = t.setLanguage(Locale(c))
            lang = c
            log("jazyk $c · " + when (r) {
                TextToSpeech.LANG_MISSING_DATA -> "chybí hlas (stáhnout v Nastavení TV → Převod textu na řeč)"
                TextToSpeech.LANG_NOT_SUPPORTED -> "hlas pro jazyk není"
                else -> "ok"
            })
        }
        t.setSpeechRate(ratePercent / 100f)
        val mode = if (waiting.get() >= 2) { waiting.set(0); TextToSpeech.QUEUE_FLUSH } else TextToSpeech.QUEUE_ADD
        waiting.incrementAndGet()
        t.speak(text, mode, Bundle(), "v" + (next++))
    }

    /** Zmlknout (spojení s PC skončilo). */
    fun stop() { try { tts?.stop() } catch (_: Throwable) {}; waiting.set(0) }

    fun shutdown() {
        try { tts?.shutdown() } catch (_: Throwable) {}
        tts = null; ready = false; lang = null; pending = null; waiting.set(0)
    }
}
