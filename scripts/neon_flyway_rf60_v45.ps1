# Aplica V45 (RF60 incidencias generales) en Neon.
# Requiere V43/V44 ya aplicadas (o script neon_flyway_catalogos_v44 previo).
#
# Uso: .\scripts\neon_flyway_rf60_v45.ps1 -ConfirmoNeonRf60
param(
    [switch]$ConfirmoNeonRf60,
    [string]$EnvFile
)
$ErrorActionPreference = "Stop"
if (-not $ConfirmoNeonRf60) {
    throw "Abortado: pasa -ConfirmoNeonRf60 tras backup verificable."
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

Set-Location (Join-Path $raiz "backend")
Write-Host "Flyway migrate Neon target=45 (RF60 incidencias generales)."
& .\mvnw.cmd -q `
  "org.flywaydb:flyway-maven-plugin:migrate" `
  "-Dflyway.url=$($env:SPRING_DATASOURCE_URL)" `
  "-Dflyway.user=$($env:SPRING_DATASOURCE_USERNAME)" `
  "-Dflyway.password=$($env:SPRING_DATASOURCE_PASSWORD)" `
  "-Dflyway.locations=filesystem:src/main/resources/db/mvp" `
  "-Dflyway.defaultSchema=mycar" `
  "-Dflyway.schemas=mycar" `
  "-Dflyway.target=45" `
  "-Dflyway.baselineOnMigrate=false"
if ($LASTEXITCODE -ne 0) { throw "Flyway migrate Neon V45 falló" }
Write-Host "Flyway migrate OK (max V45)."
