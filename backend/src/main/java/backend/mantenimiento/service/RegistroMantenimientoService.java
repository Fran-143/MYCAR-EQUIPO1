package backend.mantenimiento.service;

import backend.mantenimiento.dao.RegistroMantenimientoDao;
import backend.mantenimiento.model.DatosRegistroMantenimiento;
import backend.mantenimiento.model.DatosRepuesto;
import backend.mantenimiento.model.SolicitudRegistroMantenimiento;
import backend.mantenimiento.model.TipoMantenimiento;
import org.springframework.transaction.annotation.Transactional;
public class RegistroMantenimientoService {

    private final ValidadorRegistroMantenimiento validador;
    private final ValidadorRepuesto validadorRepuesto;
    private final VehiculoMantenimientoService vehiculos;
    private final TipoMantenimientoService tipos;
    private final LecturaKmMantenimientoService lecturas;
    private final RegistroMantenimientoDao dao;

    public RegistroMantenimientoService(
            ValidadorRegistroMantenimiento validador,
            ValidadorRepuesto validadorRepuesto,
            VehiculoMantenimientoService vehiculos,
            TipoMantenimientoService tipos,
            LecturaKmMantenimientoService lecturas,
            RegistroMantenimientoDao dao
    ) {
        this.validador = validador;
        this.validadorRepuesto = validadorRepuesto;
        this.vehiculos = vehiculos;
        this.tipos = tipos;
        this.lecturas = lecturas;
        this.dao = dao;
    }

    @Transactional
    public Long registrar(
            Long idUsuario,
            SolicitudRegistroMantenimiento solicitud
    ) {
        if (solicitud == null) {
            throw new IllegalArgumentException(
                    "solicitud: es obligatoria"
            );
        }
        if (solicitud.idVehiculo() == null) {
            throw new IllegalArgumentException(
                    "idVehiculo: es obligatorio"
            );
        }
        DatosRegistroMantenimiento datos = solicitud.datos();
        if (datos == null) {
            throw new IllegalArgumentException(
                    "datos: el mantenimiento es obligatorio"
            );
        }

        validador.validar(datos);

        if (solicitud.repuestos() != null) {
            for (DatosRepuesto repuesto : solicitud.repuestos()) {
                validadorRepuesto.validar(repuesto);
            }
        }

        vehiculos.obtenerVehiculoPropioActivoParaRegistro(
                idUsuario,
                solicitud.idVehiculo()
        );

        TipoMantenimiento tipo = tipos.obtenerTipoActivo(
                datos.codigoTipoMantenimiento()
        );

        lecturas.validarReferencia(
                solicitud.idVehiculo(),
                datos
        );

        return dao.insertar(
                idUsuario,
                tipo.idTipoMantenimiento(),
                solicitud
        );
    }
}
