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
            String estadoAlerta,
            /** Placa del vehículo vinculado; null si no aplica (p. ej. licencia). */
            String placa,
            /** Marca + modelo para listado; null si no hay vehículo. */
            String vehiculoEtiqueta
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
                    idAlerta, idVehiculo, fechaEmisionUtc, null, null, null);
        }

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
                String fechaEmisionUtc,
                String estadoAlerta) {
            this(idNotificacion, categoria, estadoGestion, leida, titulo, cuerpo, destino,
                    idAlerta, idVehiculo, fechaEmisionUtc, estadoAlerta, null, null);
        }
    }

    /**
     * Detalle enriquecido para bandeja → ficha.
     * Cálculos de días/km en servidor (America/Lima); Android solo presenta.
     */
    public record DetalleNotificacionDto(
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
            String nombreEvento,
            String vehiculoEtiqueta,
            String estadoActual,
            String fechaObjetivo,
            Integer diasRestantes,
            Integer diasTranscurridos,
            Integer kmRestantes,
            Integer kmExcedidos,
            Integer kmObjetivo,
            String frecuenciaKm,
            String ultimaActualizacionKm,
            String recordatorioCorrespondienteKm,
            String textoTemporal,
            String textoKilometraje,
            String accionDisponible,
            boolean accionHabilitada
    ) {}

    public record BandejaResponse(
            String pestana,
            String categoria,
            String filtroLectura,
            /** Notificaciones con estado_gestion = PENDIENTE (atención), no confundir con lectura. */
            int contadorPendientesGlobal,
            /** Notificaciones con leida = false (global del titular). */
            int contadorNoLeidasGlobal,
            /** Notificaciones con leida = true (global del titular). */
            int contadorLeidasGlobal,
            List<NotificacionDto> notificaciones
    ) {
        public BandejaResponse(
                String pestana,
                String categoria,
                String filtroLectura,
                int contadorPendientesGlobal,
                int contadorNoLeidasGlobal,
                List<NotificacionDto> notificaciones) {
            this(pestana, categoria, filtroLectura, contadorPendientesGlobal, contadorNoLeidasGlobal,
                    0, notificaciones);
        }
    }

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
