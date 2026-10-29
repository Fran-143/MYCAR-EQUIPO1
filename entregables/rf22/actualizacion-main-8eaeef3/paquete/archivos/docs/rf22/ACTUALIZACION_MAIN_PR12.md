# RF22 con el main del PR #12

Se incorpora main `8eaeef33b5c05e680f433f8e54f67c39f998065d` sobre el RF22 previamente entregado.
Se mantiene el inicio RF50 del equipo, su navegación, permiso de red local de Android 17 y un solo launcher MainActivity en DEBUG y RELEASE.
No se reemplaza RF50 por el inicio anterior de RF22. Alertas → Programar alerta abre el formulario existente; la consulta RF23 sigue pendiente.

Se conserva el AuthenticatedContext(sessionId, version) canónico del PR #12 y la limpieza RF08/RF17 de AuthGraph.
RF22 reutiliza ese contexto y añade su limpieza local antes de retirar las credenciales. Un fallo de limpieza deja CLEAR_FAILED y bloquea otra cuenta.
Se mantienen la corrección de migración tardía RF17 y los almacenes del equipo; no se modifica la política de privacidad ni se crean credenciales paralelas.

El auxiliar scripts/iniciar_backend_rf22.ps1 habilita RF17, RF18 y RF22 en 8081, conserva la clave local compartida y restaura el entorno al terminar.
V1–V5 y V6 RF22 permanecen sin cambios. No se añade otra migración ni se usa la base del equipo para pruebas cloud.

## Abrir la versión actualizada

Con Docker y la base habitual encendidos, detener la ejecución vieja de Spring Boot con Ctrl+C y ejecutar:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git\scripts\iniciar_backend_rf22.ps1"
```

El registro de cuenta nuevo usa la política de privacidad aprobada del equipo. Para las pruebas con el documento académico DEBUG 1.0 existente, el auxiliar acepta -VersionPrivacidad "1.0"; las cuentas existentes no requieren ese parámetro.

En otra terminal, con el emulador iniciado, ejecutar ABRIR_MYCAR_ACTUALIZADO.ps1 del ZIP de actualización.
Abrir la bienvenida real, permitir la red local si Android lo solicita, iniciar sesión y navegar por Inicio / Vehículos / Mantenimientos / Alertas.
En Alertas pulsar Programar alerta. La entrada es real: necesita una cuenta y un auto propio activo guardado.
La demostración de vehículos no crea datos reales. El diseño RF22 con ejemplos sigue disponible explícitamente en DEBUG.

No usar los instaladores antiguos APLICAR_*_RF22.ps1 sobre este main: están fijados a otra base y se detendrán por diferencias.
Los auxiliares VER_DISENO_RF22.ps1 previos pueden seguir abriendo ejemplos; ABRIR_MYCAR_ACTUALIZADO.ps1 abre el inicio nuevo y gestiona el permiso local.

## Verificación

Android: 237 pruebas JVM aprobadas, incluidas 29 RF22; APK debug y de instrumentación, Kotlin release y lint sin errores.
Backend: 368 pruebas ejecutadas sin fallos y 172 optativas omitidas. Incluye 39 RF22 (19 PostgreSQL) y ocho de la integración de sesión/vehículos.
HTTP real: login, acceso a los cuatro módulos, confirmación y repetición sin duplicado, consulta de operación e historial inicial único.
Las pruebas instrumentadas compilaron; no se ejecutaron en un emulador cloud. Validar el recorrido en el PC después de actualizar.
