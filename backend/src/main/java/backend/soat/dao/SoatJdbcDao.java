package backend.soat.dao;

import backend.soat.api.dto.ContratoSoat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class SoatJdbcDao {
    private final JdbcTemplate jdbc;

    public SoatJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public LocalDate hoyLima() {
        return jdbc.queryForObject(
                "SELECT (clock_timestamp() AT TIME ZONE 'America/Lima')::date", LocalDate.class);
    }

    public String estadoVehiculoPropio(long usuario, long vehiculo) {
        try {
            return jdbc.queryForObject("""
                    SELECT estado_vehiculo FROM mycar.vehiculo
                    WHERE id_vehiculo = ? AND id_usuario = ?
                    """, String.class, vehiculo, usuario);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public void exigirVehiculoActivo(long usuario, long vehiculo) {
        exigirTx();
        Integer ok = jdbc.queryForObject("""
                SELECT 1 FROM mycar.vehiculo
                WHERE id_vehiculo = ? AND id_usuario = ? AND estado_vehiculo = 'ACTIVO'
                FOR UPDATE
                """, Integer.class, vehiculo, usuario);
        if (ok == null) throw new EmptyResultDataAccessException(1);
    }

    public void bloquearSoatVehiculo(long vehiculo) {
        exigirTx();
        jdbc.queryForList("""
                SELECT id_soat FROM mycar.soat WHERE id_vehiculo = ? FOR UPDATE
                """, vehiculo);
    }

    public boolean existeActual(long vehiculo) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*)::int FROM mycar.soat
                WHERE id_vehiculo = ? AND vigencia = 'ACTUAL'
                """, Integer.class, vehiculo);
        return n != null && n > 0;
    }

    public Optional<FilaSoat> obtenerActual(long vehiculo) {
        return buscarUno("""
                SELECT id_soat, id_vehiculo, aseguradora, numero_poliza, fecha_inicio, fecha_vencimiento,
                       fecha_emision, nombre_contratante, importe_prima, vigencia, recordatorio_activo,
                       version_fila, storage_object_key, content_type, tamano_bytes, nombre_original
                FROM mycar.soat WHERE id_vehiculo = ? AND vigencia = 'ACTUAL'
                """, vehiculo);
    }

    public Optional<FilaSoat> obtenerPropio(long usuario, long vehiculo, long idSoat) {
        return buscarUno("""
                SELECT s.id_soat, s.id_vehiculo, s.aseguradora, s.numero_poliza, s.fecha_inicio, s.fecha_vencimiento,
                       s.fecha_emision, s.nombre_contratante, s.importe_prima, s.vigencia, s.recordatorio_activo,
                       s.version_fila, s.storage_object_key, s.content_type, s.tamano_bytes, s.nombre_original
                FROM mycar.soat s
                JOIN mycar.vehiculo v ON v.id_vehiculo = s.id_vehiculo
                WHERE s.id_soat = ? AND s.id_vehiculo = ? AND v.id_usuario = ?
                """, idSoat, vehiculo, usuario);
    }

    public List<FilaSoat> listarHistorial(long vehiculo) {
        return jdbc.query("""
                SELECT id_soat, id_vehiculo, aseguradora, numero_poliza, fecha_inicio, fecha_vencimiento,
                       fecha_emision, nombre_contratante, importe_prima, vigencia, recordatorio_activo,
                       version_fila, storage_object_key, content_type, tamano_bytes, nombre_original
                FROM mycar.soat WHERE id_vehiculo = ? AND vigencia = 'HISTORICO'
                ORDER BY fecha_inicio DESC, id_soat DESC
                """, (rs, n) -> mapear(rs), vehiculo);
    }

    public long insertar(DatosInsercion d) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.soat(
                    id_vehiculo, aseguradora, numero_poliza, fecha_inicio, fecha_vencimiento,
                    fecha_emision, nombre_contratante, importe_prima, vigencia, recordatorio_activo,
                    storage_object_key, content_type, tamano_bytes, nombre_original)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                RETURNING id_soat
                """, Long.class,
                d.idVehiculo(), d.aseguradora(), d.numeroPoliza(), d.fechaInicio(), d.fechaVencimiento(),
                d.fechaEmision(), d.nombreContratante(), d.importePrima(), d.vigencia(), d.recordatorioActivo(),
                d.storageObjectKey(), d.contentType(), d.tamanoBytes(), d.nombreOriginal());
    }

    public int actualizarCorregir(DatosActualizacion d) {
        return jdbc.update("""
                UPDATE mycar.soat SET
                    aseguradora = ?, numero_poliza = ?, fecha_inicio = ?, fecha_vencimiento = ?,
                    fecha_emision = ?, nombre_contratante = ?, importe_prima = ?,
                    storage_object_key = ?, content_type = ?, tamano_bytes = ?, nombre_original = ?,
                    version_fila = version_fila + 1, actualizado_utc = clock_timestamp()
                WHERE id_soat = ? AND id_vehiculo = ? AND vigencia = 'ACTUAL' AND version_fila = ?
                """,
                d.aseguradora(), d.numeroPoliza(), d.fechaInicio(), d.fechaVencimiento(),
                d.fechaEmision(), d.nombreContratante(), d.importePrima(),
                d.storageObjectKey(), d.contentType(), d.tamanoBytes(), d.nombreOriginal(),
                d.idSoat(), d.idVehiculo(), d.versionEsperada());
    }

    public int marcarHistorico(long idSoat, long vehiculo, int versionEsperada) {
        return jdbc.update("""
                UPDATE mycar.soat SET vigencia = 'HISTORICO', recordatorio_activo = false,
                    version_fila = version_fila + 1, actualizado_utc = clock_timestamp()
                WHERE id_soat = ? AND id_vehiculo = ? AND vigencia = 'ACTUAL' AND version_fila = ?
                """, idSoat, vehiculo, versionEsperada);
    }

    public int actualizarRecordatorio(long idSoat, long vehiculo, boolean activo, int versionEsperada) {
        return jdbc.update("""
                UPDATE mycar.soat SET recordatorio_activo = ?, version_fila = version_fila + 1,
                    actualizado_utc = clock_timestamp()
                WHERE id_soat = ? AND id_vehiculo = ? AND vigencia = 'ACTUAL' AND version_fila = ?
                """, activo, idSoat, vehiculo, versionEsperada);
    }

    public void insertarCambio(long idSoat, long usuario, String antes, String despues) {
        jdbc.update("""
                INSERT INTO mycar.soat_cambio(id_soat, id_usuario, snapshot_antes, snapshot_despues)
                VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
                """, idSoat, usuario, antes, despues);
    }

    public boolean duplicadoContextual(long vehiculo, String aseguradora, String poliza,
            LocalDate inicio, LocalDate vencimiento, Long excluirIdSoat) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*)::int FROM mycar.soat
                WHERE id_vehiculo = ?
                  AND lower(btrim(aseguradora)) = lower(btrim(?))
                  AND lower(btrim(numero_poliza)) = lower(btrim(?))
                  AND daterange(fecha_inicio, fecha_vencimiento, '[]') && daterange(?, ?, '[]')
                  AND (?::bigint IS NULL OR id_soat <> ?)
                """, Integer.class, vehiculo, aseguradora, poliza, inicio, vencimiento, excluirIdSoat, excluirIdSoat);
        return n != null && n > 0;
    }

    public boolean solapeConHistorico(long vehiculo, LocalDate inicio, LocalDate vencimiento, Long excluir) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*)::int FROM mycar.soat
                WHERE id_vehiculo = ?
                  AND daterange(fecha_inicio, fecha_vencimiento, '[]') && daterange(?, ?, '[]')
                  AND (?::bigint IS NULL OR id_soat <> ?)
                """, Integer.class, vehiculo, inicio, vencimiento, excluir, excluir);
        return n != null && n > 0;
    }

    public Optional<AlertaSoat> alertaActivaPorSoat(long idSoat) {
        return jdbc.query("""
                SELECT id_alerta, estado FROM mycar.alerta
                WHERE id_soat = ? AND categoria = 'SOAT'
                  AND estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
                ORDER BY id_alerta DESC LIMIT 1
                """, (rs, n) -> new AlertaSoat(rs.getLong(1), rs.getString(2)), idSoat)
                .stream().findFirst();
    }

    public long insertarAlertaSoat(long vehiculo, long idSoat, LocalDate fechaBase, LocalDate fechaObjetivo,
            String estado, String firma, LocalDate fechaVencidaVerificable) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.alerta(
                    id_vehiculo, categoria, id_soat, criterio, origen, origen_calculo,
                    unidad_tiempo, cantidad_tiempo, fecha_base, fecha_objetivo,
                    estado, firma_equivalencia, fecha_vencida_verificable)
                VALUES (?, 'SOAT', ?, 'POR_TIEMPO', 'MANUAL', 'DESDE_HOY',
                        'DIAS', 30, ?, ?, ?, ?, ?)
                RETURNING id_alerta
                """, Long.class, vehiculo, idSoat, fechaBase, fechaObjetivo, estado, firma,
                fechaVencidaVerificable);
    }

    public void historialAlertaInicial(long idAlerta, String estado) {
        jdbc.update("""
                INSERT INTO mycar.historial_alerta(id_alerta, estado_nuevo, motivo, version_destino)
                VALUES (?, ?, 'INICIAL_SOAT', 0)
                """, idAlerta, estado);
    }

    public int anularAlertaSoat(long idAlerta, String estadoAnterior, int versionEsperada) {
        int filas = jdbc.update("""
                UPDATE mycar.alerta SET estado = 'ANULADA', version_fila = version_fila + 1,
                    fecha_estado_utc = clock_timestamp()
                WHERE id_alerta = ? AND version_fila = ?
                  AND categoria = 'SOAT'
                  AND estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
                """, idAlerta, versionEsperada);
        if (filas == 1) {
            jdbc.update("""
                    INSERT INTO mycar.historial_alerta(id_alerta, estado_anterior, estado_nuevo, motivo, version_destino)
                    VALUES (?, ?, 'ANULADA', 'RECORDATORIO_SOAT_OFF', ?)
                    """, idAlerta, estadoAnterior, versionEsperada + 1);
        }
        return filas;
    }

    /** Renovación RF31: cierra seguimiento anterior como REALIZADA (no es Atender RF24). */
    public int cerrarAlertaSoatPorRenovacion(long idAlerta, String estadoAnterior, int versionEsperada) {
        if ("ANULADA".equals(estadoAnterior) || "REALIZADA".equals(estadoAnterior)) {
            return 0;
        }
        int filas = jdbc.update("""
                UPDATE mycar.alerta SET estado = 'REALIZADA', version_fila = version_fila + 1,
                    fecha_estado_utc = clock_timestamp()
                WHERE id_alerta = ? AND version_fila = ?
                  AND categoria = 'SOAT'
                  AND estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
                """, idAlerta, versionEsperada);
        if (filas == 1) {
            jdbc.update("""
                    INSERT INTO mycar.historial_alerta(id_alerta, estado_anterior, estado_nuevo, motivo, version_destino)
                    VALUES (?, ?, 'REALIZADA', 'RENOVACION_SOAT', ?)
                    """, idAlerta, estadoAnterior, versionEsperada + 1);
        }
        return filas;
    }

    public Optional<AlertaVersion> alertaVersion(long idAlerta) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT id_alerta, estado, version_fila FROM mycar.alerta WHERE id_alerta = ?",
                    (rs, n) -> new AlertaVersion(rs.getLong(1), rs.getString(2), rs.getInt(3)), idAlerta));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public String calcularEstadoInicial(LocalDate fechaObjetivo, LocalDate hoy) {
        return jdbc.queryForObject(
                "SELECT mycar.calcular_estado_alerta('POR_TIEMPO', ?, ?, NULL, NULL, 0)",
                String.class, fechaObjetivo, hoy);
    }

    public record DatosInsercion(
            long idVehiculo, String aseguradora, String numeroPoliza,
            LocalDate fechaInicio, LocalDate fechaVencimiento, LocalDate fechaEmision,
            String nombreContratante, BigDecimal importePrima, String vigencia, boolean recordatorioActivo,
            String storageObjectKey, String contentType, Integer tamanoBytes, String nombreOriginal
    ) { }

    public record DatosActualizacion(
            long idSoat, long idVehiculo, int versionEsperada,
            String aseguradora, String numeroPoliza,
            LocalDate fechaInicio, LocalDate fechaVencimiento, LocalDate fechaEmision,
            String nombreContratante, BigDecimal importePrima,
            String storageObjectKey, String contentType, Integer tamanoBytes, String nombreOriginal
    ) { }

    public record FilaSoat(
            long idSoat, long idVehiculo, String aseguradora, String numeroPoliza,
            LocalDate fechaInicio, LocalDate fechaVencimiento, LocalDate fechaEmision,
            String nombreContratante, BigDecimal importePrima, String vigencia, boolean recordatorioActivo,
            int versionFila, String storageObjectKey, String contentType, Integer tamanoBytes, String nombreOriginal
    ) { }

    public record AlertaSoat(long idAlerta, String estado) { }

    public record AlertaVersion(long idAlerta, String estado, int versionFila) { }

    public static ContratoSoat.SoatDto aDto(FilaSoat f) {
        return aDto(f, LocalDate.now(java.time.ZoneId.of("America/Lima")));
    }

    public static ContratoSoat.SoatDto aDto(FilaSoat f, LocalDate hoy) {
        ContratoSoat.DocumentoDto doc = null;
        if (f.storageObjectKey() != null) {
            doc = new ContratoSoat.DocumentoDto(
                    f.storageObjectKey(), f.contentType(), f.tamanoBytes(), f.nombreOriginal());
        }
        String cobertura = backend.soat.service.ValidadorSoat.estadoCobertura(
                f.fechaInicio(), f.fechaVencimiento(), hoy);
        return new ContratoSoat.SoatDto(
                f.idSoat(), f.aseguradora(), f.numeroPoliza(), f.fechaInicio(), f.fechaVencimiento(),
                f.fechaEmision(), f.nombreContratante(), f.importePrima(), f.vigencia(),
                cobertura, f.recordatorioActivo(), doc != null, f.versionFila(), doc);
    }

    private Optional<FilaSoat> buscarUno(String sql, Object... args) {
        return jdbc.query(sql, (rs, n) -> mapear(rs), args).stream().findFirst();
    }

    private static FilaSoat mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FilaSoat(
                rs.getLong("id_soat"), rs.getLong("id_vehiculo"), rs.getString("aseguradora"),
                rs.getString("numero_poliza"),
                rs.getObject("fecha_inicio", LocalDate.class), rs.getObject("fecha_vencimiento", LocalDate.class),
                rs.getObject("fecha_emision", LocalDate.class), rs.getString("nombre_contratante"),
                rs.getObject("importe_prima", BigDecimal.class), rs.getString("vigencia"),
                rs.getBoolean("recordatorio_activo"), rs.getInt("version_fila"),
                rs.getString("storage_object_key"), rs.getString("content_type"),
                rs.getObject("tamano_bytes", Integer.class), rs.getString("nombre_original"));
    }

    private void exigirTx() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("SOAT requiere transacción activa");
        }
    }
}