#!/bin/bash
# Automatická zkouška v emulátoru TELEFONU (vedle zkoušky na Android TV v emulator-test.sh):
# pomocník pro přihlášení, celá aplikace v telefonu v několika velikostech displeje a písma,
# na výšku i na šířku, a překlad ukázkové hry. Snímky jsou k prohlédnutí ve větvi s výsledky.
set -x
OUT=test-results
mkdir -p $OUT
PKG=cz.recaption
APK=${APK:-TestPreklad-emulator.apk}
LINK="recaption://login?h=10.0.2.15&p=8765&k=abcd2345&l=cs"
# Žádný příkaz pro emulátor nesmí zkoušku zablokovat: emulátor Androidu 14 občas přestane
# odpovídat (výpis záznamu po aktualizaci, stisk klávesy) a celá zkouška pak visela až do limitu.
ADB_BIN=$(command -v adb)
adb() { timeout 180 "$ADB_BIN" "$@"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
key() { adb shell input keyevent "$1"; sleep 1; }
# velikost displeje, hustota a velikost písma: "šířka x výška  hustota  písmo  název"
MODES=("720x1280 320 1.0 maly" "1080x2400 420 1.0 bezny" "1080x2400 420 1.3 velke_pismo" "1440x3120 560 1.0 velky" "2400x1080 420 1.0 nasirku" "1600x2560 320 1.0 tablet")
mode() { adb shell wm size "$1"; adb shell wm density "$2"; adb shell settings put system font_scale "$3"; sleep 3; }
# Emulátor může během zkoušky spadnout celý (ne aplikace). Bez téhle kontroly by zbytek zkoušky
# posílal příkazy do prázdna a výsledkem by byly samé nuly, ze kterých se nedá poznat, co se stalo.
# Volá se na začátku každé části: když emulátor neodpovídá, zkouška hned skončí s jasnou zprávou.
zije() {
  [ "$(adb get-state 2>/dev/null)" = "device" ] && return 0
  { echo "prelozenych_titulku=0 ruznych_titulku=0/15 pady=0 dekodovano_snimku=0 pomocnik=0 odkaz_z_televize=0 cela_aplikace=0"
    echo "VYSLEDEK=CHYBA"
    echo "EMULATOR SPADL: přestal odpovídat před částí „$1“ (chyba emulátoru, ne aplikace)"; } > $OUT/result.txt
  cat $OUT/result.txt
  exit 0
}

adb install -r "$APK" || { echo "INSTALL FAILED" > $OUT/result.txt; exit 1; }
{ adb shell getprop ro.build.version.release; adb shell getprop ro.product.model; adb shell wm size; adb shell wm density
  echo "mobilni_sit=$(adb shell pm list features | grep -c telephony)"; } > $OUT/device.txt
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb logcat -c
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')

# 1) první spuštění: v telefonu se má otevřít pomocník, ne aplikace pro televizi
adb shell am start -n $PKG/.MainActivity
sleep 10; shot p01_prvni_spusteni
# 2) odkaz ze stránky televize: pomocník má hlásit, že televize čeká na účet
adb shell "am start -n $PKG/.PhoneHelperActivity -a android.intent.action.VIEW -d '$LINK'"
sleep 4; shot p02_odkaz_z_televize
# 3) přihlašovací stránka Sony uvnitř pomocníka
# Jen do Androidu 13: emulátor Androidu 14 (na GitHubu bez grafické karty) při vykreslení té stránky
# spadne celý – dvakrát ze dvou běhů, vždy na snímku p03. Přihlášení u Sony emulátor stejně
# neověří (to umí jen skutečný telefon); na Androidu 11 se krok zkouší dál.
if [ "${SDK:-0}" -lt 34 ]; then
  adb shell "am start -n $PKG/.PhoneHelperActivity -a android.intent.action.VIEW -d '$LINK' --ez helperlogin true"
  sleep 25; shot p03_prihlaseni_sony
  key 4; key 4; key 4
fi

# 4) pomocník v různých velikostech displeje a písma
zije "pomocník v různých velikostech"
for m in "${MODES[@]}"; do
  set -- $m
  adb shell am force-stop $PKG
  mode $1 $2 $3
  adb shell "am start -n $PKG/.PhoneHelperActivity -a android.intent.action.VIEW -d '$LINK'"
  sleep 5; shot "p1_pomocnik_$4"
done
mode 1080x2400 420 1.0

# 5) celá aplikace v telefonu (režim pro televizi LG): průvodce a všechny stránky v týchž velikostech
zije "celá aplikace v telefonu"
adb shell am force-stop $PKG
adb shell am start -n $PKG/.PhoneHelperActivity --ez fullapp true
sleep 8; shot p20_cela_aplikace_pruvodce
for m in "${MODES[@]}"; do
  set -- $m
  adb shell am force-stop $PKG
  mode $1 $2 $3
  # průvodce: jazyky (0), televize (2), konzole (3) a kvalita překladu (5) – volby s popisem musí být celé vidět
  # (zařízení (1) telefon od 1.00 přeskakuje – `autoStep` v MainActivity)
  for step in 0 2 3 5; do
    adb shell am start -n $PKG/.MainActivity --ei wizard $step
    sleep 3; shot "p2_${4}_pruvodce_$step"
  done
  # stránky v pořadí menu (Nastavení s ozubeným kolem je poslední a obsahuje i licenci)
  for page in HOME PS5 LOOK HELP SETTINGS; do
    adb shell am start -n $PKG/.MainActivity --es page $page
    sleep 3; shot "p2_${4}_$page"
  done
  for step in 1 3; do
    adb shell am start -n $PKG/.MainActivity --ez phonelogin true --ei pairstep $step
    sleep 3; shot "p2_${4}_parovani_$step"
  done
  # průvodce TV Samsung (Apps2Samsung): kroky, adresa telefonu velkým písmem, tlačítka
  adb shell am start -n $PKG/.MainActivity --es open samsung
  sleep 5; shot "p2_${4}_samsung"
  key 4; sleep 1
done
adb shell am force-stop $PKG
adb shell wm size reset; adb shell wm density reset; adb shell settings put system font_scale 1.0; sleep 3

# 6) překlad v telefonu: ukázková hra jde do aplikace jako video, má se přečíst a přeložit
zije "překlad ukázkové hry"
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.TitulkyAccessibility
adb shell settings put secure accessibility_enabled 1
sleep 4
adb shell am start -n $PKG/.MainActivity --ez autostart true
sleep 15; shot p30_preklad_spusten
# jazykový balíček pro překlad se stahuje od Googlu – počkat, až je (nejvýš 3 minuty; aplikace po
# neúspěchu zkouší stáhnout znovu sama). Bez něj by se ukázková hra jen četla a nepřekládala.
for i in $(seq 1 60); do adb logcat -d | grep -q "TP_TEST.*MODEL|[a-z]*|ok" && break; sleep 3; done
adb logcat -d | grep "TP_TEST.*MODEL|" | tail -3 > $OUT/model.txt
adb shell am start -n $PKG/.TestCardActivity
for i in 01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40; do
  sleep 3; case $i in 05|15|25|35) shot "p31_hra_$i";; esac
done
key 4; sleep 2
adb shell am force-stop $PKG

zije "vyhodnocení"
adb logcat -d -v time > $OUT/logcat_full.txt
grep -E "TP_TEST|TestPreklad|AndroidRuntime|FATAL|libchiaki|DEBUG   :" $OUT/logcat_full.txt > $OUT/log.txt
grep -n -B8 -A70 -E "Fatal signal|Process: cz.recaption" $OUT/logcat_full.txt | head -400 > $OUT/crash.txt || true
SHOWS=$(grep -c "TP_TEST.*SHOW|" $OUT/log.txt)
CRASH=$(grep -cE "Process: cz.recaption|Fatal signal.*cz.recaption" $OUT/logcat_full.txt)
DECODED=$(grep "TP_TEST.*VIDEO|" $OUT/log.txt | grep -oE "decoded=[0-9]+" | cut -d= -f2 | sort -n | tail -1)
HELPER=$(grep -c "TP_TEST.*HELPER|start" $OUT/log.txt)
LINKS=$(grep -c "TP_TEST.*HELPER|link ok" $OUT/log.txt)
LG=$(grep -c "TP_TEST.*HELPER|lg mode" $OUT/log.txt)
KINDS=0
for k in "We need money" "reckon" "ride at dawn" "keep going like this" "right way" "no idea" "GET OUT OF HERE" "|Wait|" "never supposed to meet" "left door" "voice down" "been here for years" "lantern" "bridge is out" "Run for the gate"; do
  grep -qF -- "$k" <(grep "TP_TEST.*SHOW|" $OUT/log.txt) && KINDS=$((KINDS+1)) || echo "NEPRELOZENO: $k" >> $OUT/missing.txt
done
echo "prelozenych_titulku=$SHOWS ruznych_titulku=$KINDS/15 pady=$CRASH dekodovano_snimku=${DECODED:-0} pomocnik=$HELPER odkaz_z_televize=$LINKS cela_aplikace=$LG" > $OUT/result.txt
if [ "$SHOWS" -ge 3 ] && [ "$KINDS" -ge 7 ] && [ "$CRASH" -eq 0 ] && [ "${DECODED:-0}" -ge 20 ] && [ "$HELPER" -ge 1 ] && [ "$LINKS" -ge 1 ] && [ "$LG" -ge 1 ]; then
  echo "VYSLEDEK=OK" >> $OUT/result.txt
else
  echo "VYSLEDEK=CHYBA" >> $OUT/result.txt
fi
cat $OUT/missing.txt >> $OUT/result.txt 2>/dev/null
grep "TP_TEST" $OUT/log.txt | grep -v "READ|" >> $OUT/result.txt
tail -c 300000 $OUT/logcat_full.txt > $OUT/logcat_tail.txt; rm $OUT/logcat_full.txt
cat $OUT/result.txt | head -40
exit 0
