-- Temporal académico: cupo amplio para pruebas de varios vehículos.
UPDATE mycar.plan
SET max_vehiculos_registrados = 20, activo = TRUE
WHERE codigo IN ('FREE', 'PREMIUM');

UPDATE mycar.suscripcion s
SET id_plan = p_premium.id_plan
FROM mycar.plan p_free, mycar.plan p_premium
WHERE s.id_plan = p_free.id_plan
  AND p_free.codigo = 'FREE'
  AND p_premium.codigo = 'PREMIUM'
  AND s.estado = 'ACTIVA'
  AND s.fin_utc IS NULL;
