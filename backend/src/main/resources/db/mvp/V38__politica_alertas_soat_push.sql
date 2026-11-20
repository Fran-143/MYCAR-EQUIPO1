-- V38: SOAT VENCIDA desde el día siguiente; push agrupado; vínculos RF43 en outbox.
-- No altera estado_por_tiempo de mantenimiento/licencia.
-- Solo entornos locales / nuevas instalaciones. Neon productivo requiere autorización.

-- 1) Estado por tiempo exclusivo SOAT (VENCIDA = objetivo + 1 día).
CREATE OR REPLACE FUNCTION mycar.estado_por_tiempo_soat(p_objetivo DATE, p_hoy DATE)
RETURNS VARCHAR LANGUAGE SQL IMMUTABLE AS $$
 SELECT CASE WHEN p_hoy >= p_objetivo + 1 THEN 'VENCIDA'
             WHEN p_hoy >= p_objetivo - 7 THEN 'PENDIENTE'
             WHEN p_hoy >= p_objetivo - 30 THEN 'PROXIMA'
             ELSE 'PROGRAMADA' END::VARCHAR;
$$;

COMMENT ON FUNCTION mycar.estado_por_tiempo_soat(DATE, DATE) IS
  'RF SOAT: PROGRAMADA >30d; PROXIMA 30–8; PENDIENTE 7–0; VENCIDA desde día siguiente al vencimiento.';

-- 2) Sync vehículo: SOAT usa estado_por_tiempo_soat; mantenimiento conserva V11.
CREATE OR REPLACE FUNCTION mycar.sincronizar_alertas_vehiculo(
  p_id_vehiculo BIGINT, p_id_usuario BIGINT)
RETURNS INTEGER LANGUAGE plpgsql AS $$
DECLARE
 v_v mycar.vehiculo%ROWTYPE; v_a mycar.alerta%ROWTYPE;
 v_hoy DATE; v_km INTEGER; v_fecha_tiempo DATE; v_fecha_km DATE;
 v_id_lectura_km BIGINT; v_fecha_evidencia DATE; v_id_evidencia BIGINT;
 v_nuevo VARCHAR; v_anterior VARCHAR; v_cambios INTEGER:=0; v_now TIMESTAMPTZ;
 v_es_soat BOOLEAN;
BEGIN
 SELECT * INTO v_v FROM mycar.vehiculo
  WHERE id_vehiculo=p_id_vehiculo AND id_usuario=p_id_usuario AND estado_vehiculo='ACTIVO'
  FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Vehículo activo no autorizado para sincronización'; END IF;
 IF NOT EXISTS(SELECT 1 FROM mycar.usuario WHERE id_usuario=p_id_usuario AND estado_cuenta='ACTIVA') THEN
  RAISE EXCEPTION 'Cuenta sin autorización'; END IF;
 v_now:=clock_timestamp();
 v_hoy:=(v_now AT TIME ZONE 'America/Lima')::DATE;
 SELECT valor_km INTO v_km FROM mycar.lectura_kilometraje
   WHERE id_vehiculo=p_id_vehiculo ORDER BY fecha_lectura_utc DESC,id_lectura_km DESC LIMIT 1;
 IF v_km IS NULL AND EXISTS (
      SELECT 1 FROM mycar.alerta
       WHERE id_vehiculo=p_id_vehiculo
         AND estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
         AND criterio IN ('POR_KILOMETRAJE','AMBOS')
         AND categoria = 'MANTENIMIENTO'
    ) THEN
   RAISE EXCEPTION 'No hay lectura efectiva para el vehículo';
 END IF;

 FOR v_a IN SELECT * FROM mycar.alerta
   WHERE id_vehiculo=p_id_vehiculo AND estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
   ORDER BY id_alerta FOR UPDATE
 LOOP
   v_es_soat := (v_a.categoria = 'SOAT');
   v_fecha_tiempo:=NULL; v_fecha_km:=NULL; v_id_lectura_km:=NULL;

   IF v_es_soat THEN
     IF v_hoy >= v_a.fecha_objetivo + 1 THEN
        v_fecha_tiempo := v_a.fecha_objetivo + 1;
     END IF;
   ELSIF v_a.criterio IN ('POR_TIEMPO','AMBOS') AND v_hoy>=v_a.fecha_objetivo+15 THEN
      v_fecha_tiempo:=v_a.fecha_objetivo+15;
   END IF;

   IF NOT v_es_soat AND v_a.criterio IN ('POR_KILOMETRAJE','AMBOS') THEN
     SELECT l.id_lectura_km,(l.fecha_lectura_utc AT TIME ZONE 'America/Lima')::DATE
      INTO v_id_lectura_km,v_fecha_km
      FROM mycar.lectura_kilometraje l
      WHERE l.id_vehiculo=p_id_vehiculo AND l.fecha_lectura_utc>v_a.creada_utc
       AND 10::BIGINT*(l.valor_km::BIGINT-v_a.km_objetivo::BIGINT)>=v_a.intervalo_km::BIGINT
      ORDER BY l.fecha_lectura_utc ASC,l.id_lectura_km ASC LIMIT 1;
   END IF;

   v_fecha_evidencia:=NULL;v_id_evidencia:=NULL;
   IF v_fecha_tiempo IS NOT NULL AND (v_fecha_km IS NULL OR v_fecha_tiempo<=v_fecha_km) THEN
      v_fecha_evidencia:=v_fecha_tiempo;
   ELSIF v_fecha_km IS NOT NULL THEN
      v_fecha_evidencia:=v_fecha_km;v_id_evidencia:=v_id_lectura_km;
   END IF;
   IF v_a.fecha_vencida_verificable IS NOT NULL THEN
      v_fecha_evidencia:=v_a.fecha_vencida_verificable;
      v_id_evidencia:=v_a.id_lectura_vencida_evidencia;
   END IF;

   IF v_es_soat THEN
     v_nuevo := mycar.estado_por_tiempo_soat(v_a.fecha_objetivo, v_hoy);
   ELSE
     v_nuevo:=mycar.calcular_estado_alerta(v_a.criterio,v_a.fecha_objetivo,v_hoy,
                        v_a.km_objetivo,v_a.intervalo_km,coalesce(v_km,0));
   END IF;

   IF v_fecha_evidencia IS NOT NULL AND mycar.prioridad_estado_alerta(v_nuevo)<4 THEN
      v_nuevo:='VENCIDA';
   END IF;

   IF v_nuevo IS DISTINCT FROM v_a.estado THEN
      v_anterior:=v_a.estado;
      UPDATE mycar.alerta SET estado=v_nuevo,version_fila=v_a.version_fila+1,
        fecha_estado_utc=v_now,
        fecha_vencida_verificable=coalesce(v_a.fecha_vencida_verificable,v_fecha_evidencia),
        id_lectura_vencida_evidencia=coalesce(v_a.id_lectura_vencida_evidencia,v_id_evidencia)
      WHERE id_alerta=v_a.id_alerta;
      INSERT INTO mycar.historial_alerta
       (id_alerta,estado_anterior,estado_nuevo,motivo,fecha_cambio_utc,id_lectura_evidencia,version_destino)
      VALUES(v_a.id_alerta,v_anterior,v_nuevo,'SINCRONIZACION_RF23',
        v_now,v_id_evidencia,v_a.version_fila+1);
      v_cambios:=v_cambios+1;
   END IF;
 END LOOP;
 RETURN v_cambios;
END $$;

-- 3) Motivos de control push: resumen por categoría + hitos SOAT.
ALTER TABLE mycar.alerta_push_control
    DROP CONSTRAINT IF EXISTS alerta_push_control_motivo_ultimo_check;

ALTER TABLE mycar.alerta_push_control
    ADD CONSTRAINT alerta_push_control_motivo_ultimo_check
        CHECK (motivo_ultimo IS NULL OR motivo_ultimo IN (
            'ENTRADA_ESTADO', 'RECORDATORIO', 'INMEDIATO_KM',
            'RESUMEN_VENCIDAS', 'RESUMEN_CATEGORIA', 'HITO_SOAT'));

-- 4) Asociación resumen/outbox → notificaciones (RF43 y avisos sin alerta).
CREATE TABLE IF NOT EXISTS mycar.outbox_push_notificacion (
    id_outbox         BIGINT NOT NULL
        REFERENCES mycar.outbox_push(id_outbox) ON DELETE CASCADE,
    id_notificacion   BIGINT NOT NULL
        REFERENCES mycar.notificacion(id_notificacion) ON DELETE CASCADE,
    PRIMARY KEY (id_outbox, id_notificacion)
);

CREATE INDEX IF NOT EXISTS ix_outbox_push_notificacion_notif
    ON mycar.outbox_push_notificacion (id_notificacion);

COMMENT ON TABLE mycar.outbox_push_notificacion IS
    'Notificaciones incluidas en un outbox (resumen por categoría o individual RF43).';

COMMENT ON FUNCTION mycar.sincronizar_alertas_vehiculo(BIGINT, BIGINT) IS
    'RF23 sync: MANTENIMIENTO conserva +15d VENCIDA; SOAT VENCIDA desde objetivo+1.';
