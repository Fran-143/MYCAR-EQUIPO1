package backend.planes.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class ContratoPlanes {
    private ContratoPlanes() { }

    public record PlanDto(
            String codigo,
            String nombre,
            Integer maxVehiculosRegistrados,
            BigDecimal precioDemo,
            String moneda,
            String periodicidad,
            String descripcionBeneficios,
            boolean contratacionSandboxHabilitada,
            boolean listoParaContratar,
            String motivoNoContratable,
            boolean pruebaGratuitaDisponible,
            String motivoNoPruebaGratuita,
            String vigenciaPruebaConfigurada,
            String vigenciaSandboxConfigurada
    ) { }

    public record CatalogoResponse(
            List<PlanDto> planes,
            boolean sandboxLocalDisponible,
            boolean mercadoPagoTestDisponible,
            boolean pruebaGratuitaHabilitada,
            String proveedorPreferido
    ) { }

    public record MiSuscripcionResponse(
            String planCodigo,
            String planNombre,
            String modalidad,
            String estadoSuscripcionUi,
            String estado,
            String origen,
            String inicioUtc,
            String finUtc,
            Integer diasRestantes,
            Integer maxVehiculosRegistrados,
            long vehiculosRegistrados,
            long vehiculosActivos,
            boolean etiquetaSandbox,
            boolean puedeSolicitarPruebaGratuita,
            String motivoNoPruebaGratuita,
            boolean puedeContratarSandbox,
            String motivoNoSandbox,
            String referenciaOperacionUltima,
            String codigoSolicitudAbierta,
            /** Aviso visible al usuario (p. ej. Premium cancelado por admin). */
            String avisoUsuario
    ) { }

    public record EventoDto(
            long idEvento,
            String tipoEvento,
            String detalle,
            String creadoUtc,
            String referenciaOperacion
    ) { }

    public record SolicitudDto(
            String codigo,
            String modalidad,
            String estado,
            String creadoUtc,
            String decididoUtc,
            String motivoRechazo,
            String observacionAdmin,
            String referenciaOperacion,
            String proveedorOperacion,
            String estadoOperacion,
            BigDecimal importeOperacion,
            String monedaOperacion
    ) { }

    public record SolicitudDetalleDto(
            SolicitudDto solicitud,
            List<EventoDto> historial
    ) { }

    public record CrearPagoRequest(
            String idPlanCodigo,
            String proveedor,
            String idOperacion
    ) { }

    public record SimularPagoRequest(String escenario) { }

    public record OperacionPagoResponse(
            String referencia,
            String proveedor,
            String estado,
            String moneda,
            BigDecimal importe,
            String initPoint,
            String sandboxInitPoint,
            String creadoUtc,
            String verificadoUtc,
            String evidenciaVerificacion,
            boolean suscripcionActivada,
            String codigoSolicitud,
            String estadoSolicitud,
            String mensajeUsuario
    ) { }

    public record AdminResumenResponse(
            long freeActivas,
            long premiumPruebaActivas,
            long premiumSandboxActivas,
            long vencidas,
            long solicitudesPendientesAprobacion,
            long operacionesPendientes,
            long operacionesFinalizadas,
            String nota
    ) { }

    public record AdminSuscripcionItem(
            long idUsuario,
            String correo,
            String nombre,
            String planCodigo,
            String modalidad,
            String estado,
            String origen,
            String inicioUtc,
            String finUtc,
            boolean etiquetaSandbox,
            Integer diasRestantes
    ) { }

    public record AdminSolicitudItem(
            String codigo,
            long idUsuario,
            String correo,
            String nombre,
            String modalidad,
            String estado,
            String creadoUtc,
            String referenciaOperacion,
            String proveedorOperacion,
            String estadoOperacion
    ) { }

    public record AdminDetalleResponse(
            long idUsuario,
            String correo,
            String nombre,
            String estadoCuenta,
            MiSuscripcionResponse suscripcion,
            List<EventoDto> eventos,
            OperacionPagoResponse ultimaOperacion,
            Map<String, Object> vehiculos
    ) { }

    public record AdminSolicitudDetalleDto(
            SolicitudDto solicitud,
            long idUsuario,
            String correo,
            String nombre,
            String vigenciaQueSeConcederia,
            Integer cupoVehiculos,
            String descripcionBeneficios,
            boolean puedeAprobar,
            String motivoNoAprobar,
            List<EventoDto> historial,
            /** Solicitudes previas del mismo usuario (solo lectura en UI). */
            List<AdminSolicitudItem> solicitudesAnteriores
    ) { }

    public record DecisionRequest(String motivo, String observacion) { }

    public record DecisionResponse(
            String codigo,
            String estado,
            String mensaje,
            String finUtc,
            String modalidad
    ) { }

    public record VerificarResponse(
            String referencia,
            String proveedor,
            String estado,
            String mensaje,
            String evidenciaVerificacion
    ) { }
}
