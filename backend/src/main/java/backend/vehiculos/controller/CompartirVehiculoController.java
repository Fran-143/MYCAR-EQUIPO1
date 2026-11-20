package backend.vehiculos.controller;

import backend.vehiculos.seguridad.IdentidadVehiculos;
import backend.vehiculos.seguridad.SesionVehiculosNoValidada;
import backend.vehiculos.service.CompartirVehiculoPdfService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF40: PDF público temporal del vehículo propio (activo o inactivo). */
@RestController
@RequestMapping("/api/v1/vehiculos/{idVehiculo}/compartir")
public class CompartirVehiculoController {
    private final ObjectProvider<IdentidadVehiculos> identidades;
    private final CompartirVehiculoPdfService pdfService;

    public CompartirVehiculoController(
            ObjectProvider<IdentidadVehiculos> identidades,
            CompartirVehiculoPdfService pdfService
    ) {
        this.identidades = identidades;
        this.pdfService = pdfService;
    }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @RequestParam(defaultValue = "false") boolean incluirFoto
    ) {
        long idUsuario = usuario(autenticacion);
        CompartirVehiculoPdfService.PdfGenerado pdf =
                pdfService.generar(idUsuario, idVehiculo, incluirFoto);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + pdf.nombreArchivo() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf.bytes());
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
