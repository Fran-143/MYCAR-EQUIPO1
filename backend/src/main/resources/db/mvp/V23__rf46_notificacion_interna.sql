-- RF46/RF47: bandeja de notificaciones internas (sin push/outbox RF48).
-- Una fila por alerta cuando id_alerta no es NULL (upsert idempotente).

CREATE TABLE mycar.notificacion (
    id_notificacion         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario              BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
    categoria               VARCHAR(24) NOT NULL
        CHECK (categoria IN ('MANTENIMIENTO', 'SOAT', 'KILOMETRAJE', 'LICENCIA')),
    id_alerta               BIGINT REFERENCES mycar.alerta(id_alerta) ON DELETE RESTRICT,
    id_vehiculo             BIGINT REFERENCES mycar.vehiculo(id_vehiculo) ON DELETE RESTRICT,
    estado_gestion          VARCHAR(16) NOT NULL
        CHECK (estado_gestion IN ('PENDIENTE', 'ATENDIDA', 'CERRADA')),
    leida                   BOOLEAN NOT NULL DEFAULT FALSE,
    titulo                  VARCHAR(160) NOT NULL,
    cuerpo                  VARCHAR(320) NOT NULL,
    destino                 VARCHAR(48) NOT NULL DEFAULT 'DETALLE_ALERTA',
    fecha_emision_utc       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    fecha_actualizacion_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_notificacion_licencia_sin_vehiculo
        CHECK (categoria <> 'LICENCIA' OR id_vehiculo IS NULL),
    CONSTRAINT ck_notificacion_alerta_vehiculo
        CHECK (id_alerta IS NULL OR id_vehiculo IS NOT NULL)
);

CREATE UNIQUE INDEX uq_notificacion_usuario_alerta
    ON mycar.notificacion (id_usuario, id_alerta)
    WHERE id_alerta IS NOT NULL;

CREATE INDEX ix_notificacion_bandeja
    ON mycar.notificacion (id_usuario, estado_gestion, categoria, fecha_emision_utc DESC);

CREATE INDEX ix_notificacion_pendientes
    ON mycar.notificacion (id_usuario, fecha_emision_utc DESC)
    WHERE estado_gestion = 'PENDIENTE';
