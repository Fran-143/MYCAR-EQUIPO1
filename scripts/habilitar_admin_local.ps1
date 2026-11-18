[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Correo,
    [string]$BaseUrl = "http://127.0.0.1:8081"
)
$ErrorActionPreference = "Stop"
$uri = [Uri]$BaseUrl
if ($uri.Host -notin @("127.0.0.1", "localhost") -or $uri.Scheme -ne "http") {
    throw "Solo permitido contra backend local HTTP."
}
$secret = $env:MYCAR_ADMIN_BOOTSTRAP_SECRET
if ([string]::IsNullOrWhiteSpace($secret)) {
    throw "Define MYCAR_ADMIN_BOOTSTRAP_SECRET en el entorno local (no lo subas a Git)."
}
$body = @{ correo = $Correo.Trim(); secretoBootstrap = $secret } | ConvertTo-Json -Compress
try {
    $resultado = Invoke-RestMethod -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/ops/admin-bootstrap" `
        -Method Post -ContentType "application/json; charset=utf-8" `
        -Body ([Text.Encoding]::UTF8.GetBytes($body))
    Write-Host "OK: $($resultado.resultado) idUsuario=$($resultado.idUsuario) mfa=$($resultado.mfa)"
    Write-Host "El siguiente login con ese correo exigirá enroll TOTP (sin panel hasta confirmar)."
} catch {
    Write-Host "No se pudo habilitar ADMIN. Revisa que la cuenta exista, el secreto y que el backend local esté arriba."
    throw
} finally {
    $body = $null
    $secret = $null
}
