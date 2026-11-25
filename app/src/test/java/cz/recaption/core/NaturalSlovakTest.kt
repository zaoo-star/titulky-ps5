package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tykání ve slovenském překladu – věty ve tvaru, jaký píše offline překladač (vykání jedné osobě). */
class NaturalSlovakTest {
    private fun t(en: String, sk: String) = Natural.slovak(en, sk)

    @Test fun formalSingularBecomesInformal() {
        assertEquals("Si si istý?", t("Are you sure?", "Ste si istý?"))
        assertEquals("Vieš, kto som?", t("Do you know who I am?", "Viete, kto som?"))
        assertEquals("Môžeš mi pomôcť?", t("Can you help me?", "Môžete mi pomôcť?"))
        assertEquals("Koľko za to chceš?", t("How much do you want for it?", "Koľko za to chcete?"))
        assertEquals("Máš pravdu.", t("You're right.", "Máte pravdu."))
        assertEquals("Potrebuješ pomoc.", t("You need help.", "Potrebujete pomoc."))
        assertEquals("Za to zaplatíš.", t("You'll pay for this.", "Za to zaplatíte."))
        assertEquals("Ty si blázon.", t("You're crazy.", "Vy ste blázon."))
        assertEquals("Si pripravený?", t("Are you ready?", "Ste pripravení?"))
    }

    @Test fun pluralParticiplesAtAuxiliary() {
        assertEquals("Videl si to?", t("Did you see that?", "Videli ste to?"))
        assertEquals("Kde si bol?", t("Where were you?", "Kde ste boli?"))
        assertEquals("Pomohol by si mi?", t("Would you help me?", "Pomohli by ste mi?"))
        assertEquals("Mal si ísť.", t("You should have gone.", "Mali ste ísť."))
        assertEquals("Keby si prišiel skôr…", t("If you had come earlier…", "Keby ste prišli skôr…"))
        assertEquals("Nie si sám.", t("You're not alone.", "Nie ste sám."))
    }

    @Test fun pronouns() {
        assertEquals("Dlžím ti jednu.", t("I owe you one.", "Dlžím vám jednu."))
        assertEquals("Ďakujem ti.", t("Thank you.", "Ďakujem vám."))
        assertEquals("Nájdem ťa.", t("I'll find you.", "Nájdem vás."))
        assertEquals("Toto je pre teba.", t("This is for you.", "Toto je pre vás."))
        assertEquals("Prídem k tebe.", t("I'll come to you.", "Prídem k vám."))
        assertEquals("Nie je to tvoja chyba.", t("It's not your fault.", "Nie je to vaša chyba."))
        assertEquals("Kde je tvoje auto?", t("Where's your car?", "Kde je vaše auto?"))
        assertEquals("Idem s tebou.", t("I'm going with you.", "Idem s vami."))
    }

    @Test fun commandsBecomeInformal() {
        assertEquals("Poď so mnou.", t("Come with me.", "Poďte so mnou."))
        assertEquals("Počkaj na mňa.", t("Wait for me.", "Počkajte na mňa."))
        assertEquals("Pozri sa na to.", t("Look at this.", "Pozrite sa na to."))
        assertEquals("Choď!", t("Go!", "Choďte!"))
        assertEquals("Nehýb sa!", t("Don't move!", "Nehýbte sa!"))
        assertEquals("Daj mi to.", t("Give it to me.", "Dajte mi to."))
        assertEquals("Buď opatrný.", t("Be careful.", "Buďte opatrní."))
        assertEquals("Vráť to.", t("Give it back.", "Vráťte to."))
        assertEquals("Neboj sa.", t("Don't worry.", "Nebojte sa."))
        assertEquals("Pokračuj.", t("Keep going.", "Pokračujte."))
        assertEquals("Povedz mi pravdu.", t("Tell me the truth.", "Povedzte mi pravdu."))
    }

    @Test fun severalPeopleStayPlural() {
        assertEquals("Vy dvaja, poďte so mnou.", t("You two, come with me.", "Vy dvaja, poďte so mnou."))
        assertEquals("Všetci, počkajte tu.", t("Everyone, wait here.", "Všetci, počkajte tu."))
        assertEquals("Chlapci, ste pripravení?", t("Guys, are you ready?", "Chlapci, ste pripravení?"))
    }

    @Test fun unknownOrAmbiguousStays() {
        // neznámé sloveso na -íte zůstane (raději nic než špatný tvar)
        assertEquals("Prečo kričíte?", t("Why are you yelling?", "Prečo kričíte?"))
        // tykání zůstane, jak je
        assertEquals("Čo tu robíš?", t("What are you doing here?", "Čo tu robíš?"))
        // příčestí bez „ste“ se nemění (mluví se o někom jiném)
        assertEquals("Boli tam včera.", t("They were there yesterday.", "Boli tam včera."))
        // „pripravení“ bez „ste“ (o jiných lidech) zůstane
        assertEquals("Vojaci sú pripravení.", t("The soldiers are ready.", "Vojaci sú pripravení."))
    }

    @Test fun byLanguage() {
        assertEquals("Máš pravdu.", Natural.fix(Lang.SK, "You're right.", "Máte pravdu."))
        assertEquals("Máš pravdu.", Natural.fix(Lang.CS, "You're right.", "Máte pravdu."))
        assertEquals("Máte pravdu.", Natural.fix(Lang.PL, "You're right.", "Máte pravdu."))
    }
}
