package cz.recaption

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import cz.recaption.core.AtvAction
import cz.recaption.core.AtvError
import cz.recaption.core.AtvException
import cz.recaption.core.AtvInstall
import cz.recaption.core.AtvPhase
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Průvodce v telefonu: nahrát (aktualizovat) ReCaption do Android TV / Google TV / boxu přes ladění
 * po síti – bez aplikace v televizi (AtvInstaller, rozhodování v core/AtvInstall.kt). Vzor je
 * LgInstallActivity: karta kroků, které se samy odškrtávají (TV v síti, ladění zapnuté, TV klíč
 * přijala), adresa, jedno velké tlačítko a průběh. Otevírá se z pomocníka v telefonu.
 */
class AtvInstallActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var content: LinearLayout
    private lateinit var vault: AtvVault
    private var ip = ""
    private var running = false
    private var phase: AtvPhase? = null
    private var fraction: Float? = null
    private var installer: AtvInstaller? = null
    private var resultText: String? = null
    private var error: AtvError? = null
    /** Po nahrání: telefon se připojuje ke stránce nastavení v TV, aby nastavení vedl dál sám (část 4 šablony průvodce). */
    private var connecting = false

    private var probe = AtvInstall.Probe(tvFound = false, debug = false, allowed = false)
    private var foundTvs: List<String> = emptyList()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var watching = false
    @Volatile private var checking = false
    private var needsRender = false
    private var lastScan = 0L
    private val tick = object : Runnable {
        override fun run() {
            if (!watching) return
            if (needsRender && currentFocus !is EditText && !running) { needsRender = false; render(true) }
            check()
            main.postDelayed(this, 3000)
        }
    }

    private fun t(key: String) = tx(key)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        vault = AtvVault(this)
        ip = vault.lastIp
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Theme.BG) }
        content = ui.vbox().apply {
            val side = if (resources.configuration.screenWidthDp >= 900) ui.dp(80) else ui.dp(20)
            setPadding(side, ui.dp(28), side, ui.dp(36))
        }
        scroll.addView(content)
        setContentView(scroll)
        render()
    }

    override fun onResume() {
        super.onResume()
        watching = true
        main.removeCallbacks(tick)
        main.post(tick)
    }

    override fun onPause() {
        watching = false
        main.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        installer?.cancel()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (running) { installer?.cancel(); return }
        @Suppress("DEPRECATION") super.onBackPressed()
    }

    /** Na pozadí: najdi televizi (když adresa chybí) a zkus port ladění. Jen otevře a zavře spojení. */
    private fun check() {
        if (checking || running) return
        checking = true
        val ipNow = ip
        Thread({
            try {
                var found = foundTvs
                var target = AtvInstall.addressOrNull(ipNow)
                val now = System.currentTimeMillis()
                if (target == null && now - lastScan > 20_000) {
                    lastScan = now
                    found = scan()
                    target = found.firstOrNull()?.let { AtvInstall.addressOrNull(it) }
                }
                val tg = target
                val debug = tg != null && LgFinder.open(tg.ip, tg.port)
                val tvOn = tg != null && (debug || found.contains(tg.ip) || LgFinder.open(tg.ip, AtvInstall.REMOTE_PORT) || LgFinder.open(tg.ip, AtvInstall.CAST_PORT))
                val allowed = tg != null && vault.allowed(tg.ip)
                val next = AtvInstall.Probe(tvOn, debug, allowed)
                runOnUiThread {
                    if (isFinishing || running) return@runOnUiThread
                    val fill = ip.isEmpty() && tg != null && found.contains(tg.ip)
                    if (fill) ip = tg!!.ip
                    if (next != probe || found != foundTvs || fill) {
                        probe = next; foundTvs = found
                        if (currentFocus is EditText && !fill) needsRender = true else render(true)
                    }
                }
            } finally { checking = false }
        }, "tp-atv-check").start()
    }

    /** Projít domácí síť (/24): nejdřív zařízení se zapnutým laděním (5555), jinak Android TV podle služby ovládání. */
    private fun scan(): List<String> {
        val me = NetInfo.homeAddress(this) ?: return emptyList()
        val prefix = me.substringBeforeLast('.')
        val pool = Executors.newFixedThreadPool(32)
        return try {
            val jobs = (1..254).map { "$prefix.$it" }.filter { it != me }.map { host ->
                pool.submit(Callable {
                    when {
                        LgFinder.open(host, AtvInstall.ADB_PORT, 350) -> host to 0
                        LgFinder.open(host, AtvInstall.REMOTE_PORT, 350) -> host to 1
                        else -> null
                    }
                })
            }
            jobs.mapNotNull { runCatching { it.get(3, TimeUnit.SECONDS) }.getOrNull() }.sortedBy { it.second }.map { it.first }
        } finally { pool.shutdownNow() }
    }

    private fun add(v: View, top: Int = 12) = content.addView(v, ui.lp(top = top))
    private fun <V : View> tagged(v: V, tag: String): V { v.tag = tag; return v }

    private fun field(): EditText = EditText(this).apply {
        setText(ip)
        setSelection(ip.length)
        hint = t("lg_ip_hint")
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_PHONE
        IpField.setup(this)
        imeOptions = EditorInfo.IME_ACTION_DONE
        isSaveEnabled = false
        setTextColor(Theme.TEXT)
        setHintTextColor(Theme.MUTED)
        textSize = 17f
        background = ui.focusBg(Theme.CARD)
        setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12))
        tag = "atip"
        isEnabled = !running
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { ip = s?.toString()?.trim() ?: "" }
        })
    }

    /** Seznam kroků s fajfkami; rozepsaný je jen ten, který je na řadě. */
    private fun stepsCard(): View = ui.card().apply {
        addView(ui.text(t("lg_steps_h"), 18f, Theme.ACCENT, bold = true))
        val now = AtvInstall.step(probe)
        val total = AtvInstall.Step.values().size
        addView(ui.steps(now.ordinal + 1, total, tx("steps_of").replace("%1", (now.ordinal + 1).toString()).replace("%2", total.toString())), ui.lp(top = 8))
        val steps = listOf(AtvInstall.Step.FIND_TV to "at_s_find", AtvInstall.Step.DEBUG to "at_s_debug", AtvInstall.Step.CONFIRM to "at_s_confirm")
        for ((s, key) in steps) {
            val done = AtvInstall.done(s, probe)
            val cur = s == now
            val mark = if (done) "✔ " else if (cur) "➜ " else "○ "
            addView(ui.text(mark + t(key), 16f, if (done) Theme.OK else if (cur) Theme.TEXT else Theme.MUTED, bold = cur), ui.lp(top = 8))
            if (cur) {
                addView(ui.waiting(24).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(4) })   // krok se sám odškrtne (zjišťuje se každé 3 s)
                addView(ui.text(t(key + "_how"), 15f, Theme.TEXT), ui.lp(top = 4))
            }
        }
        if (now == AtvInstall.Step.READY) addView(ui.text("✔ " + t("at_s_ready"), 16f, Theme.OK, bold = true), ui.lp(top = 10))
        foundTvs.takeIf { it.isNotEmpty() }?.let { addView(ui.text(t("at_s_found").replace("%s", it.joinToString(", ")), 14f, Theme.MUTED), ui.lp(top = 8)) }
    }

    /** „Sony BRAVIA · ReCaption 0.97.55“ – jen když o TV na téhle adrese něco víme. */
    private fun statusLine(): String? {
        val target = AtvInstall.addressOrNull(ip) ?: return null
        val model = vault.model(target.ip)
        val ver = vault.tvVersion(target.ip)
        if (model == null && ver == null) return null
        return listOfNotNull(model ?: "Android TV", ver?.let { "Titulky PS5 $it" }).joinToString(" · ")
    }

    private fun render(keepFocus: Boolean = false) {
        val focused = if (keepFocus) currentFocus?.tag else null
        content.removeAllViews()
        add(ui.text(t("at_title"), 26f, Theme.TEXT, bold = true), 0)
        statusLine()?.let { add(ui.text(it, 18f, Theme.ACCENT, bold = true), 8) }
        // výsledek a chyba hned pod nadpisem (přání majitele 22. 11. 2026)
        if (!running) {
            resultText?.let { add(ui.text(it, 17f, Theme.OK, bold = true), 12) }
            if (connecting) { add(ui.text(t("rs_connecting"), 16f, Theme.ACCENT, bold = true), 12); add(ui.waiting(0), 4) }
            // jen srozumitelná věta – technická podrobnost je v záznamu (ATV|chyba …)
            error?.let { e -> add(ui.text(t("at_e_" + e.name.lowercase()), 16f, Theme.BAD, bold = true), 12) }
        }
        add(stepsCard(), 18)
        add(ui.text(t("lg_ip"), 16f, Theme.TEXT, bold = true), 20)
        add(field(), 6)

        if (running) {
            val ph = phase ?: AtvPhase.PACKAGE
            add(ui.text(t("at_p_" + ph.name.lowercase()), 17f, Theme.ACCENT, bold = true), 18)
            // přenos APK podle bajtů, ostatní kroky podle pořadí; potvrzení v TV = čekání (pruh běhá)
            val f = when (ph) {
                AtvPhase.INSTALL -> fraction
                AtvPhase.CONFIRM -> null
                else -> (ph.ordinal + 1f) / AtvPhase.values().size
            }
            add(ui.progress(f), 8)
            add(tagged(ui.button(t("lg_cancel")) { installer?.cancel() }, "atcancel"), 10)
        } else {
            // v TV stejná nebo novější verze = jen spustit (AtvInstaller nic nesnižuje), starší = aktualizovat
            val tvCode = AtvInstall.addressOrNull(ip)?.let { vault.tvCode(it.ip) } ?: 0L
            val label = when (if (tvCode > 0) AtvInstall.decide(tvCode, BuildInfo.VERSION_CODE.toLong()) else AtvAction.INSTALL) {
                AtvAction.INSTALL -> "lg_go"
                AtvAction.UPDATE -> "lg_update"
                AtvAction.LAUNCH, AtvAction.LAUNCH_NEWER -> "lg_launch"
            }
            add(tagged(ui.button(t(label), primary = true) { start() }, "atgo"), 18)
        }

        add(tagged(ui.button(t("wz_back")) { if (running) installer?.cancel(); finish() }, "atback"), 22)

        val target = focused?.let { content.findViewWithTag<View>(it) }
            ?: content.findViewWithTag<View>(if (running) "atcancel" else if (ip.isEmpty()) "atip" else "atgo")
        ui.restoringFocus()
        target?.requestFocus()
    }

    private fun start() {
        if (running) return
        if (AtvInstall.addressOrNull(ip) == null) { error = AtvError.ADDRESS; resultText = null; render(); return }
        val inst = AtvInstaller(this)
        // jednorázový klíč stránky nastavení v TV: TV ho dostane při spuštění, telefon se s ním pak připojí sám
        val phoneKey = cz.recaption.core.PhoneLogin.newKey() + cz.recaption.core.PhoneLogin.newKey()
        inst.phoneKey = phoneKey
        installer = inst
        running = true; phase = AtvPhase.PACKAGE; fraction = null; error = null; resultText = null
        render()
        val ipNow = ip
        Thread({
            try {
                val r = inst.run(ipNow) { p, f ->
                    runOnUiThread {
                        phase = p; fraction = f
                        if (!isFinishing && running) render(true)
                    }
                }
                runOnUiThread { done(r, null, phoneKey) }
            } catch (e: AtvException) {
                runOnUiThread { done(null, e) }
            } catch (e: Exception) {
                runOnUiThread { done(null, AtvException(AtvError.CONNECT)) }
            }
        }, "tp-atv-install").start()
    }

    private fun done(r: AtvInstaller.Result?, e: AtvException?, phoneKey: String? = null) {
        running = false; installer = null
        if (isFinishing || isDestroyed) return
        if (r != null) {
            val key = when (r.action) {
                AtvAction.INSTALL -> "at_r_install"
                AtvAction.UPDATE -> "at_r_update"
                AtvAction.LAUNCH -> "at_r_same"
                AtvAction.LAUNCH_NEWER -> "at_r_newer"
            }
            resultText = t(key).replace("%s", r.version) + "\n" + t(if (r.launched) "lg_r_launched" else "at_r_nolaunch")
            probe = probe.copy(allowed = true)
            // bez dialogu: výsledek i rada vypnout ladění zeleně nahoře stránky (majitel 22. 11. 2026)
            resultText = resultText + "\n" + t("at_r_off")
            // pomocník vede dál sám: připojit se k TV a otevřít průvodce v režimu „Nastavuji TV“
            if (r.launched && phoneKey != null) AtvInstall.addressOrNull(ip)?.let { continueOnTv(it.ip, phoneKey, r.model) }
        } else if (e != null && e.code != AtvError.CANCELLED) {
            error = e.code
            // podrobnost (odpověď TV, výjimka) jen do záznamu – na obrazovku nepatří (přání majitele 22. 11. 2026)
            val line = "ATV|chyba ${e.code}" + (e.detail?.let { " · $it" } ?: "")
            EventFile.add(line); cz.recaption.core.DiagLog.add(line)
        }
        render()
    }

    /**
     * Po nahrání a spuštění ReCaptionu v TV (od šablony průvodce 24. 11. 2026, přání majitele: telefon vede vším): TV dostala
     * klíč a otevřela „nastavení telefonem“ – telefon se k ní připojí (TV potřebuje chvilku na start, zkouší se až 30 s)
     * a otevře celou aplikaci v režimu „Nastavuji TV“. Nepovede se = zůstane výsledek jako dřív (čtvereček v TV jde dál).
     */
    private fun continueOnTv(host: String, key: String, model: String?) {
        connecting = true
        val link = cz.recaption.core.RemoteSetup.Link(cz.recaption.core.PhoneLogin.Target(host, RemoteHost.PORT, key), model ?: "Android TV")
        Thread({
            val until = System.currentTimeMillis() + 30_000
            var r = RemoteClient.Connect.UNREACHABLE
            while (!isFinishing && System.currentTimeMillis() < until) {
                r = RemoteClient.connect(this, link)
                if (r == RemoteClient.Connect.OK || r == RemoteClient.Connect.VERSION) break
                try { Thread.sleep(1500) } catch (_: InterruptedException) { break }
            }
            val line = "ATV|po nahrání pokračuje telefon: $r"
            EventFile.add(line); cz.recaption.core.DiagLog.add(line)
            runOnUiThread {
                connecting = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (r == RemoteClient.Connect.OK) {
                    startActivity(android.content.Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_REMOTE, true))
                    finish()
                } else render()
            }
        }, "tp-atv-continue").start()
    }
}
