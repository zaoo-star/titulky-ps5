# Sbírá podklady pro ladění profilu hry, zatímco majitel hraje: snímky obrazovky a záznam
# (titulky s překladem + co aplikace četla v oblasti titulků) z ReCaptionu na Androidu v domácí síti.
#
# Na zařízení: Nastavení → Experimentální → „Zpřístupnit záznam v domácí síti“ → ZAP (platí 2 hodiny).
#
#   .\ci\sbirej-hru.ps1 http://192.168.1.23:8789/zaznam?k=ab3k7m
#   .\ci\sbirej-hru.ps1 192.168.1.23 ab3k7m -Interval 3 -Minut 90
#
# Ukládá do zaznamy\hra-<čas>\ (v .gitignore): snimek-HHmmss.jpg (stejné snímky za sebou se neukládají)
# a zaznam.txt (přepisuje se každou minutu, na konci naposledy). Zastavení: Ctrl+C.
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Adresa,
    [Parameter(Position = 1)][string]$Kod = '',
    [int]$Port = 8789,
    [int]$Interval = 5,
    [int]$Minut = 120
)
$ErrorActionPreference = 'Stop'

if ($Adresa -match '^https?://') {
    $u = [uri]$Adresa
    $hostPart = $u.Host; $Port = $u.Port
    if ($u.Query -match 'k=([A-Za-z0-9]+)') { $Kod = $Matches[1] }
} else { $hostPart = $Adresa }
if (-not $Kod) { throw 'Chybí kód (je na obrazovce za k=).' }
if ($hostPart -notmatch '^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.|169\.254\.|127\.)') {
    throw "Adresa $hostPart není v domácí síti."
}
$zaklad = "http://${hostPart}:${Port}"

$koren = Split-Path -Parent $PSScriptRoot
$slozka = Join-Path $koren ('zaznamy\hra-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force $slozka | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Uloz-Zaznam {
    try {
        $r = Invoke-WebRequest -Uri "$zaklad/zaznam?k=$Kod" -UseBasicParsing -TimeoutSec 30
        $s = $r.RawContentStream; $s.Position = 0
        $text = (New-Object System.IO.StreamReader($s, [System.Text.Encoding]::UTF8)).ReadToEnd()
        [System.IO.File]::WriteAllText((Join-Path $slozka 'zaznam.txt'), $text, $utf8)
        return $true
    } catch {
        $kod = $_.Exception.Response.StatusCode.value__
        if ($kod -eq 403 -or $kod -eq 404) { Write-Output "Záznam v síti je vypnutý nebo kód nesedí ($kod) – končím."; return $false }
        Write-Output "Záznam se nepodařilo stáhnout: $($_.Exception.Message)"
        return $true
    }
}

if (-not (Uloz-Zaznam)) { exit 1 }
Write-Output "Sbírám do $slozka (každých $Interval s, nejvýš $Minut min). Ctrl+C = konec."
$konec = (Get-Date).AddMinutes($Minut)
$posledniHash = ''
$posledniZaznam = Get-Date
$snimku = 0
$odmitnuto = 0
try {
    while ((Get-Date) -lt $konec) {
        try {
            $r = Invoke-WebRequest -Uri "$zaklad/snimek.jpg?k=$Kod" -UseBasicParsing -TimeoutSec 15
            $bytes = $r.Content
            $hash = [System.BitConverter]::ToString((New-Object System.Security.Cryptography.SHA1Managed).ComputeHash($bytes))
            if ($hash -ne $posledniHash) {
                [System.IO.File]::WriteAllBytes((Join-Path $slozka ('snimek-' + (Get-Date -Format 'HHmmss') + '.jpg')), $bytes)
                $posledniHash = $hash; $snimku++
            }
        } catch {
            $kod = $_.Exception.Response.StatusCode.value__
            # 403 přišel i se správným kódem (telefon zhasl displej, 20. 11. 2026) – končí se až po
            # třech za sebou s minutovou pauzou (server se zavře po 30 špatných, tohle ho nevyčerpá)
            if ($kod -eq 403) {
                $odmitnuto++
                if ($odmitnuto -ge 3) { Write-Output 'Kód nesedí – končím.'; break }
                Write-Output ("{0:HH:mm} – zařízení odmítlo kód, zkusím za minutu" -f (Get-Date))
                Start-Sleep -Seconds 60
                continue
            }
            # 404 = obraz zrovna nechodí (překlad neběží) – zkusí se znovu
        }
        $odmitnuto = 0
        if (((Get-Date) - $posledniZaznam).TotalSeconds -ge 60) {
            if (-not (Uloz-Zaznam)) { break }
            $posledniZaznam = Get-Date
            Write-Output ("{0:HH:mm} – snímků: {1}" -f (Get-Date), $snimku)
        }
        Start-Sleep -Seconds $Interval
    }
} finally {
    Uloz-Zaznam | Out-Null
    Write-Output "Hotovo: $snimku snímků a zaznam.txt v $slozka"
}
