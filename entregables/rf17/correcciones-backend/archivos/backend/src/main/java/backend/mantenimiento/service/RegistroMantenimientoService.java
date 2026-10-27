package backend.mantenimiento.service;

import backend.mantenimiento.dao.RegistroMantenimientoDao;
import backend.mantenimiento.integracion.JsonOperacionMantenimiento;
import backend.mantenimiento.dao.OperacionMantenimientoDao;
import backend.mantenimiento.model.OperacionMantenimiento;
import backend.mantenimiento.model.ResultadoRegistroMantenimiento;
import backend.mantenimiento.model.EstadoOperacionRegistro;
import backend.mantenimiento.model.ConflictoOperacionMantenimientoException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
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
    private final OperacionMantenimientoDao operaciones;

    public RegistroMantenimientoService(
            ValidadorRegistroMantenimiento validador,
            ValidadorRepuesto validadorRepuesto,
            VehiculoMantenimientoService vehiculos,
            TipoMantenimientoService tipos,
            LecturaKmMantenimientoService lecturas,
            RegistroMantenimientoDao dao,
            OperacionMantenimientoDao operaciones
    ) {
        this.validador = validador;
        this.validadorRepuesto = validadorRepuesto;
        this.vehiculos = vehiculos;
        this.tipos = tipos;
        this.lecturas = lecturas;
        this.dao = dao;
        this.operaciones = operaciones;
    }

    @Transactional
    public Long registrar(
            Long idUsuario,
            SolicitudRegistroMantenimiento solicitud
    ) {
        return registrarResultado(idUsuario, solicitud).idMantenimiento();
    }

    @Transactional
    public ResultadoRegistroMantenimiento registrarResultado(
            Long idUsuario, SolicitudRegistroMantenimiento solicitud) {
        if (idUsuario == null || idUsuario <= 0) {
            throw new IllegalArgumentException("idUsuario: identidad autenticada requerida");
        }
        if (solicitud == null) {
            throw new IllegalArgumentException(
                    "solicitud: es obligatoria"
            );
        }
        if (solicitud.idVehiculo() == null || solicitud.idVehiculo() <= 0) {
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

        if (solicitud.idOperacion() == null) {
            throw new IllegalArgumentException("idOperacion: es obligatoria");
        }
        // Una copia fija evita que la lista cambie entre la huella y la escritura.
        solicitud = new SolicitudRegistroMantenimiento(solicitud.idOperacion(),
                solicitud.idVehiculo(), datos, solicitud.observaciones(),
                solicitud.repuestos() == null ? null
                        : Collections.unmodifiableList(new ArrayList<>(solicitud.repuestos())));
        String huella = JsonOperacionMantenimiento.huella(solicitud);
        boolean nueva = operaciones.intentarCrear(solicitud.idOperacion(), idUsuario, huella);
        var operacion = operaciones.buscarYBloquear(solicitud.idOperacion())
                .orElseThrow(() -> new IllegalStateException("No se encontró la operación RF17"));
        exigirPropietarioYTipo(idUsuario, operacion);
        if (!Objects.equals(operacion.hashSolicitud(), huella)) {
            throw new ConflictoOperacionMantenimientoException();
        }
        // El resultado confirmado se devuelve antes de revalidar catálogos o lecturas.
        if ("CONFIRMADA".equals(operacion.estado())) {
            return resultadoConfirmado(operacion);
        }
        if (!nueva || !"EN_PROCESO".equals(operacion.estado())) {
            throw new ConflictoOperacionMantenimientoException();
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

        Long idMantenimiento = dao.insertar(
                idUsuario,
                tipo.idTipoMantenimiento(),
                solicitud
        );
        var resultado = new ResultadoRegistroMantenimiento(solicitud.idOperacion(),
                solicitud.idVehiculo(), idMantenimiento, "REALIZADO", datos.kmServicio(),
                datos.fechaServicio(), datos.costoTotal(),
                solicitud.repuestos() == null ? 0 : solicitud.repuestos().size());
        operaciones.confirmar(solicitud.idOperacion(), idUsuario, resultado);
        return resultado;
    }

    @Transactional
    public EstadoOperacionRegistro consultarOperacion(Long usuario, UUID id) {
        if (usuario == null || usuario <= 0 || id == null) {
            throw new IllegalArgumentException("Usuario y operación requeridos");
        }
        var encontrada = operaciones.buscarYBloquear(id);
        if (encontrada.isEmpty()) {
            return new EstadoOperacionRegistro(id, "NO_ENCONTRADA", null);
        }
        var operacion = encontrada.get();
        exigirPropietarioYTipo(usuario, operacion);
        return new EstadoOperacionRegistro(id, operacion.estado(),
                "CONFIRMADA".equals(operacion.estado()) ? resultadoConfirmado(operacion) : null);
    }

    private void exigirPropietarioYTipo(Long usuario, OperacionMantenimiento operacion) {
        if (!Objects.equals(usuario, operacion.idUsuario())
                || !"REGISTRAR_MANTENIMIENTO".equals(operacion.tipoOperacion())) {
            throw new ConflictoOperacionMantenimientoException();
        }
    }

    private ResultadoRegistroMantenimiento resultadoConfirmado(OperacionMantenimiento operacion) {
        var resultado = JsonOperacionMantenimiento.leerResultado(operacion.respuestaJson());
        if (resultado == null || !Objects.equals(resultado.idOperacion(), operacion.idOperacion())) {
            throw new IllegalStateException("El resultado no corresponde a la operación RF17");
        }
        return resultado;
    }
}
