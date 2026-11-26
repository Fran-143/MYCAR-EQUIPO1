-- RF59 / Catálogos admin: timestamps en tipos + auditoría administrativa.
-- Aditivo. No altera seeds V3 ni código OTRO.

ALTER TABLE mycar.tipo_mantenimiento
    ADD COLUMN IF NOT EXISTS creado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    ADD COLUMN IF NOT EXISTS actualizado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    ADD COLUMN IF NOT EXISTS id_actor_ultimo BIGINT REFERENCES mycar.usuario(id_usuario) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tipo_mantenimiento_nombre_norm
    ON mycar.tipo_mantenimiento (lower(btrim(nombre)));

CREATE TABLE IF NOT EXISTS mycar.catalogo_auditoria (
    id_evento         BIGSERIAL PRIMARY KEY,
    catalogo          VARCHAR(40) NOT NULL
        CHECK (catalogo IN ('TIPO_MANTENIMIENTO', 'ASEGURADORA_SOAT', 'TALLER_GLOBAL')),
    id_elemento       BIGINT NOT NULL,
    accion            VARCHAR(24) NOT NULL
        CHECK (accion IN ('CREAR', 'EDITAR', 'DESACTIVAR', 'REACTIVAR')),
    valor_anterior    VARCHAR(1000),
    valor_nuevo       VARCHAR(1000),
    id_admin          BIGINT REFERENCES mycar.usuario(id_usuario) ON DELETE SET NULL,
    resultado         VARCHAR(24) NOT NULL DEFAULT 'OK'
        CHECK (resultado IN ('OK', 'DENEGADO', 'ERROR')),
    creado_utc        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS ix_catalogo_auditoria_cat_elem
    ON mycar.catalogo_auditoria (catalogo, id_elemento, creado_utc DESC);

CREATE INDEX IF NOT EXISTS ix_catalogo_auditoria_admin
    ON mycar.catalogo_auditoria (id_admin, creado_utc DESC);

ALTER TABLE mycar.taller_global
    ADD COLUMN IF NOT EXISTS actualizado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    ADD COLUMN IF NOT EXISTS notas_publicas VARCHAR(500),
    ADD COLUMN IF NOT EXISTS id_actor_ultimo BIGINT REFERENCES mycar.usuario(id_usuario) ON DELETE SET NULL;
