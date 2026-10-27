& {
    $ErrorActionPreference = "Stop"
    Set-Location "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git\android"
    $rf17Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (-not (Test-Path $rf17Adb)) { throw "No encuentro adb." }
    .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw "Fallo la compilacion de la app o de las pruebas." }
    & $rf17Adb -s emulator-5554 install -r ".\app\build\outputs\apk\debug\app-debug.apk"
    if ($LASTEXITCODE -ne 0) { throw "Fallo la instalacion de la app." }
    & $rf17Adb -s emulator-5554 install -r ".\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
    if ($LASTEXITCODE -ne 0) { throw "Fallo la instalacion de las pruebas." }
    $rf17Salida = & $rf17Adb -s emulator-5554 shell am instrument -w -e class com.mycar.app.features.mantenimiento.ui.RegistroMantenimientoPantallasTest com.mycar.app.test/androidx.test.runner.AndroidJUnitRunner
    $rf17Codigo = $LASTEXITCODE
    $rf17Salida | ForEach-Object { Write-Host $_ }
    if ($rf17Codigo -ne 0 -or (($rf17Salida -join "`n") -notmatch 'OK \(4 tests\)')) {
        throw "No se confirmaron las cuatro pruebas. Enviame la salida completa."
    }
    & $rf17Adb -s emulator-5554 shell am start -S -n "com.mycar.app/.MainActivity"
}
