# Stáhne dávky ze Záznamu snímků (ReCaption na Androidu, ladění menu / úkolu / titulků) do PC.
#
# Na zařízení:
#   1. ⚙ Nastavení → Experimentální → Ladění a hlášení → „Zpřístupnit záznam v domácí síti“ → ZAP (ukáže IP a kód).
#   2. Spustit překlad, pak tamtéž „Záznam snímků“ → OK = Start; ve hře udělat, co je potřeba (menu, úkol, titulky…);
#      znovu OK = Stop. Každé Start…Stop je samostatná dávka (snímky + index.txt s tím, co aplikace četla).
#      Se zapnutým záznamem v síti jde Start/Stop i tlačítkem „● Snímky“ / „■ Snímky“ v notifikaci.
#
#   .\ci\stahni-davky.ps1 192.168.1.23 ab3k7m             # stáhne dávky, které v PC ještě nejsou
#   .\ci\stahni-davky.ps1 192.168.1.23 ab3k7m -Vse        # všechny znovu
#   .\ci\stahni-davky.ps1 192.168.1.23 ab3k7m -Davka 3    # jen dávku 3
#   .\ci\stahni-davky.ps1 http://192.168.1.23:8789/zaznam?k=ab3k7m   # adresa tak, jak ji ukazuje zařízení
#
# Uloží do zaznamy\davky-<čas>\davka-<n>-<HHmmss>\ (snimek-0001.jpg …, index.txt). Složka zaznamy je v .gitignore.
# Dávku, která se právě nahrává, přeskočí (stáhne se po Stop).
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Adresa,
    [Parameter(Position = 1)][string]$Kod = '',
    [int]$Port = 8789,
    [int]$Davka = 0,
    [switch]$Vse
)
$ErrorActionPreference = 'Stop'

if ($Adresa -match '^https?://') {
    $u = [uri]$Adresa
    $ipAdr = $u.Host
    $Port = $u.Port
    if (-not $Kod) { $Kod = ([regex]::Match($u.Query, '[?&]k=([^&]+)')).Groups[1].Value }
} else {
    $ipAdr = $Adresa
}
if (-not $Kod) { throw 'Chybí kód (je na obrazovce zařízení u „Kód“, v adrese za k=).' }

# jen domácí síť – nic jiného tenhle skript stahovat nemá
if ($ipAdr -notmatch '^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.|169\.254\.|127\.)') {
    throw "Adresa $ipAdr není v domácí síti."
}
$zaklad = "http://${ipAdr}:${Port}"

function Chyba($e, $co) {
    $kod = $null
    try { $kod = $e.Exception.Response.StatusCode.value__ } catch {}
    if ($kod -eq 403) { throw 'Zařízení odmítlo kód (překlep, nebo se záznam v síti vypnul a zapnul s novým kódem).' }
    if ($kod -eq 404) { throw "Nenalezeno ($co) – záznam v síti je vypnutý (vypršely 2 hodiny?), nebo zařízení má starší ReCaption bez Záznamu snímků." }
    throw "Zařízení neodpovídá ($co): $($e.Exception.Message). Je zapnuté Zpřístupnit záznam v domácí síti a je PC ve stejné síti?"
}

try {
    $r = Invoke-WebRequest -Uri "$zaklad/davky?k=$Kod" -UseBasicParsing -TimeoutSec 30
} catch { Chyba $_ 'seznam dávek' }
$stream = $r.RawContentStream
$stream.Position = 0
$json = (New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)).ReadToEnd() | ConvertFrom-Json
$davky = @($json.davky)
if ($davky.Count -eq 0) { Write-Output 'Na zařízení nejsou žádné dávky (Záznam snímků → Start … Stop).'; return }

$koren = Split-Path -Parent $PSScriptRoot
$zaznamy = Join-Path $koren 'zaznamy'
New-Item -ItemType Directory -Force $zaznamy | Out-Null

# co už v PC je: složky davka-… ve všech zaznamy\davky-*, se stejným počtem snímků
$uz = @{}
Get-ChildItem -Path $zaznamy -Directory -Filter 'davky-*' -ErrorAction SilentlyContinue | ForEach-Object {
    Get-ChildItem -Path $_.FullName -Directory -Filter 'davka-*' | ForEach-Object {
        $uz[$_.Name] = @(Get-ChildItem -Path $_.FullName -Filter 'snimek-*.jpg').Count
    }
}

$cil = Join-Path $zaznamy ('davky-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$stazeno = 0
foreach ($d in $davky) {
    if ($Davka -gt 0 -and $d.n -ne $Davka) { continue }
    # název přichází ze zařízení v síti – do cesty jen přesný tvar dávky
    if ([string]$d.nazev -notmatch '^davka-\d{1,6}-\d{6}$') { Write-Output "Přeskočeno: neplatný název dávky."; continue }
    if ($d.nahrava) { Write-Output "$($d.nazev): právě se nahrává – přeskočeno (stáhni po Stop)."; continue }
    if (-not $Vse -and $Davka -eq 0 -and $uz.ContainsKey($d.nazev) -and $uz[$d.nazev] -eq $d.snimku) {
        Write-Output "$($d.nazev): už je v PC."; continue
    }
    New-Item -ItemType Directory -Force $cil | Out-Null
    $zip = Join-Path $cil "$($d.nazev).zip"
    try {
        Invoke-WebRequest -Uri "$zaklad/davka?k=$Kod&n=$($d.n)" -UseBasicParsing -TimeoutSec 600 -OutFile $zip
    } catch { Chyba $_ $d.nazev }
    $slozka = Join-Path $cil $d.nazev
    Expand-Archive -Path $zip -DestinationPath $slozka -Force
    Remove-Item $zip
    $n = @(Get-ChildItem -Path $slozka -Filter 'snimek-*.jpg').Count
    Write-Output "$($d.nazev): $n snímků ($($d.zacatek) – $($d.konec)) → $slozka"
    $stazeno++
}
if ($stazeno -eq 0) { Write-Output 'Nic nového ke stažení.' } else { Write-Output "Hotovo: $stazeno dávek v $cil (popis snímků v index.txt)." }
