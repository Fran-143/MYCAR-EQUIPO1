package backend.notificaciones.api.dto;

/** Contrato HTTP RF48 — preferencias de envío externo (sin push real en esta fase). */
public final class ContratoPreferenciasNotificacion {
    private ContratoPreferenciasNotificacion() { }

    public record PreferenciasResponse(
            boolean mantenimiento,
            boolean soat,
            boolean kilometraje,
            String permisoDispositivo
    ) { }

    public record PreferenciasRequest(
            Boolean mantenimiento,
            Boolean soat,
            Boolean kilometraje,
            String permisoDispositivo
    ) { }

    public record ErrorResponse(String codigo, String mensaje, boolean resultadoIncierto) { }
}
