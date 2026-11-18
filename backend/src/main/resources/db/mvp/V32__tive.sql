-- RF52/RF53/RF54: TIVE documental (numeración entrega TIVE). Solo PG local de prueba.
-- No ejecutar contra Neon/Render Live desde este worktree.

-- ---------------------------------------------------------------------------
-- 1) Idempotencia: tipos TIVE
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
            'RENOVAR_LICENCIA',
            'REGISTRAR_TIVE',
            'ACTUALIZAR_TIVE'
        ));

-- ---------------------------------------------------------------------------
-- 2) Tabla tive (una por vehículo)
-- ---------------------------------------------------------------------------
CREATE TABLE mycar.tive (
    id_tive              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_vehiculo          BIGINT NOT NULL REFERENCES mycar.vehiculo(id_vehiculo) ON DELETE RESTRICT,
    numero_titulo        VARCHAR(40) NOT NULL CHECK (length(btrim(numero_titulo)) > 0),
    codigo_verificacion  VARCHAR(40) NOT NULL CHECK (length(btrim(codigo_verificacion)) > 0),
    fecha_titulo         DATE NOT NULL,
    zona_registral       VARCHAR(80),
    sede_registral       VARCHAR(80),
    numero_vin           VARCHAR(17) NOT NULL
        CHECK ((numero_vin COLLATE "C") ~ '^[A-HJ-NPR-Z0-9]{17}$'),
    placa                VARCHAR(8) NOT NULL
        CHECK ((placa COLLATE "C") ~ '^(?:[A-Z][0-9][A-Z][0-9]{3}|[A-Z]{3}[0-9]{3})$'),
    numero_motor         VARCHAR(15) NOT NULL
        CHECK ((numero_motor COLLATE "C") ~ '^[A-Z0-9 -]{5,15}$' AND btrim(numero_motor) <> ''),
    numero_serie         VARCHAR(80) NOT NULL
        CHECK ((numero_serie COLLATE "C") ~ '^[A-Z0-9]{6,80}$'),
    version_fila         INTEGER NOT NULL DEFAULT 0 CHECK (version_fila >= 0),
    storage_object_key   VARCHAR(512),
    content_type         VARCHAR(120),
    tamano_bytes         INTEGER CHECK (tamano_bytes IS NULL OR tamano_bytes > 0),
    nombre_original      VARCHAR(260),
    creado_utc           TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actualizado_utc      TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_tive_vehiculo UNIQUE (id_vehiculo),
    CONSTRAINT ck_tive_documento CHECK (
        (storage_object_key IS NULL AND content_type IS NULL AND tamano_bytes IS NULL AND nombre_original IS NULL)
        OR (storage_object_key IS NOT NULL AND content_type IS NOT NULL AND tamano_bytes IS NOT NULL
            AND nombre_original IS NOT NULL AND length(btrim(storage_object_key)) > 0)
    ),
    CONSTRAINT ck_tive_zona_sede CHECK (
        (zona_registral IS NULL AND sede_registral IS NULL)
        OR (zona_registral IS NOT NULL AND length(btrim(zona_registral)) > 0
            AND sede_registral IS NOT NULL AND length(btrim(sede_registral)) > 0)
    )
);

CREATE INDEX ix_tive_vin ON mycar.tive (numero_vin);

COMMENT ON TABLE mycar.tive IS
    'Documento TIVE único por vehículo (RF52/53/54). VIN/placa inmutables vía edición; corrección VIN por soporte.';

-- ---------------------------------------------------------------------------
-- 3) Auditoría de cambios documentales TIVE
-- ---------------------------------------------------------------------------
CREATE TABLE mycar.tive_cambio (
    id_tive_cambio   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_tive          BIGINT NOT NULL REFERENCES mycar.tive(id_tive) ON DELETE RESTRICT,
    id_usuario       BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
    motivo           VARCHAR(48) NOT NULL DEFAULT 'ACTUALIZAR_DOCUMENTAL'
        CHECK (length(btrim(motivo)) > 0),
    snapshot_antes   JSONB NOT NULL,
    snapshot_despues JSONB NOT NULL,
    cambiado_utc     TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX ix_tive_cambio_tive ON mycar.tive_cambio (id_tive, cambiado_utc DESC);
