param([string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git")
. (Join-Path $PSScriptRoot 'RF22_COMUN.ps1')
$repo = Aplicar-Rf22 $Repositorio 'backend'
Push-Location (Join-Path $repo 'backend')
try {
    & .\mvnw.cmd '-Dtest=CalculadorProgramacionTest,ProgramacionAlertaServiceTest' test
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la compilacion o las pruebas unitarias RF22.' }
} finally { Pop-Location }
Write-Host 'Backend compilado y pruebas unitarias completadas. No se inicio Spring ni se aplicaron migraciones a tu BD.'
Write-Host 'Para comprobar PostgreSQL usa PROBAR_POSTGRES_RF22.ps1: crea una base desechable propia.'
Write-Host 'Para el servidor real usa INICIAR_BACKEND_RF22.ps1. Al arrancar, Flyway aplicara V6 si esta pendiente.'
