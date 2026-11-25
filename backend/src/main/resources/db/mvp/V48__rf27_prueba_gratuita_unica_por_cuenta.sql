-- RF27: una sola solicitud de prueba gratuita Premium por cuenta (toda la vida).
-- No borra historial; solo garantiza unicidad a futuro. Sandbox (PAGO_SANDBOX) no se restringe aquí.

CREATE UNIQUE INDEX IF NOT EXISTS uq_solicitud_prueba_gratuita_unica_usuario
    ON mycar.solicitud_suscripcion (id_usuario)
    WHERE modalidad = 'PRUEBA_GRATUITA';
