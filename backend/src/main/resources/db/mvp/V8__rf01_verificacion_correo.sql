-- RF01: verificación de correo por código (contrato esquema compartido).
-- Reutilizable luego en RF03/RF53 vía proposito. No editar V1–V7.
-- Códigos solo hasheados. correo_verificado_utc nullable sin backfill.

ALTER TABLE mycar.usuario
    ADD COLUMN correo_verificado_utc TIMESTAMPTZ NULL;

-- Límites y bloqueos que sobreviven a reenvíos, cambio de correo u otro proceso.
CREATE TABLE mycar.control_verificacion (
    id_control BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    proposito VARCHAR(32) NOT NULL
        CHECK (proposito IN ('REGISTRO', 'RECUPERACION', 'REACTIVACION', 'CAMBIO_CORREO')),
    -- Clave opaca del cliente (p. ej. hash de id_instalacion); no es el correo.
    clave_control VARCHAR(128) NOT NULL
        CHECK (length(btrim(clave_control)) BETWEEN 8 AND 128 AND clave_control = btrim(clave_control)),
    destinatario_normalizado VARCHAR(254)
        CHECK (
            destinatario_normalizado IS NULL
                OR (
                    length(btrim(destinatario_normalizado)) BETWEEN 5 AND 254
                        AND destinatario_normalizado = lower(btrim(destinatario_normalizado))
                )
        ),
    envios_en_ventana INTEGER NOT NULL DEFAULT 0 CHECK (envios_en_ventana >= 0),
    fallos_en_ventana INTEGER NOT NULL DEFAULT 0 CHECK (fallos_en_ventana >= 0),
    ventana_inicio_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    bloqueo_hasta_utc TIMESTAMPTZ,
    ultimo_envio_utc TIMESTAMPTZ,
    creado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actualizado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_control_verificacion_proposito_clave UNIQUE (proposito, clave_control)
);

CREATE INDEX ix_control_verificacion_destinatario
    ON mycar.control_verificacion (proposito, destinatario_normalizado)
    WHERE destinatario_normalizado IS NOT NULL;

CREATE INDEX ix_control_verificacion_bloqueo
    ON mycar.control_verificacion (bloqueo_hasta_utc)
    WHERE bloqueo_hasta_utc IS NOT NULL;

-- Proceso pre-usuario: agrupa emisiones; no guarda formulario ni contraseña.
CREATE TABLE mycar.operacion_verificacion (
    id_operacion UUID PRIMARY KEY,
    proposito VARCHAR(32) NOT NULL
        CHECK (proposito IN ('REGISTRO', 'RECUPERACION', 'REACTIVACION', 'CAMBIO_CORREO')),
    id_control BIGINT NOT NULL
        REFERENCES mycar.control_verificacion (id_control) ON DELETE RESTRICT,
    destinatario_normalizado VARCHAR(254) NOT NULL
        CHECK (
            length(btrim(destinatario_normalizado)) BETWEEN 5 AND 254
                AND destinatario_normalizado = lower(btrim(destinatario_normalizado))
        ),
    estado VARCHAR(24) NOT NULL DEFAULT 'ABIERTA'
        CHECK (estado IN ('ABIERTA', 'CODIGO_VALIDADO', 'COMPLETADA', 'EXPIRADA', 'CANCELADA')),
    -- Hash del token de autorización tras validar el código (completar registro).
    autorizacion_hash CHAR(64)
        CHECK (autorizacion_hash IS NULL OR autorizacion_hash ~ '^[a-f0-9]{64}$'),
    expira_utc TIMESTAMPTZ NOT NULL,
    creada_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    cerrada_utc TIMESTAMPTZ,
    id_usuario_resultado BIGINT
        REFERENCES mycar.usuario (id_usuario) ON DELETE RESTRICT,
    CONSTRAINT ck_operacion_verificacion_cierre CHECK (
        (estado IN ('ABIERTA', 'CODIGO_VALIDADO') AND cerrada_utc IS NULL)
            OR (estado IN ('COMPLETADA', 'EXPIRADA', 'CANCELADA') AND cerrada_utc IS NOT NULL)
    ),
    CONSTRAINT ck_operacion_verificacion_autorizacion CHECK (
        (estado IN ('ABIERTA', 'EXPIRADA', 'CANCELADA') AND autorizacion_hash IS NULL)
            OR (
                estado IN ('CODIGO_VALIDADO', 'COMPLETADA')
                    AND autorizacion_hash IS NOT NULL
            )
    ),
    CONSTRAINT ck_operacion_verificacion_usuario CHECK (
        (estado = 'COMPLETADA' AND id_usuario_resultado IS NOT NULL)
            OR (estado <> 'COMPLETADA' AND id_usuario_resultado IS NULL)
    )
);

CREATE INDEX ix_operacion_verificacion_control
    ON mycar.operacion_verificacion (id_control, creada_utc DESC);

CREATE INDEX ix_operacion_verificacion_destinatario_estado
    ON mycar.operacion_verificacion (proposito, destinatario_normalizado, estado);

CREATE INDEX ix_operacion_verificacion_expira
    ON mycar.operacion_verificacion (expira_utc)
    WHERE estado IN ('ABIERTA', 'CODIGO_VALIDADO');

-- Cada código emitido (hash); distingue emisión nueva vs reintento técnico de envío.
CREATE TABLE mycar.emision_codigo (
    id_emision BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_operacion UUID NOT NULL
        REFERENCES mycar.operacion_verificacion (id_operacion) ON DELETE RESTRICT,
    codigo_hash CHAR(64) NOT NULL
        CHECK (codigo_hash ~ '^[a-f0-9]{64}$'),
    destinatario_normalizado VARCHAR(254) NOT NULL
        CHECK (
            length(btrim(destinatario_normalizado)) BETWEEN 5 AND 254
                AND destinatario_normalizado = lower(btrim(destinatario_normalizado))
        ),
    expira_utc TIMESTAMPTZ NOT NULL,
    estado_envio VARCHAR(24) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado_envio IN ('PENDIENTE', 'ENVIADO', 'FALLIDO', 'CANCELADO')),
    reintento_tecnico BOOLEAN NOT NULL DEFAULT FALSE,
    creada_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    enviada_utc TIMESTAMPTZ,
    consumida_utc TIMESTAMPTZ,
    CONSTRAINT ck_emision_enviada CHECK (
        (estado_envio = 'ENVIADO' AND enviada_utc IS NOT NULL)
            OR (estado_envio <> 'ENVIADO')
    ),
    CONSTRAINT ck_emision_consumida_envio CHECK (
        consumida_utc IS NULL OR estado_envio = 'ENVIADO'
    )
);

CREATE INDEX ix_emision_codigo_operacion
    ON mycar.emision_codigo (id_operacion, creada_utc DESC);

CREATE INDEX ix_emision_codigo_vigente
    ON mycar.emision_codigo (id_operacion, expira_utc)
    WHERE consumida_utc IS NULL AND estado_envio IN ('PENDIENTE', 'ENVIADO');

-- Resultados idempotentes de solicitudes del cliente (reintento tras pérdida de respuesta).
CREATE TABLE mycar.intento_verificacion (
    id_intento UUID PRIMARY KEY,
    id_operacion UUID NOT NULL
        REFERENCES mycar.operacion_verificacion (id_operacion) ON DELETE RESTRICT,
    id_emision BIGINT
        REFERENCES mycar.emision_codigo (id_emision) ON DELETE RESTRICT,
    tipo VARCHAR(32) NOT NULL
        CHECK (tipo IN ('SOLICITAR_CODIGO', 'CONFIRMAR_CODIGO', 'COMPLETAR_REGISTRO')),
    resultado VARCHAR(24) NOT NULL DEFAULT 'EN_PROCESO'
        CHECK (resultado IN ('EN_PROCESO', 'ACEPTADO', 'RECHAZADO')),
    respuesta_json JSONB,
    creado_utc TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_intento_verificacion_respuesta CHECK (
        resultado = 'EN_PROCESO'
            OR respuesta_json IS NOT NULL
    )
);

CREATE INDEX ix_intento_verificacion_operacion
    ON mycar.intento_verificacion (id_operacion, creado_utc DESC);

CREATE INDEX ix_intento_verificacion_emision
    ON mycar.intento_verificacion (id_emision)
    WHERE id_emision IS NOT NULL;
