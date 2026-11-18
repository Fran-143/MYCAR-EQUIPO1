package backend.tive.dao;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class TiveJdbcDao {
    private final JdbcTemplate jdbc;

    private static final String COLS = """
            t.id_tive, t.id_vehiculo, t.numero_titulo, t.codigo_verificacion, t.fecha_titulo,
            t.zona_registral, t.sede_registral, t.numero_vin, t.placa, t.numero_motor, t.numero_serie,
            t.version_fila, t.storage_object_key, t.content_type, t.tamano_bytes, t.nombre_original
            """;

    public TiveJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existeParaVehiculo(long idVehiculo) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*)::int FROM mycar.tive WHERE id_vehiculo = ?",
                Integer.class, idVehiculo);
        return n != null && n > 0;
    }

    public Optional<VehiculoTiveCtx> vehiculoPropio(long usuario, long vehiculo) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT id_vehiculo, id_usuario, estado_vehiculo, placa, numero_vin, numero_motor, numero_serie,
                           marca, modelo, anio_fabricacion, color
                    FROM mycar.vehiculo
                    WHERE id_vehiculo = ? AND id_usuario = ?
                    """, (rs, n) -> new VehiculoTiveCtx(
                    rs.getLong("id_vehiculo"),
                    rs.getLong("id_usuario"),
                    rs.getString("estado_vehiculo"),
                    rs.getString("placa"),
                    rs.getString("numero_vin"),
                    rs.getString("numero_motor"),
                    rs.getString("numero_serie"),
                    rs.getString("marca"),
                    rs.getString("modelo"),
                    (Integer) rs.getObject("anio_fabricacion"),
                    rs.getString("color")
            ), vehiculo, usuario));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public void exigirVehiculoActivoBloqueado(long usuario, long vehiculo) {
        exigirTx();
        Integer ok = jdbc.queryForObject("""
                SELECT 1 FROM mycar.vehiculo
                WHERE id_vehiculo = ? AND id_usuario = ? AND estado_vehiculo = 'ACTIVO'
                FOR UPDATE
                """, Integer.class, vehiculo, usuario);
        if (ok == null) throw new EmptyResultDataAccessException(1);
    }

    public void bloquearTiveVehiculo(long vehiculo) {
        exigirTx();
        jdbc.queryForList("SELECT id_tive FROM mycar.tive WHERE id_vehiculo = ? FOR UPDATE", vehiculo);
    }

    public Optional<FilaTive> obtenerPorVehiculo(long vehiculo) {
        return buscarUno("SELECT " + COLS + " FROM mycar.tive t WHERE t.id_vehiculo = ?", vehiculo);
    }

    public Optional<FilaTive> obtenerPropio(long usuario, long vehiculo, long idTive) {
        return buscarUno("""
                SELECT %s FROM mycar.tive t
                JOIN mycar.vehiculo v ON v.id_vehiculo = t.id_vehiculo
                WHERE t.id_tive = ? AND t.id_vehiculo = ? AND v.id_usuario = ?
                """.formatted(COLS), idTive, vehiculo, usuario);
    }

    public long insertar(DatosInsercion d) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.tive(
                    id_vehiculo, numero_titulo, codigo_verificacion, fecha_titulo,
                    zona_registral, sede_registral, numero_vin, placa, numero_motor, numero_serie,
                    storage_object_key, content_type, tamano_bytes, nombre_original)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                RETURNING id_tive
                """, Long.class,
                d.idVehiculo(), d.numeroTitulo(), d.codigoVerificacion(), d.fechaTitulo(),
                d.zonaRegistral(), d.sedeRegistral(), d.numeroVin(), d.placa(),
                d.numeroMotor(), d.numeroSerie(),
                d.storageObjectKey(), d.contentType(), d.tamanoBytes(), d.nombreOriginal());
    }

    public int actualizar(DatosActualizacion d) {
        return jdbc.update("""
                UPDATE mycar.tive SET
                    numero_titulo = ?, codigo_verificacion = ?, fecha_titulo = ?,
                    zona_registral = ?, sede_registral = ?,
                    numero_motor = ?, numero_serie = ?,
                    storage_object_key = ?, content_type = ?, tamano_bytes = ?, nombre_original = ?,
                    version_fila = version_fila + 1, actualizado_utc = clock_timestamp()
                WHERE id_tive = ? AND id_vehiculo = ? AND version_fila = ?
                """,
                d.numeroTitulo(), d.codigoVerificacion(), d.fechaTitulo(),
                d.zonaRegistral(), d.sedeRegistral(),
                d.numeroMotor(), d.numeroSerie(),
                d.storageObjectKey(), d.contentType(), d.tamanoBytes(), d.nombreOriginal(),
                d.idTive(), d.idVehiculo(), d.versionEsperada());
    }

    public int sincronizarMotorSerieVehiculo(long idVehiculo, String motor, String serie) {
        return jdbc.update("""
                UPDATE mycar.vehiculo SET numero_motor = ?, numero_serie = ?
                WHERE id_vehiculo = ?
                """, motor, serie, idVehiculo);
    }

    public int actualizarVinVehiculoYTive(long idVehiculo, String vinNuevo) {
        int v = jdbc.update(
                "UPDATE mycar.vehiculo SET numero_vin = ? WHERE id_vehiculo = ?",
                vinNuevo, idVehiculo);
        int t = jdbc.update("""
                UPDATE mycar.tive SET numero_vin = ?, version_fila = version_fila + 1,
                    actualizado_utc = clock_timestamp()
                WHERE id_vehiculo = ?
                """, vinNuevo, idVehiculo);
        return v + t;
    }

    public boolean vinUsadoPorOtro(String vin, long excluirVehiculo) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*)::int FROM mycar.vehiculo
                WHERE numero_vin = ? AND id_vehiculo <> ?
                """, Integer.class, vin, excluirVehiculo);
        return n != null && n > 0;
    }

    public void insertarCambio(long idTive, long usuario, String antes, String despues) {
        jdbc.update("""
                INSERT INTO mycar.tive_cambio(id_tive, id_usuario, snapshot_antes, snapshot_despues)
                VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
                """, idTive, usuario, antes, despues);
    }

    private Optional<FilaTive> buscarUno(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, (rs, n) -> new FilaTive(
                    rs.getLong("id_tive"),
                    rs.getLong("id_vehiculo"),
                    rs.getString("numero_titulo"),
                    rs.getString("codigo_verificacion"),
                    rs.getObject("fecha_titulo", LocalDate.class),
                    rs.getString("zona_registral"),
                    rs.getString("sede_registral"),
                    rs.getString("numero_vin"),
                    rs.getString("placa"),
                    rs.getString("numero_motor"),
                    rs.getString("numero_serie"),
                    rs.getInt("version_fila"),
                    rs.getString("storage_object_key"),
                    rs.getString("content_type"),
                    (Integer) rs.getObject("tamano_bytes"),
                    rs.getString("nombre_original")
            ), args));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private static void exigirTx() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Se requiere transacción activa");
        }
    }

    public record VehiculoTiveCtx(
            long idVehiculo, long idUsuario, String estado,
            String placa, String numeroVin, String numeroMotor, String numeroSerie,
            String marca, String modelo, Integer anioFabricacion, String color
    ) { }

    public record FilaTive(
            long idTive, long idVehiculo, String numeroTitulo, String codigoVerificacion,
            LocalDate fechaTitulo, String zonaRegistral, String sedeRegistral,
            String numeroVin, String placa, String numeroMotor, String numeroSerie,
            int versionFila, String storageObjectKey, String contentType,
            Integer tamanoBytes, String nombreOriginal
    ) { }

    public record DatosInsercion(
            long idVehiculo, String numeroTitulo, String codigoVerificacion, LocalDate fechaTitulo,
            String zonaRegistral, String sedeRegistral, String numeroVin, String placa,
            String numeroMotor, String numeroSerie,
            String storageObjectKey, String contentType, Integer tamanoBytes, String nombreOriginal
    ) { }

    public record DatosActualizacion(
            long idTive, long idVehiculo, int versionEsperada,
            String numeroTitulo, String codigoVerificacion, LocalDate fechaTitulo,
            String zonaRegistral, String sedeRegistral, String numeroMotor, String numeroSerie,
            String storageObjectKey, String contentType, Integer tamanoBytes, String nombreOriginal
    ) { }
}
