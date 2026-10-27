# Correcciones del backend RF17

Paquete preparado a partir del backend.zip adjuntado por Nayu. Son cambios sobre el backend existente: no es un proyecto nuevo.

## Aplicar en Windows PowerShell

1. Guarda RF17_CORRECCIONES_BACKEND.zip en Descargas.
2. Ejecuta:

```powershell
$rf17Zip = "$env:USERPROFILE\Downloads\RF17_CORRECCIONES_BACKEND.zip"
$rf17Carpeta = "$env:USERPROFILE\Downloads\RF17_CORRECCIONES_BACKEND"
Expand-Archive -LiteralPath $rf17Zip -DestinationPath $rf17Carpeta -Force
powershell -NoProfile -ExecutionPolicy Bypass -File "$rf17Carpeta\APLICAR_CORRECCIONES_RF17.ps1"
```

El instalador valida el contenido de los 18 archivos contra el ZIP revisado o la versión corregida. Si encuentra otras modificaciones, se detiene antes de copiar. Hace un respaldo fuera del repositorio, copia solamente esos archivos y ejecuta las 76 pruebas unitarias. No realiza commits, push, merge, cambios Android ni cambios de migraciones. No modifica la configuración de conexión ni el Docker de tu PC. Las pruebas PostgreSQL no se ejecutan automáticamente en tu PC.

## Cambios

- RegistroMantenimientoService: reserva idOperacion y bloquea su fila; comprueba propietario, tipo REGISTRAR_MANTENIMIENTO y huella SHA-256 del contenido. Si ya está confirmada, devuelve el snapshot almacenado antes de revalidar catálogos, vehículo o kilometraje. En una operación nueva, valida y guarda mantenimiento, repuestos, asociaciones y confirmación de la operación en la misma transacción.
- OperacionMantenimientoJdbcDao: implementa el DAO existente usando la tabla existente mycar.operacion_idempotente; INSERT ON CONFLICT y SELECT FOR UPDATE impiden duplicar una misma operación concurrente. respuesta_json conserva el resultado confirmado.
- JsonOperacionMantenimiento: formato de huella RF17 v1 independiente de la configuración HTTP. Incluye vehículo, todos los datos del mantenimiento, observaciones y repuestos en orden; normaliza escala de importes y lista omitida/vacía. El UUID identifica la operación y no forma parte del contenido comparado.
- RegistroIdempotenteMantenimientoService conecta el puerto API existente con el servicio transaccional. MantenimientoHttpService delega la validación al servicio para no invalidar resultados ya confirmados. Los conflictos de reutilización se devuelven como HTTP 409, sin revelar resultados de otro usuario.
- LecturaKmMantenimientoService: extrae la comprobación cronológica común y la aplica a LECTURA y a HISTORICO_MANUAL. Mantiene las comprobaciones específicas de la lectura y la zona America/Lima. Conserva las reglas anteriores de días estrictamente anteriores/posteriores; no inventa una nueva política para lecturas del mismo día.
- Se actualizan la configuración de beans, los tipos asociados y cuatro archivos de pruebas, incluidos sus constructores.

No hace falta cambiar V1, V2, V3 ni V4: la tabla de operaciones ya existe.

## Verificación realizada en este entorno

102 pruebas ejecutadas: 76 unitarias y 26 con PostgreSQL 17.11 real, todas sin fallos, errores ni omisiones. Son las 86 pruebas anteriores más 16 nuevas. El compilador produce bytecode para Java 21; la ejecución de estas pruebas en este entorno usó JDK 25.

Casos nuevos: repetición de la misma solicitud (incluidos repuestos), contenido diferente, usuario o tipo distintos, dos envíos simultáneos, conservación del snapshot con vehículo posteriormente inactivo, rollback al fallar la confirmación y reintento con el mismo UUID, lectura antigua incompatible con el día del servicio y servicio histórico coherente. Las pruebas unitarias también verifican el límite posterior en modalidad LECTURA y que un reintento confirmado no revalide ni inserte.

El arranque directo de Spring Boot con las clases compiladas y la base temporal también se verificó: HTTP 200 y `{"status":"UP"}` en `/actuator/health`. No se probó el login ni un registro HTTP autenticado.

## Pendientes de integración

Este paquete no incorpora el main actual: parte del ZIP recibido. Antes de actualizar el PR #6 hay que revisar la integración con main y ejecutar la regresión correspondiente. No se hizo push ni se fusionó el PR.

La API RF17 mantiene su activación por propiedad deshabilitada por defecto. Sigue pendiente conectar IdentidadMantenimiento con el login real de Elsa. La aprobación del contrato API compartido y la conexión final Android/sesión se revisan aparte. Estas correcciones resuelven los dos casos del backend; no significan que RF17 completo, RF18 ni el resto de requisitos estén terminados.

## Revisión del código

archivos/backend/src contiene los archivos Java completos; CAMBIOS_RF17.diff contiene la comparación con el ZIP recibido. MANIFIESTO.json registra los hashes de texto originales y nuevos. El formato JSON/huella de RF17 debe revisarse con el equipo al integrar el contrato compartido.
