# Respaldo COMPLETO (schema + datos) del schema mycar en Neon.
# Solo lectura/export. No altera Neon. No imprime passwords ni filas PII.
# Destino: %LOCALAPPDATA%\MyCar\neon-backups\ (fuera del repo / no Git).
# Uso: .\scripts\neon_backup_full.ps1
param(
    [string]$EnvFile
)
$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
if (-not $EnvFile) { $EnvFile = Join-Path $raiz ".env.neon" }
if (!(Test-Path -LiteralPath $EnvFile)) {
    throw "Falta $EnvFile (gitignored). Copia desde el repo principal o usa solicitar_password_neon.ps1"
}
Get-Content -LiteralPath $EnvFile | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
    $k, $v = $_.Split('=', 2)
    Set-Item -Path ("Env:" + $k.Trim()) -Value $v.Trim()
}
$url = $env:SPRING_DATASOURCE_URL
if ($url -notmatch 'jdbc:postgresql://([^/:?]+)(?::(\d+))?/([^?]+)') {
    throw "URL JDBC no reconocida"
}
$hostPg = $Matches[1]
$portPg = if ($Matches[2]) { $Matches[2] } else { "5432" }
$dbPg = $Matches[3]
$userPg = $env:SPRING_DATASOURCE_USERNAME
$passPg = $env:SPRING_DATASOURCE_PASSWORD
if (-not $userPg -or -not $passPg) { throw "Credenciales Neon incompletas" }

$outDir = Join-Path $env:LOCALAPPDATA "MyCar\neon-backups"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$schemaFile = Join-Path $outDir "neon_mycar_schema_$stamp.sql"
$dataFile = Join-Path $outDir "neon_mycar_full_$stamp.sql"
$metaFile = Join-Path $outDir "neon_mycar_full_$stamp.meta.txt"
$env:PGPASSWORD = $passPg
$env:PGSSLMODE = "require"
try {
    Write-Host "Host=$hostPg db=$dbPg (password omitido)"
    Write-Host "1/2 Schema-only → (ruta bajo LOCALAPPDATA, no se imprime contenido)"
    docker run --rm -e PGPASSWORD -e PGSSLMODE -v "${outDir}:/backup" postgres:18-alpine `
        pg_dump -h $hostPg -p $portPg -U $userPg -d $dbPg `
        --schema=mycar --schema-only --no-owner --no-privileges `
        -f "/backup/neon_mycar_schema_$stamp.sql"
    if (!(Test-Path $schemaFile)) { throw "No se genero schema dump" }

    Write-Host "2/2 Full schema+data → (ruta bajo LOCALAPPDATA)"
    docker run --rm -e PGPASSWORD -e PGSSLMODE -v "${outDir}:/backup" postgres:18-alpine `
        pg_dump -h $hostPg -p $portPg -U $userPg -d $dbPg `
        --schema=mycar --no-owner --no-privileges `
        -f "/backup/neon_mycar_full_$stamp.sql"
    if (!(Test-Path $dataFile)) { throw "No se genero full dump" }

    $schemaBytes = (Get-Item $schemaFile).Length
    $dataBytes = (Get-Item $dataFile).Length
    @(
        "stamp=$stamp"
        "host=$hostPg"
        "db=$dbPg"
        "schema_file=$schemaFile"
        "schema_bytes=$schemaBytes"
        "full_file=$dataFile"
        "full_bytes=$dataBytes"
        "note=Restore: psql into empty DB then CREATE SCHEMA IF needed; see docs/PLAN_V26_NEON_RENDER.md"
        "created_utc=$((Get-Date).ToUniversalTime().ToString('o'))"
    ) | Set-Content -Encoding utf8 $metaFile

    Write-Host "Backup OK schema_bytes=$schemaBytes full_bytes=$dataBytes meta=$metaFile"
    Write-Host "STAMP=$stamp"
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
