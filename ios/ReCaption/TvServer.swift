// Spojení s aplikací ReCaption v TV LG / Samsung (stejné jako `TvSender` na Androidu).
// TV si iPhone najde sama: projde adresy domácí sítě na portu 8787 a připojí se WebSocketem.
// iPhone jen naslouchá, po připojení pošle `vitej` a pak hotové věty.
// Běží jen, když je aplikace otevřená (iOS na pozadí síť aplikaci zastaví).

import Foundation
import Network

final class TvServer {
    /// Stav pro obrazovku (volá se na hlavním vlákně).
    struct Stav: Equatable {
        var nasloucha = false
        var chyba: String?
        var televize: [TvZpravy.Ohlaseni] = []
        var pripojeno = 0
    }

    var onStav: ((Stav) -> Void)?
    /// Po ohlášení TV (pro zkoušku: rovnou poslat zkušební větu).
    var onOhlaseni: ((TvZpravy.Ohlaseni) -> Void)?

    private let fronta = DispatchQueue(label: "cz.recaption.tv")
    private var listener: NWListener?
    private var klienti: [ObjectIdentifier: NWConnection] = [:]
    private var ohlaseni: [ObjectIdentifier: TvZpravy.Ohlaseni] = [:]
    private var stav = Stav()
    private var aktualni: String?      // právě ukázaný titulek – dostane ho i TV, která se připojí později
    private var dalsiId = 1
    private var bezi = false
    private static let maxKlientu = 4

    func start() {
        fronta.async { self.otevri() }
    }

    func stop() {
        fronta.async {
            self.bezi = false
            self.listener?.cancel()
            self.listener = nil
            self.klienti.values.forEach { $0.cancel() }
            self.klienti.removeAll()
            self.ohlaseni.removeAll()
            self.stav = Stav()
            self.oznam()
        }
    }

    /// Titulek do pruhu v TV.
    func ukaz(_ text: String) {
        fronta.async {
            let zprava = TvZpravy.ukaz(id: self.dalsiId, text: text)
            self.dalsiId += 1
            self.aktualni = zprava
            self.rozesli(zprava)
        }
    }

    func skryj() {
        fronta.async {
            self.aktualni = nil
            self.rozesli(TvZpravy.skryj(id: self.dalsiId))
            self.dalsiId += 1
        }
    }

    // MARK: - naslouchání

    private func otevri() {
        guard listener == nil else { return }
        bezi = true
        let ws = NWProtocolWebSocket.Options()
        ws.autoReplyPing = true
        ws.maximumMessageSize = 64 * 1024
        let parametry = NWParameters.tcp
        parametry.defaultProtocolStack.applicationProtocols.insert(ws, at: 0)
        parametry.allowLocalEndpointReuse = true
        guard let port = NWEndpoint.Port(rawValue: TvZpravy.port),
              let l = try? NWListener(using: parametry, on: port) else {
            stav.chyba = "port"
            oznam()
            znovuPozdeji()
            return
        }
        // ohlášení v síti (Bonjour) – zároveň vyvolá dotaz iOS na přístup k místní síti
        l.service = NWListener.Service(name: "ReCaption", type: "_recaption._tcp")
        l.newConnectionHandler = { [weak self] c in self?.prijmi(c) }
        l.stateUpdateHandler = { [weak self] s in
            guard let self else { return }
            switch s {
            case .ready:
                self.stav.nasloucha = true
                self.stav.chyba = nil
                self.oznam()
            case .failed(let e):
                self.stav.nasloucha = false
                self.stav.chyba = "\(e)"
                self.oznam()
                self.listener?.cancel()
                self.listener = nil
                self.znovuPozdeji()
            default:
                break
            }
        }
        listener = l
        l.start(queue: fronta)
    }

    /// Port může chvíli držet předchozí běh – zkusit znovu za 2 s (jako `TvSender.start(retry = true)`).
    private func znovuPozdeji() {
        fronta.asyncAfter(deadline: .now() + 2) { [weak self] in
            guard let self, self.bezi else { return }
            self.otevri()
        }
    }

    private func prijmi(_ c: NWConnection) {
        guard bezi, klienti.count < Self.maxKlientu, Self.zDomaciSite(c.endpoint) else { c.cancel(); return }
        let id = ObjectIdentifier(c)
        c.stateUpdateHandler = { [weak self] s in
            guard let self else { return }
            switch s {
            case .ready:
                self.klienti[id] = c
                self.stav.pripojeno = self.klienti.count
                self.oznam()
                self.posli(TvZpravy.vitej, do: c)
                if let a = self.aktualni { self.posli(a, do: c) }
                self.cti(c)
            case .failed, .cancelled:
                self.odpoj(id)
            default:
                break
            }
        }
        c.start(queue: fronta)
    }

    private static func zDomaciSite(_ e: NWEndpoint) -> Bool {
        guard case let .hostPort(host, _) = e else { return false }
        switch host {
        case .ipv4(let a): return TvZpravy.jeDomaci("\(a)")
        case .ipv6(let a): return TvZpravy.jeDomaci("\(a)")
        case .name(let n, _): return TvZpravy.jeDomaci(n)
        @unknown default: return false
        }
    }

    private func cti(_ c: NWConnection) {
        c.receiveMessage { [weak self] data, kontext, _, chyba in
            guard let self else { return }
            if chyba != nil { c.cancel(); return }
            let meta = kontext?.protocolMetadata(definition: NWProtocolWebSocket.definition) as? NWProtocolWebSocket.Metadata
            if meta?.opcode == .close { c.cancel(); return }
            if meta?.opcode == .text, let data, let text = String(data: data, encoding: .utf8),
               let o = TvZpravy.ohlaseni(text) {
                self.ohlaseni[ObjectIdentifier(c)] = o
                self.stav.televize = Array(self.ohlaseni.values)
                self.oznam()
                let hlaseni = self.onOhlaseni
                DispatchQueue.main.async { hlaseni?(o) }
            }
            self.cti(c)
        }
    }

    private func posli(_ text: String, do c: NWConnection) {
        let meta = NWProtocolWebSocket.Metadata(opcode: .text)
        let kontext = NWConnection.ContentContext(identifier: "zprava", metadata: [meta])
        c.send(content: Data(text.utf8), contentContext: kontext, isComplete: true,
               completion: .contentProcessed { chyba in if chyba != nil { c.cancel() } })
    }

    private func rozesli(_ text: String) {
        klienti.values.forEach { posli(text, do: $0) }
    }

    private func odpoj(_ id: ObjectIdentifier) {
        guard klienti.removeValue(forKey: id) != nil || ohlaseni[id] != nil else { return }
        ohlaseni.removeValue(forKey: id)
        stav.pripojeno = klienti.count
        stav.televize = Array(ohlaseni.values)
        oznam()
    }

    private func oznam() {
        let s = stav
        let f = onStav
        DispatchQueue.main.async { f?(s) }
    }
}
