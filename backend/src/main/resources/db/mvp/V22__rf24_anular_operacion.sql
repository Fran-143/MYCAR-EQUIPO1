-- RF24-EA08: idempotencia de anulación manual vía operacion_idempotente.
-- Solo amplía el CHECK de tipo_operacion (mismo patrón que V18 / CORREGIR_KM).

ALTER TABLE mycar.operacion_idempotente
    DROP CONSTRAINT IF EXISTS operacion_idempotente_tipo_operacion_check;

ALTER TABLE mycar.operacion_idempotente
    ADD CONSTRAINT operacion_idempotente_tipo_operacion_check
        CHECK (tipo_operacion IN (
            'REGISTRAR_VEHICULO',
            'ACTUALIZAR_KM',
            'CORREGIR_KM',
            'REGISTRAR_MANTENIMIENTO',
            'PROGRAMAR_ALERTA',
            'ANULAR_ALERTA'
        ));
