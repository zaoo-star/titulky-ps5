package cz.recaption

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Služba Usnadnění. Umožní zobrazit titulky přes jiné aplikace i na televizích, které skrývají
 * povolení „Zobrazení přes jiné aplikace“ (typicky Google TV / Chromecast) – bez počítače a ADB.
 *
 * Nečte obsah obrazovky ani nic neodesílá. Jen si pamatuje, která aplikace je v popředí
 * (údaj pro ladicí výpis), a od 0.97.15 hlídá jedno tlačítko ovladače pro rychlou nabídku vzhledu ([QuickLook]).
 */
class TitulkyAccessibility : AccessibilityService() {

    companion object {
        @Volatile var instance: TitulkyAccessibility? = null
            private set

        /** Balíček aplikace, která je právě v popředí (null = nevíme). */
        @Volatile var foregroundPackage: String? = null
            private set

        /** Okna, která nejsou "přepnutí aplikace" (klávesnice, systémová lišta…). */
        private val IGNORED = listOf("com.android.systemui", "inputmethod", "android.ime", "com.google.android.katniss")
    }

    override fun onServiceConnected() {
        instance = this
        updateKeyFilter(Prefs.quickKey(this) != 0)
    }

    /**
     * Tlačítka ovladače si služba bere jen tehdy, když je nastavené tlačítko rychlé nabídky ([QuickLook]) –
     * systém pak posílá přes službu každé tlačítko a čeká na odpověď, takže bez potřeby to nezapínat.
     */
    fun updateKeyFilter(on: Boolean) {
        try {
            val info = serviceInfo ?: return
            val f = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            val want = if (on) info.flags or f else info.flags and f.inv()
            if (want != info.flags) { info.flags = want; serviceInfo = info }
        } catch (_: Throwable) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (IGNORED.any { pkg.contains(it) }) return
        foregroundPackage = pkg
    }

    /**
     * Tlačítka ovladače: jen zvolené tlačítko rychlé nabídky vzhledu (a šipky, Zpět, dokud je nabídka otevřená)
     * – viz [QuickLook]. Všechno ostatní jde dál beze změny (false).
     */
    override fun onKeyEvent(event: android.view.KeyEvent): Boolean = try { QuickLook.onKey(this, event) } catch (_: Throwable) { false }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        QuickLook.close()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
