# MyCar — bloque Android RF08

Son 17 archivos NUEVOS para el proyecto que acabas de publicar. Tú los copias y ejecutas. No se reemplaza MainActivity, Gradle, .env, manifiestos, backend ni migraciones.

## 1. Descargar y extraer

Guarda MyCar_RF08_Android_Completo.zip en Descargas y ejecuta en PowerShell:

```powershell
$paqueteAndroidMyCar = Join-Path $env:USERPROFILE "Downloads\MyCar_RF08_Android_Completo"
Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE "Downloads\MyCar_RF08_Android_Completo.zip") -DestinationPath $paqueteAndroidMyCar -ErrorAction Stop
cd C:\Proyectos\MyCarEquipo1
git status --short --branch
```

Continúa en feat/vehiculos-mvp. No necesitas cambiarte a la rama de descarga de GitHub.

## 2. Copiar todos los archivos

El siguiente bloque revisa que no exista ninguno de los destinos antes de copiar. Si detecta un nombre existente, se detiene para evitar sobrescribir avances:

```powershell
& {
    $ErrorActionPreference = "Stop"
    $repositorioAndroidMyCar = "C:\Proyectos\MyCarEquipo1"
    $origenAndroidMyCar = (Get-Item -LiteralPath (Join-Path $paqueteAndroidMyCar "ARCHIVOS")).FullName
    $copiasAndroidMyCar = @(Get-ChildItem -LiteralPath $origenAndroidMyCar -Recurse -File | ForEach-Object {
        [PSCustomObject]@{
            Origen = $_.FullName
            Destino = Join-Path $repositorioAndroidMyCar ($_.FullName.Substring($origenAndroidMyCar.Length + 1))
        }
    })
    $existentesAndroidMyCar = @($copiasAndroidMyCar | Where-Object { Test-Path -LiteralPath $_.Destino })
    if ($existentesAndroidMyCar.Count -gt 0) {
        $existentesAndroidMyCar | Select-Object Destino | Format-Table -AutoSize
        throw "Hay archivos con el mismo nombre. No se copió ningún archivo; revisar antes de continuar."
    }
    foreach ($copiaAndroidMyCar in $copiasAndroidMyCar) {
        New-Item -ItemType Directory -Path (Split-Path -Parent $copiaAndroidMyCar.Destino) -Force | Out-Null
        Copy-Item -LiteralPath $copiaAndroidMyCar.Origen -Destination $copiaAndroidMyCar.Destino -ErrorAction Stop
    }
    Write-Output "Bloque Android RF08 copiado. Fuentes anteriores conservadas."
}
```

Si prefieres el Explorador, copia las carpetas android y docs que están dentro de ARCHIVOS a la raíz del proyecto, respetando su estructura.

## 3. Compilar y probar en un solo bloque

No necesitas Docker ni iniciar el backend para estas pruebas Android. Desde PowerShell:

```powershell
cd C:\Proyectos\MyCarEquipo1\android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Esperamos BUILD SUCCESSFUL y 47 pruebas unitarias sin fallos: 46 nuevas + 1 existente. Para ver el informe:

```powershell
Start-Process ".\app\build\reports\tests\testDebugUnitTest\index.html"
```

El APK debug se genera en android/app/build/outputs/apk/debug/app-debug.apk. No cambiamos tu configuración de JDK: usa la que ya tiene configurada Android Studio/Gradle. No ejecutes las pruebas del backend otra vez por este bloque, porque su código no se modificó.

## 4. Ver el formulario sin esperar a Elsa

1. Abre C:\Proyectos\MyCarEquipo1\android en Android Studio.
2. Usa la variante debug y sincroniza Gradle si el IDE lo solicita.
3. Abre `app/src/debug/java/com/mycar/app/features/vehiculos/ui/RegistroVehiculoPreview.kt`.
4. Pulsa Split o Design. Si aparece Build & Refresh, ejecútalo.
5. En la vista previa puedes activar modo interactivo para probar campos, desplegables, OTRO/ELÉCTRICO y validaciones. Desplázate para ver el resto del formulario.

Los ejemplos son solo de diseño; Guardar no envía datos desde esta Preview ni muestra una confirmación falsa de registro. Se identifican con "Vista de diseño · no guarda vehículos".

La app abierta en el emulador todavía mostrará Inicio: no modificamos MainActivity para saltarnos la sesión. El formulario de producción, su ViewModel y el repositorio ya están preparados, pero la navegación real y el cliente autenticado se conectarán con el trabajo de Elsa. El formulario nunca necesita un idUsuario escrito libremente en el JSON.

## 5. Revisar cambios

```powershell
cd C:\Proyectos\MyCarEquipo1
git diff --check
git status --short --branch
```

Pasa el final de la compilación y el resultado de Git. Después guardaremos este bloque en un commit de tu rama. No se incluyó commit/push automático.

## Integración pendiente

En ARCHIVOS/docs/vehiculos/ANDROID_RF08_20261027.md están las clases, comportamiento de recuperación y el punto para integrar Retrofit autenticado, cuenta real, navegación de login/Inicio/Mis vehículos. No hay un token, usuario ni servidor de prueba incorporado en producción.
