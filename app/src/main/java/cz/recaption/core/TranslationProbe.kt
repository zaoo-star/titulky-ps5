package cz.recaption.core

/**
 * Typické herní repliky pro zkoušku překladu. Zkouška v emulátoru je nechá přeložit stejnou
 * cestou jako titulky ze hry a výsledek uloží do `preklad.txt` – podle něj se ladí, aby překlad
 * zněl přirozeně (viz [Natural]). Věty jsou vybrané tak, aby pokryly oslovení jedné osoby
 * i více lidí, rozkazy, otázky a běžné obraty, na kterých se strojový překlad nejčastěji splete.
 */
object TranslationProbe {
    val LINES: List<String> get() = listOf(
        // otázky na jednu osobu
        "What are you doing here?", "Are you sure about this?", "Do you know who I am?", "Can you help me?",
        "What do you want?", "Where did you get that?", "Did you hear that?", "Are you out of your mind?",
        "How much do you want for it?", "Have you seen my brother?", "Why didn't you tell me?", "Will you come with me?",
        // rozkazy a výzvy
        "Get on your horse.", "Follow me.", "Stay close.", "Hurry up!", "Watch out!", "Get down!", "Take cover!",
        "Hold on.", "Hang on a second.", "Wait for me.", "Leave me alone.", "Shut up.", "Calm down.", "Take it easy.",
        "Give me your gun.", "Put your hands up!", "Drop your weapon!", "Don't move!", "Open the door.", "Give it back.",
        "Tell me the truth.", "Trust me.", "Be careful.", "Look at me.", "Come here.", "Go away.", "Let him go.",
        "Keep your voice down, they can hear us.", "Hand me the lantern and stay close.", "Run for the gate!",
        // běžné obraty
        "I don't know.", "No idea.", "I have no idea what you're talking about.", "Come on, let's go!", "Never mind.",
        "Forget it.", "It doesn't matter.", "I'll take care of it.", "You can count on me.", "I owe you one.",
        "Thanks for your help.", "You're welcome.", "What's going on?", "What happened?", "Where are we going?",
        "I'm going to kill you.", "You'll pay for this.", "I don't trust him.", "He's lying.", "That's not true.",
        "You're right.", "You were right about him.", "I was wrong.", "I'm sorry.", "It's not your fault.",
        "We have to get out of here.", "We're running out of time.", "There's no time to explain.",
        "I've got a bad feeling about this.", "Something's wrong.", "Good job.", "Well done.", "Nice shot!",
        "See you later.", "Take care of yourself.", "I need a drink.", "Let's get this over with.", "That's enough!",
        "Mind your own business.", "What the hell is wrong with you?", "You've got to be kidding me.",
        "I can't believe it.", "It's a long story.", "We had a deal.", "I've been looking for you.",
        "You shouldn't have come here.", "They're coming!", "He got away.", "I think we lost them.",
        "The bridge is out, we have to swim.", "If we keep going like this, they will find us.",
        "I don't think anyone has been here for years.", "Get on your horse, we ride at dawn.",
        // rod podle postavy (GTA VI: Lucia/Lu žena, Jason/Jas muž – CzechGender)
        "LUCIA: I was ready for this.", "LUCIA: I told you I would come back.", "LUCIA: I'm glad you're here.",
        "LUCIA: I thought you were dead, Jason.", "JASON: Lu, you were right.", "JASON: Are you crazy, Lucia?",
        "JASON: Be careful, Lu.", "Lucia went home.", "Lucia is ready.",
        // oslovení více lidí (tykání se tu měnit nesmí)
        "You two, come with me.", "All of you, get out!", "You and your friends should leave.",
        "Boys, get on your horses.", "Are you all ready?", "You guys wait here.",
        // minulý čas a podmínka
        "What did you do?", "Did you see that?", "You lied to me.", "You could have told me.", "Would you help me?",
        "If you had listened to me, we would be rich.", "You said you would come.", "Where have you been?",
        // rod a zdvořilost v dalších jazycích (slovenština, polština, ukrajinština…; zkouška s „trlangs“)
        "LUCIA: I was waiting for you.", "JASON: I was going to call you.", "LUCIA: I didn't see anyone.",
        "JASON: I was tired.", "You should have told me.", "Could you help me, please?", "Excuse me, sir.",
        "Thank you very much.", "Please sit down.",
    ) + CANDIDATES

    /**
     * Náhradní znění pro obraty, které překladač překládá špatně. Zkouška ukáže, jak je přeloží;
     * co vyjde dobře, přidá se do [OfflinePrep] jako náhrada, a odsud se to smaže.
     */
    private val CANDIDATES: List<String> get() = listOf(
        "Run to the gate.", "Run towards the gate!", "Hurry to the gate!", "Stay close to me.", "Stay by my side.",
        "Nobody was here for many years.", "We ride out at dawn.", "We leave at sunrise.",
        "Get on the horse, we leave in the morning.", "You and your friends must leave.", "Come with me, both of you.",
        "It will be fine.", "Everything will be all right.", "I am fine.", "Leave it to me.", "Watch your back.",
        "Cover me!", "I am out of ammo.", "Reloading!", "He is dead.", "Is anyone there?", "Who is there?",
        "Show yourself!", "What was that?", "This way!", "Over here!", "Behind you!",
    )
}
