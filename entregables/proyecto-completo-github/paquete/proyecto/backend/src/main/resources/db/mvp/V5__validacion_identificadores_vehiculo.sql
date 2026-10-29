-- Reglas de identificación del vehículo.
-- Los tres campos permanecen opcionales.

ALTER TABLE mycar.vehiculo

    ADD CONSTRAINT ck_vehiculo_vin_formato
        CHECK (
            numero_vin IS NULL
                OR (numero_vin::text COLLATE "C")
    ~ '^[A-HJ-NPR-Z0-9]{17}$'
    ),

    ADD CONSTRAINT ck_vehiculo_numero_motor_formato
    CHECK (
    numero_motor IS NULL
    OR (
    (numero_motor COLLATE "C")
    ~ '^[A-Z0-9 -]{5,15}$'
    AND btrim(numero_motor) <> ''
    )
    ),

    ADD CONSTRAINT ck_vehiculo_numero_serie_formato
    CHECK (
    numero_serie IS NULL
    OR (numero_serie COLLATE "C")
    ~ '^[A-Z0-9]{6,80}$'
    );