package backend.mantenimiento.service;

import backend.mantenimiento.dao.TipoMantenimientoDao;
import backend.mantenimiento.model.TipoMantenimiento;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TipoMantenimientoServiceTest {

    @Test
    void RF17_CP01_EF_devuelveTipoActivo() {
        // ARRANGE
        var tipo = new TipoMantenimiento(
                1L, "CAMBIO_ACEITE", "Cambio de aceite", true
        );

        TipoMantenimientoDao dao = codigo -> {
            assertEquals("CAMBIO_ACEITE", codigo);
            return Optional.of(tipo);
        };

        var servicio = new TipoMantenimientoService(dao);

        // ACT
        var resultado = servicio.obtenerTipoActivo("CAMBIO_ACEITE");

        // ASSERT
        assertEquals(tipo, resultado);
    }

    @Test
    void RF17_CP06_EA05_rechazaTipoInexistente() {
        // ARRANGE
        TipoMantenimientoDao dao = codigo -> Optional.empty();
        var servicio = new TipoMantenimientoService(dao);

        // ACT + ASSERT
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.obtenerTipoActivo("NO_EXISTE")
        );
    }

    @Test
    void RF17_CP06_EA05_rechazaTipoInactivo() {
        // ARRANGE
        var tipo = new TipoMantenimiento(
                1L, "CAMBIO_ACEITE", "Cambio de aceite", false
        );

        TipoMantenimientoDao dao = codigo -> Optional.of(tipo);
        var servicio = new TipoMantenimientoService(dao);

        // ACT + ASSERT
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.obtenerTipoActivo("CAMBIO_ACEITE")
        );
    }
}