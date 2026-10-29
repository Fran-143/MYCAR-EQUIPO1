package backend.autenticacion;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;

@Component
public class LoginValidator {
    private final Validator validator;
    public LoginValidator(Validator validator) {this.validator=validator;}

    public LoginCommand validate(LoginCommand command) {
        if(command==null) throw AuthenticationFailure.invalid(java.util.Map.of("login","Datos obligatorios."));
        var errors=new LinkedHashMap<String,String>();
        String email=command.correo()==null?"":command.correo().strip().toLowerCase(Locale.ROOT);
        if(email.codePointCount(0,email.length())<5 || email.codePointCount(0,email.length())>254
                || email.indexOf('@')<1 || !validator.validate(new EmailValue(email)).isEmpty())
            errors.put("correo","Ingresa un correo válido.");
        // Login checks presence, not RF01 password complexity; never transform a secret.
        if(command.contrasena()==null || command.contrasena().isEmpty())
            errors.put("contrasena","Contraseña obligatoria.");
        if(command.idInstalacion()==null) errors.put("idInstalacion","Instalación obligatoria.");
        if(!errors.isEmpty()) throw AuthenticationFailure.invalid(errors);
        return new LoginCommand(email,command.contrasena(),command.idInstalacion());
    }
    private record EmailValue(@Email String value) { }
}
