# Aplica V46–V49 (Planes / suscripciones) en Neon.
# Requiere V45 ya aplicada.
#
# Uso: .\scripts\neon_flyway_planes_v49.ps1 -ConfirmoNeonPlanesV49
param(
    [switch]$ConfirmoNeonPlanesV49,
    [string]$EnvFile
)
$ErrorActionPreference = "Stop"
if (-not $ConfirmoNeonPlanesV49) {
    throw "Abortado: pasa -ConfirmoNeonPlanesV49 tras backup verificable."
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
Write-Host "Flyway migrate Neon target=49 (Planes RF27 + cupo FREE=1)."
& .\mvnw.cmd -q `
  "org.flywaydb:flyway-maven-plugin:migrate" `
  "-Dflyway.url=$($env:SPRING_DATASOURCE_URL)" `
  "-Dflyway.user=$($env:SPRING_DATASOURCE_USERNAME)" `
  "-Dflyway.password=$($env:SPRING_DATASOURCE_PASSWORD)" `
  "-Dflyway.locations=filesystem:src/main/resources/db/mvp" `
  "-Dflyway.defaultSchema=mycar" `
  "-Dflyway.schemas=mycar" `
  "-Dflyway.target=49" `
  "-Dflyway.baselineOnMigrate=false"

if ($LASTEXITCODE -ne 0) { throw "Flyway migrate falló ($LASTEXITCODE)" }
Write-Host "OK Neon en versión target 49."
