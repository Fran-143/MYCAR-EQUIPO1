package backend.mantenimiento.service;

import backend.mantenimiento.model.DatosRegistroMantenimiento;
import backend.mantenimiento.model.DatosRegistroMantenimiento.OrigenKmServicio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static backend.mantenimiento.model.DatosRegistroMantenimiento
        .OrigenKmServicio.LECTURA;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidadorRegistroMantenimientoTest {

    private final Clock reloj = Clock.fixed(
            Instant.parse("2026-10-26T15:00:00Z"),
            ZoneId.of("America/Lima")
    );

    private final ValidadorRegistroMantenimiento validador =
            new ValidadorRegistroMantenimiento(reloj);

    @Test
    void RF17_CP05a_EA04_rechazaFechaFutura() {
        var datos = new DatosRegistroMantenimiento(
                "MANTENIMIENTO_GENERAL",
                LocalDate.of(2026, 10, 27),
                21000, LECTURA, 701L,
                null, null, null, null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validador.validar(datos)
        );
    }

    @Test
    void RF17_CP05b_EA04_rechazaFechaAusente() {
        var datos = new DatosRegistroMantenimiento(
                "MANTENIMIENTO_GENERAL",
                null,
                21000, LECTURA, 701L,
                null, null, null, null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validador.validar(datos)
        );
    }

    @Test
    void RF17_CP05c_EA04_rechazaKilometrajeCero() {
        var datos = new DatosRegistroMantenimiento(
                "MANTENIMIENTO_GENERAL",
                LocalDate.of(2026, 10, 26),
                0, LECTURA, 701L,
                null, null, null, null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validador.validar(datos)
        );
    }

    @Test
    void RF17_CP01_EF_aceptaDatosBasicosValidos() {
        var datos = datosParaOrigen(LECTURA, 701L);

        assertDoesNotThrow(() -> validador.validar(datos));
    }

    @Test
    void RF17_CP05c2_EA04_rechazaKilometrajeAusente() {
        var datos = new DatosRegistroMantenimiento(
                "MANTENIMIENTO_GENERAL",
                LocalDate.of(2026, 10, 26),
                null, LECTURA, 701L,
                null, null, null, null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validador.validar(datos)
        );
    }

    @Test
    void RF17_CP05d_EA04_rechazaOtroSinEspecificarServicio() {
        var datos = new DatosRegistroMantenimiento(
                "OTRO",
                LocalDate.of(2026, 10, 26),
                21000, LECTURA, 701L,
                null, null, null, null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validador.validar(datos)
        );
    }

    @Test
    void RF17_CP05_EA04_rechazaServicioOtroEnTipoDistintoDeOtro() {
        // ARRANGE: CAMBIO_ACEITE existe en el catálogo y no tiene campo propio.
        var datos = new DatosRegistroMantenimiento(
                "CAMBIO_ACEITE",
                LocalDate.of(2026, 10, 26),
                21000, LECTURA, 701L,
                null, "Cambio especial", null, null
        );

        // ACT + ASSERT: servicioOtro solo corresponde al tipo OTRO.
        assertThrows(IllegalArgumentException.class,
                () -> validador.validar(datos));
    }

    @Test
    void RF17_CP05_EA04_rechazaServicioOtroAunqueEsteVacioEnTipoDistinto() {
        var datos = new DatosRegistroMantenimiento(
                "MANTENIMIENTO_GENERAL",
                LocalDate.of(2026, 10, 26),
                21000, LECTURA, 701L,
                null, "", null, null
        );

        assertThrows(IllegalArgumentException.class,
                () -> validador.validar(datos));
    }

    @Test
    void RF17_CP05_EA04_aceptaTipoComunSinServicioOtro() {
        var datos = new DatosRegistroMantenimiento(
                "CAMBIO_ACEITE",
                LocalDate.of(2026, 10, 26),
                21000, LECTURA, 701L,
                null, null, null, null
        );

        assertDoesNotThrow(() -> validador.validar(datos));
    }

    @Test
    void RF17_CP05_EA04_rechazaTipoAusenteAntesDeConsultarCatalogo() {
        var datos = new DatosRegistroMantenimiento(
                null, LocalDate.of(2026, 10, 26), 21000,
                LECTURA, 701L, null, null, null, null
        );

        var error = assertThrows(IllegalArgumentException.class,
                () -> validador.validar(datos));
        assertTrue(error.getMessage().contains("codigoTipoMantenimiento"));
    }

    @Test
    void RF17_CP05_EA04_rechazaTipoEnBlancoAntesDeConsultarCatalogo() {
        var datos = new DatosRegistroMantenimiento(
                "   ", LocalDate.of(2026, 10, 26), 21000,
                LECTURA, 701L, null, null, null, null
        );

        var error = assertThrows(IllegalArgumentException.class,
                () -> validador.validar(datos));
        assertTrue(error.getMessage().contains("codigoTipoMantenimiento"));
    }

    @Test
    void RF17_CP05_EA04_rechazaDatosDeMantenimientoAusentes() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> validador.validar(null));
        assertTrue(error.getMessage().contains("datos"));
    }

    @Test
    void RF17_CP05_EA04_aceptaOtroConServicioEspecificado() {
        var datos = new DatosRegistroMantenimiento(
                "OTRO",
                LocalDate.of(2026, 10, 26),
                21000, LECTURA, 701L,
                null, "Revisión especial", null, null
        );

        assertDoesNotThrow(() -> validador.validar(datos));
    }

    @Test
    void RF17_CP05e_EA04_rechazaCostoNegativo() {
        var datos = new DatosRegistroMantenimiento(
                "MANTENIMIENTO_GENERAL",
                LocalDate.of(2026, 10, 26),
                21000, LECTURA, 701L,
                new BigDecimal("-10.00"),
                null, null, null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validador.validar(datos)
        );
    }

    @Test
    void RF17_CP05f_EA04_rechazaProximaFechaIgualAlServicio() {
        LocalDate fechaServicio = LocalDate.of(2026, 10, 26);

        var datos = new DatosRegistroMantenimiento(
                "MANTENIMIENTO_GENERAL",
                fechaServicio,
                21000, LECTURA, 701L,
                null, null, fechaServicio, null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validador.validar(datos)
        );
    }

    @Test
    void RF17_CP05g_EA04_rechazaProximoKmIgualAlServicio() {
        var datos = new DatosRegistroMantenimiento(
                "MANTENIMIENTO_GENERAL",
                LocalDate.of(2026, 10, 26),
                21000, LECTURA, 701L,
                null, null, null, 21000
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validador.validar(datos)
        );
    }

    @Test
    void RF17_CP02_EA01_rechazaOrigenAusente() {
        var datos = datosParaOrigen(null, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> validador.validar(datos)
        );
    }

    @Test
    void RF17_CP02_EA01_rechazaLecturaSinReferencia() {
        var datos = datosParaOrigen(LECTURA, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> validador.validar(datos)
        );
    }

    @Test
    void RF17_CP02_EA01_rechazaHistoricoManualConReferencia() {
        var datos = datosParaOrigen(
                OrigenKmServicio.HISTORICO_MANUAL, 701L
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validador.validar(datos)
        );
    }

    @Test
    void RF17_CP02_EA01_aceptaEstructuraHistoricaSinReferencia() {
        var datos = datosParaOrigen(
                OrigenKmServicio.HISTORICO_MANUAL, null
        );

        assertDoesNotThrow(() -> validador.validar(datos));
    }

    private DatosRegistroMantenimiento datosParaOrigen(
            OrigenKmServicio origen,
            Long referencia
    ) {
        return new DatosRegistroMantenimiento(
                "MANTENIMIENTO_GENERAL",
                LocalDate.of(2026, 10, 26),
                21000,
                origen,
                referencia,
                null,
                null,
                null,
                null
        );
    }
}
