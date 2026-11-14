package backend.notificaciones.api.dto;

import java.util.List;

/** Contrato HTTP RF46/RF47 — bandeja interna (sin push). */
public final class ContratoNotificaciones {
    private ContratoNotificaciones() { }

    public record NotificacionDto(
            long idNotificacion,
            String categoria,
            String estadoGestion,
            boolean leida,
            String titulo,
            String cuerpo,
            String destino,
            Long idAlerta,
            Long idVehiculo,
            String fechaEmisionUtc,
            /** Estado RF23 de la alerta vinculada; null si no hay id_alerta. Distinto de estadoGestion. */
            String estadoAlerta
    ) {
        public NotificacionDto(
                long idNotificacion,
                String categoria,
                String estadoGestion,
                boolean leida,
                String titulo,
                String cuerpo,
                String destino,
                Long idAlerta,
                Long idVehiculo,
                String fechaEmisionUtc) {
            this(idNotificacion, categoria, estadoGestion, leida, titulo, cuerpo, destino,
                    idAlerta, idVehiculo, fechaEmisionUtc, null);
        }
    }

    public record BandejaResponse(
            String pestana,
            String categoria,
            String filtroLectura,
            int contadorPendientesGlobal,
            int contadorNoLeidasGlobal,
            List<NotificacionDto> notificaciones
    ) { }

    public record LeerResponse(
            long idNotificacion,
            boolean leida,
            String estadoGestion,
            String destino,
            Long idAlerta,
            Long idVehiculo
    ) { }

    public record ErrorResponse(String codigo, String mensaje, boolean resultadoIncierto) { }
}
