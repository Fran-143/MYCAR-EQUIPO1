package backend.perfil;

import java.time.LocalDate;

public record ProfileData(String nombre, String apellido, String correo, LocalDate fechaNacimiento) {
    public String nombreCompleto() {
        return (nombre + " " + apellido).strip();
    }
}
