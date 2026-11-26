-- RF08: placa peruana en dos formatos (A1B234 o ABC123).
-- No editar V2; se reemplaza el CHECK inline de columna placa.

ALTER TABLE mycar.vehiculo
    DROP CONSTRAINT IF EXISTS vehiculo_placa_check;

ALTER TABLE mycar.vehiculo
    ADD CONSTRAINT ck_vehiculo_placa_formato
        CHECK (
            (placa COLLATE "C")
                ~ '^(?:[A-Z][0-9][A-Z][0-9]{3}|[A-Z]{3}[0-9]{3})$'
        );
