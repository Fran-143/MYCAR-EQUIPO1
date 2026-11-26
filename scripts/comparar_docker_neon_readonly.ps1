[CmdletBinding()]
param()
$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $raiz "_tmp_rf_extract\neon_08nov"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Invoke-DockerSql([string]$Sql) {
    docker exec mycarequipo1-postgres-1 psql -U mycar -d mycar_mvp -v ON_ERROR_STOP=1 -At -c $Sql
}

Write-Host "=== DOCKER (readonly) ==="
$dockerTables = Invoke-DockerSql "SELECT count(*) FROM information_schema.tables WHERE table_schema='mycar' AND table_type='BASE TABLE';"
$dockerFlyway = Invoke-DockerSql "SELECT count(*) FROM mycar.flyway_schema_history;"
$dockerVersions = Invoke-DockerSql "SELECT coalesce(version,'(schema)') FROM mycar.flyway_schema_history ORDER BY installed_rank;"
$dockerCounts = Invoke-DockerSql @"
SELECT c.relname || '=' || (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from mycar.%I', c.relname), false, true, '')))[1]::text
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='mycar' AND c.relkind='r' ORDER BY 1;
"@
@"
docker_tablas=$dockerTables
docker_flyway_rows=$dockerFlyway
docker_versions:
$($dockerVersions -join "`n")
docker_counts:
$($dockerCounts -join "`n")
"@ | Set-Content -Encoding utf8 (Join-Path $outDir "docker_snapshot.txt")
Write-Host "Docker snapshot -> docker_snapshot.txt (tablas=$dockerTables flyway=$dockerFlyway)"

$envNeon = Join-Path $raiz ".env.neon"
$neonStatusPath = Join-Path $outDir "neon_snapshot_status.txt"
if (!(Test-Path $envNeon)) {
    @"
status=BLOCKED
reason=missing_.env.neon
hint=Copy .env.neon.example to .env.neon and set SPRING_DATASOURCE_PASSWORD on disk.
"@ | Set-Content -Encoding utf8 $neonStatusPath
    Write-Host "Neon: BLOCKED (falta .env.neon)"
    exit 0
}

# Cargar .env.neon sin imprimir secretos
Get-Content -LiteralPath $envNeon | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
    $k, $v = $_.Split('=', 2)
    Set-Item -Path ("Env:" + $k.Trim()) -Value $v.Trim()
}
if (-not $env:SPRING_DATASOURCE_PASSWORD) {
    @"
status=BLOCKED
reason=empty_SPRING_DATASOURCE_PASSWORD
"@ | Set-Content -Encoding utf8 $neonStatusPath
    Write-Host "Neon: BLOCKED (password vacio)"
    exit 0
}

# Comparacion via contenedor Postgres client + JDBC URL parseada con python/psql si hay red
$url = $env:SPRING_DATASOURCE_URL
if ($url -notmatch 'jdbc:postgresql://([^:/?]+):?(\d+)?/([^?]+)') {
    throw "SPRING_DATASOURCE_URL no parseable"
}
$hostNeon = $Matches[1]
$portNeon = if ($Matches[2]) { $Matches[2] } else { "5432" }
$dbNeon = $Matches[3]
$userNeon = $env:SPRING_DATASOURCE_USERNAME

Write-Host "Neon host=$hostNeon db=$dbNeon user=$userNeon (password no se imprime)"

# Usar docker con imagen postgres para psql cliente SSL
$sqlAll = @"
SELECT 'META' || chr(9) || count(*)::text FROM information_schema.tables WHERE table_schema='mycar' AND table_type='BASE TABLE';
SELECT 'FLYWAY' || chr(9) || count(*)::text FROM mycar.flyway_schema_history;
SELECT 'VER' || chr(9) || coalesce(version,'(schema)') FROM mycar.flyway_schema_history ORDER BY installed_rank;
SELECT 'CNT' || chr(9) || c.relname || '=' || (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from mycar.%I', c.relname), false, true, '')))[1]::text
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='mycar' AND c.relkind='r' ORDER BY c.relname;
"@
$tmpSql = Join-Path $outDir "_neon_ro.sql"
Set-Content -LiteralPath $tmpSql -Value $sqlAll -Encoding utf8

$env:PGPASSWORD = $env:SPRING_DATASOURCE_PASSWORD
try {
    $raw = docker run --rm -e PGPASSWORD -v "${outDir}:/out" postgres:17 `
        psql "host=$hostNeon port=$portNeon dbname=$dbNeon user=$userNeon sslmode=require" `
        -v ON_ERROR_STOP=1 -At -f /out/_neon_ro.sql 2>&1
    $code = $LASTEXITCODE
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    if (Test-Path $tmpSql) { Remove-Item $tmpSql -Force }
}

if ($code -ne 0) {
    @"
status=ERROR
exit_code=$code
detail=connection_or_query_failed_see_agent_log_no_password_echoed
"@ | Set-Content -Encoding utf8 $neonStatusPath
    Write-Host "Neon: ERROR (exit $code)"
    # No volcar raw si pudiera contener secretos; solo longitud
    Write-Host "raw_output_chars=$($raw.ToString().Length)"
    exit 1
}

$rawText = ($raw | Out-String)
$rawText | Set-Content -Encoding utf8 (Join-Path $outDir "neon_snapshot.txt")
@"
status=OK
host=$hostNeon
db=$dbNeon
"@ | Set-Content -Encoding utf8 $neonStatusPath
Write-Host "Neon snapshot OK -> neon_snapshot.txt"
