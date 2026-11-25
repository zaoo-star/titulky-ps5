package cz.recaption

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import cz.recaption.core.ReportUpload
import cz.recaption.core.SnapBatches
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Uložit snímky do telefonu (od 0.97.69, přání majitele): dávka ze Záznamu snímků leží v soukromé složce aplikace,
 * kam se Galerie ani správce souborů nedostanou. Tady jde ven bez PC a bez schránky na hlášení:
 *  - Sdílet jako ZIP (systémové Sdílet – chat, e-mail, Disk; soubor přes [ReportProvider]),
 *  - Uložit ZIP do Stažených souborů (Download/ReCaption, Android 10+),
 *  - Uložit snímky do Galerie (Pictures/ReCaption/davka-N, Android 10+).
 * Na starším Androidu (bez oprávnění k úložišti) jen Sdílet. Práce mimo hlavní vlákno; ⚙ → Experimentální → Ladění a hlášení.
 */
object SnapSaver {
    private val main = Handler(Looper.getMainLooper())

    fun open(a: Activity) {
        fun t(k: String) = a.tx(k)
        val list = try { ReportUpload.offered(SnapBatches.scan(SnapRecorder.root(a)), SnapRecorder.recording) } catch (_: Throwable) { emptyList() }
        if (list.isEmpty()) { toast(a, t("snap_none")); return }
        val fmt = SimpleDateFormat("d. M. HH:mm", Locale.ROOT)
        val labels = list.map { b ->
            t("snap_batch").replace("%n", b.n.toString()).replace("%t", fmt.format(Date(b.start))).replace("%c", b.snaps.toString())
        }
        try {
            AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(t("snap_row"))
                .setItems(labels.toTypedArray()) { _, i -> actions(a, list[i], labels[i]) }
                .setNegativeButton(t("lg_cancel"), null)
                .show()
        } catch (_: Exception) {}
    }

    private fun actions(a: Activity, b: SnapBatches.Info, label: String) {
        fun t(k: String) = a.tx(k)
        val modern = Build.VERSION.SDK_INT >= 29
        val items = buildList {
            add(t("snap_share_zip") to { work(a) { shareZip(a, b) } })
            if (modern) add(t("snap_save_zip") to { work(a) { saveZip(a, b); t("snap_saved_dl") } })
            if (modern) add(t("snap_save_gallery") to { work(a) { saveGallery(a, b); t("snap_saved_gal") } })
        }
        try {
            AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(label)
                .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
                .setNegativeButton(t("lg_cancel"), null)
                .show()
        } catch (_: Exception) {}
    }

    /** Práce na pozadí; vrátí text k ukázání (null = nic, např. otevřel se dialog Sdílet). */
    private fun work(a: Activity, job: () -> String?) {
        toast(a, a.tx("snap_working"))
        Thread({
            val msg = try { job() } catch (e: Throwable) { EventFile.add("SNAPSAVE|chyba ${e.javaClass.simpleName}"); a.tx("snap_fail") }
            if (msg != null) main.post { toast(a, msg) }
        }, "tp-ulozit-snimky").apply { isDaemon = true; start() }
    }

    private fun dir(a: Activity, b: SnapBatches.Info): File =
        SnapBatches.find(SnapRecorder.root(a), b.n) ?: error("dávka ${b.n} není")

    private fun zipName(b: SnapBatches.Info) = "ReCaption-${b.name}.zip"

    /** ZIP do sdílené složky a systémové Sdílet. */
    private fun shareZip(a: Activity, b: SnapBatches.Info): String? {
        val f = ReportProvider.zipFile(a)
        f.parentFile?.mkdirs()
        f.outputStream().buffered().use { SnapBatches.zip(dir(a, b), it) }
        EventFile.add("SNAPSAVE|sdílet dávku ${b.n} · ${f.length()} B")
        main.post {
            try {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, ReportProvider.zipUri(a))
                    putExtra(Intent.EXTRA_SUBJECT, zipName(b))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                a.startActivity(Intent.createChooser(send, a.tx("snap_share_zip")))
            } catch (_: Exception) { toast(a, a.tx("zz_noshare")) }
        }
        return null
    }

    /** ZIP do Download/ReCaption (MediaStore, Android 10+ bez oprávnění). */
    private fun saveZip(a: Activity, b: SnapBatches.Info) {
        if (Build.VERSION.SDK_INT < 29) error("Android < 10")
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, zipName(b))
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ReCaption")
        }
        val r = a.contentResolver
        val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: error("insert")
        try {
            (r.openOutputStream(uri) ?: error("stream")).buffered().use { SnapBatches.zip(dir(a, b), it) }
        } catch (e: Throwable) { try { r.delete(uri, null, null) } catch (_: Throwable) {}; throw e }
        EventFile.add("SNAPSAVE|ZIP dávky ${b.n} do Stažených")
    }

    /** Snímky do Pictures/ReCaption/davka-N (Galerie, album ReCaption). */
    private fun saveGallery(a: Activity, b: SnapBatches.Info) {
        if (Build.VERSION.SDK_INT < 29) error("Android < 10")
        val r = a.contentResolver
        val snaps = (dir(a, b).listFiles() ?: emptyArray()).filter { it.isFile && SnapBatches.isSnap(it.name) }.sortedBy { it.name }
        for (f in snaps) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${b.name}-${f.name}")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ReCaption/" + b.name)
            }
            val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: error("insert")
            try { (r.openOutputStream(uri) ?: error("stream")).use { out -> f.inputStream().use { it.copyTo(out) } } }
            catch (e: Throwable) { try { r.delete(uri, null, null) } catch (_: Throwable) {}; throw e }
        }
        EventFile.add("SNAPSAVE|${snaps.size} snímků dávky ${b.n} do Galerie")
    }

    private fun toast(a: Activity, s: String) = try { Toast.makeText(a, s, Toast.LENGTH_LONG).show() } catch (_: Throwable) {}
}
