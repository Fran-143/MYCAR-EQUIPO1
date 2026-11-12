package backend.talleres.dao;

import backend.talleres.model.DatosTaller.Taller;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TallerJdbcDao implements TallerDao {
    private final JdbcTemplate jdbc;

    public TallerJdbcDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Taller> listarActivos(long idUsuario) {
        return jdbc.query("""
                SELECT id_taller, nombre, telefono, correo, direccion, notas,
                       estado, favorito, version_fila, creado_utc
                FROM mycar.taller
                WHERE id_usuario = ? AND estado = 'ACTIVO'
                ORDER BY favorito DESC, lower(nombre), id_taller
                """, this::map, idUsuario);
    }

    @Override
    public Optional<Taller> propio(long idUsuario, long idTaller) {
        return jdbc.query("""
                SELECT id_taller, nombre, telefono, correo, direccion, notas,
                       estado, favorito, version_fila, creado_utc
                FROM mycar.taller
                WHERE id_usuario = ? AND id_taller = ?
                """, this::map, idUsuario, idTaller).stream().findFirst();
    }

    @Override
    public Optional<Taller> propioActivo(long idUsuario, long idTaller) {
        return propio(idUsuario, idTaller).filter(t -> "ACTIVO".equals(t.estado()));
    }

    @Override
    public boolean existeNombreActivo(long idUsuario, String nombre, long idExcluir) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*)::int FROM mycar.taller
                WHERE id_usuario = ? AND estado = 'ACTIVO' AND nombre = ?
                  AND (? <= 0 OR id_taller <> ?)
                """, Integer.class, idUsuario, nombre, idExcluir, idExcluir);
        return n != null && n > 0;
    }

    @Override
    public Taller insertar(long idUsuario, String nombre, String telefono, String correo,
                           String direccion, String notas) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.taller (
                    id_usuario, nombre, telefono, correo, direccion, notas,
                    estado, favorito, version_fila)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVO', false, 0)
                RETURNING id_taller, nombre, telefono, correo, direccion, notas,
                          estado, favorito, version_fila, creado_utc
                """, this::map, idUsuario, nombre, telefono, correo, direccion, notas);
    }

    @Override
    public boolean actualizar(long idUsuario, long idTaller, int versionFila,
                              String nombre, String telefono, String correo,
                              String direccion, String notas) {
        return jdbc.update("""
                UPDATE mycar.taller
                SET nombre = ?, telefono = ?, correo = ?, direccion = ?, notas = ?,
                    version_fila = version_fila + 1
                WHERE id_taller = ? AND id_usuario = ? AND estado = 'ACTIVO'
                  AND version_fila = ?
                """, nombre, telefono, correo, direccion, notas,
                idTaller, idUsuario, versionFila) == 1;
    }

    @Override
    public boolean retirar(long idUsuario, long idTaller, int versionFila) {
        return jdbc.update("""
                UPDATE mycar.taller
                SET estado = 'INACTIVO', favorito = false, version_fila = version_fila + 1
                WHERE id_taller = ? AND id_usuario = ? AND estado = 'ACTIVO'
                  AND version_fila = ?
                """, idTaller, idUsuario, versionFila) == 1;
    }

    @Override
    public boolean marcarFavoritoAtomico(long idUsuario, long idTaller) {
        Integer ok = jdbc.query("""
                SELECT 1 FROM mycar.taller
                WHERE id_taller = ? AND id_usuario = ? AND estado = 'ACTIVO'
                FOR UPDATE
                """, rs -> rs.next() ? 1 : null, idTaller, idUsuario);
        if (ok == null) return false;
        jdbc.update("""
                UPDATE mycar.taller SET favorito = false, version_fila = version_fila + 1
                WHERE id_usuario = ? AND favorito = true AND estado = 'ACTIVO' AND id_taller <> ?
                """, idUsuario, idTaller);
        return jdbc.update("""
                UPDATE mycar.taller
                SET favorito = true, version_fila = version_fila + 1
                WHERE id_taller = ? AND id_usuario = ? AND estado = 'ACTIVO'
                """, idTaller, idUsuario) == 1;
    }

    @Override
    public boolean desmarcarFavorito(long idUsuario, long idTaller) {
        return jdbc.update("""
                UPDATE mycar.taller
                SET favorito = false, version_fila = version_fila + 1
                WHERE id_taller = ? AND id_usuario = ? AND estado = 'ACTIVO' AND favorito = true
                """, idTaller, idUsuario) == 1;
    }

    private Taller map(ResultSet r, int n) throws SQLException {
        Timestamp ts = r.getTimestamp("creado_utc");
        return new Taller(
                r.getLong("id_taller"),
                r.getString("nombre"),
                r.getString("telefono"),
                r.getString("correo"),
                r.getString("direccion"),
                r.getString("notas"),
                r.getString("estado"),
                r.getBoolean("favorito"),
                r.getInt("version_fila"),
                ts == null ? null : ts.toInstant());
    }
}
