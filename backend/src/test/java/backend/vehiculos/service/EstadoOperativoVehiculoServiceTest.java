package backend.vehiculos.service;

import backend.vehiculos.dao.CondicionesRegistroVehiculoDao;
import backend.vehiculos.dao.VehiculoDao;
import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.dto.DatosDetalleVehiculo;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EstadoOperativoVehiculoServiceTest {
    CondicionesRegistroVehiculoDao condiciones;
    VehiculoDao vehiculos;
    ConsultaRegistroVehiculoService consultas;
    EstadoOperativoVehiculoService servicio;

    @BeforeEach
    void preparar() {
        condiciones = mock(CondicionesRegistroVehiculoDao.class);
        vehiculos = mock(VehiculoDao.class);
        consultas = mock(ConsultaRegistroVehiculoService.class);
        servicio = new EstadoOperativoVehiculoService(condiciones, vehiculos, consultas);
    }

    @Test
    void activaVehiculoInactivoCuandoHayCupo() {
        when(vehiculos.obtenerEstadoOperativoPropio(17, 5))
                .thenReturn(Optional.of(estado("INACTIVO", false)));
        when(vehiculos.contarActivos(17)).thenReturn(1L);
        when(vehiculos.limiteActivosPlan(17)).thenReturn(3);
        when(consultas.consultarDetalle(17, 5)).thenReturn(detalle("ACTIVO", false));

        var resultado = servicio.cambiarEstado(17, 5, "ACTIVO");

        assertEquals("ACTIVO", resultado.estadoVehiculo());
        verify(vehiculos).actualizarEstado(17, 5, "ACTIVO");
        verify(vehiculos, never()).transferirPrincipal(anyLong(), anyLong());
    }

    @Test
    void rechazaActivarSiSuperaLimiteDelPlan() {
        when(vehiculos.obtenerEstadoOperativoPropio(17, 5))
                .thenReturn(Optional.of(estado("INACTIVO", false)));
        when(vehiculos.contarActivos(17)).thenReturn(2L);
        when(vehiculos.limiteActivosPlan(17)).thenReturn(2);

        var ex = assertThrows(ErrorRegistroVehiculo.class, () -> servicio.cambiarEstado(17, 5, "ACTIVO"));
        assertEquals("LIMITE_ACTIVOS", ex.codigo());
        verify(vehiculos, never()).actualizarEstado(anyLong(), anyLong(), anyString());
    }

    @Test
    void establecePrincipalSoloSiEstaActivo() {
        when(vehiculos.obtenerEstadoOperativoPropio(17, 5))
                .thenReturn(Optional.of(estado("ACTIVO", false)));
        when(consultas.consultarDetalle(17, 5)).thenReturn(detalle("ACTIVO", true));

        var resultado = servicio.establecerPrincipal(17, 5);

        assertTrue(resultado.principal());
        verify(vehiculos).transferirPrincipal(17, 5);
    }

    private VehiculoDao.VehiculoEstadoOperativo estado(String estadoVehiculo, boolean principal) {
        return new VehiculoDao.VehiculoEstadoOperativo(
                5, estadoVehiculo, principal, "A1B234", "Toyota", "Corolla");
    }

    private DatosDetalleVehiculo detalle(String estado, boolean principal) {
        return new DatosDetalleVehiculo(
                5, "A1B234", "Toyota", "Corolla", 2020, "Particular", "Azul",
                "Combustión", "Gasolina", estado, principal, BigDecimal.valueOf(1000),
                "2020-01-01T00:00:00Z", null, null, null, null, false,
                "PARTICULAR", 1L, 1L, null, null, null, null, false);
    }
}
