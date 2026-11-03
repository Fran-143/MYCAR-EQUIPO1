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

/** RF22 — TDD de reglas ProgramarAlerta (Arrange / Act / Assert). */
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
        // Arrange
        UUID op = UUID.randomUUID();
        var req = km(op, 5000);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);
        when(alertas.insertarAlerta(any())).thenReturn(100L);

        // Act
        var resp = service.programar(9L, 3L, req);

        // Assert
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
        // Arrange
        UUID op = UUID.randomUUID();
        var req = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "AMBOS", "MESES", 6, 5000, null, null, hoy, 20000, 10L);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);
        when(alertas.insertarAlerta(any())).thenReturn(101L);

        // Act
        long id = service.programar(9L, 3L, req).idAlerta();

        // Assert
        assertEquals(101L, id);
        var cap = org.mockito.ArgumentCaptor.forClass(AlertasJdbcDao.DatosInsercion.class);
        verify(alertas).insertarAlerta(cap.capture());
        assertEquals(hoy, cap.getValue().fechaBase());
        assertNotNull(cap.getValue().fechaObjetivo());
        assertEquals(20000, cap.getValue().kmBase());
        assertEquals(25000, cap.getValue().kmObjetivo());
    }

    @Test void porTiempoSinLecturaPermiteProgramar() {
        // Arrange
        when(alertas.ultimaLectura(3L)).thenReturn(null);
        UUID op = UUID.randomUUID();
        var req = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "POR_TIEMPO", "MESES", 6, null, null, null, hoy, null, null);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);
        when(alertas.insertarAlerta(any())).thenReturn(102L);

        // Act
        long id = service.programar(9L, 3L, req).idAlerta();

        // Assert
        assertEquals(102L, id);
        var cap = org.mockito.ArgumentCaptor.forClass(AlertasJdbcDao.DatosInsercion.class);
        verify(alertas).insertarAlerta(cap.capture());
        assertNull(cap.getValue().kmBase());
        assertNull(cap.getValue().kmObjetivo());
    }

    @Test void g89RechazaSiKmBaseCambio() {
        // Arrange
        UUID op = UUID.randomUUID();
        var req = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "POR_KILOMETRAJE", null, null, 5000, null, null,
                null, 19000, 10L);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);

        // Act
        ErrorAlertas ex = assertThrows(ErrorAlertas.class, () -> service.programar(9L, 3L, req));

        // Assert
        assertEquals("BASES_CAMBIARON", ex.codigo());
        verify(alertas, never()).insertarAlerta(any());
    }

    @Test void tresAlertasDistintasNoEstanLimitadasPorTopeFicticio() {
        // Arrange + Act: tres operaciones distintas
        for (int i = 0; i < 3; i++) {
            UUID op = UUID.randomUUID();
            int intervalo = 5000 + i * 1000;
            var req = km(op, intervalo);
            String huella = HuellaProgramarAlerta.calcular(3L, req);
            operacionNueva(op, 9L, huella);
            when(alertas.insertarAlerta(any())).thenReturn(10L + i);
            assertNotNull(service.programar(9L, 3L, req).idAlerta());
        }
        // Assert
        verify(alertas, times(3)).insertarAlerta(any());
    }

    @Test void equivalenteActivaSeExplicaComoConflicto() {
        // Arrange
        UUID op = UUID.randomUUID();
        var req = km(op, 5000);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);
        when(alertas.insertarAlerta(any())).thenThrow(
                new DataIntegrityViolationException("uq_alerta_equivalente_activa"));

        // Act
        ErrorAlertas ex = assertThrows(ErrorAlertas.class, () -> service.programar(9L, 3L, req));

        // Assert
        assertEquals("ALERTA_EQUIVALENTE", ex.codigo());
    }

    @Test void criterioHelpers() {
        // Arrange / Act / Assert (helpers puros)
        assertTrue(CriterioAlerta.usaTiempo("POR_TIEMPO"));
        assertTrue(CriterioAlerta.usaTiempo("AMBOS"));
        assertFalse(CriterioAlerta.usaTiempo("POR_KILOMETRAJE"));
        assertTrue(CriterioAlerta.usaKm("POR_KILOMETRAJE"));
        assertTrue(CriterioAlerta.usaKm("AMBOS"));
        assertFalse(CriterioAlerta.usaKm("POR_TIEMPO"));
    }

    @Test void reintentoMismaOperacionConfirmadaRecuperaSinInsertar() {
        // Arrange
        UUID op = UUID.randomUUID();
        var req = km(op, 5000);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        when(operaciones.intentarCrear(eq(op), eq(9L), anyString())).thenReturn(false);
        when(operaciones.buscarYBloquear(op)).thenReturn(Optional.of(
                new OperacionAlertaJdbcDao.OperacionAlerta(
                        op, 9L, huella, "CONFIRMADA",
                        "{\"idAlerta\":77,\"estado\":\"PROGRAMADA\"}")));

        // Act
        var resp = service.programar(9L, 3L, req);

        // Assert
        assertEquals(77L, resp.idAlerta());
        assertEquals("PROGRAMADA", resp.estado());
        assertEquals(op, resp.idOperacion());
        verify(alertas, never()).insertarAlerta(any());
    }

    @Test void segundaLlegadaEnProcesoNoDuplica() {
        // Arrange
        UUID op = UUID.randomUUID();
        var req = km(op, 5000);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        when(operaciones.intentarCrear(eq(op), eq(9L), anyString())).thenReturn(false);
        when(operaciones.buscarYBloquear(op)).thenReturn(Optional.of(
                new OperacionAlertaJdbcDao.OperacionAlerta(op, 9L, huella, "EN_PROCESO", null)));

        // Act
        ErrorAlertas ex = assertThrows(ErrorAlertas.class, () -> service.programar(9L, 3L, req));

        // Assert
        assertEquals("OPERACION_EN_PROCESO", ex.codigo());
        verify(alertas, never()).insertarAlerta(any());
    }

    @Test void kmObjetivoQueDesbordaEnteroSeRechaza() {
        // Arrange
        when(alertas.ultimaLectura(3L)).thenReturn(new AlertasJdbcDao.LecturaActual(10L, Integer.MAX_VALUE - 10));
        UUID op = UUID.randomUUID();
        var req = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "POR_KILOMETRAJE", null, null, 100, null, null,
                null, Integer.MAX_VALUE - 10, 10L);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);

        // Act
        ErrorAlertas ex = assertThrows(ErrorAlertas.class, () -> service.programar(9L, 3L, req));

        // Assert
        assertEquals("DATOS_INVALIDOS", ex.codigo());
        verify(alertas, never()).insertarAlerta(any());
    }
}
