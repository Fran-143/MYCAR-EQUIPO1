-- Los roles son capacidades. FREE/PREMIUM son planes, no roles.
INSERT INTO mycar.rol(codigo_rol,nombre,activo) VALUES
 ('USUARIO','Usuario',true),('ADMINISTRADOR','Administrador',true),
 ('SOPORTE','Reservado: flujo VIN fuera del alcance aprobado',false);
INSERT INTO mycar.permiso(codigo_permiso,descripcion) VALUES
 ('PERFIL_CONSULTAR','Consultar perfil propio'),
 ('PERFIL_EDITAR','Editar perfil propio'),
 ('CONTRASENA_CAMBIAR','Cambiar contraseña propia'),
 ('CUENTA_ELIMINACION_SOLICITAR','Solicitar o cancelar eliminación propia'),
 ('VEHICULOS_CONSULTAR','Consultar vehículos propios'),
 ('VEHICULOS_REGISTRAR','Registrar vehículo según cupo efectivo'),
 ('VEHICULOS_EDITAR','Editar vehículos propios'),
 ('VEHICULOS_ESTADO_CAMBIAR','Activar o inactivar según plan'),
 ('VEHICULOS_PRINCIPAL_CAMBIAR','Cambiar vehículo principal'),
 ('KILOMETRAJE_CONSULTAR','Consultar lecturas propias'),
 ('KILOMETRAJE_ACTUALIZAR','Agregar lectura propia'),
 ('KILOMETRAJE_CORREGIR','Corregir última lectura con motivo y versión'),
 ('MANTENIMIENTOS_CONSULTAR','Consultar historial propio'),
 ('MANTENIMIENTOS_REGISTRAR','Registrar servicio propio'),
 ('MANTENIMIENTOS_EDITAR','Editar servicio propio permitido'),
 ('MANTENIMIENTOS_ANULAR','Anular servicio propio'),
 ('ALERTAS_CONSULTAR','Consultar alertas propias'),
 ('ALERTAS_PROGRAMAR','Programar alertas propias'),
 ('ALERTAS_ATENDER','Gestionar alertas propias'),
 ('HISTORIAL_EXPORTAR','Exportar historial; PDF requiere Premium efectivo'),
 ('PLANES_CONSULTAR','Consultar planes'),
 ('PREMIUM_CONTRATAR','Iniciar compra mediante proveedor'),
 ('SUSCRIPCION_CONSULTAR','Consultar suscripción propia'),
 ('SOAT_GESTIONAR','Gestionar SOAT de vehículos propios'),
 ('TALLERES_GESTIONAR','Gestionar talleres propios'),
 ('IMAGENES_GESTIONAR','Gestionar archivos propios'),
 ('VEHICULOS_COMPARTIR','Generar información compartida sin acceso ajeno'),
 ('RECORDATORIOS_KM_CONFIGURAR','Configurar recordatorios propios'),
 ('NOTIFICACIONES_GESTIONAR','Consultar y gestionar notificaciones propias'),
 ('LICENCIA_REGISTRAR','Registrar licencia propia; requiere Premium efectivo'),
 ('LICENCIA_CONSULTAR','Consultar licencia conservada incluso en Free'),
 ('INCIDENCIAS_REPORTAR','Reportar incidencia propia'),
 ('INCIDENCIAS_PROPIAS_CONSULTAR','Consultar incidencias propias'),
 ('ACCEDER_PANEL_ADMIN','Acceder a administración'),
 ('CONSULTAR_USUARIOS','Consultar datos necesarios sin hashes ni secretos'),
 ('SUSPENDER_USUARIOS','Suspender cuenta con motivo y auditoría'),
 ('REACTIVAR_USUARIOS','Reactivar cuenta suspendida con auditoría'),
 ('ASISTIR_RECUPERACION_CUENTA','Asistir recuperación sin ver ni elegir contraseña'),
 ('SUPERVISAR_SUSCRIPCIONES','Revisar verificación sin otorgar Premium manualmente'),
 ('GESTIONAR_INCIDENCIAS','Asignar, avanzar y resolver incidencias'),
 ('GESTIONAR_CATALOGOS','Gestionar catálogos con historial referencial');
INSERT INTO mycar.rol_permiso(id_rol,id_permiso) SELECT r.id_rol,p.id_permiso FROM mycar.rol r CROSS JOIN mycar.permiso p WHERE r.codigo_rol='USUARIO' AND p.codigo_permiso IN ('PERFIL_CONSULTAR','PERFIL_EDITAR','CONTRASENA_CAMBIAR','CUENTA_ELIMINACION_SOLICITAR','VEHICULOS_CONSULTAR','VEHICULOS_REGISTRAR','VEHICULOS_EDITAR','VEHICULOS_ESTADO_CAMBIAR','VEHICULOS_PRINCIPAL_CAMBIAR','KILOMETRAJE_CONSULTAR','KILOMETRAJE_ACTUALIZAR','KILOMETRAJE_CORREGIR','MANTENIMIENTOS_CONSULTAR','MANTENIMIENTOS_REGISTRAR','MANTENIMIENTOS_EDITAR','MANTENIMIENTOS_ANULAR','ALERTAS_CONSULTAR','ALERTAS_PROGRAMAR','ALERTAS_ATENDER','HISTORIAL_EXPORTAR','PLANES_CONSULTAR','PREMIUM_CONTRATAR','SUSCRIPCION_CONSULTAR','SOAT_GESTIONAR','TALLERES_GESTIONAR','IMAGENES_GESTIONAR','VEHICULOS_COMPARTIR','RECORDATORIOS_KM_CONFIGURAR','NOTIFICACIONES_GESTIONAR','LICENCIA_REGISTRAR','LICENCIA_CONSULTAR','INCIDENCIAS_REPORTAR','INCIDENCIAS_PROPIAS_CONSULTAR');
INSERT INTO mycar.rol_permiso(id_rol,id_permiso) SELECT r.id_rol,p.id_permiso FROM mycar.rol r CROSS JOIN mycar.permiso p WHERE r.codigo_rol='ADMINISTRADOR' AND p.codigo_permiso IN ('ACCEDER_PANEL_ADMIN','CONSULTAR_USUARIOS','SUSPENDER_USUARIOS','REACTIVAR_USUARIOS','ASISTIR_RECUPERACION_CUENTA','SUPERVISAR_SUSCRIPCIONES','GESTIONAR_INCIDENCIAS','GESTIONAR_CATALOGOS');
INSERT INTO mycar.plan(codigo,nombre,max_vehiculos_registrados,max_vehiculos_activos,permitir_alta_licencia,permitir_exportar_pdf)
VALUES ('FREE','Free',1,1,false,false),('PREMIUM','Premium',NULL,NULL,true,true);
-- Sin cupo Premium aprobado: NULL significa sin límite numérico configurado.
-- La activación de beneficios exige verificación válida del proveedor en backend.
INSERT INTO mycar.tipo_motor(codigo,nombre,es_electrico) VALUES ('ELECTRICO','Eléctrico',true),('OTRO','Otro',false);
INSERT INTO mycar.combustible(codigo,nombre) VALUES ('OTRO','Otro');
INSERT INTO mycar.tipo_aceite(codigo,nombre) VALUES ('OTRO','Otro');
-- Siete nombres recuperados del historial del Excel, AJUSTES_EXITOSOS_2026, fila 5.
INSERT INTO mycar.tipo_mantenimiento(codigo,nombre,es_inicial) VALUES
 ('CAMBIO_ACEITE','Cambio de aceite',true),('CAMBIO_FILTROS','Cambio de filtros',true),
 ('REVISION_FRENOS','Revisión de frenos',true),('NEUMATICOS','Neumáticos',true),
 ('ALINEAMIENTO_BALANCEO','Alineamiento y balanceo',true),
 ('MANTENIMIENTO_GENERAL','Mantenimiento general',true),('OTRO','Otro',true);
