package backend.catalogos.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogoAuditoriaDao {
    private final JdbcTemplate jdbc;

    public CatalogoAuditoriaDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registrar(String catalogo, long idElemento, String accion,
            String valorAnterior, String valorNuevo, Long idAdmin, String resultado) {
        jdbc.update("""
                INSERT INTO mycar.catalogo_auditoria
                    (catalogo, id_elemento, accion, valor_anterior, valor_nuevo, id_admin, resultado)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, catalogo, idElemento, accion, trunc(valorAnterior), trunc(valorNuevo),
                idAdmin, resultado == null ? "OK" : resultado);
    }

    private static String trunc(String v) {
        if (v == null) return null;
        return v.length() <= 1000 ? v : v.substring(0, 1000);
    }
}
