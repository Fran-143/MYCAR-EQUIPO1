param([string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git", [string]$Dispositivo = 'emulator-5554')
. (Join-Path $PSScriptRoot 'RF22_COMUN.ps1')
Build-Rf22Android $Repositorio
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb -s $Dispositivo install -r (Join-Path $Repositorio 'android\app\build\outputs\apk\debug\app-debug.apk')
if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar la app.' }
& $adb -s $Dispositivo install -r (Join-Path $Repositorio 'android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron instalar las pruebas.' }
$resultado = & $adb -s $Dispositivo shell am instrument -w -e class 'com.mycar.app.features.alertas.programacion.ProgramacionPantallasTest,com.mycar.app.features.alertas.programacion.ArchivoProgramacionTest' 'com.mycar.app.test/androidx.test.runner.AndroidJUnitRunner' 2>&1
$resultado | ForEach-Object { Write-Host $_ }
if ($LASTEXITCODE -ne 0 -or ($resultado -join "`n") -notmatch 'OK \(5 tests\)') { throw 'Las cinco pruebas del emulador no terminaron correctamente.' }
