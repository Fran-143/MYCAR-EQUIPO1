package backend.vehiculos.dao;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class CorregirKilometrajeDao {
    private final JdbcTemplate jdbc;

    public CorregirKilometrajeDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<VehiculoActivoResumen> consultarVehiculoPropio(long idUsuario, long idVehiculo) {
        return jdbc.query("""
                SELECT id_vehiculo, placa, marca, modelo, estado_vehiculo, version_km
                FROM mycar.vehiculo
                WHERE id_usuario = ? AND id_vehiculo = ?
                """, (rs, row) -> new VehiculoActivoResumen(
                rs.getLong("id_vehiculo"),
                rs.getString("placa"),
                rs.getString("marca"),
                rs.getString("modelo"),
                rs.getString("estado_vehiculo"),
                rs.getInt("version_km")
        ), idUsuario, idVehiculo).stream().findFirst();
    }

    public Optional<VehiculoActivoResumen> bloquearVehiculoPropio(long idUsuario, long idVehiculo) {
        return jdbc.query("""
                SELECT id_vehiculo, placa, marca, modelo, estado_vehiculo, version_km
                FROM mycar.vehiculo
                WHERE id_usuario = ? AND id_vehiculo = ?
                FOR UPDATE
                """, (rs, row) -> new VehiculoActivoResumen(
                rs.getLong("id_vehiculo"),
                rs.getString("placa"),
                rs.getString("marca"),
                rs.getString("modelo"),
                rs.getString("estado_vehiculo"),
                rs.getInt("version_km")
        ), idUsuario, idVehiculo).stream().findFirst();
    }

    /** Últimas lecturas efectivas (INICIAL/ACTUALIZACION), más reciente primero. */
    public List<LecturaEfectiva> listarLecturasEfectivas(long idVehiculo) {
        return jdbc.query("""
                SELECT id_lectura_km, valor_km, fecha_lectura_utc, origen
                FROM mycar.lectura_kilometraje
                WHERE id_vehiculo = ? AND origen IN ('INICIAL', 'ACTUALIZACION')
                ORDER BY fecha_lectura_utc DESC, id_lectura_km DESC
                """, (rs, row) -> new LecturaEfectiva(
                rs.getLong("id_lectura_km"),
                rs.getBigDecimal("valor_km"),
                rs.getTimestamp("fecha_lectura_utc").toInstant(),
                rs.getString("origen")
        ), idVehiculo);
    }

    public Optional<LecturaEfectiva> bloquearLectura(long idVehiculo, long idLectura) {
        return jdbc.query("""
                SELECT id_lectura_km, valor_km, fecha_lectura_utc, origen
                FROM mycar.lectura_kilometraje
                WHERE id_vehiculo = ? AND id_lectura_km = ?
                FOR UPDATE
                """, (rs, row) -> new LecturaEfectiva(
                rs.getLong("id_lectura_km"),
                rs.getBigDecimal("valor_km"),
                rs.getTimestamp("fecha_lectura_utc").toInstant(),
                rs.getString("origen")
        ), idVehiculo, idLectura).stream().findFirst();
    }

    public boolean yaFueCorregida(long idLectura) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.correccion_lectura_kilometraje
                WHERE id_lectura_km = ?
                """, Integer.class, idLectura);
        return n != null && n > 0;
    }

    /** Mantenimientos REALIZADOS con km superior al corregido (incompatibles). */
    public boolean hayMantenimientoIncompatible(long idVehiculo, BigDecimal kmCorregido) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.mantenimiento
                WHERE id_vehiculo = ? AND estado = 'REALIZADO' AND km_servicio > ?
                """, Integer.class, idVehiculo, kmCorregido);
        return n != null && n > 0;
    }

    public long registrarCorreccion(
            long idLectura,
            long idVehiculo,
            long idUsuario,
            BigDecimal kmAnterior,
            BigDecimal kmNuevo,
            BigDecimal kmPenultimo,
            String motivo,
            UUID idOperacion
    ) {
        Long id = jdbc.queryForObject("""
                INSERT INTO mycar.correccion_lectura_kilometraje(
                    id_lectura_km, id_vehiculo, id_usuario,
                    km_anterior, km_nuevo, km_penultimo, motivo, id_operacion)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id_correccion
                """, Long.class, idLectura, idVehiculo, idUsuario,
                kmAnterior, kmNuevo, kmPenultimo, motivo, idOperacion);
        return id == null ? 0L : id;
    }

    public Instant fechaCorreccion(long idCorreccion) {
        Timestamp ts = jdbc.queryForObject(
                "SELECT fecha_rectificacion_utc FROM mycar.correccion_lectura_kilometraje WHERE id_correccion = ?",
                Timestamp.class, idCorreccion);
        return ts == null ? null : ts.toInstant();
    }

    public void actualizarValorLectura(long idLectura, BigDecimal kmNuevo) {
        jdbc.execute("SELECT set_config('mycar.rf16_permitir', '1', true)");
        int filas = jdbc.update("""
                UPDATE mycar.lectura_kilometraje
                SET valor_km = ?
                WHERE id_lectura_km = ?
                """, kmNuevo, idLectura);
        if (filas != 1) {
            throw new IllegalStateException("No se actualizó la lectura RF16");
        }
    }

    public boolean incrementarVersionSiCoincide(long idVehiculo, int versionBase) {
        return jdbc.update(
                "UPDATE mycar.vehiculo SET version_km = version_km + 1 WHERE id_vehiculo = ? AND version_km = ?",
                idVehiculo, versionBase) == 1;
    }

    public record VehiculoActivoResumen(
            long idVehiculo, String placa, String marca, String modelo, String estadoVehiculo, int versionKm
    ) {}

    public record LecturaEfectiva(long idLectura, BigDecimal valorKm, Instant fechaLectura, String origen) {}
}
