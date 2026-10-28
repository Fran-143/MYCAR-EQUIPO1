param([string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git")
$ErrorActionPreference = 'Stop'
$rf22Sufijo = [Guid]::NewGuid().ToString('N').Substring(0,8)
$rf22Contenedor = 'rf22-pruebas-' + $rf22Sufijo
$rf22Base = 'mycar_mvp_test_rf22_' + $rf22Sufijo
$rf22Variables = @('MYCAR_ENV_FILE','POSTGRES_PASSWORD','MYCAR_RF22_TEST_URL','MYCAR_RF22_TEST_USER','MYCAR_RF22_TEST_PASSWORD')
$rf22Anteriores = @{}
foreach ($nombre in $rf22Variables) { $rf22Anteriores[$nombre] = [Environment]::GetEnvironmentVariable($nombre, 'Process') }
$rf22Creado = $false
$rf22Ubicacion = $false
try {
    $env:POSTGRES_PASSWORD = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    & docker info --format '{{.ServerVersion}}'
    if ($LASTEXITCODE -ne 0) { throw 'Abre Docker Desktop antes de ejecutar estas pruebas.' }
    & docker run --detach --rm --name $rf22Contenedor --label mycar.purpose=rf22-prueba-temporal --publish '127.0.0.1::5432' --env POSTGRES_PASSWORD --env POSTGRES_USER=mycar --env "POSTGRES_DB=$rf22Base" postgres:17
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar el PostgreSQL temporal.' }
    $rf22Creado = $true
    $rf22Listo = $false
    for ($intento = 0; $intento -lt 60; $intento++) {
        & docker exec $rf22Contenedor pg_isready -h 127.0.0.1 -U mycar -d $rf22Base *> $null
        if ($LASTEXITCODE -eq 0) { $rf22Listo = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $rf22Listo) { throw 'PostgreSQL temporal no quedo disponible.' }
    $puerto = & docker port $rf22Contenedor '5432/tcp'
    if ($LASTEXITCODE -ne 0 -or $puerto -notmatch '^127\.0\.0\.1:(\d+)\s*$') { throw 'No se pudo verificar el puerto temporal.' }
    $env:MYCAR_RF22_TEST_URL = 'jdbc:postgresql://127.0.0.1:' + $Matches[1] + '/' + $rf22Base
    $env:MYCAR_RF22_TEST_USER = 'mycar'
    $env:MYCAR_RF22_TEST_PASSWORD = $env:POSTGRES_PASSWORD
    $env:MYCAR_ENV_FILE = 'optional:classpath:rf22-pruebas-sin-env.properties'
    Push-Location (Join-Path $Repositorio 'backend')
    $rf22Ubicacion = $true
    & .\mvnw.cmd '-Dmycar.rf22.tests=true' '-Dtest=backend.alertas.programacion.*Test' test
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas RF22 con PostgreSQL.' }
} finally {
    if ($rf22Ubicacion) { Pop-Location }
    if ($rf22Creado) {
        & docker stop --time 5 $rf22Contenedor | Out-Null
        if ($LASTEXITCODE -eq 0) { Write-Host 'Contenedor temporal RF22 detenido y retirado.' }
        else { Write-Warning "Revisa el contenedor temporal $rf22Contenedor en Docker Desktop." }
    }
    foreach ($nombre in $rf22Variables) { [Environment]::SetEnvironmentVariable($nombre, $rf22Anteriores[$nombre], 'Process') }
}
