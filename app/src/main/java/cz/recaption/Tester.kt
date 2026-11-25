package cz.recaption

import android.content.Context
import android.content.pm.PackageManager

/**
 * Zkušební verze jen pro testery (od 1.00-rc7, přání majitele): kdo nainstaluje APK ze zkušebního kanálu
 * (`recaption.pages.dev/d/zkusebni/ReCaption.apk`, sestavené z větví s `-Pkanal=zkusebni`, meta-data `recaption.kanal`
 * v manifestu), je tester – aplikace mu nabízí zkušební verze i vydání pro všechny (vyšší z nich). Status se pamatuje,
 * takže tester zůstane testerem i po aktualizaci na vydání z `main`. Vypnout jde řádkem Zkušební verze (vidí ho jen
 * tester); nová instalace testerského APK ho zapne znovu. APK z `main` nikomu zkušební kanál nenabídne.
 */
object Tester {
    private const val FILE = "tester"
    @Volatile private var channel: String? = null

    /** Kanál, ze kterého je tohle APK („zkusebni“ / „latest“); čte se jednou. */
    fun apkChannel(c: Context): String = channel ?: try {
        val ai = c.packageManager.getApplicationInfo(c.packageName, PackageManager.GET_META_DATA)
        ai.metaData?.getString("recaption.kanal") ?: "latest"
    } catch (_: Throwable) { "latest" }.also { channel = it }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Při spuštění: první běh každé verze testerského APK = tester (i když to dřív vypnul a nainstaloval ho znovu). */
    fun refresh(c: Context) {
        if (apkChannel(c) != "zkusebni") return
        val p = prefs(c)
        if (p.getInt("kod", 0) != BuildInfo.VERSION_CODE) p.edit().putBoolean("tester", true).putInt("kod", BuildInfo.VERSION_CODE).apply()
    }

    fun active(c: Context): Boolean = prefs(c).getBoolean("tester", false)
    fun set(c: Context, on: Boolean) = prefs(c).edit().putBoolean("tester", on).apply()
}
