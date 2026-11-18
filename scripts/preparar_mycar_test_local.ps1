<#
.SYNOPSIS
  Prepara o reutiliza la cuenta local permanente "MyCar Test" (rol USUARIO).

.DESCRIPTION
  - Solo HTTP local (127.0.0.1 / localhost).
  - Usa el flujo real de registro con OTP en modo desarrollo.
  - No toca Render/Neon ni cuentas ADMIN reales.
  - Credenciales: %LOCALAPPDATA%\MyCar\mycar-test-local.json (fuera del repo).
  - Idempotente: si la cuenta ya existe, solo asegura vehículo de prueba y actualiza
    el archivo local de credenciales si pasas -ActualizarContrasena.

.PARAMETER Correo
  Correo de prueba bajo tu control. Default: mycar.test@mycar.local

.PARAMETER BaseUrl
  Backend local. Default: http://127.0.0.1:8081

.PARAMETER ActualizarContrasena
  Si la cuenta existe, pide nueva contraseña y la actualiza vía hash local (SQL local).
  Preferible: cambiar desde la app (Perfil) cuando esté disponible.
#>
[CmdletBinding()]
param(
    [string]$Correo = "mycar.test@mycar.local",
    [string]$BaseUrl = "http://127.0.0.1:8081",
    [switch]$ActualizarContrasena,
    [switch]$SinVehiculo
)

$ErrorActionPreference = "Stop"
$uri = [Uri]$BaseUrl
if ($uri.Host -notin @("127.0.0.1", "localhost") -or $uri.Scheme -ne "http") {
    throw "Solo permitido contra backend local HTTP."
}

function Read-SecurePassword([string]$Prompt) {
    $sec = Read-Host -Prompt $Prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        $sec.Dispose()
    }
}

function Assert-PasswordPolicy([string]$Password) {
    if ($Password.Length -lt 8 -or $Password.Length -gt 64) { throw "La contraseña debe tener 8–64 caracteres." }
    if ($Password -match '\s') { throw "La contraseña no debe tener espacios." }
    if ($Password -cnotmatch '[A-Z]' -or $Password -cnotmatch '[a-z]' -or $Password -notmatch '\d' -or $Password -notmatch '[^A-Za-z0-9]') {
        throw "Usa mayúscula, minúscula, dígito y carácter especial."
    }
}

function Invoke-Json([string]$Method, [string]$Path, [hashtable]$Body = $null, [string]$Token = $null) {
    $headers = @{ "Content-Type" = "application/json; charset=utf-8" }
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $params = @{
        Uri             = "$($BaseUrl.TrimEnd('/'))$Path"
        Method          = $Method
        Headers         = $headers
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 8 -Compress
        $params.Body = [Text.Encoding]::UTF8.GetBytes($json)
    }
    return Invoke-RestMethod @params
}

$credDir = Join-Path $env:LOCALAPPDATA "MyCar"
$credFile = Join-Path $credDir "mycar-test-local.json"
New-Item -ItemType Directory -Force -Path $credDir | Out-Null

# Cargar .env local si existe (solo para consultas SQL opcionales)
$repoRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $repoRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
        $k, $v = $_.Split('=', 2)
        if ($k -and -not [string]::IsNullOrWhiteSpace($k)) {
            Set-Item -Path "Env:$k" -Value $v
        }
    }
}

Write-Host "BaseUrl=$BaseUrl"
Write-Host "Correo=$Correo"
Write-Host "Credenciales locales (no Git): $credFile"

# ¿Existe ya?
$exists = $false
$idUsuario = $null
if ($env:POSTGRES_USER -and $env:POSTGRES_DB) {
    $q = "SELECT id_usuario, codigo_rol, estado_cuenta FROM mycar.usuario WHERE lower(btrim(correo))=lower(btrim('$($Correo.Replace("'","''"))'));"
    $row = docker exec mycarequipo1-postgres-1 psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -t -A -F '|' -c $q 2>$null
    if ($row -and $row.Trim().Length -gt 0) {
        $parts = $row.Trim().Split('|')
        $idUsuario = [long]$parts[0]
        $rol = $parts[1]
        $estado = $parts[2]
        if ($rol -ne 'USUARIO') { throw "La cuenta $Correo existe con rol $rol. Abortado (debe ser USUARIO)." }
        if ($estado -ne 'ACTIVA') { throw "La cuenta $Correo está $estado. Reactívala antes de usarla." }
        $exists = $true
        Write-Host "Cuenta existente idUsuario=$idUsuario (USUARIO/ACTIVA). No se vuelve a registrar."
    }
}

$password = $null
if (-not $exists) {
    Write-Host "Creando cuenta vía registro/verificación (OTP desarrollo)..."
    $password = Read-SecurePassword "Contraseña nueva para MyCar Test"
    $confirm = Read-SecurePassword "Confirma la contraseña"
    if ($password -cne $confirm) { throw "Las contraseñas no coinciden." }
    Assert-PasswordPolicy $password

    $form = @{
        nombre                 = "MyCar"
        apellido               = "Test"
        correo                 = $Correo
        fechaNacimiento        = "1995-06-15"
        contrasena             = $password
        confirmacionContrasena = $password
        consentimientoPrivacidad = @{
            aceptado = $true
            version  = "1.0"
        }
    }

    $origen = Invoke-Json -Method Post -Path "/api/v1/auth/registro/verificacion/origen"
    $emitBody = @{
        idOrigen      = $origen.idOrigen
        secretoOrigen = $origen.secretoOrigen
        formulario    = $form
    }
    $emit = Invoke-Json -Method Post -Path "/api/v1/auth/registro/verificacion/emitir" -Body $emitBody
    if (-not $emit.codigoDesarrollo) {
        throw "No llegó codigoDesarrollo. ¿mycar.verificacion.mail.modo=desarrollo en el backend local?"
    }
    $completeBody = @{
        idOperacion      = $emit.idOperacion
        secretoOperacion = $emit.secretoOperacion
        idEmision        = $emit.idEmision
        revision         = $emit.revision
        codigo           = $emit.codigoDesarrollo
        formulario       = $form
    }
    $done = Invoke-Json -Method Post -Path "/api/v1/auth/registro/verificacion/completar" -Body $completeBody
    if ($done.resultado -ne "CUENTA_CREADA") {
        throw "Registro inesperado: $($done | ConvertTo-Json -Compress)"
    }
    Write-Host "CUENTA_CREADA"

    $q2 = "SELECT id_usuario FROM mycar.usuario WHERE lower(btrim(correo))=lower(btrim('$($Correo.Replace("'","''"))'));"
    $idUsuario = [long](docker exec mycarequipo1-postgres-1 psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -t -A -c $q2).Trim()
}

# Guardar/actualizar archivo de credenciales local (nunca imprime la contraseña)
if ($password) {
    $payload = [ordered]@{
        etiqueta      = "MyCar Test"
        correo        = $Correo
        idUsuario     = $idUsuario
        rol           = "USUARIO"
        baseUrl       = $BaseUrl
        contrasena    = $password
        actualizadoUtc = (Get-Date).ToUniversalTime().ToString("o")
        nota          = "Solo local USB. No subir a Git. Cambiar si se filtra."
    }
    ($payload | ConvertTo-Json) | Set-Content -Path $credFile -Encoding UTF8
    Write-Host "Credenciales guardadas en archivo local (no se muestran aquí)."
} elseif (Test-Path $credFile) {
    Write-Host "Reutiliza la contraseña ya guardada en el archivo local de credenciales."
} else {
    Write-Host "AVISO: la cuenta existe pero no hay archivo local de contraseña."
    Write-Host "Vuelve a ejecutar con -ActualizarContrasena o inicia sesión si la recuerdas."
}

if ($ActualizarContrasena -and $exists) {
    Write-Host "Actualización de contraseña: usa Perfil > Cambiar contraseña en la app, o restablece con el flujo de recuperación local."
    Write-Host "Este script no escribe el hash a mano para no saltarse validaciones de la app."
}

# Vehículo de prueba
if (-not $SinVehiculo -and $idUsuario) {
    $placa = "TST101"
    $vin = "1G1YY22G645100001"  # 17 chars sin I/O/Q
    $qVeh = @"
SELECT id_vehiculo FROM mycar.vehiculo
WHERE id_usuario=$idUsuario AND upper(placa)=upper('$placa');
"@
    $idVeh = (docker exec mycarequipo1-postgres-1 psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -t -A -c $qVeh).Trim()
    if ($idVeh) {
        Write-Host "Vehículo de prueba ya existe idVehiculo=$idVeh placa=$placa"
    } else {
        if (-not $password -and -not (Test-Path $credFile)) {
            Write-Host "Sin contraseña disponible: no se puede registrar vehículo por API ahora."
            Write-Host "Inicia sesión en la app y registra un vehículo, o vuelve a ejecutar tras guardar credenciales."
        } else {
            if (-not $password) {
                $stored = Get-Content $credFile -Raw | ConvertFrom-Json
                $password = [string]$stored.contrasena
            }
            $install = [guid]::NewGuid().ToString()
            $login = Invoke-Json -Method Post -Path "/api/v1/auth/login" -Body @{
                correo        = $Correo
                contrasena    = $password
                idInstalacion = $install
            }
            $token = $login.accessToken
            if (-not $token) { throw "Login local falló; no hay accessToken." }
            $op = [guid]::NewGuid().ToString()
            $vehBody = @{
                idOperacion         = $op
                marca               = "Toyota"
                modelo              = "Corolla TEST"
                usoVehiculoCodigo   = "PARTICULAR"
                placa               = $placa
                anioFabricacion     = 2018
                color               = "Blanco TEST"
                idTipoMotor         = 1
                idCombustible       = 1
                kilometrajeInicial  = 12500
                numeroVin           = $vin
                numeroSerie         = "TESTSERIE01"
                numeroMotor         = "TESTMOT01"
                idTipoAceite        = 3
            }
            try {
                $reg = Invoke-Json -Method Post -Path "/api/v1/vehiculos" -Body $vehBody -Token $token
                Write-Host "Vehículo de prueba creado idVehiculo=$($reg.idVehiculo) placa=$placa"
            } catch {
                Write-Host "No se pudo crear el vehículo por API (puede existir conflicto de placa/VIN)."
                Write-Host $_.Exception.Message
            }
        }
    }
}

Write-Host ""
Write-Host "=== MyCar Test listo ==="
Write-Host "Correo: $Correo"
Write-Host "Rol: USUARIO"
Write-Host "Credenciales: $credFile"
Write-Host "ADMIN local MFA (no usar como usuario normal): admin.mfa.local@mycar.test"
Write-Host "Tras reiniciar PC: docker compose up -d postgres; backend :8081; adb reverse tcp:8081 tcp:8081"
