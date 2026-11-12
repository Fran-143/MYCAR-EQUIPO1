-- RF34–RF37: catálogo personal de talleres (por usuario, no por vehículo).
-- Conserva mantenimiento.nombre_taller (V12) para historial de texto libre.

CREATE TABLE mycar.taller (
  id_taller BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_usuario BIGINT NOT NULL REFERENCES mycar.usuario(id_usuario) ON DELETE RESTRICT,
  nombre VARCHAR(140) NOT NULL CHECK (length(btrim(nombre)) > 0),
  telefono VARCHAR(30),
  correo VARCHAR(254),
  direccion VARCHAR(200),
  notas VARCHAR(500),
  estado VARCHAR(16) NOT NULL DEFAULT 'ACTIVO'
    CHECK (estado IN ('ACTIVO', 'INACTIVO')),
  favorito BOOLEAN NOT NULL DEFAULT false,
  version_fila INTEGER NOT NULL DEFAULT 0 CHECK (version_fila >= 0),
  creado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  CONSTRAINT ck_taller_favorito_solo_activo CHECK (NOT favorito OR estado = 'ACTIVO')
);

CREATE INDEX ix_taller_usuario_estado ON mycar.taller (id_usuario, estado, favorito DESC, nombre);

-- Máximo un favorito ACTIVO por usuario.
CREATE UNIQUE INDEX uq_taller_favorito_activo
  ON mycar.taller (id_usuario)
  WHERE favorito AND estado = 'ACTIVO';

ALTER TABLE mycar.mantenimiento
  ADD COLUMN IF NOT EXISTS id_taller BIGINT
  REFERENCES mycar.taller(id_taller) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS ix_mantenimiento_taller
  ON mycar.mantenimiento (id_taller)
  WHERE id_taller IS NOT NULL;
