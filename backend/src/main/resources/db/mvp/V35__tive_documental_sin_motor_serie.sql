-- RF52/RF54: TIVE documental sin exigir motor/serie/zona/sede en el formulario.
-- Conserva filas existentes; solo relaja NOT NULL y CHECKs.

ALTER TABLE mycar.tive
    ALTER COLUMN numero_motor DROP NOT NULL,
    ALTER COLUMN numero_serie DROP NOT NULL;

ALTER TABLE mycar.tive
    DROP CONSTRAINT IF EXISTS tive_numero_motor_check;

ALTER TABLE mycar.tive
    ADD CONSTRAINT tive_numero_motor_check
        CHECK (
            numero_motor IS NULL
            OR (
                (numero_motor COLLATE "C") ~ '^[A-Z0-9 -]{5,15}$'
                AND btrim(numero_motor) <> ''
            )
        );

ALTER TABLE mycar.tive
    DROP CONSTRAINT IF EXISTS tive_numero_serie_check;

ALTER TABLE mycar.tive
    ADD CONSTRAINT tive_numero_serie_check
        CHECK (
            numero_serie IS NULL
            OR (numero_serie COLLATE "C") ~ '^[A-Z0-9]{6,80}$'
        );

COMMENT ON COLUMN mycar.tive.numero_motor IS
    'Opcional; el formulario TIVE ya no lo captura (queda en Vehículos).';
COMMENT ON COLUMN mycar.tive.numero_serie IS
    'Opcional; el formulario TIVE ya no lo captura (queda en Vehículos).';
