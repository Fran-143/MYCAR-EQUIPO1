package backend.vehiculos.controller;

import backend.vehiculos.dto.DatosCatalogosVehiculo;
import backend.vehiculos.dto.DatosListadoVehiculos;
import backend.vehiculos.dto.DatosCupoRegistro;
import backend.vehiculos.dto.DatosEstadoOperacionVehiculo;
import backend.vehiculos.dto.DatosRegistroVehiculo;
import backend.vehiculos.dto.ResultadoRegistroVehiculo;
import backend.vehiculos.seguridad.IdentidadVehiculos;
import backend.vehiculos.seguridad.SesionVehiculosNoValidada;
import backend.vehiculos.service.ConsultaRegistroVehiculoService;
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

    public VehiculosController(
            ObjectProvider<IdentidadVehiculos> identidades,
            RegistroVehiculoService registro,
            ConsultaRegistroVehiculoService consultas
    ) {
        this.identidades = identidades;
        this.registro = registro;
        this.consultas = consultas;
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
