package backend.registro.web;

import backend.registro.RegistrationCommand;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record RegistrationRequest(String nombre, String apellido, String correo,
        @JsonFormat(pattern = "uuuu-MM-dd", lenient = com.fasterxml.jackson.annotation.OptBoolean.FALSE)
        LocalDate fechaNacimiento, String contrasena, String confirmacionContrasena,
        PrivacyConsent consentimientoPrivacidad) {
    public record PrivacyConsent(Boolean aceptado, String version) { }

    public RegistrationCommand toCommand() {
        return new RegistrationCommand(nombre, apellido, correo, fechaNacimiento,
                contrasena, confirmacionContrasena,
                consentimientoPrivacidad == null ? null : consentimientoPrivacidad.aceptado(),
                consentimientoPrivacidad == null ? null : consentimientoPrivacidad.version());
    }

    @Override
    public String toString() { return "RegistrationRequest[REDACTED]"; }
}
