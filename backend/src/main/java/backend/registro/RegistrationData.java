package backend.registro;

import java.time.LocalDate;

/** Validated, normalized data. Does not contain the raw password or confirmation. */
public record RegistrationData(String nombre, String apellido, String correo,
        LocalDate fechaNacimiento, String versionPrivacidad) {
    @Override
    public String toString() { return "RegistrationData[REDACTED]"; }
}
