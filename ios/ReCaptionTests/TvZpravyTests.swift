import XCTest
@testable import ReCaption

final class TvZpravyTests: XCTestCase {
    func testZpravyMajiStejnyTvarJakoAndroid() {
        XCTAssertEqual(TvZpravy.vitej, "{\"typ\":\"vitej\"}")
        XCTAssertEqual(TvZpravy.ukaz(id: 3, text: "Ahoj"), "{\"typ\":\"ukaz\",\"id\":3,\"text\":\"Ahoj\"}")
        XCTAssertEqual(TvZpravy.skryj(id: 4), "{\"typ\":\"skryj\",\"id\":4}")
    }

    func testEscapovani() {
        XCTAssertEqual(TvZpravy.json("a\"b\\c\nd\te\u{1}"), "\"a\\\"b\\\\c\\nd\\te\\u0001\"")
        XCTAssertEqual(TvZpravy.json("Žluťoučký kůň ♪"), "\"Žluťoučký kůň ♪\"")
        // výsledek je platný JSON
        let z = TvZpravy.ukaz(id: 1, text: "Řekl: \"Jdeme!\"\nLucia: Ok")
        let o = try? JSONSerialization.jsonObject(with: Data(z.utf8)) as? [String: Any]
        XCTAssertEqual(o?["text"] as? String, "Řekl: \"Jdeme!\"\nLucia: Ok")
    }

    func testOhlaseniTv() {
        let o = TvZpravy.ohlaseni("{\"typ\":\"pripojeno\",\"televize\":\"LG OLED55B7\",\"aplikace\":\"ReCaption\",\"platforma\":\"webos\",\"verze\":\"1.5.38\",\"hdmi\":1}")
        XCTAssertEqual(o, TvZpravy.Ohlaseni(televize: "LG OLED55B7", platforma: "webos", verze: "1.5.38"))
        XCTAssertEqual(o?.nazev, "LG OLED55B7")
        XCTAssertEqual(TvZpravy.ohlaseni("{\"typ\":\"pripojeno\",\"platforma\":\"tizen\"}")?.nazev, "TV Samsung")
        XCTAssertNil(TvZpravy.ohlaseni("{\"typ\":\"hotovo\",\"id\":1}"))
        XCTAssertNil(TvZpravy.ohlaseni("nesmysl"))
    }

    func testJenDomaciSit() {
        for a in ["192.168.1.20", "10.0.0.5", "172.16.3.4", "172.31.0.1", "169.254.1.1", "127.0.0.1",
                  "::1", "fe80::1%en0", "fd00::5", "::ffff:192.168.0.7"] {
            XCTAssertTrue(TvZpravy.jeDomaci(a), a)
        }
        for a in ["8.8.8.8", "172.32.0.1", "192.169.1.1", "2a00:1450::1", "300.1.1.1", "1.2.3", ""] {
            XCTAssertFalse(TvZpravy.jeDomaci(a), a)
        }
    }
}

final class JadroTests: XCTestCase {
    func testTextyZeSpolecneLogiky() {
        // text z core/I18n*.kt přes Jadro – stejný jako na Androidu
        let cs = Texty.jazyk
        defer { Texty.jazyk = cs }
        Texty.jazyk = "cs"
        XCTAssertEqual(Texty.t("m_home"), "Domů")
        XCTAssertEqual(Texty.t("test"), "Zkušební věta do TV")
        Texty.jazyk = "de"
        XCTAssertEqual(Texty.t("m_home"), "Start")
        XCTAssertEqual(Texty.t("test"), "Test sentence to TV")
    }
}

final class Ps5KnihovnaTests: XCTestCase {
    func testKnihovnaJeVAplikaci() {
        XCTAssertTrue(Ps5Knihovna.pripravena)
        XCTAssertEqual(Ps5Knihovna.popisChyby(0), "Success")
        // CHIAKI_ERR_INVALID_DATA = 11 – stejně jako zkouška na Linuxu (ios/chiaki)
        XCTAssertEqual(Ps5Knihovna.zkouskaOdmitnuti(), 11)
    }
}
