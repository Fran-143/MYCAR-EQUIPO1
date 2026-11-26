package backend.notificaciones.dao;

import backend.notificaciones.api.dto.ContratoPreferenciasNotificacion;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PreferenciaNotificacionJdbcDao {
    private final JdbcTemplate jdbc;

    public PreferenciaNotificacionJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ContratoPreferenciasNotificacion.PreferenciasResponse> consultar(long usuario) {
        return jdbc.query("""
                SELECT mantenimiento_on, soat_on, kilometraje_on, permiso_dispositivo
                FROM mycar.preferencia_notificacion
                WHERE id_usuario = ?
                """, (rs, n) -> new ContratoPreferenciasNotificacion.PreferenciasResponse(
                rs.getBoolean("mantenimiento_on"),
                rs.getBoolean("soat_on"),
                rs.getBoolean("kilometraje_on"),
                rs.getString("permiso_dispositivo")), usuario).stream().findFirst();
    }

    /** Asegura fila RF01; no borra ni toca avisos internos. */
    public void asegurarFila(long usuario) {
        jdbc.update("""
                INSERT INTO mycar.preferencia_notificacion (
                    id_usuario, mantenimiento_on, soat_on, kilometraje_on, permiso_dispositivo)
                VALUES (?, TRUE, TRUE, TRUE, 'NO_SOLICITADO')
                ON CONFLICT (id_usuario) DO NOTHING
                """, usuario);
    }

    public ContratoPreferenciasNotificacion.PreferenciasResponse actualizar(
            long usuario, boolean mantenimiento, boolean soat, boolean kilometraje, String permiso) {
        asegurarFila(usuario);
        jdbc.update("""
                UPDATE mycar.preferencia_notificacion
                SET mantenimiento_on = ?, soat_on = ?, kilometraje_on = ?, permiso_dispositivo = ?
                WHERE id_usuario = ?
                """, mantenimiento, soat, kilometraje, permiso, usuario);
        return consultar(usuario).orElseThrow();
    }

    /** EA04: solo permiso, sin tocar switches de categoría. */
    public ContratoPreferenciasNotificacion.PreferenciasResponse actualizarSoloPermiso(
            long usuario, String permiso) {
        asegurarFila(usuario);
        jdbc.update("""
                UPDATE mycar.preferencia_notificacion
                SET permiso_dispositivo = ?
                WHERE id_usuario = ?
                """, permiso, usuario);
        return consultar(usuario).orElseThrow();
    }
}
