[CmdletBinding()]
param([string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git",[string]$Dispositivo = 'emulator-5554')
$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $Repositorio 'backend')
try {
    & .\mvnw.cmd clean test '-Dtest=ProgramarAlertaServiceReglasTest,HuellaProgramarAlertaTest,AlertasControllerTest,ActualizarKilometrajeControllerTest'
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la comprobacion backend. Comparte el resultado.' }
} finally { Pop-Location }
Push-Location (Join-Path $Repositorio 'android')
try {
    & .\gradlew.bat :app:clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la compilacion o pruebas Android. Comparte el resultado.' }
} finally { Pop-Location }
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$estado = & $adb -s $Dispositivo get-state
if ($LASTEXITCODE -ne 0 -or $estado -ne 'device') { throw 'Enciende el emulador. La compilacion ya termino.' }
& $adb -s $Dispositivo install -r (Join-Path $Repositorio 'android\app\build\outputs\apk\debug\app-debug.apk')
if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar la app.' }
$scriptBackend = Join-Path $Repositorio 'scripts\iniciar_backend_integracion_local.ps1'
Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile','-NoExit','-ExecutionPolicy','Bypass','-File',('"' + $scriptBackend + '"')) -WorkingDirectory $Repositorio | Out-Null
Write-Host 'Backend iniciado en otra ventana. Esperando disponibilidad en 8081...'
$disponible = $false
for ($intento = 0; $intento -lt 30; $intento++) {
    try {
        $health = Invoke-RestMethod -Uri 'http://localhost:8081/actuator/health' -TimeoutSec 2
        if ($health.status -eq 'UP') { $disponible = $true; break }
    } catch {}
    Start-Sleep -Seconds 1
}
if (-not $disponible) { throw 'Spring Boot no respondio a tiempo. Deja su ventana abierta y comparte el error de esa consola.' }
& $adb -s $Dispositivo shell am start -S -n 'com.mycar.app/.MainActivity'
if ($LASTEXITCODE -ne 0) { throw 'No se pudo abrir la app.' }
Write-Host 'Proyecto completo compilado, backend UP y MyCar instalada y abierta.'
Write-Host 'Inicio/Detalle/Mantenimiento: actualizar kilometraje. Alertas: Mis alertas con seis filtros.'
