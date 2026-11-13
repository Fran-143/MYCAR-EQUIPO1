package backend.vehiculos.controller;

import backend.vehiculos.dto.ContextoCorreccionKilometraje;
import backend.vehiculos.dto.ContextoKilometrajeActual;
import backend.vehiculos.dto.DatosActualizarKilometraje;
import backend.vehiculos.dto.DatosCorregirKilometraje;
import backend.vehiculos.dto.DatosEstadoOperacionCorreccionKm;
import backend.vehiculos.dto.DatosEstadoOperacionKilometraje;
import backend.vehiculos.dto.LecturaKilometrajeHistorialDto;
import backend.vehiculos.dto.ResultadoActualizarKilometraje;
import backend.vehiculos.dto.ResultadoCorregirKilometraje;
import backend.vehiculos.seguridad.IdentidadVehiculos;
import backend.vehiculos.seguridad.SesionVehiculosNoValidada;
import backend.vehiculos.service.ActualizarKilometrajeService;
import backend.vehiculos.service.CorregirKilometrajeService;
import java.util.List;
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
    private final CorregirKilometrajeService correccion;

    public KilometrajeVehiculoController(
            ObjectProvider<IdentidadVehiculos> identidades,
            ActualizarKilometrajeService kilometraje,
            CorregirKilometrajeService correccion
    ) {
        this.identidades = identidades;
        this.kilometraje = kilometraje;
        this.correccion = correccion;
    }

    @GetMapping("/{idVehiculo}/kilometraje/actual")
    public ContextoKilometrajeActual actual(Authentication autenticacion, @PathVariable long idVehiculo) {
        return kilometraje.consultarActual(usuario(autenticacion), idVehiculo);
    }

    /** RF15: historial de lecturas del vehículo propio. */
    @GetMapping("/{idVehiculo}/kilometraje/lecturas")
    public List<LecturaKilometrajeHistorialDto> listarLecturas(
            Authentication autenticacion, @PathVariable long idVehiculo
    ) {
        return kilometraje.listarLecturas(usuario(autenticacion), idVehiculo);
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

    /** RF16: contexto de corrección de la lectura indicada (debe ser la última). */
    @GetMapping("/{idVehiculo}/kilometraje/lecturas/{idLectura}/correccion")
    public ContextoCorreccionKilometraje contextoCorreccion(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @PathVariable long idLectura
    ) {
        return correccion.consultarContexto(usuario(autenticacion), idVehiculo, idLectura);
    }

    @PostMapping("/{idVehiculo}/kilometraje/lecturas/{idLectura}/correcciones")
    @ResponseStatus(HttpStatus.CREATED)
    public ResultadoCorregirKilometraje corregir(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @PathVariable long idLectura,
            @RequestBody DatosCorregirKilometraje datos
    ) {
        if (datos != null && datos.idLecturaBase() != null && datos.idLecturaBase() != idLectura) {
            throw new backend.vehiculos.domain.ErrorRegistroVehiculo(
                    "DATOS_INVALIDOS", java.util.Set.of("idLecturaBase"));
        }
        var payload = datos == null ? null : new DatosCorregirKilometraje(
                datos.idOperacion(), datos.kmCorregido(), datos.motivo(), idLectura, datos.versionBase());
        return correccion.registrar(usuario(autenticacion), idVehiculo, payload);
    }

    @GetMapping("/kilometraje/correcciones/operaciones/{idOperacion}")
    public DatosEstadoOperacionCorreccionKm operacionCorreccion(
            Authentication autenticacion, @PathVariable UUID idOperacion
    ) {
        return correccion.consultarOperacion(usuario(autenticacion), idOperacion);
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
