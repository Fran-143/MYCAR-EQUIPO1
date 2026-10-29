# Bajar main del PR #12 a tu rama RF22

Este auxiliar incorpora en tu PC el `main` revisado `8eaeef3`, conservando tu rama `feat/programar-alerta-rf22` y tu RF22. Está preparado para el estado que compartiste: HEAD `529927a`, RF22 instalado sin commit y los mismos archivos de los ZIP originales.

No crea otro proyecto Android. La carpeta en Descargas contiene el ayudante de actualización; el proyecto sigue siendo `C:\Users\PROPIETARIO\Documents\MYCAR-EQUIPO1-git`.

## Qué hace

1. Comprueba los 40 archivos originales de RF22 y que no haya otros cambios locales. Si difieren, se detiene antes de guardar commits o copiar resoluciones.
2. Copia un respaldo en Documentos y guarda RF22 en un commit local. Crea una rama local de respaldo que apunta a ese commit.
3. Combina exactamente el main `8eaeef3` con tu rama. Conserva el inicio RF50, la navegación, el permiso de red local, el login de Elsa y la integración de vehículos/mantenimientos/historial.
4. Resuelve los seis conflictos que se reprodujeron y probaron en cloud. Añade Alertas → Programar alerta para conservar el acceso a RF22 desde el inicio nuevo.
5. Comprueba que todo el árbol Git resultante coincida con la integración probada y crea el commit local de combinación.

Se crean **dos commits locales**. No sube tu rama, no abre PR ni fusiona en GitHub. No cambia tu rama main local, `.env`, claves ni bases. V1–V6 no cambian. El auxiliar de arranque RF22 actualizado habilita RF17, RF18 y RF22.

## Aplicar desde PowerShell

Guardar `RF22_ACTUALIZAR_MAIN_8eaeef3.zip` en Descargas. Detener la ejecución vieja de Spring Boot con Ctrl+C antes de volver a arrancarla más adelante.

```powershell
& {
    $ErrorActionPreference = "Stop"
    $rf22Paquete = "$env:USERPROFILE\Downloads\RF22_ACTUALIZAR_MAIN_8eaeef3.zip"
    $rf22Actualizacion = "$env:USERPROFILE\Downloads\RF22_ACTUALIZAR_MAIN"

    Expand-Archive -LiteralPath $rf22Paquete -DestinationPath $rf22Actualizacion -Force

    powershell -NoProfile -ExecutionPolicy Bypass `
        -File "$rf22Actualizacion\ACTUALIZAR_RF22_MAIN.ps1"
    if ($LASTEXITCODE -ne 0) { throw "La actualización se detuvo. Comparte el resultado." }

    powershell -NoProfile -ExecutionPolicy Bypass `
        -File "$rf22Actualizacion\COMPROBAR_RF22_ACTUALIZADO.ps1"
    if ($LASTEXITCODE -ne 0) { throw "Falló una comprobación. La integración ya está guardada; comparte el resultado." }
}
```

Durante la combinación Git muestra seis mensajes `CONFLICT` y `Automatic merge failed`. Son el resultado intermedio esperado; el auxiliar comprueba los nombres, aplica las resoluciones revisadas y confirma la combinación. Esperar el mensaje final **Actualizacion integrada** y después la comprobación de compilación. Si se detiene con otros archivos o un árbol diferente, compartir el resultado sin forzar ni descartar cambios.

Al terminar:

```powershell
Set-Location "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git"
git status --short --branch
git log -3 --oneline
```

Debe mantenerse `feat/programar-alerta-rf22`, sin cambios pendientes, con un commit de combinación y el commit que guarda RF22. La versión probada Android tiene 237 pruebas unitarias. Las comprobaciones locales no ejecutan pruebas instrumentadas ni usan la BD del equipo.

## Abrir la app actualizada

Con Docker y la base habitual del equipo funcionando, iniciar el backend en una ventana y dejarla abierta:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git\scripts\iniciar_backend_rf22.ps1"
```

En otra ventana, con el emulador iniciado y después de compilar:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\RF22_ACTUALIZAR_MAIN\ABRIR_MYCAR_ACTUALIZADO.ps1"
```

Debe abrir la bienvenida real del inicio nuevo. Permitir la red local si Android lo solicita, iniciar sesión y usar Inicio / Vehículos / Mantenimientos / Alertas. En **Alertas → Programar alerta** se abre RF22. La consulta/listado RF23 continúa como Próximamente.

Para datos reales necesitas cuenta y vehículo propio activo ya guardados. Los ejemplos de diseño siguen siendo ejemplos. RF17/RF18 reciben la integración de sesión del PR #12; la actualización no sustituye sus reglas de negocio.

## Verificación del paquete

- Backend: 368 pruebas ejecutadas sin fallos, 172 optativas omitidas; incluye 39 RF22, 19 sobre PostgreSQL, y ocho de la integración de sesión con vehículos.
- Android: 237 pruebas JVM aprobadas, incluidas 29 RF22; APK debug y de instrumentación, Kotlin release y lint sin errores. Las pruebas de emulador compilaron, pero no se ejecutaron en cloud.
- HTTP real: login y acceso a vehículos, RF17, RF18 y RF22 con la misma sesión; confirmar, repetir sin duplicar y consultar operación e historial.
- Actualizador: ejercitado con PowerShell 7.4.6 Linux en clones desechables, terminaciones CRLF, respaldo, rechazo de archivos distintos, conservación de `.env` sin incluirlo en Git y reejecución. Windows PowerShell y el recorrido en tu móvil siguen pendientes.

No reaplicar después los ZIP originales de RF22 fijados a `529927a`; sus comprobaciones rechazarán los archivos de esta nueva base.
