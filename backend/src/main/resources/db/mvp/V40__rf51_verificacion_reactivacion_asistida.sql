-- V40: verificación de titularidad para reactivación asistida (RF51 admin) + finalidad OTP.
-- Solo local (Flyway db/mvp). No edita V39.

ALTER TABLE mycar.codigo_recuperacion DROP CONSTRAINT IF EXISTS codigo_recuperacion_finalidad_check;
ALTER TABLE mycar.codigo_recuperacion
    ADD CONSTRAINT codigo_recuperacion_finalidad_check
    CHECK (finalidad IN (
        'RECUPERAR_CONTRASENA',
        'ELIMINAR_CUENTA',
        'REACTIVAR_CUENTA',
        'VERIFICAR_REACTIVACION_ADMIN'
    ));

CREATE TABLE IF NOT EXISTS mycar.verificacion_reactivacion_asistida (
    id_verificacion       BIGSERIAL PRIMARY KEY,
    id_usuario            BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
    id_admin              BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
    id_solicitud          BIGINT NOT NULL REFERENCES mycar.solicitud_eliminacion(id_solicitud) ON DELETE RESTRICT,
    estado                VARCHAR(24) NOT NULL
        CHECK (estado IN ('PENDIENTE','CONFIRMADA','CONSUMIDA','EXPIRADA','CANCELADA')),
    expira_utc            TIMESTAMPTZ NOT NULL,
    confirmada_utc        TIMESTAMPTZ,
    consumida_utc         TIMESTAMPTZ,
    creado_utc            TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_verif_reactivacion_pendiente
    ON mycar.verificacion_reactivacion_asistida (id_usuario)
    WHERE (estado = 'PENDIENTE');

CREATE INDEX IF NOT EXISTS ix_verif_reactivacion_usuario_estado
    ON mycar.verificacion_reactivacion_asistida (id_usuario, estado);
