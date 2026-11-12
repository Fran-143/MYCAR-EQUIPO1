-- RF16: rectificar última lectura (intervalo estricto) + auditoría + permitir UPDATE controlado.

ALTER TABLE mycar.operacion_idempotente
    DROP CONSTRAINT IF EXISTS operacion_idempotente_tipo_operacion_check;

ALTER TABLE mycar.operacion_idempotente
    ADD CONSTRAINT operacion_idempotente_tipo_operacion_check
        CHECK (tipo_operacion IN (
            'REGISTRAR_VEHICULO',
            'ACTUALIZAR_KM',
            'CORREGIR_KM',
            'REGISTRAR_MANTENIMIENTO',
            'PROGRAMAR_ALERTA'
        ));

CREATE TABLE mycar.correccion_lectura_kilometraje (
    id_correccion BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_lectura_km BIGINT NOT NULL,
    id_vehiculo BIGINT NOT NULL,
    id_usuario BIGINT NOT NULL REFERENCES mycar.usuario (id_usuario) ON DELETE RESTRICT,
    km_anterior NUMERIC(9, 2) NOT NULL CHECK (km_anterior > 0),
    km_nuevo NUMERIC(9, 2) NOT NULL CHECK (km_nuevo > 0),
    km_penultimo NUMERIC(9, 2) NOT NULL CHECK (km_penultimo > 0),
    motivo VARCHAR(200) NOT NULL,
    id_operacion UUID NOT NULL REFERENCES mycar.operacion_idempotente (id_operacion) ON DELETE RESTRICT,
    fecha_rectificacion_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_correccion_operacion UNIQUE (id_operacion),
    CONSTRAINT ck_correccion_intervalo_estricto
        CHECK (km_penultimo < km_nuevo AND km_nuevo < km_anterior),
    CONSTRAINT fk_correccion_lectura
        FOREIGN KEY (id_lectura_km, id_vehiculo)
            REFERENCES mycar.lectura_kilometraje (id_lectura_km, id_vehiculo)
            ON DELETE RESTRICT
);

CREATE INDEX ix_correccion_lectura_fecha
    ON mycar.correccion_lectura_kilometraje (id_lectura_km, fecha_rectificacion_utc DESC);

-- UPDATE de valor_km solo con flag de sesión de transacción (RF16). Conserva fecha original.
CREATE OR REPLACE FUNCTION mycar.tr_lectura_inmutable() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
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
