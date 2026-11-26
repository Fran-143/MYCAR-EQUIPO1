package backend.licencia.api;

import backend.licencia.api.dto.ContratoLicencia;
import backend.licencia.service.LicenciaService;
import backend.vehiculos.seguridad.IdentidadVehiculos;
import backend.vehiculos.seguridad.SesionVehiculosNoValidada;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/licencia")
public class LicenciaController {
    private final LicenciaService servicio;
    private final ObjectProvider<IdentidadVehiculos> identidades;

    public LicenciaController(LicenciaService servicio, ObjectProvider<IdentidadVehiculos> identidades) {
        this.servicio = servicio;
        this.identidades = identidades;
    }

    @GetMapping
    public ContratoLicencia.ConsultaResponse consultar(Authentication autenticacion) {
        return servicio.consultar(usuario(autenticacion));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContratoLicencia.RegistrarResponse registrar(
            Authentication autenticacion, @Valid @RequestBody ContratoLicencia.RegistrarRequest solicitud) {
        return servicio.registrar(usuario(autenticacion), solicitud);
    }

    @PutMapping
    public ContratoLicencia.EditarResponse editar(
            Authentication autenticacion, @Valid @RequestBody ContratoLicencia.EditarRequest solicitud) {
        return servicio.editar(usuario(autenticacion), solicitud);
    }

    @PostMapping("/renovar")
    @ResponseStatus(HttpStatus.CREATED)
    public ContratoLicencia.RenovarResponse renovar(
            Authentication autenticacion, @Valid @RequestBody ContratoLicencia.RenovarRequest solicitud) {
        return servicio.renovar(usuario(autenticacion), solicitud);
    }

    @PutMapping("/recordatorio")
    public ContratoLicencia.RecordatorioResponse recordatorio(
            Authentication autenticacion, @Valid @RequestBody ContratoLicencia.RecordatorioRequest solicitud) {
        return servicio.configurarRecordatorio(usuario(autenticacion), solicitud);
    }

    @PostMapping(value = "/fotos/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContratoLicencia.FotoDto subirFoto(
            Authentication autenticacion,
            @RequestParam("lado") String lado,
            @RequestParam("file") MultipartFile archivo,
            @RequestParam(value = "contentType", required = false) String contentType,
            @RequestParam("nombreOriginal") String nombreOriginal) throws java.io.IOException {
        long usuario = usuario(autenticacion);
        String ct = (contentType == null || contentType.isBlank())
                ? (archivo.getContentType() == null ? "application/octet-stream" : archivo.getContentType())
                : contentType;
        return servicio.subirFoto(usuario, lado, ct, nombreOriginal, archivo.getBytes());
    }

    @GetMapping("/fotos/{lado}")
    public ResponseEntity<byte[]> foto(
            Authentication autenticacion, @PathVariable String lado) {
        long usuario = usuario(autenticacion);
        byte[] cuerpo = servicio.leerFotoBytes(usuario, lado);
        var meta = servicio.metadatosFoto(usuario, lado);
        String tipo = meta.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : meta.contentType();
        String nombre = meta.nombreOriginal() == null ? "foto" : meta.nombreOriginal();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tipo))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombre.replace("\"", "") + "\"")
                .body(cuerpo);
    }

    private long usuario(Authentication autenticacion) {
        if (autenticacion == null || !autenticacion.isAuthenticated()
                || autenticacion instanceof AnonymousAuthenticationToken) {
            throw new SesionVehiculosNoValidada();
        }
        var identidad = identidades.getIfAvailable();
        if (identidad == null) {
            throw new SesionVehiculosNoValidada();
        }
        long id = identidad.exigirUsuarioAutenticado(autenticacion);
        if (id <= 0) {
            throw new SesionVehiculosNoValidada();
        }
        return id;
    }
}