# MyCar — Mejora del frontend RF08

Este paquete modifica el diseño del registro de vehículo sobre main f4fd107.
Contiene 9 archivos para copiar: 2 Kotlin, 6 iconos y 1 documento.
No necesita modificar PostgreSQL, el backend ni las dependencias de Gradle.

## 1. Prepara una rama en tu PC

Abre PowerShell y ejecuta:

```powershell
git -C "C:\Proyectos\MyCarEquipo1" status --short --branch
```

Si aparecen cambios locales, consérvalos antes de cambiar de rama; no uses reset ni clean.
Con el árbol limpio:

```powershell
git -C "C:\Proyectos\MyCarEquipo1" switch main
git -C "C:\Proyectos\MyCarEquipo1" pull --ff-only origin main
git -C "C:\Proyectos\MyCarEquipo1" switch -c feat/vehiculos-ui-rf08
```

Si algún comando da error, detente en ese paso. Si la rama ya existe, usa switch feat/vehiculos-ui-rf08.

## 2. Aplica los archivos

Descarga MyCar_Frontend_RF08.zip y extráelo. Dentro encontrarás ARCHIVOS.
Copia el contenido de ARCHIVOS a C:\Proyectos\MyCarEquipo1, conservando las carpetas.
Acepta reemplazar solamente RegistroVehiculoScreen.kt y RegistroVehiculoPreview.kt.
Los seis iconos y el documento son nuevos.
No copies la carpeta ARCHIVOS como una carpeta dentro del proyecto.

Alternativa con PowerShell si guardaste el ZIP con su nombre exacto en Descargas:

```powershell
$zipUiMyCar = Join-Path $env:USERPROFILE "Downloads\MyCar_Frontend_RF08.zip"
$extraidoUiMyCar = Join-Path $env:USERPROFILE "Downloads\MyCar_Frontend_RF08"
Expand-Archive -LiteralPath $zipUiMyCar -DestinationPath $extraidoUiMyCar -Force
$origenUiMyCar = Join-Path $extraidoUiMyCar "ARCHIVOS"
Get-ChildItem -LiteralPath $origenUiMyCar -Recurse -File | ForEach-Object {
    $relativoUiMyCar = $_.FullName.Substring($origenUiMyCar.Length).TrimStart('\')
    $destinoUiMyCar = Join-Path "C:\Proyectos\MyCarEquipo1" $relativoUiMyCar
    New-Item -ItemType Directory -Path (Split-Path $destinoUiMyCar -Parent) -Force | Out-Null
    Copy-Item -LiteralPath $_.FullName -Destination $destinoUiMyCar -Force
}
git -C "C:\Proyectos\MyCarEquipo1" status --short
```

Deben aparecer dos archivos Kotlin modificados, seis iconos nuevos y el documento nuevo.

## 3. Mira el diseño y prueba los campos

1. En Android Studio abre android/app/src/debug/java/com/mycar/app/features/vehiculos/ui/RegistroVehiculoPreview.kt.
2. Selecciona Split o Design. Pulsa Build & Refresh si hace falta.
3. Abre RegistroVehiculoPreview; hay variantes normal, pequeña, letra grande y oscura.
4. Activa el modo interactivo. Abre/cierra las secciones y escribe datos.
5. Pulsa Guardar vacío: deben aparecer errores y abrirse las secciones que los contienen.
6. Introduce un año con barras o un VIN inválido y pulsa Guardar: deben señalarse.
7. Selecciona Otro para mostrar su especificación; selecciona Eléctrico para ocultar lo que no aplica.
8. Abre RegistroVehiculoConDatosPreview si quieres ver un formulario rellenado.

El Preview no necesita Docker ni login y no guarda vehículos.
La app normal mantiene la navegación que ya tenía: no se conecta este formulario a un usuario inventado.
La integración con autenticación sigue pendiente de Elsa.
La foto sigue como ilustración; aún no existe un endpoint de carga de imágenes para RF08.

## 4. Compila y ejecuta las pruebas Android

```powershell
Set-Location "C:\Proyectos\MyCarEquipo1\android"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Se verificaron 47 pruebas, sin fallos. El comando no requiere PostgreSQL.
Revisa RESULTADOS_VERIFICACION.md para conocer los límites y los dos errores preexistentes de lint.

## 5. Revisa y sube TU rama

```powershell
git -C "C:\Proyectos\MyCarEquipo1" diff --check
git -C "C:\Proyectos\MyCarEquipo1" diff --stat
git -C "C:\Proyectos\MyCarEquipo1" add -- android/app/src/main/java/com/mycar/app/features/vehiculos/ui/RegistroVehiculoScreen.kt android/app/src/debug/java/com/mycar/app/features/vehiculos/ui/RegistroVehiculoPreview.kt "android/app/src/main/res/drawable/ic_rf08_*.xml" docs/vehiculos/FRONTEND_RF08.md
git -C "C:\Proyectos\MyCarEquipo1" diff --cached --check
git -C "C:\Proyectos\MyCarEquipo1" diff --cached --stat
git -C "C:\Proyectos\MyCarEquipo1" commit -m "feat: mejorar formulario Android de registro de vehiculo"
git -C "C:\Proyectos\MyCarEquipo1" push -u origin feat/vehiculos-ui-rf08
```

En GitHub crea un Pull Request: base main, compare feat/vehiculos-ui-rf08.
Revisa Files changed antes de aprobarlo. La rama docs/descarga-rf08-android solo distribuye paquetes:
NO la mezcles con main.

Título sugerido: feat: mejorar formulario Android de registro de vehículo

Descripción sugerida:

> Actualiza RF08 con secciones desplegables, ayudas de formato, selectores anclados y acciones fijas.
> Conserva las reglas y el guardado existentes. Añade Previews DEBUG para tamaños y temas.
> Validación: assembleDebug y 47 pruebas unitarias Android correctas.
> lintDebug detecta dos errores preexistentes de includeSubdomains en la configuración de red DEBUG.
> Pendiente: conectar la navegación autenticada con Elsa y revisar visualmente en el dispositivo del equipo.
