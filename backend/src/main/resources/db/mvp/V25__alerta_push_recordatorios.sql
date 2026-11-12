-- V25: control de frecuencia push (recordatorios híbridos) + ciclos diarios en outbox.
-- Seguro sobre datos existentes: no borra filas PENDIENTE/ERROR/ENVIADO; solo añade columna e índice.
-- No altera fórmulas RF23 ni bandeja RF46. Extensible a categoría KILOMETRAJE (sin UI aún).

-- Control por alerta: última fecha Lima CONFIRMAADA como enviada / ciclo / silencio futuro.
CREATE TABLE IF NOT EXISTS mycar.alerta_push_control (
    id_alerta                 BIGINT PRIMARY KEY
        REFERENCES mycar.alerta(id_alerta) ON DELETE CASCADE,
    estado_ciclo              VARCHAR(16)
        CHECK (estado_ciclo IS NULL OR estado_ciclo IN ('PROXIMA', 'PENDIENTE', 'VENCIDA')),
    fecha_ultimo_envio_lima   DATE,
    motivo_ultimo             VARCHAR(32)
        CHECK (motivo_ultimo IS NULL OR motivo_ultimo IN (
            'ENTRADA_ESTADO', 'RECORDATORIO', 'INMEDIATO_KM', 'RESUMEN_VENCIDAS')),
    -- Reserva para silenciar/posponer una alerta concreta (sin pantallas todavía).
    silenciado_hasta_lima     DATE,
    actualizado_utc           TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS ix_alerta_push_control_envio
    ON mycar.alerta_push_control (fecha_ultimo_envio_lima);

-- Relación resumen → alertas originales (id_alerta NULL en outbox_push del resumen).
CREATE TABLE IF NOT EXISTS mycar.outbox_push_alerta (
    id_outbox   BIGINT NOT NULL
        REFERENCES mycar.outbox_push(id_outbox) ON DELETE CASCADE,
    id_alerta   BIGINT NOT NULL
        REFERENCES mycar.alerta(id_alerta) ON DELETE CASCADE,
    PRIMARY KEY (id_outbox, id_alerta)
);

CREATE INDEX IF NOT EXISTS ix_outbox_push_alerta_alerta
    ON mycar.outbox_push_alerta (id_alerta);

-- Permitir un outbox por ciclo/día (antes UNIQUE bloqueaba recordatorios VENCIDA diarios).
ALTER TABLE mycar.outbox_push
    ADD COLUMN IF NOT EXISTS clave_idempotencia VARCHAR(96);

-- Filas legacy (PENDIENTE/ERROR/ENVIADO/OMITIDO) conservan estado; clave estable única.
UPDATE mycar.outbox_push
SET clave_idempotencia = 'LEGACY:' || id_outbox::TEXT
WHERE clave_idempotencia IS NULL;

ALTER TABLE mycar.outbox_push
    ALTER COLUMN clave_idempotencia SET NOT NULL;

ALTER TABLE mycar.outbox_push
    DROP CONSTRAINT IF EXISTS uq_outbox_push_idempotencia;

CREATE UNIQUE INDEX IF NOT EXISTS uq_outbox_push_clave_idempotencia
    ON mycar.outbox_push (clave_idempotencia);

COMMENT ON TABLE mycar.alerta_push_control IS
    'Frecuencia push mantenimiento: PROXIMA 1x; PENDIENTE cada 3d; VENCIDA diario. fecha_ultimo solo tras ENVIADO.';
COMMENT ON TABLE mycar.outbox_push_alerta IS
    'Alertas incluidas en un outbox resumen (categoria MANTENIMIENTO agrupado).';
COMMENT ON COLUMN mycar.outbox_push.clave_idempotencia IS
    'Idempotencia de ciclo: MANT:{alerta}:{YYYY-MM-DD}:{motivo} o MANT:USR:{usuario}:VENCIDAS:{fecha}. LEGACY:{id} para filas pre-V25.';
