-- Vista SQL que reconstruye un dato calculado (no es columna persistida en vehiculo).
-- Dato calculado: kilometraje actual = última lectura efectiva por vehículo.
-- Procedencia del modelo base: backend/src/main/resources/db/mvp/V2__tablas_mvp.sql
-- Ejecutar sobre el esquema mycar tras aplicar migraciones Flyway V1–V6.

CREATE OR REPLACE VIEW mycar.v_vehiculo_km_actual AS
SELECT
    v.id_vehiculo,
    v.id_usuario,
    v.placa,
    v.estado_vehiculo,
    v.version_km,
    lk.id_lectura_km          AS id_lectura_actual,
    lk.valor_km               AS km_actual_calculado,
    lk.fecha_lectura_utc      AS fecha_lectura_actual_utc,
    lk.origen                 AS origen_lectura_actual
FROM mycar.vehiculo v
LEFT JOIN LATERAL (
    SELECT l.id_lectura_km, l.valor_km, l.fecha_lectura_utc, l.origen
    FROM mycar.lectura_kilometraje l
    WHERE l.id_vehiculo = v.id_vehiculo
    ORDER BY l.fecha_lectura_utc DESC, l.id_lectura_km DESC
    LIMIT 1
) lk ON TRUE;

COMMENT ON VIEW mycar.v_vehiculo_km_actual IS
 'Reconstruye el kilometraje actual como dato calculado a partir de lectura_kilometraje (RF09/RF10/RF14).';

-- Consulta de verificación (resultado esperado: 0..N filas según datos; sin error de sintaxis/catálogo):
-- SELECT id_vehiculo, placa, km_actual_calculado, version_km FROM mycar.v_vehiculo_km_actual ORDER BY id_vehiculo;
