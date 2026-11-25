"""Zkouška balíčku ke stažení (ReCaption-PC.zip) na GitHubu: přibalený Python s knihovnami přečte
ukázkový titulek (RapidOCR) a přeloží ho (model Argos) – přesně tak, jak to dělá čtečka (cteni.py).

  python zkouska_balicku.py <python z balíčku> <cteni.py> <složka modelu> [okno [složka]]

S [okno] (např. pid:1234) se věta nekreslí na umělý obrázek, ale čte se ze snímku skutečného okna, které
ji ukazuje (`recaption-pc --zkouska-okno`) – zkouška snímku okna na Macu.

Konec s kódem 0 = čtení i překlad fungují. Pouští ho `.github/workflows/pc.yml` a `mac.yml`.
"""
import subprocess
import sys
import time

python, cteni, model = sys.argv[1:4]
okno = sys.argv[4] if len(sys.argv) > 4 else ""
zaznam = sys.argv[5] if len(sys.argv) > 5 else ""      # sem se uloží snímek okna, jak ho čtečka dostala
VETA = "Where are you going tonight?"

p = subprocess.Popen([python, cteni, "--model", model, "--jazyk", "cs"] + (["--okno", okno] if okno else []) +
                     (["--zaznam", zaznam] if zaznam else []), stdin=subprocess.PIPE,
                     stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8")


def radek():
    r = p.stdout.readline()
    if not r:
        print("CHYBA: pomocník skončil\n" + p.stderr.read())
        sys.exit(1)
    return r.rstrip("\n")


def posli(s):
    p.stdin.write(s + "\n")
    p.stdin.flush()


t0 = time.time()
prvni = radek()
print("start:", prvni, "za %.1f s" % (time.time() - t0))
if prvni != "PRIPRAVENO":
    sys.exit(1)

posli("CTI\t1" if okno else "CTI\t1\t" + VETA)
hlava = radek().split("\t")
print("čtení:", hlava)
if hlava[0] != "OK":
    sys.exit(1)
precteno = [radek().split("\t")[-1] for _ in range(int(hlava[1]))]
print("přečteno:", precteno)
if "GOING" not in " ".join(precteno).upper():
    print("CHYBA: věta se nepřečetla")
    if zaznam:
        time.sleep(1.5)
    sys.exit(1)

posli("PRELOZ\t" + VETA)
preklad = radek().split("\t")
print("překlad:", preklad)
if preklad[0] != "OK" or len(preklad) < 2 or not preklad[1].strip():
    sys.exit(1)

if zaznam:
    time.sleep(1.5)      # snímek záznamu se ukládá v pozadí
p.stdin.close()
print("OK")
