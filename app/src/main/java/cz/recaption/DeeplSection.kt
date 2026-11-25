package cz.recaption

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import cz.recaption.core.Deepl
import cz.recaption.core.DeeplPage
import cz.recaption.core.LoginServer
import cz.recaption.core.PhoneLogin

/**
 * Titulky → Kvalita překladu (od 0.97.28, dřív Nastavení → Experimentální) a krok průvodce „Lepší překlad (DeepL)“.
 * Zapnutí, klíč hráče (políčko, nebo vložení z telefonu přes stránku z televize – stejný server
 * jako přihlášení, viz [LoginServer.forPage]), ověření klíče a měření tohoto běhu.
 * Stránka pro telefon běží jen, dokud je na obrazovce její čtvereček.
 */
class DeeplSection(
    private val a: Activity,
    private val ui: Ui,
    private val applyToService: () -> Unit,
    private val rerender: () -> Unit,
    /** Klíč přijatý a ověřený bez potíží (průvodce pak sám pokračuje dalším krokem). */
    private val onAccepted: () -> Unit = {},
) {
    private var draft = ""
    private var busy = false
    private var msg: String? = null
    private var msgColor = Theme.MUTED
    private var page: LoginServer? = null
    /** Uložený a funkční klíč je schovaný; „Změnit klíč“ ukáže zadávání znovu. */
    private var changing = false
    /** Hráč zavřel čtvereček a chce klíč psát ručně – stránku pro telefon už sama neotvírat. */
    private var manual = false
    private val main = Handler(Looper.getMainLooper())

    private fun t(k: String) = a.tx(k)


    /**
     * [wizard] = krok průvodce „Lepší překlad (DeepL)“: bez přepínače, soukromí a měření (to je v Titulkách →
     * Kvalita překladu), jen cesta ke klíči a po jeho přijetí „✔ DeepL je nastavený“.
     */
    fun build(content: LinearLayout, wizard: Boolean = false) {
        fun add(v: View, top: Int) = content.addView(v, ui.lp(top = top))
        val has = DeeplKey.has(a)
        val on = has && DeeplKey.enabled(a)
        // poslední ověření klíče (nebo jeho problém) – řádek pod klíčem; výsledek akce jen když neříká totéž
        val p = if (has) DeeplKey.lastProblem(a) else null
        val u = if (has) DeeplKey.lastUsage(a) else null
        val shown: String? = if (!has) null else if (p != null) problem(p) else u?.let { usage(it) }
        // výsledek akce (ověření klíče, chyba tvaru, „nejdřív klíč“…) jako první věc sekce, ne pod políčkem a tlačítky
        // (přání majitele 22. 11. 2026); v průvodci až pod „✔ DeepL je nastavený“
        fun result() = msg?.takeIf { it != shown }?.let { m ->
            val res = msgColor != Theme.MUTED
            add(ui.text(m, if (res) 16f else 15f, msgColor, bold = res), if (wizard) 12 else 6)
        }
        if (!wizard) result()
        if (!wizard) {
            add(ui.settingRow(t("dl_toggle"), t(if (on) "on" else "off")) {
                if (!has) note(t("dl_need_key"), Theme.WARN)
                else { DeeplKey.setEnabled(a, !on); applyToService() }
                rerender()
            }.apply { tag = "deepl" }, 6)
            add(ui.text(t("dl_privacy"), 14f, Theme.WARN), 6)
        }

        // klíč: uložený (jen začátek a konec) a výsledek posledního ověření
        val key = if (has) DeeplKey.get(a) else null
        // telefon v režimu „Nastavuji TV“: klíč může mít jen TV (do telefonu se neposílá)
        val inTv = DeeplKey.onlyInTv(a)
        if (wizard && (key != null || inTv)) add(ui.text("✔ " + t("wz_tr_ok"), 18f, Theme.OK, bold = true).apply { tag = "dlok" }, 14)
        if (wizard && !inTv) result()
        if (!wizard) add(ui.text(if (key != null) t("dl_key_saved").replace("%s", Deepl.masked(key)) else if (inTv) t("rs_dl_tv") else t("dl_key_none"), 15f, Theme.TEXT), 10)
        if (wizard && inTv) return
        if (has) {
            if (p != null) add(ui.text(problem(p), 14f, if (p == Deepl.Problem.BAD_KEY) Theme.BAD else Theme.WARN), 4)
            else if (u != null) add(ui.text(usage(u), 14f, Theme.OK), 4)
        }
        // DeepL za běhu: proč se teď nepoužívá a měření
        val b = DeeplTranslator.backoff
        val left = b.minutesLeft(SystemClock.uptimeMillis())
        if (!wizard) b.lastProblem?.takeIf { on }?.let { bp ->
            add(ui.text(problem(bp) + " " + when {
                left < 0 -> t("dl_paused_key")
                left > 0 -> t("dl_paused").replace("%s", left.toString())
                else -> ""
            }, 14f, Theme.WARN), 4)
        }
        val s = DeeplTranslator.stats
        if (!wizard && s.requests + s.cached + s.failed > 0) add(ui.text(t("dl_stats")
            .replace("%1\$s", s.requests.toString()).replace("%2\$s", s.avgMs.toString())
            .replace("%3\$s", s.improved.toString()).replace("%4\$s", s.late.toString())
            .replace("%5\$s", s.same.toString()).replace("%6\$s", s.chars.toString()), 14f, Theme.MUTED), 6)

        // funkční uložený klíč: zadávání schované, jen Změnit / Ověřit / Odebrat (v průvodci nic – stačí Pokračovat)
        if (key != null && DeeplKey.lastProblem(a) == null && !changing) {
            if (wizard) return
            val row0 = row()
            row0.addView(ui.button(t("dl_change")) { changing = true; rerender() }.apply { tag = "dlchange" })
            row0.addView(ui.button(t("dl_key_check")) { if (!busy) verify(key, fromField = false) }.apply { tag = "dlcheck" }, gap())
            row0.addView(ui.button(t("dl_key_remove")) {
                DeeplKey.remove(a); msg = null; applyToService(); rerender()
            }.apply { tag = "dlremove" }, gap())
            add(row0, 10)
            return
        }
        if (keySteps(content)) return
        if (key != null && !wizard) {
            val row2 = row()
            row2.addView(ui.button(t("dl_key_check")) { if (!busy) verify(key, fromField = false) }.apply { tag = "dlcheck" })
            row2.addView(ui.button(t("dl_key_remove")) {
                DeeplKey.remove(a); msg = null; applyToService(); rerender()
            }.apply { tag = "dlremove" }, gap())
            add(row2, 8)
        }
    }

    /**
     * Cesta ke klíči ve třech krocích (od 0.97.28): 1. účet DeepL API (plán Developer, zdarma), 2. stránka s klíči
     * (u klíče ikona kopírování), 3. vložit klíč. Televize: jen jeden čtvereček – na stránku z televize
     * ([DeeplPage]), která v telefonu provede všemi třemi kroky i s vysvětlením (dlouhý klíč se ovladačem psát nedá);
     * vedle něj krátce kroky. Dřív tři čtverečky vedle sebe a dlouhý odstavec – majitel nevěděl, k čemu který je.
     * Telefon: tlačítka a políčko.
     * Vrací true, když se čeká na klíč z telefonu (ruční políčko se pak neukazuje).
     */
    private fun keySteps(content: LinearLayout): Boolean {
        fun add(v: View, top: Int) = content.addView(v, ui.lp(top = top))
        if (Prefs.isTv(a)) {
            if (!manual && page == null) openPage(redraw = false)
            val url = pageUrl()
            if (url != null) {
                // vlevo čtvereček, vpravo kroky a čekání – celé i s tlačítky průvodce na jednu obrazovku
                add(phoneQr(url), 12)
                content.addView(ui.smallButton(t("dl_manual")) { manual = true; stopPage(); rerender() }.apply { tag = "dlqrclose" },
                    ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT, top = 12))
                return true
            }
            // stránka běží, ale zařízení nemá adresu v domácí síti (nepovedené spuštění hlásí už openPage)
            if (page != null && msg == null) add(ui.text(t("dl_qr_nolan"), 14f, Theme.WARN), 8)
            // ručně (od 1.00): stránka DeepL API čtverečkem do telefonu, ať se adresa nepíše ovladačem; klíč se pak opíše sem
            add(signupQr(), 10)
        } else {
            // telefon / tablet: DeepL se otevře v prohlížeči, klíč se tam zkopíruje a vloží do políčka níž
            add(ui.text(t("dl_s1"), 16f, Theme.TEXT, bold = true), 12)
            add(ui.button(t("dl_open")) { open(Deepl.SIGNUP_URL) }.apply { tag = "dlopen" }, 6)
            add(ui.text(t("dl_howto"), 14f, Theme.MUTED), 6)
            add(ui.text(t("dl_s2"), 16f, Theme.TEXT, bold = true), 14)
            add(ui.button(t("dl_keys_open")) { open(Deepl.KEYS_URL) }.apply { tag = "dlkeys" }, 6)
            add(ui.text(t("dl_keys_hint"), 14f, Theme.MUTED), 6)
            add(ui.text(t("dl_s3_here"), 16f, Theme.TEXT, bold = true), 14)
        }
        add(EditText(a).apply {
            setText(draft)
            hint = t("dl_key_hint")
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(Theme.TEXT)
            setHintTextColor(Theme.MUTED)
            textSize = 16f
            background = ui.focusBg(Theme.CARD)
            setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12))
            tag = "dlkey"
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(e: Editable?) { draft = e?.toString() ?: "" }
            })
        }, 8)
        val row = row()
        row.addView(ui.button(t(if (busy) "dl_checking" else "dl_key_save"), primary = true) { if (!busy) saveTyped() }.apply { tag = "dlsave" })
        // televize: zpět ke čtverečku pro telefon
        if (Prefs.isTv(a)) row.addView(ui.button(t("dl_key_phone")) { manual = false; openPage() }.apply { tag = "dlphone" }, gap())
        add(row, 10)
        return false
    }

    /**
     * Televize: čtvereček na stránku pro telefon a vedle něj krátké kroky (podobně jako qrRow v MainActivity).
     * Vysvětlení registrace (plán Developer, karta, Security check…) je až na stránce v telefonu.
     */
    private fun phoneQr(url: String): View {
        val row = ui.hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        QrImage.bitmap(url)?.let { qr ->
            row.addView(ImageView(a).apply {
                setImageBitmap(qr); setBackgroundColor(android.graphics.Color.WHITE)
                setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
            }, LinearLayout.LayoutParams(ui.dp(168), ui.dp(168)))
        }
        val col = ui.vbox().apply { setPadding(ui.dp(24), 0, 0, 0) }
        fun line(v: View, top: Int) = col.addView(v, ui.lp(top = top))
        line(ui.text(t("dl_tv_scan"), 19f, Theme.TEXT, bold = true), 0)
        line(ui.text(t("dl_tv_1"), 16f, Theme.TEXT), 6)
        line(ui.text(t("dl_tv_2"), 16f, Theme.TEXT), 2)
        line(ui.text(t("dl_tv_3"), 16f, Theme.TEXT), 2)
        line(ui.text(t("dl_tv_where"), 14f, Theme.MUTED), 4)
        line(ui.text("⏳ " + t("dl_wait"), 16f, Theme.ACCENT, bold = true).apply { tag = "dlwait" }, 10)
        col.addView(ui.waiting(0).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(4) })
        line(ui.text(url, 13f, Theme.MUTED), 2)
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    /** Televize, klíč ručně: malý čtvereček na registraci DeepL API ([Deepl.SIGNUP_URL]) a vedle krok 1. */
    private fun signupQr(): View {
        val row = ui.hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        QrImage.bitmap(Deepl.SIGNUP_URL)?.let { qr ->
            row.addView(ImageView(a).apply {
                setImageBitmap(qr); setBackgroundColor(android.graphics.Color.WHITE)
                setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
            }, LinearLayout.LayoutParams(ui.dp(120), ui.dp(120)))
        }
        val col = ui.vbox().apply { setPadding(ui.dp(20), 0, 0, 0) }
        col.addView(ui.text(t("dl_s1"), 16f, Theme.TEXT, bold = true), ui.lp(top = 0))
        col.addView(ui.text(t("dl_tv_scan"), 14f, Theme.MUTED), ui.lp(top = 4))
        col.addView(ui.text(Deepl.SIGNUP_URL, 13f, Theme.MUTED), ui.lp(top = 2))
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun open(url: String) {
        try { a.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
        catch (_: Exception) { note(t("dl_qr_nolan"), Theme.WARN); rerender() }
    }

    /** Na úzké obrazovce (telefon na výšku) tlačítka pod sebou. */
    private fun row(): LinearLayout = if (a.resources.configuration.screenWidthDp in 1 until 600) ui.vbox() else ui.hbox()

    private fun gap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { if (a.resources.configuration.screenWidthDp in 1 until 600) topMargin = ui.dp(8) else leftMargin = ui.dp(12) }

    private fun note(s: String?, color: Int) { msg = s; msgColor = color }

    private fun problem(p: Deepl.Problem) = t("dl_p_" + p.name.lowercase())

    private fun usage(u: Deepl.Usage) = t("dl_usage").replace("%1\$s", group(u.left)).replace("%2\$s", group(u.limit))

    /** 1000000 → „1 000 000“ */
    private fun group(n: Long): String = n.toString().reversed().chunked(3).joinToString(" ").reversed()

    private fun saveTyped() {
        val key = Deepl.cleanKey(draft)
        if (Deepl.keyKind(key) == null) { note(t("dl_e_shape"), Theme.BAD); rerender(); return }
        verify(key, fromField = true)
    }

    /** Ověří klíč u DeepL; platný (nebo neověřitelný kvůli síti) se uloží. */
    private fun verify(key: String, fromField: Boolean) {
        busy = true; note(t("dl_checking"), Theme.MUTED); rerender()
        Thread {
            val r = DeeplTranslator.check(key)
            val text = store(key, r)
            a.runOnUiThread {
                busy = false
                if (r.problem != Deepl.Problem.BAD_KEY && fromField) { draft = ""; changing = false }
                note(text, if (r.problem == null) Theme.OK else if (r.problem == Deepl.Problem.BAD_KEY) Theme.BAD else Theme.WARN)
                applyToService()
                if (!a.isFinishing) rerender()
                if (r.problem == null && !a.isFinishing) onAccepted()
            }
        }.apply { isDaemon = true }.start()
    }

    /** Uloží klíč podle výsledku ověření (odmítnutý se neukládá) a vrátí text pro hráče. */
    private fun store(key: String, r: DeeplTranslator.Check): String {
        if (r.problem == Deepl.Problem.BAD_KEY) { if (DeeplKey.get(a) == key) DeeplKey.saveCheck(a, r); return problem(r.problem) }
        if (DeeplKey.get(a) != key) {
            // první klíč: DeepL rovnou zapnout (o to hráč klíč vkládal); vypnout jde v Titulkách → Kvalita překladu
            val first = !DeeplKey.has(a)
            DeeplKey.save(a, key)
            if (first) DeeplKey.setEnabled(a, true)
        }
        DeeplKey.saveCheck(a, r)
        return r.usage?.let { usage(it) } ?: problem(r.problem ?: Deepl.Problem.BAD_REPLY)
    }

    // ---------------- vložení klíče z telefonu ----------------

    private fun pageUrl(): String? {
        val s = page?.takeIf { it.running } ?: return null
        val ip = NetInfo.homeAddress(a) ?: return null
        return PhoneLogin.pageUrl(PhoneLogin.Target(ip, s.port, s.key))
    }

    private fun openPage(redraw: Boolean = true) {
        if (page != null) return
        val server = LoginServer.forPage({ Prefs.uiLang(a) }, object : LoginServer.Custom {
            override fun get(path: String) = DeeplPage.page(Prefs.uiLang(a), DeeplPage.State.ASK, path)
            override fun gone() = DeeplPage.page(Prefs.uiLang(a), DeeplPage.State.GONE, "")
            override fun post(path: String, form: Map<String, String>): String {
                val lang = Prefs.uiLang(a)
                val key = Deepl.cleanKey(form["key"])
                if (Deepl.keyKind(key) == null) return DeeplPage.page(lang, DeeplPage.State.BAD_SHAPE, path)
                val r = DeeplTranslator.check(key)
                val text = store(key, r)
                if (r.problem == Deepl.Problem.BAD_KEY) return DeeplPage.page(lang, DeeplPage.State.REJECTED, path, text)
                a.runOnUiThread {
                    note(text, if (r.problem == null) Theme.OK else Theme.WARN)
                    applyToService()
                    // stránka ještě odešle odpověď telefonu, pak se zavře
                    main.postDelayed({
                        stopPage()
                        if (!a.isFinishing) { rerender(); if (r.problem == null) onAccepted() }
                    }, 1500)
                }
                return DeeplPage.page(lang, DeeplPage.State.DONE, path, text)
            }
        }, log = { android.util.Log.i("TP_TEST", "DEEPL|page " + it) })
        if (!server.start() || NetInfo.homeAddress(a) == null) {
            server.stop()
            manual = true   // bez domácí sítě to dokola nezkoušet; tlačítko „Z telefonu“ to zkusí znovu
            note(t("dl_qr_nolan"), Theme.WARN)
        } else page = server
        if (redraw) rerender()
    }

    /** Klíč ze schránky, který už se zkoušel (odmítnutý se dokola neověřuje). */
    private var clipTried: String? = null

    /**
     * Telefon / tablet bez klíče: po návratu do aplikace (z DeepL v prohlížeči, kde se klíč zkopíroval) přečíst schránku;
     * je v ní klíč DeepL → vyplnit a rovnou ověřit. Volá jen krok průvodce DeepL – jinde se schránka nečte.
     */
    fun fromClipboard() {
        if (Prefs.isTv(a) || busy || DeeplKey.has(a)) return
        val cm = a.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
        val text = try {
            if (cm.primaryClipDescription?.hasMimeType("text/*") != true) return
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(a)?.toString()
        } catch (_: Throwable) { null } ?: return
        val key = Deepl.cleanKey(text)
        if (Deepl.keyKind(key) == null || key == clipTried) return
        clipTried = key
        draft = key
        verify(key, fromField = true)
    }

    /** Stránka pro telefon jen, dokud je vidět její čtvereček (volá se i při odchodu z Titulků a kroku průvodce). */
    fun stopPage() {
        page?.stop(); page = null
    }
}
