package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tykání v českém překladu – věty jsou skutečné výstupy offline překladače ze zkoušky. */
class NaturalTest {
    private fun t(en: String, cs: String) = Natural.czech(en, cs)

    @Test fun formalSingularBecomesInformal() {
        assertEquals("Jsi si tím jistý?", t("Are you sure about this?", "Jste si tím jistý?"))
        assertEquals("Víš, kdo jsem?", t("Do you know who I am?", "Víte, kdo jsem?"))
        assertEquals("Můžeš mi pomoci?", t("Can you help me?", "Můžete mi pomoci?"))
        assertEquals("Kolik chceš za to?", t("How much do you want for it?", "Kolik chcete za to?"))
        assertEquals("Nemám ponětí, o čem mluvíš.", t("I have no idea what you're talking about.", "Nemám ponětí, o čem mluvíte."))
        assertEquals("Můžeš se na mě počítat.", t("You can count on me.", "Můžete se na mě počítat."))
        assertEquals("Dlužím ti jednu.", t("I owe you one.", "Dlužím vám jednu."))
        assertEquals("Za to zaplatíš.", t("You'll pay for this.", "Za to zaplatíte."))
        assertEquals("Máš pravdu.", t("You're right.", "Máte pravdu."))
        assertEquals("Není to tvoje chyba.", t("It's not your fault.", "Není to vaše chyba."))
        assertEquals("Viděl jsi to?", t("Did you see that?", "Viděl jste to?"))
        assertEquals("Pomohl bys mi?", t("Would you help me?", "Pomohl byste mi?"))
    }

    @Test fun commandsBecomeInformal() {
        assertEquals("Počkej na mě.", t("Wait for me.", "Počkejte na mě."))
        assertEquals("Nech mě na pokoji.", t("Leave me alone.", "Nechte mě na pokoji."))
        assertEquals("Nepohybuj se!", t("Don't move!", "Nepohybujte se!"))
        assertEquals("Otevři dveře.", t("Open the door.", "Otevřete dveře."))
        assertEquals("Dej to zpět.", t("Give it back.", "Dejte to zpět."))
        assertEquals("Buď opatrný.", t("Be careful.", "Buďte opatrní."))
        assertEquals("Nech ho jít.", t("Let him go.", "Nechte ho jít."))
        assertEquals("Udržuj svůj hlas, mohou nás slyšet.", t("Keep your voice down, they can hear us.", "Udržujte svůj hlas, mohou nás slyšet."))
        assertEquals("Zruš mi lucernu a zůstaň blízko.", t("Hand me the lantern and stay close.", "Zrušte mi lucernu a zůstaňte blízko."))
        assertEquals("Řekni mi pravdu a vezmi si to.", t("Tell me the truth and take it.", "Řekněte mi pravdu a vezměte si to."))
    }

    @Test fun pluralParticipleForOnePersonBecomesSingular() {
        assertEquals("Neměl bys sem přijít.", t("You shouldn't have come here.", "Neměli byste sem přijít."))
        assertEquals("Kdybys mi poslouchal, byli bychom bohatí.", t("If you had listened to me, we would be rich.", "Kdybyste mi poslouchali, byli bychom bohatí."))
        assertEquals("Kam jsi šel?", t("Where did you go?", "Kam jste šli?"))
    }

    @Test fun addressingMorePeopleStaysAsItIs() {
        assertEquals("Vy dva, pojďte se mnou.", t("You two, come with me.", "Vy dva, pojďte se mnou."))
        assertEquals("Jste připraveni?", t("Are you all ready?", "Jste připraveni?"))
        assertEquals("Vy a vaši přátelé by měli odejít.", t("You and your friends should leave.", "Vy a vaši přátelé by měli odejít."))
        assertEquals("Chlapci, počkejte tady.", t("Boys, wait here.", "Chlapci, počkejte tady."))
    }

    @Test fun informalAndUnrelatedTextIsUntouched() {
        for (s in listOf("Co tady děláš?", "Slyšel jsi to?", "Musíme se odsud dostat.", "Nevěřím mu.", "Jestli chceš, počkám chvíli.",
            "Ty dva se nikdy neměli setkat.", "Piráte, stůj!", "Potřebujeme peníze, Jonasi."))
            assertEquals(s, t("Whatever you say.", s))
    }

}
