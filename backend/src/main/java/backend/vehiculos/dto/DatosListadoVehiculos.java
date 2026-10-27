package backend.vehiculos.dto;

import java.util.List;

/** Contrato técnico RF09: contadores del propietario, independientes de búsqueda y filtro. */
public record DatosListadoVehiculos(
        List<VehiculoResumen> vehiculos, ResumenEstados resumen, String filtro, String busqueda
) {
    public record ResumenEstados(long todos, long activos, long inactivos) {}
    public record VehiculoResumen(
            long idVehiculo, String placa, String marca, String modelo, int anioFabricacion,
            String usoVehiculo, String color, String tipoMotor, String combustible,
            String estadoVehiculo, boolean principal, Integer kilometrajeActual
    ) {}
}
