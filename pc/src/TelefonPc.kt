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
    /**
     * Překlad textů z telefonu najednou (DeepL jedním dotazem): [texty] a ke každému mluvčí pro rod v 1. osobě
     * (příchozí zprávy píše kontakt z nadpisu), nebo null. Vrací stejně dlouhý seznam.
     */
    private val preloz: (texty: List<String>, mluvci: List<String?>) -> List<String>,
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
    private var iconAt = 0L
    private var iconMiss = 0
    /** Měření zpoždění pro záznam: kdy brána poprvé viděla otevřený telefon a kdy ikona poprvé chyběla. */
    private var otevrenoAt = 0L
    private var chybiOd = 0L

    /** Obraz hry v posledním snímku (x0, y0, šířka, výška) – okno chiaki-ng mívá černé pruhy. */
    @Volatile private var video: IntArray? = null
    /** Displej otevřeného telefonu (zlomky obrazu hry) – čte ho vlákno titulků, proto zvlášť a nestálý. */
    @Volatile private var displej: FloatArray? = null
    /** Žádost z vlákna titulků (menu hry, obraz není): telefon schovat při příští kontrole. */
    @Volatile private var schovat = false
    /** Čas snímku poslední kontroly ikony ovládání telefonu a co ukázala (true = ovládání telefonu je vidět). */
    @Volatile private var stavAt = 0L
    @Volatile private var ikonaNaposled = false
    /** Žádost z vlákna titulků: zkontrolovat ikonu hned, ne až za sekundu. */
    @Volatile private var chciKontrolu = false

    /**
     * Leží bod ([cx], [cy] – zlomky celého snímku [fw] × [fh]) v některém místě, kde hra ukazuje telefon? Tam se text
     * jako titulek ukáže, až kontrola po jeho objevení potvrdí, že telefon otevřený není ([zavrenoPo]).
     */
    fun vMisteTelefonu(fw: Int, fh: Int, cx: Float, cy: Float): Boolean {
        if (fw <= 0 || fh <= 0) return false
        val v = video ?: intArrayOf(0, 0, fw, fh)
        if (v[2] <= 0 || v[3] <= 0) return false
        val x = (cx * fw - v[0]) / v[2]; val y = (cy * fh - v[1]) / v[3]
        return spots.any { b -> x in b[0]..b[2] && y in b[1]..b[3] }
    }

    /**
     * Telefon zavřený podle kontroly ze snímku po čase [od]: true = zavřený, false = ovládání telefonu je vidět,
     * null = takovou kontrolu ještě nemám (požádá o ni hned).
     */
    /** Ovládání telefonu bylo při poslední kontrole vidět (telefon se otevírá) – menu hry se teď nečte. */
    val ikonaVidet: Boolean get() = ikonaNaposled

    fun zavrenoPo(od: Long): Boolean? {
        if (stavAt >= od) return !ikonaNaposled
        chciKontrolu = true
        return null
    }

    /** Displej otevřeného telefonu jako zlomky celého snímku [fw] × [fh] – titulky se z jeho místa nečtou. */
    fun boxVeSnimku(fw: Int, fh: Int): FloatArray? {
        val b = displej ?: return null
        val v = video ?: return null
        if (fw <= 0 || fh <= 0) return null
        return floatArrayOf((v[0] + b[0] * v[2]) / fw, (v[1] + b[1] * v[3]) / fh, (v[0] + b[2] * v[2]) / fw, (v[1] + b[3] * v[3]) / fh)
    }

    private fun d(s: String) { if (s != diag) { diag = s; log(s) } }

    /** Z jiného vlákna: telefon schovat při příští kontrole ([tick]). */
    fun schovejPozdeji() { schovat = true; displej = null }

    /** Telefon pryč (menu hry, konec obrazu). */
    fun schovej(send: (String) -> Unit) {
        tracker = PhoneTracker(); grid = null
        if (shown) { shown = false; shownBase = null; send(TvSender.PHONE_HIDE); log("TELEFON schovan") }
    }

    /**
     * Jedna kontrola (nejvýš 1× za 1 s, ukázaný telefon 0,6 s); [helper] čte z posledního snímku (jen obraz hry bez
     * černých pruhů, [PhoneHelper.rozmer]). [pismo] / [pozadi] = vzhled pruhu pro panel v TV.
     */
    fun tick(now: Long, helper: PhoneHelper, pismo: Int, pozadi: Int, send: (String) -> Unit, snimekAt: Long = now) {
        if (!zapnuto) return
        if (schovat) { schovat = false; open.reset(); displej = null; schovej(send); checkAt = now; return }
        val hned = chciKontrolu
        // ukázaný překlad: jen ikonu ovládání vpravo dole hlídat často (levné) – po zavření telefonu pryč hned,
        // ne až po dalším celém čtení (majitel 25. 11. 2026: „když telefon schovám, dlouho ještě zůstane překlad“)
        if (shown && now - checkAt < 600 && (now - iconAt >= 200 || hned)) {
            iconAt = now
            val v0 = helper.rozmer() ?: return
            val (ib, n) = PhoneGate.iconBox(v0[2], v0[3])
            val ikona = helper.mrizka(ib, n, n)?.let { PhoneGate.icon(it, n) } ?: return
            chciKontrolu = false; ikonaNaposled = ikona; stavAt = snimekAt
            iconMiss = if (ikona) 0 else iconMiss + 1
            if (!ikona && (iconMiss == 1 || chybiOd == 0L)) chybiOd = snimekAt
            if (ikona) chybiOd = 0L
            if (iconMiss >= 2) {
                iconMiss = 0; open.reset(); displej = null; grid = null; tracker = PhoneTracker()
                shown = false; shownBase = null; send(TvSender.PHONE_HIDE)
                log("TELEFON brana zavreny (ovladani) – schovan hned (${now - chybiOd} ms od prvni chybejici ikony)")
            }
            return
        }
        // nový obsah se potvrzuje druhým čtením – to přijde hned, ne až za sekundu (telefon se překládal pomalu)
        if (!hned && now - checkAt < (if (tracker.waiting) 250 else if (shown) 600 else 400)) return
        checkAt = now
        chciKontrolu = false
        val v = helper.rozmer() ?: return
        video = v
        val fw = v[2]; val fh = v[3]
        if (fw < 320 || fh < 180) return
        val G = PhoneGate
        val (iconBox, iconN) = G.iconBox(fw, fh)
        val icon = helper.mrizka(iconBox, iconN, iconN)?.let { G.icon(it, iconN) } ?: return
        ikonaNaposled = icon; stavAt = snimekAt
        val quad = if (!icon) null else helper.mrizka(PhoneZone.WIDE, G.COLS, G.ROWS)?.let { G.quad(it, fw, fh, spots) }
        if (!icon && (iconMiss == 0 || chybiOd == 0L)) chybiOd = snimekAt
        if (icon) chybiOd = 0L
        if (open.update(icon, quad)) {
            if (open.open) otevrenoAt = snimekAt
            log("TELEFON brana " + (if (open.open) "otevreny" else "zavreny (${open.reason})") +
                (if (!open.open && shown && chybiOd > 0) " (${now - chybiOd} ms od prvni chybejici ikony)" else ""))
        }
        val b = open.box
        displej = if (open.open) b else null
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
        // seznam konverzací se nepřekládá, jen otevřená konverzace (přání majitele 25. 11. 2026)
        if (seznam(read0)) {
            if (shown) { shown = false; shownBase = null; send(TvSender.PHONE_HIDE) }
            log("TELEFON seznam zpráv – nepřekládá se")
            return
        }
        // vlastní zprávy (modrá bublina) vpravo, příchozí vlevo – podle barvy pozadí odstavce jako v aplikaci (`withPhoneColors`)
        val read = (if (read0.card || read0.feed) read0 else try {
            S.withBubbleColors(read0, read0.boxes.map { b -> b?.let { helper.pixely(it)?.let { px -> S.blueness(px) } } })
        } catch (_: Exception) { read0 }).let { stranyPodlePolohy(it) }.let { bezUseknute(it) }
        if (read.paragraphs.isEmpty()) return
        val prekladOd = System.currentTimeMillis()
        pripravPreklady(read)
        val prekladMs = System.currentTimeMillis() - prekladOd
        val texts = if (read.card) paras.map { karta(it) } else read.paragraphs.indices.map { i -> odstavec(read, i, read.paragraphs[i]) }
        val title = if (read.card) read.title?.let { karta(it) } else read.title
        val msg = zprava(read, texts, title, pismo, pozadi) ?: return
        send(msg)
        shown = true; shownBase = read; shownTexts = texts; shownTitle = title
        log("TELEFON " + read.paragraphs.indices.joinToString(" ¦ ") { i -> read.paragraphs[i].take(80) + " → " + texts.getOrElse(i) { "" }.take(80) }.take(700))
        log("TELEFON strany ${read.sides.joinToString("")} · cas: cteni ${crop.ms} ms, preklad $prekladMs ms" + (if (otevrenoAt > 0) ", od otevreni ${System.currentTimeMillis() - otevrenoAt} ms" else ""))
    }

    /**
     * Konverzace, kde barvy bublin nevyšly (žádná strana vlevo / vpravo, přání majitele 26. 11. 2026: „ať to vypadá jako
     * reálný mobil s chatem“ – na fotce z TV byl jen seznam): strana podle polohy bubliny v displeji – vlastní zprávy
     * jsou zarovnané doprava, příchozí doleva. Jen u konverzace s nadpisem (jméno kontaktu), ne u karty ani příspěvku.
     */
    private fun stranyPodlePolohy(r: PhoneRead): PhoneRead {
        if (r.card || r.feed || r.title == null || r.sides.any { it == 'p' || it == 'l' }) return r
        val sirka = r.right - r.left
        if (sirka <= 0f || r.boxes.size != r.sides.size) return r
        val strany = r.sides.indices.map { i ->
            val b = r.boxes[i]
            if (r.sides[i] == 'c' || b == null) r.sides[i]
            else {
                val vlevo = b[0] - r.left; val vpravo = r.right - b[2]
                // zarovnání na jednu stranu výrazně (široká zpráva přes celý displej zůstane příchozí)
                if (vpravo < vlevo - sirka * 0.08f) 'p' else 'l'
            }
        }
        return r.copy(sides = strany)
    }

    /** Seznam konverzací (řádky se jmény kontaktů), ne otevřená konverzace. */
    private fun seznam(read: PhoneRead) = !read.card && read.names.count { it != null } >= PhoneScreen.LIST_MIN

    private fun zprava(read: PhoneRead, texts: List<String>, title: String?, pismo: Int, pozadi: Int): String? {
        if (seznam(read)) return null
        val side = PhoneScreen.panel(read)
        val keep = texts.indices.filter { i -> read.sides.getOrElse(i) { 's' } != 'c' && texts[i].isNotBlank() && !ODDIL.matches(read.paragraphs[i].trim()) }
        if (keep.isEmpty()) return null
        val msgs = keep.map { i -> texts[i] to read.sides.getOrElse(i) { 's' } }
        val barvy = if (profil.phoneOwn != 0 && profil.phoneIn != 0) intArrayOf(profil.phoneOwn, profil.phoneIn) else null
        return TvSender.phoneMessage(title, msgs, side, pismo, pozadi, barvy = barvy)
    }

    private companion object {
        /** Nadpisy oddílů seznamu zpráv v telefonu – nepřekládají se ani neukazují. */
        val ODDIL = Regex("(?i)(suggested|messages|contacts|recent|favorites)")
    }

    /** Mluvčí odstavce pro rod: příchozí zprávu (bublina vlevo) píše kontakt z nadpisu. */
    private fun mluvci(read: PhoneRead, i: Int): String? = read.title?.takeIf { read.sides.getOrNull(i) == 'l' }

    private fun klic(t: String, kdo: String?) = if (kdo == null) t else "$kdo|$t"

    /** Co se z odstavce překládá (null = nic) – musí sedět s [odstavec]. */
    private fun zdroj(read: PhoneRead, i: Int, p: String): String? {
        if (read.names.getOrNull(i) != null) return p.takeIf { it.isNotBlank() }
        if (p.isBlank() || PhoneScreen.marks(p)) return null
        if (read.feed) return PhoneScreen.hashtags(p).first.takeIf { it.count { c -> c.isLetter() } >= 3 }
        return p
    }

    /** Všechno nepřeložené z čtení přeloží jedním dotazem (dřív odstavec po odstavci – s DeepL několik dotazů za sebou). */
    private fun pripravPreklady(read: PhoneRead) {
        val chybi = LinkedHashMap<String, Pair<String, String?>>()
        if (read.card) {
            (read.paragraphs + listOfNotNull(read.title)).flatMap { it.split(PhoneCard.SEP) }
                .filter { PhoneCard.fixed(it, lang, profil.cornerSpec) == null && it !in words }.forEach { chybi[it] = it to null }
        } else read.paragraphs.forEachIndexed { i, p ->
            val src = zdroj(read, i, p) ?: return@forEachIndexed
            val kdo = mluvci(read, i).takeIf { read.attach.getOrNull(i) != true && read.names.getOrNull(i) == null }
            val k = klic(src, kdo)
            if (k !in words) chybi[k] = src to kdo
        }
        if (chybi.isEmpty()) return
        val v = chybi.values.toList()
        val out = try { preloz(v.map { it.first }, v.map { it.second }) } catch (_: Exception) { v.map { it.first } }
        chybi.keys.forEachIndexed { j, k -> words[k] = out.getOrNull(j) ?: v[j].first }
    }

    /**
     * Horní odstavec useknutý okrajem displeje (konverzace odrolovaná, „…noticed, but shit isn't…“ začíná malým
     * písmenem uprostřed věty) se nepřekládá – překlad kousku věty jen mátl.
     */
    private fun bezUseknute(r: PhoneRead): PhoneRead {
        if (r.card || r.paragraphs.size < 2) return r
        val b = r.boxes.firstOrNull() ?: return r
        val p = r.paragraphs.first().trimStart()
        val nahore = b[1] - r.top < maxOf(r.lineH * 0.8f, 0.012f)
        if (!nahore || r.names.firstOrNull() != null || p.isEmpty() || !p.first().isLowerCase()) return r
        fun <T> d(l: List<T>) = if (l.size == r.paragraphs.size) l.drop(1) else l
        return r.copy(paragraphs = r.paragraphs.drop(1), sides = d(r.sides), images = d(r.images), boxes = d(r.boxes),
            names = d(r.names), attach = d(r.attach))
    }

    private fun tr(t: String, kdo: String? = null): String = words[klic(t, kdo)] ?: words[t]
        ?: preloz(listOf(t), listOf(kdo)).firstOrNull()?.also { words[klic(t, kdo)] = it } ?: t

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
        return tr(p, mluvci(read, i))
    }
}

/** Co [TelefonPc] potřebuje od pomocníka `cteni.py` (poslední snímek). */
internal interface PhoneHelper {
    /** Obraz hry v posledním snímku: x0, y0, šířka, výška (bez černých pruhů); null = snímek není. */
    fun rozmer(): IntArray?
    /** Barvy bodů (0xRRGGBB) obdélníku [box] (zlomky obrazu hry); null = snímek není. */
    fun pixely(box: FloatArray): IntArray?
    /** Mřížka jasu [cols] × [rows] z obdélníku [box] (zlomky) – jako `PhoneGate.sample`; null = snímek není. */
    fun mrizka(box: FloatArray, cols: Int, rows: Int): IntArray?
    /** Řádky výřezu [box] (zlomky) v plném rozlišení se vzorky jasu; [zvetseni] null = malý výřez zvětšit; null = snímek není. */
    fun telefon(box: FloatArray, zvetseni: Float? = null): PhoneCrop?
}

internal class PhoneCrop(val lines: List<Pair<MenuLine, IntArray>>, val w: Int, val h: Int, val ms: Long)
