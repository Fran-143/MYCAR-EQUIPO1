# API RF08 — implementación técnica y contrato propuesto

27 de octubre de 2026. Responsable: Francesca. Base de implementación: `feat/vehiculos-mvp`, commit `538e8a2`.

Se añaden el controlador, manejo de errores y consultas auxiliares del registro. No modifica migraciones, configuración de seguridad, pom, autenticación de Elsa ni Android. El servicio de registro transaccional que pasó las 107 pruebas se conserva.

Los nombres y estados HTTP que siguen son una propuesta técnica implementada y probada para revisar con el equipo. No convierten en aprobados los contratos anteriores ni alteran diagramas funcionales congelados.

## Rutas

| Método | Ruta | Finalidad | Resultado correcto |
|---|---|---|---|
| GET | `/api/v1/vehiculos/catalogos` | Cargar opciones reales de uso, motor, combustible y aceite | 200 con DatosCatalogosVehiculo |
| GET | `/api/v1/vehiculos/cupo-registro` | Comprobar si se puede abrir el formulario RF08 | 200 con plan, limite, registrados y puedeRegistrar |
| POST | `/api/v1/vehiculos` | Registrar de forma atómica usando el servicio existente | 201 con ResultadoRegistroVehiculo |
| GET | `/api/v1/vehiculos/operaciones/{idOperacion}` | Consultar el resultado propio de RF08 sin nuevas escrituras | 200 con idOperacion, estado y resultado |

No implementa el listado RF09 ni el detalle RF10. GET `/api/v1/vehiculos` todavía no es un listado disponible.

Todos los identificadores de usuario/vehículo/lectura son Long en Java, coherentes con BIGINT PostgreSQL. idOperacion es UUID. Las fechas del resultado se representan mediante Instant; con la configuración actual de Spring Boot se serializan en ISO-8601 UTC.

## Identidad pendiente de Elsa: comportamiento por defecto

`IdentidadVehiculos` es un puerto Java con el método:

```java
long exigirUsuarioAutenticado(Authentication autenticacion);
```

Elsa o el adaptador que acordemos aportará un único bean Spring que lo implemente. Debe validar realmente la sesión (vigente, no revocada, no expirada), resolver el identificador del usuario y lanzar `SesionVehiculosNoValidada` si no es válida. La revisión de estado ACTIVA permanece además en los servicios del dominio.

El controlador exige Authentication no nulo, autenticado y no anónimo; después necesita ese bean. Sin él rechaza, aunque Spring tenga el usuario de desarrollo. No interpreta `authentication.getName()` como idUsuario ni acepta identidad desde JSON, query o cabeceras libres.

**No se incluye implementación ficticia de autenticación en producción.** Los dobles viven exclusivamente en tests MVC. No se cambió SecurityFilterChain ni CSRF. Con la seguridad actual, una solicitud anónima puede recibir 401/403 desde los filtros antes de llegar al controlador. El contrato final de autenticación, las respuestas de filtros y el mecanismo CSRF/tokens debe acordarse con Elsa; no desactivar filtros para conseguir un alta temporal.

## Consulta previa de cupo

Ejemplo con cuenta sin vehículos y Free vigente:

```json
{"plan":"FREE","limite":1,"registrados":0,"puedeRegistrar":true}
```

Incluye ACTIVO e INACTIVO. No reserva cupo, inserta operación ni crea plan. Si false, no abrir el formulario conforme a RF08 EA01. Si falta una suscripción operativa, devuelve el error PLAN_FREE_NO_DISPONIBLE; un error técnico no se transforma en cupo disponible ni cero vehículos.

El POST siempre revalida el cupo dentro de su transacción. Una consulta previa no garantiza que siga disponible al confirmar.

## Solicitud de registro

Usa el record `DatosRegistroVehiculo` existente, con IDs reales obtenidos de los catálogos. Campos:

- idOperacion UUID; marca; modelo; usoVehiculoCodigo; placa; anioFabricacion; color.
- idTipoMotor Long; idCombustible Long cuando aplique; kilometrajeInicial entero positivo.
- Opcionales: numeroVin; numeroSerie; numeroMotor; idTipoAceite; tipoMotorOtro; combustibleOtro; tipoAceiteOtro.

Ausencia de campos opcionales = null. No enviar vacío como sustituto de null al backend. Con ELÉCTRICO, combustible, aceite y número motor y sus textos OTRO son null. Se conservan todas las validaciones existentes de placa, año, VIN, serie y motor. No se calcula ni se acepta hash desde Android.

No hay un campo idUsuario en este record. La prueba incluye un idUsuario extra y cabeceras/query inventadas para comprobar que nunca reemplazan al propietario resuelto por el servidor.

El POST devuelve 201 también para un reintento confirmado; conserva los valores del resultado almacenado. Esta decisión HTTP es técnica y debe compartirse con el equipo. La confirmación con Ver mis vehículos / Ir a inicio navega y no repite el POST.

## Recuperación tras una respuesta perdida

GET de operación filtra por idOperacion + usuario + tipo REGISTRAR_VEHICULO. No bloquea con FOR UPDATE, no cambia estado ni recalcula cupo. No revela respuesta ajena.

- CONFIRMADA: devuelve el resultado original. Android muestra confirmación sin crear otra alta.
- EN_PROCESO: resultado null; no equivale a éxito. Mantener verificación sin generar otro UUID.
- 404: ausencia visible para ese usuario/tipo. **No demuestra rollback:** la transacción original podría no haber confirmado todavía y sus filas no ser visibles. Conservar clave y contenido; ante reenvío usar exactamente la misma operación.
- Error técnico/timeout: resultado incierto; no afirmar éxito ni generar otra operación.

Para corregir datos tras un rechazo funcional inequívoco puede iniciarse una solicitud nueva; no cambiar el contenido de una clave cuyo resultado siga siendo incierto.

## Respuestas de error propuestas

JSON: `{"codigo":"...","mensaje":"...","campos":[...]}`.

| HTTP | Códigos |
|---|---|
| 400 | DATOS_INVALIDOS, SOLICITUD_INVALIDA |
| 401 | SESION_NO_VALIDA desde el controlador/adaptador; filtros pendientes de Elsa |
| 403 | CUENTA_NO_AUTORIZADA; también puede provenir de filtros actuales |
| 404 | OPERACION_NO_ENCONTRADA, para ausencia/ajena/otro tipo por igual |
| 409 | PLAN_FREE_NO_DISPONIBLE, LIMITE_FREE, PLACA_DUPLICADA, VIN_DUPLICADO, CONFLICTO_REGISTRO, CONFLICTO_IDEMPOTENCIA, OPERACION_EN_PROCESO |
| 503 | SERVICIO_NO_DISPONIBLE |
| 500 | ERROR_INTERNO |

No devuelve SQL, stacktrace ni mensajes técnicos de las excepciones. El advice aplica solo a VehiculosController; no altera los errores de los módulos de otros integrantes.

## Pruebas

`py -3 ..\scripts\test_rf08_api.py`, desde backend, ejecuta la suite DB existente y, sobre el mismo PostgreSQL desechable, validaciones y pruebas de servicio + consultas + MVC. Después verifica contexto Spring, arranque del JAR, health UP y que las cuatro rutas rechacen peticiones HTTP anónimas (401/403).

Las pruebas MVC usan identidad/servicios simulados para verificar el contrato y no acreditan el login real. Las diez consultas nuevas se prueban con Spring y PostgreSQL reales. Una prueba con otra conexión comprueba que una operación no confirmada puede ser invisible para GET, de ahí que 404 no se trate como rollback.

## Siguiente bloque independiente

Formulario Android con campos obligatorios, opciones OTRO/ELÉCTRICO, datos opcionales contraídos, validación local y estado de reintento. Los ejemplos de catálogo solo podrán usarse en previews/tests; el formulario de ejecución debe recibir catálogos y cupo reales tras autenticar. La aceptación completa requiere el adaptador de Elsa y el acuerdo Free de Sebastián.
