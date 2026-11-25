package backend.vehiculos.eliminacion;

import backend.vehiculos.eliminacion.EliminacionVehiculoModelos.ResumenDependencias;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EliminacionVehiculoDao {
    private final JdbcTemplate jdbc;

    public EliminacionVehiculoDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record FilaVehiculo(
            long idVehiculo,
            String placa,
            String marca,
            String modelo,
            String estadoVehiculo,
            String estadoRegistro,
            boolean esPrincipal,
            int versionRegistro,
            boolean tieneFoto
    ) { }

    public record CandidatoPrincipal(
            long idVehiculo,
            String placa,
            String marca,
            String modelo
    ) { }

    public Optional<FilaVehiculo> obtenerPropio(long idUsuario, long idVehiculo) {
        return jdbc.query("""
                SELECT id_vehiculo, placa, marca, modelo, estado_vehiculo,
                       COALESCE(estado_registro, 'VIGENTE') AS estado_registro,
                       es_principal, COALESCE(version_registro, 0) AS version_registro,
                       (foto_storage_object_key IS NOT NULL AND btrim(foto_storage_object_key) <> '') AS tiene_foto
                FROM mycar.vehiculo
                WHERE id_usuario = ? AND id_vehiculo = ?
                """, (rs, n) -> new FilaVehiculo(
                rs.getLong("id_vehiculo"),
                rs.getString("placa"),
                rs.getString("marca"),
                rs.getString("modelo"),
                rs.getString("estado_vehiculo"),
                rs.getString("estado_registro"),
                rs.getBoolean("es_principal"),
                rs.getInt("version_registro"),
                rs.getBoolean("tiene_foto")
        ), idUsuario, idVehiculo).stream().findFirst();
    }

    public ResumenDependencias dependencias(long idVehiculo) {
        long mant = count("SELECT COUNT(*) FROM mycar.mantenimiento WHERE id_vehiculo = ?", idVehiculo);
        long km = count("SELECT COUNT(*) FROM mycar.lectura_kilometraje WHERE id_vehiculo = ?", idVehiculo);
        long alertas = count("SELECT COUNT(*) FROM mycar.alerta WHERE id_vehiculo = ?", idVehiculo);
        long soat = count("SELECT COUNT(*) FROM mycar.soat WHERE id_vehiculo = ?", idVehiculo);
        long tive = count("SELECT COUNT(*) FROM mycar.tive WHERE id_vehiculo = ?", idVehiculo);
        boolean foto = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT (foto_storage_object_key IS NOT NULL AND btrim(foto_storage_object_key) <> '')
                FROM mycar.vehiculo WHERE id_vehiculo = ?
                """, Boolean.class, idVehiculo));
        return new ResumenDependencias(mant, km, alertas, soat, tive, foto);
    }

    public Optional<CandidatoPrincipal> candidatoPrincipalTrasEliminar(long idUsuario, long idExcluir) {
        return jdbc.query("""
                SELECT id_vehiculo, placa, marca, modelo
                FROM mycar.vehiculo
                WHERE id_usuario = ?
                  AND id_vehiculo <> ?
                  AND estado_vehiculo = 'ACTIVO'
                  AND COALESCE(estado_registro, 'VIGENTE') = 'VIGENTE'
                ORDER BY creado_utc ASC, id_vehiculo ASC
                LIMIT 1
                """, (rs, n) -> new CandidatoPrincipal(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)
        ), idUsuario, idExcluir).stream().findFirst();
    }

    /**
     * Purga datos operativos exclusivos del vehículo (misma TX).
     * Devuelve object keys de almacenamiento privado a borrar fuera de TX (best-effort).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> purgarDatosOperativos(long idVehiculo) {
        // Flag de sesión: permite DELETE en triggers append-only / inmutables (V54).
        jdbc.execute("SELECT set_config('mycar.rf62_purga', '1', true)");

        List<String> objectKeys = jdbc.query("""
                SELECT storage_object_key FROM mycar.soat
                WHERE id_vehiculo = ? AND storage_object_key IS NOT NULL AND btrim(storage_object_key) <> ''
                UNION
                SELECT storage_object_key FROM mycar.tive
                WHERE id_vehiculo = ? AND storage_object_key IS NOT NULL AND btrim(storage_object_key) <> ''
                UNION
                SELECT foto_storage_object_key FROM mycar.vehiculo
                WHERE id_vehiculo = ? AND foto_storage_object_key IS NOT NULL AND btrim(foto_storage_object_key) <> ''
                """, (rs, n) -> rs.getString(1), idVehiculo, idVehiculo, idVehiculo);

        // Alertas → historial / notificaciones / outbox (RESTRICT antes de borrar alerta)
        jdbc.update("""
                DELETE FROM mycar.historial_alerta h
                USING mycar.alerta a
                WHERE h.id_alerta = a.id_alerta AND a.id_vehiculo = ?
                """, idVehiculo);
        jdbc.update("""
                DELETE FROM mycar.outbox_push_alerta o
                USING mycar.alerta a
                WHERE o.id_alerta = a.id_alerta AND a.id_vehiculo = ?
                """, idVehiculo);
        jdbc.update("""
                DELETE FROM mycar.alerta_push_control c
                USING mycar.alerta a
                WHERE c.id_alerta = a.id_alerta AND a.id_vehiculo = ?
                """, idVehiculo);
        // Outbox → notificaciones del vehículo (RF43 KM y resúmenes)
        jdbc.update("""
                DELETE FROM mycar.outbox_push_notificacion opn
                USING mycar.notificacion n
                WHERE opn.id_notificacion = n.id_notificacion AND n.id_vehiculo = ?
                """, idVehiculo);
        jdbc.update("""
                UPDATE mycar.outbox_push o
                SET id_alerta = NULL,
                    id_notificacion = CASE
                        WHEN o.id_notificacion IN (
                            SELECT n.id_notificacion FROM mycar.notificacion n WHERE n.id_vehiculo = ?
                        ) THEN NULL ELSE o.id_notificacion END,
                    estado = CASE WHEN o.estado IN ('PENDIENTE', 'ERROR') THEN 'OMITIDO' ELSE o.estado END,
                    actualizado_utc = clock_timestamp()
                WHERE o.id_alerta IN (SELECT id_alerta FROM mycar.alerta WHERE id_vehiculo = ?)
                   OR o.id_notificacion IN (SELECT id_notificacion FROM mycar.notificacion WHERE id_vehiculo = ?)
                """, idVehiculo, idVehiculo, idVehiculo);
        // Resúmenes pendientes que aún referencian alertas de este vehículo (junction)
        jdbc.update("""
                UPDATE mycar.outbox_push o
                SET estado = 'OMITIDO',
                    actualizado_utc = clock_timestamp()
                WHERE o.estado IN ('PENDIENTE', 'ERROR')
                  AND EXISTS (
                    SELECT 1 FROM mycar.outbox_push_alerta opa
                    JOIN mycar.alerta a ON a.id_alerta = opa.id_alerta
                    WHERE opa.id_outbox = o.id_outbox AND a.id_vehiculo = ?
                  )
                """, idVehiculo);
        jdbc.update("""
                DELETE FROM mycar.notificacion
                WHERE id_vehiculo = ?
                   OR id_alerta IN (SELECT id_alerta FROM mycar.alerta WHERE id_vehiculo = ?)
                """, idVehiculo, idVehiculo);
        jdbc.update("DELETE FROM mycar.alerta WHERE id_vehiculo = ?", idVehiculo);

        // SOAT / TIVE (tras alertas por FK RESTRICT)
        jdbc.update("""
                DELETE FROM mycar.soat_cambio c
                USING mycar.soat s
                WHERE c.id_soat = s.id_soat AND s.id_vehiculo = ?
                """, idVehiculo);
        jdbc.update("DELETE FROM mycar.soat WHERE id_vehiculo = ?", idVehiculo);
        jdbc.update("""
                DELETE FROM mycar.tive_cambio c
                USING mycar.tive t
                WHERE c.id_tive = t.id_tive AND t.id_vehiculo = ?
                """, idVehiculo);
        jdbc.update("DELETE FROM mycar.tive WHERE id_vehiculo = ?", idVehiculo);

        // Mantenimientos y km
        jdbc.update("""
                DELETE FROM mycar.mantenimiento_repuesto mr
                USING mycar.mantenimiento m
                WHERE mr.id_mantenimiento = m.id_mantenimiento AND m.id_vehiculo = ?
                """, idVehiculo);
        jdbc.update("DELETE FROM mycar.mantenimiento WHERE id_vehiculo = ?", idVehiculo);
        // Correcciones RF16 antes de lecturas (FK RESTRICT)
        jdbc.update("DELETE FROM mycar.correccion_lectura_kilometraje WHERE id_vehiculo = ?", idVehiculo);
        jdbc.update("DELETE FROM mycar.lectura_kilometraje WHERE id_vehiculo = ?", idVehiculo);
        jdbc.update("DELETE FROM mycar.config_recordatorio_km WHERE id_vehiculo = ?", idVehiculo);

        // Soporte: conserva expediente, desvincula vehículo
        jdbc.update("UPDATE mycar.solicitud_soporte SET id_vehiculo = NULL WHERE id_vehiculo = ?", idVehiculo);

        jdbc.update("""
                UPDATE mycar.vehiculo
                SET foto_storage_object_key = NULL,
                    foto_content_type = NULL,
                    foto_tamano_bytes = NULL
                WHERE id_vehiculo = ?
                """, idVehiculo);
        return objectKeys;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean marcarEliminado(long idUsuario, long idVehiculo, int versionEsperada) {
        int filas = jdbc.update("""
                UPDATE mycar.vehiculo
                SET estado_registro = 'ELIMINADO',
                    eliminado_en = clock_timestamp(),
                    es_principal = FALSE,
                    version_registro = version_registro + 1,
                    numero_vin = NULL
                WHERE id_usuario = ?
                  AND id_vehiculo = ?
                  AND COALESCE(estado_registro, 'VIGENTE') = 'VIGENTE'
                  AND COALESCE(version_registro, 0) = ?
                """, idUsuario, idVehiculo, versionEsperada);
        return filas == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void limpiarPrincipal(long idUsuario) {
        jdbc.update("""
                UPDATE mycar.vehiculo
                SET es_principal = FALSE
                WHERE id_usuario = ? AND es_principal = TRUE
                """, idUsuario);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean asignarPrincipal(long idUsuario, long idVehiculo) {
        int filas = jdbc.update("""
                UPDATE mycar.vehiculo
                SET es_principal = TRUE
                WHERE id_usuario = ?
                  AND id_vehiculo = ?
                  AND estado_vehiculo = 'ACTIVO'
                  AND COALESCE(estado_registro, 'VIGENTE') = 'VIGENTE'
                """, idUsuario, idVehiculo);
        return filas == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarAuditoria(
            long idUsuario,
            long idVehiculo,
            String estadoOperativoAnterior,
            boolean eraPrincipal,
            UUID idOperacion,
            Long idPrincipalResultante
    ) {
        jdbc.update("""
                INSERT INTO mycar.auditoria_vehiculo_eliminacion (
                    id_usuario, id_vehiculo, estado_operativo_anterior, era_principal,
                    estado_registro_resultante, id_operacion, id_principal_resultante, placa_snapshot
                ) VALUES (?, ?, ?, ?, 'ELIMINADO', ?, ?,
                    (SELECT placa FROM mycar.vehiculo WHERE id_vehiculo = ?))
                ON CONFLICT (id_operacion) DO NOTHING
                """, idUsuario, idVehiculo, estadoOperativoAnterior, eraPrincipal,
                idOperacion, idPrincipalResultante, idVehiculo);
    }

    private long count(String sql, long idVehiculo) {
        Long n = jdbc.queryForObject(sql, Long.class, idVehiculo);
        return n == null ? 0L : n;
    }
}
