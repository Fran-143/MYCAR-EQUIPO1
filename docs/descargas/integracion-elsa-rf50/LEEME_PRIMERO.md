# MyCar — resolver merge de Elsa y conservar RF50

Paquete para el estado mostrado por Francesca: rama feat/integracion-sesion-vehiculos,
RF50 ya guardado en un commit local y merge pendiente de main 529927a.
Conflictos esperados: MainActivity.kt y android/app/src/debug/AndroidManifest.xml.
No abortar ni repetir el merge para aplicar este paquete.

## 1. Descargar y aplicar

En GitHub abrir MyCar_Integrar_Elsa_RF50.zip y pulsar Download raw.
El ZIP debe quedar en Descargas, fuera del repositorio.

```powershell
$zipElsaMyCar = Get-ChildItem "$env:USERPROFILE\Downloads" -File -Filter '*MyCar_Integrar_Elsa_RF50*.zip' |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (!$zipElsaMyCar) { throw 'Descarga primero el ZIP con Download raw.' }

$paqueteElsaMyCar = Join-Path $env:TEMP ('MyCar-Elsa-RF50-' + [guid]::NewGuid().ToString())
Expand-Archive -LiteralPath $zipElsaMyCar.FullName -DestinationPath $paqueteElsaMyCar
powershell -NoProfile -ExecutionPolicy Bypass -File "$paqueteElsaMyCar\APLICAR.ps1"
```

El instalador valida rama, MERGE_HEAD, exactamente los conflictos revisados, contenido de cada archivo,
prerrequisitos y hashes del paquete. Si la base cambió, se detiene antes de copiar.
Respalda todos los archivos reemplazados fuera del repositorio y prepara únicamente sus 19 rutas con git add.
Las entradas nuevas de Elsa ya preparadas por Git permanecen en el merge.
No hace commit, push, merge en GitHub, cambios SQL ni instalación de APK.

## 2. Verificaciones en tu laptop

Docker Desktop debe estar iniciado. Backend: regresión con PostgreSQL desechable.

```powershell
Set-Location 'C:\Proyectos\MyCarEquipo1\backend'
py -3 ..\scripts\test_rf08_api.py --incluir-rf09 --incluir-sesion
```

Android, primero compilación y pruebas que no necesitan dispositivo:

```powershell
Set-Location 'C:\Proyectos\MyCarEquipo1\android'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Con un emulador de pruebas completamente arrancado, sin sesión activa para los tests de bienvenida:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Si hay dispositivo offline/Unknown API Level, resolver el emulador antes de interpretar el resultado
como fallo de la aplicación. No se ha ejecutado esta última orden en el entorno de entrega.

## 3. Probar la app real

Detener el backend anterior con Ctrl+C y arrancar otra vez en su propia terminal.
Para usar el registro académico local DEBUG incluido por Elsa (documento versión 1.0):

```powershell
Set-Location 'C:\Proyectos\MyCarEquipo1'
docker compose up -d --wait postgres
powershell -NoProfile -ExecutionPolicy Bypass -File '.\scripts\iniciar_backend_integracion_local.ps1' -VersionPrivacidad '1.0'
```

Con cuenta existente puedes omitir VersionPrivacidad. Health: http://127.0.0.1:8081/actuator/health.
Android DEBUG se alinea con 8081; Release sigue pendiente de configuración de despliegue.
En Android Studio seleccionar app, emulador y Run app para instalar la compilación actual.

Esperado: Bienvenida de Elsa -> Crear cuenta o Iniciar sesión -> Inicio RF50 -> Vehículos/Mantenimientos,
sin otro formulario de login. Los módulos comparten la sesión y su renovación.
Verificar creación/listado de vehículo contra PostgreSQL; cerrar sesión sin red debe cerrar localmente.
Ver docs/inicio/INTEGRACION_ELSA_RF50.md para el recorrido y la consulta SQL de sólo lectura.

RF10/RF14 y los bloques completos de Alertas/Notificaciones/Perfil siguen pendientes. Este paquete
resuelve la integración de sesión y navegación, no declara terminados todos los RF.

## 4. Concluir tu merge local después de revisar las pruebas

```powershell
Set-Location 'C:\Proyectos\MyCarEquipo1'
git diff --name-only --diff-filter=U
git diff --cached --check
git --no-pager diff --cached --stat
git status
```

La primera orden no debe mostrar archivos. Cuando las pruebas y la revisión estén conformes:

```powershell
git commit -m 'merge: integrar autenticacion de Elsa con navegacion RF50 y vehiculos'
git status --short --branch
```

Este commit concluye únicamente el merge local de tu rama. Comparte los resultados antes de preparar
el PR; no se publica main ni se activa un merge automático. La rama docs/descarga-rf08-android es
únicamente de entregables: no crear un PR desde ella hacia main.
