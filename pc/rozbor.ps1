# Rozbor uložených snímků hry (ladění oblastí titulků a úkolu a menu GTA VI), viz src/Rozbor.kt a CTI-ME.md.
#
#   powershell -File pc\rozbor.ps1 zaznamy\davky-20261121-203000            všechny dávky ve složce
#   powershell -File pc\rozbor.ps1 zaznamy\hra-20261120-175456 -Obrazky     i snímky s rámečky do rozbor\
#   -Hra gta6 (výchozí) · -ZonaUkolu 0.915 (hranice úkolu jako v aplikaci)
#
# Vedle snímků vznikne rozbor.txt a rozbor.csv, u více složek navíc rozbor-souhrn.txt v zadané složce.
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Slozka,
    [string]$Hra = "gta6",
    [string]$ZonaUkolu = "",
    [switch]$Obrazky
)
$ErrorActionPreference = "Stop"
$nastroje = "$env:LOCALAPPDATA\ReCaptionTools"
$python = "$nastroje\venv\Scripts\python.exe"
if (-not (Test-Path $python)) { throw "Chybí Python s RapidOCR – nejdřív pc\priprava.ps1 (nebo Připravit v okně aplikace)." }
$java = Get-ChildItem "$nastroje\jdk-17*", 'C:\Program Files\Eclipse Adoptium\jdk-17*' -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path "$($_.FullName)\bin\java.exe" } | Select-Object -First 1
if (-not $java) { throw "Chybí Java 17 – spusť pc\priprava.ps1" }
$env:JAVA_HOME = $java.FullName
# sestavit, když chybí nebo je kód novější než sestavení (jako aplikace.ps1)
$lib = "$PSScriptRoot\build\install\recaption-pc\lib"
$jar = Get-Item "$lib\recaption-pc.jar" -ErrorAction SilentlyContinue
$zdroje = Get-ChildItem "$PSScriptRoot\src", "$PSScriptRoot\..\app\src\main\java\cz\recaption\core" -Recurse -Filter *.kt |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar -or $zdroje.LastWriteTime -gt $jar.LastWriteTime) {
    Write-Host "Sestavuji ReCaption pro počítač…"
    & "$PSScriptRoot\..\gradlew.bat" -p $PSScriptRoot installDist --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw "Sestavení selhalo." }
}
$env:JAVA_OPTS = "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$parametry = @("--rozbor", (Resolve-Path $Slozka).Path, "--hra", $Hra, "--python", $python, "--pomocnik", "$PSScriptRoot\cteni.py")
if ($ZonaUkolu) { $parametry += @("--zona-ukolu", $ZonaUkolu) }
if ($Obrazky) { $parametry += "--obrazky" }
& "$PSScriptRoot\build\install\recaption-pc\bin\recaption-pc.bat" @parametry
