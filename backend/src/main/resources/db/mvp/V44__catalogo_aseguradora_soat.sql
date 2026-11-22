-- Catálogo administrativo de aseguradoras SOAT (reemplaza mapa hardcodeado como fuente viva).
-- Columna mycar.soat.aseguradora sigue siendo VARCHAR histórico.

CREATE TABLE IF NOT EXISTS mycar.aseguradora_soat (
    id_aseguradora    BIGSERIAL PRIMARY KEY,
    codigo            VARCHAR(80) NOT NULL,
    nombre            VARCHAR(120) NOT NULL,
    activo            BOOLEAN NOT NULL DEFAULT TRUE,
    permite_nombre_libre BOOLEAN NOT NULL DEFAULT FALSE,
    aliases_csv       VARCHAR(500),
    ayuda_poliza      VARCHAR(400),
    ejemplo_poliza    VARCHAR(80),
    creado_utc        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actualizado_utc   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    id_actor_ultimo   BIGINT REFERENCES mycar.usuario(id_usuario) ON DELETE SET NULL,
    CONSTRAINT uq_aseguradora_soat_codigo UNIQUE (codigo),
    CONSTRAINT ck_aseguradora_codigo_no_vacio CHECK (length(btrim(codigo)) > 0),
    CONSTRAINT ck_aseguradora_nombre_no_vacio CHECK (length(btrim(nombre)) > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_aseguradora_soat_nombre_norm
    ON mycar.aseguradora_soat (lower(btrim(nombre)));

INSERT INTO mycar.aseguradora_soat(codigo, nombre, activo, permite_nombre_libre, aliases_csv, ayuda_poliza, ejemplo_poliza)
VALUES
 ('RIMAC SEGUROS', 'RÍMAC SEGUROS', TRUE, FALSE, 'RIMAC,RIMAC SEGUROS Y REASEGUROS',
  'Números y separadores según tu certificado. MyCar valida el formato, no la autenticidad oficial.', NULL),
 ('LA POSITIVA SEGUROS', 'LA POSITIVA SEGUROS', TRUE, FALSE, 'LA POSITIVA,POSITIVA,POSITIVA SEGUROS',
  'Se admiten números y guiones; conserva ceros iniciales. Sin longitud fija documentada.', NULL),
 ('PACIFICO SEGUROS', 'PACÍFICO SEGUROS', TRUE, FALSE, 'PACIFICO,PACIFICO SEGUROS Y REASEGUROS',
  'Ingresa el identificador de tu certificado Pacífico. Sin longitud fija obligatoria.', NULL),
 ('MAPFRE PERU', 'MAPFRE PERÚ', TRUE, FALSE, 'MAPFRE,MAPFRE PERU',
  'Sin formato obligatorio confirmado. Validación general de MyCar.', NULL),
 ('INTERSEGURO', 'INTERSEGURO', TRUE, FALSE, 'INTERSEGURO COMPANIA DE SEGUROS',
  'Ejemplo web de 8 dígitos es ilustrativo, no obligatorio. Usa el número de tu certificado.', '12345678'),
 ('PROTECTA SECURITY', 'PROTECTA SECURITY', TRUE, FALSE, 'PROTECTA,PROTECTA SEGUROS',
  'Formato obligatorio no confirmado. Validación general.', NULL),
 ('CRECER SEGUROS', 'CRECER SEGUROS', TRUE, FALSE, 'CRECER',
  'Figura en APESEG; sin longitud fija documentada. Validación general.', NULL),
 ('VIVIR SEGUROS', 'VIVIR SEGUROS', TRUE, FALSE, 'VIVIR,VIVIR SEGUROS S.A.',
  'Formato obligatorio no confirmado. Validación general.', NULL),
 ('QUALITAS PERU', 'QUÁLITAS PERÚ', TRUE, FALSE, 'QUALITAS,QUALITAS PERU,QUALITAS COMPANIA DE SEGUROS',
  'Portal distingue póliza/certificado; sin longitud fija inferida.', NULL),
 ('HDI SEGUROS', 'HDI SEGUROS', FALSE, FALSE, 'HDI', 'Registro histórico. Validación general.', NULL),
 ('CHUBB PERU', 'CHUBB PERÚ', FALSE, FALSE, 'CHUBB', 'Registro histórico. Validación general.', NULL),
 ('BNP PARIBAS CARDIF', 'BNP PARIBAS CARDIF', FALSE, FALSE, 'CARDIF,BNP PARIBAS CARDIF',
  'Registro histórico. Validación general.', NULL),
 ('OTRA', 'Otra aseguradora', TRUE, TRUE, 'OTRA ASEGURADORA,OTRO',
  'Indica el nombre de la aseguradora tal como aparece en tu certificado.', NULL)
ON CONFLICT (codigo) DO NOTHING;
