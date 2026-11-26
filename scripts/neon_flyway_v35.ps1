# Aplica migraciones pendientes hasta V35 (TIVE documental sin motor/serie) en Neon via Flyway.
# Requiere aprobacion explicita y backup verificable previo.
# Uso: .\scripts\neon_flyway_v35.ps1 -ConfirmoNeonV35
param(
    [switch]$ConfirmoNeonV35,
    [string]$EnvFile
)
$ErrorActionPreference = "Stop"
if (-not $ConfirmoNeonV35) {
    throw "Abortado: pasa -ConfirmoNeonV35 tras autorizacion humana y respaldo verificable."
}
$raiz = Split-Path -Parent $PSScriptRoot
if (-not $EnvFile) { $EnvFile = Join-Path $raiz ".env.neon" }
if (!(Test-Path -LiteralPath $EnvFile)) {
    throw "Falta $EnvFile"
}
Get-Content -LiteralPath $EnvFile | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
    $k, $v = $_.Split('=', 2)
    Set-Item -Path ("Env:" + $k.Trim()) -Value $v.Trim()
}
Remove-Item Env:MYCAR_ENV_FILE -ErrorAction SilentlyContinue
Set-Location (Join-Path $raiz "backend")
Write-Host "Flyway migrate Neon (pendientes; esperado V35 TIVE documental). Password omitido."
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
Write-Host "Flyway migrate OK."
