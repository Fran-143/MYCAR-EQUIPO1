-- Temporal académico: PREMIUM y FREE con cupo 3; suscripciones activas → PREMIUM.
-- Se reemplazará cuando se implemente el RF de planes.
UPDATE mycar.plan
SET max_vehiculos_registrados = 3, activo = TRUE
WHERE codigo IN ('FREE', 'PREMIUM');

INSERT INTO mycar.plan (codigo, nombre, max_vehiculos_registrados, activo)
VALUES ('PREMIUM', 'Premium', 3, TRUE)
ON CONFLICT (codigo) DO UPDATE
SET nombre = EXCLUDED.nombre,
    max_vehiculos_registrados = 3,
    activo = TRUE;

UPDATE mycar.suscripcion s
SET id_plan = p_premium.id_plan
FROM mycar.plan p_free,
     mycar.plan p_premium
WHERE s.id_plan = p_free.id_plan
  AND p_free.codigo = 'FREE'
  AND p_premium.codigo = 'PREMIUM'
  AND s.estado = 'ACTIVA'
  AND s.fin_utc IS NULL;
