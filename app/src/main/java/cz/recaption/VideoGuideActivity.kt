package cz.recaption

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import cz.recaption.core.VideoGuide
import java.net.HttpURLConnection
import java.net.URL

/**
 * Videonávod přes celou obrazovku (od 0.97.71, stránka Videonávody v menu). Video se přehrává rovnou
 * ze stránek s pomocí (`core/VideoGuide`): klip ke kroku průvodce v [EXTRA_ID] (bez něj se nic neotevře);
 * česky pro češtinu a slovenštinu, jinak anglicky.
 * Bez knihoven – `VideoView` + `MediaController`.
 * Ovládání: OK / Přehrát = pauza a pokračování, ◀ ▶ = o 10 s zpět / dopředu, Zpět = zavřít; v telefonu prstem.
 * Než se video rozběhne, je vidět náhled a točící se kolečko; bez internetu krátká chyba s adresou videa.
 */
class VideoGuideActivity : Activity() {

    companion object {
        /** Jak dlouho čekat na spojení, než se ukáže chyba. */
        private const val LOAD_TIMEOUT_MS = 30_000L
        /** Posun šipkou vlevo / vpravo. */
        private const val SEEK_MS = 10_000
        /** Který klip přehrát (id podle [cz.recaption.core.VideoSteps.ID]). */
        const val EXTRA_ID = "video"

        /**
         * Běží aplikace na televizi nebo boxu (videa pro TV), nebo v telefonu / tabletu (videa pro telefon)?
         * Podle zařízení samotného – v telefonu v režimu „Nastavuji TV“ tedy jako telefon.
         * Kromě [Prefs.isTv] (televize s vestavěným Androidem) i boxy a Chromecast (leanback, režim TV).
         */
        fun onTv(c: Context): Boolean = Prefs.isTv(c) || try {
            val pm = c.packageManager
            pm.hasSystemFeature("android.software.leanback") || pm.hasSystemFeature("android.hardware.type.television") ||
                (c.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
        } catch (_: Throwable) { false }

        fun open(c: Context, id: String? = null) {
            try {
                c.startActivity(Intent(c, VideoGuideActivity::class.java).apply { if (id != null) putExtra(EXTRA_ID, id) })
            } catch (_: Exception) {}
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var video: VideoView
    private lateinit var controller: MediaController
    private lateinit var poster: ImageView
    private lateinit var spinner: ProgressBar
    private lateinit var error: TextView
    private lateinit var url: String
    private var tv = false
    /** Video je připravené (spojení se podařilo). */
    private var prepared = false
    private var failed = false
    /** Kde video stálo, když aplikace odešla do pozadí. */
    private var position = 0
    /** Aplikace už jednou odešla do pozadí (po návratu se video rozjede znovu). */
    private var paused = false
    @Volatile private var closed = false

    private val timeout = Runnable { if (!prepared) fail() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tv = onTv(this)
        val lang = Prefs.uiLang(this)
        // jen krátké klipy ke krokům průvodce (VideoSteps.ID); celá videa jsou od 1.00-rc14 zrušená
        val id = intent?.getStringExtra(EXTRA_ID)?.takeIf { cz.recaption.core.VideoSteps.ID.matches(it) } ?: run { finish(); return }
        VideoStepList.load(this) {}   // uložený seznam (i po obnovení procesu přímo do přehrávače) – kvůli jen_cs
        val csOnly = id in VideoStepList.csOnly
        url = VideoGuide.video(id, lang, csOnly)
        val ui = Ui(this)
        val match = FrameLayout.LayoutParams.MATCH_PARENT
        val wrap = FrameLayout.LayoutParams.WRAP_CONTENT

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        // VideoView si při pevné velikosti sám drží poměr stran; na střed
        video = VideoView(this)
        root.addView(video, FrameLayout.LayoutParams(match, match, Gravity.CENTER))
        poster = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        root.addView(poster, FrameLayout.LayoutParams(match, match))
        spinner = ProgressBar(this, null, android.R.attr.progressBarStyleLarge).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(Theme.ACCENT)
        }
        root.addView(spinner, FrameLayout.LayoutParams(wrap, wrap, Gravity.CENTER))
        error = ui.text(tx("vg_error") + "\n\n" + url, 18f, Theme.TEXT).apply {
            gravity = Gravity.CENTER
            setPadding(ui.dp(32), ui.dp(24), ui.dp(32), ui.dp(24))
            visibility = View.GONE
            // v telefonu jde adresa otevřít v prohlížeči klepnutím
            if (!tv) setOnClickListener {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
            }
        }
        root.addView(error, FrameLayout.LayoutParams(match, wrap, Gravity.CENTER))
        setContentView(root)

        // Ovládací lišta: Zpět ji nejen schová, ale rovnou zavře video (na TV jinak dvakrát Zpět)
        controller = object : MediaController(this@VideoGuideActivity) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) this@VideoGuideActivity.finish()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        video.setMediaController(controller)
        video.setOnPreparedListener {
            prepared = true
            handler.removeCallbacks(timeout)
            spinner.visibility = View.GONE
            // záloha, kdyby zařízení neohlásilo první snímek
            handler.postDelayed({ poster.visibility = View.GONE }, 1500)
        }
        video.setOnInfoListener { _, what, _ ->
            when (what) {
                MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> { poster.visibility = View.GONE; spinner.visibility = View.GONE }
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> spinner.visibility = View.VISIBLE
                MediaPlayer.MEDIA_INFO_BUFFERING_END -> spinner.visibility = View.GONE
            }
            false
        }
        video.setOnCompletionListener { position = 0; controller.show(0) }
        video.setOnErrorListener { _, _, _ -> fail(); true }   // true = bez systémového okna „Video nelze přehrát“

        video.setVideoURI(Uri.parse(url))
        video.start()
        handler.postDelayed(timeout, LOAD_TIMEOUT_MS)
        loadPoster(VideoGuide.poster(id, lang, csOnly))
    }

    /** Video se nenačetlo: krátká chyba s adresou (na TV k opsání, v telefonu klepnutím do prohlížeče). */
    private fun fail() {
        if (failed || closed) return
        failed = true
        handler.removeCallbacks(timeout)
        try { video.stopPlayback() } catch (_: Exception) {}
        try { controller.hide() } catch (_: Exception) {}
        video.visibility = View.GONE
        poster.visibility = View.GONE
        spinner.visibility = View.GONE
        error.visibility = View.VISIBLE
    }

    private fun togglePlay() {
        if (video.isPlaying) { video.pause(); controller.show(0) } else { video.start(); controller.show() }
    }

    private fun seek(delta: Int) {
        if (!prepared) return
        val to = (video.currentPosition + delta).coerceIn(0, (video.duration - 1000).coerceAtLeast(0))
        video.seekTo(to)
        controller.show()
    }

    /**
     * Ovladač TV, když ovládací lišta není vidět (s viditelnou lištou klávesy dostane ona: OK na tlačítku
     * pauzy, Zpět zavře – viz [controller]).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (failed) return super.dispatchKeyEvent(event)
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                if (down && event.repeatCount == 0) togglePlay()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { if (down && !video.isPlaying) { video.start(); controller.show() }; return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (down && video.isPlaying) { video.pause(); controller.show(0) }; return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { if (down) seek(-SEEK_MS); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (down) seek(SEEK_MS); return true }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MENU -> { if (down) controller.show(); return true }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        paused = true
        if (!failed && prepared) { position = video.currentPosition; video.pause() }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        // po návratu z pozadí VideoView spojení otevře znovu – pokračovat, kde video stálo
        if (paused && !failed) {
            if (position > 0) video.seekTo(position)
            video.start()
            // kolečko jen když se spojení otevírá znovu (přehrávač už běží = bez čekání)
            spinner.visibility = if (video.isPlaying) View.GONE else View.VISIBLE
        }
    }

    override fun onDestroy() {
        closed = true
        handler.removeCallbacksAndMessages(null)
        try { video.stopPlayback() } catch (_: Exception) {}
        super.onDestroy()
    }

    /** Náhled videa (obrázek vedle videa), než se rozběhne. Když se nestáhne, nic se neděje. */
    private fun loadPoster(address: String) = Thread {
        val bmp: Bitmap? = try {
            val conn = URL(address).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            try {
                if (conn.responseCode == 200) conn.inputStream.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
                } else null
            } finally { conn.disconnect() }
        } catch (_: Exception) { null }
        if (bmp != null) runOnUiThread {
            if (!closed && !failed && !prepared) { poster.setImageBitmap(bmp); poster.visibility = View.VISIBLE }
        }
    }.start()
}
