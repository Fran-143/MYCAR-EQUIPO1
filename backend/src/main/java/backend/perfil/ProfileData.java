package backend.perfil;

import java.time.LocalDate;

public record ProfileData(
        String nombre,
        String apellido,
        String correo,
        LocalDate fechaNacimiento,
        boolean tieneFoto
) {
    public String nombreCompleto() {
        return (nombre + " " + apellido).strip();
    }
}
