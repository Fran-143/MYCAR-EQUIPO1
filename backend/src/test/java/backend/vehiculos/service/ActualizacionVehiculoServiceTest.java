package backend.vehiculos.service;

import backend.vehiculos.dao.CatalogosVehiculoDao;
import backend.vehiculos.dao.CondicionesRegistroVehiculoDao;
import backend.vehiculos.dao.VehiculoDao;
import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.dto.DatosActualizacionVehiculo;
import backend.vehiculos.dto.DatosCatalogosVehiculo;
import backend.vehiculos.dto.DatosDetalleVehiculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActualizacionVehiculoServiceTest {
    CondicionesRegistroVehiculoDao condiciones;
    VehiculoDao vehiculos;
    CatalogosVehiculoDao catalogos;
    ConsultaRegistroVehiculoService consultas;
    PresenciaTiveVehiculo presenciaTive;
    ActualizacionVehiculoService servicio;

    @BeforeEach
    void preparar() {
        condiciones = mock(CondicionesRegistroVehiculoDao.class);
        vehiculos = mock(VehiculoDao.class);
        catalogos = mock(CatalogosVehiculoDao.class);
        consultas = mock(ConsultaRegistroVehiculoService.class);
        presenciaTive = mock(PresenciaTiveVehiculo.class);
        when(catalogos.consultar()).thenReturn(cats());
        when(presenciaTive.existeParaVehiculo(anyLong())).thenReturn(false);
        servicio = new ActualizacionVehiculoService(
                condiciones, vehiculos, catalogos, consultas, presenciaTive);
    }

    @Test
    void actualizaDatosGeneralesDeVehiculoActivoPropio() {
        when(vehiculos.obtenerEditablePropio(17, 5)).thenReturn(Optional.of(editable("ACTIVO")));
        when(consultas.consultarDetalle(17, 5)).thenReturn(detalle());
        var resultado = servicio.actualizar(17, 5, datos("Rojo", 2021));
        assertEquals("Rojo", resultado.color());
        verify(vehiculos).actualizarDatosGenerales(eq(17L), eq(5L), argThat(d ->
                "Rojo".equals(d.color()) && d.anioFabricacion() == 2021));
    }

    @Test
    void rechazaVehiculoInactivo() {
        when(vehiculos.obtenerEditablePropio(17, 5)).thenReturn(Optional.of(editable("INACTIVO")));
        var ex = assertThrows(ErrorRegistroVehiculo.class,
                () -> servicio.actualizar(17, 5, datos("Rojo", 2020)));
        assertEquals("VEHICULO_INACTIVO", ex.codigo());
        verify(vehiculos, never()).actualizarDatosGenerales(anyLong(), anyLong(), any());
    }

    @Test
    void rechazaVehiculoAjenoOInexistente() {
        when(vehiculos.obtenerEditablePropio(17, 99)).thenReturn(Optional.empty());
        var ex = assertThrows(ErrorRegistroVehiculo.class,
                () -> servicio.actualizar(17, 99, datos("Rojo", 2020)));
        assertEquals("VEHICULO_NO_ENCONTRADO", ex.codigo());
    }

    @Test
    void rechazaVinInvalido() {
        when(vehiculos.obtenerEditablePropio(17, 5)).thenReturn(Optional.of(editable("ACTIVO")));
        var invalidos = new DatosActualizacionVehiculo(
                "Toyota", "Corolla", "PARTICULAR", 2020, "Azul", 1L, 1L,
                "1HGCM82633I004352", null, null, null, null, null, null);
        var ex = assertThrows(ErrorRegistroVehiculo.class,
                () -> servicio.actualizar(17, 5, invalidos));
        assertEquals("DATOS_INVALIDOS", ex.codigo());
        assertTrue(ex.campos().contains("numeroVin"));
    }

    @Test
    void conTiveBloqueaCambioDeVinMotorSerie() {
        when(vehiculos.obtenerEditablePropio(17, 5)).thenReturn(Optional.of(editable("ACTIVO")));
        when(presenciaTive.existeParaVehiculo(5)).thenReturn(true);
        var cambio = new DatosActualizacionVehiculo(
                "Toyota", "Corolla", "PARTICULAR", 2020, "Azul", 1L, 1L,
                "1HGCM82633A004352", null, null, null, null, null, null);
        var ex = assertThrows(ErrorRegistroVehiculo.class,
                () -> servicio.actualizar(17, 5, cambio));
        assertEquals("IDENTIFICADORES_BLOQUEADOS_TIVE", ex.codigo());
        verify(vehiculos, never()).actualizarDatosGenerales(anyLong(), anyLong(), any());
    }

    @Test
    void rechazaVinDuplicadoGlobal() {
        when(vehiculos.obtenerEditablePropio(17, 5)).thenReturn(Optional.of(editable("ACTIVO")));
        when(vehiculos.existeVinEnOtro("1HGCM82633A004352", 5)).thenReturn(true);
        var cambio = new DatosActualizacionVehiculo(
                "Toyota", "Corolla", "PARTICULAR", 2020, "Azul", 1L, 1L,
                "1HGCM82633A004352", null, null, null, null, null, null);
        var ex = assertThrows(ErrorRegistroVehiculo.class,
                () -> servicio.actualizar(17, 5, cambio));
        assertEquals("VIN_DUPLICADO", ex.codigo());
        assertEquals(Set.of("numeroVin"), ex.campos());
    }

    @Test
    void rechazaSiCoincideConOtroVehiculoDelMismoUsuario() {
        when(vehiculos.obtenerEditablePropio(17, 5)).thenReturn(Optional.of(editable("ACTIVO")));
        when(vehiculos.existeVehiculoIdenticoEnOtro(eq(17L), eq(5L), any())).thenReturn(true);
        var cambio = datos("Rojo", 2021);
        var ex = assertThrows(ErrorRegistroVehiculo.class,
                () -> servicio.actualizar(17, 5, cambio));
        assertEquals("VEHICULO_DUPLICADO", ex.codigo());
        verify(vehiculos, never()).actualizarDatosGenerales(anyLong(), anyLong(), any());
    }

    @Test
    void rechazaSiNoHayCambiosRespectoAlActual() {
        when(vehiculos.obtenerEditablePropio(17, 5)).thenReturn(Optional.of(editable("ACTIVO")));
        var iguales = datos("Azul", 2020);
        var ex = assertThrows(ErrorRegistroVehiculo.class,
                () -> servicio.actualizar(17, 5, iguales));
        assertEquals("SIN_CAMBIOS", ex.codigo());
        verify(vehiculos, never()).actualizarDatosGenerales(anyLong(), anyLong(), any());
    }

    private DatosActualizacionVehiculo datos(String color, int anio) {
        return new DatosActualizacionVehiculo(
                "Toyota", "Corolla", "PARTICULAR", anio, color, 1L, 1L,
                null, null, null, null, null, null, null);
    }

    private VehiculoDao.VehiculoEditable editable(String estado) {
        return new VehiculoDao.VehiculoEditable(
                5, "A1B234", estado, "Toyota", "Corolla", "PARTICULAR", 2020, "Azul",
                1L, null, 1L, null, null, null, null, null, null);
    }

    private DatosDetalleVehiculo detalle() {
        return new DatosDetalleVehiculo(
                5, "A1B234", "Toyota", "Corolla", 2021, "Particular", "Rojo",
                "Combustión", "Gasolina", "ACTIVO", true, java.math.BigDecimal.valueOf(50000), "2020-01-01T00:00:00Z",
                null, null, null, null, false, "PARTICULAR", 1L, 1L, null, null, null, null, false);
    }

    private DatosCatalogosVehiculo cats() {
        return new DatosCatalogosVehiculo(
                List.of(new DatosCatalogosVehiculo.UsoVehiculo("PARTICULAR", "Particular")),
                List.of(new DatosCatalogosVehiculo.TipoMotor(1L, "COMBUSTION", "Combustión", false)),
                List.of(new DatosCatalogosVehiculo.Combustible(1L, "GASOLINA", "Gasolina")),
                List.of()
        );
    }
}
