# Permiso de red local en Android 17 / API 37

Diagnóstico confirmado en el emulador de Francesca: backend 8081 UP, API_BASE_URL correcto,
ACCESS_LOCAL_NETWORK declarado pero granted=false. La declaración del manifiesto no concede el permiso.

MainActivity solicita el permiso en tiempo de ejecución sólo en DEBUG desde API 37 para los hosts de
pruebas 10.0.2.2 y 127.0.0.1. Antes de concederlo muestra una explicación, un botón para solicitarlo y
acceso a Ajustes si se denegó. No inicia el flujo de autenticación mientras falte el permiso.
Al volver de Ajustes consulta otra vez el permiso y abre el flujo real si ya se permitió.
Release, las versiones anteriores y servidores remotos conservan su comportamiento.

## Aplicar

El paquete requiere feat/integracion-sesion-vehiculos y el MainActivity de la integración Elsa/RF50
entregado antes. Acepta ese contenido ya preparado en un merge o después de su commit. Hace respaldo
fuera del repositorio, valida todos los archivos antes de copiar y prepara sólo sus cinco rutas.
No hace commit, push, merge, cambios de BD ni borra cuentas/datos de la app.

Después de aplicar, recompilar/instalar desde Android Studio con app / emulador / Run app, o:

```powershell
Set-Location 'C:\Proyectos\MyCarEquipo1\android'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:installDebug
```

Aceptar el diálogo de red local. Si ya se concedió mediante ADB, la app continúa sin pedirlo otra vez.

## Desbloqueo inmediato para el emulador ya instalado

```powershell
$adbMyCar = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adbMyCar -s emulator-5554 shell pm grant com.mycar.app android.permission.ACCESS_LOCAL_NETWORK
& $adbMyCar -s emulator-5554 shell dumpsys package com.mycar.app |
    Select-String -Pattern 'ACCESS_LOCAL_NETWORK'
```

Debe mostrar granted=true. Mantener el backend en ejecución y volver a probar.
El comando es una ayuda local de desarrollo; las demás laptops reciben la solicitud desde la app
cuando instalen el código corregido. No desinstalar ni borrar datos para resolver este problema.

Un error de red no confirma por sí solo el resultado de RF01. Si al reintentar indica «Correo no disponible»,
probar Iniciar sesión con la cuenta anterior antes de crear otra cuenta. Si ahora responde configuración
legal/503, iniciar el backend con la versión del documento académico DEBUG de Elsa (1.0), según la guía
de integración. No compartir contraseñas, JWT ni el contenido del archivo local de autenticación.
