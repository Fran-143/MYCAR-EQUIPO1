-- V42: bandeja de correos simulados (solo entorno local-test / desarrollo).
CREATE TABLE IF NOT EXISTS mycar.correo_simulado (
    id_correo       BIGSERIAL PRIMARY KEY,
    destinatario    VARCHAR(254) NOT NULL,
    tipo_mensaje    VARCHAR(40) NOT NULL,
    asunto          VARCHAR(200) NOT NULL,
    cuerpo          VARCHAR(2000) NOT NULL,
    codigo_otp      VARCHAR(12),
    finalidad_otp   VARCHAR(24),
    id_usuario      BIGINT,
    estado          VARCHAR(16) NOT NULL DEFAULT 'SIMULADO'
        CHECK (estado = 'SIMULADO'),
    creado_utc      TIMESTAMPTZ NOT NULL,
    expira_otp_utc  TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS ix_correo_simulado_creado
    ON mycar.correo_simulado (creado_utc DESC);

CREATE INDEX IF NOT EXISTS ix_correo_simulado_dest
    ON mycar.correo_simulado (lower(destinatario), creado_utc DESC);
