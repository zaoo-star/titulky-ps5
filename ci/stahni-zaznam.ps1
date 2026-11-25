# Stáhne záznam (hlášení o stavu) z ReCaptionu na Androidu v domácí síti.
#
# Na zařízení: Nastavení → Experimentální (úplně dole) → „Zpřístupnit záznam v domácí síti“ → ZAP. Pod řádkem je adresa,
# např. http://192.168.1.23:8789/zaznam?k=ab3k7m (platí 2 hodiny, pak se to samo vypne).
#
#   .\ci\stahni-zaznam.ps1 http://192.168.1.23:8789/zaznam?k=ab3k7m
#   .\ci\stahni-zaznam.ps1 192.168.1.23 ab3k7m            # adresa a kód zvlášť
#   .\ci\stahni-zaznam.ps1 192.168.1.23 ab3k7m -Json      # po oddílech v JSON
#
# Uloží do složky zaznamy\ (v .gitignore – do repozitáře nepatří) jako zaznam-<čas>.txt
# a kopii jako zaznamy\posledni.txt. Klíče a čísla účtu v záznamu nejsou (Redact v aplikaci).
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Adresa,
    [Parameter(Position = 1)][string]$Kod = '',
    [int]$Port = 8789,
    [switch]$Json
)
$ErrorActionPreference = 'Stop'

if ($Adresa -match '^https?://') {
    $url = $Adresa
    if ($Json) { $url = $url -replace '/zaznam(\.txt)?\?', '/zaznam.json?' }
} else {
    if (-not $Kod) { throw 'Chybí kód (je na obrazovce za k=).' }
    $cesta = if ($Json) { 'zaznam.json' } else { 'zaznam' }
    $url = "http://${Adresa}:${Port}/${cesta}?k=$Kod"
}

# jen domácí síť – nic jiného tenhle skript stahovat nemá
$hostPart = ([uri]$url).Host
if ($hostPart -notmatch '^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.|169\.254\.|127\.)') {
    throw "Adresa $hostPart není v domácí síti."
}

$koren = Split-Path -Parent $PSScriptRoot
$slozka = Join-Path $koren 'zaznamy'
New-Item -ItemType Directory -Force $slozka | Out-Null

try {
    $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 30
} catch {
    $kod = $_.Exception.Response.StatusCode.value__
    if ($kod -eq 403) { throw 'Zařízení odmítlo kód (překlep, nebo se mezitím vypnulo a zapnulo s novým kódem).' }
    if ($kod -eq 404) { throw 'Záznam v síti už je vypnutý (vypršely 2 hodiny?). Zapni ho na zařízení znovu.' }
    throw "Zařízení neodpovídá ($url): $($_.Exception.Message). Je na zařízení zapnuté Zpřístupnit záznam v domácí síti a je PC ve stejné síti?"
}

# tělo jako UTF-8 (Windows PowerShell 5 by jinak bral jiné kódování)
$stream = $r.RawContentStream
$stream.Position = 0
$text = (New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)).ReadToEnd()

$pripona = if ($url -match '/zaznam\.json') { 'json' } else { 'txt' }
$soubor = Join-Path $slozka ("zaznam-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + ".$pripona")
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($soubor, $text, $utf8)
[System.IO.File]::WriteAllText((Join-Path $slozka "posledni.$pripona"), $text, $utf8)
Write-Output "Uloženo: $soubor ($($text.Length) znaků)"
