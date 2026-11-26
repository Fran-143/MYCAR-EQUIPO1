-- RF43: ampliar frecuencias de recordatorio KM + idempotencia de notificaciones sin alerta.
-- Solo entorno local / nuevas instalaciones. No aplicar manualmente en Neon productivo sin autorización.

ALTER TABLE mycar.config_recordatorio_km
    DROP CONSTRAINT IF EXISTS config_recordatorio_km_frecuencia_check;

ALTER TABLE mycar.config_recordatorio_km
    ADD CONSTRAINT config_recordatorio_km_frecuencia_check
        CHECK (frecuencia IN ('DIARIO', 'INTERDIARIO', 'SEMANAL', 'QUINCENAL', 'MENSUAL'));

ALTER TABLE mycar.notificacion
    ADD COLUMN IF NOT EXISTS clave_idempotencia VARCHAR(96);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notificacion_clave_idempotencia
    ON mycar.notificacion (clave_idempotencia)
    WHERE clave_idempotencia IS NOT NULL;

COMMENT ON COLUMN mycar.notificacion.clave_idempotencia IS
    'RF43: KM:{idVehiculo}:{yyyy-MM-dd}:{frecuencia}. NULL en avisos ligados a alerta.';
