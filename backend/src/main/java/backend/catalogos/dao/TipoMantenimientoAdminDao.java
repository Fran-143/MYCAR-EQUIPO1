package backend.catalogos.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class TipoMantenimientoAdminDao {
    public record TipoFila(
            long id, String codigo, String nombre, boolean activo,
            Instant creadoUtc, Instant actualizadoUtc
    ) { }

    private final JdbcTemplate jdbc;
    private final RowMapper<TipoFila> map = (rs, i) -> new TipoFila(
            rs.getLong("id_tipo_mantenimiento"),
            rs.getString("codigo"),
            rs.getString("nombre"),
            rs.getBoolean("activo"),
            toInstant(rs.getTimestamp("creado_utc")),
            toInstant(rs.getTimestamp("actualizado_utc"))
    );

    public TipoMantenimientoAdminDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TipoFila> listar(String q, String estado) {
        StringBuilder sql = new StringBuilder("""
                SELECT id_tipo_mantenimiento, codigo, nombre, activo, creado_utc, actualizado_utc
                FROM mycar.tipo_mantenimiento WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        String e = estado == null ? "TODOS" : estado.trim().toUpperCase();
        if ("ACTIVOS".equals(e)) {
            sql.append(" AND activo = true");
        } else if ("INACTIVOS".equals(e)) {
            sql.append(" AND activo = false");
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (lower(nombre) LIKE ? OR lower(codigo) LIKE ?)");
            String like = "%" + q.strip().toLowerCase() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY lower(nombre), id_tipo_mantenimiento");
        return jdbc.query(sql.toString(), map, args.toArray());
    }

    public Optional<TipoFila> porId(long id) {
        return jdbc.query("""
                SELECT id_tipo_mantenimiento, codigo, nombre, activo, creado_utc, actualizado_utc
                FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento = ?
                """, map, id).stream().findFirst();
    }

    public boolean existeNombreNorm(String nombre, Long excluirId) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*)::int FROM mycar.tipo_mantenimiento
                WHERE lower(btrim(nombre)) = lower(btrim(?))
                  AND (?::bigint IS NULL OR id_tipo_mantenimiento <> ?)
                """, Integer.class, nombre, excluirId, excluirId == null ? -1L : excluirId);
        return n != null && n > 0;
    }

    public boolean existeCodigo(String codigo) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*)::int FROM mycar.tipo_mantenimiento WHERE codigo = ?",
                Integer.class, codigo);
        return n != null && n > 0;
    }

    public TipoFila insertar(String codigo, String nombre, Long idActor) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.tipo_mantenimiento (codigo, nombre, activo, id_actor_ultimo)
                VALUES (?, ?, true, ?)
                RETURNING id_tipo_mantenimiento, codigo, nombre, activo, creado_utc, actualizado_utc
                """, map, codigo, nombre, idActor);
    }

    public TipoFila actualizarNombre(long id, String nombre, Long idActor) {
        jdbc.update("""
                UPDATE mycar.tipo_mantenimiento
                SET nombre = ?, actualizado_utc = clock_timestamp(), id_actor_ultimo = ?
                WHERE id_tipo_mantenimiento = ?
                """, nombre, idActor, id);
        return porId(id).orElseThrow();
    }

    public TipoFila setActivo(long id, boolean activo, Long idActor) {
        jdbc.update("""
                UPDATE mycar.tipo_mantenimiento
                SET activo = ?, actualizado_utc = clock_timestamp(), id_actor_ultimo = ?
                WHERE id_tipo_mantenimiento = ?
                """, activo, idActor, id);
        return porId(id).orElseThrow();
    }

    public Map<String, Object> toMap(TipoFila f) {
        return Map.of(
                "id", f.id(),
                "codigo", f.codigo(),
                "nombre", f.nombre(),
                "activo", f.activo(),
                "creadoUtc", f.creadoUtc() == null ? "" : f.creadoUtc().toString(),
                "actualizadoUtc", f.actualizadoUtc() == null ? "" : f.actualizadoUtc().toString()
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
