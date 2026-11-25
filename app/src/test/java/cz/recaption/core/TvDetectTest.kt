package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Průvodce sám předvybere televizi nalezenou v domácí síti (jen jediný druh). */
class TvDetectTest {
    @Test fun onlyOneKindIsPreselected() {
        assertEquals(TvType.LG, TvDetect.suggest(listOf("192.168.1.40"), emptyList()))
        assertEquals(TvType.SAMSUNG, TvDetect.suggest(emptyList(), listOf("192.168.1.41")))
        assertNull(TvDetect.suggest(listOf("192.168.1.40"), listOf("192.168.1.41")))
        assertNull(TvDetect.suggest(emptyList(), emptyList()))
    }
}
