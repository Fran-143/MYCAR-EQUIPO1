# MyCar — Ajustes RF08, listado RF09 y navegación preparada

Base: main e65c90d (PR #7 integrado). 27/10/2026.
El paquete contiene 33 archivos de proyecto e incluye la entrada DEBUG entregada anteriormente.
No necesitas aplicar otra vez el ZIP anterior. No contiene migraciones ni .env. Ajusta las dependencias androidTest para API 37 en libs.versions.toml y app/build.gradle.kts.

## Actualización para el error de Espresso en API 37

Si ya aplicaste el bloque y apareció InputManager.getInstance, corrige únicamente:
- En android/gradle/libs.versions.toml, sección versions: junitVersion="1.3.0", espressoCore="3.7.0" y añade testRunner="1.7.0".
- En la sección libraries añade: androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "testRunner" }.
- En dependencies de android/app/build.gradle.kts añade: androidTestImplementation(libs.androidx.test.runner).
Guarda y vuelve a ejecutar connectedDebugAndroidTest con el emulador encendido.
Este ZIP ya incorpora esa corrección.

## Qué puedes hacer ahora

- Probar Agregar vehículo con Guardar deshabilitado hasta que los datos sean válidos.
- Ver errores en los campos editados y secciones que no ocultan sus errores.
- Volver desde el formulario a Mis vehículos de prueba y desde allí a Inicio/Comprobar conexión.
- Probar listado vacío, tarjeta de ejemplo, activo/inactivo, contadores, búsqueda y límite Free de ejemplo.
- Disponer de GET /api/v1/vehiculos y del cliente/listado Android real para integrarlo con Elsa.

La demostración no registra vehículos. Los catálogos y tarjetas DEBUG no son datos de tu cuenta.
El backend/cliente real no usa esos ejemplos ni acepta idUsuario del formulario.
La autenticación de Elsa y la limpieza RF06 siguen pendientes de recibir su contrato y código.

## 1. Conserva la rama en la que estás trabajando

```powershell
git -C "C:\Proyectos\MyCarEquipo1" status --short --branch
```

Si ya estás en feat/entrada-pruebas-rf08 y solo tienes los archivos del paquete anterior,
puedes aplicar este bloque en ESA misma rama, conservando el avance. Revisa tus cambios antes de reemplazar archivos.
No cambies de rama con modificaciones propias sin conservarlas. No uses reset, clean ni borrados de BD.

Si tienes el árbol limpio y quieres una rama nueva:

```powershell
git -C "C:\Proyectos\MyCarEquipo1" switch main
git -C "C:\Proyectos\MyCarEquipo1" pull --ff-only origin main
git -C "C:\Proyectos\MyCarEquipo1" switch -c feat/cierre-rf08-listado-rf09
```

Si un comando falla, detente en ese paso. No continúes suponiendo que cambiaste de rama.

## 2. Aplica los archivos

Descarga MyCar_Bloque_RF08_RF09.zip, extráelo y copia el contenido de ARCHIVOS a
C:\Proyectos\MyCarEquipo1, conservando todas las carpetas.
Acepta reemplazar los archivos del bloque; LISTA_ARCHIVOS.txt enumera los 33.
No copies la carpeta ARCHIVOS como una carpeta adicional dentro del proyecto.

Alternativa PowerShell si el ZIP está con ese nombre exacto en Descargas:

```powershell
$zipBloqueMyCar = Join-Path $env:USERPROFILE "Downloads\MyCar_Bloque_RF08_RF09.zip"
$extraidoBloqueMyCar = Join-Path $env:USERPROFILE "Downloads\MyCar_Bloque_RF08_RF09"
Expand-Archive -LiteralPath $zipBloqueMyCar -DestinationPath $extraidoBloqueMyCar -Force
$origenBloqueMyCar = Join-Path $extraidoBloqueMyCar "ARCHIVOS"
Get-ChildItem -LiteralPath $origenBloqueMyCar -Recurse -File | ForEach-Object {
    $relativoBloqueMyCar = $_.FullName.Substring($origenBloqueMyCar.Length).TrimStart('\')
    $destinoBloqueMyCar = Join-Path "C:\Proyectos\MyCarEquipo1" $relativoBloqueMyCar
    New-Item -ItemType Directory -Path (Split-Path $destinoBloqueMyCar -Parent) -Force | Out-Null
    Copy-Item -LiteralPath $_.FullName -Destination $destinoBloqueMyCar -Force
}
git -C "C:\Proyectos\MyCarEquipo1" status --short
```

## 3. Comprueba Android y abre la app

```powershell
Set-Location "C:\Proyectos\MyCarEquipo1\android"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug :app:processReleaseMainManifest
```

Resultado verificado: BUILD SUCCESSFUL. 56 pruebas JVM aprobadas, lint sin errores.
Las 3 pruebas de interfaz se compilan; no se ejecutan con ese comando.

En Android Studio elige app + debug + emulador encendido y pulsa Run.
Si aparece la bienvenida antigua: Run > Edit Configurations > app > Launch Options > Default Activity.

Prueba este recorrido:
1. Al abrir, Agregar vehículo lleva franja Modo de prueba.
2. Guardar debe estar deshabilitado con campos vacíos o inválidos.
3. Escribe una placa incorrecta y un año con barras: deben aparecer errores sin habilitar Guardar.
4. Completa marca, modelo, uso, placa A1B234, año 2020, color, motor/combustible y km positivo.
5. Otro exige texto. Eléctrico limpia combustible/aceite/número de motor y no los exige.
6. Si informas un VIN/serie/motor inválido, corrígelo para poder Guardar; puedes dejarlos vacíos.
7. Con todo válido, Guardar muestra que es una prueba, sin afirmar que registró en la BD.
8. Volver o Cancelar llevan a Mis vehículos de prueba: empieza vacío.
9. Mostrar ejemplo permite probar tarjeta, contadores y búsquedas sin coincidencias.
10. Ejemplo inactivo prueba el filtro Inactivos. Un vehículo inactivo sigue ocupando el cupo Free del ejemplo.
11. Volver lleva al menú MyCar · Pruebas. Comprobar conexión conserva la pantalla real del servidor.

Para ejecutar también las pruebas de interfaz con el emulador conectado:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## 4. Comprueba backend con PostgreSQL 17 desechable

Enciende Docker Desktop. No cambies tu .env, tu BD ni ejecutes SQL manual.

```powershell
Set-Location "C:\Proyectos\MyCarEquipo1\backend"
py -3 ..\scripts\test_rf08_api.py --incluir-rf09
```

Verificado: 42 pruebas BD, 180 de validadores/servicios/consultas/MVC, contexto Spring,
health UP y 5 rutas rechazando anónimos. Solo crea y retira su propio contenedor temporal.
Las pruebas usan fixtures en esa BD desechable; no certifican JWT/sesiones de Elsa.

## 5. Revisa, guarda y publica TU rama

Desde cualquier carpeta, prepara solo los archivos enumerados en el paquete:

```powershell
$listaBloqueMyCar = Join-Path $env:USERPROFILE "Downloads\MyCar_Bloque_RF08_RF09\LISTA_ARCHIVOS.txt"
$archivosBloqueMyCar = Get-Content -LiteralPath $listaBloqueMyCar
git -C "C:\Proyectos\MyCarEquipo1" diff --check
git -C "C:\Proyectos\MyCarEquipo1" add -- @archivosBloqueMyCar
git -C "C:\Proyectos\MyCarEquipo1" diff --cached --check
git -C "C:\Proyectos\MyCarEquipo1" diff --cached --stat
```

Revisa que solo aparezcan archivos del bloque; si ya habías confirmado el paquete anterior,
el número de archivos nuevos del commit puede ser menor de 33.

```powershell
git -C "C:\Proyectos\MyCarEquipo1" commit -m "feat: completar validacion RF08 y preparar listado RF09"
git -C "C:\Proyectos\MyCarEquipo1" branch --show-current
```

Publica la rama cuyo nombre te mostró el último comando. Por ejemplo, si usaste la rama nueva:

```powershell
git -C "C:\Proyectos\MyCarEquipo1" push -u origin feat/cierre-rf08-listado-rf09
```

Si continuaste en feat/entrada-pruebas-rf08, publica ESA rama en su lugar.
Crea un PR hacia main y revísalo antes de hacer merge. No mezcles la rama de descargas con main.

Descripción sugerida:

> Ajusta Guardar y errores de RF08; añade listado RF09 de solo lectura con búsqueda, filtros,
> contadores y kilometraje vigente. Prepara el flujo Android con repositorios autenticados y
> mantiene ejemplos exclusivamente DEBUG. Sin cambios de BD ni migraciones.
> Verificado: 42 pruebas BD, 180 backend, 56 Android, lint sin errores y arranque HTTP protegido.
> Pendiente: integrar la sesión/limpieza RF06 con la rama de Elsa y ejecutar aceptación en emulador.
