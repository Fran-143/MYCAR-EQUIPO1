-- RF60/RF61: Incidencias generales sobre solicitud_soporte (solo PG local).
-- No edita V33–V44; no tocar Neon/Render desde este worktree.

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
            'SOLICITAR_CORRECCION_VIN',
            'CREAR_INCIDENCIA_GENERAL'
        ));

ALTER TABLE mycar.solicitud_soporte
    DROP CONSTRAINT IF EXISTS solicitud_soporte_tipo_check;

ALTER TABLE mycar.solicitud_soporte
    ADD CONSTRAINT solicitud_soporte_tipo_check
        CHECK (tipo IN ('CORRECCION_VIN', 'INCIDENCIA_GENERAL'));

ALTER TABLE mycar.solicitud_soporte
    DROP CONSTRAINT IF EXISTS solicitud_soporte_estado_check;

ALTER TABLE mycar.solicitud_soporte
    ADD CONSTRAINT solicitud_soporte_estado_check
        CHECK (estado IN (
            'PENDIENTE', 'EN_REVISION', 'APROBADA', 'RECHAZADA',
            'ACEPTADA', 'EN_PROCESO', 'RESUELTA'
        ));

ALTER TABLE mycar.solicitud_soporte
    ALTER COLUMN id_vehiculo DROP NOT NULL;

DROP INDEX IF EXISTS mycar.uq_solicitud_soporte_abierta_vehiculo_tipo;

CREATE UNIQUE INDEX uq_solicitud_soporte_abierta_vehiculo_tipo
    ON mycar.solicitud_soporte (id_vehiculo, tipo)
    WHERE tipo = 'CORRECCION_VIN'
      AND id_vehiculo IS NOT NULL
      AND estado IN ('PENDIENTE', 'EN_REVISION');

CREATE TABLE mycar.solicitud_incidencia_general (
    id_solicitud          BIGINT PRIMARY KEY
        REFERENCES mycar.solicitud_soporte(id_solicitud) ON DELETE RESTRICT,
    categoria             VARCHAR(40) NOT NULL CHECK (categoria IN (
        'FALLAS_APP',
        'CUENTA_ACCESO',
        'VEHICULOS_DOCUMENTOS',
        'ALERTAS_NOTIFICACIONES',
        'SERVICIOS_EXTERNOS',
        'OTROS'
    )),
    tema_otro             VARCHAR(120),
    asunto                VARCHAR(120) NOT NULL
        CHECK (char_length(btrim(asunto)) BETWEEN 5 AND 120),
    descripcion           VARCHAR(3000) NOT NULL
        CHECK (char_length(btrim(descripcion)) BETWEEN 15 AND 3000),
    storage_object_key    VARCHAR(512),
    content_type          VARCHAR(120),
    tamano_bytes          INTEGER CHECK (tamano_bytes IS NULL OR tamano_bytes > 0),
    nombre_original       VARCHAR(260),
    respuesta_admin       VARCHAR(3000),
    cerrado_utc           TIMESTAMPTZ,
    CONSTRAINT ck_incidencia_tema_otro CHECK (
        (categoria <> 'OTROS' AND tema_otro IS NULL)
        OR (categoria = 'OTROS' AND tema_otro IS NOT NULL AND char_length(btrim(tema_otro)) BETWEEN 3 AND 120)
    )
);

CREATE INDEX ix_solicitud_incidencia_general_categoria
    ON mycar.solicitud_incidencia_general (categoria);

COMMENT ON TABLE mycar.solicitud_incidencia_general IS
    'Detalle RF60 incidencias generales; historial unificado RF61 vía solicitud_soporte.';
