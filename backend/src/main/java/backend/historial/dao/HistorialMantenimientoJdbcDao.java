package backend.historial.dao;

import backend.historial.model.AnulacionBloqueadaPorAlerta;
import backend.historial.model.DatosHistorial.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** RF18: únicamente SELECT. Incluye vehículos inactivos y tipos históricos desactivados. */
@Repository
public class HistorialMantenimientoJdbcDao implements HistorialMantenimientoDao {
    private final JdbcTemplate jdbc;
    private static final String VEHICULOS = """
            SELECT id_vehiculo, placa, marca, modelo, estado_vehiculo, es_principal
            FROM mycar.vehiculo
            WHERE id_usuario = ? AND estado_vehiculo IN ('ACTIVO', 'INACTIVO')
            """;

    public HistorialMantenimientoJdbcDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Vehiculo> vehiculosPropios(long usuario) {
        return jdbc.query(VEHICULOS + " ORDER BY es_principal DESC, id_vehiculo", this::vehiculo, usuario);
    }

    @Override
    public Optional<Vehiculo> vehiculoPropio(long usuario, long id) {
        return jdbc.query(VEHICULOS + " AND id_vehiculo = ?", this::vehiculo, usuario, id)
                .stream().findFirst();
    }

    @Override
    public List<Mantenimiento> mantenimientosPropios(long usuario, long vehiculo) {
        // El JOIN vuelve a limitar por propietario; no se obtiene el odómetro vigente.
        return jdbc.query("""
                SELECT m.id_mantenimiento, t.codigo, t.nombre, m.servicio_otro,
                       m.fecha_servicio, m.km_servicio, m.estado, m.costo_total,
                       coalesce(tal.nombre, m.nombre_taller) AS nombre_taller
                FROM mycar.mantenimiento m
                JOIN mycar.vehiculo v ON v.id_vehiculo = m.id_vehiculo
                JOIN mycar.tipo_mantenimiento t ON t.id_tipo_mantenimiento = m.id_tipo_mantenimiento
                LEFT JOIN mycar.taller tal ON tal.id_taller = m.id_taller
                WHERE v.id_usuario = ? AND v.id_vehiculo = ?
                  AND v.estado_vehiculo IN ('ACTIVO', 'INACTIVO')
                  AND m.estado IN ('REALIZADO', 'ANULADO')
                ORDER BY m.fecha_servicio DESC, m.id_mantenimiento DESC
                """, (r, n) -> new Mantenimiento(r.getLong("id_mantenimiento"),
                r.getString("codigo"), r.getString("nombre"), r.getString("servicio_otro"),
                r.getObject("fecha_servicio", LocalDate.class), r.getObject("km_servicio", Integer.class),
                r.getString("estado"), r.getBigDecimal("costo_total"), r.getString("nombre_taller")),
                usuario, vehiculo);
    }

    @Override
    public Optional<DetalleMantenimiento> detallePropio(long usuario, long vehiculo, long idMantenimiento) {
        var filas = jdbc.query("""
                SELECT m.id_mantenimiento, m.id_vehiculo, v.placa, v.marca, v.modelo, v.estado_vehiculo,
                       t.codigo, t.nombre, m.servicio_otro,
                       m.fecha_servicio, m.km_servicio, m.estado, m.costo_total,
                       m.descripcion, m.id_taller,
                       coalesce(tal.nombre, m.nombre_taller) AS nombre_taller,
                       m.proxima_fecha_sugerida, m.proximo_km_sugerido, m.anulado_utc
                FROM mycar.mantenimiento m
                JOIN mycar.vehiculo v ON v.id_vehiculo = m.id_vehiculo
                JOIN mycar.tipo_mantenimiento t ON t.id_tipo_mantenimiento = m.id_tipo_mantenimiento
                LEFT JOIN mycar.taller tal ON tal.id_taller = m.id_taller
                WHERE v.id_usuario = ? AND v.id_vehiculo = ? AND m.id_mantenimiento = ?
                  AND v.estado_vehiculo IN ('ACTIVO', 'INACTIVO')
                  AND m.estado IN ('REALIZADO', 'ANULADO')
                """, (r, n) -> {
            long id = r.getLong("id_mantenimiento");
            String estado = r.getString("estado");
            String estadoVehiculo = r.getString("estado_vehiculo");
            Long alerta = alertaActivaReferenciando(id).orElse(null);
            boolean realizado = "REALIZADO".equals(estado);
            boolean vehiculoActivo = "ACTIVO".equals(estadoVehiculo);
            boolean bloqueado = alerta != null;
            // RF20: editable solo REALIZADO + vehículo ACTIVO (independiente de alerta).
            // RF21: anulable REALIZADO + ACTIVO + sin alerta activa.
            // RF19: marca vive en mycar.repuesto (no hay marca_snapshot en mantenimiento_repuesto).
            List<RepuestoDetalle> reps = jdbc.query("""
                    SELECT mr.nombre_snapshot, r.marca, mr.cantidad, mr.precio_unitario
                    FROM mycar.mantenimiento_repuesto mr
                    JOIN mycar.repuesto r ON r.id_repuesto = mr.id_repuesto
                    WHERE mr.id_mantenimiento = ?
                    ORDER BY mr.nombre_snapshot
                    """, (rr, nn) -> new RepuestoDetalle(
                    rr.getString(1), rr.getString(2), rr.getInt(3), rr.getBigDecimal(4)), id);
            Timestamp anuladoTs = r.getTimestamp("anulado_utc");
            Instant anuladoUtc = anuladoTs == null ? null : anuladoTs.toInstant();
            Long idTaller = r.getObject("id_taller") == null ? null : r.getLong("id_taller");
            return new DetalleMantenimiento(
                    id, r.getLong("id_vehiculo"),
                    r.getString("placa"), r.getString("marca"), r.getString("modelo"), estadoVehiculo,
                    r.getString("codigo"), r.getString("nombre"),
                    r.getString("servicio_otro"), r.getObject("fecha_servicio", LocalDate.class),
                    r.getObject("km_servicio", Integer.class), estado, r.getBigDecimal("costo_total"),
                    r.getString("descripcion"), r.getString("nombre_taller"),
                    idTaller,
                    r.getObject("proxima_fecha_sugerida", LocalDate.class),
                    r.getObject("proximo_km_sugerido", Integer.class),
                    anuladoUtc,
                    realizado && vehiculoActivo,
                    realizado && vehiculoActivo && !bloqueado,
                    bloqueado, reps);
        }, usuario, vehiculo, idMantenimiento);
        return filas.stream().findFirst();
    }

    @Override
    public Optional<Long> alertaActivaReferenciando(long idMantenimiento) {
        return jdbc.query("""
                SELECT id_alerta FROM mycar.alerta
                WHERE id_mantenimiento_referencia = ?
                  AND estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
                ORDER BY id_alerta LIMIT 1
                """, (rs, n) -> rs.getLong(1), idMantenimiento).stream().findFirst();
    }

    @Override
    public boolean modificarCamposAutorizados(long usuario, long vehiculo, long idMantenimiento,
                                              java.math.BigDecimal costoTotal, String observaciones,
                                              Long idTaller, String nombreTaller) {
        // RF20 B1: escritura solo con vehículo ACTIVO y mantenimiento REALIZADO.
        return jdbc.update("""
                UPDATE mycar.mantenimiento m
                SET costo_total = ?, descripcion = ?, id_taller = ?, nombre_taller = ?
                FROM mycar.vehiculo v
                WHERE m.id_mantenimiento = ? AND m.id_vehiculo = ?
                  AND v.id_vehiculo = m.id_vehiculo AND v.id_usuario = ?
                  AND v.estado_vehiculo = 'ACTIVO'
                  AND m.estado = 'REALIZADO'
                """, costoTotal, observaciones, idTaller, nombreTaller,
                idMantenimiento, vehiculo, usuario) == 1;
    }

    @Override
    public boolean anularSiSinAlertaActiva(long usuario, long vehiculo, long idMantenimiento) {
        // Bloqueo pesimista del mantenimiento; RF21-EA04 frente a RF22 concurrente.
        Integer ok = jdbc.query("""
                SELECT 1 FROM mycar.mantenimiento m
                JOIN mycar.vehiculo v ON v.id_vehiculo = m.id_vehiculo
                WHERE m.id_mantenimiento = ? AND m.id_vehiculo = ?
                  AND v.id_usuario = ? AND m.estado = 'REALIZADO'
                  AND v.estado_vehiculo = 'ACTIVO'
                FOR UPDATE OF m
                """, rs -> rs.next() ? 1 : null, idMantenimiento, vehiculo, usuario);
        if (ok == null) return false;
        Optional<Long> alerta = alertaActivaReferenciando(idMantenimiento);
        if (alerta.isPresent()) {
            throw new AnulacionBloqueadaPorAlerta(alerta.get());
        }
        return jdbc.update("""
                UPDATE mycar.mantenimiento
                SET estado = 'ANULADO', anulado_utc = clock_timestamp()
                WHERE id_mantenimiento = ? AND id_vehiculo = ? AND estado = 'REALIZADO'
                """, idMantenimiento, vehiculo) == 1;
    }

    private Vehiculo vehiculo(ResultSet r, int n) throws SQLException {
        return new Vehiculo(r.getLong("id_vehiculo"), r.getString("placa"), r.getString("marca"),
                r.getString("modelo"), r.getString("estado_vehiculo"), r.getBoolean("es_principal"));
    }
}
