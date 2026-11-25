#!/usr/bin/env python3
"""Zabalí složku webos/app do instalačního souboru pro televize LG (.ipk).

Použití:  python3 webos/sestavit.py
Výsledek: webos/ReCaption-zkouska.ipk

Dělá totéž co oficiální nástroj LG `ares-package`, jen bez instalace čehokoli:
soubor .ipk je archiv `ar` se třemi částmi (debian-binary, control.tar.gz, data.tar.gz).
"""
import io, json, os, tarfile

TADY = os.path.dirname(os.path.abspath(__file__))
APP = os.path.join(TADY, "app")
info = json.load(open(os.path.join(APP, "appinfo.json"), encoding="utf-8"))
ID, VERZE = info["id"], info["version"]


def pridej(tar, jmeno, data=None, slozka=False):
    t = tarfile.TarInfo(jmeno)
    t.uid = t.gid = 0
    t.uname = t.gname = "root"
    t.mtime = 0
    if slozka:
        t.type, t.mode = tarfile.DIRTYPE, 0o755
        tar.addfile(t)
    else:
        t.mode, t.size = 0o644, len(data)
        tar.addfile(t, io.BytesIO(data))


def targz(napln):
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz", format=tarfile.GNU_FORMAT) as tar:
        napln(tar)
    return buf.getvalue()


soubory = sorted(f for f in os.listdir(APP) if os.path.isfile(os.path.join(APP, f)))
velikost = sum(os.path.getsize(os.path.join(APP, f)) for f in soubory)


def data(tar):
    cesta = ""
    for dil in ["usr", "palm", "applications", ID]:
        cesta += dil + "/"
        pridej(tar, cesta.rstrip("/"), slozka=True)
    for f in soubory:
        pridej(tar, "usr/palm/applications/%s/%s" % (ID, f), open(os.path.join(APP, f), "rb").read())
    pridej(tar, "usr/palm/packages", slozka=True)
    pridej(tar, "usr/palm/packages/" + ID, slozka=True)
    balik = {"app": ID, "id": ID, "loc_name": info["title"], "package_format_version": 2,
             "vendor": info.get("vendor", ""), "version": VERZE}
    pridej(tar, "usr/palm/packages/%s/packageinfo.json" % ID,
           (json.dumps(balik, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))


def control(tar):
    text = "\n".join([
        "Package: " + ID,
        "Version: " + VERZE,
        "Section: misc",
        "Priority: optional",
        "Architecture: all",
        "Installed-Size: %d" % velikost,
        "Maintainer: N/A <nobody@example.com>",
        "Description: " + info["title"],
        "webOS-Package-Format-Version: 2",
        "webOS-Packager-Version: x.y.x",
        "",
    ])
    pridej(tar, "control", text.encode("utf-8"))


vystup = os.path.join(TADY, "ReCaption-zkouska.ipk")
# Archiv `ar` se skládá ručně, aby sestavení šlo i na Windows, kde program `ar` není:
# hlavička souboru, pak za každou část řádek o 60 znacích, obsah a zarovnání na sudou délku.
with open(vystup, "wb") as ipk:
    ipk.write(b"!<arch>\n")
    for jmeno, obsah in [("debian-binary", b"2.0\n"), ("control.tar.gz", targz(control)),
                         ("data.tar.gz", targz(data))]:
        ipk.write(("%-16s%-12d%-6d%-6d%-8s%-10d`\n" % (jmeno + "/", 0, 0, 0, "100644", len(obsah))).encode("ascii"))
        ipk.write(obsah)
        if len(obsah) % 2:
            ipk.write(b"\n")
print("Hotovo:", vystup, "(%d kB)" % (os.path.getsize(vystup) // 1024))
