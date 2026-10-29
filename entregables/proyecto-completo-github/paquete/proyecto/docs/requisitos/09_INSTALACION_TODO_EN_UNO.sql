-- MYCAR MVP · INSTALACIÓN TODO EN UNO · ejecutar dentro de la BD VACÍA mycar_mvp.
-- Incluye 21 tablas, catálogos y funciones; una transacción GLOBAL. NO DROP.
BEGIN;

-- =================== PARTE 1 / 01_ESQUEMA_MYCAR.sql ===================
-- MYCAR MVP · POSTGRESQL 14+ · 11 RF · 21 tablas
-- MODELO FÍSICO INICIAL DERIVADO DEL DICCIONARIO APROBADO (26-10-2026).
-- Ejecutar SOLO en una base VACÍA DE NEGOCIO, con el usuario dueño de la BD.
-- NO usa DROP ni elimina datos. No es una migración para una BD existente.
-- Si el equipo utiliza Flyway, guardar este archivo como la primera migración NUEVA,
-- sin ejecutarlo también manualmente. El servicio Spring Boot hace las transacciones.
SET LOCAL TIME ZONE 'UTC';
DO $preflight$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='mycar') THEN
    RAISE EXCEPTION 'El esquema mycar ya tiene tablas: no ejecutar instalación inicial encima';
  END IF;
END;
$preflight$;
CREATE SCHEMA IF NOT EXISTS mycar;
SET LOCAL search_path TO mycar, pg_catalog;

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
 login_bloqueo_hasta_utc TIMESTAMPTZ
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
 anio_fabricacion INTEGER NOT NULL CHECK (anio_fabricacion >= 1886),
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

-- 06. Funciones deterministas: estados de alertas (los COMMIT son del servicio Spring)
CREATE OR REPLACE FUNCTION mycar.estado_por_tiempo(p_objetivo DATE,p_hoy DATE)
RETURNS VARCHAR LANGUAGE SQL IMMUTABLE AS $$
 SELECT CASE WHEN p_hoy>=p_objetivo+15 THEN 'VENCIDA'
             WHEN p_hoy>=p_objetivo-7 THEN 'PENDIENTE'
             WHEN p_hoy>=p_objetivo-30 THEN 'PROXIMA'
             ELSE 'PROGRAMADA' END::VARCHAR;
$$;
CREATE OR REPLACE FUNCTION mycar.estado_por_km(p_objetivo INTEGER,p_intervalo INTEGER,p_valor INTEGER)
RETURNS VARCHAR LANGUAGE SQL IMMUTABLE AS $$
 SELECT CASE WHEN 10::BIGINT*(p_valor::BIGINT-p_objetivo::BIGINT)>=p_intervalo::BIGINT AND p_valor>=p_objetivo THEN 'VENCIDA'
             WHEN p_valor>=p_objetivo THEN 'PENDIENTE'
             WHEN 10::BIGINT*(p_objetivo::BIGINT-p_valor::BIGINT)<=p_intervalo::BIGINT THEN 'PROXIMA'
             ELSE 'PROGRAMADA' END::VARCHAR;
$$;
CREATE OR REPLACE FUNCTION mycar.prioridad_estado_alerta(p_estado VARCHAR)
RETURNS INTEGER LANGUAGE SQL IMMUTABLE AS $$
 SELECT CASE p_estado WHEN 'PROGRAMADA' THEN 1 WHEN 'PROXIMA' THEN 2
   WHEN 'PENDIENTE' THEN 3 WHEN 'VENCIDA' THEN 4 ELSE 0 END;
$$;
CREATE OR REPLACE FUNCTION mycar.calcular_estado_alerta(
 p_criterio VARCHAR,p_objetivo_fecha DATE,p_hoy DATE,
 p_objetivo_km INTEGER,p_intervalo INTEGER,p_km INTEGER)
RETURNS VARCHAR LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE s_fecha VARCHAR; s_km VARCHAR;
BEGIN
 IF p_criterio='POR_TIEMPO' THEN RETURN mycar.estado_por_tiempo(p_objetivo_fecha,p_hoy); END IF;
 IF p_criterio='POR_KILOMETRAJE' THEN RETURN mycar.estado_por_km(p_objetivo_km,p_intervalo,p_km); END IF;
 IF p_criterio<>'AMBOS' THEN RAISE EXCEPTION 'Criterio desconocido'; END IF;
 s_fecha:=mycar.estado_por_tiempo(p_objetivo_fecha,p_hoy);
 s_km:=mycar.estado_por_km(p_objetivo_km,p_intervalo,p_km);
 IF mycar.prioridad_estado_alerta(s_fecha)>=mycar.prioridad_estado_alerta(s_km) THEN RETURN s_fecha; END IF;
 RETURN s_km;
END $$;

-- 07. Triggers de integridad no expresable mediante CHECK entre tablas.
CREATE OR REPLACE FUNCTION mycar.tr_usuario_edad() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.fecha_nacimiento IS NULL OR
  age((clock_timestamp() AT TIME ZONE 'America/Lima')::DATE,NEW.fecha_nacimiento)<interval '18 years'
  OR age((clock_timestamp() AT TIME ZONE 'America/Lima')::DATE,NEW.fecha_nacimiento)>interval '86 years'
  OR NEW.fecha_nacimiento<=((clock_timestamp() AT TIME ZONE 'America/Lima')::DATE - INTERVAL '86 years')::DATE
 THEN RAISE EXCEPTION 'RF01: edad requerida entre 18 y 85 años'; END IF;
 RETURN NEW;
END $$;
-- Validación de edad se complementa en Spring Boot (edad 18-85 inclusive).
CREATE TRIGGER tg_usuario_edad BEFORE INSERT OR UPDATE OF fecha_nacimiento ON mycar.usuario
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_usuario_edad();

CREATE OR REPLACE FUNCTION mycar.tr_validar_vehiculo() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_motor RECORD; v_comb VARCHAR; v_aceite VARCHAR; v_hoy INTEGER;
BEGIN
 SELECT codigo,es_electrico INTO v_motor FROM mycar.tipo_motor WHERE id_tipo_motor=NEW.id_tipo_motor;
 IF NOT FOUND THEN RAISE EXCEPTION 'Motor no existe'; END IF;
 v_hoy:=EXTRACT(YEAR FROM (clock_timestamp() AT TIME ZONE 'America/Lima'))::INTEGER;
 IF NEW.anio_fabricacion>v_hoy THEN RAISE EXCEPTION 'El año de fabricación no puede ser futuro'; END IF;
 IF v_motor.codigo='OTRO' AND nullif(btrim(coalesce(NEW.tipo_motor_otro,'')),'') IS NULL THEN
  RAISE EXCEPTION 'Motor OTRO requiere detalle'; END IF;
 IF v_motor.codigo<>'OTRO' AND NEW.tipo_motor_otro IS NOT NULL THEN RAISE EXCEPTION 'Detalle de motor solo aplica a OTRO'; END IF;
 IF v_motor.es_electrico THEN
  IF NEW.id_combustible IS NOT NULL OR NEW.combustible_otro IS NOT NULL OR
     NEW.id_tipo_aceite IS NOT NULL OR NEW.tipo_aceite_otro IS NOT NULL OR NEW.numero_motor IS NOT NULL THEN
    RAISE EXCEPTION 'Motor eléctrico: combustible/aceite/número motor no aplican'; END IF;
 ELSE
  IF NEW.id_combustible IS NULL THEN RAISE EXCEPTION 'Combustible obligatorio salvo motor eléctrico'; END IF;
  SELECT codigo INTO v_comb FROM mycar.combustible WHERE id_combustible=NEW.id_combustible;
  IF v_comb='OTRO' AND nullif(btrim(coalesce(NEW.combustible_otro,'')),'') IS NULL THEN
    RAISE EXCEPTION 'Combustible OTRO requiere detalle'; END IF;
  IF v_comb<>'OTRO' AND NEW.combustible_otro IS NOT NULL THEN RAISE EXCEPTION 'Detalle combustible solo para OTRO'; END IF;
  IF NEW.id_tipo_aceite IS NOT NULL THEN
   SELECT codigo INTO v_aceite FROM mycar.tipo_aceite WHERE id_tipo_aceite=NEW.id_tipo_aceite;
   IF v_aceite='OTRO' AND nullif(btrim(coalesce(NEW.tipo_aceite_otro,'')),'') IS NULL THEN
     RAISE EXCEPTION 'Aceite OTRO requiere detalle'; END IF;
   IF v_aceite<>'OTRO' AND NEW.tipo_aceite_otro IS NOT NULL THEN RAISE EXCEPTION 'Detalle aceite solo para OTRO'; END IF;
  ELSIF NEW.tipo_aceite_otro IS NOT NULL THEN RAISE EXCEPTION 'No hay aceite OTRO seleccionado'; END IF;
 END IF;
 IF TG_OP='UPDATE' THEN
  IF NEW.id_usuario<>OLD.id_usuario THEN RAISE EXCEPTION 'Propietario de vehículo es inmutable'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_vehiculo_validacion BEFORE INSERT OR UPDATE ON mycar.vehiculo
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_validar_vehiculo();

-- La fuente de odómetro es append-only. Las operaciones nuevas se serializan por vehículo.
CREATE OR REPLACE FUNCTION mycar.tr_validar_lectura() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_estado VARCHAR;v_ultima INTEGER;v_prop BIGINT;v_op RECORD;
BEGIN
 SELECT estado_vehiculo,id_usuario INTO v_estado,v_prop FROM mycar.vehiculo
  WHERE id_vehiculo=NEW.id_vehiculo FOR UPDATE;
 IF NOT FOUND OR v_estado<>'ACTIVO' THEN RAISE EXCEPTION 'Lectura solo para vehículo activo'; END IF;
 SELECT id_usuario,tipo_operacion INTO v_op FROM mycar.operacion_idempotente WHERE id_operacion=NEW.id_operacion;
 IF NOT FOUND THEN RAISE EXCEPTION 'No hay operación idempotente para la lectura'; END IF;
 IF v_op.id_usuario<>v_prop OR
   (NEW.origen='INICIAL' AND v_op.tipo_operacion<>'REGISTRAR_VEHICULO') OR
   (NEW.origen='ACTUALIZACION' AND v_op.tipo_operacion<>'ACTUALIZAR_KM') THEN
   RAISE EXCEPTION 'Operación idempotente no pertenece a lectura/usuario'; END IF;
 SELECT valor_km INTO v_ultima FROM mycar.lectura_kilometraje WHERE id_vehiculo=NEW.id_vehiculo
   ORDER BY fecha_lectura_utc DESC,id_lectura_km DESC LIMIT 1;
 IF NEW.origen='INICIAL' AND v_ultima IS NOT NULL THEN RAISE EXCEPTION 'La lectura inicial ya existe'; END IF;
 IF NEW.origen='ACTUALIZACION' AND (v_ultima IS NULL OR NEW.valor_km<=v_ultima) THEN
   RAISE EXCEPTION 'RF14 requiere kilometraje estrictamente mayor al vigente'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_lectura_insert BEFORE INSERT ON mycar.lectura_kilometraje
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_validar_lectura();
CREATE OR REPLACE FUNCTION mycar.tr_lectura_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Lecturas KM de RF08/RF14 son inmutables; no UPDATE/DELETE'; END $$;
CREATE TRIGGER tg_lectura_no_update BEFORE UPDATE OR DELETE ON mycar.lectura_kilometraje
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_lectura_inmutable();

CREATE OR REPLACE FUNCTION mycar.tr_validar_mantenimiento() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_lectura RECORD; v_tipo VARCHAR;
BEGIN
 -- Serializa altas RF17 y altas RF22 sobre el mismo vehículo; service debe respetar mismo orden.
 PERFORM 1 FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo FOR UPDATE;
 IF TG_OP='UPDATE' THEN
  IF NEW.id_vehiculo<>OLD.id_vehiculo THEN RAISE EXCEPTION 'No cambiar vehículo del mantenimiento'; END IF;
 END IF;
 IF TG_OP='INSERT' AND NOT EXISTS (SELECT 1 FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo AND estado_vehiculo='ACTIVO') THEN
  RAISE EXCEPTION 'RF17 registra solo sobre vehículo activo'; END IF;
 IF NEW.fecha_servicio>(clock_timestamp() AT TIME ZONE 'America/Lima')::DATE THEN
  RAISE EXCEPTION 'Fecha de mantenimiento no puede ser futura'; END IF;
 SELECT codigo INTO v_tipo FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento=NEW.id_tipo_mantenimiento;
 IF v_tipo='OTRO' AND nullif(btrim(coalesce(NEW.servicio_otro,'')),'') IS NULL THEN RAISE EXCEPTION 'Servicio OTRO requiere especificación'; END IF;
 IF v_tipo<>'OTRO' AND NEW.servicio_otro IS NOT NULL THEN RAISE EXCEPTION 'Servicio OTRO solo con tipo OTRO'; END IF;
 IF NEW.origen_km_servicio='LECTURA' THEN
  SELECT valor_km,fecha_lectura_utc INTO v_lectura FROM mycar.lectura_kilometraje
    WHERE id_lectura_km=NEW.id_lectura_km_referencia AND id_vehiculo=NEW.id_vehiculo;
  IF NOT FOUND THEN RAISE EXCEPTION 'Lectura de mantenimiento no existe en este vehículo'; END IF;
  IF v_lectura.valor_km<>NEW.km_servicio THEN
    RAISE EXCEPTION 'KM servicio debe coincidir con lectura del mismo vehículo'; END IF;
  IF NEW.fecha_servicio < (v_lectura.fecha_lectura_utc AT TIME ZONE 'America/Lima')::DATE THEN
    RAISE EXCEPTION 'Fecha de servicio no compatible con lectura seleccionada'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_mantenimiento_validar BEFORE INSERT OR UPDATE ON mycar.mantenimiento
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_validar_mantenimiento();

CREATE OR REPLACE FUNCTION mycar.tr_repuesto_propietario() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_prop_m BIGINT;v_prop_r BIGINT;
BEGIN
 SELECT v.id_usuario INTO v_prop_m FROM mycar.mantenimiento m
  JOIN mycar.vehiculo v ON v.id_vehiculo=m.id_vehiculo
  WHERE m.id_mantenimiento=NEW.id_mantenimiento;
 SELECT id_usuario INTO v_prop_r FROM mycar.repuesto WHERE id_repuesto=NEW.id_repuesto;
 IF v_prop_m IS NULL OR v_prop_r IS NULL OR v_prop_m<>v_prop_r THEN
  RAISE EXCEPTION 'Repuesto no pertenece al dueño del mantenimiento'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_mantenimiento_repuesto_prop BEFORE INSERT OR UPDATE ON mycar.mantenimiento_repuesto
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_repuesto_propietario();

CREATE OR REPLACE FUNCTION mycar.tr_repuesto_propietario_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.id_usuario<>OLD.id_usuario THEN RAISE EXCEPTION 'No reasignar propietario de repuesto'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_repuesto_propietario_inmutable BEFORE UPDATE OF id_usuario ON mycar.repuesto
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_repuesto_propietario_inmutable();

CREATE OR REPLACE FUNCTION mycar.tr_validar_alerta() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_tipo VARCHAR;v_ref RECORD;v_ultima BIGINT;v_obj DATE;v_km_actual INTEGER;v_estado_inicial VARCHAR;
BEGIN
 -- Bloquear vehículo antes de validar última referencia: RF17 y RF22 toman el mismo candado.
 PERFORM 1 FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo FOR UPDATE;
 IF TG_OP='INSERT' AND NOT EXISTS (SELECT 1 FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo AND estado_vehiculo='ACTIVO') THEN
   RAISE EXCEPTION 'RF22 solo programa sobre vehículo activo'; END IF;
 SELECT codigo INTO v_tipo FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento=NEW.id_tipo_mantenimiento;
 IF v_tipo='OTRO' AND nullif(btrim(coalesce(NEW.servicio_otro,'')),'') IS NULL THEN
  RAISE EXCEPTION 'Alerta OTRO requiere servicio específico'; END IF;
 IF v_tipo<>'OTRO' AND NEW.servicio_otro IS NOT NULL THEN RAISE EXCEPTION 'Servicio específico solo para OTRO'; END IF;
 IF NEW.criterio IN ('POR_TIEMPO','AMBOS') THEN
  IF NEW.unidad_tiempo='SEMANAS' THEN
   v_obj:=NEW.fecha_base + NEW.cantidad_tiempo*7;
  ELSE
   v_obj:=(NEW.fecha_base+make_interval(months=>NEW.cantidad_tiempo))::DATE;
  END IF;
  IF NEW.fecha_objetivo<>v_obj THEN RAISE EXCEPTION 'Fecha objetivo no coincide con base/intervalo'; END IF;
  IF TG_OP='INSERT' AND NEW.fecha_objetivo<=(clock_timestamp() AT TIME ZONE 'America/Lima')::DATE THEN
    RAISE EXCEPTION 'Fecha objetivo debe ser futura al crear alerta'; END IF;
 END IF;
 IF TG_OP='INSERT' AND NEW.id_mantenimiento_referencia IS NOT NULL THEN
   SELECT m.id_mantenimiento,m.estado,m.id_tipo_mantenimiento,m.servicio_otro
    INTO v_ref FROM mycar.mantenimiento m WHERE m.id_mantenimiento=NEW.id_mantenimiento_referencia AND m.id_vehiculo=NEW.id_vehiculo;
   IF NOT FOUND THEN RAISE EXCEPTION 'Referencia mantenimiento no existe en este vehículo'; END IF;
   IF v_ref.estado<>'REALIZADO' OR v_ref.id_tipo_mantenimiento<>NEW.id_tipo_mantenimiento THEN
      RAISE EXCEPTION 'Mantenimiento de referencia no coincide/ no está REALIZADO'; END IF;
   IF v_tipo='OTRO' AND lower(btrim(coalesce(v_ref.servicio_otro,'')))<>lower(btrim(coalesce(NEW.servicio_otro,''))) THEN
      RAISE EXCEPTION 'OTRO no coincide con mantenimiento de referencia'; END IF;
   SELECT m.id_mantenimiento INTO v_ultima FROM mycar.mantenimiento m
    WHERE m.id_vehiculo=NEW.id_vehiculo AND m.id_tipo_mantenimiento=NEW.id_tipo_mantenimiento
      AND m.estado='REALIZADO' AND
      (v_tipo<>'OTRO' OR lower(btrim(coalesce(m.servicio_otro,'')))=lower(btrim(coalesce(NEW.servicio_otro,''))))
    ORDER BY m.fecha_servicio DESC,m.id_mantenimiento DESC LIMIT 1;
   IF v_ultima IS DISTINCT FROM NEW.id_mantenimiento_referencia THEN
     RAISE EXCEPTION 'El mantenimiento de referencia ya no es el último REALIZADO'; END IF;
 END IF;
 IF TG_OP='INSERT' THEN
  SELECT valor_km INTO v_km_actual FROM mycar.lectura_kilometraje WHERE id_vehiculo=NEW.id_vehiculo
      ORDER BY fecha_lectura_utc DESC,id_lectura_km DESC LIMIT 1;
  IF v_km_actual IS NULL THEN RAISE EXCEPTION 'No hay lectura base del vehículo'; END IF;
  IF NEW.criterio IN ('POR_KILOMETRAJE','AMBOS') AND v_km_actual IS DISTINCT FROM NEW.km_base THEN
    RAISE EXCEPTION 'km_base debe coincidir con última lectura'; END IF;
  v_estado_inicial:=mycar.calcular_estado_alerta(NEW.criterio,NEW.fecha_objetivo,
    (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE,NEW.km_objetivo,NEW.intervalo_km,v_km_actual);
  IF NEW.estado IS DISTINCT FROM v_estado_inicial THEN
     RAISE EXCEPTION 'Estado inicial RF22 inválido: esperado %, recibido %',v_estado_inicial,NEW.estado;
  END IF;
  IF NEW.version_fila<>0 THEN RAISE EXCEPTION 'Alerta inicia en version_fila=0'; END IF;
 END IF;
 IF TG_OP='UPDATE' THEN
  IF ROW(NEW.id_vehiculo,NEW.id_tipo_mantenimiento,NEW.criterio,NEW.origen,NEW.categoria,
         NEW.servicio_otro,NEW.unidad_tiempo,NEW.cantidad_tiempo,NEW.fecha_base,
         NEW.fecha_objetivo,NEW.km_objetivo,NEW.km_base,NEW.intervalo_km,
         NEW.id_mantenimiento_referencia,NEW.firma_equivalencia,NEW.creada_utc)
    IS DISTINCT FROM
     ROW(OLD.id_vehiculo,OLD.id_tipo_mantenimiento,OLD.criterio,OLD.origen,OLD.categoria,
         OLD.servicio_otro,OLD.unidad_tiempo,OLD.cantidad_tiempo,OLD.fecha_base,
         OLD.fecha_objetivo,OLD.km_objetivo,OLD.km_base,OLD.intervalo_km,
         OLD.id_mantenimiento_referencia,OLD.firma_equivalencia,OLD.creada_utc) THEN
    RAISE EXCEPTION 'Atributos de identidad/criterio de alerta no se modifican en RF23'; END IF;
  IF OLD.fecha_vencida_verificable IS NOT NULL AND NEW.fecha_vencida_verificable IS DISTINCT FROM OLD.fecha_vencida_verificable THEN
    RAISE EXCEPTION 'Primera fecha VENCIDA es inmutable'; END IF;
  IF NEW.estado<>OLD.estado AND NEW.version_fila<>OLD.version_fila+1 THEN
    RAISE EXCEPTION 'Cada cambio real incrementa version_fila en uno'; END IF;
  IF NEW.estado=OLD.estado AND NEW.version_fila<>OLD.version_fila THEN
    RAISE EXCEPTION 'Sin cambio no se incrementa version_fila'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_alerta_validar BEFORE INSERT OR UPDATE ON mycar.alerta
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_validar_alerta();

CREATE OR REPLACE FUNCTION mycar.tr_validar_evidencia_alerta() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_l RECORD;v_fecha DATE;v_primera BIGINT;
BEGIN
 IF NEW.id_lectura_vencida_evidencia IS NOT NULL THEN
  SELECT id_lectura_km,id_vehiculo,valor_km,fecha_lectura_utc INTO v_l
    FROM mycar.lectura_kilometraje WHERE id_lectura_km=NEW.id_lectura_vencida_evidencia;
  IF NOT FOUND THEN RAISE EXCEPTION 'Lectura evidencia no existe'; END IF;
  IF v_l.id_vehiculo<>NEW.id_vehiculo OR NEW.km_objetivo IS NULL OR
   v_l.fecha_lectura_utc<=NEW.creada_utc OR
   10::BIGINT*(v_l.valor_km::BIGINT-NEW.km_objetivo::BIGINT)<NEW.intervalo_km::BIGINT THEN
    RAISE EXCEPTION 'Lectura no demuestra la VENCIDA de esta alerta'; END IF;
  SELECT l.id_lectura_km INTO v_primera FROM mycar.lectura_kilometraje l
   WHERE l.id_vehiculo=NEW.id_vehiculo AND l.fecha_lectura_utc>NEW.creada_utc
    AND 10::BIGINT*(l.valor_km::BIGINT-NEW.km_objetivo::BIGINT)>=NEW.intervalo_km::BIGINT
   ORDER BY l.fecha_lectura_utc,l.id_lectura_km LIMIT 1;
  IF v_primera IS DISTINCT FROM NEW.id_lectura_vencida_evidencia THEN
    RAISE EXCEPTION 'Debe referenciar primera lectura VENCIDA verificable'; END IF;
  v_fecha:=(v_l.fecha_lectura_utc AT TIME ZONE 'America/Lima')::DATE;
  IF NEW.fecha_vencida_verificable IS DISTINCT FROM v_fecha THEN
    RAISE EXCEPTION 'Fecha VENCIDA KM debe ser fecha local de lectura'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_alerta_evidencia BEFORE INSERT OR UPDATE ON mycar.alerta
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_validar_evidencia_alerta();

CREATE OR REPLACE FUNCTION mycar.tr_historial_evidencia_vehiculo() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_alerta BIGINT; v_lectura BIGINT;
BEGIN
 IF NEW.id_lectura_evidencia IS NOT NULL THEN
  SELECT id_vehiculo INTO v_alerta FROM mycar.alerta WHERE id_alerta=NEW.id_alerta;
  SELECT id_vehiculo INTO v_lectura FROM mycar.lectura_kilometraje WHERE id_lectura_km=NEW.id_lectura_evidencia;
  IF v_alerta IS DISTINCT FROM v_lectura THEN RAISE EXCEPTION 'Historial cita lectura de otro vehículo'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_historial_evidencia BEFORE INSERT OR UPDATE ON mycar.historial_alerta
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_historial_evidencia_vehiculo();
CREATE OR REPLACE FUNCTION mycar.tr_validar_version_historial() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_a RECORD;
BEGIN
 SELECT estado,version_fila INTO v_a FROM mycar.alerta WHERE id_alerta=NEW.id_alerta FOR UPDATE;
 IF NOT FOUND OR v_a.estado IS DISTINCT FROM NEW.estado_nuevo OR
    v_a.version_fila IS DISTINCT FROM NEW.version_destino THEN
    RAISE EXCEPTION 'Historial y estado/version de alerta deben coincidir en la misma transacción';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_historial_version BEFORE INSERT ON mycar.historial_alerta
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_validar_version_historial();
CREATE OR REPLACE FUNCTION mycar.tr_historial_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Historial de alertas append-only'; END $$;
CREATE TRIGGER tg_historial_no_mutar BEFORE UPDATE OR DELETE ON mycar.historial_alerta
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_historial_inmutable();


-- =================== PARTE 2 / 02_CATALOGOS_INICIALES.sql ===================
-- CARGA DE DATOS BASE · ejecutar DESPUÉS de 01_ESQUEMA_MYCAR.sql.
-- Catálogos aprobados: FREE; uso Particular/Taxi; 7 tipos de mantenimiento.
-- Catálogos vehiculares motor/combustible/aceite: OPCIONES TÉCNICAS INICIALES
-- para pruebas y desarrollo; el equipo valida las etiquetas antes de publicar.
INSERT INTO mycar.plan (codigo,nombre,max_vehiculos_registrados,activo)
VALUES ('FREE','Gratis',1,TRUE) ON CONFLICT(codigo) DO NOTHING;
INSERT INTO mycar.uso_vehiculo(codigo,nombre) VALUES
 ('PARTICULAR','Particular'),('TAXI','Taxi') ON CONFLICT(codigo) DO NOTHING;
INSERT INTO mycar.tipo_mantenimiento(codigo,nombre,activo) VALUES
 ('CAMBIO_ACEITE','Cambio de aceite',TRUE),
 ('CAMBIO_FILTROS','Cambio de filtros',TRUE),
 ('REVISION_FRENOS','Revisión de frenos',TRUE),
 ('REVISION_NEUMATICOS','Revisión de neumáticos',TRUE),
 ('ALINEAMIENTO_BALANCEO','Alineamiento y balanceo',TRUE),
 ('MANTENIMIENTO_GENERAL','Mantenimiento general',TRUE),
 ('OTRO','Otro',TRUE)
 ON CONFLICT(codigo) DO NOTHING;
-- Propuestas técnicas de opciones para poner en marcha RF08, no reglas funcionales nuevas.
INSERT INTO mycar.tipo_motor(codigo,nombre,es_electrico) VALUES
 ('COMBUSTION','Combustión',FALSE),('ELECTRICO','Eléctrico',TRUE),('OTRO','Otro',FALSE)
 ON CONFLICT(codigo) DO NOTHING;
INSERT INTO mycar.combustible(codigo,nombre) VALUES
 ('GASOLINA','Gasolina'),('DIESEL','Diésel'),('OTRO','Otro')
 ON CONFLICT(codigo) DO NOTHING;
INSERT INTO mycar.tipo_aceite(codigo,nombre) VALUES
 ('MINERAL','Mineral'),('SEMISINTETICO','Semisintético'),('SINTETICO','Sintético'),('OTRO','Otro')
 ON CONFLICT(codigo) DO NOTHING;

-- =================== PARTE 3 / 03_LOGICA_BD.sql ===================
-- FUNCIONES DE LÓGICA DE BD: RF02 (ventana móvil) y RF23 (sincronización transaccional).
-- Ejecutar tras 01_ESQUEMA_MYCAR.sql. Spring Boot sigue siendo responsable de
-- hash de passwords, tokens, autorización de endpoints e idempotencia de POST.

CREATE OR REPLACE FUNCTION mycar.registrar_fallo_login(p_id_usuario BIGINT)
RETURNS TIMESTAMPTZ LANGUAGE plpgsql AS $$
DECLARE v_u RECORD; v_now TIMESTAMPTZ; v_count INTEGER;
BEGIN
 SELECT id_usuario,estado_cuenta,login_bloqueo_hasta_utc INTO v_u
   FROM mycar.usuario WHERE id_usuario=p_id_usuario FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'No existe usuario para registrar evento de login'; END IF;
 v_now:=clock_timestamp();
 IF v_u.estado_cuenta<>'ACTIVA' THEN RETURN v_u.login_bloqueo_hasta_utc; END IF;
 IF v_u.login_bloqueo_hasta_utc IS NOT NULL AND v_now<v_u.login_bloqueo_hasta_utc THEN
  -- Si está restringido, no verificar passwords ni almacenar más intentos.
  RETURN v_u.login_bloqueo_hasta_utc;
 END IF;
 INSERT INTO mycar.intento_login_fallido(id_usuario,ocurrido_utc) VALUES(p_id_usuario,v_now);
 SELECT count(*) INTO v_count FROM mycar.intento_login_fallido
  WHERE id_usuario=p_id_usuario AND ocurrido_utc>=v_now-INTERVAL '10 minutes' AND ocurrido_utc<=v_now;
 IF v_count>=3 THEN
   UPDATE mycar.usuario SET login_bloqueo_hasta_utc=v_now+INTERVAL '10 minutes' WHERE id_usuario=p_id_usuario;
   RETURN v_now+INTERVAL '10 minutes';
 END IF;
 RETURN NULL;
END $$;

-- IMPORTANTE: antes de verificar contraseña, Spring Boot consulta
-- usuario.login_bloqueo_hasta_utc y cuenta ACTIVA; para el tercer fallo llama
-- esta función y COMMIT. Al vencer un bloqueo, la ventana móvil solo cuenta
-- intentos de los últimos diez minutos. Los login preexistentes no se revocan.
-- Errores públicos genéricos: nunca exponer existencia de correo por la respuesta.

CREATE OR REPLACE FUNCTION mycar.sincronizar_alertas_vehiculo(
  p_id_vehiculo BIGINT,p_id_usuario BIGINT)
RETURNS INTEGER LANGUAGE plpgsql AS $$
DECLARE
 v_v mycar.vehiculo%ROWTYPE; v_a mycar.alerta%ROWTYPE;
 v_hoy DATE; v_km INTEGER; v_fecha_tiempo DATE; v_fecha_km DATE;
 v_id_lectura_km BIGINT; v_fecha_evidencia DATE; v_id_evidencia BIGINT;
 v_nuevo VARCHAR; v_anterior VARCHAR; v_cambios INTEGER:=0; v_now TIMESTAMPTZ;
BEGIN
 -- RF23 es un COMANDO de sincronización, NO un GET puro. Bloqueo serial por vehículo.
 SELECT * INTO v_v FROM mycar.vehiculo
  WHERE id_vehiculo=p_id_vehiculo AND id_usuario=p_id_usuario AND estado_vehiculo='ACTIVO'
  FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Vehículo activo no autorizado para sincronización'; END IF;
 IF NOT EXISTS(SELECT 1 FROM mycar.usuario WHERE id_usuario=p_id_usuario AND estado_cuenta='ACTIVA') THEN
  RAISE EXCEPTION 'Cuenta sin autorización'; END IF;
 v_now:=clock_timestamp();
 v_hoy:=(v_now AT TIME ZONE 'America/Lima')::DATE;
 SELECT valor_km INTO v_km FROM mycar.lectura_kilometraje
   WHERE id_vehiculo=p_id_vehiculo ORDER BY fecha_lectura_utc DESC,id_lectura_km DESC LIMIT 1;
 IF v_km IS NULL THEN RAISE EXCEPTION 'No hay lectura efectiva para el vehículo'; END IF;

 FOR v_a IN SELECT * FROM mycar.alerta
   WHERE id_vehiculo=p_id_vehiculo AND estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
   ORDER BY id_alerta FOR UPDATE
 LOOP
   v_fecha_tiempo:=NULL; v_fecha_km:=NULL; v_id_lectura_km:=NULL;
   IF v_a.criterio IN ('POR_TIEMPO','AMBOS') AND v_hoy>=v_a.fecha_objetivo+15 THEN
      v_fecha_tiempo:=v_a.fecha_objetivo+15;
   END IF;
   IF v_a.criterio IN ('POR_KILOMETRAJE','AMBOS') THEN
     SELECT l.id_lectura_km,(l.fecha_lectura_utc AT TIME ZONE 'America/Lima')::DATE
      INTO v_id_lectura_km,v_fecha_km
      FROM mycar.lectura_kilometraje l
      WHERE l.id_vehiculo=p_id_vehiculo AND l.fecha_lectura_utc>v_a.creada_utc
       AND 10::BIGINT*(l.valor_km::BIGINT-v_a.km_objetivo::BIGINT)>=v_a.intervalo_km::BIGINT
      ORDER BY l.fecha_lectura_utc ASC,l.id_lectura_km ASC LIMIT 1;
   END IF;
   -- Elegir fuente VERIFICABLE más antigua; empate favorece TIEMPO.
   v_fecha_evidencia:=NULL;v_id_evidencia:=NULL;
   IF v_fecha_tiempo IS NOT NULL AND (v_fecha_km IS NULL OR v_fecha_tiempo<=v_fecha_km) THEN
      v_fecha_evidencia:=v_fecha_tiempo;
   ELSIF v_fecha_km IS NOT NULL THEN
      v_fecha_evidencia:=v_fecha_km;v_id_evidencia:=v_id_lectura_km;
   END IF;
   IF v_a.fecha_vencida_verificable IS NOT NULL THEN
      -- No recalcular ni mover hacia adelante la primera evidencia guardada.
      v_fecha_evidencia:=v_a.fecha_vencida_verificable;
      v_id_evidencia:=v_a.id_lectura_vencida_evidencia;
   END IF;
   v_nuevo:=mycar.calcular_estado_alerta(v_a.criterio,v_a.fecha_objetivo,v_hoy,
                        v_a.km_objetivo,v_a.intervalo_km,v_km);
   -- Si existe evidencia VENCIDA, aún sin sincronizaciones intermedias, adoptar VENCIDA.
   IF v_fecha_evidencia IS NOT NULL AND mycar.prioridad_estado_alerta(v_nuevo)<4 THEN
      v_nuevo:='VENCIDA';
   END IF;
   IF v_fecha_evidencia IS NOT NULL AND v_hoy>=v_fecha_evidencia+30 THEN
      v_nuevo:='ANULADA';
   END IF;
   IF v_nuevo IS DISTINCT FROM v_a.estado THEN
      v_anterior:=v_a.estado;
      -- El UPDATE y la inserción del historial ocurren en ESTA misma transacción.
      UPDATE mycar.alerta SET estado=v_nuevo,version_fila=v_a.version_fila+1,
        fecha_estado_utc=v_now,
        fecha_vencida_verificable=coalesce(v_a.fecha_vencida_verificable,v_fecha_evidencia),
        id_lectura_vencida_evidencia=coalesce(v_a.id_lectura_vencida_evidencia,v_id_evidencia)
      WHERE id_alerta=v_a.id_alerta;
      INSERT INTO mycar.historial_alerta
       (id_alerta,estado_anterior,estado_nuevo,motivo,fecha_cambio_utc,id_lectura_evidencia,version_destino)
      VALUES(v_a.id_alerta,v_anterior,v_nuevo,
        CASE WHEN v_nuevo='ANULADA' THEN 'VENCIMIENTO_30_DIAS' ELSE 'SINCRONIZACION_RF23' END,
        v_now,v_id_evidencia,v_a.version_fila+1);
      v_cambios:=v_cambios+1;
   END IF;
 END LOOP;
 RETURN v_cambios;
END $$;

COMMIT;
