#!/bin/bash
# Automatická zkouška v emulátoru Android TV: instalace, průvodce, všechny obrazovky,
# spuštění překladu nad ukázkovou „hrou“ a kontrola, že se titulky přeložily.
set -x
OUT=test-results
mkdir -p $OUT
PKG=cz.recaption
# Žádný příkaz pro emulátor nesmí zkoušku zablokovat: emulátor Androidu 14 občas přestane
# odpovídat (výpis záznamu po aktualizaci, stisk klávesy) a celá zkouška pak visela až do limitu.
ADB_BIN=$(command -v adb)
adb() { timeout 180 "$ADB_BIN" "$@"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
key() { adb shell input keyevent "$1"; sleep 1; }

adb install -r TestPreklad-emulator.apk || { echo "INSTALL FAILED" > $OUT/result.txt; exit 1; }
adb shell getprop ro.build.version.release > $OUT/device.txt
adb shell getprop ro.product.model >> $OUT/device.txt
adb shell wm size >> $OUT/device.txt
adb shell cat /proc/meminfo | head -1 >> $OUT/device.txt
# Android 13+: povolení notifikací, aby dotaz nepřekryl průvodce
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb logcat -c

# 1) první spuštění: průvodce krok za krokem (OK = Pokračovat; pořadí kroků v MainActivity.wizardSteps:
#    jazyky (jazyk aplikace a „Překládat z“), [zařízení, televize – na Android TV od 1.00 přeskočené, `autoStep`], konzole, jazyk překladu,
#    lepší překlad (DeepL – OK = Přeskočit, zůstat u offline), povolení pruhu,
#    připojení konzole (jedna řada tlačítek, OK = Pokračovat spárování `pnext`), hotovo)
adb shell am start -n $PKG/.MainActivity
sleep 10; shot 00_pruvodce_jazyky
key 23; sleep 2; shot 01b_pruvodce_konzole
key 23; sleep 2; shot 03_pruvodce_jazyk
sleep 45; shot 03b_pruvodce_jazyk_po_stazeni
key 23; sleep 2; shot 03c_pruvodce_preklad
key 23; sleep 2; shot 02_pruvodce_povoleni
# příprava spárování (sloučeno 23. 11. 2026): PlayStation v síti + účet pro ReCaption na jedné obrazovce,
# appka hledá PS5 potichu sama – druhý snímek jen po chvíli čekání (emulátor PS5 nenajde, zůstává na témže kroku)
key 23; sleep 2; shot 04_pruvodce_ps5_krok1
sleep 8; shot 04b_pruvodce_ps5_krok2_hledani
# zpět v průvodci spárováním a pak dokončení průvodce
adb shell am start -n $PKG/.MainActivity --ez nowizard true
sleep 3; shot 06_domu

# 2) všechny stránky menu: Titulky, Konzole, Instalace, Videonávody, Nastavení (⚙, poslední – dolů z Videonávodů vede na ⚙, `linkGear`)
for i in 1 2 3 4 5; do key 20; key 23; sleep 2; shot "07_menu_$i"; done

# 3) povolení a spuštění překladu (povolení snímání udělí test místo člověka)
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
# Usnadnění zapnuté jako u skutečného uživatele (pruh přes Usnadnění, aplikace ví, co je na obrazovce)
adb shell am force-stop $PKG
# (vynucené zastavení aplikace Usnadnění vypíná, proto se zapíná až po něm)
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.TitulkyAccessibility
adb shell settings put secure accessibility_enabled 1
sleep 4
adb shell am start -n $PKG/.MainActivity --ez autostart true
sleep 12; shot 08_po_spusteni
# jazykový balíček pro překlad se stahuje od Googlu – počkat, až je (nejvýš 3 minuty; aplikace po
# neúspěchu zkouší stáhnout znovu sama). Bez něj by se ukázková hra jen četla a nepřekládala.
for i in $(seq 1 60); do adb logcat -d | grep -q "TP_TEST.*MODEL|[a-z]*|ok" && break; sleep 3; done
adb logcat -d | grep "TP_TEST.*MODEL|" | tail -3 > $OUT/model.txt

# 4) ukázková hra s titulky – jde do aplikace jako video (stejná cesta jako z PlayStationu);
#    aplikace ji má dekódovat, přečíst, přeložit a zakrýt pruhem
adb shell am start -n $PKG/.TestCardActivity
for i in 01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48 49 50 51 52; do sleep 3; shot "09_hra_$i"; done

# 5) spojení s PlayStationem: konzole tu není, ale ověří se, že se nativní část načte,
#    hledání v síti a párování skončí slušně a spojení se zkouší znovu – bez pádu
key 4; sleep 2
adb shell am start -n $PKG/.MainActivity --es ps5test 10.0.2.2
sleep 45; shot 10_ps5_nedostupna
key 20; key 23; sleep 3; shot 11_stranka_playstation
# 5b) vybraná konzole Xbox: stránka konzole s přihlášením účtem Microsoft (jen vzhled, bez sítě;
#     Xbox emulátor nemá, do výsledku se nepočítá) a pak zpět na PlayStation pro další části zkoušky
adb shell am start -n $PKG/.MainActivity --es console xbox
sleep 4; shot 11b_stranka_xbox
adb shell am start -n $PKG/.MainActivity --es console ps
sleep 2

# 6) přihlášení přes telefon: QR kód a políčko na kód (sloučení kroků 23. 11. 2026 posunulo čísla kroků
#    přihlášení o jedna dolů – pod pairstep 0 je teď příprava: PlayStation v síti + účet)
adb shell am start -n $PKG/.MainActivity --ez phonelogin true --ei pairstep 1
sleep 4; shot 12j_ucet_1_qr
adb shell am start -n $PKG/.MainActivity --ez phonelogin true --ei pairstep 2
sleep 3; shot 12k_ucet_2_prihlaseni
adb shell am start -n $PKG/.MainActivity --ez phonelogin true --ei pairstep 3
sleep 3; shot 12l_ucet_3_kod
adb shell am start -n $PKG/.MainActivity --ez phonelogin true --ei pairstep 5
sleep 3; shot 12m_cislo_z_ps5

# 6b) přihlášení přes telefon bez opisování. Do výsledku zkoušky se nepočítá, jen se zapíše a vyfotí.
#  a) stránka z televize: odpovídá, cizí klíč odmítne a účet poslaný „z telefonu“ posune průvodce
#     na krok s číslem z PS5 (na snímku má být „Účet ověřen“)
adb shell am start -n $PKG/.MainActivity --ez logintest true
sleep 8; shot 12n_ucet_z_telefonu
#  b) ReCaption v telefonu (pomocník). Otevírá se odkazem ze stránky televize, stejně jako
#     u uživatele (Android 13 jiné spuštění zvenčí nepustí): hlavní obrazovka má hlásit, že
#     televize čeká na účet; pak přihlašovací stránka Sony uvnitř aplikace.
adb shell "am start -n $PKG/.PhoneHelperActivity -a android.intent.action.VIEW -d 'recaption://login?h=10.0.2.15&p=8765&k=abcd2345&l=cs'"
sleep 4; shot 12o_pomocnik
#     Přihlašovací stránka Sony jen do Androidu 13: emulátor Androidu 14 (na GitHubu bez grafické
#     karty) při jejím vykreslení zamrzne celý a zkouška pak stojí až do limitu 50 minut (19. 11. 2026
#     třikrát ze čtyř běhů, vždy na snímku 12p; stejně jako v ci/phone-test.sh). Přihlášení u Sony
#     emulátor stejně neověří – to umí jen skutečné zařízení.
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
if [ "${SDK:-0}" -lt 34 ]; then
  adb shell "am start -n $PKG/.PhoneHelperActivity -a android.intent.action.VIEW -d 'recaption://login?h=10.0.2.15&p=8765&k=abcd2345&l=cs' --ez helperlogin true"
  sleep 25; shot 12p_pomocnik_prihlaseni
  key 4; sleep 1; key 4; sleep 1; key 4; sleep 1
fi

# 6c) zkouška překladu: typické herní repliky projdou překladem stejně jako titulky ze hry.
#     Do výsledku se nepočítá; věty a jejich překlad jsou v preklad.txt (podle nich se ladí přirozenost).
adb shell am start -n $PKG/.MainActivity --ez trtest true
for i in $(seq 1 60); do sleep 3; adb logcat -d | grep -q "TP_TEST.*TR|done" && break; done

# 6d) tytéž repliky do všech jazyků překladu (offline, ML Kit) – jen na Androidu 13, ať se zkouška
#     neprodlouží všude. Do výsledku se nepočítá; výstup preklad-<kód>.txt (anglicky|překlad).
#     Záznam se sbírá průběžně (logcat by starší řádky během stahování balíčků mohl přepsat).
#     Dvě dávky: dosavadních 9 jazyků (nejvýš 8 min) a 12 jazyků jen pro překlad (nejvýš 10 min; sr jede
#     přes chorvatský model, takže 11 balíčků po ~30 MB). Druhá dávka jen když zkouška běží méně než 25 min –
#     úloha má limit 50 minut. Každá dávka začíná s čistou aplikací (načtené modely drží hodně paměti).
trl_batch() {   # $1 = kódy jazyků oddělené čárkou, $2 = nejvýš počet čekání po 6 s
  adb shell am force-stop $PKG || true
  sleep 2
  adb shell am start -n $PKG/.MainActivity --ez trtest true --es trlangs "$1" || true
  : > $OUT/trl_raw.txt
  for i in $(seq 1 $2); do
    sleep 6
    # jen řádky od začátku této dávky (záznam se nemaže – zkouška z něj dál počítá výsledek)
    adb logcat -d -v time 2>/dev/null | sed -n "/TRL|start $1 /,\$p" | grep "TP_TEST.*TRL|" >> $OUT/trl_raw.txt
    grep -q "TRL|alldone" $OUT/trl_raw.txt && break
  done
  awk '!seen[$0]++' $OUT/trl_raw.txt | sed 's/.*TRL|//' > $OUT/trl.txt || true
  for c in $(echo "$1" | tr ',' ' '); do
    grep "^$c|" $OUT/trl.txt | awk -F'|' 'NF>=3' | sed "s/^$c|//" > $OUT/preklad-$c.txt || true
  done
  grep -E "^(start|[a-z]+\|model=|done|alldone)" $OUT/trl.txt >> $OUT/preklad-stav.txt || true
  rm -f $OUT/trl_raw.txt $OUT/trl.txt
}
if [ "${SDK:-0}" -eq 33 ]; then
  adb logcat -G 16M 2>/dev/null || true
  : > $OUT/preklad-stav.txt
  trl_batch "cs,sk,pl,de,hu,uk,es,fr,it,hi" 90 || true
  if [ "$SECONDS" -lt 1500 ]; then
    trl_batch "ro,hr,sr,sl,bg,mk,sq,lt,lv,et,el,tr" 100 || true
  else
    echo "druha davka vynechana (zkouska bezi ${SECONDS} s)" >> $OUT/preklad-stav.txt
  fi
  if [ "$SECONDS" -lt 2000 ]; then
    trl_batch "nl,sv,da,nb,fi,id,vi" 70 || true
  else
    echo "treti davka vynechana (zkouska bezi ${SECONDS} s)" >> $OUT/preklad-stav.txt
  fi
  if [ "$SECONDS" -lt 2300 ]; then
    trl_batch "he,th" 30 || true
  else
    echo "ctvrta davka vynechana (zkouska bezi ${SECONDS} s)" >> $OUT/preklad-stav.txt
  fi
  if [ "$SECONDS" -lt 2400 ]; then
    trl_batch "ca,tl,ms,is,sw,af" 60 || true
  else
    echo "pata davka vynechana (zkouska bezi ${SECONDS} s)" >> $OUT/preklad-stav.txt
  fi
  # načtené jazykové modely drží hodně paměti – další kroky začnou s čistou aplikací
  adb shell am force-stop $PKG || true
  sleep 2
fi

# 7) nabídka se seznamem (jazyk): stránka Titulky (od 0.97.52 hned pod Domů) → první řádek → OK
adb shell am start -n $PKG/.MainActivity --ez nowizard true
# Šipka vpravo z menu padne na řádek vedle (Kvalita překladu, od 0.97.58 je nad ní i „Překládat z“),
# proto ještě nahoru – z prvního řádku (Jazyk překladu) nahoru nic nevede, takže šipka navíc nic nezkazí.
sleep 2; key 20; key 23; sleep 1; key 22; sleep 1; key 19; key 19; key 19; sleep 1; key 23; sleep 2; shot 13_nabidka_jazyk
key 4; sleep 1

# 8) jiné rozlišení obrazovky televize: 720p. Pruh se má i tam postavit přesně na titulek.
#    (4K emulátor nepřepne – zůstane na 1080p, taková zkouška by nic neříkala.)
for mode in "1280x720 213 720p"; do
  set -- $mode
  adb shell am force-stop $PKG
  adb shell wm size $1; adb shell wm density $2; sleep 3
  adb shell settings put secure enabled_accessibility_services $PKG/$PKG.TitulkyAccessibility
  adb shell settings put secure accessibility_enabled 1
  sleep 4
  adb shell am start -n $PKG/.MainActivity --ez autostart true
  sleep 12
  adb shell am start -n $PKG/.TestCardActivity
  sleep 20; shot "14_rozliseni_$3_a"
  sleep 8; shot "14_rozliseni_$3_b"
  sleep 8; shot "14_rozliseni_$3_c"
  key 4; sleep 1
done
adb shell am force-stop $PKG
adb shell wm size reset; adb shell wm density reset; sleep 2

# 9) aktualizace z aplikace. Do výsledku zkoušky se nepočítá, jen se zapíše a vyfotí.
#  a) úplně poprvé, bez povolení „neznámé zdroje“: co televize uživateli ukáže
adb shell am start -n $PKG/.MainActivity --ez updself true
sleep 12; shot 15a_poprve_bez_povoleni
key 4; sleep 2; key 4; sleep 2
adb shell am force-stop $PKG
#  b) stažení vydané verze z internetu a předání systému. Emulátor ji nenainstaluje (jiný procesor
#     a podpis, na Androidu 13+ ji systém odmítne hned) – ověřuje se stažení a kontrola souboru.
adb shell appops set $PKG REQUEST_INSTALL_PACKAGES allow
adb shell am start -n $PKG/.MainActivity --ez updtest true
sleep 40; shot 15b_stazeni
key 4; sleep 3
adb shell am force-stop $PKG
#  c) celá aktualizace: aplikace nainstaluje sama sebe (stejná verze i podpis) a instalace se potvrdí.
#     Stav jako u uživatele: Usnadnění zapnuté, povolení překryvu ne.
adb shell appops set $PKG SYSTEM_ALERT_WINDOW default
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.TitulkyAccessibility
adb shell settings put secure accessibility_enabled 1
sleep 3
adb shell am start -n $PKG/.MainActivity --ez updself true
sleep 12; shot 15c_dotaz
# Potvrdit jen tehdy, když se systém opravdu ptá (na Androidu 12+ projde instalace bez dotazu).
# Dolů = na Androidu 9 teprve tím dostanou tlačítka výběr; dvakrát vpravo = Instalovat.
if adb shell dumpsys activity activities | grep -E "ResumedActivity" | grep -q packageinstaller; then
  key 20; key 22; key 22; key 23
fi
sleep 25; shot 15c_po_instalaci
adb shell dumpsys activity activities | grep -m1 -E "ResumedActivity" > $OUT/po_aktualizaci.txt
#  d) podruhé: na Androidu 12+ má instalace projít bez dotazu
adb shell am start -n $PKG/.MainActivity --ez updself true
sleep 25; shot 15d_podruhe
key 4; sleep 2
adb shell am force-stop $PKG

adb logcat -d -v time > $OUT/logcat_full.txt
grep -E "TP_TEST|TestPreklad|AndroidRuntime|FATAL|libchiaki|DEBUG   :" $OUT/logcat_full.txt > $OUT/log.txt
# pád aplikace i s okolím (příčina bývá na řádcích hned pod "Fatal signal")
grep -n -B8 -A70 -E "Fatal signal|Process: cz.recaption" $OUT/logcat_full.txt | head -400 > $OUT/crash.txt || true
SHOWS=$(grep -c "TP_TEST.*SHOW|" $OUT/log.txt)
adb shell dumpsys meminfo $PKG | grep -E "TOTAL|Native Heap|Dalvik Heap" | head -4 > $OUT/memory.txt
# počítají se jen pády naší aplikace (na Androidu 9 padá vestavěná aplikace emulátoru, to není naše chyba)
CRASH=$(grep -cE "Process: cz.recaption|Fatal signal.*cz.recaption" $OUT/logcat_full.txt)
LIB=$(grep -c "TP_TEST.*START|.*lib=ok" $OUT/log.txt)
DECODED=$(grep "TP_TEST.*VIDEO|" $OUT/log.txt | grep -oE "decoded=[0-9]+" | cut -d= -f2 | sort -n | tail -1)
LINK=$(grep -c "TP_TEST.*LINK|\(WAITING\|FAILED\)" $OUT/log.txt)
PAIR=$(grep -c "TP_TEST.*PAIR|\(failed\|timeout\|error\)" $OUT/log.txt)
UPD_DL=$(grep -c "TP_TEST.*UPD|downloaded" $OUT/log.txt)
UPD_ASK=$(grep -c "TP_TEST.*UPD|confirm shown" $OUT/log.txt)
UPD_DONE=$(grep -c "TP_TEST.*UPD|replaced" $OUT/log.txt)
LOGIN_PAGE=$(grep -c "TP_TEST.*LOGIN|test app OK" $OUT/log.txt)
HELPER=$(grep -c "TP_TEST.*HELPER|link ok" $OUT/log.txt)
# kolik z deseti různých ukázkových titulků (velká písmena, jména, krátká slova…) se přeložilo
KINDS=0
for k in "We need money" "reckon" "ride at dawn" "keep going like this" "right way" "no idea" "GET OUT OF HERE" "|Wait|" "never supposed to meet" "left door" "voice down" "been here for years" "lantern" "bridge is out" "Run for the gate"; do
  grep -qF -- "$k" <(grep "TP_TEST.*SHOW|" $OUT/log.txt) && KINDS=$((KINDS+1)) || echo "NEPRELOZENO: $k" >> $OUT/missing.txt
done
echo "ruznych_titulku=$KINDS/15" > $OUT/kinds.txt
echo "prelozenych_titulku=$SHOWS pady=$CRASH knihovna_ps5=$LIB dekodovano_snimku=${DECODED:-0} pokusy_o_spojeni=$LINK parovani_bez_konzole=$PAIR aktualizace_stazeno=$UPD_DL aktualizace_dotaz=$UPD_ASK aktualizace_nahrazeno=$UPD_DONE ucet_z_telefonu=$LOGIN_PAGE pomocnik=$HELPER" > $OUT/result.txt
if [ "$SHOWS" -ge 3 ] && [ "$KINDS" -ge 7 ] && [ "$CRASH" -eq 0 ] && [ "$LIB" -ge 1 ] && [ "${DECODED:-0}" -ge 20 ] && [ "$LINK" -ge 1 ] && [ "$PAIR" -ge 1 ]; then
  echo "VYSLEDEK=OK" >> $OUT/result.txt
else
  echo "VYSLEDEK=CHYBA" >> $OUT/result.txt
fi
cat $OUT/kinds.txt $OUT/missing.txt >> $OUT/result.txt 2>/dev/null
grep "TP_TEST" $OUT/log.txt | grep -v "READ|\|TR|\|TRL|" >> $OUT/result.txt
grep "TP_TEST.*TR|" $OUT/log.txt | sed 's/.*TR|//' > $OUT/preklad.txt
adb shell dumpsys package com.google.android.webview 2>/dev/null | grep -m1 versionName >> $OUT/device.txt || true
grep "TP_TEST" $OUT/log.txt > $OUT/reads.txt
tail -c 400000 $OUT/logcat_full.txt > $OUT/logcat_tail.txt; rm $OUT/logcat_full.txt
cat $OUT/result.txt
exit 0
