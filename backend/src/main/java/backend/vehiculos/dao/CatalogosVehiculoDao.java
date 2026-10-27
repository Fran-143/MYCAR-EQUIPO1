package backend.vehiculos.dao;

import backend.vehiculos.dto.DatosCatalogosVehiculo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CatalogosVehiculoDao {

    private final JdbcTemplate jdbcTemplate;

    public CatalogosVehiculoDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public DatosCatalogosVehiculo consultar() {

        var usos = jdbcTemplate.query(
                """
                SELECT codigo, nombre
                FROM mycar.uso_vehiculo
                ORDER BY nombre, codigo
                """,
                (fila, numeroFila) ->
                        new DatosCatalogosVehiculo.UsoVehiculo(
                                fila.getString("codigo"),
                                fila.getString("nombre")
                        )
        );

        var tiposMotor = jdbcTemplate.query(
                """
                SELECT id_tipo_motor, codigo, nombre, es_electrico
                FROM mycar.tipo_motor
                ORDER BY nombre, codigo
                """,
                (fila, numeroFila) ->
                        new DatosCatalogosVehiculo.TipoMotor(
                                fila.getLong("id_tipo_motor"),
                                fila.getString("codigo"),
                                fila.getString("nombre"),
                                fila.getBoolean("es_electrico")
                        )
        );

        var combustibles = jdbcTemplate.query(
                """
                SELECT id_combustible, codigo, nombre
                FROM mycar.combustible
                ORDER BY nombre, codigo
                """,
                (fila, numeroFila) ->
                        new DatosCatalogosVehiculo.Combustible(
                                fila.getLong("id_combustible"),
                                fila.getString("codigo"),
                                fila.getString("nombre")
                        )
        );

        var tiposAceite = jdbcTemplate.query(
                """
                SELECT id_tipo_aceite, codigo, nombre
                FROM mycar.tipo_aceite
                ORDER BY nombre, codigo
                """,
                (fila, numeroFila) ->
                        new DatosCatalogosVehiculo.TipoAceite(
                                fila.getLong("id_tipo_aceite"),
                                fila.getString("codigo"),
                                fila.getString("nombre")
                        )
        );

        return new DatosCatalogosVehiculo(
                usos,
                tiposMotor,
                combustibles,
                tiposAceite
        );
    }
}