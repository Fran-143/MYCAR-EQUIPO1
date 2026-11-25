-- RF62: permitir DELETE de historial_alerta y lectura_kilometraje únicamente
-- durante la purga transaccional de eliminación definitiva (flag de sesión).
-- No altera el comportamiento append-only / inmutable del día a día.

CREATE OR REPLACE FUNCTION mycar.tr_historial_inmutable() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'DELETE'
       AND current_setting('mycar.rf62_purga', true) = '1' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Historial de alertas append-only';
END
$$;

CREATE OR REPLACE FUNCTION mycar.tr_lectura_inmutable() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF current_setting('mycar.rf62_purga', true) = '1' THEN
            RETURN OLD;
        END IF;
        RAISE EXCEPTION 'Lecturas KM de RF08/RF14 son inmutables; no DELETE';
    END IF;
    IF current_setting('mycar.rf16_permitir', true) IS DISTINCT FROM '1' THEN
        RAISE EXCEPTION 'Lecturas KM de RF08/RF14 son inmutables; no UPDATE/DELETE';
    END IF;
    IF NEW.id_lectura_km IS DISTINCT FROM OLD.id_lectura_km
        OR NEW.id_vehiculo IS DISTINCT FROM OLD.id_vehiculo
        OR NEW.origen IS DISTINCT FROM OLD.origen
        OR NEW.fecha_lectura_utc IS DISTINCT FROM OLD.fecha_lectura_utc
        OR NEW.id_operacion IS DISTINCT FROM OLD.id_operacion THEN
        RAISE EXCEPTION 'RF16 solo puede modificar valor_km de la lectura';
    END IF;
    IF NEW.valor_km IS NULL OR NEW.valor_km >= OLD.valor_km THEN
        RAISE EXCEPTION 'RF16 exige valor_km estrictamente menor al vigente';
    END IF;
    RETURN NEW;
END
$$;

COMMENT ON FUNCTION mycar.tr_historial_inmutable() IS
    'Append-only salvo purga RF62 (mycar.rf62_purga=1).';
COMMENT ON FUNCTION mycar.tr_lectura_inmutable() IS
    'Inmutable salvo RF16 (update km) o purga RF62 (DELETE con mycar.rf62_purga=1).';
