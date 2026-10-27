package backend.mantenimiento.model;

import java.util.UUID;

/** Estado de una operación RF17; la consulta exige usuario y tipo autorizados. */
public record EstadoOperacionRegistro(
        UUID idOperacion, String estado, ResultadoRegistroMantenimiento resultado
) { }
