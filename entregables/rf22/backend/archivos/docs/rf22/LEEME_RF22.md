# RF22 — Programar alerta preventiva manual

Base revisada: main 529927a, con RF18 (PR #10) y acceso Android RF01/RF02/RF06 de Elsa (PR #11).
Implementación Java 21/Spring Boot/PostgreSQL 17 y Kotlin/Compose. Las rutas nuevas se publican en
/api/v1/rf22, en namespace separado para revisión del equipo, activadas con mycar.rf22.api-habilitada=true.
No se presentan como un contrato REST congelado por las compañeras.

## Qué incluye

Backend: contexto propio activo, catálogo activo compartido, última lectura efectiva RF08/RF14,
sugerencias OTRO, antecedente REALIZADO opcional, revisión autoritativa y confirmación.
Fecha America/Lima; semanas de siete días; meses calendario desde la base original; objetivos futuros.
Revalidación al confirmar: fecha, lectura, vehículo, tipo y referencia. Cambio relevante => nueva revisión.
Alerta MANUAL/MANTENIMIENTO + historial v0 + respuesta idempotente en la misma transacción Spring.
Se reutiliza operacion_idempotente y OperacionVehiculoDao para PROGRAMAR_ALERTA.
Firma canónica SHA-256 construida en servidor; OTRO visible intacto, normalización Unicode de PostgreSQL
para comparación. El índice parcial existente protege las cuatro clases de estado activo.
No hay un almacenamiento paralelo de idempotencia ni escrituras a kilometraje desde RF22.

Android: entrada real con el login RF02 existente, formulario, selección de auto/tipo, OTRO y sugerencias
locales, dos datos actuales no editables, referencia separada, criterio, intervalos, resumen y resultado.
Operación conservada antes de POST en noBackupFilesDir por cuenta validada. Timeout => consultar y, solo
si corresponde, reenviar la misma clave/cuerpo. Una consulta NO_ENCONTRADA no demuestra rollback.
La sesión pertenece al AuthGraph/AuthRepository/KeystoreCredentialStore que Elsa integró en main.
RF22 no crea otra pantalla de login, otro cliente HTTP ni otro almacén de credenciales. Reutiliza AuthGate
(inicio de sesión/registro/cierre), y extiende mínimamente el transporte común para enviar solicitudes
protegidas sin exponer tokens a pantallas. La renovación se hace en RF02 antes del envío, nunca se repite
un POST automáticamente y las respuestas de una sesión retirada se descartan.
Los archivos de operación de RF22 no contienen contraseñas ni tokens. Un bloqueo local compartido con
RF06 impide recrearlos después del cierre. Un hook genérico limpia temporales RF22 incluso si su pantalla
ya se cerró; no modifica archivos RF17, vehículos ni filas de PostgreSQL.
La revalidación temporal RETRY no equivale a logout: conserva la operación para conciliarla al recuperar
la sesión. La creación de cuentas sigue la política y configuración legal existentes de Elsa.

## Migración V6

V1–V5 permanecen idénticas. V6 reemplaza únicamente tr_validar_alerta(): condiciona la exigencia de lectura
a POR_KILOMETRAJE/AMBOS. No altera tablas ni filas ni la función sincronizar_alertas_vehiculo de RF23.
No es alcanzable normalmente un RF08 confirmado sin su lectura inicial, pero CP02 exige soportar esa
condición excepcional para tiempo. Los criterios con km no inventan una lectura para superarla.
El instalador copia V6 y ejecuta SOLO pruebas unitarias. La BD real cambia únicamente cuando arrancas
Spring Boot con Flyway. Si el equipo ya incorporó otra V6, el instalador debe detenerse: no renombrar ni
sobrescribir migraciones sin revisar el historial con el equipo.
El test compartido de esquema ajusta solo dos cantidades esperadas por la migración adicional.

## Lo que continúa fuera de esta entrega

RF23 no tiene todavía su listado/API/host completo en main. El callback Ver alertas está preparado;
en el host actual aparece deshabilitado «Próximamente». No se muestra una lista ficticia como consulta real.
La incompatibilidad de sincronización RF23 sin lectura se mantiene documentada para su propia entrega.
RF17/RF18 conservan la inyección de sus repositorios Android pendiente; implementar RF22 no la completa automáticamente.
EA11/notificaciones internas y push, scheduler, SOAT, licencia, RF62 administrativo y alertas desde RF17
permanecen fuera del MVP. No hay una operación nueva para marcar alertas REALIZADA.

## Instalación por PowerShell

Descarga 01_RF22_BACKEND.zip y 02_RF22_ANDROID.zip. Extrae cada uno en carpetas diferentes de Descargas.
Ejecuta primero APLICAR_BACKEND_RF22.ps1 y después APLICAR_ANDROID_RF22.ps1.
Ubicación predeterminada: C:/Users/PROPIETARIO/Documents/MYCAR-EQUIPO1-git.
Ambos aceptan -Repositorio para una ubicación diferente y trabajan en feat/programar-alerta-rf22.
Si falta PR #11, el instalador crea tu rama RF22 y descarga/avanza únicamente hasta el main revisado
529927a con --ff-only, siempre con árbol limpio. No cambia la rama main ni la rama RF18. Si hay
divergencias o trabajo local se detiene; no resuelve conflictos ni descarta archivos.
Los manifiestos comprueban TODOS los archivos RF22 antes de copiarlos; admiten el original revisado o la versión
exacta ya instalada. Hay respaldo en Documentos. No hacen commit, push, fusiones con conflictos, reset, cambios de .env ni
borrado de datos. No se debe usar git add .; revisar el manifiesto de cada entrega al preparar el PR.
No uses una carpeta extraída del proyecto como destino ni copies otro proyecto sobre el original.

Si el instalador informa diferencias, compartir el archivo indicado; no quitar la comprobación ni forzar.
Si tienes cambios sin guardar en otra rama, conservarlos antes de crear la rama RF22.
Android Studio: Sync Now si lo solicita. Se conserva la entrada DEBUG de vehículos de Francesca.
Run/Default Activity puede abrir esa demostración; ABRIR_RF22_REAL.ps1 abre RF22 explícitamente.

## Ver el frontend sin backend

Con el emulador encendido, ejecuta VER_DISENO_RF22.ps1, incluido solo en el ZIP Android.
Abre la misma pantalla de producción con datos de ejemplo y un cartel visible.
Puedes recorrer formulario, resumen y confirmación simulada. No hay HTTP ni guardado de BD en ese modo.
La clase de ejemplos vive en src/debug y no existe en release.

## Guardar una alerta real

1. Instala ambos paquetes. Docker Desktop y la base normal del equipo deben estar disponibles.
2. Si Spring ya funciona en 8081 en otra terminal, detén ESA ejecución con Ctrl+C antes de usar el auxiliar.
3. Desde la raíz del proyecto:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\iniciar_backend_rf22.ps1
```

El auxiliar conserva una clave local de backend fuera de Git, compatible con el auxiliar previo del equipo,
y habilita RF22. PT5M/P1D son duraciones de desarrollo local, no una política de despliegue.
No imprime ni publica claves. Conserva tu conexión actual de PostgreSQL de .env.
Para cuentas existentes no necesitas una versión legal adicional. Para registrar una cuenta desde el acceso
de Elsa, configura en el backend la versión de su política aprobada. El DEBUG de main incluye el documento
académico local versión 1.0; si el equipo lo usa para esta prueba, inicia con -VersionPrivacidad "1.0".
Esto no cambia el documento ni configura una política de producción.
4. En otra terminal comprueba http://localhost:8081/actuator/health.
5. Ejecuta ABRIR_RF22_REAL.ps1 desde el paquete Android. Entra con una cuenta MyCar existente que tenga
un vehículo propio ACTIVO. RF08 confirmado ya debe tener lectura INICIAL; no necesitas ejecutar RF14 antes.
6. Selecciona tipo y criterio, introduce intervalos, revisa y confirma.
7. Cierra/reabre la entrada o recupera una operación tras interrupción: el backend conserva la respuesta.

El endpoint health UP demuestra disponibilidad, no valida por sí solo el guardado de RF22.
El auxiliar no arranca ni modifica contenedores del equipo; Docker debe tener la base correspondiente.

## Contrato implementado, pendiente de revisión del equipo

- GET /api/v1/rf22/contexto
- GET /api/v1/rf22/vehiculos/{id}/antecedentes?idTipoMantenimiento=...&servicioElegido=... (opcional)
- POST /api/v1/rf22/revisiones: Configuracion.
- POST /api/v1/rf22/alertas: {idOperacion,revision}.
- GET /api/v1/rf22/operaciones/{idOperacion}.

Usuario siempre desde SessionAuthentication.Principal del filtro real de Elsa, nunca de JSON/query.
No se acepta Basic, principal de texto ni un idUsuario libre.
El DEBUG usa 10.0.2.2:8081, conservando el override mycar.apiBaseUrl del build de Elsa. DTO fecha ISO; IDs/intervalos JSON enteros.
Todas las respuestas son no-store. 400 datos; 401 sesión; 404 vehículo; 409 duplicidad/revisión/tipo/operación;
422 falta de km; 503 resultado por comprobar. Los errores no contienen SQL ni credenciales.

## Verificación en tu equipo

PROBAR_POSTGRES_RF22.ps1 (ZIP backend) crea una base PostgreSQL 17 temporal, ejecuta las pruebas
RF22 y retira solo su contenedor. No usa .env ni mycar_mvp como destino de pruebas.
PROBAR_PANTALLAS_RF22.ps1 (ZIP Android) ejecuta cinco pruebas instrumentadas en emulator-5554.
Ver VERIFICACION_RF22.json para los resultados de cloud. Las pruebas del emulador deben ejecutarse
en tu PC; compilar el APK de tests no significa que hayan sido ejecutadas.
