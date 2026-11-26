[CmdletBinding()]
param(
    [int]$Puerto = 8081,
    # Académico local: alinea con Android Debug (política local-academic = 1.0). Producción: versión legal aprobada.
    [string]$VersionPrivacidad = "1.0"
)
$ErrorActionPreference = "Stop"
$raizMyCar = Split-Path -Parent $PSScriptRoot
# MFA local: cargar MYCAR_MFA_* desde .env del repo si el proceso no las trae (sin imprimir secretos).
$envLocalMyCar = Join-Path $raizMyCar ".env"
if (Test-Path -LiteralPath $envLocalMyCar) {
    Get-Content -LiteralPath $envLocalMyCar | ForEach-Object {
        $linea = $_.Trim()
        if ($linea -and -not $linea.StartsWith("#") -and $linea.Contains("=")) {
            $i = $linea.IndexOf("=")
            $k = $linea.Substring(0, $i).Trim()
            $v = $linea.Substring($i + 1).Trim().Trim('"')
            if ($k -like "MYCAR_MFA_*" -and -not [string]::IsNullOrWhiteSpace($v)) {
                Set-Item -Path "Env:$k" -Value $v
            }
        }
    }
}
# Clave solo para DESARROLLO LOCAL. Se conserva fuera del repositorio y nunca se imprime.
$directorioClaveMyCar = Join-Path $env:LOCALAPPDATA "MyCar\desarrollo-local"
$archivoClaveMyCar = Join-Path $directorioClaveMyCar "autenticacion.properties"
if (!(Test-Path -LiteralPath $archivoClaveMyCar)) {
    New-Item -ItemType Directory -Path $directorioClaveMyCar -Force | Out-Null
    $bytesMyCar = New-Object byte[] 32
    $rngMyCar = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rngMyCar.GetBytes($bytesMyCar) } finally { $rngMyCar.Dispose() }
    $contenidoMyCar = "mycar.auth.jwt-secret-base64=" + [Convert]::ToBase64String($bytesMyCar) + "`n"
    [System.IO.File]::WriteAllText($archivoClaveMyCar, $contenidoMyCar, (New-Object System.Text.UTF8Encoding($false)))
    [Array]::Clear($bytesMyCar,0,$bytesMyCar.Length)
    $contenidoMyCar = $null
}
if ($VersionPrivacidad -match '[\r\n]') { throw "Versión de privacidad inválida." }
# Perfil de ejecución efímero. Las duraciones son de prueba local, no una política de despliegue.
$configMyCar = @{
    "spring.config.additional-location" = "file:" + $archivoClaveMyCar.Replace('\','/')
    # RF60 pruebas Samsung: access más largo para no cortar el flujo admin/usuario a mitad.
    "mycar.auth.access-ttl" = "PT2H"
    "mycar.auth.session-ttl" = "P1D"
    "server.port" = $Puerto
    "mycar.rf17.api-habilitada" = "true"
    "mycar.rf18.api-habilitada" = "true"
    "mycar.rf22.api-habilitada" = "true"
}
if ($VersionPrivacidad) { $configMyCar["mycar.registro.privacidad.version-vigente"] = $VersionPrivacidad }
# Aislamiento estricto: nunca heredar SPRING_DATASOURCE_* de Neon/Render en este script.
$prevDsUrl = $env:SPRING_DATASOURCE_URL
$prevDsUser = $env:SPRING_DATASOURCE_USERNAME
$prevDsPass = $env:SPRING_DATASOURCE_PASSWORD
$prevProfiles = $env:SPRING_PROFILES_ACTIVE
$prevConfigLoc = $env:SPRING_CONFIG_LOCATION
$entornoAnteriorMyCar = $env:SPRING_APPLICATION_JSON
$ubicacionAnteriorMyCar = Get-Location
try {
    Remove-Item Env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
    Remove-Item Env:SPRING_DATASOURCE_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
    Remove-Item Env:SPRING_CONFIG_LOCATION -ErrorAction SilentlyContinue
    # Forzar JDBC local (sin imprimir secretos).
    $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:5432/mycar_mvp"
    if (-not $env:POSTGRES_USER) { $env:POSTGRES_USER = "mycar" }
    if (-not $env:POSTGRES_DB) { $env:POSTGRES_DB = "mycar_mvp" }
    if (-not $env:POSTGRES_PORT) { $env:POSTGRES_PORT = "5432" }
    $env:SPRING_DATASOURCE_USERNAME = $env:POSTGRES_USER
    if ($env:POSTGRES_PASSWORD) { $env:SPRING_DATASOURCE_PASSWORD = $env:POSTGRES_PASSWORD }
    $configMyCar["spring.datasource.url"] = "jdbc:postgresql://127.0.0.1:5432/mycar_mvp"
    $configMyCar["mycar.seguridad.exigir-datasource-local"] = "true"
    $env:SPRING_APPLICATION_JSON = $configMyCar | ConvertTo-Json -Compress
    Set-Location (Join-Path $raizMyCar "backend")
    Write-Host "Backend LOCAL puerto $Puerto -> 127.0.0.1:5432/mycar_mvp (Neon/Render bloqueados en este script)."
    if ($VersionPrivacidad) {
        Write-Host "RF01 registro habilitado (privacidad v$VersionPrivacidad). Para solo login: -VersionPrivacidad ''"
    } else {
        Write-Host "RF01 deshabilitado: login con cuentas existentes. Indica -VersionPrivacidad para crear cuentas."
    }
    & .\mvnw.cmd spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw "El backend terminó con error." }
} finally {
    $env:SPRING_APPLICATION_JSON = $entornoAnteriorMyCar
    if ($null -ne $prevDsUrl) { $env:SPRING_DATASOURCE_URL = $prevDsUrl } else { Remove-Item Env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue }
    if ($null -ne $prevDsUser) { $env:SPRING_DATASOURCE_USERNAME = $prevDsUser } else { Remove-Item Env:SPRING_DATASOURCE_USERNAME -ErrorAction SilentlyContinue }
    if ($null -ne $prevDsPass) { $env:SPRING_DATASOURCE_PASSWORD = $prevDsPass } else { Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue }
    if ($null -ne $prevProfiles) { $env:SPRING_PROFILES_ACTIVE = $prevProfiles } else { Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue }
    if ($null -ne $prevConfigLoc) { $env:SPRING_CONFIG_LOCATION = $prevConfigLoc } else { Remove-Item Env:SPRING_CONFIG_LOCATION -ErrorAction SilentlyContinue }
    Set-Location $ubicacionAnteriorMyCar
}
