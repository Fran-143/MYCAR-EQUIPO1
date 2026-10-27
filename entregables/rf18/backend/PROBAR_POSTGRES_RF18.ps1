param([string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git")
$ErrorActionPreference = 'Stop'
$rf18Sufijo = [Guid]::NewGuid().ToString('N').Substring(0,8)
$rf18Contenedor = 'rf18-pruebas-' + $rf18Sufijo
$rf18Base = 'mycar_mvp_test_rf18_' + $rf18Sufijo
$rf18Variables = @('POSTGRES_PASSWORD','MYCAR_CATALOGO_TEST_URL','MYCAR_CATALOGO_TEST_USER','MYCAR_CATALOGO_TEST_PASSWORD')
$rf18Anteriores = @{}
foreach ($nombre in $rf18Variables) { $rf18Anteriores[$nombre] = [Environment]::GetEnvironmentVariable($nombre, 'Process') }
$rf18Creado = $false
$rf18Ubicacion = $false
try {
    $env:POSTGRES_PASSWORD = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    & docker info --format '{{.ServerVersion}}'
    if ($LASTEXITCODE -ne 0) { throw 'Abre Docker Desktop antes de ejecutar estas pruebas.' }
    & docker run --detach --rm --name $rf18Contenedor --label mycar.purpose=rf18-prueba-temporal --publish '127.0.0.1::5432' --env POSTGRES_PASSWORD --env POSTGRES_USER=mycar --env "POSTGRES_DB=$rf18Base" postgres:17
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar el PostgreSQL temporal.' }
    $rf18Creado = $true
    $rf18Listo = $false
    for ($intento = 0; $intento -lt 60; $intento++) {
        & docker exec $rf18Contenedor pg_isready -h 127.0.0.1 -U mycar -d $rf18Base *> $null
        if ($LASTEXITCODE -eq 0) { $rf18Listo = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $rf18Listo) { throw 'PostgreSQL temporal no quedo disponible.' }
    $puerto = & docker port $rf18Contenedor '5432/tcp'
    if ($LASTEXITCODE -ne 0 -or $puerto -notmatch '^127\.0\.0\.1:(\d+)\s*$') { throw 'No se pudo verificar el puerto temporal.' }
    $env:MYCAR_CATALOGO_TEST_URL = 'jdbc:postgresql://127.0.0.1:' + $Matches[1] + '/' + $rf18Base
    $env:MYCAR_CATALOGO_TEST_USER = 'mycar'
    $env:MYCAR_CATALOGO_TEST_PASSWORD = $env:POSTGRES_PASSWORD
    Push-Location (Join-Path $Repositorio 'backend')
    $rf18Ubicacion = $true
    & .\mvnw.cmd '-Dmycar.rf18.tests=true' '-Dtest=backend.historial.**.*Test' test
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas RF18 con PostgreSQL.' }
} finally {
    if ($rf18Ubicacion) { Pop-Location }
    if ($rf18Creado) {
        & docker stop --time 5 $rf18Contenedor | Out-Null
        if ($LASTEXITCODE -eq 0) { Write-Host 'Contenedor temporal RF18 detenido y retirado.' }
        else { Write-Warning "No se pudo detener $rf18Contenedor. Revisa ese contenedor temporal en Docker Desktop." }
    }
    foreach ($nombre in $rf18Variables) { [Environment]::SetEnvironmentVariable($nombre, $rf18Anteriores[$nombre], 'Process') }
}
