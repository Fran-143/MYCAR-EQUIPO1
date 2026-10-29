package backend.historial.dao;

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
                       m.fecha_servicio, m.km_servicio, m.estado, m.costo_total
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
                r.getString("estado"), r.getBigDecimal("costo_total")), usuario, vehiculo);
    }

    private Vehiculo vehiculo(ResultSet r, int n) throws SQLException {
        return new Vehiculo(r.getLong("id_vehiculo"), r.getString("placa"), r.getString("marca"),
                r.getString("modelo"), r.getString("estado_vehiculo"), r.getBoolean("es_principal"));
    }
}
