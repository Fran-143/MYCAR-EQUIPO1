-- RF62: eliminación definitiva operativa.
-- 1) Placa/VIN únicos solo entre vehículos VIGENTES (libera re-registro tras baja).
-- 2) Auditoría conserva placa_snapshot y deja de impedir el borrado físico del vehículo.
-- No reescribe migraciones anteriores. No borra datos de usuarios en esta migración.

ALTER TABLE mycar.auditoria_vehiculo_eliminacion
    ADD COLUMN IF NOT EXISTS placa_snapshot VARCHAR(6);

UPDATE mycar.auditoria_vehiculo_eliminacion a
SET placa_snapshot = v.placa
FROM mycar.vehiculo v
WHERE a.id_vehiculo = v.id_vehiculo
  AND a.placa_snapshot IS NULL;

ALTER TABLE mycar.auditoria_vehiculo_eliminacion
    DROP CONSTRAINT IF EXISTS auditoria_vehiculo_eliminacion_id_vehiculo_fkey;

ALTER TABLE mycar.auditoria_vehiculo_eliminacion
    ALTER COLUMN id_vehiculo DROP NOT NULL;

-- Unicidad global antigua bloqueaba reutilizar placa/VIN tras baja lógica.
ALTER TABLE mycar.vehiculo DROP CONSTRAINT IF EXISTS vehiculo_placa_key;
ALTER TABLE mycar.vehiculo DROP CONSTRAINT IF EXISTS vehiculo_numero_vin_key;
DROP INDEX IF EXISTS mycar.vehiculo_placa_key;
DROP INDEX IF EXISTS mycar.vehiculo_numero_vin_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_vehiculo_placa_vigente
    ON mycar.vehiculo (placa)
    WHERE COALESCE(estado_registro, 'VIGENTE') = 'VIGENTE';

CREATE UNIQUE INDEX IF NOT EXISTS uq_vehiculo_vin_vigente
    ON mycar.vehiculo (numero_vin)
    WHERE numero_vin IS NOT NULL
      AND COALESCE(estado_registro, 'VIGENTE') = 'VIGENTE';

COMMENT ON INDEX mycar.uq_vehiculo_placa_vigente IS
    'RF62: la placa solo es única entre vehículos vigentes; ELIMINADO no bloquea re-registro.';
COMMENT ON INDEX mycar.uq_vehiculo_vin_vigente IS
    'RF62: el VIN solo es único entre vehículos vigentes.';
