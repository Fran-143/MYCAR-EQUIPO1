package backend.alertas.programacion.service;

import backend.alertas.programacion.dao.ProgramacionAlertaDao;
import backend.alertas.programacion.model.ProgramacionAlerta.*;
import backend.alertas.programacion.model.ErrorProgramacion;
import backend.vehiculos.dao.OperacionVehiculoDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

@Service
public class ProgramacionAlertaService {
    private static final String TIPO="PROGRAMAR_ALERTA";
    private final ProgramacionAlertaDao dao;
    private final OperacionVehiculoDao operaciones;
    private final CalculadorProgramacion calculador;
    private final ObjectMapper json;
    private final Clock reloj;
    public ProgramacionAlertaService(ProgramacionAlertaDao dao,OperacionVehiculoDao operaciones,
        CalculadorProgramacion calculador,ObjectMapper json,@Qualifier("rf22Clock") Clock reloj) {
        this.dao=dao;this.operaciones=operaciones;this.calculador=calculador;this.json=json;this.reloj=reloj;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Contexto contexto(long usuario) {
        dao.exigirCuentaActiva(usuario);
        return new Contexto("cuenta-"+usuario,LocalDate.now(reloj),List.copyOf(dao.vehiculosActivos(usuario)),List.copyOf(dao.tiposActivos()));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Antecedentes antecedentes(long usuario,long vehiculo,long tipo,String servicioElegido) {
        dao.exigirCuentaActiva(usuario);
        if (!dao.vehiculoPropioActivo(usuario,vehiculo,false)) throw new ErrorProgramacion("VEHICULO_NO_DISPONIBLE");
        var t=dao.tipoActivo(tipo,false).orElseThrow(()->new ErrorProgramacion("TIPO_NO_DISPONIBLE"));
        var lectura=dao.ultimaLectura(vehiculo).orElse(null);
        boolean otro="OTRO".equals(t.codigo());
        Referencia ref=otro && servicioElegido==null ? null : dao.ultimoRealizado(vehiculo,tipo,otro?servicioElegido:null).orElse(null);
        return new Antecedentes(LocalDate.now(reloj),lectura==null?null:lectura.idLectura(),lectura==null?null:lectura.km(),
            otro?List.copyOf(dao.sugerencias(vehiculo,tipo)):List.of(),ref);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Revision revisar(long usuario,Configuracion c) {
        dao.exigirCuentaActiva(usuario);
        var r=calcular(usuario,c,false);
        if (dao.equivalente(c.idVehiculo(),firma(r)).isPresent()) throw new ErrorProgramacion("ALERTA_EQUIVALENTE");
        return r;
    }
    @Transactional
    public Resultado confirmar(long usuario,Solicitud solicitud) {
        dao.exigirCuentaActiva(usuario);
        if (solicitud==null || solicitud.idOperacion()==null || solicitud.revision()==null)
            throw new IllegalArgumentException("Revisa la operación y el resumen");
        String huella=sha(serializar(solicitud.revision()));
        boolean nueva=operaciones.reservar(solicitud.idOperacion(),usuario,TIPO,huella);
        var op=operaciones.buscarParaActualizar(solicitud.idOperacion(),usuario,TIPO)
            .orElseThrow(()->new ErrorProgramacion("CONFLICTO_IDEMPOTENCIA"));
        if (!huella.equals(op.hashSolicitud())) throw new ErrorProgramacion("CONFLICTO_IDEMPOTENCIA");
        // Primero recuperar el resultado confirmado: ni un catálogo desactivado ni otro día crean una alerta nueva.
        if ("CONFIRMADA".equals(op.estado())) return recuperar(op.respuestaJson(),solicitud.idOperacion());
        if (!nueva || !"EN_PROCESO".equals(op.estado())) throw new ErrorProgramacion("OPERACION_EN_PROCESO");
        var actual=calcular(usuario,solicitud.revision().configuracion(),true);
        if (!actual.equals(solicitud.revision())) throw new ErrorProgramacion("REVISION_CAMBIO",actual);
        String firma=firma(actual);
        if (dao.equivalente(actual.configuracion().idVehiculo(),firma).isPresent()) throw new ErrorProgramacion("ALERTA_EQUIVALENTE");
        long id=dao.insertar(actual,firma);
        dao.historialInicial(id,actual.objetivos().estado());
        var t=dao.tipoActivo(actual.configuracion().idTipoMantenimiento(),false).orElseThrow(()->new ErrorProgramacion("TIPO_NO_DISPONIBLE"));
        var resultado=new Resultado(solicitud.idOperacion(),id,actual.configuracion().idVehiculo(),
            "OTRO".equals(t.codigo())?actual.configuracion().servicioOtro():t.nombre(),actual.configuracion().criterio(),
            actual.objetivos().fechaObjetivo(),actual.objetivos().kmObjetivo(),actual.objetivos().estado(),true);
        if (!operaciones.confirmar(solicitud.idOperacion(),usuario,TIPO,huella,serializar(resultado)))
            throw new IllegalStateException("No se pudo confirmar la operación RF22");
        return resultado;
    }
    @Transactional(readOnly=true)
    public EstadoOperacion consultar(long usuario,UUID id) {
        dao.exigirCuentaActiva(usuario);
        if(id==null) throw new IllegalArgumentException("Operación requerida");
        return dao.consultarOperacion(usuario,id).orElse(new EstadoOperacion(id,"NO_ENCONTRADA",null));
    }
    private Revision calcular(long usuario,Configuracion c,boolean bloquear) {
        if(c==null || c.idVehiculo()==null || c.idTipoMantenimiento()==null || c.criterio()==null)
            throw new IllegalArgumentException("Selecciona vehículo, mantenimiento y criterio");
        if(!dao.vehiculoPropioActivo(usuario,c.idVehiculo(),bloquear)) throw new ErrorProgramacion("VEHICULO_NO_DISPONIBLE");
        var tipo=dao.tipoActivo(c.idTipoMantenimiento(),bloquear).orElseThrow(()->new ErrorProgramacion("TIPO_NO_DISPONIBLE"));
        boolean otro="OTRO".equals(tipo.codigo());
        if(otro) {
            if(c.servicioOtro()==null || c.servicioOtro().codePointCount(0,c.servicioOtro().length())>160
                || c.servicioOtro().codePoints().anyMatch(p->p==0 || Character.isISOControl(p) && !Character.isWhitespace(p))
                || c.servicioOtro().codePoints().noneMatch(Character::isLetterOrDigit)
                || dao.normalizar(c.servicioOtro()).isBlank()) throw new IllegalArgumentException("Especifica un servicio válido de hasta 160 caracteres");
        } else if(c.servicioOtro()!=null) throw new IllegalArgumentException("El servicio específico corresponde solo a OTRO");
        Referencia ref=null;
        Configuracion efectiva=c;
        if(c.idMantenimientoReferencia()!=null) {
            if(!dao.referenciaCompatible(c.idVehiculo(),c.idTipoMantenimiento(),otro?c.servicioOtro():null,c.idMantenimientoReferencia()))
                throw new ErrorProgramacion("REFERENCIA_NO_DISPONIBLE");
            ref=dao.ultimoRealizado(c.idVehiculo(),c.idTipoMantenimiento(),otro?c.servicioOtro():null).orElse(null);
            efectiva=new Configuracion(c.idVehiculo(),c.idTipoMantenimiento(),c.servicioOtro(),c.criterio(),c.unidadTiempo(),c.cantidadTiempo(),c.intervaloKm(),ref==null?null:ref.idMantenimiento());
        }
        // POR_TIEMPO no consulta ni exige kilometraje para validar/guardar.
        var lectura=c.criterio()==Criterio.POR_TIEMPO ? null : dao.ultimaLectura(c.idVehiculo()).orElseThrow(()->new ErrorProgramacion("KM_NO_REGISTRADO"));
        var fecha=LocalDate.now(reloj);
        var objetivos=calculador.calcular(efectiva,fecha,lectura==null?null:lectura.km());
        return new Revision(efectiva,new Bases(fecha,lectura==null?null:lectura.idLectura(),lectura==null?null:lectura.km()),objetivos,ref);
    }
    private String firma(Revision r) {
        // JSON de una lista fija: posiciones y NULL inequívocos. El texto original visible se conserva.
        return sha(serializar(Arrays.asList("rf22-equivalencia-v1",r.configuracion().idTipoMantenimiento(),
            r.configuracion().criterio().name(),r.objetivos().fechaObjetivo()==null?null:r.objetivos().fechaObjetivo().toString(),
            r.objetivos().kmObjetivo(),dao.normalizar(r.configuracion().servicioOtro()))));
    }
    private Resultado recuperar(String texto,UUID id) {
        try { var r=json.readValue(texto,Resultado.class); if(!id.equals(r.idOperacion())) throw new IllegalStateException(); return r; }
        catch(Exception e) { throw new IllegalStateException("Respuesta de operación RF22 inválida"); }
    }
    private String serializar(Object o) {
        try {return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException("No se pudo representar la operación RF22");}
    }
    private String sha(String texto) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException("No se pudo calcular la huella");}
    }
}
