// Zprávy mezi iPhonem a aplikací ReCaption v TV LG / Samsung.
// Stejný tvar jako na Androidu (app/src/main/java/cz/recaption/core/TvSender.kt) – aplikace v TV
// nepozná, jestli jí věty posílá Android, nebo iPhone. Bez UIKitu a sítě, ať jde vyzkoušet v testech.

import Foundation

enum TvZpravy {
    /// Port, na kterém aplikace v TV hledá čtečku (`PORT` v app.js, `TvSender.PORT`).
    static let port: UInt16 = 8787

    /// První zpráva po připojení – podle ní TV pozná ReCaption.
    static let vitej = "{\"typ\":\"vitej\"}"

    /// Titulek do pruhu (`{"typ":"ukaz","id":1,"text":"…"}`).
    static func ukaz(id: Int, text: String) -> String {
        "{\"typ\":\"ukaz\",\"id\":\(id),\"text\":\(json(text))}"
    }

    /// Schování pruhu.
    static func skryj(id: Int) -> String {
        "{\"typ\":\"skryj\",\"id\":\(id)}"
    }

    /// Text jako řetězec JSON (stejné escapování jako `RemoteSetup.json` na Androidu).
    static func json(_ s: String) -> String {
        var out = "\""
        for ch in s.unicodeScalars {
            switch ch {
            case "\"": out += "\\\""
            case "\\": out += "\\\\"
            case "\n": out += "\\n"
            case "\r": out += "\\r"
            case "\t": out += "\\t"
            default:
                if ch.value < 0x20 { out += String(format: "\\u%04x", ch.value) } else { out.unicodeScalars.append(ch) }
            }
        }
        return out + "\""
    }

    /// Ohlášení aplikace z TV (`{"typ":"pripojeno","televize":"…","platforma":"webos","verze":"1.5.38"}`).
    struct Ohlaseni: Equatable {
        var televize: String
        var platforma: String?
        var verze: String?

        /// „LG OLED55B7“ / „Samsung …“ – krátký název do stavového řádku.
        var nazev: String {
            let t = televize.trimmingCharacters(in: .whitespaces)
            if !t.isEmpty { return t }
            switch platforma {
            case "webos": return "TV LG"
            case "tizen": return "TV Samsung"
            default: return "TV"
            }
        }
    }

    /// Přečte zprávu od TV; jen `pripojeno` je zajímavé, ostatní (`hotovo`, `zaznam`, `zmen`) zatím nil.
    static func ohlaseni(_ text: String) -> Ohlaseni? {
        guard text.count <= 16 * 1024,
              let data = text.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              obj["typ"] as? String == "pripojeno" else { return nil }
        let tv = (obj["televize"] as? String) ?? ""
        return Ohlaseni(televize: String(tv.prefix(60)),
                        platforma: obj["platforma"] as? String,
                        verze: obj["verze"] as? String)
    }

    /// Titulky patří jen do domácí sítě (jako `PhoneLogin.isHomeAddress` + loopback na Androidu).
    static func jeDomaci(_ adresa: String) -> Bool {
        var a = adresa
        if let i = a.firstIndex(of: "%") { a = String(a[..<i]) }          // fe80::1%en0
        if a.lowercased().hasPrefix("::ffff:") { a = String(a.dropFirst(7)) }
        let p = a.split(separator: ".", omittingEmptySubsequences: false)
        if p.count == 4 {
            let n = p.compactMap { $0.count <= 3 && $0.allSatisfy(\.isNumber) ? Int($0) : nil }
            guard n.count == 4, n.allSatisfy({ $0 <= 255 }) else { return false }
            return n[0] == 10 || n[0] == 127 || (n[0] == 172 && (16...31).contains(n[1])) ||
                (n[0] == 192 && n[1] == 168) || (n[0] == 169 && n[1] == 254)
        }
        let l = a.lowercased()
        return l == "::1" || l.hasPrefix("fe80:") || l.hasPrefix("fc") || l.hasPrefix("fd")
    }
}
