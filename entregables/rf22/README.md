# RF22 — Backend y Android con frontend

Paquetes para incorporar **Programar alerta preventiva manual** al proyecto original. Base revisada: `main` `529927a`, incluido el acceso de Elsa (PR #11). Esta rama contiene descargas y fuentes de los paquetes: **no debe fusionarse con main**. Los instaladores preparan la rama de implementación `feat/programar-alerta-rf22` en tu PC para su revisión posterior.

## Descargar

Entra en GitHub con tu cuenta del equipo. Abre cada ZIP y pulsa **Download raw file / Descargar archivo sin procesar**; guarda ambos en Descargas con sus nombres exactos.

- [01_RF22_BACKEND.zip](01_RF22_BACKEND.zip)
- [02_RF22_ANDROID.zip](02_RF22_ANDROID.zip)
- [Instrucciones completas y límites](android/LEEME_RF22.md)
- [Resultados de verificación](VERIFICACION_RF22.json)
- [Hashes SHA-256](SHA256SUMS.txt)

No descargues esta rama completa para reemplazar tu proyecto. Los ZIP son cambios acotados, con manifiesto y respaldo; no contienen `.env`, contraseñas, tokens ni APK.

## Qué puedes hacer

Android incluye **formulario → revisión → confirmación**: auto propio activo, tipo de mantenimiento, OTRO con sugerencias, fecha/km actuales, antecedente opcional y programación por semanas, meses, kilómetros o ambos.

La entrada real usa el login y la sesión compartida de Elsa y llama al backend RF22. La confirmación guarda alerta, historial inicial y resultado idempotente en una transacción. Repetir una misma operación no crea otra alerta. Se incorpora `V6__rf22_tiempo_sin_lectura.sql` para permitir el criterio tiempo sin exigir lectura; V1–V5 permanecen intactas. La migración se aplica al arrancar Spring Boot.

**Ver alertas** aparece como **Próximamente** porque corresponde a RF23. Esta entrega tampoco completa la integración pendiente de RF17/RF18.

## Instalar desde PowerShell

Proyecto esperado: `C:\Users\PROPIETARIO\Documents\MYCAR-EQUIPO1-git`. Necesitas Git, Java 21 y el SDK Android que ya utilizas. Conserva tus cambios locales antes de comenzar. El instalador se detiene ante archivos diferentes a la versión revisada o una migración V6 ajena; no fuerces la copia.

Copia este bloque completo después de descargar ambos ZIP:

```powershell
& {
    $ErrorActionPreference = "Stop"
    $rf22Descargas = "$env:USERPROFILE\Downloads"

    Expand-Archive -LiteralPath "$rf22Descargas\01_RF22_BACKEND.zip" `
        -DestinationPath "$rf22Descargas\RF22_BACKEND" -Force
    Expand-Archive -LiteralPath "$rf22Descargas\02_RF22_ANDROID.zip" `
        -DestinationPath "$rf22Descargas\RF22_ANDROID" -Force

    powershell -NoProfile -ExecutionPolicy Bypass `
        -File "$rf22Descargas\RF22_BACKEND\APLICAR_BACKEND_RF22.ps1"
    if ($LASTEXITCODE -ne 0) { throw "Se detuvo el backend. Comparte el resultado." }

    powershell -NoProfile -ExecutionPolicy Bypass `
        -File "$rf22Descargas\RF22_ANDROID\APLICAR_ANDROID_RF22.ps1"
    if ($LASTEXITCODE -ne 0) { throw "Se detuvo Android. Comparte el resultado." }
}
```

Se crean respaldos en Documentos. Si falta el PR #11, se incorpora por avance directo únicamente en tu rama RF22 y con el árbol limpio. No se hace commit ni push y no se modifica tu `.env`.

## Ver el frontend en el emulador

Enciende **Medium Phone API 37** (`emulator-5554`) y ejecuta:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\RF22_ANDROID\VER_DISENO_RF22.ps1"
```

Puedes recorrer las tres pantallas con ejemplos. El cartel identifica el modo de diseño: **no llama al backend ni guarda en la BD**. Para verlo no necesitas Docker ni Spring Boot.

## Guardar una alerta real

Necesitas Docker y la base habitual del equipo, una cuenta MyCar real y un auto propio ACTIVO ya guardado. La demostración de vehículos no crea autos en PostgreSQL. Si ya hay un Spring Boot en 8081, detén esa ejecución antes de iniciar otra.

En una ventana PowerShell, deja ejecutándose:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\RF22_BACKEND\INICIAR_BACKEND_RF22.ps1"
```

En otra ventana, abre la entrada real:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\RF22_ANDROID\ABRIR_RF22_REAL.ps1"
```

Inicia sesión, selecciona tu auto, completa los intervalos, revisa y confirma. Esta entrada sí usa el backend. Si necesitas crear una cuenta nueva, consulta en el LEEME la configuración de la política académica que ya incluye el acceso de Elsa.

## Verificación realizada

- RF22 backend: **38 pruebas aprobadas**, incluidas **18 sobre PostgreSQL real**.
- Regresión backend: 358 pruebas ejecutadas, 172 optativas omitidas, sin fallos. Comprobación separada del esquema: 42 aprobadas.
- HTTP real: login, revisión, confirmación, repetición sin duplicado y consulta de operación comprobados.
- Android: **183 pruebas JVM aprobadas**, 27 específicas de RF22; APK debug, Kotlin release y APK de pruebas compilados. Lint sin errores.
- Cinco pruebas instrumentadas preparadas y compiladas; **todavía no ejecutadas en un emulador**. Ejecuta `PROBAR_PANTALLAS_RF22.ps1` en tu PC.
- Scripts analizados y ejercitados con PowerShell 7.4.6 en Linux, incluidos respaldo, rechazo de cambios ajenos y colisión V6. Falta validarlos en Windows PowerShell.

La instalación y el recorrido completo en tu móvil siguen pendientes; no se ha modificado tu PC ni tu base de datos desde esta entrega.
