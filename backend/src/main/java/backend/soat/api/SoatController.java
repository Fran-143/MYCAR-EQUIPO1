package backend.soat.api;

import backend.soat.api.dto.ContratoSoat;
import backend.soat.service.SoatService;
import backend.vehiculos.seguridad.IdentidadVehiculos;
import backend.vehiculos.seguridad.SesionVehiculosNoValidada;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vehiculos/{idVehiculo}/soat")
public class SoatController {
    private final SoatService servicio;
    private final ObjectProvider<IdentidadVehiculos> identidades;

    public SoatController(SoatService servicio, ObjectProvider<IdentidadVehiculos> identidades) {
        this.servicio = servicio;
        this.identidades = identidades;
    }

    @GetMapping
    public ContratoSoat.ConsultaResponse consultar(Authentication autenticacion, @PathVariable long idVehiculo) {
        return servicio.consultar(usuario(autenticacion), idVehiculo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContratoSoat.RegistrarResponse registrar(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @Valid @RequestBody ContratoSoat.RegistrarRequest solicitud) {
        return servicio.registrar(usuario(autenticacion), idVehiculo, solicitud);
    }

    @PutMapping("/{idSoat}")
    public ContratoSoat.CorregirResponse corregir(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @PathVariable long idSoat,
            @Valid @RequestBody ContratoSoat.CorregirRequest solicitud) {
        return servicio.corregir(usuario(autenticacion), idVehiculo, idSoat, solicitud);
    }

    @PostMapping("/renovar")
    @ResponseStatus(HttpStatus.CREATED)
    public ContratoSoat.RenovarResponse renovar(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @Valid @RequestBody ContratoSoat.RenovarRequest solicitud) {
        return servicio.renovar(usuario(autenticacion), idVehiculo, solicitud);
    }

    @PutMapping("/{idSoat}/recordatorio")
    public ContratoSoat.RecordatorioResponse recordatorio(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @PathVariable long idSoat,
            @Valid @RequestBody ContratoSoat.RecordatorioRequest solicitud) {
        return servicio.configurarRecordatorio(usuario(autenticacion), idVehiculo, idSoat, solicitud);
    }

    @GetMapping("/{idSoat}/documento")
    public ContratoSoat.DocumentoDescargaResponse documento(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @PathVariable long idSoat) {
        return servicio.descargarDocumento(usuario(autenticacion), idVehiculo, idSoat);
    }

    @PostMapping("/documentos/presign-upload")
    public ContratoSoat.PresignUploadResponse presignUpload(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @Valid @RequestBody ContratoSoat.PresignUploadRequest solicitud) {
        return servicio.presignUpload(usuario(autenticacion), idVehiculo, solicitud);
    }

    private long usuario(Authentication autenticacion) {
        if (autenticacion == null || !autenticacion.isAuthenticated()
                || autenticacion instanceof AnonymousAuthenticationToken) {
            throw new SesionVehiculosNoValidada();
        }
        var identidad = identidades.getIfAvailable();
        if (identidad == null) throw new SesionVehiculosNoValidada();
        long id = identidad.exigirUsuarioAutenticado(autenticacion);
        if (id <= 0) throw new SesionVehiculosNoValidada();
        return id;
    }
}