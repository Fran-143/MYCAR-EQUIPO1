-- V24: tokens push (proveedor intercambiable) + outbox transaccional.
-- No altera RF46 bandeja ni RF48 preferencias. Compatible tras V23.

CREATE TABLE mycar.dispositivo_push (
    id_dispositivo_push     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario              BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE CASCADE,
    id_instalacion          UUID NOT NULL,
    proveedor               VARCHAR(16) NOT NULL DEFAULT 'FCM'
        CHECK (proveedor IN ('FCM')),
    token                   TEXT NOT NULL,
    activo                  BOOLEAN NOT NULL DEFAULT TRUE,
    creado_utc              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actualizado_utc         TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_dispositivo_push_instalacion UNIQUE (id_instalacion)
);

-- Un token activo no puede pertenecer a dos instalaciones; inactivos reutilizables.
CREATE UNIQUE INDEX uq_dispositivo_push_token_activo
    ON mycar.dispositivo_push (proveedor, token)
    WHERE activo;

CREATE INDEX ix_dispositivo_push_usuario_activo
    ON mycar.dispositivo_push (id_usuario)
    WHERE activo;

CREATE TABLE mycar.outbox_push (
    id_outbox               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario              BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE CASCADE,
    id_alerta               BIGINT REFERENCES mycar.alerta(id_alerta) ON DELETE SET NULL,
    id_notificacion         BIGINT REFERENCES mycar.notificacion(id_notificacion) ON DELETE SET NULL,
    categoria               VARCHAR(24) NOT NULL
        CHECK (categoria IN ('MANTENIMIENTO', 'SOAT', 'KILOMETRAJE', 'LICENCIA')),
    titulo                  VARCHAR(160) NOT NULL,
    cuerpo                  VARCHAR(320) NOT NULL,
    destino                 VARCHAR(48) NOT NULL DEFAULT 'DETALLE_ALERTA',
    estado                  VARCHAR(16) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'OMITIDO', 'ERROR')),
    intentos                INT NOT NULL DEFAULT 0,
    ultimo_error            VARCHAR(240),
    creado_utc              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actualizado_utc         TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_outbox_push_idempotencia UNIQUE (id_usuario, id_alerta, categoria, destino)
);

CREATE INDEX ix_outbox_push_pendiente
    ON mycar.outbox_push (creado_utc)
    WHERE estado IN ('PENDIENTE', 'ERROR');
