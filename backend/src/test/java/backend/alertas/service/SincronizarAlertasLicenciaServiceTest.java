package backend.alertas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.alertas.api.dto.ContratoRf22;
import backend.alertas.dao.AlertasJdbcDao;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SincronizarAlertasLicenciaServiceTest {

    AlertasJdbcDao alertas;
    SincronizarAlertasService service;

    @BeforeEach
    void preparar() {
        alertas = mock(AlertasJdbcDao.class);
        service = new SincronizarAlertasService(alertas);
    }

    @Test
    void listarLicenciaSincronizaYDevuelveTarjetas() {
        when(alertas.sincronizarLicencia(9L)).thenReturn(1);
        when(alertas.listarLicencia(9L)).thenReturn(List.of(
                new ContratoRf22.AlertaDto(
                        44L, "LICENCIA", "Licencia de conducir", "POR_TIEMPO", "DESDE_HOY",
                        "PROXIMA", LocalDate.of(2026, 12, 1), null, null, null, "A-I", 0)));

        var r = service.listarMisAlertasLicencia(9L);

        assertEquals(1, r.alertasSincronizadas());
        assertEquals(1, r.alertas().size());
        assertEquals("LICENCIA", r.alertas().get(0).codigoTipo());
        assertEquals("A-I", r.alertas().get(0).servicioOtro());
        verify(alertas).sincronizarLicencia(9L);
    }

    @Test
    void detalleLicenciaSinAtenderNiAnular() {
        when(alertas.sincronizarLicencia(9L)).thenReturn(0);
        when(alertas.obtenerDetalleLicenciaPropio(9L, 44L)).thenReturn(Optional.of(
                new AlertasJdbcDao.DetalleAlertaFila(
                        44L, 0L, 0L, "LICENCIA", "Licencia de conducir", "POR_TIEMPO", "DESDE_HOY",
                        "PENDIENTE", "2026-11-15T00:00:00.000Z", LocalDate.of(2026, 11, 20),
                        null, null, null, "DIAS", 30, LocalDate.of(2026, 10, 21), "A-IIa",
                        null, null, 1, null, null, null, null, null)));

        var d = service.consultarDetalleLicencia(9L, 44L);

        assertEquals("LICENCIA", d.categoria());
        assertEquals("A-IIa", d.servicioOtro());
        assertFalse(d.acciones().atender());
        assertFalse(d.acciones().anular());
        verify(alertas).sincronizarLicencia(anyLong());
    }
}
