// Stránka Domů: stav v krátkých bodech s tečkou (zelená = jede, oranžová = čeká, šedá = informace),
// jako Domů na Androidu.

import SwiftUI

private let zluta = Color(red: 242 / 255, green: 193 / 255, blue: 78 / 255)
private let zelena = Color(red: 0.36, green: 0.80, blue: 0.45)
private let oranzova = Color(red: 0.98, green: 0.62, blue: 0.25)
private let seda = Color(white: 0.55)
private let pozadi = Color(red: 20 / 255, green: 23 / 255, blue: 29 / 255)

struct Domu: View {
    @ObservedObject var model: Model

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack(spacing: 0) {
                    Text("Play").foregroundColor(.white)
                    Text("Lingo").foregroundColor(zluta)
                }
                .font(.system(size: 34, weight: .bold))
                .padding(.top, 24)

                VStack(alignment: .leading, spacing: 12) {
                    tvRadek
                    if model.tv.chyba != nil && !model.tv.nasloucha {
                        Radek(barva: oranzova, text: Texty.t("port"))
                    }
                    if let a = model.adresa {
                        Radek(barva: seda, text: Texty.t("phone", a))
                    } else {
                        Radek(barva: oranzova, text: Texty.t("wifi_off"))
                    }
                }

                Button(action: model.zkusebniVeta) {
                    Text(Texty.t("test"))
                        .font(.headline)
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .background(model.tv.pripojeno > 0 ? zluta : Color(white: 0.25))
                        .foregroundColor(model.tv.pripojeno > 0 ? .black : Color(white: 0.6))
                        .cornerRadius(12)
                }
                .disabled(model.tv.pripojeno == 0)
                .accessibilityIdentifier("zkouska")

                Text(Texty.t("ver", model.verze))
                    .font(.footnote)
                    .foregroundColor(seda)
                    .padding(.top, 8)
            }
            .padding(.horizontal, 20)
        }
        .background(pozadi.ignoresSafeArea())
    }

    @ViewBuilder private var tvRadek: some View {
        let tv = model.tv
        if tv.pripojeno > 1 {
            Radek(barva: zelena, text: "\(tv.pripojeno) TV · " + Texty.t("tv_st_on"))
        } else if tv.pripojeno == 1 {
            Radek(barva: zelena, text: (tv.televize.first?.nazev ?? "TV") + " · " + Texty.t("tv_st_on"))
        } else {
            VStack(alignment: .leading, spacing: 6) {
                Radek(barva: oranzova, text: "TV · " + Texty.t("tv_st_off"))
                // slabý běžící pruh: čeká se, až se TV připojí sama (ui.waiting() na Androidu)
                ProgressView().progressViewStyle(.linear).tint(oranzova.opacity(0.5))
                    .padding(.leading, 22)
            }
        }
    }
}

private struct Radek: View {
    let barva: Color
    let text: String

    var body: some View {
        HStack(spacing: 12) {
            Circle().fill(barva).frame(width: 10, height: 10)
            Text(text).foregroundColor(.white).font(.body)
        }
    }
}
