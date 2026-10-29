-- FUNCIONES DE LÓGICA DE BD: RF02 (ventana móvil) y RF23 (sincronización transaccional).
-- Ejecutar tras 01_ESQUEMA_MYCAR.sql. Spring Boot sigue siendo responsable de
-- hash de passwords, tokens, autorización de endpoints e idempotencia de POST.
BEGIN;

CREATE OR REPLACE FUNCTION mycar.registrar_fallo_login(p_id_usuario BIGINT)
RETURNS TIMESTAMPTZ LANGUAGE plpgsql AS $$
DECLARE v_u RECORD; v_now TIMESTAMPTZ; v_count INTEGER;
BEGIN
 SELECT id_usuario,estado_cuenta,login_bloqueo_hasta_utc INTO v_u
   FROM mycar.usuario WHERE id_usuario=p_id_usuario FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'No existe usuario para registrar evento de login'; END IF;
 v_now:=clock_timestamp();
 IF v_u.estado_cuenta<>'ACTIVA' THEN RETURN v_u.login_bloqueo_hasta_utc; END IF;
 IF v_u.login_bloqueo_hasta_utc IS NOT NULL AND v_now<v_u.login_bloqueo_hasta_utc THEN
  -- Si está restringido, no verificar passwords ni almacenar más intentos.
  RETURN v_u.login_bloqueo_hasta_utc;
 END IF;
 INSERT INTO mycar.intento_login_fallido(id_usuario,ocurrido_utc) VALUES(p_id_usuario,v_now);
 SELECT count(*) INTO v_count FROM mycar.intento_login_fallido
  WHERE id_usuario=p_id_usuario AND ocurrido_utc>=v_now-INTERVAL '10 minutes' AND ocurrido_utc<=v_now;
 IF v_count>=3 THEN
   UPDATE mycar.usuario SET login_bloqueo_hasta_utc=v_now+INTERVAL '10 minutes' WHERE id_usuario=p_id_usuario;
   RETURN v_now+INTERVAL '10 minutes';
 END IF;
 RETURN NULL;
END $$;

-- IMPORTANTE: antes de verificar contraseña, Spring Boot consulta
-- usuario.login_bloqueo_hasta_utc y cuenta ACTIVA; para el tercer fallo llama
-- esta función y COMMIT. Al vencer un bloqueo, la ventana móvil solo cuenta
-- intentos de los últimos diez minutos. Los login preexistentes no se revocan.
-- Errores públicos genéricos: nunca exponer existencia de correo por la respuesta.

CREATE OR REPLACE FUNCTION mycar.sincronizar_alertas_vehiculo(
  p_id_vehiculo BIGINT,p_id_usuario BIGINT)
RETURNS INTEGER LANGUAGE plpgsql AS $$
DECLARE
 v_v mycar.vehiculo%ROWTYPE; v_a mycar.alerta%ROWTYPE;
 v_hoy DATE; v_km INTEGER; v_fecha_tiempo DATE; v_fecha_km DATE;
 v_id_lectura_km BIGINT; v_fecha_evidencia DATE; v_id_evidencia BIGINT;
 v_nuevo VARCHAR; v_anterior VARCHAR; v_cambios INTEGER:=0; v_now TIMESTAMPTZ;
BEGIN
 -- RF23 es un COMANDO de sincronización, NO un GET puro. Bloqueo serial por vehículo.
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
 IF v_km IS NULL THEN RAISE EXCEPTION 'No hay lectura efectiva para el vehículo'; END IF;

 FOR v_a IN SELECT * FROM mycar.alerta
   WHERE id_vehiculo=p_id_vehiculo AND estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
   ORDER BY id_alerta FOR UPDATE
 LOOP
   v_fecha_tiempo:=NULL; v_fecha_km:=NULL; v_id_lectura_km:=NULL;
   IF v_a.criterio IN ('POR_TIEMPO','AMBOS') AND v_hoy>=v_a.fecha_objetivo+15 THEN
      v_fecha_tiempo:=v_a.fecha_objetivo+15;
   END IF;
   IF v_a.criterio IN ('POR_KILOMETRAJE','AMBOS') THEN
     SELECT l.id_lectura_km,(l.fecha_lectura_utc AT TIME ZONE 'America/Lima')::DATE
      INTO v_id_lectura_km,v_fecha_km
      FROM mycar.lectura_kilometraje l
      WHERE l.id_vehiculo=p_id_vehiculo AND l.fecha_lectura_utc>v_a.creada_utc
       AND 10::BIGINT*(l.valor_km::BIGINT-v_a.km_objetivo::BIGINT)>=v_a.intervalo_km::BIGINT
      ORDER BY l.fecha_lectura_utc ASC,l.id_lectura_km ASC LIMIT 1;
   END IF;
   -- Elegir fuente VERIFICABLE más antigua; empate favorece TIEMPO.
   v_fecha_evidencia:=NULL;v_id_evidencia:=NULL;
   IF v_fecha_tiempo IS NOT NULL AND (v_fecha_km IS NULL OR v_fecha_tiempo<=v_fecha_km) THEN
      v_fecha_evidencia:=v_fecha_tiempo;
   ELSIF v_fecha_km IS NOT NULL THEN
      v_fecha_evidencia:=v_fecha_km;v_id_evidencia:=v_id_lectura_km;
   END IF;
   IF v_a.fecha_vencida_verificable IS NOT NULL THEN
      -- No recalcular ni mover hacia adelante la primera evidencia guardada.
      v_fecha_evidencia:=v_a.fecha_vencida_verificable;
      v_id_evidencia:=v_a.id_lectura_vencida_evidencia;
   END IF;
   v_nuevo:=mycar.calcular_estado_alerta(v_a.criterio,v_a.fecha_objetivo,v_hoy,
                        v_a.km_objetivo,v_a.intervalo_km,v_km);
   -- Si existe evidencia VENCIDA, aún sin sincronizaciones intermedias, adoptar VENCIDA.
   IF v_fecha_evidencia IS NOT NULL AND mycar.prioridad_estado_alerta(v_nuevo)<4 THEN
      v_nuevo:='VENCIDA';
   END IF;
   IF v_fecha_evidencia IS NOT NULL AND v_hoy>=v_fecha_evidencia+30 THEN
      v_nuevo:='ANULADA';
   END IF;
   IF v_nuevo IS DISTINCT FROM v_a.estado THEN
      v_anterior:=v_a.estado;
      -- El UPDATE y la inserción del historial ocurren en ESTA misma transacción.
      UPDATE mycar.alerta SET estado=v_nuevo,version_fila=v_a.version_fila+1,
        fecha_estado_utc=v_now,
        fecha_vencida_verificable=coalesce(v_a.fecha_vencida_verificable,v_fecha_evidencia),
        id_lectura_vencida_evidencia=coalesce(v_a.id_lectura_vencida_evidencia,v_id_evidencia)
      WHERE id_alerta=v_a.id_alerta;
      INSERT INTO mycar.historial_alerta
       (id_alerta,estado_anterior,estado_nuevo,motivo,fecha_cambio_utc,id_lectura_evidencia,version_destino)
      VALUES(v_a.id_alerta,v_anterior,v_nuevo,
        CASE WHEN v_nuevo='ANULADA' THEN 'VENCIMIENTO_30_DIAS' ELSE 'SINCRONIZACION_RF23' END,
        v_now,v_id_evidencia,v_a.version_fila+1);
      v_cambios:=v_cambios+1;
   END IF;
 END LOOP;
 RETURN v_cambios;
END $$;

COMMIT;
