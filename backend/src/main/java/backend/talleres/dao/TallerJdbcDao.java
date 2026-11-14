package backend.talleres.dao;

import backend.talleres.model.DatosTaller.Taller;
import backend.talleres.model.DatosTaller.TallerGlobal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TallerJdbcDao implements TallerDao {
    private static final String COLS = """
            id_taller, nombre, telefono, correo, direccion, notas,
            estado, favorito, version_fila, creado_utc, origen, tiene_whatsapp, id_taller_global
            """;

    private final JdbcTemplate jdbc;

    public TallerJdbcDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Taller> listarActivos(long idUsuario, String filtroOrigen) {
        String origen = filtroOrigen == null ? "TODOS" : filtroOrigen.trim().toUpperCase();
        if ("MANUAL".equals(origen) || "ASOCIADO".equals(origen)) {
            return jdbc.query("""
                    SELECT %s FROM mycar.taller
                    WHERE id_usuario = ? AND estado = 'ACTIVO' AND origen = ?
                    ORDER BY favorito DESC, lower(nombre), id_taller
                    """.formatted(COLS), this::map, idUsuario, origen);
        }
        return jdbc.query("""
                SELECT %s FROM mycar.taller
                WHERE id_usuario = ? AND estado = 'ACTIVO'
                ORDER BY favorito DESC, lower(nombre), id_taller
                """.formatted(COLS), this::map, idUsuario);
    }

    @Override
    public Optional<Taller> propio(long idUsuario, long idTaller) {
        return jdbc.query("""
                SELECT %s FROM mycar.taller
                WHERE id_usuario = ? AND id_taller = ?
                """.formatted(COLS), this::map, idUsuario, idTaller).stream().findFirst();
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
    public boolean existeTelefonoManualActivo(long idUsuario, String telefono, long idExcluir) {
        if (telefono == null || telefono.isBlank()) return false;
        Integer n = jdbc.queryForObject("""
                SELECT count(*)::int FROM mycar.taller
                WHERE id_usuario = ? AND estado = 'ACTIVO' AND origen = 'MANUAL'
                  AND telefono = ? AND (? <= 0 OR id_taller <> ?)
                """, Integer.class, idUsuario, telefono, idExcluir, idExcluir);
        return n != null && n > 0;
    }

    @Override
    public Taller insertarManual(long idUsuario, String nombre, String telefono, String correo,
                                 String direccion, String notas, boolean tieneWhatsapp) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.taller (
                    id_usuario, nombre, telefono, correo, direccion, notas,
                    estado, favorito, version_fila, origen, tiene_whatsapp, id_taller_global)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVO', false, 0, 'MANUAL', ?, NULL)
                RETURNING %s
                """.formatted(COLS), this::map,
                idUsuario, nombre, telefono, correo, direccion, notas, tieneWhatsapp);
    }

    @Override
    public Optional<TallerGlobal> globalActivo(long idTallerGlobal) {
        return jdbc.query("""
                SELECT id_taller_global, nombre, telefono, correo, direccion, tiene_whatsapp, estado,
                       COALESCE(es_demo, false) AS es_demo
                FROM mycar.taller_global
                WHERE id_taller_global = ? AND estado = 'ACTIVO'
                """, this::mapGlobal, idTallerGlobal).stream().findFirst();
    }

    @Override
    public List<TallerGlobal> listarGlobalesActivos(long idUsuario, String busqueda, long idUsuarioPilotoDemo) {
        String q = busqueda == null ? "" : busqueda.trim();
        boolean verDemo = idUsuario > 0 && idUsuario == idUsuarioPilotoDemo;
        if (q.isEmpty()) {
            return jdbc.query("""
                    SELECT id_taller_global, nombre, telefono, correo, direccion, tiene_whatsapp, estado,
                           COALESCE(es_demo, false) AS es_demo
                    FROM mycar.taller_global
                    WHERE estado = 'ACTIVO'
                      AND (COALESCE(es_demo, false) = false OR ?)
                    ORDER BY lower(nombre), id_taller_global
                    """, this::mapGlobal, verDemo);
        }
        String like = "%" + q.toUpperCase() + "%";
        return jdbc.query("""
                SELECT id_taller_global, nombre, telefono, correo, direccion, tiene_whatsapp, estado,
                       COALESCE(es_demo, false) AS es_demo
                FROM mycar.taller_global
                WHERE estado = 'ACTIVO'
                  AND (COALESCE(es_demo, false) = false OR ?)
                  AND upper(nombre) LIKE ?
                ORDER BY lower(nombre), id_taller_global
                """, this::mapGlobal, verDemo, like);
    }

    @Override
    public boolean existeVinculoAsociadoActivo(long idUsuario, long idTallerGlobal) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*)::int FROM mycar.taller
                WHERE id_usuario = ? AND id_taller_global = ?
                  AND origen = 'ASOCIADO' AND estado = 'ACTIVO'
                """, Integer.class, idUsuario, idTallerGlobal);
        return n != null && n > 0;
    }

    @Override
    public Taller insertarAsociado(long idUsuario, long idTallerGlobal, String nombre, String telefono,
                                   String correo, String direccion, boolean tieneWhatsapp) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.taller (
                    id_usuario, nombre, telefono, correo, direccion, notas,
                    estado, favorito, version_fila, origen, tiene_whatsapp, id_taller_global)
                VALUES (?, ?, ?, ?, ?, NULL, 'ACTIVO', false, 0, 'ASOCIADO', ?, ?)
                RETURNING %s
                """.formatted(COLS), this::map,
                idUsuario, nombre, telefono, correo, direccion, tieneWhatsapp, idTallerGlobal);
    }

    @Override
    public boolean actualizarManual(long idUsuario, long idTaller, int versionFila,
                                    String nombre, String telefono, String correo,
                                    String direccion, String notas, boolean tieneWhatsapp) {
        return jdbc.update("""
                UPDATE mycar.taller
                SET nombre = ?, telefono = ?, correo = ?, direccion = ?, notas = ?,
                    tiene_whatsapp = ?, version_fila = version_fila + 1
                WHERE id_taller = ? AND id_usuario = ? AND estado = 'ACTIVO'
                  AND origen = 'MANUAL' AND version_fila = ?
                """, nombre, telefono, correo, direccion, notas, tieneWhatsapp,
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
        // Bloqueo por usuario (todas las filas ACTIVO, orden estable) para serializar
        // marcas concurrentes sin tocar talleres de otros usuarios.
        var ids = jdbc.query("""
                SELECT id_taller FROM mycar.taller
                WHERE id_usuario = ? AND estado = 'ACTIVO'
                ORDER BY id_taller
                FOR UPDATE
                """, (rs, n) -> rs.getLong(1), idUsuario);
        if (ids.stream().noneMatch(id -> id == idTaller)) return false;
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

    @Override
    public Optional<Taller> favoritoActivo(long idUsuario) {
        return jdbc.query("""
                SELECT %s FROM mycar.taller
                WHERE id_usuario = ? AND estado = 'ACTIVO' AND favorito = true
                ORDER BY id_taller LIMIT 1
                """.formatted(COLS), this::map, idUsuario).stream().findFirst();
    }

    private Taller map(ResultSet r, int n) throws SQLException {
        Timestamp ts = r.getTimestamp("creado_utc");
        String origen = r.getString("origen");
        if (origen == null || origen.isBlank()) origen = "MANUAL";
        boolean editable = "MANUAL".equals(origen);
        Long idGlobal = r.getObject("id_taller_global") == null ? null : r.getLong("id_taller_global");
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
                ts == null ? null : ts.toInstant(),
                origen,
                r.getBoolean("tiene_whatsapp"),
                idGlobal,
                editable);
    }

    private TallerGlobal mapGlobal(ResultSet r, int n) throws SQLException {
        boolean esDemo = false;
        try {
            esDemo = r.getBoolean("es_demo");
        } catch (SQLException ignored) {
            // Columnas legacy en tests sin es_demo.
        }
        return new TallerGlobal(
                r.getLong("id_taller_global"),
                r.getString("nombre"),
                r.getString("telefono"),
                r.getString("correo"),
                r.getString("direccion"),
                r.getBoolean("tiene_whatsapp"),
                r.getString("estado"),
                esDemo);
    }
}
