package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Hlídání obrazu: kdy spojení s PS5 navázat znovu (0.97 – výpadky Wi-Fi už spojení neruší hned). */
class Ps5WatchdogTest {
    private fun w(connected: Long, noFrames: Long, noInput: Long) = Ps5.watchdog(connected, noFrames, noInput)

    @Test fun obrazChodi() = assertEquals(Ps5.Watchdog.OK, w(60_000, 100, 100))

    @Test fun hnedPoSpojeniSeNehlida() = assertEquals(Ps5.Watchdog.OK, w(5_000, 5_000, 5_000))

    @Test fun kratkyVypadekSiteSeVydrzi() {
        // 0.95: 12 s bez dat z PS5 (telefon na 2,4 GHz, zhasnutý displej) → spojení se rušilo, PS5 hlásila odpojení
        assertEquals(Ps5.Watchdog.OK, w(120_000, 15_000, 15_000))
        assertEquals(Ps5.Watchdog.OK, w(120_000, 40_000, 40_000))
    }

    @Test fun dlouhyVypadekSiteZnovu() = assertEquals(Ps5.Watchdog.NETWORK, w(120_000, 46_000, 46_000))

    @Test fun dataChodiAleDekoderStoji() = assertEquals(Ps5.Watchdog.DECODER, w(120_000, 13_000, 200))
}

/** Remote Play převzalo jiné zařízení se stejným účtem (0.97). */
class Ps5OtherDeviceTest {
    @Test fun uzSePouziva() = assertEquals(true, Ps5.otherDevice(Ps5.QUIT_RP_IN_USE, "Remote Play on Console is already in use"))
    @Test fun odpojeniSDuvodem() = assertEquals(true, Ps5.otherDevice(Ps5.QUIT_REMOTE_DISCONNECTED, "Another device connected"))
    @Test fun obycejneOdpojeni() = assertEquals(false, Ps5.otherDevice(Ps5.QUIT_REMOTE_DISCONNECTED, "Server shutting down"))
    @Test fun odmitnuti() = assertEquals(false, Ps5.otherDevice(3, "Connection Refused in Session Request"))
}
