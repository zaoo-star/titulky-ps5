// ReCaption pro iPhone – čtečka pro TV LG / Samsung (zkušební).
// Díl 1: iPhone se ukáže aplikaci ReCaption v TV a pošle jí zkušební větu.
// Další díly (spojení s PS5, čtení a překlad) viz ios/CTI-ME.md.

import SwiftUI
import UIKit

@main
struct ReCaptionApp: App {
    @StateObject private var model = Model()
    @Environment(\.scenePhase) private var faze

    var body: some Scene {
        WindowGroup {
            Domu(model: model)
                .preferredColorScheme(.dark)
        }
        .onChange(of: faze) { f in
            switch f {
            case .active:
                // telefon leží u TV celou hru – displej nezhasínat, jinak iOS aplikaci zastaví
                UIApplication.shared.isIdleTimerDisabled = true
                model.start()
            case .background:
                UIApplication.shared.isIdleTimerDisabled = false
                model.stop()
            default:
                break
            }
        }
    }
}

final class Model: ObservableObject {
    @Published var tv = TvServer.Stav()
    @Published var adresa: String?

    private let server = TvServer()
    /// Zkouška na GitHubu: aplikace spuštěná s `-zkouska` pošle zkušební větu hned po ohlášení TV.
    private let zkouska = ProcessInfo.processInfo.arguments.contains("-zkouska")
    private var skryti: DispatchWorkItem?

    init() {
        server.onStav = { [weak self] s in self?.tv = s }
        server.onOhlaseni = { [weak self] _ in
            guard let self, self.zkouska else { return }
            self.zkusebniVeta()
        }
    }

    func start() {
        adresa = Sit.adresaWifi()
        server.start()
    }

    func stop() { server.stop() }

    func zkusebniVeta() {
        server.ukaz(Texty.t("test_text"))
        skryti?.cancel()
        let s = DispatchWorkItem { [weak self] in self?.server.skryj() }
        skryti = s
        DispatchQueue.main.asyncAfter(deadline: .now() + 5, execute: s)
    }

    var verze: String {
        let i = Bundle.main.infoDictionary
        let v = i?["CFBundleShortVersionString"] as? String ?? "?"
        let b = i?["CFBundleVersion"] as? String ?? "?"
        return "\(v) (\(b))"
    }
}
