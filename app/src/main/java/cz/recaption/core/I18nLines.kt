package cz.recaption.core

/**
 * Texty „Sbírání replik ze hry“ (⚙ → Čtení a překlad, LineCollector, core/LineBank.kt, od 0.97.139).
 * LINES_S = čeština a slovenština, LINES_X = ostatní jazyky. Společné: on, off, dg_close, snd_e_….
 * Od 1.00 se sbírají i ostatní texty hry (úkol, menu, telefon… – druh v LineBank), proto „texty“, ne „repliky“.
 * Popis v Nápovědě `ln_help` (od 1.00 výchozí zapnuto bez dotazu) musí odpovídat tomu, co LineBank ukládá a LineCollector posílá – při změně upravit.
 */
internal val LINES_S: Map<String, Pair<String, String>> = mapOf(
    "ln_row" to ("Sbírání replik ze hry" to "Zbieranie replík z hry"),
    "ln_help" to ("Výchozí zapnuto, jen s profilem hry (Titulky → Hra). Pro vylepšení překladů se sbírají pouze texty ze hry, které ReCaption přečetl a přeložil: anglické titulky, pokyny, úkoly, menu hry, tipy a texty telefonu ve hře. Občas se odešlou vývojáři – z textů všech hráčů vznikne přeložená paměť hry (přesnější a rychlejší překlad). Posílá se jen text, druh textu (titulek, menu…), jméno mluvčího z titulku, hra, jazyk titulků, počet výskytů a pořadí v rozhovoru – nic jiného: žádné osobní údaje, snímky, zvuk, překlady, účty, jméno hráče ani údaje o zařízení. Na serveru se po 30 dnech smažou. Vypnutím se smaže i vše nasbírané v zařízení." to
        "Predvolene zapnuté, len s profilom hry (Titulky → Hra). Na vylepšenie prekladov sa zbierajú iba texty z hry, ktoré ReCaption prečítal a preložil: anglické titulky, pokyny, úlohy, menu hry, tipy a texty telefónu v hre. Občas sa odošlú vývojárovi – z textov všetkých hráčov vznikne preložená pamäť hry (presnejší a rýchlejší preklad). Posiela sa len text, druh textu (titulok, menu…), meno hovoriaceho z titulku, hra, jazyk titulkov, počet výskytov a poradie v rozhovore – nič iné: žiadne osobné údaje, snímky, zvuk, preklady, účty, meno hráča ani údaje o zariadení. Na serveri sa po 30 dňoch zmažú. Vypnutím sa zmaže aj všetko nazbierané v zariadení."),
    "ln_on" to ("zapnuto · %n textů" to "zapnuté · %n textov"),
    "ln_no_game" to ("zapnuto · bez profilu hry" to "zapnuté · bez profilu hry"),
    "ln_status" to ("Nasbíráno: %n textů · k odeslání: %p" to "Nazbierané: %n textov · na odoslanie: %p"),
    "ln_send" to ("Odeslat teď" to "Odoslať teraz"),
    "ln_off" to ("Vypnout a smazat" to "Vypnúť a zmazať"),
    "ln_sent" to ("Texty odeslány" to "Texty odoslané"),
    "ln_nothing" to ("Nic k odeslání" to "Nič na odoslanie"),
)

internal val LINES_X: Map<Lang, Map<String, String>> = mapOf(
    Lang.EN to mapOf(
        "ln_row" to "Collect game lines",
        "ln_help" to "On by default, only with a game profile (Subtitles → Game). To improve translations, only game text that ReCaption read and translated is collected: English subtitles, prompts, objectives, game menus, tips and in-game phone text. It is sent to the developer from time to time – text from all players becomes a translation memory of the game (more accurate and faster translation). Only the text, its type (subtitle, menu…), the speaker name from the subtitle, game, subtitle language, number of appearances and order in the conversation are sent – nothing else: no personal data, screenshots, audio, translations, accounts, player name or device details. It is deleted from the server after 30 days. Turning this off also deletes everything collected on the device.",
        "ln_on" to "on · %n texts",
        "ln_no_game" to "on · no game profile",
        "ln_status" to "Collected: %n texts · to send: %p",
        "ln_send" to "Send now",
        "ln_off" to "Turn off and delete",
        "ln_sent" to "Texts sent",
        "ln_nothing" to "Nothing to send",
    ),
    Lang.PL to mapOf(
        "ln_row" to "Zbieranie kwestii z gry",
        "ln_help" to "Domyślnie włączone, tylko z profilem gry (Napisy → Gra). Aby ulepszać tłumaczenia, zbierane są wyłącznie teksty z gry, które ReCaption odczytał i przetłumaczył: angielskie napisy, polecenia, zadania, menu gry, wskazówki i teksty telefonu w grze. Co jakiś czas są wysyłane do twórcy – z tekstów wszystkich graczy powstaje pamięć tłumaczeń gry (dokładniejsze i szybsze tłumaczenie). Wysyłany jest tylko tekst, jego rodzaj (napis, menu…), imię mówiącego z napisu, gra, język napisów, liczba wystąpień i kolejność w rozmowie – nic więcej: bez danych osobowych, zrzutów ekranu, dźwięku, tłumaczeń, kont, imienia gracza i danych urządzenia. Z serwera są usuwane po 30 dniach. Wyłączenie usuwa też wszystko, co zebrano na urządzeniu.",
        "ln_on" to "włączone · %n tekstów",
        "ln_no_game" to "włączone · bez profilu gry",
        "ln_status" to "Zebrano: %n tekstów · do wysłania: %p",
        "ln_send" to "Wyślij teraz",
        "ln_off" to "Wyłącz i usuń",
        "ln_sent" to "Teksty wysłane",
        "ln_nothing" to "Nic do wysłania",
    ),
    Lang.DE to mapOf(
        "ln_row" to "Spieldialoge sammeln",
        "ln_help" to "Standardmäßig an, nur mit Spielprofil (Untertitel → Spiel). Zur Verbesserung der Übersetzungen werden ausschließlich Spieltexte gesammelt, die ReCaption gelesen und übersetzt hat: englische Untertitel, Hinweise, Aufgaben, Spielmenüs, Tipps und Texte des Telefons im Spiel. Sie werden von Zeit zu Zeit an den Entwickler gesendet – aus den Texten aller Spieler entsteht ein Übersetzungsspeicher des Spiels (genauere und schnellere Übersetzung). Gesendet werden nur der Text, seine Art (Untertitel, Menü…), der Name der sprechenden Figur aus dem Untertitel, Spiel, Untertitelsprache, Anzahl der Vorkommen und Reihenfolge im Gespräch – sonst nichts: keine persönlichen Daten, Bildschirmfotos, kein Ton, keine Übersetzungen, Konten, Spielernamen oder Gerätedaten. Nach 30 Tagen werden sie vom Server gelöscht. Ausschalten löscht auch alles auf dem Gerät Gesammelte.",
        "ln_on" to "an · %n Texte",
        "ln_no_game" to "an · ohne Spielprofil",
        "ln_status" to "Gesammelt: %n Texte · zu senden: %p",
        "ln_send" to "Jetzt senden",
        "ln_off" to "Ausschalten und löschen",
        "ln_sent" to "Texte gesendet",
        "ln_nothing" to "Nichts zu senden",
    ),
    Lang.HU to mapOf(
        "ln_row" to "Játékbeli mondatok gyűjtése",
        "ln_help" to "Alapból bekapcsolva, csak játékprofillal (Feliratok → Játék). A fordítások javításához kizárólag a játék szövegei gyűlnek, amelyeket a ReCaption beolvasott és lefordított: angol feliratok, utasítások, feladatok, játékmenük, tippek és a játékbeli telefon szövegei. Időnként elküldésre kerülnek a fejlesztőnek – az összes játékos szövegeiből a játék fordítási memóriája áll össze (pontosabb és gyorsabb fordítás). Csak a szöveg, a fajtája (felirat, menü…), a beszélő neve a feliratból, a játék, a felirat nyelve, az előfordulások száma és a beszélgetésbeli sorrend kerül elküldésre – semmi más: személyes adat, képernyőkép, hang, fordítás, fiók, játékosnév és eszközadat nem. A szerverről 30 nap után törlődnek. Kikapcsoláskor az eszközön összegyűjtött adatok is törlődnek.",
        "ln_on" to "be · %n szöveg",
        "ln_no_game" to "be · játékprofil nélkül",
        "ln_status" to "Gyűjtve: %n szöveg · küldésre vár: %p",
        "ln_send" to "Küldés most",
        "ln_off" to "Kikapcsolás és törlés",
        "ln_sent" to "Szövegek elküldve",
        "ln_nothing" to "Nincs mit küldeni",
    ),
    Lang.UK to mapOf(
        "ln_row" to "Збір реплік із гри",
        "ln_help" to "Типово увімкнено, лише з профілем гри (Субтитри → Гра). Для покращення перекладів збираються лише тексти з гри, які ReCaption прочитав і переклав: англійські субтитри, підказки, завдання, меню гри, поради та тексти телефона в грі. Час від часу вони надсилаються розробникові – з текстів усіх гравців складається пам'ять перекладів гри (точніший і швидший переклад). Надсилається лише текст, його вид (субтитр, меню…), ім'я мовця із субтитру, гра, мова субтитрів, кількість появ і порядок у розмові – нічого іншого: без особистих даних, знімків екрана, звуку, перекладів, облікових записів, імені гравця й даних пристрою. Із сервера тексти видаляються через 30 днів. Вимкнення видаляє й усе зібране на пристрої.",
        "ln_on" to "увімкнено · %n текстів",
        "ln_no_game" to "увімкнено · без профілю гри",
        "ln_status" to "Зібрано: %n текстів · до надсилання: %p",
        "ln_send" to "Надіслати зараз",
        "ln_off" to "Вимкнути й видалити",
        "ln_sent" to "Тексти надіслано",
        "ln_nothing" to "Нічого надсилати",
    ),
    Lang.ES to mapOf(
        "ln_row" to "Recopilar frases del juego",
        "ln_help" to "Activado de forma predeterminada, solo con un perfil de juego (Subtítulos → Juego). Para mejorar las traducciones se recopilan únicamente textos del juego que ReCaption ha leído y traducido: subtítulos en inglés, indicaciones, objetivos, menús del juego, consejos y textos del teléfono del juego. Se envían de vez en cuando al desarrollador; con los textos de todos los jugadores se crea una memoria de traducción del juego (traducción más precisa y rápida). Solo se envía el texto, su tipo (subtítulo, menú…), el nombre de quien habla en el subtítulo, el juego, el idioma de los subtítulos, cuántas veces apareció y el orden en la conversación; nada más: sin datos personales, capturas, audio, traducciones, cuentas, nombre del jugador ni datos del dispositivo. Se borran del servidor a los 30 días. Al desactivarlo se borra también todo lo recopilado en el dispositivo.",
        "ln_on" to "activado · %n textos",
        "ln_no_game" to "activado · sin perfil de juego",
        "ln_status" to "Recopilado: %n textos · por enviar: %p",
        "ln_send" to "Enviar ahora",
        "ln_off" to "Desactivar y borrar",
        "ln_sent" to "Textos enviados",
        "ln_nothing" to "Nada que enviar",
    ),
    Lang.FR to mapOf(
        "ln_row" to "Collecte des répliques du jeu",
        "ln_help" to "Activé par défaut, uniquement avec un profil de jeu (Sous-titres → Jeu). Pour améliorer les traductions, seuls les textes du jeu lus et traduits par ReCaption sont collectés : sous-titres anglais, consignes, objectifs, menus du jeu, astuces et textes du téléphone dans le jeu. Ils sont envoyés de temps en temps au développeur ; les textes de tous les joueurs forment une mémoire de traduction du jeu (traduction plus précise et plus rapide). Seuls le texte, son type (sous-titre, menu…), le nom du personnage dans le sous-titre, le jeu, la langue des sous-titres, le nombre d'apparitions et l'ordre dans la conversation sont envoyés – rien d'autre : pas de données personnelles, de captures d'écran, de son, de traductions, de comptes, de nom du joueur ni de données de l'appareil. Ils sont effacés du serveur après 30 jours. La désactivation efface aussi tout ce qui a été collecté sur l'appareil.",
        "ln_on" to "activé · %n textes",
        "ln_no_game" to "activé · sans profil de jeu",
        "ln_status" to "Collecté : %n textes · à envoyer : %p",
        "ln_send" to "Envoyer maintenant",
        "ln_off" to "Désactiver et effacer",
        "ln_sent" to "Textes envoyés",
        "ln_nothing" to "Rien à envoyer",
    ),
    Lang.IT to mapOf(
        "ln_row" to "Raccolta battute del gioco",
        "ln_help" to "Attivo per impostazione predefinita, solo con un profilo di gioco (Sottotitoli → Gioco). Per migliorare le traduzioni si raccolgono solo i testi del gioco letti e tradotti da ReCaption: sottotitoli inglesi, indicazioni, obiettivi, menu del gioco, suggerimenti e testi del telefono nel gioco. Vengono inviati di tanto in tanto allo sviluppatore: dai testi di tutti i giocatori nasce una memoria di traduzione del gioco (traduzione più precisa e veloce). Vengono inviati solo il testo, il suo tipo (sottotitolo, menu…), il nome di chi parla nel sottotitolo, il gioco, la lingua dei sottotitoli, quante volte è comparso e l'ordine nella conversazione – nient'altro: niente dati personali, screenshot, audio, traduzioni, account, nome del giocatore o dati del dispositivo. Vengono cancellati dal server dopo 30 giorni. Disattivando si cancella anche tutto ciò che è stato raccolto sul dispositivo.",
        "ln_on" to "attivo · %n testi",
        "ln_no_game" to "attivo · senza profilo di gioco",
        "ln_status" to "Raccolti: %n testi · da inviare: %p",
        "ln_send" to "Invia ora",
        "ln_off" to "Disattiva ed elimina",
        "ln_sent" to "Testi inviati",
        "ln_nothing" to "Niente da inviare",
    ),
)
