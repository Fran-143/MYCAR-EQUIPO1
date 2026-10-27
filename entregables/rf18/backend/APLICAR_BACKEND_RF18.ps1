param([string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git")
. (Join-Path $PSScriptRoot 'RF18_COMUN.ps1')
Preparar-RF18 $Repositorio
Aplicar-RF18 $Repositorio $PSScriptRoot 'backend'
Push-Location (Join-Path $Repositorio 'backend')
try {
    & .\mvnw.cmd '-Dtest=HistorialMantenimientoServiceTest,HistorialMantenimientoControllerTest' test
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas RF18. Conserva el resultado y el respaldo.' }
} finally { Pop-Location }
Write-Host 'Backend RF18 aplicado y pruebas unitarias terminadas. Las pruebas PostgreSQL tienen su propio script.'
Write-Host 'No se hicieron commits ni push. La API reutiliza la sesion de Elsa y se activa con mycar.rf18.api-habilitada=true.'
