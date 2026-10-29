package backend.alertas.service;

import backend.alertas.api.dto.ContratoRf22;
import backend.alertas.dao.AlertasJdbcDao;
import backend.alertas.dao.OperacionAlertaJdbcDao;
import backend.alertas.model.ErrorAlertas;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Reglas RF22: AMBOS, G89, POR_TIEMPO sin km, equivalencia; fecha_base nula en POR_KILOMETRAJE. */
class ProgramarAlertaServiceReglasTest {
    AlertasJdbcDao alertas;
    OperacionAlertaJdbcDao operaciones;
    ProgramarAlertaService service;
    LocalDate hoy = LocalDate.of(2026, 10, 28);

    @BeforeEach void preparar() {
        alertas = mock(AlertasJdbcDao.class);
        operaciones = mock(OperacionAlertaJdbcDao.class);
        service = new ProgramarAlertaService(alertas, operaciones, new ObjectMapper());
        when(alertas.hoyLima()).thenReturn(hoy);
        when(alertas.ultimaLectura(3L)).thenReturn(new AlertasJdbcDao.LecturaActual(10L, 20000));
        when(alertas.idTipoPorCodigo("CAMBIO_ACEITE")).thenReturn(1L);
        when(alertas.normalizarServicioOtro(anyString())).thenReturn("");
        when(alertas.calcularEstadoInicial(any(), any(), any(), any(), any(), anyInt())).thenReturn("PROGRAMADA");
        when(alertas.calcularFechaObjetivo(any(), anyString(), anyInt()))
                .thenAnswer(i -> ((LocalDate) i.getArgument(0)).plusMonths(((Integer) i.getArgument(2))));
    }

    private void operacionNueva(UUID op, long usuario, String huella) {
        when(operaciones.intentarCrear(eq(op), eq(usuario), anyString())).thenReturn(true);
        when(operaciones.buscarYBloquear(op)).thenReturn(Optional.of(
                new OperacionAlertaJdbcDao.OperacionAlerta(op, usuario, huella, "EN_PROCESO", null)));
    }

    private ContratoRf22.ProgramarRequest km(UUID op, int intervalo) {
        return new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "POR_KILOMETRAJE", null, null, intervalo, null, null,
                null, 20000, 10L);
    }

    @Test void porKilometrajeInsertaSinFechaBase() {
        UUID op = UUID.randomUUID();
        var req = km(op, 5000);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);
        when(alertas.insertarAlerta(any())).thenReturn(100L);

        var resp = service.programar(9L, 3L, req);
        assertEquals(100L, resp.idAlerta());

        var cap = org.mockito.ArgumentCaptor.forClass(AlertasJdbcDao.DatosInsercion.class);
        verify(alertas).insertarAlerta(cap.capture());
        assertNull(cap.getValue().fechaBase(), "POR_KILOMETRAJE exige fecha_base NULL (ck_alerta_tiempo)");
        assertNull(cap.getValue().fechaObjetivo());
        assertEquals(20000, cap.getValue().kmBase());
        assertEquals(5000, cap.getValue().intervaloKm());
        assertEquals(25000, cap.getValue().kmObjetivo());
    }

    @Test void ambosInsertaTiempoYKm() {
        UUID op = UUID.randomUUID();
        var req = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "AMBOS", "MESES", 6, 5000, null, null, hoy, 20000, 10L);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);
        when(alertas.insertarAlerta(any())).thenReturn(101L);

        assertEquals(101L, service.programar(9L, 3L, req).idAlerta());
        var cap = org.mockito.ArgumentCaptor.forClass(AlertasJdbcDao.DatosInsercion.class);
        verify(alertas).insertarAlerta(cap.capture());
        assertEquals(hoy, cap.getValue().fechaBase());
        assertNotNull(cap.getValue().fechaObjetivo());
        assertEquals(20000, cap.getValue().kmBase());
        assertEquals(25000, cap.getValue().kmObjetivo());
    }

    @Test void porTiempoSinLecturaPermiteProgramar() {
        when(alertas.ultimaLectura(3L)).thenReturn(null);
        UUID op = UUID.randomUUID();
        var req = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "POR_TIEMPO", "MESES", 6, null, null, null, hoy, null, null);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);
        when(alertas.insertarAlerta(any())).thenReturn(102L);
        assertEquals(102L, service.programar(9L, 3L, req).idAlerta());
        var cap = org.mockito.ArgumentCaptor.forClass(AlertasJdbcDao.DatosInsercion.class);
        verify(alertas).insertarAlerta(cap.capture());
        assertNull(cap.getValue().kmBase());
        assertNull(cap.getValue().kmObjetivo());
    }

    @Test void g89RechazaSiKmBaseCambio() {
        UUID op = UUID.randomUUID();
        var req = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "POR_KILOMETRAJE", null, null, 5000, null, null,
                null, 19000, 10L);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);
        ErrorAlertas ex = assertThrows(ErrorAlertas.class, () -> service.programar(9L, 3L, req));
        assertEquals("BASES_CAMBIARON", ex.codigo());
        verify(alertas, never()).insertarAlerta(any());
    }

    @Test void tresAlertasDistintasNoEstanLimitadasPorTopeFicticio() {
        for (int i = 0; i < 3; i++) {
            UUID op = UUID.randomUUID();
            int intervalo = 5000 + i * 1000;
            var req = km(op, intervalo);
            String huella = HuellaProgramarAlerta.calcular(3L, req);
            operacionNueva(op, 9L, huella);
            when(alertas.insertarAlerta(any())).thenReturn(10L + i);
            assertNotNull(service.programar(9L, 3L, req).idAlerta());
        }
        verify(alertas, times(3)).insertarAlerta(any());
    }

    @Test void equivalenteActivaSeExplicaComoConflicto() {
        UUID op = UUID.randomUUID();
        var req = km(op, 5000);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);
        when(alertas.insertarAlerta(any())).thenThrow(
                new DataIntegrityViolationException("uq_alerta_equivalente_activa"));
        ErrorAlertas ex = assertThrows(ErrorAlertas.class, () -> service.programar(9L, 3L, req));
        assertEquals("ALERTA_EQUIVALENTE", ex.codigo());
    }

    @Test void criterioHelpers() {
        assertTrue(CriterioAlerta.usaTiempo("POR_TIEMPO"));
        assertTrue(CriterioAlerta.usaTiempo("AMBOS"));
        assertFalse(CriterioAlerta.usaTiempo("POR_KILOMETRAJE"));
        assertTrue(CriterioAlerta.usaKm("POR_KILOMETRAJE"));
        assertTrue(CriterioAlerta.usaKm("AMBOS"));
        assertFalse(CriterioAlerta.usaKm("POR_TIEMPO"));
    }

    @Test void reintentoMismaOperacionConfirmadaRecuperaSinInsertar() {
        UUID op = UUID.randomUUID();
        var req = km(op, 5000);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        when(operaciones.intentarCrear(eq(op), eq(9L), anyString())).thenReturn(false);
        when(operaciones.buscarYBloquear(op)).thenReturn(Optional.of(
                new OperacionAlertaJdbcDao.OperacionAlerta(
                        op, 9L, huella, "CONFIRMADA",
                        "{\"idAlerta\":77,\"estado\":\"PROGRAMADA\"}")));

        var resp = service.programar(9L, 3L, req);
        assertEquals(77L, resp.idAlerta());
        assertEquals("PROGRAMADA", resp.estado());
        assertEquals(op, resp.idOperacion());
        verify(alertas, never()).insertarAlerta(any());
    }

    @Test void segundaLlegadaEnProcesoNoDuplica() {
        UUID op = UUID.randomUUID();
        var req = km(op, 5000);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        when(operaciones.intentarCrear(eq(op), eq(9L), anyString())).thenReturn(false);
        when(operaciones.buscarYBloquear(op)).thenReturn(Optional.of(
                new OperacionAlertaJdbcDao.OperacionAlerta(op, 9L, huella, "EN_PROCESO", null)));

        ErrorAlertas ex = assertThrows(ErrorAlertas.class, () -> service.programar(9L, 3L, req));
        assertEquals("OPERACION_EN_PROCESO", ex.codigo());
        verify(alertas, never()).insertarAlerta(any());
    }

    @Test void kmObjetivoQueDesbordaEnteroSeRechaza() {
        when(alertas.ultimaLectura(3L)).thenReturn(new AlertasJdbcDao.LecturaActual(10L, Integer.MAX_VALUE - 10));
        UUID op = UUID.randomUUID();
        var req = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "POR_KILOMETRAJE", null, null, 100, null, null,
                null, Integer.MAX_VALUE - 10, 10L);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);
        ErrorAlertas ex = assertThrows(ErrorAlertas.class, () -> service.programar(9L, 3L, req));
        assertEquals("DATOS_INVALIDOS", ex.codigo());
        verify(alertas, never()).insertarAlerta(any());
    }
}
