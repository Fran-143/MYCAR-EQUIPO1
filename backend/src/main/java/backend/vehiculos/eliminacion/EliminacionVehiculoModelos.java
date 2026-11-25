package backend.vehiculos.eliminacion;

import java.util.UUID;

public final class EliminacionVehiculoModelos {
    private EliminacionVehiculoModelos() { }

    public record ResumenDependencias(
            long mantenimientos,
            long lecturasKm,
            long alertas,
            long soat,
            long tive,
            boolean tieneFoto
    ) { }

    public record PrepararEliminacionResponse(
            long idVehiculo,
            String placa,
            String marca,
            String modelo,
            String estadoVehiculo,
            boolean principal,
            int versionRegistro,
            boolean quedaraSinPrincipal,
            Long idPrincipalCandidato,
            String placaPrincipalCandidato,
            String marcaPrincipalCandidato,
            String modeloPrincipalCandidato,
            ResumenDependencias dependencias,
            String mensajeConsecuenciaPrincipal
    ) { }

    public record ConfirmarEliminacionRequest(
            UUID idOperacion,
            int versionRegistro
    ) { }

    public record ConfirmarEliminacionResponse(
            long idVehiculo,
            String estadoRegistro,
            boolean principalResultanteExiste,
            Long idPrincipalResultante,
            String mensaje
    ) { }
}
