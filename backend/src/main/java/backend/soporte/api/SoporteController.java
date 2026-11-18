package backend.soporte.api;

import backend.soporte.api.dto.ContratoSoporte;
import backend.soporte.service.SoporteService;
import backend.vehiculos.seguridad.IdentidadVehiculos;
import backend.vehiculos.seguridad.SesionVehiculosNoValidada;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/v1/soporte")
public class SoporteController {
    private final SoporteService servicio;
    private final ObjectProvider<IdentidadVehiculos> identidades;

    public SoporteController(SoporteService servicio, ObjectProvider<IdentidadVehiculos> identidades) {
        this.servicio = servicio;
        this.identidades = identidades;
    }

    @GetMapping("/vehiculos-elegibles")
    public List<ContratoSoporte.VehiculoElegibleDto> elegibles(Authentication auth) {
        return servicio.vehiculosElegibles(usuario(auth));
    }

    @GetMapping("/solicitudes")
    public List<ContratoSoporte.SolicitudResumenDto> listar(Authentication auth) {
        return servicio.listar(usuario(auth));
    }

    @GetMapping("/solicitudes/{codigo}")
    public ContratoSoporte.SolicitudDetalleDto detalle(Authentication auth, @PathVariable String codigo) {
        return servicio.detalle(usuario(auth), codigo);
    }

    @GetMapping("/solicitudes/{codigo}/evidencia")
    public ResponseEntity<byte[]> evidencia(Authentication auth, @PathVariable String codigo) {
        ContratoSoporte.EvidenciaPayload payload = servicio.evidenciaPropia(usuario(auth), codigo);
        MediaType media = mediaTypeSeguro(payload.contentType());
        return ResponseEntity.ok()
                .contentType(media)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + nombreSeguro(payload.nombreOriginal()) + "\"")
                .body(payload.bytes());
    }

    @PostMapping(value = "/solicitudes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ContratoSoporte.CrearResponse crear(
            Authentication auth,
            @RequestParam("idOperacion") UUID idOperacion,
            @RequestParam("idVehiculo") long idVehiculo,
            @RequestParam("vinPropuesto") String vinPropuesto,
            @RequestParam("motivo") String motivo,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "contentType", required = false) String contentType
    ) throws Exception {
        boolean conArchivo = file != null && !file.isEmpty();
        String ct = null;
        String nombre = null;
        byte[] bytes = null;
        if (conArchivo) {
            ct = (contentType == null || contentType.isBlank())
                    ? (file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                    : contentType;
            nombre = file.getOriginalFilename() == null ? "evidencia" : file.getOriginalFilename();
            bytes = file.getBytes();
        }
        return servicio.crearCorreccionVin(
                usuario(auth), idOperacion, idVehiculo, vinPropuesto, motivo,
                ct, nombre, bytes);
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

    public static MediaType mediaTypeSeguro(String contentType) {
        try {
            if (contentType == null || contentType.isBlank()) return MediaType.APPLICATION_OCTET_STREAM;
            return MediaType.parseMediaType(contentType);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    public static String nombreSeguro(String nombre) {
        if (nombre == null || nombre.isBlank()) return "evidencia";
        return nombre.replace("\"", "").replace("\r", "").replace("\n", "");
    }
}
