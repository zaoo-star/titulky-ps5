package cz.recaption.core

/**
 * Přibližná poloha telefonu ve hře z profilu hry (od 1.00-rc21, přání majitele: „per hra si můžeš uložit i přibližnou
 * pozici mobilu, nebo spíš více pozic, protože jsou jiné v autě a při chůzi“): obdélník displeje ve zlomcích obrazu
 * (vlevo, nahoře, vpravo, dole), natočení ([tilt] = sklon řádků jako [PhoneScreen.tilt]) a jméno polohy jen pro záznam.
 */
data class PhoneSpot(val name: String, val box: FloatArray, val tilt: Float = 0f) {
    override fun equals(other: Any?): Boolean =
        other is PhoneSpot && name == other.name && box.contentEquals(other.box) && tilt == other.tilt
    override fun hashCode(): Int = (name.hashCode() * 31 + box.contentHashCode()) * 31 + tilt.hashCode()
}

/**
 * Kde telefon hledat (od 1.00-rc21). Dřív se pokaždé četl široký pás vpravo od minimapy ([PhoneScreen.ZONE_L]…) – velký
 * výřez = pomalejší čtení (SMS v autě se ukázaly pozdě) a víc nápisů ze scény. Teď:
 * - telefon nalezený nedávno ([RECENT_MS]) → jen kolem něj (s okrajem [MARGIN], telefon se při chůzi pohybuje),
 * - jinak polohy z profilu hry ([PhoneSpot]) – každé druhé čtení, mezi nimi široký pás (náhled zprávy vpravo dole
 *   a hra, která po aktualizaci telefon drží jinde),
 * - bez profilu jen široký pás jako dřív.
 * Rychlá kontrola profilu: telefon nalezený mimo všechny polohy profilu [MISFITS]× za sebou = hra vypadá jinak → polohy
 * profilu se do konce překladu nepoužijí (měří se jen z obrazu).
 */
class PhoneZone(private val spots: List<PhoneSpot>) {
    private var last: FloatArray? = null
    private var lastAt = -1L
    private var turn = 0
    private var misfits = 0

    /** Polohy profilu platí (nebyly zamítnuté kontrolou). */
    var spotsOk = spots.isNotEmpty()
        private set

    /**
     * Výřez pro další čtení (zlomky obrazu). [open] = výřez kolem displeje z brány „telefon je otevřený“ ([PhoneGate.zone]) –
     * s ním se široký pás nečte nikdy; telefon nalezený nedávno má přednost (text bývá přesnější než hrany).
     */
    fun next(now: Long, open: FloatArray? = null): FloatArray {
        val l = last
        if (l != null && now - lastAt <= RECENT_MS) return around(l)
        if (open != null) return open
        if (spotsOk && (turn++ % 2 == 0)) {
            val u = floatArrayOf(spots.minOf { it.box[0] }, spots.minOf { it.box[1] }, spots.maxOf { it.box[2] }, spots.maxOf { it.box[3] })
            return around(u)
        }
        return WIDE
    }

    /** Výsledek čtení: obdélník nalezeného telefonu (zlomky), nebo null (telefon není). */
    fun found(box: FloatArray?, now: Long) {
        if (box == null) { if (now - lastAt > RECENT_MS) last = null; return }
        last = box; lastAt = now
        if (!spotsOk) return
        if (spots.any { overlap(it.box, box) >= FIT }) misfits = 0
        else if (++misfits >= MISFITS) spotsOk = false
    }

    /** Poloha z profilu, ve které telefon je (nejvíc se překrývá), nebo null. */
    fun spotOf(box: FloatArray): PhoneSpot? = spots.filter { overlap(it.box, box) >= FIT }.maxByOrNull { overlap(it.box, box) }

    companion object {
        val WIDE = floatArrayOf(PhoneScreen.ZONE_L, PhoneScreen.ZONE_T, PhoneScreen.ZONE_R, PhoneScreen.ZONE_B)
        const val RECENT_MS = 3000L
        const val MARGIN = 0.06f
        const val MISFITS = 2
        /** Jaká část nalezeného textu musí ležet v poloze profilu. */
        const val FIT = 0.6f

        /** Obdélník rozšířený o [MARGIN] a oříznutý na široký pás. */
        fun around(b: FloatArray): FloatArray = floatArrayOf(
            maxOf(WIDE[0], b[0] - MARGIN), maxOf(WIDE[1], b[1] - MARGIN), minOf(WIDE[2], b[2] + MARGIN), minOf(WIDE[3], b[3] + MARGIN))

        /** Část plochy [b] (nalezený text), která leží v [spot]. */
        fun overlap(spot: FloatArray, b: FloatArray): Float {
            val w = minOf(spot[2], b[2]) - maxOf(spot[0], b[0]); val h = minOf(spot[3], b[3]) - maxOf(spot[1], b[1])
            val area = (b[2] - b[0]) * (b[3] - b[1])
            return if (w <= 0f || h <= 0f || area <= 0f) 0f else w * h / area
        }
    }
}

/**
 * Pohyb telefonu mezi čteními (od 1.00-rc21, majitel: „při chůzi se mobil nakloní, v autě se pohupuje podle dechu postavy“).
 * Čtení textu trvá ~0,3–1 s, telefon se ale hýbe pořád – proto se mezi čteními měří jen posun: jasový profil kolem telefonu
 * (průměry sloupců a řádků, světlý displej na tmavší scéně) se porovná s profilem ze snímku, ze kterého se četlo.
 */
object PhoneMotion {
    /** Profil: průměrný jas sloupců [cols] a řádků [rows] obdélníku [box] (zlomky obrazu). */
    class Profile(val box: FloatArray, val cols: IntArray, val rows: IntArray)

    /** Kolik vzorků má profil napříč (sloupce) a podél (řádky). */
    const val COLS = 160
    const val ROWS = 120

    /** Obdélník profilu kolem telefonu: o něco větší, ať je vidět okraj displeje i po posunu. */
    fun area(phone: FloatArray): FloatArray = floatArrayOf(
        (phone[0] - 0.05f).coerceAtLeast(0f), (phone[1] - 0.05f).coerceAtLeast(0f),
        (phone[2] + 0.05f).coerceAtMost(1f), (phone[3] + 0.05f).coerceAtMost(1f))

    /** Profil z jasové roviny [gray] (0–255 v dolním bajtu) šířky [w] a výšky [h] v obdélníku [box]. */
    fun profile(gray: IntArray, w: Int, h: Int, box: FloatArray): Profile {
        val x0 = (box[0] * w).toInt().coerceIn(0, w - 1); val x1 = (box[2] * w).toInt().coerceIn(x0 + 1, w)
        val y0 = (box[1] * h).toInt().coerceIn(0, h - 1); val y1 = (box[3] * h).toInt().coerceIn(y0 + 1, h)
        return profile(box, x1 - x0, y1 - y0) { x, y -> gray[(y0 + y) * w + x0 + x] and 0xFF }
    }

    /** Profil z libovolného zdroje jasu: [at](x, y) v bodech obdélníku šířky [bw] a výšky [bh]. */
    fun profile(box: FloatArray, bw: Int, bh: Int, at: (Int, Int) -> Int): Profile {
        val nc = minOf(COLS, bw); val nr = minOf(ROWS, bh)
        val cols = LongArray(nc); val rows = LongArray(nr)
        val cn = IntArray(nc); val rn = IntArray(nr)
        for (r in 0 until nr) {
            val y = r * bh / nr
            for (c in 0 until nc) {
                val v = at(c * bw / nc, y)
                cols[c] += v.toLong(); cn[c]++; rows[r] += v.toLong(); rn[r]++
            }
        }
        return Profile(box, IntArray(nc) { (cols[it] / maxOf(1, cn[it])).toInt() }, IntArray(nr) { (rows[it] / maxOf(1, rn[it])).toInt() })
    }

    /**
     * Posun [cur] proti [ref] (stejný obdélník) ve zlomcích obrazu (dx, dy), nebo null, když se nedá spolehlivě určit
     * (profil bez okraje displeje – plochý, nebo shoda horší než bez posunu).
     */
    fun shift(ref: Profile, cur: Profile): Pair<Float, Float>? {
        if (!ref.box.contentEquals(cur.box) || ref.cols.size != cur.cols.size || ref.rows.size != cur.rows.size) return null
        val sx = best(ref.cols, cur.cols) ?: return null
        val sy = best(ref.rows, cur.rows) ?: return null
        return sx * (ref.box[2] - ref.box[0]) / ref.cols.size to sy * (ref.box[3] - ref.box[1]) / ref.rows.size
    }

    /** Nejlepší posun (vzorky, s dopočtem mezi vzorky) [b] proti [a] do čtvrtiny délky; null = plochý profil. */
    fun best(a: IntArray, b: IntArray): Float? {
        val n = a.size
        if (n < 8 || (a.maxOrNull() ?: 0) - (a.minOrNull() ?: 0) < MIN_CONTRAST) return null
        val max = n / 4
        val cost = FloatArray(2 * max + 1)
        for (k in -max..max) {
            var s = 0L; var c = 0
            for (i in 0 until n) { val j = i + k; if (j in 0 until n) { s += kotlin.math.abs(a[i] - b[j]); c++ } }
            cost[k + max] = if (c < n / 2) Float.MAX_VALUE else s.toFloat() / c
        }
        var bi = 0
        for (i in cost.indices) if (cost[i] < cost[bi]) bi = i
        val k = bi - max
        // dopočet mezi vzorky (parabola přes sousedy)
        val frac = if (bi in 1 until cost.size - 1 && cost[bi - 1] < Float.MAX_VALUE && cost[bi + 1] < Float.MAX_VALUE) {
            val l = cost[bi - 1]; val m = cost[bi]; val r = cost[bi + 1]
            val d = l - 2 * m + r
            if (d > 0f) ((l - r) / (2 * d)).coerceIn(-0.5f, 0.5f) else 0f
        } else 0f
        return k + frac
    }

    /**
     * O kolik se telefon posunul mezi dvěma čteními (zlomky obrazu, [b] proti [a]): medián posunů odstavců se stejným
     * textem (čtečka bere pokaždé trochu jiné řádky, okraje sloupce proto kolísají); bez nich posun středu sloupce.
     */
    fun delta(a: PhoneRead, b: PhoneRead): Pair<Float, Float> {
        fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val dx = ArrayList<Float>(); val dy = ArrayList<Float>()
        for (i in a.paragraphs.indices) {
            val ba = a.boxes.getOrNull(i) ?: continue
            val k = key(a.paragraphs[i]).takeIf { it.length >= 4 } ?: continue
            val j = b.paragraphs.indices.firstOrNull { key(b.paragraphs[it]) == k } ?: continue
            val bb = b.boxes.getOrNull(j) ?: continue
            dx += bb[0] - ba[0]; dy += bb[1] - ba[1]
        }
        if (dx.isNotEmpty()) return dx.sorted()[dx.size / 2] to dy.sorted()[dy.size / 2]
        return (b.left + b.right - a.left - a.right) / 2f to b.top - a.top
    }

    /** Nejmenší rozdíl jasů v profilu, aby šlo o okraj displeje (ne tmavá scéna bez telefonu). */
    const val MIN_CONTRAST = 25
}

/**
 * Plynulé sledování telefonu (od 1.00-rc21): překlad se posouvá za změřeným posunem ([target]) jen po malých krocích
 * (každý krok polovina zbytku, nic neskočí) a malé pohupování (dech postavy v autě, pod [deadband]) ho nehýbe vůbec –
 * panel vedle telefonu tak necuká; překlad přes text má mrtvé pásmo menší, ať zůstane na původním textu.
 */
class PhoneFollow(private val deadband: Float) {
    /** Posun, o který je překlad právě posunutý (zlomky obrazu). */
    var x = 0f; private set
    var y = 0f; private set
    private var tx = 0f
    private var ty = 0f

    /** Nový změřený posun telefonu (proti místu, kde je překlad položený). */
    fun target(dx: Float, dy: Float) {
        // mrtvé pásmo kolem toho, kam už překlad míří (pohupování tam a zpět ho nerozhýbe)
        if (kotlin.math.abs(dx - tx) >= deadband || kotlin.math.abs(dy - ty) >= deadband ||
            kotlin.math.abs(dx - x) >= deadband * 2 || kotlin.math.abs(dy - y) >= deadband * 2) { tx = dx; ty = dy }
    }

    /** Jeden krok (~100 ms): true = posun překladu se změnil a je potřeba ho překreslit. */
    fun step(): Boolean {
        val ox = x; val oy = y
        x += (tx - x) * 0.5f; y += (ty - y) * 0.5f
        if (kotlin.math.abs(tx - x) < SNAP) x = tx
        if (kotlin.math.abs(ty - y) < SNAP) y = ty
        return kotlin.math.abs(x - ox) >= SNAP / 2 || kotlin.math.abs(y - oy) >= SNAP / 2
    }

    /**
     * Překlad se položil znovu na nové místo z čtení: dosavadní posun se přepočte, ať se nic nepohne skokem
     * ([dx], [dy] = staré místo − nové místo), a cíl je nové místo (posun 0).
     */
    fun rebase(dx: Float, dy: Float) {
        x += dx; y += dy
        // nové místo se od položeného liší málo (čtečka vzala o řádek víc / míň) → nic se nehýbe
        val still = kotlin.math.abs(x) < deadband && kotlin.math.abs(y) < deadband
        tx = if (still) x else 0f; ty = if (still) y else 0f
    }

    fun reset() { x = 0f; y = 0f; tx = 0f; ty = 0f }

    companion object {
        /** Pod jeden bod obrazu 1280 se nehýbe. */
        const val SNAP = 0.0008f
        /** Mrtvé pásmo panelu vedle telefonu a překladu přes text (zlomky obrazu). */
        const val PANEL = 0.012f
        const val OVER = 0.003f
    }
}
