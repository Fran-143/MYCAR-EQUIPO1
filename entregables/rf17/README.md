# Descargas RF17: backend, API y pantallas Android

La rama de entregables publica archivos para descargar y revisar. No aplica el código al backend ni a la app del equipo.

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
- Faltan identidad/autenticación de Elsa e implementación idempotente confirmada. La API RF17 permanece deshabilitada por defecto; no se desactiva la seguridad ni se inventa un usuario para guardar.
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
La navegación RF14, la coherencia histórica completa y el guardado idempotente siguen pendientes según los acuerdos funcionales y técnicos.

- [Resumen de la entrega inicial](RESUMEN_ENTREGA_RF17.md)
- [Contrato HTTP propuesto](CONTRATO_RF17_PROPUESTO.md)
- [Integración Android y callbacks](INTEGRACION_ANDROID_RF17.md)
- [SHA256 de los ZIP](SHA256SUMS.txt)
