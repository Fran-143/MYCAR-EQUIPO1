# Resolver el PR #9 conservando vehículos y mantenimiento

Revisión: 27/10/2026.
Rama de Francesca: feat/cierre-rf08-listado-rf09, HEAD revisado 90f5a3edf0f3a67261a999b015704e3647e25202.
main revisado: e39c0c253511b288286c70f51179d3fa471313e8 (incluye PR #6 de Nayu y PR #8 de Elsa).

Este paquete NO es el bloque completo de RF08/RF09. Contiene 7 archivos:
1. android/app/src/debug/AndroidManifest.xml: combina permiso de red local de Nayu con el launcher DEBUG de vehículos.
2. android/gradle/libs.versions.toml: conserva Espresso 3.7.0, JUnit 1.3.0 y runner explícito 1.7.0.
3. android/app/src/debug/java/com/mycar/app/debug/RegistroVehiculoDemoActivity.kt: añade acceso Inicio y mantenimientos al menú DEBUG.

Los archivos 4–7 adaptan las pruebas de servicio/DAO de vehículos de WebEnvironment.NONE a MOCK,
para cargar la nueva configuración Spring Security de Elsa en el entorno de pruebas.
No se desactivan filtros ni se cambian respuestas funcionales para hacer pasar las pruebas.

El tercer archivo permite abrir la MainActivity de Nayu desde la demostración; no modifica su módulo.
MainActivity, HomeScreen y todos los archivos de mantenimiento se incorporan mediante merge de main.
No se crean usuarios, sesiones o datos de BD. El formulario de mantenimiento conserva su requisito de sesión real.
RELEASE mantiene MainActivity como launcher; DEBUG mantiene un único launcher de demostración.

## 1. Verifica tu rama y conserva cambios locales

```powershell
git -C "C:\Proyectos\MyCarEquipo1" status --short --branch
```

Si tienes modificaciones locales, consérvalas antes de iniciar el merge. No uses reset ni clean.
Con el árbol limpio:

```powershell
git -C "C:\Proyectos\MyCarEquipo1" switch feat/cierre-rf08-listado-rf09
git -C "C:\Proyectos\MyCarEquipo1" fetch origin
$mainPr9MyCar = git -C "C:\Proyectos\MyCarEquipo1" rev-parse origin/main
if ($mainPr9MyCar -eq "e39c0c253511b288286c70f51179d3fa471313e8") {
    git -C "C:\Proyectos\MyCarEquipo1" merge --no-commit origin/main
} else {
    throw "main cambio desde la revision; no apliques estos archivos sin revisar la nueva version."
}
```

En este punto se esperan los mismos dos CONFLICT que informó GitHub.
Eso deja el merge pendiente en TU rama; todavía no integra tu PR en main.
Si aparecen archivos en conflicto distintos de los dos revisados, detente y comparte la salida.

## 2. Reemplaza únicamente los 7 archivos del paquete

Extrae MyCar_Resolucion_PR9.zip y copia el contenido de ARCHIVOS dentro de C:\Proyectos\MyCarEquipo1.
Conserva las carpetas y reemplaza esos siete archivos.
No vuelvas a aplicar el paquete completo anterior encima de este merge; su base era un main anterior a Nayu.

Alternativa PowerShell si el ZIP está con ese nombre exacto en Descargas:

```powershell
$zipPr9MyCar = Join-Path $env:USERPROFILE "Downloads\MyCar_Resolucion_PR9.zip"
$extraidoPr9MyCar = Join-Path $env:USERPROFILE "Downloads\MyCar_Resolucion_PR9"
Expand-Archive -LiteralPath $zipPr9MyCar -DestinationPath $extraidoPr9MyCar -Force
$origenPr9MyCar = Join-Path $extraidoPr9MyCar "ARCHIVOS"
Get-ChildItem -LiteralPath $origenPr9MyCar -Recurse -File | ForEach-Object {
    $relativoPr9MyCar = $_.FullName.Substring($origenPr9MyCar.Length).TrimStart('\')
    $destinoPr9MyCar = Join-Path "C:\Proyectos\MyCarEquipo1" $relativoPr9MyCar
    New-Item -ItemType Directory -Path (Split-Path $destinoPr9MyCar -Parent) -Force | Out-Null
    Copy-Item -LiteralPath $_.FullName -Destination $destinoPr9MyCar -Force
}
```

## 3. Marca la resolución y comprueba

```powershell
git -C "C:\Proyectos\MyCarEquipo1" add -- android/app/src/debug/AndroidManifest.xml android/gradle/libs.versions.toml android/app/src/debug/java/com/mycar/app/debug/RegistroVehiculoDemoActivity.kt backend/src/test/java/backend/vehiculos/service/RegistroVehiculoServicePostgresTest.java backend/src/test/java/backend/vehiculos/service/ConsultaRegistroVehiculoPostgresTest.java backend/src/test/java/backend/vehiculos/service/ListadoVehiculosPostgresTest.java backend/src/test/java/backend/vehiculos/dao/CatalogosVehiculoDaoTest.java
git --no-pager -C "C:\Proyectos\MyCarEquipo1" diff --name-only --diff-filter=U
git --no-pager -C "C:\Proyectos\MyCarEquipo1" diff --cached --check
```

Las dos revisiones deben terminar sin mostrar errores ni archivos sin resolver.
El resto del avance de Nayu aparece preparado automáticamente por el merge; es normal ver muchos archivos nuevos.

## 4. Ejecuta las comprobaciones de integración

```powershell
Set-Location "C:\Proyectos\MyCarEquipo1\android"
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:processReleaseMainManifest
```

Con el emulador encendido, ejecuta también:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Con Docker encendido, el ejecutor del backend usa PostgreSQL desechable:

```powershell
Set-Location "C:\Proyectos\MyCarEquipo1\backend"
py -3 ..\scripts\test_rf08_api.py --incluir-rf09
```

En el emulador comprueba además el menú de prueba: Volver desde formulario -> Mis vehículos -> Volver ->
Inicio y mantenimientos. Debe abrir el Inicio de Nayu, con su acceso a mantenimiento; no se simula una sesión.

## 5. Confirma el merge EN TU RAMA y actualiza el mismo PR #9

Si las comprobaciones pasan:

```powershell
git --no-pager -C "C:\Proyectos\MyCarEquipo1" diff --cached --stat
git -C "C:\Proyectos\MyCarEquipo1" commit -m "merge: integrar main conservando vehiculos y mantenimiento"
git -C "C:\Proyectos\MyCarEquipo1" push origin feat/cierre-rf08-listado-rf09
git -C "C:\Proyectos\MyCarEquipo1" status --short --branch
```

El PR #9 se actualiza automáticamente y GitHub recalcula los conflictos.
No crees otro PR ni hagas merge a main todavía: comparte el resultado para revisar la integración final.
La rama docs/descarga-rf08-android solo distribuye el paquete y no debe mezclarse con main.
