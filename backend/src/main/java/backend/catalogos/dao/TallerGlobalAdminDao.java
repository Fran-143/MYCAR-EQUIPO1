package backend.catalogos.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
public class TallerGlobalAdminDao {
    public record TallerFila(
            long id, String nombre, String telefono, String correo, String direccion,
            boolean tieneWhatsapp, boolean contactoAutorizado, String estado, boolean esDemo,
            String notasPublicas, Instant actualizadoUtc
    ) { }

    private final JdbcTemplate jdbc;
    private final RowMapper<TallerFila> map = (rs, i) -> new TallerFila(
            rs.getLong("id_taller_global"),
            rs.getString("nombre"),
            rs.getString("telefono"),
            rs.getString("correo"),
            rs.getString("direccion"),
            rs.getBoolean("tiene_whatsapp"),
            rs.getBoolean("contacto_autorizado"),
            rs.getString("estado"),
            rs.getBoolean("es_demo"),
            rs.getString("notas_publicas"),
            toInstant(rs.getTimestamp("actualizado_utc"))
    );

    public TallerGlobalAdminDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TallerFila> listar(String q, String estado) {
        StringBuilder sql = new StringBuilder("""
                SELECT id_taller_global, nombre, telefono, correo, direccion, tiene_whatsapp,
                       COALESCE(contacto_autorizado, false) AS contacto_autorizado, estado,
                       COALESCE(es_demo, false) AS es_demo, notas_publicas, actualizado_utc
                FROM mycar.taller_global WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        String e = estado == null ? "TODOS" : estado.trim().toUpperCase();
        if ("ACTIVOS".equals(e) || "ACTIVO".equals(e)) {
            sql.append(" AND estado = 'ACTIVO'");
        } else if ("INACTIVOS".equals(e) || "INACTIVO".equals(e)) {
            sql.append(" AND estado = 'INACTIVO'");
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (upper(nombre) LIKE ? OR coalesce(upper(telefono),'') LIKE ?)");
            String like = "%" + q.strip().toUpperCase(Locale.ROOT) + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY lower(nombre), id_taller_global");
        return jdbc.query(sql.toString(), map, args.toArray());
    }

    public Optional<TallerFila> porId(long id) {
        return jdbc.query("""
                SELECT id_taller_global, nombre, telefono, correo, direccion, tiene_whatsapp,
                       COALESCE(contacto_autorizado, false) AS contacto_autorizado, estado,
                       COALESCE(es_demo, false) AS es_demo, notas_publicas, actualizado_utc
                FROM mycar.taller_global WHERE id_taller_global = ?
                """, map, id).stream().findFirst();
    }

    public boolean existeNombreActivoNorm(String nombre, Long excluirId) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*)::int FROM mycar.taller_global
                WHERE lower(btrim(nombre)) = lower(btrim(?))
                  AND estado = 'ACTIVO'
                  AND (?::bigint IS NULL OR id_taller_global <> ?)
                """, Integer.class, nombre, excluirId, excluirId == null ? -1L : excluirId);
        return n != null && n > 0;
    }

    public TallerFila insertar(String nombre, String telefono, String correo, String direccion,
            boolean tieneWhatsapp, boolean contactoAutorizado, String notasPublicas, Long idActor) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.taller_global
                    (nombre, telefono, correo, direccion, tiene_whatsapp, contacto_autorizado,
                     es_demo, estado, notas_publicas, id_actor_ultimo)
                VALUES (?, ?, ?, ?, ?, ?, false, 'ACTIVO', ?, ?)
                RETURNING id_taller_global, nombre, telefono, correo, direccion, tiene_whatsapp,
                          COALESCE(contacto_autorizado, false) AS contacto_autorizado, estado,
                          COALESCE(es_demo, false) AS es_demo, notas_publicas, actualizado_utc
                """, map, nombre, telefono, correo, direccion, tieneWhatsapp, contactoAutorizado,
                notasPublicas, idActor);
    }

    public TallerFila actualizar(long id, String nombre, String telefono, String correo, String direccion,
            boolean tieneWhatsapp, boolean contactoAutorizado, String notasPublicas, Long idActor) {
        jdbc.update("""
                UPDATE mycar.taller_global
                SET nombre = ?, telefono = ?, correo = ?, direccion = ?,
                    tiene_whatsapp = ?, contacto_autorizado = ?, notas_publicas = ?,
                    actualizado_utc = clock_timestamp(), id_actor_ultimo = ?
                WHERE id_taller_global = ?
                """, nombre, telefono, correo, direccion, tieneWhatsapp, contactoAutorizado,
                notasPublicas, idActor, id);
        return porId(id).orElseThrow();
    }

    public TallerFila setEstado(long id, String estado, Long idActor) {
        jdbc.update("""
                UPDATE mycar.taller_global
                SET estado = ?, actualizado_utc = clock_timestamp(), id_actor_ultimo = ?
                WHERE id_taller_global = ?
                """, estado, idActor, id);
        return porId(id).orElseThrow();
    }

    public Map<String, Object> toMap(TallerFila f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.id());
        m.put("nombre", f.nombre());
        m.put("telefono", f.telefono());
        m.put("correo", f.correo());
        m.put("direccion", f.direccion());
        m.put("tieneWhatsapp", f.tieneWhatsapp());
        m.put("contactoAutorizado", f.contactoAutorizado());
        m.put("estado", f.estado());
        m.put("esDemo", f.esDemo());
        m.put("notasPublicas", f.notasPublicas());
        m.put("actualizadoUtc", f.actualizadoUtc() == null ? "" : f.actualizadoUtc().toString());
        return m;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
