-- RF02 MFA administrativo (TOTP). Compatible con datos existentes.
SET LOCAL TIME ZONE 'UTC';

ALTER TABLE mycar.usuario DROP CONSTRAINT IF EXISTS usuario_codigo_rol_check;
ALTER TABLE mycar.usuario
  ADD CONSTRAINT usuario_codigo_rol_check
  CHECK (codigo_rol IN ('USUARIO', 'ADMIN'));

ALTER TABLE mycar.sesion
  ADD COLUMN IF NOT EXISTS alcance VARCHAR(24) NOT NULL DEFAULT 'USUARIO'
    CHECK (alcance IN ('USUARIO', 'ADMIN', 'MFA_REENROLL', 'MFA_CHALLENGE')),
  ADD COLUMN IF NOT EXISTS mfa_verificado_utc TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS mycar.mfa_configuracion (
  id_usuario BIGINT PRIMARY KEY REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
  estado VARCHAR(16) NOT NULL CHECK (estado IN ('PENDIENTE', 'ACTIVA')),
  secreto_cifrado TEXT,
  crypto_key_id VARCHAR(16),
  ultimo_paso_aceptado BIGINT,
  activada_utc TIMESTAMPTZ,
  actualizada_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  CONSTRAINT ck_mfa_secreto CHECK (secreto_cifrado IS NOT NULL AND crypto_key_id IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS mycar.mfa_desafio (
  id_desafio UUID PRIMARY KEY,
  id_usuario BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
  tipo VARCHAR(16) NOT NULL CHECK (tipo IN ('LOGIN', 'ENROLL', 'REENROLL', 'STEP_UP')),
  token_hash CHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[a-f0-9]{64}$'),
  id_instalacion UUID NOT NULL,
  expira_utc TIMESTAMPTZ NOT NULL,
  intentos INTEGER NOT NULL DEFAULT 0 CHECK (intentos >= 0),
  bloqueo_hasta_utc TIMESTAMPTZ,
  consumido_utc TIMESTAMPTZ,
  creado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX IF NOT EXISTS ix_mfa_desafio_usuario ON mycar.mfa_desafio(id_usuario, creado_utc DESC);

CREATE TABLE IF NOT EXISTS mycar.mfa_codigo_recuperacion (
  id_codigo BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_usuario BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
  lote_id UUID NOT NULL,
  codigo_hash CHAR(64) NOT NULL CHECK (codigo_hash ~ '^[a-f0-9]{64}$'),
  usado_utc TIMESTAMPTZ,
  creado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mfa_recovery_hash_activo
  ON mycar.mfa_codigo_recuperacion(codigo_hash) WHERE usado_utc IS NULL;
CREATE INDEX IF NOT EXISTS ix_mfa_recovery_usuario ON mycar.mfa_codigo_recuperacion(id_usuario, lote_id);

CREATE TABLE IF NOT EXISTS mycar.mfa_auditoria (
  id_evento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_usuario BIGINT REFERENCES mycar.usuario(id_usuario) ON DELETE SET NULL,
  id_desafio UUID,
  evento VARCHAR(64) NOT NULL,
  detalle VARCHAR(240),
  ocurrido_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX IF NOT EXISTS ix_mfa_auditoria_usuario ON mycar.mfa_auditoria(id_usuario, ocurrido_utc DESC);
