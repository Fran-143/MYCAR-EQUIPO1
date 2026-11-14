# Registra un taller OFICIAL en taller_global (admin).
# LOCAL/prueba: apunta a localhost. PRODUCCIÓN: requiere autorización + MYCAR_TALLER_ADMIN_SECRET en Render.
# Uso:
#   $env:MYCAR_TALLER_ADMIN_SECRET='...'
#   .\scripts\taller_admin_registrar.ps1 -BaseUrl http://127.0.0.1:8081 -Nombre "Taller Prueba MyCar" -Telefono 987654321 -TieneWhatsapp -ContactoAutorizado:$false
param(
    [string]$BaseUrl = "http://127.0.0.1:8081",
    [Parameter(Mandatory = $true)][string]$Nombre,
    [string]$Telefono = "",
    [string]$Correo = "",
    [string]$Direccion = "",
    [switch]$TieneWhatsapp,
    [switch]$ContactoAutorizado,
    [switch]$EsDemo
)
$ErrorActionPreference = "Stop"
$secret = $env:MYCAR_TALLER_ADMIN_SECRET
if ([string]::IsNullOrWhiteSpace($secret)) {
    throw "Define MYCAR_TALLER_ADMIN_SECRET en el entorno (no lo pegues en el chat)."
}
$body = @{
    nombre = $Nombre
    telefono = if ($Telefono) { $Telefono } else { $null }
    correo = if ($Correo) { $Correo } else { $null }
    direccion = if ($Direccion) { $Direccion } else { $null }
    tieneWhatsapp = [bool]$TieneWhatsapp
    contactoAutorizado = [bool]$ContactoAutorizado
    esDemo = [bool]$EsDemo
} | ConvertTo-Json -Compress
$uri = ($BaseUrl.TrimEnd('/')) + "/api/v1/rf34/admin/talleres-globales"
Write-Host "POST $uri (secreto omitido; contactoAutorizado=$ContactoAutorizado)"
$resp = Invoke-RestMethod -Method Post -Uri $uri -ContentType "application/json" `
    -Headers @{ "X-MyCar-Taller-Admin-Secret" = $secret } -Body $body
Write-Host ("OK idTallerGlobal={0} nombre={1} contactoAutorizado={2}" -f $resp.idTallerGlobal, $resp.nombre, $resp.contactoAutorizado)
