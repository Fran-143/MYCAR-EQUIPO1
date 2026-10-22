-- Contratos persistentes. No implementa autenticación, envíos, pagos ni tareas programadas.
CREATE TABLE mycar.config_recordatorio_km_usuario (
 id_usuario BIGINT PRIMARY KEY REFERENCES mycar.usuario ON DELETE CASCADE,
 estado VARCHAR(10) NOT NULL DEFAULT 'PAUSADO' CHECK (estado IN ('ACTIVO','PAUSADO')),
 actualizado_utc TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE mycar.config_recordatorio_km ADD CONSTRAINT ck_ciclo_km_estado CHECK (estado IN ('ACTIVO','PAUSADO'));
ALTER TABLE mycar.config_recordatorio_km ADD CONSTRAINT ck_ciclo_km_frecuencia
 CHECK (frecuencia IN ('DIARIO','INTERDIARIO','SEMANAL','QUINCENAL','MENSUAL'));
CREATE TABLE mycar.control_verificacion (
 id_usuario BIGINT REFERENCES mycar.usuario ON DELETE CASCADE,
 proposito VARCHAR(32) NOT NULL,
 intentos_fallidos SMALLINT NOT NULL DEFAULT 0 CHECK (intentos_fallidos BETWEEN 0 AND 5),
 ultimo_envio_utc TIMESTAMPTZ,
 bloqueado_hasta_utc TIMESTAMPTZ,
 PRIMARY KEY(id_usuario,proposito)
);
COMMENT ON TABLE mycar.control_verificacion IS 'Bloqueo por usuario/propósito que sobrevive al reenvío de un código; RF53: 60 s entre envíos, 5 intentos, bloqueo 60 min en servicio transaccional.';
ALTER TABLE mycar.archivo ADD FOREIGN KEY(id_usuario) REFERENCES mycar.usuario ON DELETE CASCADE;
CREATE TABLE mycar.tarea_borrado_archivo (
 id_solicitud BIGINT REFERENCES mycar.solicitud_eliminacion ON DELETE CASCADE,
 id_archivo BIGINT REFERENCES mycar.archivo ON DELETE CASCADE,
 clave_objeto VARCHAR(350) NOT NULL,
 confirmada_utc TIMESTAMPTZ,
 PRIMARY KEY(id_solicitud,id_archivo)
);
ALTER TABLE mycar.correo_saliente ADD FOREIGN KEY(id_codigo) REFERENCES mycar.codigo_verificacion DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE mycar.correo_saliente ADD CONSTRAINT ck_correo_estado
 CHECK (estado IN ('PENDIENTE','EN_PROCESO','ENVIADO','EXPIRADO','CANCELADO'));
ALTER TABLE mycar.correo_saliente ADD CONSTRAINT ck_correo_payload
 CHECK ((estado IN ('PENDIENTE','EN_PROCESO') AND contenido_cifrado IS NOT NULL AND octet_length(contenido_cifrado)>0 AND clave_cifrado_id IS NOT NULL AND length(btrim(clave_cifrado_id))>0)
 OR (estado IN ('ENVIADO','EXPIRADO','CANCELADO') AND contenido_cifrado IS NULL AND clave_cifrado_id IS NULL));
ALTER TABLE mycar.correo_saliente ADD CONSTRAINT ck_correo_plazo CHECK(expira_utc>creado_utc AND intentos>=0);
CREATE INDEX idx_correo_pendiente ON mycar.correo_saliente(proximo_intento_utc) WHERE estado IN ('PENDIENTE','EN_PROCESO');
COMMENT ON COLUMN mycar.correo_saliente.contenido_cifrado IS 'Sobre cifrado autenticado por aplicación: destino real y parámetros, incluido OTP; clave externa a BD. Borrar al enviar/expirar/cancelar.';
ALTER TABLE mycar.codigo_verificacion ADD CONSTRAINT ck_codigo_plazo CHECK (expira_utc>creado_utc);
ALTER TABLE mycar.sesion ADD CONSTRAINT ck_sesion_plazo CHECK (expira_utc>creada_utc);
ALTER TABLE mycar.solicitud_eliminacion ADD CONSTRAINT ck_eliminacion_estado CHECK (estado IN ('PENDIENTE','CANCELADA','EJECUTADA'));
ALTER TABLE mycar.dispositivo_push ADD CONSTRAINT ck_push_permiso CHECK (permiso_so IN ('NO_SOLICITADO','CONCEDIDO','DENEGADO'));
ALTER TABLE mycar.dispositivo_push ADD CONSTRAINT ck_push_token CHECK
 ((hash_token_push IS NULL AND token_cifrado IS NULL) OR (hash_token_push IS NOT NULL AND token_cifrado IS NOT NULL));
ALTER TABLE mycar.dispositivo_push ADD CONSTRAINT ck_push_plataforma CHECK(plataforma='ANDROID');
ALTER TABLE mycar.correccion_kilometraje ADD CONSTRAINT uq_correccion_lectura_previa UNIQUE(id_lectura_anterior);
ALTER TABLE mycar.correccion_kilometraje ADD CONSTRAINT ck_correccion_version CHECK(version_anterior>=0);
ALTER TABLE mycar.mantenimiento ADD CONSTRAINT ck_mant_origen_km CHECK
 ((origen_km_servicio='LECTURA' AND id_lectura_km_referencia IS NOT NULL) OR
 (origen_km_servicio='HISTORICO_MANUAL' AND id_lectura_km_referencia IS NULL));
ALTER TABLE mycar.mantenimiento ADD CONSTRAINT fk_mant_lectura_mismo_vehiculo
 FOREIGN KEY(id_vehiculo,id_lectura_km_referencia) REFERENCES mycar.lectura_kilometraje(id_vehiculo,id_lectura_km) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE mycar.mantenimiento ADD CONSTRAINT ck_mant_siguiente CHECK
 ((proxima_fecha_sugerida IS NULL OR proxima_fecha_sugerida>fecha_servicio) AND
 (proximo_km_sugerido IS NULL OR proximo_km_sugerido>km_servicio));
ALTER TABLE mycar.soat ADD UNIQUE(id_vehiculo,id_soat);
ALTER TABLE mycar.soat ADD CONSTRAINT fk_soat_anterior_mismo_vehiculo FOREIGN KEY(id_vehiculo,id_soat_anterior)
 REFERENCES mycar.soat(id_vehiculo,id_soat) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE mycar.soat ADD CHECK(id_soat_anterior IS NULL OR id_soat_anterior<>id_soat);
ALTER TABLE mycar.evento_incidencia ADD UNIQUE(id_incidencia,id_evento_incidencia);
ALTER TABLE mycar.evidencia_incidencia ADD CONSTRAINT fk_evidencia_evento_mismo_caso
 FOREIGN KEY(id_incidencia,id_evento_incidencia) REFERENCES mycar.evento_incidencia(id_incidencia,id_evento_incidencia) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE mycar.licencia ADD CONSTRAINT ck_licencia_administrativo
 CHECK (estado_oficial IN ('VIGENTE','SUSPENDIDA','VENCIDA','CANCELADA','NO_DETERMINADA'));
COMMENT ON COLUMN mycar.licencia.fecha_revalidacion IS 'RF51/RF52: fecha_vencimiento externa se mapea a fecha_revalidacion. Vigencia temporal y estado administrativo son conceptos separados.';
COMMENT ON COLUMN mycar.licencia.consulta_utc IS 'Último intento de consulta. ultima_consulta_exitosa_utc conserva el último éxito aunque falle el siguiente intento.';
COMMENT ON COLUMN mycar.licencia.proxima_consulta_utc IS 'RF51: último éxito +30 días; programador/adapter externo pendiente, deshabilitado hasta contrato.';
CREATE UNIQUE INDEX uq_alerta_licencia_abierta ON mycar.alerta(id_licencia)
 WHERE categoria='LICENCIA' AND estado NOT IN ('REALIZADA','ANULADA');
ALTER TABLE mycar.alerta ADD CONSTRAINT ck_alerta_licencia_fecha CHECK
 (categoria<>'LICENCIA' OR (criterio IS NOT NULL AND criterio='FECHA' AND fecha_objetivo IS NOT NULL AND km_objetivo IS NULL AND id_tipo_mantenimiento IS NULL));
ALTER TABLE mycar.alerta ADD FOREIGN KEY(id_tipo_aceite_referencia) REFERENCES mycar.tipo_aceite;
ALTER TABLE mycar.alerta ADD CONSTRAINT ck_alerta_config_manual CHECK
 (categoria<>'MANTENIMIENTO' OR origen<>'MANUAL' OR
 (id_tipo_mantenimiento IS NOT NULL AND criterio IS NOT NULL AND criterio IN ('POR_TIEMPO','POR_KILOMETRAJE','AMBOS') AND
 ((criterio='POR_TIEMPO' AND fecha_objetivo IS NOT NULL AND km_objetivo IS NULL AND km_base IS NULL AND intervalo_km IS NULL) OR
 (criterio='POR_KILOMETRAJE' AND fecha_objetivo IS NULL AND unidad_tiempo IS NULL AND cantidad_tiempo IS NULL AND fecha_base IS NULL) OR
 (criterio='AMBOS' AND fecha_objetivo IS NOT NULL AND km_objetivo IS NOT NULL)) AND
 (criterio='POR_KILOMETRAJE' OR (unidad_tiempo IS NOT NULL AND unidad_tiempo IN ('SEMANAS','MESES') AND cantidad_tiempo IS NOT NULL AND cantidad_tiempo>0 AND fecha_base IS NOT NULL AND fecha_objetivo>fecha_base)) AND
 (criterio='POR_TIEMPO' OR (intervalo_km IS NOT NULL AND intervalo_km>0 AND km_base IS NOT NULL AND km_objetivo IS NOT NULL AND km_objetivo>km_base))));
CREATE UNIQUE INDEX uq_alerta_manual_firma ON mycar.alerta
 (id_usuario,id_vehiculo,id_tipo_mantenimiento,criterio,coalesce(fecha_objetivo,'infinity'::date),coalesce(km_objetivo,-1),mycar.normalizar_texto(coalesce(servicio_otro,'')))
 WHERE categoria='MANTENIMIENTO' AND origen='MANUAL' AND estado NOT IN ('REALIZADA','ANULADA');
CREATE UNIQUE INDEX uq_usuario_correo_normalizado ON mycar.usuario(lower(btrim(correo)));
CREATE UNIQUE INDEX uq_tipo_mantenimiento_nombre_normalizado ON mycar.tipo_mantenimiento(mycar.normalizar_texto(nombre));
CREATE UNIQUE INDEX uq_tipo_motor_nombre_normalizado ON mycar.tipo_motor(mycar.normalizar_texto(nombre));
CREATE UNIQUE INDEX uq_combustible_nombre_normalizado ON mycar.combustible(mycar.normalizar_texto(nombre));
CREATE UNIQUE INDEX uq_tipo_aceite_nombre_normalizado ON mycar.tipo_aceite(mycar.normalizar_texto(nombre));
ALTER TABLE mycar.tipo_mantenimiento ADD CHECK(length(mycar.normalizar_texto(nombre))>0);
ALTER TABLE mycar.usuario ADD CHECK(length(btrim(nombres))>0 AND length(btrim(correo))>3);
ALTER TABLE mycar.vehiculo ADD CHECK (NOT es_principal OR estado_vehiculo='ACTIVO');
ALTER TABLE mycar.vehiculo ADD CHECK (version_km>=0);

CREATE FUNCTION mycar.fn_validar_nacimiento() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE edad INT;
BEGIN
 IF TG_OP='UPDATE' AND OLD.fecha_nacimiento IS NOT DISTINCT FROM NEW.fecha_nacimiento THEN RETURN NEW; END IF;
 edad := extract(year FROM age((CURRENT_TIMESTAMP AT TIME ZONE 'America/Lima')::date,NEW.fecha_nacimiento));
 IF edad NOT BETWEEN 18 AND 70 THEN RAISE EXCEPTION 'RF01/RF04: edad permitida de 18 a 70 años'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER tr_usuario_nacimiento BEFORE INSERT OR UPDATE OF fecha_nacimiento ON mycar.usuario
 FOR EACH ROW EXECUTE FUNCTION mycar.fn_validar_nacimiento();

CREATE FUNCTION mycar.fn_inicializar_usuario() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,mycar AS $$
BEGIN
 INSERT INTO mycar.usuario_rol(id_usuario,id_rol) SELECT NEW.id_usuario,id_rol FROM mycar.rol WHERE codigo_rol='USUARIO';
 INSERT INTO mycar.preferencia_notificacion(id_usuario) VALUES(NEW.id_usuario);
 INSERT INTO mycar.config_recordatorio_km_usuario(id_usuario) VALUES(NEW.id_usuario);
 INSERT INTO mycar.suscripcion(id_usuario,id_plan,proveedor,estado_proveedor,inicio_utc)
 SELECT NEW.id_usuario,id_plan,'INTERNO','ACTIVA',CURRENT_TIMESTAMP FROM mycar.plan WHERE codigo='FREE';
 RETURN NEW;
END; $$;
CREATE TRIGGER tr_usuario_inicializar AFTER INSERT ON mycar.usuario FOR EACH ROW EXECUTE FUNCTION mycar.fn_inicializar_usuario();

-- Evita invalidar las comprobaciones de propiedad cambiando luego el dueño del padre.
CREATE FUNCTION mycar.fn_propietario_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (to_jsonb(OLD)->TG_ARGV[0]) IS DISTINCT FROM (to_jsonb(NEW)->TG_ARGV[0]) THEN
 RAISE EXCEPTION 'No se permite transferir la propiedad de este registro'; END IF;
 RETURN NEW;
END; $$;
DO $$ DECLARE t TEXT; c TEXT; BEGIN
 FOR t,c IN SELECT * FROM (VALUES
 ('vehiculo','id_usuario'),('taller','id_usuario'),('repuesto','id_usuario'),('archivo','id_usuario'),('archivo','clave_objeto'),
 ('combustible','codigo'),('tipo_aceite','codigo'),('tipo_mantenimiento','codigo'),('conductor','id_usuario'),('licencia','id_conductor'),('alerta','id_usuario'),('alerta','id_vehiculo'),('notificacion','id_usuario'),('correo_saliente','id_usuario'),('mantenimiento','id_vehiculo'),('soat','id_vehiculo'),
 ('incidencia','id_reportante'),('evento_incidencia','id_incidencia'),('consentimiento','id_usuario'),('codigo_verificacion','id_usuario')) x(t,c)
 LOOP EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE OF %I ON mycar.%I FOR EACH ROW EXECUTE FUNCTION mycar.fn_propietario_inmutable(%L)','tr_propietario_'||c,c,t,c); END LOOP;
END; $$;

CREATE FUNCTION mycar.fn_validar_motor() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE m mycar.tipo_motor%ROWTYPE; codigo_f TEXT; codigo_a TEXT;
BEGIN
 SELECT * INTO STRICT m FROM mycar.tipo_motor WHERE id_tipo_motor=NEW.id_tipo_motor;
 IF m.codigo='OTRO' AND length(btrim(coalesce(NEW.tipo_motor_otro,'')))=0 THEN RAISE EXCEPTION 'Especifique tipo de motor OTRO'; END IF;
 IF m.codigo<>'OTRO' AND NEW.tipo_motor_otro IS NOT NULL THEN RAISE EXCEPTION 'Detalle motor solo para OTRO'; END IF;
 IF m.es_electrico THEN
 IF NEW.id_combustible IS NOT NULL OR NEW.combustible_otro IS NOT NULL OR NEW.id_tipo_aceite IS NOT NULL OR NEW.tipo_aceite_otro IS NOT NULL OR NEW.numero_motor IS NOT NULL THEN
 RAISE EXCEPTION 'RF08: combustible, aceite y número de motor no aplican al eléctrico'; END IF;
 ELSE
 IF NEW.id_combustible IS NULL THEN RAISE EXCEPTION 'Combustible obligatorio para motor no eléctrico'; END IF;
 SELECT codigo INTO STRICT codigo_f FROM mycar.combustible WHERE id_combustible=NEW.id_combustible;
 IF (codigo_f='OTRO') <> (length(btrim(coalesce(NEW.combustible_otro,'')))>0) THEN RAISE EXCEPTION 'Especifique combustible solo para OTRO'; END IF;
 END IF;
 IF NEW.id_tipo_aceite IS NOT NULL THEN
 SELECT codigo INTO STRICT codigo_a FROM mycar.tipo_aceite WHERE id_tipo_aceite=NEW.id_tipo_aceite;
 IF (codigo_a='OTRO') <> (length(btrim(coalesce(NEW.tipo_aceite_otro,'')))>0) THEN RAISE EXCEPTION 'Especifique aceite solo para OTRO'; END IF;
 ELSIF NEW.tipo_aceite_otro IS NOT NULL THEN RAISE EXCEPTION 'Detalle aceite sin tipo'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER tr_vehiculo_motor BEFORE INSERT OR UPDATE ON mycar.vehiculo FOR EACH ROW EXECUTE FUNCTION mycar.fn_validar_motor();
-- Los códigos/semántica de motor son contratos, el nombre y activo sí son editables.
CREATE TRIGGER tr_motor_codigo BEFORE UPDATE OF codigo ON mycar.tipo_motor FOR EACH ROW EXECUTE FUNCTION mycar.fn_propietario_inmutable('codigo');
CREATE TRIGGER tr_motor_electrico BEFORE UPDATE OF es_electrico ON mycar.tipo_motor FOR EACH ROW EXECUTE FUNCTION mycar.fn_propietario_inmutable('es_electrico');

CREATE FUNCTION mycar.fn_validar_relaciones() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE titular BIGINT; otro BIGINT; veh BIGINT; codigo TEXT; valor INT;
BEGIN
 IF TG_TABLE_NAME='mantenimiento' THEN
 SELECT id_usuario INTO titular FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo;
 IF NEW.id_taller IS NOT NULL THEN
 SELECT id_usuario INTO otro FROM mycar.taller WHERE id_taller=NEW.id_taller;
 IF titular IS DISTINCT FROM otro THEN RAISE EXCEPTION 'Taller y vehículo deben pertenecer al mismo usuario'; END IF; END IF;
 IF NEW.id_alerta_origen IS NOT NULL AND NOT EXISTS(SELECT 1 FROM mycar.alerta WHERE id_alerta=NEW.id_alerta_origen AND id_vehiculo=NEW.id_vehiculo AND id_usuario=titular AND categoria='MANTENIMIENTO') THEN RAISE EXCEPTION 'Alerta origen ajena'; END IF;
 IF NEW.id_lectura_km_referencia IS NOT NULL THEN
 SELECT valor_km INTO valor FROM mycar.lectura_kilometraje WHERE id_lectura_km=NEW.id_lectura_km_referencia AND id_vehiculo=NEW.id_vehiculo;
 IF valor IS DISTINCT FROM NEW.km_servicio THEN RAISE EXCEPTION 'Lectura y km del servicio no coinciden'; END IF; END IF;
 SELECT tm.codigo INTO codigo FROM mycar.tipo_mantenimiento tm WHERE tm.id_tipo_mantenimiento=NEW.id_tipo_mantenimiento;
 IF codigo='OTRO' AND length(btrim(coalesce(NEW.servicio_otro,'')))=0 THEN RAISE EXCEPTION 'Servicio OTRO requiere descripción'; END IF;
 ELSIF TG_TABLE_NAME='alerta' THEN
 IF NEW.id_vehiculo IS NOT NULL AND NOT EXISTS(SELECT 1 FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo AND id_usuario=NEW.id_usuario) THEN RAISE EXCEPTION 'Alerta y vehículo de distintos propietarios'; END IF;
 IF NEW.id_soat IS NOT NULL AND NOT EXISTS(SELECT 1 FROM mycar.soat WHERE id_soat=NEW.id_soat AND id_vehiculo=NEW.id_vehiculo) THEN RAISE EXCEPTION 'SOAT ajeno al vehículo'; END IF;
 IF NEW.id_licencia IS NOT NULL AND NOT EXISTS(SELECT 1 FROM mycar.licencia l JOIN mycar.conductor c USING(id_conductor) WHERE l.id_licencia=NEW.id_licencia AND c.id_usuario=NEW.id_usuario) THEN RAISE EXCEPTION 'Licencia ajena'; END IF;
 IF NEW.id_mantenimiento_referencia IS NOT NULL AND NOT EXISTS(SELECT 1 FROM mycar.mantenimiento WHERE id_mantenimiento=NEW.id_mantenimiento_referencia AND id_vehiculo=NEW.id_vehiculo) THEN RAISE EXCEPTION 'Mantenimiento de referencia ajeno'; END IF;
 IF NEW.id_tipo_mantenimiento IS NOT NULL THEN
 SELECT tm.codigo INTO codigo FROM mycar.tipo_mantenimiento tm WHERE tm.id_tipo_mantenimiento=NEW.id_tipo_mantenimiento;
 IF codigo='OTRO' THEN
 NEW.servicio_otro:=mycar.normalizar_texto(coalesce(NEW.servicio_otro,''));
 IF length(NEW.servicio_otro)=0 THEN RAISE EXCEPTION 'Servicio OTRO requiere descripción'; END IF;
 ELSE NEW.servicio_otro:=NULL; END IF; END IF;
 IF NEW.categoria='MANTENIMIENTO' AND NEW.origen='MANUAL' THEN
 NEW.clave_deduplicacion_activa:='M:'||md5(concat_ws('|',NEW.id_usuario,NEW.id_vehiculo,NEW.id_tipo_mantenimiento,NEW.criterio,coalesce(NEW.fecha_objetivo::text,'NULL'),coalesce(NEW.km_objetivo::text,'NULL'),coalesce(NEW.servicio_otro,'')));
 END IF;
 ELSIF TG_TABLE_NAME='mantenimiento_repuesto' THEN
 SELECT v.id_usuario INTO titular FROM mycar.mantenimiento m JOIN mycar.vehiculo v USING(id_vehiculo) WHERE m.id_mantenimiento=NEW.id_mantenimiento;
 SELECT id_usuario INTO otro FROM mycar.repuesto WHERE id_repuesto=NEW.id_repuesto;
 IF titular IS DISTINCT FROM otro THEN RAISE EXCEPTION 'Repuesto ajeno al mantenimiento'; END IF;
 ELSIF TG_TABLE_NAME='notificacion' THEN
 IF NEW.id_vehiculo IS NOT NULL AND NOT EXISTS(SELECT 1 FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo AND id_usuario=NEW.id_usuario) THEN RAISE EXCEPTION 'Notificación y vehículo de distintos propietarios'; END IF;
 IF NEW.id_alerta IS NOT NULL AND NOT EXISTS(SELECT 1 FROM mycar.alerta WHERE id_alerta=NEW.id_alerta AND id_usuario=NEW.id_usuario AND id_vehiculo IS NOT DISTINCT FROM NEW.id_vehiculo) THEN RAISE EXCEPTION 'Notificación y alerta de distinto contexto'; END IF;
 IF NEW.tipo_destino='ALERTA' THEN
 IF NEW.id_alerta IS NULL OR NEW.id_referencia_destino IS DISTINCT FROM NEW.id_alerta THEN RAISE EXCEPTION 'Destino alerta inconsistente'; END IF;
 ELSIF NEW.tipo_destino IN ('VEHICULO','KILOMETRAJE') THEN
 IF NEW.id_vehiculo IS NULL OR NEW.id_referencia_destino IS DISTINCT FROM NEW.id_vehiculo THEN RAISE EXCEPTION 'Destino vehículo inconsistente'; END IF;
 ELSIF NEW.tipo_destino='LICENCIA' THEN
 IF NOT EXISTS(SELECT 1 FROM mycar.licencia l JOIN mycar.conductor c USING(id_conductor) WHERE l.id_licencia=NEW.id_referencia_destino AND c.id_usuario=NEW.id_usuario) THEN RAISE EXCEPTION 'Destino licencia ajeno'; END IF;
 ELSIF NEW.tipo_destino='SOAT' THEN
 IF NOT EXISTS(SELECT 1 FROM mycar.soat WHERE id_soat=NEW.id_referencia_destino AND id_vehiculo=NEW.id_vehiculo) THEN RAISE EXCEPTION 'Destino SOAT ajeno'; END IF;
 ELSIF NEW.tipo_destino='MANTENIMIENTO' THEN
 IF NOT EXISTS(SELECT 1 FROM mycar.mantenimiento WHERE id_mantenimiento=NEW.id_referencia_destino AND id_vehiculo=NEW.id_vehiculo) THEN RAISE EXCEPTION 'Destino mantenimiento ajeno'; END IF;
 ELSE RAISE EXCEPTION 'Tipo de destino desconocido'; END IF;
 ELSIF TG_TABLE_NAME='consulta_licencia_externa' THEN
 SELECT id_usuario INTO titular FROM mycar.conductor WHERE id_conductor=NEW.id_conductor;
 SELECT id_usuario INTO otro FROM mycar.consentimiento WHERE id_consentimiento=NEW.id_consentimiento;
 IF titular IS DISTINCT FROM otro THEN RAISE EXCEPTION 'Consentimiento ajeno al conductor'; END IF;
 ELSIF TG_TABLE_NAME='solicitud_correccion_vin' THEN
 SELECT id_usuario INTO titular FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo;
 IF titular IS DISTINCT FROM NEW.id_usuario_solicitante THEN RAISE EXCEPTION 'Solicitante VIN ajeno al vehículo'; END IF;
 IF NEW.id_archivo_evidencia IS NOT NULL AND NOT EXISTS(SELECT 1 FROM mycar.archivo WHERE id_archivo=NEW.id_archivo_evidencia AND id_usuario=titular) THEN RAISE EXCEPTION 'Evidencia VIN ajena'; END IF;
 ELSIF TG_TABLE_NAME='correccion_kilometraje' THEN
 IF NOT EXISTS(SELECT 1 FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo AND id_usuario=NEW.id_usuario_responsable) THEN RAISE EXCEPTION 'Autor de corrección ajeno al vehículo'; END IF;
 ELSIF TG_TABLE_NAME='correo_saliente' THEN
 IF NEW.id_codigo IS NOT NULL AND NOT EXISTS(SELECT 1 FROM mycar.codigo_verificacion WHERE id_codigo=NEW.id_codigo AND id_usuario=NEW.id_usuario) THEN RAISE EXCEPTION 'Código de otro usuario'; END IF;
 END IF;
 RETURN NEW;
END; $$;
DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['mantenimiento','alerta','mantenimiento_repuesto','notificacion','consulta_licencia_externa','correo_saliente','correccion_kilometraje','solicitud_correccion_vin'] LOOP
 EXECUTE format('CREATE TRIGGER tr_relaciones BEFORE INSERT OR UPDATE ON mycar.%I FOR EACH ROW EXECUTE FUNCTION mycar.fn_validar_relaciones()',t);
 END LOOP;
END; $$;

CREATE FUNCTION mycar.fn_validar_archivo() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE titular BIGINT; dueno BIGINT;
BEGIN
 SELECT id_usuario INTO dueno FROM mycar.archivo WHERE id_archivo=NEW.id_archivo;
 IF TG_TABLE_NAME='imagen_perfil' THEN titular:=NEW.id_usuario;
 ELSIF TG_TABLE_NAME IN ('imagen_vehiculo','imagen_tive') THEN
 SELECT id_usuario INTO titular FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo;
 ELSE SELECT id_reportante INTO titular FROM mycar.incidencia WHERE id_incidencia=NEW.id_incidencia;
 END IF;
 IF titular IS NULL OR titular IS DISTINCT FROM dueno THEN RAISE EXCEPTION 'Archivo fuera de su propietario lógico'; END IF;
 RETURN NEW;
END; $$;
DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['imagen_perfil','imagen_vehiculo','imagen_tive','evidencia_incidencia'] LOOP
 EXECUTE format('CREATE TRIGGER tr_archivo_propietario BEFORE INSERT OR UPDATE ON mycar.%I FOR EACH ROW EXECUTE FUNCTION mycar.fn_validar_archivo()',t);
 END LOOP;
END; $$;
CREATE FUNCTION mycar.fn_bloquear_archivos_en_supresion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM 1 FROM mycar.usuario WHERE id_usuario=NEW.id_usuario AND estado_cuenta='ACTIVA' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Cuenta no activa para cargar archivos'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER tr_archivo_cuenta_activa BEFORE INSERT ON mycar.archivo FOR EACH ROW EXECUTE FUNCTION mycar.fn_bloquear_archivos_en_supresion();
CREATE OR REPLACE FUNCTION mycar.fn_validar_tive() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE v_vin VARCHAR(17);
BEGIN
  SELECT numero_vin INTO v_vin FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Vehículo inexistente'; END IF;
  IF v_vin IS NULL OR btrim(v_vin)='' THEN
    RAISE EXCEPTION 'RF54: se requiere VIN del vehículo antes de registrar TIVE';
  END IF;
  IF NEW.numero_vin_documento IS NOT NULL AND upper(btrim(NEW.numero_vin_documento))<>upper(btrim(v_vin)) THEN
    RAISE EXCEPTION 'El VIN documental no coincide con el vehículo';
  END IF;
  RETURN NEW;
END;$$;
CREATE TRIGGER tr_tive_vin BEFORE INSERT OR UPDATE OF id_vehiculo, numero_vin_documento
 ON mycar.tive FOR EACH ROW EXECUTE FUNCTION mycar.fn_validar_tive();

CREATE FUNCTION mycar.fn_bloquear_vin_con_tive() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.numero_vin IS DISTINCT FROM NEW.numero_vin AND EXISTS
 (SELECT 1 FROM mycar.tive WHERE id_vehiculo=OLD.id_vehiculo) THEN
 RAISE EXCEPTION 'RF11: VIN con TIVE no admite edición ordinaria; soporte reservado';
 END IF; RETURN NEW;
END; $$;
CREATE TRIGGER tr_vehiculo_vin_restringido BEFORE UPDATE OF numero_vin ON mycar.vehiculo
 FOR EACH ROW EXECUTE FUNCTION mycar.fn_bloquear_vin_con_tive();

CREATE VIEW mycar.lectura_kilometraje_efectiva AS
 SELECT l.* FROM mycar.lectura_kilometraje l WHERE l.origen<>'REGISTRO_SERVICIO'
 AND NOT EXISTS(SELECT 1 FROM mycar.correccion_kilometraje c WHERE c.id_lectura_anterior=l.id_lectura_km);
COMMENT ON VIEW mycar.lectura_kilometraje_efectiva IS 'Antecedentes válidos; los originales sustituidos siguen en lectura_kilometraje para auditoría.';

-- RF16: serializar por vehículo ANTES de buscar la clave, y comparar el pedido completo.
CREATE FUNCTION mycar.guardar_correccion_km(
 p_usuario BIGINT,p_vehiculo BIGINT,p_lectura_previa BIGINT,p_km_nuevo INTEGER,
 p_motivo TEXT,p_version INTEGER,p_operacion VARCHAR(80)) RETURNS BIGINT
LANGUAGE plpgsql SET search_path=pg_catalog,mycar AS $$
DECLARE v mycar.vehiculo%ROWTYPE; anterior mycar.correccion_kilometraje%ROWTYPE;
 lectura mycar.lectura_kilometraje%ROWTYPE; nueva BIGINT; km_hist INTEGER;
BEGIN
 IF length(btrim(coalesce(p_motivo,''))) NOT BETWEEN 1 AND 2000 THEN RAISE EXCEPTION 'Motivo obligatorio de 1 a 2000 caracteres'; END IF;
 IF p_km_nuevo IS NULL OR p_km_nuevo<0 OR p_version IS NULL OR p_lectura_previa IS NULL THEN RAISE EXCEPTION 'Lectura, versión y km obligatorios'; END IF;
 IF length(btrim(coalesce(p_operacion,''))) NOT BETWEEN 1 AND 80 THEN RAISE EXCEPTION 'Clave de operación obligatoria'; END IF;
 PERFORM 1 FROM mycar.usuario WHERE id_usuario=p_usuario AND estado_cuenta='ACTIVA' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Cuenta no activa'; END IF;
 SELECT * INTO v FROM mycar.vehiculo WHERE id_vehiculo=p_vehiculo FOR UPDATE;
 IF NOT FOUND OR v.id_usuario<>p_usuario THEN RAISE EXCEPTION 'Vehículo ajeno o inexistente'; END IF;
 SELECT * INTO lectura FROM mycar.lectura_kilometraje WHERE id_operacion=p_operacion;
 IF FOUND THEN
 SELECT * INTO anterior FROM mycar.correccion_kilometraje WHERE id_lectura_nueva=lectura.id_lectura_km;
 IF NOT FOUND OR lectura.id_vehiculo<>p_vehiculo OR lectura.valor_km<>p_km_nuevo
 OR anterior.id_usuario_responsable<>p_usuario OR anterior.id_lectura_anterior<>p_lectura_previa
 OR anterior.version_anterior<>p_version OR anterior.motivo_correccion<>btrim(p_motivo) THEN
 RAISE EXCEPTION 'Clave de operación reutilizada con datos diferentes'; END IF;
 RETURN lectura.id_lectura_km;
 END IF;
 IF v.estado_vehiculo<>'ACTIVO' THEN RAISE EXCEPTION 'Vehículo inactivo'; END IF;
 IF v.version_km<>p_version OR v.id_lectura_km_actual IS DISTINCT FROM p_lectura_previa THEN
 RAISE EXCEPTION 'Lectura modificada concurrentemente; recargue los datos'; END IF;
 IF v.km_actual=p_km_nuevo THEN RAISE EXCEPTION 'El kilometraje debe ser diferente'; END IF;
 SELECT l.valor_km INTO km_hist FROM mycar.lectura_kilometraje l
 WHERE l.id_vehiculo=p_vehiculo AND l.id_lectura_km<>p_lectura_previa AND l.origen<>'REGISTRO_SERVICIO'
 AND NOT EXISTS(SELECT 1 FROM mycar.correccion_kilometraje c WHERE c.id_lectura_anterior=l.id_lectura_km)
 ORDER BY l.fecha_lectura_utc DESC,l.id_lectura_km DESC LIMIT 1;
 IF km_hist IS NOT NULL AND p_km_nuevo<km_hist THEN RAISE EXCEPTION 'Km menor que antecedente efectivo'; END IF;
 INSERT INTO mycar.lectura_kilometraje(id_vehiculo,valor_km,origen,id_operacion)
 VALUES(p_vehiculo,p_km_nuevo,'CORRECCION',p_operacion) RETURNING id_lectura_km INTO nueva;
 INSERT INTO mycar.correccion_kilometraje(id_vehiculo,id_lectura_anterior,id_lectura_nueva,id_usuario_responsable,motivo_correccion,version_anterior)
 VALUES(p_vehiculo,p_lectura_previa,nueva,p_usuario,btrim(p_motivo),p_version);
 UPDATE mycar.vehiculo SET km_actual=p_km_nuevo,id_lectura_km_actual=nueva,version_km=version_km+1,modificado_utc=CURRENT_TIMESTAMP WHERE id_vehiculo=p_vehiculo;
 RETURN nueva;
END; $$;
COMMENT ON FUNCTION mycar.guardar_correccion_km IS 'RF16: backend usa id del principal autenticado y vista previa confirmada. Recalcular alertas tras COMMIT mediante trabajo idempotente.';

CREATE FUNCTION mycar.tiene_permiso(p_usuario BIGINT,p_permiso TEXT) RETURNS BOOLEAN
LANGUAGE sql STABLE SET search_path=pg_catalog,mycar AS $$
 SELECT EXISTS(SELECT 1 FROM mycar.usuario u JOIN mycar.usuario_rol ur USING(id_usuario)
 JOIN mycar.rol r USING(id_rol) JOIN mycar.rol_permiso rp USING(id_rol) JOIN mycar.permiso p USING(id_permiso)
 WHERE u.id_usuario=p_usuario AND u.estado_cuenta='ACTIVA' AND r.activo AND p.codigo_permiso=p_permiso)
$$;
COMMENT ON FUNCTION mycar.tiene_permiso IS 'Lookup de autorización, NO autentica. Nunca aceptar p_usuario arbitrario del cliente. Premium exige además verificación de plan y propiedad.';

-- Purga: invocable SOLO por trabajador técnico. No autoriza por GUC solamente.
-- El rol efectivo debe ser el propietario de la función SECURITY DEFINER.
CREATE FUNCTION mycar._contexto_supresion(p_usuario BIGINT,p_vehiculo BIGINT DEFAULT NULL) RETURNS BOOLEAN
LANGUAGE sql STABLE SET search_path=pg_catalog,mycar AS $$
 SELECT current_user=pg_get_userbyid((SELECT proowner FROM pg_proc WHERE pronamespace='mycar'::regnamespace AND proname='ejecutar_supresion_cuenta' AND proargtypes='20'::oidvector))
 AND current_setting('mycar.supresion_usuario',true)=p_usuario::text
 AND (p_vehiculo IS NULL OR p_vehiculo=ANY(coalesce(nullif(current_setting('mycar.supresion_vehiculos',true),''),'{}')::bigint[]))
$$;
CREATE FUNCTION mycar.fn_proteger_usuario() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT coalesce(mycar._contexto_supresion(OLD.id_usuario),false) THEN RAISE EXCEPTION 'Use el proceso autorizado de supresión de cuenta'; END IF;
 RETURN OLD;
END; $$;
CREATE TRIGGER tr_usuario_supresion BEFORE DELETE ON mycar.usuario FOR EACH ROW EXECUTE FUNCTION mycar.fn_proteger_usuario();
CREATE FUNCTION mycar.fn_proteger_historico() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' AND coalesce(mycar._contexto_supresion(nullif(current_setting('mycar.supresion_usuario',true),'')::bigint,OLD.id_vehiculo),false) THEN RETURN OLD; END IF;
 RAISE EXCEPTION 'Registro histórico inmutable fuera de la supresión autorizada';
END; $$;
CREATE TRIGGER tr_lectura_inmutable BEFORE UPDATE OR DELETE ON mycar.lectura_kilometraje FOR EACH ROW EXECUTE FUNCTION mycar.fn_proteger_historico();
CREATE TRIGGER tr_correccion_inmutable BEFORE UPDATE OR DELETE ON mycar.correccion_kilometraje FOR EACH ROW EXECUTE FUNCTION mycar.fn_proteger_historico();
CREATE TRIGGER tr_soat_no_eliminar BEFORE DELETE ON mycar.soat FOR EACH ROW EXECUTE FUNCTION mycar.fn_proteger_historico();

CREATE FUNCTION mycar.preparar_supresion_cuenta(p_usuario BIGINT) RETURNS BIGINT
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,mycar AS $$
DECLARE solicitud BIGINT;
BEGIN
 PERFORM 1 FROM mycar.usuario WHERE id_usuario=p_usuario AND estado_cuenta='PENDIENTE_ELIMINACION' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Cuenta sin supresión pendiente'; END IF;
 SELECT id_solicitud INTO solicitud FROM mycar.solicitud_eliminacion WHERE id_usuario=p_usuario AND estado='PENDIENTE' AND limite_utc<=CURRENT_TIMESTAMP FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'No han transcurrido los 30 días calendario'; END IF;
 INSERT INTO mycar.tarea_borrado_archivo(id_solicitud,id_archivo,clave_objeto)
 SELECT solicitud,id_archivo,clave_objeto FROM mycar.archivo WHERE id_usuario=p_usuario ON CONFLICT DO NOTHING;
 RETURN solicitud;
END; $$;
CREATE FUNCTION mycar.confirmar_borrado_archivo(p_solicitud BIGINT,p_archivo BIGINT) RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,mycar AS $$
BEGIN
 UPDATE mycar.tarea_borrado_archivo t SET confirmada_utc=CURRENT_TIMESTAMP FROM mycar.solicitud_eliminacion s
 WHERE t.id_solicitud=s.id_solicitud AND s.id_solicitud=p_solicitud AND t.id_archivo=p_archivo AND s.estado='PENDIENTE' AND s.limite_utc<=CURRENT_TIMESTAMP;
 IF NOT FOUND THEN RAISE EXCEPTION 'Tarea inexistente o solicitud no vencida'; END IF;
END; $$;
CREATE FUNCTION mycar.ejecutar_supresion_cuenta(p_usuario BIGINT) RETURNS BOOLEAN
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,mycar AS $$
DECLARE solicitud BIGINT; vehiculos BIGINT[];
BEGIN
 IF NOT EXISTS(SELECT 1 FROM mycar.usuario WHERE id_usuario=p_usuario) THEN RETURN false; END IF;
 solicitud:=mycar.preparar_supresion_cuenta(p_usuario);
 IF EXISTS(SELECT 1 FROM mycar.tarea_borrado_archivo WHERE id_solicitud=solicitud AND confirmada_utc IS NULL) THEN
 RAISE EXCEPTION 'Falta confirmar el borrado de archivos externos'; END IF;
 SELECT coalesce(array_agg(id_vehiculo),'{}') INTO vehiculos FROM mycar.vehiculo WHERE id_usuario=p_usuario;
 PERFORM set_config('mycar.supresion_usuario',p_usuario::text,true);
 PERFORM set_config('mycar.supresion_vehiculos',vehiculos::text,true);
 DELETE FROM mycar.usuario WHERE id_usuario=p_usuario;
 PERFORM set_config('mycar.supresion_usuario','',true);
 PERFORM set_config('mycar.supresion_vehiculos','{}',true);
 RETURN true;
END; $$;
COMMENT ON FUNCTION mycar.ejecutar_supresion_cuenta IS 'Sin retención adicional ni tombstone personal. Requiere worker externo que borre objetos y confirme tareas. Backups/cachés/proveedores deben integrarse al proceso antes de producción.';

-- Índices de acceso y de eliminación por agregado.
CREATE INDEX idx_vehiculo_usuario_estado ON mycar.vehiculo(id_usuario,estado_vehiculo);
CREATE INDEX idx_lectura_vehiculo_fecha ON mycar.lectura_kilometraje(id_vehiculo,fecha_lectura_utc DESC,id_lectura_km DESC);
CREATE INDEX idx_mantenimiento_vehiculo_fecha ON mycar.mantenimiento(id_vehiculo,fecha_servicio DESC);
CREATE INDEX idx_soat_vehiculo_fechas ON mycar.soat(id_vehiculo,fecha_vencimiento DESC);
CREATE INDEX idx_alerta_usuario_estado_fecha ON mycar.alerta(id_usuario,estado,fecha_objetivo);
CREATE INDEX idx_notificacion_usuario_fecha ON mycar.notificacion(id_usuario,creada_utc DESC);
CREATE INDEX idx_suscripcion_usuario_estado ON mycar.suscripcion(id_usuario,estado_proveedor,fin_utc DESC);
CREATE INDEX idx_incidencia_estado_fecha ON mycar.incidencia(estado,creada_utc DESC);
CREATE INDEX idx_archivo_propietario ON mycar.archivo(id_usuario);
COMMENT ON TABLE mycar.vehiculo IS 'PK estable id_vehiculo, placa única por titular y VIN opcional único global. Free: alta solo si total registrados=0; downgrade conserva inactivos, solo principal activo.';
COMMENT ON TABLE mycar.tive IS '0..1 por vehículo; VIN obligatorio para registrar, sin unicidad global de título/código sin prueba oficial.';
COMMENT ON TABLE mycar.config_recordatorio_km_usuario IS 'Estado global independiente de frecuencia/ciclo de cada vehículo. Pausado por defecto.';
COMMENT ON TABLE mycar.licencia IS 'Estado oficial normalizado y estado_fuente separados; fallos de proveedor no sustituyen último resultado válido. JSON.pe deshabilitado hasta contrato verificado en servicio.';
COMMENT ON TABLE mycar.evidencia_incidencia IS 'Evidencia inicial sin evento, o evidencia de avance ligada a evento del mismo caso. Propietario lógico del archivo: reportante; actor puede ser administrador.';

-- Denegación predeterminada para rutinas: roles técnicos se configuran fuera de Flyway.
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA mycar FROM PUBLIC;
REVOKE ALL ON SCHEMA mycar FROM PUBLIC;
