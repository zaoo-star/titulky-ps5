package cz.recaption

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import cz.recaption.core.LoginClient
import cz.recaption.core.PhoneLogin
import cz.recaption.core.SetupPage

/**
 * ReCaption v telefonu: přečte kamerou osmimístné číslo z obrazovky PS5 a pošle ho TV (od 0.97.64). Otevírá ho tlačítko
 * „📷 Načíst kamerou“ na stránce „Nastavení TV“ v prohlížeči telefonu (odkaz `recaption://kod?h=&p=&k=`,
 * [SetupPage.scanLink]); TV pak spáruje sama ([RemoteHost], `op=kod`) a stránka v prohlížeči to po návratu ukáže.
 * Bez vlastního vzhledu: povolení kamery → čtečka [LgScanActivity] (`MODE_PS5`) → odeslat → zavřít (zpět do prohlížeče).
 * Číslo posílá jen na adresu v domácí síti ([SetupPage.parseScanLink]).
 */
class ScanCodeActivity : Activity() {
    private var target: PhoneLogin.Target? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        target = SetupPage.parseScanLink(intent?.dataString)
        if (target == null) { finish(); return }
        if (savedInstanceState != null) return   // po otočení displeje čtečka už běží
        if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scan()
        else requestPermissions(arrayOf(android.Manifest.permission.CAMERA), REQ_CAMERA)
    }

    private fun scan() {
        try {
            @Suppress("DEPRECATION") startActivityForResult(Intent(this, LgScanActivity::class.java)
                .putExtra(LgScanActivity.EXTRA_MODE, LgScanActivity.MODE_PS5), REQ_SCAN)
        } catch (_: Exception) { toast(tx("km_cam_fail")); finish() }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        @Suppress("DEPRECATION") super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_CAMERA) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) scan() else { toast(tx("km_denied")); finish() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SCAN) return
        val pin = SetupPage.pin(data?.getStringExtra(LgScanActivity.EXTRA_CODE))
        val t = target
        if (resultCode != RESULT_OK || pin == null || t == null) {
            if (data?.getBooleanExtra(LgScanActivity.EXTRA_FAILED, false) == true) toast(tx("km_cam_fail"))
            finish(); return
        }
        val app = applicationContext
        Thread({
            val ok = try {
                val (status, body) = LoginClient.request(t.host, t.port, "POST", PhoneLogin.path(t.key),
                    PhoneLogin.form(mapOf("op" to SetupPage.OP_CODE, "pin" to pin.toString().padStart(8, '0'))))
                status == 200 && body.trim() == "OK"
            } catch (_: Throwable) { false }
            runOnUiThread { Toast.makeText(app, app.tx(if (ok) "sp_scan_sent" else "sp_off"), Toast.LENGTH_LONG).show() }
        }, "tp-scan-send").start()
        finish()
    }

    private fun toast(text: String) = Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQ_CAMERA = 51
        private const val REQ_SCAN = 52
    }
}
