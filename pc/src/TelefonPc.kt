package cz.recaption.pc

import cz.recaption.core.GameProfile
import cz.recaption.core.Lang
import cz.recaption.core.MenuLine
import cz.recaption.core.PhoneCard
import cz.recaption.core.PhoneGate
import cz.recaption.core.PhoneMotion
import cz.recaption.core.PhoneOpen
import cz.recaption.core.PhoneRead
import cz.recaption.core.PhoneScreen
import cz.recaption.core.PhoneTracker
import cz.recaption.core.PhoneZone
import cz.recaption.core.TvSender
import cz.recaption.core.sms
import cz.recaption.core.PhoneNotice

/**
 * Překlad telefonu ve hře (GTA VI) na PC pro Titulky PS5 v TV – stejný postup jako v aplikaci pro Android TV
 * (`TranslatorService.startPhone` / `checkPhone`): brána „telefon je otevřený“ (ikona ovládání vpravo dole a světlý
 * displej), čtení jen výřezu kolem telefonu v plném rozlišení, odstavce přes [PhoneScreen.read], potvrzení druhým čtením
 * ([PhoneTracker]) a překlad po odstavcích. Do TV jde panel vedle telefonu (`telefon`), seznam konverzací vedle řádků.
 *
 * Dřív PC telefon vůbec neznalo: text bublin se četl jako titulek a do pruhu šel kousek poslední zprávy
 * (majitel 25. 11. 2026: „vnitřní chaos je vlastně dobrý pro / Já?“ přes telefon).
 */
internal class TelefonPc(
    private val profil: GameProfile,
    private val lang: Lang,
    /** Překlad textu z telefonu (jako menu – bez mluvčího). */
    private val preloz: (String) -> String,
    private val log: (String) -> Unit,
) {
    val zapnuto: Boolean = profil.phoneSpots.isNotEmpty()

    private val open = PhoneOpen()
    private val zone = PhoneZone(profil.phoneSpots)
    private val spots = profil.phoneSpots.map { it.box }
    private var tracker = PhoneTracker()
    private var checkAt = 0L
    private var readAt = 0L
    private var grid: IntArray? = null
    private var diag = ""
    private val words = HashMap<String, String>()

    /** Ukázaný překlad (zpráva pro TV) a podle čeho je položený – při pohybu telefonu se pošle znovu s novým místem. */
    private var shownBase: PhoneRead? = null
    private var shownTexts: List<String> = emptyList()
    private var shownTitle: String? = null
    var shown = false
        private set

    /** Obraz hry v posledním snímku (x0, y0, šířka, výška) – okno chiaki-ng mívá černé pruhy. */
    private var video: IntArray? = null

    /** Displej otevřeného telefonu jako zlomky celého snímku [fw] × [fh] – titulky se z jeho místa nečtou. */
    fun boxVeSnimku(fw: Int, fh: Int): FloatArray? {
        val b = (if (open.open) open.box else null) ?: return null
        val v = video ?: return null
        if (fw <= 0 || fh <= 0) return null
        return floatArrayOf((v[0] + b[0] * v[2]) / fw, (v[1] + b[1] * v[3]) / fh, (v[0] + b[2] * v[2]) / fw, (v[1] + b[3] * v[3]) / fh)
    }

    private fun d(s: String) { if (s != diag) { diag = s; log(s) } }

    /** Telefon pryč (menu hry, konec obrazu). */
    fun schovej(send: (String) -> Unit) {
        tracker = PhoneTracker(); grid = null
        if (shown) { shown = false; shownBase = null; send(TvSender.PHONE_HIDE); log("TELEFON schovan") }
    }

    /**
     * Jedna kontrola (nejvýš 1× za 1 s, ukázaný telefon 0,6 s); [helper] čte z posledního snímku (jen obraz hry bez
     * černých pruhů, [PhoneHelper.rozmer]). [pismo] / [pozadi] = vzhled pruhu pro panel v TV.
     */
    fun tick(now: Long, helper: PhoneHelper, pismo: Int, pozadi: Int, send: (String) -> Unit) {
        if (!zapnuto) return
        if (now - checkAt < (if (shown) 600 else 1000)) return
        checkAt = now
        val v = helper.rozmer() ?: return
        video = v
        val fw = v[2]; val fh = v[3]
        if (fw < 320 || fh < 180) return
        val G = PhoneGate
        val (iconBox, iconN) = G.iconBox(fw, fh)
        val icon = helper.mrizka(iconBox, iconN, iconN)?.let { G.icon(it, iconN) } ?: return
        val quad = if (!icon) null else helper.mrizka(PhoneZone.WIDE, G.COLS, G.ROWS)?.let { G.quad(it, fw, fh, spots) }
        if (open.update(icon, quad)) log("TELEFON brana " + if (open.open) "otevreny" else "zavreny (${open.reason})")
        val b = open.box
        if (!open.open || b == null) {
            grid = null
            if (shown || tracker.waiting) schovej(send)
            return
        }
        val z = zone.next(now, G.zone(b))
        val g = helper.mrizka(z, 6, 6) ?: return
        val prev = grid
        grid = g
        if (!PhoneScreen.changed(prev, g) && !tracker.waiting && now - readAt < 3000) return
        readAt = now
        val crop = helper.telefon(z) ?: return
        val S = PhoneScreen
        val own = crop.lines.filterNot { S.controlHint(it.first, crop.w, crop.h) }
        val vzorky = own.associate { it.first to it.second }
        val ownLines = own.map { it.first }
        val read00 = S.read(ownLines, crop.w, crop.h, { l -> S.whiteCard(vzorky[l] ?: IntArray(0)) }) { l -> S.darkText(vzorky[l] ?: IntArray(0)) }
            ?: PhoneCard.read(ownLines, crop.w, crop.h) { l -> PhoneCard.light(vzorky[l] ?: IntArray(0)) }
        val read0 = read00?.takeIf { r -> !(r.top > 0.7f && PhoneNotice.sms(crop.lines.map { it.first }, crop.w, crop.h) != null) }
        zone.found(read0?.let { floatArrayOf(it.left, it.top, it.right, it.bottom) }, now)
        val (paras, changed) = tracker.update(read0?.paragraphs ?: emptyList())
        d("TELEFON cteni radku=${crop.lines.size} odstavcu=${read0?.paragraphs?.size ?: 0} " +
            (if (read0 == null) "nic" else if (changed) "nove" else if (tracker.waiting) "ceka" else "stejne") + " (${crop.ms} ms)")
        if (!changed) {
            // týž obsah, telefon se jen pohnul: stejný překlad na nové místo (TV ho nesleduje sama)
            val base = shownBase
            if (shown && read0 != null && base != null && !read0.card && !base.card) {
                val (dx, dy) = PhoneMotion.delta(base, read0)
                if (kotlin.math.abs(dx) > 0.02f || kotlin.math.abs(dy) > 0.02f) {
                    zprava(read0, shownTexts, shownTitle, pismo, pozadi)?.let { send(it) }
                    shownBase = read0
                }
            }
            return
        }
        if (paras.isEmpty() || read0 == null) {
            if (shown) { shown = false; shownBase = null; send(TvSender.PHONE_HIDE); log("TELEFON schovan (nic)") }
            return
        }
        val read = read0
        val texts = if (read.card) paras.map { karta(it) } else read.paragraphs.indices.map { i -> odstavec(read, i, read.paragraphs[i]) }
        val title = if (read.card) read.title?.let { karta(it) } else read.title
        val msg = zprava(read, texts, title, pismo, pozadi) ?: return
        send(msg)
        shown = true; shownBase = read; shownTexts = texts; shownTitle = title
        log("TELEFON " + read.paragraphs.indices.joinToString(" ¦ ") { i -> read.paragraphs[i].take(80) + " → " + texts.getOrElse(i) { "" }.take(80) }.take(700))
    }

    private fun zprava(read: PhoneRead, texts: List<String>, title: String?, pismo: Int, pozadi: Int): String? {
        val isList = !read.card && read.names.count { it != null } >= PhoneScreen.LIST_MIN
        val side = PhoneScreen.panel(read)
        if (isList) {
            val items = texts.indices.mapNotNull { i ->
                read.boxes.getOrNull(i)?.takeIf { texts[i].isNotBlank() }?.let { b -> texts[i] to floatArrayOf(side[0], b[1], side[2], b[3]) }
            }
            return if (items.isEmpty()) null else TvSender.phoneOverMessage(items, pismo, beside = true)
        }
        val keep = texts.indices.filter { i -> read.sides.getOrElse(i) { 's' } != 'c' && texts[i].isNotBlank() }
        if (keep.isEmpty()) return null
        val msgs = keep.map { i -> texts[i] to read.sides.getOrElse(i) { 's' } }
        val barvy = if (profil.phoneOwn != 0 && profil.phoneIn != 0) intArrayOf(profil.phoneOwn, profil.phoneIn) else null
        return TvSender.phoneMessage(title, msgs, side, pismo, pozadi, barvy = barvy)
    }

    private fun tr(t: String): String = words.getOrPut(t) { preloz(t) }

    private fun karta(text: String): String =
        text.split(PhoneCard.SEP).joinToString(PhoneCard.SEP) { part -> PhoneCard.fixed(part, lang, profil.cornerSpec) ?: tr(part) }

    /** Jeden odstavec podle druhu – jako `TranslatorService.phoneParagraph` (bez rodu mluvčího). */
    private fun odstavec(read: PhoneRead, i: Int, p: String): String {
        val name = read.names.getOrNull(i)
        if (name != null) return if (p.isBlank()) name else name + ": " + tr(p)
        if (p.isBlank()) return p
        if (PhoneScreen.marks(p)) return p.trim()
        if (read.attach.getOrNull(i) == true) return "› " + tr(p)
        if (read.feed) return PhoneScreen.hashtags(p).let { (body, tags) ->
            listOf(if (body.count { it.isLetter() } >= 3) tr(body) else body, tags).filter { it.isNotBlank() }.joinToString(" ") }
        return tr(p)
    }
}

/** Co [TelefonPc] potřebuje od pomocníka `cteni.py` (poslední snímek). */
internal interface PhoneHelper {
    /** Obraz hry v posledním snímku: x0, y0, šířka, výška (bez černých pruhů); null = snímek není. */
    fun rozmer(): IntArray?
    /** Mřížka jasu [cols] × [rows] z obdélníku [box] (zlomky) – jako `PhoneGate.sample`; null = snímek není. */
    fun mrizka(box: FloatArray, cols: Int, rows: Int): IntArray?
    /** Řádky výřezu [box] (zlomky) v plném rozlišení se vzorky jasu; null = snímek není. */
    fun telefon(box: FloatArray): PhoneCrop?
}

internal class PhoneCrop(val lines: List<Pair<MenuLine, IntArray>>, val w: Int, val h: Int, val ms: Long)
