param([string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git")
$ErrorActionPreference = 'Stop'
$rf18Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $rf18Adb)) { throw 'No encuentro adb en el SDK habitual.' }
Push-Location (Join-Path $Repositorio 'android')
try {
    & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la compilacion de las pruebas.' }
    & $rf18Adb -s emulator-5554 install -r '.\app\build\outputs\apk\debug\app-debug.apk'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar la app en el emulador.' }
    & $rf18Adb -s emulator-5554 install -r '.\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar el APK de pruebas.' }
    $resultado = & $rf18Adb -s emulator-5554 shell am instrument -w -e class com.mycar.app.features.historial.ui.HistorialMantenimientoPantallasTest com.mycar.app.test/androidx.test.runner.AndroidJUnitRunner
    $salida = $LASTEXITCODE
    $resultado | ForEach-Object { Write-Host $_ }
    if ($salida -ne 0 -or (($resultado -join "`n") -notmatch 'OK \(8 tests\)')) {
        throw 'Las ocho pruebas de interfaz no terminaron correctamente. Enviame el resultado.'
    }
    & $rf18Adb -s emulator-5554 shell am start -S -n 'com.mycar.app/.features.historial.ui.HistorialEntrada'
    if ($LASTEXITCODE -ne 0) { throw 'Las pruebas pasaron, pero no se pudo abrir la app normal.' }
} finally { Pop-Location }
