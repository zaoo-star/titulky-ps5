package cz.recaption

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import cz.recaption.core.PhoneLogin
import cz.recaption.core.Ps5

/** Jak je toto zařízení připojené k domácí síti (kabel, Wi-Fi 5 GHz, Wi-Fi 2,4 GHz). */
object NetInfo {
    fun kind(ctx: Context): Ps5.Net = try {
        val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> Ps5.Net.UNKNOWN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Ps5.Net.CABLE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val mhz = wm.connectionInfo?.frequency ?: 0
                when {
                    mhz in 2400..2500 -> Ps5.Net.WIFI_24
                    mhz >= 4900 -> Ps5.Net.WIFI_5
                    else -> Ps5.Net.WIFI_5
                }
            }
            else -> Ps5.Net.UNKNOWN
        }
    } catch (_: Throwable) { Ps5.Net.UNKNOWN }

    /**
     * Adresa tohoto zařízení v domácí síti (IPv4), nebo null. Přednost má síť, přes kterou je
     * zařízení právě připojené; jinak první domácí adresa, která se najde.
     */
    fun homeAddress(ctx: Context): String? {
        try {
            val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
                ?.mapNotNull { (it.address as? java.net.Inet4Address)?.hostAddress }
                ?.firstOrNull { PhoneLogin.isHomeAddress(it) }?.let { return it }
        } catch (_: Throwable) {}
        return try {
            java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .flatMap { java.util.Collections.list(it.inetAddresses) }
                .mapNotNull { (it as? java.net.Inet4Address)?.hostAddress }
                .firstOrNull { PhoneLogin.isHomeAddress(it) }
        } catch (_: Throwable) { null }
    }

    /**
     * Je to telefon? Pak aplikace neslouží k překladu, ale jen jako pomocník pro přihlášení.
     * Televize a boxy se poznají bezpečně: nemají mobilní síť. Kdyby se přesto spletla,
     * dá se to v pomocníkovi jedním tlačítkem opravit ([Prefs.forceTv]). V režimu „Nastavuji TV“ ([RemoteClient])
     * běží v telefonu celá aplikace (nastavuje se v ní TV).
     */
    fun isPhone(ctx: Context): Boolean = isHandheld(ctx) && !Prefs.forceTv(ctx) && !RemoteClient.on(ctx)

    /**
     * Je to telefon – bez ohledu na to, jestli na něm zrovna běží pomocník, nebo celá aplikace
     * ([Prefs.forceTv])? Celá aplikace pak nabízí cestu zpět k pomocníkovi.
     */
    /** Telefon/tablet Samsung – má vlastní uspávání aplikací (návod „Nikdy neuspávané aplikace“). */
    fun isSamsungPhone(ctx: Context): Boolean = try {
        isHandheld(ctx) && android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    } catch (_: Throwable) { false }

    fun isHandheld(ctx: Context): Boolean = try {
        val pm = ctx.packageManager
        !isTvDevice(ctx) && pm.hasSystemFeature("android.hardware.telephony") && pm.hasSystemFeature("android.hardware.touchscreen")
    } catch (_: Throwable) { false }

    /** Android TV / box (leanback, televizní režim) – na rozdíl od telefonu a tabletu. */
    fun isTvDevice(ctx: Context): Boolean = try {
        val pm = ctx.packageManager
        pm.hasSystemFeature("android.software.leanback") || pm.hasSystemFeature("android.software.leanback_only") ||
            pm.hasSystemFeature("android.hardware.type.television") || pm.hasSystemFeature("android.software.live_tv") ||
            ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    } catch (_: Throwable) { false }
}
