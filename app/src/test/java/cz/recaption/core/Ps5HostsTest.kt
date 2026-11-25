package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Víc spárovaných PlayStationů: seznam, stejná konzole podle MAC, starší uložení bez seznamu. */
class Ps5HostsTest {
    private fun host(ip: String, name: String, mac: Int) =
        Ps5Host(ip, name, ByteArray(16) { 1 }, ByteArray(16) { 2 }, ByteArray(6) { if (it == 5) mac.toByte() else 0 })

    private val a = host("192.168.1.10", "PS5-A", 1)
    private val b = host("192.168.1.11", "PS5-B", 2)

    @Test fun roundTrip() {
        val list = listOf(a, b)
        assertEquals(list, Ps5Hosts.parse(Ps5Hosts.serialize(list)))
        assertEquals(emptyList<Ps5Host>(), Ps5Hosts.parse(null))
        assertEquals(listOf(a), Ps5Hosts.parse(a.serialize() + "\nnesmysl"))
    }

    @Test fun olderSaveHasOnlyTheActiveOne() {
        // kdo spároval před 0.97.67, má jen `ps5_host` – seznam z něj vznikne sám, nic se neztratí
        assertEquals(listOf(a), Ps5Hosts.withActive(emptyList(), a))
        assertEquals(listOf(b, a), Ps5Hosts.withActive(listOf(a), b))
        assertEquals(emptyList<Ps5Host>(), Ps5Hosts.withActive(emptyList(), null))
    }

    @Test fun sameConsoleByMacEvenWithNewAddress() {
        val moved = a.withIp("192.168.1.50")
        assertTrue(Ps5Hosts.same(a, moved))
        assertEquals(listOf(moved, b), Ps5Hosts.upsert(listOf(a, b), moved))
        assertEquals(listOf(moved, b), Ps5Hosts.withActive(listOf(a, b), moved))
        assertEquals(listOf(b), Ps5Hosts.remove(listOf(a, b), moved))
    }

    @Test fun listIsBounded() {
        val many = (1..10).map { host("192.168.1.${100 + it}", "PS5-$it", it + 10) }
        val list = many.fold(emptyList<Ps5Host>()) { l, h -> Ps5Hosts.upsert(l, h) }
        assertEquals(Ps5Hosts.MAX, list.size)
        assertEquals("PS5-10", list.first().name)
    }
}
