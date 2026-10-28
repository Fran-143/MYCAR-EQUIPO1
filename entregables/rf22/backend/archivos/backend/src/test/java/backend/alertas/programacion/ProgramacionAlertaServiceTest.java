package backend.alertas.programacion;

import backend.alertas.programacion.dao.ProgramacionAlertaDao;
import backend.alertas.programacion.model.ProgramacionAlerta.*;
import backend.alertas.programacion.model.ErrorProgramacion;
import backend.alertas.programacion.service.*;
import backend.vehiculos.dao.OperacionVehiculoDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProgramacionAlertaServiceTest {
    ProgramacionAlertaDao dao;
    OperacionVehiculoDao operaciones;
    ProgramacionAlertaService servicio;
    Clock reloj;
    String huella;
    @BeforeEach void preparar() {
        dao=mock(ProgramacionAlertaDao.class); operaciones=mock(OperacionVehiculoDao.class); reloj=mock(Clock.class);
        when(reloj.getZone()).thenReturn(ZoneId.of("America/Lima")); when(reloj.instant()).thenReturn(Instant.parse("2026-01-31T17:00:00Z"));
        when(dao.vehiculoPropioActivo(anyLong(),anyLong(),anyBoolean())).thenReturn(true);
        when(dao.tipoActivo(anyLong(),anyBoolean())).thenReturn(Optional.of(new Tipo(2,"MANTENIMIENTO_GENERAL","Mantenimiento general")));
        when(dao.ultimaLectura(anyLong())).thenReturn(Optional.of(new Lectura(3,50000)));
        when(dao.equivalente(anyLong(),anyString())).thenReturn(Optional.empty());
        when(dao.normalizar(anyString())).thenAnswer(i->i.<String>getArgument(0).strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," "));
        when(dao.insertar(any(),anyString())).thenReturn(7L);
        when(operaciones.reservar(any(),anyLong(),anyString(),anyString())).thenAnswer(i->{huella=i.getArgument(3);return true;});
        when(operaciones.buscarParaActualizar(any(),anyLong(),anyString())).thenAnswer(i->Optional.of(new OperacionVehiculoDao.DatosOperacion(huella,"EN_PROCESO",null)));
        when(operaciones.confirmar(any(),anyLong(),anyString(),anyString(),anyString())).thenReturn(true);
        servicio=new ProgramacionAlertaService(dao,operaciones,new CalculadorProgramacion(),new ObjectMapper().registerModule(new JavaTimeModule()),reloj);
    }
    Configuracion tiempo() {return new Configuracion(1L,2L,null,Criterio.POR_TIEMPO,Unidad.MESES,1,null,null);}
    Solicitud solicitud() {return new Solicitud(UUID.randomUUID(),servicio.revisar(8,tiempo()));}
    @Test void tiempoNoExigeNiConsultaLecturaParaGuardar() {
        var r=servicio.confirmar(8,solicitud());
        assertEquals(7,r.idAlerta());assertTrue(r.eventoCreacionRegistrado());
        verify(dao,never()).ultimaLectura(anyLong());verify(dao).historialInicial(7,"PROXIMA");
    }
    @Test void cambiaDiaSolicitaRevisionSinInsertar() {
        var s=solicitud();when(reloj.instant()).thenReturn(Instant.parse("2026-02-01T17:00:00Z"));
        var error=assertThrows(ErrorProgramacion.class,()->servicio.confirmar(8,s));
        assertEquals("REVISION_CAMBIO",error.codigo());assertEquals(LocalDate.of(2026,3,1),error.revision().objetivos().fechaObjetivo());
        verify(dao,never()).insertar(any(),anyString());
    }
    @Test void cambiaKmSolicitaConfirmacionNueva() {
        var c=new Configuracion(1L,2L,null,Criterio.POR_KILOMETRAJE,null,null,5000,null);
        var s=new Solicitud(UUID.randomUUID(),servicio.revisar(8,c));
        when(dao.ultimaLectura(1)).thenReturn(Optional.of(new Lectura(4,50200)));
        var e=assertThrows(ErrorProgramacion.class,()->servicio.confirmar(8,s));
        assertEquals(55200,e.revision().objetivos().kmObjetivo());verify(dao,never()).historialInicial(anyLong(),anyString());
    }
    @Test void otroVacioNoConsultaEquivalenciaNiInserta() {
        when(dao.tipoActivo(anyLong(),anyBoolean())).thenReturn(Optional.of(new Tipo(2,"OTRO","Otro")));
        var c=new Configuracion(1L,2L,"   ",Criterio.POR_TIEMPO,Unidad.MESES,1,null,null);
        assertThrows(IllegalArgumentException.class,()->servicio.revisar(8,c));
        verify(dao,never()).equivalente(anyLong(),anyString());
    }
    @Test void ausenciaAntecedenteNoBloqueaNiVinculaTextoLibre() {
        when(dao.tipoActivo(anyLong(),anyBoolean())).thenReturn(Optional.of(new Tipo(2,"OTRO","Otro")));
        var r=servicio.revisar(8,new Configuracion(1L,2L,"Cambio de correa",Criterio.POR_TIEMPO,Unidad.MESES,2,null,null));
        assertNull(r.referencia());verify(dao,never()).ultimoRealizado(anyLong(),anyLong(),any());
    }
    @Test void otroConNuloNoLlegaAPostgreSqlNiQuedaComoResultadoIncierto() {
        when(dao.tipoActivo(anyLong(),anyBoolean())).thenReturn(Optional.of(new Tipo(2,"OTRO","Otro")));
        var c=new Configuracion(1L,2L,"Cambio\u0000correa",Criterio.POR_TIEMPO,Unidad.MESES,2,null,null);
        assertThrows(IllegalArgumentException.class,()->servicio.revisar(8,c));
        verify(dao,never()).insertar(any(),anyString());
    }
    @Test void referenciaNuevaExigeReconfirmarYNoCambiaObjetivosDesdeHistorico() {
        when(dao.referenciaCompatible(anyLong(),anyLong(),any(),anyLong())).thenReturn(true);
        var primera=new Referencia(10,LocalDate.of(2025,1,1),10000,null);
        when(dao.ultimoRealizado(1,2,null)).thenReturn(Optional.of(primera));
        var c=new Configuracion(1L,2L,null,Criterio.POR_TIEMPO,Unidad.MESES,1,null,10L);
        var s=new Solicitud(UUID.randomUUID(),servicio.revisar(8,c));
        when(dao.ultimoRealizado(1,2,null)).thenReturn(Optional.of(new Referencia(11,LocalDate.of(2026,1,1),20000,null)));
        var e=assertThrows(ErrorProgramacion.class,()->servicio.confirmar(8,s));
        assertEquals(11,e.revision().configuracion().idMantenimientoReferencia());
        assertEquals(s.revision().objetivos(),e.revision().objetivos());
    }
    @Test void equivalenteSeRevalidaAntesDeInsertar() {
        var s=solicitud();when(dao.equivalente(anyLong(),anyString())).thenReturn(Optional.of(99L));
        assertEquals("ALERTA_EQUIVALENTE",assertThrows(ErrorProgramacion.class,()->servicio.confirmar(8,s)).codigo());
        verify(dao,never()).insertar(any(),anyString());
    }
    @Test void vehiculoAjenoOInactivoBloqueaAlta() {
        var s=solicitud();when(dao.vehiculoPropioActivo(8,1,true)).thenReturn(false);
        assertEquals("VEHICULO_NO_DISPONIBLE",assertThrows(ErrorProgramacion.class,()->servicio.confirmar(8,s)).codigo());
        verify(dao,never()).insertar(any(),anyString());
    }
    @Test void tipoDesactivadoAlConfirmarBloqueaAlta() {
        var s=solicitud();when(dao.tipoActivo(2,true)).thenReturn(Optional.empty());
        assertEquals("TIPO_NO_DISPONIBLE",assertThrows(ErrorProgramacion.class,()->servicio.confirmar(8,s)).codigo());
    }
    @Test void confirmadoSeRecuperaAunqueCambieFechaOCatalogo() throws Exception {
        var s=solicitud();var resultado=new Resultado(s.idOperacion(),7,1,"Mantenimiento general",Criterio.POR_TIEMPO,LocalDate.of(2026,2,28),null,"PROXIMA",true);
        String texto=new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(resultado);
        when(operaciones.buscarParaActualizar(any(),anyLong(),anyString())).thenAnswer(i->Optional.of(new OperacionVehiculoDao.DatosOperacion(huella,"CONFIRMADA",texto)));
        when(dao.tipoActivo(anyLong(),anyBoolean())).thenReturn(Optional.empty());
        when(reloj.instant()).thenReturn(Instant.parse("2026-03-02T17:00:00Z"));
        assertEquals(resultado,servicio.confirmar(8,s));verify(dao,never()).insertar(any(),anyString());
    }
    @Test void mismoIdContenidoDiferenteEsConflicto() {
        var s=solicitud();when(operaciones.buscarParaActualizar(any(),anyLong(),anyString())).thenReturn(Optional.of(new OperacionVehiculoDao.DatosOperacion("diferente","CONFIRMADA","{}")));
        assertEquals("CONFLICTO_IDEMPOTENCIA",assertThrows(ErrorProgramacion.class,()->servicio.confirmar(8,s)).codigo());
    }
}
