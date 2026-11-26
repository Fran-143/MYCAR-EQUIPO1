-- RF27/28/29/RF57 ampliación: solicitudes Premium + modalidad + aprobación administrativa.
-- No inventa precios/duraciones comerciales. No toca Neon/Render.

ALTER TABLE mycar.suscripcion
    ADD COLUMN IF NOT EXISTS modalidad VARCHAR(32);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'suscripcion_modalidad_check'
    ) THEN
        ALTER TABLE mycar.suscripcion
            ADD CONSTRAINT suscripcion_modalidad_check
                CHECK (modalidad IS NULL OR modalidad IN (
                    'FREE',
                    'PRUEBA_GRATUITA',
                    'PAGO_SANDBOX',
                    'PREMIUM_PAGO_REAL'
                ));
    END IF;
END $$;

-- FREE histórico: modalidad FREE; sandbox previo → PAGO_SANDBOX
UPDATE mycar.suscripcion s
SET modalidad = CASE
    WHEN p.codigo = 'FREE' THEN 'FREE'
    WHEN s.origen IN ('SANDBOX_LOCAL', 'MERCADO_PAGO_TEST') THEN 'PAGO_SANDBOX'
    ELSE COALESCE(s.modalidad, 'FREE')
END
FROM mycar.plan p
WHERE p.id_plan = s.id_plan
  AND s.modalidad IS NULL;

CREATE TABLE IF NOT EXISTS mycar.solicitud_suscripcion (
    id_solicitud BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo VARCHAR(32) NOT NULL,
    id_usuario BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
    id_plan BIGINT NOT NULL REFERENCES mycar.plan(id_plan) ON DELETE RESTRICT,
    modalidad VARCHAR(32) NOT NULL,
    estado VARCHAR(32) NOT NULL DEFAULT 'PENDIENTE_APROBACION',
    id_operacion_pago BIGINT REFERENCES mycar.operacion_pago_prueba(id_operacion_pago) ON DELETE SET NULL,
    creado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actualizado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    decidido_utc TIMESTAMPTZ,
    id_admin_decision BIGINT REFERENCES mycar.usuario(id_usuario) ON DELETE SET NULL,
    motivo_rechazo TEXT,
    observacion_admin TEXT,
    id_suscripcion_resultado BIGINT REFERENCES mycar.suscripcion(id_suscripcion) ON DELETE SET NULL,
    version_fila INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT solicitud_suscripcion_modalidad_check
        CHECK (modalidad IN ('PRUEBA_GRATUITA', 'PAGO_SANDBOX')),
    CONSTRAINT solicitud_suscripcion_estado_check
        CHECK (estado IN (
            'PENDIENTE_PAGO',
            'PENDIENTE_APROBACION',
            'APROBADA',
            'RECHAZADA',
            'CANCELADA'
        )),
    CONSTRAINT uq_solicitud_suscripcion_codigo UNIQUE (codigo)
);

CREATE INDEX IF NOT EXISTS ix_solicitud_suscripcion_usuario
    ON mycar.solicitud_suscripcion(id_usuario, creado_utc DESC);
CREATE INDEX IF NOT EXISTS ix_solicitud_suscripcion_estado
    ON mycar.solicitud_suscripcion(estado, creado_utc DESC);
CREATE INDEX IF NOT EXISTS ix_solicitud_suscripcion_modalidad
    ON mycar.solicitud_suscripcion(modalidad, estado);

-- Como máximo una solicitud abierta (pago o aprobación) por usuario.
CREATE UNIQUE INDEX IF NOT EXISTS uq_solicitud_suscripcion_abierta_usuario
    ON mycar.solicitud_suscripcion(id_usuario)
    WHERE estado IN ('PENDIENTE_PAGO', 'PENDIENTE_APROBACION');

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
            'CREAR_INCIDENCIA_GENERAL',
            'CREAR_OPERACION_PAGO_PRUEBA',
            'CONFIRMAR_OPERACION_PAGO_PRUEBA',
            'CREAR_SOLICITUD_SUSCRIPCION',
            'APROBAR_SOLICITUD_SUSCRIPCION',
            'RECHAZAR_SOLICITUD_SUSCRIPCION'
        ));
