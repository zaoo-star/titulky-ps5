#!/usr/bin/env python3
"""Nakreslí ikony verze pro obchod LG: do balíčku (80 a 130 bodů) a pro obchod (400 bodů),
a tutéž ikonu pro TV Samsung (tizen/app/icon.png, 130 bodů).

Použití:  python3 webos/obchod/ikony.py   (potřebuje knihovnu Pillow)
Od aplikace v TV 1.5.39 stejný motiv jako ikona aplikace pro Android (app/src/main/res/drawable/ic_logo.xml):
žlutá dlaždice, tmavá bublina s řečí a v ní žlutý trojúhelník „přehrát“.
"""
import os
from PIL import Image, ImageDraw

TADY = os.path.dirname(os.path.abspath(__file__))
KOREN = os.path.dirname(os.path.dirname(TADY))
ZLUTA, TMAVA = (242, 193, 78, 255), (20, 23, 29, 255)


def ikona(strana):
    k = 4 * strana / 108.0          # souřadnice jako v ic_logo.xml (108 × 108); kreslí se 4× větší a zmenší
    obr = Image.new("RGBA", (4 * strana, 4 * strana), ZLUTA)
    d = ImageDraw.Draw(obr)
    p = lambda body: [(x * k, y * k) for x, y in body]
    # LG vrací ikony se zaoblenými rohy nebo průhledným pozadím: dlaždice musí být plný čtverec.
    d.rounded_rectangle([20 * k, 26 * k, 88 * k, 72 * k], radius=10 * k, fill=TMAVA)   # bublina
    d.polygon(p([(50, 71), (36, 84), (36, 71)]), fill=TMAVA)                            # ocásek bubliny
    d.polygon(p([(46, 37), (68, 49), (46, 61)]), fill=ZLUTA)                            # „přehrát“
    return obr.resize((strana, strana), Image.LANCZOS).convert("RGB")


ikona(80).save(os.path.join(TADY, "app", "icon.png"))
ikona(130).save(os.path.join(TADY, "app", "largeIcon.png"))
ikona(400).save(os.path.join(TADY, "ikona-400.png"))
ikona(130).save(os.path.join(KOREN, "tizen", "app", "icon.png"))
print("Hotovo: app/icon.png, app/largeIcon.png, ikona-400.png, tizen/app/icon.png")
