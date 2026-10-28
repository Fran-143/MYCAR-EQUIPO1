[CmdletBinding()]
param(
    [int]$Puerto = 8081,
    [string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git",
    [string]$VersionPrivacidad = ""
)
$ErrorActionPreference = "Stop"
$raizMyCar = (Resolve-Path -LiteralPath $Repositorio).Path
if (-not (Test-Path -LiteralPath (Join-Path $raizMyCar "backend\pom.xml"))) { throw "No encuentro el backend en ese repositorio." }
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
    "mycar.auth.access-ttl" = "PT5M"
    "mycar.auth.session-ttl" = "P1D"
    "server.port" = $Puerto
    "mycar.rf22.api-habilitada" = $true
}
if ($VersionPrivacidad) { $configMyCar["mycar.registro.privacidad.version-vigente"] = $VersionPrivacidad }
$entornoAnteriorMyCar = $env:SPRING_APPLICATION_JSON
$ubicacionAnteriorMyCar = Get-Location
try {
    $env:SPRING_APPLICATION_JSON = $configMyCar | ConvertTo-Json -Compress
    Set-Location (Join-Path $raizMyCar "backend")
    Write-Host "Backend RF22 local en puerto $Puerto. Clave fuera de Git; no compartir ese archivo."
    Write-Host "Flyway validara V1-V5 y aplicara V6 si esta pendiente: solo corrige la funcion de alta RF22, sin borrar datos."
    if (!$VersionPrivacidad) { Write-Host "Login disponible para cuentas existentes. RF01 necesita una versión legal aprobada para crear cuentas." }
    & .\mvnw.cmd spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw "El backend terminó con error." }
} finally {
    $env:SPRING_APPLICATION_JSON = $entornoAnteriorMyCar
    Set-Location $ubicacionAnteriorMyCar
}
