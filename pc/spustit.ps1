# Spustí čtečku pro počítač (nejdřív jednou pc\priprava.ps1).
#
#   powershell -File pc\spustit.ps1                  ukázka bez PlayStationu
#   powershell -File pc\spustit.ps1 -Okno chiaki     čte okno, jehož titulek obsahuje „chiaki“
#   -Vystup pc | tv | oboje                          pruh na počítači (výchozí), do televize LG, obojí
#
# Ukončení: Ctrl+C.
param([string]$Okno = "", [string]$Jazyk = "cs", [ValidateSet("pc", "tv", "oboje")][string]$Vystup = "pc")
$slozka = "$env:LOCALAPPDATA\TitulkyPS5"
$model = Get-ChildItem "$slozka\modely\en_$Jazyk" -Directory | Select-Object -First 1
if (-not $model) { throw "Chybí překladový model – nejdřív spusť pc\priprava.ps1 -Jazyk $Jazyk" }
# Java 17: přednost má ta z přípravy, pak Temurin 17. Výchozí java v systému může být starší (8)
# a čtečku by nespustila.
$java = Get-ChildItem "$slozka\jdk-17*", 'C:\Program Files\Eclipse Adoptium\jdk-17*' -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path "$($_.FullName)\bin\java.exe" } | Select-Object -First 1
if (-not $java) { throw "Chybí Java 17 – spusť pc\priprava.ps1" }
$env:JAVA_HOME = $java.FullName
# Java 17 se řídí sun.stdout.encoding (stdout.encoding zná až Java 19); bez toho by
# v okně s českou znakovou sadou byla rozbitá diakritika.
$env:JAVA_OPTS = "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$parametry = @("--python", "$slozka\venv\Scripts\python.exe", "--pomocnik", "$PSScriptRoot\cteni.py",
    "--jazyk", $Jazyk, "--vystup", $Vystup)
if ($Okno) { $parametry += @("--okno", $Okno) }
& "$PSScriptRoot\build\install\recaption-pc\bin\recaption-pc.bat" @parametry
