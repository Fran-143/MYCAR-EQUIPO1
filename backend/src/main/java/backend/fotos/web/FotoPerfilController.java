package backend.fotos.web;

import backend.autenticacion.SessionAuthentication;
import backend.fotos.FotoException;
import backend.fotos.FotoPerfilService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/perfil/foto")
public class FotoPerfilController {
    private final FotoPerfilService fotos;

    public FotoPerfilController(FotoPerfilService fotos) {
        this.fotos = fotos;
    }

    public record ResultadoFoto(String resultado, String mensaje) { }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResultadoFoto> registrar(
            @AuthenticationPrincipal SessionAuthentication.Principal principal,
            @RequestParam("file") MultipartFile archivo,
            @RequestParam(value = "contentType", required = false) String contentType
    ) throws java.io.IOException {
        long userId = requireUser(principal);
        String ct = contentType == null || contentType.isBlank() ? archivo.getContentType() : contentType;
        fotos.registrar(userId, archivo.getBytes(), ct);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResultadoFoto("FOTO_REGISTRADA", "Foto registrada correctamente"));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResultadoFoto> actualizar(
            @AuthenticationPrincipal SessionAuthentication.Principal principal,
            @RequestParam("file") MultipartFile archivo,
            @RequestParam(value = "contentType", required = false) String contentType
    ) throws java.io.IOException {
        long userId = requireUser(principal);
        String ct = contentType == null || contentType.isBlank() ? archivo.getContentType() : contentType;
        fotos.actualizar(userId, archivo.getBytes(), ct);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResultadoFoto("FOTO_ACTUALIZADA", "Foto actualizada correctamente"));
    }

    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResultadoFoto> eliminar(
            @AuthenticationPrincipal SessionAuthentication.Principal principal) {
        long userId = requireUser(principal);
        fotos.eliminar(userId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResultadoFoto("FOTO_ELIMINADA", "Foto eliminada correctamente"));
    }

    @GetMapping
    public ResponseEntity<byte[]> descargar(
            @AuthenticationPrincipal SessionAuthentication.Principal principal) {
        long userId = requireUser(principal);
        byte[] cuerpo = fotos.leerBytes(userId);
        var meta = fotos.metadatos(userId);
        String tipo = meta.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : meta.contentType();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_TYPE, tipo)
                .header("X-Content-Type-Options", "nosniff")
                .body(cuerpo);
    }

    private static long requireUser(SessionAuthentication.Principal principal) {
        if (principal == null || principal.userId() <= 0) {
            throw FotoException.unauthorized();
        }
        return principal.userId();
    }
}
