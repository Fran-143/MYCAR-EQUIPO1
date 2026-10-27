param([string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git")
$ErrorActionPreference = 'Stop'
$rf18Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $rf18Adb)) { throw 'No encuentro adb en el SDK habitual.' }
Push-Location (Join-Path $Repositorio 'android')
try {
    & .\gradlew.bat :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la compilacion del diseno.' }
    & $rf18Adb -s emulator-5554 install -r '.\app\build\outputs\apk\debug\app-debug.apk'
    if ($LASTEXITCODE -ne 0) { throw 'Abre el emulador y revisa el error de instalacion.' }
    & $rf18Adb -s emulator-5554 shell am start -S -n 'com.mycar.app/.features.historial.ui.HistorialPreviewActivity'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo abrir la vista de diseno RF18.' }
} finally { Pop-Location }
Write-Host 'Vista de diseno DEBUG: usa datos de ejemplo identificados. No llama al backend ni guarda datos.'
Write-Host 'Para volver a la app normal, ejecuta ABRIR_ANDROID_RF18.ps1.'
