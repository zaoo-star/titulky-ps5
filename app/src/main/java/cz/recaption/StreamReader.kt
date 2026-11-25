package cz.recaption

import android.graphics.Bitmap
import cz.recaption.core.FrameSignature
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import cz.recaption.core.Yuv
import kotlin.math.min

/**
 * Čtečka videa: dostává snímky videa (H.264) tak, jak chodí z PlayStationu po síti,
 * dekóduje je a drží poslední obrázek připravený pro čtení titulků.
 * Nic se nezobrazuje ani nepřehrává – obraz i zvuk hry jdou do televize kabelem HDMI.
 */
class StreamReader(private val width: Int, private val height: Int) {

    companion object {
        private const val TAG = "TestPreklad"
        private const val MIME = "video/avc"
        /** Jak dlouho po výměně dekodéru čekat na klíčový snímek (pak dekódovat dál jako dřív). */
        private const val KEY_WAIT_MS = 5000L
    }

    private val thread = HandlerThread("tp-video").apply {
        start()
        // dekódování videa s vyšší prioritou (čtení titulků čeká na nový snímek); chyba se přejde, vlákno běží dál
        try { android.os.Handler(looper).post { ReadPriority.raiseCurrent() } } catch (_: Throwable) {}
    }
    private val handler = Handler(thread.looper)
    private var reader: ImageReader? = null
    private var codec: MediaCodec? = null
    private val codecLock = Any()
    private val info = MediaCodec.BufferInfo()
    private var pts = 0L

    /** Poslední snímek s hlavičkou videa (SPS/PPS) – při obnově dekodéru se pošle znovu (od 0.97.102 nejnovější, ne první). */
    private var header: ByteArray? = null

    /**
     * Dekodér se vyměnil (výjimka, nevydával obraz) a čeká se na klíčový snímek (od 0.97.102, tester QBYZ-GVHV: po výměně
     * dostával jen změny proti snímku, který nikdy neviděl, a obraz zůstal napořád zelený s kostičkami). Mezitím se
     * snímky dekodéru nedávají a [Ps5Link] si klíčový snímek od konzole vyžádá. Když nepřijde do [KEY_WAIT_MS], dekóduje
     * se dál jako dřív.
     */
    @Volatile var needKey = false
        private set
    private var needKeySince = 0L
    private var keySkipped = 0

    private val frameLock = Any()
    private var yBuf = ByteArray(0)
    private var yStride = 0
    private var frameW = 0
    private var frameH = 0
    private var frameSerial = 0L
    private var grabbedSerial = -1L
    private var argb = IntArray(0)
    private var bmp: Bitmap? = null
    private var lastColorAt = 0L

    /** Počet dekódovaných snímků a čas posledního (pro diagnostiku a hlídání spojení). */
    @Volatile var decoded = 0L
        private set
    @Volatile var lastFrameAt = 0L
        private set
    @Volatile var fed = 0L
        private set
    @Volatile var lastError: String? = null
        private set
    @Volatile var decoderName: String = "?"
        private set

    /**
     * Žádost o barvu: obdélník v zlomcích 0..1 celého obrazu (viz [requestColor]). Bez [done] je to barva jména
     * (výsledek v [colorResult]); s [done] (barevná slova úkolu) se výsledek předá rovnou jí (vlákno dekodéru).
     */
    class ColorRequest(val name: String, val left: Float, val top: Float, val right: Float, val bottom: Float,
                       val done: ((Int?, Long) -> Unit)? = null)

    /** Vyřídí se všechny u nejbližšího snímku s barvami, pak se smažou – jinak se barvy vůbec nečtou. */
    private val colorRequests = ArrayList<ColorRequest>()
    @Volatile private var colorPending = false

    /** Výsledek: jméno a barva (null = jméno bez barvy) a jak dlouho čtení trvalo (µs). */
    @Volatile var colorResult: Triple<String, Int?, Long>? = null

    /**
     * Barva z obrazu (od 0.97.28): přečte se jen jednou u nejbližšího snímku, jen v obdélníku. Barva jména nahradí
     * dosud nevyřízenou žádost o jméno; žádosti s [ColorRequest.done] (slova úkolu) se přidají (nejvýš 24 čekajících).
     */
    fun requestColor(r: ColorRequest) = synchronized(colorRequests) {
        if (r.done == null) colorRequests.removeAll { it.done == null }
        colorRequests.add(r)
        while (colorRequests.size > 24) colorRequests.removeAt(0)
        colorPending = true
    }

    /** Čekající žádosti o barvu (a smazat je), nebo null – kontrola za snímek je jen jedna proměnná. */
    private fun takeColorRequests(): List<ColorRequest>? {
        if (!colorPending) return null
        return synchronized(colorRequests) {
            colorPending = false
            colorRequests.toList().also { colorRequests.clear() }
        }
    }

    /** Výsledek jedné žádosti: slovům úkolu do [ColorRequest.done], jménu do [colorResult]. */
    private fun deliverColor(req: ColorRequest, rgb: Int?, micros: Long) {
        val d = req.done
        if (d != null) try { d(rgb, micros) } catch (_: Throwable) {} else colorResult = Triple(req.name, rgb, micros)
    }

    /**
     * Projde obdélník po krocích (nejvýš ~48×16 bodů) a předá barvu písma ([cz.recaption.core.NameColor.fromSamples]);
     * [y], [u], [v] čtou jeden bod.
     */
    private inline fun sampleColor(req: ColorRequest, w: Int, h: Int, y: (Int, Int) -> Int, u: (Int, Int) -> Int, v: (Int, Int) -> Int) {
        val t0 = System.nanoTime()
        val x0 = (req.left * w).toInt().coerceIn(0, w - 1); val x1 = (req.right * w).toInt().coerceIn(x0 + 1, w)
        val y0 = (req.top * h).toInt().coerceIn(0, h - 1); val y1 = (req.bottom * h).toInt().coerceIn(y0 + 1, h)
        val sx = maxOf(1, (x1 - x0) / 48); val sy = maxOf(1, (y1 - y0) / 16)
        val n = ((x1 - x0) / sx + 1) * ((y1 - y0) / sy + 1)
        val ys = IntArray(n); val us = IntArray(n); val vs = IntArray(n)
        var k = 0
        var yy = y0
        while (yy < y1) {
            var xx = x0
            while (xx < x1 && k < n) { ys[k] = y(xx, yy); us[k] = u(xx / 2, yy / 2); vs[k] = v(xx / 2, yy / 2); k++; xx += sx }
            yy += sy
        }
        val rgb = cz.recaption.core.NameColor.fromSamples(ys, us, vs, k)
        deliverColor(req, rgb, (System.nanoTime() - t0) / 1000)
    }

    // ---------------- záznam snímků (SnapRecorder) ----------------

    /** Čekající žádost o barevný snímek celé obrazovky (záznam snímků); null = nic – za snímek jen tahle kontrola. */
    @Volatile private var stillWanted: ((Bitmap?) -> Unit)? = null

    /** Vezme žádost o barevný snímek (nejvýš jednou). */
    private fun takeStill(): ((Bitmap?) -> Unit)? {
        if (stillWanted == null) return null
        synchronized(colorRequests) { val s = stillWanted; stillWanted = null; return s }
    }

    /**
     * Barevný snímek celé obrazovky (nanejvýš [maxW] široký) z nejbližšího snímku videa; čeká nejvýš [waitMs].
     * null = dekodér barvy nedává (záložní cesta bez popisu obrázku) nebo nic nepřišlo – pak [still] (šedý).
     * Volá jen záznam snímků, mimo hlavní vlákno.
     */
    fun colorStill(maxW: Int, waitMs: Long): Bitmap? {
        val got = java.util.concurrent.atomic.AtomicReference<Bitmap?>(null)
        val done = java.util.concurrent.CountDownLatch(1)
        val late = java.util.concurrent.atomic.AtomicBoolean(false)
        val cb: (Bitmap?) -> Unit = { b ->
            if (late.get()) b?.recycle() else got.set(b)
            done.countDown()
        }
        stillMaxW = maxW
        synchronized(colorRequests) { stillWanted = cb }
        val ok = try { done.await(waitMs, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { false }
        if (!ok) {
            synchronized(colorRequests) { if (stillWanted === cb) stillWanted = null }
            late.set(true)
            got.getAndSet(null)?.let { return it }
            return null
        }
        return got.get()
    }
    @Volatile private var stillMaxW = 1280

    /** Barevný obraz zmenšený po celých krocích na nejvýš [maxW]; [y] čte jas, [u]/[v] barvu v polovičním rozlišení. */
    private inline fun colorScaled(w: Int, h: Int, maxW: Int, y: (Int, Int) -> Int, u: (Int, Int) -> Int, v: (Int, Int) -> Int): Bitmap? = try {
        // neceločíselný krok (nejbližší bod): 1920 bodů → 1280, ne 960
        val ow = minOf(w, maxW); val oh = maxOf(1, (h.toLong() * ow / w).toInt())
        val out = IntArray(ow * oh)
        var o = 0
        for (row in 0 until oh) {
            val r = (row.toLong() * h / oh).toInt()
            for (x in 0 until ow) {
                val c = (x.toLong() * w / ow).toInt()
                out[o++] = Yuv.pixel(y(c, r), u(c / 2, r / 2), v(c / 2, r / 2))
            }
        }
        Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888).also { it.setPixels(out, 0, ow, 0, 0, ow, oh) }
    } catch (_: Throwable) { null }

    /** Mřížka jasu posledního snímku pro porovnání (viz [cz.recaption.core.SnapBatches.thumb]); null = obraz zatím není. */
    fun thumb(): IntArray? = try {
        synchronized(frameLock) {
            if (frameW <= 0 || frameH <= 0) null else cz.recaption.core.SnapBatches.thumb(yBuf, yStride, frameW, frameH)
        }
    } catch (_: Throwable) { null }

    /** Barevný náhled (poloviční velikost) – pro obrazovku aplikace a nastavení oblastí. */
    @Volatile var colorSnapshot: Bitmap? = null
        private set

    /** Zdroj videa se změnil → starý obraz zahoď, ať se neukazuje ani nečte. */
    fun clearFrames() {
        synchronized(frameLock) { frameW = 0; frameH = 0 }
        colorSnapshot = null
    }

    val frameWidth: Int get() = synchronized(frameLock) { frameW }
    val frameHeight: Int get() = synchronized(frameLock) { frameH }

    /**
     * @param openDecoder false = dekodér H.264 se neotevře hned (Xbox posílá hotové snímky přes
     *   [feedI420]); otevře se sám, až by přišlo video do [feed] (ukázka).
     */
    fun start(openDecoder: Boolean = true): Boolean = synchronized(codecLock) {
        try {
            val r = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 3)
            r.setOnImageAvailableListener({ onImage(it) }, handler)
            reader = r
            if (openDecoder) openCodec() else decoderName = "WebRTC"
            true
        } catch (e: Exception) {
            lastError = e.javaClass.simpleName + ": " + (e.message ?: "")
            Log.e(TAG, "dekodér", e)
            false
        }
    }

    /** Způsob, kterým se dekodér podařilo spustit (zapamatuje se pro případnou obnovu). */
    private var workingPlan: Plan? = null
    /** Dekodér vrací obrázky přímo (bez plochy ImageReaderu) – záloha pro zařízení, kde to jinak nejde. */
    private var bufferMode = false

    /** Způsoby, které se sice spustily, ale nevydaly žádný obraz. */
    private val badPlans = HashSet<Plan>()
    private var fedAtOpen = 0L
    private var decodedAtOpen = 0L

    /** Způsob spuštění dekodéru: který dekodér (null = výchozí) a jestli s výstupem do plochy. */
    private data class Plan(val codecName: String?, val surface: Boolean)

    /** Softwarový dekodér Androidu – s výstupem do plochy pro čtení na některých zařízeních padá. */
    private fun isSoftware(name: String) = name.startsWith("OMX.google.", true) || name.startsWith("c2.android.", true)

    /** Všechny dekodéry H.264 v zařízení (hardwarové i softwarové). */
    private fun decoderNames(): List<String> = try {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(MIME, ignoreCase = true) } }
            .map { it.name }
    } catch (_: Exception) { emptyList() }

    /**
     * Spustí dekodér. Televize a boxy se liší, proto se zkouší několik způsobů po sobě:
     * výchozí dekodér zařízení, pak ostatní; nejdřív s přímým výstupem, pak s výstupem do plochy.
     */
    private fun openCodec() {
        val plans = ArrayList<Plan>()
        workingPlan?.let { plans.add(it) }
        // Nejdřív přímý výstup (obrázek předá dekodér sám – nejspolehlivější napříč zařízeními),
        // výstup do plochy jen jako záloha a jen u hardwarových dekodérů.
        plans.add(Plan(null, surface = false))
        val names = decoderNames()
        for (n in names) plans.add(Plan(n, surface = false))
        plans.add(Plan(null, surface = true))
        for (n in names) if (!isSoftware(n)) plans.add(Plan(n, surface = true))
        var last: Exception? = null
        val tried = StringBuilder()
        if (plans.distinct().all { it in badPlans }) badPlans.clear()
        for (pl in plans.distinct()) {
            if (pl in badPlans) continue
            var c: MediaCodec? = null
            try {
                c = if (pl.codecName == null) MediaCodec.createDecoderByType(MIME) else MediaCodec.createByCodecName(pl.codecName)
                if (pl.surface && isSoftware(try { c.name } catch (_: Exception) { "" })) throw IllegalStateException("softwarový dekodér + plocha")
                val f = MediaFormat.createVideoFormat(MIME, width, height)
                f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height)
                if (!pl.surface) f.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                c.configure(f, if (pl.surface) (reader ?: throw IllegalStateException("reader")).surface else null, null, 0)
                c.start()
                codec = c
                bufferMode = !pl.surface
                workingPlan = pl
                decoderName = (try { c.name } catch (_: Exception) { "?" }) + if (pl.surface) "" else " (přímý výstup)"
                pts = 0
                fedAtOpen = fed; decodedAtOpen = decoded
                if (tried.isNotEmpty()) Log.i(TAG, "dekodér: nešlo $tried→ jde $decoderName")
                return
            } catch (e: Exception) {
                last = e
                tried.append(pl.codecName ?: "výchozí").append(if (pl.surface) "/plocha " else "/přímo ")
                try { c?.release() } catch (_: Exception) {}
            }
        }
        workingPlan = null
        throw last ?: IllegalStateException("žádný dekodér H.264")
    }

    private fun closeCodec() {
        val c = codec ?: return
        codec = null
        try { c.stop() } catch (_: Exception) {}
        try { c.release() } catch (_: Exception) {}
    }

    /**
     * Jeden snímek videa ze sítě. Vrací false, když ho dekodér nepřijal
     * (volající si pak od konzole vyžádá nový klíčový snímek).
     */
    fun feed(buf: ByteArray): Boolean = synchronized(codecLock) {
        if (reader == null) return false
        val types = cz.recaption.core.H264.nalTypes(buf)
        val key = cz.recaption.core.H264.IDR in types
        if (header == null || cz.recaption.core.H264.SPS in types) header = buf
        fed++
        if (needKey) {
            val waited = android.os.SystemClock.elapsedRealtime() - needKeySince
            if (!key && waited < KEY_WAIT_MS) { keySkipped++; return true }
            needKey = false
            tp(if (key) "DEC|klíčový snímek po ${waited} ms, přeskočeno $keySkipped" else "DEC|klíčový snímek nepřišel do ${KEY_WAIT_MS / 1000} s, dekóduji dál")
            if (key) lastError = null
        }
        try {
            // dekodér běží, dostal už přes 90 snímků a nevydal ani jeden obrázek → zkus jiný způsob
            if (codec != null && fed - fedAtOpen > 90 && decoded == decodedAtOpen) {
                workingPlan?.let { badPlans.add(it) }
                Log.w(TAG, "dekodér $decoderName nevydává obraz, zkouším jiný")
                workingPlan = null
                closeCodec()
                waitForKey("nevydává obraz")
                if (!key) return true
            }
            val c = codec ?: run { openCodec(); val h = header; if (h != null && h !== buf) push(codec!!, h); codec!! }
            val ok = push(c, buf)
            drain(c)
            ok
        } catch (e: Exception) {
            val detail = (e as? MediaCodec.CodecException)?.let { " ${it.errorCode}${if (it.isTransient) " přechodná" else ""}" } ?: ""
            lastError = e.javaClass.simpleName + detail
            Log.w(TAG, "dekódování", e)
            // dekodér se rozbil → zavři ho, příští snímek ho otevře znovu – ale až klíčovým snímkem
            closeCodec()
            waitForKey(e.javaClass.simpleName + detail)
            false
        }
    }

    /** Po výměně dekodéru čekat na klíčový snímek (viz [needKey]). */
    private fun waitForKey(why: String) {
        if (!needKey) { needKey = true; needKeySince = android.os.SystemClock.elapsedRealtime(); keySkipped = 0 }
        tp("DEC|nový dekodér ($why) · čekám na klíčový snímek")
    }

    private fun tp(msg: String) { Log.i("TP_TEST", msg); cz.recaption.core.DiagLog.add(msg); EventFile.add(msg) }

    private fun push(c: MediaCodec, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val i = c.dequeueInputBuffer(20_000)
            if (i < 0) { drain(c); return false }
            val ib = c.getInputBuffer(i) ?: return false
            ib.clear()
            val n = min(ib.remaining(), buf.size - off)
            ib.put(buf, off, n)
            c.queueInputBuffer(i, 0, n, pts++, 0)
            off += n
        }
        return true
    }

    private fun drain(c: MediaCodec) {
        while (true) {
            val o = c.dequeueOutputBuffer(info, 0)
            when {
                o >= 0 -> {
                    if (bufferMode) {
                        if (info.size != 0) try {
                            val img = c.getOutputImage(o)
                            if (img != null) handleImage(img) else handleRaw(c, o)
                        } catch (e: Exception) { lastError = "obraz: " + e.javaClass.simpleName }
                        c.releaseOutputBuffer(o, false)
                    } else c.releaseOutputBuffer(o, info.size != 0)
                }
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                @Suppress("DEPRECATION")
                o == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {}
                else -> return
            }
        }
    }

    /**
     * Záloha, když dekodér nedá obrázek s popisem: jas je ve všech běžných formátech na začátku
     * výstupu, řádek po řádku.
     */
    private fun handleRaw(c: MediaCodec, index: Int) {
        val buf = c.getOutputBuffer(index) ?: return
        val f = c.outputFormat
        fun int(k: String, d: Int) = if (f.containsKey(k)) f.getInteger(k) else d
        val fw = int(MediaFormat.KEY_WIDTH, width)
        val fh = int(MediaFormat.KEY_HEIGHT, height)
        val w = if (f.containsKey("crop-right")) int("crop-right", fw - 1) - int("crop-left", 0) + 1 else fw
        val h = if (f.containsKey("crop-bottom")) int("crop-bottom", fh - 1) - int("crop-top", 0) + 1 else fh
        val stride = maxOf(int("stride", fw), w)
        if (w <= 0 || h <= 0) return
        val b = buf.duplicate()
        b.position(info.offset)
        val need = min(b.remaining(), stride * h)
        if (need < stride * (h - 1) + w) return
        synchronized(frameLock) {
            if (yBuf.size < stride * h) yBuf = ByteArray(stride * h)
            b.get(yBuf, 0, need)
            yStride = stride; frameW = w; frameH = h; frameSerial++
        }
        decoded++
        val now = SystemClock.uptimeMillis()
        lastFrameAt = now
        takeStill()?.invoke(null)    // tady barvy nejsou – záznam snímků vezme šedý
        if (UiState.visible && UiState.previewWanted && now - lastColorAt > 1500) { lastColorAt = now; colorSnapshot = grayHalf() }
    }

    /** Šedý náhled v poloviční velikosti (když nejsou k dispozici barvy). */
    private fun grayHalf(): Bitmap? = try {
        synchronized(frameLock) {
            val w = frameW / 2; val h = frameH / 2
            val out = IntArray(w * h)
            var o = 0
            for (row in 0 until h) for (x in 0 until w) {
                val v = yBuf[row * 2 * yStride + x * 2].toInt() and 0xFF
                out[o++] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(out, 0, w, 0, 0, w, h) }
        }
    } catch (_: Exception) { null }

    /** Nový dekódovaný obrázek: zkopíruj jasovou složku (na čtení textu barvy nejsou potřeba). */
    private fun onImage(r: ImageReader) {
        val img = try { r.acquireLatestImage() } catch (_: Exception) { null } ?: return
        try {
            handleImage(img)
        } catch (e: Exception) {
            lastError = "obraz: " + e.javaClass.simpleName
        } finally {
            img.close()
        }
    }

    /** Zkopíruje jas z dekódovaného obrázku a občas uloží barevný náhled. */
    private fun handleImage(img: Image) {
        // skutečný obraz bez výplně na okraji (dekodér vrací např. 1920×1088 místo 1920×1080)
        val cr = img.cropRect
        val w = if (cr.width() in 1..img.width) cr.width() else img.width
        val h = if (cr.height() in 1..img.height) cr.height() else img.height
        val p = img.planes[0]
        val stride = p.rowStride
        val buf = p.buffer.duplicate()
        val need = min(buf.remaining(), stride * h)
        synchronized(frameLock) {
            if (yBuf.size < stride * h) yBuf = ByteArray(stride * h)
            buf.get(yBuf, 0, need)
            yStride = stride
            frameW = w
            frameH = h
            frameSerial++
        }
        decoded++
        val now = SystemClock.uptimeMillis()
        lastFrameAt = now
        takeColorRequests()?.forEach { req ->
            try {
                val yP = img.planes[0]; val uP = img.planes[1]; val vP = img.planes[2]
                val yB = yP.buffer; val uB = uP.buffer; val vB = vP.buffer
                sampleColor(req, w, h,
                    { x, r -> yB.get(r * yP.rowStride + x * yP.pixelStride).toInt() and 0xFF },
                    { x, r -> uB.get(r * uP.rowStride + x * uP.pixelStride).toInt() and 0xFF },
                    { x, r -> vB.get(r * vP.rowStride + x * vP.pixelStride).toInt() and 0xFF })
            } catch (_: Throwable) { deliverColor(req, null, 0) }
        }
        takeStill()?.let { cb ->
            val b = try {
                val yP = img.planes[0]; val uP = img.planes[1]; val vP = img.planes[2]
                val yB = yP.buffer; val uB = uP.buffer; val vB = vP.buffer
                colorScaled(w, h, stillMaxW,
                    { x, r -> yB.get(r * yP.rowStride + x * yP.pixelStride).toInt() and 0xFF },
                    { x, r -> uB.get(r * uP.rowStride + x * uP.pixelStride).toInt() and 0xFF },
                    { x, r -> vB.get(r * vP.rowStride + x * vP.pixelStride).toInt() and 0xFF })
            } catch (_: Throwable) { null }
            cb(b)
        }
        // barevný náhled jen pro obrazovku aplikace – při hraní se nedělá
        if (UiState.visible && UiState.previewWanted && now - lastColorAt > 1500) {
            lastColorAt = now
            colorSnapshot = colorHalf(img)
        }
    }

    /**
     * Hotový obrázek bez dekódování (jen ukázka na zařízeních, která neumí video zakódovat).
     * [y] = jas, w×h bajtů.
     */
    fun feedRaw(y: ByteArray, w: Int, h: Int) {
        synchronized(frameLock) {
            if (yBuf.size < w * h) yBuf = ByteArray(w * h)
            System.arraycopy(y, 0, yBuf, 0, w * h)
            yStride = w; frameW = w; frameH = h; frameSerial++
        }
        decoded++
        lastFrameAt = SystemClock.uptimeMillis()
        takeStill()?.invoke(null)
    }

    /**
     * Stojí o nový snímek? (Xbox: převod snímku WebRTC do paměti něco stojí, proto se dělá jen
     * tehdy, když čtečka předchozí už vzala, nebo je čas na barevný náhled.)
     */
    /** Chtít každý snímek (otevřené menu hry, záznam snímků) – nastavuje služba. */
    @Volatile var eager = false

    fun wantsFrame(): Boolean {
        if (eager) return true
        if (UiState.visible && UiState.previewWanted && SystemClock.uptimeMillis() - lastColorAt > 1500) return true
        if (colorPending || stillWanted != null) return true
        return synchronized(frameLock) { frameW == 0 || grabbedSerial == frameSerial }
    }

    /** Snímek přišel, ale nebyl potřeba (počítá se kvůli stavu „obraz přichází“). */
    fun frameSkipped() {
        decoded++
        lastFrameAt = SystemClock.uptimeMillis()
    }

    /**
     * Hotový snímek I420 (Xbox, WebRTC): zkopíruje jas, občas udělá barevný náhled. Dál se čte
     * úplně stejně jako video z PlayStationu ([grab] → [Crop] s otiskem [FrameSignature]).
     */
    fun feedI420(y: java.nio.ByteBuffer, stride: Int, u: java.nio.ByteBuffer, uStride: Int,
                 v: java.nio.ByteBuffer, vStride: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0 || stride < w) return
        val yb = y.duplicate().apply { position(0) }
        val need = min(yb.remaining(), stride * h)
        if (need < stride * (h - 1) + w) return
        synchronized(frameLock) {
            if (yBuf.size < stride * h) yBuf = ByteArray(stride * h)
            yb.get(yBuf, 0, need)
            yStride = stride; frameW = w; frameH = h; frameSerial++
        }
        decoded++
        val now = SystemClock.uptimeMillis()
        lastFrameAt = now
        takeColorRequests()?.forEach { req ->
            try {
                sampleColor(req, w, h,
                    { x, r -> yBuf[r * stride + x].toInt() and 0xFF },
                    { x, r -> u.get(r * uStride + x).toInt() and 0xFF },
                    { x, r -> v.get(r * vStride + x).toInt() and 0xFF })
            } catch (_: Throwable) { deliverColor(req, null, 0) }
        }
        takeStill()?.let { cb ->
            cb(colorScaled(w, h, stillMaxW,
                { x, r -> y.get(r * stride + x).toInt() and 0xFF },
                { x, r -> u.get(r * uStride + x).toInt() and 0xFF },
                { x, r -> v.get(r * vStride + x).toInt() and 0xFF }))
        }
        if (UiState.visible && UiState.previewWanted && now - lastColorAt > 1500) {
            lastColorAt = now
            colorSnapshot = try {
                val hw = w / 2; val hh = h / 2
                val out = IntArray(hw * hh)
                var o = 0
                for (row in 0 until hh) for (x in 0 until hw) {
                    val yy = yBuf[row * 2 * stride + x * 2].toInt() and 0xFF
                    val uu = u.get(row * uStride + x).toInt() and 0xFF
                    val vv = v.get(row * vStride + x).toInt() and 0xFF
                    out[o++] = Yuv.pixel(yy, uu, vv)
                }
                Bitmap.createBitmap(hw, hh, Bitmap.Config.ARGB_8888).also { it.setPixels(out, 0, hw, 0, 0, hw, hh) }
            } catch (_: Exception) { null }
        }
    }

    /** Barevný náhled v poloviční velikosti. */
    private fun colorHalf(img: Image): Bitmap? = try {
        val w = img.width / 2
        val h = img.height / 2
        val yP = img.planes[0]; val uP = img.planes[1]; val vP = img.planes[2]
        val yB = yP.buffer; val uB = uP.buffer; val vB = vP.buffer
        val out = IntArray(w * h)
        var o = 0
        for (row in 0 until h) {
            val yRow = row * 2 * yP.rowStride
            val uRow = row * uP.rowStride
            val vRow = row * vP.rowStride
            for (x in 0 until w) {
                val yy = yB.get(yRow + x * 2 * yP.pixelStride).toInt() and 0xFF
                val uu = uB.get(uRow + x * uP.pixelStride).toInt() and 0xFF
                val vv = vB.get(vRow + x * vP.pixelStride).toInt() and 0xFF
                out[o++] = Yuv.pixel(yy, uu, vv)
            }
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(out, 0, w, 0, 0, w, h) }
    } catch (_: Exception) { null }

    /**
     * Celý poslední obrázek (šedý, nanejvýš [maxW] široký) pro snímek k ladění v záznamu v síti.
     * Na čtení titulků nemá vliv (neposouvá „nový snímek“ pro [grab]).
     */
    fun still(maxW: Int = 1280): Bitmap? = try {
        synchronized(frameLock) {
            if (frameW <= 0 || frameH <= 0) return null
            val step = maxOf(1, (frameW + maxW - 1) / maxW)
            val w = frameW / step; val h = frameH / step
            val out = IntArray(w * h)
            var o = 0
            for (row in 0 until h) for (x in 0 until w) {
                val v = yBuf[row * step * yStride + x * step].toInt() and 0xFF
                out[o++] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(out, 0, w, 0, 0, w, h) }
        }
    } catch (_: Throwable) { null }

    /** Výřez posledního obrázku: bitmapa (znovupoužívaná – nerecyklovat) a kde v celém obrázku leží. */
    class Crop(
        val bmp: Bitmap, val x0: Int, val y0: Int, val fullW: Int, val fullH: Int,
        /** Otisk výřezu (viz [FrameSignature]) a kolik pixelů připadá na jednu jeho buňku. */
        val sig: IntArray, val cellPixels: Int,
    )

    /**
     * Výřez posledního obrázku (šedý) v oblasti dané zlomky 0..1. Převádí se jen výřez, ne celý
     * obrázek – při čtení úzkého pásu s titulky je to několikrát rychlejší.
     * Vrací null, dokud žádný snímek nepřišel, nebo když od minula žádný nový není.
     */
    /** Výřez pro čtení menu hry: vlastní bitmapa a pole (šedé), aby nerušil čtení titulků ([grab]). */
    class MenuCrop(val bmp: Bitmap, val gray: IntArray, val x0: Int, val y0: Int, val w: Int, val h: Int, val fullW: Int, val fullH: Int)
    private var menuBmp: Bitmap? = null
    private var menuArgb = IntArray(0)
    private var menuSerial = -1L

    /**
     * Horní část posledního snímku pro menu hry ([cz.recaption.core.GameMenu]), zmenšená na nejvýš [maxW] bodů na šířku
     * (stačí na čtení položek, je rychlejší). null = žádný nový snímek od minula. Volá se jen občas (viz služba).
     */
    fun grabMenu(bottom: Float, maxW: Int = 960): MenuCrop? = synchronized(frameLock) {
        if (frameW <= 0 || frameH <= 0 || menuSerial == frameSerial) return null
        menuSerial = frameSerial
        val step = maxOf(1, (frameW + maxW - 1) / maxW)
        val h0 = (frameH * bottom).toInt().coerceIn(1, frameH)
        val w = frameW / step; val h = h0 / step
        if (menuArgb.size != w * h) menuArgb = IntArray(w * h)
        var o = 0
        for (y in 0 until h) {
            val row = y * step * yStride
            for (x in 0 until w) {
                val g = yBuf[row + x * step].toInt() and 0xFF
                menuArgb[o++] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        }
        var b = menuBmp
        if (b == null || b.width != w || b.height != h) { b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); menuBmp = b }
        b.setPixels(menuArgb, 0, w, 0, 0, w, h)
        MenuCrop(b, menuArgb, 0, 0, w, h, w, (frameH / step))
    }

    private var cornerBmp: Bitmap? = null
    private var cornerArgb = IntArray(0)
    private var cornerSerial = -1L

    /**
     * Pravý dolní roh posledního snímku pro menu vpravo dole ([cz.recaption.core.CornerPrompts]): šedě, vlastní bitmapa
     * a čítač (neruší čtení titulků ani menu), nejvýš [maxW] bodů na šířku. Souřadnice řádků = výřez + [MenuCrop.x0]/[MenuCrop.y0]
     * v obraze [MenuCrop.fullW] × [MenuCrop.fullH]. null = žádný nový snímek od minula.
     */
    fun grabCorner(left: Float, top: Float, maxW: Int = 800): MenuCrop? = synchronized(frameLock) {
        if (frameW <= 0 || frameH <= 0 || cornerSerial == frameSerial) return null
        cornerSerial = frameSerial
        val sx0 = (frameW * left).toInt().coerceIn(0, frameW - 1)
        val sy0 = (frameH * top).toInt().coerceIn(0, frameH - 1)
        val step = maxOf(1, (frameW - sx0 + maxW - 1) / maxW)
        val w = (frameW - sx0) / step; val h = (frameH - sy0) / step
        if (w <= 0 || h <= 0) return null
        if (cornerArgb.size != w * h) cornerArgb = IntArray(w * h)
        var o = 0
        for (y in 0 until h) {
            val row = (sy0 + y * step) * yStride + sx0
            for (x in 0 until w) {
                val g = yBuf[row + x * step].toInt() and 0xFF
                cornerArgb[o++] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        }
        var b = cornerBmp
        if (b == null || b.width != w || b.height != h) { b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); cornerBmp = b }
        b.setPixels(cornerArgb, 0, w, 0, 0, w, h)
        MenuCrop(b, cornerArgb, sx0 / step, sy0 / step, w, h, frameW / step, frameH / step)
    }

    private var worldBmp: Bitmap? = null
    private var worldArgb = IntArray(0)
    private var worldSerial = -1L

    /**
     * Výřez pro pokyny u předmětů (od 0.97.88): obdélník ve zlomcích obrazu, šedě, nejvýš [maxW] bodů na šířku – písmo
     * pokynu je ~2 % výšky, při zmenšení by ho čtečka nepřečetla. Vlastní bitmapa a čítač (čtení titulků se nemění).
     */
    fun grabWorld(left: Float, top: Float, right: Float, bottom: Float, maxW: Int = 1500): MenuCrop? = synchronized(frameLock) {
        if (frameW <= 0 || frameH <= 0 || worldSerial == frameSerial) return null
        worldSerial = frameSerial
        val sx0 = (frameW * left).toInt().coerceIn(0, frameW - 1)
        val sy0 = (frameH * top).toInt().coerceIn(0, frameH - 1)
        val sx1 = (frameW * right).toInt().coerceIn(sx0 + 1, frameW)
        val sy1 = (frameH * bottom).toInt().coerceIn(sy0 + 1, frameH)
        val step = maxOf(1, (sx1 - sx0 + maxW - 1) / maxW)
        val w = (sx1 - sx0) / step; val h = (sy1 - sy0) / step
        if (w <= 0 || h <= 0) return null
        if (worldArgb.size != w * h) worldArgb = IntArray(w * h)
        var o = 0
        for (y in 0 until h) {
            val row = (sy0 + y * step) * yStride + sx0
            for (x in 0 until w) {
                val g = yBuf[row + x * step].toInt() and 0xFF
                worldArgb[o++] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        }
        var b = worldBmp
        if (b == null || b.width != w || b.height != h) { b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); worldBmp = b }
        b.setPixels(worldArgb, 0, w, 0, 0, w, h)
        MenuCrop(b, worldArgb, sx0 / step, sy0 / step, w, h, frameW / step, frameH / step)
    }

    private var phoneBmp: Bitmap? = null
    private var phoneArgb = IntArray(0)
    private var phoneSerial = -1L

    /** Výřez pro telefon ve hře (od 0.97.95) – jako [grabWorld], vlastní bitmapa a čítač. */
    fun grabPhone(left: Float, top: Float, right: Float, bottom: Float, maxW: Int = 1300): MenuCrop? = synchronized(frameLock) {
        if (frameW <= 0 || frameH <= 0 || phoneSerial == frameSerial) return null
        phoneSerial = frameSerial
        val sx0 = (frameW * left).toInt().coerceIn(0, frameW - 1)
        val sy0 = (frameH * top).toInt().coerceIn(0, frameH - 1)
        val sx1 = (frameW * right).toInt().coerceIn(sx0 + 1, frameW)
        val sy1 = (frameH * bottom).toInt().coerceIn(sy0 + 1, frameH)
        val step = maxOf(1, (sx1 - sx0 + maxW - 1) / maxW)
        val w = (sx1 - sx0) / step; val h = (sy1 - sy0) / step
        if (w <= 0 || h <= 0) return null
        if (phoneArgb.size != w * h) phoneArgb = IntArray(w * h)
        var o = 0
        for (y in 0 until h) {
            val row = (sy0 + y * step) * yStride + sx0
            for (x in 0 until w) {
                val g = yBuf[row + x * step].toInt() and 0xFF
                phoneArgb[o++] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        }
        var b = phoneBmp
        if (b == null || b.width != w || b.height != h) { b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); phoneBmp = b }
        b.setPixels(phoneArgb, 0, w, 0, 0, w, h)
        MenuCrop(b, phoneArgb, sx0 / step, sy0 / step, w, h, frameW / step, frameH / step)
    }

    private class AreaBuf { var bmp: Bitmap? = null; var argb = IntArray(0); var serial = -1L }
    private val areaBufs = HashMap<String, AreaBuf>()

    /** Výřez [key] (vlastní bitmapa a čítač na klíč, šedě, ≤ [maxW] bodů) – pro malé oblasti textů hry (od 0.97.98). */
    fun grabArea(key: String, left: Float, top: Float, right: Float, bottom: Float, maxW: Int = 1300): MenuCrop? = synchronized(frameLock) {
        val buf = areaBufs.getOrPut(key) { AreaBuf() }
        if (frameW <= 0 || frameH <= 0 || buf.serial == frameSerial) return null
        buf.serial = frameSerial
        val sx0 = (frameW * left).toInt().coerceIn(0, frameW - 1)
        val sy0 = (frameH * top).toInt().coerceIn(0, frameH - 1)
        val sx1 = (frameW * right).toInt().coerceIn(sx0 + 1, frameW)
        val sy1 = (frameH * bottom).toInt().coerceIn(sy0 + 1, frameH)
        val step = maxOf(1, (sx1 - sx0 + maxW - 1) / maxW)
        val w = (sx1 - sx0) / step; val h = (sy1 - sy0) / step
        if (w <= 0 || h <= 0) return null
        if (buf.argb.size != w * h) buf.argb = IntArray(w * h)
        val a = buf.argb
        var o = 0
        for (y in 0 until h) {
            val row = (sy0 + y * step) * yStride + sx0
            for (x in 0 until w) {
                val g = yBuf[row + x * step].toInt() and 0xFF
                a[o++] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        }
        var b = buf.bmp
        if (b == null || b.width != w || b.height != h) { b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); buf.bmp = b }
        b.setPixels(a, 0, w, 0, 0, w, h)
        MenuCrop(b, a, sx0 / step, sy0 / step, w, h, frameW / step, frameH / step)
    }

    private var sampleSerial = -1L

    /**
     * Průměrný jas (0–255) v každém obdélníku [rects] (vlevo, nahoře, vpravo, dole ve zlomcích obrazu) na posledním
     * snímku – přímo z jasové roviny, bez bitmapy, nejvýš 24 × 4 bodů na obdélník (od 0.97.58: vybraná položka menu hry
     * podle bílého pruhu na každém snímku). null = žádný nový snímek od minulého měření.
     */
    fun sampleGray(rects: List<FloatArray>): IntArray? = synchronized(frameLock) {
        if (frameW <= 0 || frameH <= 0 || sampleSerial == frameSerial) return null
        sampleSerial = frameSerial
        measureGray(rects)
    }

    /** Jako [sampleGray], ale bez hlídání nového snímku (levná brána menu vpravo dole – nesmí brát snímek menu hry). */
    fun grayNow(rects: List<FloatArray>): IntArray? = synchronized(frameLock) {
        if (frameW <= 0 || frameH <= 0) return null
        measureGray(rects)
    }

    /**
     * Mřížka jasu [cols] × [rows] z obdélníku [box] posledního snímku ([cz.recaption.core.PhoneGate.sample]) – brána
     * „telefon je otevřený“: pár desítek vodorovných řezů z jasové roviny, bez bitmapy a bez hlídání nového snímku.
     */
    fun grayGrid(box: FloatArray, cols: Int, rows: Int): IntArray? = synchronized(frameLock) {
        if (frameW <= 0 || frameH <= 0) return null
        cz.recaption.core.PhoneGate.sample(frameW, frameH, box, cols, rows) { x, y -> yBuf[y * yStride + x].toInt() and 0xFF }
    }

    /** Jasový profil obdélníku [box] z posledního snímku – posun telefonu ve hře mezi čteními ([cz.recaption.core.PhoneMotion]). */
    fun grayProfile(box: FloatArray): cz.recaption.core.PhoneMotion.Profile? = synchronized(frameLock) {
        if (frameW <= 0 || frameH <= 0) return null
        val x0 = (box[0] * frameW).toInt().coerceIn(0, frameW - 1); val x1 = (box[2] * frameW).toInt().coerceIn(x0 + 1, frameW)
        val y0 = (box[1] * frameH).toInt().coerceIn(0, frameH - 1); val y1 = (box[3] * frameH).toInt().coerceIn(y0 + 1, frameH)
        cz.recaption.core.PhoneMotion.profile(box, x1 - x0, y1 - y0) { x, y -> yBuf[(y0 + y) * yStride + x0 + x].toInt() and 0xFF }
    }

    private fun measureGray(rects: List<FloatArray>): IntArray =
        IntArray(rects.size) { i ->
            val r = rects[i]
            val x0 = (r[0] * frameW).toInt().coerceIn(0, frameW - 1); val x1 = (r[2] * frameW).toInt().coerceIn(x0 + 1, frameW)
            val y0 = (r[1] * frameH).toInt().coerceIn(0, frameH - 1); val y1 = (r[3] * frameH).toInt().coerceIn(y0 + 1, frameH)
            val sx = maxOf(1, (x1 - x0) / 24); val sy = maxOf(1, (y1 - y0) / 4)
            var s = 0L; var n = 0
            var y = y0
            while (y < y1) { val row = y * yStride; var x = x0; while (x < x1) { s += yBuf[row + x].toInt() and 0xFF; n++; x += sx }; y += sy }
            if (n == 0) 0 else (s / n).toInt()
        }

    fun grab(left: Float, top: Float, right: Float, bottom: Float): Crop? = synchronized(frameLock) {
        if (frameW <= 0 || frameH <= 0) return null
        if (grabbedSerial == frameSerial) return null
        grabbedSerial = frameSerial
        val x0 = (frameW * left).toInt().coerceIn(0, frameW - 1)
        val x1 = (frameW * right).toInt().coerceIn(x0 + 1, frameW)
        val y0 = (frameH * top).toInt().coerceIn(0, frameH - 1)
        val y1 = (frameH * bottom).toInt().coerceIn(y0 + 1, frameH)
        val w = x1 - x0
        val h = y1 - y0
        if (argb.size != w * h) argb = IntArray(w * h)
        // otisk se počítá rovnou při převodu, ze všech pixelů (nový snímek = nové pole, čtečka si ho drží)
        val sig = IntArray(FrameSignature.SIZE)
        FrameSignature.cropGray(yBuf, yStride, x0, y0, w, h, argb, sig)
        var b = bmp
        if (b == null || b.width != w || b.height != h) {
            b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp = b
        }
        b.setPixels(argb, 0, w, 0, 0, w, h)
        Crop(b, x0, y0, frameW, frameH, sig, maxOf(1, w * h / FrameSignature.SIZE))
    }

    fun stop() {
        synchronized(codecLock) {
            closeCodec()
            try { reader?.close() } catch (_: Exception) {}
            reader = null
        }
        thread.quitSafely()
    }
}
