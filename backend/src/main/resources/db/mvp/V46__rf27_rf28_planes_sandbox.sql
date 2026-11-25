-- RF27/RF28/RF29/RF57: planes sandbox local (catálogo, operación pago prueba, eventos).
-- No inventa precios comerciales; columnas de plan comerciales quedan NULL hasta configuración.

ALTER TABLE mycar.plan
    ADD COLUMN IF NOT EXISTS precio_demo NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS moneda VARCHAR(8),
    ADD COLUMN IF NOT EXISTS periodicidad VARCHAR(16),
    ADD COLUMN IF NOT EXISTS descripcion_beneficios TEXT,
    ADD COLUMN IF NOT EXISTS contratacion_sandbox_habilitada BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'plan_periodicidad_check'
    ) THEN
        ALTER TABLE mycar.plan
            ADD CONSTRAINT plan_periodicidad_check
                CHECK (periodicidad IS NULL OR periodicidad IN ('MENSUAL', 'ANUAL', 'DIARIA', 'OTRA'));
    END IF;
END $$;

INSERT INTO mycar.plan (codigo, nombre, max_vehiculos_registrados, activo)
SELECT 'PREMIUM', 'Premium', 5, TRUE
WHERE NOT EXISTS (SELECT 1 FROM mycar.plan WHERE codigo = 'PREMIUM');

ALTER TABLE mycar.suscripcion
    ADD COLUMN IF NOT EXISTS origen VARCHAR(32) NOT NULL DEFAULT 'REGISTRO',
    ADD COLUMN IF NOT EXISTS id_operacion_pago BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'suscripcion_origen_check'
    ) THEN
        ALTER TABLE mycar.suscripcion
            ADD CONSTRAINT suscripcion_origen_check
                CHECK (origen IN ('REGISTRO', 'SANDBOX_LOCAL', 'MERCADO_PAGO_TEST'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS mycar.operacion_pago_prueba (
    id_operacion_pago BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
    id_plan BIGINT NOT NULL REFERENCES mycar.plan(id_plan) ON DELETE RESTRICT,
    referencia_unica VARCHAR(64) NOT NULL,
    proveedor VARCHAR(32) NOT NULL,
    ambiente VARCHAR(8) NOT NULL DEFAULT 'TEST',
    importe NUMERIC(12, 2),
    moneda VARCHAR(8),
    estado VARCHAR(16) NOT NULL DEFAULT 'CREADA',
    id_externo VARCHAR(128),
    init_point TEXT,
    sandbox_init_point TEXT,
    creado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    verificado_utc TIMESTAMPTZ,
    evidencia_verificacion TEXT,
    version_fila INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT operacion_pago_prueba_proveedor_check
        CHECK (proveedor IN ('SIMULADOR_LOCAL', 'MERCADO_PAGO_TEST')),
    CONSTRAINT operacion_pago_prueba_ambiente_check
        CHECK (ambiente = 'TEST'),
    CONSTRAINT operacion_pago_prueba_estado_check
        CHECK (estado IN ('CREADA', 'PENDIENTE', 'APROBADA', 'RECHAZADA', 'CANCELADA', 'ERROR')),
    CONSTRAINT uq_operacion_pago_prueba_referencia UNIQUE (referencia_unica)
);

CREATE INDEX IF NOT EXISTS ix_operacion_pago_prueba_usuario
    ON mycar.operacion_pago_prueba(id_usuario, creado_utc DESC);
CREATE INDEX IF NOT EXISTS ix_operacion_pago_prueba_estado
    ON mycar.operacion_pago_prueba(estado, creado_utc DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'suscripcion_id_operacion_pago_fkey'
    ) THEN
        ALTER TABLE mycar.suscripcion
            ADD CONSTRAINT suscripcion_id_operacion_pago_fkey
                FOREIGN KEY (id_operacion_pago)
                REFERENCES mycar.operacion_pago_prueba(id_operacion_pago)
                ON DELETE SET NULL;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS mycar.suscripcion_evento (
    id_evento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
    id_suscripcion BIGINT REFERENCES mycar.suscripcion(id_suscripcion) ON DELETE SET NULL,
    id_operacion_pago BIGINT REFERENCES mycar.operacion_pago_prueba(id_operacion_pago) ON DELETE SET NULL,
    tipo_evento VARCHAR(48) NOT NULL,
    detalle TEXT,
    creado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS ix_suscripcion_evento_usuario
    ON mycar.suscripcion_evento(id_usuario, creado_utc DESC);

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
            'CONFIRMAR_OPERACION_PAGO_PRUEBA'
        ));
