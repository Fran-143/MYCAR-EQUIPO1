# Readonly: estado Flyway + deps V24 en Neon. No imprime secretos ni aplica DDL.
$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $raiz ".env.neon"
if (!(Test-Path $envFile)) { throw "Falta .env.neon" }

Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
    $k, $v = $_.Split('=', 2)
    Set-Item -Path ("Env:" + $k.Trim()) -Value $v.Trim()
}

$url = $env:SPRING_DATASOURCE_URL
if (-not $url) { throw "SPRING_DATASOURCE_URL ausente" }
# jdbc:postgresql://host:port/db?params → parse
if ($url -notmatch 'jdbc:postgresql://([^/:?]+)(?::(\d+))?/([^?]+)') {
    throw "URL JDBC no reconocida"
}
$hostPg = $Matches[1]
$portPg = if ($Matches[2]) { $Matches[2] } else { "5432" }
$dbPg = $Matches[3]
$userPg = $env:SPRING_DATASOURCE_USERNAME
$passPg = $env:SPRING_DATASOURCE_PASSWORD
if (-not $userPg -or -not $passPg) { throw "Credenciales Neon incompletas" }

$env:PGPASSWORD = $passPg
$env:PGSSLMODE = "require"
try {
    Write-Host "Neon host=$hostPg db=$dbPg (password omitido)"
    $psql = Get-Command psql -ErrorAction SilentlyContinue
    if (-not $psql) {
        # Fallback: docker con cliente psql oficial
        $query = @"
SELECT version, description, success, checksum
FROM mycar.flyway_schema_history
WHERE version IS NOT NULL
ORDER BY installed_rank;
SELECT
  to_regclass('mycar.usuario') AS usuario,
  to_regclass('mycar.alerta') AS alerta,
  to_regclass('mycar.notificacion') AS notificacion,
  to_regclass('mycar.preferencia_notificacion') AS preferencia_notificacion,
  to_regclass('mycar.dispositivo_push') AS dispositivo_push,
  to_regclass('mycar.outbox_push') AS outbox_push;
SELECT COUNT(*) AS tablas_mycar FROM information_schema.tables WHERE table_schema='mycar';
"@
        $query | docker run --rm -i -e PGPASSWORD -e PGSSLMODE postgres:16-alpine `
            psql -h $hostPg -p $portPg -U $userPg -d $dbPg -v ON_ERROR_STOP=1
    } else {
        & psql -h $hostPg -p $portPg -U $userPg -d $dbPg -v ON_ERROR_STOP=1 -c @"
SELECT version, description, success, checksum
FROM mycar.flyway_schema_history
WHERE version IS NOT NULL
ORDER BY installed_rank;
"@
        & psql -h $hostPg -p $portPg -U $userPg -d $dbPg -v ON_ERROR_STOP=1 -c @"
SELECT
  to_regclass('mycar.usuario') AS usuario,
  to_regclass('mycar.alerta') AS alerta,
  to_regclass('mycar.notificacion') AS notificacion,
  to_regclass('mycar.preferencia_notificacion') AS preferencia_notificacion,
  to_regclass('mycar.dispositivo_push') AS dispositivo_push,
  to_regclass('mycar.outbox_push') AS outbox_push;
SELECT COUNT(*) AS tablas_mycar FROM information_schema.tables WHERE table_schema='mycar';
"@
    }
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
