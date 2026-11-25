// Knihovna pro spojení s PS5 (chiaki-ng přes ios/chiaki/plchiaki.h). Díl 2: zatím jen ověření, že je v aplikaci
// a odpovídá; spárování a spojení přijdou nad ní (stejně jako Ps5Link / Ps5Pairing v Androidu).

import Foundation
import PlChiaki

enum Ps5Knihovna {
    /// Knihovna se připraví jednou při prvním použití (chiaki_lib_init).
    static let pripravena: Bool = pl_lib_init() == 0

    /// Popis chybového kódu knihovny (anglicky, do záznamu).
    static func popisChyby(_ kod: Int32) -> String {
        guard let c = pl_error_string(kod) else { return "\(kod)" }
        return String(cString: c)
    }

    /// Zkouška bez PS5: spojení se špatně dlouhým klíčem musí knihovna odmítnout (vrací kód chyby).
    static func zkouskaOdmitnuti() -> Int32 {
        _ = pripravena
        var chyba: Int32 = -1
        let klic = [UInt8](repeating: 0, count: 15)
        let rano = [UInt8](repeating: 0, count: 16)
        if let s = pl_session_create(true, "127.0.0.1", klic, klic.count, rano, rano.count, 3, 60, 0,
                                     nil, false, nil, nil, nil, &chyba) {
            pl_session_free(s)
        }
        return chyba
    }
}
