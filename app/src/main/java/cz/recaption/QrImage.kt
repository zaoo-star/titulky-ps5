package cz.recaption

import android.graphics.Bitmap
import android.graphics.Color

/** QR kód s adresou jako černobílý obrázek (knihovna zxing). */
object QrImage {
    /** null, když se kód nepodaří vytvořit. */
    fun bitmap(text: String): Bitmap? = try {
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0,
            mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
        val n = m.width
        val scale = maxOf(1, 480 / n)
        val px = IntArray(n * scale * n * scale)
        for (y in 0 until n * scale) for (x in 0 until n * scale)
            px[y * n * scale + x] = if (m.get(x / scale, y / scale)) Color.BLACK else Color.WHITE
        Bitmap.createBitmap(px, n * scale, n * scale, Bitmap.Config.ARGB_8888)
    } catch (e: Throwable) { null }
}
