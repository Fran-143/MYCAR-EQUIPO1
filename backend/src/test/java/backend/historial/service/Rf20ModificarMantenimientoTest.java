package backend.historial.service;

import backend.historial.dao.HistorialMantenimientoDao;
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

class Rf20ModificarMantenimientoTest {
    HistorialMantenimientoDao dao;
    HistorialMantenimientoService servicio;

    @BeforeEach void preparar() {
        dao = mock(HistorialMantenimientoDao.class);
        servicio = new HistorialMantenimientoService(dao, org.mockito.Mockito.mock(backend.talleres.service.TallerService.class));
    }

    @Test void rechazaVehiculoInactivo() {
        when(dao.vehiculoPropio(7, 3)).thenReturn(Optional.of(
                new Vehiculo(3, "ABC123", "Toyota", "Yaris", "INACTIVO", true)));
        assertThrows(MantenimientoNoDisponible.class, () ->
                servicio.modificar(7, 3, 10, new ModificarMantenimientoRequest(BigDecimal.ONE, "ok", null)));
        verify(dao, never()).modificarCamposAutorizados(anyLong(), anyLong(), anyLong(), any(), any(), any(), any());
    }

    @Test void guardaSoloCamposAutorizadosConActivo() {
        when(dao.vehiculoPropio(7, 3)).thenReturn(Optional.of(
                new Vehiculo(3, "ABC123", "Toyota", "Yaris", "ACTIVO", true)));
        var detalle = new DetalleMantenimiento(10, 3, "ABC123", "Toyota", "Yaris", "ACTIVO",
                "CAMBIO_ACEITE", "Cambio de aceite", null, LocalDate.of(2026, 1, 10), 20000,
                "REALIZADO", BigDecimal.TEN, "antes", "Taller A", null, null, null, null, true, true, false, List.of());
        when(dao.detallePropio(7, 3, 10)).thenReturn(Optional.of(detalle));
        when(dao.modificarCamposAutorizados(eq(7L), eq(3L), eq(10L), eq(BigDecimal.valueOf(50)), eq("nuevo"), isNull(), isNull()))
                .thenReturn(true);
        var r = servicio.modificar(7, 3, 10,
                new ModificarMantenimientoRequest(BigDecimal.valueOf(50), "nuevo", (Long) null, true));
        assertNotNull(r);
        verify(dao).modificarCamposAutorizados(7, 3, 10, BigDecimal.valueOf(50), "nuevo", null, null);
    }

    @Test void rechazaCostoNegativo() {
        assertThrows(IllegalArgumentException.class, () ->
                servicio.modificar(7, 3, 10,
                        new ModificarMantenimientoRequest(BigDecimal.valueOf(-1), null, null)));
    }

    @Test void normalizaSinTallerANullYConservaSiNullEnRequest() {
        when(dao.vehiculoPropio(7, 3)).thenReturn(Optional.of(
                new Vehiculo(3, "ABC123", "Toyota", "Yaris", "ACTIVO", true)));
        var detalle = new DetalleMantenimiento(10, 3, "ABC123", "Toyota", "Yaris", "ACTIVO",
                "CAMBIO_ACEITE", "Cambio de aceite", null, LocalDate.of(2026, 1, 10), 20000,
                "REALIZADO", BigDecimal.TEN, "obs", "Taller A", null, null, null, null, true, true, false, List.of());
        when(dao.detallePropio(7, 3, 10)).thenReturn(Optional.of(detalle));
        when(dao.modificarCamposAutorizados(eq(7L), eq(3L), eq(10L), eq(BigDecimal.TEN), eq("obs"), isNull(), isNull()))
                .thenReturn(true);
        servicio.modificar(7, 3, 10, new ModificarMantenimientoRequest(null, null, (Long) null, true));
        verify(dao).modificarCamposAutorizados(7, 3, 10, BigDecimal.TEN, "obs", null, null);

        clearInvocations(dao);
        when(dao.vehiculoPropio(7, 3)).thenReturn(Optional.of(
                new Vehiculo(3, "ABC123", "Toyota", "Yaris", "ACTIVO", true)));
        when(dao.detallePropio(7, 3, 10)).thenReturn(Optional.of(detalle));
        when(dao.modificarCamposAutorizados(eq(7L), eq(3L), eq(10L), eq(BigDecimal.TEN), eq("obs"), isNull(), eq("Taller A")))
                .thenReturn(true);
        // sinTaller/idTaller omitidos ⇒ conservar asociación actual
        servicio.modificar(7, 3, 10, new ModificarMantenimientoRequest(null, null, null, null));
        verify(dao).modificarCamposAutorizados(7, 3, 10, BigDecimal.TEN, "obs", null, "Taller A");
    }

    @Test void rechazaSiNoEditable() {
        when(dao.vehiculoPropio(7, 3)).thenReturn(Optional.of(
                new Vehiculo(3, "ABC123", "Toyota", "Yaris", "ACTIVO", true)));
        var detalle = new DetalleMantenimiento(10, 3, "ABC123", "Toyota", "Yaris", "ACTIVO",
                "CAMBIO_ACEITE", "Cambio de aceite", null, LocalDate.of(2026, 1, 10), 20000,
                "ANULADO", BigDecimal.TEN, null, null, null, null, null, null, false, false, false, List.of());
        when(dao.detallePropio(7, 3, 10)).thenReturn(Optional.of(detalle));
        assertThrows(MantenimientoNoDisponible.class, () ->
                servicio.modificar(7, 3, 10, new ModificarMantenimientoRequest(BigDecimal.ONE, "x", null, null)));
        verify(dao, never()).modificarCamposAutorizados(anyLong(), anyLong(), anyLong(), any(), any(), any(), any());
    }

    @Test void seleccionaTallerActivoConSnapshotNombre() {
        var talleres = mock(backend.talleres.service.TallerService.class);
        servicio = new HistorialMantenimientoService(dao, talleres);
        when(dao.vehiculoPropio(7, 3)).thenReturn(Optional.of(
                new Vehiculo(3, "ABC123", "Toyota", "Yaris", "ACTIVO", true)));
        var detalle = new DetalleMantenimiento(10, 3, "ABC123", "Toyota", "Yaris", "ACTIVO",
                "CAMBIO_ACEITE", "Cambio de aceite", null, LocalDate.of(2026, 1, 10), 20000,
                "REALIZADO", BigDecimal.TEN, "obs", "Legacy", null, null, null, null, true, true, false, List.of());
        when(dao.detallePropio(7, 3, 10)).thenReturn(Optional.of(detalle));
        when(talleres.exigirActivoPropioSiPresente(7, 55L)).thenReturn(
                new backend.talleres.model.DatosTaller.Taller(
                        55, "Catálogo", null, null, null, null, "ACTIVO", false, 0, java.time.Instant.now()));
        when(dao.modificarCamposAutorizados(eq(7L), eq(3L), eq(10L), eq(BigDecimal.TEN), eq("obs"), eq(55L), eq("Catálogo")))
                .thenReturn(true);
        servicio.modificar(7, 3, 10, new ModificarMantenimientoRequest(null, null, 55L, null));
        verify(dao).modificarCamposAutorizados(7, 3, 10, BigDecimal.TEN, "obs", 55L, "Catálogo");
    }
}
