package backend.vehiculos.dto;

import java.util.List;

public record DatosCatalogosVehiculo(
        List<UsoVehiculo> usos,
        List<TipoMotor> tiposMotor,
        List<Combustible> combustibles,
        List<TipoAceite> tiposAceite
) {

    public record UsoVehiculo(
            String codigo,
            String nombre
    ) {
    }

    public record TipoMotor(
            Long idTipoMotor,
            String codigo,
            String nombre,
            boolean esElectrico
    ) {
    }

    public record Combustible(
            Long idCombustible,
            String codigo,
            String nombre
    ) {
    }

    public record TipoAceite(
            Long idTipoAceite,
            String codigo,
            String nombre
    ) {
    }
}