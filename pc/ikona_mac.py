#!/usr/bin/env python3
"""Nakreslí ikonu ReCaptionu pro Mac (ReCaption.icns) – stejný motiv jako ikona pro TV LG
(`webos/obchod/ikony.py`): žlutá dlaždice, tmavá obrazovka, dole pruh titulku. Na Macu má dlaždice
zaoblené rohy a kolem sebe volný okraj, jak je u ikon aplikací zvykem.

Použití:  python3 pc/ikona_mac.py CIL.icns   (potřebuje knihovnu Pillow; pouští ho `.github/workflows/mac.yml`)
"""
import sys
from PIL import Image, ImageDraw

ZLUTA, TMAVA = (242, 193, 78, 255), (14, 16, 20, 255)
STRANA = 1024


def ikona():
    k = 2 * STRANA / 400.0          # kreslí se dvakrát větší a zmenší, ať jsou hrany hladké
    obr = Image.new("RGBA", (2 * STRANA, 2 * STRANA), (0, 0, 0, 0))
    d = ImageDraw.Draw(obr)

    def obdelnik(x0, y0, x1, y1, r, barva):
        d.rounded_rectangle([x0 * k, y0 * k, x1 * k, y1 * k], radius=r * k, fill=barva)

    obdelnik(40, 40, 360, 360, 72, ZLUTA)
    obdelnik(91, 114, 309, 286, 21, TMAVA)
    obdelnik(123, 229, 277, 255, 8, ZLUTA)
    return obr.resize((STRANA, STRANA), Image.LANCZOS)


if __name__ == "__main__":
    cil = sys.argv[1] if len(sys.argv) > 1 else "ReCaption.icns"
    ikona().save(cil, format="ICNS", sizes=[(16, 16), (32, 32), (64, 64), (128, 128), (256, 256), (512, 512), (1024, 1024)])
    print("Hotovo:", cil)
