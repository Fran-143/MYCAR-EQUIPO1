# RF18 — Descargas separadas: backend y frontend Android

Parche de Historial de mantenimientos sobre main `e39c0c2`, con RF17 y el login de Elsa integrados.

- [Descargar 01_RF18_BACKEND.zip](01_RF18_BACKEND.zip?raw=true)
- [Descargar 02_RF18_ANDROID.zip](02_RF18_ANDROID.zip?raw=true)
- [Ver código e instrucciones del backend](backend/)
- [Ver código e instrucciones Android](android/)
- [Verificación y límites](VERIFICACION_RF18.json)
- [SHA256 de los ZIP](SHA256SUMS.txt)

## Aplicar en el proyecto original desde PowerShell

Guarda los dos ZIP en Descargas y copia el bloque completo:

```powershell
& {
    $ErrorActionPreference = "Stop"
    $rf18Descargas = "$env:USERPROFILE\Downloads"
    Expand-Archive -LiteralPath "$rf18Descargas\01_RF18_BACKEND.zip" -DestinationPath "$rf18Descargas\RF18_BACKEND" -Force
    Expand-Archive -LiteralPath "$rf18Descargas\02_RF18_ANDROID.zip" -DestinationPath "$rf18Descargas\RF18_ANDROID" -Force
    powershell -NoProfile -ExecutionPolicy Bypass -File "$rf18Descargas\RF18_BACKEND\APLICAR_BACKEND_RF18.ps1"
    if ($LASTEXITCODE -ne 0) { throw "Revisa el resultado del backend antes de continuar." }
    powershell -NoProfile -ExecutionPolicy Bypass -File "$rf18Descargas\RF18_ANDROID\APLICAR_ANDROID_RF18.ps1"
    if ($LASTEXITCODE -ne 0) { throw "Revisa el resultado Android." }
}
```

No abras un segundo proyecto desde Descargas. Los instaladores trabajan en `Documents\MYCAR-EQUIPO1-git`, abren/crean la rama local `feat/historial-mantenimiento-rf18`, verifican las versiones antes de copiar y crean respaldos. La base debe incluir el PR #8 de autenticación; una rama limpia anterior se intenta avanzar por fast-forward. No hacen commit, push ni crean el PR de desarrollo.

## Ver el frontend en el emulador

Después de aplicar Android, con `emulator-5554` abierto:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\RF18_ANDROID\VER_DISENO_RF18.ps1"
```

Esta vista EXCLUSIVA de debug muestra el diseño interactivo real con ejemplos claramente identificados. No hace peticiones ni guarda. Está separada de la navegación normal y de release. Se puede probar el selector, tarjetas, costo NULL/cero y vehículo inactivo sin una sesión.

Para la app normal, usa `ABRIR_ANDROID_RF18.ps1` y pulsa Historial de mantenimientos. La pantalla normal requiere recibir los repositorios autenticados y la clave opaca de cuenta del cliente/navegación compartidos; hasta esa conexión muestra Inicia sesión. No incluye vehículos ficticios en producción ni otro login.

## Alcance y evidencia

- Backend: consultas de vehículos propios ACTIVO/INACTIVO e historial REALIZADO/ANULADO; orden determinista, contador y kilometraje histórico; solo lectura.
- Seguridad RF18: filtro Bearer y sesión real de Elsa, sin idUsuario libre. La API se activa con `mycar.rf18.api-habilitada=true` y utiliza la configuración RF02 del entorno.
- Compatibilidad: tres configuraciones de seguridad de main se limitan a contextos web para permitir las pruebas de servicios RF08 sin HttpSecurity; no se cambian sus reglas web. Ver la guía del backend.
- Frontend: Historial, selector, tarjetas, estados diferenciados y reintento, más navegación Historial → RF17 → Historial.
- Talleres, Kilometraje, detalle RF19, filtros adicionales y exportación conservan su alcance futuro. No se agregan migraciones ni tablas.
- Pasaron **30 pruebas RF18 backend** y **20 pruebas unitarias nuevas Android**. Pasaron también la regresión ampliada (384 ejecutadas, 92 omitidas por condiciones; 42 de esquema ejecutadas aparte), y las 47 unitarias Android anteriores. Debug, Kotlin Release y APK de pruebas compilaron.
- Las **8 pruebas de interfaz están compiladas, todavía no ejecutadas en este entorno**. Incluye un script para ejecutarlas en el emulador de Nayu.
- La conexión completa Android → sesión compartida → API necesita la inyección del cliente autenticado; no se declara terminada por ver la demo o compilar. La integración HTTP autenticada de RF17/RF14 sigue separada.

Esta rama publica paquetes y fuentes de revisión. Los cambios funcionales se aplican localmente mediante los instaladores y después se pueden enviar en el PR manual de RF18. No se fusionó ningún cambio RF18 en main.
