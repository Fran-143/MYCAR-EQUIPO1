# Registra el único taller oficial de prueba autorizado en Render/Neon.
# Requiere MYCAR_TALLER_ADMIN_SECRET (env o %LOCALAPPDATA%\MyCar\taller-admin-secret.txt).
# No imprime el secreto.
param(
    [string]$BaseUrl = "https://mycar-backend-1dxi.onrender.com",
    [string]$Nombre = "Taller Prueba MyCar",
    [string]$Telefono = "956188469"
)
$ErrorActionPreference = "Stop"
$secret = $env:MYCAR_TALLER_ADMIN_SECRET
if ([string]::IsNullOrWhiteSpace($secret)) {
    $path = Join-Path $env:LOCALAPPDATA "MyCar\taller-admin-secret.txt"
    if (Test-Path $path) { $secret = (Get-Content $path -Raw).Trim() }
}
if ([string]::IsNullOrWhiteSpace($secret)) {
    throw "Falta MYCAR_TALLER_ADMIN_SECRET (env o archivo local)."
}
$env:MYCAR_TALLER_ADMIN_SECRET = $secret
& "$PSScriptRoot\taller_admin_registrar.ps1" -BaseUrl $BaseUrl -Nombre $Nombre `
    -Telefono $Telefono -TieneWhatsapp -ContactoAutorizado
