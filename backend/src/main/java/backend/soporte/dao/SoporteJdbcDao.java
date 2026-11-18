package backend.soporte.dao;

import backend.soporte.api.dto.ContratoSoporte;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class SoporteJdbcDao {
    private final JdbcTemplate jdbc;

    public SoporteJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ContratoSoporte.VehiculoElegibleDto> vehiculosElegibles(long usuario) {
        return jdbc.query("""
                SELECT v.id_vehiculo, v.placa, v.marca, v.modelo,
                       COALESCE(
                           NULLIF(btrim(t.numero_vin), ''),
                           NULLIF(btrim(v.numero_vin), '')
                       ) AS numero_vin,
                       (t.id_tive IS NOT NULL) AS tiene_tive
                FROM mycar.vehiculo v
                LEFT JOIN mycar.tive t ON t.id_vehiculo = v.id_vehiculo
                WHERE v.id_usuario = ?
                  AND upper(v.estado_vehiculo) = 'ACTIVO'
                ORDER BY v.es_principal DESC, v.placa
                """, (rs, n) -> new ContratoSoporte.VehiculoElegibleDto(
                rs.getLong("id_vehiculo"),
                rs.getString("placa"),
                rs.getString("marca"),
                rs.getString("modelo"),
                rs.getString("numero_vin"),
                rs.getBoolean("tiene_tive")
        ), usuario);
    }

    public Optional<VehiculoCtx> vehiculoPropio(long usuario, long vehiculo) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT v.id_vehiculo, v.placa,
                           COALESCE(
                               NULLIF(btrim(t.numero_vin), ''),
                               NULLIF(btrim(v.numero_vin), '')
                           ) AS numero_vin,
                           (t.id_tive IS NOT NULL) AS tiene_tive
                    FROM mycar.vehiculo v
                    LEFT JOIN mycar.tive t ON t.id_vehiculo = v.id_vehiculo
                    WHERE v.id_vehiculo = ? AND v.id_usuario = ?
                    """, (rs, n) -> new VehiculoCtx(
                    rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)
            ), vehiculo, usuario));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public void bloquearVehiculo(long vehiculo) {
        exigirTx();
        jdbc.queryForObject(
                "SELECT 1 FROM mycar.vehiculo WHERE id_vehiculo = ? FOR UPDATE",
                Integer.class, vehiculo);
    }

    public boolean existeAbierta(long vehiculo, String tipo) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*)::int FROM mycar.solicitud_soporte
                WHERE id_vehiculo = ? AND tipo = ? AND estado IN ('PENDIENTE','EN_REVISION')
                """, Integer.class, vehiculo, tipo);
        return n != null && n > 0;
    }

    public Optional<String> codigoAbierta(long vehiculo, String tipo) {
        return jdbc.query("""
                SELECT codigo FROM mycar.solicitud_soporte
                WHERE id_vehiculo = ? AND tipo = ? AND estado IN ('PENDIENTE','EN_REVISION')
                ORDER BY id_solicitud DESC LIMIT 1
                """, (rs, n) -> rs.getString(1), vehiculo, tipo).stream().findFirst();
    }

    public boolean vinUsadoPorOtro(String vin, long excluirVehiculo) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*)::int FROM mycar.vehiculo
                WHERE numero_vin = ? AND id_vehiculo <> ?
                """, Integer.class, vin, excluirVehiculo);
        return n != null && n > 0;
    }

    public long insertarSolicitud(String codigo, String tipo, long usuario, long vehiculo) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.solicitud_soporte(codigo, tipo, estado, id_usuario, id_vehiculo)
                VALUES (?, ?, 'PENDIENTE', ?, ?)
                RETURNING id_solicitud
                """, Long.class, codigo, tipo, usuario, vehiculo);
    }

    public void insertarCorreccionVin(long idSolicitud, String vinReg, String vinProp, String motivo,
            String key, String contentType, Integer tamano, String nombre) {
        jdbc.update("""
                INSERT INTO mycar.solicitud_correccion_vin(
                    id_solicitud, vin_registrado, vin_propuesto, motivo,
                    storage_object_key, content_type, tamano_bytes, nombre_original)
                VALUES (?,?,?,?,?,?,?,?)
                """, idSolicitud, vinReg, vinProp, motivo, key, contentType, tamano, nombre);
    }

    public void insertarEvento(long idSolicitud, String ant, String neu, Long actor, String obs) {
        jdbc.update("""
                INSERT INTO mycar.solicitud_soporte_evento(
                    id_solicitud, estado_anterior, estado_nuevo, id_actor, observacion)
                VALUES (?,?,?,?,?)
                """, idSolicitud, ant, neu, actor, obs);
    }

    public List<ContratoSoporte.SolicitudResumenDto> listarPropias(long usuario) {
        return jdbc.query("""
                SELECT s.codigo, s.tipo, s.estado, s.id_vehiculo, v.placa, s.creado_utc, s.actualizado_utc
                FROM mycar.solicitud_soporte s
                JOIN mycar.vehiculo v ON v.id_vehiculo = s.id_vehiculo
                WHERE s.id_usuario = ?
                ORDER BY s.creado_utc DESC
                """, (rs, n) -> new ContratoSoporte.SolicitudResumenDto(
                rs.getString("codigo"), rs.getString("tipo"), rs.getString("estado"),
                rs.getLong("id_vehiculo"), rs.getString("placa"),
                ts(rs.getTimestamp("creado_utc")), ts(rs.getTimestamp("actualizado_utc"))
        ), usuario);
    }

    public Optional<FilaSolicitud> porCodigoPropia(long usuario, String codigo) {
        return porCodigo(codigo).filter(f -> f.idUsuario() == usuario);
    }

    public Optional<FilaSolicitud> porCodigo(String codigo) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(SQL_DETALLE, MAPPER_DETALLE, codigo));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private static final String SQL_DETALLE = """
            SELECT s.id_solicitud, s.codigo, s.tipo, s.estado, s.id_usuario, s.id_vehiculo,
                   s.version_fila, s.motivo_rechazo, s.creado_utc, s.actualizado_utc,
                   v.placa, v.marca, v.modelo,
                   u.nombre AS nombre_usuario, u.apellido AS apellido_usuario, u.correo AS correo_usuario,
                   c.vin_registrado, c.vin_propuesto, c.motivo,
                   c.storage_object_key, c.content_type, c.tamano_bytes, c.nombre_original
            FROM mycar.solicitud_soporte s
            JOIN mycar.vehiculo v ON v.id_vehiculo = s.id_vehiculo
            JOIN mycar.usuario u ON u.id_usuario = s.id_usuario
            JOIN mycar.solicitud_correccion_vin c ON c.id_solicitud = s.id_solicitud
            WHERE s.codigo = ?
            """;

    private static final org.springframework.jdbc.core.RowMapper<FilaSolicitud> MAPPER_DETALLE = (rs, n) -> {
        String nombre = rs.getString("nombre_usuario");
        String apellido = rs.getString("apellido_usuario");
        String nombreCompleto = ((nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido)).strip();
        return new FilaSolicitud(
                rs.getLong("id_solicitud"), rs.getString("codigo"), rs.getString("tipo"),
                rs.getString("estado"), rs.getLong("id_usuario"), rs.getLong("id_vehiculo"),
                rs.getInt("version_fila"), rs.getString("motivo_rechazo"),
                ts(rs.getTimestamp("creado_utc")), ts(rs.getTimestamp("actualizado_utc")),
                rs.getString("placa"), rs.getString("marca"), rs.getString("modelo"),
                nombreCompleto.isBlank() ? null : nombreCompleto, rs.getString("correo_usuario"),
                rs.getString("vin_registrado"), rs.getString("vin_propuesto"),
                rs.getString("motivo"), rs.getString("storage_object_key"), rs.getString("content_type"),
                (Integer) rs.getObject("tamano_bytes"), rs.getString("nombre_original")
        );
    };

    public List<ContratoSoporte.EventoDto> eventos(long idSolicitud) {
        return jdbc.query("""
                SELECT estado_anterior, estado_nuevo, observacion, ocurrido_utc
                FROM mycar.solicitud_soporte_evento
                WHERE id_solicitud = ?
                ORDER BY ocurrido_utc ASC, id_evento ASC
                """, (rs, n) -> new ContratoSoporte.EventoDto(
                rs.getString(1), rs.getString(2), rs.getString(3), ts(rs.getTimestamp(4))
        ), idSolicitud);
    }

    public List<ContratoSoporte.SolicitudResumenDto> listarAdmin(String estado, String q) {
        String like = (q == null || q.isBlank()) ? null : "%" + q.strip().toUpperCase() + "%";
        return jdbc.query("""
                SELECT s.codigo, s.tipo, s.estado, s.id_vehiculo, v.placa, s.creado_utc, s.actualizado_utc
                FROM mycar.solicitud_soporte s
                JOIN mycar.vehiculo v ON v.id_vehiculo = s.id_vehiculo
                WHERE (?::text IS NULL OR s.estado = ?)
                  AND (?::text IS NULL OR upper(s.codigo) LIKE ? OR upper(v.placa) LIKE ?)
                ORDER BY s.actualizado_utc DESC
                LIMIT 200
                """, (rs, n) -> new ContratoSoporte.SolicitudResumenDto(
                rs.getString("codigo"), rs.getString("tipo"), rs.getString("estado"),
                rs.getLong("id_vehiculo"), rs.getString("placa"),
                ts(rs.getTimestamp("creado_utc")), ts(rs.getTimestamp("actualizado_utc"))
        ), estado, estado, like, like, like);
    }

    public Optional<FilaSolicitud> bloquearPorCodigo(String codigo) {
        exigirTx();
        try {
            return Optional.ofNullable(jdbc.queryForObject(SQL_DETALLE + " FOR UPDATE OF s", MAPPER_DETALLE, codigo));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public int actualizarEstado(long id, String estado, String motivoRechazo, int versionEsperada) {
        return jdbc.update("""
                UPDATE mycar.solicitud_soporte SET
                    estado = ?, motivo_rechazo = ?, version_fila = version_fila + 1,
                    actualizado_utc = clock_timestamp()
                WHERE id_solicitud = ? AND version_fila = ?
                """, estado, motivoRechazo, id, versionEsperada);
    }

    private static Instant ts(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static void exigirTx() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Se requiere transacción activa");
        }
    }

    public record VehiculoCtx(long idVehiculo, String placa, String numeroVin, boolean tieneTive) { }

    public record FilaSolicitud(
            long idSolicitud, String codigo, String tipo, String estado, long idUsuario, long idVehiculo,
            int versionFila, String motivoRechazo, Instant creadoUtc, Instant actualizadoUtc,
            String placa, String marca, String modelo, String nombreUsuario, String correoUsuario,
            String vinRegistrado, String vinPropuesto, String motivo,
            String storageObjectKey, String contentType, Integer tamanoBytes, String nombreOriginal
    ) { }
}
