# MyCar · Integración del DDL con Spring Boot (MVP)

Esta guía distingue **las operaciones transaccionales del backend** de las FK y los triggers protectores de PostgreSQL. No crear procedimientos automáticos adicionales que dupliquen estas escrituras. El backend verifica al usuario por **sesión validada**, nunca por `id_usuario` libre enviado por Android. Todos los POST usan transacciones ACID.

## RF01 · Registrar usuario (una sola transacción)

1. Validar nombre, apellido, nacimiento 18–85, contraseña/confirmación, correo único y aceptación de privacidad.
2. `BEGIN`; insertar `usuario(estado_cuenta='ACTIVA', codigo_rol='USUARIO', ... consentimientos...)` y recuperar `id_usuario`.
3. Obtener `plan.id_plan WHERE codigo='FREE' AND activo` e insertar `suscripcion(... estado='ACTIVA',fin_utc=NULL)`.
4. Insertar `preferencia_notificacion` con ON/ON/ON/NO_SOLICITADO e insertar `config_recordatorio_km_usuario(PAUSADO)`.
5. `COMMIT` únicamente cuando las cuatro entidades estén completas. Cualquier error = `ROLLBACK`. **No trigger para crear el plan**. Tampoco crear sesión automáticamente.

## RF02 · Iniciar sesión (y fallos de contraseña)

- Antes de verificar hash, consultar si `usuario.estado_cuenta='ACTIVA'` y `login_bloqueo_hasta_utc > now()`. Si está restringido, no verificar hash ni registrar intento.
- Si contraseña incorrecta, llamar `mycar.registrar_fallo_login(id_usuario)`; usa lock de usuario, inserta intento, cuenta fallos de últimos 10 minutos y establece diez minutos adicionales al tercer fallo. Mantener error público genérico.
- Si contraseña correcta: `BEGIN`; bloquear `usuario FOR UPDATE`; comprobar ACTIVA y no restringido **de nuevo**; marcar `sesion` expiradas como `EXPIRADA` y anterior VIGENTE en la misma instalación como `REVOCADA` con `revocada_utc`; insertar nueva sesión `VIGENTE` con UUID de instalación y hash de token de renovación, expiración válida; `COMMIT`. No revocar otras instalaciones. La API debe validar sesión, expiración y estado ACTIVA en cada petición protegida.
- Una sesión VIGENTE expiró físicamente al pasar el tiempo: a efectos de autorización ya no es válida aunque no se haya actualizado aún la columna. Marcarla EXPIRADA antes de crear una nueva con la misma instalación.

## RF06 · Cerrar sesión

- Android limpia credenciales localmente de inmediato. El backend intenta revocar **solo la sesión presentada** marcándola REVOCADA y fijando `revocada_utc`; si la conexión falla, NO indicar que la revocación remota fue confirmada.

## Operación idempotente común para RF08, RF14 y extensión RF17/RF22

1. Autenticar y resolver el UUID `idOperacion`, tipo y hash SHA-256 **de payload canónico**. Este paso debe ocurrir **ANTES** de revalidar límite Free o odómetro para poder devolver un resultado confirmado previamente.
2. `BEGIN`; buscar la operación por PK global con bloqueo `FOR UPDATE` o intentar insertar una fila `EN_PROCESO` y resolver `ON CONFLICT`. Comprobar propietario, tipo y hash. Si existe CONFIRMADA con la misma solicitud, devolver `respuesta_json` sin ninguna segunda escritura; si hash distinto, error de conflicto; si EN_PROCESO persistente, reconciliar/no duplicar.
3. Si es NUEVA: bloquear usuario y/o vehículo según RF, revalidar autorización, estado, cupo y dato vigente; crear todos los recursos; actualizar operación a CONFIRMADA con `respuesta_json` en **la misma transacción**; COMMIT. Reintentar un timeout consultando la misma `idOperacion` y exactamente el mismo payload.
4. Bajo READ COMMITTED, serializar con `SELECT ... FOR UPDATE` en el orden estable **usuario primero y vehículo después** cuando hagan falta ambos. No mantener EN_PROCESO durable sin reconciliación prevista.

## RF08 · Registrar vehículo y lectura inicial

Para una operación NUEVA: `SELECT usuario FOR UPDATE`, leer suscripción ACTIVA y plan, contar `SELECT COUNT(*) FROM vehiculo WHERE id_usuario=...` **incluyendo INACTIVO**. Si Free y ya tiene 1, rechazar. Si no: insertar `vehiculo` con primera principal; insertar `lectura_kilometraje(origen='INICIAL',valor_km>0,id_operacion=...)`; insertar `config_recordatorio_km(frecuencia='INTERDIARIO',estado='PAUSADO',fecha_base_utc=lectura.fecha_lectura_utc,proximo_envio_utc=NULL,ultimo_envio_utc=NULL)`; confirmar `operacion_idempotente` con IDs resultantes, un COMMIT. La PK global de placa impide dos placas iguales incluso inactivas. **No** crear alerta por registrar vehículo.

## RF09/RF10 · Consultar

Read-only. Vehículos del usuario autenticado, activos/inactivos; kilometraje vigente a partir de la última `lectura_kilometraje` ordenada por `fecha_lectura_utc DESC,id_lectura_km DESC`. Si falla esa lectura, exponer «kilometraje temporalmente no disponible», nunca fabricar 0.

## RF14 · Actualizar kilometraje

La vista previa Android no escribe. Al pulsar Continuar en la vista previa: resolver idempotencia; bloquear el **vehículo ACTIVO**; consultar lectura efectiva y exigir valor nuevo estrictamente superior; insertar lectura ACTUALIZACION con mismo UUID de operación; incrementar `vehiculo.version_km`; confirmar `operacion_idempotente.respuesta_json`, COMMIT. El botón Continuar de la pantalla de éxito **solo navega**. Desde RF17 volver al borrador y refrescar km actual sin sobrescribir `mantenimiento.km_servicio`. Nunca actualizar alertas en RF14.

## RF17 · Registrar mantenimiento REALIZADO

Resolver idempotencia; bloquear vehículo ACTIVO; validar tipo activo, fecha no futura, `km_servicio` histórico, origen LECTURA o HISTORICO_MANUAL, referencia de lectura del mismo vehículo con valor exacto y fecha compatible cuando corresponda. Las recomendaciones opcionales se comparan contra `fecha_servicio` y `km_servicio`, no contra la lectura vigente. Insertar mantenimiento, 0..N `repuesto` (id_usuario autenticado) y filas `mantenimiento_repuesto` con `nombre_snapshot`; confirmar operación; un COMMIT. No crear alertas ni marcar alertas REALIZADA.

## RF18 · Consultar historial

GET de mantenimientos REALIZADO y ANULADO, de vehículos autorizados ACTIVO/INACTIVO. Read-only; no modificar el odómetro.

## RF22 · Programar alerta manual

Resolver idempotencia; bloquear vehículo ACTIVO (mismo lock que RF17). Recalcular lectura actual para `km_base`; validar objetivo futuro y fecha objetivo calculada desde base original (`LocalDate.plusMonths` para MESES). Si se seleccionó mantenimiento de referencia, revalidar que es el **último REALIZADO** del mismo vehículo, tipo y servicio OTRO normalizado. Construir `firma_equivalencia` estable: `version + tipo + criterio + fechaObjetivo/NULL + kmObjetivo/NULL + servicioOtroNormalizado/NULL`, con delimitadores inequívocos y SHA-256 en hex minúscula. Insertar `alerta` y `historial_alerta(estado_anterior=NULL,version_destino=0,motivo='INICIAL_RF22')`; confirmar operación; un COMMIT. El índice parcial impide dos equivalentes activas concurrentes; capturar `unique_violation` y devolver conflicto funcional controlado.

## RF23 · Sincronizar Mis Alertas

Al entrar a «Mis alertas», ejecutar `SELECT mycar.sincronizar_alertas_vehiculo(:idVehiculo,:idUsuarioAutenticado)` **antes** del GET puro de lista. La función bloquea vehículo y alerta, obtiene odómetro, calcula umbrales de tiempo/KM, busca evidencia verificable de primera VENCIDA, y escribe alerta+historial **solo ante cambios reales**. `ANULADA` se determina a partir de 30 días calendario desde la primera evidencia, no desde la fecha de COMMIT. Puede saltar directamente de PROGRAMADA a ANULADA si el historial de evidencia lo justifica; no crear transiciones ficticias.

## Importante: políticas no implementadas por el DDL

- **Permisos de red**: no se confía en IDs enviados por el cliente; el servidor verifica cuenta y titularidad.
- **Contraseña**: Spring Boot utiliza hash seguro y solo llama a `registrar_fallo_login` después de verificar una contraseña incorrecta.
- **SHA-256 de equivalencia**: PostgreSQL controla la unicidad de `firma_equivalencia` pero **Spring Boot** es dueño de su normalización y digest. Sin esto, hashes diferentes para payload equivalente podrían pasar el índice.
- **Free de un registro**: no existe un CHECK SQL que cuente otras filas de forma segura; obligatoria validación transaccional con bloqueo de usuario.
- **App**: endpoints REST/DTO y UI se implementan aparte. Este paquete es **modelo físico + lógica SQL**, no un backend completo.
