#!/usr/bin/env python3
"""QR kódy pro web (pomoc/obrazky/qr-<jméno>.svg): odkazy, které se otevírají v TELEFONU, zatímco průvodce
nebo úvodní stránka je otevřená v počítači – naskenovat místo opisování adresy. Spouští se ručně při změně adresy:

  python3 ci/qr-web.py            (potřebuje balíček segno: pip install segno)
"""
import os

import segno

SLOZKA = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "pomoc", "obrazky")

ODKAZY = {
    "stahnout": "https://recaption.pages.dev/stahnout",
    "apps2samsung": "https://github.com/Apps2Samsung/Apps2Samsung/releases/latest",
    "psn-cs": "https://www.playstation.com/cs-cz/support/account/create-account-for-psn/",
    "psn-en": "https://www.playstation.com/en-gb/support/account/create-account-for-psn/",
    "deepl": "https://www.deepl.com/pro#api",
}

if __name__ == "__main__":
    for jmeno, adresa in ODKAZY.items():
        cil = os.path.join(SLOZKA, "qr-%s.svg" % jmeno)
        segno.make(adresa, error="m").save(cil, kind="svg", scale=4, border=2, dark="#111111", light="#ffffff",
                                           xmldecl=False, svgns=True, title=adresa)
        # viewBox, ať jde obrázek na stránce zmenšit (bez něj by se ořízl)
        t = open(cil, encoding="utf-8").read()
        if "viewBox" not in t:
            import re
            w = re.search(r'width="(\d+)"', t).group(1)
            t = t.replace("<svg ", '<svg viewBox="0 0 %s %s" ' % (w, w), 1)
            open(cil, "w", encoding="utf-8").write(t)
        print(cil)
