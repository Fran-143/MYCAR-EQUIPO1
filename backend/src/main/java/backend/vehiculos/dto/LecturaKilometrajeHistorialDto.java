package backend.vehiculos.dto;

import java.math.BigDecimal;

/** RF15: ítem de historial de lecturas (solo consulta). fechaLecturaUtc ISO-8601 como String para Android/Gson. */
public record LecturaKilometrajeHistorialDto(
        long idLectura,
        BigDecimal valorKm,
        String origen,
        String fechaLecturaUtc,
        /** Si hubo RF16: valor previo a la última corrección. */
        BigDecimal kmAnteriorCorreccion,
        String motivoCorreccion,
        String fechaRectificacionUtc
) {
}
