-- RF20: taller histórico textual (módulo talleres aún no en MVP).
-- RF24: mantenimiento creado al atender ≠ referencia histórica.
-- RF24-EA08: ANULADA manual no exige evidencia de vencimiento.

ALTER TABLE mycar.mantenimiento
  ADD COLUMN IF NOT EXISTS nombre_taller VARCHAR(160);

ALTER TABLE mycar.alerta
  ADD COLUMN IF NOT EXISTS id_mantenimiento_atencion BIGINT;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'fk_alerta_mantenimiento_atencion_mismo_vehiculo'
  ) THEN
    ALTER TABLE mycar.alerta
      ADD CONSTRAINT fk_alerta_mantenimiento_atencion_mismo_vehiculo
      FOREIGN KEY (id_mantenimiento_atencion, id_vehiculo)
      REFERENCES mycar.mantenimiento (id_mantenimiento, id_vehiculo)
      ON DELETE RESTRICT;
  END IF;
END $$;

ALTER TABLE mycar.alerta DROP CONSTRAINT IF EXISTS ck_alerta_vencida_evidencia;
ALTER TABLE mycar.alerta ADD CONSTRAINT ck_alerta_vencida_evidencia CHECK (
  (id_lectura_vencida_evidencia IS NULL OR fecha_vencida_verificable IS NOT NULL)
  AND (estado <> 'VENCIDA' OR fecha_vencida_verificable IS NOT NULL)
);
