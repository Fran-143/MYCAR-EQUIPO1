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

/** RF22 — TDD de reglas ProgramarAlerta (DESDE_HOY / DESDE_MANTENIMIENTO). */
class ProgramarAlertaServiceReglasTest {
    AlertasJdbcDao alertas;
    OperacionAlertaJdbcDao operaciones;
    ProgramarAlertaService service;
    LocalDate hoy = LocalDate.of(2026, 10, 28);
    LocalDate fechaServicio = LocalDate.of(2026, 6, 28);

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
        when(alertas.mantenimientoRealizado(eq(3L), eq(55L))).thenReturn(Optional.of(
                new AlertasJdbcDao.MantenimientoBase(55L, 1L, "CAMBIO_ACEITE", fechaServicio, 15000, null, "REALIZADO")));
    }

    private void operacionNueva(UUID op, long usuario, String huella) {
        when(operaciones.intentarCrear(eq(op), eq(usuario), anyString())).thenReturn(true);
        when(operaciones.buscarYBloquear(op)).thenReturn(Optional.of(
                new OperacionAlertaJdbcDao.OperacionAlerta(op, usuario, huella, "EN_PROCESO", null)));
    }

    private ContratoRf22.ProgramarRequest kmHoy(UUID op, int intervalo) {
        return new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "POR_KILOMETRAJE", "DESDE_HOY", null, null, intervalo, null, null,
                null, 20000, 10L);
    }

    @Test void porKilometrajeInsertaSinFechaBase() {
        UUID op = UUID.randomUUID();
        var req = kmHoy(op, 5000);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);
        when(alertas.insertarAlerta(any())).thenReturn(100L);

        var resp = service.programar(9L, 3L, req);

        assertEquals(100L, resp.idAlerta());
        var cap = org.mockito.ArgumentCaptor.forClass(AlertasJdbcDao.DatosInsercion.class);
        verify(alertas).insertarAlerta(cap.capture());
        assertNull(cap.getValue().fechaBase());
        assertEquals("DESDE_HOY", cap.getValue().origenCalculo());
        assertNull(cap.getValue().idReferencia());
        assertEquals(20000, cap.getValue().kmBase());
        assertEquals(25000, cap.getValue().kmObjetivo());
    }

    @Test void ambosDesdeHoy() {
        UUID op = UUID.randomUUID();
        var req = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "AMBOS", "DESDE_HOY", "MESES", 6, 5000, null, null, hoy, 20000, 10L);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);
        when(alertas.insertarAlerta(any())).thenReturn(101L);

        long id = service.programar(9L, 3L, req).idAlerta();

        assertEquals(101L, id);
        var cap = org.mockito.ArgumentCaptor.forClass(AlertasJdbcDao.DatosInsercion.class);
        verify(alertas).insertarAlerta(cap.capture());
        assertEquals(hoy, cap.getValue().fechaBase());
        assertEquals(hoy.plusMonths(6), cap.getValue().fechaObjetivo());
        assertEquals(25000, cap.getValue().kmObjetivo());
    }

    @Test void ambosDesdeMantenimientoUsaBasesDelServicio() {
        UUID op = UUID.randomUUID();
        // km_servicio 15000 + 6000 = 21000 > vigente 20000.
        var req = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "AMBOS", "DESDE_MANTENIMIENTO", "MESES", 6, 6000, null, 55L,
                fechaServicio, 15000, null);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);
        when(alertas.insertarAlerta(any())).thenReturn(201L);

        service.programar(9L, 3L, req);

        var cap = org.mockito.ArgumentCaptor.forClass(AlertasJdbcDao.DatosInsercion.class);
        verify(alertas).insertarAlerta(cap.capture());
        assertEquals("DESDE_MANTENIMIENTO", cap.getValue().origenCalculo());
        assertEquals(fechaServicio, cap.getValue().fechaBase());
        assertEquals(fechaServicio.plusMonths(6), cap.getValue().fechaObjetivo());
        assertEquals(15000, cap.getValue().kmBase());
        assertEquals(21000, cap.getValue().kmObjetivo());
        assertEquals(55L, cap.getValue().idReferencia());
    }

    @Test void porTiempoSinLecturaPermiteProgramar() {
        when(alertas.ultimaLectura(3L)).thenReturn(null);
        UUID op = UUID.randomUUID();
        var req = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "POR_TIEMPO", "DESDE_HOY", "MESES", 6, null, null, null, hoy, null, null);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);
        when(alertas.insertarAlerta(any())).thenReturn(102L);

        long id = service.programar(9L, 3L, req).idAlerta();

        assertEquals(102L, id);
        var cap = org.mockito.ArgumentCaptor.forClass(AlertasJdbcDao.DatosInsercion.class);
        verify(alertas).insertarAlerta(cap.capture());
        assertNull(cap.getValue().kmBase());
    }

    @Test void rechazaKmObjetivoYaAlcanzado() {
        UUID op = UUID.randomUUID();
        // DESDE_MANTENIMIENTO: km_servicio 15000 + 5000 = 20000 <= vigente 21000.
        var req = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "POR_KILOMETRAJE", "DESDE_MANTENIMIENTO", null, null, 5000, null, 55L,
                null, 15000, null);
        when(alertas.ultimaLectura(3L)).thenReturn(new AlertasJdbcDao.LecturaActual(10L, 21000));
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);

        ErrorAlertas ex = assertThrows(ErrorAlertas.class, () -> service.programar(9L, 3L, req));
        assertEquals("OBJETIVO_NO_FUTURO", ex.codigo());
        verify(alertas, never()).insertarAlerta(any());
    }

    @Test void g89RechazaSiKmBaseCambio() {
        UUID op = UUID.randomUUID();
        var req = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "POR_KILOMETRAJE", "DESDE_HOY", null, null, 5000, null, null,
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
            var req = kmHoy(op, intervalo);
            String huella = HuellaProgramarAlerta.calcular(3L, req);
            operacionNueva(op, 9L, huella);
            when(alertas.insertarAlerta(any())).thenReturn(10L + i);
            assertNotNull(service.programar(9L, 3L, req).idAlerta());
        }
        verify(alertas, times(3)).insertarAlerta(any());
    }

    @Test void equivalenteActivaSeExplicaComoConflicto() {
        UUID op = UUID.randomUUID();
        var req = kmHoy(op, 5000);
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
        var req = kmHoy(op, 5000);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        when(operaciones.intentarCrear(eq(op), eq(9L), anyString())).thenReturn(false);
        when(operaciones.buscarYBloquear(op)).thenReturn(Optional.of(
                new OperacionAlertaJdbcDao.OperacionAlerta(
                        op, 9L, huella, "CONFIRMADA",
                        "{\"idAlerta\":77,\"estado\":\"PROGRAMADA\"}")));

        var resp = service.programar(9L, 3L, req);
        assertEquals(77L, resp.idAlerta());
        verify(alertas, never()).insertarAlerta(any());
    }

    @Test void segundaLlegadaEnProcesoNoDuplica() {
        UUID op = UUID.randomUUID();
        var req = kmHoy(op, 5000);
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
                op, "CAMBIO_ACEITE", "POR_KILOMETRAJE", "DESDE_HOY", null, null, 100, null, null,
                null, Integer.MAX_VALUE - 10, 10L);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);

        ErrorAlertas ex = assertThrows(ErrorAlertas.class, () -> service.programar(9L, 3L, req));
        assertEquals("DATOS_INVALIDOS", ex.codigo());
        verify(alertas, never()).insertarAlerta(any());
    }

    @Test void desdeHoyRechazaReferencia() {
        UUID op = UUID.randomUUID();
        var req = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "POR_KILOMETRAJE", "DESDE_HOY", null, null, 5000, null, 55L,
                null, 20000, 10L);
        String huella = HuellaProgramarAlerta.calcular(3L, req);
        operacionNueva(op, 9L, huella);

        ErrorAlertas ex = assertThrows(ErrorAlertas.class, () -> service.programar(9L, 3L, req));
        assertEquals("DATOS_INVALIDOS", ex.codigo());
    }

    @Test void contextoSinTipoPideReferenciasVacias() {
        when(alertas.tiposActivos()).thenReturn(java.util.List.of());
        when(alertas.referenciasRealizadas(3L, null, null)).thenReturn(java.util.List.of());
        var ctx = service.contexto(9L, 3L, null, null);
        assertTrue(ctx.referencias().isEmpty());
        verify(alertas).referenciasRealizadas(3L, null, null);
        verify(alertas).verificarVehiculoPropioActivo(9L, 3L);
    }

    @Test void contextoConTipoFiltraReferencias() {
        var ref = new ContratoRf22.ReferenciaDto(55L, "CAMBIO_ACEITE", fechaServicio, 15000, null);
        when(alertas.tiposActivos()).thenReturn(java.util.List.of());
        when(alertas.referenciasRealizadas(3L, "CAMBIO_ACEITE", null)).thenReturn(java.util.List.of(ref));
        var ctx = service.contexto(9L, 3L, "CAMBIO_ACEITE", null);
        assertEquals(1, ctx.referencias().size());
        assertEquals(55L, ctx.referencias().getFirst().idMantenimiento());
        verify(alertas).referenciasRealizadas(3L, "CAMBIO_ACEITE", null);
    }

    @Test void contextoOtroPasaServicio() {
        when(alertas.tiposActivos()).thenReturn(java.util.List.of());
        when(alertas.referenciasRealizadas(3L, "OTRO", "Correa")).thenReturn(java.util.List.of());
        service.contexto(9L, 3L, "OTRO", "Correa");
        verify(alertas).referenciasRealizadas(3L, "OTRO", "Correa");
    }
}
