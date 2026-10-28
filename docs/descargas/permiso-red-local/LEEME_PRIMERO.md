# MyCar — permiso de red local para el emulador Android 17

La salida de Francesca confirmó backend UP en 8081 y URL correcta, pero ACCESS_LOCAL_NETWORK granted=false.
Este paquete corrige la solicitud del permiso desde la app. Requiere la integración Elsa/RF50 ya aplicada.

## 1. Desbloquear el emulador actual

```powershell
$adbMyCar = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adbMyCar -s emulator-5554 shell pm grant com.mycar.app android.permission.ACCESS_LOCAL_NETWORK
& $adbMyCar -s emulator-5554 shell dumpsys package com.mycar.app |
    Select-String -Pattern 'ACCESS_LOCAL_NETWORK'
```

La línea del permiso debe indicar granted=true. Mantener el backend activo y volver a la app.
Si devuelve Correo no disponible, intentar Iniciar sesión con los datos usados antes de crear otra cuenta.

## 2. Incorporar la corrección para las demás instalaciones

Descargar MyCar_Permiso_Red_Local.zip con Download raw a Descargas, fuera del repositorio.

```powershell
$zipRedMyCar = Get-ChildItem "$env:USERPROFILE\Downloads" -File -Filter '*MyCar_Permiso_Red_Local*.zip' |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (!$zipRedMyCar) { throw 'Descarga primero el ZIP con Download raw.' }

$paqueteRedMyCar = Join-Path $env:TEMP ('MyCar-Red-Local-' + [guid]::NewGuid().ToString())
Expand-Archive -LiteralPath $zipRedMyCar.FullName -DestinationPath $paqueteRedMyCar
powershell -NoProfile -ExecutionPolicy Bypass -File "$paqueteRedMyCar\APLICAR.ps1"
```

Valida rama y base de los cinco archivos, crea respaldo fuera del proyecto y prepara únicamente esas rutas.
Acepta la integración Elsa ya aplicada con merge pendiente sin conflictos o después de su commit local.
No hace commit/push/merge, no modifica el backend, migraciones, .env ni contraseñas.

## 3. Compilar e instalar

```powershell
Set-Location 'C:\Proyectos\MyCarEquipo1\android'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
.\gradlew.bat :app:installDebug
```

Abrir MyCar en el emulador. La app pedirá el permiso si aún falta; aceptarlo.
Si se denegó de forma permanente, la pantalla ofrece Abrir ajustes.
Si ya se concedió por ADB, la app continúa directamente al flujo de Elsa.

Las pruebas de bienvenida conceden el permiso antes de lanzar MainActivity, para poder probar la navegación.
El flujo real del diálogo necesita revisión manual en el emulador; no se ejecutó en el entorno de entrega.
La corrección sólo solicita este permiso en DEBUG, API 37 o posterior y host local 10.0.2.2 / 127.0.0.1.
