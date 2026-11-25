package cz.recaption

import android.content.Context
import cz.recaption.core.TvAnnounce
import cz.recaption.core.TvProfiles
import org.json.JSONObject

/**
 * Uložené nastavení vzhledu a oblastí pro každou televizi LG / Samsung (logika v [TvProfiles]).
 * Hodnoty pro aktivní televizi jsou vždy v běžném nastavení aplikace (Prefs) – stránka Titulky
 * a čtečka tak o profilech nic vědět nemusí. Uložené profily ostatních televizí jsou ve vlastních
 * SharedPreferences `tv_profily` (JSON s typy hodnot, jak je vrací SharedPreferences).
 */
object TvProfile {
    private const val ACTIVE = "active"
    private fun store(c: Context) = c.applicationContext.getSharedPreferences("tv_profily", Context.MODE_PRIVATE)
    private fun main(c: Context) = c.applicationContext.getSharedPreferences("test_preklad", Context.MODE_PRIVATE)

    /** Televize, pro kterou teď platí vzhled (null = žádná se ještě nerozlišila). */
    fun active(c: Context): String? = store(c).getString(ACTIVE, null)

    /** Název aktivní televize pro řádek na stránce Titulky, jen u typu LG / Samsung. */
    fun activeName(c: Context): String? = active(c)?.takeIf { Prefs.tvSend(c) }?.let { TvProfiles.display(it) }

    /**
     * Ohlásila se televize – přepnout na její nastavení. true = hodnoty se změnily (čtečka si je má
     * načíst znovu). U Android TV nic (box je zapojený v jedné televizi).
     */
    @Synchronized
    fun onAnnounce(c: Context, a: TvAnnounce): Boolean {
        if (!Prefs.tvSend(c)) return false
        val sw = TvProfiles.decide(active(c), TvProfiles.id(a)) ?: return false
        val prefs = main(c)
        val all = prefs.all
        val edit = store(c).edit()
        sw.saveTo?.let { old ->
            // hodnoty klíčů, které už k TV nepatří (`obj_on` do 0.97.97), v profilu TV zůstanou – nic se nemaže
            val o = try { store(c).getString("p_$old", null)?.let { JSONObject(it) } } catch (_: Exception) { null } ?: JSONObject()
            for (k in TvProfiles.KEYS) { o.remove(k); all[k]?.let { o.put(k, it) } }
            edit.putString("p_$old", o.toString())
        }
        edit.putString(ACTIVE, sw.loadFrom).apply()
        // nová televize bez uloženého nastavení: zůstává kopie posledního
        val saved = store(c).getString("p_${sw.loadFrom}", null) ?: return false
        val o = try { JSONObject(saved) } catch (_: Exception) { return false }
        val e = prefs.edit()
        for (k in TvProfiles.KEYS) {
            if (!o.has(k)) { e.remove(k); continue }
            when (val v = o.get(k)) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putInt(k, v.toInt())
                is String -> e.putString(k, v)
                else -> {}
            }
        }
        e.apply()
        return true
    }
}
