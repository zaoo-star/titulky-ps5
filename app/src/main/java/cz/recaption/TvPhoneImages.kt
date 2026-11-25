package cz.recaption

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Obrázky z telefonu ve hře pro aplikaci v TV LG/Samsung (od 0.97.100, TV 1.5.24): JPEG jako `data:` adresa,
 * zmenšený na nejvýš 240 bodů šířky, kvalita 60; dohromady nejvýš ~45 kB (další obrázky pak jen jako čára).
 */
object TvPhoneImages {
    private const val MAX_W = 240
    private const val BUDGET = 45_000

    fun encode(images: List<Bitmap?>): List<String?> {
        var used = 0
        return images.map { b ->
            if (b == null || b.isRecycled) null else try {
                val s = if (b.width <= MAX_W) b else Bitmap.createScaledBitmap(b, MAX_W, maxOf(1, b.height * MAX_W / b.width), true)
                val out = ByteArrayOutputStream()
                s.compress(Bitmap.CompressFormat.JPEG, 60, out)
                if (s !== b) s.recycle()
                val uri = "data:image/jpeg;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                if (used + uri.length > BUDGET) null else { used += uri.length; uri }
            } catch (_: Throwable) { null }
        }
    }
}
