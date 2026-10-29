-- PRUEBAS BÁSICAS DE ESTRUCTURA Y RESTRICCIONES.
-- Ejecutar DESPUÉS de 01/02/03 en la BD de desarrollo mycar_mvp.
-- Todos los datos creados aquí se revierten con ROLLBACK.
BEGIN;
DO $$
DECLARE
 n INTEGER;v_u BIGINT; v_plan BIGINT; v_motor BIGINT; v_fuel BIGINT;
 v_tipo BIGINT; v_veh BIGINT;v_lectura BIGINT; v_op UUID:='123e4567-e89b-42d3-a456-426614174000';
 v_alerta BIGINT; ok BOOLEAN;
BEGIN
 SELECT count(*) INTO n FROM information_schema.tables
  WHERE table_schema='mycar' AND table_type='BASE TABLE';
 IF n<>21 THEN RAISE EXCEPTION 'Esperadas 21 tablas, encontradas %',n; END IF;
 SELECT count(*) INTO n FROM mycar.tipo_mantenimiento;
 IF n<>7 THEN RAISE EXCEPTION 'Se requieren los 7 tipos mantenimiento: %',n; END IF;
 SELECT id_plan INTO v_plan FROM mycar.plan WHERE codigo='FREE';
 SELECT id_tipo_motor INTO v_motor FROM mycar.tipo_motor WHERE codigo='COMBUSTION';
 SELECT id_combustible INTO v_fuel FROM mycar.combustible WHERE codigo='GASOLINA';
 SELECT id_tipo_mantenimiento INTO v_tipo FROM mycar.tipo_mantenimiento WHERE codigo='MANTENIMIENTO_GENERAL';
 IF v_plan IS NULL OR v_motor IS NULL OR v_fuel IS NULL OR v_tipo IS NULL THEN
  RAISE EXCEPTION 'Faltan catálogos básicos del MVP'; END IF;
 IF mycar.estado_por_tiempo(DATE '2026-11-01',DATE '2026-10-02')<>'PROXIMA' THEN
   RAISE EXCEPTION 'Regla temporal de alerta incorrecta'; END IF;
 IF mycar.estado_por_km(27000,1255,27125)<>'PENDIENTE' OR
    mycar.estado_por_km(27000,1255,27126)<>'VENCIDA' THEN
   RAISE EXCEPTION 'Regla exacta 10 por ciento incorrecta'; END IF;
 IF (DATE '2026-01-31'+make_interval(months=>1))::DATE<>DATE '2026-02-28' THEN
   RAISE EXCEPTION 'Cálculo fin de mes incorrecto'; END IF;

 INSERT INTO mycar.usuario(correo,password_hash,nombre,apellido,fecha_nacimiento,
   consentimiento_privacidad_fecha_utc,consentimiento_privacidad_version)
 VALUES('prueba_mycarsql@example.test',repeat('x',60),'Prueba','MVP',DATE '2000-01-15',clock_timestamp(),'v1')
 RETURNING id_usuario INTO v_u;
 INSERT INTO mycar.suscripcion(id_usuario,id_plan,estado) VALUES(v_u,v_plan,'ACTIVA');
 INSERT INTO mycar.preferencia_notificacion(id_usuario) VALUES(v_u);
 INSERT INTO mycar.config_recordatorio_km_usuario(id_usuario) VALUES(v_u);

 -- UNIQUE parcial de suscripción activa.
 ok:=FALSE;
 BEGIN INSERT INTO mycar.suscripcion(id_usuario,id_plan,estado) VALUES(v_u,v_plan,'ACTIVA');
 EXCEPTION WHEN unique_violation THEN ok:=TRUE; END;
 IF NOT ok THEN RAISE EXCEPTION 'Falló UNIQUE de suscripción activa'; END IF;

 INSERT INTO mycar.operacion_idempotente(id_operacion,id_usuario,tipo_operacion,hash_solicitud)
 VALUES(v_op,v_u,'REGISTRAR_VEHICULO',repeat('a',64));
 INSERT INTO mycar.vehiculo(id_usuario,placa,marca,modelo,uso_vehiculo_codigo,
  anio_fabricacion,color,id_tipo_motor,id_combustible,es_principal)
 VALUES(v_u,'A1B234','Marca de prueba','Modelo de prueba','PARTICULAR',2020,
   'Azul',v_motor,v_fuel,TRUE) RETURNING id_vehiculo INTO v_veh;
 INSERT INTO mycar.lectura_kilometraje(id_vehiculo,valor_km,origen,id_operacion)
 VALUES(v_veh,20000,'INICIAL',v_op) RETURNING id_lectura_km INTO v_lectura;
 INSERT INTO mycar.config_recordatorio_km(id_vehiculo,fecha_base_utc)
 SELECT v_veh,fecha_lectura_utc FROM mycar.lectura_kilometraje WHERE id_lectura_km=v_lectura;
 UPDATE mycar.operacion_idempotente SET estado='CONFIRMADA',
  respuesta_json=jsonb_build_object('idVehiculo',v_veh,'idLecturaKm',v_lectura)
 WHERE id_operacion=v_op;

 -- RF23 no debe inventar transiciones si estado vigente ya es correcto.
 SELECT mycar.sincronizar_alertas_vehiculo(v_veh,v_u) INTO n;
 IF n<>0 THEN RAISE EXCEPTION 'RF23 escribió sin cambio real'; END IF;

 -- Probar el regex exacto de placa.
 ok:=FALSE;
 BEGIN
  INSERT INTO mycar.vehiculo(id_usuario,placa,marca,modelo,uso_vehiculo_codigo,
   anio_fabricacion,color,id_tipo_motor,id_combustible)
  VALUES(v_u,'ABC123','Marca','Modelo','PARTICULAR',2020,'Azul',v_motor,v_fuel);
 EXCEPTION WHEN check_violation THEN ok:=TRUE; END;
 IF NOT ok THEN RAISE EXCEPTION 'Regex placa defectuoso'; END IF;

 -- No existe segunda lectura inicial incluso con otra operación.
 ok:=FALSE;
 BEGIN
  INSERT INTO mycar.lectura_kilometraje(id_vehiculo,valor_km,origen,id_operacion)
  VALUES(v_veh,21000,'INICIAL',v_op);
 EXCEPTION WHEN unique_violation OR raise_exception THEN ok:=TRUE; END;
 IF NOT ok THEN RAISE EXCEPTION 'Segunda lectura INICIAL fue aceptada'; END IF;

 -- Mantenimiento LECTURA debe referenciar el valor REAL del KM.
 ok:=FALSE;
 BEGIN
  INSERT INTO mycar.mantenimiento(id_vehiculo,id_tipo_mantenimiento,km_servicio,
   fecha_servicio,origen_km_servicio,id_lectura_km_referencia)
  VALUES(v_veh,v_tipo,19000,(clock_timestamp() AT TIME ZONE 'America/Lima')::DATE,
   'LECTURA',v_lectura);
 EXCEPTION WHEN raise_exception THEN ok:=TRUE; END;
 IF NOT ok THEN RAISE EXCEPTION 'RF17 aceptó KM distinto de la lectura'; END IF;

 INSERT INTO mycar.alerta(id_vehiculo,id_tipo_mantenimiento,criterio,unidad_tiempo,
  cantidad_tiempo,fecha_base,fecha_objetivo,estado,firma_equivalencia)
 VALUES(v_veh,v_tipo,'POR_TIEMPO','SEMANAS',1,
   (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE,
   (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE+7,
   'PENDIENTE',repeat('b',64)) RETURNING id_alerta INTO v_alerta;
 INSERT INTO mycar.historial_alerta(id_alerta,estado_nuevo,motivo,version_destino)
 VALUES(v_alerta,'PENDIENTE','INICIAL_RF22',0);
 ok:=FALSE;
 BEGIN
  INSERT INTO mycar.alerta(id_vehiculo,id_tipo_mantenimiento,criterio,unidad_tiempo,
    cantidad_tiempo,fecha_base,fecha_objetivo,estado,firma_equivalencia)
  VALUES(v_veh,v_tipo,'POR_TIEMPO','SEMANAS',1,
   (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE,
   (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE+7,
    'PENDIENTE',repeat('b',64));
 EXCEPTION WHEN unique_violation THEN ok:=TRUE; END;
 IF NOT ok THEN RAISE EXCEPTION 'No se bloqueó alerta equivalente activa'; END IF;

 RAISE NOTICE 'PRUEBAS SMOKE OK (21 tablas, catálogos, KM exacto, fechas, FK/UNIQUE/CHECK). DATOS DE PRUEBA SE REVERSARÁN.';
END $$;
ROLLBACK;
