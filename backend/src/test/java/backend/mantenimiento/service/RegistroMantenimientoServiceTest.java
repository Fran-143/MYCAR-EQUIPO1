package backend.mantenimiento.service;

import backend.mantenimiento.dao.RegistroMantenimientoDao;
import backend.mantenimiento.model.DatosRegistroMantenimiento;
import backend.mantenimiento.model.DatosRegistroMantenimiento.OrigenKmServicio;
import backend.mantenimiento.model.DatosRepuesto;
import backend.mantenimiento.model.SolicitudRegistroMantenimiento;
import backend.mantenimiento.model.TipoMantenimiento;
import backend.mantenimiento.model.VehiculoMantenimiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistroMantenimientoServiceTest {

    private ValidadorRegistroMantenimiento validador;
    private ValidadorRepuesto validadorRepuesto;
    private VehiculoMantenimientoService vehiculos;
    private TipoMantenimientoService tipos;
    private LecturaKmMantenimientoService lecturas;
    private RegistroMantenimientoDao dao;
    private RegistroMantenimientoService servicio;

    @BeforeEach
    void preparar() {
        validador = mock(ValidadorRegistroMantenimiento.class);
        validadorRepuesto = mock(ValidadorRepuesto.class);
        vehiculos = mock(VehiculoMantenimientoService.class);
        tipos = mock(TipoMantenimientoService.class);
        lecturas = mock(LecturaKmMantenimientoService.class);
        dao = mock(RegistroMantenimientoDao.class);

        servicio = new RegistroMantenimientoService(
                validador,
                validadorRepuesto,
                vehiculos,
                tipos,
                lecturas,
                dao
        );

        when(vehiculos.obtenerVehiculoPropioActivoParaRegistro(1L, 10L))
                .thenReturn(
                        new VehiculoMantenimiento(10L, 1L, "ACTIVO")
                );

        when(tipos.obtenerTipoActivo("MANTENIMIENTO_GENERAL"))
                .thenReturn(
                        new TipoMantenimiento(
                                5L,
                                "MANTENIMIENTO_GENERAL",
                                "Mantenimiento general",
                                true
                        )
                );
    }

    @Test
    void RF17_CP01_EF_validaYDelegaElGuardadoCompleto() {
        var solicitud = solicitudConRepuesto(1);

        when(dao.insertar(1L, 5L, solicitud)).thenReturn(900L);

        Long resultado = servicio.registrar(1L, solicitud);

        assertEquals(Long.valueOf(900L), resultado);

        verify(validador).validar(solicitud.datos());
        verify(validadorRepuesto).validar(
                solicitud.repuestos().get(0)
        );
        verify(vehiculos).obtenerVehiculoPropioActivoParaRegistro(1L, 10L);
        verify(tipos).obtenerTipoActivo("MANTENIMIENTO_GENERAL");
        verify(lecturas).validarReferencia(10L, solicitud.datos());
        verify(dao).insertar(1L, 5L, solicitud);
    }

    @Test
    void RF17_CP05_EA04_rechazoDeDatosImpideGuardar() {
        var solicitud = solicitudConRepuesto(1);

        doThrow(new IllegalArgumentException("Datos inválidos"))
                .when(validador)
                .validar(solicitud.datos());

        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.registrar(1L, solicitud)
        );

        verifyNoInteractions(dao);
    }

    @Test
    void RF17_CP06_EA05_vehiculoAjenoImpideGuardar() {
        var solicitud = solicitudConRepuesto(1);

        when(vehiculos.obtenerVehiculoPropioActivoParaRegistro(1L, 10L))
                .thenThrow(
                        new IllegalArgumentException(
                                "El vehículo no pertenece al usuario"
                        )
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.registrar(1L, solicitud)
        );

        verifyNoInteractions(dao);
    }

    @Test
    void RF17_CP05_EA04_repuestoInvalidoImpideGuardar() {
        var solicitud = solicitudConRepuesto(0);
        var repuesto = solicitud.repuestos().get(0);

        doThrow(new IllegalArgumentException("Cantidad inválida"))
                .when(validadorRepuesto)
                .validar(repuesto);

        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.registrar(1L, solicitud)
        );

        verifyNoInteractions(dao);
    }

    @Test
    void RF17_CP05_EA04_servicioOtroConTipoComunNoLlegaAlDao() {
        var datos = new DatosRegistroMantenimiento(
                "CAMBIO_ACEITE",
                LocalDate.of(2026, 10, 26),
                21000,
                OrigenKmServicio.LECTURA,
                701L,
                null,
                "Revisión especial",
                null,
                null
        );
        var solicitud = new SolicitudRegistroMantenimiento(
                UUID.randomUUID(), 10L, datos, null, List.of()
        );
        var servicioConValidadorReal = new RegistroMantenimientoService(
                new ValidadorRegistroMantenimiento(Clock.fixed(
                        Instant.parse("2026-10-26T15:00:00Z"),
                        ZoneId.of("America/Lima")
                )),
                validadorRepuesto, vehiculos, tipos, lecturas, dao
        );

        assertThrows(IllegalArgumentException.class,
                () -> servicioConValidadorReal.registrar(1L, solicitud));
        verifyNoInteractions(dao);
    }

    @Test
    void RF17_CP05_EA04_rechazaSolicitudAusenteAntesDePersistir() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(1L, null));
        assertTrue(error.getMessage().contains("solicitud"));
        verifyNoInteractions(dao);
    }

    @Test
    void RF17_CP05_EA04_rechazaVehiculoAusenteAntesDePersistir() {
        var original = solicitudConRepuesto(1);
        var solicitud = new SolicitudRegistroMantenimiento(
                original.idOperacion(), null, original.datos(),
                original.observaciones(), original.repuestos()
        );

        var error = assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(1L, solicitud));
        assertTrue(error.getMessage().contains("idVehiculo"));
        verifyNoInteractions(dao);
    }

    @Test
    void RF17_CP05_EA04_rechazaDatosAusentesAntesDePersistir() {
        var solicitud = new SolicitudRegistroMantenimiento(
                UUID.randomUUID(), 10L, null, null, List.of()
        );

        var error = assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(1L, solicitud));
        assertTrue(error.getMessage().contains("datos"));
        verifyNoInteractions(dao);
    }

    @Test
    void RF17_CP05_EA04_tipoEnBlancoNoConsultaCatalogoNiInserta() {
        var datos = new DatosRegistroMantenimiento(
                "   ", LocalDate.of(2026, 10, 26), 21000,
                OrigenKmServicio.LECTURA, 701L,
                null, null, null, null
        );
        var solicitud = new SolicitudRegistroMantenimiento(
                UUID.randomUUID(), 10L, datos, null, List.of()
        );
        var servicioConValidadorReal = new RegistroMantenimientoService(
                new ValidadorRegistroMantenimiento(Clock.fixed(
                        Instant.parse("2026-10-26T15:00:00Z"),
                        ZoneId.of("America/Lima")
                )),
                validadorRepuesto, vehiculos, tipos, lecturas, dao
        );

        assertThrows(IllegalArgumentException.class,
                () -> servicioConValidadorReal.registrar(1L, solicitud));
        verifyNoInteractions(tipos, dao);
    }

    @Test
    void RF17_CP05_EA04_rechazaLineaNulaAntesDePersistir() {
        var original = solicitudConRepuesto(1);
        var solicitud = new SolicitudRegistroMantenimiento(
                original.idOperacion(), 10L, original.datos(),
                original.observaciones(), Arrays.asList((DatosRepuesto) null)
        );
        var servicioConValidadorReal = new RegistroMantenimientoService(
                validador, new ValidadorRepuesto(), vehiculos,
                tipos, lecturas, dao
        );

        assertThrows(IllegalArgumentException.class,
                () -> servicioConValidadorReal.registrar(1L, solicitud));
        verifyNoInteractions(dao);
    }

    @Test
    void RF17_CP04_EA03_delegaListaFinalConNombresRepetidos() {
        var original = solicitudConRepuesto(1);
        var primero = original.repuestos().get(0);
        var segundo = new DatosRepuesto(
                primero.nombre(), null, 3, null
        );
        var solicitud = new SolicitudRegistroMantenimiento(
                original.idOperacion(), original.idVehiculo(),
                original.datos(), original.observaciones(),
                List.of(primero, segundo)
        );
        when(dao.insertar(1L, 5L, solicitud)).thenReturn(900L);

        assertEquals(900L, servicio.registrar(1L, solicitud));
        verify(validadorRepuesto).validar(primero);
        verify(validadorRepuesto).validar(segundo);
        verify(dao, times(1)).insertar(1L, 5L, solicitud);
        assertEquals(2, solicitud.repuestos().size());
    }

    @Test
    void RF17_CP04_EA03_delegaSoloRepuestoDeLaListaFinal() {
        var original = solicitudConRepuesto(1);
        var bujiaEditada = new DatosRepuesto(
                "Bujía", "NGK", 3, new BigDecimal("20.00")
        );
        // El filtro retirado del borrador no forma parte de esta solicitud.
        var solicitud = new SolicitudRegistroMantenimiento(
                original.idOperacion(), original.idVehiculo(),
                original.datos(), original.observaciones(),
                List.of(bujiaEditada)
        );
        when(dao.insertar(1L, 5L, solicitud)).thenReturn(902L);

        assertEquals(902L, servicio.registrar(1L, solicitud));
        verify(validadorRepuesto).validar(bujiaEditada);
        verify(validadorRepuesto, never()).validar(original.repuestos().get(0));
        verify(dao, times(1)).insertar(1L, 5L, solicitud);
    }

    @Test
    void RF17_CP01_EF_aceptaListaDeRepuestosOmitida() {
        var original = solicitudConRepuesto(1);
        var solicitud = new SolicitudRegistroMantenimiento(
                original.idOperacion(), original.idVehiculo(),
                original.datos(), null, null
        );
        when(dao.insertar(1L, 5L, solicitud)).thenReturn(901L);

        assertEquals(901L, servicio.registrar(1L, solicitud));
        verifyNoInteractions(validadorRepuesto);
        verify(dao).insertar(1L, 5L, solicitud);
    }

    @Test
    void RF17_CP06_EA05_tipoDesactivadoImpideInsercion() {
        var solicitud = solicitudConRepuesto(1);
        when(tipos.obtenerTipoActivo("MANTENIMIENTO_GENERAL"))
                .thenThrow(new IllegalArgumentException("Tipo inactivo"));

        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(1L, solicitud));
        verifyNoInteractions(dao);
    }

    @Test
    void RF17_CP05_EA04_lecturaInvalidaImpideInsercion() {
        var solicitud = solicitudConRepuesto(1);
        doThrow(new IllegalArgumentException("Lectura ajena"))
                .when(lecturas).validarReferencia(10L, solicitud.datos());

        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(1L, solicitud));
        verifyNoInteractions(dao);
    }

    private SolicitudRegistroMantenimiento solicitudConRepuesto(
            int cantidad
    ) {
        var datos = new DatosRegistroMantenimiento(
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

        var repuesto = new DatosRepuesto(
                "Filtro de aceite",
                "Bosch",
                cantidad,
                new BigDecimal("35.00")
        );

        return new SolicitudRegistroMantenimiento(
                UUID.randomUUID(),
                10L,
                datos,
                "Se realizó el mantenimiento general",
                List.of(repuesto)
        );
    }
}
