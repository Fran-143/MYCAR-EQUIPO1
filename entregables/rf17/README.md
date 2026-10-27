# Descargas RF17: backend, API y pantallas Android

La rama de entregables publica archivos para descargar y revisar. No aplica el código al backend ni a la app del equipo.

## Correcciones del backend tras la revisión del PR #6

- [Descargar RF17_CORRECCIONES_BACKEND.zip](RF17_CORRECCIONES_BACKEND.zip?raw=true).
- [Ver el código completo y el instalador PowerShell](correcciones-backend/).
- [Instrucciones y alcance](correcciones-backend/LEEME_RF17.md).
- [Comparación con el backend.zip recibido](correcciones-backend/CAMBIOS_RF17.diff).
- [Resultados de las pruebas](correcciones-backend/VERIFICACION.json).

Este parche parte del `backend.zip` adjuntado por Nayu. Corrige la duplicación de mantenimientos al repetir una operación y la validación cronológica incompleta en modalidad LECTURA. Incluye 18 archivos Java completos y un instalador que verifica las versiones, hace un respaldo y ejecuta las pruebas unitarias en el proyecto original.

Se verificaron 102 pruebas: 76 unitarias y 26 con PostgreSQL 17.11, sin fallos ni omisiones. También se verificó el arranque Spring y HTTP health UP. Las pruebas se compilaron para Java 21 y se ejecutaron con JDK 25 en el entorno de revisión. El instalador PowerShell queda para ejecución en el Windows de Nayu.

**Esta publicación permite descargar y revisar las correcciones. No actualiza la rama de trabajo de Nayu ni el PR #6.** El parche todavía debe aplicarse en su PC, integrarse con `main` y pasar la revisión del equipo. La API sigue deshabilitada por defecto y falta integrar el login real; no se ha verificado un registro HTTP autenticado ni el flujo completo Android → API → PostgreSQL.

## Actualización: pantallas RF17

- [Descargar RF17_PANTALLAS_ANDROID.zip](RF17_PANTALLAS_ANDROID.zip?raw=true).
- [Ver todos los archivos fuente e instaladores](pantallas-android/).
- [Instrucciones, rutas completas, validación y pendientes](pantallas-android/LEEME_PANTALLAS_RF17.md).

El paquete contiene el formulario real, diálogo de repuestos, resultado, estados de carga/error/cancelación y entrada desde Inicio. Los componentes reales están en `src/main`; los ejemplos interactivos están separados en Preview y pruebas.

Descomprime el ZIP completo en Descargas, carpeta `RF17_PANTALLAS_ANDROID`, y con el emulador abierto ejecuta:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\RF17_PANTALLAS_ANDROID\INSTALAR_PANTALLAS_RF17.ps1"
```

El instalador verifica las versiones conocidas, respalda los archivos anteriores, compila e instala en `emulator-5554`. En Inicio pulsa **Registrar mantenimiento**. Sin sesión real se muestra el formulario con controles bloqueados.

Los 11 archivos también se pueden revisar/copiar manualmente en `pantallas-android/archivos/`. No se publican APK firmados con claves del entorno.

## Relación con el backend

- La comprobación de conexión de la app ya mostró «Servidor disponible» en el emulador de Nayu. Eso comprueba `/actuator/health`.
- RF17 dispone de adaptador HTTP y una entrada preparada para recibir el repositorio autenticado y una clave opaca de cuenta.
- **El registro completo Android → API RF17 → PostgreSQL todavía no está conectado.** La entrada actual no recibe las dependencias de la sesión real.
- Falta integrar identidad/autenticación de Elsa. El nuevo parche de backend incluye una implementación idempotente RF17 para revisión del equipo; la entrega inicial no la tenía. La API RF17 permanece deshabilitada por defecto.
- Los Preview simulan únicamente interacciones visuales, con un rótulo explícito. No llaman al backend ni guardan datos en PostgreSQL.

Las pantallas y el APK de pruebas compilaron. Las cuatro pruebas de interfaz no se ejecutaron en un emulador del entorno de revisión. Una compilación o un Preview no dan RF17 por terminado.

## Entrega inicial

Los tres ZIP iniciales se basan en `feat/mantenimientos-alertas-rf17-rf27`, commit `a5d2264`; no incorporan los cambios posteriores de main:

- [01_RF17_BACKEND.zip](01_RF17_BACKEND.zip?raw=true): backend completo, con las pruebas existentes conservadas.
- [02_RF17_API.zip](02_RF17_API.zip?raw=true): copia de revisión de la capa HTTP; depende del backend.
- [03_RF17_ANDROID.zip](03_RF17_ANDROID.zip?raw=true): Android inicial con los componentes RF17. La navegación al formulario se agrega con la actualización de pantallas anterior.

Si GitHub muestra la vista del archivo, usa **Download raw file**, el icono de descarga.
No es necesario descargar otra vez todo el repositorio. Los instaladores no ejecutan comandos Git ni cambian la BD.

Se mantienen las migraciones V1–V4 y las pruebas existentes. Los paquetes no contienen .env, credenciales, .git, compilados ni cachés.
La entrega inicial conserva los pendientes documentados en su momento. El nuevo parche corrige los dos problemas del backend señalados en el PR #6. La navegación RF14, el historial RF18 y la integración de sesión/contrato compartido siguen requiriendo trabajo.

- [Resumen de la entrega inicial](RESUMEN_ENTREGA_RF17.md)
- [Contrato HTTP propuesto](CONTRATO_RF17_PROPUESTO.md)
- [Integración Android y callbacks](INTEGRACION_ANDROID_RF17.md)
- [SHA256 de los ZIP](SHA256SUMS.txt)
