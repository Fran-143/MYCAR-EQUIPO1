package backend.historial.dao;

import backend.historial.model.AnulacionBloqueadaPorAlerta;
import backend.historial.model.DatosHistorial.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
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
                       m.fecha_servicio, m.km_servicio, m.estado, m.costo_total, m.nombre_taller
                FROM mycar.mantenimiento m
                JOIN mycar.vehiculo v ON v.id_vehiculo = m.id_vehiculo
                JOIN mycar.tipo_mantenimiento t ON t.id_tipo_mantenimiento = m.id_tipo_mantenimiento
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
                SELECT m.id_mantenimiento, m.id_vehiculo, t.codigo, t.nombre, m.servicio_otro,
                       m.fecha_servicio, m.km_servicio, m.estado, m.costo_total,
                       m.descripcion, m.nombre_taller
                FROM mycar.mantenimiento m
                JOIN mycar.vehiculo v ON v.id_vehiculo = m.id_vehiculo
                JOIN mycar.tipo_mantenimiento t ON t.id_tipo_mantenimiento = m.id_tipo_mantenimiento
                WHERE v.id_usuario = ? AND v.id_vehiculo = ? AND m.id_mantenimiento = ?
                  AND v.estado_vehiculo IN ('ACTIVO', 'INACTIVO')
                  AND m.estado IN ('REALIZADO', 'ANULADO')
                """, (r, n) -> {
            long id = r.getLong("id_mantenimiento");
            String estado = r.getString("estado");
            Long alerta = alertaActivaReferenciando(id).orElse(null);
            boolean realizado = "REALIZADO".equals(estado);
            boolean bloqueado = alerta != null;
            List<RepuestoDetalle> reps = jdbc.query("""
                    SELECT nombre_snapshot, cantidad, precio_unitario
                    FROM mycar.mantenimiento_repuesto WHERE id_mantenimiento = ?
                    ORDER BY nombre_snapshot
                    """, (rr, nn) -> new RepuestoDetalle(
                    rr.getString(1), null, rr.getInt(2), rr.getBigDecimal(3)), id);
            return new DetalleMantenimiento(
                    id, r.getLong("id_vehiculo"), r.getString("codigo"), r.getString("nombre"),
                    r.getString("servicio_otro"), r.getObject("fecha_servicio", LocalDate.class),
                    r.getObject("km_servicio", Integer.class), estado, r.getBigDecimal("costo_total"),
                    r.getString("descripcion"), r.getString("nombre_taller"),
                    realizado, realizado && !bloqueado, bloqueado, reps);
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
                                              java.math.BigDecimal costoTotal, String observaciones, String taller) {
        return jdbc.update("""
                UPDATE mycar.mantenimiento m
                SET costo_total = ?, descripcion = ?, nombre_taller = ?
                FROM mycar.vehiculo v
                WHERE m.id_mantenimiento = ? AND m.id_vehiculo = ?
                  AND v.id_vehiculo = m.id_vehiculo AND v.id_usuario = ?
                  AND v.estado_vehiculo IN ('ACTIVO', 'INACTIVO')
                  AND m.estado = 'REALIZADO'
                """, costoTotal, observaciones, taller, idMantenimiento, vehiculo, usuario) == 1;
    }

    @Override
    public boolean anularSiSinAlertaActiva(long usuario, long vehiculo, long idMantenimiento) {
        // Bloqueo pesimista del mantenimiento; RF21-EA04 frente a RF22 concurrente.
        Integer ok = jdbc.query("""
                SELECT 1 FROM mycar.mantenimiento m
                JOIN mycar.vehiculo v ON v.id_vehiculo = m.id_vehiculo
                WHERE m.id_mantenimiento = ? AND m.id_vehiculo = ?
                  AND v.id_usuario = ? AND m.estado = 'REALIZADO'
                  AND v.estado_vehiculo IN ('ACTIVO', 'INACTIVO')
                FOR UPDATE OF m
                """, rs -> rs.next() ? 1 : null, idMantenimiento, vehiculo, usuario);
        if (ok == null) return false;
        Optional<Long> alerta = alertaActivaReferenciando(idMantenimiento);
        if (alerta.isPresent()) {
            throw new AnulacionBloqueadaPorAlerta(alerta.get());
        }
        return jdbc.update("""
                UPDATE mycar.mantenimiento SET estado = 'ANULADO'
                WHERE id_mantenimiento = ? AND id_vehiculo = ? AND estado = 'REALIZADO'
                """, idMantenimiento, vehiculo) == 1;
    }

    private Vehiculo vehiculo(ResultSet r, int n) throws SQLException {
        return new Vehiculo(r.getLong("id_vehiculo"), r.getString("placa"), r.getString("marca"),
                r.getString("modelo"), r.getString("estado_vehiculo"), r.getBoolean("es_principal"));
    }
}
