[CmdletBinding()]
param([string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git",[string]$Dispositivo = 'emulator-5554')
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw 'No encuentro adb en el SDK habitual. Comparte la ubicacion del SDK.' }
$estado = & $adb -s $Dispositivo get-state 2>$null
if ($LASTEXITCODE -ne 0 -or $estado -ne 'device') { throw 'Enciende el emulador Medium Phone y espera que termine de iniciar.' }
$apk = Join-Path $Repositorio 'android\app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw 'Primero ejecuta COMPROBAR_RF22_ACTUALIZADO.ps1.' }
& $adb -s $Dispositivo install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar la app.' }
& $adb -s $Dispositivo shell am start -S -n 'com.mycar.app/.MainActivity'
if ($LASTEXITCODE -ne 0) { throw 'No se pudo abrir la app.' }
Write-Host 'Abierto el inicio real actualizado. Permite la red local si se solicita, inicia sesion y abre Alertas > Programar alerta.'
