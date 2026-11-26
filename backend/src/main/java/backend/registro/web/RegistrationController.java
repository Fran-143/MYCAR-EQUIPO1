package backend.registro.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/registro")
public class RegistrationController {

    /** Bypass OTP deshabilitado: usar /api/v1/auth/registro/verificacion/*. */
    @PostMapping(consumes = "application/json", produces = "application/json")
    @ResponseStatus(HttpStatus.GONE)
    public RegistrationResponse register(@RequestBody RegistrationRequest request) {
        throw new UnsupportedOperationException("REGISTRO_REQUIERE_VERIFICACION");
    }

    public record RegistrationResponse(String resultado, String destino) { }
}
