# Actualizar main y combinar Inicio, vehículos y mantenimiento

Fecha: 27/10/2026. Rama de Francesca: feat/integracion-sesion-vehiculos.
Base local actual: 032aef2, con el paquete de sesión de 30 archivos aplicado y aún sin commit.
main revisado: 6fc5ad4 (PR #10 de historial RF18 de Nayu).

Este ZIP contiene cinco archivos de resolución. El avance completo de tus compañeras lo descarga Git.
No es una copia antigua de la app ni reemplaza el paquete de sesión de 30 archivos.
Los auxiliares operan solo en tu rama. No hacen push ni merge a main ni alteran BD o credenciales.

## 1. Extraer este paquete

Descargar MyCar_Actualizar_Main_RF18_Inicio.zip con Download raw y guardar en Descargas.
En PowerShell, copiar todo el bloque respetando sus saltos de línea:

```powershell
$zipActualizacionMyCar = Get-ChildItem "$env:USERPROFILE\Downloads" -File -Filter '*MyCar_Actualizar_Main_RF18_Inicio*.zip' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (!$zipActualizacionMyCar) { throw 'Primero descarga el ZIP en Descargas.' }
$actualizacionMyCar = Join-Path $env:TEMP ('MyCar-Actualizar-Inicio-' + [guid]::NewGuid().ToString())
Expand-Archive -LiteralPath $zipActualizacionMyCar.FullName -DestinationPath $actualizacionMyCar
```

El ZIP tiene CONSERVAR.ps1 y RESOLVER.ps1 en su raíz. Esta carpeta temporal se usa solo como origen.
Si abres otra ventana de PowerShell, tendrás que volver a definir $actualizacionMyCar con esa ruta.

## 2. Conservar tu trabajo en un commit local

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$actualizacionMyCar\CONSERVAR.ps1"
```

Este auxiliar compara los 30 archivos con el paquete de sesión original, admitiendo LF/CRLF, y prepara
únicamente esos archivos en Git. Si tienes cambios adicionales en ellos se detiene antes de agregar.
No ejecuta el commit: revisa el resumen y después:

```powershell
git -C 'C:\Proyectos\MyCarEquipo1' diff --cached --check
git --no-pager -C 'C:\Proyectos\MyCarEquipo1' diff --cached --stat
git -C 'C:\Proyectos\MyCarEquipo1' commit -m 'feat: conectar sesion real con registro y listado de vehiculos'
git -C 'C:\Proyectos\MyCarEquipo1' status --short --branch
```

El estado debe quedar limpio en feat/integracion-sesion-vehiculos. El commit queda en tu laptop;
no aprueba un pull request ni sube la aplicación a GitHub. Si no quedó limpio, compartir la salida antes de seguir.

## 3. Incorporar el avance de GitHub en TU rama

```powershell
git -C 'C:\Proyectos\MyCarEquipo1' fetch origin
$mainActualizacionMyCar = git -C 'C:\Proyectos\MyCarEquipo1' rev-parse origin/main
if ($mainActualizacionMyCar -eq '6fc5ad42a40b1091092d49d3b3e4e31bc508a6c9') {
    git -C 'C:\Proyectos\MyCarEquipo1' merge --no-commit origin/main
} else {
    throw 'main cambió desde la revisión; comparte git log -1 --oneline origin/main para adaptar la resolución.'
}
```

Se esperan exactamente estos dos conflictos:

- android/app/src/main/java/com/mycar/app/MainActivity.kt
- android/app/src/main/java/com/mycar/app/features/inicio/HomeScreen.kt

Son normales al combinar cambios de navegación. No borrar archivos, descartar tu trabajo ni usar reset.
Si fetch falla, no continuar hasta que descargue correctamente. Si hay otros conflictos, compartirlos.

## 4. Aplicar la combinación preparada

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$actualizacionMyCar\RESOLVER.ps1"
```

Exige el merge revisado, rechaza otros conflictos, comprueba los cinco archivos, respalda las versiones
locales fuera del repositorio, copia la resolución y la prepara en Git. No hace commit ni push.
Después estos dos comandos deben terminar sin salida:

```powershell
git -C 'C:\Proyectos\MyCarEquipo1' diff --name-only --diff-filter=U
git -C 'C:\Proyectos\MyCarEquipo1' diff --cached --check
```

Verás también archivos de historial/seguridad/pruebas de Nayu preparados automáticamente por el merge.
Eso es lo esperado: se está incorporando su avance completo de main, conservando tu bloque de sesión.

## 5. Verificar y ejecutar la app actualizada

Docker Desktop operativo. Desde backend:

```powershell
Set-Location 'C:\Proyectos\MyCarEquipo1\backend'
py -3 ..\scripts\test_rf08_api.py --incluir-rf09 --incluir-sesion
```

Desde android, con el emulador encendido para la última orden:

```powershell
Set-Location 'C:\Proyectos\MyCarEquipo1\android'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Ejecutar app desde Android Studio usando el emulador seleccionado y la actividad de inicio predeterminada.
No basta con compilar ni con usar una APK instalada antes de esta actualización.
Ahora abre Inicio principal con vehículos, registro de mantenimiento, historial, comprobación de conexión
y, solo en DEBUG, pruebas visuales. Estas últimas siguen sin guardar vehículos en BD.
La conexión real de RF08 se abre por Mis vehículos · iniciar sesión.

Mantenimiento e historial mantienen sus requisitos de repositorio/cuenta de Nayu: mostrar los botones no
significa que se haya conectado su cliente Android a la sesión de vehículos. No se inventan identidades.
El historial backend nuevo mantiene su propiedad de habilitación y su seguridad de main.

El backend de desarrollo real se ejecuta como antes, en una terminal dedicada desde raíz:

```powershell
Set-Location 'C:\Proyectos\MyCarEquipo1'
powershell -NoProfile -ExecutionPolicy Bypass -File '.\scripts\iniciar_backend_integracion_local.ps1'
```

## 6. Terminar la integración en tu rama

Solo con las pruebas aprobadas:

```powershell
git --no-pager -C 'C:\Proyectos\MyCarEquipo1' diff --cached --stat
git -C 'C:\Proyectos\MyCarEquipo1' commit -m 'merge: incorporar historial RF18 y unificar inicio'
git -C 'C:\Proyectos\MyCarEquipo1' status --short --branch
```

La rama debe quedar limpia. Si todo está revisado, para publicar TU rama y abrir un PR:

```powershell
git -C 'C:\Proyectos\MyCarEquipo1' push -u origin feat/integracion-sesion-vehiculos
```

El push no integra main. Crear PR hacia main y revisarlo antes de Merge pull request.
Título propuesto: «Integrar sesión real de vehículos y conservar navegación de mantenimiento e historial».
No crear un PR desde docs/descarga-rf08-android: esa rama distribuye los ZIP.

## 7. Comprobar persistencia en tu BD

Registrar desde Mis vehículos · iniciar sesión, con cuenta válida y cupo. Después, desde raíz:

```powershell
Set-Location 'C:\Proyectos\MyCarEquipo1'
docker compose exec -T postgres psql -U mycar -d mycar_mvp -c "SELECT v.id_vehiculo, v.placa, v.marca, v.modelo, l.valor_km, o.estado FROM mycar.vehiculo v LEFT JOIN mycar.lectura_kilometraje l ON l.id_vehiculo=v.id_vehiculo AND l.origen='INICIAL' LEFT JOIN mycar.operacion_idempotente o ON o.id_operacion=l.id_operacion ORDER BY v.id_vehiculo DESC LIMIT 10;"
```

Buscar la placa introducida, su kilometraje y estado CONFIRMADA. Sin esa salida o una consulta equivalente
no se afirma aquí que ya se haya guardado un vehículo en la BD personal de Francesca.
