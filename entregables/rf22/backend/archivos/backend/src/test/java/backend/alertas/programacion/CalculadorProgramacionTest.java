package backend.alertas.programacion;

import backend.alertas.programacion.model.ProgramacionAlerta.*;
import backend.alertas.programacion.service.CalculadorProgramacion;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class CalculadorProgramacionTest {
    private final CalculadorProgramacion calculador = new CalculadorProgramacion();
    private Configuracion tiempo(Unidad unidad, int cantidad) {
        return new Configuracion(1L, 1L, null, Criterio.POR_TIEMPO, unidad, cantidad, null, null);
    }
    @Test void semanasSonSieteDiasYSinKmEsValido() {
        var r = calculador.calcular(tiempo(Unidad.SEMANAS, 1), LocalDate.of(2026,1,31), null);
        assertEquals(LocalDate.of(2026,2,7), r.fechaObjetivo());
        assertNull(r.kmObjetivo()); assertEquals("PENDIENTE", r.estado());
    }
    @Test void mesesCalendarioDesdeBaseOriginalYBisiesto() {
        assertEquals(LocalDate.of(2026,2,28), calculador.calcular(tiempo(Unidad.MESES,1),LocalDate.of(2026,1,31),null).fechaObjetivo());
        assertEquals(LocalDate.of(2024,2,29), calculador.calcular(tiempo(Unidad.MESES,1),LocalDate.of(2024,1,31),null).fechaObjetivo());
        assertEquals(LocalDate.of(2026,3,31), calculador.calcular(tiempo(Unidad.MESES,2),LocalDate.of(2026,1,31),null).fechaObjetivo());
    }
    @Test void kilometrajeActualMasIntervaloNuncaKmHistorico() {
        var c = new Configuracion(1L,1L,null,Criterio.POR_KILOMETRAJE,null,null,5000,null);
        var r = calculador.calcular(c,LocalDate.of(2026,1,1),50000);
        assertEquals(55000,r.kmObjetivo()); assertEquals("PROGRAMADA",r.estado());
    }
    @Test void ambosCreaUnObjetivoConDosCondicionesYEstadoMasAvanzado() {
        var c = new Configuracion(1L,1L,null,Criterio.AMBOS,Unidad.SEMANAS,1,5000,null);
        var r = calculador.calcular(c,LocalDate.of(2026,1,1),50000);
        assertEquals(55000,r.kmObjetivo()); assertEquals(LocalDate.of(2026,1,8),r.fechaObjetivo());
        assertEquals("PENDIENTE",r.estado());
    }
    @Test void kmYAmbosSinLecturaSeBloquean() {
        assertThrows(IllegalArgumentException.class,()->calculador.calcular(new Configuracion(1L,1L,null,Criterio.POR_KILOMETRAJE,null,null,1,null),LocalDate.now(),null));
        assertThrows(IllegalArgumentException.class,()->calculador.calcular(new Configuracion(1L,1L,null,Criterio.AMBOS,Unidad.MESES,1,1,null),LocalDate.now(),null));
    }
    @Test void intervalosNoPositivosYCamposDeOtroCriterioSeRechazan() {
        assertThrows(IllegalArgumentException.class,()->calculador.calcular(tiempo(Unidad.MESES,0),LocalDate.now(),null));
        assertThrows(IllegalArgumentException.class,()->calculador.calcular(tiempo(Unidad.SEMANAS,-1),LocalDate.now(),null));
        assertThrows(IllegalArgumentException.class,()->calculador.calcular(new Configuracion(1L,1L,null,Criterio.POR_TIEMPO,Unidad.MESES,1,10,null),LocalDate.now(),null));
    }
    @Test void excesoDeIntegerYDeFechaSeRechazaComoEntradaInvalida() {
        assertThrows(IllegalArgumentException.class,()->calculador.calcular(new Configuracion(1L,1L,null,Criterio.POR_KILOMETRAJE,null,null,10,null),LocalDate.now(),Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->calculador.calcular(tiempo(Unidad.SEMANAS,Integer.MAX_VALUE),LocalDate.now(),null));
    }
    @Test void limitesTemporalesDeInicio() {
        assertEquals("PROXIMA", calculador.calcular(tiempo(Unidad.SEMANAS,2),LocalDate.now(),null).estado());
        assertEquals("PROXIMA", calculador.calcular(tiempo(Unidad.SEMANAS,4),LocalDate.now(),null).estado());
        assertEquals("PROGRAMADA", calculador.calcular(tiempo(Unidad.SEMANAS,5),LocalDate.now(),null).estado());
    }
}
