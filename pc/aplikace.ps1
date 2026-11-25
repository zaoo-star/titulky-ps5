# Spustí okno aplikace ReCaption pro počítač (spouští ho ReCaption.cmd dvojklikem).
# Když čtečka ještě není sestavená, sestaví ji (asi minuta, jen poprvé a po změně kódu).
# Knihovny pro čtení a překlad stáhne aplikace sama tlačítkem Připravit na stránce Domů.
$ErrorActionPreference = "Stop"
$slozka = "$env:LOCALAPPDATA\ReCaptionTools"
$java = Get-ChildItem "$slozka\jdk-17*", 'C:\Program Files\Eclipse Adoptium\jdk-17*' -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path "$($_.FullName)\bin\javaw.exe" } | Select-Object -First 1
if (-not $java) {
    Add-Type -AssemblyName PresentationFramework
    [System.Windows.MessageBox]::Show("Chybí Java 17 (Temurin). Nainstaluj ji z adoptium.net a spusť ReCaption znovu.", "ReCaption") | Out-Null
    exit 1
}
$env:JAVA_HOME = $java.FullName
$lib = "$PSScriptRoot\build\install\recaption-pc\lib"
# sestavit, když chybí nebo je kód novější než sestavení
$jar = Get-Item "$lib\recaption-pc.jar" -ErrorAction SilentlyContinue
$zdroje = Get-ChildItem "$PSScriptRoot\src", "$PSScriptRoot\..\app\src\main\java\cz\recaption\core" -Recurse -Filter *.kt |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar -or $zdroje.LastWriteTime -gt $jar.LastWriteTime) {
    Write-Host "Sestavuji ReCaption pro počítač…"
    & "$PSScriptRoot\..\gradlew.bat" -p $PSScriptRoot installDist --console=plain -q
    if ($LASTEXITCODE -ne 0) { Read-Host "Sestavení selhalo – Enter zavře okno"; exit 1 }
}
Start-Process "$($java.FullName)\bin\javaw.exe" -ArgumentList @(
    "-Drecaption.pc=`"$PSScriptRoot`"", "-Dfile.encoding=UTF-8",
    "-cp", "`"$lib\*`"", "cz.recaption.pc.AplikaceKt")
