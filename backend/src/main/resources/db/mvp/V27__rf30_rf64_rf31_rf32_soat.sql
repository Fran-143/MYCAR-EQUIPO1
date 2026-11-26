-- RF30/RF64/RF31/RF32: SOAT documental + alerta categoria SOAT.
-- Solo migraciones locales / PG de prueba. No ejecutar contra Neon/Render Live desde CI de este worktree.

-- ---------------------------------------------------------------------------
-- 1) Idempotencia: tipos SOAT
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
            'RENOVAR_SOAT'
        ));

-- ---------------------------------------------------------------------------
-- 2) Tabla soat
-- ---------------------------------------------------------------------------
CREATE TABLE mycar.soat (
    id_soat              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_vehiculo          BIGINT NOT NULL REFERENCES mycar.vehiculo(id_vehiculo) ON DELETE RESTRICT,
    aseguradora          VARCHAR(120) NOT NULL CHECK (length(btrim(aseguradora)) > 0),
    numero_poliza        VARCHAR(80) NOT NULL CHECK (length(btrim(numero_poliza)) > 0),
    fecha_inicio         DATE NOT NULL,
    fecha_vencimiento    DATE NOT NULL,
    fecha_emision        DATE,
    nombre_contratante   VARCHAR(160),
    importe_prima        NUMERIC(12, 2) CHECK (importe_prima IS NULL OR importe_prima >= 0),
    vigencia             VARCHAR(16) NOT NULL CHECK (vigencia IN ('ACTUAL', 'HISTORICO')),
    recordatorio_activo  BOOLEAN NOT NULL DEFAULT FALSE,
    version_fila         INTEGER NOT NULL DEFAULT 0 CHECK (version_fila >= 0),
    storage_object_key   VARCHAR(512),
    content_type         VARCHAR(120),
    tamano_bytes         INTEGER CHECK (tamano_bytes IS NULL OR tamano_bytes > 0),
    nombre_original      VARCHAR(260),
    creado_utc           TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actualizado_utc      TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_soat_fechas CHECK (fecha_vencimiento > fecha_inicio),
    CONSTRAINT ck_soat_emision CHECK (fecha_emision IS NULL OR fecha_emision <= fecha_vencimiento),
    CONSTRAINT ck_soat_documento CHECK (
        (storage_object_key IS NULL AND content_type IS NULL AND tamano_bytes IS NULL AND nombre_original IS NULL)
        OR (storage_object_key IS NOT NULL AND content_type IS NOT NULL AND tamano_bytes IS NOT NULL
            AND nombre_original IS NOT NULL AND length(btrim(storage_object_key)) > 0)
    ),
    CONSTRAINT uq_soat_id_vehiculo UNIQUE (id_soat, id_vehiculo)
);

CREATE UNIQUE INDEX uq_soat_actual_por_vehiculo
    ON mycar.soat (id_vehiculo)
    WHERE vigencia = 'ACTUAL';

CREATE INDEX ix_soat_vehiculo_vigencia
    ON mycar.soat (id_vehiculo, vigencia, fecha_vencimiento DESC);

CREATE INDEX ix_soat_vehiculo_historial
    ON mycar.soat (id_vehiculo, fecha_inicio DESC, id_soat DESC);

COMMENT ON TABLE mycar.soat IS
    'Vigencia documental SOAT declarada por el titular. ACTUAL≠cobertura VIGENTE.';

-- ---------------------------------------------------------------------------
-- 3) Trazabilidad Corregir (RF31)
-- ---------------------------------------------------------------------------
CREATE TABLE mycar.soat_cambio (
    id_soat_cambio   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_soat          BIGINT NOT NULL REFERENCES mycar.soat(id_soat) ON DELETE RESTRICT,
    id_usuario       BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
    motivo           VARCHAR(48) NOT NULL DEFAULT 'CORREGIR_DATOS'
        CHECK (length(btrim(motivo)) > 0),
    snapshot_antes   JSONB NOT NULL,
    snapshot_despues JSONB NOT NULL,
    cambiado_utc     TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX ix_soat_cambio_soat ON mycar.soat_cambio (id_soat, cambiado_utc DESC);

-- ---------------------------------------------------------------------------
-- 4) Ampliar alerta para categoria SOAT
-- ---------------------------------------------------------------------------
ALTER TABLE mycar.alerta
    ALTER COLUMN id_tipo_mantenimiento DROP NOT NULL;

ALTER TABLE mycar.alerta
    ADD COLUMN IF NOT EXISTS id_soat BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_alerta_soat_mismo_vehiculo'
    ) THEN
        ALTER TABLE mycar.alerta
            ADD CONSTRAINT fk_alerta_soat_mismo_vehiculo
                FOREIGN KEY (id_soat, id_vehiculo)
                REFERENCES mycar.soat (id_soat, id_vehiculo)
                ON DELETE RESTRICT;
    END IF;
END $$;

ALTER TABLE mycar.alerta DROP CONSTRAINT IF EXISTS alerta_categoria_check;
ALTER TABLE mycar.alerta DROP CONSTRAINT IF EXISTS ck_alerta_categoria;

ALTER TABLE mycar.alerta
    ADD CONSTRAINT ck_alerta_categoria
        CHECK (categoria IN ('MANTENIMIENTO', 'SOAT'));

ALTER TABLE mycar.alerta DROP CONSTRAINT IF EXISTS ck_alerta_tiempo;
ALTER TABLE mycar.alerta DROP CONSTRAINT IF EXISTS ck_alerta_km;
ALTER TABLE mycar.alerta DROP CONSTRAINT IF EXISTS ck_alerta_categoria_coherencia;

ALTER TABLE mycar.alerta
    ADD CONSTRAINT ck_alerta_categoria_coherencia CHECK (
        (categoria = 'MANTENIMIENTO'
            AND id_tipo_mantenimiento IS NOT NULL
            AND id_soat IS NULL)
        OR (categoria = 'SOAT'
            AND id_tipo_mantenimiento IS NULL
            AND id_soat IS NOT NULL
            AND criterio = 'POR_TIEMPO'
            AND unidad_tiempo = 'DIAS'
            AND cantidad_tiempo = 30
            AND fecha_base IS NOT NULL
            AND fecha_objetivo IS NOT NULL
            AND km_base IS NULL AND intervalo_km IS NULL AND km_objetivo IS NULL
            AND id_mantenimiento_referencia IS NULL
            AND id_mantenimiento_atencion IS NULL)
    );

ALTER TABLE mycar.alerta
    ADD CONSTRAINT ck_alerta_tiempo CHECK (
        (categoria = 'SOAT')
        OR (criterio = 'POR_KILOMETRAJE'
            AND unidad_tiempo IS NULL AND cantidad_tiempo IS NULL
            AND fecha_base IS NULL AND fecha_objetivo IS NULL)
        OR (criterio IN ('POR_TIEMPO', 'AMBOS')
            AND unidad_tiempo IN ('SEMANAS', 'MESES')
            AND unidad_tiempo IS NOT NULL AND cantidad_tiempo IS NOT NULL
            AND cantidad_tiempo > 0
            AND fecha_base IS NOT NULL AND fecha_objetivo IS NOT NULL)
    );

ALTER TABLE mycar.alerta
    ADD CONSTRAINT ck_alerta_km CHECK (
        (categoria = 'SOAT')
        OR (criterio = 'POR_TIEMPO'
            AND km_base IS NULL AND intervalo_km IS NULL AND km_objetivo IS NULL)
        OR (criterio IN ('POR_KILOMETRAJE', 'AMBOS')
            AND km_base IS NOT NULL AND intervalo_km IS NOT NULL AND km_objetivo IS NOT NULL
            AND km_base > 0 AND intervalo_km > 0
            AND km_objetivo = km_base + intervalo_km AND km_objetivo > km_base)
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_alerta_soat_activa
    ON mycar.alerta (id_soat)
    WHERE categoria = 'SOAT'
      AND estado IN ('PROGRAMADA', 'PROXIMA', 'PENDIENTE', 'VENCIDA');

CREATE INDEX IF NOT EXISTS ix_alerta_soat
    ON mycar.alerta (id_soat)
    WHERE id_soat IS NOT NULL;

COMMENT ON COLUMN mycar.alerta.id_soat IS
    'FK SOAT cuando categoria=SOAT; NULL en MANTENIMIENTO.';

-- ---------------------------------------------------------------------------
-- 5) Trigger alerta: rama SOAT (DIAS / fecha_base desde vencimiento)
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
  IF NEW.categoria = 'SOAT' THEN
    PERFORM 1 FROM mycar.vehiculo WHERE id_vehiculo = NEW.id_vehiculo FOR UPDATE;
    v_hoy := (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE;
    IF NEW.id_soat IS NULL THEN
      RAISE EXCEPTION 'Alerta SOAT requiere id_soat';
    END IF;
    IF NEW.criterio <> 'POR_TIEMPO' OR NEW.unidad_tiempo <> 'DIAS' OR NEW.cantidad_tiempo <> 30 THEN
      RAISE EXCEPTION 'Alerta SOAT: criterio DIAS/30 inválido';
    END IF;
    v_obj := NEW.fecha_base + NEW.cantidad_tiempo;
    IF NEW.fecha_objetivo <> v_obj THEN
      RAISE EXCEPTION 'Fecha objetivo SOAT no coincide con base+30 días';
    END IF;
    IF TG_OP = 'INSERT' THEN
      v_estado_inicial := mycar.calcular_estado_alerta(
          'POR_TIEMPO', NEW.fecha_objetivo, v_hoy, NULL, NULL, 0);
      IF NEW.estado IS DISTINCT FROM v_estado_inicial THEN
        RAISE EXCEPTION 'Estado inicial SOAT inválido: esperado %, recibido %',
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
    RAISE EXCEPTION 'RF22 solo programa sobre vehículo activo';
  END IF;

  SELECT codigo INTO v_tipo FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento = NEW.id_tipo_mantenimiento;
  IF TG_OP = 'INSERT' AND NOT EXISTS (
      SELECT 1 FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento = NEW.id_tipo_mantenimiento AND activo
  ) THEN
    RAISE EXCEPTION 'Tipo de mantenimiento no activo';
  END IF;
  IF v_tipo = 'OTRO' AND nullif(btrim(coalesce(NEW.servicio_otro, '')), '') IS NULL THEN
    RAISE EXCEPTION 'Alerta OTRO requiere servicio específico';
  END IF;
  IF v_tipo <> 'OTRO' AND NEW.servicio_otro IS NOT NULL THEN
    RAISE EXCEPTION 'Servicio específico solo para OTRO';
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
    RAISE EXCEPTION 'La fecha base cambió: revisar y confirmar de nuevo';
  END IF;

  IF TG_OP = 'INSERT' AND NEW.id_mantenimiento_referencia IS NOT NULL THEN
    SELECT m.id_mantenimiento, m.estado, m.id_tipo_mantenimiento, m.servicio_otro,
           m.fecha_servicio, m.km_servicio
      INTO v_ref
      FROM mycar.mantenimiento m
     WHERE m.id_mantenimiento = NEW.id_mantenimiento_referencia
       AND m.id_vehiculo = NEW.id_vehiculo;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'Referencia mantenimiento no existe en este vehículo';
    END IF;
    IF v_ref.estado <> 'REALIZADO' OR v_ref.id_tipo_mantenimiento <> NEW.id_tipo_mantenimiento THEN
      RAISE EXCEPTION 'Mantenimiento de referencia no coincide/ no está REALIZADO';
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
        RAISE EXCEPTION 'No hay lectura base del vehículo';
      END IF;
      IF NEW.origen_calculo = 'DESDE_HOY' AND v_km_actual IS DISTINCT FROM NEW.km_base THEN
        RAISE EXCEPTION 'km_base debe coincidir con última lectura';
      END IF;
      IF NEW.km_objetivo IS NULL OR NEW.km_objetivo <= v_km_actual THEN
        RAISE EXCEPTION 'Kilometraje objetivo debe superar el vigente';
      END IF;
    END IF;

    v_estado_inicial := mycar.calcular_estado_alerta(
        NEW.criterio, NEW.fecha_objetivo, v_hoy, NEW.km_objetivo, NEW.intervalo_km,
        coalesce(v_km_actual, 0));
    IF NEW.estado IS DISTINCT FROM v_estado_inicial THEN
      RAISE EXCEPTION 'Estado inicial RF22 inválido: esperado %, recibido %',
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
