package backend.licencia.dao;

import backend.licencia.api.dto.ContratoLicencia;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class LicenciaJdbcDao {
    private final JdbcTemplate jdbc;

    private static final String COLUMNAS = """
            id_licencia, id_usuario, categoria, numero_cifrado, numero_blind, crypto_key_id,
            fecha_emision, fecha_vencimiento, vigencia, recordatorio_activo, version_fila,
            foto_frontal_key, foto_frontal_content_type, foto_frontal_tamano, foto_frontal_nombre,
            foto_posterior_key, foto_posterior_content_type, foto_posterior_tamano, foto_posterior_nombre,
            documento_cifrado
            """;

    public LicenciaJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public LocalDate hoyLima() {
        return jdbc.queryForObject(
                "SELECT (clock_timestamp() AT TIME ZONE 'America/Lima')::date", LocalDate.class);
    }

    public void bloquearLicenciaUsuario(long usuario) {
        exigirTx();
        jdbc.queryForList("""
                SELECT id_licencia FROM mycar.licencia WHERE id_usuario = ? FOR UPDATE
                """, usuario);
    }

    public boolean existeActual(long usuario) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*)::int FROM mycar.licencia
                WHERE id_usuario = ? AND vigencia = 'ACTUAL'
                """, Integer.class, usuario);
        return n != null && n > 0;
    }

    public Optional<FilaLicencia> obtenerActual(long usuario) {
        return buscarUno(
                "SELECT " + COLUMNAS + " FROM mycar.licencia WHERE id_usuario = ? AND vigencia = 'ACTUAL'",
                usuario);
    }

    public Optional<FilaLicencia> obtenerPropio(long usuario, long idLicencia) {
        return buscarUno(
                "SELECT " + COLUMNAS + " FROM mycar.licencia WHERE id_licencia = ? AND id_usuario = ?",
                idLicencia, usuario);
    }

    public List<FilaLicencia> listarHistorial(long usuario) {
        return jdbc.query(
                "SELECT " + COLUMNAS
                        + " FROM mycar.licencia WHERE id_usuario = ? AND vigencia = 'HISTORICO'"
                        + " ORDER BY fecha_vencimiento DESC, id_licencia DESC",
                (rs, n) -> mapear(rs), usuario);
    }

    /**
     * Número ya usado por el usuario (ACTUAL o HISTORICO).
     * En renovación se excluye la licencia que pasa a historial para permitir conservar el mismo número.
     */
    public boolean numeroEnUso(long usuario, String numeroBlind, Long excluirId) {
        if (numeroBlind == null || numeroBlind.isBlank()) {
            return false;
        }
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*)::int FROM mycar.licencia
                WHERE id_usuario = ? AND numero_blind = ?
                  AND (?::bigint IS NULL OR id_licencia <> ?)
                """, Integer.class, usuario, numeroBlind, excluirId, excluirId);
        return n != null && n > 0;
    }

    /** Misma huella de negocio ya registrada (evita clonar una licencia histórica). */
    public boolean existeDuplicadoExacto(
            long usuario, String numeroBlind, String categoria,
            LocalDate emision, LocalDate vencimiento, Long excluirId) {
        if (numeroBlind == null || numeroBlind.isBlank() || categoria == null
                || emision == null || vencimiento == null) {
            return false;
        }
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*)::int FROM mycar.licencia
                WHERE id_usuario = ? AND numero_blind = ? AND categoria = ?
                  AND fecha_emision = ? AND fecha_vencimiento = ?
                  AND (?::bigint IS NULL OR id_licencia <> ?)
                """, Integer.class, usuario, numeroBlind, categoria, emision, vencimiento,
                excluirId, excluirId);
        return n != null && n > 0;
    }

    public long insertar(DatosInsercion d) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.licencia(
                    id_usuario, categoria, numero_cifrado, numero_blind, crypto_key_id,
                    fecha_emision, fecha_vencimiento, vigencia, recordatorio_activo,
                    foto_frontal_key, foto_frontal_content_type, foto_frontal_tamano, foto_frontal_nombre,
                    foto_posterior_key, foto_posterior_content_type, foto_posterior_tamano, foto_posterior_nombre,
                    documento_cifrado)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                RETURNING id_licencia
                """, Long.class,
                d.idUsuario(), d.categoria(), d.numeroCifrado(), d.numeroBlind(), d.cryptoKeyId(),
                d.fechaEmision(), d.fechaVencimiento(), d.vigencia(), d.recordatorioActivo(),
                d.fotoFrontalKey(), d.fotoFrontalContentType(), d.fotoFrontalTamano(), d.fotoFrontalNombre(),
                d.fotoPosteriorKey(), d.fotoPosteriorContentType(), d.fotoPosteriorTamano(), d.fotoPosteriorNombre(),
                d.documentoCifrado());
    }

    public int actualizarEditar(DatosActualizacion d) {
        return jdbc.update("""
                UPDATE mycar.licencia SET
                    categoria = ?, numero_cifrado = ?, numero_blind = ?, crypto_key_id = ?,
                    fecha_emision = ?, fecha_vencimiento = ?,
                    foto_frontal_key = ?, foto_frontal_content_type = ?, foto_frontal_tamano = ?, foto_frontal_nombre = ?,
                    foto_posterior_key = ?, foto_posterior_content_type = ?, foto_posterior_tamano = ?, foto_posterior_nombre = ?,
                    documento_cifrado = ?,
                    version_fila = version_fila + 1, actualizado_utc = clock_timestamp()
                WHERE id_licencia = ? AND id_usuario = ? AND vigencia = 'ACTUAL' AND version_fila = ?
                """,
                d.categoria(), d.numeroCifrado(), d.numeroBlind(), d.cryptoKeyId(),
                d.fechaEmision(), d.fechaVencimiento(),
                d.fotoFrontalKey(), d.fotoFrontalContentType(), d.fotoFrontalTamano(), d.fotoFrontalNombre(),
                d.fotoPosteriorKey(), d.fotoPosteriorContentType(), d.fotoPosteriorTamano(), d.fotoPosteriorNombre(),
                d.documentoCifrado(),
                d.idLicencia(), d.idUsuario(), d.versionEsperada());
    }

    public int marcarHistorico(long idLicencia, long usuario, int versionEsperada) {
        return jdbc.update("""
                UPDATE mycar.licencia SET
                    vigencia = 'HISTORICO',
                    recordatorio_activo = false,
                    foto_frontal_key = NULL, foto_frontal_content_type = NULL,
                    foto_frontal_tamano = NULL, foto_frontal_nombre = NULL,
                    foto_posterior_key = NULL, foto_posterior_content_type = NULL,
                    foto_posterior_tamano = NULL, foto_posterior_nombre = NULL,
                    documento_cifrado = false,
                    version_fila = version_fila + 1, actualizado_utc = clock_timestamp()
                WHERE id_licencia = ? AND id_usuario = ? AND vigencia = 'ACTUAL' AND version_fila = ?
                """, idLicencia, usuario, versionEsperada);
    }

    public int actualizarRecordatorio(long idLicencia, long usuario, boolean activo, int versionEsperada) {
        return jdbc.update("""
                UPDATE mycar.licencia SET recordatorio_activo = ?, version_fila = version_fila + 1,
                    actualizado_utc = clock_timestamp()
                WHERE id_licencia = ? AND id_usuario = ? AND vigencia = 'ACTUAL' AND version_fila = ?
                """, activo, idLicencia, usuario, versionEsperada);
    }

    public Optional<AlertaLicencia> alertaActivaPorLicencia(long idLicencia) {
        return jdbc.query("""
                SELECT id_alerta, estado FROM mycar.alerta
                WHERE id_licencia = ? AND categoria = 'LICENCIA'
                  AND estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
                ORDER BY id_alerta DESC LIMIT 1
                """, (rs, n) -> new AlertaLicencia(rs.getLong(1), rs.getString(2)), idLicencia)
                .stream().findFirst();
    }

    public long insertarAlertaLicencia(long usuario, long idLicencia, LocalDate fechaBase, LocalDate fechaObjetivo,
            String estado, String firma, LocalDate fechaVencidaVerificable) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.alerta(
                    id_vehiculo, id_usuario, categoria, id_licencia, criterio, origen, origen_calculo,
                    unidad_tiempo, cantidad_tiempo, fecha_base, fecha_objetivo,
                    estado, firma_equivalencia, fecha_vencida_verificable)
                VALUES (NULL, ?, 'LICENCIA', ?, 'POR_TIEMPO', 'MANUAL', 'DESDE_HOY',
                        'DIAS', 30, ?, ?, ?, ?, ?)
                RETURNING id_alerta
                """, Long.class, usuario, idLicencia, fechaBase, fechaObjetivo, estado, firma, fechaVencidaVerificable);
    }

    public void historialAlertaInicial(long idAlerta, String estado) {
        jdbc.update("""
                INSERT INTO mycar.historial_alerta(id_alerta, estado_nuevo, motivo, version_destino)
                VALUES (?, ?, 'INICIAL_LICENCIA', 0)
                """, idAlerta, estado);
    }

    public int anularAlertaLicencia(long idAlerta, String estadoAnterior, int versionEsperada) {
        int filas = jdbc.update("""
                UPDATE mycar.alerta SET estado = 'ANULADA', version_fila = version_fila + 1,
                    fecha_estado_utc = clock_timestamp()
                WHERE id_alerta = ? AND version_fila = ?
                  AND categoria = 'LICENCIA'
                  AND estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
                """, idAlerta, versionEsperada);
        if (filas == 1) {
            jdbc.update("""
                    INSERT INTO mycar.historial_alerta(id_alerta, estado_anterior, estado_nuevo, motivo, version_destino)
                    VALUES (?, ?, 'ANULADA', 'RECORDATORIO_LICENCIA_OFF', ?)
                    """, idAlerta, estadoAnterior, versionEsperada + 1);
        }
        return filas;
    }

    public int cerrarAlertaLicenciaPorRenovacion(long idAlerta, String estadoAnterior, int versionEsperada) {
        if ("ANULADA".equals(estadoAnterior) || "REALIZADA".equals(estadoAnterior)) {
            return 0;
        }
        int filas = jdbc.update("""
                UPDATE mycar.alerta SET estado = 'REALIZADA', version_fila = version_fila + 1,
                    fecha_estado_utc = clock_timestamp()
                WHERE id_alerta = ? AND version_fila = ?
                  AND categoria = 'LICENCIA'
                  AND estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
                """, idAlerta, versionEsperada);
        if (filas == 1) {
            jdbc.update("""
                    INSERT INTO mycar.historial_alerta(id_alerta, estado_anterior, estado_nuevo, motivo, version_destino)
                    VALUES (?, ?, 'REALIZADA', 'RENOVACION_LICENCIA', ?)
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
            long idUsuario, String categoria, String numeroCifrado, String numeroBlind, String cryptoKeyId,
            LocalDate fechaEmision, LocalDate fechaVencimiento, String vigencia, boolean recordatorioActivo,
            String fotoFrontalKey, String fotoFrontalContentType, Integer fotoFrontalTamano, String fotoFrontalNombre,
            String fotoPosteriorKey, String fotoPosteriorContentType, Integer fotoPosteriorTamano, String fotoPosteriorNombre,
            boolean documentoCifrado
    ) { }

    public record DatosActualizacion(
            long idLicencia, long idUsuario, int versionEsperada,
            String categoria, String numeroCifrado, String numeroBlind, String cryptoKeyId,
            LocalDate fechaEmision, LocalDate fechaVencimiento,
            String fotoFrontalKey, String fotoFrontalContentType, Integer fotoFrontalTamano, String fotoFrontalNombre,
            String fotoPosteriorKey, String fotoPosteriorContentType, Integer fotoPosteriorTamano, String fotoPosteriorNombre,
            boolean documentoCifrado
    ) { }

    public record FilaLicencia(
            long idLicencia, long idUsuario, String categoria, String numeroCifrado, String numeroBlind, String cryptoKeyId,
            LocalDate fechaEmision, LocalDate fechaVencimiento, String vigencia, boolean recordatorioActivo, int versionFila,
            String fotoFrontalKey, String fotoFrontalContentType, Integer fotoFrontalTamano, String fotoFrontalNombre,
            String fotoPosteriorKey, String fotoPosteriorContentType, Integer fotoPosteriorTamano, String fotoPosteriorNombre,
            boolean documentoCifrado
    ) { }

    public record AlertaLicencia(long idAlerta, String estado) { }

    public record AlertaVersion(long idAlerta, String estado, int versionFila) { }

    public static ContratoLicencia.HistorialResumen aHistorialResumen(FilaLicencia f) {
        return aHistorialResumen(f, null);
    }

    public static ContratoLicencia.HistorialResumen aHistorialResumen(FilaLicencia f, String numero) {
        return new ContratoLicencia.HistorialResumen(
                f.idLicencia(), f.categoria(), numero, f.fechaEmision(), f.fechaVencimiento(), "ANTERIOR");
    }

    private Optional<FilaLicencia> buscarUno(String sql, Object... args) {
        return jdbc.query(sql, (rs, n) -> mapear(rs), args).stream().findFirst();
    }

    private static FilaLicencia mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FilaLicencia(
                rs.getLong("id_licencia"), rs.getLong("id_usuario"), rs.getString("categoria"),
                rs.getString("numero_cifrado"), rs.getString("numero_blind"), rs.getString("crypto_key_id"),
                rs.getObject("fecha_emision", LocalDate.class), rs.getObject("fecha_vencimiento", LocalDate.class),
                rs.getString("vigencia"), rs.getBoolean("recordatorio_activo"), rs.getInt("version_fila"),
                rs.getString("foto_frontal_key"), rs.getString("foto_frontal_content_type"),
                rs.getObject("foto_frontal_tamano", Integer.class), rs.getString("foto_frontal_nombre"),
                rs.getString("foto_posterior_key"), rs.getString("foto_posterior_content_type"),
                rs.getObject("foto_posterior_tamano", Integer.class), rs.getString("foto_posterior_nombre"),
                rs.getBoolean("documento_cifrado"));
    }

    private void exigirTx() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Licencia requiere transacción activa");
        }
    }
}