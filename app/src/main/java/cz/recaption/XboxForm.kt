package cz.recaption

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import cz.recaption.core.XboxAuth
import cz.recaption.core.XboxFail

/**
 * Stránka PlayStation, když je vybraná konzole Xbox (i krok průvodce s připojením konzole):
 * přihlášení účtem Microsoft kódem na microsoft.com/link (čtvereček pro telefon, v telefonu tlačítko
 * na stránku), seznam Xboxů na účtu a výběr. Stav (kód, seznam) drží tahle třída, takže překreslení
 * obrazovky o nic nepřijde. Ovládá se ovladačem od televize i prstem.
 */
class XboxForm(
    private val a: Activity,
    private val ui: Ui,
    private val add: (View, Int) -> Unit,
    /** Řada tlačítek (na úzké obrazovce pod sebou) a odsazení dalšího tlačítka v ní. */
    private val row: () -> LinearLayout,
    private val wrapLp: () -> LinearLayout.LayoutParams,
    /** Čtvereček s adresou a text vedle něj (stejný jako u přihlášení PlayStationu). */
    private val qrRow: (String, String) -> View,
    /** Překreslit obrazovku (volá se na hlavním vlákně). */
    private val rerender: () -> Unit,
    /** Xbox vybraný (i sám – jediný na účtu): průvodce pak sám pokračuje na Hotovo jako po spárování PS5. */
    private val onReady: () -> Unit = {},
) {
    private fun t(k: String) = a.tx(k)

    private var code: XboxAuth.DeviceCode? = null
    /** Čeká se, až člověk zadá kód (obrazovka nesmí zhasnout). */
    val waiting: Boolean get() = code != null
    private var busy = false
    private var msg: String? = null
    private var msgColor = Theme.MUTED
    private var consoles: List<XboxAuth.XboxConsole>? = null
    private var loading = false
    /** Číslo právě běžícího čekání na kód (staré čekání po zrušení skončí samo). */
    @Volatile private var attempt = 0

    private fun <V : View> tagged(v: V, tag: String): V { v.tag = tag; return v }

    private fun post(f: () -> Unit) = a.runOnUiThread { if (!a.isFinishing) { f(); rerender() } }

    /**
     * Vykreslí obsah. Hlavní tlačítko má značku „pnext“ jako u PlayStationu, aby na něm v průvodci
     * stál výběr. [onStart] = tlačítko Spustit překlad (null = neukazovat, např. v průvodci).
     */
    fun render(onStart: (() -> Unit)?) {
        val c = code
        // nepřihlášený: chyba přihlášení / spojení jako první věc, ne pod dlouhou kartou přípravy (přání majitele 22. 11. 2026)
        if (c == null && !XboxAccount.signedIn(a)) msgLine(8)
        add(ui.text(t("xb_intro"), 16f, Theme.MUTED), 12)
        when {
            c != null -> renderCode(c)
            !XboxAccount.signedIn(a) -> {
                add(ui.card().apply {
                    addView(ui.text(t("xb_prep_h"), 16f, Theme.ACCENT, bold = true))
                    addView(ui.text(t("xb_prep"), 16f, Theme.TEXT), ui.lp(top = 6))
                }, 12)
                add(tagged(ui.button(t(if (busy) "xb_login_wait" else "xb_login_btn"), primary = true) { if (!busy) login() }, "pnext"), 14)
            }
            else -> renderAccount(onStart)
        }
    }

    /** Výsledek / chyba: chyba a výsledek tučně (jako u spárování PS5), průběžná šedá hláška obyčejně. */
    private fun msgLine(top: Int) = msg?.let { m ->
        val res = msgColor != Theme.MUTED
        add(ui.text(m, if (res) 16f else 15f, msgColor, bold = res), top)
    }

    private fun renderCode(c: XboxAuth.DeviceCode) {
        val link = XboxAuth.shortLink(c.verificationUri)
        add(ui.text(t("xb_login_code").replace("%s", link), 17f, Theme.TEXT), 12)
        add(tagged(ui.text(c.userCode, 40f, Theme.ACCENT, bold = true).apply { letterSpacing = 0.12f }, "xcode"), 6)
        add(qrRow(c.verificationUri, t("xb_login_qr")), 10)
        val left = ((c.expiresAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
        add(ui.text(t("xb_login_waiting").replace("%d", left.toString()), 15f, Theme.MUTED), 8)
        add(ui.progress(null), 6)   // čeká se, až se hráč přihlásí v telefonu
        val r = row()
        // telefon: stránku rovnou otevřít (čtvereček by si sám nenačetl)
        if (NetInfo.isHandheld(a)) r.addView(tagged(ui.button(t("xb_login_open"), primary = true) {
            runCatching { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.verificationUri))) }
        }, "pnext"))
        r.addView(tagged(ui.button(t("xb_login_cancel"), primary = r.childCount == 0) { cancel(); rerender() },
            if (r.childCount == 0) "pnext" else "xcancel"), if (r.childCount == 0) ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT) else wrapLp())
        add(r, 12)
    }

    private fun renderAccount(onStart: (() -> Unit)?) {
        val gt = XboxAccount.gamertag(a)
        add(ui.text(t("xb_login_ok") + if (gt.isNotEmpty()) "  " + t("xb_signed").replace("%s", gt) else "", 17f, Theme.OK, bold = true), 12)
        val chosen = XboxAccount.consoleId(a)
        if (chosen != null) add(ui.card().apply {
            addView(ui.text(t("xb_chosen").replace("%s", XboxAccount.consoleName(a)), 20f, Theme.OK, bold = true))
        }, 10)
        msgLine(10)
        if (chosen != null && onStart != null && !TranslatorService.running)
            add(tagged(ui.button(t("b_start"), primary = true) { onStart() }, "pnext"), 12)

        val list = consoles
        if (list == null && !loading) loadConsoles()
        if (list != null || loading) {
            add(ui.section(t("xb_consoles_h")), 0)
            when {
                loading -> add(ui.text(t("xb_consoles_load"), 15f, Theme.MUTED), 0)
                list.isNullOrEmpty() -> add(ui.text(t("xb_consoles_none"), 15f, Theme.WARN), 0)
                else -> {
                    if (chosen == null) add(ui.text(t("xb_choose_hint"), 15f, Theme.MUTED), 0)
                    list.forEachIndexed { i, k ->
                        val mark = if (k.id == chosen) "✔ " else ""
                        add(tagged(ui.settingRow(mark + k.name, t(if (k.isOn) "xb_on" else "xb_off"), if (k.isOn) Theme.OK else Theme.MUTED) {
                            choose(k)
                        }, if (chosen == null && i == 0) "pnext" else "xcon$i"), 8)
                    }
                }
            }
        }
        val r = row()
        r.addView(tagged(ui.button(t("xb_consoles_reload")) { consoles = null; loadConsoles() }, "xreload"))
        r.addView(tagged(ui.button(t("xb_logout")) {
            if (TranslatorService.running && TranslatorService.xbox) a.stopService(Intent(a, TranslatorService::class.java))
            XboxAccount.signOut(a); consoles = null; msg = null; rerender()
        }, "xlogout"), wrapLp())
        add(r, 14)
    }

    private fun choose(k: XboxAuth.XboxConsole) {
        val changed = k.id != XboxAccount.consoleId(a)
        XboxAccount.setConsole(a, k.id, k.name)
        // běží překlad s jiným Xboxem → zastavit, příští spuštění vezme nový
        if (changed && TranslatorService.running && TranslatorService.xbox) a.stopService(Intent(a, TranslatorService::class.java))
        msg = null
        onReady()
        rerender()
    }

    private fun loadConsoles() {
        loading = true
        Thread({
            val r = runCatching { XboxAccount.consoles(a) }
            post {
                loading = false
                r.onSuccess { l ->
                    consoles = l
                    // jediný Xbox na účtu: rovnou vybrat
                    if (l.size == 1 && XboxAccount.consoleId(a) == null) { XboxAccount.setConsole(a, l[0].id, l[0].name); onReady() }
                }.onFailure { e ->
                    consoles = emptyList()
                    showError((e as? XboxAccount.XboxException)?.fail ?: XboxFail.NETWORK)
                }
            }
        }, "tp-xbox-list").start()
    }

    private fun showError(f: XboxFail) { msg = t(f.textKey); msgColor = Theme.BAD }

    private fun login() {
        busy = true; msg = null; rerender()
        val my = ++attempt
        Thread({
            val c = runCatching { XboxAccount.requestCode() }.getOrNull()
            if (c == null) { post { busy = false; showError(XboxFail.NETWORK) }; return@Thread }
            post { busy = false; code = c; keepScreenOn(true) }
            val ok = XboxAccount.waitForLogin(a, c) { attempt != my || a.isFinishing }
            if (ok == null) return@Thread
            post {
                code = null; keepScreenOn(false)
                if (ok) { msg = null; consoles = null } else { msg = t("xb_login_fail"); msgColor = Theme.BAD }
            }
        }, "tp-xbox-login").start()
    }

    /** Zrušit rozběhnuté přihlašování (tlačítko Zrušit, odchod z aplikace). */
    fun cancel() {
        attempt++
        code = null; busy = false
        keepScreenOn(false)
    }

    /** Zadání kódu v telefonu trvá – televize mezitím nesmí zhasnout. */
    private fun keepScreenOn(on: Boolean) {
        if (on) a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
