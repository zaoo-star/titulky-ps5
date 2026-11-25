package cz.recaption.core

/**
 * Víc spárovaných PlayStationů (od 0.97.67, přání majitele: „počítat s tím, že má někdo dvě TV nebo dva PlayStationy“).
 * Aktivní konzole zůstává pod starým klíčem `ps5_host` (nic se aktualizací nemění, spojení ji čte jako dřív); seznam všech
 * spárovaných je navíc pod novým klíčem `ps5_hosts` (řádek = [Ps5Host.serialize]). Kdo seznam ještě nemá, má v něm jen aktivní.
 * Stejná konzole = stejná MAC (adresa v síti se může změnit), bez MAC stejná adresa. Testy `Ps5HostsTest`.
 */
object Ps5Hosts {
    /** Nejvýš tolik konzolí v seznamu (nejstarší vypadne). */
    const val MAX = 6

    fun parse(text: String?): List<Ps5Host> = (text ?: "").lines().mapNotNull { Ps5Host.parse(it.trim()) }

    fun serialize(list: List<Ps5Host>): String = list.joinToString("\n") { it.serialize() }

    /** Je to tatáž konzole (podle MAC, bez ní podle adresy)? */
    fun same(a: Ps5Host, b: Ps5Host): Boolean =
        if (a.mac.any { it != 0.toByte() } && b.mac.any { it != 0.toByte() }) a.mac.contentEquals(b.mac) else a.ip == b.ip

    /** Uložený seznam doplněný o aktivní konzoli (starší uložení seznam nemá). */
    fun withActive(list: List<Ps5Host>, active: Ps5Host?): List<Ps5Host> =
        if (active == null || list.any { same(it, active) }) list.map { h -> if (active != null && same(h, active)) active else h }
        else listOf(active) + list

    /** Přidá nebo nahradí (nové spárování / nová adresa) – nahoru, aby byla poslední použitá první. */
    fun upsert(list: List<Ps5Host>, h: Ps5Host): List<Ps5Host> = (listOf(h) + list.filterNot { same(it, h) }).take(MAX)

    fun remove(list: List<Ps5Host>, h: Ps5Host): List<Ps5Host> = list.filterNot { same(it, h) }
}
