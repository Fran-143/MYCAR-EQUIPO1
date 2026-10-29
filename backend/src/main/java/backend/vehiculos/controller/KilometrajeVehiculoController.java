package backend.vehiculos.controller;

import backend.vehiculos.dto.ContextoKilometrajeActual;
import backend.vehiculos.dto.DatosActualizarKilometraje;
import backend.vehiculos.dto.DatosEstadoOperacionKilometraje;
import backend.vehiculos.dto.ResultadoActualizarKilometraje;
import backend.vehiculos.seguridad.IdentidadVehiculos;
import backend.vehiculos.seguridad.SesionVehiculosNoValidada;
import backend.vehiculos.service.ActualizarKilometrajeService;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehiculos")
public class KilometrajeVehiculoController {
    private final ObjectProvider<IdentidadVehiculos> identidades;
    private final ActualizarKilometrajeService kilometraje;

    public KilometrajeVehiculoController(
            ObjectProvider<IdentidadVehiculos> identidades,
            ActualizarKilometrajeService kilometraje
    ) {
        this.identidades = identidades;
        this.kilometraje = kilometraje;
    }

    @GetMapping("/{idVehiculo}/kilometraje/actual")
    public ContextoKilometrajeActual actual(Authentication autenticacion, @PathVariable long idVehiculo) {
        return kilometraje.consultarActual(usuario(autenticacion), idVehiculo);
    }

    @PostMapping("/{idVehiculo}/kilometraje/lecturas")
    @ResponseStatus(HttpStatus.CREATED)
    public ResultadoActualizarKilometraje registrar(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @RequestBody DatosActualizarKilometraje datos
    ) {
        return kilometraje.registrar(usuario(autenticacion), idVehiculo, datos);
    }

    @GetMapping("/kilometraje/operaciones/{idOperacion}")
    public DatosEstadoOperacionKilometraje operacion(
            Authentication autenticacion, @PathVariable UUID idOperacion
    ) {
        return kilometraje.consultarOperacion(usuario(autenticacion), idOperacion);
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
