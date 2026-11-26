[CmdletBinding()]
param(
    [int]$Puerto = 8081,
    [string]$VersionPrivacidad = "1.0"
)
$ErrorActionPreference = "Stop"
$raizMyCar = Split-Path -Parent $PSScriptRoot
$archivoEnvNeon = Join-Path $raizMyCar ".env.neon"
if (!(Test-Path -LiteralPath $archivoEnvNeon)) {
    throw @"
Falta .env.neon en la raiz del repo.
1) Copia .env.neon.example a .env.neon
2) Rellena SPRING_DATASOURCE_PASSWORD (no lo pegues en el chat)
3) Vuelve a ejecutar este script
"@
}

# No cargar .env de Docker: evita mezclar host localhost con Neon.
Get-Content -LiteralPath $archivoEnvNeon | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
    $clave, $valor = $_.Split('=', 2)
    Set-Item -Path ("Env:" + $clave.Trim()) -Value $valor.Trim()
}
# Spring ConfigData no reconoce la extensión ".neon". Las credenciales ya están
# en Env:SPRING_DATASOURCE_*. Anulamos el import por defecto de ../.env (Docker).
Remove-Item Env:MYCAR_ENV_FILE -ErrorAction SilentlyContinue
$env:SPRING_PROFILES_ACTIVE = "neon"

if (-not $env:SPRING_DATASOURCE_URL) {
    throw "SPRING_DATASOURCE_URL ausente en .env.neon"
}
if (-not $env:SPRING_DATASOURCE_USERNAME) {
    throw "SPRING_DATASOURCE_USERNAME ausente en .env.neon"
}
if (-not $env:SPRING_DATASOURCE_PASSWORD) {
    throw "SPRING_DATASOURCE_PASSWORD vacio en .env.neon (usa scripts/solicitar_password_neon.ps1)"
}

$directorioClaveMyCar = Join-Path $env:LOCALAPPDATA "MyCar\desarrollo-local"
$archivoClaveMyCar = Join-Path $directorioClaveMyCar "autenticacion.properties"
if (!(Test-Path -LiteralPath $archivoClaveMyCar)) {
    New-Item -ItemType Directory -Path $directorioClaveMyCar -Force | Out-Null
    $bytesMyCar = New-Object byte[] 32
    $rngMyCar = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rngMyCar.GetBytes($bytesMyCar) } finally { $rngMyCar.Dispose() }
    $contenidoMyCar = "mycar.auth.jwt-secret-base64=" + [Convert]::ToBase64String($bytesMyCar) + "`n"
    [System.IO.File]::WriteAllText($archivoClaveMyCar, $contenidoMyCar, (New-Object System.Text.UTF8Encoding($false)))
    [Array]::Clear($bytesMyCar, 0, $bytesMyCar.Length)
}

if ($VersionPrivacidad -match '[\r\n]') { throw "Version de privacidad invalida." }

$configMyCar = @{
    # Evita importar ../.env (Docker) al no poder usar .env.neon como ConfigData.
    "spring.config.import" = "optional:file:mycar-skip-docker-env.properties"
    "spring.config.additional-location" = "file:" + $archivoClaveMyCar.Replace('\', '/')
    "mycar.auth.access-ttl" = "PT2H"
    "mycar.auth.session-ttl" = "P1D"
    "server.port" = $Puerto
    "mycar.rf17.api-habilitada" = "true"
    "mycar.rf18.api-habilitada" = "true"
    "mycar.rf22.api-habilitada" = "true"
    "mycar.rf34.api-habilitada" = "true"
    "mycar.rf46.api-habilitada" = "true"
    "mycar.rf23.evaluacion-diaria-habilitada" = "false"
    "spring.flyway.enabled" = "false"
}
if ($VersionPrivacidad) { $configMyCar["mycar.registro.privacidad.version-vigente"] = $VersionPrivacidad }

$entornoAnteriorJson = $env:SPRING_APPLICATION_JSON
$entornoAnteriorProfile = $env:SPRING_PROFILES_ACTIVE
$entornoAnteriorEnvFile = $env:MYCAR_ENV_FILE
$ubicacionAnterior = Get-Location
try {
    $env:SPRING_APPLICATION_JSON = $configMyCar | ConvertTo-Json -Compress
    Set-Location (Join-Path $raizMyCar "backend")
    Write-Host "Backend Neon (perfil neon) en puerto $Puerto. Flyway OFF. RF23 OFF. Password no se imprime."
    & .\mvnw.cmd spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw "El backend Neon termino con error." }
} finally {
    $env:SPRING_APPLICATION_JSON = $entornoAnteriorJson
    $env:SPRING_PROFILES_ACTIVE = $entornoAnteriorProfile
    $env:MYCAR_ENV_FILE = $entornoAnteriorEnvFile
    Set-Location $ubicacionAnterior
}
