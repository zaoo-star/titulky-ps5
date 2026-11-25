package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Vlastní slovník hráče má přednost i před jménem z profilu hry (od 0.97.54). */
class GlossaryUserTest {
    @Test fun jmenoZProfiluZustava() {
        val g = Glossary(listOf("Safehouse"), mapOf("safehouse" to "úkryt"))
        assertEquals(listOf("Safehouse"), g.prepare("Go to the Safehouse").names)
    }

    @Test fun vlastniSlovnikPrebijeJmeno() {
        val g = Glossary(listOf("Safehouse"), mapOf("safehouse" to "úkryt"), userTerms = listOf("safehouse"))
        assertEquals(listOf("úkryt"), g.prepare("Go to the Safehouse").names)
    }
}
