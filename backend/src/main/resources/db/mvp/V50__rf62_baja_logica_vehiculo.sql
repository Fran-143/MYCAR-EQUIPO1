-- RF62: baja lógica de vehículo (estado_registro VIGENTE/ELIMINADO).
-- No borra historiales ni libera placa/VIN. No altera estado_vehiculo (RF12).

ALTER TABLE mycar.vehiculo
    ADD COLUMN IF NOT EXISTS estado_registro VARCHAR(16) NOT NULL DEFAULT 'VIGENTE',
    ADD COLUMN IF NOT EXISTS eliminado_en TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version_registro INTEGER NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'vehiculo_estado_registro_check'
    ) THEN
        ALTER TABLE mycar.vehiculo
            ADD CONSTRAINT vehiculo_estado_registro_check
                CHECK (estado_registro IN ('VIGENTE', 'ELIMINADO'));
    END IF;
END $$;

UPDATE mycar.vehiculo
SET estado_registro = 'VIGENTE'
WHERE estado_registro IS NULL OR btrim(estado_registro) = '';

CREATE INDEX IF NOT EXISTS ix_vehiculo_usuario_registro
    ON mycar.vehiculo (id_usuario, estado_registro, estado_vehiculo);

-- Auditoría mínima de baja (misma TX que la eliminación).
CREATE TABLE IF NOT EXISTS mycar.auditoria_vehiculo_eliminacion (
    id_auditoria BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
    id_vehiculo BIGINT NOT NULL REFERENCES mycar.vehiculo(id_vehiculo) ON DELETE RESTRICT,
    estado_operativo_anterior VARCHAR(16) NOT NULL,
    era_principal BOOLEAN NOT NULL,
    estado_registro_resultante VARCHAR(16) NOT NULL DEFAULT 'ELIMINADO',
    id_operacion UUID NOT NULL,
    id_principal_resultante BIGINT,
    creado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_auditoria_vehiculo_eliminacion_op UNIQUE (id_operacion)
);

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
            'RECHAZAR_SOLICITUD_SUSCRIPCION',
            'ELIMINAR_VEHICULO'
        ));
