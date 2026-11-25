package cz.recaption

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import cz.recaption.core.DiagLog
import cz.recaption.core.LinkStatus
import cz.recaption.core.LogShare
import cz.recaption.core.Ps5
import cz.recaption.core.Redact
import cz.recaption.core.TvLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hlášení o stavu aplikace přes celou obrazovku – aby se dalo vyfotit a poslat: verze, zařízení,
 * stav spojení s PlayStationem, poslední pád a poslední události. Celé (delší) hlášení se navíc
 * uloží do souboru. Klíče, čísla účtu ani PIN v něm nejsou ([Redact]).
 * Ovládání: šipky ▲▼ posouvají, na konci šipka dolů přejde na tlačítka; v telefonu prstem.
 */
class DiagActivity : Activity() {

    private lateinit var ui: Ui
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        val report = DiagReport.build(this)
        val where = DiagReport.save(this, report)
        val phone = resources.configuration.screenWidthDp in 1 until 600
        val pad = ui.dp(if (phone) 16 else 48)

        val root = ui.vbox().apply {
            setBackgroundColor(Theme.BG)
            setPadding(pad, ui.dp(if (phone) 18 else 28), pad, ui.dp(if (phone) 12 else 22))
        }
        root.addView(ui.text(tx("dg_title"), if (phone) 22f else 28f, Theme.TEXT, bold = true))
        root.addView(ui.text(tx("dg_hint"), if (phone) 14f else 16f, Theme.ACCENT), ui.lp(top = 4))
        NetLogShare.url(this)?.let { root.addView(ui.text(tx("zz_url").replace("%s", it), if (phone) 14f else 16f, Theme.OK), ui.lp(top = 4)) }

        val body = ui.vbox().apply { setPadding(ui.dp(18), ui.dp(4), ui.dp(18), ui.dp(16)) }
        val textSize = if (phone) 13f else 16f
        for (s in report.sections) {
            body.addView(ui.section(s.title))
            body.addView(ui.text(s.text, textSize, Theme.TEXT).apply {
                if (s.mono) { typeface = Typeface.MONOSPACE; this.textSize = textSize - 2f; setTextColor(Theme.MUTED) }
                setLineSpacing(0f, 1.15f)
            })
        }
        body.addView(ui.text(tx("rep_saved") + " " + where, textSize - 2f, Theme.MUTED), ui.lp(top = 18))

        scroll = ScrollView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = false
            isScrollbarFadingEnabled = false
            background = ui.focusBg(Theme.SIDEBAR, focused = Theme.CARD)
            addView(body)
            tag = "diagtext"
        }
        // Šipky posouvají text po kouscích; na konci šipka dolů pustí výběr na tlačítka.
        scroll.setOnKeyListener { _, code, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val step = scroll.height / 3
            when (code) {
                KeyEvent.KEYCODE_DPAD_DOWN -> if (!scroll.canScrollVertically(1)) false else { scroll.smoothScrollBy(0, step); true }
                KeyEvent.KEYCODE_DPAD_UP -> if (!scroll.canScrollVertically(-1)) false else { scroll.smoothScrollBy(0, -step); true }
                KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { scroll.smoothScrollBy(0, scroll.height); true }
                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { scroll.smoothScrollBy(0, -scroll.height); true }
                else -> false
            }
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = ui.dp(14) })

        val buttons = ui.hbox()
        fun gap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = ui.dp(12) }
        buttons.addView(ui.button(tx("dg_top")) { scroll.smoothScrollTo(0, 0); scroll.requestFocus() }.apply { tag = "diagtop" })
        // celé hlášení (jako soubor) do schránky nebo přes Sdílet – záloha, když nejde záznam v síti
        // do telefonu přes čtvereček (stejná Wi-Fi) – hráč pak hlášení pošle autorovi chatem nebo e-mailem
        // na úložiště vývojáře s kódem (ReportSender, core/ReportUpload.kt) – i s dávkou snímků ze hry
        buttons.addView(ui.button(tx("snd_btn")) { ReportSender.open(this, ui, report.full) }.apply { tag = "diagsend" }, gap())
        buttons.addView(ui.button(tx("zz_qr_btn")) { NetLogShare.showQr(this, ui) }.apply { tag = "diagqr" }, gap())
        buttons.addView(ui.button(tx("zz_copy")) { NetLogShare.copy(this, report.full) }.apply { tag = "diagcopy" }, gap())
        buttons.addView(ui.button(tx("zz_share")) { NetLogShare.share(this, report.full) }.apply { tag = "diagshare" }, gap())
        buttons.addView(ui.button(tx("dg_close"), primary = true) { finish() }.apply { tag = "diagclose" }, gap())
        // v úzkém telefonu se tlačítka nevejdou – dají se posunout do strany
        root.addView(android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(buttons) }, ui.lp(top = 12))
        setContentView(root)
        scroll.post { scroll.requestFocus() }
    }

    override fun onResume() { super.onResume(); UiState.shown() }
    override fun onPause() { UiState.hidden(); super.onPause() }
}

/** Obsah hlášení o stavu aplikace (obrazovka i soubor). Všechno prochází přes [Redact]. */
object DiagReport {
    class Section(val title: String, val text: String, val mono: Boolean = false)
    /** [full] = celé hlášení jako text, [parts] = totéž po oddílech (název, text) – obojí už přes [Redact]. */
    class Report(val sections: List<Section>, val full: String, val parts: List<Pair<String, String>>)

    /** Hodnoty, které se v hlášení nesmí objevit: čísla účtu, klíče konzole, PIN. */
    fun secrets(c: Context): List<String> {
        val out = mutableListOf(Prefs.psnId(c), Prefs.ps5LoginPin(c))
        Prefs.phoneAccounts(c).forEach { out += it.id }
        DeeplKey.get(c)?.let { out += it }
        Prefs.ps5Host(c)?.let { h ->
            out += Ps5.hex(h.registKey); out += Ps5.hex(h.morning)
            val end = h.registKey.indexOfFirst { it == 0.toByte() }.let { if (it >= 0) it else h.registKey.size }
            out += String(h.registKey, 0, end, Charsets.UTF_8)
            Ps5.wakeCredential(h.registKey)?.let { out += it.toString() }
        }
        // Xbox: obnovovací token a tokeny v paměti (v hlášení se nesmí objevit nikdy)
        runCatching { XboxAccount.refreshToken(c) }.getOrNull()?.let { out += it }
        out += XboxAccount.memorySecrets()
        return out.filter { it.isNotBlank() }
    }

    /** Zapnuté volby her a čtení (jen čtení Prefs) – ať jde z hlášení poznat, s čím hráč hrál. */
    fun optionsLine(c: Context): String {
        fun yn(b: Boolean) = if (b) "ano" else "ne"
        val corner = when (Prefs.cornerMenu(c)) { 1 -> "nad"; 2 -> "přes"; else -> "vypnuto" }
        return "Volby: pokyny=" + yn(Prefs.gamePrompts(c)) + ", úkol=" + yn(Prefs.objectiveOn(c)) +
            ", menu hry=" + yn(Prefs.gameMenu(c)) + ", vpravo dole=" + corner + ", u předmětů=" + yn(Prefs.worldPrompts(c)) + ", tipy=" + yn(Prefs.gameTips(c)) +
            ", jen nové=" + yn(Prefs.onlyNewLines(c)) + ", rychlejší čtení=" + yn(Prefs.fastRead(c)) +
            ", nezhasínat displej=" + yn(Prefs.screenDim(c)) + ", nejkratší doba=" + Prefs.minShowMs(c) + " ms"
    }

    fun build(c: Context): Report {
        val secrets = secrets(c)
        fun clean(s: String) = Redact.clean(s, secrets)
        val now = SimpleDateFormat("d. M. yyyy HH:mm:ss", Locale.US).format(Date())
        val dm = c.resources.displayMetrics
        val lang = Prefs.lang(c)
        val app = StringBuilder()
            .append("Titulky PS5 ").append(BuildInfo.VERSION).append(" (").append(BuildInfo.VERSION_CODE).append(") · ").append(now).append('\n')
            .append("Zařízení: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(" · Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            .append("Obrazovka: ").append(dm.widthPixels).append('×').append(dm.heightPixels)
            .append(", televize: ").append(Prefs.isTv(c)).append(", procesor: ").append(Build.SUPPORTED_ABIS.joinToString("/")).append('\n')
            .append("Síť: ").append(NetInfo.kind(c)).append('\n')
            .append("Jazyk: ").append(lang.code).append(", aplikace: ").append(Prefs.uiLang(c).code).append(", hra: ").append(Prefs.gameId(c)).append('\n')
            .append("Usnadnění: ").append(TitulkyAccessibility.instance != null)
            .append(", překryv: ").append(try { Settings.canDrawOverlays(c) } catch (_: Throwable) { "?" })
            .append(", okna: ").append(OverlayWindows.count()).append(" (nepovedlo se ").append(OverlayWindows.failures).append(')')
            .append(", model: ").append(OfflineTranslator.isReady(lang)).append('\n')
            .append("Automatika: ").append(Prefs.autoZone(c)).append(", oblast: ").append(Prefs.layout(c).serialize()).append('\n')
            .append(optionsLine(c)).append('\n')
            .append("Licence: ").append(Prefs.license(c).kind).append(", televize: ").append(Prefs.tvType(c).key)
            .append(cz.recaption.core.TvSender.lastSeen?.announce?.let { a -> " (ohlásila se: ${a.platform ?: "?"} ${a.version ?: "?"})" } ?: "")
            .append('\n').append(DeeplTranslator.diagLine(c))
            .toString()

        val st = TranslatorService.linkStatus()
        val host = Prefs.ps5Host(c)
        val conn = StringBuilder()
        val console = Prefs.console(c)
        LinkStatus.details(Prefs.uiLang(c), st, console).forEach { conn.append(it).append('\n') }
        conn.append('\n')
            .append("Konzole: ").append(console.code).append(if (TranslatorService.xbox) " (čte se z Xboxu)" else "").append('\n')
            .append("Xbox: ").append(if (XboxAccount.signedIn(c)) "přihlášeno" else "nepřihlášeno")
            .append(XboxAccount.consoleId(c)?.let { ", vybraný " + XboxAccount.consoleName(c) } ?: "")
            .append(", spojení: ").append(XboxLink.state).append(XboxLink.reason?.let { " ($it)" } ?: "").append('\n')
            .append("PlayStation: ").append(if (host != null) "spárováno (${host.name}, ${host.ip})" else "nespárováno").append('\n')
            .append("Spojení: ").append(Ps5Link.state).append(Ps5Link.reason?.let { " ($it)" } ?: "")
            .append(", stav: ").append(st.stage).append(", ukázka: ").append(TranslatorService.demo).append('\n')
            .append("Snímků: ").append(TranslatorService.framesDecoded).append(", obraz: ").append(TranslatorService.videoSize.ifEmpty { "–" })
            .append(", čtení: ").append(TranslatorService.readMs).append(" ms")
            .append(", zaseknutí čtení: ").append(TranslatorService.ocrHangsTotal).append('\n')
            .append("Knihovna: ").append(Ps5Link.selfTest())
            .append(Ps5Link.recentEndings().takeIf { it.isNotEmpty() }?.let { "\nPlayStation ukončil spojení:\n  " + it.joinToString("\n  ") } ?: "")
            .append(TranslatorService.startError?.let { "\nChyba spuštění: $it" } ?: "")

        val crash = CrashLog.last(c)
        // ze souboru (i z minulých spuštění) + párování a přihlášení, které jsou jen v paměti
        val pairing = DiagLog.dump().split('\n').filter { l ->
            listOf("PSN|", "PAIR|", "LOGIN|", "XBOX|", "ERROR").any { l.contains(it) }
        }.takeLast(20)
        val events = (EventFile.tail(50).lines() + pairing).filter { it.isNotBlank() }.joinToString("\n")

        // záznam z aplikace v televizi LG / Samsung (přichází po síti, viz core/TvLog.kt)
        val tvLog = TvLog.read()
        val tvTail = TvLog.tail(tvLog, 25)

        val sections = listOf(
            Section(c.tx("dg_app"), clean(app)),
            Section(c.tx("dg_conn"), clean(conn.toString())),
            Section(c.tx("dg_crash"), crash?.let { clean(it) } ?: c.tx("dg_nocrash"), mono = crash != null),
            Section(c.tx("dg_events"), events.ifBlank { null }?.let { clean(it) } ?: c.tx("dg_noevents"), mono = events.isNotBlank()),
            Section(c.tx("zz_tv"), tvTail.ifBlank { null }?.let { clean(it) } ?: c.tx("zz_notv"), mono = tvTail.isNotBlank()),
        )

        // soubor: navíc všechny uložené pády, události z paměti a konec záznamu spojení s konzolí
        val parts = mutableListOf(
            "aplikace a zařízení" to app,
            "spojení" to conn.toString(),
            "pády" to (CrashLog.all(c) ?: "žádné"),
            "záznam (i z minulých spuštění)" to EventFile.tail(200),
            "poslední události" to DiagLog.dump(),
        )
        try {
            val f = Ps5Link.logFile(c)
            // bez opakovaných „Heartbeat“ a výpisů bajtů běžícího spojení – jinak zaplnily celý oddíl a důvod
            // odmítnutí spojení z dřívějška se do hlášení nevešel (hlášení kolegy majitele, 20. 11. 2026)
            if (f.exists()) parts += "spojení s PlayStationem" to f.readText().takeLast(200_000).lines()
                .filterNot { "Heartbeat" in it || "CTRL RECEIVED" in it || it.startsWith("[I] offset") || Regex("^\\[[A-Z]]\\s+[0-9a-f]+ ( [0-9a-f]{2})+").containsMatchIn(it) }
                .joinToString("\n").takeLast(6000)
        } catch (_: Exception) {}
        parts += "televize (záznam z aplikace v televizi)" to TvLog.tail(tvLog, 400).ifBlank { "nic nepřišlo" }
        // ladění profilů her: titulky s překladem a co bylo v oblasti čtení (jen text ze hry, nic osobního)
        val hm = SimpleDateFormat("HH:mm:ss", Locale.US)
        parts += "titulky ze hry (hra: ${Prefs.gameId(c)}, odhad: ${Prefs.gameGuess(c) ?: "–"})" to
            cz.recaption.core.SubtitleHistory.all().reversed().joinToString("\n") { h ->
                hm.format(Date(h.time)) + "  " + h.original.replace('\n', ' ') + "\n          → " + h.translated.replace('\n', ' ')
            }.ifBlank { "zatím nic" }
        parts += "barvy jmen ze hry (pro profil hry)" to cz.recaption.core.NameColor.report()
        parts += "co aplikace četla v oblasti titulků" to
            cz.recaption.core.ScreenReads.all().joinToString("\n") { r ->
                hm.format(Date(r.time)) + (if (r.count > 1) " ×${r.count}" else "") + "  [" + r.verdict + "]  " + r.raw
            }.ifBlank { "zatím nic" }
        val cleanParts = parts.map { (t, s) -> t to clean(s) }
        return Report(sections, LogShare.text(cleanParts, secrets), cleanParts)
    }

    /** Uloží celé hlášení do souboru; vrací cestu (nebo „?“). */
    fun save(c: Context, r: Report): String = try {
        val dir = c.getExternalFilesDir(null) ?: c.filesDir
        val f = File(dir, "TitulkyPS5-hlaseni.txt")
        f.writeText(r.full, Charsets.UTF_8)
        f.absolutePath
    } catch (_: Exception) { "?" }
}
