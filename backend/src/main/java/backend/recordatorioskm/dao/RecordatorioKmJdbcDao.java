package backend.recordatorioskm.dao;

import backend.recordatorioskm.domain.FrecuenciaRecordatorioKm;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecordatorioKmJdbcDao {
    private final JdbcTemplate jdbc;

    public RecordatorioKmJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void asegurarConfigUsuario(long idUsuario) {
        jdbc.update("""
                INSERT INTO mycar.config_recordatorio_km_usuario(id_usuario, estado_recordatorios)
                VALUES (?, 'PAUSADO')
                ON CONFLICT (id_usuario) DO NOTHING
                """, idUsuario);
    }

    public String estadoGlobal(long idUsuario) {
        var filas = jdbc.query("""
                SELECT estado_recordatorios FROM mycar.config_recordatorio_km_usuario
                WHERE id_usuario = ?
                """, (rs, n) -> rs.getString(1), idUsuario);
        return filas.isEmpty() ? "PAUSADO" : filas.getFirst();
    }

    public String actualizarEstadoGlobal(long idUsuario, String estado) {
        asegurarConfigUsuario(idUsuario);
        jdbc.update("""
                UPDATE mycar.config_recordatorio_km_usuario
                SET estado_recordatorios = ?
                WHERE id_usuario = ?
                """, estado, idUsuario);
        return estadoGlobal(idUsuario);
    }

    public List<VehiculoConfigRow> listarVehiculosActivos(long idUsuario) {
        return jdbc.query("""
                SELECT v.id_vehiculo, v.marca, v.modelo, v.placa,
                       (v.foto_storage_object_key IS NOT NULL AND btrim(v.foto_storage_object_key) <> '') AS tiene_foto,
                       c.frecuencia, c.estado AS estado_config,
                       c.fecha_base_utc, c.proximo_envio_utc, c.ultimo_envio_utc,
                       (
                         SELECT l.valor_km FROM mycar.lectura_kilometraje l
                         WHERE l.id_vehiculo = v.id_vehiculo
                         ORDER BY l.fecha_lectura_utc DESC, l.id_lectura_km DESC
                         LIMIT 1
                       ) AS km_actual,
                       (
                         SELECT l.fecha_lectura_utc FROM mycar.lectura_kilometraje l
                         WHERE l.id_vehiculo = v.id_vehiculo
                         ORDER BY l.fecha_lectura_utc DESC, l.id_lectura_km DESC
                         LIMIT 1
                       ) AS fecha_ultima_lectura
                FROM mycar.vehiculo v
                LEFT JOIN mycar.config_recordatorio_km c ON c.id_vehiculo = v.id_vehiculo
                WHERE v.id_usuario = ?
                  AND v.estado_vehiculo = 'ACTIVO'
                ORDER BY v.es_principal DESC, v.marca, v.modelo, v.id_vehiculo
                """, (rs, n) -> new VehiculoConfigRow(
                rs.getLong("id_vehiculo"),
                rs.getString("marca"),
                rs.getString("modelo"),
                rs.getString("placa"),
                rs.getBoolean("tiene_foto"),
                rs.getString("frecuencia"),
                rs.getString("estado_config"),
                toInstant(rs.getObject("fecha_base_utc")),
                toInstant(rs.getObject("proximo_envio_utc")),
                toInstant(rs.getObject("ultimo_envio_utc")),
                (BigDecimal) rs.getObject("km_actual"),
                toInstant(rs.getObject("fecha_ultima_lectura"))), idUsuario);
    }

    public Optional<VehiculoPropio> vehiculoPropio(long idUsuario, long idVehiculo) {
        return jdbc.query("""
                SELECT id_vehiculo, estado_vehiculo, marca, modelo
                FROM mycar.vehiculo
                WHERE id_usuario = ? AND id_vehiculo = ?
                """, (rs, n) -> new VehiculoPropio(
                rs.getLong("id_vehiculo"),
                rs.getString("estado_vehiculo"),
                rs.getString("marca"),
                rs.getString("modelo")), idUsuario, idVehiculo).stream().findFirst();
    }

    public Optional<Instant> fechaUltimaLectura(long idVehiculo) {
        return jdbc.query("""
                SELECT fecha_lectura_utc FROM mycar.lectura_kilometraje
                WHERE id_vehiculo = ?
                ORDER BY fecha_lectura_utc DESC, id_lectura_km DESC
                LIMIT 1
                """, (rs, n) -> toInstant(rs.getObject("fecha_lectura_utc")), idVehiculo)
                .stream().findFirst().filter(i -> i != null);
    }

    public void guardarFrecuencia(
            long idVehiculo,
            FrecuenciaRecordatorioKm frecuencia,
            Instant fechaBase,
            Instant proximoEnvio
    ) {
        int n = jdbc.update("""
                UPDATE mycar.config_recordatorio_km
                SET frecuencia = ?,
                    estado = 'ACTIVO',
                    fecha_base_utc = ?,
                    proximo_envio_utc = ?
                WHERE id_vehiculo = ?
                """, frecuencia.codigo(), Timestamp.from(fechaBase),
                proximoEnvio == null ? null : Timestamp.from(proximoEnvio), idVehiculo);
        if (n == 0) {
            jdbc.update("""
                    INSERT INTO mycar.config_recordatorio_km(
                        id_vehiculo, frecuencia, estado, fecha_base_utc, proximo_envio_utc)
                    VALUES (?, ?, 'ACTIVO', ?, ?)
                    """, idVehiculo, frecuencia.codigo(),
                    Timestamp.from(fechaBase),
                    proximoEnvio == null ? null : Timestamp.from(proximoEnvio));
        }
    }

    public void recalcularTrasLectura(long idVehiculo, Instant fechaLectura, Instant proximoEnvio) {
        jdbc.update("""
                UPDATE mycar.config_recordatorio_km
                SET fecha_base_utc = ?,
                    proximo_envio_utc = ?
                WHERE id_vehiculo = ?
                """, Timestamp.from(fechaLectura),
                proximoEnvio == null ? null : Timestamp.from(proximoEnvio),
                idVehiculo);
    }

    public Optional<String> frecuenciaVehiculo(long idVehiculo) {
        return jdbc.query("""
                SELECT frecuencia FROM mycar.config_recordatorio_km WHERE id_vehiculo = ?
                """, (rs, n) -> rs.getString("frecuencia"), idVehiculo).stream().findFirst();
    }

    public List<PendienteJobRow> listarPendientesJob(Instant ahora) {
        return jdbc.query("""
                SELECT u.id_usuario, v.id_vehiculo, v.marca, v.modelo,
                       c.frecuencia, c.proximo_envio_utc
                FROM mycar.config_recordatorio_km c
                JOIN mycar.vehiculo v ON v.id_vehiculo = c.id_vehiculo
                JOIN mycar.config_recordatorio_km_usuario u ON u.id_usuario = v.id_usuario
                WHERE u.estado_recordatorios = 'ACTIVO'
                  AND v.estado_vehiculo = 'ACTIVO'
                  AND c.proximo_envio_utc IS NOT NULL
                  AND c.proximo_envio_utc <= ?
                ORDER BY c.proximo_envio_utc, v.id_vehiculo
                """, (rs, n) -> new PendienteJobRow(
                rs.getLong("id_usuario"),
                rs.getLong("id_vehiculo"),
                rs.getString("marca"),
                rs.getString("modelo"),
                rs.getString("frecuencia"),
                toInstant(rs.getObject("proximo_envio_utc"))), Timestamp.from(ahora));
    }

    public boolean insertarNotificacionKm(
            long idUsuario,
            long idVehiculo,
            String titulo,
            String cuerpo,
            String claveIdempotencia
    ) {
        int n = jdbc.update("""
                INSERT INTO mycar.notificacion(
                    id_usuario, categoria, id_alerta, id_vehiculo,
                    estado_gestion, leida, titulo, cuerpo, destino,
                    clave_idempotencia, fecha_emision_utc, fecha_actualizacion_utc)
                SELECT ?, 'KILOMETRAJE', NULL, ?, 'PENDIENTE', FALSE, ?, ?, 'ACTUALIZAR_KILOMETRAJE',
                       ?, clock_timestamp(), clock_timestamp()
                WHERE NOT EXISTS (
                    SELECT 1 FROM mycar.notificacion n WHERE n.clave_idempotencia = ?
                )
                """, idUsuario, idVehiculo, titulo, cuerpo, claveIdempotencia, claveIdempotencia);
        return n == 1;
    }

    public void marcarUltimoEnvio(long idVehiculo, Instant cuando) {
        jdbc.update("""
                UPDATE mycar.config_recordatorio_km
                SET ultimo_envio_utc = ?
                WHERE id_vehiculo = ?
                """, Timestamp.from(cuando), idVehiculo);
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.toInstant(ZoneOffset.UTC);
        }
        throw new IllegalStateException("Tipo temporal no soportado: " + value.getClass());
    }

    public record VehiculoConfigRow(
            long idVehiculo,
            String marca,
            String modelo,
            String placa,
            boolean tieneFoto,
            String frecuencia,
            String estadoConfig,
            Instant fechaBase,
            Instant proximoEnvio,
            Instant ultimoEnvio,
            BigDecimal kmActual,
            Instant fechaUltimaLectura
    ) {
    }

    public record VehiculoPropio(long idVehiculo, String estadoVehiculo, String marca, String modelo) {
    }

    public record PendienteJobRow(
            long idUsuario,
            long idVehiculo,
            String marca,
            String modelo,
            String frecuencia,
            Instant proximoEnvio
    ) {
    }

    public static LocalDate aFechaLima(Instant instant) {
        return instant.atZone(FrecuenciaRecordatorioKm.ZONA_LIMA).toLocalDate();
    }

    public static Instant inicioDiaLimaUtc(LocalDate fechaLima) {
        return fechaLima.atStartOfDay(FrecuenciaRecordatorioKm.ZONA_LIMA).toInstant();
    }
}
