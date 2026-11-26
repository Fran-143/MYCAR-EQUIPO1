package backend.tive.api;

import backend.tive.api.dto.ContratoTive;
import backend.tive.service.TiveService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/vehiculos/{idVehiculo}/tive")
public class TiveController {
    private final TiveService servicio;
    private final ObjectProvider<IdentidadVehiculos> identidades;

    public TiveController(TiveService servicio, ObjectProvider<IdentidadVehiculos> identidades) {
        this.servicio = servicio;
        this.identidades = identidades;
    }

    @GetMapping("/precheck")
    public ContratoTive.PrecheckResponse precheck(Authentication autenticacion, @PathVariable long idVehiculo) {
        return servicio.precheck(usuario(autenticacion), idVehiculo);
    }

    @GetMapping
    public ContratoTive.ConsultaResponse consultar(Authentication autenticacion, @PathVariable long idVehiculo) {
        return servicio.consultar(usuario(autenticacion), idVehiculo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContratoTive.RegistrarResponse registrar(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @Valid @RequestBody ContratoTive.RegistrarRequest solicitud) {
        return servicio.registrar(usuario(autenticacion), idVehiculo, solicitud);
    }

    @PutMapping("/{idTive}")
    public ContratoTive.ActualizarResponse actualizar(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @PathVariable long idTive,
            @Valid @RequestBody ContratoTive.ActualizarRequest solicitud) {
        return servicio.actualizar(usuario(autenticacion), idVehiculo, idTive, solicitud);
    }

    @GetMapping("/{idTive}/documento")
    public ResponseEntity<byte[]> documento(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @PathVariable long idTive) {
        long usuario = usuario(autenticacion);
        var fila = servicio.obtenerFilaDocumento(usuario, idVehiculo, idTive);
        if (fila.storageObjectKey() == null) {
            throw new backend.tive.model.ErrorTive("DOCUMENTO_NO_DISPONIBLE", "Esta TIVE no tiene documento adjunto.");
        }
        byte[] cuerpo = servicio.leerDocumentoBytes(usuario, idVehiculo, idTive);
        String tipo = fila.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : fila.contentType();
        String nombre = fila.nombreOriginal() == null ? "documento" : fila.nombreOriginal();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tipo))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombre.replace("\"", "") + "\"")
                .body(cuerpo);
    }

    @PostMapping(value = "/documentos/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContratoTive.DocumentoDto subirDocumento(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @RequestParam("file") MultipartFile archivo,
            @RequestParam(value = "contentType", required = false) String contentType,
            @RequestParam("nombreOriginal") String nombreOriginal) throws java.io.IOException {
        long usuario = usuario(autenticacion);
        String ct = (contentType == null || contentType.isBlank())
                ? (archivo.getContentType() == null ? "application/octet-stream" : archivo.getContentType())
                : contentType;
        return servicio.subirDocumentoAutenticado(usuario, idVehiculo, ct, nombreOriginal, archivo.getBytes());
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
