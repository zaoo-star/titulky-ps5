package cz.recaption

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import cz.recaption.core.I18n

/**
 * Černý displej telefonu při překladu (od 1.0.5, přání majitele – nápad z GlossaSubu).
 * Telefon jako čtečka: displej zůstane technicky zapnutý (čtení obrazu se nezpomalí jako po zhasnutí),
 * ale je celý černý s nejnižším jasem – OLED nesvítí, telefon nehřeje a nebliká na stole.
 * Otevírá ho tlačítko ☾ na Domů a na stránce Titulky ke čtení (jen telefon, jen při běžícím překladu).
 * Zpět (na stránku, odkud se přišlo): jedno klepnutí na displej nebo tlačítko Zpět (od 1.0.5, přání majitele;
 * dřív dvě klepnutí). Nápověda „Klepnutím zpět“ se ukáže na 3 s po otevření. Když překlad skončí, zavře se samo.
 */
class BlackScreenActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var hint: TextView
    private val hideHint = Runnable { hint.visibility = View.INVISIBLE }
    /** Překlad skončil → nemá smysl držet černý displej. */
    private val watch = object : Runnable {
        override fun run() {
            if (!TranslatorService.running) { finish(); return }
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 0.01f }
        hint = TextView(this).apply {
            text = I18n.t(Prefs.uiLang(this@BlackScreenActivity), "bs_hint")
            setTextColor(Color.rgb(0x60, 0x60, 0x60))
            textSize = 16f
            gravity = Gravity.CENTER
            visibility = View.VISIBLE
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        setContentView(root)
        hideBars()
        handler.postDelayed(hideHint, 3000)
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    override fun onResume() {
        super.onResume()
        hideBars()
        handler.post(watch)
    }

    override fun onPause() {
        handler.removeCallbacks(watch)
        super.onPause()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // jedno klepnutí = zpět (ACTION_UP, ať se klepnutí nepropíše do stránky pod tím)
        if (event.action == MotionEvent.ACTION_UP) finish()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
