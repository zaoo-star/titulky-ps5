// Texty aplikace. Společné texty (stejné jako na Androidu, všech 10 jazyků) bere ze společné logiky (Jadro,
// core/I18n*.kt); texty, které má zatím jen iPhone, jsou tady – česky, slovensky a anglicky (ostatní jazyky
// anglicky). Bez tykání (zásady vzhledu v CLAUDE.md).

import Foundation
import Jadro

enum Texty {
    private static let cs: [String: String] = [
        "phone": "iPhone · %@",
        "wifi_off": "Wi-Fi · nepřipojeno",
        "port": "Spojení s TV · nejde otevřít",
        "test": "Zkušební věta do TV",
        "test_text": "Ahoj! Tohle je zkouška ReCaptionu z iPhonu.",
        "ver": "ReCaption pro iPhone %@ · zkušební",
    ]
    private static let sk: [String: String] = [
        "phone": "iPhone · %@",
        "wifi_off": "Wi-Fi · nepripojené",
        "port": "Spojenie s TV · nedá sa otvoriť",
        "test": "Skúšobná veta do TV",
        "test_text": "Ahoj! Toto je skúška ReCaptionu z iPhonu.",
        "ver": "ReCaption pre iPhone %@ · skúšobná",
    ]
    private static let en: [String: String] = [
        "phone": "iPhone · %@",
        "wifi_off": "Wi-Fi · not connected",
        "port": "TV connection · cannot open",
        "test": "Test sentence to TV",
        "test_text": "Hi! This is a ReCaption test from iPhone.",
        "ver": "ReCaption for iPhone %@ · test",
    ]

    /** Jazyk aplikace podle jazyků zařízení – stejné pravidlo jako na Androidu. */
    static var jazyk: String = ReCaptionJadro.shared.uiLang(preferred: Locale.preferredLanguages)

    static func t(_ klic: String) -> String {
        let tabulka = jazyk == "cs" ? cs : jazyk == "sk" ? sk : en
        if let s = tabulka[klic] ?? (cs[klic] != nil ? en[klic] : nil) { return s }
        return ReCaptionJadro.shared.text(lang: jazyk, key: klic)
    }

    static func t(_ klic: String, _ hodnota: CVarArg) -> String {
        String(format: t(klic), hodnota)
    }
}
