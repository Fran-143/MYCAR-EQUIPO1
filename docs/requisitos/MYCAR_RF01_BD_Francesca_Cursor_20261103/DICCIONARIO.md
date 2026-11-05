# Diccionario exacto del SQL entregado

Fuente: `sql/V7__rf01_verificacion_aislada.sql`. SHA-256: `a4538994ad6153f18f2d1aa6f0e3ee2c1baab5dea5949696d6c0b487ad862fd0`. Columnas, tipos, DEFAULT, nulabilidad y cláusulas se extraen del archivo; no se reconstruyen del diseño.

Todas las tablas están en `mycar`. Sin DEFAULT significa que SQL no genera el valor. NULL permitido no equivale a libre de CHECK condicional. PK implica NOT NULL aunque no figure literalmente. Fechas: TIMESTAMPTZ; ningún campo temporal nuevo tiene DEFAULT clock_timestamp(). UUID tampoco tiene generación DEFAULT.

Las restricciones sin nombre explícito reciben nombre automático de PostgreSQL; se reproduce su definición sin inventar nombres. CHECK rechaza FALSE, pero puede admitir UNKNOWN por NULL: ver brechas documentadas. No hay triggers nuevos en esta migración.

## `control_verificacion`

Control duradero por correo/finalidad o por origen/finalidad. Ejemplo: correo A acumula dos fallos aunque cambie de proceso.

**Qué guarda / qué no guarda:** Correo normalizado o hash de credencial de origen, HMAC de sujeto, fallos/bloqueo y contadores técnicos. No guarda OTP, formulario, contraseña, emisión ni recibo.

**Eliminación:** Sin referencias: más de siete días para CORREO o treinta para ORIGEN según ultimo_uso_utc, sin ventanas/bloqueos/backoff vigentes; solo al ejecutar maintain(). Ver limitación del campo ultimo_uso en RELACIONES_Y_REGLAS.

| Campo | Tipo exacto | NULL | DEFAULT SQL |
|---|---|---|---|
| `id_control` | `UUID` | No (PK) | Sin DEFAULT |
| `finalidad` | `VARCHAR(16)` | No | Sin DEFAULT |
| `tipo` | `VARCHAR(8)` | No | Sin DEFAULT |
| `clave_sujeto` | `BYTEA` | No | Sin DEFAULT |
| `correo_normalizado` | `VARCHAR(254)` | Sí | Sin DEFAULT |
| `credencial_hash` | `BYTEA` | Sí | Sin DEFAULT |
| `fallos_desde_utc` | `TIMESTAMPTZ` | Sí | Sin DEFAULT |
| `fallos` | `SMALLINT` | No | `0` |
| `bloqueo_hasta_utc` | `TIMESTAMPTZ` | Sí | Sin DEFAULT |
| `solicitudes_desde_utc` | `TIMESTAMPTZ` | Sí | Sin DEFAULT |
| `solicitudes` | `SMALLINT` | No | `0` |
| `despachos_desde_utc` | `TIMESTAMPTZ` | Sí | Sin DEFAULT |
| `despachos` | `SMALLINT` | No | `0` |
| `backoff_hasta_utc` | `TIMESTAMPTZ` | Sí | Sin DEFAULT |
| `creado_utc` | `TIMESTAMPTZ` | No | Sin DEFAULT |
| `ultimo_uso_utc` | `TIMESTAMPTZ` | No | Sin DEFAULT |

### PK, FK, UNIQUE y CHECK exactos

Restricciones de columna (además de tipos/defaults ya tabulados):

```sql
id_control UUID PRIMARY KEY
```

```sql
finalidad VARCHAR(16) NOT NULL CHECK(finalidad='REGISTRO')
```

```sql
tipo VARCHAR(8) NOT NULL CHECK(tipo IN ('CORREO','ORIGEN'))
```

```sql
clave_sujeto BYTEA NOT NULL CHECK(octet_length(clave_sujeto)=32)
```

```sql
credencial_hash BYTEA CHECK(octet_length(credencial_hash)=32)
```

```sql
fallos SMALLINT NOT NULL DEFAULT 0 CHECK(fallos BETWEEN 0 AND 3)
```

```sql
solicitudes SMALLINT NOT NULL DEFAULT 0 CHECK(solicitudes BETWEEN 0 AND 60)
```

```sql
despachos SMALLINT NOT NULL DEFAULT 0 CHECK(despachos BETWEEN 0 AND 12)
```

Restricciones de tabla:

```sql
UNIQUE(finalidad,tipo,clave_sujeto),
UNIQUE(id_control,finalidad,tipo),
CHECK((fallos=0)=(fallos_desde_utc IS NULL)),
CHECK(bloqueo_hasta_utc IS NULL OR (fallos=3 AND bloqueo_hasta_utc>fallos_desde_utc)),
CHECK((solicitudes=0)=(solicitudes_desde_utc IS NULL)),
CHECK((despachos=0)=(despachos_desde_utc IS NULL)),
CHECK((tipo='CORREO' AND credencial_hash IS NULL AND correo_normalizado IS NOT NULL
     AND correo_normalizado=lower(btrim(correo_normalizado)) AND length(correo_normalizado) BETWEEN 5 AND 254
     AND solicitudes=0 AND despachos=0)
   OR (tipo='ORIGEN' AND correo_normalizado IS NULL AND credencial_hash IS NOT NULL
     AND fallos=0 AND bloqueo_hasta_utc IS NULL))
```

### Índices explícitos

```sql
CREATE UNIQUE INDEX uq_control_correo ON mycar.control_verificacion(finalidad,correo_normalizado) WHERE tipo='CORREO';
CREATE INDEX ix_control_uso ON mycar.control_verificacion(ultimo_uso_utc);
CREATE INDEX ix_control_bloqueo ON mycar.control_verificacion(bloqueo_hasta_utc);
```

Además, PK y UNIQUE crean sus índices de respaldo. No se afirma que los nombres automáticos sean los explícitos anteriores.

### Valores de `tipo`

- `CORREO`: Control global de un correo normalizado para REGISTRO.
- `ORIGEN`: Credencial/controles de una instalación protegida; no identidad personal.

### Garantías que NO aporta SQL por sí solo

Validar correo completo, calcular HMAC/verificar credencial, que tres fallos ocurran en la ventana, duración del bloqueo y evitar reinicio por otra operación. CHECK limita rangos/forma, no calcula políticas; backend y bloqueos las aplican.

## `operacion_verificacion`

Proceso protegido de RF01. Ejemplo: proceso P mantiene autorización y revisión al pasar de correo A a correo B.

**Qué guarda / qué no guarda:** Hash de secreto, origen, revisión, estados/fechas y usuario resultado al completar. No guarda formulario, contraseña, OTP ni correo destinatario directo.

**Eliminación:** Recibos y emisiones se borran antes; operación a las 72 h desde creación, por maintain(). Expirar/cancelar no elimina inmediatamente el historial.

| Campo | Tipo exacto | NULL | DEFAULT SQL |
|---|---|---|---|
| `id_operacion` | `UUID` | No (PK) | Sin DEFAULT |
| `finalidad` | `VARCHAR(16)` | No | Sin DEFAULT |
| `id_control_origen` | `UUID` | No | Sin DEFAULT |
| `tipo_origen` | `VARCHAR(8)` | No | `'ORIGEN'` |
| `secreto_hash` | `BYTEA` | No | Sin DEFAULT |
| `estado` | `VARCHAR(12)` | No | Sin DEFAULT |
| `revision` | `BIGINT` | No | `0` |
| `usuario_contexto` | `BIGINT` | Sí | Sin DEFAULT |
| `usuario_resultado` | `BIGINT` | Sí | Sin DEFAULT |
| `creada_utc` | `TIMESTAMPTZ` | No | Sin DEFAULT |
| `expira_utc` | `TIMESTAMPTZ` | No | Sin DEFAULT |
| `completada_utc` | `TIMESTAMPTZ` | Sí | Sin DEFAULT |
| `retener_hasta_utc` | `TIMESTAMPTZ` | No | Sin DEFAULT |

### PK, FK, UNIQUE y CHECK exactos

Restricciones de columna (además de tipos/defaults ya tabulados):

```sql
id_operacion UUID PRIMARY KEY
```

```sql
finalidad VARCHAR(16) NOT NULL CHECK(finalidad='REGISTRO')
```

```sql
tipo_origen VARCHAR(8) NOT NULL DEFAULT 'ORIGEN' CHECK(tipo_origen='ORIGEN')
```

```sql
secreto_hash BYTEA NOT NULL CHECK(octet_length(secreto_hash)=32)
```

```sql
estado VARCHAR(12) NOT NULL CHECK(estado IN ('ABIERTA','COMPLETADA','CANCELADA','EXPIRADA'))
```

```sql
revision BIGINT NOT NULL DEFAULT 0 CHECK(revision>=0)
```

```sql
usuario_contexto BIGINT REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT CHECK(usuario_contexto IS NULL)
```

```sql
usuario_resultado BIGINT REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT
```

Restricciones de tabla:

```sql
FOREIGN KEY(id_control_origen,finalidad,tipo_origen) REFERENCES mycar.control_verificacion(id_control,finalidad,tipo) ON DELETE RESTRICT,
UNIQUE(id_operacion,finalidad),
CHECK(expira_utc=creada_utc+INTERVAL '24 hours'),
CHECK(retener_hasta_utc=expira_utc+INTERVAL '48 hours'),
CHECK((estado='COMPLETADA' AND usuario_resultado IS NOT NULL AND completada_utc IS NOT NULL)
    OR (estado<>'COMPLETADA' AND usuario_resultado IS NULL AND completada_utc IS NULL))
```

### Índices explícitos

```sql
CREATE UNIQUE INDEX uq_operacion_usuario_resultado ON mycar.operacion_verificacion(usuario_resultado) WHERE usuario_resultado IS NOT NULL;
CREATE INDEX ix_operacion_origen ON mycar.operacion_verificacion(id_control_origen,creada_utc);
CREATE INDEX ix_operacion_expira ON mycar.operacion_verificacion(estado,expira_utc);
CREATE INDEX ix_operacion_retener ON mycar.operacion_verificacion(retener_hasta_utc);
```

Además, PK y UNIQUE crean sus índices de respaldo. No se afirma que los nombres automáticos sean los explícitos anteriores.

### Valores de `estado`

- `ABIERTA`: Proceso que puede avanzar si además sigue vigente.
- `COMPLETADA`: Registro confirmado; exige usuario_resultado y completada_utc.
- `CANCELADA`: Abandono del proceso; no solo cambio de correo.
- `EXPIRADA`: Vencimiento materializado por mantenimiento.

### Garantías que NO aporta SQL por sí solo

Autorizar mediante secreto, incrementar revisión, impedir transiciones/reaperturas, comprobar hora actual y que usuario_resultado sea recién creado/coincida su correo. La FK opcional de contexto NO habilita otros RF porque CHECK exige NULL.

## `emision_codigo`

Una generación de OTP y su trabajo de envío. Ejemplo: E1 queda cancelada y E2 es un reenvío; un reintento técnico sigue siendo E2.

**Qué guarda / qué no guarda:** Vinculación al destinatario mediante control CORREO, verificador protegido, copia cifrada temporal, estados y fechas de envío/cupo, clave externa idempotente y lease. No guarda OTP en claro ni formulario.

**Eliminación:** Secretos al cancelar/consumir/vencer; cifrado también al aceptar/rechazar/agotar intentos. Fila hasta limpieza de operación a las 72 h; cancelar no elimina cargos pasados.

| Campo | Tipo exacto | NULL | DEFAULT SQL |
|---|---|---|---|
| `id_emision` | `UUID` | No (PK) | Sin DEFAULT |
| `id_operacion` | `UUID` | No | Sin DEFAULT |
| `finalidad` | `VARCHAR(16)` | No | Sin DEFAULT |
| `generacion` | `BIGINT` | No | Sin DEFAULT |
| `id_control_correo` | `UUID` | No | Sin DEFAULT |
| `tipo_correo` | `VARCHAR(8)` | No | `'CORREO'` |
| `estado_codigo` | `VARCHAR(12)` | No | Sin DEFAULT |
| `estado_envio` | `VARCHAR(12)` | No | Sin DEFAULT |
| `estado_cupo` | `VARCHAR(12)` | No | Sin DEFAULT |
| `estado_entrega` | `VARCHAR(12)` | No | `'DESCONOCIDA'` |
| `verificador` | `BYTEA` | Sí | Sin DEFAULT |
| `clave_verificador_version` | `SMALLINT` | Sí | Sin DEFAULT |
| `payload_cifrado` | `BYTEA` | Sí | Sin DEFAULT |
| `nonce` | `BYTEA` | Sí | Sin DEFAULT |
| `clave_cifrado_id` | `VARCHAR(40)` | Sí | Sin DEFAULT |
| `proveedor` | `VARCHAR(16)` | No | Sin DEFAULT |
| `clave_idempotencia_proveedor` | `VARCHAR(100)` | No | Sin DEFAULT |
| `id_proveedor` | `VARCHAR(128)` | Sí | Sin DEFAULT |
| `error_categoria` | `VARCHAR(40)` | Sí | Sin DEFAULT |
| `creada_utc` | `TIMESTAMPTZ` | No | Sin DEFAULT |
| `reserva_hasta_utc` | `TIMESTAMPTZ` | No | Sin DEFAULT |
| `primer_despacho_utc` | `TIMESTAMPTZ` | Sí | Sin DEFAULT |
| `vence_codigo_utc` | `TIMESTAMPTZ` | Sí | Sin DEFAULT |
| `aceptada_utc` | `TIMESTAMPTZ` | Sí | Sin DEFAULT |
| `cerrada_utc` | `TIMESTAMPTZ` | Sí | Sin DEFAULT |
| `intentos_despacho` | `SMALLINT` | No | `0` |
| `proximo_intento_utc` | `TIMESTAMPTZ` | Sí | Sin DEFAULT |
| `lease_id` | `UUID` | Sí | Sin DEFAULT |
| `lease_hasta_utc` | `TIMESTAMPTZ` | Sí | Sin DEFAULT |

### PK, FK, UNIQUE y CHECK exactos

Restricciones de columna (además de tipos/defaults ya tabulados):

```sql
id_emision UUID PRIMARY KEY
```

```sql
finalidad VARCHAR(16) NOT NULL CHECK(finalidad='REGISTRO')
```

```sql
generacion BIGINT NOT NULL CHECK(generacion>0)
```

```sql
tipo_correo VARCHAR(8) NOT NULL DEFAULT 'CORREO' CHECK(tipo_correo='CORREO')
```

```sql
estado_codigo VARCHAR(12) NOT NULL CHECK(estado_codigo IN ('RESERVADO','ACTIVO','CANCELADO','VENCIDO','CONSUMIDO'))
```

```sql
estado_envio VARCHAR(12) NOT NULL CHECK(estado_envio IN ('PENDIENTE','EN_CURSO','ACEPTADO','RECHAZADO','INCIERTO'))
```

```sql
estado_cupo VARCHAR(12) NOT NULL CHECK(estado_cupo IN ('RESERVADO','CONFIRMADO','LIBERADO','INCIERTO'))
```

```sql
estado_entrega VARCHAR(12) NOT NULL DEFAULT 'DESCONOCIDA' CHECK(estado_entrega IN ('DESCONOCIDA','ENTREGADO','REBOTADO','FALLIDO'))
```

```sql
verificador BYTEA CHECK(octet_length(verificador)=32)
```

```sql
payload_cifrado BYTEA CHECK(octet_length(payload_cifrado)<=8192)
```

```sql
nonce BYTEA CHECK(octet_length(nonce)=12)
```

```sql
intentos_despacho SMALLINT NOT NULL DEFAULT 0 CHECK(intentos_despacho BETWEEN 0 AND 4)
```

Restricciones de tabla:

```sql
FOREIGN KEY(id_operacion,finalidad) REFERENCES mycar.operacion_verificacion(id_operacion,finalidad) ON DELETE RESTRICT,
FOREIGN KEY(id_control_correo,finalidad,tipo_correo) REFERENCES mycar.control_verificacion(id_control,finalidad,tipo) ON DELETE RESTRICT,
UNIQUE(id_operacion,finalidad,generacion),
UNIQUE(id_emision,id_operacion,finalidad),
UNIQUE(proveedor,clave_idempotencia_proveedor),
CHECK(reserva_hasta_utc=creada_utc+INTERVAL '120 seconds'),
CHECK((primer_despacho_utc IS NULL AND vence_codigo_utc IS NULL)
    OR (primer_despacho_utc IS NOT NULL AND vence_codigo_utc=primer_despacho_utc+INTERVAL '10 minutes')),
CHECK(estado_envio<>'ACEPTADO' OR (aceptada_utc IS NOT NULL AND id_proveedor IS NOT NULL AND estado_cupo='CONFIRMADO')),
CHECK(estado_cupo<>'CONFIRMADO' OR aceptada_utc IS NOT NULL),
CHECK(estado_cupo<>'INCIERTO' OR primer_despacho_utc IS NOT NULL),
CHECK((estado_codigo IN ('CANCELADO','VENCIDO','CONSUMIDO'))=(cerrada_utc IS NOT NULL)),
CHECK((verificador IS NULL)=(clave_verificador_version IS NULL)),
CHECK((payload_cifrado IS NULL AND nonce IS NULL AND clave_cifrado_id IS NULL)
    OR (payload_cifrado IS NOT NULL AND nonce IS NOT NULL AND clave_cifrado_id IS NOT NULL)),
CHECK((lease_id IS NULL)=(lease_hasta_utc IS NULL))
```

### Índices explícitos

```sql
CREATE UNIQUE INDEX uq_emision_vigente ON mycar.emision_codigo(id_operacion) WHERE estado_codigo IN ('RESERVADO','ACTIVO');
CREATE UNIQUE INDEX uq_emision_proveedor ON mycar.emision_codigo(proveedor,id_proveedor) WHERE id_proveedor IS NOT NULL;
CREATE INDEX ix_emision_correo ON mycar.emision_codigo(id_control_correo,primer_despacho_utc);
CREATE INDEX ix_emision_operacion ON mycar.emision_codigo(id_operacion,primer_despacho_utc);
CREATE INDEX ix_emision_cola ON mycar.emision_codigo(estado_envio,proximo_intento_utc);
```

Además, PK y UNIQUE crean sus índices de respaldo. No se afirma que los nombres automáticos sean los explícitos anteriores.

### Valores de `estado_codigo`

- `RESERVADO`: Preparada sin primer despacho.
- `ACTIVO`: Se marcó primer despacho; también debe comprobarse la fecha.
- `CANCELADO`: Invalidada por cancelación, sustitución o fallo que lo exige.
- `VENCIDO`: Vencimiento materializado.
- `CONSUMIDO`: Usado satisfactoriamente en registro final.

### Valores de `estado_envio`

- `PENDIENTE`: Aún no se marcó despacho.
- `EN_CURSO`: Posible IO iniciado; puede quedar sin respuesta.
- `ACEPTADO`: Aceptación con ID; no prueba entrega.
- `RECHAZADO`: Evidencia definitiva de no aceptación de la emisión.
- `INCIERTO`: No puede confirmarse aceptación o rechazo.

### Valores de `estado_cupo`

- `RESERVADO`: Ocupa provisionalmente mientras la reserva sea válida.
- `CONFIRMADO`: Envío aceptado cargado en su ventana.
- `LIBERADO`: No consume oportunidad funcional.
- `INCIERTO`: Cargo conservador por posible aceptación; no es reserva sin despacho.

### Valores de `estado_entrega`

- `DESCONOCIDA`: Estado inicial y único actualizado por esta etapa.
- `ENTREGADO`: Catálogo preparado; no integrado con eventos reales.
- `REBOTADO`: Catálogo preparado; no integrado.
- `FALLIDO`: Catálogo preparado; no integrado.

### Garantías que NO aporta SQL por sí solo

Generación aleatoria/seis cifras, algoritmo criptográfico, uso único real/transiciones, reserva válida según hora actual, cuotas, generación creciente, vigencia dentro de operación y envío real. La unicidad parcial solo impide dos filas materializadas RESERVADO/ACTIVO; no detecta expiración por reloj ni prueba entrega.

## `intento_verificacion`

Recibo idempotente de una mutación confirmada, no solo un intento incorrecto. Ejemplo: solicitud S repetida devuelve el mismo fallo sin incrementarlo otra vez.

**Qué guarda / qué no guarda:** HMAC de solicitud, acción, IDs, resultado/status/revisión y si contó un fallo. No guarda el cuerpo, OTP recibido, contraseña o formulario en claro; no registra todos los errores.

**Eliminación:** Se conserva hasta limpieza de la operación, a las 72 h desde creación. No eliminar selectivamente recibos vigentes para permitir recontabilizar solicitudes.

| Campo | Tipo exacto | NULL | DEFAULT SQL |
|---|---|---|---|
| `id_operacion` | `UUID` | No | Sin DEFAULT |
| `id_solicitud` | `UUID` | No | Sin DEFAULT |
| `finalidad` | `VARCHAR(16)` | No | Sin DEFAULT |
| `accion` | `VARCHAR(12)` | No | Sin DEFAULT |
| `id_emision` | `UUID` | Sí | Sin DEFAULT |
| `huella_solicitud` | `BYTEA` | No | Sin DEFAULT |
| `clave_huella_version` | `SMALLINT` | No | Sin DEFAULT |
| `resultado` | `VARCHAR(40)` | No | Sin DEFAULT |
| `http_status` | `SMALLINT` | No | Sin DEFAULT |
| `revision_resultado` | `BIGINT` | No | Sin DEFAULT |
| `fallo_contabilizado` | `BOOLEAN` | No | `FALSE` |
| `bloqueo_hasta_resultado_utc` | `TIMESTAMPTZ` | Sí | Sin DEFAULT |
| `ocurrido_utc` | `TIMESTAMPTZ` | No | Sin DEFAULT |

### PK, FK, UNIQUE y CHECK exactos

Restricciones de columna (además de tipos/defaults ya tabulados):

```sql
finalidad VARCHAR(16) NOT NULL CHECK(finalidad='REGISTRO')
```

```sql
accion VARCHAR(12) NOT NULL CHECK(accion IN ('EMITIR','CANCELAR','ABANDONAR','COMPLETAR'))
```

```sql
huella_solicitud BYTEA NOT NULL CHECK(octet_length(huella_solicitud)=32)
```

```sql
resultado VARCHAR(40) NOT NULL CHECK(resultado IN ('EMISION_CREADA','CANCELACION_CONFIRMADA','ABANDONO_CONFIRMADO','CUENTA_CREADA','CODIGO_INCORRECTO','BLOQUEO_INICIADO'))
```

```sql
http_status SMALLINT NOT NULL CHECK(http_status BETWEEN 200 AND 499)
```

```sql
revision_resultado BIGINT NOT NULL CHECK(revision_resultado>=0)
```

Restricciones de tabla:

```sql
PRIMARY KEY(id_operacion,id_solicitud),
FOREIGN KEY(id_operacion,finalidad) REFERENCES mycar.operacion_verificacion(id_operacion,finalidad) ON DELETE RESTRICT,
FOREIGN KEY(id_emision,id_operacion,finalidad) REFERENCES mycar.emision_codigo(id_emision,id_operacion,finalidad) ON DELETE RESTRICT,
CHECK((accion NOT IN ('EMITIR','COMPLETAR')) OR id_emision IS NOT NULL),
CHECK(fallo_contabilizado=(resultado IN ('CODIGO_INCORRECTO','BLOQUEO_INICIADO'))),
CHECK(NOT fallo_contabilizado OR accion='COMPLETAR')
```

### Índices explícitos

```sql
CREATE INDEX ix_intento_emision ON mycar.intento_verificacion(id_emision,ocurrido_utc);
```

Además, PK y UNIQUE crean sus índices de respaldo. No se afirma que los nombres automáticos sean los explícitos anteriores.

### Valores de `accion`

- `EMITIR`: Solicitud que creó emisión.
- `CANCELAR`: Cancelación de verificación; conserva proceso.
- `ABANDONAR`: Cancelación del proceso.
- `COMPLETAR`: Comprobación/finalización del registro.

### Valores de `resultado`

- `EMISION_CREADA`: Reserva y emisión confirmadas.
- `CANCELACION_CONFIRMADA`: Cancelación registrada.
- `ABANDONO_CONFIRMADO`: Proceso abandonado.
- `CUENTA_CREADA`: Registro atómico confirmado.
- `CODIGO_INCORRECTO`: Un fallo contabilizado.
- `BLOQUEO_INICIADO`: Tercer fallo contabilizado y bloqueo producido.

### Garantías que NO aporta SQL por sí solo

Que la huella sea HMAC válido, igualdad del contenido repetido, incremento exactamente una vez, correspondencia completa acción/resultado/status o la TX de cuenta. PK/FK impiden duplicidad y mezclas estructurales; protocolo de locks/relectura/transacción completa las garantías.

## Columna añadida a usuario

```sql
ALTER TABLE mycar.usuario ADD COLUMN correo_verificado_utc TIMESTAMPTZ;
```

Permite NULL, sin DEFAULT, índice o backfill. Cuentas anteriores permanecen NULL. El backend nuevo la establece en TX final; el login actual no se modifica. La relación con operaciones usa las FK de usuario_contexto/usuario_resultado descritas arriba, no esta fecha.
