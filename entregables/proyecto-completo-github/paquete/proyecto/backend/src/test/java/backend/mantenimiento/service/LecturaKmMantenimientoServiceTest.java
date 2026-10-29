package backend.mantenimiento.service;

import backend.mantenimiento.dao.LecturaKmMantenimientoDao;
import backend.mantenimiento.model.DatosRegistroMantenimiento;
import backend.mantenimiento.model.DatosRegistroMantenimiento.OrigenKmServicio;
import backend.mantenimiento.model.LecturaKmReferencia;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LecturaKmMantenimientoServiceTest {

    @Test
    void RF17_CP02_EA01_rechazaKmManualInferiorALecturaDeDiaAnterior() {
        // ARRANGE: la lectura efectiva del mismo vehículo precede al servicio.
        var dao = mock(LecturaKmMantenimientoDao.class);
        var fechaServicio = LocalDate.of(2026, 9, 20);
        when(dao.buscarUltimaLecturaAnterior(10L, fechaServicio))
                .thenReturn(Optional.of(new LecturaKmReferencia(
                        700L, 10L, 48000, Instant.parse("2026-09-10T15:00:00Z")
                )));
        var servicio = new LecturaKmMantenimientoService(dao);

        // ACT + ASSERT: 47 000 km no son coherentes después de 48 000 km.
        assertThrows(IllegalArgumentException.class,
                () -> servicio.validarReferencia(
                        10L, datosHistoricos(fechaServicio, 47000)));
    }

    @Test
    void RF17_CP02_EA01_aceptaKmManualCoherenteConLecturaAnterior() {
        var dao = mock(LecturaKmMantenimientoDao.class);
        var fecha = LocalDate.of(2026, 9, 20);
        when(dao.buscarUltimaLecturaAnterior(10L, fecha))
                .thenReturn(Optional.of(new LecturaKmReferencia(
                        700L, 10L, 48000, Instant.parse("2026-09-10T15:00:00Z")
                )));

        assertDoesNotThrow(() -> new LecturaKmMantenimientoService(dao)
                .validarReferencia(10L, datosHistoricos(fecha, 49000)));
        verify(dao).buscarPrimeraLecturaPosterior(10L, fecha);
    }

    @Test
    void RF17_CP02_EA01_rechazaLecturaAnteriorDeOtroVehiculo() {
        var dao = mock(LecturaKmMantenimientoDao.class);
        var fecha = LocalDate.of(2026, 9, 20);
        when(dao.buscarUltimaLecturaAnterior(10L, fecha))
                .thenReturn(Optional.of(new LecturaKmReferencia(
                        700L, 20L, 48000, Instant.parse("2026-09-10T15:00:00Z")
                )));

        assertThrows(IllegalArgumentException.class,
                () -> new LecturaKmMantenimientoService(dao)
                        .validarReferencia(10L, datosHistoricos(fecha, 49000)));
    }

    @Test
    void RF17_CP02_EA01_sinLecturaAnteriorMantieneLimitePosterior() {
        var dao = mock(LecturaKmMantenimientoDao.class);
        var fecha = LocalDate.of(2026, 9, 20);
        when(dao.buscarPrimeraLecturaPosterior(10L, fecha))
                .thenReturn(Optional.of(new LecturaKmReferencia(
                        702L, 10L, 50000, Instant.parse("2026-10-10T15:00:00Z")
                )));

        assertThrows(IllegalArgumentException.class,
                () -> new LecturaKmMantenimientoService(dao)
                        .validarReferencia(10L, datosHistoricos(fecha, 51000)));
    }

    @Test
    void RF17_CP02_EA01_respetaAmbosLimitesDeDiasDistintos() {
        var dao = mock(LecturaKmMantenimientoDao.class);
        var fecha = LocalDate.of(2026, 9, 20);
        when(dao.buscarUltimaLecturaAnterior(10L, fecha))
                .thenReturn(Optional.of(new LecturaKmReferencia(
                        700L, 10L, 48000, Instant.parse("2026-09-10T15:00:00Z")
                )));
        when(dao.buscarPrimeraLecturaPosterior(10L, fecha))
                .thenReturn(Optional.of(new LecturaKmReferencia(
                        702L, 10L, 50000, Instant.parse("2026-10-10T15:00:00Z")
                )));
        var servicio = new LecturaKmMantenimientoService(dao);

        assertDoesNotThrow(() -> servicio.validarReferencia(
                10L, datosHistoricos(fecha, 49000)));
        assertThrows(IllegalArgumentException.class, () -> servicio.validarReferencia(
                10L, datosHistoricos(fecha, 47000)));
        assertThrows(IllegalArgumentException.class, () -> servicio.validarReferencia(
                10L, datosHistoricos(fecha, 51000)));
    }

    @Test
    void RF17_CP02_EA01_rechazaKmManualMayorQueLecturaDeDiaPosterior() {
        // ARRANGE: una lectura efectiva posterior del mismo vehículo marca el límite.
        var dao = mock(LecturaKmMantenimientoDao.class);
        when(dao.buscarPrimeraLecturaPosterior(10L, LocalDate.of(2026, 9, 20)))
                .thenReturn(Optional.of(new LecturaKmReferencia(
                        701L,
                        10L,
                        50000,
                        Instant.parse("2026-10-10T15:00:00Z")
                )));
        var servicio = new LecturaKmMantenimientoService(dao);
        var datos = new DatosRegistroMantenimiento(
                "MANTENIMIENTO_GENERAL",
                LocalDate.of(2026, 9, 20),
                52000,
                OrigenKmServicio.HISTORICO_MANUAL,
                null,
                null,
                null,
                null,
                null
        );

        // ACT + ASSERT: 52 000 km no pueden preceder a una lectura de 50 000 km.
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.validarReferencia(10L, datos)
        );
    }

    @Test
    void RF17_CP02_EA01_aceptaKmManualNoMayorQueLecturaPosterior() {
        var dao = mock(LecturaKmMantenimientoDao.class);
        var fechaServicio = LocalDate.of(2026, 9, 20);
        when(dao.buscarPrimeraLecturaPosterior(10L, fechaServicio))
                .thenReturn(Optional.of(new LecturaKmReferencia(
                        701L, 10L, 50000, Instant.parse("2026-10-10T15:00:00Z")
                )));
        var servicio = new LecturaKmMantenimientoService(dao);

        assertDoesNotThrow(() -> servicio.validarReferencia(
                10L, datosHistoricos(fechaServicio, 47000)));
        verify(dao, never()).buscarPorId(any());
    }

    @Test
    void RF17_CP02_EA01_rechazaReferenciaPosteriorDeOtroVehiculo() {
        var dao = mock(LecturaKmMantenimientoDao.class);
        var fechaServicio = LocalDate.of(2026, 9, 20);
        when(dao.buscarPrimeraLecturaPosterior(10L, fechaServicio))
                .thenReturn(Optional.of(new LecturaKmReferencia(
                        701L, 20L, 50000, Instant.parse("2026-10-10T15:00:00Z")
                )));

        assertThrows(IllegalArgumentException.class,
                () -> new LecturaKmMantenimientoService(dao).validarReferencia(
                        10L, datosHistoricos(fechaServicio, 47000)));
    }

    @Test
    void RF17_CP02_EA01_sinLecturaPosteriorNoImponeLimitePosterior() {
        var dao = mock(LecturaKmMantenimientoDao.class);
        var fechaServicio = LocalDate.of(2026, 9, 20);

        assertDoesNotThrow(() -> new LecturaKmMantenimientoService(dao)
                .validarReferencia(10L, datosHistoricos(fechaServicio, 47000)));
        verify(dao).buscarPrimeraLecturaPosterior(10L, fechaServicio);
    }

    @Test
    void RF17_CP02_EA01_aceptaLecturaCompatibleEnFechaDeLima() {
        // En UTC es 27, pero en Lima todavía es 26.
        var servicio = servicioConLectura(
                10L,
                21000,
                "2026-10-27T02:00:00Z"
        );

        assertDoesNotThrow(
                () -> servicio.validarReferencia(10L, datosConLectura())
        );
    }

    @Test
    void RF17_CP02_EA01_rechazaLecturaInexistente() {
        LecturaKmMantenimientoDao dao = mock(LecturaKmMantenimientoDao.class);

        var servicio = new LecturaKmMantenimientoService(dao);

        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.validarReferencia(10L, datosConLectura())
        );
        verify(dao).buscarPorId(701L);
    }

    @Test
    void RF17_CP02_EA01_rechazaLecturaDeOtroVehiculo() {
        // La lectura pertenece al vehículo 20; registramos en el 10.
        var servicio = servicioConLectura(
                20L,
                21000,
                "2026-10-26T15:00:00Z"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.validarReferencia(10L, datosConLectura())
        );
    }

    @Test
    void RF17_CP02_EA01_rechazaKilometrajeDistintoDeLaLectura() {
        // La lectura tiene 22000 km; el servicio declara 21000.
        var servicio = servicioConLectura(
                10L,
                22000,
                "2026-10-26T15:00:00Z"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.validarReferencia(10L, datosConLectura())
        );
    }

    @Test
    void RF17_CP02_EA01_rechazaLecturaPosteriorAlDiaDelServicio() {
        // Lectura del 27 en Lima; servicio del 26.
        var servicio = servicioConLectura(
                10L,
                21000,
                "2026-10-27T15:00:00Z"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.validarReferencia(10L, datosConLectura())
        );
    }

    @Test
    void RF17_CP02_EA01_historicoManualSinLecturaPosteriorNoConsultaPorId() {
        LecturaKmMantenimientoDao dao = mock(LecturaKmMantenimientoDao.class);

        var servicio = new LecturaKmMantenimientoService(dao);

        var datos = new DatosRegistroMantenimiento(
                "MANTENIMIENTO_GENERAL",
                LocalDate.of(2026, 10, 26),
                21000,
                OrigenKmServicio.HISTORICO_MANUAL,
                null,
                null,
                null,
                null,
                null
        );

        assertDoesNotThrow(
                () -> servicio.validarReferencia(10L, datos)
        );
        verify(dao, never()).buscarPorId(any());
    }

    @Test
    void revision_rechazaLecturaAntiguaSiOtraAnteriorAlServicioYaEsMayor() {
        var dao = mock(LecturaKmMantenimientoDao.class);
        var fecha = LocalDate.of(2026, 10, 26);
        when(dao.buscarPorId(701L)).thenReturn(Optional.of(new LecturaKmReferencia(
                701L, 10L, 21000, Instant.parse("2026-10-20T15:00:00Z"))));
        when(dao.buscarUltimaLecturaAnterior(10L, fecha)).thenReturn(Optional.of(
                new LecturaKmReferencia(702L, 10L, 30000,
                        Instant.parse("2026-10-25T15:00:00Z"))));
        assertThrows(IllegalArgumentException.class,
                () -> new LecturaKmMantenimientoService(dao)
                        .validarReferencia(10L, datosConLectura()));
    }

    @Test
    void revision_lecturaTambienRespetaElLimitePosterior() {
        var dao = mock(LecturaKmMantenimientoDao.class);
        when(dao.buscarPorId(701L)).thenReturn(Optional.of(new LecturaKmReferencia(
                701L, 10L, 21000, Instant.parse("2026-10-20T15:00:00Z"))));
        when(dao.buscarPrimeraLecturaPosterior(10L, LocalDate.of(2026, 10, 26)))
                .thenReturn(Optional.of(new LecturaKmReferencia(702L, 10L, 20500,
                        Instant.parse("2026-10-27T15:00:00Z"))));
        assertThrows(IllegalArgumentException.class,
                () -> new LecturaKmMantenimientoService(dao)
                        .validarReferencia(10L, datosConLectura()));
    }

    @Test
    void revision_permiteServicioHistoricoConLecturaMenorQueLaActual() {
        var dao = mock(LecturaKmMantenimientoDao.class);
        var fecha = LocalDate.of(2026, 10, 26);
        when(dao.buscarPorId(701L)).thenReturn(Optional.of(new LecturaKmReferencia(
                701L, 10L, 21000, Instant.parse("2026-10-25T15:00:00Z"))));
        when(dao.buscarUltimaLecturaAnterior(10L, fecha)).thenReturn(Optional.of(
                new LecturaKmReferencia(701L, 10L, 21000,
                        Instant.parse("2026-10-25T15:00:00Z"))));
        when(dao.buscarPrimeraLecturaPosterior(10L, fecha)).thenReturn(Optional.of(
                new LecturaKmReferencia(702L, 10L, 30000,
                        Instant.parse("2026-10-27T15:00:00Z"))));
        assertDoesNotThrow(() -> new LecturaKmMantenimientoService(dao)
                .validarReferencia(10L, datosConLectura()));
    }

    private LecturaKmMantenimientoService servicioConLectura(
            Long idVehiculo,
            Integer valorKm,
            String fechaUtc
    ) {
        var lectura = new LecturaKmReferencia(
                701L,
                idVehiculo,
                valorKm,
                Instant.parse(fechaUtc)
        );

        LecturaKmMantenimientoDao dao = mock(LecturaKmMantenimientoDao.class);
        when(dao.buscarPorId(701L)).thenReturn(Optional.of(lectura));

        return new LecturaKmMantenimientoService(dao);
    }

    private DatosRegistroMantenimiento datosConLectura() {
        return new DatosRegistroMantenimiento(
                "MANTENIMIENTO_GENERAL",
                LocalDate.of(2026, 10, 26),
                21000,
                OrigenKmServicio.LECTURA,
                701L,
                null,
                null,
                null,
                null
        );
    }

    private DatosRegistroMantenimiento datosHistoricos(
            LocalDate fechaServicio, int kmServicio) {
        return new DatosRegistroMantenimiento(
                "MANTENIMIENTO_GENERAL", fechaServicio, kmServicio,
                OrigenKmServicio.HISTORICO_MANUAL, null,
                null, null, null, null
        );
    }
}
