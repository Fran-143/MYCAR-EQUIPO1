package backend.alertas.programacion.dao;

import backend.alertas.programacion.model.ProgramacionAlerta.*;
import backend.alertas.programacion.model.ErrorProgramacion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Date;
import java.util.*;

@Repository
public class ProgramacionAlertaJdbcDao implements ProgramacionAlertaDao {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ProgramacionAlertaJdbcDao(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public void exigirCuentaActiva(long usuario) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM mycar.usuario WHERE id_usuario=? AND estado_cuenta='ACTIVA')",Boolean.class,usuario))) throw new ErrorProgramacion("SESION_REQUERIDA");
    }
    public List<Vehiculo> vehiculosActivos(long usuario) {
        return jdbc.query("""
            SELECT v.id_vehiculo,v.placa,v.marca,v.modelo,v.es_principal,
              CASE WHEN a.codigo='OTRO' THEN v.tipo_aceite_otro ELSE a.nombre END AS aceite
            FROM mycar.vehiculo v LEFT JOIN mycar.tipo_aceite a ON a.id_tipo_aceite=v.id_tipo_aceite
            WHERE v.id_usuario=? AND v.estado_vehiculo='ACTIVO' ORDER BY v.es_principal DESC,v.id_vehiculo
            """,(rs,n)->new Vehiculo(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBoolean(5),rs.getString(6)),usuario);
    }
    public List<Tipo> tiposActivos() {
        return jdbc.query("SELECT id_tipo_mantenimiento,codigo,nombre FROM mycar.tipo_mantenimiento WHERE activo ORDER BY nombre",(rs,n)->new Tipo(rs.getLong(1),rs.getString(2),rs.getString(3)));
    }
    public boolean vehiculoPropioActivo(long usuario,long vehiculo,boolean bloquear) {
        return !jdbc.query("SELECT id_vehiculo FROM mycar.vehiculo WHERE id_usuario=? AND id_vehiculo=? AND estado_vehiculo='ACTIVO'"+(bloquear?" FOR UPDATE":""),(rs,n)->rs.getLong(1),usuario,vehiculo).isEmpty();
    }
    public Optional<Tipo> tipoActivo(long tipo,boolean bloquear) {
        return jdbc.query("SELECT id_tipo_mantenimiento,codigo,nombre FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento=? AND activo"+(bloquear?" FOR SHARE":""),(rs,n)->new Tipo(rs.getLong(1),rs.getString(2),rs.getString(3)),tipo).stream().findFirst();
    }
    public Optional<Lectura> ultimaLectura(long vehiculo) {
        return jdbc.query("SELECT id_lectura_km,valor_km FROM mycar.lectura_kilometraje WHERE id_vehiculo=? AND origen IN ('INICIAL','ACTUALIZACION') ORDER BY fecha_lectura_utc DESC,id_lectura_km DESC LIMIT 1",(rs,n)->new Lectura(rs.getLong(1),rs.getInt(2)),vehiculo).stream().findFirst();
    }
    public List<String> sugerencias(long vehiculo,long tipo) {
        return jdbc.query("""
            SELECT DISTINCT ON (mycar.normalizar_servicio_otro(servicio_otro)) servicio_otro
            FROM mycar.mantenimiento WHERE id_vehiculo=? AND id_tipo_mantenimiento=?
              AND estado='REALIZADO' AND servicio_otro IS NOT NULL
            ORDER BY mycar.normalizar_servicio_otro(servicio_otro),fecha_servicio DESC,id_mantenimiento DESC
            """,(rs,n)->rs.getString(1),vehiculo,tipo);
    }
    public Optional<Referencia> ultimoRealizado(long vehiculo,long tipo,String otro) {
        return jdbc.query("""
            SELECT id_mantenimiento,fecha_servicio,km_servicio,servicio_otro FROM mycar.mantenimiento
            WHERE id_vehiculo=? AND id_tipo_mantenimiento=? AND estado='REALIZADO'
             AND (?::text IS NULL OR mycar.normalizar_servicio_otro(servicio_otro)=mycar.normalizar_servicio_otro(?))
            ORDER BY fecha_servicio DESC,id_mantenimiento DESC LIMIT 1
            """,(rs,n)->new Referencia(rs.getLong(1),rs.getDate(2).toLocalDate(),rs.getInt(3),rs.getString(4)),vehiculo,tipo,otro,otro).stream().findFirst();
    }
    public boolean referenciaCompatible(long vehiculo,long tipo,String otro,long referencia) {
        // Estado se revalida por ultimoRealizado; una referencia anulada puede retirarse en nueva revisión.
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM mycar.mantenimiento WHERE id_vehiculo=? AND id_tipo_mantenimiento=? AND id_mantenimiento=?
             AND (?::text IS NULL OR mycar.normalizar_servicio_otro(servicio_otro)=mycar.normalizar_servicio_otro(?)))
            """,Boolean.class,vehiculo,tipo,referencia,otro,otro));
    }
    public String normalizar(String servicio) { return servicio==null ? null : jdbc.queryForObject("SELECT mycar.normalizar_servicio_otro(?)",String.class,servicio); }
    public Optional<Long> equivalente(long vehiculo,String firma) {
        return jdbc.query("SELECT id_alerta FROM mycar.alerta WHERE id_vehiculo=? AND firma_equivalencia=? AND estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')",(rs,n)->rs.getLong(1),vehiculo,firma).stream().findFirst();
    }
    public long insertar(Revision r,String firma) {
        var c=r.configuracion();var b=r.bases();var o=r.objetivos();
        return Objects.requireNonNull(jdbc.queryForObject("""
            INSERT INTO mycar.alerta(id_vehiculo,id_tipo_mantenimiento,servicio_otro,criterio,unidad_tiempo,cantidad_tiempo,
             fecha_base,fecha_objetivo,km_base,intervalo_km,km_objetivo,id_mantenimiento_referencia,estado,firma_equivalencia)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id_alerta
            """,Long.class,c.idVehiculo(),c.idTipoMantenimiento(),c.servicioOtro(),c.criterio().name(),
            c.unidadTiempo()==null?null:c.unidadTiempo().name(),c.cantidadTiempo(),o.fechaObjetivo()==null?null:Date.valueOf(b.fechaBase()),
            o.fechaObjetivo()==null?null:Date.valueOf(o.fechaObjetivo()),b.kmBase(),c.intervaloKm(),o.kmObjetivo(),c.idMantenimientoReferencia(),o.estado(),firma));
    }
    public void historialInicial(long alerta,String estado) {
        jdbc.update("INSERT INTO mycar.historial_alerta(id_alerta,estado_anterior,estado_nuevo,motivo,version_destino) VALUES (?,NULL,?,'INICIAL_RF22',0)",alerta,estado);
    }
    public Optional<EstadoOperacion> consultarOperacion(long usuario,UUID id) {
        return jdbc.query("SELECT estado,respuesta_json::text FROM mycar.operacion_idempotente WHERE id_operacion=? AND id_usuario=? AND tipo_operacion='PROGRAMAR_ALERTA'",(rs,n)-> {
            try { return new EstadoOperacion(id,rs.getString(1),rs.getString(2)==null?null:json.readValue(rs.getString(2),Resultado.class)); }
            catch(Exception e) { throw new IllegalStateException("Respuesta de operación inválida"); }
        },id,usuario).stream().findFirst();
    }
}
