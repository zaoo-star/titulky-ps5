package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Rod postav ve slovenském překladu (vymyšlené postavy: Silvia/Sisi a Noemi = žena, Tomas = muž). */
class SlovakGenderTest {
    // postavy přímo v testu – data her jsou od 1.00-rc3 jen na serveru (soukromé repo), ne v aplikaci
    private val cast = Cast(listOf(GameCharacter("Silvia", Gender.F, listOf("Sisi")), GameCharacter("Noemi", Gender.F),
        GameCharacter("Tomas", Gender.M, listOf("Tom", "T-bone"))))
    private fun t(en: String, sk: String, speaker: String? = null) = SlovakGender.fix(cast, en, sk, speaker)

    @Test fun firstPersonFollowsSpeaker() {
        assertEquals("Bola som pripravená.", t("I was ready.", "Bol som pripravený.", "SILVIA"))
        assertEquals("Povedala som ti to.", t("I told you.", "Povedal som ti to.", "Silvia"))
        assertEquals("Nevidela som ho.", t("I didn't see him.", "Nevidel som ho.", "SISI"))
        assertEquals("Keby som to vedela, išla by som s tebou.",
            t("If I knew, I would go with you.", "Keby som to vedel, išiel by som s tebou.", "SILVIA"))
        assertEquals("Som rada, že si tu.", t("I'm glad you're here.", "Som rád, že si tu.", "SILVIA"))
        assertEquals("Prišla som sama.", t("I came alone.", "Prišiel som sám.", "SILVIA"))
        assertEquals("Mohla som to urobiť.", t("I could have done it.", "Mohol som to urobiť.", "SILVIA"))
        assertEquals("Nie som si istá.", t("I'm not sure.", "Nie som si istý.", "SILVIA"))
        assertEquals("Chcela som ti zavolať.", t("I wanted to call you.", "Chcel som ti zavolať.", "SILVIA"))
        assertEquals("Myslela som, že je to krásna vec… nie, bola som šťastná.",
            t("I thought it was nice… no, I was happy.", "Myslel som, že je to krásna vec… nie, bol som šťastný.", "SILVIA"))
    }

    @Test fun maleAndUnknownStay() {
        assertEquals("Bol som pripravený.", t("I was ready.", "Bol som pripravený.", "TOMAS"))
        assertEquals("Bol som pripravený.", t("I was ready.", "Bola som pripravená.", "TOMAS"))
        assertEquals("Išiel som domov.", t("I went home.", "Išla som domov.", "TOMAS"))
        assertEquals("Bol som pripravený.", t("I was ready.", "Bol som pripravený.", "COP"))
        assertEquals("Bol som pripravený.", t("I was ready.", "Bol som pripravený."))
    }

    @Test fun secondPersonFollowsAddressee() {
        assertEquals("Sisi, mala si pravdu.", t("Sisi, you were right.", "Sisi, mal si pravdu."))
        assertEquals("Bola si pripravená, Silvia?", t("Were you ready, Silvia?", "Bol si pripravený, Silvia?"))
        assertEquals("Silvia, buď opatrná.", t("Silvia, be careful.", "Silvia, buď opatrný."))
        // „si“ je i zvratné zájmeno: s „he“ v originálu může patřit k němu – nic
        assertEquals("Sisi, vzal si to sám.", t("Sisi, he took it himself.", "Sisi, vzal si to sám."))
        // oslovený muž zůstává
        assertEquals("Tomas, mal si pravdu.", t("Tomas, you were right.", "Tomas, mal si pravdu."))
    }

    @Test fun thirdPersonAfterName() {
        assertEquals("Silvia išla domov.", t("Silvia went home.", "Silvia išiel domov."))
        assertEquals("Noemi už bola preč.", t("Noemi was already gone.", "Noemi už bol preč."))
        assertEquals("Noemi je pripravená.", t("Noemi is ready.", "Noemi je pripravený."))
        assertEquals("Noemi sa vrátila a povedala, že príde.",
            t("Noemi came back and said she's coming.", "Noemi sa vrátil a povedal, že príde."))
        assertEquals("Silvia si vzala auto.", t("Silvia took the car.", "Silvia si vzal auto."))
        assertEquals("Silvia mohla ísť.", t("Silvia could go.", "Silvia mohol ísť."))
        assertEquals("Noemi mi včera zavolala.", t("Noemi called me yesterday.", "Noemi mi včera zavolal."))
        assertEquals("Zavolala Noemi.", t("Noemi called.", "Zavolal Noemi."))
        // muž zůstává
        assertEquals("Tomas išiel domov.", t("Tomas went home.", "Tomas išiel domov."))
    }

    @Test fun doubtfulLeftAlone() {
        // podstatné jméno na -ol / -l není příčestí
        assertEquals("Silvia má nový hotel.", t("Silvia has a new hotel.", "Silvia má nový hotel."))
        assertEquals("Silvia kostol nevidí.", t("Silvia doesn't see the church.", "Silvia kostol nevidí."))
        // „Vzal ju Tomas“ – podmětem je muž, Silvia je předmět
        assertEquals("Tomas vzal Silviu domov.", t("Tomas took Silvia home.", "Tomas vzal Silviu domov."))
        // bez postav se nic nemění
        assertEquals("Bol som pripravený.", SlovakGender.fix(Cast.EMPTY, "I was ready.", "Bol som pripravený.", "SILVIA"))
    }

    @Test fun glossaryUsesSlovakForSk() {
        val g = Glossary(cast = cast)
        assertEquals("Bola som pripravená.", g.genders(Lang.SK, "I was ready.", "Bol som pripravený.", "SILVIA"))
        assertEquals("Byla jsem připravená.", g.genders(Lang.CS, "I was ready.", "Byl jsem připravený.", "SILVIA"))
        assertEquals("Bol som pripravený.", g.genders(Lang.PL, "I was ready.", "Bol som pripravený.", "SILVIA"))
    }
}
