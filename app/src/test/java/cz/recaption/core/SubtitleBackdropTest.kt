package cz.recaption.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nápis ve scéně na místě titulků ([SubtitleBackdrop]) – jasy vymyšlené podle běžných poměrů (písmo ~15 % rámečku). */
class SubtitleBackdropTest {
    /** Rámeček: [text] bodů písma jasu [ink], zbytek pozadí [bg] (100 bodů). */
    private fun box(bg: Int, ink: Int, text: Int = 15) = IntArray(100) { if (it < text) ink else bg }
    private val whiteOnDarkBox = box(25, 250)
    private val darkOnLightShirt = box(210, 70)
    private val whiteOnBrightSky = box(225, 255)

    @Test fun darkLettersOnLightAreNotASubtitle() {
        val b = SubtitleBackdrop()
        // jméno na světlém dresu: tmavé písmo, nic světlejšího než pozadí
        assertTrue(b.sceneText(darkOnLightShirt, 2))
        // bílý titulek na tmavém i na světlém nebi zůstává
        assertFalse(b.sceneText(whiteOnDarkBox, 2))
        assertFalse(b.sceneText(whiteOnBrightSky, 2))
        // delší řádek se podle tohoto pravidla nevyřadí (věta bez mluvčího přes světlou scénu)
        assertFalse(b.sceneText(darkOnLightShirt, 6))
        // málo bodů = neví se
        assertFalse(b.sceneText(IntArray(5) { 200 }, 1))
    }

    @Test fun learnsThatTheGameDrawsABoxBehindSubtitles() {
        val b = SubtitleBackdrop()
        repeat(5) { b.learn(SubtitleBackdrop.median(whiteOnDarkBox)) }
        assertFalse(b.boxed)                       // ještě málo titulků
        b.learn(SubtitleBackdrop.median(whiteOnDarkBox))
        assertTrue(b.boxed)
        // s podkladem: cokoli bez mluvčího na světlém pozadí není titulek, i delší a i se světlým písmem
        assertTrue(b.sceneText(whiteOnBrightSky, 6))
        assertTrue(b.sceneText(box(160, 240), 4))
        // druhý řádek titulku / popisek zvuku leží v tmavém podkladu – zůstává
        assertFalse(b.sceneText(whiteOnDarkBox, 4))
    }

    @Test fun withoutABoxNothingIsLearned() {
        val b = SubtitleBackdrop()
        // titulky bez podkladu: pozadí jednou tmavé, jednou světlé
        listOf(30, 40, 180, 25, 60, 35, 200, 30).forEach { b.learn(it) }
        assertFalse(b.boxed)
        assertFalse(b.sceneText(whiteOnBrightSky, 6))
        // po světlých titulcích musí zase přijít dost tmavých
        repeat(SubtitleBackdrop.KEEP) { b.learn(30) }
        assertTrue(b.boxed)
        b.reset()
        assertFalse(b.boxed)
        // jedna replika čtená dokola v tmavé scéně podklad nenaučí
        repeat(20) { b.learn(30, "Alex: Wait here.") }
        assertFalse(b.boxed)
    }
}
