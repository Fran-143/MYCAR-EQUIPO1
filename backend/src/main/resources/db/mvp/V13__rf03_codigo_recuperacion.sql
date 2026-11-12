-- RF03: códigos temporales de recuperación de contraseña (aislados de verificación RF01).
CREATE TABLE IF NOT EXISTS mycar.codigo_recuperacion (
    id_codigo           BIGSERIAL PRIMARY KEY,
    id_usuario          BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE CASCADE,
    codigo_hash         CHAR(64) NOT NULL,
    estado              VARCHAR(16) NOT NULL
        CHECK (estado IN ('ACTIVO', 'CONSUMIDO', 'INVALIDADO')),
    expira_utc          TIMESTAMPTZ NOT NULL,
    creado_utc          TIMESTAMPTZ NOT NULL,
    consumido_utc       TIMESTAMPTZ,
    intentos_fallidos   INT NOT NULL DEFAULT 0 CHECK (intentos_fallidos >= 0)
);

CREATE INDEX IF NOT EXISTS ix_codigo_recuperacion_usuario_activo
    ON mycar.codigo_recuperacion (id_usuario, estado)
    WHERE estado = 'ACTIVO';
