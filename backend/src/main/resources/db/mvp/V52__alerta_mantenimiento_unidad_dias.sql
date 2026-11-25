-- RF17-EA02 / RF22: el contrato admite unidadTiempo=DIAS, pero tr_validar_alerta
-- solo contemplaba SEMANAS vs MESES (ELSE = meses) en alertas MANTENIMIENTO.
-- Con DIAS el INSERT fallaba («Fecha objetivo no coincide…») y la alerta
-- nunca aparecía en Mis alertas.

CREATE OR REPLACE FUNCTION mycar.tr_validar_alerta() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  v_tipo VARCHAR;
  v_ref RECORD;
  v_obj DATE;
  v_km_actual INTEGER;
  v_estado_inicial VARCHAR;
  v_hoy DATE;
BEGIN
  IF NEW.categoria = 'LICENCIA' THEN
    PERFORM 1 FROM mycar.usuario WHERE id_usuario = NEW.id_usuario FOR UPDATE;
    v_hoy := (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE;
    IF NEW.id_licencia IS NULL THEN
      RAISE EXCEPTION 'Alerta LICENCIA requiere id_licencia';
    END IF;
    IF NEW.id_vehiculo IS NOT NULL THEN
      RAISE EXCEPTION 'Alerta LICENCIA exige id_vehiculo NULL';
    END IF;
    IF NEW.id_usuario IS NULL THEN
      RAISE EXCEPTION 'Alerta LICENCIA requiere id_usuario';
    END IF;
    IF NEW.criterio <> 'POR_TIEMPO' OR NEW.unidad_tiempo <> 'DIAS' OR NEW.cantidad_tiempo <> 30 THEN
      RAISE EXCEPTION 'Alerta LICENCIA: criterio DIAS/30 invalido';
    END IF;
    v_obj := NEW.fecha_base + NEW.cantidad_tiempo;
    IF NEW.fecha_objetivo <> v_obj THEN
      RAISE EXCEPTION 'Fecha objetivo LICENCIA no coincide con base+30 dias';
    END IF;
    IF TG_OP = 'INSERT' THEN
      v_estado_inicial := mycar.calcular_estado_alerta(
          'POR_TIEMPO', NEW.fecha_objetivo, v_hoy, NULL, NULL, 0);
      IF NEW.estado IS DISTINCT FROM v_estado_inicial THEN
        RAISE EXCEPTION 'Estado inicial LICENCIA invalido: esperado %, recibido %',
            v_estado_inicial, NEW.estado;
      END IF;
      IF NEW.version_fila <> 0 THEN
        RAISE EXCEPTION 'Alerta inicia en version_fila=0';
      END IF;
    END IF;
    RETURN NEW;
  END IF;
  IF NEW.categoria = 'SOAT' THEN
    PERFORM 1 FROM mycar.vehiculo WHERE id_vehiculo = NEW.id_vehiculo FOR UPDATE;
    v_hoy := (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE;
    IF NEW.id_soat IS NULL THEN
      RAISE EXCEPTION 'Alerta SOAT requiere id_soat';
    END IF;
    IF NEW.criterio <> 'POR_TIEMPO' OR NEW.unidad_tiempo <> 'DIAS' OR NEW.cantidad_tiempo <> 30 THEN
      RAISE EXCEPTION 'Alerta SOAT: criterio DIAS/30 invalido';
    END IF;
    v_obj := NEW.fecha_base + NEW.cantidad_tiempo;
    IF NEW.fecha_objetivo <> v_obj THEN
      RAISE EXCEPTION 'Fecha objetivo SOAT no coincide con base+30 dias';
    END IF;
    IF TG_OP = 'INSERT' THEN
      v_estado_inicial := mycar.calcular_estado_alerta(
          'POR_TIEMPO', NEW.fecha_objetivo, v_hoy, NULL, NULL, 0);
      IF NEW.estado IS DISTINCT FROM v_estado_inicial THEN
        RAISE EXCEPTION 'Estado inicial SOAT invalido: esperado %, recibido %',
            v_estado_inicial, NEW.estado;
      END IF;
      IF NEW.version_fila <> 0 THEN
        RAISE EXCEPTION 'Alerta inicia en version_fila=0';
      END IF;
    END IF;
    RETURN NEW;
  END IF;

  PERFORM 1 FROM mycar.vehiculo WHERE id_vehiculo = NEW.id_vehiculo FOR UPDATE;
  v_hoy := (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE;

  IF TG_OP = 'INSERT' AND NOT EXISTS (
      SELECT 1 FROM mycar.vehiculo WHERE id_vehiculo = NEW.id_vehiculo AND estado_vehiculo = 'ACTIVO'
  ) THEN
    RAISE EXCEPTION 'RF22 solo programa sobre vehiculo activo';
  END IF;

  SELECT codigo INTO v_tipo FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento = NEW.id_tipo_mantenimiento;
  IF TG_OP = 'INSERT' AND NOT EXISTS (
      SELECT 1 FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento = NEW.id_tipo_mantenimiento AND activo
  ) THEN
    RAISE EXCEPTION 'Tipo de mantenimiento no activo';
  END IF;
  IF v_tipo = 'OTRO' AND nullif(btrim(coalesce(NEW.servicio_otro, '')), '') IS NULL THEN
    RAISE EXCEPTION 'Alerta OTRO requiere servicio especifico';
  END IF;
  IF v_tipo <> 'OTRO' AND NEW.servicio_otro IS NOT NULL THEN
    RAISE EXCEPTION 'Servicio especifico solo para OTRO';
  END IF;

  IF TG_OP = 'INSERT' THEN
    IF NEW.origen_calculo = 'DESDE_HOY' AND NEW.id_mantenimiento_referencia IS NOT NULL THEN
      RAISE EXCEPTION 'DESDE_HOY no admite mantenimiento de referencia';
    END IF;
    IF NEW.origen_calculo = 'DESDE_MANTENIMIENTO' AND NEW.id_mantenimiento_referencia IS NULL THEN
      RAISE EXCEPTION 'DESDE_MANTENIMIENTO exige mantenimiento de referencia';
    END IF;
  END IF;

  IF NEW.criterio IN ('POR_TIEMPO', 'AMBOS') THEN
    IF NEW.unidad_tiempo = 'SEMANAS' THEN
      v_obj := NEW.fecha_base + NEW.cantidad_tiempo * 7;
    ELSIF NEW.unidad_tiempo = 'DIAS' THEN
      v_obj := (NEW.fecha_base + make_interval(days => NEW.cantidad_tiempo))::DATE;
    ELSE
      v_obj := (NEW.fecha_base + make_interval(months => NEW.cantidad_tiempo))::DATE;
    END IF;
    IF NEW.fecha_objetivo <> v_obj THEN
      RAISE EXCEPTION 'Fecha objetivo no coincide con base/intervalo';
    END IF;
    IF TG_OP = 'INSERT' AND NEW.fecha_objetivo <= v_hoy THEN
      RAISE EXCEPTION 'Fecha objetivo debe ser futura al crear alerta';
    END IF;
  END IF;

  IF TG_OP = 'INSERT' AND NEW.origen_calculo = 'DESDE_HOY'
     AND NEW.criterio IN ('POR_TIEMPO', 'AMBOS')
     AND NEW.fecha_base IS DISTINCT FROM v_hoy THEN
    RAISE EXCEPTION 'La fecha base cambio: revisar y confirmar de nuevo';
  END IF;

  IF TG_OP = 'INSERT' AND NEW.id_mantenimiento_referencia IS NOT NULL THEN
    SELECT m.id_mantenimiento, m.estado, m.id_tipo_mantenimiento, m.servicio_otro,
           m.fecha_servicio, m.km_servicio
      INTO v_ref
      FROM mycar.mantenimiento m
     WHERE m.id_mantenimiento = NEW.id_mantenimiento_referencia
       AND m.id_vehiculo = NEW.id_vehiculo;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'Referencia mantenimiento no existe en este vehiculo';
    END IF;
    IF v_ref.estado <> 'REALIZADO' OR v_ref.id_tipo_mantenimiento <> NEW.id_tipo_mantenimiento THEN
      RAISE EXCEPTION 'Mantenimiento de referencia no coincide/ no esta REALIZADO';
    END IF;
    IF v_tipo = 'OTRO'
       AND mycar.normalizar_servicio_otro(coalesce(v_ref.servicio_otro, ''))
         <> mycar.normalizar_servicio_otro(coalesce(NEW.servicio_otro, '')) THEN
      RAISE EXCEPTION 'OTRO no coincide con mantenimiento de referencia';
    END IF;
    IF NEW.origen_calculo = 'DESDE_MANTENIMIENTO' THEN
      IF NEW.criterio IN ('POR_TIEMPO', 'AMBOS')
         AND NEW.fecha_base IS DISTINCT FROM v_ref.fecha_servicio THEN
        RAISE EXCEPTION 'fecha_base debe coincidir con fecha_servicio del mantenimiento';
      END IF;
      IF NEW.criterio IN ('POR_KILOMETRAJE', 'AMBOS')
         AND NEW.km_base IS DISTINCT FROM v_ref.km_servicio THEN
        RAISE EXCEPTION 'km_base debe coincidir con km_servicio del mantenimiento';
      END IF;
    END IF;
  END IF;

  IF TG_OP = 'INSERT' THEN
    SELECT valor_km INTO v_km_actual
      FROM mycar.lectura_kilometraje
     WHERE id_vehiculo = NEW.id_vehiculo
     ORDER BY fecha_lectura_utc DESC, id_lectura_km DESC
     LIMIT 1;

    IF NEW.criterio IN ('POR_KILOMETRAJE', 'AMBOS') THEN
      IF v_km_actual IS NULL THEN
        RAISE EXCEPTION 'No hay lectura base del vehiculo';
      END IF;
      IF NEW.origen_calculo = 'DESDE_HOY' AND v_km_actual IS DISTINCT FROM NEW.km_base THEN
        RAISE EXCEPTION 'km_base debe coincidir con ultima lectura';
      END IF;
      IF NEW.km_objetivo IS NULL OR NEW.km_objetivo <= v_km_actual THEN
        RAISE EXCEPTION 'Kilometraje objetivo debe superar el vigente';
      END IF;
    END IF;

    v_estado_inicial := mycar.calcular_estado_alerta(
        NEW.criterio, NEW.fecha_objetivo, v_hoy, NEW.km_objetivo, NEW.intervalo_km,
        coalesce(v_km_actual, 0));
    IF NEW.estado IS DISTINCT FROM v_estado_inicial THEN
      RAISE EXCEPTION 'Estado inicial RF22 invalido: esperado %, recibido %',
          v_estado_inicial, NEW.estado;
    END IF;
    IF NEW.version_fila <> 0 THEN
      RAISE EXCEPTION 'Alerta inicia en version_fila=0';
    END IF;
  END IF;

  IF TG_OP = 'UPDATE' THEN
    IF ROW(NEW.id_vehiculo, NEW.id_tipo_mantenimiento, NEW.criterio, NEW.origen, NEW.categoria,
           NEW.origen_calculo, NEW.servicio_otro, NEW.unidad_tiempo, NEW.cantidad_tiempo, NEW.fecha_base,
           NEW.fecha_objetivo, NEW.km_objetivo, NEW.km_base, NEW.intervalo_km,
           NEW.id_mantenimiento_referencia, NEW.firma_equivalencia, NEW.creada_utc)
       IS DISTINCT FROM
       ROW(OLD.id_vehiculo, OLD.id_tipo_mantenimiento, OLD.criterio, OLD.origen, OLD.categoria,
           OLD.origen_calculo, OLD.servicio_otro, OLD.unidad_tiempo, OLD.cantidad_tiempo, OLD.fecha_base,
           OLD.fecha_objetivo, OLD.km_objetivo, OLD.km_base, OLD.intervalo_km,
           OLD.id_mantenimiento_referencia, OLD.firma_equivalencia, OLD.creada_utc) THEN
      RAISE EXCEPTION 'Atributos de identidad/criterio de alerta no se modifican en RF23';
    END IF;
    IF OLD.fecha_vencida_verificable IS NOT NULL
       AND NEW.fecha_vencida_verificable IS DISTINCT FROM OLD.fecha_vencida_verificable THEN
      RAISE EXCEPTION 'Primera fecha VENCIDA es inmutable';
    END IF;
    IF NEW.estado <> OLD.estado AND NEW.version_fila <> OLD.version_fila + 1 THEN
      RAISE EXCEPTION 'Cada cambio real incrementa version_fila en uno';
    END IF;
    IF NEW.estado = OLD.estado AND NEW.version_fila <> OLD.version_fila THEN
      RAISE EXCEPTION 'Sin cambio no se incrementa version_fila';
    END IF;
  END IF;
  RETURN NEW;
END $$;

COMMENT ON FUNCTION mycar.tr_validar_alerta() IS
  'Consistencia alerta RF22/RF23. MANTENIMIENTO: SEMANAS, MESES y DIAS.';
