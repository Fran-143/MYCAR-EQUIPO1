package backend.mantenimiento.model;

import java.util.UUID;

/** Contrato propuesto de consulta; no representa un motor idempotente implementado. */
public record EstadoOperacionRegistro(
        UUID idOperacion, String estado, ResultadoRegistroMantenimiento resultado
) { }
