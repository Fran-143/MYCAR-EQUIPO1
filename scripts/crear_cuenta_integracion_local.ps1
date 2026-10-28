[CmdletBinding()]
param([string]$VersionPrivacidad, [string]$BaseUrl = "http://127.0.0.1:8081")
$ErrorActionPreference = "Stop"
# Este auxiliar llama RF01: crea UNA cuenta y sus datos iniciales reales. No inserta SQL ni es un seed.
$uriMyCar = [Uri]$BaseUrl
if ($uriMyCar.Host -notin @("127.0.0.1","localhost") -or $uriMyCar.Scheme -ne "http") {
    throw "Este auxiliar solo permite el backend de desarrollo local por HTTP."
}
if (!$VersionPrivacidad) { throw "Indica la versión legal aprobada por Elsa y configurada en el backend; no inventes una versión." }
$nombreMyCar = Read-Host "Nombre"
$apellidoMyCar = Read-Host "Apellido"
$correoMyCar = Read-Host "Correo de la nueva cuenta"
$fechaMyCar = Read-Host "Fecha de nacimiento (AAAA-MM-DD)"
$claveMyCar = Read-Host "Contraseña" -AsSecureString
$confirmacionMyCar = Read-Host "Confirma contraseña" -AsSecureString
$aceptaMyCar = Read-Host "Tras leer la política vigente, escribe ACEPTO para consentir el registro"
if ($aceptaMyCar -cne "ACEPTO") { throw "Registro cancelado: sin consentimiento no se envía la solicitud." }
$punteroClaveMyCar = [IntPtr]::Zero
$punteroConfirmacionMyCar = [IntPtr]::Zero
try {
    $punteroClaveMyCar = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($claveMyCar)
    $punteroConfirmacionMyCar = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($confirmacionMyCar)
    $cuerpoMyCar = @{
        nombre=$nombreMyCar; apellido=$apellidoMyCar; correo=$correoMyCar; fechaNacimiento=$fechaMyCar
        contrasena=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($punteroClaveMyCar)
        confirmacionContrasena=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($punteroConfirmacionMyCar)
        consentimientoPrivacidad=@{aceptado=$true;version=$VersionPrivacidad}
    } | ConvertTo-Json -Compress
    $resultadoMyCar = Invoke-RestMethod -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/auth/registro" -Method Post -ContentType "application/json; charset=utf-8" -Body ([Text.Encoding]::UTF8.GetBytes($cuerpoMyCar))
    Write-Host "Resultado: $($resultadoMyCar.resultado). Ahora inicia sesión en la app."
} catch {
    # No imprimir cuerpo de solicitud, contraseñas ni cabeceras.
    Write-Host "No se confirmó la creación de la cuenta. Revisa validaciones, correo duplicado y configuración legal del backend."
    throw "Registro local no confirmado."
} finally {
    if ($punteroClaveMyCar -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($punteroClaveMyCar) }
    if ($punteroConfirmacionMyCar -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($punteroConfirmacionMyCar) }
    $cuerpoMyCar=$null; $claveMyCar=$null; $confirmacionMyCar=$null
}
