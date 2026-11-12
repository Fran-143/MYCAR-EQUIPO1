# Aplica SOLO V25 pendiente en Neon via Flyway. Requiere aprobacion explicita.
# Uso: .\scripts\fcm_neon_flyway_v25.ps1 -ConfirmoNeonV25
param(
    [switch]$ConfirmoNeonV25
)
$ErrorActionPreference = "Stop"
if (-not $ConfirmoNeonV25) {
    throw "Abortado: pasa -ConfirmoNeonV25 tras autorizacion."
}
$raiz = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $raiz ".env.neon"
Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
    $k, $v = $_.Split('=', 2)
    Set-Item -Path ("Env:" + $k.Trim()) -Value $v.Trim()
}
Remove-Item Env:MYCAR_ENV_FILE -ErrorAction SilentlyContinue

Set-Location (Join-Path $raiz "backend")
Write-Host "Ejecutando Flyway migrate controlado V25 (una vez)..."
& .\mvnw.cmd -q `
  "org.flywaydb:flyway-maven-plugin:migrate" `
  "-Dflyway.url=$($env:SPRING_DATASOURCE_URL)" `
  "-Dflyway.user=$($env:SPRING_DATASOURCE_USERNAME)" `
  "-Dflyway.password=$($env:SPRING_DATASOURCE_PASSWORD)" `
  "-Dflyway.locations=filesystem:src/main/resources/db/mvp" `
  "-Dflyway.defaultSchema=mycar" `
  "-Dflyway.schemas=mycar" `
  "-Dflyway.baselineOnMigrate=false"

if ($LASTEXITCODE -ne 0) { throw "Flyway migrate fallo (exit $LASTEXITCODE)" }
Write-Host "Flyway migrate OK — historial actualizado por Flyway (sin edits manuales)."
