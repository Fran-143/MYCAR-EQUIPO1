package backend.historial.service;

import backend.historial.dao.HistorialMantenimientoDao;
import backend.historial.model.AnulacionBloqueadaPorAlerta;
import backend.historial.model.DatosHistorial.*;
import backend.historial.model.MantenimientoNoDisponible;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Rf21AnularMantenimientoTest {
    HistorialMantenimientoDao dao;
    HistorialMantenimientoService servicio;

    @BeforeEach void preparar() {
        dao = mock(HistorialMantenimientoDao.class);
        servicio = new HistorialMantenimientoService(dao);
        when(dao.vehiculoPropio(7, 3)).thenReturn(Optional.of(
                new Vehiculo(3, "ABC123", "Toyota", "Yaris", "ACTIVO", true)));
    }

    @Test void bloqueaSiHayAlertaActiva() {
        when(dao.detallePropio(7, 3, 10)).thenReturn(Optional.of(detalle("REALIZADO", true)));
        when(dao.anularSiSinAlertaActiva(7, 3, 10)).thenThrow(new AnulacionBloqueadaPorAlerta(55));
        var ex = assertThrows(AnulacionBloqueadaPorAlerta.class, () -> servicio.anular(7, 3, 10));
        assertEquals(55, ex.idAlerta());
        verify(dao).anularSiSinAlertaActiva(7, 3, 10);
    }

    @Test void anulaCuandoNoHayDependenciaActiva() {
        when(dao.detallePropio(7, 3, 10)).thenReturn(Optional.of(detalle("REALIZADO", false)));
        when(dao.anularSiSinAlertaActiva(7, 3, 10)).thenReturn(true);
        var r = servicio.anular(7, 3, 10);
        assertEquals("ANULADO", r.estado());
        assertEquals(10, r.idMantenimiento());
    }

    @Test void noAnulaYaAnulado() {
        when(dao.detallePropio(7, 3, 10)).thenReturn(Optional.of(detalle("ANULADO", false)));
        assertThrows(MantenimientoNoDisponible.class, () -> servicio.anular(7, 3, 10));
        verify(dao, never()).anularSiSinAlertaActiva(anyLong(), anyLong(), anyLong());
    }

    private DetalleMantenimiento detalle(String estado, boolean bloqueado) {
        return new DetalleMantenimiento(10, 3, "CAMBIO_ACEITE", "Cambio de aceite", null,
                LocalDate.of(2026, 1, 10), 20000, estado, BigDecimal.TEN, null, null,
                "REALIZADO".equals(estado), "REALIZADO".equals(estado) && !bloqueado, bloqueado, List.of());
    }
}
