package backend.vehiculos.dto;

import java.math.BigDecimal;

/** RF16: contexto de elegibilidad y límites para corregir la última lectura. */
public record ContextoCorreccionKilometraje(
        long idVehiculo,
        String placa,
        String marca,
        String modelo,
        long idLectura,
        BigDecimal kmPenultimo,
        BigDecimal kmUltimo,
        int versionKm,
        boolean elegible,
        String motivoNoElegible
) {
}
