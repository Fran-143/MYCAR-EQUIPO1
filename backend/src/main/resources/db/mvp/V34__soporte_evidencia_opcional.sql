-- RF63: evidencia de corrección VIN opcional (solo PG local).
-- No altera V31–V33 ya aplicadas.

ALTER TABLE mycar.solicitud_correccion_vin
    ALTER COLUMN storage_object_key DROP NOT NULL,
    ALTER COLUMN content_type DROP NOT NULL,
    ALTER COLUMN tamano_bytes DROP NOT NULL,
    ALTER COLUMN nombre_original DROP NOT NULL;

ALTER TABLE mycar.solicitud_correccion_vin
    DROP CONSTRAINT IF EXISTS solicitud_correccion_vin_tamano_bytes_check;

ALTER TABLE mycar.solicitud_correccion_vin
    ADD CONSTRAINT solicitud_correccion_vin_tamano_bytes_check
        CHECK (tamano_bytes IS NULL OR tamano_bytes > 0);

COMMENT ON COLUMN mycar.solicitud_correccion_vin.storage_object_key IS
    'Evidencia opcional; NULL si el titular no adjuntó archivo.';
