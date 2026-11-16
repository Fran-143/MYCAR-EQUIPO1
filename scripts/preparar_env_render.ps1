# Genera JWT de despliegue (fuera de Git) y lista env vars del panel Render.
# NO imprime passwords ni el JWT.
[CmdletBinding()]
param()
$ErrorActionPreference = "Stop"
$outDir = Join-Path $env:LOCALAPPDATA "MyCar\render-free"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$jwtFile = Join-Path $outDir "jwt-secret.properties"
if (!(Test-Path $jwtFile)) {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $b64 = [Convert]::ToBase64String($bytes)
    [System.IO.File]::WriteAllText($jwtFile, "mycar.auth.jwt-secret-base64=$b64`n", (New-Object System.Text.UTF8Encoding($false)))
    [Array]::Clear($bytes, 0, $bytes.Length)
    Write-Host "JWT creado en %LOCALAPPDATA%\MyCar\render-free\jwt-secret.properties"
} else {
    Write-Host "JWT de despliegue ya existe (no se regenera)."
}

$cryptoFile = Join-Path $outDir "datos-sensibles.properties"
if (!(Test-Path $cryptoFile)) {
    $aes = New-Object byte[] 32
    $blind = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($aes)
        $rng.GetBytes($blind)
    } finally { $rng.Dispose() }
    $lines = @(
        "mycar.datos-sensibles.enabled=true",
        "mycar.datos-sensibles.key-id=v1",
        "mycar.datos-sensibles.aes-key=$([Convert]::ToBase64String($aes))",
        "mycar.datos-sensibles.blind-key=$([Convert]::ToBase64String($blind))"
    )
    [System.IO.File]::WriteAllLines($cryptoFile, $lines, (New-Object System.Text.UTF8Encoding($false)))
    [Array]::Clear($aes, 0, $aes.Length)
    [Array]::Clear($blind, 0, $blind.Length)
    Write-Host "Claves cifrado creadas en %LOCALAPPDATA%\MyCar\render-free\datos-sensibles.properties"
} else {
    Write-Host "Claves cifrado de despliegue ya existen (no se regeneran)."
}

Write-Host ""
Write-Host "Render > mycar-backend > Environment (secretos sync:false):"
Write-Host "  SPRING_PROFILES_ACTIVE=neon,render"
Write-Host "  SPRING_DATASOURCE_URL=jdbc:postgresql://...neon.tech:5432/neondb?sslmode=require"
Write-Host "  SPRING_DATASOURCE_USERNAME=neondb_owner"
Write-Host "  SPRING_DATASOURCE_PASSWORD=(desde .env.neon; no chat)"
Write-Host "  MYCAR_AUTH_JWT_SECRET_BASE64=(valor de jwt-secret.properties)"
Write-Host "  MYCAR_DATOS_SENSIBLES_ENABLED=true"
Write-Host "  MYCAR_DATOS_SENSIBLES_KEY_ID=v1"
Write-Host "  MYCAR_DATOS_SENSIBLES_AES_KEY=(aes-key de datos-sensibles.properties)"
Write-Host "  MYCAR_DATOS_SENSIBLES_BLIND_KEY=(blind-key de datos-sensibles.properties)"
Write-Host "  SPRING_FLYWAY_ENABLED=false"
Write-Host "  MYCAR_RF23_EVALUACION_DIARIA_HABILITADA=false"
Write-Host "  MYCAR_VERIFICACION_MAIL_MODO=smtp"
Write-Host "  MYCAR_RECUPERACION_DESARROLLO=false"
Write-Host ""
Write-Host "Region Blueprint: virginia | Health: /actuator/health | Dockerfile: ./Dockerfile"
Write-Host "PENDIENTE: correo OTP no operativo en Render Free (SMTP 465/587 bloqueados)."
Write-Host "Sin MYCAR_DATOS_SENSIBLES_* en Dashboard, SOAT/Licencia falla con CIFRADO_NO_CONFIGURADO."
Write-Host ""
Write-Host "Android release:"
Write-Host "  cd android; .\gradlew.bat assembleRelease -Pmycar.apiBaseUrl=https://TU-SERVICIO.onrender.com/"
