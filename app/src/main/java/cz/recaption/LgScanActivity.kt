package cz.recaption

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import cz.recaption.core.LgScan
import cz.recaption.core.LgScanResult
import cz.recaption.core.LgScanVote
import cz.recaption.core.Ps5CodeScan
import cz.recaption.core.Ps5CodeVote

/**
 * Načtení adresy televize a Passphrase z obrazovky LG Developer Mode kamerou telefonu.
 *
 * Kamera přes Camera2 ze systému (žádná knihovna navíc do APK): náhled v TextureView a snímky
 * YUV do ML Kitu (stejné rozpoznávání textu jako čtení titulků). Snímky zůstávají jen v paměti,
 * nic se neukládá ani neposílá a přečtený text se nevypisuje do logu. Co přečíst, rozhoduje
 * core/LgScan.kt; údaj platí, až když ho ML Kit přečte dvakrát stejně (LgScanVote).
 * Výsledek (adresa, Passphrase) vrací průvodci LgInstallActivity k potvrzení.
 * V režimu [MODE_PS5] čte místo toho osmimístné číslo pro spárování z obrazovky PlayStationu
 * (core/Ps5CodeScan.kt) a vrací ho stránce párování v MainActivity; v režimu [MODE_PS5_IP] adresu
 * konzole z obrazovky Nastavení → Síť → Stav připojení (když ji aplikace v síti sama nenašla).
 */
class LgScanActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var texture: TextureView
    private lateinit var status: TextView
    private lateinit var buttons: LinearLayout
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val vote = LgScanVote()
    private var found = LgScanResult()
    private val mode by lazy { intent?.getStringExtra(EXTRA_MODE) }
    /** Režim PlayStation: číslo pro spárování, nebo (MODE_PS5_IP) adresa konzole. */
    private val ps5 by lazy { mode == MODE_PS5 || mode == MODE_PS5_IP }
    private val ps5ip by lazy { mode == MODE_PS5_IP }
    private val codeVote = Ps5CodeVote()
    private var code: String? = null
    private var finished = false

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previewSize = Size(1280, 720)
    private var sensorRotation = 90
    @Volatile private var busy = false
    @Volatile private var paused = false

    private fun t(key: String) = tx(key)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        val root = FrameLayout(this).apply { setBackgroundColor(Theme.BG) }
        texture = TextureView(this)
        root.addView(texture, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val panel = ui.vbox().apply {
            setBackgroundColor(0xE6000000.toInt())
            setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(20))
        }
        panel.addView(ui.text(t(if (ps5ip) "km_aim_ps5ip" else if (ps5) "km_aim_ps5" else "km_aim"), 15f, Theme.MUTED))
        status = ui.text("", 18f, Theme.TEXT, bold = true)
        panel.addView(status, ui.lp(top = 10))
        buttons = ui.vbox()
        panel.addView(buttons, ui.lp(top = 6))
        root.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        setContentView(root)
        texture.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> transform() }
        showFound()
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { finish(); return }
        thread = HandlerThread("tp-lg-scan").also { it.start(); handler = Handler(it.looper) }
        if (texture.isAvailable) open() else texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) = open()
            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) = transform()
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
        }
    }

    override fun onPause() {
        close()
        super.onPause()
    }

    override fun onDestroy() {
        try { recognizer.close() } catch (_: Exception) {}
        super.onDestroy()
    }

    /** Zadní kamera; když není, jakákoli. */
    private fun pickCamera(m: CameraManager): String? {
        val ids = m.cameraIdList
        return ids.firstOrNull { m.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
            ?: ids.firstOrNull()
    }

    /** Největší rozměr do 1920×1080 (drobné písmo Passphrase z dálky potřebuje rozlišení). */
    private fun pickSize(sizes: Array<Size>): Size =
        sizes.filter { it.width <= 1920 && it.height <= 1080 }.maxByOrNull { it.width * it.height } ?: sizes.minByOrNull { it.width * it.height }!!

    @SuppressLint("MissingPermission")
    private fun open() {
        val h = handler ?: return
        try {
            val m = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = pickCamera(m) ?: return fail()
            val ch = m.getCameraCharacteristics(id)
            val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return fail()
            sensorRotation = ch.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val yuv = pickSize(map.getOutputSizes(ImageFormat.YUV_420_888))
            val prev = map.getOutputSizes(SurfaceTexture::class.java)
            // náhled ve stejném poměru stran jako snímky pro čtení
            previewSize = prev.filter { it.width <= 1920 && it.height <= 1080 && it.width * yuv.height == it.height * yuv.width }
                .maxByOrNull { it.width * it.height } ?: pickSize(prev)
            reader = ImageReader.newInstance(yuv.width, yuv.height, ImageFormat.YUV_420_888, 3).also { r ->
                r.setOnImageAvailableListener({ onImage(it) }, h)
            }
            runOnUiThread { transform() }
            m.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(c: CameraDevice) { camera = c; startSession(c) }
                override fun onDisconnected(c: CameraDevice) { c.close(); camera = null }
                override fun onError(c: CameraDevice, error: Int) { c.close(); camera = null; fail() }
            }, h)
        } catch (_: Exception) { fail() }
    }

    @Suppress("DEPRECATION")
    private fun startSession(c: CameraDevice) {
        try {
            val st = texture.surfaceTexture ?: return fail()
            st.setDefaultBufferSize(previewSize.width, previewSize.height)
            val preview = Surface(st)
            val r = reader ?: return
            c.createCaptureSession(listOf(preview, r.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    try {
                        val req = c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(preview); addTarget(r.surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        }
                        s.setRepeatingRequest(req.build(), null, handler)
                    } catch (_: Exception) { fail() }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) = fail()
            }, handler)
        } catch (_: Exception) { fail() }
    }

    /** Otočení snímku pro ML Kit: zadní kamera = (orientace snímače − otočení displeje). */
    @Suppress("DEPRECATION")
    private fun rotation(): Int {
        val deg = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0
        }
        return (sensorRotation - deg + 360) % 360
    }

    private fun onImage(r: ImageReader) {
        val img = try { r.acquireLatestImage() } catch (_: Exception) { null } ?: return
        if (busy || paused || finished) { img.close(); return }
        busy = true
        val input = try { InputImage.fromMediaImage(img, rotation()) } catch (_: Exception) { img.close(); busy = false; return }
        recognizer.process(input)
            .addOnSuccessListener { text ->
                img.close(); busy = false
                if (reader !== r) { r.close(); return@addOnSuccessListener }
                // řádky v pořadí čtení; text se nikam nevypisuje
                val lines = text.textBlocks.flatMap { b -> b.lines.map { it.text } }
                if (ps5) onCode(if (ps5ip) Ps5CodeScan.ip(lines) else Ps5CodeScan.parse(lines)) else onRead(LgScan.parse(lines))
            }
            .addOnFailureListener { img.close(); busy = false; if (reader !== r) r.close() }
    }

    private fun onRead(r: LgScanResult) {
        if (paused || finished) return
        vote.add(r)
        val now = vote.current()
        if (now == found) return
        found = now
        if (found.complete) paused = true
        runOnUiThread { showFound() }
    }

    private fun onCode(c: String?) {
        if (paused || finished) return
        val now = codeVote.add(c)
        if (now == code) return
        code = now
        if (now != null) paused = true
        runOnUiThread { showFound() }
    }

    private fun showFound() {
        if (ps5) {
            status.text = if (ps5ip) t("km_found_ps5ip").replace("%s", code ?: t("km_none"))
                else t("km_found_code").replace("%s", code?.let { it.substring(0, 4) + " " + it.substring(4) } ?: t("km_none"))
            status.setTextColor(Theme.TEXT)
            buttons.removeAllViews()
            if (code != null) buttons.addView(ui.button(t("km_use"), primary = true) { deliver() }, ui.lp(top = 8))
            if (paused) buttons.addView(ui.button(t("km_again")) { codeVote.clear(); code = null; paused = false; showFound() }, ui.lp(top = 8))
            buttons.addView(ui.button(t("wz_back")) { finish() }, ui.lp(top = 8))
            (buttons.getChildAt(0))?.requestFocus()
            return
        }
        val none = t("km_none")
        val sb = StringBuilder()
        sb.append(t("km_found_ip").replace("%s", found.ip ?: none)).append('\n')
        sb.append(t("km_found_pass").replace("%s", found.pass ?: none))
        if (found.passUnsure) sb.append("\n").append(t("km_unsure"))
        status.text = sb.toString()
        status.setTextColor(if (found.passUnsure) Theme.WARN else Theme.TEXT)
        buttons.removeAllViews()
        if (!found.empty) buttons.addView(ui.button(t("km_use"), primary = true) { deliver() }, ui.lp(top = 8))
        if (paused) buttons.addView(ui.button(t("km_again")) { vote.clear(); found = LgScanResult(); paused = false; showFound() }, ui.lp(top = 8))
        buttons.addView(ui.button(t("wz_back")) { finish() }, ui.lp(top = 8))
        (buttons.getChildAt(0))?.requestFocus()
    }

    private fun deliver() {
        if (finished) return
        finished = true
        val data = Intent()
        code?.let { data.putExtra(EXTRA_CODE, it) }
        found.ip?.let { data.putExtra(EXTRA_IP, it) }
        found.pass?.let { data.putExtra(EXTRA_PASS, it) }
        data.putExtra(EXTRA_UNSURE, found.passUnsure)
        setResult(RESULT_OK, data)
        finish()
    }

    private fun fail() {
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_FAILED, true))
            finish()
        }
    }

    private fun close() {
        try { session?.close() } catch (_: Exception) {}
        session = null
        try { camera?.close() } catch (_: Exception) {}
        camera = null
        // snímek, který právě čte ML Kit, se zavře až po přečtení (v onImage)
        if (!busy) try { reader?.close() } catch (_: Exception) {}
        reader = null
        thread?.quitSafely(); thread = null; handler = null
    }

    /** Náhled vyplní obrazovku bez deformace (ořízne okraje) i při otočeném telefonu. */
    @Suppress("DEPRECATION")
    private fun transform() {
        val vw = texture.width.toFloat(); val vh = texture.height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        val rot = windowManager.defaultDisplay.rotation
        val pw = previewSize.width.toFloat(); val ph = previewSize.height.toFloat()
        val m = Matrix()
        val cx = vw / 2f; val cy = vh / 2f
        if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270) {
            val view = RectF(0f, 0f, vw, vh)
            val buf = RectF(0f, 0f, ph, pw)
            buf.offset(cx - buf.centerX(), cy - buf.centerY())
            m.setRectToRect(view, buf, Matrix.ScaleToFit.FILL)
            val s = maxOf(vh / ph, vw / pw)
            m.postScale(s, s, cx, cy)
            m.postRotate(90f * (rot - 2), cx, cy)
        } else {
            // na výšku systém obraz otočí sám; jen oříznout na poměr stran obrazovky
            val bw = ph; val bh = pw
            val s = maxOf(vw / bw, vh / bh)
            m.setScale(bw * s / vw, bh * s / vh, cx, cy)
            if (rot == Surface.ROTATION_180) m.postRotate(180f, cx, cy)
        }
        texture.setTransform(m)
    }

    companion object {
        const val EXTRA_IP = "ip"
        const val EXTRA_PASS = "pass"
        const val EXTRA_UNSURE = "unsure"
        const val EXTRA_FAILED = "failed"
        const val EXTRA_CODE = "code"
        const val EXTRA_MODE = "mode"
        const val MODE_PS5 = "ps5"
        const val MODE_PS5_IP = "ps5ip"

        /** Tlačítko jen tam, kde to dává smysl: zařízení s kamerou, které není televize ani box. */
        fun available(c: Context): Boolean = try {
            val pm = c.packageManager
            val tv = pm.hasSystemFeature("android.software.leanback") ||
                (c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
            !tv && pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        } catch (_: Exception) { false }
    }
}
