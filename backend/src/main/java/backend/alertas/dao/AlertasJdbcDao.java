package backend.alertas.dao;

import backend.alertas.api.dto.ContratoRf22;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class AlertasJdbcDao {
    private final JdbcTemplate jdbc;

    public AlertasJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void verificarVehiculoPropioActivo(long usuario, long vehiculo) {
        Integer ok = jdbc.queryForObject("""
                SELECT 1 FROM mycar.vehiculo
                WHERE id_vehiculo = ? AND id_usuario = ? AND estado_vehiculo = 'ACTIVO'
                """, Integer.class, vehiculo, usuario);
        if (ok == null) throw new EmptyResultDataAccessException(1);
    }

    public void exigirVehiculoPropioActivo(long usuario, long vehiculo) {
        exigirTx();
        Integer ok = jdbc.queryForObject("""
                SELECT 1 FROM mycar.vehiculo
                WHERE id_vehiculo = ? AND id_usuario = ? AND estado_vehiculo = 'ACTIVO'
                FOR UPDATE
                """, Integer.class, vehiculo, usuario);
        if (ok == null) throw new EmptyResultDataAccessException(1);
    }

    public LecturaActual ultimaLectura(long vehiculo) {
        return jdbc.query("""
                SELECT id_lectura_km, valor_km
                FROM mycar.lectura_kilometraje
                WHERE id_vehiculo = ?
                ORDER BY fecha_lectura_utc DESC, id_lectura_km DESC
                LIMIT 1
                """, rs -> rs.next()
                ? new LecturaActual(rs.getLong(1), rs.getInt(2))
                : null, vehiculo);
    }

    public LocalDate hoyLima() {
        return jdbc.queryForObject(
                "SELECT (clock_timestamp() AT TIME ZONE 'America/Lima')::date", LocalDate.class);
    }

    public LocalDate calcularFechaObjetivo(LocalDate base, String unidad, int cantidad) {
        if ("SEMANAS".equals(unidad)) {
            return jdbc.queryForObject("SELECT ?::date + ? * 7", LocalDate.class, base, cantidad);
        }
        return jdbc.queryForObject(
                "SELECT (?::date + make_interval(months => ?))::date", LocalDate.class, base, cantidad);
    }

    public String normalizarServicioOtro(String texto) {
        if (texto == null || texto.isBlank()) return "";
        return jdbc.queryForObject("SELECT mycar.normalizar_servicio_otro(?)", String.class, texto);
    }

    public long idTipoPorCodigo(String codigo) {
        return jdbc.queryForObject(
                "SELECT id_tipo_mantenimiento FROM mycar.tipo_mantenimiento WHERE codigo = ? AND activo",
                Long.class, codigo);
    }

    public String calcularEstadoInicial(
            String criterio, LocalDate fechaObjetivo, LocalDate hoy,
            Integer kmObjetivo, Integer intervaloKm, int kmActual) {
        return jdbc.queryForObject("""
                SELECT mycar.calcular_estado_alerta(?, ?, ?, ?, ?, ?)
                """, String.class, criterio, fechaObjetivo, hoy, kmObjetivo, intervaloKm, kmActual);
    }

    public long insertarAlerta(DatosInsercion d) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.alerta(
                    id_vehiculo, id_tipo_mantenimiento, criterio, servicio_otro,
                    unidad_tiempo, cantidad_tiempo, fecha_base, fecha_objetivo,
                    km_base, intervalo_km, km_objetivo, estado, firma_equivalencia,
                    id_mantenimiento_referencia)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                RETURNING id_alerta
                """, Long.class,
                d.idVehiculo(), d.idTipo(), d.criterio(), d.servicioOtro(),
                d.unidadTiempo(), d.cantidadTiempo(), d.fechaBase(), d.fechaObjetivo(),
                d.kmBase(), d.intervaloKm(), d.kmObjetivo(), d.estado(), d.firma(),
                d.idReferencia());
    }

    public void historialInicial(long idAlerta, String estado) {
        jdbc.update("""
                INSERT INTO mycar.historial_alerta(id_alerta, estado_nuevo, motivo, version_destino)
                VALUES (?, ?, 'INICIAL_RF22', 0)
                """, idAlerta, estado);
    }

    public int sincronizar(long vehiculo, long usuario) {
        Integer n = jdbc.queryForObject(
                "SELECT mycar.sincronizar_alertas_vehiculo(?, ?)", Integer.class, vehiculo, usuario);
        return n == null ? 0 : n;
    }

    public List<ContratoRf22.AlertaDto> listar(long vehiculo) {
        return jdbc.query("""
                SELECT a.id_alerta, tm.codigo, tm.nombre, a.criterio, a.estado,
                       a.fecha_objetivo, a.km_objetivo, a.km_base, a.intervalo_km,
                       a.servicio_otro, a.version_fila
                FROM mycar.alerta a
                JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = a.id_tipo_mantenimiento
                WHERE a.id_vehiculo = ?
                ORDER BY a.creada_utc DESC, a.id_alerta DESC
                """, (rs, n) -> new ContratoRf22.AlertaDto(
                rs.getLong("id_alerta"),
                rs.getString("codigo"),
                rs.getString("nombre"),
                rs.getString("criterio"),
                rs.getString("estado"),
                rs.getObject("fecha_objetivo", LocalDate.class),
                rs.getObject("km_objetivo", Integer.class),
                rs.getObject("km_base", Integer.class),
                rs.getObject("intervalo_km", Integer.class),
                rs.getString("servicio_otro"),
                rs.getInt("version_fila")), vehiculo);
    }

    public List<ContratoRf22.TipoDto> tiposActivos() {
        return jdbc.query("""
                SELECT id_tipo_mantenimiento, codigo, nombre
                FROM mycar.tipo_mantenimiento WHERE activo ORDER BY nombre
                """, (rs, n) -> new ContratoRf22.TipoDto(rs.getLong(1), rs.getString(2), rs.getString(3)));
    }

    public List<ContratoRf22.ReferenciaDto> referenciasRealizadas(long vehiculo) {
        return jdbc.query("""
                SELECT m.id_mantenimiento, tm.codigo, m.fecha_servicio, m.servicio_otro
                FROM mycar.mantenimiento m
                JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = m.id_tipo_mantenimiento
                WHERE m.id_vehiculo = ? AND m.estado = 'REALIZADO'
                ORDER BY m.fecha_servicio DESC, m.id_mantenimiento DESC
                LIMIT 30
                """, (rs, n) -> new ContratoRf22.ReferenciaDto(
                rs.getLong(1), rs.getString(2),
                rs.getObject(3, LocalDate.class), rs.getString(4)), vehiculo);
    }

    public Optional<Long> vehiculoDeAlerta(long idAlerta) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id_vehiculo FROM mycar.alerta WHERE id_alerta = ?", Long.class, idAlerta));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private void exigirTx() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Alertas requiere transacción activa");
        }
    }

    public record LecturaActual(long idLecturaKm, int valorKm) { }

    public record DatosInsercion(
            long idVehiculo, long idTipo, String criterio, String servicioOtro,
            String unidadTiempo, Integer cantidadTiempo, LocalDate fechaBase, LocalDate fechaObjetivo,
            Integer kmBase, Integer intervaloKm, Integer kmObjetivo, String estado, String firma,
            Long idReferencia
    ) { }
}
