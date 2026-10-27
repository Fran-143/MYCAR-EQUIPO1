package backend.registro;

import java.time.LocalDate;

/** Input of the application use case, not an approved HTTP contract. */
public record RegistrationCommand(String nombre, String apellido, String correo,
        LocalDate fechaNacimiento, String contrasena, String confirmacionContrasena,
        Boolean privacidadAceptada, String versionPrivacidad) {
    @Override
    public String toString() {
        return "RegistrationCommand[REDACTED]";
    }
}
