-- Temporal académico: habilita PREMIUM (máx. 3 vehículos) y migra suscripciones activas FREE → PREMIUM.
-- RF de planes comerciales se definirá después; no elimina FREE del catálogo.
INSERT INTO mycar.plan (codigo, nombre, max_vehiculos_registrados, activo)
VALUES ('PREMIUM', 'Premium', 3, TRUE)
ON CONFLICT (codigo) DO UPDATE
SET nombre = EXCLUDED.nombre,
    max_vehiculos_registrados = EXCLUDED.max_vehiculos_registrados,
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
