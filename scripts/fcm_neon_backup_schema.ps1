# Respaldo schema-only de Neon (mycar) antes de V24. No imprime password.
$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $raiz ".env.neon"
Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
    $k, $v = $_.Split('=', 2)
    Set-Item -Path ("Env:" + $k.Trim()) -Value $v.Trim()
}
$url = $env:SPRING_DATASOURCE_URL
if ($url -notmatch 'jdbc:postgresql://([^/:?]+)(?::(\d+))?/([^?]+)') { throw "URL JDBC no reconocida" }
$hostPg = $Matches[1]
$portPg = if ($Matches[2]) { $Matches[2] } else { "5432" }
$dbPg = $Matches[3]
$userPg = $env:SPRING_DATASOURCE_USERNAME
$passPg = $env:SPRING_DATASOURCE_PASSWORD
$outDir = Join-Path $raiz "_tmp_rf_extract\neon_08nov\backups"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outFile = Join-Path $outDir "neon_mycar_schema_$stamp.sql"
$env:PGPASSWORD = $passPg
$env:PGSSLMODE = "require"
try {
    Write-Host "Backup schema-only → $outFile"
    # Neon usa PG 18; el cliente del dump debe ser >= servidor.
    docker run --rm -e PGPASSWORD -e PGSSLMODE -v "${outDir}:/backup" postgres:18-alpine `
        pg_dump -h $hostPg -p $portPg -U $userPg -d $dbPg `
        --schema=mycar --schema-only --no-owner --no-privileges `
        -f "/backup/neon_mycar_schema_$stamp.sql"
    if (!(Test-Path $outFile)) { throw "No se genero el backup" }
    $size = (Get-Item $outFile).Length
    Write-Host "Backup OK bytes=$size (contenido no se muestra)"
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
