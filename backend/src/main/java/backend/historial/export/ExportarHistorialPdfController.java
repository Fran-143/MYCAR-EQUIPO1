package backend.historial.export;

import backend.historial.export.ExportarHistorialModelos.ContextoExport;
import backend.historial.export.ExportarHistorialPdfService.PdfGenerado;
import backend.autenticacion.SessionAuthentication;
import backend.mantenimiento.seguridad.SesionMantenimientoNoValidada;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehiculos/{idVehiculo}/historial/mantenimientos")
public class ExportarHistorialPdfController {
    private final ExportarHistorialPdfService servicio;

    public ExportarHistorialPdfController(ExportarHistorialPdfService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/exportar-pdf/contexto")
    public ResponseEntity<ContextoExport> contexto(
            Authentication autenticacion,
            @PathVariable long idVehiculo
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.contexto(usuario(autenticacion), idVehiculo));
    }

    @PostMapping(value = "/exportar-pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportar(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @RequestBody ExportarHistorialRequest body
    ) {
        PdfGenerado pdf = servicio.generar(usuario(autenticacion), idVehiculo, body);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + pdf.nombreArchivo() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-MyCar-Nombre-Archivo", pdf.nombreArchivo())
                .body(pdf.bytes());
    }

    private long usuario(Authentication autenticacion) {
        if (autenticacion == null || !autenticacion.isAuthenticated()
                || autenticacion instanceof AnonymousAuthenticationToken) {
            throw new SesionMantenimientoNoValidada();
        }
        if (!(autenticacion.getPrincipal() instanceof SessionAuthentication.Principal principal)
                || principal.userId() <= 0) {
            throw new SesionMantenimientoNoValidada();
        }
        return principal.userId();
    }
}
