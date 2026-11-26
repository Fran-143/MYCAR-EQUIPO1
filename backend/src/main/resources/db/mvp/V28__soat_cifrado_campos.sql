-- Fase 1: cifrado poliza/contratante + indice ciego y metadatos documento cifrado
ALTER TABLE mycar.soat
    ALTER COLUMN numero_poliza TYPE TEXT,
    ALTER COLUMN nombre_contratante TYPE TEXT;

ALTER TABLE mycar.soat
    ADD COLUMN IF NOT EXISTS numero_poliza_blind VARCHAR(64),
    ADD COLUMN IF NOT EXISTS crypto_key_id VARCHAR(16),
    ADD COLUMN IF NOT EXISTS documento_cifrado BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_soat_numero_poliza_blind ON mycar.soat (numero_poliza_blind);