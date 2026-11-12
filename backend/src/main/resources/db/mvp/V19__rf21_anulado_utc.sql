-- RF21: marca de tiempo de anulación lógica (servidor). Solo se setea al pasar REALIZADO → ANULADO.
ALTER TABLE mycar.mantenimiento
  ADD COLUMN IF NOT EXISTS anulado_utc TIMESTAMPTZ NULL;

-- RF21-EA04: impide COMMIT de anulación si, tras el chequeo de aplicación, aparece una alerta activa
-- que referencia el mantenimiento (carrera con RF22 bajo READ COMMITTED).
CREATE OR REPLACE FUNCTION mycar.tr_rf21_bloquear_anular_con_alerta_activa()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_id_alerta BIGINT;
BEGIN
  IF NEW.estado = 'ANULADO' AND OLD.estado IS DISTINCT FROM 'ANULADO' THEN
    SELECT a.id_alerta INTO v_id_alerta
      FROM mycar.alerta a
     WHERE a.id_mantenimiento_referencia = NEW.id_mantenimiento
       AND a.estado IN ('PROGRAMADA', 'PROXIMA', 'PENDIENTE', 'VENCIDA')
     ORDER BY a.id_alerta
     LIMIT 1;
    IF v_id_alerta IS NOT NULL THEN
      RAISE EXCEPTION 'RF21: anulación bloqueada por alerta activa %', v_id_alerta
        USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.anulado_utc IS NULL THEN
      NEW.anulado_utc := clock_timestamp();
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS tg_rf21_bloquear_anular_con_alerta_activa ON mycar.mantenimiento;
CREATE TRIGGER tg_rf21_bloquear_anular_con_alerta_activa
  BEFORE UPDATE OF estado ON mycar.mantenimiento
  FOR EACH ROW
  EXECUTE FUNCTION mycar.tr_rf21_bloquear_anular_con_alerta_activa();
