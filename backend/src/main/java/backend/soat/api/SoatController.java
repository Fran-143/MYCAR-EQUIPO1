package backend.soat.api;

import backend.soat.api.dto.ContratoSoat;
import backend.soat.service.SoatService;
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
    public ResponseEntity<byte[]> documento(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @PathVariable long idSoat) {
        long usuario = usuario(autenticacion);
        var fila = servicio.obtenerFilaDocumento(usuario, idVehiculo, idSoat);
        if (fila.storageObjectKey() == null) {
            throw new backend.soat.model.ErrorSoat("DOCUMENTO_NO_DISPONIBLE", "Este SOAT no tiene documento adjunto.");
        }
        byte[] cuerpo = servicio.leerDocumentoBytes(usuario, idVehiculo, idSoat);
        String tipo = fila.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : fila.contentType();
        String nombre = fila.nombreOriginal() == null ? "documento" : fila.nombreOriginal();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tipo))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre.replace("\"", "") + "\"")
                .body(cuerpo);
    }

    @PostMapping(value = "/documentos/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContratoSoat.DocumentoDto subirDocumento(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @RequestParam("file") MultipartFile archivo,
            @RequestParam(value = "contentType", required = false) String contentType,
            @RequestParam("nombreOriginal") String nombreOriginal) throws java.io.IOException {
        long usuario = usuario(autenticacion);
        String ct = (contentType == null || contentType.isBlank())
                ? (archivo.getContentType() == null ? "application/octet-stream" : archivo.getContentType())
                : contentType;
        return servicio.subirDocumentoAutenticado(
                usuario, idVehiculo, ct, nombreOriginal, archivo.getBytes());
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