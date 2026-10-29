-- CARGA DE DATOS BASE · ejecutar DESPUÉS de 01_ESQUEMA_MYCAR.sql.
-- Catálogos aprobados: FREE; uso Particular/Taxi; 7 tipos de mantenimiento.
-- Catálogos vehiculares motor/combustible/aceite: OPCIONES TÉCNICAS INICIALES
-- para pruebas y desarrollo; el equipo valida las etiquetas antes de publicar.
BEGIN;
INSERT INTO mycar.plan (codigo,nombre,max_vehiculos_registrados,activo)
VALUES ('FREE','Gratis',1,TRUE) ON CONFLICT(codigo) DO NOTHING;
INSERT INTO mycar.uso_vehiculo(codigo,nombre) VALUES
 ('PARTICULAR','Particular'),('TAXI','Taxi') ON CONFLICT(codigo) DO NOTHING;
INSERT INTO mycar.tipo_mantenimiento(codigo,nombre,activo) VALUES
 ('CAMBIO_ACEITE','Cambio de aceite',TRUE),
 ('CAMBIO_FILTROS','Cambio de filtros',TRUE),
 ('REVISION_FRENOS','Revisión de frenos',TRUE),
 ('REVISION_NEUMATICOS','Revisión de neumáticos',TRUE),
 ('ALINEAMIENTO_BALANCEO','Alineamiento y balanceo',TRUE),
 ('MANTENIMIENTO_GENERAL','Mantenimiento general',TRUE),
 ('OTRO','Otro',TRUE)
 ON CONFLICT(codigo) DO NOTHING;
-- Propuestas técnicas de opciones para poner en marcha RF08, no reglas funcionales nuevas.
INSERT INTO mycar.tipo_motor(codigo,nombre,es_electrico) VALUES
 ('COMBUSTION','Combustión',FALSE),('ELECTRICO','Eléctrico',TRUE),('OTRO','Otro',FALSE)
 ON CONFLICT(codigo) DO NOTHING;
INSERT INTO mycar.combustible(codigo,nombre) VALUES
 ('GASOLINA','Gasolina'),('DIESEL','Diésel'),('OTRO','Otro')
 ON CONFLICT(codigo) DO NOTHING;
INSERT INTO mycar.tipo_aceite(codigo,nombre) VALUES
 ('MINERAL','Mineral'),('SEMISINTETICO','Semisintético'),('SINTETICO','Sintético'),('OTRO','Otro')
 ON CONFLICT(codigo) DO NOTHING;
COMMIT;
