package backend.mantenimiento.dao;

import backend.mantenimiento.api.dto.ContratoRf17.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Consultas RF17 de solo lectura; no registra lecturas ni modifica RF14. */
@Repository
public class ContextoMantenimientoJdbcDao {
    private final JdbcTemplate jdbc;
    public ContextoMantenimientoJdbcDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<TipoDto> tiposActivos() {
        return jdbc.query("""
                SELECT id_tipo_mantenimiento, codigo, nombre
                FROM mycar.tipo_mantenimiento WHERE activo ORDER BY nombre
                """, (rs,n) -> new TipoDto(rs.getLong(1),rs.getString(2),rs.getString(3)));
    }

    public List<VehiculoDto> vehiculosActivos(long usuario) {
        return jdbc.query("""
                SELECT v.id_vehiculo, v.placa, v.marca, v.modelo, v.es_principal,
                       l.id_lectura_km, l.valor_km, l.fecha_lectura_utc,
                       CASE WHEN a.codigo = 'OTRO' THEN v.tipo_aceite_otro ELSE a.nombre END AS aceite
                FROM mycar.vehiculo v
                LEFT JOIN mycar.tipo_aceite a ON a.id_tipo_aceite = v.id_tipo_aceite
                LEFT JOIN LATERAL (
                    SELECT id_lectura_km, round(valor_km)::integer AS valor_km, fecha_lectura_utc
                    FROM mycar.lectura_kilometraje
                    WHERE id_vehiculo = v.id_vehiculo
                      AND origen IN ('INICIAL','ACTUALIZACION')
                    ORDER BY fecha_lectura_utc DESC, id_lectura_km DESC LIMIT 1
                ) l ON true
                WHERE v.id_usuario = ? AND v.estado_vehiculo = 'ACTIVO'
                ORDER BY v.es_principal DESC, v.id_vehiculo
                """, (rs,n) -> new VehiculoDto(
                rs.getLong("id_vehiculo"), rs.getString("placa"), rs.getString("marca"),
                rs.getString("modelo"), rs.getBoolean("es_principal"),
                rs.getObject("id_lectura_km", Long.class), enteroKm(rs.getObject("valor_km")),
                rs.getObject("fecha_lectura_utc", OffsetDateTime.class), rs.getString("aceite")), usuario);
    }

    public List<LecturaDto> lecturas(long vehiculo, LocalDate fecha) {
        return jdbc.query("""
                SELECT id_lectura_km, round(valor_km)::integer AS valor_km, fecha_lectura_utc
                FROM mycar.lectura_kilometraje
                WHERE id_vehiculo = ? AND origen IN ('INICIAL','ACTUALIZACION')
                  AND (fecha_lectura_utc AT TIME ZONE 'America/Lima')::date <= ?
                ORDER BY fecha_lectura_utc DESC, id_lectura_km DESC
                """, (rs,n) -> new LecturaDto(rs.getLong(1), rs.getInt(2),
                rs.getObject(3, OffsetDateTime.class)), vehiculo, fecha);
    }

    /** valor_km es NUMERIC en BD; getObject(..., Integer.class) falla en el driver PG. */
    private static Integer enteroKm(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return n.intValue();
        throw new IllegalStateException("valor_km inesperado: " + raw.getClass().getName());
    }
}
