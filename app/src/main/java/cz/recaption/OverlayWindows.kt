package cz.recaption

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import cz.recaption.core.DiagLog
import cz.recaption.core.OverlayBounds
import cz.recaption.core.WindowBudget
import java.util.Collections
import java.util.WeakHashMap

/**
 * Všechna okna přes hru (pruh, úkol, popisky menu, stav…) se přidávají a odebírají tudy (od 1.0.1, hlášení R932-QZ86):
 * - nikdy nespadne – chyba přidání (plno, chybí oprávnění, systém odmítl) = okno není, aplikace běží dál;
 * - aplikace má nejvýš [WindowBudget.MAX] oken, ať zbude místo pro obrazovky a dialogy aplikace (Samsung má na proces
 *   strop oken – plný strop = Poslat vývojáři „nic nedělá“ a další obrazovka spadne „window count is over max“);
 * - bez oprávnění „zobrazení přes jiné aplikace“ se okno vůbec nezkouší přidat (telefon jako čtečka pro TV LG/Samsung
 *   ho nepotřebuje – dřív se zkoušelo při každém čtení) a po chybě se chvíli nezkouší znovu.
 * Počet a chyby jsou v hlášení o stavu (řádek Usnadnění, „okna“).
 */
object OverlayWindows {
    private val budget = WindowBudget()
    private val live: MutableSet<View> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))
    @Volatile var failures = 0
        private set
    @Volatile private var lastLogged = 0L

    /** Kolik oken přes hru teď aplikace má. */
    fun count(): Int = live.size

    /** Přidá okno (hlavní vlákno); false = nejde (nic nespadne). */
    fun add(wm: WindowManager?, c: Context, v: View, lp: WindowManager.LayoutParams): Boolean {
        if (wm == null) return false
        val now = SystemClock.uptimeMillis()
        if (!budget.mayAdd(live.size, now)) return false
        if (lp.type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY &&
            !(try { Settings.canDrawOverlays(c) } catch (_: Throwable) { false })) {
            budget.failed(now)
            return false
        }
        return try {
            wm.addView(v, lp)
            live += v
            budget.ok()
            true
        } catch (e: Throwable) {
            budget.failed(now)
            failures++
            // do záznamu nejvýš jednou za minutu
            if (now - lastLogged > 60_000 || lastLogged == 0L) {
                lastLogged = now
                DiagLog.add("OKNA|nelze přidat okno (${live.size} oken): ${e.javaClass.simpleName} ${e.message ?: ""}".trim())
            }
            false
        }
    }

    /** Odebere okno (hlavní vlákno); okno, které nebylo přidané, nevadí. */
    fun remove(wm: WindowManager?, v: View) {
        live.remove(v)
        if (wm == null) return
        try { wm.removeView(v) } catch (_: Throwable) {}
    }
}

/**
 * Skupina popisků v jednom okně (od 1.0.1): dřív měl každý popisek menu (až 32), řádek menu vpravo dole, pokyn u předmětu,
 * odstavec telefonu i tlačítko patičky vlastní okno. Teď má skupina jedno okno, které obalí jen viditelné popisky
 * (ne celou obrazovku – mimo ně jde dotyk dál jako dřív), a popisky v něm leží na stejných místech a ve stejné velikosti.
 *
 * Volající s popisky zachází jako dřív s okny: [add] místo přidání okna, [update] místo `updateViewLayout`
 * (a po schování popisků); `LayoutParams` popisku dál nese polohu a velikost na obrazovce (x, y, šířka, výška).
 * Okno vznikne s prvním popiskem; po [DROP_MS] bez viditelného popisku se odebere a [onDrop] vyprázdní seznamy volajícího.
 */
class OverlayLayer(
    private val host: Context,
    private val wm: WindowManager?,
    private val screenW: Int,
    private val newParams: () -> WindowManager.LayoutParams,
    private val onDrop: () -> Unit,
) {
    companion object {
        const val DROP_MS = 60_000L
    }

    private val main = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var rootLp: WindowManager.LayoutParams? = null
    private val kids = ArrayList<Pair<View, WindowManager.LayoutParams>>()
    private var pending = false
    private val dropLater = Runnable { if (kids.none { it.first.visibility == View.VISIBLE }) drop() }

    /** Nový popisek do skupiny (hlavní vlákno); false = okno skupiny nejde vytvořit. */
    fun add(v: View, lp: WindowManager.LayoutParams): Boolean {
        val r = root ?: run {
            val nr = FrameLayout(host).apply { visibility = View.GONE }
            val nlp = newParams()
            if (!OverlayWindows.add(wm, host, nr, nlp)) return false
            root = nr; rootLp = nlp
            nr
        }
        r.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        kids += v to lp
        update()
        return true
    }

    /** Polohy, velikosti nebo viditelnost popisků se změnily – okno se přeměří (jednou za průchod hlavního vlákna). */
    fun update() {
        if (pending) return
        pending = true
        main.post { pending = false; layout() }
    }

    private fun layout() {
        val r = root ?: return
        val rlp = rootLp ?: return
        val shown = ArrayList<Pair<View, IntArray>>()
        for ((v, lp) in kids) {
            if (v.visibility != View.VISIBLE) continue
            val ws = if (lp.width >= 0) View.MeasureSpec.makeMeasureSpec(lp.width, View.MeasureSpec.EXACTLY)
                else View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST)
            val hs = if (lp.height >= 0) View.MeasureSpec.makeMeasureSpec(lp.height, View.MeasureSpec.EXACTLY)
                else View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            v.measure(ws, hs)
            shown += v to intArrayOf(lp.x, lp.y, v.measuredWidth, v.measuredHeight)
        }
        val box = OverlayBounds.union(shown.map { it.second })
        if (box == null) {
            if (r.visibility != View.GONE) r.visibility = View.GONE
            main.removeCallbacks(dropLater)
            main.postDelayed(dropLater, DROP_MS)
            return
        }
        main.removeCallbacks(dropLater)
        for ((v, q) in shown) {
            val flp = (v.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            flp.width = q[2]; flp.height = q[3]
            flp.leftMargin = q[0] - box[0]; flp.topMargin = q[1] - box[1]
            v.layoutParams = flp
        }
        rlp.x = box[0]; rlp.y = box[1]; rlp.width = box[2]; rlp.height = box[3]
        r.visibility = View.VISIBLE
        try { wm?.updateViewLayout(r, rlp) } catch (_: Exception) {}
    }

    /** Okno skupiny pryč (popisky dlouho schované); příště se vytvoří znovu. */
    private fun drop() {
        val r = root ?: return
        root = null; rootLp = null
        r.removeAllViews()
        kids.clear()
        OverlayWindows.remove(wm, r)
        onDrop()
    }

    /** Konec (nové nastavení, konec překladu): okno pryč hned. */
    fun destroy() {
        main.removeCallbacks(dropLater)
        drop()
    }
}
