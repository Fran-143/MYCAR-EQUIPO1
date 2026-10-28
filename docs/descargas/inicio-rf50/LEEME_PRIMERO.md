# MyCar: Inicio RF50 y navegación común

Rama de Francesca: feat/integracion-sesion-vehiculos. main revisado: 6fc5ad4, con RF18 incorporado localmente.
Aplicar DESPUÉS del bloque de sesión y de la resolución de Inicio/RF18 de cinco archivos.
Este paquete no hace commit, push ni merge a main. La rama docs/descarga-rf08-android solo distribuye el ZIP.

## 1. Conservar el estado anterior

En PowerShell:

    git -C 'C:\Proyectos\MyCarEquipo1' status

Si dice «All conflicts fixed but you are still merging», termina el merge ya resuelto en TU rama:

    git -C 'C:\Proyectos\MyCarEquipo1' diff --cached --check
    git -C 'C:\Proyectos\MyCarEquipo1' commit -m 'merge: incorporar historial RF18 y unificar inicio'

El estado debe quedar limpio en feat/integracion-sesion-vehiculos. No aplicar sobre main ni repetir ZIP anteriores.
Si hay conflictos todavía o cambios adicionales sin conservar, compartir el estado antes de continuar.

## 2. Descargar y aplicar

Descargar MyCar_Inicio_RF50_Navegacion.zip en Descargas mediante Download raw.
Copiar el bloque con sus saltos de línea:

```powershell
$zipInicioMyCar=Get-ChildItem "$env:USERPROFILE\Downloads" -File -Filter '*MyCar_Inicio_RF50_Navegacion*.zip' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if(!$zipInicioMyCar) { throw 'Descarga primero el ZIP con Download raw.' }
$paqueteInicioMyCar=Join-Path $env:TEMP ('MyCar-Inicio-RF50-'+[guid]::NewGuid().ToString())
Expand-Archive -LiteralPath $zipInicioMyCar.FullName -DestinationPath $paqueteInicioMyCar
powershell -NoProfile -ExecutionPolicy Bypass -File "$paqueteInicioMyCar\APLICAR.ps1"
```

El auxiliar comprueba rama, merge concluido, árbol limpio, todos los archivos/hash y su base antes de copiar.
Hace un respaldo de fuentes fuera del proyecto. Si cambia algún archivo base se detiene sin reemplazos.

## 3. Verificar

Docker Desktop operativo; desde backend:

```powershell
Set-Location 'C:\Proyectos\MyCarEquipo1\backend'
py -3 ..\scripts\test_rf08_api.py --incluir-rf09 --incluir-sesion
```

Desde android:

```powershell
Set-Location 'C:\Proyectos\MyCarEquipo1\android'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

La última orden necesita un emulador plenamente arrancado. Si aparece Unknown API Level, resolver primero
el emulador: no abortar un merge ni modificar código para ocultar un fallo de dispositivo.
Los ejecutores de BD utilizan PostgreSQL desechable, no mycar_mvp.

## 4. Ejecutar la app con datos reales

IMPORTANTE: detener con Ctrl+C la terminal del backend anterior. El auxiliar ahora habilita RF17/RF18
para la integración local; un proceso antiguo mantiene su configuración anterior aunque cambien los archivos.
Arrancarlo otra vez en una terminal dedicada:

```powershell
Set-Location 'C:\Proyectos\MyCarEquipo1'
docker compose up -d --wait postgres
powershell -NoProfile -ExecutionPolicy Bypass -File '.\scripts\iniciar_backend_integracion_local.ps1'
```

Mantiene 8081 y la clave JWT fuera de Git. Comprobar http://127.0.0.1:8081/actuator/health.
Luego Android Studio → app → emulador → Run app, con la actividad predeterminada.

La app abre Inicio. Barra: Inicio | Vehículos | Mantenimientos | Alertas.
Sin sesión no inventa datos: «Hola» y acceso al login existente. Vehículos o Mantenimientos permiten entrar.
Después de login carga Inicio real. La misma sesión se reutiliza para RF08/RF09 y RF17/RF18.
Con un vehículo real activo: selector, placa, lectura disponible y último mantenimiento REALIZADO.
Mantenimientos reutiliza las pantallas existentes de Nayu; no hay otro formulario ni historial paralelo.

Alertas, Perfil y Notificaciones muestran Próximamente como se pidió. RF10/RF14 no existen en la base:
Ver vehículo y Actualizar tienen reservas de navegación, sin inventar una segunda implementación.
No hay nombre/fotos en los contratos actuales: encabezado neutro e ilustración predeterminada.
No se muestran nombre Carlos, badge 2, vehículos ni kilometrajes de ejemplo como datos reales.

En código: HomeScreen.kt es ahora el resumen; MyCarNavigation.kt organiza las pestañas.
La comprobación anterior se conserva aparte como ConexionServidorScreen, sin dominar Inicio.
Vista de diseño: src/debug/.../features/inicio/ui/InicioPreview.kt (fixtures solo DEBUG; contenido de Inicio).

Si no hay cuenta, su creación usa el endpoint RF01 existente y la versión legal aprobada por Elsa.
No inventar ese valor, compartir contraseñas ni insertar SQL para simular el flujo de la app.

## 5. Revisar resultados y preparar cambios locales

Consultar docs/inicio/RF50_INICIO_Y_NAVEGACION.md para rutas, decisiones y límites.
RF50 COMPLETO permanece pendiente de sus bloques de alertas/notificaciones, datos de perfil/fotos y RF10/RF14.
Esta entrega implementa el resumen disponible y la navegación solicitada sin esas funcionalidades excluidas.

Después de pruebas y revisión manual, preparar solo los archivos del manifiesto:

```powershell
$manifiestoInicioMyCar=Get-Content "$paqueteInicioMyCar\MANIFIESTO.json" -Raw | ConvertFrom-Json
$rutasInicioMyCar=@($manifiestoInicioMyCar.archivos | ForEach-Object { $_.ruta })
git -C 'C:\Proyectos\MyCarEquipo1' add -- $rutasInicioMyCar
git -C 'C:\Proyectos\MyCarEquipo1' diff --cached --check
git --no-pager -C 'C:\Proyectos\MyCarEquipo1' diff --cached --stat
```

No se ejecuta git commit, push ni merge automáticamente. Compartir resultados para revisar antes de publicar.
El nombre/commit exacto de tu merge local puede diferir del entorno de prueba: el instalador valida contenido
por archivo para no depender del SHA de un commit creado en otra laptop.
