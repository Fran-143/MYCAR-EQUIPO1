# Archivos que dependen del contrato SQL

Rutas relativas al repositorio compartido; hashes exactos de los seis archivos nuevos copiados en
FUENTES_SHA256.json. Las líneas corresponden a esta copia, no necesariamente a main futuro.

| Archivo | Dependencia |
|---|---|
| backend/src/main/java/backend/registro/verificacion/VerificationService.java | SQL directo de las cuatro tablas; emit/cancel/abandon/complete/state, transiciones, resultados, finalidad REGISTRO, revisión, horas, inserción usuario.correo_verificado_utc y mantenimiento. receipt() (~47), emit() (~56), complete() (~136), authorizeDispatch() (~213), maintain() (~268). Mantener columnas y catálogos exactos. |
| backend/src/main/java/backend/registro/verificacion/VerificationRepository.java | operation/emission/current/receipt/lockOrigin/lockEmails/lockOperation/email/ensureEmail/close (~31–57). Necesita clave única del sujeto/correo, PK de operación, unicidad de recibo y estados RESERVADO/ACTIVO; JdbcTemplate + TransactionTemplate de DataSourceTransactionManager. |
| backend/src/main/java/backend/registro/verificacion/VerificationPolicy.java | technical(), emissionLimit(), wrongCode(), clearFailures(). JOIN de emisiones/operaciones por operación+finalidad; cuenta estados de cupo, fechas y controles. No convertir contadores o ventanas a otro significado sin adaptar esta clase. |
| backend/src/main/java/backend/registro/verificacion/VerificationCrypto.java | BYTEA de 32 bytes para HMAC, nonce 12 bytes, payload AES-GCM y contexto REGISTRO/operación/emisión/correo/generación. Cuatro claves externas separadas; versiones iniciales 1/v1, sin keyring. No cambiar normalización/contexto sin invalidar códigos/huellas existentes. |
| backend/src/main/java/backend/registro/verificacion/VerificationDispatcher.java | claim → payload → authorizeDispatch → MailGateway → deliveryResult. Depende de lease, reserva, fechas fijas y estados; IO fuera de TX. No está programado ni expuesto públicamente. |
| backend/src/main/java/backend/registro/verificacion/MailGateway.java | Contrato Accepted/Rejected/Uncertain y Message; idempotencyKey por emisión. No adapter productivo. Rejected(true) excluye aceptación de toda la emisión. |

## Código preexistente reutilizado (no copiado en el ZIP)

- backend/src/main/java/backend/registro/RegisterAccount.java: register() transaccional actual; validador,
  privacidad, correo disponible, encoder Argon2 y store. El nuevo servicio lo reutiliza dentro de TX exterior.
- backend/src/main/java/backend/registro/RegistrationValidator.java y RegistrationPrivacyPolicy.java:
  validaciones existentes, normalización y versión legal; ninguna regla Gmail exclusiva.
- backend/src/main/java/backend/registro/RegistrationStore.java y persistence/JdbcRegistrationStore.java:
  usuario, plan codigo='FREE' activo, suscripcion ACTIVA, config_recordatorio_km_usuario PAUSADO,
  preferencia_notificacion ON/ON/ON/NO_SOLICITADO. Clasificación de duplicado por SQLSTATE 23505 y nombre
  `uq_usuario_correo_normalizado`: preservar o adaptar expresamente ese nombre preexistente.
- backend/src/main/java/backend/registro/web/RegistrationController.java y
  RegistrationSecurityConfiguration.java: recorrido antiguo conservado; no exigen OTP actualmente.
- backend/src/main/resources/application.properties: Flyway solo classpath:db/mvp. No entregar ni
  copiar configuración local a otro equipo; revisar configuración efectiva antes de cualquier aplicación.

## Pruebas / documentación locales (identificadas, no copiadas: se excluyen fixtures)

- backend/src/test/java/backend/registro/verificacion/VerificationPostgresTest.java: 36 casos, DB
  desechable, V1–V6/cuenta anterior/V7, concurrencia y transacciones reales; ubicación Flyway y conteo 7
  deben revisarse al integrar. Sus contadores/fechas esperadas reflejan las políticas provisionales actuales.
- backend/src/test/java/backend/registro/verificacion/ControlledMailGateway.java: doble exclusivo de tests.
- docs/diseno/RF01_verificacion_etapa1.md: especificación/estado local, contiene contexto de pruebas;
  no se copia para evitar transportar datos o identificadores de fixtures innecesarios.
- Pruebas existentes revisadas: RegistrationIntegrationTest (12), RegisterAccountTest (4),
  RegistrationValidatorTest (25), RegistrationWebTest (3). No están incluidas en esta entrega de DDL.

Ninguna tabla nueva es registro_pendiente. No hay almacén adicional oculto de formulario o contraseña.
Los nombres de índices nuevos están en DICCIONARIO; Java no captura sus nombres en mensajes de error:
su estructura/unicidad es el contrato relevante. Las restricciones PK/FK/CHECK sin nombre explícito en
V7 reciben nombre automático de PostgreSQL; no inventar nombres contractuales como si estuvieran en el SQL.
