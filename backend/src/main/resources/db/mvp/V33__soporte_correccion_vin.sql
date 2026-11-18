-- RF63/RF61/RF58: Soporte corrección VIN (numeración entrega). Solo PG local.
-- No ejecutar contra Neon/Render Live desde este worktree.

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
            'ACTUALIZAR_TIVE',
            'SOLICITAR_CORRECCION_VIN'
        ));

CREATE TABLE mycar.solicitud_soporte (
    id_solicitud     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo           VARCHAR(24) NOT NULL,
    tipo             VARCHAR(32) NOT NULL CHECK (tipo IN ('CORRECCION_VIN')),
    estado           VARCHAR(16) NOT NULL CHECK (estado IN (
        'PENDIENTE', 'EN_REVISION', 'APROBADA', 'RECHAZADA'
    )),
    id_usuario       BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
    id_vehiculo      BIGINT NOT NULL REFERENCES mycar.vehiculo(id_vehiculo) ON DELETE RESTRICT,
    version_fila     INTEGER NOT NULL DEFAULT 0 CHECK (version_fila >= 0),
    motivo_rechazo   VARCHAR(500),
    creado_utc       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actualizado_utc  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_solicitud_soporte_codigo UNIQUE (codigo)
);

CREATE UNIQUE INDEX uq_solicitud_soporte_abierta_vehiculo_tipo
    ON mycar.solicitud_soporte (id_vehiculo, tipo)
    WHERE estado IN ('PENDIENTE', 'EN_REVISION');

CREATE INDEX ix_solicitud_soporte_usuario
    ON mycar.solicitud_soporte (id_usuario, creado_utc DESC);

CREATE INDEX ix_solicitud_soporte_estado
    ON mycar.solicitud_soporte (estado, actualizado_utc DESC);

CREATE TABLE mycar.solicitud_correccion_vin (
    id_solicitud          BIGINT PRIMARY KEY
        REFERENCES mycar.solicitud_soporte(id_solicitud) ON DELETE RESTRICT,
    vin_registrado        VARCHAR(17) NOT NULL
        CHECK ((vin_registrado COLLATE "C") ~ '^[A-HJ-NPR-Z0-9]{17}$'),
    vin_propuesto         VARCHAR(17) NOT NULL
        CHECK ((vin_propuesto COLLATE "C") ~ '^[A-HJ-NPR-Z0-9]{17}$'),
    motivo                VARCHAR(500) NOT NULL CHECK (length(btrim(motivo)) > 0),
    storage_object_key    VARCHAR(512) NOT NULL,
    content_type          VARCHAR(120) NOT NULL,
    tamano_bytes          INTEGER NOT NULL CHECK (tamano_bytes > 0),
    nombre_original       VARCHAR(260) NOT NULL,
    CONSTRAINT ck_correccion_vin_distinto CHECK (vin_registrado <> vin_propuesto)
);

CREATE TABLE mycar.solicitud_soporte_evento (
    id_evento        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_solicitud     BIGINT NOT NULL REFERENCES mycar.solicitud_soporte(id_solicitud) ON DELETE RESTRICT,
    estado_anterior  VARCHAR(16),
    estado_nuevo     VARCHAR(16) NOT NULL,
    id_actor         BIGINT REFERENCES mycar.usuario(id_usuario) ON DELETE SET NULL,
    observacion      VARCHAR(500),
    ocurrido_utc     TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX ix_solicitud_soporte_evento
    ON mycar.solicitud_soporte_evento (id_solicitud, ocurrido_utc ASC);

COMMENT ON TABLE mycar.solicitud_soporte IS
    'Historial general de soporte (RF61). CORRECCION_VIN se resuelve en RF58.';
