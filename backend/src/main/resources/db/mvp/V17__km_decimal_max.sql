-- RF14/RF08: kilometraje con hasta 2 decimales y máximo 9,999,999.99
ALTER TABLE mycar.lectura_kilometraje
    DROP CONSTRAINT IF EXISTS lectura_kilometraje_valor_km_check;

ALTER TABLE mycar.lectura_kilometraje
    ALTER COLUMN valor_km TYPE NUMERIC(9, 2)
    USING valor_km::numeric(9, 2);

ALTER TABLE mycar.lectura_kilometraje
    ADD CONSTRAINT lectura_kilometraje_valor_km_check
        CHECK (valor_km > 0 AND valor_km <= 9999999.99);
