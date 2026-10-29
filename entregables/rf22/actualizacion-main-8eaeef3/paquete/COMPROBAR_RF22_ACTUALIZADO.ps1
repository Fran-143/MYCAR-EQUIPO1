[CmdletBinding()]
param([string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git")
$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $Repositorio 'backend')
try {
    & .\mvnw.cmd '-Dtest=CalculadorProgramacionTest,ProgramacionAlertaServiceTest,IdentidadSesionVehiculosTest' test
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la comprobacion backend. La integracion local esta guardada; comparte el resultado.' }
} finally { Pop-Location }
Push-Location (Join-Path $Repositorio 'android')
try {
    & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug '-Pmycar.apiBaseUrl=http://10.0.2.2:8081/'
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la comprobacion Android. La integracion local esta guardada; comparte el resultado.' }
} finally { Pop-Location }
Write-Host 'Backend y Android verificados. Para abrir la app ejecuta ABRIR_MYCAR_ACTUALIZADO.ps1.'
