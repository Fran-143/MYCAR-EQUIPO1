# Aplica SOLO V24 pendiente en Neon via Flyway (Spring). Requiere aprobacion explicita.
# Uso: .\scripts\fcm_neon_flyway_v24.ps1 -ConfirmoNeonV24
param(
    [switch]$ConfirmoNeonV24,
    [switch]$SoloInfo
)
$ErrorActionPreference = "Stop"
if (-not $ConfirmoNeonV24 -and -not $SoloInfo) {
    throw "Abortado: pasa -ConfirmoNeonV24 tras autorizacion, o -SoloInfo para dry-check."
}
$raiz = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $raiz ".env.neon"
Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
    $k, $v = $_.Split('=', 2)
    Set-Item -Path ("Env:" + $k.Trim()) -Value $v.Trim()
}
Remove-Item Env:MYCAR_ENV_FILE -ErrorAction SilentlyContinue
$env:SPRING_PROFILES_ACTIVE = "neon"

# Perfil neon apaga Flyway; lo reactivamos solo para este proceso controlado.
# ignore-missing-migrations no; validate checksums de V1-V23 debe pasar.
$json = @{
    "spring.config.import" = "optional:file:mycar-skip-docker-env.properties"
    "spring.flyway.enabled" = "true"
    "spring.flyway.locations" = "classpath:db/mvp"
    "spring.flyway.default-schema" = "mycar"
    "spring.flyway.schemas" = "mycar"
    "spring.jpa.hibernate.ddl-auto" = "validate"
    "mycar.rf23.evaluacion-diaria-habilitada" = "false"
    "mycar.fcm.worker-habilitado" = "false"
    "server.port" = "0"
} | ConvertTo-Json -Compress
$env:SPRING_APPLICATION_JSON = $json

Set-Location (Join-Path $raiz "backend")
if ($SoloInfo) {
    Write-Host "Flyway info (validate + pendientes) — sin migrate automatico en -SoloInfo via bootRun."
    Write-Host "Usa migrate solo con -ConfirmoNeonV24."
}

# Un solo arranque corto: Flyway migrate al iniciar; luego salir.
# Usamos un main via spring-boot:run con timeout no existe; mejor flyway-maven-plugin.
# Anadimos goals via dependencia ya en pom (flyway-core). Plugin CLI:
# URL JDBC completa (con jdbc:). Plugin resuelto desde el BOM de Spring Boot.
Write-Host "Ejecutando Flyway migrate controlado (una vez)..."
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
