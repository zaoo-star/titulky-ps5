#!/usr/bin/env python3
"""QR kódy pro dobrovolný příspěvek na stránce pomoc/podporit.md (spouští se ručně, jen při změně účtu).

  python3 ci/qr-platba.py            (potřebuje balíček segno: pip install segno)

Vyrobí pomoc/obrazky/qr-platba-cz.png (QR Platba / SPD – české banky) a qr-platba-sk.png (PAY by square –
slovenské banky, na účet v eurech). Částka v kódu není, dárce ji zadá sám. Jméno příjemce v kódu také není (banka ho stejně ukáže).
"""
import binascii
import lzma
import os

import segno

IBAN = "CZ9230300000002329017034"        # korunový účet (Česko)
IBAN_EUR = "CZ7030300000002329017042"    # účet v eurech (Slovensko, EU – bez převodu na koruny)
BIC = "AIRACZPP"
ZPRAVA = "ReCaption"
SLOZKA = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "pomoc", "obrazky")


def spd():
    """QR Platba (Short Payment Descriptor) – standard České bankovní asociace."""
    return "SPD*1.0*ACC:%s*CC:CZK*MSG:%s" % (IBAN, ZPRAVA.upper())


ABECEDA = "0123456789ABCDEFGHIJKLMNOPQRSTUV"
LZMA_FILTR = [{"id": lzma.FILTER_LZMA1, "lc": 3, "lp": 0, "pb": 2, "dict_size": 128 * 1024}]


def by_square():
    """PAY by square (Slovenská banková asociace): pole oddělená tabulátorem, CRC32, LZMA1, base32hex."""
    pole = [
        "",        # id dokladu
        "1",       # počet plateb
        "1",       # platební příkaz
        "",        # částka – zadá dárce
        "EUR",
        "",        # splatnost
        "", "", "",  # variabilní, konstantní, specifický symbol
        "",        # reference
        ZPRAVA,    # poznámka pro příjemce
        "1",       # počet účtů
        IBAN_EUR,
        BIC,
        "0",       # trvalý příkaz
        "0",       # inkaso
        "", "", "",  # jméno a adresa příjemce
    ]
    data = "\t".join(pole).encode("utf-8")
    celek = binascii.crc32(data).to_bytes(4, "little") + data
    stlaceno = lzma.compress(celek, format=lzma.FORMAT_RAW, filters=LZMA_FILTR)
    # kontrola: rozbalí se zpátky na totéž
    assert lzma.decompress(stlaceno, format=lzma.FORMAT_RAW, filters=LZMA_FILTR)[:len(celek)] == celek
    bajty = b"\x00\x00" + len(celek).to_bytes(2, "little") + stlaceno
    bity = "".join(format(b, "08b") for b in bajty)
    bity += "0" * (-len(bity) % 5)
    return "".join(ABECEDA[int(bity[i:i + 5], 2)] for i in range(0, len(bity), 5))


def uloz(text, jmeno):
    cesta = os.path.join(SLOZKA, jmeno)
    segno.make(text, error="m", micro=False).save(cesta, scale=10, border=4)
    print(jmeno, "←", text[:70] + ("…" if len(text) > 70 else ""))


uloz(spd(), "qr-platba-cz.png")
uloz(by_square(), "qr-platba-sk.png")
