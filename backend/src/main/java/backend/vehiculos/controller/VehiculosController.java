package backend.vehiculos.controller;

import backend.vehiculos.dto.DatosActualizacionVehiculo;
import backend.vehiculos.dto.DatosCambioEstadoVehiculo;
import backend.vehiculos.dto.DatosCatalogosVehiculo;
import backend.vehiculos.dto.DatosDetalleVehiculo;
import backend.vehiculos.dto.DatosListadoVehiculos;
import backend.vehiculos.dto.DatosCupoRegistro;
import backend.vehiculos.dto.DatosEstadoOperacionVehiculo;
import backend.vehiculos.dto.DatosRegistroVehiculo;
import backend.vehiculos.dto.ResultadoRegistroVehiculo;
import backend.vehiculos.seguridad.IdentidadVehiculos;
import backend.vehiculos.seguridad.SesionVehiculosNoValidada;
import backend.vehiculos.service.ActualizacionVehiculoService;
import backend.vehiculos.service.ConsultaRegistroVehiculoService;
import backend.vehiculos.service.EstadoOperativoVehiculoService;
import backend.vehiculos.service.RegistroVehiculoService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehiculos")
public class VehiculosController {
    private final ObjectProvider<IdentidadVehiculos> identidades;
    private final RegistroVehiculoService registro;
    private final ConsultaRegistroVehiculoService consultas;
    private final ActualizacionVehiculoService actualizacion;
    private final EstadoOperativoVehiculoService estadoOperativo;

    public VehiculosController(
            ObjectProvider<IdentidadVehiculos> identidades,
            RegistroVehiculoService registro,
            ConsultaRegistroVehiculoService consultas,
            ActualizacionVehiculoService actualizacion,
            EstadoOperativoVehiculoService estadoOperativo
    ) {
        this.identidades = identidades;
        this.registro = registro;
        this.consultas = consultas;
        this.actualizacion = actualizacion;
        this.estadoOperativo = estadoOperativo;
    }

    public record IdentidadCuenta(long idUsuario) { }

    /** Identidad local confirmada por el servidor; no es una credencial ni acepta un id libre. */
    @GetMapping("/identidad")
    public org.springframework.http.ResponseEntity<IdentidadCuenta> identidad(Authentication autenticacion) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new IdentidadCuenta(usuario(autenticacion)));
    }

    @GetMapping("/{idVehiculo}")
    public DatosDetalleVehiculo detalle(Authentication autenticacion, @PathVariable long idVehiculo) {
        return consultas.consultarDetalle(usuario(autenticacion), idVehiculo);
    }

    @PutMapping("/{idVehiculo}")
    public DatosDetalleVehiculo actualizar(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @RequestBody DatosActualizacionVehiculo datos
    ) {
        return actualizacion.actualizar(usuario(autenticacion), idVehiculo, datos);
    }

    /** RF13: establece el vehículo como principal exclusivo (solo ACTIVO propio). */
    @PutMapping("/{idVehiculo}/principal")
    public DatosDetalleVehiculo establecerPrincipal(
            Authentication autenticacion, @PathVariable long idVehiculo
    ) {
        return estadoOperativo.establecerPrincipal(usuario(autenticacion), idVehiculo);
    }

    /** RF12: activa o desactiva un vehículo propio. */
    @PutMapping("/{idVehiculo}/estado")
    public DatosDetalleVehiculo cambiarEstado(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @RequestBody DatosCambioEstadoVehiculo datos
    ) {
        return estadoOperativo.cambiarEstado(
                usuario(autenticacion),
                idVehiculo,
                datos == null ? null : datos.estadoVehiculo()
        );
    }

    @GetMapping
    public DatosListadoVehiculos listar(
            Authentication autenticacion,
            @RequestParam(required = false) String filtro,
            @RequestParam(required = false) String busqueda
    ) {
        return consultas.listarVehiculos(usuario(autenticacion), filtro, busqueda);
    }

    @GetMapping("/catalogos")
    public DatosCatalogosVehiculo catalogos(Authentication autenticacion) {
        return consultas.consultarCatalogos(usuario(autenticacion));
    }

    @GetMapping("/cupo-registro")
    public DatosCupoRegistro cupo(Authentication autenticacion) {
        return consultas.consultarCupo(usuario(autenticacion));
    }

    @GetMapping("/operaciones/{idOperacion}")
    public DatosEstadoOperacionVehiculo operacion(
            Authentication autenticacion, @PathVariable UUID idOperacion
    ) {
        return consultas.consultarOperacion(usuario(autenticacion), idOperacion);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResultadoRegistroVehiculo registrar(
            Authentication autenticacion, @RequestBody DatosRegistroVehiculo datos
    ) {
        return registro.registrar(usuario(autenticacion), datos);
    }

    private long usuario(Authentication autenticacion) {
        if (autenticacion == null || !autenticacion.isAuthenticated()
                || autenticacion instanceof AnonymousAuthenticationToken) {
            throw new SesionVehiculosNoValidada();
        }
        IdentidadVehiculos identidad = identidades.getIfAvailable();
        if (identidad == null) {
            throw new SesionVehiculosNoValidada();
        }
        long idUsuario = identidad.exigirUsuarioAutenticado(autenticacion);
        if (idUsuario <= 0) {
            throw new SesionVehiculosNoValidada();
        }
        return idUsuario;
    }
}
