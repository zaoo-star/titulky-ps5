package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Žádný klíč textu nesmí být ve dvou souborech s texty: při skládání (`I18n.PS`) by pozdější tiše přepsal dřívější
 * (0.97.68: `rs_e_net` z odeslání hlášení přepsal hlášku nastavení z telefonu).
 */
class DuplicateKeysTest {
    @Test fun noKeyInTwoTextFiles() {
        val maps = mapOf(
            "PS5" to PS5_S, "LOGIN" to LOGIN_S, "DIAG" to DIAG_S, "LG" to LG_S, "XBOX" to XBOX_S, "SS" to SS_S,
            "DEEPL" to DEEPL_S, "UI" to UI_S, "ZAZNAM" to ZAZNAM_S, "KAMERA" to KAMERA_S, "HELP" to HELP_S,
            "GAME" to GAME_S, "QUICK" to QUICK_S, "REMOTE" to REMOTE_S, "SETUP" to SETUP_S, "ATV" to ATV_S,
            "MULTI" to MULTI_S, "SEND" to SEND_S,
        )
        val owner = HashMap<String, String>()
        val clashes = ArrayList<String>()
        for ((file, m) in maps) for (k in m.keys) {
            val prev = owner.put(k, file)
            if (prev != null) clashes += "$k ($prev, $file)"
        }
        assertEquals(emptyList<String>(), clashes)
    }
}
