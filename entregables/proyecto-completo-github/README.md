# Actualizar TODO MyCar con las versiones publicadas en GitHub

Proyecto completo: `feat/mantenimientos-rf22-rf23` (`b7116ad`) + README actual de `main` (`91eb169`). El código de Android y backend se conserva tal como está publicado. No se añade V7 ni otra implementación RF22.

Incluye inicio, registro/login/cierre, vehículos, detalle, kilometraje RF14 desde Inicio/Detalle/Mantenimiento, mantenimiento RF17, historial RF18, programación RF22 y Mis alertas RF23 con los filtros Todas/Programadas/Próximas/Pendientes/Vencidas/Realizadas.

Este ZIP contiene los 464 archivos del proyecto completo en `proyecto/`, además de auxiliares de actualización. **Tu proyecto de trabajo sigue siendo Documents/MYCAR-EQUIPO1-git**. La copia `proyecto/` del ZIP sirve para revisión e integridad; no abrirla como un segundo proyecto ni copiarla manualmente sobre tu carpeta.

## Qué cambia en tu PC

- Conserva tu rama anterior y crea una referencia local de respaldo.
- Guarda el código anterior en un ZIP y la BD PostgreSQL en un dump privado dentro de Documentos.
- Cambia tu carpeta actual a la rama del equipo `feat/mantenimientos-rf22-rf23`, siguiendo su origen, e incorpora el README de main. No sube tu trabajo ni fusiona nada en GitHub.
- Mantiene `.env`, SDK local y la clave de desarrollo fuera de Git.
- La V6 ya aplicada en tu PC tiene checksum `790664128`. La V6 oficial de esta rama tiene checksum `-665165790`.
- La opción explícita **-AplicarV6Publicada** ejecuta las definiciones oficiales V6 y utiliza **Flyway repair** para alinear su registro, después del respaldo. Solo permite los checksums revisados, sin migraciones fallidas y con V1–V5 intactas. Verifica que todas las tablas de negocio conserven la misma cantidad de filas. No borra la base ni añade V7.
- Si hay alertas/operaciones RF22 de la implementación anterior o una operación pendiente en la app, se detiene antes de modificar archivos o BD. Esos datos necesitan revisar compatibilidad entre contratos; no se descartan.

Flyway puede contar siete entradas de historial: creación del esquema + seis migraciones. Eso no significa que exista V7.

## Ejecutar

1. Detener Spring Boot anterior con Ctrl+C. Mantener Docker/PostgreSQL y el emulador `emulator-5554` encendidos.
2. Guardar `MYCAR_ACTUALIZAR_TODO.zip` en Descargas.
3. Pegar en PowerShell:

```powershell
& {
    $ErrorActionPreference = "Stop"
    $mycarActualizacion = "$env:USERPROFILE\Downloads\MYCAR_ACTUALIZAR_TODO"

    Expand-Archive -LiteralPath "$env:USERPROFILE\Downloads\MYCAR_ACTUALIZAR_TODO.zip" `
        -DestinationPath $mycarActualizacion -Force

    powershell -NoProfile -ExecutionPolicy Bypass `
        -File "$mycarActualizacion\ACTUALIZAR_PROYECTO_COMPLETO.ps1" `
        -AplicarV6Publicada
    if ($LASTEXITCODE -ne 0) { throw "La actualización se detuvo. Comparte el resultado." }

    powershell -NoProfile -ExecutionPolicy Bypass `
        -File "$mycarActualizacion\COMPILAR_E_INSTALAR.ps1"
    if ($LASTEXITCODE -ne 0) { throw "Falló una comprobación. Comparte el resultado." }
}
```

El segundo auxiliar limpia las compilaciones anteriores para retirar clases antiguas, ejecuta pruebas sin utilizar tu BD, compila la app normal, la instala y arranca el backend oficial en otra ventana. Dejar esa ventana abierta. El registro académico local utiliza privacidad 1.0, como ya establece el script de GitHub.

Si se detiene por cambios locales o nuevas revisiones en GitHub, compartir el resultado sin forzar. El proyecto actual, sus respaldos y la BD se conservan. El dump contiene datos privados: conservarlo localmente, no subirlo a GitHub ni al chat.

## Dónde están las funciones

- Inicio → Actualizar: kilometraje.
- Vehículos → Detalle → Actualizar kilometraje.
- Mantenimientos → Registrar → Actualizar kilometraje vigente: abre RF14 y conserva el borrador.
- Alertas → Mis alertas: selector de vehículo si hay varios activos y seis filtros.
- Alertas → Programar alerta: RF22 por tiempo/km/ambos.

La app utiliza datos reales de la cuenta autenticada. La ausencia de vehículos activos muestra el estado vacío correspondiente.

## Verificación

- Código final idéntico al de las dos revisiones GitHub declaradas; todo el Android/backend corresponde a b7116ad, README a main91eb169.
- Android: 237 pruebas JVM aprobadas; APK normal y Kotlin release compilados; lint sin errores. Un único launcher MainActivity.
- Backend: 341 pruebas ejecutadas sin fallos, 184 optativas omitidas.
- HTTP real sobre PostgreSQL temporal: conserva cuenta/vehículo tras alinear V6; RF14 actualiza y repite sin duplicado, RF22 programa y repite sin duplicado, RF23 lista las alertas y RF17/RF18 responden.
- Actualizador ejercitado con PowerShell 7.4.6 Linux: Git, respaldo y compatibilidad PostgreSQL reales; preparación del classpath Maven simulada. Pendiente ejecutar en Windows y probar las pantallas en el emulador del usuario.
- La rama publicada tiene un fallo al compilar su APK de pruebas instrumentadas: el doble `AccionesPantallaDePrueba` no implementa `proponerAceiteVehiculo`. Se conserva el código publicado como pidió el usuario. El APK normal, las pruebas JVM y lint sí compilan/pasan. No se ejecutaron pruebas instrumentadas.

