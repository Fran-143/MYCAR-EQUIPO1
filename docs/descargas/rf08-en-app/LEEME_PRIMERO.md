# MyCar — Abrir el formulario RF08 al iniciar la app

Este paquete permite probar el formulario en el emulador, sin utilizar el Preview y sin esperar el login de Elsa.
La app mostrará Modo de prueba. Guarda valida el formulario; todavía NO registra vehículos en PostgreSQL.

Contiene 5 archivos de proyecto: 4 de Android DEBUG y 1 documento. Se aplica sobre main con PR #7 integrado.

## 1. Prepara tu rama

```powershell
git -C "C:\Proyectos\MyCarEquipo1" status --short --branch
```

Si aparecen cambios locales, consérvalos antes de cambiar de rama. Con el árbol limpio:

```powershell
git -C "C:\Proyectos\MyCarEquipo1" switch main
git -C "C:\Proyectos\MyCarEquipo1" pull --ff-only origin main
git -C "C:\Proyectos\MyCarEquipo1" switch -c feat/entrada-pruebas-rf08
```

Si un comando falla, detente en ese paso. Si ya creaste esa rama, usa switch feat/entrada-pruebas-rf08.

## 2. Extrae y copia

Extrae MyCar_RF08_Abrir_En_App.zip. Copia el contenido de ARCHIVOS dentro de C:\Proyectos\MyCarEquipo1,
conservando las carpetas. Reemplaza AndroidManifest.xml de src/debug y RegistroVehiculoPreview.kt.
No reemplaces el manifiesto principal de src/main. Los otros 3 archivos son nuevos.

Alternativa PowerShell si el ZIP está con su nombre exacto en Descargas:

```powershell
$zipAppMyCar = Join-Path $env:USERPROFILE "Downloads\MyCar_RF08_Abrir_En_App.zip"
$extraidoAppMyCar = Join-Path $env:USERPROFILE "Downloads\MyCar_RF08_Abrir_En_App"
Expand-Archive -LiteralPath $zipAppMyCar -DestinationPath $extraidoAppMyCar -Force
$origenAppMyCar = Join-Path $extraidoAppMyCar "ARCHIVOS"
Get-ChildItem -LiteralPath $origenAppMyCar -Recurse -File | ForEach-Object {
    $relativoAppMyCar = $_.FullName.Substring($origenAppMyCar.Length).TrimStart('\')
    $destinoAppMyCar = Join-Path "C:\Proyectos\MyCarEquipo1" $relativoAppMyCar
    New-Item -ItemType Directory -Path (Split-Path $destinoAppMyCar -Parent) -Force | Out-Null
    Copy-Item -LiteralPath $_.FullName -Destination $destinoAppMyCar -Force
}
git -C "C:\Proyectos\MyCarEquipo1" status --short
```

## 3. Compila y abre en el emulador

```powershell
Set-Location "C:\Proyectos\MyCarEquipo1\android"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:processReleaseMainManifest
```

En Android Studio elige app, variante debug y el emulador encendido. Pulsa Run (triángulo verde).
Al abrir debe aparecer Agregar vehículo con una franja que indica que no guarda en la BD.

Si sigue apareciendo la bienvenida: Run > Edit Configurations > app > Launch Options > Default Activity.
Vuelve a ejecutar Run para instalar la versión nueva. No hace falta borrar los datos de la app.

Prueba:
- Guardar vacío muestra errores.
- Año con barras o futuro, placa inválida y km cero se rechazan al Guardar.
- Otro muestra la especificación. Eléctrico oculta campos que no aplican.
- Identificación adicional es opcional y se puede desplegar.
- Volver y Cancelar abren el menú MyCar · Pruebas.
- Desde el menú puedes reabrir el formulario o Comprobar conexión.
- Datos válidos muestra que es una prueba; no debe decir Vehículo registrado.

El formulario no necesita Docker. Comprobar conexión sí necesita el backend y la red configurados como antes.

## 4. Revisa y publica TU rama cuando termines de probar

```powershell
git -C "C:\Proyectos\MyCarEquipo1" diff --check
git -C "C:\Proyectos\MyCarEquipo1" add -- android/app/src/debug/AndroidManifest.xml android/app/src/debug/java/com/mycar/app/debug/RegistroVehiculoDemoActivity.kt android/app/src/debug/java/com/mycar/app/features/vehiculos/ui/RegistroVehiculoDemo.kt android/app/src/debug/java/com/mycar/app/features/vehiculos/ui/RegistroVehiculoPreview.kt docs/vehiculos/PROBAR_RF08_EN_APP.md
git -C "C:\Proyectos\MyCarEquipo1" diff --cached --check
git -C "C:\Proyectos\MyCarEquipo1" diff --cached --stat
git -C "C:\Proyectos\MyCarEquipo1" commit -m "feat: habilitar prueba del formulario RF08 al abrir la app debug"
git -C "C:\Proyectos\MyCarEquipo1" push -u origin feat/entrada-pruebas-rf08
```

Crea un PR hacia main y revísalo antes de hacer merge.
La rama docs/descarga-rf08-android distribuye este ZIP; no debe mezclarse con main.
