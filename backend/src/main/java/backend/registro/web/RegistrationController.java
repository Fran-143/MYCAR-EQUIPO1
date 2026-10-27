package backend.registro.web;

import backend.registro.RegisterAccount;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/registro")
public class RegistrationController {
    private final RegisterAccount registerAccount;

    public RegistrationController(RegisterAccount registerAccount) { this.registerAccount = registerAccount; }

    @PostMapping(consumes = "application/json", produces = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@RequestBody RegistrationRequest request) {
        registerAccount.register(request.toCommand());
        return new RegistrationResponse("CUENTA_CREADA", "INICIO_SESION");
    }

    public record RegistrationResponse(String resultado, String destino) { }
}
