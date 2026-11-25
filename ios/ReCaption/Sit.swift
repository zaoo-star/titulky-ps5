// Adresa iPhonu v domácí síti (Wi-Fi) – jen pro stavový řádek, TV si iPhone najde sama.

import Foundation

enum Sit {
    /// IPv4 adresa na Wi-Fi (en0), nebo nil bez Wi-Fi.
    static func adresaWifi() -> String? {
        var seznam: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&seznam) == 0, let prvni = seznam else { return nil }
        defer { freeifaddrs(seznam) }
        var vysledek: String?
        for p in sequence(first: prvni, next: { $0.pointee.ifa_next }) {
            let i = p.pointee
            guard let adr = i.ifa_addr, adr.pointee.sa_family == UInt8(AF_INET),
                  String(cString: i.ifa_name) == "en0" else { continue }
            var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            if getnameinfo(adr, socklen_t(adr.pointee.sa_len), &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST) == 0 {
                vysledek = String(cString: host)
            }
        }
        return vysledek
    }
}
