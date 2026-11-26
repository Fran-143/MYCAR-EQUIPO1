-- RF55/RF56/RF07/RF51 + RF03 Elsa (límites progresivos). Solo local / siguiente versión Flyway.
-- No altera V1–V38 aplicadas. No despliegue productivo desde este archivo.

SET LOCAL TIME ZONE 'UTC';

-- 1) Estados de cuenta ampliados
ALTER TABLE mycar.usuario DROP CONSTRAINT IF EXISTS usuario_estado_cuenta_check;
ALTER TABLE mycar.usuario ADD CONSTRAINT usuario_estado_cuenta_check
    CHECK (estado_cuenta IN (
        'ACTIVA','SUSPENDIDA','BLOQUEADA','PENDIENTE_ELIMINACION','ELIMINADA'));

-- 2) Campos de bloqueo / suspensión administrativa (independientes de restricción de recuperación)
ALTER TABLE mycar.usuario
    ADD COLUMN IF NOT EXISTS motivo_estado VARCHAR(120),
    ADD COLUMN IF NOT EXISTS observacion_estado VARCHAR(500),
    ADD COLUMN IF NOT EXISTS bloqueo_admin_hasta_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS tipo_bloqueo VARCHAR(32),
    ADD COLUMN IF NOT EXISTS ultimo_acceso_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS eliminacion_programada_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS constancia_eliminacion VARCHAR(64);

COMMENT ON COLUMN mycar.usuario.tipo_bloqueo IS
    'ACCESO | ADMINISTRATIVA | NULL. RECUPERACION vive en restriccion_recuperacion (no sobrescribe estado_cuenta).';

-- 3) Solicitud de eliminación (RF07) — una vigente por usuario
CREATE TABLE IF NOT EXISTS mycar.solicitud_eliminacion (
    id_solicitud          BIGSERIAL PRIMARY KEY,
    id_usuario            BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
    estado                VARCHAR(24) NOT NULL
        CHECK (estado IN ('VIGENTE','CANCELADA','EJECUTADA')),
    motivo_codigo         VARCHAR(40),
    motivo_texto          VARCHAR(500),
    solicitada_utc        TIMESTAMPTZ NOT NULL,
    vence_utc             TIMESTAMPTZ NOT NULL,
    cancelada_utc         TIMESTAMPTZ,
    ejecutada_utc         TIMESTAMPTZ,
    id_actor_admin        BIGINT REFERENCES mycar.usuario(id_usuario) ON DELETE SET NULL,
    origen                VARCHAR(24) NOT NULL DEFAULT 'TITULAR'
        CHECK (origen IN ('TITULAR','ADMIN')),
    CONSTRAINT ck_solicitud_elim_fechas CHECK (vence_utc > solicitada_utc)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_solicitud_eliminacion_vigente
    ON mycar.solicitud_eliminacion (id_usuario) WHERE estado = 'VIGENTE';
CREATE INDEX IF NOT EXISTS ix_solicitud_eliminacion_vence
    ON mycar.solicitud_eliminacion (vence_utc) WHERE estado = 'VIGENTE';

-- 4) Historial / auditoría de estados
CREATE TABLE IF NOT EXISTS mycar.historial_estado_cuenta (
    id_evento             BIGSERIAL PRIMARY KEY,
    id_usuario_afectado   BIGINT REFERENCES mycar.usuario(id_usuario) ON DELETE SET NULL,
    constancia_ref        VARCHAR(64),
    id_actor              BIGINT REFERENCES mycar.usuario(id_usuario) ON DELETE SET NULL,
    accion                VARCHAR(48) NOT NULL,
    estado_anterior       VARCHAR(24),
    estado_posterior      VARCHAR(24),
    motivo                VARCHAR(500),
    detalle_json          TEXT,
    resultado             VARCHAR(24) NOT NULL DEFAULT 'OK'
        CHECK (resultado IN ('OK','DENEGADO','ERROR')),
    creado_utc            TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX IF NOT EXISTS ix_historial_estado_usuario
    ON mycar.historial_estado_cuenta (id_usuario_afectado, creado_utc DESC);
CREATE INDEX IF NOT EXISTS ix_historial_estado_constancia
    ON mycar.historial_estado_cuenta (constancia_ref) WHERE constancia_ref IS NOT NULL;

-- 5) Restricción progresiva RF03 (Elsa) — no cambia estado_cuenta
CREATE TABLE IF NOT EXISTS mycar.restriccion_recuperacion (
    id_usuario            BIGINT PRIMARY KEY REFERENCES mycar.usuario(id_usuario) ON DELETE CASCADE,
    finalidad             VARCHAR(24) NOT NULL DEFAULT 'RECUPERAR_CONTRASENA'
        CHECK (finalidad = 'RECUPERAR_CONTRASENA'),
    solicitudes_ciclo     INT NOT NULL DEFAULT 0 CHECK (solicitudes_ciclo >= 0),
    nivel_bloqueo         INT NOT NULL DEFAULT 0 CHECK (nivel_bloqueo >= 0),
    bloqueado_hasta_utc   TIMESTAMPTZ,
    ultima_solicitud_utc  TIMESTAMPTZ,
    alerta_enviada_hasta  TIMESTAMPTZ,
    actualizado_utc       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS mycar.evento_restriccion_recuperacion (
    id_evento             BIGSERIAL PRIMARY KEY,
    id_usuario            BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE CASCADE,
    tipo                  VARCHAR(40) NOT NULL,
    nivel                 INT,
    bloqueado_hasta_utc   TIMESTAMPTZ,
    origen_huella         VARCHAR(64),
    creado_utc            TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX IF NOT EXISTS ix_evento_restriccion_usuario
    ON mycar.evento_restriccion_recuperacion (id_usuario, creado_utc DESC);

-- 6) Alerta de bloqueo de login (anti-duplicado por evento)
CREATE TABLE IF NOT EXISTS mycar.alerta_seguridad_enviada (
    id_alerta             BIGSERIAL PRIMARY KEY,
    id_usuario            BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE CASCADE,
    tipo_evento           VARCHAR(40) NOT NULL
        CHECK (tipo_evento IN ('LOGIN_BLOQUEO','RECUPERACION_BLOQUEO')),
    ventana_clave         VARCHAR(64) NOT NULL,
    enviado_utc           TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (id_usuario, tipo_evento, ventana_clave)
);

-- 7) OTP de ciclo de cuenta (RF07/RF51) — misma forma que codigo_recuperacion, finalidad separada
ALTER TABLE mycar.codigo_recuperacion
    ADD COLUMN IF NOT EXISTS finalidad VARCHAR(24) NOT NULL DEFAULT 'RECUPERAR_CONTRASENA';

DO $fix_codigo$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'codigo_recuperacion_finalidad_check'
    ) THEN
        ALTER TABLE mycar.codigo_recuperacion
            ADD CONSTRAINT codigo_recuperacion_finalidad_check
            CHECK (finalidad IN ('RECUPERAR_CONTRASENA','ELIMINAR_CUENTA','REACTIVAR_CUENTA'));
    END IF;
END;
$fix_codigo$;

CREATE INDEX IF NOT EXISTS ix_codigo_recuperacion_usuario_finalidad_activo
    ON mycar.codigo_recuperacion (id_usuario, finalidad, estado)
    WHERE estado = 'ACTIVO';

-- 8) Ampliar finalidad Elsa V10 (compatibilidad; REGISTRO sigue vigente)
ALTER TABLE mycar.control_verificacion DROP CONSTRAINT IF EXISTS control_verificacion_finalidad_check;
ALTER TABLE mycar.control_verificacion
    ADD CONSTRAINT control_verificacion_finalidad_check
    CHECK (finalidad IN ('REGISTRO','ELIMINAR_CUENTA','REACTIVAR_CUENTA'));

ALTER TABLE mycar.operacion_verificacion DROP CONSTRAINT IF EXISTS operacion_verificacion_finalidad_check;
ALTER TABLE mycar.operacion_verificacion
    ADD CONSTRAINT operacion_verificacion_finalidad_check
    CHECK (finalidad IN ('REGISTRO','ELIMINAR_CUENTA','REACTIVAR_CUENTA'));

ALTER TABLE mycar.emision_codigo DROP CONSTRAINT IF EXISTS emision_codigo_finalidad_check;
ALTER TABLE mycar.emision_codigo
    ADD CONSTRAINT emision_codigo_finalidad_check
    CHECK (finalidad IN ('REGISTRO','ELIMINAR_CUENTA','REACTIVAR_CUENTA'));

ALTER TABLE mycar.intento_verificacion DROP CONSTRAINT IF EXISTS intento_verificacion_finalidad_check;
ALTER TABLE mycar.intento_verificacion
    ADD CONSTRAINT intento_verificacion_finalidad_check
    CHECK (finalidad IN ('REGISTRO','ELIMINAR_CUENTA','REACTIVAR_CUENTA'));

-- 9) Nota: VARCHAR(16) Elsa — ELIMINAR_CUENTA(15) y REACTIVAR_CUENTA(16) caben.
-- Si el entorno local ya amplió columnas, este ALTER es no-op seguro.
ALTER TABLE mycar.control_verificacion ALTER COLUMN finalidad TYPE VARCHAR(24);
ALTER TABLE mycar.operacion_verificacion ALTER COLUMN finalidad TYPE VARCHAR(24);
ALTER TABLE mycar.emision_codigo ALTER COLUMN finalidad TYPE VARCHAR(24);
ALTER TABLE mycar.intento_verificacion ALTER COLUMN finalidad TYPE VARCHAR(24);
