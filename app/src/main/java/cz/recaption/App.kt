package cz.recaption

import android.app.Application
import cz.recaption.core.TvLog
import java.io.File

/**
 * Aplikace jako celek – vzniká jako první, ještě před obrazovkou, službou překladu i Usnadněním.
 * Proto se tu hned zapíná zápis pádů ([CrashLog]), ať se zachytí pád v kterékoli části.
 * Taky sem patří soubor se záznamem z televize ([TvLog]) a obnovení záznamu v síti
 * ([NetLogShare]), když ho uživatel zapnul a ještě nevypršel.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        TvLog.file = File(filesDir, TvLog.FILE)
        // titulky ke čtení v telefonu: historie v souboru, posledních pár do paměti na pozadí
        cz.recaption.core.ReadFeed.file = File(filesDir, cz.recaption.core.ReadFeed.FILE)
        cz.recaption.core.ReadFeed.load()
        // testerské APK ze zkušebního kanálu zapne testera (Tester.kt)
        try { Tester.refresh(this) } catch (_: Throwable) {}
        // z čeho se překládá (volba „Překládat z“, výchozí angličtina) – offline překladač ho potřebuje od začátku
        try { OfflineTranslator.source = Prefs.sourceLang(this) } catch (_: Throwable) {}
        try { NetLogShare.resume(this) } catch (_: Throwable) {}
        // stažený seznam her (profily), když je novější než vestavěný – jinak zůstává vestavěný
        GameListUpdate.load(this)
        // čtvereček „Podpořit ReCaption“ pro aplikaci v TV LG/Samsung (zpráva podpora) – jednou, na pozadí
        Thread({
            try {
                val bmp = QrImage.bitmap(cz.recaption.core.HelpLinks.SUPPORT) ?: return@Thread
                val out = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                cz.recaption.core.TvSender.support = cz.recaption.core.TvSender.supportMessage("data:image/png;base64," +
                    android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP))
            } catch (_: Throwable) {}
        }, "tp-kafe").start()
    }
}
