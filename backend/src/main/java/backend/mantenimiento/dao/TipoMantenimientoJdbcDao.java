package backend.mantenimiento.dao;

import backend.mantenimiento.model.TipoMantenimiento;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class TipoMantenimientoJdbcDao implements TipoMantenimientoDao {

    private final JdbcTemplate jdbcTemplate;

    public TipoMantenimientoJdbcDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<TipoMantenimiento> buscarPorCodigo(String codigo) {
        String sql = """
                SELECT id_tipo_mantenimiento, codigo, nombre, activo
                FROM mycar.tipo_mantenimiento
                WHERE codigo = ?
                """;

        List<TipoMantenimiento> tipos = jdbcTemplate.query(
                sql,
                (rs, fila) -> new TipoMantenimiento(
                        rs.getLong("id_tipo_mantenimiento"),
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        rs.getBoolean("activo")
                ),
                codigo
        );

        return tipos.stream().findFirst();
    }
}