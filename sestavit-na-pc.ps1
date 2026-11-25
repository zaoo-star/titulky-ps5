# Sestavení APK na počítači s Windows (bez Android Studia), podepsané stálým klíčem.
#
#   .\sestavit-na-pc.ps1                  sestaví APK pro televize a telefony (ARM)
#   .\sestavit-na-pc.ps1 -Instalovat      a nahraje ho do zařízení připojeného přes adb
#
# Klíč a heslo leží mimo repozitář v %USERPROFILE%\.recaption\klic (recaption.p12, heslo.txt).
# Bez nich se APK podepíše dočasným klíčem a nepůjde nainstalovat přes vydanou verzi.
# Nativní část (spojení s PlayStationem) se nepřekládá, ale bere z posledního vydaného APK.
# Výsledek: ReCaption-pc.apk v kořeni repozitáře (necommituje se).

param([switch]$Instalovat)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = (Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory |
        Where-Object Name -like 'jdk-17*' | Select-Object -First 1).FullName
}
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }
# Cesty z registru, aby se našly i nástroje nainstalované po otevření okna (protoc, Python).
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;" +
    [Environment]::GetEnvironmentVariable('Path', 'User') + ';' +
    [Environment]::GetEnvironmentVariable('Path', 'Machine')

$klic = "$env:USERPROFILE\.recaption\klic"
if ((Test-Path "$klic\recaption.p12") -and (Test-Path "$klic\heslo.txt")) {
    $env:RECAPTION_KEY_FILE = "$klic\recaption.p12"
    $env:RECAPTION_KEY_PASSWORD = (Get-Content "$klic\heslo.txt" -Raw).Trim()
} else {
    Write-Warning "Stálý klíč nenalezen v $klic – APK bude podepsané dočasným klíčem."
}

# Nativní část (spojení s PlayStationem) se na Windows přeložit nedá, bere se hotová z vydání
# „latest“ na GitHubu. Stahuje se jen jednou pro každé vydání.
$nativni = "$env:USERPROFILE\.recaption\nativni"
$stamp = "$nativni\vydani.txt"
$vydani = (& gh release view latest --json publishedAt --jq .publishedAt)
if (-not (Test-Path $stamp) -or (Get-Content $stamp -Raw).Trim() -ne $vydani) {
    Write-Host "Stahuji nativní část z vydání latest ($vydani)…"
    Remove-Item -Recurse -Force $nativni -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force "$nativni\apk" | Out-Null
    & gh release download latest -p TestPreklad.apk -D "$nativni\apk" --clobber
    if ($LASTEXITCODE -ne 0) { throw "Stažení vydaného APK selhalo." }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead("$nativni\apk\TestPreklad.apk")
    try {
        # jen naše nativní část; knihovny ML Kit přidá sestavení samo ze svých závislostí
        $nase = 'libchiaki-jni.so', 'libc++_shared.so'
        foreach ($e in $zip.Entries | Where-Object { $_.FullName -like 'lib/*/*.so' -and $nase -contains $_.Name }) {
            $cil = Join-Path "$nativni\lib" ($e.FullName.Substring(4) -replace '/', '\')
            New-Item -ItemType Directory -Force (Split-Path $cil) | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($e, $cil, $true)
        }
    } finally { $zip.Dispose() }
    Remove-Item -Recurse -Force "$nativni\apk"
    Set-Content $stamp $vydani
}
& git fetch -q origin tag latest --force 2>$null
& git diff --quiet latest -- app/src/main/cpp app/src/main/java/com/metallic third_party 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Nativní část se od vydání latest změnila – APK z PC ji neobsahuje. Ověř to sestavením na GitHubu."
}

try {
    & .\gradlew.bat assembleRelease "-PnativniZ=$nativni\lib"
    if ($LASTEXITCODE -ne 0) { throw "Sestavení selhalo." }
} finally {
    Remove-Item Env:RECAPTION_KEY_PASSWORD -ErrorAction SilentlyContinue
}

Copy-Item app\build\outputs\apk\release\app-release.apk ReCaption-pc.apk -Force
$otisk = & "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --print-certs ReCaption-pc.apk |
    Select-String 'SHA-256' | Select-Object -First 1
$staly = '9cc0a5e6719dbe63d87bf893452e6f1205dda322d2a027f7508acf96ee38c317'
if ("$otisk" -match $staly) { Write-Host "Podpis: stálý klíč" } else { Write-Warning "Podpis: dočasný klíč" }
Write-Host "Hotovo: $PSScriptRoot\ReCaption-pc.apk"

if ($Instalovat) {
    & adb install -r ReCaption-pc.apk
    if ($LASTEXITCODE -ne 0) { throw "Instalace přes adb selhala." }
}
