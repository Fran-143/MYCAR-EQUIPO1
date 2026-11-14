# Aplica SOLO V26 pendiente en Neon via Flyway. Requiere aprobación explícita.
# NO ejecutar sin autorización humana.
# Uso (desde worktree con .env.neon o copiado desde repo principal):
#   .\scripts\neon_flyway_v26.ps1 -ConfirmoNeonV26
param(
    [switch]$ConfirmoNeonV26,
    [string]$EnvFile
)
$ErrorActionPreference = "Stop"
if (-not $ConfirmoNeonV26) {
    throw "Abortado: pasa -ConfirmoNeonV26 tras autorizacion humana."
}
$raiz = Split-Path -Parent $PSScriptRoot
if (-not $EnvFile) { $EnvFile = Join-Path $raiz ".env.neon" }
if (!(Test-Path -LiteralPath $EnvFile)) {
    throw "Falta $EnvFile — copia desde C:\Proyectos\MyCarEquipo1\.env.neon o usa scripts/solicitar_password_neon.ps1"
}
Get-Content -LiteralPath $EnvFile | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
    $k, $v = $_.Split('=', 2)
    Set-Item -Path ("Env:" + $k.Trim()) -Value $v.Trim()
}
Remove-Item Env:MYCAR_ENV_FILE -ErrorAction SilentlyContinue

# Precheck RO: debe estar en 25 y sin V26
Write-Host "Precheck: Flyway debe estar en 25 antes de V26 (password no se imprime)."
Set-Location (Join-Path $raiz "backend")
Write-Host "Ejecutando Flyway migrate controlado (aplicara V26 si es la unica pendiente)..."
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
Write-Host "Flyway migrate OK — V26 aplicada. Verificar columnas y health Render tras redeploy."
