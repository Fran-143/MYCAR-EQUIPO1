package backend.autenticacion;

import java.util.UUID;

/** Application input; not a finalized REST DTO. */
public record LoginCommand(String correo, String contrasena, UUID idInstalacion) {
    @Override public String toString() { return "LoginCommand[REDACTED]"; }
}
