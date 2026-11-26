-- Fase 2: licencia de conducir (manual A-I / A-IIa) + alerta LICENCIA (sin vehculo).
-- Solo migraciones locales / PG de prueba. No ejecutar contra Neon/Render Live desde CI de este worktree.

-- ---------------------------------------------------------------------------
-- 1) Idempotencia: tipos licencia
-- ---------------------------------------------------------------------------
ALTER TABLE mycar.operacion_idempotente
    DROP CONSTRAINT IF EXISTS operacion_idempotente_tipo_operacion_check;

ALTER TABLE mycar.operacion_idempotente
    ADD CONSTRAINT operacion_idempotente_tipo_operacion_check
        CHECK (tipo_operacion IN (
            'REGISTRAR_VEHICULO',
            'ACTUALIZAR_KM',
            'CORREGIR_KM',
            'REGISTRAR_MANTENIMIENTO',
            'PROGRAMAR_ALERTA',
            'ANULAR_ALERTA',
            'REGISTRAR_SOAT',
            'RENOVAR_SOAT',
            'REGISTRAR_LICENCIA',
            'RENOVAR_LICENCIA'
        ));

-- ---------------------------------------------------------------------------
-- 2) Tabla licencia (ACTUAL + HISTORICO)
-- ---------------------------------------------------------------------------
CREATE TABLE mycar.licencia (
    id_licencia              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario               BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
    categoria                VARCHAR(8) NOT NULL CHECK (categoria IN ('A-I', 'A-IIa')),
    numero_cifrado           TEXT NOT NULL,
    numero_blind             VARCHAR(64) NOT NULL,
    crypto_key_id            VARCHAR(16),
    fecha_emision            DATE NOT NULL,
    fecha_vencimiento        DATE NOT NULL,
    vigencia                 VARCHAR(16) NOT NULL CHECK (vigencia IN ('ACTUAL', 'HISTORICO')),
    recordatorio_activo      BOOLEAN NOT NULL DEFAULT FALSE,
    version_fila             INTEGER NOT NULL DEFAULT 0 CHECK (version_fila >= 0),
    foto_frontal_key         VARCHAR(512),
    foto_frontal_content_type VARCHAR(120),
    foto_frontal_tamano      INTEGER CHECK (foto_frontal_tamano IS NULL OR foto_frontal_tamano > 0),
    foto_frontal_nombre      VARCHAR(260),
    foto_posterior_key       VARCHAR(512),
    foto_posterior_content_type VARCHAR(120),
    foto_posterior_tamano    INTEGER CHECK (foto_posterior_tamano IS NULL OR foto_posterior_tamano > 0),
    foto_posterior_nombre    VARCHAR(260),
    documento_cifrado        BOOLEAN NOT NULL DEFAULT FALSE,
    creado_utc               TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actualizado_utc          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_licencia_fechas CHECK (fecha_vencimiento > fecha_emision),
    CONSTRAINT ck_licencia_foto_frontal CHECK (
        (foto_frontal_key IS NULL AND foto_frontal_content_type IS NULL
            AND foto_frontal_tamano IS NULL AND foto_frontal_nombre IS NULL)
        OR (foto_frontal_key IS NOT NULL AND foto_frontal_content_type IS NOT NULL
            AND foto_frontal_tamano IS NOT NULL AND foto_frontal_nombre IS NOT NULL)
    ),
    CONSTRAINT ck_licencia_foto_posterior CHECK (
        (foto_posterior_key IS NULL AND foto_posterior_content_type IS NULL
            AND foto_posterior_tamano IS NULL AND foto_posterior_nombre IS NULL)
        OR (foto_posterior_key IS NOT NULL AND foto_posterior_content_type IS NOT NULL
            AND foto_posterior_tamano IS NOT NULL AND foto_posterior_nombre IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_licencia_actual_por_usuario
    ON mycar.licencia (id_usuario)
    WHERE vigencia = 'ACTUAL';

CREATE UNIQUE INDEX uq_licencia_numero_actual_usuario
    ON mycar.licencia (id_usuario, numero_blind)
    WHERE vigencia = 'ACTUAL';

CREATE INDEX ix_licencia_usuario_vigencia
    ON mycar.licencia (id_usuario, vigencia, fecha_vencimiento DESC);

COMMENT ON TABLE mycar.licencia IS
    'Licencia de conducir declarada manualmente. Una fila ACTUAL por usuario; HISTORICO sin fotos.';

-- ---------------------------------------------------------------------------
-- 3) Ampliar alerta para categoria LICENCIA (id_vehiculo NULL)
-- ---------------------------------------------------------------------------
ALTER TABLE mycar.alerta
    ALTER COLUMN id_vehiculo DROP NOT NULL;

ALTER TABLE mycar.alerta
    ADD COLUMN IF NOT EXISTS id_licencia BIGINT,
    ADD COLUMN IF NOT EXISTS id_usuario BIGINT;

ALTER TABLE mycar.licencia
    ADD CONSTRAINT uq_licencia_id_usuario UNIQUE (id_licencia, id_usuario);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_alerta_licencia_usuario'
    ) THEN
        ALTER TABLE mycar.alerta
            ADD CONSTRAINT fk_alerta_licencia_usuario
                FOREIGN KEY (id_licencia, id_usuario)
                REFERENCES mycar.licencia (id_licencia, id_usuario)
                ON DELETE RESTRICT;
    END IF;
END $$;

ALTER TABLE mycar.alerta DROP CONSTRAINT IF EXISTS ck_alerta_categoria;
ALTER TABLE mycar.alerta
    ADD CONSTRAINT ck_alerta_categoria
        CHECK (categoria IN ('MANTENIMIENTO', 'SOAT', 'LICENCIA'));

ALTER TABLE mycar.alerta DROP CONSTRAINT IF EXISTS ck_alerta_categoria_coherencia;
ALTER TABLE mycar.alerta
    ADD CONSTRAINT ck_alerta_categoria_coherencia CHECK (
        (categoria = 'MANTENIMIENTO'
            AND id_tipo_mantenimiento IS NOT NULL
            AND id_soat IS NULL
            AND id_licencia IS NULL
            AND id_vehiculo IS NOT NULL)
        OR (categoria = 'SOAT'
            AND id_tipo_mantenimiento IS NULL
            AND id_licencia IS NULL
            AND id_usuario IS NULL
            AND id_soat IS NOT NULL
            AND id_vehiculo IS NOT NULL
            AND criterio = 'POR_TIEMPO'
            AND unidad_tiempo = 'DIAS'
            AND cantidad_tiempo = 30
            AND fecha_base IS NOT NULL
            AND fecha_objetivo IS NOT NULL
            AND km_base IS NULL AND intervalo_km IS NULL AND km_objetivo IS NULL
            AND id_mantenimiento_referencia IS NULL
            AND id_mantenimiento_atencion IS NULL)
        OR (categoria = 'LICENCIA'
            AND id_tipo_mantenimiento IS NULL
            AND id_soat IS NULL
            AND id_licencia IS NOT NULL
            AND id_usuario IS NOT NULL
            AND id_vehiculo IS NULL
            AND criterio = 'POR_TIEMPO'
            AND unidad_tiempo = 'DIAS'
            AND cantidad_tiempo = 30
            AND fecha_base IS NOT NULL
            AND fecha_objetivo IS NOT NULL
            AND km_base IS NULL AND intervalo_km IS NULL AND km_objetivo IS NULL
            AND id_mantenimiento_referencia IS NULL
            AND id_mantenimiento_atencion IS NULL)
    );

ALTER TABLE mycar.alerta DROP CONSTRAINT IF EXISTS ck_alerta_tiempo;
ALTER TABLE mycar.alerta
    ADD CONSTRAINT ck_alerta_tiempo CHECK (
        (categoria IN ('SOAT', 'LICENCIA'))
        OR (criterio = 'POR_KILOMETRAJE'
            AND unidad_tiempo IS NULL AND cantidad_tiempo IS NULL
            AND fecha_base IS NULL AND fecha_objetivo IS NULL)
        OR (criterio IN ('POR_TIEMPO', 'AMBOS')
            AND unidad_tiempo IN ('SEMANAS', 'MESES', 'DIAS')
            AND unidad_tiempo IS NOT NULL AND cantidad_tiempo IS NOT NULL
            AND cantidad_tiempo > 0
            AND fecha_base IS NOT NULL AND fecha_objetivo IS NOT NULL)
    );

ALTER TABLE mycar.alerta DROP CONSTRAINT IF EXISTS ck_alerta_km;
ALTER TABLE mycar.alerta
    ADD CONSTRAINT ck_alerta_km CHECK (
        (categoria IN ('SOAT', 'LICENCIA'))
        OR (criterio = 'POR_TIEMPO'
            AND km_base IS NULL AND intervalo_km IS NULL AND km_objetivo IS NULL)
        OR (criterio IN ('POR_KILOMETRAJE', 'AMBOS')
            AND km_base IS NOT NULL AND intervalo_km IS NOT NULL AND km_objetivo IS NOT NULL
            AND km_base > 0 AND intervalo_km > 0
            AND km_objetivo = km_base + intervalo_km AND km_objetivo > km_base)
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_alerta_licencia_activa
    ON mycar.alerta (id_licencia)
    WHERE categoria = 'LICENCIA'
      AND estado IN ('PROGRAMADA', 'PROXIMA', 'PENDIENTE', 'VENCIDA');

CREATE INDEX IF NOT EXISTS ix_alerta_licencia
    ON mycar.alerta (id_licencia)
    WHERE id_licencia IS NOT NULL;

COMMENT ON COLUMN mycar.alerta.id_licencia IS
    'FK licencia cuando categoria=LICENCIA; id_vehiculo NULL.';

-- Notificaciones internas: LICENCIA sin vehculo pero con id_alerta
ALTER TABLE mycar.notificacion DROP CONSTRAINT IF EXISTS ck_notificacion_alerta_vehiculo;
ALTER TABLE mycar.notificacion
    ADD CONSTRAINT ck_notificacion_alerta_vehiculo
        CHECK (
            id_alerta IS NULL
            OR id_vehiculo IS NOT NULL
            OR categoria = 'LICENCIA'
        );
-- ---------------------------------------------------------------------------
-- 4) Trigger alerta: rama LICENCIA (hereda cuerpo V27)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION mycar.tr_validar_alerta() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  v_tipo VARCHAR;
  v_ref RECORD;
  v_obj DATE;
  v_km_actual INTEGER;
  v_estado_inicial VARCHAR;
  v_hoy DATE;
BEGIN
  IF NEW.categoria = 'LICENCIA' THEN
    PERFORM 1 FROM mycar.usuario WHERE id_usuario = NEW.id_usuario FOR UPDATE;
    v_hoy := (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE;
    IF NEW.id_licencia IS NULL THEN
      RAISE EXCEPTION 'Alerta LICENCIA requiere id_licencia';
    END IF;
    IF NEW.id_vehiculo IS NOT NULL THEN
      RAISE EXCEPTION 'Alerta LICENCIA exige id_vehiculo NULL';
    END IF;
    IF NEW.id_usuario IS NULL THEN
      RAISE EXCEPTION 'Alerta LICENCIA requiere id_usuario';
    END IF;
    IF NEW.criterio <> 'POR_TIEMPO' OR NEW.unidad_tiempo <> 'DIAS' OR NEW.cantidad_tiempo <> 30 THEN
      RAISE EXCEPTION 'Alerta LICENCIA: criterio DIAS/30 invalido';
    END IF;
    v_obj := NEW.fecha_base + NEW.cantidad_tiempo;
    IF NEW.fecha_objetivo <> v_obj THEN
      RAISE EXCEPTION 'Fecha objetivo LICENCIA no coincide con base+30 dias';
    END IF;
    IF TG_OP = 'INSERT' THEN
      v_estado_inicial := mycar.calcular_estado_alerta(
          'POR_TIEMPO', NEW.fecha_objetivo, v_hoy, NULL, NULL, 0);
      IF NEW.estado IS DISTINCT FROM v_estado_inicial THEN
        RAISE EXCEPTION 'Estado inicial LICENCIA invalido: esperado %, recibido %',
            v_estado_inicial, NEW.estado;
      END IF;
      IF NEW.version_fila <> 0 THEN
        RAISE EXCEPTION 'Alerta inicia en version_fila=0';
      END IF;
    END IF;
    RETURN NEW;
  END IF;
  IF NEW.categoria = 'SOAT' THEN
    PERFORM 1 FROM mycar.vehiculo WHERE id_vehiculo = NEW.id_vehiculo FOR UPDATE;
    v_hoy := (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE;
    IF NEW.id_soat IS NULL THEN
      RAISE EXCEPTION 'Alerta SOAT requiere id_soat';
    END IF;
    IF NEW.criterio <> 'POR_TIEMPO' OR NEW.unidad_tiempo <> 'DIAS' OR NEW.cantidad_tiempo <> 30 THEN
      RAISE EXCEPTION 'Alerta SOAT: criterio DIAS/30 invalido';
    END IF;
    v_obj := NEW.fecha_base + NEW.cantidad_tiempo;
    IF NEW.fecha_objetivo <> v_obj THEN
      RAISE EXCEPTION 'Fecha objetivo SOAT no coincide con base+30 dias';
    END IF;
    IF TG_OP = 'INSERT' THEN
      v_estado_inicial := mycar.calcular_estado_alerta(
          'POR_TIEMPO', NEW.fecha_objetivo, v_hoy, NULL, NULL, 0);
      IF NEW.estado IS DISTINCT FROM v_estado_inicial THEN
        RAISE EXCEPTION 'Estado inicial SOAT invalido: esperado %, recibido %',
            v_estado_inicial, NEW.estado;
      END IF;
      IF NEW.version_fila <> 0 THEN
        RAISE EXCEPTION 'Alerta inicia en version_fila=0';
      END IF;
    END IF;
    RETURN NEW;
  END IF;

  PERFORM 1 FROM mycar.vehiculo WHERE id_vehiculo = NEW.id_vehiculo FOR UPDATE;
  v_hoy := (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE;

  IF TG_OP = 'INSERT' AND NOT EXISTS (
      SELECT 1 FROM mycar.vehiculo WHERE id_vehiculo = NEW.id_vehiculo AND estado_vehiculo = 'ACTIVO'
  ) THEN
    RAISE EXCEPTION 'RF22 solo programa sobre vehiculo activo';
  END IF;

  SELECT codigo INTO v_tipo FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento = NEW.id_tipo_mantenimiento;
  IF TG_OP = 'INSERT' AND NOT EXISTS (
      SELECT 1 FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento = NEW.id_tipo_mantenimiento AND activo
  ) THEN
    RAISE EXCEPTION 'Tipo de mantenimiento no activo';
  END IF;
  IF v_tipo = 'OTRO' AND nullif(btrim(coalesce(NEW.servicio_otro, '')), '') IS NULL THEN
    RAISE EXCEPTION 'Alerta OTRO requiere servicio especifico';
  END IF;
  IF v_tipo <> 'OTRO' AND NEW.servicio_otro IS NOT NULL THEN
    RAISE EXCEPTION 'Servicio especifico solo para OTRO';
  END IF;

  IF TG_OP = 'INSERT' THEN
    IF NEW.origen_calculo = 'DESDE_HOY' AND NEW.id_mantenimiento_referencia IS NOT NULL THEN
      RAISE EXCEPTION 'DESDE_HOY no admite mantenimiento de referencia';
    END IF;
    IF NEW.origen_calculo = 'DESDE_MANTENIMIENTO' AND NEW.id_mantenimiento_referencia IS NULL THEN
      RAISE EXCEPTION 'DESDE_MANTENIMIENTO exige mantenimiento de referencia';
    END IF;
  END IF;

  IF NEW.criterio IN ('POR_TIEMPO', 'AMBOS') THEN
    IF NEW.unidad_tiempo = 'SEMANAS' THEN
      v_obj := NEW.fecha_base + NEW.cantidad_tiempo * 7;
    ELSE
      v_obj := (NEW.fecha_base + make_interval(months => NEW.cantidad_tiempo))::DATE;
    END IF;
    IF NEW.fecha_objetivo <> v_obj THEN
      RAISE EXCEPTION 'Fecha objetivo no coincide con base/intervalo';
    END IF;
    IF TG_OP = 'INSERT' AND NEW.fecha_objetivo <= v_hoy THEN
      RAISE EXCEPTION 'Fecha objetivo debe ser futura al crear alerta';
    END IF;
  END IF;

  IF TG_OP = 'INSERT' AND NEW.origen_calculo = 'DESDE_HOY'
     AND NEW.criterio IN ('POR_TIEMPO', 'AMBOS')
     AND NEW.fecha_base IS DISTINCT FROM v_hoy THEN
    RAISE EXCEPTION 'La fecha base cambio: revisar y confirmar de nuevo';
  END IF;

  IF TG_OP = 'INSERT' AND NEW.id_mantenimiento_referencia IS NOT NULL THEN
    SELECT m.id_mantenimiento, m.estado, m.id_tipo_mantenimiento, m.servicio_otro,
           m.fecha_servicio, m.km_servicio
      INTO v_ref
      FROM mycar.mantenimiento m
     WHERE m.id_mantenimiento = NEW.id_mantenimiento_referencia
       AND m.id_vehiculo = NEW.id_vehiculo;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'Referencia mantenimiento no existe en este vehiculo';
    END IF;
    IF v_ref.estado <> 'REALIZADO' OR v_ref.id_tipo_mantenimiento <> NEW.id_tipo_mantenimiento THEN
      RAISE EXCEPTION 'Mantenimiento de referencia no coincide/ no esta REALIZADO';
    END IF;
    IF v_tipo = 'OTRO'
       AND mycar.normalizar_servicio_otro(coalesce(v_ref.servicio_otro, ''))
         <> mycar.normalizar_servicio_otro(coalesce(NEW.servicio_otro, '')) THEN
      RAISE EXCEPTION 'OTRO no coincide con mantenimiento de referencia';
    END IF;
    IF NEW.origen_calculo = 'DESDE_MANTENIMIENTO' THEN
      IF NEW.criterio IN ('POR_TIEMPO', 'AMBOS')
         AND NEW.fecha_base IS DISTINCT FROM v_ref.fecha_servicio THEN
        RAISE EXCEPTION 'fecha_base debe coincidir con fecha_servicio del mantenimiento';
      END IF;
      IF NEW.criterio IN ('POR_KILOMETRAJE', 'AMBOS')
         AND NEW.km_base IS DISTINCT FROM v_ref.km_servicio THEN
        RAISE EXCEPTION 'km_base debe coincidir con km_servicio del mantenimiento';
      END IF;
    END IF;
  END IF;

  IF TG_OP = 'INSERT' THEN
    SELECT valor_km INTO v_km_actual
      FROM mycar.lectura_kilometraje
     WHERE id_vehiculo = NEW.id_vehiculo
     ORDER BY fecha_lectura_utc DESC, id_lectura_km DESC
     LIMIT 1;

    IF NEW.criterio IN ('POR_KILOMETRAJE', 'AMBOS') THEN
      IF v_km_actual IS NULL THEN
        RAISE EXCEPTION 'No hay lectura base del vehiculo';
      END IF;
      IF NEW.origen_calculo = 'DESDE_HOY' AND v_km_actual IS DISTINCT FROM NEW.km_base THEN
        RAISE EXCEPTION 'km_base debe coincidir con ultima lectura';
      END IF;
      IF NEW.km_objetivo IS NULL OR NEW.km_objetivo <= v_km_actual THEN
        RAISE EXCEPTION 'Kilometraje objetivo debe superar el vigente';
      END IF;
    END IF;

    v_estado_inicial := mycar.calcular_estado_alerta(
        NEW.criterio, NEW.fecha_objetivo, v_hoy, NEW.km_objetivo, NEW.intervalo_km,
        coalesce(v_km_actual, 0));
    IF NEW.estado IS DISTINCT FROM v_estado_inicial THEN
      RAISE EXCEPTION 'Estado inicial RF22 invalido: esperado %, recibido %',
          v_estado_inicial, NEW.estado;
    END IF;
    IF NEW.version_fila <> 0 THEN
      RAISE EXCEPTION 'Alerta inicia en version_fila=0';
    END IF;
  END IF;

  IF TG_OP = 'UPDATE' THEN
    IF ROW(NEW.id_vehiculo, NEW.id_tipo_mantenimiento, NEW.criterio, NEW.origen, NEW.categoria,
           NEW.origen_calculo, NEW.servicio_otro, NEW.unidad_tiempo, NEW.cantidad_tiempo, NEW.fecha_base,
           NEW.fecha_objetivo, NEW.km_objetivo, NEW.km_base, NEW.intervalo_km,
           NEW.id_mantenimiento_referencia, NEW.firma_equivalencia, NEW.creada_utc)
       IS DISTINCT FROM
       ROW(OLD.id_vehiculo, OLD.id_tipo_mantenimiento, OLD.criterio, OLD.origen, OLD.categoria,
           OLD.origen_calculo, OLD.servicio_otro, OLD.unidad_tiempo, OLD.cantidad_tiempo, OLD.fecha_base,
           OLD.fecha_objetivo, OLD.km_objetivo, OLD.km_base, OLD.intervalo_km,
           OLD.id_mantenimiento_referencia, OLD.firma_equivalencia, OLD.creada_utc) THEN
      RAISE EXCEPTION 'Atributos de identidad/criterio de alerta no se modifican en RF23';
    END IF;
    IF OLD.fecha_vencida_verificable IS NOT NULL
       AND NEW.fecha_vencida_verificable IS DISTINCT FROM OLD.fecha_vencida_verificable THEN
      RAISE EXCEPTION 'Primera fecha VENCIDA es inmutable';
    END IF;
    IF NEW.estado <> OLD.estado AND NEW.version_fila <> OLD.version_fila + 1 THEN
      RAISE EXCEPTION 'Cada cambio real incrementa version_fila en uno';
    END IF;
    IF NEW.estado = OLD.estado AND NEW.version_fila <> OLD.version_fila THEN
      RAISE EXCEPTION 'Sin cambio no se incrementa version_fila';
    END IF;
  END IF;
  RETURN NEW;
END $$;

