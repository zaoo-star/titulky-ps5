package cz.recaption

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import cz.recaption.core.DiagLog
import cz.recaption.core.LogServer
import cz.recaption.core.LogShare
import cz.recaption.core.SnapBatches
import java.io.File

/**
 * Záznam pro vývojáře v domácí síti (Nastavení → Experimentální → Ladění a hlášení → „Zpřístupnit záznam v domácí síti“, výchozí
 * vypnuto). Zapnuté = na portu [LogShare.PORT] běží [LogServer] s krátkým kódem; adresu i kód
 * ukáže řádek v Nastavení a obrazovka hlášení. Po [LogShare.DURATION_MS] se samo vypne (i když
 * mezitím aplikace spadla a znovu se spustila – konec platnosti je uložený).
 * Obsah = celé hlášení o stavu ([DiagReport], vše přes Redact).
 * Navíc: Kopírovat / Sdílet hlášení z obrazovky hlášení ([copy], [share]).
 */
object NetLogShare {
    private const val PREFS = "zaznam_sit"
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var server: LogServer? = null
    private val autoOff = Runnable { appCtx?.let { disable(it) } }
    @Volatile private var appCtx: Context? = null

    enum class Result { OK, NO_NETWORK, FAILED }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun until(c: Context) = prefs(c).getLong("until", 0L)
    private fun now() = System.currentTimeMillis()

    /** Je záznam v síti zapnutý (a ještě nevypršel)? */
    fun active(c: Context): Boolean = server?.running == true && LogShare.active(until(c), now())

    /** Adresa pro vývojáře (null = vypnuto nebo zařízení není v domácí síti). */
    fun url(c: Context): String? {
        rebindIfMoved(c)
        val s = server?.takeIf { active(c) } ?: return null
        val ip = NetInfo.homeAddress(c) ?: return null
        return LogShare.url(ip, s.port, s.code)
    }

    /** Adresa stránky pro telefon (čtvereček na obrazovce hlášení); null = vypnuto nebo mimo domácí síť. */
    fun pageUrl(c: Context): String? {
        rebindIfMoved(c)
        val s = server?.takeIf { active(c) } ?: return null
        val ip = NetInfo.homeAddress(c) ?: return null
        return LogShare.pageUrl(ip, s.port, s.code)
    }

    /**
     * Hlášení do telefonu: zapne záznam v síti (když neběží) a ukáže čtvereček na stránku pro telefon.
     * Telefon na stejné Wi-Fi si hlášení stáhne jako soubor a hráč ho pošle autorovi chatem nebo e-mailem.
     */
    fun showQr(a: Activity, ui: Ui) {
        if (!active(a)) {
            val r = enable(a)
            if (r != Result.OK) {
                Toast.makeText(a, a.tx(if (r == Result.NO_NETWORK) "zz_nonet" else "zz_fail"), Toast.LENGTH_LONG).show()
                return
            }
        }
        val url = pageUrl(a) ?: return
        val box = ui.vbox().apply { setBackgroundColor(Theme.BG); setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(8)) }
        QrImage.bitmap(url)?.let { qr ->
            box.addView(android.widget.ImageView(a).apply { setImageBitmap(qr); setBackgroundColor(android.graphics.Color.WHITE); setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8)) },
                android.widget.LinearLayout.LayoutParams(ui.dp(240), ui.dp(240)).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL })
        }
        box.addView(ui.text(a.tx("zz_qr_text"), 16f, Theme.TEXT), ui.lp(top = 12))
        box.addView(ui.text(url, 13f, Theme.MUTED), ui.lp(top = 6))
        try {
            android.app.AlertDialog.Builder(a).setTitle(a.tx("zz_qr_title")).setView(box)
                .setPositiveButton(a.tx("dg_close")) { d, _ -> d.dismiss() }.show()
        } catch (_: Exception) {}
    }

    fun minutesLeft(c: Context): Long = LogShare.minutesLeft(until(c), now())

    /** Zapne na [LogShare.DURATION_MS]. */
    @Synchronized fun enable(c: Context): Result {
        if (NetInfo.homeAddress(c) == null) return Result.NO_NETWORK
        val code = LogShare.newCode()
        val until = now() + LogShare.DURATION_MS
        prefs(c).edit().putLong("until", until).putString("code", code).apply()
        return if (start(c, code, until)) { TranslatorService.refreshNotification(); Result.OK } else { disable(c); Result.FAILED }
    }

    @Synchronized fun disable(c: Context) {
        prefs(c).edit().remove("until").remove("code").apply()
        main.removeCallbacks(autoOff)
        server?.stop()
        server = null
        DiagLog.add("ZAZNAM|off")
        TranslatorService.refreshNotification()   // tlačítko Záznam snímků v notifikaci zmizí
    }

    /** Po spuštění aplikace: bylo zapnuté a ještě nevypršelo → pustit znovu se stejným kódem. */
    @Synchronized fun resume(c: Context) {
        val u = until(c)
        val code = prefs(c).getString("code", null)
        if (u == 0L) return
        if (!LogShare.active(u, now()) || !LogShare.validCode(code)) { disable(c); return }
        start(c, code!!, u)
    }

    /** Po změně sítě (volá služba): server přesunout na novou adresu zařízení, když se změnila. */
    fun refresh(c: Context) = try { rebindIfMoved(c) } catch (_: Throwable) {}

    /** Adresa, na které server právě poslouchá (null = všude). */
    private var boundIp: String? = null

    /**
     * Telefon dostal v síti jinou adresu (přeskok mezi pásmy Wi-Fi, nové přidělení routerem): server by dál
     * poslouchal na staré, která už neexistuje, a vývojář by se nepřipojil, i když aplikace ukazuje „zapnuto“
     * s novou adresou (0.97: 192.168.3.76 → .42). Pustit znovu na nové adrese se stejným kódem.
     */
    @Synchronized private fun rebindIfMoved(c: Context) {
        if (server?.running != true) return
        val now = NetInfo.homeAddress(c) ?: return
        if (now == boundIp) return
        val code = prefs(c).getString("code", null) ?: return
        val u = until(c)
        if (!LogShare.active(u, now())) return
        DiagLog.add("ZAZNAM|adresa $boundIp → $now")
        start(c, code, u)
    }

    private fun start(c: Context, code: String, end: Long): Boolean {
        val ctx = c.applicationContext
        appCtx = ctx
        server?.stop()
        val s = LogServer(
            code,
            parts = { DiagReport.build(ctx).parts },
            secrets = { DiagReport.secrets(ctx) },
            open = { LogShare.active(until(ctx), now()) },
            onStop = { main.post { disable(ctx) } },
            log = { DiagLog.add("ZAZNAM|$it") },
            lang = { Prefs.uiLang(ctx) },
            image = { TranslatorService.stillJpeg() },
            // záznam snímků (⚙ → Ladění a hlášení → Záznam snímků): seznam dávek a ZIP jedné dávky
            batches = { SnapBatches.json(SnapBatches.scan(SnapRecorder.root(ctx)), SnapRecorder.recording) },
            batchDir = { n -> SnapBatches.find(SnapRecorder.root(ctx), n) },
        )
        // poslouchat jen na adrese domácí sítě (ne na mobilních datech); neznámá = všude, ptát se
        // stejně smí jen domácí síť (LogShare.allowedPeer)
        val ip = NetInfo.homeAddress(ctx)
        val bind = ip?.let { runCatching { java.net.InetAddress.getByName(it) }.getOrNull() }
        if (!s.start(LogShare.PORT, bind)) return false
        server = s
        boundIp = ip
        main.removeCallbacks(autoOff)
        main.postDelayed(autoOff, LogShare.remainingMs(end, now()))
        return true
    }

    /**
     * Řádek do Nastavení (jeden přepínač; když je zapnuto, pod ním adresa a zbývající čas).
     * Celé je to jeden pohled, aby šel vložit jedním voláním.
     */
    fun row(a: Activity, ui: Ui, rerender: () -> Unit): View {
        val box = ui.vbox()
        val on = active(a)
        box.addView(ui.settingRow(a.tx("zz_toggle"), a.tx(if (on) "on" else "off")) {
            if (active(a)) disable(a)
            else when (enable(a)) {
                Result.OK -> {}
                Result.NO_NETWORK -> Toast.makeText(a, a.tx("zz_nonet"), Toast.LENGTH_LONG).show()
                Result.FAILED -> Toast.makeText(a, a.tx("zz_fail"), Toast.LENGTH_LONG).show()
            }
            rerender()
        }.apply { tag = "zaznamsit" })
        val u = url(a)
        if (!on || u == null) {
            box.addView(ui.text(a.tx("zz_desc"), 14f, Theme.MUTED), ui.lp(top = 6))
            return box
        }
        // IP, port a kód velkým písmem pod sebou – majitel je čte z obrazovky a diktuje (od 0.97.38);
        // kdy se to vypne až pod tím, celá adresa pro vývojáře úplně dole
        val uri = try { android.net.Uri.parse(u) } catch (_: Throwable) { null }
        listOf("zz_ip" to (uri?.host ?: "?"), "zz_port" to (uri?.port?.toString() ?: "?"), "zz_code" to (uri?.getQueryParameter("k") ?: "?"))
            .forEachIndexed { i, (k, v) ->
                box.addView(ui.hbox().apply {
                    addView(ui.text(a.tx(k), 20f, Theme.MUTED), android.widget.LinearLayout.LayoutParams(ui.dp(90), android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))
                    addView(ui.text(v, 26f, Theme.OK, bold = true).apply { letterSpacing = 0.06f })
                }, ui.lp(top = if (i == 0) 10 else 4))
            }
        box.addView(ui.text(a.tx("zz_left").replace("%s", minutesLeft(a).toString()), 14f, Theme.MUTED), ui.lp(top = 12))
        box.addView(ui.text(a.tx("zz_url").replace("%s", u), 12f, Theme.MUTED), ui.lp(top = 14))
        return box
    }

    // ---------------- záloha: schránka a Sdílet ----------------

    fun copy(a: Activity, text: String) {
        try {
            val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("ReCaption", text))
            Toast.makeText(a, a.tx("zz_copied"), Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(a, a.tx("zz_noshare"), Toast.LENGTH_LONG).show()
        }
    }

    /** Pošle hlášení jako textový soubor přes systémové Sdílet ([ReportProvider]). */
    fun share(a: Activity, text: String) {
        try {
            val f = ReportProvider.file(a)
            f.parentFile?.mkdirs()
            f.writeText(text, Charsets.UTF_8)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, ReportProvider.uri(a))
                putExtra(Intent.EXTRA_SUBJECT, "ReCaption " + BuildInfo.VERSION + " – hlášení")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            a.startActivity(Intent.createChooser(send, a.tx("zz_share")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Throwable) {
            Toast.makeText(a, a.tx("zz_noshare"), Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * Vydá jiným aplikacím (jen s povolením ze Sdílet) jediný soubor: poslední sdílené hlášení.
 * Nic jiného nečte a nic nepřijímá.
 */
class ReportProvider : ContentProvider() {
    companion object {
        private const val NAME = "ReCaption-hlaseni.txt"
        fun authority(c: Context) = c.packageName + ".hlaseni"
        fun file(c: Context) = File(File(c.cacheDir, "sdileni"), NAME)
        fun uri(c: Context): Uri = Uri.parse("content://" + authority(c) + "/" + NAME)
        /** Dávka snímků jako ZIP ke sdílení (`SnapSaver`, od 0.97.69) – druhý a poslední soubor, který tu jde ven. */
        private const val ZIP = "ReCaption-snimky.zip"
        fun zipFile(c: Context) = File(File(c.cacheDir, "sdileni"), ZIP)
        fun zipUri(c: Context): Uri = Uri.parse("content://" + authority(c) + "/" + ZIP)
    }

    override fun onCreate() = true

    private fun name(uri: Uri): String? = uri.lastPathSegment?.takeIf { (it == NAME || it == ZIP) && uri.pathSegments.size == 1 }
    private fun fileOf(c: Context, n: String) = if (n == ZIP) zipFile(c) else file(c)

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val c = context ?: return null
        val n = name(uri)
        if (n == null || mode != "r") throw java.io.FileNotFoundException()
        return ParcelFileDescriptor.open(fileOf(c, n), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = when (name(uri)) { NAME -> "text/plain"; ZIP -> "application/zip"; else -> null }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val c = context ?: return null
        val n = name(uri) ?: return null
        val cols = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(cols, 1).apply { addRow(arrayOf<Any>(n, fileOf(c, n).length())) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
