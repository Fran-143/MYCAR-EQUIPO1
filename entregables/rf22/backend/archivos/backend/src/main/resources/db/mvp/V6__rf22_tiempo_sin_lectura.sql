-- RF22 CP02: POR_TIEMPO admite ausencia de lectura; KM/AMBOS conservan la exigencia.
-- V1-V5 permanecen inmutables. No cambia tablas, datos, firmas ni sincronización RF23.
CREATE OR REPLACE FUNCTION mycar.tr_validar_alerta() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_tipo VARCHAR;v_ref RECORD;v_ultima BIGINT;v_obj DATE;v_km_actual INTEGER;v_estado_inicial VARCHAR;
BEGIN
 -- Bloquear vehículo antes de validar última referencia: RF17 y RF22 toman el mismo candado.
 PERFORM 1 FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo FOR UPDATE;
 IF TG_OP='INSERT' AND NOT EXISTS (SELECT 1 FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo AND estado_vehiculo='ACTIVO') THEN
   RAISE EXCEPTION 'RF22 solo programa sobre vehículo activo'; END IF;
 SELECT codigo INTO v_tipo FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento=NEW.id_tipo_mantenimiento;
 IF TG_OP='INSERT' AND NOT EXISTS (SELECT 1 FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento=NEW.id_tipo_mantenimiento AND activo) THEN
  RAISE EXCEPTION 'Tipo de mantenimiento no activo'; END IF;
 IF v_tipo='OTRO' AND nullif(btrim(coalesce(NEW.servicio_otro,'')),'') IS NULL THEN
  RAISE EXCEPTION 'Alerta OTRO requiere servicio específico'; END IF;
 IF v_tipo<>'OTRO' AND NEW.servicio_otro IS NOT NULL THEN RAISE EXCEPTION 'Servicio específico solo para OTRO'; END IF;
 IF TG_OP='INSERT' AND NEW.criterio IN ('POR_TIEMPO','AMBOS') AND NEW.fecha_base IS DISTINCT FROM (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE THEN
  RAISE EXCEPTION 'La fecha base cambió: revisar y confirmar de nuevo'; END IF;
 IF NEW.criterio IN ('POR_TIEMPO','AMBOS') THEN
  IF NEW.unidad_tiempo='SEMANAS' THEN
   v_obj:=NEW.fecha_base + NEW.cantidad_tiempo*7;
  ELSE
   v_obj:=(NEW.fecha_base+make_interval(months=>NEW.cantidad_tiempo))::DATE;
  END IF;
  IF NEW.fecha_objetivo<>v_obj THEN RAISE EXCEPTION 'Fecha objetivo no coincide con base/intervalo'; END IF;
  IF TG_OP='INSERT' AND NEW.fecha_objetivo<=(clock_timestamp() AT TIME ZONE 'America/Lima')::DATE THEN
    RAISE EXCEPTION 'Fecha objetivo debe ser futura al crear alerta'; END IF;
 END IF;
 IF TG_OP='INSERT' AND NEW.id_mantenimiento_referencia IS NOT NULL THEN
   SELECT m.id_mantenimiento,m.estado,m.id_tipo_mantenimiento,m.servicio_otro
    INTO v_ref FROM mycar.mantenimiento m WHERE m.id_mantenimiento=NEW.id_mantenimiento_referencia AND m.id_vehiculo=NEW.id_vehiculo;
   IF NOT FOUND THEN RAISE EXCEPTION 'Referencia mantenimiento no existe en este vehículo'; END IF;
   IF v_ref.estado<>'REALIZADO' OR v_ref.id_tipo_mantenimiento<>NEW.id_tipo_mantenimiento THEN
      RAISE EXCEPTION 'Mantenimiento de referencia no coincide/ no está REALIZADO'; END IF;
   IF TG_OP='INSERT' AND NOT EXISTS (SELECT 1 FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento=NEW.id_tipo_mantenimiento AND activo) THEN
  RAISE EXCEPTION 'Tipo de mantenimiento no activo'; END IF;
 IF v_tipo='OTRO' AND mycar.normalizar_servicio_otro(coalesce(v_ref.servicio_otro,''))<>mycar.normalizar_servicio_otro(coalesce(NEW.servicio_otro,'')) THEN
      RAISE EXCEPTION 'OTRO no coincide con mantenimiento de referencia'; END IF;
   SELECT m.id_mantenimiento INTO v_ultima FROM mycar.mantenimiento m
    WHERE m.id_vehiculo=NEW.id_vehiculo AND m.id_tipo_mantenimiento=NEW.id_tipo_mantenimiento
      AND m.estado='REALIZADO' AND
      (v_tipo<>'OTRO' OR mycar.normalizar_servicio_otro(coalesce(m.servicio_otro,''))=mycar.normalizar_servicio_otro(coalesce(NEW.servicio_otro,'')))
    ORDER BY m.fecha_servicio DESC,m.id_mantenimiento DESC LIMIT 1;
   IF v_ultima IS DISTINCT FROM NEW.id_mantenimiento_referencia THEN
     RAISE EXCEPTION 'El mantenimiento de referencia ya no es el último REALIZADO'; END IF;
 END IF;
 IF TG_OP='INSERT' THEN
  SELECT valor_km INTO v_km_actual FROM mycar.lectura_kilometraje WHERE id_vehiculo=NEW.id_vehiculo
      ORDER BY fecha_lectura_utc DESC,id_lectura_km DESC LIMIT 1;
  IF NEW.criterio IN ('POR_KILOMETRAJE','AMBOS') AND v_km_actual IS NULL THEN
    RAISE EXCEPTION 'No hay lectura base del vehículo'; END IF;
  IF NEW.criterio IN ('POR_KILOMETRAJE','AMBOS') AND v_km_actual IS DISTINCT FROM NEW.km_base THEN
    RAISE EXCEPTION 'km_base debe coincidir con última lectura'; END IF;
  v_estado_inicial:=mycar.calcular_estado_alerta(NEW.criterio,NEW.fecha_objetivo,
    (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE,NEW.km_objetivo,NEW.intervalo_km,v_km_actual);
  IF NEW.estado IS DISTINCT FROM v_estado_inicial THEN
     RAISE EXCEPTION 'Estado inicial RF22 inválido: esperado %, recibido %',v_estado_inicial,NEW.estado;
  END IF;
  IF NEW.version_fila<>0 THEN RAISE EXCEPTION 'Alerta inicia en version_fila=0'; END IF;
 END IF;
 IF TG_OP='UPDATE' THEN
  IF ROW(NEW.id_vehiculo,NEW.id_tipo_mantenimiento,NEW.criterio,NEW.origen,NEW.categoria,
         NEW.servicio_otro,NEW.unidad_tiempo,NEW.cantidad_tiempo,NEW.fecha_base,
         NEW.fecha_objetivo,NEW.km_objetivo,NEW.km_base,NEW.intervalo_km,
         NEW.id_mantenimiento_referencia,NEW.firma_equivalencia,NEW.creada_utc)
    IS DISTINCT FROM
     ROW(OLD.id_vehiculo,OLD.id_tipo_mantenimiento,OLD.criterio,OLD.origen,OLD.categoria,
         OLD.servicio_otro,OLD.unidad_tiempo,OLD.cantidad_tiempo,OLD.fecha_base,
         OLD.fecha_objetivo,OLD.km_objetivo,OLD.km_base,OLD.intervalo_km,
         OLD.id_mantenimiento_referencia,OLD.firma_equivalencia,OLD.creada_utc) THEN
    RAISE EXCEPTION 'Atributos de identidad/criterio de alerta no se modifican en RF23'; END IF;
  IF OLD.fecha_vencida_verificable IS NOT NULL AND NEW.fecha_vencida_verificable IS DISTINCT FROM OLD.fecha_vencida_verificable THEN
    RAISE EXCEPTION 'Primera fecha VENCIDA es inmutable'; END IF;
  IF NEW.estado<>OLD.estado AND NEW.version_fila<>OLD.version_fila+1 THEN
    RAISE EXCEPTION 'Cada cambio real incrementa version_fila en uno'; END IF;
  IF NEW.estado=OLD.estado AND NEW.version_fila<>OLD.version_fila THEN
    RAISE EXCEPTION 'Sin cambio no se incrementa version_fila'; END IF;
 END IF;
 RETURN NEW;
END $$;
