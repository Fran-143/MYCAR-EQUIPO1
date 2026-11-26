# Aplica V43+V44 (Catálogos) en Neon SIN ejecutar V42 (correo_simulado local-only).
# 1) Inserta fila Flyway V42 con checksum local (sin DDL).
# 2) flyway migrate target=44 → solo V43/V44.
#
# Uso: .\scripts\neon_flyway_catalogos_v44.ps1 -ConfirmoNeonCatalogos
param(
    [switch]$ConfirmoNeonCatalogos,
    [string]$EnvFile
)
$ErrorActionPreference = "Stop"
if (-not $ConfirmoNeonCatalogos) {
    throw "Abortado: pasa -ConfirmoNeonCatalogos tras backup verificable."
}
$raiz = Split-Path -Parent $PSScriptRoot
if (-not $EnvFile) { $EnvFile = Join-Path $raiz ".env.neon" }
if (!(Test-Path -LiteralPath $EnvFile)) { throw "Falta $EnvFile" }
Get-Content -LiteralPath $EnvFile | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
    $k, $v = $_.Split('=', 2)
    Set-Item -Path ("Env:" + $k.Trim()) -Value $v.Trim()
}
Remove-Item Env:MYCAR_ENV_FILE -ErrorAction SilentlyContinue

$url = $env:SPRING_DATASOURCE_URL
$userPg = $env:SPRING_DATASOURCE_USERNAME
$passPg = $env:SPRING_DATASOURCE_PASSWORD
if (-not $url -or -not $userPg -or -not $passPg) { throw "Credenciales Neon incompletas" }
if ($url -notmatch "neon\.tech") { throw "URL no parece Neon. Abortado." }
if ($url -notmatch 'jdbc:postgresql://([^/:?]+)(?::(\d+))?/([^?]+)') {
    throw "URL JDBC no reconocida"
}
$hostPg = $Matches[1]
$portPg = if ($Matches[2]) { $Matches[2] } else { "5432" }
$dbPg = $Matches[3]

$v42Checksum = (docker exec mycarequipo1-postgres-1 psql -U mycar -d mycar_mvp -t -A -c `
    "SELECT checksum FROM mycar.flyway_schema_history WHERE version='42' AND success LIMIT 1;").Trim()
if (-not $v42Checksum) { throw "No se pudo leer checksum V42 desde Postgres local." }

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$skipSqlHost = Join-Path $env:TEMP "mycar_neon_skip_v42_$stamp.sql"
@"
INSERT INTO mycar.flyway_schema_history (
  installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success
)
SELECT
  COALESCE((SELECT MAX(installed_rank) FROM mycar.flyway_schema_history), 0) + 1,
  '42',
  'correo simulado local test',
  'SQL',
  'V42__correo_simulado_local_test.sql',
  $v42Checksum,
  current_user,
  now(),
  0,
  true
WHERE NOT EXISTS (
  SELECT 1 FROM mycar.flyway_schema_history WHERE version = '42'
);
"@ | Set-Content -Path $skipSqlHost -Encoding UTF8

Write-Host "Neon: marcando V42 skip (sin DDL correo_simulado). Host=$hostPg db=$dbPg"
$env:PGPASSWORD = $passPg
$env:PGSSLMODE = "require"
$tmpDir = Split-Path $skipSqlHost
$skipName = Split-Path $skipSqlHost -Leaf
docker run --rm -e PGPASSWORD -e PGSSLMODE -v "${tmpDir}:/sql" postgres:18-alpine `
    psql -h $hostPg -p $portPg -U $userPg -d $dbPg -v ON_ERROR_STOP=1 -f "/sql/$skipName"
if ($LASTEXITCODE -ne 0) { throw "Falló insert skip V42 en Neon" }
Remove-Item $skipSqlHost -ErrorAction SilentlyContinue

Set-Location (Join-Path $raiz "backend")
Write-Host "Flyway migrate Neon target=44 (V43/V44; V42 ya marcada)."
& .\mvnw.cmd -q `
  "org.flywaydb:flyway-maven-plugin:migrate" `
  "-Dflyway.url=$($env:SPRING_DATASOURCE_URL)" `
  "-Dflyway.user=$($env:SPRING_DATASOURCE_USERNAME)" `
  "-Dflyway.password=$($env:SPRING_DATASOURCE_PASSWORD)" `
  "-Dflyway.locations=filesystem:src/main/resources/db/mvp" `
  "-Dflyway.defaultSchema=mycar" `
  "-Dflyway.schemas=mycar" `
  "-Dflyway.target=44" `
  "-Dflyway.baselineOnMigrate=false"
if ($LASTEXITCODE -ne 0) { throw "Flyway migrate Neon falló" }
Write-Host "Flyway migrate OK (max V44; V42 sin DDL)."
Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
