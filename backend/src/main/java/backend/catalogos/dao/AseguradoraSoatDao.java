package backend.catalogos.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class AseguradoraSoatDao {
    public record AseguradoraFila(
            long id, String codigo, String nombre, boolean activo, boolean permiteNombreLibre,
            String aliasesCsv, String ayudaPoliza, String ejemploPoliza,
            Instant creadoUtc, Instant actualizadoUtc
    ) {
        public Set<String> aliases() {
            if (aliasesCsv == null || aliasesCsv.isBlank()) return Set.of();
            return Set.of(aliasesCsv.split("\\s*,\\s*"));
        }
    }

    private final JdbcTemplate jdbc;
    private final RowMapper<AseguradoraFila> map = (rs, i) -> new AseguradoraFila(
            rs.getLong("id_aseguradora"),
            rs.getString("codigo"),
            rs.getString("nombre"),
            rs.getBoolean("activo"),
            rs.getBoolean("permite_nombre_libre"),
            rs.getString("aliases_csv"),
            rs.getString("ayuda_poliza"),
            rs.getString("ejemplo_poliza"),
            toInstant(rs.getTimestamp("creado_utc")),
            toInstant(rs.getTimestamp("actualizado_utc"))
    );

    public AseguradoraSoatDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AseguradoraFila> listarAdmin(String q, String estado) {
        StringBuilder sql = new StringBuilder("""
                SELECT id_aseguradora, codigo, nombre, activo, permite_nombre_libre, aliases_csv,
                       ayuda_poliza, ejemplo_poliza, creado_utc, actualizado_utc
                FROM mycar.aseguradora_soat WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        String e = estado == null ? "TODOS" : estado.trim().toUpperCase();
        if ("ACTIVOS".equals(e)) sql.append(" AND activo = true");
        else if ("INACTIVOS".equals(e)) sql.append(" AND activo = false");
        if (q != null && !q.isBlank()) {
            sql.append(" AND (lower(nombre) LIKE ? OR lower(codigo) LIKE ?)");
            String like = "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY activo DESC, lower(nombre), id_aseguradora");
        return jdbc.query(sql.toString(), map, args.toArray());
    }

    public List<AseguradoraFila> listarActivasUsuario() {
        return jdbc.query("""
                SELECT id_aseguradora, codigo, nombre, activo, permite_nombre_libre, aliases_csv,
                       ayuda_poliza, ejemplo_poliza, creado_utc, actualizado_utc
                FROM mycar.aseguradora_soat
                WHERE activo = true
                ORDER BY CASE WHEN permite_nombre_libre THEN 1 ELSE 0 END, lower(nombre), id_aseguradora
                """, map);
    }

    public List<AseguradoraFila> listarTodas() {
        return jdbc.query("""
                SELECT id_aseguradora, codigo, nombre, activo, permite_nombre_libre, aliases_csv,
                       ayuda_poliza, ejemplo_poliza, creado_utc, actualizado_utc
                FROM mycar.aseguradora_soat
                """, map);
    }

    public Optional<AseguradoraFila> porId(long id) {
        return jdbc.query("""
                SELECT id_aseguradora, codigo, nombre, activo, permite_nombre_libre, aliases_csv,
                       ayuda_poliza, ejemplo_poliza, creado_utc, actualizado_utc
                FROM mycar.aseguradora_soat WHERE id_aseguradora = ?
                """, map, id).stream().findFirst();
    }

    public boolean existeNombreNorm(String nombre, Long excluirId) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*)::int FROM mycar.aseguradora_soat
                WHERE lower(btrim(nombre)) = lower(btrim(?))
                  AND (?::bigint IS NULL OR id_aseguradora <> ?)
                """, Integer.class, nombre, excluirId, excluirId == null ? -1L : excluirId);
        return n != null && n > 0;
    }

    public boolean existeCodigo(String codigo, Long excluirId) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*)::int FROM mycar.aseguradora_soat
                WHERE upper(btrim(codigo)) = upper(btrim(?))
                  AND (?::bigint IS NULL OR id_aseguradora <> ?)
                """, Integer.class, codigo, excluirId, excluirId == null ? -1L : excluirId);
        return n != null && n > 0;
    }

    public AseguradoraFila insertar(String codigo, String nombre, Long idActor) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.aseguradora_soat
                    (codigo, nombre, activo, permite_nombre_libre, id_actor_ultimo)
                VALUES (?, ?, true, false, ?)
                RETURNING id_aseguradora, codigo, nombre, activo, permite_nombre_libre, aliases_csv,
                          ayuda_poliza, ejemplo_poliza, creado_utc, actualizado_utc
                """, map, codigo, nombre, idActor);
    }

    public AseguradoraFila actualizarNombre(long id, String nombre, Long idActor) {
        jdbc.update("""
                UPDATE mycar.aseguradora_soat
                SET nombre = ?, actualizado_utc = clock_timestamp(), id_actor_ultimo = ?
                WHERE id_aseguradora = ?
                """, nombre, idActor, id);
        return porId(id).orElseThrow();
    }

    public AseguradoraFila setActivo(long id, boolean activo, Long idActor) {
        jdbc.update("""
                UPDATE mycar.aseguradora_soat
                SET activo = ?, actualizado_utc = clock_timestamp(), id_actor_ultimo = ?
                WHERE id_aseguradora = ?
                """, activo, idActor, id);
        return porId(id).orElseThrow();
    }

    public Map<String, Object> toMap(AseguradoraFila f) {
        return Map.of(
                "id", f.id(),
                "codigo", f.codigo(),
                "nombre", f.nombre(),
                "activo", f.activo(),
                "permiteNombreLibre", f.permiteNombreLibre(),
                "creadoUtc", f.creadoUtc() == null ? "" : f.creadoUtc().toString(),
                "actualizadoUtc", f.actualizadoUtc() == null ? "" : f.actualizadoUtc().toString()
        );
    }

    public Map<String, Object> toMapUsuario(AseguradoraFila f) {
        return Map.of(
                "id", f.id(),
                "codigo", f.codigo(),
                "nombre", f.nombre(),
                "permiteNombreLibre", f.permiteNombreLibre(),
                "ayudaPoliza", f.ayudaPoliza() == null ? "" : f.ayudaPoliza(),
                "ejemploPoliza", f.ejemploPoliza() == null ? "" : f.ejemploPoliza()
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
