-- RF37–39 / RF41–42–44: metadatos de foto personalizada (bytes en Storage privado).
-- No mezclar con evidencias SOAT/TIVE/licencia.

ALTER TABLE mycar.usuario
    ADD COLUMN IF NOT EXISTS foto_storage_object_key TEXT,
    ADD COLUMN IF NOT EXISTS foto_content_type TEXT,
    ADD COLUMN IF NOT EXISTS foto_tamano_bytes BIGINT,
    ADD COLUMN IF NOT EXISTS foto_actualizada_en TIMESTAMPTZ;

ALTER TABLE mycar.vehiculo
    ADD COLUMN IF NOT EXISTS foto_storage_object_key TEXT,
    ADD COLUMN IF NOT EXISTS foto_content_type TEXT,
    ADD COLUMN IF NOT EXISTS foto_tamano_bytes BIGINT,
    ADD COLUMN IF NOT EXISTS foto_actualizada_en TIMESTAMPTZ;

COMMENT ON COLUMN mycar.usuario.foto_storage_object_key IS 'Clave opaca en bucket privado de fotos de perfil; sin URL pública.';
COMMENT ON COLUMN mycar.vehiculo.foto_storage_object_key IS 'Clave opaca de imagen principal del vehículo; sin URL pública.';
