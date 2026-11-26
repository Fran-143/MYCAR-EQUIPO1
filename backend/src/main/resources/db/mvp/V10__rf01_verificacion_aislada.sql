-- RF01 Elsa/Francesca: logica aislada (UUID/BYTEA, finalidad REGISTRO). Version 10.
-- V7 del ZIP era etiqueta local; V8 Nayu (proposito) se conserva como archivo. No editar V1-V9.
-- Mismas tablas que V8: esta migracion reemplaza el esquema Nayu por el de Elsa.
DROP TABLE IF EXISTS mycar.intento_verificacion;
DROP TABLE IF EXISTS mycar.emision_codigo;
DROP TABLE IF EXISTS mycar.operacion_verificacion;
DROP TABLE IF EXISTS mycar.control_verificacion;
ALTER TABLE mycar.usuario ADD COLUMN IF NOT EXISTS correo_verificado_utc TIMESTAMPTZ;
SET LOCAL TIME ZONE 'UTC';

CREATE TABLE mycar.control_verificacion (
 id_control UUID PRIMARY KEY,
 finalidad VARCHAR(16) NOT NULL CHECK(finalidad='REGISTRO'),
 tipo VARCHAR(8) NOT NULL CHECK(tipo IN ('CORREO','ORIGEN')),
 clave_sujeto BYTEA NOT NULL CHECK(octet_length(clave_sujeto)=32),
 correo_normalizado VARCHAR(254),
 credencial_hash BYTEA CHECK(octet_length(credencial_hash)=32),
 fallos_desde_utc TIMESTAMPTZ,
 fallos SMALLINT NOT NULL DEFAULT 0 CHECK(fallos BETWEEN 0 AND 3),
 bloqueo_hasta_utc TIMESTAMPTZ,
 solicitudes_desde_utc TIMESTAMPTZ,
 solicitudes SMALLINT NOT NULL DEFAULT 0 CHECK(solicitudes BETWEEN 0 AND 60),
 despachos_desde_utc TIMESTAMPTZ,
 despachos SMALLINT NOT NULL DEFAULT 0 CHECK(despachos BETWEEN 0 AND 12),
 backoff_hasta_utc TIMESTAMPTZ,
 creado_utc TIMESTAMPTZ NOT NULL,
 ultimo_uso_utc TIMESTAMPTZ NOT NULL,
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
);
CREATE UNIQUE INDEX uq_control_correo ON mycar.control_verificacion(finalidad,correo_normalizado) WHERE tipo='CORREO';
CREATE INDEX ix_control_uso ON mycar.control_verificacion(ultimo_uso_utc);
CREATE INDEX ix_control_bloqueo ON mycar.control_verificacion(bloqueo_hasta_utc);

CREATE TABLE mycar.operacion_verificacion (
 id_operacion UUID PRIMARY KEY,
 finalidad VARCHAR(16) NOT NULL CHECK(finalidad='REGISTRO'),
 id_control_origen UUID NOT NULL,
 tipo_origen VARCHAR(8) NOT NULL DEFAULT 'ORIGEN' CHECK(tipo_origen='ORIGEN'),
 secreto_hash BYTEA NOT NULL CHECK(octet_length(secreto_hash)=32),
 estado VARCHAR(12) NOT NULL CHECK(estado IN ('ABIERTA','COMPLETADA','CANCELADA','EXPIRADA')),
 revision BIGINT NOT NULL DEFAULT 0 CHECK(revision>=0),
 usuario_contexto BIGINT REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT CHECK(usuario_contexto IS NULL),
 usuario_resultado BIGINT REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
 creada_utc TIMESTAMPTZ NOT NULL,
 expira_utc TIMESTAMPTZ NOT NULL,
 completada_utc TIMESTAMPTZ,
 retener_hasta_utc TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(id_control_origen,finalidad,tipo_origen) REFERENCES mycar.control_verificacion(id_control,finalidad,tipo) ON DELETE RESTRICT,
 UNIQUE(id_operacion,finalidad),
 CHECK(expira_utc=creada_utc+INTERVAL '24 hours'),
 CHECK(retener_hasta_utc=expira_utc+INTERVAL '48 hours'),
 CHECK((estado='COMPLETADA' AND usuario_resultado IS NOT NULL AND completada_utc IS NOT NULL)
    OR (estado<>'COMPLETADA' AND usuario_resultado IS NULL AND completada_utc IS NULL))
);
CREATE UNIQUE INDEX uq_operacion_usuario_resultado ON mycar.operacion_verificacion(usuario_resultado) WHERE usuario_resultado IS NOT NULL;
CREATE INDEX ix_operacion_origen ON mycar.operacion_verificacion(id_control_origen,creada_utc);
CREATE INDEX ix_operacion_expira ON mycar.operacion_verificacion(estado,expira_utc);
CREATE INDEX ix_operacion_retener ON mycar.operacion_verificacion(retener_hasta_utc);

CREATE TABLE mycar.emision_codigo (
 id_emision UUID PRIMARY KEY,
 id_operacion UUID NOT NULL,
 finalidad VARCHAR(16) NOT NULL CHECK(finalidad='REGISTRO'),
 generacion BIGINT NOT NULL CHECK(generacion>0),
 id_control_correo UUID NOT NULL,
 tipo_correo VARCHAR(8) NOT NULL DEFAULT 'CORREO' CHECK(tipo_correo='CORREO'),
 estado_codigo VARCHAR(12) NOT NULL CHECK(estado_codigo IN ('RESERVADO','ACTIVO','CANCELADO','VENCIDO','CONSUMIDO')),
 estado_envio VARCHAR(12) NOT NULL CHECK(estado_envio IN ('PENDIENTE','EN_CURSO','ACEPTADO','RECHAZADO','INCIERTO')),
 estado_cupo VARCHAR(12) NOT NULL CHECK(estado_cupo IN ('RESERVADO','CONFIRMADO','LIBERADO','INCIERTO')),
 estado_entrega VARCHAR(12) NOT NULL DEFAULT 'DESCONOCIDA' CHECK(estado_entrega IN ('DESCONOCIDA','ENTREGADO','REBOTADO','FALLIDO')),
 verificador BYTEA CHECK(octet_length(verificador)=32),
 clave_verificador_version SMALLINT,
 payload_cifrado BYTEA CHECK(octet_length(payload_cifrado)<=8192),
 nonce BYTEA CHECK(octet_length(nonce)=12),
 clave_cifrado_id VARCHAR(40),
 proveedor VARCHAR(16) NOT NULL,
 clave_idempotencia_proveedor VARCHAR(100) NOT NULL,
 id_proveedor VARCHAR(128),
 error_categoria VARCHAR(40),
 creada_utc TIMESTAMPTZ NOT NULL,
 reserva_hasta_utc TIMESTAMPTZ NOT NULL,
 primer_despacho_utc TIMESTAMPTZ,
 vence_codigo_utc TIMESTAMPTZ,
 aceptada_utc TIMESTAMPTZ,
 cerrada_utc TIMESTAMPTZ,
 intentos_despacho SMALLINT NOT NULL DEFAULT 0 CHECK(intentos_despacho BETWEEN 0 AND 4),
 proximo_intento_utc TIMESTAMPTZ,
 lease_id UUID,
 lease_hasta_utc TIMESTAMPTZ,
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
);
CREATE UNIQUE INDEX uq_emision_vigente ON mycar.emision_codigo(id_operacion) WHERE estado_codigo IN ('RESERVADO','ACTIVO');
CREATE UNIQUE INDEX uq_emision_proveedor ON mycar.emision_codigo(proveedor,id_proveedor) WHERE id_proveedor IS NOT NULL;
CREATE INDEX ix_emision_correo ON mycar.emision_codigo(id_control_correo,primer_despacho_utc);
CREATE INDEX ix_emision_operacion ON mycar.emision_codigo(id_operacion,primer_despacho_utc);
CREATE INDEX ix_emision_cola ON mycar.emision_codigo(estado_envio,proximo_intento_utc);

CREATE TABLE mycar.intento_verificacion (
 id_operacion UUID NOT NULL,
 id_solicitud UUID NOT NULL,
 finalidad VARCHAR(16) NOT NULL CHECK(finalidad='REGISTRO'),
 accion VARCHAR(12) NOT NULL CHECK(accion IN ('EMITIR','CANCELAR','ABANDONAR','COMPLETAR')),
 id_emision UUID,
 huella_solicitud BYTEA NOT NULL CHECK(octet_length(huella_solicitud)=32),
 clave_huella_version SMALLINT NOT NULL,
 resultado VARCHAR(40) NOT NULL CHECK(resultado IN ('EMISION_CREADA','CANCELACION_CONFIRMADA','ABANDONO_CONFIRMADO','CUENTA_CREADA','CODIGO_INCORRECTO','BLOQUEO_INICIADO')),
 http_status SMALLINT NOT NULL CHECK(http_status BETWEEN 200 AND 499),
 revision_resultado BIGINT NOT NULL CHECK(revision_resultado>=0),
 fallo_contabilizado BOOLEAN NOT NULL DEFAULT FALSE,
 bloqueo_hasta_resultado_utc TIMESTAMPTZ,
 ocurrido_utc TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(id_operacion,id_solicitud),
 FOREIGN KEY(id_operacion,finalidad) REFERENCES mycar.operacion_verificacion(id_operacion,finalidad) ON DELETE RESTRICT,
 FOREIGN KEY(id_emision,id_operacion,finalidad) REFERENCES mycar.emision_codigo(id_emision,id_operacion,finalidad) ON DELETE RESTRICT,
 CHECK((accion NOT IN ('EMITIR','COMPLETAR')) OR id_emision IS NOT NULL),
 CHECK(fallo_contabilizado=(resultado IN ('CODIGO_INCORRECTO','BLOQUEO_INICIADO'))),
 CHECK(NOT fallo_contabilizado OR accion='COMPLETAR')
);
CREATE INDEX ix_intento_emision ON mycar.intento_verificacion(id_emision,ocurrido_utc);
