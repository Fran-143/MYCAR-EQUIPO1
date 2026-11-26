package backend.historial.export;

import backend.historial.export.ExportarHistorialModelos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ExportarHistorialJdbcDao {
    private final JdbcTemplate jdbc;

    public ExportarHistorialJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CabeceraVehiculo> vehiculoExportable(long idUsuario, long idVehiculo) {
        return jdbc.query("""
                SELECT v.id_vehiculo, v.placa, v.marca, v.modelo, v.estado_vehiculo,
                       l.valor_km AS km_vigente
                FROM mycar.vehiculo v
                LEFT JOIN LATERAL (
                    SELECT valor_km
                    FROM mycar.lectura_kilometraje
                    WHERE id_vehiculo = v.id_vehiculo AND origen IN ('INICIAL','ACTUALIZACION')
                    ORDER BY fecha_lectura_utc DESC, id_lectura_km DESC
                    LIMIT 1
                ) l ON TRUE
                WHERE v.id_usuario = ? AND v.id_vehiculo = ?
                  AND v.estado_vehiculo IN ('ACTIVO', 'INACTIVO')
                  AND COALESCE(v.estado_registro, 'VIGENTE') = 'VIGENTE'
                """, (rs, n) -> new CabeceraVehiculo(
                rs.getLong("id_vehiculo"),
                rs.getString("placa"),
                rs.getString("marca"),
                rs.getString("modelo"),
                rs.getString("estado_vehiculo"),
                rs.getObject("km_vigente") == null ? null : rs.getBigDecimal("km_vigente").intValue()
        ), idUsuario, idVehiculo).stream().findFirst();
    }

    public List<TipoFiltro> tiposParaVehiculo(long idUsuario, long idVehiculo) {
        Map<Long, TipoFiltro> porId = new LinkedHashMap<>();
        jdbc.query("""
                SELECT t.id_tipo_mantenimiento, t.codigo, t.nombre
                FROM mycar.tipo_mantenimiento t
                WHERE t.activo = TRUE
                ORDER BY t.nombre
                """, (rs, n) -> {
            long id = rs.getLong(1);
            porId.putIfAbsent(id, new TipoFiltro(id, rs.getString(2), rs.getString(3)));
            return null;
        });
        jdbc.query("""
                SELECT DISTINCT t.id_tipo_mantenimiento, t.codigo, t.nombre
                FROM mycar.mantenimiento m
                JOIN mycar.vehiculo v ON v.id_vehiculo = m.id_vehiculo
                JOIN mycar.tipo_mantenimiento t ON t.id_tipo_mantenimiento = m.id_tipo_mantenimiento
                WHERE v.id_usuario = ? AND v.id_vehiculo = ?
                  AND v.estado_vehiculo IN ('ACTIVO', 'INACTIVO')
                  AND COALESCE(v.estado_registro, 'VIGENTE') = 'VIGENTE'
                  AND m.estado IN ('REALIZADO', 'ANULADO')
                ORDER BY t.nombre
                """, (rs, n) -> {
            long id = rs.getLong(1);
            porId.putIfAbsent(id, new TipoFiltro(id, rs.getString(2), rs.getString(3)));
            return null;
        }, idUsuario, idVehiculo);
        return new ArrayList<>(porId.values());
    }

    public boolean tipoAutorizado(long idUsuario, long idVehiculo, long idTipo) {
        Integer ok = jdbc.query("""
                SELECT 1 WHERE EXISTS (
                  SELECT 1 FROM mycar.tipo_mantenimiento t WHERE t.id_tipo_mantenimiento = ? AND t.activo = TRUE
                ) OR EXISTS (
                  SELECT 1 FROM mycar.mantenimiento m
                  JOIN mycar.vehiculo v ON v.id_vehiculo = m.id_vehiculo
                  WHERE v.id_usuario = ? AND v.id_vehiculo = ? AND m.id_tipo_mantenimiento = ?
                    AND m.estado IN ('REALIZADO', 'ANULADO')
                )
                """, rs -> rs.next() ? 1 : null, idTipo, idUsuario, idVehiculo, idTipo);
        return ok != null;
    }

    public List<MantenimientoExport> consultarExport(
            long idUsuario,
            long idVehiculo,
            LocalDate desde,
            LocalDate hasta,
            Long idTipo,
            boolean incluirAnulados
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT m.id_mantenimiento, m.id_tipo_mantenimiento, t.codigo, t.nombre, m.servicio_otro,
                       m.fecha_servicio, m.km_servicio, m.estado, m.costo_total, m.descripcion,
                       m.proxima_fecha_sugerida, m.proximo_km_sugerido,
                       coalesce(tal.nombre, m.nombre_taller) AS nombre_taller
                FROM mycar.mantenimiento m
                JOIN mycar.vehiculo v ON v.id_vehiculo = m.id_vehiculo
                JOIN mycar.tipo_mantenimiento t ON t.id_tipo_mantenimiento = m.id_tipo_mantenimiento
                LEFT JOIN mycar.taller tal ON tal.id_taller = m.id_taller
                WHERE v.id_usuario = ? AND v.id_vehiculo = ?
                  AND v.estado_vehiculo IN ('ACTIVO', 'INACTIVO')
                  AND COALESCE(v.estado_registro, 'VIGENTE') = 'VIGENTE'
                  AND m.fecha_servicio >= ? AND m.fecha_servicio <= ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(idUsuario);
        args.add(idVehiculo);
        args.add(desde);
        args.add(hasta);
        if (incluirAnulados) {
            sql.append(" AND m.estado IN ('REALIZADO', 'ANULADO')");
        } else {
            sql.append(" AND m.estado = 'REALIZADO'");
        }
        if (idTipo != null) {
            sql.append(" AND m.id_tipo_mantenimiento = ?");
            args.add(idTipo);
        }
        sql.append(" ORDER BY m.fecha_servicio DESC, m.id_mantenimiento DESC");

        List<MantenimientoExport> filas = jdbc.query(sql.toString(), (rs, n) -> {
            String rec = formatearRecomendacion(
                    rs.getObject("proxima_fecha_sugerida", LocalDate.class),
                    rs.getObject("proximo_km_sugerido", Integer.class));
            return new MantenimientoExport(
                    rs.getLong("id_mantenimiento"),
                    rs.getLong("id_tipo_mantenimiento"),
                    rs.getString("codigo"),
                    rs.getString("nombre"),
                    rs.getString("servicio_otro"),
                    rs.getObject("fecha_servicio", LocalDate.class),
                    rs.getObject("km_servicio", Integer.class),
                    rs.getString("estado"),
                    rs.getBigDecimal("costo_total"),
                    rs.getString("descripcion"),
                    rec,
                    rs.getString("nombre_taller"),
                    List.of()
            );
        }, args.toArray());

        if (filas.isEmpty()) {
            return filas;
        }
        Map<Long, List<RepuestoExport>> porMant = new HashMap<>();
        String in = String.join(",", filas.stream().map(m -> String.valueOf(m.idMantenimiento())).toList());
        String sqlRep = """
                SELECT mr.id_mantenimiento, mr.nombre_snapshot, r.marca, mr.cantidad, mr.precio_unitario
                FROM mycar.mantenimiento_repuesto mr
                JOIN mycar.repuesto r ON r.id_repuesto = mr.id_repuesto
                WHERE mr.id_mantenimiento IN (%s)
                ORDER BY mr.id_mantenimiento, mr.nombre_snapshot
                """.formatted(in);
        jdbc.query(sqlRep, (rs, n) -> {
            long id = rs.getLong(1);
            porMant.computeIfAbsent(id, k -> new ArrayList<>()).add(new RepuestoExport(
                    rs.getString(2), rs.getString(3), rs.getObject(4, Integer.class), rs.getBigDecimal(5)));
            return null;
        });

        return filas.stream().map(m -> new MantenimientoExport(
                m.idMantenimiento(), m.idTipo(), m.codigoTipo(), m.nombreTipo(), m.servicioOtro(),
                m.fechaServicio(), m.kmServicio(), m.estado(), m.costoTotal(), m.descripcion(),
                m.recomendaciones(), m.nombreTaller(),
                porMant.getOrDefault(m.idMantenimiento(), List.of())
        )).toList();
    }

    private static String formatearRecomendacion(LocalDate proxFecha, Integer proxKm) {
        if (proxFecha == null && proxKm == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (proxFecha != null) {
            sb.append("Próxima fecha: ").append(proxFecha);
        }
        if (proxKm != null) {
            if (!sb.isEmpty()) sb.append(" · ");
            sb.append("Próximo km: ").append(proxKm);
        }
        return sb.toString();
    }

    public record CabeceraVehiculo(
            long idVehiculo,
            String placa,
            String marca,
            String modelo,
            String estadoVehiculo,
            Integer kmVigente
    ) { }
}
