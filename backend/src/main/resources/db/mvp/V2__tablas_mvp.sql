-- 21 tablas del MVP. V1 permanece intacta; Flyway controla la transacción.
SET LOCAL TIME ZONE 'UTC';
DO $preflight$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='mycar' AND c.relkind IN ('r','p','v','m','f')
      AND c.relname<>'flyway_schema_history'
  ) THEN
    RAISE EXCEPTION 'V2 requiere mycar sin relaciones de negocio; se conserva todo dato existente. Revisar el destino sin borrar ni reparar Flyway';
  END IF;
END;
$preflight$;

-- 01. Identidad y plan Free (RF01, RF02, RF06)
CREATE TABLE mycar.usuario (
 id_usuario BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 correo VARCHAR(254) NOT NULL CHECK (length(btrim(correo)) BETWEEN 5 AND 254 AND correo=btrim(correo)),
 password_hash VARCHAR(255) NOT NULL CHECK (length(btrim(password_hash))>15),
 estado_cuenta VARCHAR(24) NOT NULL DEFAULT 'ACTIVA' CHECK (estado_cuenta IN ('ACTIVA','SUSPENDIDA','PENDIENTE_ELIMINACION')),
 fecha_registro_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 nombre VARCHAR(100) NOT NULL CHECK (length(btrim(nombre)) BETWEEN 2 AND 100),
 apellido VARCHAR(100) NOT NULL CHECK (length(btrim(apellido)) BETWEEN 2 AND 100),
 fecha_nacimiento DATE NOT NULL,
 consentimiento_privacidad_fecha_utc TIMESTAMPTZ NOT NULL,
 consentimiento_privacidad_version VARCHAR(40) NOT NULL CHECK(length(btrim(consentimiento_privacidad_version))>0),
 codigo_rol VARCHAR(32) NOT NULL DEFAULT 'USUARIO' CHECK (codigo_rol='USUARIO'),
 login_bloqueo_hasta_utc TIMESTAMPTZ,
 login_fallos_desde_utc TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_usuario_correo_normalizado ON mycar.usuario(lower(btrim(correo)));

CREATE TABLE mycar.plan (
 id_plan BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 codigo VARCHAR(20) NOT NULL UNIQUE CHECK (codigo IN ('FREE','PREMIUM')),
 nombre VARCHAR(60) NOT NULL CHECK(length(btrim(nombre))>0),
 max_vehiculos_registrados INTEGER NOT NULL CHECK(max_vehiculos_registrados>0),
 activo BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE mycar.suscripcion (
 id_suscripcion BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 id_usuario BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
 id_plan BIGINT NOT NULL REFERENCES mycar.plan(id_plan) ON DELETE RESTRICT,
 estado VARCHAR(16) NOT NULL CHECK(estado IN ('ACTIVA','VENCIDA')),
 inicio_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 fin_utc TIMESTAMPTZ,
 CONSTRAINT ck_suscripcion_fechas CHECK(fin_utc IS NULL OR fin_utc>=inicio_utc)
);
CREATE UNIQUE INDEX uq_suscripcion_activa_usuario ON mycar.suscripcion(id_usuario) WHERE estado='ACTIVA';
CREATE INDEX ix_suscripcion_usuario_historia ON mycar.suscripcion(id_usuario,inicio_utc DESC);

CREATE TABLE mycar.preferencia_notificacion (
 id_usuario BIGINT PRIMARY KEY REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
 mantenimiento_on BOOLEAN NOT NULL DEFAULT TRUE,
 soat_on BOOLEAN NOT NULL DEFAULT TRUE,
 kilometraje_on BOOLEAN NOT NULL DEFAULT TRUE,
 permiso_dispositivo VARCHAR(24) NOT NULL DEFAULT 'NO_SOLICITADO'
  CHECK(permiso_dispositivo IN ('NO_SOLICITADO','CONCEDIDO','DENEGADO'))
);
CREATE TABLE mycar.config_recordatorio_km_usuario (
 id_usuario BIGINT PRIMARY KEY REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
 estado_recordatorios VARCHAR(16) NOT NULL DEFAULT 'PAUSADO'
  CHECK(estado_recordatorios IN ('PAUSADO','ACTIVO'))
);
CREATE TABLE mycar.sesion (
 id_sesion BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 id_usuario BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
 id_instalacion UUID NOT NULL,
 refresh_token_hash CHAR(64) NOT NULL UNIQUE CHECK(refresh_token_hash ~ '^[a-f0-9]{64}$'),
 estado_sesion VARCHAR(16) NOT NULL DEFAULT 'VIGENTE' CHECK(estado_sesion IN ('VIGENTE','REVOCADA','EXPIRADA')),
 expira_utc TIMESTAMPTZ NOT NULL,
 revocada_utc TIMESTAMPTZ,
 ultimo_acceso_utc TIMESTAMPTZ,
 CONSTRAINT ck_sesion_revocacion CHECK(estado_sesion<>'REVOCADA' OR revocada_utc IS NOT NULL)
);
CREATE UNIQUE INDEX uq_sesion_vigente_instalacion ON mycar.sesion(id_usuario,id_instalacion) WHERE estado_sesion='VIGENTE';
CREATE INDEX ix_sesion_usuario_estado ON mycar.sesion(id_usuario,estado_sesion);
CREATE TABLE mycar.intento_login_fallido (
 id_intento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 id_usuario BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
 ocurrido_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX ix_intento_login_ventana ON mycar.intento_login_fallido(id_usuario,ocurrido_utc DESC);

-- 02. Catálogos de vehículos / mantenimiento
CREATE TABLE mycar.uso_vehiculo (
 codigo VARCHAR(30) PRIMARY KEY,
 nombre VARCHAR(80) NOT NULL CHECK(length(btrim(nombre))>0)
);
CREATE TABLE mycar.tipo_motor (
 id_tipo_motor BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 codigo VARCHAR(50) NOT NULL UNIQUE,
 nombre VARCHAR(100) NOT NULL,
 es_electrico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE mycar.combustible (
 id_combustible BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 codigo VARCHAR(50) NOT NULL UNIQUE,
 nombre VARCHAR(100) NOT NULL
);
CREATE TABLE mycar.tipo_aceite (
 id_tipo_aceite BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 codigo VARCHAR(50) NOT NULL UNIQUE,
 nombre VARCHAR(100) NOT NULL
);
CREATE TABLE mycar.tipo_mantenimiento (
 id_tipo_mantenimiento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 codigo VARCHAR(50) NOT NULL UNIQUE,
 nombre VARCHAR(120) NOT NULL UNIQUE,
 activo BOOLEAN NOT NULL DEFAULT TRUE
);

-- 03. Vehículos y lecturas (RF08, RF09, RF10, RF14)
CREATE TABLE mycar.vehiculo (
 id_vehiculo BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 id_usuario BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
 placa VARCHAR(6) NOT NULL UNIQUE CHECK(placa ~ '^[A-Z][0-9][A-Z][0-9]{3}$'),
 numero_vin CHAR(17) UNIQUE,
 marca VARCHAR(100) NOT NULL CHECK(length(btrim(marca))>0),
 modelo VARCHAR(100) NOT NULL CHECK(length(btrim(modelo))>0),
 uso_vehiculo_codigo VARCHAR(30) NOT NULL REFERENCES mycar.uso_vehiculo(codigo),
 anio_fabricacion INTEGER NOT NULL CHECK (anio_fabricacion > 0),
 color VARCHAR(80) NOT NULL CHECK(length(btrim(color))>0),
 id_tipo_motor BIGINT NOT NULL REFERENCES mycar.tipo_motor(id_tipo_motor),
 tipo_motor_otro VARCHAR(100),
 id_combustible BIGINT REFERENCES mycar.combustible(id_combustible),
 combustible_otro VARCHAR(100),
 id_tipo_aceite BIGINT REFERENCES mycar.tipo_aceite(id_tipo_aceite),
 tipo_aceite_otro VARCHAR(100),
 numero_serie VARCHAR(80),
 numero_motor VARCHAR(80),
 estado_vehiculo VARCHAR(16) NOT NULL DEFAULT 'ACTIVO' CHECK(estado_vehiculo IN ('ACTIVO','INACTIVO')),
 es_principal BOOLEAN NOT NULL DEFAULT FALSE,
 version_km INTEGER NOT NULL DEFAULT 0 CHECK(version_km>=0),
 creado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CONSTRAINT ck_vin_si_presente CHECK (numero_vin IS NULL OR length(btrim(numero_vin))=17),
 CONSTRAINT uq_vehiculo_id_propietario UNIQUE (id_vehiculo,id_usuario)
);
CREATE INDEX ix_vehiculo_usuario_estado ON mycar.vehiculo(id_usuario,estado_vehiculo,es_principal DESC);
CREATE UNIQUE INDEX uq_vehiculo_principal_usuario ON mycar.vehiculo(id_usuario) WHERE es_principal;

CREATE TABLE mycar.operacion_idempotente (
 id_operacion UUID PRIMARY KEY,
 id_usuario BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
 tipo_operacion VARCHAR(32) NOT NULL CHECK(tipo_operacion IN (
  'REGISTRAR_VEHICULO','ACTUALIZAR_KM','REGISTRAR_MANTENIMIENTO','PROGRAMAR_ALERTA')),
 hash_solicitud CHAR(64) NOT NULL CHECK(hash_solicitud ~ '^[a-f0-9]{64}$'),
 estado VARCHAR(20) NOT NULL DEFAULT 'EN_PROCESO' CHECK(estado IN ('EN_PROCESO','CONFIRMADA')),
 respuesta_json JSONB,
 creado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CONSTRAINT ck_operacion_confirmada_respuesta CHECK (estado<>'CONFIRMADA' OR respuesta_json IS NOT NULL)
);
CREATE INDEX ix_operacion_usuario_tipo ON mycar.operacion_idempotente(id_usuario,tipo_operacion,creado_utc DESC);
CREATE TABLE mycar.lectura_kilometraje (
 id_lectura_km BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 id_vehiculo BIGINT NOT NULL REFERENCES mycar.vehiculo(id_vehiculo) ON DELETE RESTRICT,
 valor_km INTEGER NOT NULL CHECK(valor_km>0),
 origen VARCHAR(24) NOT NULL CHECK(origen IN ('INICIAL','ACTUALIZACION')),
 fecha_lectura_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 id_operacion UUID NOT NULL REFERENCES mycar.operacion_idempotente(id_operacion) ON DELETE RESTRICT,
 CONSTRAINT uq_lectura_id_vehiculo UNIQUE(id_lectura_km,id_vehiculo),
 CONSTRAINT uq_lectura_id_operacion UNIQUE(id_operacion)
);
CREATE UNIQUE INDEX uq_lectura_inicial_por_vehiculo ON mycar.lectura_kilometraje(id_vehiculo) WHERE origen='INICIAL';
CREATE INDEX ix_lectura_vehiculo_fecha ON mycar.lectura_kilometraje(id_vehiculo,fecha_lectura_utc DESC,id_lectura_km DESC);
CREATE TABLE mycar.config_recordatorio_km (
 id_vehiculo BIGINT PRIMARY KEY REFERENCES mycar.vehiculo(id_vehiculo) ON DELETE RESTRICT,
 frecuencia VARCHAR(24) NOT NULL DEFAULT 'INTERDIARIO' CHECK(frecuencia='INTERDIARIO'),
 estado VARCHAR(12) NOT NULL DEFAULT 'PAUSADO' CHECK(estado IN ('PAUSADO','ACTIVO')),
 fecha_base_utc TIMESTAMPTZ NOT NULL,
 proximo_envio_utc TIMESTAMPTZ,
 ultimo_envio_utc TIMESTAMPTZ
);

-- 04. Mantenimientos y repuestos (RF17, RF18)
CREATE TABLE mycar.mantenimiento (
 id_mantenimiento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 id_vehiculo BIGINT NOT NULL REFERENCES mycar.vehiculo(id_vehiculo) ON DELETE RESTRICT,
 id_tipo_mantenimiento BIGINT NOT NULL REFERENCES mycar.tipo_mantenimiento(id_tipo_mantenimiento),
 km_servicio INTEGER NOT NULL CHECK(km_servicio>0),
 fecha_servicio DATE NOT NULL,
 origen_km_servicio VARCHAR(30) NOT NULL CHECK(origen_km_servicio IN ('LECTURA','HISTORICO_MANUAL')),
 id_lectura_km_referencia BIGINT,
 costo_total NUMERIC(12,2) CHECK(costo_total>=0),
 servicio_otro VARCHAR(160),
 estado VARCHAR(16) NOT NULL DEFAULT 'REALIZADO' CHECK(estado IN ('REALIZADO','ANULADO')),
 descripcion TEXT,
 proxima_fecha_sugerida DATE,
 proximo_km_sugerido INTEGER,
 CONSTRAINT uq_mantenimiento_id_vehiculo UNIQUE(id_mantenimiento,id_vehiculo),
 CONSTRAINT fk_mantenimiento_lectura_mismo_vehiculo FOREIGN KEY(id_lectura_km_referencia,id_vehiculo)
  REFERENCES mycar.lectura_kilometraje(id_lectura_km,id_vehiculo) ON DELETE RESTRICT,
 CONSTRAINT ck_mantenimiento_lectura_condicional CHECK (
  (origen_km_servicio='LECTURA' AND id_lectura_km_referencia IS NOT NULL)
  OR (origen_km_servicio='HISTORICO_MANUAL' AND id_lectura_km_referencia IS NULL)
 ),
 CONSTRAINT ck_mantenimiento_fecha_recomendada CHECK(proxima_fecha_sugerida IS NULL OR proxima_fecha_sugerida>fecha_servicio),
 CONSTRAINT ck_mantenimiento_km_recomendado CHECK(proximo_km_sugerido IS NULL OR proximo_km_sugerido>km_servicio)
);
CREATE INDEX ix_mantenimiento_vehiculo_fecha ON mycar.mantenimiento(id_vehiculo,fecha_servicio DESC,id_mantenimiento DESC);
CREATE INDEX ix_mantenimiento_ultimo_por_tipo ON mycar.mantenimiento(id_vehiculo,id_tipo_mantenimiento,fecha_servicio DESC,id_mantenimiento DESC) WHERE estado='REALIZADO';

CREATE TABLE mycar.repuesto (
 id_repuesto BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 id_usuario BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
 nombre VARCHAR(140) NOT NULL CHECK(length(btrim(nombre))>0),
 marca VARCHAR(120),
 CONSTRAINT uq_repuesto_id_propietario UNIQUE(id_repuesto,id_usuario)
);
CREATE TABLE mycar.mantenimiento_repuesto (
 id_mantenimiento BIGINT NOT NULL REFERENCES mycar.mantenimiento(id_mantenimiento) ON DELETE RESTRICT,
 id_repuesto BIGINT NOT NULL REFERENCES mycar.repuesto(id_repuesto) ON DELETE RESTRICT,
 nombre_snapshot VARCHAR(140) NOT NULL CHECK(length(btrim(nombre_snapshot))>0),
 cantidad INTEGER NOT NULL CHECK(cantidad>0),
 precio_unitario NUMERIC(12,2) CHECK(precio_unitario>=0),
 PRIMARY KEY(id_mantenimiento,id_repuesto)
);

-- 05. Alertas / evidencia / historial (RF22, RF23)
CREATE TABLE mycar.alerta (
 id_alerta BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 id_vehiculo BIGINT NOT NULL REFERENCES mycar.vehiculo(id_vehiculo) ON DELETE RESTRICT,
 id_tipo_mantenimiento BIGINT NOT NULL REFERENCES mycar.tipo_mantenimiento(id_tipo_mantenimiento),
 criterio VARCHAR(24) NOT NULL CHECK(criterio IN ('POR_TIEMPO','POR_KILOMETRAJE','AMBOS')),
 origen VARCHAR(24) NOT NULL DEFAULT 'MANUAL' CHECK(origen='MANUAL'),
 categoria VARCHAR(32) NOT NULL DEFAULT 'MANTENIMIENTO' CHECK(categoria='MANTENIMIENTO'),
 servicio_otro VARCHAR(160),
 unidad_tiempo VARCHAR(24),
 cantidad_tiempo INTEGER,
 fecha_base DATE,
 id_mantenimiento_referencia BIGINT,
 creada_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 fecha_objetivo DATE,
 km_objetivo INTEGER,
 km_base INTEGER,
 intervalo_km INTEGER,
 estado VARCHAR(16) NOT NULL CHECK(estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA','ANULADA','REALIZADA')),
 fecha_vencida_verificable DATE,
 fecha_estado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 version_fila INTEGER NOT NULL DEFAULT 0 CHECK(version_fila>=0),
 id_lectura_vencida_evidencia BIGINT,
 firma_equivalencia CHAR(64) NOT NULL CHECK(firma_equivalencia ~ '^[a-f0-9]{64}$'),
 CONSTRAINT fk_alerta_mantenimiento_mismo_vehiculo FOREIGN KEY(id_mantenimiento_referencia,id_vehiculo)
  REFERENCES mycar.mantenimiento(id_mantenimiento,id_vehiculo) ON DELETE RESTRICT,
 CONSTRAINT fk_alerta_lectura_mismo_vehiculo FOREIGN KEY(id_lectura_vencida_evidencia,id_vehiculo)
  REFERENCES mycar.lectura_kilometraje(id_lectura_km,id_vehiculo) ON DELETE RESTRICT,
 CONSTRAINT ck_alerta_tiempo CHECK (
  (criterio='POR_KILOMETRAJE' AND unidad_tiempo IS NULL AND cantidad_tiempo IS NULL AND fecha_base IS NULL AND fecha_objetivo IS NULL)
  OR (criterio IN ('POR_TIEMPO','AMBOS') AND unidad_tiempo IN ('SEMANAS','MESES')
      AND unidad_tiempo IS NOT NULL AND cantidad_tiempo IS NOT NULL
      AND cantidad_tiempo>0 AND fecha_base IS NOT NULL AND fecha_objetivo IS NOT NULL)
 ),
 CONSTRAINT ck_alerta_km CHECK (
  (criterio='POR_TIEMPO' AND km_base IS NULL AND intervalo_km IS NULL AND km_objetivo IS NULL)
  OR (criterio IN ('POR_KILOMETRAJE','AMBOS') AND km_base IS NOT NULL
     AND intervalo_km IS NOT NULL AND km_objetivo IS NOT NULL
     AND km_base>0 AND intervalo_km>0 AND km_objetivo=km_base+intervalo_km AND km_objetivo>km_base)
 ),
 CONSTRAINT ck_alerta_vencida_evidencia CHECK (
   (id_lectura_vencida_evidencia IS NULL OR fecha_vencida_verificable IS NOT NULL)
   AND (estado NOT IN ('VENCIDA','ANULADA') OR fecha_vencida_verificable IS NOT NULL)
 )
);
CREATE UNIQUE INDEX uq_alerta_equivalente_activa ON mycar.alerta(id_vehiculo,firma_equivalencia)
 WHERE estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA');
CREATE INDEX ix_alerta_vehiculo_estado ON mycar.alerta(id_vehiculo,estado,creada_utc DESC);
CREATE TABLE mycar.historial_alerta (
 id_historial_alerta BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 id_alerta BIGINT NOT NULL REFERENCES mycar.alerta(id_alerta) ON DELETE RESTRICT,
 estado_anterior VARCHAR(16),
 estado_nuevo VARCHAR(16) NOT NULL CHECK(estado_nuevo IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA','ANULADA','REALIZADA')),
 motivo VARCHAR(48) NOT NULL CHECK(length(btrim(motivo))>0),
 fecha_cambio_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 id_lectura_evidencia BIGINT REFERENCES mycar.lectura_kilometraje(id_lectura_km) ON DELETE RESTRICT,
 version_destino INTEGER NOT NULL CHECK(version_destino>=0),
 CONSTRAINT uq_historial_alerta_version UNIQUE(id_alerta,version_destino),
 CONSTRAINT ck_historial_inicial CHECK (
  (version_destino=0 AND estado_anterior IS NULL)
  OR (version_destino>0 AND estado_anterior IS NOT NULL AND estado_anterior<>estado_nuevo)
 )
);
CREATE UNIQUE INDEX uq_historial_alerta_inicial ON mycar.historial_alerta(id_alerta) WHERE estado_anterior IS NULL;
CREATE INDEX ix_historial_alerta_eventos ON mycar.historial_alerta(id_alerta,fecha_cambio_utc DESC);
