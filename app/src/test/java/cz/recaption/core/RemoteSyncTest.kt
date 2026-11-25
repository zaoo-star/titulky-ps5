package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSyncTest {
    @Test fun sendsOnlyChangedTransferredValues() {
        val s = RemoteSync(mapOf("lang" to "cs", "text_size" to 22, "stats" to "x"))
        assertTrue(s.pending(mapOf("lang" to "cs", "text_size" to 22, "stats" to "y", "tv_type" to "android")).isEmpty())
        assertEquals(mapOf("text_size" to 30), s.pending(mapOf("lang" to "cs", "text_size" to 30)))
    }

    @Test fun confirmedChangesAreNotSentAgain() {
        val s = RemoteSync(mapOf("lang" to "cs", "bold_text" to false))
        val now = mapOf("lang" to "de", "bold_text" to true)
        val p = s.pending(now)
        s.confirm(p)
        assertTrue(s.pending(now).isEmpty())
        assertEquals("de", s.confirmed()["lang"])
    }

    @Test fun unsentChangesStayPendingAfterFailure() {
        val s = RemoteSync(mapOf("lang" to "cs"))
        val now = mapOf("lang" to "pl")
        s.pending(now)   // poslat se nepodařilo – nic se nepotvrdí
        assertEquals(mapOf("lang" to "pl"), s.pending(now))
    }

    @Test fun changeDuringSendingIsKept() {
        val s = RemoteSync(mapOf("text_size" to 22))
        val first = s.pending(mapOf("text_size" to 24))
        // mezitím hráč posunul ještě dál; potvrdí se jen to, co odešlo
        s.confirm(first)
        assertEquals(mapOf("text_size" to 26), s.pending(mapOf("text_size" to 26)))
    }

    @Test fun removedValueIsSentAsNullAndForgotten() {
        val s = RemoteSync(mapOf("dict_cs" to "a=b", "lang" to "cs"))
        val p = s.pending(mapOf("lang" to "cs"))
        assertEquals(mapOf("dict_cs" to null), p)
        s.confirm(p)
        assertFalse("dict_cs" in s.confirmed())
        assertTrue(s.pending(mapOf("lang" to "cs")).isEmpty())
    }

    @Test fun pairedPs5GoesToTv() {
        val s = RemoteSync(emptyMap<String, Any?>())
        assertEquals(setOf("ps5_host", "psn_id"), s.pending(mapOf("ps5_host" to "x", "psn_id" to "y", "phone_accounts" to "z")).keys)
    }

    @Test fun replies() {
        assertEquals(RemoteSync.Reply.OK, RemoteSync.reply(200, "OK\n"))
        assertEquals(RemoteSync.Reply.VERSION, RemoteSync.reply(200, RemoteSetup.ERR_VERSION))
        assertEquals(RemoteSync.Reply.REJECTED, RemoteSync.reply(200, "REJECTED"))
        assertEquals(RemoteSync.Reply.BAD, RemoteSync.reply(200, "BAD"))
        assertEquals(RemoteSync.Reply.BAD, RemoteSync.reply(404, "<html>"))
        assertEquals(RemoteSync.Reply.UNREACHABLE, RemoteSync.reply(0, null))
    }

    @Test fun localValuesAreAndroidTv() {
        val snap = RemoteSetup.Snapshot(1, "Sony", true, mapOf("lang" to "cs"))
        val v = RemoteSync.localValues(snap)
        assertEquals("cs", v["lang"])
        assertEquals(TvType.ANDROID.key, v["tv_type"])
        assertEquals(false, v["reader_device"])
        // to, co se do TV neposílá, se po spojení nepočítá jako změna
        assertTrue(RemoteSync(snap.values).pending(v).isEmpty())
    }

    @Test fun snapshotCarriesDeeplSwitch() {
        val off = RemoteSetup.parseSnapshot(RemoteSetup.snapshot("TV", emptyMap<String, Any?>(), true, false))!!
        assertTrue(off.hasDeeplKey); assertFalse(off.deeplOn)
        // starší TV bez `deepl_on`: zapnuto, když má klíč
        val old = RemoteSetup.parseSnapshot("""{"v":1,"name":"TV","deepl":true,"prefs":{}}""")!!
        assertTrue(old.deeplOn)
    }

    @Test fun tvName() {
        assertEquals("Sony Bravia", RemoteSync.tvName("x", " Sony Bravia "))
        assertEquals("Obývák", RemoteSync.tvName("Obývák", ""))
        assertEquals("Android TV", RemoteSync.tvName("", ""))
    }
}
