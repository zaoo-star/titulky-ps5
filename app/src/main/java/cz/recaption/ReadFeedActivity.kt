package cz.recaption

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import cz.recaption.core.I18n
import cz.recaption.core.ReadFeed
import cz.recaption.core.SpeakerColors
import cz.recaption.core.TextLogic

/**
 * Titulky ke čtení v telefonu (od 1.0.5, přání majitele – podle GlossaSubu, který má výpis titulků na hlavní stránce).
 * Jen telefon: celá obrazovka bez menu, pod sebou přeložené repliky ze hry v pořadí, jak se ukázaly v pruhu
 * ([ReadFeed]), jména mluvčích v barvách pruhu, nejnovější dole. Posouvá se sama dolů, když hráč neodscrolloval nahoru.
 * Originál menším šedým písmem jen se zapnutým Originálem v Titulkách. Displej nezhasíná (smyslem je číst);
 * ☾ = černý displej ([BlackScreenActivity]), klepnutím zpět sem. Obnova každých 500 ms, jen při změně [ReadFeed.version].
 */
class ReadFeedActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var ui: Ui
    private lateinit var scroll: ScrollView
    private lateinit var list: LinearLayout
    private lateinit var empty: TextView
    private var shownVersion = -1L
    /** Vykreslené repliky (id → položka a řádek), v pořadí. */
    private val views = LinkedHashMap<Long, Pair<ReadFeed.Entry, TextView>>()
    private var withOriginal = false
    /** Starší repliky načtené ze souboru tlačítkem „Starší titulky“ (nad aktuálními). */
    private lateinit var olderBox: LinearLayout
    private lateinit var olderBtn: TextView
    /** Nejstarší zobrazená replika (pro další „Starší titulky“). */
    private var oldestId = Long.MAX_VALUE
    /** Ve souboru už nic staršího není. */
    private var olderDone = false
    @Volatile private var loadingOlder = false

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 500)
        }
    }

    private fun t(key: String) = I18n.t(Prefs.uiLang(this), key)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ui = Ui(this)
        val root = ui.vbox().apply { setBackgroundColor(Theme.BG) }

        // od 1.0.01 čistá stránka jen s replikami – bez nadpisu a tlačítka ☾ (přání majitele), nejnovější nahoře
        list = ui.vbox().apply { setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(24)) }
        empty = ui.text(t("rf_empty"), 16f, Theme.MUTED)
        // historie: starší repliky ze souboru pod aktuálními (nejnovější nahoře)
        olderBtn = ui.smallButton("▼ " + t("rf_older")) { loadOlder() }
        olderBox = ui.vbox()
        // tlačítko je na konci seznamu: vidět, jen když hráč doscrolluje k nejstarším;
        // dokud text nepřeteče obrazovku, není vůbec (přání majitele) – viz [updateOlderBtn]
        olderBtn.visibility = View.GONE
        list.addView(empty, ui.lp(top = 8))
        list.addView(olderBox, ui.lp())
        list.addView(olderBtn, ui.lp(top = 14))
        scroll = ScrollView(this).apply { isFillViewport = true; addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        // od 1.0.02 (přání majitele): klepnutím se dole na chvíli ukážou tlačítka Zpět a ☾ (černý displej), jinak čistá stránka
        controls = ui.hbox().apply {
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(230, 0x1A, 0x1E, 0x25))
            setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(14))
            addView(ui.button("← " + t("wz_back")) { finish() }, LinearLayout.LayoutParams(0, ui.dp(52), 1f))
            addView(ui.button("☾︎  " + t("bs_btn")) {
                if (TranslatorService.running) startActivity(Intent(this@ReadFeedActivity, BlackScreenActivity::class.java))
            }, LinearLayout.LayoutParams(0, ui.dp(52), 1f).apply { leftMargin = ui.dp(12) })
            visibility = View.GONE
        }
        val frame = android.widget.FrameLayout(this).apply {
            addView(root)
            addView(controls, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }
        val taps = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: android.view.MotionEvent): Boolean { toggleControls(); return false }
        })
        scroll.setOnTouchListener { _, ev -> taps.onTouchEvent(ev); false }
        setContentView(frame)
    }

    /** Tlačítka dole (Zpět, ☾): klepnutí je ukáže / schová, samy zmizí po [CONTROLS_MS]. */
    private lateinit var controls: LinearLayout
    private val hideControls = Runnable { controls.visibility = View.GONE }

    private fun toggleControls() {
        handler.removeCallbacks(hideControls)
        if (controls.visibility == View.VISIBLE) { controls.visibility = View.GONE; return }
        controls.visibility = View.VISIBLE
        handler.postDelayed(hideControls, CONTROLS_MS)
    }

    override fun onResume() {
        super.onResume()
        // Originál se mohl mezitím zapnout / vypnout → vykreslit znovu
        val orig = Prefs.showOriginal(this)
        if (orig != withOriginal) {
            withOriginal = orig
            views.values.forEach { list.removeView(it.second) }; views.clear()
            olderBox.removeAllViews(); oldestId = Long.MAX_VALUE; olderDone = false
            shownVersion = -1
        }
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun refresh() {
        val v = ReadFeed.version
        if (v == shownVersion) return
        shownVersion = v
        // nahoře = nové repliky přibudou nahoře a jsou hned vidět; odscrollováno dolů = nechat, kde hráč čte
        val atTop = views.isEmpty() || scroll.scrollY <= ui.dp(48)
        val oldH = list.height
        val entries = ReadFeed.all()
        // repliky, které vypadly z paměti (nejvýš ReadFeed.MAX), zůstávají na stránce – hráč je může zrovna číst.
        // Přibyly starší (načtení ze souboru po spuštění aplikace) → vykreslit aktuální znovu, ať sedí pořadí.
        val newest = views.keys.maxOrNull() ?: Long.MIN_VALUE
        if (entries.any { it.id < newest && it.id !in views }) {
            views.values.forEach { list.removeView(it.second) }; views.clear()
        }
        for (e in entries) {
            val have = views[e.id]
            if (have != null) {
                // oprava téže repliky (varianta čtení, DeepL) – jen přepsat text
                if (have.first !== e) { have.second.text = format(e); have.second.bidiFor(have.second.text); views[e.id] = e to have.second }
                continue
            }
            val tv = ui.text(format(e), 20f, Theme.TEXT).apply { setLineSpacing(0f, 1.1f); bidiFor(text) }
            // nejnovější nahoře: novější než všechny ukázané → úplně nahoru, jinak pod novější (přepis po načtení ze souboru)
            val pos = views.keys.count { it > e.id }
            list.addView(tv, pos, ui.lp(top = 14))
            views[e.id] = e to tv
            if (e.id < oldestId) oldestId = e.id
        }
        empty.visibility = if (views.isEmpty() && olderBox.childCount == 0) View.VISIBLE else View.GONE
        // telefon vedle hry: dole = posouvá se samo; odscrollováno nahoru = stojí, dokud se hráč nevrátí dolů
        scroll.post {
            updateOlderBtn()
            if (atTop) scroll.scrollTo(0, 0) else scroll.scrollBy(0, list.height - oldH)   // čtené místo neuskočí
        }
    }

    /** „Starší titulky“ jen, když text přetéká obrazovku a ve souboru ještě něco staršího může být. */
    private fun updateOlderBtn() {
        val btnH = if (olderBtn.visibility == View.VISIBLE) olderBtn.height + ui.dp(4) else 0
        val overflow = list.height - btnH > scroll.height
        val want = overflow && !olderDone && !(views.isEmpty() && olderBox.childCount == 0)
        if (want == (olderBtn.visibility == View.VISIBLE)) return
        olderBtn.visibility = if (want) View.VISIBLE else View.GONE
    }

    /** Načíst starší repliky ze souboru (na pozadí) a přidat je nad ostatní; scroll zůstane u čteného místa. */
    private fun loadOlder() {
        if (loadingOlder) return
        loadingOlder = true
        val before = oldestId
        Thread({
            val got = ReadFeed.older(before)
            runOnUiThread {
                loadingOlder = false
                if (isFinishing) return@runOnUiThread
                if (got.isEmpty()) { olderDone = true; olderBtn.visibility = View.GONE; return@runOnUiThread }
                // starší pod dosud ukázané, nejnovější z nich první
                got.asReversed().forEach { e ->
                    olderBox.addView(ui.text(format(e), 20f, Theme.TEXT).apply { setLineSpacing(0f, 1.1f); bidiFor(text) }, ui.lp(top = 14))
                }
                oldestId = got.first().id
                if (got.size < 150) { olderDone = true; olderBtn.visibility = View.GONE }
                empty.visibility = View.GONE
            }
        }, "tp-readfeed-older").apply { isDaemon = true }.start()
    }

    /** Překlad po mluvčích jako v pruhu (jméno tučně v barvě pruhu), pod ním případně originál menším šedým písmem. */
    private fun format(e: ReadFeed.Entry): CharSequence {
        val sb = SpannableStringBuilder()
        e.segments.forEachIndexed { i, s ->
            if (i > 0) sb.append('\n')
            val start = sb.length
            if (s.choice) {
                sb.append(TextLogic.CHOICE_MARK)
                sb.setSpan(ForegroundColorSpan(SpeakerColors.PALETTE[0]), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (s.speaker != null) {
                sb.append(s.speaker).append(": ")
                e.colors[s.speaker]?.let { c ->
                    sb.setSpan(ForegroundColorSpan(c), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            sb.append(s.body)
        }
        if (withOriginal && e.original.isNotBlank()) {
            val start = sb.length
            TextLogic.markPrompt(TextLogic.segments(e.original)).filter { !it.prompt }.forEach { s ->
                sb.append('\n')
                s.speaker?.let { sb.append(TextLogic.normalizeName(it)).append(": ") }
                sb.append(s.body)
            }
            if (sb.length > start) {
                sb.setSpan(ForegroundColorSpan(ORIGINAL), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(RelativeSizeSpan(0.7f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }

    private companion object {
        val ORIGINAL = Color.rgb(0xA8, 0xB0, 0xB8)
        const val CONTROLS_MS = 4000L
    }
}
