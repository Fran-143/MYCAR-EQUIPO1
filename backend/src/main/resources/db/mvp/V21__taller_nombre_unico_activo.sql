-- RF34/RF35 EA: un nombre ACTIVO único por usuario (tras normalización UPPER en servicio).
CREATE UNIQUE INDEX IF NOT EXISTS uq_taller_nombre_activo
  ON mycar.taller (id_usuario, nombre)
  WHERE estado = 'ACTIVO';
