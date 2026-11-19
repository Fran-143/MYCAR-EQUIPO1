package backend.fotos.web;

import backend.fotos.FotoException;
import backend.fotos.FotoVehiculoService;
import backend.vehiculos.seguridad.IdentidadVehiculos;
import backend.vehiculos.seguridad.SesionVehiculosNoValidada;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/vehiculos/{idVehiculo}/foto")
public class FotoVehiculoController {
    private final FotoVehiculoService fotos;
    private final ObjectProvider<IdentidadVehiculos> identidades;

    public FotoVehiculoController(FotoVehiculoService fotos, ObjectProvider<IdentidadVehiculos> identidades) {
        this.fotos = fotos;
        this.identidades = identidades;
    }

    public record ResultadoFoto(String resultado, String mensaje) { }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResultadoFoto> registrar(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @RequestParam("file") MultipartFile archivo,
            @RequestParam(value = "contentType", required = false) String contentType
    ) throws java.io.IOException {
        long userId = usuario(autenticacion);
        String ct = contentType == null || contentType.isBlank() ? archivo.getContentType() : contentType;
        fotos.registrar(userId, idVehiculo, archivo.getBytes(), ct);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResultadoFoto("IMAGEN_REGISTRADA", "Imagen registrada correctamente"));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResultadoFoto> actualizar(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @RequestParam("file") MultipartFile archivo,
            @RequestParam(value = "contentType", required = false) String contentType
    ) throws java.io.IOException {
        long userId = usuario(autenticacion);
        String ct = contentType == null || contentType.isBlank() ? archivo.getContentType() : contentType;
        fotos.actualizar(userId, idVehiculo, archivo.getBytes(), ct);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResultadoFoto("IMAGEN_ACTUALIZADA", "Imagen actualizada correctamente"));
    }

    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResultadoFoto> eliminar(
            Authentication autenticacion, @PathVariable long idVehiculo) {
        fotos.eliminar(usuario(autenticacion), idVehiculo);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResultadoFoto("IMAGEN_ELIMINADA", "Imagen eliminada correctamente"));
    }

    @GetMapping
    public ResponseEntity<byte[]> descargar(
            Authentication autenticacion, @PathVariable long idVehiculo) {
        long userId = usuario(autenticacion);
        byte[] cuerpo = fotos.leerBytes(userId, idVehiculo);
        var meta = fotos.metadatos(userId, idVehiculo);
        String tipo = meta.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : meta.contentType();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_TYPE, tipo)
                .header("X-Content-Type-Options", "nosniff")
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
