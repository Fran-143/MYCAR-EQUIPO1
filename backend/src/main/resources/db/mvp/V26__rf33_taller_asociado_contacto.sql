-- RF33–RF36: MANUAL vs ASOCIADO, tiene_whatsapp, catálogo global.
-- RF24: preferencia de contacto por alerta (id_taller_contacto).
-- Conserva historial de mantenimientos y no altera estados de alerta.
-- Teléfonos MANUAL duplicados: FAIL-FAST (sin inactivar datos de usuario).

CREATE TABLE IF NOT EXISTS mycar.taller_global (
  id_taller_global BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre VARCHAR(140) NOT NULL CHECK (length(btrim(nombre)) > 0),
  telefono VARCHAR(30),
  correo VARCHAR(254),
  direccion VARCHAR(200),
  tiene_whatsapp BOOLEAN NOT NULL DEFAULT false,
  -- Autorización explícita de contacto (WA/llamada). Teléfono o ASOCIADO solos NO autorizan.
  contacto_autorizado BOOLEAN NOT NULL DEFAULT false,
  es_demo BOOLEAN NOT NULL DEFAULT false,
  estado VARCHAR(16) NOT NULL DEFAULT 'ACTIVO'
    CHECK (estado IN ('ACTIVO', 'INACTIVO')),
  creado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  CONSTRAINT ck_taller_global_wa_requiere_tel
    CHECK (NOT tiene_whatsapp OR (telefono IS NOT NULL AND btrim(telefono) <> '')),
  CONSTRAINT ck_taller_global_contacto_autorizado
    CHECK (
      NOT contacto_autorizado
      OR (tiene_whatsapp AND telefono IS NOT NULL AND btrim(telefono) <> '')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_taller_global_nombre_activo
  ON mycar.taller_global (lower(btrim(nombre)))
  WHERE estado = 'ACTIVO';

ALTER TABLE mycar.taller
  ADD COLUMN IF NOT EXISTS origen VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
  ADD COLUMN IF NOT EXISTS id_taller_global BIGINT
    REFERENCES mycar.taller_global(id_taller_global) ON DELETE RESTRICT,
  ADD COLUMN IF NOT EXISTS tiene_whatsapp BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS contacto_autorizado BOOLEAN NOT NULL DEFAULT false;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'ck_taller_origen'
  ) THEN
    ALTER TABLE mycar.taller
      ADD CONSTRAINT ck_taller_origen CHECK (origen IN ('MANUAL', 'ASOCIADO'));
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'ck_taller_asociado_global'
  ) THEN
    ALTER TABLE mycar.taller
      ADD CONSTRAINT ck_taller_asociado_global CHECK (
        (origen = 'MANUAL' AND id_taller_global IS NULL)
        OR (origen = 'ASOCIADO' AND id_taller_global IS NOT NULL)
      );
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'ck_taller_wa_requiere_tel'
  ) THEN
    ALTER TABLE mycar.taller
      ADD CONSTRAINT ck_taller_wa_requiere_tel
        CHECK (NOT tiene_whatsapp OR (telefono IS NOT NULL AND btrim(telefono) <> ''));
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'ck_taller_contacto_autorizado'
  ) THEN
    ALTER TABLE mycar.taller
      ADD CONSTRAINT ck_taller_contacto_autorizado CHECK (
        NOT contacto_autorizado
        OR (tiene_whatsapp AND telefono IS NOT NULL AND btrim(telefono) <> '')
      );
  END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_taller_vinculo_asociado_activo
  ON mycar.taller (id_usuario, id_taller_global)
  WHERE origen = 'ASOCIADO' AND estado = 'ACTIVO' AND id_taller_global IS NOT NULL;

-- Prechequeo: no crear índice único si ya hay teléfonos MANUAL duplicados (protege datos reales).
DO $$
DECLARE
  n_dups INTEGER;
  muestra TEXT;
BEGIN
  SELECT COUNT(*) INTO n_dups
  FROM (
    SELECT id_usuario, telefono
    FROM mycar.taller
    WHERE COALESCE(origen, 'MANUAL') = 'MANUAL'
      AND estado = 'ACTIVO'
      AND telefono IS NOT NULL
      AND btrim(telefono) <> ''
    GROUP BY id_usuario, telefono
    HAVING COUNT(*) > 1
  ) d;
  IF n_dups > 0 THEN
    SELECT string_agg(fmt, '; ') INTO muestra
    FROM (
      SELECT format('usuario=%s tel=%s ids=%s', id_usuario, telefono,
                    string_agg(id_taller::text, ',' ORDER BY id_taller)) AS fmt
      FROM mycar.taller
      WHERE COALESCE(origen, 'MANUAL') = 'MANUAL'
        AND estado = 'ACTIVO'
        AND telefono IS NOT NULL
        AND btrim(telefono) <> ''
      GROUP BY id_usuario, telefono
      HAVING COUNT(*) > 1
      LIMIT 5
    ) s;
    RAISE EXCEPTION
      'V26 bloqueada: % grupo(s) de teléfono MANUAL duplicado. Resolver antes de migrar. Muestra: %',
      n_dups, COALESCE(muestra, '(sin detalle)');
  END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_taller_telefono_manual_activo
  ON mycar.taller (id_usuario, telefono)
  WHERE origen = 'MANUAL' AND estado = 'ACTIVO' AND telefono IS NOT NULL;

ALTER TABLE mycar.alerta
  ADD COLUMN IF NOT EXISTS id_taller_contacto BIGINT
    REFERENCES mycar.taller(id_taller) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS ix_alerta_taller_contacto
  ON mycar.alerta (id_taller_contacto)
  WHERE id_taller_contacto IS NOT NULL;

-- Catálogo DEMO: solo taller_global, nunca filas en mycar.taller (no son negocios del usuario).
INSERT INTO mycar.taller_global (nombre, telefono, correo, direccion, tiene_whatsapp, es_demo, estado)
SELECT v.nombre, v.telefono, v.correo, v.direccion, v.tiene_whatsapp, true, 'ACTIVO'
FROM (VALUES
  ('[DEMO CATÁLOGO] MYCAR LIMA', '000000001', NULL::varchar, 'LIMA — SOLO CATÁLOGO DEMO', true),
  ('[DEMO CATÁLOGO] MYCAR AREQUIPA', '000000002', NULL::varchar, 'AREQUIPA — SOLO CATÁLOGO DEMO', false)
) AS v(nombre, telefono, correo, direccion, tiene_whatsapp)
WHERE NOT EXISTS (
  SELECT 1 FROM mycar.taller_global g
  WHERE lower(btrim(g.nombre)) = lower(btrim(v.nombre)) AND g.estado = 'ACTIVO'
);
