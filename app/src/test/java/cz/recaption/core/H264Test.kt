package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class H264Test {
    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun keyFrameWithHeader() {
        // SPS (0x67), PPS (0x68), IDR (0x65) – 4bajtové i 3bajtové start kódy
        val f = b(0, 0, 0, 1, 0x67, 1, 2, 0, 0, 0, 1, 0x68, 3, 0, 0, 1, 0x65, 9, 9)
        assertEquals(setOf(7, 8, 5), H264.nalTypes(f))
        assertTrue(H264.isKey(f))
    }

    @Test fun deltaFrameIsNotKey() {
        val p = b(0, 0, 0, 1, 0x41, 5, 6, 7)   // změnový snímek (typ 1)
        assertEquals(setOf(1), H264.nalTypes(p))
        assertFalse(H264.isKey(p))
        assertFalse(H264.isKey(ByteArray(0)))
    }
}
