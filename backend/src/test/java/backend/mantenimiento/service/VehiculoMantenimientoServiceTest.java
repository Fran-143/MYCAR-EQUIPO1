package backend.mantenimiento.service;

import backend.mantenimiento.dao.VehiculoMantenimientoDao;
import backend.mantenimiento.model.VehiculoMantenimiento;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VehiculoMantenimientoServiceTest {

    @Test
    void RF17_CP01_EF_aceptaVehiculoPropioActivo() {
        var vehiculo = new VehiculoMantenimiento(
                10L, 1L, "ACTIVO"
        );

        var servicio = servicioConVehiculo(vehiculo);

        var resultado = servicio.obtenerVehiculoPropioActivo(
                1L, 10L
        );

        assertEquals(vehiculo, resultado);
    }

    @Test
    void RF17_CP06_EA05_rechazaVehiculoInexistente() {
        VehiculoMantenimientoDao dao = mock(VehiculoMantenimientoDao.class);

        var servicio = new VehiculoMantenimientoService(dao);

        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.obtenerVehiculoPropioActivo(1L, 10L)
        );
        verify(dao).buscarPorId(10L);
    }

    @Test
    void RF17_CP06_EA05_rechazaVehiculoAjeno() {
        // El vehículo pertenece al usuario 2; solicita el usuario 1.
        var vehiculo = new VehiculoMantenimiento(
                10L, 2L, "ACTIVO"
        );

        var servicio = servicioConVehiculo(vehiculo);

        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.obtenerVehiculoPropioActivo(1L, 10L)
        );
    }

    @Test
    void RF17_CP06_EA05_rechazaVehiculoPropioInactivo() {
        var vehiculo = new VehiculoMantenimiento(
                10L, 1L, "INACTIVO"
        );

        var servicio = servicioConVehiculo(vehiculo);

        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.obtenerVehiculoPropioActivo(1L, 10L)
        );
    }

    @Test
    void RF17_CP02_EA01_registroConsultaVehiculoBloqueado() {
        var vehiculo = new VehiculoMantenimiento(10L, 1L, "ACTIVO");
        VehiculoMantenimientoDao dao = mock(VehiculoMantenimientoDao.class);
        when(dao.buscarPorIdParaRegistro(10L)).thenReturn(Optional.of(vehiculo));

        assertEquals(vehiculo, new VehiculoMantenimientoService(dao)
                .obtenerVehiculoPropioActivoParaRegistro(1L, 10L));
        verify(dao).buscarPorIdParaRegistro(10L);
    }

    @Test
    void RF17_CP06_EA05_bloqueoNoOmitePropiedadYEstadoActivo() {
        VehiculoMantenimientoDao dao = mock(VehiculoMantenimientoDao.class);
        var servicio = new VehiculoMantenimientoService(dao);
        when(dao.buscarPorIdParaRegistro(10L))
                .thenReturn(Optional.of(new VehiculoMantenimiento(10L, 2L, "ACTIVO")))
                .thenReturn(Optional.of(new VehiculoMantenimiento(10L, 1L, "INACTIVO")));

        assertThrows(IllegalArgumentException.class,
                () -> servicio.obtenerVehiculoPropioActivoParaRegistro(1L, 10L));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.obtenerVehiculoPropioActivoParaRegistro(1L, 10L));
    }

    private VehiculoMantenimientoService servicioConVehiculo(
            VehiculoMantenimiento vehiculo
    ) {
        VehiculoMantenimientoDao dao = mock(VehiculoMantenimientoDao.class);
        when(dao.buscarPorId(vehiculo.idVehiculo()))
                .thenReturn(Optional.of(vehiculo));

        return new VehiculoMantenimientoService(dao);
    }
}
