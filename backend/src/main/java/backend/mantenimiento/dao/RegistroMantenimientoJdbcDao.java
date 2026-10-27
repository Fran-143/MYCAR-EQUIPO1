package backend.mantenimiento.dao;

import backend.mantenimiento.model.DatosRepuesto;
import backend.mantenimiento.model.SolicitudRegistroMantenimiento;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class RegistroMantenimientoJdbcDao
        implements RegistroMantenimientoDao {

    private final JdbcTemplate jdbcTemplate;

    public RegistroMantenimientoJdbcDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insertar(
            Long idUsuario,
            Long idTipoMantenimiento,
            SolicitudRegistroMantenimiento solicitud
    ) {
        if (!TransactionSynchronizationManager
                .isActualTransactionActive()) {
            throw new IllegalStateException(
                    "El registro requiere una transacción activa"
            );
        }

        var datos = solicitud.datos();

        Long idMantenimiento = jdbcTemplate.queryForObject(
                """
                INSERT INTO mycar.mantenimiento (
                    id_vehiculo,
                    id_tipo_mantenimiento,
                    km_servicio,
                    fecha_servicio,
                    origen_km_servicio,
                    id_lectura_km_referencia,
                    costo_total,
                    servicio_otro,
                    estado,
                    descripcion,
                    proxima_fecha_sugerida,
                    proximo_km_sugerido
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?,
                    'REALIZADO', ?, ?, ?
                )
                RETURNING id_mantenimiento
                """,
                Long.class,
                solicitud.idVehiculo(),
                idTipoMantenimiento,
                datos.kmServicio(),
                datos.fechaServicio(),
                datos.origenKmServicio().name(),
                datos.idLecturaKmReferencia(),
                datos.costoTotal(),
                datos.servicioOtro(),
                solicitud.observaciones(),
                datos.proximaFechaSugerida(),
                datos.proximoKmSugerido()
        );

        if (idMantenimiento == null) {
            throw new IllegalStateException(
                    "No se obtuvo el identificador del mantenimiento"
            );
        }

        if (solicitud.repuestos() != null) {
            for (DatosRepuesto repuesto : solicitud.repuestos()) {
                insertarRepuesto(
                        idUsuario,
                        idMantenimiento,
                        repuesto
                );
            }
        }

        return idMantenimiento;
    }

    private void insertarRepuesto(
            Long idUsuario,
            Long idMantenimiento,
            DatosRepuesto repuesto
    ) {
        Long idRepuesto = jdbcTemplate.queryForObject(
                """
                INSERT INTO mycar.repuesto (
                    id_usuario,
                    nombre,
                    marca
                )
                VALUES (?, ?, ?)
                RETURNING id_repuesto
                """,
                Long.class,
                idUsuario,
                repuesto.nombre(),
                repuesto.marca()
        );

        if (idRepuesto == null) {
            throw new IllegalStateException(
                    "No se obtuvo el identificador del repuesto"
            );
        }

        jdbcTemplate.update(
                """
                INSERT INTO mycar.mantenimiento_repuesto (
                    id_mantenimiento,
                    id_repuesto,
                    nombre_snapshot,
                    cantidad,
                    precio_unitario
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                idMantenimiento,
                idRepuesto,
                repuesto.nombre(),
                repuesto.cantidad(),
                repuesto.precioUnitario()
        );
    }
}