-- RF23: sincronizacion de alertas LICENCIA (sin vehiculo).
-- Misma maquina de estados POR_TIEMPO que sincronizar_alertas_vehiculo (V11).

CREATE OR REPLACE FUNCTION mycar.sincronizar_alertas_licencia_usuario(p_id_usuario BIGINT)
RETURNS INTEGER LANGUAGE plpgsql AS $$
DECLARE
 v_a mycar.alerta%ROWTYPE;
 v_hoy DATE;
 v_nuevo VARCHAR;
 v_anterior VARCHAR;
 v_cambios INTEGER:=0;
 v_now TIMESTAMPTZ;
BEGIN
 IF NOT EXISTS(
      SELECT 1 FROM mycar.usuario
       WHERE id_usuario=p_id_usuario AND estado_cuenta='ACTIVA') THEN
  RAISE EXCEPTION 'Cuenta sin autorizacion';
 END IF;
 v_now:=clock_timestamp();
 v_hoy:=(v_now AT TIME ZONE 'America/Lima')::DATE;

 FOR v_a IN
   SELECT a.* FROM mycar.alerta a
   WHERE a.id_usuario=p_id_usuario
     AND a.categoria='LICENCIA'
     AND a.id_vehiculo IS NULL
     AND a.estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
   ORDER BY a.id_alerta
   FOR UPDATE OF a
 LOOP
   v_nuevo:=mycar.calcular_estado_alerta(
              v_a.criterio, v_a.fecha_objetivo, v_hoy,
              v_a.km_objetivo, v_a.intervalo_km, 0);
   IF v_a.fecha_vencida_verificable IS NOT NULL
      AND mycar.prioridad_estado_alerta(v_nuevo)<4 THEN
      v_nuevo:='VENCIDA';
   ELSIF v_a.criterio='POR_TIEMPO'
      AND v_hoy>=v_a.fecha_objetivo+15
      AND mycar.prioridad_estado_alerta(v_nuevo)<4 THEN
      v_nuevo:='VENCIDA';
   END IF;
   IF v_nuevo IS DISTINCT FROM v_a.estado THEN
      v_anterior:=v_a.estado;
      UPDATE mycar.alerta SET
        estado=v_nuevo,
        version_fila=v_a.version_fila+1,
        fecha_estado_utc=v_now,
        fecha_vencida_verificable=COALESCE(
          v_a.fecha_vencida_verificable,
          CASE WHEN v_nuevo='VENCIDA' THEN v_hoy ELSE NULL END)
      WHERE id_alerta=v_a.id_alerta;
      INSERT INTO mycar.historial_alerta
        (id_alerta,estado_anterior,estado_nuevo,motivo,fecha_cambio_utc,version_destino)
      VALUES(v_a.id_alerta,v_anterior,v_nuevo,'SINCRONIZACION_RF23',
             v_now,v_a.version_fila+1);
      v_cambios:=v_cambios+1;
   END IF;
 END LOOP;
 RETURN v_cambios;
END $$;
