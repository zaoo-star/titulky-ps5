#!/usr/bin/env python3
"""Pomocník čtečky pro počítač: získá obraz hry, přečte z něj text a přeloží věty.

Pouští ho čtečka (pc/src/Main.kt) a mluví s ním po řádcích přes standardní vstup a výstup:

  CTI <tab> vynutit(0/1) [<tab> text ukázky]
      -> STEJNE                       obraz se od minula nezměnil (jen když vynutit=0)
      -> CHYBA <tab> popis            obraz není (např. okno s hrou není otevřené)
      -> OK <tab> N <tab> šířka <tab> výška <tab> x0 <tab> y0 <tab> ms
         a za tím N řádků:  vlevo <tab> nahoře <tab> vpravo <tab> dole <tab> text
         (souřadnice jsou v čteném výřezu; x0, y0 je jeho levý horní roh v celém obraze)
  PRELOZ <tab> anglický text
      -> OK <tab> překlad
  BARVA <tab> vlevo <tab> nahoře <tab> vpravo <tab> dole      (v celém posledním snímku)
      -> OK <tab> r <tab> g <tab> b    průměrná barva nejsvětlejších bodů (písmo jména mluvčího)
      -> NIC                          málo bodů / žádný snímek
  OKNO <tab> nazev
      -> OK                           dál se čte jiné okno (stejný zápis jako --okno, např. pid:1234)
  MENU
      -> OK <tab> N <tab> šířka <tab> výška <tab> ms  a za tím N řádků:
         vlevo <tab> nahoře <tab> vpravo <tab> dole <tab> jas <tab> text
         horních 60 % posledního snímku, zmenšené na nejvýš 960 bodů na šířku (šířka × výška = celý
         zmenšený snímek); jas = průměrný jas pod řádkem 0–255 (vybraná položka menu má bílý pruh)

MRIZKA <tab> vlevo <tab> nahoře <tab> vpravo <tab> dole <tab> sloupců <tab> řádků   (zlomky posledního snímku)
      -> OK <tab> jasy oddělené čárkou (řádek po řádku; z každého řádku mřížky vodorovný řez jeho středem,
         bod = průměr jasu přes šířku políčka – jako PhoneGate.sample v aplikaci)
      -> NIC                          žádný snímek
  SNIMEK
      -> OK <tab> šířka <tab> výška   jen sejme obraz okna (bez čtení) jako poslední snímek – druhý pomocník
         jen pro telefon ve hře ho tak má vlastní a titulky nečekají
      -> CHYBA <tab> popis
  ROZMER
      -> OK <tab> x0 <tab> y0 <tab> šířka <tab> výška   obraz hry v posledním snímku bez černých pruhů
         po stranách / nahoře a dole (okno chiaki-ng mívá jiný poměr stran než 16:9); MRIZKA a TELEFON
         počítají zlomky i body v tomhle obdélníku
      -> NIC                          žádný snímek
  PIXELY <tab> vlevo <tab> nahoře <tab> vpravo <tab> dole      (zlomky obrazu hry, viz ROZMER)
      -> OK <tab> barvy bodů 0xRRGGBB desítkově oddělené čárkou (mřížka nejvýš 40 × 8 – jako barevný snímek
         v aplikaci pro modrou bublinu vlastních zpráv)
  TELEFON <tab> vlevo <tab> nahoře <tab> vpravo <tab> dole [<tab> zvětšení]   (zlomky posledního snímku;
         bez zvětšení se malý výřez čte zvětšený až 2×, 1 = bez zvětšení – menu přes celou obrazovku)
      -> OK <tab> N <tab> šířka <tab> výška <tab> ms  a za tím N řádků přečtených ve výřezu:
         vlevo <tab> nahoře <tab> vpravo <tab> dole <tab> sklon <tab> vzorky <tab> text
         (souřadnice v bodech celého snímku; sklon = dy/dx horní hrany; vzorky = jasy z rámečku řádku
         jako PhoneScreen.samples – nejvýš 48 × 10, oddělené čárkou)

Rozbor uložených snímků (--rozbor: bez modelu a bez okna, pouští ho pc/src/Rozbor.kt):
  SOUBOR <tab> cesta k obrázku
      -> OK <tab> N <tab> šířka <tab> výška <tab> ms  a za tím N řádků z celého obrázku:
         vlevo <tab> nahoře <tab> vpravo <tab> dole <tab> jas <tab> slova <tab> text
         slova = „l,t,r,b,jas;…“ po slovech řádku (z rámečků písmen; prázdné = nevíme)
      -> CHYBA <tab> popis

Čte se jen spodní třetina obrazu, stejně jako v aplikaci pro Android TV.
Výběr titulků a rozhodování, co ukázat, tu není – to dělá čtečka stejnou logikou jako Android.

Záznam pro ladění (--zaznam SLOZKA): celý obraz hry barevně a v plném rozlišení se uloží jako
snimek-HHMMSS-mmm.jpg, nejvýš jednou za --zaznam-s sekund a jen když se obraz od minule změnil.
Text se z něj nečte (čtení by zdržovalo titulky) – přečte se až potom při rozboru.

Zdroj obrazu:
  --okno NAZEV    okno, jehož titulek obsahuje NAZEV (např. chiaki, kde běží obraz z PlayStationu)
  (bez --okno)    ukázka: text ze čtečky se nakreslí na umělý „herní“ obrázek

Windows: snímek okna přes PrintWindow (třída Okno). Mac: Quartz / ScreenCaptureKit přes pyobjc (třída
OknoMac), potřebuje povolení „Nahrávání obrazovky“ pro ReCaption; na Macu se okno hledá i podle jména
programu („chiaki-ng – titulek“).

  --okna          (jen Mac, ladění) vypíše okna na obrazovce a skončí
"""
import argparse
import codecs
import ctypes
import io
import random
import re
import sys
import time

import numpy as np

sys.stdin = io.TextIOWrapper(sys.stdin.buffer, encoding="utf-8", newline="\n")
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", newline="\n")

OBLAST_OD = 2.0 / 3.0      # čte se spodní třetina obrazu


def ven(*casti):
    sys.stdout.write("\t".join(str(c) for c in casti) + "\n")
    sys.stdout.flush()


# ---- obraz z okna (Windows) -------------------------------------------------

class Okno:
    """Snímek obsahu okna podle části jeho titulku. Okno nesmí být minimalizované."""

    def __init__(self, nazev):
        self.nazev = nazev.lower()
        self.u = ctypes.windll.user32
        self.g = ctypes.windll.gdi32
        try:
            ctypes.windll.shcore.SetProcessDpiAwareness(2)      # souřadnice ve skutečných bodech
        except Exception:
            pass
        self.hwnd = None

    def _najdi(self):
        nalezeno = []
        typ = ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)
        # „pid:1234“ = největší viditelné okno procesu (chiaki-ng spuštěný ReCaptionem)
        if self.nazev.startswith("pid:") and self.nazev[4:].isdigit():
            from ctypes import wintypes
            hledany = int(self.nazev[4:])
            okna = []

            def podle_procesu(hwnd, _):
                if self.u.IsWindowVisible(hwnd):
                    pid = wintypes.DWORD()
                    self.u.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
                    if pid.value == hledany:
                        r = wintypes.RECT()
                        self.u.GetWindowRect(hwnd, ctypes.byref(r))
                        okna.append(((r.right - r.left) * (r.bottom - r.top), hwnd))
                return True

            self.u.EnumWindows(typ(podle_procesu), 0)
            return max(okna)[1] if okna else None

        def kazde(hwnd, _):
            if self.u.IsWindowVisible(hwnd):
                n = self.u.GetWindowTextLengthW(hwnd)
                if n:
                    buf = ctypes.create_unicode_buffer(n + 1)
                    self.u.GetWindowTextW(hwnd, buf, n + 1)
                    if self.nazev in buf.value.lower():
                        nalezeno.append(hwnd)
            return True

        self.u.EnumWindows(typ(kazde), 0)
        return nalezeno[0] if nalezeno else None

    def snimek(self):
        from ctypes import wintypes
        if not self.hwnd or not self.u.IsWindow(self.hwnd):
            self.hwnd = self._najdi()
        if not self.hwnd:
            return None, "okno „%s“ není otevřené" % self.nazev
        if self.u.IsIconic(self.hwnd):
            return None, "okno je minimalizované"
        r = wintypes.RECT()
        self.u.GetClientRect(self.hwnd, ctypes.byref(r))
        w, h = r.right, r.bottom
        if w < 320 or h < 180:
            return None, "okno je moc malé"
        # PrintWindow s vykreslením celého obsahu funguje i pro okna kreslená grafickou kartou
        # a i když je okno zakryté jiným.
        dc = self.u.GetDC(self.hwnd)
        mem = self.g.CreateCompatibleDC(dc)
        bmp = self.g.CreateCompatibleBitmap(dc, w, h)
        self.g.SelectObject(mem, bmp)
        self.u.PrintWindow(self.hwnd, mem, 1 | 2)

        class BITMAPINFOHEADER(ctypes.Structure):
            _fields_ = [("biSize", wintypes.DWORD), ("biWidth", wintypes.LONG), ("biHeight", wintypes.LONG),
                        ("biPlanes", wintypes.WORD), ("biBitCount", wintypes.WORD), ("biCompression", wintypes.DWORD),
                        ("biSizeImage", wintypes.DWORD), ("biXPelsPerMeter", wintypes.LONG),
                        ("biYPelsPerMeter", wintypes.LONG), ("biClrUsed", wintypes.DWORD), ("biClrImportant", wintypes.DWORD)]

        bi = BITMAPINFOHEADER()
        bi.biSize = ctypes.sizeof(BITMAPINFOHEADER)
        bi.biWidth, bi.biHeight, bi.biPlanes, bi.biBitCount = w, -h, 1, 32
        buf = ctypes.create_string_buffer(w * h * 4)
        self.g.GetDIBits(mem, bmp, 0, h, buf, ctypes.byref(bi), 0)
        self.g.DeleteObject(bmp)
        self.g.DeleteDC(mem)
        self.u.ReleaseDC(self.hwnd, dc)
        obraz = np.frombuffer(buf, dtype=np.uint8).reshape(h, w, 4)[:, :, :3]      # BGR
        return obraz, None


# ---- obraz z okna (Mac) -------------------------------------------------------

class OknoMac:
    """Snímek obsahu okna na Macu podle jména programu / titulku nebo „pid:1234“.

    Stejné hledání a stejný ořez titulku okna jako `MacOknoHry` v `pc/src/Mac.kt` – poloha titulků ve
    snímku pak sedí s polohou pruhu na obrazovce. Okno smí být zakryté jiným; systém ale obraz cizího
    okna vydá jen s povolením Nahrávání obrazovky (Nastavení systému → Soukromí a zabezpečení).
    """
    TITULEK = 28            # výška titulku běžného okna v bodech; okno přes celou obrazovku ho nemá
    HLEDAT_PO = 1.0         # jak často se znovu zjišťuje, kde okno je (s)

    def __init__(self, nazev):
        import os
        import Quartz
        self.Q = Quartz
        self.nazev = nazev.lower()
        self.rodic = os.getppid()       # okna ReCaptionu (rodič pomocníka) se nečtou
        self.okno = None                # (číslo okna, šířka, výška v bodech, titulek v bodech)
        self.hledano = 0.0
        self.pres_sck = os.environ.get("RECAPTION_SNIMEK", "") == "sck"
        self.sck_filtr = None           # (číslo okna, filtr ScreenCaptureKit)

    @staticmethod
    def seznam(Q, vse=False):
        """Okna odpředu dozadu: (číslo, pid, jméno pro hledání, x, y, šířka, výška)."""
        volba = (Q.kCGWindowListOptionAll if vse else Q.kCGWindowListOptionOnScreenOnly) | Q.kCGWindowListExcludeDesktopElements
        vysledek = []
        for o in Q.CGWindowListCopyWindowInfo(volba, Q.kCGNullWindowID) or []:
            b = o.get("kCGWindowBounds")
            if b is None or int(o.get("kCGWindowLayer", 0)) != 0:
                continue
            vlastnik = str(o.get("kCGWindowOwnerName") or "")
            titulek = str(o.get("kCGWindowName") or "")
            jmeno = vlastnik if not titulek or titulek == vlastnik else vlastnik + " – " + titulek
            vysledek.append((int(o.get("kCGWindowNumber", 0)), int(o.get("kCGWindowOwnerPID", 0)), jmeno,
                             float(b["X"]), float(b["Y"]), float(b["Width"]), float(b["Height"])))
        return vysledek

    def _titulek(self, x, y, w, h):
        """0 pro okno přes celou obrazovku (i pod výřezem kamery), jinak výška titulku okna."""
        Q = self.Q
        try:
            _, obrazovky, _ = Q.CGGetActiveDisplayList(16, None, None)
        except Exception:
            obrazovky = [Q.CGMainDisplayID()]
        for d in obrazovky or []:
            r = Q.CGDisplayBounds(d)
            if (abs(x - r.origin.x) < 2 and abs(w - r.size.width) < 2 and
                    abs((y + h) - (r.origin.y + r.size.height)) < 2 and h >= r.size.height - 60):
                return 0
        return self.TITULEK

    def _najdi(self):
        for vse in (False, True):
            okna = [o for o in self.seznam(self.Q, vse) if o[5] >= 100 and o[6] >= 100]
            if self.nazev.startswith("pid:") and self.nazev[4:].isdigit():
                hledany = int(self.nazev[4:])
                okna = [o for o in okna if o[1] == hledany]
                o = max(okna, key=lambda o: o[5] * o[6]) if okna else None
            else:
                o = next((o for o in okna if o[1] != self.rodic and self.nazev in o[2].lower()), None)
            if o:
                return (o[0], o[5], o[6], self._titulek(o[3], o[4], o[5], o[6]))
        return None

    def _cg(self, cislo, siroke):
        Q = self.Q
        volby = Q.kCGWindowImageBoundsIgnoreFraming | (Q.kCGWindowImageNominalResolution if siroke else Q.kCGWindowImageBestResolution)
        return Q.CGWindowListCreateImage(Q.CGRectNull, Q.kCGWindowListOptionIncludingWindow, cislo, volby)

    def _sck(self, cislo, w, h):
        """Snímek přes ScreenCaptureKit (macOS 14+) – pro systémy, kde starší cesta už obraz nevydá."""
        import threading
        import ScreenCaptureKit as S
        if not self.sck_filtr or self.sck_filtr[0] != cislo:
            hotovo, nalez = threading.Event(), {}

            def obsah(o, chyba):
                nalez["o"] = o
                hotovo.set()

            S.SCShareableContent.getShareableContentExcludingDesktopWindows_onScreenWindowsOnly_completionHandler_(True, False, obsah)
            if not hotovo.wait(5) or nalez.get("o") is None:
                return None
            okno = next((x for x in nalez["o"].windows() if x.windowID() == cislo), None)
            if okno is None:
                return None
            self.sck_filtr = (cislo, S.SCContentFilter.alloc().initWithDesktopIndependentWindow_(okno))
        nastaveni = S.SCStreamConfiguration.alloc().init()
        # velikost snímku = okno ve skutečných bodech displeje (Retina 2×), nejvýš ale 1920 na šířku –
        # víc čtení nepomůže a jen zdržuje; větší snímek, než je okno, by systém nechal z větší části prázdný
        try:
            hustota = float(self.sck_filtr[1].pointPixelScale())
        except Exception:
            hustota = 1.0
        m = 1.0 if w >= 1280 else max(1.0, min(hustota, 1920.0 / w))
        nastaveni.setWidth_(int(w * m))
        nastaveni.setHeight_(int(h * m))
        nastaveni.setScalesToFit_(True)
        nastaveni.setShowsCursor_(False)
        hotovo, nalez = threading.Event(), {}

        def snimek(obraz, chyba):
            nalez["o"] = obraz
            hotovo.set()

        S.SCScreenshotManager.captureImageWithFilter_configuration_completionHandler_(self.sck_filtr[1], nastaveni, snimek)
        if not hotovo.wait(5):
            return None
        if nalez.get("o") is None:
            self.sck_filtr = None
        return nalez.get("o")

    def _pole(self, img):
        """CGImage → pole bodů BGR."""
        Q = self.Q
        iw, ih, radek = Q.CGImageGetWidth(img), Q.CGImageGetHeight(img), Q.CGImageGetBytesPerRow(img)
        if Q.CGImageGetBitsPerPixel(img) != 32:
            return None
        data = Q.CGDataProviderCopyData(Q.CGImageGetDataProvider(img))
        a = np.frombuffer(bytes(data), dtype=np.uint8)[:ih * radek].reshape(ih, radek // 4, 4)[:, :iw]
        info = Q.CGImageGetBitmapInfo(img)
        alfa_prvni = (info & 0x1F) in (2, 4, 6)
        maly = (info & 0x7000) == 0x2000        # kCGBitmapByteOrder32Little
        if maly and alfa_prvni:
            return a[:, :, :3]                  # B G R A
        if maly:
            return a[:, :, 1:4]                 # A B G R
        if alfa_prvni:
            return a[:, :, 3:0:-1]              # A R G B
        return a[:, :, 2::-1]                   # R G B A

    def snimek(self):
        Q = self.Q
        ted = time.time()
        if self.okno is None or ted - self.hledano > self.HLEDAT_PO:
            self.okno = self._najdi()
            self.hledano = ted
        if not self.okno:
            return None, "okno „%s“ není otevřené" % self.nazev
        cislo, w, h, titulek = self.okno
        if w < 320 or h - titulek < 180:
            return None, "okno je moc malé"
        img = None
        if not self.pres_sck:
            try:
                img = self._cg(cislo, w >= 1280)
            except Exception:
                img = None
        if img is None or Q.CGImageGetWidth(img) < 2:
            try:
                img = self._sck(cislo, w, h)
                if img is not None:
                    self.pres_sck = True
            except Exception as e:
                sys.stderr.write("ScreenCaptureKit: %r\n" % e)
                img = None
        povoleno = True
        try:
            povoleno = bool(Q.CGPreflightScreenCaptureAccess())
        except Exception:
            pass
        obraz = self._pole(img) if img is not None and Q.CGImageGetWidth(img) >= 2 else None
        if obraz is None or (not povoleno and int(obraz[::16, ::16].max()) == 0):
            self.okno = None
            if not povoleno:
                return None, "chybí povolení Nahrávání obrazovky"
            return None, "okno nejde vyfotit"
        orez = int(round(titulek * obraz.shape[1] / w))
        return obraz[orez:], None


def okno_hry(nazev):
    return OknoMac(nazev) if sys.platform == "darwin" else Okno(nazev)


def pismo_ukazky(tucne):
    """Písmo ukázkové „hry“ – Arial na Windows i Macu, jinde nejbližší podobné."""
    from PIL import ImageFont
    kandidati = (["C:/Windows/Fonts/arialbd.ttf", "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
                  "/Library/Fonts/Arial Bold.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"] if tucne else
                 ["C:/Windows/Fonts/arial.ttf", "/System/Library/Fonts/Supplemental/Arial.ttf",
                  "/Library/Fonts/Arial.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"])
    velikost = 20 if tucne else 26
    for k in kandidati:
        try:
            return ImageFont.truetype(k, velikost)
        except OSError:
            pass
    try:
        return ImageFont.truetype("Helvetica.ttc", velikost)
    except OSError:
        return ImageFont.load_default(velikost)


# ---- ukázka: umělý „herní“ obrázek s titulkem -------------------------------

class Ukazka:
    """Nakreslí zadaný titulek na barevné neklidné pozadí (zkouška bez PlayStationu)."""
    W, H = 1280, 720

    def __init__(self):
        from PIL import Image, ImageDraw, ImageFilter, ImageFont
        self.Image, self.ImageDraw, self.ImageFont = Image, ImageDraw, ImageFont
        random.seed(7)
        im = Image.new("RGB", (self.W, self.H), (40, 30, 30))
        d = ImageDraw.Draw(im)
        for _ in range(200):
            x, y, r = random.randint(0, self.W), random.randint(0, self.H), random.randint(20, 170)
            d.ellipse([x - r, y - r, x + r, y + r], fill=tuple(random.randint(20, 230) for _ in range(3)))
        self.pozadi = im.filter(ImageFilter.GaussianBlur(5))
        self.pismo = pismo_ukazky(False)
        self.pismo_hud = pismo_ukazky(True)

    def snimek(self, text):
        im = self.pozadi.copy()
        d = self.ImageDraw.Draw(im)
        d.text((40, 28), "OBJECTIVE: Reach the tower", font=self.pismo_hud, fill=(255, 220, 90))
        radky = [r for r in text.split("\n") if r]
        y = self.H - 70 - len(radky) * 34
        for r in radky:
            w = d.textlength(r, font=self.pismo)
            d.text(((self.W - w) / 2, y), r, font=self.pismo, fill=(255, 255, 255), stroke_width=2, stroke_fill=(0, 0, 0))
            y += 34
        return np.asarray(im)[:, :, ::-1], None


# ---- záznam pro ladění -----------------------------------------------------

class Zaznamnik:
    """Ukládá celé snímky hry do složky záznamu (v pozadí, čtení titulků nečeká)."""

    def __init__(self, slozka, interval):
        import os
        import threading
        os.makedirs(slozka, exist_ok=True)
        self.slozka = slozka
        self.interval = max(0.5, interval)
        self.posledni = 0.0
        self.otisk = None
        self.zamek = threading.Lock()
        self.threading = threading

    def mozna(self, obraz):
        ted = time.time()
        if ted - self.posledni < self.interval:
            return
        h, w = obraz.shape[:2]
        vh, vw = (h // 16) * 16, (w // 16) * 16
        otisk = obraz[:vh, :vw].mean(axis=2).reshape(vh // 16, 16, vw // 16, 16).mean(axis=(1, 3))
        if self.otisk is not None and self.otisk.shape == otisk.shape and np.abs(otisk - self.otisk).max() < 6:
            return
        if not self.zamek.acquire(blocking=False):
            return      # předchozí snímek se ještě ukládá
        self.posledni = ted
        self.otisk = otisk
        jmeno = time.strftime("snimek-%H%M%S-", time.localtime(ted)) + "%03d.jpg" % int((ted % 1) * 1000)
        kopie = np.ascontiguousarray(obraz[:, :, ::-1])      # BGR → RGB

        def uloz():
            try:
                from PIL import Image
                Image.fromarray(kopie).save(self.slozka + "/" + jmeno, quality=85)
            except Exception as e:
                sys.stderr.write("záznam: %r\n" % e)
            finally:
                self.zamek.release()

        self.threading.Thread(target=uloz, daemon=True).start()


# ---- překlad ------------------------------------------------------------------

class Prekladac:
    """Překlad bez internetu: model Argos (jádro CTranslate2), dělení slov Moses + BPE."""

    def __init__(self, slozka, cil):
        import ctranslate2
        from sacremoses import MosesDetokenizer, MosesTokenizer
        from subword_nmt.apply_bpe import BPE
        self.model = ctranslate2.Translator(slozka + "/model", device="cpu", inter_threads=1, intra_threads=4)
        self.bpe = BPE(codecs.open(slozka + "/bpe.model", encoding="utf-8"))
        self.tok = MosesTokenizer(lang="en")
        self.detok = MosesDetokenizer(lang=cil)

    def preloz(self, text):
        # Model Argos z více vět najednou občas tu další vynechá („…problem, huh? You pissing me off!“
        # → jen první věta, 25. 11. 2026). Každá věta se proto překládá zvlášť (v jedné dávce).
        vety = vety_textu(text)
        davka = [self.bpe.process_line(self.tok.tokenize(v, return_str=True, escape=True)).split() for v in vety]
        davka = [k for k in davka if k]
        if not davka:
            return ""
        r = self.model.translate_batch(davka, beam_size=4, max_decoding_length=160)
        vysledek = []
        for v in r:
            slova = " ".join(v.hypotheses[0]).replace("@@ ", "").replace("@@", "").split()
            vysledek.append(self.detok.detokenize(slova))
        return " ".join(x for x in vysledek if x)


def vety_textu(text):
    """Rozdělí titulek na věty. Čtečka mezeru za „?“, „!“ a „.“ často spolkne („huh?You“),
    proto se nejdřív doplní – ale ne v číslech („1.5“) ani ve zkratkách s tečkou uvnitř („U.S.“)."""
    text = re.sub(r"([?!])(?=[A-Za-z\"'])", r"\1 ", text)
    # „Yeah,alright,we“ – mezera za čárkou mezi písmeny (čísla jako 1,000 zůstanou)
    text = re.sub(r"(?<=[A-Za-z]),(?=[A-Za-z])", ", ", text)
    text = re.sub(r"(?<=[a-z]{2})\.(?=[A-Z][a-z])", ". ", text)
    vety = []
    for v in re.split(r"(?<=[.?!…])\s+(?=[\"'A-Z0-9¿¡])", text):
        v = v.strip()
        if not v:
            continue
        # „Mr. Smith“: za zkratkou titulu věta nekončí
        if vety and re.search(r"(?:^|\s)(?:Mr|Mrs|Ms|Dr|St|Jr|Sr|vs|Lt|Sgt|Capt|Det|Prof)\.$", vety[-1]):
            vety[-1] += " " + v
        else:
            vety.append(v)
    return vety or [text.strip()]


def velka(text):
    """Řádek psaný velkými písmeny čtečka občas vrátí s pár malými („We HAVe TO GET Out“).
    Když velká písmena jasně převažují, bere se celý jako velký; o zbytek se postará čtečka."""
    pismena = [c for c in text if c.isalpha()]
    if len(pismena) >= 4 and sum(c.isupper() for c in pismena) >= 0.6 * len(pismena):
        return text.upper()
    return text


def barva_jmena(obraz, obdelnik):
    """Barva písma jména mluvčího (jako NameColor.fromSamples na Androidu): nejsvětlejší čtvrtina bodů
    v obdélníku (aspoň jas 110) je písmo, z nich průměrná barva. Bílé/šedé jméno pozná až čtečka."""
    if obraz is None:
        return ("NIC",)
    h, w = obraz.shape[:2]
    l, t, r, b = obdelnik
    l, r = max(0, min(l, w - 1)), max(0, min(r, w))
    t, b = max(0, min(t, h - 1)), max(0, min(b, h))
    if r - l < 2 or b - t < 2:
        return ("NIC",)
    kus = obraz[t:b, l:r].reshape(-1, 3).astype(np.int32)      # BGR
    jas = (kus[:, 2] * 299 + kus[:, 1] * 587 + kus[:, 0] * 114) // 1000
    if len(jas) < 8:
        return ("NIC",)
    mez = max(int(np.sort(jas)[(len(jas) * 3) // 4]), 110)
    pismo = kus[jas >= mez]
    if len(pismo) < 4:
        return ("NIC",)
    bb, gg, rr = pismo.mean(axis=0)
    return ("OK", int(rr), int(gg), int(bb))


def obraz_hry(obraz):
    """Obdélník obrazu hry 16:9 uprostřed snímku, když jsou kolem něj černé pruhy; jinak celý snímek."""
    h, w = obraz.shape[:2]
    if h < 10 or w < 10:
        return 0, 0, w, h
    if w * 9 > h * 16 + 16:
        vw, vh = (h * 16 + 8) // 9, h
    elif h * 16 > w * 9 + 16:
        vw, vh = w, (w * 9 + 8) // 16
    else:
        return 0, 0, w, h
    vx, vy = (w - vw) // 2, (h - vh) // 2
    pruhy = [obraz[:, :vx], obraz[:, vx + vw:]] if vx > 0 else [obraz[:vy], obraz[vy + vh:]]
    if all(p.size == 0 or p[::4, ::4].mean() < 14 for p in pruhy):
        return vx, vy, vw, vh
    return 0, 0, w, h


def jas_bgr(obraz):
    """Jas (luma) jako na Androidu – meze PhoneScreen / PhoneGate jsou vyladěné na něj, ne na prostý průměr barev."""
    if obraz.ndim < 3:
        return obraz.astype(np.float32)
    o = obraz.astype(np.float32)
    return o[..., 0] * 0.114 + o[..., 1] * 0.587 + o[..., 2] * 0.299


def mrizka_jasu(obraz, box, sloupcu, radku):
    """Mřížka jasu jako PhoneGate.sample (aplikace): řez středem každého řádku, průměr přes šířku políčka."""
    h, w = obraz.shape[:2]
    x0 = min(max(int(box[0] * w), 0), w - 1); x1 = min(max(int(box[2] * w), x0 + 1), w)
    y0 = min(max(int(box[1] * h), 0), h - 1); y1 = min(max(int(box[3] * h), y0 + 1), h)
    ven_ = []
    for r in range(radku):
        y = min(y0 + (y1 - y0) * (2 * r + 1) // (2 * radku), h - 1)
        rez = jas_bgr(obraz[y, x0:x1])
        for c in range(sloupcu):
            a = (x1 - x0) * c // sloupcu
            b = max(a + 1, (x1 - x0) * (c + 1) // sloupcu)
            ven_.append(int(rez[a:b].mean()))
    return ven_


def vzorky_jasu(sedy, l, t, r, b):
    """Jasy z rámečku řádku jako PhoneScreen.samples (nejvýš 48 × 10 bodů)."""
    h, w = sedy.shape
    x0 = min(max(l, 0), w - 1); x1 = min(max(r, x0 + 1), w)
    y0 = min(max(t, 0), h - 1); y1 = min(max(b, y0 + 1), h)
    sx = max(1, (x1 - x0) // 48); sy = max(1, (y1 - y0) // 10)
    return sedy[y0:y1:sy, x0:x1:sx].astype(np.int32).ravel()


def pixely(obraz, box):
    """Barvy bodů obdélníku (zlomky) v mřížce nejvýš 40 × 8, jako číslo 0xRRGGBB (obraz je BGR)."""
    h, w = obraz.shape[:2]
    x0 = min(max(int(box[0] * w), 0), w - 1); x1 = min(max(int(box[2] * w), x0 + 1), w)
    y0 = min(max(int(box[1] * h), 0), h - 1); y1 = min(max(int(box[3] * h), y0 + 1), h)
    sx = max(1, (x1 - x0) // 40); sy = max(1, (y1 - y0) // 8)
    o = obraz[y0:y1:sy, x0:x1:sx].astype(np.int64)
    return (o[..., 2] << 16 | o[..., 1] << 8 | o[..., 0]).ravel().tolist()


def zvetseni_telefonu(vyska):
    """Kolikrát zvětšit výřez telefonu: na ~1250 bodů výšky (telefon zabírá ~70 % výšky obrazu), nejvýš 1,6×.
    Dřív ~1600 bodů a 2× – čtení trvalo zhruba dvakrát déle a překlad telefonu naskočil až po ~5 s (majitel 25. 11. 2026)."""
    if vyska <= 0:
        return 1.0
    return max(1.0, min(1.6, 1250.0 / vyska))


def zvetsi(obraz, k):
    try:
        import cv2
        return cv2.resize(obraz, None, fx=k, fy=k, interpolation=cv2.INTER_CUBIC)
    except ImportError:
        from PIL import Image
        h, w = obraz.shape[:2]
        return np.asarray(Image.fromarray(obraz).resize((int(w * k), int(h * k)), Image.BICUBIC))


def cti_telefon(ocr, obraz, box, k_pevne=None):
    """Výřez kolem telefonu v plném rozlišení: řádky v bodech celého snímku se sklonem a vzorky jasu."""
    h, w = obraz.shape[:2]
    x0 = min(max(int(box[0] * w), 0), w - 1); x1 = min(max(int(box[2] * w), x0 + 8), w)
    y0 = min(max(int(box[1] * h), 0), h - 1); y1 = min(max(int(box[3] * h), y0 + 8), h)
    vyrez = obraz[y0:y1, x0:x1]
    sedy = jas_bgr(obraz)
    # písmo v telefonu je malé (~20 bodů) a čtečka pak slepuje slova („askwhenIgoseeher“, 25. 11. 2026) –
    # výřez se čte zvětšený, souřadnice se pak vrátí zpět
    k = zvetseni_telefonu(vyrez.shape[0]) if k_pevne is None else k_pevne
    if k > 1.0:
        vyrez = zvetsi(vyrez, k)
    vysledek, _ = ocr(np.ascontiguousarray(vyrez), use_cls=False)
    radky = []
    for ctyruhelnik, text, jistota in (vysledek or []):
        if jistota < 0.5:
            continue
        xs = [p[0] / k for p in ctyruhelnik]
        ys = [p[1] / k for p in ctyruhelnik]
        l, t, r, b = int(min(xs)) + x0, int(min(ys)) + y0, int(max(xs)) + x0, int(max(ys)) + y0
        dx = float(ctyruhelnik[1][0] - ctyruhelnik[0][0]) / k
        sklon = float(ctyruhelnik[1][1] - ctyruhelnik[0][1]) / k / dx if dx > 8 else 0.0
        vz = ",".join(str(int(v)) for v in vzorky_jasu(sedy, l, t, r, b))
        radky.append((l, t, r, b, "%.4f" % sklon, vz, velka(text.replace("\t", " ").replace("\n", " "))))
    return radky, w, h


def rozbor_souboru(ocr, cesta):
    """Celý uložený snímek: řádky s polohou, jasem pod nimi a rámečky slov (rozbor dávek, Rozbor.kt)."""
    from PIL import Image
    obraz = np.asarray(Image.open(cesta).convert("RGB"))[:, :, ::-1]      # BGR jako snímek okna
    h, w = obraz.shape[:2]
    sedy = obraz.mean(axis=2)

    def jas(l, t, r, b):
        l, t = max(0, min(l, w - 1)), max(0, min(t, h - 1))
        return int(sedy[t:max(t + 1, min(b, h)), l:max(l + 1, min(r, w))].mean())

    t0 = time.time()
    vysledek, _ = ocr(np.ascontiguousarray(obraz), use_cls=False, return_word_box=True)
    ms = int((time.time() - t0) * 1000)
    radky = []
    for polozka in (vysledek or []):
        ctyruhelnik, text, jistota = polozka[0], polozka[1], polozka[2]
        if jistota < 0.5:
            continue
        xs = [b[0] for b in ctyruhelnik]
        ys = [b[1] for b in ctyruhelnik]
        l, t, r, b = int(min(xs)), int(min(ys)), int(max(xs)), int(max(ys))
        slova = []
        if len(polozka) > 4:
            # rámečky jednotlivých znaků → slova (mezera odděluje)
            kus = None
            for ram, znak in zip(polozka[3], polozka[4]):
                if not znak.strip():
                    if kus:
                        slova.append(kus)
                    kus = None
                    continue
                box = [min(p[0] for p in ram), min(p[1] for p in ram), max(p[0] for p in ram), max(p[1] for p in ram)]
                kus = box if kus is None else [min(kus[0], box[0]), min(kus[1], box[1]), max(kus[2], box[2]), max(kus[3], box[3])]
            if kus:
                slova.append(kus)
        sl = ";".join("%d,%d,%d,%d,%d" % (x0, y0, x1, y1, jas(int(x0), int(y0), int(x1), int(y1))) for x0, y0, x1, y1 in slova)
        radky.append((l, t, r, b, jas(l, t, r, b), sl, velka(text.replace("\t", " ").replace("\n", " "))))
    return w, h, ms, radky


def rozbor(ocr):
    """Režim --rozbor: jen příkaz SOUBOR (čtení uložených snímků), žádný překlad ani okno."""
    ven("PRIPRAVENO")
    for radek in sys.stdin:
        cast = radek.rstrip("\n").split("\t")
        try:
            if cast[0] == "SOUBOR" and len(cast) > 1:
                w, h, ms, radky = rozbor_souboru(ocr, cast[1])
                ven("OK", len(radky), w, h, ms)
                for r in radky:
                    ven(*r)
            else:
                ven("CHYBA", "neznámý příkaz")
        except Exception as e:      # jeden vadný soubor nesmí rozbor shodit
            ven("CHYBA", repr(e).replace("\t", " ").replace("\n", " ")[:200])


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--model", default="", help="složka rozbaleného překladového modelu Argos")
    p.add_argument("--rozbor", action="store_true", help="jen čtení uložených snímků (příkaz SOUBOR), bez překladu a okna")
    p.add_argument("--okna", action="store_true", help="(Mac) vypsat okna na obrazovce a skončit")
    p.add_argument("--jazyk", default="cs")
    p.add_argument("--okno", default="", help="část titulku okna s obrazem hry; bez toho běží ukázka")
    p.add_argument("--zaznam", default="", help="složka pro snímky hry (ladění); bez toho se nic neukládá")
    p.add_argument("--zaznam-s", type=float, default=2.0, help="nejkratší odstup snímků záznamu v sekundách")
    p.add_argument("--vedlejsi", action="store_true",
                   help="druhý pomocník (telefon, menu): nižší priorita a méně vláken, ať nebrzdí čtení titulků")
    a = p.parse_args()
    if a.vedlejsi and sys.platform == "win32":
        try:
            import ctypes
            ctypes.windll.kernel32.SetPriorityClass(ctypes.windll.kernel32.GetCurrentProcess(), 0x4000)  # BELOW_NORMAL
        except Exception:
            pass
    if a.okna:
        import Quartz
        print("povolení Nahrávání obrazovky:", bool(Quartz.CGPreflightScreenCaptureAccess()))
        for o in OknoMac.seznam(Quartz):
            print("%d\tpid:%d\t%s\t%d×%d" % (o[0], o[1], o[2], o[5], o[6]))
        return

    from rapidocr_onnxruntime import RapidOCR
    # delší strana nejvýš 1280 bodů: čtení pásu s titulky pak trvá asi pětinu sekundy
    if a.vedlejsi:
        try:
            ocr = RapidOCR(det_limit_side_len=1280, det_limit_type="max", intra_op_num_threads=2, inter_op_num_threads=1)
        except TypeError:
            ocr = RapidOCR(det_limit_side_len=1280, det_limit_type="max")
    else:
        ocr = RapidOCR(det_limit_side_len=1280, det_limit_type="max")
    if a.rozbor:
        rozbor(ocr)
        return
    if not a.model:
        p.error("chybí --model")
    prekladac = Prekladac(a.model, a.jazyk)
    zdroj = okno_hry(a.okno) if a.okno else Ukazka()
    zaznamnik = Zaznamnik(a.zaznam, a.zaznam_s) if a.zaznam else None
    ocr(np.zeros((120, 640, 3), dtype=np.uint8), use_cls=False)      # první čtení trvá déle – ať to není až ve hře
    prekladac.preloz("Hello.")
    ven("PRIPRAVENO")

    minule = None
    posledni = None      # celý poslední snímek (BGR) – pro barvu jména a menu
    for radek in sys.stdin:
        cast = radek.rstrip("\n").split("\t")
        try:
            if cast[0] == "OKNO":
                # čtečka pro TV: po každém připojení k PS5 je chiaki-ng nový proces s novým oknem
                if not isinstance(zdroj, Ukazka) and len(cast) > 1 and cast[1]:
                    zdroj = okno_hry(cast[1])
                    minule = None
                    posledni = None
                    ven("OK")
                else:
                    ven("CHYBA", "okno nejde změnit")
            elif cast[0] == "BARVA":
                ven(*barva_jmena(posledni, [int(float(c)) for c in cast[1:5]]))
            elif cast[0] == "MENU":
                if posledni is None:
                    ven("OK", 0, 0, 0, 0)
                    continue
                t0 = time.time()
                h, w = posledni.shape[:2]
                krok = max(1, (w + 959) // 960)
                mensi = posledni[:int(h * 0.6):krok, ::krok]
                sedy = mensi.mean(axis=2)
                vysledek, _ = ocr(np.ascontiguousarray(mensi), use_cls=False)
                radky = []
                for ctyruhelnik, text, jistota in (vysledek or []):
                    if jistota < 0.5:
                        continue
                    xs = [b[0] for b in ctyruhelnik]
                    ys = [b[1] for b in ctyruhelnik]
                    l, t, r, b = int(min(xs)), int(min(ys)), int(max(xs)), int(max(ys))
                    jas = int(sedy[max(0, t):max(t + 1, b), max(0, l):max(l + 1, r)].mean())
                    radky.append((l, t, r, b, jas, text.replace("\t", " ").replace("\n", " ")))
                ven("OK", len(radky), w // krok, h // krok, int((time.time() - t0) * 1000))
                for r in radky:
                    ven(*r)
            elif cast[0] == "SNIMEK":
                if isinstance(zdroj, Ukazka):
                    ven("CHYBA", "ukázka")
                    continue
                obraz, chyba = zdroj.snimek()
                if obraz is None:
                    posledni = None
                    ven("CHYBA", chyba)
                else:
                    posledni = obraz
                    ven("OK", obraz.shape[1], obraz.shape[0])
            elif cast[0] == "ROZMER":
                if posledni is None:
                    ven("NIC")
                    continue
                vx, vy, vw, vh = obraz_hry(posledni)
                ven("OK", vx, vy, vw, vh)
            elif cast[0] == "MRIZKA":
                if posledni is None:
                    ven("NIC")
                    continue
                vx, vy, vw, vh = obraz_hry(posledni)
                ven("OK", ",".join(str(v) for v in mrizka_jasu(posledni[vy:vy + vh, vx:vx + vw], [float(c) for c in cast[1:5]], int(cast[5]), int(cast[6]))))
            elif cast[0] == "PIXELY":
                if posledni is None:
                    ven("NIC")
                    continue
                vx, vy, vw, vh = obraz_hry(posledni)
                ven("OK", ",".join(str(v) for v in pixely(posledni[vy:vy + vh, vx:vx + vw], [float(c) for c in cast[1:5]])))
            elif cast[0] == "TELEFON":
                if posledni is None:
                    ven("OK", 0, 0, 0, 0)
                    continue
                t0 = time.time()
                vx, vy, vw, vh = obraz_hry(posledni)
                radky, w, h = cti_telefon(ocr, posledni[vy:vy + vh, vx:vx + vw], [float(c) for c in cast[1:5]],
                                          float(cast[5]) if len(cast) > 5 else None)
                ven("OK", len(radky), w, h, int((time.time() - t0) * 1000))
                for r in radky:
                    ven(*r)
            elif cast[0] == "PRELOZ":
                ven("OK", prekladac.preloz(cast[1] if len(cast) > 1 else "").replace("\t", " ").replace("\n", " "))
            elif cast[0] == "CTI":
                vynutit = len(cast) > 1 and cast[1] == "1"
                if isinstance(zdroj, Ukazka):
                    obraz, chyba = zdroj.snimek((cast[2] if len(cast) > 2 else "").replace("\\n", "\n"))
                else:
                    obraz, chyba = zdroj.snimek()
                if obraz is None:
                    minule = None
                    ven("CHYBA", chyba)
                    continue
                posledni = obraz
                if zaznamnik:
                    zaznamnik.mozna(obraz)
                h, w = obraz.shape[:2]
                y0 = int(h * OBLAST_OD)
                vyrez = obraz[y0:h]
                # hrubý otisk výřezu: když se od minula nezměnil, číst znovu netřeba
                # (průměr jasu po čtverečcích 8×8; stačí jediný změněný čtvereček – i jednoslovný
                # titulek „Wait“ zabere jen zlomek výřezu a v průměru celé plochy by zanikl)
                vh, vw = (vyrez.shape[0] // 8) * 8, (vyrez.shape[1] // 8) * 8
                otisk = vyrez[:vh, :vw].mean(axis=2).reshape(vh // 8, 8, vw // 8, 8).mean(axis=(1, 3))
                stejne = minule is not None and minule.shape == otisk.shape and np.abs(otisk - minule).max() < 6
                minule = otisk
                if stejne and not vynutit:
                    ven("STEJNE")
                    continue
                t0 = time.time()
                vysledek, _ = ocr(np.ascontiguousarray(vyrez), use_cls=False)
                ms = int((time.time() - t0) * 1000)
                radky = []
                for ctyruhelnik, text, jistota in (vysledek or []):
                    if jistota < 0.5:
                        continue
                    xs = [b[0] for b in ctyruhelnik]
                    ys = [b[1] for b in ctyruhelnik]
                    radky.append((int(min(xs)), int(min(ys)), int(max(xs)), int(max(ys)), velka(text.replace("\t", " ").replace("\n", " "))))
                ven("OK", len(radky), w, h, 0, y0, ms)
                for r in radky:
                    ven(*r)
            else:
                ven("CHYBA", "neznámý příkaz")
        except Exception as e:      # jedna chyba nesmí čtečku shodit
            ven("CHYBA", repr(e).replace("\t", " ").replace("\n", " ")[:200])


if __name__ == "__main__":
    main()
