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
            String estadoVehiculo, boolean principal, java.math.BigDecimal kilometrajeActual,
            boolean tieneFoto,
            /** true si existe fila en mycar.tive. */
            boolean tieneTive,
            /**
             * Cobertura SOAT ACTUAL según fechas (America/Lima) o alerta PROXIMA real:
             * null | POR_INICIAR | VIGENTE | PROXIMA | VENCIDA.
             */
            String estadoSoat
    ) {}
}
