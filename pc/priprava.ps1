# Jednorázová příprava čtečky pro počítač (Windows). Nic neinstaluje do systému – vše jde do
# složky %LOCALAPPDATA%\ReCaptionTools (asi 0,6 GB):
#   - Python prostředí s knihovnami pro čtení textu a překlad,
#   - překladový model angličtina → zvolený jazyk (Argos, běží bez internetu).
# Potřebuje nainstalovaný Python 3.10+ a Javu 17 (proměnná JAVA_HOME).
#
#   powershell -File pc\priprava.ps1 [-Jazyk cs]
#   -BezSestaveni   jen knihovny a model (tak ji spouští okno aplikace – sestavená už je)
#   -Python CESTA   Python s knihovnami už je (balíček ke stažení ReCaption-PC.zip) – stáhne se jen model
param([string]$Jazyk = "cs", [switch]$BezSestaveni, [string]$Python = "")
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$slozka = "$env:LOCALAPPDATA\ReCaptionTools"
New-Item -ItemType Directory -Force "$slozka\modely" | Out-Null

if (-not $Python) {
    if (-not (Test-Path "$slozka\venv\Scripts\python.exe")) { python -m venv "$slozka\venv" }
    & "$slozka\venv\Scripts\python.exe" -m pip install -q ctranslate2 subword-nmt sacremoses rapidocr-onnxruntime pillow
}

$model = "$slozka\modely\en_$Jazyk"
if (-not (Test-Path $model)) {
    $seznam = Invoke-RestMethod "https://raw.githubusercontent.com/argosopentech/argospm-index/main/index.json"
    $balik = $seznam | Where-Object { $_.from_code -eq "en" -and $_.to_code -eq $Jazyk } | Select-Object -First 1
    if (-not $balik) { throw "Pro jazyk '$Jazyk' překladový model není." }
    Invoke-WebRequest $balik.links[0] -OutFile "$model.zip"
    Expand-Archive "$model.zip" -DestinationPath $model -Force
    Remove-Item "$model.zip"
}
if ($BezSestaveni) { Write-Host "Hotovo."; exit 0 }
& "$PSScriptRoot\..\gradlew.bat" -p $PSScriptRoot installDist --console=plain -q
Write-Host "Hotovo. Spuštění: powershell -File pc\spustit.ps1 [-Okno chiaki]"
