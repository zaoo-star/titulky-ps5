package cz.recaption.core

/**
 * Nastavení Android TV z telefonu – strana telefonu bez Androidu (3. díl návrhu `navrhy/nastaveni-z-telefonu.md`).
 * Telefon v režimu „Nastavuji TV“ drží, co už televize má ([sent]), a posílá jen rozdíl proti tomu, co je teď
 * v nastavení telefonu pro TV. Neposlané změny se nezahodí: při další změně nebo po [RETRY_MS] se pošlou znovu.
 * Testy `RemoteSyncTest`.
 */
class RemoteSync(initial: Map<String, *>) {
    /** Jak televize odpověděla (nebo že se nedalo spojit). */
    enum class Reply { OK, VERSION, BAD, REJECTED, UNREACHABLE }

    /** Co televize podle posledních potvrzených odpovědí má (jen přenášené volby). */
    private var sent: Map<String, Any?> = LinkedHashMap(RemoteSetup.filter(initial))

    /** Volby, které je potřeba poslat: rozdíl proti tomu, co TV už má (prázdné = nic). */
    @Synchronized fun pending(current: Map<String, *>): Map<String, Any?> = RemoteSetup.diff(sent, current)

    /** TV poslané změny přijala – započítat je (smazaná volba = null). */
    @Synchronized fun confirm(changes: Map<String, Any?>) {
        val next = LinkedHashMap(sent)
        for ((k, v) in changes) if (v == null) next.remove(k) else next[k] = v
        sent = next
    }

    /** Co TV má – k uložení, ať telefon po novém spuštění aplikace pokračuje, kde skončil. */
    @Synchronized fun confirmed(): Map<String, Any?> = LinkedHashMap(sent)

    companion object {
        /** Změny v řadě za sebou (posuvník, psaní) se pošlou najednou až po chvíli klidu. */
        const val DEBOUNCE_MS = 400L
        /** TV nebyla k zastižení: další pokus po této době (nebo hned s další změnou). */
        const val RETRY_MS = 10_000L

        /** Odpověď stránky z TV → výsledek; [body] null = spojení se nepodařilo. */
        fun reply(status: Int, body: String?): Reply {
            if (body == null) return Reply.UNREACHABLE
            if (status != 200) return Reply.BAD
            return when (body.trim()) {
                "OK" -> Reply.OK
                RemoteSetup.ERR_VERSION -> Reply.VERSION
                "REJECTED" -> Reply.REJECTED
                else -> Reply.BAD
            }
        }

        /**
         * Nastavení telefonu pro TV hned po spojení: volby ze snímku TV (TV je hlavní) a k tomu, že jde
         * o Android TV, ne o telefon jako čtečku – podle toho aplikace v telefonu ukazuje kroky a volby.
         */
        fun localValues(s: RemoteSetup.Snapshot): Map<String, Any?> = LinkedHashMap(s.values).apply {
            put("tv_type", TvType.ANDROID.key)
            put("tv_send", false)
            put("reader_device", false)
        }

        /** Jméno TV k zobrazení v pruhu „Nastavuji: …“ (z odkazu, ze snímku, jinak obecně). */
        fun tvName(fromLink: String, fromSnapshot: String): String =
            fromSnapshot.trim().ifEmpty { fromLink.trim() }.ifEmpty { "Android TV" }.take(60)
    }
}
