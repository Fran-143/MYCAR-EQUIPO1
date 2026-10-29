package backend.historial.service;

import backend.historial.dao.HistorialMantenimientoDao;
import backend.historial.model.DatosHistorial.*;
import backend.historial.model.HistorialInconsistente;
import backend.historial.model.HistorialNoDisponible;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistorialMantenimientoServiceTest {
    private HistorialMantenimientoDao dao;
    private HistorialMantenimientoService servicio;
    private final Vehiculo activo = new Vehiculo(10, "ABC123", "Toyota", "Corolla", "ACTIVO", true);
    private final Vehiculo inactivo = new Vehiculo(11, "DEF456", "Nissan", "Versa", "INACTIVO", false);

    @BeforeEach void preparar() {
        dao = mock(HistorialMantenimientoDao.class);
        servicio = new HistorialMantenimientoService(dao);
        when(dao.vehiculoPropio(1, 10)).thenReturn(Optional.of(activo));
    }

    @Test void CP01_preservaKmHistoricosCostoNullCeroYAnulados() {
        var ultimo = registro(20, "2026-10-10", 49000, "ANULADO", BigDecimal.ZERO);
        var anterior = registro(19, "2026-09-20", 47000, "REALIZADO", null);
        when(dao.mantenimientosPropios(1, 10)).thenReturn(List.of(ultimo, anterior));
        var resultado = servicio.consultar(1, 10);
        assertEquals(2, resultado.total());
        assertEquals(List.of(ultimo, anterior), resultado.mantenimientos());
        assertEquals(49000, resultado.mantenimientos().getFirst().kmServicio());
        assertEquals(BigDecimal.ZERO, resultado.mantenimientos().getFirst().costoTotal());
        assertNull(resultado.mantenimientos().getLast().costoTotal());
    }

    @Test void CP02_historialVacioEsUnResultadoValido() {
        var r = servicio.consultar(1, 10);
        assertEquals(0, r.total()); assertTrue(r.mantenimientos().isEmpty());
        assertEquals(activo, r.vehiculo());
    }

    @Test void CP03_sinVehiculosNoConsultaMantenimientos() {
        assertTrue(servicio.contexto(1).vehiculos().isEmpty());
        verify(dao, never()).mantenimientosPropios(anyLong(), anyLong());
    }

    @Test void CP04_inactivosYAusenciaDePrincipalSeConservan() {
        var sinPrincipal = new Vehiculo(10, "ABC123", "Toyota", "Corolla", "ACTIVO", false);
        when(dao.vehiculosPropios(1)).thenReturn(List.of(sinPrincipal, inactivo));
        when(dao.vehiculoPropio(1, 11)).thenReturn(Optional.of(inactivo));
        assertEquals(List.of(sinPrincipal, inactivo), servicio.contexto(1).vehiculos());
        assertEquals(inactivo, servicio.consultar(1, 11).vehiculo());
    }

    @Test void CP05_ajenoOInexistenteNoConsultaRegistros() {
        assertThrows(HistorialNoDisponible.class, () -> servicio.consultar(1, 99));
        verify(dao, never()).mantenimientosPropios(anyLong(), anyLong());
    }

    @Test void CP06_errorTecnicoNoSeConvierteEnVacio() {
        when(dao.mantenimientosPropios(1, 10)).thenThrow(new IllegalStateException("Fallo controlado"));
        assertThrows(IllegalStateException.class, () -> servicio.consultar(1, 10));
    }

    @Test void exigeUsuarioValidoAntesDeConsultar() {
        assertThrows(IllegalArgumentException.class, () -> servicio.contexto(0));
        assertThrows(IllegalArgumentException.class, () -> servicio.consultar(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> servicio.consultar(1, 0));
        verifyNoInteractions(dao);
    }

    @Test void rechazaRegistrosSinKmHistoricoEnVezDeInventarlo() {
        when(dao.mantenimientosPropios(1, 10)).thenReturn(List.of(
                registro(20, "2026-10-10", null, "REALIZADO", null)));
        assertThrows(HistorialInconsistente.class, () -> servicio.consultar(1, 10));
    }

    @Test void otroConservaServicioEspecificoYRechazaAusencia() {
        var otro = new Mantenimiento(20, "OTRO", "Otro", "Alineación", LocalDate.of(2026, 10, 10),
                49000, "REALIZADO", null);
        when(dao.mantenimientosPropios(1, 10)).thenReturn(List.of(otro));
        assertEquals("Alineación", servicio.consultar(1, 10).mantenimientos().getFirst().servicioOtro());
        when(dao.mantenimientosPropios(1, 10)).thenReturn(List.of(new Mantenimiento(20, "OTRO", "Otro",
                null, otro.fechaServicio(), 49000, "REALIZADO", null)));
        assertThrows(HistorialInconsistente.class, () -> servicio.consultar(1, 10));
    }

    @Test void ordenPorIdDescendenteDesempataMismaFecha() {
        when(dao.mantenimientosPropios(1, 10)).thenReturn(List.of(
                registro(21, "2026-10-10", 49000, "REALIZADO", null),
                registro(20, "2026-10-10", 49000, "ANULADO", null)));
        assertEquals(21, servicio.consultar(1, 10).mantenimientos().getFirst().idMantenimiento());
        when(dao.mantenimientosPropios(1, 10)).thenReturn(List.of(
                registro(20, "2026-10-10", 49000, "REALIZADO", null),
                registro(21, "2026-10-10", 49000, "REALIZADO", null)));
        assertThrows(HistorialInconsistente.class, () -> servicio.consultar(1, 10));
    }

    @Test void rechazaVehiculosDuplicadosOMultiplesPrincipales() {
        when(dao.vehiculosPropios(1)).thenReturn(List.of(activo, activo));
        assertThrows(HistorialInconsistente.class, () -> servicio.contexto(1));
        when(dao.vehiculosPropios(1)).thenReturn(List.of(activo,
                new Vehiculo(11, "DEF456", "Nissan", "Versa", "ACTIVO", true)));
        assertThrows(HistorialInconsistente.class, () -> servicio.contexto(1));
    }

    private Mantenimiento registro(long id, String fecha, Integer km, String estado, BigDecimal costo) {
        return new Mantenimiento(id, "CAMBIO_ACEITE", "Cambio de aceite", null,
                LocalDate.parse(fecha), km, estado, costo);
    }
}
